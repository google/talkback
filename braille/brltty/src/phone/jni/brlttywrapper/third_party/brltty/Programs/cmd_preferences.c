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

#include "cmd_queue.h"
#include "cmd_preferences.h"
#include "brl_cmds.h"
#include "prefs.h"
#include "menu.h"
#include "menu_prefs.h"
#include "scr_special.h"
#include "message.h"
#include "alert.h"
#include "core.h"

static int
handlePreferencesCommands (int command, void *data) {
  switch (command & BRL_MSK_CMD) {
    {
      static const char modeString_preferences[] = "prf";
      static Preferences savedPreferences;

    case BRL_CMD_PREFMENU: {
      int ok = 0;

      if (isSpecialScreen(SCR_MENU)) {
        if (prefs.saveOnExit) {
          if (savePreferences()) {
            alert(ALERT_COMMAND_DONE);
          }
        }

        deactivateSpecialScreen(SCR_MENU);
        ok = 1;
      } else if (activateSpecialScreen(SCR_MENU)) {
        updateLogMessagesSubmenu();
        updateSessionAttributes();
        savedPreferences = prefs;
        ok = 1;
      }

      if (ok) {
        infoMode = 0;
      } else {
        alert(ALERT_COMMAND_REJECTED);
      }

      break;
    }

    case BRL_CMD_PREFSAVE:
      if (isSpecialScreen(SCR_MENU)) {
        if (savePreferences()) alert(ALERT_COMMAND_DONE);
        deactivateSpecialScreen(SCR_MENU);
      } else if (savePreferences()) {
        alert(ALERT_COMMAND_DONE);
      } else {
        alert(ALERT_COMMAND_REJECTED);
      }
      break;

    case BRL_CMD_PREFLOAD:
      if (isSpecialScreen(SCR_MENU)) {
        setPreferences(&savedPreferences);
        message(modeString_preferences, gettext("changes discarded"), 0);
      } else if (loadPreferences()) {
        alert(ALERT_COMMAND_DONE);
      } else {
        alert(ALERT_COMMAND_REJECTED);
      }
      break;
    }

    default: {
      int arg = command & BRL_MSK_ARG;

      switch (command & BRL_MSK_BLK) {
        {
          MenuItem *item;

        case BRL_CMD_BLK(SET_TEXT_TABLE):
          item = getPreferencesMenuItem_textTable();
          goto doSetMenuItem;

        case BRL_CMD_BLK(SET_ATTRIBUTES_TABLE):
          item = getPreferencesMenuItem_attributesTable();
          goto doSetMenuItem;

        case BRL_CMD_BLK(SET_CONTRACTION_TABLE):
          item = getPreferencesMenuItem_contractionTable();
          goto doSetMenuItem;

        case BRL_CMD_BLK(SET_KEYBOARD_TABLE):
          item = getPreferencesMenuItem_keyboardTable();
          goto doSetMenuItem;

        case BRL_CMD_BLK(SET_LANGUAGE_PROFILE):
          item = getPreferencesMenuItem_languageProfile();
          goto doSetMenuItem;

        doSetMenuItem:
          if (item) {
            unsigned int count = brl.textColumns;

            if (count <= arg) count = arg + 1;
            changeMenuItem(item);

            if (changeMenuSettingScaled(getMenuItemMenu(item), arg, count)) {
              break;
            }
          }

          alert(ALERT_COMMAND_REJECTED);
          break;
        }

        default:
          return 0;
      }

      break;
    }
  }

  return 1;
}

int
addPreferencesCommands (void) {
  return pushCommandHandler("preferences", KTB_CTX_DEFAULT,
                            handlePreferencesCommands, NULL, NULL);
}
