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

#include "embed.h"
#include "cmd_queue.h"
#include "cmd_speech.h"
#include "cmd_utils.h"
#include "brl_cmds.h"
#include "prefs.h"
#include "alert.h"
#include "spk.h"
#include "scr.h"
#include "update.h"
#include "core.h"

#ifdef ENABLE_SPEECH_SUPPORT
static void
sayScreenRegion (int left, int top, int width, int height, int track, SayMode mode) {
  size_t count = width * height;
  ScreenCharacter characters[count];

  if (mode == sayImmediate) muteSpeech(&spk, __func__);
  readScreen(left, top, width, height, characters);
  spk.track.isActive = track;
  spk.track.screenNumber = scr.number;
  spk.track.firstLine = top;
  spk.track.speechLocation = SPK_LOC_NONE;
  sayScreenCharacters(characters, count, 0);
}

static void
sayScreenLines (int line, int count, int track, SayMode mode) {
  sayScreenRegion(0, line, scr.cols, count, track, mode);
}

static void
speakDone (const ScreenCharacter *line, int column, int count, int spell) {
  ScreenCharacter internalBuffer[count];

  if (line) {
    line = &line[column];
  } else {
    readScreen(column, ses->spky, count, 1, internalBuffer);
    line = internalBuffer;
  }

  speakCharacters(line, count, spell, 1);
  placeBrailleWindowHorizontally(ses->spkx);
  slideBrailleWindowVertically(ses->spky);
  suppressAutospeak();
}

static void
speakCurrentCharacter (void) {
  speakDone(NULL, ses->spkx, 1, 0);
}

static void
speakCurrentLine (void) {
  speakDone(NULL, 0, scr.cols, 0);
}

static int
handleSpeechCommands (int command, void *data) {
  switch (command & BRL_MSK_CMD) {
    case BRL_CMD_RESTARTSPEECH:
      restartSpeechDriver();
      break;

    case BRL_CMD_SPK_STOP:
      disableSpeechDriver(gettext("speech driver stopped"));
      break;

    case BRL_CMD_SPK_START:
      enableSpeechDriver(1);
      break;

    case BRL_CMD_SPKHOME:
      if (scr.number == spk.track.screenNumber) {
        trackSpeech();
      } else {
        alert(ALERT_COMMAND_REJECTED);
      }
      break;

    case BRL_CMD_MUTE:
      muteSpeech(&spk, "command");
      break;

    case BRL_CMD_SAY_LINE:
      sayScreenLines(ses->winy, 1, 0, prefs.sayLineMode);
      break;
    case BRL_CMD_SAY_ABOVE:
      sayScreenLines(0, ses->winy+1, 1, sayImmediate);
      break;
    case BRL_CMD_SAY_BELOW:
      sayScreenLines(ses->winy, scr.rows-ses->winy, 1, sayImmediate);
      break;

    case BRL_CMD_SAY_SLOWER:
      if (!canSetSpeechRate(&spk)) {
        alert(ALERT_COMMAND_REJECTED);
      } else if (prefs.speechRate > 0) {
        setSpeechRate(&spk, --prefs.speechRate, 1);
      } else {
        alert(ALERT_NO_CHANGE);
      }
      break;

    case BRL_CMD_SAY_FASTER:
      if (!canSetSpeechRate(&spk)) {
        alert(ALERT_COMMAND_REJECTED);
      } else if (prefs.speechRate < SPK_RATE_MAXIMUM) {
        setSpeechRate(&spk, ++prefs.speechRate, 1);
      } else {
        alert(ALERT_NO_CHANGE);
      }
      break;

    case BRL_CMD_SAY_SOFTER:
      if (!canSetSpeechVolume(&spk)) {
        alert(ALERT_COMMAND_REJECTED);
      } else if (prefs.speechVolume > 0) {
        setSpeechVolume(&spk, --prefs.speechVolume, 1);
      } else {
        alert(ALERT_NO_CHANGE);
      }
      break;

    case BRL_CMD_SAY_LOUDER:
      if (!canSetSpeechVolume(&spk)) {
        alert(ALERT_COMMAND_REJECTED);
      } else if (prefs.speechVolume < SPK_VOLUME_MAXIMUM) {
        setSpeechVolume(&spk, ++prefs.speechVolume, 1);
      } else {
        alert(ALERT_NO_CHANGE);
      }
      break;

    case BRL_CMD_SPEAK_CURR_CHAR:
      speakCurrentCharacter();
      break;

    case BRL_CMD_SPEAK_PREV_CHAR:
      if (ses->spkx > 0) {
        ses->spkx -= 1;
        speakCurrentCharacter();
      } else if (ses->spky > 0) {
        ses->spky -= 1;
        ses->spkx = scr.cols - 1;
        alert(ALERT_WRAP_UP);
        speakCurrentCharacter();
      } else {
        alert(ALERT_BOUNCE);
      }
      break;

    case BRL_CMD_SPEAK_NEXT_CHAR:
      if (ses->spkx < (scr.cols - 1)) {
        ses->spkx += 1;
        speakCurrentCharacter();
      } else if (ses->spky < (scr.rows - 1)) {
        ses->spky += 1;
        ses->spkx = 0;
        alert(ALERT_WRAP_DOWN);
        speakCurrentCharacter();
      } else {
        alert(ALERT_BOUNCE);
      }
      break;

    case BRL_CMD_SPEAK_FRST_CHAR: {
      ScreenCharacter characters[scr.cols];
      int column;

      readScreenRow(ses->spky, scr.cols, characters);
      if ((column = findFirstNonSpaceCharacter(characters, scr.cols)) >= 0) {
        ses->spkx = column;
        speakDone(characters, column, 1, 0);
      } else {
        alert(ALERT_COMMAND_REJECTED);
      }

      break;
    }

    case BRL_CMD_SPEAK_LAST_CHAR: {
      ScreenCharacter characters[scr.cols];
      int column;

      readScreenRow(ses->spky, scr.cols, characters);
      if ((column = findLastNonSpaceCharacter(characters, scr.cols)) >= 0) {
        ses->spkx = column;
        speakDone(characters, column, 1, 0);
      } else {
        alert(ALERT_COMMAND_REJECTED);
      }

      break;
    }

    {
      int direction;
      int spell;

    case BRL_CMD_SPEAK_PREV_WORD:
      direction = -1;
      spell = 0;
      goto speakWord;

    case BRL_CMD_SPEAK_NEXT_WORD:
      direction = 1;
      spell = 0;
      goto speakWord;

    case BRL_CMD_SPEAK_CURR_WORD:
      direction = 0;
      spell = 0;
      goto speakWord;

    case BRL_CMD_SPELL_CURR_WORD:
      direction = 0;
      spell = 1;
      goto speakWord;

    speakWord:
      {
        int row = ses->spky;
        int column = ses->spkx;

        ScreenCharacter characters[scr.cols];
        ScreenCharacterType type;
        int onCurrentWord;

        int from = column;
        int to = from + 1;

      findWord:
        readScreenRow(row, scr.cols, characters);
        type = (row == ses->spky)? getScreenCharacterType(&characters[column]): SCT_SPACE;
        onCurrentWord = type != SCT_SPACE;

        if (direction < 0) {
          while (1) {
            if (column == 0) {
              if ((type != SCT_SPACE) && !onCurrentWord) {
                ses->spkx = from = column;
                ses->spky = row;
                break;
              }

              if (row == 0) goto noWord;
              if (row-- == ses->spky) alert(ALERT_WRAP_UP);
              column = scr.cols;
              goto findWord;
            }

            {
              ScreenCharacterType newType = getScreenCharacterType(&characters[--column]);

              if (newType != type) {
                if (onCurrentWord) {
                  onCurrentWord = 0;
                } else if (type != SCT_SPACE) {
                  ses->spkx = from = column + 1;
                  ses->spky = row;
                  break;
                }

                if (newType != SCT_SPACE) to = column + 1;
                type = newType;
              }
            }
          }
        } else if (direction > 0) {
          while (1) {
            if (++column == scr.cols) {
              if ((type != SCT_SPACE) && !onCurrentWord) {
                to = column;
                ses->spkx = from;
                ses->spky = row;
                break;
              }

              if (row == (scr.rows - 1)) goto noWord;
              if (row++ == ses->spky) alert(ALERT_WRAP_DOWN);
              column = -1;
              goto findWord;
            }

            {
              ScreenCharacterType newType = getScreenCharacterType(&characters[column]);

              if (newType != type) {
                if (onCurrentWord) {
                  onCurrentWord = 0;
                } else if (type != SCT_SPACE) {
                  to = column;
                  ses->spkx = from;
                  ses->spky = row;
                  break;
                }

                if (newType != SCT_SPACE) from = column;
                type = newType;
              }
            }
          }
        } else if (type != SCT_SPACE) {
          while (from > 0) {
            if (getScreenCharacterType(&characters[--from]) != type) {
              from += 1;
              break;
            }
          }

          while (to < scr.cols) {
            if (getScreenCharacterType(&characters[to]) != type) break;
            to += 1;
          }
        }

        speakDone(characters, from, to-from, spell);
        break;
      }

    noWord:
      alert(ALERT_BOUNCE);
      break;
    }

    case BRL_CMD_SPEAK_CURR_LINE:
      speakCurrentLine();
      break;

    {
      int increment;
      int limit;

    case BRL_CMD_SPEAK_PREV_LINE:
      increment = -1;
      limit = 0;
      goto speakLine;

    case BRL_CMD_SPEAK_NEXT_LINE:
      increment = 1;
      limit = scr.rows - 1;
      goto speakLine;

    speakLine:
      if (ses->spky == limit) {
        alert(ALERT_BOUNCE);
      } else {
        if (prefs.skipIdenticalLines) {
          ScreenCharacter original[scr.cols];
          ScreenCharacter current[scr.cols];
          unsigned int count = 0;

          readScreenRow(ses->spky, scr.cols, original);

          do {
            readScreenRow(ses->spky+=increment, scr.cols, current);
            if (!isSameRow(original, current, scr.cols, isSameText)) break;
            alertLineSkipped(&count);
          } while (ses->spky != limit);
        } else {
          ses->spky += increment;
        }

        speakCurrentLine();
      }

      break;
    }

    case BRL_CMD_SPEAK_FRST_LINE: {
      ScreenCharacter characters[scr.cols];
      int row = 0;

      while (row < scr.rows) {
        readScreenRow(row, scr.cols, characters);
        if (!isAllSpaceCharacters(characters, scr.cols)) break;
        row += 1;
      }

      if (row < scr.rows) {
        ses->spky = row;
        ses->spkx = 0;
        speakCurrentLine();
      } else {
        alert(ALERT_COMMAND_REJECTED);
      }

      break;
    }

    case BRL_CMD_SPEAK_LAST_LINE: {
      ScreenCharacter characters[scr.cols];
      int row = scr.rows - 1;

      while (row >= 0) {
        readScreenRow(row, scr.cols, characters);
        if (!isAllSpaceCharacters(characters, scr.cols)) break;
        row -= 1;
      }

      if (row >= 0) {
        ses->spky = row;
        ses->spkx = 0;
        speakCurrentLine();
      } else {
        alert(ALERT_COMMAND_REJECTED);
      }

      break;
    }

    case BRL_CMD_DESC_CURR_CHAR: {
      char description[0X50];
      formatCharacterDescription(description, sizeof(description), ses->spkx, ses->spky);
      sayString(&spk, description, SAY_OPT_MUTE_FIRST);
      break;
    }

    case BRL_CMD_ROUTE_CURR_LOCN:
      if (routeScreenCursor(ses->spkx, ses->spky, scr.number)) {
        alert(ALERT_ROUTING_STARTED);
      } else {
        alert(ALERT_COMMAND_REJECTED);
      }
      break;

    case BRL_CMD_SPEAK_CURR_LOCN: {
      char buffer[0X50];
      snprintf(buffer, sizeof(buffer), "%s %d, %s %d",
               gettext("line"), ses->spky+1,
               gettext("column"), ses->spkx+1);
      sayString(&spk, buffer, SAY_OPT_MUTE_FIRST);
      break;
    }

    case BRL_CMD_SPEAK_INDENT:
      speakIndent(NULL, 0, 1);
      break;

    default:
      return 0;
  }

  return 1;
}
#endif /* ENABLE_SPEECH_SUPPORT */

int
addSpeechCommands (void) {
#ifdef ENABLE_SPEECH_SUPPORT
  return pushCommandHandler("speech", KTB_CTX_DEFAULT,
                            handleSpeechCommands, NULL, NULL);
#else /* ENABLE_SPEECH_SUPPORT */
  return 0;
#endif /* ENABLE_SPEECH_SUPPORT */
}
