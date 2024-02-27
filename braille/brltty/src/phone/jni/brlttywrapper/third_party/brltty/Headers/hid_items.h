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

#ifndef BRLTTY_INCLUDED_HID_ITEMS
#define BRLTTY_INCLUDED_HID_ITEMS

#include "hid_types.h"

#ifdef __cplusplus
extern "C" {
#endif /* __cplusplus */

typedef union {
  HidUnsignedValue u;
  HidSignedValue s;
} HidItemValue;

typedef struct {
  HidItemValue value;
  uint8_t tag;
  uint8_t valueSize;
} HidItem;

extern int hidNextItem (
  HidItem *item,
  const unsigned char **bytes,
  size_t *count
);

extern unsigned char hidItemValueSize (unsigned char item);

extern int hidReportSize (
  const HidItemsDescriptor *items,
  HidReportIdentifier identifier,
  HidReportSize *size
);

#ifdef __cplusplus
}
#endif /* __cplusplus */

#endif /* BRLTTY_INCLUDED_HID_ITEMS */
