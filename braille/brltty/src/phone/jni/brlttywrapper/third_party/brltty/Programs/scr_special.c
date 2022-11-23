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

/* scr.cc - The screen reading library
 *
 * Note: Although C++, this code requires no standard C++ library.
 * This is important as BRLTTY *must not* rely on too many
 * run-time shared libraries, nor be a huge executable.
 */

#include "prologue.h"

#include "log.h"
#include "scr.h"
#include "scr_special.h"
#include "update.h"
#include "message.h"

#include "scr_frozen.h"
static FrozenScreen frozenScreen;

static int
frozenScreen_construct (void) {
  return frozenScreen.construct(&mainScreen.base);
}

#include "scr_help.h"
static HelpScreen helpScreen;

static int
helpScreen_construct (void) {
  return helpScreen.construct();
}

#include "scr_menu.h"
#include "menu_prefs.h"
static MenuScreen menuScreen;

static int
menuScreen_construct (void) {
  Menu *menu = getPreferencesMenu();
  if (!menu) return 0;

  updateLogMessagesSubmenu();
  return menuScreen.construct(menu);
}

typedef struct {
  const char *const name;
  int (*const construct) (void);
  void (**const destruct) (void);
  BaseScreen *const base;

  const unsigned autoDestruct:1;

  unsigned isConstructed:1;
  unsigned isActive:1;
} SpecialScreenEntry;

#define SPECIAL_SCREEN_INITIALIZER(type) \
  .name = #type, \
  .construct = type ## Screen_construct, \
  .destruct = &type ## Screen.destruct, \
  .base = &type ## Screen.base

static SpecialScreenEntry specialScreenTable[] = {
  [SCR_FROZEN] = {
    SPECIAL_SCREEN_INITIALIZER(frozen),
    .autoDestruct = 1
  },

  [SCR_HELP] = {
    SPECIAL_SCREEN_INITIALIZER(help)
  },

  [SCR_MENU] = {
    SPECIAL_SCREEN_INITIALIZER(menu)
  },
};

static const unsigned char specialScreenCount = ARRAY_COUNT(specialScreenTable);

static SpecialScreenEntry *
getSpecialScreenEntry (SpecialScreenType type) {
  return &specialScreenTable[type];
}

static void
logScreenAction (const char *type, const char *name, const char *action) {
  logMessage(LOG_DEBUG, "%s %s screen: %s", action, type, name);
}

static void
logMainScreenAction (const char *action) {
  logScreenAction("main", screen->definition.name, action);
}

static void
logSpecialScreenAction (const SpecialScreenEntry *sse, const char *action) {
  logScreenAction("special", sse->name, action);
}

static int
constructSpecialScreen (SpecialScreenEntry *sse) {
  if (sse->isConstructed) return !sse->autoDestruct;
  logSpecialScreenAction(sse, "constructing");
  if (!sse->construct()) return 0;
  sse->isConstructed = 1;
  return 1;
}

static void
destructSpecialScreen (SpecialScreenEntry *sse) {
  if (sse->isConstructed) {
    logSpecialScreenAction(sse, "destructing");
    (*sse->destruct)();
    sse->isConstructed = 0;
  }
}

void
beginSpecialScreens (void) {
  initializeFrozenScreen(&frozenScreen);
  initializeMenuScreen(&menuScreen);
  initializeHelpScreen(&helpScreen);
}

void
endSpecialScreens (void) {
  SpecialScreenEntry *sse = specialScreenTable;
  SpecialScreenEntry *end = sse + specialScreenCount;

  while (sse < end) {
    destructSpecialScreen(sse);
    sse += 1;
  }
}

static void
announceCurrentScreen (void) {
  const char *title = currentScreen->getTitle();
  if (title) message(NULL, title, 0);
}

static void
setCurrentScreen (BaseScreen *screen) {
  currentScreen = screen;
  scheduleUpdate("new screen selected");
  announceCurrentScreen();
}

static void
setSpecialScreen (const SpecialScreenEntry *sse) {
  logSpecialScreenAction(sse, "selecting");
  setCurrentScreen(sse->base);
}

static void
selectCurrentScreen (void) {
  const SpecialScreenEntry *sse = specialScreenTable;
  const SpecialScreenEntry *end = sse + specialScreenCount;

  while (sse < end) {
    if (sse->isActive) break;
    sse += 1;
  }

  if (sse == end) {
    logMainScreenAction("selecting");
    setCurrentScreen(&mainScreen.base);
  } else {
    setSpecialScreen(sse);
  }
}

int
activateSpecialScreen (SpecialScreenType type) {
  SpecialScreenEntry *sse = getSpecialScreenEntry(type);

  if (!constructSpecialScreen(sse)) return 0;
  logSpecialScreenAction(sse, "activating");
  sse->isActive = 1;
  setSpecialScreen(sse);
  return 1;
}

void
deactivateSpecialScreen (SpecialScreenType type) {
  SpecialScreenEntry *sse = getSpecialScreenEntry(type);

  logSpecialScreenAction(sse, "deactivating");
  sse->isActive = 0;
  if (sse->autoDestruct) destructSpecialScreen(sse);
  selectCurrentScreen();
}

int
haveSpecialScreen (SpecialScreenType type) {
  return getSpecialScreenEntry(type)->isActive;
}

int
isSpecialScreen (SpecialScreenType type) {
  return currentScreen == getSpecialScreenEntry(type)->base;
}

int
constructHelpScreen (void) {
  SpecialScreenEntry *sse = getSpecialScreenEntry(SCR_HELP);

  return constructSpecialScreen(sse);
}

int
addHelpPage (void) {
  return helpScreen.addPage();
}

unsigned int
getHelpPageCount (void) {
  return helpScreen.getPageCount();
}

unsigned int
getHelpPageNumber (void) {
  return helpScreen.getPageNumber();
}

int
setHelpPageNumber (unsigned int number) {
  return helpScreen.setPageNumber(number);
}

int
clearHelpPage (void) {
  return helpScreen.clearPage();
}

int
addHelpLine (const wchar_t *characters) {
  return helpScreen.addLine(characters);
}

unsigned int
getHelpLineCount (void) {
  return helpScreen.getLineCount();
}
