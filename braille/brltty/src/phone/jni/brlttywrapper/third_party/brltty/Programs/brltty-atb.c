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

#include "program.h"
#include "options.h"
#include "log.h"
#include "atb.h"

static char *opt_tablesDirectory;

BEGIN_OPTION_TABLE(programOptions)
  { .letter = 'T',
    .word = "tables-directory",
    .flags = OPT_Hidden,
    .argument = strtext("directory"),
    .setting.string = &opt_tablesDirectory,
    .internal.setting = TABLES_DIRECTORY,
    .internal.adjust = fixInstallPath,
    .description = strtext("Path to directory containing tables.")
  },
END_OPTION_TABLE

int
main (int argc, char *argv[]) {
  ProgramExitStatus exitStatus = PROG_EXIT_SUCCESS;

  {
    static const OptionsDescriptor descriptor = {
      OPTION_TABLE(programOptions),
      .applicationName = "brltty-atb",
      .argumentsSummary = "attributes-table"
    };
    PROCESS_OPTIONS(descriptor, argc, argv);
  }

  if (argc) {
    const char *tableName = (argc--, *argv++);
    char *tablePath = makeAttributesTablePath(opt_tablesDirectory, tableName);

    if (tablePath) {
      if ((attributesTable = compileAttributesTable(tablePath))) {
        exitStatus = PROG_EXIT_SUCCESS;

        destroyAttributesTable(attributesTable);
      } else {
        exitStatus = PROG_EXIT_FATAL;
      }

      free(tablePath);
    } else {
      exitStatus = PROG_EXIT_FATAL;
    }
  } else {
    logMessage(LOG_ERR, "missing attributes table name");
    exitStatus = PROG_EXIT_SYNTAX;
  }

  return exitStatus;
}
