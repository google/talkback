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

#include <errno.h>

#include "log.h"
#include "timing.h"

#include "serial_grub.h"
#include "serial_internal.h"

#define SERIAL_NO_BYTE -1

#define SERIAL_BAUD_ENTRY(baud) {(baud), (baud)}

BEGIN_SERIAL_BAUD_TABLE
  SERIAL_BAUD_ENTRY(   110),
  SERIAL_BAUD_ENTRY(   150),
  SERIAL_BAUD_ENTRY(   300),
  SERIAL_BAUD_ENTRY(   600),
  SERIAL_BAUD_ENTRY(  1200),
  SERIAL_BAUD_ENTRY(  2400),
  SERIAL_BAUD_ENTRY(  4800),
  SERIAL_BAUD_ENTRY(  9600),
  SERIAL_BAUD_ENTRY( 19200),
  SERIAL_BAUD_ENTRY( 38400),
  SERIAL_BAUD_ENTRY( 57600),
  SERIAL_BAUD_ENTRY(115200),
END_SERIAL_BAUD_TABLE

void
serialPutInitialAttributes (SerialAttributes *attributes) {
}

int
serialPutSpeed (SerialAttributes *attributes, SerialSpeed speed) {
  attributes->speed = speed;
  return 1;
}

int
serialPutDataBits (SerialAttributes *attributes, unsigned int bits) {
  if ((bits < 5) || (bits > 8)) return 0;
  attributes->word_len = bits;
  return 1;
}

int
serialPutStopBits (SerialAttributes *attributes, SerialStopBits bits) {
  switch (bits) {
    case SERIAL_STOP_1:
      attributes->stop_bits = GRUB_SERIAL_STOP_BITS_1;
      break;

    case SERIAL_STOP_2:
      attributes->stop_bits = GRUB_SERIAL_STOP_BITS_2;
      break;

    default:
      return 0;
  }

  return 1;
}

int
serialPutParity (SerialAttributes *attributes, SerialParity parity) {
  switch (parity) {
    case SERIAL_PARITY_NONE:
      attributes->parity = GRUB_SERIAL_PARITY_NONE;
      break;

    case SERIAL_PARITY_ODD:
      attributes->parity = GRUB_SERIAL_PARITY_ODD;
      break;

    case SERIAL_PARITY_EVEN:
      attributes->parity = GRUB_SERIAL_PARITY_EVEN;
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
  return 0;
}

unsigned int
serialGetDataBits (const SerialAttributes *attributes) {
  return attributes->word_len;
}

unsigned int
serialGetStopBits (const SerialAttributes *attributes) {
  switch (attributes->stop_bits) {
    case GRUB_SERIAL_STOP_BITS_1: return 1;
    case GRUB_SERIAL_STOP_BITS_2: return 2;
    default:                      return 0;
  }
}

unsigned int
serialGetParityBits (const SerialAttributes *attributes) {
  return (attributes->parity == GRUB_SERIAL_PARITY_NONE)? 0: 1;
}

int
serialGetAttributes (SerialDevice *serial, SerialAttributes *attributes) {
  *attributes = serial->package.port->config;
  return 1;
}

int
serialPutAttributes (SerialDevice *serial, const SerialAttributes *attributes) {
  grub_err_t result = serial->package.port->driver->configure(serial->package.port, attributes);
  if (result == GRUB_ERR_NONE) return 1;
  return 0;
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
  if (serial->package.byte == SERIAL_NO_BYTE) {
    TimePeriod period;
    startTimePeriod(&period, timeout);

    while ((serial->package.byte = serial->package.port->driver->fetch(serial->package.port)) == SERIAL_NO_BYTE) {
      if (afterTimePeriod(&period, NULL)) {
        errno = EAGAIN;
        return 0;
      }

      approximateDelay(1);
    }
  }

  return 1;
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
  unsigned char *byte = buffer;
  const unsigned char *const first = byte;
  const unsigned char *const end = first + size;
  int timeout = initialTimeout;

  while (byte < end) {
    if (!serialPollInput(serial, timeout)) break;
    *byte++ = serial->package.byte;
    serial->package.byte = SERIAL_NO_BYTE;
    timeout = subsequentTimeout;
  }

  {
    size_t count = byte - first;
    if (count) return count;
  }

  errno = EAGAIN;
  return -1;
}

ssize_t
serialPutData (
  SerialDevice *serial,
  const void *data, size_t size
) {
  const unsigned char *byte = data;
  const unsigned char *end = byte + size;

  while (byte < end) {
    serial->package.port->driver->put(serial->package.port, *byte++);
  }

  return size;
}

int
serialGetLines (SerialDevice *serial) {
  errno = ENOSYS;
  return 0;
}

int
serialPutLines (SerialDevice *serial, SerialLines high, SerialLines low) {
  errno = ENOSYS;
  return 0;
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
  if ((serial->package.port = grub_serial_find(device))) {
    serial->package.byte = SERIAL_NO_BYTE;

    if (serialPrepareDevice(serial)) {
      logMessage(LOG_CATEGORY(SERIAL_IO), "device opened: %s",
                 device);
      return 1;
    }
  } else {
    logMessage(LOG_ERR, "cannot find serial device: %s", device);
    errno = ENOENT;
  }

  return 0;
}

void
serialDisconnectDevice (SerialDevice *serial) {
  serial->package.port = NULL;
}

int
serialEnsureFileDescriptor (SerialDevice *serial) {
  return 1;
}

void
serialClearError (SerialDevice *serial) {
}

