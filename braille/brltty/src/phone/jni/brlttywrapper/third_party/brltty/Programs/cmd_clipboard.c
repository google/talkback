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
#include <ctype.h>

#include "log.h"
#include "alert.h"
#include "cmd_queue.h"
#include "cmd_utils.h"
#include "cmd_clipboard.h"
#include "clipboard.h"
#include "brl_cmds.h"
#include "scr.h"
#include "routing.h"
#include "file.h"
#include "datafile.h"
#include "utf8.h"
#include "core.h"

typedef struct {
  ClipboardObject *clipboard;

  struct {
    int column;
    int row;
    int offset;
  } begin;
} ClipboardCommandData;

static wchar_t *
cpbReadScreen (ClipboardCommandData *ccd, size_t *length, int fromColumn, int fromRow, int toColumn, int toRow) {
  wchar_t *newBuffer = NULL;
  int columns = toColumn - fromColumn + 1;
  int rows = toRow - fromRow + 1;

  if ((columns >= 1) && (rows >= 1) && (ccd->begin.offset >= 0)) {
    wchar_t fromBuffer[rows * columns];

    if (readScreenText(fromColumn, fromRow, columns, rows, fromBuffer)) {
      wchar_t toBuffer[rows * (columns + 1)];
      wchar_t *toAddress = toBuffer;

      const wchar_t *fromAddress = fromBuffer;
      int row;

      for (row=fromRow; row<=toRow; row+=1) {
        int column;

        for (column=fromColumn; column<=toColumn; column+=1) {
          wchar_t character = *fromAddress++;
          if (iswcntrl(character) || iswspace(character)) character = WC_C(' ');
          *toAddress++ = character;
        }

        if (row != toRow) *toAddress++ = WC_C('\r');
      }

      /* make a new permanent buffer of just the right size */
      {
        size_t newLength = toAddress - toBuffer;

        if ((newBuffer = allocateCharacters(newLength))) {
          wmemcpy(newBuffer, toBuffer, (*length = newLength));
        }
      }
    }
  }

  return newBuffer;
}

static int
cpbEndOperation (ClipboardCommandData *ccd, const wchar_t *characters, size_t length,
                 int insertCR) {
  lockMainClipboard();
    if (insertCR && ccd->begin.offset >= 1) {
      size_t length;
      const wchar_t *characters = getClipboardContent(ccd->clipboard, &length);
      if (length > ccd->begin.offset) length = ccd->begin.offset;
      while (length > 0) {
        size_t last = length - 1;
        if (characters[last] == WC_C('\r')) insertCR = 0;
        if (characters[last] != WC_C(' ')) break;
        length = last;
      }
      ccd->begin.offset = length;
    }
    if (ccd->begin.offset <= 0) insertCR = 0;

    int truncated = truncateClipboardContent(ccd->clipboard, ccd->begin.offset);
    if (insertCR) appendClipboardContent(ccd->clipboard, &(wchar_t){WC_C('\r')}, 1);
    int appended = appendClipboardContent(ccd->clipboard, characters, length);
  unlockMainClipboard();

  if (truncated || appended) onMainClipboardUpdated();
  if (!appended) return 0;
  alert(ALERT_CLIPBOARD_END);
  return 1;
}

static void
cpbBeginOperation (ClipboardCommandData *ccd, int column, int row) {
  ccd->begin.column = column;
  ccd->begin.row = row;

  lockMainClipboard();
    ccd->begin.offset = getClipboardContentLength(ccd->clipboard);
  unlockMainClipboard();

  alert(ALERT_CLIPBOARD_BEGIN);
}

static int
cpbRectangularCopy (ClipboardCommandData *ccd, int column, int row) {
  int copied = 0;
  size_t length;
  wchar_t *buffer = cpbReadScreen(ccd, &length, ccd->begin.column, ccd->begin.row, column, row);

  if (buffer) {
    {
      const wchar_t *from = buffer;
      const wchar_t *end = from + length;
      wchar_t *to = buffer;
      int spaces = 0;

      while (from != end) {
        wchar_t character = *from++;

        switch (character) {
          case WC_C(' '):
            spaces += 1;
            continue;

          case WC_C('\r'):
            spaces = 0;
            break;

          default:
            break;
        }

        while (spaces) {
          *to++ = WC_C(' ');
          spaces -= 1;
        }

        *to++ = character;
      }

      length = to - buffer;
    }

    if (cpbEndOperation(ccd, buffer, length, 1)) copied = 1;
    free(buffer);
  }

  return copied;
}

static int
cpbLinearCopy (ClipboardCommandData *ccd, int column, int row) {
  int copied = 0;
  ScreenDescription screen;
  describeScreen(&screen);

  {
    int rightColumn = screen.cols - 1;
    size_t length;
    wchar_t *buffer = cpbReadScreen(ccd, &length, 0, ccd->begin.row, rightColumn, row);

    if (buffer) {
      if (column < rightColumn) {
        wchar_t *start = buffer + length;

        while (start != buffer) {
          if (*--start == WC_C('\r')) {
            start += 1;
            break;
          }
        }

        {
          int adjustment = (column + 1) - (buffer + length - start);
          if (adjustment < 0) length += adjustment;
        }
      }

      if (ccd->begin.column) {
        wchar_t *start = wmemchr(buffer, WC_C('\r'), length);
        if (!start) start = buffer + length;
        if ((start - buffer) > ccd->begin.column) start = buffer + ccd->begin.column;
        if (start != buffer) wmemmove(buffer, start, (length -= start - buffer));
      }

      {
        const wchar_t *from = buffer;
        const wchar_t *end = from + length;
        wchar_t *to = buffer;
        int spaces = 0;
        int newlines = 0;

        while (from != end) {
          wchar_t character = *from++;

          switch (character) {
            case WC_C(' '):
              spaces += 1;
              continue;

            case WC_C('\r'):
              newlines += 1;
              continue;

            default:
              break;
          }

          if (newlines) {
            if ((newlines > 1) || (spaces > 0)) spaces = 1;
            newlines = 0;
          }

          while (spaces) {
            *to++ = WC_C(' ');
            spaces -= 1;
          }

          *to++ = character;
        }

        if (spaces || newlines) *to++ = WC_C(' ');
        length = to - buffer;
      }

      if (cpbEndOperation(ccd, buffer, length, 0)) copied = 1;
      free(buffer);
    }
  }

  return copied;
}

static int
pasteCharacters (const wchar_t *characters, size_t count) {
  if (!characters) return 0;
  if (!count) return 0;

  if (!isMainScreen()) return 0;
  if (isRouting()) return 0;

  {
    unsigned int i;

    for (i=0; i<count; i+=1) {
      if (!insertScreenKey(characters[i])) return 0;
    }
  }

  return 1;
}

static int
cpbPaste (ClipboardCommandData *ccd, unsigned int index) {
  int pasted;

  lockMainClipboard();
    const wchar_t *characters;
    size_t length;

    if (index) {
      characters = getClipboardHistory(ccd->clipboard, index-1, &length);
    } else {
      characters = getClipboardContent(ccd->clipboard, &length);
    }

    while (length > 0) {
      size_t last = length - 1;
      if (characters[last] != WC_C(' ')) break;
      length = last;
    }

    pasted = pasteCharacters(characters, length);
  unlockMainClipboard();

  return pasted;
}

static FILE *
cpbOpenFile (const char *mode) {
  const char *file = "clipboard";
  char *path = makeUpdatablePath(file);

  if (path) {
    FILE *stream = openDataFile(path, mode, 0);

    free(path);
    path = NULL;

    if (stream) return stream;
  }

  return NULL;
}

static int
cpbSave (ClipboardCommandData *ccd) {
  int ok = 0;

  lockMainClipboard();
    size_t length;
    const wchar_t *characters = getClipboardContent(ccd->clipboard, &length);

    if (length > 0) {
      FILE *stream = cpbOpenFile("w");

      if (stream) {
        if (writeUtf8Characters(stream, characters, length)) {
          ok = 1;
        }

        if (fclose(stream) == EOF) {
          logSystemError("fclose");
          ok = 0;
        }
      }
    }
  unlockMainClipboard();

  return ok;
}

static int
cpbRestore (ClipboardCommandData *ccd) {
  int ok = 0;
  FILE *stream = cpbOpenFile("r");

  if (stream) {
    int wasUpdated = 0;

    lockMainClipboard();
    {
      int isClear = 0;

      if (isClipboardEmpty(ccd->clipboard)) {
        isClear = 1;
      } else if (clearClipboardContent(ccd->clipboard)) {
        isClear = 1;
        wasUpdated = 1;
      }

      if (isClear) {
        ok = 1;

        size_t size = 0X1000;
        char buffer[size];
        size_t length = 0;

        do {
          size_t count = fread(&buffer[length], 1, (size - length), stream);
          int done = (length += count) < size;

          if (ferror(stream)) {
            logSystemError("fread");
            ok = 0;
          } else {
            const char *next = buffer;
            size_t left = length;

            while (left > 0) {
              const char *start = next;
              wint_t wi = convertUtf8ToWchar(&next, &left);

              if (wi == WEOF) {
                length = next - start;

                if (left > 0) {
                  logBytes(LOG_ERR, "invalid UTF-8 character", start, length);
                  ok = 0;
                  break;
                }

                memmove(buffer, start, length);
              } else {
                wchar_t wc = wi;

                if (appendClipboardContent(ccd->clipboard, &wc, 1)) {
                  wasUpdated = 1;
                } else {
                  ok = 0;
                  break;
                }
              }
            }
          }

          if (done) break;
        } while (ok);
      }
    }
    unlockMainClipboard();

    if (fclose(stream) == EOF) {
      logSystemError("fclose");
      ok = 0;
    }

    if (wasUpdated) onMainClipboardUpdated();
  }

  return ok;
}

static int
findCharacters (const wchar_t **address, size_t *length, const wchar_t *characters, size_t count) {
  const wchar_t *ptr = *address;
  size_t len = *length;

  while (count <= len) {
    const wchar_t *next = wmemchr(ptr, *characters, len);
    if (!next) break;

    len -= next - ptr;
    if (wmemcmp((ptr = next), characters, count) == 0) {
      *address = ptr;
      *length = len;
      return 1;
    }

    ++ptr, --len;
  }

  return 0;
}

static int
handleClipboardCommands (int command, void *data) {
  ClipboardCommandData *ccd = data;

  switch (command & BRL_MSK_CMD) {
    case BRL_CMD_PASTE:
      if (!cpbPaste(ccd, 0)) alert(ALERT_COMMAND_REJECTED);
      break;

    case BRL_CMD_CLIP_SAVE:
      alert(cpbSave(ccd)? ALERT_COMMAND_DONE: ALERT_COMMAND_REJECTED);
      break;

    case BRL_CMD_CLIP_RESTORE:
      alert(cpbRestore(ccd)? ALERT_COMMAND_DONE: ALERT_COMMAND_REJECTED);
      break;

    {
      int increment;

      const wchar_t *cpbBuffer;
      size_t cpbLength;

    case BRL_CMD_PRSEARCH:
      increment = -1;
      goto doSearch;

    case BRL_CMD_NXSEARCH:
      increment = 1;
      goto doSearch;

    doSearch:
      lockMainClipboard();
        if ((cpbBuffer = getClipboardContent(ccd->clipboard, &cpbLength))) {
          int found = 0;
          size_t count = cpbLength;

          if (count <= scr.cols) {
            int line = ses->winy;
            wchar_t buffer[scr.cols];
            wchar_t characters[count];

            {
              unsigned int i;
              for (i=0; i<count; i+=1) characters[i] = towlower(cpbBuffer[i]);
            }

            while ((line >= 0) && (line <= (int)(scr.rows - brl.textRows))) {
              const wchar_t *address = buffer;
              size_t length = scr.cols;
              readScreenText(0, line, length, 1, buffer);

              {
                for (size_t i=0; i<length; i+=1) buffer[i] = towlower(buffer[i]);
              }

              if (line == ses->winy) {
                if (increment < 0) {
                  int end = ses->winx + count - 1;
                  if (end < length) length = end;
                } else {
                  int start = ses->winx + textCount;
                  if (start > length) start = length;
                  address += start;
                  length -= start;
                }
              }

              if (findCharacters(&address, &length, characters, count)) {
                if (increment < 0) {
                  while (findCharacters(&address, &length, characters, count)) {
                    ++address, --length;
                  }
                }

                ses->winy = line;
                ses->winx = (address - buffer) / textCount * textCount;
                found = 1;
                break;
              }

              line += increment;
            }
          }

          if (!found) alert(ALERT_BOUNCE);
        } else {
          alert(ALERT_COMMAND_REJECTED);
        }
      unlockMainClipboard();

      break;
    }

    default: {
      int arg = command & BRL_MSK_ARG;
      int ext = BRL_CODE_GET(EXT, command);

      switch (command & BRL_MSK_BLK) {
        {
          int clear;
          int column, row;

        case BRL_CMD_BLK(CLIP_NEW):
          clear = 1;
          goto doClipBegin;

        case BRL_CMD_BLK(CLIP_ADD):
          clear = 0;
          goto doClipBegin;

        doClipBegin:
          if (getCharacterCoordinates(arg, &row, &column, NULL, 0)) {
            if (clear) clearClipboardContent(ccd->clipboard);
            cpbBeginOperation(ccd, column, row);
          } else {
            alert(ALERT_COMMAND_REJECTED);
          }

          break;
        }

        case BRL_CMD_BLK(COPY_RECT): {
          int column, row;

          if (getCharacterCoordinates(arg, &row, NULL, &column, 1))
            if (cpbRectangularCopy(ccd, column, row))
              break;

          alert(ALERT_COMMAND_REJECTED);
          break;
        }

        case BRL_CMD_BLK(COPY_LINE): {
          int column, row;

          if (getCharacterCoordinates(arg, &row, NULL, &column, 1))
            if (cpbLinearCopy(ccd, column, row))
              break;

          alert(ALERT_COMMAND_REJECTED);
          break;
        }

        {
          int clear;

        case BRL_CMD_BLK(CLIP_COPY):
          clear = 1;
          goto doCopy;

        case BRL_CMD_BLK(CLIP_APPEND):
          clear = 0;
          goto doCopy;

        doCopy:
          if (ext > arg) {
            int column1, row1;

            if (getCharacterCoordinates(arg, &row1, &column1, NULL, 0)) {
              int column2, row2;

              if (getCharacterCoordinates(ext, &row2, NULL, &column2, 1)) {
                if (clear) clearClipboardContent(ccd->clipboard);
                cpbBeginOperation(ccd, column1, row1);
                if (cpbLinearCopy(ccd, column2, row2)) break;
              }
            }
          }

          alert(ALERT_COMMAND_REJECTED);
          break;
        }

        case BRL_CMD_BLK(PASTE_HISTORY):
          if (!cpbPaste(ccd, arg)) alert(ALERT_COMMAND_REJECTED);
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
destroyClipboardCommandData (void *data) {
  ClipboardCommandData *ccd = data;
  free(ccd);
}

int
addClipboardCommands (void) {
  ClipboardCommandData *ccd;

  if ((ccd = malloc(sizeof(*ccd)))) {
    memset(ccd, 0, sizeof(*ccd));
    ccd->clipboard = getMainClipboard();

    ccd->begin.column = 0;
    ccd->begin.row = 0;
    ccd->begin.offset = -1;

    if (pushCommandHandler("clipboard", KTB_CTX_DEFAULT,
                           handleClipboardCommands, destroyClipboardCommandData, ccd)) {
      return 1;
    }

    free(ccd);
  } else {
    logMallocError();
  }

  return 0;
}
