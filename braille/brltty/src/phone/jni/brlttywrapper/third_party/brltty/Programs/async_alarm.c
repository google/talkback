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
#include "async_alarm.h"
#include "async_internal.h"
#include "timing.h"

typedef struct {
  TimeValue time;
  int interval;

  AsyncAlarmCallback *callback;
  void *data;

  unsigned active:1;
  unsigned cancel:1;
  unsigned reschedule:1;
} AlarmEntry;

struct AsyncAlarmDataStruct {
  Queue *alarmQueue;
};

void
asyncDeallocateAlarmData (AsyncAlarmData *ad) {
  if (ad) {
    if (ad->alarmQueue) deallocateQueue(ad->alarmQueue);
    free(ad);
  }
}

static AsyncAlarmData *
getAlarmData (void) {
  AsyncThreadSpecificData *tsd = asyncGetThreadSpecificData();
  if (!tsd) return NULL;

  if (!tsd->alarmData) {
    AsyncAlarmData *ad;

    if (!(ad = malloc(sizeof(*ad)))) {
      logMallocError();
      return NULL;
    }

    memset(ad, 0, sizeof(*ad));
    ad->alarmQueue = NULL;
    tsd->alarmData = ad;
  }

  return tsd->alarmData;
}

static void
cancelAlarm (Element *element) {
  AlarmEntry *alarm = getElementItem(element);

  if (alarm->active) {
    alarm->cancel = 1;
  } else {
    deleteElement(element);
  }
}

static void
deallocateAlarmEntry (void *item, void *data) {
  AlarmEntry *alarm = item;

  free(alarm);
}

static int
compareAlarmEntries (const void *newItem, const void *existingItem, void *queueData) {
  const AlarmEntry *newAlarm = newItem;
  const AlarmEntry *existingAlarm = existingItem;

  return compareTimeValues(&newAlarm->time, &existingAlarm->time) < 0;
}

static Queue *
getAlarmQueue (int create) {
  AsyncAlarmData *ad = getAlarmData();
  if (!ad) return NULL;

  if (!ad->alarmQueue && create) {
    if ((ad->alarmQueue = newQueue(deallocateAlarmEntry, compareAlarmEntries))) {
      static AsyncQueueMethods methods = {
        .cancelRequest = cancelAlarm
      };

      setQueueData(ad->alarmQueue, &methods);
    }
  }

  return ad->alarmQueue;
}

typedef struct {
  const TimeValue *time;
  AsyncAlarmCallback *callback;
  void *data;
} AlarmElementParameters;

static Element *
newAlarmElement (const void *parameters) {
  const AlarmElementParameters *aep = parameters;
  Queue *alarms = getAlarmQueue(1);

  if (alarms) {
    AlarmEntry *alarm;

    if ((alarm = malloc(sizeof(*alarm)))) {
      memset(alarm, 0, sizeof(*alarm));

      alarm->time = *aep->time;

      alarm->callback = aep->callback;
      alarm->data = aep->data;

      alarm->active = 0;
      alarm->cancel = 0;
      alarm->reschedule = 0;

      {
        Element *element = enqueueItem(alarms, alarm);

        if (element) {
          logSymbol(LOG_CATEGORY(ASYNC_EVENTS), aep->callback, "alarm added");
          return element;
        }
      }

      free(alarm);
    } else {
      logMallocError();
    }
  }

  return NULL;
}

int
asyncNewAbsoluteAlarm (
  AsyncHandle *handle,
  const TimeValue *time,
  AsyncAlarmCallback *callback,
  void *data
) {
  const AlarmElementParameters aep = {
    .time = time,
    .callback = callback,
    .data = data
  };

  return asyncMakeHandle(handle, newAlarmElement, &aep);
}

int
asyncNewRelativeAlarm (
  AsyncHandle *handle,
  int milliseconds,
  AsyncAlarmCallback *callback,
  void *data
) {
  TimeValue time;

  getMonotonicTime(&time);
  adjustTimeValue(&time, milliseconds);
  return asyncNewAbsoluteAlarm(handle, &time, callback, data);
}

static Element *
getAlarmElement (AsyncHandle handle) {
  return asyncGetHandleElement(handle, getAlarmQueue(0));
}

int
asyncResetAlarmTo (AsyncHandle handle, const TimeValue *time) {
  Element *element = getAlarmElement(handle);

  if (element) {
    AlarmEntry *alarm = getElementItem(element);

    alarm->time = *time;
    requeueElement(element);
    return 1;
  }

  return 0;
}

int
asyncResetAlarmIn (AsyncHandle handle, int milliseconds) {
  TimeValue time;

  getMonotonicTime(&time);
  adjustTimeValue(&time, milliseconds);
  return asyncResetAlarmTo(handle, &time);
}

int
asyncResetAlarmInterval (AsyncHandle handle, int milliseconds) {
  Element *element = getAlarmElement(handle);

  if (element) {
    AlarmEntry *alarm = getElementItem(element);

    alarm->interval = milliseconds;
    alarm->reschedule = milliseconds > 0;
    return 1;
  }

  return 0;
}

static int
testInactiveAlarm (void *item, void *data) {
  const AlarmEntry *alarm = item;

  return !alarm->active;
}

int
asyncExecuteAlarmCallback (AsyncAlarmData *ad, long int *timeout) {
  if (ad) {
    Queue *alarms = ad->alarmQueue;

    if (alarms) {
      Element *element = processQueue(alarms, testInactiveAlarm, NULL);

      if (element) {
        AlarmEntry *alarm = getElementItem(element);
        TimeValue now;
        long int milliseconds;

        getMonotonicTime(&now);
        milliseconds = millisecondsBetween(&now, &alarm->time);

        if (milliseconds <= 0) {
          AsyncAlarmCallback *callback = alarm->callback;
          const AsyncAlarmCallbackParameters parameters = {
            .now = &now,
            .data = alarm->data
          };

          logSymbol(LOG_CATEGORY(ASYNC_EVENTS), callback, "alarm starting");
          alarm->active = 1;
          if (callback) callback(&parameters);
          alarm->active = 0;

          if (alarm->reschedule) {
            adjustTimeValue(&alarm->time, alarm->interval);
            getMonotonicTime(&now);
            if (compareTimeValues(&alarm->time, &now) < 0) alarm->time = now;
            requeueElement(element);
          } else {
            alarm->cancel = 1;
          }

          if (alarm->cancel) deleteElement(element);
          return 1;
        }

        if (milliseconds < *timeout) {
          *timeout = milliseconds;
          logSymbol(LOG_CATEGORY(ASYNC_EVENTS), alarm->callback, "next alarm: %ld", *timeout);
        }
      }
    }
  }

  return 0;
}
