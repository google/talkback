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

#ifndef BRLTTY_INCLUDED_TIMING
#define BRLTTY_INCLUDED_TIMING

#ifdef __cplusplus
extern "C" {
#endif /* __cplusplus */

#define MSECS_PER_SEC  1000
#define USECS_PER_MSEC 1000
#define NSECS_PER_USEC 1000
#define USECS_PER_SEC  (USECS_PER_MSEC * MSECS_PER_SEC)
#define NSECS_PER_MSEC (NSECS_PER_USEC * USECS_PER_MSEC)
#define NSECS_PER_SEC  (NSECS_PER_USEC * USECS_PER_MSEC * MSECS_PER_SEC)

#define SECS_PER_MIN 60
#define MINS_PER_HR  60
#define HRS_PER_DAY  24
#define DAYS_PER_WK  7
#define SECS_PER_HR  (SECS_PER_MIN * MINS_PER_HR)
#define SECS_PER_DAY (SECS_PER_MIN * MINS_PER_HR * HRS_PER_DAY)
#define SECS_PER_WK  (SECS_PER_MIN * MINS_PER_HR * HRS_PER_DAY * DAYS_PER_WK)
#define MINS_PER_DAY (MINS_PER_HR * HRS_PER_DAY)
#define MINS_PER_WK  (MINS_PER_HR * HRS_PER_DAY * DAYS_PER_WK)
#define HRS_PER_WK   (HRS_PER_DAY * DAYS_PER_WK)

typedef struct {
  int32_t seconds;
  int32_t nanoseconds;
} TimeValue;

typedef struct {
  uint16_t year;
  uint8_t month;
  uint8_t day;
  uint8_t hour;
  uint8_t minute;
  uint8_t second;
  int32_t nanosecond;
} TimeComponents;

#define PRIsec PRIi32
#define PRInsec PRIi32

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
