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

#include "log.h"
#include "queue.h"
#include "lock.h"
#include "program.h"

static Element *discardedElements = NULL;

static LockDescriptor *
getDiscardedElementsLock (void) {
  static LockDescriptor *lock = NULL;

  return getLockDescriptor(&lock, "queue-discarded-elements");
}

static void
lockDiscardedElements (void) {
  obtainExclusiveLock(getDiscardedElementsLock());
}

static void
unlockDiscardedElements (void) {
  releaseLock(getDiscardedElementsLock());
}

struct QueueStruct {
  Element *head;
  unsigned int size;
  void *data;
  ItemDeallocator *deallocateItem;
  ItemComparator *compareItems;
};

struct ElementStruct {
  Element *next;
  Element *previous;
  Queue *queue;
  int identifier;
  void *item;
};

static void
addElement (Queue *queue, Element *element) {
  {
    static int identifier = 0;
    element->identifier = ++identifier;
  }

  element->queue = queue;
  queue->size += 1;
}

static void
removeElement (Element *element) {
  element->queue->size -= 1;
  element->queue = NULL;
  element->identifier = 0;
}

static void
removeItem (Element *element) {
  if (element->item) {
    Queue *queue = element->queue;
    ItemDeallocator *deallocateItem = queue->deallocateItem;

    if (deallocateItem) deallocateItem(element->item, queue->data);
    element->item = NULL;
  }
}

static void
discardElement (Element *element) {
  removeItem(element);
  removeElement(element);

  lockDiscardedElements();
    element->next = discardedElements;
    discardedElements = element;
  unlockDiscardedElements();
}

static Element *
retrieveElement (void) {
  Element *element;

  lockDiscardedElements();
    if ((element = discardedElements)) {
      discardedElements = element->next;
      element->next = NULL;
    }
  unlockDiscardedElements();

  return element;
}

static Element *
newElement (Queue *queue, void *item) {
  Element *element;

  if (!(element = retrieveElement())) {
    if (!(element = malloc(sizeof(*element)))) {
      logMallocError();
      return NULL;
    }

    element->previous = element->next = NULL;
  }

  addElement(queue, element);
  element->item = item;
  return element;
}

static void
linkFirstElement (Element *element) {
  element->queue->head = element->previous = element->next = element;
}

static void
linkAdditionalElement (Element *reference, Element *element) {
  element->next = reference;
  element->previous = reference->previous;
  element->next->previous = element;
  element->previous->next = element;
}

static void
unlinkElement (Element *element) {
  Queue *queue = element->queue;
  if (element == element->next) {
    queue->head = NULL;
  } else {
    if (element == queue->head) queue->head = element->next;
    element->next->previous = element->previous;
    element->previous->next = element->next;
  }
  element->previous = element->next = NULL;
}

void
deleteElement (Element *element) {
  unlinkElement(element);
  discardElement(element);
}

typedef struct {
  Queue *queue;
  void *item;
} FindReferenceElementData;

static int
findReferenceElement (const void *item, void *data) {
  const FindReferenceElementData *fre = data;

  return fre->queue->compareItems(fre->item, item, fre->queue->data);
}

static void
linkElement (Element *element) {
  Queue *queue = element->queue;

  if (queue->head) {
    Element *reference;
    int isNewHead = 0;

    if (queue->compareItems) {
      FindReferenceElementData fre = {
        .queue = queue,
        .item = element->item
      };

      if (!(reference = findElement(queue, findReferenceElement, &fre))) {
        reference = queue->head;
      } else if (reference == queue->head) {
        isNewHead = 1;
      }
    } else {
      reference = queue->head;
    }

    linkAdditionalElement(reference, element);
    if (isNewHead) queue->head = element;
  } else {
    linkFirstElement(element);
  }
}

Element *
enqueueItem (Queue *queue, void *item) {
  Element *element = newElement(queue, item);

  if (element) linkElement(element);
  return element;
}

void
requeueElement (Element *element) {
  unlinkElement(element);
  linkElement(element);
}

void
moveElement (Element *element, Queue *queue) {
  unlinkElement(element);
  removeElement(element);
  addElement(queue, element);
  linkElement(element);
}

void *
dequeueItem (Queue *queue) {
  void *item;
  Element *element;

  if (!(element = queue->head)) return NULL;
  item = element->item;
  element->item = NULL;

  deleteElement(element);
  return item;
}

Queue *
getElementQueue (const Element *element) {
  return element->queue;
}

int
getElementIdentifier (const Element *element) {
  return element->identifier;
}

void *
getElementItem (const Element *element) {
  return element->item;
}

static int queueInitialized = 0;

static void
exitQueue (void *data) {
  lockDiscardedElements();
    while (discardedElements) {
      Element *element = discardedElements;
      discardedElements = element->next;
      free(element);
    }
  unlockDiscardedElements();

  queueInitialized = 0;
}

Queue *
newQueue (ItemDeallocator *deallocateItem, ItemComparator *compareItems) {
  Queue *queue;

  if (!queueInitialized) {
    queueInitialized = 1;
    onProgramExit("queue", exitQueue, NULL);
  }

  if ((queue = malloc(sizeof(*queue)))) {
    queue->head = NULL;
    queue->size = 0;
    queue->data = NULL;
    queue->deallocateItem = deallocateItem;
    queue->compareItems = compareItems;
    return queue;
  } else {
    logMallocError();
  }

  return NULL;
}

void
deleteElements (Queue *queue) {
  while (queue->head) deleteElement(queue->head);
}

void
deallocateQueue (Queue *queue) {
  deleteElements(queue);
  free(queue);
}

static void
exitProgramQueue (void *data) {
  Queue **queue = data;

  if (*queue) {
    deallocateQueue(*queue);
    *queue = NULL;
  }
}

Queue *
getProgramQueue (
  Queue **queue, const char *name, int create,
  QueueCreator *createQueue, void *data
) {
  if (!*queue && create) {
    if ((*queue = createQueue(data))) {
      onProgramExit(name, exitProgramQueue, queue);
    }
  }

  return *queue;
}

int
getQueueSize (const Queue *queue) {
  return queue->size;
}

void *
getQueueData (const Queue *queue) {
  return queue->data;
}

void *
setQueueData (Queue *queue, void *data) {
  void *previous = queue->data;
  queue->data = data;
  return previous;
}

Element *
getQueueHead (const Queue *queue) {
  return queue->head;
}

Element *
getStackHead (const Queue *queue) {
  Element *head = queue->head;
  return head? head->previous: NULL;
}

static Element *
getElementByIndex (const Queue *queue, unsigned int index, int fromTail) {
  if (index < queue->size) {
    Element *element = queue->head;

    {
      int i = queue->size - 1 - index;

      if (i < index) {
        index = i;
        fromTail = !fromTail;
      }
    }

    if (fromTail) element = element->previous;

    while (index > 0) {
      element = fromTail? element->previous: element->next;
      index -= 1;
    }

    return element;
  }

  return 0;
}

Element *
getQueueElement (const Queue *queue, unsigned int index) {
  return getElementByIndex(queue, index, 0);
}

Element *
getStackElement (const Queue *queue, unsigned int index) {
  return getElementByIndex(queue, index, 1);
}

Element *
findElement (const Queue *queue, ItemTester *testItem, void *data) {
  if (queue->head) {
    Element *element = queue->head;

    do {
      if (testItem(element->item, data)) return element;
    } while ((element = element->next) != queue->head);
  }
  return NULL;
}

void *
findItem (const Queue *queue, ItemTester *testItem, void *data) {
  Element *element = findElement(queue, testItem, data);
  if (element) return element->item;
  return NULL;
}

static int
testElementHasItem (const void *item, void *data) {
  return item == data;
}

Element *
findElementWithItem (const Queue *queue, void *item) {
  return findElement(queue, testElementHasItem, item);
}

Element *
processQueue (Queue *queue, ItemProcessor *processItem, void *data) {
  Element *element = queue->head;
  while (element) {
    Element *next = element->next;
    if (next == queue->head) next = NULL;
    if (processItem(element->item, data)) return element;
    element = next;
  }
  return NULL;
}

static int
testItemAddress (const void *item, void *data) {
  return item == data;
}

int
deleteItem (Queue *queue, void *item) {
  Element *element = findElement(queue, testItemAddress, item);
  if (!element) return 0;

  element->item = NULL;
  deleteElement(element);
  return 1;
}
