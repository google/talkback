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

#include "log.h"
#include "options.h"
#include "prefs.h"
#include "parse.h"
#include "tune_utils.h"
#include "notes.h"
#include "datafile.h"
#include "morse.h"

static int opt_fromFiles;
static char *opt_morsePitch;
static char *opt_morseSpeed;
static int opt_morseGroups;
static char *opt_outputVolume;
static char *opt_tuneDevice;

#ifdef HAVE_MIDI_SUPPORT
static char *opt_midiInstrument;
#endif /* HAVE_MIDI_SUPPORT */

BEGIN_OPTION_TABLE(programOptions)
  { .letter = 'f',
    .word = "files",
    .setting.flag = &opt_fromFiles,
    .description = "Use files rather than command line arguments."
  },

  { .letter = 't',
    .word = "tone",
    .argument = "frequency",
    .setting.string = &opt_morsePitch,
    .description = "The pitch of the tone."
  },

  { .letter = 's',
    .word = "speed",
    .argument = "wordsPerMinute",
    .setting.string = &opt_morseSpeed,
    .description = "Morse speed (words per minute)."
  },

  { .letter = 'g',
    .word = "groups",
    .setting.flag = &opt_morseGroups,
    .description = "Speed is in groups (rather than words) per minute."
  },

  { .letter = 'v',
    .word = "volume",
    .argument = "loudness",
    .setting.string = &opt_outputVolume,
    .description = "Output volume (percentage)."
  },

  { .letter = 'd',
    .word = "device",
    .argument = "device",
    .setting.string = &opt_tuneDevice,
    .description = "Name of tune device."
  },

#ifdef HAVE_PCM_SUPPORT
  { .letter = 'p',
    .word = "pcm-device",
    .flags = OPT_Hidden,
    .argument = "device",
    .setting.string = &opt_pcmDevice,
    .description = "Device specifier for soundcard digital audio."
  },
#endif /* HAVE_PCM_SUPPORT */

#ifdef HAVE_MIDI_SUPPORT
  { .letter = 'm',
    .word = "midi-device",
    .flags = OPT_Hidden,
    .argument = "device",
    .setting.string = &opt_midiDevice,
    .description = "Device specifier for the Musical Instrument Digital Interface."
  },

  { .letter = 'i',
    .word = "instrument",
    .argument = "instrument",
    .setting.string = &opt_midiInstrument,
    .description = "Name of MIDI instrument."
  },
#endif /* HAVE_MIDI_SUPPORT */
END_OPTION_TABLE

static
DATA_OPERANDS_PROCESSOR(processMorseLine) {
  MorseObject *morse = data;

  DataOperand text;
  getTextRemaining(file, &text);

  if (!addMorseCharacters(morse, text.characters, text.length)) return 0;
  if (!addMorseSpace(morse)) return 0;
  return 1;
}

static void
exitMorseObject (void *data) {
  MorseObject *morse = data;
  destroyMorseObject(morse);
}

int
main (int argc, char *argv[]) {
  {
    static const OptionsDescriptor descriptor = {
      OPTION_TABLE(programOptions),
      .applicationName = "brltty-morse",
      .argumentsSummary = "text... | -f [{file | -}...]"
    };
    PROCESS_OPTIONS(descriptor, argc, argv);
  }

  resetPreferences();
  if (!parseTuneDevice(opt_tuneDevice)) return PROG_EXIT_SYNTAX;
  if (!parseTuneVolume(opt_outputVolume)) return PROG_EXIT_SYNTAX;

#ifdef HAVE_MIDI_SUPPORT
  if (!parseTuneInstrument(opt_midiInstrument)) return PROG_EXIT_SYNTAX;
#endif /* HAVE_MIDI_SUPPORT */

  MorseObject *morse = newMorseObject();
  if (!morse) return PROG_EXIT_FATAL;
  onProgramExit("morse-object", exitMorseObject, morse);

  {
    int ok = 0;

    {
      int pitch = getMorsePitch(morse);
      static int minimum = 1;
      static int maximum = 0XFFFF;

      if (validateInteger(&pitch, opt_morsePitch, &minimum, &maximum)) {
        if (setMorsePitch(morse, pitch)) {
          ok = 1;
        }
      }
    }

    if (!ok) {
      logMessage(LOG_WARNING, "unsupported Morse pitch: %s (Hz)", opt_morsePitch);
      return PROG_EXIT_SYNTAX;
    }
  }

  {
    int ok = 0;

    int speed;
    const char *unit;

    if (opt_morseGroups) {
      speed = getMorseGroupsPerMinute(morse);
      unit = "groups";
    } else {
      speed = getMorseWordsPerMinute(morse);
      unit = "words";
    }

    {
      static int minimum = 1;
      static int maximum = 100;

      if (validateInteger(&speed, opt_morseSpeed, &minimum, &maximum)) {
        if (opt_morseGroups) {
          if (setMorseGroupsPerMinute(morse, speed)) ok = 1;
        } else {
          if (setMorseWordsPerMinute(morse, speed)) ok = 1;
        }
      }
    }

    if (!ok) {
      logMessage(LOG_WARNING, "unsupported Morse speed: %s (%s per minute)", opt_morseSpeed, unit);
      return PROG_EXIT_SYNTAX;
    }
  }

  if (!setTuneDevice()) return PROG_EXIT_SEMANTIC;
  ProgramExitStatus exitStatus = PROG_EXIT_FATAL;

  if (opt_fromFiles) {
    const InputFilesProcessingParameters parameters = {
      .dataFileParameters = {
        .processOperands = processMorseLine,
        .data = &morse
      }
    };

    exitStatus = processInputFiles(argv, argc, &parameters);
  } else if (argc) {
    exitStatus = PROG_EXIT_SUCCESS;

    do {
      if (!(addMorseString(morse, *argv) && addMorseSpace(morse))) {
        exitStatus = PROG_EXIT_FATAL;
        break;
      }

      argv += 1;
    } while (argc -= 1);
  } else {
    logMessage(LOG_ERR, "missing text");
    exitStatus = PROG_EXIT_SYNTAX;
  }

  if (exitStatus == PROG_EXIT_SUCCESS) {
    if (!playMorseSequence(morse)) {
      exitStatus = PROG_EXIT_FATAL;
    }
  }

  return exitStatus;
}

#include "alert.h"

void
alert (AlertIdentifier identifier) {
}
