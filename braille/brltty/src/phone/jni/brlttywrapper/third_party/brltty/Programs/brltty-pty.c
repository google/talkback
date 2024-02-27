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

/* not done yet:
 * parent: terminal type list
 * screen: resize
 */

#include "prologue.h"

#include <stdio.h>
#include <stdarg.h>
#include <string.h>
#include <errno.h>
#include <limits.h>
#include <sys/wait.h>

#include "log.h"
#include "cmdline.h"
#include "pty_object.h"
#include "pty_terminal.h"
#include "parse.h"
#include "file.h"
#include "async_handle.h"
#include "async_wait.h"
#include "async_io.h"
#include "async_signal.h"

static int opt_driverDirectives;
static int opt_showPath;
static char *opt_asUser;
static char *opt_asGroup;
static char *opt_workingDirectory;
static char *opt_homeDirectory;

static int opt_logInput;
static int opt_logOutput;
static int opt_logSequences;
static int opt_logUnexpected;

BEGIN_OPTION_TABLE(programOptions)
  { .word = "driver-directives",
    .letter = 'x',
    .setting.flag = &opt_driverDirectives,
    .description = strtext("write driver directives to standard error")
  },

  { .word = "show-path",
    .letter = 'p',
    .setting.flag = &opt_showPath,
    .description = strtext("show the absolute path to the pty slave")
  },

  { .word = "user",
    .letter = 'u',
    .argument = "user",
    .setting.string = &opt_asUser,
    .description = strtext("the name or number of the user to run as")
  },

  { .word = "group",
    .letter = 'g',
    .argument = "group",
    .setting.string = &opt_asGroup,
    .description = strtext("the name or number of the group to run as")
  },

  { .word = "working-directory",
    .letter = 'd',
    .argument = "path",
    .setting.string = &opt_workingDirectory,
    .description = strtext("the directory to change to")
  },

  { .word = "home-directory",
    .letter = 'D',
    .argument = "path",
    .setting.string = &opt_homeDirectory,
    .description = strtext("the home directory to use")
  },

  { .word = "log-input",
    .letter = 'I',
    .setting.flag = &opt_logInput,
    .description = strtext("log input written to the pty slave")
  },

  { .word = "log-output",
    .letter = 'O',
    .setting.flag = &opt_logOutput,
    .description = strtext("log output received from the pty slave that isn't an escape sequence or a special character")
  },

  { .word = "log-sequences",
    .letter = 'S',
    .setting.flag = &opt_logSequences,
    .description = strtext("log escape sequences and special characters received from the pty slave")
  },

  { .word = "log-unexpected",
    .letter = 'U',
    .setting.flag = &opt_logUnexpected,
    .description = strtext("log unexpected input/output")
  },
END_OPTION_TABLE(programOptions)

static void writeDriverDirective (const char *format, ...) PRINTF(1, 2);

static void
writeDriverDirective (const char *format, ...) {
  if (opt_driverDirectives) {
    va_list args;
    va_start(args, format);

    {
      FILE *stream = stderr;
      vfprintf(stream, format, args);
      fputc('\n', stream);
      fflush(stream);
    }

    va_end(args);
  }
}

static int
setEnvironmentString (const char *variable, const char *string) {
  int result = setenv(variable, string, 1);
  if (result != -1) return 1;

  logSystemError("setenv");
  return 0;
}

static int
setEnvironmentInteger (const char *variable, int integer) {
  char string[0X10];
  snprintf(string, sizeof(string), "%d", integer);
  return setEnvironmentString(variable, string);
}

static int
setEnvironmentVariables (void) {
  if (!setEnvironmentString("TERM_PROGRAM", programName)) return 0;
  if (!setEnvironmentString("TERM_PROGRAM_VERSION", PACKAGE_VERSION)) return 0;

  {
    static const char *const variables[] = {
      /* screen */ "STY", "WINDOW",
      /* tmux   */ "TMUX",
    };

    const char *const *variable = variables;
    const char *const *end = variable + ARRAY_COUNT(variables);

    while (variable < end) {
      if (unsetenv(*variable) == -1) {
        logSystemError("unsetenv");
      }

      variable += 1;
    }
  }

  {
    size_t width, height;

    if (getConsoleSize(&width, &height)) {
      if (!setEnvironmentInteger("COLUMNS", width)) return 0;
      if (!setEnvironmentInteger("LINES", height)) return 0;
    }
  }

  return setEnvironmentString("TERM", ptyGetTerminalType());
}

static int
prepareChild (PtyObject *pty) {
  setsid();
  ptyCloseMaster(pty);

  if (setEnvironmentVariables()) {
    int tty;
    if (!ptyOpenSlave(pty, &tty)) return 0;
    int keep = 0;

    for (int fd=0; fd<=2; fd+=1) {
      if (fd == tty) {
        keep = 1;
      } else {
        int result = dup2(tty, fd);

        if (result == -1) {
          logSystemError("dup2");
          return 0;
        }
      }
    }

    if (!keep) close(tty);
  }

  return 1;
}

static int
runChild (PtyObject *pty, char **command) {
  char *defaultCommand[2];

  if (!(command && *command)) {
    char *shell = getenv("SHELL");
    if (!(shell && *shell)) shell = "/bin/sh";

    defaultCommand[0] = shell;
    defaultCommand[1] = NULL;
    command = defaultCommand;
  }

  if (prepareChild(pty)) {
    int result = execvp(*command, command);

    if (result == -1) {
      switch (errno) {
        case ENOENT:
          logMessage(LOG_ERR, "%s: %s", gettext("command not found"), *command);
          return PROG_EXIT_SEMANTIC;

        default:
          logSystemError("execvp");
          break;
      }
    } else {
      logMessage(LOG_ERR, "unexpected return from execvp");
    }
  }

  return PROG_EXIT_FATAL;
}

static unsigned char parentIsQuitting;
static unsigned char childHasTerminated;
static unsigned char slaveHasBeenClosed;

static
ASYNC_CONDITION_TESTER(parentTerminationTester) {
  if (parentIsQuitting) return 1;
  return childHasTerminated && slaveHasBeenClosed;
}

static void
parentQuitMonitor (int signalNumber) {
  parentIsQuitting = 1;
}

static void
childTerminationMonitor (int signalNumber) {
  childHasTerminated = 1;
}

static int
installSignalHandlers (void) {
  if (!asyncHandleSignal(SIGTERM, parentQuitMonitor, NULL)) return 0;
  if (!asyncHandleSignal(SIGINT, parentQuitMonitor, NULL)) return 0;
  if (!asyncHandleSignal(SIGQUIT, parentQuitMonitor, NULL)) return 0;
  return asyncHandleSignal(SIGCHLD, childTerminationMonitor, NULL);
}

static
ASYNC_MONITOR_CALLBACK(standardInputMonitor) {
  PtyObject *pty = parameters->data;
  if (ptyProcessTerminalInput(pty)) return 1;

  parentIsQuitting = 1;
  return 0;
}

static
ASYNC_INPUT_CALLBACK(ptyInputHandler) {
  if (!(parameters->error || parameters->end)) {
    size_t length = parameters->length;

    if (!ptyProcessTerminalOutput(parameters->buffer, length)) {
      parentIsQuitting = 1;
    }

    return length;
  }

  slaveHasBeenClosed = 1;
  return 0;
}

static int
reapExitStatus (pid_t pid) {
  while (1) {
    int status;
    pid_t result = waitpid(pid, &status, 0);

    if (result == -1) {
      if (errno == EINTR) continue;
      logSystemError("waitpid");
      break;
    }

    if (WIFEXITED(status)) return WEXITSTATUS(status);
    if (WIFSIGNALED(status)) return 0X80 | WTERMSIG(status);

    #ifdef WCOREDUMP
    if (WCOREDUMP(status)) return 0X80 | WTERMSIG(status);
    #endif /* WCOREDUMP */

    #ifdef WIFSTOPPED
    if (WIFSTOPPED(status)) continue;
    #endif /* WIFSTOPPED */

    #ifdef WIFCONTINUED
    if (WIFCONTINUED(status)) continue;
    #endif /* WIFCONTINUED */
  }

  return PROG_EXIT_FATAL;
}

static int
runParent (PtyObject *pty, pid_t child) {
  int exitStatus = PROG_EXIT_FATAL;
  AsyncHandle ptyInputHandle;

  parentIsQuitting = 0;
  childHasTerminated = 0;
  slaveHasBeenClosed = 0;

  if (asyncReadFile(&ptyInputHandle, ptyGetMaster(pty), 1, ptyInputHandler, NULL)) {
    AsyncHandle standardInputHandle;

    if (asyncMonitorFileInput(&standardInputHandle, STDIN_FILENO, standardInputMonitor, pty)) {
      if (installSignalHandlers()) {
        if (!isatty(2)) {
          unsigned char level = LOG_NOTICE;
          ptySetTerminalLogLevel(level);
          ptySetLogLevel(pty, level);
        }

        if (ptyBeginTerminal(pty, opt_driverDirectives)) {
          writeDriverDirective("path %s", ptyGetPath(pty));

          asyncAwaitCondition(INT_MAX, parentTerminationTester, NULL);
          if (!parentIsQuitting) exitStatus = reapExitStatus(child);

          ptyEndTerminal();
        }
      }

      asyncCancelRequest(standardInputHandle);
    }

    asyncCancelRequest(ptyInputHandle);
  }

  return exitStatus;
}

int
main (int argc, char *argv[]) {
  int exitStatus = PROG_EXIT_FATAL;
  PtyObject *pty;

  {
    const CommandLineDescriptor descriptor = {
      .options = &programOptions,
      .applicationName = "brltty-pty",

      .usage = {
        .purpose = strtext("Run a shell or terminal manager within a pty (virtual terminal) and export its screen via a shared memory segment so that brltty can read it via its Terminal Emulator screen driver."),
        .parameters = "[command [arg ...]]",
      }
    };

    PROCESS_OPTIONS(descriptor, argc, argv);
  }

  ptySetLogTerminalInput(opt_logInput);
  ptySetLogTerminalOutput(opt_logOutput);
  ptySetLogTerminalSequences(opt_logSequences);
  ptySetLogUnexpectedTerminalIO(opt_logUnexpected);

  if (!isatty(STDIN_FILENO)) {
    logMessage(LOG_ERR, "%s", gettext("standard input isn't a terminal"));
    return PROG_EXIT_SEMANTIC;
  }

  if (!isatty(STDOUT_FILENO)) {
    logMessage(LOG_ERR, "%s", gettext("standard output isn't a terminal"));
    return PROG_EXIT_SEMANTIC;
  }

  {
    uid_t user = 0;
    gid_t group = 0;

    if (*opt_asUser) {
      if (!validateUser(&user, opt_asUser, &group)) {
        logMessage(LOG_ERR, "unknown user: %s", opt_asUser);
        return PROG_EXIT_SEMANTIC;
      }
    }

    if (*opt_asGroup) {
      if (!validateGroup(&group, opt_asGroup)) {
        logMessage(LOG_ERR, "unknown group: %s", opt_asGroup);
        return PROG_EXIT_SEMANTIC;
      }
    }

    if (group) {
      if (setregid(group, group) == -1) {
        logSystemError("setregid");
        return PROG_EXIT_FATAL;
      }
    }

    if (user) {
      if (setreuid(user, user) == -1) {
        logSystemError("setreuid");
        return PROG_EXIT_FATAL;
      }
    }
  }

  if (*opt_workingDirectory) {
    if (chdir(opt_workingDirectory) == -1) {
      logMessage(LOG_ERR, "can't change to directory: %s: %s", opt_workingDirectory, strerror(errno));
      return PROG_EXIT_FATAL;
    }
  }

  if (*opt_homeDirectory) {
    if (!setEnvironmentString("HOME", opt_homeDirectory)) {
      return PROG_EXIT_FATAL;
    }
  }

  if ((pty = ptyNewObject())) {
    ptySetLogInput(pty, opt_logInput);
    const char *ttyPath = ptyGetPath(pty);

    if (opt_showPath) {
      FILE *stream = stderr;
      fprintf(stream, "%s\n", ttyPath);
      fflush(stream);
    }

    pid_t child = fork();
    switch (child) {
      case -1:
        logSystemError("fork");
        break;

      case 0:
        _exit(runChild(pty, argv));

      default:
        exitStatus = runParent(pty, child);
        break;
    }

    ptyDestroyObject(pty);
  }

  return exitStatus;
}
