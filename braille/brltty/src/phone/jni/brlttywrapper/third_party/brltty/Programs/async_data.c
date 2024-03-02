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
#include "thread.h"
#include "async_internal.h"

static THREAD_SPECIFIC_DATA_NEW(tsdAsync) {
  AsyncThreadSpecificData *tsd;

  if ((tsd = malloc(sizeof(*tsd)))) {
    memset(tsd, 0, sizeof(*tsd));

    tsd->waitData = NULL;
    tsd->alarmData = NULL;
    tsd->taskData = NULL;
    tsd->ioData = NULL;
    tsd->signalData = NULL;

    return tsd;
  } else {
    logMallocError();
  }

  return NULL;
}

static THREAD_SPECIFIC_DATA_DESTROY(tsdAsync) {
  AsyncThreadSpecificData *tsd = data;

  if (tsd) {
    asyncDeallocateWaitData(tsd->waitData);
    asyncDeallocateAlarmData(tsd->alarmData);
    asyncDeallocateTaskData(tsd->taskData);
    asyncDeallocateIoData(tsd->ioData);

#ifdef ASYNC_CAN_HANDLE_SIGNALS
    asyncDeallocateSignalData(tsd->signalData);
#endif /* ASYNC_CAN_HANDLE_SIGNALS */

    free(tsd);
  }
}

THREAD_SPECIFIC_DATA_CONTROL(tsdAsync);

AsyncThreadSpecificData *
asyncGetThreadSpecificData (void) {
  return getThreadSpecificData(&tsdAsync);
}
