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
#include "parameters.h"
#include "activity.h"
#include "async_alarm.h"
#include "async_wait.h"

typedef enum {
  ACT_STOPPED,
  ACT_PREPARED,
  ACT_SCHEDULED,
  ACT_STARTED,

  ACT_PREPARING,
  ACT_PREPARING_STOP,

  ACT_STARTING,
  ACT_STARTING_STOP,
  ACT_STARTING_RESTART,

  ACT_STOPPING,
  ACT_STOPPING_START
} ActivityState;

typedef struct {
  const char *name;
} ActivityStateDescriptor;

static const ActivityStateDescriptor activityStateDescriptors[] = {
  [ACT_STOPPED] = {
    .name = "stopped"
  },

  [ACT_PREPARED] = {
    .name = "prepared"
  },

  [ACT_SCHEDULED] = {
    .name = "scheduled"
  },

  [ACT_STARTED] = {
    .name = "started"
  },

  [ACT_PREPARING] = {
    .name = "preparing"
  },

  [ACT_PREPARING_STOP] = {
    .name = "preparing+stop"
  },

  [ACT_STARTING] = {
    .name = "starting"
  },

  [ACT_STARTING_STOP] = {
    .name = "starting+stop"
  },

  [ACT_STARTING_RESTART] = {
    .name = "starting+restart"
  },

  [ACT_STOPPING] = {
    .name = "stopping"
  },

  [ACT_STOPPING_START] = {
    .name = "stopping+start"
  },
};

struct ActivityObjectStruct {
  const ActivityMethods *methods;
  void *data;

  ActivityState state;
  AsyncHandle startAlarm;
};

static const char *
getActivityStateName (ActivityState state) {
  if (state < ARRAY_COUNT(activityStateDescriptors)) {
    const char *name = activityStateDescriptors[state].name;

    if (name && *name) return name;
  }

  return "unknown";
}

static void
logUnexpectedActivityState (ActivityObject *activity, const char *action) {
  ActivityState state = activity->state;

  logMessage(LOG_WARNING, "unexpected activity state: %s: %s: %u[%s]",
             activity->methods->activityName, action, state, getActivityStateName(state));
}

static void
setActivityState (ActivityObject *activity, ActivityState state) {
  logMessage(LOG_DEBUG, "activity state change: %s: %u[%s]",
             activity->methods->activityName, state, getActivityStateName(state));

  activity->state = state;
}

static void
logActivityActionRequest (ActivityObject *activity, const char *action) {
  logMessage(LOG_DEBUG, "activity action request: %s: %s",
             activity->methods->activityName, action);
}

static void
logActivityActionFailed (ActivityObject *activity, const char *action) {
  logMessage(LOG_DEBUG, "activity action failed: %s: %s",
             activity->methods->activityName, action);
}

static void
logActivityActionTimeout (ActivityObject *activity, const char *action) {
  logMessage(LOG_DEBUG, "activity action timeout: %s: %s",
             activity->methods->activityName, action);
}

static void
cancelActivityStartAlarm (ActivityObject *activity) {
  asyncCancelRequest(activity->startAlarm);
  activity->startAlarm = NULL;
}

ASYNC_ALARM_CALLBACK(handleActivityStartAlarm) {
  ActivityObject *activity = parameters->data;
  ActivityStartMethod *start = activity->methods->start;
  ActivityState oldState = activity->state;
  int started;
  ActivityState newState;

  setActivityState(activity, ACT_STARTING);
  started = !start || start(activity->data);

  if (started) {
    cancelActivityStartAlarm(activity);
  } else {
    logActivityActionFailed(activity, "start");
  }

  newState = activity->state;
  setActivityState(activity, (started? ACT_STARTED: oldState));

  if (newState == ACT_STARTING_STOP) {
    stopActivity(activity);
  } else if (newState == ACT_STARTING_RESTART) {
    stopActivity(activity);
    startActivity(activity);
  } else if (newState != ACT_STARTING) {
    logUnexpectedActivityState(activity, "starting");
  }
}

static int
prepareActivity (ActivityObject *activity) {
  ActivityPrepareMethod *prepare = activity->methods->prepare;
  ActivityState oldState = activity->state;

  if (!prepare) {
    setActivityState(activity, ACT_PREPARED);
    return 1;
  }

  setActivityState(activity, ACT_PREPARING);

  if (!prepare(activity->data)) {
    setActivityState(activity, oldState);
    return 0;
  }

  if (activity->state == ACT_PREPARING) {
    setActivityState(activity, ACT_PREPARED);
    return 1;
  }

  if (activity->state == ACT_PREPARING_STOP) {
    setActivityState(activity, ACT_STOPPED);
    return 0;
  }

  logUnexpectedActivityState(activity, "preparing");
  return 0;
}

static int
scheduleActivity (ActivityObject *activity) {
  if (asyncNewRelativeAlarm(&activity->startAlarm, 0, handleActivityStartAlarm, activity)) {
    if (asyncResetAlarmEvery(activity->startAlarm, activity->methods->retryInterval)) {
      setActivityState(activity, ACT_SCHEDULED);
      return 1;
    }

    cancelActivityStartAlarm(activity);
  }

  return 0;
}

void
startActivity (ActivityObject *activity) {
  logActivityActionRequest(activity, "start");

  while (1) {
    switch (activity->state) {
      case ACT_STOPPED:
        if (prepareActivity(activity)) continue;
        return;

      case ACT_PREPARING_STOP:
        setActivityState(activity, ACT_PREPARING);
        continue;

      case ACT_PREPARED:
        if (scheduleActivity(activity)) continue;
        return;

      case ACT_SCHEDULED:
        asyncResetAlarmIn(activity->startAlarm, 0);
        return;

      case ACT_STARTING_STOP:
        setActivityState(activity, ACT_STARTING_RESTART);
        continue;

      case ACT_STOPPING:
        setActivityState(activity, ACT_STOPPING_START);
        continue;

      case ACT_PREPARING:
      case ACT_STARTING:
      case ACT_STARTING_RESTART:
      case ACT_STARTED:
      case ACT_STOPPING_START:
        return;
    }

    logUnexpectedActivityState(activity, "start");
    break;
  }
}

void
stopActivity (ActivityObject *activity) {
  logActivityActionRequest(activity, "stop");

  while (1) {
    switch (activity->state) {
      case ACT_PREPARING:
        setActivityState(activity, ACT_PREPARING_STOP);
        continue;

      case ACT_PREPARED:
        setActivityState(activity, ACT_STOPPED);
        continue;

      case ACT_SCHEDULED:
        cancelActivityStartAlarm(activity);
        setActivityState(activity, ACT_PREPARED);
        continue;

      case ACT_STARTING:
      case ACT_STARTING_RESTART:
        setActivityState(activity, ACT_STARTING_STOP);
        continue;

      case ACT_STARTED: {
        ActivityStopMethod *stop = activity->methods->stop;

        if (stop) {
          ActivityState newState;

          setActivityState(activity, ACT_STOPPING);
          stop(activity->data);
          newState = activity->state;
          setActivityState(activity, ACT_STOPPED);

          if (newState == ACT_STOPPING_START) {
            startActivity(activity);
          } else if (newState != ACT_STOPPING) {
            logUnexpectedActivityState(activity, "stopping");
          }
        } else {
          setActivityState(activity, ACT_STOPPED);
        }

        return;
      }

      case ACT_STOPPING_START:
        setActivityState(activity, ACT_STOPPING);
        continue;

      case ACT_PREPARING_STOP:
      case ACT_STARTING_STOP:
      case ACT_STOPPING:
      case ACT_STOPPED:
        return;
    }

    logUnexpectedActivityState(activity, "stop");
    break;
  }
}

ActivityObject *
newActivity (const ActivityMethods *methods, void *data) {
  ActivityObject *activity;

  if ((activity = malloc(sizeof(*activity)))) {
    memset(activity, 0, sizeof(*activity));

    activity->methods = methods;
    activity->data = data;

    activity->state = ACT_STOPPED;
    activity->startAlarm = NULL;

    return activity;
  } else {
    logMallocError();
  }

  return NULL;
}

void
destroyActivity (ActivityObject *activity) {
  stopActivity(activity);
  awaitActivityStopped(activity);
  free(activity);
}

int
isActivityStarted (const ActivityObject *activity) {
  return activity->state == ACT_STARTED;
}

int
isActivityStopped (const ActivityObject *activity) {
  return activity->state == ACT_STOPPED;
}

ASYNC_CONDITION_TESTER(testActivityStarted) {
  const ActivityObject *activity = data;

  return isActivityStarted(activity);
}

int
awaitActivityStarted (ActivityObject *activity) {
  int timeout = activity->methods->startTimeout;

  if (!timeout) timeout = DEFAULT_ACTIVITY_START_TIMEOUT;
  if (asyncAwaitCondition(timeout, testActivityStarted, activity)) return 1;

  logActivityActionTimeout(activity, "start");
  return 0;
}

ASYNC_CONDITION_TESTER(testActivityStopped) {
  const ActivityObject *activity = data;

  return isActivityStopped(activity);
}

int
awaitActivityStopped (ActivityObject *activity) {
  int timeout = activity->methods->stopTimeout;

  if (!timeout) timeout = DEFAULT_ACTIVITY_STOP_TIMEOUT;
  if (asyncAwaitCondition(timeout, testActivityStopped, activity)) return 1;

  logActivityActionTimeout(activity, "stop");
  return 0;
}
