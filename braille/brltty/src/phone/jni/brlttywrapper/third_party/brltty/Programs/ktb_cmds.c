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

#include "ktb_cmds.h"
#include "brl_cmds.h"

static const CommandListEntry commandList_modes[] = {
  { .code = BRL_CMD_HELP },
  { .code = BRL_CMD_LEARN },
  { .code = BRL_CMD_PREFMENU },
  { .code = BRL_CMD_INFO },
  { .code = BRL_CMD_DISPMD },
  { .code = BRL_CMD_FREEZE },
  { .code = BRL_CMD_BLK(DESCCHAR) },
  { .code = BRL_CMD_TIME },
  { .code = BRL_CMD_INDICATORS },
  { .code = BRL_CMD_BLK(CONTEXT) },
};

static const CommandListEntry commandList_cursor[] = {
  { .code = BRL_CMD_HOME },
  { .code = BRL_CMD_BACK },
  { .code = BRL_CMD_RETURN },
  { .code = BRL_CMD_BLK(ROUTE) },
  { .code = BRL_CMD_BLK(ROUTE_LINE) },
  { .code = BRL_CMD_CSRJMP_VERT },
  { .code = BRL_CMD_ROUTE_CURR_LOCN },
};

static const CommandListEntry commandList_vertical[] = {
  { .code = BRL_CMD_LNUP },
  { .code = BRL_CMD_LNDN },
  { .code = BRL_CMD_TOP },
  { .code = BRL_CMD_BOT },
  { .code = BRL_CMD_TOP_LEFT },
  { .code = BRL_CMD_BOT_LEFT },
  { .code = BRL_CMD_PRDIFLN },
  { .code = BRL_CMD_NXDIFLN },
  { .code = BRL_CMD_ATTRUP },
  { .code = BRL_CMD_ATTRDN },
  { .code = BRL_CMD_PRPGRPH },
  { .code = BRL_CMD_NXPGRPH },
  { .code = BRL_CMD_PRPROMPT },
  { .code = BRL_CMD_NXPROMPT },
  { .code = BRL_CMD_WINUP },
  { .code = BRL_CMD_WINDN },
  { .code = BRL_CMD_BLK(PRINDENT) },
  { .code = BRL_CMD_BLK(NXINDENT) },
  { .code = BRL_CMD_BLK(PRDIFCHAR) },
  { .code = BRL_CMD_BLK(NXDIFCHAR) },
  { .code = BRL_CMD_BLK(GOTOLINE) },
};

static const CommandListEntry commandList_horizontal[] = {
  { .code = BRL_CMD_FWINLT },
  { .code = BRL_CMD_FWINRT },
  { .code = BRL_CMD_FWINLTSKIP },
  { .code = BRL_CMD_FWINRTSKIP },
  { .code = BRL_CMD_PRNBWIN},
  { .code = BRL_CMD_NXNBWIN},
  { .code = BRL_CMD_LNBEG },
  { .code = BRL_CMD_LNEND },
  { .code = BRL_CMD_CHRLT },
  { .code = BRL_CMD_CHRRT },
  { .code = BRL_CMD_HWINLT },
  { .code = BRL_CMD_HWINRT },
  { .code = BRL_CMD_BLK(SETLEFT) },
};

static const CommandListEntry commandList_window[] = {
  { .code = BRL_CMD_GUI_TITLE },
  { .code = BRL_CMD_GUI_BRL_ACTIONS },
  { .code = BRL_CMD_GUI_HOME },
  { .code = BRL_CMD_GUI_BACK },
  { .code = BRL_CMD_GUI_DEV_SETTINGS },
  { .code = BRL_CMD_GUI_DEV_OPTIONS },
  { .code = BRL_CMD_GUI_APP_LIST },
  { .code = BRL_CMD_GUI_APP_MENU },
  { .code = BRL_CMD_GUI_APP_ALERTS },
  { .code = BRL_CMD_GUI_AREA_ACTV },
  { .code = BRL_CMD_GUI_AREA_PREV },
  { .code = BRL_CMD_GUI_AREA_NEXT },
  { .code = BRL_CMD_GUI_ITEM_FRST },
  { .code = BRL_CMD_GUI_ITEM_PREV },
  { .code = BRL_CMD_GUI_ITEM_NEXT },
  { .code = BRL_CMD_GUI_ITEM_LAST },
};

static const CommandListEntry commandList_clipboard[] = {
  { .code = BRL_CMD_BLK(CLIP_NEW) },
  { .code = BRL_CMD_BLK(CLIP_ADD) },
  { .code = BRL_CMD_BLK(COPY_LINE) },
  { .code = BRL_CMD_BLK(COPY_RECT) },
  { .code = BRL_CMD_BLK(CLIP_COPY) },
  { .code = BRL_CMD_BLK(CLIP_APPEND) },
  { .code = BRL_CMD_PASTE },
  { .code = BRL_CMD_BLK(PASTE_HISTORY) },
  { .code = BRL_CMD_PRSEARCH },
  { .code = BRL_CMD_NXSEARCH },
  { .code = BRL_CMD_CLIP_SAVE },
  { .code = BRL_CMD_CLIP_RESTORE },
};

static const CommandListEntry commandList_text[] = {
  { .code = BRL_CMD_TXTSEL_CLEAR },
  { .code = BRL_CMD_BLK(TXTSEL_SET) },
  { .code = BRL_CMD_BLK(TXTSEL_START) },
  { .code = BRL_CMD_TXTSEL_ALL },
  { .code = BRL_CMD_HOST_COPY },
  { .code = BRL_CMD_HOST_CUT },
  { .code = BRL_CMD_HOST_PASTE },
};

static const CommandListEntry commandList_feature[] = {
  { .code = BRL_CMD_TOUCH_NAV },
  { .code = BRL_CMD_AUTOREPEAT },
  { .code = BRL_CMD_SIXDOTS },
  { .code = BRL_CMD_CONTRACTED },
  { .code = BRL_CMD_COMPBRL6 },
  { .code = BRL_CMD_SKPIDLNS },
  { .code = BRL_CMD_SKPBLNKWINS },
  { .code = BRL_CMD_SLIDEWIN },
  { .code = BRL_CMD_CSRTRK },
  { .code = BRL_CMD_CSRSIZE },
  { .code = BRL_CMD_CSRVIS },
  { .code = BRL_CMD_CSRHIDE },
  { .code = BRL_CMD_CSRBLINK },
  { .code = BRL_CMD_ATTRVIS },
  { .code = BRL_CMD_ATTRBLINK },
  { .code = BRL_CMD_CAPBLINK },
  { .code = BRL_CMD_TUNES },
  { .code = BRL_CMD_BLK(SET_TEXT_TABLE) },
  { .code = BRL_CMD_BLK(SET_ATTRIBUTES_TABLE) },
  { .code = BRL_CMD_BLK(SET_CONTRACTION_TABLE) },
  { .code = BRL_CMD_BLK(SET_KEYBOARD_TABLE) },
  { .code = BRL_CMD_BLK(SET_LANGUAGE_PROFILE) },
};

static const CommandListEntry commandList_menu[] = {
  { .code = BRL_CMD_MENU_PREV_ITEM },
  { .code = BRL_CMD_MENU_NEXT_ITEM },
  { .code = BRL_CMD_MENU_FIRST_ITEM },
  { .code = BRL_CMD_MENU_LAST_ITEM },
  { .code = BRL_CMD_MENU_PREV_SETTING },
  { .code = BRL_CMD_MENU_NEXT_SETTING },
  { .code = BRL_CMD_MENU_PREV_LEVEL },
  { .code = BRL_CMD_PREFSAVE },
  { .code = BRL_CMD_PREFLOAD },
  { .code = BRL_CMD_PREFRESET },
};

static const CommandListEntry commandList_say[] = {
  { .code = BRL_CMD_MUTE },
  { .code = BRL_CMD_SAY_LINE },
  { .code = BRL_CMD_SAY_ALL },
  { .code = BRL_CMD_SAY_ABOVE },
  { .code = BRL_CMD_SAY_BELOW },
  { .code = BRL_CMD_SPKHOME },
  { .code = BRL_CMD_SAY_SOFTER },
  { .code = BRL_CMD_SAY_LOUDER },
  { .code = BRL_CMD_SAY_SLOWER },
  { .code = BRL_CMD_SAY_FASTER },
  { .code = BRL_CMD_SAY_LOWER },
  { .code = BRL_CMD_SAY_HIGHER },
  { .code = BRL_CMD_AUTOSPEAK },
  { .code = BRL_CMD_ASPK_SEL_LINE },
  { .code = BRL_CMD_ASPK_SEL_CHAR },
  { .code = BRL_CMD_ASPK_INS_CHARS },
  { .code = BRL_CMD_ASPK_DEL_CHARS },
  { .code = BRL_CMD_ASPK_REP_CHARS },
  { .code = BRL_CMD_ASPK_CMP_WORDS },
  { .code = BRL_CMD_ASPK_INDENT },
};

static const CommandListEntry commandList_speak[] = {
  { .code = BRL_CMD_BLK(ROUTE_SPEECH) },
  { .code = BRL_CMD_SPEAK_CURR_CHAR },
  { .code = BRL_CMD_DESC_CURR_CHAR },
  { .code = BRL_CMD_SPEAK_PREV_CHAR },
  { .code = BRL_CMD_SPEAK_NEXT_CHAR },
  { .code = BRL_CMD_SPEAK_FRST_CHAR },
  { .code = BRL_CMD_SPEAK_LAST_CHAR },
  { .code = BRL_CMD_SPEAK_CURR_WORD },
  { .code = BRL_CMD_SPELL_CURR_WORD },
  { .code = BRL_CMD_SPEAK_PREV_WORD },
  { .code = BRL_CMD_SPEAK_NEXT_WORD },
  { .code = BRL_CMD_SPEAK_CURR_LINE },
  { .code = BRL_CMD_SPEAK_PREV_LINE },
  { .code = BRL_CMD_SPEAK_NEXT_LINE },
  { .code = BRL_CMD_SPEAK_FRST_LINE },
  { .code = BRL_CMD_SPEAK_LAST_LINE },
  { .code = BRL_CMD_SPEAK_INDENT },
  { .code = BRL_CMD_SPEAK_CURR_LOCN },
  { .code = BRL_CMD_SHOW_CURR_LOCN },
};

static const CommandListEntry commandList_input[] = {
  { .code = BRL_CMD_BLK(PASSDOTS) },
  { .code = BRL_CMD_BLK(PASSCHAR) },
  { .code = BRL_CMD_KEY(BACKSPACE) },
  { .code = BRL_CMD_KEY(ENTER) },
  { .code = BRL_CMD_KEY(TAB) },
  { .code = BRL_CMD_KEY(CURSOR_LEFT) },
  { .code = BRL_CMD_KEY(CURSOR_RIGHT) },
  { .code = BRL_CMD_KEY(CURSOR_UP) },
  { .code = BRL_CMD_KEY(CURSOR_DOWN) },
  { .code = BRL_CMD_KEY(PAGE_UP) },
  { .code = BRL_CMD_KEY(PAGE_DOWN) },
  { .code = BRL_CMD_KEY(HOME) },
  { .code = BRL_CMD_KEY(END) },
  { .code = BRL_CMD_KEY(INSERT) },
  { .code = BRL_CMD_KEY(DELETE) },
  { .code = BRL_CMD_UNSTICK },
  { .code = BRL_CMD_UPPER },
  { .code = BRL_CMD_SHIFT },
  { .code = BRL_CMD_CONTROL },
  { .code = BRL_CMD_META },
  { .code = BRL_CMD_ALTGR },
  { .code = BRL_CMD_GUI },
  { .code = BRL_CMD_KEY(ESCAPE) },
  { .code = BRL_CMD_KEY(FUNCTION) },
  { .code = BRL_CMD_BLK(SWITCHVT) },
  { .code = BRL_CMD_SWITCHVT_PREV },
  { .code = BRL_CMD_SWITCHVT_NEXT },
  { .code = BRL_CMD_BLK(SELECTVT) },
  { .code = BRL_CMD_SELECTVT_PREV },
  { .code = BRL_CMD_SELECTVT_NEXT },
  { .code = BRL_CMD_BRLKBD },
  { .code = BRL_CMD_BRLUCDOTS },
};

static const CommandListEntry commandList_special[] = {
  { .code = BRL_CMD_BLK(SETMARK) },
  { .code = BRL_CMD_BLK(GOTOMARK) },
  { .code = BRL_CMD_REFRESH },
  { .code = BRL_CMD_BLK(REFRESH_LINE) },
  { .code = BRL_CMD_RESTARTBRL },
  { .code = BRL_CMD_BRL_STOP },
  { .code = BRL_CMD_BRL_START },
  { .code = BRL_CMD_RESTARTSPEECH },
  { .code = BRL_CMD_SPK_STOP },
  { .code = BRL_CMD_SPK_START },
  { .code = BRL_CMD_SCR_STOP },
  { .code = BRL_CMD_SCR_START },
};

static const CommandListEntry commandList_internal[] = {
  { .code = BRL_CMD_NOOP },
  { .code = BRL_CMD_OFFLINE },
  { .code = BRL_CMD_BLK(ALERT) },
  { .code = BRL_CMD_BLK(PASSXT) },
  { .code = BRL_CMD_BLK(PASSAT) },
  { .code = BRL_CMD_BLK(PASSPS2) },
  { .code = BRL_CMD_BLK(TOUCH_AT) },
  { .code = BRL_CMD_BLK(MACRO) },
  { .code = BRL_CMD_BLK(HOSTCMD) },
};

#define COMMAND_LIST(name) .commands = { \
  .table = commandList_##name, \
  .count = ARRAY_COUNT(commandList_##name), \
}

const CommandGroupEntry commandGroupTable[] = {
  { COMMAND_LIST(modes),
    .after = commandGroupHook_hotkeys,
    .name = "Special Modes"
  },

  { COMMAND_LIST(cursor),
    .name = "Cursor Functions"
  },

  { COMMAND_LIST(vertical),
    .name = "Vertical Navigation"
  },

  { COMMAND_LIST(horizontal),
    .name = "Horizontal Navigation"
  },

  { COMMAND_LIST(window),
    .name = "Window Navigation"
  },

  { COMMAND_LIST(clipboard),
    .name = "Clipboard Functions"
  },

  { COMMAND_LIST(text),
    .name = "Text Selection and the Host Clipboard"
  },

  { COMMAND_LIST(feature),
    .name = "Configuration Functions"
  },

  { COMMAND_LIST(menu),
    .name = "Menu Operations"
  },

  { COMMAND_LIST(say),
    .name = "Speech Functions"
  },

  { COMMAND_LIST(speak),
    .name = "Speech Navigation"
  },

  { COMMAND_LIST(input),
    .before = commandGroupHook_keyboardFunctions,
    .name = "Keyboard Input"
  },

  { COMMAND_LIST(special),
    .name = "Special Functions"
  },

  { COMMAND_LIST(internal),
    .name = "Internal Functions"
  },
};

const unsigned char commandGroupCount = ARRAY_COUNT(commandGroupTable);
