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

#ifndef BRLTTY_INCLUDED_SPK_BASE
#define BRLTTY_INCLUDED_SPK_BASE

#include "spk_types.h"

#ifdef __cplusplus
extern "C" {
#endif /* __cplusplus */

extern int tellSpeechFinished (volatile SpeechSynthesizer *spk);
extern int tellSpeechLocation (volatile SpeechSynthesizer *spk, int index);

extern unsigned int getIntegerSpeechVolume (unsigned char setting, unsigned int normal);
extern unsigned int getIntegerSpeechRate (unsigned char setting, unsigned int normal);
extern unsigned int getIntegerSpeechPitch (unsigned char setting, unsigned int normal);

#ifndef NO_FLOAT
extern float getFloatSpeechVolume (unsigned char setting);
extern float getFloatSpeechRate (unsigned char setting);
extern float getFloatSpeechPitch (unsigned char setting);
#endif /* NO_FLOAT */

#ifdef __cplusplus
}
#endif /* __cplusplus */

#endif /* BRLTTY_INCLUDED_SPK_BASE */
