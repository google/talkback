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

#ifndef BRLTTY_INCLUDED_HIDKEYS
#define BRLTTY_INCLUDED_HIDKEYS

#ifdef __cplusplus
extern "C" {
#endif /* __cplusplus */

typedef struct {
  uint8_t modifiers;
  uint8_t reserved;
  uint8_t keys[6];
} PACKED HidKeyboardPacket;

extern void initializeHidKeyboardPacket (HidKeyboardPacket *packet);
extern void processHidKeyboardPacket (
  HidKeyboardPacket *oldPacket,
  const HidKeyboardPacket *newPacket
);

#ifdef __cplusplus
}
#endif /* __cplusplus */

#endif /* BRLTTY_INCLUDED_HIDKEYS */
