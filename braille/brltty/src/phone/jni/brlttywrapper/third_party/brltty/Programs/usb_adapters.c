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

#include "usb_adapters.h"

const UsbSerialAdapter usbSerialAdapterTable[] = {
  { /* Albatross, Cebra, HIMS SyncBraille, HandyTech FTDI, Hedo MobilLine, MDV */
    .vendor=0X0403, .product=0X6001,
    .generic = 1,
    .operations = &usbSerialOperations_FTDI_FT8U232AM
  },

  { /* DotPad */
    .vendor=0X0403, .product=0X6010,
    .generic = 1,
    .operations = &usbSerialOperations_FTDI_FT232BM
  },

  { /* Hedo MobilLine */
    .vendor=0X0403, .product=0XDE58,
    .operations = &usbSerialOperations_FTDI_FT232BM
  },

  { /* Hedo ProfiLine */
    .vendor=0X0403, .product=0XDE59,
    .operations = &usbSerialOperations_FTDI_FT232BM
  },

  { /* Papenmeier FTDI */
    .vendor=0X0403, .product=0XF208,
    .operations = &usbSerialOperations_FTDI_FT232BM
  },

  { /* Baum Vario40 (40 cells) */
    .vendor=0X0403, .product=0XFE70,
    .operations = &usbSerialOperations_FTDI_FT232BM
  },

  { /* Baum PocketVario (24 cells) */
    .vendor=0X0403, .product=0XFE71,
    .operations = &usbSerialOperations_FTDI_FT232BM
  },

  { /* Baum SuperVario 40 (40 cells) */
    .vendor=0X0403, .product=0XFE72,
    .operations = &usbSerialOperations_FTDI_FT232BM
  },

  { /* Baum SuperVario 32 (32 cells) */
    .vendor=0X0403, .product=0XFE73,
    .operations = &usbSerialOperations_FTDI_FT232BM
  },

  { /* Baum SuperVario 64 (64 cells) */
    .vendor=0X0403, .product=0XFE74,
    .operations = &usbSerialOperations_FTDI_FT232BM
  },

  { /* Baum SuperVario 80 (80 cells) */
    .vendor=0X0403, .product=0XFE75,
    .operations = &usbSerialOperations_FTDI_FT232BM
  },

  { /* Baum VarioPro 80 (80 cells) */
    .vendor=0X0403, .product=0XFE76,
    .operations = &usbSerialOperations_FTDI_FT232BM
  },

  { /* Baum VarioPro 64 (64 cells) */
    .vendor=0X0403, .product=0XFE77,
    .operations = &usbSerialOperations_FTDI_FT232BM
  },

  { /* Baum VarioPro 40 (40 cells) */
    .vendor=0X0904, .product=0X2000,
    .operations = &usbSerialOperations_FTDI_FT232BM
  },

  { /* Baum EcoVario 24 (24 cells) */
    .vendor=0X0904, .product=0X2001,
    .operations = &usbSerialOperations_FTDI_FT232BM
  },

  { /* Baum EcoVario 40 (40 cells) */
    .vendor=0X0904, .product=0X2002,
    .operations = &usbSerialOperations_FTDI_FT232BM
  },

  { /* Baum VarioConnect 40 (40 cells) */
    .vendor=0X0904, .product=0X2007,
    .operations = &usbSerialOperations_FTDI_FT232BM
  },

  { /* Baum VarioConnect 32 (32 cells) */
    .vendor=0X0904, .product=0X2008,
    .operations = &usbSerialOperations_FTDI_FT232BM
  },

  { /* Baum VarioConnect 24 (24 cells) */
    .vendor=0X0904, .product=0X2009,
    .operations = &usbSerialOperations_FTDI_FT232BM
  },

  { /* Baum VarioConnect 64 (64 cells) */
    .vendor=0X0904, .product=0X2010,
    .operations = &usbSerialOperations_FTDI_FT232BM
  },

  { /* Baum VarioConnect 80 (80 cells) */
    .vendor=0X0904, .product=0X2011,
    .operations = &usbSerialOperations_FTDI_FT232BM
  },

  { /* Baum EcoVario 32 (32 cells) */
    .vendor=0X0904, .product=0X2014,
    .operations = &usbSerialOperations_FTDI_FT232BM
  },

  { /* Baum EcoVario 64 (64 cells) */
    .vendor=0X0904, .product=0X2015,
    .operations = &usbSerialOperations_FTDI_FT232BM
  },

  { /* Baum EcoVario 80 (80 cells) */
    .vendor=0X0904, .product=0X2016,
    .operations = &usbSerialOperations_FTDI_FT232BM
  },

  { /* Baum Refreshabraille 18 (18 cells) */
    .vendor=0X0904, .product=0X3000,
    .operations = &usbSerialOperations_FTDI_FT232BM
  },

  { /* HandyTech GoHubs */
    .vendor=0X0921, .product=0X1200,
    .operations = &usbSerialOperations_Belkin
  },

  { /* BrailleMemo Pocket, Seika BrailleDisplay */
    .vendor=0X10C4, .product=0XEA60,
    .generic = 1,
    .operations = &usbSerialOperations_CP2101
  },

  { /* Seika NoteTaker */
    .vendor=0X10C4, .product=0XEA80,
    .generic = 1,
    .operations = &usbSerialOperations_CP2110
  },

  { /* Canute */
    .vendor=0X16C0, .product=0X05E1,
    .operations = &usbSerialOperations_CDC_ACM
  },

  { /* NLS eReader Zoomax */
    .vendor=0X1A86, .product=0X7523,
    .generic = 1,
    .operations = &usbSerialOperations_CH341
  },
};

const size_t usbSerialAdapterCount = ARRAY_COUNT(usbSerialAdapterTable);
