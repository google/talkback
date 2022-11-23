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
#include "async_internal.h"

struct AsyncHandleStruct {
  Element *element;
  int identifier;
  AsyncThreadSpecificData *tsd;
};

int
asyncMakeHandle (
  AsyncHandle *handle,
  Element *(*newElement) (const void *parameters),
  const void *parameters
) {
  if (handle) {
    if (!(*handle = malloc(sizeof(**handle)))) {
      logMallocError();
      return 0;
    }
  }

  {
    Element *element = newElement(parameters);

    if (element) {
      if (handle) {
        memset(*handle, 0, sizeof(**handle));
        (*handle)->element = element;
        (*handle)->identifier = getElementIdentifier(element);
        (*handle)->tsd = asyncGetThreadSpecificData();
      }

      return 1;
    }
  }

  if (handle) free(*handle);
  return 0;
}

int
asyncTestHandle (AsyncHandle handle) {
  AsyncThreadSpecificData *tsd = asyncGetThreadSpecificData();

  if (handle->tsd == tsd) return 1;
  logMessage(LOG_WARNING, "invalid async handle");
  return 0;
}

Element *
asyncGetHandleElement (AsyncHandle handle, const Queue *queue) {
  if (asyncTestHandle(handle)) {
    if (queue) {
      Element *element = handle->element;

      if (handle->identifier == getElementIdentifier(element)) {
        if ((queue == ASYNC_ANY_QUEUE) || (queue == getElementQueue(element))) {
          return element;
        }
      }
    }
  }

  return NULL;
}

void
asyncDiscardHandle (AsyncHandle handle) {
  free(handle);
}

void
asyncCancelRequest (AsyncHandle handle) {
  Element *element = asyncGetHandleElement(handle, ASYNC_ANY_QUEUE);

  asyncDiscardHandle(handle);

  if (element) {
    Queue *queue = getElementQueue(element);
    const AsyncQueueMethods *methods = getQueueData(queue);

    if (methods) {
      if (methods->cancelRequest) {
        methods->cancelRequest(element);
        return;
      }
    }

    deleteElement(element);
  }
}

typedef struct {
  Element *element;
} ElementHandleParameters;

static Element *
newElementHandle (const void *parameters) {
  const ElementHandleParameters *ehp = parameters;

  return ehp->element;
}

int
asyncMakeElementHandle (AsyncHandle *handle, Element *element) {
  const ElementHandleParameters ehp = {
    .element = element
  };

  return asyncMakeHandle(handle, newElementHandle, &ehp);
}
