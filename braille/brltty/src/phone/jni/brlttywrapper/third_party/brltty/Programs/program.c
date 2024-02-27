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
#include <ctype.h>
#include <errno.h>
#include <locale.h>
#include <fcntl.h>
#include <sys/stat.h>
#include <limits.h>

#include "program.h"
#include "messages.h"
#include "pgmpath.h"
#include "pid.h"
#include "log.h"
#include "file.h"
#include "parse.h"
#include "system.h"

const char standardStreamArgument[] = "-";
const char standardInputName[] = "<standard-input>";
const char standardOutputName[] = "<standard-output>";
const char standardErrorName[] = "<standard-error>";

const char *programPath;
const char *programName;

static char *
testProgram (const char *directory, const char *name) {
  char *path;

  if ((path = makePath(directory, name))) {
    if (testProgramPath(path)) return path;

    free(path);
  }

  return NULL;
}

static char *
findProgram (const char *name) {
  char *path = NULL;
  const char *string;

  if ((string = getenv("PATH"))) {
    int count;
    char **array;

    if ((array = splitString(string, ':', &count))) {
      for (unsigned int index=0; index<count; index+=1) {
        const char *directory = array[index];
        if (!*directory) directory = CURRENT_DIRECTORY_NAME;
        if ((path = testProgram(directory, name))) break;
      }

      deallocateStrings(array);
    }
  }

  return path;
}

void
beginProgram (int argumentCount, char **argumentVector) {
#if defined(GRUB_RUNTIME)

#else /* at exit */
  atexit(endProgram);
#endif /* at exit */

  setlocale(LC_ALL, "");
  initializeSystemObject();
  ensureAllMessagesProperties();

  if ((programPath = getProgramPath())) {
    registerProgramMemory("program-path", &programPath);
  } else {
    programPath = argumentVector[0];
  }

  if (!isExplicitPath(programPath)) {
    char *path = findProgram(programPath);
    if (!path) path = testProgram(CURRENT_DIRECTORY_NAME, programPath);
    if (path) programPath = path;
  }

  if (isExplicitPath(programPath)) {
#if defined(HAVE_REALPATH) && defined(PATH_MAX)
    if (!isAbsolutePath(programPath)) {
      char buffer[PATH_MAX];
      char *path = realpath(programPath, buffer);

      if (path) {
        char *realPath = strdup(path);

        if (realPath) {
          programPath = realPath;
        } else {
          logMallocError();
        }
      } else {
        logSystemError("realpath");
      }
    }
#endif /* defined(HAVE_REALPATH) && defined(PATH_MAX) */

    if (!isAbsolutePath(programPath)) {
      char *directory;

      if ((directory = getWorkingDirectory())) {
        char *path;
        if ((path = makePath(directory, programPath))) programPath = path;
        free(directory);
      }
    }
  }

  programName = locatePathName(programPath);
  pushLogPrefix(programName);
}

const char *
getProgramDirectory (void) {
  static const char *programDirectory = NULL;

  if (!programDirectory) {
    if ((programDirectory = getPathDirectory(programPath))) {
      logMessage(LOG_DEBUG, "program directory: %s", programDirectory);
      registerProgramMemory("program-directory", &programDirectory);
    } else {
      logMessage(LOG_WARNING, gettext("cannot determine program directory"));
      programDirectory = "";
    }
  }

  if (!*programDirectory) return NULL;
  return programDirectory;
}

int
fixInstallPath (char **path) {
  const char *programDirectory = getProgramDirectory();
  if (!programDirectory) programDirectory = CURRENT_DIRECTORY_NAME;

  const char *problem = strtext("cannot fix install path");
  char *newPath = makePath(programDirectory, *path);

  if (newPath) {
    if (changeStringSetting(path, newPath)) {
      if (isAbsolutePath(*path)) {
        problem = NULL;
      } else {
        problem = strtext("install path not absolute");
      }
    }

    free(newPath);
  }

  if (!problem) return 1;
  logMessage(LOG_WARNING, "%s: %s", gettext(problem), *path);
  return 0;
}

char *
makeProgramPath (const char *name) {
   const char *directory = getProgramDirectory();
   if (!directory) return NULL;
   return makePath(directory, name);
}

char *
makeCommandPath (const char *name) {
  char *path = NULL;
  char *directory = NULL;

  if (changeStringSetting(&directory, COMMANDS_DIRECTORY)) {
    if (fixInstallPath(&directory)) {
      path = makePath(directory, name);
    }
  }

  if (directory) free(directory);
  return path;
}

int
createPidFile (const char *path, ProcessIdentifier pid) {
#if defined(GRUB_RUNTIME)
  errno = EROFS;

#else /* create pid file */
  if (!pid) pid = getProcessIdentifier();

  if (path && *path) {
    if (!ensurePathDirectory(path)) return 0;

    typedef enum {PFS_ready, PFS_stale, PFS_clash, PFS_error} PidFileState;
    PidFileState state = PFS_error;

    lockUmask();
    int file = open(
      path, O_RDWR | O_CREAT, S_IRUSR | S_IWUSR
#ifdef S_IRGRP
                            | S_IRGRP
#endif /* S_IRGRP */
#ifdef S_IROTH
                            | S_IROTH
#endif /* S_IROTH */
    );
    unlockUmask();

    if (file != -1) {
      int locked = acquireFileLock(file, 1);

      if (locked || (errno == ENOSYS)) {
        char buffer[0X20];
        ssize_t length;

        if ((length = read(file, buffer, sizeof(buffer))) != -1) {
          ProcessIdentifier oldPid;
          char terminator;
          int count;

          if (length == sizeof(buffer)) length -= 1;
          buffer[length] = 0;
          count = sscanf(buffer, "%" SCNpid "%c", &oldPid, &terminator);
          state = PFS_stale;

          if ((count == 1) ||
              ((count == 2) && ((terminator == '\n') || (terminator == '\r')))) {
            if (oldPid == pid) {
              state = PFS_ready;
            } else if (testProcessIdentifier(oldPid)) {
              logMessage(LOG_ERR, "instance already running: PID=%" PRIpid, oldPid);
              state = PFS_clash;
            }
          }
        } else {
          logSystemError("read");
        }

        if (state == PFS_stale) {
          state = PFS_error;

          if (lseek(file, 0, SEEK_SET) != -1) {
            if (ftruncate(file, 0) != -1) {
              length = snprintf(buffer, sizeof(buffer), "%" PRIpid "\n", pid);

              if (write(file, buffer, length) != -1) {
                state = PFS_ready;
              } else {
                logSystemError("write");
              }
            } else {
              logSystemError("ftruncate");
            }
          } else {
            logSystemError("lseek");
          }
        }

        if (locked) releaseFileLock(file);
      }

      close(file);
    } else {
      logMessage(LOG_WARNING, "%s: %s: %s",
                 gettext("cannot open process identifier file"),
                 path, strerror(errno));
    }

    switch (state) {
      case PFS_ready:
        return 1;

      case PFS_clash:
        errno = EEXIST;
        break;

      case PFS_error:
        break;

      default:
        logMessage(LOG_WARNING, "unexpected PID file state: %u", state);
        break;
    }
  }
#endif /* create pid file */

  return 0;
}

int
cancelProgram (const char *pidFile) {
  int cancelled = 0;
  FILE *file;

  if ((file = fopen(pidFile, "r"))) {
    char buffer[0X100];
    const char *line;

    if ((line = fgets(buffer, sizeof(buffer), file))) {
      char *end;
      long int pid = strtol(line, &end, 10);

      if (!*end || isspace((unsigned char)*end)) {
        if (cancelProcess(pid)) cancelled = 1;
      }
    }

    fclose(file);
  } else {
    logMessage(LOG_ERR, "%s: %s: %s",
               gettext("pid file open error"),
               pidFile, strerror(errno));
  }

  return cancelled;
}

typedef struct ProgramExitEntryStruct ProgramExitEntry;
static ProgramExitEntry *programExitEntries = NULL;

struct ProgramExitEntryStruct {
  ProgramExitEntry *next;
  char *name;
  ProgramExitHandler *handler;
  void *data;
};

void
onProgramExit (const char *name, ProgramExitHandler *handler, void *data) {
  ProgramExitEntry *pxe;

  if ((pxe = malloc(sizeof(*pxe)))) {
    pxe->name = strdup(name);
    pxe->handler = handler;
    pxe->data = data;

    pxe->next = programExitEntries;
    programExitEntries = pxe;
    logMessage(LOG_DEBUG, "program exit event added: %s", name);
  } else {
    logMallocError();
  }
}

static void
exitProgramMemory (void *data) {
  char **pointer = data;

  if (*pointer) {
    free(*pointer);
    *pointer = NULL;
  }
}

void
registerProgramMemory (const char *name, void *pointer) {
  onProgramExit(name, exitProgramMemory, pointer);
}

void
endProgram (void) {
  logMessage(LOG_DEBUG, "stopping program components");

  while (programExitEntries) {
    ProgramExitEntry *pxe = programExitEntries;
    const char *name = pxe->name;

    programExitEntries = pxe->next;
    if (!name) name = "unknown";

    logMessage(LOG_DEBUG, "stopping program component: %s", name);
    pxe->handler(pxe->data);

    if (pxe->name) free(pxe->name);
    free(pxe);
  }

  logMessage(LOG_DEBUG, "stopped program components");
  popLogPrefix();
}
