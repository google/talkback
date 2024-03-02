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

#ifndef BRLTTY_INCLUDED_MSG_QUEUE
#define BRLTTY_INCLUDED_MSG_QUEUE

#ifdef __cplusplus
extern "C" {
#endif /* __cplusplus */

typedef long int MessageType;

extern int sendMessage(int queue, MessageType type, const void *content,
                       size_t length, int flags);
extern ssize_t receiveMessage(int queue, MessageType *type, void *buffer,
                              size_t size, int flags);

typedef struct {
  void *data;
  MessageType type;

  size_t length;
  char content[];
} MessageHandlerParameters;

typedef void MessageHandler(const MessageHandlerParameters *parameters);
extern int startMessageReceiver(const char *name, int queue, MessageType type,
                                size_t size, MessageHandler *handler,
                                void *data);

#ifdef __cplusplus
}
#endif /* __cplusplus */

#endif /* BRLTTY_INCLUDED_MSG_QUEUE */
