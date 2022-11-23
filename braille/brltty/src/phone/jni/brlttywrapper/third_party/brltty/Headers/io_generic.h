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

#ifndef BRLTTY_INCLUDED_IO_GENERIC
#define BRLTTY_INCLUDED_IO_GENERIC

#include "gio_types.h"
#include "async_io.h"

#ifdef __cplusplus
extern "C" {
#endif /* __cplusplus */

extern const GioPublicProperties *gioGetPublicProperties (const char **identifier);

extern void gioInitializeDescriptor (GioDescriptor *descriptor);
extern void gioInitializeSerialParameters (SerialParameters *parameters);

extern GioEndpoint *gioConnectResource (
  const char *identifier,
  const GioDescriptor *descriptor
);

extern int gioDisconnectResource (GioEndpoint *endpoint);

extern const void *gioGetApplicationData (GioEndpoint *endpoint);
extern char *gioGetResourceName (GioEndpoint *endpoint);

extern ssize_t gioWriteData (GioEndpoint *endpoint, const void *data, size_t size);
extern int gioAwaitInput (GioEndpoint *endpoint, int timeout);
extern ssize_t gioReadData (GioEndpoint *endpoint, void *buffer, size_t size, int wait);
extern int gioReadByte (GioEndpoint *endpoint, unsigned char *byte, int wait);
extern int gioDiscardInput (GioEndpoint *endpoint);

extern int gioReconfigureResource (
  GioEndpoint *endpoint,
  const SerialParameters *parameters
);

extern unsigned int gioGetBytesPerSecond (GioEndpoint *endpoint);
extern unsigned int gioGetMillisecondsToTransfer (GioEndpoint *endpoint, size_t bytes);

extern ssize_t gioTellResource (
  GioEndpoint *endpoint,
  uint8_t recipient, uint8_t type,
  uint8_t request, uint16_t value, uint16_t index,
  const void *data, uint16_t size
);

extern ssize_t gioAskResource (
  GioEndpoint *endpoint,
  uint8_t recipient, uint8_t type,
  uint8_t request, uint16_t value, uint16_t index,
  void *buffer, uint16_t size
);

extern size_t gioGetHidReportSize (GioEndpoint *endpoint, unsigned char report);

extern ssize_t gioSetHidReport (
  GioEndpoint *endpoint, unsigned char report,
  const void *data, uint16_t size
);

extern ssize_t gioWriteHidReport (
  GioEndpoint *endpoint,
  const unsigned char *data, uint16_t size
);

extern ssize_t gioGetHidReport (
  GioEndpoint *endpoint, unsigned char report,
  void *buffer, uint16_t size
);

extern ssize_t gioSetHidFeature (
  GioEndpoint *endpoint, unsigned char report,
  const void *data, uint16_t size
);

extern ssize_t gioWriteHidFeature (
  GioEndpoint *endpoint,
  const unsigned char *data, uint16_t size
);

extern ssize_t gioGetHidFeature (
  GioEndpoint *endpoint, unsigned char report,
  void *buffer, uint16_t size
);

extern int gioMonitorInput (GioEndpoint *endpoint, AsyncMonitorCallback *callback, void *data);

typedef struct {
  void *const data;
  int error;
} GioHandleInputParameters;

#define GIO_INPUT_HANDLER(name) int name (GioHandleInputParameters *parameters)
typedef GIO_INPUT_HANDLER(GioInputHandler);
typedef struct GioHandleInputObjectStruct GioHandleInputObject;

extern GioHandleInputObject *gioNewHandleInputObject (
  GioEndpoint *endpoint, int pollInterval,
  GioInputHandler *handler, void *data
);

extern void gioDestroyHandleInputObject (GioHandleInputObject *hio);

extern GioTypeIdentifier gioGetResourceType (GioEndpoint *endpoint);
extern void *gioGetResourceObject (GioEndpoint *endpoint);

#ifdef __cplusplus
}
#endif /* __cplusplus */

#endif /* BRLTTY_INCLUDED_IO_GENERIC */
