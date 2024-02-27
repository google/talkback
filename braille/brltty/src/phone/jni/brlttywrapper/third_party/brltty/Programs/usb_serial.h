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

#ifndef BRLTTY_INCLUDED_USB_SERIAL
#define BRLTTY_INCLUDED_USB_SERIAL

#include "io_usb.h"
#include "io_log.h"

#ifdef __cplusplus
extern "C" {
#endif /* __cplusplus */

typedef struct {
  const UsbSerialOperations *operations;
  uint16_t vendor;
  uint16_t product;
  unsigned generic:1;
} UsbSerialAdapter;

extern const UsbSerialAdapter *usbFindSerialAdapter (const UsbDeviceDescriptor *descriptor);
extern const UsbSerialOperations usbSerialOperations_CDC_ACM;
extern const UsbSerialOperations usbSerialOperations_Belkin;
extern const UsbSerialOperations usbSerialOperations_CH341;
extern const UsbSerialOperations usbSerialOperations_CP2101;
extern const UsbSerialOperations usbSerialOperations_CP2110;
extern const UsbSerialOperations usbSerialOperations_FTDI_SIO;
extern const UsbSerialOperations usbSerialOperations_FTDI_FT8U232AM;
extern const UsbSerialOperations usbSerialOperations_FTDI_FT232BM;

extern UsbSerialData *usbGetSerialData (UsbDevice *devic3e);
extern int usbSkipInitialBytes (UsbInputFilterData *data, unsigned int count);

static inline int
usbUpdateByte (uint8_t *byte, uint8_t mask, uint8_t value) {
  if ((*byte & mask) == value) return 0;

  *byte &= ~mask;
  *byte |= value;
  return 1;
}

#ifdef __cplusplus
}
#endif /* __cplusplus */

#endif /* BRLTTY_INCLUDED_USB_SERIAL */
