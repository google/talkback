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

#include "log.h"
#include "parameters.h"
#include "thread.h"
#include "async_handle.h"
#include "async_alarm.h"
#include "async_event.h"
#include "async_wait.h"
#include "program.h"
#include "tune.h"
#include "notes.h"

static int tuneInitialized = 0;
static AsyncHandle tuneDeviceCloseTimer = NULL;
static int openErrorLevel = LOG_ERR;

static const NoteMethods *noteMethods = NULL;
static NoteDevice *noteDevice = NULL;

static int
flushNoteDevice (void) {
  if (!noteDevice) return 1;
  return noteMethods->flush(noteDevice);
}

static void
closeTuneDevice (void) {
  if (tuneDeviceCloseTimer) {
    asyncCancelRequest(tuneDeviceCloseTimer);
    tuneDeviceCloseTimer = NULL;
  }

  if (noteDevice) {
    noteMethods->destruct(noteDevice);
    noteDevice = NULL;
  }
}

ASYNC_ALARM_CALLBACK(handleTuneDeviceCloseTimeout) {
  if (tuneDeviceCloseTimer) {
    asyncDiscardHandle(tuneDeviceCloseTimer);
    tuneDeviceCloseTimer = NULL;
  }

  closeTuneDevice();
}

static int
openTuneDevice (void) {
  const int timeout = TUNE_DEVICE_CLOSE_DELAY;

  if (noteDevice) {
    asyncResetAlarmIn(tuneDeviceCloseTimer, timeout);
    return 1;
  }

  if (noteMethods) {
    if ((noteDevice = noteMethods->construct(openErrorLevel)) != NULL) {
      asyncNewRelativeAlarm(&tuneDeviceCloseTimer, timeout, handleTuneDeviceCloseTimeout, NULL);
      return 1;
    }
  }

  return 0;
}

static const NoteElement *currentlyPlayingNotes = NULL;
static const ToneElement *currentlyPlayingTones = NULL;

typedef unsigned char TuneSynchronizationMonitor;

typedef enum {
  TUNE_REQ_SET_DEVICE,
  TUNE_REQ_PLAY_NOTES,
  TUNE_REQ_PLAY_TONES,
  TUNE_REQ_WAIT,
  TUNE_REQ_SYNCHRONIZE
} TuneRequestType;

typedef struct {
  TuneRequestType type;

  union {
    struct {
      const NoteMethods *methods;
    } setDevice;

    struct {
      const NoteElement *tune;
    } playNotes;

    struct {
      const ToneElement *tune;
    } playTones;

    struct {
      int time;
    } wait;

    struct {
      TuneSynchronizationMonitor *monitor;
    } synchronize;
  } parameters;
} TuneRequest;

static void
handleTuneRequest_setDevice (const NoteMethods *methods) {
  if (methods != noteMethods) {
    closeTuneDevice();
    noteMethods = methods;
  }
}

static void
handleTuneRequest_playNotes (const NoteElement *tune) {
  while (tune->duration) {
    if (!openTuneDevice()) return;
    if (!noteMethods->note(noteDevice, tune->duration, tune->note)) return;
    tune += 1;
  }

  flushNoteDevice();
}

static void
handleTuneRequest_playTones (const ToneElement *tune) {
  while (tune->duration) {
    if (!openTuneDevice()) return;
    if (!noteMethods->tone(noteDevice, tune->duration, tune->frequency)) return;
    tune += 1;
  }

  flushNoteDevice();
}

static void
handleTuneRequest_wait (int time) {
  asyncWait(time);
}

static void
handleTuneRequest_synchronize (TuneSynchronizationMonitor *monitor) {
  *monitor = 1;
}

static void
handleTuneRequest (TuneRequest *req) {
  if (req) {
    switch (req->type) {
      case TUNE_REQ_SET_DEVICE:
        handleTuneRequest_setDevice(req->parameters.setDevice.methods);
        break;

      case TUNE_REQ_PLAY_NOTES: {
        const NoteElement *tune = req->parameters.playNotes.tune;

        currentlyPlayingNotes = tune;
        handleTuneRequest_playNotes(tune);
        currentlyPlayingNotes = NULL;

        break;
      }

      case TUNE_REQ_PLAY_TONES: {
        const ToneElement *tune = req->parameters.playTones.tune;

        currentlyPlayingTones = tune;
        handleTuneRequest_playTones(tune);
        currentlyPlayingTones = NULL;

        break;
      }

      case TUNE_REQ_WAIT:
        handleTuneRequest_wait(req->parameters.wait.time);
        break;

      case TUNE_REQ_SYNCHRONIZE:
        handleTuneRequest_synchronize(req->parameters.synchronize.monitor);
        break;
    }

    free(req);
  } else {
    closeTuneDevice();
  }
}

#ifdef GOT_PTHREADS
typedef enum {
  TUNE_THREAD_NONE,
  TUNE_THREAD_STARTING,
  TUNE_THREAD_FAILED,

  TUNE_THREAD_RUNNING,
  TUNE_THREAD_STOPPING,
  TUNE_THREAD_STOPPED
} TuneThreadState;

static TuneThreadState tuneThreadState = TUNE_THREAD_NONE;
static pthread_t tuneThreadIdentifier;
static AsyncEvent *tuneRequestEvent = NULL;
static AsyncEvent *tuneMessageEvent = NULL;

static void
setTuneThreadState (TuneThreadState newState) {
  TuneThreadState oldState = tuneThreadState;

  logMessage(LOG_DEBUG, "tune thread state change: %d -> %d", oldState, newState);
  tuneThreadState = newState;
}

ASYNC_CONDITION_TESTER(testTuneThreadStarted) {
  return tuneThreadState != TUNE_THREAD_STARTING;
}

ASYNC_CONDITION_TESTER(testTuneThreadStopping) {
  return tuneThreadState == TUNE_THREAD_STOPPING;
}

ASYNC_CONDITION_TESTER(testTuneThreadStopped) {
  return tuneThreadState == TUNE_THREAD_STOPPED;
}

typedef enum {
  TUNE_MSG_SET_STATE
} TuneMessageType;

typedef struct {
  TuneMessageType type;

  union {
    struct {
      TuneThreadState state;
    } setState;
  } parameters;
} TuneMessage;

static void
handleTuneMessage (TuneMessage *msg) {
  switch (msg->type) {
    case TUNE_MSG_SET_STATE:
      setTuneThreadState(msg->parameters.setState.state);
      break;
  }

  free(msg);
}

ASYNC_EVENT_CALLBACK(handleTuneMessageEvent) {
  TuneMessage *msg = parameters->signalData;

  if (msg) handleTuneMessage(msg);
}

static int
sendTuneMessage (TuneMessage *msg) {
  return asyncSignalEvent(tuneMessageEvent, msg);
}

static TuneMessage *
newTuneMessage (TuneMessageType type) {
  TuneMessage *msg;

  if ((msg = malloc(sizeof(*msg)))) {
    memset(msg, 0, sizeof(*msg));
    msg->type = type;
    return msg;
  } else {
    logMallocError();
  }

  return NULL;
}

static void
sendTuneThreadState (TuneThreadState state) {
  TuneMessage *msg;

  if ((msg = newTuneMessage(TUNE_MSG_SET_STATE))) {
    msg->parameters.setState.state = state;
    if (!sendTuneMessage(msg)) free(msg);
  }
}

static void
finishTuneRequest_stop (void) {
  setTuneThreadState(TUNE_THREAD_STOPPING);
}

static void
finishTuneRequest_synchronize (void) {
  sendTuneMessage(NULL);
}

ASYNC_EVENT_CALLBACK(handleTuneRequestEvent) {
  TuneRequest *req = parameters->signalData;
  void (*finish) (void) = NULL;

  if (req) {
    switch (req->type) {
      case TUNE_REQ_SYNCHRONIZE:
        finish = finishTuneRequest_synchronize;
        break;

      default:
        break;
    }
  } else {
    finish = finishTuneRequest_stop;
  }

  handleTuneRequest(req);
  if (finish) finish();
}

THREAD_FUNCTION(runTuneThread) {
  if ((tuneRequestEvent = asyncNewEvent(handleTuneRequestEvent, NULL))) {
    sendTuneThreadState(TUNE_THREAD_RUNNING);
    asyncWaitFor(testTuneThreadStopping, NULL);

    asyncDiscardEvent(tuneRequestEvent);
    tuneRequestEvent = NULL;
  }

  sendTuneThreadState(TUNE_THREAD_STOPPED);
  return NULL;
}

static int
startTuneThread (void) {
  if (tuneThreadState == TUNE_THREAD_NONE) {
    setTuneThreadState(TUNE_THREAD_STARTING);

    if ((tuneMessageEvent = asyncNewEvent(handleTuneMessageEvent, NULL))) {
      int creationError = createThread("tune-thread", &tuneThreadIdentifier,
                                       NULL, runTuneThread, NULL);

      if (!creationError) {
        asyncWaitFor(testTuneThreadStarted, NULL);
        if (tuneThreadState == TUNE_THREAD_RUNNING) return 1;
      } else {
        logActionError(creationError, "tune thread creation");
        setTuneThreadState(TUNE_THREAD_FAILED);
      }

      asyncDiscardEvent(tuneMessageEvent);
      tuneMessageEvent = NULL;
    }
  }

  return tuneThreadState == TUNE_THREAD_RUNNING;
}
#endif /* GOT_PTHREADS */

static int
sendTuneRequest (TuneRequest *req) {
#ifdef GOT_PTHREADS
  if (startTuneThread()) {
    return asyncSignalEvent(tuneRequestEvent, req);
  }
#endif /* GOT_PTHREADS */

  handleTuneRequest(req);
  return 1;
}

static void
exitTunes (void *data) {
  sendTuneRequest(NULL);

#ifdef GOT_PTHREADS
  if (tuneThreadState >= TUNE_THREAD_RUNNING) {
    asyncWaitFor(testTuneThreadStopped, NULL);
  }

  tuneThreadState = TUNE_THREAD_NONE;
#endif /* GOT_PTHREADS */

  tuneInitialized = 0;
}
 
static TuneRequest *
newTuneRequest (TuneRequestType type) {
  TuneRequest *req;

  if (!tuneInitialized) {
    tuneInitialized = 1;
    onProgramExit("tunes", exitTunes, NULL);
  }

  if ((req = malloc(sizeof(*req)))) {
    memset(req, 0, sizeof(*req));
    req->type = type;
    return req;
  } else {
    logMallocError();
  }

  return NULL;
}

int
tuneSetDevice (TuneDevice device) {
  const NoteMethods *methods;

  switch (device) {
    default:
      return 0;

#ifdef HAVE_BEEP_SUPPORT
    case tdBeeper:
      methods = &beepNoteMethods;
      break;
#endif /* HAVE_BEEP_SUPPORT */

#ifdef HAVE_PCM_SUPPORT
    case tdPcm:
      methods = &pcmNoteMethods;
      break;
#endif /* HAVE_PCM_SUPPORT */

#ifdef HAVE_MIDI_SUPPORT
    case tdMidi:
      methods = &midiNoteMethods;
      break;
#endif /* HAVE_MIDI_SUPPORT */

#ifdef HAVE_FM_SUPPORT
    case tdFm:
      methods = &fmNoteMethods;
      break;
#endif /* HAVE_FM_SUPPORT */
  }

  {
    TuneRequest *req;

    if ((req = newTuneRequest(TUNE_REQ_SET_DEVICE))) {
      req->parameters.setDevice.methods = methods;
      if (!sendTuneRequest(req)) free(req);
    }
  }

  return 1;
}

void
tunePlayNotes (const NoteElement *tune) {
  if (tune != currentlyPlayingNotes) {
    TuneRequest *req;

    if ((req = newTuneRequest(TUNE_REQ_PLAY_NOTES))) {
      req->parameters.playNotes.tune = tune;
      if (!sendTuneRequest(req)) free(req);
    }
  }
}

void
tunePlayTones (const ToneElement *tune) {
  if (tune != currentlyPlayingTones) {
    TuneRequest *req;

    if ((req = newTuneRequest(TUNE_REQ_PLAY_TONES))) {
      req->parameters.playTones.tune = tune;
      if (!sendTuneRequest(req)) free(req);
    }
  }
}

void
tuneWait (int time) {
  TuneRequest *req;

  if ((req = newTuneRequest(TUNE_REQ_WAIT))) {
    req->parameters.wait.time = time;
    if (!sendTuneRequest(req)) free(req);
  }
}

ASYNC_CONDITION_TESTER(testTuneSynchronizationMonitor) {
  TuneSynchronizationMonitor *monitor = data;

  return !!*monitor;
}

void
tuneSynchronize (void) {
  TuneRequest *req;

  if ((req = newTuneRequest(TUNE_REQ_SYNCHRONIZE))) {
    TuneSynchronizationMonitor monitor = 0;
    req->parameters.synchronize.monitor = &monitor;

    if (sendTuneRequest(req)) {
      asyncWaitFor(testTuneSynchronizationMonitor, &monitor);
    } else {
      free(req);
    }
  }
}

void
suppressTuneDeviceOpenErrors (void) {
  openErrorLevel = LOG_DEBUG;
}
