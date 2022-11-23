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

#ifndef BRLTTY_INCLUDED_ACTIVITY
#define BRLTTY_INCLUDED_ACTIVITY

#ifdef __cplusplus
extern "C" {
#endif /* __cplusplus */

typedef int ActivityPrepareMethod (void *data);
typedef int ActivityStartMethod (void *data);
typedef void ActivityStopMethod (void *data);

typedef struct {
  const char *activityName;
  int retryInterval;
  int startTimeout;
  int stopTimeout;

  ActivityPrepareMethod *prepare;
  ActivityStartMethod *start;
  ActivityStopMethod *stop;
} ActivityMethods;

typedef struct ActivityObjectStruct ActivityObject;

extern ActivityObject *newActivity (const ActivityMethods *methods, void *data);
extern void destroyActivity (ActivityObject *activity);

extern void startActivity (ActivityObject *activity);
extern void stopActivity (ActivityObject *activity);

extern int isActivityStarted (const ActivityObject *activity);
extern int isActivityStopped (const ActivityObject *activity);

extern int awaitActivityStarted (ActivityObject *activity);
extern int awaitActivityStopped (ActivityObject *activity);

#ifdef __cplusplus
}
#endif /* __cplusplus */

#endif /* BRLTTY_INCLUDED_ACTIVITY */
