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

#include "log.h"
#include "report.h"
#include "cmd_queue.h"
#include "cmd_keycodes.h"
#include "kbd_keycodes.h"
#include "brl_cmds.h"
#include "alert.h"

typedef enum {
  MOD_RELEASE = 0, /* must be first */

  MOD_GUI_LEFT,
  MOD_GUI_RIGHT,
  MOD_CONTEXT,

  MOD_LOCK_CAPS,
  MOD_LOCK_SCROLL,
  MOD_LOCK_NUMBER,

  MOD_SHIFT_LEFT,
  MOD_SHIFT_RIGHT,

  MOD_CONTROL_LEFT,
  MOD_CONTROL_RIGHT,

  MOD_ALT_LEFT,
  MOD_ALT_RIGHT
} Modifier;

#define MOD_BIT(number) (1 << (number))
#define MOD_SET(number, bits) ((bits) |= MOD_BIT((number)))
#define MOD_CLR(number, bits) ((bits) &= ~MOD_BIT((number)))
#define MOD_TST(number, bits) ((bits) & MOD_BIT((number)))

typedef struct {
  int command;
  int alternate;
} KeyEntry;

static const KeyEntry keyEntry_Escape = {BRL_CMD_KEY(ESCAPE)};
static const KeyEntry keyEntry_F1 = {BRL_CMD_KFN(1)};
static const KeyEntry keyEntry_F2 = {BRL_CMD_KFN(2)};
static const KeyEntry keyEntry_F3 = {BRL_CMD_KFN(3)};
static const KeyEntry keyEntry_F4 = {BRL_CMD_KFN(4)};
static const KeyEntry keyEntry_F5 = {BRL_CMD_KFN(5)};
static const KeyEntry keyEntry_F6 = {BRL_CMD_KFN(6)};
static const KeyEntry keyEntry_F7 = {BRL_CMD_KFN(7)};
static const KeyEntry keyEntry_F8 = {BRL_CMD_KFN(8)};
static const KeyEntry keyEntry_F9 = {BRL_CMD_KFN(9)};
static const KeyEntry keyEntry_F10 = {BRL_CMD_KFN(10)};
static const KeyEntry keyEntry_F11 = {BRL_CMD_KFN(11)};
static const KeyEntry keyEntry_F12 = {BRL_CMD_KFN(12)};
static const KeyEntry keyEntry_ScrollLock = {MOD_LOCK_SCROLL};

static const KeyEntry keyEntry_F13 = {BRL_CMD_KFN(13)};
static const KeyEntry keyEntry_F14 = {BRL_CMD_KFN(14)};
static const KeyEntry keyEntry_F15 = {BRL_CMD_KFN(15)};
static const KeyEntry keyEntry_F16 = {BRL_CMD_KFN(16)};
static const KeyEntry keyEntry_F17 = {BRL_CMD_KFN(17)};
static const KeyEntry keyEntry_F18 = {BRL_CMD_KFN(18)};
static const KeyEntry keyEntry_F19 = {BRL_CMD_KFN(19)};
static const KeyEntry keyEntry_F20 = {BRL_CMD_KFN(20)};
static const KeyEntry keyEntry_F21 = {BRL_CMD_KFN(21)};
static const KeyEntry keyEntry_F22 = {BRL_CMD_KFN(22)};
static const KeyEntry keyEntry_F23 = {BRL_CMD_KFN(23)};
static const KeyEntry keyEntry_F24 = {BRL_CMD_KFN(24)};

static const KeyEntry keyEntry_Grave = {BRL_CMD_CHAR(WC_C('`')), BRL_CMD_CHAR(WC_C('~'))};
static const KeyEntry keyEntry_1 = {BRL_CMD_CHAR(WC_C('1')), BRL_CMD_CHAR(WC_C('!'))};
static const KeyEntry keyEntry_2 = {BRL_CMD_CHAR(WC_C('2')), BRL_CMD_CHAR(WC_C('@'))};
static const KeyEntry keyEntry_3 = {BRL_CMD_CHAR(WC_C('3')), BRL_CMD_CHAR(WC_C('#'))};
static const KeyEntry keyEntry_4 = {BRL_CMD_CHAR(WC_C('4')), BRL_CMD_CHAR(WC_C('$'))};
static const KeyEntry keyEntry_5 = {BRL_CMD_CHAR(WC_C('5')), BRL_CMD_CHAR(WC_C('%'))};
static const KeyEntry keyEntry_6 = {BRL_CMD_CHAR(WC_C('6')), BRL_CMD_CHAR(WC_C('^'))};
static const KeyEntry keyEntry_7 = {BRL_CMD_CHAR(WC_C('7')), BRL_CMD_CHAR(WC_C('&'))};
static const KeyEntry keyEntry_8 = {BRL_CMD_CHAR(WC_C('8')), BRL_CMD_CHAR(WC_C('*'))};
static const KeyEntry keyEntry_9 = {BRL_CMD_CHAR(WC_C('9')), BRL_CMD_CHAR(WC_C('('))};
static const KeyEntry keyEntry_0 = {BRL_CMD_CHAR(WC_C('0')), BRL_CMD_CHAR(WC_C(')'))};
static const KeyEntry keyEntry_Minus = {BRL_CMD_CHAR(WC_C('-')), BRL_CMD_CHAR(WC_C('_'))};
static const KeyEntry keyEntry_Equal = {BRL_CMD_CHAR(WC_C('=')), BRL_CMD_CHAR(WC_C('+'))};
static const KeyEntry keyEntry_Backspace = {BRL_CMD_KEY(BACKSPACE)};

static const KeyEntry keyEntry_Tab = {BRL_CMD_KEY(TAB)};
static const KeyEntry keyEntry_Q = {BRL_CMD_CHAR(WC_C('q')), BRL_CMD_CHAR(WC_C('Q'))};
static const KeyEntry keyEntry_W = {BRL_CMD_CHAR(WC_C('w')), BRL_CMD_CHAR(WC_C('W'))};
static const KeyEntry keyEntry_E = {BRL_CMD_CHAR(WC_C('e')), BRL_CMD_CHAR(WC_C('E'))};
static const KeyEntry keyEntry_R = {BRL_CMD_CHAR(WC_C('r')), BRL_CMD_CHAR(WC_C('R'))};
static const KeyEntry keyEntry_T = {BRL_CMD_CHAR(WC_C('t')), BRL_CMD_CHAR(WC_C('T'))};
static const KeyEntry keyEntry_Y = {BRL_CMD_CHAR(WC_C('y')), BRL_CMD_CHAR(WC_C('Y'))};
static const KeyEntry keyEntry_U = {BRL_CMD_CHAR(WC_C('u')), BRL_CMD_CHAR(WC_C('U'))};
static const KeyEntry keyEntry_I = {BRL_CMD_CHAR(WC_C('i')), BRL_CMD_CHAR(WC_C('I'))};
static const KeyEntry keyEntry_O = {BRL_CMD_CHAR(WC_C('o')), BRL_CMD_CHAR(WC_C('O'))};
static const KeyEntry keyEntry_P = {BRL_CMD_CHAR(WC_C('p')), BRL_CMD_CHAR(WC_C('P'))};
static const KeyEntry keyEntry_LeftBracket = {BRL_CMD_CHAR(WC_C('[')), BRL_CMD_CHAR(WC_C('{'))};
static const KeyEntry keyEntry_RightBracket = {BRL_CMD_CHAR(WC_C(']')), BRL_CMD_CHAR(WC_C('}'))};
static const KeyEntry keyEntry_Backslash = {BRL_CMD_CHAR('\\'), BRL_CMD_CHAR(WC_C('|'))};

static const KeyEntry keyEntry_CapsLock = {MOD_LOCK_CAPS};
static const KeyEntry keyEntry_A = {BRL_CMD_CHAR(WC_C('a')), BRL_CMD_CHAR(WC_C('A'))};
static const KeyEntry keyEntry_S = {BRL_CMD_CHAR(WC_C('s')), BRL_CMD_CHAR(WC_C('S'))};
static const KeyEntry keyEntry_D = {BRL_CMD_CHAR(WC_C('d')), BRL_CMD_CHAR(WC_C('D'))};
static const KeyEntry keyEntry_F = {BRL_CMD_CHAR(WC_C('f')), BRL_CMD_CHAR(WC_C('F'))};
static const KeyEntry keyEntry_G = {BRL_CMD_CHAR(WC_C('g')), BRL_CMD_CHAR(WC_C('G'))};
static const KeyEntry keyEntry_H = {BRL_CMD_CHAR(WC_C('h')), BRL_CMD_CHAR(WC_C('H'))};
static const KeyEntry keyEntry_J = {BRL_CMD_CHAR(WC_C('j')), BRL_CMD_CHAR(WC_C('J'))};
static const KeyEntry keyEntry_K = {BRL_CMD_CHAR(WC_C('k')), BRL_CMD_CHAR(WC_C('K'))};
static const KeyEntry keyEntry_L = {BRL_CMD_CHAR(WC_C('l')), BRL_CMD_CHAR(WC_C('L'))};
static const KeyEntry keyEntry_Semicolon = {BRL_CMD_CHAR(WC_C(';')), BRL_CMD_CHAR(WC_C(':'))};
static const KeyEntry keyEntry_Apostrophe = {BRL_CMD_CHAR(WC_C('\'')), BRL_CMD_CHAR(WC_C('"'))};
static const KeyEntry keyEntry_Enter = {BRL_CMD_KEY(ENTER)};

static const KeyEntry keyEntry_LeftShift = {MOD_SHIFT_LEFT};
static const KeyEntry keyEntry_Europe2 = {BRL_CMD_CHAR(WC_C('<')), BRL_CMD_CHAR(WC_C('>'))};
static const KeyEntry keyEntry_Z = {BRL_CMD_CHAR(WC_C('z')), BRL_CMD_CHAR(WC_C('Z'))};
static const KeyEntry keyEntry_X = {BRL_CMD_CHAR(WC_C('x')), BRL_CMD_CHAR(WC_C('X'))};
static const KeyEntry keyEntry_C = {BRL_CMD_CHAR(WC_C('c')), BRL_CMD_CHAR(WC_C('C'))};
static const KeyEntry keyEntry_V = {BRL_CMD_CHAR(WC_C('v')), BRL_CMD_CHAR(WC_C('V'))};
static const KeyEntry keyEntry_B = {BRL_CMD_CHAR(WC_C('b')), BRL_CMD_CHAR(WC_C('B'))};
static const KeyEntry keyEntry_N = {BRL_CMD_CHAR(WC_C('n')), BRL_CMD_CHAR(WC_C('N'))};
static const KeyEntry keyEntry_M = {BRL_CMD_CHAR(WC_C('m')), BRL_CMD_CHAR(WC_C('M'))};
static const KeyEntry keyEntry_Comma = {BRL_CMD_CHAR(WC_C(',')), BRL_CMD_CHAR(WC_C('<'))};
static const KeyEntry keyEntry_Period = {BRL_CMD_CHAR(WC_C('.')), BRL_CMD_CHAR(WC_C('>'))};
static const KeyEntry keyEntry_Slash = {BRL_CMD_CHAR(WC_C('/')), BRL_CMD_CHAR(WC_C('?'))};
static const KeyEntry keyEntry_RightShift = {MOD_SHIFT_RIGHT};

static const KeyEntry keyEntry_LeftControl = {MOD_CONTROL_LEFT};
static const KeyEntry keyEntry_LeftGUI = {MOD_GUI_LEFT};
static const KeyEntry keyEntry_LeftAlt = {MOD_ALT_LEFT};
static const KeyEntry keyEntry_Space = {BRL_CMD_CHAR(WC_C(' '))};
static const KeyEntry keyEntry_RightAlt = {MOD_ALT_RIGHT};
static const KeyEntry keyEntry_RightGUI = {MOD_GUI_RIGHT};
static const KeyEntry keyEntry_Context = {MOD_CONTEXT};
static const KeyEntry keyEntry_RightControl = {MOD_CONTROL_RIGHT};

static const KeyEntry keyEntry_Insert = {BRL_CMD_KEY(INSERT)};
static const KeyEntry keyEntry_Delete = {BRL_CMD_KEY(DELETE)};
static const KeyEntry keyEntry_Home = {BRL_CMD_KEY(HOME)};
static const KeyEntry keyEntry_End = {BRL_CMD_KEY(END)};
static const KeyEntry keyEntry_PageUp = {BRL_CMD_KEY(PAGE_UP)};
static const KeyEntry keyEntry_PageDown = {BRL_CMD_KEY(PAGE_DOWN)};

static const KeyEntry keyEntry_ArrowUp = {BRL_CMD_KEY(CURSOR_UP)};
static const KeyEntry keyEntry_ArrowLeft = {BRL_CMD_KEY(CURSOR_LEFT)};
static const KeyEntry keyEntry_ArrowDown = {BRL_CMD_KEY(CURSOR_DOWN)};
static const KeyEntry keyEntry_ArrowRight = {BRL_CMD_KEY(CURSOR_RIGHT)};

static const KeyEntry keyEntry_NumLock = {MOD_LOCK_NUMBER};
static const KeyEntry keyEntry_KPSlash = {BRL_CMD_CHAR(WC_C('/'))};
static const KeyEntry keyEntry_KPAsterisk = {BRL_CMD_CHAR(WC_C('*'))};
static const KeyEntry keyEntry_KPMinus = {BRL_CMD_CHAR(WC_C('-'))};
static const KeyEntry keyEntry_KPPlus = {BRL_CMD_CHAR(WC_C('+'))};
static const KeyEntry keyEntry_KPEnter = {BRL_CMD_KEY(ENTER)};
static const KeyEntry keyEntry_KPPeriod = {BRL_CMD_KEY(DELETE), BRL_CMD_CHAR(WC_C('.'))};
static const KeyEntry keyEntry_KP0 = {BRL_CMD_KEY(INSERT), BRL_CMD_CHAR(WC_C('0'))};
static const KeyEntry keyEntry_KP1 = {BRL_CMD_KEY(END), BRL_CMD_CHAR(WC_C('1'))};
static const KeyEntry keyEntry_KP2 = {BRL_CMD_KEY(CURSOR_DOWN), BRL_CMD_CHAR(WC_C('2'))};
static const KeyEntry keyEntry_KP3 = {BRL_CMD_KEY(PAGE_DOWN), BRL_CMD_CHAR(WC_C('3'))};
static const KeyEntry keyEntry_KP4 = {BRL_CMD_KEY(CURSOR_LEFT), BRL_CMD_CHAR(WC_C('4'))};
static const KeyEntry keyEntry_KP5 = {BRL_CMD_CHAR(WC_C('5'))};
static const KeyEntry keyEntry_KP6 = {BRL_CMD_KEY(CURSOR_RIGHT), BRL_CMD_CHAR(WC_C('6'))};
static const KeyEntry keyEntry_KP7 = {BRL_CMD_KEY(HOME), BRL_CMD_CHAR(WC_C('7'))};
static const KeyEntry keyEntry_KP8 = {BRL_CMD_KEY(CURSOR_UP), BRL_CMD_CHAR(WC_C('8'))};
static const KeyEntry keyEntry_KP9 = {BRL_CMD_KEY(PAGE_UP), BRL_CMD_CHAR(WC_C('9'))};
static const KeyEntry keyEntry_KPComma = {BRL_CMD_CHAR(WC_C(','))};

typedef struct {
  ReportListenerInstance *resetListener;

  struct {
    const KeyEntry *const *keyMap;
    size_t keyCount;
    unsigned int modifiers;
  } xt;

  struct {
    const KeyEntry *const *keyMap;
    size_t keyCount;
    unsigned int modifiers;
  } at;

  struct {
    unsigned int modifiers;
  } ps2;
} KeycodeCommandData;

#define USE_KEY_MAP(set,escape) \
  (kcd->set.keyCount = (kcd->set.keyMap = set##KeyMap##escape)? \
  (sizeof(set##KeyMap##escape) / sizeof(*kcd->set.keyMap)): 0)

static void
handleKey (const KeyEntry *key, int release, unsigned int *modifiers) {
  if (key) {
    int cmd = key->command;
    int blk = cmd & BRL_MSK_BLK;

    if (key->alternate) {
      int alternate = 0;

      if (blk == BRL_CMD_BLK(PASSCHAR)) {
        if (MOD_TST(MOD_SHIFT_LEFT, *modifiers) || MOD_TST(MOD_SHIFT_RIGHT, *modifiers)) alternate = 1;
      } else {
        if (MOD_TST(MOD_LOCK_NUMBER, *modifiers)) alternate = 1;
      }

      if (alternate) {
        cmd = key->alternate;
        blk = cmd & BRL_MSK_BLK;
      }
    }

    if (cmd) {
      if (blk) {
        if (!release) {
          if (blk == BRL_CMD_BLK(PASSCHAR)) {
            if (MOD_TST(MOD_LOCK_CAPS, *modifiers)) cmd |= BRL_FLG_INPUT_UPPER;
            if (MOD_TST(MOD_ALT_LEFT, *modifiers)) cmd |= BRL_FLG_INPUT_META;
            if (MOD_TST(MOD_ALT_RIGHT, *modifiers)) cmd |= BRL_FLG_INPUT_ALTGR;
            if (MOD_TST(MOD_GUI_LEFT, *modifiers) || MOD_TST(MOD_GUI_RIGHT, *modifiers)) cmd |= BRL_FLG_INPUT_GUI;
            if (MOD_TST(MOD_CONTROL_LEFT, *modifiers) || MOD_TST(MOD_CONTROL_RIGHT, *modifiers)) cmd |= BRL_FLG_INPUT_CONTROL;
          } else if ((blk == BRL_CMD_BLK(PASSKEY)) && MOD_TST(MOD_ALT_LEFT, *modifiers)) {
            int arg = cmd & BRL_MSK_ARG;

            switch (arg) {
              case BRL_KEY_CURSOR_LEFT:
                cmd = BRL_CMD_SWITCHVT_PREV;
                break;

              case BRL_KEY_CURSOR_RIGHT:
                cmd = BRL_CMD_SWITCHVT_NEXT;
                break;

              default:
                if (arg >= BRL_KEY_FUNCTION) {
                  cmd = BRL_CMD_BLK(SWITCHVT) + (arg - BRL_KEY_FUNCTION);
                }

                break;
            }
          }

          handleCommand(cmd);
        }
      } else {
        switch (cmd) {
          case MOD_LOCK_SCROLL:
          case MOD_LOCK_NUMBER:
          case MOD_LOCK_CAPS:
            if (!release) {
              if (MOD_TST(cmd, *modifiers)) {
                MOD_CLR(cmd, *modifiers);
              } else {
                MOD_SET(cmd, *modifiers);
              }
            }

            break;

          case MOD_SHIFT_LEFT:
          case MOD_SHIFT_RIGHT:
          case MOD_CONTROL_LEFT:
          case MOD_CONTROL_RIGHT:
          case MOD_ALT_LEFT:
          case MOD_ALT_RIGHT:
            if (release) {
              MOD_CLR(cmd, *modifiers);
            } else {
              MOD_SET(cmd, *modifiers);
            }

            break;
        }
      }
    }
  }
}

static const KeyEntry *const xtKeyMap00[] = {
  [XT_KEY_00_Escape] = &keyEntry_Escape,
  [XT_KEY_00_F1] = &keyEntry_F1,
  [XT_KEY_00_F2] = &keyEntry_F2,
  [XT_KEY_00_F3] = &keyEntry_F3,
  [XT_KEY_00_F4] = &keyEntry_F4,
  [XT_KEY_00_F5] = &keyEntry_F5,
  [XT_KEY_00_F6] = &keyEntry_F6,
  [XT_KEY_00_F7] = &keyEntry_F7,
  [XT_KEY_00_F8] = &keyEntry_F8,
  [XT_KEY_00_F9] = &keyEntry_F9,
  [XT_KEY_00_F10] = &keyEntry_F10,
  [XT_KEY_00_F11] = &keyEntry_F11,
  [XT_KEY_00_F12] = &keyEntry_F12,
  [XT_KEY_00_ScrollLock] = &keyEntry_ScrollLock,

  [XT_KEY_00_F13] = &keyEntry_F13,
  [XT_KEY_00_F14] = &keyEntry_F14,
  [XT_KEY_00_F15] = &keyEntry_F15,
  [XT_KEY_00_F16] = &keyEntry_F16,
  [XT_KEY_00_F17] = &keyEntry_F17,
  [XT_KEY_00_F18] = &keyEntry_F18,
  [XT_KEY_00_F19] = &keyEntry_F19,
  [XT_KEY_00_F20] = &keyEntry_F20,
  [XT_KEY_00_F21] = &keyEntry_F21,
  [XT_KEY_00_F22] = &keyEntry_F22,
  [XT_KEY_00_F23] = &keyEntry_F23,
  [XT_KEY_00_F24] = &keyEntry_F24,

  [XT_KEY_00_Grave] = &keyEntry_Grave,
  [XT_KEY_00_1] = &keyEntry_1,
  [XT_KEY_00_2] = &keyEntry_2,
  [XT_KEY_00_3] = &keyEntry_3,
  [XT_KEY_00_4] = &keyEntry_4,
  [XT_KEY_00_5] = &keyEntry_5,
  [XT_KEY_00_6] = &keyEntry_6,
  [XT_KEY_00_7] = &keyEntry_7,
  [XT_KEY_00_8] = &keyEntry_8,
  [XT_KEY_00_9] = &keyEntry_9,
  [XT_KEY_00_0] = &keyEntry_0,
  [XT_KEY_00_Minus] = &keyEntry_Minus,
  [XT_KEY_00_Equal] = &keyEntry_Equal,
  [XT_KEY_00_Backspace] = &keyEntry_Backspace,

  [XT_KEY_00_Tab] = &keyEntry_Tab,
  [XT_KEY_00_Q] = &keyEntry_Q,
  [XT_KEY_00_W] = &keyEntry_W,
  [XT_KEY_00_E] = &keyEntry_E,
  [XT_KEY_00_R] = &keyEntry_R,
  [XT_KEY_00_T] = &keyEntry_T,
  [XT_KEY_00_Y] = &keyEntry_Y,
  [XT_KEY_00_U] = &keyEntry_U,
  [XT_KEY_00_I] = &keyEntry_I,
  [XT_KEY_00_O] = &keyEntry_O,
  [XT_KEY_00_P] = &keyEntry_P,
  [XT_KEY_00_LeftBracket] = &keyEntry_LeftBracket,
  [XT_KEY_00_RightBracket] = &keyEntry_RightBracket,
  [XT_KEY_00_Backslash] = &keyEntry_Backslash,

  [XT_KEY_00_CapsLock] = &keyEntry_CapsLock,
  [XT_KEY_00_A] = &keyEntry_A,
  [XT_KEY_00_S] = &keyEntry_S,
  [XT_KEY_00_D] = &keyEntry_D,
  [XT_KEY_00_F] = &keyEntry_F,
  [XT_KEY_00_G] = &keyEntry_G,
  [XT_KEY_00_H] = &keyEntry_H,
  [XT_KEY_00_J] = &keyEntry_J,
  [XT_KEY_00_K] = &keyEntry_K,
  [XT_KEY_00_L] = &keyEntry_L,
  [XT_KEY_00_Semicolon] = &keyEntry_Semicolon,
  [XT_KEY_00_Apostrophe] = &keyEntry_Apostrophe,
  [XT_KEY_00_Enter] = &keyEntry_Enter,

  [XT_KEY_00_LeftShift] = &keyEntry_LeftShift,
  [XT_KEY_00_Europe2] = &keyEntry_Europe2,
  [XT_KEY_00_Z] = &keyEntry_Z,
  [XT_KEY_00_X] = &keyEntry_X,
  [XT_KEY_00_C] = &keyEntry_C,
  [XT_KEY_00_V] = &keyEntry_V,
  [XT_KEY_00_B] = &keyEntry_B,
  [XT_KEY_00_N] = &keyEntry_N,
  [XT_KEY_00_M] = &keyEntry_M,
  [XT_KEY_00_Comma] = &keyEntry_Comma,
  [XT_KEY_00_Period] = &keyEntry_Period,
  [XT_KEY_00_Slash] = &keyEntry_Slash,
  [XT_KEY_00_RightShift] = &keyEntry_RightShift,

  [XT_KEY_00_LeftControl] = &keyEntry_LeftControl,
  [XT_KEY_00_LeftAlt] = &keyEntry_LeftAlt,
  [XT_KEY_00_Space] = &keyEntry_Space,

  [XT_KEY_00_NumLock] = &keyEntry_NumLock,
  [XT_KEY_00_KPAsterisk] = &keyEntry_KPAsterisk,
  [XT_KEY_00_KPMinus] = &keyEntry_KPMinus,
  [XT_KEY_00_KPPlus] = &keyEntry_KPPlus,
  [XT_KEY_00_KPPeriod] = &keyEntry_KPPeriod,
  [XT_KEY_00_KP0] = &keyEntry_KP0,
  [XT_KEY_00_KP1] = &keyEntry_KP1,
  [XT_KEY_00_KP2] = &keyEntry_KP2,
  [XT_KEY_00_KP3] = &keyEntry_KP3,
  [XT_KEY_00_KP4] = &keyEntry_KP4,
  [XT_KEY_00_KP5] = &keyEntry_KP5,
  [XT_KEY_00_KP6] = &keyEntry_KP6,
  [XT_KEY_00_KP7] = &keyEntry_KP7,
  [XT_KEY_00_KP8] = &keyEntry_KP8,
  [XT_KEY_00_KP9] = &keyEntry_KP9,
};

static const KeyEntry *const xtKeyMapE0[] = {
  [XT_KEY_E0_LeftGUI] = &keyEntry_LeftGUI,
  [XT_KEY_E0_RightAlt] = &keyEntry_RightAlt,
  [XT_KEY_E0_RightGUI] = &keyEntry_RightGUI,
  [XT_KEY_E0_Context] = &keyEntry_Context,
  [XT_KEY_E0_RightControl] = &keyEntry_RightControl,

  [XT_KEY_E0_Insert] = &keyEntry_Insert,
  [XT_KEY_E0_Delete] = &keyEntry_Delete,
  [XT_KEY_E0_Home] = &keyEntry_Home,
  [XT_KEY_E0_End] = &keyEntry_End,
  [XT_KEY_E0_PageUp] = &keyEntry_PageUp,
  [XT_KEY_E0_PageDown] = &keyEntry_PageDown,

  [XT_KEY_E0_ArrowUp] = &keyEntry_ArrowUp,
  [XT_KEY_E0_ArrowLeft] = &keyEntry_ArrowLeft,
  [XT_KEY_E0_ArrowDown] = &keyEntry_ArrowDown,
  [XT_KEY_E0_ArrowRight] = &keyEntry_ArrowRight,

  [XT_KEY_E0_KPSlash] = &keyEntry_KPSlash,
  [XT_KEY_E0_KPEnter] = &keyEntry_KPEnter,
};

#define xtKeyMapE1 NULL

static void
xtHandleScanCode (KeycodeCommandData *kcd, unsigned char code) {
  if (code == XT_MOD_E0) {
    USE_KEY_MAP(xt, E0);
  } else if (code == XT_MOD_E1) {
    USE_KEY_MAP(xt, E1);
  } else {
    int release = (code & XT_BIT_RELEASE) != 0;
    code &= ~XT_BIT_RELEASE;

    if (code < kcd->xt.keyCount) {
      const KeyEntry *key = kcd->xt.keyMap[code];

      USE_KEY_MAP(xt, 00);
      handleKey(key, release, &kcd->xt.modifiers);
    }
  }
}

static const KeyEntry *const atKeyMap00[] = {
  [AT_KEY_00_Escape] = &keyEntry_Escape,
  [AT_KEY_00_F1] = &keyEntry_F1,
  [AT_KEY_00_F2] = &keyEntry_F2,
  [AT_KEY_00_F3] = &keyEntry_F3,
  [AT_KEY_00_F4] = &keyEntry_F4,
  [AT_KEY_00_F5] = &keyEntry_F5,
  [AT_KEY_00_F6] = &keyEntry_F6,
  [AT_KEY_00_F7] = &keyEntry_F7,
  [AT_KEY_00_F8] = &keyEntry_F8,
  [AT_KEY_00_F9] = &keyEntry_F9,
  [AT_KEY_00_F10] = &keyEntry_F10,
  [AT_KEY_00_F11] = &keyEntry_F11,
  [AT_KEY_00_F12] = &keyEntry_F12,
  [AT_KEY_00_ScrollLock] = &keyEntry_ScrollLock,

  [AT_KEY_00_F13] = &keyEntry_F13,
  [AT_KEY_00_F14] = &keyEntry_F14,
  [AT_KEY_00_F15] = &keyEntry_F15,
  [AT_KEY_00_F16] = &keyEntry_F16,
  [AT_KEY_00_F17] = &keyEntry_F17,
  [AT_KEY_00_F18] = &keyEntry_F18,
  [AT_KEY_00_F19] = &keyEntry_F19,
  [AT_KEY_00_F20] = &keyEntry_F20,
  [AT_KEY_00_F21] = &keyEntry_F21,
  [AT_KEY_00_F22] = &keyEntry_F22,
  [AT_KEY_00_F23] = &keyEntry_F23,
  [AT_KEY_00_F24] = &keyEntry_F24,

  [AT_KEY_00_Grave] = &keyEntry_Grave,
  [AT_KEY_00_1] = &keyEntry_1,
  [AT_KEY_00_2] = &keyEntry_2,
  [AT_KEY_00_3] = &keyEntry_3,
  [AT_KEY_00_4] = &keyEntry_4,
  [AT_KEY_00_5] = &keyEntry_5,
  [AT_KEY_00_6] = &keyEntry_6,
  [AT_KEY_00_7] = &keyEntry_7,
  [AT_KEY_00_8] = &keyEntry_8,
  [AT_KEY_00_9] = &keyEntry_9,
  [AT_KEY_00_0] = &keyEntry_0,
  [AT_KEY_00_Minus] = &keyEntry_Minus,
  [AT_KEY_00_Equal] = &keyEntry_Equal,
  [AT_KEY_00_Backspace] = &keyEntry_Backspace,

  [AT_KEY_00_Tab] = &keyEntry_Tab,
  [AT_KEY_00_Q] = &keyEntry_Q,
  [AT_KEY_00_W] = &keyEntry_W,
  [AT_KEY_00_E] = &keyEntry_E,
  [AT_KEY_00_R] = &keyEntry_R,
  [AT_KEY_00_T] = &keyEntry_T,
  [AT_KEY_00_Y] = &keyEntry_Y,
  [AT_KEY_00_U] = &keyEntry_U,
  [AT_KEY_00_I] = &keyEntry_I,
  [AT_KEY_00_O] = &keyEntry_O,
  [AT_KEY_00_P] = &keyEntry_P,
  [AT_KEY_00_LeftBracket] = &keyEntry_LeftBracket,
  [AT_KEY_00_RightBracket] = &keyEntry_RightBracket,
  [AT_KEY_00_Backslash] = &keyEntry_Backslash,

  [AT_KEY_00_CapsLock] = &keyEntry_CapsLock,
  [AT_KEY_00_A] = &keyEntry_A,
  [AT_KEY_00_S] = &keyEntry_S,
  [AT_KEY_00_D] = &keyEntry_D,
  [AT_KEY_00_F] = &keyEntry_F,
  [AT_KEY_00_G] = &keyEntry_G,
  [AT_KEY_00_H] = &keyEntry_H,
  [AT_KEY_00_J] = &keyEntry_J,
  [AT_KEY_00_K] = &keyEntry_K,
  [AT_KEY_00_L] = &keyEntry_L,
  [AT_KEY_00_Semicolon] = &keyEntry_Semicolon,
  [AT_KEY_00_Apostrophe] = &keyEntry_Apostrophe,
  [AT_KEY_00_Enter] = &keyEntry_Enter,

  [AT_KEY_00_LeftShift] = &keyEntry_LeftShift,
  [AT_KEY_00_Europe2] = &keyEntry_Europe2,
  [AT_KEY_00_Z] = &keyEntry_Z,
  [AT_KEY_00_X] = &keyEntry_X,
  [AT_KEY_00_C] = &keyEntry_C,
  [AT_KEY_00_V] = &keyEntry_V,
  [AT_KEY_00_B] = &keyEntry_B,
  [AT_KEY_00_N] = &keyEntry_N,
  [AT_KEY_00_M] = &keyEntry_M,
  [AT_KEY_00_Comma] = &keyEntry_Comma,
  [AT_KEY_00_Period] = &keyEntry_Period,
  [AT_KEY_00_Slash] = &keyEntry_Slash,
  [AT_KEY_00_RightShift] = &keyEntry_RightShift,

  [AT_KEY_00_LeftControl] = &keyEntry_LeftControl,
  [AT_KEY_00_LeftAlt] = &keyEntry_LeftAlt,
  [AT_KEY_00_Space] = &keyEntry_Space,

  [AT_KEY_00_NumLock] = &keyEntry_NumLock,
  [AT_KEY_00_KPAsterisk] = &keyEntry_KPAsterisk,
  [AT_KEY_00_KPMinus] = &keyEntry_KPMinus,
  [AT_KEY_00_KPPlus] = &keyEntry_KPPlus,
  [AT_KEY_00_KPPeriod] = &keyEntry_KPPeriod,
  [AT_KEY_00_KP0] = &keyEntry_KP0,
  [AT_KEY_00_KP1] = &keyEntry_KP1,
  [AT_KEY_00_KP2] = &keyEntry_KP2,
  [AT_KEY_00_KP3] = &keyEntry_KP3,
  [AT_KEY_00_KP4] = &keyEntry_KP4,
  [AT_KEY_00_KP5] = &keyEntry_KP5,
  [AT_KEY_00_KP6] = &keyEntry_KP6,
  [AT_KEY_00_KP7] = &keyEntry_KP7,
  [AT_KEY_00_KP8] = &keyEntry_KP8,
  [AT_KEY_00_KP9] = &keyEntry_KP9,
};

static const KeyEntry *const atKeyMapE0[] = {
  [AT_KEY_E0_LeftGUI] = &keyEntry_LeftGUI,
  [AT_KEY_E0_RightAlt] = &keyEntry_RightAlt,
  [AT_KEY_E0_RightGUI] = &keyEntry_RightGUI,
  [AT_KEY_E0_Context] = &keyEntry_Context,
  [AT_KEY_E0_RightControl] = &keyEntry_RightControl,

  [AT_KEY_E0_Insert] = &keyEntry_Insert,
  [AT_KEY_E0_Delete] = &keyEntry_Delete,
  [AT_KEY_E0_Home] = &keyEntry_Home,
  [AT_KEY_E0_End] = &keyEntry_End,
  [AT_KEY_E0_PageUp] = &keyEntry_PageUp,
  [AT_KEY_E0_PageDown] = &keyEntry_PageDown,

  [AT_KEY_E0_ArrowUp] = &keyEntry_ArrowUp,
  [AT_KEY_E0_ArrowLeft] = &keyEntry_ArrowLeft,
  [AT_KEY_E0_ArrowDown] = &keyEntry_ArrowDown,
  [AT_KEY_E0_ArrowRight] = &keyEntry_ArrowRight,

  [AT_KEY_E0_KPSlash] = &keyEntry_KPSlash,
  [AT_KEY_E0_KPEnter] = &keyEntry_KPEnter,
};

#define atKeyMapE1 NULL

static void
atHandleScanCode (KeycodeCommandData *kcd, unsigned char code) {
  if (code == AT_MOD_RELEASE) {
    MOD_SET(MOD_RELEASE, kcd->at.modifiers);
  } else if (code == AT_MOD_E0) {
    USE_KEY_MAP(at, E0);
  } else if (code == AT_MOD_E1) {
    USE_KEY_MAP(at, E1);
  } else if (code < kcd->at.keyCount) {
    const KeyEntry *key = kcd->at.keyMap[code];
    int release = MOD_TST(MOD_RELEASE, kcd->at.modifiers);

    MOD_CLR(MOD_RELEASE, kcd->at.modifiers);
    USE_KEY_MAP(at, 00);

    handleKey(key, release, &kcd->at.modifiers);
  }
}

static const KeyEntry *const ps2KeyMap[] = {
  [PS2_KEY_Escape] = &keyEntry_Escape,
  [PS2_KEY_F1] = &keyEntry_F1,
  [PS2_KEY_F2] = &keyEntry_F2,
  [PS2_KEY_F3] = &keyEntry_F3,
  [PS2_KEY_F4] = &keyEntry_F4,
  [PS2_KEY_F5] = &keyEntry_F5,
  [PS2_KEY_F6] = &keyEntry_F6,
  [PS2_KEY_F7] = &keyEntry_F7,
  [PS2_KEY_F8] = &keyEntry_F8,
  [PS2_KEY_F9] = &keyEntry_F9,
  [PS2_KEY_F10] = &keyEntry_F10,
  [PS2_KEY_F11] = &keyEntry_F11,
  [PS2_KEY_F12] = &keyEntry_F12,
  [PS2_KEY_ScrollLock] = &keyEntry_ScrollLock,

  [PS2_KEY_Grave] = &keyEntry_Grave,
  [PS2_KEY_1] = &keyEntry_1,
  [PS2_KEY_2] = &keyEntry_2,
  [PS2_KEY_3] = &keyEntry_3,
  [PS2_KEY_4] = &keyEntry_4,
  [PS2_KEY_5] = &keyEntry_5,
  [PS2_KEY_6] = &keyEntry_6,
  [PS2_KEY_7] = &keyEntry_7,
  [PS2_KEY_8] = &keyEntry_8,
  [PS2_KEY_9] = &keyEntry_9,
  [PS2_KEY_0] = &keyEntry_0,
  [PS2_KEY_Minus] = &keyEntry_Minus,
  [PS2_KEY_Equal] = &keyEntry_Equal,
  [PS2_KEY_Backspace] = &keyEntry_Backspace,

  [PS2_KEY_Tab] = &keyEntry_Tab,
  [PS2_KEY_Q] = &keyEntry_Q,
  [PS2_KEY_W] = &keyEntry_W,
  [PS2_KEY_E] = &keyEntry_E,
  [PS2_KEY_R] = &keyEntry_R,
  [PS2_KEY_T] = &keyEntry_T,
  [PS2_KEY_Y] = &keyEntry_Y,
  [PS2_KEY_U] = &keyEntry_U,
  [PS2_KEY_I] = &keyEntry_I,
  [PS2_KEY_O] = &keyEntry_O,
  [PS2_KEY_P] = &keyEntry_P,
  [PS2_KEY_LeftBracket] = &keyEntry_LeftBracket,
  [PS2_KEY_RightBracket] = &keyEntry_RightBracket,
  [PS2_KEY_Backslash] = &keyEntry_Backslash,

  [PS2_KEY_CapsLock] = &keyEntry_CapsLock,
  [PS2_KEY_A] = &keyEntry_A,
  [PS2_KEY_S] = &keyEntry_S,
  [PS2_KEY_D] = &keyEntry_D,
  [PS2_KEY_F] = &keyEntry_F,
  [PS2_KEY_G] = &keyEntry_G,
  [PS2_KEY_H] = &keyEntry_H,
  [PS2_KEY_J] = &keyEntry_J,
  [PS2_KEY_K] = &keyEntry_K,
  [PS2_KEY_L] = &keyEntry_L,
  [PS2_KEY_Semicolon] = &keyEntry_Semicolon,
  [PS2_KEY_Apostrophe] = &keyEntry_Apostrophe,
  [PS2_KEY_Enter] = &keyEntry_Enter,

  [PS2_KEY_LeftShift] = &keyEntry_LeftShift,
  [PS2_KEY_Europe2] = &keyEntry_Europe2,
  [PS2_KEY_Z] = &keyEntry_Z,
  [PS2_KEY_X] = &keyEntry_X,
  [PS2_KEY_C] = &keyEntry_C,
  [PS2_KEY_V] = &keyEntry_V,
  [PS2_KEY_B] = &keyEntry_B,
  [PS2_KEY_N] = &keyEntry_N,
  [PS2_KEY_M] = &keyEntry_M,
  [PS2_KEY_Comma] = &keyEntry_Comma,
  [PS2_KEY_Period] = &keyEntry_Period,
  [PS2_KEY_Slash] = &keyEntry_Slash,
  [PS2_KEY_RightShift] = &keyEntry_RightShift,

  [PS2_KEY_LeftControl] = &keyEntry_LeftControl,
  [PS2_KEY_LeftGUI] = &keyEntry_LeftGUI,
  [PS2_KEY_LeftAlt] = &keyEntry_LeftAlt,
  [PS2_KEY_Space] = &keyEntry_Space,
  [PS2_KEY_RightAlt] = &keyEntry_RightAlt,
  [PS2_KEY_RightGUI] = &keyEntry_RightGUI,
  [PS2_KEY_Context] = &keyEntry_Context,
  [PS2_KEY_RightControl] = &keyEntry_RightControl,

  [PS2_KEY_Insert] = &keyEntry_Insert,
  [PS2_KEY_Delete] = &keyEntry_Delete,
  [PS2_KEY_Home] = &keyEntry_Home,
  [PS2_KEY_End] = &keyEntry_End,
  [PS2_KEY_PageUp] = &keyEntry_PageUp,
  [PS2_KEY_PageDown] = &keyEntry_PageDown,

  [PS2_KEY_ArrowUp] = &keyEntry_ArrowUp,
  [PS2_KEY_ArrowLeft] = &keyEntry_ArrowLeft,
  [PS2_KEY_ArrowDown] = &keyEntry_ArrowDown,
  [PS2_KEY_ArrowRight] = &keyEntry_ArrowRight,

  [PS2_KEY_NumLock] = &keyEntry_NumLock,
  [PS2_KEY_KPSlash] = &keyEntry_KPSlash,
  [PS2_KEY_KPAsterisk] = &keyEntry_KPAsterisk,
  [PS2_KEY_KPMinus] = &keyEntry_KPMinus,
  [PS2_KEY_KPPlus] = &keyEntry_KPPlus,
  [PS2_KEY_KPEnter] = &keyEntry_KPEnter,
  [PS2_KEY_KPPeriod] = &keyEntry_KPPeriod,
  [PS2_KEY_KP0] = &keyEntry_KP0,
  [PS2_KEY_KP1] = &keyEntry_KP1,
  [PS2_KEY_KP2] = &keyEntry_KP2,
  [PS2_KEY_KP3] = &keyEntry_KP3,
  [PS2_KEY_KP4] = &keyEntry_KP4,
  [PS2_KEY_KP5] = &keyEntry_KP5,
  [PS2_KEY_KP6] = &keyEntry_KP6,
  [PS2_KEY_KP7] = &keyEntry_KP7,
  [PS2_KEY_KP8] = &keyEntry_KP8,
  [PS2_KEY_KP9] = &keyEntry_KP9,
  [PS2_KEY_KPComma] = &keyEntry_KPComma,
};

static void
ps2HandleScanCode (KeycodeCommandData *kcd, unsigned char code) {
  if (code == PS2_MOD_RELEASE) {
    MOD_SET(MOD_RELEASE, kcd->ps2.modifiers);
  } else if (code < ARRAY_COUNT(ps2KeyMap)) {
    const KeyEntry *key = ps2KeyMap[code];
    int release = MOD_TST(MOD_RELEASE, kcd->ps2.modifiers);

    MOD_CLR(MOD_RELEASE, kcd->ps2.modifiers);

    handleKey(key, release, &kcd->ps2.modifiers);
  }
}

static int
handleKeycodeCommands (int command, void *data) {
  KeycodeCommandData *kcd = data;
  int arg = command & BRL_MSK_ARG;

  switch (command & BRL_MSK_BLK) {
    case BRL_CMD_BLK(PASSXT):
      if (command & BRL_FLG_KBD_RELEASE) arg |= XT_BIT_RELEASE;
      if (command & BRL_FLG_KBD_EMUL0) xtHandleScanCode(kcd, XT_MOD_E0);
      if (command & BRL_FLG_KBD_EMUL1) xtHandleScanCode(kcd, XT_MOD_E1);
      xtHandleScanCode(kcd, arg);
      break;

    case BRL_CMD_BLK(PASSAT):
      if (command & BRL_FLG_KBD_RELEASE) atHandleScanCode(kcd, AT_MOD_RELEASE);
      if (command & BRL_FLG_KBD_EMUL0) atHandleScanCode(kcd, AT_MOD_E0);
      if (command & BRL_FLG_KBD_EMUL1) atHandleScanCode(kcd, AT_MOD_E1);
      atHandleScanCode(kcd, arg);
      break;

    case BRL_CMD_BLK(PASSPS2):
      if (command & BRL_FLG_KBD_RELEASE) ps2HandleScanCode(kcd, PS2_MOD_RELEASE);
      ps2HandleScanCode(kcd, arg);
      break;

    default:
      return 0;
  }

  return 1;
}

static void
resetKeycodeCommandData (void *data) {
  KeycodeCommandData *kcd = data;

  USE_KEY_MAP(xt, 00);
  kcd->xt.modifiers = 0;

  USE_KEY_MAP(at, 00);
  kcd->at.modifiers = 0;

  kcd->ps2.modifiers = 0;
}

REPORT_LISTENER(keycodeCommandDataResetListener) {
  KeycodeCommandData *kcd = parameters->listenerData;

  resetKeycodeCommandData(kcd);
}

static void
destroyKeycodeCommandData (void *data) {
  KeycodeCommandData *kcd = data;

  unregisterReportListener(kcd->resetListener);
  free(kcd);
}

int
addKeycodeCommands (void) {
  KeycodeCommandData *kcd;

  if ((kcd = malloc(sizeof(*kcd)))) {
    memset(kcd, 0, sizeof(*kcd));
    resetKeycodeCommandData(kcd);

    if ((kcd->resetListener = registerReportListener(REPORT_BRAILLE_DEVICE_ONLINE, keycodeCommandDataResetListener, kcd))) {
      if (pushCommandHandler("keycodes", KTB_CTX_DEFAULT,
                             handleKeycodeCommands, destroyKeycodeCommandData, kcd)) {
        return 1;
      }

      unregisterReportListener(kcd->resetListener);
    }

    free(kcd);
  } else {
    logMallocError();
  }

  return 0;
}
