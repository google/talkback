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

#include <errno.h>

WIN_ERRNO_STORAGE_CLASS int
win_toErrno (DWORD error) {
  switch (error) {
    case NO_ERROR: return 0;
    case ERROR_INVALID_OPERATION: return EPERM;
    case ERROR_FILE_NOT_FOUND: return ENOENT;
    case ERROR_FILE_EXISTS: return EEXIST;
    case ERROR_BAD_EXE_FORMAT: return ENOEXEC;
    case ERROR_INVALID_HANDLE: return EBADF;
    case ERROR_WAIT_NO_CHILDREN: return ECHILD;
    case ERROR_NO_SYSTEM_RESOURCES: return EAGAIN;
    case ERROR_NOT_ENOUGH_MEMORY: return ENOMEM;
    case ERROR_OUTOFMEMORY: return ENOMEM;
    case ERROR_ACCESS_DENIED: return EACCES;
    case ERROR_INVALID_ADDRESS: return EFAULT;
    case ERROR_BAD_ARGUMENTS: return EINVAL;
    case ERROR_TOO_MANY_OPEN_FILES: return ENFILE;
    case ERROR_OPEN_FILES: return EBUSY;
    case ERROR_HANDLE_DISK_FULL: return ENOSPC;
    case ERROR_DISK_FULL: return ENOSPC;
    case ERROR_WRITE_PROTECT: return EROFS;
    case ERROR_BROKEN_PIPE: return EPIPE;
    case ERROR_FILENAME_EXCED_RANGE: return ENAMETOOLONG;
    case ERROR_NOT_SUPPORTED: return ENOSYS;
    case ERROR_DIR_NOT_EMPTY: return ENOTEMPTY;
    case ERROR_DEVICE_IN_USE: return EBUSY;
    case ERROR_DEVICE_REMOVED: return ENODEV;
    case ERROR_DEVICE_NOT_AVAILABLE: return ENODEV;
    case ERROR_DEVICE_NOT_CONNECTED: return ENODEV;
    case WSAEBADF: return EBADF;
    case WSAEACCES: return EACCES;
    case WSAEFAULT: return EFAULT;
    case WSAEINVAL: return EINVAL;
    case WSAEMFILE: return EMFILE;
    case WSAEWOULDBLOCK: return EAGAIN;
    case WSAENAMETOOLONG: return ENAMETOOLONG;
    case WSAENOTEMPTY: return ENOTEMPTY;
    default: return EIO;
  }
}
