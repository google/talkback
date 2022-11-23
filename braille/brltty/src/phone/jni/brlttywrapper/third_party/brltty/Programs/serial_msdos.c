/*
 * BRLTTY - A background process providing access to the console screen (when in
 *          text mode) for a blind person using a refreshable braille display.
 *
 * Copyright (C) 1995-2019 by The BRLTTY Developers.
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
#include "async_wait.h"
#include "timing.h"
#include "io_misc.h"
#include "ports.h"

#include "serial_msdos.h"
#include "serial_internal.h"

#include <dos.h>
#include <dpmi.h>
#include <bios.h>
#include <go32.h>
#include <sys/farptr.h>

#define SERIAL_DIVISOR_BASE 115200
#define SERIAL_DIVISOR(baud) (SERIAL_DIVISOR_BASE / (baud))
#define SERIAL_SPEED(baud) {.divisor=SERIAL_DIVISOR(baud), .bps=SERIAL_BIOS_BAUD_##baud}
#define SERIAL_BAUD_ENTRY(baud) {baud, .speed=SERIAL_SPEED(baud)}

BEGIN_SERIAL_BAUD_TABLE
  SERIAL_BAUD_ENTRY(110),
  SERIAL_BAUD_ENTRY(150),
  SERIAL_BAUD_ENTRY(300),
  SERIAL_BAUD_ENTRY(600),
  SERIAL_BAUD_ENTRY(1200),
  SERIAL_BAUD_ENTRY(2400),
  SERIAL_BAUD_ENTRY(4800),
  SERIAL_BAUD_ENTRY(9600),
  SERIAL_BAUD_ENTRY(19200),
  SERIAL_BAUD_ENTRY(38400),
  SERIAL_BAUD_ENTRY(57600),
  SERIAL_BAUD_ENTRY(115200),
END_SERIAL_BAUD_TABLE

static inline int
serialGetPort (SerialDevice *serial) {
  return serial->package.deviceIndex;
}

static unsigned short
serialPortBase (SerialDevice *serial) {
  return _farpeekw(_dos_ds, (0X0400 + (2 * serialGetPort(serial))));
}

static unsigned char
serialReadPort (SerialDevice *serial, unsigned char port) {
  return readPort1(serialPortBase(serial)+port);
}

static void
serialWritePort (SerialDevice *serial, unsigned char port, unsigned char value) {
  writePort1(serialPortBase(serial)+port, value);
}

static unsigned int
serialBiosCommand (SerialDevice *serial, int command, unsigned char data) {
  return _bios_serialcom(command, serialGetPort(serial), data);
}

static int
serialTestInput (SerialDevice *serial) {
  return !!(serialBiosCommand(serial, _COM_STATUS, 0) & SERIAL_BIOS_STATUS_DATA_READY);
}

void
serialPutInitialAttributes (SerialAttributes *attributes) {
  attributes->speed = serialGetBaudEntry(9600)->speed;
  attributes->bios.fields.bps = attributes->speed.bps;
  attributes->bios.fields.dataBits = SERIAL_BIOS_DATA_8;
  attributes->bios.fields.stopBits = SERIAL_BIOS_STOP_1;
  attributes->bios.fields.parity = SERIAL_BIOS_PARITY_NONE;
}

int
serialPutSpeed (SerialAttributes *attributes, SerialSpeed speed) {
  logMessage(LOG_CATEGORY(SERIAL_IO), "put speed: bps=%u divisor=%u",
             speed.bps, speed.divisor);

  attributes->speed = speed;
  attributes->bios.fields.bps = attributes->speed.bps;
  return 1;
}

int
serialPutDataBits (SerialAttributes *attributes, unsigned int bits) {
  if (bits == 8) {
    attributes->bios.fields.dataBits = SERIAL_BIOS_DATA_8;
  } else if (bits == 7) {
    attributes->bios.fields.dataBits = SERIAL_BIOS_DATA_7;
  } else {
    return 0;
  }

  return 1;
}

int
serialPutStopBits (SerialAttributes *attributes, SerialStopBits bits) {
  if (bits == SERIAL_STOP_1) {
    attributes->bios.fields.stopBits = SERIAL_BIOS_STOP_1;
  } else if (bits == SERIAL_STOP_2) {
    attributes->bios.fields.stopBits = SERIAL_BIOS_STOP_2;
  } else {
    return 0;
  }

  return 1;
}

int
serialPutParity (SerialAttributes *attributes, SerialParity parity) {
  switch (parity) {
    case SERIAL_PARITY_NONE:
      attributes->bios.fields.parity = SERIAL_BIOS_PARITY_NONE;
      break;

    case SERIAL_PARITY_ODD:
      attributes->bios.fields.parity = SERIAL_BIOS_PARITY_ODD;
      break;

    case SERIAL_PARITY_EVEN:
      attributes->bios.fields.parity = SERIAL_BIOS_PARITY_EVEN;
      break;

    default:
      return 0;
  }

  return 1;
}

SerialFlowControl
serialPutFlowControl (SerialAttributes *attributes, SerialFlowControl flow) {
  return flow;
}

int
serialPutModemState (SerialAttributes *attributes, int enabled) {
  return !enabled;
}

unsigned int
serialGetDataBits (const SerialAttributes *attributes) {
  switch (attributes->bios.fields.dataBits) {
    default:
    case SERIAL_BIOS_DATA_8: return 8;
    case SERIAL_BIOS_DATA_7: return 7;
  }
}

unsigned int
serialGetStopBits (const SerialAttributes *attributes) {
  switch (attributes->bios.fields.stopBits) {
    default:
    case SERIAL_BIOS_STOP_1: return 1;
    case SERIAL_BIOS_STOP_2: return 2;
  }
}

unsigned int
serialGetParityBits (const SerialAttributes *attributes) {
  return (attributes->bios.fields.parity == SERIAL_BIOS_PARITY_NONE)? 0: 1;
}

int
serialGetAttributes (SerialDevice *serial, SerialAttributes *attributes) {
  unsigned char lcr;
  int divisor;

  {
    int wasEnabled = disable();

    lcr = serialReadPort(serial, UART_PORT_LCR);
    serialWritePort(serial, UART_PORT_LCR, (lcr | UART_FLAG_LCR_DLAB));

    divisor = (serialReadPort(serial, UART_PORT_DLH) << 8) |
               serialReadPort(serial, UART_PORT_DLL);
    serialWritePort(serial, UART_PORT_LCR, lcr);

    if (wasEnabled) enable();
  }

  attributes->bios.byte = lcr;

  {
    const SerialBaudEntry *baud = serialGetBaudEntry(SERIAL_DIVISOR_BASE/divisor);

    if (baud) {
      attributes->speed = baud->speed;
    } else {
      logMessage(LOG_WARNING, "unsupported serial divisor: %d", divisor);
      memset(&attributes->speed, 0, sizeof(attributes->speed));
    }
  }

  attributes->bios.fields.bps = attributes->speed.bps;
  return 1;
}

int
serialPutAttributes (SerialDevice *serial, const SerialAttributes *attributes) {
  if (attributes->speed.bps < (0X1 << 3)) {
    unsigned char byte = attributes->bios.byte;

    logMessage(LOG_CATEGORY(SERIAL_IO), "put attributes: port=%d byte=0X%02X",
               serialGetPort(serial), byte);
    serialBiosCommand(serial, _COM_INIT, byte);
  } else {
    SerialBiosConfiguration lcr = attributes->bios;

    lcr.fields.bps = 0;
    logMessage(LOG_CATEGORY(SERIAL_IO), "put attributes: port=%d lcr=0X%02X divisor=%u",
               serialGetPort(serial), lcr.byte, attributes->speed.divisor);

    {
      int wasEnabled = disable();

      serialWritePort(serial, UART_PORT_LCR, (lcr.byte | UART_FLAG_LCR_DLAB));
      serialWritePort(serial, UART_PORT_DLL, (attributes->speed.divisor & 0XFF));
      serialWritePort(serial, UART_PORT_DLH, (attributes->speed.divisor >> 8));
      serialWritePort(serial, UART_PORT_LCR, lcr.byte);

      if (wasEnabled) enable();
    }
  }

  return 1;
}

int
serialCancelInput (SerialDevice *serial) {
  return 1;
}

int
serialCancelOutput (SerialDevice *serial) {
  return 1;
}

int
serialMonitorInput (SerialDevice *serial, AsyncMonitorCallback *callback, void *data) {
  return 0;
}

int
serialPollInput (SerialDevice *serial, int timeout) {
  TimePeriod period;

  if (timeout) startTimePeriod(&period, timeout);

  while (1) {
    if (serialTestInput(serial)) return 1;
    if (!timeout) break;
    if (afterTimePeriod(&period, NULL)) break;
    asyncWait(1);
  }

  errno = EAGAIN;
  return 0;
}

int
serialDrainOutput (SerialDevice *serial) {
  return 1;
}

ssize_t
serialGetData (
  SerialDevice *serial,
  void *buffer, size_t size,
  int initialTimeout, int subsequentTimeout
) {
  unsigned char *const start = buffer;
  unsigned char *const end = start + size;
  unsigned char *byte = start;
  int timeout = initialTimeout;

  while (byte < end) {
    if (!serialPollInput(serial, timeout)) break;
    timeout = subsequentTimeout;

    {
      int status = serialBiosCommand(serial, _COM_RECEIVE, 0);

      *byte++ = status & 0XFF;
    }
  }

  return byte - start;
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
  serial->linesState = serialReadPort(serial, UART_PORT_MSR) & 0XF0;
  return 1;
}

int
serialPutLines (SerialDevice *serial, SerialLines high, SerialLines low) {
  int wasEnabled = disable();
  unsigned char oldMCR = serialReadPort(serial, UART_PORT_MCR);

  serialWritePort(serial, UART_PORT_MCR,
                  (oldMCR | high) & ~low);
  if (wasEnabled) enable();
  return 1;
}

int
serialRegisterWaitLines (SerialDevice *serial, SerialLines lines) {
  return 1;
}

int
serialMonitorWaitLines (SerialDevice *serial) {
  return 0;
}

int
serialConnectDevice (SerialDevice *serial, const char *device) {
  if ((serial->fileDescriptor = open(device, O_RDWR|O_NOCTTY|O_NONBLOCK)) != -1) {
    serial->package.deviceIndex = -1;

    {
      char *truePath;

      if ((truePath = _truename(device, NULL))) {
        char *com;

        {
          char *c = truePath;

          while (*c) {
            *c = toupper(*c);
            c += 1;
          }
        }

        if ((com = strstr(truePath, "COM"))) {
          serial->package.deviceIndex = atoi(com+3) - 1;
        }

        free(truePath);
      }
    }

    if (serial->package.deviceIndex >= 0) {
      if (serialPrepareDevice(serial)) {
        logMessage(LOG_CATEGORY(SERIAL_IO), "device opened: %s: fd=%d",
                   device, serial->fileDescriptor);
        return 1;
      }
    } else {
      logMessage(LOG_ERR, "could not determine serial device number: %s", device);
    }

    close(serial->fileDescriptor);
  } else {
    logMessage(LOG_ERR, "cannot open serial device: %s: %s", device, strerror(errno));
  }

  return 0;
}

void
serialDisconnectDevice (SerialDevice *serial) {
}

int
serialEnsureFileDescriptor (SerialDevice *serial) {
  return 1;
}

void
serialClearError (SerialDevice *serial) {
}

