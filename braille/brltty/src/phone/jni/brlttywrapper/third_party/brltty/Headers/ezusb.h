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

#ifndef BRLTTY_INCLUDED_EZUSB
#define BRLTTY_INCLUDED_EZUSB

#include "io_usb.h"
#include "ihex_types.h"

#ifdef __cplusplus
extern "C" {
#endif /* __cplusplus */

#define EZUSB_REQUEST_RECIPIENT UsbControlRecipient_Device
#define EZUSB_REQUEST_TYPE UsbControlType_Vendor
#define EZUSB_REQUEST_INDEX 0

typedef uint8_t EzusbAction;
#define EZUSB_ACTION_RW_INTERNAL 0XA0
#define EZUSB_ACTION_RW_EEPROM   0XA2
#define EZUSB_ACTION_RW_MEMORY   0XA3

extern int ezusbWriteData (
  UsbDevice *device, EzusbAction action, IhexAddress address,
  const unsigned char *data, size_t length
);

extern int ezusbReadData (
  UsbDevice *device, EzusbAction action, IhexAddress address,
  unsigned char *buffer, size_t size
);

extern int ezusbVerifyData (
  UsbDevice *device, EzusbAction action, IhexAddress address,
  const unsigned char *data, size_t length
);

#define EZUSB_CPUCS_ADDRESS 0X7F92
#define EZUSB_CPUCS_RESET 0X00
#define EZUSB_CPUCS_STOP  0X01

extern int ezusbWriteCPUCS (UsbDevice *device, uint8_t state);
extern int ezusbStopCPU (UsbDevice *device);
extern int ezusbResetCPU (UsbDevice *device);

extern int ezusbInstallBlob (
  UsbDevice *device, const char *name, EzusbAction action
);

extern int ezusbProcessBlob (
  const char *name, IhexRecordHandler *handler, void *data
);

#ifdef __cplusplus
}
#endif /* __cplusplus */

#endif /* BRLTTY_INCLUDED_EZUSB */
