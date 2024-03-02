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

#include "crc_generate.h"
#include "crc_internal.h"
#include "log.h"

crc_t
crcMostSignificantBit (unsigned int width) {
  return CRC_C(1) << (width - 1);
}

crc_t
crcReflectBits (crc_t fromValue, unsigned int width) {
  crc_t fromBit = crcMostSignificantBit(width);
  crc_t toBit = 1;
  crc_t toValue = 0;

  while (fromBit) {
    if (fromValue & fromBit) toValue |= toBit;
    fromBit >>= 1;
    toBit <<= 1;
  }

  return toValue;
}

void
crcReflectValue (crc_t *value, const CRCAlgorithm *algorithm) {
  *value = crcReflectBits(*value, algorithm->checksumWidth);
}

void
crcReflectByte (uint8_t *byte) {
  *byte = crcReflectBits(*byte, CRC_BYTE_WIDTH);
}

static uint8_t crcDirectDataTranslationTable[CRC_BYTE_INDEXED_TABLE_SIZE] = {1};
static uint8_t crcReflectedDataTranslationTable[CRC_BYTE_INDEXED_TABLE_SIZE] = {1};

static void
crcMakeDataTranslationTable (CRCProperties *properties, const CRCAlgorithm *algorithm) {
  if (algorithm->reflectData) {
    uint8_t *table = crcReflectedDataTranslationTable;
    properties->dataTranslationTable = table;

    if (*table) {
      for (unsigned int index=0; index<=UINT8_MAX; index+=1) {
        uint8_t *byte = &table[index];
        *byte = index;
        crcReflectByte(byte);
      }
    }
  } else {
    uint8_t *table = crcDirectDataTranslationTable;
    properties->dataTranslationTable = table;

    if (*table) {
      for (unsigned int index=0; index<=UINT8_MAX; index+=1) {
        table[index] = index;
      }
    }
  }
}

static void
crcMakeRemainderCache (CRCProperties *properties, const CRCAlgorithm *algorithm) {
  // Compute the remainder for each possible dividend.
  for (unsigned int dividend=0; dividend<=UINT8_MAX; dividend+=1) {
    // Start with the dividend followed by zeros.
    crc_t remainder = dividend << properties->byteShift;

    // Perform modulo-2 division, a bit at a time.
    for (unsigned int bit=CRC_BYTE_WIDTH; bit>0; bit-=1) {
      // Try to divide the current data bit.
      if (remainder & properties->mostSignificantBit) {
        remainder <<= 1;
        remainder ^= algorithm->generatorPolynomial;
      } else {
        remainder <<= 1;
      }
    }

    // Store the result into the table.
    properties->remainderCache[dividend] = remainder & properties->valueMask;
  }
}

void
crcMakeProperties (CRCProperties *properties, const CRCAlgorithm *algorithm) {
  properties->byteShift = algorithm->checksumWidth - CRC_BYTE_WIDTH;
  properties->mostSignificantBit = crcMostSignificantBit(algorithm->checksumWidth);
  properties->valueMask = (properties->mostSignificantBit - 1) | properties->mostSignificantBit;

  crcMakeDataTranslationTable(properties, algorithm);
  crcMakeRemainderCache(properties, algorithm);
}

void
crcResetGenerator (CRCGenerator *crc) {
  crc->currentValue = crc->algorithm.initialValue;
}

CRCGenerator *
crcNewGenerator (const CRCAlgorithm *algorithm) {
  CRCGenerator *crc;
  const char *name = algorithm->primaryName;
  size_t size = sizeof(*crc) + strlen(name) + 1;

  if ((crc = malloc(size))) {
    memset(crc, 0, size);

    crc->algorithm = *algorithm;
    strcpy(crc->algorithmName, name);
    crc->algorithm.primaryName = crc->algorithmName;

    crcMakeProperties(&crc->properties, &crc->algorithm);
    crcResetGenerator(crc);
    return crc;
  } else {
    logMallocError();
  }

  return NULL;
}

void
crcDestroyGenerator (CRCGenerator *crc) {
  free(crc);
}

void
crcAddByte (CRCGenerator *crc, uint8_t byte) {
  byte = crc->properties.dataTranslationTable[byte];
  byte ^= crc->currentValue >> crc->properties.byteShift;
  crc->currentValue = crc->properties.remainderCache[byte] ^ (crc->currentValue << CRC_BYTE_WIDTH);
  crc->currentValue &= crc->properties.valueMask;
}

void
crcAddData (CRCGenerator *crc, const void *data, size_t size) {
  const uint8_t *byte = data;
  const uint8_t *end = byte + size;
  while (byte < end) crcAddByte(crc, *byte++);
}

crc_t
crcGetValue (const CRCGenerator *crc) {
  return crc->currentValue;
}

crc_t
crcGetChecksum (const CRCGenerator *crc) {
  const CRCAlgorithm *algorithm = &crc->algorithm;
  crc_t checksum = crc->currentValue;
  if (crc->algorithm.reflectResult) crcReflectValue(&checksum, algorithm);
  checksum ^= algorithm->xorMask;
  return checksum;
}

crc_t
crcGetResidue (CRCGenerator *crc) {
  const CRCAlgorithm *algorithm = &crc->algorithm;

  crc_t originalValue = crc->currentValue;
  crc_t checksum = crcGetChecksum(crc);

  unsigned int size = algorithm->checksumWidth / CRC_BYTE_WIDTH;
  uint8_t data[size];

  if (algorithm->reflectResult) {
    uint8_t *byte = data;
    const uint8_t *end = byte + size;

    while (byte < end) {
      *byte++ = checksum;
      checksum >>= CRC_BYTE_WIDTH;
    }
  } else {
    uint8_t *byte = data + size;

    while (byte-- > data) {
      *byte = checksum;
      checksum >>= CRC_BYTE_WIDTH;
    }
  }

  crcAddData(crc, data, size);
  crc_t residue = crc->currentValue;
  if (algorithm->reflectResult) crcReflectValue(&residue, algorithm);

  crc->currentValue = originalValue;
  return residue;
}

const CRCAlgorithm *
crcGetAlgorithm (const CRCGenerator *crc) {
  return &crc->algorithm;
}

const CRCProperties *
crcGetProperties (const CRCGenerator *crc) {
  return &crc->properties;
}
