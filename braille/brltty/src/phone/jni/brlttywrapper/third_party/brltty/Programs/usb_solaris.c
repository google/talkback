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
#include <strings.h>
#include <errno.h>
#include <dirent.h>
#include <fcntl.h>
#include <sys/asynch.h>
#include <sys/usb/clients/ugen/usb_ugen.h>

#include "log.h"
#include "io_usb.h"
#include "usb_internal.h"

struct UsbDeviceExtensionStruct {
  char *path;
  int data;
  int status;

  unsigned char configuration;
  unsigned char interface;
  unsigned char alternative;
};

struct UsbEndpointExtensionStruct {
  Queue *requests;

  char *name;
  int data;
  int status;
};

typedef struct {
  aio_result_t result; /* must be first for aiowait() */

  UsbEndpoint *endpoint;

  void *context;
  void *buffer;
  size_t length;
} UsbAsynchronousRequest;

static int
usbOpenStatusFile (const char *path, int *status) {
  if ((*status = open(path, O_RDONLY)) != -1) return 1;
  logSystemError("USB status file open");
  return 0;
}

static int
usbOpenEndpointFiles (
  const char *device,
  const char *endpoint,
  int *data,
  int *status,
  int flags
) {
  static const char *const suffix = "stat";
  char path[strlen(device) + 1 + strlen(endpoint) + strlen(suffix) + 1];

  sprintf(path, "%s/%s", device, endpoint);
  if ((*data = open(path, flags)) != -1) {
    strcat(path, suffix);
    if (usbOpenStatusFile(path, status)) {
      return 1;
    } else {
      logSystemError("USB endpoint status open");
    }

    close(*data);
    *data = -1;
  } else {
    logSystemError("USB endpoint data open");
  }

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
  devx->configuration = configuration;
  return 1;
}

int
usbClaimInterface (UsbDevice *device, unsigned char interface) {
  UsbDeviceExtension *devx = device->extension;
  devx->interface = interface;
  devx->alternative = 0;
  return 1;
}

int
usbReleaseInterface (UsbDevice *device, unsigned char interface) {
  return 1;
}

int
usbSetAlternative (
  UsbDevice *device,
  unsigned char interface,
  unsigned char alternative
) {
  UsbDeviceExtension *devx = device->extension;
  devx->interface = interface;
  devx->alternative = alternative;
  return 1;
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
  UsbSetupPacket setup;

  setup.bRequestType = direction | recipient | type;
  setup.bRequest = request;
  putLittleEndian16(&setup.wValue, value);
  putLittleEndian16(&setup.wIndex, index);
  putLittleEndian16(&setup.wLength, length);

  switch (direction) {
    case UsbControlDirection_Input: {
      size_t size = sizeof(setup);
      ssize_t count;

      if ((count = write(devx->data, &setup, size)) == -1) {
        logSystemError("USB control request");
      } else if (count != size) {
        logMessage(LOG_ERR, "USB truncated control request: %d < %d", count, size);
        errno = EIO;
      } else if ((count = read(devx->data, buffer, length)) == -1) {
        logSystemError("USB control read");
      } else {
        return count;
      }
      break;
    }

    case UsbControlDirection_Output: {
      unsigned char packet[sizeof(setup) + length];
      size_t size = 0;
      ssize_t count;

      memcpy(&packet[size], &setup, sizeof(setup));
      size += sizeof(setup);

      memcpy(&packet[size], buffer, length);
      size += length;

      if ((count = write(devx->data, packet, size)) == -1) {
        logSystemError("USB control write");
      } else if (count != size) {
        logMessage(LOG_ERR, "USB truncated control write: %d < %d", count, size);
        errno = EIO;
      } else {
        return size;
      }
      break;
    }

    default:
      logMessage(LOG_ERR, "USB unsupported control direction: %02X", direction);
      errno = ENOSYS;
      break;
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
  UsbEndpoint *endpoint;

  if ((endpoint = usbGetEndpoint(device, endpointAddress))) {
    UsbEndpointExtension *eptx = endpoint->extension;
    UsbAsynchronousRequest *request;

    if ((request = malloc(sizeof(*request) + length))) {
      UsbEndpointDirection direction = USB_ENDPOINT_DIRECTION(endpoint->descriptor);

      request->endpoint = endpoint;
      request->context = context;
      request->buffer = (request->length = length)? (request + 1): NULL;
      request->result.aio_return = AIO_INPROGRESS;

      switch (direction) {
        case UsbEndpointDirection_Input:
          if (aioread(eptx->data, request->buffer, request->length, 0, SEEK_CUR, &request->result) != -1) return request;
          logSystemError("USB asynchronous read");
          break;

        case UsbEndpointDirection_Output:
          if (request->buffer) memcpy(request->buffer, buffer, length);
          if (aiowrite(eptx->data, request->buffer, request->length, 0, SEEK_CUR, &request->result) != -1) return request;
          logSystemError("USB asynchronous write");
          break;

        default:
          logMessage(LOG_ERR, "USB unsupported asynchronous direction: %02X", direction);
          errno = ENOSYS;
          break;
      }

      free(request);
    } else {
      logSystemError("USB asynchronous request allocate");
    }
  }

  return NULL;
}

int
usbCancelRequest (UsbDevice *device, void *request) {
  UsbAsynchronousRequest *req = request;
  UsbEndpoint *endpoint = req->endpoint;
  UsbEndpointExtension *eptx = endpoint->extension;

  if (!deleteItem(eptx->requests, req)) {
    if (aiocancel(&req->result) == -1) {
      if ((errno != EINVAL) && (errno != EACCES)) {
        logSystemError("USB asynchronous cancel");
        return 0;
      }
    }
  }

  free(request);
  return 1;
}

void *
usbReapResponse (
  UsbDevice *device,
  unsigned char endpointAddress,
  UsbResponse *response,
  int wait
) {
  UsbEndpoint *endpoint;

  if ((endpoint = usbGetEndpoint(device, endpointAddress))) {
    UsbEndpointExtension *eptx = endpoint->extension;
    struct timeval timeout;
    UsbAsynchronousRequest *request;

    timeout.tv_sec = 0;
    timeout.tv_usec = 0;

    while (!(request = dequeueItem(eptx->requests))) {
      aio_result_t *result;

    doWait:
      if ((int)(result = aiowait(wait? NULL: &timeout)) == -1) {
        if (errno == EINTR) goto doWait;

        if (errno != EINVAL) {
          logSystemError("USB asynchronous wait");
          return NULL;
        }

        result = NULL;
      }

      if (!result) {
        errno = EAGAIN;
        return NULL;
      }

      request = (UsbAsynchronousRequest *)result;
      {
        UsbEndpoint *ep = request->endpoint;
        UsbEndpointExtension *epx = ep->extension;
        if (!enqueueItem(epx->requests, request)) {
          logSystemError("USB asynchronous enqueue");
        }
      }
    }

    response->context = request->context;
    response->buffer = request->buffer;
    response->size = request->length;
    response->count = request->result.aio_return;
    response->error = request->result.aio_errno;

    if (response->count == -1) {
      errno = response->error;
      logSystemError("USB asynchronous completion");
    } else {
      switch (USB_ENDPOINT_DIRECTION(endpoint->descriptor)) {
        case UsbEndpointDirection_Input:
          if (!usbApplyInputFilters(endpoint, response->buffer, response->size, &response->count)) {
            response->error = EIO;
            response->count = -1;
          }
          break;
      }
    }

    return request;
  }

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
  UsbEndpoint *endpoint;

  if ((endpoint = usbGetInputEndpoint(device, endpointNumber))) {
    UsbEndpointExtension *eptx = endpoint->extension;
    ssize_t count;

  doRead:
    if ((count = read(eptx->data, buffer, length)) == -1) {
      if (errno == EINTR) goto doRead;
      logSystemError("USB endpoint read");
    } else if (!usbApplyInputFilters(endpoint, buffer, length, &count)) {
      errno = EIO;
    } else if (!count) {
      errno = EAGAIN;
    } else {
      return count;
    }
  }

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
  UsbEndpoint *endpoint;

  if ((endpoint = usbGetOutputEndpoint(device, endpointNumber))) {
    UsbEndpointExtension *eptx = endpoint->extension;
    ssize_t count;

  doWrite:
    if ((count = write(eptx->data, buffer, length)) == -1) {
      if (errno == EINTR) goto doWrite;
      logSystemError("USB endpoint write");
    } else if (count != length) {
      logMessage(LOG_ERR, "USB truncated endpoint write: %d < %d", count, length);
      errno = EIO;
    } else {
      return count;
    }
  }

  return -1;
}

int
usbReadDeviceDescriptor (UsbDevice *device) {
  int count = usbGetDeviceDescriptor(device, &device->descriptor);

  if (count == UsbDescriptorSize_Device) {
    return 1;
  }

  if (count != -1) {
    logMessage(LOG_ERR, "USB short device descriptor (%d).", count);
    errno = EIO;
  }

  return 0;
}

int
usbAllocateEndpointExtension (UsbEndpoint *endpoint) {
  UsbDevice *device = endpoint->device;
  UsbDeviceExtension *devx = device->extension;
  UsbEndpointExtension *eptx;

  if ((eptx = malloc(sizeof(*eptx)))) {
    if ((eptx->requests = newQueue(NULL, NULL))) {
      int flags;
  
      {
        char name[0X80];
        int length = 0;
  
        if (devx->configuration != 1) {
          int count;
          sprintf(&name[length], "cfg%d%n", devx->configuration, &count);
          length += count;
        }
  
        {
          int count;
          sprintf(&name[length], "if%d%n", devx->interface, &count);
          length += count;
        }
  
        if (devx->alternative != 0) {
          int count;
          sprintf(&name[length], ".%d%n", devx->alternative, &count);
          length += count;
        }
  
        {
          const UsbEndpointDescriptor *descriptor = endpoint->descriptor;
          UsbEndpointDirection direction = USB_ENDPOINT_DIRECTION(descriptor);
          const char *prefix;
  
          switch (direction) {
            case UsbEndpointDirection_Input:
              prefix = "in";
              flags = O_RDONLY;
              break;
  
            case UsbEndpointDirection_Output:
              prefix = "out";
              flags = O_WRONLY;
              break;
  
            default:
              logMessage(LOG_ERR, "USB unsupported endpoint direction: %02X", direction);
              goto nameError;
          }
  
          {
            int count;
            sprintf(&name[length], "%s%d%n", prefix, USB_ENDPOINT_NUMBER(descriptor), &count);
            length += count;
          }
        }
  
        eptx->name = strdup(name);
      }
  
      if (eptx->name) {
        if (usbOpenEndpointFiles(devx->path, eptx->name, &eptx->data, &eptx->status,  flags)) {
          endpoint->extension = eptx;
          return 1;
        }
  
        free(eptx->name);
      }
    nameError:

      deallocateQueue(eptx->requests);
    }

    free(eptx);
  }

  return 0;
}

void
usbDeallocateEndpointExtension (UsbEndpointExtension *eptx) {
  if (eptx->status != -1) {
    close(eptx->status);
    eptx->status = -1;
  }

  if (eptx->data != -1) {
    close(eptx->data);
    eptx->data = -1;
  }

  if (eptx->name) {
    free(eptx->name);
    eptx->name = NULL;
  }

  if (eptx->requests) {
    deallocateQueue(eptx->requests);
    eptx->requests = NULL;
  }

  free(eptx);
}

void
usbDeallocateDeviceExtension (UsbDeviceExtension *devx) {
  if (devx->status != -1) {
    close(devx->status);
    devx->status = -1;
  }

  if (devx->data != -1) {
    close(devx->data);
    devx->data = -1;
  }

  if (devx->path) {
    free(devx->path);
    devx->path = NULL;
  }

  free(devx);
}

UsbDevice *
usbFindDevice (UsbDeviceChooser *chooser, UsbChooseChannelData *data) {
  UsbDevice *device = NULL;
  static const char *const rootPath = "/dev/usb";
  int rootLength = strlen(rootPath);
  DIR *rootDirectory;

  if ((rootDirectory = opendir(rootPath))) {
    struct dirent *deviceEntry;

    while ((deviceEntry = readdir(rootDirectory))) {
      const char *deviceName = deviceEntry->d_name;
      int deviceLength = strlen(deviceName);

      {
        unsigned int vendor;
        unsigned int product;
        int length;
        if (sscanf(deviceName, "%x.%x%n", &vendor, &product, &length) < 2) continue;
        if (length != deviceLength) continue;
      }

      deviceLength += rootLength + 1;
      {
        char devicePath[deviceLength + 1];
        DIR *deviceDirectory;

        sprintf(devicePath, "%s/%s", rootPath, deviceName);
        if ((deviceDirectory = opendir(devicePath))) {
          struct dirent *instanceEntry;

          while ((instanceEntry = readdir(deviceDirectory))) {
            const char *instanceName = instanceEntry->d_name;
            int instanceLength = strlen(instanceName);

            {
              unsigned int number;
              int length;
              if (sscanf(instanceName, "%u%n", &number, &length) < 1) continue;
              if (length != instanceLength) continue;
            }

            instanceLength += deviceLength + 1;
            {
              char instancePath[instanceLength + 1];
              UsbDeviceExtension *devx;

              sprintf(instancePath, "%s/%s", devicePath, instanceName);
              if ((devx = malloc(sizeof(*devx)))) {
                if ((devx->path = strdup(instancePath))) {
                  if (usbOpenEndpointFiles(devx->path, "cntrl0", &devx->data, &devx->status, O_RDWR)) {
                    if ((device = usbTestDevice(devx, chooser, data))) break;

                    close(devx->status);
                    close(devx->data);
                  }

                  free(devx->path);
                }

                free(devx);
              }
            }
          }

          closedir(deviceDirectory);
        }
      }

      if (device) break;
    }

    closedir(rootDirectory);
  }

  return device;
}

void
usbForgetDevices (void) {
}
