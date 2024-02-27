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

#include "prologue.h"

#include "log.h"
#include "spk_types.h"
#include "spk_base.h"
#include "spk_thread.h"
#include "parse.h"

#ifdef ENABLE_SPEECH_SUPPORT
int
tellSpeechFinished (SpeechSynthesizer *spk) {
  return speechMessage_speechFinished(spk->driver.thread);
}

int
tellSpeechLocation (SpeechSynthesizer *spk, int index) {
  if (!spk->track.isActive) return 1;
  return speechMessage_speechLocation(spk->driver.thread, index);
}

static unsigned int
getIntegerSetting (unsigned char setting, unsigned char internal, unsigned int external) {
  return rescaleInteger(setting, internal, external);
}

unsigned int
getIntegerSpeechVolume (unsigned char setting, unsigned int normal) {
  return getIntegerSetting(setting, SPK_VOLUME_DEFAULT, normal);
}

unsigned int
getIntegerSpeechRate (unsigned char setting, unsigned int normal) {
  return getIntegerSetting(setting, SPK_RATE_DEFAULT, normal);
}

unsigned int
getIntegerSpeechPitch (unsigned char setting, unsigned int normal) {
  return getIntegerSetting(setting, SPK_PITCH_DEFAULT, normal);
}

#ifndef NO_FLOAT
float
getFloatSpeechVolume (unsigned char setting) {
  return (float)setting / (float)SPK_VOLUME_DEFAULT;
}

float
getFloatSpeechRate (unsigned char setting) {
  static const float spkRateTable[] = {
    0.3333,
    0.3720,
    0.4152,
    0.4635,
    0.5173,
    0.5774,
    0.6444,
    0.7192,
    0.8027,
    0.8960,
    1.0000,
    1.1161,
    1.2457,
    1.3904,
    1.5518,
    1.7320,
    1.9332,
    2.1577,
    2.4082,
    2.6879,
    3.0000
  };

  return spkRateTable[setting];
}

float
getFloatSpeechPitch (unsigned char setting) {
  return (float)setting / (float)SPK_PITCH_DEFAULT;
}
#endif /* NO_FLOAT */
#endif /* ENABLE_SPEECH_SUPPORT */
