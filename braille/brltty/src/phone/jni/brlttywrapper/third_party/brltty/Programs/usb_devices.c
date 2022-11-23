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

#include "usb_devices.h"

#define USB_DEVICE_ENTRY(vendor,product,...) \
  { .vendorIdentifier = vendor, \
    .productIdentifier = product, \
    .driverCodes = (const char *const []){__VA_ARGS__, NULL} \
  }

const UsbDeviceEntry usbDeviceTable[] = {
// BEGIN_USB_DEVICES

// Device: 0403:6001
// Generic Identifier
// Vendor: Future Technology Devices International, Ltd
// Product: FT232 USB-Serial (UART) IC
// Albatross [all models]
// Cebra [all models]
// HIMS [Sync Braille]
// HandyTech [FTDI chip]
// Hedo [MobilLine]
// MDV [all models]
USB_DEVICE_ENTRY(0X0403, 0X6001, "at", "ce", "hd", "hm", "ht", "md"),

// Device: 0403:DE58
// Hedo [MobilLine]
USB_DEVICE_ENTRY(0X0403, 0XDE58, "hd"),

// Device: 0403:DE59
// Hedo [ProfiLine]
USB_DEVICE_ENTRY(0X0403, 0XDE59, "hd"),

// Device: 0403:F208
// Papenmeier [all models]
USB_DEVICE_ENTRY(0X0403, 0XF208, "pm"),

// Device: 0403:FE70
// Baum [Vario 40 (40 cells)]
USB_DEVICE_ENTRY(0X0403, 0XFE70, "bm"),

// Device: 0403:FE71
// Baum [PocketVario (24 cells)]
USB_DEVICE_ENTRY(0X0403, 0XFE71, "bm"),

// Device: 0403:FE72
// Baum [SuperVario 40 (40 cells)]
USB_DEVICE_ENTRY(0X0403, 0XFE72, "bm"),

// Device: 0403:FE73
// Baum [SuperVario 32 (32 cells)]
USB_DEVICE_ENTRY(0X0403, 0XFE73, "bm"),

// Device: 0403:FE74
// Baum [SuperVario 64 (64 cells)]
USB_DEVICE_ENTRY(0X0403, 0XFE74, "bm"),

// Device: 0403:FE75
// Baum [SuperVario 80 (80 cells)]
USB_DEVICE_ENTRY(0X0403, 0XFE75, "bm"),

// Device: 0403:FE76
// Baum [VarioPro 80 (80 cells)]
USB_DEVICE_ENTRY(0X0403, 0XFE76, "bm"),

// Device: 0403:FE77
// Baum [VarioPro 64 (64 cells)]
USB_DEVICE_ENTRY(0X0403, 0XFE77, "bm"),

// Device: 0452:0100
// Metec [all models]
USB_DEVICE_ENTRY(0X0452, 0X0100, "mt"),

// Device: 045E:930A
// HIMS [Braille Sense (USB 1.1)]
// HIMS [Braille Sense (USB 2.0)]
// HIMS [Braille Sense U2 (USB 2.0)]
USB_DEVICE_ENTRY(0X045E, 0X930A, "hm"),

// Device: 045E:930B
// HIMS [Braille Edge]
USB_DEVICE_ENTRY(0X045E, 0X930B, "hm"),

// Device: 0483:A1D3
// Baum [Orbit 20 (20 cells)]
USB_DEVICE_ENTRY(0X0483, 0XA1D3, "bm"),

// Device: 06B0:0001
// Alva [Satellite (5nn)]
USB_DEVICE_ENTRY(0X06B0, 0X0001, "al"),

// Device: 0798:0001
// Voyager [all models]
USB_DEVICE_ENTRY(0X0798, 0X0001, "vo"),

// Device: 0798:0600
// Alva [Voyager Protocol Converter]
USB_DEVICE_ENTRY(0X0798, 0X0600, "al"),

// Device: 0798:0624
// Alva [BC624]
USB_DEVICE_ENTRY(0X0798, 0X0624, "al"),

// Device: 0798:0640
// Alva [BC640]
USB_DEVICE_ENTRY(0X0798, 0X0640, "al"),

// Device: 0798:0680
// Alva [BC680]
USB_DEVICE_ENTRY(0X0798, 0X0680, "al"),

// Device: 0904:2000
// Baum [VarioPro 40 (40 cells)]
USB_DEVICE_ENTRY(0X0904, 0X2000, "bm"),

// Device: 0904:2001
// Baum [EcoVario 24 (24 cells)]
USB_DEVICE_ENTRY(0X0904, 0X2001, "bm"),

// Device: 0904:2002
// Baum [EcoVario 40 (40 cells)]
USB_DEVICE_ENTRY(0X0904, 0X2002, "bm"),

// Device: 0904:2007
// Baum [VarioConnect 40 (40 cells)]
USB_DEVICE_ENTRY(0X0904, 0X2007, "bm"),

// Device: 0904:2008
// Baum [VarioConnect 32 (32 cells)]
USB_DEVICE_ENTRY(0X0904, 0X2008, "bm"),

// Device: 0904:2009
// Baum [VarioConnect 24 (24 cells)]
USB_DEVICE_ENTRY(0X0904, 0X2009, "bm"),

// Device: 0904:2010
// Baum [VarioConnect 64 (64 cells)]
USB_DEVICE_ENTRY(0X0904, 0X2010, "bm"),

// Device: 0904:2011
// Baum [VarioConnect 80 (80 cells)]
USB_DEVICE_ENTRY(0X0904, 0X2011, "bm"),

// Device: 0904:2014
// Baum [EcoVario 32 (32 cells)]
USB_DEVICE_ENTRY(0X0904, 0X2014, "bm"),

// Device: 0904:2015
// Baum [EcoVario 64 (64 cells)]
USB_DEVICE_ENTRY(0X0904, 0X2015, "bm"),

// Device: 0904:2016
// Baum [EcoVario 80 (80 cells)]
USB_DEVICE_ENTRY(0X0904, 0X2016, "bm"),

// Device: 0904:3000
// Baum [Refreshabraille 18 (18 cells)]
USB_DEVICE_ENTRY(0X0904, 0X3000, "bm"),

// Device: 0904:3001
// Baum [Orbit in Refreshabraille Emulation Mode (18 cells)]
// Baum [Refreshabraille 18 (18 cells)]
USB_DEVICE_ENTRY(0X0904, 0X3001, "bm"),

// Device: 0904:4004
// Baum [Pronto! V3 18 (18 cells)]
USB_DEVICE_ENTRY(0X0904, 0X4004, "bm"),

// Device: 0904:4005
// Baum [Pronto! V3 40 (40 cells)]
USB_DEVICE_ENTRY(0X0904, 0X4005, "bm"),

// Device: 0904:4007
// Baum [Pronto! V4 18 (18 cells)]
USB_DEVICE_ENTRY(0X0904, 0X4007, "bm"),

// Device: 0904:4008
// Baum [Pronto! V4 40 (40 cells)]
USB_DEVICE_ENTRY(0X0904, 0X4008, "bm"),

// Device: 0904:6001
// Baum [SuperVario2 40 (40 cells)]
USB_DEVICE_ENTRY(0X0904, 0X6001, "bm"),

// Device: 0904:6002
// Baum [PocketVario2 (24 cells)]
USB_DEVICE_ENTRY(0X0904, 0X6002, "bm"),

// Device: 0904:6003
// Baum [SuperVario2 32 (32 cells)]
USB_DEVICE_ENTRY(0X0904, 0X6003, "bm"),

// Device: 0904:6004
// Baum [SuperVario2 64 (64 cells)]
USB_DEVICE_ENTRY(0X0904, 0X6004, "bm"),

// Device: 0904:6005
// Baum [SuperVario2 80 (80 cells)]
USB_DEVICE_ENTRY(0X0904, 0X6005, "bm"),

// Device: 0904:6006
// Baum [Brailliant2 40 (40 cells)]
USB_DEVICE_ENTRY(0X0904, 0X6006, "bm"),

// Device: 0904:6007
// Baum [Brailliant2 24 (24 cells)]
USB_DEVICE_ENTRY(0X0904, 0X6007, "bm"),

// Device: 0904:6008
// Baum [Brailliant2 32 (32 cells)]
USB_DEVICE_ENTRY(0X0904, 0X6008, "bm"),

// Device: 0904:6009
// Baum [Brailliant2 64 (64 cells)]
USB_DEVICE_ENTRY(0X0904, 0X6009, "bm"),

// Device: 0904:600A
// Baum [Brailliant2 80 (80 cells)]
USB_DEVICE_ENTRY(0X0904, 0X600A, "bm"),

// Device: 0904:6011
// Baum [VarioConnect 24 (24 cells)]
USB_DEVICE_ENTRY(0X0904, 0X6011, "bm"),

// Device: 0904:6012
// Baum [VarioConnect 32 (32 cells)]
USB_DEVICE_ENTRY(0X0904, 0X6012, "bm"),

// Device: 0904:6013
// Baum [VarioConnect 40 (40 cells)]
USB_DEVICE_ENTRY(0X0904, 0X6013, "bm"),

// Device: 0904:6101
// Baum [VarioUltra 20 (20 cells)]
USB_DEVICE_ENTRY(0X0904, 0X6101, "bm"),

// Device: 0904:6102
// Baum [VarioUltra 40 (40 cells)]
USB_DEVICE_ENTRY(0X0904, 0X6102, "bm"),

// Device: 0904:6103
// Baum [VarioUltra 32 (32 cells)]
USB_DEVICE_ENTRY(0X0904, 0X6103, "bm"),

// Device: 0921:1200
// HandyTech [GoHubs chip]
USB_DEVICE_ENTRY(0X0921, 0X1200, "ht"),

// Device: 0F4E:0100
// FreedomScientific [Focus 1]
USB_DEVICE_ENTRY(0X0F4E, 0X0100, "fs"),

// Device: 0F4E:0111
// FreedomScientific [PAC Mate]
USB_DEVICE_ENTRY(0X0F4E, 0X0111, "fs"),

// Device: 0F4E:0112
// FreedomScientific [Focus 2]
USB_DEVICE_ENTRY(0X0F4E, 0X0112, "fs"),

// Device: 0F4E:0114
// FreedomScientific [Focus Blue]
USB_DEVICE_ENTRY(0X0F4E, 0X0114, "fs"),

// Device: 10C4:EA60
// Generic Identifier
// Vendor: Cygnal Integrated Products, Inc.
// Product: CP210x UART Bridge / myAVR mySmartUSB light
// BrailleMemo [Pocket]
// Seika [Braille Display]
USB_DEVICE_ENTRY(0X10C4, 0XEA60, "mm", "sk"),

// Device: 10C4:EA80
// Generic Identifier
// Vendor: Cygnal Integrated Products, Inc.
// Product: CP210x UART Bridge
// Seika [Note Taker]
USB_DEVICE_ENTRY(0X10C4, 0XEA80, "sk"),

// Device: 1148:0301
// BrailleMemo [Smart]
USB_DEVICE_ENTRY(0X1148, 0X0301, "mm"),

// Device: 1209:ABC0
// Inceptor [all models]
USB_DEVICE_ENTRY(0X1209, 0XABC0, "ic"),

// Device: 1C71:C004
// BrailleNote [HumanWare APEX]
USB_DEVICE_ENTRY(0X1C71, 0XC004, "bn"),

// Device: 1C71:C005
// HumanWare [Brailliant BI 32/40, Brailliant B 80 (serial protocol)]
USB_DEVICE_ENTRY(0X1C71, 0XC005, "hw"),

// Device: 1C71:C006
// HumanWare [non-Touch models (HID protocol)]
USB_DEVICE_ENTRY(0X1C71, 0XC006, "hw"),

// Device: 1C71:C00A
// HumanWare [BrailleNote Touch (HID protocol)]
USB_DEVICE_ENTRY(0X1C71, 0XC00A, "hw"),

// Device: 1C71:C021
// HumanWare [Brailliant BI 14 (serial protocol)]
USB_DEVICE_ENTRY(0X1C71, 0XC021, "hw"),

// Device: 1FE4:0003
// HandyTech [USB-HID adapter]
USB_DEVICE_ENTRY(0X1FE4, 0X0003, "ht"),

// Device: 1FE4:0044
// HandyTech [Easy Braille (HID)]
USB_DEVICE_ENTRY(0X1FE4, 0X0044, "ht"),

// Device: 1FE4:0054
// HandyTech [Active Braille]
USB_DEVICE_ENTRY(0X1FE4, 0X0054, "ht"),

// Device: 1FE4:0055
// HandyTech [Connect Braille 40]
USB_DEVICE_ENTRY(0X1FE4, 0X0055, "ht"),

// Device: 1FE4:0061
// HandyTech [Actilino]
USB_DEVICE_ENTRY(0X1FE4, 0X0061, "ht"),

// Device: 1FE4:0064
// HandyTech [Active Star 40]
USB_DEVICE_ENTRY(0X1FE4, 0X0064, "ht"),

// Device: 1FE4:0074
// HandyTech [Braille Star 40 (HID)]
USB_DEVICE_ENTRY(0X1FE4, 0X0074, "ht"),

// Device: 1FE4:0081
// HandyTech [Basic Braille 16]
USB_DEVICE_ENTRY(0X1FE4, 0X0081, "ht"),

// Device: 1FE4:0082
// HandyTech [Basic Braille 20]
USB_DEVICE_ENTRY(0X1FE4, 0X0082, "ht"),

// Device: 1FE4:0083
// HandyTech [Basic Braille 32]
USB_DEVICE_ENTRY(0X1FE4, 0X0083, "ht"),

// Device: 1FE4:0084
// HandyTech [Basic Braille 40]
USB_DEVICE_ENTRY(0X1FE4, 0X0084, "ht"),

// Device: 1FE4:0086
// HandyTech [Basic Braille 64]
USB_DEVICE_ENTRY(0X1FE4, 0X0086, "ht"),

// Device: 1FE4:0087
// HandyTech [Basic Braille 80]
USB_DEVICE_ENTRY(0X1FE4, 0X0087, "ht"),

// Device: 1FE4:008A
// HandyTech [Basic Braille 48]
USB_DEVICE_ENTRY(0X1FE4, 0X008A, "ht"),

// Device: 1FE4:008B
// HandyTech [Basic Braille 160]
USB_DEVICE_ENTRY(0X1FE4, 0X008B, "ht"),

// Device: 1FE4:00A4
// HandyTech [Activator]
USB_DEVICE_ENTRY(0X1FE4, 0X00A4, "ht"),

// Device: 4242:0001
// Pegasus [all models]
USB_DEVICE_ENTRY(0X4242, 0X0001, "pg"),

// Device: C251:1122
// EuroBraille [Esys (version < 3.0, no SD card)]
USB_DEVICE_ENTRY(0XC251, 0X1122, "eu"),

// Device: C251:1123
// EuroBraille [reserved]
USB_DEVICE_ENTRY(0XC251, 0X1123, "eu"),

// Device: C251:1124
// EuroBraille [Esys (version < 3.0, with SD card)]
USB_DEVICE_ENTRY(0XC251, 0X1124, "eu"),

// Device: C251:1125
// EuroBraille [reserved]
USB_DEVICE_ENTRY(0XC251, 0X1125, "eu"),

// Device: C251:1126
// EuroBraille [Esys (version >= 3.0, no SD card)]
USB_DEVICE_ENTRY(0XC251, 0X1126, "eu"),

// Device: C251:1127
// EuroBraille [reserved]
USB_DEVICE_ENTRY(0XC251, 0X1127, "eu"),

// Device: C251:1128
// EuroBraille [Esys (version >= 3.0, with SD card)]
USB_DEVICE_ENTRY(0XC251, 0X1128, "eu"),

// Device: C251:1129
// EuroBraille [reserved]
USB_DEVICE_ENTRY(0XC251, 0X1129, "eu"),

// Device: C251:112A
// EuroBraille [reserved]
USB_DEVICE_ENTRY(0XC251, 0X112A, "eu"),

// Device: C251:112B
// EuroBraille [reserved]
USB_DEVICE_ENTRY(0XC251, 0X112B, "eu"),

// Device: C251:112C
// EuroBraille [reserved]
USB_DEVICE_ENTRY(0XC251, 0X112C, "eu"),

// Device: C251:112D
// EuroBraille [reserved]
USB_DEVICE_ENTRY(0XC251, 0X112D, "eu"),

// Device: C251:112E
// EuroBraille [reserved]
USB_DEVICE_ENTRY(0XC251, 0X112E, "eu"),

// Device: C251:112F
// EuroBraille [reserved]
USB_DEVICE_ENTRY(0XC251, 0X112F, "eu"),

// Device: C251:1130
// EuroBraille [Esytime (firmware 1.03, 2014-03-31)]
// EuroBraille [Esytime]
USB_DEVICE_ENTRY(0XC251, 0X1130, "eu"),

// Device: C251:1131
// EuroBraille [reserved]
USB_DEVICE_ENTRY(0XC251, 0X1131, "eu"),

// Device: C251:1132
// EuroBraille [reserved]
USB_DEVICE_ENTRY(0XC251, 0X1132, "eu"),

// END_USB_DEVICES
};

const uint16_t usbDeviceCount = ARRAY_COUNT(usbDeviceTable);

