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
#include <search.h>

#include "log.h"
#include "program.h"
#include "options.h"
#include "file.h"

BEGIN_OPTION_TABLE(programOptions)
END_OPTION_TABLE

static void
noMemory (void) {
  fprintf(stderr, "%s: insufficient memory\n", programName);
  exit(PROG_EXIT_FATAL);
}

static int
compareStrings (const void *string1, const void *string2) {
  return strcmp(string1, string2);
}

static void
logFileName (const char *name, void *data) {
  static void *namesTree = NULL;

  if (!tfind(name, &namesTree, compareStrings)) {
    name = strdup(name);
    if (!name) noMemory();
    if (!tsearch(name, &namesTree, compareStrings)) noMemory();
    printf("%s\n", name);
  }
}

static DATA_CONDITION_TESTER(testConditionOperand) {
  return 1;
}

static DATA_OPERANDS_PROCESSOR(processUnknownDirective) {
  DataOperand directive;

  if (getDataOperand(file, &directive, NULL)) {
    if (directive.length >= 2) {
      if (isKeyword(WS_C("if"), directive.characters, 2)) {
        return processConditionOperands(file, testConditionOperand, 0, NULL, data);
      }
    }
  }

  return 1;
}

static DATA_OPERANDS_PROCESSOR(processOperands) {
  BEGIN_DATA_DIRECTIVE_TABLE
    DATA_NESTING_DIRECTIVES,
    DATA_CONDITION_DIRECTIVES,
    DATA_VARIABLE_DIRECTIVES,
    {NULL, processUnknownDirective},
  END_DATA_DIRECTIVE_TABLE

  return processDirectiveOperand(file, &directives, "attributes table directive", data);
}

int
main (int argc, char *argv[]) {
  ProgramExitStatus exitStatus;

  {
    static const OptionsDescriptor descriptor = {
      OPTION_TABLE(programOptions),
      .applicationName = "brltty-lsinc",
      .argumentsSummary = "file ..."
    };

    PROCESS_OPTIONS(descriptor, argc, argv);
  }

  if (argc == 0) {
    logMessage(LOG_ERR, "missing file");
    exitStatus = PROG_EXIT_SYNTAX;
  } else {
    exitStatus = PROG_EXIT_SUCCESS;

    do {
      const char *path = *argv++;
      argc -= 1;

      const DataFileParameters parameters = {
        .processOperands = processOperands,
        .logFileName = logFileName
      };

      if (testProgramPath(path)) {
        logFileName(path, parameters.data);
      } else if (!processDataFile(path, &parameters)) {
        exitStatus = PROG_EXIT_SEMANTIC;
      }
    } while (argc);
  }

  return exitStatus;
}
