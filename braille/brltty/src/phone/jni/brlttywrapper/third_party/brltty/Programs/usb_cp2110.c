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
#include "usb_serial.h"
#include "usb_cp2110.h"
#include "usb_hid.h"
#include "bitfield.h"

typedef enum {
  USB_CP2110_PARITY_NONE,
  USB_CP2110_PARITY_EVEN,
  USB_CP2110_PARITY_ODD,
  USB_CP2110_PARITY_MARK,
  USB_CP2110_PARITY_SPACE
} USB_CP2110_Parity;

typedef enum {
  USB_CP2110_FLOW_NONE,
  USB_CP2110_FLOW_HARDWARE
} USB_CP2110_FlowControl;

typedef enum {
  USB_CP2110_DATA_5,
  USB_CP2110_DATA_6,
  USB_CP2110_DATA_7,
  USB_CP2110_DATA_8
} USB_CP2110_DataBits;

typedef enum {
  USB_CP2110_STOP_SHORT,
  USB_CP2110_STOP_LONG
} USB_CP2110_StopBits;

typedef struct {
  uint8_t reportIdentifier;
  uint32_t baudRate;
  uint8_t parity;
  uint8_t flowControl;
  uint8_t dataBits;
  uint8_t stopBits;
} PACKED USB_CP2110_UartConfigurationReport;

static int
usbInputFilter_CP2110 (UsbInputFilterData *data) {
  return usbSkipInitialBytes(data, 1);
}

static int
usbSetReport_CP2110 (UsbDevice *device, const void *report, size_t size) {
  const unsigned char *bytes = report;
  ssize_t result = usbHidSetReport(device, 0, bytes[0], report, size, 1000);
  return result != -1;
}

static int
usbSetLineConfiguration_CP2110 (UsbDevice *device, unsigned int baud, unsigned int dataBits, SerialStopBits stopBits, SerialParity parity, SerialFlowControl flowControl) {
  USB_CP2110_UartConfigurationReport report;

  memset(&report, 0, sizeof(report));
  report.reportIdentifier = 0X50;

  if ((baud >= 300) && (baud <= 500000)) {
    putBigEndian32(&report.baudRate, baud);
  } else {
    logUnsupportedBaud(baud);
    errno = EINVAL;
    return 0;
  }

  switch (dataBits) {
    case 5:
      report.dataBits = USB_CP2110_DATA_5;
      break;

    case 6:
      report.dataBits = USB_CP2110_DATA_6;
      break;

    case 7:
      report.dataBits = USB_CP2110_DATA_7;
      break;

    case 8:
      report.dataBits = USB_CP2110_DATA_8;
      break;

    default:
      logUnsupportedDataBits(dataBits);
      errno = EINVAL;
      return 0;
  }

  if (stopBits == SERIAL_STOP_1) {
    report.stopBits = USB_CP2110_STOP_SHORT;
  } else if (stopBits == ((dataBits > 5)? SERIAL_STOP_2: SERIAL_STOP_1_5)) {
    report.stopBits = USB_CP2110_STOP_LONG;
  } else {
    logUnsupportedStopBits(stopBits);
    errno = EINVAL;
    return 0;
  }

  switch (parity) {
    case SERIAL_PARITY_NONE:
      report.parity = USB_CP2110_PARITY_NONE;
      break;

    case SERIAL_PARITY_ODD:
      report.parity = USB_CP2110_PARITY_ODD;
      break;

    case SERIAL_PARITY_EVEN:
      report.parity = USB_CP2110_PARITY_EVEN;
      break;

    case SERIAL_PARITY_MARK:
      report.parity = USB_CP2110_PARITY_MARK;
      break;

    case SERIAL_PARITY_SPACE:
      report.parity = USB_CP2110_PARITY_SPACE;
      break;

    default:
      logUnsupportedParity(parity);
      errno = EINVAL;
      return 0;
  }

  switch (flowControl) {
    case SERIAL_FLOW_NONE:
      report.flowControl = USB_CP2110_FLOW_NONE;
      break;

    case SERIAL_FLOW_HARDWARE:
      report.flowControl = USB_CP2110_FLOW_HARDWARE;
      break;

    default:
      logUnsupportedFlowControl(flowControl);
      errno = EINVAL;
      return 0;
  }

  return usbSetReport_CP2110(device, &report, sizeof(report));
}

static int
usbSetUartStatus_CP2110 (UsbDevice *device, unsigned char status) {
  const unsigned char report[] = {0X41, status};
  return usbSetReport_CP2110(device, report, sizeof(report));
}

static int
usbEnableAdapter_CP2110 (UsbDevice *device) {
  if (usbSetUartStatus_CP2110(device, 0X01)) {
    return 1;
  }

  return 0;
}

static ssize_t
usbWriteData_CP2110 (UsbDevice *device, const void *data, size_t size) {
  const unsigned char *first = data;
  const unsigned char *next = first;

  while (size) {
    unsigned char report[0X40];
    size_t count = sizeof(report) - 1;

    if (count > size) count = size;
    report[0] = count;
    memcpy(&report[1], next, count);

    {
      ssize_t result = usbWriteEndpoint(device, 2, report, count+1, 1000);
      if (result == -1) return result;
    }

    next += count;
    size -= count;
  }

  return next - first;
}

const UsbSerialOperations usbSerialOperations_CP2110 = {
  .name = "CP2110",

  .setLineConfiguration = &usbSetLineConfiguration_CP2110,

  .enableAdapter = usbEnableAdapter_CP2110,
  .inputFilter = usbInputFilter_CP2110,
  .writeData = usbWriteData_CP2110
};
