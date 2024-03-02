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

#ifndef BRLTTY_INCLUDED_EMBED
#define BRLTTY_INCLUDED_EMBED

#include "program.h"

#ifdef __cplusplus
extern "C" {
#endif /* __cplusplus */

typedef enum {
  WAIT_STOP,
  WAIT_CONTINUE
} WaitResult;

FUNCTION_DECLARE(brlttyConstruct, ProgramExitStatus, (int argc, char *argv[]));
FUNCTION_DECLARE(brlttyDestruct, int, (void));

FUNCTION_DECLARE(brlttyEnableInterrupt, int, (void));
FUNCTION_DECLARE(brlttyDisableInterrupt, int, (void));

FUNCTION_DECLARE(brlttyInterrupt, int, (WaitResult waitResult));
FUNCTION_DECLARE(brlttyWait, WaitResult, (int duration));

FUNCTION_DECLARE(changeLogLevel, int, (const char *operand));
FUNCTION_DECLARE(changeLogCategories, int, (const char *operand));

FUNCTION_DECLARE(changeTextTable, int, (const char *name));
FUNCTION_DECLARE(changeAttributesTable, int, (const char *name));
FUNCTION_DECLARE(changeContractionTable, int, (const char *name));
FUNCTION_DECLARE(changeKeyboardTable, int, (const char *name));

FUNCTION_DECLARE(restartBrailleDriver, void, (void));
FUNCTION_DECLARE(changeBrailleDriver, int, (const char *driver));
FUNCTION_DECLARE(changeBrailleParameters, int, (const char *parameters));
FUNCTION_DECLARE(changeBrailleDevice, int, (const char *device));

FUNCTION_DECLARE(restartSpeechDriver, void, (void));
FUNCTION_DECLARE(changeSpeechDriver, int, (const char *driver));
FUNCTION_DECLARE(changeSpeechParameters, int, (const char *parameters));

FUNCTION_DECLARE(restartScreenDriver, void, (void));
FUNCTION_DECLARE(changeScreenDriver, int, (const char *driver));
FUNCTION_DECLARE(changeScreenParameters, int, (const char *parameters));

FUNCTION_DECLARE(changeMessageLocale, int, (const char *parameters));
FUNCTION_DECLARE(showMessage, void, (const char *text));

#ifdef __cplusplus
}
#endif /* __cplusplus */

#endif /* BRLTTY_INCLUDED_EMBED */
