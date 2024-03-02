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

#ifndef BRLTTY_INCLUDED_GETTIME
#define BRLTTY_INCLUDED_GETTIME

#include "prologue.h"
#include <sys/time.h>

#ifdef HAVE_CLOCK_GETTIME
#include <time.h>
#endif /* HAVE_CLOCK_GETTIME */

#ifdef __cplusplus
extern "C" {
#endif /* __cplusplus */

static inline int
getRealTime (struct timeval *now) {
  int result;

#if defined(HAVE_CLOCK_GETTIME) && !defined(__MINGW32__)
  struct timespec time;
  result = clock_gettime(CLOCK_REALTIME, &time);
  now->tv_sec = time.tv_sec;
  now->tv_usec = time.tv_nsec / 1000;
#else /* getRealTime */
#pragma GCC diagnostic push
#pragma GCC diagnostic ignored "-Wdeprecated-declarations"
  result = gettimeofday(now, NULL);
#pragma GCC diagnostic pop
#endif /* getRealTime */

  return result;
}

#ifdef __cplusplus
}
#endif /* __cplusplus */

#endif /* BRLTTY_INCLUDED_GETTIME */
