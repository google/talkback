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
#include <ctype.h>

#include "log.h"
#include "file.h"
#include "datafile.h"
#include "utf8.h"
#include "cmd.h"
#include "brl_cmds.h"
#include "ktb.h"
#include "ktb_internal.h"
#include "program.h"

const KeyboardFunction keyboardFunctionTable[] = {
  {.name="dot1", .bit=BRL_DOT1},
  {.name="dot2", .bit=BRL_DOT2},
  {.name="dot3", .bit=BRL_DOT3},
  {.name="dot4", .bit=BRL_DOT4},
  {.name="dot5", .bit=BRL_DOT5},
  {.name="dot6", .bit=BRL_DOT6},
  {.name="dot7", .bit=BRL_DOT7},
  {.name="dot8", .bit=BRL_DOT8},
  {.name="space", .bit=BRL_DOTC},
  {.name="shift", .bit=BRL_FLG_INPUT_SHIFT},
  {.name="upper", .bit=BRL_FLG_INPUT_UPPER},
  {.name="control", .bit=BRL_FLG_INPUT_CONTROL},
  {.name="meta", .bit=BRL_FLG_INPUT_META},
  {.name="altgr", .bit=BRL_FLG_INPUT_ALTGR},
  {.name="gui", .bit=BRL_FLG_INPUT_GUI}
};
unsigned char keyboardFunctionCount = ARRAY_COUNT(keyboardFunctionTable);

typedef struct {
  const char *file;
  KeyTable *table;

  const CommandEntry **commandTable;
  unsigned int commandCount;

  BoundCommand nullBoundCommand;

  unsigned char context;
  unsigned hideRequested:1;
  unsigned hideInherited:1;
} KeyTableData;

void
copyKeyValues (KeyValue *target, const KeyValue *source, unsigned int count) {
  memcpy(target, source, count*sizeof(*target));
}

int
compareKeyValues (const KeyValue *value1, const KeyValue *value2) {
  if (value1->group < value2->group) return -1;
  if (value1->group > value2->group) return 1;

  if (value1->number < value2->number) return -1;
  if (value1->number > value2->number) return 1;

  return 0;
}

static int
compareKeyArrays (
  unsigned int count1, const KeyValue *array1,
  unsigned int count2, const KeyValue *array2
) {
  if (count1 < count2) return -1;
  if (count1 > count2) return 1;
  return memcmp(array1, array2, count1*sizeof(*array1));
}

int
findKeyValue (
  const KeyValue *values, unsigned int count,
  const KeyValue *target, unsigned int *position
) {
  int first = 0;
  int last = count - 1;

  while (first <= last) {
    int current = (first + last) / 2;
    const KeyValue *value = &values[current];
    int relation = compareKeyValues(target, value);

    if (relation < 0) {
      last = current - 1;
    } else if (relation > 0) {
      first = current + 1;
    } else {
      *position = current;
      return 1;
    }
  }

  *position = first;
  return 0;
}

int
insertKeyValue (
  KeyValue **values, unsigned int *count, unsigned int *size,
  const KeyValue *value, unsigned int position
) {
  if (*count == *size) {
    unsigned int newSize = (*size)? (*size)<<1: 0X10;
    KeyValue *newValues = realloc(*values, ARRAY_SIZE(newValues, newSize));

    if (!newValues) {
      logMallocError();
      return 0;
    }

    *values = newValues;
    *size = newSize;
  }

  memmove(&(*values)[position+1], &(*values)[position],
          ((*count)++ - position) * sizeof(**values));
  (*values)[position] = *value;
  return 1;
}

void
removeKeyValue (KeyValue *values, unsigned int *count, unsigned int position) {
  memmove(&values[position], &values[position+1],
          (--*count - position) * sizeof(*values));
}

int
deleteKeyValue (KeyValue *values, unsigned int *count, const KeyValue *value) {
  unsigned int position;
  int found = findKeyValue(values, *count, value, &position);

  if (found) removeKeyValue(values, count, position);
  return found;
}

static inline int
hideBindings (const KeyTableData *ktd) {
  return ktd->hideRequested || ktd->hideInherited;
}

static KeyContext *
getKeyContext (KeyTableData *ktd, unsigned char context) {
  if (context >= ktd->table->keyContexts.count) {
    unsigned int newCount = context + 1;
    KeyContext *newTable = realloc(ktd->table->keyContexts.table, ARRAY_SIZE(newTable, newCount));

    if (!newTable) {
      logMallocError();
      return NULL;
    }
    ktd->table->keyContexts.table = newTable;

    while (ktd->table->keyContexts.count < newCount) {
      KeyContext *ctx = &ktd->table->keyContexts.table[ktd->table->keyContexts.count++];
      memset(ctx, 0, sizeof(*ctx));

      ctx->name = NULL;
      ctx->title = NULL;

      ctx->isSpecial = 0;
      ctx->isDefined = 0;
      ctx->isReferenced = 0;
      ctx->isIsolated = 0;

      ctx->keyBindings.table = NULL;
      ctx->keyBindings.size = 0;
      ctx->keyBindings.count = 0;

      ctx->hotkeys.table = NULL;
      ctx->hotkeys.size = 0;
      ctx->hotkeys.count = 0;

      ctx->mappedKeys.table = NULL;
      ctx->mappedKeys.size = 0;
      ctx->mappedKeys.count = 0;
      ctx->mappedKeys.superimpose = 0;
    }
  }

  return &ktd->table->keyContexts.table[context];
}

static inline KeyContext *
getCurrentKeyContext (KeyTableData *ktd) {
  return getKeyContext(ktd, ktd->context);
}

static int
setString (wchar_t **string, const wchar_t *characters, size_t length) {
  if (*string) free(*string);

  if (!(*string = malloc(ARRAY_SIZE(*string, length+1)))) {
    logMallocError();
    return 0;
  }

  wmemcpy(*string, characters, length);
  (*string)[length] = 0;
  return 1;
}

static int
setKeyContextName (KeyContext *ctx, const wchar_t *name, size_t length) {
  return setString(&ctx->name, name, length);
}

static int
setKeyContextTitle (KeyContext *ctx, const wchar_t *title, size_t length) {
  return setString(&ctx->title, title, length);
}

static int
findKeyContext (unsigned char *context, const wchar_t *name, int length, KeyTableData *ktd) {
  for (*context=0; *context<ktd->table->keyContexts.count; *context+=1) {
    KeyContext *ctx = &ktd->table->keyContexts.table[*context];

    if (ctx->name) {
      if (wcslen(ctx->name) == length) {
        if (wmemcmp(ctx->name, name, length) == 0) {
          return 1;
        }
      }
    }
  }

  {
    KeyContext *ctx = getKeyContext(ktd, *context);

    if (ctx) {
      if (setKeyContextName(ctx, name, length)) {
        return 1;
      }

      ktd->table->keyContexts.count -= 1;
    }
  }

  return 0;
}

static int
compareToName (const wchar_t *location1, int length1, const char *location2) {
  const wchar_t *end1 = location1 + length1;

  while (1) {
    if (location1 == end1) return *location2? -1: 0;
    if (!*location2) return 1;

    {
      wchar_t character1 = towlower(*location1);
      char character2 = tolower((unsigned char)*location2);

      if (character1 < character2) return -1;
      if (character1 > character2) return 1;
    }

    location1 += 1;
    location2 += 1;
  }
}

static int
sortKeyNames (const void *element1, const void *element2) {
  const KeyNameEntry *const *kne1 = element1;
  const KeyNameEntry *const *kne2 = element2;
  return strcasecmp((*kne1)->name, (*kne2)->name);
}

static int
searchKeyName (const void *target, const void *element) {
  const DataOperand *name = target;
  const KeyNameEntry *const *kne = element;
  return compareToName(name->characters, name->length, (*kne)->name);
}

static int
sortKeyValues (const void *element1, const void *element2) {
  const KeyNameEntry *const *kne1 = element1;
  const KeyNameEntry *const *kne2 = element2;

  {
    int result = compareKeyValues(&(*kne1)->value, &(*kne2)->value);
    if (result != 0) return result;
  }

  if (*kne1 < *kne2) return -1;
  if (*kne1 > *kne2) return 1;

  return 0;
}

typedef struct {
  unsigned int count;
} CountKeyNameData;

static int
countKeyName (const KeyNameEntry *kne, void *data) {
  if (kne) {
    CountKeyNameData *ckd = data;

    ckd->count += 1;
  }

  return 1;
}

typedef struct {
  const KeyNameEntry **kne;
} AddKeyNameData;

static int
addKeyName (const KeyNameEntry *kne, void *data) {
  if (kne) {
    AddKeyNameData *akd = data;

    *akd->kne++ = kne;
  }

  return 1;
}

static int
allocateKeyNameTable (KeyTableData *ktd, KEY_NAME_TABLES_REFERENCE keys) {
  {
    CountKeyNameData ckd = {
      .count = 0
    };

    forEachKeyName(keys, countKeyName, &ckd);
    ktd->table->keyNames.count = ckd.count;
  }

  if ((ktd->table->keyNames.table = malloc(ARRAY_SIZE(ktd->table->keyNames.table, ktd->table->keyNames.count)))) {
    {
      AddKeyNameData akd = {
        .kne = ktd->table->keyNames.table
      };

      forEachKeyName(keys, addKeyName, &akd);
    }

    qsort(ktd->table->keyNames.table, ktd->table->keyNames.count, sizeof(*ktd->table->keyNames.table), sortKeyNames);
    return 1;
  }

  return 0;
}

static const KeyNameEntry *const *
findKeyName (const wchar_t *characters, int length, KeyTableData *ktd) {
  const DataOperand name = {
    .characters = characters,
    .length = length
  };

  return bsearch(&name, ktd->table->keyNames.table, ktd->table->keyNames.count, sizeof(*ktd->table->keyNames.table), searchKeyName);
}

static int
parseKeyName (DataFile *file, KeyValue *value, const wchar_t *characters, int length, KeyTableData *ktd) {
  const wchar_t *suffix = wmemchr(characters, WC_C('.'), length);
  int prefixLength;
  int suffixLength;

  if (suffix) {
    if (!(prefixLength = suffix - characters)) {
      reportDataError(file, "missing key group name: %.*" PRIws, length, characters);
      return 0;
    }

    if (!(suffixLength = (characters + length) - ++suffix)) {
      reportDataError(file, "missing key number: %.*" PRIws, length, characters);
      return 0;
    }
  } else {
    prefixLength = length;
    suffixLength = 0;
  }

  {
    const KeyNameEntry *const *kne = findKeyName(characters, prefixLength, ktd);

    if (!kne) {
      reportDataError(file, "unknown key name: %.*" PRIws, prefixLength, characters);
      return 0;
    }

    *value = (*kne)->value;
  }

  if (suffix) {
    int ok = 0;
    int number;

    if (isNumber(&number, suffix, suffixLength))
      if (number > 0)
        if (--number <= KTB_KEY_MAX)
          ok = 1;

    if (!ok) {
      reportDataError(file, "invalid key number: %.*" PRIws, suffixLength, suffix);
      return 0;
    }

    if (value->number != KTB_KEY_ANY) {
      reportDataError(file, "not a key group: %.*" PRIws, prefixLength, characters);
      return 0;
    }

    value->number = number;
  }

  return 1;
}

static int
getKeyOperand (DataFile *file, KeyValue *value, KeyTableData *ktd) {
  DataString name;

  if (getDataString(file, &name, 1, "key name")) {
    if (parseKeyName(file, value, name.characters, name.length, ktd)) {
      return 1;
    }
  }

  return 0;
}

static int
newModifierPosition (const KeyCombination *combination, const KeyValue *modifier, unsigned int *position) {
  int found = findKeyValue(combination->modifierKeys, combination->modifierCount, modifier, position);
  return found && (modifier->number != KTB_KEY_ANY);
}

static int
insertModifier (DataFile *file, KeyCombination *combination, unsigned int position, const KeyValue *value) {
  if (combination->modifierCount == MAX_MODIFIERS_PER_COMBINATION) {
    reportDataError(file, "too many modifier keys");
    return 0;
  }

  {
    int index = combination->modifierCount;

    while (index--) {
      if (index >= position) {
        combination->modifierKeys[index+1] = combination->modifierKeys[index];
      }

      if (combination->modifierPositions[index] >= position) {
        combination->modifierPositions[index] += 1;
      }
    }
  }

  combination->modifierKeys[position] = *value;
  combination->modifierPositions[combination->modifierCount++] = position;
  return 1;
}

static int
parseKeyCombination (DataFile *file, KeyCombination *combination, const wchar_t *characters, int length, KeyTableData *ktd) {
  KeyValue value;

  memset(combination, 0, sizeof(*combination));
  combination->modifierCount = 0;

  while (1) {
    const wchar_t *end = wmemchr(characters, WC_C('+'), length);
    if (!end) break;

    {
      int count = end - characters;

      if (!count) {
        reportDataError(file, "missing modifier key");
        return 0;
      }
      if (!parseKeyName(file, &value, characters, count, ktd)) return 0;

      {
        unsigned int position;

        if (newModifierPosition(combination, &value, &position)) {
          reportDataError(file, "duplicate modifier key: %.*" PRIws, count, characters);
          return 0;
        }

        if (!insertModifier(file, combination, position, &value)) return 0;
        if (value.number == KTB_KEY_ANY) combination->anyKeyCount += 1;
      }

      length -= count + 1;
      characters = end + 1;
    }
  }

  if (length) {
    if (*characters == WC_C('!')) {
      characters += 1, length -= 1;
      combination->flags |= KCF_IMMEDIATE_KEY;
    }
  }

  if (!length) {
    reportDataError(file, "missing key");
    return 0;
  }
  if (!parseKeyName(file, &value, characters, length, ktd)) return 0;

  {
    unsigned int position;

    if (newModifierPosition(combination, &value, &position)) {
      reportDataError(file, "duplicate key: %.*" PRIws, length, characters);
      return 0;
    }

    if (combination->flags & KCF_IMMEDIATE_KEY) {
      combination->immediateKey = value;
    } else if (!insertModifier(file, combination, position, &value)) {
      return 0;
    }
    if (value.number == KTB_KEY_ANY) combination->anyKeyCount += 1;
  }

  return 1;
}

static int
getKeysOperand (DataFile *file, KeyCombination *combination, KeyTableData *ktd) {
  DataString names;

  if (getDataString(file, &names, 1, "key combination")) {
    if (parseKeyCombination(file, combination, names.characters, names.length, ktd)) return 1;
  }

  return 0;
}

static int
sortKeyboardFunctionNames (const void *element1, const void *element2) {
  const KeyboardFunction *const *kbf1 = element1;
  const KeyboardFunction *const *kbf2 = element2;
  return strcasecmp((*kbf1)->name, (*kbf2)->name);
}

static int
searchKeyboardFunctionName (const void *target, const void *element) {
  const DataOperand *name = target;
  const KeyboardFunction *const *kbf = element;
  return compareToName(name->characters, name->length, (*kbf)->name);
}

static int
parseKeyboardFunctionName (DataFile *file, const KeyboardFunction **keyboardFunction, const wchar_t *characters, int length, KeyTableData *ktd) {
  static const KeyboardFunction **sortedKeyboardFunctions = NULL;

  if (!sortedKeyboardFunctions) {
    const KeyboardFunction **newTable = malloc(ARRAY_SIZE(newTable, keyboardFunctionCount));

    if (!newTable) {
      logMallocError();
      return 0;
    }

    {
      const KeyboardFunction *source = keyboardFunctionTable;
      const KeyboardFunction **target = newTable;
      unsigned int count = keyboardFunctionCount;

      do {
        *target++ = source++;
      } while (--count);

      qsort(newTable, keyboardFunctionCount, sizeof(*newTable), sortKeyboardFunctionNames);
    }

    sortedKeyboardFunctions = newTable;
    registerProgramMemory("sorted-keyboard-functions", &sortedKeyboardFunctions);
  }

  {
    const DataOperand name = {
      .characters = characters,
      .length = length
    };
    const KeyboardFunction *const *kbf = bsearch(&name, sortedKeyboardFunctions, keyboardFunctionCount, sizeof(*sortedKeyboardFunctions), searchKeyboardFunctionName);

    if (kbf) {
      *keyboardFunction = *kbf;
      return 1;
    }
  }

  reportDataError(file, "unknown keyboard function: %.*" PRIws, length, characters);
  return 0;
}

static int
getKeyboardFunctionOperand (DataFile *file, const KeyboardFunction **keyboardFunction, KeyTableData *ktd) {
  DataOperand name;

  if (getDataOperand(file, &name, "keyboard function name")) {
    if (parseKeyboardFunctionName(file, keyboardFunction, name.characters, name.length, ktd)) return 1;
  }

  return 0;
}

static int
sortCommandNames (const void *element1, const void *element2) {
  const CommandEntry *const *cmd1 = element1;
  const CommandEntry *const *cmd2 = element2;
  return strcasecmp((*cmd1)->name, (*cmd2)->name);
}

static int
searchCommandName (const void *target, const void *element) {
  const DataOperand *name = target;
  const CommandEntry *const *cmd = element;
  return compareToName(name->characters, name->length, (*cmd)->name);
}

static int
allocateCommandTable (KeyTableData *ktd) {
  {
    const CommandEntry *command = commandTable;

    ktd->commandCount = 0;
    while (command->name) {
      ktd->commandCount += 1;
      command += 1;
    }
  }

  if ((ktd->commandTable = malloc(ktd->commandCount * sizeof(*ktd->commandTable)))) {
    {
      const CommandEntry *command = commandTable;
      const CommandEntry **address = ktd->commandTable;
      while (command->name) *address++ = command++;
    }

    qsort(ktd->commandTable, ktd->commandCount, sizeof(*ktd->commandTable), sortCommandNames);
    return 1;
  }

  return 0;
}

static int
applyCommandModifier (int *command, const CommandModifierEntry *modifiers, const DataOperand *name) {
  const CommandModifierEntry *modifier = modifiers;

  while (modifier->name) {
    if (!(*command & modifier->bit)) {
      if (compareToName(name->characters, name->length, modifier->name) == 0) {
        *command |= modifier->bit;
        return 1;
      }
    }

    modifier += 1;
  }

  return 0;
}

static int
parseCommandOperand (DataFile *file, BoundCommand *cmd, const wchar_t *characters, int length, KeyTableData *ktd) {
  int offsetDone = 0;
  int unicodeDone = 0;

  const wchar_t *end = wmemchr(characters, WC_C('+'), length);
  const CommandEntry *const *command;

  {
    const DataOperand name = {
      .characters = characters,
      .length = end? end-characters: length
    };

    if (!name.length) {
      reportDataError(file, "missing command name");
      return 0;
    }

    if (!(command = bsearch(&name, ktd->commandTable, ktd->commandCount, sizeof(*ktd->commandTable), searchCommandName))) {
      reportDataError(file, "unknown command name: %.*" PRIws, name.length, name.characters);
      return 0;
    }
  }

  cmd->value = (cmd->entry = *command)->code;

  while (end) {
    DataOperand modifier;

    if ((modifier.length = (length -= (end - characters) + 1))) {
      modifier.characters = characters = end + 1;
      end = wmemchr(characters, WC_C('+'), length);
      if (end) modifier.length = end - characters;
    }

    if (!modifier.length) {
      reportDataError(file, "missing command modifier");
      return 0;
    }

    if ((*command)->isToggle && !(cmd->value & BRL_FLG_TOGGLE_MASK)) {
      if (applyCommandModifier(&cmd->value, commandModifierTable_toggle, &modifier)) continue;
    }

    if ((*command)->isMotion) {
      if (applyCommandModifier(&cmd->value, commandModifierTable_motion, &modifier)) continue;
    }

    if ((*command)->isRow) {
      if (applyCommandModifier(&cmd->value, commandModifierTable_row, &modifier)) continue;
    }

    if ((*command)->isVertical) {
      if (applyCommandModifier(&cmd->value, commandModifierTable_vertical, &modifier)) continue;
    }

    if ((*command)->isInput) {
      if (applyCommandModifier(&cmd->value, commandModifierTable_input, &modifier)) continue;
    }

    if ((*command)->isCharacter) {
      if (applyCommandModifier(&cmd->value, commandModifierTable_character, &modifier)) continue;

      if (!unicodeDone) {
        if (modifier.length == 1) {
          cmd->value |= BRL_ARG_SET(modifier.characters[0]);
          unicodeDone = 1;
          continue;
        }
      }
    }

    if ((*command)->isBraille) {
      if (applyCommandModifier(&cmd->value, commandModifierTable_braille, &modifier)) continue;
      if (applyCommandModifier(&cmd->value, commandModifierTable_character, &modifier)) continue;
    }

    if ((*command)->isKeyboard) {
      if (applyCommandModifier(&cmd->value, commandModifierTable_keyboard, &modifier)) continue;
    }

    if (!offsetDone) {
      if ((*command)->code == BRL_CMD_BLK(CONTEXT)) {
        unsigned char context;

        if (findKeyContext(&context, modifier.characters, modifier.length, ktd)) {
          KeyContext *ctx = getKeyContext(ktd, context);
          if (!ctx) return 0;

          if (ctx->isSpecial) {
            reportDataError(file, "invalid target context: %"PRIws, ctx->name);
          } else {
            ctx->isReferenced = 1;
            cmd->value += context - KTB_CTX_DEFAULT;
          }

          offsetDone = 1;
          continue;
        }
      } else if (((*command)->isOffset || (*command)->isColumn) || (*command)->isRow) {
        int maximum = BRL_MSK_ARG - ((*command)->code & BRL_MSK_ARG);
        int offset;

        if (isNumber(&offset, modifier.characters, modifier.length)) {
          if ((offset >= 0) && (offset <= maximum)) {
            cmd->value += offset;
            offsetDone = 1;
            continue;
          }
        }
      }
    }

    reportDataError(file, "unknown command modifier: %.*" PRIws, modifier.length, modifier.characters);
    return 0;
  }

  return 1;
}

static int
getCommandsOperand (DataFile *file, BoundCommand **cmds, KeyTableData *ktd) {
  DataString commands;

  if (getDataString(file, &commands, 1, "command")) {
    const wchar_t *characters = commands.characters;
    unsigned int length = commands.length;
    int first = 1;

    while (1) {
      int count;

      BoundCommand *cmd = *cmds++;
      if (!cmd) break;

      if (first) {
        first = 0;
      } else if (length) {
        characters += 1;
        length -= 1;
      }

      {
        const wchar_t *end = wmemchr(characters, WC_C(':'), length);
        count = end? (end - characters): length;
      }

      if (!count) {
        *cmd = ktd->nullBoundCommand;
      } else if (!parseCommandOperand(file, cmd, characters, count, ktd)) {
        return 0;
      }

      characters += count;
      length -= count;
    }

    if (!length) return 1;
    reportDataError(file, "too many commands: %.*" PRIws, length, characters);
  }

  return 0;
}

static int
getCommandOperand (DataFile *file, BoundCommand *cmd, KeyTableData *ktd) {
  BoundCommand *cmds[] = {cmd, NULL};

  return getCommandsOperand(file, cmds, ktd);
}

static int
compareKeyCombinations (const KeyCombination *combination1, const KeyCombination *combination2) {
  if (combination1->flags & KCF_IMMEDIATE_KEY) {
    if (combination2->flags & KCF_IMMEDIATE_KEY) {
      int relation = compareKeyValues(&combination1->immediateKey, &combination2->immediateKey);
      if (relation) return relation;
    } else {
      return -1;
    }
  } else if (combination2->flags & KCF_IMMEDIATE_KEY) {
    return 1;
  }

  return compareKeyArrays(combination1->modifierCount, combination1->modifierKeys,
                          combination2->modifierCount, combination2->modifierKeys);
}

int
compareKeyBindings (const KeyBinding *binding1, const KeyBinding *binding2) {
  return compareKeyCombinations(&binding1->keyCombination, &binding2->keyCombination);
}

static int
findKeyBinding (
  const KeyBinding *bindings, unsigned int count,
  const KeyBinding *target, unsigned int *position
) {
  int first = 0;
  int last = count - 1;

  while (first <= last) {
    int current = (first + last) / 2;
    const KeyBinding *binding = &bindings[current];
    int relation = compareKeyBindings(target, binding);

    if (relation < 0) {
      last = current - 1;
    } else if (relation > 0) {
      first = current + 1;
    } else {
      *position = current;
      return 1;
    }
  }

  *position = first;
  return 0;
}

static int
addKeyBinding (KeyContext *ctx, const KeyBinding *binding, int incomplete) {
  unsigned int position;
  int found = findKeyBinding(ctx->keyBindings.table, ctx->keyBindings.count, binding, &position);

  if (!found) {
    if (ctx->keyBindings.count == ctx->keyBindings.size) {
      unsigned int newSize = ctx->keyBindings.size? ctx->keyBindings.size<<1: 0X10;
      KeyBinding *newTable = realloc(ctx->keyBindings.table, ARRAY_SIZE(newTable, newSize));

      if (!newTable) {
        logMallocError();
        return 0;
      }

      ctx->keyBindings.table = newTable;
      ctx->keyBindings.size = newSize;
    }

    memmove(&ctx->keyBindings.table[position+1],
            &ctx->keyBindings.table[position],
            (ctx->keyBindings.count++ - position) * sizeof(*binding));
  } else if (incomplete) {
    return 1;
  }

  {
    KeyBinding *kb = &ctx->keyBindings.table[position];
    *kb = *binding;
    if (found) kb->flags |= KBF_DUPLICATE;
  }

  return 1;
}

static void
initializeKeyBinding (KeyBinding *binding, KeyTableData *ktd) {
  memset(binding, 0, sizeof(*binding));
  binding->primaryCommand = ktd->nullBoundCommand;
  binding->secondaryCommand = ktd->nullBoundCommand;
  if (hideBindings(ktd)) binding->flags |= KBF_HIDDEN;
}

int
compareHotkeyEntries (const HotkeyEntry *hotkey1, const HotkeyEntry *hotkey2) {
  return compareKeyValues(&hotkey1->keyValue, &hotkey2->keyValue);
}

static int
findHotkeyEntry (
  const HotkeyEntry *hotkeyEntries, unsigned int count,
  const HotkeyEntry *target, unsigned int *position
) {
  int first = 0;
  int last = count - 1;

  while (first <= last) {
    int current = (first + last) / 2;
    const HotkeyEntry *hotkey = &hotkeyEntries[current];
    int relation = compareHotkeyEntries(target, hotkey);

    if (relation < 0) {
      last = current - 1;
    } else if (relation > 0) {
      first = current + 1;
    } else {
      *position = current;
      return 1;
    }
  }

  *position = first;
  return 0;
}

static int
addHotkey (KeyContext *ctx, const HotkeyEntry *hotkey) {
  unsigned int position;
  int found = findHotkeyEntry(ctx->hotkeys.table, ctx->hotkeys.count, hotkey, &position);

  if (!found) {
    if (ctx->hotkeys.count == ctx->hotkeys.size) {
      unsigned int newSize = ctx->hotkeys.size? ctx->hotkeys.size<<1: 0X8;
      HotkeyEntry *newTable = realloc(ctx->hotkeys.table, ARRAY_SIZE(newTable, newSize));

      if (!newTable) {
        logMallocError();
        return 0;
      }

      ctx->hotkeys.table = newTable;
      ctx->hotkeys.size = newSize;
    }

    memmove(&ctx->hotkeys.table[position+1],
            &ctx->hotkeys.table[position],
            (ctx->hotkeys.count++ - position) * sizeof(*hotkey));
  }

  {
    HotkeyEntry *hk = &ctx->hotkeys.table[position];
    *hk = *hotkey;
    if (found) hk->flags |= HKF_DUPLICATE;
  }

  return 1;
}

int
compareMappedKeyEntries (const MappedKeyEntry *map1, const MappedKeyEntry *map2) {
  return compareKeyValues(&map1->keyValue, &map2->keyValue);
}

static int
findMappedKeyEntry (
  const MappedKeyEntry *mappedKeyEntries, unsigned int count,
  const MappedKeyEntry *target, unsigned int *position
) {
  int first = 0;
  int last = count - 1;

  while (first <= last) {
    int current = (first + last) / 2;
    const MappedKeyEntry *map = &mappedKeyEntries[current];
    int relation = compareMappedKeyEntries(target, map);

    if (relation < 0) {
      last = current - 1;
    } else if (relation > 0) {
      first = current + 1;
    } else {
      *position = current;
      return 1;
    }
  }

  *position = first;
  return 0;
}

static int
addMappedKey (KeyContext *ctx, const MappedKeyEntry *map) {
  unsigned int position;
  int found = findMappedKeyEntry(ctx->mappedKeys.table, ctx->mappedKeys.count, map, &position);

  if (!found) {
    if (ctx->mappedKeys.count == ctx->mappedKeys.size) {
      unsigned int newSize = ctx->mappedKeys.size? ctx->mappedKeys.size<<1: 0X8;
      MappedKeyEntry *newTable = realloc(ctx->mappedKeys.table, ARRAY_SIZE(newTable, newSize));

      if (!newTable) {
        logMallocError();
        return 0;
      }

      ctx->mappedKeys.table = newTable;
      ctx->mappedKeys.size = newSize;
    }

    memmove(&ctx->mappedKeys.table[position+1],
            &ctx->mappedKeys.table[position],
            (ctx->mappedKeys.count++ - position) * sizeof(*map));
  }

  {
    MappedKeyEntry *mk = &ctx->mappedKeys.table[position];
    *mk = *map;
    if (found) mk->flags |= MKF_DUPLICATE;
  }

  return 1;
}

static DATA_OPERANDS_PROCESSOR(processBindOperands) {
  KeyTableData *ktd = data;

  KeyBinding binding;
  initializeKeyBinding(&binding, ktd);

  if (getKeysOperand(file, &binding.keyCombination, ktd)) {
    BoundCommand *cmds[] = {
      &binding.primaryCommand,
      &binding.secondaryCommand,
      NULL
    };

    if (getCommandsOperand(file, cmds, ktd)) {
      KeyContext *ctx = getCurrentKeyContext(ktd);

      if (ctx) {
        if (addKeyBinding(ctx, &binding, 0)) {
          return 1;
        }
      }

      return 0;
    }
  }

  return 1;
}

static DATA_OPERANDS_PROCESSOR(processContextOperands) {
  KeyTableData *ktd = data;
  DataString name;

  if (getDataString(file, &name, 1, "context name")) {
    if (findKeyContext(&ktd->context, name.characters, name.length, ktd)) {
      KeyContext *ctx = getCurrentKeyContext(ktd);

      if (ctx) {
        DataOperand title;

        ctx->isDefined = 1;

        if (getTextOperand(file, &title, NULL)) {
          if (ctx->title) {
            if ((title.length != wcslen(ctx->title)) ||
                (wmemcmp(title.characters, ctx->title, title.length) != 0)) {
              reportDataError(file, "context title redefined");
            }
          } else if (!setKeyContextTitle(ctx, title.characters, title.length)) {
            return 0;
          }
        }
      }
    }
  }

  return 1;
}

static DATA_OPERANDS_PROCESSOR(processHideOperands) {
  KeyTableData *ktd = data;
  DataString state;

  if (getDataString(file, &state, 1, "hide state")) {
    if (isKeyword(WS_C("on"), state.characters, state.length)) {
      ktd->hideRequested = 1;
    } else if (isKeyword(WS_C("off"), state.characters, state.length)) {
      ktd->hideRequested = 0;
    } else {
      reportDataError(file, "unknown hide state: %.*" PRIws, state.length, state.characters);
    }
  }

  return 1;
}

static DATA_OPERANDS_PROCESSOR(processHotkeyOperands) {
  KeyTableData *ktd = data;
  HotkeyEntry hotkey;

  memset(&hotkey, 0, sizeof(hotkey));
  if (hideBindings(ktd)) hotkey.flags |= HKF_HIDDEN;

  if (getKeyOperand(file, &hotkey.keyValue, ktd)) {
    if (getCommandOperand(file, &hotkey.pressCommand, ktd)) {
      if (getCommandOperand(file, &hotkey.releaseCommand, ktd)) {
        KeyContext *ctx = getCurrentKeyContext(ktd);

        if (ctx) {
          if (addHotkey(ctx, &hotkey)) {
            return 1;
          }
        }

        return 0;
      }
    }
  }

  return 1;
}

static DATA_CONDITION_TESTER(testKeyDefined) {
  return !!findKeyName(identifier->characters, identifier->length, data);
}

static int
processKeyTestOperands (DataFile *file, int not, void *data) {
  return processConditionOperands(file, testKeyDefined, not, "key name", data);
}

static DATA_OPERANDS_PROCESSOR(processIfKeyOperands) {
  return processKeyTestOperands(file, 0, data);
}

static DATA_OPERANDS_PROCESSOR(processIfNotKeyOperands) {
  return processKeyTestOperands(file, 1, data);
}

static DATA_CONDITION_TESTER(testPlatformName) {
  static const wchar_t *const platforms[] = {
#ifdef __ANDROID__
    WS_C("android"),
#endif /* __ANDROID__ */

#ifdef __APPLE__
    WS_C("apple"),
#endif /* __APPLE__ */

#ifdef __CYGWIN__
    WS_C("cygwin"),
#endif /* __CYGWIN__ */

#ifdef __MSDOS__
    WS_C("dos"),
#endif /* __MSDOS__ */

#ifdef GRUB_RUNTIME
    WS_C("grub"),
#endif /* GRUB_RUNTIME */

#ifdef __linux__
    WS_C("linux"),
#endif /* __linux__ */

#ifdef __MINGW32__
    WS_C("mingw32"),
#endif /* __MINGW32__ */

#ifdef __MINGW64__
    WS_C("mingw64"),
#endif /* __MINGW64__ */

#ifdef __OpenBSD__
    WS_C("openbsd"),
#endif /* __OpenBSD__ */

#ifdef __sun__
    WS_C("sun"),
#endif /* __sun__ */

#ifdef WINDOWS
    WS_C("windows"),
#endif /* WINDOWS */

    NULL
  };

  const wchar_t *const *platform = platforms;

  while (*platform) {
    if (identifier->length == wcslen(*platform)) {
      if (wcsncmp(*platform, identifier->characters, identifier->length) == 0) {
        return 1;
      }
    }

    platform += 1;
  }

  return 0;
}

static int
processPlatformTestOperands (DataFile *file, int not, void *data) {
  return processConditionOperands(file, testPlatformName, not, "platform name", data);
}

static DATA_OPERANDS_PROCESSOR(processIfPlatformOperands) {
  return processPlatformTestOperands(file, 0, data);
}

static DATA_OPERANDS_PROCESSOR(processIfNotPlatformOperands) {
  return processPlatformTestOperands(file, 1, data);
}

static DATA_OPERANDS_PROCESSOR(processIgnoreOperands) {
  KeyTableData *ktd = data;
  HotkeyEntry hotkey;

  memset(&hotkey, 0, sizeof(hotkey));
  if (hideBindings(ktd)) hotkey.flags |= HKF_HIDDEN;
  hotkey.pressCommand = hotkey.releaseCommand = ktd->nullBoundCommand;

  if (getKeyOperand(file, &hotkey.keyValue, ktd)) {
    KeyContext *ctx = getCurrentKeyContext(ktd);

    if (ctx) {
      if (addHotkey(ctx, &hotkey)) {
        return 1;
      }
    }

    return 0;
  }

  return 1;
}

static DATA_OPERANDS_PROCESSOR(processIncludeWrapper) {
  KeyTableData *ktd = data;
  int result;

  unsigned char context = ktd->context;
  unsigned int hideRequested = ktd->hideRequested;
  unsigned int hideInherited = ktd->hideInherited;

  if (ktd->hideRequested) ktd->hideInherited = 1;
  result = processIncludeOperands(file, data);

  ktd->context = context;
  ktd->hideRequested = hideRequested;
  ktd->hideInherited = hideInherited;
  return result;
}

static DATA_OPERANDS_PROCESSOR(processIsolatedOperands) {
  KeyTableData *ktd = data;
  KeyContext *ctx = getCurrentKeyContext(ktd);

  if (ctx) {
    if (!ctx->isIsolated) {
      ctx->isIsolated = 1;
    } else {
      reportDataError(file, "context already solated: %"PRIws, ctx->name);
    }
  }

  return 1;
}

static DATA_OPERANDS_PROCESSOR(processMacroOperands) {
  KeyTableData *ktd = data;
  KeyTable *table = ktd->table;

  KeyBinding binding;
  initializeKeyBinding(&binding, ktd);

  {
    BoundCommand *cmd = &binding.primaryCommand;
    cmd->value = BRL_CMD_BLK(MACRO);
    cmd->entry = findCommandEntry(cmd->value);
    cmd->value += table->commandMacros.count;
  }

  if (getKeysOperand(file, &binding.keyCombination, ktd)) {
    size_t limit = 100;
    BoundCommand commands[limit];
    size_t count = 0;

    while (findDataOperand(file, NULL)) {
      if (count == limit) {
        reportDataError(file, "command macro too large");
        return 1;
      }

      BoundCommand *command = &commands[count];
      if (!getCommandOperand(file, command, ktd)) return 1;
      count += 1;
    }

    if (count == 0) {
      reportDataError(file, "empty command macro");
    } else {
      if (table->commandMacros.count == table->commandMacros.size) {
        size_t newSize = table->commandMacros.size? table->commandMacros.size<<1: 4;
        CommandMacro *newTable = realloc(table->commandMacros.table, ARRAY_SIZE(table->commandMacros.table, newSize));

        if (!newTable) {
          logMallocError();
          return 0;
        }

        table->commandMacros.table = newTable;
        table->commandMacros.size = newSize;
      }

      CommandMacro *macro = &table->commandMacros.table[table->commandMacros.count];
      memset(macro, 0, sizeof(*macro));
      size_t size = ARRAY_SIZE(macro->commands, (macro->count = count));

      if ((macro->commands = malloc(size))) {
        memcpy(macro->commands, commands, size);
        KeyContext *ctx = getCurrentKeyContext(ktd);

        if (ctx) {
          if (addKeyBinding(ctx, &binding, 0)) {
            table->commandMacros.count += 1;
            return 1;
          }
        }

        free(macro->commands);
      }

      return 0;
    }
  }

  return 1;
}

static DATA_OPERANDS_PROCESSOR(processMapOperands) {
  KeyTableData *ktd = data;
  MappedKeyEntry map;

  memset(&map, 0, sizeof(map));
  if (hideBindings(ktd)) map.flags |= MKF_HIDDEN;

  if (getKeyOperand(file, &map.keyValue, ktd)) {
    if (map.keyValue.number != KTB_KEY_ANY) {
      if (getKeyboardFunctionOperand(file, &map.keyboardFunction, ktd)) {
        KeyContext *ctx = getCurrentKeyContext(ktd);

        if (ctx) {
          if (addMappedKey(ctx, &map)) {
            return 1;
          }
        }

        return 0;
      }
    } else {
      reportDataError(file, "cannot map a key group");
    }
  }

  return 1;
}

static DATA_OPERANDS_PROCESSOR(processNoteOperands) {
  KeyTableData *ktd = data;
  DataOperand operand;

  if (getTextOperand(file, &operand, "note text")) {
    if (!hideBindings(ktd)) {
      DataString string;

      if (parseDataString(file, &string, operand.characters, operand.length, 0)) {
        if (ktd->table->notes.count == ktd->table->notes.size) {
          unsigned int newSize = (ktd->table->notes.size == 0)? 8: (ktd->table->notes.size << 1);
          wchar_t **newTable = realloc(ktd->table->notes.table, ARRAY_SIZE(newTable, newSize));

          if (!newTable) {
            logMallocError();
            return 0;
          }

          ktd->table->notes.table = newTable;
          ktd->table->notes.size = newSize;
        }

        {
          wchar_t *noteString = malloc(ARRAY_SIZE(noteString, string.length+1));

          if (!noteString) {
            logMallocError();
            return 0;
          }

          wmemcpy(noteString, string.characters, string.length);
          noteString[string.length] = 0;

          ktd->table->notes.table[ktd->table->notes.count++] = noteString;
          return 1;
        }
      }
    }
  }

  return 1;
}

static DATA_OPERANDS_PROCESSOR(processRunOperands) {
  KeyTableData *ktd = data;
  KeyTable *table = ktd->table;
  int seriousFailure = 0;

  KeyBinding binding;
  initializeKeyBinding(&binding, ktd);

  {
    BoundCommand *cmd = &binding.primaryCommand;
    cmd->value = BRL_CMD_BLK(HOSTCMD);
    cmd->entry = findCommandEntry(cmd->value);
    cmd->value += table->hostCommands.count;
  }

  if (getKeysOperand(file, &binding.keyCombination, ktd)) {
    int allArgumentsParsed = 1;

    size_t limit = 100;
    char *arguments[limit];
    size_t count = 0;

    while (findDataOperand(file, NULL)) {
      if (count == limit) {
        reportDataError(file, "too many host command arguments");
        allArgumentsParsed = 0;
        break;
      }

      DataString argument;
      if (!getDataString(file, &argument, 0, "host command argument")) {
        allArgumentsParsed = 0;
        break;
      }

      if (!(arguments[count] = getUtf8FromWchars(argument.characters, argument.length, NULL))) {
        seriousFailure = 1;
        break;
      }

      count += 1;
    }

    if (allArgumentsParsed && !seriousFailure) {
      if (count == 0) {
        reportDataError(file, "host command name/path not specified");
      } else {
        seriousFailure = 1;

        if (table->hostCommands.count == table->hostCommands.size) {
          size_t newSize = table->hostCommands.size? table->hostCommands.size<<1: 4;
          HostCommand *newTable = realloc(table->hostCommands.table, ARRAY_SIZE(table->hostCommands.table, newSize));

          if (!newTable) {
            logMallocError();
            goto SERIOUS_FAILURE;
          }

          table->hostCommands.table = newTable;
          table->hostCommands.size = newSize;
        }

        HostCommand *hc = &table->hostCommands.table[table->hostCommands.count];
        memset(hc, 0, sizeof(*hc));
        size_t size = ARRAY_SIZE(hc->arguments, (hc->count = count));

        if ((hc->arguments = malloc(size + sizeof(*hc->arguments)))) {
          memcpy(hc->arguments, arguments, size);
          hc->arguments[hc->count] = NULL;
          KeyContext *ctx = getCurrentKeyContext(ktd);

          if (ctx) {
            if (addKeyBinding(ctx, &binding, 0)) {
              table->hostCommands.count += 1;
              return 1;
            }
          }

          free(hc->arguments);
        }
      }
    }

  SERIOUS_FAILURE:
    while (count > 0) free(arguments[--count]);
  }

  return !seriousFailure;
}

static DATA_OPERANDS_PROCESSOR(processSuperimposeOperands) {
  KeyTableData *ktd = data;

  {
    const KeyboardFunction *kbf;

    if (getKeyboardFunctionOperand(file, &kbf, ktd)) {
      KeyContext *ctx = getCurrentKeyContext(ktd);

      if (ctx) {
        ctx->mappedKeys.superimpose |= kbf->bit;
        return 1;
      }

      return 0;
    }
  }

  return 1;
}

static DATA_OPERANDS_PROCESSOR(processTitleOperands) {
  KeyTableData *ktd = data;
  DataOperand title;

  if (getTextOperand(file, &title, "title text")) {
    if (ktd->table->title) {
      reportDataError(file, "table title specified more than once");
    } else if (!(ktd->table->title = malloc(ARRAY_SIZE(ktd->table->title, title.length+1)))) {
      logMallocError();
      return 0;
    } else {
      wmemcpy(ktd->table->title, title.characters, title.length);
      ktd->table->title[title.length] = 0;
      return 1;
    }
  }

  return 1;
}

static DATA_OPERANDS_PROCESSOR(processKeyTableOperands) {
  BEGIN_DATA_DIRECTIVE_TABLE
    DATA_VARIABLE_DIRECTIVES,
    DATA_CONDITION_DIRECTIVES,
    {.name=WS_C("bind"), .processor=processBindOperands},
    {.name=WS_C("context"), .processor=processContextOperands},
    {.name=WS_C("hide"), .processor=processHideOperands},
    {.name=WS_C("hotkey"), .processor=processHotkeyOperands},
    {.name=WS_C("ifkey"), .processor=processIfKeyOperands, .unconditional=1},
    {.name=WS_C("ifnotkey"), .processor=processIfNotKeyOperands, .unconditional=1},
    {.name=WS_C("ifplatform"), .processor=processIfPlatformOperands, .unconditional=1},
    {.name=WS_C("ifnotplatform"), .processor=processIfNotPlatformOperands, .unconditional=1},
    {.name=WS_C("ignore"), .processor=processIgnoreOperands},
    {.name=WS_C("include"), .processor=processIncludeWrapper},
    {.name=WS_C("isolated"), .processor=processIsolatedOperands},
    {.name=WS_C("macro"), .processor=processMacroOperands},
    {.name=WS_C("map"), .processor=processMapOperands},
    {.name=WS_C("note"), .processor=processNoteOperands},
    {.name=WS_C("run"), .processor=processRunOperands},
    {.name=WS_C("superimpose"), .processor=processSuperimposeOperands},
    {.name=WS_C("title"), .processor=processTitleOperands},
  END_DATA_DIRECTIVE_TABLE

  return processDirectiveOperand(file, &directives, "key table directive", data);
}

void
resetLongPressData (KeyTable *table) {
  if (table->longPress.alarm) {
    asyncCancelRequest(table->longPress.alarm);
    table->longPress.alarm = NULL;
  }

  table->longPress.command = BRL_CMD_NOOP;
  table->longPress.repeat = 0;

  table->longPress.keyAction = NULL;
  table->longPress.keyContext = KTB_CTX_DEFAULT;
  table->longPress.keyValue.group = 0;
  table->longPress.keyValue.number = KTB_KEY_ANY;
}

void
resetKeyTable (KeyTable *table) {
  resetLongPressData(table);
  table->release.command = BRL_CMD_NOOP;
  table->pressedKeys.count = 0;
  table->context.current = table->context.next = table->context.persistent = KTB_CTX_DEFAULT;
}

static int
addIncompleteBinding (KeyContext *ctx, const KeyValue *keys, unsigned char count) {
  static const BoundCommand command = {
    .entry = NULL,
    .value = EOF
  };

  KeyBinding binding = {
    .flags = KBF_HIDDEN,

    .primaryCommand = command,
    .secondaryCommand = command,

    .keyCombination = {
      .modifierCount = count
    }
  };

  copyKeyValues(binding.keyCombination.modifierKeys, keys, count);
  return addKeyBinding(ctx, &binding, 1);
}

static int
addIncompleteSubbindings (KeyContext *ctx, const KeyValue *keys, unsigned char count) {
  if (count > 1) {
    KeyValue values[--count];
    unsigned int index = 0;

    copyKeyValues(values, &keys[1], count);

    while (1) {
      if (!addIncompleteBinding(ctx, values, count)) return 0;
      if (!addIncompleteSubbindings(ctx, values, count)) return 0;
      if (index == count) break;
      values[index] = keys[index];
      index += 1;
    }
  }

  return 1;
}

static int
addIncompleteBindings (KeyContext *ctx) {
  size_t count = ctx->keyBindings.count;

  if (count > 0) {
    KeyBinding bindings[count];

    memcpy(
      bindings, ctx->keyBindings.table,
      (count * sizeof(*ctx->keyBindings.table))
    );

    const KeyBinding *binding = bindings;
    const KeyBinding *end = binding + count;

    while (binding < end) {
      const KeyCombination *combination = &binding->keyCombination;
      if (!addIncompleteBinding(ctx, combination->modifierKeys, combination->modifierCount)) return 0;
      if (!addIncompleteSubbindings(ctx, combination->modifierKeys, combination->modifierCount)) return 0;
      binding += 1;
    }
  }

  return 1;
}

static int
prepareKeyBindings (KeyContext *ctx) {
  if (!addIncompleteBindings(ctx)) return 0;

  if (ctx->keyBindings.count < ctx->keyBindings.size) {
    if (ctx->keyBindings.count) {
      KeyBinding *newTable = realloc(ctx->keyBindings.table, ARRAY_SIZE(newTable, ctx->keyBindings.count));

      if (!newTable) {
        logMallocError();
        return 0;
      }

      ctx->keyBindings.table = newTable;
    } else {
      free(ctx->keyBindings.table);
      ctx->keyBindings.table = NULL;
    }

    ctx->keyBindings.size = ctx->keyBindings.count;
  }

  return 1;
}

int
finishKeyTable (KeyTableData *ktd) {
  for (unsigned int context=0; context<ktd->table->keyContexts.count; context+=1) {
    KeyContext *ctx = &ktd->table->keyContexts.table[context];
    if (!prepareKeyBindings(ctx)) return 0;
  }

  qsort(ktd->table->keyNames.table, ktd->table->keyNames.count, sizeof(*ktd->table->keyNames.table), sortKeyValues);
  resetKeyTable(ktd->table);
  return 1;
}

static int
defineInitialKeyContexts (KeyTableData *ktd) {
  typedef struct {
    unsigned char context;
    const wchar_t *name;
    const wchar_t *title;
  } PropertiesEntry;

  static const PropertiesEntry propertiesTable[] = {
    { .context = KTB_CTX_DEFAULT,
      .title = WS_C("Default Bindings"),
      .name = WS_C("default")
    },

    { .context = KTB_CTX_MENU,
      .title = WS_C("Menu Bindings"),
      .name = WS_C("menu")
    },

    { .name = NULL }
  };
  const PropertiesEntry *properties = propertiesTable;

  while (properties->name) {
    KeyContext *ctx = getKeyContext(ktd, properties->context);

    if (!ctx) return 0;
    if (properties->context != KTB_CTX_DEFAULT) ctx->isSpecial = 1;

    ctx->isDefined = 1;
    ctx->isReferenced = 1;

    if (properties->name) {
      if (!setKeyContextName(ctx, properties->name, wcslen(properties->name))) {
        return 0;
      }
    }

    if (properties->title) {
      if (!setKeyContextTitle(ctx, properties->title, wcslen(properties->title))) {
        return 0;
      }
    }

    properties += 1;
  }

  return 1;
}

KeyTable *
compileKeyTable (const char *name, KEY_NAME_TABLES_REFERENCE keys) {
  KeyTable *table = NULL;

  if (setTableDataVariables(KEY_TABLE_EXTENSION, KEY_SUBTABLE_EXTENSION)) {
    KeyTableData ktd;

    memset(&ktd, 0, sizeof(ktd));
    ktd.file = name;
    ktd.context = KTB_CTX_DEFAULT;

    {
      BoundCommand *cmd = &ktd.nullBoundCommand;

      cmd->entry = findCommandEntry(cmd->value = BRL_CMD_NOOP);
    }

    if ((ktd.table = malloc(sizeof(*ktd.table)))) {
      ktd.table->title = NULL;

      ktd.table->notes.table = NULL;
      ktd.table->notes.size = 0;
      ktd.table->notes.count = 0;

      ktd.table->keyNames.table = NULL;
      ktd.table->keyNames.count = 0;

      ktd.table->keyContexts.table = NULL;
      ktd.table->keyContexts.count = 0;

      ktd.table->pressedKeys.table = NULL;
      ktd.table->pressedKeys.size = 0;
      ktd.table->pressedKeys.count = 0;

      ktd.table->longPress.alarm = NULL;

      ktd.table->autorelease.alarm = NULL;
      ktd.table->autorelease.time = 0;

      ktd.table->commandMacros.table = NULL;
      ktd.table->commandMacros.size = 0;
      ktd.table->commandMacros.count = 0;

      ktd.table->hostCommands.table = NULL;
      ktd.table->hostCommands.size = 0;
      ktd.table->hostCommands.count = 0;

      ktd.table->options.logLabel = NULL;
      ktd.table->options.logKeyEventsFlag = NULL;
      ktd.table->options.keyboardEnabledFlag = NULL;

      if (defineInitialKeyContexts(&ktd)) {
        if (allocateKeyNameTable(&ktd, keys)) {
          if (allocateCommandTable(&ktd)) {
            const DataFileParameters parameters = {
              .processOperands = processKeyTableOperands,
              .data = &ktd
            };

            if (processDataFile(name, &parameters)) {
              if (finishKeyTable(&ktd)) {
                table = ktd.table;
                ktd.table = NULL;
              }
            }

            if (ktd.commandTable) free(ktd.commandTable);
          }
        }
      }

      if (ktd.table) destroyKeyTable(ktd.table);
    } else {
      logMallocError();
    }
  }

  return table;
}

void
destroyKeyTable (KeyTable *table) {
  resetLongPressData(table);
  setKeyAutoreleaseTime(table, 0);

  while (table->notes.count) free(table->notes.table[--table->notes.count]);

  while (table->keyContexts.count) {
    KeyContext *ctx = &table->keyContexts.table[--table->keyContexts.count];

    if (ctx->name) free(ctx->name);
    if (ctx->title) free(ctx->title);

    if (ctx->keyBindings.table) free(ctx->keyBindings.table);
    if (ctx->hotkeys.table) free(ctx->hotkeys.table);
    if (ctx->mappedKeys.table) free(ctx->mappedKeys.table);
  }

  if (table->commandMacros.table) {
    while (table->commandMacros.count > 0) {
      CommandMacro *macro = &table->commandMacros.table[--table->commandMacros.count];
      free(macro->commands);
    }

    free(table->commandMacros.table);
  }

  if (table->hostCommands.table) {
    while (table->hostCommands.count > 0) {
      HostCommand *hcmd = &table->hostCommands.table[--table->hostCommands.count];
      free(hcmd->arguments);
    }

    free(table->hostCommands.table);
  }

  if (table->keyContexts.table) free(table->keyContexts.table);
  if (table->keyNames.table) free(table->keyNames.table);
  if (table->notes.table) free(table->notes.table);
  if (table->title) free(table->title);
  if (table->pressedKeys.table) free(table->pressedKeys.table);
  free(table);
}

char *
ensureKeyTableExtension (const char *path) {
  return ensureFileExtension(path, KEY_TABLE_EXTENSION);
}

char *
makeKeyTablePath (const char *directory, const char *name) {
  return makeFilePath(directory, name, KEY_TABLE_EXTENSION);
}

char *
makeKeyboardTablePath (const char *directory, const char *name) {
  char *subdirectory = makePath(directory, KEYBOARD_TABLES_SUBDIRECTORY);

  if (subdirectory) {
    char *file = makeKeyTablePath(subdirectory, name);

    free(subdirectory);
    if (file) return file;
  }

  return NULL;
}

char *
makeInputTablePath (const char *directory, const char *driver, const char *name) {
  const char *components[] = {
    directory,
    INPUT_TABLES_SUBDIRECTORY,
    driver
  };
  char *subdirectory = joinPath(components, ARRAY_COUNT(components));

  if (subdirectory) {
    char *file = makeKeyTablePath(subdirectory, name);

    free(subdirectory);
    if (file) return file;
  }

  return NULL;
}
