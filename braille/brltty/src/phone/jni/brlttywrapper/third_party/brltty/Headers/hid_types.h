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

#ifndef BRLTTY_INCLUDED_HID_TYPES
#define BRLTTY_INCLUDED_HID_TYPES

#ifdef __cplusplus
extern "C" {
#endif /* __cplusplus */

typedef uint16_t HidDeviceIdentifier;
typedef uint8_t HidReportIdentifier;

typedef uint32_t HidUnsignedValue;
typedef int32_t HidSignedValue;

typedef struct {
  size_t count;
  unsigned char bytes[];
} HidItemsDescriptor;

typedef struct {
  size_t input;
  size_t output;
  size_t feature;
} HidReportSize;

typedef struct {
  HidDeviceIdentifier vendorIdentifier;
  HidDeviceIdentifier productIdentifier;
} HidCommonProperties;

typedef struct {
  const char *manufacturerName;
  const char *productDescription;
  const char *serialNumber;
} HidUSBProperties;

typedef struct {
  const char *macAddress;
  const char *deviceName;
} HidBluetoothProperties;

typedef struct {
  HidCommonProperties common;
  HidUSBProperties usb;
} HidUSBFilter;

typedef struct {
  HidCommonProperties common;
  HidBluetoothProperties bluetooth;
} HidBluetoothFilter;

typedef struct {
  HidCommonProperties common;
  HidUSBProperties usb;
  HidBluetoothProperties bluetooth;

  struct {
    unsigned char wantUSB:1;
    unsigned char wantBluetooth:1;
  } flags;
} HidFilter;

typedef struct {
  const void *data;
  const char *name;
  HidDeviceIdentifier vendor;
  HidDeviceIdentifier product;
} HidModelEntry;

#define BEGIN_HID_MODEL_TABLE static const HidModelEntry hidModelTable[] = {
#define END_HID_MODEL_TABLE { .name=NULL, .vendor=0 } };

#ifdef __cplusplus
}
#endif /* __cplusplus */

#endif /* BRLTTY_INCLUDED_HID_TYPES */
