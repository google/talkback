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

#ifndef BRLTTY_INCLUDED_SPK_THREAD
#define BRLTTY_INCLUDED_SPK_THREAD

#include "spk_types.h"

#ifdef __cplusplus
extern "C" {
#endif /* __cplusplus */

#ifdef ENABLE_SPEECH_SUPPORT
extern int constructSpeechDriverThread (
  volatile SpeechSynthesizer *spk,
  char **parameters
);

extern void destroySpeechDriverThread (
  volatile SpeechSynthesizer *spk
);

extern int speechRequest_sayText (
  volatile SpeechDriverThread *sdt,
  const char *text, size_t length,
  size_t count, const unsigned char *attributes,
  SayOptions options
);

extern int speechRequest_muteSpeech (
  volatile SpeechDriverThread *sdt
);

extern int speechRequest_drainSpeech (
  volatile SpeechDriverThread *sdt
);

extern int speechRequest_setVolume (
  volatile SpeechDriverThread *sdt,
  unsigned char setting
);

extern int speechRequest_setRate (
  volatile SpeechDriverThread *sdt,
  unsigned char setting
);

extern int speechRequest_setPitch (
  volatile SpeechDriverThread *sdt,
  unsigned char setting
);

extern int speechRequest_setPunctuation (
  volatile SpeechDriverThread *sdt,
  SpeechPunctuation setting
);

extern int speechMessage_speechFinished (
  volatile SpeechDriverThread *sdt
);

extern int speechMessage_speechLocation (
  volatile SpeechDriverThread *sdt,
  int location
);
#endif /* ENABLE_SPEECH_SUPPORT */

#ifdef __cplusplus
}
#endif /* __cplusplus */

#endif /* BRLTTY_INCLUDED_SPK_THREAD */
