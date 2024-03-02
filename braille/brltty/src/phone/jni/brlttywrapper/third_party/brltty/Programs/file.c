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
#include <stdarg.h>
#include <string.h>
#include <errno.h>
#include <ctype.h>
#include <fcntl.h>
#include <sys/stat.h>
#include <limits.h>
#include <locale.h>

#ifdef HAVE_LANGINFO_H
#include <langinfo.h>
#endif /* HAVE_LANGINFO_H */

#ifdef HAVE_ICONV_H
#include <iconv.h>
#endif /* HAVE_ICONV_H */

#ifdef HAVE_SYS_FILE_H
#include <sys/file.h>
#endif /* HAVE_SYS_FILE_H */

#include "parameters.h"
#include "log.h"
#include "strfmt.h"
#include "file.h"
#include "lock.h"
#include "parse.h"
#include "async_wait.h"
#include "utf8.h"
#include "program.h"


static inline int
allowBackslashAsPathSeparator (void) {
#if defined(__MINGW32__) || defined(__MSDOS__)
  return 1;
#else /* allow backslash */
  return 0;
#endif /* allow backslash */
}

static int
isDriveLetter (char character) {
  return !!strchr("ABCDEFGHIJKLMNOPQRSTUVWXYZ", toupper(character));
}

int
isPathSeparator (const char character) {
  if (character == PATH_SEPARATOR_CHARACTER) return 1;

  if (allowBackslashAsPathSeparator()) {
    if (character == '\\') {
      return 1;
    }
  }

  return 0;
}

int
isAbsolutePath (const char *path) {
  if (isPathSeparator(path[0])) return 1;

  if (allowBackslashAsPathSeparator()) {
    if (isDriveLetter(path[0])) {
      if (path[1] == ':') {
        if (isPathSeparator(path[2])) {
          return 1;
        }
      }
    }
  }

  return 0;
}

static size_t
stripPathSeparator (const char *path, size_t length) {
  while (length) {
    if (!isPathSeparator(path[length-1])) break;
    length -= 1;
  }

  return length;
}

char *
getPathDirectory (const char *path) {
  size_t length = strlen(path);
  size_t end = stripPathSeparator(path, length);

  if (end) {
    while (--end) {
      if (isPathSeparator(path[end-1])) {
        break;
      }
    }

    if ((length = end)) {
      if ((end = stripPathSeparator(path, length))) {
        length = end;
      }
    }
  }

  if (!length) length = strlen((path = CURRENT_DIRECTORY_NAME));
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
    if (isPathSeparator(*--name)) {
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
      if ((first != size) && !isPathSeparator(next[strlen(next)-1])) {
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

static LockDescriptor *
getUmaskLock (void) {
  static LockDescriptor *lock = NULL;
  return getLockDescriptor(&lock, "umask");
}

void
lockUmask (void) {
  obtainExclusiveLock(getUmaskLock());
}

void
unlockUmask (void) {
  releaseLock(getUmaskLock());
}

int
createDirectory (const char *path, int worldWritable) {
#if defined(GRUB_RUNTIME)
  errno = EROFS;

#else /* make directory */
#ifdef __MINGW32__
  if (mkdir(path) != -1) return 1;
#else /* __MINGW32__ */
  lockUmask();
  int created = mkdir(path, (S_IRWXU | S_IRWXG | S_IROTH | S_IXOTH)) != -1;
  unlockUmask();

  if (created) {
    if (!worldWritable) return 1;
    mode_t mode = 0;

    #ifdef S_IRWXU
    mode |= S_IRWXU;
    #endif /* S_IRWXU */

    #ifdef S_IRWXG
    mode |= S_IRWXG;
    #endif /* S_IRWXG */

    #ifdef S_IRWXO
    mode |= S_IRWXO;
    #endif /* S_IRWXO */

    #ifdef S_ISVTX
    mode |= S_ISVTX;
    #endif /* S_ISVTX */

    {
      lockUmask();
      int changed = chmod(path, mode) != -1;
      unlockUmask();
      if (changed) return 1;
    }

    logMessage(LOG_WARNING,
      "%s: %s: %s",
      gettext("cannot make world writable"),
      path, strerror(errno)
    );

    return 0;
  }
#endif /* __MINGW32__ */
#endif /* make directory */

  logMessage(LOG_WARNING,
    "%s: %s: %s",
    gettext("cannot create directory"),
    path, strerror(errno)
  );

  return 0;
}

int
ensureDirectory (const char *path, int worldWritable) {
  if (testDirectoryPath(path)) return 1;

  if (errno == EEXIST) {
    logMessage(LOG_ERR, "not a directory: %s", path);
  } else if (errno != ENOENT) {
    logMessage(LOG_ERR, "cannot access directory: %s: %s", path, strerror(errno));
  } else {
    {
      char *parent = getPathDirectory(path);
      if (!parent) return 0;
      int exists = 0;

      if (strcmp(parent, path) != 0) {
        exists = ensureDirectory(parent, 0);
      }

      free(parent);
      if (!exists) return 0;
    }

    if (createDirectory(path, worldWritable)) {
      logMessage(LOG_NOTICE, "directory created: %s", path);
      return 1;
    }
  }

  return 0;
}

int
ensurePathDirectory (const char *path) {
  char *directory = getPathDirectory(path);
  if (!directory) return 0;

  {
    int exists = ensureDirectory(directory, 0);
    free(directory);
    return exists;
  }
}

static void
setDirectory (const char **variable, const char *directory) {
  *variable = directory;
}

static const char *
getDirectory (const char *const *variable) {
  if (*variable && **variable) {
    if (ensureDirectory(*variable, 0)) {
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
  return makePath(base, (xdg? PACKAGE_TARNAME: (CURRENT_DIRECTORY_NAME PACKAGE_TARNAME)));
}

static int
addOverridePath (char **paths, size_t *index, const char *base, int xdg) {
  char *path = makeOverridePath(base, xdg);
  if (!path) return 0;

  logMessage(LOG_DEBUG, "override directory: %s", path);
  paths[(*index)++] = path;
  return 1;
}

static char **overrideDirectories = NULL;

const char *const *
getAllOverrideDirectories (void) {
  if (!overrideDirectories) {
    logMessage(LOG_DEBUG, "determining override directories");

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
          overrideDirectories = paths;
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

  return (const char *const *)overrideDirectories;
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

void
forgetOverrideDirectories (void) {
  if (overrideDirectories) {
    logMessage(LOG_DEBUG, "forgetting override directories");
    deallocateStrings(overrideDirectories);
    overrideDirectories = NULL;
  }
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
readLine (FILE *file, char **buffer, size_t *size, size_t *length) {
  char *line;

  if (ferror(file)) return 0;
  if (feof(file)) return 0;

  if (!*size) {
    if (!(*buffer = malloc((*size = 0X80)))) {
      logMallocError();
      return 0;
    }
  }

  if ((line = fgets(*buffer, *size, file))) {
    size_t count = strlen(line); /* Line length including new-line. */

    /* No trailing new-line means that the buffer isn't big enough. */
    while (line[count-1] != '\n') {
      /* If necessary, extend the buffer. */
      if ((*size - (count + 1)) == 0) {
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
      if (!(line = fgets(&(*buffer)[count], (*size -count), file))) {
        if (!ferror(file)) goto done;
        logSystemError("fgets");
        return 0;
      }

      count += strlen(line); /* New total line length. */
      line = *buffer; /* Point to the beginning of the line. */
    }

    if (--count > 0) {
      if (line[count-1] == '\r') {
        count -= 1;
      }
    }

    line[count] = 0; /* Remove trailing new-line. */
  done:
    if (length) *length = count;
    return 1;
  } else if (ferror(file)) {
    logSystemError("fgets");
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
  char *buffer = NULL;
  size_t bufferSize = 0;

  LineHandlerParameters parameters = {
    .data = data,

    .line = {
      .number = 0,
    },
  };

  while (1) {
    parameters.line.number += 1;
    if (!readLine(file, &buffer, &bufferSize, &parameters.line.length)) break;
    parameters.line.text = buffer;
    if (!handleLine(&parameters)) break;
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

static void
detachStandardStream (FILE *stream, const char *name, int output) {
  const char *nullDevice = "/dev/null";

  if (output) {
    fflush(stream);
  }

  if (!freopen(nullDevice, (output? "a": "r"), stream)) {
    if (errno != ENOENT) {
      char action[0X40];
      snprintf(action, sizeof(action), "freopen[%s]", name);
      logSystemError(action);
    }
  }
}

void
detachStandardInput (void) {
  detachStandardStream(stdin, "stdin", 0);
}

void
detachStandardOutput (void) {
  detachStandardStream(stdout, "stdout", 1);
}

void
detachStandardError (void) {
  detachStandardStream(stderr, "stderr", 1);
}

void
detachStandardStreams (void) {
  detachStandardInput();
  detachStandardOutput();
  detachStandardError();
}

#ifdef __MINGW32__
int
getConsoleSize (size_t *width, size_t *height) {
  HANDLE handle = GetStdHandle(STD_OUTPUT_HANDLE);

  if (handle) {
    if (handle != INVALID_HANDLE_VALUE) {
      if (GetFileType(handle) == FILE_TYPE_CHAR) {
        CONSOLE_SCREEN_BUFFER_INFO info;

        if (GetConsoleScreenBufferInfo(handle, &info)) {
          COORD size = info.dwSize;
          if (width) *width = size.X;
          if (height) *height = size.Y;
          return 1;
        }
      }
    }
  }

  return 0;
}

const char *
getConsoleEncoding (void) {
  static char encoding[0X10];

  if (!encoding[0]) {
    unsigned cp = GetConsoleOutputCP();

    if (cp == CP_UTF8) {
      strcpy(encoding, "UTF-8");
    } else {
      snprintf(encoding, sizeof(encoding), "CP%u", cp);
    }

    logMessage(LOG_DEBUG, "Console Encoding: %s", encoding);
  }

  return encoding;
}

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
#include <sys/ioctl.h>

int
getConsoleSize (size_t *width, size_t *height) {
  struct winsize size;
  if (ioctl(STDOUT_FILENO, TIOCGWINSZ, &size) == -1) return 0;

  if (width) *width = size.ws_col;
  if (height) *height = size.ws_row;
  return 1;
}

const char *
getConsoleEncoding (void) {
  static const char *encoding = NULL;

  if (!encoding) {
    setlocale(LC_ALL, "");

#ifdef HAVE_NL_LANGINFO
    encoding = nl_langinfo(CODESET);
#endif /* HAVE_NL_LANGINFO */

    if (encoding) {
      if (!(encoding = strdup(encoding))) {
        logMallocError();
      }
    }

    if (!encoding) encoding = "";
    logMessage(LOG_DEBUG, "Console Encoding: %s", encoding);
  }

  return encoding;
}

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

void
writeWithConsoleEncoding (FILE *stream, const char *bytes, size_t count) {
  static const char *consoleEncoding = NULL;
  if (!consoleEncoding) consoleEncoding = getConsoleEncoding();

  if (!consoleEncoding || isCharsetUTF8(consoleEncoding)) {
    consoleEncoding = "";
  }

#ifdef HAVE_ICONV_H
  if (*consoleEncoding) {
    static iconv_t iconvHandle = (iconv_t)-1;

    if (iconvHandle == (iconv_t)-1) {
      static const char internalEncoding[] = "UTF-8";
      const char *externalEncoding = consoleEncoding;

      if ((iconvHandle = iconv_open(externalEncoding, internalEncoding)) == (iconv_t)-1) {
        consoleEncoding = "";

        logMessage(LOG_WARNING,
          "iconv open error: %s -> %s: %s",
          internalEncoding, externalEncoding, strerror(errno)
        );

        goto ENCODING_NOT_SUPPORTED;
      }
    }

    const char *inputNext = bytes;
    size_t inputLeft = count;

    char outputBuffer[inputLeft * MB_LEN_MAX];
    char *outputNext = outputBuffer;
    size_t outputLeft = sizeof(outputBuffer);

    ssize_t result = iconv(
      iconvHandle,
      (char **)&inputNext, &inputLeft,
      &outputNext, &outputLeft
    );

    if (result != -1) {
      size_t length = outputNext - outputBuffer;
      outputBuffer[length] = 0;
      fputs(outputBuffer, stream);
    }

    return;
  }
ENCODING_NOT_SUPPORTED:
#endif /* HAVE_ICONV_H */

  fwrite(bytes, 1, count, stream);
}

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

char *
readSymbolicLink (const char *path) {
  char *content = NULL;
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

    {
      int length;

      #ifdef HAVE_READLINK
      length = readlink(path, buffer, size);
      #else /* HAVE_READLINK */
      length = -1;
      errno = ENOSYS;
      #endif /* HAVE_READLINK */

      if (length == -1) {
        if (errno != ENOENT) logSystemError("readlink");
        break;
      }

      if (length < size) {
        buffer[length] = 0;
        if (!(content = strdup(buffer))) logMallocError();
        break;
      }
    }
  }

  if (buffer) free(buffer);
  return content;
}
