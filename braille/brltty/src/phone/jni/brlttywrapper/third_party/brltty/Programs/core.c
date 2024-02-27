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
#include <time.h>

#ifdef HAVE_LANGINFO_H
#include <langinfo.h>
#endif /* HAVE_LANGINFO_H */

#ifdef HAVE_SYS_WAIT_H
#include <sys/wait.h>
#endif /* HAVE_SYS_WAIT_H */

#include "parameters.h"
#include "embed.h"
#include "log.h"
#include "alert.h"
#include "strfmt.h"

#include "cmd_queue.h"
#include "cmd_clipboard.h"
#include "cmd_custom.h"
#include "cmd_input.h"
#include "cmd_keycodes.h"
#include "cmd_learn.h"
#include "cmd_miscellaneous.h"
#include "cmd_navigation.h"
#include "cmd_override.h"
#include "cmd_preferences.h"
#include "cmd_speech.h"
#include "cmd_toggle.h"
#include "cmd_touch.h"

#include "async_handle.h"
#include "async_wait.h"
#include "async_alarm.h"
#include "async_event.h"
#include "async_signal.h"
#include "async_task.h"

#include "brl_cmds.h"
#include "timing.h"
#include "ctb.h"
#include "routing.h"
#include "utf8.h"
#include "unicode.h"
#include "scr.h"
#include "update.h"
#include "ses.h"
#include "brl.h"
#include "brl_utils.h"
#include "prefs.h"
#include "api_control.h"
#include "core.h"

#ifdef ENABLE_SPEECH_SUPPORT
#include "spk.h"
#endif /* ENABLE_SPEECH_SUPPORT */

BrailleDisplay brl;                        /* For the Braille routines */

int
haveBrailleDisplay (void) {
  return braille->definition.code != noBraille.definition.code;
}

ScreenDescription scr;
SessionEntry *ses = NULL;

unsigned char infoMode = 0;

unsigned int textStart;
unsigned int textCount;
unsigned char textMaximized = 0;

unsigned int statusStart;
unsigned int statusCount;

unsigned int fullWindowShift;                /* Full window horizontal distance */
unsigned int halfWindowShift;                /* Half window horizontal distance */
unsigned int verticalWindowShift;                /* Window vertical distance */

int
isContractedBraille (void) {
  return (prefs.brailleVariant == bvContracted6)
      || (prefs.brailleVariant == bvContracted8)
      ;
}

int
isSixDotComputerBraille (void) {
  return (prefs.brailleVariant == bvComputer6)
      || (prefs.brailleVariant == bvContracted6)
      ;
}

static void
setBrailleVariant (int contracted, int sixDot) {
  prefs.brailleVariant = contracted?
                         (sixDot? bvContracted6: bvContracted8):
                         (sixDot? bvComputer6: bvComputer8);
}

void
setContractedBraille (int contracted) {
  setBrailleVariant(contracted, isSixDotComputerBraille());
  api.updateParameter(BRLAPI_PARAM_LITERARY_BRAILLE, 0);
}

void
setSixDotComputerBraille (int sixDot) {
  setBrailleVariant(isContractedBraille(), sixDot);
  api.updateParameter(BRLAPI_PARAM_COMPUTER_BRAILLE_CELL_SIZE, 0);
}

void
onBrailleVariantUpdated (void) {
  api.updateParameter(BRLAPI_PARAM_COMPUTER_BRAILLE_CELL_SIZE, 0);
  api.updateParameter(BRLAPI_PARAM_LITERARY_BRAILLE, 0);
}

int
startScreenCursorRouting (int column, int row) {
  if (!routeScreenCursor(column, row, scr.number)) return 0;
  if (isRouting()) alert(ALERT_ROUTING_STARTED);
  return 1;
}

int
bringScreenCursor (int column, int row) {
  if (!startScreenCursorRouting(column, row)) return 0;
  RoutingStatus status = getRoutingStatus(1);

  if (status != ROUTING_STATUS_NONE) {
    alert(
      (status > ROUTING_STATUS_COLUMN)? ALERT_ROUTING_FAILED:
      ALERT_ROUTING_SUCCEEDED
    );

    ses->spkx = scr.posx;
    ses->spky = scr.posy;
  }

  return 1;
}

typedef struct {
  int motionColumn;
  int motionRow;

  int speechColumn;
  int speechRow;
} PrecommandState;

static void *
preprocessCommand (void) {
  PrecommandState *pre;

  if ((pre = malloc(sizeof(*pre)))) {
    memset(pre, 0, sizeof(*pre));

    pre->motionColumn = ses->winx;
    pre->motionRow = ses->winy;

    pre->speechColumn = ses->spkx;
    pre->speechRow = ses->spky;

    suspendUpdates();
    return pre;
  } else {
    logMallocError();
  }

  return NULL;
}

static void
postprocessCommand (void *state, int command, const CommandEntry *cmd, int handled) {
  PrecommandState *pre = state;

  if (pre) {
    resumeUpdates(0);
    if (handled) scheduleUpdate("command executed");

    if ((ses->winx != pre->motionColumn) || (ses->winy != pre->motionRow)) {
      /* The braille window has been manually moved. */
      reportBrailleWindowMoved();

      ses->motx = ses->winx;
      ses->moty = ses->winy;

      isContracted = 0;
    }

    if (cmd) {
      if (cmd->isMotion) {
        if (command & BRL_FLG_MOTION_ROUTE) {
          if ((ses->spkx != pre->speechColumn) || (ses->spky != pre->speechRow)) {
            /* The speech cursor has moved. */
            bringScreenCursor(ses->spkx, ses->spky);
          } else if (command & BRL_MSK_BLK) {
            bringScreenCursor((command & BRL_MSK_ARG), ses->winy);
          } else {
            int left = ses->winx;
            int right = MIN(left+textCount, scr.cols) - 1;

            int top = ses->winy;
            int bottom = MIN(top+brl.textRows, scr.rows) - 1;

            if ((scr.posx < left) || (scr.posx > right) ||
                (scr.posy < top) || (scr.posy > bottom)) {
              bringScreenCursor(left, top);
            }
          }
        }
      }
    }

    free(pre);
  }
}

static int
handleUnhandledCommands (int command, void *data) {
  switch (command & BRL_MSK_CMD) {
    case BRL_CMD_NOOP:        /* do nothing but loop */
      break;

    default:
      alert(ALERT_COMMAND_REJECTED);
      return 0;
  }

  return 1;
}

static int
handleApiCommands (int command, void *data) {
  return api.handleCommand(command);
}

static int
addScreenCommands (void) {
  return pushCommandHandler("screen", KTB_CTX_DEFAULT,
                            handleScreenCommands, NULL, NULL);
}

static int
addCommands (void) {
  if (!pushCommandEnvironment("main", preprocessCommand, postprocessCommand)) return 0;

  pushCommandHandler("unhandled", KTB_CTX_DEFAULT,
                     handleUnhandledCommands, NULL, NULL);

  addMiscellaneousCommands();
  addLearnCommands();
  addSpeechCommands();
  addClipboardCommands();
  addPreferencesCommands();
  addToggleCommands();
  addTouchCommands();
  addKeycodeCommands();
  addInputCommands();
  addNavigationCommands();

  addOverrideCommands();
  addScreenCommands();
  addCustomCommands();

  pushCommandHandler("API", KTB_CTX_DEFAULT,
                     handleApiCommands, NULL, NULL);

  return 1;
}

static AsyncHandle delayedCursorTrackingAlarm;

ASYNC_ALARM_CALLBACK(handleDelayedCursorTrackingAlarm) {
  asyncDiscardHandle(delayedCursorTrackingAlarm);
  delayedCursorTrackingAlarm = NULL;

  ses->trkx = ses->dctx;
  ses->trky = ses->dcty;

  ses->dctx = -1;
  ses->dcty = -1;

  scheduleUpdate("delayed cursor tracking");
}

void
cancelDelayedCursorTrackingAlarm (void) {
  if (delayedCursorTrackingAlarm) {
    asyncCancelRequest(delayedCursorTrackingAlarm);
    delayedCursorTrackingAlarm = NULL;
  }
}

static void
setSessionEntry (void) {
  describeScreen(&scr);
  if (scr.number == -1) scr.number = userVirtualTerminal(0);

  {
    typedef enum {SAME, DIFFERENT, FIRST} State;
    State state = (!ses)? FIRST:
                  (scr.number == ses->number)? SAME:
                  DIFFERENT;

    if (state != SAME) {
      cancelDelayedCursorTrackingAlarm();
      ses = getSessionEntry(scr.number);

      if (state == FIRST) {
        addCommands();
      }
    }
  }
}

void
updateSessionAttributes (void) {
  setSessionEntry();

  {
    int maximum = MAX(scr.rows-1, 0);
    int *table[] = {&ses->winy, &ses->moty, NULL};
    int **value = table;

    while (*value) {
      if (**value > maximum) **value = maximum;
      value += 1;
    }
  }

  {
    int maximum = MAX(scr.cols-1, 0);
    int *table[] = {&ses->winx, &ses->motx, NULL};
    int **value = table;

    while (*value) {
      if (**value > maximum) **value = maximum;
      value += 1;
    }
  }
}

void
fillStatusSeparator (wchar_t *text, unsigned char *dots) {
  if ((prefs.statusSeparator != ssNone) && (statusCount > 0)) {
    int onRight = statusStart > 0;
    unsigned int column = (onRight? statusStart: textStart) - 1;

    wchar_t textSeparator;
#ifdef HAVE_WCHAR_H 
    const wchar_t textSeparator_left  = 0X23B8; /* LEFT VERTICAL BOX LINE */
    const wchar_t textSeparator_right = 0X23B9; /* RIGHT VERTICAL BOX LINE */
    const wchar_t textSeparator_block = 0X2503; /* BOX DRAWINGS HEAVY VERTICAL */
#else /* HAVE_WCHAR_H */
    const wchar_t textSeparator_left  = 0X5B; /* LEFT SQUARE BRACKET */
    const wchar_t textSeparator_right = 0X5D; /* RIGHT SQUARE BRACKET */
    const wchar_t textSeparator_block = 0X7C; /* VERTICAL LINE */
#endif /* HAVE_WCHAR_H */

    unsigned char dotsSeparator;
    const unsigned char dotsSeparator_left = BRL_DOT1 | BRL_DOT2 | BRL_DOT3 | BRL_DOT7;
    const unsigned char dotsSeparator_right = BRL_DOT4 | BRL_DOT5 | BRL_DOT6 | BRL_DOT8;
    const unsigned char dotsSeparator_block = dotsSeparator_left | dotsSeparator_right;

    text += column;
    dots += column;

    switch (prefs.statusSeparator) {
      case ssBlock:
        textSeparator = textSeparator_block;
        dotsSeparator = dotsSeparator_block;
        break;

      case ssStatusSide:
        textSeparator = onRight? textSeparator_right: textSeparator_left;
        dotsSeparator = onRight? dotsSeparator_right: dotsSeparator_left;
        break;

      case ssTextSide:
        textSeparator = onRight? textSeparator_left: textSeparator_right;
        dotsSeparator = onRight? dotsSeparator_left: dotsSeparator_right;
        break;

      default:
        textSeparator = WC_C(' ');
        dotsSeparator = 0;
        break;
    }

    {
      unsigned int row;
      for (row=0; row<brl.textRows; row+=1) {
        *text = textSeparator;
        text += brl.textColumns;

        *dots = dotsSeparator;
        dots += brl.textColumns;
      }
    }
  }
}

int
writeBrailleCharacters (const char *mode, const wchar_t *characters, size_t length) {
  wchar_t textBuffer[brl.textColumns * brl.textRows];

  fillTextRegion(textBuffer, brl.buffer,
                 textStart, textCount, brl.textColumns, brl.textRows,
                 characters, length);

  {
    size_t modeLength = mode? countUtf8Characters(mode): 0;
    wchar_t modeCharacters[modeLength + 1];
    makeWcharsFromUtf8(mode, modeCharacters, ARRAY_COUNT(modeCharacters));
    fillTextRegion(textBuffer, brl.buffer,
                   statusStart, statusCount, brl.textColumns, brl.textRows,
                   modeCharacters, modeLength);
  }

  fillStatusSeparator(textBuffer, brl.buffer);

  return writeBrailleWindow(&brl, textBuffer, 0);
}

int
writeBrailleText (const char *mode, const char *text) {
  size_t count = countUtf8Characters(text) + 1;
  wchar_t characters[count];
  size_t length = makeWcharsFromUtf8(text, characters, count);
  return writeBrailleCharacters(mode, characters, length);
}

int
showBrailleText (const char *mode, const char *text, int minimumDelay) {
  int ok = writeBrailleText(mode, text);
  drainBrailleOutput(&brl, minimumDelay);
  return ok;
}

static inline const char *
getMeridianString_am (void) {
#ifdef HAVE_NL_LANGINFO
  return nl_langinfo(AM_STR);
#else /* HAVE_NL_LANGINFO */
  return "am";
#endif /* HAVE_NL_LANGINFO */
}

static inline const char *
getMeridianString_pm (void) {
#ifdef HAVE_NL_LANGINFO
  return nl_langinfo(PM_STR);
#else /* HAVE_NL_LANGINFO */
  return "pm";
#endif /* HAVE_NL_LANGINFO */
}

static const char *
getMeridianString (uint8_t *hour) {
  const char *string = NULL;

  switch (prefs.timeFormat) {
    case tf12Hour: {
      const uint8_t twelve = 12;

      string = (*hour < twelve)? getMeridianString_am(): getMeridianString_pm();
      *hour %= twelve;
      if (!*hour) *hour = twelve;
      break;
    }

    default:
      break;
  }

  return string;
}

STR_BEGIN_FORMATTER(formatBrailleTime, const TimeFormattingData *fmt)
  char time[0X40];

  {
    const char *hourFormat = "%02" PRIu8;
    const char *minuteFormat = "%02" PRIu8;
    const char *secondFormat = "%02" PRIu8;
    char separator;

    switch (prefs.timeSeparator) {
      default:
      case tsColon:
        separator = ':';
        break;

      case tsDot:
        separator = '.';
        break;
    }

    switch (prefs.timeFormat) {
      default:
      case tf24Hour:
        break;

      case tf12Hour:
        hourFormat = "%" PRIu8;
        break;
    }

    STR_BEGIN(time, sizeof(time));
    STR_PRINTF(hourFormat, fmt->components.hour);
    STR_PRINTF("%c", separator);
    STR_PRINTF(minuteFormat, fmt->components.minute);

    if (prefs.showSeconds) {
      STR_PRINTF("%c", separator);
      STR_PRINTF(secondFormat, fmt->components.second);
    }

    if (fmt->meridian) STR_PRINTF("%s", fmt->meridian);
    STR_END;
  }

  if (prefs.datePosition == dpNone) {
    STR_PRINTF("%s", time);
  } else {
    char date[0X40];

    {
      const char *yearFormat = "%04" PRIu16;
      const char *monthFormat = "%02" PRIu8;
      const char *dayFormat = "%02" PRIu8;

      uint16_t year = fmt->components.year;
      uint8_t month = fmt->components.month + 1;
      uint8_t day = fmt->components.day + 1;

      char separator;

      switch (prefs.dateSeparator) {
        default:
        case dsDash:
          separator = '-';
          break;

        case dsSlash:
          separator = '/';
          break;

        case dsDot:
          separator = '.';
          break;
      }

      STR_BEGIN(date, sizeof(date));
      switch (prefs.dateFormat) {
        default:
        case dfYearMonthDay:
          STR_PRINTF(yearFormat, year);
          STR_PRINTF("%c", separator);
          STR_PRINTF(monthFormat, month);
          STR_PRINTF("%c", separator);
          STR_PRINTF(dayFormat, day);
          break;

        case dfMonthDayYear:
          STR_PRINTF(monthFormat, month);
          STR_PRINTF("%c", separator);
          STR_PRINTF(dayFormat, day);
          STR_PRINTF("%c", separator);
          STR_PRINTF(yearFormat, year);
          break;

        case dfDayMonthYear:
          STR_PRINTF(dayFormat, day);
          STR_PRINTF("%c", separator);
          STR_PRINTF(monthFormat, month);
          STR_PRINTF("%c", separator);
          STR_PRINTF(yearFormat, year);
          break;
      }
      STR_END;

      switch (prefs.datePosition) {
        case dpBeforeTime:
          STR_PRINTF("%s %s", date, time);
          break;

        case dpAfterTime:
          STR_PRINTF("%s %s", time, date);
          break;

        default:
          STR_PRINTF("%s", date);
          break;
      }
    }
  }
STR_END_FORMATTER

void
getTimeFormattingData (TimeFormattingData *fmt) {
  getCurrentTime(&fmt->value);
  expandTimeValue(&fmt->value, &fmt->components);
  fmt->meridian = getMeridianString(&fmt->components.hour);
}

int
isCursorPosition (int x) {
  return (x == scr.posx) && (ses->winy == scr.posy) && showScreenCursor();
}

int
isWordBreak (const ScreenCharacter *characters, int x) {
  if (!iswspace(characters[x].text)) return 0;
  return !isCursorPosition(x);
}

int
getWordWrapLength (int row, int from, int count) {
  int width = scr.cols;
  if (from >= width) return 0;

  int end = from + count;
  if (end >= width) return width - from;

  ScreenCharacter characters[width];
  readScreenRow(row, width, characters);

  int to = end;
  int onWordBreak = iswspace(characters[to].text);

  if (!onWordBreak) {
    int index = to;

    while (index > from) {
      if (iswspace(characters[--index].text)) {
        to = index;
        onWordBreak = 1;
        break;
      }
    }
  }

  if (onWordBreak) {
    while (to < width) {
      if (!iswspace(characters[to].text)) break;
      if ((to >= end) && isCursorPosition(to)) break;
      to += 1;
    }
  }

  return to - from;
}

void
setWordWrapStart (int start) {
  if (start < 0) start = 0;
  ses->winx = start;

  if (start > 0) {
    int end = start + textCount;
    if (end > scr.cols) end = scr.cols;

    ScreenCharacter characters[end];
    readScreenRow(ses->winy, end, characters);

    while (end > 0) {
      if (!isWordBreak(characters, --end)) {
        end += 1;
        break;
      }
    }

    start = end - textCount;
    if (start < 0) start = 0;

    if (start > 0) {
      if (!isWordBreak(characters, start-1)) {
        while (start < end) {
          if (isWordBreak(characters, start)) break;
          start += 1;
        }
      }

      while (start < end) {
        if (!isWordBreak(characters, start)) break;
        start += 1;
      }
    }

    if (start < end) ses->winx = start;
  }
}

void 
placeBrailleWindowHorizontally (int x) {
  if (prefs.slidingBrailleWindow) {
    ses->winx = MAX(0, (x - (int)(textCount / 2)));
  } else {
    ses->winx = x / textCount * textCount;
  }
}

void
placeRightEdge (int column) {
  if (isContracting()) {
    ses->winx = 0;

    while (1) {
      int length = getContractedLength(textCount);
      int end = ses->winx + length;

      if (end > column) break;
      if (end == ses->winx) break;
      ses->winx = end;
    }
  } else {
    ses->winx = column / textCount * textCount;
  }
}

void
placeBrailleWindowRight (void) {
  placeRightEdge(scr.cols-1);
}

int
moveBrailleWindowLeft (unsigned int amount) {
  if (ses->winx < 1) return 0;
  if (amount < 1) return 0;

  ses->winx -= MIN(ses->winx, amount);
  return 1;
}

int
moveBrailleWindowRight (unsigned int amount) {
  if (amount < 1) return 0;
  int newx = ses->winx + amount;
  if (newx >= scr.cols) return 0;

  ses->winx = newx;
  return 1;
}

int
shiftBrailleWindowLeft (unsigned int amount) {
  if (isContracting()) {
    int reference = ses->winx;
    if (!reference) return 0;

    {
      int from = 0;
      int to = ses->winx;

      while (from < to) {
        int end = (ses->winx = ((from + to) / 2)) + getContractedLength(amount);

        if (end < reference) {
          from = ses->winx + 1;
        } else {
          to = ses->winx;
        }
      }

      if (!(ses->winx = from)) return 1;
    }

    ScreenCharacter characters[reference];
    readScreenRow(ses->winy, reference, characters);
    int x = ses->winx;

    if (!isWordBreak(characters, x-1)) {
      int wasIdeographic = isIdeographicCharacter(characters[x-1].text);

      for (int i=x; i<reference; i+=1) {
        int isIdeographic = isIdeographicCharacter(characters[i].text);

        if (!(isIdeographic && wasIdeographic)) {
          if (!isWordBreak(characters, i)) {
            wasIdeographic = isIdeographic;
            continue;
          }
        }

        x = i;
        break;
      }
    }

    while (x < reference) {
      if (!isWordBreak(characters, x)) break;
      x += 1;
    }

    if (x < reference) ses->winx = x;
    return 1;
  }

  if (prefs.wordWrap) {
    if (ses->winx < 1) return 0;
    setWordWrapStart(ses->winx - amount);
    return 1;
  }

  return moveBrailleWindowLeft(amount);
}

int
shiftBrailleWindowRight (unsigned int amount) {
  if (isContracting()) {
    amount = getContractedLength(amount);
  } else if (prefs.wordWrap) {
    amount = getWordWrapLength(ses->winy, ses->winx, amount);
  }

  return moveBrailleWindowRight(amount);
}

void
slideBrailleWindowVertically (int y) {
  if ((y < ses->winy) || (y >= (int)(ses->winy + brl.textRows))) {
    y -= brl.textRows / 2;

    {
      int maxy = scr.rows - brl.textRows;
      if (y > maxy) y = maxy;
    }

    if (y < 0) y = 0;
    ses->winy = y;
  }
}

static int
isWithinBrailleWindow (int x, int y) {
  return (x >= ses->winx)
      && (x < (int)(ses->winx + textCount))
      && (y >= ses->winy)
      && (y < (int)(ses->winy + brl.textRows))
      ;
}

int
trackScreenCursor (int place) {
  if (!SCR_CURSOR_OK()) return 0;

  if (place) {
    cancelDelayedCursorTrackingAlarm();
  } else if (delayedCursorTrackingAlarm) {
    /* A cursor tracking motion has been delayed. If the cursor returned
     * to its initial location in the mean time then we discard and ignore
     * the previous motion. Otherwise we wait for the timer to expire.
     */
    if ((ses->dctx == scr.posx) && (ses->dcty == scr.posy)) {
      cancelDelayedCursorTrackingAlarm();
    }

    return 1;
  } else if ((prefs.cursorTrackingDelay > 0) && (ses->dctx != -1) &&
             !isWithinBrailleWindow(ses->trkx, ses->trky)) {
    /* The cursor may move spuriously while a program updates information
     * on a status bar. If cursor tracking is on and the cursor was
     * outside the braille window before it moved, we delay the tracking
     * motion for a while so as not to obnoxiously move the braille window
     * in case the cursor will eventually return to its initial location
     * within a short time.
     */
    ses->dctx = ses->trkx;
    ses->dcty = ses->trky;

    int delay = 250 << (prefs.cursorTrackingDelay - 1);
    asyncNewRelativeAlarm(&delayedCursorTrackingAlarm, delay,
                          handleDelayedCursorTrackingAlarm, NULL);

    return 1;
  }

  /* anything but -1 */
  ses->dctx = 0;
  ses->dcty = 0;

  if (isContracted) {
    slideBrailleWindowVertically(scr.posy);
    contractedTrack = 1;

    if (scr.posx > ses->winx) {
      if (scr.posx < (ses->winx + getContractedLength(textCount))) {
        return 1;
      }
    }

    ses->winx = scr.posx;
    shiftBrailleWindowLeft(halfWindowShift);
    return 1;
  }

  if (place && !isWithinBrailleWindow(scr.posx, scr.posy)) {
    placeBrailleWindowHorizontally(scr.posx);
  }

  if (prefs.slidingBrailleWindow) {
    {
      int width = scr.cols;
      ScreenCharacter characters[width];
      readScreenRow(scr.posy, width, characters);

      int column = findLastNonSpaceCharacter(characters, width);
      if (column < 0) column = 0;
      if (column < textCount) ses->winx = 0;
    }

    int reset = textCount * 3 / 10;
    int trigger = prefs.eagerSlidingBrailleWindow? textCount*3/20: 0;
    if (scr.posx == ses->winx) trigger = 1;

    if (scr.posx < (ses->winx + trigger)) {
      ses->winx = MAX(scr.posx-reset, 0);
    } else if (scr.posx >= (int)(ses->winx + textCount - trigger)) {
      ses->winx = MAX(MIN(scr.posx+reset+1, scr.cols)-(int)textCount, 0);
    }
  } else if (scr.posx < ses->winx) {
    ses->winx -= ((ses->winx - scr.posx - 1) / textCount + 1) * textCount;
    if (ses->winx < 0) ses->winx = 0;
  } else {
    ses->winx += (scr.posx - ses->winx) / textCount * textCount;
  }

  if (prefs.wordWrap) {
    int length = getWordWrapLength(ses->winy, ses->winx, textCount);
    int next = ses->winx + length;
    if (scr.posx >= next) ses->winx = next;
  }

  slideBrailleWindowVertically(scr.posy);
  return 1;
}

int
findFirstNonSpaceCharacter (const ScreenCharacter *characters, int count) {
  int index = 0;

  while (index < count) {
    if (!iswspace(characters[index].text)) return index;
    index += 1;
  }

  return -1;
}

int
findLastNonSpaceCharacter (const ScreenCharacter *characters, int count) {
  int index = count;

  while (index > 0)
    if (!iswspace(characters[--index].text))
      return index;

  return -1;
}

int
isAllSpaceCharacters (const ScreenCharacter *characters, int count) {
  return findFirstNonSpaceCharacter(characters, count) < 0;
}

#ifdef ENABLE_SPEECH_SUPPORT
SpeechSynthesizer spk;

int
haveSpeechSynthesizer (void) {
  return speech->definition.code != noSpeech.definition.code;
}

void
trackSpeech (void) {
  int location = spk.track.speechLocation;

  if (location != SPK_LOC_NONE) {
    placeBrailleWindowHorizontally(location % scr.cols);
    slideBrailleWindowVertically(spk.track.firstLine + (location / scr.cols));
    scheduleUpdate("speech tracked");
  }
}

int
isAutospeakActive (void) {
  if (!haveSpeechSynthesizer()) return 0;
  if (prefs.autospeak) return 1;
  if (haveBrailleDisplay()) return 0;
  return !opt_quietIfNoBraille;
}

void
sayScreenCharacters (const ScreenCharacter *characters, size_t count, SayOptions options) {
  wchar_t text[count];
  wchar_t *t = text;

  unsigned char attributes[count];
  unsigned char *a = attributes;

  for (unsigned int i=0; i<count; i+=1) {
    const ScreenCharacter *character = &characters[i];
    *t++ = character->text;
    *a++ = character->attributes;
  }

  sayWideCharacters(&spk, text, attributes, count, options);
}

void
speakCharacters (const ScreenCharacter *characters, size_t count, int spell, int interrupt) {
  SayOptions sayOptions = 0;
  if (interrupt) sayOptions |= SAY_OPT_MUTE_FIRST;

  if (isAllSpaceCharacters(characters, count)) {
    switch (prefs.speechWhitespaceIndicator) {
      default:
      case swsNone:
        break;

      case swsSaySpace: {
        wchar_t buffer[0X100];
        size_t length = makeWcharsFromUtf8(gettext("space"), buffer, ARRAY_COUNT(buffer));

        sayWideCharacters(&spk, buffer, NULL, length, sayOptions);
        break;
      }
    }
  } else if (count == 1) {
    wchar_t character = characters[0].text;
    unsigned char attributes = characters[0].attributes;
    const char *prefix = NULL;

    if (iswupper(character)) {
      switch (prefs.speechUppercaseIndicator) {
        default:
        case sucNone:
          break;

        case sucSayCap:
          // "cap" here, used during speech output, is short for "capital".
          // It is spoken just before an uppercase letter, e.g. "cap A".
          prefix = gettext("cap");
          break;

        case sucRaisePitch:
          sayOptions |= SAY_OPT_HIGHER_PITCH;
          break;
      }
    } else if (iswpunct(character)) {
      sayOptions |= SAY_OPT_ALL_PUNCTUATION;
    }

    if (prefix) {
      wchar_t textBuffer[0X100];
      size_t length = makeWcharsFromUtf8(prefix, textBuffer, ARRAY_COUNT(textBuffer));

      textBuffer[length++] = WC_C(' ');
      textBuffer[length++] = character;

      unsigned char attributesBuffer[length];
      memset(attributesBuffer, SCR_COLOUR_DEFAULT, length);
      attributesBuffer[length-1] = attributes;

      sayWideCharacters(&spk, textBuffer, attributesBuffer, length, sayOptions);
    } else {
      sayWideCharacters(&spk, &character, &attributes, 1, sayOptions);
    }
  } else if (spell) {
    size_t length = count * 2;
    wchar_t textBuffer[length];
    unsigned char attributesBuffer[length];

    wchar_t *text = textBuffer;
    unsigned char *attributes = attributesBuffer;

    const ScreenCharacter *character = characters;
    const ScreenCharacter *end = character + count;

    while (character < end) {
      *text++ = character->text;
      *attributes++ = character->attributes;

      *text++ = WC_C(' ');
      *attributes++ = SCR_COLOUR_DEFAULT;

      character += 1;
    }

    sayWideCharacters(&spk, textBuffer, attributesBuffer, length-1, sayOptions);
  } else {
    sayScreenCharacters(characters, count, sayOptions);
  }
}

int
speakIndent (const ScreenCharacter *characters, int count, int evenIfNoIndent) {
  int length = scr.cols;
  ScreenCharacter buffer[length];

  if (!characters) {
    readScreenRow(ses->spky, length, buffer);
    characters = buffer;
    count = length;
  }

  int indent = findFirstNonSpaceCharacter(characters, count);
  if ((indent < 1) && !evenIfNoIndent) return 0;

  char message[50];
  const char *text = message;

  if (indent < 0) {
    text = gettext("blank line");
  } else {
    snprintf(message, sizeof(message),
             "%s %d", gettext("indent"), indent);
  }

  logMessage(LOG_CATEGORY(SPEECH_EVENTS),
             "line indent: %d", indent);

  sayString(&spk, text, SAY_OPT_MUTE_FIRST);
  return 1;
}
#endif /* ENABLE_SPEECH_SUPPORT */

int
isContracting (void) {
  return isContractedBraille() && contractionTable;
}

int
getContractedLength (unsigned int outputLimit) {
  int inputLength = scr.cols - ses->winx;
  wchar_t inputBuffer[inputLength];
  readScreenText(ses->winx, ses->winy, inputLength, 1, inputBuffer);

  int outputLength = outputLimit;
  unsigned char outputBuffer[outputLength];

  int offsetCount = inputLength;
  int outputOffsets[offsetCount + 1];

  contractText(
    contractionTable, NULL,
    inputBuffer, &inputLength,
    outputBuffer, &outputLength,
    outputOffsets, getCursorOffsetForContracting()
  );

  for (int length=0; length<inputLength; length+=1) {
    int offset = outputOffsets[length];

    if (offset != CTB_NO_OFFSET) {
      if (offset >= outputLimit) {
        return length;
      }
    }
  }

  return inputLength;
}

int
showScreenCursor (void) {
  return scr.hasCursor
      && prefs.showScreenCursor
      && !(ses->hideScreenCursor || brl.hideCursor)
      ;
}

int
isSameText (
  const ScreenCharacter *character1,
  const ScreenCharacter *character2
) {
  return character1->text == character2->text;
}

int
isSameAttributes (
  const ScreenCharacter *character1,
  const ScreenCharacter *character2
) {
  return character1->attributes == character2->attributes;
}

int
isSameCharacter (
  const ScreenCharacter *character1,
  const ScreenCharacter *character2
) {
  return isSameText(character1, character2) && isSameAttributes(character1, character2);
}

int
isSameRow (
  const ScreenCharacter *characters1,
  const ScreenCharacter *characters2,
  int count,
  IsSameCharacter isSameCharacter
) {
  int i;
  for (i=0; i<count; ++i)
    if (!isSameCharacter(&characters1[i], &characters2[i]))
      return 0;

  return 1;
}

int
canBraille (void) {
  return braille && brl.buffer && !brl.noDisplay && !brl.isSuspended;
}

static unsigned int interruptEnabledCount;
static AsyncEvent *interruptEvent;
static int interruptPending;
static WaitResult waitResult;

typedef struct {
  WaitResult waitResult;
} InterruptEventParameters;

int
brlttyInterrupt (WaitResult waitResult) {
  if (interruptEvent) {
    InterruptEventParameters *iep;

    if ((iep = malloc(sizeof(*iep)))) {
      memset(iep, 0, sizeof(*iep));
      iep->waitResult = waitResult;

      if (asyncSignalEvent(interruptEvent, iep)) {
        return 1;
      }

      free(iep);
    } else {
      logMallocError();
    }
  }

  return 0;
}

ASYNC_EVENT_CALLBACK(handleCoreInterrupt) {
  InterruptEventParameters *iep = parameters->signalData;

  if (iep) {
    interruptPending = 1;
    waitResult = iep->waitResult;
    free(iep);
  }
}

int
brlttyEnableInterrupt (void) {
  if (!interruptEnabledCount) {
    if (!(interruptEvent = asyncNewEvent(handleCoreInterrupt, NULL))) {
      return 0;
    }
  }

  interruptEnabledCount += 1;
  return 1;
}

int
brlttyDisableInterrupt (void) {
  if (!interruptEnabledCount) return 0;

  if (!--interruptEnabledCount) {
    asyncDiscardEvent(interruptEvent);
    interruptEvent = NULL;
  }

  return 1;
}

typedef void UnmonitoredConditionHandler (const void *data);

static void
handleRoutingDone (const void *data) {
  const RoutingStatus *status = data;

  alert(
    (*status > ROUTING_STATUS_SUCCEESS)? ALERT_ROUTING_FAILED:
    ALERT_ROUTING_SUCCEEDED
  );

  ses->spkx = scr.posx;
  ses->spky = scr.posy;
}

static void
handleBrailleDriverFailed (const void *data) {
  restartBrailleDriver();
}

static time_t programTerminationRequestTime;
static int programTerminationRequestSignal;
static volatile sig_atomic_t programTerminationRequestCount;

typedef struct {
  UnmonitoredConditionHandler *handler;
  const void *data;
} UnmonitoredConditionDescriptor;

ASYNC_CONDITION_TESTER(checkUnmonitoredConditions) {
  UnmonitoredConditionDescriptor *ucd = data;

  if (interruptPending) {
    logMessage(LOG_CATEGORY(ASYNC_EVENTS), "interrupt pending");
    ucd->data = &waitResult;
    interruptPending = 0;
    return 1;
  }

  if (programTerminationRequestCount) {
    // This is a memory read barrier to ensure that the most recent
    // time and number for the program termination signal are seen.
    __sync_synchronize();

    logMessage(LOG_CATEGORY(ASYNC_EVENTS),
      "program termination requested: Count=%ld Signal=%d",
      (long)programTerminationRequestCount, programTerminationRequestSignal
    );

    static const WaitResult result = WAIT_STOP;
    ucd->data = &result;
    return 1;
  }

  {
    static RoutingStatus status;

    if ((status = getRoutingStatus(0)) != ROUTING_STATUS_NONE) {
      logMessage(LOG_CATEGORY(ASYNC_EVENTS), "routing status: %u", status);
      ucd->handler = handleRoutingDone;
      ucd->data = &status;
      return 1;
    }
  }

  if (brl.hasFailed) {
    logMessage(LOG_CATEGORY(ASYNC_EVENTS), "braille driver failed");
    ucd->handler = handleBrailleDriverFailed;
    return 1;
  }

  return 0;
}

WaitResult
brlttyWait (int duration) {
  UnmonitoredConditionDescriptor ucd = {
    .handler = NULL,
    .data = NULL
  };

  if (asyncAwaitCondition(duration, checkUnmonitoredConditions, &ucd)) {
    if (!ucd.handler) {
      const WaitResult *result = ucd.data;
      return *result;
    }

    ucd.handler(ucd.data);
  }

  return 1;
}

int
showDotPattern (unsigned char dots, unsigned char duration) {
  if (braille->writeStatus && (brl.statusColumns > 0)) {
    unsigned int length = brl.statusColumns * brl.statusRows;
    unsigned char cells[length];        /* status cell buffer */
    memset(cells, dots, length);
    if (!braille->writeStatus(&brl, cells)) return 0;
  }

  memset(brl.buffer, dots, brl.textColumns*brl.textRows);
  if (!writeBrailleWindow(&brl, NULL, 0)) return 0;

  drainBrailleOutput(&brl, duration);
  return 1;
}

static void
exitSessions (void *data) {
  cancelDelayedCursorTrackingAlarm();

  if (ses) {
    popCommandEnvironment();
    ses = NULL;
  }

  deallocateSessionEntries();
}

static AsyncEvent *addCoreTaskEvent = NULL;

static int
startCoreTasks (void) {
  if (!addCoreTaskEvent) {
    if (!(addCoreTaskEvent = asyncNewAddTaskEvent())) {
      return 0;
    }
  }

  return 1;
}

static void
stopCoreTasks (void) {
  if (addCoreTaskEvent) {
    asyncDiscardEvent(addCoreTaskEvent);
    addCoreTaskEvent = NULL;
  }
}

static void
logCoreTaskAction (CoreTaskCallback *callback, const char *action) {
  logSymbol(LOG_DEBUG, callback, "%s core task", action);
}

typedef struct {
  struct {
    CoreTaskCallback *callback;
    void *data;
  } run;

  struct {
    AsyncEvent *event;
    unsigned finished:1;
  } wait;
} CoreTaskData;

ASYNC_TASK_CALLBACK(handleCoreTask) {
  CoreTaskData *ctd = data;

  {
    CoreTaskCallback *callback = ctd->run.callback;
    logCoreTaskAction(callback, "starting");
    callback(ctd->run.data);
    logCoreTaskAction(callback, "finished");
  }

  {
    AsyncEvent *event = ctd->wait.event;
    if (event) asyncSignalEvent(event, ctd);
  }
}

ASYNC_CONDITION_TESTER(testCoreTaskFinished) {
  CoreTaskData *ctd = data;
  return ctd->wait.finished;
}

ASYNC_EVENT_CALLBACK(setCoreTaskFinished) {
  CoreTaskData *ctd = parameters->signalData;
  ctd->wait.finished = 1;
}

int
runCoreTask (CoreTaskCallback *callback, void *data, int wait) {
  int wasScheduled = 0;

  if (addCoreTaskEvent) {
    CoreTaskData *ctd;

    if ((ctd = malloc(sizeof(*ctd)))) {
      memset(ctd, 0, sizeof(*ctd));

      ctd->run.callback = callback;
      ctd->run.data = data;

      ctd->wait.event = NULL;
      ctd->wait.finished = 0;

      if (!wait || (ctd->wait.event = asyncNewEvent(setCoreTaskFinished, NULL))) {
        logCoreTaskAction(callback, "scheduling");

        if (asyncAddTask(addCoreTaskEvent, handleCoreTask, ctd)) {
          wasScheduled = 1;

          if (wait) {
            logCoreTaskAction(callback, "awaiting");
            asyncWaitFor(testCoreTaskFinished, ctd);
            logCoreTaskAction(callback, "completed");
          }
        }

        {
          AsyncEvent *event = ctd->wait.event;
          if (event) asyncDiscardEvent(event);
        }
      }

      free(ctd);
    } else {
      logMallocError();
    }
  } else {
    logMessage(LOG_ERR, "core tasks not started");
  }

  return wasScheduled;
}

#ifdef ASYNC_CAN_HANDLE_SIGNALS
ASYNC_SIGNAL_HANDLER(handleProgramTerminationRequest) {
  time_t now = time(NULL);

  int reset = difftime(now, programTerminationRequestTime)
            > PROGRAM_TERMINATION_REQUEST_RESET_SECONDS;

  int count = reset? 0: programTerminationRequestCount;
  if (++count > PROGRAM_TERMINATION_REQUEST_COUNT_THRESHOLD) exit(1);

  programTerminationRequestTime = now;
  programTerminationRequestSignal = signalNumber;

  // This is a memory write barrier to ensure that the time and number
  // for this signal will be visible before its count is adjusted.
  __sync_synchronize();

  programTerminationRequestCount = count;
}

#ifdef SIGCHLD
ASYNC_SIGNAL_HANDLER(handleChildDeath) {
}
#endif /* SIGCHLD */
#endif /* ASYNC_CAN_HANDLE_SIGNALS */

ProgramExitStatus
brlttyConstruct (int argc, char *argv[]) {
  {
    TimeValue now;
    getMonotonicTime(&now);
    srand(now.seconds ^ now.nanoseconds);
  }

  {
    ProgramExitStatus exitStatus = brlttyPrepare(argc, argv);
    if (exitStatus != PROG_EXIT_SUCCESS) return exitStatus;
  }

  programTerminationRequestTime = time(NULL);
  programTerminationRequestSignal = 0;
  programTerminationRequestCount = 0;

#ifdef ASYNC_CAN_BLOCK_SIGNALS
  asyncBlockObtainableSignals();
#endif /* ASYNC_CAN_BLOCK_SIGNALS */

#ifdef ASYNC_CAN_HANDLE_SIGNALS
#ifdef SIGPIPE
  /* We ignore SIGPIPE before calling brlttyStart() so that a driver
   * which uses a broken pipe won't abort program execution.
   */
  asyncIgnoreSignal(SIGPIPE, NULL);
#endif /* SIGPIPE */

#ifdef SIGTERM
  asyncHandleSignal(SIGTERM, handleProgramTerminationRequest, NULL);
#endif /* SIGTERM */

#ifdef SIGINT
  asyncHandleSignal(SIGINT, handleProgramTerminationRequest, NULL);
#endif /* SIGINT */

#ifdef SIGCHLD
  asyncHandleSignal(SIGCHLD, handleChildDeath, NULL);
#endif /* SIGCHLD */
#endif /* ASYNC_CAN_HANDLE_SIGNALS */

  interruptEnabledCount = 0;
  interruptEvent = NULL;
  interruptPending = 0;

  delayedCursorTrackingAlarm = NULL;

  startCoreTasks();
  beginCommandQueue();
  beginUpdates();
  suspendUpdates();

  {
    ProgramExitStatus exitStatus = brlttyStart();
    if (exitStatus != PROG_EXIT_SUCCESS) return exitStatus;
  }

  onProgramExit("sessions", exitSessions, NULL);
  setSessionEntry();
  ses->trkx = scr.posx; ses->trky = scr.posy;
  if (!trackScreenCursor(1)) ses->winx = ses->winy = 0;
  ses->motx = ses->winx; ses->moty = ses->winy;
  ses->spkx = ses->winx; ses->spky = ses->winy;

  resumeUpdates(1);
  return PROG_EXIT_SUCCESS;
}

int
brlttyDestruct (void) {
  if (prefs.saveOnExit) savePreferences();

  suspendUpdates();
  stopCoreTasks();

  endProgram();
  endCommandQueue();
  return 1;
}
