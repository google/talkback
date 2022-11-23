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

#ifndef BRLTTY_INCLUDED_KTB_CMDS
#define BRLTTY_INCLUDED_KTB_CMDS

#ifdef __cplusplus
extern "C" {
#endif /* __cplusplus */

typedef struct CommandGroupHookDataStruct CommandGroupHookData;
typedef void CommandGroupHook (CommandGroupHookData *data);
extern CommandGroupHook commandGroupHook_keyboardFunctions;
extern CommandGroupHook commandGroupHook_hotkeys;

typedef struct {
  int code;
} CommandListEntry;

typedef struct {
  struct {
    const CommandListEntry *table;
    unsigned int count;
  } commands;

  const char *name;
  CommandGroupHook *before;
  CommandGroupHook *after;
} CommandGroupEntry;

extern const CommandGroupEntry commandGroupTable[];
extern const unsigned char commandGroupCount;

#ifdef __cplusplus
}
#endif /* __cplusplus */

#endif /* BRLTTY_INCLUDED_KTB_CMDS */
