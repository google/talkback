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
#include "utf8.h"
#include "cmd.h"
#include "brl_cmds.h"
#include "ktb.h"
#include "ktb_list.h"
#include "ktb_cmds.h"

struct CommandGroupHookDataStruct {
  ListGenerationData *const lgd;
  const KeyContext *const ctx;

  int ok;
};

static inline void
listCommandSubgroup (
  int (*list) (ListGenerationData *lgd, const KeyContext *ctx),
  CommandGroupHookData *cgh
) {
  cgh->ok = list(cgh->lgd, cgh->ctx);
}

static int
handleCommandGroupHook (CommandGroupHook *handler, CommandGroupHookData *cgh) {
  if (!handler) return 1;
  cgh->ok = 0;
  handler(cgh);
  return cgh->ok;
}

static void *
getMethodData (ListGenerationData *lgd) {
  return lgd->list.internal? lgd: lgd->list.data;
}

static int
writeHeader (ListGenerationData *lgd, const wchar_t *text, int level) {
  return lgd->list.methods->writeHeader(text, level, getMethodData(lgd));
}

static int
writeLine (ListGenerationData *lgd, const wchar_t *line) {
  return lgd->list.writeLine(line, lgd->list.data);
}

static int
writeBlankLine (ListGenerationData *lgd) {
  return writeLine(lgd, WS_C(""));
}

static int
addCharacters (ListGenerationData *lgd, const wchar_t *characters, size_t count) {
  size_t newLength = lgd->line.length + count;

  if (newLength > lgd->line.size) {
    size_t newSize = (newLength | 0X3F) + 1;
    wchar_t *newCharacters = realloc(lgd->line.characters, ARRAY_SIZE(newCharacters, newSize));

    if (!newCharacters) {
      logSystemError("realloc");
      return 0;
    }

    lgd->line.characters = newCharacters;
    lgd->line.size = newSize;
  }

  wmemcpy(&lgd->line.characters[lgd->line.length], characters, count);
  lgd->line.length = newLength;
  return 1;
}

static int
putCharacters (ListGenerationData *lgd, const wchar_t *characters, size_t count) {
  if (lgd->line.length == 0) {
    if (lgd->list.elementLevel > 0) {
      const unsigned int indent = 2;
      const unsigned int count = indent * lgd->list.elementLevel;
      wchar_t characters[count];

      wmemset(characters, WC_C(' '), count);
      characters[count - indent] = lgd->list.elementBullet;
      lgd->list.elementBullet = WC_C(' ');

      if (!addCharacters(lgd, characters, count)) return 0;
    }
  }

  return addCharacters(lgd, characters, count);
}

static int
putCharacter (ListGenerationData *lgd, wchar_t character) {
  return putCharacters(lgd, &character, 1);
}

static int
putCharacterString (ListGenerationData *lgd, const wchar_t *string) {
  return putCharacters(lgd, string, wcslen(string));
}

static int
putUtf8String (ListGenerationData *lgd, const char *string) {
  size_t size = strlen(string) + 1;
  wchar_t characters[size];
  wchar_t *character = characters;

  convertUtf8ToWchars(&string, &character, size);
  return putCharacters(lgd, characters, character-characters);
}

static void
clearLine (ListGenerationData *lgd) {
  lgd->line.length = 0;
}

static void
trimLine (ListGenerationData *lgd) {
  while (lgd->line.length > 0) {
    size_t last = lgd->line.length - 1;

    if (lgd->line.characters[last] != WC_C(' ')) break;
    lgd->line.length = last;
  }
}

static int
finishLine (ListGenerationData *lgd) {
  trimLine(lgd);
  if (!putCharacter(lgd, 0)) return 0;
  return 1;
}

static int
endLine (ListGenerationData *lgd) {
  if (lgd->topicHeader) {
    if (!writeHeader(lgd, lgd->topicHeader, 1)) return 0;
    lgd->topicHeader = NULL;
  }

  if (lgd->listHeader) {
    const char *string = lgd->listHeader;
    lgd->listHeader = NULL;

    size_t size = strlen(string) + 1;
    wchar_t characters[size];
    wchar_t *character = characters;

    convertUtf8ToWchars(&string, &character, size);
    if (!writeHeader(lgd, characters, 2)) return 0;
  }

  if (!finishLine(lgd)) return 0;
  if (!writeLine(lgd, lgd->line.characters)) return 0;
  clearLine(lgd);
  return 1;
}

static int
beginList (ListGenerationData *lgd, const char *header) {
  lgd->listHeader = header;
  return 1;
}

static int
endList (ListGenerationData *lgd) {
  return lgd->list.methods->endList(getMethodData(lgd));
}

static int
beginElement (ListGenerationData *lgd, unsigned int level) {
  return lgd->list.methods->beginElement(level, getMethodData(lgd));
}

static int
searchKeyNameEntry (const void *target, const void *element) {
  const KeyValue *value = target;
  const KeyNameEntry *const *kne = element;
  return compareKeyValues(value, &(*kne)->value);
}

const KeyNameEntry *
findKeyNameEntry (KeyTable *table, const KeyValue *value) {
  const KeyNameEntry *const *array = table->keyNames.table;
  unsigned int count = table->keyNames.count;

  const KeyNameEntry *const *kne = bsearch(value, array, count, sizeof(*array), searchKeyNameEntry);
  if (!kne) return NULL;

  while (kne > array) {
    if (compareKeyValues(value, &(*--kne)->value) != 0) {
      kne += 1;
      break;
    }
  }

  return *kne;
}

STR_BEGIN_FORMATTER(formatKeyName, KeyTable *table, const KeyValue *value)
  const KeyNameEntry *kne = findKeyNameEntry(table, value);

  if (kne) {
    STR_PRINTF("%s", kne->name);
  } else if (value->number != KTB_KEY_ANY) {
    const KeyValue anyKey = {
      .group = value->group,
      .number = KTB_KEY_ANY
    };

    if ((kne = findKeyNameEntry(table, &anyKey))) {
      STR_PRINTF("%s.%u", kne->name, value->number+1);
    }
  }

  if (STR_LENGTH == 0) STR_PRINTF("?");
STR_END_FORMATTER

static int
putKeyName (ListGenerationData *lgd, const KeyValue *value) {
  char string[0X100];
  formatKeyName(string, sizeof(string), lgd->keyTable, value);
  return putUtf8String(lgd, string);
}

STR_BEGIN_FORMATTER(formatKeyCombination, KeyTable *table, const KeyCombination *combination)
  char keyDelimiter = 0;
  unsigned char dotCount = 0;

  const char *dotPrefix = "dot";
  const size_t dotPrefixLength = strlen(dotPrefix);
  const size_t dotNameLength = dotPrefixLength + 1;

  for (unsigned char index=0; index<combination->modifierCount; index+=1) {
    char keyName[0X100];
    formatKeyName(keyName, sizeof(keyName), table,
                  &combination->modifierKeys[combination->modifierPositions[index]]);

    if (strlen(keyName) == dotNameLength) {
      if (strncasecmp(keyName, dotPrefix, dotPrefixLength) == 0) {
        char dotNumber = keyName[dotPrefixLength];

        if ((dotNumber >= '1') && (dotNumber <= '8')) {
          if (++dotCount == 1) goto FIRST_DOT;

          if (dotCount == 2) {
            char firstDot = *STR_POP();
            STR_PRINTF("s%c", firstDot);
          }

          STR_PRINTF("%c", dotNumber);
          continue;
        }
      }
    }

    dotCount = 0;
  FIRST_DOT:

    if (keyDelimiter) {
      STR_PRINTF("%c", keyDelimiter);
    } else {
      keyDelimiter = '+';
    }

    STR_PRINTF("%s", keyName);
  }

  if (combination->flags & KCF_IMMEDIATE_KEY) {
    if (keyDelimiter) STR_PRINTF("%c", keyDelimiter);
    STR_FORMAT(formatKeyName, table, &combination->immediateKey);
  }
STR_END_FORMATTER

static int
putKeyCombination (ListGenerationData *lgd, const KeyCombination *combination) {
  char string[0X100];
  formatKeyCombination(string, sizeof(string), lgd->keyTable, combination);
  return putUtf8String(lgd, string);
}

static int
putCommandDescription (ListGenerationData *lgd, const BoundCommand *cmd, int details) {
  char description[0X60];

  describeCommand(description, sizeof(description), cmd->value,
                  (details? (CDO_IncludeOperand | CDO_DefaultOperand): 0));
  return putUtf8String(lgd, description);
}

static int
putKeyboardFunction (ListGenerationData *lgd, const KeyboardFunction *kbf) {
  if (!beginElement(lgd, 1)) return 0;
  if (!putCharacterString(lgd, WS_C("braille keyboard "))) return 0;
  if (!putUtf8String(lgd, kbf->name)) return 0;
  if (!putCharacterString(lgd, WS_C(": "))) return 0;
  return 1;
}

static int
listKeyboardFunctions (ListGenerationData *lgd, const KeyContext *ctx) {
  if (ctx->mappedKeys.count > 0) {
    for (unsigned int index=0; index<ctx->mappedKeys.count; index+=1) {
      const MappedKeyEntry *map = &ctx->mappedKeys.table[index];

      if (!(map->flags & MKF_HIDDEN)) {
        const KeyboardFunction *kbf = map->keyboardFunction;

        if (!putKeyboardFunction(lgd, kbf)) return 0;
        if (!putKeyName(lgd, &map->keyValue)) return 0;
        if (!endLine(lgd)) return 0;
      }
    }

    {
      const KeyboardFunction *kbf = keyboardFunctionTable;
      const KeyboardFunction *end = kbf + keyboardFunctionCount;

      while (kbf < end) {
        if (ctx->mappedKeys.superimpose & kbf->bit) {
          if (!putKeyboardFunction(lgd, kbf)) return 0;
          if (!putCharacterString(lgd, WS_C("superimposed"))) return 0;
          if (!endLine(lgd)) return 0;
        }

        kbf += 1;
      }
    }
  }

  return 1;
}

void
commandGroupHook_keyboardFunctions (CommandGroupHookData *cgh) {
  listCommandSubgroup(listKeyboardFunctions, cgh);
}

static int
listHotkeyEvent (ListGenerationData *lgd, const KeyValue *keyValue, const char *event, const BoundCommand *cmd) {
  if (cmd->value != BRL_CMD_NOOP) {
    if (!beginElement(lgd, 1)) return 0;

    if ((cmd->value & BRL_MSK_BLK) == BRL_CMD_BLK(CONTEXT)) {
      const KeyContext *ctx = getKeyContext(lgd->keyTable, (KTB_CTX_DEFAULT + (cmd->value & BRL_MSK_ARG)));
      if (!ctx) return 0;
      if (!putUtf8String(lgd, "switch to ")) return 0;
      if (!putCharacterString(lgd, ctx->title)) return 0;
    } else {
      if (!putCommandDescription(lgd, cmd, (keyValue->number != KTB_KEY_ANY))) return 0;
    }

    if (!putCharacterString(lgd, WS_C(": "))) return 0;
    if (!putUtf8String(lgd, event)) return 0;
    if (!putCharacter(lgd, WC_C(' '))) return 0;
    if (!putKeyName(lgd, keyValue)) return 0;
    if (!endLine(lgd)) return 0;
  }

  return 1;
}

static int
listHotkeys (ListGenerationData *lgd, const KeyContext *ctx) {
  const HotkeyEntry *hotkey = ctx->hotkeys.table;
  unsigned int count = ctx->hotkeys.count;

  while (count) {
    if (!(hotkey->flags & HKF_HIDDEN)) {
      if (!listHotkeyEvent(lgd, &hotkey->keyValue, "press", &hotkey->pressCommand)) return 0;
      if (!listHotkeyEvent(lgd, &hotkey->keyValue, "release", &hotkey->releaseCommand)) return 0;
    }

    hotkey += 1, count -= 1;
  }

  return 1;
}

void
commandGroupHook_hotkeys (CommandGroupHookData *cgh) {
  listCommandSubgroup(listHotkeys, cgh);
}

static int
saveBindingLine (
  ListGenerationData *lgd, size_t keysOffset,
  const BoundCommand *command, const KeyBinding *binding
) {
  if (lgd->binding.count == lgd->binding.size) {
    size_t newSize = lgd->binding.size? (lgd->binding.size << 1): 0X10;
    BindingLine **newLines = realloc(lgd->binding.lines, ARRAY_SIZE(newLines, newSize));

    if (!newLines) {
      logMallocError();
      return 0;
    }

    lgd->binding.lines = newLines;

    while (lgd->binding.size < newSize) {
      lgd->binding.lines[lgd->binding.size++] = NULL;
    }
  }

  {
    BindingLine *line;
    size_t size = sizeof(*line) + (sizeof(line->text[0]) * lgd->line.length);

    if (!(line = malloc(size))) {
      logMallocError();
      return 0;
    }

    line->command = command;
    line->keyCombination = &binding->keyCombination;
    line->keysOffset = keysOffset;
    wmemcpy(line->text, lgd->line.characters, (line->length = lgd->line.length));
    lgd->binding.lines[lgd->binding.count++] = line;
  }

  clearLine(lgd);
  return 1;
}

static void
removeBindingLine (ListGenerationData *lgd, int index) {
  {
    BindingLine *bl = lgd->binding.lines[index];

    free(bl);
  }

  if (--lgd->binding.count > index) {
    memmove(&lgd->binding.lines[index], &lgd->binding.lines[index+1],
            ((lgd->binding.count - index) * sizeof(*lgd->binding.lines)));
  }
}

static void
removeBindingLines (ListGenerationData *lgd) {
  size_t *count = &lgd->binding.count;

  while (*count > 0) removeBindingLine(lgd, (*count - 1));
}

static int
sortBindingLines (const void *element1, const void *element2) {
  const BindingLine *const *line1 = element1;
  const BindingLine *const *line2 = element2;

  int command1 = (*line1)->command->value;
  int command2 = (*line2)->command->value;

  int cmd1 = command1 & BRL_MSK_CMD;
  int cmd2 = command2 & BRL_MSK_CMD;
  if (cmd1 < cmd2) return -1;
  if (cmd1 > cmd2) return 1;

  if (command1 < command2) return -1;
  if (command1 > command2) return 1;

  const KeyCombination *combination1 = (*line1)->keyCombination;
  const KeyCombination *combination2 = (*line2)->keyCombination;
  if (combination1->anyKeyCount < combination2->anyKeyCount) return -1;
  if (combination1->anyKeyCount > combination2->anyKeyCount) return 1;

  if (combination1 < combination2) return -1;
  if (combination1 > combination2) return 1;
  return 0;
}

static int
listBindingLine (ListGenerationData *lgd, int index, int *isSame) {
  const BindingLine *bl = lgd->binding.lines[index];
  int asList = *isSame;

  if (*isSame) {
    *isSame = 0;
  } else {
    if (!beginElement(lgd, 1)) return 0;
    if (!putCharacters(lgd, bl->text, bl->keysOffset)) return 0;
  }

  {
    int next = index + 1;

    if (next < lgd->binding.count) {
      const BindingLine *nl = lgd->binding.lines[next];

      if (bl->command->value == nl->command->value) {
        if (bl->keyCombination->anyKeyCount == nl->keyCombination->anyKeyCount) {
          if (!asList) {
            if (!endLine(lgd)) return 0;
          }

          asList = 1;
          *isSame = 1;
        }
      }
    }
  }

  if (asList) {
    if (!beginElement(lgd, 2)) return 0;
  }

  if (!putCharacters(lgd, &bl->text[bl->keysOffset], (bl->length - bl->keysOffset))) return 0;
  if (!endLine(lgd)) return 0;

  removeBindingLine(lgd, index);
  return 1;
}

static int
listBindingLines (ListGenerationData *lgd, const KeyContext *ctx) {
  if (lgd->binding.count > 0) {
    qsort(lgd->binding.lines, lgd->binding.count,
          sizeof(*lgd->binding.lines), sortBindingLines);

    {
      const CommandGroupEntry *grp = commandGroupTable;
      const CommandGroupEntry *grpEnd = grp + commandGroupCount;

      while (grp < grpEnd) {
        const CommandListEntry *cmd = grp->commands.table;
        const CommandListEntry *cmdEnd = cmd + grp->commands.count;

        CommandGroupHookData cgh = {
          .lgd = lgd,
          .ctx = ctx
        };

        if (!beginList(lgd, grp->name)) return 0;
        if (!handleCommandGroupHook(grp->before, &cgh)) return 0;

        while (cmd < cmdEnd) {
          int first = 0;
          int last = lgd->binding.count - 1;

          while (first <= last) {
            int current = (first + last) / 2;
            const BindingLine *bl = lgd->binding.lines[current];
            int command = bl->command->value & BRL_MSK_CMD;

            if (command < cmd->code) {
              first = current + 1;
            } else {
              last = current - 1;
            }
          }

          int isSame = 0;
          int current = cmd->code;

          // Binding lines are removed as they're processed, so, even though
          // first isn't being incremented, the count will be decremented.
          while (first < lgd->binding.count) {
            const BindingLine *bl = lgd->binding.lines[first];
            int next = bl->command->value;

            if ((next & BRL_MSK_CMD) != current) {
              int blk = next & BRL_MSK_BLK;
              if (!blk) break;
              if (blk != (current & BRL_MSK_BLK)) break;
            }

            if (!listBindingLine(lgd, first, &isSame)) return 0;
          }

          cmd += 1;
        }

        if (!handleCommandGroupHook(grp->after, &cgh)) return 0;
        if (!endList(lgd)) return 0;
        grp += 1;
      }

      {
        int isSame = 0;

        if (!beginList(lgd, "Uncategorized Bindings")) return 0;

        while (lgd->binding.count > 0) {
          if (!listBindingLine(lgd, 0, &isSame)) return 0;
        }

        if (!endList(lgd)) return 0;
      }
    }
  }

  return 1;
}

static int listKeyBindings (ListGenerationData *lgd, const KeyContext *ctx, const wchar_t *keysPrefix);

static int
listKeyBinding (ListGenerationData *lgd, const KeyBinding *binding, int longPress, const wchar_t *keysPrefix) {
  const BoundCommand *cmd = longPress? &binding->secondaryCommand: &binding->primaryCommand;

  if (cmd->value == BRL_CMD_NOOP) return 1;

  if (!putCommandDescription(lgd, cmd, !binding->keyCombination.anyKeyCount)) return 0;
  if (!putCharacterString(lgd, WS_C(": "))) return 0;
  size_t keysOffset = lgd->line.length;

  if (keysPrefix) {
    if (!putCharacterString(lgd, keysPrefix)) return 0;
    if (!putCharacterString(lgd, WS_C(", "))) return 0;
  }

  if (longPress) {
    if (!putCharacterString(lgd, WS_C("long "))) return 0;
  }

  if (!putKeyCombination(lgd, &binding->keyCombination)) return 0;

  if ((cmd->value & BRL_MSK_BLK) == BRL_CMD_BLK(CONTEXT)) {
    const KeyContext *ctx = getKeyContext(lgd->keyTable, (KTB_CTX_DEFAULT + (cmd->value & BRL_MSK_ARG)));
    if (!ctx) return 0;

    {
      size_t length = lgd->line.length - keysOffset;
      wchar_t keys[length + 1];

      wmemcpy(keys, &lgd->line.characters[keysOffset], length);
      keys[length] = 0;
      clearLine(lgd);

      if (isTemporaryKeyContext(lgd->keyTable, ctx)) {
        if (!listKeyBindings(lgd, ctx, keys)) return 0;
      } else {
        if (!putCharacterString(lgd, WS_C("switch to "))) return 0;
        if (!putCharacterString(lgd, ctx->title)) return 0;
        if (!putCharacterString(lgd, WS_C(": "))) return 0;
        keysOffset = lgd->line.length;
        if (!putCharacterString(lgd, keys)) return 0;
        if (!saveBindingLine(lgd, keysOffset, cmd, binding)) return 0;
      }
    }
  } else {
    if (!saveBindingLine(lgd, keysOffset, cmd, binding)) return 0;
  }

  return 1;
}

static int
listKeyBindings (ListGenerationData *lgd, const KeyContext *ctx, const wchar_t *keysPrefix) {
  const KeyBinding *binding = ctx->keyBindings.table;
  unsigned int count = ctx->keyBindings.count;

  while (count) {
    if (!(binding->flags & KBF_HIDDEN)) {
      if (!listKeyBinding(lgd, binding, 0, keysPrefix)) return 0;
      if (!listKeyBinding(lgd, binding, 1, keysPrefix)) return 0;
    }

    binding += 1, count -= 1;
  }

  return 1;
}

static int
listKeyContext (ListGenerationData *lgd, const KeyContext *ctx) {
  lgd->topicHeader = ctx->title;
  if (!listKeyBindings(lgd, ctx, NULL)) return 0;
  if (!listBindingLines(lgd, ctx)) return 0;
  return 1;
}

static int
listSpecialKeyContexts (ListGenerationData *lgd) {
  static const unsigned char contexts[] = {
    KTB_CTX_DEFAULT,
    KTB_CTX_MENU
  };

  const unsigned char *context = contexts;
  unsigned int count = ARRAY_COUNT(contexts);

  while (count) {
    const KeyContext *ctx = getKeyContext(lgd->keyTable, *context);

    if (ctx) {
      if (!listKeyContext(lgd, ctx)) return 0;
    }

    context += 1, count -= 1;
  }

  return 1;
}

static int
listPersistentKeyContexts (ListGenerationData *lgd) {
  for (unsigned int context=KTB_CTX_DEFAULT+1; context<lgd->keyTable->keyContexts.count; context+=1) {
    const KeyContext *ctx = getKeyContext(lgd->keyTable, context);

    if (ctx && !isTemporaryKeyContext(lgd->keyTable, ctx)) {
      if (!listKeyContext(lgd, ctx)) return 0;
    }
  }

  return 1;
}

static int
listKeyTableTitle (ListGenerationData *lgd) {
  if (!putUtf8String(lgd, gettext("Key Table"))) return 0;

  if (lgd->keyTable->title) {
    if (!putCharacterString(lgd, WS_C(": "))) return 0;
    if (!putCharacterString(lgd, lgd->keyTable->title)) return 0;

    if (!finishLine(lgd)) return 0;
    if (!writeHeader(lgd, lgd->line.characters, 0)) return 0;
    clearLine(lgd);
  }

  return 1;
}

static int
listKeyTableNotes (ListGenerationData *lgd) {
  if (!beginList(lgd, "Notes")) return 0;

  for (unsigned int noteIndex=0; noteIndex<lgd->keyTable->notes.count; noteIndex+=1) {
    const wchar_t *line = lgd->keyTable->notes.table[noteIndex];
    unsigned int level;
    int prefixed;

    if (*line == WC_C('*')) {
      level = 0;
      prefixed = 1;
    } else if (*line == WC_C('+')) {
      level = 2;
      prefixed = 1;
    } else {
      level = 1;
      prefixed = 0;
    }

    if (level > 0) {
      if (!beginElement(lgd, level)) return 0;
    }

    if (prefixed) {
      line += 1;
      while (iswspace(*line)) line += 1;
    }

    if (!putCharacterString(lgd, line)) return 0;
    if (!endLine(lgd)) return 0;
  }

  if (!endList(lgd)) return 0;
  return 1;
}

static int
listCommandMacros (ListGenerationData *lgd) {
  unsigned int count = lgd->keyTable->commandMacros.count;

  if (count > 0) {
    lgd->topicHeader = WS_C("Command Macros");

    const CommandMacro *macro = lgd->keyTable->commandMacros.table;
    const CommandMacro *endMacros = macro + count;
    unsigned int number = 0;

    while (macro < endMacros) {
      {
        char buffer[0X100];
        snprintf(buffer, sizeof(buffer), "Command Macro #%u:", ++number);
        if (!putUtf8String(lgd, buffer)) return 0;
      }

      {
        const BoundCommand *command = macro->commands;
        const BoundCommand *endCommands = command + macro->count;

        while (command < endCommands) {
          if (!putCharacter(lgd, WC_C(' '))) return 0;
          if (!putUtf8String(lgd, command->entry->name)) return 0;
          command += 1;
        }
      }

      if (!endLine(lgd)) return 0;
      macro += 1;
    }

    if (!endLine(lgd)) return 0;
  }

  return 1;
}

static int
listHostCommands (ListGenerationData *lgd) {
  unsigned int count = lgd->keyTable->hostCommands.count;

  if (count > 0) {
    lgd->topicHeader = WS_C("Host Commands");

    const HostCommand *hc = lgd->keyTable->hostCommands.table;
    const HostCommand *end = hc + count;
    unsigned int number = 0;

    while (hc < end) {
      {
        char buffer[0X100];
        snprintf(buffer, sizeof(buffer), "Host Command #%u:", ++number);
        if (!putUtf8String(lgd, buffer)) return 0;
      }

      {
        char **argument = hc->arguments;

        while (*argument) {
          if (!putCharacter(lgd, WC_C(' '))) return 0;
          if (!putUtf8String(lgd, *argument)) return 0;
          argument += 1;
        }
      }

      if (!endLine(lgd)) return 0;
      hc += 1;
    }

    if (!endLine(lgd)) return 0;
  }

  return 1;
}

static int
listKeyTableSections (ListGenerationData *lgd) {
  typedef int Lister (ListGenerationData *lgd);

  static Lister *const listerTable[] = {
    listKeyTableTitle,
    listKeyTableNotes,
    listCommandMacros,
    listHostCommands,
    listSpecialKeyContexts,
    listPersistentKeyContexts
  };

  Lister *const *lister = listerTable;
  Lister *const *end = lister + ARRAY_COUNT(listerTable);

  while (lister < end) {
    if (!(*lister)(lgd)) return 0;
    lister += 1;
  }

  return 1;
}

static int
internalWriteHeader (const wchar_t *text, unsigned int level, void *data) {
  static const wchar_t characters[] = {WC_C('='), WC_C('-')};
  ListGenerationData *lgd = data;

  if (!writeLine(lgd, text)) return 0;

  if (level < ARRAY_COUNT(characters)) {
    size_t length = wcslen(text);
    wchar_t underline[length + 1];

    wmemset(underline, characters[level], length);
    underline[length] = 0;

    if (!writeLine(lgd, underline)) return 0;
    if (!writeBlankLine(lgd)) return 0;
  }

  return 1;
}

static int
internalBeginElement (unsigned int level, void *data) {
  ListGenerationData *lgd = data;

  static const wchar_t bullets[] = {
    WC_C('*'),
    WC_C('+'),
    WC_C('-')
  };

  lgd->list.elementLevel = level;
  lgd->list.elementBullet = bullets[level - 1];
  return 1;
}

static int
internalEndList (void *data) {
  ListGenerationData *lgd = data;

  if (lgd->list.elementLevel > 0) {
    lgd->list.elementLevel = 0;
    lgd->listHeader = NULL;
    if (!writeBlankLine(lgd)) return 0;
  }

  return 1;
}

int
listKeyTable (KeyTable *table, const KeyTableListMethods *methods, KeyTableWriteLineMethod *writeLine, void *data) {
  static const KeyTableListMethods internalMethods = {
    .writeHeader = internalWriteHeader,
    .beginElement = internalBeginElement,
    .endList = internalEndList
  };

  ListGenerationData lgd = {
    .keyTable = table,

    .topicHeader = NULL,
    .listHeader = NULL,

    .line = {
      .characters = NULL,
      .size = 0,
      .length = 0,
    },

    .list = {
      .methods = methods? methods: &internalMethods,
      .writeLine = writeLine,
      .data = data,
      .internal = !methods,

      .elementLevel = 0
    },

    .binding = {
      .lines = NULL,
      .size = 0,
      .count = 0
    }
  };

  int result = listKeyTableSections(&lgd);

  if (lgd.binding.lines) {
    removeBindingLines(&lgd);
    free(lgd.binding.lines);
  }

  if (lgd.line.characters) free(lgd.line.characters);
  return result;
}

int
forEachKeyName (KEY_NAME_TABLES_REFERENCE keys, KeyNameEntryHandler *handleKeyNameEntry, void *data) {
  const KeyNameEntry *const *knt = keys;

  while (*knt) {
    const KeyNameEntry *kne = *knt;

    if (knt != keys) {
      if (!handleKeyNameEntry(NULL, data)) {
        return 0;
      }
    }

    while (kne->name) {
      if (!handleKeyNameEntry(kne, data)) return 0;
      kne += 1;
    }

    knt += 1;
  }

  return 1;
}

typedef struct {
  KeyTableWriteLineMethod *const writeLine;
  void *const data;
} ListKeyNameData;

static int
listKeyName (const KeyNameEntry *kne, void *data) {
  const ListKeyNameData *lkn = data;
  const char *name = kne? kne->name: "";
  size_t size = strlen(name) + 1;
  wchar_t characters[size];
  wchar_t *character = characters;

  convertUtf8ToWchars(&name, &character, size);
  return lkn->writeLine(characters, lkn->data);
}

int
listKeyNames (KEY_NAME_TABLES_REFERENCE keys, KeyTableWriteLineMethod *writeLine, void *data) {
  ListKeyNameData lkn = {
    .writeLine = writeLine,
    .data = data
  };

  return forEachKeyName(keys, listKeyName, &lkn);
}
