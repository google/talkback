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

#include <string.h>

#include "hidkeys.h"
#include "kbd_keycodes.h"
#include "bitmask.h"
#include "brl_cmds.h"
#include "cmd_enqueue.h"

typedef struct {
  uint16_t xtCode;
  uint16_t atCode;
  uint8_t ps2Code;
} HidKeyEntry;

static const HidKeyEntry hidKeyTable[] = {
  /* aA */
  [HID_KEY_A] = {
    .xtCode = XT_KEY(00, A),
    .atCode = AT_KEY(00, A),
    .ps2Code = PS2_KEY_A
  },

  /* bB */
  [HID_KEY_B] = {
    .xtCode = XT_KEY(00, B),
    .atCode = AT_KEY(00, B),
    .ps2Code = PS2_KEY_B
  },

  /* cC */
  [HID_KEY_C] = {
    .xtCode = XT_KEY(00, C),
    .atCode = AT_KEY(00, C),
    .ps2Code = PS2_KEY_C
  },

  /* dD */
  [HID_KEY_D] = {
    .xtCode = XT_KEY(00, D),
    .atCode = AT_KEY(00, D),
    .ps2Code = PS2_KEY_D
  },

  /* eE */
  [HID_KEY_E] = {
    .xtCode = XT_KEY(00, E),
    .atCode = AT_KEY(00, E),
    .ps2Code = PS2_KEY_E
  },

  /* fF */
  [HID_KEY_F] = {
    .xtCode = XT_KEY(00, F),
    .atCode = AT_KEY(00, F),
    .ps2Code = PS2_KEY_F
  },

  /* gG */
  [HID_KEY_G] = {
    .xtCode = XT_KEY(00, G),
    .atCode = AT_KEY(00, G),
    .ps2Code = PS2_KEY_G
  },

  /* hH */
  [HID_KEY_H] = {
    .xtCode = XT_KEY(00, H),
    .atCode = AT_KEY(00, H),
    .ps2Code = PS2_KEY_H
  },

  /* iI */
  [HID_KEY_I] = {
    .xtCode = XT_KEY(00, I),
    .atCode = AT_KEY(00, I),
    .ps2Code = PS2_KEY_I
  },

  /* jJ */
  [HID_KEY_J] = {
    .xtCode = XT_KEY(00, J),
    .atCode = AT_KEY(00, J),
    .ps2Code = PS2_KEY_J
  },

  /* kK */
  [HID_KEY_K] = {
    .xtCode = XT_KEY(00, K),
    .atCode = AT_KEY(00, K),
    .ps2Code = PS2_KEY_K
  },

  /* lL */
  [HID_KEY_L] = {
    .xtCode = XT_KEY(00, L),
    .atCode = AT_KEY(00, L),
    .ps2Code = PS2_KEY_L
  },

  /* mM */
  [HID_KEY_M] = {
    .xtCode = XT_KEY(00, M),
    .atCode = AT_KEY(00, M),
    .ps2Code = PS2_KEY_M
  },

  /* nN */
  [HID_KEY_N] = {
    .xtCode = XT_KEY(00, N),
    .atCode = AT_KEY(00, N),
    .ps2Code = PS2_KEY_N
  },

  /* oO */
  [HID_KEY_O] = {
    .xtCode = XT_KEY(00, O),
    .atCode = AT_KEY(00, O),
    .ps2Code = PS2_KEY_O
  },

  /* pP */
  [HID_KEY_P] = {
    .xtCode = XT_KEY(00, P),
    .atCode = AT_KEY(00, P),
    .ps2Code = PS2_KEY_P
  },

  /* qQ */
  [HID_KEY_Q] = {
    .xtCode = XT_KEY(00, Q),
    .atCode = AT_KEY(00, Q),
    .ps2Code = PS2_KEY_Q
  },

  /* rR */
  [HID_KEY_R] = {
    .xtCode = XT_KEY(00, R),
    .atCode = AT_KEY(00, R),
    .ps2Code = PS2_KEY_R
  },

  /* sS */
  [HID_KEY_S] = {
    .xtCode = XT_KEY(00, S),
    .atCode = AT_KEY(00, S),
    .ps2Code = PS2_KEY_S
  },

  /* tT */
  [HID_KEY_T] = {
    .xtCode = XT_KEY(00, T),
    .atCode = AT_KEY(00, T),
    .ps2Code = PS2_KEY_T
  },

  /* uU */
  [HID_KEY_U] = {
    .xtCode = XT_KEY(00, U),
    .atCode = AT_KEY(00, U),
    .ps2Code = PS2_KEY_U
  },

  /* vV */
  [HID_KEY_V] = {
    .xtCode = XT_KEY(00, V),
    .atCode = AT_KEY(00, V),
    .ps2Code = PS2_KEY_V
  },

  /* wW */
  [HID_KEY_W] = {
    .xtCode = XT_KEY(00, W),
    .atCode = AT_KEY(00, W),
    .ps2Code = PS2_KEY_W
  },

  /* xX */
  [HID_KEY_X] = {
    .xtCode = XT_KEY(00, X),
    .atCode = AT_KEY(00, X),
    .ps2Code = PS2_KEY_X
  },

  /* yY */
  [HID_KEY_Y] = {
    .xtCode = XT_KEY(00, Y),
    .atCode = AT_KEY(00, Y),
    .ps2Code = PS2_KEY_Y
  },

  /* zZ */
  [HID_KEY_Z] = {
    .xtCode = XT_KEY(00, Z),
    .atCode = AT_KEY(00, Z),
    .ps2Code = PS2_KEY_Z
  },

  /* 1! */
  [HID_KEY_1] = {
    .xtCode = XT_KEY(00, 1),
    .atCode = AT_KEY(00, 1),
    .ps2Code = PS2_KEY_1
  },

  /* 2@ */
  [HID_KEY_2] = {
    .xtCode = XT_KEY(00, 2),
    .atCode = AT_KEY(00, 2),
    .ps2Code = PS2_KEY_2
  },

  /* 3# */
  [HID_KEY_3] = {
    .xtCode = XT_KEY(00, 3),
    .atCode = AT_KEY(00, 3),
    .ps2Code = PS2_KEY_3
  },

  /* 4$ */
  [HID_KEY_4] = {
    .xtCode = XT_KEY(00, 4),
    .atCode = AT_KEY(00, 4),
    .ps2Code = PS2_KEY_4
  },

  /* 5% */
  [HID_KEY_5] = {
    .xtCode = XT_KEY(00, 5),
    .atCode = AT_KEY(00, 5),
    .ps2Code = PS2_KEY_5
  },

  /* 6^ */
  [HID_KEY_6] = {
    .xtCode = XT_KEY(00, 6),
    .atCode = AT_KEY(00, 6),
    .ps2Code = PS2_KEY_6
  },

  /* 7& */
  [HID_KEY_7] = {
    .xtCode = XT_KEY(00, 7),
    .atCode = AT_KEY(00, 7),
    .ps2Code = PS2_KEY_7
  },

  /* 8* */
  [HID_KEY_8] = {
    .xtCode = XT_KEY(00, 8),
    .atCode = AT_KEY(00, 8),
    .ps2Code = PS2_KEY_8
  },

  /* 9( */
  [HID_KEY_9] = {
    .xtCode = XT_KEY(00, 9),
    .atCode = AT_KEY(00, 9),
    .ps2Code = PS2_KEY_9
  },

  /* 0) */
  [HID_KEY_0] = {
    .xtCode = XT_KEY(00, 0),
    .atCode = AT_KEY(00, 0),
    .ps2Code = PS2_KEY_0
  },

  /* Return */
  [HID_KEY_Enter] = {
    .xtCode = XT_KEY(00, Enter),
    .atCode = AT_KEY(00, Enter),
    .ps2Code = PS2_KEY_Enter
  },

  /* Escape */
  [HID_KEY_Escape] = {
    .xtCode = XT_KEY(00, Escape),
    .atCode = AT_KEY(00, Escape),
    .ps2Code = PS2_KEY_Escape
  },

  /* Backspace */
  [HID_KEY_Backspace] = {
    .xtCode = XT_KEY(00, Backspace),
    .atCode = AT_KEY(00, Backspace),
    .ps2Code = PS2_KEY_Backspace
  },

  /* Tab */
  [HID_KEY_Tab] = {
    .xtCode = XT_KEY(00, Tab),
    .atCode = AT_KEY(00, Tab),
    .ps2Code = PS2_KEY_Tab
  },

  /* Space */
  [HID_KEY_Space] = {
    .xtCode = XT_KEY(00, Space),
    .atCode = AT_KEY(00, Space),
    .ps2Code = PS2_KEY_Space
  },

  /* -_ */
  [HID_KEY_Minus] = {
    .xtCode = XT_KEY(00, Minus),
    .atCode = AT_KEY(00, Minus),
    .ps2Code = PS2_KEY_Minus
  },

  /* =+ */
  [HID_KEY_Equal] = {
    .xtCode = XT_KEY(00, Equal),
    .atCode = AT_KEY(00, Equal),
    .ps2Code = PS2_KEY_Equal
  },

  /* [{ */
  [HID_KEY_LeftBracket] = {
    .xtCode = XT_KEY(00, LeftBracket),
    .atCode = AT_KEY(00, LeftBracket),
    .ps2Code = PS2_KEY_LeftBracket
  },

  /* ]} */
  [HID_KEY_RightBracket] = {
    .xtCode = XT_KEY(00, RightBracket),
    .atCode = AT_KEY(00, RightBracket),
    .ps2Code = PS2_KEY_RightBracket
  },

  /* \| */
  [HID_KEY_Backslash] = {
    .xtCode = XT_KEY(00, Backslash),
    .atCode = AT_KEY(00, Backslash),
    .ps2Code = PS2_KEY_Backslash
  },

  /* Europe 1 (Note 2) */
  [HID_KEY_Europe1] = {
    .xtCode = XT_KEY(00, Europe1),
    .atCode = AT_KEY(00, Europe1),
    .ps2Code = PS2_KEY_Europe1
  },

  /* ;: */
  [HID_KEY_Semicolon] = {
    .xtCode = XT_KEY(00, Semicolon),
    .atCode = AT_KEY(00, Semicolon),
    .ps2Code = PS2_KEY_Semicolon
  },

  /* '" */
  [HID_KEY_Apostrophe] = {
    .xtCode = XT_KEY(00, Apostrophe),
    .atCode = AT_KEY(00, Apostrophe),
    .ps2Code = PS2_KEY_Apostrophe
  },

  /* `~ */
  [HID_KEY_Grave] = {
    .xtCode = XT_KEY(00, Grave),
    .atCode = AT_KEY(00, Grave),
    .ps2Code = PS2_KEY_Grave
  },

  /* ,< */
  [HID_KEY_Comma] = {
    .xtCode = XT_KEY(00, Comma),
    .atCode = AT_KEY(00, Comma),
    .ps2Code = PS2_KEY_Comma
  },

  /* .> */
  [HID_KEY_Period] = {
    .xtCode = XT_KEY(00, Period),
    .atCode = AT_KEY(00, Period),
    .ps2Code = PS2_KEY_Period
  },

  /* /? */
  [HID_KEY_Slash] = {
    .xtCode = XT_KEY(00, Slash),
    .atCode = AT_KEY(00, Slash),
    .ps2Code = PS2_KEY_Slash
  },

  /* Caps Lock */
  [HID_KEY_CapsLock] = {
    .xtCode = XT_KEY(00, CapsLock),
    .atCode = AT_KEY(00, CapsLock),
    .ps2Code = PS2_KEY_CapsLock
  },

  /* F1 */
  [HID_KEY_F1] = {
    .xtCode = XT_KEY(00, F1),
    .atCode = AT_KEY(00, F1),
    .ps2Code = PS2_KEY_F1
  },

  /* F2 */
  [HID_KEY_F2] = {
    .xtCode = XT_KEY(00, F2),
    .atCode = AT_KEY(00, F2),
    .ps2Code = PS2_KEY_F2
  },

  /* F3 */
  [HID_KEY_F3] = {
    .xtCode = XT_KEY(00, F3),
    .atCode = AT_KEY(00, F3),
    .ps2Code = PS2_KEY_F3
  },

  /* F4 */
  [HID_KEY_F4] = {
    .xtCode = XT_KEY(00, F4),
    .atCode = AT_KEY(00, F4),
    .ps2Code = PS2_KEY_F4
  },

  /* F5 */
  [HID_KEY_F5] = {
    .xtCode = XT_KEY(00, F5),
    .atCode = AT_KEY(00, F5),
    .ps2Code = PS2_KEY_F5
  },

  /* F6 */
  [HID_KEY_F6] = {
    .xtCode = XT_KEY(00, F6),
    .atCode = AT_KEY(00, F6),
    .ps2Code = PS2_KEY_F6
  },

  /* F7 */
  [HID_KEY_F7] = {
    .xtCode = XT_KEY(00, F7),
    .atCode = AT_KEY(00, F7),
    .ps2Code = PS2_KEY_F7
  },

  /* F8 */
  [HID_KEY_F8] = {
    .xtCode = XT_KEY(00, F8),
    .atCode = AT_KEY(00, F8),
    .ps2Code = PS2_KEY_F8
  },

  /* F9 */
  [HID_KEY_F9] = {
    .xtCode = XT_KEY(00, F9),
    .atCode = AT_KEY(00, F9),
    .ps2Code = PS2_KEY_F9
  },

  /* F10 */
  [HID_KEY_F10] = {
    .xtCode = XT_KEY(00, F10),
    .atCode = AT_KEY(00, F10),
    .ps2Code = PS2_KEY_F10
  },

  /* F11 */
  [HID_KEY_F11] = {
    .xtCode = XT_KEY(00, F11),
    .atCode = AT_KEY(00, F11),
    .ps2Code = PS2_KEY_F11
  },

  /* F12 */
  [HID_KEY_F12] = {
    .xtCode = XT_KEY(00, F12),
    .atCode = AT_KEY(00, F12),
    .ps2Code = PS2_KEY_F12
  },

  /* Print Screen (Note 1) */
  [HID_KEY_PrintScreen] = {
    .xtCode = XT_KEY(E0, PrintScreen),
    .atCode = AT_KEY(E0, PrintScreen),
    .ps2Code = PS2_KEY_PrintScreen
  },

  /* Scroll Lock */
  [HID_KEY_ScrollLock] = {
    .xtCode = XT_KEY(00, ScrollLock),
    .atCode = AT_KEY(00, ScrollLock),
    .ps2Code = PS2_KEY_ScrollLock
  },

  /* Pause */
  [HID_KEY_Pause] = {
    .xtCode = XT_KEY(E1, Pause),
    .atCode = AT_KEY(E1, Pause),
    .ps2Code = PS2_KEY_Pause
  },

  /* Insert (Note 1) */
  [HID_KEY_Insert] = {
    .xtCode = XT_KEY(E0, Insert),
    .atCode = AT_KEY(E0, Insert),
    .ps2Code = PS2_KEY_Insert
  },

  /* Home (Note 1) */
  [HID_KEY_Home] = {
    .xtCode = XT_KEY(E0, Home),
    .atCode = AT_KEY(E0, Home),
    .ps2Code = PS2_KEY_Home
  },

  /* Page Up (Note 1) */
  [HID_KEY_PageUp] = {
    .xtCode = XT_KEY(E0, PageUp),
    .atCode = AT_KEY(E0, PageUp),
    .ps2Code = PS2_KEY_PageUp
  },

  /* Delete (Note 1) */
  [HID_KEY_Delete] = {
    .xtCode = XT_KEY(E0, Delete),
    .atCode = AT_KEY(E0, Delete),
    .ps2Code = PS2_KEY_Delete
  },

  /* End (Note 1) */
  [HID_KEY_End] = {
    .xtCode = XT_KEY(E0, End),
    .atCode = AT_KEY(E0, End),
    .ps2Code = PS2_KEY_End
  },

  /* Page Down (Note 1) */
  [HID_KEY_PageDown] = {
    .xtCode = XT_KEY(E0, PageDown),
    .atCode = AT_KEY(E0, PageDown),
    .ps2Code = PS2_KEY_PageDown
  },

  /* Right Arrow (Note 1) */
  [HID_KEY_ArrowRight] = {
    .xtCode = XT_KEY(E0, ArrowRight),
    .atCode = AT_KEY(E0, ArrowRight),
    .ps2Code = PS2_KEY_ArrowRight
  },

  /* Left Arrow (Note 1) */
  [HID_KEY_ArrowLeft] = {
    .xtCode = XT_KEY(E0, ArrowLeft),
    .atCode = AT_KEY(E0, ArrowLeft),
    .ps2Code = PS2_KEY_ArrowLeft
  },

  /* Down Arrow (Note 1) */
  [HID_KEY_ArrowDown] = {
    .xtCode = XT_KEY(E0, ArrowDown),
    .atCode = AT_KEY(E0, ArrowDown),
    .ps2Code = PS2_KEY_ArrowDown
  },

  /* Up Arrow (Note 1) */
  [HID_KEY_ArrowUp] = {
    .xtCode = XT_KEY(E0, ArrowUp),
    .atCode = AT_KEY(E0, ArrowUp),
    .ps2Code = PS2_KEY_ArrowUp
  },

  /* Num Lock */
  [HID_KEY_NumLock] = {
    .xtCode = XT_KEY(00, NumLock),
    .atCode = AT_KEY(00, NumLock),
    .ps2Code = PS2_KEY_NumLock
  },

  /* Keypad / (Note 1) */
  [HID_KEY_KPSlash] = {
    .xtCode = XT_KEY(E0, KPSlash),
    .atCode = AT_KEY(E0, KPSlash),
    .ps2Code = PS2_KEY_KPSlash
  },

  /* Keypad * */
  [HID_KEY_KPAsterisk] = {
    .xtCode = XT_KEY(00, KPAsterisk),
    .atCode = AT_KEY(00, KPAsterisk),
    .ps2Code = PS2_KEY_KPAsterisk
  },

  /* Keypad - */
  [HID_KEY_KPMinus] = {
    .xtCode = XT_KEY(00, KPMinus),
    .atCode = AT_KEY(00, KPMinus),
    .ps2Code = PS2_KEY_KPMinus
  },

  /* Keypad + */
  [HID_KEY_KPPlus] = {
    .xtCode = XT_KEY(00, KPPlus),
    .atCode = AT_KEY(00, KPPlus),
    .ps2Code = PS2_KEY_KPPlus
  },

  /* Keypad Enter */
  [HID_KEY_KPEnter] = {
    .xtCode = XT_KEY(E0, KPEnter),
    .atCode = AT_KEY(E0, KPEnter),
    .ps2Code = PS2_KEY_KPEnter
  },

  /* Keypad 1 End */
  [HID_KEY_KP1] = {
    .xtCode = XT_KEY(00, KP1),
    .atCode = AT_KEY(00, KP1),
    .ps2Code = PS2_KEY_KP1
  },

  /* Keypad 2 Down */
  [HID_KEY_KP2] = {
    .xtCode = XT_KEY(00, KP2),
    .atCode = AT_KEY(00, KP2),
    .ps2Code = PS2_KEY_KP2
  },

  /* Keypad 3 PageDn */
  [HID_KEY_KP3] = {
    .xtCode = XT_KEY(00, KP3),
    .atCode = AT_KEY(00, KP3),
    .ps2Code = PS2_KEY_KP3
  },

  /* Keypad 4 Left */
  [HID_KEY_KP4] = {
    .xtCode = XT_KEY(00, KP4),
    .atCode = AT_KEY(00, KP4),
    .ps2Code = PS2_KEY_KP4
  },

  /* Keypad 5 */
  [HID_KEY_KP5] = {
    .xtCode = XT_KEY(00, KP5),
    .atCode = AT_KEY(00, KP5),
    .ps2Code = PS2_KEY_KP5
  },

  /* Keypad 6 Right */
  [HID_KEY_KP6] = {
    .xtCode = XT_KEY(00, KP6),
    .atCode = AT_KEY(00, KP6),
    .ps2Code = PS2_KEY_KP6
  },

  /* Keypad 7 Home */
  [HID_KEY_KP7] = {
    .xtCode = XT_KEY(00, KP7),
    .atCode = AT_KEY(00, KP7),
    .ps2Code = PS2_KEY_KP7
  },

  /* Keypad 8 Up */
  [HID_KEY_KP8] = {
    .xtCode = XT_KEY(00, KP8),
    .atCode = AT_KEY(00, KP8),
    .ps2Code = PS2_KEY_KP8
  },

  /* Keypad 9 PageUp */
  [HID_KEY_KP9] = {
    .xtCode = XT_KEY(00, KP9),
    .atCode = AT_KEY(00, KP9),
    .ps2Code = PS2_KEY_KP9
  },

  /* Keypad 0 Insert */
  [HID_KEY_KP0] = {
    .xtCode = XT_KEY(00, KP0),
    .atCode = AT_KEY(00, KP0),
    .ps2Code = PS2_KEY_KP0
  },

  /* Keypad . Delete */
  [HID_KEY_KPPeriod] = {
    .xtCode = XT_KEY(00, KPPeriod),
    .atCode = AT_KEY(00, KPPeriod),
    .ps2Code = PS2_KEY_KPPeriod
  },

  /* Europe 2 (Note 2) */
  [HID_KEY_Europe2] = {
    .xtCode = XT_KEY(00, Europe2),
    .atCode = AT_KEY(00, Europe2),
    .ps2Code = PS2_KEY_Europe2
  },

  /* App */
  [HID_KEY_Context] = {
    .xtCode = XT_KEY(E0, Context),
    .atCode = AT_KEY(E0, Context),
    .ps2Code = PS2_KEY_Context
  },

  /* Keyboard Power */
  [HID_KEY_Power] = {
    .xtCode = XT_KEY(E0, Power),
    .atCode = AT_KEY(E0, Power),
    .ps2Code = 0X00
  },

  /* Keypad = */
  [HID_KEY_KPEqual] = {
    .xtCode = XT_KEY(00, KPEqual),
    .atCode = AT_KEY(00, KPEqual),
    .ps2Code = 0X00
  },

  /* F13 */
  [HID_KEY_F13] = {
    .xtCode = XT_KEY(00, F13),
    .atCode = AT_KEY(00, F13),
    .ps2Code = 0X00
  },

  /* F14 */
  [HID_KEY_F14] = {
    .xtCode = XT_KEY(00, F14),
    .atCode = AT_KEY(00, F14),
    .ps2Code = 0X00
  },

  /* F15 */
  [HID_KEY_F15] = {
    .xtCode = XT_KEY(00, F15),
    .atCode = AT_KEY(00, F15),
    .ps2Code = 0X00
  },

  /* F16 */
  [HID_KEY_F16] = {
    .xtCode = XT_KEY(00, F16),
    .atCode = AT_KEY(00, F16),
    .ps2Code = 0X00
  },

  /* F17 */
  [HID_KEY_F17] = {
    .xtCode = XT_KEY(00, F17),
    .atCode = AT_KEY(00, F17),
    .ps2Code = 0X00
  },

  /* F18 */
  [HID_KEY_F18] = {
    .xtCode = XT_KEY(00, F18),
    .atCode = AT_KEY(00, F18),
    .ps2Code = 0X00
  },

  /* F19 */
  [HID_KEY_F19] = {
    .xtCode = XT_KEY(00, F19),
    .atCode = AT_KEY(00, F19),
    .ps2Code = 0X00
  },

  /* F20 */
  [HID_KEY_F20] = {
    .xtCode = XT_KEY(00, F20),
    .atCode = AT_KEY(00, F20),
    .ps2Code = 0X00
  },

  /* F21 */
  [HID_KEY_F21] = {
    .xtCode = XT_KEY(00, F21),
    .atCode = AT_KEY(00, F21),
    .ps2Code = 0X00
  },

  /* F22 */
  [HID_KEY_F22] = {
    .xtCode = XT_KEY(00, F22),
    .atCode = AT_KEY(00, F22),
    .ps2Code = 0X00
  },

  /* F23 */
  [HID_KEY_F23] = {
    .xtCode = XT_KEY(00, F23),
    .atCode = AT_KEY(00, F23),
    .ps2Code = 0X00
  },

  /* F24 */
  [HID_KEY_F24] = {
    .xtCode = XT_KEY(00, F24),
    .atCode = AT_KEY(00, F24),
    .ps2Code = 0X00
  },

  /* Keyboard Execute */
  [HID_KEY_Execute] = {
    .xtCode = 0X0000,
    .atCode = 0X0000,
    .ps2Code = 0X00
  },

  /* Keyboard Help */
  [HID_KEY_Help] = {
    .xtCode = XT_KEY(E0, Help),
    .atCode = AT_KEY(E0, Help),
    .ps2Code = 0X00
  },

  /* Keyboard Menu */
  [HID_KEY_Menu] = {
    .xtCode = 0X0000,
    .atCode = 0X0000,
    .ps2Code = 0X00
  },

  /* Keyboard Select */
  [HID_KEY_Select] = {
    .xtCode = 0X0000,
    .atCode = 0X0000,
    .ps2Code = 0X00
  },

  /* Keyboard Stop */
  [HID_KEY_Stop] = {
    .xtCode = 0X0000,
    .atCode = 0X0000,
    .ps2Code = 0X00
  },

  /* Keyboard Again */
  [HID_KEY_Again] = {
    .xtCode = 0X0000,
    .atCode = 0X0000,
    .ps2Code = 0X00
  },

  /* Keyboard Undo */
  [HID_KEY_Undo] = {
    .xtCode = XT_KEY(E0, Undo),
    .atCode = AT_KEY(E0, Undo),
    .ps2Code = 0X00
  },

  /* Keyboard Cut */
  [HID_KEY_Cut] = {
    .xtCode = XT_KEY(E0, Cut),
    .atCode = AT_KEY(E0, Cut),
    .ps2Code = 0X00
  },

  /* Keyboard Copy */
  [HID_KEY_Copy] = {
    .xtCode = XT_KEY(E0, Copy),
    .atCode = AT_KEY(E0, Copy),
    .ps2Code = 0X00
  },

  /* Keyboard Paste */
  [HID_KEY_Paste] = {
    .xtCode = XT_KEY(E0, Paste),
    .atCode = AT_KEY(E0, Paste),
    .ps2Code = 0X00
  },

  /* Keyboard Find */
  [HID_KEY_Find] = {
    .xtCode = 0X0000,
    .atCode = 0X0000,
    .ps2Code = 0X00
  },

  /* Keyboard Mute */
  [HID_KEY_Mute] = {
    .xtCode = XT_KEY(E0, Mute),
    .atCode = AT_KEY(E0, Mute),
    .ps2Code = 0X00
  },

  /* Keyboard Volume Up */
  [HID_KEY_VolumeUp] = {
    .xtCode = XT_KEY(E0, VolumeUp),
    .atCode = AT_KEY(E0, VolumeUp),
    .ps2Code = 0X00
  },

  /* Keyboard Volume Dn */
  [HID_KEY_VolumeDown] = {
    .xtCode = XT_KEY(E0, VolumeDown),
    .atCode = AT_KEY(E0, VolumeDown),
    .ps2Code = 0X00
  },

  /* Caps Lock */
  [HID_KEY_CapsLocking] = {
    .xtCode = 0X0000,
    .atCode = 0X0000,
    .ps2Code = 0X00
  },

  /* Num Lock */
  [HID_KEY_NumLocking] = {
    .xtCode = 0X0000,
    .atCode = 0X0000,
    .ps2Code = 0X00
  },

  /* Scroll Lock */
  [HID_KEY_ScrollLocking] = {
    .xtCode = 0X0000,
    .atCode = 0X0000,
    .ps2Code = 0X00
  },

  /* Keypad , (Brazilian Keypad .) */
  [HID_KEY_KPComma] = {
    .xtCode = XT_KEY(00, KPComma),
    .atCode = AT_KEY(00, KPComma),
    .ps2Code = PS2_KEY_KPComma
  },

  /* Keyboard Equal Sign */
  [HID_KEY_Equal_X1] = {
    .xtCode = XT_KEY(00, Equal),
    .atCode = AT_KEY(00, Equal),
    .ps2Code = PS2_KEY_Equal
  },

  /* Keyboard Int'l 1 (Ro) */
  [HID_KEY_International1] = {
    .xtCode = XT_KEY(00, International1),
    .atCode = AT_KEY(00, International1),
    .ps2Code = PS2_KEY_International1
  },

  /* Keyboard Intl'2 (Katakana/Hiragana) */
  [HID_KEY_International2] = {
    .xtCode = XT_KEY(00, International2),
    .atCode = AT_KEY(00, International2),
    .ps2Code = PS2_KEY_International2
  },

  /* Keyboard Int'l 3 (Yen) */
  [HID_KEY_International3] = {
    .xtCode = XT_KEY(00, International3),
    .atCode = AT_KEY(00, International3),
    .ps2Code = PS2_KEY_International3
  },

  /* Keyboard Int'l 4 (Henkan) */
  [HID_KEY_International4] = {
    .xtCode = XT_KEY(00, International4),
    .atCode = AT_KEY(00, International4),
    .ps2Code = PS2_KEY_International4
  },

  /* Keyboard Int'l 5 (Muhenkan) */
  [HID_KEY_International5] = {
    .xtCode = XT_KEY(00, International5),
    .atCode = AT_KEY(00, International5),
    .ps2Code = PS2_KEY_International5
  },

  /* Keyboard Int'l 6 (PC9800 Keypad ,) */
  [HID_KEY_International6] = {
    .xtCode = XT_KEY(00, International6),
    .atCode = AT_KEY(00, International6),
    .ps2Code = 0X00
  },

  /* Keyboard Int'l 7 */
  [HID_KEY_International7] = {
    .xtCode = 0X0000,
    .atCode = 0X0000,
    .ps2Code = 0X00
  },

  /* Keyboard Int'l 8 */
  [HID_KEY_International8] = {
    .xtCode = 0X0000,
    .atCode = 0X0000,
    .ps2Code = 0X00
  },

  /* Keyboard Int'l 9 */
  [HID_KEY_International9] = {
    .xtCode = 0X0000,
    .atCode = 0X0000,
    .ps2Code = 0X00
  },

  /* Keyboard Lang 1 (Hanguel/English) */
  [HID_KEY_Language1] = {
    .xtCode = XT_KEY(00, Language1),
    .atCode = AT_KEY(00, Language1),
    .ps2Code = 0X00
  },

  /* Keyboard Lang 2 (Hanja) */
  [HID_KEY_Language2] = {
    .xtCode = XT_KEY(00, Language2),
    .atCode = AT_KEY(00, Language2),
    .ps2Code = 0X00
  },

  /* Keyboard Lang 3 (Katakana) */
  [HID_KEY_Language3] = {
    .xtCode = XT_KEY(00, Language3),
    .atCode = AT_KEY(00, Language3),
    .ps2Code = 0X00
  },

  /* Keyboard Lang 4 (Hiragana) */
  [HID_KEY_Language4] = {
    .xtCode = XT_KEY(00, Language4),
    .atCode = AT_KEY(00, Language4),
    .ps2Code = 0X00
  },

  /* Keyboard Lang 5 (Hiragana) */
  [HID_KEY_Language5] = {
    .xtCode = XT_KEY(00, Language5),
    .atCode = AT_KEY(00, Language5),
    .ps2Code = 0X00
  },

  /* Keyboard Lang 6 */
  [HID_KEY_Language6] = {
    .xtCode = 0X0000,
    .atCode = 0X0000,
    .ps2Code = 0X00
  },

  /* Keyboard Lang 7 */
  [HID_KEY_Language7] = {
    .xtCode = 0X0000,
    .atCode = 0X0000,
    .ps2Code = 0X00
  },

  /* Keyboard Lang 8 */
  [HID_KEY_Language8] = {
    .xtCode = 0X0000,
    .atCode = 0X0000,
    .ps2Code = 0X00
  },

  /* Keyboard Lang 9 */
  [HID_KEY_Language9] = {
    .xtCode = 0X0000,
    .atCode = 0X0000,
    .ps2Code = 0X00
  },

  /* Keyboard Alternate Erase */
  [HID_KEY_AlternateErase] = {
    .xtCode = 0X0000,
    .atCode = 0X0000,
    .ps2Code = 0X00
  },

  /* Keyboard SysReq/Attention */
  [HID_KEY_SystemReequest] = {
    .xtCode = XT_KEY(00, SystemRequest),
    .atCode = AT_KEY(00, SystemRequest),
    .ps2Code = 0X00
  },

  /* Keyboard Cancel */
  [HID_KEY_Cancel] = {
    .xtCode = 0X0000,
    .atCode = 0X0000,
    .ps2Code = 0X00
  },

  /* Keyboard Clear */
  [HID_KEY_Clear] = {
    .xtCode = 0X0000,
    .atCode = 0X0000,
    .ps2Code = 0X00
  },

  /* Keyboard Prior */
  [HID_KEY_Prior] = {
    .xtCode = 0X0000,
    .atCode = 0X0000,
    .ps2Code = 0X00
  },

  /* Keyboard Return */
  [HID_KEY_Return] = {
    .xtCode = 0X0000,
    .atCode = 0X0000,
    .ps2Code = 0X00
  },

  /* Keyboard Separator */
  [HID_KEY_Separator] = {
    .xtCode = 0X0000,
    .atCode = 0X0000,
    .ps2Code = 0X00
  },

  /* Keyboard Out */
  [HID_KEY_Out] = {
    .xtCode = 0X0000,
    .atCode = 0X0000,
    .ps2Code = 0X00
  },

  /* Keyboard Oper */
  [HID_KEY_Oper] = {
    .xtCode = 0X0000,
    .atCode = 0X0000,
    .ps2Code = 0X00
  },

  /* Keyboard Clear/Again */
  [HID_KEY_ClearAgain] = {
    .xtCode = 0X0000,
    .atCode = 0X0000,
    .ps2Code = 0X00
  },

  /* Keyboard CrSel/Props */
  [HID_KEY_CrSel] = {
    .xtCode = XT_KEY(00, CrSel),
    .atCode = AT_KEY(00, CrSel),
    .ps2Code = PS2_KEY_CrSel
  },

  /* Keyboard ExSel */
  [HID_KEY_ExSel] = {
    .xtCode = XT_KEY(00, ExSel),
    .atCode = AT_KEY(00, ExSel),
    .ps2Code = PS2_KEY_ExSel
  },

  /* xxx */
  [HID_KEY_KP00] = {
    .xtCode = 0X0000,
    .atCode = 0X0000,
    .ps2Code = 0X00
  },

  /* xxx */
  [HID_KEY_KP000] = {
    .xtCode = 0X0000,
    .atCode = 0X0000,
    .ps2Code = 0X00
  },

  /* xxx */
  [HID_KEY_KPThousandsSeparator] = {
    .xtCode = 0X0000,
    .atCode = 0X0000,
    .ps2Code = 0X00
  },

  /* xxx */
  [HID_KEY_KPDecimalSeparator] = {
    .xtCode = 0X0000,
    .atCode = 0X0000,
    .ps2Code = 0X00
  },

  /* xxx */
  [HID_KEY_KPCurrencyUnit] = {
    .xtCode = 0X0000,
    .atCode = 0X0000,
    .ps2Code = 0X00
  },

  /* xxx */
  [HID_KEY_KPCurrencySubunit] = {
    .xtCode = 0X0000,
    .atCode = 0X0000,
    .ps2Code = 0X00
  },

  /* xxx */
  [HID_KEY_KPLeftParenthesis] = {
    .xtCode = 0X0000,
    .atCode = 0X0000,
    .ps2Code = 0X00
  },

  /* xxx */
  [HID_KEY_KPRightParenthesis] = {
    .xtCode = 0X0000,
    .atCode = 0X0000,
    .ps2Code = 0X00
  },

  /* xxx */
  [HID_KEY_KPLeftBrace] = {
    .xtCode = 0X0000,
    .atCode = 0X0000,
    .ps2Code = 0X00
  },

  /* xxx */
  [HID_KEY_KPRightBrace] = {
    .xtCode = 0X0000,
    .atCode = 0X0000,
    .ps2Code = 0X00
  },

  /* xxx */
  [HID_KEY_KPTab] = {
    .xtCode = 0X0000,
    .atCode = 0X0000,
    .ps2Code = 0X00
  },

  /* xxx */
  [HID_KEY_KPBackspace] = {
    .xtCode = 0X0000,
    .atCode = 0X0000,
    .ps2Code = 0X00
  },

  /* xxx */
  [HID_KEY_KPA] = {
    .xtCode = 0X0000,
    .atCode = 0X0000,
    .ps2Code = 0X00
  },

  /* xxx */
  [HID_KEY_KPB] = {
    .xtCode = 0X0000,
    .atCode = 0X0000,
    .ps2Code = 0X00
  },

  /* xxx */
  [HID_KEY_KPC] = {
    .xtCode = 0X0000,
    .atCode = 0X0000,
    .ps2Code = 0X00
  },

  /* xxx */
  [HID_KEY_KPD] = {
    .xtCode = 0X0000,
    .atCode = 0X0000,
    .ps2Code = 0X00
  },

  /* xxx */
  [HID_KEY_KPE] = {
    .xtCode = 0X0000,
    .atCode = 0X0000,
    .ps2Code = 0X00
  },

  /* xxx */
  [HID_KEY_KPF] = {
    .xtCode = 0X0000,
    .atCode = 0X0000,
    .ps2Code = 0X00
  },

  /* xxx */
  [HID_KEY_KPBitwiseXor] = {
    .xtCode = 0X0000,
    .atCode = 0X0000,
    .ps2Code = 0X00
  },

  /* xxx */
  [HID_KEY_KPExponentiate] = {
    .xtCode = 0X0000,
    .atCode = 0X0000,
    .ps2Code = 0X00
  },

  /* xxx */
  [HID_KEY_KPmodulo] = {
    .xtCode = 0X0000,
    .atCode = 0X0000,
    .ps2Code = 0X00
  },

  /* xxx */
  [HID_KEY_KPLess] = {
    .xtCode = 0X0000,
    .atCode = 0X0000,
    .ps2Code = 0X00
  },

  /* xxx */
  [HID_KEY_KPGreater] = {
    .xtCode = 0X0000,
    .atCode = 0X0000,
    .ps2Code = 0X00
  },

  /* xxx */
  [HID_KEY_KPBitwiseAnd] = {
    .xtCode = 0X0000,
    .atCode = 0X0000,
    .ps2Code = 0X00
  },

  /* xxx */
  [HID_KEY_KPBooleanAnd] = {
    .xtCode = 0X0000,
    .atCode = 0X0000,
    .ps2Code = 0X00
  },

  /* xxx */
  [HID_KEY_KPBitwiseOr] = {
    .xtCode = 0X0000,
    .atCode = 0X0000,
    .ps2Code = 0X00
  },

  /* xxx */
  [HID_KEY_KPBooleanOr] = {
    .xtCode = 0X0000,
    .atCode = 0X0000,
    .ps2Code = 0X00
  },

  /* xxx */
  [HID_KEY_KPColon] = {
    .xtCode = 0X0000,
    .atCode = 0X0000,
    .ps2Code = 0X00
  },

  /* xxx */
  [HID_KEY_KPNumber] = {
    .xtCode = 0X0000,
    .atCode = 0X0000,
    .ps2Code = 0X00
  },

  /* xxx */
  [HID_KEY_KPSpace] = {
    .xtCode = 0X0000,
    .atCode = 0X0000,
    .ps2Code = 0X00
  },

  /* xxx */
  [HID_KEY_KPAt] = {
    .xtCode = 0X0000,
    .atCode = 0X0000,
    .ps2Code = 0X00
  },

  /* xxx */
  [HID_KEY_KPBooleanNot] = {
    .xtCode = 0X0000,
    .atCode = 0X0000,
    .ps2Code = 0X00
  },

  /* xxx */
  [HID_KEY_KPMemoryStore] = {
    .xtCode = 0X0000,
    .atCode = 0X0000,
    .ps2Code = 0X00
  },

  /* xxx */
  [HID_KEY_KPMemoryRecall] = {
    .xtCode = 0X0000,
    .atCode = 0X0000,
    .ps2Code = 0X00
  },

  /* xxx */
  [HID_KEY_KPMemoryClear] = {
    .xtCode = 0X0000,
    .atCode = 0X0000,
    .ps2Code = 0X00
  },

  /* xxx */
  [HID_KEY_KPMemoryAdd] = {
    .xtCode = 0X0000,
    .atCode = 0X0000,
    .ps2Code = 0X00
  },

  /* xxx */
  [HID_KEY_KPMemorySubtract] = {
    .xtCode = 0X0000,
    .atCode = 0X0000,
    .ps2Code = 0X00
  },

  /* xxx */
  [HID_KEY_KPMemoryMultiply] = {
    .xtCode = 0X0000,
    .atCode = 0X0000,
    .ps2Code = 0X00
  },

  /* xxx */
  [HID_KEY_KPMemoryDivide] = {
    .xtCode = 0X0000,
    .atCode = 0X0000,
    .ps2Code = 0X00
  },

  /* xxx */
  [HID_KEY_KPPlusMinus] = {
    .xtCode = 0X0000,
    .atCode = 0X0000,
    .ps2Code = 0X00
  },

  /* xxx */
  [HID_KEY_KPClear] = {
    .xtCode = 0X0000,
    .atCode = 0X0000,
    .ps2Code = 0X00
  },

  /* xxx */
  [HID_KEY_KPClearEntry] = {
    .xtCode = 0X0000,
    .atCode = 0X0000,
    .ps2Code = 0X00
  },

  /* xxx */
  [HID_KEY_KPBinary] = {
    .xtCode = 0X0000,
    .atCode = 0X0000,
    .ps2Code = 0X00
  },

  /* xxx */
  [HID_KEY_KPOctal] = {
    .xtCode = 0X0000,
    .atCode = 0X0000,
    .ps2Code = 0X00
  },

  /* xxx */
  [HID_KEY_KPDecimal] = {
    .xtCode = 0X0000,
    .atCode = 0X0000,
    .ps2Code = 0X00
  },

  /* xxx */
  [HID_KEY_KPHexadecimal] = {
    .xtCode = 0X0000,
    .atCode = 0X0000,
    .ps2Code = 0X00
  },

  /* Left Control */
  [HID_KEY_LeftControl] = {
    .xtCode = XT_KEY(00, LeftControl),
    .atCode = AT_KEY(00, LeftControl),
    .ps2Code = PS2_KEY_LeftControl
  },

  /* Left Shift */
  [HID_KEY_LeftShift] = {
    .xtCode = XT_KEY(00, LeftShift),
    .atCode = AT_KEY(00, LeftShift),
    .ps2Code = PS2_KEY_LeftShift
  },

  /* Left Alt */
  [HID_KEY_LeftAlt] = {
    .xtCode = XT_KEY(00, LeftAlt),
    .atCode = AT_KEY(00, LeftAlt),
    .ps2Code = PS2_KEY_LeftAlt
  },

  /* Left GUI */
  [HID_KEY_LeftGUI] = {
    .xtCode = XT_KEY(E0, LeftGUI),
    .atCode = AT_KEY(E0, LeftGUI),
    .ps2Code = PS2_KEY_LeftGUI
  },

  /* Right Control */
  [HID_KEY_RightControl] = {
    .xtCode = XT_KEY(E0, RightControl),
    .atCode = AT_KEY(E0, RightControl),
    .ps2Code = PS2_KEY_RightControl
  },

  /* Right Shift */
  [HID_KEY_RightShift] = {
    .xtCode = XT_KEY(00, RightShift),
    .atCode = AT_KEY(00, RightShift),
    .ps2Code = PS2_KEY_RightShift
  },

  /* Right Alt */
  [HID_KEY_RightAlt] = {
    .xtCode = XT_KEY(E0, RightAlt),
    .atCode = AT_KEY(E0, RightAlt),
    .ps2Code = PS2_KEY_RightAlt
  },

  /* Right GUI */
  [HID_KEY_RightGUI] = {
    .xtCode = XT_KEY(E0, RightGUI),
    .atCode = AT_KEY(E0, RightGUI),
    .ps2Code = PS2_KEY_RightGUI
  },
};

static int
enqueueXtCode (uint8_t code) {
  return enqueueCommand(BRL_CMD_BLK(PASSXT) | code);
}

static int
enqueueHidKeyEvent (unsigned char key, int press) {
  if (key < ARRAY_COUNT(hidKeyTable)) {
    uint16_t code = hidKeyTable[key].xtCode;

    if (code) {
      {
        uint8_t escape = (code >> 8) & 0XFF;

        if (escape)
          if (!enqueueXtCode(escape))
            return 0;
      }

      code &= 0XFF;

      if (!press) {
        if (code & 0X80) return 1;
        code |= 0X80;
      }

      if (!enqueueXtCode(code)) return 0;
    }
  }

  return 1;
}

static unsigned char
getPressedKeys (const HidKeyboardPacket *packet, unsigned char *keys) {
  unsigned char count = 0;

  {
    static const unsigned char modifiers[] = {
      HID_KEY_LeftControl,
      HID_KEY_LeftShift,
      HID_KEY_LeftAlt,
      HID_KEY_LeftGUI,
      HID_KEY_RightControl,
      HID_KEY_RightShift,
      HID_KEY_RightAlt,
      HID_KEY_RightGUI,
    };

    const unsigned char *modifier = modifiers;
    uint8_t bit = 0X1;

    while (bit) {
      if (packet->modifiers & bit) keys[count++] = *modifier;
      modifier += 1;
      bit <<= 1;
    }
  }

  {
    int index;

    for (index=0; index<6; index+=1) {
      unsigned char key = packet->keys[index];
      if (!key) break;
      keys[count++] = key;
    }
  }

  return count;
}

void
initializeHidKeyboardPacket (HidKeyboardPacket *packet) {
  memset(packet, 0, sizeof(*packet));
}

void
processHidKeyboardPacket (
  HidKeyboardPacket *oldPacket,
  const HidKeyboardPacket *newPacket
) {
  unsigned char oldKeys[14];
  unsigned char oldCount = getPressedKeys(oldPacket, oldKeys);

  unsigned char newKeys[14];
  unsigned char newCount = getPressedKeys(newPacket, newKeys);

  BITMASK(pressedKeys, 0X100, char);
  unsigned char index;

  BITMASK_ZERO(pressedKeys);

  for (index=0; index<newCount; index+=1) {
    unsigned char key = newKeys[index];

    BITMASK_SET(pressedKeys, key);
  }

  for (index=0; index<oldCount; index+=1) {
    unsigned char key = oldKeys[index];

    if (BITMASK_TEST(pressedKeys, key)) {
      BITMASK_CLEAR(pressedKeys, key);
    } else {
      enqueueHidKeyEvent(key, 0);
    }
  }

  for (index=0; index<newCount; index+=1) {
    unsigned char key = newKeys[index];

    if (BITMASK_TEST(pressedKeys, key)) {
      enqueueHidKeyEvent(key, 1);
    }
  }

  *oldPacket = *newPacket;
}
