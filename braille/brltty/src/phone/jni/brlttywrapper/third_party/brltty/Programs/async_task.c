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
#include "async_task.h"
#include "async_internal.h"
#include "async_event.h"

typedef struct {
  AsyncTaskCallback *callback;
  void *data;
} TaskDefinition;

struct AsyncTaskDataStruct {
  Queue *taskQueue;
};

void
asyncDeallocateTaskData (AsyncTaskData *td) {
  if (td) {
    if (td->taskQueue) deallocateQueue(td->taskQueue);
    free(td);
  }
}

static AsyncTaskData *
getTaskData (void) {
  AsyncThreadSpecificData *tsd = asyncGetThreadSpecificData();
  if (!tsd) return NULL;

  if (!tsd->taskData) {
    AsyncTaskData *td;

    if (!(td = malloc(sizeof(*td)))) {
      logMallocError();
      return NULL;
    }

    memset(td, 0, sizeof(*td));
    td->taskQueue = NULL;
    tsd->taskData = td;
  }

  return tsd->taskData;
}

static void
deallocateTaskDefinition (void *item, void *data) {
  TaskDefinition *task = item;

  free(task);
}

static Queue *
getTaskQueue (int create) {
  AsyncTaskData *td = getTaskData();
  if (!td) return NULL;

  if (!td->taskQueue && create) {
    td->taskQueue = newQueue(deallocateTaskDefinition, NULL);
  }

  return td->taskQueue;
}

static int
addTask (TaskDefinition *task) {
  Queue *queue = getTaskQueue(1);

  if (queue) {
    if (enqueueItem(queue, task)) {
      logSymbol(LOG_CATEGORY(ASYNC_EVENTS), task->callback, "task added");
      return 1;
    }
  }

  return 0;
}

int
asyncAddTask (AsyncEvent *event, AsyncTaskCallback *callback, void *data) {
  TaskDefinition *task;

  if ((task = malloc(sizeof(*task)))) {
    memset(task, 0, sizeof(*task));
    task->callback = callback;
    task->data = data;

    if (event) {
      if (asyncSignalEvent(event, task)) return 1;
    } else if (addTask(task)) {
      return 1;
    }

    free(task);
  } else {
    logMallocError();
  }

  return 0;
}

ASYNC_EVENT_CALLBACK(asyncHandleAddTaskEvent) {
  addTask(parameters->signalData);
}

AsyncEvent *
asyncNewAddTaskEvent (void) {
  return asyncNewEvent(asyncHandleAddTaskEvent, NULL);
}

int
asyncExecuteTaskCallback (AsyncTaskData *td) {
  if (td) {
    Queue *queue = td->taskQueue;

    if (queue) {
      TaskDefinition *task = dequeueItem(queue);

      if (task) {
        AsyncTaskCallback *callback = task->callback;

        logSymbol(LOG_CATEGORY(ASYNC_EVENTS), callback, "task starting");
        if (callback) callback(task->data);
        free(task);
        return 1;
      }
    }
  }

  return 0;
}
