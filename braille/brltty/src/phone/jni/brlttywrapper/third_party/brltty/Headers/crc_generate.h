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

#ifndef BRLTTY_INCLUDED_CRC_GENERATE
#define BRLTTY_INCLUDED_CRC_GENERATE

#include <sys/types.h>
#include "crc_algorithms.h"
#include "crc_properties.h"

#ifdef __cplusplus
extern "C" {
#endif /* __cplusplus */

typedef struct CRCGeneratorStruct CRCGenerator;
extern CRCGenerator *crcNewGenerator (const CRCAlgorithm *algorithm);
extern void crcResetGenerator (CRCGenerator *crc);
extern void crcDestroyGenerator (CRCGenerator *crc);

extern void crcAddByte (CRCGenerator *crc, uint8_t byte);
extern void crcAddData (CRCGenerator *crc, const void *data, size_t size);

extern crc_t crcGetChecksum (const CRCGenerator *crc);
extern crc_t crcGetResidue (CRCGenerator *crc);

extern const CRCAlgorithm *crcGetAlgorithm (const CRCGenerator *crc);
extern const CRCProperties *crcGetProperties (const CRCGenerator *crc);
extern crc_t crcGetValue (const CRCGenerator *crc);

#ifdef __cplusplus
}
#endif /* __cplusplus */

#endif /* BRLTTY_INCLUDED_CRC_GENERATE */
