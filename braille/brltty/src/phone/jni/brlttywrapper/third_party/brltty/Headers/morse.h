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

#ifndef BRLTTY_INCLUDED_MORSE
#define BRLTTY_INCLUDED_MORSE

#ifdef __cplusplus
extern "C" {
#endif /* __cplusplus */

#define MORSE_UNITS_MARK_SHORT  1
#define MORSE_UNITS_MARK_LONG   3
#define MORSE_UNITS_GAP_SYMBOL  1
#define MORSE_UNITS_GAP_LETTER  3
#define MORSE_UNITS_GAP_WORD    7
#define MORSE_UNITS_PER_WORD   50
#define MORSE_UNITS_PER_GROUP  60

typedef struct MorseObjectStruct MorseObject;
extern void *newMorseObject (void);
extern void destroyMorseObject (MorseObject *morse);

extern unsigned int getMorsePitch (MorseObject *morse);
extern int setMorsePitch (MorseObject *morse, unsigned int frequency);

extern unsigned int getMorseWordsPerMinute (MorseObject *morse);
extern int setMorseWordsPerMinute (MorseObject *morse, unsigned int rate);

extern unsigned int getMorseGroupsPerMinute (MorseObject *morse);
extern int setMorseGroupsPerMinute (MorseObject *morse, unsigned int rate);

extern int addMorseString (MorseObject *morse, const char *string);
extern int addMorseCharacters (MorseObject *morse, const wchar_t *characters, size_t count);
extern int addMorseCharacter (MorseObject *morse, wchar_t character);
extern int addMorseSpace (MorseObject *morse);

typedef uint8_t MorsePattern;
extern MorsePattern getMorsePattern (wchar_t character);
extern int addMorsePattern (MorseObject *morse, MorsePattern pattern);

extern int playMorseSequence (MorseObject *morse);
extern void clearMorseSequence (MorseObject *morse);

#ifdef __cplusplus
}
#endif /* __cplusplus */

#endif /* BRLTTY_INCLUDED_MORSE */
