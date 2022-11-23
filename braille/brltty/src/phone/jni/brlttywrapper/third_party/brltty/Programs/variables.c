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

#include <string.h>

#include "log.h"
#include "strfmt.h"
#include "variables.h"
#include "queue.h"
#include "charset.h"

typedef struct {
  const wchar_t *characters;
  int length;
} CharacterString;

static void
initializeCharacterString (CharacterString *string) {
  string->characters = WS_C("\0");
  string->length = 0;
}

static void
clearCharacterString (CharacterString *string) {
  if (string->length > 0) free((void *)string->characters);
  initializeCharacterString(string);
}

static int
setCharacterString (CharacterString *string, const wchar_t *characters, int length) {
  if (!length) {
    clearCharacterString(string);
  } else {
    wchar_t *newCharacters;

    if (!(newCharacters = malloc(ARRAY_SIZE(newCharacters, (length + 1))))) {
      logMallocError();
      return 0;
    }

    wmemcpy(newCharacters, characters, length);
    newCharacters[length] = WC_C('\0');

    clearCharacterString(string);
    string->characters = newCharacters;
    string->length = length;
  }

  return 1;
}

static void
getCharacterString (const CharacterString *string, const wchar_t **characters, int *length) {
  *characters = string->characters;
  *length = string->length;
}

struct VariableStruct {
  CharacterString name;
  CharacterString value;
};

struct VariableNestingLevelStruct {
  const char *name;
  VariableNestingLevel *previous;
  Queue *variables;
  unsigned int references;
};

VariableNestingLevel *
claimVariableNestingLevel (VariableNestingLevel *vnl) {
  vnl->references += 1;
  return vnl;
}

static void
deallocateVariable (void *item, void *data) {
  Variable *variable = item;

  clearCharacterString(&variable->name);
  clearCharacterString(&variable->value);
  free(variable);
}

VariableNestingLevel *
newVariableNestingLevel (VariableNestingLevel *previous, const char *name) {
  VariableNestingLevel *vnl;

  if ((vnl = malloc(sizeof(*vnl)))) {
    memset(vnl, 0, sizeof(*vnl));
    vnl->name = name;
    vnl->references = 0;

    if ((vnl->variables = newQueue(deallocateVariable, NULL))) {
      if ((vnl->previous = previous)) claimVariableNestingLevel(previous);
      return vnl;
    }

    free(vnl);
  } else {
    logMallocError();
  }

  return NULL;
}

static void
destroyVariableNestingLevel (VariableNestingLevel *vnl) {
  deallocateQueue(vnl->variables);
  free(vnl);
}

VariableNestingLevel *
removeVariableNestingLevel (VariableNestingLevel *vnl) {
  VariableNestingLevel *previous = vnl->previous;
  if (!--vnl->references) destroyVariableNestingLevel(vnl);
  return previous;
}

void
releaseVariableNestingLevel (VariableNestingLevel *vnl) {
  while (vnl && !--vnl->references) {
    VariableNestingLevel *previous = vnl->previous;
    destroyVariableNestingLevel(vnl);
    vnl = previous;
  }
}

static void
listVariableLine (const char *line) {
  logMessage(LOG_NOTICE, "%s", line);
}

static int
listVariable (void *item, void *data) {
  const Variable *variable = item;

  char line[0X100];
  STR_BEGIN(line, sizeof(line));

  STR_PRINTF("variable: ");
  STR_PRINTF("%.*" PRIws, variable->name.length, variable->name.characters);
  STR_PRINTF(" = ");
  STR_PRINTF("%.*" PRIws, variable->value.length, variable->value.characters);

  STR_END;
  listVariableLine(line);

  return 0;
}

void
listVariables (VariableNestingLevel *from) {
  listVariableLine("begin variable listing");

  while (from) {
    {
      char header[0X100];
      STR_BEGIN(header, sizeof(header));

      STR_PRINTF("variable nesting level:");
      if (from->name) STR_PRINTF(" %s", from->name);
      if (from->references != 1) STR_PRINTF(" Refs:%u", from->references);

      STR_END;
      listVariableLine(header);
    }

    processQueue(from->variables, listVariable, NULL);
    from = from->previous;
  }

  listVariableLine("end variable listing");
}

static int
testVariableName (const void *item, void *data) {
  const Variable *variable = item;
  const CharacterString *key = data;

  if (variable->name.length == key->length) {
    if (wmemcmp(variable->name.characters, key->characters, key->length) == 0) {
      return 1;
    }
  }

  return 0;
}

static Variable *
findVariable (VariableNestingLevel *vnl, const wchar_t *name, int length, int create) {
  Variable *variable;

  {
    CharacterString key = {
      .characters = name,
      .length = length
    };

    if ((variable = findItem(vnl->variables, testVariableName, &key))) return variable;
  }

  if (create) {
    if ((variable = malloc(sizeof(*variable)))) {
      memset(variable, 0, sizeof(*variable));
      initializeCharacterString(&variable->name);
      initializeCharacterString(&variable->value);

      if (setCharacterString(&variable->name, name, length)) {
        if (enqueueItem(vnl->variables, variable)) {
          return variable;
        }

        clearCharacterString(&variable->name);
      }

      free(variable);
    } else {
      logMallocError();
    }
  }

  return NULL;
}

const Variable *
findReadableVariable (VariableNestingLevel *vnl, const wchar_t *name, int length) {
  while (vnl) {
    Variable *variable = findVariable(vnl, name, length, 0);
    if (variable) return variable;
    vnl = vnl->previous;
  }

  return NULL;
}

Variable *
findWritableVariable (VariableNestingLevel *vnl, const wchar_t *name, int length) {
  return findVariable(vnl, name, length, 1);
}

void
deleteVariables (VariableNestingLevel *vnl) {
  deleteElements(vnl->variables);
}

int
setVariable (Variable *variable, const wchar_t *value, int length) {
  return setCharacterString(&variable->value, value, length);
}

void
getVariableName (const Variable *variable, const wchar_t **characters, int *length) {
  getCharacterString(&variable->name, characters, length);
}

void
getVariableValue (const Variable *variable, const wchar_t **characters, int *length) {
  getCharacterString(&variable->value, characters, length);
}

int
setStringVariable (VariableNestingLevel *vnl, const char *name, const char *value) {
  size_t nameLength = getUtf8Length(name);
  wchar_t nameBuffer[nameLength + 1];

  size_t valueLength = getUtf8Length(value);
  wchar_t valueBuffer[valueLength + 1];

  {
    const char *utf8 = name;
    wchar_t *wc = nameBuffer;
    convertUtf8ToWchars(&utf8, &wc, ARRAY_COUNT(nameBuffer));
  }

  {
    const char *utf8 = value;
    wchar_t *wc = valueBuffer;
    convertUtf8ToWchars(&utf8, &wc, ARRAY_COUNT(valueBuffer));
  }

  Variable *variable = findVariable(vnl, nameBuffer, nameLength, 1);

  if (variable) {
    if (setVariable(variable, valueBuffer, valueLength)) {
      return 1;
    }
  }

  return 0;
}

int
setStringVariables (VariableNestingLevel *vnl, const VariableInitializer *initializers) {
  if (initializers) {
    const VariableInitializer *initializer = initializers;
     
    while (initializer->name) {
      if (!setStringVariable(vnl, initializer->name, initializer->value)) return 0;
      initializer += 1;
    }
  }

  return 1;
}

VariableNestingLevel *
getGlobalVariables (int create) {
  static VariableNestingLevel *globalVariables = NULL;

  if (!globalVariables) {
    VariableNestingLevel *vnl;

    if (!(vnl = newVariableNestingLevel(NULL, "global"))) {
      return NULL;
    }

    claimVariableNestingLevel(vnl);
    globalVariables = vnl;
  }

  return globalVariables;
}

int
setGlobalVariable (const char *name, const char *value) {
  VariableNestingLevel *vnl = getGlobalVariables(1);
  if (!vnl) return 0;
  return setStringVariable(vnl, name, value);
}
