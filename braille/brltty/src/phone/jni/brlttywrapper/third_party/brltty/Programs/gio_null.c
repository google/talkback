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
#include "parse.h"

struct GioHandleStruct {
  int place_holder;
};

static int
disconnectNullResource (GioHandle *handle) {
  free(handle);
  return 1;
}

static const char *
makeNullResourceIdentifier (GioHandle *handle, char *buffer, size_t size) {
  STR_BEGIN(buffer, size);
  STR_PRINTF("%s%c", "null", PARAMETER_QUALIFIER_CHARACTER);
  STR_END;
  return buffer;
}

static ssize_t
writeNullData (GioHandle *handle, const void *data, size_t size, int timeout) {
  return size;
}

static int
awaitNullInput (GioHandle *handle, int timeout) {
  return 1;
}

static ssize_t
readNullData (
  GioHandle *handle, void *buffer, size_t size,
  int initialTimeout, int subsequentTimeout
) {
  return 0;
}

static int
monitorNullInput (GioHandle *handle, AsyncMonitorCallback *callback, void *data) {
  return 1;
}

static const GioHandleMethods gioNullMethods = {
  .disconnectResource = disconnectNullResource,

  .makeResourceIdentifier = makeNullResourceIdentifier,

  .writeData = writeNullData,
  .awaitInput = awaitNullInput,
  .readData = readNullData,
  .monitorInput = monitorNullInput
};

static int
testNullIdentifier (const char **identifier) {
  return hasQualifier(identifier, "null");
}

static const GioPublicProperties gioPublicProperties_null = {
  .testIdentifier = testNullIdentifier,

  .type = {
    .name = "null",
    .identifier = GIO_TYPE_NULL
  }
};

static int
isNullSupported (const GioDescriptor *descriptor) {
  return 1;
}

static const GioOptions *
getNullOptions (const GioDescriptor *descriptor) {
  return &descriptor->null.options;
}

static const GioHandleMethods *
getNullMethods (void) {
  return &gioNullMethods;
}

static GioHandle *
connectNullResource (
  const char *identifier,
  const GioDescriptor *descriptor
) {
  GioHandle *handle = malloc(sizeof(*handle));

  if (handle) {
    memset(handle, 0, sizeof(*handle));

    return handle;
  } else {
    logMallocError();
  }

  return NULL;
}

static const GioPrivateProperties gioPrivateProperties_null = {
  .isSupported = isNullSupported,

  .getOptions = getNullOptions,
  .getHandleMethods = getNullMethods,

  .connectResource = connectNullResource
};

const GioProperties gioProperties_null = {
  .public = &gioPublicProperties_null,
  .private = &gioPrivateProperties_null
};
