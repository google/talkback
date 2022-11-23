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

#include "prologue.h"

#include <stdio.h>
#include <string.h>
#include <locale.h>
#include <signal.h>

#include "log.h"
#include "parse.h"
#include "charset.h"
#include "unicode.h"

#if defined(WINDOWS)
#define USE_WINDOWS
#elif defined(HAVE_PKG_XAW)
#define USE_XAW
#define USE_XT
#include <X11/Intrinsic.h>
#include <X11/Xaw/Form.h>
#include <X11/Xaw/Paned.h>
#include <X11/Xaw/Label.h>
#include <X11/Xaw/Command.h>
#include <X11/Xaw/Repeater.h>
#include <X11/Xaw/SimpleMenu.h>
#include <X11/Xaw/SmeLine.h>
#include <X11/Xaw/SmeBSB.h>
#elif defined(HAVE_PKG_XAW3D)
#define USE_XAW
#define USE_XT
#define XAW_INTERNATIONALIZATION
#include <X11/Intrinsic.h>
#include <X11/Xaw3d/Form.h>
#include <X11/Xaw3d/Paned.h>
#include <X11/Xaw3d/Label.h>
#include <X11/Xaw3d/Command.h>
#include <X11/Xaw3d/Repeater.h>
#include <X11/Xaw3d/SimpleMenu.h>
#include <X11/Xaw3d/SmeLine.h>
#include <X11/Xaw3d/SmeBSB.h>
#elif defined(HAVE_PKG_NEXTAW)
#define USE_XAW
#define USE_XT
#include <X11/Intrinsic.h>
#include <X11/neXtaw/Form.h>
#include <X11/neXtaw/Paned.h>
#include <X11/neXtaw/Label.h>
#include <X11/neXtaw/Command.h>
#include <X11/neXtaw/Repeater.h>
#include <X11/neXtaw/SimpleMenu.h>
#include <X11/neXtaw/SmeLine.h>
#include <X11/neXtaw/SmeBSB.h>
#elif defined(HAVE_PKG_XAWPLUS)
#define USE_XAW
#define USE_XT
#include <X11/Intrinsic.h>
#include <X11/XawPlus/Form.h>
#include <X11/XawPlus/Paned.h>
#include <X11/XawPlus/Label.h>
#include <X11/XawPlus/Command.h>
#include <X11/XawPlus/Repeater.h>
#include <X11/XawPlus/SimpleMenu.h>
#include <X11/XawPlus/SmeLine.h>
#include <X11/XawPlus/SmeBSB.h>
#elif defined(HAVE_PKG_XM)
#define USE_XM
#define USE_XT
#include <X11/Intrinsic.h>
#include <Xm/Xm.h>
#include <Xm/Form.h>
#include <Xm/PanedW.h>
#include <Xm/Label.h>
#include <Xm/PushB.h>
#include <Xm/ToggleB.h>
#include <Xm/RowColumn.h>
#include <Xm/MenuShell.h>
#else /* HAVE_PKG_ */
#error GUI toolkit either unspecified or unsupported
#endif /* HAVE_PKG_ */

#ifdef USE_XT
#define XK_MISCELLANY
#include <X11/Xlib.h>
#include <X11/StringDefs.h>
#include <X11/keysymdef.h>
#include <X11/Shell.h>
#endif /* USE_XT */

#ifdef USE_WINDOWS
#include <commctrl.h>
#include <windowsx.h>
#define XtNumber(t) (sizeof(t)/sizeof(*t))
typedef void *XtPointer;
#endif /* USE_WINDOWS */

#if defined(USE_XAW)
#define formWidgetClass       formWidgetClass
#define panedWidgetClass      panedWidgetClass
#define labelWidgetClass      labelWidgetClass
#define commandWidgetClass    commandWidgetClass
#define repeaterWidgetClass   repeaterWidgetClass
#define menuEntryWidgetClass  smeBSBObjectClass
#define CreatePopupMenu(title, toplevel) \
	XtCreatePopupShell(title, simpleMenuWidgetClass, toplevel, NULL, 0)
#define AddMenuSeparator(title, menu) \
	XtVaCreateManagedWidget(title, smeLineObjectClass, menu, NULL)
#define AddMenuLabel(title, menu) \
	XtVaCreateManagedWidget(title, smeBSBObjectClass, menu, NULL);
#define AddMenuRadio(title, menu, cb, checked) \
	XtVaCreateManagedWidget(title, menuEntryWidgetClass, menu, \
	NvalueChangedCallback, cb, NtoggleState, checked ? check : None, \
	XtNleftMargin, 9, \
	NULL);
#define Nlabel                XtNlabel
#define Ncallback             XtNcallback
#define NvalueChangedCallback XtNcallback
#define Ntop                  XtNtop
#define Nbottom               XtNbottom
#define Nleft                 XtNleft
#define Nright                XtNright
#define ChainTop              XtChainTop
#define ChainBottom           XtChainTop
#define ChainLeft             XtChainLeft
#define ChainRight            XtChainLeft
#define NvertDistance         XtNvertDistance
#define NhorizDistance        XtNhorizDistance
#define NtoggleState          XtNleftBitmap
#define MenuWidget            Widget
#elif defined(USE_XM)
#define formWidgetClass       xmFormWidgetClass
#define panedWidgetClass      xmPanedWindowWidgetClass
#define labelWidgetClass      xmLabelWidgetClass
#define commandWidgetClass    xmPushButtonWidgetClass
#define repeaterWidgetClass   xmPushButtonWidgetClass
#define menuEntryWidgetClass  xmToggleButtonWidgetClass
#define CreatePopupMenu(title, toplevel) \
	XmCreatePopupMenu(toplevel, title, NULL, 0)
#define AddMenuSeparator(title, menu) while (0) { }
#define AddMenuLabel(title, menu) \
	XtVaCreateManagedWidget(title, xmToggleButtonWidgetClass, menu, NULL);
#define AddMenuRadio(title, menu, cb, checked) \
	XtVaCreateManagedWidget(title, menuEntryWidgetClass, menu, \
	NvalueChangedCallback, cb, NtoggleState, checked ? XmSET : XmUNSET, \
	NULL);
#define Nlabel                XmNlabelString
#define Ncallback             XmNactivateCallback
#define NvalueChangedCallback XmNvalueChangedCallback
#define Ntop                  XmNtopAttachment
#define Nbottom               XmNbottomAttachment
#define Nleft                 XmNleftAttachment
#define Nright                XmNrightAttachment
#define ChainTop              XmATTACH_FORM
#define ChainBottom           XmATTACH_NONE
#define ChainLeft             XmATTACH_FORM
#define ChainRight            XmATTACH_NONE
#define NvertDistance         XmNtopOffset
#define NhorizDistance        XmNleftOffset
#define NtoggleState          XmNset
#define MenuWidget            Widget
#elif defined(USE_WINDOWS)
#define Widget                HWND
#define MenuWidget            HMENU
#define CreatePopupMenu(title, toplevel) \
	CreatePopupMenu()
#define AddMenuSeparator(title, menu) \
	AppendMenu(menu, MF_SEPARATOR, 0, NULL)
#define AddMenuLabel(title, menu) \
	AppendMenu(menu, MF_STRING | MF_DISABLED, 0, title)
#define AddMenuRadio(title, menu, cb, check) \
	AppendMenu(menu, MF_STRING | (check?MF_CHECKED:0), cb, title)
#define CHRX 16
#define CHRY 20
#define RIGHTMARGIN 100
#else /* USE_ */
#error GUI toolkit paradigm either unspecified or unsupported
#endif /* USE_ */

typedef enum {
  PARM_TKPARMS,
  PARM_LINES,
  PARM_COLUMNS,
  PARM_MODEL,
  PARM_INPUT,
  PARM_FONT
} DriverParameter;
#define BRLPARMS "tkparms", "lines", "columns", "model", "input", "font"

#include "brl_driver.h"
#include "braille.h"

#define MAXLINES 3
#define MAXCOLS 88
#define WHOLESIZE (MAXLINES * MAXCOLS)
static int cols,lines;
static int input;
static char *model = "simple";
static const char *fontname = "-*-clearlyu-*-*-*-*-*-*-*-*-*-*-iso10646-1,-*-fixed-*-*-*-*-*-*-*-*-*-*-iso10646-1,-*-unifont-*-*-*-*-*-*-*-*-*-*-iso10646-1,-*-fixed-*-*-*-*-*-*-*-*-*-*-iso8859-1";
static int xtArgc = 1;
static char *xtDefArgv[]= { "brltty", NULL };
static char **xtArgv = xtDefArgv;
static int regenerate;
static int generateToplevel(void);
static void destroyToplevel(void);
#if defined(USE_XAW) || defined(USE_WINDOWS)
static unsigned char displayedWindow[WHOLESIZE];
#endif /* USE_XAW || USE_WINDOWS */
static wchar_t displayedVisual[WHOLESIZE];

#define BUTWIDTH 48
#define BUTHEIGHT 32

static Widget toplevel,hbox,display[WHOLESIZE];
static MenuWidget menu;
#if defined(USE_XAW) || defined(USE_WINDOWS)
static Widget displayb[WHOLESIZE];
#endif /* USE_XAW || USE_WINDOWS */
#ifdef USE_XAW
static Pixmap check;
#endif /* USE_XAW */
static int lastcursor = BRL_NO_CURSOR;
#ifdef USE_XT
static Atom wm_delete_window;
static Widget vbox,keybox;
static Pixel displayForeground,displayBackground;
static XtAppContext app_con;
#ifdef USE_XM
static XmString display_cs;
#endif /* USE_XAW */
#endif /* USE_XT */
#ifdef USE_XAW
static XFontSet fontset;
#elif defined(USE_WINDOWS)
static HFONT font;
static int totlines;
#endif /* USE_WINDOWS */

#ifdef USE_WINDOWS
static int modelWidth,modelHeight;
#endif /* USE_WINDOWS */

#ifdef USE_XT
static void KeyPressCB(Widget w, XtPointer closure, XtPointer callData)
{
  logMessage(LOG_DEBUG,"keypresscb(%p)", closure);
  enqueueCommand((long) closure);
}

static void keypress(Widget w, XEvent *event, String *params, Cardinal *num_params) {
  static Modifiers my_modifiers;
  long keypressed;
  Modifiers modifiers, modifier;
  KeySym keysym;

  if (event->type != KeyPress && event->type != KeyRelease) {
    logMessage(LOG_ERR,"keypress is not a KeyPress");
    return;
  }
  keysym = XtGetActionKeysym(event, &modifiers);
  modifiers |= my_modifiers;
  logMessage(LOG_DEBUG,"keypress(%#lx), modif(%#x)", keysym, modifiers);

  /* latin1 */
  if (keysym < 0x100) keysym |= 0x1000000;

  if ((keysym & 0x1f000000) == 0x1000000) {
    /* unicode */
    if ((keysym & ~UNICODE_CELL_MASK) == UNICODE_BRAILLE_ROW)
      keypressed = BRL_CMD_BLK(PASSDOTS) | (keysym & 0xff);
    else {
      int c = convertWcharToChar(keysym & 0xffffff);
      if (c == EOF) {
	logMessage(LOG_DEBUG, "non translatable unicode U+%lx", keysym & 0xffffff);
	return;
      }
      keypressed = BRL_CMD_BLK(PASSCHAR) | c;
    }
  }
  else switch(keysym) {
    case XK_Shift_L:
    case XK_Shift_R:   modifier = ShiftMask;   goto modif;
    case XK_Control_L:
    case XK_Control_R: modifier = ControlMask; goto modif;
    case XK_Alt_L:
    case XK_Alt_R:
    case XK_Meta_L:
    case XK_Meta_R:    modifier = Mod1Mask;    goto modif;
    case XK_KP_Enter:
    case XK_Return:       keypressed = BRL_CMD_BLK(PASSKEY) | BRL_KEY_ENTER;           break;
    case XK_KP_Tab:
    case XK_Tab:          keypressed = BRL_CMD_BLK(PASSKEY) | BRL_KEY_TAB;             break;
    case XK_BackSpace:    keypressed = BRL_CMD_BLK(PASSKEY) | BRL_KEY_BACKSPACE;       break;
    case XK_Escape:       keypressed = BRL_CMD_BLK(PASSKEY) | BRL_KEY_ESCAPE;          break;
    case XK_KP_Left:
    case XK_Left:         keypressed = BRL_CMD_BLK(PASSKEY) | BRL_KEY_CURSOR_LEFT;     break;
    case XK_KP_Right:
    case XK_Right:        keypressed = BRL_CMD_BLK(PASSKEY) | BRL_KEY_CURSOR_RIGHT;    break;
    case XK_KP_Up:
    case XK_Up:           keypressed = BRL_CMD_BLK(PASSKEY) | BRL_KEY_CURSOR_UP;       break;
    case XK_KP_Down:
    case XK_Down:         keypressed = BRL_CMD_BLK(PASSKEY) | BRL_KEY_CURSOR_DOWN;     break;
    case XK_KP_Page_Up:
    case XK_Page_Up:      keypressed = BRL_CMD_BLK(PASSKEY) | BRL_KEY_PAGE_UP;         break;
    case XK_KP_Page_Down:
    case XK_Page_Down:    keypressed = BRL_CMD_BLK(PASSKEY) | BRL_KEY_PAGE_DOWN;       break;
    case XK_KP_Home:
    case XK_Home:         keypressed = BRL_CMD_BLK(PASSKEY) | BRL_KEY_HOME;            break;
    case XK_KP_End:
    case XK_End:          keypressed = BRL_CMD_BLK(PASSKEY) | BRL_KEY_END;             break;
    case XK_KP_Insert:
    case XK_Insert:       keypressed = BRL_CMD_BLK(PASSKEY) | BRL_KEY_INSERT;          break;
    case XK_KP_Delete:
    case XK_Delete:       keypressed = BRL_CMD_BLK(PASSKEY) | BRL_KEY_DELETE;          break;
    case XK_KP_F1:
    case XK_F1:           keypressed = BRL_CMD_BLK(PASSKEY) | (BRL_KEY_FUNCTION +  0); break;
    case XK_KP_F2:
    case XK_F2:           keypressed = BRL_CMD_BLK(PASSKEY) | (BRL_KEY_FUNCTION +  1); break;
    case XK_KP_F3:
    case XK_F3:           keypressed = BRL_CMD_BLK(PASSKEY) | (BRL_KEY_FUNCTION +  2); break;
    case XK_KP_F4:
    case XK_F4:           keypressed = BRL_CMD_BLK(PASSKEY) | (BRL_KEY_FUNCTION +  3); break;
    case XK_F5:           keypressed = BRL_CMD_BLK(PASSKEY) | (BRL_KEY_FUNCTION +  4); break;
    case XK_F6:           keypressed = BRL_CMD_BLK(PASSKEY) | (BRL_KEY_FUNCTION +  5); break;
    case XK_F7:           keypressed = BRL_CMD_BLK(PASSKEY) | (BRL_KEY_FUNCTION +  6); break;
    case XK_F8:           keypressed = BRL_CMD_BLK(PASSKEY) | (BRL_KEY_FUNCTION +  7); break;
    case XK_F9:           keypressed = BRL_CMD_BLK(PASSKEY) | (BRL_KEY_FUNCTION +  8); break;
    case XK_F10:          keypressed = BRL_CMD_BLK(PASSKEY) | (BRL_KEY_FUNCTION +  9); break;
    case XK_F11:          keypressed = BRL_CMD_BLK(PASSKEY) | (BRL_KEY_FUNCTION + 10); break;
    case XK_F12:          keypressed = BRL_CMD_BLK(PASSKEY) | (BRL_KEY_FUNCTION + 11); break;
    case XK_F13:          keypressed = BRL_CMD_BLK(PASSKEY) | (BRL_KEY_FUNCTION + 12); break;
    case XK_F14:          keypressed = BRL_CMD_BLK(PASSKEY) | (BRL_KEY_FUNCTION + 13); break;
    case XK_F15:          keypressed = BRL_CMD_BLK(PASSKEY) | (BRL_KEY_FUNCTION + 14); break;
    case XK_F16:          keypressed = BRL_CMD_BLK(PASSKEY) | (BRL_KEY_FUNCTION + 15); break;
    case XK_F17:          keypressed = BRL_CMD_BLK(PASSKEY) | (BRL_KEY_FUNCTION + 16); break;
    case XK_F18:          keypressed = BRL_CMD_BLK(PASSKEY) | (BRL_KEY_FUNCTION + 17); break;
    case XK_F19:          keypressed = BRL_CMD_BLK(PASSKEY) | (BRL_KEY_FUNCTION + 18); break;
    case XK_F20:          keypressed = BRL_CMD_BLK(PASSKEY) | (BRL_KEY_FUNCTION + 19); break;
    case XK_F21:          keypressed = BRL_CMD_BLK(PASSKEY) | (BRL_KEY_FUNCTION + 20); break;
    case XK_F22:          keypressed = BRL_CMD_BLK(PASSKEY) | (BRL_KEY_FUNCTION + 21); break;
    case XK_F23:          keypressed = BRL_CMD_BLK(PASSKEY) | (BRL_KEY_FUNCTION + 22); break;
    case XK_F24:          keypressed = BRL_CMD_BLK(PASSKEY) | (BRL_KEY_FUNCTION + 23); break;
    case XK_F25:          keypressed = BRL_CMD_BLK(PASSKEY) | (BRL_KEY_FUNCTION + 24); break;
    case XK_F26:          keypressed = BRL_CMD_BLK(PASSKEY) | (BRL_KEY_FUNCTION + 25); break;
    case XK_F27:          keypressed = BRL_CMD_BLK(PASSKEY) | (BRL_KEY_FUNCTION + 26); break;
    case XK_F28:          keypressed = BRL_CMD_BLK(PASSKEY) | (BRL_KEY_FUNCTION + 27); break;
    case XK_F29:          keypressed = BRL_CMD_BLK(PASSKEY) | (BRL_KEY_FUNCTION + 28); break;
    case XK_F30:          keypressed = BRL_CMD_BLK(PASSKEY) | (BRL_KEY_FUNCTION + 29); break;
    case XK_F31:          keypressed = BRL_CMD_BLK(PASSKEY) | (BRL_KEY_FUNCTION + 30); break;
    case XK_F32:          keypressed = BRL_CMD_BLK(PASSKEY) | (BRL_KEY_FUNCTION + 31); break;
    case XK_F33:          keypressed = BRL_CMD_BLK(PASSKEY) | (BRL_KEY_FUNCTION + 32); break;
    case XK_F34:          keypressed = BRL_CMD_BLK(PASSKEY) | (BRL_KEY_FUNCTION + 33); break;
    case XK_F35:          keypressed = BRL_CMD_BLK(PASSKEY) | (BRL_KEY_FUNCTION + 34); break;
    case XK_KP_Space:     keypressed = BRL_CMD_BLK(PASSCHAR) | ' '; break;
    case XK_KP_Equal:     keypressed = BRL_CMD_BLK(PASSCHAR) | '='; break;
    case XK_KP_Multiply:  keypressed = BRL_CMD_BLK(PASSCHAR) | '*'; break;
    case XK_KP_Add:       keypressed = BRL_CMD_BLK(PASSCHAR) | '+'; break;
    case XK_KP_Separator: keypressed = BRL_CMD_BLK(PASSCHAR) | ','; break;
    case XK_KP_Subtract:  keypressed = BRL_CMD_BLK(PASSCHAR) | '-'; break;
    case XK_KP_Decimal:   keypressed = BRL_CMD_BLK(PASSCHAR) | '.'; break;
    case XK_KP_Divide:    keypressed = BRL_CMD_BLK(PASSCHAR) | '/'; break;
    case XK_KP_0:         keypressed = BRL_CMD_BLK(PASSCHAR) | '0'; break;
    case XK_KP_1:         keypressed = BRL_CMD_BLK(PASSCHAR) | '1'; break;
    case XK_KP_2:         keypressed = BRL_CMD_BLK(PASSCHAR) | '2'; break;
    case XK_KP_3:         keypressed = BRL_CMD_BLK(PASSCHAR) | '3'; break;
    case XK_KP_4:         keypressed = BRL_CMD_BLK(PASSCHAR) | '4'; break;
    case XK_KP_5:         keypressed = BRL_CMD_BLK(PASSCHAR) | '5'; break;
    case XK_KP_6:         keypressed = BRL_CMD_BLK(PASSCHAR) | '6'; break;
    case XK_KP_7:         keypressed = BRL_CMD_BLK(PASSCHAR) | '7'; break;
    case XK_KP_8:         keypressed = BRL_CMD_BLK(PASSCHAR) | '8'; break;
    case XK_KP_9:         keypressed = BRL_CMD_BLK(PASSCHAR) | '9'; break;
    default: logMessage(LOG_DEBUG,"unsupported keysym %lx",keysym); return;
  }

  if (modifiers & ControlMask) keypressed |= BRL_FLG_INPUT_CONTROL;
  if (modifiers & Mod1Mask) keypressed |= BRL_FLG_INPUT_META;
  if (modifiers & ShiftMask) keypressed |= BRL_FLG_INPUT_SHIFT;
  if (modifiers & LockMask) keypressed |= BRL_FLG_INPUT_UPPER;
  if (event->type != KeyPress) keypressed = BRL_CMD_NOOP;

  logMessage(LOG_DEBUG,"keypressed %#lx", keypressed);
  enqueueCommand(keypressed);
  return;

modif:
  logMessage(LOG_DEBUG,"modifier %#x", modifier);
  if (event->type == KeyPress)
    my_modifiers |= modifier;
  else
    my_modifiers &= ~modifier;
}

static void route(Widget w, XEvent *event, String *params, Cardinal *num_params)
{
  int index = atoi(params[0]);
  logMessage(LOG_DEBUG,"route(%u)", index);
  enqueueCommand(BRL_CMD_BLK(ROUTE) | (index&BRL_MSK_ARG));
}

static void quit(Widget w, XEvent *event, String *params, Cardinal *num_params)
{
  XtAppSetExitFlag(app_con);
}
#endif /* USE_XT */

static inline Widget crKeyBut(char *name, long keycode, int repeat,
    int horizDistance, int vertDistance)
{
  Widget button;
#if defined(USE_XT)
  button = XtVaCreateManagedWidget(name,
    repeat?repeaterWidgetClass:commandWidgetClass, keybox,
    XtNwidth, BUTWIDTH, XtNheight, BUTHEIGHT,
#ifdef USE_XAW
    XtNinitialDelay, 500, XtNminimumDelay, 100,
#endif /* USE_XAW */
    NhorizDistance, horizDistance,
    NvertDistance, vertDistance,
    Ntop, ChainTop,
    Nbottom, ChainBottom,
    Nleft, ChainLeft,
    Nright, ChainRight,
    NULL);
  XtAddCallback(button, Ncallback, KeyPressCB, (XtPointer) keycode);
#elif defined(USE_WINDOWS)
  button = CreateWindow(WC_BUTTON, name, WS_CHILD | WS_VISIBLE, horizDistance, totlines*CHRY+1+vertDistance, BUTWIDTH, BUTHEIGHT, toplevel, NULL, NULL, NULL);
  SetWindowLongPtr(button, GWLP_USERDATA, (LONG_PTR) keycode);
#else /* USE_ */
#error Toolkit button creation unspecified
#endif /* USE_ */
  return button;
}

struct button {
  char *label;
  long keycode;
  int repeat;
  int x,y;
};

struct model {
  char *name;
  struct button *buttons;
  int width,height;
};

static struct model *keyModel;

static struct button buttons_simple[] = {
  { "Dot1",   BRL_CMD_BLK(PASSDOTS)  | BRL_DOT1  , 0, 0, 0 },
  { "Dot2",   BRL_CMD_BLK(PASSDOTS)  | BRL_DOT2  , 0, 0, 1 },
  { "Dot3",   BRL_CMD_BLK(PASSDOTS)  | BRL_DOT3  , 0, 0, 2 },
  { "Dot4",   BRL_CMD_BLK(PASSDOTS)  | BRL_DOT4  , 0, 1, 0 },
  { "Dot5",   BRL_CMD_BLK(PASSDOTS)  | BRL_DOT5  , 0, 1, 1 },
  { "Dot6",   BRL_CMD_BLK(PASSDOTS)  | BRL_DOT6  , 0, 1, 2 },
  { "Dot7",   BRL_CMD_BLK(PASSDOTS)  | BRL_DOT7  , 0, 0, 3 },
  { "Dot8",   BRL_CMD_BLK(PASSDOTS)  | BRL_DOT8  , 0, 1, 3 },
  { "`",      BRL_CMD_TOP_LEFT, 0, 3, 0 },
  { "^",      BRL_CMD_LNUP,   1, 4, 0 },
  { "<",      BRL_CMD_FWINLT, 1, 3, 1 },
  { "Home",   BRL_CMD_HOME,   0, 4, 1 },
  { ">",      BRL_CMD_FWINRT, 1, 5, 1 },
  { "<=",     BRL_CMD_FWINLTSKIP, 0, 3, 2 },
  { "v",      BRL_CMD_LNDN,   1, 4, 2 },
  { "=>",     BRL_CMD_FWINRTSKIP, 0, 5, 2 },
  { "alt-c",  BRL_FLG_INPUT_META    | BRL_CMD_BLK(PASSCHAR) | 'c', 0, 3, 3 },
  { "ctrl-c", BRL_FLG_INPUT_CONTROL | BRL_CMD_BLK(PASSCHAR) | 'c', 0, 4, 3 },
  { "a",      BRL_CMD_BLK(PASSCHAR)                        | 'a', 0, 5, 3 },
  { "A",      BRL_CMD_BLK(PASSCHAR)                        | 'A', 0, 6, 3 },
  { "Alt-F1", BRL_FLG_INPUT_META | BRL_KEY_FUNCTION | BRL_CMD_BLK(PASSKEY) , 0, 7, 3 },
  { "Frez",   BRL_CMD_FREEZE,   0, 6, 0 },
  { "Help",   BRL_CMD_HELP,     0, 7, 0 },
  { "Pref",   BRL_CMD_PREFMENU, 0, 6, 1 },
  { "PL",     BRL_CMD_PREFLOAD, 0, 6, 2 },
  { "PS",     BRL_CMD_PREFSAVE, 0, 7, 2 },
  { NULL,     0,                0, 0, 0},
};

static struct button buttons_vs[] = {
	/*
  { "VT1",  BRL_CMD_BLK(SWITCHVT)+0,   1, 0, 1 },
  { "VT2",  BRL_CMD_BLK(SWITCHVT)+1, 1, 1, 1 },
  { "VT3",  BRL_CMD_BLK(SWITCHVT)+2, 1, 2, 1 },
  { "VT4",  BRL_CMD_BLK(SWITCHVT)+3, 1, 6, 1 },
  { "VT5",  BRL_CMD_BLK(SWITCHVT)+4, 1, 7, 1 },
  { "VT6",  BRL_CMD_BLK(SWITCHVT)+5, 1, 8, 1 },
	*/
  //{ "B5", EOF, /* cut */      1, 5, 2 },
  { "TOP",  BRL_CMD_TOP_LEFT,   1, 6, 2 },
  { "BOT",  BRL_CMD_BOT_LEFT,   1, 6, 4 },
  { "<=",   BRL_CMD_FWINLTSKIP, 1, 1, 0 },
  { "<=",   BRL_CMD_FWINLTSKIP, 1, 8, 2 },
  { "=>",   BRL_CMD_FWINRTSKIP, 1, 2, 0 },
  { "=>",   BRL_CMD_FWINRTSKIP, 1, 8, 4 },
  { "-^-",  BRL_CMD_LNUP,       1, 7, 2 },
  { "-v-",  BRL_CMD_LNDN,       1, 7, 4 },
  { "->",   BRL_CMD_FWINRT,     1, 8, 3 },
  { "<-",   BRL_CMD_FWINLT,     1, 6, 3 },
  { "HOME", BRL_CMD_HOME,       1, 7, 3 },
  { "^",    BRL_CMD_KEY(CURSOR_UP),    1, 1, 2 },
  { "v",    BRL_CMD_KEY(CURSOR_DOWN),  1, 1, 4 },
  { ">",    BRL_CMD_KEY(CURSOR_RIGHT), 1, 2, 3 },
  { "<",    BRL_CMD_KEY(CURSOR_LEFT),  1, 0, 3 },
  //{ "B3",   BRL_CMD_CSRVIS,     1, 2, 2 },
  { "DEL",  BRL_CMD_KEY(DELETE),       1, 0, 4 },
  { "INS",  BRL_CMD_KEY(INSERT),       1, 2, 4 },
  //{ "C5",   BRL_CMD_PASTE,      1, 5, 3 },
  //{ "D5",   EOF,                1, 5, 4 },
  //{ "B4",   EOF,                1, 3, 2 },

  //{ "B1",   EOF,                1, 0, 2 },
  //{ "C2",   EOF,                1, 1, 3 },
  //{ "C4",   EOF,                1, 3, 3 },
  //{ "D4",   EOF,                1, 3, 4 },
  { NULL,   0,                  0, 0, 0},
};

static struct model models[] = {
  { "normal",	buttons_simple,	4, 4 },
  { "vs",	buttons_vs,	9, 5 },
};

static void setModel(Widget w, XtPointer closure, XtPointer data)
{
  intptr_t newModel = (intptr_t) closure;
  if (newModel == XtNumber(models))
    keyModel = NULL;
  else
    keyModel = &models[newModel];
  regenerate = 1;
}

static void createKeyButtons(struct button *buttons) {
  struct button *b;
  for (b=buttons; b->label; b++)
    crKeyBut(b->label, b->keycode, b->repeat, b->x*(BUTWIDTH+1), b->y*(BUTHEIGHT+1));
}

struct radioInt {
  char *name;
  int value;
};

static struct radioInt colsRadio [] = {
  { "80", 80 },
  { "60", 60 },
  { "40", 40 },
  { "20", 20 },
  { "8",  8  },
};

static struct radioInt linesRadio [] = {
  { "3", 3 },
  { "2", 2 },
  { "1", 1 },
};

static void setWidth(Widget w, XtPointer closure, XtPointer data)
{
  intptr_t newCols = (intptr_t) closure;
  cols = newCols;
  regenerate = 1;
}

static void setHeight(Widget w, XtPointer closure, XtPointer data)
{
  intptr_t newLines = (intptr_t) closure;
  lines = newLines;
  regenerate = 1;
}

typedef void (*actionfun_t)(Widget, XtPointer, XtPointer);

enum actions {
  SETMODEL,
  SETWIDTH,
  SETHEIGHT,
};

static actionfun_t actionfun[] = {
  [SETMODEL] = setModel,
  [SETWIDTH] = setWidth,
  [SETHEIGHT] = setHeight,
};

#if defined(USE_XT)
#define SET_ACTION(cb, set) \
  (cb)[0].callback = (XtCallbackProc) actionfun[set]
#define SET_VALUE(cb, value) \
  (cb)[0].closure = (void*)(intptr_t) (value)
#elif defined(USE_WINDOWS)
#define SET_ACTION(cb, set) \
  (cb) = (set) << 8
#define SET_VALUE(cb, value) \
  (cb) = ((cb) & (~0xff)) | (value)
#define GET_ACTIONFUN(cbint) \
  actionfun[(cbint) >> 8]
#define GET_VALUE(cbint) \
  ((cbint) & 0xff)
#else /* USE_ */
#error Toolkit callback recording unspecified
#endif /* USE_ */
  
#ifdef USE_WINDOWS
static LRESULT CALLBACK wndProc(HWND hwnd, UINT uMsg, WPARAM wParam, LPARAM lParam) {
  if (uMsg == WM_COMMAND) {
    long keypressed;
    hwnd = GET_WM_COMMAND_HWND(wParam, lParam);
    keypressed = GetWindowLongPtr(hwnd, GWLP_USERDATA);
    if (keypressed) {
      enqueueCommand(keypressed);
    } else {
      /* menu entry */
      GET_ACTIONFUN(wParam)(NULL, (XtPointer)(GET_VALUE(wParam)), NULL);
    }
    return 0;
  }
  if (uMsg == WM_CONTEXTMENU) {
    TrackPopupMenu(menu, TPM_LEFTALIGN|TPM_RIGHTBUTTON, GET_X_LPARAM(lParam), GET_Y_LPARAM(lParam), 0, toplevel, NULL);
    return 0;
  }
  return DefWindowProc(hwnd, uMsg, wParam, lParam);
}

#define BRAILLE_USB 82
int CALLBACK fontEnumProc(ENUMLOGFONTEX *lpelfe, NEWTEXTMETRICEX *lpntme, DWORD FontType, LPARAM lParam) {
	int shift = 8*sizeof(lpntme->ntmFontSig.fsUsb[0]);
	if (!(lpntme->ntmFontSig.fsUsb[BRAILLE_USB / shift] &
		(1 << (BRAILLE_USB % shift))))
		return 1;
	font = CreateFont(CHRY-6, CHRX-4, 0, 0, 0, FALSE, FALSE, FALSE, DEFAULT_CHARSET, OUT_DEFAULT_PRECIS, CLIP_DEFAULT_PRECIS, DEFAULT_QUALITY, DEFAULT_PITCH, (LPCTSTR) lpelfe->elfFullName);
	if (!font) {
		logWindowsSystemError("Couldn't load font");
		logMessage(LOG_ERR,"font %s", lpelfe->elfFullName);
		return 1;
	}
	logMessage(LOG_INFO, "Using braille font `%s\'",lpelfe->elfFullName);
	return 0;
}
#endif /* USE_WINDOWS */

static int brl_readCommand(BrailleDisplay *brl, KeyTableCommandContext context)
{
#if defined(USE_XT)
  while (XtAppPending(app_con)) {
    XtAppProcessEvent(app_con,XtIMAll);
    if (XtAppGetExitFlag(app_con))
      raise(SIGTERM);
#elif defined(USE_WINDOWS)
    MSG msg;

    while (PeekMessage(&msg, NULL, 0, 0, PM_REMOVE)) {
      if (msg.message == WM_QUIT
	  || msg.message == WM_DESTROY
	  || msg.message == WM_CLOSE)
	raise(SIGTERM);
      else {
	TranslateMessage(&msg);
	DispatchMessage(&msg);
      }
#else /* USE_ */
#error Toolkit loop unspecified
#endif /* USE_ */
    if (regenerate) {
      regenerate = 0;
      destroyToplevel();
      generateToplevel();
      brl->textColumns = cols;
      brl->textRows = lines;
      brl->resizeRequired = 1;
    }
  }
  return EOF;
}

#ifdef USE_XT
static char *fallback_resources[] = {
  "*display.background: lightgreen",
#ifdef USE_XAW
  "*displayb.background: black",
  "*displayb.foreground: white",
#endif /* USE_XAW */
  "*keybox.background: lightgrey",
  "*menu.Label: Brltty",
  "*menu.background: lightgrey",
  NULL
};
#endif /* USE_XT */

#ifdef USE_XM
static void popup(Widget w, XEvent *event, String *params, Cardinal *num_params)
{
  Widget shell = XtParent(menu);
  XmMenuPosition(menu, &event->xbutton);
  XtManageChild(menu);
  XtPopup(shell, XtGrabNone);
}
#endif /* USE_XM */

static int generateToplevel(void)
{
#ifdef USE_XT
  int argc;
  char **argv;
#ifdef USE_XAW
  char *def_string_return;
  char **missing_charset_list_return;
  int missing_charset_count_return;
#endif /* USE_XAW */
  XtActionsRec actions [] = {
    { "route", route },
    { "keypress", keypress },
#ifdef USE_XM
    { "popup", popup },
#endif /* USE_XM */
    { "Quit", quit },
    };
  char translations[] = "<Message>WM_PROTOCOLS: Quit()";
  char inputActions[] = "\
:<Key>: keypress()\n\
:<KeyUp>: keypress()\n";
  char popupAction[] =
	"None<Btn3Down>: "
#if defined(USE_XAW)
	"XawPositionSimpleMenu(menu) MenuPopup(menu)"
#elif defined(USE_XM)
	"popup()"
#endif /* USE_ */
	"\n";
  Widget tmp_vbox;
  char *disp;
#ifdef USE_XAW
  char *dispb;
#endif /* USE_XAW */
  XtCallbackRec cb[2] = { { NULL, NULL }, { NULL, NULL } };
#endif /* USE_XT */
#ifdef USE_WINDOWS
  UINT cb = 0;
#endif /* USE_WINDOWS */
  struct radioInt *radioInt;
  struct model *radioModel;
  int y,x;

#if defined(USE_XT)
  argc = xtArgc;
  if ((argv = malloc((xtArgc + 1) * sizeof(*xtArgv)))) {
    memcpy(argv, xtArgv, (xtArgc + 1) * sizeof(*xtArgv));

    /* toplevel */
    toplevel = XtVaOpenApplication(&app_con, "Brltty",
      NULL, 0,
      &argc, argv, fallback_resources,
      sessionShellWidgetClass,
      XtNallowShellResize, True,
      XtNinput, input ? True : False,
      NULL);

    XtAppAddActions(app_con,actions,XtNumber(actions));
    XtOverrideTranslations(toplevel,XtParseTranslationTable(translations));

    free(argv);
  } else {
    logMallocError();
    toplevel = NULL;
  }

#elif defined(USE_WINDOWS)
  {
    HWND root = GetDesktopWindow();
    HDC hdc = GetDC(root);
    EnumFontFamiliesEx(hdc, NULL, (void*) fontEnumProc, 0, 0);
    ReleaseDC(root, hdc);
    if (!font) {
      logMessage(LOG_ERR,"Error while loading braille font");
      totlines = lines;
    } else {
      totlines = 2*lines;
    }
  }

  {
    WNDCLASS wndclass = {
      .style = 0,
      .lpfnWndProc = wndProc,
      .cbClsExtra = 0,
      .cbWndExtra = 0,
      .hInstance = NULL,
      .hIcon = LoadIcon(NULL, IDI_APPLICATION), /* TODO: nice icon */
      .hCursor = LoadCursor(NULL, IDC_ARROW),
      .hbrBackground = NULL,
      .lpszMenuName = NULL,
      .lpszClassName = "BRLTTYWClass",
    };
    if (!(RegisterClass(&wndclass)) &&
	GetLastError() != ERROR_CLASS_ALREADY_EXISTS) {
      logWindowsSystemError("RegisterClass");
      if (font) {
	DeleteObject(font);
	font = NULL;
      }
      return 0;
    }
    modelWidth = cols*CHRX;
    if (keyModel) {
      if (keyModel->width*(BUTWIDTH+1)+1 > modelWidth)
	modelWidth = keyModel->width *(BUTWIDTH +1)-1;
      modelHeight = keyModel->height*(BUTHEIGHT+1);
    } else {
      modelHeight = 0;
    }
    if (!(toplevel = CreateWindowEx(WS_EX_TOPMOST | WS_EX_TOOLWINDOW,
	    "BRLTTYWClass", "BRLTTY",
	    WS_POPUP, GetSystemMetrics(SM_CXSCREEN)-modelWidth-RIGHTMARGIN, 0,
	    modelWidth, totlines*CHRY+modelHeight, NULL, NULL, NULL, NULL))) {
      logWindowsSystemError("CreateWindow");
      if (font) {
	DeleteObject(font);
	font = NULL;
      }
      return 0;
    }
  }
#else /* USE_ */
#error Toolkit toplevel creation unspecified
#endif /* USE_ */

  /* vertical separation */
#ifdef USE_XT
  vbox = XtVaCreateManagedWidget("vbox",panedWidgetClass,toplevel,
#ifdef USE_XM
    XmNmarginHeight, 0,
    XmNmarginWidth, 0,
    XmNspacing, 1,
#endif /* USE_XM */
    XtNresize, True,
    XtNtranslations, XtParseTranslationTable(popupAction),
    NULL);
  if (input)
    XtAugmentTranslations(vbox, XtParseTranslationTable(inputActions));
#endif /* USE_XT */

#ifdef USE_XAW
  if (!(fontset = XCreateFontSet(XtDisplay(toplevel), fontname, &missing_charset_list_return, &missing_charset_count_return, &def_string_return)))
    logMessage(LOG_ERR,"Error while loading unicode font");
  if (missing_charset_count_return) {
    int i;
    for (i=0; i<missing_charset_count_return; i++)
      logMessage(LOG_INFO,"Could not load a unicode font for charset %s",missing_charset_list_return[i]);
    XFreeStringList(missing_charset_list_return);
  }
#endif /* USE_XAW */
  
#ifdef USE_XT
  /* horizontal separation */
  hbox = XtVaCreateManagedWidget("hbox",panedWidgetClass,vbox,
    XtNorientation, XtEhorizontal,
#ifdef USE_XM
    XmNmarginHeight, 0,
    XmNmarginWidth, 0,
    XmNspacing, 0,
#endif /* USE_XM */
#ifdef USE_XAW
    XtNshowGrip,False,
#else /* USE_XAW */
    XmNpaneMaximum,20*lines,
    XmNpaneMinimum,20*lines,
    XmNskipAdjust, True,
#endif /* USE_XAW */
    XtNresize, True,
    NULL);

  /* display Label */
  disp=XtMalloc(2);
  disp[0]=' ';
  disp[1]=0;

#ifdef USE_XAW
  dispb=XtMalloc(4);
  dispb[0]=0xe0|((0x28>>4)&0x0f);
  dispb[1]=0x80|((0x28<<2)&0x3f);
  dispb[2]=0x80;
  dispb[3]=0;
#endif /* USE_XAW */

#ifdef USE_XM
  display_cs = XmStringCreateLocalized(disp);
#endif /* USE_XM */
#endif /* USE_XT */

#ifdef USE_WINDOWS
  hbox = CreateWindow(WC_STATIC, "", WS_CHILD | WS_VISIBLE, 0, 0,
       modelWidth, totlines*CHRY+modelHeight, toplevel, NULL, NULL, NULL);
#endif /* USE_WINDOWS */

  for (x=0;x<cols;x++) {
#ifdef USE_XT
    /* vertical separation */
    tmp_vbox = XtVaCreateManagedWidget("tmp_vbox",panedWidgetClass,hbox,
#ifdef USE_XAW
      XtNshowGrip,False,
#else /* USE_XAW */
      XmNpaneMaximum,20,
      XmNpaneMinimum,20,
      XmNskipAdjust, True,
#endif /* USE_XAW */
#ifdef USE_XM
      XmNmarginHeight, 0,
      XmNmarginWidth, 0,
      XmNspacing, 0,
#endif /* USE_XM */
      XtNresize, True,
      NULL);
#endif /* USE_XT */

    for (y=0;y<lines;y++) {
#if defined(USE_XT)
      char action[] = "<Btn1Up>: route(100)";
      XtTranslations transl;

      snprintf(action,sizeof(action),"<Btn1Up>: route(%u)",y*cols+x);
      transl = XtParseTranslationTable(action);

      display[y*cols+x] = XtVaCreateManagedWidget("display",labelWidgetClass,tmp_vbox,
	XtNtranslations, transl,
#ifdef USE_XAW
	XtNshowGrip,False,
	XtNinternational, True,
#else /* USE_XAW */
	XmNpaneMaximum,20,
	XmNpaneMinimum,20,
	XmNskipAdjust, True,
#endif /* USE_XAW */
#ifdef USE_XAW
	XtNlabel, disp,
	fontset ? XNFontSet : NULL, fontset, NULL
#else /* USE_XAW */
	XmNlabelString, display_cs, NULL
#endif /* USE_XAW */
	);

#ifdef USE_XAW
      if (fontset) {
	displayb[y*cols+x] = XtVaCreateManagedWidget("displayb",labelWidgetClass,tmp_vbox,
	  XtNtranslations, transl,
	  XtNinternational, True,
	  XNFontSet, fontset,
	  XtNshowGrip,False,
	  XtNlabel, dispb,
	  NULL);
      }
#endif /* USE_XAW */
#elif defined(USE_WINDOWS)
      display[y*cols+x] = CreateWindow(WC_BUTTON, " ", WS_CHILD | WS_VISIBLE | BS_CHECKBOX | BS_PUSHLIKE, x*CHRX, y*CHRY, CHRX, CHRY, toplevel, NULL, NULL, NULL);
      SetWindowLongPtr(display[y*cols+x], GWLP_USERDATA, (LONG_PTR) (BRL_CMD_BLK(ROUTE) | ((y*cols+x)&BRL_MSK_ARG)));
      if (font) {
        displayb[y*cols+x] = CreateWindowW(WC_BUTTONW, WS_C(" "), WS_CHILD | WS_VISIBLE | BS_CHECKBOX | BS_PUSHLIKE, x*CHRX, (lines+y)*CHRY, CHRX, CHRY, toplevel, NULL, NULL, NULL);
        SetWindowLongPtr(displayb[y*cols+x], GWLP_USERDATA, (LONG_PTR) (BRL_CMD_BLK(ROUTE) | ((y*cols+x)&BRL_MSK_ARG)));
	SendMessage(displayb[y*cols+x], WM_SETFONT, (WPARAM) font, TRUE);
      }
#else /* USE_ */
#error Toolkit display unspecified
#endif /* USE_ */
    }
  }
#ifdef USE_XT
#ifdef USE_XM
  XmStringFree(display_cs);
#endif /* USE_XM */
  XtFree(disp);
#ifdef USE_XAW
  XtFree(dispb);
#endif /* USE_XAW */
#endif /* USE_XT */
#ifdef USE_XT
  XtVaGetValues(display[0],
    XtNforeground, &displayForeground,
    XtNbackground, &displayBackground,
    NULL);
#endif /* USE_XT */

  if (keyModel) {
    /* key box */
#ifdef USE_XT
    keybox = XtVaCreateManagedWidget("keybox",formWidgetClass,vbox,
#ifdef USE_XAW
      XtNdefaultDistance,0,
#endif /* USE_XAW */
      NULL);
#endif /* USE_XT */
    createKeyButtons(keyModel->buttons);
  }

  menu = CreatePopupMenu("menu", toplevel);

#ifdef USE_XAW
  if (!check) {
    static unsigned char checkimg [] = {
      0x00, 0x00, 0xc0, 0x60, 0x33, 0x1e, 0x0c, 0x00
    };
    check = XCreateBitmapFromData(XtDisplay(toplevel),
	RootWindowOfScreen(XtScreen(toplevel)), (char *) checkimg, 8, 8);
  }
#endif /* USE_XAW */

#ifdef USE_XAW
  AddMenuSeparator("WidthLine", menu);
#endif /* USE_XAW */
  AddMenuLabel("Width", menu);
  SET_ACTION(cb, SETWIDTH);
  for (radioInt = colsRadio; radioInt < &colsRadio[XtNumber(colsRadio)]; radioInt++) {
    SET_VALUE(cb, radioInt->value);
    AddMenuRadio(radioInt->name, menu, cb, radioInt->value == cols);
  }

  AddMenuSeparator("HeightLine", menu);
  AddMenuLabel("Height", menu);
  SET_ACTION(cb, SETHEIGHT);
  for (radioInt = linesRadio; radioInt < &linesRadio[XtNumber(linesRadio)]; radioInt++) {
    SET_VALUE(cb, radioInt->value);
    AddMenuRadio(radioInt->name, menu, cb, radioInt->value == lines);
  }

  AddMenuSeparator("ModelLine", menu);
  AddMenuLabel("Model", menu);
  SET_ACTION(cb, SETMODEL);
  for (radioModel = models; radioModel < &models[XtNumber(models)]; radioModel++) {
    SET_VALUE(cb, radioModel-models);
    AddMenuRadio(radioModel->name, menu, cb, radioModel == keyModel);
  }

  SET_VALUE(cb, XtNumber(models));
  AddMenuRadio("bare", menu, cb, !keyModel);

  /* go go go */
#if defined(USE_XT)
  XtRealizeWidget(toplevel);
  if (!wm_delete_window)
    wm_delete_window = XInternAtom(XtDisplay(toplevel), "WM_DELETE_WINDOW", False);
  XSetWMProtocols(XtDisplay(toplevel),XtWindow(toplevel),&wm_delete_window,1);
#elif defined(USE_WINDOWS)
  ShowWindow(toplevel, SW_SHOWDEFAULT);
  UpdateWindow(toplevel);
#else /* USE_ */
#error Toolkit toplevel realization unspecified
#endif /* USE_ */
#if defined(USE_XAW) || defined(USE_WINDOWS)
  memset(displayedWindow,0,sizeof(displayedWindow));
#endif /* USE_XAW || USE_WINDOWS */
  memset(displayedVisual,0,sizeof(displayedVisual));
  lastcursor = BRL_NO_CURSOR;
  return 1;
}

static int brl_construct(BrailleDisplay *brl, char **parameters, const char *device)
{
  lines=1;
  if (*parameters[PARM_LINES]) {
    static const int minimum = 1;
    static const int maximum = MAXLINES;
    int value;
    if (validateInteger(&value, parameters[PARM_LINES], &minimum, &maximum)) {
      lines=value;
    } else {
      logMessage(LOG_WARNING, "%s: %s", "invalid line count", parameters[PARM_LINES]);
    }
  }

  cols=40;
  if (*parameters[PARM_COLUMNS]) {
    static const int minimum = 1;
    static const int maximum = MAXCOLS;
    int value;
    if (validateInteger(&value, parameters[PARM_COLUMNS], &minimum, &maximum)) {
      cols=value;
    } else {
      logMessage(LOG_WARNING, "%s: %s", "invalid column count", parameters[PARM_COLUMNS]);
    }
  }

  if (*parameters[PARM_INPUT]) {
    unsigned int value;
    if (validateOnOff(&value, parameters[PARM_INPUT])) {
      input = value;
    } else {
      logMessage(LOG_WARNING, "%s: %s", "invalid input setting", parameters[PARM_INPUT]);
    }
  }

  if (*parameters[PARM_TKPARMS]) {
    int reallocated = 0;

    {
      int count;
      char **args1 = splitString(parameters[PARM_TKPARMS], ' ', &count);

      if (args1) {
        char **args2 = realloc(args1, (count+2) * sizeof(char *));

        if (args2) {
          char *name = strdup(xtDefArgv[0]);

          args1 = NULL;

          if (name) {
            memmove(args2+1, args2, (count+1) * sizeof(char *));
            args2[0] = name;
            count += 1;

            if (xtArgv != xtDefArgv) deallocateStrings(xtArgv);
            xtArgv = args2;
            xtArgc = count;
            args2 = NULL;

            reallocated = 1;
          } else {
            logMallocError();
          }

          if (args2) deallocateStrings(args2);
        } else {
          logMallocError();
        }

        if (args1) deallocateStrings(args1);
      }
    }

    if (!reallocated) return 0;
  }

  if (*parameters[PARM_MODEL]) {
    model = parameters[PARM_MODEL];
    for (keyModel = models; keyModel < &models[XtNumber(models)] && strcmp(keyModel->name,model); keyModel++);
    if (keyModel == &models[XtNumber(models)]) keyModel = NULL;
  }

  if (*parameters[PARM_FONT]) {
    fontname = parameters[PARM_FONT];
  }

#if defined(USE_XT)
  XtToolkitThreadInitialize();
  XtSetLanguageProc(NULL, NULL, NULL);
#endif /* USE_XT */ 

  brl->textColumns=cols;
  brl->textRows=lines;

  return generateToplevel();
}
static void destroyToplevel(void)
{
#if defined(USE_XT)
#ifdef USE_XAW
  if (fontset) {
    XFreeFontSet(XtDisplay(toplevel),fontset);
    fontset = NULL;
  }
  check = None;
#endif /* USE_XAW */
  XtDestroyApplicationContext(app_con);
  app_con = NULL;
#elif defined(USE_WINDOWS)
  DestroyMenu(menu);
  if (!DestroyWindow(toplevel))
    logWindowsSystemError("DestroyWindow");
  if (font) {
    DeleteObject(font);
    font = NULL;
  }
#else /* USE_ */
#error Toolkit toplevel destruction unspecified
#endif /* USE_ */
}

static void brl_destruct(BrailleDisplay *brl)
{
  destroyToplevel();
}

static int brl_writeWindow(BrailleDisplay *brl, const wchar_t *text)
{
  unsigned int from, to;
  wchar_t wc;
  int i;
#ifdef USE_XM
  char data[2];
#elif defined(USE_XAW)
  Utf8Buffer utf8;
#elif defined(USE_WINDOWS)
  wchar_t data[3];
#endif

  if (lastcursor != brl->cursor) {
    if (lastcursor != BRL_NO_CURSOR) {
#if defined(USE_XT)
      XtVaSetValues(display[lastcursor],
	XtNforeground, displayForeground,
	XtNbackground, displayBackground,
	NULL);
#elif defined(USE_WINDOWS)
      SendMessage(display[lastcursor],BM_SETSTATE,FALSE,0);
#else /* USE_ */
#error Toolkit cursor not specified
#endif /* USE_ */
    }
    lastcursor = brl->cursor;
    if (lastcursor != BRL_NO_CURSOR) {
#if defined(USE_XT)
      XtVaSetValues(display[lastcursor],
	XtNforeground, displayBackground,
	XtNbackground, displayForeground,
	NULL);
#elif defined(USE_WINDOWS)
      SendMessage(display[lastcursor],BM_SETSTATE,TRUE,0);
#else /* USE_ */
#error Toolkit cursor not specified
#endif /* USE_ */
    }
  }

  if (text && wmemcmp(text,displayedVisual,brl->textRows*brl->textColumns)) {
    for (i=0;i<brl->textRows*brl->textColumns;i++) {
      if (displayedVisual[i] != text[i]) {
	wc = text[i];
	if (wc == 0) wc = WC_C(' ');
#ifdef USE_XM
	if (wc < 0x100)
	  data[0] = wc;
	else
	  data[0] = '?';
	data[1] = 0;
#elif defined(USE_XAW)
	convertWcharToUtf8(wc, utf8);
#elif defined(USE_WINDOWS)
	data[0] = wc;
	if (data[0]==WC_C('&')) {
	  data[1] = WC_C('&');
	  data[2] = 0;
	} else
	  data[1]=0;
#else /* USE_ */
#error Toolkit cursor not specified
#endif /* USE_ */

#if defined(USE_XT)
#ifdef USE_XM
	display_cs = XmStringCreateLocalized(data);
#endif /* USE_XM */
	XtVaSetValues(display[i],
#ifdef USE_XAW
	  XtNlabel, utf8,
#else /* USE_XAW */
	  XmNlabelString, display_cs,
#endif /* USE_XAW */
	  NULL);
#ifdef USE_XM
	XmStringFree(display_cs);
#endif /* USE_XM */
#elif defined(USE_WINDOWS)
	SetWindowTextW(display[i],data);
#else /* USE_ */
#error Toolkit display refresh unspecified
#endif /* USE_ */
	displayedVisual[i] = text[i];
      }
    }
  }

#if defined(USE_XAW) || defined(USE_WINDOWS)
  if (!cellsHaveChanged(displayedWindow,brl->buffer,brl->textRows*brl->textColumns,&from,&to,NULL) || !displayb[0]) return 1;

  for (i=from;i<to;i++) {
    unsigned char c = brl->buffer[i];
    c =
       (!!(c&BRL_DOT1))<<0
      |(!!(c&BRL_DOT2))<<1
      |(!!(c&BRL_DOT3))<<2
      |(!!(c&BRL_DOT4))<<3
      |(!!(c&BRL_DOT5))<<4
      |(!!(c&BRL_DOT6))<<5
      |(!!(c&BRL_DOT7))<<6
      |(!!(c&BRL_DOT8))<<7;
#ifdef USE_XAW
    convertWcharToUtf8(UNICODE_BRAILLE_ROW | c, utf8);

    XtVaSetValues(displayb[i], XtNlabel, utf8, NULL);
#elif defined(USE_WINDOWS)
    data[0] = UNICODE_BRAILLE_ROW | c;
    data[1] = 0;
    SetWindowTextW(displayb[i],data);
#endif /* USE_WINDOWS */
  }
#endif /* USE_XAW || USE_WINDOWS */
  return 1;
}
