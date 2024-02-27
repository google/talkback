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

#include <errno.h>

#include "serial_none.h"
#include "serial_internal.h"

BEGIN_SERIAL_BAUD_TABLE
END_SERIAL_BAUD_TABLE

void
serialPutInitialAttributes (SerialAttributes *attributes) {
}

int
serialPutSpeed (SerialAttributes *attributes, SerialSpeed speed) {
  return 0;
}

int
serialPutDataBits (SerialAttributes *attributes, unsigned int bits) {
  return 0;
}

int
serialPutStopBits (SerialAttributes *attributes, SerialStopBits bits) {
  return 0;
}

int
serialPutParity (SerialAttributes *attributes, SerialParity parity) {
  return 0;
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
  return 8;
}

unsigned int
serialGetStopBits (const SerialAttributes *attributes) {
  return 1;
}

unsigned int
serialGetParityBits (const SerialAttributes *attributes) {
  return 0;
}

int
serialGetAttributes (SerialDevice *serial, SerialAttributes *attributes) {
  errno = ENOSYS;
  return 0;
}

int
serialPutAttributes (SerialDevice *serial, const SerialAttributes *attributes) {
  errno = ENOSYS;
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
  errno = ENOSYS;
  return -1;
}

ssize_t
serialPutData (
  SerialDevice *serial,
  const void *data, size_t size
) {
  errno = ENOSYS;
  return -1;
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
  errno = ENOENT;
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

