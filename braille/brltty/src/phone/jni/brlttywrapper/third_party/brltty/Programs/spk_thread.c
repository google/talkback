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

#include "parameters.h"
#include "log.h"
#include "strfmt.h"
#include "prefs.h"
#include "spk_thread.h"
#include "spk.h"
#include "async_wait.h"
#include "async_event.h"
#include "thread.h"
#include "queue.h"

#ifdef ENABLE_SPEECH_SUPPORT
typedef enum {
  THD_CONSTRUCTING,
  THD_STARTING,
  THD_READY,
  THD_STOPPING,
  THD_FINISHED
} ThreadState;

typedef struct {
  const char *name;
} ThreadStateEntry;

static const ThreadStateEntry threadStateTable[] = {
  [THD_CONSTRUCTING] = {
    .name = "constructing"
  },

  [THD_STARTING] = {
    .name = "starting"
  },

  [THD_READY] = {
    .name = "ready"
  },

  [THD_STOPPING] = {
    .name = "stopping"
  },

  [THD_FINISHED] = {
    .name = "finished"
  },
};

static inline const ThreadStateEntry *
getThreadStateEntry (ThreadState state) {
  if (state >= ARRAY_COUNT(threadStateTable)) return NULL;
  return &threadStateTable[state];
}

typedef enum {
  RSP_PENDING,
  RSP_INTEGER
} SpeechResponseType;

struct SpeechDriverThreadStruct {
  ThreadState threadState;
  Queue *requestQueue;

  SpeechSynthesizer *speechSynthesizer;
  char **driverParameters;

#ifdef GOT_PTHREADS
  pthread_t threadIdentifier;
  AsyncEvent *requestEvent;
  AsyncEvent *messageEvent;
  unsigned isBeingDestroyed:1;
#endif /* GOT_PTHREADS */

  struct {
    SpeechResponseType type;

    union {
      int INTEGER;
    } value;
  } response;
};

typedef enum {
  REQ_SAY_TEXT,
  REQ_MUTE_SPEECH,
  REQ_DRAIN_SPEECH,

  REQ_SET_VOLUME,
  REQ_SET_RATE,
  REQ_SET_PITCH,
  REQ_SET_PUNCTUATION
} SpeechRequestType;

static const char *const speechRequestNames[] = {
  [REQ_SAY_TEXT] = "say text",
  [REQ_MUTE_SPEECH] = "mute speech",
  [REQ_DRAIN_SPEECH] = "drain speech",

  [REQ_SET_VOLUME] = "set volume",
  [REQ_SET_RATE] = "set rate",
  [REQ_SET_PITCH] = "set pitch",
  [REQ_SET_PUNCTUATION] = "set punctuation"
};

typedef struct {
  SpeechRequestType type;

  union {
    struct {
      const unsigned char *text;
      size_t length;
      size_t count;
      const unsigned char *attributes;
      SayOptions options;
    } sayText;

    struct {
      unsigned char setting;
    } setVolume;

    struct {
      unsigned char setting;
    } setRate;

    struct {
      unsigned char setting;
    } setPitch;

    struct {
      SpeechPunctuation setting;
    } setPunctuation;
  } arguments;

  unsigned char data[0];
} SpeechRequest;

typedef struct {
  const void *address;
  size_t size;
  unsigned end:1;
} SpeechDatum;

#define BEGIN_SPEECH_DATA SpeechDatum data[] = {
#define END_SPEECH_DATA {.end=1} };

typedef enum {
  MSG_REQUEST_FINISHED,
  MSG_SPEECH_FINISHED,
  MSG_SPEECH_LOCATION
} SpeechMessageType;

static const char *const speechMessageNames[] = {
  [MSG_REQUEST_FINISHED] = "request finished",
  [MSG_SPEECH_FINISHED] = "speech finished",
  [MSG_SPEECH_LOCATION] = "speech location"
};

typedef struct {
  SpeechMessageType type;

  union {
    struct {
      int result;
    } requestFinished;

    struct {
      int location;
    } speechLocation;
  } arguments;

  unsigned char data[0];
} SpeechMessage;

static const char *
getActionName (unsigned int action, const char *const *names, size_t count) {
  return (action < count)? names[action]: NULL;
}

typedef struct {
  const char *action;
  const char *type;
  const char *name;
  unsigned int value;
} LogSpeechActionData;

static
STR_BEGIN_FORMATTER(formatLogSpeechActionData, const void *data)
  const LogSpeechActionData *lsa = data;

  STR_PRINTF("%s speech %s: ", lsa->action, lsa->type);

  if (lsa->name) {
    STR_PRINTF("%s", lsa->name);
  } else {
    STR_PRINTF("%u", lsa->value);
  }
STR_END_FORMATTER

static void
logSpeechAction (const LogSpeechActionData *lsa) {
  logData(LOG_CATEGORY(SPEECH_EVENTS), formatLogSpeechActionData, lsa);
}

static void
logSpeechRequest (SpeechRequest *req, const char *action) {
  const LogSpeechActionData lsa = {
    .action = action,
    .type = "request",
    .name = req? getActionName(req->type, speechRequestNames, ARRAY_COUNT(speechRequestNames)): "stop",
    .value = req? req->type: 0
  };

  logSpeechAction(&lsa);
}

static void
logSpeechMessage (SpeechMessage *msg, const char *action) {
  const LogSpeechActionData lsa = {
    .action = action,
    .type = "message",
    .name = getActionName(msg->type, speechMessageNames, ARRAY_COUNT(speechMessageNames)),
    .value = msg->type
  };

  logSpeechAction(&lsa);
}

static int
testThreadValidity (SpeechDriverThread *sdt) {
  if (!sdt) return 0;

#ifdef GOT_PTHREADS
  if (sdt->isBeingDestroyed) return 0;
#endif /* GOT_PTHREADS */

  SpeechSynthesizer *spk = sdt->speechSynthesizer;
  if (!spk) return 0;
  if (sdt != spk->driver.thread) return 0;

  if (sdt->threadState != THD_READY) return 0;
  return 1;
}

static void
setThreadState (SpeechDriverThread *sdt, ThreadState state) {
  const ThreadStateEntry *entry = getThreadStateEntry(state);
  const char *name = entry? entry->name: NULL;

  if (!name) name = "?";
  logMessage(LOG_CATEGORY(SPEECH_EVENTS), "driver thread %s", name);
  sdt->threadState = state;
}

static size_t
getSpeechDataSize (const SpeechDatum *data) {
  size_t size = 0;

  if (data) {
    const SpeechDatum *datum = data;

    while (!datum->end) {
      if (datum->address) size += datum->size;
      datum += 1;
    }
  }

  return size;
}

static void
moveSpeechData (unsigned char *target, SpeechDatum *data) {
  if (data) {
    SpeechDatum *datum = data;

    while (!datum->end) {
      if (datum->address) {
        memcpy(target, datum->address, datum->size);
        datum->address = target;
        target += datum->size;
      }

      datum += 1;
    }
  }
}

static inline void
setResponsePending (SpeechDriverThread *sdt) {
  sdt->response.type = RSP_PENDING;
}

static void
setIntegerResponse (SpeechDriverThread *sdt, int value) {
  sdt->response.type = RSP_INTEGER;
  sdt->response.value.INTEGER = value;
}

ASYNC_CONDITION_TESTER(testSpeechResponseReceived) {
  SpeechDriverThread *sdt = data;

  return sdt->response.type != RSP_PENDING;
}

static int
awaitSpeechResponse (SpeechDriverThread *sdt, int timeout) {
  return asyncAwaitCondition(timeout, testSpeechResponseReceived, sdt);
}

static void sendSpeechRequest (SpeechDriverThread *sdt);

static void
handleSpeechMessage (SpeechDriverThread *sdt, SpeechMessage *msg) {
  logSpeechMessage(msg, "handling");

  if (msg) {
    switch (msg->type) {
      case MSG_REQUEST_FINISHED:
        setIntegerResponse(sdt, msg->arguments.requestFinished.result);
        sendSpeechRequest(sdt);
        break;

      case MSG_SPEECH_FINISHED: {
        SpeechSynthesizer *spk = sdt->speechSynthesizer;
        SetSpeechFinishedMethod *setFinished = spk->setFinished;

        if (setFinished) setFinished(spk);
        break;
      }

      case MSG_SPEECH_LOCATION: {
        SpeechSynthesizer *spk = sdt->speechSynthesizer;
        SetSpeechLocationMethod *setLocation = spk->setLocation;

        if (setLocation) setLocation(spk, msg->arguments.speechLocation.location);
        break;
      }

      default:
        logMessage(LOG_CATEGORY(SPEECH_EVENTS), "unimplemented message: %u", msg->type);
        break;
    }

    free(msg);
  }
}

static int
sendSpeechMessage (SpeechDriverThread *sdt, SpeechMessage *msg) {
  logSpeechMessage(msg, "sending");

#ifdef GOT_PTHREADS
  return asyncSignalEvent(sdt->messageEvent, msg);
#else /* GOT_PTHREADS */
  handleSpeechMessage(sdt, msg);
  return 1;
#endif /* GOT_PTHREADS */
}

static SpeechMessage *
newSpeechMessage (SpeechMessageType type, SpeechDatum *data) {
  SpeechMessage *msg;
  size_t size = sizeof(*msg) + getSpeechDataSize(data);

  if ((msg = malloc(size))) {
    memset(msg, 0, sizeof(*msg));
    msg->type = type;
    moveSpeechData(msg->data, data);
    return msg;
  } else {
    logMallocError();
  }

  return NULL;
}

static int
speechMessage_requestFinished (
  SpeechDriverThread *sdt,
  int result
) {
  SpeechMessage *msg;

  if ((msg = newSpeechMessage(MSG_REQUEST_FINISHED, NULL))) {
    msg->arguments.requestFinished.result = result;
    if (sendSpeechMessage(sdt, msg)) return 1;

    free(msg);
  }

  return 0;
}

int
speechMessage_speechFinished (
  SpeechDriverThread *sdt
) {
  SpeechMessage *msg;

  if ((msg = newSpeechMessage(MSG_SPEECH_FINISHED, NULL))) {
    if (sendSpeechMessage(sdt, msg)) return 1;

    free(msg);
  }

  return 0;
}

int
speechMessage_speechLocation (
  SpeechDriverThread *sdt,
  int location
) {
  SpeechMessage *msg;

  if ((msg = newSpeechMessage(MSG_SPEECH_LOCATION, NULL))) {
    msg->arguments.speechLocation.location = location;
    if (sendSpeechMessage(sdt, msg)) return 1;

    free(msg);
  }

  return 0;
}

static int
sendIntegerResponse (SpeechDriverThread *sdt, int result) {
  return speechMessage_requestFinished(sdt, result);
}

static void
handleSpeechRequest (SpeechDriverThread *sdt, SpeechRequest *req) {
  SpeechSynthesizer *spk = sdt->speechSynthesizer;

  logSpeechRequest(req, "handling");

  if (req) {
    switch (req->type) {
      case REQ_SAY_TEXT: {
        SayOptions options = req->arguments.sayText.options;
        int restorePitch = 0;
        int restorePunctuation = 0;

        if (options & SAY_OPT_MUTE_FIRST) speech->mute(spk);

        if (options & SAY_OPT_HIGHER_PITCH) {
          if (spk->setPitch) {
            unsigned char pitch = prefs.speechPitch + 7;

            if (pitch > SPK_PITCH_MAXIMUM) pitch = SPK_PITCH_MAXIMUM;

            if (pitch != prefs.speechPitch) {
              spk->setPitch(spk, pitch);
              restorePitch = 1;
            }
          }
        }

        if (options & SAY_OPT_ALL_PUNCTUATION) {
          if (spk->setPunctuation) {
            unsigned char punctuation = SPK_PUNCTUATION_ALL;

            if (punctuation != prefs.speechPunctuation) {
              spk->setPunctuation(spk, punctuation);
              restorePunctuation = 1;
            }
          }
        }

        speech->say(spk,
          req->arguments.sayText.text, req->arguments.sayText.length,
          req->arguments.sayText.count, req->arguments.sayText.attributes
        );

        if (restorePunctuation) spk->setPunctuation(spk, prefs.speechPunctuation);
        if (restorePitch) spk->setPitch(spk, prefs.speechPitch);

        sendIntegerResponse(sdt, 1);
        break;
      }

      case REQ_MUTE_SPEECH: {
        speech->mute(spk);

        sendIntegerResponse(sdt, 1);
        break;
      }

      case REQ_DRAIN_SPEECH: {
        spk->drain(spk);

        sendIntegerResponse(sdt, 1);
        break;
      }

      case REQ_SET_VOLUME: {
        spk->setVolume(spk, req->arguments.setVolume.setting);

        sendIntegerResponse(sdt, 1);
        break;
      }

      case REQ_SET_RATE: {
        spk->setRate(spk, req->arguments.setRate.setting);

        sendIntegerResponse(sdt, 1);
        break;
      }

      case REQ_SET_PITCH: {
        spk->setPitch(spk, req->arguments.setPitch.setting);

        sendIntegerResponse(sdt, 1);
        break;
      }

      case REQ_SET_PUNCTUATION: {
        spk->setPunctuation(spk, req->arguments.setPunctuation.setting);

        sendIntegerResponse(sdt, 1);
        break;
      }

      default:
        logMessage(LOG_CATEGORY(SPEECH_EVENTS), "unimplemented request: %u", req->type);
        sendIntegerResponse(sdt, 0);
        break;
    }

    free(req);
  } else {
    setThreadState(sdt, THD_STOPPING);
    sendIntegerResponse(sdt, 1);
  }
}

typedef struct {
  SpeechRequestType const type;
} TestSpeechRequestData;

static int
testSpeechRequest (const void *item, void *data) {
  const SpeechRequest *req = item;
  const TestSpeechRequestData *tsr = data;

  return req->type == tsr->type;
}

static Element *
findSpeechRequestElement (SpeechDriverThread *sdt, SpeechRequestType type) {
  TestSpeechRequestData tsr = {
    .type = type
  };

  if (!testThreadValidity(sdt)) return NULL;
  return findElement(sdt->requestQueue, testSpeechRequest, &tsr);
}

static void
removeSpeechRequests (SpeechDriverThread *sdt, SpeechRequestType type) {
  Element *element;

  while ((element = findSpeechRequestElement(sdt, type))) deleteElement(element);
}

static void
muteSpeechRequestQueue (SpeechDriverThread *sdt) {
  removeSpeechRequests(sdt, REQ_SAY_TEXT);
  removeSpeechRequests(sdt, REQ_MUTE_SPEECH);
}

static void
sendSpeechRequest (SpeechDriverThread *sdt) {
  while (getQueueSize(sdt->requestQueue) > 0) {
    SpeechRequest *req = dequeueItem(sdt->requestQueue);

    logSpeechRequest(req, "sending");
    setResponsePending(sdt);

#ifdef GOT_PTHREADS
    if (!asyncSignalEvent(sdt->requestEvent, req)) {
      if (req) free(req);
      setIntegerResponse(sdt, 0);
      continue;
    }
#else /* GOT_PTHREADS */
    handleSpeechRequest(sdt, req);
#endif /* GOT_PTHREADS */

    break;
  }
}

static int
enqueueSpeechRequest (SpeechDriverThread *sdt, SpeechRequest *req) {
  if (testThreadValidity(sdt)) {
    logSpeechRequest(req, "enqueuing");

    if (enqueueItem(sdt->requestQueue, req)) {
      if (sdt->response.type != RSP_PENDING) {
        if (getQueueSize(sdt->requestQueue) == 1) {
          sendSpeechRequest(sdt);
        }
      }

      return 1;
    }
  }

  return 0;
}

static SpeechRequest *
newSpeechRequest (SpeechRequestType type, SpeechDatum *data) {
  SpeechRequest *req;
  size_t size = sizeof(*req) + getSpeechDataSize(data);

  if ((req = malloc(size))) {
    memset(req, 0, sizeof(*req));
    req->type = type;
    moveSpeechData(req->data, data);
    return req;
  } else {
    logMallocError();
  }

  return NULL;
}

int
speechRequest_sayText (
  SpeechDriverThread *sdt,
  const char *text, size_t length,
  size_t count, const unsigned char *attributes,
  SayOptions options
) {
  SpeechRequest *req;

  BEGIN_SPEECH_DATA
    {.address=text, .size=length+1},
    {.address=attributes, .size=count},
  END_SPEECH_DATA

  if ((req = newSpeechRequest(REQ_SAY_TEXT, data))) {
    req->arguments.sayText.text = data[0].address;
    req->arguments.sayText.length = length;
    req->arguments.sayText.count = count;
    req->arguments.sayText.attributes = data[1].address;
    req->arguments.sayText.options = options;

    if (options & SAY_OPT_MUTE_FIRST) muteSpeechRequestQueue(sdt);
    if (enqueueSpeechRequest(sdt, req)) return 1;

    free(req);
  }

  return 0;
}

int
speechRequest_muteSpeech (
  SpeechDriverThread *sdt
) {
  SpeechRequest *req;

  if ((req = newSpeechRequest(REQ_MUTE_SPEECH, NULL))) {
    muteSpeechRequestQueue(sdt);
    if (enqueueSpeechRequest(sdt, req)) return 1;

    free(req);
  }

  return 0;
}

int
speechRequest_drainSpeech (
  SpeechDriverThread *sdt
) {
  SpeechRequest *req;

  if ((req = newSpeechRequest(REQ_DRAIN_SPEECH, NULL))) {
    if (enqueueSpeechRequest(sdt, req)) {
      awaitSpeechResponse(sdt, SPEECH_RESPONSE_WAIT_TIMEOUT);
      return 1;
    }

    free(req);
  }

  return 0;
}

int
speechRequest_setVolume (
  SpeechDriverThread *sdt,
  unsigned char setting
) {
  SpeechRequest *req;

  if ((req = newSpeechRequest(REQ_SET_VOLUME, NULL))) {
    req->arguments.setVolume.setting = setting;
    if (enqueueSpeechRequest(sdt, req)) return 1;

    free(req);
  }

  return 0;
}

int
speechRequest_setRate (
  SpeechDriverThread *sdt,
  unsigned char setting
) {
  SpeechRequest *req;

  if ((req = newSpeechRequest(REQ_SET_RATE, NULL))) {
    req->arguments.setRate.setting = setting;
    if (enqueueSpeechRequest(sdt, req)) return 1;

    free(req);
  }

  return 0;
}

int
speechRequest_setPitch (
  SpeechDriverThread *sdt,
  unsigned char setting
) {
  SpeechRequest *req;

  if ((req = newSpeechRequest(REQ_SET_PITCH, NULL))) {
    req->arguments.setPitch.setting = setting;
    if (enqueueSpeechRequest(sdt, req)) return 1;

    free(req);
  }

  return 0;
}

int
speechRequest_setPunctuation (
  SpeechDriverThread *sdt,
  SpeechPunctuation setting
) {
  SpeechRequest *req;

  if ((req = newSpeechRequest(REQ_SET_PUNCTUATION, NULL))) {
    req->arguments.setPunctuation.setting = setting;
    if (enqueueSpeechRequest(sdt, req)) return 1;

    free(req);
  }

  return 0;
}

static void
setThreadReady (SpeechDriverThread *sdt) {
  setThreadState(sdt, THD_READY);
  sendIntegerResponse(sdt, 1);
}

static int
startSpeechDriver (SpeechDriverThread *sdt) {
  logMessage(LOG_CATEGORY(SPEECH_EVENTS), "starting driver");
  return speech->construct(sdt->speechSynthesizer, sdt->driverParameters);
}

static void
stopSpeechDriver (SpeechDriverThread *sdt) {
  logMessage(LOG_CATEGORY(SPEECH_EVENTS), "stopping driver");
  speech->destruct(sdt->speechSynthesizer);
}

#ifdef GOT_PTHREADS
ASYNC_CONDITION_TESTER(testSpeechDriverThreadStopping) {
  SpeechDriverThread *sdt = data;

  return sdt->threadState == THD_STOPPING;
}

ASYNC_EVENT_CALLBACK(handleSpeechMessageEvent) {
  SpeechDriverThread *sdt = parameters->eventData;
  SpeechMessage *msg = parameters->signalData;

  handleSpeechMessage(sdt, msg);
}

ASYNC_EVENT_CALLBACK(handleSpeechRequestEvent) {
  SpeechDriverThread *sdt = parameters->eventData;
  SpeechRequest *req = parameters->signalData;

  handleSpeechRequest(sdt, req);
}

static void
awaitSpeechDriverThreadTermination (SpeechDriverThread *sdt) {
  void *result;

  pthread_join(sdt->threadIdentifier, &result);
}

THREAD_FUNCTION(runSpeechDriverThread) {
  SpeechDriverThread *sdt = argument;

  setThreadState(sdt, THD_STARTING);

  if ((sdt->requestEvent = asyncNewEvent(handleSpeechRequestEvent, sdt))) {
    if (startSpeechDriver(sdt)) {
      setThreadReady(sdt);
      asyncWaitFor(testSpeechDriverThreadStopping, sdt);
      stopSpeechDriver(sdt);
    } else {
      logMessage(LOG_CATEGORY(SPEECH_EVENTS), "driver construction failure");
    }

    asyncDiscardEvent(sdt->requestEvent);
    sdt->requestEvent = NULL;
  } else {
    logMessage(LOG_CATEGORY(SPEECH_EVENTS), "request event construction failure");
  }

  {
    int ok = sdt->threadState == THD_STOPPING;

    sendIntegerResponse(sdt, ok);
  }

  setThreadState(sdt, THD_FINISHED);
  return NULL;
}
#endif /* GOT_PTHREADS */

static void
deallocateSpeechRequest (void *item, void *data) {
  SpeechRequest *req = item;

  logSpeechRequest(req, "unqueuing");
  free(req);
}

int
constructSpeechDriverThread (
  SpeechSynthesizer *spk,
  char **parameters
) {
  SpeechDriverThread *sdt;

  if ((sdt = malloc(sizeof(*sdt)))) {
    memset(sdt, 0, sizeof(*sdt));
    setThreadState(sdt, THD_CONSTRUCTING);
    setResponsePending(sdt);

    sdt->speechSynthesizer = spk;
    sdt->driverParameters = parameters;

    if ((sdt->requestQueue = newQueue(deallocateSpeechRequest, NULL))) {
      spk->driver.thread = sdt;

#ifdef GOT_PTHREADS
      if ((sdt->messageEvent = asyncNewEvent(handleSpeechMessageEvent, sdt))) {
        pthread_t threadIdentifier;
        int createError = createThread("speech-driver",
                                       &threadIdentifier, NULL,
                                       runSpeechDriverThread, sdt);

        if (!createError) {
          sdt->threadIdentifier = threadIdentifier;

          if (awaitSpeechResponse(sdt, SPEECH_DRIVER_THREAD_START_TIMEOUT)) {
            if (sdt->response.type == RSP_INTEGER) {
              if (sdt->response.value.INTEGER) {
                return 1;
              }
            }

            logMessage(LOG_CATEGORY(SPEECH_EVENTS), "driver thread initialization failure");
            awaitSpeechDriverThreadTermination(sdt);
          } else {
            logMessage(LOG_CATEGORY(SPEECH_EVENTS), "driver thread initialization timeout");
          }
        } else {
          logMessage(LOG_CATEGORY(SPEECH_EVENTS), "driver thread creation failure: %s", strerror(createError));
        }

        asyncDiscardEvent(sdt->messageEvent);
        sdt->messageEvent = NULL;
      } else {
        logMessage(LOG_CATEGORY(SPEECH_EVENTS), "response event construction failure");
      }
#else /* GOT_PTHREADS */
      if (startSpeechDriver(sdt)) {
        setThreadReady(sdt);
        return 1;
      }
#endif /* GOT_PTHREADS */

      spk->driver.thread = NULL;
      deallocateQueue(sdt->requestQueue);
    }

    free(sdt);
  } else {
    logMallocError();
  }

  spk->driver.thread = NULL;
  return 0;
}

void
destroySpeechDriverThread (SpeechSynthesizer *spk) {
  SpeechDriverThread *sdt = spk->driver.thread;

  deleteElements(sdt->requestQueue);

#ifdef GOT_PTHREADS
  if (enqueueSpeechRequest(sdt, NULL)) {
    sdt->isBeingDestroyed = 1;
    awaitSpeechResponse(sdt, SPEECH_DRIVER_THREAD_STOP_TIMEOUT);

    setResponsePending(sdt);
    awaitSpeechResponse(sdt, SPEECH_DRIVER_THREAD_STOP_TIMEOUT);

    awaitSpeechDriverThreadTermination(sdt);
  }

  if (sdt->messageEvent) asyncDiscardEvent(sdt->messageEvent);
#else /* GOT_PTHREADS */
  stopSpeechDriver(sdt);
  setThreadState(sdt, THD_FINISHED);
#endif /* GOT_PTHREADS */

  sdt->speechSynthesizer->driver.thread = NULL;
  deallocateQueue(sdt->requestQueue);
  free(sdt);
}
#endif /* ENABLE_SPEECH_SUPPORT */
