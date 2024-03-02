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
#include <errno.h>
#include <locale.h>
#include <fcntl.h>
#include <sys/stat.h>

#include "log.h"
#include "messages.h"
#include "file.h"

// MinGW doesn't define LC_MESSAGES
#ifndef LC_MESSAGES
#define LC_MESSAGES LC_ALL
#endif /* LC_MESSAGES */

// Windows needs O_BINARY
#ifndef O_BINARY
#define O_BINARY 0
#endif /* O_BINARY */

static char *localeDirectory = NULL;
static char *localeSpecifier = NULL;
static char *domainName = NULL;

const char *
getMessagesDirectory (void) {
  return localeDirectory;
}

const char *
getMessagesLocale (void) {
  return localeSpecifier;
}

const char *
getMessagesDomain (void) {
  return domainName;
}

static const uint32_t magicNumber = UINT32_C(0X950412DE);
typedef uint32_t GetIntegerFunction (uint32_t value);

typedef struct {
  uint32_t magicNumber;
  uint32_t versionNumber;
  uint32_t messageCount;
  uint32_t sourceMessages;
  uint32_t translatedMessages;
  uint32_t hashSize;
  uint32_t hashOffset;
} MessageCatalogHeader;

typedef struct {
  union {
    void *data;
    const unsigned char *bytes;
    const MessageCatalogHeader *header;
  } view;

  size_t dataSize;
  GetIntegerFunction *getInteger;
} MessageCatalog;

static MessageCatalog messageCatalog = {
  .view.data = NULL,
  .dataSize = 0,
  .getInteger = NULL,
};

static uint32_t
getNativeInteger (uint32_t value) {
  return value;
}

static uint32_t
getFlippedInteger (uint32_t value) {
  uint32_t result = 0;

  while (value) {
    result <<= 8;
    result |= value & UINT8_MAX;
    value >>= 8;
  }

  return result;
}

static int
checkMagicNumber (MessageCatalog *catalog) {
  const MessageCatalogHeader *header = catalog->view.header;

  {
    static GetIntegerFunction *const functions[] = {
      getNativeInteger,
      getFlippedInteger,
      NULL
    };

    GetIntegerFunction *const *function = functions;

    while (*function) {
      if ((*function)(header->magicNumber) == magicNumber) {
        catalog->getInteger = *function;
        return 1;
      }

      function += 1;
    }
  }

  return 0;
}

static char *
makeLocaleDirectoryPath (void) {
  size_t length = strlen(localeSpecifier);

  char dialect[length + 1];
  strcpy(dialect, localeSpecifier);
  length = strcspn(dialect, ".@");
  dialect[length] = 0;

  char language[length + 1];
  strcpy(language, dialect);
  length = strcspn(language, "_");
  language[length] = 0;

  char *codes[] = {dialect, language, NULL};
  char **code = codes;

  while (*code && **code) {
    char *path = makePath(localeDirectory, *code);

    if (path) {
      if (testDirectoryPath(path)) return path;
      free(path);
    }

    code += 1;
  }

  logMessage(LOG_DEBUG, "messages locale not found: %s", localeSpecifier);
  return NULL;
}

static char *
makeCatalogFilePath (void) {
  char *locale = makeLocaleDirectoryPath();

  if (locale) {
    char *category = makePath(locale, "LC_MESSAGES");

    free(locale);
    locale = NULL;

    if (category) {
      char *catalog = makeFilePath(category, domainName, ".mo");

      free(category);
      category = NULL;

      if (catalog) return catalog;
    }
  }

  return NULL;
}

static int
setMessageCatalog (void *data, size_t size) {
  MessageCatalog catalog = {
    .view.data = data,
    .dataSize = size
  };

  if (checkMagicNumber(&catalog)) {
    messageCatalog = catalog;
    return 1;
  }

  return 0;
}

static int
setEmptyMessageCatalog (void) {
  MessageCatalogHeader *header;
  size_t size = sizeof(*header);

  if ((header = malloc(size))) {
    memset(header, 0, size);
    header->magicNumber = magicNumber;

    header->sourceMessages = size;
    header->translatedMessages = header->sourceMessages;

    if (setMessageCatalog(header, size)) return 1;
    free(header);
  } else {
    logMallocError();
  }

  return 0;
}

int
loadMessageCatalog (void) {
  if (messageCatalog.view.data) return 1;
  ensureAllMessagesProperties();

  int loaded = 0;
  char *path = makeCatalogFilePath();

  if (path) {
    int fd = open(path, (O_RDONLY | O_BINARY));

    if (fd != -1) {
      struct stat info;

      if (fstat(fd, &info) != -1) {
        size_t size = info.st_size;
        void *data = NULL;

        if (size) {
          if ((data = malloc(size))) {
            ssize_t count = read(fd, data, size);

            if (count == -1) {
              logMessage(LOG_WARNING,
                "message catalog read error: %s: %s",
                path, strerror(errno)
              );
            } else if (count < size) {
              logMessage(LOG_WARNING,
                "truncated message catalog: %"PRIssize" < %"PRIsize": %s",
                count, size, path
              );
            } else if (setMessageCatalog(data, size)) {
              data = NULL;
              loaded = 1;
            }

            if (!loaded) free(data);
          } else {
            logMallocError();
          }
        } else {
          logMessage(LOG_WARNING, "empty message catalog");
        }
      } else {
        logMessage(LOG_WARNING,
          "message catalog stat error: %s: %s",
          path, strerror(errno)
        );
      }

      close(fd);
    } else {
      logMessage(LOG_WARNING,
        "message catalog open error: %s: %s",
        path, strerror(errno)
      );
    }

    free(path);
  }

  if (!loaded) {
    if (setEmptyMessageCatalog()) {
      loaded = 1;
      logMessage(LOG_DEBUG, "no message translations");
    }
  }

  return loaded;
}

void
releaseMessageCatalog (void) {
  if (messageCatalog.view.data) free(messageCatalog.view.data);
  memset(&messageCatalog, 0, sizeof(messageCatalog));
}

static inline const MessageCatalogHeader *
getHeader (void) {
  return messageCatalog.view.header;
}

static inline const void *
getItem (uint32_t offset) {
  return &messageCatalog.view.bytes[messageCatalog.getInteger(offset)];
}

uint32_t
getMessageCount (void) {
  return messageCatalog.getInteger(getHeader()->messageCount);
}

struct MessageStruct {
  uint32_t length;
  uint32_t offset;
};

uint32_t
getMessageLength (const Message *message) {
  return messageCatalog.getInteger(message->length);
}

const char *
getMessageText (const Message *message) {
  return getItem(message->offset);
}

static inline const Message *
getSourceMessages (void) {
  return getItem(getHeader()->sourceMessages);
}

static inline const Message *
getTranslatedMessages (void) {
  return getItem(getHeader()->translatedMessages);
}

const Message *
getSourceMessage (unsigned int index) {
  return &getSourceMessages()[index];
}

const Message *
getTranslatedMessage (unsigned int index) {
  return &getTranslatedMessages()[index];
}

const char *
getMessagesMetadata (void) {
  if (getMessageCount() == 0) return "";

  {
    const Message *message = getSourceMessage(0);
    if (getMessageLength(message) != 0) return "";
  }

  return getMessageText(getTranslatedMessage(0));
}

char *
getMessagesProperty (const char *name) {
  size_t nameLength = strlen(name);
  const char *metadata = getMessagesMetadata();

  while (metadata) {
    const char *line = metadata;
    size_t lineLength = strcspn(line, "\n\x00");

    const char *end = line + lineLength;
    metadata = *end? (line + lineLength + 1): NULL;

    if (nameLength < lineLength) {
      if (memcmp(line, name, nameLength) == 0) {
        if (line[nameLength] == ':') {
          const char *value = line + nameLength + 1;
          while (iswspace(*value)) value += 1;

          size_t valueLength = end - value;
          char *copy = malloc(valueLength + 1);

          if (copy) {
            memcpy(copy, value, valueLength);
            copy[valueLength] = 0;
          } else {
            logMallocError();
          }

          return copy;
        }
      }
    }
  }

  return NULL;
}

char *
getMessagesAttribute (const char *property, const char *name) {
  size_t nameLength = strlen(name);
  const char *byte = property;

  while (*byte) {
    while (iswspace(*byte)) byte += 1;
    const char *nameStart = byte;

    while (iswalpha(*byte)) byte += 1;
    const char *nameEnd = byte;

    while (iswspace(*byte)) byte += 1;
    if (!*byte) break;

    if (*byte != '=') continue;
    byte += 1;

    while (iswspace(*byte)) byte += 1;
    const char *valueStart = byte;

    while (*byte && (*byte != ';')) byte += 1;
    const char *valueEnd = byte;
    if (*byte) byte += 1;

    if ((nameEnd - nameStart) == nameLength) {
      if (memcmp(name, nameStart, nameLength) == 0) {
        size_t length = valueEnd - valueStart;
        char *value = malloc(length + 1);

        if (value) {
          memcpy(value, valueStart, length);
          value[length] = 0;
        } else {
          logMallocError();
        }

        return value;
      }
    }
  }

  return NULL;
}

int
findSourceMessage (const char *text, size_t textLength, unsigned int *index) {
  const Message *messages = getSourceMessages();
  int from = 0;
  int to = getMessageCount();

  while (from < to) {
    int current = (from + to) / 2;
    const Message *message = &messages[current];

    uint32_t messageLength = getMessageLength(message);
    int relation = memcmp(text, getMessageText(message), MIN(textLength, messageLength));

    if (relation == 0) {
      if (textLength == messageLength) {
        *index = current;
        return 1;
      }

      relation = (textLength < messageLength)? -1: 1;
    }

    if (relation < 0) {
      to = current;
    } else {
      from = current + 1;
    }
  }

  return 0;
}

const Message *
findSimpleTranslation (const char *text, size_t length) {
  if (!text) return NULL;
  if (!length) return NULL;

  if (loadMessageCatalog()) {
    unsigned int index;

    if (findSourceMessage(text, length, &index)) {
      return getTranslatedMessage(index);
    }
  }

  return NULL;
}

const char *
getSimpleTranslation (const char *text) {
  const Message *translation = findSimpleTranslation(text, strlen(text));
  if (translation) return getMessageText(translation);
  return text;
}

const Message *
findPluralTranslation (const char *const *strings) {
  unsigned int count = 0;
  while (strings[count]) count += 1;
  if (!count) return NULL;

  size_t size = 0;
  size_t lengths[count];

  for (unsigned int index=0; index<count; index+=1) {
    size_t length = strlen(strings[index]);
    lengths[index] = length;
    size += length + 1;
  }

  char text[size];
  char *byte = text;

  for (unsigned int index=0; index<count; index+=1) {
    byte = mempcpy(byte, strings[index], (lengths[index] + 1));
  }

  byte -= 1; // the length mustn't include the final NUL
  return findSimpleTranslation(text, (byte - text));
}

const char *
getPluralTranslation (const char *singular, const char *plural, unsigned long int count) {
  int useSingular = count == 1;

  const char *const strings[] = {singular, plural, NULL};
  const Message *message = findPluralTranslation(strings);
  if (!message) return useSingular? singular: plural;

  const char *translation = getMessageText(message);
  if (!useSingular) translation += strlen(translation) + 1;
  return translation;
}

#ifdef ENABLE_I18N_SUPPORT
static int
setDirectory (const char *directory) {
  if (bindtextdomain(domainName, directory)) return 1;
  logSystemError("bindtextdomain");
  return 0;
}

static int
setDomain (const char *domain) {
  if (!textdomain(domain)) {
    logSystemError("textdomain");
    return 0;
  }

  if (!bind_textdomain_codeset(domain, "UTF-8")) {
    logSystemError("bind_textdomain_codeset");
  }

  return 1;
}
#else /* ENABLE_I18N_SUPPORT */
static int
setDirectory (const char *directory) {
  return 1;
}

static int
setDomain (const char *domain) {
  return 1;
}

char *
gettext (const char *text) {
  return (char *)getSimpleTranslation(text);
}

char *
ngettext (const char *singular, const char *plural, unsigned long int count) {
  return (char *)getPluralTranslation(singular, plural, count);
}
#endif /* ENABLE_I18N_SUPPORT */

static int
updateProperty (
  char **property, const char *value, const char *defaultValue,
  int (*setter) (const char *value)
) {
  releaseMessageCatalog();

  if (!(value && *value)) value = defaultValue;
  char *copy = strdup(value);

  if (copy) {
    if (!setter || setter(value)) {
      if (*property) free(*property);
      *property = copy;
      return 1;
    }

    free(copy);
  } else {
    logMallocError();
  }

  return 0;
}

int
setMessagesDirectory (const char *directory) {
  return updateProperty(&localeDirectory, directory, LOCALE_DIRECTORY, setDirectory);
}

int
setMessagesLocale (const char *specifier) {
  return updateProperty(&localeSpecifier, specifier, "C.UTF-8", NULL);
}

int
setMessagesDomain (const char *name) {
  return updateProperty(&domainName, name, PACKAGE_TARNAME, setDomain);
}

void
ensureAllMessagesProperties (void) {
  if (!localeSpecifier) {
    setMessagesLocale(setlocale(LC_MESSAGES, ""));
  }

  if (!domainName) setMessagesDomain(NULL);
  if (!localeDirectory) setMessagesDirectory(NULL);
}
