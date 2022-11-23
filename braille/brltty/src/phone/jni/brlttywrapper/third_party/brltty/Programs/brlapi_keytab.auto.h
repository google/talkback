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

#ifndef BRLAPI_INCLUDED_KEYTAB
#define BRLAPI_INCLUDED_KEYTAB

#ifdef __cplusplus
extern "C" {
#endif /* __cplusplus */

{
  .name = "NOOP",
  .code = (BRLAPI_KEY_TYPE_CMD | BRLAPI_KEY_CMD_NOOP)
},
{
  .name = "LNUP",
  .code = (BRLAPI_KEY_TYPE_CMD | BRLAPI_KEY_CMD_LNUP)
},
{
  .name = "LNDN",
  .code = (BRLAPI_KEY_TYPE_CMD | BRLAPI_KEY_CMD_LNDN)
},
{
  .name = "WINUP",
  .code = (BRLAPI_KEY_TYPE_CMD | BRLAPI_KEY_CMD_WINUP)
},
{
  .name = "WINDN",
  .code = (BRLAPI_KEY_TYPE_CMD | BRLAPI_KEY_CMD_WINDN)
},
{
  .name = "PRDIFLN",
  .code = (BRLAPI_KEY_TYPE_CMD | BRLAPI_KEY_CMD_PRDIFLN)
},
{
  .name = "NXDIFLN",
  .code = (BRLAPI_KEY_TYPE_CMD | BRLAPI_KEY_CMD_NXDIFLN)
},
{
  .name = "ATTRUP",
  .code = (BRLAPI_KEY_TYPE_CMD | BRLAPI_KEY_CMD_ATTRUP)
},
{
  .name = "ATTRDN",
  .code = (BRLAPI_KEY_TYPE_CMD | BRLAPI_KEY_CMD_ATTRDN)
},
{
  .name = "TOP",
  .code = (BRLAPI_KEY_TYPE_CMD | BRLAPI_KEY_CMD_TOP)
},
{
  .name = "BOT",
  .code = (BRLAPI_KEY_TYPE_CMD | BRLAPI_KEY_CMD_BOT)
},
{
  .name = "TOP_LEFT",
  .code = (BRLAPI_KEY_TYPE_CMD | BRLAPI_KEY_CMD_TOP_LEFT)
},
{
  .name = "BOT_LEFT",
  .code = (BRLAPI_KEY_TYPE_CMD | BRLAPI_KEY_CMD_BOT_LEFT)
},
{
  .name = "PRPGRPH",
  .code = (BRLAPI_KEY_TYPE_CMD | BRLAPI_KEY_CMD_PRPGRPH)
},
{
  .name = "NXPGRPH",
  .code = (BRLAPI_KEY_TYPE_CMD | BRLAPI_KEY_CMD_NXPGRPH)
},
{
  .name = "PRPROMPT",
  .code = (BRLAPI_KEY_TYPE_CMD | BRLAPI_KEY_CMD_PRPROMPT)
},
{
  .name = "NXPROMPT",
  .code = (BRLAPI_KEY_TYPE_CMD | BRLAPI_KEY_CMD_NXPROMPT)
},
{
  .name = "PRSEARCH",
  .code = (BRLAPI_KEY_TYPE_CMD | BRLAPI_KEY_CMD_PRSEARCH)
},
{
  .name = "NXSEARCH",
  .code = (BRLAPI_KEY_TYPE_CMD | BRLAPI_KEY_CMD_NXSEARCH)
},
{
  .name = "CHRLT",
  .code = (BRLAPI_KEY_TYPE_CMD | BRLAPI_KEY_CMD_CHRLT)
},
{
  .name = "CHRRT",
  .code = (BRLAPI_KEY_TYPE_CMD | BRLAPI_KEY_CMD_CHRRT)
},
{
  .name = "HWINLT",
  .code = (BRLAPI_KEY_TYPE_CMD | BRLAPI_KEY_CMD_HWINLT)
},
{
  .name = "HWINRT",
  .code = (BRLAPI_KEY_TYPE_CMD | BRLAPI_KEY_CMD_HWINRT)
},
{
  .name = "FWINLT",
  .code = (BRLAPI_KEY_TYPE_CMD | BRLAPI_KEY_CMD_FWINLT)
},
{
  .name = "FWINRT",
  .code = (BRLAPI_KEY_TYPE_CMD | BRLAPI_KEY_CMD_FWINRT)
},
{
  .name = "FWINLTSKIP",
  .code = (BRLAPI_KEY_TYPE_CMD | BRLAPI_KEY_CMD_FWINLTSKIP)
},
{
  .name = "FWINRTSKIP",
  .code = (BRLAPI_KEY_TYPE_CMD | BRLAPI_KEY_CMD_FWINRTSKIP)
},
{
  .name = "LNBEG",
  .code = (BRLAPI_KEY_TYPE_CMD | BRLAPI_KEY_CMD_LNBEG)
},
{
  .name = "LNEND",
  .code = (BRLAPI_KEY_TYPE_CMD | BRLAPI_KEY_CMD_LNEND)
},
{
  .name = "HOME",
  .code = (BRLAPI_KEY_TYPE_CMD | BRLAPI_KEY_CMD_HOME)
},
{
  .name = "BACK",
  .code = (BRLAPI_KEY_TYPE_CMD | BRLAPI_KEY_CMD_BACK)
},
{
  .name = "RETURN",
  .code = (BRLAPI_KEY_TYPE_CMD | BRLAPI_KEY_CMD_RETURN)
},
{
  .name = "FREEZE",
  .code = (BRLAPI_KEY_TYPE_CMD | BRLAPI_KEY_CMD_FREEZE)
},
{
  .name = "DISPMD",
  .code = (BRLAPI_KEY_TYPE_CMD | BRLAPI_KEY_CMD_DISPMD)
},
{
  .name = "SIXDOTS",
  .code = (BRLAPI_KEY_TYPE_CMD | BRLAPI_KEY_CMD_SIXDOTS)
},
{
  .name = "SLIDEWIN",
  .code = (BRLAPI_KEY_TYPE_CMD | BRLAPI_KEY_CMD_SLIDEWIN)
},
{
  .name = "SKPIDLNS",
  .code = (BRLAPI_KEY_TYPE_CMD | BRLAPI_KEY_CMD_SKPIDLNS)
},
{
  .name = "SKPBLNKWINS",
  .code = (BRLAPI_KEY_TYPE_CMD | BRLAPI_KEY_CMD_SKPBLNKWINS)
},
{
  .name = "CSRVIS",
  .code = (BRLAPI_KEY_TYPE_CMD | BRLAPI_KEY_CMD_CSRVIS)
},
{
  .name = "CSRHIDE",
  .code = (BRLAPI_KEY_TYPE_CMD | BRLAPI_KEY_CMD_CSRHIDE)
},
{
  .name = "CSRTRK",
  .code = (BRLAPI_KEY_TYPE_CMD | BRLAPI_KEY_CMD_CSRTRK)
},
{
  .name = "CSRSIZE",
  .code = (BRLAPI_KEY_TYPE_CMD | BRLAPI_KEY_CMD_CSRSIZE)
},
{
  .name = "CSRBLINK",
  .code = (BRLAPI_KEY_TYPE_CMD | BRLAPI_KEY_CMD_CSRBLINK)
},
{
  .name = "ATTRVIS",
  .code = (BRLAPI_KEY_TYPE_CMD | BRLAPI_KEY_CMD_ATTRVIS)
},
{
  .name = "ATTRBLINK",
  .code = (BRLAPI_KEY_TYPE_CMD | BRLAPI_KEY_CMD_ATTRBLINK)
},
{
  .name = "CAPBLINK",
  .code = (BRLAPI_KEY_TYPE_CMD | BRLAPI_KEY_CMD_CAPBLINK)
},
{
  .name = "TUNES",
  .code = (BRLAPI_KEY_TYPE_CMD | BRLAPI_KEY_CMD_TUNES)
},
{
  .name = "AUTOREPEAT",
  .code = (BRLAPI_KEY_TYPE_CMD | BRLAPI_KEY_CMD_AUTOREPEAT)
},
{
  .name = "AUTOSPEAK",
  .code = (BRLAPI_KEY_TYPE_CMD | BRLAPI_KEY_CMD_AUTOSPEAK)
},
{
  .name = "HELP",
  .code = (BRLAPI_KEY_TYPE_CMD | BRLAPI_KEY_CMD_HELP)
},
{
  .name = "INFO",
  .code = (BRLAPI_KEY_TYPE_CMD | BRLAPI_KEY_CMD_INFO)
},
{
  .name = "LEARN",
  .code = (BRLAPI_KEY_TYPE_CMD | BRLAPI_KEY_CMD_LEARN)
},
{
  .name = "PREFMENU",
  .code = (BRLAPI_KEY_TYPE_CMD | BRLAPI_KEY_CMD_PREFMENU)
},
{
  .name = "PREFSAVE",
  .code = (BRLAPI_KEY_TYPE_CMD | BRLAPI_KEY_CMD_PREFSAVE)
},
{
  .name = "PREFLOAD",
  .code = (BRLAPI_KEY_TYPE_CMD | BRLAPI_KEY_CMD_PREFLOAD)
},
{
  .name = "MENU_FIRST_ITEM",
  .code = (BRLAPI_KEY_TYPE_CMD | BRLAPI_KEY_CMD_MENU_FIRST_ITEM)
},
{
  .name = "MENU_LAST_ITEM",
  .code = (BRLAPI_KEY_TYPE_CMD | BRLAPI_KEY_CMD_MENU_LAST_ITEM)
},
{
  .name = "MENU_PREV_ITEM",
  .code = (BRLAPI_KEY_TYPE_CMD | BRLAPI_KEY_CMD_MENU_PREV_ITEM)
},
{
  .name = "MENU_NEXT_ITEM",
  .code = (BRLAPI_KEY_TYPE_CMD | BRLAPI_KEY_CMD_MENU_NEXT_ITEM)
},
{
  .name = "MENU_PREV_SETTING",
  .code = (BRLAPI_KEY_TYPE_CMD | BRLAPI_KEY_CMD_MENU_PREV_SETTING)
},
{
  .name = "MENU_NEXT_SETTING",
  .code = (BRLAPI_KEY_TYPE_CMD | BRLAPI_KEY_CMD_MENU_NEXT_SETTING)
},
{
  .name = "MUTE",
  .code = (BRLAPI_KEY_TYPE_CMD | BRLAPI_KEY_CMD_MUTE)
},
{
  .name = "SPKHOME",
  .code = (BRLAPI_KEY_TYPE_CMD | BRLAPI_KEY_CMD_SPKHOME)
},
{
  .name = "SAY_LINE",
  .code = (BRLAPI_KEY_TYPE_CMD | BRLAPI_KEY_CMD_SAY_LINE)
},
{
  .name = "SAY_ABOVE",
  .code = (BRLAPI_KEY_TYPE_CMD | BRLAPI_KEY_CMD_SAY_ABOVE)
},
{
  .name = "SAY_BELOW",
  .code = (BRLAPI_KEY_TYPE_CMD | BRLAPI_KEY_CMD_SAY_BELOW)
},
{
  .name = "SAY_SLOWER",
  .code = (BRLAPI_KEY_TYPE_CMD | BRLAPI_KEY_CMD_SAY_SLOWER)
},
{
  .name = "SAY_FASTER",
  .code = (BRLAPI_KEY_TYPE_CMD | BRLAPI_KEY_CMD_SAY_FASTER)
},
{
  .name = "SAY_SOFTER",
  .code = (BRLAPI_KEY_TYPE_CMD | BRLAPI_KEY_CMD_SAY_SOFTER)
},
{
  .name = "SAY_LOUDER",
  .code = (BRLAPI_KEY_TYPE_CMD | BRLAPI_KEY_CMD_SAY_LOUDER)
},
{
  .name = "SWITCHVT_PREV",
  .code = (BRLAPI_KEY_TYPE_CMD | BRLAPI_KEY_CMD_SWITCHVT_PREV)
},
{
  .name = "SWITCHVT_NEXT",
  .code = (BRLAPI_KEY_TYPE_CMD | BRLAPI_KEY_CMD_SWITCHVT_NEXT)
},
{
  .name = "CSRJMP_VERT",
  .code = (BRLAPI_KEY_TYPE_CMD | BRLAPI_KEY_CMD_CSRJMP_VERT)
},
{
  .name = "PASTE",
  .code = (BRLAPI_KEY_TYPE_CMD | BRLAPI_KEY_CMD_PASTE)
},
{
  .name = "RESTARTBRL",
  .code = (BRLAPI_KEY_TYPE_CMD | BRLAPI_KEY_CMD_RESTARTBRL)
},
{
  .name = "RESTARTSPEECH",
  .code = (BRLAPI_KEY_TYPE_CMD | BRLAPI_KEY_CMD_RESTARTSPEECH)
},
{
  .name = "OFFLINE",
  .code = (BRLAPI_KEY_TYPE_CMD | BRLAPI_KEY_CMD_OFFLINE)
},
{
  .name = "SHIFT",
  .code = (BRLAPI_KEY_TYPE_CMD | BRLAPI_KEY_CMD_SHIFT)
},
{
  .name = "UPPER",
  .code = (BRLAPI_KEY_TYPE_CMD | BRLAPI_KEY_CMD_UPPER)
},
{
  .name = "CONTROL",
  .code = (BRLAPI_KEY_TYPE_CMD | BRLAPI_KEY_CMD_CONTROL)
},
{
  .name = "META",
  .code = (BRLAPI_KEY_TYPE_CMD | BRLAPI_KEY_CMD_META)
},
{
  .name = "TIME",
  .code = (BRLAPI_KEY_TYPE_CMD | BRLAPI_KEY_CMD_TIME)
},
{
  .name = "MENU_PREV_LEVEL",
  .code = (BRLAPI_KEY_TYPE_CMD | BRLAPI_KEY_CMD_MENU_PREV_LEVEL)
},
{
  .name = "ASPK_SEL_LINE",
  .code = (BRLAPI_KEY_TYPE_CMD | BRLAPI_KEY_CMD_ASPK_SEL_LINE)
},
{
  .name = "ASPK_SEL_CHAR",
  .code = (BRLAPI_KEY_TYPE_CMD | BRLAPI_KEY_CMD_ASPK_SEL_CHAR)
},
{
  .name = "ASPK_INS_CHARS",
  .code = (BRLAPI_KEY_TYPE_CMD | BRLAPI_KEY_CMD_ASPK_INS_CHARS)
},
{
  .name = "ASPK_DEL_CHARS",
  .code = (BRLAPI_KEY_TYPE_CMD | BRLAPI_KEY_CMD_ASPK_DEL_CHARS)
},
{
  .name = "ASPK_REP_CHARS",
  .code = (BRLAPI_KEY_TYPE_CMD | BRLAPI_KEY_CMD_ASPK_REP_CHARS)
},
{
  .name = "ASPK_CMP_WORDS",
  .code = (BRLAPI_KEY_TYPE_CMD | BRLAPI_KEY_CMD_ASPK_CMP_WORDS)
},
{
  .name = "SPEAK_CURR_CHAR",
  .code = (BRLAPI_KEY_TYPE_CMD | BRLAPI_KEY_CMD_SPEAK_CURR_CHAR)
},
{
  .name = "SPEAK_PREV_CHAR",
  .code = (BRLAPI_KEY_TYPE_CMD | BRLAPI_KEY_CMD_SPEAK_PREV_CHAR)
},
{
  .name = "SPEAK_NEXT_CHAR",
  .code = (BRLAPI_KEY_TYPE_CMD | BRLAPI_KEY_CMD_SPEAK_NEXT_CHAR)
},
{
  .name = "SPEAK_CURR_WORD",
  .code = (BRLAPI_KEY_TYPE_CMD | BRLAPI_KEY_CMD_SPEAK_CURR_WORD)
},
{
  .name = "SPEAK_PREV_WORD",
  .code = (BRLAPI_KEY_TYPE_CMD | BRLAPI_KEY_CMD_SPEAK_PREV_WORD)
},
{
  .name = "SPEAK_NEXT_WORD",
  .code = (BRLAPI_KEY_TYPE_CMD | BRLAPI_KEY_CMD_SPEAK_NEXT_WORD)
},
{
  .name = "SPEAK_CURR_LINE",
  .code = (BRLAPI_KEY_TYPE_CMD | BRLAPI_KEY_CMD_SPEAK_CURR_LINE)
},
{
  .name = "SPEAK_PREV_LINE",
  .code = (BRLAPI_KEY_TYPE_CMD | BRLAPI_KEY_CMD_SPEAK_PREV_LINE)
},
{
  .name = "SPEAK_NEXT_LINE",
  .code = (BRLAPI_KEY_TYPE_CMD | BRLAPI_KEY_CMD_SPEAK_NEXT_LINE)
},
{
  .name = "SPEAK_FRST_CHAR",
  .code = (BRLAPI_KEY_TYPE_CMD | BRLAPI_KEY_CMD_SPEAK_FRST_CHAR)
},
{
  .name = "SPEAK_LAST_CHAR",
  .code = (BRLAPI_KEY_TYPE_CMD | BRLAPI_KEY_CMD_SPEAK_LAST_CHAR)
},
{
  .name = "SPEAK_FRST_LINE",
  .code = (BRLAPI_KEY_TYPE_CMD | BRLAPI_KEY_CMD_SPEAK_FRST_LINE)
},
{
  .name = "SPEAK_LAST_LINE",
  .code = (BRLAPI_KEY_TYPE_CMD | BRLAPI_KEY_CMD_SPEAK_LAST_LINE)
},
{
  .name = "DESC_CURR_CHAR",
  .code = (BRLAPI_KEY_TYPE_CMD | BRLAPI_KEY_CMD_DESC_CURR_CHAR)
},
{
  .name = "SPELL_CURR_WORD",
  .code = (BRLAPI_KEY_TYPE_CMD | BRLAPI_KEY_CMD_SPELL_CURR_WORD)
},
{
  .name = "ROUTE_CURR_LOCN",
  .code = (BRLAPI_KEY_TYPE_CMD | BRLAPI_KEY_CMD_ROUTE_CURR_LOCN)
},
{
  .name = "SPEAK_CURR_LOCN",
  .code = (BRLAPI_KEY_TYPE_CMD | BRLAPI_KEY_CMD_SPEAK_CURR_LOCN)
},
{
  .name = "SHOW_CURR_LOCN",
  .code = (BRLAPI_KEY_TYPE_CMD | BRLAPI_KEY_CMD_SHOW_CURR_LOCN)
},
{
  .name = "CLIP_SAVE",
  .code = (BRLAPI_KEY_TYPE_CMD | BRLAPI_KEY_CMD_CLIP_SAVE)
},
{
  .name = "CLIP_RESTORE",
  .code = (BRLAPI_KEY_TYPE_CMD | BRLAPI_KEY_CMD_CLIP_RESTORE)
},
{
  .name = "BRLUCDOTS",
  .code = (BRLAPI_KEY_TYPE_CMD | BRLAPI_KEY_CMD_BRLUCDOTS)
},
{
  .name = "BRLKBD",
  .code = (BRLAPI_KEY_TYPE_CMD | BRLAPI_KEY_CMD_BRLKBD)
},
{
  .name = "UNSTICK",
  .code = (BRLAPI_KEY_TYPE_CMD | BRLAPI_KEY_CMD_UNSTICK)
},
{
  .name = "ALTGR",
  .code = (BRLAPI_KEY_TYPE_CMD | BRLAPI_KEY_CMD_ALTGR)
},
{
  .name = "GUI",
  .code = (BRLAPI_KEY_TYPE_CMD | BRLAPI_KEY_CMD_GUI)
},
{
  .name = "BRL_STOP",
  .code = (BRLAPI_KEY_TYPE_CMD | BRLAPI_KEY_CMD_BRL_STOP)
},
{
  .name = "BRL_START",
  .code = (BRLAPI_KEY_TYPE_CMD | BRLAPI_KEY_CMD_BRL_START)
},
{
  .name = "SPK_STOP",
  .code = (BRLAPI_KEY_TYPE_CMD | BRLAPI_KEY_CMD_SPK_STOP)
},
{
  .name = "SPK_START",
  .code = (BRLAPI_KEY_TYPE_CMD | BRLAPI_KEY_CMD_SPK_START)
},
{
  .name = "SCR_STOP",
  .code = (BRLAPI_KEY_TYPE_CMD | BRLAPI_KEY_CMD_SCR_STOP)
},
{
  .name = "SCR_START",
  .code = (BRLAPI_KEY_TYPE_CMD | BRLAPI_KEY_CMD_SCR_START)
},
{
  .name = "SELECTVT_PREV",
  .code = (BRLAPI_KEY_TYPE_CMD | BRLAPI_KEY_CMD_SELECTVT_PREV)
},
{
  .name = "SELECTVT_NEXT",
  .code = (BRLAPI_KEY_TYPE_CMD | BRLAPI_KEY_CMD_SELECTVT_NEXT)
},
{
  .name = "PRNBWIN",
  .code = (BRLAPI_KEY_TYPE_CMD | BRLAPI_KEY_CMD_PRNBWIN)
},
{
  .name = "NXNBWIN",
  .code = (BRLAPI_KEY_TYPE_CMD | BRLAPI_KEY_CMD_NXNBWIN)
},
{
  .name = "TOUCH_NAV",
  .code = (BRLAPI_KEY_TYPE_CMD | BRLAPI_KEY_CMD_TOUCH_NAV)
},
{
  .name = "SPEAK_INDENT",
  .code = (BRLAPI_KEY_TYPE_CMD | BRLAPI_KEY_CMD_SPEAK_INDENT)
},
{
  .name = "ASPK_INDENT",
  .code = (BRLAPI_KEY_TYPE_CMD | BRLAPI_KEY_CMD_ASPK_INDENT)
},
{
  .name = "ROUTE",
  .code = (BRLAPI_KEY_TYPE_CMD | BRLAPI_KEY_CMD_ROUTE)
},
{
  .name = "CLIP_NEW",
  .code = (BRLAPI_KEY_TYPE_CMD | BRLAPI_KEY_CMD_CLIP_NEW)
},
{
  .name = "CUTBEGIN",
  .code = (BRLAPI_KEY_TYPE_CMD | BRLAPI_KEY_CMD_CUTBEGIN)
},
{
  .name = "CLIP_ADD",
  .code = (BRLAPI_KEY_TYPE_CMD | BRLAPI_KEY_CMD_CLIP_ADD)
},
{
  .name = "CUTAPPEND",
  .code = (BRLAPI_KEY_TYPE_CMD | BRLAPI_KEY_CMD_CUTAPPEND)
},
{
  .name = "COPY_RECT",
  .code = (BRLAPI_KEY_TYPE_CMD | BRLAPI_KEY_CMD_COPY_RECT)
},
{
  .name = "CUTRECT",
  .code = (BRLAPI_KEY_TYPE_CMD | BRLAPI_KEY_CMD_CUTRECT)
},
{
  .name = "COPY_LINE",
  .code = (BRLAPI_KEY_TYPE_CMD | BRLAPI_KEY_CMD_COPY_LINE)
},
{
  .name = "CUTLINE",
  .code = (BRLAPI_KEY_TYPE_CMD | BRLAPI_KEY_CMD_CUTLINE)
},
{
  .name = "SWITCHVT",
  .code = (BRLAPI_KEY_TYPE_CMD | BRLAPI_KEY_CMD_SWITCHVT)
},
{
  .name = "PRINDENT",
  .code = (BRLAPI_KEY_TYPE_CMD | BRLAPI_KEY_CMD_PRINDENT)
},
{
  .name = "NXINDENT",
  .code = (BRLAPI_KEY_TYPE_CMD | BRLAPI_KEY_CMD_NXINDENT)
},
{
  .name = "DESCCHAR",
  .code = (BRLAPI_KEY_TYPE_CMD | BRLAPI_KEY_CMD_DESCCHAR)
},
{
  .name = "SETLEFT",
  .code = (BRLAPI_KEY_TYPE_CMD | BRLAPI_KEY_CMD_SETLEFT)
},
{
  .name = "SETMARK",
  .code = (BRLAPI_KEY_TYPE_CMD | BRLAPI_KEY_CMD_SETMARK)
},
{
  .name = "GOTOMARK",
  .code = (BRLAPI_KEY_TYPE_CMD | BRLAPI_KEY_CMD_GOTOMARK)
},
{
  .name = "GOTOLINE",
  .code = (BRLAPI_KEY_TYPE_CMD | BRLAPI_KEY_CMD_GOTOLINE)
},
{
  .name = "PRDIFCHAR",
  .code = (BRLAPI_KEY_TYPE_CMD | BRLAPI_KEY_CMD_PRDIFCHAR)
},
{
  .name = "NXDIFCHAR",
  .code = (BRLAPI_KEY_TYPE_CMD | BRLAPI_KEY_CMD_NXDIFCHAR)
},
{
  .name = "CLIP_COPY",
  .code = (BRLAPI_KEY_TYPE_CMD | BRLAPI_KEY_CMD_CLIP_COPY)
},
{
  .name = "COPYCHARS",
  .code = (BRLAPI_KEY_TYPE_CMD | BRLAPI_KEY_CMD_COPYCHARS)
},
{
  .name = "CLIP_APPEND",
  .code = (BRLAPI_KEY_TYPE_CMD | BRLAPI_KEY_CMD_CLIP_APPEND)
},
{
  .name = "APNDCHARS",
  .code = (BRLAPI_KEY_TYPE_CMD | BRLAPI_KEY_CMD_APNDCHARS)
},
{
  .name = "PASTE_HISTORY",
  .code = (BRLAPI_KEY_TYPE_CMD | BRLAPI_KEY_CMD_PASTE_HISTORY)
},
{
  .name = "SET_TEXT_TABLE",
  .code = (BRLAPI_KEY_TYPE_CMD | BRLAPI_KEY_CMD_SET_TEXT_TABLE)
},
{
  .name = "SET_ATTRIBUTES_TABLE",
  .code = (BRLAPI_KEY_TYPE_CMD | BRLAPI_KEY_CMD_SET_ATTRIBUTES_TABLE)
},
{
  .name = "SET_CONTRACTION_TABLE",
  .code = (BRLAPI_KEY_TYPE_CMD | BRLAPI_KEY_CMD_SET_CONTRACTION_TABLE)
},
{
  .name = "SET_KEYBOARD_TABLE",
  .code = (BRLAPI_KEY_TYPE_CMD | BRLAPI_KEY_CMD_SET_KEYBOARD_TABLE)
},
{
  .name = "SET_LANGUAGE_PROFILE",
  .code = (BRLAPI_KEY_TYPE_CMD | BRLAPI_KEY_CMD_SET_LANGUAGE_PROFILE)
},
{
  .name = "SELECTVT",
  .code = (BRLAPI_KEY_TYPE_CMD | BRLAPI_KEY_CMD_SELECTVT)
},
{
  .name = "ALERT",
  .code = (BRLAPI_KEY_TYPE_CMD | BRLAPI_KEY_CMD_ALERT)
},
{
  .name = "PASSDOTS",
  .code = (BRLAPI_KEY_TYPE_CMD | BRLAPI_KEY_CMD_PASSDOTS)
},
{
  .name = "PASSAT",
  .code = (BRLAPI_KEY_TYPE_CMD | BRLAPI_KEY_CMD_PASSAT)
},
{
  .name = "PASSXT",
  .code = (BRLAPI_KEY_TYPE_CMD | BRLAPI_KEY_CMD_PASSXT)
},
{
  .name = "PASSPS2",
  .code = (BRLAPI_KEY_TYPE_CMD | BRLAPI_KEY_CMD_PASSPS2)
},
{
  .name = "CONTEXT",
  .code = (BRLAPI_KEY_TYPE_CMD | BRLAPI_KEY_CMD_CONTEXT)
},
{
  .name = "TOUCH_AT",
  .code = (BRLAPI_KEY_TYPE_CMD | BRLAPI_KEY_CMD_TOUCH_AT)
},
{
  .name = "LINEFEED",
  .code = (BRLAPI_KEY_TYPE_SYM | BRLAPI_KEY_SYM_LINEFEED)
},
{
  .name = "TAB",
  .code = (BRLAPI_KEY_TYPE_SYM | BRLAPI_KEY_SYM_TAB)
},
{
  .name = "BACKSPACE",
  .code = (BRLAPI_KEY_TYPE_SYM | BRLAPI_KEY_SYM_BACKSPACE)
},
{
  .name = "ESCAPE",
  .code = (BRLAPI_KEY_TYPE_SYM | BRLAPI_KEY_SYM_ESCAPE)
},
{
  .name = "LEFT",
  .code = (BRLAPI_KEY_TYPE_SYM | BRLAPI_KEY_SYM_LEFT)
},
{
  .name = "RIGHT",
  .code = (BRLAPI_KEY_TYPE_SYM | BRLAPI_KEY_SYM_RIGHT)
},
{
  .name = "UP",
  .code = (BRLAPI_KEY_TYPE_SYM | BRLAPI_KEY_SYM_UP)
},
{
  .name = "DOWN",
  .code = (BRLAPI_KEY_TYPE_SYM | BRLAPI_KEY_SYM_DOWN)
},
{
  .name = "PAGE_UP",
  .code = (BRLAPI_KEY_TYPE_SYM | BRLAPI_KEY_SYM_PAGE_UP)
},
{
  .name = "PAGE_DOWN",
  .code = (BRLAPI_KEY_TYPE_SYM | BRLAPI_KEY_SYM_PAGE_DOWN)
},
{
  .name = "HOME",
  .code = (BRLAPI_KEY_TYPE_SYM | BRLAPI_KEY_SYM_HOME)
},
{
  .name = "END",
  .code = (BRLAPI_KEY_TYPE_SYM | BRLAPI_KEY_SYM_END)
},
{
  .name = "INSERT",
  .code = (BRLAPI_KEY_TYPE_SYM | BRLAPI_KEY_SYM_INSERT)
},
{
  .name = "DELETE",
  .code = (BRLAPI_KEY_TYPE_SYM | BRLAPI_KEY_SYM_DELETE)
},
{
  .name = "F1",
  .code = BRLAPI_KEY_SYM_FUNCTION+0
},
{
  .name = "F2",
  .code = BRLAPI_KEY_SYM_FUNCTION+1
},
{
  .name = "F3",
  .code = BRLAPI_KEY_SYM_FUNCTION+2
},
{
  .name = "F4",
  .code = BRLAPI_KEY_SYM_FUNCTION+3
},
{
  .name = "F5",
  .code = BRLAPI_KEY_SYM_FUNCTION+4
},
{
  .name = "F6",
  .code = BRLAPI_KEY_SYM_FUNCTION+5
},
{
  .name = "F7",
  .code = BRLAPI_KEY_SYM_FUNCTION+6
},
{
  .name = "F8",
  .code = BRLAPI_KEY_SYM_FUNCTION+7
},
{
  .name = "F9",
  .code = BRLAPI_KEY_SYM_FUNCTION+8
},
{
  .name = "F10",
  .code = BRLAPI_KEY_SYM_FUNCTION+9
},
{
  .name = "F11",
  .code = BRLAPI_KEY_SYM_FUNCTION+10
},
{
  .name = "F12",
  .code = BRLAPI_KEY_SYM_FUNCTION+11
},
{
  .name = "F13",
  .code = BRLAPI_KEY_SYM_FUNCTION+12
},
{
  .name = "F14",
  .code = BRLAPI_KEY_SYM_FUNCTION+13
},
{
  .name = "F15",
  .code = BRLAPI_KEY_SYM_FUNCTION+14
},
{
  .name = "F16",
  .code = BRLAPI_KEY_SYM_FUNCTION+15
},
{
  .name = "F17",
  .code = BRLAPI_KEY_SYM_FUNCTION+16
},
{
  .name = "F18",
  .code = BRLAPI_KEY_SYM_FUNCTION+17
},
{
  .name = "F19",
  .code = BRLAPI_KEY_SYM_FUNCTION+18
},
{
  .name = "F20",
  .code = BRLAPI_KEY_SYM_FUNCTION+19
},
{
  .name = "F21",
  .code = BRLAPI_KEY_SYM_FUNCTION+20
},
{
  .name = "F22",
  .code = BRLAPI_KEY_SYM_FUNCTION+21
},
{
  .name = "F23",
  .code = BRLAPI_KEY_SYM_FUNCTION+22
},
{
  .name = "F24",
  .code = BRLAPI_KEY_SYM_FUNCTION+23
},
{
  .name = "F25",
  .code = BRLAPI_KEY_SYM_FUNCTION+24
},
{
  .name = "F26",
  .code = BRLAPI_KEY_SYM_FUNCTION+25
},
{
  .name = "F27",
  .code = BRLAPI_KEY_SYM_FUNCTION+26
},
{
  .name = "F28",
  .code = BRLAPI_KEY_SYM_FUNCTION+27
},
{
  .name = "F29",
  .code = BRLAPI_KEY_SYM_FUNCTION+28
},
{
  .name = "F30",
  .code = BRLAPI_KEY_SYM_FUNCTION+29
},
{
  .name = "F31",
  .code = BRLAPI_KEY_SYM_FUNCTION+30
},
{
  .name = "F32",
  .code = BRLAPI_KEY_SYM_FUNCTION+31
},
{
  .name = "F33",
  .code = BRLAPI_KEY_SYM_FUNCTION+32
},
{
  .name = "F34",
  .code = BRLAPI_KEY_SYM_FUNCTION+33
},
{
  .name = "F35",
  .code = BRLAPI_KEY_SYM_FUNCTION+34
},

#ifdef __cplusplus
}
#endif /* __cplusplus */

#endif /* BRLAPI_INCLUDED_KEYTAB */
