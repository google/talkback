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

#include "addresses.h"
#include "log.h"
#include "dynld.h"
#include "program.h"

typedef struct {
  void *address;
  char name[0];
} AddressEntry;

static AddressEntry **addressTable = NULL;
static int addressCount = 0;
static int addressLimit = 0;

static void
exitAddressTable (void *data) {
  if (addressTable) free(addressTable);

  addressTable = NULL;
  addressCount = 0;
  addressLimit = 0;
}

static int
findAddressIndex (int *index, void *address) {
  int first = 0;
  int last = addressCount - 1;

  while (first <= last) {
    int current = (first + last) / 2;
    AddressEntry *entry = addressTable[current];

    if (entry->address == address) {
      *index = current;
      return 1;
    }

    if (address < entry->address) {
      last = current - 1;
    } else {
      first = current + 1;
    }
  }

  *index = first;
  return 0;
}

static void
moveAddressSlice (int to, int from, int count) {
  memmove(&addressTable[to], &addressTable[from], ARRAY_SIZE(addressTable, count));
}

static int
insertAddressEntry (int index, AddressEntry *entry) {
  if (addressCount == addressLimit) {
    int newLimit = addressLimit + 1;
    AddressEntry **newTable = realloc(addressTable, ARRAY_SIZE(newTable, newLimit));

    if (!newTable) {
      logMallocError();
      return 0;
    }

    if (!addressTable) {
      onProgramExit("address-table", exitAddressTable, NULL);
    }

    addressTable = newTable;
    addressLimit = newLimit;
  }

  moveAddressSlice(index+1, index, (addressCount++ - index));
  addressTable[index] = entry;
  return 1;
}

static void
removeAddressEntry (int index) {
  free(addressTable[index]);
  moveAddressSlice(index, index+1, (--addressCount - index));
}

int
setAddressName (void *address, const char *format, ...) {
  char name[0X1000];
  AddressEntry *entry;
  size_t size;

  {
    va_list arguments;

    va_start(arguments, format);
    vsnprintf(name, sizeof(name), format, arguments);
    va_end(arguments);
  }

  size = sizeof(*entry) + strlen(name) + 1;

  if ((entry = malloc(size))) {
    int index;

    memset(entry, 0, sizeof(*entry));
    entry->address = address;
    strcpy(entry->name, name);

    if (findAddressIndex(&index, address)) {
      free(addressTable[index]);
      addressTable[index] = entry;
      return 1;
    }

    if (insertAddressEntry(index, entry)) return 1;

    free(entry);
  } else {
    logMallocError();
  }

  return 0;
}

void
unsetAddressName (void *address) {
  int index;

  if (findAddressIndex(&index, address)) {
    removeAddressEntry(index);
  }
}

const char *
getAddressName (void *address, ptrdiff_t *offset) {
  {
    int index;

    if (findAddressIndex(&index, address)) {
      if (offset) *offset = 0;
      return addressTable[index]->name;
    }
  }

  return getSharedSymbolName(address, offset);
}
