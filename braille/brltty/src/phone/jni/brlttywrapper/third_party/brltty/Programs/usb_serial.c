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
#include "program.h"
#include "usb_internal.h"
#include "usb_serial.h"
#include "usb_adapters.h"
#include "bitfield.h"

static void
usbLogSerialProblem (UsbDevice *device, const char *problem) {
  logMessage(LOG_CATEGORY(SERIAL_IO),
    "%s: Vendor:%04X Product:%04X",
    problem,
    getLittleEndian16(device->descriptor.idVendor),
    getLittleEndian16(device->descriptor.idProduct)
  );
}

int
usbSkipInitialBytes (UsbInputFilterData *data, unsigned int count) {
  if (data->length > count) {
    unsigned char *buffer = data->buffer;
    memmove(buffer, buffer+count, data->length-=count);
  } else {
    data->length = 0;
  }

  return 1;
}

static const UsbSerialAdapter **usbSerialAdapters = NULL;

static int
usbCompareSerialAdapters (const UsbSerialAdapter *adapter1, const UsbSerialAdapter *adapter2) {
  if (adapter1->vendor < adapter2->vendor) return -1;
  if (adapter1->vendor > adapter2->vendor) return 1;

  if (adapter1->product < adapter2->product) return -1;
  if (adapter1->product > adapter2->product) return 1;

  return 0;
}

static int
usbSortSerialAdapters (const void *element1, const void *element2) {
  const UsbSerialAdapter *const *adapter1 = element1;
  const UsbSerialAdapter *const *adapter2 = element2;

  return usbCompareSerialAdapters(*adapter1, *adapter2);
}

static int
usbSearchSerialAdapter (const void *target, const void *element) {
  const UsbSerialAdapter *const *adapter = element;

  return usbCompareSerialAdapters(target, *adapter);
}

static const UsbSerialAdapter *
usbGetSerialAdapter (uint16_t vendor, uint16_t product) {
  const UsbSerialAdapter target = {
    .vendor = vendor,
    .product = product
  };

  const UsbSerialAdapter *const *adapter = bsearch(&target, usbSerialAdapters, usbSerialAdapterCount, sizeof(*usbSerialAdapters), usbSearchSerialAdapter);

  return adapter? *adapter: NULL;
}

const UsbSerialAdapter *
usbFindSerialAdapter (const UsbDeviceDescriptor *descriptor) {
  if (!usbSerialAdapters) {
    const UsbSerialAdapter **adapters;

    if (!(adapters = malloc(usbSerialAdapterCount * sizeof(*adapters)))) {
      logMallocError();
      return NULL;
    }

    {
      const UsbSerialAdapter *source = usbSerialAdapterTable;
      const UsbSerialAdapter *end = source + usbSerialAdapterCount;
      const UsbSerialAdapter **target = adapters;

      while (source < end) *target++ = source++;
      qsort(adapters, usbSerialAdapterCount, sizeof(*adapters), usbSortSerialAdapters);
    }

    usbSerialAdapters = adapters;
    registerProgramMemory("sorted-usb-serial-adapters", &usbSerialAdapters);
  }

  {
    uint16_t vendor = getLittleEndian16(descriptor->idVendor);
    uint16_t product = getLittleEndian16(descriptor->idProduct);
    const UsbSerialAdapter *adapter = usbGetSerialAdapter(vendor, product);

    if (!adapter) adapter = usbGetSerialAdapter(vendor, 0);
    return adapter;
  }
}

int
usbSetSerialOperations (UsbDevice *device) {
  if (!device->serial.operations) {
    const UsbSerialOperations *uso = NULL;

    {
      const UsbSerialAdapter *adapter = usbFindSerialAdapter(&device->descriptor);

      if (adapter) {
        if ((uso = adapter->operations)) {
          UsbInputFilter *filter = uso->inputFilter;
         
          if (filter && !usbAddInputFilter(device, filter)) return 0;
        }
      }
    }

    if (!uso) {
      if (device->descriptor.bDeviceClass == 0X02) {
        uso = &usbSerialOperations_CDC_ACM;
      }
    }

    if (uso) {
      logMessage(LOG_CATEGORY(SERIAL_IO), "USB adapter: %s", uso->name);
      UsbSerialData *usd = NULL;

      if (uso->makeData) {
        if (!uso->makeData(device, &usd)) {
          return 0;
        }
      }

      device->serial.operations = uso;
      device->serial.data = usd;
    }
  }

  return 1;
}

const UsbSerialOperations *
usbGetSerialOperations (UsbDevice *device) {
  return device->serial.operations;
}

UsbSerialData *
usbGetSerialData (UsbDevice *device) {
  return device->serial.data;
}

int
usbSetSerialParameters (UsbDevice *device, const SerialParameters *parameters) {
  int ok = 0;
  const UsbSerialOperations *serial = usbGetSerialOperations(device);

  if (!serial) {
    usbLogSerialProblem(device, "no serial operations");
    errno = ENOSYS;
  } else if (serial->setLineConfiguration) {
    ok = serial->setLineConfiguration(
      device, parameters->baud,
      parameters->dataBits, parameters->stopBits,
      parameters->parity, parameters->flowControl
    );
  } else if (serial->setLineProperties) {
    ok = serial->setLineProperties(
      device, parameters->baud,
      parameters->dataBits, parameters->stopBits,
      parameters->parity
    );
  } else {
    ok = 1;

    if (!serial->setBaud) {
      usbLogSerialProblem(device, "setting baud is not supported");
    } else if (!serial->setBaud(device, parameters->baud)) {
      ok = 0;
    }

    if (!serial->setDataFormat) {
      usbLogSerialProblem(device, "setting data format is not supported");
    } else if (!serial->setDataFormat(device, parameters->dataBits, parameters->stopBits, parameters->parity)) {
      ok = 0;
    }

    if (!serial->setFlowControl) {
      usbLogSerialProblem(device, "setting flow control is not supported");
    } else if (!serial->setFlowControl(device, parameters->flowControl)) {
      ok = 0;
    }
  }

  return ok;
}
