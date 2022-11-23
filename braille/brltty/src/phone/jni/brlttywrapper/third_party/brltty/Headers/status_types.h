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

#ifndef BRLTTY_INCLUDED_STATUS_TYPES
#define BRLTTY_INCLUDED_STATUS_TYPES

#ifdef __cplusplus
extern "C" {
#endif /* __cplusplus */

typedef enum {
  sfEnd,
  sfWindowCoordinates,
  sfWindowColumn,
  sfWindowRow,
  sfCursorCoordinates,
  sfCursorColumn,
  sfCursorRow,
  sfCursorAndWindowColumn,
  sfCursorAndWindowRow,
  sfScreenNumber,
  sfStateDots,
  sfStateLetter,
  sfTime,
  sfAlphabeticWindowCoordinates,
  sfAlphabeticCursorCoordinates,
  sfGeneric
} StatusField;

#define GSC_MARKER 0XFF /* must be in GSC_FIRST */
typedef enum {
  GSC_FIRST = 0 /* must be first */,

  /* numbers */
  gscBrailleWindowColumn,
  gscBrailleWindowRow,
  gscScreenCursorColumn,
  gscScreenCursorRow,
  gscScreenNumber,

  /* flags */
  gscFrozenScreen,
  gscDisplayMode,
  gscTextStyle,
  gscSlidingBrailleWindow,
  gscSkipIdenticalLines,
  gscSkipBlankBrailleWindows,
  gscShowScreenCursor,
  gscHideScreenCursor,
  gscTrackScreenCursor,
  gscScreenCursorStyle,
  gscBlinkingScreenCursor,
  gscShowAttributes,
  gscBlinkingAttributes,
  gscBlinkingCapitals,
  gscAlertTunes,
  gscAutorepeat,
  gscAutospeak,
  gscBrailleInputMode,

  GSC_COUNT /* must be last */
} BRL_GenericStatusCell;

#ifdef __cplusplus
}
#endif /* __cplusplus */

#endif /* BRLTTY_INCLUDED_STATUS_TYPES */
