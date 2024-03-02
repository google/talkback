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

#ifndef BRLTTY_INCLUDED_IO_SERIAL
#define BRLTTY_INCLUDED_IO_SERIAL

#include <stdio.h>

#include "serial_types.h"
#include "async_types_io.h"

#ifdef __cplusplus
extern "C" {
#endif /* __cplusplus */

typedef struct SerialDeviceStruct SerialDevice;

#define SERIAL_DEVICE_QUALIFIER "serial"
extern int isSerialDeviceIdentifier (const char **identifier);

extern int serialValidateBaud (unsigned int *baud, const char *description, const char *word, const unsigned int *choices);

extern SerialDevice *serialOpenDevice (const char *identifier);
extern void serialCloseDevice (SerialDevice *serial);
extern int serialRestartDevice (SerialDevice *serial, unsigned int baud);

extern const char *serialMakeDeviceIdentifier (SerialDevice *serial, char *buffer, size_t size);
extern const char *serialGetDevicePath (SerialDevice *serial);
extern FILE *serialGetStream (SerialDevice *serial);

extern int serialDiscardInput (SerialDevice *serial);
extern int serialDiscardOutput (SerialDevice *serial);
extern int serialFlushOutput (SerialDevice *serial);

extern int serialMonitorInput (SerialDevice *serial, AsyncMonitorCallback *callback, void *data);
extern int serialAwaitInput (SerialDevice *serial, int timeout);
extern int serialAwaitOutput (SerialDevice *serial);

extern ssize_t serialReadData (
  SerialDevice *serial,
  void *buffer, size_t size,
  int initialTimeout, int subsequentTimeout
);

extern int serialReadChunk (
  SerialDevice *serial,
  void *buffer, size_t *offset, size_t count,
  int initialTimeout, int subsequentTimeout
);

extern ssize_t serialWriteData (
  SerialDevice *serial,
  const void *data, size_t size
);

extern int serialParseBaud (unsigned int *baud, const char *string);
extern int serialParseDataBits (unsigned int *bits, const char *string);
extern int serialParseStopBits (unsigned int *bits, const char *string);
extern int serialParseParity (SerialParity *parity, const char *string);
extern int serialParseFlowControl (SerialFlowControl *flow, const char *string);

extern int serialSetParameters (SerialDevice *serial, const SerialParameters *parameters);
extern int serialSetBaud (SerialDevice *serial, unsigned int baud);
extern int serialSetDataBits (SerialDevice *serial, unsigned int bits);
extern int serialSetStopBits (SerialDevice *serial, SerialStopBits bits);
extern int serialSetParity (SerialDevice *serial, SerialParity parity);
extern int serialSetFlowControl (SerialDevice *serial, SerialFlowControl flow);

extern unsigned int serialGetCharacterSize (const SerialParameters *parameters);
extern unsigned int serialGetCharacterBits (SerialDevice *serial);

extern int serialSetLineRTS (SerialDevice *serial, int up);
extern int serialSetLineDTR (SerialDevice *serial, int up);

extern int serialTestLineCTS (SerialDevice *serial);
extern int serialTestLineDSR (SerialDevice *serial);

extern int serialWaitLineCTS (SerialDevice *serial, int up, int flank);
extern int serialWaitLineDSR (SerialDevice *serial, int up, int flank);

#ifdef __cplusplus
}
#endif /* __cplusplus */

#endif /* BRLTTY_INCLUDED_IO_SERIAL */
