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
#include <errno.h>

#include "log.h"
#include "cmdline.h"
#include "datafile.h"
#include "utf8.h"
#include "brlapi.h"

static char *opt_apiHost;
static char *opt_authSchemes;
static int opt_getContent;
static char *opt_setContent;
static int opt_removeNewline;

BEGIN_OPTION_TABLE(programOptions)
  { .word = "brlapi",
    .letter = 'b',
    .argument = "[host][:port]",
    .setting.string = &opt_apiHost,
    .description = "BrlAPIa host and/or port to connect to."
  },

  { .word = "auth",
    .letter = 'a',
    .argument = "scheme+...",
    .setting.string = &opt_authSchemes,
    .description = "BrlAPI authorization/authentication schemes."
  },

  { .word = "get-content",
    .letter = 'g',
    .setting.flag = &opt_getContent,
    .description = "Write the content of the clipboard to standard output."
  },

  { .word = "set-content",
    .letter = 's',
    .argument = "content",
    .setting.string = &opt_setContent,
    .description = "Set the content of the clipboard."
  },

  { .word = "remove-newline",
    .letter = 'r',
    .setting.flag = &opt_removeNewline,
    .description = "Remove a trailing newline."
  },
END_OPTION_TABLE(programOptions)

static const brlapi_param_t apiParameter = BRLAPI_PARAM_CLIPBOARD_CONTENT;
static const brlapi_param_subparam_t apiSubparam = 0;
static const brlapi_param_flags_t apiFlags = BRLAPI_PARAMF_GLOBAL;

static char *
getClipboardContent (void) {
  return brlapi_getParameterAlloc(apiParameter, apiSubparam, apiFlags, NULL);
}

static int
setClipboardContent (const char *content, size_t length) {
  if (opt_removeNewline) {
    if (length > 0) {
      size_t newLength = length - 1;
      if (content[newLength] == '\n') length = newLength;
    }
  }

  return brlapi_setParameter(apiParameter, apiSubparam, apiFlags, content, length) >= 0;
}

typedef struct {
  struct {
    wchar_t *characters;
    size_t size;
    size_t count;
  } content;
} LineProcessingData;

static int
addContent (LineProcessingData *lpd, const wchar_t *characters, size_t count) {
  {
    size_t newSize = lpd->content.count + count;

    if (newSize > lpd->content.size) {
      newSize |= 0XFFF;
      newSize += 1;
      wchar_t *newCharacters = allocateCharacters(newSize);

      if (!newCharacters) {
        logMallocError();
        return 0;
      }

      if (lpd->content.characters) {
        wmemcpy(newCharacters, lpd->content.characters, lpd->content.count);
      }

      lpd->content.characters = newCharacters;
      lpd->content.size = newSize;
    }
  }

  wmemcpy(&lpd->content.characters[lpd->content.count], characters, count);
  lpd->content.count += count;
  return 1;
}

static DATA_OPERANDS_PROCESSOR(processInputLine) {
  LineProcessingData *lpd = data;

  {
    DataOperand line;
    getTextRemaining(file, &line);
    if (!addContent(lpd, line.characters, line.length)) return 0;
  }

  {
    static const wchar_t delimiter[] = {WC_C('\n')};
    return addContent(lpd, delimiter, ARRAY_COUNT(delimiter));
  }
}

int
main (int argc, char *argv[]) {
  ProgramExitStatus exitStatus = PROG_EXIT_FATAL;

  {
    const CommandLineDescriptor descriptor = {
      .options = &programOptions,
      .applicationName = "brltty-clip",

      .usage = {
        .purpose = strtext("Manage brltty's clipboard from the command line."),
        .parameters = "[{input-file | -} ...]",
      }
    };

    PROCESS_OPTIONS(descriptor, argc, argv);
  }

  brlapi_connectionSettings_t settings = {
    .host = opt_apiHost,
    .auth = opt_authSchemes
  };

  brlapi_fileDescriptor fileDescriptor = brlapi_openConnection(&settings, &settings);

  if (fileDescriptor != (brlapi_fileDescriptor)(-1)) {
    char *oldContent = NULL;

    LineProcessingData lpd = {
      .content = {
        .characters = NULL,
        .size = 0,
        .count = 0
      }
    };

    int getContent = !!opt_getContent;
    int setContent = !!*opt_setContent;

    if (!(getContent || setContent)) {
      const InputFilesProcessingParameters parameters = {
        .dataFileParameters = {
          .options = DFO_NO_COMMENTS,
          .processOperands = processInputLine,
          .data = &lpd
        }
      };

      exitStatus = processInputFiles(argv, argc, &parameters);
    } else if (argc > 0) {
      logMessage(LOG_ERR, "too many arguments");
      exitStatus = PROG_EXIT_SYNTAX;
    } else {
      exitStatus = PROG_EXIT_SUCCESS;
    }

    if (exitStatus == PROG_EXIT_SUCCESS) {
      if (getContent) {
        oldContent = getClipboardContent();
        if (!oldContent) exitStatus = PROG_EXIT_FATAL;
      }
    }

    if (exitStatus == PROG_EXIT_SUCCESS) {
      if (setContent) {
        if (!setClipboardContent(opt_setContent, strlen(opt_setContent))) {
          exitStatus = PROG_EXIT_FATAL;
        }
      }
    }

    if (lpd.content.characters) {
      if (exitStatus == PROG_EXIT_SUCCESS) {
        exitStatus = PROG_EXIT_FATAL;

        size_t length;
        char *content = getUtf8FromWchars(lpd.content.characters, lpd.content.count, &length);

        if (content) {
          if (setClipboardContent(content, length)) {
            exitStatus = PROG_EXIT_SUCCESS;
          }

          free(content);
        }
      }

      free(lpd.content.characters);
    }

    if (oldContent) {
      if (exitStatus == PROG_EXIT_SUCCESS) {
        if (opt_removeNewline) {
          size_t length = strlen(oldContent);

          if (length > 0) {
            if (oldContent[--length] == '\n') {
              oldContent[length] = 0;
            }
          }
        }

        printf("%s", oldContent);

        if (ferror(stdout)) {
          logMessage(LOG_ERR, "standard output write error: %s", strerror(errno));
          exitStatus = PROG_EXIT_FATAL;
        }
      }

      free(oldContent);
    }

    brlapi_closeConnection();
  } else {
    logMessage(LOG_ERR, "failed to connect to %s using auth %s: %s",
               settings.host, settings.auth, brlapi_strerror(&brlapi_error));
  }

  return exitStatus;
}
