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

#include <stdio.h>
#include <string.h>
#include <errno.h>
#include <fcntl.h>
#include <sys/stat.h>

#ifdef HAVE_REGEX_H
#include <regex.h>
#endif /* HAVE_REGEX_H */

#include "log.h"
#include "strfmt.h"
#include "parameters.h"
#include "bitfield.h"
#include "bitmask.h"
#include "parse.h"
#include "file.h"
#include "utf8.h"
#include "device.h"
#include "timing.h"
#include "async_handle.h"
#include "async_wait.h"
#include "async_alarm.h"
#include "io_misc.h"
#include "io_usb.h"
#include "usb_internal.h"
#include "usb_devices.h"
#include "usb_serial.h"

ssize_t
usbControlRead (
  UsbDevice *device,
  uint8_t recipient,
  uint8_t type,
  uint8_t request,
  uint16_t value,
  uint16_t index,
  void *buffer,
  uint16_t length,
  int timeout
) {
  return usbControlTransfer(device, UsbControlDirection_Input, recipient, type,
                            request, value, index, buffer, length, timeout);
}

ssize_t
usbControlWrite (
  UsbDevice *device,
  uint8_t recipient,
  uint8_t type,
  uint8_t request,
  uint16_t value,
  uint16_t index,
  const void *buffer,
  uint16_t length,
  int timeout
) {
  return usbControlTransfer(device, UsbControlDirection_Output, recipient, type,
                            request, value, index, (void *)buffer, length, timeout);
}

ssize_t
usbGetDescriptor (
  UsbDevice *device,
  unsigned char type,
  unsigned char number,
  unsigned int index,
  UsbDescriptor *descriptor,
  int timeout
) {
  return usbControlRead(device, UsbControlRecipient_Device, UsbControlType_Standard,
                        UsbStandardRequest_GetDescriptor, (type << 8) | number, index,
                        descriptor->bytes, sizeof(descriptor->bytes), timeout);
}

int
usbGetDeviceDescriptor (
  UsbDevice *device,
  UsbDeviceDescriptor *descriptor
) {
  UsbDescriptor desc;
  int size = usbGetDescriptor(device, UsbDescriptorType_Device, 0, 0, &desc, 1000);

  if (size != -1) {
    *descriptor = desc.device;
  }

  return size;
}

int
usbGetLanguage (
  UsbDevice *device,
  uint16_t *language,
  int timeout
) {
  UsbDescriptor descriptor;
  ssize_t size = usbGetDescriptor(device, UsbDescriptorType_String,
                              0, 0, &descriptor, timeout);

  if (size != -1) {
    if (size >= 4) {
      *language = getLittleEndian16(descriptor.string.wData[0]);
      logMessage(LOG_CATEGORY(USB_IO), "USB language: %02X", *language);
      return 1;
    } else {
      logMessage(LOG_ERR, "USB language code string too short: %"PRIssize, size);
      errno = EIO;
    }
  } else {
    logMessage(LOG_ERR, "USB language code string read error");
  }

  return 0;
}

char *
usbDecodeString (const UsbStringDescriptor *descriptor) {
  size_t count = (descriptor->bLength - 2) / sizeof(descriptor->wData[0]);
  char buffer[(count * UTF8_LEN_MAX) + 1];

  const uint16_t *source = descriptor->wData;
  const uint16_t *end = source + count;
  char *target = buffer;

  while (source < end) {
    size_t length = convertWcharToUtf8(getLittleEndian16(*source++), target);

    target += length;
  }
  *target = 0;

  {
    char *string = strdup(buffer);

    if (!string) logMallocError();
    return string;
  }
}

char *
usbGetString (
  UsbDevice *device,
  unsigned char number,
  int timeout
) {
  UsbDescriptor descriptor;

  if (!device->language) {
    if (!usbGetLanguage(device, &device->language, timeout)) {
      return NULL;
    }
  }

  if (usbGetDescriptor(device, UsbDescriptorType_String,
                       number, device->language,
                       &descriptor, timeout) == -1) {
    logMessage(LOG_ERR, "USB string read error: %u", number);
    return NULL;
  }

  return usbDecodeString(&descriptor.string);
}

char *
usbGetManufacturer (UsbDevice *device, int timeout) {
  return usbGetString(device, device->descriptor.iManufacturer, timeout);
}

char *
usbGetProduct (UsbDevice *device, int timeout) {
  return usbGetString(device, device->descriptor.iProduct, timeout);
}

char *
usbGetSerialNumber (UsbDevice *device, int timeout) {
  return usbGetString(device, device->descriptor.iSerialNumber, timeout);
}

static size_t
usbFormatLogSetupPacket (char *buffer, size_t size, const void *data) {
  const UsbSetupPacket *setup = data;
  size_t length;
  STR_BEGIN(buffer, size);

  STR_PRINTF("setup packet: Typ:%02X Req:%02X Val:%04X Idx:%04X Len:%04X",
             setup->bRequestType, setup->bRequest,
             getLittleEndian16(setup->wValue),
             getLittleEndian16(setup->wIndex),
             getLittleEndian16(setup->wLength));

  length = STR_LENGTH;
  STR_END;
  return length;
}

void
usbLogSetupPacket (const UsbSetupPacket *setup) {
  logData(LOG_CATEGORY(USB_IO), usbFormatLogSetupPacket, setup);
}

void
usbMakeSetupPacket (
  UsbSetupPacket *setup,
  uint8_t direction,
  uint8_t recipient,
  uint8_t type,
  uint8_t request,
  uint16_t value,
  uint16_t index,
  uint16_t length
) {
  setup->bRequestType = direction | recipient | type;
  setup->bRequest = request;
  putLittleEndian16(&setup->wValue, value);
  putLittleEndian16(&setup->wIndex, index);
  putLittleEndian16(&setup->wLength, length);
  usbLogSetupPacket(setup);
}

void
usbLogEndpointData (
  UsbEndpoint *endpoint, const char *label,
  const void *data, size_t size
) {
  logBytes(LOG_CATEGORY(USB_IO), "endpoint %02X %s", data, size,
           endpoint->descriptor->bEndpointAddress, label);
}

void
usbLogString (
  UsbDevice *device,
  unsigned char number,
  const char *label
) {
  if (number) {
    char *string = usbGetString(device, number, 1000);
    if (string) {
      logMessage(LOG_INFO, "USB: %s: %s", label, string);
      free(string);
    }
  }
}

int
usbStringEquals (const char *reference, const char *value) {
  return strcmp(reference, value) == 0;
}

int
usbStringMatches (const char *reference, const char *value) {
  int ok = 0;

#ifdef REG_EXTENDED
  regex_t expression;

  if (regcomp(&expression, value, REG_EXTENDED|REG_NOSUB) == 0) {
    if (regexec(&expression, reference, 0, NULL, 0) == 0) {
      ok = 1;
    }

    regfree(&expression);
  }
#endif /* REG_EXTENDED */

  return ok;
}

int
usbVerifyString (
  UsbDevice *device,
  UsbStringVerifier verify,
  unsigned char index,
  const char *value
) {
  int ok = 0;
  if (!(value && *value)) return 1;

  if (index) {
    char *reference = usbGetString(device, index, 1000);
    if (reference) {
      if (verify(reference, value)) ok = 1;
      free(reference);
    }
  }
  return ok;
}

int
usbVerifyManufacturerName (UsbDevice *device, const char *eRegExp) {
  return usbVerifyString(device, usbStringMatches,
                         device->descriptor.iManufacturer, eRegExp);
}

int
usbVerifyProductDescription (UsbDevice *device, const char *eRegExp) {
  return usbVerifyString(device, usbStringMatches,
                         device->descriptor.iProduct, eRegExp);
}

int
usbVerifySerialNumber (UsbDevice *device, const char *string) {
  return usbVerifyString(device, usbStringEquals,
                         device->descriptor.iSerialNumber, string);
}

int
usbParseVendorIdentifier (uint16_t *identifier, const char *string) {
  if (string && *string) {
    unsigned int value;

    if (isUnsignedInteger(&value, string)) {
      if ((value > 0) && (value <= UINT16_MAX)) {
        *identifier = value;
        return 1;
      }
    }

    logMessage(LOG_WARNING, "invalid USB vendor identifier: %s", string);
    return 0;
  }

  *identifier = 0;
  return 1;
}

int
usbVerifyVendorIdentifier (const UsbDeviceDescriptor *descriptor, uint16_t identifier) {
  if (!identifier) return 1;
  return identifier == getLittleEndian16(descriptor->idVendor);
}

int
usbParseProductIdentifier (uint16_t *identifier, const char *string) {
  if (string && *string) {
    unsigned int value;

    if (isUnsignedInteger(&value, string)) {
      if ((value > 0) && (value <= UINT16_MAX)) {
        *identifier = value;
        return 1;
      }
    }

    logMessage(LOG_WARNING, "invalid USB product identifier: %s", string);
    return 0;
  }

  *identifier = 0;
  return 1;
}

int
usbVerifyProductIdentifier (const UsbDeviceDescriptor *descriptor, uint16_t identifier) {
  if (!identifier) return 1;
  return identifier == getLittleEndian16(descriptor->idProduct);
}

static int
usbVerifyStrings (
  UsbDevice *device,
  const char *const *strings,
  unsigned char number
) {
  if (!strings) return 1;
  if (!number) return 0;

  char *string = usbGetString(device, number, 1000);
  int matched = 0;

  if (string) {
    while (*strings) {
      if (strcmp(*strings, string) == 0) {
        matched = 1;
        break;
      }

      strings += 1;
    }

    free(string);
  }

  return matched;
}

const UsbDeviceDescriptor *
usbDeviceDescriptor (UsbDevice *device) {
  return &device->descriptor;
}

int
usbGetConfiguration (
  UsbDevice *device,
  unsigned char *configuration
) {
  ssize_t size = usbControlRead(device, UsbControlRecipient_Device, UsbControlType_Standard,
                                UsbStandardRequest_GetConfiguration, 0, 0,
                                configuration, sizeof(*configuration), 1000);
  if (size != -1) return 1;
  logMessage(LOG_WARNING, "USB standard request not supported: get configuration");
  return 0;
}

static void
usbDeallocateConfigurationDescriptor (UsbDevice *device) {
  if (device->configuration) {
    free(device->configuration);
    device->configuration = NULL;
  }
}

const UsbConfigurationDescriptor *
usbConfigurationDescriptor (
  UsbDevice *device
) {
  if (!device->configuration) {
    unsigned char current;

    if (device->descriptor.bNumConfigurations < 2) {
      current = 1;
    } else if (!usbGetConfiguration(device, &current)) {
      current = 0;
    }

    if (current) {
      UsbDescriptor descriptor;
      unsigned char number;

      for (number=0; number<device->descriptor.bNumConfigurations; number++) {
        int size = usbGetDescriptor(device, UsbDescriptorType_Configuration,
                                    number, 0, &descriptor, 1000);
        if (size == -1) {
          logMessage(LOG_WARNING, "USB configuration descriptor not readable: %d", number);
        } else if (descriptor.configuration.bConfigurationValue == current) {
          break;
        }
      }

      if (number < device->descriptor.bNumConfigurations) {
        int length = getLittleEndian16(descriptor.configuration.wTotalLength);
        UsbDescriptor *descriptors;

        if ((descriptors = malloc(length))) {
          ssize_t size;

          if (length > sizeof(descriptor)) {
            size = usbControlRead(device, UsbControlRecipient_Device, UsbControlType_Standard,
                                  UsbStandardRequest_GetDescriptor,
                                  (UsbDescriptorType_Configuration << 8) | number,
                                  0, descriptors, length, 1000);
          } else {
            memcpy(descriptors, &descriptor, (size = length));
          }

          if (size != -1) {
            device->configuration = &descriptors->configuration;
          } else {
            free(descriptors);
          }
        } else {
          logSystemError("USB configuration descriptor allocate");
        }
      } else {
        logMessage(LOG_ERR, "USB configuration descriptor not found: %d", current);
      }
    }
  }

  return device->configuration;
}

int
usbConfigureDevice (
  UsbDevice *device,
  unsigned char configuration
) {
  usbCloseInterface(device);

  if (usbSetConfiguration(device, configuration)) {
    usbDeallocateConfigurationDescriptor(device);
    return 1;
  }

  {
    const UsbConfigurationDescriptor *descriptor = usbConfigurationDescriptor(device);

    if (descriptor)
      if (descriptor->bConfigurationValue == configuration)
        return 1;
  }

  return 0;
}

int
usbNextDescriptor (
  UsbDevice *device,
  const UsbDescriptor **descriptor
) {
  if (*descriptor) {
    const UsbDescriptor *next = (UsbDescriptor *)&(*descriptor)->bytes[(*descriptor)->header.bLength];
    const UsbDescriptor *first = (UsbDescriptor *)device->configuration;
    unsigned int length = getLittleEndian16(first->configuration.wTotalLength);

    if ((&next->bytes[0] - &first->bytes[0]) >= length) return 0;
    if ((&next->bytes[next->header.bLength] - &first->bytes[0]) > length) return 0;

    *descriptor = next;
  } else if (usbConfigurationDescriptor(device)) {
    *descriptor = (UsbDescriptor *)device->configuration;
  } else {
    return 0;
  }

  return 1;
}

const UsbInterfaceDescriptor *
usbInterfaceDescriptor (
  UsbDevice *device,
  unsigned char interface,
  unsigned char alternative
) {
  const UsbDescriptor *descriptor = NULL;

  while (usbNextDescriptor(device, &descriptor)) {
    if (descriptor->interface.bDescriptorType == UsbDescriptorType_Interface) {
      if (descriptor->interface.bInterfaceNumber == interface) {
        if (descriptor->interface.bAlternateSetting == alternative) {
          return &descriptor->interface;
        }
      }
    }
  }

  logMessage(LOG_WARNING, "USB: interface descriptor not found: %d.%d", interface, alternative);
  errno = ENOENT;
  return NULL;
}

unsigned int
usbAlternativeCount (
  UsbDevice *device,
  unsigned char interface
) {
  unsigned int count = 0;
  const UsbDescriptor *descriptor = NULL;

  while (usbNextDescriptor(device, &descriptor)) {
    if (descriptor->interface.bDescriptorType == UsbDescriptorType_Interface) {
      if (descriptor->interface.bInterfaceNumber == interface) {
        count += 1;
      }
    }
  }

  return count;
}

const UsbEndpointDescriptor *
usbEndpointDescriptor (
  UsbDevice *device,
  unsigned char endpointAddress
) {
  const UsbDescriptor *descriptor = NULL;
  device->scratch.endpointInterfaceDescriptor = NULL;

  while (usbNextDescriptor(device, &descriptor)) {
    if (descriptor->header.bDescriptorType == UsbDescriptorType_Interface) {
      device->scratch.endpointInterfaceDescriptor = &descriptor->interface;
      continue;
    }

    if (descriptor->header.bDescriptorType == UsbDescriptorType_Endpoint) {
      if (descriptor->endpoint.bEndpointAddress == endpointAddress) {
        return &descriptor->endpoint;
      }
    }
  }

  logMessage(LOG_WARNING, "USB: endpoint descriptor not found: %02X", endpointAddress);
  errno = ENOENT;
  return NULL;
}

static void
usbCancelInputMonitor (UsbEndpoint *endpoint) {
  if (endpoint->direction.input.pipe.monitor) {
    asyncCancelRequest(endpoint->direction.input.pipe.monitor);
    endpoint->direction.input.pipe.monitor = NULL;
  }
}

static inline int
usbHaveInputPipe (UsbEndpoint *endpoint) {
  return endpoint->direction.input.pipe.output != INVALID_FILE_DESCRIPTOR;
}

static inline int
usbHaveInputError (UsbEndpoint *endpoint) {
  return endpoint->direction.input.pipe.input == INVALID_FILE_DESCRIPTOR;
}

void
usbSetEndpointInputError (UsbEndpoint *endpoint, int error) {
  if (!usbHaveInputError(endpoint)) {
    endpoint->direction.input.pipe.error = error;
    closeFile(&endpoint->direction.input.pipe.input);
  }
}

static int
usbSetInputError (void *item, void *data) {
  UsbEndpoint *endpoint = item;
  const int *error = data;

  if (usbHaveInputPipe(endpoint)) {
    usbSetEndpointInputError(endpoint, *error);
  }

  return 0;
}

void
usbSetDeviceInputError (UsbDevice *device, int error) {
  processQueue(device->endpoints, usbSetInputError, &error);
}

int
usbEnqueueInput (UsbEndpoint *endpoint, const void *buffer, size_t length) {
  if (usbHaveInputError(endpoint)) {
    errno = EIO;
    return 0;
  }

  return writeFile(endpoint->direction.input.pipe.input, buffer, length) != -1;
}

void
usbDestroyInputPipe (UsbEndpoint *endpoint) {
  usbCancelInputMonitor(endpoint);
  closeFile(&endpoint->direction.input.pipe.input);
  closeFile(&endpoint->direction.input.pipe.output);
}

int
usbMakeInputPipe (UsbEndpoint *endpoint) {
  if (usbHaveInputPipe(endpoint)) return 1;

  if (createAnonymousPipe(&endpoint->direction.input.pipe.input,
                          &endpoint->direction.input.pipe.output)) {
    setCloseOnExec(endpoint->direction.input.pipe.input, 1);
    setCloseOnExec(endpoint->direction.input.pipe.output, 1);

    if (setBlockingIo(endpoint->direction.input.pipe.output, 0)) {
      return 1;
    }
  }

  usbDestroyInputPipe(endpoint);
  return 0;
}

int
usbMonitorInputPipe (
  UsbDevice *device, unsigned char endpointNumber,
  AsyncMonitorCallback *callback, void *data
) {
  UsbEndpoint *endpoint = usbGetInputEndpoint(device, endpointNumber);

  if (endpoint) {
    if (usbHaveInputPipe(endpoint)) {
      usbCancelInputMonitor(endpoint);
      if (!callback) return 1;

      if (asyncMonitorFileInput(&endpoint->direction.input.pipe.monitor,
                                endpoint->direction.input.pipe.output,
                                callback, data)) {
        return 1;
      }
    }
  }

  return 0;
}

static void
usbDeallocateEndpoint (void *item, void *data) {
  UsbEndpoint *endpoint = item;

  switch (USB_ENDPOINT_DIRECTION(endpoint->descriptor)) {
    case UsbEndpointDirection_Input:
      if (endpoint->direction.input.pending.alarm) {
        asyncCancelRequest(endpoint->direction.input.pending.alarm);
        endpoint->direction.input.pending.alarm = NULL;
      }

      if (endpoint->direction.input.pending.requests) {
        deallocateQueue(endpoint->direction.input.pending.requests);
        endpoint->direction.input.pending.requests = NULL;
      }

      if (endpoint->direction.input.completed.request) {
        free(endpoint->direction.input.completed.request);
        endpoint->direction.input.completed.request = NULL;
      }

      break;

    default:
      break;
  }

  if (endpoint->extension) {
    usbDeallocateEndpointExtension(endpoint->extension);
    endpoint->extension = NULL;
  }

  switch (USB_ENDPOINT_DIRECTION(endpoint->descriptor)) {
    case UsbEndpointDirection_Input:
      usbDestroyInputPipe(endpoint);
      break;

    default:
      break;
  }

  free(endpoint);
}

static int
usbTestEndpoint (const void *item, void *data) {
  const UsbEndpoint *endpoint = item;
  const unsigned char *endpointAddress = data;
  return endpoint->descriptor->bEndpointAddress == *endpointAddress;
}

UsbEndpoint *
usbGetEndpoint (UsbDevice *device, unsigned char endpointAddress) {
  UsbEndpoint *endpoint = findItem(device->endpoints, usbTestEndpoint, &endpointAddress);
  if (endpoint) return endpoint;
  const UsbEndpointDescriptor *descriptor = usbEndpointDescriptor(device, endpointAddress);

  if (descriptor) {
    {
      const char *direction;
      const char *transfer;

      switch (USB_ENDPOINT_DIRECTION(descriptor)) {
        default:                          direction = "?";   break;
        case UsbEndpointDirection_Input:  direction = "in";  break;
        case UsbEndpointDirection_Output: direction = "out"; break;
      }

      switch (USB_ENDPOINT_TRANSFER(descriptor)) {
        default:                              transfer = "?";   break;
        case UsbEndpointTransfer_Control:     transfer = "ctl"; break;
        case UsbEndpointTransfer_Isochronous: transfer = "iso"; break;
        case UsbEndpointTransfer_Bulk:        transfer = "blk"; break;
        case UsbEndpointTransfer_Interrupt:   transfer = "int"; break;
      }

      logMessage(LOG_CATEGORY(USB_IO),
        "ept=%02X dir=%s xfr=%s pkt=%d ivl=%dms",
        descriptor->bEndpointAddress,
        direction, transfer,
        getLittleEndian16(descriptor->wMaxPacketSize),
        descriptor->bInterval
      );
    }

    if ((endpoint = malloc(sizeof(*endpoint)))) {
      memset(endpoint, 0, sizeof(*endpoint));

      endpoint->device = device;
      endpoint->interface = device->scratch.endpointInterfaceDescriptor;
      endpoint->descriptor = descriptor;
      endpoint->extension = NULL;
      endpoint->prepare = NULL;

      switch (USB_ENDPOINT_DIRECTION(endpoint->descriptor)) {
        case UsbEndpointDirection_Input:
          endpoint->direction.input.pending.requests = NULL;
          endpoint->direction.input.pending.alarm = NULL;
          endpoint->direction.input.pending.delay = 0;

          endpoint->direction.input.completed.request = NULL;
          endpoint->direction.input.completed.buffer = NULL;
          endpoint->direction.input.completed.length = 0;

          endpoint->direction.input.pipe.input = INVALID_FILE_DESCRIPTOR;
          endpoint->direction.input.pipe.output = INVALID_FILE_DESCRIPTOR;
          endpoint->direction.input.pipe.monitor = NULL;
          endpoint->direction.input.pipe.error = 0;

          break;
      }

      if (usbAllocateEndpointExtension(endpoint)) {
        if (enqueueItem(device->endpoints, endpoint)) {
          if (device->disableEndpointReset) {
            logMessage(LOG_CATEGORY(USB_IO), "endpoint reset disabled");
          } else {
            usbClearHalt(device, endpoint->descriptor->bEndpointAddress);
          }

          if (!endpoint->prepare || endpoint->prepare(endpoint)) return endpoint;
          deleteItem(device->endpoints, endpoint);
        }

        usbDeallocateEndpointExtension(endpoint->extension);
        usbDestroyInputPipe(endpoint);
      }

      free(endpoint);
    }
  }

  return NULL;
}

UsbEndpoint *
usbGetInputEndpoint (UsbDevice *device, unsigned char endpointNumber) {
  return usbGetEndpoint(device, endpointNumber|UsbEndpointDirection_Input);
}

UsbEndpoint *
usbGetOutputEndpoint (UsbDevice *device, unsigned char endpointNumber) {
  return usbGetEndpoint(device, endpointNumber|UsbEndpointDirection_Output);
}

static int
usbFinishEndpoint (void *item, void *data) {
  UsbEndpoint *endpoint = item;

  switch (USB_ENDPOINT_DIRECTION(endpoint->descriptor)) {
    case UsbEndpointDirection_Input:
      if (endpoint->direction.input.pending.requests) {
        deleteElements(endpoint->direction.input.pending.requests);
      }
      break;

    default:
      break;
  }

  return 0;
}

static void
usbRemoveEndpoints (UsbDevice *device, int final) {
  if (device->endpoints) {
    processQueue(device->endpoints, usbFinishEndpoint, NULL);
    deleteElements(device->endpoints);

    if (final) {
      deallocateQueue(device->endpoints);
      device->endpoints = NULL;
    }
  }
}

static void
usbDeallocateInputFilter (void *item, void *data) {
  UsbInputFilterEntry *entry = item;
  free(entry);
}

int
usbAddInputFilter (UsbDevice *device, UsbInputFilter *filter) {
  UsbInputFilterEntry *entry;

  if ((entry = malloc(sizeof(*entry)))) {
    memset(entry, 0, sizeof(*entry));
    entry->filter = filter;

    if (enqueueItem(device->inputFilters, entry)) return 1;

    free(entry);
  }

  return 0;
}

static int
usbApplyInputFilter (void *item, void *data) {
  UsbInputFilterEntry *entry = item;

  return !entry->filter(data);
}

int
usbApplyInputFilters (UsbEndpoint *endpoint, void *buffer, size_t size, ssize_t *length) {
  Queue *filters = endpoint->device->inputFilters;

  if (getQueueSize(filters) == 0) {
    usbLogEndpointData(endpoint, "input", buffer, *length);
  } else {
    usbLogEndpointData(endpoint, "unfiltered input", buffer, *length);

    UsbInputFilterData data = {
      .buffer = buffer,
      .size = size,
      .length = *length
    };

    if (processQueue(filters, usbApplyInputFilter, &data)) {
      errno = EIO;
      return 0;
    }

    *length = data.length;
    usbLogEndpointData(endpoint, "filtered input", buffer, *length);
  }

  return 1;
}

void
usbCloseInterface (
  UsbDevice *device
) {
  usbRemoveEndpoints(device, 0);

  if (device->interface) {
    usbReleaseInterface(device, device->interface->bInterfaceNumber);
    device->interface = NULL;
  }
}

int
usbOpenInterface (
  UsbDevice *device,
  unsigned char interface,
  unsigned char alternative
) {
  const UsbInterfaceDescriptor *descriptor = usbInterfaceDescriptor(device, interface, alternative);
  if (!descriptor) return 0;
  if (descriptor == device->interface) return 1;

  if (device->interface) {
    if (device->interface->bInterfaceNumber != interface) {
      usbCloseInterface(device);
    }
  }

  if (!device->interface) {
    if (!usbClaimInterface(device, interface)) {
      return 0;
    }
  }

  if (usbAlternativeCount(device, interface) == 1) goto done;

  {
    unsigned char response[1];
    ssize_t size = usbControlRead(device, UsbControlRecipient_Interface, UsbControlType_Standard,
                                  UsbStandardRequest_GetInterface, 0, interface,
                                  response, sizeof(response), 1000);

    if (size != -1) {
      if (response[0] == alternative) goto done;
    } else {
      logMessage(LOG_WARNING, "USB standard request not supported: get interface");
    }
  }

  if (usbSetAlternative(device, interface, alternative)) goto done;
  if (!device->interface) usbReleaseInterface(device, interface);
  return 0;

done:
  device->interface = descriptor;
  return 1;
}

void
usbCloseDevice (UsbDevice *device) {
  if (device->serial.operations) {
    const UsbSerialOperations *uso = device->serial.operations;
    if (uso->disableAdapter) uso->disableAdapter(device);
  }

  usbCloseInterface(device);
  usbRemoveEndpoints(device, 1);

  if (device->inputFilters) {
    deallocateQueue(device->inputFilters);
    device->inputFilters = NULL;
  }

  if (device->serial.data) {
    device->serial.operations->destroyData(device->serial.data);
    device->serial.data = NULL;
  }

  if (device->extension) {
    usbDeallocateDeviceExtension(device->extension);
    device->extension = NULL;
  }

  usbDeallocateConfigurationDescriptor(device);
  free(device);
}

static UsbDevice *
usbOpenDevice (UsbDeviceExtension *extension) {
  UsbDevice *device;

  if ((device = malloc(sizeof(*device)))) {
    memset(device, 0, sizeof(*device));
    device->extension = extension;
    device->serial.operations = NULL;
    device->serial.data = NULL;
    device->resetDevice = 0;
    device->disableEndpointReset = 0;

    if ((device->endpoints = newQueue(usbDeallocateEndpoint, NULL))) {
      if ((device->inputFilters = newQueue(usbDeallocateInputFilter, NULL))) {
        if (usbReadDeviceDescriptor(device)) {
          if (device->descriptor.bDescriptorType == UsbDescriptorType_Device) {
            if (device->descriptor.bLength == UsbDescriptorSize_Device) {
              return device;
            }
          }
        }

        deallocateQueue(device->inputFilters);
      }

      usbRemoveEndpoints(device, 1);
    }
    free(device);
  }

  logSystemError("USB device open");
  return NULL;
}

UsbDevice *
usbTestDevice (UsbDeviceExtension *extension, UsbDeviceChooser *chooser, UsbChooseChannelData *data) {
  UsbDevice *device;

  if ((device = usbOpenDevice(extension))) {
    logMessage(LOG_CATEGORY(USB_IO),
      "testing device: vendor=%04X product=%04X",
      getLittleEndian16(device->descriptor.idVendor),
      getLittleEndian16(device->descriptor.idProduct)
    );

    if (chooser(device, data)) {
      usbLogString(device, device->descriptor.iManufacturer, "Manufacturer Name");
      usbLogString(device, device->descriptor.iProduct, "Product Description");
      usbLogString(device, device->descriptor.iSerialNumber, "Serial Number");
      return device;
    }

    errno = ENOENT;
    device->extension = NULL;
    usbCloseDevice(device);
  }

  return NULL;
}

void
usbLogInputProblem (UsbEndpoint *endpoint, const char *problem) {
  logMessage(LOG_WARNING, "USB input: %s: Ept:%02X",
             problem, endpoint->descriptor->bEndpointAddress);
}

static void
usbDeallocatePendingInputRequest (void *item, void *data) {
  void *request = item;
  UsbEndpoint *endpoint = data;
  usbCancelRequest(endpoint->device, request);
}

static Element *
usbAddPendingInputRequest (UsbEndpoint *endpoint) {
  void *request = usbSubmitRequest(endpoint->device,
                                   endpoint->descriptor->bEndpointAddress,
                                   NULL,
                                   getLittleEndian16(endpoint->descriptor->wMaxPacketSize),
                                   endpoint);

  if (request) {
    Element *element = enqueueItem(endpoint->direction.input.pending.requests, request);

    if (element) return element;
    usbCancelRequest(endpoint->device, request);
  }

  return NULL;
}

static void
usbEnsurePendingInputRequests (UsbEndpoint *endpoint, int count) {
  int limit = USB_INPUT_INTERRUPT_REQUESTS_MAXIMUM;
  if ((count < 1) || (count > limit)) count = limit;
  endpoint->direction.input.pending.delay = 0;

  while (getQueueSize(endpoint->direction.input.pending.requests) < count) {
    if (!usbAddPendingInputRequest(endpoint)) {
      break;
    }
  }
}

ASYNC_ALARM_CALLBACK(usbHandleSchedulePendingInputRequest) {
  UsbEndpoint *endpoint = parameters->data;

  asyncDiscardHandle(endpoint->direction.input.pending.alarm);
  endpoint->direction.input.pending.alarm = NULL;

  usbAddPendingInputRequest(endpoint);
}

static void
usbSchedulePendingInputRequest (UsbEndpoint *endpoint) {
  if (!endpoint->direction.input.pending.alarm) {
    int *delay = &endpoint->direction.input.pending.delay;

    if (!*delay) *delay = 1;
    *delay = MIN(*delay, USB_INPUT_INTERRUPT_DELAY_MAXIMUM);

    asyncNewRelativeAlarm(&endpoint->direction.input.pending.alarm, *delay,
                          usbHandleSchedulePendingInputRequest, endpoint);

    *delay += 1;
  }
}

int
usbHandleInputResponse (UsbEndpoint *endpoint, const void *buffer, size_t length) {
  int requestsLeft = getQueueSize(endpoint->direction.input.pending.requests);

  if (length > 0) {
    if (!usbEnqueueInput(endpoint, buffer, length)) {
      usbLogInputProblem(endpoint, "data not enqueued");
      return 0;
    }

    usbEnsurePendingInputRequests(endpoint, requestsLeft+2);
    return 1;
  }

  if (requestsLeft == 0) {
    usbSchedulePendingInputRequest(endpoint);
  }

  return 1;
}

void
usbBeginInput (
  UsbDevice *device,
  unsigned char endpointNumber
) {
  UsbEndpoint *endpoint = usbGetInputEndpoint(device, endpointNumber);

  if (endpoint) {
    if (!endpoint->direction.input.pending.requests) {
      if ((endpoint->direction.input.pending.requests = newQueue(usbDeallocatePendingInputRequest, NULL))) {
        setQueueData(endpoint->direction.input.pending.requests, endpoint);
      }
    }

    if (endpoint->direction.input.pending.requests) {
      usbEnsurePendingInputRequests(endpoint, 0);
    }
  }
}

static int
usbGetPollInterval (UsbEndpoint *endpoint) {
  int interval = endpoint->descriptor->bInterval;

  if (interval > 0) {
    if (getLittleEndian16(endpoint->device->descriptor.bcdUSB) >= UsbSpecificationVersion_2_0) {
      interval = (1 << (interval - 1)) / 8;
    }
  }

  return interval;
}

int
usbAwaitInput (
  UsbDevice *device,
  unsigned char endpointNumber,
  int timeout
) {
  UsbEndpoint *endpoint;
  int retryInterval;

  if (!(endpoint = usbGetInputEndpoint(device, endpointNumber))) {
    return 0;
  }

  if (usbHaveInputPipe(endpoint)) {
    if (usbHaveInputError(endpoint)) {
      errno = endpoint->direction.input.pipe.error;
      return 0;
    }

    return awaitFileInput(endpoint->direction.input.pipe.output, timeout);
  }

  if (endpoint->direction.input.completed.request) {
    return 1;
  }

  if (!timeout) {
    errno = EAGAIN;
    return 0;
  }

  retryInterval = usbGetPollInterval(endpoint);
  retryInterval = MAX(USB_INPUT_AWAIT_RETRY_INTERVAL_MINIMUM, retryInterval);

  if (!(endpoint->direction.input.pending.requests && getQueueSize(endpoint->direction.input.pending.requests))) {
    int size = getLittleEndian16(endpoint->descriptor->wMaxPacketSize);
    unsigned char *buffer = malloc(size);

    if (buffer) {
      TimePeriod period;
      startTimePeriod(&period, timeout);

      while (1) {
        ssize_t count = usbReadEndpoint(device, endpointNumber, buffer, size, 20);

        if (count != -1) {
          if (count) {
            endpoint->direction.input.completed.request = buffer;
            endpoint->direction.input.completed.buffer = buffer;
            endpoint->direction.input.completed.length = count;
            return 1;
          }

          errno = EAGAIN;
        }

#ifdef ETIMEDOUT
        if (errno == ETIMEDOUT) errno = EAGAIN;
#endif /* ETIMEDOUT */

        if (errno != EAGAIN) break;
        if (afterTimePeriod(&period, NULL)) break;
        asyncWait(retryInterval);
      }

      free(buffer);
    } else {
      logMallocError();
    }

    return 0;
  }

  {
    TimePeriod period;
    startTimePeriod(&period, timeout);

    while (1) {
      UsbResponse response;
      void *request;

      while (!(request = usbReapResponse(device,
                                         endpointNumber | UsbEndpointDirection_Input,
                                         &response, 0))) {
        if (errno != EAGAIN) return 0;
        if (afterTimePeriod(&period, NULL)) return 0;
        asyncWait(retryInterval);
      }

      usbAddPendingInputRequest(endpoint);
      deleteItem(endpoint->direction.input.pending.requests, request);

      if (response.count > 0) {
        endpoint->direction.input.completed.request = request;
        endpoint->direction.input.completed.buffer = response.buffer;
        endpoint->direction.input.completed.length = response.count;
        return 1;
      }

      free(request);
    }
  }
}

ssize_t
usbReadData (
  UsbDevice *device,
  unsigned char endpointNumber,
  void *buffer,
  size_t length,
  int initialTimeout,
  int subsequentTimeout
) {
  UsbEndpoint *endpoint = usbGetInputEndpoint(device, endpointNumber);

  if (endpoint) {
    unsigned char *bytes = buffer;
    unsigned char *target = bytes;

    if (usbHaveInputPipe(endpoint)) {
      if (usbHaveInputError(endpoint)) {
        errno = endpoint->direction.input.pipe.error;
        endpoint->direction.input.pipe.error = EAGAIN;
        return -1;
      }

      return readFile(endpoint->direction.input.pipe.output, buffer, length, initialTimeout, subsequentTimeout);
    }

    while (length > 0) {
      int timeout = (target != bytes)? subsequentTimeout:
                    initialTimeout? initialTimeout:
                    USB_INPUT_READ_INITIAL_TIMEOUT_DEFAULT;

      if (!usbAwaitInput(device, endpointNumber, timeout)) {
        if (errno == EAGAIN) break;
        return -1;
      }

      {
        size_t count = endpoint->direction.input.completed.length;

        if (length < count) count = length;
        memcpy(target, endpoint->direction.input.completed.buffer, count);

        if ((endpoint->direction.input.completed.length -= count)) {
          endpoint->direction.input.completed.buffer += count;
        } else {
          endpoint->direction.input.completed.buffer = NULL;
          free(endpoint->direction.input.completed.request);
          endpoint->direction.input.completed.request = NULL;
        }

        target += count;
        length -= count;
      }
    }

    return target - bytes;
  }

  return -1;
}

ssize_t
usbWriteData (
  UsbDevice *device,
  unsigned char endpointNumber,
  const void *data,
  size_t length,
  int timeout
) {
  UsbEndpoint *endpoint = usbGetOutputEndpoint(device, endpointNumber);

  if (endpoint) {
    const uint16_t size = getLittleEndian16(endpoint->descriptor->wMaxPacketSize);
    const unsigned char *from = data;
    const unsigned char *const end = from + length;

    while (from < end) {
      size_t count = MIN((end - from), size);
      ssize_t result = usbWriteEndpoint(device, endpointNumber, from, count, timeout);

      if (result == -1) return result;
      from += result;
    }

    return length;
  }

  return -1;
}

static int
usbPrepareChannel (UsbChannel *channel) {
  const UsbChannelDefinition *definition = channel->definition;
  UsbDevice *device = channel->device;

  device->resetDevice = definition->resetDevice;
  device->disableEndpointReset = definition->disableEndpointReset;

  if (definition->disableAutosuspend) {
    logMessage(LOG_CATEGORY(USB_IO), "disabling autosuspend");
    usbDisableAutosuspend(device);
  }

  if (device->resetDevice) {
    usbResetDevice(device);
  }

  if (usbConfigureDevice(device, definition->configuration)) {
    if (usbOpenInterface(device, definition->interface, definition->alternative)) {
      int ok = 1;

      if (ok) {
        if (!usbSetSerialOperations(device)) {
          ok = 0;
        } else if (device->serial.operations) {
          logMessage(LOG_CATEGORY(USB_IO), "serial adapter: %s",
                     device->serial.operations->name);
        }
      }

      if (ok) {
        if (device->serial.operations) {
          if (device->serial.operations->enableAdapter) {
            if (!device->serial.operations->enableAdapter(device)) {
              ok = 0;
            }
          }
        }
      }

      if (ok) {
        if (definition->serial) {
          if (!usbSetSerialParameters(device, definition->serial)) {
            ok = 0;
          }
        }
      }

      if (ok) {
        if (definition->inputEndpoint) {
          UsbEndpoint *endpoint = usbGetInputEndpoint(device, definition->inputEndpoint);

          if (!endpoint) {
            ok = 0;
          } else if ((USB_ENDPOINT_TRANSFER(endpoint->descriptor) == UsbEndpointTransfer_Interrupt) ||
                     usbHaveInputPipe(endpoint)) {
            usbBeginInput(device, definition->inputEndpoint);
          }
        }
      }

      if (ok) {
        if (definition->outputEndpoint) {
          UsbEndpoint *endpoint = usbGetOutputEndpoint(device, definition->outputEndpoint);

          if (!endpoint) {
            ok = 0;
          }
        }
      }

      if (ok) return 1;
      usbCloseInterface(device);
    }
  }

  return 0;
}

static int
usbVerifyInterface (UsbDevice *device, const UsbChannelDefinition *definition) {
  const UsbInterfaceDescriptor *interface = usbInterfaceDescriptor(device, definition->interface, definition->alternative);
  if (!interface) return 0;

  BITMASK(endpoints, 0X100, char);
  BITMASK_ZERO(endpoints);

  {
    const UsbDescriptor *descriptor = (const UsbDescriptor *)interface;

    while (usbNextDescriptor(device, &descriptor)) {
      uint8_t type = descriptor->header.bDescriptorType;
      if (type == UsbDescriptorType_Interface) break;
      if (type != UsbDescriptorType_Endpoint) continue;
      BITMASK_SET(endpoints, descriptor->endpoint.bEndpointAddress);
    }
  }

  if (definition->inputEndpoint) {
    if (!BITMASK_TEST(endpoints, (definition->inputEndpoint | UsbEndpointDirection_Input))) {
      return 0;
    }
  }

  if (definition->outputEndpoint) {
    if (!BITMASK_TEST(endpoints, (definition->outputEndpoint | UsbEndpointDirection_Output))) {
      return 0;
    }
  }

  return 1;
}

struct UsbChooseChannelDataStruct {
  const UsbChannelDefinition *definition;

  const char *serialNumber;
  uint16_t vendorIdentifier;
  uint16_t productIdentifier;
  unsigned genericDevices:1;
};

static int
usbChooseChannel (UsbDevice *device, UsbChooseChannelData *data) {
  const UsbDeviceDescriptor *descriptor = &device->descriptor;
  logBytes(LOG_CATEGORY(USB_IO), "device descriptor", descriptor, sizeof(*descriptor));

  if (!(descriptor->iManufacturer || descriptor->iProduct || descriptor->iSerialNumber)) {
    UsbDeviceDescriptor actualDescriptor;
    ssize_t result = usbGetDeviceDescriptor(device, &actualDescriptor);

    if (result == UsbDescriptorSize_Device) {
      device->descriptor = actualDescriptor;

      logBytes(LOG_CATEGORY(USB_IO),
        "using actual device descriptor",
        descriptor, sizeof(*descriptor)
      );
    }
  }

  {
    uint16_t vendor = getLittleEndian16(descriptor->idVendor);
    uint16_t product = getLittleEndian16(descriptor->idProduct);

    const char *const *drivers = usbGetDriverCodes(vendor, product);
    if (!drivers) return 0;
  }

  for (const UsbChannelDefinition *definition = data->definition;
       definition->vendor; definition+=1) {
    if (definition->version && (definition->version != getLittleEndian16(descriptor->bcdUSB))) continue;
    if (!USB_IS_PRODUCT(descriptor, definition->vendor, definition->product)) continue;

    if (!data->genericDevices) {
      const UsbSerialAdapter *adapter = usbFindSerialAdapter(descriptor);
      if (adapter && adapter->generic) continue;
    }

    if (!usbVerifyVendorIdentifier(descriptor, data->vendorIdentifier)) continue;
    if (!usbVerifyProductIdentifier(descriptor, data->productIdentifier)) continue;
    if (!usbVerifySerialNumber(device, data->serialNumber)) continue;

    if (!usbVerifyStrings(device, definition->manufacturers, descriptor->iManufacturer)) continue;
    if (!usbVerifyStrings(device, definition->products, descriptor->iProduct)) continue;

    if (definition->verifyInterface) {
      if (!usbConfigureDevice(device, definition->configuration)) continue;
      if (!usbVerifyInterface(device, definition)) continue;
    }

    data->definition = definition;
    return 1;
  }

  return 0;
}

static UsbChannel *
usbNewChannel (UsbChooseChannelData *data) {
  UsbChannel *channel;

  if ((channel = malloc(sizeof(*channel)))) {
    memset(channel, 0, sizeof(*channel));

    if ((channel->device = usbFindDevice(usbChooseChannel, data))) {
      channel->definition = data->definition;
      return channel;
    }

    free(channel);
  } else {
    logMallocError();
  }

  return NULL;
}

typedef enum {
  USB_PARM_SERIAL_NUMBER,
  USB_PARM_VENDOR_IDENTIFIER,
  USB_PARM_PRODUCT_IDENTIFIER,
  USB_PARM_GENERIC_DEVICES
} UsbDeviceParameter;

static const char *const usbDeviceParameterNames[] = {
  "serialNumber",
  "vendorIdentifier",
  "productIdentifier",
  "genericDevices",
  NULL
};

static char **
usbGetDeviceParameters (const char *identifier) {
  if (!identifier) identifier = "";
  return getDeviceParameters(usbDeviceParameterNames, identifier);
}

UsbChannel *
usbOpenChannel (const UsbChannelDefinition *definitions, const char *identifier) {
  UsbChannel *channel = NULL;
  char **parameters = usbGetDeviceParameters(identifier);

  if (parameters) {
    int ok = 1;

    UsbChooseChannelData choose = {
      .definition = definitions,
      .serialNumber = parameters[USB_PARM_SERIAL_NUMBER]
    };

    if (!usbParseVendorIdentifier(&choose.vendorIdentifier, parameters[USB_PARM_VENDOR_IDENTIFIER])) ok = 0;
    if (!usbParseProductIdentifier(&choose.productIdentifier, parameters[USB_PARM_PRODUCT_IDENTIFIER])) ok = 0;

    {
      const char *parameter = parameters[USB_PARM_GENERIC_DEVICES];

      if (!(parameter && *parameter)) {
        choose.genericDevices = 1;
      } else {
        unsigned int flag;

        if (validateYesNo(&flag, parameter)) {
          choose.genericDevices = flag;
        } else {
          logMessage(LOG_WARNING, "invalid generic devices option: %s", parameter);
          ok = 0;
        }
      }
    }

    if (ok) {
      if (!(channel = usbNewChannel(&choose))) {
        logMessage(LOG_CATEGORY(USB_IO), "device not found%s%s",
                   (*identifier? ": ": ""), identifier);
      }
    }

    deallocateStrings(parameters);
  }

  if (channel) {
    if (usbPrepareChannel(channel)) {
      return channel;
    }

    usbCloseChannel(channel);
  }

  return NULL;
}

void
usbCloseChannel (UsbChannel *channel) {
  usbCloseDevice(channel->device);
  free(channel);
}

const char *
usbMakeChannelIdentifier (UsbChannel *channel, char *buffer, size_t size) {
  UsbDevice *device = channel->device;

  UsbDeviceDescriptor descriptor;
  if (!usbGetDeviceDescriptor(device, &descriptor)) return NULL;

  size_t length;
  STR_BEGIN(buffer, size);
  STR_PRINTF("%s%c", USB_DEVICE_QUALIFIER, PARAMETER_QUALIFIER_CHARACTER);

  {
    uint16_t vendorIdentifier = getLittleEndian16(descriptor.idVendor);

    if (vendorIdentifier) {
      STR_PRINTF(
        "%s%c0X%04X%c",
        usbDeviceParameterNames[USB_PARM_VENDOR_IDENTIFIER],
        PARAMETER_ASSIGNMENT_CHARACTER,
        vendorIdentifier,
        DEVICE_PARAMETER_SEPARATOR
      );
    }
  }

  {
    uint16_t productIdentifier = getLittleEndian16(descriptor.idProduct);

    if (productIdentifier) {
      STR_PRINTF(
        "%s%c0X%04X%c",
        usbDeviceParameterNames[USB_PARM_PRODUCT_IDENTIFIER],
        PARAMETER_ASSIGNMENT_CHARACTER,
        productIdentifier,
        DEVICE_PARAMETER_SEPARATOR
      );
    }
  }

  {
    char *serialNumber = usbGetSerialNumber(device, 1000);

    if (serialNumber) {
      if (!strchr(serialNumber, DEVICE_PARAMETER_SEPARATOR)) {
        STR_PRINTF(
          "%s%c%s%c",
          usbDeviceParameterNames[USB_PARM_SERIAL_NUMBER],
          PARAMETER_ASSIGNMENT_CHARACTER,
          serialNumber,
          DEVICE_PARAMETER_SEPARATOR
        );
      }

      free(serialNumber);
    }
  }

  length = STR_LENGTH;
  STR_END;

  {
    char *last = &buffer[length] - 1;
    if (*last == DEVICE_PARAMETER_SEPARATOR) *last = 0;
  }

  return buffer;
}

static int
usbCompareDeviceEntries (const void *element1, const void *element2) {
  const UsbDeviceEntry *entry1 = element1;
  const UsbDeviceEntry *entry2 = element2;

  if (entry1->vendorIdentifier < entry2->vendorIdentifier) return -1;
  if (entry1->vendorIdentifier > entry2->vendorIdentifier) return 1;

  if (entry1->productIdentifier < entry2->productIdentifier) return -1;
  if (entry1->productIdentifier > entry2->productIdentifier) return 1;

  return 0;
}

static int
usbSearchDeviceEntry (const void *target, const void *element) {
  const UsbDeviceEntry *entry1 = target;
  const UsbDeviceEntry *entry2 = element;
  return usbCompareDeviceEntries(entry1, entry2);
}

const char *const *
usbGetDriverCodes (uint16_t vendor, uint16_t product) {
  const UsbDeviceEntry target = {
    .vendorIdentifier = vendor,
    .productIdentifier = product
  };

  const UsbDeviceEntry *entry = bsearch(&target, usbDeviceTable,
                                        usbDeviceCount,
                                        sizeof(usbDeviceTable[0]),
                                        usbSearchDeviceEntry);

  return entry? entry->driverCodes: NULL;
}

int
isUsbDeviceIdentifier (const char **identifier) {
  return hasQualifier(identifier, USB_DEVICE_QUALIFIER);
}
