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

#ifndef BRLTTY_INCLUDED_IO_MISC
#define BRLTTY_INCLUDED_IO_MISC

#include "get_sockets.h"

#ifdef __cplusplus
extern "C" {
#endif /* __cplusplus */

extern void closeFile (FileDescriptor *fileDescriptor);
extern int awaitFileInput (FileDescriptor fileDescriptor, int timeout);
extern int awaitFileOutput (FileDescriptor fileDescriptor, int timeout);
extern int awaitFileAlert (FileDescriptor fileDescriptor, int timeout);

extern ssize_t readFile (
  FileDescriptor fileDescriptor, void *buffer, size_t size,
  int initialTimeout, int subsequentTimeout
);

extern ssize_t writeFile (FileDescriptor fileDescriptor, const void *buffer, size_t size);

#ifdef GOT_SOCKETS
extern void closeSocket (SocketDescriptor *socketDescriptor);
extern int awaitSocketInput (SocketDescriptor socketDescriptor, int timeout);
extern int awaitSocketOutput (SocketDescriptor socketDescriptor, int timeout);
extern int awaitSocketAlert (SocketDescriptor socketDescriptor, int timeout);

extern ssize_t readSocket (
  SocketDescriptor socketDescriptor, void *buffer, size_t size,
  int initialTimeout, int subsequentTimeout
);

extern ssize_t writeSocket (SocketDescriptor socketDescriptor, const void *buffer, size_t size);

extern int connectSocket (
  SocketDescriptor socketDescriptor,
  const struct sockaddr *address,
  size_t addressLength,
  int timeout
);

extern int setSocketLingerTime (SocketDescriptor socketDescriptor, int seconds);
extern int setSocketNoLinger (SocketDescriptor socketDescriptor);
#endif /* GOT_SOCKETS */

extern int changeOpenFlags (FileDescriptor fileDescriptor, int flagsToClear, int flagsToSet);
extern int setOpenFlags (FileDescriptor fileDescriptor, int state, int flags);
extern int setBlockingIo (FileDescriptor fileDescriptor, int state);
extern int setCloseOnExec (FileDescriptor fileDescriptor, int state);

#ifdef __cplusplus
}
#endif /* __cplusplus */

#endif /* BRLTTY_INCLUDED_IO_MISC */
