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

#ifndef BRLTTY_INCLUDED_SCR_TERMINAL
#define BRLTTY_INCLUDED_SCR_TERMINAL

#include <sys/ipc.h>

#ifdef __cplusplus
extern "C" {
#endif /* __cplusplus */

extern int makeTerminalKey(key_t *key, const char *path);

typedef enum {
  TERM_MSG_INPUT_TEXT = 't',        // driver->emulator - UTF-8
  TERM_MSG_SEGMENT_UPDATED = 'u',   // emulator->driver - no content
  TERM_MSG_EMULATOR_EXITING = 'x',  // emulator->driver - no content
} TerminalMessageType;

extern int getMessageQueue(int *queue, key_t key);

typedef struct {
  uint8_t red;
  uint8_t green;
  uint8_t blue;
} ScreenSegmentColor;

typedef struct {
  uint32_t text;

  ScreenSegmentColor foreground;
  ScreenSegmentColor background;
  uint8_t alpha;

  unsigned char blink : 1;
  unsigned char underline : 1;
} ScreenSegmentCharacter;

typedef struct {
  uint32_t charactersOffset;
} ScreenSegmentRow;

typedef struct {
  uint32_t headerSize;
  uint32_t segmentSize;

  uint32_t screenHeight;
  uint32_t screenWidth;

  uint32_t cursorRow;
  uint32_t cursorColumn;

  uint32_t screenNumber;
  uint32_t commonFlags;
  uint32_t privateFlags;

  uint32_t rowsOffset;
  uint32_t rowSize;

  uint32_t charactersOffset;
  uint32_t characterSize;
} ScreenSegmentHeader;

extern int getScreenSegment(int *identifier, key_t key);
extern ScreenSegmentHeader *attachScreenSegment(int identifier);
extern int detachScreenSegment(ScreenSegmentHeader *segment);

extern ScreenSegmentHeader *getScreenSegmentForKey(key_t key);
extern ScreenSegmentHeader *getScreenSegmentForPath(const char *path);
extern void logScreenSegment(const ScreenSegmentHeader *segment);

static inline int haveScreenRowArray(const ScreenSegmentHeader *segment) {
  return !!segment->rowsOffset;
}

static inline unsigned int getScreenRowWidth(
    const ScreenSegmentHeader *segment) {
  return segment->screenWidth * segment->characterSize;
}

static inline unsigned int getScreenCharacterCount(
    const ScreenSegmentHeader *segment) {
  return segment->screenWidth * segment->screenHeight;
}

extern void *getScreenItem(ScreenSegmentHeader *segment, uint32_t offset);
extern ScreenSegmentRow *getScreenRowArray(ScreenSegmentHeader *segment);
extern ScreenSegmentCharacter *getScreenCharacterArray(
    ScreenSegmentHeader *segment, const ScreenSegmentCharacter **end);

extern ScreenSegmentCharacter *getScreenRow(ScreenSegmentHeader *segment,
                                            unsigned int row,
                                            const ScreenSegmentCharacter **end);
extern ScreenSegmentCharacter *getScreenCharacter(
    ScreenSegmentHeader *segment, unsigned int row, unsigned int column,
    const ScreenSegmentCharacter **end);

#ifdef __cplusplus
}
#endif /* __cplusplus */

#endif /* BRLTTY_INCLUDED_SCR_TERMINAL */
