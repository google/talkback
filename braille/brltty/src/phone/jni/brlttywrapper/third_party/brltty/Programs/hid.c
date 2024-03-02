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

#include "log.h"
#include "strfmt.h"
#include "io_hid.h"
#include "hid_internal.h"
#include "parse.h"
#include "device.h"

typedef enum {
  HID_PARM_ADDRESS,
  HID_PARM_NAME,

  HID_PARM_MANUFACTURER,
  HID_PARM_DESCRIPTION,
  HID_PARM_SERIAL_NUMBER,

  HID_PARM_VENDOR,
  HID_PARM_PRODUCT,
} HidDeviceParameter;

static const char *const hidDeviceParameterNames[] = {
  "address",
  "name",

  "manufacturer",
  "description",
  "serialNumber",

  "vendor",
  "product",

  NULL
};

static char **
hidGetDeviceParameters (const char *string) {
  if (!string) string = "";
  return getDeviceParameters(hidDeviceParameterNames, string);
}

typedef struct {
  STR_DECLARE_FORMATTER((*extendDeviceIdentifier), HidDevice *device);
} HidBusMethods;

static
STR_BEGIN_FORMATTER(hidExtendUSBDeviceIdentifier, HidDevice *device)
  {
    const char *serialNumber = hidGetDeviceAddress(device);

    if (serialNumber && *serialNumber) {
      STR_PRINTF(
        "%s%c%s%c",
        hidDeviceParameterNames[HID_PARM_SERIAL_NUMBER],
        PARAMETER_ASSIGNMENT_CHARACTER,
        serialNumber, DEVICE_PARAMETER_SEPARATOR
      );
    }
  }
STR_END_FORMATTER

static const HidBusMethods hidUSBBusMethods = {
  .extendDeviceIdentifier = hidExtendUSBDeviceIdentifier,
};

static
STR_BEGIN_FORMATTER(hidExtendBluetoothDeviceIdentifier, HidDevice *device)
  {
    const char *macAddress = hidGetDeviceAddress(device);

    if (macAddress && *macAddress) {
      STR_PRINTF(
        "%s%c%s%c",
        hidDeviceParameterNames[HID_PARM_ADDRESS],
        PARAMETER_ASSIGNMENT_CHARACTER,
        macAddress, DEVICE_PARAMETER_SEPARATOR
      );
    }
  }
STR_END_FORMATTER

static const HidBusMethods hidBluetoothBusMethods= {
  .extendDeviceIdentifier = hidExtendBluetoothDeviceIdentifier,
};

void
hidInitializeUSBFilter (HidUSBFilter *filter) {
  memset(filter, 0, sizeof(*filter));
}

void
hidInitializeBluetoothFilter (HidBluetoothFilter *filter) {
  memset(filter, 0, sizeof(*filter));
}

int
hidParseDeviceIdentifier (HidDeviceIdentifier *identifier, const char *string) {
  if (!string) return 0;
  if (!*string) return 0;
  if (strlen(string) > 4) return 0;

  char *end;
  long unsigned int value = strtoul(string, &end, 0X10);
  if (*end) return 0;
  if (value > UINT16_MAX) return 0;

  *identifier = value;
  return 1;
}

int
hidMatchString (const char *actualString, const char *testString) {
  size_t testLength = strlen(testString);
  if (testLength > strlen(actualString)) return 0;
  return strncasecmp(actualString, testString, testLength) == 0;
}

struct HidDeviceStruct {
  HidHandle *handle;

  const HidBusMethods *busMethods;
  const HidHandleMethods *handleMethods;
};

static void
hidDestroyHandle (HidHandle *handle) {
  HidDestroyHandleMethod *method = hidPackageDescriptor.handleMethods->destroyHandle;

  if (method) {
    method(handle);
  } else {
    logUnsupportedOperation("hidCloseDevice");
    errno = ENOSYS;
  }
}

static HidDevice *
hidNewDevice (HidHandle *handle, const HidBusMethods *busMethods) {
  if (handle) {
    HidDevice *device;

    if ((device = malloc(sizeof(*device)))) {
      memset(device, 0, sizeof(*device));
      device->handle = handle;
      device->handleMethods = hidPackageDescriptor.handleMethods;
      device->busMethods = busMethods;
      return device;
    } else {
      logMallocError();
    }

    hidDestroyHandle(handle);
  }

  return NULL;
}

HidDevice *
hidOpenUSBDevice (const HidUSBFilter *filter) {
  HidNewUSBHandleMethod *method = hidPackageDescriptor.newUSBHandle;

  if (!method) {
    logUnsupportedOperation("hidOpenUSBDevice");
    errno = ENOSYS;
    return NULL;
  }

  return hidNewDevice(method(filter), &hidUSBBusMethods);
}

HidDevice *
hidOpenBluetoothDevice (const HidBluetoothFilter *filter) {
  HidNewBluetoothHandleMethod *method = hidPackageDescriptor.newBluetoothHandle;

  if (!method) {
    logUnsupportedOperation("hidOpenBluetoothDevice");
    errno = ENOSYS;
    return NULL;
  }

  return hidNewDevice(method(filter), &hidBluetoothBusMethods);
}

void
hidInitializeFilter (HidFilter *filter) {
  memset(filter, 0, sizeof(*filter));
}

int
hidSetFilterIdentifiers (
  HidFilter *filter, const char *vendor, const char *product
) {
  typedef struct {
    const char *name;
    const char *operand;
    HidDeviceIdentifier *identifier;
  } IdentifierEntry;

  const IdentifierEntry identifierTable[] = {
    { .name = "vendor",
      .operand = vendor,
      .identifier = &filter->common.vendorIdentifier,
    },

    { .name = "product",
      .operand = product,
      .identifier = &filter->common.productIdentifier,
    },
  };

  const IdentifierEntry *cur = identifierTable;
  const IdentifierEntry *end = cur + ARRAY_COUNT(identifierTable);

  while (cur < end) {
    if (cur->operand && *cur->operand) {
      if (!hidParseDeviceIdentifier(cur->identifier, cur->operand)) {
        logMessage(LOG_ERR, "invalid %s identifier: %s", cur->name, cur->operand);
        return 0;
      }
    }

    cur += 1;
  }

  return 1;
}

static int
hidCopyStringFilter (const void *from, void *to) {
  const char *fromString = from;
  if (!fromString) return 0;
  if (!*fromString) return 0;

  const char **toString = to;
  *toString = fromString;
  return 1;
}

static int
hidCopyIdentifierFilter (const void *from, void *to) {
  const HidDeviceIdentifier *fromIdentifier = from;
  if (!*fromIdentifier) return 0;

  HidDeviceIdentifier *toIdentifier = to;
  *toIdentifier = *fromIdentifier;
  return 1;
}

static int
hidTestMacAddress (const void *from) {
  const char *address = from;
  const char *byte = address;

  unsigned int state = 0;
  unsigned int octets = 0;
  const char *digits = "0123456789ABCDEFabcdef";

  while (*byte) {
    if (!state) octets += 1;

    if (++state < 3) {
      if (!strchr(digits, *byte)) return 0;
    } else {
      if (*byte != ':') return 0;
      state = 0;
    }

    byte += 1;
  }

  return (octets == 6) && (state == 2);
}

int
hidOpenDeviceWithFilter (HidDevice **device, const HidFilter *filter) {
  unsigned char wantUSB = filter->flags.wantUSB;
  unsigned char wantBluetooth = filter->flags.wantBluetooth;

  HidCommonProperties common;
  memset(&common, 0, sizeof(common));

  HidUSBProperties usb;
  memset(&usb, 0, sizeof(usb));

  HidBluetoothProperties bluetooth;
  memset(&bluetooth, 0, sizeof(bluetooth));

  typedef struct {
    const char *name;
    unsigned char *flag;
    int (*copy) (const void *from, void *to);
    int (*test) (const void *from);
    const void *from;
    void *to;
  } FilterEntry;

  const FilterEntry filterTable[] = {
    { .name = "vendor identifier",
      .copy = hidCopyIdentifierFilter,
      .from = &filter->common.vendorIdentifier,
      .to = &common.vendorIdentifier,
    },

    { .name = "product identifier",
      .copy = hidCopyIdentifierFilter,
      .from = &filter->common.productIdentifier,
      .to = &common.productIdentifier,
    },

    { .name = "manufacturer name",
      .copy = hidCopyStringFilter,
      .from = filter->usb.manufacturerName,
      .to = &usb.manufacturerName,
      .flag = &wantUSB,
    },

    { .name = "product description",
      .copy = hidCopyStringFilter,
      .from = filter->usb.productDescription,
      .to = &usb.productDescription,
      .flag = &wantUSB,
    },

    { .name = "serial number",
      .copy = hidCopyStringFilter,
      .from = filter->usb.serialNumber,
      .to = &usb.serialNumber,
      .flag = &wantUSB,
    },

    { .name = "MAC address",
      .copy = hidCopyStringFilter,
      .test = hidTestMacAddress,
      .from = filter->bluetooth.macAddress,
      .to = &bluetooth.macAddress,
      .flag = &wantBluetooth,
    },

    { .name = "device name",
      .copy = hidCopyStringFilter,
      .from = filter->bluetooth.deviceName,
      .to = &bluetooth.deviceName,
      .flag = &wantBluetooth,
    },
  };

  const FilterEntry *cur = filterTable;
  const FilterEntry *end = cur + ARRAY_COUNT(filterTable);

  while (cur < end) {
    if (cur->copy(cur->from, cur->to)) {
      if (cur->flag) *cur->flag = 1;

      if (cur->test) {
        if (!cur->test(cur->from)) {
          const char *operand = cur->from;
          logMessage(LOG_ERR, "invalid %s: %s", cur->name, operand);
          return 0;
        }
      }
    }

    cur += 1;
  }

  if (wantUSB && wantBluetooth) {
    logMessage(LOG_ERR, "conflicting filter options");
    return 0;
  }

  if (wantBluetooth) {
    HidBluetoothFilter hbf = {
      .common = common,
      .bluetooth = bluetooth,
    };
    *device = hidOpenBluetoothDevice(&hbf);
  } else {
    HidUSBFilter huf = {
      .common = common,
      .usb = usb,
    };

    *device = hidOpenUSBDevice(&huf);
  }

  return 1;
}

int
hidOpenDeviceWithParameters (HidDevice **device, const char *string) {
  char **parameters = hidGetDeviceParameters(string);

  if (parameters) {
    HidFilter filter = {
      .usb = {
        .manufacturerName = parameters[HID_PARM_MANUFACTURER],
        .productDescription = parameters[HID_PARM_DESCRIPTION],
        .serialNumber = parameters[HID_PARM_SERIAL_NUMBER],
      },

      .bluetooth = {
        .macAddress = parameters[HID_PARM_ADDRESS],
        .deviceName = parameters[HID_PARM_NAME],
      },
    };

    int ok = hidSetFilterIdentifiers(
      &filter,
      parameters[HID_PARM_VENDOR],
      parameters[HID_PARM_PRODUCT]
    );

    if (ok) ok = hidOpenDeviceWithFilter(device, &filter);
    deallocateStrings(parameters);
    if (ok) return 1;
  }

  return 0;
}

void
hidCloseDevice (HidDevice *device) {
  hidDestroyHandle(device->handle);
  free(device);
}

const HidItemsDescriptor *
hidGetItems (HidDevice *device) {
  HidGetItemsMethod *method = device->handleMethods->getItems;

  if (!method) {
    logUnsupportedOperation("hidGetItems");
    errno = ENOSYS;
    return 0;
  }

  return method(device->handle);
}

int
hidGetReportSize (
  HidDevice *device,
  HidReportIdentifier identifier,
  HidReportSize *size
) {
  HidGetReportSizeMethod *method = device->handleMethods->getReportSize;

  if (!method) {
    logUnsupportedOperation("hidGetReportSize");
    errno = ENOSYS;
    return 0;
  }

  return method(device->handle, identifier, size);
}

static void
hidLogDataTransfer (const char *action, const unsigned char *data, size_t size, HidReportIdentifier identifier) {
  logBytes(LOG_CATEGORY(HID_IO),
    "%s: %02X", data, size, action, identifier
  );
}

ssize_t
hidGetReport (HidDevice *device, unsigned char *buffer, size_t size) {
  HidGetReportMethod *method = device->handleMethods->getReport;

  if (!method) {
    logUnsupportedOperation("hidGetReport");
    errno = ENOSYS;
    return 0;
  }

  HidReportIdentifier identifier = *buffer;
  ssize_t result = method(device->handle, buffer, size);

  if (result != -1) {
    hidLogDataTransfer("get report", buffer, result, identifier);
  }

  return result;
}

ssize_t
hidSetReport (HidDevice *device, const unsigned char *report, size_t size) {
  HidSetReportMethod *method = device->handleMethods->setReport;

  if (!method) {
    logUnsupportedOperation("hidSetReport");
    errno = ENOSYS;
    return 0;
  }

  hidLogDataTransfer("set report", report+1, size-1, *report);
  return method(device->handle, report, size);
}

ssize_t
hidGetFeature (HidDevice *device, unsigned char *buffer, size_t size) {
  HidGetFeatureMethod *method = device->handleMethods->getFeature;

  if (!method) {
    logUnsupportedOperation("hidGetFeature");
    errno = ENOSYS;
    return 0;
  }

  HidReportIdentifier identifier = *buffer;
  ssize_t result = method(device->handle, buffer, size);

  if (result != -1) {
    hidLogDataTransfer("get feature", buffer, result, identifier);
  }

  return result;
}

ssize_t
hidSetFeature (HidDevice *device, const unsigned char *feature, size_t size) {
  HidSetFeatureMethod *method = device->handleMethods->setFeature;

  if (!method) {
    logUnsupportedOperation("hidSetFeature");
    errno = ENOSYS;
    return 0;
  }

  hidLogDataTransfer("set feature", feature+1, size-1, *feature);
  return method(device->handle, feature, size);
}

int
hidWriteData (HidDevice *device, const unsigned char *data, size_t size) {
  HidWriteDataMethod *method = device->handleMethods->writeData;

  if (!method) {
    logUnsupportedOperation("hidWriteData");
    errno = ENOSYS;
    return 0;
  }

  logBytes(LOG_CATEGORY(HID_IO),
    "output", data, size
  );

  return method(device->handle, data, size);
}

int
hidMonitorInput (HidDevice *device, AsyncMonitorCallback *callback, void *data) {
  HidMonitorInputMethod *method = device->handleMethods->monitorInput;

  if (!method) {
    logUnsupportedOperation("hidMonitorInput");
    errno = ENOSYS;
    return 0;
  }

  return method(device->handle, callback, data);
}

int
hidAwaitInput (HidDevice *device, int timeout) {
  HidAwaitInputMethod *method = device->handleMethods->awaitInput;

  if (!method) {
    logUnsupportedOperation("hidAwaitInput");
    errno = ENOSYS;
    return 0;
  }

  return method(device->handle, timeout);
}

ssize_t
hidReadData (
  HidDevice *device, unsigned char *buffer, size_t size,
  int initialTimeout, int subsequentTimeout
) {
  HidReadDataMethod *method = device->handleMethods->readData;

  if (!method) {
    logUnsupportedOperation("hidReadData");
    errno = ENOSYS;
    return -1;
  }

  ssize_t result = method(device->handle, buffer, size, initialTimeout, subsequentTimeout);

  if (result != -1) {
    if (result > 0) {
      logBytes(LOG_CATEGORY(HID_IO),
        "input", buffer, result
      );
    }
  }

  return result;
}

int
hidGetDeviceIdentifiers (HidDevice *device, HidDeviceIdentifier *vendor, HidDeviceIdentifier *product) {
  HidGetDeviceIdentifiersMethod *method = device->handleMethods->getDeviceIdentifiers;

  if (!method) {
    logUnsupportedOperation("hidGetDeviceIdentifiers");
    errno = ENOSYS;
    return 0;
  }

  return method(device->handle, vendor, product);
}

const char *
hidGetDeviceAddress (HidDevice *device) {
  HidGetDeviceAddressMethod *method = device->handleMethods->getDeviceAddress;

  if (!method) {
    logUnsupportedOperation("hidGetDeviceAddress");
    errno = ENOSYS;
    return NULL;
  }

  return method(device->handle);
}

const char *
hidGetDeviceName (HidDevice *device) {
  HidGetDeviceNameMethod *method = device->handleMethods->getDeviceName;

  if (!method) {
    logUnsupportedOperation("hidGetDeviceName");
    errno = ENOSYS;
    return NULL;
  }

  return method(device->handle);
}

const char *
hidGetHostPath (HidDevice *device) {
  HidGetHostPathMethod *method = device->handleMethods->getHostPath;

  if (!method) {
    logUnsupportedOperation("hidGetHostPath");
    errno = ENOSYS;
    return NULL;
  }

  return method(device->handle);
}

const char *
hidGetHostDevice (HidDevice *device) {
  HidGetHostDeviceMethod *method = device->handleMethods->getHostDevice;

  if (!method) {
    logUnsupportedOperation("hidGetHostDevice");
    errno = ENOSYS;
    return NULL;
  }

  return method(device->handle);
}

const char *
hidCacheString (
  HidHandle *handle, char **cachedValue,
  char *buffer, size_t size,
  HidGetStringMethod *getString, void *data
) {
  if (!*cachedValue) {
    if (!getString(handle, buffer, size, data)) return NULL;
    char *value = strdup(buffer);

    if (!value) {
      logMallocError();
      return NULL;
    }

    *cachedValue = value;
  }

  return *cachedValue;
}

const char *
hidMakeDeviceIdentifier (HidDevice *device, char *buffer, size_t size) {
  size_t length;
  STR_BEGIN(buffer, size);
  STR_PRINTF("%s%c", HID_DEVICE_QUALIFIER, PARAMETER_QUALIFIER_CHARACTER);

  {
    HidDeviceIdentifier vendor;
    HidDeviceIdentifier product;

    if (hidGetDeviceIdentifiers(device, &vendor, &product)) {
      if (vendor) {
        STR_PRINTF(
          "%s%c%04X%c",
          hidDeviceParameterNames[HID_PARM_VENDOR],
          PARAMETER_ASSIGNMENT_CHARACTER,
          vendor, DEVICE_PARAMETER_SEPARATOR
        );
      }

      if (product) {
        STR_PRINTF(
          "%s%c%04X%c",
          hidDeviceParameterNames[HID_PARM_PRODUCT],
          PARAMETER_ASSIGNMENT_CHARACTER,
          product, DEVICE_PARAMETER_SEPARATOR
        );
      }
    }
  }

  STR_FORMAT(device->busMethods->extendDeviceIdentifier, device);
  length = STR_LENGTH;
  STR_END;

  {
    char *last = &buffer[length] - 1;
    if (*last == DEVICE_PARAMETER_SEPARATOR) *last = 0;
  }

  return buffer;
}

int
isHidDeviceIdentifier (const char **identifier) {
  return hasQualifier(identifier, HID_DEVICE_QUALIFIER);
}
