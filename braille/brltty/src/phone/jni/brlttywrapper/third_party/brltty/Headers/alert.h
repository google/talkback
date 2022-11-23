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

#ifndef BRLTTY_INCLUDED_ALERT
#define BRLTTY_INCLUDED_ALERT

#ifdef __cplusplus
extern "C" {
#endif /* __cplusplus */

typedef enum {
  ALERT_NONE,

  ALERT_BRAILLE_ON,
  ALERT_BRAILLE_OFF,

  ALERT_COMMAND_DONE,
  ALERT_COMMAND_REJECTED,

  ALERT_MARK_SET,

  ALERT_CLIPBOARD_BEGIN,
  ALERT_CLIPBOARD_END,

  ALERT_NO_CHANGE,
  ALERT_TOGGLE_ON,
  ALERT_TOGGLE_OFF,

  ALERT_CURSOR_LINKED,
  ALERT_CURSOR_UNLINKED,

  ALERT_SCREEN_FROZEN,
  ALERT_SCREEN_UNFROZEN,
  ALERT_FREEZE_REMINDER,

  ALERT_WRAP_DOWN,
  ALERT_WRAP_UP,

  ALERT_SKIP_FIRST,
  ALERT_SKIP_ONE,
  ALERT_SKIP_SEVERAL,
  ALERT_BOUNCE,

  ALERT_ROUTING_STARTED,
  ALERT_ROUTING_SUCCEEDED,
  ALERT_ROUTING_FAILED,

  ALERT_MODIFIER_NEXT,
  ALERT_MODIFIER_ON,
  ALERT_MODIFIER_OFF,

  ALERT_CONSOLE_BELL,
  ALERT_KEYS_AUTORELEASED,

  ALERT_SCROLL_UP,
} AlertIdentifier;

extern void alert (AlertIdentifier identifier);

extern int showDotPattern (unsigned char dots, unsigned char duration);

#ifdef __cplusplus
}
#endif /* __cplusplus */

#endif /* BRLTTY_INCLUDED_ALERT */
