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
#include <errno.h>

#include "parameters.h"
#include "log.h"
#include "strfmt.h"
#include "file.h"
#include "system_linux.h"
#include "kbd.h"
#include "kbd_internal.h"

#ifdef HAVE_LINUX_UINPUT_H
#include <limits.h>
#include <dirent.h>
#include <fcntl.h>
#include <sys/ioctl.h>
#include <sys/stat.h>
#include <sys/socket.h>
#include <linux/types.h>
#include <linux/netlink.h>
#include <linux/input.h>
#include <linux/uinput.h>

#include "bitmask.h"
#include "async_alarm.h"
#include "async_io.h"

struct KeyboardMonitorExtensionStruct {
  struct {
    int socket;
    AsyncHandle monitor;
  } uevent;
};

struct KeyboardInstanceExtensionStruct {
  UinputObject *uinput;
  AsyncHandle udevDelay;

  struct {
    int descriptor;
    AsyncHandle monitor;
  } file;

  struct {
    char *path;
    int major;
    int minor;
  } device;
};

BEGIN_KEY_CODE_MAP
  [KEY_ESC] = KBD_KEY_ACTION(Escape),
  [KEY_1] = KBD_KEY_NUMBER(One),
  [KEY_2] = KBD_KEY_NUMBER(Two),
  [KEY_3] = KBD_KEY_NUMBER(Three),
  [KEY_4] = KBD_KEY_NUMBER(Four),
  [KEY_5] = KBD_KEY_NUMBER(Five),
  [KEY_6] = KBD_KEY_NUMBER(Six),
  [KEY_7] = KBD_KEY_NUMBER(Seven),
  [KEY_8] = KBD_KEY_NUMBER(Eight),
  [KEY_9] = KBD_KEY_NUMBER(Nine),
  [KEY_0] = KBD_KEY_NUMBER(Zero),
  [KEY_MINUS] = KBD_KEY_SYMBOL(Minus),
  [KEY_EQUAL] = KBD_KEY_SYMBOL(Equals),
  [KEY_BACKSPACE] = KBD_KEY_ACTION(DeleteBackward),
  [KEY_TAB] = KBD_KEY_ACTION(Tab),
  [KEY_Q] = KBD_KEY_LETTER(Q),
  [KEY_W] = KBD_KEY_LETTER(W),
  [KEY_E] = KBD_KEY_LETTER(E),
  [KEY_R] = KBD_KEY_LETTER(R),
  [KEY_T] = KBD_KEY_LETTER(T),
  [KEY_Y] = KBD_KEY_LETTER(Y),
  [KEY_U] = KBD_KEY_LETTER(U),
  [KEY_I] = KBD_KEY_LETTER(I),
  [KEY_O] = KBD_KEY_LETTER(O),
  [KEY_P] = KBD_KEY_LETTER(P),
  [KEY_LEFTBRACE] = KBD_KEY_SYMBOL(LeftBracket),
  [KEY_RIGHTBRACE] = KBD_KEY_SYMBOL(RightBracket),
  [KEY_ENTER] = KBD_KEY_ACTION(Enter),
  [KEY_LEFTCTRL] = KBD_KEY_MODIFIER(ControlLeft),
  [KEY_A] = KBD_KEY_LETTER(A),
  [KEY_S] = KBD_KEY_LETTER(S),
  [KEY_D] = KBD_KEY_LETTER(D),
  [KEY_F] = KBD_KEY_LETTER(F),
  [KEY_G] = KBD_KEY_LETTER(G),
  [KEY_H] = KBD_KEY_LETTER(H),
  [KEY_J] = KBD_KEY_LETTER(J),
  [KEY_K] = KBD_KEY_LETTER(K),
  [KEY_L] = KBD_KEY_LETTER(L),
  [KEY_SEMICOLON] = KBD_KEY_SYMBOL(Semicolon),
  [KEY_APOSTROPHE] = KBD_KEY_SYMBOL(Apostrophe),
  [KEY_GRAVE] = KBD_KEY_SYMBOL(Grave),
  [KEY_LEFTSHIFT] = KBD_KEY_MODIFIER(ShiftLeft),
  [KEY_BACKSLASH] = KBD_KEY_SYMBOL(Backslash),
  [KEY_Z] = KBD_KEY_LETTER(Z),
  [KEY_X] = KBD_KEY_LETTER(X),
  [KEY_C] = KBD_KEY_LETTER(C),
  [KEY_V] = KBD_KEY_LETTER(V),
  [KEY_B] = KBD_KEY_LETTER(B),
  [KEY_N] = KBD_KEY_LETTER(N),
  [KEY_M] = KBD_KEY_LETTER(M),
  [KEY_COMMA] = KBD_KEY_SYMBOL(Comma),
  [KEY_DOT] = KBD_KEY_SYMBOL(Period),
  [KEY_SLASH] = KBD_KEY_SYMBOL(Slash),
  [KEY_RIGHTSHIFT] = KBD_KEY_MODIFIER(ShiftRight),
  [KEY_KPASTERISK] = KBD_KEY_KPSYMBOL(Multiply),
  [KEY_LEFTALT] = KBD_KEY_MODIFIER(AltLeft),
  [KEY_SPACE] = KBD_KEY_SYMBOL(Space),
  [KEY_CAPSLOCK] = KBD_KEY_LOCK(Capitals),
  [KEY_F1] = KBD_KEY_FUNCTION(F1),
  [KEY_F2] = KBD_KEY_FUNCTION(F2),
  [KEY_F3] = KBD_KEY_FUNCTION(F3),
  [KEY_F4] = KBD_KEY_FUNCTION(F4),
  [KEY_F5] = KBD_KEY_FUNCTION(F5),
  [KEY_F6] = KBD_KEY_FUNCTION(F6),
  [KEY_F7] = KBD_KEY_FUNCTION(F7),
  [KEY_F8] = KBD_KEY_FUNCTION(F8),
  [KEY_F9] = KBD_KEY_FUNCTION(F9),
  [KEY_F10] = KBD_KEY_FUNCTION(F10),
  [KEY_NUMLOCK] = KBD_KEY_LOCK(Numbers),
  [KEY_SCROLLLOCK] = KBD_KEY_LOCK(Scroll),
  [KEY_KP7] = KBD_KEY_KPNUMBER(Seven),
  [KEY_KP8] = KBD_KEY_KPNUMBER(Eight),
  [KEY_KP9] = KBD_KEY_KPNUMBER(Nine),
  [KEY_KPMINUS] = KBD_KEY_KPSYMBOL(Minus),
  [KEY_KP4] = KBD_KEY_KPNUMBER(Four),
  [KEY_KP5] = KBD_KEY_KPNUMBER(Five),
  [KEY_KP6] = KBD_KEY_KPNUMBER(Six),
  [KEY_KPPLUS] = KBD_KEY_KPSYMBOL(Plus),
  [KEY_KP1] = KBD_KEY_KPNUMBER(One),
  [KEY_KP2] = KBD_KEY_KPNUMBER(Two),
  [KEY_KP3] = KBD_KEY_KPNUMBER(Three),
  [KEY_KP0] = KBD_KEY_KPNUMBER(Zero),
  [KEY_KPDOT] = KBD_KEY_KPSYMBOL(Period),
  [KEY_ZENKAKUHANKAKU] = KBD_KEY_UNMAPPED,
  [KEY_102ND] = KBD_KEY_SYMBOL(Europe2),
  [KEY_F11] = KBD_KEY_FUNCTION(F11),
  [KEY_F12] = KBD_KEY_FUNCTION(F12),
  [KEY_RO] = KBD_KEY_UNMAPPED,
  [KEY_KATAKANA] = KBD_KEY_UNMAPPED,
  [KEY_HIRAGANA] = KBD_KEY_UNMAPPED,
  [KEY_HENKAN] = KBD_KEY_UNMAPPED,
  [KEY_KATAKANAHIRAGANA] = KBD_KEY_UNMAPPED,
  [KEY_MUHENKAN] = KBD_KEY_UNMAPPED,
  [KEY_KPJPCOMMA] = KBD_KEY_UNMAPPED,
  [KEY_KPENTER] = KBD_KEY_KPACTION(Enter),
  [KEY_RIGHTCTRL] = KBD_KEY_MODIFIER(ControlRight),
  [KEY_KPSLASH] = KBD_KEY_KPSYMBOL(Divide),
  [KEY_SYSRQ] = KBD_KEY_ACTION(SystemRequest),
  [KEY_RIGHTALT] = KBD_KEY_MODIFIER(AltRight),
  [KEY_LINEFEED] = KBD_KEY_UNMAPPED,
  [KEY_HOME] = KBD_KEY_ACTION(Home),
  [KEY_UP] = KBD_KEY_ACTION(ArrowUp),
  [KEY_PAGEUP] = KBD_KEY_ACTION(PageUp),
  [KEY_LEFT] = KBD_KEY_ACTION(ArrowLeft),
  [KEY_RIGHT] = KBD_KEY_ACTION(ArrowRight),
  [KEY_END] = KBD_KEY_ACTION(End),
  [KEY_DOWN] = KBD_KEY_ACTION(ArrowDown),
  [KEY_PAGEDOWN] = KBD_KEY_ACTION(PageDown),
  [KEY_INSERT] = KBD_KEY_ACTION(Insert),
  [KEY_DELETE] = KBD_KEY_ACTION(DeleteForward),
  [KEY_MACRO] = KBD_KEY_UNMAPPED,
  [KEY_MUTE] = KBD_KEY_MEDIA(Mute),
  [KEY_VOLUMEDOWN] = KBD_KEY_MEDIA(VolumeDown),
  [KEY_VOLUMEUP] = KBD_KEY_MEDIA(VolumeUp),
  [KEY_POWER] = KBD_KEY_ACTION(Power),
  [KEY_KPEQUAL] = KBD_KEY_KPSYMBOL(Equals),
  [KEY_KPPLUSMINUS] = KBD_KEY_KPSYMBOL(PlusMinus),
  [KEY_LEFTMETA] = KBD_KEY_ACTION(GuiLeft),
  [KEY_RIGHTMETA] = KBD_KEY_ACTION(GuiRight),
  [KEY_COMPOSE] = KBD_KEY_ACTION(Context),
  [KEY_PAUSE] = KBD_KEY_ACTION(Pause),
  [KEY_KPCOMMA] = KBD_KEY_KPSYMBOL(Comma),
  [KEY_HANGEUL] = KBD_KEY_UNMAPPED,
  [KEY_HANGUEL] = KBD_KEY_UNMAPPED,
  [KEY_HANJA] = KBD_KEY_UNMAPPED,
  [KEY_YEN] = KBD_KEY_UNMAPPED,
  [KEY_LEFTMETA] = KBD_KEY_UNMAPPED,
  [KEY_RIGHTMETA] = KBD_KEY_UNMAPPED,
  [KEY_COMPOSE] = KBD_KEY_UNMAPPED,
  [KEY_STOP] = KBD_KEY_ACTION(Stop),
  [KEY_AGAIN] = KBD_KEY_ACTION(Again),
  [KEY_PROPS] = KBD_KEY_ACTION(Props),
  [KEY_UNDO] = KBD_KEY_ACTION(Undo),
  [KEY_FRONT] = KBD_KEY_ACTION(Front),
  [KEY_COPY] = KBD_KEY_ACTION(Copy),
  [KEY_OPEN] = KBD_KEY_ACTION(Open),
  [KEY_PASTE] = KBD_KEY_ACTION(Paste),
  [KEY_FIND] = KBD_KEY_ACTION(Find),
  [KEY_CUT] = KBD_KEY_ACTION(Cut),
  [KEY_HELP] = KBD_KEY_ACTION(Help),
  [KEY_MENU] = KBD_KEY_ACTION(Menu),
  [KEY_CALC] = KBD_KEY_UNMAPPED,
  [KEY_SETUP] = KBD_KEY_UNMAPPED,
  [KEY_SLEEP] = KBD_KEY_UNMAPPED,
  [KEY_WAKEUP] = KBD_KEY_UNMAPPED,
  [KEY_FILE] = KBD_KEY_UNMAPPED,
  [KEY_SENDFILE] = KBD_KEY_UNMAPPED,
  [KEY_DELETEFILE] = KBD_KEY_UNMAPPED,
  [KEY_XFER] = KBD_KEY_UNMAPPED,
  [KEY_PROG1] = KBD_KEY_UNMAPPED,
  [KEY_PROG2] = KBD_KEY_UNMAPPED,
  [KEY_WWW] = KBD_KEY_UNMAPPED,
  [KEY_MSDOS] = KBD_KEY_UNMAPPED,
  [KEY_COFFEE] = KBD_KEY_UNMAPPED,
  [KEY_SCREENLOCK] = KBD_KEY_UNMAPPED,
  [KEY_DIRECTION] = KBD_KEY_UNMAPPED,
  [KEY_CYCLEWINDOWS] = KBD_KEY_UNMAPPED,
  [KEY_MAIL] = KBD_KEY_UNMAPPED,
  [KEY_BOOKMARKS] = KBD_KEY_UNMAPPED,
  [KEY_COMPUTER] = KBD_KEY_UNMAPPED,
  [KEY_BACK] = KBD_KEY_UNMAPPED,
  [KEY_FORWARD] = KBD_KEY_UNMAPPED,
  [KEY_CLOSECD] = KBD_KEY_MEDIA(Close),
  [KEY_EJECTCD] = KBD_KEY_MEDIA(Eject),
  [KEY_EJECTCLOSECD] = KBD_KEY_MEDIA(EjectClose),
  [KEY_NEXTSONG] = KBD_KEY_MEDIA(Next),
  [KEY_PLAYPAUSE] = KBD_KEY_MEDIA(PlayPause),
  [KEY_PREVIOUSSONG] = KBD_KEY_MEDIA(Previous),
  [KEY_STOPCD] = KBD_KEY_MEDIA(Stop),
  [KEY_RECORD] = KBD_KEY_MEDIA(Record),
  [KEY_REWIND] = KBD_KEY_MEDIA(Backward),
  [KEY_PHONE] = KBD_KEY_UNMAPPED,
  [KEY_ISO] = KBD_KEY_UNMAPPED,
  [KEY_CONFIG] = KBD_KEY_UNMAPPED,
  [KEY_HOMEPAGE] = KBD_KEY_UNMAPPED,
  [KEY_REFRESH] = KBD_KEY_UNMAPPED,
  [KEY_EXIT] = KBD_KEY_UNMAPPED,
  [KEY_MOVE] = KBD_KEY_UNMAPPED,
  [KEY_EDIT] = KBD_KEY_UNMAPPED,
  [KEY_SCROLLUP] = KBD_KEY_UNMAPPED,
  [KEY_SCROLLDOWN] = KBD_KEY_UNMAPPED,
  [KEY_KPLEFTPAREN] = KBD_KEY_KPSYMBOL(LeftParenthesis),
  [KEY_KPRIGHTPAREN] = KBD_KEY_KPSYMBOL(RightParenthesis),
  [KEY_NEW] = KBD_KEY_UNMAPPED,
  [KEY_REDO] = KBD_KEY_UNMAPPED,
  [KEY_F13] = KBD_KEY_FUNCTION(F13),
  [KEY_F14] = KBD_KEY_FUNCTION(F14),
  [KEY_F15] = KBD_KEY_FUNCTION(F15),
  [KEY_F16] = KBD_KEY_FUNCTION(F16),
  [KEY_F17] = KBD_KEY_FUNCTION(F17),
  [KEY_F18] = KBD_KEY_FUNCTION(F18),
  [KEY_F19] = KBD_KEY_FUNCTION(F19),
  [KEY_F20] = KBD_KEY_FUNCTION(F20),
  [KEY_F21] = KBD_KEY_FUNCTION(F21),
  [KEY_F22] = KBD_KEY_FUNCTION(F22),
  [KEY_F23] = KBD_KEY_FUNCTION(F23),
  [KEY_F24] = KBD_KEY_FUNCTION(F24),
  [KEY_PLAYCD] = KBD_KEY_MEDIA(Play),
  [KEY_PAUSECD] = KBD_KEY_MEDIA(Pause),
  [KEY_PROG3] = KBD_KEY_UNMAPPED,
  [KEY_PROG4] = KBD_KEY_UNMAPPED,
  [KEY_DASHBOARD] = KBD_KEY_UNMAPPED,
  [KEY_SUSPEND] = KBD_KEY_UNMAPPED,
  [KEY_CLOSE] = KBD_KEY_UNMAPPED,
  [KEY_PLAY] = KBD_KEY_UNMAPPED,
  [KEY_FASTFORWARD] = KBD_KEY_MEDIA(Forward),
  [KEY_BASSBOOST] = KBD_KEY_UNMAPPED,
  [KEY_PRINT] = KBD_KEY_UNMAPPED,
  [KEY_HP] = KBD_KEY_UNMAPPED,
  [KEY_CAMERA] = KBD_KEY_UNMAPPED,
  [KEY_SOUND] = KBD_KEY_UNMAPPED,
  [KEY_QUESTION] = KBD_KEY_UNMAPPED,
  [KEY_EMAIL] = KBD_KEY_UNMAPPED,
  [KEY_CHAT] = KBD_KEY_UNMAPPED,
  [KEY_SEARCH] = KBD_KEY_UNMAPPED,
  [KEY_CONNECT] = KBD_KEY_UNMAPPED,
  [KEY_FINANCE] = KBD_KEY_UNMAPPED,
  [KEY_SPORT] = KBD_KEY_UNMAPPED,
  [KEY_SHOP] = KBD_KEY_UNMAPPED,
  [KEY_ALTERASE] = KBD_KEY_UNMAPPED,
  [KEY_CANCEL] = KBD_KEY_UNMAPPED,
  [KEY_BRIGHTNESSDOWN] = KBD_KEY_UNMAPPED,
  [KEY_BRIGHTNESSUP] = KBD_KEY_UNMAPPED,
  [KEY_MEDIA] = KBD_KEY_UNMAPPED,
  [KEY_SWITCHVIDEOMODE] = KBD_KEY_UNMAPPED,
  [KEY_KBDILLUMTOGGLE] = KBD_KEY_UNMAPPED,
  [KEY_KBDILLUMDOWN] = KBD_KEY_UNMAPPED,
  [KEY_KBDILLUMUP] = KBD_KEY_UNMAPPED,
  [KEY_SEND] = KBD_KEY_UNMAPPED,
  [KEY_REPLY] = KBD_KEY_UNMAPPED,
  [KEY_FORWARDMAIL] = KBD_KEY_UNMAPPED,
  [KEY_SAVE] = KBD_KEY_UNMAPPED,
  [KEY_DOCUMENTS] = KBD_KEY_UNMAPPED,
  [KEY_BATTERY] = KBD_KEY_UNMAPPED,
  [KEY_BLUETOOTH] = KBD_KEY_UNMAPPED,
  [KEY_WLAN] = KBD_KEY_UNMAPPED,
  [KEY_UWB] = KBD_KEY_UNMAPPED,
  [KEY_UNKNOWN] = KBD_KEY_UNMAPPED,
  [KEY_VIDEO_NEXT] = KBD_KEY_UNMAPPED,
  [KEY_VIDEO_PREV] = KBD_KEY_UNMAPPED,
  [KEY_BRIGHTNESS_CYCLE] = KBD_KEY_UNMAPPED,
  [KEY_BRIGHTNESS_ZERO] = KBD_KEY_UNMAPPED,
  [KEY_DISPLAY_OFF] = KBD_KEY_UNMAPPED,
  [KEY_WIMAX] = KBD_KEY_UNMAPPED,
  [KEY_OK] = KBD_KEY_UNMAPPED,
  [KEY_SELECT] = KBD_KEY_ACTION(Select),
  [KEY_GOTO] = KBD_KEY_UNMAPPED,
  [KEY_CLEAR] = KBD_KEY_ACTION(Clear),
  [KEY_POWER2] = KBD_KEY_UNMAPPED,
  [KEY_OPTION] = KBD_KEY_UNMAPPED,
  [KEY_INFO] = KBD_KEY_UNMAPPED,
  [KEY_TIME] = KBD_KEY_UNMAPPED,
  [KEY_VENDOR] = KBD_KEY_UNMAPPED,
  [KEY_ARCHIVE] = KBD_KEY_UNMAPPED,
  [KEY_PROGRAM] = KBD_KEY_UNMAPPED,
  [KEY_CHANNEL] = KBD_KEY_UNMAPPED,
  [KEY_FAVORITES] = KBD_KEY_UNMAPPED,
  [KEY_EPG] = KBD_KEY_UNMAPPED,
  [KEY_PVR] = KBD_KEY_UNMAPPED,
  [KEY_MHP] = KBD_KEY_UNMAPPED,
  [KEY_LANGUAGE] = KBD_KEY_UNMAPPED,
  [KEY_TITLE] = KBD_KEY_UNMAPPED,
  [KEY_SUBTITLE] = KBD_KEY_UNMAPPED,
  [KEY_ANGLE] = KBD_KEY_UNMAPPED,
  [KEY_ZOOM] = KBD_KEY_UNMAPPED,
  [KEY_MODE] = KBD_KEY_UNMAPPED,
  [KEY_KEYBOARD] = KBD_KEY_UNMAPPED,
  [KEY_SCREEN] = KBD_KEY_UNMAPPED,
  [KEY_PC] = KBD_KEY_UNMAPPED,
  [KEY_TV] = KBD_KEY_UNMAPPED,
  [KEY_TV2] = KBD_KEY_UNMAPPED,
  [KEY_VCR] = KBD_KEY_UNMAPPED,
  [KEY_VCR2] = KBD_KEY_UNMAPPED,
  [KEY_SAT] = KBD_KEY_UNMAPPED,
  [KEY_SAT2] = KBD_KEY_UNMAPPED,
  [KEY_CD] = KBD_KEY_UNMAPPED,
  [KEY_TAPE] = KBD_KEY_UNMAPPED,
  [KEY_RADIO] = KBD_KEY_UNMAPPED,
  [KEY_TUNER] = KBD_KEY_UNMAPPED,
  [KEY_PLAYER] = KBD_KEY_UNMAPPED,
  [KEY_TEXT] = KBD_KEY_UNMAPPED,
  [KEY_DVD] = KBD_KEY_UNMAPPED,
  [KEY_AUX] = KBD_KEY_UNMAPPED,
  [KEY_MP3] = KBD_KEY_UNMAPPED,
  [KEY_AUDIO] = KBD_KEY_UNMAPPED,
  [KEY_VIDEO] = KBD_KEY_UNMAPPED,
  [KEY_DIRECTORY] = KBD_KEY_UNMAPPED,
  [KEY_LIST] = KBD_KEY_UNMAPPED,
  [KEY_MEMO] = KBD_KEY_UNMAPPED,
  [KEY_CALENDAR] = KBD_KEY_UNMAPPED,
  [KEY_RED] = KBD_KEY_UNMAPPED,
  [KEY_GREEN] = KBD_KEY_UNMAPPED,
  [KEY_YELLOW] = KBD_KEY_UNMAPPED,
  [KEY_BLUE] = KBD_KEY_UNMAPPED,
  [KEY_CHANNELUP] = KBD_KEY_UNMAPPED,
  [KEY_CHANNELDOWN] = KBD_KEY_UNMAPPED,
  [KEY_FIRST] = KBD_KEY_UNMAPPED,
  [KEY_LAST] = KBD_KEY_UNMAPPED,
  [KEY_AB] = KBD_KEY_UNMAPPED,
  [KEY_NEXT] = KBD_KEY_UNMAPPED,
  [KEY_RESTART] = KBD_KEY_UNMAPPED,
  [KEY_SLOW] = KBD_KEY_UNMAPPED,
  [KEY_SHUFFLE] = KBD_KEY_UNMAPPED,
  [KEY_BREAK] = KBD_KEY_UNMAPPED,
  [KEY_PREVIOUS] = KBD_KEY_UNMAPPED,
  [KEY_DIGITS] = KBD_KEY_UNMAPPED,
  [KEY_TEEN] = KBD_KEY_UNMAPPED,
  [KEY_TWEN] = KBD_KEY_UNMAPPED,
  [KEY_VIDEOPHONE] = KBD_KEY_UNMAPPED,
  [KEY_GAMES] = KBD_KEY_UNMAPPED,
  [KEY_ZOOMIN] = KBD_KEY_UNMAPPED,
  [KEY_ZOOMOUT] = KBD_KEY_UNMAPPED,
  [KEY_ZOOMRESET] = KBD_KEY_UNMAPPED,
  [KEY_WORDPROCESSOR] = KBD_KEY_UNMAPPED,
  [KEY_EDITOR] = KBD_KEY_UNMAPPED,
  [KEY_SPREADSHEET] = KBD_KEY_UNMAPPED,
  [KEY_GRAPHICSEDITOR] = KBD_KEY_UNMAPPED,
  [KEY_PRESENTATION] = KBD_KEY_UNMAPPED,
  [KEY_DATABASE] = KBD_KEY_UNMAPPED,
  [KEY_NEWS] = KBD_KEY_UNMAPPED,
  [KEY_VOICEMAIL] = KBD_KEY_UNMAPPED,
  [KEY_ADDRESSBOOK] = KBD_KEY_UNMAPPED,
  [KEY_MESSENGER] = KBD_KEY_UNMAPPED,
  [KEY_DISPLAYTOGGLE] = KBD_KEY_UNMAPPED,
  [KEY_SPELLCHECK] = KBD_KEY_UNMAPPED,
  [KEY_LOGOFF] = KBD_KEY_UNMAPPED,
  [KEY_DOLLAR] = KBD_KEY_UNMAPPED,
  [KEY_EURO] = KBD_KEY_UNMAPPED,
  [KEY_FRAMEBACK] = KBD_KEY_UNMAPPED,
  [KEY_FRAMEFORWARD] = KBD_KEY_UNMAPPED,
  [KEY_CONTEXT_MENU] = KBD_KEY_UNMAPPED,
  [KEY_MEDIA_REPEAT] = KBD_KEY_UNMAPPED,
  [KEY_DEL_EOL] = KBD_KEY_UNMAPPED,
  [KEY_DEL_EOS] = KBD_KEY_UNMAPPED,
  [KEY_INS_LINE] = KBD_KEY_UNMAPPED,
  [KEY_DEL_LINE] = KBD_KEY_UNMAPPED,
  [KEY_FN] = KBD_KEY_UNMAPPED,
  [KEY_FN_ESC] = KBD_KEY_UNMAPPED,
  [KEY_FN_F1] = KBD_KEY_UNMAPPED,
  [KEY_FN_F2] = KBD_KEY_UNMAPPED,
  [KEY_FN_F3] = KBD_KEY_UNMAPPED,
  [KEY_FN_F4] = KBD_KEY_UNMAPPED,
  [KEY_FN_F5] = KBD_KEY_UNMAPPED,
  [KEY_FN_F6] = KBD_KEY_UNMAPPED,
  [KEY_FN_F7] = KBD_KEY_UNMAPPED,
  [KEY_FN_F8] = KBD_KEY_UNMAPPED,
  [KEY_FN_F9] = KBD_KEY_UNMAPPED,
  [KEY_FN_F10] = KBD_KEY_UNMAPPED,
  [KEY_FN_F11] = KBD_KEY_UNMAPPED,
  [KEY_FN_F12] = KBD_KEY_UNMAPPED,
  [KEY_FN_1] = KBD_KEY_UNMAPPED,
  [KEY_FN_2] = KBD_KEY_UNMAPPED,
  [KEY_FN_D] = KBD_KEY_UNMAPPED,
  [KEY_FN_E] = KBD_KEY_UNMAPPED,
  [KEY_FN_F] = KBD_KEY_UNMAPPED,
  [KEY_FN_S] = KBD_KEY_UNMAPPED,
  [KEY_FN_B] = KBD_KEY_UNMAPPED,
  [KEY_BRL_DOT1] = KBD_KEY_BRAILLE(Dot1),
  [KEY_BRL_DOT2] = KBD_KEY_BRAILLE(Dot2),
  [KEY_BRL_DOT3] = KBD_KEY_BRAILLE(Dot3),
  [KEY_BRL_DOT4] = KBD_KEY_BRAILLE(Dot4),
  [KEY_BRL_DOT5] = KBD_KEY_BRAILLE(Dot5),
  [KEY_BRL_DOT6] = KBD_KEY_BRAILLE(Dot6),
  [KEY_BRL_DOT7] = KBD_KEY_BRAILLE(Dot7),
  [KEY_BRL_DOT8] = KBD_KEY_BRAILLE(Dot8),
  [KEY_BRL_DOT9] = KBD_KEY_BRAILLE(Backward),
  [KEY_BRL_DOT10] = KBD_KEY_BRAILLE(Forward),
END_KEY_CODE_MAP

int
newKeyboardMonitorExtension (KeyboardMonitorExtension **kmx) {
  if ((*kmx = malloc(sizeof(**kmx)))) {
    memset(*kmx,  0, sizeof(**kmx));

    (*kmx)->uevent.socket = -1;
    (*kmx)->uevent.monitor = NULL;

    return 1;
  } else {
    logMallocError();
  }

  return 0;
}

void
destroyKeyboardMonitorExtension (KeyboardMonitorExtension *kmx) {
  if (kmx->uevent.monitor) asyncCancelRequest(kmx->uevent.monitor);
  if (kmx->uevent.socket != -1) close(kmx->uevent.socket);
  free(kmx);
}

int
newKeyboardInstanceExtension (KeyboardInstanceExtension **kix) {
  if ((*kix = malloc(sizeof(**kix)))) {
    memset(*kix,  0, sizeof(**kix));

    (*kix)->uinput = NULL;
    (*kix)->udevDelay = NULL;

    (*kix)->file.descriptor = -1;
    (*kix)->file.monitor = NULL;

    (*kix)->device.path = NULL;
    (*kix)->device.major = 0;
    (*kix)->device.minor = 0;

    return 1;
  } else {
    logMallocError();
  }

  *kix = NULL;
  return 0;
}

void
destroyKeyboardInstanceExtension (KeyboardInstanceExtension *kix) {
  if (kix->file.monitor) {
    asyncCancelRequest(kix->file.monitor);
    logMessage(LOG_DEBUG, "closing keyboard: %s: fd=%d",
               kix->device.path, kix->file.descriptor);
  }

  if (kix->file.descriptor != -1) close(kix->file.descriptor);
  if (kix->udevDelay) asyncCancelRequest(kix->udevDelay);
  if (kix->uinput) destroyUinputObject(kix->uinput);
  if (kix->device.path) free(kix->device.path);
  free(kix);
}

int
forwardKeyEvent (KeyboardInstanceObject *kio, int code, int press) {
  return writeKeyEvent(kio->kix->uinput, code, (press? 1: 0));
}

ASYNC_INPUT_CALLBACK(handleLinuxKeyboardEvent) {
  KeyboardInstanceObject *kio = parameters->data;
  static const char label[] = "keyboard";

  if (parameters->error) {
    logMessage(LOG_DEBUG, "%s read error: fd=%d: %s",
               label, kio->kix->file.descriptor, strerror(parameters->error));
    destroyKeyboardInstanceObject(kio);
  } else if (parameters->end) {
    logMessage(LOG_DEBUG, "%s end-of-file: fd=%d", 
               label, kio->kix->file.descriptor);
    destroyKeyboardInstanceObject(kio);
  } else {
    const struct input_event *event = parameters->buffer;

    if (parameters->length >= sizeof(*event)) {
      switch (event->type) {
        case EV_KEY: {
          int release = event->value == 0;
          int press   = event->value == 1;

          if (release || press) handleKeyEvent(kio, event->code, press);
          break;
        }

        case EV_REP: {
          switch (event->code) {
            case REP_DELAY: {
              writeRepeatDelay(kio->kix->uinput, event->value);
              break;
            }

            case REP_PERIOD: {
              writeRepeatPeriod(kio->kix->uinput, event->value);
              break;
            }

            default:
              break;
          }

          break;
        }

        default:
          break;
      }

      return sizeof(*event);
    }
  }

  return 0;
}

static UinputObject *
newUinputInstance (const char *device) {
  char name[0X40];

  snprintf(name, sizeof(name), "Keyboard Instance - %s", locatePathName(device));
  return newUinputObject(name);
}

static int
prepareUinputInstance (UinputObject *uinput, int keyboard) {
  {
    int type = EV_KEY;
    BITMASK(mask, KEY_MAX+1, char);
    int size = ioctl(keyboard, EVIOCGBIT(type, sizeof(mask)), mask);

    if (size == -1) {
      logSystemError("ioctl[EVIOCGBIT]");
      return 0;
    }

    {
      int count = size * 8;

      {
        int key = KEY_ENTER;

        if (key >= count) return 0;
        if (!BITMASK_TEST(mask, key)) return 0;
      }

      if (!enableUinputEventType(uinput, type)) return 0;

      for (int key=0; key<count; key+=1) {
        if (BITMASK_TEST(mask, key)) {
          if (!enableUinputKey(uinput, key)) {
            return 0;
          }
        }
      }
    }
  }

  if (!enableUinputEventType(uinput, EV_REP)) return 0;
  if (!createUinputDevice(uinput)) return 0;

  {
    int properties[2];

    if (ioctl(keyboard, EVIOCGREP, properties) != -1) {
      if (!writeRepeatDelay(uinput, properties[0])) return 0;
      if (!writeRepeatPeriod(uinput, properties[1])) return 0;
    }
  }

  {
    BITMASK(mask, KEY_MAX+1, char);
    int size = ioctl(keyboard, EVIOCGKEY(sizeof(mask)), mask);

    if (size != -1) {
      int count = size * 8;
      for (int key=0; key<count; key+=1) {
        if (BITMASK_TEST(mask, key)) {
          logMessage(LOG_WARNING, "key already pressed: %d", key);
        }
      }
    }
  }

  return 1;
}

static int
monitorKeyboard (KeyboardInstanceObject *kio) {
  const char *deviceName = locatePathName(kio->kix->device.path);

  if ((kio->kix->file.descriptor = open(kio->kix->device.path, O_RDONLY)) != -1) {
    struct stat status;

    if (fstat(kio->kix->file.descriptor, &status) != -1) {
      if (S_ISCHR(status.st_mode)) {
        {
          char description[0X100];

          STR_BEGIN(description, sizeof(description));
          STR_PRINTF("%s:", deviceName);

          {
            struct input_id identity;

            if (ioctl(kio->kix->file.descriptor, EVIOCGID, &identity) != -1) {
              STR_PRINTF(" bus=%04X vnd=%04X prd=%04X ver=%04X",
                         identity.bustype, identity.vendor, identity.product, identity.version);

              {
                static const KeyboardType typeTable[] = {
  #ifdef BUS_I8042
                  [BUS_I8042] = KBD_TYPE_PS2,
  #endif /* BUS_I8042 */

  #ifdef BUS_USB
                  [BUS_USB] = KBD_TYPE_USB,
  #endif /* BUS_USB */

  #ifdef BUS_BLUETOOTH
                  [BUS_BLUETOOTH] = KBD_TYPE_Bluetooth,
  #endif /* BUS_BLUETOOTH */
                };

                if (identity.bustype < ARRAY_COUNT(typeTable)) {
                  kio->actualProperties.type = typeTable[identity.bustype];
                }
              }

              kio->actualProperties.vendor = identity.vendor;
              kio->actualProperties.product = identity.product;
            } else if (errno != ENOTTY) {
              logMessage(LOG_WARNING, "cannot get input device identity: %s: %s",
                         deviceName, strerror(errno));
            }
          }

          {
            char topology[0X100];

            if (ioctl(kio->kix->file.descriptor, EVIOCGPHYS(sizeof(topology)), topology) != -1) {
              if (*topology) {
                STR_PRINTF(" tpl=%s", topology);
              }
            }
          }

          {
            char identifier[0X100];

            if (ioctl(kio->kix->file.descriptor, EVIOCGUNIQ(sizeof(identifier)), identifier) != -1) {
              if (*identifier) {
                STR_PRINTF(" id=%s", identifier);
              }
            }
          }

          {
            char name[0X100];

            if (ioctl(kio->kix->file.descriptor, EVIOCGNAME(sizeof(name)), name) != -1) {
              if (*name) {
                STR_PRINTF(" nam=%s", name);
              }
            }
          }

          STR_END;
          logMessage(LOG_DEBUG, "checking input device: %s", description);
        }
        
        if (kio->actualProperties.type) {
          if (checkKeyboardProperties(&kio->actualProperties, &kio->kmo->requiredProperties)) {
            if (ioctl(kio->kix->file.descriptor, EVIOCGRAB, 1) != -1) {
              if ((kio->kix->uinput = newUinputInstance(kio->kix->device.path))) {
                if (prepareUinputInstance(kio->kix->uinput, kio->kix->file.descriptor)) {
                  if (asyncReadFile(&kio->kix->file.monitor,
                                    kio->kix->file.descriptor, sizeof(struct input_event),
                                    handleLinuxKeyboardEvent, kio)) {
                    logMessage(LOG_DEBUG, "keyboard opened: %s: fd=%d",
                               kio->kix->device.path, kio->kix->file.descriptor);

                    return 1;
                  }
                }
              }
            } else {
              logSystemError("ioctl[EVIOCGRAB]");
            }
          }
        }
      }
    } else {
      logMessage(LOG_WARNING, "cannot stat input device: %s: %s",
                 deviceName, strerror(errno));
    }
  } else {
    logMessage(LOG_WARNING, "cannot open input device: %s: %s",
               deviceName, strerror(errno));
  }

  return 0;
}

static void
monitorCurrentKeyboards (KeyboardMonitorObject *kmo) {
  const char *root = "/dev/input";
  const size_t rootLength = strlen(root);
  DIR *directory;

  logMessage(LOG_DEBUG, "searching for keyboards");

  if ((directory = opendir(root))) {
    struct dirent *entry;

    while ((entry = readdir(directory))) {
      KeyboardInstanceObject *kio;

      if ((kio = newKeyboardInstanceObject(kmo))) {
        const size_t pathSize = rootLength + 1 + strlen(entry->d_name) + 1;

        if ((kio->kix->device.path = malloc(pathSize))) {
          snprintf(kio->kix->device.path, pathSize, "%s/%s", root, entry->d_name);
          if (monitorKeyboard(kio)) continue;
        } else {
          logMallocError();
        }

        destroyKeyboardInstanceObject(kio);
      }
    }

    closedir(directory);
  } else {
    logMessage(LOG_DEBUG, "cannot open directory: %s: %s", root, strerror(errno));
  }

  logMessage(LOG_DEBUG, "keyboard search complete");
}

#ifdef NETLINK_KOBJECT_UEVENT
ASYNC_ALARM_CALLBACK(openLinuxInputDevice) {
  KeyboardInstanceObject *kio = parameters->data;

  asyncDiscardHandle(kio->kix->udevDelay);
  kio->kix->udevDelay = NULL;

  if (!monitorKeyboard(kio)) destroyKeyboardInstanceObject(kio);
}

static int
getDeviceNumbers (const char *device, int *major, int *minor) {
  int ok = 0;
  static const char prefix[] = "/sys";
  static const char suffix[] = "/dev";
  char path[strlen(prefix) + strlen(device) + sizeof(suffix)];
  int descriptor;

  snprintf(path, sizeof(path), "%s%s%s", prefix, device, suffix);

  if ((descriptor = open(path, O_RDONLY)) != -1) {
    char buffer[0X10];
    ssize_t length;

    if ((length = read(descriptor, buffer, sizeof(buffer))) > 0) {
      if (sscanf(buffer, "%d:%d", major, minor) == 2) {
        ok = 1;
      }
    }

    close(descriptor);
  } else {
    logMessage(LOG_DEBUG, "cannot open sysfs dev file: %s: %s",
               path, strerror(errno));
  }

  return ok;
}

ASYNC_INPUT_CALLBACK(handleKobjectUeventString) {
  KeyboardMonitorObject *kmo = parameters->data;
  static const char label[] = "kobject uevent";

  if (parameters->error) {
    logMessage(LOG_DEBUG, "%s read error: %s", label, strerror(parameters->error));
  } else if (parameters->end) {
    logMessage(LOG_DEBUG, "%s end-of-file", label);
  } else {
    const char *string = parameters->buffer;
    const char *end = memchr(string, 0, parameters->length);

    if (end) {
      size_t length = end - string;

      static const char delimiters[] = {'@', '=', '\0'};
      const char *delimiter = strpbrk(string, delimiters);

      if (!delimiter) {
        const char *data = end + 1;
        size_t size;

        if (strcmp(string, "libudev") == 0) {
          size = 32;
        } else {
          logMessage(LOG_WARNING, "unrecognized %s segment: %s", label, string);
          size = 0;
        }

        length += size;
        if (parameters->length < length) return 0;
        logBytes(LOG_DEBUG, "%s data: %s", data, size, label, string);
      } else if (*delimiter == '@') {
        const char *action = string;
        const char *device = delimiter + 1;
        int actionLength = delimiter - action;

        logMessage(LOG_DEBUG, "%s action: %.*s %s", label, actionLength, action, device);

        if (strncmp(action, "add", actionLength) == 0) {
          const char *suffix = device;

          while ((suffix = strstr(suffix, "/input"))) {
            int input;
            int event;

            if (sscanf(++suffix, "input%d/event%d", &input, &event) == 2) {
              KeyboardInstanceObject *kio;

              if ((kio = newKeyboardInstanceObject(kmo))) {
                if (getDeviceNumbers(device, &kio->kix->device.major, &kio->kix->device.minor)) {
                  char path[0X40];

                  snprintf(path, sizeof(path), "/dev/input/event%d", event);

                  if ((kio->kix->device.path = strdup(path))) {
                    if (asyncNewRelativeAlarm(&kio->kix->udevDelay,
                                              LINUX_INPUT_DEVICE_OPEN_DELAY,
                                              openLinuxInputDevice, kio)) {
                      break;
                    }
                  } else {
                    logMallocError();
                  }
                }

                destroyKeyboardInstanceObject(kio);
              }
            }
          }
        }
      } else if (*delimiter == '=') {
        const char *name = string;
        const char *value = delimiter + 1;
        int nameLength = delimiter - name;

        logMessage(LOG_DEBUG, "%s property: %.*s %s", label, nameLength, name, value);
      }

      return length + 1;
    }
  }

  return 0;
}

static int
getKobjectUeventSocket (void) {
  static int socketDescriptor = -1;

  if (socketDescriptor == -1) {
    const struct sockaddr_nl socketAddress = {
      .nl_family = AF_NETLINK,
      .nl_pid = getpid(),
      .nl_groups = 0XFFFFFFFF
    };

    if ((socketDescriptor = socket(PF_NETLINK, SOCK_DGRAM, NETLINK_KOBJECT_UEVENT)) != -1) {
      if (bind(socketDescriptor, (const struct sockaddr *)&socketAddress, sizeof(socketAddress)) == -1) {
        logSystemError("bind");
        close(socketDescriptor);
        socketDescriptor = -1;
      }
    } else {
      logSystemError("socket");
    }
  }

  return socketDescriptor;
}
#endif /* NETLINK_KOBJECT_UEVENT */

static int
monitorNewKeyboards (KeyboardMonitorObject *kmo) {
#ifdef NETLINK_KOBJECT_UEVENT
  if ((kmo->kmx->uevent.socket = getKobjectUeventSocket()) != -1) {
    if (asyncReadSocket(&kmo->kmx->uevent.monitor,
                        kmo->kmx->uevent.socket, 6+1+PATH_MAX+1,
                        handleKobjectUeventString, kmo)) {
      return 1;
    }

    close(kmo->kmx->uevent.socket);
    kmo->kmx->uevent.socket = -1;
  }
#endif /* NETLINK_KOBJECT_UEVENT */

  return 0;
}
#endif /* HAVE_LINUX_UINPUT_H */

int
monitorKeyboards (KeyboardMonitorObject *kmo) {
#ifdef HAVE_LINUX_UINPUT_H
  monitorCurrentKeyboards(kmo);
  monitorNewKeyboards(kmo);
#endif /* HAVE_LINUX_UINPUT_H */

  return 1;
}
