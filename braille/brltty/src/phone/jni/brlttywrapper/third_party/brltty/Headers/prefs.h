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

#ifndef BRLTTY_INCLUDED_PREFS
#define BRLTTY_INCLUDED_PREFS

#ifdef __cplusplus
extern "C" {
#endif /* __cplusplus */

typedef enum {
  csUnderline,
  csBlock,
  csLowerLeftDot,
  csLowerRightDot
} CursorStyles;

typedef enum {
  tsComputerBraille8,
  tsContractedBraille,
  tsComputerBraille6
} TextStyles;

typedef enum {
  sbwAll,
  sbwEndOfLine,
  sbwRestOfLine
} SkipBlankWindowsMode;

typedef enum {
  sayImmediate,
  sayEnqueue
} SayMode;

typedef enum {
  sucNone,
  sucSayCap,
  sucRaisePitch
} SpeechUppercaseIndicator;

typedef enum {
  swsNone,
  swsSaySpace
} SpeechWhitespaceIndicator;

typedef enum {
  tf24Hour,
  tf12Hour
} TimeFormat;

typedef enum {
  tsColon,
  tsDot
} TimeSeparator;

typedef enum {
  dpNone,
  dpBeforeTime,
  dpAfterTime
} DatePosition;

typedef enum {
  dfYearMonthDay,
  dfMonthDayYear,
  dfDayMonthYear
} DateFormat;

typedef enum {
  dsDash,
  dsSlash,
  dsDot
} DateSeparator;

typedef enum {
  spNone,
  spLeft,
  spRight
} StatusPosition;

typedef enum {
  ssNone,
  ssSpace,
  ssBlock,
  ssStatusSide,
  ssTextSide
} StatusSeparator;

typedef enum {
  atOff,
  at5s,
  at10s,
  at20s,
  at40s
} AutoreleaseTime;

typedef enum {
  ctdNone,
  ctd250ms,
  ctd500ms,
  ctd1s,
  ctd2s
} CursorTrackingDelay;

/*
 * Structure definition for preferences (settings which are saveable).
 */
typedef struct {
  unsigned char magic[2];
  unsigned char showScreenCursor;
  unsigned char version;
  unsigned char showAttributes;
  unsigned char touchSensitivity;
  unsigned char blinkingScreenCursor;
  unsigned char autorepeatEnabled;
  unsigned char blinkingCapitals;
  unsigned char longPressTime;
  unsigned char blinkingAttributes;
  unsigned char autorepeatInterval;
  unsigned char screenCursorStyle;
  unsigned char sayLineMode;
  unsigned char screenCursorVisibleTime;
  unsigned char autospeak;
  unsigned char screenCursorInvisibleTime;
  unsigned char pcmVolume;
  unsigned char capitalsVisibleTime;
  unsigned char midiVolume;
  unsigned char capitalsInvisibleTime;
  unsigned char fmVolume;
  unsigned char attributesVisibleTime;
  unsigned char highlightBrailleWindowLocation;
  unsigned char attributesInvisibleTime;
  unsigned char trackScreenPointer;
  unsigned char textStyle;
  unsigned char autorepeatPanning;
  unsigned char slidingBrailleWindow;
  unsigned char eagerSlidingBrailleWindow;
  unsigned char alertTunes;
  unsigned char tuneDevice;
  unsigned char skipIdenticalLines;
  unsigned char alertMessages;
  unsigned char skipBlankBrailleWindowsMode;
  unsigned char alertDots;
  unsigned char skipBlankBrailleWindows;
  unsigned char midiInstrument;
  unsigned char expandCurrentWord;
  unsigned char brailleWindowOverlap;
  unsigned char speechRate;
  unsigned char speechVolume;
  unsigned char brailleFirmness;
  unsigned char speechPunctuation;
  unsigned char speechPitch;
  unsigned char statusPosition;
  unsigned char statusCount;
  unsigned char statusSeparator;
  unsigned char statusFields[10];

  /*****************************************************************************/
  /* No fields above this point should be added, removed, or reordered so that */
  /* backward compatibility with old binary preference files will be retained. */
  /*                                                                           */
  /* Fields below this point may be modified as desired.                       */
  /*****************************************************************************/

  unsigned char brailleKeyboardEnabled;
  unsigned char brailleInputMode;
  unsigned char brailleQuickSpace;
  unsigned char brailleDisplayOrientation;

  unsigned char wordWrap;
  unsigned char capitalizationMode;

  unsigned char speechUppercaseIndicator;
  unsigned char speechWhitespaceIndicator;

  unsigned char autospeakSelectedLine;
  unsigned char autospeakSelectedCharacter;
  unsigned char autospeakInsertedCharacters;
  unsigned char autospeakDeletedCharacters;
  unsigned char autospeakReplacedCharacters;
  unsigned char autospeakCompletedWords;
  unsigned char autospeakLineIndent;

  unsigned char showSpeechCursor;
  unsigned char speechCursorStyle;
  unsigned char blinkingSpeechCursor;
  unsigned char speechCursorVisibleTime;
  unsigned char speechCursorInvisibleTime;

  unsigned char timeFormat;
  unsigned char timeSeparator;
  unsigned char showSeconds;
  unsigned char datePosition;
  unsigned char dateFormat;
  unsigned char dateSeparator;

  unsigned char consoleBellAlert;
  unsigned char keyboardLedAlerts;

  unsigned char autoreleaseTime;
  unsigned char firstRelease;
  unsigned char touchNavigation;

  unsigned char cursorTrackingDelay;
  unsigned char trackScreenScroll;

  unsigned char saveOnExit;
  unsigned char showSubmenuSizes;
  unsigned char showAdvancedSubmenus;
  unsigned char showAllItems;
} PACKED Preferences;

extern Preferences prefs;		/* current preferences settings */
#define PREFERENCES_TIME(time) ((time) * 10)

extern void resetPreferences (void);
extern int setPreference (char *string);
extern void setStatusFields (const unsigned char *fields);

extern char *makePreferencesFilePath (const char *name);
extern int loadPreferencesFile (const char *path);
extern int savePreferencesFile (const char *path);

typedef struct PreferenceEntryStruct PreferenceEntry;
extern const PreferenceEntry *findPreferenceEntry (const char *name);

#ifdef __cplusplus
}
#endif /* __cplusplus */

#endif /* BRLTTY_INCLUDED_PREFS */
