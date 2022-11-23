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

#ifndef BRLTTY_INCLUDED_BEEP
#define BRLTTY_INCLUDED_BEEP

#ifdef __cplusplus
extern "C" {
#endif /* __cplusplus */

typedef uint16_t BeepFrequency;
typedef uint16_t BeepDuration;

extern int playBeep (BeepFrequency frequency, BeepDuration duration);

extern int canBeep (void);
extern int synchronousBeep (BeepFrequency frequency, BeepDuration duration);
extern int asynchronousBeep (BeepFrequency frequency, BeepDuration duration);
extern int startBeep (BeepFrequency frequency);
extern int stopBeep (void);
extern void endBeep (void);

#ifdef __cplusplus
}
#endif /* __cplusplus */

#endif /* BRLTTY_INCLUDED_BEEP */
