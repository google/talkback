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
#include <ctype.h>
#include <errno.h>

#include "log.h"
#include "strfmt.h"
#include "program.h"
#include "cmdline.h"
#include "file.h"
#include "get_select.h"
#include "brl_dots.h"
#include "charset.h"
#include "ttb.h"
#include "ttb_internal.h"
#include "ttb_compile.h"

#undef HAVE_UNDEFINED_CHARACTERS_SUPPORT
#if defined(__linux__)
#define HAVE_UNDEFINED_CHARACTERS_SUPPORT

#include <fcntl.h>
#include <sys/stat.h>
#include <sys/ioctl.h>
#include <linux/kd.h>

static void
showUndefinedCharacters (TextTableData *ttd) {
  char path[0X40];
  int console;

  {
    const char *vt = getenv("XDG_VTNR");

    if (!(vt && *vt)) vt = "0";
    snprintf(path, sizeof(path), "/dev/tty%s", vt);
  }

  if ((console = open(path, O_RDONLY)) != -1) {
    struct unimapdesc sfm;
    unsigned short size = 0X100;

    while (1) {
      sfm.entry_ct = size;

      if (!(sfm.entries = malloc(ARRAY_SIZE(sfm.entries, sfm.entry_ct)))) {
        logMallocError();
        break;
      }

      if (ioctl(console, GIO_UNIMAP, &sfm) != -1) break;
      free(sfm.entries);
      sfm.entries = NULL;

      if (errno != ENOMEM) {
        logSystemError("ioctl[GIO_UNIMAP]");
        break;
      }

      if (!(size <<= 1)) {
        logMessage(LOG_ERR, "screen font map too big");
        break;
      }
    }

    if (sfm.entries) {
      const struct unipair *entry = sfm.entries;
      const struct unipair *end = entry + sfm.entry_ct;

      while (entry < end) {
        wchar_t character = entry++->unicode;

        if (character == UNICODE_REPLACEMENT_CHARACTER) continue;
        if ((character & ~UNICODE_CELL_MASK) == UNICODE_BRAILLE_ROW) continue;

        if (!getUnicodeCell(ttd, character)) {
          const TextTableHeader *header = getTextTableHeader(ttd);

          const TextTableAliasEntry *alias = locateTextTableAlias(
            character, getTextTableItem(ttd, header->aliasArray), header->aliasCount
          );

          if (!alias) {
            char buffer[0X80];

            STR_BEGIN(buffer, sizeof(buffer));
            STR_PRINTF("undefined character: U+%04"PRIX32, (uint32_t)character);

            {
              char name[0X40];

              if (getCharacterName(character, name, sizeof(name))) {
                STR_PRINTF(" [%s]", name);
              }
            }

            STR_END;
            logMessage(LOG_WARNING, "%s", buffer);
          }
        }
      }

      free(sfm.entries);
    }

    close(console);
  } else {
    logMessage(LOG_ERR, "cannot open console: %s: %s", path, strerror(errno));
  }
}

#endif /* undefined characters support */

static char *opt_charset;
static char *opt_inputFormat;
static char *opt_outputFormat;
static char *opt_tablesDirectory;
static int opt_edit;

#ifdef HAVE_UNDEFINED_CHARACTERS_SUPPORT
static int opt_undefined;
#endif /* HAVE_UNDEFINED_CHARACTERS_SUPPORT */

BEGIN_OPTION_TABLE(programOptions)
  { .word = "edit",
    .letter = 'e',
    .setting.flag = &opt_edit,
    .description = strtext("Edit table.")
  },

  { .word = "input-format",
    .letter = 'i',
    .argument = "format",
    .setting.string = &opt_inputFormat,
    .description = strtext("Format of input file.")
  },

  { .word = "output-format",
    .letter = 'o',
    .argument = "format",
    .setting.string = &opt_outputFormat,
    .description = strtext("Format of output file.")
  },

  { .word = "charset",
    .letter = 'c',
    .argument = "charset",
    .setting.string = &opt_charset,
    .description = strtext("8-bit character set to use.")
  },

#ifdef HAVE_UNDEFINED_CHARACTERS_SUPPORT
  { .word = "undefined",
    .letter = 'u',
    .setting.flag = &opt_undefined,
    .description = strtext("Report the characters within the current screen font that aren't defined within the text table.")
  },
#endif /* HAVE_UNDEFINED_CHARACTERS_SUPPORT */

  { .word = "tables-directory",
    .letter = 'T',
    .argument = "directory",
    .setting.string = &opt_tablesDirectory,
    .internal.setting = TABLES_DIRECTORY,
    .internal.adjust = fixInstallPath,
    .description = strtext("Path to directory containing text tables.")
  },
END_OPTION_TABLE(programOptions)

static const BrlDotTable dotsInternal = {
  BRL_DOT_1, BRL_DOT_2, BRL_DOT_3, BRL_DOT_4,
  BRL_DOT_5, BRL_DOT_6, BRL_DOT_7, BRL_DOT_8
};

static const BrlDotTable dots12345678 = {
  0X01, 0X02, 0X04, 0X08, 0X10, 0X20, 0X40, 0X80
};

static const BrlDotTable dots14253678 = {
  0X01, 0X04, 0X10, 0X02, 0X08, 0X20, 0X40, 0X80
};

static unsigned char
mapDots (unsigned char input, const BrlDotTable from, const BrlDotTable to) {
  unsigned char output = 0;
  {
    for (int dot=0; dot<BRL_DOT_COUNT; dot+=1) {
      if (input & from[dot]) output |= to[dot];
    }
  }
  return output;
}

typedef TextTableData *TableReader (const char *path, FILE *file, const void *data);
typedef int TableWriter (const char *path, FILE *file, TextTableData *ttd, const void *data);
typedef int CharacterWriter (FILE *file, const char *directive, wchar_t character, unsigned char dots);
typedef int AliasWriter (FILE *file, const TextTableAliasEntry *alias, const void *data);

static int
getDots (TextTableData *ttd, wchar_t character, unsigned char *dots) {
  const unsigned char *cell = getUnicodeCell(ttd, character);
  if (!cell) return 0;
  *dots = *cell;
  return 1;
}

static int
writeString (FILE *file, const char *string) {
  if (fputs(string, file) != EOF) return 1;
  logSystemError("output");
  return 0;
}

static int
endLine (FILE *file) {
  return writeString(file, "\n");
}

typedef int CommentWriter (FILE *file, const char *text);

static int
writeHashComment (FILE *file, const char *text) {
  if (!writeString(file, "# ")) return 0;
  if (!writeString(file, text)) return 0;
  if (!endLine(file)) return 0;
  return 1;
}

static int
writeSemicolonComment (FILE *file, const char *text) {
  if (!writeString(file, "; ")) return 0;
  if (!writeString(file, text)) return 0;
  if (!endLine(file)) return 0;
  return 1;
}

static int
writeCComment (FILE *file, const char *text) {
  if (!writeString(file, "/* ")) return 0;
  if (!writeString(file, text)) return 0;
  if (!writeString(file, " */")) return 0;
  if (!endLine(file)) return 0;
  return 1;
}

static int
defineCMacro (FILE *file, const char *name, const char *args) {
  if (!writeString(file, "#ifndef ")) return 0;
  if (!writeString(file, name)) return 0;
  if (!endLine(file)) return 0;

  if (!writeString(file, "#define ")) return 0;
  if (!writeString(file, name)) return 0;
  if (!writeString(file, args)) return 0;
  if (!endLine(file)) return 0;

  if (!writeString(file, "#endif")) return 0;
  if (!endLine(file)) return 0;

  if (!endLine(file)) return 0;
  return 1;
}

static int
beginCMacro (FILE *file, const char *name) {
  if (!writeString(file, name)) return 0;
  if (!writeString(file, "(")) return 0;
  return 1;
}

static int
endCMacro (FILE *file) {
  if (!writeString(file, ")")) return 0;
  return 1;
}

static int
nextCArgument (FILE *file) {
  return fprintf(file, ", ") != EOF;
}

static int
writeHeaderComment (FILE *file, CommentWriter *writeComment) {
  char buffer[0X80];

  STR_BEGIN(buffer, sizeof(buffer));
  STR_PRINTF("generated by %s", programName);
  if (*opt_charset) STR_PRINTF(": charset=%s", opt_charset);
  STR_END;

  return writeComment(file, buffer);
}

static int
writeCharacterDescription (FILE *file, wchar_t character) {
  if (!writeString(file, " ")) return 0;
  if (!writeUtf8Character(file, ((iswprint(character) && !iswspace(character))? character: WC_C(' ')))) return 0;

  {
    char name[0X40];

    if (getCharacterName(character, name, sizeof(name))) {
      if (!writeString(file, " [")) return 0;
      if (!writeString(file, name)) return 0;
      if (!writeString(file, "]")) return 0;
    }
  }

  return 1;
}

typedef struct {
  wchar_t character;
  unsigned char dots;
} InputCharacterEntry;

static int
sortInputCharacterEntries (const void *element1, const void *element2) {
  const InputCharacterEntry *entry1 = element1;
  const InputCharacterEntry *entry2 = element2;

  if (entry1->character < entry2->character) return -1;
  if (entry1->character > entry2->character) return 1;
  return 0;
}

typedef struct {
  const char *in;
  const char *out;
  const char *inOut;
} WriteCharacterDirectives;

#define WRITE_CHARACTER_DIRECTIVES(...) &(const WriteCharacterDirectives){__VA_ARGS__}
#define WRITE_CHARACTERS_IN_ONLY WRITE_CHARACTER_DIRECTIVES(.in="")
#define WRITE_CHARACTERS_OUT_ONLY WRITE_CHARACTER_DIRECTIVES(.out="")
#define WRITE_CHARACTERS_NATIVE WRITE_CHARACTER_DIRECTIVES(.in="input", .out="glyph", .inOut="char")
#define WRITE_CHARACTERS_LIBLOUIS WRITE_CHARACTER_DIRECTIVES(.in="nofor", .out="noback", .inOut="")

static int
writeInputCharacters (
  FILE *file, const wchar_t *character,
  const InputCharacterEntry **entry,
  const InputCharacterEntry *const *end,
  CharacterWriter *writer, const char *directive
) {
  while (*entry < *end) {
    if (character) {
      if ((*entry)->character >= *character) {
        break;
      }
    }

    if (directive) {
      if (!writer(file, directive, (*entry)->character, (*entry)->dots)) {
        return 0;
      }
    }

    *entry += 1;
  }

  return 1;
}

static int
writeCharacters (FILE *file, TextTableData *ttd, CharacterWriter writer, const void *data) {
  const WriteCharacterDirectives *directives = data;
  const TextTableHeader *header = getTextTableHeader(ttd);

  InputCharacterEntry inputCharactersBuffer[0X100];
  unsigned int inputCharacterCount = 0;

  for (unsigned int dots=0; dots<0X100; dots+=1) {
    if (BITMASK_TEST(header->inputCharacterDefined, dots)) {
      InputCharacterEntry *entry = &inputCharactersBuffer[inputCharacterCount++];
      entry->character = header->inputCharacters[dots];
      entry->dots = dots;
    }
  }

  qsort(
    inputCharactersBuffer, inputCharacterCount,
    sizeof(inputCharactersBuffer[0]),
    sortInputCharacterEntries
  );

  const InputCharacterEntry *inputCharacterEntry = inputCharactersBuffer;
  const InputCharacterEntry *const inputCharacterEnd = inputCharacterEntry + inputCharacterCount;

  for (unsigned int groupNumber=0; groupNumber<UNICODE_GROUP_COUNT; groupNumber+=1) {
    TextTableOffset groupOffset = header->unicodeGroups[groupNumber];

    if (groupOffset) {
      const UnicodeGroupEntry *group = getTextTableItem(ttd, groupOffset);

      for (unsigned int planeNumber=0; planeNumber<UNICODE_PLANES_PER_GROUP; planeNumber+=1) {
        TextTableOffset planeOffset = group->planes[planeNumber];

        if (planeOffset) {
          const UnicodePlaneEntry *plane = getTextTableItem(ttd, planeOffset);

          for (unsigned int rowNumber=0; rowNumber<UNICODE_ROWS_PER_PLANE; rowNumber+=1) {
            TextTableOffset rowOffset = plane->rows[rowNumber];

            if (rowOffset) {
              const UnicodeRowEntry *row = getTextTableItem(ttd, rowOffset);

              for (unsigned int cellNumber=0; cellNumber<UNICODE_CELLS_PER_ROW; cellNumber+=1) {
                if (BITMASK_TEST(row->cellDefined, cellNumber)) {
                  wchar_t character = UNICODE_CHARACTER(groupNumber, planeNumber, rowNumber, cellNumber);
                  unsigned char dots = row->cells[cellNumber];

                  {
                    int ok = writeInputCharacters(
                      file, &character,
                      &inputCharacterEntry, &inputCharacterEnd,
                      writer, directives->in
                    );

                    if (!ok) return 0;
                  }

                  if (inputCharacterEntry < inputCharacterEnd) {
                    if (character == inputCharacterEntry->character) {
                      if (dots == inputCharacterEntry->dots) {
                        const char *directive = directives->inOut;

                        if (directive) {
                          if (!writer(file, directive, character, dots)) {
                            return 0;
                          }

                          inputCharacterEntry += 1;
                          continue;
                        }
                      }
                    }
                  }

                  {
                    const char *directive = directives->out;

                    if (directive) {
                      if (!writer(file, directive, character, dots)) {
                        return 0;
                      }
                    }
                  }
                }
              }
            }
          }
        }
      }
    }
  }

  return writeInputCharacters(
    file, NULL,
    &inputCharacterEntry, &inputCharacterEnd,
    writer, directives->in
  );
}

static int
writeAliases (FILE *file, TextTableData *ttd, AliasWriter writer, const void *data) {
  const TextTableHeader *header = getTextTableHeader(ttd);
  const TextTableAliasEntry *alias = getTextTableItem(ttd, header->aliasArray);
  const TextTableAliasEntry *end = alias + header->aliasCount;

  while (alias < end) {
    if (!writer(file, alias, data)) return 0;
    alias += 1;
  }

  return 1;
}

static TextTableData *
readTable_native (const char *path, FILE *file, const void *data) {
  return processTextTableStream(file, path);
}

static int
writeDots_native (FILE *file, unsigned char dots) {
  if (fprintf(file, "(") == EOF) return 0;

  for (unsigned char dot=0X01; dot; dot<<=1) {
    char number = (dots & dot)? brlDotToNumber(dot): ' ';
    if (fprintf(file, "%c", number) == EOF) return 0;
  }

  if (fprintf(file, ")") == EOF) return 0;
  return 1;
}

static int
writeCharacter_native (
  FILE *file, const char *directive,
  wchar_t character, unsigned char dots
) {
  if (fprintf(file, "%s\t", directive) == EOF) return 0;
  if (!writeHexadecimalCharacter(file, character)) return 0;
  if (fprintf(file, "\t") == EOF) return 0;
  if (!writeDots_native(file, dots)) return 0;
  if (fprintf(file, "\t#") == EOF) return 0;

  if (fprintf(file, " ") == EOF) return 0;
  if (!writeUtf8Cell(file, dots)) return 0;

  if (!writeCharacterDescription(file, character)) return 0;
  return endLine(file);
}

static int
writeAlias_native (FILE *file, const TextTableAliasEntry *alias, const void *data) {
  if (fprintf(file, "alias\t") == EOF) return 0;
  if (!writeHexadecimalCharacter(file, alias->from)) return 0;
  if (fprintf(file, "\t") == EOF) return 0;
  if (!writeHexadecimalCharacter(file, alias->to)) return 0;
  if (fprintf(file, "\t#") == EOF) return 0;
  if (!writeCharacterDescription(file, alias->from)) return 0;
  return endLine(file);
}

static int
writeTable_native (
  const char *path, FILE *file, TextTableData *ttd, const void *data
) {
  if (!writeHeaderComment(file, writeHashComment)) return 0;
  if (!writeCharacters(file, ttd, writeCharacter_native, data)) return 0;
  if (!writeAliases(file, ttd, writeAlias_native, data)) return 0;
  return 1;
}

static TextTableData *
readTable_binary (const char *path, FILE *file, const void *data) {
  TextTableData *ttd;

  if ((ttd = newTextTableData())) {
    int count = 0X100;
    int byte;

    for (byte=0; byte<count; byte+=1) {
      int dots = fgetc(file);

      if (dots == EOF) {
        if (ferror(file)) {
          logMessage(LOG_ERR, "input error: %s: %s", path, strerror(errno));
        } else {
          logMessage(LOG_ERR, "table too short: %s", path);
        }

        break;
      }

      if (data) dots = mapDots(dots, data, dotsInternal);
      if (!setTextTableByte(ttd, byte, dots)) break;
    }

    if (byte == count) return ttd;
    destroyTextTableData(ttd);
  }

  return NULL;
}

static int
writeTable_binary (
  const char *path, FILE *file, TextTableData *ttd, const void *data
) {
  for (unsigned int byte=0; byte<0X100; byte+=1) {
    unsigned char dots;

    {
      wint_t character = convertCharToWchar(byte);
      if (character == WEOF) character = UNICODE_REPLACEMENT_CHARACTER;

      if (!getDots(ttd, character, &dots)) {
        dots = 0;
      }
    }

    if (data) dots = mapDots(dots, dotsInternal, data);
    if (fputc(dots, file) == EOF) {
      logMessage(LOG_ERR, "output error: %s: %s", path, strerror(errno));
      return 0;
    }
  }

  return 1;
}

static TextTableData *
readTable_LibLouis (const char *path, FILE *file, const void *data) {
  return processLibLouisStream(file, path);
}

static const char *
getCharacterType_LibLouis (wchar_t character) {
  if (iswspace(character)) return "space";

  if (iswlower(character)) return "lowercase";
  if (iswupper(character)) return "uppercase";
  if (iswalpha(character)) return "letter";

  if (iswdigit(character)) return "digit";
  if (iswxdigit(character)) return "digit";

  if (iswpunct(character)) return "punctuation";
  return NULL;
}

static int
writeCharacter_LibLouis (
  FILE *file, const char *directive,
  wchar_t character, unsigned char dots
) {
  const char *type = getCharacterType_LibLouis(character);
  if (!type) return 1;

  if (*directive) {
    if (fprintf(file, "%s ", directive) == EOF) {
      return 0;
    }
  }

  if (fprintf(file, "%s\t", type) == EOF) return 0;

  if (character == WC_C('\\')) {
    if (!writeString(file, "\\\\")) return 0;
  } else if (character == WC_C('\f')) {
    if (!writeString(file, "\\f")) return 0;
  } else if (character == WC_C('\n')) {
    if (!writeString(file, "\\n")) return 0;
  } else if (character == WC_C('\r')) {
    if (!writeString(file, "\\r")) return 0;
  } else if (character == WC_C(' ')) {
    if (!writeString(file, "\\s")) return 0;
  } else if (character == WC_C('\t')) {
    if (!writeString(file, "\\t")) return 0;
  } else if (character == WC_C('\v')) {
    if (!writeString(file, "\\v")) return 0;
  } else if ((character > 0X20) && (character < 0X7F) && (character != '#')) {
    wint_t value = character;
    if (fprintf(file, "%" PRIwc, value) == EOF) return 0;
  } else {
    unsigned long int value = character;
    int digits;
    char format;

    if (value < (1 << 16)) {
      digits = 4;
      format = 'x';
    } else if (value < (1 << 20)) {
      digits = 5;
      format = 'y';
    } else {
      digits = 8;
      format = 'z';
    }

    if (fprintf(file, "\\%c%0*lX", format, digits, value) == EOF) return 0;
  }

  if (fprintf(file, "\t") == EOF) return 0;
  if (!dots) {
    if (fprintf(file, "0") == EOF) return 0;
  } else {
    if (!writeDots(file, dots)) return 0;
  }

  {
    char name[0X40];

    if (getCharacterName(character, name, sizeof(name))) {
      if (fprintf(file, "\t# %s", name) == EOF) return 0;
    }
  }

  return endLine(file);
}

static int
writeTable_LibLouis (
  const char *path, FILE *file, TextTableData *ttd, const void *data
) {
  if (!writeHeaderComment(file, writeHashComment)) return 0;
  if (!writeCharacters(file, ttd, writeCharacter_LibLouis, data)) return 0;
  return 1;
}

#ifdef HAVE_ICONV_H
static TextTableData *
readTable_gnome (const char *path, FILE *file, const void *data) {
  return processGnomeBrailleStream(file, path);
}

static int
writeCharacter_gnome (
  FILE *file, const char *directive,
  wchar_t character, unsigned char dots
) {
  wchar_t pattern = UNICODE_BRAILLE_ROW | dots;

  if (iswprint(character) && !iswspace(character)) {
    Utf8Buffer utf8Character;
    if (!convertWcharToUtf8(character, utf8Character)) return 0;

    Utf8Buffer utf8Pattern;
    if (!convertWcharToUtf8(pattern, utf8Pattern)) return 0;

    if (fprintf(file, "UCS-CHAR %s %s", utf8Character, utf8Pattern) == EOF) return 0;
  } else {
    uint32_t c = character;
    uint32_t p = pattern;
    if (fprintf(file, "UNICODE-CHAR U+%02" PRIx32 " U+%04" PRIx32, c, p) == EOF) return 0;
  }

  {
    char name[0X40];

    if (getCharacterName(character, name, sizeof(name))) {
      if (fprintf(file, "  # %s", name) == EOF) return 0;
    }
  }

  return endLine(file);
}

static int
writeTable_gnome (
  const char *path, FILE *file, TextTableData *ttd, const void *data
) {
  /* TODO UNKNOWN-CHAR %wc all */
  if (fprintf(file, "ENCODING UTF-8\n") == EOF) return 0;
  if (!writeHeaderComment(file, writeHashComment)) return 0;
  if (!writeCharacters(file, ttd, writeCharacter_gnome, data)) return 0;
  return 1;
}
#endif /* HAVE_ICONV_H */

static int
writeCharacterDots_XCompose (FILE *file, unsigned char dots) {
  if (fprintf(file, "<braille_") == EOF) return 0;

  if (!dots) {
    if (fprintf(file, "blank") == EOF) return 0;
  } else {
    if (fprintf(file, "dots_") == EOF) return 0;
    if (!writeDots(file, dots)) return 0;
  }

  if (fprintf(file, ">") == EOF) return 0;

  return 1;
}

static int
writeCharacterOutput_XCompose (FILE *file, wchar_t character) {
  if (fprintf(file, " : \"") == EOF) return 0;

  switch (character) {
    case WC_C('\n'):
      if (fprintf(file, "\\n") == EOF) return 0;
      break;

    case WC_C('\r'):
      if (fprintf(file, "\\r") == EOF) return 0;
      break;

    case WC_C('"'):
      if (fprintf(file, "\\\"") == EOF) return 0;
      break;

    case WC_C('\\'):
      if (fprintf(file, "\\\\") == EOF) return 0;
      break;

    default:
      if (fprintf(file, "%lc", (wint_t)character) == EOF) return 0;
      break;
  }

  if (fprintf(file, "\"") == EOF) return 0;

  {
    char name[0X40];

    if (getCharacterName(character, name, sizeof(name))) {
      if (fprintf(file, "  # %s", name) == EOF) return 0;
    }
  }

  return endLine(file);
}

static int
writeCharacter_XCompose (
  FILE *file, const char *directive,
  wchar_t character, unsigned char dots
) {
  if (!writeCharacterDots_XCompose (file, dots)) return 0;
  return writeCharacterOutput_XCompose(file, character);
}

static int
writeTable_XCompose (
  const char *path, FILE *file, TextTableData *ttd, const void *data
) {
  if (!writeHeaderComment(file, writeHashComment)) return 0;
  if (!writeCharacters(file, ttd, writeCharacter_XCompose, data)) return 0;
  return 1;
}

static int
writeCharacter_half_XCompose (
  FILE *file, wchar_t character,
  unsigned char leftDots, unsigned char rightDots
) {
  if (!writeCharacterDots_XCompose (file, leftDots)) return 0;
  if (fprintf(file, " ") == EOF) return 0;
  if (!writeCharacterDots_XCompose (file, rightDots)) return 0;
  return writeCharacterOutput_XCompose(file, character);
}

static int
writeCharacter_leftrighthalf_XCompose (
  FILE *file, const char *directive,
  wchar_t character, unsigned char dots
) {
  unsigned char leftDots = brlGetLeftDots(dots);
  unsigned char rightDots = brlGetRightDots(dots);

  if (!writeCharacter_half_XCompose(file, character, leftDots, rightDots)) {
    return 0;
  }

  if (!leftDots && rightDots) {
    /* Also add shortcut without blank pattern for left part.  */
    if (!writeCharacterDots_XCompose (file, rightDots)) return 0;
    if (!writeCharacterOutput_XCompose(file, character)) return 0;
  }

  return 1;
}

static int
writeTable_leftrighthalf_XCompose (
  const char *path, FILE *file, TextTableData *ttd, const void *data
) {
  if (!writeHeaderComment(file, writeHashComment)) return 0;
  if (!writeCharacters(file, ttd, writeCharacter_leftrighthalf_XCompose, data)) return 0;
  return 1;
}

static int
writeCharacter_lefthalf_XCompose (
  FILE *file, const char *directive,
  wchar_t character, unsigned char dots
) {
  return writeCharacter_half_XCompose(
    file, character,
    brlGetLeftDots(dots),
    brlGetRightDotsToLeftDots(dots)
  );
}

static int
writeTable_lefthalf_XCompose (
  const char *path, FILE *file, TextTableData *ttd, const void *data
) {
  if (!writeHeaderComment(file, writeHashComment)) return 0;
  if (!writeCharacters(file, ttd, writeCharacter_lefthalf_XCompose, data)) return 0;
  return 1;
}

static int
writeCharacter_lefthalfalt_XCompose (
  FILE *file, const char *directive,
  wchar_t character, unsigned char dots
) {
  return writeCharacter_half_XCompose(
    file, character,
    brlGetLeftDots(dots),
    brlGetRightDotsToLeftDotsAlt(dots)
  );
}

static int
writeTable_lefthalfalt_XCompose (
  const char *path, FILE *file, TextTableData *ttd, const void *data
) {
  if (!writeHeaderComment(file, writeHashComment)) return 0;
  if (!writeCharacters(file, ttd, writeCharacter_lefthalfalt_XCompose, data)) return 0;
  return 1;
}

static int
writeCharacter_righthalf_XCompose (
  FILE *file, const char *directive,
  wchar_t character, unsigned char dots
) {
  return writeCharacter_half_XCompose(
    file, character,
    brlGetLeftDotsToRightDots(dots),
    brlGetRightDots(dots)
  );
}

static int
writeTable_righthalf_XCompose (
  const char *path, FILE *file, TextTableData *ttd, const void *data
) {
  if (!writeHeaderComment(file, writeHashComment)) return 0;
  if (!writeCharacters(file, ttd, writeCharacter_righthalf_XCompose, data)) return 0;
  return 1;
}

static int
writeCharacter_righthalfalt_XCompose (
  FILE *file, const char *directive,
  wchar_t character, unsigned char dots
) {
  return writeCharacter_half_XCompose(
    file, character,
    brlGetLeftDotsToRightDotsAlt(dots),
    brlGetRightDots(dots)
  );
}

static int
writeTable_righthalfalt_XCompose (
  const char *path, FILE *file, TextTableData *ttd, const void *data
) {
  if (!writeHeaderComment(file, writeHashComment)) return 0;
  if (!writeCharacters(file, ttd, writeCharacter_righthalfalt_XCompose, data)) return 0;
  return 1;
}

static int
writeCharacter_JAWS (
  FILE *file, const char *directive,
  wchar_t character, unsigned char dots
) {
  uint32_t value = character;

  if (fprintf(file, "U+%04" PRIX32 "=", value) == EOF) return 0;
  if (!writeDots(file, dots)) return 0;
  return endLine(file);
}

static int
writeTable_JAWS (
  const char *path, FILE *file, TextTableData *ttd, const void *data
) {
  if (!writeHeaderComment(file, writeSemicolonComment)) return 0;
  if (!writeCharacters(file, ttd, writeCharacter_JAWS, data)) return 0;
  return 1;
}

static int
writeCharacterValue_CPreprocessor (FILE *file, wchar_t character) {
  return fprintf(file, "0X%08" PRIX32, (uint32_t)character) != EOF;
}

static int
writeCharacterName_CPreprocessor (FILE *file, wchar_t character) {
  char name[0X40];

  if (getCharacterName(character, name, sizeof(name))) {
    if (!writeString(file, "\"")) return 0;
    if (!writeString(file, name)) return 0;
    if (!writeString(file, "\"")) return 0;
  } else {
    if (!beginCMacro(file, "BRLTTY_TEXT_TABLE_NO_NAME")) return 0;
    if (!writeCharacterValue_CPreprocessor(file, character)) return 0;
    if (!endCMacro(file)) return 0;
  }

  return 1;
}

static int
writeCharacter_CPreprocessor (
  FILE *file, const char *directive,
  wchar_t character, unsigned char dots
) {
  if (!beginCMacro(file, "BRLTTY_TEXT_TABLE_CHARACTER")) return 0;
  if (!writeCharacterValue_CPreprocessor(file, character)) return 0;

  if (!nextCArgument(file)) return 0;
  if (fprintf(file, "0X%02" PRIX8, dots) == EOF) return 0;

  if (!nextCArgument(file)) return 0;
  if (fprintf(file, "%s", directive) == EOF) return 0;

  if (!nextCArgument(file)) return 0;
  if (!writeCharacterName_CPreprocessor(file, character)) return 0;

  if (!endCMacro(file)) return 0;
  return endLine(file);
}

static int
writeAlias_CPreprocessor (FILE *file, const TextTableAliasEntry *alias, const void *data) {
  if (!beginCMacro(file, "BRLTTY_TEXT_TABLE_ALIAS")) return 0;
  if (!writeCharacterValue_CPreprocessor(file, alias->from)) return 0;

  if (!nextCArgument(file)) return 0;
  if (!writeCharacterValue_CPreprocessor(file, alias->to)) return 0;

  if (!nextCArgument(file)) return 0;
  if (!writeCharacterName_CPreprocessor(file, alias->from)) return 0;

  if (!endCMacro(file)) return 0;
  if (!endLine(file)) return 0;
  return 1;
}

static int
writeTable_CPreprocessor (
  const char *path, FILE *file, TextTableData *ttd, const void *data
) {
  if (!writeHeaderComment(file, writeCComment)) return 0;
  if (!endLine(file)) return 0;

  if (!defineCMacro(file, "BRLTTY_TEXT_TABLE_BEGIN_CHARACTERS", "")) return 0;
  if (!defineCMacro(file, "BRLTTY_TEXT_TABLE_CHARACTER", "(unicode, braille, properties, name)")) return 0;
  if (!defineCMacro(file, "BRLTTY_TEXT_TABLE_END_CHARACTERS", "")) return 0;

  if (!defineCMacro(file, "BRLTTY_TEXT_TABLE_BEGIN_ALIASES", "")) return 0;
  if (!defineCMacro(file, "BRLTTY_TEXT_TABLE_ALIAS", "(from, to, name)")) return 0;
  if (!defineCMacro(file, "BRLTTY_TEXT_TABLE_END_ALIASES", "")) return 0;

  if (!defineCMacro(file, "BRLTTY_TEXT_TABLE_NO_NAME", "(character)")) return 0;

  if (fprintf(file, "BRLTTY_TEXT_TABLE_BEGIN_CHARACTERS\n") == EOF) return 0;
  if (!writeCharacters(file, ttd, writeCharacter_CPreprocessor, data)) return 0;
  if (fprintf(file, "BRLTTY_TEXT_TABLE_END_CHARACTERS\n") == EOF) return 0;
  if (!endLine(file)) return 0;

  if (fprintf(file, "BRLTTY_TEXT_TABLE_BEGIN_ALIASES\n") == EOF) return 0;
  if (!writeAliases(file, ttd, writeAlias_CPreprocessor, data)) return 0;
  if (fprintf(file, "BRLTTY_TEXT_TABLE_END_ALIASES\n") == EOF) return 0;
  if (!endLine(file)) return 0;

  return 1;
}

typedef struct {
  const char *name;
  TableReader *read;
  TableWriter *write;
  const void *data;
} FormatEntry;

static const FormatEntry formatEntries[] = {
  { .name = "ttb",
    .read = readTable_native,
    .write = writeTable_native,
    .data = WRITE_CHARACTERS_NATIVE,
  },

  { .name = "a2b",
    .read = readTable_binary,
    .write = writeTable_binary,
    .data = &dots12345678,
  },

  { .name = "sbl",
    .read = readTable_binary,
    .write = writeTable_binary,
    .data = &dots14253678,
  },

  { .name = "ctb",
    .read = readTable_LibLouis,
    .write = writeTable_LibLouis,
    .data = WRITE_CHARACTERS_LIBLOUIS,
  },

  { .name = "utb",
    .read = readTable_LibLouis,
    .write = writeTable_LibLouis,
    .data = WRITE_CHARACTERS_LIBLOUIS,
  },

#ifdef HAVE_ICONV_H
  { .name = "gnb",
    .read = readTable_gnome,
    .write = writeTable_gnome,
    .data = WRITE_CHARACTERS_IN_ONLY,
  },
#endif /* HAVE_ICONV_H */

  { .name = "XCompose",
    .write = writeTable_XCompose,
    .data = WRITE_CHARACTERS_IN_ONLY,
  },

  { .name = "half-XCompose",
    .write = writeTable_leftrighthalf_XCompose,
    .data = WRITE_CHARACTERS_IN_ONLY,
  },

  { .name = "lefthalf-XCompose",
    .write = writeTable_lefthalf_XCompose,
    .data = WRITE_CHARACTERS_IN_ONLY,
  },

  { .name = "lefthalfalt-XCompose",
    .write = writeTable_lefthalfalt_XCompose,
    .data = WRITE_CHARACTERS_IN_ONLY,
  },

  { .name = "righthalf-XCompose",
    .write = writeTable_righthalf_XCompose,
    .data = WRITE_CHARACTERS_IN_ONLY,
  },

  { .name = "righthalfalt-XCompose",
    .write = writeTable_righthalfalt_XCompose,
    .data = WRITE_CHARACTERS_IN_ONLY,
  },

  { .name = "jbt",
    .write = writeTable_JAWS,
    .data = WRITE_CHARACTERS_OUT_ONLY,
  },

  { .name = "cpp",
    .write = writeTable_CPreprocessor,
    .data = WRITE_CHARACTER_DIRECTIVES(.in="1", .out="2", .inOut="3"),
  },

  { .name = NULL }
};

static const FormatEntry *
findFormatEntry (const char *name) {
  const FormatEntry *format = formatEntries;
  while (format->name) {
    if (strcmp(name, format->name) == 0) return format;
    format += 1;
  }
  return NULL;
}

static const FormatEntry *
getFormatEntry (const char *name, const char *path, const char *description) {
  if (!(name && *name)) {
    name = locatePathExtension(path);

    if (!(name && *++name)) {
      logMessage(LOG_ERR, "unspecified %s format.", description);
      exit(PROG_EXIT_SYNTAX);
    }
  }

  {
    const FormatEntry *format = findFormatEntry(name);
    if (format) return format;
  }

  logMessage(LOG_ERR, "unknown %s format: %s", description, name);
  exit(PROG_EXIT_SYNTAX);
}

static const char *inputPath;
static const char *outputPath;
static const FormatEntry *inputFormat;
static const FormatEntry *outputFormat;

static FILE *
openTable (const char **file, const char *mode, const char *directory, FILE *stdStream, const char *stdName) {
  if (stdStream) {
    if (strcmp(*file, standardStreamArgument) == 0) {
      *file = stdName;
      return stdStream;
    }
  }

  if (directory) {
    const char *path = makeTextTablePath(directory, *file);

    if (!path) return NULL;
    *file = path;
  }

  {
    FILE *stream = fopen(*file, mode);

    if (!stream) logMessage(LOG_ERR, "table open error: %s: %s", *file, strerror(errno));
    return stream;
  }
}

static FILE *
openInputTable (const char **path, int allowStandardInput) {
  FILE *stdStream;
  const char *stdName;

  if (allowStandardInput) {
    stdStream = stdin;
    stdName = standardInputName;
  } else {
    stdStream = NULL;
    stdName = NULL;
  }

  return openTable(path, "r", opt_tablesDirectory, stdStream, stdName);
}

static FILE *
openOutputTable (const char **path) {
  return openTable(path, "w", NULL, stdout, standardOutputName);
}

static TextTableData *
readTable (const char *path, FILE *file, const FormatEntry *fmt) {
  if (fmt->read) return fmt->read(path, file, fmt->data);
  logMessage(LOG_ERR, "reading not supported: %s", fmt->name);
  return NULL;
}

static ProgramExitStatus
convertTable (void) {
  ProgramExitStatus exitStatus;
  FILE *inputFile = openInputTable(&inputPath, 1);

  if (inputFile) {
    TextTableData *ttd;

    if ((ttd = readTable(inputPath, inputFile, inputFormat))) {
#ifdef HAVE_UNDEFINED_CHARACTERS_SUPPORT
      if (opt_undefined) {
        showUndefinedCharacters(ttd);
      }
#endif /* HAVE_UNDEFINED_CHARACTERS_SUPPORT */

      if (outputPath) {
        FILE *outputFile = openOutputTable(&outputPath);

        if (outputFile) {
          if (outputFormat->write(outputPath, outputFile, ttd, outputFormat->data)) {
            exitStatus = PROG_EXIT_SUCCESS;
          } else {
            exitStatus = PROG_EXIT_FATAL;
          }

          fclose(outputFile);
        } else {
          exitStatus = PROG_EXIT_FATAL;
        }
      } else {
        exitStatus = PROG_EXIT_SUCCESS;
      }

      destroyTextTableData(ttd);
    } else {
      exitStatus = PROG_EXIT_FATAL;
    }

    fclose(inputFile);
  } else {
    exitStatus = PROG_EXIT_FATAL;
  }

  return exitStatus;
}

#include "get_curses.h"
#ifndef GOT_CURSES
#define refresh() fflush(stdout)
#define printw printf
#define erase() printf("\r\n\v")
#define beep() printf("\a")

static int inputAttributesChanged;

#if defined(__MINGW32__)
#define STDIN_HANDLE ((HANDLE)_get_osfhandle(STDIN_FILENO))
static DWORD inputConsoleMode;

#undef beep
#define beep() MessageBeep(MB_ICONWARNING)

#else /* termios */
#include <termios.h>
static struct termios inputTerminalAttributes;

#ifndef _POSIX_VDISABLE
#define _POSIX_VDISABLE 0
#endif /* _POSIX_VDISABLE */
#endif /* input terminal definitions */
#endif /* GOT_CURSES */

#ifdef ENABLE_API
#define BRLAPI_NO_DEPRECATED
#include "brlapi.h"
#endif /* ENABLE_API */

typedef struct {
  TextTableData *ttd;
  unsigned updated:1;

  const char *charset;
  union {
    wchar_t unicode;
    unsigned char byte;
  } character;

#ifdef ENABLE_API
  brlapi_fileDescriptor brlapiFileDescriptor;
  const char *brlapiErrorFunction;
  const char *brlapiErrorMessage;
#endif /* ENABLE_API */

  unsigned int displayWidth;
  unsigned int displayHeight;
} EditTableData;

#ifdef ENABLE_API
static int
haveBrailleDisplay (EditTableData *etd) {
  return etd->brlapiFileDescriptor != (brlapi_fileDescriptor)-1;
}

static void
setBrlapiError (EditTableData *etd, const char *function) {
  if ((etd->brlapiErrorFunction = function)) {
    etd->brlapiErrorMessage = brlapi_strerror(&brlapi_error);
  } else {
    etd->brlapiErrorMessage = NULL;
  }
}

static void
releaseBrailleDisplay (EditTableData *etd) {
  brlapi_closeConnection();
  etd->brlapiFileDescriptor = (brlapi_fileDescriptor)-1;
}

static int
claimBrailleDisplay (EditTableData *etd) {
  etd->brlapiFileDescriptor = brlapi_openConnection(NULL, NULL);
  if (haveBrailleDisplay(etd)) {
    if (brlapi_getDisplaySize(&etd->displayWidth, &etd->displayHeight) != -1) {
      if (brlapi_enterTtyMode(BRLAPI_TTY_DEFAULT, NULL) != -1) {
        setBrlapiError(etd, NULL);
        return 1;
      } else {
        setBrlapiError(etd, "brlapi_enterTtyMode");
      }
    } else {
      setBrlapiError(etd, "brlapi_getDisplaySize");
    }

    releaseBrailleDisplay(etd);
  } else {
    setBrlapiError(etd, "brlapi_openConnection");
  }

  return 0;
}
#endif /* ENABLE_API */

static int
getCharacter (EditTableData *etd, wchar_t *character) {
  if (etd->charset) {
    wint_t wc = convertCharToWchar(etd->character.byte);
    if (wc == WEOF) return 0;
    *character = wc;
  } else {
    *character = etd->character.unicode;
  }

  return 1;
}

static wchar_t *
makeCharacterDescription (TextTableData *ttd, wchar_t character, size_t *length, int *defined, unsigned char *braille) {
  char buffer[0X100];
  size_t characterIndex;
  size_t brailleIndex;
  size_t descriptionLength;

  unsigned char dots = 0;
  int gotDots = getDots(ttd, character, &dots);

  wchar_t printableCharacter;
  char printablePrefix;

  printableCharacter = character;
  if (iswLatin1(printableCharacter)) {
    printablePrefix = '^';
    if (!(printableCharacter & 0X60)) {
      printableCharacter |= 0X40;
      if (printableCharacter & 0X80) printablePrefix = '~';
    } else if (printableCharacter == 0X7F) {
      printableCharacter ^= 0X40;       
    } else if (printableCharacter != printablePrefix) {
      printablePrefix = ' ';
    }
  } else {
    printablePrefix = ' ';
  }
  if (!gotDots) dots = 0;

#define DOT(n) ((dots & BRL_DOT_##n)? ((n) + '0'): ' ')
  {
    uint32_t value = character;

    STR_BEGIN(buffer, sizeof(buffer));
    STR_PRINTF("%04" PRIX32 " %c", value, printablePrefix);
    characterIndex = STR_LENGTH;
    STR_PRINTF("x ");
    brailleIndex = STR_LENGTH;
    STR_PRINTF("x %c%c%c%c%c%c%c%c%c%c",
             (gotDots? '[': ' '),
             DOT(1), DOT(2), DOT(3), DOT(4), DOT(5), DOT(6), DOT(7), DOT(8),
             (gotDots? ']': ' '));
    descriptionLength = STR_LENGTH;
    STR_END;
  }
#undef DOT

  {
    char *name = buffer + descriptionLength + 1;
    int size = buffer + sizeof(buffer) - name;

    if (getCharacterName(character, name, size)) {
      descriptionLength += strlen(name) + 1;
      *--name = ' ';
    }
  }

  {
    wchar_t *description = calloc(descriptionLength+1, sizeof(*description));
    if (description) {
      for (unsigned int i=0; i<descriptionLength; i+=1) {
        wint_t wc = convertCharToWchar(buffer[i]);
        if (wc == WEOF) wc = WC_C(' ');
        description[i] = wc;
      }

      description[characterIndex] = printableCharacter;
      description[brailleIndex] = gotDots? (UNICODE_BRAILLE_ROW | dots): WC_C(' ');
      description[descriptionLength] = 0;

      if (length) *length = descriptionLength;
      if (defined) *defined = gotDots;
      if (braille) *braille = dots;
      return description;
    }
  }

  return NULL;
}

static void
printCharacterString (const wchar_t *wcs) {
  while (*wcs) {
    wint_t wc = *wcs++;
    printw("%" PRIwc, wc);
  }
}

static void
printNavigationPair (
  const char *key1, const char *preposition1,
  const char *key2, const char *preposition2,
  const char *adjective, const char *noun,
  int width
) {
  int length = 0;

  if (*key1 || *key2) {
    const char *separator = (*key1 && *key2)? "/": "";

    printw("%s%s%s: %s%s%s %s %s%n",
           key1, separator, key2,
           (*key1? preposition1: ""), separator, (*key2? preposition2: ""),
           adjective, noun, &length);
  }

  if (width > length) printw("%*s", width-length, "");
}

static int
updateCharacterDescription (EditTableData *etd) {
  int ok = 0;

  wchar_t character = UNICODE_REPLACEMENT_CHARACTER;
  int gotCharacter = getCharacter(etd, &character);

  size_t length;
  int gotDots;
  unsigned char dots;
  wchar_t *description = makeCharacterDescription(etd->ttd, character, &length, &gotDots, &dots);

  if (description) {
    ok = 1;
    erase();

#if defined(GOT_CURSES)
#define KEY_FIRST_ACTUAL_CHARACTER ""
#define KEY_LAST_ACTUAL_CHARACTER ""
#define KEY_PREVIOUS_ACTUAL_CHARACTER "Left"
#define KEY_NEXT_ACTUAL_CHARACTER "Right"
#define KEY_FIRST_DEFINED_CHARACTER "Home"
#define KEY_LAST_DEFINED_CHARACTER "End"
#define KEY_PREVIOUS_DEFINED_CHARACTER "Up"
#define KEY_NEXT_DEFINED_CHARACTER "Down"
#define KEY_TOGGLE_DOT1 "F4"
#define KEY_TOGGLE_DOT2 "F3"
#define KEY_TOGGLE_DOT3 "F2"
#define KEY_TOGGLE_DOT4 "F5"
#define KEY_TOGGLE_DOT5 "F6"
#define KEY_TOGGLE_DOT6 "F7"
#define KEY_TOGGLE_DOT7 "F1"
#define KEY_TOGGLE_DOT8 "F8"
#define KEY_TOGGLE_CHARACTER "F9"
#define KEY_ALTERNATE_CHARACTER "F10"
#define KEY_SAVE_TABLE "F11"
#define KEY_EXIT_EDITOR "F12"
#else /* standard input/output */
#define KEY_FIRST_ACTUAL_CHARACTER "^S"
#define KEY_LAST_ACTUAL_CHARACTER "^G"
#define KEY_PREVIOUS_ACTUAL_CHARACTER "^D"
#define KEY_NEXT_ACTUAL_CHARACTER "^F"
#define KEY_FIRST_DEFINED_CHARACTER "^H"
#define KEY_LAST_DEFINED_CHARACTER "^L"
#define KEY_PREVIOUS_DEFINED_CHARACTER "^J"
#define KEY_NEXT_DEFINED_CHARACTER "^K"
#define KEY_TOGGLE_DOT1 "^R"
#define KEY_TOGGLE_DOT2 "^E"
#define KEY_TOGGLE_DOT3 "^W"
#define KEY_TOGGLE_DOT4 "^U"
#define KEY_TOGGLE_DOT5 "^I"
#define KEY_TOGGLE_DOT6 "^O"
#define KEY_TOGGLE_DOT7 "^Q"
#define KEY_TOGGLE_DOT8 "^P"
#define KEY_TOGGLE_CHARACTER "^T"
#define KEY_ALTERNATE_CHARACTER "^Y"
#define KEY_SAVE_TABLE "^A"
#define KEY_EXIT_EDITOR "^Z"
#endif /* key definitions */

    {
      const char *first = "first";
      const char *last = "last";
      const char *previous = "prev";
      const char *next = "next";

      const char *actual = etd->charset? etd->charset: "unicode";
      const char *defined = "defined";

      const char *character = "char";
      const int width = 38;

      printNavigationPair(KEY_PREVIOUS_ACTUAL_CHARACTER, previous,
                          KEY_NEXT_ACTUAL_CHARACTER, next,
                          actual, character, width);
      printNavigationPair(KEY_PREVIOUS_DEFINED_CHARACTER, previous,
                          KEY_NEXT_DEFINED_CHARACTER, next,
                          defined, character, 0);
      printw("\n");

      printNavigationPair(KEY_FIRST_ACTUAL_CHARACTER, first,
                          KEY_LAST_ACTUAL_CHARACTER, last,
                          actual, character, width);
      printNavigationPair(KEY_FIRST_DEFINED_CHARACTER, first,
                          KEY_LAST_DEFINED_CHARACTER, last,
                          defined, character, 0);
      printw("\n");
    }
    printw("\n");

#define DOT(n) printw("%s: %s dot %u    ", KEY_TOGGLE_DOT##n, ((dots & BRL_DOT_##n)? "lower": "raise"), n)
    DOT(1);
    DOT(4);
    printw("%s: %s", KEY_TOGGLE_CHARACTER,
           !gotCharacter? "":
           !gotDots? "define character (empty cell)":
           dots? "clear all dots":
           "undefine character");
    printw("\n");

    DOT(2);
    DOT(5);
    printw("%s:", KEY_ALTERNATE_CHARACTER);
    {
      const char *lower = "lowercase";
      const char *upper = "uppercase";
      const char *alternate = NULL;

      if (etd->charset) {
        if (isupper(etd->character.byte)) {
          alternate = lower;
        } else if (islower(etd->character.byte)) {
          alternate = upper;
        }
      } else {
        if (iswupper(etd->character.unicode)) {
          alternate = lower;
        } else if (iswlower(etd->character.unicode)) {
          alternate = upper;
        }
      }

      if (alternate) printw(" switch to %s equivalent", alternate);
    }
    printw("\n");

    DOT(3);
    DOT(6);
    printw("%s: %s", KEY_SAVE_TABLE,
           etd->updated? "save table": "");
    printw("\n");

    DOT(7);
    DOT(8);
    printw("%s: exit table editor", KEY_EXIT_EDITOR);
    if (etd->updated) printw(" (unsaved changes)");
    printw("\n");
#undef DOT

    printw("\n");

    if (etd->charset) {
      printw("%02X: %s\n", etd->character.byte, etd->charset);
    }

    printCharacterString(description);
    printw("\n");

#define DOT(n) ((dots & BRL_DOT_##n)? '#': ' ')
    printw(" +---+ \n");
    printw("1|%c %c|4\n", DOT(1), DOT(4));
    printw("2|%c %c|5\n", DOT(2), DOT(5));
    printw("3|%c %c|6\n", DOT(3), DOT(6));
    printw("7|%c %c|8\n", DOT(7), DOT(8));
    printw(" +---+ \n");
#undef DOT

#ifdef ENABLE_API
    if (etd->brlapiErrorFunction) {
      printw("BrlAPI error: %s: %s\n",
             etd->brlapiErrorFunction, etd->brlapiErrorMessage);
      setBrlapiError(etd, NULL);
    }
#endif /* ENABLE_API */

    refresh();

#ifdef ENABLE_API
    if (haveBrailleDisplay(etd)) {
      brlapi_writeArguments_t args = BRLAPI_WRITEARGUMENTS_INITIALIZER;
      wchar_t text[etd->displayWidth];

      {
        size_t count = MIN(etd->displayWidth, length);
        wmemcpy(text, description, count);
        wmemset(&text[count], WC_C(' '), etd->displayWidth-count);
      }

      args.regionBegin = 1;
      args.regionSize = etd->displayWidth;
      args.text = (char *)text;
      args.textSize = etd->displayWidth * sizeof(text[0]);
      args.charset = "WCHAR_T";

      if (brlapi_write(&args) == -1) {
        setBrlapiError(etd, "brlapi_write");
        releaseBrailleDisplay(etd);
      }
    }
#endif /* ENABLE_API */

    free(description);
  }

  return ok;
}

static void
setPreviousActualCharacter (EditTableData *etd) {
  if (etd->charset) {
    etd->character.byte = (etd->character.byte - 1) & CHARSET_BYTE_MAXIMUM;
  } else {
    etd->character.unicode = (etd->character.unicode - 1) & WCHAR_MAX;
  }
}

static void
setNextActualCharacter (EditTableData *etd) {
  if (etd->charset) {
    etd->character.byte = (etd->character.byte + 1) & CHARSET_BYTE_MAXIMUM;
  } else {
    etd->character.unicode = (etd->character.unicode + 1) & WCHAR_MAX;
  }
}

static void
setFirstActualCharacter (EditTableData *etd) {
  if (etd->charset) {
    etd->character.byte = 0;
  } else {
    etd->character.unicode = 0;
  }
}

static void
setLastActualCharacter (EditTableData *etd) {
  if (etd->charset) {
    etd->character.byte = CHARSET_BYTE_MAXIMUM;
  } else {
    etd->character.unicode = WCHAR_MAX;
  }
}

static int
findCharacter (EditTableData *etd, int backward) {
  const int increment = backward? -1: 1;

  if (etd->charset) {
    const int byteLimit = backward? 0: CHARSET_BYTE_MAXIMUM;
    const int byteReset = CHARSET_BYTE_MAXIMUM - byteLimit - increment;

    unsigned char byte = etd->character.byte;
    int counter = CHARSET_BYTE_COUNT;

    do {
      if (byte == byteLimit) byte = byteReset;
      byte += increment;

      {
        wint_t wc = convertCharToWchar(byte);

        if (wc != WEOF) {
          if (getUnicodeCell(etd->ttd, wc)) {
            etd->character.byte = byte;
            return 1;
          }
        }       
      }
    } while ((counter -= 1) >= 0);
  } else {
    const int groupLimit = backward? 0: UNICODE_GROUP_MAXIMUM;
    const int planeLimit = backward? 0: UNICODE_PLANE_MAXIMUM;
    const int rowLimit = backward? 0: UNICODE_ROW_MAXIMUM;
    const int cellLimit = backward? 0: UNICODE_CELL_MAXIMUM;

    const int groupReset = UNICODE_GROUP_MAXIMUM - groupLimit;
    const int planeReset = UNICODE_PLANE_MAXIMUM - planeLimit;
    const int rowReset = UNICODE_ROW_MAXIMUM - rowLimit;
    const int cellReset = UNICODE_CELL_MAXIMUM - cellLimit - increment;

    int groupNumber = UNICODE_GROUP_NUMBER(etd->character.unicode);
    int planeNumber = UNICODE_PLANE_NUMBER(etd->character.unicode);
    int rowNumber = UNICODE_ROW_NUMBER(etd->character.unicode);
    int cellNumber = UNICODE_CELL_NUMBER(etd->character.unicode);

    const TextTableHeader *header = getTextTableHeader(etd->ttd);
    int groupCounter = UNICODE_GROUP_COUNT;

    do {
      TextTableOffset groupOffset = header->unicodeGroups[groupNumber];

      if (groupOffset) {
        const UnicodeGroupEntry *group = getTextTableItem(etd->ttd, groupOffset);

        while (1) {
          TextTableOffset planeOffset = group->planes[planeNumber];

          if (planeOffset) {
            const UnicodePlaneEntry *plane = getTextTableItem(etd->ttd, planeOffset);

            while (1) {
              TextTableOffset rowOffset = plane->rows[rowNumber];

              if (rowOffset) {
                const UnicodeRowEntry *row = getTextTableItem(etd->ttd, rowOffset);

                while (cellNumber != cellLimit) {
                  cellNumber += increment;

                  if (BITMASK_TEST(row->cellDefined, cellNumber)) {
                    etd->character.unicode = UNICODE_CHARACTER(groupNumber, planeNumber, rowNumber, cellNumber);
                    return 1;
                  }
                }
              }

              cellNumber = cellReset;

              if (rowNumber == rowLimit) break;
              rowNumber += increment;
            }
          }

          rowNumber = rowReset;
          cellNumber = cellReset;

          if (planeNumber == planeLimit) break;
          planeNumber += increment;
        }
      }

      planeNumber = planeReset;
      rowNumber = rowReset;
      cellNumber = cellReset;

      if (groupNumber == groupLimit) {
        groupNumber = groupReset;
      } else {
        groupNumber += increment;
      }
    } while ((groupCounter -= 1) >= 0);
  }

  return 0;
}

static int
setPreviousDefinedCharacter (EditTableData *etd) {
  return findCharacter(etd, 1);
}

static int
setNextDefinedCharacter (EditTableData *etd) {
  return findCharacter(etd, 0);
}

static int
setFirstDefinedCharacter (EditTableData *etd) {
  setLastActualCharacter(etd);
  if (setNextDefinedCharacter(etd)) return 1;

  setFirstActualCharacter(etd);
  return 0;
}

static int
setLastDefinedCharacter (EditTableData *etd) {
  setFirstActualCharacter(etd);
  if (setPreviousDefinedCharacter(etd)) return 1;

  setLastActualCharacter(etd);
  return 0;
}

static int
setAlternateCharacter (EditTableData *etd) {
  if (etd->charset) {
    if (isalpha(etd->character.byte)) {
      if (islower(etd->character.byte)) {
        etd->character.byte = toupper(etd->character.byte);
        return 1;
      }

      if (isupper(etd->character.byte)) {
        etd->character.byte = tolower(etd->character.byte);
        return 1;
      }
    }
  } else {
    if (iswalpha(etd->character.unicode)) {
      if (iswlower(etd->character.unicode)) {
        etd->character.unicode = towupper(etd->character.unicode);
        return 1;
      }

      if (iswupper(etd->character.unicode)) {
        etd->character.unicode = towlower(etd->character.unicode);
        return 1;
      }
    }
  }

  return 0;
}

static int
toggleCharacter (EditTableData *etd) {
  wchar_t character;
  if (!getCharacter(etd, &character)) return 0;

  {
    const unsigned char *cell = getUnicodeCell(etd->ttd, character);
    if (cell && !*cell) {
      unsetTextTableCharacter(etd->ttd, character);
    } else if (!setTextTableCharacter(etd->ttd, character, 0)) {
      return 0;
    }
  }

  etd->updated = 1;
  return 1;
}

static int
toggleDot (EditTableData *etd, unsigned char dot) {
  wchar_t character;

  if (getCharacter(etd, &character)) {
    const unsigned char *cell = getUnicodeCell(etd->ttd, character);
    unsigned char dots = cell? *cell: 0;

    if (setTextTableCharacter(etd->ttd, character, dots^dot)) {
      etd->updated = 1;
      return 1;
    }
  }

  return 0;
}

static int
setDots (EditTableData *etd, unsigned char dots) {
  wchar_t character;

  if (getCharacter(etd, &character)) {
    if (setTextTableCharacter(etd->ttd, character, dots)) {
      etd->updated = 1;
      return 1;
    }
  }

  return 0;
}

static int
saveTable (EditTableData *etd) {
  int ok = 0;
  FILE *outputFile;

  if (!outputPath) outputPath = inputPath;
  if (!outputFormat) outputFormat = inputFormat;

  if ((outputFile = openOutputTable(&outputPath))) {
    if (outputFormat->write(outputPath, outputFile, etd->ttd, outputFormat->data)) {
      ok = 1;
      etd->updated = 0;
    }

    fclose(outputFile);
  }

  return ok;
}

static int
doKeyboardCommand (EditTableData *etd) {
#undef IS_UNICODE_CHARACTER
#if defined(GOT_CURSES)
#ifdef GOT_CURSES_WCH
#define IS_UNICODE_CHARACTER
  wint_t ch;
  int ret = get_wch(&ch);

  if (ret == KEY_CODE_YES)
#else /* GOT_CURSES_WCH */
  int ch = getch();

  if (ch >= 0X100)
#endif /* GOT_CURSES_WCH */
  {
    switch (ch) {
      case KEY_LEFT:
        setPreviousActualCharacter(etd);
        break;

      case KEY_RIGHT:
        setNextActualCharacter(etd);
        break;

      case KEY_UP:
        if (!setPreviousDefinedCharacter(etd)) beep();
        break;

      case KEY_DOWN:
        if (!setNextDefinedCharacter(etd)) beep();
        break;

      case KEY_HOME:
        if (!setFirstDefinedCharacter(etd)) beep();
        break;

      case KEY_END:
        if (!setLastDefinedCharacter(etd)) beep();
        break;

      case KEY_F(1):
        if (!toggleDot(etd, BRL_DOT_7)) beep();
        break;

      case KEY_F(2):
        if (!toggleDot(etd, BRL_DOT_3)) beep();
        break;

      case KEY_F(3):
        if (!toggleDot(etd, BRL_DOT_2)) beep();
        break;

      case KEY_F(4):
        if (!toggleDot(etd, BRL_DOT_1)) beep();
        break;

      case KEY_F(5):
        if (!toggleDot(etd, BRL_DOT_4)) beep();
        break;

      case KEY_F(6):
        if (!toggleDot(etd, BRL_DOT_5)) beep();
        break;

      case KEY_F(7):
        if (!toggleDot(etd, BRL_DOT_6)) beep();
        break;

      case KEY_F(8):
        if (!toggleDot(etd, BRL_DOT_8)) beep();
        break;

      case KEY_F(9):
        if (!toggleCharacter(etd)) beep();
        break;

      case KEY_F(10):
        if (!setAlternateCharacter(etd)) beep();
        break;

      case KEY_F(11):
        if (!(etd->updated && saveTable(etd))) beep();
        break;

      case KEY_F(12):
        return 0;

      default:
        beep();
        break;
    }
  } else

#ifdef GOT_CURSES_WCH
  if (ret == OK)
#endif /* GOT_CURSES_WCH */
#else /* standard input/output */
  int handled = 1;

#ifdef __MSDOS__
  int ch = fgetc(stdin);
  if (ch == EOF) return 0;
#else /* __MSDOS__ */
#define IS_UNICODE_CHARACTER
  wint_t ch = fgetwc(stdin);
  if (ch == WEOF) return 0;
#endif /* __MSDOS__ */

  switch (ch) {
    case 0X1B: /* escape */
      return 0;

    case 0X11: /* CTRL-Q */
      if (!toggleDot(etd, BRL_DOT_7)) beep();
      break;

    case 0X17: /* CTRL-W */
      if (!toggleDot(etd, BRL_DOT_3)) beep();
      break;

    case 0X05: /* CTRL-E */
      if (!toggleDot(etd, BRL_DOT_2)) beep();
      break;

    case 0X12: /* CTRL-R */
      if (!toggleDot(etd, BRL_DOT_1)) beep();
      break;

    case 0X14: /* CTRL-T */
      if (!toggleCharacter(etd)) beep();
      break;

    case 0X19: /* CTRL-Y */
      if (!setAlternateCharacter(etd)) beep();
      break;

    case 0X15: /* CTRL-U */
      if (!toggleDot(etd, BRL_DOT_4)) beep();
      break;

    case 0X09: /* CTRL-I */
      if (!toggleDot(etd, BRL_DOT_5)) beep();
      break;

    case 0X0F: /* CTRL-O */
      if (!toggleDot(etd, BRL_DOT_6)) beep();
      break;

    case 0X10: /* CTRL-P */
      if (!toggleDot(etd, BRL_DOT_8)) beep();
      break;

    case 0X01: /* CTRL-A */
      if (!(etd->updated && saveTable(etd))) beep();
      break;

    case 0X13: /* CTRL-S */
      setFirstActualCharacter(etd);
      break;

    case 0X04: /* CTRL-D */
      setPreviousActualCharacter(etd);
      break;

    case 0X06: /* CTRL-F */
      setNextActualCharacter(etd);
      break;

    case 0X07: /* CTRL-G */
      setLastActualCharacter(etd);
      break;

    case 0X08: /* CTRL-H */
      if (!setFirstDefinedCharacter(etd)) beep();
      break;

    case 0X0A: /* CTRL-J */
      if (!setPreviousDefinedCharacter(etd)) beep();
      break;

    case 0X0B: /* CTRL-K */
      if (!setNextDefinedCharacter(etd)) beep();
      break;

    case 0X0C: /* CTRL-L */
      if (!setLastDefinedCharacter(etd)) beep();
      break;

    case 0X1A: /* CTRL-Z */
      return 0;

    case 0X18: /* CTRL-X */
      beep();
      break;

    case 0X03: /* CTRL-C */
      beep();
      break;

    case 0X16: /* CTRL-V */
      beep();
      break;

    case 0X02: /* CTRL-B */
      beep();
      break;

    case 0X0E: /* CTRL-N */
      beep();
      break;

    case 0X0D: /* CTRL-M */
      beep();
      break;

    default:
      handled = 0;
      break;
  }

  if (!handled)
#endif /* read character */

  {
    wint_t character;

#ifdef IS_UNICODE_CHARACTER
    character = ch;
#else /* IS_UNICODE_CHARACTER */
    character = convertCharToWchar(ch);
#endif /* IS_UNICODE_CHARACTER */

    if ((character >= UNICODE_BRAILLE_ROW) &&
        (character <= (UNICODE_BRAILLE_ROW | UNICODE_CELL_MASK))) {
      if (!setDots(etd, character & UNICODE_CELL_MASK)) beep();
    } else {
      if (etd->charset) {
        int c;

#ifdef IS_UNICODE_CHARACTER
        c = convertWcharToChar(ch);
#else /* IS_UNICODE_CHARACTER */
        c = ch;
#endif /* IS_UNICODE_CHARACTER */

        if (c != EOF) {
          etd->character.byte = c;
        } else {
          beep();
        }
      } else if (character != WEOF) {
        etd->character.unicode = character;
      } else {
        beep();
      }
    }
  }

  return 1;
}

#ifndef __MINGW32__
#ifdef ENABLE_API
static int
doBrailleCommand (EditTableData *etd) {
  if (haveBrailleDisplay(etd)) {
    brlapi_keyCode_t key;
    int ret = brlapi_readKey(0, &key);

    if (ret == 1) {
      unsigned long code = key & BRLAPI_KEY_CODE_MASK;

      switch (key & BRLAPI_KEY_TYPE_MASK) {
        case BRLAPI_KEY_TYPE_CMD:
          switch (code & BRLAPI_KEY_CMD_BLK_MASK) {
            case 0:
              switch (code) {
                case BRLAPI_KEY_CMD_FWINLT:
                  setPreviousActualCharacter(etd);
                  break;

                case BRLAPI_KEY_CMD_FWINRT:
                  setNextActualCharacter(etd);
                  break;

                case BRLAPI_KEY_CMD_LNUP:
                  if (!setPreviousDefinedCharacter(etd)) beep();
                  break;

                case BRLAPI_KEY_CMD_LNDN:
                  if (!setNextDefinedCharacter(etd)) beep();
                  break;

                case BRLAPI_KEY_CMD_TOP_LEFT:
                case BRLAPI_KEY_CMD_TOP:
                  if (!setFirstDefinedCharacter(etd)) beep();
                  break;

                case BRLAPI_KEY_CMD_BOT_LEFT:
                case BRLAPI_KEY_CMD_BOT:
                  if (!setLastDefinedCharacter(etd)) beep();
                  break;

                default:
                  beep();
                  break;
              }
              break;

            case BRLAPI_KEY_CMD_PASSDOTS:
              if (!setDots(etd, code & BRLAPI_KEY_CMD_ARG_MASK)) beep();
              break;

            default:
              beep();
              break;
          }
          break;

        case BRLAPI_KEY_TYPE_SYM: {
          /* latin1 */
          if (code < 0X100) code |= BRLAPI_KEY_SYM_UNICODE;

          if ((code & 0X1f000000) == BRLAPI_KEY_SYM_UNICODE) {
            /* unicode */
            if ((code & 0Xffff00) == UNICODE_BRAILLE_ROW) {
              /* Set braille pattern */
              if (!setDots(etd, code & UNICODE_CELL_MASK)) beep();
            } else {
              wchar_t character = code & 0XFFFFFF;

              if (etd->charset) {
                int c = convertWcharToChar(character);

                if (c != EOF) {
                  etd->character.byte = c;
                } else {
                  beep();
                }
              } else {
                etd->character.unicode = character;
              }
            }
          } else {
            switch (code) {
              case BRLAPI_KEY_SYM_LEFT:
                setPreviousActualCharacter(etd);
                break;

              case BRLAPI_KEY_SYM_RIGHT:
                setNextActualCharacter(etd);
                break;

              case BRLAPI_KEY_SYM_UP:
                if (!setPreviousDefinedCharacter(etd)) beep();
                break;

              case BRLAPI_KEY_SYM_DOWN:
                if (!setNextDefinedCharacter(etd)) beep();
                break;

              case BRLAPI_KEY_SYM_HOME:
                if (!setFirstDefinedCharacter(etd)) beep();
                break;

              case BRLAPI_KEY_SYM_END:
                if (!setLastDefinedCharacter(etd)) beep();
                break;

              default:
                beep();
                break;
            }
          }
          break;
        }

        default:
          beep();
          break;
      }
    } else if (ret == -1) {
      setBrlapiError(etd, "brlapi_readKey");
      releaseBrailleDisplay(etd);
    }
  }

  return 1;
}
#endif /* ENABLE_API */
#endif /* __MINGW32__ */

static ProgramExitStatus
editTable (void) {
  ProgramExitStatus exitStatus;
  EditTableData etd;

  etd.ttd = NULL;
  etd.updated = 0;

  {
    FILE *inputFile = openInputTable(&inputPath, 0);

    if (inputFile) {
      if ((etd.ttd = readTable(inputPath, inputFile, inputFormat))) {
        exitStatus = PROG_EXIT_SUCCESS;
      } else {
        exitStatus = PROG_EXIT_FATAL;
      }

      fclose(inputFile);
    } else {
      exitStatus = PROG_EXIT_FATAL;
    }
  }

  if (exitStatus == PROG_EXIT_SUCCESS) {
#ifdef ENABLE_API
    claimBrailleDisplay(&etd);
#endif /* ENABLE_API */

#if defined(GOT_CURSES)
    initscr();
    cbreak();
    keypad(stdscr, TRUE);
    noecho();
    nonl();
    intrflush(stdscr, FALSE);
#else /* standard input/output */
    setvbuf(stdin, NULL, _IONBF, 0);
    inputAttributesChanged = 0;

#if defined(__MINGW32__)
    if (GetConsoleMode(STDIN_HANDLE, &inputConsoleMode)) {
      DWORD newConsoleMode = inputConsoleMode;
      newConsoleMode &= ~(ENABLE_LINE_INPUT | ENABLE_ECHO_INPUT | ENABLE_PROCESSED_INPUT);

      if (SetConsoleMode(STDIN_HANDLE, newConsoleMode)) {
        inputAttributesChanged = 1;
      }
    }
#else /* termios */
    if (tcgetattr(STDIN_FILENO, &inputTerminalAttributes) != -1) {
      struct termios newAttributes = inputTerminalAttributes;

      newAttributes.c_iflag &= ~(IGNBRK | BRKINT | PARMRK | ISTRIP | INLCR | IGNCR | ICRNL | IXON);
      newAttributes.c_lflag &= ~(ECHO | ECHONL | ICANON | ISIG | IEXTEN);
      newAttributes.c_cflag &= ~(CSIZE | PARENB);
      newAttributes.c_cflag |= CS8;

      {
        for (int i=0; i<NCCS; i+=1) newAttributes.c_cc[i] = _POSIX_VDISABLE;
      }

      newAttributes.c_cc[VTIME] = 0;
      newAttributes.c_cc[VMIN] = 1;

      if (tcsetattr(STDIN_FILENO, TCSAFLUSH, &newAttributes) != -1) {
        inputAttributesChanged = 1;
      };
    }
#endif /* input terminal initialization */
#endif /* initialize keyboard and screen */

    etd.charset = *opt_charset? opt_charset: NULL;
    setFirstDefinedCharacter(&etd);

    while (updateCharacterDescription(&etd)) {
      fd_set set;
      FD_ZERO(&set);

#ifndef __MINGW32__
      {
        int maximumFileDescriptor = STDIN_FILENO;
        FD_SET(STDIN_FILENO, &set);

#ifdef ENABLE_API
        if (haveBrailleDisplay(&etd)) {
          FD_SET(etd.brlapiFileDescriptor, &set);

          if (etd.brlapiFileDescriptor > maximumFileDescriptor) {
            maximumFileDescriptor = etd.brlapiFileDescriptor;
          }
        }
#endif /* ENABLE_API */

        select(maximumFileDescriptor+1, &set, NULL, NULL, NULL);
      }

#ifdef ENABLE_API
      if (haveBrailleDisplay(&etd) && FD_ISSET(etd.brlapiFileDescriptor, &set)) {
        if (!doBrailleCommand(&etd)) break;
      }
#endif /* ENABLE_API */

      if (FD_ISSET(STDIN_FILENO, &set))
#endif /* __MINGW32__ */
      {
        if (!doKeyboardCommand(&etd)) break;
      }
    }

    erase();
    refresh();

#if defined(GOT_CURSES)
    endwin();
#else /* standard input/output */
    if (inputAttributesChanged) {
#if defined(__MINGW32__)
      SetConsoleMode(STDIN_HANDLE, inputConsoleMode);
#else /* termios */
      tcsetattr(STDIN_FILENO, TCSAFLUSH, &inputTerminalAttributes);
#endif /* input terminal restoration */
    }
#endif /* restore keyboard and screen */

#ifdef ENABLE_API
    if (haveBrailleDisplay(&etd)) releaseBrailleDisplay(&etd);
#endif /* ENABLE_API */

    if (etd.ttd) destroyTextTableData(etd.ttd);
  }

  return exitStatus;
}

int
main (int argc, char *argv[]) {
  ProgramExitStatus exitStatus;

  {
    const CommandLineDescriptor descriptor = {
      .options = &programOptions,
      .applicationName = "brltty-ttb",

      .usage = {
        .purpose = strtext("Check/edit a text (computer braille) table, or convert it from one format to another."),
        .parameters = "input-table [output-table]",
      }
    };

    PROCESS_OPTIONS(descriptor, argc, argv);
  }

  if (argc == 0) {
    logMessage(LOG_ERR, "missing input table");
    return PROG_EXIT_SYNTAX;
  }
  inputPath = *argv++, argc--;

  if (argc > 0) {
    outputPath = *argv++, argc--;
  } else if (opt_outputFormat && *opt_outputFormat) {
    const char *extension = locatePathExtension(inputPath);
    int prefix = extension? (extension - inputPath): strlen(inputPath);
    char buffer[prefix + 1 + strlen(opt_outputFormat) + 1];

    snprintf(buffer, sizeof(buffer), "%.*s.%s", prefix, inputPath, opt_outputFormat);

    if (!(outputPath = strdup(buffer))) {
      logMallocError();
      return PROG_EXIT_FATAL;
    }
  } else {
    outputPath = NULL;
  }

  if (argc > 0) {
    logMessage(LOG_ERR, "too many parameters");
    return PROG_EXIT_SYNTAX;
  }

  inputFormat = getFormatEntry(opt_inputFormat, inputPath, "input");

  if (outputPath) {
    outputFormat = getFormatEntry(opt_outputFormat, outputPath, "output");
  } else {
    outputFormat = NULL;
  }

  if (*opt_charset && !setCharset(opt_charset)) {
    logMessage(LOG_ERR, "cannot establish character set: %s", opt_charset);
    return PROG_EXIT_SEMANTIC;
  }

  if (opt_edit) {
    exitStatus = editTable();
  } else {
    exitStatus = convertTable();
  }

  return exitStatus;
}
