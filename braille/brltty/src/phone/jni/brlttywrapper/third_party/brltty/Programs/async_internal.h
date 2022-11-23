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

#ifndef BRLTTY_INCLUDED_ASYNC_INTERNAL
#define BRLTTY_INCLUDED_ASYNC_INTERNAL

#include "async.h"
#include "queue.h"

#ifdef __cplusplus
extern "C" {
#endif /* __cplusplus */

typedef struct AsyncWaitDataStruct AsyncWaitData;
extern void asyncDeallocateWaitData (AsyncWaitData *waitData);

typedef struct AsyncAlarmDataStruct AsyncAlarmData;
extern void asyncDeallocateAlarmData (AsyncAlarmData *alarmData);
extern int asyncExecuteAlarmCallback (AsyncAlarmData *ad, long int *timeout);

typedef struct AsyncTaskDataStruct AsyncTaskData;
extern void asyncDeallocateTaskData (AsyncTaskData *taskData);
extern int asyncExecuteTaskCallback (AsyncTaskData *td);

typedef struct AsyncIoDataStruct AsyncIoData;
extern void asyncDeallocateIoData (AsyncIoData *ioData);
extern int asyncExecuteIoCallback (AsyncIoData *iod, long int timeout);

typedef struct AsyncSignalDataStruct AsyncSignalData;
extern void asyncDeallocateSignalData (AsyncSignalData *sd);

typedef struct {
  AsyncWaitData *waitData;
  AsyncAlarmData *alarmData;
  AsyncTaskData *taskData;
  AsyncIoData *ioData;
  AsyncSignalData *signalData;
} AsyncThreadSpecificData;

extern AsyncThreadSpecificData *asyncGetThreadSpecificData (void);

extern int asyncMakeHandle (
  AsyncHandle *handle,
  Element *(*newElement) (const void *parameters),
  const void *parameters
);

extern int asyncMakeElementHandle (AsyncHandle *handle, Element *element);

#define ASYNC_ANY_QUEUE ((const Queue *)1)
extern Element *asyncGetHandleElement (AsyncHandle handle, const Queue *queue);

typedef struct {
  void (*cancelRequest) (Element *element);
} AsyncQueueMethods;

#ifdef __cplusplus
}
#endif /* __cplusplus */

#endif /* BRLTTY_INCLUDED_ASYNC_INTERNAL */
