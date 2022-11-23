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

#ifndef BRLTTY_INCLUDED_MENU_PREFS
#define BRLTTY_INCLUDED_MENU_PREFS

#include "menu.h"

#ifdef __cplusplus
extern "C" {
#endif /* __cplusplus */

extern Menu *getPreferencesMenu (void);
extern int updateLogMessagesSubmenu (void);

#define PREFS_MENU_ITEM_APPLY(apply) \
apply(textTable) \
apply(attributesTable) \
apply(contractionTable) \
apply(keyboardTable) \
apply(languageProfile)

#define PREFS_MENU_ITEM_GETTER_PROTOTYPE(name) MenuItem *getPreferencesMenuItem_##name (void)
#define PREFS_MENU_ITEM_GETTER_DEFINE(name) extern PREFS_MENU_ITEM_GETTER_PROTOTYPE(name);
PREFS_MENU_ITEM_APPLY(PREFS_MENU_ITEM_GETTER_DEFINE)

#ifdef __cplusplus
}
#endif /* __cplusplus */

#endif /* BRLTTY_INCLUDED_MENU */
