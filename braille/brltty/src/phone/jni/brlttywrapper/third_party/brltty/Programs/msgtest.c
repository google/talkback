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
#include <errno.h>

#include "log.h"
#include "program.h"
#include "cmdline.h"
#include "messages.h"
#include "parse.h"
#include "file.h"

static char *opt_localeDirectory;
static char *opt_localeSpecifier;
static char *opt_domainName;

static FILE *outputStream;
static int opt_utf8Output;

BEGIN_OPTION_TABLE(programOptions)
  { .word = "directory",
    .letter = 'd',
    .argument = strtext("path"),
    .setting.string = &opt_localeDirectory,
    .internal.adjust = fixInstallPath,
    .description = strtext("the locale directory containing the translations")
  },

  { .word = "locale",
    .letter = 'l',
    .argument = strtext("specifier"),
    .setting.string = &opt_localeSpecifier,
    .description = strtext("the locale in which to look up a translation")
  },

  { .word = "domain",
    .letter = 'n',
    .argument = strtext("name"),
    .setting.string = &opt_domainName,
    .description = strtext("the name of the domain containing the translations")
  },

  { .word = "utf8",
    .letter = 'u',
    .setting.flag = &opt_utf8Output,
    .description = strtext("write the translations using UTF-8")
  },
END_OPTION_TABLE(programOptions)

static int
noOutputErrorYet (void) {
  if (!ferror(outputStream)) return 1;
  logMessage(LOG_ERR, "output error: %s", strerror(errno));
  return 0;
}

static int
putCharacter (char c) {
  fputc(c, outputStream);
  return noOutputErrorYet();
}

static int
putNewline (void) {
  return putCharacter('\n');
}

static int
putBytes (const char *bytes, size_t count) {
  while (count) {
    uint32_t last = count - 1;
    if (bytes[last] != '\n') break;
    count = last;
  }

  if (opt_utf8Output) {
    fwrite(bytes, 1, count, outputStream);
  } else {
    writeWithConsoleEncoding(outputStream, bytes, count);
  }

  return noOutputErrorYet();
}

static int
putString (const char *string) {
  return putBytes(string, strlen(string));
}

static int
putMessage (const Message *message) {
  return putBytes(getMessageText(message), getMessageLength(message));
}

static int
listTranslation (const Message *source, const Message *translation) {
  return putMessage(source)
      && putString(" -> ")
      && putMessage(translation)
      && putNewline();
}

static int
listAllTranslations (void) {
  uint32_t count = getMessageCount();

  for (unsigned int index=0; index<count; index+=1) {
    const Message *source = getSourceMessage(index);
    if (getMessageLength(source) == 0) continue;

    const Message *translation = getTranslatedMessage(index);
    if (!listTranslation(source, translation)) return 0;
  }

  return 1;
}

static int
showSimpleTranslation (const char *text) {
  {
    unsigned int index;

    if (findSourceMessage(text, strlen(text), &index)) {
      return putMessage(getTranslatedMessage(index)) && putNewline();
    }
  }

  logMessage(LOG_WARNING, "translation not found: %s", text);
  return 0;
}

static int
showPluralTranslation (const char *singular, const char *plural, int count) {
  const char *translation = getPluralTranslation(singular, plural, count);
  return putString(translation) && putNewline();
}

static int
showProperty (const char *propertyName, const char *attributeName) {
  int ok = 0;
  char *propertyValue = getMessagesProperty(propertyName);

  if (propertyValue) {
    if (!attributeName) {
      fprintf(outputStream, "%s\n", propertyValue);
      ok = noOutputErrorYet();
    } else {
      char *attributeValue = getMessagesAttribute(propertyValue, attributeName);

      if (attributeValue) {
        fprintf(outputStream, "%s\n", attributeValue);
        ok = noOutputErrorYet();
        free(attributeValue);
      } else {
        logMessage(LOG_WARNING,
          "attribute not defined: %s: %s",
          propertyName, attributeName
        );
      }
    }

    free(propertyValue);
  } else {
    logMessage(LOG_WARNING, "property not defined: %s", propertyName);
  }

  return ok;
}

static int
parseQuantity (int *count, const char *quantity) {
  static const int minimum = 0;
  static const int maximum = 999999999;

  if (validateInteger(count, quantity, &minimum, &maximum)) return 1;
  logMessage(LOG_ERR, "invalid quantity: %s", quantity);
  return 0;
}

static const char *
nextParameter (char ***argv, int *argc, const char *description) {
  if (*argc) {
    *argc -= 1;
    return *(*argv)++;
  }

  if (!description) return NULL;
  logMessage(LOG_ERR, "missing %s", description);
  exit(PROG_EXIT_SYNTAX);
}

static void
noMoreParameters (char ***argv, int *argc) {
  if (*argc) {
    logMessage(LOG_ERR, "too many parameters");
    exit(PROG_EXIT_SYNTAX);
  }
}

static void
beginAction (char ***argv, int *argc) {
  noMoreParameters(argv, argc);

  {
    const char *directory = opt_localeDirectory;

    if (*directory) {
      if (!testDirectoryPath(directory)) {
        logMessage(LOG_WARNING, "not a directory: %s", directory);
        exit(PROG_EXIT_SEMANTIC);
      }

      setMessagesDirectory(directory);
    }
  }

  if (*opt_localeSpecifier) setMessagesLocale(opt_localeSpecifier);
  if (*opt_domainName) setMessagesDomain(opt_domainName);
  if (!loadMessageCatalog()) exit(PROG_EXIT_FATAL);
}

int
main (int argc, char *argv[]) {
  outputStream = stdout;

  {
    const CommandLineDescriptor descriptor = {
      .options = &programOptions,
      .applicationName = "msgtest",

      .usage = {
        .purpose = strtext("Test message localization using the message catalog reader."),
        .parameters = "action [argument ...]",
      }
    };

    PROCESS_OPTIONS(descriptor, argc, argv);
  }

  if (!argc) {
    logMessage(LOG_ERR, "missing action");
    exit(PROG_EXIT_SYNTAX);
  }

  const char *action = *argv++;
  argc -= 1;
  int ok = 1;

  if (isAbbreviation("translation", action)) {
    const char *message = nextParameter(&argv, &argc, "message");
    const char *plural = nextParameter(&argv, &argc, NULL);

    if (plural) {
      const char *quantity = nextParameter(&argv, &argc, "quantity");

      int count;
      if (!parseQuantity(&count, quantity)) return PROG_EXIT_SYNTAX;

      beginAction(&argv, &argc);
      ok = showPluralTranslation(message, plural, count);
    } else {
      beginAction(&argv, &argc);
      ok = showSimpleTranslation(message);
    }
  } else if (isAbbreviation("count", action)) {
    beginAction(&argv, &argc);
    fprintf(outputStream, "%u\n", getMessageCount());
    ok = noOutputErrorYet();
  } else if (isAbbreviation("all", action)) {
    beginAction(&argv, &argc);
    ok = listAllTranslations();
  } else if (isAbbreviation("metadata", action)) {
    beginAction(&argv, &argc);
    fprintf(outputStream, "%s\n", getMessagesMetadata());
    ok = noOutputErrorYet();
  } else if (isAbbreviation("property", action)) {
    const char *property = nextParameter(&argv, &argc, "property name");
    const char *attribute = nextParameter(&argv, &argc, NULL);

    beginAction(&argv, &argc);
    ok = showProperty(property, attribute);
  } else {
    logMessage(LOG_ERR, "unknown action: %s", action);
    return PROG_EXIT_SYNTAX;
  }

  if (ferror(outputStream)) return PROG_EXIT_FATAL;
  return ok? PROG_EXIT_SUCCESS: PROG_EXIT_SEMANTIC;
}
