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

#ifndef BRLTTY_INCLUDED_SPK_THREAD
#define BRLTTY_INCLUDED_SPK_THREAD

#include "spk_types.h"

#ifdef __cplusplus
extern "C" {
#endif /* __cplusplus */

#ifdef ENABLE_SPEECH_SUPPORT
extern int constructSpeechDriverThread (
  SpeechSynthesizer *spk,
  char **parameters
);

extern void destroySpeechDriverThread (
  SpeechSynthesizer *spk
);

extern int speechRequest_sayText (
  SpeechDriverThread *sdt,
  const char *text, size_t length,
  size_t count, const unsigned char *attributes,
  SayOptions options
);

extern int speechRequest_muteSpeech (
  SpeechDriverThread *sdt
);

extern int speechRequest_drainSpeech (
  SpeechDriverThread *sdt
);

extern int speechRequest_setVolume (
  SpeechDriverThread *sdt,
  unsigned char setting
);

extern int speechRequest_setRate (
  SpeechDriverThread *sdt,
  unsigned char setting
);

extern int speechRequest_setPitch (
  SpeechDriverThread *sdt,
  unsigned char setting
);

extern int speechRequest_setPunctuation (
  SpeechDriverThread *sdt,
  SpeechPunctuation setting
);

extern int speechMessage_speechFinished (
  SpeechDriverThread *sdt
);

extern int speechMessage_speechLocation (
  SpeechDriverThread *sdt,
  int location
);
#endif /* ENABLE_SPEECH_SUPPORT */

#ifdef __cplusplus
}
#endif /* __cplusplus */

#endif /* BRLTTY_INCLUDED_SPK_THREAD */
