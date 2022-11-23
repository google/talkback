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
#include <string.h>
#include <strings.h>
#include <errno.h>
#include <fcntl.h>

#include "log.h"
#include "strfmt.h"
#include "device.h"
#include "file.h"
#include "parse.h"

FILE *
getConsole (void) {
#if defined(GRUB_RUNTIME)
  return stdout;
#else /* get console */
  static FILE *console = NULL;

  if (!console) {
    if ((console = fopen("/dev/console", "wb"))) {
      logMessage(LOG_DEBUG, "console opened: fd=%d", fileno(console));
      registerProgramStream("console-stream", &console);
    } else {
      logSystemError("console open");
    }
  }

  return console;
#endif /* get console */
}

int
writeConsole (const unsigned char *bytes, size_t count) {
  FILE *console = getConsole();
  if (!console) return 0;

  while (count) {
    size_t result = fwrite(bytes, 1, count, console);
    if (!ferror(console)) fflush(console);

    if (ferror(console)) {
      logSystemError("console write");
      return 0;
    }

    bytes += result;
    count -= result;
  }

  return 1;
}

int
ringBell (void) {
  static unsigned char bellSequence[] = {0X07};
  return writeConsole(bellSequence, sizeof(bellSequence));
}

const char *
getDeviceDirectory (void) {
  static const char *deviceDirectory = NULL;

  if (!deviceDirectory) {
    const char *directory = DEVICE_DIRECTORY;
    const size_t directoryLength = strlen(directory);

    static const char *const variables[] = {"DTDEVROOT", "UTDEVROOT", NULL};
    const char *const *variable = variables;

    while (*variable) {
      const char *root = getenv(*variable);

      if (root && *root) {
        const size_t rootLength = strlen(root);
        char path[rootLength + directoryLength + 1];
        snprintf(path, sizeof(path), "%s%s", root, directory);

        if (testDirectoryPath(path)) {
          if ((deviceDirectory = strdup(path))) goto found;
          logMallocError();
        } else if (errno != ENOENT) {
          logMessage(LOG_ERR, "device directory error: %s (%s): %s",
                     path, *variable, strerror(errno));
        }
      }

      variable += 1;
    }

    deviceDirectory = directory;
  found:
    logMessage(LOG_DEBUG, "device directory: %s", deviceDirectory);
  }

  return deviceDirectory;
}

char *
getDevicePath (const char *device) {
  const char *directory = getDeviceDirectory();

#ifdef ALLOW_DOS_DEVICE_NAMES
  if (isDosDevice(device, NULL)) {
  //directory = NULL;
  }
#endif /* ALLOW_DOS_DEVICE_NAMES */

  return makePath(directory, device);
}

const char *
resolveDeviceName (const char *const *names, const char *description) {
  const char *first = *names;
  const char *device = NULL;
  const char *name;

  while ((name = *names++)) {
    char *path = getDevicePath(name);

    if (!path) break;
    logMessage(LOG_DEBUG, "checking %s device: %s", description, path);

    if (testPath(path)) {
      device = name;
      free(path);
      break;
    }

    logMessage(LOG_DEBUG, "%s device access error: %s: %s",
               description, path, strerror(errno));
    if (errno != ENOENT)
      if (!device)
        device = name;
    free(path);
  }

  if (!device) {
    if (first) {
      device = first;
    } else {
      logMessage(LOG_ERR, "%s device names not defined", description);
    }
  }

  if (device) logMessage(LOG_INFO, "%s device: %s", description, device);
  return device;
}

char **
getDeviceParameters (const char *const *names, const char *identifier) {
  char parameters[strlen(names[0]) + 1 + strlen(identifier) + 1];
  STR_BEGIN(parameters, sizeof(parameters))

  {
    static const char characters[] = {
      DEVICE_PARAMETER_SEPARATOR,
      PARAMETER_ASSIGNMENT_CHARACTER,
      0
    };
    const char *character = strpbrk(identifier, characters);

    if (!(character && (*character == PARAMETER_ASSIGNMENT_CHARACTER))) {
       STR_PRINTF("%s%c", names[0], PARAMETER_ASSIGNMENT_CHARACTER);
    }
  }

  {
    char *character = STR_NEXT;
    STR_PRINTF("%s", identifier);

    while (character < STR_NEXT) {
      if (*character == DEVICE_PARAMETER_SEPARATOR) *character = PARAMETER_SEPARATOR_CHARACTER;
      character += 1;
    }
  }

  STR_END;
  return getParameters(names, NULL, parameters);
}

#ifdef ALLOW_DOS_DEVICE_NAMES
int
isDosDevice (const char *identifier, const char *prefix) {
  size_t count = strcspn(identifier, ":");
  size_t length;

  if (!count) return 0;

  if (prefix) {
    if (!(length = strlen(prefix))) return 0;
    if (length > count) return 0;
    if (strncasecmp(identifier, prefix, length) != 0) return 0;
  } else {
    length = strspn(identifier, "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz");
    if (!length) return 0;
  }

  identifier += length;
  count -= length;

  if (strspn(identifier, "0123456789") != count) return 0;
  return 1;
}
#endif /* ALLOW_DOS_DEVICE_NAMES */
