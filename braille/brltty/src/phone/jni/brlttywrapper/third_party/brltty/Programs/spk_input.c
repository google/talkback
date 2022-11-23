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

#include <string.h>

#include "log.h"
#include "spk_input.h"
#include "spk.h"
#include "pipe.h"
#include "core.h"

#ifdef ENABLE_SPEECH_SUPPORT
struct SpeechInputObjectStruct {
  NamedPipeObject *pipe;
};

static
NAMED_PIPE_INPUT_CALLBACK(handleSpeechInput) {
//SpeechInputObject *obj = parameters->data;

  const unsigned char *buffer = parameters->buffer;
  size_t length = parameters->length;
  char string[length + 1];

  memcpy(string, buffer, length);
  string[length] = 0;
  sayString(&spk, string, 0);

  return length;
}

SpeechInputObject *
newSpeechInputObject (const char *name) {
  SpeechInputObject *obj;

  if ((obj = malloc(sizeof(*obj)))) {
    memset(obj, 0, sizeof(*obj));

    if ((obj->pipe = newNamedPipeObject(name, handleSpeechInput, obj))) {
      return obj;
    }

    free(obj);
  } else {
    logMallocError();
  }

  return NULL;
}

void
destroySpeechInputObject (SpeechInputObject *obj) {
  if (obj->pipe) destroyNamedPipeObject(obj->pipe);
  free(obj);
}
#endif /* ENABLE_SPEECH_SUPPORT */
