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

#ifndef BRLTTY_INCLUDED_SCR_TYPES
#define BRLTTY_INCLUDED_SCR_TYPES

#ifdef __cplusplus
extern "C" {
#endif /* __cplusplus */

typedef enum {
  SCR_ATTR_FG_BLUE   = 0X01,
  SCR_ATTR_FG_GREEN  = 0X02,
  SCR_ATTR_FG_RED    = 0X04,
  SCR_ATTR_FG_BRIGHT = 0X08,
  SCR_ATTR_BG_BLUE   = 0X10,
  SCR_ATTR_BG_GREEN  = 0X20,
  SCR_ATTR_BG_RED    = 0X40,
  SCR_ATTR_BLINK     = 0X80,

  SCR_MASK_FG = SCR_ATTR_FG_RED | SCR_ATTR_FG_GREEN | SCR_ATTR_FG_BLUE | SCR_ATTR_FG_BRIGHT,
  SCR_MASK_BG = SCR_ATTR_BG_RED | SCR_ATTR_BG_GREEN | SCR_ATTR_BG_BLUE,

  SCR_COLOUR_FG_BLACK          = 0,
  SCR_COLOUR_FG_BLUE           = SCR_ATTR_FG_BLUE,
  SCR_COLOUR_FG_GREEN          = SCR_ATTR_FG_GREEN,
  SCR_COLOUR_FG_CYAN           = SCR_ATTR_FG_GREEN | SCR_ATTR_FG_BLUE,
  SCR_COLOUR_FG_RED            = SCR_ATTR_FG_RED,
  SCR_COLOUR_FG_MAGENTA        = SCR_ATTR_FG_RED | SCR_ATTR_FG_BLUE,
  SCR_COLOUR_FG_BROWN          = SCR_ATTR_FG_RED | SCR_ATTR_FG_GREEN,
  SCR_COLOUR_FG_LIGHT_GREY     = SCR_ATTR_FG_RED | SCR_ATTR_FG_GREEN | SCR_ATTR_FG_BLUE,
  SCR_COLOUR_FG_DARK_GREY      = SCR_ATTR_FG_BRIGHT | SCR_COLOUR_FG_BLACK,
  SCR_COLOUR_FG_LIGHT_BLUE     = SCR_ATTR_FG_BRIGHT | SCR_COLOUR_FG_BLUE,
  SCR_COLOUR_FG_LIGHT_GREEN    = SCR_ATTR_FG_BRIGHT | SCR_COLOUR_FG_GREEN,
  SCR_COLOUR_FG_LIGHT_CYAN     = SCR_ATTR_FG_BRIGHT | SCR_COLOUR_FG_CYAN,
  SCR_COLOUR_FG_LIGHT_RED      = SCR_ATTR_FG_BRIGHT | SCR_COLOUR_FG_RED,
  SCR_COLOUR_FG_LIGHT_MAGENTA  = SCR_ATTR_FG_BRIGHT | SCR_COLOUR_FG_MAGENTA,
  SCR_COLOUR_FG_YELLOW         = SCR_ATTR_FG_BRIGHT | SCR_COLOUR_FG_BROWN,
  SCR_COLOUR_FG_WHITE          = SCR_ATTR_FG_BRIGHT | SCR_COLOUR_FG_LIGHT_GREY,

  SCR_COLOUR_BG_BLACK      = 0,
  SCR_COLOUR_BG_BLUE       = SCR_ATTR_BG_BLUE,
  SCR_COLOUR_BG_GREEN      = SCR_ATTR_BG_GREEN,
  SCR_COLOUR_BG_CYAN       = SCR_ATTR_BG_GREEN | SCR_ATTR_BG_BLUE,
  SCR_COLOUR_BG_RED        = SCR_ATTR_BG_RED,
  SCR_COLOUR_BG_MAGENTA    = SCR_ATTR_BG_RED | SCR_ATTR_BG_BLUE,
  SCR_COLOUR_BG_BROWN      = SCR_ATTR_BG_RED | SCR_ATTR_BG_GREEN,
  SCR_COLOUR_BG_LIGHT_GREY = SCR_ATTR_BG_RED | SCR_ATTR_BG_GREEN | SCR_ATTR_BG_BLUE,

  SCR_COLOUR_DEFAULT = SCR_COLOUR_FG_LIGHT_GREY | SCR_COLOUR_BG_BLACK
} ScreenAttributes;

typedef struct {
  wchar_t text;
  ScreenAttributes attributes;
} ScreenCharacter;

typedef struct {
  short rows, cols;	/* screen dimensions */
  short posx, posy;	/* cursor position */
  int number;		      /* screen number */
  unsigned cursor:1;
  const char *unreadable;
} ScreenDescription;

typedef struct {
  short left, top;	/* top-left corner (offset from 0) */
  short width, height;	/* dimensions */
} ScreenBox;

#define SCR_KEY_SHIFT     0X40000000
#define SCR_KEY_UPPER     0X20000000
#define SCR_KEY_CONTROL   0X10000000
#define SCR_KEY_ALT_LEFT  0X08000000
#define SCR_KEY_ALT_RIGHT 0X04000000
#define SCR_KEY_GUI       0X02000000
#define SCR_KEY_CHAR_MASK 0X00FFFFFF

#define SCR_KEY_UNICODE_ROW 0XF800

typedef enum {
  SCR_KEY_ENTER = SCR_KEY_UNICODE_ROW,
  SCR_KEY_TAB,
  SCR_KEY_BACKSPACE,
  SCR_KEY_ESCAPE,
  SCR_KEY_CURSOR_LEFT,
  SCR_KEY_CURSOR_RIGHT,
  SCR_KEY_CURSOR_UP,
  SCR_KEY_CURSOR_DOWN,
  SCR_KEY_PAGE_UP,
  SCR_KEY_PAGE_DOWN,
  SCR_KEY_HOME,
  SCR_KEY_END,
  SCR_KEY_INSERT,
  SCR_KEY_DELETE,
  SCR_KEY_FUNCTION,

  SCR_KEY_F1 = SCR_KEY_FUNCTION,
  SCR_KEY_F2,
  SCR_KEY_F3,
  SCR_KEY_F4,
  SCR_KEY_F5,
  SCR_KEY_F6,
  SCR_KEY_F7,
  SCR_KEY_F8,
  SCR_KEY_F9,
  SCR_KEY_F10,
  SCR_KEY_F11,
  SCR_KEY_F12,
  SCR_KEY_F13,
  SCR_KEY_F14,
  SCR_KEY_F15,
  SCR_KEY_F16,
  SCR_KEY_F17,
  SCR_KEY_F18,
  SCR_KEY_F19,
  SCR_KEY_F20,
  SCR_KEY_F21,
  SCR_KEY_F22,
  SCR_KEY_F23,
  SCR_KEY_F24,
} ScreenKey;

/* must be less than 0 */
#define SCR_NO_VT -1

typedef struct ScreenDriverStruct ScreenDriver;

#ifdef __cplusplus
}
#endif /* __cplusplus */

#endif /* BRLTTY_INCLUDED_SCR_TYPES */
