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
#include "ezusb.h"
#include "ihex.h"
#include "timing.h"
#include "core.h"

#define EZUSB_REQUEST_TIMEOUT 1000
#define EZUSB_CPUCS_DELAY 10

int
ezusbWriteData (UsbDevice *device, EzusbAction action, IhexAddress address, const unsigned char *data, size_t length) {
  ssize_t result = usbControlWrite(
    device, EZUSB_REQUEST_RECIPIENT, EZUSB_REQUEST_TYPE,
    action, address, EZUSB_REQUEST_INDEX,
    data, length, EZUSB_REQUEST_TIMEOUT
  );

  if (result != -1) {
    size_t count = result;
    if (count == length) return 1;

    logMessage(LOG_ERR,
      "firmware write length mismatch:"
      " Address:%04"PRIX16
      " Expect:%"PRIsize
      " Actual:%"PRIsize,
      address, length, count
    );
  }

  return 0;
}

int
ezusbReadData (UsbDevice *device, EzusbAction action, IhexAddress address, unsigned char *buffer, size_t size) {
  ssize_t result = usbControlRead(
    device, EZUSB_REQUEST_RECIPIENT, EZUSB_REQUEST_TYPE,
    action, address, EZUSB_REQUEST_INDEX,
    buffer, size, EZUSB_REQUEST_TIMEOUT
  );

  if (result != -1) {
    size_t count = result;
    if (count == size) return 1;

    logMessage(LOG_ERR,
      "firmware read length mismatch:"
      " Address:%04"PRIX16
      " Expect:%"PRIsize
      " Actual:%"PRIsize,
      address, size, count
    );
  }

  return 0;
}

int
ezusbVerifyData (UsbDevice *device, EzusbAction action, IhexAddress address, const unsigned char *data, size_t length) {
  unsigned char buffer[length];

  {
    int ok = ezusbReadData(
      device, action, address, buffer, length
    );

    if (!ok) return 0;
  }

  if (memcmp(buffer, data, length) != 0) {
    logMessage(LOG_ERR,
      "firmware data verification mismatch:"
      " Address:%04"PRIX16,
      address
    );

    logBytes(LOG_DEBUG, "expect", data, length);
    logBytes(LOG_DEBUG, "actual", buffer, length);
    return 0;
  }

  return 1;
}

int
ezusbWriteCPUCS (UsbDevice *device, uint8_t state) {
  int ok = ezusbWriteData(
    device, EZUSB_ACTION_RW_INTERNAL,
    EZUSB_CPUCS_ADDRESS, &state, sizeof(state)
  );

  if (ok) approximateDelay(EZUSB_CPUCS_DELAY);
  return ok;
}

int
ezusbStopCPU (UsbDevice *device) {
  return ezusbWriteCPUCS(device, EZUSB_CPUCS_STOP);
}

int
ezusbResetCPU (UsbDevice *device) {
  return ezusbWriteCPUCS(device, EZUSB_CPUCS_RESET);
}

int
ezusbProcessBlob (const char *name, IhexRecordHandler *handler, void *data) {
  int ok = 0;
  char *path = ihexMakePath(opt_driversDirectory, name);

  if (path) {
    if (ihexProcessFile(path, handler, data)) {
      ok = 1;
    }

    free(path);
  }

  return ok;
}

typedef struct {
  UsbDevice *device;
  EzusbAction action;
} EzusbRecordProcessingData;

static int
ezusbInstallData (const IhexParsedRecord *record, void *data) {
  const EzusbRecordProcessingData *rpd = data;

  UsbDevice *const device = rpd->device;
  const EzusbAction action = rpd->action;

  const IhexAddress address = record->address;
  const IhexByte *const bytes = record->data;
  const IhexCount count = record->count;

  if (!ezusbWriteData(device, action, address, bytes, count)) return 0;
  if (!ezusbVerifyData(device, action, address, bytes, count)) return 0;

  return 1;
}

int
ezusbInstallBlob (UsbDevice *device, const char *name, EzusbAction action) {
  EzusbRecordProcessingData rpd = {
    .device = device,
    .action = action
  };

  return ezusbProcessBlob(name, ezusbInstallData, &rpd);
}
