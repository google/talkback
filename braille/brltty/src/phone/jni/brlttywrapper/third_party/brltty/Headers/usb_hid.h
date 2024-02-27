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

#ifndef BRLTTY_INCLUDED_USB_HID
#define BRLTTY_INCLUDED_USB_HID

#include "hid_types.h"

#ifdef __cplusplus
extern "C" {
#endif /* __cplusplus */

typedef enum {
  UsbHidRequest_GetReport   = 0X01,
  UsbHidRequest_GetIdle     = 0X02,
  UsbHidRequest_GetProtocol = 0X03,
  UsbHidRequest_SetReport   = 0X09,
  UsbHidRequest_SetIdle     = 0X0A,
  UsbHidRequest_SetProtocol = 0X0B
} UsbHidRequest;

typedef enum {
  UsbHidReportType_Input   = 0X01,
  UsbHidReportType_Output  = 0X02,
  UsbHidReportType_Feature = 0X03
} UsbHidReportType;

extern const UsbHidDescriptor *usbHidDescriptor (UsbDevice *device);

extern HidItemsDescriptor *usbHidGetItems (
  UsbDevice *device,
  unsigned char interface,
  unsigned char number,
  int timeout
);

extern ssize_t usbHidGetReport (
  UsbDevice *device,
  unsigned char interface,
  HidReportIdentifier identifier,
  unsigned char *buffer,
  uint16_t size,
  int timeout
);

extern ssize_t usbHidSetReport (
  UsbDevice *device,
  unsigned char interface,
  HidReportIdentifier identifier,
  const unsigned char *data,
  uint16_t length,
  int timeout
);

extern ssize_t usbHidGetFeature (
  UsbDevice *device,
  unsigned char interface,
  HidReportIdentifier identifier,
  unsigned char *buffer,
  uint16_t size,
  int timeout
);

extern ssize_t usbHidSetFeature (
  UsbDevice *device,
  unsigned char interface,
  HidReportIdentifier identifier,
  const unsigned char *data,
  uint16_t length,
  int timeout
);

#ifdef __cplusplus
}
#endif /* __cplusplus */

#endif /* BRLTTY_INCLUDED_USB_HID */
