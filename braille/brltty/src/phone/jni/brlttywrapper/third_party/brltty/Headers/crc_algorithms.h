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

#ifndef BRLTTY_INCLUDED_CRC_ALGORITHMS
#define BRLTTY_INCLUDED_CRC_ALGORITHMS

#include "crc_definitions.h"

#ifdef __cplusplus
extern "C" {
#endif /* __cplusplus */

typedef enum {
  CRC_ALGORITHM_CLASS_UNKNOWN,
  CRC_ALGORITHM_CLASS_ATTESTED,
  CRC_ALGORITHM_CLASS_CONFIRMED,
  CRC_ALGORITHM_CLASS_ACADEMIC,
  CRC_ALGORITHM_CLASS_THIRD_PARTY,
} CRCAlgorithmClass;

typedef struct {
  const char *primaryName; // the official name of the algorithm
  const char *const *secondaryNames; // other names that the algorithm is known by (NULL terminated)
  CRCAlgorithmClass algorithmClass;

  unsigned char checksumWidth; // the width of the checksum (in bits)
  unsigned char reflectData; // reflect each data byte before processing it
  unsigned char reflectResult; // reflect the final value (before the xor)

  crc_t generatorPolynomial; // the polynomial that generates the checksum
  crc_t initialValue; // the starting value (before any processing)
  crc_t xorMask; // the xor (exclussive or) mask to apply to the final value

  crc_t checkValue; // the checksum for the official check data ("123456789")
  crc_t residue; // the final value (no reflection or xor) of the check data
                 // followed by its checksum (in network byte order)
} CRCAlgorithm;

extern const CRCAlgorithm *crcProvidedAlgorithms[];
extern const CRCAlgorithm *crcGetProvidedAlgorithm (const char *name);

#ifdef __cplusplus
}
#endif /* __cplusplus */

#endif /* BRLTTY_INCLUDED_CRC_ALGORITHMS */
