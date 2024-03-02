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

#ifndef BRLTTY_INCLUDED_CRC_VERIFY
#define BRLTTY_INCLUDED_CRC_VERIFY

#include <sys/types.h>
#include <stdint.h>
#include "crc_generate.h"

#ifdef __cplusplus
extern "C" {
#endif /* __cplusplus */

extern const uint8_t crcCheckData[];
extern const uint8_t crcCheckSize;

extern int crcVerifyChecksum (const CRCGenerator *crc, crc_t expected);
extern int crcVerifyResidue (CRCGenerator *crc);

extern int crcVerifyAlgorithm (const CRCAlgorithm *algorithm);
extern int crcVerifyProvidedAlgorithms (void);

extern int crcVerifyAlgorithmWithData (
  const CRCAlgorithm *algorithm,
  const void *data, size_t size, crc_t expected
);

extern int crcVerifyAlgorithmWithString (
  const CRCAlgorithm *algorithm,
  const char *string, crc_t expected
);

#ifdef __cplusplus
}
#endif /* __cplusplus */

#endif /* BRLTTY_INCLUDED_CRC_VERIFY */
