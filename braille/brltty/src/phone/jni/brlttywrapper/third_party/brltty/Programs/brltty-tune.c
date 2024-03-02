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
#include "cmdline.h"
#include "prefs.h"
#include "tune_utils.h"
#include "tune_builder.h"
#include "notes.h"
#include "datafile.h"

static int opt_fromFiles;
static char *opt_outputVolume;
static char *opt_tuneDevice;

#ifdef HAVE_MIDI_SUPPORT
static char *opt_midiInstrument;
#endif /* HAVE_MIDI_SUPPORT */

BEGIN_OPTION_TABLE(programOptions)
  { .word = "files",
    .letter = 'f',
    .setting.flag = &opt_fromFiles,
    .description = "Use files rather than command line arguments."
  },

  { .word = "volume",
    .letter = 'v',
    .argument = "loudness",
    .setting.string = &opt_outputVolume,
    .description = "Output volume (percentage)."
  },

  { .word = "device",
    .letter = 'd',
    .argument = "device",
    .setting.string = &opt_tuneDevice,
    .description = "Name of tune device."
  },

#ifdef HAVE_PCM_SUPPORT
  { .word = "pcm-device",
    .letter = 'p',
    .argument = "device",
    .setting.string = &opt_pcmDevice,
    .description = "Device specifier for soundcard digital audio."
  },
#endif /* HAVE_PCM_SUPPORT */

#ifdef HAVE_MIDI_SUPPORT
  { .word = "midi-device",
    .letter = 'm',
    .argument = "device",
    .setting.string = &opt_midiDevice,
    .description = "Device specifier for the Musical Instrument Digital Interface."
  },

  { .word = "instrument",
    .letter = 'i',
    .argument = "instrument",
    .setting.string = &opt_midiInstrument,
    .description = "Name of MIDI instrument."
  },
#endif /* HAVE_MIDI_SUPPORT */
END_OPTION_TABLE(programOptions)

static
BEGIN_USAGE_NOTES(usageNotes)
  "If the tune is specified on the command line then each argument contains a command group.",
  "If it's read from a file then each line contains a command group.",
  "Each specified file contains a different tune.",
END_USAGE_NOTES

static void
beginTuneStream (const char *name, void *data) {
  TuneBuilder *tb = data;
  resetTuneBuilder(tb);
  setTuneSourceName(tb, name);
}

static void
playTune (TuneBuilder *tb) {
  ToneElement *tune = getTune(tb);

  if (tune) {
    tunePlayTones(tune);
    tuneSynchronize();
    free(tune);
  }
}

static void
endTuneStream (int incomplete, void *data) {
  if (!incomplete) {
    TuneBuilder *tb = data;
    playTune(tb);
  }
}

static
DATA_OPERANDS_PROCESSOR(processTuneOperands) {
  DataOperand line;

  if (getTextOperand(file, &line, NULL)) {
    DataString text;

    if (parseDataString(file, &text, line.characters, line.length, 0)) {
      return parseTuneText(data, text.characters);
    }
  }

  return 1;
}

static
DATA_OPERANDS_PROCESSOR(processTuneLine) {
  TuneBuilder *tb = data;
  incrementTuneSourceIndex(tb);

  BEGIN_DATA_DIRECTIVE_TABLE
    DATA_NESTING_DIRECTIVES,
    DATA_VARIABLE_DIRECTIVES,
    DATA_CONDITION_DIRECTIVES,
    {.name=NULL, .processor=processTuneOperands},
  END_DATA_DIRECTIVE_TABLE

  return processDirectiveOperand(file, &directives, "tune file directive", tb);
}

int
main (int argc, char *argv[]) {
  {
    const CommandLineDescriptor descriptor = {
      .options = &programOptions,
      .applicationName = "brltty-tune",

      .usage = {
        .purpose = strtext("Compose a tune with the tune builder and play it with the tone generator."),
        .parameters = "commands ... | -f [{file | -} ...]",
        .notes = USAGE_NOTES(usageNotes, tuneBuilderUsageNotes),
      }
    };

    PROCESS_OPTIONS(descriptor, argc, argv);
  }

  resetPreferences();
  if (!parseTuneDevice(opt_tuneDevice)) return PROG_EXIT_SYNTAX;
  if (!parseTuneVolume(opt_outputVolume)) return PROG_EXIT_SYNTAX;

#ifdef HAVE_MIDI_SUPPORT
  if (!parseTuneInstrument(opt_midiInstrument)) return PROG_EXIT_SYNTAX;
#endif /* HAVE_MIDI_SUPPORT */

  if (!setTuneDevice()) return PROG_EXIT_SEMANTIC;
  ProgramExitStatus exitStatus = PROG_EXIT_FATAL;
  TuneBuilder *tb = newTuneBuilder();

  if (tb) {
    if (opt_fromFiles) {
      const InputFilesProcessingParameters parameters = {
        .beginStream = beginTuneStream,
        .endStream = endTuneStream,

        .dataFileParameters = {
          .processOperands = processTuneLine,
          .data = tb
        }
      };

      exitStatus = processInputFiles(argv, argc, &parameters);
    } else if (argc) {
      exitStatus = PROG_EXIT_SUCCESS;
      setTuneSourceName(tb, "<command-line>");

      do {
        incrementTuneSourceIndex(tb);
        if (!parseTuneString(tb, *argv)) break;
        argv += 1;
      } while (argc -= 1);

      playTune(tb);
    } else {
      logMessage(LOG_ERR, "missing tune");
      exitStatus = PROG_EXIT_SYNTAX;
    }

    if (exitStatus == PROG_EXIT_SUCCESS) {
      switch (getTuneStatus(tb)) {
        case TUNE_STATUS_OK:
          exitStatus = PROG_EXIT_SUCCESS;
          break;

        case TUNE_STATUS_SYNTAX:
          exitStatus = PROG_EXIT_SYNTAX;
          break;

        case TUNE_STATUS_FATAL:
          exitStatus = PROG_EXIT_FATAL;
          break;
      }
    } else if (exitStatus == PROG_EXIT_FORCE) {
      exitStatus = PROG_EXIT_SUCCESS;
    }

    destroyTuneBuilder(tb);
  }

  return exitStatus;
}

#include "alert.h"

void
alert (AlertIdentifier identifier) {
}
