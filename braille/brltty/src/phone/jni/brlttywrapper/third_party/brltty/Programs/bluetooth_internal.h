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

#ifndef BRLTTY_INCLUDED_BLUETOOTH_INTERNAL
#define BRLTTY_INCLUDED_BLUETOOTH_INTERNAL

#ifdef __cplusplus
extern "C" {
#endif /* __cplusplus */

#define BDA_SIZE 6

extern char *bthObtainDeviceName (uint64_t bda, int timeout);

typedef struct BluetoothConnectionExtensionStruct BluetoothConnectionExtension;

extern BluetoothConnectionExtension *bthNewConnectionExtension (uint64_t bda);
extern void bthReleaseConnectionExtension (BluetoothConnectionExtension *bcx);

extern int bthPollInput (BluetoothConnectionExtension *bcx, int timeout);

extern ssize_t bthGetData (
  BluetoothConnectionExtension *bcx, void *buffer, size_t size,
  int initialTimeout, int subsequentTimeout
);

extern ssize_t bthPutData (BluetoothConnectionExtension *bcx, const void *buffer, size_t size);

extern int bthDiscoverChannel (
  uint8_t *channel, BluetoothConnectionExtension *bcx,
  const void *uuidBytes, size_t uuidLength,
  int timeout
);

extern int bthOpenChannel (BluetoothConnectionExtension *bcx, uint8_t channel, int timeout);

struct BluetoothConnectionStruct {
  uint64_t address;
  uint8_t channel;
  BluetoothConnectionExtension *extension;
};

typedef struct {
  const char *namePrefix;
  const char *const *driverCodes;
} BluetoothNameEntry;

extern const BluetoothNameEntry bluetoothNameTable[];

typedef struct {
  const char *name;
  uint64_t address;
  unsigned paired:1;
} DiscoveredBluetoothDevice;

typedef int DiscoveredBluetoothDeviceTester (
  const DiscoveredBluetoothDevice *device, void *data
);

extern void bthProcessDiscoveredDevices (
  DiscoveredBluetoothDeviceTester *testDevice, void *data
);

#ifdef __cplusplus
}
#endif /* __cplusplus */

#endif /* BRLTTY_INCLUDED_BLUETOOTH_INTERNAL */
