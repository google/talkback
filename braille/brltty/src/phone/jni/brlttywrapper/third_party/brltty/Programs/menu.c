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

#include "prologue.h"

#include <stdio.h>
#include <stdarg.h>
#include <string.h>
#include <errno.h>
#include <fcntl.h>
#include <sys/stat.h>

#ifdef HAVE_LANGINFO_H
#include <langinfo.h>
#endif /* HAVE_LANGINFO_H */

#undef CAN_GLOB
#if defined(HAVE_GLOB)
#define CAN_GLOB
#include <glob.h>

#elif defined(__MINGW32__)
#define CAN_GLOB
#include <io.h>

#else /* glob: paradigm-specific global definitions */
#warning file globbing support not available on this platform
#endif /* glob: paradigm-specific global definitions */

#include "log.h"
#include "menu.h"
#include "strfmt.h"
#include "prefs.h"
#include "timing.h"
#include "parse.h"
#include "file.h"

typedef struct {
  char *directory;
  const char *extension;
  const char *pattern;
  char *initial;
  char *current;
  unsigned none:1;

#if defined(HAVE_GLOB)
  glob_t glob;
#elif defined(__MINGW32__)
  char **names;
  int offset;
#endif /* glob: paradigm-specific field declarations */

  char **paths;
  int count;
  unsigned char setting;
  char *pathsArea[3];
} FileData;

typedef struct {
  Menu *menu;
  unsigned opened:1;

  unsigned int total;
  unsigned int visible;
} SubmenuData;

struct MenuStruct {
  Menu *parent;

  struct {
    MenuItem *array;
    unsigned int size;
    unsigned int count;
    unsigned int index;
  } items;

  unsigned int menuNumber;
  unsigned int submenuCount;
  MenuItem *activeItem;

  char valueBuffer[0X20];
};

typedef struct {
  int (*testItem) (const MenuItem *item);
  int (*beginItem) (MenuItem *item);
  void (*endItem) (MenuItem *item, int deallocating);
  void (*activateItem) (MenuItem *item);
  const char * (*getValue) (const MenuItem *item);
  const char * (*getText) (const MenuItem *item);
  const char * (*getComment) (const MenuItem *item);
} MenuItemMethods;

struct MenuItemStruct {
  Menu *menu;
  unsigned char *setting;                 /* pointer to current value */

  const char *title;                      /* item name for presentation */
  const char *subtitle;                      /* item name for presentation */

  const MenuItemMethods *methods;
  MenuItemTester *test;                     /* returns true if item should be presented */
  MenuItemChanged *changed;

  unsigned char minimum;                  /* lowest valid value */
  unsigned char maximum;                  /* highest valid value */
  unsigned char step;                  /* present only multiples of this value */

  union {
    const char *text;
    const MenuString *strings;
    FileData *files;
    SubmenuData *submenu;

    struct {
      const char *unit;
      NumericMenuItemFormatter *formatter;
    } numeric;

    struct {
      MenuToolFunction *function;
    } tool;
  } data;
};

static inline const char *
getLocalizedText (const char *string) {
  return (string && *string)? gettext(string): "";
}

static const char *
formatValue (Menu *menu, const char *format, ...) {
  {
    va_list arguments;

    va_start(arguments, format);
    vsnprintf(menu->valueBuffer, sizeof(menu->valueBuffer), format, arguments);
    va_end(arguments);
  }

  return menu->valueBuffer;
}

Menu *
newMenu (void) {
  Menu *menu;

  if ((menu = malloc(sizeof(*menu)))) {
    memset(menu, 0, sizeof(*menu));
    menu->parent = NULL;

    menu->items.array = NULL;
    menu->items.size = 0;
    menu->items.count = 0;
    menu->items.index = 0;

    menu->menuNumber = 0;
    menu->submenuCount = 0;
    menu->activeItem = NULL;

    return menu;
  } else {
    logMallocError();
  }

  return NULL;
}

static int
beginMenuItem (MenuItem *item) {
  return !item->methods->beginItem || item->methods->beginItem(item);
}

static void
endMenuItem (MenuItem *item, int deallocating) {
  if (item->methods->endItem) item->methods->endItem(item, deallocating);
}

void
destroyMenu (Menu *menu) {
  if (menu) {
    if (menu->items.array) {
      MenuItem *item = menu->items.array;
      const MenuItem *end = item + menu->items.count;

      while (item < end) endMenuItem(item++, 1);
      free(menu->items.array);
    }

    free(menu);
  }
}

unsigned int
getMenuNumber (const Menu *menu) {
  return menu->menuNumber;
}

Menu *
getMenuParent (const Menu *menu) {
  return menu->parent;
}

unsigned int
getMenuSize (const Menu *menu) {
  return menu->items.count;
}

unsigned int
getMenuIndex (const Menu *menu) {
  return menu->items.index;
}

MenuItem *
getMenuItem (Menu *menu, unsigned int index) {
  return (index < menu->items.count)? &menu->items.array[index]: NULL;
}

static MenuItem *
getSelectedMenuItem (Menu *menu) {
  return getMenuItem(menu, menu->items.index);
}

static int
testMenuItem (const MenuItem *item, int all) {
  if (!item) return 0;
  if (all) return 1;
  if (item->methods->testItem && !item->methods->testItem(item)) return 0;
  return !item->test || item->test();
}

int
isMenuItemSettable (const MenuItem *item) {
  return !!item->setting;
}

int
isMenuItemAction (const MenuItem *item) {
  return !!item->methods->activateItem;
}

int
isMenuItemVisible (const MenuItem *item) {
  return testMenuItem(item, 0);
}

static inline int
testMenuItemActive (Menu *menu, unsigned int index) {
  return testMenuItem(getMenuItem(menu, index), 0);
}

static inline int
testMenuItemVisible (Menu *menu, unsigned int index) {
  return testMenuItem(getMenuItem(menu, index), prefs.showAllItems);
}

Menu *
getMenuItemMenu (const MenuItem *item) {
  return item->menu;
}

unsigned int
getMenuItemIndex (const MenuItem *item) {
  return item - item->menu->items.array;
}

const char *
getMenuItemTitle (const MenuItem *item) {
  return getLocalizedText(item->title);
}

const char *
getMenuItemSubtitle (const MenuItem *item) {
  return getLocalizedText(item->subtitle);
}

const char *
getMenuItemValue (const MenuItem *item) {
  return item->methods->getValue? item->methods->getValue(item): "";
}

const char *
getMenuItemText (const MenuItem *item) {
  return item->methods->getText? item->methods->getText(item): getMenuItemValue(item);
}

const char *
getMenuItemComment (const MenuItem *item) {
  return item->methods->getComment? item->methods->getComment(item): "";
}

static MenuItem *
newMenuItem (Menu *menu, unsigned char *setting, const MenuString *name) {
  if (menu->items.count == menu->items.size) {
    unsigned int newSize = menu->items.size? (menu->items.size << 1): 0X10;
    MenuItem *newArray = realloc(menu->items.array, (newSize * sizeof(*newArray)));

    if (!newArray) {
      logMallocError();
      return NULL;
    }

    menu->items.array = newArray;
    menu->items.size = newSize;
  }

  {
    MenuItem *item = getMenuItem(menu, menu->items.count++);

    item->menu = menu;
    item->setting = setting;

    if (name) {
      item->title = name->label;
      item->subtitle = name->comment;
    } else {
      item->title = NULL;
      item->subtitle = NULL;
    }

    item->methods = NULL;
    item->test = NULL;
    item->changed = NULL;

    item->minimum = 0;
    item->maximum = 0;
    item->step = 1;

    return item;
  }
}

void
setMenuItemTester (MenuItem *item, MenuItemTester *handler) {
  item->test = handler;
}

void
setMenuItemChanged (MenuItem *item, MenuItemChanged *handler) {
  item->changed = handler;
}

static const char *
getValue_text (const MenuItem *item) {
  return item->data.text;
}

static const MenuItemMethods menuItemMethods_text = {
  .getValue = getValue_text
};

MenuItem *
newTextMenuItem (Menu *menu, const MenuString *name, const char *text) {
  MenuItem *item = newMenuItem(menu, NULL, name);

  if (item) {
    item->methods = &menuItemMethods_text;
    item->data.text = text;
  }

  return item;
}

static const char *
getValue_numeric (const MenuItem *item) {
  Menu *menu = item->menu;

  item->data.numeric.formatter(
    menu, *item->setting,
    menu->valueBuffer, sizeof(menu->valueBuffer)
  );

  return menu->valueBuffer;
}

static const char *
getComment_numeric (const MenuItem *item) {
  return getLocalizedText(item->data.numeric.unit);
}

static const MenuItemMethods menuItemMethods_numeric = {
  .getValue = getValue_numeric,
  .getComment = getComment_numeric
};

static void
defaultNumericMenuItemFormatter (
  Menu *menu, unsigned char value,
  char *buffer, size_t size
) {
  snprintf(buffer, size, "%u", value);
}

MenuItem *
newNumericMenuItem (
  Menu *menu, unsigned char *setting, const MenuString *name,
  unsigned char minimum, unsigned char maximum, unsigned char step,
  const char *unit, NumericMenuItemFormatter *formatter
) {
  if (!formatter) formatter = defaultNumericMenuItemFormatter;

  MenuItem *item = newMenuItem(menu, setting, name);

  if (item) {
    item->methods = &menuItemMethods_numeric;
    item->minimum = minimum;
    item->maximum = maximum;
    item->step = step;
    item->data.numeric.unit = unit;
    item->data.numeric.formatter = formatter;
  }

  return item;
}

static void
formatTime (Menu *menu, unsigned char time, char *buffer, size_t size) {
  unsigned int milliseconds = PREFS2MSECS(time);

  unsigned int seconds = milliseconds / MSECS_PER_SEC;
  milliseconds %= MSECS_PER_SEC;

  const char *decimalPoint;
#ifdef HAVE_NL_LANGINFO
  decimalPoint = nl_langinfo(RADIXCHAR);
#else /* HAVE_NL_LANGINFO */
  decimalPoint = NULL;
#endif /* HAVE_NL_LANGINFO */
  if (!decimalPoint) decimalPoint = ".";

  size_t end;
  size_t decimalFrom;
  size_t decimalTo;

  STR_BEGIN(buffer, size);
  STR_PRINTF("%u", seconds);
  decimalFrom = STR_LENGTH;
  STR_PRINTF("%s", decimalPoint);
  decimalTo = STR_LENGTH;
  STR_PRINTF("%03u", milliseconds);
  end = STR_LENGTH;
  STR_END;

  while (buffer[--end] == '0');
  if (++end == decimalTo) end = decimalFrom;
  buffer[end] = 0;
}

MenuItem *
newTimeMenuItem (
  Menu *menu, unsigned char *setting,
  const MenuString *name
) {
  return newNumericMenuItem(menu, setting, name, 10, 250, 10, strtext("seconds"), formatTime);
}

MenuItem *
newPercentMenuItem (
  Menu *menu, unsigned char *setting,
  const MenuString *name, unsigned char step
) {
  return newNumericMenuItem(menu, setting, name, 0, 100, step, "%", NULL);
}

static const char *
getValue_strings (const MenuItem *item) {
  const MenuString *strings = item->data.strings;
  return getLocalizedText(strings[*item->setting - item->minimum].label);
}

static const char *
getComment_strings (const MenuItem *item) {
  const MenuString *strings = item->data.strings;
  return getLocalizedText(strings[*item->setting - item->minimum].comment);
}

static const MenuItemMethods menuItemMethods_strings = {
  .getValue = getValue_strings,
  .getComment = getComment_strings
};

static void
setMenuItemStrings (MenuItem *item, const MenuString *strings, unsigned char count) {
  item->methods = &menuItemMethods_strings;
  item->data.strings = strings;
  item->minimum = 0;
  item->maximum = count - 1;
  item->step = 1;
}

MenuItem *
newStringsMenuItem (
  Menu *menu, unsigned char *setting, const MenuString *name,
  const MenuString *strings, unsigned char count
) {
  MenuItem *item = newMenuItem(menu, setting, name);

  if (item) {
    setMenuItemStrings(item, strings, count);
  }

  return item;
}

MenuItem *
newBooleanMenuItem (Menu *menu, unsigned char *setting, const MenuString *name) {
  static const MenuString strings[] = {
    {.label=strtext("No")},
    {.label=strtext("Yes")}
  };

  return newEnumeratedMenuItem(menu, setting, name, strings);
}

static int
qsortCompare_fileNames (const void *element1, const void *element2) {
  const char *const *name1 = element1;
  const char *const *name2 = element2;
  return strcmp(*name1, *name2);
}

static int
beginItem_files (MenuItem *item) {
  FileData *files = item->data.files;
  int index;

  files->paths = files->pathsArea;
  files->count = ARRAY_COUNT(files->pathsArea) - 1;
  files->paths[files->count] = NULL;
  index = files->count;

#ifdef CAN_GLOB
  {
#ifdef HAVE_FCHDIR
    int originalDirectory = open(".", O_RDONLY);

    if (originalDirectory != -1)
#else /* HAVE_FCHDIR */
    char *originalDirectory = getWorkingDirectory();

    if (originalDirectory)
#endif /* HAVE_FCHDIR */
    {
      if (chdir(files->directory) != -1) {
#if defined(HAVE_GLOB)
        memset(&files->glob, 0, sizeof(files->glob));
        files->glob.gl_offs = files->count;

        if (glob(files->pattern, GLOB_DOOFFS, NULL, &files->glob) == 0) {
          files->paths = files->glob.gl_pathv;

          /* The behaviour of gl_pathc is inconsistent. Some implementations
           * include the leading NULL pointers and some don't. Let's just
           * figure it out the hard way by finding the trailing NULL.
           */
          while (files->paths[files->count]) files->count += 1;
        }
#elif defined(__MINGW32__)
        struct _finddata_t findData;
        long findHandle = _findfirst(files->pattern, &findData);
        int allocated = files->count | 0XF;

        files->offset = files->count;
        files->names = malloc(allocated * sizeof(*files->names));

        if (findHandle != -1) {
          do {
            if (files->count >= allocated) {
              allocated = allocated * 2;
              files->names = realloc(files->names, allocated * sizeof(*files->names));
            }

            files->names[files->count++] = strdup(findData.name);
          } while (_findnext(findHandle, &findData) == 0);

          _findclose(findHandle);
        }

        files->names = realloc(files->names, files->count * sizeof(*files->names));
        files->paths = files->names;
#endif /* glob: paradigm-specific field initialization */

#ifdef HAVE_FCHDIR
        if (fchdir(originalDirectory) == -1) logSystemError("fchdir");
#else /* HAVE_FCHDIR */
        if (chdir(originalDirectory) == -1) logSystemError("chdir");
#endif /* HAVE_FCHDIR */
      } else {
        logMessage(LOG_ERR, "%s: %s: %s",
                   gettext("cannot set working directory"), files->directory, strerror(errno));
      }

#ifdef HAVE_FCHDIR
      close(originalDirectory);
#else /* HAVE_FCHDIR */
      free(originalDirectory);
#endif /* HAVE_FCHDIR */
    } else {
#ifdef HAVE_FCHDIR
      logMessage(LOG_ERR, "%s: %s",
                 gettext("cannot open working directory"), strerror(errno));
#else /* HAVE_FCHDIR */
      logMessage(LOG_ERR, "%s", gettext("cannot determine working directory"));
#endif /* HAVE_FCHDIR */
    }
  }
#endif /* CAN_GLOB */

  qsort(&files->paths[index], files->count-index, sizeof(*files->paths), qsortCompare_fileNames);
  if (files->none) files->paths[--index] = "";
  files->paths[--index] = files->initial;
  files->paths += index;
  files->count -= index;
  files->setting = 0;

  for (index=1; index<files->count; index+=1) {
    if (strcmp(files->paths[index], files->initial) == 0) {
      files->paths += 1;
      files->count -= 1;
      break;
    }
  }

  for (index=0; index<files->count; index+=1) {
    if (strcmp(files->paths[index], files->current) == 0) {
      files->setting = index;
      break;
    }
  }

  item->maximum = files->count - 1;
  return 1;
}

static void
endItem_files (MenuItem *item, int deallocating) {
  FileData *files = item->data.files;

  if (files->current) free(files->current);
  files->current = deallocating? NULL: strdup(files->paths[files->setting]);

#if defined(HAVE_GLOB)
  if (files->glob.gl_pathc) {
    for (int i=0; i<files->glob.gl_offs; i+=1) files->glob.gl_pathv[i] = NULL;
    globfree(&files->glob);
    files->glob.gl_pathc = 0;
  }
#elif defined(__MINGW32__)
  if (files->names) {
    for (int i=files->offset; i<files->count; i+=1) free(files->names[i]);
    free(files->names);
    files->names = NULL;
  }
#endif /* glob: paradigm-specific memory deallocation */
}

static const char *
getValue_files (const MenuItem *item) {
  const FileData *files = item->data.files;
  const char *path;

  if (item == item->menu->activeItem) {
    path = files->paths[files->setting];
  } else {
    path = files->current;
  }

  if (!path) path = "";
  const char *name = locatePathName(path);

  if (name == path) {
    const char *extension = files->extension;

    if (hasFileExtension(name, extension)) {
      int length = strlen(path) - strlen(extension);
      Menu *menu = item->menu;

      snprintf(
        menu->valueBuffer, sizeof(menu->valueBuffer),
        "%.*s", length, name
      );

      path = menu->valueBuffer;
    }
  }

  return path;
}

static const char *
getText_files (const MenuItem *item) {
  return item->methods->getValue(item);
}

static const MenuItemMethods menuItemMethods_files = {
  .beginItem = beginItem_files,
  .endItem = endItem_files,
  .getValue = getValue_files,
  .getText = getText_files
};

MenuItem *
newFilesMenuItem (
  Menu *menu, const MenuString *name,
  const char *directory, const char *subdirectory, const char *extension,
  const char *initial, int none
) {
  FileData *files;

  if ((files = malloc(sizeof(*files)))) {
    memset(files, 0, sizeof(*files));
    files->extension = extension;
    files->none = !!none;

    char *pattern;
    {
      const char *strings[] = {"*", extension};
      pattern = joinStrings(strings, ARRAY_COUNT(strings));
    }

    if (pattern) {
      files->pattern = pattern; 

      if ((files->initial = *initial? ensureFileExtension(initial, extension): strdup(""))) {
        if ((files->current = strdup(files->initial))) {
          if (subdirectory) {
            files->directory = makePath(directory, subdirectory);
          } else if (!(files->directory = strdup(directory))) {
            logMallocError();
          }

          if (files->directory) {
            MenuItem *item = newMenuItem(menu, &files->setting, name);

            if (item) {
              item->methods = &menuItemMethods_files;
              item->data.files = files;
              return item;
            }

            free(files->directory);
          }

          free(files->current);
        } else {
          logMallocError();
        }

        free(files->initial);
      } else {
        logMallocError();
      }

      free(pattern);
    } else {
      logMallocError();
    }

    free(files);
  } else {
    logMallocError();
  }

  return NULL;
}

static void
activateItem_tool (MenuItem *item) {
  item->data.tool.function();
}

static const char *
getValue_tool (const MenuItem *item) {
  return NULL;
}

static const MenuItemMethods menuItemMethods_tool = {
  .activateItem = activateItem_tool,
  .getValue = getValue_tool
};

MenuItem *
newToolMenuItem (Menu *menu, const MenuString *name, MenuToolFunction *function) {
  MenuItem *item = newMenuItem(menu, NULL, name);

  if (item) {
    item->methods = &menuItemMethods_tool;
    item->data.tool.function = function;
  }

  return item;
}

static MenuItem *
getParentMenuItem (const MenuItem *item) {
  return getSelectedMenuItem(item->menu->parent);
}

static int
testItem_submenu (const MenuItem *item) {
  return getMenuSize(item->data.submenu->menu) > 1;
}

static int
beginItem_submenu (MenuItem *item) {
  item->data.submenu->visible = 0;

  {
    Menu *menu = item->data.submenu->menu;
    unsigned int size = getMenuSize(menu);

    for (unsigned int index=1; index<size; index+=1) {
      if (testMenuItemActive(menu, index)) {
        item->data.submenu->visible += 1;
      }
    }

    item->data.submenu->total = size - 1;
  }

  return 1;
}

static void
endItem_submenu (MenuItem *item, int deallocating) {
  if (deallocating) {
    SubmenuData *submenu = item->data.submenu;

    destroyMenu(submenu->menu);
    free(submenu);
  }
}

static void
activateItem_submenu (MenuItem *item) {
  endMenuItem(item, 0);
  item->data.submenu->opened = 1;
}

static const char *
getValue_submenu (const MenuItem *item) {
  return "--->";
}

static const char *
getComment_submenu (const MenuItem *item) {
  if (!prefs.showSubmenuSizes) return "";

  {
    const SubmenuData *submenu = item->data.submenu;

    return prefs.showAllItems?
             formatValue(item->menu, "%u", submenu->total):
             formatValue(item->menu, "%u/%u", submenu->visible, submenu->total);
  }
}

static const MenuItemMethods menuItemMethods_submenu = {
  .testItem = testItem_submenu,
  .beginItem = beginItem_submenu,
  .endItem = endItem_submenu,
  .activateItem = activateItem_submenu,
  .getValue = getValue_submenu,
  .getComment = getComment_submenu
};

static void
activateItem_close (MenuItem *item) {
  item = getParentMenuItem(item);
  item->data.submenu->opened = 0;
  beginMenuItem(item);
}

static const char *
getValue_close (const MenuItem *item) {
  return getLocalizedText(strtext("Close"));
}

static const char *
getComment_close (const MenuItem *item) {
  return getMenuItemTitle(getParentMenuItem(item));
}

static const MenuItemMethods menuItemMethods_close = {
  .activateItem = activateItem_close,
  .getValue = getValue_close,
  .getComment = getComment_close
};

Menu *
newSubmenuMenuItem (
  Menu *menu, const MenuString *name
) {
  SubmenuData *submenu;

  if ((submenu = malloc(sizeof(*submenu)))) {
    memset(submenu, 0, sizeof(*submenu));

    if ((submenu->menu = newMenu())) {
      static const MenuString closeName = {.label="<---"};
      MenuItem *close;

      if ((close = newMenuItem(submenu->menu, NULL, &closeName))) {
        MenuItem *item;

        if ((item = newMenuItem(menu, NULL, name))) {
          submenu->menu->parent = menu;
          submenu->opened = 0;
          close->methods = &menuItemMethods_close;

          item->methods = &menuItemMethods_submenu;
          item->data.submenu = submenu;

          while (1) {
            menu->submenuCount += 1;
            if (!menu->parent) break;
            menu = menu->parent;
          }

          submenu->menu->menuNumber = menu->submenuCount;
          return submenu->menu;
        }
      }

      destroyMenu(submenu->menu);
    }

    free(submenu);
  } else {
    logMallocError();
  }

  return NULL;
}

void
changeMenuItem (MenuItem *item) {
  Menu *menu = item->menu;

  menu->items.index = getMenuItemIndex(item);
}

int
changeMenuItemPrevious (Menu *menu, int wrap) {
  unsigned int index = menu->items.index;
  if (index >= menu->items.count) return 0;

  do {
    if (!menu->items.index) {
      if (!wrap) {
        menu->items.index = index;
        return 0;
      }

      menu->items.index = menu->items.count;
    }

    if (--menu->items.index == index) return 0;
  } while (!testMenuItemVisible(menu, menu->items.index));

  return 1;
}

int
changeMenuItemNext (Menu *menu, int wrap) {
  unsigned int index = menu->items.index;
  if (index >= menu->items.count) return 0;

  do {
    if (++menu->items.index == menu->items.count) {
      if (!wrap) {
        menu->items.index = index;
        return 0;
      }

      menu->items.index = 0;
    }

    if (menu->items.index == index) return 0;
  } while (!testMenuItemVisible(menu, menu->items.index));

  return 1;
}

int
changeMenuItemFirst (Menu *menu) {
  if (!menu->items.count) return 0;
  menu->items.index = 0;
  return testMenuItemVisible(menu, menu->items.index) || changeMenuItemNext(menu, 0);
}

int
changeMenuItemLast (Menu *menu) {
  if (!menu->items.count) return 0;
  menu->items.index = menu->items.count - 1;
  return testMenuItemVisible(menu, menu->items.index) || changeMenuItemPrevious(menu, 0);
}

int
changeMenuItemIndex (Menu *menu, unsigned int index) {
  if (index >= menu->items.count) return 0;
  menu->items.index = index;
  return 1;
}

static int
activateMenuItem (MenuItem *item) {
  if (!item->methods->activateItem) return 0;
  item->methods->activateItem(item);
  return 1;
}

static int
adjustMenuSetting (const MenuItem *item, int (*adjust) (const MenuItem *item, int wrap), int wrap) {
  unsigned char setting = *item->setting;
  int count = item->maximum - item->minimum + 1;

  do {
    int ok = 0;

    if (--count) {
      if (adjust(item, wrap)) {
        ok = 1;
      }
    }

    if (!ok) {
      *item->setting = setting;
      return 0;
    }
  } while ((*item->setting % item->step) || (item->changed && !item->changed(item, *item->setting)));

  return 1;
}

static int
decrementMenuSetting (const MenuItem *item, int wrap) {
  if ((*item->setting)-- <= item->minimum) {
    if (!wrap) return 0;
    *item->setting = item->maximum;
  }

  return 1;
}

int
changeMenuSettingPrevious (Menu *menu, int wrap) {
  MenuItem *item = getCurrentMenuItem(menu);

  if (activateMenuItem(item)) return 1;
  if (!item->setting) return 0;
  return adjustMenuSetting(item, decrementMenuSetting, wrap);
}

static int
incrementMenuSetting (const MenuItem *item, int wrap) {
  if ((*item->setting)++ >= item->maximum) {
    if (!wrap) return 0;
    *item->setting = item->minimum;
  }

  return 1;
}

int
changeMenuSettingNext (Menu *menu, int wrap) {
  MenuItem *item = getCurrentMenuItem(menu);

  if (activateMenuItem(item)) return 1;
  if (!item->setting) return 0;
  return adjustMenuSetting(item, incrementMenuSetting, wrap);
}

int
changeMenuSettingScaled (Menu *menu, unsigned int index, unsigned int count) {
  MenuItem *item = getCurrentMenuItem(menu);

  if (activateMenuItem(item)) return 1;

  if (item->setting) {
    unsigned char oldSetting = *item->setting;

    if (item->methods->getValue == getValue_numeric) {
      *item->setting = rescaleInteger(index, count-1, item->maximum-item->minimum) + item->minimum;
    } else {
      *item->setting = index % (item->maximum + 1);
    }

    if (!item->changed || item->changed(item, *item->setting)) return 1;
    *item->setting = oldSetting;
  }

  return 0;
}

MenuItem *
getCurrentMenuItem (Menu *menu) {
  MenuItem *newItem = getSelectedMenuItem(menu);
  MenuItem *oldItem = menu->activeItem;

  if (newItem != oldItem) {
    if (oldItem) endMenuItem(oldItem, 0);
    menu->activeItem = beginMenuItem(newItem)? newItem: NULL;
  }

  return newItem;
}

Menu *
getCurrentSubmenu (Menu *menu) {
  while (1) {
    MenuItem *item = getCurrentMenuItem(menu);

    if (item->methods != &menuItemMethods_submenu) break;
    if (!item->data.submenu->opened) break;
    menu = item->data.submenu->menu;
  }

  return menu;
}
