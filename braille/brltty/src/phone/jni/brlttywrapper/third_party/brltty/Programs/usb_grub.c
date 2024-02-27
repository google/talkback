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

#include "prologue.h"

#include <stdio.h>
#include <errno.h>

#include "log.h"
#include "io_usb.h"
#include "usb_internal.h"

int
usbDisableAutosuspend (UsbDevice *device) {
  logUnsupportedFunction();
  return 0;
}

int
usbSetConfiguration (UsbDevice *device, unsigned char configuration) {
  logUnsupportedFunction();
  return 0;
}

int
usbClaimInterface (UsbDevice *device, unsigned char interface) {
  logUnsupportedFunction();
  return 0;
}

int
usbReleaseInterface (UsbDevice *device, unsigned char interface) {
  logUnsupportedFunction();
  return 0;
}

int
usbSetAlternative (
  UsbDevice *device,
  unsigned char interface,
  unsigned char alternative
) {
  logUnsupportedFunction();
  return 0;
}

int
usbResetDevice (UsbDevice *device) {
  logUnsupportedFunction();
  return 0;
}

int
usbClearHalt (UsbDevice *device, unsigned char endpointAddress) {
  logUnsupportedFunction();
  return 0;
}

ssize_t
usbControlTransfer (
  UsbDevice *device,
  uint8_t direction,
  uint8_t recipient,
  uint8_t type,
  uint8_t request,
  uint16_t value,
  uint16_t index,
  void *buffer,
  uint16_t length,
  int timeout
) {
  logUnsupportedFunction();
  return -1;
}

void *
usbSubmitRequest (
  UsbDevice *device,
  unsigned char endpointAddress,
  void *buffer,
  size_t length,
  void *context
) {
  logUnsupportedFunction();
  return NULL;
}

int
usbCancelRequest (UsbDevice *device, void *request) {
  logUnsupportedFunction();
  return 0;
}

void *
usbReapResponse (
  UsbDevice *device,
  unsigned char endpointAddress,
  UsbResponse *response,
  int wait
) {
  logUnsupportedFunction();
  return NULL;
}

int
usbMonitorInputEndpoint (
  UsbDevice *device, unsigned char endpointNumber,
  AsyncMonitorCallback *callback, void *data
) {
  return 0;
}

ssize_t
usbReadEndpoint (
  UsbDevice *device,
  unsigned char endpointNumber,
  void *buffer,
  size_t length,
  int timeout
) {
  logUnsupportedFunction();
  return -1;
}

ssize_t
usbWriteEndpoint (
  UsbDevice *device,
  unsigned char endpointNumber,
  const void *buffer,
  size_t length,
  int timeout
) {
  logUnsupportedFunction();
  return -1;
}

int
usbReadDeviceDescriptor (UsbDevice *device) {
  logUnsupportedFunction();
  return 0;
}

int
usbAllocateEndpointExtension (UsbEndpoint *endpoint) {
  return 1;
}

void
usbDeallocateEndpointExtension (UsbEndpointExtension *eptx) {
}

void
usbDeallocateDeviceExtension (UsbDeviceExtension *devx) {
}

UsbDevice *
usbFindDevice (UsbDeviceChooser *chooser, UsbChooseChannelData *data) {
  return NULL;
}

void
usbForgetDevices (void) {
}
