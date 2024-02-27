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

#ifndef BRLTTY_INCLUDED_SYSTEM_MSDOS
#define BRLTTY_INCLUDED_SYSTEM_MSDOS

#ifdef __cplusplus
extern "C" {
#endif /* __cplusplus */

extern void msdosBackground (void);

extern unsigned long msdosUSleep (unsigned long microseconds);

extern unsigned short msdosGetCodePage (void);

static inline unsigned long
msdosMakeAddress (unsigned short segment, unsigned short offset) {
  return ((unsigned long)segment << 4) + (unsigned long)offset;
}

static inline void msdosBreakAddress (
  unsigned long address, int absolute,
  unsigned short *segment, unsigned short *offset
) {
  if (absolute) {
    if (segment) *segment = (address >> 4) & 0XF000;
    if (offset) *offset = address & 0XFFFF;
  } else {
    if (segment) *segment = address >> 4;
    if (offset) *offset = address & 0XF;
  }
}

#define MSDOS_PIT_FREQUENCY UINT64_C(1193180)

#ifdef __cplusplus
}
#endif /* __cplusplus */

#endif /* BRLTTY_INCLUDED_SYSTEM_MSDOS */
