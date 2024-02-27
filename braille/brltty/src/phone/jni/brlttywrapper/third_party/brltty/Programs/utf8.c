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

#include "log.h"
#include "utf8.h"
#include "unicode.h"

wchar_t *
allocateCharacters (size_t count) {
  {
    wchar_t *characters = malloc(count * sizeof(*characters));
    if (characters) return characters;
  }

  logMallocError();
  return NULL;
}

size_t
convertCodepointToUtf8 (uint32_t codepoint, Utf8Buffer utf8) {
  size_t length = 0;

  if (!(codepoint & ~0X7F)) {
    utf8[length++] = codepoint;
  } else {
    char *end = &utf8[2];

    {
      uint32_t value = codepoint;
      uint32_t mask = ~((1 << 11) - 1);

      while ((value &= mask)) {
        mask <<= 5;
        end += 1;
      }

      length = end - utf8;
    }

    {
      uint32_t value = codepoint;

      do {
        *--end = (value & 0X3F) | 0X80;
        value >>= 6;
      } while (end > utf8);
    }

    *end |= ~((1 << (8 - length)) - 1);
  }

  utf8[length] = 0;
  return length;
}

int
convertUtf8ToCodepoint (uint32_t *codepoint, const char **utf8, size_t *utfs) {
  int ok = 0;
  uint32_t cp = 0;

  int first = 1;
  int state = 0;

  while (*utfs) {
    unsigned char byte = *(*utf8)++;
    *utfs -= 1;

    if (!(byte & 0X80)) {
      if (!first) goto unexpected;
      cp = byte;
      ok = 1;
      break;
    }

    if (!(byte & 0X40)) {
      if (first) break;
      cp = (cp << 6) | (byte & 0X3F);

      if (!--state) {
        ok = 1;
        break;
      }
    } else {
      if (!first) goto unexpected;

      state = 1;
      uint8_t bit = 0X20;

      while (byte & bit) {
        if (!(bit >>= 1)) break;
        state += 1;
      }

      cp = byte & ((1 << (6 - state)) - 1);
    }

    first = 0;
  }

  while (*utfs) {
    if ((**utf8 & 0XC0) != 0X80) break;
    ok = 0;

    *utf8 += 1;
    *utfs -= 1;
  }

  if (!ok) goto error;
  *codepoint = cp;
  return 1;

unexpected:
  *utf8 -= 1;
  *utfs += 1;
error:
  return 0;
}

size_t
convertWcharToUtf8 (wchar_t character, Utf8Buffer utf8) {
  return convertCodepointToUtf8(character, utf8);
}

wint_t
convertUtf8ToWchar (const char **utf8, size_t *utfs) {
  uint32_t codepoint;
  int ok = convertUtf8ToCodepoint(&codepoint, utf8, utfs);
  if (!ok) return WEOF;

  if (codepoint > WCHAR_MAX) codepoint = UNICODE_REPLACEMENT_CHARACTER;
  return codepoint;
}

void
convertUtf8ToWchars (const char **utf8, wchar_t **characters, size_t count) {
  while (**utf8 && (count > 1)) {
    size_t utfs = UTF8_LEN_MAX;
    wint_t character = convertUtf8ToWchar(utf8, &utfs);

    if (character == WEOF) break;
    *(*characters)++ = character;
    count -= 1;
  }

  if (count) **characters = 0;
}

size_t
makeUtf8FromWchars (const wchar_t *characters, unsigned int count, char *buffer, size_t size) {
  char *byte = buffer;
  const char *end = byte + size;

  for (unsigned int i=0; i<count; i+=1) {
    Utf8Buffer utf8;
    size_t utfs = convertWcharToUtf8(characters[i], utf8);

    char *next = byte + utfs;
    if (next >= end) break;

    memcpy(byte, utf8, utfs);
    byte = next;
  }

  *byte = 0;
  return byte - buffer;
}

char *
getUtf8FromWchars (const wchar_t *characters, unsigned int count, size_t *length) {
  size_t size = (count * UTF8_LEN_MAX) + 1;
  char buffer[size];
  size_t len = makeUtf8FromWchars(characters, count, buffer, size);
  char *text = strdup(buffer);

  if (!text) {
    logMallocError();
  } else if (length) {
    *length = len;
  }

  return text;
}

size_t
makeWcharsFromUtf8 (const char *text, wchar_t *characters, size_t size) {
  size_t length = strlen(text);
  size_t count = 0;

  while (length > 0) {
    const char *utf8 = text;
    size_t utfs = length;
    wint_t character = convertUtf8ToWchar(&utf8, &utfs);

    if (character == WEOF) break;
    if (!character) break;

    if (characters) {
      if (count == size) break;
      characters[count] = character;
    }

    count += 1;
    text = utf8;
    length = utfs;
  }

  if (characters && (count < size)) characters[count] = 0;
  return count;
}

size_t
countUtf8Characters (const char *text) {
  return makeWcharsFromUtf8(text, NULL, 0);
}

int
writeUtf8Character (FILE *stream, wchar_t character) {
  Utf8Buffer utf8;
  size_t utfs = convertWcharToUtf8(character, utf8);

  if (utfs) {
    if (fwrite(utf8, 1, utfs, stream) == utfs) {
      return 1;
    } else {
      logSystemError("fwrite");
    }
  } else {
    logBytes(LOG_ERR, "invalid Unicode character", &character, sizeof(character));
  }

  return 0;
}

int
writeUtf8Characters (FILE *stream, const wchar_t *characters, size_t count) {
  const wchar_t *character = characters;
  const wchar_t *end = character + count;

  while (character < end) {
    if (!writeUtf8Character(stream, *character++)) {
      return 0;
    }
  }

  return 1;
}

int
writeUtf8ByteOrderMark (FILE *stream) {
#if UNICODE_BYTE_ORDER_MARK <= WCHAR_MAX
  if (!writeUtf8Character(stream, UNICODE_BYTE_ORDER_MARK)) {
    return 0;
  }
#endif /* UNICODE_BYTE_ORDER_MARK <= WCHAR_MAX */

  return 1;
}

int
isCharsetUTF8 (const char *name) {
  {
    const char *substring = "utf";
    size_t length = strlen(substring);
    if (strncasecmp(name, substring, length) != 0) return 0;
    name += length;
    if (*name == '-') name += 1;
  }

  return strcmp(name, "8") == 0;
}
