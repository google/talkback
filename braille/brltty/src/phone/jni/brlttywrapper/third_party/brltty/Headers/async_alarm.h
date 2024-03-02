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

#ifndef BRLTTY_INCLUDED_ASYNC_ALARM
#define BRLTTY_INCLUDED_ASYNC_ALARM

#include "async_types_alarm.h"
#include "async_types_handle.h"

#ifdef __cplusplus
extern "C" {
#endif /* __cplusplus */

extern int asyncNewAbsoluteAlarm (
  AsyncHandle *handle,
  const TimeValue *time,
  AsyncAlarmCallback *callback,
  void *data
);

extern int asyncNewRelativeAlarm (
  AsyncHandle *handle,
  int milliseconds,
  AsyncAlarmCallback *callback,
  void *data
);

extern int asyncResetAlarmTo (AsyncHandle handle, const TimeValue *time);
extern int asyncResetAlarmIn (AsyncHandle handle, int milliseconds);
extern int asyncResetAlarmInterval (AsyncHandle handle, int milliseconds);

#ifdef __cplusplus
}
#endif /* __cplusplus */

#endif /* BRLTTY_INCLUDED_ASYNC_ALARM */
