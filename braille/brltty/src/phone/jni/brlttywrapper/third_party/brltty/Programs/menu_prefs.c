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
#include "log_history.h"
#include "embed.h"
#include "revision.h"
#include "menu.h"
#include "menu_prefs.h"
#include "prefs.h"
#include "profile.h"
#include "status_types.h"
#include "timing.h"
#include "ttb.h"
#include "atb.h"
#include "ctb.h"
#include "ktb.h"
#include "tune.h"
#include "bell.h"
#include "leds.h"
#include "midi.h"
#include "core.h"

#define PREFS_MENU_ITEM_VARIABLE(name) prefsMenuItemVariable_##name
#define PREFS_MENU_ITEM_GETTER_DECLARE(name) \
static MenuItem *PREFS_MENU_ITEM_VARIABLE(name) = NULL; \
PREFS_MENU_ITEM_GETTER_PROTOTYPE(name) { \
  return getPreferencesMenu()? PREFS_MENU_ITEM_VARIABLE(name): NULL; \
}

PREFS_MENU_ITEM_APPLY(PREFS_MENU_ITEM_GETTER_DECLARE)

#define NAME(name) static const MenuString itemName = {.label=name}
#define ITEM(new) MenuItem *item = (new); if (!item) goto noItem
#define TEST(property) setMenuItemTester(item, test##property)
#define CHANGED(property) setMenuItemChanged(item, changed##property)
#define SET(name) PREFS_MENU_ITEM_VARIABLE(name) = item

#define SUBMENU(variable, parent, name) \
  NAME(name); \
  Menu *variable = newSubmenuMenuItem(parent, &itemName); \
  if (!variable) goto noItem

static int
testAdvancedSubmenu (void) {
  return prefs.showAdvancedSubmenus;
}

static void
setAdvancedSubmenu (Menu *submenu) {
  Menu *parent = getMenuParent(submenu);

  if (parent) {
    unsigned int size = getMenuSize(parent);

    if (size) {
      MenuItem *item = getMenuItem(parent, size-1);

      if (item) setMenuItemTester(item, testAdvancedSubmenu);
    }
  }
}

static int
testSlidingBrailleWindow (void) {
  return prefs.slidingBrailleWindow;
}

static int
changedBrailleWindowOverlap (const MenuItem *item UNUSED, unsigned char setting) {
  if (setting >= textCount) return 0;
  reconfigureBrailleWindow();
  return 1;
}

static int
changedAutoreleaseTime (const MenuItem *item UNUSED, unsigned char setting) {
  if (brl.keyTable) setKeyAutoreleaseTime(brl.keyTable, setting);
  return 1;
}

static int
testAutorepeatEnabled (void) {
  return prefs.autorepeatEnabled;
}

static int
setAutorepeat (BrailleDisplay *brl, int on, int delay, int interval) {
  if (!brl->setAutorepeat) return 1;
  return setBrailleAutorepeat(brl, on, delay, interval);
}

static int
changedAutorepeatEnabled (const MenuItem *item UNUSED, unsigned char setting) {
  return setAutorepeat(&brl, setting,
                       PREFERENCES_TIME(prefs.longPressTime),
                       PREFERENCES_TIME(prefs.autorepeatInterval));
}

static int
changedAutorepeatDelay (const MenuItem *item UNUSED, unsigned char setting) {
  return setAutorepeat(&brl, prefs.autorepeatEnabled,
                       setting,
                       PREFERENCES_TIME(prefs.autorepeatInterval));
}

static int
changedAutorepeatInterval (const MenuItem *item UNUSED, unsigned char setting) {
  return setAutorepeat(&brl, prefs.autorepeatEnabled,
                       PREFERENCES_TIME(prefs.longPressTime),
                       setting);
}

static int
testShowScreenCursor (void) {
  return prefs.showScreenCursor;
}

static int
testBlinkingScreenCursor (void) {
  return testShowScreenCursor() && prefs.blinkingScreenCursor;
}

static int
testShowAttributes (void) {
  return prefs.showAttributes;
}

static int
testBlinkingAttributes (void) {
  return testShowAttributes() && prefs.blinkingAttributes;
}

static int
testBlinkingCapitals (void) {
  return prefs.blinkingCapitals;
}

static int
testBrailleFirmness (void) {
  return canSetBrailleFirmness(&brl);
}

static int
changedBrailleFirmness (const MenuItem *item UNUSED, unsigned char setting) {
  return setBrailleFirmness(&brl, setting);
}

static int
testTouchSensitivity (void) {
  return canSetTouchSensitivity(&brl);
}

static int
changedTouchSensitivity (const MenuItem *item UNUSED, unsigned char setting) {
  return setTouchSensitivity(&brl, setting);
}

static int
testBrailleDisplayOrientation (void) {
  return brl.rotateInput != NULL;
}

static int
testConsoleBellAlert (void) {
  return canMonitorConsoleBell();
}

static int
changedConsoleBellAlert (const MenuItem *item UNUSED, unsigned char setting) {
  return setConsoleBellMonitoring(setting);
}

static int
testKeyboardLedAlerts (void) {
  return canMonitorLeds();
}

static int
changedKeyboardLedAlerts (const MenuItem *item UNUSED, unsigned char setting) {
  return setLedMonitoring(setting);
}

static int
testTunes (void) {
  return prefs.alertTunes;
}

static int
changedTuneDevice (const MenuItem *item UNUSED, unsigned char setting) {
  return tuneSetDevice(setting);
}

#ifdef HAVE_PCM_SUPPORT
static int
testTunesPcm (void) {
  return testTunes() && (prefs.tuneDevice == tdPcm);
}
#endif /* HAVE_PCM_SUPPORT */

#ifdef HAVE_MIDI_SUPPORT
static int
testTunesMidi (void) {
  return testTunes() && (prefs.tuneDevice == tdMidi);
}
#endif /* HAVE_MIDI_SUPPORT */

#ifdef HAVE_FM_SUPPORT
static int
testTunesFm (void) {
  return testTunes() && (prefs.tuneDevice == tdFm);
}
#endif /* HAVE_FM_SUPPORT */

#ifdef ENABLE_SPEECH_SUPPORT
static int
testSpeechVolume (void) {
  return canSetSpeechVolume(&spk);
}

static int
changedSpeechVolume (const MenuItem *item UNUSED, unsigned char setting) {
  return setSpeechVolume(&spk, setting, !prefs.autospeak);
}

static int
testSpeechRate (void) {
  return canSetSpeechRate(&spk);
}

static int
changedSpeechRate (const MenuItem *item UNUSED, unsigned char setting) {
  return setSpeechRate(&spk, setting, !prefs.autospeak);
}

static int
testSpeechPitch (void) {
  return canSetSpeechPitch(&spk);
}

static int
changedSpeechPitch (const MenuItem *item UNUSED, unsigned char setting) {
  return setSpeechPitch(&spk, setting, !prefs.autospeak);
}

static int
testSpeechPunctuation (void) {
  return canSetSpeechPunctuation(&spk);
}

static int
changedSpeechPunctuation (const MenuItem *item UNUSED, unsigned char setting) {
  return setSpeechPunctuation(&spk, setting, !prefs.autospeak);
}

static int
testAutospeak (void) {
  return prefs.autospeak;
}

static int
testShowSpeechCursor (void) {
  return prefs.showSpeechCursor;
}

static int
testBlinkingSpeechCursor (void) {
  return testShowSpeechCursor() && prefs.blinkingSpeechCursor;
}
#endif /* ENABLE_SPEECH_SUPPORT */

static int
testShowDate (void) {
  return prefs.datePosition != dpNone;
}

static int
testStatusPosition (void) {
  return !haveStatusCells();
}

static int
changedStatusPosition (const MenuItem *item UNUSED, unsigned char setting UNUSED) {
  reconfigureBrailleWindow();
  return 1;
}

static int
testStatusCount (void) {
  return testStatusPosition() && (prefs.statusPosition != spNone);
}

static int
changedStatusCount (const MenuItem *item UNUSED, unsigned char setting UNUSED) {
  reconfigureBrailleWindow();
  return 1;
}

static int
testStatusSeparator (void) {
  return testStatusCount();
}

static int
changedStatusSeparator (const MenuItem *item UNUSED, unsigned char setting UNUSED) {
  reconfigureBrailleWindow();
  return 1;
}

static int
testStatusField (unsigned char index) {
  return (haveStatusCells() || (prefs.statusPosition != spNone)) &&
         ((index == 0) || (prefs.statusFields[index-1] != sfEnd));
}

static int
changedStatusField (unsigned char index, unsigned char setting) {
  switch (setting) {
    case sfGeneric:
      if (index > 0) return 0;
      if (!haveStatusCells()) return 0;
      if (!braille->statusFields) return 0;
      if (*braille->statusFields != sfGeneric) return 0;
      /* fall through */

    case sfEnd:
      if (prefs.statusFields[index+1] != sfEnd) return 0;
      break;

    default:
      if ((index > 0) && (prefs.statusFields[index-1] == sfGeneric)) return 0;
      break;
  }

  reconfigureBrailleWindow();
  return 1;
}

#define STATUS_FIELD_HANDLERS(n) \
  static int testStatusField##n (void) { return testStatusField(n-1); } \
  static int changedStatusField##n (const MenuItem *item UNUSED, unsigned char setting) { return changedStatusField(n-1, setting); }
STATUS_FIELD_HANDLERS(1)
STATUS_FIELD_HANDLERS(2)
STATUS_FIELD_HANDLERS(3)
STATUS_FIELD_HANDLERS(4)
STATUS_FIELD_HANDLERS(5)
STATUS_FIELD_HANDLERS(6)
STATUS_FIELD_HANDLERS(7)
STATUS_FIELD_HANDLERS(8)
STATUS_FIELD_HANDLERS(9)
#undef STATUS_FIELD_HANDLERS

static int
changedTextTable (const MenuItem *item, unsigned char setting UNUSED) {
  return changeTextTable(getMenuItemValue(item));
}

static int
changedAttributesTable (const MenuItem *item, unsigned char setting UNUSED) {
  return changeAttributesTable(getMenuItemValue(item));
}

static int
changedKeyboardTable (const MenuItem *item, unsigned char setting UNUSED) {
  return changeKeyboardTable(getMenuItemValue(item));
}

#ifdef ENABLE_CONTRACTED_BRAILLE
static int
testContractedBraille (void) {
  return prefs.textStyle == tsContractedBraille;
}

static int
changedContractionTable (const MenuItem *item, unsigned char setting UNUSED) {
  return changeContractionTable(getMenuItemValue(item));
}
#endif /* ENABLE_CONTRACTED_BRAILLE */

static int
testInputTable (void) {
  return !!brl.keyTable;
}

static int
testKeyboardTable (void) {
  return !!keyboardTable;
}

static MenuItem *
newProfileMenuItem (Menu *menu, const ProfileDescriptor *profile) {
  MenuString name = {.label = profile->category};

  return newFilesMenuItem(menu, &name, opt_tablesDirectory, PROFILES_SUBDIRECTORY, profile->extension, "", 1);
}

static int
changedProfile (const ProfileDescriptor *profile, const MenuItem *item) {
  const char *value = getMenuItemValue(item);

  if (*value) {
    activateProfile(profile, opt_tablesDirectory, value);
  } else {
    deactivateProfile(profile);
  }

  return 1;
}

static int
changedLanguageProfile (const MenuItem *item, unsigned char setting UNUSED) {
  return changedProfile(&languageProfile, item);
}

static MenuItem *
newStatusFieldMenuItem (
  Menu *menu, unsigned char number,
  MenuItemTester *test, MenuItemChanged *changed
) {
  static const MenuString strings[] = {
    {.label=strtext("End")},
    {.label=strtext("Braille Window Coordinates"), .comment=strtext("2 cells")},
    {.label=strtext("Braille Window Column"), .comment=strtext("1 cell")},
    {.label=strtext("Braille Window Row"), .comment=strtext("1 cell")},
    {.label=strtext("Screen Cursor Coordinates"), .comment=strtext("2 cells")},
    {.label=strtext("Screen Cursor Column"), .comment=strtext("1 cell")},
    {.label=strtext("Screen Cursor Row"), .comment=strtext("1 cell")},
    {.label=strtext("Screen Cursor and Braille Window Column"), .comment=strtext("2 cells")},
    {.label=strtext("Screen Cursor and Braille Window Row"), .comment=strtext("2 cells")},
    {.label=strtext("Screen Number"), .comment=strtext("1 cell")},
    {.label=strtext("State Dots"), .comment=strtext("1 cell")},
    {.label=strtext("State Letter"), .comment=strtext("1 cell")},
    {.label=strtext("Time"), .comment=strtext("2 cells")},
    {.label=strtext("Alphabetic Braille Window Coordinates"), .comment=strtext("1 cell")},
    {.label=strtext("Alphabetic Screen Cursor Coordinates"), .comment=strtext("1 cell")},
    {.label=strtext("Generic")}
  };

  MenuString name = {
    .label = strtext("Status Field")
  };

  char *comment;
  {
    char buffer[0X3];
    snprintf(buffer, sizeof(buffer), "%u", number);
    name.comment = comment = strdup(buffer);
  }

  if (comment) {
    MenuItem *item = newEnumeratedMenuItem(menu, &prefs.statusFields[number-1], &name, strings);

    if (item) {
      setMenuItemTester(item, test);
      setMenuItemChanged(item, changed);
      return item;
    }

    free(comment);
  } else {
    logMallocError();
  }

  return NULL;
}

static MenuItem *
newTimeMenuItem (Menu *menu, unsigned char *setting, const MenuString *name) {
  return newNumericMenuItem(menu, setting, name, 1, 100, 4, strtext("csecs"));
}

#if defined(HAVE_PCM_SUPPORT) || defined(HAVE_MIDI_SUPPORT) || defined(HAVE_FM_SUPPORT)
static MenuItem *
newVolumeMenuItem (Menu *menu, unsigned char *setting, const MenuString *name) {
  return newNumericMenuItem(menu, setting, name, 0, 100, 5, strtext("percentage"));
}
#endif /* defined(HAVE_PCM_SUPPORT) || defined(HAVE_MIDI_SUPPORT) || defined(HAVE_FM_SUPPORT) */

#ifdef HAVE_MIDI_SUPPORT
static MenuString *
makeMidiInstrumentMenuStrings (void) {
  MenuString *strings = malloc(midiInstrumentCount * sizeof(*strings));

  if (strings) {
    unsigned int instrument;

    for (instrument=0; instrument<midiInstrumentCount; instrument+=1) {
      MenuString *string = &strings[instrument];
      string->label = midiInstrumentTable[instrument];
      string->comment = midiGetInstrumentType(instrument);
    }
  }

  return strings;
}
#endif /* HAVE_MIDI_SUPPORT */

static Menu *logMessagesMenu = NULL;
static const LogEntry *newestLogMessage = NULL;

static int
addNewLogMessages (const LogEntry *message) {
  if (message == newestLogMessage) return 1;
  if (!addNewLogMessages(getPreviousLogEntry(message))) return 0;

  MenuString name;
  const TimeValue *time = getLogEntryTime(message);
  unsigned int count = getLogEntryCount(message);

  if (time) {
    char buffer[0X20];
    formatSeconds(buffer, sizeof(buffer), "%Y-%m-%d@%H:%M:%S", time->seconds);
    name.label = strdup(buffer);
  } else {
    name.label = NULL;
  }

  if (count > 1) {
    char buffer[0X10];
    snprintf(buffer, sizeof(buffer), "(%u)", count);
    name.comment = strdup(buffer);
  } else {
    name.comment = NULL;
  }

  MenuItem *item = newTextMenuItem(logMessagesMenu, &name, getLogEntryText(message));
  if (!item) return 0;

  newestLogMessage = message;
  return 1;
}

int
updateLogMessagesSubmenu (void) {
  return addNewLogMessages(getNewestLogMessage(1));
}

static Menu *
makePreferencesMenu (void) {
  static const MenuString cursorStyles[] = {
    {.label=strtext("Underline"), .comment=strtext("dots 7 and 8")},
    {.label=strtext("Block"), .comment=strtext("all dots")},
    {.label=strtext("Lower Left Dot"), .comment=strtext("dot 7")},
    {.label=strtext("Lower Right Dot"), .comment=strtext("dot 8")}
  };

  Menu *rootMenu = newMenu();
  if (!rootMenu) goto noMenu;

  {
    NAME(strtext("Save on Exit"));
    ITEM(newBooleanMenuItem(rootMenu, &prefs.saveOnExit, &itemName));
  }

  {
    SUBMENU(optionsSubmenu, rootMenu, strtext("Menu Options"));

    {
      NAME(strtext("Show Submenu Sizes"));
      ITEM(newBooleanMenuItem(optionsSubmenu, &prefs.showSubmenuSizes, &itemName));
    }

    {
      NAME(strtext("Show Advanced Submenus"));
      ITEM(newBooleanMenuItem(optionsSubmenu, &prefs.showAdvancedSubmenus, &itemName));
    }

    {
      NAME(strtext("Show All Items"));
      ITEM(newBooleanMenuItem(optionsSubmenu, &prefs.showAllItems, &itemName));
    }
  }

  {
    SUBMENU(presentationSubmenu, rootMenu, strtext("Braille Presentation"));

    {
      static const MenuString strings[] = {
        {.label=strtext("8-Dot Computer Braille")},
        {.label=strtext("Contracted Braille")},
        {.label=strtext("6-Dot Computer Braille")}
      };

      NAME(strtext("Text Style"));
      ITEM(newEnumeratedMenuItem(presentationSubmenu, &prefs.textStyle, &itemName, strings));
    }

#ifdef ENABLE_CONTRACTED_BRAILLE
    {
      NAME(strtext("Expand Current Word"));
      ITEM(newBooleanMenuItem(presentationSubmenu, &prefs.expandCurrentWord, &itemName));
      TEST(ContractedBraille);
    }

    {
      static const MenuString strings[] = {
        {.label=strtext("No Capitalization")},
        {.label=strtext("Use Capital Sign")},
        {.label=strtext("Superimpose Dot 7")}
      };

      NAME(strtext("Capitalization Mode"));
      ITEM(newEnumeratedMenuItem(presentationSubmenu, &prefs.capitalizationMode, &itemName, strings));
      TEST(ContractedBraille);
    }
#endif /* ENABLE_CONTRACTED_BRAILLE */

    {
      static const MenuString strings[] = {
        {.label=strtext("Minimum")},
        {.label=strtext("Low")},
        {.label=strtext("Medium")},
        {.label=strtext("High")},
        {.label=strtext("Maximum")}
      };

      NAME(strtext("Braille Firmness"));
      ITEM(newEnumeratedMenuItem(presentationSubmenu, &prefs.brailleFirmness, &itemName, strings));
      TEST(BrailleFirmness);
      CHANGED(BrailleFirmness);
    }
  }

  {
    SUBMENU(indicatorsSubmenu, rootMenu, strtext("Text Indicators"));

    {
      NAME(strtext("Show Screen Cursor"));
      ITEM(newBooleanMenuItem(indicatorsSubmenu, &prefs.showScreenCursor, &itemName));
    }

    {
      NAME(strtext("Screen Cursor Style"));
      ITEM(newEnumeratedMenuItem(indicatorsSubmenu, &prefs.screenCursorStyle, &itemName, cursorStyles));
      TEST(ShowScreenCursor);
    }

    {
      NAME(strtext("Blinking Screen Cursor"));
      ITEM(newBooleanMenuItem(indicatorsSubmenu, &prefs.blinkingScreenCursor, &itemName));
      TEST(ShowScreenCursor);
    }

    {
      NAME(strtext("Screen Cursor Visible Time"));
      ITEM(newTimeMenuItem(indicatorsSubmenu, &prefs.screenCursorVisibleTime, &itemName));
      TEST(BlinkingScreenCursor);
    }

    {
      NAME(strtext("Screen Cursor Invisible Time"));
      ITEM(newTimeMenuItem(indicatorsSubmenu, &prefs.screenCursorInvisibleTime, &itemName));
      TEST(BlinkingScreenCursor);
    }

    {
      NAME(strtext("Show Attributes"));
      ITEM(newBooleanMenuItem(indicatorsSubmenu, &prefs.showAttributes, &itemName));
    }

    {
      NAME(strtext("Blinking Attributes"));
      ITEM(newBooleanMenuItem(indicatorsSubmenu, &prefs.blinkingAttributes, &itemName));
      TEST(ShowAttributes);
    }

    {
      NAME(strtext("Attributes Visible Time"));
      ITEM(newTimeMenuItem(indicatorsSubmenu, &prefs.attributesVisibleTime, &itemName));
      TEST(BlinkingAttributes);
    }

    {
      NAME(strtext("Attributes Invisible Time"));
      ITEM(newTimeMenuItem(indicatorsSubmenu, &prefs.attributesInvisibleTime, &itemName));
      TEST(BlinkingAttributes);
    }

    {
      NAME(strtext("Blinking Capitals"));
      ITEM(newBooleanMenuItem(indicatorsSubmenu, &prefs.blinkingCapitals, &itemName));
    }

    {
      NAME(strtext("Capitals Visible Time"));
      ITEM(newTimeMenuItem(indicatorsSubmenu, &prefs.capitalsVisibleTime, &itemName));
      TEST(BlinkingCapitals);
    }

    {
      NAME(strtext("Capitals Invisible Time"));
      ITEM(newTimeMenuItem(indicatorsSubmenu, &prefs.capitalsInvisibleTime, &itemName));
      TEST(BlinkingCapitals);
    }
  }

  {
    SUBMENU(navigationSubmenu, rootMenu, strtext("Navigation Options"));

    {
      NAME(strtext("Word Wrap"));
      ITEM(newBooleanMenuItem(navigationSubmenu, &prefs.wordWrap, &itemName));
    }
    {
      NAME(strtext("Skip Identical Lines"));
      ITEM(newBooleanMenuItem(navigationSubmenu, &prefs.skipIdenticalLines, &itemName));
    }

    {
      NAME(strtext("Skip Blank Braille Windows"));
      ITEM(newBooleanMenuItem(navigationSubmenu, &prefs.skipBlankBrailleWindows, &itemName));
    }

    {
      static const MenuString strings[] = {
        {.label=strtext("All")},
        {.label=strtext("End of Line")},
        {.label=strtext("Rest of Line")}
      };

      NAME(strtext("Skip Which Blank Braille Windows"));
      ITEM(newEnumeratedMenuItem(navigationSubmenu, &prefs.skipBlankBrailleWindowsMode, &itemName, strings));
    }

    {
      NAME(strtext("Sliding Braille Window"));
      ITEM(newBooleanMenuItem(navigationSubmenu, &prefs.slidingBrailleWindow, &itemName));
    }

    {
      NAME(strtext("Eager Sliding Braille Window"));
      ITEM(newBooleanMenuItem(navigationSubmenu, &prefs.eagerSlidingBrailleWindow, &itemName));
      TEST(SlidingBrailleWindow);
    }

    {
      NAME(strtext("Braille Window Overlap"));
      ITEM(newNumericMenuItem(navigationSubmenu, &prefs.brailleWindowOverlap, &itemName, 0, 20, 1, strtext("cells")));
      CHANGED(BrailleWindowOverlap);
    }

    {
      static const MenuString strings[] = {
        {.label=strtext("None")},
        {.label=strtext("250 milliseconds")},
        {.label=strtext("500 milliseconds")},
        {.label=strtext("1 second")},
        {.label=strtext("2 seconds")}
      };

      NAME(strtext("Cursor Tracking Delay"));
      ITEM(newEnumeratedMenuItem(navigationSubmenu, &prefs.cursorTrackingDelay, &itemName, strings));
    }

    {
      NAME(strtext("Track Screen Scroll"));
      ITEM(newBooleanMenuItem(navigationSubmenu, &prefs.trackScreenScroll, &itemName));
    }

#ifdef HAVE_LIBGPM
    {
      NAME(strtext("Track Screen Pointer"));
      ITEM(newBooleanMenuItem(navigationSubmenu, &prefs.trackScreenPointer, &itemName));
    }
#endif /* HAVE_LIBGPM */

    {
      NAME(strtext("Highlight Braille Window Location"));
      ITEM(newBooleanMenuItem(navigationSubmenu, &prefs.highlightBrailleWindowLocation, &itemName));
    }
  }

  {
    SUBMENU(typingSubmenu, rootMenu, strtext("Braille Typing"));

    {
      NAME(strtext("Keyboard Enabled"));
      ITEM(newBooleanMenuItem(typingSubmenu, &prefs.brailleKeyboardEnabled, &itemName));
    }

    {
      static const MenuString strings[] = {
        {.label=strtext("Translated via Text Table")},
        {.label=strtext("Dots via Unicode Braille")}
      };

      NAME(strtext("Input Mode"));
      ITEM(newEnumeratedMenuItem(typingSubmenu, &prefs.brailleInputMode, &itemName, strings));
    }

    {
      NAME(strtext("Quick Space"));
      ITEM(newBooleanMenuItem(typingSubmenu, &prefs.brailleQuickSpace, &itemName));
    }
  }

  {
    SUBMENU(inputSubmenu, rootMenu, strtext("Input Options"));

    {
      static const MenuString strings[] = {
        {.label=strtext("Off")},
        {.label=strtext("5 seconds")},
        {.label=strtext("10 seconds")},
        {.label=strtext("20 seconds")},
        {.label=strtext("40 seconds")}
      };

      NAME(strtext("Autorelease Time"));
      ITEM(newEnumeratedMenuItem(inputSubmenu, &prefs.autoreleaseTime, &itemName, strings));
      CHANGED(AutoreleaseTime);
    }

    {
      NAME(strtext("First Release"));
      ITEM(newBooleanMenuItem(inputSubmenu, &prefs.firstRelease, &itemName));
    }

    {
      NAME(strtext("Long Press Time"));
      ITEM(newTimeMenuItem(inputSubmenu, &prefs.longPressTime, &itemName));
      CHANGED(AutorepeatDelay);
    }

    {
      NAME(strtext("Autorepeat"));
      ITEM(newBooleanMenuItem(inputSubmenu, &prefs.autorepeatEnabled, &itemName));
      CHANGED(AutorepeatEnabled);
    }

    {
      NAME(strtext("Autorepeat Interval"));
      ITEM(newTimeMenuItem(inputSubmenu, &prefs.autorepeatInterval, &itemName));
      TEST(AutorepeatEnabled);
      CHANGED(AutorepeatInterval);
    }

    {
      NAME(strtext("Autorepeat Panning"));
      ITEM(newBooleanMenuItem(inputSubmenu, &prefs.autorepeatPanning, &itemName));
      TEST(AutorepeatEnabled);
    }

    {
      NAME(strtext("Touch Navigation"));
      ITEM(newBooleanMenuItem(inputSubmenu, &prefs.touchNavigation, &itemName));
      TEST(TouchSensitivity);
    }

    {
      static const MenuString strings[] = {
        {.label=strtext("Minimum")},
        {.label=strtext("Low")},
        {.label=strtext("Medium")},
        {.label=strtext("High")},
        {.label=strtext("Maximum")}
      };

      NAME(strtext("Touch Sensitivity"));
      ITEM(newEnumeratedMenuItem(inputSubmenu, &prefs.touchSensitivity, &itemName, strings));
      TEST(TouchSensitivity);
      CHANGED(TouchSensitivity);
    }

    {
      static const MenuString strings[] = {
        {.label=strtext("Normal")},
        {.label=strtext("Rotated")}
      };

      NAME(strtext("Braille Display Orientation"));
      ITEM(newEnumeratedMenuItem(inputSubmenu, &prefs.brailleDisplayOrientation, &itemName, strings));
      TEST(BrailleDisplayOrientation);
    }

    {
      NAME(strtext("Keyboard Table"));
      ITEM(newFilesMenuItem(inputSubmenu, &itemName, opt_tablesDirectory, KEYBOARD_TABLES_SUBDIRECTORY, KEY_TABLE_EXTENSION, opt_keyboardTable, 1));
      CHANGED(KeyboardTable);
      SET(keyboardTable);
    }
  }

  {
    SUBMENU(alertsSubmenu, rootMenu, strtext("Event Alerts"));

    {
      NAME(strtext("Console Bell Alert"));
      ITEM(newBooleanMenuItem(alertsSubmenu, &prefs.consoleBellAlert, &itemName));
      TEST(ConsoleBellAlert);
      CHANGED(ConsoleBellAlert);
    }

    {
      NAME(strtext("Keyboard LED Alerts"));
      ITEM(newBooleanMenuItem(alertsSubmenu, &prefs.keyboardLedAlerts, &itemName));
      TEST(KeyboardLedAlerts);
      CHANGED(KeyboardLedAlerts);
    }

    {
      NAME(strtext("Alert Tunes"));
      ITEM(newBooleanMenuItem(alertsSubmenu, &prefs.alertTunes, &itemName));
    }

    {
      static const MenuString strings[] = {
        {.label=strtext("Beeper"), .comment=strtext("console tone generator")},
        {.label=strtext("PCM"), .comment=strtext("soundcard digital audio")},
        {.label=strtext("MIDI"), .comment=strtext("Musical Instrument Digital Interface")},
        {.label=strtext("FM"), .comment=strtext("soundcard synthesizer")}
      };

      NAME(strtext("Tune Device"));
      ITEM(newEnumeratedMenuItem(alertsSubmenu, &prefs.tuneDevice, &itemName, strings));
      TEST(Tunes);
      CHANGED(TuneDevice);
    }

#ifdef HAVE_PCM_SUPPORT
    {
      NAME(strtext("PCM Volume"));
      ITEM(newVolumeMenuItem(alertsSubmenu, &prefs.pcmVolume, &itemName));
      TEST(TunesPcm);
    }
#endif /* HAVE_PCM_SUPPORT */

#ifdef HAVE_MIDI_SUPPORT
    {
      NAME(strtext("MIDI Volume"));
      ITEM(newVolumeMenuItem(alertsSubmenu, &prefs.midiVolume, &itemName));
      TEST(TunesMidi);
    }

    {
      const MenuString *strings = makeMidiInstrumentMenuStrings();
      if (!strings) goto noItem;

      {
        NAME(strtext("MIDI Instrument"));
        ITEM(newStringsMenuItem(alertsSubmenu, &prefs.midiInstrument, &itemName, strings, midiInstrumentCount));
        TEST(TunesMidi);
      }
    }
#endif /* HAVE_MIDI_SUPPORT */

#ifdef HAVE_FM_SUPPORT
    {
      NAME(strtext("FM Volume"));
      ITEM(newVolumeMenuItem(alertsSubmenu, &prefs.fmVolume, &itemName));
      TEST(TunesFm);
    }
#endif /* HAVE_FM_SUPPORT */

    {
      NAME(strtext("Alert Dots"));
      ITEM(newBooleanMenuItem(alertsSubmenu, &prefs.alertDots, &itemName));
    }

    {
      NAME(strtext("Alert Messages"));
      ITEM(newBooleanMenuItem(alertsSubmenu, &prefs.alertMessages, &itemName));
    }
  }

#ifdef ENABLE_SPEECH_SUPPORT
  {
    SUBMENU(speechSubmenu, rootMenu, strtext("Speech Options"));

    {
      NAME(strtext("Speech Volume"));
      ITEM(newNumericMenuItem(speechSubmenu, &prefs.speechVolume, &itemName, 0, SPK_VOLUME_MAXIMUM, 1, NULL));
      TEST(SpeechVolume);
      CHANGED(SpeechVolume);
    }

    {
      NAME(strtext("Speech Rate"));
      ITEM(newNumericMenuItem(speechSubmenu, &prefs.speechRate, &itemName, 0, SPK_RATE_MAXIMUM, 1, NULL));
      TEST(SpeechRate);
      CHANGED(SpeechRate);
    }

    {
      NAME(strtext("Speech Pitch"));
      ITEM(newNumericMenuItem(speechSubmenu, &prefs.speechPitch, &itemName, 0, SPK_PITCH_MAXIMUM, 1, NULL));
      TEST(SpeechPitch);
      CHANGED(SpeechPitch);
    }

    {
      static const MenuString strings[] = {
        {.label=strtext("None")},
        {.label=strtext("Some")},
        {.label=strtext("All")}
      };

      NAME(strtext("Speech Punctuation"));
      ITEM(newEnumeratedMenuItem(speechSubmenu, &prefs.speechPunctuation, &itemName, strings));
      TEST(SpeechPunctuation);
      CHANGED(SpeechPunctuation);
    }

    {
      static const MenuString strings[] = {
        {.label=strtext("None")},
        // "cap" here, used during speech output, is short for "capital".
        // It is spoken just before an uppercase letter, e.g. "cap A".
        {.label=strtext("Say Cap")},
        {.label=strtext("Raise Pitch")}
      };

      NAME(strtext("Speech Uppercase Indicator"));
      ITEM(newEnumeratedMenuItem(speechSubmenu, &prefs.speechUppercaseIndicator, &itemName, strings));
    }

    {
      static const MenuString strings[] = {
        {.label=strtext("None")},
        {.label=strtext("Say Space")},
      };

      NAME(strtext("Speech Whitespace Indicator"));
      ITEM(newEnumeratedMenuItem(speechSubmenu, &prefs.speechWhitespaceIndicator, &itemName, strings));
    }

    {
      static const MenuString strings[] = {
        {.label=strtext("Immediate")},
        {.label=strtext("Enqueue")}
      };

      NAME(strtext("Say Line Mode"));
      ITEM(newEnumeratedMenuItem(speechSubmenu, &prefs.sayLineMode, &itemName, strings));
    }

    {
      NAME(strtext("Autospeak"));
      ITEM(newBooleanMenuItem(speechSubmenu, &prefs.autospeak, &itemName));
    }

    {
      NAME(strtext("Speak Selected Line"));
      ITEM(newBooleanMenuItem(speechSubmenu, &prefs.autospeakSelectedLine, &itemName));
      TEST(Autospeak);
    }

    {
      NAME(strtext("Speak Selected Character"));
      ITEM(newBooleanMenuItem(speechSubmenu, &prefs.autospeakSelectedCharacter, &itemName));
      TEST(Autospeak);
    }

    {
      NAME(strtext("Speak Inserted Characters"));
      ITEM(newBooleanMenuItem(speechSubmenu, &prefs.autospeakInsertedCharacters, &itemName));
      TEST(Autospeak);
    }

    {
      NAME(strtext("Speak Deleted Characters"));
      ITEM(newBooleanMenuItem(speechSubmenu, &prefs.autospeakDeletedCharacters, &itemName));
      TEST(Autospeak);
    }

    {
      NAME(strtext("Speak Replaced Characters"));
      ITEM(newBooleanMenuItem(speechSubmenu, &prefs.autospeakReplacedCharacters, &itemName));
      TEST(Autospeak);
    }

    {
      NAME(strtext("Speak Completed Words"));
      ITEM(newBooleanMenuItem(speechSubmenu, &prefs.autospeakCompletedWords, &itemName));
      TEST(Autospeak);
    }

    {
      NAME(strtext("Speak Line Indent"));
      ITEM(newBooleanMenuItem(speechSubmenu, &prefs.autospeakLineIndent, &itemName));
      TEST(Autospeak);
    }

    {
      NAME(strtext("Show Speech Cursor"));
      ITEM(newBooleanMenuItem(speechSubmenu, &prefs.showSpeechCursor, &itemName));
    }

    {
      NAME(strtext("Speech Cursor Style"));
      ITEM(newEnumeratedMenuItem(speechSubmenu, &prefs.speechCursorStyle, &itemName, cursorStyles));
      TEST(ShowSpeechCursor);
    }

    {
      NAME(strtext("Blinking Speech Cursor"));
      ITEM(newBooleanMenuItem(speechSubmenu, &prefs.blinkingSpeechCursor, &itemName));
      TEST(ShowSpeechCursor);
    }

    {
      NAME(strtext("Speech Cursor Visible Time"));
      ITEM(newTimeMenuItem(speechSubmenu, &prefs.speechCursorVisibleTime, &itemName));
      TEST(BlinkingSpeechCursor);
    }

    {
      NAME(strtext("Speech Cursor Invisible Time"));
      ITEM(newTimeMenuItem(speechSubmenu, &prefs.speechCursorInvisibleTime, &itemName));
      TEST(BlinkingSpeechCursor);
    }
  }
#endif /* ENABLE_SPEECH_SUPPORT */

  {
    SUBMENU(timeSubmenu, rootMenu, strtext("Time Presentation"));

    {
      static const MenuString strings[] = {
        {.label=strtext("24 Hour")},
        {.label=strtext("12 Hour")}
      };

      NAME(strtext("Time Format"));
      ITEM(newEnumeratedMenuItem(timeSubmenu, &prefs.timeFormat, &itemName, strings));
    }

    {
      static const MenuString strings[] = {
        {.label=strtext("Colon"), ":"},
        {.label=strtext("Dot"), "."},
      };

      NAME(strtext("Time Separator"));
      ITEM(newEnumeratedMenuItem(timeSubmenu, &prefs.timeSeparator, &itemName, strings));
    }

    {
      NAME(strtext("Show Seconds"));
      ITEM(newBooleanMenuItem(timeSubmenu, &prefs.showSeconds, &itemName));
    }

    {
      static const MenuString strings[] = {
        {.label=strtext("None")},
        {.label=strtext("Before Time")},
        {.label=strtext("After Time")}
      };

      NAME(strtext("Date Position"));
      ITEM(newEnumeratedMenuItem(timeSubmenu, &prefs.datePosition, &itemName, strings));
    }

    {
      static const MenuString strings[] = {
        {.label=strtext("Year Month Day")},
        {.label=strtext("Month Day Year")},
        {.label=strtext("Day Month Year")},
      };

      NAME(strtext("Date Format"));
      ITEM(newEnumeratedMenuItem(timeSubmenu, &prefs.dateFormat, &itemName, strings));
      TEST(ShowDate);
    }

    {
      static const MenuString strings[] = {
        {.label=strtext("Dash"), "-"},
        {.label=strtext("Slash"), "/"},
        {.label=strtext("Dot"), "."}
      };

      NAME(strtext("Date Separator"));
      ITEM(newEnumeratedMenuItem(timeSubmenu, &prefs.dateSeparator, &itemName, strings));
      TEST(ShowDate);
    }
  }

  {
    SUBMENU(statusSubmenu, rootMenu, strtext("Status Cells"));

    {
      static const MenuString strings[] = {
        {.label=strtext("None")},
        {.label=strtext("Left")},
        {.label=strtext("Right")}
      };

      NAME(strtext("Status Position"));
      ITEM(newEnumeratedMenuItem(statusSubmenu, &prefs.statusPosition, &itemName, strings));
      TEST(StatusPosition);
      CHANGED(StatusPosition);
    }

    {
      NAME(strtext("Status Count"));
      ITEM(newNumericMenuItem(statusSubmenu, &prefs.statusCount, &itemName, 0, MAX((int)brl.textColumns/2-1, 0), 1, strtext("cells")));
      TEST(StatusCount);
      CHANGED(StatusCount);
    }

    {
      static const MenuString strings[] = {
        {.label=strtext("None")},
        {.label=strtext("Space")},
        {.label=strtext("Block")},
        {.label=strtext("Status Side")},
        {.label=strtext("Text Side")}
      };

      NAME(strtext("Status Separator"));
      ITEM(newEnumeratedMenuItem(statusSubmenu, &prefs.statusSeparator, &itemName, strings));
      TEST(StatusSeparator);
      CHANGED(StatusSeparator);
    }

    {
#define STATUS_FIELD_ITEM(number) { ITEM(newStatusFieldMenuItem(statusSubmenu, number, testStatusField##number, changedStatusField##number)); }
      STATUS_FIELD_ITEM(1);
      STATUS_FIELD_ITEM(2);
      STATUS_FIELD_ITEM(3);
      STATUS_FIELD_ITEM(4);
      STATUS_FIELD_ITEM(5);
      STATUS_FIELD_ITEM(6);
      STATUS_FIELD_ITEM(7);
      STATUS_FIELD_ITEM(8);
      STATUS_FIELD_ITEM(9);
#undef STATUS_FIELD_ITEM
    }
  }

  {
    SUBMENU(tablesSubmenu, rootMenu, strtext("Braille Tables"));

    {
      NAME(strtext("Text Table"));
      ITEM(newFilesMenuItem(tablesSubmenu, &itemName, opt_tablesDirectory, TEXT_TABLES_SUBDIRECTORY, TEXT_TABLE_EXTENSION, opt_textTable, 0));
      CHANGED(TextTable);
      SET(textTable);
    }

    {
      NAME(strtext("Attributes Table"));
      ITEM(newFilesMenuItem(tablesSubmenu, &itemName, opt_tablesDirectory, ATTRIBUTES_TABLES_SUBDIRECTORY, ATTRIBUTES_TABLE_EXTENSION, opt_attributesTable, 0));
      CHANGED(AttributesTable);
      SET(attributesTable);
    }

#ifdef ENABLE_CONTRACTED_BRAILLE
    {
      NAME(strtext("Contraction Table"));
      ITEM(newFilesMenuItem(tablesSubmenu, &itemName, opt_tablesDirectory, CONTRACTION_TABLES_SUBDIRECTORY, CONTRACTION_TABLE_EXTENSION, opt_contractionTable, 1));
      CHANGED(ContractionTable);
      SET(contractionTable);
    }
#endif /* ENABLE_CONTRACTED_BRAILLE */
  }

  {
    SUBMENU(profilesSubmenu, rootMenu, strtext("Profiles"));

    {
      ITEM(newProfileMenuItem(profilesSubmenu, &languageProfile));
      CHANGED(LanguageProfile);
      SET(languageProfile);
    }
  }

  {
    SUBMENU(buildSubmenu, rootMenu, strtext("Build Information"));
    setAdvancedSubmenu(buildSubmenu);

    {
      NAME(strtext("Package Version"));
      ITEM(newTextMenuItem(buildSubmenu, &itemName, PACKAGE_VERSION));
    }

    {
      NAME(strtext("Package Revision"));
      ITEM(newTextMenuItem(buildSubmenu, &itemName, getRevisionIdentifier()));
    }

    {
      NAME(strtext("Web Site"));
      ITEM(newTextMenuItem(buildSubmenu, &itemName, PACKAGE_URL));
    }

    {
      NAME(strtext("Bug Reports"));
      ITEM(newTextMenuItem(buildSubmenu, &itemName, PACKAGE_BUGREPORT));
    }

    {
      NAME(strtext("Configuration Directory"));
      ITEM(newTextMenuItem(buildSubmenu, &itemName, CONFIGURATION_DIRECTORY));
    }

    {
      NAME(strtext("Configuration File"));
      ITEM(newTextMenuItem(buildSubmenu, &itemName, CONFIGURATION_FILE));
    }

    {
      NAME(strtext("Updatable Directory"));
      ITEM(newTextMenuItem(buildSubmenu, &itemName, UPDATABLE_DIRECTORY));
    }

    {
      NAME(strtext("Preferences File"));
      ITEM(newTextMenuItem(buildSubmenu, &itemName, PREFERENCES_FILE));
    }

    {
      NAME(strtext("Writable Directory"));
      ITEM(newTextMenuItem(buildSubmenu, &itemName, WRITABLE_DIRECTORY));
    }

    {
      NAME(strtext("Drivers Directory"));
      ITEM(newTextMenuItem(buildSubmenu, &itemName, DRIVERS_DIRECTORY));
    }

    {
      NAME(strtext("Tables Directory"));
      ITEM(newTextMenuItem(buildSubmenu, &itemName, TABLES_DIRECTORY));
    }

    {
      NAME(strtext("Locale Directory"));
      ITEM(newTextMenuItem(buildSubmenu, &itemName, LOCALE_DIRECTORY));
    }
  }

  {
    static const MenuString logLevels[] = {
      {.label=strtext("Emergency")},
      {.label=strtext("Alert")},
      {.label=strtext("Critical")},
      {.label=strtext("Error")},
      {.label=strtext("Warning")},
      {.label=strtext("Notice")},
      {.label=strtext("Information")},
      {.label=strtext("Debug")}
    };

    SUBMENU(internalSubmenu, rootMenu, strtext("Internal Parameters"));
    setAdvancedSubmenu(internalSubmenu);

    {
      NAME(strtext("System Log Level"));
      ITEM(newEnumeratedMenuItem(internalSubmenu, &systemLogLevel, &itemName, logLevels));
    }

    {
      NAME(strtext("Standard Error Log Level"));
      ITEM(newEnumeratedMenuItem(internalSubmenu, &stderrLogLevel, &itemName, logLevels));
    }

    {
      NAME(strtext("Category Log Level"));
      ITEM(newEnumeratedMenuItem(internalSubmenu, &categoryLogLevel, &itemName, logLevels));
    }

    {
      SUBMENU(logCategoriesSubmenu, internalSubmenu, strtext("Log Categories"));
      setAdvancedSubmenu(logCategoriesSubmenu);

      {
        LogCategoryIndex category;

        for (category=0; category<LOG_CATEGORY_COUNT; category+=1) {
          const char *description = getLogCategoryTitle(category);

          if (description && *description) {
            MenuString *name;

            if (!(name = malloc(sizeof(*name)))) goto noItem;
            memset(name, 0, sizeof(*name));
            name->label = description;

            {
              ITEM(newBooleanMenuItem(logCategoriesSubmenu, &logCategoryFlags[category], name));

              switch (category) {
                case LOG_CATEGORY_INDEX(BRAILLE_KEYS):
                  TEST(InputTable);
                  break;

                case LOG_CATEGORY_INDEX(KEYBOARD_KEYS):
                  TEST(KeyboardTable);
                  break;

                default:
                  break;
              }
            }
          }
        }
      }
    }
  }

  {
    SUBMENU(toolsSubmenu, rootMenu, strtext("Tools"));
    setAdvancedSubmenu(toolsSubmenu);

    {
      NAME(strtext("Restart Braille Driver"));
      ITEM(newToolMenuItem(toolsSubmenu, &itemName, restartBrailleDriver));
    }

#ifdef ENABLE_SPEECH_SUPPORT
    {
      NAME(strtext("Restart Speech Driver"));
      ITEM(newToolMenuItem(toolsSubmenu, &itemName, restartSpeechDriver));
    }
#endif /* ENABLE_SPEECH_SUPPORT */

    {
      NAME(strtext("Restart Screen Driver"));
      ITEM(newToolMenuItem(toolsSubmenu, &itemName, restartScreenDriver));
    }
  }

  {
    NAME(strtext("Log Messages"));
    logMessagesMenu = newSubmenuMenuItem(rootMenu, &itemName);
  }

  return rootMenu;

noItem:
  destroyMenu(rootMenu);
noMenu:
  return NULL;
}

Menu *
getPreferencesMenu (void) {
  static Menu *menu = NULL;
  if (!menu) menu = makePreferencesMenu();
  return menu;
}
