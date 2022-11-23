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

#include <grub/dl.h>

#include "dynld.h"

void *
loadSharedObject (const char *name) {
  return grub_dl_load(name);
}

void 
unloadSharedObject (void *object) {
  grub_dl_unload(object);
}

int 
findSharedSymbol (void *object, const char *symbol, void *pointerAddress) {
  void **address = pointerAddress;
  grub_symbol_t sym = grub_get_symbol(symbol, object);

  if (!sym) return 0;
  *address = sym->addr;
  return 1;
}

const char *
getSharedSymbolName (void *address, ptrdiff_t *offset) {
  return NULL;
}
