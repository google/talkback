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

#include "log.h"
#include "io_generic.h"
#include "gio_internal.h"
#include "io_hid.h"

struct GioHandleStruct {
  HidDevice *device;
  const HidModelEntry *model;
};

static int
disconnectHidResource (GioHandle *handle) {
  hidCloseDevice(handle->device);
  free(handle);
  return 1;
}

static const char *
makeHidResourceIdentifier (GioHandle *handle, char *buffer, size_t size) {
  return hidMakeDeviceIdentifier(handle->device, buffer, size);
}

static char *
getHidResourceName (GioHandle *handle, int timeout) {
  const char *name = hidGetDeviceName(handle->device);

  if (name) {
    char *copy = strdup(name);
    if (copy) return copy;
    logMallocError();
  }

  return NULL;
}

static void *
getHidResourceObject (GioHandle *handle) {
  return handle->device;
}

static ssize_t
writeHidData (GioHandle *handle, const void *data, size_t size, int timeout) {
  return hidWriteData(handle->device, data, size);
}

static int
awaitHidInput (GioHandle *handle, int timeout) {
  return hidAwaitInput(handle->device, timeout);
}

static ssize_t
readHidData (
  GioHandle *handle, void *buffer, size_t size,
  int initialTimeout, int subsequentTimeout
) {
  return hidReadData(handle->device, buffer, size,
                     initialTimeout, subsequentTimeout);
}

static int
monitorHidInput (GioHandle *handle, AsyncMonitorCallback *callback, void *data) {
  return hidMonitorInput(handle->device, callback, data);
}

int
getHidReportSize (
  GioHandle *handle, HidReportIdentifier identifier,
  HidReportSize *size, int timeout
) {
  return hidGetReportSize(handle->device, identifier, size);
}

static HidItemsDescriptor *
getHidDescriptor (GioHandle *handle
) {
  return hidGetItems(handle->device);
}

static ssize_t
getHidReport (
  GioHandle *handle, HidReportIdentifier identifier,
  unsigned char *buffer, size_t size, int timeout
) {
  buffer[0] = identifier;
  return hidGetReport(handle->device, buffer, size);
}

static ssize_t
setHidReport (
  GioHandle *handle, HidReportIdentifier identifier,
  const unsigned char *data, size_t size, int timeout
) {
  unsigned char buffer[1 + size];

  if (!identifier) {
    buffer[0] = identifier;
    memcpy(&buffer[1], data, size);

    data = buffer;
    size += 1;
  }

  return hidSetReport(handle->device, data, size);
}

static ssize_t
getHidFeature (
  GioHandle *handle, HidReportIdentifier identifier,
  unsigned char *buffer, size_t size, int timeout
) {
  buffer[0] = identifier;
  return hidGetFeature(handle->device, buffer, size);
}

static ssize_t
setHidFeature (
  GioHandle *handle, HidReportIdentifier identifier,
  const unsigned char *data, size_t size, int timeout
) {
  unsigned char buffer[1 + size];

  if (!identifier) {
    buffer[0] = identifier;
    memcpy(&buffer[1], data, size);

    data = buffer;
    size += 1;
  }

  return hidSetFeature(handle->device, data, size);
}

static const GioHandleMethods gioHidMethods = {
  .disconnectResource = disconnectHidResource,

  .makeResourceIdentifier = makeHidResourceIdentifier,
  .getResourceName = getHidResourceName,
  .getResourceObject = getHidResourceObject,

  .writeData = writeHidData,
  .awaitInput = awaitHidInput,
  .readData = readHidData,
  .monitorInput = monitorHidInput,

  .getHidReportSize = getHidReportSize,
  .getHidDescriptor = getHidDescriptor,
  .getHidReport = getHidReport,
  .setHidReport = setHidReport,
  .getHidFeature = getHidFeature,
  .setHidFeature = setHidFeature,
};

static int
testHidIdentifier (const char **identifier) {
  return isHidDeviceIdentifier(identifier);
}

static const GioPublicProperties gioPublicProperties_hid = {
  .testIdentifier = testHidIdentifier,

  .type = {
    .name = "HID",
    .identifier = GIO_TYPE_HID
  }
};

static int
isHidSupported (const GioDescriptor *descriptor) {
  return !!descriptor->hid.modelTable;
}

static const GioOptions *
getHidOptions (const GioDescriptor *descriptor) {
  return &descriptor->hid.options;
}

static const GioHandleMethods *
getHidMethods (void) {
  return &gioHidMethods;
}

static const HidModelEntry *
getHidModelEntry (HidDevice *device, const HidModelEntry *model) {
  if (model) {
    HidDeviceIdentifier vendor;
    HidDeviceIdentifier product;

    if (hidGetDeviceIdentifiers(device, &vendor, &product)) {
      for (; (model->vendor || model->product || model->name); model+=1) {
        if (model->vendor) {
          if (model->vendor != vendor) {
            continue;
          }
        }

        if (model->product) {
          if (model->product != product) {
            continue;
          }
        }

        if (model->name) {
          const char *name = hidGetDeviceName(device);
          if (!name) continue;

          size_t length = strlen(model->name);
          if (length > strlen(name)) continue;
          if (strncasecmp(name, model->name, length) != 0) continue;
        }

        logMessage(LOG_CATEGORY(HID_IO), "model found: %s", model->name);
        return model;
      }
    }
  }

  return NULL;
}

static GioHandle *
connectHidResource (
  const char *identifier,
  const GioDescriptor *descriptor
) {
  GioHandle *handle = malloc(sizeof(*handle));

  if (handle) {
    memset(handle, 0, sizeof(*handle));

    if (hidOpenDeviceWithParameters(&handle->device, identifier)) {
      if (handle->device) {
        handle->model = getHidModelEntry(
          handle->device,
          descriptor->hid.modelTable
        );

        if (handle->model) return handle;
      }
    }

    free(handle);
  } else {
    logMallocError();
  }

  return NULL;
}

static int
prepareHidEndpoint (GioEndpoint *endpoint) {
  gioSetApplicationData(endpoint, endpoint->handle->model->data);
  return 1;
}

static const GioPrivateProperties gioPrivateProperties_hid = {
  .isSupported = isHidSupported,

  .getOptions = getHidOptions,
  .getHandleMethods = getHidMethods,

  .connectResource = connectHidResource,
  .prepareEndpoint = prepareHidEndpoint,
};

const GioProperties gioProperties_hid = {
  .public = &gioPublicProperties_hid,
  .private = &gioPrivateProperties_hid
};
