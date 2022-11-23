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

#include <stdio.h>
#include <stdarg.h>
#include <string.h>
#include <ctype.h>
#include <errno.h>
#include <fcntl.h>
#include <sys/stat.h>

#ifdef HAVE_SYS_FILE_H
#include <sys/file.h>
#endif /* HAVE_SYS_FILE_H */

#include "parameters.h"
#include "log.h"
#include "strfmt.h"
#include "file.h"
#include "parse.h"
#include "async_wait.h"
#include "charset.h"
#include "program.h"


int
isPathDelimiter (const char character) {
  return character == FILE_PATH_DELIMITER;
}

int
isAbsolutePath (const char *path) {
  if (isPathDelimiter(path[0])) return 1;

#if defined(__MINGW32__) || defined(__MSDOS__)
  if (strchr("ABCDEFGHIJKLMNOPQRSTUVWXYZ", toupper(path[0])) && (path[1] == ':')) return 1;
#endif /* defined(__MINGW32__) || defined(__MSDOS__) */

  return 0;
}

static size_t
stripPathDelimiter (const char *path, size_t length) {
  while (length) {
    if (!isPathDelimiter(path[length-1])) break;
    length -= 1;
  }
  return length;
}

char *
getPathDirectory (const char *path) {
  size_t length = strlen(path);
  size_t end = stripPathDelimiter(path, length);

  if (end) {
    while (--end)
      if (isPathDelimiter(path[end-1]))
        break;

    if ((length = end))
      if ((end = stripPathDelimiter(path, length)))
        length = end;
  }

  if (!length) length = strlen(path = ".");
  {
    char *directory = malloc(length + 1);

    if (directory) {
      memcpy(directory, path, length);
      directory[length] = 0;
    } else {
      logMallocError();
    }

    return directory;
  }
}

const char *
locatePathName (const char *path) {
  const char *name = path + strlen(path);

  while (name != path) {
    if (isPathDelimiter(*--name)) {
      ++name;
      break;
    }
  }

  return name;
}

const char *
locatePathExtension (const char *path) {
  const char *name = locatePathName(path);
  const char *extension = strrchr(name, '.');

  if (extension && extension[1]) {
    const char *c = extension;

    while (c > name) {
      if (*--c != '.') {
        return extension;
      }
    }
  }

  return NULL;
}

int
isExplicitPath (const char *path) {
  return locatePathName(path) != path;
}

char *
joinPath (const char *const *components, unsigned int count) {
  const char *const *component = components + count;
  unsigned int size = (count * 2) - 1;
  const char *strings[size];
  unsigned int first = size;

  while (component != components) {
    const char *next = *--component;

    if (next && *next) {
      if ((first != size) && !isPathDelimiter(next[strlen(next)-1])) {
        strings[--first] = "/";
      }

      strings[--first] = next;
      if (isAbsolutePath(next)) break;
    }
  }

  return joinStrings(&strings[first], size-first);
}

char *
makePath (const char *directory, const char *file) {
  const char *const components[] = {directory, file};

  return joinPath(components, ARRAY_COUNT(components));
}

int
hasFileExtension (const char *path, const char *extension) {
  const char *tail = locatePathExtension(path);

  if (!tail) return 0;
  return strcmp(tail, extension) == 0;
}

char *
replaceFileExtension (const char *path, const char *extension) {
  const char *oldExtension = locatePathExtension(path);

  if (oldExtension) {
    size_t headLength = oldExtension - path;
    size_t extensionLength = strlen(extension);
    char *newPath = malloc(headLength + extensionLength + 1);

    if (newPath) {
      char *byte = newPath;

      byte = mempcpy(byte, path, headLength);
      byte = mempcpy(byte, extension, extensionLength);
      *byte = 0;

      return newPath;
    } else {
      logMallocError();
    }
  } else {
    logMessage(LOG_WARNING, "path has no extension: %s", path);
  }

  return NULL;
}

char *
ensureFileExtension (const char *path, const char *extension) {
  const char *strings[2];
  int count = 0;

  strings[count++] = path;
  if (extension && !locatePathExtension(path)) strings[count++] = extension;
  return joinStrings(strings, count);
}

char *
makeFilePath (const char *directory, const char *name, const char *extension) {
  char *path = NULL;
  char *file = ensureFileExtension(name, extension);

  if (file) {
    if (isExplicitPath(file)) return file;
    path = makePath(directory, file);
    free(file);
  }

  return path;
}

int
testPath (const char *path) {
#ifdef F_OK
  return access(path, F_OK) != -1;
#else /* F_OK */
  errno = ENOSYS;
  return 0;
#endif /* F_OK */
}

int
testFilePath (const char *path) {
#ifdef S_ISREG
  struct stat status;

  if (stat(path, &status) != -1) {
    if (S_ISREG(status.st_mode)) return 1;
    errno = EEXIST;
  }
#else /* S_ISREG */
  int result = open(path, O_RDONLY);

  if (result != -1) {
    close(result);
    return 1;
  }
#endif /* S_ISREG */

  return 0;
}

int
testProgramPath (const char *path) {
  if (!testFilePath(path)) return 0;

#if defined(__MINGW32__)
  {
    const char *extension = locatePathExtension(path);

    if (extension) {
      static char **extensions = NULL;

      if (!extensions) {
        const char *string = getenv("PATHEXT");

        {
          static char *noExtensions[] = {NULL};

          extensions = noExtensions;
        }

        if (string) {
          char **strings = splitString(string, ';', NULL);

          if (strings) extensions = strings;
        }
      }

      {
        char **x = extensions;

        while (*x) {
          if (strcasecmp(*x, extension) == 0) return 1;
          x += 1;
        }
      }
    }
  }

  return 0;

#elif defined(X_OK)
  return access(path, X_OK) != -1;

#else /* X_OK */
  errno = ENOSYS;
  return 0;
#endif /* X_OK */
}

int
testDirectoryPath (const char *path) {
#ifdef S_ISDIR
  struct stat status;

  if (stat(path, &status) != -1) {
    if (S_ISDIR(status.st_mode)) return 1;
    errno = EEXIST;
  }
#else /* S_ISDIR */
  errno = ENOSYS;
#endif /* S_ISDIR */

  return 0;
}

int
createDirectory (const char *path) {
#if defined(GRUB_RUNTIME)
  errno = EROFS;

#else /* make directory */
  if (mkdir(path
#ifndef __MINGW32__
           ,(S_IRWXU | S_IRGRP | S_IXGRP | S_IROTH | S_IXOTH)
#endif /* __MINGW32__ */
           ) != -1) return 1;
#endif /* make directory */

  logMessage(LOG_WARNING, "%s: %s: %s",
             gettext("cannot create directory"),
             path, strerror(errno));
  return 0;
}

int
ensureDirectory (const char *path) {
  if (testDirectoryPath(path)) return 1;

  if (errno == EEXIST) {
    logMessage(LOG_ERR, "not a directory: %s", path);
  } else if (errno != ENOENT) {
    logMessage(LOG_ERR, "cannot access directory: %s: %s", path, strerror(errno));
  } else {
    {
      char *directory = getPathDirectory(path);
      if (!directory) return 0;

      {
         int exists = ensureDirectory(directory);
         free(directory);
         if (!exists) return 0;
      }
    }

    if (createDirectory(path)) {
      logMessage(LOG_NOTICE, "directory created: %s", path);
      return 1;
    }
  }

  return 0;
}

static void
setDirectory (const char **variable, const char *directory) {
  *variable = directory;
}

static const char *
getDirectory (const char *const *variable) {
  if (*variable && **variable) {
    if (ensureDirectory(*variable)) {
      return *variable;
    }
  }

  return NULL;
}

static char *
makeDirectoryPath (const char *const *variable, const char *file) {
  const char *directory = getDirectory(variable);
  if (directory) return makePath(directory, file);
  return NULL;
}

static const char *updatableDirectory = NULL;

void
setUpdatableDirectory (const char *directory) {
  setDirectory(&updatableDirectory, directory);
}

const char *
getUpdatableDirectory (void) {
  return getDirectory(&updatableDirectory);
}

char *
makeUpdatablePath (const char *file) {
  return makeDirectoryPath(&updatableDirectory, file);
}

static const char *writableDirectory = NULL;

void
setWritableDirectory (const char *directory) {
  setDirectory(&writableDirectory, directory);
}

const char *
getWritableDirectory (void) {
  return getDirectory(&writableDirectory);
}

char *
makeWritablePath (const char *file) {
  return makeDirectoryPath(&writableDirectory, file);
}

char *
getWorkingDirectory (void) {
#if defined(GRUB_RUNTIME)
  errno = ENOSYS;
#else /* get working directory */
  size_t size = 0X80;
  char *buffer = NULL;

  while (1) {
    {
      char *newBuffer = realloc(buffer, size<<=1);

      if (!newBuffer) {
        logMallocError();
        break;
      }

      buffer = newBuffer;
    }

    if (getcwd(buffer, size)) return buffer;

    if (errno != ERANGE) {
      logSystemError("getcwd");
      break;
    }
  }

  if (buffer) free(buffer);
#endif /* get working directory */

  logMessage(LOG_WARNING, "%s: %s",
             gettext("cannot get working directory"),
             strerror(errno));
  return NULL;
}

int
setWorkingDirectory (const char *path) {
#if defined(GRUB_RUNTIME)
  errno = ENOSYS;
#else /* set working directory */
  if (chdir(path) != -1) return 1;
#endif /* set working directory */

  logMessage(LOG_WARNING, "%s: %s: %s",
             gettext("cannot set working directory"),
             path, strerror(errno));
  return 0;
}

char *
getHomeDirectory (void) {
#if defined(GRUB_RUNTIME)
#else /* get home directory */
  char *path = getenv("HOME");

  if (path && *path) {
    if ((path = strdup(path))) return path;
    logMallocError();
  }
#endif /* get home directory */

  return NULL;
}

static char *
makeOverridePath (const char *base, int xdg) {
  return makePath(base, (xdg? PACKAGE_TARNAME: ("." PACKAGE_TARNAME)));
}

static int
addOverridePath (char **paths, size_t *index, const char *base, int xdg) {
  char *path = makeOverridePath(base, xdg);

  if (!path) return 0;
  logMessage(LOG_INFO, "Override Directory: %s", path);
  paths[(*index)++] = path;
  return 1;
}

const char *const *
getAllOverrideDirectories (void) {
  static const char *const *overrideDirectories = NULL;

  if (!overrideDirectories) {
    const char *secondaryList = getenv("XDG_CONFIG_DIRS");
    int secondaryCount;
    char **secondaryBases = splitString(((secondaryList && *secondaryList)? secondaryList: "/etc/xdg"), ':', &secondaryCount);

    if (secondaryBases) {
      size_t count = 1 + secondaryCount + 1;
      char **paths;

      if ((paths = malloc(sizeof(*paths) * (count + 1)))) {
        size_t index = 0;

        {
          const char *primary = getenv("XDG_CONFIG_HOME");

          if (primary && *primary) {
            if (!addOverridePath(paths, &index, primary, 1)) goto done;
          } else {
            char *home = getHomeDirectory();

            if (home) {
              char *base = *home? makePath(home, ".config"): NULL;

              free(home);
              home = NULL;

              if (base) {
                int added = addOverridePath(paths, &index, base, 1);

                free(base);
                base = NULL;

                if (!added) goto done;
              }
            }
          }
        }

        if (!index) {
          char *primary = strdup("");

          if (!primary) {
            logMallocError();
            goto done;
          }

          paths[index++] = primary;
        }

        {
          char **base = secondaryBases;

          while (*base) {
            if (**base) {
              if (!addOverridePath(paths, &index, *base, 1)) {
                break;
              }
            } else {
              count -= 1;
            }

            base += 1;
          }

          if (*base) goto done;
        }

        {
          int added = 0;
          char *home = getHomeDirectory();

          if (home && *home) {
            if (addOverridePath(paths, &index, home, 0)) added = 1;
          } else {
            char *current = getWorkingDirectory();

            if (current) {
              if (addOverridePath(paths, &index, current, 0)) added = 1;
              free(current);
            }
          }

          if (home) free(home);
          if (!added) goto done;
        }

done:
        paths[index] = NULL;

        if (index == count) {
          overrideDirectories = (const char *const *)paths;
        } else {
          deallocateStrings(paths);
        }
      } else {
        logMallocError();
      }

      deallocateStrings(secondaryBases);
    }

    if (!overrideDirectories) logMessage(LOG_WARNING, "no override directories");
  }

  return overrideDirectories;
}

const char *
getPrimaryOverrideDirectory (void) {
  const char *const *directories = getAllOverrideDirectories();

  if (directories) {
    const char *directory = directories[0];

    if (directory && *directory) return directory;
  }

  logMessage(LOG_WARNING, "no primary override directory");
  return NULL;
}

#if defined(F_SETLK)
static int
modifyFileLock (int file, int action, short type) {
  struct flock lock;

  memset(&lock, 0, sizeof(lock));
  lock.l_type = type;
  lock.l_whence = SEEK_SET;
  lock.l_start = 0;
  lock.l_len = 0;

  do {
    if (fcntl(file, action, &lock) != -1) return 1;
  } while (errno == EINTR);

  if (errno == EACCES) errno = EAGAIN;
  if (errno != EAGAIN) logSystemError("fcntl[struct flock *]");
  return 0;
}

static int
lockFile (int file, int exclusive, int wait) {
  return modifyFileLock(file, (wait? F_SETLKW: F_SETLK), (exclusive? F_WRLCK: F_RDLCK));
}

int
acquireFileLock (int file, int exclusive) {
  return lockFile(file, exclusive, 1);
}

int
attemptFileLock (int file, int exclusive) {
  return lockFile(file, exclusive, 0);
}

int
releaseFileLock (int file) {
  return modifyFileLock(file, F_SETLK, F_UNLCK);
}

#elif defined(LOCK_EX)
static int
modifyFileLock (int file, int operation) {
  do {
    if (flock(file, operation) != -1) return 1;
  } while (errno == EINTR);

#ifdef EWOULDBLOCK
  if (errno == EWOULDBLOCK) errno = EAGAIN;
#endif /* EWOULDBLOCK */

  if (errno == EACCES) errno = EAGAIN;
  if (errno != EAGAIN) logSystemError("flock");
  return 0;
}

int
acquireFileLock (int file, int exclusive) {
  return modifyFileLock(file, (exclusive? LOCK_EX: LOCK_SH));
}

int
attemptFileLock (int file, int exclusive) {
  return modifyFileLock(file, ((exclusive? LOCK_EX: LOCK_SH) | LOCK_NB));
}

int
releaseFileLock (int file) {
  return modifyFileLock(file, LOCK_UN);
}

#elif defined(F_LOCK)
static int
modifyRegionLock (int file, int command, off_t length) {
  do {
    if (lockf(file, command, length) != -1) return 1;
  } while (errno == EINTR);

  if (errno == EACCES) errno = EAGAIN;
  if (errno != EAGAIN) logSystemError("lockf");
  return 0;
}

static int
modifyFileLock (int file, int command) {
  off_t offset;

  if ((offset = lseek(file, 0, SEEK_CUR)) == -1) {
    logSystemError("lseek");
  } else if (modifyRegionLock(file, command, 0)) {
    if (!offset) return 1;
    if (modifyRegionLock(file, command, -offset)) return 1;
  }

  return 0;
}

int
acquireFileLock (int file, int exclusive) {
  return modifyFileLock(file, F_LOCK);
}

int
attemptFileLock (int file, int exclusive) {
  return modifyFileLock(file, F_TLOCK);
}

int
releaseFileLock (int file) {
  return modifyFileLock(file, F_ULOCK);
}

#elif defined(__MINGW32__)
#include <io.h>
#include <sys/locking.h>
#include <limits.h>

static int
modifyFileLock (int file, int mode) {
  int ok = 0;
  off_t offset;

  if ((offset = lseek(file, 0, SEEK_CUR)) != -1) {
    if (lseek(file, 0, SEEK_SET) != -1) {
      int wait;

      if (mode == _LK_LOCK) {
        mode = _LK_NBLCK;
        wait = 1;
      } else if (mode == _LK_RLCK) {
        mode = _LK_NBRLCK;
        wait = 1;
      } else {
        wait = 0;
      }

      while (1) {
        if (_locking(file, mode, LONG_MAX) != -1) {
          ok = 1;
          break;
        }

        if (errno != EACCES) {
          logSystemError("_locking");
          break;
        }

        if (!wait) break;
        asyncWait(WINDOWS_FILE_LOCK_RETRY_INTERVAL);
      }

      if (lseek(file, offset, SEEK_SET) == -1) {
        logSystemError("lseek");
        ok = 0;
      }
    } else {
      logSystemError("lseek");
    }
  } else {
    logSystemError("lseek");
  }

  return ok;
}

int
acquireFileLock (int file, int exclusive) {
  return modifyFileLock(file, (exclusive? _LK_LOCK: _LK_RLCK));
}

int
attemptFileLock (int file, int exclusive) {
  return modifyFileLock(file, (exclusive? _LK_NBLCK: _LK_NBRLCK));
}

int
releaseFileLock (int file) {
  return modifyFileLock(file, _LK_UNLCK);
}

#else /* file locking */
#warning file lock support not available on this platform

int
acquireFileLock (int file, int exclusive) {
  logUnsupportedFunction();
  return 0;
}

int
attemptFileLock (int file, int exclusive) {
  logUnsupportedFunction();
  return 0;
}

int
releaseFileLock (int file) {
  logUnsupportedFunction();
  return 0;
}
#endif /* file locking */

static void
exitProgramStream (void *data) {
  FILE **stream = data;

  if (*stream) {
    fclose(*stream);
    *stream = NULL;
  }
}

void
registerProgramStream (const char *name, FILE **stream) {
  onProgramExit(name, exitProgramStream, stream);
}

FILE *
openFile (const char *path, const char *mode, int optional) {
  FILE *file = fopen(path, mode);

  if (file) {
    logMessage(LOG_DEBUG, "file opened: %s fd=%d", path, fileno(file));
  } else {
    logMessage((optional && (errno == ENOENT))? LOG_DEBUG: LOG_ERR,
               "cannot open file: %s: %s", path, strerror(errno));
  }

  return file;
}

int
readLine (FILE *file, char **buffer, size_t *size) {
  char *line;

  if (ferror(file)) return 0;
  if (feof(file)) return 0;

  if (!*size) {
    if (!(*buffer = malloc(*size = 0X80))) {
      logMallocError();
      return 0;
    }
  }

  if ((line = fgets(*buffer, *size, file))) {
    size_t length = strlen(line); /* Line length including new-line. */

    /* No trailing new-line means that the buffer isn't big enough. */
    while (line[length-1] != '\n') {
      /* If necessary, extend the buffer. */
      if ((*size - (length + 1)) == 0) {
        size_t newSize = *size << 1;
        char *newBuffer = realloc(*buffer, newSize);

        if (!newBuffer) {
          logMallocError();
          return 0;
        }

        *buffer = newBuffer;
        *size = newSize;
      }

      /* Read the rest of the line into the end of the buffer. */
      if (!(line = fgets(&(*buffer)[length], *size-length, file))) {
        if (!ferror(file)) return 1;
        logSystemError("read");
        return 0;
      }

      length += strlen(line); /* New total line length. */
      line = *buffer; /* Point to the beginning of the line. */
    }

    if (--length > 0)
      if (line[length-1] == '\r')
        --length;
    line[length] = 0; /* Remove trailing new-line. */
    return 1;
  } else if (ferror(file)) {
    logSystemError("read");
  }

  return 0;
}

/* Process each line of an input text file safely.
 * This routine handles the actual reading of the file,
 * insuring that the input buffer is always big enough,
 * and calls a caller-supplied handler once for each line in the file.
 * The caller-supplied data pointer is passed straight through to the handler.
 */
int
processLines (FILE *file, LineHandler handleLine, void *data) {
  unsigned int lineNumber = 0;
  char *buffer = NULL;
  size_t bufferSize = 0;

  while (readLine(file, &buffer, &bufferSize)) {
    char *line = buffer;

    if (!lineNumber++) {
      static const char utf8ByteOrderMark[] = {0XEF, 0XBB, 0XBF};
      static const unsigned int length = sizeof(utf8ByteOrderMark);
      if (strncmp(line, utf8ByteOrderMark, length) == 0) line += length;
    }

    if (!handleLine(line, data)) break;
  }

  if (buffer) free(buffer);
  return !ferror(file);
}

STR_BEGIN_FORMATTER(formatInputError, const char *file, const int *line, const char *format, va_list arguments)
  if (file) STR_PRINTF("%s", file);
  if (line) STR_PRINTF("[%d]", *line);
  if (STR_LENGTH) STR_PRINTF(": ");
  STR_VPRINTF(format, arguments);
STR_END_FORMATTER

#ifdef __MINGW32__
ssize_t
readFileDescriptor (FileDescriptor fileDescriptor, void *buffer, size_t size) {
  {
    DWORD count;

    if (ReadFile(fileDescriptor, buffer, size, &count, NULL)) return count;
  }

  setSystemErrno();
  return -1;
}

ssize_t
writeFileDescriptor (FileDescriptor fileDescriptor, const void *buffer, size_t size) {
  {
    DWORD count;

    if (WriteFile(fileDescriptor, buffer, size, &count, NULL)) return count;
  }

  setSystemErrno();
  return -1;
}

const char *
getNamedPipeDirectory (void) {
  return "//./pipe";
}

int
createAnonymousPipe (FileDescriptor *pipeInput, FileDescriptor *pipeOutput) {
  SECURITY_ATTRIBUTES attributes;

  ZeroMemory(&attributes, sizeof(attributes));
  attributes.nLength = sizeof(attributes);
  attributes.bInheritHandle = TRUE;
  attributes.lpSecurityDescriptor = NULL;

  if (CreatePipe(pipeOutput, pipeInput, &attributes, 0)) {
    return 1;
  } else {
    logWindowsSystemError("CreatePipe");
  }

  return 0;
}

#else /* unix file/socket descriptor operations */
ssize_t
readFileDescriptor (FileDescriptor fileDescriptor, void *buffer, size_t size) {
  return read(fileDescriptor, buffer, size);
}

ssize_t
writeFileDescriptor (FileDescriptor fileDescriptor, const void *buffer, size_t size) {
  return write(fileDescriptor, buffer, size);
}

const char *
getNamedPipeDirectory (void) {
  return getWritableDirectory();
}

int
createAnonymousPipe (FileDescriptor *pipeInput, FileDescriptor *pipeOutput) {
  int fileDescriptors[2];

  if (pipe(fileDescriptors) != -1) {
    *pipeInput = fileDescriptors[1];
    *pipeOutput = fileDescriptors[0];
    return 1;
  } else {
    logSystemError("pipe");
  }

  return 0;
}
#endif /* basic file/socket descriptor operations */

#ifdef GOT_SOCKETS
ssize_t
readSocketDescriptor (SocketDescriptor socketDescriptor, void *buffer, size_t size) {
  return recv(socketDescriptor, buffer, size, 0);
}

ssize_t
writeSocketDescriptor (SocketDescriptor socketDescriptor, const void *buffer, size_t size) {
  return send(socketDescriptor, buffer, size, 0);
}
#endif /* GOT_SOCKETS */
