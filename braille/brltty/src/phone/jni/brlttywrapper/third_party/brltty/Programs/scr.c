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

#include <string.h>

#include "log.h"
#include "scr.h"
#include "scr_real.h"
#include "driver.h"

MainScreen mainScreen;
BaseScreen *currentScreen = &mainScreen.base;

int
isMainScreen (void) {
  return currentScreen == &mainScreen.base;
}

const char *const *
getScreenParameters (const ScreenDriver *driver) {
  return driver->parameters;
}

const DriverDefinition *
getScreenDriverDefinition (const ScreenDriver *driver) {
  return &driver->definition;
}

static void
initializeScreen (void) {
  screen->initialize(&mainScreen);
}

void
setNoScreen (void) {
  screen = &noScreen;
  initializeScreen();
}

int
constructScreenDriver (char **parameters) {
  initializeScreen();

  if (mainScreen.processParameters(parameters)) {
    if (mainScreen.construct()) {
      return 1;
    } else {
      logMessage(LOG_DEBUG, "screen driver initialization failed: %s",
                 screen->definition.code);
    }

    mainScreen.releaseParameters();
  }

  return 0;
}

void
destructScreenDriver (void) {
  mainScreen.destruct();
  mainScreen.releaseParameters();
}


int
pollScreen (void) {
  return currentScreen->poll();
}

int
refreshScreen (void) {
  return currentScreen->refresh();
}

void
describeScreen (ScreenDescription *description) {
  describeBaseScreen(currentScreen, description);
}

int
readScreen (short left, short top, short width, short height, ScreenCharacter *buffer) {
  ScreenBox box;
  box.left = left;
  box.top = top;
  box.width = width;
  box.height = height;
  return currentScreen->readCharacters(&box, buffer);
}

int
readScreenText (short left, short top, short width, short height, wchar_t *buffer) {
  unsigned int count = width * height;
  ScreenCharacter characters[count];
  if (!readScreen(left, top, width, height, characters)) return 0;

  {
    int i;
    for (i=0; i<count; ++i) buffer[i] = characters[i].text;
  }

  return 1;
}

int
insertScreenKey (ScreenKey key) {
  logMessage(LOG_CATEGORY(SCREEN_DRIVER), "insert key: 0X%04X", key);
  return currentScreen->insertKey(key);
}

int
routeScreenCursor (int column, int row, int screen) {
  return currentScreen->routeCursor(column, row, screen);
}

int
highlightScreenRegion (int left, int right, int top, int bottom) {
  return currentScreen->highlightRegion(left, right, top, bottom);
}

int
unhighlightScreenRegion (void) {
  return currentScreen->unhighlightRegion();
}

int
getScreenPointer (int *column, int *row) {
  return currentScreen->getPointer(column, row);
}

int
currentVirtualTerminal (void) {
  return currentScreen->currentVirtualTerminal();
}

int
selectScreenVirtualTerminal (int vt) {
  return currentScreen->selectVirtualTerminal(vt);
}

int
switchScreenVirtualTerminal (int vt) {
  return currentScreen->switchVirtualTerminal(vt);
}

int
nextScreenVirtualTerminal (void) {
  return currentScreen->nextVirtualTerminal();
}

int
previousScreenVirtualTerminal (void) {
  return currentScreen->previousVirtualTerminal();
}

int
userVirtualTerminal (int number) {
  return mainScreen.userVirtualTerminal(number);
}

int
handleScreenCommands (int command, void *data) {
  return currentScreen->handleCommand(command);
}

KeyTableCommandContext
getScreenCommandContext (void) {
  return currentScreen->getCommandContext();
}


int
constructRoutingScreen (void) {
  /* This function should be used in a forked process. Though we want to
   * have a separate file descriptor for the main screen from the one used
   * in the main thread.  So we close and reopen the device.
   */
  mainScreen.destruct();
  return mainScreen.construct();
}

void
destructRoutingScreen (void) {
  mainScreen.destruct();
  mainScreen.releaseParameters();
}
