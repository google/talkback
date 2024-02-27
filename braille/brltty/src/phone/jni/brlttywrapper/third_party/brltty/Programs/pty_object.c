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
#include <fcntl.h>

#include "log.h"
#include "pty_object.h"
#include "scr_types.h"

struct PtyObjectStruct {
  char *path;
  int master;

  unsigned char logLevel;
  unsigned char logInput:1;
};

PtyObject *
ptyNewObject (void) {
  PtyObject *pty;

  if ((pty = malloc(sizeof(*pty)))) {
    memset(pty, 0, sizeof(*pty));

    pty->path = NULL;
    pty->master = INVALID_FILE_DESCRIPTOR;

    pty->logLevel = LOG_DEBUG;
    pty->logInput = 0;

    if ((pty->master = posix_openpt(O_RDWR)) != -1) {
      if ((pty->path = ptsname(pty->master))) {
        if ((pty->path = strdup(pty->path))) {
          if (grantpt(pty->master) != -1) {
            if (unlockpt(pty->master) != -1) {
              return pty;
            } else {
              logSystemError("unlockpt");
            }
          } else {
            logSystemError("grantpt");
          }

          free(pty->path);
        } else {
          logMallocError();
        }
      } else {
        logSystemError("ptsname");
      }

      close(pty->master);
    } else {
      logSystemError("posix_openpt");
    }

    free(pty);
  } else {
    logMallocError();
  }

  return NULL;
}

const char *
ptyGetPath (const PtyObject *pty) {
  return pty->path;
}

int
ptyGetMaster (const PtyObject *pty) {
  return pty->master;
}

void
ptySetLogLevel (PtyObject *pty, unsigned char level) {
  pty->logLevel = level;
}

void
ptySetLogInput (PtyObject *pty, int yes) {
  pty->logInput = yes;
}

int
ptyWriteInputData (PtyObject *pty, const void *data, size_t length) {
  if (pty->logInput) {
    logBytes(pty->logLevel, "pty input", data, length);
  }

  if (write(pty->master, data, length) != -1) return 1;
  logSystemError("pty write input");
  return 0;
}

int
ptyWriteInputCharacter (PtyObject *pty, wchar_t character, int kxMode) {
  if (!isSpecialKey(character)) {
    char buffer[MB_CUR_MAX];
    int count = wctomb(buffer, character);
    if (count == -1) return 0;
    return ptyWriteInputData(pty, buffer, count);
  }

  const char *sequence = NULL;
  char buffer[0X20];

  #define KEY(key, seq) case SCR_KEY_##key: sequence = seq; break;
  switch (character) {
    KEY(ENTER       , "\r")
    KEY(TAB         , "\t")
    KEY(BACKSPACE   , "\x7F")
    KEY(ESCAPE      , "\x1B")

    KEY(CURSOR_UP   , "\x1BOA")
    KEY(CURSOR_DOWN , "\x1BOB")
    KEY(CURSOR_RIGHT, "\x1BOC")
    KEY(CURSOR_LEFT , "\x1BOD")

    KEY(HOME        , "\x1B[1~")
    KEY(INSERT      , "\x1B[2~")
    KEY(DELETE      , "\x1B[3~")
    KEY(END         , "\x1B[4~")
    KEY(PAGE_UP     , "\x1B[5~")
    KEY(PAGE_DOWN   , "\x1B[6~")

    KEY(F1          , "\x1BOP")
    KEY(F2          , "\x1BOQ")
    KEY(F3          , "\x1BOR")
    KEY(F4          , "\x1BOS")
    KEY(F5          , "\x1B[15~")
    KEY(F6          , "\x1B[17~")
    KEY(F7          , "\x1B[18~")
    KEY(F8          , "\x1B[19~")
    KEY(F9          , "\x1B[20~")
    KEY(F10         , "\x1B[21~")
    KEY(F11         , "\x1B[23~")
    KEY(F12         , "\x1B[24~")

    default:
      logMessage(LOG_WARNING, "unsupported pty screen key: %04X", character);
      break;
  }
  #undef KEY

  if (sequence) {
    switch (character) {
      case SCR_KEY_CURSOR_LEFT:
      case SCR_KEY_CURSOR_RIGHT:
      case SCR_KEY_CURSOR_UP:
      case SCR_KEY_CURSOR_DOWN:
        strcpy(buffer, sequence);
        buffer[1] = kxMode? 'O': '[';
        sequence = buffer;
        break;
    }

    if (!ptyWriteInputData(pty, sequence, strlen(sequence))) {
      return 0;
    }
  }

  return 1;
}

void
ptyCloseMaster (PtyObject *pty) {
  if (pty->master != INVALID_FILE_DESCRIPTOR) {
    close(pty->master);
    pty->master = INVALID_FILE_DESCRIPTOR;
  }
}

int
ptyOpenSlave (const PtyObject *pty, int *fileDescriptor) {
  int result = open(pty->path, O_RDWR);
  int opened = result != INVALID_FILE_DESCRIPTOR;

  if (opened) {
    *fileDescriptor = result;
  } else {
    logSystemError("pty slave open");
  }

  return opened;
}

void
ptyDestroyObject (PtyObject *pty) {
  ptyCloseMaster(pty);
  free(pty->path);
  free(pty);
}
