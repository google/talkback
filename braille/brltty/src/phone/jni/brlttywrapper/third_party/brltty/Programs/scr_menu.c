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
#include <string.h>

#include "log.h"
#include "strfmt.h"
#include "scr.h"
#include "scr_menu.h"
#include "brl_cmds.h"
#include "cmd_queue.h"
#include "alert.h"
#include "charset.h"
#include "core.h"

typedef struct {
  MenuItem *item;
  size_t length;
  size_t settingIndent;
  wchar_t text[0];
} RenderedMenuItem;

static Menu *rootMenu = NULL;

static int screenUpdated;
static RenderedMenuItem **screenLines;
static unsigned int lineCount;
static unsigned int screenHeight;

static Menu *screenMenu;
static unsigned int screenColumn;
static unsigned int screenRow;
static unsigned int screenWidth;

static inline const MenuItem *
getCurrentItem (void) {
  Menu *menu = screenMenu;

  return getMenuItem(menu, getMenuIndex(menu));
}

static inline void
setFocusedItem (void) {
  changeMenuItem(screenLines[ses->winy]->item);
}

static RenderedMenuItem *
newRenderedMenuItem (Menu *menu) {
  MenuItem *item = getCurrentMenuItem(menu);

  char labelString[0X100];
  size_t labelLength;
  {
    const char *title = getMenuItemTitle(item);
    const char *subtitle = getMenuItemSubtitle(item);

    STR_BEGIN(labelString, ARRAY_COUNT(labelString));
    STR_PRINTF("%s", title);

    if (*subtitle) {
      if (*title) STR_PRINTF(" ");
      STR_PRINTF("%s", subtitle);
    }

    if (labelString[0]) {
      if (!isMenuItemAction(item)) STR_PRINTF(":");
      STR_PRINTF(" ");
    }

    labelLength = STR_LENGTH;
    STR_END;
  }

  char settingString[0X100];
  size_t settingLength;
  {
    const char *text = getMenuItemText(item);
    const char *comment = getMenuItemComment(item);

    if (!text) {
      text = "";
    } else if (!*text) {
      text = gettext("<off>");
    }

    STR_BEGIN(settingString, ARRAY_COUNT(settingString));
    STR_PRINTF("%s", text);
    if (*comment) STR_PRINTF(" (%s)", comment);
    settingLength = STR_LENGTH;
    STR_END;
  }

  {
    size_t maximumLength = labelLength + settingLength;
    wchar_t characters[maximumLength];

    size_t settingIndent;
    size_t actualLength;

    {
      size_t currentLength = 0;
       
      currentLength += convertTextToWchars(&characters[currentLength], labelString, maximumLength-currentLength);
      settingIndent = currentLength;

      currentLength += convertTextToWchars(&characters[currentLength], settingString, maximumLength-currentLength);
      actualLength = currentLength;
    }

    {
      RenderedMenuItem *rmi;
      size_t size = sizeof(*rmi) + (actualLength * sizeof(rmi->text[0]));

      if ((rmi = malloc(size))) {
        memset(rmi, 0, sizeof(*rmi));
        wmemcpy(rmi->text, characters, actualLength);

        rmi->item = item;
        rmi->length = actualLength;
        rmi->settingIndent = settingIndent;

        return rmi;
      } else {
        logMallocError();
      }
    }
  }

  return NULL;
}

static void
destroyRenderedMenuItem (RenderedMenuItem *rmi) {
  free(rmi);
}

static void
removeLines (void) {
  while (screenHeight > 0) {
    destroyRenderedMenuItem(screenLines[--screenHeight]);
  }
}

static int
setScreenRow (void) {
  const MenuItem *item = getCurrentItem();

  for (unsigned int row=0; row<screenHeight; row+=1) {
    const RenderedMenuItem *rmi = screenLines[row];

    if (rmi->item == item) {
      screenRow = row;
      return 1;
    }
  }

  return 0;
}

static int
reloadScreen (int constructing) {
  Menu *menu = getCurrentSubmenu(rootMenu);
  unsigned int index = getMenuIndex(menu);

  screenMenu = menu;
  screenWidth = 0;
  removeLines();

  if (changeMenuItemFirst(menu)) {
    {
      unsigned int count = getMenuSize(menu);

      if (count > lineCount) {
        RenderedMenuItem **lines = realloc(screenLines, ARRAY_SIZE(screenLines, count));

        if (!lines) {
          logMallocError();
          return 0;
        }

        screenLines = lines;
        lineCount = count;
      }
    }

    do {
      RenderedMenuItem *rmi = newRenderedMenuItem(menu);

      if (!rmi) return 0;
      screenLines[screenHeight++] = rmi;
      if (screenWidth < rmi->length) screenWidth = rmi->length;
    } while (changeMenuItemNext(menu, 0));

    if (changeMenuItemIndex(menu, index)) {
      if (constructing) {
        screenRow = 0;
        screenColumn = 0;
      } else {
        if (!setScreenRow()) return 0;
        screenColumn = screenLines[screenRow]->settingIndent;
      }

      return 1;
    }
  }

  return 0;
}

static int
refresh_MenuScreen (void) {
  if (screenUpdated) {
    if (!reloadScreen(0)) return 0;
    screenUpdated = 0;
  }

  return 1;
}

static int
construct_MenuScreen (Menu *menu) {
  rootMenu = menu;

  screenUpdated = 0;
  screenLines = NULL;
  lineCount = 0;
  screenHeight = 0;

  return reloadScreen(1);
}

static void
destruct_MenuScreen (void) {
  if (screenLines) {
    removeLines();
    free(screenLines);
    screenLines = NULL;
  }

  rootMenu = NULL;
}

static int
currentVirtualTerminal_MenuScreen (void) {
  return userVirtualTerminal(2 + getMenuNumber(screenMenu));
}

static const char *
getTitle_MenuScreen (void) {
  return gettext("Preferences Menu");
}

static void
describe_MenuScreen (ScreenDescription *description) {
  description->cols = MAX(screenWidth, 1);
  description->rows = MAX(screenHeight, 1);

  description->posx = screenColumn;
  description->posy = screenRow;

  description->number = currentVirtualTerminal_MenuScreen();
}

static int
readCharacters_MenuScreen (const ScreenBox *box, ScreenCharacter *buffer) {
  if (validateScreenBox(box, screenWidth, screenHeight)) {
    ScreenCharacter *character = buffer;

    for (unsigned int row=0; row<box->height; row+=1) {
      const RenderedMenuItem *rmi = screenLines[row + box->top];

      for (unsigned int column=0; column<box->width; column+=1) {
        unsigned int index = column + box->left;

        character->text = (index < rmi->length)? rmi->text[index]: WC_C(' ');
        character->attributes = SCR_COLOUR_DEFAULT;
        character += 1;
      }
    }

    return 1;
  }

  return 0;
}

static void
commandRejected (void) {
  alert(ALERT_COMMAND_REJECTED);
}

static void
itemChanged (void) {
  setScreenRow();
  screenColumn = 0;
}

static void
settingChanged (void) {
  screenUpdated = 1;
}

static int
handleCommand_MenuScreen (int command) {
  switch (command) {
    case BRL_CMD_KEY(BACKSPACE):
    case BRL_CMD_MENU_PREV_LEVEL: {
      Menu *menu = screenMenu;

      if (menu != rootMenu) {
        if (!changeMenuItemIndex(screenMenu, 0)) {
          commandRejected();
        } else if (!changeMenuSettingNext(menu, 0)) {
          commandRejected();
        } else {
          setFocusedItem();
          settingChanged();
        }

        return 1;
      }
    }

    case BRL_CMD_KEY(ESCAPE):
    case BRL_CMD_KEY(ENTER): {
      int handled = handleCommand(BRL_CMD_PREFMENU);

      if (handled) setFocusedItem();
      return handled;
    }

    case BRL_CMD_KEY(HOME): {
      int handled = handleCommand(BRL_CMD_PREFLOAD);

      if (handled) settingChanged();
      return handled;
    }

    case BRL_CMD_KEY(END): {
      int handled = handleCommand(BRL_CMD_PREFSAVE);

      if (handled) setFocusedItem();
      return handled;
    }

    case BRL_CMD_KEY(PAGE_UP):
    case BRL_CMD_MENU_FIRST_ITEM: {
      if (changeMenuItemFirst(screenMenu)) {
        itemChanged();
      } else {
        commandRejected();
      }

      return 1;
    }

    case BRL_CMD_KEY(PAGE_DOWN):
    case BRL_CMD_MENU_LAST_ITEM: {
      if (changeMenuItemLast(screenMenu)) {
        itemChanged();
      } else {
        commandRejected();
      }

      return 1;
    }

    case BRL_CMD_KEY(CURSOR_UP):
    case BRL_CMD_MENU_PREV_ITEM: {
      if (changeMenuItemPrevious(screenMenu, 1)) {
        itemChanged();
      } else {
        commandRejected();
      }

      return 1;
    }

    case BRL_CMD_KEY(CURSOR_DOWN):
    case BRL_CMD_MENU_NEXT_ITEM: {
      if (changeMenuItemNext(screenMenu, 1)) {
        itemChanged();
      } else {
        commandRejected();
      }

      return 1;
    }

    case BRL_CMD_KEY(CURSOR_LEFT):
    case BRL_CMD_BACK:
    case BRL_CMD_MENU_PREV_SETTING: {
      setFocusedItem();

      if (changeMenuSettingPrevious(screenMenu, 1)) {
        settingChanged();
      } else {
        commandRejected();
      }

      return 1;
    }

    case BRL_CMD_KEY(CURSOR_RIGHT):
    case BRL_CMD_HOME:
    case BRL_CMD_RETURN:
    case BRL_CMD_MENU_NEXT_SETTING: {
      setFocusedItem();

      if (changeMenuSettingNext(screenMenu, 1)) {
        settingChanged();
      } else {
        commandRejected();
      }

      return 1;
    }

    case BRL_CMD_CSRJMP_VERT: {
      setFocusedItem();
      return 1;
    }

    default: {
      if ((command & BRL_MSK_BLK) == BRL_CMD_BLK(ROUTE)) {
        unsigned int key = command & BRL_MSK_ARG;
        unsigned int count = brl.textColumns;

        if (key < count) {
          setFocusedItem();

          if (changeMenuSettingScaled(screenMenu, key, count)) {
            settingChanged();
          } else {
            commandRejected();
          }
        } else if ((key >= statusStart) && (key < (statusStart + statusCount))) {
          switch (key - statusStart) {
            default:
              commandRejected();
              break;
          }
        } else {
          commandRejected();
        }

        return 1;
      }

      break;
    }
  }

  return 0;
}

static KeyTableCommandContext
getCommandContext_MenuScreen (void) {
  return KTB_CTX_MENU;
}

void
initializeMenuScreen (MenuScreen *menu) {
  initializeBaseScreen(&menu->base);

  menu->base.currentVirtualTerminal = currentVirtualTerminal_MenuScreen;
  menu->base.getTitle = getTitle_MenuScreen;

  menu->base.refresh = refresh_MenuScreen;
  menu->base.describe = describe_MenuScreen;
  menu->base.readCharacters = readCharacters_MenuScreen;

  menu->base.handleCommand = handleCommand_MenuScreen;
  menu->base.getCommandContext = getCommandContext_MenuScreen;

  menu->construct = construct_MenuScreen;
  menu->destruct = destruct_MenuScreen;
}
