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

#include <errno.h>

#include "pid.h"

#if defined(__MSDOS__) || defined(GRUB_RUNTIME)
ProcessIdentifier
getProcessIdentifier (void) {
  return MY_PROCESS_ID;
}

int
testProcessIdentifier (ProcessIdentifier pid) {
  return pid == MY_PROCESS_ID;
}

int
cancelProcess (ProcessIdentifier pid) {
  errno = ENOSYS;
  return 0;
}

#elif defined(__MINGW32__)
ProcessIdentifier
getProcessIdentifier (void) {
  return GetCurrentProcessId();
}

int
testProcessIdentifier (ProcessIdentifier pid) {
  HANDLE handle = OpenProcess(PROCESS_QUERY_INFORMATION, FALSE, pid);
  if (!handle) return 0;
  CloseHandle(handle);
  return 1;
}

int
cancelProcess (ProcessIdentifier pid) {
  errno = ENOSYS;
  return 0;
}

#else /* Unix */

#ifdef HAVE_SIGNAL_H
#include <signal.h>
#endif /* HAVE_SIGNAL_H */

ProcessIdentifier
getProcessIdentifier (void) {
  return getpid();
}

int
testProcessIdentifier (ProcessIdentifier pid) {
  return kill(pid, 0) != -1;
}

int
cancelProcess (ProcessIdentifier pid) {
  if (kill(pid, SIGTERM) != -1) return 1;
  return 0;
}

#endif /* pid support */
