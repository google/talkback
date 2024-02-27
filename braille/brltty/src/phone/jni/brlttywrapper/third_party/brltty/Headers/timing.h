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

#ifndef BRLTTY_INCLUDED_TIMING
#define BRLTTY_INCLUDED_TIMING

#include <timing_types.h>

#ifdef __cplusplus
extern "C" {
#endif /* __cplusplus */

extern void getCurrentTime (TimeValue *time);
extern void setCurrentTime (const TimeValue *time);

extern void makeTimeValue (TimeValue *value, const TimeComponents *components);
extern void expandTimeValue (const TimeValue *value, TimeComponents *components);
extern size_t formatSeconds (char *buffer, size_t size, const char *format, int32_t seconds);

extern void normalizeTimeValue (TimeValue *time);
extern void adjustTimeValue (TimeValue *time, int milliseconds);

extern int compareTimeValues (const TimeValue *first, const TimeValue *second);
extern long int millisecondsBetween (const TimeValue *from, const TimeValue *to);

extern long int millisecondsTillNextSecond (const TimeValue *reference);
extern long int millisecondsTillNextMinute (const TimeValue *reference);

extern void getMonotonicTime (TimeValue *now);
extern long int getMonotonicElapsed (const TimeValue *start);

typedef struct {
  TimeValue start;
  long int length;
} TimePeriod;

extern void startTimePeriod (TimePeriod *period, long int length);
extern void restartTimePeriod (TimePeriod *period);
extern int afterTimePeriod (const TimePeriod *period, long int *elapsed);

extern void approximateDelay (int milliseconds);		/* sleep for `msec' milliseconds */
extern void accurateDelay (const TimeValue *duration);

#ifdef __cplusplus
}
#endif /* __cplusplus */

#endif /* BRLTTY_INCLUDED_TIMING */
