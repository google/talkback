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
#include "io_serial.h"

struct GioHandleStruct {
  SerialDevice *device;
  SerialParameters parameters;
};

static int
disconnectSerialResource (GioHandle *handle) {
  serialCloseDevice(handle->device);
  free(handle);
  return 1;
}

static const char *
makeSerialResourceIdentifier (GioHandle *handle, char *buffer, size_t size) {
  return serialMakeDeviceIdentifier(handle->device, buffer, size);
}

static void *
getSerialResourceObject (GioHandle *handle) {
  return handle->device;
}

static ssize_t
writeSerialData (GioHandle *handle, const void *data, size_t size, int timeout) {
  return serialWriteData(handle->device, data, size);
}

static int
awaitSerialInput (GioHandle *handle, int timeout) {
  return serialAwaitInput(handle->device, timeout);
}

static ssize_t
readSerialData (
  GioHandle *handle, void *buffer, size_t size,
  int initialTimeout, int subsequentTimeout
) {
  return serialReadData(handle->device, buffer, size,
                        initialTimeout, subsequentTimeout);
}

static int
monitorSerialInput (GioHandle *handle, AsyncMonitorCallback *callback, void *data) {
  return serialMonitorInput(handle->device, callback, data);
}

static int
reconfigureSerialResource (GioHandle *handle, const SerialParameters *parameters) {
  int ok = serialSetParameters(handle->device, parameters);

  if (ok) handle->parameters = *parameters;
  return ok;
}

static const GioHandleMethods gioSerialMethods = {
  .disconnectResource = disconnectSerialResource,

  .makeResourceIdentifier = makeSerialResourceIdentifier,
  .getResourceObject = getSerialResourceObject,

  .writeData = writeSerialData,
  .awaitInput = awaitSerialInput,
  .readData = readSerialData,
  .monitorInput = monitorSerialInput,
  .reconfigureResource = reconfigureSerialResource,
};

static int
testSerialIdentifier (const char **identifier) {
  return isSerialDeviceIdentifier(identifier);
}

static const GioPublicProperties gioPublicProperties_serial = {
  .testIdentifier = testSerialIdentifier,

  .type = {
    .name = "serial",
    .identifier = GIO_TYPE_SERIAL
  }
};

static int
isSerialSupported (const GioDescriptor *descriptor) {
  return descriptor->serial.parameters != NULL;
}

static const GioOptions *
getSerialOptions (const GioDescriptor *descriptor) {
  return &descriptor->serial.options;
}

static const GioHandleMethods *
getSerialMethods (void) {
  return &gioSerialMethods;
}

static GioHandle *
connectSerialResource (
  const char *identifier,
  const GioDescriptor *descriptor
) {
  GioHandle *handle = malloc(sizeof(*handle));

  if (handle) {
    memset(handle, 0, sizeof(*handle));

    if ((handle->device = serialOpenDevice(identifier))) {
      if (serialSetParameters(handle->device, descriptor->serial.parameters)) {
        handle->parameters = *descriptor->serial.parameters;
        return handle;
      }

      serialCloseDevice(handle->device);
    }

    free(handle);
  } else {
    logMallocError();
  }

  return NULL;
}

static int
prepareSerialEndpoint (GioEndpoint *endpoint) {
  gioSetBytesPerSecond(endpoint, &endpoint->handle->parameters);
  return 1;
}

static const GioPrivateProperties gioPrivateProperties_serial = {
  .isSupported = isSerialSupported,

  .getOptions = getSerialOptions,
  .getHandleMethods = getSerialMethods,

  .connectResource = connectSerialResource,
  .prepareEndpoint = prepareSerialEndpoint
};

const GioProperties gioProperties_serial = {
  .public = &gioPublicProperties_serial,
  .private = &gioPrivateProperties_serial
};
