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

#ifndef BRLTTY_INCLUDED_ASYNC_IO
#define BRLTTY_INCLUDED_ASYNC_IO

#include "async_types_io.h"
#include "async_types_handle.h"

#ifdef __cplusplus
extern "C" {
#endif /* __cplusplus */

extern int asyncMonitorFileInput (
  AsyncHandle *handle,
  FileDescriptor fileDescriptor,
  AsyncMonitorCallback *callback, void *data
);

extern int asyncMonitorSocketInput (
  AsyncHandle *handle,
  SocketDescriptor socketDescriptor,
  AsyncMonitorCallback *callback, void *data
);

extern int asyncMonitorFileOutput (
  AsyncHandle *handle,
  FileDescriptor fileDescriptor,
  AsyncMonitorCallback *callback, void *data
);

extern int asyncMonitorSocketOutput (
  AsyncHandle *handle,
  SocketDescriptor socketDescriptor,
  AsyncMonitorCallback *callback, void *data
);

extern int asyncMonitorFileAlert (
  AsyncHandle *handle,
  FileDescriptor fileDescriptor,
  AsyncMonitorCallback *callback, void *data
);

extern int asyncMonitorSocketAlert (
  AsyncHandle *handle,
  SocketDescriptor socketDescriptor,
  AsyncMonitorCallback *callback, void *data
);

extern int asyncReadFile (
  AsyncHandle *handle,
  FileDescriptor fileDescriptor,
  size_t size,
  AsyncInputCallback *callback, void *data
);

extern int asyncReadSocket (
  AsyncHandle *handle,
  SocketDescriptor socketDescriptor,
  size_t size,
  AsyncInputCallback *callback, void *data
);

extern int asyncWriteFile (
  AsyncHandle *handle,
  FileDescriptor fileDescriptor,
  const void *buffer, size_t size,
  AsyncOutputCallback *callback, void *data
);

extern int asyncWriteSocket (
  AsyncHandle *handle,
  SocketDescriptor socketDescriptor,
  const void *buffer, size_t size,
  AsyncOutputCallback *callback, void *data
);

#ifdef __cplusplus
}
#endif /* __cplusplus */

#endif /* BRLTTY_INCLUDED_ASYNC_IO */
