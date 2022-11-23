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

#include <limits.h>

#include "log.h"

struct UsbDeviceExtensionStruct {
  char *path;
  int file;
  int timeout;
};

struct UsbEndpointExtensionStruct {
  int file;
  int timeout;
};

static int
usbSetTimeout (int file, int new, int *old) {
  if (!old || (new != *old)) {
    int arg = new;
    if (ioctl(file, USB_SET_TIMEOUT, &arg) == -1) {
      logSystemError("USB timeout set");
      return 0;
    }
    if (old) *old = new;
  }
  return 1;
}

static int
usbSetShortTransfers (int file, int arg) {
  if (ioctl(file, USB_SET_SHORT_XFER, &arg) != -1) return 1;
  logSystemError("USB set short transfers");
  return 0;
}

int
usbDisableAutosuspend (UsbDevice *device) {
  logUnsupportedFunction();
  return 0;
}

int
usbSetConfiguration (UsbDevice *device, unsigned char configuration) {
  UsbDeviceExtension *devx = device->extension;
  int arg = configuration;
  if (ioctl(devx->file, USB_SET_CONFIG, &arg) != -1) return 1;
  logSystemError("USB configuration set");
  return 0;
}

int
usbClaimInterface (UsbDevice *device, unsigned char interface) {
  return 1;
/*
  logUnsupportedFunction();
  return 0;
*/
}

int
usbReleaseInterface (UsbDevice *device, unsigned char interface) {
  return 1;
/*
  logUnsupportedFunction();
  return 0;
*/
}

int
usbSetAlternative (
  UsbDevice *device,
  unsigned char interface,
  unsigned char alternative
) {
  UsbDeviceExtension *devx = device->extension;
  struct usb_alt_interface arg;
  memset(&arg, 0, sizeof(arg));
  arg.uai_interface_index = interface;
  arg.uai_alt_no = alternative;
  if (ioctl(devx->file, USB_SET_ALTINTERFACE, &arg) != -1) return 1;
  logSystemError("USB alternative set");
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
  UsbDeviceExtension *devx = device->extension;
  struct usb_ctl_request arg;
  memset(&arg, 0, sizeof(arg));
  arg.ucr_request.bmRequestType = direction | recipient | type;
  arg.ucr_request.bRequest = request;
  USETW(arg.ucr_request.wValue, value);
  USETW(arg.ucr_request.wIndex, index);
  USETW(arg.ucr_request.wLength, length);
  arg.ucr_data = buffer;
  arg.ucr_flags = USBD_SHORT_XFER_OK;
  if (usbSetTimeout(devx->file, timeout, &devx->timeout)) {
    if (ioctl(devx->file, USB_DO_REQUEST, &arg) != -1) return arg.ucr_actlen;
    logSystemError("USB control transfer");
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
  ssize_t count = -1;
  UsbEndpoint *endpoint = usbGetInputEndpoint(device, endpointNumber);
  if (endpoint) {
    UsbEndpointExtension *eptx = endpoint->extension;
    if (usbSetTimeout(eptx->file, timeout, &eptx->timeout)) {
      if ((count = read(eptx->file, buffer, length)) != -1) {
        if (!usbApplyInputFilters(endpoint, buffer, length, &count)) {
          errno = EIO;
          count = -1;
        }
      } else if (errno != ETIMEDOUT) {
        logSystemError("USB endpoint read");
      }
    }
  }
  return count;
}

ssize_t
usbWriteEndpoint (
  UsbDevice *device,
  unsigned char endpointNumber,
  const void *buffer,
  size_t length,
  int timeout
) {
  UsbEndpoint *endpoint = usbGetOutputEndpoint(device, endpointNumber);
  if (endpoint) {
    UsbEndpointExtension *eptx = endpoint->extension;
    if (usbSetTimeout(eptx->file, timeout, &eptx->timeout)) {
      ssize_t count = write(eptx->file, buffer, length);
      if (count != -1) return count;
      logSystemError("USB endpoint write");
    }
  }
  return -1;
}

int
usbReadDeviceDescriptor (UsbDevice *device) {
  UsbDeviceExtension *devx = device->extension;
  if (ioctl(devx->file, USB_GET_DEVICE_DESC, &device->descriptor) != -1) {
    return 1;
  }
  logSystemError("USB device descriptor read");
  return 0;
}

int
usbAllocateEndpointExtension (UsbEndpoint *endpoint) {
  UsbDeviceExtension *devx = endpoint->device->extension;
  UsbEndpointExtension *eptx;

  if ((eptx = malloc(sizeof(*eptx)))) {
    const char *prefix = devx->path;
    const char *dot = strchr(prefix, '.');
    int length = dot? (dot - prefix): strlen(prefix);
    char path[PATH_MAX+1];
    int flags = O_RDWR;

    snprintf(path, sizeof(path), USB_ENDPOINT_PATH_FORMAT,
             length, prefix, USB_ENDPOINT_NUMBER(endpoint->descriptor));

    switch (USB_ENDPOINT_DIRECTION(endpoint->descriptor)) {
      case UsbEndpointDirection_Input : flags = O_RDONLY; break;
      case UsbEndpointDirection_Output: flags = O_WRONLY; break;
    }

    if ((eptx->file = open(path, flags)) != -1) {
      if (((flags & O_ACCMODE) != O_RDONLY) || 
          usbSetShortTransfers(eptx->file, 1)) {
        eptx->timeout = -1;

        endpoint->extension = eptx;
        return 1;
      }

      close(eptx->file);
    }

    free(eptx);
  }

  return 0;
}

void
usbDeallocateEndpointExtension (UsbEndpointExtension *eptx) {
  if (eptx->file != -1) {
    close(eptx->file);
    eptx->file = -1;
  }

  free(eptx);
}

void
usbDeallocateDeviceExtension (UsbDeviceExtension *devx) {
  if (devx->file != -1) {
    close(devx->file);
    devx->file = -1;
  }

  free(devx->path);
  free(devx);
}

UsbDevice *
usbFindDevice (UsbDeviceChooser *chooser, UsbChooseChannelData *data) {
  UsbDevice *device = NULL;
  int busNumber = 0;
  while (1) {
    char busPath[PATH_MAX+1];
    int bus;
    snprintf(busPath, sizeof(busPath), "/dev/usb%d", busNumber);
    if ((bus = open(busPath, O_RDONLY)) != -1) {
      int deviceNumber;
      for (deviceNumber=1; deviceNumber<USB_MAX_DEVICES; deviceNumber++) {
        struct usb_device_info info;
        memset(&info, 0, sizeof(info));
        info.udi_addr = deviceNumber;
        if (ioctl(bus, USB_DEVICEINFO, &info) != -1) {
          static const char *driver = "ugen";
          const char *deviceName = info.udi_devnames[0];

          logMessage(LOG_CATEGORY(USB_IO), "device [%d,%d]: vendor=%s product=%s",
                     busNumber, deviceNumber, info.udi_vendor, info.udi_product);
          {
            int nameNumber;
            for (nameNumber=0; nameNumber<USB_MAX_DEVNAMES; nameNumber++) {
              const char *name = info.udi_devnames[nameNumber];
              if (*name)
                logMessage(LOG_CATEGORY(USB_IO), "name %d: %s", nameNumber, name);
            }
          }

          if (strncmp(deviceName, driver, strlen(driver)) == 0) {
            char devicePath[PATH_MAX+1];
            snprintf(devicePath, sizeof(devicePath), USB_CONTROL_PATH_FORMAT, deviceName);

            {
              UsbDeviceExtension *devx;
              if ((devx = malloc(sizeof(*devx)))) {
                if ((devx->path = strdup(devicePath))) {
                  if ((devx->file = open(devx->path, O_RDWR)) != -1) {
                    devx->timeout = -1;
                    if ((device = usbTestDevice(devx, chooser, data))) {
                      close(bus);
                      return device;
                    }
                    close(devx->file);
                  }
                  free(devx->path);
                }
                free(devx);
              }
            }
          }
        } else if (errno != ENXIO) {
          logSystemError("USB device query");
        }
      }
      close(bus);
    } else if (errno == ENOENT) {
      break;
    } else if (errno != ENXIO) {
      logSystemError("USB bus open");
    }
    busNumber++;
  }
  return device;
}

void
usbForgetDevices (void) {
}
