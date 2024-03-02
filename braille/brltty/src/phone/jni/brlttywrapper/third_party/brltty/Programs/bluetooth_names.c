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

#include "bluetooth_internal.h"

#define BLUETOOTH_NAME_ENTRY(name,...) { \
  .namePrefix = name, \
  .driverCodes = NULL_TERMINATED_STRING_ARRAY(__VA_ARGS__) \
}

const BluetoothNameEntry bluetoothNameTable[] = {
  // HandyTech: Actilino
  BLUETOOTH_NAME_ENTRY("Actilino ALO", "ht"),

  // HandyTech: Activator
  BLUETOOTH_NAME_ENTRY("Activator AC4", "ht"),

  // HandyTech: Active Braille
  BLUETOOTH_NAME_ENTRY("Active Braille AB", "ht"),

  // HandyTech: Active Star
  BLUETOOTH_NAME_ENTRY("Active Star AS", "ht"),

  // Alva: Basic Controller (6nn)
  BLUETOOTH_NAME_ENTRY("ALVA BC", "al"),

  // HumanWare: APH Chameleon
  BLUETOOTH_NAME_ENTRY("APH Chameleon", "hw"),

  // HumanWare: APH Mantis
  BLUETOOTH_NAME_ENTRY("APH Mantis", "hw"),

  // HandyTech: Basic Braille
  BLUETOOTH_NAME_ENTRY("Basic Braille BB", "ht"),

  // HandyTech: Basic Braille Plus
  BLUETOOTH_NAME_ENTRY("Basic Braille Plus BP", "ht"),

  // Baum: Conny
  BLUETOOTH_NAME_ENTRY("BAUM Conny", "bm"),

  // Baum: Pocket Vario
  BLUETOOTH_NAME_ENTRY("Baum PocketVario", "bm"),

  // Baum: Super Vario
  BLUETOOTH_NAME_ENTRY("Baum SuperVario", "bm"),

  // Baum: Super Vario
  BLUETOOTH_NAME_ENTRY("Baum SVario", "bm"),

  // Baum: Braille Connect
  BLUETOOTH_NAME_ENTRY("BrailleConnect", "bm"),

  // HIMS: Braille Edge
  BLUETOOTH_NAME_ENTRY("BrailleEDGE", "hm"),

  // Inceptor: Braille Me
  BLUETOOTH_NAME_ENTRY("BrailleMe", "ic"),

  // KGS: Braille Memo Pocket
  BLUETOOTH_NAME_ENTRY("BMpk", "mm"),

  // KGS: Braille Memo Smart
  BLUETOOTH_NAME_ENTRY("BMsmart", "mm"),

  // KGS: Braille Memo 32
  BLUETOOTH_NAME_ENTRY("BM32", "mm"),

  // HumanWare: Braille Note Touch
  BLUETOOTH_NAME_ENTRY("BrailleNote Touch", "hw"),

  // HIMS: Braille Sense
  BLUETOOTH_NAME_ENTRY("BrailleSense", "hm"),

  // HandyTech: Braille Star
  BLUETOOTH_NAME_ENTRY("Braille Star", "ht"),

  // Papenmeier
  BLUETOOTH_NAME_ENTRY("Braillex", "pm"),

  // HumanWare: Brailliant BI
  BLUETOOTH_NAME_ENTRY("Brailliant BI", "hw"),

  // HumanWare: Brailliant BI 14
  BLUETOOTH_NAME_ENTRY("Brailliant 14", "hw"),

  // HumanWare: Brailliant B 80
  BLUETOOTH_NAME_ENTRY("Brailliant 80", "hw"),

  // HandyTech: Braillino
  BLUETOOTH_NAME_ENTRY("Braillino BL", "ht"),

  // National Braille Press: B2G
  BLUETOOTH_NAME_ENTRY("B2G", "bm"),

  // Baum: Conny
  BLUETOOTH_NAME_ENTRY("Conny", "bm"),

  // DotPad
  BLUETOOTH_NAME_ENTRY("DotPad", "dp"),

  // HandyTech: Easy Braille
  BLUETOOTH_NAME_ENTRY("Easy Braille EBR", "ht"),

  // Alva: EL12
  // Harpo: Braille Pen
  BLUETOOTH_NAME_ENTRY("EL12-", "al", "vo"),

  // EuroBraille
  BLUETOOTH_NAME_ENTRY("Esys-", "eu"),

  // Freedom Scientific: Focus
  BLUETOOTH_NAME_ENTRY("Focus", "fs"),

  // HumanWare: BrailleOne
  BLUETOOTH_NAME_ENTRY("Humanware BrailleOne", "hw"),

  // HumanWare: Brailliant
  BLUETOOTH_NAME_ENTRY("HWG Brailliant", "bm"),

  // MDV
  BLUETOOTH_NAME_ENTRY("MB248", "md"),

  // NLS eReader: HumanWare
  BLUETOOTH_NAME_ENTRY("NLS eReader H", "hw"),

  // NLS eReader: Zoomax
  BLUETOOTH_NAME_ENTRY("NLS eReader Z", "bm"),

  // American Printing House: Orbit Reader
  BLUETOOTH_NAME_ENTRY("Orbit Reader", "bm"),

  // Baum: Pronto!
  BLUETOOTH_NAME_ENTRY("Pronto!", "bm"),

  // American Printing House: Refreshabraille
  BLUETOOTH_NAME_ENTRY("Refreshabraille", "bm"),

  // HIMS: Smart Beetle
  BLUETOOTH_NAME_ENTRY("SmartBeetle", "hm"),

  // Baum: Super Vario
  BLUETOOTH_NAME_ENTRY("SuperVario", "bm"),

  // Seika: Note Taker
  BLUETOOTH_NAME_ENTRY("TSM", "sk"),

  // Baum: Vario Connect
  BLUETOOTH_NAME_ENTRY("VarioConnect", "bm"),

  // Baum: Vario Ultra
  BLUETOOTH_NAME_ENTRY("VarioUltra", "bm"),

  { .namePrefix = NULL }
};
