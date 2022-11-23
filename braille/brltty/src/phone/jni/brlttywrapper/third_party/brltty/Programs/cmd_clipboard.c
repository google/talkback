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
#include <ctype.h>

#include "log.h"
#include "cmd_queue.h"
#include "cmd_clipboard.h"
#include "cmd_utils.h"
#include "brl_cmds.h"
#include "scr.h"
#include "routing.h"
#include "alert.h"
#include "queue.h"
#include "file.h"
#include "datafile.h"
#include "charset.h"
#include "core.h"

typedef struct {
  struct {
    wchar_t *characters;
    size_t size;
    size_t length;
  } buffer;

  struct {
    int column;
    int row;
    int offset;
  } begin;

  struct {
    Queue *queue;
  } history;
} ClipboardCommandData;

typedef struct {
  wchar_t *characters;
  size_t length;
} HistoryEntry;

static wchar_t *
cpbAllocateCharacters (size_t count) {
  {
    wchar_t *characters;
    if ((characters = malloc(count * sizeof(*characters)))) return characters;
  }

  logMallocError();
  return NULL;
}

static const HistoryEntry *
cpbGetHistory (ClipboardCommandData *ccd, unsigned int index) {
  Element *element = getStackElement(ccd->history.queue, index);

  if (!element) return NULL;
  return getElementItem(element);
}

static void
cpbAddHistory (ClipboardCommandData *ccd, const wchar_t *characters, size_t length) {
  if (length > 0) {
    Queue *queue = ccd->history.queue;
    Element *element = getStackHead(queue);

    if (element) {
      const HistoryEntry *entry = getElementItem(element);

      if (length == entry->length) {
        if (wmemcmp(characters, entry->characters, length) == 0) {
          return;
        }
      }
    }

    {
      HistoryEntry *entry;

      if ((entry = malloc(sizeof(*entry)))) {
        if ((entry->characters = cpbAllocateCharacters(length))) {
          wmemcpy(entry->characters, characters, length);
          entry->length = length;

          if (enqueueItem(queue, entry)) {
            return;
          }

          free(entry->characters);
        } else {
          logMallocError();
        }

        free(entry);
      } else {
        logMallocError();
      }
    }
  }
}

static const wchar_t *
cpbGetContent (ClipboardCommandData *ccd, size_t *length) {
  *length = ccd->buffer.length;
  return ccd->buffer.characters;
}

static void
cpbTruncateContent (ClipboardCommandData *ccd, size_t length) {
  if (length < ccd->buffer.length) ccd->buffer.length = length;
}

static void
cpbClearContent (ClipboardCommandData *ccd) {
  size_t length;
  const wchar_t *characters = cpbGetContent(ccd, &length);

  cpbAddHistory(ccd, characters, length);
  cpbTruncateContent(ccd, 0);
}

static int
cpbAddContent (ClipboardCommandData *ccd, const wchar_t *characters, size_t length) {
  size_t newLength = ccd->buffer.length + length;

  if (newLength > ccd->buffer.size) {
    size_t newSize = newLength | 0XFF;
    wchar_t *newCharacters = cpbAllocateCharacters(newSize);

    if (!newCharacters) {
      logMallocError();
      return 0;
    }

    wmemcpy(newCharacters, ccd->buffer.characters, ccd->buffer.length);
    if (ccd->buffer.characters) free(ccd->buffer.characters);
    ccd->buffer.characters = newCharacters;
    ccd->buffer.size = newSize;
  }

  wmemcpy(&ccd->buffer.characters[ccd->buffer.length], characters, length);
  ccd->buffer.length += length;
  return 1;
}

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

        if ((newBuffer = cpbAllocateCharacters(newLength))) {
          wmemcpy(newBuffer, toBuffer, (*length = newLength));
        }
      }
    }
  }

  return newBuffer;
}

static int
cpbEndOperation (ClipboardCommandData *ccd, const wchar_t *characters, size_t length) {
  cpbTruncateContent(ccd, ccd->begin.offset);
  if (!cpbAddContent(ccd, characters, length)) return 0;
  alert(ALERT_CLIPBOARD_END);
  return 1;
}

static void
cpbBeginOperation (ClipboardCommandData *ccd, int column, int row) {
  ccd->begin.column = column;
  ccd->begin.row = row;
  ccd->begin.offset = ccd->buffer.length;
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

    if (cpbEndOperation(ccd, buffer, length)) copied = 1;
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

        length = to - buffer;
      }

      if (cpbEndOperation(ccd, buffer, length)) copied = 1;
      free(buffer);
    }
  }

  return copied;
}

static int
cpbPaste (ClipboardCommandData *ccd, unsigned int index) {
  const wchar_t *characters;
  size_t length;

  if (index) {
    const HistoryEntry *entry = cpbGetHistory(ccd, index-1);

    if (!entry) return 0;
    characters = entry->characters;
    length = entry->length;
  } else {
    characters = cpbGetContent(ccd, &length);
  }

  if (!isMainScreen()) return 0;
  if (isRouting()) return 0;
  if (!length) return 0;

  {
    unsigned int i;

    for (i=0; i<length; i+=1)
      if (!insertScreenKey(characters[i]))
        return 0;
  }

  return 1;
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
  size_t length;
  const wchar_t *characters = cpbGetContent(ccd, &length);

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

  return ok;
}

static int
cpbRestore (ClipboardCommandData *ccd) {
  int ok = 0;
  FILE *stream = cpbOpenFile("r");

  if (stream) {
    size_t size = 0X1000;
    char buffer[size];
    size_t length = 0;

    cpbClearContent(ccd);
    ok = 1;

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

            if (!cpbAddContent(ccd, &wc, 1)) {
              ok = 0;
              break;
            }
          }
        }
      }

      if (done) break;
    } while (ok);

    if (fclose(stream) == EOF) {
      logSystemError("fclose");
      ok = 0;
    }
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
      if ((cpbBuffer = cpbGetContent(ccd, &cpbLength))) {
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
              size_t i;
              for (i=0; i<length; i++) buffer[i] = towlower(buffer[i]);
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
              if (increment < 0)
                while (findCharacters(&address, &length, characters, count))
                  ++address, --length;

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
          if (getCharacterCoordinates(arg, &column, &row, 0, 0)) {
            if (clear) cpbClearContent(ccd);
            cpbBeginOperation(ccd, column, row);
          } else {
            alert(ALERT_COMMAND_REJECTED);
          }

          break;
        }

        case BRL_CMD_BLK(COPY_RECT): {
          int column, row;

          if (getCharacterCoordinates(arg, &column, &row, 1, 1))
            if (cpbRectangularCopy(ccd, column, row))
              break;

          alert(ALERT_COMMAND_REJECTED);
          break;
        }

        case BRL_CMD_BLK(COPY_LINE): {
          int column, row;

          if (getCharacterCoordinates(arg, &column, &row, 1, 1))
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

            if (getCharacterCoordinates(arg, &column1, &row1, 0, 0)) {
              int column2, row2;

              if (getCharacterCoordinates(ext, &column2, &row2, 1, 1)) {
                if (clear) cpbClearContent(ccd);
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
cpbDeallocateHistoryEntry (void *item, void *data) {
  HistoryEntry *entry = item;

  if (entry->characters) free(entry->characters);
  free(entry);
}

static void
destroyClipboardCommandData (void *data) {
  ClipboardCommandData *ccd = data;

  deallocateQueue(ccd->history.queue);
  free(ccd);
}

int
addClipboardCommands (void) {
  ClipboardCommandData *ccd;

  if ((ccd = malloc(sizeof(*ccd)))) {
    memset(ccd, 0, sizeof(*ccd));

    ccd->buffer.characters = NULL;
    ccd->buffer.size = 0;
    ccd->buffer.length = 0;

    ccd->begin.column = 0;
    ccd->begin.row = 0;
    ccd->begin.offset = -1;

    if ((ccd->history.queue = newQueue(cpbDeallocateHistoryEntry, NULL))) {
      if (pushCommandHandler("clipboard", KTB_CTX_DEFAULT,
                             handleClipboardCommands, destroyClipboardCommandData, ccd)) {
        return 1;
      }

      deallocateQueue(ccd->history.queue);
    }

    free(ccd);
  } else {
    logMallocError();
  }

  return 0;
}
