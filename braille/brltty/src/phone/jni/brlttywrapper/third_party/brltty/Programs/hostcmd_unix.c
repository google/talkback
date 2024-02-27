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

#include <stdio.h>
#include <errno.h>
#include <fcntl.h>
#include <signal.h>
#include <sys/wait.h>

#include "log.h"
#include "hostcmd_unix.h"
#include "hostcmd_internal.h"

static int *
getPipeDescriptor (HostCommandStream *hcs, unsigned int index) {
  return &hcs->package.pipe[index];
}

static int *
getInputDescriptor (HostCommandStream *hcs) {
  return getPipeDescriptor(hcs, 0);
}

static int *
getOutputDescriptor (HostCommandStream *hcs) {
  return getPipeDescriptor(hcs, 1);
}

static int *
getParentDescriptor (HostCommandStream *hcs) {
  return hcs->isInput? getOutputDescriptor(hcs): getInputDescriptor(hcs);
}

static int *
getChildDescriptor (HostCommandStream *hcs) {
  return hcs->isInput? getInputDescriptor(hcs): getOutputDescriptor(hcs);
}

int
constructHostCommandPackageData (HostCommandPackageData *pkg) {
  pkg->pipe[0] = pkg->pipe[1] = -1;
  return 1;
}

void
destructHostCommandPackageData (HostCommandPackageData *pkg) {
  {
    int *fileDescriptor = pkg->pipe;
    const int *end = fileDescriptor + 2;

    while (fileDescriptor < end) {
      if (*fileDescriptor != -1) {
        close(*fileDescriptor);
        *fileDescriptor = -1;
      }

      fileDescriptor += 1;
    }
  }
}

int
prepareHostCommandStream (HostCommandStream *hcs, void *data) {
  if (pipe(hcs->package.pipe) == -1) {
    logSystemError("pipe");
    return 0;
  }

  return 1;
}

static int
finishParentHostCommandStream (HostCommandStream *hcs, void *data) {
  int *local = getParentDescriptor(hcs);
  int *remote = getChildDescriptor(hcs);

  close(*remote);
  *remote = -1;

  if (!finishHostCommandStream(hcs, *local)) return 0;
  *local = -1;

  return 1;
}

static int
finishChildHostCommandStream (HostCommandStream *hcs, void *data) {
  int *local = getChildDescriptor(hcs);
  int *remote = getParentDescriptor(hcs);

  close(*remote);
  *remote = -1;

  if (close(hcs->fileDescriptor) == -1) {
    logSystemError("close");
    return 0;
  }

  if (fcntl(*local, F_DUPFD, hcs->fileDescriptor) == -1) {
    logSystemError("fcntl[F_DUPFD]");
    return 0;
  }

  close(*local);
  *local = -1;

  return 1;
}

int
runCommand (
  int *result,
  const char *const *command,
  HostCommandStream *streams,
  int asynchronous
) {
  int ok = 0;
  sigset_t newMask, oldMask;
  pid_t pid;

  sigemptyset(&newMask);
  sigaddset(&newMask, SIGCHLD);
  sigprocmask(SIG_BLOCK, &newMask, &oldMask);

  switch ((pid = fork())) {
    case -1: /* error */
      logSystemError("fork");
      break;

    case 0: /* child */
      sigprocmask(SIG_SETMASK, &oldMask, NULL);

      if (processHostCommandStreams(streams, finishChildHostCommandStream, NULL)) {
        execvp(command[0], (char *const*)command);

        switch (errno) {
          case ENOENT:
            logMessage(LOG_ERR, "command not found: %s", command[0]);
            break;

          default:
            logSystemError("execvp");
            break;
        }
      }

      _exit(1);

    default: /* parent */
      if (processHostCommandStreams(streams, finishParentHostCommandStream, NULL)) {
        ok = 1;

        if (asynchronous) {
          *result = 0;
        } else {
          int status;

          if (waitpid(pid, &status, 0) == -1) {
            logSystemError("waitpid");
          } else if (WIFEXITED(status)) {
            *result = WEXITSTATUS(status);
            logMessage(LOG_DEBUG, "host command exit status: %d: %s",
                       *result, command[0]);
          } else if (WIFSIGNALED(status)) {
            *result = WTERMSIG(status);
            logMessage(LOG_DEBUG, "host command termination signal: %d: %s",
                       *result, command[0]);
            *result += 0X80;
          } else if (WIFSTOPPED(status)) {
            *result = WSTOPSIG(status);
            logMessage(LOG_DEBUG, "host command stop signal: %d: %s",
                       *result, command[0]);
            *result += 0X80;
          } else {
            logMessage(LOG_DEBUG, "unknown host command status: 0X%X: %s",
                       status, command[0]);
          }
        }
      }

      break;
  }

  sigprocmask(SIG_SETMASK, &oldMask, NULL);
  return ok;
}
