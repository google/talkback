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
#include <ctype.h>
#include <locale.h>
#include <errno.h>
#include <fcntl.h>

#include "parameters.h"
#include "embed.h"
#include "log.h"
#include "report.h"
#include "strfmt.h"
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
#include "async_alarm.h"
#include "program.h"
#include "revision.h"
#include "service.h"
#include "options.h"
#include "profile_types.h"
#include "brl_input.h"
#include "cmd_queue.h"
#include "core.h"
#include "api_control.h"
#include "prefs.h"
#include "charset.h"

#include "io_generic.h"
#include "io_usb.h"
#include "io_bluetooth.h"

#ifdef __MINGW32__
int isWindowsService = 0;
#endif /* __MINGW32__ */

#ifdef __MSDOS__
#include "system_msdos.h"
#endif /* __MSDOS__ */

static const char optionOperand_none[] = "no";
static const char optionOperand_autodetect[] = "auto";

#define SERVICE_NAME "BrlAPI"
#define SERVICE_DESCRIPTION "Braille API (BrlAPI)"

static
STR_BEGIN_FORMATTER(formatLogLevelString, unsigned int index)
  switch (index) {
    case 0:
      STR_PRINTF("0-%u", logLevelCount-1);
      break;

    case 1: {
      unsigned int level;

      for (level=0; level<logLevelCount; level+=1) {
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

static int opt_version;
static int opt_verify;
static int opt_quiet;
static int opt_noDaemon;
static int opt_standardError;
static char *opt_logLevel;
static char *opt_logFile;
static int opt_bootParameters = 1;
static int opt_environmentVariables;
static char *opt_messageHoldTimeout;

static int opt_cancelExecution;
static const char *const optionStrings_CancelExecution[] = {
  PACKAGE_TARNAME,
  NULL
};

static char *opt_pidFile;
static char *opt_configurationFile;
static char *opt_preferencesFile;
static char *opt_preferenceOverrides;
static char *opt_promptPatterns;

static char *opt_updatableDirectory;
static char *opt_writableDirectory;
static char *opt_driversDirectory;

static char *opt_brailleDevice;
int opt_releaseDevice;
static char **brailleDevices = NULL;
static const char *brailleDevice = NULL;
static int brailleConstructed;

static char *opt_brailleDriver;
static char **brailleDrivers = NULL;
static const BrailleDriver *brailleDriver = NULL;
static void *brailleObject = NULL;
static char *opt_brailleParameters;
static char *brailleParameters = NULL;
static char **brailleDriverParameters = NULL;
static char *oldPreferencesFile = NULL;
static int oldPreferencesEnabled = 1;

char *opt_tablesDirectory;
char *opt_textTable;
char *opt_attributesTable;

#ifdef ENABLE_CONTRACTED_BRAILLE
char *opt_contractionTable;
ContractionTable *contractionTable = NULL;
#endif /* ENABLE_CONTRACTED_BRAILLE */

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
  { .letter = 'Y',
    .word = "start-message",
    .flags = OPT_Hidden | OPT_Config | OPT_Environ,
    .argument = strtext("text"),
    .setting.string = &opt_startMessage,
    .description = strtext("The text to be shown when the braille driver starts and to be spoken when the speech driver starts.")
  },

  { .letter = 'Z',
    .word = "stop-message",
    .flags = OPT_Hidden | OPT_Config | OPT_Environ,
    .argument = strtext("text"),
    .setting.string = &opt_stopMessage,
    .description = strtext("The text to be shown when the braille driver stops.")
  },

  { .letter = 'E',
    .word = "environment-variables",
    .flags = OPT_Hidden,
    .setting.flag = &opt_environmentVariables,
    .description = strtext("Recognize environment variables.")
  },

  { .letter = 'n',
    .word = "no-daemon",
    .flags = OPT_Hidden,
    .setting.flag = &opt_noDaemon,
    .description = strtext("Remain a foreground process.")
  },

  { .letter = 'I',
    .word = "install-service",
    .flags = OPT_Hidden,
    .setting.flag = &opt_installService,
    .description = strtext("Install the %s service, and then exit."),
    .strings.array = optionStrings_InstallService
  },

  { .letter = 'R',
    .word = "remove-service",
    .flags = OPT_Hidden,
    .setting.flag = &opt_removeService,
    .description = strtext("Remove the %s service, and then exit."),
    .strings.array = optionStrings_RemoveService
  },

  { .letter = 'C',
    .word = "cancel-execution",
    .flags = OPT_Hidden,
    .setting.flag = &opt_cancelExecution,
    .description = strtext("Stop an existing instance of %s, and then exit."),
    .strings.array = optionStrings_CancelExecution
  },

  { .letter = 'P',
    .word = "pid-file",
    .flags = OPT_Hidden | OPT_Config | OPT_Environ,
    .argument = strtext("file"),
    .setting.string = &opt_pidFile,
    .internal.adjust = fixInstallPath,
    .description = strtext("Path to process identifier file.")
  },

  { .letter = 'f',
    .word = "configuration-file",
    .flags = OPT_Environ,
    .argument = strtext("file"),
    .setting.string = &opt_configurationFile,
    .internal.setting = CONFIGURATION_DIRECTORY "/" CONFIGURATION_FILE,
    .internal.adjust = fixInstallPath,
    .description = strtext("Path to default settings file.")
  },

  { .letter = 'F',
    .word = "preferences-file",
    .flags = OPT_Hidden | OPT_Config | OPT_Environ,
    .argument = strtext("file"),
    .setting.string = &opt_preferencesFile,
    .internal.setting = PREFERENCES_FILE,
    .description = strtext("Name of or path to default preferences file.")
  },

  { .letter = 'o',
    .word = "override-preference",
    .flags = OPT_Extend | OPT_Config | OPT_Environ,
    .argument = strtext("name=value,..."),
    .setting.string = &opt_preferenceOverrides,
    .description = strtext("Explicit preference settings.")
  },

  { .letter = 'z',
    .word = "prompt-patterns",
    .flags = OPT_Extend | OPT_Config | OPT_Environ,
    .argument = strtext("regexp,..."),
    .setting.string = &opt_promptPatterns,
    .description = strtext("Patterns that match command prompts.")
  },

  { .letter = 'U',
    .word = "updatable-directory",
    .flags = OPT_Hidden | OPT_Config | OPT_Environ,
    .argument = strtext("directory"),
    .setting.string = &opt_updatableDirectory,
    .internal.setting = UPDATABLE_DIRECTORY,
    .internal.adjust = fixInstallPath,
    .description = strtext("Path to directory which contains files that can be updated.")
  },

  { .letter = 'W',
    .word = "writable-directory",
    .flags = OPT_Hidden | OPT_Config | OPT_Environ,
    .argument = strtext("directory"),
    .setting.string = &opt_writableDirectory,
    .internal.setting = WRITABLE_DIRECTORY,
    .internal.adjust = fixInstallPath,
    .description = strtext("Path to directory which can be written to.")
  },

  { .letter = 'D',
    .word = "drivers-directory",
    .flags = OPT_Hidden | OPT_Config | OPT_Environ,
    .argument = strtext("directory"),
    .setting.string = &opt_driversDirectory,
    .internal.setting = DRIVERS_DIRECTORY,
    .internal.adjust = fixInstallPath,
    .description = strtext("Path to directory containing drivers.")
  },

#ifdef ENABLE_API
  { .letter = 'N',
    .word = "no-api",
    .flags = OPT_Hidden | OPT_Config | OPT_Environ,
    .setting.flag = &opt_noApi,
    .description = strtext("Disable the application programming interface.")
  },

  { .letter = 'A',
    .word = "api-parameters",
    .flags = OPT_Extend | OPT_Config | OPT_Environ,
    .argument = strtext("name=value,..."),
    .setting.string = &opt_apiParameters,
    .internal.setting = API_PARAMETERS,
    .description = strtext("Parameters for the application programming interface.")
  },
#endif /* ENABLE_API */

  { .letter = 'b',
    .word = "braille-driver",
    .bootParameter = 1,
    .flags = OPT_Config | OPT_Environ,
    .argument = strtext("driver,..."),
    .setting.string = &opt_brailleDriver,
    .internal.setting = optionOperand_autodetect,
    .description = strtext("Braille driver code (%s, %s, or one of {%s})."),
    .strings.array = optionStrings_BrailleDriver
  },

  { .letter = 'B',
    .word = "braille-parameters",
    .bootParameter = 4,
    .flags = OPT_Extend | OPT_Config | OPT_Environ,
    .argument = strtext("name=value,..."),
    .setting.string = &opt_brailleParameters,
    .internal.setting = BRAILLE_PARAMETERS,
    .description = strtext("Parameters for the braille driver.")
  },

  { .letter = 'd',
    .word = "braille-device",
    .bootParameter = 2,
    .flags = OPT_Config | OPT_Environ,
    .argument = strtext("identifier,..."),
    .setting.string = &opt_brailleDevice,
    .internal.setting = BRAILLE_DEVICE,
    .description = strtext("Device for accessing braille display.")
  },

  { .letter = 'r',
    .word = "release-device",
    .flags = OPT_Hidden | OPT_Config | OPT_Environ,
    .setting.flag = &opt_releaseDevice,
#ifdef WINDOWS
    .internal.setting = FLAG_TRUE_WORD,
#else /* WINDOWS */
    .internal.setting = FLAG_FALSE_WORD,
#endif /* WINDOWS */
    .description = strtext("Release braille device when screen or window is unreadable.")
  },

  { .letter = 'T',
    .word = "tables-directory",
    .flags = OPT_Hidden | OPT_Config | OPT_Environ,
    .argument = strtext("directory"),
    .setting.string = &opt_tablesDirectory,
    .internal.setting = TABLES_DIRECTORY,
    .internal.adjust = fixInstallPath,
    .description = strtext("Path to directory containing tables.")
  },

  { .letter = 't',
    .word = "text-table",
    .bootParameter = 3,
    .flags = OPT_Config | OPT_Environ,
    .argument = strtext("file"),
    .setting.string = &opt_textTable,
    .internal.setting = optionOperand_autodetect,
    .description = strtext("Name of or path to text table (or %s)."),
    .strings.array = optionStrings_TextTable
  },

  { .letter = 'a',
    .word = "attributes-table",
    .flags = OPT_Config | OPT_Environ,
    .argument = strtext("file"),
    .setting.string = &opt_attributesTable,
    .description = strtext("Name of or path to attributes table.")
  },

#ifdef ENABLE_CONTRACTED_BRAILLE
  { .letter = 'c',
    .word = "contraction-table",
    .flags = OPT_Config | OPT_Environ,
    .argument = strtext("file"),
    .setting.string = &opt_contractionTable,
    .description = strtext("Name of or path to contraction table.")
  },
#endif /* ENABLE_CONTRACTED_BRAILLE */

  { .letter = 'k',
    .word = "keyboard-table",
    .flags = OPT_Config | OPT_Environ,
    .argument = strtext("file"),
    .setting.string = &opt_keyboardTable,
    .description = strtext("Name of or path to keyboard table.")
  },

  { .letter = 'K',
    .word = "keyboard-properties",
    .flags = OPT_Hidden | OPT_Extend | OPT_Config | OPT_Environ,
    .argument = strtext("name=value,..."),
    .setting.string = &opt_keyboardProperties,
    .description = strtext("Properties of eligible keyboards.")
  },

#ifdef ENABLE_SPEECH_SUPPORT
  { .letter = 's',
    .word = "speech-driver",
    .flags = OPT_Config | OPT_Environ,
    .argument = strtext("driver,..."),
    .setting.string = &opt_speechDriver,
    .internal.setting = optionOperand_autodetect,
    .description = strtext("Speech driver code (%s, %s, or one of {%s})."),
    .strings.array = optionStrings_SpeechDriver
  },

  { .letter = 'S',
    .word = "speech-parameters",
    .flags = OPT_Extend | OPT_Config | OPT_Environ,
    .argument = strtext("name=value,..."),
    .setting.string = &opt_speechParameters,
    .internal.setting = SPEECH_PARAMETERS,
    .description = strtext("Parameters for the speech driver.")
  },

  { .letter = 'i',
    .word = "speech-input",
    .flags = OPT_Hidden | OPT_Config | OPT_Environ,
    .argument = strtext("file"),
    .setting.string = &opt_speechInput,
    .description = strtext("Name of or path to speech input object.")
  },

  { .letter = 'Q',
    .word = "quiet-if-no-braille",
    .flags = OPT_Hidden | OPT_Config | OPT_Environ,
    .setting.flag = &opt_quietIfNoBraille,
    .description = strtext("Do not autospeak when braille is not being used.")
  },
#endif /* ENABLE_SPEECH_SUPPORT */

  { .letter = 'x',
    .word = "screen-driver",
    .flags = OPT_Config | OPT_Environ,
    .argument = strtext("driver,..."),
    .setting.string = &opt_screenDriver,
    .internal.setting = DEFAULT_SCREEN_DRIVER,
    .description = strtext("Screen driver code (%s, %s, or one of {%s})."),
    .strings.array = optionStrings_ScreenDriver
  },

  { .letter = 'X',
    .word = "screen-parameters",
    .flags = OPT_Extend | OPT_Config | OPT_Environ,
    .argument = strtext("name=value,..."),
    .setting.string = &opt_screenParameters,
    .internal.setting = SCREEN_PARAMETERS,
    .description = strtext("Parameters for the screen driver.")
  },

#ifdef HAVE_PCM_SUPPORT
  { .letter = 'p',
    .word = "pcm-device",
    .flags = OPT_Hidden | OPT_Config | OPT_Environ,
    .argument = strtext("device"),
    .setting.string = &opt_pcmDevice,
    .description = strtext("PCM (soundcard digital audio) device specifier.")
  },
#endif /* HAVE_PCM_SUPPORT */

#ifdef HAVE_MIDI_SUPPORT
  { .letter = 'm',
    .word = "midi-device",
    .flags = OPT_Hidden | OPT_Config | OPT_Environ,
    .argument = strtext("device"),
    .setting.string = &opt_midiDevice,
    .description = strtext("MIDI (Musical Instrument Digital Interface) device specifier.")
  },
#endif /* HAVE_MIDI_SUPPORT */

  { .letter = 'M',
    .word = "message-timeout",
    .flags = OPT_Hidden,
    .argument = strtext("csecs"),
    .setting.string = &opt_messageHoldTimeout,
    .description = strtext("Message hold timeout (in 10ms units).")
  },

  { .letter = 'e',
    .word = "standard-error",
    .flags = OPT_Hidden,
    .setting.flag = &opt_standardError,
    .description = strtext("Log to standard error rather than to the system log.")
  },

  { .letter = 'q',
    .word = "quiet",
    .setting.flag = &opt_quiet,
    .description = strtext("Suppress start-up messages.")
  },

  { .letter = 'l',
    .word = "log-level",
    .flags = OPT_Hidden | OPT_Config | OPT_Environ | OPT_Format,
    .argument = strtext("lvl|cat,..."),
    .setting.string = &opt_logLevel,
    .description = strtext("Logging level (%s or one of {%s}) and/or log categories to enable (any combination of {%s}, each optionally prefixed by %s to disable)"),
    .strings.format = formatLogLevelString
  },

  { .letter = 'L',
    .word = "log-file",
    .flags = OPT_Hidden | OPT_Config | OPT_Environ,
    .argument = strtext("file"),
    .setting.string = &opt_logFile,
    .description = strtext("Path to log file.")
  },

  { .letter = 'v',
    .word = "verify",
    .setting.flag = &opt_verify,
    .description = strtext("Write the start-up logs, and then exit.")
  },

  { .letter = 'V',
    .word = "version",
    .setting.flag = &opt_version,
    .description = strtext("Log the versions of the core, API, and built-in drivers, and then exit.")
  },
END_OPTION_TABLE

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
  if (*value) {
    if (variable) setGlobalVariable(variable, value);
  } else {
    value = gettext("none");
  }

  logMessage(LOG_INFO, "%s: %s", label, value);
}

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

ProgramExitStatus
brlttyPrepare (int argc, char *argv[]) {
  {
    static const OptionsDescriptor descriptor = {
      OPTION_TABLE(programOptions),
      .doBootParameters = &opt_bootParameters,
      .doEnvironmentVariables = &opt_environmentVariables,
      .configurationFile = &opt_configurationFile,
      .applicationName = "brltty"
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

  setUpdatableDirectory(opt_updatableDirectory);
  setWritableDirectory(opt_writableDirectory);

  setLogLevels();
  onProgramExit("log", exitLog, NULL);

  if (*opt_logFile) {
    openLogFile(opt_logFile);
  } else {
    openSystemLog();
  }

  logProgramBanner();
  logProperty(opt_logLevel, "logLevel", gettext("Log Level"));

  return PROG_EXIT_SUCCESS;
}

int
changeTextTable (const char *name) {
  return replaceTextTable(opt_tablesDirectory, name);
}

static void
exitTextTable (void *data) {
  changeTextTable(NULL);
}

int
changeAttributesTable (const char *name) {
  return replaceAttributesTable(opt_tablesDirectory, name);
}

static void
exitAttributesTable (void *data) {
  changeAttributesTable(NULL);
}

#ifdef ENABLE_CONTRACTED_BRAILLE
static void
exitContractionTable (void *data) {
  if (contractionTable) {
    destroyContractionTable(contractionTable);
    contractionTable = NULL;
  }
}

int
changeContractionTable (const char *name) {
  ContractionTable *table = NULL;

  if (*name) {
    char *path;

    if ((path = makeContractionTablePath(opt_tablesDirectory, name))) {
      logMessage(LOG_DEBUG, "compiling contraction table: %s", path);

      if (!(table = compileContractionTable(path))) {
        logMessage(LOG_ERR, "%s: %s", gettext("cannot compile contraction table"), path);
      }

      free(path);
    }

    if (!table) return 0;
  }

  if (contractionTable) destroyContractionTable(contractionTable);
  contractionTable = table;
  return 1;
}
#endif /* ENABLE_CONTRACTED_BRAILLE */

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

/*
static void
disableKeyboardMonitor (void) {
  ActivityObject *activity = getKeyboardMonitorActivity(0);

  if (activity) stopActivity(activity);
}
*/

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
handleUtf8HelpLine (char *line, void *data) {
  const char *utf8 = line;
  size_t count = strlen(utf8) + 1;
  wchar_t buffer[count];
  wchar_t *characters = buffer;

  convertUtf8ToWchars(&utf8, &characters, count);
  return handleWcharHelpLine(buffer, data);
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
    destroyKeyTable(keyboardTable);
    disableKeyboardHelpPage();
  }

  if ((keyboardTable = table)) {
    enableKeyboardMonitor();

    setKeyTableLogLabel(keyboardTable, "kbd");
    setLogKeyEventsFlag(keyboardTable, &LOG_CATEGORY_FLAG(KEYBOARD_KEYS));

    makeKeyboardHelpPage();
  }

  return 1;
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

  logMessage(LOG_DEBUG, "regions: text=%u.%u status=%u.%u",
             textStart, textCount, statusStart, statusCount);

  fullWindowShift = MAX(textCount-prefs.brailleWindowOverlap, 1);
  halfWindowShift = textCount / 2;
  verticalWindowShift = (rows > 1)? rows: 5;
  logMessage(LOG_DEBUG, "shifts: full=%u half=%u vertical=%u",
             fullWindowShift, halfWindowShift, verticalWindowShift);
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

  setBrailleAutorepeat(&brl, prefs.autorepeatEnabled,
                       PREFERENCES_TIME(prefs.longPressTime),
                       PREFERENCES_TIME(prefs.autorepeatInterval));

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
setPreferences (const Preferences *newPreferences) {
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
      sfWindowCoordinates, sfCursorCoordinates, sfEnd
    };

    static const unsigned char fields5[] = {
      sfWindowCoordinates, sfCursorCoordinates, sfStateDots, sfEnd
    };

    static const unsigned char fields6[] = {
      sfWindowCoordinates, sfCursorCoordinates, sfStateDots, sfScreenNumber,
      sfEnd
    };

    static const unsigned char fields7[] = {
      sfWindowCoordinates, sfCursorCoordinates, sfStateDots, sfTime,
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
  char **settings = splitString(opt_preferenceOverrides, PARAMETER_SEPARATOR_CHARACTER, &count);

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

int
loadPreferences (void) {
  int ok = 0;

  {
    char *path = makePreferencesFilePath(opt_preferencesFile);

    if (path) {
      if (testFilePath(path)) {
        oldPreferencesEnabled = 0;
        if (loadPreferencesFile(path)) ok = 1;
      }

      free(path);
    }
  }

  if (oldPreferencesEnabled && oldPreferencesFile) {
    if (loadPreferencesFile(oldPreferencesFile)) ok = 1;
  }

  if (!ok) resetPreferences();
  setPreferenceOverrides();
  applyAllPreferences();
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

  if (!ok) message(NULL, gettext("not saved"), 0);
  return ok;
}

#ifdef ENABLE_API
static void
exitApiServer (void *data) {
  if (api.isLinked()) api.unlink();
  if (api.isStarted()) api.stop();

  if (apiParameters) {
    deallocateStrings(apiParameters);
    apiParameters = NULL;
  }
}
#endif /* ENABLE_API */

static void
startApiServer (void) {
#ifdef ENABLE_API
  if (!(opt_noApi || api.isStarted())) {
    const char *const *parameters = api.getParameters();

    apiParameters = getParameters(parameters,
                                  NULL,
                                  opt_apiParameters);

    if (apiParameters) {
      api.identify(0);
      logParameters(parameters, apiParameters,
                    gettext("API Parameter"));

      if (!opt_verify) {
        if (api.start(apiParameters)) {
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
    static const char *const fallbackDrivers[] = {optionOperand_none, NULL};
    driver = fallbackDrivers;
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
  brl.api = &api;
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

          makeBrailleHelpPage(keyTablePath);
          free(keyTablePath);
        }
      }

      report(REPORT_BRAILLE_DEVICE_ONLINE, NULL);
      startBrailleInput();

      brailleConstructed = 1;
      return 1;
    }

    braille->destruct(&brl);
  } else {
    logMessage(LOG_DEBUG, "%s: %s -> %s",
               gettext("braille driver initialization failed"),
               braille->definition.code, brailleDevice);
  }

  return 0;
}

void
destructBrailleDriver (void) {
  stopBrailleInput();
  drainBrailleOutput(&brl, 0);
  report(REPORT_BRAILLE_DEVICE_OFFLINE, NULL);

  brailleConstructed = 0;
  braille->destruct(&brl);

  disableBrailleHelpPage();
  destructBrailleDisplay(&brl);
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
        logMessage(LOG_INFO, "%s: %s [%s]",
                   gettext("Braille Driver"), braille->definition.code, braille->definition.name);
        identifyBrailleDriver(braille, 0);
        logParameters(braille->parameters, brailleDriverParameters,
                      gettext("Braille Parameter"));
        logMessage(LOG_INFO, "%s: %s", gettext("Braille Device"), brailleDevice);

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

          api.link();

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
            static const char *const serialDrivers[] = {
              "md", "pm", "ts", "ht", "bn", "al", "bm", "pg", "sk",
              NULL
            };

            autodetectableDrivers = serialDrivers;
            break;
          }

          case GIO_TYPE_USB: {
            static const char *const usbDrivers[] = {
              "al", "bm", "bn", "eu", "fs", "hd", "hm", "ht", "hw", "ic", "mt", "pg", "pm", "sk", "vo",
              NULL
            };

            autodetectableDrivers = usbDrivers;
            break;
          }

          case GIO_TYPE_BLUETOOTH: {
            if (!(autodetectableDrivers = bthGetDriverCodes(dev, BLUETOOTH_DEVICE_NAME_OBTAIN_TIMEOUT))) {
              static const char *bluetoothDrivers[] = {
                "np", "ht", "al", "bm",
                NULL
              };

              autodetectableDrivers = bluetoothDrivers;
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
    api.unlink();
    if (brailleConstructed) destructBrailleDriver();
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
      if (!loadPreferencesFile(oldPreferencesFile)) resetPreferences();
      setPreferenceOverrides();
      applyAllPreferences();
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

        if (!*text) {
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
  if (brailleConstructed) {
    const char *text = opt_stopMessage;
    if (!*text) text = gettext("BRLTTY stopped");
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
  char **newDrivers = splitString(driver, ',', NULL);

  if (newDrivers) {
    char **oldDrivers = brailleDrivers;

    brailleDrivers = newDrivers;
    if (oldDrivers) deallocateStrings(oldDrivers);
    return 1;
  }

  return 0;
}

int
changeBrailleParameters (const char *parameters) {
  char *newParameters;

  if (!parameters) parameters = "";

  if ((newParameters = strdup(parameters))) {
    char *oldParameters = brailleParameters;

    brailleParameters = newParameters;
    if (oldParameters) free(oldParameters);
    return 1;
  } else {
    logMallocError();
  }

  return 0;
}
int
changeBrailleDevice (const char *device) {
  char **newDevices = splitString(device, ',', NULL);

  if (newDevices) {
    char **oldDevices = brailleDevices;

    brailleDevices = newDevices;
    if (oldDevices) deallocateStrings(oldDevices);
    return 1;
  }

  return 0;
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
endAutospeakDelay (volatile SpeechSynthesizer *spk) {
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
setSpeechFinished (volatile SpeechSynthesizer *spk) {
  spk->track.isActive = 0;
  spk->track.speechLocation = SPK_LOC_NONE;

  endAutospeakDelay(spk);
}

static void
setSpeechLocation (volatile SpeechSynthesizer *spk, int location) {
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
        logMessage(LOG_INFO, "%s: %s [%s]",
                   gettext("Speech Driver"), speech->definition.code, speech->definition.name);
        identifySpeechDriver(speech, 0);
        logParameters(speech->parameters, speechDriverParameters,
                      gettext("Speech Parameter"));

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

    if (!*text) {
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
  char **newDrivers = splitString(driver, ',', NULL);

  if (newDrivers) {
    char **oldDrivers = speechDrivers;

    speechDrivers = newDrivers;
    if (oldDrivers) deallocateStrings(oldDrivers);
    return 1;
  }

  return 0;
}

int
changeSpeechParameters (const char *parameters) {
  char *newParameters;

  if (!parameters) parameters = "";

  if ((newParameters = strdup(parameters))) {
    char *oldParameters = speechParameters;

    speechParameters = newParameters;
    if (oldParameters) free(oldParameters);
    return 1;
  } else {
    logMallocError();
  }

  return 0;
}
#endif /* ENABLE_SPEECH_SUPPORT */

static int
initializeScreenDriver (const char *code, int verify) {
  if ((screen = loadScreenDriver(code, &screenObject, opt_driversDirectory))) {
    screenDriverParameters = getParameters(getScreenParameters(screen),
                                           getScreenDriverDefinition(screen)->code,
                                           screenParameters);

    if (screenDriverParameters) {
      int constructed = verify;

      if (!constructed) {
        logMessage(LOG_DEBUG, "initializing screen driver: %s",
                   getScreenDriverDefinition(screen)->code);

        if (constructScreenDriver(screenDriverParameters)) {
          constructed = 1;
          screenDriver = screen;
        }
      }

      if (constructed) {
        logMessage(LOG_INFO, "%s: %s [%s]",
                   gettext("Screen Driver"),
                   getScreenDriverDefinition(screen)->code,
                   getScreenDriverDefinition(screen)->name);
        identifyScreenDriver(screen, 0);
        logParameters(getScreenParameters(screen),
                      screenDriverParameters,
                      gettext("Screen Parameter"));

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
  char **newDrivers = splitString(driver, ',', NULL);

  if (newDrivers) {
    char **oldDrivers = screenDrivers;

    screenDrivers = newDrivers;
    if (oldDrivers) deallocateStrings(oldDrivers);
    return 1;
  }

  return 0;
}

int
changeScreenParameters (const char *parameters) {
  char *newParameters;

  if (!parameters) parameters = "";

  if ((newParameters = strdup(parameters))) {
    char *oldParameters = screenParameters;

    screenParameters = newParameters;
    if (oldParameters) free(oldParameters);
    return 1;
  } else {
    logMallocError();
  }

  return 0;
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

static void
detachStream (FILE *stream, const char *name, int output) {
  const char *nullDevice = "/dev/null";

  if (!freopen(nullDevice, (output? "a": "r"), stream)) {
    if (errno != ENOENT) {
      char action[0X40];

      snprintf(action, sizeof(action), "freopen[%s]", name);
      logSystemError(action);
    }
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
      installService(SERVICE_NAME, SERVICE_DESCRIPTION);
      stop = 1;
    }

    if (stop) return PROG_EXIT_FORCE;
  }

  if (!validateInterval(&messageHoldTimeout, opt_messageHoldTimeout)) {
    logMessage(LOG_ERR, "%s: %s", gettext("invalid message hold timeout"), opt_messageHoldTimeout);
  }

  if (opt_version) {
    logMessage(LOG_INFO, "Copyright %s", PACKAGE_COPYRIGHT);
    identifyScreenDrivers(1);

#ifdef ENABLE_API
    api.identify(1);
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
    fflush(stdout);
    fflush(stderr);
    stderrLogLevel = 0;

#if defined(GRUB_RUNTIME)

#else /* redirect stdio streams to /dev/null */
    detachStream(stdin, "stdin", 0);
    detachStream(stdout, "stdout", 1);
    if (!opt_standardError) detachStream(stderr, "stderr", 1);
#endif /* redirect stdio streams to /dev/null */

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
      logProperty(directory, "workingDirectory", gettext("Working Directory"));
      free(directory);
    } else {
      logMessage(LOG_WARNING, "%s: %s", gettext("cannot determine working directory"), strerror(errno));
    }
  }

  logProperty(opt_configurationFile, "configurationFile", gettext("Configuration File"));
  logProperty(opt_preferencesFile, "preferencesFile", gettext("Preferences File"));

  constructBrailleDisplay(&brl);
  loadPreferences();

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

  logProperty(opt_updatableDirectory, "updatableDirectory", gettext("Updatable Directory"));
  logProperty(opt_writableDirectory, "writableDirectory", gettext("Writable Directory"));
  logProperty(opt_driversDirectory, "driversDirectory", gettext("Drivers Directory"));
  logProperty(opt_tablesDirectory, "tablesDirectory", gettext("Tables Directory"));

  /* handle text table option */
  if (*opt_textTable) {
    if (strcmp(opt_textTable, optionOperand_autodetect) == 0) {
      char *name = selectTextTable(opt_tablesDirectory);

      changeStringSetting(&opt_textTable, "");

      if (name) {
        if (replaceTextTable(opt_tablesDirectory, name)) {
          changeStringSetting(&opt_textTable, name);
        }

        free(name);
      }
    } else if (!replaceTextTable(opt_tablesDirectory, opt_textTable)) {
      changeStringSetting(&opt_textTable, "");
    }
  }

  if (!*opt_textTable) {
    changeStringSetting(&opt_textTable, TEXT_TABLE);
  }

  logProperty(opt_textTable, "textTable", gettext("Text Table"));
  onProgramExit("text-table", exitTextTable, NULL);

  /* handle attributes table option */
  if (*opt_attributesTable) {
    if (!replaceAttributesTable(opt_tablesDirectory, opt_attributesTable)) {
      changeStringSetting(&opt_attributesTable, "");
    }
  }

  if (!*opt_attributesTable) {
    changeStringSetting(&opt_attributesTable, ATTRIBUTES_TABLE);
  }

  logProperty(opt_attributesTable, "attributesTable", gettext("Attributes Table"));
  onProgramExit("attributes-table", exitAttributesTable, NULL);

#ifdef ENABLE_CONTRACTED_BRAILLE
  /* handle contraction table option */
  onProgramExit("contraction-table", exitContractionTable, NULL);
  if (*opt_contractionTable) changeContractionTable(opt_contractionTable);
  logProperty(opt_contractionTable, "contractionTable", gettext("Contraction Table"));
#endif /* ENABLE_CONTRACTED_BRAILLE */

  parseKeyboardProperties(&keyboardProperties, opt_keyboardProperties);

  onProgramExit("keyboard-table", exitKeyboardTable, NULL);
  changeKeyboardTable(opt_keyboardTable);
  logProperty(opt_keyboardTable, "keyboardTable", gettext("Keyboard Table"));

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

  changeBrailleDriver(opt_brailleDriver);
  changeBrailleParameters(opt_brailleParameters);
  changeBrailleDevice(opt_brailleDevice);
  brailleConstructed = 0;
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
  logProperty(opt_speechInput, "speechInput", gettext("Speech Input"));
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
  if (setlocale(LC_ALL, locale)) return 1;
  logSystemError("setlocale");
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

#ifdef ENABLE_CONTRACTED_BRAILLE
  { .name = WS_C("contraction-table"),
    .defaultValue = &opt_contractionTable,
    .change = changeContractionTable
  },
#endif /* ENABLE_CONTRACTED_BRAILLE */
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
