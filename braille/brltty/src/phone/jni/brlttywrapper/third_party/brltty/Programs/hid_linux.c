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
#include <fcntl.h>
#include <sys/stat.h>
#include <sys/ioctl.h>
#include <libudev.h>
#include <linux/hidraw.h>
#include <linux/input.h>

#include "log.h"
#include "hid_types.h"
#include "hid_internal.h"
#include "hid_items.h"
#include "io_misc.h"
#include "async_handle.h"
#include "async_io.h"

struct HidHandleStruct {
  char *sysfsPath;
  char *devicePath;

  int fileDescriptor;
  AsyncHandle inputMonitor;
  struct hidraw_devinfo deviceInformation;

  HidItemsDescriptor *hidItems;
  char *deviceAddress;
  char *deviceName;
  char *hostPath;

  char strings[];
};

static void
hidLinuxCancelInputMonitor (HidHandle *handle) {
  if (handle->inputMonitor) {
    asyncCancelRequest(handle->inputMonitor);
    handle->inputMonitor = NULL;
  }
}

static void
hidLinuxDestroyHandle (HidHandle *handle) {
  hidLinuxCancelInputMonitor(handle);
  close(handle->fileDescriptor);

  if (handle->hidItems) free(handle->hidItems);
  if (handle->deviceAddress) free(handle->deviceAddress);
  if (handle->deviceName) free(handle->deviceName);
  if (handle->hostPath) free(handle->hostPath);

  free(handle);
}

static const HidItemsDescriptor *
hidLinuxGetItems (HidHandle *handle) {
  if (handle->hidItems) return handle->hidItems;
  int size;

  if (ioctl(handle->fileDescriptor, HIDIOCGRDESCSIZE, &size) != -1) {
    struct hidraw_report_descriptor descriptor = {
      .size = size
    };

    if (ioctl(handle->fileDescriptor, HIDIOCGRDESC, &descriptor) != -1) {
      HidItemsDescriptor *items;

      if ((items = malloc(sizeof(*items) + size))) {
        memset(items, 0, sizeof(*items));
        items->count = size;
        memcpy(items->bytes, descriptor.value, size);
        return (handle->hidItems = items);
      } else {
        logMallocError();
      }
    } else {
      logSystemError("ioctl[HIDIOCGRDESC]");
    }
  } else {
    logSystemError("ioctl[HIDIOCGRDESCSIZE]");
  }

  return NULL;
}

static int
hidLinuxGetReportSize (
  HidHandle *handle,
  HidReportIdentifier identifier,
  HidReportSize *size
) {
  const HidItemsDescriptor *items = hidLinuxGetItems(handle);
  if (!items) return 0;
  return hidReportSize(items, identifier, size);
}

static ssize_t
hidLinuxGetReport (HidHandle *handle, unsigned char *buffer, size_t size) {
  int length;

#ifdef HIDIOCGINPUT
  length = ioctl(handle->fileDescriptor, HIDIOCGINPUT(size), buffer);
#else /* HIDIOCGINPUT */
  length = -1;
  errno = ENOSYS;
#endif /* HIDIOCGINPUT */

  if (length == -1) logSystemError("ioctl[HIDIOCGINPUT]");
  return length;
}

static ssize_t
hidLinuxSetReport (HidHandle *handle, const unsigned char *report, size_t size) {
  int count;

#ifdef HIDIOCSOUTPUT
  count = ioctl(handle->fileDescriptor, HIDIOCSOUTPUT(size), report);
#else /* HIDIOCSOUTPUT */
  count = write(handle->fileDescriptor, report, size);
#endif /* HIDIOCSOUTPUT */

  if (count == -1) logSystemError("ioctl[HIDIOCSOUTPUT]");
  return count;
}

static ssize_t
hidLinuxGetFeature (HidHandle *handle, unsigned char *buffer, size_t size) {
  int result = ioctl(handle->fileDescriptor, HIDIOCGFEATURE(size), buffer);

  if (result == -1) {
    logSystemError("ioctl[HIDIOCGFEATURE]");
  }

  return result;
}

static ssize_t
hidLinuxSetFeature (HidHandle *handle, const unsigned char *feature, size_t size) {
  int count = ioctl(handle->fileDescriptor, HIDIOCSFEATURE(size), feature);
  if (count == -1) logSystemError("ioctl[HIDIOCSFEATURE]");
  return count;
}

static int
hidLinuxWriteData (HidHandle *handle, const unsigned char *data, size_t size) {
  return writeFile(handle->fileDescriptor, data, size);
}

static int
hidLinuxMonitorInput (HidHandle *handle, AsyncMonitorCallback *callback, void *data) {
  hidLinuxCancelInputMonitor(handle);
  if (!callback) return 1;
  return asyncMonitorFileInput(&handle->inputMonitor, handle->fileDescriptor, callback, data);
}

static int
hidLinuxAwaitInput (HidHandle *handle, int timeout) {
  return awaitFileInput(handle->fileDescriptor, timeout);
}

static ssize_t
hidLinuxReadData (
  HidHandle *handle, unsigned char *buffer, size_t size,
  int initialTimeout, int subsequentTimeout
) {
  return readFile(handle->fileDescriptor, buffer, size, initialTimeout, subsequentTimeout);
}

static int
hidLinuxGetDeviceIdentifiers (HidHandle *handle, HidDeviceIdentifier *vendor, HidDeviceIdentifier *product) {
  if (vendor) *vendor = handle->deviceInformation.vendor;
  if (product) *product = handle->deviceInformation.product;
  return 1;
}

static int
hidLinuxGetRawName (HidHandle *handle, char *buffer, size_t size, void *data) {
  // For USB, this will be the manufacturer string, a space, and the product string.
  // For Bluetooth, this will be the name of the device.
  int length = ioctl(handle->fileDescriptor, HIDIOCGRAWNAME(size), buffer);

  if (length == -1) {
    logSystemError("ioctl[HIDIOCGRAWNAME]");
    length = 0;
  } else if (length == size) {
    length -= 1;
  }

  buffer[length] = 0;
  return !!length;
}

static int
hidLinuxGetRawPhysical (HidHandle *handle, char *buffer, size_t size, void *data) {
  // For USB, this will be the physical path (controller, hubs, ports, etc) to the device.
  // For Bluetooth, this will be the address of the host controller.
  int length = ioctl(handle->fileDescriptor, HIDIOCGRAWPHYS(size), buffer);

  if (length == -1) {
    logSystemError("ioctl[HIDIOCGRAWPHYS]");
    length = 0;
  } else if (length == size) {
    length -= 1;
  }

  buffer[length] = 0;
  return !!length;
}

static int
hidLinuxGetRawUnique (HidHandle *handle, char *buffer, size_t size, void *data) {
  // For USB, this will be the serial number of the device.
  // For Bluetooth, this will be the MAC (hardware) address of the device.
  int length;

#ifdef HIDIOCGRAWUNIQ
  length = ioctl(handle->fileDescriptor, HIDIOCGRAWUNIQ(size), buffer);
#else /* HIDIOCGRAWUNIQ */
  length = -1;
  errno = ENOSYS;
#endif /* HIDIOCGRAWUNIQ */

  if (length == -1) {
    logSystemError("ioctl[HIDIOCGRAWUNIQ]");
    length = 0;
  } else if (length == size) {
    length -= 1;
  }

  buffer[length] = 0;
  return !!length;
}

static const char *
hidLinuxGetDeviceAddress (HidHandle *handle) {
  char buffer[0X1000];

  return hidCacheString(
    handle, &handle->deviceAddress,
    buffer, sizeof(buffer),
    hidLinuxGetRawUnique, NULL
  );
}

static const char *
hidLinuxGetDeviceName (HidHandle *handle) {
  char buffer[0X1000];

  return hidCacheString(
    handle, &handle->deviceName,
    buffer, sizeof(buffer),
    hidLinuxGetRawName, NULL
  );
}

static const char *
hidLinuxGetHostPath (HidHandle *handle) {
  char buffer[0X1000];

  return hidCacheString(
    handle, &handle->hostPath,
    buffer, sizeof(buffer),
    hidLinuxGetRawPhysical, NULL
  );
}

static const char *
hidLinuxGetHostDevice (HidHandle *handle) {
  return handle->devicePath;
}

static const HidHandleMethods hidLinuxHandleMethods = {
  .destroyHandle = hidLinuxDestroyHandle,

  .getItems = hidLinuxGetItems,

  .getReportSize = hidLinuxGetReportSize,
  .getReport = hidLinuxGetReport,
  .setReport = hidLinuxSetReport,
  .getFeature = hidLinuxGetFeature,
  .setFeature = hidLinuxSetFeature,

  .writeData = hidLinuxWriteData,
  .monitorInput = hidLinuxMonitorInput,
  .awaitInput = hidLinuxAwaitInput,
  .readData = hidLinuxReadData,

  .getDeviceIdentifiers = hidLinuxGetDeviceIdentifiers,
  .getDeviceAddress = hidLinuxGetDeviceAddress,
  .getDeviceName = hidLinuxGetDeviceName,
  .getHostPath = hidLinuxGetHostPath,
  .getHostDevice = hidLinuxGetHostDevice,
};

typedef int HidLinuxAttributeTester (
  struct udev_device *device,
  const char *name,
  const void *value
);

static int
hidLinuxTestString (
  struct udev_device *device,
  const char *name,
  const void *value
) {
  const char *testString = value;
  if (!testString) return 1;
  if (!*testString) return 1;

  const char *actualString = udev_device_get_sysattr_value(device, name);
  if (!actualString) return 0;
  if (!*actualString) return 0;

  return hidMatchString(actualString, testString);
}

typedef struct {
  const char *name;
  const void *value;
  HidLinuxAttributeTester *function;
} HidLinuxAttributeTest;

static int
hidLinuxTestAttributes (
  struct udev_device *device,
  const HidLinuxAttributeTest *tests,
  size_t testCount
) {
  const HidLinuxAttributeTest *test = tests;
  const HidLinuxAttributeTest *end = test + testCount;

  while (test < end) {
    if (!test->function(device, test->name, test->value)) return 0;
    test += 1;
  }

  return 1;
}

static HidHandle *
hidLinuxNewHandle (struct udev_device *device) {
  const char *sysPath = udev_device_get_syspath(device);
  const char *devPath = udev_device_get_devnode(device);

  size_t sysSize = strlen(sysPath) + 1;
  size_t devSize = strlen(devPath) + 1;
  HidHandle *handle = malloc(sizeof(*handle) + sysSize + devSize);

  if (handle) {
    memset(handle, 0, sizeof(*handle));

    {
      char *string = handle->strings;
      string = mempcpy((handle->sysfsPath = string), sysPath, sysSize);
      string = mempcpy((handle->devicePath = string), devPath, devSize);
    }

    if ((handle->fileDescriptor = open(devPath, (O_RDWR | O_NONBLOCK))) != -1) {
      if (ioctl(handle->fileDescriptor, HIDIOCGRAWINFO, &handle->deviceInformation) != -1) {
        return handle;
      } else {
        logSystemError("ioctl[HIDIOCGRAWINFO]");
      }

      close(handle->fileDescriptor);
    } else {
      logMessage(LOG_ERR, "device open error: %s: %s", devPath, strerror(errno));
    }

    free(handle);
  } else {
    logMallocError();
  }

  return NULL;
}

typedef int HidLinuxPropertiesTester (
  HidHandle *handle,
  struct udev_device *device,
  const void *filter
);

static HidHandle *
hidLinuxFindDevice (HidLinuxPropertiesTester *testProperties, const void *filter) {
  HidHandle *handle = NULL;
  struct udev *udev = udev_new();

  if (udev) {
    struct udev_enumerate *enumeration = udev_enumerate_new(udev);

    if (enumeration) {
      udev_enumerate_add_match_subsystem(enumeration, "hidraw");
      udev_enumerate_scan_devices(enumeration);

      struct udev_list_entry *deviceList = udev_enumerate_get_list_entry(enumeration);
      struct udev_list_entry *deviceEntry;

      udev_list_entry_foreach(deviceEntry, deviceList) {
        const char *sysPath = udev_list_entry_get_name(deviceEntry);
        struct udev_device *hidDevice = udev_device_new_from_syspath(udev, sysPath);

        if (hidDevice) {
          if ((handle = hidLinuxNewHandle(hidDevice))) {
            if (!testProperties(handle, hidDevice, filter)) {
              hidLinuxDestroyHandle(handle);
              handle = NULL;
            }
          }

          udev_device_unref(hidDevice);
        }

        if (handle) break;
      }

      udev_enumerate_unref(enumeration);
      enumeration = NULL;
    }

    udev_unref(udev);
    udev = NULL;
  }

  return handle;
}

static int
hidLinuxTestCommonProperties (HidHandle *handle, const HidCommonProperties *common) {
  if (common->vendorIdentifier) {
    if (handle->deviceInformation.vendor != common->vendorIdentifier) {
      return 0;
    }
  }

  if (common->productIdentifier) {
    if (handle->deviceInformation.product != common->productIdentifier) {
      return 0;
    }
  }

  return 1;
}

static int
hidLinuxTestUSBProperties (HidHandle *handle, struct udev_device *hidDevice, const void *filter) {
  if (handle->deviceInformation.bustype != BUS_USB) return 0;

  const HidUSBFilter *huf = filter;
  if (!hidLinuxTestCommonProperties(handle, &huf->common)) return 0;

  struct udev_device *usbDevice = udev_device_get_parent_with_subsystem_devtype(hidDevice, "usb", "usb_device");
  if (!usbDevice) return 0;
  const HidUSBProperties *test = &huf->usb;

  const HidLinuxAttributeTest tests[] = {
    { .name = "manufacturer",
      .value = test->manufacturerName,
      .function = hidLinuxTestString
    },

    { .name = "product",
      .value = test->productDescription,
      .function = hidLinuxTestString
    },

    { .name = "serial",
      .value = test->serialNumber,
      .function = hidLinuxTestString
    },
  };

  return hidLinuxTestAttributes(usbDevice, tests, ARRAY_COUNT(tests));
}

static HidHandle *
hidLinuxNewUSBHandle (const HidUSBFilter *filter) {
  return hidLinuxFindDevice(hidLinuxTestUSBProperties, filter);
}

static int
hidLinuxTestBluetoothProperties (HidHandle *handle, struct udev_device *hidDevice, const void *filter) {
  if (handle->deviceInformation.bustype != BUS_BLUETOOTH) return 0;

  const HidBluetoothFilter *hbf = filter;
  if (!hidLinuxTestCommonProperties(handle, &hbf->common)) return 0;
  const HidBluetoothProperties *test = &hbf->bluetooth;

  {
    const char *testAddress = test->macAddress;

    if (testAddress && *testAddress) {
      const char *actualAddress = hidLinuxGetDeviceAddress(handle);
      if (!actualAddress) return 0;
      if (strcasecmp(actualAddress, testAddress) != 0) return 0;
    }
  }

  {
    const char *testName = test->deviceName;

    if (testName && *testName) {
      const char *actualName = hidLinuxGetDeviceName(handle);
      if (!actualName) return 0;
      if (!hidMatchString(actualName, testName)) return 0;
    }
  }

  return 1;
}

static HidHandle *
hidLinuxNewBluetoothHandle (const HidBluetoothFilter *filter) {
  return hidLinuxFindDevice(hidLinuxTestBluetoothProperties, filter);
}

const HidPackageDescriptor hidPackageDescriptor = {
  .packageName = "Linux",
  .handleMethods = &hidLinuxHandleMethods,

  .newUSBHandle = hidLinuxNewUSBHandle,
  .newBluetoothHandle = hidLinuxNewBluetoothHandle,
};
