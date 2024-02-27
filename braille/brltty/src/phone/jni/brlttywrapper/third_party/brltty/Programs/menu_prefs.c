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

#include "log.h"
#include "log_history.h"
#include "embed.h"
#include "revision.h"
#include "api_control.h"
#include "menu.h"
#include "menu_prefs.h"
#include "prefs.h"
#include "profile.h"
#include "status_types.h"
#include "blink.h"
#include "timing.h"
#include "brl.h"
#include "spk.h"
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

#define PROPERTY(variable, value) \
  static unsigned char variable; \
  variable = (value)

#define BLINK_PERIOD(blink) PROPERTY(period, MSECS2PREFS(getBlinkPeriod((blink))))
#define BLINK_VISIBLE(blink) PROPERTY(visible, getBlinkPercentVisible((blink)))

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
changeAutorepeatProperties (BrailleDisplay *brl, int on, int delay, int interval) {
  if (!canSetAutorepeatProperties(brl)) return 1;
  return setAutorepeatProperties(brl, on, delay, interval);
}

static int
changedAutorepeatEnabled (const MenuItem *item UNUSED, unsigned char setting) {
  return changeAutorepeatProperties(&brl, setting,
                                    PREFS2MSECS(prefs.longPressTime),
                                    PREFS2MSECS(prefs.autorepeatInterval));
}

static int
changedAutorepeatDelay (const MenuItem *item UNUSED, unsigned char setting) {
  return changeAutorepeatProperties(&brl, prefs.autorepeatEnabled,
                                    setting,
                                    PREFS2MSECS(prefs.autorepeatInterval));
}

static int
changedAutorepeatInterval (const MenuItem *item UNUSED, unsigned char setting) {
  return changeAutorepeatProperties(&brl, prefs.autorepeatEnabled,
                                    PREFS2MSECS(prefs.longPressTime),
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
changedScreenCursorBlinkPeriod (const MenuItem *item UNUSED, unsigned char setting) {
  setBlinkPeriod(&screenCursorBlinkDescriptor, PREFS2MSECS(setting));
  return 1;
}

static int
changedScreenCursorBlinkPercentage (const MenuItem *item UNUSED, unsigned char setting) {
  return setBlinkPercentVisible(&screenCursorBlinkDescriptor, setting);
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
changedAttributesUnderlineBlinkPeriod (const MenuItem *item UNUSED, unsigned char setting) {
  setBlinkPeriod(&attributesUnderlineBlinkDescriptor, PREFS2MSECS(setting));
  return 1;
}

static int
changedAttributesUnderlineBlinkPercentage (const MenuItem *item UNUSED, unsigned char setting) {
  return setBlinkPercentVisible(&attributesUnderlineBlinkDescriptor, setting);
}

static int
testBlinkingCapitals (void) {
  return prefs.blinkingCapitals;
}

static int
changedUppercaseLettersBlinkPeriod (const MenuItem *item UNUSED, unsigned char setting) {
  setBlinkPeriod(&uppercaseLettersBlinkDescriptor, PREFS2MSECS(setting));
  return 1;
}

static int
changedUppercaseLettersBlinkPercentage (const MenuItem *item UNUSED, unsigned char setting) {
  return setBlinkPercentVisible(&uppercaseLettersBlinkDescriptor, setting);
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
testAlertTunes (void) {
  return prefs.alertTunes;
}

static int
changedAlertTunes (const MenuItem *item UNUSED, unsigned char setting) {
  api.updateParameter(BRLAPI_PARAM_AUDIBLE_ALERTS, 0);
  return 1;
}

static int
changedTuneDevice (const MenuItem *item UNUSED, unsigned char setting) {
  return tuneSetDevice(setting);
}

#ifdef HAVE_PCM_SUPPORT
static int
testTunesPcm (void) {
  return testAlertTunes() && (prefs.tuneDevice == tdPcm);
}
#endif /* HAVE_PCM_SUPPORT */

#ifdef HAVE_MIDI_SUPPORT
static int
testTunesMidi (void) {
  return testAlertTunes() && (prefs.tuneDevice == tdMidi);
}
#endif /* HAVE_MIDI_SUPPORT */

#ifdef HAVE_FM_SUPPORT
static int
testTunesFm (void) {
  return testAlertTunes() && (prefs.tuneDevice == tdFm);
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

static void
formatSpeechVolume (Menu *menu, unsigned char volume, char *buffer, size_t size) {
  snprintf(
    buffer, size, "%d", toNormalizedSpeechVolume(volume)
  );
}

static int
testSpeechRate (void) {
  return canSetSpeechRate(&spk);
}

static int
changedSpeechRate (const MenuItem *item UNUSED, unsigned char setting) {
  return setSpeechRate(&spk, setting, !prefs.autospeak);
}

static void
formatSpeechRate (Menu *menu, unsigned char rate, char *buffer, size_t size) {
  snprintf(
    buffer, size, "%d", toNormalizedSpeechRate(rate)
  );
}

static int
testSpeechPitch (void) {
  return canSetSpeechPitch(&spk);
}

static int
changedSpeechPitch (const MenuItem *item UNUSED, unsigned char setting) {
  return setSpeechPitch(&spk, setting, !prefs.autospeak);
}

static void
formatSpeechPitch (Menu *menu, unsigned char pitch, char *buffer, size_t size) {
  snprintf(
    buffer, size, "%d", toNormalizedSpeechPitch(pitch)
  );
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

static int
changedSpeechCursorBlinkPeriod (const MenuItem *item UNUSED, unsigned char setting) {
  setBlinkPeriod(&speechCursorBlinkDescriptor, PREFS2MSECS(setting));
  return 1;
}

static int
changedSpeechCursorBlinkPercentage (const MenuItem *item UNUSED, unsigned char setting) {
  return setBlinkPercentVisible(&speechCursorBlinkDescriptor, setting);
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
changedSkipIdenticalLines (const MenuItem *item, unsigned char setting UNUSED) {
  api.updateParameter(BRLAPI_PARAM_SKIP_IDENTICAL_LINES, 0);
  return 1;
}

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

static int
testComputerBraille (void) {
  return !isContractedBraille();
}

static int
testContractedBraille (void) {
  return isContractedBraille();
}

static int
changedContractedBraille (const MenuItem *item, unsigned char setting UNUSED) {
  setContractedBraille(setting);
  api.updateParameter(BRLAPI_PARAM_LITERARY_BRAILLE, 0);
  return 1;
}

static int
changedContractionTable (const MenuItem *item, unsigned char setting UNUSED) {
  return changeContractionTable(getMenuItemValue(item));
}

static int
changedComputerBraille (const MenuItem *item, unsigned char setting UNUSED) {
  setSixDotComputerBraille(setting);
  api.updateParameter(BRLAPI_PARAM_COMPUTER_BRAILLE_CELL_SIZE, 0);
  return 1;
}

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
    [sfEnd] = {.label=strtext("End")},
    [sfWindowCoordinates2] = {.label=strtext("Window Coordinates"), .comment=strtext("2 cells")},
    [sfWindowColumn] = {.label=strtext("Window Column"), .comment=strtext("1 cell")},
    [sfWindowRow] = {.label=strtext("Window Row"), .comment=strtext("1 cell")},
    [sfCursorCoordinates2] = {.label=strtext("Cursor Coordinates"), .comment=strtext("2 cells")},
    [sfCursorColumn] = {.label=strtext("Cursor Column"), .comment=strtext("1 cell")},
    [sfCursorRow] = {.label=strtext("Cursor Row"), .comment=strtext("1 cell")},
    [sfCursorAndWindowColumn2] = {.label=strtext("Cursor and Window Column"), .comment=strtext("2 cells")},
    [sfCursorAndWindowRow2] = {.label=strtext("Cursor and Window Row"), .comment=strtext("2 cells")},
    [sfScreenNumber] = {.label=strtext("Screen Number"), .comment=strtext("1 cell")},
    [sfStateDots] = {.label=strtext("State Dots"), .comment=strtext("1 cell")},
    [sfStateLetter] = {.label=strtext("State Letter"), .comment=strtext("1 cell")},
    [sfTime] = {.label=strtext("Time"), .comment=strtext("2 cells")},
    [sfAlphabeticWindowCoordinates] = {.label=strtext("Alphabetic Window Coordinates"), .comment=strtext("1 cell")},
    [sfAlphabeticCursorCoordinates] = {.label=strtext("Alphabetic Cursor Coordinates"), .comment=strtext("1 cell")},
    [sfGeneric] = {.label=strtext("Generic")},
    [sfCursorCoordinates3] = {.label=strtext("Cursor Coordinates"), .comment=strtext("3 cells")},
    [sfWindowCoordinates3] = {.label=strtext("Window Coordinates"), .comment=strtext("3 cells")},
    [sfCursorAndWindowColumn3] = {.label=strtext("Cursor and Window Column"), .comment=strtext("3 cells")},
    [sfCursorAndWindowRow3] = {.label=strtext("Cursor and Window Row"), .comment=strtext("3 cells")},
    [sfSpace] = {.label=strtext("Space"), .comment=strtext("1 cell")},
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
newBlinkVisibleMenuItem (Menu *menu, unsigned char *setting, const MenuString *name) {
  return newPercentMenuItem(menu, setting, name, 5);
}

#if defined(HAVE_PCM_SUPPORT) || defined(HAVE_MIDI_SUPPORT) || defined(HAVE_FM_SUPPORT)
static MenuItem *
newVolumeMenuItem (Menu *menu, unsigned char *setting, const MenuString *name) {
  return newPercentMenuItem(menu, setting, name, 5);
}
#endif /* defined(HAVE_PCM_SUPPORT) || defined(HAVE_MIDI_SUPPORT) || defined(HAVE_FM_SUPPORT) */

#ifdef HAVE_MIDI_SUPPORT
static MenuString *
makeMidiInstrumentMenuStrings (void) {
  MenuString *strings = malloc(midiInstrumentCount * sizeof(*strings));

  if (strings) {
    for (unsigned int instrument=0; instrument<midiInstrumentCount; instrument+=1) {
      MenuString *string = &strings[instrument];
      string->label = midiInstrumentTable[instrument];
      string->comment = midiGetInstrumentGroup(instrument);
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
    [csBottomDots] = {.label=strtext("Underline"), .comment=strtext("dots 7 and 8")},
    [csAllDots] = {.label=strtext("Block"), .comment=strtext("all dots")},
    [csLowerLeftDot] = {.label=strtext("Lower Left Dot"), .comment=strtext("dot 7")},
    [csLowerRightDot] = {.label=strtext("Lower Right Dot"), .comment=strtext("dot 8")},
    [csNoDots] = {.label=strtext("Hide"), .comment=strtext("no dots")},
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
        {.label=strtext("Computer Braille")},
        {.label=strtext("Contracted Braille")},
      };

      NAME(strtext("Braille Variant"));
      PROPERTY(yes, isContractedBraille());
      ITEM(newEnumeratedMenuItem(presentationSubmenu, &yes, &itemName, strings));
      CHANGED(ContractedBraille);
    }

    {
      NAME(strtext("Expand Current Word"));
      ITEM(newBooleanMenuItem(presentationSubmenu, &prefs.expandCurrentWord, &itemName));
      TEST(ContractedBraille);
    }

    {
      static const MenuString strings[] = {
        [CTB_CAP_NONE] = {.label=strtext("No Capitalization")},
        [CTB_CAP_SIGN] = {.label=strtext("Use Capital Sign")},
        [CTB_CAP_DOT7] = {.label=strtext("Superimpose Dot 7")},
      };

      NAME(strtext("Capitalization Mode"));
      ITEM(newEnumeratedMenuItem(presentationSubmenu, &prefs.capitalizationMode, &itemName, strings));
      TEST(ContractedBraille);
    }

    {
      static const MenuString strings[] = {
        {.label=strtext("8-dot")},
        {.label=strtext("6-dot")},
      };

      NAME(strtext("Computer Braille Cell Type"));
      PROPERTY(yes, isSixDotComputerBraille());
      ITEM(newEnumeratedMenuItem(presentationSubmenu, &yes, &itemName, strings));
      TEST(ComputerBraille);
      CHANGED(ComputerBraille);
    }

    {
      static const MenuString strings[] = {
        [BRL_FIRMNESS_MINIMUM] = {.label=strtext("Minimum")},
        [BRL_FIRMNESS_LOW] = {.label=strtext("Low")},
        [BRL_FIRMNESS_MEDIUM] = {.label=strtext("Medium")},
        [BRL_FIRMNESS_HIGH] = {.label=strtext("High")},
        [BRL_FIRMNESS_MAXIMUM] = {.label=strtext("Maximum")},
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
      NAME(strtext("Screen Cursor Blink Period"));
      BLINK_PERIOD(&screenCursorBlinkDescriptor);
      ITEM(newTimeMenuItem(indicatorsSubmenu, &period, &itemName));
      TEST(BlinkingScreenCursor);
      CHANGED(ScreenCursorBlinkPeriod);
    }

    {
      NAME(strtext("Screen Cursor Percent Visible"));
      BLINK_VISIBLE(&screenCursorBlinkDescriptor);
      ITEM(newBlinkVisibleMenuItem(indicatorsSubmenu, &visible, &itemName));
      TEST(BlinkingScreenCursor);
      CHANGED(ScreenCursorBlinkPercentage);
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
      NAME(strtext("Attributes Blink Period"));
      BLINK_PERIOD(&attributesUnderlineBlinkDescriptor);
      ITEM(newTimeMenuItem(indicatorsSubmenu, &period, &itemName));
      TEST(BlinkingAttributes);
      CHANGED(AttributesUnderlineBlinkPeriod);
    }

    {
      NAME(strtext("Attributes Percent Visible"));
      BLINK_VISIBLE(&attributesUnderlineBlinkDescriptor);
      ITEM(newBlinkVisibleMenuItem(indicatorsSubmenu, &visible, &itemName));
      TEST(BlinkingAttributes);
      CHANGED(AttributesUnderlineBlinkPercentage);
    }

    {
      NAME(strtext("Blinking Capitals"));
      ITEM(newBooleanMenuItem(indicatorsSubmenu, &prefs.blinkingCapitals, &itemName));
    }

    {
      NAME(strtext("Capitals Blink Period"));
      BLINK_PERIOD(&uppercaseLettersBlinkDescriptor);
      ITEM(newTimeMenuItem(indicatorsSubmenu, &period, &itemName));
      TEST(BlinkingCapitals);
      CHANGED(UppercaseLettersBlinkPeriod);
    }

    {
      NAME(strtext("Capitals Percent Visible"));
      BLINK_VISIBLE(&uppercaseLettersBlinkDescriptor);
      ITEM(newBlinkVisibleMenuItem(indicatorsSubmenu, &visible, &itemName));
      TEST(BlinkingCapitals);
      CHANGED(UppercaseLettersBlinkPercentage);
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
      CHANGED(SkipIdenticalLines);
    }

    {
      NAME(strtext("Skip Blank Braille Windows"));
      ITEM(newBooleanMenuItem(navigationSubmenu, &prefs.skipBlankBrailleWindows, &itemName));
    }

    {
      static const MenuString strings[] = {
        [sbwAll] = {.label=strtext("All")},
        [sbwEndOfLine] = {.label=strtext("End of Line")},
        [sbwRestOfLine] = {.label=strtext("Rest of Line")},
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
      ITEM(newNumericMenuItem(navigationSubmenu, &prefs.brailleWindowOverlap, &itemName, 0, 20, 1, strtext("cells"), NULL));
      CHANGED(BrailleWindowOverlap);
    }

    {
      NAME(strtext("Scroll-aware Cursor Navigation"));
      ITEM(newBooleanMenuItem(navigationSubmenu, &prefs.scrollAwareCursorNavigation, &itemName));
    }

    {
      static const MenuString strings[] = {
        [ctdNone] = {.label=strtext("None")},
        [ctd250ms] = {.label=strtext("250 milliseconds")},
        [ctd500ms] = {.label=strtext("500 milliseconds")},
        [ctd1s] = {.label=strtext("1 second")},
        [ctd2s] = {.label=strtext("2 seconds")},
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

    {
      NAME(strtext("Start Selection with Routing Key"));
      ITEM(newBooleanMenuItem(navigationSubmenu, &prefs.startSelectionWithRoutingKey, &itemName));
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
        [BRL_TYPING_TEXT] = {.label=strtext("Translated via Text Table")},
        [BRL_TYPING_DOTS] = {.label=strtext("Dots via Unicode Braille")},
      };

      NAME(strtext("Typing Mode"));
      ITEM(newEnumeratedMenuItem(typingSubmenu, &prefs.brailleTypingMode, &itemName, strings));
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
        [atOff] = {.label=strtext("Off")},
        [at5s] = {.label=strtext("5 seconds")},
        [at10s] = {.label=strtext("10 seconds")},
        [at20s] = {.label=strtext("20 seconds")},
        [at40s] = {.label=strtext("40 seconds")},
      };

      NAME(strtext("Autorelease Time"));
      ITEM(newEnumeratedMenuItem(inputSubmenu, &prefs.autoreleaseTime, &itemName, strings));
      CHANGED(AutoreleaseTime);
    }

    {
      NAME(strtext("On First Release"));
      ITEM(newBooleanMenuItem(inputSubmenu, &prefs.onFirstRelease, &itemName));
    }

    {
      NAME(strtext("Long Press Time"));
      ITEM(newTimeMenuItem(inputSubmenu, &prefs.longPressTime, &itemName));
      CHANGED(AutorepeatDelay);
    }

    {
      NAME(strtext("Autorepeat Enabled"));
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
        [BRL_SENSITIVITY_MINIMUM] = {.label=strtext("Minimum")},
        [BRL_SENSITIVITY_LOW] = {.label=strtext("Low")},
        [BRL_SENSITIVITY_MEDIUM] = {.label=strtext("Medium")},
        [BRL_SENSITIVITY_HIGH] = {.label=strtext("High")},
        [BRL_SENSITIVITY_MAXIMUM] = {.label=strtext("Maximum")},
      };

      NAME(strtext("Touch Sensitivity"));
      ITEM(newEnumeratedMenuItem(inputSubmenu, &prefs.touchSensitivity, &itemName, strings));
      TEST(TouchSensitivity);
      CHANGED(TouchSensitivity);
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
      CHANGED(AlertTunes);
    }

    {
      static const MenuString strings[] = {
        [tdBeeper] = {.label=strtext("Beeper"), .comment=strtext("console tone generator")},
        [tdPcm] = {.label=strtext("PCM"), .comment=strtext("soundcard digital audio")},
        [tdMidi] = {.label=strtext("MIDI"), .comment=strtext("Musical Instrument Digital Interface")},
        [tdFm] = {.label=strtext("FM"), .comment=strtext("soundcard synthesizer")},
      };

      NAME(strtext("Tune Device"));
      ITEM(newEnumeratedMenuItem(alertsSubmenu, &prefs.tuneDevice, &itemName, strings));
      TEST(AlertTunes);
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

#ifdef ENABLE_SPEECH_SUPPORT
    {
      NAME(strtext("Speak Key Context"));
      ITEM(newBooleanMenuItem(alertsSubmenu, &prefs.speakKeyContext, &itemName));
    }

    {
      NAME(strtext("Speak Modifier Key"));
      ITEM(newBooleanMenuItem(alertsSubmenu, &prefs.speakModifierKey, &itemName));
    }
#endif /* ENABLE_SPEECH_SUPPORT */
  }

#ifdef ENABLE_SPEECH_SUPPORT
  {
    SUBMENU(autospeakSubmenu, rootMenu, strtext("Autospeak Options"));

    {
      NAME(strtext("Autospeak"));
      ITEM(newBooleanMenuItem(autospeakSubmenu, &prefs.autospeak, &itemName));
    }

    {
      NAME(strtext("Speak Selected Line"));
      ITEM(newBooleanMenuItem(autospeakSubmenu, &prefs.autospeakSelectedLine, &itemName));
      TEST(Autospeak);
    }

    {
      NAME(strtext("Speak Selected Character"));
      ITEM(newBooleanMenuItem(autospeakSubmenu, &prefs.autospeakSelectedCharacter, &itemName));
      TEST(Autospeak);
    }

    {
      NAME(strtext("Speak Inserted Characters"));
      ITEM(newBooleanMenuItem(autospeakSubmenu, &prefs.autospeakInsertedCharacters, &itemName));
      TEST(Autospeak);
    }

    {
      NAME(strtext("Speak Deleted Characters"));
      ITEM(newBooleanMenuItem(autospeakSubmenu, &prefs.autospeakDeletedCharacters, &itemName));
      TEST(Autospeak);
    }

    {
      NAME(strtext("Speak Replaced Characters"));
      ITEM(newBooleanMenuItem(autospeakSubmenu, &prefs.autospeakReplacedCharacters, &itemName));
      TEST(Autospeak);
    }

    {
      NAME(strtext("Speak Completed Words"));
      ITEM(newBooleanMenuItem(autospeakSubmenu, &prefs.autospeakCompletedWords, &itemName));
      TEST(Autospeak);
    }

    {
      NAME(strtext("Speak Line Indent"));
      ITEM(newBooleanMenuItem(autospeakSubmenu, &prefs.autospeakLineIndent, &itemName));
      TEST(Autospeak);
    }
  }

  {
    SUBMENU(speechSubmenu, rootMenu, strtext("Speech Options"));

    {
      NAME(strtext("Speech Volume"));
      ITEM(newNumericMenuItem(speechSubmenu, &prefs.speechVolume, &itemName, 0, SPK_VOLUME_MAXIMUM, 1, "%", formatSpeechVolume));
      TEST(SpeechVolume);
      CHANGED(SpeechVolume);
    }

    {
      NAME(strtext("Speech Rate"));
      ITEM(newNumericMenuItem(speechSubmenu, &prefs.speechRate, &itemName, 0, SPK_RATE_MAXIMUM, 1, NULL, formatSpeechRate));
      TEST(SpeechRate);
      CHANGED(SpeechRate);
    }

    {
      NAME(strtext("Speech Pitch"));
      ITEM(newNumericMenuItem(speechSubmenu, &prefs.speechPitch, &itemName, 0, SPK_PITCH_MAXIMUM, 1, NULL, formatSpeechPitch));
      TEST(SpeechPitch);
      CHANGED(SpeechPitch);
    }

    {
      static const MenuString strings[] = {
        [SPK_PUNCTUATION_NONE] = {.label=strtext("None")},
        [SPK_PUNCTUATION_SOME] = {.label=strtext("Some")},
        [SPK_PUNCTUATION_ALL] = {.label=strtext("All")},
      };

      NAME(strtext("Speech Punctuation"));
      ITEM(newEnumeratedMenuItem(speechSubmenu, &prefs.speechPunctuation, &itemName, strings));
      TEST(SpeechPunctuation);
      CHANGED(SpeechPunctuation);
    }

    {
      static const MenuString strings[] = {
        [sucNone] = {.label=strtext("None")},
        // "cap" here, used during speech output, is short for "capital".
        // It is spoken just before an uppercase letter, e.g. "cap A".
        [sucSayCap] = {.label=strtext("Say Cap")},
        [sucRaisePitch] = {.label=strtext("Raise Pitch")},
      };

      NAME(strtext("Speech Uppercase Indicator"));
      ITEM(newEnumeratedMenuItem(speechSubmenu, &prefs.speechUppercaseIndicator, &itemName, strings));
    }

    {
      static const MenuString strings[] = {
        [swsNone] = {.label=strtext("None")},
        [swsSaySpace] = {.label=strtext("Say Space")},
      };

      NAME(strtext("Speech Whitespace Indicator"));
      ITEM(newEnumeratedMenuItem(speechSubmenu, &prefs.speechWhitespaceIndicator, &itemName, strings));
    }

    {
      static const MenuString strings[] = {
        [sayImmediate] = {.label=strtext("Immediate")},
        [sayEnqueue] = {.label=strtext("Enqueue")},
      };

      NAME(strtext("Say Line Mode"));
      ITEM(newEnumeratedMenuItem(speechSubmenu, &prefs.sayLineMode, &itemName, strings));
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
      NAME(strtext("Speech Cursor Blink Period"));
      BLINK_PERIOD(&speechCursorBlinkDescriptor);
      ITEM(newTimeMenuItem(speechSubmenu, &period, &itemName));
      TEST(BlinkingSpeechCursor);
      CHANGED(SpeechCursorBlinkPeriod);
    }

    {
      NAME(strtext("Speech Cursor Percent Visible"));
      BLINK_VISIBLE(&speechCursorBlinkDescriptor);
      ITEM(newBlinkVisibleMenuItem(speechSubmenu, &visible, &itemName));
      TEST(BlinkingSpeechCursor);
      CHANGED(SpeechCursorBlinkPercentage);
    }
  }
#endif /* ENABLE_SPEECH_SUPPORT */

  {
    SUBMENU(timeSubmenu, rootMenu, strtext("Time Presentation"));

    {
      static const MenuString strings[] = {
        [tf24Hour] = {.label=strtext("24 Hour")},
        [tf12Hour] = {.label=strtext("12 Hour")},
      };

      NAME(strtext("Time Format"));
      ITEM(newEnumeratedMenuItem(timeSubmenu, &prefs.timeFormat, &itemName, strings));
    }

    {
      static const MenuString strings[] = {
        [tsColon] = {.label=strtext("Colon"), ":"},
        [tsDot] = {.label=strtext("Dot"), "."},
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
        [dpNone] = {.label=strtext("None")},
        [dpBeforeTime] = {.label=strtext("Before Time")},
        [dpAfterTime] = {.label=strtext("After Time")},
      };

      NAME(strtext("Date Position"));
      ITEM(newEnumeratedMenuItem(timeSubmenu, &prefs.datePosition, &itemName, strings));
    }

    {
      static const MenuString strings[] = {
        [dfYearMonthDay] = {.label=strtext("Year Month Day")},
        [dfMonthDayYear] = {.label=strtext("Month Day Year")},
        [dfDayMonthYear] = {.label=strtext("Day Month Year")},
      };

      NAME(strtext("Date Format"));
      ITEM(newEnumeratedMenuItem(timeSubmenu, &prefs.dateFormat, &itemName, strings));
      TEST(ShowDate);
    }

    {
      static const MenuString strings[] = {
        [dsDash] = {.label=strtext("Dash"), "-"},
        [dsSlash] = {.label=strtext("Slash"), "/"},
        [dsDot] = {.label=strtext("Dot"), "."},
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
        [spNone] = {.label=strtext("None")},
        [spLeft] = {.label=strtext("Left")},
        [spRight] = {.label=strtext("Right")},
      };

      NAME(strtext("Status Position"));
      ITEM(newEnumeratedMenuItem(statusSubmenu, &prefs.statusPosition, &itemName, strings));
      TEST(StatusPosition);
      CHANGED(StatusPosition);
    }

    {
      NAME(strtext("Status Count"));
      ITEM(newNumericMenuItem(statusSubmenu, &prefs.statusCount, &itemName, 0, MAX((int)brl.textColumns/2-1, 0), 1, strtext("cells"), NULL));
      TEST(StatusCount);
      CHANGED(StatusCount);
    }

    {
      static const MenuString strings[] = {
        [ssNone] = {.label=strtext("None")},
        [ssSpace] = {.label=strtext("Space")},
        [ssBlock] = {.label=strtext("Block")},
        [ssStatusSide] = {.label=strtext("Status Side")},
        [ssTextSide] = {.label=strtext("Text Side")},
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
      NAME(strtext("Contraction Table"));
      ITEM(newFilesMenuItem(tablesSubmenu, &itemName, opt_tablesDirectory, CONTRACTION_TABLES_SUBDIRECTORY, CONTRACTION_TABLE_EXTENSION, opt_contractionTable, 1));
      CHANGED(ContractionTable);
      SET(contractionTable);
    }

    {
      NAME(strtext("Attributes Table"));
      ITEM(newFilesMenuItem(tablesSubmenu, &itemName, opt_tablesDirectory, ATTRIBUTES_TABLES_SUBDIRECTORY, ATTRIBUTES_TABLE_EXTENSION, opt_attributesTable, 0));
      CHANGED(AttributesTable);
      SET(attributesTable);
    }
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
      NAME(strtext("Mailing List"));
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
      [LOG_EMERG] = {.label=strtext("Emergency")},
      [LOG_ALERT] = {.label=strtext("Alert")},
      [LOG_CRIT] = {.label=strtext("Critical")},
      [LOG_ERR] = {.label=strtext("Error")},
      [LOG_WARNING] = {.label=strtext("Warning")},
      [LOG_NOTICE] = {.label=strtext("Notice")},
      [LOG_INFO] = {.label=strtext("Information")},
      [LOG_DEBUG] = {.label=strtext("Debug")},
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
