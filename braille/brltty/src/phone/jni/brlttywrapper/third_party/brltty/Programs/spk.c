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

#include <stdio.h>
#include <string.h>
#include <errno.h>
#include <fcntl.h>
#include <sys/stat.h>

#include "log.h"
#include "parameters.h"
#include "program.h"
#include "file.h"
#include "parse.h"
#include "prefs.h"
#include "utf8.h"
#include "spk.h"
#include "spk_thread.h"

void
constructSpeechSynthesizer (SpeechSynthesizer *spk) {
  spk->canAutospeak = 1;

  spk->track.isActive = 0;
  spk->track.screenNumber = SPK_SCR_NONE;
  spk->track.firstLine = 0;
  spk->track.speechLocation = SPK_LOC_NONE;

  spk->setVolume = NULL;
  spk->setRate = NULL;
  spk->setPitch = NULL;
  spk->setPunctuation = NULL;
  spk->drain = NULL;

  spk->setFinished = NULL;
  spk->setLocation = NULL;

  spk->driver.thread = NULL;
  spk->driver.data = NULL;
}

void
destructSpeechSynthesizer (SpeechSynthesizer *spk) {
}

int
startSpeechDriverThread (SpeechSynthesizer *spk, char **parameters) {
  return constructSpeechDriverThread(spk, parameters);
}

void
stopSpeechDriverThread (SpeechSynthesizer *spk) {
  destroySpeechDriverThread(spk);
}

int
muteSpeech (SpeechSynthesizer *spk, const char *reason) {
  int result;

  logMessage(LOG_CATEGORY(SPEECH_EVENTS), "mute: %s", reason);
  result = speechRequest_muteSpeech(spk->driver.thread);

  if (spk->setFinished) spk->setFinished(spk);
  return result;
}

int
sayUtf8Characters (
  SpeechSynthesizer *spk,
  const char *text, const unsigned char *attributes,
  size_t length, size_t count,
  SayOptions options
) {
  if (count) {
    logMessage(LOG_CATEGORY(SPEECH_EVENTS), "say: %s", text);
    if (!speechRequest_sayText(spk->driver.thread, text, length, count, attributes, options)) return 0;
  }

  return 1;
}

int
sayWideCharacters (
  SpeechSynthesizer *spk,
  const wchar_t *characters, const unsigned char *attributes,
  size_t count, SayOptions options
) {
  int ok = 0;
  size_t length;
  void *text = getUtf8FromWchars(characters, count, &length);

  if (text) {
    if (sayUtf8Characters(spk, text, attributes, length, count, options)) ok = 1;
    free(text);
  } else {
    logMallocError();
  }

  return ok;
}

int
sayString (
  SpeechSynthesizer *spk,
  const char *string, SayOptions options
) {
  return sayUtf8Characters(spk, string, NULL, strlen(string), countUtf8Characters(string), options);
}

static int
sayStringSetting (
  SpeechSynthesizer *spk,
  const char *name, const char *string
) {
  char statement[0X40];

  snprintf(statement, sizeof(statement), "%s %s", name, string);
  return sayString(spk, statement, SAY_OPT_MUTE_FIRST);
}

static int
sayIntegerSetting (
  SpeechSynthesizer *spk,
  const char *name, int integer
) {
  char string[0X10];

  snprintf(string, sizeof(string), "%d", integer);
  return sayStringSetting(spk, name, string);
}

int
canDrainSpeech (SpeechSynthesizer *spk) {
  return spk->drain != NULL;
}

int
drainSpeech (SpeechSynthesizer *spk) {
  if (!canDrainSpeech(spk)) return 0;
  logMessage(LOG_CATEGORY(SPEECH_EVENTS), "drain speech");
  speechRequest_drainSpeech(spk->driver.thread);
  return 1;
}

int
canSetSpeechVolume (SpeechSynthesizer *spk) {
  return spk->setVolume != NULL;
}

int
toNormalizedSpeechVolume (unsigned char volume) {
  return rescaleInteger(volume, SPK_VOLUME_DEFAULT, 100);
}

int
setSpeechVolume (SpeechSynthesizer *spk, int setting, int say) {
  if (!canSetSpeechVolume(spk)) return 0;
  logMessage(LOG_CATEGORY(SPEECH_EVENTS), "set volume: %d", setting);
  speechRequest_setVolume(spk->driver.thread, setting);

  if (say) {
    sayIntegerSetting(
      spk, gettext("volume"),
      toNormalizedSpeechVolume(setting)
    );
  }

  return 1;
}

int
canSetSpeechRate (SpeechSynthesizer *spk) {
  return spk->setRate != NULL;
}

int
toNormalizedSpeechRate (unsigned char rate) {
  return rate - SPK_RATE_DEFAULT;
}

int
setSpeechRate (SpeechSynthesizer *spk, int setting, int say) {
  if (!canSetSpeechRate(spk)) return 0;
  logMessage(LOG_CATEGORY(SPEECH_EVENTS), "set rate: %d", setting);
  speechRequest_setRate(spk->driver.thread, setting);

  if (say) {
    sayIntegerSetting(
      spk, gettext("rate"),
      toNormalizedSpeechRate(setting)
    );
  }

  return 1;
}

int
canSetSpeechPitch (SpeechSynthesizer *spk) {
  return spk->setPitch != NULL;
}

int
toNormalizedSpeechPitch (unsigned char pitch) {
  return pitch - SPK_PITCH_DEFAULT;
}

int
setSpeechPitch (SpeechSynthesizer *spk, int setting, int say) {
  if (!canSetSpeechPitch(spk)) return 0;
  logMessage(LOG_CATEGORY(SPEECH_EVENTS), "set pitch: %d", setting);
  speechRequest_setPitch(spk->driver.thread, setting);

  if (say) {
    sayIntegerSetting(
      spk, gettext("pitch"),
      toNormalizedSpeechPitch(setting)
    );
  }

  return 1;
}

int
canSetSpeechPunctuation (SpeechSynthesizer *spk) {
  return spk->setPunctuation != NULL;
}

int
setSpeechPunctuation (SpeechSynthesizer *spk, SpeechPunctuation setting, int say) {
  if (!canSetSpeechPunctuation(spk)) return 0;
  logMessage(LOG_CATEGORY(SPEECH_EVENTS), "set punctuation: %d", setting);
  speechRequest_setPunctuation(spk->driver.thread, setting);
  return 1;
}
