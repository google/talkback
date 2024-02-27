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
#include "async_event.h"
#include "async_signal.h"
#include "async_internal.h"
#include "get_thread.h"

#ifdef ASYNC_CAN_HANDLE_SIGNALS
#if defined(HAVE_SYS_SIGNALFD_H)
#include <sys/signalfd.h>
#include "async_io.h"

#else /* paradigm-specific signal monitoring definitions */
#endif /* paradigm-specific signal monitoring definitions */

struct AsyncSignalDataStruct {
#ifdef ASYNC_CAN_MONITOR_SIGNALS
  Queue *signalQueue;
#endif /* ASYNC_CAN_MONITOR_SIGNALS */

#ifdef ASYNC_CAN_BLOCK_SIGNALS
  sigset_t obtainableSignals;
#endif /* ASYNC_CAN_BLOCK_SIGNALS */

#ifdef ASYNC_CAN_OBTAIN_SIGNALS
  sigset_t claimedSignals;
  sigset_t obtainedSignals;

  int firstObtainableSignal;
  int lastObtainableSignal;
#endif /* ASYNC_CAN_OBTAIN_SIGNALS */
};

void
asyncDeallocateSignalData (AsyncSignalData *sd) {
  if (sd) {
#ifdef ASYNC_CAN_MONITOR_SIGNALS
    if (sd->signalQueue) deallocateQueue(sd->signalQueue);
#endif /* ASYNC_CAN_MONITOR_SIGNALS */

    free(sd);
  }
}

#if defined(ASYNC_CAN_BLOCK_SIGNALS) || defined(ASYNC_CAN_MONITOR_SIGNALS) || defined(ASYNC_CAN_OBTAIN_SIGNALS)
static AsyncSignalData *
getSignalData (void) {
  AsyncThreadSpecificData *tsd = asyncGetThreadSpecificData();
  if (!tsd) return NULL;

  if (!tsd->signalData) {
    AsyncSignalData *sd;

    if (!(sd = malloc(sizeof(*sd)))) {
      logMallocError();
      return NULL;
    }

    memset(sd, 0, sizeof(*sd));

#ifdef ASYNC_CAN_MONITOR_SIGNALS
    sd->signalQueue = NULL;
#endif /* ASYNC_CAN_MONITOR_SIGNALS */

#ifdef ASYNC_CAN_BLOCK_SIGNALS
    sigemptyset(&sd->obtainableSignals);
#endif /* ASYNC_CAN_BLOCK_SIGNALS */

#ifdef ASYNC_CAN_OBTAIN_SIGNALS
    sigemptyset(&sd->claimedSignals);
    sigemptyset(&sd->obtainedSignals);

    sd->firstObtainableSignal = SIGRTMIN;
    sd->lastObtainableSignal = SIGRTMAX;

#ifdef ASYNC_CAN_BLOCK_SIGNALS
    {
      int signalNumber;

      for (signalNumber=sd->firstObtainableSignal; signalNumber<=sd->lastObtainableSignal; signalNumber+=1) {
        sigaddset(&sd->obtainableSignals, signalNumber);
      }
    }
#endif /* ASYNC_CAN_BLOCK_SIGNALS */
#endif /* ASYNC_CAN_OBTAIN_SIGNALS */

    tsd->signalData = sd;
  }

  return tsd->signalData;
}
#endif /* need signal data */

int
asyncHandleSignal (int signalNumber, AsyncSignalHandler *newHandler, AsyncSignalHandler **oldHandler) {
#if defined(HAVE_SIGACTION)
  struct sigaction newAction;
  struct sigaction oldAction;

  memset(&newAction, 0, sizeof(newAction));
  sigemptyset(&newAction.sa_mask);
  newAction.sa_handler = newHandler;

  if (sigaction(signalNumber, &newAction, &oldAction) != -1) {
    if (oldHandler) *oldHandler = oldAction.sa_handler;
    return 1;
  }

  logSystemError("sigaction");
#else /* set signal handler */
  AsyncSignalHandler *result = signal(signalNumber, newHandler);

  if (result != SIG_ERR) {
    if (oldHandler) *oldHandler = result;
    return 1;
  }

  logSystemError("signal");
#endif /* set signal handler */

  return 0;
}

int
asyncIgnoreSignal (int signalNumber, AsyncSignalHandler **oldHandler) {
  return asyncHandleSignal(signalNumber, SIG_IGN, oldHandler);
}

int
asyncRevertSignal (int signalNumber, AsyncSignalHandler **oldHandler) {
  return asyncHandleSignal(signalNumber, SIG_DFL, oldHandler);
}

ASYNC_SIGNAL_HANDLER(asyncEmptySignalHandler) {
}

#ifdef ASYNC_CAN_BLOCK_SIGNALS
static int
setSignalMask (int how, const sigset_t *newMask, sigset_t *oldMask) {
#ifdef GOT_PTHREADS
  int error = pthread_sigmask(how, newMask, oldMask);

  if (!error) return 1;
  logActionError(error, "pthread_setmask");
#else /* GOT_PTHREADS */
  if (sigprocmask(how, newMask, oldMask) != -1) return 1;
  logSystemError("sigprocmask");
#endif /* GOT_PTHREADS */

  return 0;
}

static int
makeSignalMask (sigset_t *signalMask, int signalNumber) {
  if (sigemptyset(signalMask) != -1) {
    if (sigaddset(signalMask, signalNumber) != -1) {
      return 1;
    } else {
      logSystemError("sigaddset");
    }
  } else {
    logSystemError("sigemptyset");
  }

  return 0;
}

int
asyncSetSignalBlocked (int signalNumber, int state) {
  sigset_t mask;

  if (makeSignalMask(&mask, signalNumber)) {
    if (setSignalMask((state? SIG_BLOCK: SIG_UNBLOCK), &mask, NULL)) {
      return 1;
    }
  }

  return 0;
}

static int
getSignalMask (sigset_t *mask) {
  return setSignalMask(SIG_SETMASK, NULL, mask);
}

int
asyncIsSignalBlocked (int signalNumber) {
  sigset_t signalMask;

  if (getSignalMask(&signalMask)) {
    int result = sigismember(&signalMask, signalNumber);

    if (result != -1) return result;
    logSystemError("sigismember");
  }

  return 0;
}

int
asyncWithSignalsBlocked (
  const sigset_t *mask,
  AsyncWithSignalsBlockedFunction *function,
  void *data
) {
  sigset_t oldMask;

  if (setSignalMask(SIG_BLOCK, mask, &oldMask)) {
    function(data);
    setSignalMask(SIG_SETMASK, &oldMask, NULL);
    return 1;
  }

  return 0;
}

int
asyncWithSignalBlocked (
  int number,
  AsyncWithSignalsBlockedFunction *function,
  void *data
) {
  sigset_t mask;

  if (makeSignalMask(&mask, number)) {
    if (asyncWithSignalsBlocked(&mask, function, data)) {
      return 1;
    }
  }

  return 0;
}

int
asyncWithAllSignalsBlocked (
  AsyncWithSignalsBlockedFunction *function,
  void *data
) {
  sigset_t mask;

  if (sigfillset(&mask) != -1) {
    if (asyncWithSignalsBlocked(&mask, function, data)) {
      return 1;
    }
  } else {
    logSystemError("sigfillset");
  }

  return 0;
}

int
asyncWithObtainableSignalsBlocked (
  AsyncWithSignalsBlockedFunction *function,
  void *data
) {
  AsyncSignalData *sd = getSignalData();

  if (sd) {
    if (asyncWithSignalsBlocked(&sd->obtainableSignals, function, data)) {
      return 1;
    }
  }

  return 0;
}

int
asyncBlockObtainableSignals (void) {
  AsyncSignalData *sd = getSignalData();

  if (sd) {
    if (setSignalMask(SIG_BLOCK, &sd->obtainableSignals, NULL)) {
      return 1;
    }
  }

  return 0;
}
#endif /* ASYNC_CAN_BLOCK_SIGNALS */

#ifdef ASYNC_CAN_MONITOR_SIGNALS
typedef struct {
  int number;
  Queue *monitors;
  unsigned wasBlocked:1;

#if defined(HAVE_SYS_SIGNALFD_H)
  struct {
    int fileDescriptor;
    AsyncHandle asyncMonitor;
  } signalfd;

#else /* paradigm-specific signal monitoring fields */
  struct {
    AsyncEvent *event;
    AsyncSignalHandler *old;
  } handler;
#endif /* paradigm-specific signal monitoring fields */
} SignalEntry;

typedef struct {
  SignalEntry *signal;
  AsyncSignalCallback *callback;
  void *data;
  unsigned active:1;
  unsigned delete:1;
} MonitorEntry;

#if defined(HAVE_SYS_SIGNALFD_H)
static void
logSignalfdAction (const SignalEntry *sig, const char *action) {
  logMessage(LOG_CATEGORY(ASYNC_EVENTS), "%s signalfd monitor: sig=%d fd=%d",
             action, sig->number, sig->signalfd.fileDescriptor);
}

static void
initializeSignalfdFileDescriptor (SignalEntry *sig) {
  sig->signalfd.fileDescriptor = -1;
}

static void
initializeSignalfdAsyncMonitor (SignalEntry *sig) {
  sig->signalfd.asyncMonitor = NULL;
}

static void
initializeSignalMonitoring (SignalEntry *sig) {
  initializeSignalfdFileDescriptor(sig);
  initializeSignalfdAsyncMonitor(sig);
}

static void
closeSignalfdFileDescriptor (SignalEntry *sig) {
  struct signalfd_siginfo buffer;

  while (read(sig->signalfd.fileDescriptor, &buffer, sizeof(buffer)) != -1);
  close(sig->signalfd.fileDescriptor);
  initializeSignalfdFileDescriptor(sig);
}

static void
cancelSignalfdAsyncMonitor (SignalEntry *sig) {
  asyncCancelRequest(sig->signalfd.asyncMonitor);
  initializeSignalfdAsyncMonitor(sig);
}

static void
deactivateSignalMonitoring (SignalEntry *sig) {
  logSignalfdAction(sig, "destroying");
  cancelSignalfdAsyncMonitor(sig);
  closeSignalfdFileDescriptor(sig);
}

#else /* paradigm-specific signal monitoring functions */
static void
initializeHandlerEvent (SignalEntry *sig) {
  sig->handler.event = NULL;
}

static void
initializeOldHandler (SignalEntry *sig) {
  sig->handler.old = NULL;
}

static void
initializeSignalMonitoring (SignalEntry *sig) {
  initializeHandlerEvent(sig);
  initializeOldHandler(sig);
}

static void
discardHandlerEvent (SignalEntry *sig) {
  asyncDiscardEvent(sig->handler.event);
  initializeHandlerEvent(sig);
}

static void
restoreOldHandler (SignalEntry *sig) {
  asyncHandleSignal(sig->number, sig->handler.old, NULL);
  initializeOldHandler(sig);
}

static void
deactivateSignalMonitoring (SignalEntry *sig) {
  restoreOldHandler(sig);
  discardHandlerEvent(sig);
}
#endif /* paradigm-specific signal monitoring functions */

static void
deallocateMonitorEntry (void *item, void *data) {
  MonitorEntry *mon = item;

  free(mon);
}

static void
deallocateSignalEntry (void *item, void *data) {
  SignalEntry *sig = item;

  deallocateQueue(sig->monitors);
  free(sig);
}

static Queue *
getSignalQueue (int create) {
  AsyncSignalData *sd = getSignalData();
  if (!sd) return NULL;

  if (!sd->signalQueue && create) {
    sd->signalQueue = newQueue(deallocateSignalEntry, NULL);
  }

  return sd->signalQueue;
}

typedef struct {
  SignalEntry *const signalEntry;
} DeleteSignalEntryParameters;

ASYNC_WITH_SIGNALS_BLOCKED_FUNCTION(asyncDeleteSignalEntry) {
  DeleteSignalEntryParameters *parameters = data;
  Queue *signals = getSignalQueue(0);
  Element *signalElement = findElementWithItem(signals, parameters->signalEntry);

  deleteElement(signalElement);
}

static void
deleteMonitor (Element *monitorElement) {
  MonitorEntry *mon = getElementItem(monitorElement);
  SignalEntry *sig = mon->signal;

  logSymbol(LOG_CATEGORY(ASYNC_EVENTS), mon->callback, "signal monitor removed: %d", sig->number);
  deleteElement(monitorElement);

  if (getQueueSize(sig->monitors) == 0) {
    logMessage(LOG_CATEGORY(ASYNC_EVENTS), "deactivating signal monitoring: %d", sig->number);
    asyncSetSignalBlocked(sig->number, sig->wasBlocked);
    deactivateSignalMonitoring(sig);

    {
      DeleteSignalEntryParameters parameters = {
        .signalEntry = sig
      };

      asyncWithAllSignalsBlocked(asyncDeleteSignalEntry, &parameters);
    }
  }
}

static void
cancelMonitor (Element *monitorElement) {
  MonitorEntry *mon = getElementItem(monitorElement);

  if (mon->active) {
    mon->delete = 1;
  } else {
    deleteMonitor(monitorElement);
  }
}

static void
handlePendingSignal (const SignalEntry *sig) {
  Element *monitorElement = getStackHead(sig->monitors);

  if (monitorElement) {
    MonitorEntry *mon = getElementItem(monitorElement);
    AsyncSignalCallback *callback = mon->callback;

    const AsyncSignalCallbackParameters parameters = {
      .signal = sig->number,
      .data = mon->data
    };

    logSymbol(LOG_CATEGORY(ASYNC_EVENTS), callback, "signal %d starting", sig->number);
    mon->active = 1;
    if (!callback(&parameters)) mon->delete = 1;
    mon->active = 0;
    logSymbol(LOG_CATEGORY(ASYNC_EVENTS), callback, "signal %d finished", sig->number);
    if (mon->delete) deleteMonitor(monitorElement);
  }
}

typedef struct {
  Queue *const signalQueue;
  SignalEntry *const signalEntry;

  Element *signalElement;
} AddSignalEntryParameters;

ASYNC_WITH_SIGNALS_BLOCKED_FUNCTION(asyncAddSignalEntry) {
  AddSignalEntryParameters *parameters = data;

  parameters->signalElement = enqueueItem(parameters->signalQueue, parameters->signalEntry);
}

typedef struct {
  int signalNumber;
} TestMonitoredSignalKey;

static int
testMonitoredSignal (const void *item, void *data) {
  const SignalEntry *sig = item;
  const TestMonitoredSignalKey *key = data;

  return sig->number == key->signalNumber;
}

static Element *
getSignalElement (int signalNumber, int create) {
  Queue *signals = getSignalQueue(create);

  if (signals) {
    {
      TestMonitoredSignalKey key = {
        .signalNumber = signalNumber
      };

      {
        Element *element = findElement(signals, testMonitoredSignal, &key);

        if (element) return element;
      }
    }

    if (create) {
      SignalEntry *sig;

      if ((sig = malloc(sizeof(*sig)))) {
        memset(sig, 0, sizeof(*sig));
        sig->number = signalNumber;
        initializeSignalMonitoring(sig);

        if ((sig->monitors = newQueue(deallocateMonitorEntry, NULL))) {
          {
            static AsyncQueueMethods methods = {
              .cancelRequest = cancelMonitor
            };

            setQueueData(sig->monitors, &methods);
          }

          {
            AddSignalEntryParameters parameters = {
              .signalQueue = signals,
              .signalEntry = sig,

              .signalElement = NULL
            };

            asyncWithAllSignalsBlocked(asyncAddSignalEntry, &parameters);
            if (parameters.signalElement) return parameters.signalElement;
          }

          deallocateQueue(sig->monitors);
        }

        free(sig);
      } else {
        logMallocError();
      }
    }
  }

  return NULL;
}

#if defined(HAVE_SYS_SIGNALFD_H)
ASYNC_INPUT_CALLBACK(asyncHandleSignalfdInput) {
  static const char label[] = "signalfd";
  const SignalEntry *sig = parameters->data;

  if (parameters->error) {
    logMessage(LOG_WARNING, "%s read error: fd=%d sig=%d: %s",
               label, sig->signalfd.fileDescriptor, sig->number, strerror(parameters->error));
  } else if (parameters->end) {
    logMessage(LOG_WARNING, "%s end-of-file: fd=%d sig=%d",
               label, sig->signalfd.fileDescriptor, sig->number);
  } else {
    const struct signalfd_siginfo *info = parameters->buffer;

    handlePendingSignal(sig);
    return sizeof(*info);
  }

  return 0;
}

static int
activateSignalMonitoring (SignalEntry *sig) {
  sigset_t mask;

  if (makeSignalMask(&mask, sig->number)) {
    int flags = 0;

#ifdef SFD_NONBLOCK
    flags |= SFD_NONBLOCK;
#endif /* SFD_NONBLOCK */

#ifdef SFD_CLOEXEC
    flags |= SFD_CLOEXEC;
#endif /* SFD_CLOEXEC */

    if ((sig->signalfd.fileDescriptor = signalfd(-1, &mask, flags)) != -1) {
      if (asyncReadFile(&sig->signalfd.asyncMonitor, sig->signalfd.fileDescriptor,
                        sizeof(struct signalfd_siginfo),
                        asyncHandleSignalfdInput, sig)) {
        if (sig->wasBlocked || asyncSetSignalBlocked(sig->number, 1)) {
          logSignalfdAction(sig, "created");
          return 1;
        }

        cancelSignalfdAsyncMonitor(sig);
      }

      closeSignalfdFileDescriptor(sig);
    } else {
      logSystemError("signalfd");
    }
  }

  return 0;
}

#else /* paradigm-specific signal monitoring handlers */
ASYNC_EVENT_CALLBACK(asyncHandlePendingSignal) {
  SignalEntry *sig = parameters->eventData;

  handlePendingSignal(sig);
}

ASYNC_SIGNAL_HANDLER(asyncHandleMonitoredSignal) {
  Element *signalElement = getSignalElement(signalNumber, 0);

  if (signalElement) {
    SignalEntry *sig = getElementItem(signalElement);

    asyncSignalEvent(sig->handler.event, NULL);
  }
}

static int
activateSignalMonitoring (SignalEntry *sig) {
  if ((sig->handler.event = asyncNewEvent(asyncHandlePendingSignal, sig))) {
    if (asyncHandleSignal(sig->number, asyncHandleMonitoredSignal, &sig->handler.old)) {
      if (!sig->wasBlocked || asyncSetSignalBlocked(sig->number, 0)) {
        return 1;
      }

      restoreOldHandler(sig);
    }

    discardHandlerEvent(sig);
  }

  return 0;
}
#endif /* paradigm-specific signal monitoring handlers */

typedef struct {
  int signal;
  AsyncSignalCallback *callback;
  void *data;
} MonitorElementParameters;

static Element *
newMonitorElement (const void *parameters) {
  const MonitorElementParameters *mep = parameters;
  Element *signalElement = getSignalElement(mep->signal, 1);

  if (signalElement) {
    SignalEntry *sig = getElementItem(signalElement);
    int newSignal = getQueueSize(sig->monitors) == 0;
    MonitorEntry *mon;

    if ((mon = malloc(sizeof(*mon)))) {
      memset(mon, 0, sizeof(*mon));

      mon->signal = sig;
      mon->callback = mep->callback;
      mon->data = mep->data;

      mon->active = 0;
      mon->delete = 0;

      {
        Element *monitorElement = enqueueItem(sig->monitors, mon);

        if (monitorElement) {
          int added = !newSignal;

          if (!added) {
            logMessage(LOG_CATEGORY(ASYNC_EVENTS), "activating signal monitoring: %d", sig->number);
            sig->wasBlocked = asyncIsSignalBlocked(sig->number);
            if (activateSignalMonitoring(sig)) added = 1;
          }

          if (added) {
            logSymbol(LOG_CATEGORY(ASYNC_EVENTS), mon->callback, "signal monitor added: %d", sig->number);
            return monitorElement;
          }

          deleteElement(monitorElement);
        }
      }

      free(mon);
    } else {
      logMallocError();
    }

    if (newSignal) deleteElement(signalElement);
  }

  return NULL;
}

int
asyncMonitorSignal (
  AsyncHandle *handle, int signal,
  AsyncSignalCallback *callback, void *data
) {
  const MonitorElementParameters mep = {
    .signal = signal,
    .callback = callback,
    .data = data
  };

  return asyncMakeHandle(handle, newMonitorElement, &mep);
}
#endif /* ASYNC_CAN_MONITOR_SIGNALS */

#ifdef ASYNC_CAN_OBTAIN_SIGNALS
int
asyncClaimSignalNumber (int signal) {
  AsyncSignalData *sd = getSignalData();

  if (sd) {
    const char *reason = "signal number not claimable";

    if (sigismember(&sd->obtainableSignals, signal)) {
      if (sigismember(&sd->claimedSignals, signal)) {
        reason = "signal number already claimed";
      } else if (sigismember(&sd->obtainedSignals, signal)) {
        reason = "signal number in use";
      } else {
        sigaddset(&sd->claimedSignals, signal);
        return 1;
      }
    }

    logMessage(LOG_ERR, "%s: %d", reason, signal);
  }

  return 0;
}

int
asyncReleaseSignalNumber (int signal) {
  AsyncSignalData *sd = getSignalData();

  if (sd) {
    if (sigismember(&sd->claimedSignals, signal)) {
      sigdelset(&sd->claimedSignals, signal);
      return 1;
    }
  }

  logMessage(LOG_ERR, "signal number not claimed: %d", signal);
  return 0;
}

int
asyncObtainSignalNumber (void) {
  AsyncSignalData *sd = getSignalData();

  if (sd) {
    int signal;

    for (signal=sd->firstObtainableSignal; signal<=sd->lastObtainableSignal; signal+=1) {
      if (sigismember(&sd->obtainableSignals, signal)) {
        if (!sigismember(&sd->claimedSignals, signal)) {
          if (!sigismember(&sd->obtainedSignals, signal)) {
            sigaddset(&sd->obtainedSignals, signal);
            return signal;
          }
        }
      }
    }
  }

  logMessage(LOG_ERR, "no obtainable signal number");
  return 0;
}

int
asyncRelinquishSignalNumber (int signal) {
  AsyncSignalData *sd = getSignalData();

  if (sd) {
    if (sigismember(&sd->obtainedSignals, signal)) {
      sigdelset(&sd->obtainedSignals, signal);
      return 1;
    }
  }

  logMessage(LOG_ERR, "signal number not obtained: %d", signal);
  return 0;
}
#endif /* ASYNC_CAN_OBTAIN_SIGNALS */
#endif /* ASYNC_CAN_HANDLE_SIGNALS */
