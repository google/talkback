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

#include "prologue.h"

#include <stdio.h>
#include <string.h>
#include <ctype.h>
#include <errno.h>
#include <dirent.h>
#include <fcntl.h>
#include <sys/stat.h>
#include <sys/vfs.h>
#include <sys/ioctl.h>
#include <linux/usbdevice_fs.h>

#ifndef USBDEVFS_DISCONNECT
#define USBDEVFS_DISCONNECT _IO('U', 22)
#endif /* USBDEVFS_DISCONNECT */

#ifndef USBDEVFS_CONNECT
#define USBDEVFS_CONNECT _IO('U', 23)
#endif /* USBDEVFS_CONNECT */

#include "parameters.h"
#include "log.h"
#include "strfmt.h"
#include "file.h"
#include "parse.h"
#include "timing.h"
#include "async_wait.h"
#include "async_alarm.h"
#include "async_io.h"
#include "async_signal.h"
#include "mntpt.h"
#include "io_usb.h"
#include "usb_internal.h"

typedef struct {
  char *sysfsPath;
  char *usbfsPath;
  UsbDeviceDescriptor usbDescriptor;
} UsbHostDevice;

static Queue *usbHostDevices = NULL;

struct UsbDeviceExtensionStruct {
  const UsbHostDevice *host;
  int usbfsFile;
  AsyncHandle usbfsMonitorHandle;
};

struct UsbEndpointExtensionStruct {
  Queue *completedRequests;

  struct {
    struct {
      AsyncHandle handle;
      int number;
    } signal;
  } monitor;
};

static int
usbOpenUsbfsFile (UsbDeviceExtension *devx) {
  if (devx->usbfsFile == -1) {
    if ((devx->usbfsFile = open(devx->host->usbfsPath, O_RDWR)) == -1) {
      logMessage(LOG_ERR, "USBFS open error: %s: %s",
                 devx->host->usbfsPath, strerror(errno));
      return 0;
    }

    logMessage(LOG_CATEGORY(USB_IO), "usbfs file opened: %s fd=%d",
               devx->host->usbfsPath, devx->usbfsFile);
  }

  return 1;
}

static void
usbCloseUsbfsFile (UsbDeviceExtension *devx) {
  if (devx->usbfsFile != -1) {
    close(devx->usbfsFile);
    devx->usbfsFile = -1;
  }
}

int
usbDisableAutosuspend (UsbDevice *device) {
  UsbDeviceExtension *devx = device->extension;
  int ok = 0;

  if (devx->host->sysfsPath) {
    char *path = makePath(devx->host->sysfsPath, "power/autosuspend");

    if (path) {
      int file = open(path, O_WRONLY);

      if (file != -1) {
        static const char *const values[] = {"-1", "0", NULL};
        const char *const *value = values;

        while (*value) {
          size_t length = strlen(*value);
          ssize_t result = write(file, *value, length);

          if (result != -1) {
            ok = 1;
            break;
          }

          if (errno != EINVAL) {
            logMessage(LOG_ERR, "write error: %s: %s", path, strerror(errno));
            break;
          }

          ++value;
        }

        close(file);
      } else {
        logMessage((errno == ENOENT)? LOG_CATEGORY(USB_IO): LOG_ERR,
                   "open error: %s: %s", path, strerror(errno));
      }

      free(path);
    }
  }

  return ok;
}

static char *
usbGetDriver (UsbDevice *device, unsigned char interface) {
  UsbDeviceExtension *devx = device->extension;

  if (usbOpenUsbfsFile(devx)) {
    struct usbdevfs_getdriver arg;

    memset(&arg, 0, sizeof(arg));
    arg.interface = interface;

    if (ioctl(devx->usbfsFile, USBDEVFS_GETDRIVER, &arg) != -1) {
      char *name = strdup(arg.driver);
      if (name) return name;
      logMallocError();
    } else {
      logSystemError("USB get driver name");
    }
  }

  return NULL;
}

static int
usbControlDriver (
  UsbDevice *device,
  unsigned char interface,
  int code,
  void *data
) {
  UsbDeviceExtension *devx = device->extension;

  if (usbOpenUsbfsFile(devx)) {
    struct usbdevfs_ioctl arg;

    memset(&arg, 0, sizeof(arg));
    arg.ifno = interface;
    arg.ioctl_code = code;
    arg.data = data;

    if (ioctl(devx->usbfsFile, USBDEVFS_IOCTL, &arg) != -1) return 1;
    logSystemError("USB driver control");
  }

  return 0;
}

static int
usbDisconnectDriver (UsbDevice *device, unsigned char interface) {
#ifdef USBDEVFS_DISCONNECT
  logMessage(LOG_CATEGORY(USB_IO), "disconnecting kernel driver: Int:%u", interface);
  if (usbControlDriver(device, interface, USBDEVFS_DISCONNECT, NULL)) return 1;
#else /* USBDEVFS_DISCONNECT */
  errno = ENOSYS;
#endif /* USBDEVFS_DISCONNECT */

  logSystemError("USAB driver disconnect");
  return 0;
}

static int
usbDisconnectInterface (UsbDevice *device, unsigned char interface) {
  char *driver = usbGetDriver(device, interface);

  if (driver) {
    int isUsbfs = strcmp(driver, "usbfs") == 0;

    logMessage(LOG_WARNING, "USB interface in use: %u (%s)", interface, driver);
    free(driver);

    if (isUsbfs) {
      logPossibleCause("another " PACKAGE_TARNAME " process may be accessing the same device");
      errno = EBUSY;
    } else if (usbDisconnectDriver(device, interface)) {
      return 1;
    }
  }

  return 0;
}

int
usbSetConfiguration (UsbDevice *device, unsigned char configuration) {
  UsbDeviceExtension *devx = device->extension;

  logMessage(LOG_CATEGORY(USB_IO), "setting configuration: %u", configuration);

  if (usbOpenUsbfsFile(devx)) {
    unsigned int arg = configuration;

    if (ioctl(devx->usbfsFile, USBDEVFS_SETCONFIGURATION, &arg) != -1) return 1;
    logSystemError("USB configuration set");
  }

  return 0;
}

int
usbClaimInterface (UsbDevice *device, unsigned char interface) {
  UsbDeviceExtension *devx = device->extension;

  logMessage(LOG_CATEGORY(USB_IO), "claiming interface: %u", interface);

  if (usbOpenUsbfsFile(devx)) {
    int disconnected = 0;

    while (1) {
      unsigned int arg = interface;

      if (ioctl(devx->usbfsFile, USBDEVFS_CLAIMINTERFACE, &arg) != -1) return 1;
      if (errno != EBUSY) break;
      if (disconnected) break;

      if (!usbDisconnectInterface(device, interface)) {
        errno = EBUSY;
        break;
      }
      disconnected = 1;
    }

    logSystemError("USB interface claim");
  }

  return 0;
}

int
usbReleaseInterface (UsbDevice *device, unsigned char interface) {
  UsbDeviceExtension *devx = device->extension;

  logMessage(LOG_CATEGORY(USB_IO), "releasing interface: %u", interface);

  if (usbOpenUsbfsFile(devx)) {
    unsigned int arg = interface;
    if (ioctl(devx->usbfsFile, USBDEVFS_RELEASEINTERFACE, &arg) != -1) return 1;
    if (errno == ENODEV) return 1;
    logSystemError("USB interface release");
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

  logMessage(LOG_CATEGORY(USB_IO), "setting alternative: %u[%u]", interface, alternative);

  if (usbOpenUsbfsFile(devx)) {
    struct usbdevfs_setinterface arg;

    memset(&arg, 0, sizeof(arg));
    arg.interface = interface;
    arg.altsetting = alternative;

    if (ioctl(devx->usbfsFile, USBDEVFS_SETINTERFACE, &arg) != -1) return 1;
    logSystemError("USB alternative set");
  }

  return 0;
}

int
usbResetDevice (UsbDevice *device) {
  UsbDeviceExtension *devx = device->extension;

  logMessage(LOG_CATEGORY(USB_IO), "reset device");

  if (usbOpenUsbfsFile(devx)) {
    unsigned int arg = 0;

    if (ioctl(devx->usbfsFile, USBDEVFS_RESET, &arg) != -1) return 1;
    logSystemError("USB device reset");
  }

  return 0;
}

int
usbClearHalt (UsbDevice *device, unsigned char endpointAddress) {
  UsbDeviceExtension *devx = device->extension;

  logMessage(LOG_CATEGORY(USB_IO), "clear halt: %02X", endpointAddress);

  if (usbOpenUsbfsFile(devx)) {
    unsigned int arg = endpointAddress;

    if (ioctl(devx->usbfsFile, USBDEVFS_CLEAR_HALT, &arg) != -1) return 1;
    logSystemError("USB endpoint clear");
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

  if (usbOpenUsbfsFile(devx)) {
    UsbSetupPacket setup;
    struct usbdevfs_ctrltransfer arg;

    usbMakeSetupPacket(&setup, direction, recipient, type,
                       request, value, index, length);

    memset(&arg, 0, sizeof(arg));
    arg.bRequestType = setup.bRequestType;
    arg.bRequest = setup.bRequest;
    arg.wValue = getLittleEndian16(setup.wValue);
    arg.wIndex = getLittleEndian16(setup.wIndex);
    arg.wLength = getLittleEndian16(setup.wLength);
    arg.data = buffer;
    arg.timeout = timeout;

    if (direction == UsbControlDirection_Output) {
      if (length) logBytes(LOG_CATEGORY(USB_IO), "control output", buffer, length);
    }

    {
      ssize_t count = ioctl(devx->usbfsFile, USBDEVFS_CONTROL, &arg);

      if (count != -1) {
        if (direction == UsbControlDirection_Input) {
          logBytes(LOG_CATEGORY(USB_IO), "control input", buffer, count);
        }

        return count;
      }

      logSystemError("USB control transfer");
    }
  }

  return -1;
}

static UsbEndpoint *
usbReapURB (UsbDevice *device, int wait) {
  UsbDeviceExtension *devx = device->extension;

  if (usbOpenUsbfsFile(devx)) {
    struct usbdevfs_urb *urb;

    if (ioctl(devx->usbfsFile,
              wait? USBDEVFS_REAPURB: USBDEVFS_REAPURBNDELAY,
              &urb) != -1) {
      if (urb) {
        UsbEndpoint *endpoint;

        if ((endpoint = usbGetEndpoint(device, urb->endpoint))) {
          UsbEndpointExtension *eptx = endpoint->extension;

          if (enqueueItem(eptx->completedRequests, urb)) return endpoint;
          logSystemError("USB completed request enqueue");
          free(urb);
        }
      } else {
        errno = EAGAIN;
      }
    } else {
      if (wait || (errno != EAGAIN)) logSystemError("USB URB reap");
    }
  }

  return NULL;
}

typedef struct {
  const struct usbdevfs_urb *urb;
  const char *action;
} UsbFormatUrbData;

static size_t
usbFormatURB (char *buffer, size_t size, const void *data) {
  const UsbFormatUrbData *fud = data;
  const struct usbdevfs_urb *urb = fud->urb;
  size_t length;

  STR_BEGIN(buffer, size);
  STR_PRINTF("%s URB:", fud->action);

  STR_PRINTF(" Adr:%p", urb);
  STR_PRINTF(" Ept:%02X", urb->endpoint);

  STR_PRINTF(" Typ:%u", urb->type);
  {
    static const char *const types[] = {
      [USBDEVFS_URB_TYPE_CONTROL] = "ctl",
      [USBDEVFS_URB_TYPE_BULK] = "blk",
      [USBDEVFS_URB_TYPE_INTERRUPT] = "int",
      [USBDEVFS_URB_TYPE_ISO] = "iso"
    };

    if (urb->type < ARRAY_COUNT(types)) {
      const char *type = types[urb->type];
      if (type) STR_PRINTF("(%s)", type);
    }
  }

  STR_PRINTF(" Flg:%02X", urb->flags);
  {
    typedef struct {
      unsigned char bit;
      const char *name;
    } UrbFlagEntry;

    static const UrbFlagEntry urbFlagTable[] = {
#ifdef USBDEVFS_URB_SHORT_NOT_OK
      { .bit = USBDEVFS_URB_SHORT_NOT_OK,
        .name = "spd"
      },
#endif /* USBDEVFS_URB_SHORT_NOT_OK */

#ifdef USBDEVFS_URB_ISO_ASAP
      { .bit = USBDEVFS_URB_ISO_ASAP,
        .name = "isa"
      },
#endif /* USBDEVFS_URB_ISO_ASAP */

#ifdef USBDEVFS_URB_BULK_CONTINUATION
      { .bit = USBDEVFS_URB_BULK_CONTINUATION,
        .name = "bkc"
      },
#endif /* USBDEVFS_URB_BULK_CONTINUATION */

#ifdef USBDEVFS_URB_NO_FSBR
      { .bit = USBDEVFS_URB_NO_FSBR,
        .name = "nof"
      },
#endif /* USBDEVFS_URB_NO_FSBR */

#ifdef USBDEVFS_URB_ZERO_PACKET
      { .bit = USBDEVFS_URB_ZERO_PACKET,
        .name = "zpk"
      },
#endif /* USBDEVFS_URB_ZERO_PACKET */

#ifdef USBDEVFS_URB_NO_INTERRUPT
      { .bit = USBDEVFS_URB_NO_INTERRUPT,
        .name = "noi"
      },
#endif /* USBDEVFS_URB_NO_INTERRUPT */

      { .bit=0, .name=NULL }
    };

    int first = 1;
    const UrbFlagEntry *flag = urbFlagTable;

    while (flag->bit) {
      if (urb->flags & flag->bit) {
        STR_PRINTF("%c%s", (first? '(': ','), flag->name);
        first = 0;
      }

      flag += 1;
    }

    if (!first) STR_PRINTF(")");
  }

  STR_PRINTF(" Buf:%p", urb->buffer);
  STR_PRINTF(" Siz:%d", urb->buffer_length);
  STR_PRINTF(" Len:%d", urb->actual_length);
  STR_PRINTF(" Sig:%d", urb->signr);

  {
    int error = urb->status;
    STR_PRINTF(" Err:%d", error);

    if (error) {
      if (error < 0) error = -error;
      STR_PRINTF("(%s)", strerror(error));
    }
  }

  length = STR_LENGTH;
  STR_END;
  return length;
}

static void
usbLogURB (const struct usbdevfs_urb *urb, const char *action) {
  const UsbFormatUrbData fud = {
    .urb = urb,
    .action = action
  };

  logData(LOG_CATEGORY(USB_IO), usbFormatURB, &fud);
}

static struct usbdevfs_urb *
usbMakeURB (
  const UsbEndpointDescriptor *endpoint,
  void *buffer,
  size_t length,
  void *context
) {
  struct usbdevfs_urb *urb;

  if ((urb = malloc(sizeof(*urb) + length))) {
    memset(urb, 0, sizeof(*urb));
    urb->endpoint = endpoint->bEndpointAddress;
    urb->flags = 0;
    urb->signr = 0;
    urb->usercontext = context;

    if (!(urb->buffer_length = length)) {
      urb->buffer = NULL;
    } else {
      urb->buffer = urb + 1;
      if (buffer) memcpy(urb->buffer, buffer, length);
    }

    switch (USB_ENDPOINT_TRANSFER(endpoint)) {
      case UsbEndpointTransfer_Control:
        urb->type = USBDEVFS_URB_TYPE_CONTROL;
        break;

      case UsbEndpointTransfer_Isochronous:
        urb->type = USBDEVFS_URB_TYPE_ISO;
        break;

      case UsbEndpointTransfer_Interrupt:
        urb->type = USBDEVFS_URB_TYPE_INTERRUPT;
        break;

      case UsbEndpointTransfer_Bulk:
        urb->type = USBDEVFS_URB_TYPE_BULK;
        break;
    }

    return urb;
  } else {
    logMallocError();
  }

  return NULL;
}

static int
usbSubmitURB (struct usbdevfs_urb *urb, UsbEndpoint *endpoint) {
  const UsbEndpointDescriptor *descriptor = endpoint->descriptor;
  UsbDevice *device = endpoint->device;
  UsbDeviceExtension *devx = device->extension;

  while (1) {
    usbLogURB(urb, "submitting");

    if ((urb->endpoint & UsbEndpointDirection_Mask) == UsbEndpointDirection_Output) {
      logBytes(LOG_CATEGORY(USB_IO), "URB output", urb->buffer, urb->buffer_length);
    }

    if (ioctl(devx->usbfsFile, USBDEVFS_SUBMITURB, urb) != -1) {
      logMessage(LOG_CATEGORY(USB_IO), "URB submitted");
      return 1;
    }

    if ((errno == EINVAL) &&
        (USB_ENDPOINT_TRANSFER(descriptor) == UsbEndpointTransfer_Interrupt) &&
        (urb->type == USBDEVFS_URB_TYPE_BULK)) {
      logMessage(LOG_CATEGORY(USB_IO), "changing URB type from bulk to interrupt");
      urb->type = USBDEVFS_URB_TYPE_INTERRUPT;
      continue;
    }

    /* UHCI support returns ENXIO if a URB is already submitted. */
    logSystemError("USB URB submit");
    return 0;
  }
}

void *
usbSubmitRequest (
  UsbDevice *device,
  unsigned char endpointAddress,
  void *buffer,
  size_t length,
  void *context
) {
  UsbDeviceExtension *devx = device->extension;

  if (usbOpenUsbfsFile(devx)) {
    UsbEndpoint *endpoint;

    if ((endpoint = usbGetEndpoint(device, endpointAddress))) {
      UsbEndpointExtension *eptx = endpoint->extension;
      struct usbdevfs_urb *urb;

      if ((urb = usbMakeURB(endpoint->descriptor, buffer, length, context))) {
        urb->actual_length = 0;
        urb->signr = eptx->monitor.signal.number;

        if (usbSubmitURB(urb, endpoint)) {
          return urb;
        }

        free(urb);
      } else {
        logSystemError("USB URB allocate");
      }
    }
  }

  return NULL;
}

int
usbCancelRequest (UsbDevice *device, void *request) {
  UsbDeviceExtension *devx = device->extension;

  if (usbOpenUsbfsFile(devx)) {
    int reap = 1;

    if (ioctl(devx->usbfsFile, USBDEVFS_DISCARDURB, request) == -1) {
      if (errno == ENODEV) {
        reap = 0;
      } else if (errno != EINVAL) {
        logSystemError("USB URB discard");
      }
    }
    
    {
      struct usbdevfs_urb *urb = request;
      UsbEndpoint *endpoint;

      if ((endpoint = usbGetEndpoint(device, urb->endpoint))) {
        UsbEndpointExtension *eptx = endpoint->extension;
        int found = 1;

        while (!deleteItem(eptx->completedRequests, request)) {
          if (!reap) break;

          if (!usbReapURB(device, 0)) {
            found = 0;
            break;
          }
        }

        if (found) {
          free(request);
          return 1;
        }

        logMessage(LOG_ERR, "USB request not found: urb=%p ept=%02X",
                   urb, urb->endpoint);
      }
    }
  }

  return 0;
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
    struct usbdevfs_urb *urb;

    while (!(urb = dequeueItem(eptx->completedRequests))) {
      if (!usbReapURB(device, wait)) return NULL;
    }

    usbLogURB(urb, "reaped");

    response->context = urb->usercontext;
    response->buffer = urb->buffer;
    response->size = urb->buffer_length;

    if ((response->error = urb->status)) {
      if (response->error < 0) response->error = -response->error;
      errno = response->error;
      logSystemError("USB URB status");
      response->count = -1;
    } else {
      response->count = urb->actual_length;

      switch (USB_ENDPOINT_DIRECTION(endpoint->descriptor)) {
        case UsbEndpointDirection_Input:
          if (!usbApplyInputFilters(endpoint, response->buffer, response->size, &response->count)) {
            response->error = EIO;
            response->count = -1;
          }
          break;
      }
    }

    return urb;
  }

  return NULL;
}

static ssize_t
usbBulkTransfer (
  UsbEndpoint *endpoint,
  void *buffer,
  size_t length,
  int timeout
) {
  UsbDeviceExtension *devx = endpoint->device->extension;

  if (usbOpenUsbfsFile(devx)) {
    struct usbdevfs_bulktransfer arg;

    memset(&arg, 0, sizeof(arg));
    arg.ep = endpoint->descriptor->bEndpointAddress;
    arg.data = buffer;
    arg.len = length;
    arg.timeout = timeout;

    {
      int count = ioctl(devx->usbfsFile, USBDEVFS_BULK, &arg);
      if (count != -1) return count;
      if (USB_ENDPOINT_DIRECTION(endpoint->descriptor) == UsbEndpointDirection_Input)
        if (errno == ETIMEDOUT)
          errno = EAGAIN;
      if (errno != EAGAIN) logSystemError("USB bulk transfer");
    }
  }

  return -1;
}

static struct usbdevfs_urb *
usbInterruptTransfer (
  UsbEndpoint *endpoint,
  void *buffer,
  size_t length,
  int timeout
) {
  UsbDevice *device = endpoint->device;
  struct usbdevfs_urb *urb = usbSubmitRequest(device,
                                              endpoint->descriptor->bEndpointAddress,
                                              buffer, length, NULL);

  if (urb) {
    UsbEndpointExtension *eptx = endpoint->extension;
    int retryInterval = endpoint->descriptor->bInterval + 1;
    TimePeriod period;

    if (timeout) startTimePeriod(&period, timeout);

    do {
      if (usbReapURB(device, 0) &&
          deleteItem(eptx->completedRequests, urb)) {
        if (!urb->status) return urb;
        if ((errno = urb->status) < 0) errno = -errno;
        free(urb);
        break;
      }

      if (!timeout || afterTimePeriod(&period, NULL)) {
        usbCancelRequest(device, urb);
        errno = ETIMEDOUT;
        break;
      }

      asyncWait(retryInterval);
    } while (1);
  }

  return NULL;
}

int
usbMonitorInputEndpoint (
  UsbDevice *device, unsigned char endpointNumber,
  AsyncMonitorCallback *callback, void *data
) {
  return usbMonitorInputPipe(device, endpointNumber, callback, data);
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
  UsbEndpoint *endpoint;

  logMessage(LOG_CATEGORY(USB_IO), "reading endpoint: %u", endpointNumber);

  if ((endpoint = usbGetInputEndpoint(device, endpointNumber))) {
    UsbEndpointTransfer transfer = USB_ENDPOINT_TRANSFER(endpoint->descriptor);

    switch (transfer) {
      case UsbEndpointTransfer_Interrupt:
        if (!LINUX_USB_INPUT_TREAT_INTERRUPT_AS_BULK) {
          struct usbdevfs_urb *urb = usbInterruptTransfer(endpoint, NULL, length, timeout);

          if (urb) {
            count = urb->actual_length;
            if (count > length) count = length;
            memcpy(buffer, urb->buffer, count);
            free(urb);
          }

          break;
        }

      case UsbEndpointTransfer_Bulk:
        count = usbBulkTransfer(endpoint, buffer, length, timeout);
        break;

      default:
        logMessage(LOG_ERR, "USB input transfer not supported: %d", transfer);
        errno = ENOSYS;
        break;
    }

    if (count != -1) {
      if (!usbApplyInputFilters(endpoint, buffer, length, &count)) {
        errno = EIO;
        count = -1;
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
  UsbEndpoint *endpoint;

  if ((endpoint = usbGetOutputEndpoint(device, endpointNumber))) {
    UsbEndpointTransfer transfer = USB_ENDPOINT_TRANSFER(endpoint->descriptor);

    usbLogEndpointData(endpoint, "output", buffer, length);

    switch (transfer) {
      case UsbEndpointTransfer_Interrupt:
      case UsbEndpointTransfer_Bulk:
        return usbBulkTransfer(endpoint, (void *)buffer, length, timeout);
/*
      case UsbEndpointTransfer_Interrupt: {
        struct usbdevfs_urb *urb = usbInterruptTransfer(endpoint, (void *)buffer, length, timeout);

        if (urb) {
          ssize_t count = urb->actual_length;
          free(urb);
          return count;
        }
        break;
      }
*/
      default:
        logMessage(LOG_ERR, "USB output transfer not supported: %d", transfer);
        errno = ENOSYS;
        break;
    }
  }
  return -1;
}

int
usbReadDeviceDescriptor (UsbDevice *device) {
  device->descriptor = device->extension->host->usbDescriptor;
  return 1;
}

static int
usbHandleInputURB (UsbEndpoint *endpoint, struct usbdevfs_urb *urb) {
  deleteItem(endpoint->direction.input.pending.requests, urb);

  if (urb->actual_length < 0) {
    usbLogInputProblem(endpoint, "data not available");
    return 0;
  }

  return usbHandleInputResponse(endpoint, urb->buffer, urb->actual_length);
}

static void
usbInitializeSignalMonitor (UsbEndpointExtension *eptx) {
  eptx->monitor.signal.handle = NULL;
  eptx->monitor.signal.number = 0;
}

static void
usbStopSignalMonitor (UsbEndpointExtension *eptx) {
  if (eptx->monitor.signal.handle) {
    asyncCancelRequest(eptx->monitor.signal.handle);
    eptx->monitor.signal.handle = NULL;
  }

  if (eptx->monitor.signal.number) {
    asyncRelinquishSignalNumber(eptx->monitor.signal.number);
    eptx->monitor.signal.number = 0;
  }
}

ASYNC_SIGNAL_CALLBACK(usbHandleInputSignal) {
  UsbEndpoint *endpoint = parameters->data;
  UsbEndpointExtension *eptx = endpoint->extension;

  while (1) {
    UsbResponse response;
    struct usbdevfs_urb *urb = usbReapResponse(endpoint->device,
                                               endpoint->descriptor->bEndpointAddress,
                                               &response, 0);

    if (!urb) return 1;

    {
      int handled = 0;

      if (!response.error) {
        urb->actual_length = response.count;
        if (usbHandleInputURB(endpoint, urb)) handled = 1;
      } else {
        errno = response.error;
      }

      if (!handled) {
        usbSetEndpointInputError(endpoint, errno);
        usbStopSignalMonitor(eptx);
      }

      free(urb);
      if (!handled) return 0;
    }
  }
}

static int
usbStartSignalMonitor (UsbEndpoint *endpoint) {
  UsbEndpointExtension *eptx = endpoint->extension;

  if ((eptx->monitor.signal.number = asyncObtainSignalNumber())) {
    if (asyncMonitorSignal(&eptx->monitor.signal.handle,
                           eptx->monitor.signal.number,
                           usbHandleInputSignal, endpoint)) {
      logMessage(LOG_CATEGORY(USB_IO),
                 "signal monitor started: Ept:%02X Sig:%d",
                 endpoint->descriptor->bEndpointAddress,
                 eptx->monitor.signal.number);
      return 1;
    } else {
      usbLogInputProblem(endpoint, "monitor not registered");
    }

    asyncRelinquishSignalNumber(eptx->monitor.signal.number);
  } else {
    usbLogInputProblem(endpoint, "signal number not obtained");
  }

  return 0;
}

static void
usbInitializeUsbfsMonitor (UsbDeviceExtension *devx) {
  devx->usbfsMonitorHandle = NULL;
}

static void
usbStopUsbfsMonitor (UsbDeviceExtension *devx) {
  if (devx->usbfsMonitorHandle) {
    asyncCancelRequest(devx->usbfsMonitorHandle);
    devx->usbfsMonitorHandle = NULL;
  }
}

static int
usbHandleCompletedInputRequest (UsbEndpoint *endpoint, struct usbdevfs_urb *urb) {
  ssize_t count = urb->actual_length;
  int error = urb->status;

  if (!error) {
    if (usbApplyInputFilters(endpoint, urb->buffer, urb->buffer_length, &count)) {
      urb->actual_length = count;

      if (usbHandleInputURB(endpoint, urb)) {
        return 1;
      }
    }
  } else {
    if (error < 0) error = -error;
    errno = error;
    logSystemError("USB URB status");
  }

  return 0;
}

ASYNC_MONITOR_CALLBACK(usbHandleCompletedInputRequests) {
  UsbDevice *device = parameters->data;
  UsbEndpoint *endpoint;

  {
    int error = parameters->error;

    if (error) {
      logActionError(error, "USBFS output monitor");
      usbSetDeviceInputError(device, errno);
      return 0;
    }
  }

  while ((endpoint = usbReapURB(device, 0))) {
    UsbEndpointExtension *eptx = endpoint->extension;
    struct usbdevfs_urb *urb;

    while ((urb = dequeueItem(eptx->completedRequests))) {
      usbLogURB(urb, "reaped");

      {
        int handled = usbHandleCompletedInputRequest(endpoint, urb);
        if (!handled) usbSetEndpointInputError(endpoint, errno);

        free(urb);
        if (!handled) return 0;
      }
    }
  }

  if (errno == EAGAIN) return 1;
  usbSetDeviceInputError(device, errno);
  return 0;
}

static int
usbStartUsbfsMonitor (UsbDevice *device) {
  UsbDeviceExtension *devx = device->extension;

  if (devx->usbfsMonitorHandle) return 1;

  if (usbOpenUsbfsFile(devx)) {
    if (asyncMonitorFileOutput(&devx->usbfsMonitorHandle,
                               devx->usbfsFile,
                               usbHandleCompletedInputRequests,
                               device)) {
      logMessage(LOG_CATEGORY(USB_IO), "USBFS monitor started");
      return 1;
    } else {
      logMessage(LOG_ERR, "USBFS monitor error: %s: %s",
                 devx->host->usbfsPath, strerror(errno));
    }
  }

  return 0;
}

static int
usbPrepareInputEndpoint (UsbEndpoint *endpoint) {
  UsbDevice *device = endpoint->device;

  if (LINUX_USB_INPUT_PIPE_DISABLE) return 1;

  switch (USB_ENDPOINT_TRANSFER(endpoint->descriptor)) {
    case UsbEndpointTransfer_Bulk:
    case UsbEndpointTransfer_Interrupt:
      break;

    default:
      return 1;
  }

  if (usbMakeInputPipe(endpoint)) {
    int monitorStarted = LINUX_USB_INPUT_USE_SIGNAL_MONITOR?
                         usbStartSignalMonitor(endpoint):
                         usbStartUsbfsMonitor(device);

    if (monitorStarted) {
      return 1;
    } else {
      usbLogInputProblem(endpoint, "monitor not started");
    }

    usbDestroyInputPipe(endpoint);
  } else {
    usbLogInputProblem(endpoint, "pipe not created");
  }

  return 0;
}

int
usbAllocateEndpointExtension (UsbEndpoint *endpoint) {
  UsbEndpointExtension *eptx;

  if ((eptx = malloc(sizeof(*eptx)))) {
    memset(eptx, 0, sizeof(*eptx));
    usbInitializeSignalMonitor(eptx);

    if ((eptx->completedRequests = newQueue(NULL, NULL))) {
      switch (USB_ENDPOINT_DIRECTION(endpoint->descriptor)) {
        case UsbEndpointDirection_Input:
          endpoint->prepare = usbPrepareInputEndpoint;
          break;
      }

      endpoint->extension = eptx;
      return 1;
    } else {
      logSystemError("USB endpoint completed request queue allocate");
    }

    free(eptx);
  } else {
    logSystemError("USB endpoint extension allocate");
  }

  return 0;
}

void
usbDeallocateEndpointExtension (UsbEndpointExtension *eptx) {
  usbStopSignalMonitor(eptx);

  if (eptx->completedRequests) {
    deallocateQueue(eptx->completedRequests);
    eptx->completedRequests = NULL;
  }

  free(eptx);
}

void
usbDeallocateDeviceExtension (UsbDeviceExtension *devx) {
  usbStopUsbfsMonitor(devx);
  usbCloseUsbfsFile(devx);
  free(devx);
}

static void
usbDeallocateHostDevice (void *item, void *data) {
  UsbHostDevice *host = item;

  if (host->sysfsPath) free(host->sysfsPath);
  if (host->usbfsPath) free(host->usbfsPath);
  free(host);
}

typedef struct {
  UsbDeviceChooser *chooser;
  UsbChooseChannelData *data;
  UsbDevice *device;
} UsbTestHostDeviceData;

static int
usbTestHostDevice (void *item, void *data) {
  const UsbHostDevice *host = item;
  UsbTestHostDeviceData *test = data;
  UsbDeviceExtension *devx;

  if ((devx = malloc(sizeof(*devx)))) {
    memset(devx, 0, sizeof(*devx));
    devx->host = host;
    devx->usbfsFile = -1;
    usbInitializeUsbfsMonitor(devx);

    if ((test->device = usbTestDevice(devx, test->chooser, test->data))) return 1;

    usbDeallocateDeviceExtension(devx);
  } else {
    logMallocError();
  }

  return 0;
}

static char *
usbMakeSysfsPath (const char *usbfsPath) {
  const char *tail = usbfsPath + strlen(usbfsPath);

  {
    int count = 0;
    while (1) {
      if (tail == usbfsPath) return 0;
      if (!isPathDelimiter(*--tail)) continue;
      if (++count == 2) break;
    }
  }

  {
    unsigned int bus;
    unsigned int device;
    char extra;
    int count = sscanf(tail, "/%u/%u%c", &bus, &device, &extra);

    if (count == 2) {
      unsigned int minor = ((bus - 1) << 7) | (device - 1);

      static const char *const formats[] = {
        "/sys/dev/char/189:%4$u%1$n%2$u%3$u",
        "/sys/class/usb_device/usbdev%2$u.%3$u/device%1$n",
        "/sys/class/usb_endpoint/usbdev%2$u.%3$u_ep00/device%1$n",
        NULL
      };
      const char *const *format = formats;

      while (*format) {
        int length;
        char path[strlen(*format) + (2 * 0X10) + 1];

        snprintf(path, sizeof(path), *format, &length, bus, device, minor);
        path[length] = 0;

        if (access(path, F_OK) != -1) {
          char *sysfsPath = strdup(path);
          if (!sysfsPath) logSystemError("strdup");
          return sysfsPath;
        }

        format += 1;
      }
    }
  }

  return NULL;
}

static int
usbReadHostDeviceDescriptor (UsbHostDevice *host) {
  int ok = 0;
  int file = -1;
  int sysfs = 0;

  if (file == -1) {
    if (host->sysfsPath) {
      char *path;

      if ((path = makePath(host->sysfsPath, "descriptors"))) {
        if ((file = open(path, O_RDONLY)) != -1) {
          sysfs = 1;
        }

        free(path);
      }
    }
  }

  if (file == -1) {
    file = open(host->usbfsPath, O_RDONLY);
  }

  if (file != -1) {
    int count = read(file, &host->usbDescriptor, UsbDescriptorSize_Device);

    if (count == -1) {
      logSystemError("USB device descriptor read");
    } else if (count != UsbDescriptorSize_Device) {
      logMessage(LOG_ERR, "USB short device descriptor: %d", count);
    } else {
      ok = 1;

      if (!sysfs) {
        host->usbDescriptor.bcdUSB = getLittleEndian16(host->usbDescriptor.bcdUSB);
        host->usbDescriptor.idVendor = getLittleEndian16(host->usbDescriptor.idVendor);
        host->usbDescriptor.idProduct = getLittleEndian16(host->usbDescriptor.idProduct);
        host->usbDescriptor.bcdDevice = getLittleEndian16(host->usbDescriptor.bcdDevice);
      }
    }

    close(file);
  }

  return ok;
}

static int
usbAddHostDevice (const char *path) {
  int ok = 0;
  UsbHostDevice *host;

  if ((host = malloc(sizeof(*host)))) {
    if ((host->usbfsPath = strdup(path))) {
      host->sysfsPath = usbMakeSysfsPath(host->usbfsPath);

      if (!usbReadHostDeviceDescriptor(host)) {
        ok = 1;
      } else if (enqueueItem(usbHostDevices, host)) {
        return 1;
      }

      if (host->sysfsPath) free(host->sysfsPath);
      free(host->usbfsPath);
    } else {
      logSystemError("strdup");
    }

    free(host);
  } else {
    logMallocError();
  }

  return ok;
}

static int
usbAddHostDevices (const char *root) {
  int ok = 0;
  size_t rootLength = strlen(root);
  DIR *directory;

  if ((directory = opendir(root))) {
    struct dirent *entry;

    ok = 1;
    while ((entry = readdir(directory))) {
      size_t nameLength = strlen(entry->d_name);
      struct stat status;
      char path[rootLength + 1 + nameLength + 1];

      if (strspn(entry->d_name, "0123456789") != nameLength) continue;
      snprintf(path, sizeof(path), "%s/%s", root, entry->d_name);
      if (stat(path, &status) == -1) continue;

      if (S_ISDIR(status.st_mode)) {
        if (!usbAddHostDevices(path)) ok = 0;
      } else if (S_ISREG(status.st_mode) || S_ISCHR(status.st_mode)) {
        if (!usbAddHostDevice(path)) ok = 0;
      }

      if (!ok) break;
    }

    closedir(directory);
  }

  return ok;
}

typedef int (*FileSystemVerifier) (const char *path);

typedef struct {
  const char *path;
  FileSystemVerifier verify;
} FileSystemCandidate;

static int
usbVerifyFileSystem (const char *path, long type) {
  struct statfs status;
  if (statfs(path, &status) != -1) {
    if (status.f_type == type) return 1;
  }
  return 0;
}

static char *
usbGetFileSystem (const char *type, const FileSystemCandidate *candidates, MountPointTester test, FileSystemVerifier verify) {
  if (candidates) {
    const FileSystemCandidate *candidate = candidates;

    while (candidate->path) {
      logMessage(LOG_CATEGORY(USB_IO),
                 "USBFS root candidate: %s: %s",
                 type, candidate->path);

      if (candidate->verify(candidate->path)) {
        char *path = strdup(candidate->path);
        if (path) return path;
        logMallocError();
      }

      candidate += 1;
    }
  }

  if (test) {
    char *path = findMountPoint(test);
    if (path) return path;
  }

  if (verify) {
    char *directory = makeWritablePath(type);

    if (directory) {
      if (ensureDirectory(directory)) {
        if (verify(directory)) return directory;

        {
          const char *strings[] = {PACKAGE_TARNAME, "-", type};
          char *name = joinStrings(strings, ARRAY_COUNT(strings));
          if (makeMountPoint(directory, name, type)) return directory;
        }
      }

      free(directory);
    }
  }

  return NULL;
}

static int
usbVerifyDirectory (const char *path) {
  if (access(path, F_OK) != -1) return 1;
  return 0;
}

static int
usbVerifyUsbfs (const char *path) {
  return usbVerifyFileSystem(path, USBDEVICE_SUPER_MAGIC);
}

static int
usbTestUsbfs (const char *path, const char *type) {
  if ((strcmp(type, "usbdevfs") == 0) ||
      (strcmp(type, "usbfs") == 0)) {
    if (usbVerifyUsbfs(path)) {
      return 1;
    }
  }
  return 0;
}

static char *
usbGetUsbfs (void) {
  static const FileSystemCandidate usbfsCandidates[] = {
    {.path="/dev/bus/usb", .verify=usbVerifyDirectory},
    {.path="/proc/bus/usb", .verify=usbVerifyUsbfs},
    {.path=NULL, .verify=NULL}
  };

  return usbGetFileSystem("usbfs", usbfsCandidates, usbTestUsbfs, usbVerifyUsbfs);
}

UsbDevice *
usbFindDevice (UsbDeviceChooser *chooser, UsbChooseChannelData *data) {
  if (!usbHostDevices) {
    int ok = 0;

    if ((usbHostDevices = newQueue(usbDeallocateHostDevice, NULL))) {
      char *root;

      if ((root = usbGetUsbfs())) {
        logMessage(LOG_CATEGORY(USB_IO), "USBFS root: %s", root);
        if (usbAddHostDevices(root)) ok = 1;

        free(root);
      } else {
        logMessage(LOG_CATEGORY(USB_IO), "USBFS not mounted");
      }

      if (!ok) {
        deallocateQueue(usbHostDevices);
        usbHostDevices = NULL;
      }
    }
  }

  if (usbHostDevices) {
    UsbTestHostDeviceData test = {
      .chooser = chooser,
      .data = data,
      .device = NULL
    };

    if (processQueue(usbHostDevices, usbTestHostDevice, &test)) return test.device;
  }

  return NULL;
}

void
usbForgetDevices (void) {
  if (usbHostDevices) {
    deallocateQueue(usbHostDevices);
    usbHostDevices = NULL;
  }
}
