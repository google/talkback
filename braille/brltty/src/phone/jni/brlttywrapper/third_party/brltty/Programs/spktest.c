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

/* spktest.c - Test progrm for the speech synthesizer drivers.
 */

#include "prologue.h"

#include <stdio.h>
#include <string.h>
#include <strings.h>
#include <errno.h>

#include "program.h"
#include "cmdline.h"
#include "log.h"
#include "spk.h"
#include "file.h"
#include "parse.h"
#include "async_wait.h"

static char *opt_textString;
static char *opt_speechVolume;
static char *opt_speechRate;
static char *opt_pcmDevice;
static char *opt_driversDirectory;

BEGIN_OPTION_TABLE(programOptions)
  { .word = "text-string",
    .letter = 't',
    .argument = "string",
    .setting.string = &opt_textString,
    .description = "Text to be spoken."
  },

  { .word = "volume",
    .letter = 'v',
    .argument = "loudness",
    .setting.string = &opt_speechVolume,
    .description = "Floating-point speech volume multiplier."
  },

  { .word = "rate",
    .letter = 'r',
    .argument = "speed",
    .setting.string = &opt_speechRate,
    .description = "Floating-point speech rate multiplier."
  },

  { .word = "device",
    .letter = 'd',
    .argument = "device",
    .setting.string = &opt_pcmDevice,
    .description = "Digital audio soundcard device specifier."
  },

  { .word = "drivers-directory",
    .letter = 'D',
    .argument = "directory",
    .setting.string = &opt_driversDirectory,
    .internal.setting = DRIVERS_DIRECTORY,
    .internal.adjust = fixInstallPath,
    .description = "Path to directory for loading drivers."
  },
END_OPTION_TABLE(programOptions)

static int
say (SpeechSynthesizer *spk, const char *string) {
  if (!sayString(spk, string, 0)) return 0;
  asyncWait(250);
  return 1;
}

static int
sayLine (const LineHandlerParameters *parameters) {
  SpeechSynthesizer *spk = parameters->data;

  say(spk, parameters->line.text);
  return 1;
}

int
main (int argc, char *argv[]) {
  ProgramExitStatus exitStatus;
  SpeechSynthesizer spk;


  const char *driver = NULL;
  void *object;

  int speechVolume = SPK_VOLUME_DEFAULT;
  int speechRate = SPK_RATE_DEFAULT;

  {
    const CommandLineDescriptor descriptor = {
      .options = &programOptions,
      .applicationName = "spktest",

      .usage = {
        .purpose = strtext("Test a speech driver."),
        .parameters = "[driver [parameter=value ...]]",
      }
    };

    PROCESS_OPTIONS(descriptor, argc, argv);
  }

  if (opt_speechVolume && *opt_speechVolume) {
    static const int minimum = 0;
    static const int maximum = SPK_VOLUME_MAXIMUM;

    if (!validateInteger(&speechVolume, opt_speechVolume, &minimum, &maximum)) {
      logMessage(LOG_ERR, "%s: %s", "invalid volume multiplier", opt_speechVolume);
      return PROG_EXIT_SYNTAX;
    }
  }

  if (opt_speechRate && *opt_speechRate) {
    static const int minimum = 0;
    static const int maximum = SPK_RATE_MAXIMUM;

    if (!validateInteger(&speechRate, opt_speechRate, &minimum, &maximum)) {
      logMessage(LOG_ERR, "%s: %s", "invalid rate multiplier", opt_speechRate);
      return PROG_EXIT_SYNTAX;
    }
  }

  if (argc) {
    driver = *argv++, --argc;
  }

  if ((speech = loadSpeechDriver(driver, &object, opt_driversDirectory))) {
    const char *const *parameterNames = speech->parameters;
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
        logMessage(LOG_ERR, "missing speech driver parameter value: %s", assignment);
      } else if (delimiter == assignment) {
        logMessage(LOG_ERR, "missing speech driver parameter name: %s", assignment);
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

        if (!ok) logMessage(LOG_ERR, "invalid speech driver parameter: %s", assignment);
      }

      if (!ok) return PROG_EXIT_SYNTAX;
      argc -= 1;
    }

    constructSpeechSynthesizer(&spk);
    identifySpeechDriver(speech, 0);		/* start-up messages */

    if (startSpeechDriverThread(&spk, parameterSettings)) {
      setSpeechVolume(&spk, speechVolume, 0);
      setSpeechRate(&spk, speechRate, 0);

      if (opt_textString && *opt_textString) {
        say(&spk, opt_textString);
      } else {
        processLines(stdin, sayLine, (void *)&spk);
      }

      drainSpeech(&spk);
      stopSpeechDriverThread(&spk);
      exitStatus = PROG_EXIT_SUCCESS;
    } else {
      logMessage(LOG_ERR, "can't initialize speech driver");
      exitStatus = PROG_EXIT_FATAL;
    }
  } else {
    logMessage(LOG_ERR, "can't load speech driver");
    exitStatus = PROG_EXIT_FATAL;
  }

  return exitStatus;
}
