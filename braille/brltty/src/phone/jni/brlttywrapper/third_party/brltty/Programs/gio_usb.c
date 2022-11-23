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

#include <string.h>
#include <errno.h>

#ifdef HAVE_POSIX_THREADS
#ifdef __MINGW32__
#include "win_pthread.h"
#else /* __MINGW32__ */
#include <pthread.h>
#endif /* __MINGW32__ */
#endif /* HAVE_POSIX_THREADS */

#include "log.h"
#include "parameters.h"
#include "async_wait.h"
#include "io_generic.h"
#include "gio_internal.h"
#include "io_usb.h"

struct GioHandleStruct {
  UsbChannel *channel;
  GioUsbConnectionProperties properties;
};

static int
disconnectUsbResource (GioHandle *handle) {
  usbCloseChannel(handle->channel);
  free(handle);
  return 1;
}

static char *
getUsbResourceName (GioHandle *handle, int timeout) {
  UsbChannel *channel = handle->channel;

  return usbGetProduct(channel->device, timeout);
}

static ssize_t
writeUsbData (GioHandle *handle, const void *data, size_t size, int timeout) {
  UsbChannel *channel = handle->channel;

  {
    GioUsbWriteDataMethod *method = handle->properties.writeData;

    if (method) {
      return method(channel->device, channel->definition, data, size, timeout);
    }
  }

  if (channel->definition->outputEndpoint) {
    return usbWriteData(channel->device,
                        channel->definition->outputEndpoint,
                        data, size, timeout);
  }

  {
    const UsbSerialOperations *serial = usbGetSerialOperations(channel->device);

    if (serial) {
      if (serial->writeData) {
        return serial->writeData(channel->device, data, size);
      }
    }
  }

  errno = ENOSYS;
  return -1;
}

static int
awaitUsbInput (GioHandle *handle, int timeout) {
  UsbChannel *channel = handle->channel;

  {
    GioUsbAwaitInputMethod *method = handle->properties.awaitInput;

    if (method) {
      return method(channel->device, channel->definition, timeout);
    }
  }

  {
    unsigned char endpoint = channel->definition->inputEndpoint;

    if (!endpoint) {
      asyncWait(timeout);
      return 0;
    }

    return usbAwaitInput(channel->device, endpoint, timeout);
  }
}

static ssize_t
readUsbData (
  GioHandle *handle, void *buffer, size_t size,
  int initialTimeout, int subsequentTimeout
) {
  UsbChannel *channel = handle->channel;

  {
    GioUsbReadDataMethod *method = handle->properties.readData;

    if (method) {
      return method(channel->device, channel->definition, buffer, size, initialTimeout, subsequentTimeout);
    }
  }

  {
    unsigned char endpoint = channel->definition->inputEndpoint;

    if (!endpoint) {
      errno = EAGAIN;
      return -1;
    }

    return usbReadData(channel->device, endpoint,
                       buffer, size, initialTimeout, subsequentTimeout);
  }
}

static int
reconfigureUsbResource (GioHandle *handle, const SerialParameters *parameters) {
  UsbChannel *channel = handle->channel;

  return usbSetSerialParameters(channel->device, parameters);
}

static ssize_t
tellUsbResource (
  GioHandle *handle, uint8_t recipient, uint8_t type,
  uint8_t request, uint16_t value, uint16_t index,
  const void *data, uint16_t size, int timeout
) {
  UsbChannel *channel = handle->channel;

  return usbControlWrite(channel->device, recipient, type,
                         request, value, index, data, size, timeout);
}

static ssize_t
askUsbResource (
  GioHandle *handle, uint8_t recipient, uint8_t type,
  uint8_t request, uint16_t value, uint16_t index,
  void *buffer, uint16_t size, int timeout
) {
  UsbChannel *channel = handle->channel;

  return usbControlRead(channel->device, recipient, type,
                        request, value, index, buffer, size, timeout);
}

static int
getUsbHidReportItems (GioHandle *handle, GioHidReportItemsData *items, int timeout) {
  UsbChannel *channel = handle->channel;
  unsigned char *address;
  ssize_t result = usbHidGetItems(channel->device,
                                  channel->definition->interface, 0,
                                  &address, timeout);

  if (!address) return 0;
  items->address = address;
  items->size = result;
  return 1;
}

static size_t
getUsbHidReportSize (const GioHidReportItemsData *items, unsigned char report) {
  size_t size;
  if (usbHidGetReportSize(items->address, items->size, report, &size)) return size;
  errno = ENOSYS;
  return 0;
}

static ssize_t
setUsbHidReport (
  GioHandle *handle, unsigned char report,
  const void *data, uint16_t size, int timeout
) {
  UsbChannel *channel = handle->channel;

  return usbHidSetReport(channel->device, channel->definition->interface,
                         report, data, size, timeout);
}

static ssize_t
getUsbHidReport (
  GioHandle *handle, unsigned char report,
  void *buffer, uint16_t size, int timeout
) {
  UsbChannel *channel = handle->channel;

  return usbHidGetReport(channel->device, channel->definition->interface,
                         report, buffer, size, timeout);
}

static ssize_t
setUsbHidFeature (
  GioHandle *handle, unsigned char report,
  const void *data, uint16_t size, int timeout
) {
  UsbChannel *channel = handle->channel;

  return usbHidSetFeature(channel->device, channel->definition->interface,
                          report, data, size, timeout);
}

static ssize_t
getUsbHidFeature (
  GioHandle *handle, unsigned char report,
  void *buffer, uint16_t size, int timeout
) {
  UsbChannel *channel = handle->channel;

  return usbHidGetFeature(channel->device, channel->definition->interface,
                          report, buffer, size, timeout);
}

static int
monitorUsbInput (GioHandle *handle, AsyncMonitorCallback *callback, void *data) {
  if (!GIO_USB_INPUT_MONITOR_DISABLE) {
    UsbChannel *channel = handle->channel;
    unsigned char endpoint = channel->definition->inputEndpoint;

    if (!endpoint) return 1;
    return usbMonitorInputEndpoint(channel->device, endpoint, callback, data);
  }

  return 0;
}

static void *
getUsbResourceObject (GioHandle *handle) {
  return handle->channel;
}

static const GioMethods gioUsbMethods = {
  .disconnectResource = disconnectUsbResource,

  .getResourceName = getUsbResourceName,

  .writeData = writeUsbData,
  .awaitInput = awaitUsbInput,
  .readData = readUsbData,

  .reconfigureResource = reconfigureUsbResource,

  .tellResource = tellUsbResource,
  .askResource = askUsbResource,

  .getHidReportItems = getUsbHidReportItems,
  .getHidReportSize = getUsbHidReportSize,

  .setHidReport = setUsbHidReport,
  .getHidReport = getUsbHidReport,

  .setHidFeature = setUsbHidFeature,
  .getHidFeature = getUsbHidFeature,

  .monitorInput = monitorUsbInput,

  .getResourceObject = getUsbResourceObject
};

static int
testUsbIdentifier (const char **identifier) {
  return isUsbDeviceIdentifier(identifier);
}

static const GioPublicProperties gioPublicProperties_usb = {
  .testIdentifier = testUsbIdentifier,

  .type = {
    .name = "USB",
    .identifier = GIO_TYPE_USB
  }
};

static int
isUsbSupported (const GioDescriptor *descriptor) {
  return descriptor->usb.channelDefinitions != NULL;
}

static const GioOptions *
getUsbOptions (const GioDescriptor *descriptor) {
  return &descriptor->usb.options;
}

static const GioMethods *
getUsbMethods (void) {
  return &gioUsbMethods;
}

static GioHandle *
connectUsbResource (
  const char *identifier,
  const GioDescriptor *descriptor
) {
  GioHandle *handle = malloc(sizeof(*handle));

  if (handle) {
    memset(handle, 0, sizeof(*handle));

    if ((handle->channel = usbOpenChannel(descriptor->usb.channelDefinitions, identifier))) {
      const UsbChannel *channel = handle->channel;
      const UsbChannelDefinition *definition = channel->definition;
      GioUsbConnectionProperties *properties = &handle->properties;

      memset(properties, 0, sizeof(*properties));
      properties->applicationData = definition->data;
      properties->writeData = NULL;
      properties->awaitInput = NULL;
      properties->readData = NULL;
      properties->inputFilter = NULL;

      {
        GioUsbSetConnectionPropertiesMethod *method = descriptor->usb.setConnectionProperties;

        if (method) method(properties, definition);
      }

      if (!properties->inputFilter ||
          usbAddInputFilter(channel->device, properties->inputFilter)) {
        return handle;
      }

      usbCloseChannel(handle->channel);
    }

    free(handle);
  } else {
    logMallocError();
  }

  return NULL;
}

static int
prepareUsbEndpoint (GioEndpoint *endpoint) {
  GioHandle *handle = endpoint->handle;
  UsbChannel *channel = handle->channel;

  if (!endpoint->options.applicationData) {
    endpoint->options.applicationData = handle->properties.applicationData;
  }

  {
    const SerialParameters *parameters = channel->definition->serial;

    if (parameters) {
      gioSetBytesPerSecond(endpoint, parameters);
    }
  }

  return 1;
}

static const GioPrivateProperties gioPrivateProperties_usb = {
  .isSupported = isUsbSupported,

  .getOptions = getUsbOptions,
  .getMethods = getUsbMethods,

  .connectResource = connectUsbResource,
  .prepareEndpoint = prepareUsbEndpoint
};

const GioProperties gioProperties_usb = {
  .public = &gioPublicProperties_usb,
  .private = &gioPrivateProperties_usb
};
