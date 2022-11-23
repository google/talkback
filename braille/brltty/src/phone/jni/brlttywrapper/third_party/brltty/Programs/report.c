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
#include "report.h"
#include "queue.h"

struct ReportListenerInstanceStruct {
  Element *element;
  ReportListener *listener;
  void *data;
};

typedef struct {
  ReportIdentifier identifier;
  Queue *listeners;
} ReportEntry;

static ReportEntry **reportTable = NULL;
static unsigned int reportSize = 0;
static unsigned int reportCount = 0;

static int
findReportEntry (ReportIdentifier identifier, int *position) {
  int first = 0;
  int last = reportCount - 1;

  while (first <= last) {
    int current = (first + last) / 2;
    ReportEntry *report = reportTable[current];

    if (report->identifier < identifier) {
      first = current + 1;
    } else if (report->identifier > identifier) {
      last = current - 1;
    } else {
      *position = current;
      return 1;
    }
  }

  *position = first;
  return 0;
}

static ReportEntry *
getReportEntry (ReportIdentifier identifier, int add) {
  int position;
  int found = findReportEntry(identifier, &position);

  if (found) return reportTable[position];
  if (!add) return NULL;

  if (reportCount == reportSize) {
    unsigned int newSize = reportCount? (reportCount << 1): 1;
    ReportEntry **newTable = realloc(reportTable, ARRAY_SIZE(reportTable, newSize));

    if (!newTable) {
      logMallocError();
      return NULL;
    }

    reportTable = newTable;
    reportSize = newSize;
  }

  {
    ReportEntry **slot = &reportTable[position];
    ReportEntry *report = malloc(sizeof(*report));

    if (!report) {
      logMallocError();
      return NULL;
    }

    memset(report, 0, sizeof(*report));
    report->identifier = identifier;
    report->listeners = NULL;

    memmove(slot+1, slot, ((reportCount++ - position) * sizeof(*slot)));
    *slot = report;

    return report;
  }
}

static int
tellListener (void *item, void *data) {
  ReportListenerInstance *rli = item;
  ReportListenerParameters *parameters = data;

  parameters->listenerData = rli->data;
  rli->listener(parameters);

  return 0;
}

void
report (ReportIdentifier identifier, const void *data) {
  ReportEntry *report = getReportEntry(identifier, 0);

  if (report) {
    if (report->listeners) {
      ReportListenerParameters parameters = {
        .reportIdentifier = identifier,
        .reportData = data,
        .listenerData = NULL
      };

      processQueue(report->listeners, tellListener, &parameters);
    }
  }
}

static int
testListener (void *item, void *data) {
  ReportListenerInstance *rli = item;
  ReportListener *listener = data;

  return rli->listener == listener;
}

static Element *
findListenerElement (ReportEntry *report, ReportListener *listener) {
  return processQueue(report->listeners, testListener, listener);
}

static void
deallocateReportListenerInstance (void *item, void *data) {
  ReportListenerInstance *rli = item;
  ReportEntry *report = data;

  logSymbol(LOG_DEBUG, rli->listener,
            "report listener unregistered: %u", report->identifier);

  free(rli);
}

ReportListenerInstance *
registerReportListener (ReportIdentifier identifier, ReportListener *listener, void *data) {
  ReportEntry *report = getReportEntry(identifier, 1);

  if (report) {
    if (!report->listeners) {
      if (!(report->listeners = newQueue(deallocateReportListenerInstance, NULL))) {
        return NULL;
      }

      setQueueData(report->listeners, report);
    }

    if (findListenerElement(report, listener)) {
      logSymbol(LOG_WARNING, listener, "report listener already registered: %u", identifier);
    } else {
      ReportListenerInstance *rli;

      if ((rli = malloc(sizeof(*rli)))) {
        memset(rli, 0, sizeof(*rli));
        rli->listener = listener;
        rli->data = data;

        if ((rli->element = enqueueItem(report->listeners, rli))) {
          logSymbol(LOG_DEBUG, listener, "report listener registered: %u", identifier);
          return rli;
        }

        free(rli);
      } else {
        logMallocError();
      }
    }
  }

  return NULL;
}

void
unregisterReportListener (ReportListenerInstance *rli) {
  deleteElement(rli->element);
}
