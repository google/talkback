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
#include <sys/stat.h>
#include <sys/msg.h>
#include <sys/shm.h>

#include "log.h"
#include "scr_emulator.h"

static const int ipcCreationFlags = IPC_CREAT | IPC_EXCL | S_IRUSR | S_IWUSR;

void
moveScreenCharacters (ScreenSegmentCharacter *to, const ScreenSegmentCharacter *from, size_t count) {
  if (count && (from != to)) {
    memmove(to, from, (count * sizeof(*from)));
  }
}

void
setScreenCharacters (ScreenSegmentCharacter *from, const ScreenSegmentCharacter *to, const ScreenSegmentCharacter *character) {
  while (from < to) *from++ = *character;
}

void
propagateScreenCharacter (ScreenSegmentCharacter *from, const ScreenSegmentCharacter *to) {
  setScreenCharacters(from+1, to, from);
}

void
fillScreenRows (ScreenSegmentHeader *segment, unsigned int row, unsigned int count, const ScreenSegmentCharacter *character) {
  while (count--) {
    const ScreenSegmentCharacter *to;
    ScreenSegmentCharacter *from = getScreenRow(segment, row++, &to);
    setScreenCharacters(from, to, character);
  }
}

void
moveScreenRows (ScreenSegmentHeader *segment, unsigned int from, unsigned int to, unsigned int count) {
  if (count && (from != to)) {
    moveScreenCharacters(
      getScreenRow(segment, to, NULL),
      getScreenRow(segment, from, NULL),
      (count * segment->screenWidth)
    );
  }
}

#define SWAP(a, b) do { (a) ^= (b); (b) ^= (a); (a) ^= (b); } while (0)

#undef HAVE_BUILTIN_CTZ
#ifdef __has_builtin
#if __has_builtin(__builtin_ctz)
#define HAVE_BUILTIN_CTZ
#endif /* __has_builtin(__builtin_ctz) */
#endif /* __has_builtin */

#ifdef HAVE_BUILTIN_CTZ
static inline int
ctz (unsigned int x) {
  return __builtin_ctz(x);
}

#else /* HAVE_BUILTIN_CTZ */
#include <string.h>

static inline int
ctz (unsigned int x) {
  return ffs(x) - 1;
}
#endif /* HAVE_BUILTIN_CTZ */

/* Greatest Common Divisor
 *
 * gcd(a,b) computes the greatest common divisor of a and b. I included 
 * a highly optimized implementation for speed. But the simplest 
 * implementation would look like:
 *
 * unsigned long gcd(unsigned long a, unsigned long b) {
 *   if (b == 0) return a;
 *   return gcd(b, a % b);
 * }
 */
static unsigned int
gcd (unsigned int a, unsigned int b) {
  unsigned int r = a | b;
  if (!a || !b) return r;

  b >>= ctz(b);
  if (b == 1) return r & -r;

  while (1) {
    a >>= ctz(a);
    if (a == 1) return r & -r;
    if (a == b) return a << ctz(r);

    if (a < b) SWAP(a, b);
    a -= b;
  }
}

/* Scrolling the Row Array
 *
 * The idea is to have lines indexed into an array. Then, a screen scroll 
 * can be achieved by performing an array rotation. To scroll one line 
 * up, the array is rotated left by one position and what used to be the 
 * top row becomes the bottom row and gets cleared. To scroll one line 
 * down, the array is rotated left by n-1 positions instead, and the bottom 
 * row becomes the top row. And this works the same regardless of the 
 * number of lines to scroll.
 *
 * The array rotation algorithm used here is complexity O(n) in execution 
 * and O(1) in memory usage, n being the array size. The scroll amount 
 * doesn't affect complexity.
 *
 * See https://www.geeksforgeeks.org/array-rotation/ for algorithmic 
 * details.
 */
void
scrollScreenRows (ScreenSegmentHeader *segment, unsigned int top, unsigned int size, unsigned int count, int down) {
  if (haveScreenRowArray(segment)) {
    unsigned int delta = down? (size - count): count;

    for (unsigned int i=0; i<gcd(delta, size); i+=1) {
      ScreenSegmentRow row = getScreenRowArray(segment)[top + i];
      unsigned int j = i;

      while (1) {
        unsigned int k = j + delta;
        if (k >= size) k -= size;
        if (k == i) break;

        getScreenRowArray(segment)[top + j] = getScreenRowArray(segment)[top + k];
        j = k;
      }

      getScreenRowArray(segment)[top + j] = row;
    }
  } else if (down) {
    moveScreenRows(segment, top, (top + count), (size - count));
  } else {
    moveScreenRows(segment, (top + count), top, (size - count));
  }
}

int
destroyScreenSegment (int identifier) {
  if (shmctl(identifier, IPC_RMID, NULL) != -1) return 1;
  logSystemError("shmctl[IPC_RMID]");
  return 0;
}

static void
initializeScreenCharacters (ScreenSegmentCharacter *from, const ScreenSegmentCharacter *to) {
  const ScreenSegmentCharacter initializer = {
    .text = ' ',
    .foreground = SCREEN_SEGMENT_COLOR_WHITE,
    .background = SCREEN_SEGMENT_COLOR_BLACK,
    .alpha = UINT8_MAX,
  };

  setScreenCharacters(from, to, &initializer);
}

ScreenSegmentHeader *
createScreenSegment (int *identifier, key_t key, int columns, int rows, int enableRowArray) {
  size_t rowsSize = enableRowArray? (sizeof(ScreenSegmentRow) * rows): 0;
  size_t charactersSize = sizeof(ScreenSegmentCharacter) * rows * columns;

  size_t segmentSize = sizeof(ScreenSegmentHeader) + rowsSize + charactersSize;
  int segmentIdentifier;

  if (getScreenSegment(&segmentIdentifier, key)) {
    destroyScreenSegment(segmentIdentifier);
  }

  if ((segmentIdentifier = shmget(key, segmentSize, ipcCreationFlags)) != -1) {
    ScreenSegmentHeader *segment = attachScreenSegment(segmentIdentifier);

    if (segment) {
      uint32_t nextOffset = 0;

      segment->segmentSize = segmentSize;
      segment->headerSize = sizeof(*segment);
      nextOffset += segment->headerSize;

      segment->screenHeight = rows;
      segment->screenWidth = columns;

      segment->cursorRow = 0;
      segment->cursorColumn = 0;

      segment->screenNumber = 0;
      segment->commonFlags = 0;
      segment->privateFlags = 0;

      if (rowsSize) {
        segment->rowSize = sizeof(ScreenSegmentRow);
        segment->rowsOffset = nextOffset;
        nextOffset += rowsSize;
      } else {
        segment->rowSize = 0;
        segment->rowsOffset = 0;
      }

      segment->characterSize = sizeof(ScreenSegmentCharacter);
      segment->charactersOffset = nextOffset;
      nextOffset += charactersSize;

      if (haveScreenRowArray(segment)) {
        /* Rows are initially sequential. */

        ScreenSegmentRow *row = getScreenRowArray(segment);
        ScreenSegmentRow *end = row + rows;

        uint32_t offset = segment->charactersOffset;
        uint32_t increment = getScreenRowWidth(segment);

        while (row < end) {
          row->charactersOffset = offset;
          offset += increment;
          row += 1;
        }
      }

      {
        const ScreenSegmentCharacter *to;
        ScreenSegmentCharacter *from = getScreenCharacterArray(segment, &to);
        initializeScreenCharacters(from, to);
      }

      if (identifier) *identifier = segmentIdentifier;
      return segment;
    }

    destroyScreenSegment(segmentIdentifier);
  } else {
    logSystemError("shmget");
  }

  return NULL;
}

int
destroyMessageQueue (int queue) {
  if (msgctl(queue, IPC_RMID, NULL) != -1) return 1;
  logSystemError("msgctl[IPC_RMID]");
  return 0;
}

int
createMessageQueue (int *queue, key_t key) {
  int q;

  if (getMessageQueue(&q, key)) {
    destroyMessageQueue(q);
  }

  if ((q = msgget(key, ipcCreationFlags)) != -1) {
    if (queue) *queue = q;
    return 1;
  } else {
    logSystemError("msgget");
  }

  return 0;
}
