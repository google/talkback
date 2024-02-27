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

/* LogText/braille.h - Configurable definitions for the LogText driver
 * Dave Mielke <dave@mielke.cc> (October 2001)
 *
 * Edit as necessary for your system.
 */

/* KEY_COMMAND commands */
#define KEY_COMMAND 0X9F /* dots-37 */
#define KEY_COMMAND_SWITCHVT_PREV 0X2D /* '-' dots-368 */
#define KEY_COMMAND_SWITCHVT_NEXT 0X2B /* '+' dots-2358 */
#define KEY_COMMAND_SWITCHVT_1    0X31 /* '1' dots-18 */
#define KEY_COMMAND_SWITCHVT_2    0X32 /* '2' dots-128 */
#define KEY_COMMAND_SWITCHVT_3    0X33 /* '3' dots-148 */
#define KEY_COMMAND_SWITCHVT_4    0X34 /* '4' dots-1458 */
#define KEY_COMMAND_SWITCHVT_5    0X35 /* '5' dots-158 */
#define KEY_COMMAND_SWITCHVT_6    0X36 /* '6' dots-1248 */
#define KEY_COMMAND_SWITCHVT_7    0X37 /* '7' dots-12458 */
#define KEY_COMMAND_SWITCHVT_8    0X38 /* '8' dots-1258 */
#define KEY_COMMAND_SWITCHVT_9    0X39 /* '9' dots-248 */
#define KEY_COMMAND_SWITCHVT_10   0X30 /* '0' dots-2458 */
#define KEY_COMMAND_PAGE_UP       0X75 /* 'u' dots-136 */
#define KEY_COMMAND_PAGE_DOWN     0X64 /* 'd' dots-145 */
#define KEY_COMMAND_FREEZE_OFF    0X66 /* 'f' dots-124 */
#define KEY_COMMAND_FREEZE_ON     0X46 /* 'F' dots-1247 */
#define KEY_COMMAND_INFO          0X49 /* 'I' dots-247 */
#define KEY_COMMAND_PREFMENU      0X50 /* 'P' dots-12347 */
#define KEY_COMMAND_PREFSAVE      0X53 /* 'S' dots-2347 */
#define KEY_COMMAND_PREFLOAD      0X4C /* 'L' dots-1237 */
#define KEY_COMMAND_RESTARTBRL    0X52 /* 'R' dots-12357 */
#define KEY_COMMAND_DOWNLOAD      0X44 /* 'D' dots-1457 */

#define KEY_SHIFT 8
#define KEY_MASK ((1 << KEY_SHIFT) - 1)
#define COMPOUND_KEY(key,arg) ((key) | ((arg) << KEY_SHIFT))

/* function keys */
#define KEY_FUNCTION 0X00
#define FUNCTION_KEY(arg) COMPOUND_KEY(KEY_FUNCTION, arg)
#define KEY_FUNCTION_ENTER             FUNCTION_KEY(0X1C) /* Enter          p 6,8 L/R */
#define KEY_FUNCTION_CURSOR_LEFT_JUMP  FUNCTION_KEY(0X47)
#define KEY_FUNCTION_CURSOR_UP         FUNCTION_KEY(0X48)
#define KEY_FUNCTION_CURSOR_UP_JUMP    FUNCTION_KEY(0X49)
#define KEY_FUNCTION_CURSOR_LEFT       FUNCTION_KEY(0X4B)
#define KEY_FUNCTION_CURSOR_RIGHT      FUNCTION_KEY(0X4D)
#define KEY_FUNCTION_CURSOR_RIGHT_JUMP FUNCTION_KEY(0X4F)
#define KEY_FUNCTION_CURSOR_DOWN       FUNCTION_KEY(0X50)
#define KEY_FUNCTION_CURSOR_DOWN_JUMP  FUNCTION_KEY(0X51)
#define KEY_FUNCTION_F1                FUNCTION_KEY(0X78) /* F1 (1)         p 1,8 L/R */
#define KEY_FUNCTION_F2                FUNCTION_KEY(0X79) /* F2 (2)         p 1,2,8 L/R */
#define KEY_FUNCTION_F3                FUNCTION_KEY(0X7A) /* F3 (3)         p 1,4,8 L/R */
#define KEY_FUNCTION_F4                FUNCTION_KEY(0X7B) /* F4 (4)         p 1,4,5,8 L/R */
#define KEY_FUNCTION_F5                FUNCTION_KEY(0X7C) /* F5 (5)         p 1,5,8 L/R */
#define KEY_FUNCTION_F6                FUNCTION_KEY(0X7D) /* F6 (6)         p 1,2,4,8 L/R */
#define KEY_FUNCTION_F7                FUNCTION_KEY(0X7E) /* F7 (7)         p 1,2,4,5,8 L/R */
#define KEY_FUNCTION_F8              //FUNCTION_KEY(0X7E) /* F8 (8)         p 1,2,5,8 L/R */
#define KEY_FUNCTION_F9                FUNCTION_KEY(0X7F) /* F9 (9)         p 2,4,8 L/R */
#define KEY_FUNCTION_F10               FUNCTION_KEY(0X81) /* F10 (0)        p 2,4,5,8 L/R */
#define KEY_FUNCTION_TAB               FUNCTION_KEY(0XA5) /* Tab            p 8  L/R */
// The following are defined in the manual but don't appear to work.
#define KEY_FUNCTION_F11             //FUNCTION_KEY(0XXX) /* F11 (k + p 8)  p 1,3,8 L/R */
#define KEY_FUNCTION_F12             //FUNCTION_KEY(0XXX) /* F12 (l + p 8)  p 1,2,3,8 L/R */
#define KEY_FUNCTION_HOME            //FUNCTION_KEY(0XXX) /* Home (M)       p 1,3,4,7 L/R */
#define KEY_FUNCTION_UP_ARROW        //FUNCTION_KEY(0XXX) /* Up arrow (U)   p 1,3,6,7 L/R */
#define KEY_FUNCTION_PAGE_UP         //FUNCTION_KEY(0XXX) /* Page up (P)    p 1,2,3,4,7 L/R */
#define KEY_FUNCTION_LEFT_ARROW      //FUNCTION_KEY(0XXX) /* Left arrow (V) p 1,2,3,6,7 L/R */
#define KEY_FUNCTION_RIGHT_ARROW     //FUNCTION_KEY(0XXX) /* Right arrow(R) p 1,2,3,5,7 L/R */
#define KEY_FUNCTION_ENDN            //FUNCTION_KEY(0XXX) /* End (N)        p 1,3,4,5,7 L/R */
#define KEY_FUNCTION_DOWN_ARROW      //FUNCTION_KEY(0XXX) /* Down arrow (W) p 2,4,5,6,7 L/R */
#define KEY_FUNCTION_PAGE_DOWN       //FUNCTION_KEY(0XXX) /* Page down (O)  p 1,3,5,7 L/R */
#define KEY_FUNCTION_DELETE          //FUNCTION_KEY(0XXX) /* Delete (T)     p 2,3,4,5,7 L/R */
#define KEY_FUNCTION_LEFT_ARROW_GRAY //FUNCTION_KEY(0XXX) /* Left arrow, gr p 1,2,3,6,7,8 L/R */
#define KEY_FUNCTION_PRINT_SCREEN    //FUNCTION_KEY(0XXX) /* Print screen   p 2,4,6,8 L/R */
#define KEY_FUNCTION_PAUSE_BREAK     //FUNCTION_KEY(0XXX) /* Pause/break    p 4,5,6,8 L/R */
#define KEY_FUNCTION_BACKSPACE       //FUNCTION_KEY(0XXX) /* Backspace      p 1,3,5,8 L/R */
#define KEY_FUNCTION_NULL            //FUNCTION_KEY(0XXX) /* Null           p 7 L/R */

#define KEY_FUNCTION2 0XE0
#define FUNCTION2_KEY(arg) COMPOUND_KEY(KEY_FUNCTION2, arg)
#define KEY_FUNCTION_INSERT            FUNCTION2_KEY(0X97) /* Insert (S)     p 2,3,4,7 L/R */
#define KEY_FUNCTION_HOME_GRAY       //FUNCTION2_KEY(0X97) /* Home, gray     p 1,3,4,7,8 L/R */
#define KEY_FUNCTION_UP_ARROW_GRAY     FUNCTION2_KEY(0X98) /* Up arrow, gray p 1,3,6,7,8 L/R */
#define KEY_FUNCTION_PAGE_UP_GRAY      FUNCTION2_KEY(0X99) /* Page up, gray  p 1,2,3,4,7,8 L/R */
#define KEY_FUNCTION_DOWN_ARROW_GRAY   FUNCTION2_KEY(0X9A) /* Down arrow, gr p 2,4,5,6,7,8 L/R */
#define KEY_FUNCTION_RIGHT_ARROW_GRAY  FUNCTION2_KEY(0X9D) /* Right arrow,gr p 1,2,3,5,7,8 L/R */
#define KEY_FUNCTION_END_GRAY          FUNCTION2_KEY(0X9F) /* End, gray      p 1,3,4,5,7,8 L/R */
#define KEY_FUNCTION_PAGE_DOWN_GRAY    FUNCTION2_KEY(0XA1) /* Page down,gray p 1,3,5,7,8 L/R */
#define KEY_FUNCTION_INSERT_GRAY       FUNCTION2_KEY(0XA2) /* Insert, gray   p 2,3,4,7,8 L/R */
#define KEY_FUNCTION_DELETE_GRAY       FUNCTION2_KEY(0XA3) /* Delete, gray   p 2,3,4,5,7,8 L/R */

/* Update screen, not a key */
#define KEY_UPDATE 0XFF
