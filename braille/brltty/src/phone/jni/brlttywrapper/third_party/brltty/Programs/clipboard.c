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
#include <ctype.h>

#include "log.h"
#include "clipboard.h"
#include "utf8.h"
#include "queue.h"
#include "lock.h"
#include "program.h"
#include "api_control.h"

typedef struct {
  wchar_t *characters;
  size_t length;
} HistoryEntry;

struct ClipboardObjectStruct {
  struct {
    wchar_t *characters;
    size_t size;
    size_t length;
  } buffer;

  struct {
    Queue *queue;
  } history;
};

const wchar_t *
getClipboardHistory (ClipboardObject *cpb, unsigned int index, size_t *length) {
  Element *element = getStackElement(cpb->history.queue, index);
  if (!element) return NULL;

  const HistoryEntry *entry = getElementItem(element);
  *length = entry->length;
  return entry->characters;
}

int
addClipboardHistory (ClipboardObject *cpb, const wchar_t *characters, size_t length) {
  if (!length) return 1;

  Queue *queue = cpb->history.queue;
  Element *element = getStackHead(queue);

  if (element) {
    const HistoryEntry *entry = getElementItem(element);

    if (length == entry->length) {
      if (wmemcmp(characters, entry->characters, length) == 0) {
        return 1;
      }
    }
  }

  {
    HistoryEntry *entry;

    if ((entry = malloc(sizeof(*entry)))) {
      if ((entry->characters = allocateCharacters(length))) {
        wmemcpy(entry->characters, characters, length);
        entry->length = length;

        if (enqueueItem(queue, entry)) {
          return 1;
        }

        free(entry->characters);
      } else {
        logMallocError();
      }

      free(entry);
    } else {
      logMallocError();
    }
  }

  return 0;
}

const wchar_t *
getClipboardContent (ClipboardObject *cpb, size_t *length) {
  *length = cpb->buffer.length;
  return cpb->buffer.characters;
}

char *
getClipboardContentUTF8 (ClipboardObject *cpb) {
  size_t length;
  const wchar_t *characters = getClipboardContent(cpb, &length);
  return getUtf8FromWchars(characters, length, NULL);
}

size_t
getClipboardContentLength (ClipboardObject *cpb) {
  return cpb->buffer.length;
}

int
truncateClipboardContent (ClipboardObject *cpb, size_t length) {
  if (length >= cpb->buffer.length) return 0;
  cpb->buffer.length = length;
  return 1;
}

int
clearClipboardContent (ClipboardObject *cpb) {
  size_t length;
  const wchar_t *characters = getClipboardContent(cpb, &length);

  if (!addClipboardHistory(cpb, characters, length)) return 0;
  truncateClipboardContent(cpb, 0);
  return 1;
}

int
appendClipboardContent (ClipboardObject *cpb, const wchar_t *characters, size_t length) {
  size_t newLength = cpb->buffer.length + length;

  if (newLength > cpb->buffer.size) {
    size_t newSize = newLength | 0XFF;
    wchar_t *newCharacters = allocateCharacters(newSize);

    if (!newCharacters) {
      logMallocError();
      return 0;
    }

    wmemcpy(newCharacters, cpb->buffer.characters, cpb->buffer.length);
    if (cpb->buffer.characters) free(cpb->buffer.characters);
    cpb->buffer.characters = newCharacters;
    cpb->buffer.size = newSize;
  }

  wmemcpy(&cpb->buffer.characters[cpb->buffer.length], characters, length);
  cpb->buffer.length += length;
  return 1;
}

int
setClipboardContent (ClipboardObject *cpb, const wchar_t *characters, size_t length) {
  int truncated = truncateClipboardContent(cpb, 0);
  int appended = appendClipboardContent(cpb, characters, length);
  return truncated || appended;
}

int
appendClipboardContentUTF8 (ClipboardObject *cpb, const char *text) {
  size_t length = strlen(text);
  wchar_t characters[length + 1];
  size_t count = makeWcharsFromUtf8(text, characters, ARRAY_COUNT(characters));
  return appendClipboardContent(cpb, characters, count);
}

int
setClipboardContentUTF8 (ClipboardObject *cpb, const char *text) {
  int truncated = truncateClipboardContent(cpb, 0);
  int appended = appendClipboardContentUTF8(cpb, text);
  return truncated || appended;
}

static void
deallocateClipboardHistoryEntry (void *item, void *data) {
  HistoryEntry *entry = item;
  if (entry->characters) free(entry->characters);
  free(entry);
}

ClipboardObject *
newClipboard (void) {
  ClipboardObject *cpb;

  if ((cpb = malloc(sizeof(*cpb)))) {
    memset(cpb, 0, sizeof(*cpb));

    cpb->buffer.characters = NULL;
    cpb->buffer.size = 0;
    cpb->buffer.length = 0;

    if ((cpb->history.queue = newQueue(deallocateClipboardHistoryEntry, NULL))) {
      return cpb;
    }
  } else {
    logMallocError();
  }

  return NULL;
}

void
destroyClipboard (ClipboardObject *cpb) {
  if (cpb->buffer.characters) free(cpb->buffer.characters);
  deallocateQueue(cpb->history.queue);
  free(cpb);
}

static LockDescriptor *
getMainClipboardLock (void) {
  static LockDescriptor *lock = NULL;
  return getLockDescriptor(&lock, "main-clipboard");
}

void
lockMainClipboard (void) {
  obtainExclusiveLock(getMainClipboardLock());
}

void
unlockMainClipboard (void) {
  releaseLock(getMainClipboardLock());
}

static void
exitMainClipboard (void *data) {
  ClipboardObject **clipboard = data;

  lockMainClipboard();
    destroyClipboard(*clipboard);
    *clipboard = NULL;
  unlockMainClipboard();
}

ClipboardObject *
getMainClipboard (void) {
  static ClipboardObject *clipboard = NULL;

  lockMainClipboard();
    if (!clipboard) {
      if ((clipboard = newClipboard())) {
        onProgramExit("main-clipboard", exitMainClipboard, &clipboard);
      }
    }
  unlockMainClipboard();

  return clipboard;
}

void
onMainClipboardUpdated (void) {
  api.updateParameter(BRLAPI_PARAM_CLIPBOARD_CONTENT, 0);
}

int
setMainClipboardContent (const char *content) {
  ClipboardObject *cpb = getMainClipboard();
  int updated;

  lockMainClipboard();
    updated = setClipboardContentUTF8(cpb, content);
  unlockMainClipboard();

  if (updated) onMainClipboardUpdated();
  return updated;
}

char *
getMainClipboardContent (void) {
  ClipboardObject *cpb = getMainClipboard();
  char *content;

  lockMainClipboard();
    content = getClipboardContentUTF8(cpb);
  unlockMainClipboard();

  return content;
}
