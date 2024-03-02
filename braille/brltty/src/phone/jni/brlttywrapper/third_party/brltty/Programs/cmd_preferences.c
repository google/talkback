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

#include <string.h>

#include "log.h"
#include "cmd_queue.h"
#include "cmd_preferences.h"
#include "brl_cmds.h"
#include "prefs.h"
#include "menu.h"
#include "menu_prefs.h"
#include "scr_special.h"
#include "scr_menu.h"
#include "message.h"
#include "alert.h"
#include "core.h"

typedef struct {
  PreferenceSettings savedPreferences;
} PreferencesCommandData;

static int
save (void) {
  int saved = savePreferences();

  if (saved) {
    alert(ALERT_COMMAND_DONE);
  } else {
    message(NULL, gettext("not saved"), 0);
  }

  return saved;
}

static int
handlePreferencesCommands (int command, void *data) {
  static const char modeString_preferences[] = "prf";
  PreferencesCommandData *pcd = data;

  switch (command & BRL_MSK_CMD) {
    case BRL_CMD_PREFMENU: {
      int ok = 0;

      if (isSpecialScreen(SCR_MENU)) {
        if (prefs.saveOnExit) save();
        deactivateSpecialScreen(SCR_MENU);
        ok = 1;
      } else if (activateSpecialScreen(SCR_MENU)) {
        updateLogMessagesSubmenu();
        updateSessionAttributes();
        pcd->savedPreferences = prefs;
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
        save();
        deactivateSpecialScreen(SCR_MENU);
      } else if (!save()) {
        alert(ALERT_COMMAND_REJECTED);
      }
      break;

    case BRL_CMD_PREFLOAD:
      if (isSpecialScreen(SCR_MENU)) {
        setPreferences(&pcd->savedPreferences);
        menuScreenUpdated();
        message(modeString_preferences, gettext("changes discarded"), 0);
      } else if (loadPreferences(0)) {
        menuScreenUpdated();
        alert(ALERT_COMMAND_DONE);
      } else {
        alert(ALERT_COMMAND_REJECTED);
      }
      break;

    case BRL_CMD_PREFRESET:
      if (loadPreferences(1)) {
        menuScreenUpdated();
        alert(ALERT_COMMAND_DONE);
      } else {
        alert(ALERT_COMMAND_REJECTED);
      }
      break;

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

static void
destroyPreferencesCommandData (void *data) {
  PreferencesCommandData *pcd = data;
  free(pcd);
}

int
addPreferencesCommands (void) {
  PreferencesCommandData *pcd;

  if ((pcd = malloc(sizeof(*pcd)))) {
    memset(pcd, 0, sizeof(*pcd));

    if (pushCommandHandler("preferences", KTB_CTX_DEFAULT,
                           handlePreferencesCommands, destroyPreferencesCommandData, pcd)) {
      return 1;
    }

    free(pcd);
  } else {
    logMallocError();
  }

  return 0;
}
