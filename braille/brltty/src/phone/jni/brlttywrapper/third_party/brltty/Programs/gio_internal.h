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

#ifndef BRLTTY_INCLUDED_GIO_INTERNAL
#define BRLTTY_INCLUDED_GIO_INTERNAL

#ifdef __cplusplus
extern "C" {
#endif /* __cplusplus */

typedef struct GioHandleStruct GioHandle;

typedef struct {
  void *address;
  size_t size;
} GioHidReportItemsData;

typedef int GioDisconnectResourceMethod (GioHandle *handle);

typedef char *GioGetResourceNameMethod (GioHandle *handle, int timeout);

typedef ssize_t GioWriteDataMethod (GioHandle *handle, const void *data, size_t size, int timeout);

typedef int GioAwaitInputMethod (GioHandle *handle, int timeout);

typedef ssize_t GioReadDataMethod (
  GioHandle *handle, void *buffer, size_t size,
  int initialTimeout, int subsequentTimeout
);

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

typedef int GioGetHidReportItemsMethod (GioHandle *handle, GioHidReportItemsData *items, int timeout);

typedef size_t GioGetHidReportSizeMethod (const GioHidReportItemsData *items, unsigned char report);

typedef ssize_t GioSetHidReportMethod (
  GioHandle *handle, unsigned char report,
  const void *data, uint16_t size, int timeout
);

typedef ssize_t GioGetHidReportMethod (
  GioHandle *handle, unsigned char report,
  void *buffer, uint16_t size, int timeout
);

typedef ssize_t GioSetHidFeatureMethod (
  GioHandle *handle, unsigned char report,
  const void *data, uint16_t size, int timeout
);

typedef ssize_t GioGetHidFeatureMethod (
  GioHandle *handle, unsigned char report,
  void *buffer, uint16_t size, int timeout
);

typedef int GioMonitorInputMethod (GioHandle *handle, AsyncMonitorCallback *callback, void *data);

typedef void *GioGetResourceObjectMethod (GioHandle *handle);

typedef struct {
  GioDisconnectResourceMethod *disconnectResource;

  GioGetResourceNameMethod *getResourceName;

  GioWriteDataMethod *writeData;
  GioAwaitInputMethod *awaitInput;
  GioReadDataMethod *readData;

  GioReconfigureResourceMethod *reconfigureResource;

  GioTellResourceMethod *tellResource;
  GioAskResourceMethod *askResource;

  GioGetHidReportItemsMethod *getHidReportItems;
  GioGetHidReportSizeMethod *getHidReportSize;

  GioSetHidReportMethod *setHidReport;
  GioGetHidReportMethod *getHidReport;

  GioSetHidFeatureMethod *setHidFeature;
  GioGetHidFeatureMethod *getHidFeature;

  GioMonitorInputMethod *monitorInput;

  GioGetResourceObjectMethod *getResourceObject;
} GioMethods;

struct GioEndpointStruct {
  GioHandle *handle;
  const GioMethods *methods;
  GioOptions options;
  GioTypeIdentifier resourceType;
  unsigned int bytesPerSecond;
  GioHidReportItemsData hidReportItems;

  struct {
    int error;
    unsigned int from;
    unsigned int to;
    unsigned char buffer[0X40];
  } input;
};

typedef int GioIsSupportedMethod (const GioDescriptor *descriptor);

typedef const GioOptions *GioGetOptionsMethod (const GioDescriptor *descriptor);

typedef const GioMethods *GioGetMethodsMethod (void);

typedef GioHandle *GioConnectResourceMethod (
  const char *identifier,
  const GioDescriptor *descriptor
);

typedef int GioPrepareEndpointMethod (GioEndpoint *endpoint);

typedef struct {
  GioIsSupportedMethod *isSupported;

  GioGetOptionsMethod *getOptions;
  GioGetMethodsMethod *getMethods;

  GioConnectResourceMethod *connectResource;
  GioPrepareEndpointMethod *prepareEndpoint;
} GioPrivateProperties;

typedef struct {
  const GioPublicProperties *public;
  const GioPrivateProperties *private;
} GioProperties;

extern const GioProperties *const gioProperties[];
extern const GioProperties gioProperties_null;
extern const GioProperties gioProperties_serial;
extern const GioProperties gioProperties_usb;
extern const GioProperties gioProperties_bluetooth;

extern void gioSetBytesPerSecond (GioEndpoint *endpoint, const SerialParameters *parameters);

#ifdef __cplusplus
}
#endif /* __cplusplus */

#endif /* BRLTTY_INCLUDED_GIO_INTERNAL */
