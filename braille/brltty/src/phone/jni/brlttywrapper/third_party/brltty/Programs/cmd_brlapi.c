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

#include "cmd_brlapi.h"
#include "brl_cmds.h"
#include "ttb.h"

#ifdef ENABLE_API
static brlapi_keyCode_t
cmdWCharToBrlapi (wchar_t wc) {
  if (iswLatin1(wc)) return BRLAPI_KEY_TYPE_SYM | wc;
  return BRLAPI_KEY_TYPE_SYM | BRLAPI_KEY_SYM_UNICODE | wc;
}

int
cmdBrlttyToBrlapi (brlapi_keyCode_t *code, int command, int retainDots) {
  int blk = command & BRL_MSK_BLK;
  int arg = BRL_ARG_GET(command);

  switch (blk) {
    case BRL_CMD_BLK(PASSCHAR):
      *code = cmdWCharToBrlapi(arg);
      break;

    case BRL_CMD_BLK(PASSDOTS):
      if (retainDots) goto doDefault;
      *code = cmdWCharToBrlapi(convertInputToCharacter(arg));
      break;

    case BRL_CMD_BLK(PASSKEY):
      switch (arg) {
        case BRL_KEY_ENTER:        *code = BRLAPI_KEY_SYM_LINEFEED;  break;
        case BRL_KEY_TAB:          *code = BRLAPI_KEY_SYM_TAB;       break;
        case BRL_KEY_BACKSPACE:    *code = BRLAPI_KEY_SYM_BACKSPACE; break;
        case BRL_KEY_ESCAPE:       *code = BRLAPI_KEY_SYM_ESCAPE;    break;
        case BRL_KEY_CURSOR_LEFT:  *code = BRLAPI_KEY_SYM_LEFT;      break;
        case BRL_KEY_CURSOR_RIGHT: *code = BRLAPI_KEY_SYM_RIGHT;     break;
        case BRL_KEY_CURSOR_UP:    *code = BRLAPI_KEY_SYM_UP;        break;
        case BRL_KEY_CURSOR_DOWN:  *code = BRLAPI_KEY_SYM_DOWN;      break;
        case BRL_KEY_PAGE_UP:      *code = BRLAPI_KEY_SYM_PAGE_UP;   break;
        case BRL_KEY_PAGE_DOWN:    *code = BRLAPI_KEY_SYM_PAGE_DOWN; break;
        case BRL_KEY_HOME:         *code = BRLAPI_KEY_SYM_HOME;      break;
        case BRL_KEY_END:          *code = BRLAPI_KEY_SYM_END;       break;
        case BRL_KEY_INSERT:       *code = BRLAPI_KEY_SYM_INSERT;    break;
        case BRL_KEY_DELETE:       *code = BRLAPI_KEY_SYM_DELETE;    break;

        default: {
          int key = arg - BRL_KEY_FUNCTION;
          if (key < 0) return 0;
          if (key > 34) return 0;
          *code = BRLAPI_KEY_SYM_FUNCTION + key;
          break;
        }
      }
      break;

    default:
    doDefault:
      *code = BRLAPI_KEY_TYPE_CMD
            | (blk >> BRL_SHIFT_BLK << BRLAPI_KEY_CMD_BLK_SHIFT)
            | (arg                  << BRLAPI_KEY_CMD_ARG_SHIFT)
            ;
      break;
  }

  switch (blk) {
    case BRL_CMD_BLK(PASSCHAR):
    case BRL_CMD_BLK(PASSDOTS):
    case BRL_CMD_BLK(PASSKEY):
      *code = *code
            | (command & BRL_FLG_INPUT_SHIFT   ? BRLAPI_KEY_FLG_SHIFT   : 0)
            | (command & BRL_FLG_INPUT_UPPER   ? BRLAPI_KEY_FLG_UPPER   : 0)
            | (command & BRL_FLG_INPUT_CONTROL ? BRLAPI_KEY_FLG_CONTROL : 0)
            | (command & BRL_FLG_INPUT_META    ? BRLAPI_KEY_FLG_META    : 0)
            | (command & BRL_FLG_INPUT_ALTGR   ? BRLAPI_KEY_FLG_ALTGR   : 0)
            | (command & BRL_FLG_INPUT_GUI     ? BRLAPI_KEY_FLG_GUI     : 0)
            ;
      break;

    case BRL_CMD_BLK(PASSXT):
    case BRL_CMD_BLK(PASSAT):
    case BRL_CMD_BLK(PASSPS2):
      *code = *code
            | (command & BRL_FLG_KBD_RELEASE ? BRLAPI_KEY_FLG_KBD_RELEASE : 0)
            | (command & BRL_FLG_KBD_EMUL0   ? BRLAPI_KEY_FLG_KBD_EMUL0   : 0)
            | (command & BRL_FLG_KBD_EMUL1   ? BRLAPI_KEY_FLG_KBD_EMUL1   : 0)
            ;
      break;

    default:
      *code = *code
            | (command & BRL_FLG_TOGGLE_ON     ? BRLAPI_KEY_FLG_TOGGLE_ON     : 0)
            | (command & BRL_FLG_TOGGLE_OFF    ? BRLAPI_KEY_FLG_TOGGLE_OFF    : 0)
            | (command & BRL_FLG_MOTION_ROUTE  ? BRLAPI_KEY_FLG_MOTION_ROUTE  : 0)
            | (command & BRL_FLG_MOTION_SCALED ? BRLAPI_KEY_FLG_MOTION_SCALED : 0)
            | (command & BRL_FLG_MOTION_TOLEFT ? BRLAPI_KEY_FLG_MOTION_TOLEFT : 0)
            ;
      break;
  }

  return 1;
}

int
cmdBrlapiToBrltty (brlapi_keyCode_t code) {
  int cmd;

  switch (code & BRLAPI_KEY_TYPE_MASK) {
    case BRLAPI_KEY_TYPE_CMD:
      cmd = BRL_BLK_PUT((code & BRLAPI_KEY_CMD_BLK_MASK) >> BRLAPI_KEY_CMD_BLK_SHIFT);
      cmd |= BRL_ARG_SET((code & BRLAPI_KEY_CMD_ARG_MASK) >> BRLAPI_KEY_CMD_ARG_SHIFT);
      break;

    case BRLAPI_KEY_TYPE_SYM: {
      unsigned long keysym = code & BRLAPI_KEY_CODE_MASK;

      switch (keysym) {
        case BRLAPI_KEY_SYM_BACKSPACE: cmd = BRL_CMD_BLK(PASSKEY)|BRL_KEY_BACKSPACE;    break;
        case BRLAPI_KEY_SYM_TAB:       cmd = BRL_CMD_BLK(PASSKEY)|BRL_KEY_TAB;          break;
        case BRLAPI_KEY_SYM_LINEFEED:  cmd = BRL_CMD_BLK(PASSKEY)|BRL_KEY_ENTER;        break;
        case BRLAPI_KEY_SYM_ESCAPE:    cmd = BRL_CMD_BLK(PASSKEY)|BRL_KEY_ESCAPE;       break;
        case BRLAPI_KEY_SYM_HOME:      cmd = BRL_CMD_BLK(PASSKEY)|BRL_KEY_HOME;         break;
        case BRLAPI_KEY_SYM_LEFT:      cmd = BRL_CMD_BLK(PASSKEY)|BRL_KEY_CURSOR_LEFT;  break;
        case BRLAPI_KEY_SYM_UP:        cmd = BRL_CMD_BLK(PASSKEY)|BRL_KEY_CURSOR_UP;    break;
        case BRLAPI_KEY_SYM_RIGHT:     cmd = BRL_CMD_BLK(PASSKEY)|BRL_KEY_CURSOR_RIGHT; break;
        case BRLAPI_KEY_SYM_DOWN:      cmd = BRL_CMD_BLK(PASSKEY)|BRL_KEY_CURSOR_DOWN;  break;
        case BRLAPI_KEY_SYM_PAGE_UP:   cmd = BRL_CMD_BLK(PASSKEY)|BRL_KEY_PAGE_UP;      break;
        case BRLAPI_KEY_SYM_PAGE_DOWN: cmd = BRL_CMD_BLK(PASSKEY)|BRL_KEY_PAGE_DOWN;    break;
        case BRLAPI_KEY_SYM_END:       cmd = BRL_CMD_BLK(PASSKEY)|BRL_KEY_END;          break;
        case BRLAPI_KEY_SYM_INSERT:    cmd = BRL_CMD_BLK(PASSKEY)|BRL_KEY_INSERT;       break;
        case BRLAPI_KEY_SYM_DELETE:    cmd = BRL_CMD_BLK(PASSKEY)|BRL_KEY_DELETE;       break;

        default:
          if ((keysym >= BRLAPI_KEY_SYM_FUNCTION) &&
              (keysym <= (BRLAPI_KEY_SYM_FUNCTION + 34))) {
            cmd = BRL_CMD_KFN(keysym - BRLAPI_KEY_SYM_FUNCTION);
          } else if ((keysym < 0X100) ||
                     ((keysym & 0x1F000000) == BRLAPI_KEY_SYM_UNICODE)) {
            wchar_t c = keysym & 0xFFFFFF;
            cmd = BRL_CMD_BLK(PASSCHAR) | BRL_ARG_SET(c);
          } else {
            return EOF;
          }
          break;
      }

      break;
    }

    default:
      return EOF;
  }

  switch (cmd & BRL_MSK_BLK) {
    case BRL_CMD_BLK(PASSCHAR):
    case BRL_CMD_BLK(PASSDOTS):
    case BRL_CMD_BLK(PASSKEY):
      cmd = cmd
          | (code & BRLAPI_KEY_FLG_SHIFT   ? BRL_FLG_INPUT_SHIFT   : 0)
          | (code & BRLAPI_KEY_FLG_UPPER   ? BRL_FLG_INPUT_UPPER   : 0)
          | (code & BRLAPI_KEY_FLG_CONTROL ? BRL_FLG_INPUT_CONTROL : 0)
          | (code & BRLAPI_KEY_FLG_META    ? BRL_FLG_INPUT_META    : 0)
          | (code & BRLAPI_KEY_FLG_ALTGR   ? BRL_FLG_INPUT_ALTGR   : 0)
          | (code & BRLAPI_KEY_FLG_GUI     ? BRL_FLG_INPUT_GUI     : 0)
          ;
      break;

    case BRL_CMD_BLK(PASSXT):
    case BRL_CMD_BLK(PASSAT):
    case BRL_CMD_BLK(PASSPS2):
      cmd = cmd
          | (code & BRLAPI_KEY_FLG_KBD_RELEASE ? BRL_FLG_KBD_RELEASE : 0)
          | (code & BRLAPI_KEY_FLG_KBD_EMUL0   ? BRL_FLG_KBD_EMUL0   : 0)
          | (code & BRLAPI_KEY_FLG_KBD_EMUL1   ? BRL_FLG_KBD_EMUL1   : 0)
          ;
      break;

    default:
      cmd = cmd
          | (code & BRLAPI_KEY_FLG_TOGGLE_ON     ? BRL_FLG_TOGGLE_ON     : 0)
          | (code & BRLAPI_KEY_FLG_TOGGLE_OFF    ? BRL_FLG_TOGGLE_OFF    : 0)
          | (code & BRLAPI_KEY_FLG_MOTION_ROUTE  ? BRL_FLG_MOTION_ROUTE  : 0)
          | (code & BRLAPI_KEY_FLG_MOTION_SCALED ? BRL_FLG_MOTION_SCALED : 0)
          | (code & BRLAPI_KEY_FLG_MOTION_TOLEFT ? BRL_FLG_MOTION_TOLEFT : 0)
          ;
      break;
  }

  return cmd;
}
#endif /* ENABLE_API */
