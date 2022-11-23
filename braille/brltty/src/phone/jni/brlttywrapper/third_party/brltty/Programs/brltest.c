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
#include <strings.h>
#include <errno.h>

#include "program.h"
#include "options.h"
#include "parameters.h"
#include "log.h"
#include "parse.h"
#include "file.h"
#include "cmd_queue.h"
#include "brl.h"
#include "brl_input.h"
#include "brl_utils.h"
#include "ttb.h"
#include "ktb.h"
#include "message.h"
#include "charset.h"
#include "async_wait.h"
#include "learn.h"

BrailleDisplay brl;

static char *opt_brailleDevice;
static char *opt_driversDirectory;
static char *opt_tablesDirectory;
static char *opt_writableDirectory;

BEGIN_OPTION_TABLE(programOptions)
  { .letter = 'D',
    .word = "drivers-directory",
    .flags = OPT_Hidden,
    .argument = "directory",
    .setting.string = &opt_driversDirectory,
    .internal.setting = DRIVERS_DIRECTORY,
    .internal.adjust = fixInstallPath,
    .description = "Path to directory for loading drivers."
  },

  { .letter = 'T',
    .word = "tables-directory",
    .flags = OPT_Hidden,
    .argument = strtext("directory"),
    .setting.string = &opt_tablesDirectory,
    .internal.setting = TABLES_DIRECTORY,
    .internal.adjust = fixInstallPath,
    .description = strtext("Path to directory containing tables.")
  },

  { .letter = 'W',
    .word = "writable-directory",
    .flags = OPT_Hidden,
    .argument = strtext("directory"),
    .setting.string = &opt_writableDirectory,
    .internal.setting = WRITABLE_DIRECTORY,
    .internal.adjust = fixInstallPath,
    .description = strtext("Path to directory which can be written to.")
  },

  { .letter = 'd',
    .word = "device",
    .argument = "device",
    .setting.string = &opt_brailleDevice,
    .internal.setting = BRAILLE_DEVICE,
    .description = "Path to device for accessing braille display."
  },
END_OPTION_TABLE

int
main (int argc, char *argv[]) {
  ProgramExitStatus exitStatus;

  const char *driver = NULL;
  void *object;

  {
    static const OptionsDescriptor descriptor = {
      OPTION_TABLE(programOptions),
      .applicationName = "brltest",
      .argumentsSummary = "[driver [parameter=value ...]]"
    };

    PROCESS_OPTIONS(descriptor, argc, argv);
  }

  setWritableDirectory(opt_writableDirectory);

  if (argc) {
    driver = *argv++, --argc;
  }

  if (!*opt_brailleDevice) {
    changeStringSetting(&opt_brailleDevice, BRAILLE_DEVICE);
  }

  if ((braille = loadBrailleDriver(driver, &object, opt_driversDirectory))) {
    const char *const *parameterNames = braille->parameters;
    char **parameterSettings;

    if (!parameterNames) {
      static const char *const noNames[] = {NULL};

      parameterNames = noNames;
    }

    {
      const char *const *name = parameterNames;
      unsigned int count;
      char **setting;

      while (*name) name += 1;
      count = name - parameterNames;

      if (!(parameterSettings = malloc((count + 1) * sizeof(*parameterSettings)))) {
        logMallocError();
        return PROG_EXIT_FATAL;
      }

      setting = parameterSettings;
      while (count--) *setting++ = "";
      *setting = NULL;
    }

    while (argc) {
      char *assignment = *argv++;
      int ok = 0;
      char *delimiter = strchr(assignment, '=');

      if (!delimiter) {
        logMessage(LOG_ERR, "missing braille driver parameter value: %s", assignment);
      } else if (delimiter == assignment) {
        logMessage(LOG_ERR, "missing braille driver parameter name: %s", assignment);
      } else {
        size_t nameLength = delimiter - assignment;
        const char *const *name = parameterNames;

        while (*name) {
          if (strncasecmp(assignment, *name, nameLength) == 0) {
            parameterSettings[name - parameterNames] = delimiter + 1;
            ok = 1;
            break;
          }

          name += 1;
        }

        if (!ok) logMessage(LOG_ERR, "invalid braille driver parameter: %s", assignment);
      }

      if (!ok) return PROG_EXIT_SYNTAX;
      argc -= 1;
    }

    constructBrailleDisplay(&brl);
    identifyBrailleDriver(braille, 0);		/* start-up messages */

    if (braille->construct(&brl, parameterSettings, opt_brailleDevice)) {
      if (ensureBrailleBuffer(&brl, LOG_INFO)) {
        if (brl.keyNames) {
          char *path = makeInputTablePath(opt_tablesDirectory, braille->definition.code, brl.keyBindings);

          if (path) {
            brl.keyTable = compileKeyTable(path, brl.keyNames);

            free(path);
          }
        }

        beginCommandQueue();
        startBrailleInput();
        learnMode(10000);
        stopBrailleInput();

        if (brl.keyTable) {
          KeyTable *table = brl.keyTable;

          brl.keyTable = NULL;
          destroyKeyTable(table);
        }

        braille->destruct(&brl);		/* finish with the display */
        exitStatus = PROG_EXIT_SUCCESS;
      } else {
        logMessage(LOG_ERR, "can't allocate braille buffer");
        exitStatus = PROG_EXIT_FATAL;
      }
    } else {
      logMessage(LOG_ERR, "can't initialize braille driver");
      exitStatus = PROG_EXIT_FATAL;
    }
  } else {
    logMessage(LOG_ERR, "can't load braille driver");
    exitStatus = PROG_EXIT_FATAL;
  }

  return exitStatus;
}

typedef struct {
  unsigned endWait:1;
} MessageData;

static int
handleMessageCommands (int command, void *data) {
  MessageData *mgd = data;

  mgd->endWait = 1;
  return 1;
}

ASYNC_CONDITION_TESTER(testEndMessageWait) {
  const MessageData *mgd = data;

  return mgd->endWait;
}

int
message (const char *mode, const char *text, MessageOptions options) {
  MessageData mgd;

  size_t size = brl.textColumns * brl.textRows;
  wchar_t buffer[size];

  size_t length = getTextLength(text);
  wchar_t characters[length + 1];
  const wchar_t *character = characters;

  clearStatusCells(&brl);
  convertTextToWchars(characters, text, ARRAY_COUNT(characters));

  pushCommandEnvironment("message", NULL, NULL);
  pushCommandHandler("message", KTB_CTX_WAITING,
                     handleMessageCommands, NULL, &mgd);

  while (length) {
    size_t count = (length <= size)? length: (size - 1);

    wmemcpy(buffer, character, count);
    character += count;
    length -= count;

    if (length) {
      buffer[(count = size) - 1] = WC_C('-');
    }
    wmemset(&buffer[count], WC_C(' '), (size - count));

    {
      unsigned int i;

      for (i=0; i<size; i+=1) {
        brl.buffer[i] = convertCharacterToDots(textTable, buffer[i]);
      }
    }

    if (!braille->writeWindow(&brl, buffer)) return 0;

    if (length) {
      int delay = MESSAGE_HOLD_TIMEOUT - brl.writeDelay;

      mgd.endWait = 0;
      drainBrailleOutput(&brl, 0);
      asyncAwaitCondition(delay, testEndMessageWait, &mgd);
    }
  }

  popCommandEnvironment();
  return 1;
}

#include "scr.h"

KeyTableCommandContext
getScreenCommandContext (void) {
  return KTB_CTX_DEFAULT;
}

#include "alert.h"

void
alert (AlertIdentifier identifier) {
}
