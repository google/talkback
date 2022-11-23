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

#ifndef BRLTTY_INCLUDED_KTB_INTERNAL
#define BRLTTY_INCLUDED_KTB_INTERNAL

#include "strfmth.h"
#include "cmd_types.h"
#include "async.h"

#ifdef __cplusplus
extern "C" {
#endif /* __cplusplus */

#define MAX_MODIFIERS_PER_COMBINATION 10

typedef struct {
  const char *name;
  int bit;
} KeyboardFunction;

extern const KeyboardFunction keyboardFunctionTable[];
extern unsigned char keyboardFunctionCount;

typedef enum {
  KCF_IMMEDIATE_KEY = 0X01
} KeyCombinationFlag;

typedef struct {
  unsigned char flags;
  unsigned char anyKeyCount;
  unsigned char modifierCount;
  unsigned char modifierPositions[MAX_MODIFIERS_PER_COMBINATION];
  KeyValue modifierKeys[MAX_MODIFIERS_PER_COMBINATION];
  KeyValue immediateKey;
} KeyCombination;

typedef struct {
  const CommandEntry *entry;
  int value;
} BoundCommand;

typedef enum {
  KBF_HIDDEN    = 0X01,
  KBF_DUPLICATE = 0X80
} KeyBindingFlag;

typedef struct {
  BoundCommand primaryCommand;
  BoundCommand secondaryCommand;
  KeyCombination keyCombination;
  unsigned char flags;
} KeyBinding;

typedef enum {
  HKF_HIDDEN    = 0X01,
  HKF_DUPLICATE = 0X80
} HotkeyFlag;

typedef struct {
  KeyValue keyValue;
  BoundCommand pressCommand;
  BoundCommand releaseCommand;
  unsigned char flags;
} HotkeyEntry;

typedef enum {
  MKF_HIDDEN    = 0X01,
  MKF_DUPLICATE = 0X80
} MappedKeyFlag;

typedef struct {
  KeyValue keyValue;
  const KeyboardFunction *keyboardFunction;
  unsigned char flags;
} MappedKeyEntry;

typedef struct {
  wchar_t *name;
  wchar_t *title;

  unsigned isSpecial:1;
  unsigned isDefined:1;
  unsigned isReferenced:1;

  struct {
    KeyBinding *table;
    unsigned int size;
    unsigned int count;
  } keyBindings;

  struct {
    HotkeyEntry *table;
    unsigned int size;
    unsigned int count;
  } hotkeys;

  struct {
    MappedKeyEntry *table;
    unsigned int size;
    unsigned int count;
    int superimpose;
  } mappedKeys;
} KeyContext;

struct KeyTableStruct {
  wchar_t *title;

  struct {
    wchar_t **table;
    unsigned int size;
    unsigned int count;
  } notes;

  struct {
    const KeyNameEntry **table;
    unsigned int count;
  } keyNames;

  struct {
    KeyContext *table;
    unsigned int count;
  } keyContexts;

  struct {
    unsigned char persistent;
    unsigned char next;
    unsigned char current;
  } context;

  struct {
    KeyValue *table;
    unsigned int size;
    unsigned int count;
  } pressedKeys;

  struct {
    int command;
  } release;

  struct {
    AsyncHandle alarm;
    int command;
    unsigned repeat:1;

    const char *keyAction;
    unsigned char keyContext;
    KeyValue keyValue;
  } longPress;

  struct {
    AsyncHandle alarm;
    int time;
  } autorelease;

  struct {
    const char *logLabel;
    const unsigned char *logKeyEventsFlag;
    const unsigned char *keyboardEnabledFlag;
  } options;
};

extern void copyKeyValues (KeyValue *target, const KeyValue *source, unsigned int count);
extern int compareKeyValues (const KeyValue *value1, const KeyValue *value2);

extern int findKeyValue (
  const KeyValue *values, unsigned int count,
  const KeyValue *target, unsigned int *position
);

extern int insertKeyValue (
  KeyValue **values, unsigned int *count, unsigned int *size,
  const KeyValue *value, unsigned int position
);

extern void removeKeyValue (KeyValue *values, unsigned int *count, unsigned int position);
extern int deleteKeyValue (KeyValue *values, unsigned int *count, const KeyValue *value);

extern int compareKeyBindings (const KeyBinding *binding1, const KeyBinding *binding2);
extern int compareHotkeyEntries (const HotkeyEntry *hotkey1, const HotkeyEntry *hotkey2);
extern int compareMappedKeyEntries (const MappedKeyEntry *map1, const MappedKeyEntry *map2);

extern STR_DECLARE_FORMATTER(formatKeyName, KeyTable *table, const KeyValue *value);
extern STR_DECLARE_FORMATTER(formatKeyCombination, KeyTable *table, const KeyCombination *combination);

extern void resetLongPressData (KeyTable *table);

#ifdef __cplusplus
}
#endif /* __cplusplus */

#endif /* BRLTTY_INCLUDED_KTB_INTERNAL */
