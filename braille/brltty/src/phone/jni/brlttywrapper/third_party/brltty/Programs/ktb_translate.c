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

#include "log.h"
#include "strfmt.h"
#include "alert.h"
#include "prefs.h"
#include "ktb.h"
#include "ktb_internal.h"
#include "ktb_inspect.h"
#include "brl_cmds.h"
#include "cmd.h"
#include "cmd_enqueue.h"
#include "async_alarm.h"

#define BRL_CMD_ALERT(alert) BRL_CMD_ARG(ALERT, ALERT_##alert)

ASYNC_ALARM_CALLBACK(handleKeyAutoreleaseAlarm) {
  KeyTable *table = parameters->data;

  asyncDiscardHandle(table->autorelease.alarm);
  table->autorelease.alarm = NULL;

  for (unsigned int index=0; index<table->pressedKeys.count; index+=1) {
    const KeyValue *kv = &table->pressedKeys.table[index];

    char key[0X40];
    STR_BEGIN(key, sizeof(key));

    STR_FORMAT(formatKeyName, table, kv);
    STR_PRINTF(" (Grp:%u Num:%u)", kv->group, kv->number);

    STR_END;
    logMessage(LOG_WARNING, "autoreleasing key: %s", key);
  }

  resetKeyTable(table);
  alert(ALERT_KEYS_AUTORELEASED);
}

static void
cancelAutoreleaseAlarm (KeyTable *table) {
  if (table->autorelease.alarm) {
    asyncCancelRequest(table->autorelease.alarm);
    table->autorelease.alarm = NULL;
  }
}

static void
setAutoreleaseAlarm (KeyTable *table) {
  if (!table->autorelease.time || !table->pressedKeys.count) {
    cancelAutoreleaseAlarm(table);
  } else if (table->autorelease.alarm) {
    asyncResetAlarmIn(table->autorelease.alarm, table->autorelease.time);
  } else {
    asyncNewRelativeAlarm(&table->autorelease.alarm, table->autorelease.time,
                          handleKeyAutoreleaseAlarm, table);
  }
}

void
setKeyAutoreleaseTime (KeyTable *table, unsigned char setting) {
  table->autorelease.time = setting? (5000 << (setting - 1)): 0;
  setAutoreleaseAlarm(table);
}

static int
sortModifierKeys (const void *element1, const void *element2) {
  const KeyValue *modifier1 = element1;
  const KeyValue *modifier2 = element2;
  return compareKeyValues(modifier1, modifier2);
}

static int
searchKeyBinding (const void *target, const void *element) {
  const KeyBinding *reference = target;
  const KeyBinding *binding = element;
  return compareKeyBindings(reference, binding);
}

static const KeyBinding *
findKeyBinding (KeyTable *table, unsigned char context, const KeyValue *immediate, int *isIncomplete) {
  const KeyContext *ctx = getKeyContext(table, context);

  if (!ctx) return NULL;
  if (!ctx->keyBindings.table) return NULL;
  if (table->pressedKeys.count > MAX_MODIFIERS_PER_COMBINATION) return NULL;

  KeyBinding target = {
    .keyCombination.modifierCount = table->pressedKeys.count
  };

  if (immediate) {
    target.keyCombination.immediateKey = *immediate;
    target.keyCombination.flags |= KCF_IMMEDIATE_KEY;
  }

  while (1) {
    unsigned int all = (1 << table->pressedKeys.count) - 1;

    for (unsigned int bits=0; bits<=all; bits+=1) {
      {
        unsigned int index;
        unsigned int bit;

        for (index=0, bit=1; index<table->pressedKeys.count; index+=1, bit<<=1) {
          KeyValue *modifier = &target.keyCombination.modifierKeys[index];

          *modifier = table->pressedKeys.table[index];
          if (bits & bit) modifier->number = KTB_KEY_ANY;
        }
      }

      qsort(
        target.keyCombination.modifierKeys, table->pressedKeys.count,
        sizeof(*target.keyCombination.modifierKeys), sortModifierKeys
      );

      {
        const KeyBinding *binding = bsearch(&target, ctx->keyBindings.table,
                                            ctx->keyBindings.count,
                                            sizeof(*ctx->keyBindings.table),
                                            searchKeyBinding);

        if (binding) {
          if (binding->primaryCommand.value != EOF) return binding;
          *isIncomplete = 1;
        }
      }
    }

    if (!(target.keyCombination.flags & KCF_IMMEDIATE_KEY)) break;
    if (target.keyCombination.immediateKey.number == KTB_KEY_ANY) break;
    target.keyCombination.immediateKey.number = KTB_KEY_ANY;
  }

  return NULL;
}

static int
searchHotkeyEntry (const void *target, const void *element) {
  const HotkeyEntry *reference = target;
  const HotkeyEntry *hotkey = element;
  return compareHotkeyEntries(reference, hotkey);
}

static const HotkeyEntry *
findHotkeyEntry (KeyTable *table, unsigned char context, const KeyValue *keyValue) {
  const KeyContext *ctx = getKeyContext(table, context);

  if (!ctx) return NULL;
  if (!ctx->hotkeys.table) return NULL;

  HotkeyEntry target = {
    .keyValue = *keyValue
  };

  return bsearch(&target, ctx->hotkeys.table, ctx->hotkeys.count,
                 sizeof(*ctx->hotkeys.table), searchHotkeyEntry);
}

static int
searchMappedKeyEntry (const void *target, const void *element) {
  const MappedKeyEntry *reference = target;
  const MappedKeyEntry *map = element;
  return compareMappedKeyEntries(reference, map);
}

static const MappedKeyEntry *
findMappedKeyEntry (const KeyContext *ctx, const KeyValue *keyValue) {
  if (!ctx) return NULL;
  if (!ctx->mappedKeys.table) return NULL;

  MappedKeyEntry target = {
    .keyValue = *keyValue
  };

  return bsearch(&target, ctx->mappedKeys.table, ctx->mappedKeys.count,
                 sizeof(*ctx->mappedKeys.table), searchMappedKeyEntry);
}

static int
makeKeyboardCommand (KeyTable *table, unsigned char context, int allowChords) {
  const KeyContext *ctx;

  if ((ctx = getKeyContext(table, context))) {
    int bits = 0;

    for (unsigned int pressedIndex=0; pressedIndex<table->pressedKeys.count; pressedIndex+=1) {
      const KeyValue *keyValue = &table->pressedKeys.table[pressedIndex];
      const MappedKeyEntry *map = findMappedKeyEntry(ctx, keyValue);

      if (!map) return EOF;
      bits |= map->keyboardFunction->bit;
    }

    {
      int space = bits & BRL_DOTC;
      int dots = bits & BRL_ALL_DOTS;

      if (!allowChords) {
        if (!space == !dots) return EOF;
        bits &= ~BRL_DOTC;
      }

      if (dots) bits |= ctx->mappedKeys.superimpose;
    }

    return BRL_CMD_BLK(PASSDOTS) | bits;
  }

  return EOF;
}

static int
findPressedKey (KeyTable *table, const KeyValue *value, unsigned int *position) {
  return findKeyValue(table->pressedKeys.table, table->pressedKeys.count, value, position);
}

static int
insertPressedKey (KeyTable *table, const KeyValue *value, unsigned int position) {
  return insertKeyValue(&table->pressedKeys.table, &table->pressedKeys.count, &table->pressedKeys.size, value, position);
}

static void
removePressedKey (KeyTable *table, unsigned int position) {
  removeKeyValue(table->pressedKeys.table, &table->pressedKeys.count, position);
}

static inline void
deleteExplicitKeyValue (KeyValue *values, unsigned int *count, const KeyValue *value) {
  if (value->number != KTB_KEY_ANY) deleteKeyValue(values, count, value);
}

static int
sortKeyOffsets (const void *element1, const void *element2) {
  const KeyValue *value1 = element1;
  const KeyValue *value2 = element2;

  if (value1->number < value2->number) return -1;
  if (value1->number > value2->number) return 1;
  return 0;
}

static void
addCommandArguments (KeyTable *table, int *command, const CommandEntry *entry, const KeyBinding *binding) {
  if (entry->isOffset | entry->isColumn | entry->isRow | entry->isRange | entry->isKeyboard) {
    unsigned int keyCount = table->pressedKeys.count;
    KeyValue keyValues[keyCount];
    copyKeyValues(keyValues, table->pressedKeys.table, keyCount);

    {
      int index;

      for (index=0; index<binding->keyCombination.modifierCount; index+=1) {
        deleteExplicitKeyValue(keyValues, &keyCount, &binding->keyCombination.modifierKeys[index]);
      }
    }

    if (binding->keyCombination.flags & KCF_IMMEDIATE_KEY) {
      deleteExplicitKeyValue(keyValues, &keyCount, &binding->keyCombination.immediateKey);
    }

    if (keyCount > 0) {
      if (keyCount > 1) {
        qsort(keyValues, keyCount, sizeof(*keyValues), sortKeyOffsets);
        if (entry->isRange) *command |= BRL_EXT_PUT(keyValues[1].number);
      }

      *command += keyValues[0].number;
    } else if (entry->isColumn) {
      if (!entry->isRouting) *command |= BRL_MSK_ARG;
    }
  }
}

static int
processCommand (KeyTable *table, int command) {
  int blk = command & BRL_MSK_BLK;
  int arg = command & BRL_MSK_ARG;

  switch (blk) {
    case BRL_CMD_BLK(CONTEXT): {
      unsigned char context = KTB_CTX_DEFAULT + arg;
      const KeyContext *ctx = getKeyContext(table, context);

      if (ctx) {
        command = BRL_CMD_NOOP;
        table->context.next = context;

        if (isTemporaryKeyContext(table, ctx)) {
          if (!enqueueCommand(BRL_CMD_ALERT(TOGGLE_ON))) return 0;
        } else {
          table->context.persistent = context;
          if (!enqueueCommand(BRL_CMD_ALERT(TOGGLE_OFF))) return 0;
        }
      }

      break;
    }

    case BRL_CMD_BLK(PASSCHAR):
    case BRL_CMD_BLK(PASSDOTS):
    case BRL_CMD_BLK(PASSKEY):
      if (table->options.keyboardEnabledFlag && !*table->options.keyboardEnabledFlag) {
        command = BRL_CMD_ALERT(COMMAND_REJECTED);
      }
      break;

    default:
      break;
  }

  return enqueueCommand(command);
}

static void
logKeyEvent (
  KeyTable *table, const char *action,
  unsigned char context, const KeyValue *keyValue, int command
) {
  if (table->options.logKeyEventsFlag && *table->options.logKeyEventsFlag) {
    char buffer[0X100];

    STR_BEGIN(buffer, sizeof(buffer));
    if (table->options.logLabel) STR_PRINTF("%s ", table->options.logLabel);
    STR_PRINTF("key %s: ", action);
    STR_FORMAT(formatKeyName, table, keyValue);
    STR_PRINTF(" (Ctx:%u Grp:%u Num:%u)", context, keyValue->group, keyValue->number);

    if (command != EOF) {
      const CommandEntry *cmd = findCommandEntry(command);
      const char *name = cmd? cmd->name: "?";

      STR_PRINTF(" -> %s (Cmd:%06X)", name, command);
    }

    STR_END;
    logMessage(categoryLogLevel, "%s", buffer);
  }
}

static void setLongPressAlarm (KeyTable *table, unsigned char when);

ASYNC_ALARM_CALLBACK(handleLongPressAlarm) {
  KeyTable *table = parameters->data;
  int command = table->longPress.command;

  asyncDiscardHandle(table->longPress.alarm);
  table->longPress.alarm = NULL;

  logKeyEvent(table, table->longPress.keyAction,
              table->longPress.keyContext,
              &table->longPress.keyValue,
              command);

  if (table->longPress.repeat) {
    table->longPress.keyAction = "repeat";
    setLongPressAlarm(table, prefs.autorepeatInterval);
  }

  table->release.command = BRL_CMD_NOOP;
  processCommand(table, command);
}

static void
setLongPressAlarm (KeyTable *table, unsigned char when) {
  asyncNewRelativeAlarm(&table->longPress.alarm, PREFERENCES_TIME(when),
                        handleLongPressAlarm, table);
}

static int
isRepeatableCommand (int command) {
  if (prefs.autorepeatEnabled) {
    switch (command & BRL_MSK_BLK) {
      case BRL_CMD_BLK(PASSCHAR):
      case BRL_CMD_BLK(PASSDOTS):
        return 1;

      default:
        switch (command & BRL_MSK_CMD) {
          case BRL_CMD_LNUP:
          case BRL_CMD_LNDN:
          case BRL_CMD_PRDIFLN:
          case BRL_CMD_NXDIFLN:
          case BRL_CMD_CHRLT:
          case BRL_CMD_CHRRT:

          case BRL_CMD_MENU_PREV_ITEM:
          case BRL_CMD_MENU_NEXT_ITEM:
          case BRL_CMD_MENU_PREV_SETTING:
          case BRL_CMD_MENU_NEXT_SETTING:

          case BRL_CMD_KEY(BACKSPACE):
          case BRL_CMD_KEY(DELETE):
          case BRL_CMD_KEY(PAGE_UP):
          case BRL_CMD_KEY(PAGE_DOWN):
          case BRL_CMD_KEY(CURSOR_UP):
          case BRL_CMD_KEY(CURSOR_DOWN):
          case BRL_CMD_KEY(CURSOR_LEFT):
          case BRL_CMD_KEY(CURSOR_RIGHT):

          case BRL_CMD_SPEAK_PREV_CHAR:
          case BRL_CMD_SPEAK_NEXT_CHAR:
          case BRL_CMD_SPEAK_PREV_WORD:
          case BRL_CMD_SPEAK_NEXT_WORD:
          case BRL_CMD_SPEAK_PREV_LINE:
          case BRL_CMD_SPEAK_NEXT_LINE:
            return 1;

          case BRL_CMD_FWINLT:
          case BRL_CMD_FWINRT:
            if (prefs.autorepeatPanning) return 1;

          default:
            break;
        }
        break;
    }
  }

  return 0;
}

KeyTableState
processKeyEvent (
  KeyTable *table, unsigned char context,
  KeyGroup keyGroup, KeyNumber keyNumber, int press
) {
  const KeyValue keyValue = {
    .group = keyGroup,
    .number = keyNumber
  };

  KeyTableState state = KTS_UNBOUND;
  int command = EOF;
  const HotkeyEntry *hotkey;

  if (press && !table->pressedKeys.count) {
    table->context.current = table->context.next;
    table->context.next = table->context.persistent;
  }
  if (context == KTB_CTX_DEFAULT) context = table->context.current;

  if (!(hotkey = findHotkeyEntry(table, context, &keyValue))) {
    const KeyValue anyKey = {
      .group = keyValue.group,
      .number = KTB_KEY_ANY
    };

    hotkey = findHotkeyEntry(table, context, &anyKey);
  }

  if (hotkey) {
    const BoundCommand *cmd = press? &hotkey->pressCommand: &hotkey->releaseCommand;

    if (cmd->value != BRL_CMD_NOOP) processCommand(table, (command = cmd->value));
    state = KTS_HOTKEY;
  } else {
    int isImmediate = 1;
    unsigned int keyPosition;
    int wasPressed = findPressedKey(table, &keyValue, &keyPosition);
    if (wasPressed) removePressedKey(table, keyPosition);

    if (press) {
      int isIncomplete = 0;
      const KeyBinding *binding = findKeyBinding(table, context, &keyValue, &isIncomplete);
      int inserted = insertPressedKey(table, &keyValue, keyPosition);

      if (binding) {
        command = binding->primaryCommand.value;
      } else if ((binding = findKeyBinding(table, context, NULL, &isIncomplete))) {
        command = binding->primaryCommand.value;
        isImmediate = 0;
      } else if ((command = makeKeyboardCommand(table, context, 0)) != EOF) {
        isImmediate = 0;
      } else if (context == KTB_CTX_DEFAULT) {
        command = EOF;
      } else if (!inserted) {
        command = EOF;
      } else {
        removePressedKey(table, keyPosition);
        binding = findKeyBinding(table, KTB_CTX_DEFAULT, &keyValue, &isIncomplete);
        inserted = insertPressedKey(table, &keyValue, keyPosition);

        if (binding) {
          command = binding->primaryCommand.value;
        } else if ((binding = findKeyBinding(table, KTB_CTX_DEFAULT, NULL, &isIncomplete))) {
          command = binding->primaryCommand.value;
          isImmediate = 0;
        } else {
          command = EOF;
        }
      }

      if (prefs.brailleQuickSpace) {
        int cmd = makeKeyboardCommand(table, context, 1);

        if (cmd != EOF) {
          command = cmd;
          isImmediate = 0;
        }
      }

      if (command == EOF) {
        command = BRL_CMD_NOOP;
        if (isIncomplete) state = KTS_MODIFIERS;
      } else {
        state = KTS_COMMAND;
      }

      if (!wasPressed) {
        int secondaryCommand = BRL_CMD_NOOP;

        resetLongPressData(table);
        table->release.command = BRL_CMD_NOOP;

        if (binding) {
          addCommandArguments(table, &command, binding->primaryCommand.entry, binding);

          secondaryCommand = binding->secondaryCommand.value;
          addCommandArguments(table, &secondaryCommand, binding->secondaryCommand.entry, binding);
        }

        if (context == KTB_CTX_WAITING) {
          table->release.command = BRL_CMD_NOOP;
        } else {
          if (secondaryCommand == BRL_CMD_NOOP) {
            if (isRepeatableCommand(command)) {
              secondaryCommand = command;
            }
          }

          if (isImmediate) {
            table->release.command = BRL_CMD_NOOP;
          } else {
            table->release.command = command;
            command = BRL_CMD_NOOP;
          }

          if (secondaryCommand != BRL_CMD_NOOP) {
            table->longPress.command = secondaryCommand;
            table->longPress.repeat = isRepeatableCommand(secondaryCommand);

            table->longPress.keyAction = "long";
            table->longPress.keyContext = context;
            table->longPress.keyValue = keyValue;

            setLongPressAlarm(table, prefs.longPressTime);
          }
        }

        processCommand(table, command);
      }
    } else {
      resetLongPressData(table);

      if (prefs.firstRelease || (table->pressedKeys.count == 0)) {
        int *cmd = &table->release.command;

        if (*cmd != BRL_CMD_NOOP) {
          processCommand(table, (command = *cmd));
          *cmd = BRL_CMD_NOOP;
        }
      }
    }

    setAutoreleaseAlarm(table);
  }

  logKeyEvent(table, (press? "press": "release"), context, &keyValue, command);
  return state;
}

void
releaseAllKeys (KeyTable *table) {
  while (table->pressedKeys.count) {
    const KeyValue *kv = &table->pressedKeys.table[0];
    processKeyEvent(table, KTB_CTX_DEFAULT, kv->group, kv->number, 0);
  }
}

void
setKeyTableLogLabel (KeyTable *table, const char *label) {
  table->options.logLabel = label;
}

void
setLogKeyEventsFlag (KeyTable *table, const unsigned char *flag) {
  table->options.logKeyEventsFlag = flag;
}

void
setKeyboardEnabledFlag (KeyTable *table, const unsigned char *flag) {
  table->options.keyboardEnabledFlag = flag;
}

void
getKeyGroupCommands (KeyTable *table, KeyGroup group, int *commands, unsigned int size) {
  const KeyContext *ctx = getKeyContext(table, KTB_CTX_DEFAULT);

  if (ctx) {
    unsigned int i;

    for (i=0; i<size; i+=1) {
      commands[i] = BRL_CMD_NOOP;
    }

    for (i=0; i<ctx->keyBindings.count; i+=1) {
      const KeyBinding *binding = &ctx->keyBindings.table[i];
      const KeyCombination *combination = &binding->keyCombination;
      const KeyValue *key;

      if (combination->flags & KCF_IMMEDIATE_KEY) {
        if (combination->modifierCount != 0) continue;
        key = &combination->immediateKey;
      } else {
        if (combination->modifierCount != 1) continue;
        key = &combination->modifierKeys[0];
      }

      if (key->group == group) {
        if (key->number != KTB_KEY_ANY) {
          if (key->number < size) {
            int command = binding->primaryCommand.value;

            if (command != BRL_CMD_NOOP) {
              commands[key->number] = command;
            }
          }
        }
      }
    }
  }
}
