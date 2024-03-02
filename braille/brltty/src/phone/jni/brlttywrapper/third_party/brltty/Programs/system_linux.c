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
#include <string.h>
#include <errno.h>
#include <fcntl.h>
#include <sys/ioctl.h>
#include <sys/stat.h>
#include <sys/statvfs.h>
#include <dirent.h>
#include <sys/sysmacros.h>
#include <linux/major.h>

#include "log.h"
#include "parse.h"
#include "file.h"
#include "device.h"
#include "async_handle.h"
#include "async_wait.h"
#include "async_io.h"
#include "hostcmd.h"
#include "bitmask.h"
#include "system.h"
#include "system_linux.h"

typedef struct {
  PathProcessor *processor;
  const PathProcessorParameters *parameters;
} PathProcessingData;

static int
processDirectoryEntry (const char *name, const PathProcessingData *ppd) {
  const PathProcessorParameters *parameters = ppd->parameters;

  const char *directory = parameters->path;
  char path[strlen(directory) + 1 + strlen(name) + 1];

  snprintf(
    path, sizeof(path), "%s%c%s",
    directory, PATH_SEPARATOR_CHARACTER, name
  );

  return processPathTree(path, ppd->processor, parameters->data);
}

int
processPathTree (const char *path, PathProcessor *processPath, void *data) {
  PathProcessorParameters parameters = {
    .path = path,
    .data = data
  };

  PathProcessingData ppd = {
    .processor = processPath,
    .parameters = &parameters
  };

  int stop = 0;
  DIR *directory;

  if ((directory = opendir(path))) {
    if (processPath(&parameters)) {
      struct dirent *entry;

      while ((entry = readdir(directory))) {
        const char *name = entry->d_name;

        if (strcmp(name, CURRENT_DIRECTORY_NAME) == 0) continue;
        if (strcmp(name, PARENT_DIRECTORY_NAME) == 0) continue;

        if (!processDirectoryEntry(name, &ppd)) {
          stop = 1;
          break;
        }
      }
    } else {
      stop = 1;
    }

    closedir(directory);
  } else if (errno == ENOTDIR) {
    if (!processPath(&parameters)) stop = 1;
  } else {
    logMessage(LOG_WARNING, "can't access path: %s: %s", path, strerror(errno));
  }

  return !stop;
}

int
compareGroups (gid_t group1, gid_t group2) {
  if (group1 < group2) return -1;
  if (group1 > group2) return 1;
  return 0;
}

static int
groupSorter (const void *element1,const void *element2) {
  const gid_t *group1 = element1;
  const gid_t *group2 = element2;
  return compareGroups(*group1, *group2);
}

void
sortGroups (gid_t *groups, size_t count) {
  qsort(groups, count, sizeof(*groups), groupSorter);
}

void
removeDuplicateGroups (gid_t *groups, size_t *count) {
  if (*count > 1) {
    sortGroups(groups, *count);

    gid_t *to = groups;
    const gid_t *from = to + 1;
    const gid_t *end = to + *count;

    while (from < end) {
      if (*from != *to) {
        if (++to != from) {
          *to = *from;
        }
      }

      from += 1;
    }

    *count = ++to - groups;
  }
}

void
processSupplementaryGroups (GroupsProcessor *processGroups, void *data) {
  ssize_t size = getgroups(0, NULL);

  if (size != -1) {
    gid_t groups[size];
    ssize_t result = getgroups(size, groups);

    if (result != -1) {
      size_t count = result;
      removeDuplicateGroups(groups, &count);
      processGroups(groups, count, data);
    } else {
      logSystemError("getgroups");
    }
  } else {
    logSystemError("getgroups");
  }
}

typedef struct {
  const gid_t *groups;
  size_t count;
  unsigned char have:1;
} HaveGroupsData;

static void
haveGroups (const gid_t *groups, size_t count, void *data) {
  HaveGroupsData *hgd = data;

  const gid_t *need = hgd->groups;
  const gid_t *needEnd = need + hgd->count;

  const gid_t *have = groups;
  const gid_t *haveEnd = have + count;

  while (have < haveEnd) {
    if (*have > *need) break;
    if (*have++ < *need) continue;

    if (++need == needEnd) {
      hgd->have = 1;
      return;
    }
  }

  hgd->have = 0;
}

int
haveSupplementaryGroups (const gid_t *groups, size_t count) {
  HaveGroupsData hgd = {
    .groups = groups,
    .count = count
  };

  processSupplementaryGroups(haveGroups, &hgd);
  return hgd.have;
}

#ifdef HAVE_LINUX_INPUT_H
#include <linux/input.h>

#ifndef input_event_sec
#define input_event_sec time.tv_sec
#endif /* input_event_sec */

#ifndef input_event_usec
#define input_event_usec time.tv_usec
#endif /* input_event_usec */

#include "kbd_keycodes.h"

LINUX_KEY_MAP(xt00) = {
  [XT_KEY_00_Escape] = KEY_ESC,
  [XT_KEY_00_F1] = KEY_F1,
  [XT_KEY_00_F2] = KEY_F2,
  [XT_KEY_00_F3] = KEY_F3,
  [XT_KEY_00_F4] = KEY_F4,
  [XT_KEY_00_F5] = KEY_F5,
  [XT_KEY_00_F6] = KEY_F6,
  [XT_KEY_00_F7] = KEY_F7,
  [XT_KEY_00_F8] = KEY_F8,
  [XT_KEY_00_F9] = KEY_F9,
  [XT_KEY_00_F10] = KEY_F10,
  [XT_KEY_00_F11] = KEY_F11,
  [XT_KEY_00_F12] = KEY_F12,
  [XT_KEY_00_SystemRequest] = KEY_SYSRQ,
  [XT_KEY_00_ScrollLock] = KEY_SCROLLLOCK,

  [XT_KEY_00_F13] = KEY_F13,
  [XT_KEY_00_F14] = KEY_F14,
  [XT_KEY_00_F15] = KEY_F15,
  [XT_KEY_00_F16] = KEY_F16,
  [XT_KEY_00_F17] = KEY_F17,
  [XT_KEY_00_F18] = KEY_F18,
  [XT_KEY_00_F19] = KEY_F19,
  [XT_KEY_00_F20] = KEY_F20,
  [XT_KEY_00_F21] = KEY_F21,
  [XT_KEY_00_F22] = KEY_F22,
  [XT_KEY_00_F23] = KEY_F23,
  [XT_KEY_00_F24] = KEY_F24,

  [XT_KEY_00_Grave] = KEY_GRAVE,
  [XT_KEY_00_1] = KEY_1,
  [XT_KEY_00_2] = KEY_2,
  [XT_KEY_00_3] = KEY_3,
  [XT_KEY_00_4] = KEY_4,
  [XT_KEY_00_5] = KEY_5,
  [XT_KEY_00_6] = KEY_6,
  [XT_KEY_00_7] = KEY_7,
  [XT_KEY_00_8] = KEY_8,
  [XT_KEY_00_9] = KEY_9,
  [XT_KEY_00_0] = KEY_0,
  [XT_KEY_00_Minus] = KEY_MINUS,
  [XT_KEY_00_Equal] = KEY_EQUAL,
  [XT_KEY_00_Backspace] = KEY_BACKSPACE,

  [XT_KEY_00_Tab] = KEY_TAB,
  [XT_KEY_00_Q] = KEY_Q,
  [XT_KEY_00_W] = KEY_W,
  [XT_KEY_00_E] = KEY_E,
  [XT_KEY_00_R] = KEY_R,
  [XT_KEY_00_T] = KEY_T,
  [XT_KEY_00_Y] = KEY_Y,
  [XT_KEY_00_U] = KEY_U,
  [XT_KEY_00_I] = KEY_I,
  [XT_KEY_00_O] = KEY_O,
  [XT_KEY_00_P] = KEY_P,
  [XT_KEY_00_LeftBracket] = KEY_LEFTBRACE,
  [XT_KEY_00_RightBracket] = KEY_RIGHTBRACE,
  [XT_KEY_00_Backslash] = KEY_BACKSLASH,

  [XT_KEY_00_CapsLock] = KEY_CAPSLOCK,
  [XT_KEY_00_A] = KEY_A,
  [XT_KEY_00_S] = KEY_S,
  [XT_KEY_00_D] = KEY_D,
  [XT_KEY_00_F] = KEY_F,
  [XT_KEY_00_G] = KEY_G,
  [XT_KEY_00_H] = KEY_H,
  [XT_KEY_00_J] = KEY_J,
  [XT_KEY_00_K] = KEY_K,
  [XT_KEY_00_L] = KEY_L,
  [XT_KEY_00_Semicolon] = KEY_SEMICOLON,
  [XT_KEY_00_Apostrophe] = KEY_APOSTROPHE,
  [XT_KEY_00_Enter] = KEY_ENTER,

  [XT_KEY_00_LeftShift] = KEY_LEFTSHIFT,
  [XT_KEY_00_Europe2] = KEY_102ND,
  [XT_KEY_00_Z] = KEY_Z,
  [XT_KEY_00_X] = KEY_X,
  [XT_KEY_00_C] = KEY_C,
  [XT_KEY_00_V] = KEY_V,
  [XT_KEY_00_B] = KEY_B,
  [XT_KEY_00_N] = KEY_N,
  [XT_KEY_00_M] = KEY_M,
  [XT_KEY_00_Comma] = KEY_COMMA,
  [XT_KEY_00_Period] = KEY_DOT,
  [XT_KEY_00_Slash] = KEY_SLASH,
  [XT_KEY_00_RightShift] = KEY_RIGHTSHIFT,

  [XT_KEY_00_LeftControl] = KEY_LEFTCTRL,
  [XT_KEY_00_LeftAlt] = KEY_LEFTALT,
  [XT_KEY_00_Space] = KEY_SPACE,

  [XT_KEY_00_NumLock] = KEY_NUMLOCK,
  [XT_KEY_00_KPAsterisk] = KEY_KPASTERISK,
  [XT_KEY_00_KPMinus] = KEY_KPMINUS,
  [XT_KEY_00_KPPlus] = KEY_KPPLUS,
  [XT_KEY_00_KPPeriod] = KEY_KPDOT,
  [XT_KEY_00_KP0] = KEY_KP0,
  [XT_KEY_00_KP1] = KEY_KP1,
  [XT_KEY_00_KP2] = KEY_KP2,
  [XT_KEY_00_KP3] = KEY_KP3,
  [XT_KEY_00_KP4] = KEY_KP4,
  [XT_KEY_00_KP5] = KEY_KP5,
  [XT_KEY_00_KP6] = KEY_KP6,
  [XT_KEY_00_KP7] = KEY_KP7,
  [XT_KEY_00_KP8] = KEY_KP8,
  [XT_KEY_00_KP9] = KEY_KP9,

  [XT_KEY_00_KPComma] = KEY_KPCOMMA,
  [XT_KEY_00_KPEqual] = KEY_KPEQUAL,

  [XT_KEY_00_International1] = KEY_RO,
  [XT_KEY_00_International2] = KEY_KATAKANAHIRAGANA,
  [XT_KEY_00_International3] = KEY_YEN,
  [XT_KEY_00_International4] = KEY_HENKAN,
  [XT_KEY_00_International5] = KEY_MUHENKAN,
  [XT_KEY_00_International6] = KEY_KPJPCOMMA,

  [XT_KEY_00_Language3] = KEY_KATAKANA,
  [XT_KEY_00_Language4] = KEY_HIRAGANA,
};

LINUX_KEY_MAP(xtE0) = {
  [XT_KEY_E0_LeftGUI] = KEY_LEFTMETA,
  [XT_KEY_E0_RightAlt] = KEY_RIGHTALT,
  [XT_KEY_E0_RightGUI] = KEY_RIGHTMETA,
  [XT_KEY_E0_Context] = KEY_COMPOSE,
  [XT_KEY_E0_RightControl] = KEY_RIGHTCTRL,

  [XT_KEY_E0_Insert] = KEY_INSERT,
  [XT_KEY_E0_Delete] = KEY_DELETE,
  [XT_KEY_E0_Home] = KEY_HOME,
  [XT_KEY_E0_End] = KEY_END,
  [XT_KEY_E0_PageUp] = KEY_PAGEUP,
  [XT_KEY_E0_PageDown] = KEY_PAGEDOWN,

  [XT_KEY_E0_ArrowUp] = KEY_UP,
  [XT_KEY_E0_ArrowLeft] = KEY_LEFT,
  [XT_KEY_E0_ArrowDown] = KEY_DOWN,
  [XT_KEY_E0_ArrowRight] = KEY_RIGHT,

  [XT_KEY_E0_KPEnter] = KEY_KPENTER,
  [XT_KEY_E0_KPSlash] = KEY_KPSLASH,

  [XT_KEY_E0_Copy] = KEY_COPY,
  [XT_KEY_E0_Cut] = KEY_CUT,
  [XT_KEY_E0_Paste] = KEY_PASTE,
  [XT_KEY_E0_Undo] = KEY_UNDO,
  [XT_KEY_E0_Redo] = KEY_REDO,

  [XT_KEY_E0_MyComputer] = KEY_COMPUTER,
  [XT_KEY_E0_Calculator] = KEY_CALC,
  [XT_KEY_E0_Mail] = KEY_MAIL,
  [XT_KEY_E0_Mail_X1] = KEY_MAIL,

  [XT_KEY_E0_WebHome] = KEY_HOMEPAGE,
  [XT_KEY_E0_WebBookmarks] = KEY_BOOKMARKS,
  [XT_KEY_E0_WebSearch] = KEY_SEARCH,
  [XT_KEY_E0_WebBack] = KEY_BACK,
  [XT_KEY_E0_WebForward] = KEY_FORWARD,
  [XT_KEY_E0_WebRefresh] = KEY_REFRESH,
  [XT_KEY_E0_WebStop] = KEY_STOP,

  [XT_KEY_E0_Mute] = KEY_MUTE,
  [XT_KEY_E0_VolumeDown] = KEY_VOLUMEDOWN,
  [XT_KEY_E0_VolumeUp] = KEY_VOLUMEUP,

  [XT_KEY_E0_MediaVideo] = KEY_MEDIA,
  [XT_KEY_E0_MediaPlayPause] = KEY_PLAYPAUSE,
  [XT_KEY_E0_MediaStop] = KEY_STOPCD,
  [XT_KEY_E0_MediaPrevious] = KEY_PREVIOUSSONG,
  [XT_KEY_E0_MediaNext] = KEY_NEXTSONG,

  [XT_KEY_E0_Power] = KEY_POWER,
  [XT_KEY_E0_Sleep] = KEY_SLEEP,
  [XT_KEY_E0_Wake] = KEY_WAKEUP,
};

LINUX_KEY_MAP(xtE1) = {
  [XT_KEY_E1_Pause] = KEY_PAUSE,
};

LINUX_KEY_MAP(at00) = {
  [AT_KEY_00_Escape] = KEY_ESC,
  [AT_KEY_00_F1] = KEY_F1,
  [AT_KEY_00_F2] = KEY_F2,
  [AT_KEY_00_F3] = KEY_F3,
  [AT_KEY_00_F4] = KEY_F4,
  [AT_KEY_00_F5] = KEY_F5,
  [AT_KEY_00_F6] = KEY_F6,
  [AT_KEY_00_F7] = KEY_F7,
  [AT_KEY_00_F7_X1] = KEY_F7,
  [AT_KEY_00_F8] = KEY_F8,
  [AT_KEY_00_F9] = KEY_F9,
  [AT_KEY_00_F10] = KEY_F10,
  [AT_KEY_00_F11] = KEY_F11,
  [AT_KEY_00_F12] = KEY_F12,
  [AT_KEY_00_SystemRequest] = KEY_SYSRQ,
  [AT_KEY_00_ScrollLock] = KEY_SCROLLLOCK,

  [AT_KEY_00_F13] = KEY_F13,
  [AT_KEY_00_F14] = KEY_F14,
  [AT_KEY_00_F15] = KEY_F15,
  [AT_KEY_00_F16] = KEY_F16,
  [AT_KEY_00_F17] = KEY_F17,
  [AT_KEY_00_F18] = KEY_F18,
  [AT_KEY_00_F19] = KEY_F19,
  [AT_KEY_00_F20] = KEY_F20,
  [AT_KEY_00_F21] = KEY_F21,
  [AT_KEY_00_F22] = KEY_F22,
  [AT_KEY_00_F23] = KEY_F23,
  [AT_KEY_00_F24] = KEY_F24,

  [AT_KEY_00_Grave] = KEY_GRAVE,
  [AT_KEY_00_1] = KEY_1,
  [AT_KEY_00_2] = KEY_2,
  [AT_KEY_00_3] = KEY_3,
  [AT_KEY_00_4] = KEY_4,
  [AT_KEY_00_5] = KEY_5,
  [AT_KEY_00_6] = KEY_6,
  [AT_KEY_00_7] = KEY_7,
  [AT_KEY_00_8] = KEY_8,
  [AT_KEY_00_9] = KEY_9,
  [AT_KEY_00_0] = KEY_0,
  [AT_KEY_00_Minus] = KEY_MINUS,
  [AT_KEY_00_Equal] = KEY_EQUAL,
  [AT_KEY_00_Backspace] = KEY_BACKSPACE,

  [AT_KEY_00_Tab] = KEY_TAB,
  [AT_KEY_00_Q] = KEY_Q,
  [AT_KEY_00_W] = KEY_W,
  [AT_KEY_00_E] = KEY_E,
  [AT_KEY_00_R] = KEY_R,
  [AT_KEY_00_T] = KEY_T,
  [AT_KEY_00_Y] = KEY_Y,
  [AT_KEY_00_U] = KEY_U,
  [AT_KEY_00_I] = KEY_I,
  [AT_KEY_00_O] = KEY_O,
  [AT_KEY_00_P] = KEY_P,
  [AT_KEY_00_LeftBracket] = KEY_LEFTBRACE,
  [AT_KEY_00_RightBracket] = KEY_RIGHTBRACE,
  [AT_KEY_00_Backslash] = KEY_BACKSLASH,

  [AT_KEY_00_CapsLock] = KEY_CAPSLOCK,
  [AT_KEY_00_A] = KEY_A,
  [AT_KEY_00_S] = KEY_S,
  [AT_KEY_00_D] = KEY_D,
  [AT_KEY_00_F] = KEY_F,
  [AT_KEY_00_G] = KEY_G,
  [AT_KEY_00_H] = KEY_H,
  [AT_KEY_00_J] = KEY_J,
  [AT_KEY_00_K] = KEY_K,
  [AT_KEY_00_L] = KEY_L,
  [AT_KEY_00_Semicolon] = KEY_SEMICOLON,
  [AT_KEY_00_Apostrophe] = KEY_APOSTROPHE,
  [AT_KEY_00_Enter] = KEY_ENTER,

  [AT_KEY_00_LeftShift] = KEY_LEFTSHIFT,
  [AT_KEY_00_Europe2] = KEY_102ND,
  [AT_KEY_00_Z] = KEY_Z,
  [AT_KEY_00_X] = KEY_X,
  [AT_KEY_00_C] = KEY_C,
  [AT_KEY_00_V] = KEY_V,
  [AT_KEY_00_B] = KEY_B,
  [AT_KEY_00_N] = KEY_N,
  [AT_KEY_00_M] = KEY_M,
  [AT_KEY_00_Comma] = KEY_COMMA,
  [AT_KEY_00_Period] = KEY_DOT,
  [AT_KEY_00_Slash] = KEY_SLASH,
  [AT_KEY_00_RightShift] = KEY_RIGHTSHIFT,

  [AT_KEY_00_LeftControl] = KEY_LEFTCTRL,
  [AT_KEY_00_LeftAlt] = KEY_LEFTALT,
  [AT_KEY_00_Space] = KEY_SPACE,

  [AT_KEY_00_NumLock] = KEY_NUMLOCK,
  [AT_KEY_00_KPAsterisk] = KEY_KPASTERISK,
  [AT_KEY_00_KPMinus] = KEY_KPMINUS,
  [AT_KEY_00_KPPlus] = KEY_KPPLUS,
  [AT_KEY_00_KPPeriod] = KEY_KPDOT,
  [AT_KEY_00_KP0] = KEY_KP0,
  [AT_KEY_00_KP1] = KEY_KP1,
  [AT_KEY_00_KP2] = KEY_KP2,
  [AT_KEY_00_KP3] = KEY_KP3,
  [AT_KEY_00_KP4] = KEY_KP4,
  [AT_KEY_00_KP5] = KEY_KP5,
  [AT_KEY_00_KP6] = KEY_KP6,
  [AT_KEY_00_KP7] = KEY_KP7,
  [AT_KEY_00_KP8] = KEY_KP8,
  [AT_KEY_00_KP9] = KEY_KP9,

  [AT_KEY_00_KPComma] = KEY_KPCOMMA,
  [AT_KEY_00_KPEqual] = KEY_KPEQUAL,

  [AT_KEY_00_International1] = KEY_RO,
  [AT_KEY_00_International2] = KEY_KATAKANAHIRAGANA,
  [AT_KEY_00_International3] = KEY_YEN,
  [AT_KEY_00_International4] = KEY_HENKAN,
  [AT_KEY_00_International5] = KEY_MUHENKAN,
  [AT_KEY_00_International6] = KEY_KPJPCOMMA,

  [AT_KEY_00_Language3] = KEY_KATAKANA,
  [AT_KEY_00_Language4] = KEY_HIRAGANA,
};

LINUX_KEY_MAP(atE0) = {
  [AT_KEY_E0_LeftGUI] = KEY_LEFTMETA,
  [AT_KEY_E0_RightAlt] = KEY_RIGHTALT,
  [AT_KEY_E0_RightGUI] = KEY_RIGHTMETA,
  [AT_KEY_E0_Context] = KEY_COMPOSE,
  [AT_KEY_E0_RightControl] = KEY_RIGHTCTRL,

  [AT_KEY_E0_Insert] = KEY_INSERT,
  [AT_KEY_E0_Delete] = KEY_DELETE,
  [AT_KEY_E0_Home] = KEY_HOME,
  [AT_KEY_E0_End] = KEY_END,
  [AT_KEY_E0_PageUp] = KEY_PAGEUP,
  [AT_KEY_E0_PageDown] = KEY_PAGEDOWN,

  [AT_KEY_E0_ArrowUp] = KEY_UP,
  [AT_KEY_E0_ArrowLeft] = KEY_LEFT,
  [AT_KEY_E0_ArrowDown] = KEY_DOWN,
  [AT_KEY_E0_ArrowRight] = KEY_RIGHT,

  [AT_KEY_E0_KPEnter] = KEY_KPENTER,
  [AT_KEY_E0_KPSlash] = KEY_KPSLASH,

  [AT_KEY_E0_Copy] = KEY_COPY,
  [AT_KEY_E0_Cut] = KEY_CUT,
  [AT_KEY_E0_Paste] = KEY_PASTE,
  [AT_KEY_E0_Undo] = KEY_UNDO,
  [AT_KEY_E0_Redo] = KEY_REDO,

  [AT_KEY_E0_MyComputer] = KEY_COMPUTER,
  [AT_KEY_E0_Calculator] = KEY_CALC,
  [AT_KEY_E0_Mail] = KEY_MAIL,
  [AT_KEY_E0_Mail_X1] = KEY_MAIL,

  [AT_KEY_E0_WebHome] = KEY_HOMEPAGE,
  [AT_KEY_E0_WebBookmarks] = KEY_BOOKMARKS,
  [AT_KEY_E0_WebSearch] = KEY_SEARCH,
  [AT_KEY_E0_WebBack] = KEY_BACK,
  [AT_KEY_E0_WebForward] = KEY_FORWARD,
  [AT_KEY_E0_WebRefresh] = KEY_REFRESH,
  [AT_KEY_E0_WebStop] = KEY_STOP,

  [AT_KEY_E0_Mute] = KEY_MUTE,
  [AT_KEY_E0_VolumeDown] = KEY_VOLUMEDOWN,
  [AT_KEY_E0_VolumeUp] = KEY_VOLUMEUP,

  [AT_KEY_E0_MediaVideo] = KEY_MEDIA,
  [AT_KEY_E0_MediaPlayPause] = KEY_PLAYPAUSE,
  [AT_KEY_E0_MediaStop] = KEY_STOPCD,
  [AT_KEY_E0_MediaPrevious] = KEY_PREVIOUSSONG,
  [AT_KEY_E0_MediaNext] = KEY_NEXTSONG,

  [AT_KEY_E0_Power] = KEY_POWER,
  [AT_KEY_E0_Sleep] = KEY_SLEEP,
  [AT_KEY_E0_Wake] = KEY_WAKEUP,
};

LINUX_KEY_MAP(atE1) = {
  [AT_KEY_E1_Pause] = KEY_PAUSE,
};

LINUX_KEY_MAP(ps2) = {
  [PS2_KEY_Escape] = KEY_ESC,
  [PS2_KEY_F1] = KEY_F1,
  [PS2_KEY_F2] = KEY_F2,
  [PS2_KEY_F3] = KEY_F3,
  [PS2_KEY_F4] = KEY_F4,
  [PS2_KEY_F5] = KEY_F5,
  [PS2_KEY_F6] = KEY_F6,
  [PS2_KEY_F7] = KEY_F7,
  [PS2_KEY_F8] = KEY_F8,
  [PS2_KEY_F9] = KEY_F9,
  [PS2_KEY_F10] = KEY_F10,
  [PS2_KEY_F11] = KEY_F11,
  [PS2_KEY_F12] = KEY_F12,
  [PS2_KEY_Pause] = KEY_PAUSE,
  [PS2_KEY_ScrollLock] = KEY_SCROLLLOCK,

  [PS2_KEY_Grave] = KEY_GRAVE,
  [PS2_KEY_1] = KEY_1,
  [PS2_KEY_2] = KEY_2,
  [PS2_KEY_3] = KEY_3,
  [PS2_KEY_4] = KEY_4,
  [PS2_KEY_5] = KEY_5,
  [PS2_KEY_6] = KEY_6,
  [PS2_KEY_7] = KEY_7,
  [PS2_KEY_8] = KEY_8,
  [PS2_KEY_9] = KEY_9,
  [PS2_KEY_0] = KEY_0,
  [PS2_KEY_Minus] = KEY_MINUS,
  [PS2_KEY_Equal] = KEY_EQUAL,
  [PS2_KEY_Backspace] = KEY_BACKSPACE,

  [PS2_KEY_Tab] = KEY_TAB,
  [PS2_KEY_Q] = KEY_Q,
  [PS2_KEY_W] = KEY_W,
  [PS2_KEY_E] = KEY_E,
  [PS2_KEY_R] = KEY_R,
  [PS2_KEY_T] = KEY_T,
  [PS2_KEY_Y] = KEY_Y,
  [PS2_KEY_U] = KEY_U,
  [PS2_KEY_I] = KEY_I,
  [PS2_KEY_O] = KEY_O,
  [PS2_KEY_P] = KEY_P,
  [PS2_KEY_LeftBracket] = KEY_LEFTBRACE,
  [PS2_KEY_RightBracket] = KEY_RIGHTBRACE,
  [PS2_KEY_Backslash] = KEY_BACKSLASH,
  [PS2_KEY_Europe1] = KEY_BACKSLASH,

  [PS2_KEY_CapsLock] = KEY_CAPSLOCK,
  [PS2_KEY_A] = KEY_A,
  [PS2_KEY_S] = KEY_S,
  [PS2_KEY_D] = KEY_D,
  [PS2_KEY_F] = KEY_F,
  [PS2_KEY_G] = KEY_G,
  [PS2_KEY_H] = KEY_H,
  [PS2_KEY_J] = KEY_J,
  [PS2_KEY_K] = KEY_K,
  [PS2_KEY_L] = KEY_L,
  [PS2_KEY_Semicolon] = KEY_SEMICOLON,
  [PS2_KEY_Apostrophe] = KEY_APOSTROPHE,
  [PS2_KEY_Enter] = KEY_ENTER,

  [PS2_KEY_LeftShift] = KEY_LEFTSHIFT,
  [PS2_KEY_Europe2] = KEY_102ND,
  [PS2_KEY_Z] = KEY_Z,
  [PS2_KEY_X] = KEY_X,
  [PS2_KEY_C] = KEY_C,
  [PS2_KEY_V] = KEY_V,
  [PS2_KEY_B] = KEY_B,
  [PS2_KEY_N] = KEY_N,
  [PS2_KEY_M] = KEY_M,
  [PS2_KEY_Comma] = KEY_COMMA,
  [PS2_KEY_Period] = KEY_DOT,
  [PS2_KEY_Slash] = KEY_SLASH,
  [PS2_KEY_RightShift] = KEY_RIGHTSHIFT,

  [PS2_KEY_LeftControl] = KEY_LEFTCTRL,
  [PS2_KEY_LeftAlt] = KEY_LEFTALT,
  [PS2_KEY_LeftGUI] = KEY_LEFTMETA,
  [PS2_KEY_Space] = KEY_SPACE,
  [PS2_KEY_RightAlt] = KEY_RIGHTALT,
  [PS2_KEY_RightGUI] = KEY_RIGHTMETA,
  [PS2_KEY_Context] = KEY_COMPOSE,
  [PS2_KEY_RightControl] = KEY_RIGHTCTRL,

  [PS2_KEY_Insert] = KEY_INSERT,
  [PS2_KEY_Delete] = KEY_DELETE,
  [PS2_KEY_Home] = KEY_HOME,
  [PS2_KEY_End] = KEY_END,
  [PS2_KEY_PageUp] = KEY_PAGEUP,
  [PS2_KEY_PageDown] = KEY_PAGEDOWN,

  [PS2_KEY_ArrowUp] = KEY_UP,
  [PS2_KEY_ArrowLeft] = KEY_LEFT,
  [PS2_KEY_ArrowDown] = KEY_DOWN,
  [PS2_KEY_ArrowRight] = KEY_RIGHT,

  [PS2_KEY_NumLock] = KEY_NUMLOCK,
  [PS2_KEY_KPSlash] = KEY_KPSLASH,
  [PS2_KEY_KPAsterisk] = KEY_KPASTERISK,
  [PS2_KEY_KPMinus] = KEY_KPMINUS,
  [PS2_KEY_KPPlus] = KEY_KPPLUS,
  [PS2_KEY_KPEnter] = KEY_KPENTER,
  [PS2_KEY_KPPeriod] = KEY_KPDOT,
  [PS2_KEY_KP0] = KEY_KP0,
  [PS2_KEY_KP1] = KEY_KP1,
  [PS2_KEY_KP2] = KEY_KP2,
  [PS2_KEY_KP3] = KEY_KP3,
  [PS2_KEY_KP4] = KEY_KP4,
  [PS2_KEY_KP5] = KEY_KP5,
  [PS2_KEY_KP6] = KEY_KP6,
  [PS2_KEY_KP7] = KEY_KP7,
  [PS2_KEY_KP8] = KEY_KP8,
  [PS2_KEY_KP9] = KEY_KP9,
  [PS2_KEY_KPComma] = KEY_KPCOMMA,

  [PS2_KEY_International1] = KEY_RO,
  [PS2_KEY_International2] = KEY_KATAKANAHIRAGANA,
  [PS2_KEY_International3] = KEY_YEN,
  [PS2_KEY_International4] = KEY_HENKAN,
  [PS2_KEY_International5] = KEY_MUHENKAN,
};

LINUX_KEY_MAP(hid) = {
  [HID_KEY_Escape] = KEY_ESC,
  [HID_KEY_F1] = KEY_F1,
  [HID_KEY_F2] = KEY_F2,
  [HID_KEY_F3] = KEY_F3,
  [HID_KEY_F4] = KEY_F4,
  [HID_KEY_F5] = KEY_F5,
  [HID_KEY_F6] = KEY_F6,
  [HID_KEY_F7] = KEY_F7,
  [HID_KEY_F8] = KEY_F8,
  [HID_KEY_F9] = KEY_F9,
  [HID_KEY_F10] = KEY_F10,
  [HID_KEY_F11] = KEY_F11,
  [HID_KEY_F12] = KEY_F12,
  [HID_KEY_Pause] = KEY_PAUSE,
  [HID_KEY_ScrollLock] = KEY_SCROLLLOCK,

  [HID_KEY_F13] = KEY_F13,
  [HID_KEY_F14] = KEY_F14,
  [HID_KEY_F15] = KEY_F15,
  [HID_KEY_F16] = KEY_F16,
  [HID_KEY_F17] = KEY_F17,
  [HID_KEY_F18] = KEY_F18,
  [HID_KEY_F19] = KEY_F19,
  [HID_KEY_F20] = KEY_F20,
  [HID_KEY_F21] = KEY_F21,
  [HID_KEY_F22] = KEY_F22,
  [HID_KEY_F23] = KEY_F23,
  [HID_KEY_F24] = KEY_F24,

  [HID_KEY_Grave] = KEY_GRAVE,
  [HID_KEY_1] = KEY_1,
  [HID_KEY_2] = KEY_2,
  [HID_KEY_3] = KEY_3,
  [HID_KEY_4] = KEY_4,
  [HID_KEY_5] = KEY_5,
  [HID_KEY_6] = KEY_6,
  [HID_KEY_7] = KEY_7,
  [HID_KEY_8] = KEY_8,
  [HID_KEY_9] = KEY_9,
  [HID_KEY_0] = KEY_0,
  [HID_KEY_Minus] = KEY_MINUS,
  [HID_KEY_Equal] = KEY_EQUAL,
  [HID_KEY_Backspace] = KEY_BACKSPACE,

  [HID_KEY_Tab] = KEY_TAB,
  [HID_KEY_Q] = KEY_Q,
  [HID_KEY_W] = KEY_W,
  [HID_KEY_E] = KEY_E,
  [HID_KEY_R] = KEY_R,
  [HID_KEY_T] = KEY_T,
  [HID_KEY_Y] = KEY_Y,
  [HID_KEY_U] = KEY_U,
  [HID_KEY_I] = KEY_I,
  [HID_KEY_O] = KEY_O,
  [HID_KEY_P] = KEY_P,
  [HID_KEY_LeftBracket] = KEY_LEFTBRACE,
  [HID_KEY_RightBracket] = KEY_RIGHTBRACE,
  [HID_KEY_Backslash] = KEY_BACKSLASH,
  [HID_KEY_Europe1] = KEY_BACKSLASH,

  [HID_KEY_CapsLock] = KEY_CAPSLOCK,
  [HID_KEY_A] = KEY_A,
  [HID_KEY_S] = KEY_S,
  [HID_KEY_D] = KEY_D,
  [HID_KEY_F] = KEY_F,
  [HID_KEY_G] = KEY_G,
  [HID_KEY_H] = KEY_H,
  [HID_KEY_J] = KEY_J,
  [HID_KEY_K] = KEY_K,
  [HID_KEY_L] = KEY_L,
  [HID_KEY_Semicolon] = KEY_SEMICOLON,
  [HID_KEY_Apostrophe] = KEY_APOSTROPHE,
  [HID_KEY_Enter] = KEY_ENTER,

  [HID_KEY_LeftShift] = KEY_LEFTSHIFT,
  [HID_KEY_Europe2] = KEY_102ND,
  [HID_KEY_Z] = KEY_Z,
  [HID_KEY_X] = KEY_X,
  [HID_KEY_C] = KEY_C,
  [HID_KEY_V] = KEY_V,
  [HID_KEY_B] = KEY_B,
  [HID_KEY_N] = KEY_N,
  [HID_KEY_M] = KEY_M,
  [HID_KEY_Comma] = KEY_COMMA,
  [HID_KEY_Period] = KEY_DOT,
  [HID_KEY_Slash] = KEY_SLASH,
  [HID_KEY_RightShift] = KEY_RIGHTSHIFT,

  [HID_KEY_LeftControl] = KEY_LEFTCTRL,
  [HID_KEY_LeftAlt] = KEY_LEFTALT,
  [HID_KEY_LeftGUI] = KEY_LEFTMETA,
  [HID_KEY_Space] = KEY_SPACE,
  [HID_KEY_RightAlt] = KEY_RIGHTALT,
  [HID_KEY_RightGUI] = KEY_RIGHTMETA,
  [HID_KEY_Context] = KEY_COMPOSE,
  [HID_KEY_RightControl] = KEY_RIGHTCTRL,

  [HID_KEY_Insert] = KEY_INSERT,
  [HID_KEY_Delete] = KEY_DELETE,
  [HID_KEY_Home] = KEY_HOME,
  [HID_KEY_End] = KEY_END,
  [HID_KEY_PageUp] = KEY_PAGEUP,
  [HID_KEY_PageDown] = KEY_PAGEDOWN,

  [HID_KEY_ArrowUp] = KEY_UP,
  [HID_KEY_ArrowLeft] = KEY_LEFT,
  [HID_KEY_ArrowDown] = KEY_DOWN,
  [HID_KEY_ArrowRight] = KEY_RIGHT,

  [HID_KEY_NumLock] = KEY_NUMLOCK,
  [HID_KEY_KPSlash] = KEY_KPSLASH,
  [HID_KEY_KPAsterisk] = KEY_KPASTERISK,
  [HID_KEY_KPMinus] = KEY_KPMINUS,
  [HID_KEY_KPPlus] = KEY_KPPLUS,
  [HID_KEY_KPEnter] = KEY_KPENTER,
  [HID_KEY_KPPeriod] = KEY_KPDOT,
  [HID_KEY_KP0] = KEY_KP0,
  [HID_KEY_KP1] = KEY_KP1,
  [HID_KEY_KP2] = KEY_KP2,
  [HID_KEY_KP3] = KEY_KP3,
  [HID_KEY_KP4] = KEY_KP4,
  [HID_KEY_KP5] = KEY_KP5,
  [HID_KEY_KP6] = KEY_KP6,
  [HID_KEY_KP7] = KEY_KP7,
  [HID_KEY_KP8] = KEY_KP8,
  [HID_KEY_KP9] = KEY_KP9,

  [HID_KEY_KPComma] = KEY_KPCOMMA,
  [HID_KEY_KPEqual] = KEY_KPEQUAL,

  [HID_KEY_International1] = KEY_RO,
  [HID_KEY_International2] = KEY_KATAKANAHIRAGANA,
  [HID_KEY_International3] = KEY_YEN,
  [HID_KEY_International4] = KEY_HENKAN,
  [HID_KEY_International5] = KEY_MUHENKAN,
  [HID_KEY_International6] = KEY_KPJPCOMMA,

  [HID_KEY_Language3] = KEY_KATAKANA,
  [HID_KEY_Language4] = KEY_HIRAGANA,
  [HID_KEY_Language5] = KEY_ZENKAKUHANKAKU,

  [HID_KEY_Copy] = KEY_COPY,
  [HID_KEY_Cut] = KEY_CUT,
  [HID_KEY_Paste] = KEY_PASTE,
  [HID_KEY_Undo] = KEY_UNDO,

  [HID_KEY_Mute] = KEY_MUTE,
  [HID_KEY_VolumeDown] = KEY_VOLUMEDOWN,
  [HID_KEY_VolumeUp] = KEY_VOLUMEUP,

  [HID_KEY_Power] = KEY_POWER,
};

#define LINUX_KEY_MAP_DESCRIPTOR(type) { \
  .name = #type, \
  .keys = LINUX_KEY_MAP_NAME(type), \
  .count = ARRAY_COUNT(LINUX_KEY_MAP_NAME(type)) \
}

const LinuxKeyMapDescriptor linuxKeyMapDescriptors[] = {
  LINUX_KEY_MAP_DESCRIPTOR(xt00),
  LINUX_KEY_MAP_DESCRIPTOR(xtE0),
  LINUX_KEY_MAP_DESCRIPTOR(xtE1),
  LINUX_KEY_MAP_DESCRIPTOR(at00),
  LINUX_KEY_MAP_DESCRIPTOR(atE0),
  LINUX_KEY_MAP_DESCRIPTOR(atE1),
  LINUX_KEY_MAP_DESCRIPTOR(ps2),
  LINUX_KEY_MAP_DESCRIPTOR(hid)
};

const unsigned char linuxKeyMapCount = ARRAY_COUNT(linuxKeyMapDescriptors);
#endif /* HAVE_LINUX_INPUT_H */

#ifdef HAVE_LINUX_UINPUT_H
#include <linux/uinput.h>

struct UinputObjectStruct {
  int fileDescriptor;
  BITMASK(pressedKeys, KEY_MAX+1, char);
};
#endif /* HAVE_LINUX_UINPUT_H */

int
installKernelModule (const char *name, unsigned char *status) {
  if (status && *status) return *status == 2;

  {
    const char *command = "modprobe";
    char buffer[0X100];
    if (status) ++*status;

    {
      const char *path = "/proc/sys/kernel/modprobe";
      FILE *stream = fopen(path, "r");

      if (stream) {
        char *line = fgets(buffer, sizeof(buffer), stream);

        if (line) {
          size_t length = strlen(line);
          if (length && (line[length-1] == '\n')) line[--length] = 0;
          if (length) command = line;
        }

        fclose(stream);
      } else {
        logMessage(LOG_WARNING, "cannot open %s: %s", path, strerror(errno));
      }
    }

    {
      const char *const arguments[] = {command, "-q", name, NULL};
      int ok = executeHostCommand(arguments) == 0;

      if (!ok) {
        logMessage(LOG_WARNING, "kernel module not installed: %s", name);
        return 0;
      }

      if (status) ++*status;
    }
  }

  return 1;
}

int
installSpeakerModule (void) {
  static unsigned char status = 0;
  return installKernelModule("pcspkr", &status);
}

int
installUinputModule (void) {
  static unsigned char status = 0;
  int wait = !status;
  int installed = installKernelModule("uinput", &status);

  if (!installed) wait = 0;
  if (wait) asyncWait(500);
  return installed;
}

static int
openDevice (const char *path, int flags, int allowModeSubset) {
  int descriptor;

  #ifdef O_CLOEXEC
  flags |= O_CLOEXEC;
  #endif /* O_CLOEXEC */

  if ((descriptor = open(path, flags)) != -1) goto opened;
  if (!allowModeSubset) goto failed;
  if ((flags & O_ACCMODE) != O_RDWR) goto failed;
  flags &= ~O_ACCMODE;

  {
    int error = errno;

    if (errno == EACCES) goto tryWriteOnly;
    if (errno == EROFS) goto tryReadOnly;
    goto failed;

  tryWriteOnly:
    if ((descriptor = open(path, (flags | O_WRONLY))) != -1) goto opened;

  tryReadOnly:
    if ((descriptor = open(path, (flags | O_RDONLY))) != -1) goto opened;

    errno = error;
  }

failed:
  logMessage(LOG_DEBUG, "cannot open device: %s: %s", path, strerror(errno));
  return -1;

opened:
  logMessage(LOG_DEBUG, "device opened: %s: fd=%d", path, descriptor);
  return descriptor;
}

static int
canContainDevices (const char *directory) {
  struct statvfs vfs;

  if (statvfs(directory, &vfs) == -1) {
    logSystemError("statvfs");
  } else if (vfs.f_flag & ST_NODEV) {
    logMessage(LOG_WARNING, "cannot contain device files: %s", directory);
    errno = EPERM;
  } else {
    return 1;
  }

  return 0;
}

static int
canCreateDevice (const char *path) {
  int yes = 0;
  char *directory;

  if ((directory = getPathDirectory(path))) {
    if (canContainDevices(directory)) yes = 1;
    free(directory);
  }

  return yes;
}

static int
createCharacterDevice (const char *path, int flags, int major, int minor) {
  int descriptor = -1;

  if (canCreateDevice(path)) {
    descriptor = openDevice(path, flags, 0);
  }

  if (descriptor == -1) {
    if (errno == ENOENT) {
      mode_t mode = S_IFCHR | S_IRUSR | S_IWUSR;

      if (mknod(path, mode, makedev(major, minor)) == -1) {
        logMessage(LOG_DEBUG,
          "cannot create device: %s: %s", path, strerror(errno)
        );
      } else {
        logMessage(LOG_DEBUG,
          "device created: %s mode=%06o major=%d minor=%d",
          path, mode, major, minor
        );

        descriptor = openDevice(path, flags, 0);
      }
    }
  }

  return descriptor;
}

int
openCharacterDevice (const char *name, int flags, int major, int minor) {
  char *path = getDevicePath(name);
  int descriptor;

  if (!path) {
    descriptor = -1;
  } else if ((descriptor = openDevice(path, flags, 1)) == -1) {
    if ((errno == ENOENT) || (errno == EACCES)) {
      free(path);

      if ((path = makeWritablePath(locatePathName(name)))) {
        descriptor = createCharacterDevice(path, flags, major, minor);
      }
    }
  }

  if (descriptor != -1) {
    int ok = 0;
    struct stat status;

    if (fstat(descriptor, &status) == -1) {
      logMessage(LOG_DEBUG, "cannot fstat device: %d [%s]: %s",
                 descriptor, path, strerror(errno));
    } else if (!S_ISCHR(status.st_mode)) {
      logMessage(LOG_DEBUG, "not a character device: %s: fd=%d", path, descriptor);
    } else {
      ok = 1;
    }

    if (!ok) {
      close(descriptor);
      logMessage(LOG_DEBUG, "device closed: %s: fd=%d", path, descriptor);
      descriptor = -1;
    }
  }

  if (path) free(path);
  return descriptor;
}

UinputObject *
newUinputObject (const char *name) {
#ifdef HAVE_LINUX_UINPUT_H
  UinputObject *uinput;

  if ((uinput = malloc(sizeof(*uinput)))) {
    memset(uinput, 0, sizeof(*uinput));
    installUinputModule();

    const char *device;
    {
      static const char *const names[] = {"uinput", "input/uinput", NULL};
      device = resolveDeviceName(names, 0, "uinput");
    }

    if (device) {
      if ((uinput->fileDescriptor = openCharacterDevice(device, O_RDWR, MISC_MAJOR, 223)) != -1) {
        struct uinput_user_dev description;
        
        memset(&description, 0, sizeof(description));
        snprintf(description.name, sizeof(description.name),
                 "%s %s %s",
                 PACKAGE_NAME, PACKAGE_VERSION, name);

        if (write(uinput->fileDescriptor, &description, sizeof(description)) != -1) {
#ifdef UI_SET_PHYS
          {
            extern const char *__progname;
            char topology[0X40];

            snprintf(topology, sizeof(topology),
                     "pid-%"PRIu32"/%s/%d",
                     (uint32_t)getpid(), __progname, uinput->fileDescriptor);

            if (ioctl(uinput->fileDescriptor, UI_SET_PHYS, topology) == -1) {
              logSystemError("ioctl[UI_SET_PHYS]");
            }
          }
#endif /* UI_SET_PHYS */

          logMessage(LOG_DEBUG, "uinput opened: %s: %s fd=%d",
                     device, description.name, uinput->fileDescriptor);

          return uinput;
        } else {
          logSystemError("write(struct uinput_user_dev)");
        }

        close(uinput->fileDescriptor);
      } else {
        logMessage(LOG_DEBUG, "cannot open uinput device: %s: %s", device, strerror(errno));
      }
    }

    free(uinput);
    uinput = NULL;
  } else {
    logMallocError();
  }
#else /* HAVE_LINUX_UINPUT_H */
  logMessage(LOG_WARNING, "uinput support not available");
  errno = ENOSYS;
#endif /* HAVE_LINUX_UINPUT_H */

  return NULL;
}

void
destroyUinputObject (UinputObject *uinput) {
#ifdef HAVE_LINUX_UINPUT_H
  releasePressedKeys(uinput);
  close(uinput->fileDescriptor);
  free(uinput);
#endif /* HAVE_LINUX_UINPUT_H */
}

int
getUinputFileDescriptor (UinputObject *uinput) {
  return uinput->fileDescriptor;
}

int
createUinputDevice (UinputObject *uinput) {
#ifdef HAVE_LINUX_UINPUT_H
  if (ioctl(uinput->fileDescriptor, UI_DEV_CREATE) != -1) return 1;
  logSystemError("ioctl[UI_DEV_CREATE]");
#endif /* HAVE_LINUX_UINPUT_H */

  return 0;
}

int
enableUinputEventType (UinputObject *uinput, int type) {
#ifdef HAVE_LINUX_UINPUT_H
  if (ioctl(uinput->fileDescriptor, UI_SET_EVBIT, type) != -1) return 1;
  logSystemError("ioctl[UI_SET_EVBIT]");
#endif /* HAVE_LINUX_UINPUT_H */

  return 0;
}

int
writeInputEvent (UinputObject *uinput, uint16_t type, uint16_t code, int32_t value) {
#ifdef HAVE_LINUX_UINPUT_H
  struct timeval now;
  gettimeofday(&now, NULL);

  struct input_event event = {
    .input_event_sec = now.tv_sec,
    .input_event_usec = now.tv_usec,

    .type = type,
    .code = code,
    .value = value,
  };

  if (write(uinput->fileDescriptor, &event, sizeof(event)) != -1) return 1;
  logSystemError("write(struct input_event)");
#endif /* HAVE_LINUX_UINPUT_H */

  return 0;
}

static int
writeSynReport (UinputObject *uinput) {
  return writeInputEvent(uinput, EV_SYN, SYN_REPORT, 0);
}

int
enableUinputKey (UinputObject *uinput, int key) {
#ifdef HAVE_LINUX_UINPUT_H
  if (ioctl(uinput->fileDescriptor, UI_SET_KEYBIT, key) != -1) return 1;
  logSystemError("ioctl[UI_SET_KEYBIT]");
#endif /* HAVE_LINUX_UINPUT_H */

  return 0;
}

int
writeKeyEvent (UinputObject *uinput, int key, int press) {
#ifdef HAVE_LINUX_UINPUT_H
  if (writeInputEvent(uinput, EV_KEY, key, press)) {
    if (press) {
      BITMASK_SET(uinput->pressedKeys, key);
    } else {
      BITMASK_CLEAR(uinput->pressedKeys, key);
    }

    if (writeSynReport(uinput)) {
      return 1;
    }
  }
#endif /* HAVE_LINUX_UINPUT_H */

  return 0;
}

int
releasePressedKeys (UinputObject *uinput) {
#ifdef HAVE_LINUX_UINPUT_H
  unsigned int key;

  for (key=0; key<=KEY_MAX; key+=1) {
    if (BITMASK_TEST(uinput->pressedKeys, key)) {
      if (!writeKeyEvent(uinput, key, 0)) return 0;
      BITMASK_CLEAR(uinput->pressedKeys, key);
    }
  }
#endif /* HAVE_LINUX_UINPUT_H */

  return 1;
}

int
writeRepeatDelay (UinputObject *uinput, int delay) {
  if (writeInputEvent(uinput, EV_REP, REP_DELAY, delay)) {
    if (writeSynReport(uinput)) {
      return 1;
    }
  }

  return 0;
}

int
writeRepeatPeriod (UinputObject *uinput, int period) {
  if (writeInputEvent(uinput, EV_REP, REP_PERIOD, period)) {
    if (writeSynReport(uinput)) {
      return 1;
    }
  }

  return 0;
}

#ifdef HAVE_LINUX_INPUT_H
static int
enableKeyboardKeys (UinputObject *uinput) {
  const LinuxKeyMapDescriptor *map = linuxKeyMapDescriptors;
  const LinuxKeyMapDescriptor *end = map + linuxKeyMapCount;
  BITMASK(enabledKeys, KEY_MAX+1, char);

  if (!enableUinputEventType(uinput, EV_KEY)) return 0;
  BITMASK_ZERO(enabledKeys);

  while (map < end) {
    unsigned int code;

    for (code=0; code<map->count; code+=1) {
      LinuxKeyCode key = map->keys[code];

      if (key) {
        if (!BITMASK_TEST(enabledKeys, key)) {
          BITMASK_SET(enabledKeys, key);
          if (!enableUinputKey(uinput, key)) return 0;
        }
      }
    }

    map += 1;
  }

  return 1;
}
#endif /* HAVE_LINUX_INPUT_H */

int
enableUinputSound (UinputObject *uinput, int sound) {
#ifdef HAVE_LINUX_UINPUT_H
  if (ioctl(uinput->fileDescriptor, UI_SET_SNDBIT, sound) != -1) return 1;
  logSystemError("ioctl[UI_SET_SNDBIT]");
#endif /* HAVE_LINUX_UINPUT_H */

  return 0;
}

int
enableUinputLed (UinputObject *uinput, int led) {
#ifdef HAVE_LINUX_UINPUT_H
  if (ioctl(uinput->fileDescriptor, UI_SET_LEDBIT, led) != -1) return 1;
  logSystemError("ioctl[UI_SET_LEDBIT]");
#endif /* HAVE_LINUX_UINPUT_H */

  return 0;
}

UinputObject *
newUinputKeyboard (const char *name) {
#ifdef HAVE_LINUX_INPUT_H
  UinputObject *uinput;

  if ((uinput = newUinputObject(name))) {
    if (enableKeyboardKeys(uinput)) {
      if (enableUinputEventType(uinput, EV_REP)) {
        if (createUinputDevice(uinput)) {
          return uinput;
        }
      }
    }

    destroyUinputObject(uinput);
  }
#endif /* HAVE_LINUX_INPUT_H */

  return NULL;
}

struct InputEventMonitorStruct {
  UinputObject *uinputObject;
  int fileDescriptor;
  AsyncHandle asyncHandle;

  UinputObjectPreparer *prepareUinputObject;
  InputEventHandler *handleInputEvent;
};

static void
closeInputEventMonitor (InputEventMonitor *monitor) {
  close(monitor->fileDescriptor);
  monitor->fileDescriptor = -1;
}

ASYNC_INPUT_CALLBACK(handleInterceptedInputEvent) {
  InputEventMonitor *monitor = parameters->data;
  static const char label[] = "input event monitor";

  if (parameters->error) {
    logMessage(LOG_DEBUG, "%s read error: fd=%d: %s",
               label, monitor->fileDescriptor, strerror(parameters->error));
    closeInputEventMonitor(monitor);
  } else if (parameters->end) {
    logMessage(LOG_DEBUG, "%s end-of-file: fd=%d",
               label, monitor->fileDescriptor);
    closeInputEventMonitor(monitor);
  } else {
    const struct input_event *event = parameters->buffer;

    if (parameters->length >= sizeof(*event)) {
      monitor->handleInputEvent(event);
      return sizeof(*event);
    }
  }

  return 0;
}

InputEventMonitor *
newInputEventMonitor (
  const char *name,
  UinputObjectPreparer *prepareUinputObject,
  InputEventHandler *handleInputEvent
) {
  InputEventMonitor *monitor;

  if ((monitor = malloc(sizeof(*monitor)))) {
    memset(monitor, 0, sizeof(*monitor));
    monitor->prepareUinputObject = prepareUinputObject;
    monitor->handleInputEvent = handleInputEvent;

    if ((monitor->uinputObject = newUinputObject(name))) {
      monitor->fileDescriptor = getUinputFileDescriptor(monitor->uinputObject);

      if (prepareUinputObject(monitor->uinputObject)) {
        if (createUinputDevice(monitor->uinputObject)) {
          if (asyncReadFile(&monitor->asyncHandle, monitor->fileDescriptor,
                            sizeof(struct input_event),
                            handleInterceptedInputEvent, monitor)) {
            logMessage(LOG_DEBUG, "input event monitor opened: fd=%d",
                       monitor->fileDescriptor);

            return monitor;
          }
        }
      }

      destroyUinputObject(monitor->uinputObject);
    }

    free(monitor);
  } else {
    logMallocError();
  }

  return NULL;
}

void
destroyInputEventMonitor (InputEventMonitor *monitor) {
  asyncCancelRequest(monitor->asyncHandle);
  destroyUinputObject(monitor->uinputObject);
  free(monitor);
}

void
initializeSystemObject (void) {
}
