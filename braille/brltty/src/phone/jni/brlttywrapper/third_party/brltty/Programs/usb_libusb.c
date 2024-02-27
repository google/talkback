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
#include <string.h>
#include <errno.h>
#include <usb.h>

#include "log.h"
#include "io_usb.h"
#include "usb_internal.h"
#include "bitfield.h"

#ifdef __MINGW32__
#ifndef ETIMEDOUT
#define ETIMEDOUT 116
#endif /* ETIMEDOUT */
#endif /* __MINGW32__ */

struct UsbDeviceExtensionStruct {
  struct usb_dev_handle *handle;
};

int
usbDisableAutosuspend (UsbDevice *device) {
  logUnsupportedFunction();
  return 0;
}

int
usbSetConfiguration (UsbDevice *device, unsigned char configuration) {
  UsbDeviceExtension *devx = device->extension;
  int result;

  logMessage(LOG_CATEGORY(USB_IO), "setting configuration: %u", configuration);
  result = usb_set_configuration(devx->handle, configuration);
  if (result >= 0) return 1;

  errno = -result;
  logSystemError("USB configuration set");
  return 0;
}

int
usbClaimInterface (UsbDevice *device, unsigned char interface) {
  UsbDeviceExtension *devx = device->extension;
  int detached = 0;
  int result;

  logMessage(LOG_CATEGORY(USB_IO), "claiming interface: %u", interface);

  while (1) {
    char driver[0X100];

    result = usb_claim_interface(devx->handle, interface);
    if (result >= 0) return 1;

    if (result != -EBUSY) break;
    if (detached) break;

#ifdef LIBUSB_HAS_GET_DRIVER_NP
    result = usb_get_driver_np(devx->handle, interface, driver, sizeof(driver));

    if (result < 0)
#endif /* LIBUSB_HAS_GET_DRIVER_NP */

    {
      strcpy(driver, "unknown");
    }

    logMessage(LOG_WARNING, "USB interface in use: %u (%s)", interface, driver);

    if (strcmp(driver, "usbfs") == 0) {
      result = -EBUSY;
      break;
    }

#ifdef LIBUSB_HAS_DETACH_KERNEL_DRIVER_NP
    logMessage(LOG_CATEGORY(USB_IO), "detaching kernel driver: %u (%s)",
               interface, driver);

    result = usb_detach_kernel_driver_np(devx->handle, interface);

    if (result >= 0) {
      logMessage(LOG_CATEGORY(USB_IO), "detached kernel driver: %u (%s)",
                 interface, driver);

      detached = 1;
      continue;
    }

    result = -EBUSY;
#endif /* LIBUSB_HAS_DETACH_KERNEL_DRIVER_NP */

    break;
  }

  errno = -result;
  logSystemError("USB interface claim");
  return 0;
}

int
usbReleaseInterface (UsbDevice *device, unsigned char interface) {
  UsbDeviceExtension *devx = device->extension;
  int result;

  logMessage(LOG_CATEGORY(USB_IO), "releasing interface: %u", interface);
  result = usb_release_interface(devx->handle, interface);
  if (result >= 0) return 1;

  errno = -result;
  logSystemError("USB interface release");
  return 0;
}

int
usbSetAlternative (
  UsbDevice *device,
  unsigned char interface,
  unsigned char alternative
) {
  UsbDeviceExtension *devx = device->extension;
  int result;

  logMessage(LOG_CATEGORY(USB_IO), "setting alternative: %u[%u]", interface, alternative);
  result = usb_set_altinterface(devx->handle, alternative);
  if (result >= 0) return 1;

  errno = -result;
  logSystemError("USB alternative set");
  return 0;
}

int
usbResetDevice (UsbDevice *device) {
  logMessage(LOG_CATEGORY(USB_IO), "reset device");

  UsbDeviceExtension *devx = device->extension;
  int result = usb_reset(devx->handle);
  if (result >= 0) return 1;

  errno = -result;
  logSystemError("USB device reset");
  return 0;
}

int
usbClearHalt (UsbDevice *device, unsigned char endpointAddress) {
  logMessage(LOG_CATEGORY(USB_IO), "clear halt: %02X", endpointAddress);

  UsbDeviceExtension *devx = device->extension;
  int result = usb_clear_halt(devx->handle, endpointAddress);
  if (result >= 0) return 1;

  errno = -result;
  logSystemError("USB endpoint clear");
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
  UsbDeviceExtension *devx = device->extension;
  UsbSetupPacket setup;
  int result;

  usbMakeSetupPacket(&setup, direction, recipient, type, request, value, index, length);

  if (direction == UsbControlDirection_Output) {
    if (length) logBytes(LOG_CATEGORY(USB_IO), "control output", buffer, length);
  }

  result = usb_control_msg(devx->handle, setup.bRequestType, setup.bRequest,
                           getLittleEndian16(setup.wValue),
                           getLittleEndian16(setup.wIndex), buffer,
                           getLittleEndian16(setup.wLength), timeout);

  if (result >= 0) {
    if (direction == UsbControlDirection_Input) {
      logBytes(LOG_CATEGORY(USB_IO), "control input", buffer, result);
    }

    return result;
  }

  errno = -result;
  logSystemError("USB control transfer");
  return -1;
}

void *
usbSubmitRequest (
  UsbDevice *device,
  unsigned char endpointAddress,
  void *buffer,
  size_t length,
  void *data
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
  UsbDeviceExtension *devx = device->extension;
  UsbEndpoint *endpoint;

  if ((endpoint = usbGetInputEndpoint(device, endpointNumber))) {
    const UsbEndpointDescriptor *descriptor = endpoint->descriptor;
    UsbEndpointTransfer transfer = USB_ENDPOINT_TRANSFER(descriptor);
    ssize_t result = -1;

    switch (transfer) {
      case UsbEndpointTransfer_Bulk:
        result = usb_bulk_read(devx->handle, descriptor->bEndpointAddress,
                               buffer, length, timeout);
        break;

      case UsbEndpointTransfer_Interrupt:
        result = usb_interrupt_read(devx->handle, descriptor->bEndpointAddress,
                                    buffer, length, timeout);
        break;

      default:
        logMessage(LOG_ERR, "USB endpoint input transfer not supported: 0X%02X", transfer);
        result = -ENOSYS;
        break;
    }

    if (result >= 0) {
      if (!usbApplyInputFilters(endpoint, buffer, length, &result)) {
        result = -EIO;
      }
    }

    if (result >= 0) return result;
    errno = -result;
  }

#ifdef ETIMEDOUT
  if (errno == ETIMEDOUT) errno = EAGAIN;
#endif /* ETIMEDOUT */

  if (errno != EAGAIN) logSystemError("USB endpoint read");
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
  UsbDeviceExtension *devx = device->extension;
  UsbEndpoint *endpoint;

  if ((endpoint = usbGetOutputEndpoint(device, endpointNumber))) {
    const UsbEndpointDescriptor *descriptor = endpoint->descriptor;
    UsbEndpointTransfer transfer = USB_ENDPOINT_TRANSFER(descriptor);
    int result = -1;

    usbLogEndpointData(endpoint, "output", buffer, length);

    switch (transfer) {
      case UsbEndpointTransfer_Bulk:
        result = usb_bulk_write(devx->handle, descriptor->bEndpointAddress,
                                (char *)buffer, length, timeout);
        break;

      case UsbEndpointTransfer_Interrupt:
        result = usb_interrupt_write(devx->handle, descriptor->bEndpointAddress,
                                     (char *)buffer, length, timeout);
        break;

      default:
        logMessage(LOG_ERR, "USB endpoint output transfer not supported: 0X%02X", transfer);
        result = -ENOSYS;
        break;
    }

    if (result >= 0) return result;
    errno = -result;
  }

  logSystemError("USB endpoint write");
  return -1;
}

int
usbReadDeviceDescriptor (UsbDevice *device) {
  UsbDeviceExtension *devx = device->extension;
  memcpy(&device->descriptor, &usb_device(devx->handle)->descriptor, UsbDescriptorSize_Device);
  return 1;
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
  if (devx->handle) {
    usb_close(devx->handle);
    devx->handle = NULL;
  }

  free(devx);
}

UsbDevice *
usbFindDevice (UsbDeviceChooser *chooser, UsbChooseChannelData *data) {
  UsbDevice *device = NULL;
  int result;

  {
    static int initialized = 0;
    if (!initialized) {
      usb_init();
      initialized = 1;
    }
  }

  if ((result = usb_find_busses()) >= 0) {
    if ((result = usb_find_devices()) >= 0) {
      struct usb_bus *bus = usb_get_busses();

      if (bus) {
        struct usb_bus *bus0 = bus;

        do {
          struct usb_device *dev = bus->devices;

          if (dev) {
            struct usb_device *dev0 = dev;

            do {
              UsbDeviceExtension *devx;

              if ((devx = malloc(sizeof(*devx)))) {
                if ((devx->handle = usb_open(dev))) {
                  if ((device = usbTestDevice(devx, chooser, data))) return device;

                  usb_close(devx->handle);
                } else {
                  logMessage(LOG_ERR, "USB open error: vendor=%X product=%X",
                             getLittleEndian16(dev->descriptor.idVendor),
                             getLittleEndian16(dev->descriptor.idProduct));
                }

                free(devx);
              } else {
                logSystemError("USB device extension allocate");
              }

              if ((dev = dev->next) == dev0) dev = NULL;
            } while (dev);
          }

          if ((bus = bus->next) == bus0) bus = NULL;
        } while (bus);
      }
    } else {
      errno = -result;
      logSystemError("USB devices find");
    }
  } else {
    errno = -result;
    logSystemError("USB busses find");
  }

  return device;
}

void
usbForgetDevices (void) {
}
