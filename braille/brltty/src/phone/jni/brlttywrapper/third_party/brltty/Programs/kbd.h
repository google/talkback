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

#ifndef BRLTTY_INCLUDED_KBD
#define BRLTTY_INCLUDED_KBD

#include "ktb_types.h"

#ifdef __cplusplus
extern "C" {
#endif /* __cplusplus */

typedef enum {
  KBD_TYPE_Any = 0,
  KBD_TYPE_PS2,
  KBD_TYPE_USB,
  KBD_TYPE_Bluetooth
} KeyboardType;

typedef struct {
  KeyboardType type;
  int vendor;
  int product;
} KeyboardProperties;

extern const KeyboardProperties anyKeyboard;

extern int parseKeyboardProperties (KeyboardProperties *properties, const char *string);
extern int checkKeyboardProperties (const KeyboardProperties *actual, const KeyboardProperties *required);

typedef struct KeyboardMonitorObjectStruct KeyboardMonitorObject;
typedef KeyTableState KeyEventHandler (KeyGroup group, KeyNumber number, int press);

extern KeyboardMonitorObject *newKeyboardMonitorObject (const KeyboardProperties *properties, KeyEventHandler handleKeyEvent);
extern void destroyKeyboardMonitorObject (KeyboardMonitorObject *kmo);

#ifdef __cplusplus
}
#endif /* __cplusplus */

#endif /* BRLTTY_INCLUDED_KBD */
