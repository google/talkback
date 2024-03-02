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
#include <string.h>

#include "log.h"
#include "strfmt.h"
#include "alert.h"
#include "prefs.h"
#include "unicode.h"
#include "ktb.h"
#include "ktb_internal.h"
#include "ktb_inspect.h"
#include "brl_types.h"
#include "brl_cmds.h"
#include "cmd.h"
#include "cmd_enqueue.h"
#include "async_alarm.h"
#include "hostcmd.h"

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
isInputKey (BRL_Key key) {
  switch (key) {
    case BRL_KEY_BACKSPACE:
    case BRL_KEY_DELETE:
    case BRL_KEY_ESCAPE:
    case BRL_KEY_TAB:
    case BRL_KEY_ENTER:
      return 1;

    default:
      return 0;
  }
}

static int
processCommand (KeyTable *table, int command) {
  int isInput = 0;

  switch (command) {
    default: {
      int arg = command & BRL_MSK_ARG;

      switch (command & BRL_MSK_BLK) {
        case BRL_CMD_BLK(CONTEXT): {
          unsigned char context = KTB_CTX_DEFAULT + arg;
          const KeyContext *ctx = getKeyContext(table, context);

          if (ctx) {
            table->context.next = context;

            if (isTemporaryKeyContext(table, ctx)) {
              command = BRL_CMD_ALERT(CONTEXT_TEMPORARY);
            } else {
              table->context.persistent = context;
              command =
                (context == KTB_CTX_DEFAULT)?
                BRL_CMD_ALERT(CONTEXT_DEFAULT):
                BRL_CMD_ALERT(CONTEXT_PERSISTENT);
            }

            if (prefs.speakKeyContext) {
              if (ctx->title) {
                speakAlertText(ctx->title);
              } else {
                const wchar_t *name = ctx->name;
                const wchar_t *from = name;

                wchar_t text[(wcslen(name) * 2) + 1];
                wchar_t *to = text;

                while (*from) {
                  wchar_t character = *from++;

                  if (iswupper(character)) {
                    if (to != text) {
                      *to++ = WC_C(' ');
                    }
                  }

                  *to++ = character;
                }

                *to = 0;
                speakAlertText(text);
              }
            } else if (!enqueueCommand(command)) {
              return 0;
            }

            command = BRL_CMD_NOOP;
          }

          break;
        }

        case BRL_CMD_BLK(MACRO): {
          if (arg < table->commandMacros.count) {
            const CommandMacro *macro = &table->commandMacros.table[arg];
            const BoundCommand *cmd = macro->commands;
            const BoundCommand *end = cmd + macro->count;

            while (cmd < end) {
              if (!processCommand(table, (cmd++)->value)) return 0;
            }
          }

          command = BRL_CMD_NOOP;
          break;
        }

        case BRL_CMD_BLK(HOSTCMD): {
          if (arg < table->hostCommands.count) {
            const HostCommand *hc = &table->hostCommands.table[arg];

            HostCommandOptions options;
            initializeHostCommandOptions(&options);
            options.asynchronous = 1;

            runHostCommand((const char *const *)hc->arguments, &options);
          }

          command = BRL_CMD_NOOP;
          break;
        }

        case BRL_CMD_BLK(PASSDOTS):
          switch (prefs.brailleTypingMode) {
            case BRL_TYPING_DOTS: {
              wchar_t character = UNICODE_BRAILLE_ROW | arg;
              int flags = command & BRL_MSK_FLG;
              command = BRL_CMD_BLK(PASSCHAR) | BRL_ARG_SET(character) | flags;
              break;
            }
          }

          isInput = 1;
          break;

        case BRL_CMD_BLK(PASSCHAR):
          isInput = 1;
          break;

        case BRL_CMD_BLK(PASSKEY):
          if (isInputKey(arg)) isInput = 1;
          break;

        default:
          break;
      }

      break;
    }
  }

  if (isInput) {
    if (table->options.keyboardEnabledFlag && !*table->options.keyboardEnabledFlag) {
      command = BRL_CMD_ALERT(COMMAND_REJECTED);
    }
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
  asyncNewRelativeAlarm(&table->longPress.alarm, PREFS2MSECS(when),
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

static int
getPressedKeysCommand (
  KeyTable *table, unsigned char context,
  const KeyValue *key, unsigned int position,
  const KeyBinding **binding, int *wasInserted,
  int *isIncomplete, int *isImmediate
) {
  *binding = findKeyBinding(table, context, key, isIncomplete);
  *wasInserted = insertPressedKey(table, key, position);

  if (*binding) {
    *isImmediate = 1;
    return (*binding)->primaryCommand.value;
  }

  if ((*binding = findKeyBinding(table, context, NULL, isIncomplete))) {
    *isImmediate = 0;
    return (*binding)->primaryCommand.value;
  }

  return EOF;
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
      const KeyBinding *binding;
      int isIncomplete = 0;
      int wasInserted;

      int command = getPressedKeysCommand(
        table, context,
        &keyValue, keyPosition,
        &binding, &wasInserted,
        &isIncomplete, &isImmediate
      );

      if (command == EOF) {
        if ((command = makeKeyboardCommand(table, context, 0)) != EOF) {
          isImmediate = 0;
        }       ;
      }

      if (command == EOF) {
        int tryDefaultContext = wasInserted && (context != KTB_CTX_DEFAULT);

        if (tryDefaultContext) {
          const KeyContext *ctx = getKeyContext(table, context);

          if (ctx && ctx->isIsolated) {
            tryDefaultContext = 0;
            command = BRL_CMD_NOOP;
          }
        }

        if (tryDefaultContext) {
          removePressedKey(table, keyPosition);

          command = getPressedKeysCommand(
            table, KTB_CTX_DEFAULT,
            &keyValue, keyPosition,
            &binding, &wasInserted,
            &isIncomplete, &isImmediate
          );

          if (command != EOF) {
            switch (command & BRL_MSK_BLK) {
              case BRL_CMD_BLK(PASSDOTS): {
                command = BRL_CMD_ALERT(COMMAND_REJECTED);
                break;
              }
            }
          }
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

      if (prefs.onFirstRelease || (table->pressedKeys.count == 0)) {
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

typedef struct {
  int *array;
  unsigned int size;
  unsigned int count;
} AddCommandData;

static int
addCommand (AddCommandData *acd, int command) {
  if (command == EOF) return 1;
  int blk = command & BRL_MSK_BLK;
  command &= blk? BRL_MSK_BLK: BRL_MSK_CMD;
  if (command == BRL_CMD_NOOP) return 1;

  if (acd->count == acd->size) {
    unsigned int newSize = acd->size? (acd->size << 1): 0X100;
    int *newArray = realloc(acd->array, ARRAY_SIZE(newArray, newSize));

    if (!newArray) {
      logMallocError();
      return 0;
    }

    acd->array = newArray;
    acd->size = newSize;
  }

  acd->array[acd->count++] = command;
  return 1;
}

static int
addBoundCommand (AddCommandData *acd, const BoundCommand *cmd) {
  return addCommand(acd, cmd->value);
}

static int
sortCommands (const void *element1, const void *element2) {
  const int *command1 = element1;
  const int *command2 = element2;

  if (*command1 < *command2) return -1;
  if (*command1 > *command2) return 1;
  return 0;
}

int *
getBoundCommands (KeyTable *table, unsigned int *count) {
  AddCommandData acd = {
    .array = NULL,
    .size = 0,
    .count = 0
  };

  for (unsigned int context=0; context<table->keyContexts.count; context+=1) {
    const KeyContext *ctx = getKeyContext(table, context);

    if (ctx) {
      {
        const KeyBinding *binding = ctx->keyBindings.table;
        const KeyBinding *end = binding + ctx->keyBindings.count;

        while (binding < end) {
          if (!addBoundCommand(&acd, &binding->primaryCommand)) goto error;
          if (!addBoundCommand(&acd, &binding->secondaryCommand)) goto error;
          binding += 1;
        }
      }

      {
        const HotkeyEntry *hotkey = ctx->hotkeys.table;
        const HotkeyEntry *end = hotkey + ctx->hotkeys.count;

        while (hotkey < end) {
          if (!addBoundCommand(&acd, &hotkey->pressCommand)) goto error;
          if (!addBoundCommand(&acd, &hotkey->releaseCommand)) goto error;
          hotkey += 1;
        }
      }

      if (ctx->mappedKeys.count) {
        if (!addCommand(&acd, BRL_CMD_BLK(PASSDOTS))) {
          goto error;
        }
      }
    }
  }

  if (acd.count > 1) {
    qsort(acd.array, acd.count, sizeof(*acd.array), sortCommands);

    int *to = acd.array;
    const int *from = to;
    const int *end = from + acd.count;

    while (from < end) {
      if ((from == acd.array) || (*from != *(from - 1))) {
        if (from != to) *to = *from;
        to += 1;
      }

      from += 1;
    }

    acd.count = to - acd.array;
  }

  {
    int *newArray = realloc(acd.array, ARRAY_SIZE(newArray, acd.count));
    if (newArray) acd.array = newArray;
  }

  *count = acd.count;
  return acd.array;

error:
  if (acd.array) free(acd.array);
  return NULL;
}
