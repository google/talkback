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

#ifndef BRLTTY_INCLUDED_CRC_DEFINITIONS
#define BRLTTY_INCLUDED_CRC_DEFINITIONS

#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif /* __cplusplus */

typedef uint32_t crc_t;
#define CRC_C UINT32_C
#define PRIcrc PRIX32

#ifndef UINT24_C
#define UINT24_C UINT32_C
#endif /* UINT24_C */

#ifndef UINT24_MAX
#define UINT24_MAX UINT24_C(0XFFFFFF)
#endif /* UINT24_MAX */

#ifdef __cplusplus
}
#endif /* __cplusplus */

#endif /* BRLTTY_INCLUDED_CRC_DEFINITIONS */
