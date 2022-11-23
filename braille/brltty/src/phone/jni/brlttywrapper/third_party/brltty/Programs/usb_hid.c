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

#include "log.h"
#include "bitfield.h"
#include "io_usb.h"

const unsigned char usbHidItemLengths[] = {0, 1, 2, 4};

const UsbHidDescriptor *
usbHidDescriptor (UsbDevice *device) {
  const UsbDescriptor *descriptor = NULL;

  while (usbNextDescriptor(device, &descriptor))
    if (descriptor->endpoint.bDescriptorType == UsbDescriptorType_HID)
      return &descriptor->hid;

  logMessage(LOG_WARNING, "USB: HID descriptor not found");
  errno = ENOENT;
  return NULL;
}

ssize_t
usbHidGetItems (
  UsbDevice *device,
  unsigned char interface,
  unsigned char number,
  unsigned char **items,
  int timeout
) {
  const UsbHidDescriptor *hid = usbHidDescriptor(device);

  if (hid) {
    if (number < hid->bNumDescriptors) {
      const UsbClassDescriptor *descriptor = &hid->descriptors[number];
      uint16_t length = getLittleEndian16(descriptor->wDescriptorLength);
      void *buffer = malloc(length);

      if (buffer) {
        ssize_t result = usbControlRead(device,
                                        UsbControlRecipient_Interface, UsbControlType_Standard,
                                        UsbStandardRequest_GetDescriptor,
                                        (descriptor->bDescriptorType << 8) | interface,
                                        number, buffer, length, timeout);

        if (result != -1) {
          *items = buffer;
          return result;
        }

        free(buffer);
      } else {
        logMallocError();
      }
    } else {
      logMessage(LOG_WARNING, "USB report descriptor not found: %u[%u]",
                 interface, number);
    }
  }

  return -1;
}

int
usbHidFillReportDescription (
  const unsigned char *items,
  size_t size,
  unsigned char identifier,
  UsbHidReportDescription *description
) {
  int found = 0;
  int index = 0;

  while (index < size) {
    unsigned char item = items[index++];
    UsbHidItemType type = USB_HID_ITEM_TYPE(item);
    unsigned char length = usbHidItemLengths[USB_HID_ITEM_LENGTH(item)];
    uint32_t value = 0;

    if (length) {
      unsigned char shift = 0;

      do {
        if (index == size) return 0;
        value |= items[index++] << shift;
        shift += 8;
      } while (--length);
    }

    switch (type) {
      case UsbHidItemType_ReportID:
        if (!found && (value == identifier)) {
          memset(description, 0, sizeof(*description));
          description->reportIdentifier = identifier;

          found = 1;
          continue;
        }
        break;

      case UsbHidItemType_ReportCount:
        if (found) {
          description->reportCount = value;
          goto defined;
        }
        break;

      case UsbHidItemType_ReportSize:
        if (found) {
          description->reportSize = value;
          goto defined;
        }
        break;

      case UsbHidItemType_LogicalMinimum:
        if (found) {
          description->logicalMinimum = value;
          goto defined;
        }
        break;

      case UsbHidItemType_LogicalMaximum:
        if (found) {
          description->logicalMaximum = value;
          goto defined;
        }
        break;

      defined:
        description->defined |= USB_HID_ITEM_BIT(type);
        continue;

      default:
        break;
    }

    if (found) break;
  }

  return found;
}

int
usbHidGetReportSize (
  const unsigned char *items,
  size_t length,
  unsigned char identifier,
  size_t *size
) {
  UsbHidReportDescription description;

  *size = 0;

  if (usbHidFillReportDescription(items, length, identifier, &description)) {
    if (description.defined & USB_HID_ITEM_BIT(UsbHidItemType_ReportCount)) {
      if (description.defined & USB_HID_ITEM_BIT(UsbHidItemType_ReportSize)) {
        uint32_t bytes = ((description.reportCount * description.reportSize) + 7) / 8;

        logMessage(LOG_CATEGORY(USB_IO), "HID report size: %02X = %"PRIu32, identifier, bytes);
        *size = 1 + bytes;
        return 1;
      } else {
        logMessage(LOG_WARNING, "HID report size not defined: %02X", identifier);
      }
    } else {
      logMessage(LOG_WARNING, "HID report count not defined: %02X", identifier);
    }
  } else {
    logMessage(LOG_WARNING, "HID report not found: %02X", identifier);
  }

  return 0;
}

ssize_t
usbHidGetReport (
  UsbDevice *device,
  unsigned char interface,
  unsigned char report,
  void *buffer,
  uint16_t length,
  int timeout
) {
  return usbControlRead(device,
                        UsbControlRecipient_Interface, UsbControlType_Class,
                        UsbHidRequest_GetReport,
                        (UsbHidReportType_Input << 8) | report, interface,
                        buffer, length, timeout);
}

ssize_t
usbHidSetReport (
  UsbDevice *device,
  unsigned char interface,
  unsigned char report,
  const void *buffer,
  uint16_t length,
  int timeout
) {
  return usbControlWrite(device,
                         UsbControlRecipient_Interface, UsbControlType_Class,
                         UsbHidRequest_SetReport,
                         (UsbHidReportType_Output << 8) | report, interface,
                         buffer, length, timeout);
}

ssize_t
usbHidGetFeature (
  UsbDevice *device,
  unsigned char interface,
  unsigned char report,
  void *buffer,
  uint16_t length,
  int timeout
) {
  return usbControlRead(device,
                        UsbControlRecipient_Interface, UsbControlType_Class,
                        UsbHidRequest_GetReport,
                        (UsbHidReportType_Feature << 8) | report, interface,
                        buffer, length, timeout);
}

ssize_t
usbHidSetFeature (
  UsbDevice *device,
  unsigned char interface,
  unsigned char report,
  const void *buffer,
  uint16_t length,
  int timeout
) {
  return usbControlWrite(device,
                         UsbControlRecipient_Interface, UsbControlType_Class,
                         UsbHidRequest_SetReport,
                         (UsbHidReportType_Feature << 8) | report, interface,
                         buffer, length, timeout);
}
