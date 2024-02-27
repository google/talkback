/*
 * Copyright (C) 2015 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

/*
 * This is a library to expose a brlapi-like interface that can be linked
 * into another binary.  The intended use is on Android, compiled under
 * the NDK, meaning that some system and I/O abstractons must be
 * provided by the user of the library.
 */

#include "third_party/brltty/Headers/prologue.h"  // NOLINT Must include first

#include "libbrltty.h"
#include "third_party/brltty/Programs/core.h"
#include "third_party/brltty/Programs/api_control.h"
#include "third_party/brltty/Programs/scr.h"
#include "third_party/brltty/Headers/async_wait.h"
#include "third_party/brltty/Headers/brl_cmds.h"
#include "third_party/brltty/Headers/cmd.h"
#include "third_party/brltty/Headers/file.h"
#include "third_party/brltty/Headers/ktb.h"
#include "third_party/brltty/Headers/ktb_types.h"
#include "third_party/brltty/Headers/log.h"
#include "third_party/brltty/Headers/parse.h"
#include "third_party/brltty/Headers/brl_utils.h"
#include "third_party/brltty/Programs/cmd_queue.h"
#include "third_party/brltty/Headers/prefs.h"
#include "third_party/brltty/Headers/queue.h"
#include "third_party/brltty/Headers/timing.h"
#include "third_party/brltty/Programs/brl.h"
#include "third_party/brltty/Programs/cmd_queue.h"
#include "third_party/brltty/Headers/parse.h"
#include "third_party/brltty/config.h"
#include "third_party/brltty/Programs/ktb_internal.h" // NOLINT Must precede ktb_inspect.h
#include "third_party/brltty/Programs/ktb_inspect.h"
#include "third_party/brltty/Programs/update.h"

// textStart and textCount are taken from Programs/brltty.c. We declare them
// here so we don't have to include the entire brltty.c file.
unsigned int textStart = 0;
unsigned int textCount;

/*
 * The global variable 'braille' is the driver struct with vtable etc.  It is
 * declared in brl.h and defined in brl.c.  It is used in this file to be
 * consistent with the rest of brltty, meaning that we can only have one
 * driver loaded per address space.  Therefore, we declare the rest of the
 * variables we need static for simplicity.
 */

/*
 * Set to non-NULL when shared objects are used.
 */
static void* brailleSharedObject = NULL;

/*
 * Display struct, containing data for a particular display
 * (dimensions, the display buffer etc).
 */
static BrailleDisplay brailleDisplay;

/*
 * Array of driver-specific parameters.
 */
static char** driverParameters = NULL;

/*
 * Queue of unprocessed commands.
 */
static Queue *commandQueue = NULL;

static int
createEmptyDriverParameters (void);

static void
freeDriverParameters(void);

static int
compileKeys(const char* tablesDir);

static char *
getKeyTablePath(const char *tablesDir);

static int
listKeyContext(const KeyContext *context, const KeyTable* keyTable,
               KeyMapEntryCallback callback, void* data);
static int
listKeyBinding(const KeyBinding *binding, const KeyTable* keyTable,
               KeyMapEntryCallback callback, void* data);
static const char*
findKeyName(const KeyTable* keyTable, const KeyValue* value);

int
brltty_initialize (const char* driverCode, const char* brailleDevice,
                   const char* tablesDir) {
  int ret = 0;
  systemLogLevel = LOG_DEBUG;

  logMessage(LOG_DEBUG, "Loading braille driver %s", driverCode);
  setNoScreen();
  braille = loadBrailleDriver(driverCode, &brailleSharedObject, NULL);
  if (!braille) {
    logMessage(LOG_ERR, "Couldn't load braille driver %s.", driverCode);
    goto out;
  }

  logMessage(LOG_DEBUG, "Initializing braille driver");
  constructBrailleDisplay(&brailleDisplay);

  logMessage(LOG_DEBUG, "Identifying braille driver");
  identifyBrailleDriver(braille, 1);

  driverParameters = getParameters(braille->parameters,
                                              driverCode,
                                              brailleDevice);
  if (!driverParameters) {
    goto unloadDriver;
  }

  logParameters(braille->parameters, driverParameters,
                      gettext("Braille Parameter"));
  logMessage(LOG_DEBUG, "Constructing braille driver");
  if (!braille->construct(&brailleDisplay, driverParameters, brailleDevice)) {
    logMessage(LOG_ERR, "Couldn't initialize braille driver %s on device %s",
               driverCode, driverCode);
    goto freeParameters;
  }

  if (brltty_getTextCells() > BRLTTY_MAX_TEXT_CELLS) {
    logMessage(LOG_ERR, "Unsupported display size: %d",
               brltty_getTextCells());
    goto destructBraille;
  }

  if (!compileKeys(tablesDir)) {
    goto destructBraille;
  }

  textCount = brltty_getTextCells();

  // TODO: Should set bufferResized to catch buffer size changes if we want to
  // signal those to the screen reader, which is probably useful.
  logMessage(LOG_DEBUG, "Allocating braille buffer");
  if (!ensureBrailleBuffer(&brailleDisplay, LOG_INFO)) {
    logMessage(LOG_ERR, "Couldn't allocate braille buffer");
    goto destructBraille;
  }

  // Register our command handler to capture braille key events.
  beginCommandQueue();
  pushCommandHandler("libbrltty-android", KTB_CTX_DEFAULT,
  brltty_handleCommand, NULL /* destroy handler */, NULL /* data */);

  commandQueue = newQueue(NULL, NULL);

  logMessage(LOG_NOTICE, "Successfully initialized braille driver "
             "%s on device %s", driverCode, brailleDevice);
  ret = 1;
  goto out;

destructBraille:
  braille->destruct(&brailleDisplay);

freeParameters:
  freeDriverParameters();

unloadDriver:
  /* No unloading yet. */
  destructBrailleDisplay(&brailleDisplay);
  braille = NULL;

out:
  return ret;
}

int
brltty_destroy(void) {
  if (braille == NULL) {
    // braille might has been null because starting failed.
    logMessage(LOG_ERR, "Double destruction of braille driver");
    return 0;
  }
  destructBrailleDisplay(&brailleDisplay);

  deallocateQueue(commandQueue);
  commandQueue = NULL;

  popCommandHandler();
  endCommandQueue();

  braille->destruct(&brailleDisplay);
  freeDriverParameters();
  braille = NULL;
  return 0;
}

int
brltty_readCommand(int *readDelayMillis) {
  if (braille == NULL) {
    return BRL_CMD_RESTARTBRL;
  }

  readBrailleCommand(&brailleDisplay, KTB_CTX_DEFAULT);

  // Fake async by emptying the brltty event queue and then re-reading with a
  // delay. This allows us to deal with long-press and autorepeat.
  // TODO: Make long-pressing on routing keys work again.
  asyncWait(1);
  if (brailleDisplay.keyTable->release.command != BRL_CMD_NOOP ||
       brailleDisplay.keyTable->longPress.command != BRL_CMD_NOOP) {
    // Essentially, loop and re-read at 1/2 of the repeat interval.
    // The interval pref is in csec; we want msec: x * 10 / 2 = x * 5.
    *readDelayMillis = prefs.autorepeatInterval * 5;
  }

  return brltty_popCommand();
}

typedef struct {
  int command;
} CommandQueueItem;

int
brltty_handleCommand(int command, void *data) {
  if (!commandQueue) {
    return 0;
  }

  CommandQueueItem *item = malloc(sizeof(CommandQueueItem));
  if (item) {
    item->command = command;

    if (enqueueItem(commandQueue, item)) {
      return 1;
    }
    free(item);
  } else {
    logMallocError();
  }

  return 0;
}

int
brltty_popCommand() {
  if (!commandQueue) {
    return EOF;
  }

  CommandQueueItem *item;
  if ((item = dequeueItem(commandQueue))) {
    int command = item->command;
    free(item);
    item = NULL;
    return command;
  }

  return EOF;
}

int
brltty_writeWindow(unsigned char *dotPattern, size_t patternSize) {
  if (braille == NULL) {
    return 0;
  }
  size_t bufSize = brailleDisplay.textColumns * brailleDisplay.textRows;
  if (patternSize > bufSize) {
    patternSize = bufSize;
  }
  memcpy(brailleDisplay.buffer, dotPattern, patternSize);
  if (patternSize < bufSize) {
    memset(brailleDisplay.buffer + patternSize, 0, bufSize - patternSize);
  }
  return braille->writeWindow(&brailleDisplay, NULL);
}


int
brltty_getTextCells(void) {
  return brailleDisplay.textColumns * brailleDisplay.textRows;
}

int
brltty_getStatusCells(void) {
  return brailleDisplay.statusRows * brailleDisplay.statusColumns;
}

/*
 * Creates an array of empty strings, storing a pointer to the array in
 * the global variable driverParameters.  The size of the array
 * corresponds to the number of parameters expected by the current
 * driver.
 */
static int
createEmptyDriverParameters (void) {
  const char *const *parameterNames = braille->parameters;
  int count = 0;
  int i;
  if (!parameterNames) {
    static const char *const noNames[] = {NULL};
    parameterNames = noNames;
  }

  while (parameterNames[count] != NULL) {
    ++count;
  }
  if (!(driverParameters = malloc((count + 1) * sizeof(*driverParameters)))) {
    logMessage(LOG_ERR, "insufficient memory.");
    return 0;
  }
  for (i = 0; i < count; ++i) {
    driverParameters[i] = "";
  }
  return 1;
}

static void
freeDriverParameters() {
  free(driverParameters);
  driverParameters = NULL;
}

static int
compileKeys(const char *tablesDir) {
  if (brailleDisplay.keyNames != NULL) {
    char* path = getKeyTablePath(tablesDir);
    if (path == NULL) {
      logMessage(LOG_ERR, "Couldn't construct key table filename");
      return 0;
    }
    brailleDisplay.keyTable = compileKeyTable(path,
                                              brailleDisplay.keyNames);
    if (brailleDisplay.keyTable != NULL) {
      setLogKeyEventsFlag(brailleDisplay.keyTable, "");
    } else {
      logMessage(LOG_ERR, "Couldn't compile key table %s", path);
    }
    free(path);
    return brailleDisplay.keyTable != NULL;
  } else {
    return 1;
  }
}

static char *
getKeyTablePath(const char *tablesDir) {
  char *fileName;
  const char *strings[] = {
    braille->definition.code, "/", brailleDisplay.keyBindings,
    KEY_TABLE_EXTENSION
  };
  fileName = joinStrings(strings, ARRAY_COUNT(strings));
  if (fileName == NULL) {
    return NULL;
  }

  char *path = makePath(tablesDir, fileName);
  free(fileName);
  return path;
}

int
brltty_listKeyMap(KeyMapEntryCallback callback, void* data) {
  KeyTable* keyTable = brailleDisplay.keyTable;
  if (keyTable == NULL) {
    logMessage(LOG_ERR, "No key table to list");
    return 0;
  }
  const KeyContext *context = getKeyContext(keyTable, KTB_CTX_DEFAULT);
  if (context == NULL) {
    logMessage(LOG_ERR, "Can't get default key context");
    return 0;
  }
  return listKeyContext(context, keyTable, callback, data);
}

static int
listKeyContext(const KeyContext *context, const KeyTable* keyTable,
               KeyMapEntryCallback callback,
               void *data) {
  int i;
  for (i = 0; i < context->keyBindings.count; ++i) {
    const KeyBinding* binding = &context->keyBindings.table[i];
    if (binding->flags & KBF_HIDDEN) {
      continue;
    }
    if (!listKeyBinding(binding, keyTable, callback, data)) {
      return 0;
    }
  }
  return 1;
}

static int
listKeyBinding(const KeyBinding *binding, const KeyTable *keyTable,
               KeyMapEntryCallback callback, void *data) {
  // Allow room for all modifiers, the immediate key and a terminating NULL.
  const char *keys[MAX_MODIFIERS_PER_COMBINATION + 2];
  int i;
  const KeyCombination *combination = &binding->keyCombination;
  for (i = 0; i < combination->modifierCount; ++i) {
    // Key values are sorted in this list for quick comparison,
    // the modifierPositions array is ordered according to how the
    // keys were entered in the keymap file and maps to the sort order.
    int position = combination->modifierPositions[i];
    const KeyValue* value = &combination->modifierKeys[position];
    const char *name = findKeyName(keyTable, value);
    if (name == NULL) {
      return 0;
    }
    keys[i] = name;
  }
  if (combination->flags & KCF_IMMEDIATE_KEY) {
    const char *name = findKeyName(keyTable, &combination->immediateKey);
    if (name == NULL) {
      return 0;
    }
    keys[i++] = name;
  }
  keys[i] = NULL;
  int ret = callback(binding->primaryCommand.value, i, keys, 0 /*isLongPress*/,
      data);
  if (!ret) {
    return ret;
  }
  /* Since we implement long press automatically, add a corresponding
   * binding to the route command if this route command isn't already
   * a long press in the key table.
   * Only do this if immediatekey flag is not active since in that
   * case longpress isn't possible.
   */
  int route = (binding->primaryCommand.value &
      (BRL_MSK_BLK | BRLTTY_ROUTE_ARG_FLG_LONG_PRESS)) == BRL_CMD_BLK(ROUTE);
  int immediate = combination->flags & KCF_IMMEDIATE_KEY;
  if (route && !immediate) {
    ret = callback(
        binding->primaryCommand.value | BRLTTY_ROUTE_ARG_FLG_LONG_PRESS,
        i, keys, 1 /*isLongPress*/, data);
  }
  return ret;
}

static int
compareValueToKeyNameEntry(const void *key, const void *element) {
  const KeyValue* value = key;
  const KeyNameEntry *const *nameEntry = element;
  return compareKeyValues(value, &(*nameEntry)->value);
}

static const char *
findKeyName(const KeyTable *keyTable, const KeyValue *value) {
  const KeyNameEntry **entries = keyTable->keyNames.table;
  int entryCount = keyTable->keyNames.count;
  const KeyNameEntry **entry = bsearch(value, entries, entryCount,
                                      sizeof(KeyNameEntry*),
                                      compareValueToKeyNameEntry);
  if (entry != NULL) {
    return (*entry)->name;
  } else {
    logMessage(LOG_ERR, "No key name for key [%d, %d]",
               value->group, value->number);
    return NULL;
  }
}
