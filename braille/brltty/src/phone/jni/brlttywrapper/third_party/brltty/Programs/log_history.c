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
#include "log_history.h"
#include "timing.h"

struct LogEntryStruct {
  struct LogEntryStruct *previous;
  TimeValue time;
  unsigned int count;
  unsigned noSquash:1;
  char text[0];
};

const LogEntry *
getPreviousLogEntry (const LogEntry *entry) {
  return entry->previous;
}

const char *
getLogEntryText (const LogEntry *entry) {
  return entry->text;
}

const TimeValue *
getLogEntryTime (const LogEntry *entry) {
  return &entry->time;
}

unsigned int
getLogEntryCount (const LogEntry *entry) {
  return entry->count;
}

int
pushLogEntry (LogEntry **head, const char *text, LogEntryPushOptions options) {
  int log = !(options & LPO_NOLOG);
  LogEntry *entry = NULL;

  if (options & LPO_SQUASH) {
    if ((entry = *head)) {
      if (!entry->noSquash && (strcmp(entry->text, text) == 0)) {
        entry->count += 1;
      } else {
        entry = NULL;
      }
    }
  }

  if (!entry) {
    const size_t size = sizeof(*entry) + strlen(text) + 1;

    if (!(entry = malloc(size))) {
      if (log) logMallocError();
      return 0;
    }

    memset(entry, 0, sizeof(*entry));
    entry->count = 1;
    strcpy(entry->text, text);

    entry->previous = *head;
    *head = entry;
  }

  getCurrentTime(&entry->time);
  return 1;
}

int
popLogEntry (LogEntry **head) {
  if (!*head) return 0;
  LogEntry *entry = *head;
  *head = entry->previous;
  free(entry);
  return 1;
}

static CriticalSectionLock logMessageLock = CRITICAL_SECTION_LOCK_INITIALIZER;

static void
lockLogMessages (void) {
  enterCriticalSection(&logMessageLock);
}

static void
unlockLogMessages (void) {
  leaveCriticalSection(&logMessageLock);
}

static LogEntry *logMessageStack = NULL;

const LogEntry *
getNewestLogMessage (int freeze) {
  lockLogMessages();
  LogEntry *message = logMessageStack;
  if (freeze && message) message->noSquash = 1;
  unlockLogMessages();
  return message;
}

void
pushLogMessage (const char *message) {
  lockLogMessages();
  pushLogEntry(&logMessageStack, message, (LPO_NOLOG | LPO_SQUASH));
  unlockLogMessages();
}
