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

#ifndef BRLTTY_INCLUDED_SPK_TYPES
#define BRLTTY_INCLUDED_SPK_TYPES

#include "driver.h"

#ifdef __cplusplus
extern "C" {
#endif /* __cplusplus */

typedef enum {
  SAY_OPT_MUTE_FIRST      = 0X01,
  SAY_OPT_HIGHER_PITCH    = 0X02,
  SAY_OPT_ALL_PUNCTUATION = 0X04,
} SayOptions;

#define SPK_VOLUME_DEFAULT 10
#define SPK_VOLUME_MAXIMUM (SPK_VOLUME_DEFAULT * 2)

#define SPK_RATE_DEFAULT 10
#define SPK_RATE_MAXIMUM (SPK_RATE_DEFAULT * 2)

#define SPK_PITCH_DEFAULT 10
#define SPK_PITCH_MAXIMUM (SPK_PITCH_DEFAULT * 2)

typedef enum {
  SPK_PUNCTUATION_NONE,
  SPK_PUNCTUATION_SOME,
  SPK_PUNCTUATION_ALL
} SpeechPunctuation;

typedef struct SpeechSynthesizerStruct SpeechSynthesizer;
typedef struct SpeechDriverThreadStruct SpeechDriverThread;
typedef struct SpeechDataStruct SpeechData;

#define SPK_SCR_NONE -1
#define SPK_LOC_NONE -1

typedef void SetSpeechVolumeMethod (SpeechSynthesizer *spk, unsigned char setting);
typedef void SetSpeechRateMethod (SpeechSynthesizer *spk, unsigned char setting);
typedef void SetSpeechPitchMethod (SpeechSynthesizer *spk, unsigned char setting);
typedef void SetSpeechPunctuationMethod (SpeechSynthesizer *spk, SpeechPunctuation setting);
typedef void DrainSpeechMethod (SpeechSynthesizer *spk);

typedef void SetSpeechFinishedMethod (SpeechSynthesizer *spk);
typedef void SetSpeechLocationMethod (SpeechSynthesizer *spk, int location);

struct SpeechSynthesizerStruct {
  unsigned char sayBanner : 1;
  unsigned char canAutospeak : 1;

  struct {
    int screenNumber;
    int firstLine;
    int speechLocation;
    unsigned char isActive : 1;
  } track;

  SetSpeechVolumeMethod *setVolume;
  SetSpeechRateMethod *setRate;
  SetSpeechPitchMethod *setPitch;
  SetSpeechPunctuationMethod *setPunctuation;
  DrainSpeechMethod *drain;

  SetSpeechFinishedMethod *setFinished;
  SetSpeechLocationMethod *setLocation;

  struct {
    SpeechDriverThread *thread;
    SpeechData *data;
  } driver;
};

typedef struct {
  DRIVER_DEFINITION_DECLARATION;

  const char *const *parameters;

  int (*construct) (SpeechSynthesizer *spk, char **parameters);
  void (*destruct) (SpeechSynthesizer *spk);

  void (*say) (SpeechSynthesizer *spk, const unsigned char *text, size_t length, size_t count, const unsigned char *attributes);
  void (*mute) (SpeechSynthesizer *spk);
} SpeechDriver;

#ifdef __cplusplus
}
#endif /* __cplusplus */

#endif /* BRLTTY_INCLUDED_SPK_TYPES */
