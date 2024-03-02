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

#ifndef BRLTTY_INCLUDED_REPORT
#define BRLTTY_INCLUDED_REPORT

#include "brlapi_param.h"

#ifdef __cplusplus
extern "C" {
#endif /* __cplusplus */

typedef enum {
  REPORT_BRAILLE_DEVICE_ONLINE,
  REPORT_BRAILLE_DEVICE_OFFLINE,
  REPORT_BRAILLE_WINDOW_MOVED,
  REPORT_BRAILLE_WINDOW_UPDATED,
  REPORT_BRAILLE_KEY_EVENT,
  REPORT_API_PARAMETER_UPDATED,
} ReportIdentifier;

extern void report (ReportIdentifier identiier, const void *data);

extern void reportParameterUpdated (
  brlapi_param_t parameter, brlapi_param_subparam_t subparam
);

typedef struct {
  ReportIdentifier reportIdentifier;
  const void *reportData;
  void *listenerData;
} ReportListenerParameters;

#define REPORT_LISTENER(name) void name (const ReportListenerParameters *parameters)
typedef REPORT_LISTENER(ReportListener);
typedef struct ReportListenerInstanceStruct ReportListenerInstance;

extern ReportListenerInstance *registerReportListener (
  ReportIdentifier identifier,
  ReportListener *listener,
  void *data
);

extern void unregisterReportListener (ReportListenerInstance *rli);

typedef struct {
  struct {
    unsigned int column;
    unsigned int row;
  } screen;

  struct {
    unsigned int count;
  } text;
} BrailleWindowMovedReport;

typedef struct {
  const unsigned char *cells;
  unsigned int count;
} BrailleWindowUpdatedReport;

typedef struct {
  brlapi_param_t parameter;
  brlapi_param_subparam_t subparam;
} ApiParameterUpdatedReport;

#ifdef __cplusplus
}
#endif /* __cplusplus */

#endif /* BRLTTY_INCLUDED_REPORT */
