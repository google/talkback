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

#ifndef BRLTTY_INCLUDED_GIO_INTERNAL
#define BRLTTY_INCLUDED_GIO_INTERNAL

#ifdef __cplusplus
extern "C" {
#endif /* __cplusplus */

typedef struct GioHandleStruct GioHandle;

typedef int GioDisconnectResourceMethod (GioHandle *handle);

typedef GioEndpoint *GioGetChainedEndpointMethod (GioHandle *handle);

typedef const char *MakeResourceIdentifierMethod (GioHandle *handle, char *buffer, size_t size);

typedef char *GioGetResourceNameMethod (GioHandle *handle, int timeout);

typedef void *GioGetResourceObjectMethod (GioHandle *handle);

typedef ssize_t GioWriteDataMethod (GioHandle *handle, const void *data, size_t size, int timeout);

typedef int GioAwaitInputMethod (GioHandle *handle, int timeout);

typedef ssize_t GioReadDataMethod (
  GioHandle *handle, void *buffer, size_t size,
  int initialTimeout, int subsequentTimeout
);

typedef int GioMonitorInputMethod (GioHandle *handle, AsyncMonitorCallback *callback, void *data);

typedef int GioReconfigureResourceMethod (GioHandle *handle, const SerialParameters *parameters);

typedef ssize_t GioTellResourceMethod (
  GioHandle *handle, uint8_t recipient, uint8_t type,
  uint8_t request, uint16_t value, uint16_t index,
  const void *data, uint16_t size, int timeout
);

typedef ssize_t GioAskResourceMethod (
  GioHandle *handle, uint8_t recipient, uint8_t type,
  uint8_t request, uint16_t value, uint16_t index,
  void *buffer, uint16_t size, int timeout
);

typedef HidItemsDescriptor *GioGetHidDescriptorMethod(
  GioHandle *handle
);

typedef int GioGetHidReportSizeMethod (
  GioHandle *handle, HidReportIdentifier identifier,
  HidReportSize *size, int timeout
);

typedef ssize_t GioGetHidReportMethod (
  GioHandle *handle, HidReportIdentifier identifier,
  unsigned char *buffer, size_t size, int timeout
);

typedef ssize_t GioSetHidReportMethod (
  GioHandle *handle, HidReportIdentifier identifier,
  const unsigned char *data, size_t size, int timeout
);

typedef ssize_t GioGetHidFeatureMethod (
  GioHandle *handle, HidReportIdentifier identifier,
  unsigned char *buffer, size_t size, int timeout
);

typedef ssize_t GioSetHidFeatureMethod (
  GioHandle *handle, HidReportIdentifier identifier,
  const unsigned char *data, size_t size, int timeout
);

typedef struct {
  GioDisconnectResourceMethod *disconnectResource;
  GioGetChainedEndpointMethod *getChainedEndpoint;

  MakeResourceIdentifierMethod *makeResourceIdentifier;
  GioGetResourceNameMethod *getResourceName;
  GioGetResourceObjectMethod *getResourceObject;

  GioWriteDataMethod *writeData;
  GioAwaitInputMethod *awaitInput;
  GioReadDataMethod *readData;
  GioMonitorInputMethod *monitorInput;
  GioReconfigureResourceMethod *reconfigureResource;

  GioTellResourceMethod *tellResource;
  GioAskResourceMethod *askResource;

  GioGetHidReportSizeMethod *getHidReportSize;
  GioGetHidDescriptorMethod *getHidDescriptor;
  GioGetHidReportMethod *getHidReport;
  GioSetHidReportMethod *setHidReport;
  GioGetHidFeatureMethod *getHidFeature;
  GioSetHidFeatureMethod *setHidFeature;
} GioHandleMethods;

struct GioEndpointStruct {
  GioHandle *handle;
  const GioHandleMethods *handleMethods;
  GioOptions options;
  GioTypeIdentifier resourceType;
  unsigned int bytesPerSecond;
  unsigned char referenceCount;

  struct {
    int error;
    unsigned int from;
    unsigned int to;
    unsigned char buffer[0X40];
  } input;
};

typedef int GioIsSupportedMethod (const GioDescriptor *descriptor);

typedef const GioOptions *GioGetOptionsMethod (const GioDescriptor *descriptor);

typedef const GioHandleMethods *GioGetHandleMethodsMethod (void);

typedef GioHandle *GioConnectResourceMethod (
  const char *identifier,
  const GioDescriptor *descriptor
);

typedef int GioPrepareEndpointMethod (GioEndpoint *endpoint);

typedef struct {
  GioIsSupportedMethod *isSupported;

  GioGetOptionsMethod *getOptions;
  GioGetHandleMethodsMethod *getHandleMethods;

  GioConnectResourceMethod *connectResource;
  GioPrepareEndpointMethod *prepareEndpoint;
} GioPrivateProperties;

typedef struct {
  const GioPublicProperties *public;
  const GioPrivateProperties *private;
} GioProperties;

extern const GioProperties *const gioProperties[];
extern const GioProperties gioProperties_serial;
extern const GioProperties gioProperties_usb;
extern const GioProperties gioProperties_bluetooth;
extern const GioProperties gioProperties_hid;
extern const GioProperties gioProperties_null;

extern void gioSetBytesPerSecond (GioEndpoint *endpoint, const SerialParameters *parameters);
extern void gioSetApplicationData (GioEndpoint *endpoint, const void *data);

static inline int
gioIsHidSupported (const GioDescriptor *descriptor) {
  return gioProperties_hid.private->isSupported(descriptor);
}

#ifdef __cplusplus
}
#endif /* __cplusplus */

#endif /* BRLTTY_INCLUDED_GIO_INTERNAL */
