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

#include <string.h>

#include "log.h"
#include "report.h"
#include "alert.h"
#include "parameters.h"
#include "cmd_queue.h"
#include "cmd_input.h"
#include "cmd_utils.h"
#include "brl_cmds.h"
#include "unicode.h"
#include "ttb.h"
#include "scr.h"
#include "async_alarm.h"
#include "core.h"

typedef struct {
  ReportListenerInstance *resetListener;

  struct {
    AsyncHandle timeout;
    int on;
    int next;
  } modifiers;
} InputCommandData;

static void
initializeModifierTimeout (InputCommandData *icd) {
  icd->modifiers.timeout = NULL;
}

static void
cancelModifierTimeout (InputCommandData *icd) {
  if (icd->modifiers.timeout) {
    asyncCancelRequest(icd->modifiers.timeout);
    initializeModifierTimeout(icd);
  }
}

static void
initializeModifierFlags (InputCommandData *icd) {
  icd->modifiers.on = 0;
  icd->modifiers.next = 0;
}

static void
clearModifierFlags (InputCommandData *icd) {
  initializeModifierFlags(icd);
  alert(ALERT_MODIFIER_OFF);
}

ASYNC_ALARM_CALLBACK(handleStickyModifiersTimeout) {
  InputCommandData *icd = parameters->data;

  asyncDiscardHandle(icd->modifiers.timeout);
  initializeModifierTimeout(icd);

  clearModifierFlags(icd);
}

static int
haveModifierFlags (InputCommandData *icd) {
  return icd->modifiers.on || icd->modifiers.next;
}

static int
setModifierTimeout (InputCommandData *icd) {
  if (!haveModifierFlags(icd)) {
    cancelModifierTimeout(icd);
    return 1;
  }

  return icd->modifiers.timeout?
         asyncResetAlarmIn(icd->modifiers.timeout,
                           INPUT_STICKY_MODIFIERS_TIMEOUT):
         asyncNewRelativeAlarm(&icd->modifiers.timeout,
                               INPUT_STICKY_MODIFIERS_TIMEOUT,
                               handleStickyModifiersTimeout, icd);
}

static void
applyModifierFlags (InputCommandData *icd, int *flags) {
  *flags |= icd->modifiers.on;
  *flags |= icd->modifiers.next;
  icd->modifiers.next = 0;

  setModifierTimeout(icd);
}

static int
insertKey (ScreenKey key, int flags) {
  if (flags & BRL_FLG_INPUT_SHIFT) key |= SCR_KEY_SHIFT;
  if (flags & BRL_FLG_INPUT_UPPER) key |= SCR_KEY_UPPER;
  if (flags & BRL_FLG_INPUT_CONTROL) key |= SCR_KEY_CONTROL;
  if (flags & BRL_FLG_INPUT_META) key |= SCR_KEY_ALT_LEFT;
  if (flags & BRL_FLG_INPUT_ALTGR) key |= SCR_KEY_ALT_RIGHT;
  if (flags & BRL_FLG_INPUT_GUI) key |= SCR_KEY_GUI;
  return insertScreenKey(key);
}

static void
handleVirtualTerminalSwitched (int switched) {
  if (switched) {
    updateSessionAttributes();
  } else {
    alert(ALERT_COMMAND_REJECTED);
  }
}

static int
selectVirtualTerminal (int vt) {
  int selected = selectScreenVirtualTerminal(vt);

  if (selected) {
    updateSessionAttributes();
  } else {
    alert(ALERT_COMMAND_REJECTED);
  }

  return selected;
}

static int
handleInputCommands (int command, void *data) {
  InputCommandData *icd = data;

  switch (command & BRL_MSK_CMD) {
    case BRL_CMD_UNSTICK: {
      cancelModifierTimeout(icd);

      if (haveModifierFlags(icd)) {
        clearModifierFlags(icd);
      } else {
        alert(ALERT_COMMAND_REJECTED);
      }

      break;
    }

    {
      int flag;

    case BRL_CMD_SHIFT:
      flag = BRL_FLG_INPUT_SHIFT;
      goto doModifier;

    case BRL_CMD_UPPER:
      flag = BRL_FLG_INPUT_UPPER;
      goto doModifier;

    case BRL_CMD_CONTROL:
      flag = BRL_FLG_INPUT_CONTROL;
      goto doModifier;

    case BRL_CMD_META:
      flag = BRL_FLG_INPUT_META;
      goto doModifier;

    case BRL_CMD_ALTGR:
      flag = BRL_FLG_INPUT_ALTGR;
      goto doModifier;

    case BRL_CMD_GUI:
      flag = BRL_FLG_INPUT_GUI;
      goto doModifier;

    doModifier:
      cancelModifierTimeout(icd);

      if (icd->modifiers.on & flag) {
        icd->modifiers.on &= ~flag;
        icd->modifiers.next &= ~flag;
        alert(ALERT_MODIFIER_OFF);
      } else if (icd->modifiers.next & flag) {
        icd->modifiers.on |= flag;
        icd->modifiers.next &= ~flag;
        alert(ALERT_MODIFIER_ON);
      } else {
        icd->modifiers.next |= flag;
        alert(ALERT_MODIFIER_NEXT);
      }

      setModifierTimeout(icd);
      break;
    }

    case BRL_CMD_SWITCHVT_PREV:
      handleVirtualTerminalSwitched(previousScreenVirtualTerminal());
      break;

    case BRL_CMD_SWITCHVT_NEXT:
      handleVirtualTerminalSwitched(nextScreenVirtualTerminal());
      break;

    case BRL_CMD_SELECTVT_PREV:
      selectVirtualTerminal(scr.number-1);
      break;

    case BRL_CMD_SELECTVT_NEXT:
      selectVirtualTerminal(scr.number+1);
      break;

    default: {
      int arg = command & BRL_MSK_ARG;
      int flags = command & BRL_MSK_FLG;

      switch (command & BRL_MSK_BLK) {
        case BRL_CMD_BLK(PASSKEY): {
          ScreenKey key;

          switch (arg) {
            case BRL_KEY_ENTER:
              key = SCR_KEY_ENTER;
              break;
            case BRL_KEY_TAB:
              key = SCR_KEY_TAB;
              break;
            case BRL_KEY_BACKSPACE:
              key = SCR_KEY_BACKSPACE;
              break;
            case BRL_KEY_ESCAPE:
              key = SCR_KEY_ESCAPE;
              break;
            case BRL_KEY_CURSOR_LEFT:
              key = SCR_KEY_CURSOR_LEFT;
              break;
            case BRL_KEY_CURSOR_RIGHT:
              key = SCR_KEY_CURSOR_RIGHT;
              break;
            case BRL_KEY_CURSOR_UP:
              key = SCR_KEY_CURSOR_UP;
              break;
            case BRL_KEY_CURSOR_DOWN:
              key = SCR_KEY_CURSOR_DOWN;
              break;
            case BRL_KEY_PAGE_UP:
              key = SCR_KEY_PAGE_UP;
              break;
            case BRL_KEY_PAGE_DOWN:
              key = SCR_KEY_PAGE_DOWN;
              break;
            case BRL_KEY_HOME:
              key = SCR_KEY_HOME;
              break;
            case BRL_KEY_END:
              key = SCR_KEY_END;
              break;
            case BRL_KEY_INSERT:
              key = SCR_KEY_INSERT;
              break;
            case BRL_KEY_DELETE:
              key = SCR_KEY_DELETE;
              break;
            default:
              if (arg < BRL_KEY_FUNCTION) goto badKey;
              key = SCR_KEY_FUNCTION + (arg - BRL_KEY_FUNCTION);
              break;
          }

          applyModifierFlags(icd, &flags);
          if (!insertKey(key, flags)) {
          badKey:
            alert(ALERT_COMMAND_REJECTED);
          }
          break;
        }

        case BRL_CMD_BLK(PASSCHAR): {
          applyModifierFlags(icd, &flags);
          if (!insertKey(BRL_ARG_GET(command), flags)) alert(ALERT_COMMAND_REJECTED);
          break;
        }

        case BRL_CMD_BLK(PASSDOTS): {
          applyModifierFlags(icd, &flags);
          wchar_t character;

          switch (prefs.brailleInputMode) {
            case BRL_INPUT_TEXT:
              character = convertDotsToCharacter(textTable, arg);
              break;

            default:
              logMessage(LOG_WARNING, "unknown braille input mode: %u", prefs.brailleInputMode);
              /* fall through */

            case BRL_INPUT_DOTS:
              character = UNICODE_BRAILLE_ROW | arg;
              break;
          }

          if (!insertKey(character, flags)) {
            alert(ALERT_COMMAND_REJECTED);
          } else if ((command & BRL_DOTC) && arg) {
            if (!insertKey(WC_C(' '), flags)) alert(ALERT_COMMAND_REJECTED);
          }

          break;
        }

        case BRL_CMD_BLK(SWITCHVT):
          handleVirtualTerminalSwitched(switchScreenVirtualTerminal(arg+1));
          break;

        case BRL_CMD_BLK(SELECTVT):
          selectVirtualTerminal(arg+1);
          break;

        default:
          return 0;
      }

      break;
    }
  }

  return 1;
}

static void
resetInputCommandData (InputCommandData *icd) {
  cancelModifierTimeout(icd);
  initializeModifierFlags(icd);
}

REPORT_LISTENER(inputCommandDataResetListener) {
  InputCommandData *icd = parameters->listenerData;

  resetInputCommandData(icd);
}

static void
destroyInputCommandData (void *data) {
  InputCommandData *icd = data;

  unregisterReportListener(icd->resetListener);
  cancelModifierTimeout(icd);
  free(icd);
}

int
addInputCommands (void) {
  InputCommandData *icd;

  if ((icd = malloc(sizeof(*icd)))) {
    memset(icd, 0, sizeof(*icd));
    initializeModifierTimeout(icd);
    initializeModifierFlags(icd);

    if ((icd->resetListener = registerReportListener(REPORT_BRAILLE_DEVICE_ONLINE, inputCommandDataResetListener, icd))) {
      if (pushCommandHandler("input", KTB_CTX_DEFAULT,
                             handleInputCommands, destroyInputCommandData, icd)) {
        return 1;
      }

      unregisterReportListener(icd->resetListener);
    }

    free(icd);
  } else {
    logMallocError();
  }

  return 0;
}
