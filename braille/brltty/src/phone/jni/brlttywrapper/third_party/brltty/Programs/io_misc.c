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
#include <fcntl.h>

#include "io_misc.h"
#include "log.h"
#include "file.h"
#include "async_handle.h"
#include "async_wait.h"
#include "async_io.h"

typedef struct InputOutputMethodsStruct InputOutputMethods;

typedef struct {
  const InputOutputMethods *methods;

  union {
    FileDescriptor file;
    SocketDescriptor socket;
  } descriptor;
} InputOutputHandle;

typedef struct {
  unsigned ready:1;
} InputOutputMonitor;

typedef int MonitorInputOutputMethod (const InputOutputHandle *ioh, AsyncHandle *handle, InputOutputMonitor *iom);
typedef ssize_t ReadDataMethod (const InputOutputHandle *ioh, void *buffer, size_t size);
typedef ssize_t WriteDataMethod (const InputOutputHandle *ioh, const void *buffer, size_t size);

struct InputOutputMethodsStruct {
  MonitorInputOutputMethod *monitorInput;
  MonitorInputOutputMethod *monitorOutput;
  MonitorInputOutputMethod *monitorAlert;

  ReadDataMethod *readData;
  WriteDataMethod *writeData;
};

ASYNC_MONITOR_CALLBACK(setInputOutputMonitor) {
  InputOutputMonitor *iom = parameters->data;

  iom->ready = 1;
  return 0;
}

ASYNC_CONDITION_TESTER(testInputOutputMonitor) {
  InputOutputMonitor *iom = data;

  return iom->ready;
}

static int
awaitInputOutput (const InputOutputHandle *ioh, int timeout, MonitorInputOutputMethod *monitorInputOutput) {
  InputOutputMonitor iom = {
    .ready = 0
  };

  AsyncHandle monitor;

  if (monitorInputOutput(ioh, &monitor, &iom)) {
    asyncAwaitCondition(timeout, testInputOutputMonitor, &iom);
    asyncCancelRequest(monitor);
    if (iom.ready) return 1;

#ifdef ETIMEDOUT
    errno = ETIMEDOUT;
#else /* ETIMEDOUT */
    errno = EAGAIN;
#endif /* ETIMEDOUT */
  }

  return 0;
}

static int
awaitInput (const InputOutputHandle *ioh, int timeout) {
  return awaitInputOutput(ioh, timeout, ioh->methods->monitorInput);
}

static int
awaitOutput (const InputOutputHandle *ioh, int timeout) {
  return awaitInputOutput(ioh, timeout, ioh->methods->monitorOutput);
}

static int
awaitAlert (const InputOutputHandle *ioh, int timeout) {
  return awaitInputOutput(ioh, timeout, ioh->methods->monitorAlert);
}

static ssize_t
readData (
  const InputOutputHandle *ioh,
  void *buffer, size_t size,
  int initialTimeout, int subsequentTimeout
) {
  unsigned char *address = buffer;

#ifdef __MSDOS__
  int tried = 0;
  goto noInput;
#endif /* __MSDOS__ */

  while (size > 0) {
    ssize_t count = ioh->methods->readData(ioh, address, size);

#ifdef __MSDOS__
    tried = 1;
#endif /* __MSDOS__ */

    if (count == -1) {
      if (errno == EINTR) continue;
      if (errno == EAGAIN) goto noInput;

#ifdef EWOULDBLOCK
      if (errno == EWOULDBLOCK) goto noInput;
#endif /* EWOULDBLOCK */

      logSystemError("read");
      return count;
    }

    if (!count) {
      unsigned char *start;
      unsigned int offset;
      int timeout;

    noInput:
      start = buffer;
      offset = address - start;
      timeout = offset? subsequentTimeout: initialTimeout;

      if (timeout) {
        if (awaitInput(ioh, timeout)) continue;
      } else

#ifdef __MSDOS__
      if (!tried) {
        if (awaitInput(ioh, 0)) continue;
      } else
#endif /* __MSDOS__ */

      {
        errno = EAGAIN;
      }

      break;
    }

    address += count;
    size -= count;
  }

  {
    unsigned char *start = buffer;

    return address - start;
  }
}

static ssize_t
writeData (const InputOutputHandle *ioh, const void *buffer, size_t size) {
  const unsigned char *address = buffer;

canWrite:
  while (size > 0) {
    ssize_t count = ioh->methods->writeData(ioh, address, size);

    if (count == -1) {
      if (errno == EINTR) continue;
      if (errno == EAGAIN) goto noOutput;

#ifdef EWOULDBLOCK
      if (errno == EWOULDBLOCK) goto noOutput;
#endif /* EWOULDBLOCK */

      logSystemError("Write");
      return count;
    }

    if (!count) {
      errno = EAGAIN;

    noOutput:
      do {
        if (awaitOutput(ioh, 15000)) goto canWrite;
      } while (errno == EAGAIN);

      return -1;
    }

    address += count;
    size -= count;
  }

  {
    const unsigned char *start = buffer;
    return address - start;
  }
}

static int
monitorFileInput (const InputOutputHandle *ioh, AsyncHandle *handle, InputOutputMonitor *iom) {
  return asyncMonitorFileInput(handle, ioh->descriptor.file, setInputOutputMonitor, iom);
}

static int
monitorFileOutput (const InputOutputHandle *ioh, AsyncHandle *handle, InputOutputMonitor *iom) {
  return asyncMonitorFileOutput(handle, ioh->descriptor.file, setInputOutputMonitor, iom);
}

static int
monitorFileAlert (const InputOutputHandle *ioh, AsyncHandle *handle, InputOutputMonitor *iom) {
  return asyncMonitorFileAlert(handle, ioh->descriptor.file, setInputOutputMonitor, iom);
}

static ssize_t
readFileData (const InputOutputHandle *ioh, void *buffer, size_t size) {
  return readFileDescriptor(ioh->descriptor.file, buffer, size);
}

static ssize_t
writeFileData (const InputOutputHandle *ioh, const void *buffer, size_t size) {
  return writeFileDescriptor(ioh->descriptor.file, buffer, size);
}

static const InputOutputMethods fileMethods = {
  .monitorInput = monitorFileInput,
  .monitorOutput = monitorFileOutput,
  .monitorAlert = monitorFileAlert,

  .readData = readFileData,
  .writeData = writeFileData
};

static void
makeFileHandle (InputOutputHandle *ioh, FileDescriptor fileDescriptor) {
  ioh->methods = &fileMethods;
  ioh->descriptor.file = fileDescriptor;
}

void
closeFile (FileDescriptor *fileDescriptor) {
  if (*fileDescriptor != INVALID_FILE_DESCRIPTOR) {
    closeFileDescriptor(*fileDescriptor);
    *fileDescriptor = INVALID_FILE_DESCRIPTOR;
  }
}

int
awaitFileInput (FileDescriptor fileDescriptor, int timeout) {
  InputOutputHandle ioh;

  makeFileHandle(&ioh, fileDescriptor);
  return awaitInput(&ioh, timeout);
}

int
awaitFileOutput (FileDescriptor fileDescriptor, int timeout) {
  InputOutputHandle ioh;

  makeFileHandle(&ioh, fileDescriptor);
  return awaitOutput(&ioh, timeout);
}

int
awaitFileAlert (FileDescriptor fileDescriptor, int timeout) {
  InputOutputHandle ioh;

  makeFileHandle(&ioh, fileDescriptor);
  return awaitAlert(&ioh, timeout);
}

ssize_t
readFile (
  FileDescriptor fileDescriptor, void *buffer, size_t size,
  int initialTimeout, int subsequentTimeout
) {
  InputOutputHandle ioh;

  makeFileHandle(&ioh, fileDescriptor);
  return readData(&ioh, buffer, size, initialTimeout, subsequentTimeout);
}

ssize_t
writeFile (FileDescriptor fileDescriptor, const void *buffer, size_t size) {
  InputOutputHandle ioh;

  makeFileHandle(&ioh, fileDescriptor);
  return writeData(&ioh, buffer, size);
}

#ifdef GOT_SOCKETS
static int
monitorSocketInput (const InputOutputHandle *ioh, AsyncHandle *handle, InputOutputMonitor *iom) {
  return asyncMonitorSocketInput(handle, ioh->descriptor.socket, setInputOutputMonitor, iom);
}

static int
monitorSocketOutput (const InputOutputHandle *ioh, AsyncHandle *handle, InputOutputMonitor *iom) {
  return asyncMonitorSocketOutput(handle, ioh->descriptor.socket, setInputOutputMonitor, iom);
}

static int
monitorSocketAlert (const InputOutputHandle *ioh, AsyncHandle *handle, InputOutputMonitor *iom) {
  return asyncMonitorSocketAlert(handle, ioh->descriptor.socket, setInputOutputMonitor, iom);
}

static ssize_t
readSocketData (const InputOutputHandle *ioh, void *buffer, size_t size) {
  return readSocketDescriptor(ioh->descriptor.socket, buffer, size);
}

static ssize_t
writeSocketData (const InputOutputHandle *ioh, const void *buffer, size_t size) {
  return writeSocketDescriptor(ioh->descriptor.socket, buffer, size);
}

static const InputOutputMethods socketMethods = {
  .monitorInput = monitorSocketInput,
  .monitorOutput = monitorSocketOutput,
  .monitorAlert = monitorSocketAlert,

  .readData = readSocketData,
  .writeData = writeSocketData
};

static void
makeSocketHandle (InputOutputHandle *ioh, SocketDescriptor socketDescriptor) {
  ioh->methods = &socketMethods;
  ioh->descriptor.socket = socketDescriptor;
}

void
closeSocket (SocketDescriptor *socketDescriptor) {
  if (*socketDescriptor != INVALID_SOCKET_DESCRIPTOR) {
    closeSocketDescriptor(*socketDescriptor);
    *socketDescriptor = INVALID_SOCKET_DESCRIPTOR;
  }
}

int
awaitSocketInput (SocketDescriptor socketDescriptor, int timeout) {
  InputOutputHandle ioh;

  makeSocketHandle(&ioh, socketDescriptor);
  return awaitInput(&ioh, timeout);
}

int
awaitSocketOutput (SocketDescriptor socketDescriptor, int timeout) {
  InputOutputHandle ioh;

  makeSocketHandle(&ioh, socketDescriptor);
  return awaitOutput(&ioh, timeout);
}

int
awaitSocketAlert (SocketDescriptor socketDescriptor, int timeout) {
  InputOutputHandle ioh;

  makeSocketHandle(&ioh, socketDescriptor);
  return awaitAlert(&ioh, timeout);
}

ssize_t
readSocket (
  SocketDescriptor socketDescriptor, void *buffer, size_t size,
  int initialTimeout, int subsequentTimeout
) {
  InputOutputHandle ioh;

  makeSocketHandle(&ioh, socketDescriptor);
  return readData(&ioh, buffer, size, initialTimeout, subsequentTimeout);
}

ssize_t
writeSocket (SocketDescriptor socketDescriptor, const void *buffer, size_t size) {
  InputOutputHandle ioh;

  makeSocketHandle(&ioh, socketDescriptor);
  return writeData(&ioh, buffer, size);
}

int
connectSocket (
  SocketDescriptor socketDescriptor,
  const struct sockaddr *address,
  size_t addressLength,
  int timeout
) {
  int result = connect(socketDescriptor, address, addressLength);

  if (result == -1) {
#ifdef EINPROGRESS
    if (getSocketError() == EINPROGRESS) {
      if (awaitSocketOutput(socketDescriptor, timeout)) {
        int error;
        socklen_t length = sizeof(error);

        if (getsockopt(socketDescriptor, SOL_SOCKET, SO_ERROR, (void *)&error, &length) != -1) {
          if (!error) return 0;
          errno = error;
        }
      }
    }
#endif /* EINPROGRESS */
  }

  return result;
}

int
setSocketLingerTime (SocketDescriptor socketDescriptor, int seconds) {
  struct linger linger = {
    .l_onoff = 1,
    .l_linger = seconds
  };

  if (setsockopt(socketDescriptor, SOL_SOCKET, SO_LINGER, (const void *)&linger, sizeof(linger)) != -1) return 1;
  logSystemError("setsockopt[SO_LINGER]");
  return 0;
}

int
setSocketNoLinger (SocketDescriptor socketDescriptor) {
  return setSocketLingerTime(socketDescriptor, 0);
}

#else /* have sockets */
#warning sockets not supported on this platform
#endif /* GOT_SOCKETS */

int
changeOpenFlags (FileDescriptor fileDescriptor, int flagsToClear, int flagsToSet) {
#if defined(F_GETFL) && defined(F_SETFL)
  int flags;

  if ((flags = fcntl(fileDescriptor, F_GETFL)) != -1) {
    flags &= ~flagsToClear;
    flags |= flagsToSet;
    if (fcntl(fileDescriptor, F_SETFL, flags) != -1) {
      return 1;
    } else {
      logSystemError("F_SETFL");
    }
  } else {
    logSystemError("F_GETFL");
  }
#else /* defined(F_GETFL) && defined(F_SETFL) */
  errno = ENOSYS;
#endif /* defined(F_GETFL) && defined(F_SETFL) */

  return 0;
}

int
setOpenFlags (FileDescriptor fileDescriptor, int state, int flags) {
  if (state) {
    return changeOpenFlags(fileDescriptor, 0, flags);
  } else {
    return changeOpenFlags(fileDescriptor, flags, 0);
  }
}

int
setBlockingIo (FileDescriptor fileDescriptor, int state) {
#ifdef O_NONBLOCK
  if (setOpenFlags(fileDescriptor, !state, O_NONBLOCK)) return 1;
#else /* O_NONBLOCK */
  errno = ENOSYS;
#endif /* O_NONBLOCK */

  return 0;
}

int
setCloseOnExec (FileDescriptor fileDescriptor, int state) {
#if defined(F_SETFD) && defined(FD_CLOEXEC)
  if (fcntl(fileDescriptor, F_SETFD, (state? FD_CLOEXEC: 0)) != -1) return 1;
#else /* defined(F_SETFD) && defined(FD_CLOEXEC) */
  errno = ENOSYS;
#endif /* defined(F_SETFD) && defined(FD_CLOEXEC) */

  return 0;
}
