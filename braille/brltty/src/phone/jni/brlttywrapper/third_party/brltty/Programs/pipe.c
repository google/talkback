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

#include <string.h>
#include <errno.h>
#include <sys/stat.h>
#include <fcntl.h>

#include "log.h"
#include "pipe.h"
#include "file.h"
#include "async_io.h"

struct NamedPipeObjectStruct {
  NamedPipeInputCallback *callback;
  void *data;

  int (*createPipe) (NamedPipeObject *obj);
  int (*monitorPipe) (NamedPipeObject *obj);
  void (*resetPipe) (NamedPipeObject *obj);
  void (*releaseResources) (NamedPipeObject *obj);

  struct {
    char *path;
  } host;

  struct {
    FileDescriptor descriptor;
    AsyncHandle monitor;
  } input;

#if defined(__MINGW32__)
  struct {
    struct {
      AsyncHandle monitor;
      HANDLE event;
      OVERLAPPED overlapped;
    } connect;
  } windows;

#endif /*  */
};

static void
initializeHostPath (NamedPipeObject *obj) {
  obj->host.path = NULL;
}

static void
deallocateHostPath (NamedPipeObject *obj) {
  free(obj->host.path);
  initializeHostPath(obj);
}

static void
removePipe (NamedPipeObject *obj) {
  unlink(obj->host.path);
}

static void
initializeInputDescriptor (NamedPipeObject *obj) {
  obj->input.descriptor = INVALID_FILE_DESCRIPTOR;
}

static void
closeInputDescriptor (NamedPipeObject *obj) {
  closeFileDescriptor(obj->input.descriptor);
  initializeInputDescriptor(obj);
}

static void
initializeInputMonitor (NamedPipeObject *obj) {
  obj->input.monitor = NULL;
}

static void
stopInputMonitor (NamedPipeObject *obj) {
  asyncCancelRequest(obj->input.monitor);
  initializeInputMonitor(obj);
}

ASYNC_INPUT_CALLBACK(handleNamedPipeInput) {
  NamedPipeObject *obj = parameters->data;

  if (parameters->error) {
    logMessage(LOG_WARNING, "named pipe input error: %s: %s",
               obj->host.path, strerror(parameters->error));
  } else if (parameters->end) {
    logMessage(LOG_WARNING, "named pipe end-of-file: %s", obj->host.path);
  } else {
    const NamedPipeInputCallbackParameters input = {
      .buffer = parameters->buffer,
      .length = parameters->length,
      .data = obj->data
    };

    return obj->callback(&input);
  }

  asyncDiscardHandle(obj->input.monitor);
  initializeInputMonitor(obj);

  if (obj->resetPipe) obj->resetPipe(obj);
  obj->monitorPipe(obj);

  return 0;
}

static int
monitorInput (NamedPipeObject *obj) {
  if (!obj->input.monitor) {
    if (!asyncReadFile(&obj->input.monitor, obj->input.descriptor, 0X1000, handleNamedPipeInput, obj)) {
      return 0;
    }
  }

  return 1;
}

#if defined(__MINGW32__)
static int
createWindowsPipe (NamedPipeObject *obj) {
  obj->windows.connect.monitor = NULL;

  if ((obj->windows.connect.event = CreateEvent(NULL, TRUE, FALSE, NULL))) {
    if ((obj->input.descriptor = CreateNamedPipe(obj->host.path,
                                                 PIPE_ACCESS_INBOUND | FILE_FLAG_OVERLAPPED,
                                                 PIPE_TYPE_MESSAGE | PIPE_READMODE_MESSAGE,
                                                 1, 0, 0, 0, NULL)) != INVALID_HANDLE_VALUE) {
      logMessage(LOG_DEBUG, "named pipe created: %s: handle=%u",
                 obj->host.path, (unsigned int)obj->input.descriptor);

      return 1;
    } else {
      logWindowsSystemError("CreateNamedPipe");
    }

    CloseHandle(obj->windows.connect.event);
  } else {
    logWindowsSystemError("CreateEvent");
  }

  return 0;
}

static int
doWindowsPipeConnected (NamedPipeObject *obj) {
  return monitorInput(obj);
}

ASYNC_MONITOR_CALLBACK(handleWindowsPipeConnected) {
  NamedPipeObject *obj = parameters->data;

  asyncDiscardHandle(obj->windows.connect.monitor);
  obj->windows.connect.monitor = NULL;

  doWindowsPipeConnected(obj);
  return 0;
}

static int
monitorWindowsPipeConnect (NamedPipeObject *obj) {
  if (ResetEvent(obj->windows.connect.event)) {
    ZeroMemory(&obj->windows.connect.overlapped, sizeof(obj->windows.connect.overlapped));
    obj->windows.connect.overlapped.hEvent = obj->windows.connect.event;

    if (ConnectNamedPipe(obj->input.descriptor, &obj->windows.connect.overlapped)) {
      if (doWindowsPipeConnected(obj)) {
        return 1;
      }
    } else {
      DWORD error = GetLastError();

      if (error == ERROR_PIPE_CONNECTED) {
        if (doWindowsPipeConnected(obj)) {
          return 1;
        }
      } else if (error == ERROR_IO_PENDING) {
        if (asyncMonitorFileInput(&obj->windows.connect.monitor, obj->windows.connect.event, handleWindowsPipeConnected, obj)) {
          return 1;
        }
      } else {
        logWindowsError(error, "ConnectNamedPipe");
      }
    }
  } else {
    logWindowsSystemError("ResetEvent");
  }

  return 0;
}

static void
disconnectWindowsPipe (NamedPipeObject *obj) {
  if (!DisconnectNamedPipe(obj->input.descriptor)) {
    logWindowsSystemError("DisconnectNamedPipe");
  }
}

static void
releaseWindowsResources (NamedPipeObject *obj) {
  if (obj->windows.connect.monitor) {
    asyncCancelRequest(obj->windows.connect.monitor);
    obj->windows.connect.monitor = NULL;
  }

  if (obj->windows.connect.event) {
    CloseHandle(obj->windows.connect.event);
    obj->windows.connect.event = NULL;
  }
}

static void
setNamedPipeMethods (NamedPipeObject *obj) {
  obj->createPipe = createWindowsPipe;
  obj->monitorPipe = monitorWindowsPipeConnect;
  obj->resetPipe = disconnectWindowsPipe;
  obj->releaseResources = releaseWindowsResources;
}

#elif defined(S_ISFIFO)
static int
createFifo (NamedPipeObject *obj) {
  int result = mkfifo(obj->host.path, 0);

  if ((result == -1) && (errno == EEXIST)) {
    struct stat fifo;

    if (lstat(obj->host.path, &fifo) == -1) {
      logMessage(LOG_ERR, "cannot stat FIFO: %s: %s",
                 obj->host.path, strerror(errno));
    } else if (S_ISFIFO(fifo.st_mode)) {
      result = 0;
    }
  }

  if (result != -1) {
    if (chmod(obj->host.path, S_IRUSR|S_IWUSR|S_IWGRP|S_IWOTH) != -1) {
      // open read-write even though we only read to prevent an end-of-file condition
      if ((obj->input.descriptor = open(obj->host.path, O_RDWR|O_NONBLOCK)) != -1) {
        logMessage(LOG_DEBUG, "FIFO created: %s: fd=%d",
                   obj->host.path, obj->input.descriptor);

        return 1;
      } else {
        logMessage(LOG_ERR, "cannot open FIFO: %s: %s",
                   obj->host.path, strerror(errno));
      }
    } else {
      logMessage(LOG_ERR, "cannot set FIFO permissions: %s: %s",
                 obj->host.path, strerror(errno));
    }

    removePipe(obj);
  } else {
    logMessage(LOG_ERR, "cannot create FIFO: %s: %s",
               obj->host.path, strerror(errno));
  }

  return 0;
}

static void
setNamedPipeMethods (NamedPipeObject *obj) {
  obj->createPipe = createFifo;
}

#else /* named pipe functions */
#warning named pipes not supported on this platform

static void
setNamedPipeMethods (NamedPipeObject *obj) {
}
#endif /* named pipes functions */

NamedPipeObject *
newNamedPipeObject (const char *name, NamedPipeInputCallback *callback, void *data) {
  NamedPipeObject *obj;

  if ((obj = malloc(sizeof(*obj)))) {
    memset(obj, 0, sizeof(*obj));

    obj->callback = callback;
    obj->data = data;

    obj->createPipe = NULL;
    obj->monitorPipe = monitorInput;
    obj->resetPipe = NULL;
    obj->releaseResources = NULL;
    setNamedPipeMethods(obj);

    initializeHostPath(obj);
    initializeInputDescriptor(obj);
    initializeInputMonitor(obj);

    {
      const char *directory = getNamedPipeDirectory();

      obj->host.path = directory? makePath(directory, name): NULL;
    }

    if (obj->host.path) {
      if (!obj->createPipe) {
        logUnsupportedOperation("create named pipe");
      } else if (obj->createPipe(obj)) {
        if (!obj->monitorPipe) {
          logUnsupportedOperation("monitor named pipe");
        } else if (obj->monitorPipe(obj)) {
          return obj;
        }

        closeInputDescriptor(obj);
        removePipe(obj);
      }

      deallocateHostPath(obj);
    }

    free(obj);
  } else {
    logMallocError();
  }

  return NULL;
}

void
destroyNamedPipeObject (NamedPipeObject *obj) {
  logMessage(LOG_DEBUG, "destroying named pipe: %s", obj->host.path);
  if (obj->releaseResources) obj->releaseResources(obj);
  stopInputMonitor(obj);
  closeInputDescriptor(obj);
  removePipe(obj);
  deallocateHostPath(obj);
  free(obj);
}
