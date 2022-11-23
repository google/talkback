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
#include <mach-o/dyld.h>

#include "log.h"
#include "dynld.h"

static void
logDyldError (const char *action) {
  NSLinkEditErrors errors;
  int number;
  const char *file;
  const char *message;
  NSLinkEditError(&errors, &number, &file, &message);
  logMessage(LOG_ERR, "%.*s", (int)(strlen(message)-1), message);
}

void *
loadSharedObject (const char *path) {
  NSObjectFileImage image;
  switch (NSCreateObjectFileImageFromFile(path, &image)) {
    case NSObjectFileImageSuccess: {
      NSModule module = NSLinkModule(image, path, NSLINKMODULE_OPTION_RETURN_ON_ERROR);
      if (module) return module;
      logDyldError("link module");
      logMessage(LOG_ERR, "shared object not linked: %s", path);
      break;
    }

    case NSObjectFileImageInappropriateFile:
      logMessage(LOG_ERR, "inappropriate object type: %s", path);
      break;

    case NSObjectFileImageArch:
      logMessage(LOG_ERR, "incorrect object architecture: %s", path);
      break;

    case NSObjectFileImageFormat:
      logMessage(LOG_ERR, "invalid object format: %s", path);
      break;

    case NSObjectFileImageAccess:
      logMessage(LOG_ERR, "inaccessible object: %s", path);
      break;

    case NSObjectFileImageFailure:
    default:
      logMessage(LOG_ERR, "shared object not loaded: %s", path);
      break;
  }
  return NULL;
}

void 
unloadSharedObject (void *object) {
  NSModule module = object;
  NSUnLinkModule(module, NSUNLINKMODULE_OPTION_NONE);
}

int 
findSharedSymbol (void *object, const char *symbol, void *pointerAddress) {
  NSModule module = object;
  char name[strlen(symbol) + 2];
  snprintf(name, sizeof(name), "_%s", symbol);
  {
    NSSymbol sym = NSLookupSymbolInModule(module, name);
    if (sym) {
      void **address = pointerAddress;
      *address = NSAddressOfSymbol(sym);
      return 1;
    }
  }
  return 0;
}

const char *
getSharedSymbolName (void *address, ptrdiff_t *offset) {
  return NULL;
}
