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
#include <string.h>
#include <errno.h>
#include <limits.h>
#include <signal.h>

#include "embed.h"
#include "log.h"
#include "api_control.h"
#include "core.h"

static ProgramExitStatus
brlttyRun (void) {
  while (brlttyWait(INT_MAX));
  return PROG_EXIT_SUCCESS;
}

#ifdef __MINGW32__
#include "utf8.h"

static SERVICE_STATUS_HANDLE serviceStatusHandle;
static DWORD serviceState;
static ProgramExitStatus serviceExitStatus;

static BOOL
setServiceState (DWORD state, DWORD exitStatus, const char *name) {
  SERVICE_STATUS status = {
    .dwServiceType = SERVICE_WIN32_OWN_PROCESS | SERVICE_INTERACTIVE_PROCESS,
    .dwCurrentState = state,

    .dwWin32ExitCode = (exitStatus != PROG_EXIT_SUCCESS)? ERROR_SERVICE_SPECIFIC_ERROR: NO_ERROR,
    .dwServiceSpecificExitCode = exitStatus
  };

  switch (status.dwCurrentState) {
    default:
      status.dwControlsAccepted = SERVICE_ACCEPT_STOP | SERVICE_ACCEPT_PAUSE_CONTINUE;

    case SERVICE_START_PENDING:
    case SERVICE_STOP_PENDING:
    case SERVICE_STOPPED:
      break;
  }

  switch (status.dwCurrentState) {
    case SERVICE_START_PENDING:
    case SERVICE_PAUSE_PENDING:
    case SERVICE_CONTINUE_PENDING:
    case SERVICE_STOP_PENDING:
      status.dwWaitHint = 10000;
      status.dwCheckPoint = 0;

    default:
      break;
  }

  serviceState = state;
  if (SetServiceStatus(serviceStatusHandle, &status)) return 1;

  logWindowsSystemError(name);
  return 0;
}
#define SET_SERVICE_STATE(state,code) setServiceState(state, code, #state)

static void WINAPI
serviceControlHandler (DWORD code) {
  switch (code) {
    case SERVICE_CONTROL_STOP:
      SET_SERVICE_STATE(SERVICE_STOP_PENDING, PROG_EXIT_SUCCESS);
      raise(SIGTERM);
      break;

    case SERVICE_CONTROL_PAUSE:
      SET_SERVICE_STATE(SERVICE_PAUSE_PENDING, PROG_EXIT_SUCCESS);
      api.suspendDriver();
      SET_SERVICE_STATE(SERVICE_PAUSED, PROG_EXIT_SUCCESS);
      break;

    case SERVICE_CONTROL_CONTINUE:
      SET_SERVICE_STATE(SERVICE_CONTINUE_PENDING, PROG_EXIT_SUCCESS);
      if (api.resumeDriver()) {
        SET_SERVICE_STATE(SERVICE_RUNNING, PROG_EXIT_SUCCESS);
      } else {
        SET_SERVICE_STATE(SERVICE_PAUSED, PROG_EXIT_SUCCESS);
      }
      break;

    default:
      logMessage(LOG_WARNING, "unexpected service control code: %lu", code);
      break;
  }
}

static void
exitService (void) {
  SET_SERVICE_STATE(SERVICE_STOPPED, PROG_EXIT_SUCCESS);
}

static char **
getCommandLineArguments (DWORD *argc) {
  int count;
  LPWSTR *arguments = CommandLineToArgvW(GetCommandLineW(), &count);
  char **argv;

  if ((argv = malloc(ARRAY_SIZE(argv, count+1)))) {
    unsigned int index = 0;

    while (index < count) {
      const wchar_t *argument = arguments[index];

      if (!(argv[index] = getUtf8FromWchars(argument, wcslen(argument), NULL))) {
        logMallocError();
        break;
      }

      index += 1;
    }

    LocalFree(arguments);
    arguments = NULL;

    if (index == count) {
      argv[count] = NULL;
      *argc = count;
      return argv;
    }

    while (index > 0) free(argv[index-=1]);
    free(argv);
  } else {
    logMallocError();
  }

  return NULL;
}

static void WINAPI
serviceMain (DWORD argc, LPSTR *argv) {
  atexit(exitService);

  if ((argv = getCommandLineArguments(&argc))) {
    if ((serviceStatusHandle = RegisterServiceCtrlHandler("", serviceControlHandler))) {
      if ((SET_SERVICE_STATE(SERVICE_START_PENDING, PROG_EXIT_SUCCESS))) {
        if ((serviceExitStatus = brlttyConstruct(argc, argv)) == PROG_EXIT_SUCCESS) {
          if ((SET_SERVICE_STATE(SERVICE_RUNNING, PROG_EXIT_SUCCESS))) {
            serviceExitStatus = brlttyRun();
          } else {
            serviceExitStatus = PROG_EXIT_FATAL;
          }

          brlttyDestruct();
        } else if (serviceExitStatus == PROG_EXIT_FORCE) {
          serviceExitStatus = PROG_EXIT_SUCCESS;
        }

        SET_SERVICE_STATE(SERVICE_STOPPED, serviceExitStatus);
      }
    } else {
      logWindowsSystemError("RegisterServiceCtrlHandler");
    }
  }
}
#endif /* __MINGW32__ */

int
main (int argc, char *argv[]) {
#ifdef __MINGW32__
  {
    static SERVICE_TABLE_ENTRY serviceTable[] = {
      { .lpServiceName="", .lpServiceProc=serviceMain },
      {}
    };

    isWindowsService = 1;
    if (StartServiceCtrlDispatcher(serviceTable)) return serviceExitStatus;
    isWindowsService = 0;

    if (GetLastError() != ERROR_FAILED_SERVICE_CONTROLLER_CONNECT) {
      logWindowsSystemError("StartServiceCtrlDispatcher");
      return PROG_EXIT_FATAL;
    }
  }
#endif /* __MINGW32__ */

#ifdef INIT_PATH
#define INIT_NAME "init"

  if ((getpid() == 1) || strstr(argv[0], "linuxrc")) {
    fprintf(stderr, gettext("\"%s\" started as \"%s\"\n"), PACKAGE_NAME, argv[0]);
    fflush(stderr);

    switch (fork()) {
      case -1: /* failed */
        fprintf(stderr, gettext("fork of \"%s\" failed: %s\n"),
                PACKAGE_NAME, strerror(errno));
        fflush(stderr);

      default: /* parent */
        fprintf(stderr, gettext("executing \"%s\" (from \"%s\")\n"), INIT_NAME, INIT_PATH);
        fflush(stderr);

      executeInit:
        execv(INIT_PATH, argv);
        /* execv() shouldn't return */

        fprintf(stderr, gettext("execution of \"%s\" failed: %s\n"), INIT_NAME, strerror(errno));
        fflush(stderr);
        exit(1);

      case 0: { /* child */
        static char *arguments[] = {"brltty", "-E", "-n", "-e", "-linfo", NULL};
        argv = arguments;
        argc = ARRAY_COUNT(arguments) - 1;
        break;
      }
    }
  } else if (!strstr(argv[0], "brltty")) {
    /* 
     * If we are substituting the real init binary, then we may consider
     * when someone might want to call that binary even when pid != 1.
     * One example is /sbin/telinit which is a symlink to /sbin/init.
     */
    goto executeInit;
  }
#endif /* INIT_PATH */

#ifdef STDERR_PATH
  freopen(STDERR_PATH, "a", stderr);
#endif /* STDERR_PATH */

  {
    ProgramExitStatus exitStatus = brlttyConstruct(argc, argv);

    if (exitStatus == PROG_EXIT_SUCCESS) {
      exitStatus = brlttyRun();
      brlttyDestruct();
    } else if (exitStatus == PROG_EXIT_FORCE) {
      exitStatus = PROG_EXIT_SUCCESS;
    }

    return exitStatus;
  }
}
