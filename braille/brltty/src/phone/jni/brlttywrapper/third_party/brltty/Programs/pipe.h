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

#ifndef BRLTTY_INCLUDED_PIPE
#define BRLTTY_INCLUDED_PIPE

#ifdef __cplusplus
extern "C" {
#endif /* __cplusplus */

typedef struct NamedPipeObjectStruct NamedPipeObject;

typedef struct {
  const unsigned char *buffer;
  size_t length;
  void *data;
} NamedPipeInputCallbackParameters;

#define NAMED_PIPE_INPUT_CALLBACK(name) size_t name (const NamedPipeInputCallbackParameters *parameters)
typedef NAMED_PIPE_INPUT_CALLBACK(NamedPipeInputCallback);

extern NamedPipeObject *newNamedPipeObject (const char *name, NamedPipeInputCallback *callback, void *data);
extern void destroyNamedPipeObject (NamedPipeObject *obj);

#ifdef __cplusplus
}
#endif /* __cplusplus */

#endif /* BRLTTY_INCLUDED_PIPE */
