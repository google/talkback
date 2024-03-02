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

#include <string.h>
#include <errno.h>
#include <sys/msg.h>

#include "log.h"
#include "msg_queue.h"
#include "async_event.h"
#include "thread.h"

typedef struct {
  MessageType type;
  char content[];
} Message;

#define MESSAGE(name, length) \
  unsigned char name##_bytes[sizeof(MessageType) + (length)]; \
  Message *name = (void *)name##_bytes;

int
sendMessage (int queue, MessageType type, const void *content, size_t length, int flags) {
  MESSAGE(message, length);

  if (!content) {
    length = 0;
  } else if (length) {
    memcpy(message->content, content, length);
  }

  message->type = type;
  if (msgsnd(queue, message, length, flags) != -1) return 1;

  logSystemError("msgsnd");
  return 0;
}

ssize_t
receiveMessage (int queue, MessageType *type, void *buffer, size_t size, int flags) {
  MESSAGE(message, size);

  if (!buffer) size = 0;
  ssize_t length = msgrcv(queue, message, size, *type, flags);

  if (length != -1) {
    *type = message->type;
    if (length) memcpy(buffer, message->content, length);
  } else if (errno != EIDRM) {
    logSystemError("msgrcv");
  }

  return length;
}

typedef struct {
  AsyncEvent *event;
  pthread_t thread;

  MessageHandler *handler;
  void *data;

  int queue;
  MessageType type;
  size_t size;
} MessageReceiverArgument;

ASYNC_EVENT_CALLBACK(handleReceivedMessage) {
  MessageReceiverArgument *mra = parameters->eventData;
  MessageHandlerParameters *mhp = parameters->signalData;

  if (mhp) {
    mra->handler(mhp);
    free(mhp);
  } else {
    void *result;
    pthread_join(mra->thread, &result);

    asyncDiscardEvent(mra->event);
    free(mra);
  }
}

THREAD_FUNCTION(messageReceiverThread) {
  MessageReceiverArgument *mra = argument;
  char buffer[mra->size];

  while (1) {
    MessageType type = mra->type;
    ssize_t length = receiveMessage(mra->queue, &type, buffer, mra->size, 0);

    if (length != -1) {
      MessageHandlerParameters *mhp;

      if ((mhp = malloc(sizeof(*mhp) + length))) {
        memset(mhp, 0, sizeof(*mhp));

        mhp->data = mra->data;
        mhp->type = type;

        mhp->length = length;
        memcpy(mhp->content, buffer, mhp->length);

        if (asyncSignalEvent(mra->event, mhp)) continue;
        free(mhp);
      } else {
        logMallocError();
      }
    }

    break;
  }

  asyncSignalEvent(mra->event, NULL);
  return NULL;
}

int
startMessageReceiver (const char *name, int queue, MessageType type, size_t size, MessageHandler *handler, void *data) {
  MessageReceiverArgument *mra;

  if ((mra = malloc(sizeof(*mra)))) {
    memset(mra, 0, sizeof(*mra));

    mra->handler = handler;
    mra->data = data;

    mra->queue = queue;
    mra->type = type;
    mra->size = size;

    if ((mra->event = asyncNewEvent(handleReceivedMessage, mra))) {
      int threadCreationError = createThread(name, &mra->thread, NULL, messageReceiverThread, mra);

      if (!threadCreationError) {
        logMessage(LOG_DEBUG, "message receiver started: %s", name);
        return 1;
      }

      asyncDiscardEvent(mra->event);
    }

    free(mra);
  } else {
    logMallocError();
  }

  logMessage(LOG_WARNING, "message receiver not started: %s", name);
  return 0;
}
