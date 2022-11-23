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

#include "log.h"
#include "dynld.h"

void *
loadSharedObject (const char *path) {
  HMODULE library;
  if (!(library = LoadLibrary(path)))
    logWindowsSystemError("loading library");
  return library;
}

void 
unloadSharedObject (void *object) {
  if (!(FreeLibrary((HMODULE) object)))
    logWindowsSystemError("unloading library");
}

int 
findSharedSymbol (void *object, const char *symbol, void *pointerAddress) {
  void **address = pointerAddress;
  if ((*address = GetProcAddress((HMODULE) object, symbol)))
    return 1;
  logWindowsSystemError("looking up symbol in library");
  return 0;
}

const char *
getSharedSymbolName (void *address, ptrdiff_t *offset) {
  return NULL;
}
