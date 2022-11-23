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

#ifndef BRLTTY_INCLUDED_CORE
#define BRLTTY_INCLUDED_CORE

#include "prologue.h"

#include "strfmth.h"
#include "program.h"
#include "timing.h"
#include "cmd.h"
#include "brl.h"
#include "spk.h"
#include "scr_types.h"
#include "ses.h"
#include "ctb.h"
#include "ktb.h"
#include "prefs.h"
#include "profile_types.h"

#ifdef __cplusplus
extern "C" {
#endif /* __cplusplus */

extern ScreenDescription scr;
#define SCR_COLUMN_OK(column) IS_WITHIN_BOUNDS((column), scr.cols)
#define SCR_ROW_OK(row) IS_WITHIN_BOUNDS((row), scr.rows)
#define SCR_COORDINATES_OK(column,row) (SCR_COLUMN_OK((column)) && SCR_ROW_OK((row)))
#define SCR_CURSOR_OK() SCR_COORDINATES_OK(scr.posx, scr.posy)
#define SCR_COLUMN_NUMBER(column) (SCR_COLUMN_OK((column))? (column)+1: 0)
#define SCR_ROW_NUMBER(row) (SCR_ROW_OK((row))? (row)+1: 0)

extern void updateSessionAttributes (void);
extern SessionEntry *ses;

typedef int (*IsSameCharacter) (
  const ScreenCharacter *character1,
  const ScreenCharacter *character2
);

extern int isSameText (
  const ScreenCharacter *character1,
  const ScreenCharacter *character2
);

extern int isSameAttributes (
  const ScreenCharacter *character1,
  const ScreenCharacter *character2
);

extern int
isSameCharacter (
  const ScreenCharacter *character1,
  const ScreenCharacter *character2
);

extern int isSameRow (
  const ScreenCharacter *characters1,
  const ScreenCharacter *characters2,
  int count,
  IsSameCharacter isSameCharacter
);

extern unsigned char infoMode;

extern int canBraille (void);
extern int writeBrailleCharacters (const char *mode, const wchar_t *characters, size_t length);
extern void fillStatusSeparator (wchar_t *text, unsigned char *dots);

extern int writeBrailleText (const char *mode, const char *text);
extern int showBrailleText (const char *mode, const char *text, int minimumDelay);

extern char *opt_tablesDirectory;
extern char *opt_textTable;
extern char *opt_attributesTable;
extern char *opt_contractionTable;
extern char *opt_keyboardTable;

extern int opt_releaseDevice;

extern int isWordBreak (const ScreenCharacter *characters, int x);
extern int getWordWrapLength (int row, int from, int count);
extern void setWordWrapStart (int start);

extern void placeRightEdge (int column);
extern void placeBrailleWindowRight (void);
extern void placeBrailleWindowHorizontally (int x);

extern int moveBrailleWindowLeft (unsigned int amount);
extern int moveBrailleWindowRight (unsigned int amount);

extern int shiftBrailleWindowLeft (unsigned int amount);
extern int shiftBrailleWindowRight (unsigned int amount);

extern void slideBrailleWindowVertically (int y);

extern int showScreenCursor (void);
extern int trackScreenCursor (int place);
extern void cancelDelayedCursorTrackingAlarm (void);

typedef struct {
  TimeValue value;
  TimeComponents components;
  const char *meridian;
} TimeFormattingData;

extern void getTimeFormattingData (TimeFormattingData *fmt);
extern STR_DECLARE_FORMATTER(formatBrailleTime, const TimeFormattingData *fmt);

#ifdef ENABLE_CONTRACTED_BRAILLE
extern int isContracted;
extern int contractedLength;
extern int contractedStart;
extern int contractedOffsets[0X100];
extern int contractedTrack;

extern int isContracting (void);
extern int getUncontractedCursorOffset (int x, int y);
extern int getContractedCursor (void);
extern int getContractedLength (unsigned int outputLimit);
#endif /* ENABLE_CONTRACTED_BRAILLE */

extern ContractionTable *contractionTable;

extern KeyTable *keyboardTable;

extern ProgramExitStatus brlttyPrepare (int argc, char *argv[]);
extern ProgramExitStatus brlttyStart (void);

extern void setPreferences (const Preferences *newPreferences);
extern int loadPreferences (void);
extern int savePreferences (void);

extern unsigned char getScreenCursorDots (void);

extern BrailleDisplay brl;			/* braille driver reference */
extern unsigned int textStart;
extern unsigned int textCount;
extern unsigned char textMaximized;
extern unsigned int statusStart;
extern unsigned int statusCount;
extern unsigned int fullWindowShift;			/* Full window horizontal distance */
extern unsigned int halfWindowShift;			/* Half window horizontal distance */
extern unsigned int verticalWindowShift;			/* Window vertical distance */

extern void setBrailleOn (void);
extern void setBrailleOff (const char *message);
extern void enableBrailleDriver (void);
extern void disableBrailleDriver (const char *reason);
extern int constructBrailleDriver (void);
extern void destructBrailleDriver (void);
extern void forgetDevices (void);

extern void reconfigureBrailleWindow (void);
extern int haveStatusCells (void);

typedef enum {
  SCT_WORD,
  SCT_NONWORD,
  SCT_SPACE
} ScreenCharacterType;

extern ScreenCharacterType getScreenCharacterType (const ScreenCharacter *character);
extern int findFirstNonSpaceCharacter (const ScreenCharacter *characters, int count);
extern int findLastNonSpaceCharacter (const ScreenCharacter *characters, int count);
extern int isAllSpaceCharacters (const ScreenCharacter *characters, int count);

#ifdef ENABLE_SPEECH_SUPPORT
extern volatile SpeechSynthesizer spk;
extern int opt_quietIfNoBraille;

extern int isAutospeakActive (void);

extern void sayScreenCharacters (const ScreenCharacter *characters, size_t count, SayOptions options);
extern void speakCharacters (const ScreenCharacter *characters, size_t count, int spell, int interrupt);
extern int speakIndent (const ScreenCharacter *characters, int count, int evenIfNoIndent);
extern void trackSpeech (void);

extern void enableSpeechDriver (int sayBanner);
extern void disableSpeechDriver (const char *reason);
extern int constructSpeechDriver (void);
extern void destructSpeechDriver (void);
#endif /* ENABLE_SPEECH_SUPPORT */

extern void enableScreenDriver (void);
extern void disableScreenDriver (const char *reason);

#ifdef __MINGW32__
extern int isWindowsService;
#endif /* __MINGW32__ */

extern const ProfileDescriptor languageProfile;

#ifdef __cplusplus
}
#endif /* __cplusplus */

#endif /* BRLTTY_INCLUDED_CORE */
