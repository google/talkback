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

#ifndef BRLTTY_INCLUDED_CMD
#define BRLTTY_INCLUDED_CMD

#include "strfmth.h"
#include "cmd_types.h"

#ifdef __cplusplus
extern "C" {
#endif /* __cplusplus */

extern const CommandEntry commandTable[];
extern int getCommandCount (void);
extern const CommandEntry *findCommandEntry (int code);

typedef struct {
  const char *name;
  int bit;
} CommandModifierEntry;

extern const CommandModifierEntry commandModifierTable_toggle[];
extern const CommandModifierEntry commandModifierTable_motion[];
extern const CommandModifierEntry commandModifierTable_row[];
extern const CommandModifierEntry commandModifierTable_vertical[];
extern const CommandModifierEntry commandModifierTable_input[];
extern const CommandModifierEntry commandModifierTable_character[];
extern const CommandModifierEntry commandModifierTable_braille[];
extern const CommandModifierEntry commandModifierTable_keyboard[];

typedef enum {
  CDO_IncludeName    = 0X1,
  CDO_IncludeOperand = 0X2,
  CDO_DefaultOperand = 0X4
} CommandDescriptionOption;

extern STR_DECLARE_FORMATTER(describeCommand, int command, CommandDescriptionOption options);
extern void logCommand (int command);
extern void logTransformedCommand (int oldCommand, int newCommand);

#ifdef __cplusplus
}
#endif /* __cplusplus */

#endif /* BRLTTY_INCLUDED_CMD */
