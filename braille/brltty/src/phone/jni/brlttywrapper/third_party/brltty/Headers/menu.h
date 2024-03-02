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

#ifndef BRLTTY_INCLUDED_MENU
#define BRLTTY_INCLUDED_MENU

#ifdef __cplusplus
extern "C" {
#endif /* __cplusplus */

typedef struct MenuStruct Menu;
typedef struct MenuItemStruct MenuItem;

typedef struct {
  const char *label;
  const char *comment;
} MenuString;

extern Menu *newMenu (void);
extern void destroyMenu (Menu *menu);

extern MenuItem *newTextMenuItem (Menu *menu, const MenuString *name, const char *text);

typedef void NumericMenuItemFormatter (
  Menu *menu, unsigned char value,
  char *buffer, size_t size
);

extern MenuItem *newNumericMenuItem (
  Menu *menu, unsigned char *setting, const MenuString *name,
  unsigned char minimum, unsigned char maximum, unsigned char step,
  const char *unit, NumericMenuItemFormatter *formatter
);

extern MenuItem *newTimeMenuItem (
  Menu *menu, unsigned char *setting,
  const MenuString *name
);

extern MenuItem *newPercentMenuItem (
  Menu *menu, unsigned char *setting,
  const MenuString *name, unsigned char step
);

extern MenuItem *newStringsMenuItem (
  Menu *menu, unsigned char *setting, const MenuString *name,
  const MenuString *strings, unsigned char count
);

#define newEnumeratedMenuItem(menu, setting, name, strings) newStringsMenuItem(menu, setting, name, strings, ARRAY_COUNT(strings))
extern MenuItem *newBooleanMenuItem (Menu *menu, unsigned char *setting, const MenuString *name);

extern MenuItem *newFilesMenuItem (
  Menu *menu, const MenuString *name,
  const char *directory, const char *subdirectory, const char *extension,
  const char *initial, int none
);

typedef void MenuToolFunction (void);
extern MenuItem *newToolMenuItem (Menu *menu, const MenuString *name, MenuToolFunction *function);

extern Menu *newSubmenuMenuItem (
  Menu *menu, const MenuString *name
);

typedef int MenuItemTester (void);
extern void setMenuItemTester (MenuItem *item, MenuItemTester *handler);

typedef int MenuItemChanged (const MenuItem *item, unsigned char setting);
extern void setMenuItemChanged (MenuItem *item, MenuItemChanged *handler);

extern unsigned int getMenuNumber (const Menu *menu);
extern Menu *getMenuParent (const Menu *menu);
extern unsigned int getMenuSize (const Menu *menu);
extern unsigned int getMenuIndex (const Menu *menu);
extern MenuItem *getMenuItem (Menu *menu, unsigned int index);

extern int isMenuItemSettable (const MenuItem *item);
extern int isMenuItemAction (const MenuItem *item);
extern int isMenuItemVisible (const MenuItem *item);

extern Menu *getMenuItemMenu (const MenuItem *item);
extern unsigned int getMenuItemIndex (const MenuItem *item);
extern const char *getMenuItemTitle (const MenuItem *item);
extern const char *getMenuItemSubtitle (const MenuItem *item);
extern const char *getMenuItemValue (const MenuItem *item);
extern const char *getMenuItemText (const MenuItem *item);
extern const char *getMenuItemComment (const MenuItem *item);

extern void changeMenuItem (MenuItem *item);
extern int changeMenuItemPrevious (Menu *menu, int wrap);
extern int changeMenuItemNext (Menu *menu, int wrap);
extern int changeMenuItemFirst (Menu *menu);
extern int changeMenuItemLast (Menu *menu);
extern int changeMenuItemIndex (Menu *menu, unsigned int index);

extern int changeMenuSettingPrevious (Menu *menu, int wrap);
extern int changeMenuSettingNext (Menu *menu, int wrap);
extern int changeMenuSettingScaled (Menu *menu, unsigned int index, unsigned int count);

extern MenuItem *getCurrentMenuItem (Menu *menu);
extern Menu *getCurrentSubmenu (Menu *menu);

#ifdef __cplusplus
}
#endif /* __cplusplus */

#endif /* BRLTTY_INCLUDED_MENU */
