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

#ifndef BRLTTY_INCLUDED_SERIAL_INTERNAL
#define BRLTTY_INCLUDED_SERIAL_INTERNAL

#include "prologue.h"

#include <stdio.h>

#include "io_serial.h"
#include "thread.h"

#ifdef __cplusplus
extern "C" {
#endif /* __cplusplus */

typedef void SerialFlowControlProc (SerialDevice *serial);

struct SerialDeviceStruct {
  char *devicePath;
  int fileDescriptor;
  FILE *stream;

  SerialAttributes originalAttributes;
  SerialAttributes currentAttributes;
  SerialAttributes pendingAttributes;

  SerialLines linesState;
  SerialLines waitLines;

#ifdef HAVE_POSIX_THREADS
  SerialFlowControlProc *currentFlowControlProc;
  SerialFlowControlProc *pendingFlowControlProc;
  pthread_t flowControlThread;
  unsigned flowControlRunning:1;
  unsigned flowControlStop:1;
#endif /* HAVE_POSIX_THREADS */

  SerialPackageFields package;
};

typedef struct {
  unsigned int baud;
  SerialSpeed speed;
} SerialBaudEntry;

extern const SerialBaudEntry *serialGetBaudEntry (unsigned int baud);
#define SERIAL_BAUD_TABLE_DECLARATION  const SerialBaudEntry serialBaudTable[]
extern SERIAL_BAUD_TABLE_DECLARATION;
#define BEGIN_SERIAL_BAUD_TABLE SERIAL_BAUD_TABLE_DECLARATION = {
#define END_SERIAL_BAUD_TABLE {0} };

extern void serialPutInitialAttributes (SerialAttributes *attributes);
extern int serialPutSpeed (SerialAttributes *attributes, SerialSpeed speed);
extern int serialPutDataBits (SerialAttributes *attributes, unsigned int bits);
extern int serialPutStopBits (SerialAttributes *attributes, SerialStopBits bits);
extern int serialPutParity (SerialAttributes *attributes, SerialParity parity);
extern SerialFlowControl serialPutFlowControl (SerialAttributes *attributes, SerialFlowControl flow);
extern int serialPutModemState (SerialAttributes *attributes, int enabled);

extern unsigned int serialGetDataBits (const SerialAttributes *attributes);
extern unsigned int serialGetStopBits (const SerialAttributes *attributes);
extern unsigned int serialGetParityBits (const SerialAttributes *attributes);

extern int serialGetAttributes (SerialDevice *serial, SerialAttributes *attributes);
extern int serialPutAttributes (SerialDevice *serial, const SerialAttributes *attributes);

extern int serialCancelInput (SerialDevice *serial);
extern int serialCancelOutput (SerialDevice *serial);

extern int serialPollInput (SerialDevice *serial, int timeout);
extern int serialDrainOutput (SerialDevice *serial);

extern ssize_t serialGetData (
  SerialDevice *serial,
  void *buffer, size_t size,
  int initialTimeout, int subsequentTimeout
);

extern ssize_t serialPutData (
  SerialDevice *serial,
  const void *data, size_t size
);

extern int serialGetLines (SerialDevice *serial);
extern int serialPutLines (SerialDevice *serial, SerialLines high, SerialLines low);

extern int serialRegisterWaitLines (SerialDevice *serial, SerialLines lines);
extern int serialMonitorWaitLines (SerialDevice *serial);

extern int serialConnectDevice (SerialDevice *serial, const char *device);
extern int serialPrepareDevice (SerialDevice *serial);

extern void serialDisconnectDevice (SerialDevice *serial);
extern int serialEnsureFileDescriptor (SerialDevice *serial);
extern void serialClearError (SerialDevice *serial);

#ifdef __cplusplus
}
#endif /* __cplusplus */

#endif /* BRLTTY_INCLUDED_SERIAL_INTERNAL */
