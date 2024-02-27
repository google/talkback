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

#include <string.h>
#include <errno.h>
#include <libusb.h>

#include "log.h"
#include "io_usb.h"
#include "usb_internal.h"
#include "bitfield.h"

struct UsbDeviceExtensionStruct {
  libusb_device *device;
  libusb_device_handle *handle;
};

static libusb_context *usbContext = NULL;
static libusb_device **usbDeviceList = NULL;
static int usbDeviceCount = 0;

static int
usbToErrno (enum libusb_error error) {
  switch (error) {
    case LIBUSB_ERROR_IO:
      return EIO;

    case LIBUSB_ERROR_INVALID_PARAM:
      return EINVAL;

    case LIBUSB_ERROR_ACCESS:
      return EACCES;

    case LIBUSB_ERROR_NO_DEVICE:
      return ENODEV;

    case LIBUSB_ERROR_NOT_FOUND:
      return ENOENT;

    case LIBUSB_ERROR_BUSY:
      return EBUSY;

    case LIBUSB_ERROR_TIMEOUT:
      return EAGAIN;

#ifdef EMSGSIZE
    case LIBUSB_ERROR_OVERFLOW:
      return EMSGSIZE;
#endif /* EMSGSIZE */

    case LIBUSB_ERROR_PIPE:
      return EPIPE;

    case LIBUSB_ERROR_INTERRUPTED:
      return EINTR;

    case LIBUSB_ERROR_NO_MEM:
      return ENOMEM;

    case LIBUSB_ERROR_NOT_SUPPORTED:
      return ENOSYS;

    default:
      logMessage(LOG_CATEGORY(USB_IO), "unsupported libusb1 error code: %d", error);
    case LIBUSB_ERROR_OTHER:
      return EIO;
  }
}

static void
usbSetErrno (enum libusb_error error, const char *action) {
  errno = usbToErrno(error);
  if (action) logSystemError(action);
}

static int
usbGetHandle (UsbDeviceExtension *devx) {
  if (!devx->handle) {
    int result;

    if ((result = libusb_open(devx->device, &devx->handle)) != LIBUSB_SUCCESS) {
      usbSetErrno(result, "libusb_open");
      return 0;
    }
  }

  return 1;
}

int
usbDisableAutosuspend (UsbDevice *device) {
  logUnsupportedFunction();
  return 0;
}

int
usbSetConfiguration (UsbDevice *device, unsigned char configuration) {
  UsbDeviceExtension *devx = device->extension;

  if (usbGetHandle(devx)) {
    int result;

    logMessage(LOG_CATEGORY(USB_IO), "setting configuration: %u", configuration);
    result = libusb_set_configuration(devx->handle, configuration);
    if (result == LIBUSB_SUCCESS) return 1;
    usbSetErrno(result, "libusb_set_configuration");
  }

  return 0;
}

int
usbClaimInterface (UsbDevice *device, unsigned char interface) {
  UsbDeviceExtension *devx = device->extension;

  if (usbGetHandle(devx)) {
    int detached = 0;
    int result;

    logMessage(LOG_CATEGORY(USB_IO), "claiming interface: %u", interface);

    while (1) {
      result = libusb_claim_interface(devx->handle, interface);
      if (result == LIBUSB_SUCCESS) return 1;

      if (result != LIBUSB_ERROR_BUSY)  break;
      if (detached) break;

      logMessage(LOG_WARNING, "USB interface in use: %u", interface);
      result = libusb_detach_kernel_driver(devx->handle, interface);

      if (result == LIBUSB_SUCCESS) {
        logMessage(LOG_WARNING, "USB interface detached: %u", interface);
        detached = 1;
        continue;
      }

      result = LIBUSB_ERROR_BUSY;
      break;
    }

    usbSetErrno(result, "libusb_claim_interface");
  }

  return 0;
}

int
usbReleaseInterface (UsbDevice *device, unsigned char interface) {
  UsbDeviceExtension *devx = device->extension;

  if (usbGetHandle(devx)) {
    int result;

    logMessage(LOG_CATEGORY(USB_IO), "releasing interface: %u", interface);
    result = libusb_release_interface(devx->handle, interface);
    if (result == LIBUSB_SUCCESS) return 1;
    usbSetErrno(result, "libusb_release_interface");
  }

  return 0;
}

int
usbSetAlternative (
  UsbDevice *device,
  unsigned char interface,
  unsigned char alternative
) {
  UsbDeviceExtension *devx = device->extension;

  if (usbGetHandle(devx)) {
    int result;

    logMessage(LOG_CATEGORY(USB_IO), "setting alternative: %u[%u]", interface, alternative);
    result = libusb_set_interface_alt_setting(devx->handle, interface, alternative);
    if (result == LIBUSB_SUCCESS) return 1;
    usbSetErrno(result, "libusb_set_interface_alt_setting");
  }

  return 0;
}

int
usbResetDevice (UsbDevice *device) {
  UsbDeviceExtension *devx = device->extension;

  if (usbGetHandle(devx)) {
    logMessage(LOG_CATEGORY(USB_IO), "reset device");
    int result = libusb_reset_device(devx->handle);
    if (result == LIBUSB_SUCCESS) return 1;
    usbSetErrno(result, "libusb_reset_device");
  }

  return 0;
}

int
usbClearHalt (UsbDevice *device, unsigned char endpointAddress) {
  UsbDeviceExtension *devx = device->extension;

  if (usbGetHandle(devx)) {
    logMessage(LOG_CATEGORY(USB_IO), "clear halt: %02X", endpointAddress);
    int result = libusb_clear_halt(devx->handle, endpointAddress);
    if (result == LIBUSB_SUCCESS) return 1;
    usbSetErrno(result, "libusb_clear_halt");
  }

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

  if (usbGetHandle(devx)) {
    UsbSetupPacket setup;
    int result;

    usbMakeSetupPacket(&setup, direction, recipient, type, request, value, index, length);

    if (direction == UsbControlDirection_Output) {
      if (length) logBytes(LOG_CATEGORY(USB_IO), "control output", buffer, length);
    }

    result = libusb_control_transfer(devx->handle,
                                     setup.bRequestType, setup.bRequest,
                                     getLittleEndian16(setup.wValue),
                                     getLittleEndian16(setup.wIndex), buffer,
                                     getLittleEndian16(setup.wLength), timeout);

    if (result >= 0) {
      if (direction == UsbControlDirection_Input) {
        logBytes(LOG_CATEGORY(USB_IO), "control input", buffer, result);
      }

      return result;
    }

    usbSetErrno(result, "");
  }

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
  UsbDeviceExtension *devx = device->extension;

  if (usbGetHandle(devx)) {
    UsbEndpoint *endpoint;

    if ((endpoint = usbGetInputEndpoint(device, endpointNumber))) {
      const UsbEndpointDescriptor *descriptor = endpoint->descriptor;
      UsbEndpointTransfer transfer = USB_ENDPOINT_TRANSFER(descriptor);
      int actual_length;
      int result;

      switch (transfer) {
        case UsbEndpointTransfer_Bulk:
          result = libusb_bulk_transfer(devx->handle, descriptor->bEndpointAddress,
                                        buffer, length, &actual_length, timeout);
          break;

        case UsbEndpointTransfer_Interrupt:
          result = libusb_interrupt_transfer(devx->handle, descriptor->bEndpointAddress,
                                             buffer, length, &actual_length, timeout);
          break;

        default:
          logMessage(LOG_ERR, "USB endpoint input transfer not supported: 0X%02X", transfer);
          result = LIBUSB_ERROR_NOT_SUPPORTED;
          break;
      }

      if (result == LIBUSB_SUCCESS) {
        ssize_t count = actual_length;

        if (usbApplyInputFilters(endpoint, buffer, length, &count)) return count;
        result = LIBUSB_ERROR_IO;
      }

      usbSetErrno(result, NULL);
    }
  }

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

  if (usbGetHandle(devx)) {
    UsbEndpoint *endpoint;

    if ((endpoint = usbGetOutputEndpoint(device, endpointNumber))) {
      const UsbEndpointDescriptor *descriptor = endpoint->descriptor;
      UsbEndpointTransfer transfer = USB_ENDPOINT_TRANSFER(descriptor);
      int actual_length;
      int result;

      switch (transfer) {
        case UsbEndpointTransfer_Bulk:
          result = libusb_bulk_transfer(devx->handle, descriptor->bEndpointAddress,
                                        (void *)buffer, length, &actual_length, timeout);
          break;

        case UsbEndpointTransfer_Interrupt:
          result = libusb_interrupt_transfer(devx->handle, descriptor->bEndpointAddress,
                                             (void *)buffer, length, &actual_length, timeout);
          break;

        default:
          logMessage(LOG_ERR, "USB endpoint output transfer not supported: 0X%02X", transfer);
          result = LIBUSB_ERROR_NOT_SUPPORTED;
          break;
      }

      if (result == LIBUSB_SUCCESS) return actual_length;
      usbSetErrno(result, NULL);
    }
  }

  logSystemError("USB endpoint write");
  return -1;
}

int
usbReadDeviceDescriptor (UsbDevice *device) {
  UsbDeviceExtension *devx = device->extension;
  struct libusb_device_descriptor descriptor;
  int result;

  if ((result = libusb_get_device_descriptor(devx->device, &descriptor)) == LIBUSB_SUCCESS) {
    memcpy(&device->descriptor, &descriptor, UsbDescriptorSize_Device);
    return 1;
  } else {
    usbSetErrno(result, "libusb_get_device_descriptor");
  }

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
  if (devx->handle) libusb_close(devx->handle);
  libusb_unref_device(devx->device);
  free(devx);
}

UsbDevice *
usbFindDevice (UsbDeviceChooser *chooser, UsbChooseChannelData *data) {
  int result;
  UsbDeviceExtension *devx;

  if (!usbContext) {
    if ((result = libusb_init(&usbContext)) != LIBUSB_SUCCESS) {
      usbSetErrno(result, "libusb_init");
      return NULL;
    }
  }

  if (!usbDeviceList) {
    ssize_t count;

    if ((count = libusb_get_device_list(usbContext, &usbDeviceList)) < 0) {
      usbSetErrno(count, "libusb_get_device_list");
      return NULL;
    }

    usbDeviceCount = count;
  }

  if ((devx = malloc(sizeof(*devx)))) {
    libusb_device **libusbDevice = usbDeviceList;
    int deviceCount = usbDeviceCount;

    while (deviceCount) {
      deviceCount -= 1;
      devx->device = *libusbDevice++;
      libusb_ref_device(devx->device);

      devx->handle = NULL;

      {
        UsbDevice *device = usbTestDevice(devx, chooser, data);
        if (device) return device;
      }

      libusb_unref_device(devx->device);
    }

    free(devx);
  } else {
    logMallocError();
  }

  return NULL;
}

void
usbForgetDevices (void) {
  if (usbDeviceList) {
    libusb_free_device_list(usbDeviceList, 1);
    usbDeviceList = NULL;
  }

  usbDeviceCount = 0;
}
