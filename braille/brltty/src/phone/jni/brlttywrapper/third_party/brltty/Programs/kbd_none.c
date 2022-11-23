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

#include "prologue.h"

#include "kbd.h"
#include "kbd_internal.h"

BEGIN_KEY_CODE_MAP
  [0] = KBD_KEY_UNMAPPED
END_KEY_CODE_MAP

int
newKeyboardMonitorExtension (KeyboardMonitorExtension **kmx) {
  return 1;
}

void
destroyKeyboardMonitorExtension (KeyboardMonitorExtension *kmx) {
}

int
newKeyboardInstanceExtension (KeyboardInstanceExtension **kix) {
  return 1;
}

void
destroyKeyboardInstanceExtension (KeyboardInstanceExtension *kix) {
}

int
forwardKeyEvent (KeyboardInstanceObject *kio, int code, int press) {
  return 0;
}

int
monitorKeyboards (KeyboardMonitorObject *kmo) {
  return 0;
}
