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

#ifndef BRLTTY_INCLUDED_IO_USB
#define BRLTTY_INCLUDED_IO_USB

#include "prologue.h"
#include "usb_types.h"
#include "async_types_io.h"

#ifdef __cplusplus
extern "C" {
#endif /* __cplusplus */

extern UsbDevice *usbFindDevice (UsbDeviceChooser *chooser, UsbChooseChannelData *data);
extern void usbForgetDevices (void);

extern void usbCloseDevice (UsbDevice *device);
extern int usbDisableAutosuspend (UsbDevice *device);

extern const UsbDeviceDescriptor *usbDeviceDescriptor (UsbDevice *device);
#define USB_IS_PRODUCT(descriptor,vendor,product) ((getLittleEndian16((descriptor)->idVendor) == (vendor)) && (getLittleEndian16((descriptor)->idProduct) == (product)))

extern int usbNextDescriptor (
  UsbDevice *device,
  const UsbDescriptor **descriptor
);
extern const UsbConfigurationDescriptor *usbConfigurationDescriptor (
  UsbDevice *device
);
extern const UsbInterfaceDescriptor *usbInterfaceDescriptor (
  UsbDevice *device,
  unsigned char interface,
  unsigned char alternative
);
extern unsigned int usbAlternativeCount (
  UsbDevice *device,
  unsigned char interface
);
extern const UsbEndpointDescriptor *usbEndpointDescriptor (
  UsbDevice *device,
  unsigned char endpointAddress
);

extern int usbConfigureDevice (
  UsbDevice *device,
  unsigned char configuration
);
extern int usbGetConfiguration (
  UsbDevice *device,
  unsigned char *configuration
);

extern int usbOpenInterface (
  UsbDevice *device,
  unsigned char interface,
  unsigned char alternative
);
extern void usbCloseInterface (
  UsbDevice *device
);

extern int usbResetDevice (UsbDevice *device);
extern int usbClearHalt (UsbDevice *device, unsigned char endpointAddress);

extern ssize_t usbControlRead (
  UsbDevice *device,
  uint8_t recipient,
  uint8_t type,
  uint8_t request,
  uint16_t value,
  uint16_t index,
  void *buffer,
  uint16_t length,
  int timeout
);
extern ssize_t usbControlWrite (
  UsbDevice *device,
  uint8_t recipient,
  uint8_t type,
  uint8_t request,
  uint16_t value,
  uint16_t index,
  const void *buffer,
  uint16_t length,
  int timeout
);

extern ssize_t usbGetDescriptor (
  UsbDevice *device,
  unsigned char type,
  unsigned char number,
  unsigned int index,
  UsbDescriptor *descriptor,
  int timeout
);
extern int usbGetDeviceDescriptor (
  UsbDevice *device,
  UsbDeviceDescriptor *descriptor
);
extern int usbGetLanguage (
  UsbDevice *device,
  uint16_t *language,
  int timeout
);
extern char *usbGetString (
  UsbDevice *device,
  unsigned char number,
  int timeout
);
extern char *usbDecodeString (const UsbStringDescriptor *descriptor);
extern char *usbGetManufacturer (UsbDevice *device, int timeout);
extern char *usbGetProduct (UsbDevice *device, int timeout);
extern char *usbGetSerialNumber (UsbDevice *device, int timeout);

extern void usbLogString (
  UsbDevice *device,
  unsigned char number,
  const char *description
);

typedef int (UsbStringVerifier) (const char *reference, const char *value);
extern UsbStringVerifier usbStringEquals;
extern UsbStringVerifier usbStringMatches;
extern int usbVerifyString (
  UsbDevice *device,
  UsbStringVerifier verify,
  unsigned char index,
  const char *value
);
extern int usbVerifyManufacturerName (UsbDevice *device, const char *eRegExp);
extern int usbVerifyProductDescription (UsbDevice *device, const char *eRegExp);
extern int usbVerifySerialNumber (UsbDevice *device, const char *string);

extern int usbParseVendorIdentifier (uint16_t *identifier, const char *string);
extern int usbVerifyVendorIdentifier (const UsbDeviceDescriptor *descriptor, uint16_t identifier);

extern int usbParseProductIdentifier (uint16_t *identifier, const char *string);
extern int usbVerifyProductIdentifier (const UsbDeviceDescriptor *descriptor, uint16_t identifier);

extern void usbBeginInput (
  UsbDevice *device,
  unsigned char endpointNumber
);

extern int usbMonitorInputEndpoint (
  UsbDevice *device, unsigned char endpointNumber,
  AsyncMonitorCallback *callback, void *data
);

extern ssize_t usbReadEndpoint (
  UsbDevice *device,
  unsigned char endpointNumber,
  void *buffer,
  size_t length,
  int timeout
);
extern ssize_t usbWriteEndpoint (
  UsbDevice *device,
  unsigned char endpointNumber,
  const void *buffer,
  size_t length,
  int timeout
);

typedef struct {
  void *context;
  void *buffer;
  size_t size;
  ssize_t count;
  int error;
} UsbResponse;
extern void *usbSubmitRequest (
  UsbDevice *device,
  unsigned char endpointAddress,
  void *buffer,
  size_t length,
  void *context
);

extern int usbCancelRequest (UsbDevice *device, void *request);

extern void *usbReapResponse (
  UsbDevice *device,
  unsigned char endpointAddress,
  UsbResponse *response,
  int wait
);

extern int usbAwaitInput (
  UsbDevice *device,
  unsigned char endpointNumber,
  int timeout
);
extern ssize_t usbReadData (
  UsbDevice *device,
  unsigned char endpointNumber,
  void *buffer,
  size_t length,
  int initialTimeout,
  int subsequentTimeout
);

extern ssize_t usbWriteData (
  UsbDevice *device,
  unsigned char endpointNumber,
  const void *data,
  size_t length,
  int timeout
);

extern int usbAddInputFilter (UsbDevice *device, UsbInputFilter *filter);

extern const UsbSerialOperations *usbGetSerialOperations (UsbDevice *device);
extern int usbSetSerialParameters (UsbDevice *device, const SerialParameters *parameters);

typedef struct {
  const UsbChannelDefinition *definition;
  UsbDevice *device;
} UsbChannel;

extern UsbChannel *usbOpenChannel (const UsbChannelDefinition *definitions, const char *identifier);
extern void usbCloseChannel (UsbChannel *channel);
extern const char *usbMakeChannelIdentifier (UsbChannel *channel, char *buffer, size_t size);

extern const char *const *usbGetDriverCodes (uint16_t vendor, uint16_t product);

#define USB_DEVICE_QUALIFIER "usb"
extern int isUsbDeviceIdentifier (const char **identifier);

#ifdef __cplusplus
}
#endif /* __cplusplus */

#endif /* BRLTTY_INCLUDED_IO_USB */
