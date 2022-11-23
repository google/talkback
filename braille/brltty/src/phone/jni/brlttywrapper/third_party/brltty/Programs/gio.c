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
#include "async_wait.h"
#include "async_alarm.h"
#include "io_generic.h"
#include "gio_internal.h"
#include "io_serial.h"

const GioProperties *const gioProperties[] = {
  &gioProperties_null,
  &gioProperties_serial,
  &gioProperties_usb,
  &gioProperties_bluetooth,
  NULL
};

static void
gioInitializeOptions (GioOptions *options) {
  options->applicationData = NULL;
  options->readyDelay = 0;
  options->inputTimeout = 0;
  options->outputTimeout = 0;
  options->requestTimeout = 0;
}

void
gioInitializeDescriptor (GioDescriptor *descriptor) {
  gioInitializeOptions(&descriptor->null.options);

  descriptor->serial.parameters = NULL;
  gioInitializeOptions(&descriptor->serial.options);
  descriptor->serial.options.inputTimeout = 100;

  descriptor->usb.channelDefinitions = NULL;
  descriptor->usb.setConnectionProperties = NULL;
  gioInitializeOptions(&descriptor->usb.options);
  descriptor->usb.options.inputTimeout = 1000;
  descriptor->usb.options.outputTimeout = 1000;
  descriptor->usb.options.requestTimeout = 1000;

  descriptor->bluetooth.channelNumber = 0;
  descriptor->bluetooth.discoverChannel = 0;
  gioInitializeOptions(&descriptor->bluetooth.options);
  descriptor->bluetooth.options.inputTimeout = 1000;
  descriptor->bluetooth.options.requestTimeout = 5000;
}

void
gioInitializeSerialParameters (SerialParameters *parameters) {
  parameters->baud = SERIAL_DEFAULT_BAUD;
  parameters->dataBits = SERIAL_DEFAULT_DATA_BITS;
  parameters->stopBits = SERIAL_DEFAULT_STOP_BITS;
  parameters->parity = SERIAL_DEFAULT_PARITY;
  parameters->flowControl = SERIAL_DEFAULT_FLOW_CONTROL;
}

void
gioSetBytesPerSecond (GioEndpoint *endpoint, const SerialParameters *parameters) {
  endpoint->bytesPerSecond = parameters->baud / serialGetCharacterSize(parameters);
}

static int
gioStartEndpoint (GioEndpoint *endpoint) {
  {
    int delay = endpoint->options.readyDelay;

    if (delay) asyncWait(delay);
  }

  return 1;
}

static const GioProperties *
gioGetProperties (
  const char **identifier,
  const GioDescriptor *descriptor
) {
  for (const GioProperties *const *properties = gioProperties;
       *properties; properties+=1) {
    if (descriptor) {
      GioIsSupportedMethod *isSupported = (*properties)->private->isSupported;

      if (!isSupported) {
        logUnsupportedOperation("isSupported");
        continue;
      }

      if (!isSupported(descriptor)) continue;
    }

    {
      GioTestIdentifierMethod *testIdentifier = (*properties)->public->testIdentifier;

      if (!testIdentifier) {
        logUnsupportedOperation("testIdentifier");
        continue;
      }

      if (testIdentifier(identifier)) return *properties;
    }
  }

  errno = ENOSYS;
  logMessage(LOG_WARNING, "unsupported generic resource identifier: %s", *identifier);
  return NULL;
}

const GioPublicProperties *
gioGetPublicProperties (const char **identifier) {
  const GioProperties *properties = gioGetProperties(identifier, NULL);
  if (properties == NULL) return NULL;
  return properties->public;
}

GioEndpoint *
gioConnectResource (
  const char *identifier,
  const GioDescriptor *descriptor
) {
  const GioProperties *properties = gioGetProperties(&identifier, descriptor);

  if (properties) {
    GioEndpoint *endpoint;

    if ((endpoint = malloc(sizeof(*endpoint)))) {
      endpoint->resourceType = properties->public->type.identifier;
      endpoint->bytesPerSecond = 0;

      endpoint->input.error = 0;
      endpoint->input.from = 0;
      endpoint->input.to = 0;

      endpoint->hidReportItems.address = NULL;
      endpoint->hidReportItems.size = 0;

      if (properties->private->getOptions) {
        endpoint->options = *properties->private->getOptions(descriptor);
      } else {
        gioInitializeOptions(&endpoint->options);
      }

      if (properties->private->getMethods) {
        endpoint->methods = properties->private->getMethods();
      } else {
        endpoint->methods = NULL;
      }

      if (properties->private->connectResource) {
        if ((endpoint->handle = properties->private->connectResource(identifier, descriptor))) {
          if (!properties->private->prepareEndpoint || properties->private->prepareEndpoint(endpoint)) {
            if (gioStartEndpoint(endpoint)) {
              return endpoint;
            }
          }

          {
            int originalErrno = errno;
            gioDisconnectResource(endpoint);
            errno = originalErrno;
          }

          return NULL;
        }
      } else {
        logUnsupportedOperation("connectResource");
      }

      free(endpoint);
    } else {
      logMallocError();
    }
  }

  return NULL;
}

int
gioDisconnectResource (GioEndpoint *endpoint) {
  int ok = 0;
  GioDisconnectResourceMethod *method = endpoint->methods->disconnectResource;

  if (!method) {
    logUnsupportedOperation("disconnectResource");
  } else if (method(endpoint->handle)) {
    ok = 1;
  }

  if (endpoint->hidReportItems.address) free(endpoint->hidReportItems.address);
  free(endpoint);
  return ok;
}

const void *
gioGetApplicationData (GioEndpoint *endpoint) {
  return endpoint->options.applicationData;
}

char *
gioGetResourceName (GioEndpoint *endpoint) {
  char *name = NULL;
  GioGetResourceNameMethod *method = endpoint->methods->getResourceName;

  if (!method) {
    logUnsupportedOperation("getResourceName");
  } else {
    name = method(endpoint->handle, endpoint->options.requestTimeout);
  }

  return name;
}

ssize_t
gioWriteData (GioEndpoint *endpoint, const void *data, size_t size) {
  GioWriteDataMethod *method = endpoint->methods->writeData;

  if (!method) {
    logUnsupportedOperation("writeData");
    return -1;
  }

  ssize_t result = method(endpoint->handle, data, size,
                          endpoint->options.outputTimeout);

  if (endpoint->options.ignoreWriteTimeouts) {
    if (result == -1) {
      if ((errno == EAGAIN)
#ifdef ETIMEDOUT
       || (errno == ETIMEDOUT)
#endif /* ETIMEDOUT */
      ) result = size;
    }
  }

  return result;
}

int
gioAwaitInput (GioEndpoint *endpoint, int timeout) {
  GioAwaitInputMethod *method = endpoint->methods->awaitInput;

  if (!method) {
    logUnsupportedOperation("awaitInput");
    return 0;
  }

  if (endpoint->input.to - endpoint->input.from) return 1;

  return method(endpoint->handle, timeout);
}

ssize_t
gioReadData (GioEndpoint *endpoint, void *buffer, size_t size, int wait) {
  GioReadDataMethod *method = endpoint->methods->readData;

  if (!method) {
    logUnsupportedOperation("readData");
    return -1;
  }

  {
    unsigned char *start = buffer;
    unsigned char *next = start;

    while (size) {
      {
        unsigned int count = endpoint->input.to - endpoint->input.from;

        if (count) {
          if (count > size) count = size;
          memcpy(next, &endpoint->input.buffer[endpoint->input.from], count);

          endpoint->input.from += count;
          next += count;
          size -= count;
          continue;
        }

        endpoint->input.from = endpoint->input.to = 0;
      }

      if (endpoint->input.error) {
        if (next != start) break;
        errno = endpoint->input.error;
        endpoint->input.error = 0;
        return -1;
      }

      {
        ssize_t result = method(endpoint->handle,
                                &endpoint->input.buffer[endpoint->input.to],
                                sizeof(endpoint->input.buffer) - endpoint->input.to,
                                (wait? endpoint->options.inputTimeout: 0), 0);

        if (result > 0) {
          logBytes(LOG_CATEGORY(GENERIC_INPUT), NULL, &endpoint->input.buffer[endpoint->input.to], result);
          endpoint->input.to += result;
          wait = 1;
        } else {
          if (!result) break;
          if (errno == EAGAIN) break;
          endpoint->input.error = errno;
        }
      }
    }

    if (next == start) errno = EAGAIN;
    return next - start;
  }
}

int
gioReadByte (GioEndpoint *endpoint, unsigned char *byte, int wait) {
  ssize_t result = gioReadData(endpoint, byte, 1, wait);
  if (result > 0) return 1;
  if (result == 0) errno = EAGAIN;
  return 0;
}

int
gioDiscardInput (GioEndpoint *endpoint) {
  unsigned char byte;
  while (gioReadByte(endpoint, &byte, 0));
  return errno == EAGAIN;
}

int
gioReconfigureResource (
  GioEndpoint *endpoint,
  const SerialParameters *parameters
) {
  int ok = 1;
  GioReconfigureResourceMethod *method = endpoint->methods->reconfigureResource;

  if (!method) {
    logUnsupportedOperation("reconfigureResource");
  } else if (method(endpoint->handle, parameters)) {
    gioSetBytesPerSecond(endpoint, parameters);
  } else {
    ok = 0;
  }

  return ok;
}

unsigned int
gioGetBytesPerSecond (GioEndpoint *endpoint) {
  return endpoint->bytesPerSecond;
}

unsigned int
gioGetMillisecondsToTransfer (GioEndpoint *endpoint, size_t bytes) {
  return endpoint->bytesPerSecond? (((bytes * 1000) / endpoint->bytesPerSecond) + 1): 0;
}

ssize_t
gioTellResource (
  GioEndpoint *endpoint,
  uint8_t recipient, uint8_t type,
  uint8_t request, uint16_t value, uint16_t index,
  const void *data, uint16_t size
) {
  GioTellResourceMethod *method = endpoint->methods->tellResource;

  if (!method) {
    logUnsupportedOperation("tellResource");
    return -1;
  }

  return method(endpoint->handle, recipient, type,
                request, value, index, data, size,
                endpoint->options.requestTimeout);
}

ssize_t
gioAskResource (
  GioEndpoint *endpoint,
  uint8_t recipient, uint8_t type,
  uint8_t request, uint16_t value, uint16_t index,
  void *buffer, uint16_t size
) {
  GioAskResourceMethod *method = endpoint->methods->askResource;

  if (!method) {
    logUnsupportedOperation("askResource");
    return -1;
  }

  return method(endpoint->handle, recipient, type,
                request, value, index, buffer, size,
                endpoint->options.requestTimeout);
}

size_t
gioGetHidReportSize (GioEndpoint *endpoint, unsigned char report) {
  if (!endpoint->hidReportItems.address) {
    GioGetHidReportItemsMethod *method = endpoint->methods->getHidReportItems;

    if (!method) {
      logUnsupportedOperation("getHidReportItems");
      return 0;
    }

    if (!method(endpoint->handle, &endpoint->hidReportItems,
                endpoint->options.requestTimeout)) {
      return 0;
    }
  }

  {
    GioGetHidReportSizeMethod *method = endpoint->methods->getHidReportSize;

    if (!method) {
      logUnsupportedOperation("getHidReportSize");
      return 0;
    }

    return method(&endpoint->hidReportItems, report);
  }
}

ssize_t
gioSetHidReport (
  GioEndpoint *endpoint, unsigned char report,
  const void *data, uint16_t size
) {
  GioSetHidReportMethod *method = endpoint->methods->setHidReport;

  if (!method) {
    logUnsupportedOperation("setHidReport");
    return -1;
  }

  return method(endpoint->handle, report,
                data, size, endpoint->options.requestTimeout);
}

ssize_t
gioWriteHidReport (
  GioEndpoint *endpoint,
  const unsigned char *data, uint16_t size
) {
  return gioSetHidReport(endpoint, data[0], data, size);
}

ssize_t
gioGetHidReport (
  GioEndpoint *endpoint, unsigned char report,
  void *buffer, uint16_t size
) {
  GioGetHidReportMethod *method = endpoint->methods->getHidReport;

  if (!method) {
    logUnsupportedOperation("getHidReport");
    return -1;
  }

  return method(endpoint->handle, report,
                buffer, size, endpoint->options.requestTimeout);
}

ssize_t
gioSetHidFeature (
  GioEndpoint *endpoint, unsigned char report,
  const void *data, uint16_t size
) {
  GioSetHidFeatureMethod *method = endpoint->methods->setHidFeature;

  if (!method) {
    logUnsupportedOperation("setHidFeature");
    return -1;
  }

  return method(endpoint->handle, report,
                data, size, endpoint->options.requestTimeout);
}

ssize_t
gioWriteHidFeature (
  GioEndpoint *endpoint,
  const unsigned char *data, uint16_t size
) {
  return gioSetHidFeature(endpoint, data[0], data, size);
}

ssize_t
gioGetHidFeature (
  GioEndpoint *endpoint, unsigned char report,
  void *buffer, uint16_t size
) {
  GioGetHidFeatureMethod *method = endpoint->methods->getHidFeature;

  if (!method) {
    logUnsupportedOperation("getHidFeature");
    return -1;
  }

  return method(endpoint->handle, report,
                buffer, size, endpoint->options.requestTimeout);
}

int
gioMonitorInput (GioEndpoint *endpoint, AsyncMonitorCallback *callback, void *data) {
  GioMonitorInputMethod *method = endpoint->methods->monitorInput;

  if (method) {
    if (method(endpoint->handle, callback, data)) {
      return 1;
    }
  }

  return 0;
}

struct GioHandleInputObjectStruct {
  GioEndpoint *endpoint;
  AsyncHandle pollAlarm;

  GioInputHandler *handler;
  void *data;
};

static int
handleInput (GioHandleInputObject *hio, int error) {
  GioHandleInputParameters parameters = {
    .error = error,
    .data = hio->data
  };

  return hio->handler(&parameters);
}

ASYNC_MONITOR_CALLBACK(handleInputMonitor) {
  GioHandleInputObject *hio = parameters->data;

  handleInput(hio, parameters->error);
  return 1;
}

ASYNC_ALARM_CALLBACK(handleInputAlarm) {
  GioHandleInputObject *hio = parameters->data;

  if (handleInput(hio, 0)) asyncResetAlarmIn(hio->pollAlarm, 0);
}

GioHandleInputObject *
gioNewHandleInputObject (
  GioEndpoint *endpoint, int pollInterval,
  GioInputHandler *handler, void *data
) {
  GioHandleInputObject *hio;

  if ((hio = malloc(sizeof(*hio)))) {
    memset(hio, 0, sizeof(*hio));

    hio->endpoint = endpoint;
    hio->pollAlarm = NULL;

    hio->handler = handler;
    hio->data = data;

    if (endpoint) {
      if (gioMonitorInput(endpoint, handleInputMonitor, hio)) {
        handleInput(hio, 0);
        return hio;
      }
    }

    if (asyncNewRelativeAlarm(&hio->pollAlarm, 0, handleInputAlarm, hio)) {
      if (asyncResetAlarmEvery(hio->pollAlarm, pollInterval)) {
        return hio;
      }

      asyncCancelRequest(hio->pollAlarm);
    }

    free(hio);
  } else {
    logMallocError();
  }

  return NULL;
}

void
gioDestroyHandleInputObject (GioHandleInputObject *hio) {
  if (hio->pollAlarm) {
    asyncCancelRequest(hio->pollAlarm);
  } else {
    gioMonitorInput(hio->endpoint, NULL, NULL);
  }

  free(hio);
}

GioTypeIdentifier
gioGetResourceType (GioEndpoint *endpoint) {
  return endpoint->resourceType;
}

void *
gioGetResourceObject (GioEndpoint *endpoint) {
  GioGetResourceObjectMethod *method = endpoint->methods->getResourceObject;

  if (method) return method(endpoint->handle);
  logUnsupportedOperation("getResourceObject");
  return NULL;
}
