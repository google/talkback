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

#import <IOBluetooth/objc/IOBluetoothDevice.h>
#import <IOBluetooth/objc/IOBluetoothSDPUUID.h>
#import <IOBluetooth/objc/IOBluetoothSDPServiceRecord.h>
#import <IOBluetooth/objc/IOBluetoothRFCOMMChannel.h>

#include "log.h"
#include "io_misc.h"
#include "io_bluetooth.h"
#include "bluetooth_internal.h"
#include "system_darwin.h"

@interface ServiceQueryResult: AsynchronousResult
- (void) sdpQueryComplete
  : (IOBluetoothDevice *) device
  status: (IOReturn) status;
@end

@interface BluetoothConnectionDelegate: AsynchronousTask
@property (assign) BluetoothConnectionExtension *bluetoothConnectionExtension;
@end

@interface RfcommChannelDelegate: BluetoothConnectionDelegate
- (void) rfcommChannelData
  : (IOBluetoothRFCOMMChannel *) rfcommChannel
  data: (void *) dataPointer
  length: (size_t) dataLength;

- (void) rfcommChannelClosed
  : (IOBluetoothRFCOMMChannel*) rfcommChannel;

- (IOReturn) run;
@end

struct BluetoothConnectionExtensionStruct {
  BluetoothDeviceAddress bluetoothAddress;
  IOBluetoothDevice *bluetoothDevice;

  IOBluetoothRFCOMMChannel *rfcommChannel;
  RfcommChannelDelegate *rfcommDelegate;

  int inputPipe[2];
};

static void
bthSetError (IOReturn result, const char *action) {
  setDarwinSystemError(result);
  logSystemError(action);
}

static void
bthInitializeRfcommChannel (BluetoothConnectionExtension *bcx) {
  bcx->rfcommChannel = nil;
}

static void
bthDestroyRfcommChannel (BluetoothConnectionExtension *bcx) {
  if (bcx->rfcommChannel) {
    [bcx->rfcommChannel closeChannel];
    [bcx->rfcommChannel release];
    bthInitializeRfcommChannel(bcx);
  }
}

static void
bthInitializeRfcommDelegate (BluetoothConnectionExtension *bcx) {
  bcx->rfcommDelegate = nil;
}

static void
bthDestroyRfcommDelegate (BluetoothConnectionExtension *bcx) {
  if (bcx->rfcommDelegate) {
    [bcx->rfcommDelegate stop];
    [bcx->rfcommDelegate wait:5];
    [bcx->rfcommDelegate release];
    bthInitializeRfcommDelegate(bcx);
  }
}

static void
bthInitializeBluetoothDevice (BluetoothConnectionExtension *bcx) {
  bcx->bluetoothDevice = nil;
}

static void
bthDestroyBluetoothDevice (BluetoothConnectionExtension *bcx) {
  if (bcx->bluetoothDevice) {
    [bcx->bluetoothDevice closeConnection];
    [bcx->bluetoothDevice release];
    bthInitializeBluetoothDevice(bcx);
  }
}

static void
bthInitializeInputPipe (BluetoothConnectionExtension *bcx) {
  bcx->inputPipe[0] = bcx->inputPipe[1] = INVALID_FILE_DESCRIPTOR;
}

static void
bthDestroyInputPipe (BluetoothConnectionExtension *bcx) {
  int *fileDescriptor = bcx->inputPipe;
  const int *end = fileDescriptor + ARRAY_COUNT(bcx->inputPipe);

  while (fileDescriptor < end) {
    closeFile(fileDescriptor);
    fileDescriptor += 1;
  }
}

static void
bthMakeAddress (BluetoothDeviceAddress *address, uint64_t bda) {
  unsigned int index = sizeof(address->data);

  while (index > 0) {
    address->data[--index] = bda & 0XFF;
    bda >>= 8;
  }
}

BluetoothConnectionExtension *
bthNewConnectionExtension (uint64_t bda) {
  BluetoothConnectionExtension *bcx;

  if ((bcx = malloc(sizeof(*bcx)))) {
    memset(bcx, 0, sizeof(*bcx));
    bthInitializeInputPipe(bcx);
    bthMakeAddress(&bcx->bluetoothAddress, bda);

    if ((bcx->bluetoothDevice = [IOBluetoothDevice deviceWithAddress:&bcx->bluetoothAddress])) {
      [bcx->bluetoothDevice retain];

      return bcx;
    }

    free(bcx);
  } else {
    logMallocError();
  }

  return NULL;
}

void
bthReleaseConnectionExtension (BluetoothConnectionExtension *bcx) {
  bthDestroyRfcommChannel(bcx);
  bthDestroyRfcommDelegate(bcx);
  bthDestroyBluetoothDevice(bcx);
  bthDestroyInputPipe(bcx);
  free(bcx);
}

int
bthOpenChannel (BluetoothConnectionExtension *bcx, uint8_t channel, int timeout) {
  IOReturn result;

  if (pipe(bcx->inputPipe) != -1) {
    if (setBlockingIo(bcx->inputPipe[0], 0)) {
      if ((bcx->rfcommDelegate = [RfcommChannelDelegate new])) {
        bcx->rfcommDelegate.bluetoothConnectionExtension = bcx;

        if ((result = [bcx->bluetoothDevice openRFCOMMChannelSync:&bcx->rfcommChannel withChannelID:channel delegate:nil]) == kIOReturnSuccess) {
          if ([bcx->rfcommDelegate start]) {
            return 1;
          }

          bthDestroyRfcommChannel(bcx);
        } else {
          bthSetError(result, "RFCOMM channel open");
        }

        bthDestroyRfcommDelegate(bcx);
      }
    }

    bthDestroyInputPipe(bcx);
  } else {
    logSystemError("pipe");
  }

  return 0;
}

static int
bthPerformServiceQuery (BluetoothConnectionExtension *bcx) {
  int ok = 0;
  IOReturn result;
  ServiceQueryResult *target = [ServiceQueryResult new];

  if (target) {
    if ((result = [bcx->bluetoothDevice performSDPQuery:target]) == kIOReturnSuccess) {
      if ([target wait:10]) {
        if ((result = target.finalStatus) == kIOReturnSuccess) {
          ok = 1;
        } else {
          bthSetError(result, "service discovery response");
        }
      }
    } else {
      bthSetError(result, "service discovery request");
    }

    [target release];
  }

  return ok;
}

int
bthDiscoverChannel (
  uint8_t *channel, BluetoothConnectionExtension *bcx,
  const void *uuidBytes, size_t uuidLength,
  int timeout
) {
  IOReturn result;

  if (bthPerformServiceQuery(bcx)) {
    IOBluetoothSDPUUID *uuid = [IOBluetoothSDPUUID uuidWithBytes:uuidBytes length:uuidLength];

    if (uuid) {
      IOBluetoothSDPServiceRecord *record = [bcx->bluetoothDevice getServiceRecordForUUID:uuid];

      if (record) {
        if ((result = [record getRFCOMMChannelID:channel]) == kIOReturnSuccess) {
          return 1;
        } else {
          bthSetError(result, "RFCOMM channel lookup");
        }
      }
    }
  }

  return 0;
}

int
bthMonitorInput (BluetoothConnection *connection, AsyncMonitorCallback *callback, void *data) {
  return 0;
}

int
bthPollInput (BluetoothConnectionExtension *bcx, int timeout) {
  return awaitFileInput(bcx->inputPipe[0], timeout);
}

ssize_t
bthGetData (
  BluetoothConnectionExtension *bcx, void *buffer, size_t size,
  int initialTimeout, int subsequentTimeout
) {
  return readFile(bcx->inputPipe[0], buffer, size, initialTimeout, subsequentTimeout);
}

ssize_t
bthPutData (BluetoothConnectionExtension *bcx, const void *buffer, size_t size) {
  IOReturn result = [bcx->rfcommChannel writeSync:(void *)buffer length:size];

  if (result == kIOReturnSuccess) return size;
  bthSetError(result, "RFCOMM channel write");
  return -1;
}

char *
bthObtainDeviceName (uint64_t bda, int timeout) {
  IOReturn result;
  BluetoothDeviceAddress address;

  bthMakeAddress(&address, bda);

  {
    IOBluetoothDevice *device = [IOBluetoothDevice deviceWithAddress:&address];

    if (device != nil) {
      if ((result = [device remoteNameRequest:nil]) == kIOReturnSuccess) {
        NSString *nsName = device.name;

        if (nsName != nil) {
          const char *utf8Name = [nsName UTF8String];

          if (utf8Name != NULL) {
            char *name = strdup(utf8Name);

            if (name != NULL) {
              return name;
            }
          }
        }
      } else {
        bthSetError(result, "device name query");
      }

      [device closeConnection];
    }
  }

  return NULL;
}

@implementation ServiceQueryResult
- (void) sdpQueryComplete
  : (IOBluetoothDevice *) device
  status: (IOReturn) status
  {
    [self setStatus:status];
  }
@end

@implementation BluetoothConnectionDelegate
@synthesize bluetoothConnectionExtension;
@end

@implementation RfcommChannelDelegate
- (void) rfcommChannelData
  : (IOBluetoothRFCOMMChannel *) rfcommChannel
  data: (void *) dataPointer
  length: (size_t) dataLength
  {
    writeFile(self.bluetoothConnectionExtension->inputPipe[1], dataPointer, dataLength);
  }

- (void) rfcommChannelClosed
  : (IOBluetoothRFCOMMChannel*) rfcommChannel
  {
    logMessage(LOG_NOTICE, "RFCOMM channel closed");
  }

- (IOReturn) run
  {
    IOReturn result;
    logMessage(LOG_CATEGORY(BLUETOOTH_IO), "RFCOMM channel delegate started");

    {
      BluetoothConnectionExtension *bcx = self.bluetoothConnectionExtension;

      if ((result = [bcx->rfcommChannel setDelegate:self]) == kIOReturnSuccess) {
        CFRunLoopRun();
        result = kIOReturnSuccess;
      } else {
        bthSetError(result, "RFCOMM channel delegate set");
      }
    }

    logMessage(LOG_CATEGORY(BLUETOOTH_IO), "RFCOMM channel delegate finished");
    return result;
  }
@end

void
bthProcessDiscoveredDevices (
  DiscoveredBluetoothDeviceTester *testDevice, void *data
) {
}
