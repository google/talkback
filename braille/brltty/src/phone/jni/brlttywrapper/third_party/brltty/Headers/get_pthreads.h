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

#ifndef BRLTTY_INCLUDED_GET_PTHREADS
#define BRLTTY_INCLUDED_GET_PTHREADS

#include "prologue.h"
#undef GOT_PTHREADS

#if defined(__MINGW32__)
#define GOT_PTHREADS
#include "win_pthread.h"

#elif defined(HAVE_POSIX_THREADS)
#define GOT_PTHREADS
#include <pthread.h>

#endif /* posix thread definitions */

#ifdef __cplusplus
extern "C" {
#endif /* __cplusplus */

#ifdef __cplusplus
}
#endif /* __cplusplus */

#endif /* BRLTTY_INCLUDED_GET_PTHREADS */
