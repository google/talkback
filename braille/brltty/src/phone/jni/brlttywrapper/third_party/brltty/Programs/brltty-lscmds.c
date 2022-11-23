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
#include <ctype.h>

#include "program.h"
#include "options.h"
#include "ktb_cmds.h"
#include "cmd.h"

BEGIN_OPTION_TABLE(programOptions)
END_OPTION_TABLE

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

static const char headerCharacters[] = {'=', '-', '~'};
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
  writeString(header);
  endLine();

  writeCharacters(headerCharacters[headerLevel], strlen(header));
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
listCommand (const CommandEntry *command) {
  incrementHeaderLevel();
  writeHeader(command->name);

  {
    const char *description = command->description;
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

  decrementHeaderLevel();
}

static void
listGroup (const CommandGroupEntry *group) {
  incrementHeaderLevel();
  writeHeader(group->name);

  const CommandListEntry *command = group->commands.table;
  const CommandListEntry *end = command + group->commands.count;

  while (command < end) {
    listCommand(findCommandEntry(command->code));
    command += 1;
  }

  decrementHeaderLevel();
}

static void
listGroups (void) {
  const CommandGroupEntry *group = commandGroupTable;
  const CommandGroupEntry *end = group + commandGroupCount;

  while (group < end) {
    listGroup(group);
    group += 1;
  }
}

int
main (int argc, char *argv[]) {
  {
    static const OptionsDescriptor descriptor = {
      OPTION_TABLE(programOptions),
      .applicationName = "brltty-lscmds"
    };

    PROCESS_OPTIONS(descriptor, argc, argv);
  }

  writeHeader("BRLTTY Command Reference");
  writeString(".. contents::\n\n");

  listGroups();
  return PROG_EXIT_SUCCESS;
}
