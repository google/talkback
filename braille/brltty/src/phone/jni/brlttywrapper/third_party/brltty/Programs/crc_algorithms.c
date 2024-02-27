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

#include "crc_algorithms.h"

#define CRC_ALGORITHM_SYMBOL(name) crcAlgorithm_ ## name
#define CRC_ALGORITHM_DEFINITION(name) static const CRCAlgorithm CRC_ALGORITHM_SYMBOL(name)
#define CRC_SECONDARY_NAMES(...) .secondaryNames = (const char *const []){__VA_ARGS__, NULL}

/*
 * These CRC algorithms have been copied from:
 * http://reveng.sourceforge.net/crc-catalogue/: 1-15.htm, 16.htm, 17plus.htm
 */

CRC_ALGORITHM_DEFINITION(CRC8_AUTOSAR) = {
  .primaryName = "CRC-8/AUTOSAR",
  .algorithmClass = CRC_ALGORITHM_CLASS_ATTESTED,

  .checksumWidth = 8,
  .generatorPolynomial = UINT8_C(0X2F),
  .initialValue = UINT8_MAX,
  .xorMask = UINT8_MAX,

  .checkValue = UINT8_C(0XDF),
  .residue = UINT8_C(0X42),
};

CRC_ALGORITHM_DEFINITION(CRC8_BLUETOOTH) = {
  .primaryName = "CRC-8/BLUETOOTH",
  .algorithmClass = CRC_ALGORITHM_CLASS_ATTESTED,

  .checksumWidth = 8,
  .reflectData = 1,
  .reflectResult = 1,
  .generatorPolynomial = UINT8_C(0XA7),

  .checkValue = UINT8_C(0X26),
};

CRC_ALGORITHM_DEFINITION(CRC8_CDMA2000) = {
  .primaryName = "CRC-8/CDMA2000",
  .algorithmClass = CRC_ALGORITHM_CLASS_ACADEMIC,

  .checksumWidth = 8,
  .generatorPolynomial = UINT8_C(0X9B),
  .initialValue = UINT8_MAX,

  .checkValue = UINT8_C(0XDA),
};

CRC_ALGORITHM_DEFINITION(CRC8_DARC) = {
  .primaryName = "CRC-8/DARC",
  .algorithmClass = CRC_ALGORITHM_CLASS_ATTESTED,

  .checksumWidth = 8,
  .reflectData = 1,
  .reflectResult = 1,
  .generatorPolynomial = UINT8_C(0X39),

  .checkValue = UINT8_C(0X15),
};

CRC_ALGORITHM_DEFINITION(CRC8_DVB_S2) = {
  .primaryName = "CRC-8/DVB-S2",
  .algorithmClass = CRC_ALGORITHM_CLASS_ACADEMIC,

  .checksumWidth = 8,
  .generatorPolynomial = UINT8_C(0XD5),

  .checkValue = UINT8_C(0XBC),
};

CRC_ALGORITHM_DEFINITION(CRC8_GSM_A) = {
  .primaryName = "CRC-8/GSM-A",
  .algorithmClass = CRC_ALGORITHM_CLASS_ACADEMIC,

  .checksumWidth = 8,
  .generatorPolynomial = UINT8_C(0X1D),

  .checkValue = UINT8_C(0X37),
};

CRC_ALGORITHM_DEFINITION(CRC8_GSM_B) = {
  .primaryName = "CRC-8/GSM-B",
  .algorithmClass = CRC_ALGORITHM_CLASS_ACADEMIC,

  .checksumWidth = 8,
  .generatorPolynomial = UINT8_C(0X49),
  .xorMask = UINT8_MAX,

  .checkValue = UINT8_C(0X94),
  .residue = UINT8_C(0X53),
};

CRC_ALGORITHM_DEFINITION(CRC8_I_432_1) = {
  .primaryName = "CRC-8/I-432-1",
  .algorithmClass = CRC_ALGORITHM_CLASS_ACADEMIC,
  CRC_SECONDARY_NAMES("CRC-8/ITU"),

  .checksumWidth = 8,
  .generatorPolynomial = UINT8_C(0X07),
  .xorMask = UINT8_C(0X55),

  .checkValue = UINT8_C(0XA1),
  .residue = UINT8_C(0XAC),
};

CRC_ALGORITHM_DEFINITION(CRC8_I_CODE) = {
  .primaryName = "CRC-8/I-CODE",
  .algorithmClass = CRC_ALGORITHM_CLASS_ATTESTED,

  .checksumWidth = 8,
  .generatorPolynomial = UINT8_C(0X1D),
  .initialValue = UINT8_C(0XFD),

  .checkValue = UINT8_C(0X7E),
};

CRC_ALGORITHM_DEFINITION(CRC8_LTE) = {
  .primaryName = "CRC-8/LTE",
  .algorithmClass = CRC_ALGORITHM_CLASS_ACADEMIC,

  .checksumWidth = 8,
  .generatorPolynomial = UINT8_C(0X9B),

  .checkValue = UINT8_C(0XEA),
};

CRC_ALGORITHM_DEFINITION(CRC8_MAXIM_DOW) = {
  .primaryName = "CRC-8/MAXIM-DOW",
  .algorithmClass = CRC_ALGORITHM_CLASS_ATTESTED,
  CRC_SECONDARY_NAMES("CRC-8/MAXIM", "DOW-CRC"),

  .checksumWidth = 8,
  .reflectData = 1,
  .reflectResult = 1,
  .generatorPolynomial = UINT8_C(0X31),

  .checkValue = UINT8_C(0XA1),
};

CRC_ALGORITHM_DEFINITION(CRC8_MIFARE_MAD) = {
  .primaryName = "CRC-8/MIFARE-MAD",
  .algorithmClass = CRC_ALGORITHM_CLASS_ATTESTED,

  .checksumWidth = 8,
  .generatorPolynomial = UINT8_C(0X1D),
  .initialValue = UINT8_C(0XC7),

  .checkValue = UINT8_C(0X99),
};

CRC_ALGORITHM_DEFINITION(CRC8_NRSC_5) = {
  .primaryName = "CRC-8/NRSC-5",
  .algorithmClass = CRC_ALGORITHM_CLASS_ATTESTED,

  .checksumWidth = 8,
  .generatorPolynomial = UINT8_C(0X31),
  .initialValue = UINT8_MAX,

  .checkValue = UINT8_C(0XF7),
};

CRC_ALGORITHM_DEFINITION(CRC8_OPENSAFETY) = {
  .primaryName = "CRC-8/OPENSAFETY",
  .algorithmClass = CRC_ALGORITHM_CLASS_ATTESTED,

  .checksumWidth = 8,
  .generatorPolynomial = UINT8_C(0X2F),

  .checkValue = UINT8_C(0X3E),
};

CRC_ALGORITHM_DEFINITION(CRC8_ROHC) = {
  .primaryName = "CRC-8/ROHC",
  .algorithmClass = CRC_ALGORITHM_CLASS_ACADEMIC,

  .checksumWidth = 8,
  .reflectData = 1,
  .reflectResult = 1,
  .generatorPolynomial = UINT8_C(0X07),
  .initialValue = UINT8_MAX,

  .checkValue = UINT8_C(0XD0),
};

CRC_ALGORITHM_DEFINITION(CRC8_SAE_J1850) = {
  .primaryName = "CRC-8/SAE-J1850",
  .algorithmClass = CRC_ALGORITHM_CLASS_ATTESTED,

  .checksumWidth = 8,
  .generatorPolynomial = UINT8_C(0X1D),
  .initialValue = UINT8_MAX,
  .xorMask = UINT8_MAX,

  .checkValue = UINT8_C(0X4B),
  .residue = UINT8_C(0XC4),
};

CRC_ALGORITHM_DEFINITION(CRC8_SMBUS) = {
  .primaryName = "CRC-8/SMBUS",
  .algorithmClass = CRC_ALGORITHM_CLASS_ATTESTED,
  CRC_SECONDARY_NAMES("CRC-8"),

  .checksumWidth = 8,
  .generatorPolynomial = UINT8_C(0X07),

  .checkValue = UINT8_C(0XF4),
};

CRC_ALGORITHM_DEFINITION(CRC8_TECH_3250) = {
  .primaryName = "CRC-8/TECH-3250",
  .algorithmClass = CRC_ALGORITHM_CLASS_ATTESTED,
  CRC_SECONDARY_NAMES("CRC-8/AES", "CRC-8/EBU"),

  .checksumWidth = 8,
  .reflectData = 1,
  .reflectResult = 1,
  .generatorPolynomial = UINT8_C(0X1D),
  .initialValue = UINT8_MAX,

  .checkValue = UINT8_C(0X97),
};

CRC_ALGORITHM_DEFINITION(CRC8_WCDMA) = {
  .primaryName = "CRC-8/WCDMA",
  .algorithmClass = CRC_ALGORITHM_CLASS_THIRD_PARTY,

  .checksumWidth = 8,
  .reflectData = 1,
  .reflectResult = 1,
  .generatorPolynomial = UINT8_C(0X9B),

  .checkValue = UINT8_C(0X25),
};

CRC_ALGORITHM_DEFINITION(CRC16_ARC) = {
  .primaryName = "CRC-16/ARC",
  .algorithmClass = CRC_ALGORITHM_CLASS_ATTESTED,
  CRC_SECONDARY_NAMES("ARC", "CRC-16", "CRC-16/LHA", "CRC-IBM"),

  .checksumWidth = 16,
  .reflectData = 1,
  .reflectResult = 1,
  .generatorPolynomial = UINT16_C(0X8005),

  .checkValue = UINT16_C(0XBB3D),
};

CRC_ALGORITHM_DEFINITION(CRC16_CDMA2000) = {
  .primaryName = "CRC-16/CDMA2000",
  .algorithmClass = CRC_ALGORITHM_CLASS_ACADEMIC,

  .checksumWidth = 16,
  .generatorPolynomial = UINT16_C(0XC867),
  .initialValue = UINT16_MAX,

  .checkValue = UINT16_C(0X4C06),
};

CRC_ALGORITHM_DEFINITION(CRC16_CMS) = {
  .primaryName = "CRC-16/CMS",
  .algorithmClass = CRC_ALGORITHM_CLASS_THIRD_PARTY,

  .checksumWidth = 16,
  .generatorPolynomial = UINT16_C(0X8005),
  .initialValue = UINT16_MAX,

  .checkValue = UINT16_C(0XAEE7),
};

CRC_ALGORITHM_DEFINITION(CRC16_DDS_110) = {
  .primaryName = "CRC-16/DDS-110",
  .algorithmClass = CRC_ALGORITHM_CLASS_ATTESTED,

  .checksumWidth = 16,
  .generatorPolynomial = UINT16_C(0X8005),
  .initialValue = UINT16_C(0X800D),

  .checkValue = UINT16_C(0X9ECF),
};

CRC_ALGORITHM_DEFINITION(CRC16_DECT_R) = {
  .primaryName = "CRC-16/DECT-R",
  .algorithmClass = CRC_ALGORITHM_CLASS_ATTESTED,
  CRC_SECONDARY_NAMES("R-CRC-16"),

  .checksumWidth = 16,
  .generatorPolynomial = UINT16_C(0X0589),
  .xorMask = UINT16_C(0X0001),

  .checkValue = UINT16_C(0X007E),
  .residue = UINT16_C(0X0589),
};

CRC_ALGORITHM_DEFINITION(CRC16_DECT_X) = {
  .primaryName = "CRC-16/DECT-X",
  .algorithmClass = CRC_ALGORITHM_CLASS_ATTESTED,
  CRC_SECONDARY_NAMES("X-CRC-16"),

  .checksumWidth = 16,
  .generatorPolynomial = UINT16_C(0X0589),

  .checkValue = UINT16_C(0X007F),
};

CRC_ALGORITHM_DEFINITION(CRC16_DNP) = {
  .primaryName = "CRC-16/DNP",
  .algorithmClass = CRC_ALGORITHM_CLASS_CONFIRMED,

  .checksumWidth = 16,
  .reflectData = 1,
  .reflectResult = 1,
  .generatorPolynomial = UINT16_C(0X3D65),
  .xorMask = UINT16_MAX,

  .checkValue = UINT16_C(0XEA82),
  .residue = UINT16_C(0X66C5),
};

CRC_ALGORITHM_DEFINITION(CRC16_EN_13757) = {
  .primaryName = "CRC-16/EN-13757",
  .algorithmClass = CRC_ALGORITHM_CLASS_CONFIRMED,

  .checksumWidth = 16,
  .generatorPolynomial = UINT16_C(0X3D65),
  .xorMask = UINT16_MAX,

  .checkValue = UINT16_C(0XC2B7),
  .residue = UINT16_C(0XA366),
};

CRC_ALGORITHM_DEFINITION(CRC16_GENIBUS) = {
  .primaryName = "CRC-16/GENIBUS",
  .algorithmClass = CRC_ALGORITHM_CLASS_ATTESTED,
  CRC_SECONDARY_NAMES("CRC-16/DARC", "CRC-16/EPC", "CRC-16/EPC-C1G2", "CRC-16/I-CODE"),

  .checksumWidth = 16,
  .generatorPolynomial = UINT16_C(0X1021),
  .initialValue = UINT16_MAX,
  .xorMask = UINT16_MAX,

  .checkValue = UINT16_C(0XD64E),
  .residue = UINT16_C(0X1D0F),
};

CRC_ALGORITHM_DEFINITION(CRC16_GSM) = {
  .primaryName = "CRC-16/GSM",
  .algorithmClass = CRC_ALGORITHM_CLASS_ATTESTED,

  .checksumWidth = 16,
  .generatorPolynomial = UINT16_C(0X1021),
  .xorMask = UINT16_MAX,

  .checkValue = UINT16_C(0XCE3C),
  .residue = UINT16_C(0X1D0F),
};

CRC_ALGORITHM_DEFINITION(CRC16_IBM_3740) = {
  .primaryName = "CRC-16/IBM-3740",
  .algorithmClass = CRC_ALGORITHM_CLASS_ATTESTED,
  CRC_SECONDARY_NAMES("CRC-16/AUTOSAR", "CRC-16/CCITT-FALSE"),

  .checksumWidth = 16,
  .generatorPolynomial = UINT16_C(0X1021),
  .initialValue = UINT16_MAX,

  .checkValue = UINT16_C(0X29B1),
};

CRC_ALGORITHM_DEFINITION(CRC16_IBM_SDLC) = {
  .primaryName = "CRC-16/IBM-SDLC",
  .algorithmClass = CRC_ALGORITHM_CLASS_ATTESTED,
  CRC_SECONDARY_NAMES("CRC-16/ISO-HDLC", "CRC-16/ISO-IEC-14443-3-B", "CRC-16/X-25", "CRC-B", "X-25"),

  .checksumWidth = 16,
  .reflectData = 1,
  .reflectResult = 1,
  .generatorPolynomial = UINT16_C(0X1021),
  .initialValue = UINT16_MAX,
  .xorMask = UINT16_MAX,

  .checkValue = UINT16_C(0X906E),
  .residue = UINT16_C(0XF0B8),
};

CRC_ALGORITHM_DEFINITION(CRC16_ISO_IEC_14443_3_A) = {
  .primaryName = "CRC-16/ISO-IEC-14443-3-A",
  .algorithmClass = CRC_ALGORITHM_CLASS_ATTESTED,
  CRC_SECONDARY_NAMES("CRC-A"),

  .checksumWidth = 16,
  .reflectData = 1,
  .reflectResult = 1,
  .generatorPolynomial = UINT16_C(0X1021),
  .initialValue = UINT16_C(0XC6C6),

  .checkValue = UINT16_C(0XBF05),
};

CRC_ALGORITHM_DEFINITION(CRC16_KERMIT) = {
  .primaryName = "CRC-16/KERMIT",
  .algorithmClass = CRC_ALGORITHM_CLASS_ATTESTED,
  CRC_SECONDARY_NAMES("CRC-16/CCITT", "CRC-16/CCITT-TRUE", "CRC-16/V-41-LSB", "CRC-CCITT", "KERMIT"),

  .checksumWidth = 16,
  .reflectData = 1,
  .reflectResult = 1,
  .generatorPolynomial = UINT16_C(0X1021),

  .checkValue = UINT16_C(0X2189),
};

CRC_ALGORITHM_DEFINITION(CRC16_LJ1200) = {
  .primaryName = "CRC-16/LJ1200",
  .algorithmClass = CRC_ALGORITHM_CLASS_THIRD_PARTY,

  .checksumWidth = 16,
  .generatorPolynomial = UINT16_C(0X6F63),

  .checkValue = UINT16_C(0XBDF4),
};

CRC_ALGORITHM_DEFINITION(CRC16_MAXIM_DOW) = {
  .primaryName = "CRC-16/MAXIM-DOW",
  .algorithmClass = CRC_ALGORITHM_CLASS_ATTESTED,
  CRC_SECONDARY_NAMES("CRC-16/MAXIM"),

  .checksumWidth = 16,
  .reflectData = 1,
  .reflectResult = 1,
  .generatorPolynomial = UINT16_C(0X8005),
  .xorMask = UINT16_MAX,

  .checkValue = UINT16_C(0X44C2),
  .residue = UINT16_C(0XB001),
};

CRC_ALGORITHM_DEFINITION(CRC16_MCRF4XX) = {
  .primaryName = "CRC-16/MCRF4XX",
  .algorithmClass = CRC_ALGORITHM_CLASS_ATTESTED,

  .checksumWidth = 16,
  .reflectData = 1,
  .reflectResult = 1,
  .generatorPolynomial = UINT16_C(0X1021),
  .initialValue = UINT16_MAX,

  .checkValue = UINT16_C(0X6F91),
};

CRC_ALGORITHM_DEFINITION(CRC16_MODBUS) = {
  .primaryName = "CRC-16/MODBUS",
  .algorithmClass = CRC_ALGORITHM_CLASS_ATTESTED,
  CRC_SECONDARY_NAMES("MODBUS"),

  .checksumWidth = 16,
  .reflectData = 1,
  .reflectResult = 1,
  .generatorPolynomial = UINT16_C(0X8005),
  .initialValue = UINT16_MAX,

  .checkValue = UINT16_C(0X4B37),
};

CRC_ALGORITHM_DEFINITION(CRC16_NRSC_5) = {
  .primaryName = "CRC-16/NRSC-5",
  .algorithmClass = CRC_ALGORITHM_CLASS_ATTESTED,

  .checksumWidth = 16,
  .reflectData = 1,
  .reflectResult = 1,
  .generatorPolynomial = UINT16_C(0X080B),
  .initialValue = UINT16_MAX,

  .checkValue = UINT16_C(0XA066),
};

CRC_ALGORITHM_DEFINITION(CRC16_OPENSAFETY_A) = {
  .primaryName = "CRC-16/OPENSAFETY-A",
  .algorithmClass = CRC_ALGORITHM_CLASS_ATTESTED,

  .checksumWidth = 16,
  .generatorPolynomial = UINT16_C(0X5935),

  .checkValue = UINT16_C(0X5D38),
};

CRC_ALGORITHM_DEFINITION(CRC16_OPENSAFETY_B) = {
  .primaryName = "CRC-16/OPENSAFETY-B",
  .algorithmClass = CRC_ALGORITHM_CLASS_ATTESTED,

  .checksumWidth = 16,
  .generatorPolynomial = UINT16_C(0X755B),

  .checkValue = UINT16_C(0X20FE),
};

CRC_ALGORITHM_DEFINITION(CRC16_PROFIBUS) = {
  .primaryName = "CRC-16/PROFIBUS",
  .algorithmClass = CRC_ALGORITHM_CLASS_ATTESTED,
  CRC_SECONDARY_NAMES("CRC-16/IEC-61158-2"),

  .checksumWidth = 16,
  .generatorPolynomial = UINT16_C(0X1DCF),
  .initialValue = UINT16_MAX,
  .xorMask = UINT16_MAX,

  .checkValue = UINT16_C(0XA819),
  .residue = UINT16_C(0XE394),
};

CRC_ALGORITHM_DEFINITION(CRC16_RIELLO) = {
  .primaryName = "CRC-16/RIELLO",
  .algorithmClass = CRC_ALGORITHM_CLASS_THIRD_PARTY,

  .checksumWidth = 16,
  .reflectData = 1,
  .reflectResult = 1,
  .generatorPolynomial = UINT16_C(0X1021),
  .initialValue = UINT16_C(0XB2AA),

  .checkValue = UINT16_C(0X63D0),
};

CRC_ALGORITHM_DEFINITION(CRC16_SPI_FUJITSU) = {
  .primaryName = "CRC-16/SPI-FUJITSU",
  .algorithmClass = CRC_ALGORITHM_CLASS_ATTESTED,
  CRC_SECONDARY_NAMES("CRC-16/AUG-CCITT"),

  .checksumWidth = 16,
  .generatorPolynomial = UINT16_C(0X1021),
  .initialValue = UINT16_C(0X1D0F),

  .checkValue = UINT16_C(0XE5CC),
};

CRC_ALGORITHM_DEFINITION(CRC16_T10_DIF) = {
  .primaryName = "CRC-16/T10-DIF",
  .algorithmClass = CRC_ALGORITHM_CLASS_ATTESTED,

  .checksumWidth = 16,
  .generatorPolynomial = UINT16_C(0X8BB7),

  .checkValue = UINT16_C(0XD0DB),
};

CRC_ALGORITHM_DEFINITION(CRC16_TELEDISK) = {
  .primaryName = "CRC-16/TELEDISK",
  .algorithmClass = CRC_ALGORITHM_CLASS_CONFIRMED,

  .checksumWidth = 16,
  .generatorPolynomial = UINT16_C(0XA097),

  .checkValue = UINT16_C(0X0FB3),
};

CRC_ALGORITHM_DEFINITION(CRC16_TMS37157) = {
  .primaryName = "CRC-16/TMS37157",
  .algorithmClass = CRC_ALGORITHM_CLASS_ATTESTED,

  .checksumWidth = 16,
  .reflectData = 1,
  .reflectResult = 1,
  .generatorPolynomial = UINT16_C(0X1021),
  .initialValue = UINT16_C(0X89EC),

  .checkValue = UINT16_C(0X26B1),
};

CRC_ALGORITHM_DEFINITION(CRC16_UMTS) = {
  .primaryName = "CRC-16/UMTS",
  .algorithmClass = CRC_ALGORITHM_CLASS_ATTESTED,
  CRC_SECONDARY_NAMES("CRC-16/BUYPASS", "CRC-16/VERIFONE"),

  .checksumWidth = 16,
  .generatorPolynomial = UINT16_C(0X8005),

  .checkValue = UINT16_C(0XFEE8),
};

CRC_ALGORITHM_DEFINITION(CRC16_USB) = {
  .primaryName = "CRC-16/USB",
  .algorithmClass = CRC_ALGORITHM_CLASS_THIRD_PARTY,

  .checksumWidth = 16,
  .reflectData = 1,
  .reflectResult = 1,
  .generatorPolynomial = UINT16_C(0X8005),
  .initialValue = UINT16_MAX,
  .xorMask = UINT16_MAX,

  .checkValue = UINT16_C(0XB4C8),
  .residue = UINT16_C(0XB001),
};

CRC_ALGORITHM_DEFINITION(CRC16_XMODEM) = {
  .primaryName = "CRC-16/XMODEM",
  .algorithmClass = CRC_ALGORITHM_CLASS_ATTESTED,
  CRC_SECONDARY_NAMES("CRC-16/ACORN", "CRC-16/LTE", "CRC-16/V-41-MSB", "XMODEM", "ZMODEM"),

  .checksumWidth = 16,
  .generatorPolynomial = UINT16_C(0X1021),

  .checkValue = UINT16_C(0X31C3),
};

CRC_ALGORITHM_DEFINITION(CRC24_BLE) = {
  .primaryName = "CRC-24/BLE",
  .algorithmClass = CRC_ALGORITHM_CLASS_ATTESTED,

  .checksumWidth = 24,
  .reflectData = 1,
  .reflectResult = 1,
  .generatorPolynomial = UINT24_C(0X00065B),
  .initialValue = UINT24_C(0X555555),

  .checkValue = UINT24_C(0XC25A56),
};

CRC_ALGORITHM_DEFINITION(CRC24_FLEXRAY_A) = {
  .primaryName = "CRC-24/FLEXRAY-A",
  .algorithmClass = CRC_ALGORITHM_CLASS_ATTESTED,

  .checksumWidth = 24,
  .generatorPolynomial = UINT24_C(0X5D6DCB),
  .initialValue = UINT24_C(0XFEDCBA),

  .checkValue = UINT24_C(0X7979BD),
};

CRC_ALGORITHM_DEFINITION(CRC24_FLEXRAY_B) = {
  .primaryName = "CRC-24/FLEXRAY-B",
  .algorithmClass = CRC_ALGORITHM_CLASS_ATTESTED,

  .checksumWidth = 24,
  .generatorPolynomial = UINT24_C(0X5D6DCB),
  .initialValue = UINT24_C(0XABCDEF),

  .checkValue = UINT24_C(0X1F23B8),
};

CRC_ALGORITHM_DEFINITION(CRC24_INTERLAKEN) = {
  .primaryName = "CRC-24/INTERLAKEN",
  .algorithmClass = CRC_ALGORITHM_CLASS_ACADEMIC,

  .checksumWidth = 24,
  .generatorPolynomial = UINT24_C(0X328B63),
  .initialValue = UINT24_MAX,
  .xorMask = UINT24_MAX,

  .checkValue = UINT24_C(0XB4F3E6),
  .residue = UINT24_C(0X144E63),
};

CRC_ALGORITHM_DEFINITION(CRC24_LTE_A) = {
  .primaryName = "CRC-24/LTE-A",
  .algorithmClass = CRC_ALGORITHM_CLASS_ACADEMIC,

  .checksumWidth = 24,
  .generatorPolynomial = UINT24_C(0X864CFB),

  .checkValue = UINT24_C(0XCDE703),
};

CRC_ALGORITHM_DEFINITION(CRC24_LTE_B) = {
  .primaryName = "CRC-24/LTE-B",
  .algorithmClass = CRC_ALGORITHM_CLASS_ACADEMIC,

  .checksumWidth = 24,
  .generatorPolynomial = UINT24_C(0X800063),

  .checkValue = UINT24_C(0X23EF52),
};

CRC_ALGORITHM_DEFINITION(CRC24_OPENPGP) = {
  .primaryName = "CRC-24/OPENPGP",
  .algorithmClass = CRC_ALGORITHM_CLASS_ATTESTED,
  CRC_SECONDARY_NAMES("CRC-24"),

  .checksumWidth = 24,
  .generatorPolynomial = UINT24_C(0X864CFB),
  .initialValue = UINT24_C(0XB704CE),

  .checkValue = UINT24_C(0X21CF02),
};

CRC_ALGORITHM_DEFINITION(CRC24_OS_9) = {
  .primaryName = "CRC-24/OS-9",
  .algorithmClass = CRC_ALGORITHM_CLASS_ATTESTED,

  .checksumWidth = 24,
  .generatorPolynomial = UINT24_C(0X800063),
  .initialValue = UINT24_MAX,
  .xorMask = UINT24_MAX,

  .checkValue = UINT24_C(0X200FA5),
  .residue = UINT24_C(0X800FE3),
};

CRC_ALGORITHM_DEFINITION(CRC32_AIXM) = {
  .primaryName = "CRC-32/AIXM",
  .algorithmClass = CRC_ALGORITHM_CLASS_ATTESTED,
  CRC_SECONDARY_NAMES("CRC-32Q"),

  .checksumWidth = 32,
  .generatorPolynomial = UINT32_C(0X814141AB),

  .checkValue = UINT32_C(0X3010BF7F),
};

CRC_ALGORITHM_DEFINITION(CRC32_AUTOSAR) = {
  .primaryName = "CRC-32/AUTOSAR",
  .algorithmClass = CRC_ALGORITHM_CLASS_ATTESTED,

  .checksumWidth = 32,
  .reflectData = 1,
  .reflectResult = 1,
  .generatorPolynomial = UINT32_C(0XF4ACFB13),
  .initialValue = UINT32_MAX,
  .xorMask = UINT32_MAX,

  .checkValue = UINT32_C(0X1697D06A),
  .residue = UINT32_C(0X904CDDBF),
};

CRC_ALGORITHM_DEFINITION(CRC32_BASE91_D) = {
  .primaryName = "CRC-32/BASE91-D",
  .algorithmClass = CRC_ALGORITHM_CLASS_CONFIRMED,
  CRC_SECONDARY_NAMES("CRC-32D"),

  .checksumWidth = 32,
  .reflectData = 1,
  .reflectResult = 1,
  .generatorPolynomial = UINT32_C(0XA833982B),
  .initialValue = UINT32_MAX,
  .xorMask = UINT32_MAX,

  .checkValue = UINT32_C(0X87315576),
  .residue = UINT32_C(0X45270551),
};

CRC_ALGORITHM_DEFINITION(CRC32_BZIP2) = {
  .primaryName = "CRC-32/BZIP2",
  .algorithmClass = CRC_ALGORITHM_CLASS_ATTESTED,
  CRC_SECONDARY_NAMES("CRC-32/AAL5", "CRC-32/DECT-B", "B-CRC-32"),

  .checksumWidth = 32,
  .generatorPolynomial = UINT32_C(0X04C11DB7),
  .initialValue = UINT32_MAX,
  .xorMask = UINT32_MAX,

  .checkValue = UINT32_C(0XFC891918),
  .residue = UINT32_C(0XC704DD7B),
};

CRC_ALGORITHM_DEFINITION(CRC32_CD_ROM_EDC) = {
  .primaryName = "CRC-32/CD-ROM-EDC",
  .algorithmClass = CRC_ALGORITHM_CLASS_ACADEMIC,

  .checksumWidth = 32,
  .reflectData = 1,
  .reflectResult = 1,
  .generatorPolynomial = UINT32_C(0X8001801B),

  .checkValue = UINT32_C(0X6EC2EDC4),
};

CRC_ALGORITHM_DEFINITION(CRC32_CKSUM) = {
  .primaryName = "CRC-32/CKSUM",
  .algorithmClass = CRC_ALGORITHM_CLASS_ATTESTED,
  CRC_SECONDARY_NAMES("CKSUM", "CRC-32/POSIX"),

  .checksumWidth = 32,
  .generatorPolynomial = UINT32_C(0X04C11DB7),
  .xorMask = UINT32_MAX,

  .checkValue = UINT32_C(0X765E7680),
  .residue = UINT32_C(0XC704DD7B),
};

CRC_ALGORITHM_DEFINITION(CRC32_ISCSI) = {
  .primaryName = "CRC-32/ISCSI",
  .algorithmClass = CRC_ALGORITHM_CLASS_ATTESTED,
  CRC_SECONDARY_NAMES("CRC-32/BASE91-C", "CRC-32/CASTAGNOLI", "CRC-32/INTERLAKEN", "CRC-32C"),

  .checksumWidth = 32,
  .reflectData = 1,
  .reflectResult = 1,
  .generatorPolynomial = UINT32_C(0X1EDC6F41),
  .initialValue = UINT32_MAX,
  .xorMask = UINT32_MAX,

  .checkValue = UINT32_C(0XE3069283),
  .residue = UINT32_C(0XB798B438),
};

CRC_ALGORITHM_DEFINITION(CRC32_ISO_HDLC) = {
  .primaryName = "CRC-32/ISO-HDLC",
  .algorithmClass = CRC_ALGORITHM_CLASS_ATTESTED,
  CRC_SECONDARY_NAMES("CRC-32", "CRC-32/ADCCP", "CRC-32/V-42", "CRC-32/XZ", "PKZIP"),

  .checksumWidth = 32,
  .reflectData = 1,
  .reflectResult = 1,
  .generatorPolynomial = UINT32_C(0X04C11DB7),
  .initialValue = UINT32_MAX,
  .xorMask = UINT32_MAX,

  .checkValue = UINT32_C(0XCBF43926),
  .residue = UINT32_C(0XDEBB20E3),
};

CRC_ALGORITHM_DEFINITION(CRC32_JAMCRC) = {
  .primaryName = "CRC-32/JAMCRC",
  .algorithmClass = CRC_ALGORITHM_CLASS_CONFIRMED,
  CRC_SECONDARY_NAMES("JAMCRC"),

  .checksumWidth = 32,
  .reflectData = 1,
  .reflectResult = 1,
  .generatorPolynomial = UINT32_C(0X04C11DB7),
  .initialValue = UINT32_MAX,

  .checkValue = UINT32_C(0X340BC6D9),
};

CRC_ALGORITHM_DEFINITION(CRC32_MPEG_2) = {
  .primaryName = "CRC-32/MPEG-2",
  .algorithmClass = CRC_ALGORITHM_CLASS_ATTESTED,

  .checksumWidth = 32,
  .generatorPolynomial = UINT32_C(0X04C11DB7),
  .initialValue = UINT32_MAX,

  .checkValue = UINT32_C(0X0376E6E7),
};

CRC_ALGORITHM_DEFINITION(CRC32_XFER) = {
  .primaryName = "CRC-32/XFER",
  .algorithmClass = CRC_ALGORITHM_CLASS_CONFIRMED,
  CRC_SECONDARY_NAMES("XFER"),

  .checksumWidth = 32,
  .generatorPolynomial = UINT32_C(0X000000AF),

  .checkValue = UINT32_C(0XBD0BE338),
};

const CRCAlgorithm *crcProvidedAlgorithms[] = {
  &CRC_ALGORITHM_SYMBOL(CRC8_AUTOSAR),
  &CRC_ALGORITHM_SYMBOL(CRC8_BLUETOOTH),
  &CRC_ALGORITHM_SYMBOL(CRC8_CDMA2000),
  &CRC_ALGORITHM_SYMBOL(CRC8_DARC),
  &CRC_ALGORITHM_SYMBOL(CRC8_DVB_S2),
  &CRC_ALGORITHM_SYMBOL(CRC8_GSM_A),
  &CRC_ALGORITHM_SYMBOL(CRC8_GSM_B),
  &CRC_ALGORITHM_SYMBOL(CRC8_I_432_1),
  &CRC_ALGORITHM_SYMBOL(CRC8_I_CODE),
  &CRC_ALGORITHM_SYMBOL(CRC8_LTE),
  &CRC_ALGORITHM_SYMBOL(CRC8_MAXIM_DOW),
  &CRC_ALGORITHM_SYMBOL(CRC8_MIFARE_MAD),
  &CRC_ALGORITHM_SYMBOL(CRC8_NRSC_5),
  &CRC_ALGORITHM_SYMBOL(CRC8_OPENSAFETY),
  &CRC_ALGORITHM_SYMBOL(CRC8_ROHC),
  &CRC_ALGORITHM_SYMBOL(CRC8_SAE_J1850),
  &CRC_ALGORITHM_SYMBOL(CRC8_SMBUS),
  &CRC_ALGORITHM_SYMBOL(CRC8_TECH_3250),
  &CRC_ALGORITHM_SYMBOL(CRC8_WCDMA),

  &CRC_ALGORITHM_SYMBOL(CRC16_ARC),
  &CRC_ALGORITHM_SYMBOL(CRC16_CDMA2000),
  &CRC_ALGORITHM_SYMBOL(CRC16_CMS),
  &CRC_ALGORITHM_SYMBOL(CRC16_DDS_110),
  &CRC_ALGORITHM_SYMBOL(CRC16_DECT_R),
  &CRC_ALGORITHM_SYMBOL(CRC16_DECT_X),
  &CRC_ALGORITHM_SYMBOL(CRC16_DNP),
  &CRC_ALGORITHM_SYMBOL(CRC16_EN_13757),
  &CRC_ALGORITHM_SYMBOL(CRC16_GENIBUS),
  &CRC_ALGORITHM_SYMBOL(CRC16_GSM),
  &CRC_ALGORITHM_SYMBOL(CRC16_IBM_3740),
  &CRC_ALGORITHM_SYMBOL(CRC16_IBM_SDLC),
  &CRC_ALGORITHM_SYMBOL(CRC16_ISO_IEC_14443_3_A),
  &CRC_ALGORITHM_SYMBOL(CRC16_KERMIT),
  &CRC_ALGORITHM_SYMBOL(CRC16_LJ1200),
  &CRC_ALGORITHM_SYMBOL(CRC16_MAXIM_DOW),
  &CRC_ALGORITHM_SYMBOL(CRC16_MCRF4XX),
  &CRC_ALGORITHM_SYMBOL(CRC16_MODBUS),
  &CRC_ALGORITHM_SYMBOL(CRC16_NRSC_5),
  &CRC_ALGORITHM_SYMBOL(CRC16_OPENSAFETY_A),
  &CRC_ALGORITHM_SYMBOL(CRC16_OPENSAFETY_B),
  &CRC_ALGORITHM_SYMBOL(CRC16_PROFIBUS),
  &CRC_ALGORITHM_SYMBOL(CRC16_RIELLO),
  &CRC_ALGORITHM_SYMBOL(CRC16_SPI_FUJITSU),
  &CRC_ALGORITHM_SYMBOL(CRC16_T10_DIF),
  &CRC_ALGORITHM_SYMBOL(CRC16_TELEDISK),
  &CRC_ALGORITHM_SYMBOL(CRC16_TMS37157),
  &CRC_ALGORITHM_SYMBOL(CRC16_UMTS),
  &CRC_ALGORITHM_SYMBOL(CRC16_USB),
  &CRC_ALGORITHM_SYMBOL(CRC16_XMODEM),

  &CRC_ALGORITHM_SYMBOL(CRC24_BLE),
  &CRC_ALGORITHM_SYMBOL(CRC24_FLEXRAY_A),
  &CRC_ALGORITHM_SYMBOL(CRC24_FLEXRAY_B),
  &CRC_ALGORITHM_SYMBOL(CRC24_INTERLAKEN),
  &CRC_ALGORITHM_SYMBOL(CRC24_LTE_A),
  &CRC_ALGORITHM_SYMBOL(CRC24_LTE_B),
  &CRC_ALGORITHM_SYMBOL(CRC24_OPENPGP),
  &CRC_ALGORITHM_SYMBOL(CRC24_OS_9),

  &CRC_ALGORITHM_SYMBOL(CRC32_AIXM),
  &CRC_ALGORITHM_SYMBOL(CRC32_AUTOSAR),
  &CRC_ALGORITHM_SYMBOL(CRC32_BASE91_D),
  &CRC_ALGORITHM_SYMBOL(CRC32_BZIP2),
  &CRC_ALGORITHM_SYMBOL(CRC32_CD_ROM_EDC),
  &CRC_ALGORITHM_SYMBOL(CRC32_CKSUM),
  &CRC_ALGORITHM_SYMBOL(CRC32_ISCSI),
  &CRC_ALGORITHM_SYMBOL(CRC32_ISO_HDLC),
  &CRC_ALGORITHM_SYMBOL(CRC32_JAMCRC),
  &CRC_ALGORITHM_SYMBOL(CRC32_MPEG_2),
  &CRC_ALGORITHM_SYMBOL(CRC32_XFER),

  NULL
};

const CRCAlgorithm *
crcGetProvidedAlgorithm (const char *name) {
  const CRCAlgorithm **algorithm = crcProvidedAlgorithms;

  while (*algorithm) {
    if (strcmp(name, (*algorithm)->primaryName) == 0) return *algorithm;
    const char *const *alias = (*algorithm)->secondaryNames;

    if (alias) {
      while (*alias) {
        if (strcmp(name, *alias) == 0) return *algorithm;
        alias += 1;
      }
    }

    algorithm += 1;
  }

  return NULL;
}
