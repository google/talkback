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
#include "async_wait.h"
#include "async_internal.h"
#include "timing.h"

typedef struct {
  AsyncThreadSpecificData *tsd;
  long int timeout;
} CallbackExecuterParameters;

typedef int CallbackExecuter (CallbackExecuterParameters *parameters);

typedef struct {
  CallbackExecuter *execute;
  const char *action;
} CallbackExecuterEntry;

struct AsyncWaitDataStruct {
  AsyncThreadSpecificData *tsd;
  unsigned int waitDepth;
};

static int
alarmCallbackExecuter (CallbackExecuterParameters *parameters) {
  return asyncExecuteAlarmCallback(parameters->tsd->alarmData, &parameters->timeout);
}

static int
taskCallbackExecuter (CallbackExecuterParameters *parameters) {
  AsyncThreadSpecificData *tsd = parameters->tsd;

  if (tsd->waitData->waitDepth != 1) return 0;
  return asyncExecuteTaskCallback(tsd->taskData);
}

static int
ioCallbackExecuter (CallbackExecuterParameters *parameters) {
  return asyncExecuteIoCallback(parameters->tsd->ioData, parameters->timeout);
}

static const CallbackExecuterEntry callbackExecuterTable[] = {
  { .execute = alarmCallbackExecuter,
    .action = "alarm handled"
  },

  { .execute = taskCallbackExecuter,
    .action = "task performed"
  },

  { .execute = ioCallbackExecuter,
    .action = "I/O operation handled"
  },

  { .execute = NULL,
    .action = "wait timed out"
  }
};

void
asyncDeallocateWaitData (AsyncWaitData *wd) {
  if (wd) {
    free(wd);
  }
}

static AsyncWaitData *
getWaitData (void) {
  AsyncThreadSpecificData *tsd = asyncGetThreadSpecificData();
  if (!tsd) return NULL;

  if (!tsd->waitData) {
    AsyncWaitData *wd;

    if (!(wd = malloc(sizeof(*wd)))) {
      logMallocError();
      return NULL;
    }

    memset(wd, 0, sizeof(*wd));
    wd->tsd = tsd;
    wd->waitDepth = 0;
    tsd->waitData = wd;
  }

  return tsd->waitData;
}

static void
awaitAction (long int timeout) {
  AsyncWaitData *wd = getWaitData();

  if (wd) {
    const CallbackExecuterEntry *cbx = callbackExecuterTable;

    CallbackExecuterParameters parameters = {
      .tsd = wd->tsd,
      .timeout = timeout
    };

    wd->waitDepth += 1;
    logMessage(LOG_CATEGORY(ASYNC_EVENTS),
               "begin: level %u: timeout %ld",
               wd->waitDepth, timeout);

    while (cbx->execute) {
      if (cbx->execute(&parameters)) break;
      cbx += 1;
    }

    logMessage(LOG_CATEGORY(ASYNC_EVENTS),
               "end: level %u: %s",
               wd->waitDepth, cbx->action);

    wd->waitDepth -= 1;
  } else {
    logMessage(LOG_CATEGORY(ASYNC_EVENTS), "waiting: %ld", timeout);
    approximateDelay(timeout);
  }
}

int
asyncAwaitCondition (int timeout, AsyncConditionTester *testCondition, void *data) {
  int first = 1;
  TimePeriod period;
  startTimePeriod(&period, timeout);

  while (!(testCondition && testCondition(data))) {
    long int elapsed;

    if (first) {
      first = 0;
      elapsed = 0;
    } else if (afterTimePeriod(&period, &elapsed)) {
      return 0;
    }

    awaitAction(timeout - elapsed);
  }

  logSymbol(LOG_CATEGORY(ASYNC_EVENTS), testCondition, "condition satisfied");
  return 1;
}

void
asyncWait (int duration) {
  asyncAwaitCondition(duration, NULL, NULL);
}

void
asyncWaitFor (AsyncConditionTester *testCondition, void *data) {
  while (!asyncAwaitCondition(1000000, testCondition, data));
}
