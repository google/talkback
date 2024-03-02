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

#include "program.h"
#include "cmdline.h"
#include "crc.h"

static char *opt_algorithmName;
static char *opt_algorithmClass;
static char *opt_checksumWidth;
static char *opt_reflectData;
static char *opt_reflectResult;
static char *opt_generatorPolynomial;
static char *opt_initialValue;
static char *opt_xorMask;
static char *opt_checkValue;
static char *opt_residue;

BEGIN_OPTION_TABLE(programOptions)
  { .word = "name",
    .letter = 'n',
    .argument = "string",
    .setting.string = &opt_algorithmName,
    .description = "the algorithm name"
  },

  { .word = "class",
    .letter = 'c',
    .argument = "string",
    .setting.string = &opt_algorithmClass,
    .description = "the algorithm class"
  },

  { .word = "width",
    .letter = 'w',
    .argument = "integer",
    .setting.string = &opt_checksumWidth,
    .description = "the checksum width"
  },

  { .word = "reflect-data",
    .letter = 'd',
    .argument = "boolean",
    .setting.string = &opt_reflectData,
    .description = "reflect the data"
  },

  { .word = "reflect-result",
    .letter = 'r',
    .argument = "boolean",
    .setting.string = &opt_reflectResult,
    .description = "reflect the result"
  },

  { .word = "polynomial",
    .letter = 'p',
    .argument = "integer",
    .setting.string = &opt_generatorPolynomial,
    .description = "the generator polynomial"
  },

  { .word = "initial-value",
    .letter = 'i',
    .argument = "integer",
    .setting.string = &opt_initialValue,
    .description = "the initial value"
  },

  { .word = "xor-mask",
    .letter = 'x',
    .argument = "integer",
    .setting.string = &opt_xorMask,
    .description = "the final xor mask"
  },

  { .word = "check-value",
    .letter = 'C',
    .argument = "integer",
    .setting.string = &opt_checkValue,
    .description = "the check value"
  },

  { .word = "residue",
    .letter = 'R',
    .argument = "integer",
    .setting.string = &opt_residue,
    .description = "the residue"
  },
END_OPTION_TABLE(programOptions)

static int
validateOptions (void) {
  return 1;
}

int
main (int argc, char *argv[]) {
  {
    const CommandLineDescriptor descriptor = {
      .options = &programOptions,
      .applicationName = "crctest",

      .usage = {
        .purpose = strtext("Test supported CRC (Cyclic Redundancy Check) checksum algorithms."),
      }
    };

    PROCESS_OPTIONS(descriptor, argc, argv);
  }

  if (!validateOptions()) return PROG_EXIT_SYNTAX;

  if (!crcVerifyProvidedAlgorithms()) return PROG_EXIT_FATAL;
  return PROG_EXIT_SUCCESS;
}
