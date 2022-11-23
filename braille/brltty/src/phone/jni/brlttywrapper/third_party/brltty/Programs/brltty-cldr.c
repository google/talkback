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
#include <errno.h>

#include "program.h"
#include "options.h"
#include "log.h"
#include "cldr.h"
#include "datafile.h"
#include "charset.h"

#define DEFAULT_OUTPUT_FORMAT "%s\\t%n\\n"

static char *opt_outputFormat;

BEGIN_OPTION_TABLE(programOptions)
  { .letter = 'f',
    .word = "output-format",
    .argument = strtext("string"),
    .setting.string = &opt_outputFormat,
    .internal.setting = DEFAULT_OUTPUT_FORMAT,
    .description = strtext("Format of each output line.")
  },
END_OPTION_TABLE

static void
onFormatError (void) {
  exit(PROG_EXIT_SYNTAX);
}

static void
onMissingCharacter (const char *type) {
  logMessage(LOG_ERR, "missing %s character", type);
  onFormatError();
}

static void
onUnrecognizedCharacter (const char *type, int byte) {
  logMessage(LOG_ERR, "unrecognized %s character: %c", type, byte);
  onFormatError();
}

static void
onOutputError (void) {
  logMessage(LOG_ERR, "output error %d: %s", errno, strerror(errno));
  exit(PROG_EXIT_FATAL);
}

static void
putByte (int byte) {
  if (fputc(byte, stdout) == EOF) onOutputError();
}

static void
putString (const char *string) {
  if (fputs(string, stdout) == EOF) onOutputError();
}

static void
putHexadecimal (const char *string) {
  size_t size = strlen(string) + 1;
  wchar_t characters[size];

  const char *byte = string;
  wchar_t *character = characters;
  wchar_t *end = character;
  convertUtf8ToWchars(&byte, &end, size);

  while (character < end) {
    if (writeHexadecimalCharacter(stdout, *character) == EOF) onOutputError();
    character += 1;
  }
}

static
CLDR_ANNOTATION_HANDLER(handleAnnotation) {
  typedef enum {LITERAL, FORMAT, ESCAPE} State;
  State state = LITERAL;
  const char *format = opt_outputFormat;

  while (*format) {
    int byte = *format & 0XFF;

    switch (state) {
      case LITERAL: {
        switch (byte) {
          case '%':
            state = FORMAT;
            break;

          case '\\':
            state = ESCAPE;
            break;

          default:
            putByte(byte);
            break;
        }

        break;
      }

      case FORMAT: {
        switch (byte) {
          case 'n':
            putString(parameters->name);
            break;

          case 's':
            putString(parameters->sequence);
            break;

          case 'x':
            putHexadecimal(parameters->sequence);
            break;

          case '%':
            putByte(byte);
            break;

          default:
            onUnrecognizedCharacter("format", byte);
            return 0;
        }

        state = LITERAL;
        break;
      }

      case ESCAPE: {
        static const char escapes[] = {
          ['a'] = '\a',
          ['b'] = '\b',
          ['e'] = '\e',
          ['f'] = '\f',
          ['n'] = '\n',
          ['r'] = '\r',
          ['t'] = '\t',
          ['v'] = '\v',
          ['\\'] = '\\'
        };

        switch (byte) {
          default: {
            if (byte < ARRAY_COUNT(escapes)) {
              char escape = escapes[byte];

              if (escape) {
                putByte(escape);
                break;
              }
            }

            onUnrecognizedCharacter("escape", byte);
            return 0;
          }
        }

        state = LITERAL;
        break;
      }
    }

    format += 1;
  }

  switch (state) {
    case LITERAL:
      return 1;

    case FORMAT:
      onMissingCharacter("format");
      break;

    case ESCAPE:
      onMissingCharacter("escape");
      break;
  }

  return 0;
}

int
main (int argc, char *argv[]) {
  {
    static const OptionsDescriptor descriptor = {
      OPTION_TABLE(programOptions),
      .applicationName = "brltty-cldr",
      .argumentsSummary = "input-file"
    };
    PROCESS_OPTIONS(descriptor, argc, argv);
  }

  if (argc < 1) {
    logMessage(LOG_ERR, "missing annotations file name");
    return PROG_EXIT_SYNTAX;
  }

  const char *inputFile = *argv++;
  argc -= 1;

  if (argc > 0) {
    logMessage(LOG_ERR, "too many parameters");
    return PROG_EXIT_SYNTAX;
  }

  return cldrParseFile(inputFile, handleAnnotation, NULL)?
         PROG_EXIT_SUCCESS:
         PROG_EXIT_FATAL;
}
