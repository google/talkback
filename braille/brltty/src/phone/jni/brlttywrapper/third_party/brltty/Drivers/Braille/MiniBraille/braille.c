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

/* MiniBraille/braille.c - Braille display library
 * the following Tieman B.V. braille terminals are supported
 *
 * - MiniBraille v 1.5 (20 braille cells + 2 status)
 *   (probably other versions too)
 *
 * Brailcom o.p.s. <technik@brailcom.cz>
 *
 * Thanks to Tieman B.V., which gives me protocol information. Author.
 */

#include "prologue.h"

#include <stdio.h>
#include <string.h>
#include <time.h>

#include "log.h"
#include "timing.h"
#include "ascii.h"
#include "message.h"

#define BRL_STATUS_FIELDS sfCursorAndWindowColumn, sfCursorAndWindowRow, sfStateDots
#define BRL_HAVE_STATUS_CELLS
#include "brl_driver.h"

#include "io_serial.h"
static SerialDevice *serialDevice = NULL;
static const unsigned int serialBaud = 9600;
static unsigned int serialCharactersPerSecond;

#define KEY_F1     0x01
#define KEY_F2     0x02
#define KEY_LEFT   0x04
#define KEY_UP     0x08
#define KEY_CENTER 0x10
#define KEY_DOWN   0x20
#define KEY_RIGHT  0x40

#define POST_COMMAND_DELAY 30

static unsigned char textCells[20];
static unsigned char statusCells[2];
static int refreshNeeded;

static int
writeData (BrailleDisplay *brl, const unsigned char *bytes, int count) {
  ssize_t result = serialWriteData(serialDevice, bytes, count);

  if (result == -1) {
    logSystemError("write");
    return 0;
  }

  drainBrailleOutput(brl, 0);
  brl->writeDelay += (result * 1000 / serialCharactersPerSecond) + POST_COMMAND_DELAY;
  return 1;
}

static int
writeCells  (BrailleDisplay *brl) {
  static const unsigned char beginSequence[] = {ESC, 'Z', '1'};
  static const unsigned char endSequence[] = {CR};

  unsigned char buffer[sizeof(beginSequence) + sizeof(statusCells) + sizeof(textCells) + sizeof(endSequence)];
  unsigned char *byte = buffer;

  byte = mempcpy(byte, beginSequence, sizeof(beginSequence));
  byte = translateOutputCells(byte, statusCells, sizeof(statusCells));
  byte = translateOutputCells(byte, textCells, sizeof(textCells));
  byte = mempcpy(byte, endSequence, sizeof(endSequence));

  return writeData(brl, buffer, byte-buffer);
}

static void
updateCells (unsigned char *target, const unsigned char *source, size_t count) {
  if (cellsHaveChanged(target, source, count, NULL, NULL, NULL)) {
    refreshNeeded = 1;
  }
}

static void
clearCells (unsigned char *cells, size_t count) {
  memset(cells, 0, count);
  refreshNeeded = 1;
}

static int
beep (BrailleDisplay *brl) {
  static const unsigned char sequence[] = {ESC, 'B', CR};
  return writeData(brl, sequence, sizeof(sequence));
}

static int
inputFunction_showTime (BrailleDisplay *brl) {
  time_t clock = time(NULL);
  const struct tm *local = localtime(&clock);
  char text[sizeof(textCells) + 1];
  strftime(text, sizeof(text), "%Y-%m-%d %H:%M:%S", local);
  message(NULL, text, 0);
  return BRL_CMD_NOOP;
}

static unsigned char cursorDots;
static unsigned char cursorOffset;

static void
putCursor (BrailleDisplay *brl) {
  brl->buffer[cursorOffset] = cursorDots;
}

static int
inputFunction_incrementCursor (BrailleDisplay *brl) {
  if (++cursorOffset < sizeof(textCells)) return BRL_CMD_NOOP;

  cursorOffset = 0;
  return BRL_CMD_FWINRT;
}

static int
inputFunction_decrementCursor (BrailleDisplay *brl) {
  if (cursorOffset) {
    --cursorOffset;
    return BRL_CMD_NOOP;
  }

  cursorOffset = sizeof(textCells) - 1;
  return BRL_CMD_FWINLT;
}

typedef struct InputModeStruct InputMode;

typedef enum {
  IBT_unbound = 0, /* automatically set if not explicitly initialized */
  IBT_command,
  IBT_block,
  IBT_function,
  IBT_submode
} InputBindingType;

typedef union {
  int command;
  int block;
  int (*function) (BrailleDisplay *brl);
  const InputMode *submode;
} InputBindingValue;

typedef struct {
  InputBindingType type;
  InputBindingValue value;
} InputBinding;

struct InputModeStruct {
  InputBinding keyF1, keyF2, keyLeft, keyUp, keyCenter, keyDown, keyRight;

  unsigned temporary:1;
  void (*modifyWindow) (BrailleDisplay *brl);
  const char *name;
};

#define BIND(k,t,v) .key##k = {.type = IBT_##t, .value.t = (v)}
#define BIND_COMMAND(k,c) BIND(k, command, BRL_CMD_##c)
#define BIND_BLOCK(k,b) BIND(k, block, BRL_CMD_BLK(b))
#define BIND_FUNCTION(k,f) BIND(k, function, inputFunction_##f)
#define BIND_SUBMODE(k,m) BIND(k, submode, &inputMode_##m)

static const InputMode inputMode_char_f1 = {
  BIND_BLOCK(F1, SETLEFT),
  BIND_BLOCK(F2, DESCCHAR),
  BIND_BLOCK(Left, CLIP_ADD),
  BIND_BLOCK(Up, CLIP_NEW),
  BIND_BLOCK(Center, ROUTE),
  BIND_BLOCK(Down, COPY_RECT),
  BIND_BLOCK(Right, COPY_LINE),

  .temporary = 1,
  .name = "Char-F1"
};

static const InputMode inputMode_f1_f1 = {
  BIND_COMMAND(F1, HELP),
  BIND_COMMAND(F2, LEARN),
  BIND_COMMAND(Left, INFO),
  BIND_FUNCTION(Right, showTime),
  BIND_COMMAND(Up, PREFLOAD),
  BIND_COMMAND(Down, PREFMENU),
  BIND_COMMAND(Center, PREFSAVE),

  .temporary = 1,
  .name = "F1-F1"
};

static const InputMode inputMode_f1_f2 = {
  BIND_COMMAND(F1, FREEZE),
  BIND_COMMAND(F2, DISPMD),
  BIND_COMMAND(Left, ATTRVIS),
  BIND_COMMAND(Right, CSRVIS),
  BIND_COMMAND(Up, SKPBLNKWINS),
  BIND_COMMAND(Down, SKPIDLNS),
  BIND_COMMAND(Center, SIXDOTS),

  .temporary = 1,
  .name = "F1-F2"
};

static const InputMode inputMode_f1_left = {

  .temporary = 1,
  .name = "F1-Left"
};

static const InputMode inputMode_f1_right = {
  BIND_COMMAND(F2, AUTOSPEAK),
  BIND_COMMAND(Left, SAY_ABOVE),
  BIND_COMMAND(Right, SAY_BELOW),
  BIND_COMMAND(Up, MUTE),
  BIND_COMMAND(Down, SAY_LINE),
  BIND_COMMAND(Center, SPKHOME),

  .temporary = 1,
  .name = "F1-Right"
};

static const InputMode inputMode_f1_up = {
  BIND_COMMAND(F1, PRSEARCH),
  BIND_COMMAND(F2, NXSEARCH),
  BIND_COMMAND(Left, ATTRUP),
  BIND_COMMAND(Right, ATTRDN),
  BIND_COMMAND(Up, PRPGRPH),
  BIND_COMMAND(Down, NXPGRPH),
  BIND_COMMAND(Center, CSRJMP_VERT),

  .temporary = 1,
  .name = "F1-Up"
};

static const InputMode inputMode_f1_down = {
  BIND_COMMAND(F1, PRPROMPT),
  BIND_COMMAND(F2, NXPROMPT),
  BIND_COMMAND(Left, FWINLTSKIP),
  BIND_COMMAND(Right, FWINRTSKIP),
  BIND_COMMAND(Up, PRDIFLN),
  BIND_COMMAND(Down, NXDIFLN),
  BIND_COMMAND(Center, PASTE),

  .temporary = 1,
  .name = "F1-Down"
};

static const InputMode inputMode_f1_center = {
  BIND_SUBMODE(F1, char_f1),
  BIND_FUNCTION(Left, decrementCursor),
  BIND_FUNCTION(Right, incrementCursor),
  BIND_COMMAND(Up, LNUP),
  BIND_COMMAND(Down, LNDN),

  .temporary = 0,
  .modifyWindow = putCursor,
  .name = "F1-Center"
};

static const InputMode inputMode_f1 = {
  BIND_SUBMODE(F1, f1_f1),
  BIND_SUBMODE(F2, f1_f2),
  BIND_SUBMODE(Left, f1_left),
  BIND_SUBMODE(Right, f1_right),
  BIND_SUBMODE(Up, f1_up),
  BIND_SUBMODE(Down, f1_down),
  BIND_SUBMODE(Center, f1_center),

  .temporary = 1,
  .name = "F1"
};

static const InputMode inputMode_f2 = {
  BIND_COMMAND(F1, TOP_LEFT),
  BIND_COMMAND(F2, BOT_LEFT),
  BIND_COMMAND(Left, LNBEG),
  BIND_COMMAND(Right, LNEND),
  BIND_COMMAND(Up, TOP),
  BIND_COMMAND(Down, BOT),
  BIND_COMMAND(Center, CSRTRK),

  .temporary = 1,
  .name = "F2"
};

static const InputMode inputMode_basic = {
  BIND_SUBMODE(F1, f1),
  BIND_SUBMODE(F2, f2),
  BIND_COMMAND(Left, FWINLT),
  BIND_COMMAND(Right, FWINRT),
  BIND_COMMAND(Up, LNUP),
  BIND_COMMAND(Down, LNDN),
  BIND_COMMAND(Center, RETURN),

  .temporary = 0,
  .name = "Basic"
};

static const InputMode *inputMode;
static TimePeriod inputPeriod;

static void
setInputMode (const InputMode *mode) {
  if (mode->temporary) {
    char title[sizeof(textCells) + 1];
    snprintf(title, sizeof(title), "%s Mode", mode->name);
    message(NULL, title, MSG_NODELAY|MSG_SILENT);
  }

  inputMode = mode;
  startTimePeriod(&inputPeriod, 3000);
}

static void
resetInputMode (void) {
  setInputMode(&inputMode_basic);
}

static int
brl_construct (BrailleDisplay *brl, char **parameters, const char *device) {
  if (!isSerialDeviceIdentifier(&device)) {
    unsupportedDeviceIdentifier(device);
    return 0;
  }

  if ((serialDevice = serialOpenDevice(device))) {
    if (serialRestartDevice(serialDevice, serialBaud)) {
      serialCharactersPerSecond = serialBaud / serialGetCharacterBits(serialDevice);

      /* hm, how to switch to 38400 ? 
      static const unsigned char sequence[] = {ESC, 'V', CR};
      writeData(brl, sequence, sizeof(sequence));
      serialDiscardInput(serialDevice);
      serialSetBaud(serialDevice, 38400);
      */

      MAKE_OUTPUT_TABLE(0X01, 0X02, 0X04, 0X80, 0X40, 0X20, 0X08, 0X10);
      clearCells(textCells,  sizeof(textCells));
      clearCells(statusCells,  sizeof(statusCells));
      resetInputMode();

      cursorDots = 0XFF;
      cursorOffset = sizeof(textCells) / 2;

      brl->textColumns = sizeof(textCells);
      brl->textRows = 1;
      brl->statusColumns = sizeof(statusCells);
      brl->statusRows = 1;

      beep(brl);
      return 1;
    }

    serialCloseDevice(serialDevice);
    serialDevice = NULL;
  }

  return 0;
}

static void
brl_destruct (BrailleDisplay *brl) {
  if (serialDevice) {
    serialCloseDevice(serialDevice);
    serialDevice = NULL;
  }
}

static int
brl_writeWindow (BrailleDisplay *brl, const wchar_t *text) {
  if (inputMode->modifyWindow) inputMode->modifyWindow(brl);
  updateCells(textCells, brl->buffer, sizeof(textCells));
  if (refreshNeeded && !inputMode->temporary) {
    writeCells(brl);
    refreshNeeded = 0;
  }
  return 1;
}

static int
brl_writeStatus (BrailleDisplay *brl, const unsigned char *s) {
  updateCells(statusCells, s, sizeof(statusCells));
  return 1;
}

static int
brl_readCommand (BrailleDisplay *brl, KeyTableCommandContext context) {
  unsigned char byte;
  const InputMode *mode;
  const InputBinding *binding;

  {
    int result = serialReadData(serialDevice, &byte, 1, 0, 0);

    if (result == 0) {
      if (inputMode->temporary)
        if (afterTimePeriod(&inputPeriod, NULL))
          resetInputMode();

      return EOF;
    }

    if (result == -1) {
      logSystemError("read");
      return BRL_CMD_RESTARTBRL;
    }
  }

  mode = inputMode;
  if (mode->temporary) resetInputMode();

  switch (byte) {
    case KEY_F1:     binding = &mode->keyF1;     break;
    case KEY_F2:     binding = &mode->keyF2;     break;
    case KEY_LEFT:   binding = &mode->keyLeft;   break;
    case KEY_RIGHT:  binding = &mode->keyRight;  break;
    case KEY_UP:     binding = &mode->keyUp;     break;
    case KEY_DOWN:   binding = &mode->keyDown;   break;
    case KEY_CENTER: binding = &mode->keyCenter; break;

    default:
      logMessage(LOG_WARNING, "unhandled key: %s -> %02X", mode->name, byte);
      beep(brl);
      return EOF;
  }

  switch (binding->type) {
    case IBT_unbound:
      logMessage(LOG_WARNING, "unbound key: %s -> %02X", mode->name, byte);
      beep(brl);
      break;

    case IBT_command:
      return binding->value.command;

    case IBT_block:
      return binding->value.block + cursorOffset;

    case IBT_function:
      return binding->value.function(brl);

    case IBT_submode: {
      setInputMode(binding->value.submode);
      break;
    }

    default:
      logMessage(LOG_WARNING, "unhandled input binding type: %02X", binding->type);
      break;
  }

  return BRL_CMD_NOOP;
}
