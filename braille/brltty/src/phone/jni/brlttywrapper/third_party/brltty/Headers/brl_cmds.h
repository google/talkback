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

#ifndef BRLTTY_INCLUDED_BRL_CMDS
#define BRLTTY_INCLUDED_BRL_CMDS

#include "brl_dots.h"

#ifdef __cplusplus
extern "C" {
#endif /* __cplusplus */

#define BRL_BITS_ARG 8
#define BRL_BITS_BLK 8
#define BRL_BITS_FLG 8
#define BRL_BITS_EXT 8

#define BRL_SHIFT_ARG 0
#define BRL_SHIFT_BLK (BRL_SHIFT_ARG + BRL_BITS_ARG)
#define BRL_SHIFT_FLG (BRL_SHIFT_BLK + BRL_BITS_BLK)
#define BRL_SHIFT_EXT (BRL_SHIFT_FLG + BRL_BITS_FLG)

#define BRL_CODE_MASK(name) ((1 << BRL_BITS_##name) - 1)
#define BRL_CODE_GET(name,code) (((code) >> BRL_SHIFT_##name) & BRL_CODE_MASK(name))
#define BRL_CODE_PUT(name,value) ((value) << BRL_SHIFT_##name)
#define BRL_CODE_SET(name,value) BRL_CODE_PUT(name, ((value) & BRL_CODE_MASK(name)))

#define BRL_ARG_PUT(arg) BRL_CODE_PUT(ARG, (arg))
#define BRL_BLK_PUT(blk) BRL_CODE_PUT(BLK, (blk))
#define BRL_FLG_PUT(fLG) BRL_CODE_PUT(FLG, (flg))
#define BRL_EXT_PUT(ext) BRL_CODE_PUT(EXT, (ext))

#define BRL_MSK(name) BRL_CODE_PUT(name, BRL_CODE_MASK(name))
#define BRL_MSK_ARG BRL_MSK(ARG)
#define BRL_MSK_BLK BRL_MSK(BLK)
#define BRL_MSK_FLG BRL_MSK(FLG)
#define BRL_MSK_EXT BRL_MSK(EXT)
#define BRL_MSK_CMD (BRL_MSK_BLK | BRL_MSK_ARG)

#define BRL_ARG_GET(code) (BRL_CODE_GET(ARG, (code)) | (BRL_CODE_GET(EXT, (code)) << BRL_BITS_ARG))
#define BRL_ARG_SET(value) (BRL_CODE_SET(ARG, (value)) | BRL_CODE_SET(EXT, ((value) >> BRL_BITS_ARG)))

/* The following command codes are return values for brl_readCommand().
 * The BRL_CMD_* codes are guaranteed to be between 1 and 255
 * inclusive, with the exception of BRL_CMD_NOOP = 0.
 *
 * brl_readCommand() should return EOF if there are no keys to return.  If,
 * however, if it has masked out the key for internal use, it may return
 * BRL_CMD_NOOP - in which case it will be called again immediately, rather
 * than waiting for the next cycle.  This can save internal nesting ...
 */

/*
 * Please comment all BRL_CMD_* definitions. They are
 * used during automatic command table generation.
 */

typedef enum {
  /* special commands which must be first and remain in order */
  BRL_CMD_NOOP = 0 /* do nothing */,

  /* vertical motion */
  BRL_CMD_LNUP /* go up one line */,
  BRL_CMD_LNDN /* go down one line */,
  BRL_CMD_WINUP /* go up several lines */,
  BRL_CMD_WINDN /* go down several lines */,
  BRL_CMD_PRDIFLN /* go up to nearest line with different content */,
  BRL_CMD_NXDIFLN /* go down to nearest line with different content */,
  BRL_CMD_ATTRUP /* go up to nearest line with different highlighting */,
  BRL_CMD_ATTRDN /* go down to nearest line with different highlighting */,
  BRL_CMD_TOP /* go to top line */,
  BRL_CMD_BOT /* go to bottom line */,
  BRL_CMD_TOP_LEFT /* go to beginning of top line */,
  BRL_CMD_BOT_LEFT /* go to beginning of bottom line */,
  BRL_CMD_PRPGRPH /* go up to first line of paragraph */,
  BRL_CMD_NXPGRPH /* go down to first line of next paragraph */,
  BRL_CMD_PRPROMPT /* go up to previous command prompt */,
  BRL_CMD_NXPROMPT /* go down to next command prompt */,
  BRL_CMD_PRSEARCH /* search backward for clipboard text */,
  BRL_CMD_NXSEARCH /* search forward for clipboard text */,
  
  /* horizontal motion */
  BRL_CMD_CHRLT /* go left one character */,
  BRL_CMD_CHRRT /* go right one character */,
  BRL_CMD_HWINLT /* go left half a braille window */,
  BRL_CMD_HWINRT /* go right half a braille window */,
  BRL_CMD_FWINLT /* go backward one braille window */,
  BRL_CMD_FWINRT /* go forward one braille window */,
  BRL_CMD_FWINLTSKIP /* go backward skipping blank braille windows */,
  BRL_CMD_FWINRTSKIP /* go forward skipping blank braille windows */,
  BRL_CMD_LNBEG /* go to beginning of line */,
  BRL_CMD_LNEND /* go to end of line */,
  
  /* implicit motion */
  BRL_CMD_HOME /* go to screen cursor */,
  BRL_CMD_BACK /* go back after cursor tracking */,
  BRL_CMD_RETURN /* go to screen cursor or go back after cursor tracking */,
  
  /* feature activation and deactivation */
  BRL_CMD_FREEZE /* set screen image frozen/unfrozen */,
  BRL_CMD_DISPMD /* set display mode attributes/text */,
  BRL_CMD_SIXDOTS /* set text style 6-dot/8-dot */,
  BRL_CMD_SLIDEWIN /* set sliding braille window on/off */,
  BRL_CMD_SKPIDLNS /* set skipping of lines with identical content on/off */,
  BRL_CMD_SKPBLNKWINS /* set skipping of blank braille windows on/off */,
  BRL_CMD_CSRVIS /* set screen cursor visibility on/off */,
  BRL_CMD_CSRHIDE /* set hidden screen cursor on/off */,
  BRL_CMD_CSRTRK /* set track screen cursor on/off */,
  BRL_CMD_CSRSIZE /* set screen cursor style block/underline */,
  BRL_CMD_CSRBLINK /* set screen cursor blinking on/off */,
  BRL_CMD_ATTRVIS /* set attribute underlining on/off */,
  BRL_CMD_ATTRBLINK /* set attribute blinking on/off */,
  BRL_CMD_CAPBLINK /* set capital letter blinking on/off */,
  BRL_CMD_TUNES /* set alert tunes on/off */,
  BRL_CMD_AUTOREPEAT /* set autorepeat on/off */,
  BRL_CMD_AUTOSPEAK /* set autospeak on/off */,
 
  /* mode selection */
  BRL_CMD_HELP /* enter/leave help display */,
  BRL_CMD_INFO /* enter/leave status display */,
  BRL_CMD_LEARN /* enter/leave command learn mode */,
  
  /* preference setting */
  BRL_CMD_PREFMENU /* enter/leave preferences menu */,
  BRL_CMD_PREFSAVE /* save preferences to disk */,
  BRL_CMD_PREFLOAD /* restore preferences from disk */,
  
  /* menu navigation */
  BRL_CMD_MENU_FIRST_ITEM /* go up to first item */,
  BRL_CMD_MENU_LAST_ITEM /* go down to last item */,
  BRL_CMD_MENU_PREV_ITEM /* go up to previous item */,
  BRL_CMD_MENU_NEXT_ITEM /* go down to next item */,
  BRL_CMD_MENU_PREV_SETTING /* select previous choice */,
  BRL_CMD_MENU_NEXT_SETTING /* select next choice */,
 
  /* speech controls */
  BRL_CMD_MUTE /* stop speaking */,
  BRL_CMD_SPKHOME /* go to current speaking position */,
  BRL_CMD_SAY_LINE /* speak current line */,
  BRL_CMD_SAY_ABOVE /* speak from top of screen through current line */,
  BRL_CMD_SAY_BELOW /* speak from current line through bottom of screen */,
  BRL_CMD_SAY_SLOWER /* decrease speaking rate */,
  BRL_CMD_SAY_FASTER /* increase speaking rate */,
  BRL_CMD_SAY_SOFTER /* decrease speaking volume */,
  BRL_CMD_SAY_LOUDER /* increase speaking volume */,
  
  /* virtual terminal switching */
  BRL_CMD_SWITCHVT_PREV /* switch to the previous virtual terminal */,
  BRL_CMD_SWITCHVT_NEXT /* switch to the next virtual terminal */,
  
  /* miscellaneous */
  BRL_CMD_CSRJMP_VERT /* bring screen cursor to current line */,
  BRL_CMD_PASTE /* insert clipboard text after screen cursor */,
  BRL_CMD_RESTARTBRL /* restart braille driver */,
  BRL_CMD_RESTARTSPEECH /* restart speech driver */,

  BRL_CMD_OFFLINE /* braille display temporarily unavailable */,

  BRL_CMD_SHIFT /* cycle the Shift sticky input modifier (next, on, off) */,
  BRL_CMD_UPPER /* cycle the Upper sticky input modifier (next, on, off) */,
  BRL_CMD_CONTROL /* cycle the Control sticky input modifier (next, on, off) */,
  BRL_CMD_META /* cycle the Meta (Left Alt) sticky input modifier (next, on, off) */,

  BRL_CMD_TIME /* show current date and time */,
  BRL_CMD_MENU_PREV_LEVEL /* go to previous menu level */,

  BRL_CMD_ASPK_SEL_LINE /* set autospeak selected line on/off */,
  BRL_CMD_ASPK_SEL_CHAR /* set autospeak selected character on/off */,
  BRL_CMD_ASPK_INS_CHARS /* set autospeak inserted characters on/off */,
  BRL_CMD_ASPK_DEL_CHARS /* set autospeak deleted characters on/off */,
  BRL_CMD_ASPK_REP_CHARS /* set autospeak replaced characters on/off */,
  BRL_CMD_ASPK_CMP_WORDS /* set autospeak completed words on/off */,

  BRL_CMD_SPEAK_CURR_CHAR /* speak current character */,
  BRL_CMD_SPEAK_PREV_CHAR /* go to and speak previous character */,
  BRL_CMD_SPEAK_NEXT_CHAR /* go to and speak next character */,
  BRL_CMD_SPEAK_CURR_WORD /* speak current word */,
  BRL_CMD_SPEAK_PREV_WORD /* go to and speak previous word */,
  BRL_CMD_SPEAK_NEXT_WORD /* go to and speak next word */,
  BRL_CMD_SPEAK_CURR_LINE /* speak current line */,
  BRL_CMD_SPEAK_PREV_LINE /* go to and speak previous line */,
  BRL_CMD_SPEAK_NEXT_LINE /* go to and speak next line */,
  BRL_CMD_SPEAK_FRST_CHAR /* go to and speak first non-blank character on line */,
  BRL_CMD_SPEAK_LAST_CHAR /* go to and speak last non-blank character on line */,
  BRL_CMD_SPEAK_FRST_LINE /* go to and speak first non-blank line on screen */,
  BRL_CMD_SPEAK_LAST_LINE /* go to and speak last non-blank line on screen */,
  BRL_CMD_DESC_CURR_CHAR /* describe current character */,
  BRL_CMD_SPELL_CURR_WORD /* spell current word */,
  BRL_CMD_ROUTE_CURR_LOCN /* bring screen cursor to speech cursor */,
  BRL_CMD_SPEAK_CURR_LOCN /* speak speech cursor location */,
  BRL_CMD_SHOW_CURR_LOCN /* set speech cursor visibility on/off */,

  BRL_CMD_CLIP_SAVE /* save clipboard to disk */,
  BRL_CMD_CLIP_RESTORE /* restore clipboard from disk */,

  BRL_CMD_BRLUCDOTS /* set braille input mode dots/text */,
  BRL_CMD_BRLKBD /* set braille keyboard enabled/disabled */,

  BRL_CMD_UNSTICK /* clear all sticky input modifiers */,
  BRL_CMD_ALTGR /* cycle the AltGr (Right Alt) sticky input modifier (next, on, off) */,
  BRL_CMD_GUI /* cycle the GUI (Windows) sticky input modifier (next, on, off) */,

  BRL_CMD_BRL_STOP /* stop the braille driver */,
  BRL_CMD_BRL_START /* start the braille driver */,
  BRL_CMD_SPK_STOP /* stop the speech driver */,
  BRL_CMD_SPK_START /* start the speech driver */,
  BRL_CMD_SCR_STOP /* stop the screen driver */,
  BRL_CMD_SCR_START /* start the screen driver */,

  BRL_CMD_SELECTVT_PREV /* bind to the previous virtual terminal */,
  BRL_CMD_SELECTVT_NEXT /* bind to the next virtual terminal */,

  BRL_CMD_PRNBWIN /* go backward to nearest non-blank braille window */,
  BRL_CMD_NXNBWIN /* go forward to nearest non-blank braille window */,

  BRL_CMD_TOUCH_NAV /* set touch navigation on/off */,

  BRL_CMD_SPEAK_INDENT /* speak indent of current line */,
  BRL_CMD_ASPK_INDENT /* set autospeak indent of current line on/off */,

  BRL_basicCommandCount /* must be last */
} BRL_BasicCommand;

/*
 * Please comment all BRL_BLK_* definitions. They are
 * used during automatic help file generation.
 */
typedef enum {
  BRL_BLK_BASIC = 0 /* (must be first) */,
  BRL_BLK_ROUTE /* bring screen cursor to character */,
  BRL_BLK_CLIP_NEW /* start new clipboard at character */,
  BRL_BLK_CLIP_ADD /* append to clipboard from character */,
  BRL_BLK_COPY_RECT /* rectangular copy to character */,
  BRL_BLK_COPY_LINE /* linear copy to character */,
  BRL_BLK_SWITCHVT /* switch to specific virtual terminal */,
  BRL_BLK_PRINDENT /* go up to nearest line with less indent than character */,
  BRL_BLK_NXINDENT /* go down to nearest line with less indent than character */,
  BRL_BLK_DESCCHAR /* describe character */,
  BRL_BLK_SETLEFT /* place left end of braille window at character */,
  BRL_BLK_SETMARK /* remember current braille window position */,
  BRL_BLK_GOTOMARK /* go to remembered braille window position */,
  BRL_BLK_GOTOLINE /* go to selected line */,
  BRL_BLK_PRDIFCHAR /* go up to nearest line with different character */,
  BRL_BLK_NXDIFCHAR /* go down to nearest line with different character */,
  BRL_BLK_CLIP_COPY /* copy characters to clipboard */,
  BRL_BLK_CLIP_APPEND /* append characters to clipboard */,
  BRL_BLK_PASTE_HISTORY /* insert clipboard history entry after screen cursor */,
  BRL_BLK_SET_TEXT_TABLE /* set text table */,
  BRL_BLK_SET_ATTRIBUTES_TABLE /* set attributes table */,
  BRL_BLK_SET_CONTRACTION_TABLE /* set contraction table */,
  BRL_BLK_SET_KEYBOARD_TABLE /* set keyboard table */,
  BRL_BLK_SET_LANGUAGE_PROFILE /* set language profile */,
  BRL_BLK_18 /* (reserved) */,
  BRL_BLK_19 /* (reserved) */,
  BRL_BLK_1A /* (reserved) */,
  BRL_BLK_1B /* (reserved) */,
  BRL_BLK_1C /* (reserved) */,
  BRL_BLK_1D /* (reserved) */,
  BRL_BLK_SELECTVT /* bind to specific virtual terminal */,
  BRL_BLK_ALERT /* render an alert */,
  BRL_BLK_PASSKEY /* (emulate special key) */,
  BRL_BLK_PASSCHAR /* type unicode character */,
  BRL_BLK_PASSDOTS /* type braille dots */,
  BRL_BLK_PASSAT /* AT (set 2) keyboard scan code */,
  BRL_BLK_PASSXT /* XT (set 1) keyboard scan code */,
  BRL_BLK_PASSPS2 /* PS/2 (set 3) keyboard scan code */,
  BRL_BLK_CONTEXT /* switch to command context */,
  BRL_BLK_TOUCH_AT /* current reading location */,

  BRL_blockCommandCount /* must be last */
} BRL_BlockCommand;

typedef enum {
  BRL_KEY_ENTER,
  BRL_KEY_TAB,
  BRL_KEY_BACKSPACE,
  BRL_KEY_ESCAPE,
  BRL_KEY_CURSOR_LEFT,
  BRL_KEY_CURSOR_RIGHT,
  BRL_KEY_CURSOR_UP,
  BRL_KEY_CURSOR_DOWN,
  BRL_KEY_PAGE_UP,
  BRL_KEY_PAGE_DOWN,
  BRL_KEY_HOME,
  BRL_KEY_END,
  BRL_KEY_INSERT,
  BRL_KEY_DELETE,
  BRL_KEY_FUNCTION
} BRL_Key;

#define BRL_CMD_BLK(cmd) (BRL_BLK_##cmd << BRL_SHIFT_BLK)
#define BRL_CMD_ARG(blk,arg) (BRL_CMD_BLK(blk) | BRL_ARG_SET((arg)))
#define BRL_CMD_CHAR(wc) BRL_CMD_ARG(PASSCHAR, (wc))
#define BRL_CMD_KEY(key) BRL_CMD_ARG(PASSKEY, (BRL_KEY_##key))
#define BRL_CMD_KFN(n) BRL_CMD_KEY(FUNCTION + ((n) - 1))

#define BRL_FLG_TOGGLE_ON   0X010000 /* enable feature */
#define BRL_FLG_TOGGLE_OFF  0X020000 /* disable feature */
#define BRL_FLG_TOGGLE_MASK (BRL_FLG_TOGGLE_ON | BRL_FLG_TOGGLE_OFF) /* mask for all toggle flags */

#define BRL_FLG_MOTION_ROUTE  0X040000 /* bring screen cursor into braille window after function */
#define BRL_FLG_MOTION_SCALED 0X080000 /* scale arg=0X00-0XFF to screen height */
#define BRL_FLG_MOTION_TOLEFT 0X100000 /* go to beginning of line */

#define BRL_FLG_INPUT_SHIFT   0X010000 /* shift key pressed */
#define BRL_FLG_INPUT_UPPER   0X020000 /* convert to uppercase */
#define BRL_FLG_INPUT_CONTROL 0X040000 /* control key pressed */
#define BRL_FLG_INPUT_META    0X080000 /* meta (left alt) key pressed */
#define BRL_FLG_INPUT_ALTGR   0X100000 /* altgr (right alt) key pressed */
#define BRL_FLG_INPUT_GUI     0X200000 /* gui (windows) key pressed */

#define BRL_FLG_KBD_RELEASE 0X010000 /* it is a release scan code */
#define BRL_FLG_KBD_EMUL0   0X020000 /* it is an emulation 0 scan code */
#define BRL_FLG_KBD_EMUL1   0X040000 /* it is an emulation 1 scan code */

/* The bits for each braille dot.
 *
 * This is the same dot-to-bit mapping which is:
 * +  specified by the ISO/TR 11548-1 standard
 * +  used within the Unicode braille row:
 */
/*
 * From least- to most-significant octal digit:
 * +  the first contains dots 1-3
 * +  the second contains dots 4-6
 * +  the third contains dots 7-8
 * 
 * Here are a few ways to illustrate a braille cell:
 *    By Dot   By Bit   As Octal
 *    Number   Number    Digits
 *     1  4     0  3    001  010
 *     2  5     1  4    002  020
 *     3  6     2  5    004  040
 *     7  8     6  7    100  200
 */
#define BRL_ARG_DOT(n) BRL_ARG_SET(1 << ((n) - 1))
#define BRL_DOT1 BRL_ARG_DOT(1) /* upper-left dot of standard braille cell */
#define BRL_DOT2 BRL_ARG_DOT(2) /* middle-left dot of standard braille cell */
#define BRL_DOT3 BRL_ARG_DOT(3) /* lower-left dot of standard braille cell */
#define BRL_DOT4 BRL_ARG_DOT(4) /* upper-right dot of standard braille cell */
#define BRL_DOT5 BRL_ARG_DOT(5) /* middle-right dot of standard braille cell */
#define BRL_DOT6 BRL_ARG_DOT(6) /* lower-right dot of standard braille cell */
#define BRL_DOT7 BRL_ARG_DOT(7) /* lower-left dot of computer braille cell */
#define BRL_DOT8 BRL_ARG_DOT(8) /* lower-right dot of computer braille cell */
#define BRL_DOTC BRL_ARG_DOT(9) /* chord (space bar on braille keyboard) */

#define BRL_ALL_DOTS ( \
  BRL_DOT1 | BRL_DOT2 | BRL_DOT3 | BRL_DOT4 | \
  BRL_DOT5 | BRL_DOT6 | BRL_DOT7 | BRL_DOT8 \
)

#ifdef __cplusplus
}
#endif /* __cplusplus */

#endif /* BRLTTY_INCLUDED_BRL_CMDS */
