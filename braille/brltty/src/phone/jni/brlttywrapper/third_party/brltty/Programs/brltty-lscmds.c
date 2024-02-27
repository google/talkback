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
#include <ctype.h>

#include "program.h"
#include "cmdline.h"
#include "ktb_cmds.h"
#include "cmd.h"

BEGIN_OPTION_TABLE(programOptions)
END_OPTION_TABLE(programOptions)

static void
writeCharacter (char character) {
  putc(character, stdout);
}

static void
writeCharacters (char character, size_t count) {
  while (count > 0) {
    writeCharacter(character);
    count -= 1;
  }
}

static void
endLine (void) {
  writeCharacter('\n');
}

static void
writeString (const char *string) {
  while (*string) writeCharacter(*string++);
}

static const char headerCharacters[] = {'~', '=', '-'};
static unsigned char headerLevel = 0;

static void
incrementHeaderLevel (void) {
  headerLevel += 1;
}

static void
decrementHeaderLevel (void) {
  headerLevel -= 1;
}

static void
writeHeader (const char *header) {
  char headerCharacter = headerCharacters[headerLevel];
  size_t headerLength = strlen(header);

  if (headerLevel == 0) {
    writeCharacters(headerCharacter, headerLength);
    endLine();
  }

  writeString(header);
  endLine();

  writeCharacters(headerCharacter, headerLength);
  endLine();
  endLine();
}

void
commandGroupHook_hotkeys (CommandGroupHookData *data) {
}

void
commandGroupHook_keyboardFunctions (CommandGroupHookData *data) {
}

static void
listModifiers (int include, const char *type, int *started, const CommandModifierEntry *modifiers) {
  if (include) {
    if (!*started) {
      *started = 1;
      printf("The following modifiers may be specified:\n\n");
    }

    printf("* %s", type);

    if (modifiers) {
      const CommandModifierEntry *modifier = modifiers;
      char punctuation = ':';

      while (modifier->name) {
        printf("%c %s", punctuation, modifier->name);
        punctuation = ',';
        modifier += 1;
      }
    }

    endLine();
  }
}

static void
putCommand (const CommandEntry *command) {
  {
    const char *description = command->description;

    printf(".. _%s:\n\n", command->name);
    printf("**%s** - ", command->name);

    writeCharacter(toupper(*description++));
    printf("%s.\n\n", description);
  }

  {
    int started = 0;

    listModifiers(command->isOffset, "an offset", &started, NULL);
    listModifiers(command->isColumn, "a column number", &started, NULL);
    listModifiers(command->isCharacter, "a single character", &started, NULL);

    listModifiers(
      command->isToggle, "Toggle", &started,
      commandModifierTable_toggle
    );

    listModifiers(
      command->isMotion, "Motion", &started,
      commandModifierTable_motion
    );

    listModifiers(
      command->isRow, "Row", &started,
      commandModifierTable_row
    );

    listModifiers(
      command->isVertical, "Vertical", &started,
      commandModifierTable_vertical
    );

    listModifiers(
      command->isInput, "Input", &started,
      commandModifierTable_input
    );

    listModifiers(
      (command->isCharacter || command->isBraille), "Character", &started,
      commandModifierTable_character
    );

    listModifiers(
      command->isBraille, "Braille", &started,
      commandModifierTable_braille
    );

    listModifiers(
      command->isKeyboard, "Keyboard", &started,
      commandModifierTable_keyboard
    );

    if (started) endLine();
  }
}

static void
putGroup (const CommandGroupEntry *group) {
  incrementHeaderLevel();
  writeHeader(group->name);

  size_t count = group->commands.count;
  const CommandEntry *commands[count];

  for (unsigned int index=0; index<count; index+=1) {
    commands[index] = findCommandEntry(group->commands.table[index].code);
  }

  for (unsigned int index=0; index<count; index+=1) {
    printf("* `%s`_\n", commands[index]->name);
  }
  printf("\n");

  for (unsigned int index=0; index<count; index+=1) {
    putCommand(commands[index]);
  }

  decrementHeaderLevel();
}

static void
putGroups (void) {
  const CommandGroupEntry *group = commandGroupTable;
  const CommandGroupEntry *end = group + commandGroupCount;

  while (group < end) {
    putGroup(group);
    group += 1;
  }
}

static int
compareCommands (const void *element1, const void *element2) {
  const CommandEntry *const *command1 = element1;
  const CommandEntry *const *command2 = element2;
  return strcmp((*command1)->name, (*command2)->name);
}

static void
putCommandIndex (void) {
  incrementHeaderLevel();
  writeHeader("Alphabetical Command Index");

  int count = getCommandCount();
  const CommandEntry *commands[count];

  for (int index=0; index<count; index+=1) {
    commands[index] = &commandTable[index];
  }
  qsort(commands, count, sizeof(commands[0]), compareCommands);

  for (int index=0; index<count; index+=1) {
    printf("* `%s`_\n", commands[index]->name);
  }

  printf("\n");
  decrementHeaderLevel();
}

int
main (int argc, char *argv[]) {
  {
    const CommandLineDescriptor descriptor = {
      .options = &programOptions,
      .applicationName = "brltty-lscmds",

      .usage = {
        .purpose = strtext("Write a brltty command reference in reStructuredText."),
      }
    };

    PROCESS_OPTIONS(descriptor, argc, argv);
  }

  writeHeader("The BRLTTY Command Reference");
  writeString(".. contents::\n\n");

  putCommandIndex();
  putGroups();
  return PROG_EXIT_SUCCESS;
}
