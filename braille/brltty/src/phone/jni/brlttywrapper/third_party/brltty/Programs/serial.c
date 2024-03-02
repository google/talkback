/*
 * BRLTTY - A background process providing access to the console screen (when in
 *          text mode) for a blind person using a refreshable braille display.
 *
 * Copyright (C) 1995-2023 by The BRLTTY Developers.
 *
 * BRLTTY comes with ABSOLUTELY NO WARRANTY.
 *
 * This is free software, placed under the terms of the
 * GNU Lesser General Public License, as published by the Free Software
 * Foundation; either version 2.1 of the License, or (at your option) any
 * later version. Please see the file LICENSE-LGPL for details.
 *
 * Web Page: http://brltty.app/
 *
 * This software is maintained by Dave Mielke <dave@mielke.cc>.
 */

#include "prologue.h"

#include <string.h>
#include <errno.h>

#include "log.h"
#include "io_log.h"
#include "strfmt.h"
#include "parameters.h"
#include "parse.h"
#include "device.h"
#include "async_wait.h"

#if defined(USE_PKG_SERIAL_NONE)
#include "serial_none.h"
#elif defined(USE_PKG_SERIAL_GRUB)
#include "serial_grub.h"
#elif defined(USE_PKG_SERIAL_MSDOS)
#include "serial_msdos.h"
#elif defined(USE_PKG_SERIAL_TERMIOS)
#include "serial_termios.h"
#elif defined(USE_PKG_SERIAL_WINDOWS)
#include "serial_windows.h"
#else /* serial package */
#error serial I/O package not selected
#include "serial_none.h"
#endif /* serial package */

#include "serial_internal.h"

const char *
serialGetDevicePath (SerialDevice *serial) {
  return serial->devicePath;
}

const SerialBaudEntry *
serialGetBaudEntry (unsigned int baud) {
  const SerialBaudEntry *entry = serialBaudTable;

  while (entry->baud) {
    if (baud == entry->baud) return entry;
    entry += 1;
  }

  logMessage(LOG_WARNING, "undefined serial baud: %u", baud);
  return NULL;
}

static void
serialInitializeAttributes (SerialAttributes *attributes) {
  memset(attributes, 0, sizeof(*attributes));
  serialPutInitialAttributes(attributes);

  {
    const SerialBaudEntry *entry = serialGetBaudEntry(SERIAL_DEFAULT_BAUD);

    if (entry) {
      if (!serialPutSpeed(attributes, entry->speed)) {
        logMessage(LOG_WARNING, "default serial baud not supported: %u", SERIAL_DEFAULT_BAUD);
      }
    }
  }

  if (!serialPutDataBits(attributes, SERIAL_DEFAULT_DATA_BITS)) {
    logMessage(LOG_WARNING, "default serial data bits not supported: %u", SERIAL_DEFAULT_DATA_BITS);
  }

  if (!serialPutStopBits(attributes, SERIAL_DEFAULT_STOP_BITS)) {
    logMessage(LOG_WARNING, "default serial stop bits not supported: %u", SERIAL_DEFAULT_STOP_BITS);
  }

  if (!serialPutParity(attributes, SERIAL_DEFAULT_PARITY)) {
    logMessage(LOG_WARNING, "default serial parity not supported: %u", SERIAL_DEFAULT_PARITY);
  }

  if (serialPutFlowControl(attributes, SERIAL_DEFAULT_FLOW_CONTROL)) {
    logMessage(LOG_WARNING, "default serial flow control not supported: 0X%04X", SERIAL_DEFAULT_FLOW_CONTROL);
  }

  {
    int state = 0;

    if (!serialPutModemState(attributes, state)) {
      logMessage(LOG_WARNING, "default serial modem state not supported: %d", state);
    }
  }
}

int
serialSetBaud (SerialDevice *serial, unsigned int baud) {
  const SerialBaudEntry *entry = serialGetBaudEntry(baud);

  if (entry) {
    logMessage(LOG_CATEGORY(SERIAL_IO), "set baud: %u", baud);
    if (serialPutSpeed(&serial->pendingAttributes, entry->speed)) return 1;
    logUnsupportedBaud(baud);
  }

  return 0;
}

int
serialValidateBaud (unsigned int *baud, const char *description, const char *word, const unsigned int *choices) {
  if (!*word || isUnsignedInteger(baud, word)) {
    const SerialBaudEntry *entry = serialGetBaudEntry(*baud);

    if (entry) {
      if (!choices) return 1;

      while (*choices) {
        if (*baud == *choices) return 1;
        choices += 1;
      }

      logMessage(LOG_ERR, "unsupported %s: %u", description, *baud);
    } else {
      logMessage(LOG_ERR, "undefined %s: %u", description, *baud);
    }
  } else {
    logMessage(LOG_ERR, "invalid %s: %u", description, *baud);
  }

  return 0;
}

int
serialSetDataBits (SerialDevice *serial, unsigned int bits) {
  logMessage(LOG_CATEGORY(SERIAL_IO), "set data bits: %u", bits);
  if (serialPutDataBits(&serial->pendingAttributes, bits)) return 1;

  logUnsupportedDataBits(bits);
  return 0;
}

int
serialSetStopBits (SerialDevice *serial, SerialStopBits bits) {
  logMessage(LOG_CATEGORY(SERIAL_IO), "set stop bits: %u", bits);
  if (serialPutStopBits(&serial->pendingAttributes, bits)) return 1;

  logUnsupportedStopBits(bits);
  return 0;
}

int
serialSetParity (SerialDevice *serial, SerialParity parity) {
  logMessage(LOG_CATEGORY(SERIAL_IO), "set parity: %u", parity);
  if (serialPutParity(&serial->pendingAttributes, parity)) return 1;

  logUnsupportedParity(parity);
  return 0;
}

#ifdef HAVE_POSIX_THREADS
static void
serialFlowControlProc_inputCTS (SerialDevice *serial) {
  int up = serialTestLineCTS(serial);

  while (!serial->flowControlStop) {
    serialSetLineRTS(serial, up);
    serialWaitLineCTS(serial, (up = !up), 0);
  }
}

THREAD_FUNCTION(serialFlowControlThread) {
  SerialDevice *serial = argument;

  serial->currentFlowControlProc(serial);
  return NULL;
}

static int
serialStartFlowControlThread (SerialDevice *serial) {
  if (!serial->flowControlRunning && serial->currentFlowControlProc) {
    pthread_t thread;
    pthread_attr_t attributes;

    pthread_attr_init(&attributes);
    pthread_attr_setdetachstate(&attributes, PTHREAD_CREATE_DETACHED);

    serial->flowControlStop = 0;
    if (createThread("serial-input-cts", &thread, &attributes,
                     serialFlowControlThread, serial)) {
      logSystemError("pthread_create");
      return 0;
    }

    serial->flowControlThread = thread;
    serial->flowControlRunning = 1;
  }

  return 1;
}

static void
serialStopFlowControlThread (SerialDevice *serial) {
  if (serial->flowControlRunning) {
    serial->flowControlStop = 1;
    serial->flowControlRunning = 0;
  }
}
#endif /* HAVE_POSIX_THREADS */

int
serialSetFlowControl (SerialDevice *serial, SerialFlowControl flow) {
  logMessage(LOG_CATEGORY(SERIAL_IO), "set flow control: 0X%02X", flow);
  flow = serialPutFlowControl(&serial->pendingAttributes, flow);

#ifdef HAVE_POSIX_THREADS
  if (flow & SERIAL_FLOW_INPUT_CTS) {
    flow &= ~SERIAL_FLOW_INPUT_CTS;
    serial->pendingFlowControlProc = serialFlowControlProc_inputCTS;
  } else {
    serial->pendingFlowControlProc = NULL;
  }

  {
    int state = !!serial->pendingFlowControlProc;

    if (!serialPutModemState(&serial->pendingAttributes, state)) {
      logMessage(LOG_WARNING, "unsupported serial modem state: %d", state);
    }
  }
#endif /* HAVE_POSIX_THREADS */

  if (!flow) return 1;
  logUnsupportedFlowControl(flow);
  return 0;
}

int
serialSetParameters (SerialDevice *serial, const SerialParameters *parameters) {
  if (!serialSetBaud(serial, parameters->baud)) return 0;
  if (!serialSetDataBits(serial, parameters->dataBits)) return 0;
  if (!serialSetStopBits(serial, parameters->stopBits)) return 0;
  if (!serialSetParity(serial, parameters->parity)) return 0;
  if (!serialSetFlowControl(serial, parameters->flowControl)) return 0;
  return 1;
}

unsigned int
serialGetCharacterSize (const SerialParameters *parameters) {
  unsigned int size = 1 /* start bit */ + parameters->dataBits;
  size += (parameters->stopBits == SERIAL_STOP_1)? 1: 2;
  if (parameters->parity != SERIAL_PARITY_NONE) size += 1;
  return size;
}

unsigned int
serialGetCharacterBits (SerialDevice *serial) {
  const SerialAttributes *attributes = &serial->pendingAttributes;
  return 1 /* start bit */
       + serialGetDataBits(attributes)
       + serialGetParityBits(attributes)
       + serialGetStopBits(attributes)
       ;
}

int
serialDiscardInput (SerialDevice *serial) {
  logMessage(LOG_CATEGORY(SERIAL_IO), "discard input");
  return serialCancelInput(serial);
}

int
serialDiscardOutput (SerialDevice *serial) {
  logMessage(LOG_CATEGORY(SERIAL_IO), "discard output");
  return serialCancelOutput(serial);
}

int
serialFlushOutput (SerialDevice *serial) {
  logMessage(LOG_CATEGORY(SERIAL_IO), "flush output");

  if (serial->stream) {
    if (fflush(serial->stream) == EOF) {
      logSystemError("fflush");
      return 0;
    }
  }
  return 1;
}

int
serialAwaitOutput (SerialDevice *serial) {
  if (!serialFlushOutput(serial)) return 0;
  if (!serialDrainOutput(serial)) return 0;
  return 1;
}

static void
serialCopyAttributes (SerialAttributes *destination, const SerialAttributes *source) {
  memcpy(destination, source, sizeof(*destination));
}

static int
serialCompareAttributes (const SerialAttributes *attributes, const SerialAttributes *reference) {
  return memcmp(attributes, reference, sizeof(*attributes)) == 0;
}

static int
serialReadAttributes (SerialDevice *serial) {
  return serialGetAttributes(serial, &serial->currentAttributes);
}

static int
serialWriteAttributes (SerialDevice *serial, const SerialAttributes *attributes) {
  if (!serialCompareAttributes(attributes, &serial->currentAttributes)) {
    if (!serialAwaitOutput(serial)) return 0;
    logBytes(LOG_CATEGORY(SERIAL_IO), "attributes", attributes, sizeof(*attributes));
    if (!serialPutAttributes(serial, attributes)) return 0;
    serialCopyAttributes(&serial->currentAttributes, attributes);
  }

  return 1;
}

static int
serialFlushAttributes (SerialDevice *serial) {
#ifdef HAVE_POSIX_THREADS
  int restartFlowControlThread = serial->pendingFlowControlProc != serial->currentFlowControlProc;
  if (restartFlowControlThread) serialStopFlowControlThread(serial);
#endif /* HAVE_POSIX_THREADS */

  if (!serialWriteAttributes(serial, &serial->pendingAttributes)) return 0;

#ifdef HAVE_POSIX_THREADS
  if (restartFlowControlThread) {
    serial->currentFlowControlProc = serial->pendingFlowControlProc;
    if (!serialStartFlowControlThread(serial)) return 0;
  }
#endif /* HAVE_POSIX_THREADS */

  return 1;
}

int
serialAwaitInput (SerialDevice *serial, int timeout) {
  if (!serialFlushAttributes(serial)) return 0;
  if (!serialPollInput(serial, timeout)) return 0;
  return 1;
}

ssize_t
serialReadData (
  SerialDevice *serial,
  void *buffer, size_t size,
  int initialTimeout, int subsequentTimeout
) {
  if (!serialFlushAttributes(serial)) return -1;

  {
    ssize_t result = serialGetData(serial, buffer, size, initialTimeout, subsequentTimeout);

    if (result > 0) {
      logBytes(LOG_CATEGORY(SERIAL_IO), "input", buffer, result);
    }

    return result;
  }
}

int
serialReadChunk (
  SerialDevice *serial,
  void *buffer, size_t *offset, size_t count,
  int initialTimeout, int subsequentTimeout
) {
  unsigned char *byte = buffer;
  const unsigned char *const first = byte;
  const unsigned char *const end = first + count;
  int timeout = *offset? subsequentTimeout: initialTimeout;

  if (!serialFlushAttributes(serial)) return 0;
  byte += *offset;

  while (byte < end) {
    ssize_t result = serialGetData(serial, byte, 1, timeout, subsequentTimeout);

    if (!result) {
      result = -1;
      errno = EAGAIN;
    }

    if (result == -1) {
      if (errno == EINTR) continue;
      return 0;
    }

    byte += 1;
    *offset += 1;
    timeout = subsequentTimeout;
  }

  if (byte > first) {
    logBytes(LOG_CATEGORY(SERIAL_IO), "input", first, (byte - first));
  }

  return 1;
}

ssize_t
serialWriteData (
  SerialDevice *serial,
  const void *data, size_t size
) {
  if (!serialFlushAttributes(serial)) return -1;
  if (size > 0) logBytes(LOG_CATEGORY(SERIAL_IO), "output", data, size);
  return serialPutData(serial, data, size);
}

static int
serialReadLines (SerialDevice *serial, SerialLines *lines) {
  int result = serialGetLines(serial);
  if (result) *lines = serial->linesState;
  return result;
}

static int
serialWriteLines (SerialDevice *serial, SerialLines high, SerialLines low) {
  return serialPutLines(serial, high, low);
}

static int
serialSetLine (SerialDevice *serial, SerialLines line, int up) {
  return serialWriteLines(serial, up?line:0, up?0:line);
}

int
serialSetLineRTS (SerialDevice *serial, int up) {
  return serialSetLine(serial, SERIAL_LINE_RTS, up);
}

int
serialSetLineDTR (SerialDevice *serial, int up) {
  return serialSetLine(serial, SERIAL_LINE_DTR, up);
}

static int
serialTestLines (SerialDevice *serial, SerialLines high, SerialLines low) {
  SerialLines lines;
  if (serialReadLines(serial, &lines))
    if (((lines & high) == high) && ((~lines & low) == low))
      return 1;
  return 0;
}

int
serialTestLineCTS (SerialDevice *serial) {
  return serialTestLines(serial, SERIAL_LINE_CTS, 0);
}

int
serialTestLineDSR (SerialDevice *serial) {
  return serialTestLines(serial, SERIAL_LINE_DSR, 0);
}

static int
serialDefineWaitLines (SerialDevice *serial, SerialLines lines) {
  if (lines != serial->waitLines) {
    if (!serialRegisterWaitLines(serial, lines)) return 0;
    serial->waitLines = lines;
  }

  return 1;
}

static int
serialAwaitLineChange (SerialDevice *serial) {
  return serialMonitorWaitLines(serial);
}

static int
serialWaitLines (SerialDevice *serial, SerialLines high, SerialLines low) {
  SerialLines lines = high | low;
  int ok = 0;

  if (serialDefineWaitLines(serial, lines)) {
    while (!serialTestLines(serial, high, low))
      if (!serialAwaitLineChange(serial))
        goto done;
    ok = 1;
  }

done:
  serialDefineWaitLines(serial, 0);
  return ok;
}

static int
serialWaitFlank (SerialDevice *serial, SerialLines line, int up) {
  int ok = 0;

  if (serialDefineWaitLines(serial, line)) {
    while (!serialTestLines(serial, up?0:line, up?line:0))
      if (!serialAwaitLineChange(serial))
        goto done;
    if (serialAwaitLineChange(serial)) ok = 1;
  }

done:
  serialDefineWaitLines(serial, 0);
  return ok;
}

int
serialWaitLine (SerialDevice *serial, SerialLines line, int up, int flank) {
  return flank? serialWaitFlank(serial, line, up):
                serialWaitLines(serial, up?line:0, up?0:line);
}

int
serialWaitLineCTS (SerialDevice *serial, int up, int flank) {
  return serialWaitLine(serial, SERIAL_LINE_CTS, up, flank);
}

int
serialWaitLineDSR (SerialDevice *serial, int up, int flank) {
  return serialWaitLine(serial, SERIAL_LINE_DSR, up, flank);
}

int
serialPrepareDevice (SerialDevice *serial) {
  if (serialReadAttributes(serial)) {
    serialCopyAttributes(&serial->originalAttributes, &serial->currentAttributes);
    serialInitializeAttributes(&serial->pendingAttributes);

    serial->linesState = 0;
    serial->waitLines = 0;

#ifdef HAVE_POSIX_THREADS
    serial->currentFlowControlProc = NULL;
    serial->pendingFlowControlProc = NULL;
    serial->flowControlRunning = 0;
#endif /* HAVE_POSIX_THREADS */

    return 1;
  }

  return 0;
}

int
serialParseBaud (unsigned int *baud, const char *string) {
  if (isUnsignedInteger(baud, string)) return 1;

  logMessage(LOG_WARNING, "invalid serial baud: %s", string);
  return 0;
}

int
serialParseDataBits (unsigned int *bits, const char *string) {
  if (isUnsignedInteger(bits, string)) return 1;

  logMessage(LOG_WARNING, "invalid serial data bit count: %s", string);
  return 0;
}

int
serialParseStopBits (unsigned int *bits, const char *string) {
  if (isUnsignedInteger(bits, string)) return 1;

  logMessage(LOG_WARNING, "invalid serial stop bit count: %s", string);
  return 0;
}

int
serialParseParity (SerialParity *parity, const char *string) {
  if (isAbbreviation(string, "none")) {
    *parity = SERIAL_PARITY_NONE;
  } else if (isAbbreviation(string, "odd")) {
    *parity = SERIAL_PARITY_ODD;
  } else if (isAbbreviation(string, "even")) {
    *parity = SERIAL_PARITY_EVEN;
  } else if (isAbbreviation(string, "space")) {
    *parity = SERIAL_PARITY_SPACE;
  } else if (isAbbreviation(string, "mark")) {
    *parity = SERIAL_PARITY_MARK;
  } else {
    logMessage(LOG_WARNING, "invalid serial parity: %s", string);
    return 0;
  }

  return 1;
}

int
serialParseFlowControl (SerialFlowControl *flow, const char *string) {
  if (isAbbreviation(string, "none")) {
    *flow = SERIAL_FLOW_NONE;
  } else if (isAbbreviation(string, "hardware")) {
    *flow = SERIAL_FLOW_HARDWARE;
  } else {
    logMessage(LOG_WARNING, "invalid serial flow control: %s", string);
    return 0;
  }

  return 1;
}

static int
serialConfigureBaud (SerialDevice *serial, const char *string) {
  if (string && *string) {
    unsigned int baud;

    if (!serialParseBaud(&baud, string)) return 0;
    if (!serialSetBaud(serial, baud)) return 0;
  }

  return 1;
}

static int
serialConfigureDataBits (SerialDevice *serial, const char *string) {
  if (string && *string) {
    unsigned int bits;

    if (!serialParseDataBits(&bits, string)) return 0;
    if (!serialSetDataBits(serial, bits)) return 0;
  }

  return 1;
}

static int
serialConfigureStopBits (SerialDevice *serial, const char *string) {
  if (string && *string) {
    unsigned int bits;

    if (!serialParseStopBits(&bits, string)) return 0;
    if (!serialSetStopBits(serial, bits)) return 0;
  }

  return 1;
}

static int
serialConfigureParity (SerialDevice *serial, const char *string) {
  if (string && *string) {
    SerialParity parity;

    if (!serialParseParity(&parity, string)) return 0;
    if (!serialSetParity(serial, parity)) return 0;
  }

  return 1;
}

static int
serialConfigureFlowControl (SerialDevice *serial, const char *string) {
  if (string && *string) {
    SerialFlowControl flow;

    if (!serialParseFlowControl(&flow, string)) return 0;
    if (!serialSetFlowControl(serial, flow)) return 0;
  }

  return 1;
}

typedef enum {
  SERIAL_PARM_NAME,
  SERIAL_PARM_BAUD,
  SERIAL_PARM_DATA_BITS,
  SERIAL_PARM_STOP_BITS,
  SERIAL_PARM_DATA_PARITY,
  SERIAL_PARM_FLOW_CONTROL
} SerialDeviceParameter;

static const char *const serialDeviceParameterNames[] = {
  "name",
  "baud",
  "dataBits",
  "stopBits",
  "parity",
  "flowControl",
  NULL
};

static char **
serialGetDeviceParameters (const char *identifier) {
  if (!identifier) identifier = "";
  return getDeviceParameters(serialDeviceParameterNames, identifier);
}

SerialDevice *
serialOpenDevice (const char *identifier) {
  char **parameters = serialGetDeviceParameters(identifier);

  if (parameters) {
    SerialDevice *serial;

    if ((serial = malloc(sizeof(*serial)))) {
      memset(serial, 0, sizeof(*serial));

      {
        const char *name = parameters[SERIAL_PARM_NAME];
        if (!*name) name = SERIAL_FIRST_DEVICE;
        serial->devicePath = getDevicePath(name);
      }

      if (serial->devicePath) {
        serial->fileDescriptor = -1;
        serial->stream = NULL;

        if (serialConnectDevice(serial, serial->devicePath)) {
          int ok = 1;

          if (!serialConfigureBaud(serial, parameters[SERIAL_PARM_BAUD])) ok = 0;
          if (!serialConfigureDataBits(serial, parameters[SERIAL_PARM_DATA_BITS])) ok = 0;
          if (!serialConfigureStopBits(serial, parameters[SERIAL_PARM_STOP_BITS])) ok = 0;
          if (!serialConfigureParity(serial, parameters[SERIAL_PARM_DATA_PARITY])) ok = 0;
          if (!serialConfigureFlowControl(serial, parameters[SERIAL_PARM_FLOW_CONTROL])) ok = 0;

          deallocateStrings(parameters);
          if (ok) return serial;

          serialCloseDevice(serial);
          return NULL;
        }

        free(serial->devicePath);
      }

      free(serial);
    } else {
      logMallocError();
    }

    deallocateStrings(parameters);
  }

  return NULL;
}

void
serialCloseDevice (SerialDevice *serial) {
#ifdef HAVE_POSIX_THREADS
  serialStopFlowControlThread(serial);
#endif /* HAVE_POSIX_THREADS */

  serialWriteAttributes(serial, &serial->originalAttributes);

  if (serial->stream) {
    fclose(serial->stream);
  } else if (serial->fileDescriptor != -1) {
    close(serial->fileDescriptor);
  } else {
    serialDisconnectDevice(serial);
  }

  free(serial->devicePath);
  free(serial);
}

const char *
serialMakeDeviceIdentifier (SerialDevice *serial, char *buffer, size_t size) {
  STR_BEGIN(buffer, size);

  STR_PRINTF(
    "%s%c%s%c%s",
    SERIAL_DEVICE_QUALIFIER,
    PARAMETER_QUALIFIER_CHARACTER,
    serialDeviceParameterNames[SERIAL_PARM_NAME],
    PARAMETER_ASSIGNMENT_CHARACTER,
    serialGetDevicePath(serial)
  );

  STR_END;
  return buffer;
}

int
serialRestartDevice (SerialDevice *serial, unsigned int baud) {
  SerialLines highLines = 0;
  SerialLines lowLines = 0;
  int usingB0;

#ifdef HAVE_POSIX_THREADS
  SerialFlowControlProc *flowControlProc = serial->pendingFlowControlProc;
#endif /* HAVE_POSIX_THREADS */

  logMessage(LOG_CATEGORY(SERIAL_IO), "restarting");

  if (serial->stream) {
#if defined(GRUB_RUNTIME)
#else /* clearerr() */
    clearerr(serial->stream);
#endif /* clear error on stdio stream */
  }

  serialClearError(serial);

  if (!serialDiscardOutput(serial)) return 0;

#ifdef HAVE_POSIX_THREADS
  serial->pendingFlowControlProc = NULL;
#endif /* HAVE_POSIX_THREADS */

#ifdef B0
  if (!serialPutSpeed(&serial->pendingAttributes, B0)) return 0;
  usingB0 = 1;
#else /* B0 */
  usingB0 = 0;
#endif /* B0 */

  if (!serialFlushAttributes(serial)) {
    if (!usingB0) return 0;
    if (!serialSetBaud(serial, baud)) return 0;
    if (!serialFlushAttributes(serial)) return 0;
    usingB0 = 0;
  }

  if (!usingB0) {
    SerialLines lines;
    if (!serialReadLines(serial, &lines)) return 0;

    {
      static const SerialLines linesTable[] = {SERIAL_LINE_DTR, SERIAL_LINE_RTS, 0};
      const SerialLines *line = linesTable;

      while (*line) {
        *((lines & *line)? &highLines: &lowLines) |= *line;
        line += 1;
      }
    }

    if (highLines)
      if (!serialWriteLines(serial, 0, highLines|lowLines))
        return 0;
  }

  asyncWait(SERIAL_DEVICE_RESTART_DELAY);
  if (!serialDiscardInput(serial)) return 0;

  if (!usingB0)
    if (!serialWriteLines(serial, highLines, lowLines))
      return 0;

#ifdef HAVE_POSIX_THREADS
  serial->pendingFlowControlProc = flowControlProc;
#endif /* HAVE_POSIX_THREADS */

  if (!serialSetBaud(serial, baud)) return 0;
  if (!serialFlushAttributes(serial)) return 0;

  logMessage(LOG_CATEGORY(SERIAL_IO), "restarted");
  return 1;
}

FILE *
serialGetStream (SerialDevice *serial) {
  if (!serial->stream) {
    if (!serialEnsureFileDescriptor(serial)) return NULL;

#if defined(GRUB_RUNTIME)
    errno = ENOSYS;
#else /* fdopen() */
    serial->stream = fdopen(serial->fileDescriptor, "ab+");
#endif /* create stdio stream */

    if (!serial->stream) {
      logSystemError("fdopen");
      return NULL;
    }
  }

  return serial->stream;
}

int
isSerialDeviceIdentifier (const char **identifier) {
#ifdef ALLOW_DOS_DEVICE_NAMES
  if (isDosDevice(*identifier, "COM")) return 1;
#endif /* ALLOW_DOS_DEVICE_NAMES */

  if (hasQualifier(identifier, SERIAL_DEVICE_QUALIFIER)) return 1;
  if (hasNoQualifier(*identifier)) return 1;
  return 0;
}
