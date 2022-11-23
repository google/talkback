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

#include <stdio.h>
#include <errno.h>
#include <mach/mach.h>
#include <IOKit/IOKitLib.h>
#include <IOKit/IOCFPlugIn.h>
#include <IOKit/usb/IOUSBLib.h>

#include "log.h"
#include "io_usb.h"
#include "usb_internal.h"
#include "system_darwin.h"

typedef struct {
  UsbEndpoint *endpoint;
  void *context;
  void *buffer;
  size_t length;

  IOReturn result;
  ssize_t count;
} UsbAsynchronousRequest;

struct UsbDeviceExtensionStruct {
  IOUSBDeviceInterface182 **device;
  unsigned deviceOpened:1;

  IOUSBInterfaceInterface190 **interface;
  unsigned interfaceOpened:1;
  UInt8 pipeCount;

  CFRunLoopSourceRef runloopSource;
};

struct UsbEndpointExtensionStruct {
  UsbEndpoint *endpoint;
  Queue *completedRequests;

  UInt8 pipeNumber;
  UInt8 endpointNumber;
  UInt8 transferDirection;
  UInt8 transferMode;
  UInt8 pollInterval;
  UInt16 packetSize;
};

static void
setUsbError (long int result, const char *action) {
  switch (result) {
    default: setDarwinSystemError(result); break;

  //MAP_DARWIN_ERROR(kIOUSBUnknownPipeErr, )
  //MAP_DARWIN_ERROR(kIOUSBTooManyPipesErr, )
  //MAP_DARWIN_ERROR(kIOUSBNoAsyncPortErr, )
  //MAP_DARWIN_ERROR(kIOUSBNotEnoughPipesErr, )
  //MAP_DARWIN_ERROR(kIOUSBNotEnoughPowerErr, )
  //MAP_DARWIN_ERROR(kIOUSBEndpointNotFound, )
  //MAP_DARWIN_ERROR(kIOUSBConfigNotFound, )
    MAP_DARWIN_ERROR(kIOUSBTransactionTimeout, ETIMEDOUT)
  //MAP_DARWIN_ERROR(kIOUSBTransactionReturned, )
  //MAP_DARWIN_ERROR(kIOUSBPipeStalled, )
  //MAP_DARWIN_ERROR(kIOUSBInterfaceNotFound, )
  //MAP_DARWIN_ERROR(kIOUSBLowLatencyBufferNotPreviouslyAllocated, )
  //MAP_DARWIN_ERROR(kIOUSBLowLatencyFrameListNotPreviouslyAllocated, )
  //MAP_DARWIN_ERROR(kIOUSBHighSpeedSplitError, )
  //MAP_DARWIN_ERROR(kIOUSBLinkErr, )
  //MAP_DARWIN_ERROR(kIOUSBNotSent2Err, )
  //MAP_DARWIN_ERROR(kIOUSBNotSent1Err, )
  //MAP_DARWIN_ERROR(kIOUSBBufferUnderrunErr, )
  //MAP_DARWIN_ERROR(kIOUSBBufferOverrunErr, )
  //MAP_DARWIN_ERROR(kIOUSBReserved2Err, )
  //MAP_DARWIN_ERROR(kIOUSBReserved1Err, )
  //MAP_DARWIN_ERROR(kIOUSBWrongPIDErr, )
  //MAP_DARWIN_ERROR(kIOUSBPIDCheckErr, )
  //MAP_DARWIN_ERROR(kIOUSBDataToggleErr, )
  //MAP_DARWIN_ERROR(kIOUSBBitstufErr, )
  //MAP_DARWIN_ERROR(kIOUSBCRCErr, )
  }

  if (action) {
    logMessage(LOG_WARNING, "Darwin error 0X%lX.", result);
    logSystemError(action);
  }
}

static int
openDevice (UsbDevice *device, int seize) {
  UsbDeviceExtension *devx = device->extension;
  IOReturn result;

  if (!devx->deviceOpened) {
    const char *action = "opened";
    int level = LOG_INFO;

    result = (*devx->device)->USBDeviceOpen(devx->device);
    if (result != kIOReturnSuccess) {
      setUsbError(result, "USB device open");
      if ((result != kIOReturnExclusiveAccess) || !seize) return 0;

      result = (*devx->device)->USBDeviceOpenSeize(devx->device);
      if (result != kIOReturnSuccess) {
        setUsbError(result, "USB device seize");
        return 0;
      }

      action = "seized";
      level = LOG_NOTICE;
    }

    logMessage(level, "USB device %s: vendor=%04X product=%04X",
               action, device->descriptor.idVendor, device->descriptor.idProduct);
    devx->deviceOpened = 1;
  }

  return 1;
}

static int
unsetInterface (UsbDeviceExtension *devx) {
  int ok = 1;

  if (devx->interface) {
    IOReturn result;

    if (devx->interfaceOpened) {
      if (devx->runloopSource) {
        {
          int pipe;
          for (pipe=1; pipe<=devx->pipeCount; ++pipe) {
            result = (*devx->interface)->AbortPipe(devx->interface, pipe);
            if (result != kIOReturnSuccess) {
              setUsbError(result, "USB pipe abort");
            }
          }
        }

        removeRunLoopSource(devx->runloopSource);
      }

      result = (*devx->interface)->USBInterfaceClose(devx->interface);
      if (result != kIOReturnSuccess) {
        setUsbError(result, "USB interface close");
        ok = 0;
      }
      devx->interfaceOpened = 0;
    }

    (*devx->interface)->Release(devx->interface);
    devx->interface = NULL;
  }

  return ok;
}

static int
isInterface (IOUSBInterfaceInterface190 **interface, UInt8 number) {
  IOReturn result;
  UInt8 num;

  result = (*interface)->GetInterfaceNumber(interface, &num);
  if (result != kIOReturnSuccess) {
    setUsbError(result, "USB interface number query");
  } else if (num == number) {
    return 1;
  }

  return 0;
}

static int
setInterface (UsbDeviceExtension *devx, UInt8 number) {
  int found = 0;
  IOReturn result;
  io_iterator_t iterator = 0;

  if (devx->interface)
    if (isInterface(devx->interface, number))
      return 1;

  {
    IOUSBFindInterfaceRequest request;

    request.bInterfaceClass = kIOUSBFindInterfaceDontCare;
    request.bInterfaceSubClass = kIOUSBFindInterfaceDontCare;
    request.bInterfaceProtocol = kIOUSBFindInterfaceDontCare;
    request.bAlternateSetting = kIOUSBFindInterfaceDontCare;

    result = (*devx->device)->CreateInterfaceIterator(devx->device, &request, &iterator);
  }

  if ((result == kIOReturnSuccess) && iterator) {
    io_service_t service;

    while ((service = IOIteratorNext(iterator))) {
      IOCFPlugInInterface **plugin = NULL;
      SInt32 score;

      result = IOCreatePlugInInterfaceForService(service,
                                                 kIOUSBInterfaceUserClientTypeID,
                                                 kIOCFPlugInInterfaceID,
                                                 &plugin, &score);
      IOObjectRelease(service);
      service = 0;

      if ((result == kIOReturnSuccess) && plugin) {
        IOUSBInterfaceInterface190 **interface = NULL;

        result = (*plugin)->QueryInterface(plugin,
                                           CFUUIDGetUUIDBytes(kIOUSBInterfaceInterfaceID190),
                                           (LPVOID)&interface);
        (*plugin)->Release(plugin);
        plugin = NULL;

        if ((result == kIOReturnSuccess) && interface) {
          if (isInterface(interface, number)) {
            unsetInterface(devx);
            devx->interface = interface;
            found = 1;
            break;
          }

          (*interface)->Release(interface);
          interface = NULL;
        } else {
          setUsbError(result, "USB interface interface create");
        }
      } else {
        setUsbError(result, "USB interface service plugin create");
      }
    }
    if (!found) logMessage(LOG_ERR, "USB interface not found: %d", number);

    IOObjectRelease(iterator);
    iterator = 0;
  } else {
    setUsbError(result, "USB interface iterator create");
  }

  return found;
}

static void
usbAsynchronousRequestCallback (void *context, IOReturn result, void *arg) {
  UsbAsynchronousRequest *request = context;
  UsbEndpoint *endpoint = request->endpoint;
  UsbEndpointExtension *eptx = endpoint->extension;

  request->result = result;
  request->count = (intptr_t)arg;

  if (!enqueueItem(eptx->completedRequests, request)) {
    logSystemError("USB completed request enqueue");
    free(request);
  }
}

int
usbDisableAutosuspend (UsbDevice *device) {
  logUnsupportedFunction();
  return 0;
}

int
usbSetConfiguration (UsbDevice *device, unsigned char configuration) {
  UsbDeviceExtension *devx = device->extension;

  if (openDevice(device, 1)) {
    UInt8 arg = configuration;
    IOReturn result = (*devx->device)->SetConfiguration(devx->device, arg);
    if (result == kIOReturnSuccess) return 1;
    setUsbError(result, "USB configuration set");
  }

  return 0;
}

int
usbClaimInterface (UsbDevice *device, unsigned char interface) {
  UsbDeviceExtension *devx = device->extension;
  IOReturn result;

  if (setInterface(devx, interface)) {
    if (devx->interfaceOpened) return 1;

    result = (*devx->interface)->USBInterfaceOpen(devx->interface);
    if (result == kIOReturnSuccess) {
      result = (*devx->interface)->GetNumEndpoints(devx->interface, &devx->pipeCount);
      if (result == kIOReturnSuccess) {
        devx->interfaceOpened = 1;
        return 1;
      } else {
        setUsbError(result, "USB pipe count query");
      }

      (*devx->interface)->USBInterfaceClose(devx->interface);
    } else {
      setUsbError(result, "USB interface open");
    }
  }

  return 0;
}

int
usbReleaseInterface (UsbDevice *device, unsigned char interface) {
  UsbDeviceExtension *devx = device->extension;
  return setInterface(devx, interface) && unsetInterface(devx);
}

int
usbSetAlternative (
  UsbDevice *device,
  unsigned char interface,
  unsigned char alternative
) {
  UsbDeviceExtension *devx = device->extension;

  if (setInterface(devx, interface)) {
    IOReturn result;
    UInt8 arg;

    result = (*devx->interface)->GetAlternateSetting(devx->interface, &arg);
    if (result == kIOReturnSuccess) {
      if (arg == alternative) return 1;

      arg = alternative;
      result = (*devx->interface)->SetAlternateInterface(devx->interface, arg);
      if (result == kIOReturnSuccess) return 1;
      setUsbError(result, "USB alternative set");
    } else {
      setUsbError(result, "USB alternative get");
    }
  }

  return 0;
}

int
usbResetDevice (UsbDevice *device) {
  logUnsupportedFunction();
  return 0;
}

int
usbClearHalt (UsbDevice *device, unsigned char endpointAddress) {
  UsbDeviceExtension *devx = device->extension;
  UsbEndpoint *endpoint;

  if ((endpoint = usbGetEndpoint(device, endpointAddress))) {
    UsbEndpointExtension *eptx = endpoint->extension;
    IOReturn result;

    result = (*devx->interface)->ClearPipeStallBothEnds(devx->interface, eptx->pipeNumber);
    if (result == kIOReturnSuccess) return 1;
    setUsbError(result, "USB endpoint clear");
  }

  return 0;
}

ssize_t
usbControlTransfer (
  UsbDevice *device,
  uint8_t direction,
  uint8_t recipient,
  uint8_t type,
  uint8_t request,
  uint16_t value,
  uint16_t index,
  void *buffer,
  uint16_t length,
  int timeout
) {
  UsbDeviceExtension *devx = device->extension;
  IOReturn result;
  IOUSBDevRequestTO arg;

  arg.bmRequestType = direction | recipient | type;
  arg.bRequest = request;
  arg.wValue = value;
  arg.wIndex = index;
  arg.wLength = length;

  arg.pData = buffer;
  arg.noDataTimeout = timeout;
  arg.completionTimeout = timeout;

  result = (*devx->device)->DeviceRequestTO(devx->device, &arg);
  if (result == kIOReturnSuccess) return arg.wLenDone;
  setUsbError(result, "USB control transfer");
  return -1;
}

void *
usbSubmitRequest (
  UsbDevice *device,
  unsigned char endpointAddress,
  void *buffer,
  size_t length,
  void *context
) {
  UsbDeviceExtension *devx = device->extension;
  UsbEndpoint *endpoint;

  if ((endpoint = usbGetEndpoint(device, endpointAddress))) {
    UsbEndpointExtension *eptx = endpoint->extension;
    IOReturn result;
    UsbAsynchronousRequest *request;

    if (!devx->runloopSource) {
      result = (*devx->interface)->CreateInterfaceAsyncEventSource(devx->interface,
                                                                   &devx->runloopSource);
      if (result != kIOReturnSuccess) {
        setUsbError(result, "USB interface event source create");
        return NULL;
      }

      addRunLoopSource(devx->runloopSource);
    }

    if ((request = malloc(sizeof(*request) + length))) {
      request->endpoint = endpoint;
      request->context = context;
      request->buffer = (request->length = length)? (request + 1): NULL;

      switch (eptx->transferDirection) {
        case kUSBIn:
          result = (*devx->interface)->ReadPipeAsync(devx->interface, eptx->pipeNumber,
                                                     request->buffer, request->length,
                                                     usbAsynchronousRequestCallback, request);
          if (result == kIOReturnSuccess) return request;
          setUsbError(result, "USB endpoint asynchronous read");
          break;

        case kUSBOut:
          if (request->buffer) memcpy(request->buffer, buffer, length);
          result = (*devx->interface)->WritePipeAsync(devx->interface, eptx->pipeNumber,
                                                      request->buffer, request->length,
                                                      usbAsynchronousRequestCallback, request);
          if (result == kIOReturnSuccess) return request;
          setUsbError(result, "USB endpoint asynchronous write");
          break;

        default:
          logMessage(LOG_ERR, "USB endpoint direction not suppported: %d",
                     eptx->transferDirection);
          errno = ENOSYS;
          break;
      }

      free(request);
    } else {
      logSystemError("USB asynchronous request allocate");
    }
  }

  return NULL;
}

int
usbCancelRequest (UsbDevice *device, void *request) {
  logUnsupportedFunction();
  return 0;
}

void *
usbReapResponse (
  UsbDevice *device,
  unsigned char endpointAddress,
  UsbResponse *response,
  int wait
) {
  UsbEndpoint *endpoint;

  if ((endpoint = usbGetEndpoint(device, endpointAddress))) {
    UsbEndpointExtension *eptx = endpoint->extension;
    UsbAsynchronousRequest *request;

    while (!(request = dequeueItem(eptx->completedRequests))) {
      switch (executeRunLoop((wait? 60: 0))) {
        case kCFRunLoopRunTimedOut:
          if (wait) continue;
        case kCFRunLoopRunFinished:
          errno = EAGAIN;
          goto none;

        case kCFRunLoopRunStopped:
        case kCFRunLoopRunHandledSource:
        default:
          continue;
      }
    }

    response->context = request->context;
    response->buffer = request->buffer;
    response->size = request->length;

    if (request->result == kIOReturnSuccess) {
      response->error = 0;
      response->count = request->count;

      if (!usbApplyInputFilters(endpoint, response->buffer, response->size, &response->count)) {
        response->error = EIO;
        response->count = -1;
      }
    } else {
      setUsbError(request->result, "USB asynchronous response");
      response->error = errno;
      response->count = -1;
    }

    return request;
  }

none:
  return NULL;
}

int
usbMonitorInputEndpoint (
  UsbDevice *device, unsigned char endpointNumber,
  AsyncMonitorCallback *callback, void *data
) {
  return 0;
}

ssize_t
usbReadEndpoint (
  UsbDevice *device,
  unsigned char endpointNumber,
  void *buffer,
  size_t length,
  int timeout
) {
  UsbDeviceExtension *devx = device->extension;
  UsbEndpoint *endpoint;

  if ((endpoint = usbGetInputEndpoint(device, endpointNumber))) {
    UsbEndpointExtension *eptx = endpoint->extension;
    IOReturn result;
    UInt32 count;
    int stalled = 0;

  read:
    count = length;
    result = (*devx->interface)->ReadPipeTO(devx->interface, eptx->pipeNumber,
                                            buffer, &count,
                                            timeout, timeout);

    switch (result) {
      case kIOReturnSuccess:
        {
          ssize_t actual = count;

          if (usbApplyInputFilters(endpoint, buffer, length, &actual)) return actual;
        }

        errno = EIO;
        break;

      case kIOUSBTransactionTimeout:
        errno = EAGAIN;
        break;

      case kIOUSBPipeStalled:
        if (!stalled) {
          result = (*devx->interface)->ClearPipeStallBothEnds(devx->interface, eptx->pipeNumber);
          if (result == kIOReturnSuccess) {
            stalled = 1;
            goto read;
          }

          setUsbError(result, "USB stall clear");
          break;
        }

      default:
        setUsbError(result, "USB endpoint read");
        break;
    }
  }

  return -1;
}

ssize_t
usbWriteEndpoint (
  UsbDevice *device,
  unsigned char endpointNumber,
  const void *buffer,
  size_t length,
  int timeout
) {
  UsbDeviceExtension *devx = device->extension;
  UsbEndpoint *endpoint;

  if ((endpoint = usbGetOutputEndpoint(device, endpointNumber))) {
    UsbEndpointExtension *eptx = endpoint->extension;
    IOReturn result;

    result = (*devx->interface)->WritePipeTO(devx->interface, eptx->pipeNumber,
                                             (void *)buffer, length,
                                             timeout, timeout);
    if (result == kIOReturnSuccess) return length;
    setUsbError(result, "USB endpoint write");
  }

  return -1;
}

int
usbReadDeviceDescriptor (UsbDevice *device) {
  UsbDeviceExtension *devx = device->extension;
  IOReturn result;

  {
    uint8_t speed;
    if ((result = (*devx->device)->GetDeviceSpeed(devx->device, &speed)) != kIOReturnSuccess) goto error;
    switch (speed) {
      default                 : device->descriptor.bcdUSB = 0X0000; break;
      case kUSBDeviceSpeedLow : device->descriptor.bcdUSB = kUSBRel10; break;
      case kUSBDeviceSpeedFull: device->descriptor.bcdUSB = kUSBRel11; break;
      case kUSBDeviceSpeedHigh: device->descriptor.bcdUSB = kUSBRel20; break;
    }
  }

  if ((result = (*devx->device)->GetDeviceClass(devx->device, &device->descriptor.bDeviceClass)) != kIOReturnSuccess) goto error;
  if ((result = (*devx->device)->GetDeviceSubClass(devx->device, &device->descriptor.bDeviceSubClass)) != kIOReturnSuccess) goto error;
  if ((result = (*devx->device)->GetDeviceProtocol(devx->device, &device->descriptor.bDeviceProtocol)) != kIOReturnSuccess) goto error;

  if ((result = (*devx->device)->GetDeviceVendor(devx->device, &device->descriptor.idVendor)) != kIOReturnSuccess) goto error;
  if ((result = (*devx->device)->GetDeviceProduct(devx->device, &device->descriptor.idProduct)) != kIOReturnSuccess) goto error;
  if ((result = (*devx->device)->GetDeviceReleaseNumber(devx->device, &device->descriptor.bcdDevice)) != kIOReturnSuccess) goto error;

  if ((result = (*devx->device)->USBGetManufacturerStringIndex(devx->device, &device->descriptor.iManufacturer)) != kIOReturnSuccess) goto error;
  if ((result = (*devx->device)->USBGetProductStringIndex(devx->device, &device->descriptor.iProduct)) != kIOReturnSuccess) goto error;
  if ((result = (*devx->device)->USBGetSerialNumberStringIndex(devx->device, &device->descriptor.iSerialNumber)) != kIOReturnSuccess) goto error;

  if ((result = (*devx->device)->GetNumberOfConfigurations(devx->device, &device->descriptor.bNumConfigurations)) != kIOReturnSuccess) goto error;
  device->descriptor.bMaxPacketSize0 = 0;

  device->descriptor.bLength = UsbDescriptorSize_Device;
  device->descriptor.bDescriptorType = UsbDescriptorType_Device;
  return 1;

error:
  setUsbError(result, "USB device descriptor read");
  return 0;
}

int
usbAllocateEndpointExtension (UsbEndpoint *endpoint) {
  UsbDeviceExtension *devx = endpoint->device->extension;
  UsbEndpointExtension *eptx;

  if ((eptx = malloc(sizeof(*eptx)))) {
    if ((eptx->completedRequests = newQueue(NULL, NULL))) {
      IOReturn result;
      unsigned char number = USB_ENDPOINT_NUMBER(endpoint->descriptor);
      unsigned char direction = USB_ENDPOINT_DIRECTION(endpoint->descriptor);

      for (eptx->pipeNumber=1; eptx->pipeNumber<=devx->pipeCount; ++eptx->pipeNumber) {
        result = (*devx->interface)->GetPipeProperties(devx->interface, eptx->pipeNumber,
                                                       &eptx->transferDirection, &eptx->endpointNumber,
                                                       &eptx->transferMode, &eptx->packetSize, &eptx->pollInterval);
        if (result == kIOReturnSuccess) {
          if ((eptx->endpointNumber == number) &&
              (((eptx->transferDirection == kUSBIn) && (direction == UsbEndpointDirection_Input)) ||
               ((eptx->transferDirection == kUSBOut) && (direction == UsbEndpointDirection_Output)))) {
            logMessage(LOG_CATEGORY(USB_IO), "ept=%02X -> pip=%d (num=%d dir=%d xfr=%d int=%d pkt=%d)",
                       endpoint->descriptor->bEndpointAddress, eptx->pipeNumber,
                       eptx->endpointNumber, eptx->transferDirection, eptx->transferMode,
                       eptx->pollInterval, eptx->packetSize);

            eptx->endpoint = endpoint;
            endpoint->extension = eptx;
            return 1;
          }
        } else {
          setUsbError(result, "USB pipe properties query");
        }
      }

      errno = EIO;
      logMessage(LOG_ERR, "USB pipe not found: ept=%02X",
                 endpoint->descriptor->bEndpointAddress);

      deallocateQueue(eptx->completedRequests);
    } else {
      logSystemError("USB completed request queue allocate");
    }

    free(eptx);
  } else {
    logSystemError("USB endpoint extension allocate");
  }

  return 0;
}

void
usbDeallocateEndpointExtension (UsbEndpointExtension *eptx) {
  if (eptx->completedRequests) {
    deallocateQueue(eptx->completedRequests);
    eptx->completedRequests = NULL;
  }

  free(eptx);
}

void
usbDeallocateDeviceExtension (UsbDeviceExtension *devx) {
  IOReturn result;

  unsetInterface(devx);

  if (devx->deviceOpened) {
    result = (*devx->device)->USBDeviceClose(devx->device);
    if (result != kIOReturnSuccess) setUsbError(result, "USB device close");
    devx->deviceOpened = 0;
  }

  (*devx->device)->Release(devx->device);
  devx->device = NULL;

  free(devx);
}

UsbDevice *
usbFindDevice (UsbDeviceChooser *chooser, UsbChooseChannelData *data) {
  UsbDevice *device = NULL;
  kern_return_t kernelResult;
  IOReturn ioResult;
  mach_port_t port;

  kernelResult = IOMasterPort(MACH_PORT_NULL, &port);
  if (kernelResult == KERN_SUCCESS) {
    CFMutableDictionaryRef dictionary;

    if ((dictionary = IOServiceMatching(kIOUSBDeviceClassName))) {
      io_iterator_t iterator = 0;

      kernelResult = IOServiceGetMatchingServices(port, dictionary, &iterator);
      dictionary = NULL;

      if ((kernelResult == KERN_SUCCESS) && iterator) {
        io_service_t service;

        while ((service = IOIteratorNext(iterator))) {
          IOCFPlugInInterface **plugin = NULL;
          SInt32 score;

          ioResult = IOCreatePlugInInterfaceForService(service,
                                                       kIOUSBDeviceUserClientTypeID,
                                                       kIOCFPlugInInterfaceID,
                                                       &plugin, &score);
          IOObjectRelease(service);
          service = 0;

          if ((ioResult == kIOReturnSuccess) && plugin) {
            IOUSBDeviceInterface182 **interface = NULL;

            ioResult = (*plugin)->QueryInterface(plugin,
                                                 CFUUIDGetUUIDBytes(kIOUSBDeviceInterfaceID182),
                                                 (LPVOID)&interface);
            (*plugin)->Release(plugin);
            plugin = NULL;

            if ((ioResult == kIOReturnSuccess) && interface) {
              UsbDeviceExtension *devx;

              if ((devx = malloc(sizeof(*devx)))) {
                devx->device = interface;
                devx->deviceOpened = 0;

                devx->interface = NULL;
                devx->interfaceOpened = 0;

                devx->runloopSource = NULL;

                if ((device = usbTestDevice(devx, chooser, data))) break;
                free(devx);
                devx = NULL;
              } else {
                logSystemError("USB device extension allocate");
              }

              (*interface)->Release(interface);
              interface = NULL;
            } else {
              setUsbError(ioResult, "USB device interface create");
            }
          } else {
            setUsbError(ioResult, "USB device service plugin create");
          }
        }

        IOObjectRelease(iterator);
        iterator = 0;
      } else {
        setUsbError(kernelResult, "USB device iterator create");
      }
    } else {
      logMessage(LOG_ERR, "USB device matching dictionary create error.");
    }

    mach_port_deallocate(mach_task_self(), port);
  } else {
    setUsbError(kernelResult, "Darwin master port create");
  }

  return device;
}

void
usbForgetDevices (void) {
}
