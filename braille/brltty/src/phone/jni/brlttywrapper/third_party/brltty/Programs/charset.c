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

#include "log.h"
#include "charset_internal.h"
#include "unicode.h"
#include "lock.h"
#include "program.h"

#if defined(__MINGW32__)
#include "system_windows.h"

#elif defined(__ANDROID__)
#include "system_java.h"

#else /* unix */
#include <locale.h>
#endif /* locale definitions */

const char defaultCharset[] = "ISO-8859-1";

static char *currentCharset = NULL;

char *
getLocaleName (void) {
#if defined(__MINGW32__)
  return getWindowsLocaleName();

#elif defined(__ANDROID__)
  return getJavaLocaleName();

#else /* unix */
  char *name = setlocale(LC_CTYPE, NULL);

  if (name) {
    if (!(name = strdup(name))) {
      logMallocError();
    }
  }

  return name;
#endif /* text table locale */
}

int
isPosixLocale (const char *locale) {
  if (strcmp(locale, "C") == 0) return 1;
  if (strcmp(locale, "POSIX") == 0) return 1;
  return 0;
}

size_t
convertTextToWchars (wchar_t *characters, const char *text, size_t size) {
  size_t length = strlen(text);
  size_t count = 0;
  mbstate_t state;
  memset(&state, 0, sizeof(state));

  while (length > 0) {
    wchar_t character;
    ssize_t consumed;

#if defined(__ANDROID__)
    const char *utf8 = text;
    size_t utfs = length;
    wint_t wc = convertUtf8ToWchar(&utf8, &utfs);

    if (!wc) break;
    character = wc;
    consumed = utf8 - text;
#elif defined(HAVE_WCHAR_H)
    consumed = mbrtowc(&character, text, length, &state);
    if (consumed < 0) consumed = 0;
#else /* no conversion */
    character = *text & 0XFF;
    consumed = 1;
#endif /* HAVE_WCHAR_H */

    if (!consumed) break;
    if (!character) break;

    if (characters) {
      if (count == size) break;
      characters[count] = character;
    }

    text += consumed;
    length -= consumed;
    count += 1;
  }

  if (characters && (count < size)) characters[count] = 0;
  return count;
}

size_t
getTextLength (const char *text) {
  return convertTextToWchars(NULL, text, 0);
}

size_t
convertWcharToUtf8 (wchar_t wc, Utf8Buffer utf8) {
  size_t utfs;

  if (!(wc & ~0X7F)) {
    *utf8 = wc;
    utfs = 1;
  } else {
    Utf8Buffer buffer;
    char *end = &buffer[0] + sizeof(buffer);
    char *byte = end;
    static const wchar_t mask = (1 << ((sizeof(wchar_t) * 8) - 6)) - 1;

    do {
      *--byte = (wc & 0X3F) | 0X80;
    } while ((wc = (wc >> 6) & mask));

    utfs = end - byte;
    if ((*byte & 0X7F) >= (1 << (7 - utfs))) {
      *--byte = 0;
      utfs++;
    }

    *byte |= ~((1 << (8 - utfs)) - 1);
    memcpy(utf8, byte, utfs);
  }

  utf8[utfs] = 0;
  return utfs;
}

wint_t
convertUtf8ToWchar (const char **utf8, size_t *utfs) {
  uint32_t character = UINT32_MAX;
  int state = 0;

  while (*utfs) {
    unsigned char byte = *(*utf8)++;
    (*utfs)--;

    if (!(byte & 0X80)) {
      if (character != UINT32_MAX) goto truncated;
      character = byte;
      break;
    }

    if (!(byte & 0X40)) {
      if (character == UINT32_MAX) break;
      character = (character << 6) | (byte & 0X3F);
      if (!--state) break;
    } else {
      if (!(byte & 0X20)) {
        state = 1;
      } else if (!(byte & 0X10)) {
        state = 2;
      } else if (!(byte & 0X08)) {
        state = 3;
      } else if (!(byte & 0X04)) {
        state = 4;
      } else if (!(byte & 0X02)) {
        state = 5;
      } else {
        state = 0;
      }

      if (character != UINT32_MAX) goto truncated;

      if (!state) {
        character = UINT32_MAX;
        break;
      }

      character = byte & ((1 << (6 - state)) - 1);
    }
  }

  while (*utfs) {
    if ((**utf8 & 0XC0) != 0X80) break;
    (*utf8)++, (*utfs)--;
    character = UINT32_MAX;
  }

  if (character == UINT32_MAX) goto error;
  if (character > WCHAR_MAX) character = UNICODE_REPLACEMENT_CHARACTER;
  return character;

truncated:
  (*utf8)--, (*utfs)++;
error:
  return WEOF;
}

size_t
getUtf8Length (const char *utf8) {
  size_t length = 0;

  while (*utf8) {
    size_t utfs = UTF8_LEN_MAX;
    wint_t character = convertUtf8ToWchar(&utf8, &utfs);

    if (character == WEOF) break;
    length += 1;
  }

  return length;
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

char *
makeUtf8FromWchars (const wchar_t *characters, unsigned int count, size_t *length) {
  char *text = malloc((count * UTF8_LEN_MAX) + 1);

  if (text) {
    char *t = text;
    unsigned int i;

    for (i=0; i<count; i+=1) {
      Utf8Buffer utf8;
      size_t utfs = convertWcharToUtf8(characters[i], utf8);

      if (utfs) {
        t = mempcpy(t, utf8, utfs);
      } else {
        *t++ = ' ';
      }
    }

    *t = 0;
    if (length) *length = t - text;
    return text;
  } else {
    logMallocError();
  }

  return NULL;
}

size_t
convertCharToUtf8 (char c, Utf8Buffer utf8) {
  wint_t wc = convertCharToWchar(c);
  if (wc == WEOF) return 0;
  return convertWcharToUtf8(wc, utf8);
}

int
convertUtf8ToChar (const char **utf8, size_t *utfs) {
  wint_t wc = convertUtf8ToWchar(utf8, utfs);
  if (wc == WEOF) return EOF;
  return convertWcharToChar(wc);
}

const char *
getWcharCharset (void) {
  static const char *wcharCharset = NULL;

  if (!wcharCharset) {
    char charset[0X10];

    snprintf(charset, sizeof(charset), "UCS-%lu%cE",
             (unsigned long)sizeof(wchar_t),
#ifdef WORDS_BIGENDIAN
             'B'
#else /* WORDS_BIGENDIAN */
             'L'
#endif /* WORDS_BIGENDIAN */
            );

    if ((wcharCharset = strdup(charset))) {
      registerProgramMemory("wchar-charset", &wcharCharset);
    } else {
      logMallocError();
    }
  }

  return wcharCharset;
}

const char *
setCharset (const char *name) {
  char *charset;

  if (name) {
    if (currentCharset && (strcmp(currentCharset, name) == 0)) return currentCharset;
  } else if (currentCharset) {
    return currentCharset;
  } else {
    name = getLocaleCharset();
  }

  if (!(charset = strdup(name))) {
    logMallocError();
    return NULL;
  }

  if (!registerCharacterSet(charset)) {
    free(charset);
    return NULL;
  }

  if (currentCharset) {
    free(currentCharset);
  } else {
    registerProgramMemory("current-charset", &currentCharset);
  }

  return currentCharset = charset;
}

const char *
getCharset (void) {
  return setCharset(NULL);
}

static LockDescriptor *
getCharsetLock (void) {
  static LockDescriptor *lock = NULL;

  return getLockDescriptor(&lock, "charset");
}

int
lockCharset (LockOptions options) {
  LockDescriptor *lock = getCharsetLock();
  if (!lock) return 0;
  return obtainLock(lock, options);
}

void
unlockCharset (void) {
  LockDescriptor *lock = getCharsetLock();
  if (lock) releaseLock(lock);
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
