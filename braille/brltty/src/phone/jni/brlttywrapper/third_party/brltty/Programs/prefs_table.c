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

#include "prefs.h"
#include "prefs_internal.h"
#include "status_types.h"
#include "defaults.h"

#define PREFERENCE_STRING_TABLE(name, ...) \
static const char *const preferenceStringArray_##name[] = {__VA_ARGS__}; \
static const PreferenceStringTable preferenceStringTable_##name = { \
  .table = preferenceStringArray_##name, \
  .count = ARRAY_COUNT(preferenceStringArray_##name) \
};

PREFERENCE_STRING_TABLE(boolean,
  "no", "yes"
)

PREFERENCE_STRING_TABLE(textStyle,
  "8dot", "contracted", "6dot"
)

PREFERENCE_STRING_TABLE(capitalizationMode,
  "none", "sign", "dot7"
)

PREFERENCE_STRING_TABLE(skipBlankWindowsMode,
  "all", "end", "rest"
)

PREFERENCE_STRING_TABLE(cursorTrackingDelay,
  "0", "25", "50", "100", "200"
)

PREFERENCE_STRING_TABLE(autoreleaseTime,
  "0", "5", "10", "20", "40"
)

PREFERENCE_STRING_TABLE(cursorStyle,
  "underline", "block", "dot7", "dot8"
)

PREFERENCE_STRING_TABLE(brailleFirmness,
  "minimum", "low", "medium", "high", "maximum"
)

PREFERENCE_STRING_TABLE(touchSensitivity,
  "minimum", "low", "medium", "high", "maximum"
)

PREFERENCE_STRING_TABLE(brailleInputMode,
  "text", "dots"
)

PREFERENCE_STRING_TABLE(displayOrientation,
  "normal", "rotated"
)

PREFERENCE_STRING_TABLE(tuneDevice,
  "beeper", "pcm", "midi", "fm"
)

PREFERENCE_STRING_TABLE(speechPunctuation,
  "none", "some", "all"
)

PREFERENCE_STRING_TABLE(speechUppercaseIndicator,
  "none", "cap", "higher"
)

PREFERENCE_STRING_TABLE(speechWhitespaceIndicator,
  "none", "space"
)

PREFERENCE_STRING_TABLE(sayLineMode,
  "immediate", "enqueue"
)

PREFERENCE_STRING_TABLE(timeFormat,
  "24hour", "12hour"
)

PREFERENCE_STRING_TABLE(timeSeparator,
  "colon", "dot"
)

PREFERENCE_STRING_TABLE(datePosition,
  "no", "before", "after"
)

PREFERENCE_STRING_TABLE(dateFormat,
  "ymd", "mdy", "dmy"
)

PREFERENCE_STRING_TABLE(dateSeparator,
  "dash", "slash", "dot"
)

PREFERENCE_STRING_TABLE(statusPosition,
  "none", "left", "right"
)

PREFERENCE_STRING_TABLE(statusSeparator,
  "none", "space", "block", "status", "text"
)

PREFERENCE_STRING_TABLE(statusField,
  "end",
  "wxy", "wx", "wy",
  "cxy", "cx", "cy",
  "cwx", "cwy",
  "sn",
  "dots", "letter",
  "time",
  "wxya", "cxya",
  "generic"
)

const PreferenceEntry preferenceTable[] = {
  { .name = "save-on-exit",
    .defaultValue = DEFAULT_SAVE_ON_EXIT,
    .settingNames = &preferenceStringTable_boolean,
    .setting = &prefs.saveOnExit
  },

  { .name = "show-submenu-sizes",
    .defaultValue = DEFAULT_SHOW_SUBMENU_SIZES,
    .settingNames = &preferenceStringTable_boolean,
    .setting = &prefs.showSubmenuSizes
  },

  { .name = "show-advanced-submenus",
    .defaultValue = DEFAULT_SHOW_ADVANCED_SUBMENUS,
    .settingNames = &preferenceStringTable_boolean,
    .setting = &prefs.showAdvancedSubmenus
  },

  { .name = "show-all-items",
    .defaultValue = DEFAULT_SHOW_ALL_ITEMS,
    .settingNames = &preferenceStringTable_boolean,
    .setting = &prefs.showAllItems
  },

  { .name = "text-style",
    .defaultValue = DEFAULT_TEXT_STYLE,
    .settingNames = &preferenceStringTable_textStyle,
    .setting = &prefs.textStyle
  },

  { .name = "expand-current-word",
    .defaultValue = DEFAULT_EXPAND_CURRENT_WORD,
    .settingNames = &preferenceStringTable_boolean,
    .setting = &prefs.expandCurrentWord
  },

  { .name = "capitalization-mode",
    .defaultValue = DEFAULT_CAPITALIZATION_MODE,
    .settingNames = &preferenceStringTable_capitalizationMode,
    .setting = &prefs.capitalizationMode
  },

  { .name = "braille-firmness",
    .defaultValue = DEFAULT_BRAILLE_FIRMNESS,
    .settingNames = &preferenceStringTable_brailleFirmness,
    .setting = &prefs.brailleFirmness
  },

  { .name = "show-screen-cursor",
    .defaultValue = DEFAULT_SHOW_SCREEN_CURSOR,
    .settingNames = &preferenceStringTable_boolean,
    .setting = &prefs.showScreenCursor
  },

  { .name = "screen-cursor-style",
    .defaultValue = DEFAULT_SCREEN_CURSOR_STYLE,
    .settingNames = &preferenceStringTable_cursorStyle,
    .setting = &prefs.screenCursorStyle
  },

  { .name = "blinking-screen-cursor",
    .defaultValue = DEFAULT_BLINKING_SCREEN_CURSOR,
    .settingNames = &preferenceStringTable_boolean,
    .setting = &prefs.blinkingScreenCursor
  },

  { .name = "screen-cursor-visible-time",
    .defaultValue = DEFAULT_SCREEN_CURSOR_VISIBLE_TIME,
    .setting = &prefs.screenCursorVisibleTime
  },

  { .name = "screen-cursor-invisible-time",
    .defaultValue = DEFAULT_SCREEN_CURSOR_INVISIBLE_TIME,
    .setting = &prefs.screenCursorInvisibleTime
  },

  { .name = "show-attributes",
    .defaultValue = DEFAULT_SHOW_ATTRIBUTES,
    .settingNames = &preferenceStringTable_boolean,
    .setting = &prefs.showAttributes
  },

  { .name = "blinking-attributes",
    .defaultValue = DEFAULT_BLINKING_ATTRIBUTES,
    .settingNames = &preferenceStringTable_boolean,
    .setting = &prefs.blinkingAttributes
  },

  { .name = "attributes-visible-time",
    .defaultValue = DEFAULT_ATTRIBUTES_VISIBLE_TIME,
    .setting = &prefs.attributesVisibleTime
  },

  { .name = "attributes-invisible-time",
    .defaultValue = DEFAULT_ATTRIBUTES_INVISIBLE_TIME,
    .setting = &prefs.attributesInvisibleTime
  },

  { .name = "blinking-capitals",
    .defaultValue = DEFAULT_BLINKING_CAPITALS,
    .settingNames = &preferenceStringTable_boolean,
    .setting = &prefs.blinkingCapitals
  },

  { .name = "capitals-visible-time",
    .defaultValue = DEFAULT_CAPITALS_VISIBLE_TIME,
    .setting = &prefs.capitalsVisibleTime
  },

  { .name = "capitals-invisible-time",
    .defaultValue = DEFAULT_CAPITALS_INVISIBLE_TIME,
    .setting = &prefs.capitalsInvisibleTime
  },

  { .name = "word-wrap",
    .defaultValue = DEFAULT_WORD_WRAP,
    .settingNames = &preferenceStringTable_boolean,
    .setting = &prefs.wordWrap
  },

  { .name = "skip-identical-lines",
    .defaultValue = DEFAULT_SKIP_IDENTICAL_LINES,
    .settingNames = &preferenceStringTable_boolean,
    .setting = &prefs.skipIdenticalLines
  },

  { .name = "skip-blank-braille-windows",
    .defaultValue = DEFAULT_SKIP_BLANK_BRAILLE_WINDOWS,
    .settingNames = &preferenceStringTable_boolean,
    .setting = &prefs.skipBlankBrailleWindows
  },

  { .name = "skip-blank-braille-windows-mode",
    .defaultValue = DEFAULT_SKIP_BLANK_BRAILLE_WINDOWS_MODE,
    .settingNames = &preferenceStringTable_skipBlankWindowsMode,
    .setting = &prefs.skipBlankBrailleWindowsMode
  },

  { .name = "sliding-braille-window",
    .defaultValue = DEFAULT_SLIDING_BRAILLE_WINDOW,
    .settingNames = &preferenceStringTable_boolean,
    .setting = &prefs.slidingBrailleWindow
  },

  { .name = "eager-sliding-braille-window",
    .defaultValue = DEFAULT_EAGER_SLIDING_BRAILLE_WINDOW,
    .settingNames = &preferenceStringTable_boolean,
    .setting = &prefs.eagerSlidingBrailleWindow
  },

  { .name = "braille-window-overlap",
    .defaultValue = DEFAULT_BRAILLE_WINDOW_OVERLAP,
    .setting = &prefs.brailleWindowOverlap
  },

  { .name = "cursor-tracking-delay",
    .defaultValue = DEFAULT_CURSOR_TRACKING_DELAY,
    .settingNames = &preferenceStringTable_cursorTrackingDelay,
    .setting = &prefs.cursorTrackingDelay
  },

  { .name = "track-screen-scroll",
    .defaultValue = DEFAULT_TRACK_SCREEN_SCROLL,
    .settingNames = &preferenceStringTable_boolean,
    .setting = &prefs.trackScreenScroll
  },

  { .name = "track-screen-pointer",
    .defaultValue = DEFAULT_TRACK_SCREEN_POINTER,
    .settingNames = &preferenceStringTable_boolean,
    .setting = &prefs.trackScreenPointer
  },

  { .name = "highlight-braille-window-location",
    .defaultValue = DEFAULT_HIGHLIGHT_BRAILLE_WINDOW_LOCATION,
    .settingNames = &preferenceStringTable_boolean,
    .setting = &prefs.highlightBrailleWindowLocation
  },

  { .name = "autorelease-time",
    .defaultValue = DEFAULT_AUTORELEASE_TIME,
    .settingNames = &preferenceStringTable_autoreleaseTime,
    .setting = &prefs.autoreleaseTime
  },

  { .name = "first-release",
    .defaultValue = DEFAULT_FIRST_RELEASE,
    .setting = &prefs.firstRelease
  },

  { .name = "long-press-time",
    .defaultValue = DEFAULT_LONG_PRESS_TIME,
    .setting = &prefs.longPressTime
  },

  { .name = "autorepeat",
    .defaultValue = DEFAULT_AUTOREPEAT_ENABLED,
    .settingNames = &preferenceStringTable_boolean,
    .setting = &prefs.autorepeatEnabled
  },

  { .name = "autorepeat-interval",
    .defaultValue = DEFAULT_AUTOREPEAT_INTERVAL,
    .setting = &prefs.autorepeatInterval
  },

  { .name = "autorepeat-panning",
    .defaultValue = DEFAULT_AUTOREPEAT_PANNING,
    .settingNames = &preferenceStringTable_boolean,
    .setting = &prefs.autorepeatPanning
  },

  { .name = "touch-navigation",
    .defaultValue = DEFAULT_TOUCH_NAVIGATION,
    .settingNames = &preferenceStringTable_boolean,
    .setting = &prefs.touchNavigation
  },

  { .name = "touch-sensitivity",
    .defaultValue = DEFAULT_TOUCH_SENSITIVITY,
    .settingNames = &preferenceStringTable_touchSensitivity,
    .setting = &prefs.touchSensitivity
  },

  { .name = "braille-keyboard-enabled",
    .defaultValue = DEFAULT_BRAILLE_KEYBOARD_ENABLED,
    .settingNames = &preferenceStringTable_boolean,
    .setting = &prefs.brailleKeyboardEnabled
  },

  { .name = "braille-input-mode",
    .defaultValue = DEFAULT_BRAILLE_INPUT_MODE,
    .settingNames = &preferenceStringTable_brailleInputMode,
    .setting = &prefs.brailleInputMode
  },

  { .name = "braille-quick-space",
    .defaultValue = DEFAULT_BRAILLE_QUICK_SPACE,
    .setting = &prefs.brailleQuickSpace
  },

  { .name = "braille-display-orientation",
    .defaultValue = DEFAULT_BRAILLE_DISPLAY_ORIENTATION,
    .settingNames = &preferenceStringTable_displayOrientation,
    .setting = &prefs.brailleDisplayOrientation
  },

  { .name = "alerts-console-bell",
    .defaultValue = DEFAULT_CONSOLE_BELL_ALERT,
    .settingNames = &preferenceStringTable_boolean,
    .setting = &prefs.consoleBellAlert
  },

  { .name = "alerts-keyboard-leds",
    .defaultValue = DEFAULT_KEYBOARD_LED_ALERTS,
    .settingNames = &preferenceStringTable_boolean,
    .setting = &prefs.keyboardLedAlerts
  },

  { .name = "alert-tunes",
    .defaultValue = DEFAULT_ALERT_TUNES,
    .settingNames = &preferenceStringTable_boolean,
    .setting = &prefs.alertTunes
  },

  { .name = "tune-device",
    .defaultValue = DEFAULT_TUNE_DEVICE,
    .settingNames = &preferenceStringTable_tuneDevice,
    .setting = &prefs.tuneDevice
  },

  { .name = "pcm-volume",
    .defaultValue = DEFAULT_PCM_VOLUME,
    .setting = &prefs.pcmVolume
  },

  { .name = "midi-volume",
    .defaultValue = DEFAULT_MIDI_VOLUME,
    .setting = &prefs.midiVolume
  },

  { .name = "midi-instrument",
    .defaultValue = DEFAULT_MIDI_INSTRUMENT,
    .setting = &prefs.midiInstrument
  },

  { .name = "fm-volume",
    .defaultValue = DEFAULT_FM_VOLUME,
    .setting = &prefs.fmVolume
  },

  { .name = "alert-dots",
    .defaultValue = DEFAULT_ALERT_DOTS,
    .settingNames = &preferenceStringTable_boolean,
    .setting = &prefs.alertDots
  },

  { .name = "alert-messages",
    .defaultValue = DEFAULT_ALERT_MESSAGES,
    .settingNames = &preferenceStringTable_boolean,
    .setting = &prefs.alertMessages
  },

  { .name = "speech-volume",
    .defaultValue = DEFAULT_SPEECH_VOLUME,
    .setting = &prefs.speechVolume
  },

  { .name = "speech-rate",
    .defaultValue = DEFAULT_SPEECH_RATE,
    .setting = &prefs.speechRate
  },

  { .name = "speech-pitch",
    .defaultValue = DEFAULT_SPEECH_PITCH,
    .setting = &prefs.speechPitch
  },

  { .name = "speech-punctuation",
    .defaultValue = DEFAULT_SPEECH_PUNCTUATION,
    .settingNames = &preferenceStringTable_speechPunctuation,
    .setting = &prefs.speechPunctuation
  },

  { .name = "speech-uppercase-indicator",
    .defaultValue = DEFAULT_SPEECH_UPPERCASE_INDICATOR,
    .settingNames = &preferenceStringTable_speechUppercaseIndicator,
    .setting = &prefs.speechUppercaseIndicator
  },

  { .name = "speech-whitespace-indicator",
    .defaultValue = DEFAULT_SPEECH_WHITESPACE_INDICATOR,
    .settingNames = &preferenceStringTable_speechWhitespaceIndicator,
    .setting = &prefs.speechWhitespaceIndicator
  },

  { .name = "say-line-mode",
    .defaultValue = DEFAULT_SAY_LINE_MODE,
    .settingNames = &preferenceStringTable_sayLineMode,
    .setting = &prefs.sayLineMode
  },

  { .name = "autospeak",
    .defaultValue = DEFAULT_AUTOSPEAK,
    .settingNames = &preferenceStringTable_boolean,
    .setting = &prefs.autospeak
  },

  { .name = "autospeak-selected-line",
    .defaultValue = DEFAULT_AUTOSPEAK_SELECTED_LINE,
    .settingNames = &preferenceStringTable_boolean,
    .setting = &prefs.autospeakSelectedLine
  },

  { .name = "autospeak-selected-character",
    .defaultValue = DEFAULT_AUTOSPEAK_SELECTED_CHARACTER,
    .settingNames = &preferenceStringTable_boolean,
    .setting = &prefs.autospeakSelectedCharacter
  },

  { .name = "autospeak-inserted-characters",
    .defaultValue = DEFAULT_AUTOSPEAK_INSERTED_CHARACTERS,
    .settingNames = &preferenceStringTable_boolean,
    .setting = &prefs.autospeakInsertedCharacters
  },

  { .name = "autospeak-deleted-characters",
    .defaultValue = DEFAULT_AUTOSPEAK_DELETED_CHARACTERS,
    .settingNames = &preferenceStringTable_boolean,
    .setting = &prefs.autospeakDeletedCharacters
  },

  { .name = "autospeak-replaced-characters",
    .defaultValue = DEFAULT_AUTOSPEAK_REPLACED_CHARACTERS,
    .settingNames = &preferenceStringTable_boolean,
    .setting = &prefs.autospeakReplacedCharacters
  },

  { .name = "autospeak-completed-words",
    .defaultValue = DEFAULT_AUTOSPEAK_COMPLETED_WORDS,
    .settingNames = &preferenceStringTable_boolean,
    .setting = &prefs.autospeakCompletedWords
  },

  { .name = "autospeak-line-indent",
    .defaultValue = DEFAULT_AUTOSPEAK_LINE_INDENT,
    .settingNames = &preferenceStringTable_boolean,
    .setting = &prefs.autospeakLineIndent
  },

  { .name = "show-speech-cursor",
    .defaultValue = DEFAULT_SHOW_SPEECH_CURSOR,
    .settingNames = &preferenceStringTable_boolean,
    .setting = &prefs.showSpeechCursor
  },

  { .name = "speech-cursor-style",
    .defaultValue = DEFAULT_SPEECH_CURSOR_STYLE,
    .settingNames = &preferenceStringTable_cursorStyle,
    .setting = &prefs.speechCursorStyle
  },

  { .name = "blinking-speech-cursor",
    .defaultValue = DEFAULT_BLINKING_SPEECH_CURSOR,
    .settingNames = &preferenceStringTable_boolean,
    .setting = &prefs.blinkingSpeechCursor
  },

  { .name = "speech-cursor-visible-time",
    .defaultValue = DEFAULT_SPEECH_CURSOR_VISIBLE_TIME,
    .setting = &prefs.speechCursorVisibleTime
  },

  { .name = "speech-cursor-invisible-time",
    .defaultValue = DEFAULT_SPEECH_CURSOR_INVISIBLE_TIME,
    .setting = &prefs.speechCursorInvisibleTime
  },

  { .name = "time-format",
    .defaultValue = DEFAULT_TIME_FORMAT,
    .settingNames = &preferenceStringTable_timeFormat,
    .setting = &prefs.timeFormat
  },

  { .name = "time-separator",
    .defaultValue = DEFAULT_TIME_SEPARATOR,
    .settingNames = &preferenceStringTable_timeSeparator,
    .setting = &prefs.timeSeparator
  },

  { .name = "show-seconds",
    .defaultValue = DEFAULT_SHOW_SECONDS,
    .settingNames = &preferenceStringTable_boolean,
    .setting = &prefs.showSeconds
  },

  { .name = "date-position",
    .defaultValue = DEFAULT_DATE_POSITION,
    .settingNames = &preferenceStringTable_datePosition,
    .setting = &prefs.datePosition
  },

  { .name = "date-format",
    .defaultValue = DEFAULT_DATE_FORMAT,
    .settingNames = &preferenceStringTable_dateFormat,
    .setting = &prefs.dateFormat
  },

  { .name = "date-separator",
    .defaultValue = DEFAULT_DATE_SEPARATOR,
    .settingNames = &preferenceStringTable_dateSeparator,
    .setting = &prefs.dateSeparator
  },

  { .name = "status-position",
    .defaultValue = DEFAULT_STATUS_POSITION,
    .settingNames = &preferenceStringTable_statusPosition,
    .setting = &prefs.statusPosition
  },

  { .name = "status-count",
    .defaultValue = DEFAULT_STATUS_COUNT,
    .setting = &prefs.statusCount
  },

  { .name = "status-separator",
    .defaultValue = DEFAULT_STATUS_SEPARATOR,
    .settingNames = &preferenceStringTable_statusSeparator,
    .setting = &prefs.statusSeparator
  },

  { .name = "status-fields",
    .defaultValue = sfEnd,
    .encountered = &statusFieldsSet,
    .settingNames = &preferenceStringTable_statusField,
    .settingCount = ARRAY_COUNT(prefs.statusFields),
    .setting = prefs.statusFields
  }
};

const unsigned char preferenceCount = ARRAY_COUNT(preferenceTable);

const PreferenceAliasEntry preferenceAliasTable[] = {
  {.oldName="autorepeat-delay", .newName="long-press-time"},
  {.oldName="show-cursor", .newName="show-screen-cursor"},
  {.oldName="cursor-style", .newName="screen-cursor-style"},
  {.oldName="blinking-cursor", .newName="blinking-screen-cursor"},
  {.oldName="cursor-visible-time", .newName="screen-cursor-visible-time"},
  {.oldName="cursor-invisible-time", .newName="screen-cursor-invisible-time"},
  {.oldName="skip-blank-windows", .newName="skip-blank-braille-windows"},
  {.oldName="skip-blank-windows-mode", .newName="skip-blank-braille-windows-mode"},
  {.oldName="sliding-window", .newName="sliding-braille-window"},
  {.oldName="eager-sliding-window", .newName="eager-sliding-braille-window"},
  {.oldName="window-overlap", .newName="braille-window-overlap"},
  {.oldName="window-follows-pointer", .newName="track-screen-pointer"},
  {.oldName="highlight-window", .newName="highlight-braille-window-location"},
  {.oldName="uppercase-indicator", .newName="speech-uppercase-indicator"},
  {.oldName="whitespace-indicator", .newName="speech-whitespace-indicator"},
  {.oldName="braille-sensitivity", .newName="touch-sensitivity"},
};

const unsigned char preferenceAliasCount = ARRAY_COUNT(preferenceAliasTable);
