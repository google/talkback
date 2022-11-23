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

#ifndef BRLTTY_INCLUDED_SPK
#define BRLTTY_INCLUDED_SPK

#include "spk_types.h"

#ifdef __cplusplus
extern "C" {
#endif /* __cplusplus */

extern void constructSpeechSynthesizer (volatile SpeechSynthesizer *spk);
extern void destructSpeechSynthesizer (volatile SpeechSynthesizer *spk);

extern int startSpeechDriverThread (volatile SpeechSynthesizer *spk, char **parameters);
extern void stopSpeechDriverThread (volatile SpeechSynthesizer *spk);

extern int muteSpeech (volatile SpeechSynthesizer *spk, const char *reason);

extern int canDrainSpeech (volatile SpeechSynthesizer *spk);
extern int drainSpeech (volatile SpeechSynthesizer *spk);

extern int sayUtf8Characters (
  volatile SpeechSynthesizer *spk,
  const char *text, const unsigned char *attributes,
  size_t length, size_t count,
  SayOptions options
);

extern int sayWideCharacters (
  volatile SpeechSynthesizer *spk,
  const wchar_t *characters, const unsigned char *attributes,
  size_t count, SayOptions options
);

extern int sayString (
  volatile SpeechSynthesizer *spk,
  const char *string, SayOptions
);

extern int canSetSpeechVolume (volatile SpeechSynthesizer *spk);
extern int setSpeechVolume (volatile SpeechSynthesizer *spk, int setting, int say);

extern int canSetSpeechRate (volatile SpeechSynthesizer *spk);
extern int setSpeechRate (volatile SpeechSynthesizer *spk, int setting, int say);

extern int canSetSpeechPitch (volatile SpeechSynthesizer *spk);
extern int setSpeechPitch (volatile SpeechSynthesizer *spk, int setting, int say);

extern int canSetSpeechPunctuation (volatile SpeechSynthesizer *spk);
extern int setSpeechPunctuation (volatile SpeechSynthesizer *spk, SpeechPunctuation setting, int say);

extern int haveSpeechDriver (const char *code);
extern const char *getDefaultSpeechDriver (void);
extern const SpeechDriver *loadSpeechDriver (const char *code, void **driverObject, const char *driverDirectory);
extern void identifySpeechDriver (const SpeechDriver *driver, int full);
extern void identifySpeechDrivers (int full);
extern const SpeechDriver *speech;
extern const SpeechDriver noSpeech;

#ifdef __cplusplus
}
#endif /* __cplusplus */

#endif /* BRLTTY_INCLUDED_SPK */
