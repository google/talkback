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
#include <sys/stat.h>
#include <fcntl.h>

#include "log.h"
#include "io_misc.h"
#include "async_io.h"

#include "serial_termios.h"
#include "serial_internal.h"

BEGIN_SERIAL_BAUD_TABLE
#ifdef B50
  {50, B50},
#endif /* B50 */

#ifdef B75
  {75, B75},
#endif /* B75 */

#ifdef B110
  {110, B110},
#endif /* B110 */

#ifdef B134
  {134, B134},
#endif /* B134 */

#ifdef B150
  {150, B150},
#endif /* B150 */

#ifdef B200
  {200, B200},
#endif /* B200 */

#ifdef B300
  {300, B300},
#endif /* B300 */

#ifdef B600
  {600, B600},
#endif /* B600 */

#ifdef B1200
  {1200, B1200},
#endif /* B1200 */

#ifdef B1800
  {1800, B1800},
#endif /* B1800 */

#ifdef B2400
  {2400, B2400},
#endif /* B2400 */

#ifdef B4800
  {4800, B4800},
#endif /* B4800 */

#ifdef B9600
  {9600, B9600},
#endif /* B9600 */

#ifdef B19200
  {19200, B19200},
#endif /* B19200 */

#ifdef B38400
  {38400, B38400},
#endif /* B38400 */

#ifdef B57600
  {57600, B57600},
#endif /* B57600 */

#ifdef B115200
  {115200, B115200},
#endif /* B115200 */

#ifdef B230400
  {230400, B230400},
#endif /* B230400 */

#ifdef B460800
  {460800, B460800},
#endif /* B460800 */

#ifdef B500000
  {500000, B500000},
#endif /* B500000 */

#ifdef B576000
  {576000, B576000},
#endif /* B576000 */

#ifdef B921600
  {921600, B921600},
#endif /* B921600 */

#ifdef B1000000
  {1000000, B1000000},
#endif /* B1000000 */

#ifdef B1152000
  {1152000, B1152000},
#endif /* B1152000 */

#ifdef B1500000
  {1500000, B1500000},
#endif /* B1500000 */

#ifdef B2000000
  {2000000, B2000000},
#endif /* B2000000 */

#ifdef B2500000
  {2500000, B2500000},
#endif /* B2500000 */

#ifdef B3000000
  {3000000, B3000000},
#endif /* B3000000 */

#ifdef B3500000
  {3500000, B3500000},
#endif /* B3500000 */

#ifdef B4000000
  {4000000, B4000000},
#endif /* B4000000 */
END_SERIAL_BAUD_TABLE

void
serialPutInitialAttributes (SerialAttributes *attributes) {
  attributes->c_cflag = CREAD;
  attributes->c_iflag = IGNPAR | IGNBRK;

#ifdef IEXTEN
  attributes->c_lflag |= IEXTEN;
#endif /* IEXTEN */

#ifdef _POSIX_VDISABLE
  if (_POSIX_VDISABLE) {
    unsigned int i;

    for (i=0; i<NCCS; i+=1) {
      if (i == VTIME) continue;
      if (i == VMIN) continue;
      attributes->c_cc[i] = _POSIX_VDISABLE;
    }
  }
#endif /* _POSIX_VDISABLE */
}

int
serialPutSpeed (SerialAttributes *attributes, SerialSpeed speed) {
  if (cfsetospeed(attributes, speed) != -1) {
    if (cfsetispeed(attributes, speed) != -1) {
      return 1;
    } else {
      logSystemError("cfsetispeed");
    }
  } else {
    logSystemError("cfsetospeed");
  }

  return 0;
}

int
serialPutDataBits (SerialAttributes *attributes, unsigned int bits) {
  tcflag_t size;

  switch (bits) {
#ifdef CS5
#if !defined(CS6) || (CS5 != CS6)
    case 5: size = CS5; break;
#endif
#endif /* CS5 */

#ifdef CS6
#if !defined(CS7) || (CS6 != CS7)
    case 6: size = CS6; break;
#endif
#endif /* CS6 */

#ifdef CS7
    case 7: size = CS7; break;
#endif /* CS7 */

#ifdef CS8
    case 8: size = CS8; break;
#endif /* CS8 */

    default:
      return 0;
  }

  attributes->c_cflag &= ~CSIZE;
  attributes->c_cflag |= size;
  return 1;
}

int
serialPutStopBits (SerialAttributes *attributes, SerialStopBits bits) {
  if (bits == SERIAL_STOP_1) {
    attributes->c_cflag &= ~CSTOPB;
  } else if (bits == SERIAL_STOP_2) {
    attributes->c_cflag |= CSTOPB;
  } else {
    return 0;
  }

  return 1;
}

int
serialPutParity (SerialAttributes *attributes, SerialParity parity) {
  attributes->c_cflag &= ~(PARENB | PARODD);

#ifdef PARSTK
  attributes->c_cflag &= ~PARSTK;
#endif /* PARSTK */

  if (parity != SERIAL_PARITY_NONE) {
    if (parity == SERIAL_PARITY_ODD) {
      attributes->c_cflag |= PARODD;
    } else

#ifndef PARSTK
#ifdef CMSPAR
#define PARSTK CMSPAR
#endif /* CMSPAR */
#endif /* PARSTK */

#ifdef PARSTK
    if (parity == SERIAL_PARITY_SPACE) {
      attributes->c_cflag |= PARSTK;
    } else

    if (parity == SERIAL_PARITY_MARK) {
      attributes->c_cflag |= PARSTK | PARODD;
    } else
#endif /* PARSTK */

    if (parity != SERIAL_PARITY_EVEN) {
      return 0;
    }

    attributes->c_cflag |= PARENB;
  }

  return 1;
}

SerialFlowControl
serialPutFlowControl (SerialAttributes *attributes, SerialFlowControl flow) {
  typedef struct {
    tcflag_t *field;
    tcflag_t flag;
    SerialFlowControl flow;
  } FlowControlEntry;

  const FlowControlEntry flowControlTable[] = {
#ifdef CRTSCTS
    {&attributes->c_cflag, CRTSCTS, SERIAL_FLOW_OUTPUT_RTS | SERIAL_FLOW_OUTPUT_CTS},
#endif /* CRTSCTS */

#ifdef IHFLOW
    {&attributes->c_cflag, IHFLOW, SERIAL_FLOW_INPUT_RTS},
#endif /* IHFLOW */

#ifdef OHFLOW
    {&attributes->c_cflag, OHFLOW, SERIAL_FLOW_OUTPUT_CTS},
#endif /* OHFLOW */

#ifdef IXOFF
    {&attributes->c_iflag, IXOFF, SERIAL_FLOW_INPUT_XON},
#endif /* IXOFF */

#ifdef IXON
    {&attributes->c_iflag, IXON, SERIAL_FLOW_OUTPUT_XON},
#endif /* IXON */

    {NULL, 0, 0}
  };
  const FlowControlEntry *entry = flowControlTable;

  while (entry->field) {
    if ((flow & entry->flow) == entry->flow) {
      flow &= ~entry->flow;
      *entry->field |= entry->flag;
    } else if (!(flow & entry->flow)) {
      *entry->field &= ~entry->flag;
    }

    entry += 1;
  }

  return flow;
}

int
serialPutModemState (SerialAttributes *attributes, int enabled) {
  if (enabled) {
    attributes->c_cflag &= ~CLOCAL;
  } else {
    attributes->c_cflag |= CLOCAL;
  }

  return 1;
}

unsigned int
serialGetDataBits (const SerialAttributes *attributes) {
  tcflag_t size = attributes->c_cflag & CSIZE;

  switch (size) {
#ifdef CS5
#if !defined(CS6) || (CS5 != CS6)
    case CS5: return 5;
#endif
#endif /* CS5 */

#ifdef CS6
#if !defined(CS7) || (CS6 != CS7)
    case CS6: return 6;
#endif
#endif /* CS6 */

#ifdef CS7
    case CS7: return 7;
#endif /* CS7 */

#ifdef CS8
    case CS8: return 8;
#endif /* CS8 */

    default:
      logMessage(LOG_WARNING, "unsupported termios data bits: %lX", (unsigned long)size);
      return 0;
  }
}

unsigned int
serialGetStopBits (const SerialAttributes *attributes) {
  return (attributes->c_cflag & CSTOPB)? 2: 1;
}

unsigned int
serialGetParityBits (const SerialAttributes *attributes) {
  return (attributes->c_cflag & PARENB)? 1: 0;
}

int
serialGetAttributes (SerialDevice *serial, SerialAttributes *attributes) {
  if (tcgetattr(serial->fileDescriptor, attributes) != -1) return 1;
  logSystemError("tcgetattr");
  return 0;
}

int
serialPutAttributes (SerialDevice *serial, const SerialAttributes *attributes) {
  if (tcsetattr(serial->fileDescriptor, TCSANOW, attributes) != -1) return 1;
  logSystemError("tcsetattr");
  return 0;
}

int
serialCancelInput (SerialDevice *serial) {
  if (tcflush(serial->fileDescriptor, TCIFLUSH) != -1) return 1;
  if (errno == EINVAL) return 1;
  logSystemError("TCIFLUSH");
  return 0;
}

int
serialCancelOutput (SerialDevice *serial) {
  if (tcflush(serial->fileDescriptor, TCOFLUSH) != -1) return 1;
  if (errno == EINVAL) return 1;
  logSystemError("TCOFLUSH");
  return 0;
}

static void
serialCancelInputMonitor (SerialDevice *serial) {
  if (serial->package.inputMonitor) {
    asyncCancelRequest(serial->package.inputMonitor);
    serial->package.inputMonitor = NULL;
  }
}

int
serialMonitorInput (SerialDevice *serial, AsyncMonitorCallback *callback, void *data) {
  serialCancelInputMonitor(serial);
  if (!callback) return 1;
  return asyncMonitorFileInput(&serial->package.inputMonitor, serial->fileDescriptor, callback, data);
}

int
serialPollInput (SerialDevice *serial, int timeout) {
  return awaitFileInput(serial->fileDescriptor, timeout);
}

int
serialDrainOutput (SerialDevice *serial) {
#ifdef HAVE_TCDRAIN
  do {
    if (tcdrain(serial->fileDescriptor) != -1) return 1;
  } while (errno == EINTR);
#else /* HAVE_TCDRAIN */
  errno = ENOSYS;
#endif /* HAVE_TCDRAIN */

  logSystemError("tcdrain");
  return 0;
}

ssize_t
serialGetData (
  SerialDevice *serial,
  void *buffer, size_t size,
  int initialTimeout, int subsequentTimeout
) {
  return readFile(serial->fileDescriptor, buffer, size, initialTimeout, subsequentTimeout);
}

ssize_t
serialPutData (
  SerialDevice *serial,
  const void *data, size_t size
) {
  return writeFile(serial->fileDescriptor, data, size);
}

int
serialGetLines (SerialDevice *serial) {
#ifdef TIOCMGET
  if (ioctl(serial->fileDescriptor, TIOCMGET, &serial->linesState) == -1) {
    logSystemError("TIOCMGET");
    return 0;
  }
#else /* TIOCMGET */
#warning getting modem lines not supported on this platform
  serial->linesState = SERIAL_LINE_RTS | SERIAL_LINE_CTS | SERIAL_LINE_DTR | SERIAL_LINE_DSR | SERIAL_LINE_CAR;
#endif /* TIOCMGET */

  return 1;
}

int
serialPutLines (SerialDevice *serial, SerialLines high, SerialLines low) {
#ifdef TIOCMSET
  if (serialGetLines(serial)) {
    SerialLines lines = serial->linesState;
    lines |= high;
    lines &= ~low;

    if (ioctl(serial->fileDescriptor, TIOCMSET, &lines) != -1) return 1;
    logSystemError("TIOCMSET");
  }
#else /* TIOCMSET */
#warning setting modem lines not supported on this platform
#endif /* TIOCMSET */

  return 0;
}

int
serialRegisterWaitLines (SerialDevice *serial, SerialLines lines) {
  return 1;
}

int
serialMonitorWaitLines (SerialDevice *serial) {
#ifdef TIOCMIWAIT
  if (ioctl(serial->fileDescriptor, TIOCMIWAIT, serial->waitLines) != -1) return 1;
  logSystemError("TIOCMIWAIT");
#else /* TIOCMIWAIT */
  SerialLines old = serial->linesState & serial->waitLines;

  while (serialGetLines(serial)) {
    if ((serial->linesState & serial->waitLines) != old) return 1;
  }
#endif /* TIOCMIWAIT */

  return 0;
}

int
serialConnectDevice (SerialDevice *serial, const char *device) {
  serial->package.inputMonitor = NULL;

  if ((serial->fileDescriptor = open(device, O_RDWR|O_NOCTTY|O_NONBLOCK)) != -1) {
    setCloseOnExec(serial->fileDescriptor, 1);

    if (isatty(serial->fileDescriptor)) {
      if (serialPrepareDevice(serial)) {
        logMessage(LOG_CATEGORY(SERIAL_IO), "device opened: %s: fd=%d",
                   device, serial->fileDescriptor);
        return 1;
      }
    } else {
      logMessage(LOG_ERR, "not a serial device: %s", device);
    }

    close(serial->fileDescriptor);
  } else {
    logMessage(((errno == ENOENT)? LOG_DEBUG: LOG_ERR),
               "cannot open serial device: %s: %s",
               device, strerror(errno));
  }

  return 0;
}

void
serialDisconnectDevice (SerialDevice *serial) {
  serialCancelInputMonitor(serial);
}

int
serialEnsureFileDescriptor (SerialDevice *serial) {
  return 1;
}

void
serialClearError (SerialDevice *serial) {
}
