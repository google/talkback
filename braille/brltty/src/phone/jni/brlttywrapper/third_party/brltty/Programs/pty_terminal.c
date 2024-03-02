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

/* unimplemented output actions
 * enacs=\E(B\E)0 - enable alternate charset mode
 * hts=\EH - set tab
 * kmous=\E[M - mouse event
 * tbc=\E[3g - clear all tabs
 * u6=\E[%i%d;%dR - user string 6
 * u7=\E[6n - user string 7
 * u8=\E[?1;2c - user string 8
 * u9=\E[c - user string 9
 */

#include "prologue.h"

#include <string.h>

#include "log.h"
#include "strfmt.h"
#include "pty_terminal.h"
#include "pty_screen.h"
#include "scr_types.h"
#include "ascii.h"

static unsigned char terminalLogLevel = LOG_DEBUG;
static unsigned char logInput = 0;
static unsigned char logOutput = 0;
static unsigned char logSequences = 0;
static unsigned char logUnexpected = 0;

void
ptySetTerminalLogLevel (unsigned char level) {
  terminalLogLevel = level;
  ptySetScreenLogLevel(level);
}

void
ptySetLogTerminalInput (int yes) {
  logInput = yes;
}

void
ptySetLogTerminalOutput (int yes) {
  logOutput = yes;
}

void
ptySetLogTerminalSequences (int yes) {
  logSequences = yes;
}

void
ptySetLogUnexpectedTerminalIO (int yes) {
  logUnexpected = yes;
}

static const char ptyTerminalType[] = "screen";

const char *
ptyGetTerminalType (void) {
  return ptyTerminalType;
}

static unsigned char insertMode = 0;
static unsigned char alternateCharsetMode = 0;
static unsigned char keypadTransmitMode = 0;
static unsigned char bracketedPasteMode = 0;
static unsigned char absoluteCursorAddressingMode = 0;

int
ptyBeginTerminal (PtyObject *pty, int driverDirectives) {
  insertMode = 0;
  alternateCharsetMode = 0;
  keypadTransmitMode = 0;
  bracketedPasteMode = 0;
  absoluteCursorAddressingMode = 0;

  return ptyBeginScreen(pty, driverDirectives);
}

void
ptyEndTerminal (void) {
  ptyEndScreen();
}

static void
soundAlert (void) {
  beep();
}

static void
showAlert (void) {
  flash();
}

int
ptyProcessTerminalInput (PtyObject *pty) {
  int character = getch();

  if (logInput) {
    const char *name = keyname(character);
    if (!name) name = "unknown";
    logMessage(terminalLogLevel, "input: 0X%02X (%s)", character, name);
  }

  if (character > UINT8_MAX) {
    ScreenKey key = 0;

    #define KEY(from, to) case KEY_##from: key = SCR_KEY_##to; break;
    switch (character) {
      KEY(ENTER    , ENTER)
      KEY(BACKSPACE, BACKSPACE)

      KEY(LEFT     , CURSOR_LEFT)
      KEY(RIGHT    , CURSOR_RIGHT)
      KEY(UP       , CURSOR_UP)
      KEY(DOWN     , CURSOR_DOWN)

      KEY(PPAGE    , PAGE_UP)
      KEY(NPAGE    , PAGE_DOWN)
      KEY(HOME     , HOME)
      KEY(END      , END)
      KEY(IC       , INSERT)
      KEY(DC       , DELETE)

      KEY(F( 1)    , F1)
      KEY(F( 2)    , F2)
      KEY(F( 3)    , F3)
      KEY(F( 4)    , F4)
      KEY(F( 5)    , F5)
      KEY(F( 6)    , F6)
      KEY(F( 7)    , F7)
      KEY(F( 8)    , F8)
      KEY(F( 9)    , F9)
      KEY(F(10)    , F10)
      KEY(F(11)    , F11)
      KEY(F(12)    , F12)
    }
    #undef KEY

    if (key) {
      if (!ptyWriteInputCharacter(pty, key, keypadTransmitMode)) {
        return 0;
      }
    } else if (logUnexpected) {
      const char *name = keyname(character);
      if (!name) name = "unknown";
      logMessage(terminalLogLevel, "unexpected input: 0X%02X (%s)", character, name);
    }

    return 1;
  }

  char byte = character;
  return ptyWriteInputData(pty, &byte,1);
}

static unsigned char outputByteBuffer[0X40];
static unsigned char outputByteCount;

static void
logUnexpectedSequence (void) {
  if (logUnexpected) {
    logBytes(
      terminalLogLevel, "unexpected sequence",
      outputByteBuffer, outputByteCount
    );
  }
}

typedef enum {
  OPS_BASIC,
  OPS_ESCAPE,
  OPS_BRACKET,
  OPS_NUMBER,
  OPS_DIGIT,
  OPS_ACTION,
} OutputParserState;

static OutputParserState outputParserState;
static unsigned char outputParserQuestionMark;

static unsigned int outputParserNumber;
static unsigned int outputParserNumberArray[9];
static unsigned char outputParserNumberCount;

static void
addOutputParserNumber (unsigned int number) {
  if (outputParserNumberCount < ARRAY_COUNT(outputParserNumberArray)) {
    outputParserNumberArray[outputParserNumberCount++] = number;
  }
}

static unsigned int
getOutputActionCount (void) {
  if (outputParserNumberCount == 0) return 1;
  return outputParserNumberArray[0];
}

static void
logOutputAction (const char *name, const char *description) {
  if (logSequences) {
    char prefix[0X100];
    STR_BEGIN(prefix, sizeof(prefix));
    STR_PRINTF("action: %s", name);

    for (unsigned int i=0; i<outputParserNumberCount; i+=1) {
      STR_PRINTF(" %u", outputParserNumberArray[i]);
    }

    if (description && *description) STR_PRINTF(" (%s)", description);
    STR_END;
    logBytes(terminalLogLevel, "%s", outputByteBuffer, outputByteCount, prefix);
  }
}

typedef enum {
  OBP_DONE,
  OBP_CONTINUE,
  OBP_REPROCESS,
  OBP_UNEXPECTED,
} OutputByteParserResult;

typedef OutputByteParserResult OutputByteParser (unsigned char byte);

static OutputByteParserResult
parseOutputByte_BASIC (unsigned char byte) {
  outputParserQuestionMark = 0;
  outputParserNumberCount = 0;

  switch (byte) {
    case ASCII_ESC:
      outputParserState = OPS_ESCAPE;
      return OBP_CONTINUE;

    case ASCII_BEL:
      logOutputAction("bel", "audible alert");
      soundAlert();
      return OBP_DONE;

    case ASCII_BS:
      logOutputAction("cub1", "cursor left 1");
      ptyMoveCursorLeft(1);
      return OBP_DONE;

    case ASCII_HT:
      logOutputAction("ht", "tab forward");
      ptyTabForward();
      return OBP_DONE;

    case ASCII_LF:
      if (ptyAmWithinScrollRegion()) {
        logOutputAction("ind", "move down 1");
        ptyMoveDown1();
      } else {
        logOutputAction("cud1", "cursor down 1");
        ptyMoveCursorDown(1);
      }
      return OBP_DONE;

    case ASCII_CR:
      logOutputAction("cr", "carriage return");
      ptySetCursorColumn(0);
      return OBP_DONE;

    case ASCII_SO:
      logOutputAction("smacs", "alternate charset on");
      alternateCharsetMode = 1;
      return OBP_DONE;

    case ASCII_SI:
      logOutputAction("rmacs", "alternate charset off");
      alternateCharsetMode = 0;
      return OBP_DONE;

    default: {
      if (logOutput) {
        logMessage(terminalLogLevel, "output: 0X%02X", byte);
      }

      if (insertMode) ptyInsertCharacters(1);
      ptyAddCharacter(byte);
      return OBP_DONE;
    }
  }
}

static OutputByteParserResult
parseOutputByte_ESCAPE (unsigned char byte) {
  switch (byte) {
    case '[':
      outputParserState = OPS_BRACKET;
      return OBP_CONTINUE;

    case '=':
      logOutputAction("smkx", "keypad transmit on");
      keypadTransmitMode = 1;
      return OBP_DONE;

    case '>':
      logOutputAction("rmkx", "keypad transmit off");
      keypadTransmitMode = 0;
      return OBP_DONE;

    case 'E':
      logOutputAction("nel", "new line");
      ptySetCursorColumn(0);
      ptyMoveDown1();
      return OBP_DONE;

    case 'M':
      if (ptyAmWithinScrollRegion()) {
        logOutputAction("ri", "move up 1");
        ptyMoveUp1();
      } else {
        logOutputAction("cuu1", "cursor up 1");
        ptyMoveCursorUp(1);
      }
      return OBP_DONE;

    case 'c':
      logOutputAction("clear", "clear screen");
      ptySetCursorPosition(0, 0);
      ptyClearToEndOfDisplay();
      return OBP_DONE;

    case 'g':
      logOutputAction("flash", "visual alert");
      showAlert();
      return OBP_DONE;

    case '7':
      logOutputAction("sc", "save cursor position");
      ptySaveCursorPosition();
      return OBP_DONE;

    case '8':
      logOutputAction("rc", "restore cursor position");
      ptyRestoreCursorPosition();
      return OBP_DONE;
  }

  return OBP_UNEXPECTED;
}

static OutputByteParserResult
parseOutputByte_BRACKET (unsigned char byte) {
  outputParserState = OPS_NUMBER;
  if (outputParserQuestionMark) return OBP_REPROCESS;
  if (byte != '?') return OBP_REPROCESS;

  outputParserQuestionMark = 1;
  outputParserState = OPS_BRACKET;
  return OBP_CONTINUE;
}

static OutputByteParserResult
parseOutputByte_NUMBER (unsigned char byte) {
  if (iswdigit(byte)) {
    outputParserNumber = 0;
    outputParserState = OPS_DIGIT;
  } else {
    outputParserState = OPS_ACTION;
  }

  return OBP_REPROCESS;
}

static OutputByteParserResult
parseOutputByte_DIGIT (unsigned char byte) {
  if (iswdigit(byte)) {
    outputParserNumber *= 10;
    outputParserNumber += byte - '0';
    return OBP_CONTINUE;
  }

  addOutputParserNumber(outputParserNumber);
  outputParserNumber = 0;
  if (byte == ';') return OBP_CONTINUE;

  outputParserState = OPS_ACTION;
  return OBP_REPROCESS;
}

static OutputByteParserResult
performBracketAction_h (unsigned char byte) {
  if (outputParserNumberCount == 1) {
    switch (outputParserNumberArray[0]) {
      case 4:
        logOutputAction("smir", "insert on");
        insertMode = 1;
        return OBP_DONE;

      case 34:
        logOutputAction("cnorm", "cursor normal visibility");
        ptySetCursorVisibility(1);
        return OBP_DONE;
    }
  }

  return OBP_UNEXPECTED;
}

static OutputByteParserResult
performBracketAction_l (unsigned char byte) {
  if (outputParserNumberCount == 1) {
    switch (outputParserNumberArray[0]) {
      case 4:
        logOutputAction("rmir", "insert off");
        insertMode = 0;
        return OBP_DONE;

      case 34:
        logOutputAction("cvvis", "cursor very visile");
        ptySetCursorVisibility(2);
        return OBP_DONE;
    }
  }

  return OBP_UNEXPECTED;
}

static OutputByteParserResult
performBracketAction_m (unsigned char byte) {
  if (outputParserNumberCount == 0) addOutputParserNumber(0);

  for (unsigned int index=0; index<outputParserNumberCount; index+=1) {
    unsigned int number = outputParserNumberArray[index];

    switch (number / 10) {
      {
        const char *name;
        const char *description;
        void (*setColor) (int color);
        int color;

      case 3:
        name = "setaf";
        description = "foreground color";
        setColor = ptySetForegroundColor;
        goto doColor;

      case 4:
        name = "setab";
        description = "background color";
        setColor = ptySetBackgroundColor;
        goto doColor;

      doColor:
        color = number % 10;
        if (color == 8) return OBP_UNEXPECTED;
        if (color == 9) color = -1;

        logOutputAction(name, description);
        setColor(color);
        continue;
      }
    }

    switch (number) {
      case 0:
        logOutputAction("sgr0", "all attributes off");
        ptySetAttributes(0);
        continue;

      case 1:
        logOutputAction("bold", "bold on");
        ptyAddAttributes(A_BOLD);
        continue;

      case 2:
        logOutputAction("dim", "dim on");
        ptyAddAttributes(A_DIM);
        continue;

      case 3:
        logOutputAction("smso", "standout on");
        ptyAddAttributes(A_STANDOUT);
        continue;

      case 4:
        logOutputAction("smul", "underline on");
        ptyAddAttributes(A_UNDERLINE);
        continue;

      case 5:
        logOutputAction("blink", "blink on");
        ptyAddAttributes(A_BLINK);
        continue;

      case 7:
        logOutputAction("rev", "reverse video on");
        ptyAddAttributes(A_REVERSE);
        continue;

      case 22:
        logOutputAction("normal", "bold/dim off");
        ptyRemoveAttributes(A_BOLD | A_DIM);
        continue;

      case 23:
        logOutputAction("rmso", "standout off");
        ptyRemoveAttributes(A_STANDOUT);
        continue;

      case 24:
        logOutputAction("rmul", "underline off");
        ptyRemoveAttributes(A_UNDERLINE);
        continue;

      case 25:
        logOutputAction("unblink", "blink off");
        ptyRemoveAttributes(A_BLINK);
        continue;

      case 27:
        logOutputAction("unrev", "reverse video off");
        ptyRemoveAttributes(A_REVERSE);
        continue;
    }

    return OBP_UNEXPECTED;
  }

  return OBP_DONE;
}

static OutputByteParserResult
performBracketAction (unsigned char byte) {
  switch (byte) {
    case 'A':
      logOutputAction("cuu", "cursor up");
      ptyMoveCursorUp(getOutputActionCount());
      return OBP_DONE;

    case 'B':
      logOutputAction("cud", "cursor down");
      ptyMoveCursorDown(getOutputActionCount());
      return OBP_DONE;

    case 'C':
      logOutputAction("cuf", "cursor right");
      ptyMoveCursorRight(getOutputActionCount());
      return OBP_DONE;

    case 'D':
      logOutputAction("cub", "cursor left");
      ptyMoveCursorLeft(getOutputActionCount());
      return OBP_DONE;

    case 'G': {
      if (outputParserNumberCount != 1) return OBP_UNEXPECTED   ;
      unsigned int *column = &outputParserNumberArray[0];
      if (!(*column)--) return OBP_UNEXPECTED;

      logOutputAction("hpa", "set cursor column");
      ptySetCursorColumn(*column);
      return OBP_DONE;
    }

    case 'H': {
      if (outputParserNumberCount == 0) {
        addOutputParserNumber(1);
        addOutputParserNumber(1);
      } else if (outputParserNumberCount != 2) {
        return OBP_UNEXPECTED;
      }

      unsigned int *row = &outputParserNumberArray[0];
      unsigned int *column = &outputParserNumberArray[1];

      if (!(*row)--) return OBP_UNEXPECTED;
      if (!(*column)--) return OBP_UNEXPECTED;

      logOutputAction("cup", "set cursor position");
      ptySetCursorPosition(*row, *column);
      return OBP_DONE;
    }

    case 'J':
      if (outputParserNumberCount != 0) return OBP_UNEXPECTED;
      logOutputAction("ed", "clear to end of display");
      ptyClearToEndOfDisplay();
      return OBP_DONE;

    case 'K': {
      if (outputParserNumberCount == 0) addOutputParserNumber(0);
      if (outputParserNumberCount != 1) return OBP_UNEXPECTED;

      switch (outputParserNumberArray[0]) {
        case 0:
          logOutputAction("el", "clear to end of line");
          ptyClearToEndOfLine();
          return OBP_DONE;

        case 1:
          logOutputAction("el1", "clear to beginning of line");
          ptyClearToBeginningOfLine();
          return OBP_DONE;
      }

      break;
    }

    case 'L':
      logOutputAction("il", "insert lines");
      ptyInsertLines(getOutputActionCount());
      return OBP_DONE;

    case 'M':
      logOutputAction("dl", "delete lines");
      ptyDeleteLines(getOutputActionCount());
      return OBP_DONE;

    case 'P':
      logOutputAction("dch", "delete characters");
      ptyDeleteCharacters(getOutputActionCount());
      return OBP_DONE;

    case 'S':
      logOutputAction("indn", "scroll forward");
      ptyScrollUp(getOutputActionCount());
      return OBP_DONE;

    case 'T':
      logOutputAction("rin", "scroll backward");
      ptyScrollDown(getOutputActionCount());
      return OBP_DONE;

    case 'Z':
      logOutputAction("cbt", "tab backward");
      ptyTabBackward();
      return OBP_DONE;

    case 'd': {
      if (outputParserNumberCount != 1) return OBP_UNEXPECTED   ;
      unsigned int *row = &outputParserNumberArray[0];
      if (!(*row)--) return OBP_UNEXPECTED;

      logOutputAction("vpa", "set cursor row");
      ptySetCursorRow(*row);
      return OBP_DONE;
    }

    case 'h':
      return performBracketAction_h(byte);

    case 'l':
      return performBracketAction_l(byte);

    case 'm':
      return performBracketAction_m(byte);

    case 'r': {
      if (outputParserNumberCount != 2) return OBP_UNEXPECTED;

      unsigned int *top = &outputParserNumberArray[0];
      unsigned int *bottom = &outputParserNumberArray[1];

      if (!(*top)--) return OBP_UNEXPECTED;
      if (!(*bottom)--) return OBP_UNEXPECTED;

      logOutputAction("csr", "set scroll region");
      ptySetScrollRegion(*top, *bottom);
      return OBP_DONE;
    }

    case '@':
      logOutputAction("ic", "insert characters");
      ptyInsertCharacters(getOutputActionCount());
      return OBP_DONE;
  }

  return OBP_UNEXPECTED;
}

static OutputByteParserResult
performQuestionMarkAction_h (unsigned char byte) {
  if (outputParserNumberCount == 1) {
    switch (outputParserNumberArray[0]) {
      case 1:
        logOutputAction("smkx", "keypad transmit on");
        keypadTransmitMode = 1;
        return OBP_DONE;

      case 25:
        logOutputAction("cnorm", "cursor normal visibility");
        ptySetCursorVisibility(1);
        return OBP_DONE;

      case 1049:
        logOutputAction("smcup", "absolute cursor addressing on");
        absoluteCursorAddressingMode = 1;
        return OBP_DONE;

      case 2004:
        logOutputAction("smbp", "bracketed paste on");
        bracketedPasteMode = 1;
        return OBP_DONE;
    }
  }

  return OBP_UNEXPECTED;
}

static OutputByteParserResult
performQuestionMarkAction_l (unsigned char byte) {
  if (outputParserNumberCount == 1) {
    switch (outputParserNumberArray[0]) {
      case 1:
        logOutputAction("rmkx", "keypad transmit off");
        keypadTransmitMode = 0;
        return OBP_DONE;

      case 25:
        logOutputAction("civis", "cursor invisible");
        ptySetCursorVisibility(0);
        return OBP_DONE;

      case 1049:
        logOutputAction("rmcup", "absolute cursor addressing off");
        absoluteCursorAddressingMode = 0;
        return OBP_DONE;

      case 2004:
        logOutputAction("rmbp", "bracketed paste off");
        bracketedPasteMode = 0;
        return OBP_DONE;
    }
  }

  return OBP_UNEXPECTED;
}

static OutputByteParserResult
performQuestionMarkAction (unsigned char byte) {
  switch (byte) {
    case 'h':
      return performQuestionMarkAction_h(byte);

    case 'l':
      return performQuestionMarkAction_l(byte);
  }

  return OBP_UNEXPECTED;
}

static OutputByteParserResult
parseOutputByte_ACTION (unsigned char byte) {
  if (outputParserQuestionMark) {
    return performQuestionMarkAction(byte);
  } else {
    return performBracketAction(byte);
  }
}

typedef struct {
  OutputByteParser *parseOutputByte;
  const char *name;
} OutputParserStateEntry;

#define OPS(state) \
[OPS_##state] = { \
  .parseOutputByte = parseOutputByte_##state, \
  .name = #state, \
}

static const OutputParserStateEntry outputParserStateTable[] = {
  OPS(BASIC),
  OPS(ESCAPE),
  OPS(BRACKET),
  OPS(NUMBER),
  OPS(DIGIT),
  OPS(ACTION),
};
#undef OPS

static int
parseOutputByte (unsigned char byte) {
  if (outputParserState == OPS_BASIC) {
    outputByteCount = 0;
  }

  if (outputByteCount < ARRAY_COUNT(outputByteBuffer)) {
    outputByteBuffer[outputByteCount++] = byte;
  }

  while (1) {
    OutputByteParserResult result = outputParserStateTable[outputParserState].parseOutputByte(byte);

    switch (result) {
      case OBP_REPROCESS:
        continue;

      case OBP_UNEXPECTED:
        logUnexpectedSequence();
        /* fall through */

      case OBP_DONE:
        outputParserState = OPS_BASIC;
        return 1;

      case OBP_CONTINUE:
        return 0;
    }
  }
}

int
ptyProcessTerminalOutput (const unsigned char *bytes, size_t count) {
  int wantRefresh = 0;

  const unsigned char *byte = bytes;
  const unsigned char *end = byte + count;

  while (byte < end) {
    wantRefresh = parseOutputByte(*byte++);
  }

  if (wantRefresh) {
    ptyRefreshScreen();
  }

  return 1;
}
