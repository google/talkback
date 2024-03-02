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
#include <strings.h>
#include <ctype.h>
#include <locale.h>
#include <errno.h>
#include <fcntl.h>

#include "parameters.h"
#include "embed.h"
#include "log.h"
#include "report.h"
#include "strfmt.h"
#include "pgmprivs.h"
#include "lock.h"
#include "activity.h"
#include "update.h"
#include "cmd.h"
#include "cmd_navigation.h"
#include "brl.h"
#include "brl_utils.h"
#include "spk.h"
#include "spk_input.h"
#include "scr.h"
#include "scr_special.h"
#include "status.h"
#include "blink.h"
#include "variables.h"
#include "datafile.h"
#include "ttb.h"
#include "atb.h"
#include "ctb.h"
#include "ktb.h"
#include "ktb_keyboard.h"
#include "kbd.h"
#include "alert.h"
#include "bell.h"
#include "leds.h"
#include "tune.h"
#include "notes.h"
#include "message.h"
#include "file.h"
#include "parse.h"
#include "dynld.h"
#include "async_handle.h"
#include "async_alarm.h"
#include "program.h"
#include "messages.h"
#include "revision.h"
#include "service.h"
#include "cmdline.h"
#include "profile_types.h"
#include "brl_input.h"
#include "cmd_queue.h"
#include "core.h"
#include "api_control.h"
#include "prefs.h"
#include "utf8.h"

#include "io_generic.h"
#include "io_usb.h"
#include "io_bluetooth.h"

#ifdef __MINGW32__
int isWindowsService = 0;
#endif /* __MINGW32__ */

#ifdef __MSDOS__
#include "system_msdos.h"
#endif /* __MSDOS__ */

static void
makeProgramBanner (char *buffer, size_t size, int includeRevision) {
  const char *revision = includeRevision? getRevisionIdentifier(): "";
  snprintf(buffer, size, "%s %s%s%s",
           PACKAGE_NAME, PACKAGE_VERSION,
           (*revision? " rev ": ""), revision);
}

static void
logProgramBanner (void) {
  char banner[0X100];
  makeProgramBanner(banner, sizeof(banner), 1);

  {
    int pushed = pushLogPrefix("");
    logMessage(LOG_NOTICE, "%s [%s]", banner, PACKAGE_URL);
    if (pushed) popLogPrefix();
  }
}

static void
logProperty (const char *value, const char *variable, const char *label) {
  if (value && *value) {
    if (variable) setGlobalVariable(variable, value);
  } else {
    value = "none";
  }

  logMessage(LOG_INFO, "%s: %s", label, value);
}

static const char optionOperand_none[] = "no";
static const char optionOperand_autodetect[] = "auto";
static const char optionOperand_off[] = "off";

static const char *const *const fallbackBrailleDrivers =
  NULL_TERMINATED_STRING_ARRAY(
    optionOperand_none
  );

static const char *const *const autodetectableBrailleDrivers_serial =
  NULL_TERMINATED_STRING_ARRAY(
    "md", "pm", "ts", "ht", "bn", "al", "bm", "pg", "sk"
  );

static const char *const *const autodetectableBrailleDrivers_USB =
  NULL_TERMINATED_STRING_ARRAY(
    "al", "bm", "bn", "cn", "dp", "eu", "fs", "hd", "hm", "ht", "hw", "ic", "mt", "pg", "pm", "sk", "vo"
  );

static const char *const *const autodetectableBrailleDrivers_Bluetooth =
  NULL_TERMINATED_STRING_ARRAY(
    "np", "ht", "al", "bm"
  );

#define SERVICE_NAME "BrlAPI"
#define SERVICE_DESCRIPTION "Braille Devices API"

static
STR_BEGIN_FORMATTER(formatLogLevelString, unsigned int index)
  switch (index) {
    case 0:
      STR_PRINTF("0-%u", logLevelCount-1);
      break;

    case 1: {
      for (unsigned int level=0; level<logLevelCount; level+=1) {
        if (level) STR_PRINTF(" ");
        STR_PRINTF("%s", logLevelNames[level]);
      }

      break;
    }

    case 2: {
      LogCategoryIndex category;

      STR_PRINTF("%s", logCategoryName_all);

      for (category=0; category<LOG_CATEGORY_COUNT; category+=1) {
        const char *name = getLogCategoryName(category);

        if (name && *name) {
          STR_PRINTF(" %s", name);
        }
      }

      break;
    }

    case 3:
      STR_PRINTF("%c", logCategoryPrefix_disable);
      break;

    default:
      break;
  }
STR_END_FORMATTER

static const char *const *const screenContentQualityChoices =
  NULL_TERMINATED_STRING_ARRAY(
    "none", "low", "poor", "fair", "good", "high"
  );

STR_BEGIN_FORMATTER(formatScreenContentQualityChoices, unsigned int index)
  switch (index) {
    case 0: {
      const char *const *choices = screenContentQualityChoices;
      const char *const *choice = choices;

      while (*choice) {
        if (choice != choices) STR_PRINTF(" ");
        STR_PRINTF("%s", *choice);
        choice += 1;
      }

      break;
    }

    default:
      break;
  }
STR_END_FORMATTER

static int opt_installService;
static const char *const optionStrings_InstallService[] = {
  SERVICE_NAME,
  NULL
};

static int opt_removeService;
static const char *const optionStrings_RemoveService[] = {
  SERVICE_NAME,
  NULL
};

static char *opt_startMessage;
static char *opt_stopMessage;
static char *opt_localeDirectory;

static int opt_version;
static int opt_verify;
static int opt_quiet;
static int opt_noDaemon;
static int opt_standardError;
static char *opt_logLevel;
static char *opt_logFile;
static int opt_bootParameters = 1;
static int opt_environmentVariables;
static char *opt_messageTime;

static int opt_cancelExecution;
static const char *const optionStrings_CancelExecution[] = {
  PACKAGE_TARNAME,
  NULL
};

static char *opt_promptPatterns;

static int opt_stayPrivileged;
static char *opt_privilegeParameters;

static char *opt_pidFile;
static char *opt_configurationFile;

static char *opt_updatableDirectory;
static char *opt_writableDirectory;
char *opt_driversDirectory;

char *opt_brailleDevice;
static char **brailleDevices = NULL;
static const char *brailleDevice = NULL;
int opt_releaseDevice;

static char *opt_brailleDriver;
static char **brailleDrivers = NULL;
static const BrailleDriver *brailleDriver = NULL;
static void *brailleObject = NULL;
static int brailleDriverConstructed;

static char *opt_brailleParameters;
static char *brailleParameters = NULL;
static char **brailleDriverParameters = NULL;

static char *opt_preferencesFile;
static char *opt_overridePreferences;

static char *oldPreferencesFile = NULL;
static int oldPreferencesEnabled = 1;

char *opt_tablesDirectory;
char *opt_textTable;
char *opt_contractionTable;
char *opt_attributesTable;

char *opt_keyboardTable;
KeyTable *keyboardTable = NULL;
static KeyboardMonitorObject *keyboardMonitor = NULL;

static char *opt_keyboardProperties;
static KeyboardProperties keyboardProperties;

#ifdef ENABLE_API
static int opt_noApi;
static char *opt_apiParameters = NULL;
static char **apiParameters = NULL;
#endif /* ENABLE_API */

#ifdef ENABLE_SPEECH_SUPPORT
static char *opt_speechDriver;
static char **speechDrivers = NULL;
static const SpeechDriver *speechDriver = NULL;
static void *speechObject = NULL;

static char *opt_speechParameters;
static char *speechParameters = NULL;
static char **speechDriverParameters = NULL;

static char *opt_speechInput;
static SpeechInputObject *speechInputObject;

int opt_quietIfNoBraille;
static char *opt_autospeakThreshold;
unsigned int autospeakMinimumScreenContentQuality;

static void
setAutospeakThreshold (void) {
  const char *choice = opt_autospeakThreshold;

  int ok = validateChoice(
    &autospeakMinimumScreenContentQuality,
    choice, screenContentQualityChoices
  );

  if (!ok) {
    logMessage(LOG_ERR, "%s: %s",
      gettext("unknown screen content quality"),
      choice
    );
  }

  logProperty(
    screenContentQualityChoices[autospeakMinimumScreenContentQuality],
    "autospeakThreshold", "Autospeak Threshold"
  );
}
#endif /* ENABLE_SPEECH_SUPPORT */

static char *opt_screenDriver;
static char **screenDrivers = NULL;
static const ScreenDriver *screenDriver = NULL;
static void *screenObject = NULL;
static char *opt_screenParameters;
static char *screenParameters = NULL;
static char **screenDriverParameters = NULL;

static const char *const optionStrings_TextTable[] = {
  optionOperand_autodetect,
  NULL
};

static const char *const optionStrings_BrailleDriver[] = {
  optionOperand_autodetect,
  optionOperand_none,
  BRAILLE_DRIVER_CODES,
  NULL
};

static const char *const optionStrings_ScreenDriver[] = {
  optionOperand_autodetect,
  optionOperand_none,
  SCREEN_DRIVER_CODES,
  NULL
};

#ifdef ENABLE_SPEECH_SUPPORT
static const char *const optionStrings_SpeechDriver[] = {
  optionOperand_autodetect,
  optionOperand_none,
  SPEECH_DRIVER_CODES,
  NULL
};
#endif /* ENABLE_SPEECH_SUPPORT */

BEGIN_OPTION_TABLE(programOptions)
  { .word = "version",
    .letter = 'V',
    .setting.flag = &opt_version,
    .description = strtext("Log the versions of the core, API, and built-in drivers, and then exit.")
  },

  { .word = "environment-variables",
    .letter = 'E',
    .setting.flag = &opt_environmentVariables,
    .description = strtext("Recognize environment variables.")
  },

  { .word = "configuration-file",
    .letter = 'f',
    .flags = OPT_EnvVar,
    .argument = strtext("file"),
    .setting.string = &opt_configurationFile,
    .internal.setting = CONFIGURATION_DIRECTORY "/" CONFIGURATION_FILE,
    .internal.adjust = fixInstallPath,
    .description = strtext("Path to default settings file.")
  },

  { .word = "braille-driver",
    .letter = 'b',
    .bootParameter = 1,
    .flags = OPT_Config | OPT_EnvVar,
    .argument = strtext("driver,..."),
    .setting.string = &opt_brailleDriver,
    .internal.setting = optionOperand_autodetect,
    .description = strtext("Braille driver code (%s, %s, or one of {%s})."),
    .strings.array = optionStrings_BrailleDriver
  },

  { .word = "braille-parameters",
    .letter = 'B',
    .bootParameter = 4,
    .flags = OPT_Extend | OPT_Config | OPT_EnvVar,
    .argument = strtext("name=value,..."),
    .setting.string = &opt_brailleParameters,
    .internal.setting = BRAILLE_PARAMETERS,
    .description = strtext("Parameters for the braille driver.")
  },

  { .word = "braille-device",
    .letter = 'd',
    .bootParameter = 2,
    .flags = OPT_Config | OPT_EnvVar,
    .argument = strtext("identifier,..."),
    .setting.string = &opt_brailleDevice,
    .internal.setting = BRAILLE_DEVICE,
    .description = strtext("Device for accessing braille display.")
  },

  { .word = "release-device",
    .letter = 'r',
    .flags = OPT_Config | OPT_EnvVar,
    .setting.flag = &opt_releaseDevice,
#ifdef WINDOWS
    .internal.setting = OPT_WORD_TRUE,
#else /* WINDOWS */
    .internal.setting = OPT_WORD_FALSE,
#endif /* WINDOWS */
    .description = strtext("Release braille device when screen or window is unreadable.")
  },

  { .word = "text-table",
    .letter = 't',
    .bootParameter = 3,
    .flags = OPT_Config | OPT_EnvVar,
    .argument = strtext("file"),
    .setting.string = &opt_textTable,
    .internal.setting = optionOperand_autodetect,
    .description = strtext("Name of or path to text table (or %s)."),
    .strings.array = optionStrings_TextTable
  },

  { .word = "contraction-table",
    .letter = 'c',
    .flags = OPT_Config | OPT_EnvVar,
    .argument = strtext("file"),
    .setting.string = &opt_contractionTable,
    .internal.setting = optionOperand_autodetect,
    .description = strtext("Name of or path to contraction table.")
  },

  { .word = "attributes-table",
    .letter = 'a',
    .flags = OPT_Config | OPT_EnvVar,
    .argument = strtext("file"),
    .setting.string = &opt_attributesTable,
    .description = strtext("Name of or path to attributes table.")
  },

#ifdef ENABLE_SPEECH_SUPPORT
  { .word = "speech-driver",
    .letter = 's',
    .flags = OPT_Config | OPT_EnvVar,
    .argument = strtext("driver,..."),
    .setting.string = &opt_speechDriver,
    .internal.setting = optionOperand_autodetect,
    .description = strtext("Speech driver code (%s, %s, or one of {%s})."),
    .strings.array = optionStrings_SpeechDriver
  },

  { .word = "speech-parameters",
    .letter = 'S',
    .flags = OPT_Extend | OPT_Config | OPT_EnvVar,
    .argument = strtext("name=value,..."),
    .setting.string = &opt_speechParameters,
    .internal.setting = SPEECH_PARAMETERS,
    .description = strtext("Parameters for the speech driver.")
  },

  { .word = "speech-input",
    .letter = 'i',
    .flags = OPT_Config | OPT_EnvVar,
    .argument = strtext("file"),
    .setting.string = &opt_speechInput,
    .description = strtext("Name of or path to speech input object.")
  },

  { .word = "quiet-if-no-braille",
    .letter = 'Q',
    .flags = OPT_Config | OPT_EnvVar,
    .setting.flag = &opt_quietIfNoBraille,
    .description = strtext("Do not autospeak when braille is not being used.")
  },

  { .word = "autospeak-threshold",
    .flags = OPT_Config | OPT_EnvVar | OPT_Format,
    .argument = strtext("quality"),
    .setting.string = &opt_autospeakThreshold,
    .description = strtext("Minimum screen content quality to autospeak (one of {%s})."),
    .strings.format = formatScreenContentQualityChoices
  },
#endif /* ENABLE_SPEECH_SUPPORT */

  { .word = "screen-driver",
    .letter = 'x',
    .flags = OPT_Config | OPT_EnvVar,
    .argument = strtext("driver,..."),
    .setting.string = &opt_screenDriver,
    .internal.setting = DEFAULT_SCREEN_DRIVER,
    .description = strtext("Screen driver code (%s, %s, or one of {%s})."),
    .strings.array = optionStrings_ScreenDriver
  },

  { .word = "screen-parameters",
    .letter = 'X',
    .flags = OPT_Extend | OPT_Config | OPT_EnvVar,
    .argument = strtext("name=value,..."),
    .setting.string = &opt_screenParameters,
    .internal.setting = SCREEN_PARAMETERS,
    .description = strtext("Parameters for the screen driver.")
  },

  { .word = "keyboard-table",
    .letter = 'k',
    .flags = OPT_Config | OPT_EnvVar,
    .argument = strtext("file"),
    .setting.string = &opt_keyboardTable,
    .internal.setting = optionOperand_off,
    .description = strtext("Name of or path to keyboard table.")
  },

  { .word = "keyboard-properties",
    .letter = 'K',
    .flags = OPT_Extend | OPT_Config | OPT_EnvVar,
    .argument = strtext("name=value,..."),
    .setting.string = &opt_keyboardProperties,
    .description = strtext("Properties of eligible keyboards.")
  },

  { .word = "preferences-file",
    .letter = 'F',
    .flags = OPT_Config | OPT_EnvVar,
    .argument = strtext("file"),
    .setting.string = &opt_preferencesFile,
    .internal.setting = PREFERENCES_FILE,
    .description = strtext("Name of or path to default preferences file.")
  },

  { .word = "override-preferences",
    .letter = 'o',
    .flags = OPT_Extend | OPT_Config | OPT_EnvVar,
    .argument = strtext("name=value,..."),
    .setting.string = &opt_overridePreferences,
    .description = strtext("Explicit preference settings.")
  },

#ifdef ENABLE_API
  { .word = "no-api",
    .letter = 'N',
    .flags = OPT_Config | OPT_EnvVar,
    .setting.flag = &opt_noApi,
    .description = strtext("Disable the application programming interface.")
  },

  { .word = "api-parameters",
    .letter = 'A',
    .flags = OPT_Extend | OPT_Config | OPT_EnvVar,
    .argument = strtext("name=value,..."),
    .setting.string = &opt_apiParameters,
    .internal.setting = API_PARAMETERS,
    .description = strtext("Parameters for the application programming interface.")
  },
#endif /* ENABLE_API */

  { .word = "quiet",
    .letter = 'q',
    .flags = OPT_Config | OPT_EnvVar,
    .setting.flag = &opt_quiet,
    .description = strtext("Suppress start-up messages.")
  },

  { .word = "log-level",
    .letter = 'l',
    .flags = OPT_Extend | OPT_Config | OPT_EnvVar | OPT_Format,
    .argument = strtext("lvl|cat,..."),
    .setting.string = &opt_logLevel,
    .description = strtext("Logging level (%s or one of {%s}) and/or log categories to enable (any combination of {%s}, each optionally prefixed by %s to disable)."),
    .strings.format = formatLogLevelString
  },

  { .word = "log-file",
    .letter = 'L',
    .flags = OPT_Config | OPT_EnvVar,
    .argument = strtext("file"),
    .setting.string = &opt_logFile,
    .description = strtext("Path to log file.")
  },

  { .word = "standard-error",
    .letter = 'e',
    .setting.flag = &opt_standardError,
    .description = strtext("Log to standard error rather than to the system log.")
  },

  { .word = "no-daemon",
    .letter = 'n',
    .setting.flag = &opt_noDaemon,
    .description = strtext("Remain a foreground process.")
  },

  { .word = "stay-privileged",
    .letter = 'z',
    .flags = OPT_Config | OPT_EnvVar,
    .setting.flag = &opt_stayPrivileged,
    .description = strtext("Don't switch to an unprivileged user or relinquish any privileges (group memberships, capabilities, etc).")
  },

  { .word = "privilege-parameters",
    .letter = 'Z',
    .flags = OPT_Extend | OPT_Config | OPT_EnvVar,
    .argument = strtext("name=value,..."),
    .setting.string = &opt_privilegeParameters,
    .internal.setting = PRIVILEGE_PARAMETERS,
    .description = strtext("Parameters for the privilege establishment stage.")
  },

  { .word = "message-time",
    .letter = 'M',
    .flags = OPT_Config | OPT_EnvVar,
    .argument = strtext("csecs"),
    .setting.string = &opt_messageTime,
    .description = strtext("Message hold timeout (in 10ms units).")
  },

  { .word = "start-message",
    .flags = OPT_Config | OPT_EnvVar,
    .argument = strtext("text"),
    .setting.string = &opt_startMessage,
    .description = strtext("The text to be shown when the braille driver starts and to be spoken when the speech driver starts.")
  },

  { .word = "stop-message",
    .flags = OPT_Config | OPT_EnvVar,
    .argument = strtext("text"),
    .setting.string = &opt_stopMessage,
    .description = strtext("The text to be shown when the braille driver stops.")
  },

  { .word = "prompt-patterns",
    .flags = OPT_Extend | OPT_Config | OPT_EnvVar,
    .argument = strtext("regexp,..."),
    .setting.string = &opt_promptPatterns,
    .description = strtext("Patterns that match command prompts.")
  },

#ifdef HAVE_PCM_SUPPORT
  { .word = "pcm-device",
    .letter = 'p',
    .flags = OPT_Config | OPT_EnvVar,
    .argument = strtext("device"),
    .setting.string = &opt_pcmDevice,
    .description = strtext("PCM (soundcard digital audio) device specifier.")
  },
#endif /* HAVE_PCM_SUPPORT */

#ifdef HAVE_MIDI_SUPPORT
  { .word = "midi-device",
    .letter = 'm',
    .flags = OPT_Config | OPT_EnvVar,
    .argument = strtext("device"),
    .setting.string = &opt_midiDevice,
    .description = strtext("MIDI (Musical Instrument Digital Interface) device specifier.")
  },
#endif /* HAVE_MIDI_SUPPORT */

  { .word = "tables-directory",
    .letter = 'T',
    .flags = OPT_Config | OPT_EnvVar,
    .argument = strtext("directory"),
    .setting.string = &opt_tablesDirectory,
    .internal.setting = TABLES_DIRECTORY,
    .internal.adjust = fixInstallPath,
    .description = strtext("Path to directory containing tables.")
  },

  { .word = "drivers-directory",
    .letter = 'D',
    .flags = OPT_Config | OPT_EnvVar,
    .argument = strtext("directory"),
    .setting.string = &opt_driversDirectory,
    .internal.setting = DRIVERS_DIRECTORY,
    .internal.adjust = fixInstallPath,
    .description = strtext("Path to directory containing drivers.")
  },

  { .word = "updatable-directory",
    .letter = 'U',
    .flags = OPT_Config | OPT_EnvVar,
    .argument = strtext("directory"),
    .setting.string = &opt_updatableDirectory,
    .internal.setting = UPDATABLE_DIRECTORY,
    .internal.adjust = fixInstallPath,
    .description = strtext("Path to directory which contains files that can be updated.")
  },

  { .word = "writable-directory",
    .letter = 'W',
    .flags = OPT_Config | OPT_EnvVar,
    .argument = strtext("directory"),
    .setting.string = &opt_writableDirectory,
    .internal.setting = WRITABLE_DIRECTORY,
    .internal.adjust = fixInstallPath,
    .description = strtext("Path to directory which can be written to.")
  },

  { .word = "locale-directory",
    .flags = OPT_Config | OPT_EnvVar,
    .argument = strtext("directory"),
    .setting.string = &opt_localeDirectory,
    .internal.setting = LOCALE_DIRECTORY,
    .internal.adjust = fixInstallPath,
    .description = strtext("Path to directory which contains message localizations.")
  },

  { .word = "pid-file",
    .letter = 'P',
    .flags = OPT_Config | OPT_EnvVar,
    .argument = strtext("file"),
    .setting.string = &opt_pidFile,
    .internal.adjust = fixInstallPath,
    .description = strtext("Path to process identifier file.")
  },

  { .word = "cancel-execution",
    .letter = 'C',
    .setting.flag = &opt_cancelExecution,
    .description = strtext("Stop an existing instance of %s, and then exit."),
    .strings.array = optionStrings_CancelExecution
  },

  { .word = "install-service",
    .letter = 'I',
    .setting.flag = &opt_installService,
    .description = strtext("Install the %s service, and then exit."),
    .strings.array = optionStrings_InstallService
  },

  { .word = "remove-service",
    .letter = 'R',
    .setting.flag = &opt_removeService,
    .description = strtext("Remove the %s service, and then exit."),
    .strings.array = optionStrings_RemoveService
  },

  { .word = "verify",
    .letter = 'v',
    .setting.flag = &opt_verify,
    .description = strtext("Write the start-up logs, and then exit.")
  },
END_OPTION_TABLE(programOptions)

int
changeLogLevel (const char *operand) {
  int ok = 1;
  char **strings = splitString(operand, ',', NULL);

  if (strings) {
    char **string = strings;

    while (*string) {
      unsigned int level;

      if (isLogLevel(&level, *string)) {
        systemLogLevel = level;
      } else if (!setLogCategory(*string)) {
        logMessage(LOG_ERR, "%s: %s", gettext("unknown log level or category"), *string);
        ok = 0;
      }

      string += 1;
    }

    deallocateStrings(strings);
  }

  return ok;
}

int
changeLogCategories (const char *operand) {
  disableAllLogCategories();
  return changeLogLevel(operand);
}

static void
exitLog (void *data) {
  closeSystemLog();
  closeLogFile();
}

static void
setLogLevels (void) {
  systemLogLevel = LOG_NOTICE;
  disableAllLogCategories();
  changeLogLevel(opt_logLevel);

  {
    unsigned char level;

    if (opt_standardError) {
      level = systemLogLevel;
    } else {
      level = LOG_NOTICE;
      if (opt_version || opt_verify) level += 1;
      if (opt_quiet) level -= 1;
    }

    stderrLogLevel = level;
  }
}

static void
establishPrivileges (void) {
  const char *platform = getPrivilegeParametersPlatform();
  const char *const *names = getPrivilegeParameterNames();
  char **parameters = getParameters(names, platform, opt_privilegeParameters);

  if (parameters) {
    logParameters(names, parameters, "Privilege Parameter");
    establishProgramPrivileges(parameters, opt_stayPrivileged);
    deallocateStrings(parameters);
  }
}

ProgramExitStatus
brlttyPrepare (int argc, char *argv[]) {
  {
    const CommandLineDescriptor descriptor = {
      .options = &programOptions,

      .applicationName = "brltty",
      .configurationFile = &opt_configurationFile,
      .doEnvironmentVariables = &opt_environmentVariables,
      .doBootParameters = &opt_bootParameters,

      .usage = {
        .purpose = strtext("Screen reader for those who use a braille device."),
      }
    };

    ProgramExitStatus exitStatus = processOptions(&descriptor, &argc, &argv);

    switch (exitStatus) {
      case PROG_EXIT_SYNTAX:
      case PROG_EXIT_SUCCESS:
        break;

      default:
        return exitStatus;
    }
  }

  if (argc) {
    logMessage(LOG_ERR, "%s: %s", gettext("excess argument"), argv[0]);
  }

  setMessagesDirectory(opt_localeDirectory);
  setUpdatableDirectory(opt_updatableDirectory);
  setWritableDirectory(opt_writableDirectory);

  setLogLevels();
  onProgramExit("log", exitLog, NULL);

  {
    const char *logFile;

    if (*opt_logFile) {
      logFile = opt_logFile;
      openLogFile(logFile);
    } else {
      logFile = "<system>";
      openSystemLog();
    }

    logProgramBanner();
    logProperty(logFile, "logFile", "Log File");
    logProperty(opt_logLevel, "logLevel", "Log Level");
  }

  logProperty(getMessagesLocale(), "messagesLocale", "Messages Locale");
  logProperty(getMessagesDomain(), "messagesDomain", "Messages Domain");
  logProperty(getMessagesDirectory(), "messagesDirectory", "Messages Directory");

#ifdef ENABLE_SPEECH_SUPPORT
  setAutospeakThreshold();
#endif /* ENABLE_SPEECH_SUPPORT */

  establishPrivileges();
  return PROG_EXIT_SUCCESS;
}

static int
setTextTable (const char *name) {
  if (!name) name = "";
  if (!replaceTextTable(opt_tablesDirectory, name)) return 0;

  if (!*name) name = TEXT_TABLE;
  changeStringSetting(&opt_textTable, name);

  api.updateParameter(BRLAPI_PARAM_COMPUTER_BRAILLE_TABLE, 0);
  return 1;
}

static int
setTextTableForLocale (void) {
  changeStringSetting(&opt_textTable, "");
  char *name = getTextTableForLocale(opt_tablesDirectory);

  if (name) {
    logMessage(LOG_DEBUG, "using autoselected text table: %s", name);
    int ok = setTextTable(name);
    free(name);
    if (ok) return 1;
  }

  return 0;
}

int
changeTextTable (const char *name) {
  if (strcmp(name, optionOperand_autodetect) == 0) {
    return setTextTableForLocale();
  }

  return setTextTable(name);
}

static void
exitTextTable (void *data) {
  setTextTable(NULL);
}

static int
setContractionTable (const char *name) {
  if (!name) name = "";
  if (!replaceContractionTable(opt_tablesDirectory, name)) return 0;

  if (!*name) name = CONTRACTION_TABLE;
  changeStringSetting(&opt_contractionTable, name);

  api.updateParameter(BRLAPI_PARAM_LITERARY_BRAILLE_TABLE, 0);
  return 1;
}

static int
setContractionTableForLocale (void) {
  changeStringSetting(&opt_contractionTable, "");
  char *name = getContractionTableForLocale(opt_tablesDirectory);

  if (name) {
    logMessage(LOG_DEBUG, "using autoselected contraction table: %s", name);
    int ok = setContractionTable(name);
    free(name);
    if (ok) return 1;
  }

  return 0;
}

int
changeContractionTable (const char *name) {
  if (strcmp(name, optionOperand_autodetect) == 0) {
    return setContractionTableForLocale();
  }

  return setContractionTable(name);
}

static void
exitContractionTable (void *data) {
  setContractionTable(NULL);
}

static void
setTextAndContractionTables (void) {
  int usingInternalTextTable = 0;

  if (*opt_textTable) {
    if (strcmp(opt_textTable, optionOperand_autodetect) == 0) {
      setTextTableForLocale();
    } else if (!setTextTable(opt_textTable)) {
      changeStringSetting(&opt_textTable, "");
    }
  }

  if (!*opt_textTable) {
    logMessage(LOG_DEBUG, "using internal text table: %s", TEXT_TABLE);
    changeStringSetting(&opt_textTable, TEXT_TABLE);
    usingInternalTextTable = 1;
  }

  logProperty(opt_textTable, "textTable", "Text Table");
  onProgramExit("text-table", exitTextTable, NULL);

  if (*opt_contractionTable) {
    if (strcmp(opt_contractionTable, optionOperand_autodetect) == 0) {
      if (setContractionTableForLocale()) {
        if (usingInternalTextTable) {
          if (!isContractedBraille()) {
            setContractedBraille(1);
            logMessage(LOG_DEBUG, "contracted braille has been enabled");
          }
        }
      }
    } else if (!setContractionTable(opt_contractionTable)) {
      changeStringSetting(&opt_contractionTable, "");
    }
  }

  if (!*opt_contractionTable) {
    if (setContractionTable(NULL)) {
      logMessage(LOG_DEBUG, "using internal contraction table: %s", CONTRACTION_TABLE);
    }
  }

  logProperty(opt_contractionTable, "contractionTable", "Contraction Table");
  onProgramExit("contraction-table", exitContractionTable, NULL);
}

int
changeAttributesTable (const char *name) {
  if (!name) name = "";
  if (!replaceAttributesTable(opt_tablesDirectory, name)) return 0;

  if (!*name) name = ATTRIBUTES_TABLE;
  changeStringSetting(&opt_attributesTable, name);

  return 1;
}

static void
exitAttributesTable (void *data) {
  changeAttributesTable(NULL);
}

static void
setAttributesTable (void) {
  if (*opt_attributesTable) {
    if (!changeAttributesTable(opt_attributesTable)) {
      changeStringSetting(&opt_attributesTable, "");
    }
  }

  if (!*opt_attributesTable) {
    changeStringSetting(&opt_attributesTable, ATTRIBUTES_TABLE);
  }

  logProperty(opt_attributesTable, "attributesTable", "Attributes Table");
  onProgramExit("attributes-table", exitAttributesTable, NULL);
}

static KeyTableState
handleKeyboardEvent (KeyGroup group, KeyNumber number, int press) {
  if (keyboardTable) {
    if (!scr.unreadable) {
      return processKeyEvent(keyboardTable, getCurrentCommandContext(), group, number, press);
    }

    resetKeyTable(keyboardTable);
  }

  return KTS_UNBOUND;
}

static int
startKeyboardMonitor (void) {
  return !!(keyboardMonitor = newKeyboardMonitorObject(&keyboardProperties, handleKeyboardEvent));
}

static void
stopKeyboardMonitor (void) {
  if (keyboardMonitor) {
    destroyKeyboardMonitorObject(keyboardMonitor);
    keyboardMonitor = NULL;
  }
}

static int
prepareKeyboardMonitorActivity (void *data) {
  return 1;
}

static int
startKeyboardMonitorActivity (void *data) {
  return startKeyboardMonitor();
}

static void
stopKeyboardMonitorActivity (void *data) {
  stopKeyboardMonitor();
}

static const ActivityMethods keyboardMonitorActivityMethods = {
  .activityName = "keyboard-monitor",
  .retryInterval = KEYBOARD_MONITOR_START_RETRY_INTERVAL,

  .prepare = prepareKeyboardMonitorActivity,
  .start = startKeyboardMonitorActivity,
  .stop = stopKeyboardMonitorActivity
};

static ActivityObject *keyboardMonitorActivity = NULL;

static void
exitKeyboardMonitor (void *data) {
  if (keyboardMonitorActivity) {
    destroyActivity(keyboardMonitorActivity);
    keyboardMonitorActivity = NULL;
  }
}

static ActivityObject *
getKeyboardMonitorActivity (int allocate) {
  if (!keyboardMonitorActivity) {
    if (allocate) {
      if (!(keyboardMonitorActivity = newActivity(&keyboardMonitorActivityMethods, NULL))) {
        return NULL;
      }

      onProgramExit("keyboard-monitor", exitKeyboardMonitor, NULL);
    }
  }

  return keyboardMonitorActivity;
}

static void
enableKeyboardMonitor (void) {
  ActivityObject *activity = getKeyboardMonitorActivity(1);
  if (activity) startActivity(activity);
}

static void
disableKeyboardMonitor (void) {
  ActivityObject *activity = getKeyboardMonitorActivity(0);
  if (activity) stopActivity(activity);
}

static unsigned int brailleHelpPageNumber = 0;
static unsigned int keyboardHelpPageNumber = 0;

static int
enableHelpPage (unsigned int *pageNumber) {
  if (!*pageNumber) {
    if (!constructHelpScreen()) return 0;
    if (!(*pageNumber = addHelpPage())) return 0;
  }

  return setHelpPageNumber(*pageNumber);
}

static int
enableBrailleHelpPage (void) {
  return enableHelpPage(&brailleHelpPageNumber);
}

static int
enableKeyboardHelpPage (void) {
  return enableHelpPage(&keyboardHelpPageNumber);
}

static void
disableHelpPage (unsigned int pageNumber) {
  if (pageNumber) {
    if (setHelpPageNumber(pageNumber)) {
      clearHelpPage();
    }
  }
}

static void
disableBrailleHelpPage (void) {
  disableHelpPage(brailleHelpPageNumber);
}

static void
disableKeyboardHelpPage (void) {
  disableHelpPage(keyboardHelpPageNumber);
}

static int
handleWcharHelpLine (const wchar_t *line, void *data UNUSED) {
  return addHelpLine(line);
}

static int
handleUtf8HelpLine (const LineHandlerParameters *parameters) {
  const char *utf8 = parameters->line.text;
  size_t size = parameters->line.length + 1;
  wchar_t characters[size];
  wchar_t *character = characters;

  convertUtf8ToWchars(&utf8, &character, size);
  return handleWcharHelpLine(characters, parameters->data);
}

static int
loadHelpFile (const char *file) {
  int loaded = 0;
  FILE *stream;

  if ((stream = openDataFile(file, "r", 0))) {
    if (processLines(stream, handleUtf8HelpLine, NULL)) loaded = 1;

    fclose(stream);
  }

  return loaded;
}

static char *
makeBrailleKeyTablePath (void) {
  return makeInputTablePath(opt_tablesDirectory, braille->definition.code, brl.keyBindings);
}

static void
makeBrailleHelpPage (const char *keyTablePath) {
  if (enableBrailleHelpPage()) {
    if (brl.keyTable) {
      listKeyTable(brl.keyTable, NULL, handleWcharHelpLine, NULL);
    } else {
      char *keyHelpPath = replaceFileExtension(keyTablePath, KEY_HELP_EXTENSION);

      if (keyHelpPath) {
        if (loadHelpFile(keyHelpPath)) {
          logMessage(LOG_INFO, "%s: %s", gettext("Key Help"), keyHelpPath);
        } else {
          logMessage(LOG_WARNING, "%s: %s", gettext("cannot open key help"), keyHelpPath);
        }

        free(keyHelpPath);
      }
    }

    if (!getHelpLineCount()) {
      addHelpLine(WS_C("help not available"));
      message(NULL, gettext("no key bindings"), 0);
    }
  }
}

static void
makeKeyboardHelpPage (void) {
  if (enableKeyboardHelpPage()) {
    listKeyTable(keyboardTable, NULL, handleWcharHelpLine, NULL);
  }
}

static void
exitKeyboardTable (void *data) {
  if (keyboardTable) {
    destroyKeyTable(keyboardTable);
    keyboardTable = NULL;
  }

  disableKeyboardHelpPage();
}

int
changeKeyboardTable (const char *name) {
  KeyTable *table = NULL;

  if (!*name) name = "";
  if (strcmp(name, optionOperand_off) == 0) name = "";

  if (*name) {
    char *path = makeKeyboardTablePath(opt_tablesDirectory, name);

    if (path) {
      logMessage(LOG_DEBUG, "compiling keyboard table: %s", path);

      if (!(table = compileKeyTable(path, KEY_NAME_TABLES(keyboard)))) {
        logMessage(LOG_ERR, "%s: %s", gettext("cannot compile keyboard table"), path);
      }

      free(path);
    }

    if (!table) return 0;
  }

  if (keyboardTable) {
    disableKeyboardHelpPage();
    disableKeyboardMonitor();

    destroyKeyTable(keyboardTable);
    keyboardTable = NULL;
  }

  if (table) {
    setKeyTableLogLabel(table, "kbd");
    setLogKeyEventsFlag(table, &LOG_CATEGORY_FLAG(KEYBOARD_KEYS));

    keyboardTable = table;
    enableKeyboardMonitor();
    makeKeyboardHelpPage();
  }

  if (!*name) name = optionOperand_off;
  logMessage(LOG_DEBUG, "keyboard table changed: %s -> %s", opt_keyboardTable, name);

  changeStringSetting(&opt_keyboardTable, name);
  return 1;
}

static void
setKeyboardTable (void) {
  parseKeyboardProperties(&keyboardProperties, opt_keyboardProperties);
  onProgramExit("keyboard-table", exitKeyboardTable, NULL);
  changeKeyboardTable(opt_keyboardTable);
  logProperty(opt_keyboardTable, "keyboardTable", "Keyboard Table");
}

int
haveStatusCells (void) {
  return brl.statusColumns > 0;
}

static void
brailleWindowReconfigured (unsigned int rows, unsigned int columns) {
  textStart = 0;
  textCount = columns;
  statusStart = 0;
  statusCount = 0;

  if (!(textMaximized || haveStatusCells())) {
    unsigned int separatorWidth = (prefs.statusSeparator == ssNone)? 0: 1;
    unsigned int reserved = 1 + separatorWidth;

    if (brl.textColumns > reserved) {
      unsigned int statusWidth = prefs.statusCount;

      if (!statusWidth) statusWidth = getStatusFieldsLength(prefs.statusFields);
      statusWidth = MIN(statusWidth, brl.textColumns-reserved);

      if (statusWidth > 0) {
        switch (prefs.statusPosition) {
          case spLeft:
            statusStart = 0;
            statusCount = statusWidth;
            textStart = statusCount + separatorWidth;
            textCount = columns - textStart;
            break;

          case spRight:
            statusCount = statusWidth;
            statusStart = columns - statusCount;
            textCount = statusStart - separatorWidth;
            textStart = 0;
            break;
        }
      }
    }
  }

  logMessage(LOG_DEBUG,
    "regions: text=%u.%u status=%u.%u",
    textStart, textCount,
    statusStart, statusCount
  );

  fullWindowShift = MAX(textCount-prefs.brailleWindowOverlap, 1);
  halfWindowShift = textCount / 2;
  verticalWindowShift = (rows > 1)? rows: 5;

  logMessage(LOG_DEBUG,
    "shifts: full=%u half=%u vertical=%u",
    fullWindowShift, halfWindowShift, verticalWindowShift
  );
}

void
reconfigureBrailleWindow (void) {
  brailleWindowReconfigured(brl.textRows, brl.textColumns);
}

static void
applyBraillePreferences (void) {
  reconfigureBrailleWindow();

  setBrailleFirmness(&brl, prefs.brailleFirmness);
  setTouchSensitivity(&brl, prefs.touchSensitivity);

  setAutorepeatProperties(&brl, prefs.autorepeatEnabled,
                          PREFS2MSECS(prefs.longPressTime),
                          PREFS2MSECS(prefs.autorepeatInterval));

  if (brl.keyTable) {
    setKeyAutoreleaseTime(brl.keyTable, prefs.autoreleaseTime);
  }
}

#ifdef ENABLE_SPEECH_SUPPORT
static void
applySpeechPreferences (void) {
  setSpeechVolume(&spk, prefs.speechVolume, 0);
  setSpeechRate(&spk, prefs.speechRate, 0);
  setSpeechPitch(&spk, prefs.speechPitch, 0);
  setSpeechPunctuation(&spk, prefs.speechPunctuation, 0);
}
#endif /* ENABLE_SPEECH_SUPPORT */

static void
applyAllPreferences (void) {
  setConsoleBellMonitoring(prefs.consoleBellAlert);
  setLedMonitoring(prefs.keyboardLedAlerts);
  tuneSetDevice(prefs.tuneDevice);
  applyBraillePreferences();

#ifdef ENABLE_SPEECH_SUPPORT
  applySpeechPreferences();
#endif /* ENABLE_SPEECH_SUPPORT */
}

void
setPreferences (const PreferenceSettings *newPreferences) {
  prefs = *newPreferences;
  applyAllPreferences();
}

static void
ensureStatusFields (void) {
  const unsigned char *fields = braille->statusFields;
  unsigned int count = brl.statusColumns * brl.statusRows;

  if (!fields && count) {
    static const unsigned char fields1[] = {
      sfWindowRow, sfEnd
    };

    static const unsigned char fields2[] = {
      sfWindowRow, sfCursorRow, sfEnd
    };

    static const unsigned char fields3[] = {
      sfWindowRow, sfCursorRow, sfCursorColumn, sfEnd
    };

    static const unsigned char fields4[] = {
      sfWindowCoordinates2, sfCursorCoordinates2, sfEnd
    };

    static const unsigned char fields5[] = {
      sfWindowCoordinates2, sfCursorCoordinates2, sfStateDots, sfEnd
    };

    static const unsigned char fields6[] = {
      sfWindowCoordinates2, sfCursorCoordinates2, sfStateDots, sfScreenNumber,
      sfEnd
    };

    static const unsigned char fields7[] = {
      sfWindowCoordinates2, sfCursorCoordinates2, sfStateDots, sfTime,
      sfEnd
    };

    static const unsigned char *const fieldsTable[] = {
      fields1, fields2, fields3, fields4, fields5, fields6, fields7
    };

    static const unsigned char fieldsCount = ARRAY_COUNT(fieldsTable);
    if (count > fieldsCount) count = fieldsCount;
    fields = fieldsTable[count - 1];
  }

  setStatusFields(fields);
}

static void
setPreferenceOverrides (void) {
  int count;
  char **settings = splitString(opt_overridePreferences, PARAMETER_SEPARATOR_CHARACTER, &count);

  if (settings) {
    char **setting = settings;
    char **end = setting + count;

    while (setting < end) {
      setPreference(*setting);
      setting += 1;
    }

    deallocateStrings(settings);
  }
}

static void
finishPreferencesLoad (void) {
  setPreferenceOverrides();
  applyAllPreferences();
}

int
loadPreferences (int reset) {
  int ok = 0;
  int found = 0;

  if (reset) {
    resetPreferences();
  } else {
    {
      char *path = makePreferencesFilePath(opt_preferencesFile);

      if (path) {
        if (testFilePath(path)) {
          found = 1;
          if (loadPreferencesFile(path)) ok = 1;
          oldPreferencesEnabled = 0;
        } else {
          logMessage(LOG_DEBUG, "preferences file not found: %s", path);
        }

        free(path);
      }
    }

    if (oldPreferencesEnabled) {
      const char *path = oldPreferencesFile;

      if (path) {
        if (testFilePath(path)) {
          found = 1;
          if (loadPreferencesFile(path)) ok = 1;
        } else {
          logMessage(LOG_DEBUG, "old preferences file not found: %s", path);
        }
      }
    }
  }

  if (!found) {
    char *path = makePath(opt_tablesDirectory, "default.prefs");

    if (path) {
      if (loadPreferencesFile(path)) ok = 1;
      free(path);
    }
  }

  finishPreferencesLoad();
  return ok;
}

int 
savePreferences (void) {
  int ok = 0;
  char *path = makePreferencesFilePath(opt_preferencesFile);

  if (path) {
    if (savePreferencesFile(path)) {
      ok = 1;
      oldPreferencesEnabled = 0;
    }

    free(path);
  }

  return ok;
}

#ifdef ENABLE_API
static void
exitApiServer (void *data) {
  if (api.isServerLinked()) api.unlinkServer();
  if (api.isServerRunning()) api.stopServer();

  if (apiParameters) {
    deallocateStrings(apiParameters);
    apiParameters = NULL;
  }
}
#endif /* ENABLE_API */

static void
startApiServer (void) {
#ifdef ENABLE_API
  if (!(opt_noApi || api.isServerRunning())) {
    const char *const *parameters = api.getServerParameters();

    apiParameters = getParameters(parameters,
                                  NULL,
                                  opt_apiParameters);

    if (apiParameters) {
      api.logServerIdentity(0);
      logParameters(parameters, apiParameters, "API Parameter");

      if (!opt_verify) {
        if (api.startServer(apiParameters)) {
          onProgramExit("api-server", exitApiServer, NULL);
        }
      }
    }
  }
#endif /* ENABLE_API */
}

typedef struct {
  const char *driverType;
  const char *const *requestedDrivers;
  const char *const *autodetectableDrivers;
  const char * (*getDefaultDriver) (void);
  int (*haveDriver) (const char *code);
  int (*initializeDriver) (const char *code, int verify);
} DriverActivationData;

static int
activateDriver (const DriverActivationData *data, int verify) {
  int oneDriver = data->requestedDrivers[0] && !data->requestedDrivers[1];
  int autodetect = oneDriver && (strcmp(data->requestedDrivers[0], optionOperand_autodetect) == 0);
  const char *const defaultDrivers[] = {data->getDefaultDriver(), NULL};
  const char *const *driver;

  if (!oneDriver || autodetect) verify = 0;

  if (!autodetect) {
    driver = data->requestedDrivers;
  } else if (defaultDrivers[0]) {
    driver = defaultDrivers;
  } else if (*(driver = data->autodetectableDrivers)) {
    logMessage(LOG_DEBUG, "performing %s driver autodetection", data->driverType);
  } else {
    logMessage(LOG_DEBUG, "no autodetectable %s drivers", data->driverType);
  }

  if (!*driver) {
    driver = fallbackBrailleDrivers;
    autodetect = 0;
  }

  while (*driver) {
    if (!autodetect || data->haveDriver(*driver)) {
      logMessage(LOG_DEBUG, "checking for %s driver: %s", data->driverType, *driver);
      if (data->initializeDriver(*driver, verify)) return 1;
    }

    ++driver;
  }

  logMessage(LOG_DEBUG, "%s driver not found", data->driverType);
  return 0;
}

static void
unloadDriverObject (void **object) {
#ifdef ENABLE_SHARED_OBJECTS
  if (*object) {
    unloadSharedObject(*object);
    *object = NULL;
  }
#endif /* ENABLE_SHARED_OBJECTS */
}

void
forgetDevices (void) {
  usbForgetDevices();
  bthForgetDevices();
}

static void
initializeBrailleDisplay (void) {
  constructBrailleDisplay(&brl);
  brl.bufferResized = &brailleWindowReconfigured;
}

static LockDescriptor *
getBrailleDriverLock (void) {
  static LockDescriptor *lock = NULL;
  return getLockDescriptor(&lock, "braille-driver");
}

void
lockBrailleDriver (void) {
  obtainExclusiveLock(getBrailleDriverLock());
}

void
unlockBrailleDriver (void) {
  releaseLock(getBrailleDriverLock());
}

int
isBrailleDriverConstructed (void) {
  return brailleDriverConstructed;
}

static void
setBrailleDriverConstructed (int yes) {
  lockBrailleDriver();
  brailleDriverConstructed = yes;
  unlockBrailleDriver();

  if (brailleDriverConstructed) {
    announceBrailleOnline();
  } else {
    announceBrailleOffline();
  }

  static const brlapi_param_t parameters[] = {
    BRLAPI_PARAM_DRIVER_CODE,
    BRLAPI_PARAM_DRIVER_NAME,
    BRLAPI_PARAM_DRIVER_VERSION,
    BRLAPI_PARAM_DEVICE_MODEL,
    BRLAPI_PARAM_DEVICE_CELL_SIZE,
    BRLAPI_PARAM_DISPLAY_SIZE,
    BRLAPI_PARAM_DEVICE_IDENTIFIER,
    BRLAPI_PARAM_DEVICE_SPEED,
    BRLAPI_PARAM_DEVICE_KEY_CODES,
    BRLAPI_PARAM_BOUND_COMMAND_CODES,
  };

  const brlapi_param_t *parameter = parameters;
  const brlapi_param_t *end = parameter + ARRAY_COUNT(parameters);

  while (parameter < end) {
    api.updateParameter(*parameter++, 0);
  }
}

int
constructBrailleDriver (void) {
  initializeBrailleDisplay();

  if (braille->construct(&brl, brailleDriverParameters, brailleDevice)) {
    if (ensureBrailleBuffer(&brl, LOG_INFO)) {
      if (brl.keyBindings) {
        char *keyTablePath = makeBrailleKeyTablePath();

        logMessage(LOG_INFO, "%s: %s", gettext("Key Bindings"), brl.keyBindings);

        if (keyTablePath) {
          if (brl.keyNames) {
            if ((brl.keyTable = compileKeyTable(keyTablePath, brl.keyNames))) {
              logMessage(LOG_INFO, "%s: %s", gettext("Key Table"), keyTablePath);

              setKeyTableLogLabel(brl.keyTable, "brl");
              setLogKeyEventsFlag(brl.keyTable, &LOG_CATEGORY_FLAG(BRAILLE_KEYS));
              setKeyboardEnabledFlag(brl.keyTable, &prefs.brailleKeyboardEnabled);
            } else {
              logMessage(LOG_WARNING, "%s: %s", gettext("cannot compile key table"), keyTablePath);
            }
          }

          if (haveBrailleDisplay()) makeBrailleHelpPage(keyTablePath);
          free(keyTablePath);
        }
      }

      setBrailleDriverConstructed(1);
      startBrailleInput();
      return 1;
    }

    braille->destruct(&brl);
  } else {
    logMessage(LOG_DEBUG, "%s: %s -> %s",
               "braille driver initialization failed",
               braille->definition.code, brailleDevice);
  }

  return 0;
}

void
destructBrailleDriver (void) {
  stopBrailleInput();
  drainBrailleOutput(&brl, 0);

  setBrailleDriverConstructed(0);
  braille->destruct(&brl);

  disableBrailleHelpPage();
  destructBrailleDisplay(&brl);
}

int
isBrailleOnline (void) {
  return isBrailleDriverConstructed() && !brl.isOffline;
}

static int
initializeBrailleDriver (const char *code, int verify) {
  if ((braille = loadBrailleDriver(code, &brailleObject, opt_driversDirectory))) {
    brailleDriverParameters = getParameters(braille->parameters,
                                            braille->definition.code,
                                            brailleParameters);

    if (brailleDriverParameters) {
      int constructed = verify;

      if (!constructed) {
        logMessage(LOG_DEBUG, "initializing braille driver: %s -> %s",
                   braille->definition.code, brailleDevice);

        if (constructBrailleDriver()) {
          brailleDriver = braille;
          constructed = 1;
        }
      }

      if (constructed) {
        identifyBrailleDriver(braille, 0);
        logMessage(LOG_INFO, "%s: %s", gettext("Braille Device"), brailleDevice);

        logParameters(
          braille->parameters,
          brailleDriverParameters,
          "Braille Parameter"
        );

        {
          const char *strings[] = {
            CONFIGURATION_DIRECTORY, "/",
            PACKAGE_TARNAME, "-",
            braille->definition.code, ".prefs"
          };

          oldPreferencesFile = joinStrings(strings, ARRAY_COUNT(strings));
        }

        if (oldPreferencesFile) {
          logMessage(LOG_INFO, "%s: %s", gettext("Old Preferences File"), oldPreferencesFile);

          api.linkServer();

          return 1;
        } else {
          logMallocError();
        }
      }

      deallocateStrings(brailleDriverParameters);
      brailleDriverParameters = NULL;
    }

    unloadDriverObject(&brailleObject);
  } else {
    logMessage(LOG_ERR, "%s: %s", gettext("braille driver not loadable"), code);
  }

  braille = &noBraille;
  return 0;
}

static int
activateBrailleDriver (int verify) {
  int oneDevice = brailleDevices[0] && !brailleDevices[1];
  const char *const *device = (const char *const *)brailleDevices;

  if (!oneDevice) verify = 0;

  while (*device) {
    const char *const *autodetectableDrivers = NULL;

    brailleDevice = *device;
    logMessage(LOG_DEBUG, "checking braille device: %s", brailleDevice);

    {
      const char *dev = brailleDevice;
      const GioPublicProperties *properties = gioGetPublicProperties(&dev);

      if (properties) {
        logMessage(LOG_DEBUG, "braille device type: %s", properties->type.name);

        switch (properties->type.identifier) {
          case GIO_TYPE_SERIAL: {
            autodetectableDrivers = autodetectableBrailleDrivers_serial;
            break;
          }

          case GIO_TYPE_USB: {
            autodetectableDrivers = autodetectableBrailleDrivers_USB;
            break;
          }

          case GIO_TYPE_BLUETOOTH: {
            if (!(autodetectableDrivers = bthGetDriverCodes(dev, BLUETOOTH_DEVICE_NAME_OBTAIN_TIMEOUT))) {
              autodetectableDrivers = autodetectableBrailleDrivers_Bluetooth;
            }

            break;
          }

          default:
            break;
        }
      } else {
        logMessage(LOG_DEBUG, "unrecognized braille device type");
      }
    }

    if (!autodetectableDrivers) {
      static const char *noDrivers[] = {NULL};
      autodetectableDrivers = noDrivers;
    }

    {
      const DriverActivationData data = {
        .driverType = "braille",
        .requestedDrivers = (const char *const *)brailleDrivers,
        .autodetectableDrivers = autodetectableDrivers,
        .getDefaultDriver = getDefaultBrailleDriver,
        .haveDriver = haveBrailleDriver,
        .initializeDriver = initializeBrailleDriver
      };
      if (activateDriver(&data, verify)) return 1;
    }

    device += 1;
  }

  brailleDevice = NULL;
  return 0;
}

static void
deactivateBrailleDriver (void) {
  if (brailleDriver) {
    api.unlinkServer();
    if (brailleDriverConstructed) destructBrailleDriver();
    braille = &noBraille;
    brailleDevice = NULL;
    brailleDriver = NULL;
  }

  unloadDriverObject(&brailleObject);
  stopAllBlinkDescriptors();

  if (brailleDriverParameters) {
    deallocateStrings(brailleDriverParameters);
    brailleDriverParameters = NULL;
  }

  if (oldPreferencesFile) {
    free(oldPreferencesFile);
    oldPreferencesFile = NULL;
  }
}

static int
startBrailleDriver (void) {
  forgetDevices();

  if (activateBrailleDriver(0)) {
    if (oldPreferencesEnabled) {
      loadPreferencesFile(oldPreferencesFile);
      finishPreferencesLoad();
    } else {
      applyBraillePreferences();
    }

    ensureStatusFields();
    alert(ALERT_BRAILLE_ON);

    ses->winx = 0;
    trackScreenCursor(1);

    if (clearStatusCells(&brl)) {
      if (opt_quiet) {
        scheduleUpdate("braille driver start");
        return 1;
      }

      {
        char banner[0X100];
        const char *text = opt_startMessage;

        if (*text) {
          text = gettext(text);
        } else {
          makeProgramBanner(banner, sizeof(banner), 0);
          text = banner;
        }

        if (message(NULL, text, MSG_SILENT)) return 1;
      }
    }

    deactivateBrailleDriver();
  }

  return 0;
}

static void
stopBrailleDriver (void) {
  deactivateBrailleDriver();
  alert(ALERT_BRAILLE_OFF);
}

static int
prepareBrailleDriverActivity (void *data) {
  initializeBrailleDisplay();
  ensureBrailleBuffer(&brl, LOG_DEBUG);
  return 1;
}

static int
startBrailleDriverActivity (void *data) {
  return startBrailleDriver();
}

static void
stopBrailleDriverActivity (void *data) {
  stopBrailleDriver();
}

static const ActivityMethods brailleDriverActivityMethods = {
  .activityName = "braille-driver",
  .retryInterval = BRAILLE_DRIVER_START_RETRY_INTERVAL,

  .prepare = prepareBrailleDriverActivity,
  .start = startBrailleDriverActivity,
  .stop = stopBrailleDriverActivity
};

static ActivityObject *brailleDriverActivity = NULL;

static void
writeBrailleMessage (const char *text) {
  clearStatusCells(&brl);
  message(NULL, text, (MSG_NODELAY | MSG_SILENT | MSG_SYNC));
  brl.noDisplay = 1;
}

static void
exitBrailleDriver (void *data) {
  if (brailleDriverConstructed) {
    const char *text = opt_stopMessage;

    if (*text) {
      text = gettext(text);
    } else {
      text = gettext("BRLTTY stopped");
    }

    writeBrailleMessage(text);
  }

  if (brailleDriverActivity) {
    destroyActivity(brailleDriverActivity);
    brailleDriverActivity = NULL;
  }

  forgetDevices();
}

static ActivityObject *
getBrailleDriverActivity (int allocate) {
  if (!brailleDriverActivity) {
    if (allocate) {
      if (!(brailleDriverActivity = newActivity(&brailleDriverActivityMethods, NULL))) {
        return NULL;
      }

      onProgramExit("braille-driver", exitBrailleDriver, NULL);
    }
  }

  return brailleDriverActivity;
}

static int canEnableBrailleDriver = 1;

void
enableBrailleDriver (void) {
  if (canEnableBrailleDriver) {
    ActivityObject *activity = getBrailleDriverActivity(1);
    if (activity) startActivity(activity);
  }
}

void
disableBrailleDriver (const char *reason) {
  ActivityObject *activity = getBrailleDriverActivity(0);

  if (activity) {
    if (reason) writeBrailleMessage(reason);
    stopActivity(activity);
  }
}

void
setBrailleOn (void) {
  if (!canEnableBrailleDriver) {
    canEnableBrailleDriver = 1;
    enableBrailleDriver();
  }
}

void
setBrailleOff (const char *message) {
  canEnableBrailleDriver = 0;
  disableBrailleDriver(message);
}

void
restartBrailleDriver (void) {
  disableBrailleDriver(gettext("braille driver restarting"));
  awaitActivityStopped(brailleDriverActivity);
  brl.hasFailed = 0;

  logMessage(LOG_INFO, gettext("reinitializing braille driver"));
  enableBrailleDriver();
}

static void
exitBrailleData (void *data) {
  if (brailleDrivers) {
    deallocateStrings(brailleDrivers);
    brailleDrivers = NULL;
  }

  if (brailleParameters) {
    free(brailleParameters);
    brailleParameters = NULL;
  }

  if (brailleDevices) {
    deallocateStrings(brailleDevices);
    brailleDevices = NULL;
  }
}

int
changeBrailleDriver (const char *driver) {
  return changeListSetting(&brailleDrivers, &opt_brailleDriver, driver);
}

int
changeBrailleParameters (const char *parameters) {
  if (!parameters) parameters = "";
  return changeStringSetting(&brailleParameters, parameters);
}

int
changeBrailleDevice (const char *device) {
  return changeListSetting(&brailleDevices, &opt_brailleDevice, device);
}

#ifdef ENABLE_SPEECH_SUPPORT
static AsyncHandle autospeakDelayAlarm = NULL;

static void
cancelAutospeakDelayAlarm (void) {
  if (autospeakDelayAlarm) {
    asyncCancelRequest(autospeakDelayAlarm);
    autospeakDelayAlarm = NULL;
  }
}

static void
endAutospeakDelay (SpeechSynthesizer *spk) {
  cancelAutospeakDelayAlarm();

  if (!spk->canAutospeak) {
    spk->canAutospeak = 1;
    scheduleUpdate("banner spoken");
  }
}

ASYNC_ALARM_CALLBACK(handleAutospeakDelayAlarm) {
  asyncDiscardHandle(autospeakDelayAlarm);
  autospeakDelayAlarm = NULL;

  endAutospeakDelay(&spk);
}

static void
beginAutospeakDelay (int duration) {
  if (asyncNewRelativeAlarm(&autospeakDelayAlarm, duration,
                            handleAutospeakDelayAlarm, NULL)) {
    spk.canAutospeak = 0;
  }
}

static void
setSpeechFinished (SpeechSynthesizer *spk) {
  spk->track.isActive = 0;
  spk->track.speechLocation = SPK_LOC_NONE;

  endAutospeakDelay(spk);
}

static void
setSpeechLocation (SpeechSynthesizer *spk, int location) {
  if (spk->track.isActive) {
    if (scr.number == spk->track.screenNumber) {
      if (location != spk->track.speechLocation) {
        spk->track.speechLocation = location;
        if (ses->trackScreenCursor) trackSpeech();
      }

      return;
    }

    setSpeechFinished(spk);
  }
}

static void
initializeSpeechSynthesizer (void) {
  constructSpeechSynthesizer(&spk);
  spk.setFinished = setSpeechFinished;
  spk.setLocation = setSpeechLocation;
}

int
constructSpeechDriver (void) {
  initializeSpeechSynthesizer();

  if (startSpeechDriverThread(&spk, speechDriverParameters)) {
    return 1;
  } else {
    logMessage(LOG_DEBUG, "speech driver initialization failed: %s",
               speech->definition.code);
  }

  return 0;
}

void
destructSpeechDriver (void) {
  stopSpeechDriverThread(&spk);
  destructSpeechSynthesizer(&spk);
}

static int
initializeSpeechDriver (const char *code, int verify) {
  if ((speech = loadSpeechDriver(code, &speechObject, opt_driversDirectory))) {
    speechDriverParameters = getParameters(speech->parameters,
                                           speech->definition.code,
                                           speechParameters);

    if (speechDriverParameters) {
      int constructed = verify;

      if (!constructed) {
        logMessage(LOG_DEBUG, "initializing speech driver: %s",
                   speech->definition.code);

        if (constructSpeechDriver()) {
          constructed = 1;
          speechDriver = speech;
        }
      }

      if (constructed) {
        identifySpeechDriver(speech, 0);

        logParameters(
          speech->parameters,
          speechDriverParameters,
          "Speech Parameter"
        );

        return 1;
      }

      deallocateStrings(speechDriverParameters);
      speechDriverParameters = NULL;
    }

    unloadDriverObject(&speechObject);
  } else {
    logMessage(LOG_ERR, "%s: %s", gettext("speech driver not loadable"), code);
  }

  speech = &noSpeech;
  return 0;
}

static int
activateSpeechDriver (int verify) {
  static const char *const autodetectableDrivers[] = {
    NULL
  };

  const DriverActivationData data = {
    .driverType = "speech",
    .requestedDrivers = (const char *const *)speechDrivers,
    .autodetectableDrivers = autodetectableDrivers,
    .getDefaultDriver = getDefaultSpeechDriver,
    .haveDriver = haveSpeechDriver,
    .initializeDriver = initializeSpeechDriver
  };

  return activateDriver(&data, verify);
}

static void
deactivateSpeechDriver (void) {
  if (speechDriver) {
    destructSpeechDriver();

    speech = &noSpeech;
    speechDriver = NULL;
  }

  unloadDriverObject(&speechObject);

  if (speechDriverParameters) {
    deallocateStrings(speechDriverParameters);
    speechDriverParameters = NULL;
  }
}

static int
startSpeechDriver (void) {
  if (!activateSpeechDriver(0)) return 0;
  applySpeechPreferences();

  if (!opt_quiet && spk.sayBanner) {
    char banner[0X100];
    const char *text = opt_startMessage;

    if (*text) {
      text = gettext(text);
    } else {
      makeProgramBanner(banner, sizeof(banner), 0);
      text = banner;
    }

    sayString(&spk, text, SAY_OPT_MUTE_FIRST);
    beginAutospeakDelay(SPEECH_DRIVER_START_AUTOSPEAK_DELAY);
  } else if (isAutospeakActive()) {
    autospeak(AUTOSPEAK_FORCE);
  }

  return 1;
}

static void
stopSpeechDriver (void) {
  cancelAutospeakDelayAlarm();

  muteSpeech(&spk, "driver stop");
  deactivateSpeechDriver();
}

static int
prepareSpeechDriverActivity (void *data) {
  initializeSpeechSynthesizer();
  return 1;
}

static int
startSpeechDriverActivity (void *data) {
  return startSpeechDriver();
}

static void
stopSpeechDriverActivity (void *data) {
  stopSpeechDriver();
}

static const ActivityMethods speechDriverActivityMethods = {
  .activityName = "speech-driver",
  .retryInterval = SPEECH_DRIVER_START_RETRY_INTERVAL,

  .prepare = prepareSpeechDriverActivity,
  .start = startSpeechDriverActivity,
  .stop = stopSpeechDriverActivity
};

static ActivityObject *speechDriverActivity = NULL;

static void
exitSpeechDriver (void *data) {
  if (speechDriverActivity) {
    destroyActivity(speechDriverActivity);
    speechDriverActivity = NULL;
  }
}

static ActivityObject *
getSpeechDriverActivity (int allocate) {
  if (!speechDriverActivity) {
    if (allocate) {
      if (!(speechDriverActivity = newActivity(&speechDriverActivityMethods, NULL))) {
        return NULL;
      }

      onProgramExit("speech-driver", exitSpeechDriver, NULL);
    }
  }

  return speechDriverActivity;
}

void
enableSpeechDriver (int sayBanner) {
  ActivityObject *activity = getSpeechDriverActivity(1);

  spk.sayBanner = sayBanner;
  if (activity) startActivity(activity);
}

void
disableSpeechDriver (const char *reason) {
  ActivityObject *activity = getSpeechDriverActivity(0);

  if (activity) {
    if (reason) {
      sayString(&spk, reason, SAY_OPT_MUTE_FIRST);
      drainSpeech(&spk);
    }

    stopActivity(activity);
  }
}

void
restartSpeechDriver (void) {
  disableSpeechDriver(gettext("speech driver restarting"));
  awaitActivityStopped(speechDriverActivity);

  logMessage(LOG_INFO, gettext("reinitializing speech driver"));
  enableSpeechDriver(1);
}

static void
exitSpeechData (void *data) {
  if (speechDrivers) {
    deallocateStrings(speechDrivers);
    speechDrivers = NULL;
  }

  if (speechParameters) {
    free(speechParameters);
    speechParameters = NULL;
  }
}

static void
exitSpeechInput (void *data) {
  if (speechInputObject) {
    destroySpeechInputObject(speechInputObject);
    speechInputObject = NULL;
  }
}

int
changeSpeechDriver (const char *driver) {
  return changeListSetting(&speechDrivers, &opt_speechDriver, driver);
}

int
changeSpeechParameters (const char *parameters) {
  if (!parameters) parameters = "";
  return changeStringSetting(&speechParameters, parameters);
}
#endif /* ENABLE_SPEECH_SUPPORT */

static int
initializeScreenDriver (const char *code, int verify) {
  if ((screen = loadScreenDriver(code, &screenObject, opt_driversDirectory))) {
    screenDriverParameters = getParameters(
      getScreenParameters(screen),
      screen->definition.code,
      screenParameters
    );

    if (screenDriverParameters) {
      int constructed = verify;

      if (!constructed) {
        logMessage(LOG_DEBUG,
          "initializing screen driver: %s",
          screen->definition.code
        );

        if (constructScreenDriver(screenDriverParameters)) {
          constructed = 1;
          screenDriver = screen;
        }
      }

      if (constructed) {
        identifyScreenDriver(screen, 0);

        logParameters(
          getScreenParameters(screen),
          screenDriverParameters,
          "Screen Parameter"
        );

        return 1;
      }

      deallocateStrings(screenDriverParameters);
      screenDriverParameters = NULL;
    }

    unloadDriverObject(&screenObject);
  } else {
    logMessage(LOG_ERR, "%s: %s", gettext("screen driver not loadable"), code);
  }

  setNoScreen();
  return 0;
}

static int
activateScreenDriver (int verify) {
  static const char *const autodetectableDrivers[] = {
    NULL
  };

  const DriverActivationData data = {
    .driverType = "screen",
    .requestedDrivers = (const char *const *)screenDrivers,
    .autodetectableDrivers = autodetectableDrivers,
    .getDefaultDriver = getDefaultScreenDriver,
    .haveDriver = haveScreenDriver,
    .initializeDriver = initializeScreenDriver
  };

  return activateDriver(&data, verify);
}

static void
deactivateScreenDriver (void) {
  if (screenDriver) {
    destructScreenDriver();

    setNoScreen();
    screenDriver = NULL;
  }

  unloadDriverObject(&screenObject);

  if (screenDriverParameters) {
    deallocateStrings(screenDriverParameters);
    screenDriverParameters = NULL;
  }
}

static int
startScreenDriver (void) {
  if (!activateScreenDriver(0)) return 0;
  if (isMainScreen()) scheduleUpdate("main screen started");
  return 1;
}

static void
stopScreenDriver (void) {
  deactivateScreenDriver();
}

static int
prepareScreenDriverActivity (void *data) {
  return 1;
}

static int
startScreenDriverActivity (void *data) {
  return startScreenDriver();
}

static void
stopScreenDriverActivity (void *data) {
  stopScreenDriver();
}

static const ActivityMethods screenDriverActivityMethods = {
  .activityName = "screen-driver",
  .retryInterval = SCREEN_DRIVER_START_RETRY_INTERVAL,

  .prepare = prepareScreenDriverActivity,
  .start = startScreenDriverActivity,
  .stop = stopScreenDriverActivity
};

static ActivityObject *screenDriverActivity = NULL;

static void
exitScreenDriver (void *data) {
  if (screenDriverActivity) {
    destroyActivity(screenDriverActivity);
    screenDriverActivity = NULL;
  }
}

static ActivityObject *
getScreenDriverActivity (int allocate) {
  if (!screenDriverActivity) {
    if (allocate) {
      if (!(screenDriverActivity = newActivity(&screenDriverActivityMethods, NULL))) {
        return NULL;
      }

      onProgramExit("screen-driver", exitScreenDriver, NULL);
    }
  }

  return screenDriverActivity;
}

void
enableScreenDriver (void) {
  ActivityObject *activity = getScreenDriverActivity(1);

  setNoScreenDriverReason(NULL);
  if (activity) startActivity(activity);
}

void
disableScreenDriver (const char *reason) {
  ActivityObject *activity = getScreenDriverActivity(0);

  setNoScreenDriverReason(reason);
  if (activity) stopActivity(activity);
}

void
restartScreenDriver (void) {
  disableScreenDriver(gettext("screen driver restarting"));
  awaitActivityStopped(screenDriverActivity);

  logMessage(LOG_INFO, gettext("reinitializing screen driver"));
  enableScreenDriver();
}

static void
exitScreenData (void *data) {
  endSpecialScreens();

  if (screenDrivers) {
    deallocateStrings(screenDrivers);
    screenDrivers = NULL;
  }

  if (screenParameters) {
    free(screenParameters);
    screenParameters = NULL;
  }
}

int
changeScreenDriver (const char *driver) {
  return changeListSetting(&screenDrivers, &opt_screenDriver, driver);
}

int
changeScreenParameters (const char *parameters) {
  if (!parameters) parameters = "";
  return changeStringSetting(&screenParameters, parameters);
}

int
changeMessageLocale (const char *locale) {
  int changed = !!setlocale(LC_ALL, locale);

  if (changed) {
    api.updateParameter(BRLAPI_PARAM_MESSAGE_LOCALE, 0);
  } else {
    logMessage(LOG_WARNING, "message locale change failed: %s", locale);
  }

  return changed;
}

static void
exitPidFile (void *data) {
#if defined(GRUB_RUNTIME)

#else /* remove pid file */
  unlink(opt_pidFile);
#endif /* remove pid file */
}

static int
makePidFile (ProcessIdentifier pid) {
  return createPidFile(opt_pidFile, pid);
}

static int tryPidFile (void);

ASYNC_ALARM_CALLBACK(retryPidFile) {
  tryPidFile();
}

static int
tryPidFile (void) {
  if (makePidFile(0)) {
    onProgramExit("pid-file", exitPidFile, NULL);
  } else if (errno == EEXIST) {
    return 0;
  } else {
    asyncNewRelativeAlarm(NULL, PID_FILE_CREATE_RETRY_INTERVAL, retryPidFile, NULL);
  }

  return 1;
}

#if defined(__MINGW32__)
static void
background (void) {
  char *variableName;

  {
    const char *strings[] = {programName, "_DAEMON"};
    variableName = joinStrings(strings, ARRAY_COUNT(strings));
  }

  {
    int i;
    for (i=0; variableName[i]; i+=1) {
      char c = variableName[i];

      if (c == '_') continue;
      if (isdigit((unsigned char)c) && (i > 0)) continue;

      if (isalpha((unsigned char)c)) {
        if (islower((unsigned char)c)) variableName[i] = toupper((unsigned char)c);
        continue;
      }

      variableName[i] = '_';
    }
  }

  if (!getenv(variableName)) {
    LPTSTR commandLine = GetCommandLine();
    STARTUPINFO startupInfo;
    PROCESS_INFORMATION processInfo;
    
    memset(&startupInfo, 0, sizeof(startupInfo));
    startupInfo.cb = sizeof(startupInfo);

    if (!SetEnvironmentVariable(variableName, "BACKGROUND")) {
      logWindowsSystemError("SetEnvironmentVariable");
      exit(PROG_EXIT_FATAL);
    }

    if (!CreateProcess(NULL, commandLine, NULL, NULL, TRUE,
                       CREATE_NEW_PROCESS_GROUP | CREATE_SUSPENDED,
                       NULL, NULL, &startupInfo, &processInfo)) {
      logWindowsSystemError("CreateProcess");
      exit(PROG_EXIT_FATAL);
    }

    {
      int created = makePidFile(processInfo.dwProcessId);
      int resumed = ResumeThread(processInfo.hThread) != -1;

      if (!created) {
        if (errno == EEXIST) {
          ExitProcess(PROG_EXIT_FATAL);
        }
      }

      if (!resumed) {
        logWindowsSystemError("ResumeThread");
        ExitProcess(PROG_EXIT_FATAL);
      }
    }

    ExitProcess(PROG_EXIT_SUCCESS);
  }

  free(variableName);
}

#elif defined(__MSDOS__)
static void
background (void) {
  msdosBackground();
}

#elif defined(GRUB_RUNTIME)
static void
background (void) {
}

#else /* Unix */
static void
background (void) {
  int fds[2];

  if (pipe(fds) == -1) {
    logSystemError("pipe");
    exit(PROG_EXIT_FATAL);
  }

  fflush(stdout);
  fflush(stderr);

  {
    pid_t child = fork();

    if (child == -1) {
      logSystemError("fork");
      exit(PROG_EXIT_FATAL);
    }

    if (child) {
      ProgramExitStatus exitStatus = PROG_EXIT_SUCCESS;

      if (close(fds[0]) == -1) logSystemError("close");

      if (!makePidFile(child)) {
        if (errno == EEXIST) {
          exitStatus = PROG_EXIT_SEMANTIC;
        }
      }

      if (close(fds[1]) == -1) logSystemError("close");
      _exit(exitStatus);
    }
  }

  if (close(fds[1]) == -1) logSystemError("close");

  {
    unsigned char buffer[1];

    if (read(fds[0], buffer, sizeof(buffer)) == -1) logSystemError("read");
    if (close(fds[0]) == -1) logSystemError("close");
  }

  if (setsid() == -1) {                        
    logSystemError("setsid");
    exit(PROG_EXIT_FATAL);
  }
}
#endif /* background() */

static int
validateInterval (int *value, const char *string) {
  if (!*string) return 1;

  {
    static const int minimum = 1;
    int ok = validateInteger(value, string, &minimum, NULL);
    if (ok) *value *= 10;
    return ok;
  }
}

ProgramExitStatus
brlttyStart (void) {
  if (opt_cancelExecution) {
    ProgramExitStatus exitStatus;

    if (!*opt_pidFile) {
      exitStatus = PROG_EXIT_SEMANTIC;
      logMessage(LOG_ERR, "%s", gettext("pid file not specified"));
    } else if (cancelProgram(opt_pidFile)) {
      exitStatus = PROG_EXIT_FORCE;
    } else {
      exitStatus = PROG_EXIT_FATAL;
    }

    return exitStatus;
  }

  {
    int stop = 0;

    if (opt_removeService) {
      removeService(SERVICE_NAME);
      stop = 1;
    }

    if (opt_installService) {
      installService(SERVICE_NAME, SERVICE_DESCRIPTION, opt_configurationFile);
      stop = 1;
    }

    if (stop) return PROG_EXIT_FORCE;
  }

  if (!validateInterval(&messageHoldTimeout, opt_messageTime)) {
    logMessage(LOG_ERR, "%s: %s", gettext("invalid message hold timeout"), opt_messageTime);
  }

  if (opt_version) {
    logMessage(LOG_INFO, "Copyright %s", PACKAGE_COPYRIGHT);
    identifyScreenDrivers(1);

#ifdef ENABLE_API
    api.logServerIdentity(1);
#endif /* ENABLE_API */

    identifyBrailleDrivers(1);

#ifdef ENABLE_SPEECH_SUPPORT
    identifySpeechDrivers(1);
#endif /* ENABLE_SPEECH_SUPPORT */

    return PROG_EXIT_FORCE;
  }

  if (opt_verify) opt_noDaemon = 1;
  if (!opt_noDaemon
#ifdef __MINGW32__
      && !isWindowsService
#endif
     ) {
    background();
  }

  if (*opt_pidFile) {
    if (!tryPidFile()) {
      return PROG_EXIT_SEMANTIC;
    }
  }

  if (!opt_noDaemon) {
    stderrLogLevel = 0;

    detachStandardInput();
    detachStandardOutput();
    if (!opt_standardError) detachStandardError();

#ifdef __MINGW32__
    {
      HANDLE h = CreateFile("NUL", GENERIC_READ|GENERIC_WRITE,
                            FILE_SHARE_READ|FILE_SHARE_WRITE,
                            NULL, OPEN_EXISTING, 0, NULL);

      if (!h) {
        logWindowsSystemError("CreateFile[NUL]");
      } else {
        SetStdHandle(STD_INPUT_HANDLE, h);
        SetStdHandle(STD_OUTPUT_HANDLE, h);

        if (!opt_standardError) {
          SetStdHandle(STD_ERROR_HANDLE, h);
        }
      }
    }
#endif /* __MINGW32__ */
  }

  /*
   * From this point, all IO functions as printf, puts, perror, etc. can't be
   * used anymore since we are a daemon.  The logMessage() facility should 
   * be used instead.
   */

  changeScreenDriver(opt_screenDriver);
  changeScreenParameters(opt_screenParameters);
  beginSpecialScreens();
  onProgramExit("screen-data", exitScreenData, NULL);

  suppressTuneDeviceOpenErrors();

  {
    char *directory;

    if ((directory = getWorkingDirectory())) {
      logProperty(directory, "workingDirectory", "Working Directory");
      free(directory);
    } else {
      logMessage(LOG_WARNING, "%s: %s", gettext("cannot determine working directory"), strerror(errno));
    }
  }

  logProperty(opt_configurationFile, "configurationFile", "Configuration File");
  logProperty(opt_tablesDirectory, "tablesDirectory", "Tables Directory");
  logProperty(opt_driversDirectory, "driversDirectory", "Drivers Directory");
  logProperty(opt_writableDirectory, "writableDirectory", "Writable Directory");
  logProperty(opt_updatableDirectory, "updatableDirectory", "Updatable Directory");
  logProperty(opt_preferencesFile, "preferencesFile", "Preferences File");

  resetPreferences();
  loadPreferences(0);

  if (opt_promptPatterns && *opt_promptPatterns) {
    int count;
    char **patterns = splitString(opt_promptPatterns, PARAMETER_SEPARATOR_CHARACTER, &count);

    if (patterns) {
      for (int index=0; index<count; index+=1) {
        if (!addPromptPattern(patterns[index])) break;
      }

      deallocateStrings(patterns);
    }
  }

  setTextAndContractionTables();
  setAttributesTable();
  setKeyboardTable();

  /* initialize screen driver */
  if (opt_verify) {
    if (activateScreenDriver(1)) deactivateScreenDriver();
  } else {
    setNoScreen();
    enableScreenDriver();
  }
  
  /* The device(s) the braille display might be connected to. */
  if (!*opt_brailleDevice) {
    logMessage(LOG_ERR, gettext("braille device not specified"));
    return PROG_EXIT_SYNTAX;
  }

  constructBrailleDisplay(&brl);
  changeBrailleDriver(opt_brailleDriver);
  changeBrailleParameters(opt_brailleParameters);
  changeBrailleDevice(opt_brailleDevice);
  brailleDriverConstructed = 0;
  onProgramExit("braille-data", exitBrailleData, NULL);

  if (opt_verify) {
    if (activateBrailleDriver(1)) deactivateBrailleDriver();
  } else {
    enableBrailleDriver();
  }

#ifdef ENABLE_SPEECH_SUPPORT
  constructSpeechSynthesizer(&spk);
  changeSpeechDriver(opt_speechDriver);
  changeSpeechParameters(opt_speechParameters);
  onProgramExit("speech-data", exitSpeechData, NULL);

  if (opt_verify) {
    if (activateSpeechDriver(1)) deactivateSpeechDriver();
  } else {
    enableSpeechDriver(1);
  }

  /* Create the file system object for speech input. */
  logProperty(opt_speechInput, "speechInput", "Speech Input");
  if (!opt_verify) {
    if (*opt_speechInput) {
      speechInputObject = newSpeechInputObject(opt_speechInput);
      onProgramExit("speech-input", exitSpeechInput, NULL);
    }
  }
#endif /* ENABLE_SPEECH_SUPPORT */

  startApiServer();

  if (!opt_verify) notifyServiceReady();

  return opt_verify? PROG_EXIT_FORCE: PROG_EXIT_SUCCESS;
}

static char *configuredLocale = "";

static int
changeLocale (const char *locale) {
  if (changeMessageLocale(locale)) return 1;
  setlocale(LC_ALL, configuredLocale);
  return 0;
}

static const ProfileProperty languageProfileProperties[] = {
  { .name = WS_C("locale"),
    .defaultValue = &configuredLocale,
    .change = changeLocale
  },

#ifdef ENABLE_SPEECH_SUPPORT
  { .name = WS_C("speech-driver"),
    .defaultValue = &opt_speechDriver,
    .change = changeSpeechDriver
  },

  { .name = WS_C("speech-parameters"),
    .defaultValue = &opt_speechParameters,
    .change = changeSpeechParameters
  },
#endif /* ENABLE_SPEECH_SUPPORT */

  { .name = WS_C("text-table"),
    .defaultValue = &opt_textTable,
    .change = changeTextTable
  },

  { .name = WS_C("contraction-table"),
    .defaultValue = &opt_contractionTable,
    .change = changeContractionTable
  },
};

static int
beginLanguageProfile (void) {
#ifdef ENABLE_SPEECH_SUPPORT
  disableSpeechDriver(NULL);
  awaitActivityStopped(speechDriverActivity);
#endif /* ENABLE_SPEECH_SUPPORT */

  return 1;
}

static int
endLanguageProfile (void) {
#ifdef ENABLE_SPEECH_SUPPORT
  enableSpeechDriver(0);
#endif /* ENABLE_SPEECH_SUPPORT */

  if (brl.keyTable) {
    char *path = makeBrailleKeyTablePath();

    if (path) {
      disableBrailleHelpPage();
      makeBrailleHelpPage(path);
      free(path);
    }
  }

  if (keyboardTable) {
    disableKeyboardHelpPage();
    makeKeyboardHelpPage();
  }

  return 1;
}

const ProfileDescriptor languageProfile = {
  .category = strtext("Language"),
  .extension = LANGUAGE_PROFILE_EXTENSION,

  .begin = beginLanguageProfile,
  .end = endLanguageProfile,

  .properties = {
    .array = languageProfileProperties,
    .count = ARRAY_COUNT(languageProfileProperties)
  }
};
