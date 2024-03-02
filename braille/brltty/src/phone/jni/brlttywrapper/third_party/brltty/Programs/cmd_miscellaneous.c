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

#include "strfmt.h"
#include "cmd_queue.h"
#include "cmd_miscellaneous.h"
#include "cmd_utils.h"
#include "brl_cmds.h"
#include "prefs.h"
#include "scr_special.h"
#include "message.h"
#include "alert.h"
#include "core.h"

#ifdef ENABLE_SPEECH_SUPPORT
static
STR_BEGIN_FORMATTER(formatSpeechDate, const TimeFormattingData *fmt)
  const char *yearFormat = "%u";
  const char *monthFormat = "%s";
  const char *dayFormat = "%u";

  uint16_t year = fmt->components.year;
  uint8_t day = fmt->components.day + 1;

  char month[0X20];
  size_t length = strftime(month, sizeof(month), "%B", &fmt->components.time);
  month[length] = 0;

  switch (prefs.dateFormat) {
    default:
    case dfYearMonthDay:
      STR_PRINTF(yearFormat, year);
      STR_PRINTF(" ");
      STR_PRINTF(monthFormat, month);
      STR_PRINTF(" ");
      STR_PRINTF(dayFormat, day);
      break;

    case dfMonthDayYear:
      STR_PRINTF(monthFormat, month);
      STR_PRINTF(" ");
      STR_PRINTF(dayFormat, day);
      STR_PRINTF(", ");
      STR_PRINTF(yearFormat, year);
      break;

    case dfDayMonthYear:
      STR_PRINTF(dayFormat, day);
      STR_PRINTF(" ");
      STR_PRINTF(monthFormat, month);
      STR_PRINTF(", ");
      STR_PRINTF(yearFormat, year);
      break;
  }
STR_END_FORMATTER

static
STR_BEGIN_FORMATTER(formatSpeechTime, const TimeFormattingData *fmt)
  unsigned int hours = fmt->components.hour;
  unsigned int minutes = fmt->components.minute;
  unsigned int seconds = fmt->components.second;

  if (minutes > 0) {
    STR_PRINTF("%u", hours);
    if (minutes < 10) STR_PRINTF(" 0");
    STR_PRINTF(" %u", minutes);
  } else if (!fmt->meridian) {
    // xgettext: This is how to say when the time is exactly on (i.e. zero minutes after) an hour.
    // xgettext: (%u represents the number of hours)
    STR_PRINTF(ngettext("%u o'clock", "%u o'clock", hours), hours);
  }

  if (fmt->meridian) {
    const char *character = fmt->meridian;
    while (*character) STR_PRINTF(" %c", *character++);
  }

  if (prefs.showSeconds) {
    STR_PRINTF(", ");

    if (seconds == 0) {
      // xgettext: This is the term used when the time is exactly on (i.e. zero seconds after) a minute.
      STR_PRINTF("%s", gettext("exactly"));
    } else {
      STR_PRINTF("%s", gettext("and"));

      // xgettext: This is a number (%u) of seconds (time units).
      STR_PRINTF(ngettext("%u second", "%u seconds", seconds), seconds);
    }
  }
STR_END_FORMATTER

static void
speakTime (const TimeFormattingData *fmt) {
  char announcement[0X100];
  char time[0X80];

  STR_BEGIN(announcement, sizeof(announcement));
  formatSpeechTime(time, sizeof(time), fmt);

  if (prefs.datePosition == dpNone) {
    STR_PRINTF("%s", time);
  } else {
    char date[0X40];
    formatSpeechDate(date, sizeof(date), fmt);

    switch (prefs.datePosition) {
      case dpBeforeTime:
        STR_PRINTF("%s, %s", date, time);
        break;

      case dpAfterTime:
        STR_PRINTF("%s, %s", time, date);
        break;

      default:
        STR_PRINTF("%s", date);
        break;
    }
  }

  STR_PRINTF(".");
  STR_END;
  sayString(&spk, announcement, SAY_OPT_MUTE_FIRST);
}
#endif /* ENABLE_SPEECH_SUPPORT */

static void
showTime (const TimeFormattingData *fmt) {
  char buffer[0X80];

  formatBrailleTime(buffer, sizeof(buffer), fmt);
  message(NULL, buffer, MSG_SILENT);
}

static int
handleMiscellaneousCommands (int command, void *data) {
  switch (command & BRL_MSK_CMD) {
    case BRL_CMD_RESTARTBRL:
      brl.hasFailed = 1;
      break;

    case BRL_CMD_BRL_STOP:
      disableBrailleDriver(gettext("braille driver stopped"));
      break;

    case BRL_CMD_BRL_START:
      enableBrailleDriver();
      break;

    case BRL_CMD_SCR_STOP:
      disableScreenDriver(gettext("screen driver stopped"));
      break;

    case BRL_CMD_SCR_START:
      enableScreenDriver();
      break;

    case BRL_CMD_HELP: {
      int ok = 0;
      unsigned int pageNumber;

      if (isSpecialScreen(SCR_HELP)) {
        pageNumber = getHelpPageNumber() + 1;
        ok = 1;
      } else {
        pageNumber = haveSpecialScreen(SCR_HELP)? getHelpPageNumber(): 1;
        if (!activateSpecialScreen(SCR_HELP)) pageNumber = 0;
      }

      if (pageNumber) {
        unsigned int pageCount = getHelpPageCount();

        while (pageNumber <= pageCount) {
          if (setHelpPageNumber(pageNumber))
            if (getHelpLineCount())
              break;

          pageNumber += 1;
        }

        if (pageNumber > pageCount) {
          deactivateSpecialScreen(SCR_HELP);
        } else {
          ok = 1;
        }

        updateSessionAttributes();
      }

      if (ok) {
        infoMode = 0;
      } else {
        message(NULL, gettext("help not available"), 0);
      }

      break;
    }

    case BRL_CMD_TIME: {
      TimeFormattingData fmt;
      getTimeFormattingData(&fmt);

#ifdef ENABLE_SPEECH_SUPPORT
      if (isAutospeakActive()) speakTime(&fmt);
#endif /* ENABLE_SPEECH_SUPPORT */

      showTime(&fmt);
      break;
    }

    case BRL_CMD_REFRESH: {
      if (canRefreshBrailleDisplay(&brl)) {
        if (refreshBrailleDisplay(&brl)) {
          break;
        }
      }

      alert(ALERT_COMMAND_REJECTED);
      break;
    }

    default: {
      int arg = command & BRL_MSK_ARG;

      switch (command & BRL_MSK_BLK) {
        case BRL_CMD_BLK(DESCCHAR): {
          int column, row;

          if (getCharacterCoordinates(arg, &row, &column, NULL, 0)) {
            char description[0X80];
            STR_BEGIN(description, sizeof(description));
            STR_FORMAT(formatCharacterDescription, column, row);
            STR_END;
            message(NULL, description, 0);
          } else {
            alert(ALERT_COMMAND_REJECTED);
          }

          break;
        }

        case BRL_CMD_BLK(REFRESH_LINE): {
          if (canRefreshBrailleRow(&brl)) {
            if (refreshBrailleRow(&brl, arg)) {
              break;
            }
          }

          alert(ALERT_COMMAND_REJECTED);
          break;
        }

        case BRL_CMD_BLK(ALERT):
          alert(arg);
          break;

        default:
          return 0;
      }

      break;
    }
  }

  return 1;
}

int
addMiscellaneousCommands (void) {
  return pushCommandHandler("miscellaneous", KTB_CTX_DEFAULT,
                            handleMiscellaneousCommands, NULL, NULL);
}
