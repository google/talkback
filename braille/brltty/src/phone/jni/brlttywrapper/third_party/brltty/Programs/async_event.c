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
#include "async_io.h"
#include "async_event.h"
#include "async_internal.h"
#include "file.h"

struct AsyncEventStruct {
  AsyncEventCallback *callback;
  void *data;

  FileDescriptor pipeInput;
  FileDescriptor pipeOutput;

  FileDescriptor monitorDescriptor;
  AsyncHandle monitorHandle;

#ifdef __MINGW32__
  CRITICAL_SECTION criticalSection;
  unsigned int pendingCount;
#endif /* __MINGW32__ */
};

ASYNC_MONITOR_CALLBACK(asyncMonitorEventPipe) {
  AsyncEvent *event = parameters->data;
  void *data;
  const size_t size = sizeof(data);

  if (readFileDescriptor(event->pipeOutput, &data, size) == size) {
#ifdef __MINGW32__
    EnterCriticalSection(&event->criticalSection);
    if (!--event->pendingCount) ResetEvent(event->monitorDescriptor);
    LeaveCriticalSection(&event->criticalSection);
#endif /* __MINGW32__ */

    {
      AsyncEventCallback *callback = event->callback;

      const AsyncEventCallbackParameters parameters = {
        .eventData = event->data,
        .signalData = data
      };

      logSymbol(LOG_CATEGORY(ASYNC_EVENTS), callback, "event starting");
      if (callback) callback(&parameters);
    }

    return 1;
  }

  return 0;
}

int
asyncSignalEvent (AsyncEvent *event, void *data) {
  const size_t size = sizeof(data);
  ssize_t result = writeFileDescriptor(event->pipeInput, &data, size);

  if (result == size) {
#ifdef __MINGW32__
    EnterCriticalSection(&event->criticalSection);
    if (!event->pendingCount++) SetEvent(event->monitorDescriptor);
    LeaveCriticalSection(&event->criticalSection);
#endif /* __MINGW32__ */

    return 1;
  }

  if (result == -1) {
    logSystemError("write");
  } else {
    logMessage(LOG_ERR, "short write"); 
  }

  return 0;
}

AsyncEvent *
asyncNewEvent (AsyncEventCallback *callback, void *data) {
  AsyncEvent *event;

  if ((event = malloc(sizeof(*event)))) {
    memset(event, 0, sizeof(*event));
    event->callback = callback;
    event->data = data;

    if (createAnonymousPipe(&event->pipeInput, &event->pipeOutput)) {
#ifdef __MINGW32__
      if (!(event->monitorDescriptor = CreateEvent(NULL, TRUE, FALSE, NULL))) {
        logWindowsSystemError("CreateEvent");
        event->monitorDescriptor = INVALID_FILE_DESCRIPTOR;
      }
#else /* __MINGW32__ */
      event->monitorDescriptor = event->pipeOutput;
#endif /* __MINGW32__ */

      if (event->monitorDescriptor != INVALID_FILE_DESCRIPTOR) {
        if (asyncMonitorFileInput(&event->monitorHandle, event->monitorDescriptor,
                                  asyncMonitorEventPipe, event)) {
#ifdef __MINGW32__
          InitializeCriticalSection(&event->criticalSection);
          event->pendingCount = 0;
#endif /* __MINGW32__ */

          logSymbol(LOG_CATEGORY(ASYNC_EVENTS), event->callback, "event added");
          return event;
        }

        closeFileDescriptor(event->monitorDescriptor);
      }

      closeFileDescriptor(event->pipeInput);
      closeFileDescriptor(event->pipeOutput);
    }

    free(event);
  } else {
    logMallocError();
  }

  return NULL;
}

void
asyncDiscardEvent (AsyncEvent *event) {
  asyncCancelRequest(event->monitorHandle);

  closeFileDescriptor(event->pipeInput);
  closeFileDescriptor(event->pipeOutput);

#ifdef __MINGW32__
  CloseHandle(event->monitorDescriptor);
  DeleteCriticalSection(&event->criticalSection);
#endif /* __MINGW32__ */

  logSymbol(LOG_CATEGORY(ASYNC_EVENTS), event->callback, "event removed");
  free(event);
}

