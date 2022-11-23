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

#include "parameters.h"
#include "cmd_queue.h"
#include "cmd_toggle.h"
#include "brl_cmds.h"
#include "prefs.h"
#include "scr.h"
#include "scr_special.h"
#include "alert.h"
#include "tune.h"
#include "core.h"

typedef enum {
  TOGGLE_ERROR,
  TOGGLE_SAME,
  TOGGLE_OFF,
  TOGGLE_ON
} ToggleResult;

static ToggleResult
toggleBit (
  int *bits, int bit, int command,
  AlertIdentifier offAlert,
  AlertIdentifier onAlert
) {
  int oldBits = *bits;

  switch (command & BRL_FLG_TOGGLE_MASK) {
    case 0:
      *bits ^= bit;
      break;

    case BRL_FLG_TOGGLE_ON:
      *bits |= bit;
      break;

    case BRL_FLG_TOGGLE_OFF:
      *bits &= ~bit;
      break;

    default:
      alert(ALERT_COMMAND_REJECTED);
      return TOGGLE_ERROR;
  }

  {
    int isOn = (*bits & bit) != 0;
    AlertIdentifier identifier = isOn? onAlert: offAlert;

    alert(identifier);
    if (*bits != oldBits) return isOn? TOGGLE_ON: TOGGLE_OFF;

    tuneWait(TUNE_TOGGLE_REPEAT_DELAY);
    alert(identifier);
    return TOGGLE_SAME;
  }
}

static ToggleResult
toggleSetting (
  unsigned char *setting, int command,
  AlertIdentifier offAlert,
  AlertIdentifier onAlert
) {
  const int bit = 1;
  int bits = *setting? bit: 0;
  ToggleResult result = toggleBit(&bits, bit, command, offAlert, onAlert);

  *setting = (bits & bit)? bit: 0;
  return result;
}

static ToggleResult
toggleFeatureSetting (unsigned char *setting, int command) {
  return toggleSetting(setting, command, ALERT_TOGGLE_OFF, ALERT_TOGGLE_ON);
}

static ToggleResult
toggleModeSetting (unsigned char *setting, int command) {
  return toggleSetting(setting, command, ALERT_NONE, ALERT_NONE);
}

static int
handleToggleCommands (int command, void *data) {
  switch (command & BRL_MSK_CMD) {
    case BRL_CMD_SKPIDLNS:
      toggleFeatureSetting(&prefs.skipIdenticalLines, command);
      break;

    case BRL_CMD_SKPBLNKWINS:
      toggleFeatureSetting(&prefs.skipBlankBrailleWindows, command);
      break;

    case BRL_CMD_SLIDEWIN:
      toggleFeatureSetting(&prefs.slidingBrailleWindow, command);
      break;

    case BRL_CMD_SIXDOTS:
      toggleFeatureSetting(&prefs.textStyle, command);
      break;

    case BRL_CMD_CSRTRK:
      toggleSetting(&ses->trackScreenCursor, command, ALERT_CURSOR_UNLINKED, ALERT_CURSOR_LINKED);

      if (ses->trackScreenCursor) {
#ifdef ENABLE_SPEECH_SUPPORT
        if (spk.track.isActive && (scr.number == spk.track.screenNumber)) {
          spk.track.speechLocation = SPK_LOC_NONE;
        } else
#endif /* ENABLE_SPEECH_SUPPORT */

        {
          trackScreenCursor(1);
        }
      }
      break;

    case BRL_CMD_CSRSIZE:
      toggleFeatureSetting(&prefs.screenCursorStyle, command);
      break;

    case BRL_CMD_CSRVIS:
      toggleFeatureSetting(&prefs.showScreenCursor, command);
      break;

    case BRL_CMD_CSRHIDE:
      toggleModeSetting(&ses->hideScreenCursor, command);
      break;

    case BRL_CMD_CSRBLINK:
      toggleFeatureSetting(&prefs.blinkingScreenCursor, command);
      break;

    case BRL_CMD_ATTRVIS:
      toggleFeatureSetting(&prefs.showAttributes, command);
      break;

    case BRL_CMD_ATTRBLINK:
      toggleFeatureSetting(&prefs.blinkingAttributes, command);
      break;

    case BRL_CMD_CAPBLINK:
      toggleFeatureSetting(&prefs.blinkingCapitals, command);
      break;

    case BRL_CMD_AUTOREPEAT:
      toggleFeatureSetting(&prefs.autorepeatEnabled, command);
      break;

    case BRL_CMD_BRLKBD:
      toggleFeatureSetting(&prefs.brailleKeyboardEnabled, command);
      break;

    case BRL_CMD_BRLUCDOTS:
      toggleFeatureSetting(&prefs.brailleInputMode, command);
      break;

    case BRL_CMD_TOUCH_NAV:
      toggleFeatureSetting(&prefs.touchNavigation, command);
      break;

    case BRL_CMD_TUNES:
      toggleFeatureSetting(&prefs.alertTunes, command);        /* toggle sound on/off */
      break;

    case BRL_CMD_AUTOSPEAK:
      toggleFeatureSetting(&prefs.autospeak, command);
      break;

    case BRL_CMD_ASPK_SEL_LINE:
      toggleFeatureSetting(&prefs.autospeakSelectedLine, command);
      break;

    case BRL_CMD_ASPK_SEL_CHAR:
      toggleFeatureSetting(&prefs.autospeakSelectedCharacter, command);
      break;

    case BRL_CMD_ASPK_INS_CHARS:
      toggleFeatureSetting(&prefs.autospeakInsertedCharacters, command);
      break;

    case BRL_CMD_ASPK_DEL_CHARS:
      toggleFeatureSetting(&prefs.autospeakDeletedCharacters, command);
      break;

    case BRL_CMD_ASPK_REP_CHARS:
      toggleFeatureSetting(&prefs.autospeakReplacedCharacters, command);
      break;

    case BRL_CMD_ASPK_CMP_WORDS:
      toggleFeatureSetting(&prefs.autospeakCompletedWords, command);
      break;

    case BRL_CMD_ASPK_INDENT:
      toggleFeatureSetting(&prefs.autospeakLineIndent, command);
      break;

    case BRL_CMD_SHOW_CURR_LOCN:
      toggleFeatureSetting(&prefs.showSpeechCursor, command);
      break;

    case BRL_CMD_INFO:
      if (haveStatusCells() || !(textMaximized || statusCount)) {
        toggleModeSetting(&infoMode, command);
      } else {
        ToggleResult result = toggleModeSetting(&textMaximized, command);
        if (result > TOGGLE_SAME) reconfigureBrailleWindow();
      }
      break;

    case BRL_CMD_DISPMD:
      toggleModeSetting(&ses->displayMode, command);
      break;

    case BRL_CMD_FREEZE: {
      unsigned char setting;

      if (isMainScreen()) {
        setting = 0;
      } else if (isSpecialScreen(SCR_FROZEN)) {
        setting = 1;
      } else {
        alert(ALERT_COMMAND_REJECTED);
        break;
      }

      switch (toggleSetting(&setting, command, ALERT_SCREEN_UNFROZEN, ALERT_SCREEN_FROZEN)) {
        case TOGGLE_OFF:
          deactivateSpecialScreen(SCR_FROZEN);
          break;

        case TOGGLE_ON:
          if (!activateSpecialScreen(SCR_FROZEN)) alert(ALERT_COMMAND_REJECTED);
          break;

        default:
          break;
      }

      break;
    }

    default:
      return 0;
  }

  return 1;
}

int
addToggleCommands (void) {
  return pushCommandHandler("toggle", KTB_CTX_DEFAULT,
                            handleToggleCommands, NULL, NULL);
}
