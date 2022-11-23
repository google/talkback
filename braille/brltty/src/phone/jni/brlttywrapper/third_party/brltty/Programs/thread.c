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
#include <errno.h>

#include "log.h"
#include "strfmt.h"
#include "thread.h"
#include "async_signal.h"
#include "async_event.h"
#include "async_wait.h"

#undef HAVE_THREAD_NAMES

#ifdef GOT_PTHREADS
typedef struct {
  ThreadFunction *function;
  void *argument;
  char name[0];
} RunThreadArgument;

static void *
runThread (void *argument) {
  RunThreadArgument *run = argument;
  void *result;

  setThreadName(run->name);
  logMessage(LOG_CATEGORY(ASYNC_EVENTS), "thread starting: %s", run->name);
  result = run->function(run->argument);
  logMessage(LOG_CATEGORY(ASYNC_EVENTS), "thread finished: %s", run->name);

  free(run);
  return result;
}

typedef struct {
  const char *const name;
  pthread_t *const thread;
  const pthread_attr_t *const attributes;
  ThreadFunction *const function;
  void *const argument;

  int error;
} CreateThreadParameters;

static int
createActualThread (void *parameters) {
  CreateThreadParameters *create = parameters;
  RunThreadArgument *run;

  if ((run = malloc(sizeof(*run) + strlen(create->name) + 1))) {
    memset(run, 0, sizeof(*run));
    run->function = create->function;
    run->argument = create->argument;
    strcpy(run->name, create->name);

    logMessage(LOG_CATEGORY(ASYNC_EVENTS), "creating thread: %s", create->name);
    create->error = pthread_create(create->thread, create->attributes, runThread, run);
    if (!create->error) return 1;
    logMessage(LOG_CATEGORY(ASYNC_EVENTS), "thread not created: %s: %s", create->name, strerror(create->error));

    free(run);
  } else {
    create->error = errno;
    logMallocError();
  }

  return 0;
}

#ifdef ASYNC_CAN_BLOCK_SIGNALS
ASYNC_WITH_SIGNALS_BLOCKED_FUNCTION(createSignalSafeThread) {
  static const int signals[] = {
#ifdef SIGINT
    SIGINT,
#endif /* SIGINT */

#ifdef SIGTERM
    SIGTERM,
#endif /* SIGTERM */

#ifdef SIGCHLD
    SIGCHLD,
#endif /* SIGCHLD */

    0
  };

  CreateThreadParameters *create = data;
  const int *signal = signals;

  while (*signal) {
    asyncSetSignalBlocked(*signal, 1);
    signal += 1;
  }

  createActualThread(create);
}
#endif /* ASYNC_CAN_BLOCK_SIGNALS */

int
createThread (
  const char *name,
  pthread_t *thread, const pthread_attr_t *attributes,
  ThreadFunction *function, void *argument
) {
  CreateThreadParameters create = {
    .name = name,
    .thread = thread,
    .attributes = attributes,
    .function = function,
    .argument = argument
  };

#ifdef ASYNC_CAN_BLOCK_SIGNALS
  asyncWithObtainableSignalsBlocked(createSignalSafeThread, &create);
#else /* ASYNC_CAN_BLOCK_SIGNALS */
  createActualThread(&create);
#endif /* ASYNC_CAN_BLOCK_SIGNALS */

  return create.error;
}

typedef struct {
  ThreadFunction *const function;
  void *const argument;

  AsyncEvent *event;
  unsigned returned:1;
} CallThreadFunctionData;

THREAD_FUNCTION(runThreadFunction) {
  CallThreadFunctionData *ctf = argument;
  void *result = ctf->function? ctf->function(ctf->argument): NULL;

  asyncSignalEvent(ctf->event, NULL);
  return result;
}

ASYNC_EVENT_CALLBACK(handleThreadFunctionReturned) {
  CallThreadFunctionData *ctf = parameters->eventData;

  ctf->returned = 1;
}

ASYNC_CONDITION_TESTER(testThreadFunctionReturned) {
  CallThreadFunctionData *ctf = data;

  return ctf->returned;
}

int
callThreadFunction (
  const char *name, ThreadFunction *function,
  void *argument, void **result
) {
  int called = 0;

  CallThreadFunctionData ctf = {
    .function = function,
    .argument = argument,

    .returned = 0
  };

  if ((ctf.event = asyncNewEvent(handleThreadFunctionReturned, &ctf))) {
    pthread_t thread;
    int error = createThread(name, &thread, NULL, runThreadFunction, &ctf);

    if (!error) {
      asyncWaitFor(testThreadFunctionReturned, &ctf);

      {
        void *r;

        if (!result) result = &r;
        pthread_join(thread, result);
      }

      called = 1;
    } else {
      errno = error;
    }

    asyncDiscardEvent(ctf.event);
  }

  return called;
}

int
lockMutex (pthread_mutex_t *mutex) {
  int result = pthread_mutex_lock(mutex);

  logSymbol(LOG_CATEGORY(ASYNC_EVENTS), mutex, "mutex lock");
  return result;
}

int
unlockMutex (pthread_mutex_t *mutex) {
  logSymbol(LOG_CATEGORY(ASYNC_EVENTS), mutex, "mutex unlock");
  return pthread_mutex_unlock(mutex);
}

#if defined(HAVE_PTHREAD_GETNAME_NP) && defined(__GLIBC__)
#define HAVE_THREAD_NAMES

size_t
formatThreadName (char *buffer, size_t size) {
  int error = pthread_getname_np(pthread_self(), buffer, size);

  return error? 0: strlen(buffer);
}

void
setThreadName (const char *name) {
  pthread_setname_np(pthread_self(), name);
}

#elif defined(HAVE_PTHREAD_GETNAME_NP) && defined(__APPLE__)
#define HAVE_THREAD_NAMES

size_t
formatThreadName (char *buffer, size_t size) {
  {
    int error = pthread_getname_np(pthread_self(), buffer, size);

    if (error) return 0;
    if (*buffer) return strlen(buffer);
  }

  if (pthread_main_np()) {
    size_t length;

    STR_BEGIN(buffer, size);
    STR_PRINTF("main");
    length = STR_LENGTH;
    STR_END;

    return length;
  }

  return 0;
}

void
setThreadName (const char *name) {
  pthread_setname_np(name);
}

#endif /* thread names */
#endif /* GOT_PTHREADS */

#ifndef HAVE_THREAD_NAMES
size_t
formatThreadName (char *buffer, size_t size) {
  return 0;
}

void
setThreadName (const char *name) {
}
#endif /* HAVE_THREAD_NAMES */

#if defined(PTHREAD_MUTEX_INITIALIZER)
static void
createThreadSpecificDataKey (ThreadSpecificDataControl *ctl) {
  int error;

  pthread_mutex_lock(&ctl->mutex);
    if (!ctl->key.created) {
      error = pthread_key_create(&ctl->key.value, ctl->destroy);

      if (!error) {
        ctl->key.created = 1;
      } else {
        logActionError(error, "pthread_key_create");
      }
    }
  pthread_mutex_unlock(&ctl->mutex);
}

#ifdef ASYNC_CAN_BLOCK_SIGNALS
ASYNC_WITH_SIGNALS_BLOCKED_FUNCTION(createThreadSpecificDataKeyWithSignalsBlocked) {
  ThreadSpecificDataControl *ctl = data;

  createThreadSpecificDataKey(ctl);
}
#endif /* ASYNC_CAN_BLOCK_SIGNALS */

void *
getThreadSpecificData (ThreadSpecificDataControl *ctl) {
  int error;

#ifdef ASYNC_CAN_BLOCK_SIGNALS
  asyncWithAllSignalsBlocked(createThreadSpecificDataKeyWithSignalsBlocked, ctl);
#else /* ASYNC_CAN_BLOCK_SIGNALS */
  createThreadSpecificDataKey(ctl);
#endif /* ASYNC_CAN_BLOCK_SIGNALS */

  if (ctl->key.created) {
    void *tsd = pthread_getspecific(ctl->key.value);
    if (tsd) return tsd;

    if ((tsd = ctl->new())) {
      if (!(error = pthread_setspecific(ctl->key.value, tsd))) {
        return tsd;
      } else {
        logActionError(error, "pthread_setspecific");
      }

      ctl->destroy(tsd);
    }
  }

  return NULL;
}

#else /* thread specific data */
#include "program.h"

static void
exitThreadSpecificData (void *data) {
  ThreadSpecificDataControl *ctl = data;

  if (ctl->data) {
    ctl->destroy(ctl->data);
    ctl->data = NULL;
  }
}

void *
getThreadSpecificData (ThreadSpecificDataControl *ctl) {
  if (!ctl->data) {
    if ((ctl->data = ctl->new())) {
      onProgramExit("thread-specific-data", exitThreadSpecificData, ctl);
    }
  }

  return ctl->data;
}
#endif /* thread specific data */
