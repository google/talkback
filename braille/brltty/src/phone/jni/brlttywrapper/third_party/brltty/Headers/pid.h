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

#ifndef BRLTTY_INCLUDED_PID
#define BRLTTY_INCLUDED_PID

#ifdef __cplusplus
extern "C" {
#endif /* __cplusplus */

#if defined(__MSDOS__) || defined(GRUB_RUNTIME)
typedef int ProcessIdentifier;
#define PRIpid "d"
#define SCNpid "d"
#define MY_PROCESS_ID 1

#elif defined(__MINGW32__)
typedef DWORD ProcessIdentifier;
#define PRIpid "lu"
#define SCNpid "lu"

#else /* Unix */
typedef pid_t ProcessIdentifier;
#define PRIpid "d"
#define SCNpid "d"
#endif /* platform-dependent definitions */

extern ProcessIdentifier getProcessIdentifier (void);
extern int testProcessIdentifier (ProcessIdentifier pid);
extern int cancelProcess (ProcessIdentifier pid);

#ifdef __cplusplus
}
#endif /* __cplusplus */

#endif /* BRLTTY_INCLUDED_PID */
