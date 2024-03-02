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

#include <string.h>

#include "log.h"
#include "dataarea.h"

struct DataAreaStruct {
  unsigned char *address;
  size_t size;
  size_t used;
};

void
resetDataArea (DataArea *area) {
  area->address = NULL;
  area->size = 0;
  area->used = 0;
}

DataArea *
newDataArea (void) {
  DataArea *area;
  if ((area = malloc(sizeof(*area)))) resetDataArea(area);
  return area;
}

void
destroyDataArea (DataArea *area) {
  if (area->address) free(area->address);
  free(area);
}

int
allocateDataItem (DataArea *area, DataOffset *offset, size_t size, size_t alignment) {
  size_t newOffset = (area->used + (alignment - 1)) / alignment * alignment;
  size_t newUsed = newOffset + size;

  if (newUsed > area->size) {
    size_t newSize = (newUsed | 0XFFF) + 1;
    unsigned char *newAddress;

    if (!(newAddress = realloc(area->address, newSize))) {
      logMallocError();
      return 0;
    }

    memset(newAddress+area->size, 0, (newSize - area->size));
    area->address = newAddress;
    area->size = newSize;
  }

  area->used = newUsed;
  if (offset) *offset = newOffset;
  return 1;
}

void *
getDataItem (DataArea *area, DataOffset offset) {
  return area->address + offset;
}

size_t
getDataSize (DataArea *area) {
  return area->used;
}

int
saveDataItem (DataArea *area, DataOffset *offset, const void *item, size_t size, size_t alignment) {
  if (!allocateDataItem(area, offset, size, alignment)) return 0;
  memcpy(getDataItem(area, *offset), item, size);
  return 1;
}
