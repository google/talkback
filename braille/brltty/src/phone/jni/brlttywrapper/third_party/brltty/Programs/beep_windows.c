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

#include "beep.h"

int
canBeep (void) {
  return 1;
}

int
asynchronousBeep (BeepFrequency frequency, BeepDuration duration) {
  return 0;
}

int
synchronousBeep (BeepFrequency frequency, BeepDuration duration) {
  return Beep(frequency, duration);
}

int
startBeep (BeepFrequency frequency) {
  return 0;
}

int
stopBeep (void) {
  return 0;
}

void
endBeep (void) {
}
