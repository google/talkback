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
#include <locale.h>

#include <limits.h>
#ifndef MB_LEN_MAX
#define MB_LEN_MAX 16
#endif /* MB_LEN_MAX */

#ifdef HAVE_ICONV_H
#include <iconv.h>
static iconv_t conversionDescriptor = NULL;
#endif /* HAVE_ICONV_H */

#include "log.h"
#include "parse.h"
#include "charset.h"
#include "unicode.h"
#include "get_curses.h"

#ifdef GOT_CURSES
#define newLine() addch('\n')
#else /* GOT_CURSES */
#define addstr(string) serialWriteData(ttyDevice, string, strlen(string))
#define addch(character) do { unsigned char __c = (character); serialWriteData(ttyDevice, &__c, 1); } while(0)
#define getch() getch_noCurses()
#define newLine() addstr("\r\n")
#endif /* GOT_CURSES */

#ifdef GOT_CURSES
#define BRLPARM_TERM "term",
#else /* GOT_CURSES */
#define BRLPARM_TERM
#endif /* GOT_CURSES */

#ifdef HAVE_ICONV_H
#define BRLPARM_CHARSET "charset",
#else /* HAVE_ICONV_H */
#define BRLPARM_CHARSET
#endif /* HAVE_ICONV_H */

typedef enum {
  PARM_BAUD,

#ifdef GOT_CURSES
  PARM_TERM,
#endif /* GOT_CURSES */

  PARM_LINES,
  PARM_COLUMNS,

#ifdef HAVE_ICONV_H
  PARM_CHARSET,
#endif /* HAVE_ICONV_H */

  PARM_LOCALE
} DriverParameter;
#define BRLPARMS "baud", BRLPARM_TERM "lines", "columns", BRLPARM_CHARSET "locale"

#include "brl_driver.h"
#include "braille.h"
#include "io_serial.h"

#define MAX_WINDOW_LINES 3
#define MAX_WINDOW_COLUMNS 80
#define MAX_WINDOW_SIZE (MAX_WINDOW_LINES * MAX_WINDOW_COLUMNS)

static SerialDevice *ttyDevice = NULL;
static FILE *ttyStream = NULL;
static char *classificationLocale = NULL;

#ifdef GOT_CURSES
static SCREEN *ttyScreen = NULL;
#else /* GOT_CURSES */
static inline int
getch_noCurses (void) {
  unsigned char c;
  if (serialReadData(ttyDevice, &c, 1, 0, 0) == 1) return c;
  return EOF;
}
#endif /* GOT_CURSES */

static int
brl_construct (BrailleDisplay *brl, char **parameters, const char *device) {
  unsigned int ttyBaud = 9600;
  const char *ttyType = "vt100";
  int windowLines = 1;
  int windowColumns = 40;

#ifdef HAVE_ICONV_H
  const char *characterSet = getLocaleCharset();
#endif /* HAVE_ICONV_H */

  if (!isSerialDeviceIdentifier(&device)) {
    unsupportedDeviceIdentifier(device);
    return 0;
  }

  {
    unsigned int baud = ttyBaud;

    if (serialValidateBaud(&baud, "TTY baud", parameters[PARM_BAUD], NULL)) {
      ttyBaud = baud;
    }
  }

#ifdef GOT_CURSES
  if (*parameters[PARM_TERM]) {
    ttyType = parameters[PARM_TERM];
  }
#endif /* GOT_CURSES */

  {
    static const int minimum = 1;
    static const int maximum = MAX_WINDOW_LINES;
    int lines = windowLines;

    if (validateInteger(&lines, parameters[PARM_LINES], &minimum, &maximum)) {
      windowLines = lines;
    } else {
      logMessage(LOG_WARNING, "%s: %s", "invalid line count", parameters[PARM_LINES]);
    }
  }

  {
    static const int minimum = 1;
    static const int maximum = MAX_WINDOW_COLUMNS;
    int columns = windowColumns;

    if (validateInteger(&columns, parameters[PARM_COLUMNS], &minimum, &maximum)) {
      windowColumns = columns;
    } else {
      logMessage(LOG_WARNING, "%s: %s", "invalid column count", parameters[PARM_COLUMNS]);
    }
  }

#ifdef HAVE_ICONV_H
  if (*parameters[PARM_CHARSET]) {
    characterSet = parameters[PARM_CHARSET];
  }
#endif /* HAVE_ICONV_H */

  if (*parameters[PARM_LOCALE]) {
    classificationLocale = parameters[PARM_LOCALE];
  }

#ifdef HAVE_ICONV_H
  if ((conversionDescriptor = iconv_open(characterSet, "WCHAR_T")) != (iconv_t)-1) {
#endif /* HAVE_ICONV_H */
    if ((ttyDevice = serialOpenDevice(device))) {
      if (serialRestartDevice(ttyDevice, ttyBaud)) {
#ifdef GOT_CURSES
        if ((ttyStream = serialGetStream(ttyDevice))) {
          if ((ttyScreen = newterm(ttyType, ttyStream, ttyStream))) {
            cbreak();
            noecho();
            nonl();

            nodelay(stdscr, TRUE);
            intrflush(stdscr, FALSE);
            keypad(stdscr, TRUE);

            clear();
            refresh();
#endif /* GOT_CURSES */

            brl->textColumns = windowColumns;
            brl->textRows = windowLines; 

            logMessage(LOG_INFO, "TTY: type=%s baud=%u size=%dx%d",
                       ttyType, ttyBaud, windowColumns, windowLines);
            return 1;
#ifdef GOT_CURSES
          } else {
            logSystemError("newterm");
          }

          ttyStream = NULL;
        }
#endif /* GOT_CURSES */
      }

      serialCloseDevice(ttyDevice);
      ttyDevice = NULL;
    }

#ifdef HAVE_ICONV_H
    iconv_close(conversionDescriptor);
  } else {
    logSystemError("iconv_open");
  }

  conversionDescriptor = NULL;
#endif /* HAVE_ICONV_H */

  return 0;
}

static void
brl_destruct (BrailleDisplay *brl) {
#ifdef GOT_CURSES
  if (ttyScreen) {
    endwin();

#ifndef __MINGW32__
    delscreen(ttyScreen);
#endif /* __MINGW32__ */

    ttyScreen = NULL;
  }
#endif /* GOT_CURSES */

  if (ttyDevice) {
    ttyStream = NULL;
    serialCloseDevice(ttyDevice);
    ttyDevice = NULL;
  }

#ifdef HAVE_ICONV_H
  if (conversionDescriptor) {
    iconv_close(conversionDescriptor);
    conversionDescriptor = NULL;
  }
#endif /* HAVE_ICONV_H */
}

static void
writeText (const wchar_t *buffer, int columns) {
  int column;
  for (column=0; column<columns; column++) {
    wchar_t c = buffer[column];

#ifdef HAVE_ICONV_H
    char *pc = (char*) &c;
    size_t sc = sizeof(wchar_t);
    char d[MB_LEN_MAX+1];
    char *pd = d;
    size_t sd = MB_LEN_MAX;

    if (iconv(conversionDescriptor, &pc, &sc, &pd, &sd) != (size_t)-1) {
      *pd = 0;
      addstr(d);
    } else
#endif /* HAVE_ICONV_H */
    {
      addch(c);
    }
  }
}

static int
brl_writeWindow (BrailleDisplay *brl, const wchar_t *text) {
  static unsigned char previousContent[MAX_WINDOW_SIZE];
  static int previousCursor = -1;
  char *previousLocale;

  if (!cellsHaveChanged(previousContent, brl->buffer, brl->textColumns*brl->textRows, NULL, NULL, NULL) &&
      (brl->cursor == previousCursor)) {
    return 1;
  }

  previousCursor = brl->cursor;

  if (classificationLocale) {
    previousLocale = setlocale(LC_CTYPE, NULL);
    setlocale(LC_CTYPE, classificationLocale);
  } else {
    previousLocale = NULL;
  }

#ifdef GOT_CURSES
  clear();
#else /* GOT_CURSES */
  newLine();
#endif /* GOT_CURSES */

  {
    wchar_t braille[brl->textColumns];

    for (unsigned int row=0; row<brl->textRows; row++) {
      unsigned int offset = row * brl->textColumns;
      writeText(&text[offset], brl->textColumns);

      for (unsigned int column=0; column<brl->textColumns; column+=1) {
        unsigned char c = brl->buffer[offset + column];
        braille[column] = UNICODE_BRAILLE_ROW
                        | (!!(c & BRL_DOT1) << 0)
                        | (!!(c & BRL_DOT2) << 1)
                        | (!!(c & BRL_DOT3) << 2)
                        | (!!(c & BRL_DOT4) << 3)
                        | (!!(c & BRL_DOT5) << 4)
                        | (!!(c & BRL_DOT6) << 5)
                        | (!!(c & BRL_DOT7) << 6)
                        | (!!(c & BRL_DOT8) << 7)
                        ;
      }

      newLine();
      writeText(braille, brl->textColumns);

      if (row < (brl->textRows - 1)) {
        newLine();
      }
    }
  }

#ifdef GOT_CURSES
  if ((brl->cursor != BRL_NO_CURSOR) && (brl->cursor < (brl->textColumns * brl->textRows))) {
    move(brl->cursor/brl->textColumns, brl->cursor%brl->textColumns);
  } else {
    move(brl->textRows, 0);
  }

  refresh();
#else /* GOT_CURSES */
  if ((brl->textRows == 1) && (brl->cursor != BRL_NO_CURSOR) && (brl->cursor < brl->textColumns)) {
    addch('\r');
    writeText(text, brl->cursor);
  } else {
    newLine();
  }
#endif /* GOT_CURSES */

  if (previousLocale) setlocale(LC_CTYPE, previousLocale);
  return 1;
}

static int
keyToCommand (BrailleDisplay *brl, KeyTableCommandContext context, int key) {
  switch (key) {
    case EOF: return EOF;

    default:
      if (key <= 0XFF) return BRL_CMD_CHAR(key);
      logMessage(LOG_WARNING, "unrecognized curses key: %d", key);
      return BRL_CMD_NOOP;

#ifdef GOT_CURSES
#define MAP(key,cmd) case KEY_##key: return BRL_CMD_##cmd
    MAP(BACKSPACE, KEY(BACKSPACE));

    MAP(LEFT, FWINLT);
    MAP(RIGHT, FWINRT);
    MAP(UP, LNUP);
    MAP(DOWN, LNDN);

    MAP(PPAGE, PRDIFLN);
    MAP(NPAGE, NXDIFLN);
    MAP(A3, PRDIFLN);
    MAP(C3, NXDIFLN);

    MAP(HOME, TOP);
    MAP(END, BOT);
    MAP(A1, TOP);
    MAP(C1, BOT);

    MAP(IC, ATTRUP);
    MAP(DC, ATTRDN);
    MAP(B2, HOME);

    MAP(F(1), HELP);
    MAP(F(2), LEARN);
    MAP(F(3), INFO);
    MAP(F(4), PREFMENU);

    MAP(F(5), PRPROMPT);
    MAP(F(6), NXPROMPT);
    MAP(F(7), PRPGRPH);
    MAP(F(8), NXPGRPH);

    MAP(F(9), LNBEG);
    MAP(F(10), CHRLT);
    MAP(F(11), CHRRT);
    MAP(F(12), LNEND);
#undef MAP
#endif /* GOT_CURSES */
  }
}

static int
readKey (BrailleDisplay *brl) {
  int key = getch();

#ifdef GOT_CURSES
  if (key == ERR) return EOF;
#endif /* GOT_CURSES */

  if (key != EOF) {
    logMessage(LOG_CATEGORY(BRAILLE_DRIVER), "curses key: %d", key);
  }

  return key;
}

static int
brl_readCommand (BrailleDisplay *brl, KeyTableCommandContext context) {
  int command = keyToCommand(brl, context, readKey(brl));

  if (command != EOF) {
    logMessage(LOG_CATEGORY(BRAILLE_DRIVER), "command: 0X%04X", command);
  }

  return command;
}
