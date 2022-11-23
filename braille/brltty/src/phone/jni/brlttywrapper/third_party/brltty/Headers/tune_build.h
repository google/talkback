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

#ifndef BRLTTY_INCLUDED_TUNE_BUILD
#define BRLTTY_INCLUDED_TUNE_BUILD

#include "tune.h"

#ifdef __cplusplus
extern "C" {
#endif /* __cplusplus */

#define NOTES_PER_SCALE 7

typedef enum {
  TUNE_STATUS_OK,
  TUNE_STATUS_SYNTAX,
  TUNE_STATUS_FATAL
} TuneStatus;

typedef struct TuneBuilderStruct TuneBuilder;

extern TuneBuilder *newTuneBuilder (void);
extern void resetTuneBuilder (TuneBuilder *tune);
extern void destroyTuneBuilder (TuneBuilder *tb);

extern TuneStatus getTuneStatus (TuneBuilder *tb);
extern void setTuneSourceName (TuneBuilder *tb, const char *name);
extern void setTuneSourceIndex (TuneBuilder *tb, unsigned int index);
extern void incrementTuneSourceIndex (TuneBuilder *tb);

extern int parseTuneString (TuneBuilder *tune, const char *string);
extern int parseTuneText (TuneBuilder *tune, const wchar_t *text);
extern ToneElement *getTune (TuneBuilder *tune);

extern int addTone (TuneBuilder *tune, const ToneElement *tone);
extern int addNote (TuneBuilder *tune, unsigned char note, int duration);

#ifdef __cplusplus
}
#endif /* __cplusplus */

#endif /* BRLTTY_INCLUDED_TUNE_BUILD */
