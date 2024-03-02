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

#include <errno.h>
#include <time.h>

#if defined(HAVE_GETTIMEOFDAY) || defined(HAVE_SETTIMEOFDAY)
#include <sys/time.h>
#endif /* HAVE_(GET|SET)TIMEOFDAY */

#ifdef HAVE_SYS_POLL_H
#include <poll.h>
#endif /* HAVE_SYS_POLL_H */

#ifdef HAVE_SELECT
#ifdef HAVE_SYS_SELECT_H
#include <sys/select.h>
#else /* HAVE_SYS_SELECT_H */
#include <sys/time.h>
#endif /* HAVE_SYS_SELECT_H */
#endif /* HAVE_SELECT */

#include "log.h"
#include "timing.h"

#ifdef __MSDOS__
#include "system_msdos.h"
#endif /* __MSDOS__ */

#if !HAVE_DECL_LOCALTIME_R
static inline struct tm *
localtime_r (const time_t *timep, struct tm *result) {
  *result = *localtime(timep);
  return result;
}
#endif /* HAVE_DECL_LOCALTIME_R */

void
getCurrentTime (TimeValue *now) {
  now->seconds = 0;
  now->nanoseconds = 0;

#if defined(GRUB_RUNTIME)
  static time_t baseSeconds = 0;
  static uint64_t baseMilliseconds;

  if (!baseSeconds) {
    baseSeconds = time(NULL);
    baseMilliseconds = grub_get_time_ms();
  }

  {
    uint64_t milliseconds = grub_get_time_ms() - baseMilliseconds;

    now->seconds = baseSeconds + (milliseconds / MSECS_PER_SEC);
    now->nanoseconds = (milliseconds % MSECS_PER_SEC) * NSECS_PER_MSEC;
  }

#elif defined(HAVE_CLOCK_GETTIME) && defined(CLOCK_REALTIME) && !defined(__MINGW32__)
  struct timespec ts;

  if (clock_gettime(CLOCK_REALTIME, &ts) != -1) {
    now->seconds = ts.tv_sec;
    now->nanoseconds = ts.tv_nsec;
  } else {
  //logSystemError("clock_gettime");
  }

#elif defined(HAVE_GETTIMEOFDAY)
  struct timeval tv;

  #pragma GCC diagnostic push
  #pragma GCC diagnostic ignored "-Wdeprecated-declarations"
  int result = gettimeofday(&tv, NULL);
  #pragma GCC diagnostic pop

  if (result != -1) {
    now->seconds = tv.tv_sec;
    now->nanoseconds = tv.tv_usec * NSECS_PER_USEC;
  } else {
  //logSystemError("gettimeofday");
  }

#elif defined(HAVE_TIME)
  now->seconds = time(NULL);

#else /* get current time */
#warning get current time not supported on this platform
#endif /* get current time */
}

void
setCurrentTime (const TimeValue *now) {
#if defined(HAVE_CLOCK_SETTIME) && defined(CLOCK_REALTIME)
  const struct timespec ts = {
    .tv_sec = now->seconds,
    .tv_nsec = now->nanoseconds
  };

  if (clock_settime(CLOCK_REALTIME, &ts) == -1) {
    logSystemError("clock_settime");
  }

#elif defined(HAVE_SETTIMEOFDAY)
  struct timeval tv = {
    .tv_sec = now->seconds,
    .tv_usec = now->nanoseconds / NSECS_PER_USEC
  };

  if (settimeofday(&tv, NULL) == -1) {
    logSystemError("settimeofday");
  }

#elif defined(__MINGW32__)
  TimeComponents components;
  expandTimeValue(now, &components);

  SYSTEMTIME time = {
    .wYear = components.year,
    .wMonth = components.month + 1,
    .wDay = components.day + 1,
    .wHour = components.hour,
    .wMinute = components.minute,
    .wSecond = components.second,
    .wMilliseconds = now->nanoseconds / NSECS_PER_MSEC
  };

  if (!SetLocalTime(&time)) {
    logWindowsSystemError("SetLocalTime");
  }

#elif defined(HAVE_STIME)
  const time_t seconds = now->seconds;

  if (stime(&seconds) == -1) {
    logSystemError("stime");
  }

#else /* set current time */
#warning set current time not supported on this platform
#endif /* get current time */
}

void
makeTimeValue (TimeValue *value, const TimeComponents *components) {
  value->nanoseconds = components->nanosecond;

#if defined(GRUB_RUNTIME)
  value->seconds = 0;

#else /* make seconds */
  struct tm time = {
    .tm_year = components->year - 1900,
    .tm_mon = components->month,
    .tm_mday = components->day + 1,
    .tm_hour = components->hour,
    .tm_min = components->minute,
    .tm_sec = components->second,
    .tm_isdst = -1
  };

  value->seconds = mktime(&time);
#endif /* make seconds */
}

void
expandTimeValue (const TimeValue *value, TimeComponents *components) {
  time_t seconds = value->seconds;
  components->nanosecond = value->nanoseconds;

  struct tm *time = &components->time;
  localtime_r(&seconds, time);

#if defined(GRUB_RUNTIME)
  components->year = time->tm.year;
  components->month = time->tm.month - 1;
  components->day = time->tm.day - 1;
  components->hour = time->tm.hour;
  components->minute = time->tm.minute;
  components->second = time->tm.second;

#else /* expand seconds */
  components->year = time->tm_year + 1900;
  components->month = time->tm_mon;
  components->day = time->tm_mday - 1;
  components->hour = time->tm_hour;
  components->minute = time->tm_min;
  components->second = time->tm_sec;
#endif /* expand seconds */
}

size_t
formatSeconds (char *buffer, size_t size, const char *format, int32_t seconds) {
  time_t time = seconds;
  struct tm description;

  localtime_r(&time, &description);
  return strftime(buffer, size, format, &description);
}

void
normalizeTimeValue (TimeValue *time) {
  while (time->nanoseconds < 0) {
    time->seconds -= 1;
    time->nanoseconds += NSECS_PER_SEC;
  }

  while (time->nanoseconds >= NSECS_PER_SEC) {
    time->seconds += 1;
    time->nanoseconds -= NSECS_PER_SEC;
  }
}

void
adjustTimeValue (TimeValue *time, int milliseconds) {
  TimeValue amount = {
    .seconds = milliseconds / MSECS_PER_SEC,
    .nanoseconds = (milliseconds % MSECS_PER_SEC) * NSECS_PER_MSEC
  };

  normalizeTimeValue(time);
  normalizeTimeValue(&amount);
  time->seconds += amount.seconds;
  time->nanoseconds += amount.nanoseconds;
  normalizeTimeValue(time);
}

int
compareTimeValues (const TimeValue *first, const TimeValue *second) {
  if (first->seconds < second->seconds) return -1;
  if (first->seconds > second->seconds) return 1;

  if (first->nanoseconds < second->nanoseconds) return -1;
  if (first->nanoseconds > second->nanoseconds) return 1;

  return 0;
}

long int
millisecondsBetween (const TimeValue *from, const TimeValue *to) {
  TimeValue elapsed = {
    .seconds = to->seconds - from->seconds,
    .nanoseconds = to->nanoseconds - from->nanoseconds
  };

  normalizeTimeValue(&elapsed);
  return ((long int)elapsed.seconds * MSECS_PER_SEC)
       + (elapsed.nanoseconds / NSECS_PER_MSEC);
}

long int
millisecondsTillNextSecond (const TimeValue *reference) {
  TimeValue time = *reference;

  time.nanoseconds = 0;
  time.seconds += 1;
  return millisecondsBetween(reference, &time);
}

long int
millisecondsTillNextMinute (const TimeValue *reference) {
  TimeValue time = *reference;
  int32_t *seconds = &time.seconds;

  time.nanoseconds = 0;
  *seconds /= SECS_PER_MIN;
  *seconds += 1;
  *seconds *= SECS_PER_MIN;
  return millisecondsBetween(reference, &time);
}

void
getMonotonicTime (TimeValue *now) {
#if defined(GRUB_RUNTIME)
  grub_uint64_t milliseconds = grub_get_time_ms();
  now->seconds = milliseconds / MSECS_PER_SEC;
  now->nanoseconds = (milliseconds % MSECS_PER_SEC) * NSECS_PER_MSEC;

#elif defined(CLOCK_REALTIME)
  static const clockid_t clocks[] = {
#ifdef CLOCK_MONOTONIC_RAW
    CLOCK_MONOTONIC_RAW,
#endif /* CLOCK_MONOTONIC_RAW */

#ifdef CLOCK_MONOTONIC_HR
    CLOCK_MONOTONIC_HR,
#endif /* CLOCK_MONOTONIC_HR */

#ifdef CLOCK_MONOTONIC
    CLOCK_MONOTONIC,
#endif /* CLOCK_MONOTONIC */

    CLOCK_REALTIME
  };

  static const clockid_t *clock = clocks;

  while (*clock != CLOCK_REALTIME) {
    struct timespec ts;

    if (clock_gettime(*clock, &ts) != -1) {
      now->seconds = ts.tv_sec;
      now->nanoseconds = ts.tv_nsec;
      return;
    }

    logMessage(LOG_WARNING, "clock not available: %u", (unsigned int)*clock);
    clock += 1;
  }
#endif /* get monotonic time */

  getCurrentTime(now);
}

long int
getMonotonicElapsed (const TimeValue *start) {
  TimeValue now;

  getMonotonicTime(&now);
  return millisecondsBetween(start, &now);
}

void
restartTimePeriod (TimePeriod *period) {
  getMonotonicTime(&period->start);
}

void
startTimePeriod (TimePeriod *period, long int length) {
  period->length = length;
  restartTimePeriod(period);
}

int
afterTimePeriod (const TimePeriod *period, long int *elapsed) {
  long int milliseconds = getMonotonicElapsed(&period->start);

  if (elapsed) *elapsed = milliseconds;
  return milliseconds >= period->length;
}

void
approximateDelay (int milliseconds) {
  if (milliseconds > 0) {
#if defined(__MINGW32__)
    Sleep(milliseconds);

#elif defined(__MSDOS__)
    msdosUSleep(milliseconds * USECS_PER_MSEC);

#elif defined (GRUB_RUNTIME)
    grub_millisleep(milliseconds);

#elif defined(HAVE_NANOSLEEP)
    const struct timespec timeout = {
      .tv_sec = milliseconds / MSECS_PER_SEC,
      .tv_nsec = (milliseconds % MSECS_PER_SEC) * NSECS_PER_MSEC
    };

    if (nanosleep(&timeout, NULL) == -1) {
      if (errno != EINTR) logSystemError("nanosleep");
    }

#elif defined(HAVE_SYS_POLL_H)
    if (poll(NULL, 0, milliseconds) == -1) {
      if (errno != EINTR) logSystemError("poll");
    }

#elif defined(HAVE_SELECT)
    struct timeval timeout = {
      .tv_sec = milliseconds / MSECS_PER_SEC,
      .tv_usec = (milliseconds % MSECS_PER_SEC) * USECS_PER_MSEC
    };

    if (select(0, NULL, NULL, NULL, &timeout) == -1) {
      if (errno != EINTR) logSystemError("select");
    }

#endif /* approximate delay */
  }
}

void
accurateDelay (const TimeValue *duration) {
  TimeValue delay = *duration;
  normalizeTimeValue(&delay);

  if ((delay.seconds > 0) || ((delay.seconds == 0) && (delay.nanoseconds > 0))) {
#if defined(HAVE_NANOSLEEP)
    const struct timespec timeout = {
      .tv_sec = delay.seconds,
      .tv_nsec = delay.nanoseconds
    };

    if (nanosleep(&timeout, NULL) == -1) {
      if (errno != EINTR) logSystemError("nanosleep");
    }

#elif defined(HAVE_SELECT)
    struct timeval timeout = {
      .tv_sec = delay.seconds,
      .tv_usec = (delay.nanoseconds + (NSECS_PER_USEC - 1)) / NSECS_PER_USEC
    };

    if (timeout.tv_usec == USECS_PER_SEC) {
      timeout.tv_sec += 1;
      timeout.tv_usec = 0;
    }

    if (select(0, NULL, NULL, NULL, &timeout) == -1) {
      if (errno != EINTR) logSystemError("select");
    }

#else /* accurate delay */
    approximateDelay((delay.seconds * MSECS_PER_SEC) + ((delay.nanoseconds + (NSECS_PER_MSEC - 1)) / NSECS_PER_MSEC));
#endif /* accurate delay */
  }
}
