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

#include <io.h>
#include <fcntl.h>

#include "log.h"
#include "system_windows.h"
#include "hostcmd_windows.h"
#include "hostcmd_internal.h"

typedef struct {
  DWORD identifier;
  HANDLE *field;
} HandleEntry;

static void
closeHandle (HANDLE *handle) {
  if (*handle != INVALID_HANDLE_VALUE) {
    if (*handle) {
      CloseHandle(*handle);
    }

    *handle = INVALID_HANDLE_VALUE;
  }
}

static HANDLE *
getParentHandle (HostCommandStream *hcs) {
  return hcs->isInput? &hcs->package.outputHandle: &hcs->package.inputHandle;
}

static HANDLE *
getChildHandle (HostCommandStream *hcs) {
  return hcs->isInput? &hcs->package.inputHandle: &hcs->package.outputHandle;
}

int
constructHostCommandPackageData (HostCommandPackageData *pkg) {
  pkg->inputHandle = INVALID_HANDLE_VALUE;
  pkg->outputHandle = INVALID_HANDLE_VALUE;
  return 1;
}

void
destructHostCommandPackageData (HostCommandPackageData *pkg) {
  closeHandle(&pkg->inputHandle);
  closeHandle(&pkg->outputHandle);
}

int
prepareHostCommandStream (HostCommandStream *hcs, void *data) {
  SECURITY_ATTRIBUTES attributes;

  ZeroMemory(&attributes, sizeof(attributes));
  attributes.nLength = sizeof(attributes);
  attributes.bInheritHandle = TRUE;
  attributes.lpSecurityDescriptor = NULL;

  if (CreatePipe(&hcs->package.inputHandle, &hcs->package.outputHandle, &attributes, 0)) {
    if (SetHandleInformation(*getParentHandle(hcs), HANDLE_FLAG_INHERIT, 0)) {
      return 1;
    } else {
      logWindowsSystemError("SetHandleInformation");
    }
  } else {
    logWindowsSystemError("CreatePipe");
  }

  return 0;
}

typedef struct {
  const HandleEntry *const handleTable;
} SetChildHandleData;

static int
setChildHandle (HostCommandStream *hcs, void *data) {
  SetChildHandleData *sch = data;

  *sch->handleTable[hcs->fileDescriptor].field = *getChildHandle(hcs);
  return 1;
}

static int
finishParentHostCommandStream (HostCommandStream *hcs, void *data) {
  {
    HANDLE *handle = getParentHandle(hcs);
    int mode = hcs->isInput? O_WRONLY: O_RDONLY;
    int fileDescriptor;

    if ((fileDescriptor = _open_osfhandle((intptr_t)*handle, mode)) == -1) {
      logSystemError("_open_osfhandle");
      return 0;
    }
    *handle = INVALID_HANDLE_VALUE;

    if (!finishHostCommandStream(hcs, fileDescriptor)) {
      _close(fileDescriptor);
      return 0;
    }
  }

  closeHandle(getChildHandle(hcs));
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
  char *line = makeWindowsCommandLine(command);

  if (line) {
    STARTUPINFO startup;

    const HandleEntry handleTable[] = {
      [0] = {.identifier=STD_INPUT_HANDLE , .field=&startup.hStdInput },
      [1] = {.identifier=STD_OUTPUT_HANDLE, .field=&startup.hStdOutput},
      [2] = {.identifier=STD_ERROR_HANDLE , .field=&startup.hStdError },
    };

    const unsigned int handleCount = ARRAY_COUNT(handleTable);
    const HandleEntry *const handleEnd = handleTable + handleCount;

    SetChildHandleData sch = {
      .handleTable = handleTable
    };

    logMessage(LOG_DEBUG, "host command: %s", line);

    ZeroMemory(&startup, sizeof(startup));
    startup.cb = sizeof(startup);
    startup.dwFlags = STARTF_USESTDHANDLES;

    {
      const HandleEntry *hdl = handleTable;

      while (hdl < handleEnd) {
        if ((*hdl->field = GetStdHandle(hdl->identifier)) == INVALID_HANDLE_VALUE) {
          logWindowsSystemError("GetStdHandle");
          return 0;
        }

        hdl += 1;
      }
    }

    if (processHostCommandStreams(streams, setChildHandle, &sch)) {
      PROCESS_INFORMATION info;

      ZeroMemory(&info, sizeof(info));

      if (CreateProcess(NULL, line, NULL, NULL, TRUE,
                        CREATE_NEW_PROCESS_GROUP,
                        NULL, NULL, &startup, &info)) {
        if (processHostCommandStreams(streams, finishParentHostCommandStream, NULL)) {
          ok = 1;

          if (asynchronous) {
            *result = 0;
          } else {
            DWORD waitResult;

            *result = 0XFF;

            while ((waitResult = WaitForSingleObject(info.hProcess, INFINITE)) == WAIT_TIMEOUT);

            if (waitResult == WAIT_OBJECT_0) {
              DWORD exitCode;

              if (GetExitCodeProcess(info.hProcess, &exitCode)) {
                *result = exitCode;
              } else {
                logWindowsSystemError("GetExitCodeProcess");
              }
            } else {
              logWindowsSystemError("WaitForSingleObject");
            }
          }
        }

        CloseHandle(info.hProcess);
        CloseHandle(info.hThread);
      } else {
        logWindowsSystemError("CreateProcess");
      }
    }

    free(line);
  } else {
    logMallocError();
  }

  return ok;
}
