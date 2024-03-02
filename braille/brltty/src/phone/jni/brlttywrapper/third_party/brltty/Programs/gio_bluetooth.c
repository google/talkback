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
#include "strfmt.h"
#include "io_generic.h"
#include "gio_internal.h"
#include "io_bluetooth.h"
#include "brl.h"

struct GioHandleStruct {
  BluetoothConnection *connection;
  GioEndpoint *hidEndpoint;
};

static int
disconnectBluetoothResource (GioHandle *handle) {
  if (handle->connection) bthCloseConnection(handle->connection);
  if (handle->hidEndpoint) gioDisconnectResource(handle->hidEndpoint);
  free(handle);
  return 1;
}

static GioEndpoint *
getBluetoothChainedEndpoint (GioHandle *handle) {
  return handle->hidEndpoint;
}

static const char *
makeBluetoothResourceIdentifier (GioHandle *handle, char *buffer, size_t size) {
  return bthMakeConnectionIdentifier(handle->connection, buffer, size);
}

static char *
getBluetoothResourceName (GioHandle *handle, int timeout) {
  const char *name = bthGetNameOfDevice(handle->connection, timeout);

  if (name) {
    char *copy = strdup(name);
    if (copy) return copy;
    logMallocError();
  }

  return NULL;
}

static void *
getBluetoothResourceObject (GioHandle *handle) {
  return handle->connection;
}

static ssize_t
writeBluetoothData (GioHandle *handle, const void *data, size_t size, int timeout) {
  return bthWriteData(handle->connection, data, size);
}

static int
awaitBluetoothInput (GioHandle *handle, int timeout) {
  return bthAwaitInput(handle->connection, timeout);
}

static ssize_t
readBluetoothData (
  GioHandle *handle, void *buffer, size_t size,
  int initialTimeout, int subsequentTimeout
) {
  return bthReadData(handle->connection, buffer, size,
                     initialTimeout, subsequentTimeout);
}

static int
monitorBluetoothInput (GioHandle *handle, AsyncMonitorCallback *callback, void *data) {
  return bthMonitorInput(handle->connection, callback, data);
}

static const GioHandleMethods gioBluetoothMethods = {
  .disconnectResource = disconnectBluetoothResource,
  .getChainedEndpoint = getBluetoothChainedEndpoint,

  .makeResourceIdentifier = makeBluetoothResourceIdentifier,
  .getResourceName = getBluetoothResourceName,
  .getResourceObject = getBluetoothResourceObject,

  .writeData = writeBluetoothData,
  .awaitInput = awaitBluetoothInput,
  .readData = readBluetoothData,
  .monitorInput = monitorBluetoothInput,
};

static int
testBluetoothIdentifier (const char **identifier) {
  return isBluetoothDeviceIdentifier(identifier);
}

static const GioPublicProperties gioPublicProperties_bluetooth = {
  .testIdentifier = testBluetoothIdentifier,

  .type = {
    .name = "Bluetooth",
    .identifier = GIO_TYPE_BLUETOOTH
  }
};

static int
isBluetoothSupported (const GioDescriptor *descriptor) {
  return descriptor->bluetooth.channelNumber || descriptor->bluetooth.discoverChannel;
}

static const GioOptions *
getBluetoothOptions (const GioDescriptor *descriptor) {
  return &descriptor->bluetooth.options;
}

static const GioHandleMethods *
getBluetoothMethods (void) {
  return &gioBluetoothMethods;
}

static GioEndpoint *
getHidEndpoint (uint64_t address, const GioDescriptor *descriptor) {
  char identifier[0X40];
  STR_BEGIN(identifier, sizeof(identifier));
  STR_PRINTF("hid:address=");
  STR_FORMAT(bthFormatAddress, address);
  STR_END;

  return gioConnectResource(identifier, descriptor);
}

static GioHandle *
connectBluetoothResource (
  const char *identifier,
  const GioDescriptor *descriptor
) {
  GioHandle *handle = malloc(sizeof(*handle));

  if (handle) {
    memset(handle, 0, sizeof(*handle));

    BluetoothConnectionRequest request;
    bthInitializeConnectionRequest(&request);

    request.driver = braille->definition.code;
    request.channel = descriptor->bluetooth.channelNumber;
    request.discover = descriptor->bluetooth.discoverChannel;

    if (bthApplyParameters(&request, identifier)) {
      if (gioIsHidSupported(descriptor)) {
        GioEndpoint *hidEndpoint = getHidEndpoint(request.address, descriptor);

        if (hidEndpoint) {
          handle->hidEndpoint = hidEndpoint;
          return handle;
        }
      }

      if ((handle->connection = bthOpenConnection(&request))) {
        return handle;
      }
    }

    free(handle);
  } else {
    logMallocError();
  }

  return NULL;
}

static const GioPrivateProperties gioPrivateProperties_bluetooth = {
  .isSupported = isBluetoothSupported,

  .getOptions = getBluetoothOptions,
  .getHandleMethods = getBluetoothMethods,

  .connectResource = connectBluetoothResource
};

const GioProperties gioProperties_bluetooth = {
  .public = &gioPublicProperties_bluetooth,
  .private = &gioPrivateProperties_bluetooth
};
