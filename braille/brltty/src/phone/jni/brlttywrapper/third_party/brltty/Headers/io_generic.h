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

#ifndef BRLTTY_INCLUDED_IO_GENERIC
#define BRLTTY_INCLUDED_IO_GENERIC

#include "gio_types.h"
#include "async_types_io.h"

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

extern const void *gioGetApplicationData (GioEndpoint *endpoint);
extern int gioDisconnectResource (GioEndpoint *endpoint);

extern const char *gioMakeResourceIdentifier (GioEndpoint *endpoint, char *buffer, size_t size);
extern char *gioGetResourceIdentifier (GioEndpoint *endpoint);

extern char *gioGetResourceName (GioEndpoint *endpoint);
extern GioTypeIdentifier gioGetResourceType (GioEndpoint *endpoint);
extern void *gioGetResourceObject (GioEndpoint *endpoint);

extern ssize_t gioWriteData (GioEndpoint *endpoint, const void *data, size_t size);
extern int gioAwaitInput (GioEndpoint *endpoint, int timeout);
extern ssize_t gioReadData (GioEndpoint *endpoint, void *buffer, size_t size, int wait);
extern int gioReadByte (GioEndpoint *endpoint, unsigned char *byte, int wait);
extern int gioDiscardInput (GioEndpoint *endpoint);

extern int gioMonitorInput (GioEndpoint *endpoint, AsyncMonitorCallback *callback, void *data);

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

extern int gioGetHidReportSize (
  GioEndpoint *endpoint,
  HidReportIdentifier identifier,
  HidReportSize *size
);

extern size_t gioGetHidInputSize (
  GioEndpoint *endpoint,
  HidReportIdentifier identifier
);

extern size_t gioGetHidOutputSize (
  GioEndpoint *endpoint,
  HidReportIdentifier identifier
);

extern size_t gioGetHidFeatureSize (
  GioEndpoint *endpoint,
  HidReportIdentifier identifier
);

extern ssize_t gioGetHidReport (
  GioEndpoint *endpoint, HidReportIdentifier identifier,
  unsigned char *buffer, size_t size
);

extern ssize_t gioReadHidReport (
  GioEndpoint *endpoint,
  unsigned char *buffer, size_t size
);

extern ssize_t gioSetHidReport (
  GioEndpoint *endpoint, HidReportIdentifier identifier,
  const unsigned char *data, size_t size
);

extern ssize_t gioWriteHidReport (
  GioEndpoint *endpoint,
  const unsigned char *data, size_t size
);

extern ssize_t gioGetHidFeature (
  GioEndpoint *endpoint, HidReportIdentifier identifier,
  unsigned char *buffer, size_t size
);

extern ssize_t gioReadHidFeature (
  GioEndpoint *endpoint,
  unsigned char *buffer, size_t size
);

extern ssize_t gioSetHidFeature (
  GioEndpoint *endpoint, HidReportIdentifier identifier,
  const unsigned char *data, size_t size
);

extern ssize_t gioWriteHidFeature (
  GioEndpoint *endpoint,
  const unsigned char *data, size_t size
);

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

#ifdef __cplusplus
}
#endif /* __cplusplus */

#endif /* BRLTTY_INCLUDED_IO_GENERIC */
