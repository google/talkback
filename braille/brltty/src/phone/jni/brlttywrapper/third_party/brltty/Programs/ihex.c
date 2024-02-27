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

#include "log.h"
#include "strfmt.h"
#include "ihex.h"
#include "file.h"
#include "datafile.h"

#define IHEX_PARSE_VERIFY 0

#define IHEX_RECORD_PREFIX  ':'
#define IHEX_COMMENT_PREFIX '#'

#define IHEX_BYTE_WIDTH 8
#define IHEX_BYTE_MASK ((1 << IHEX_BYTE_WIDTH) - 1)

static size_t
ihexByteCount (size_t count) {
  return 1 // the number of data bytes
       + 2 // the starting address
       + 1 // the record type
       + count // the data
       + 1 // the checksum
       ;
}

size_t
ihexRecordLength (size_t count) {
  return 1 // the colon prefix
       + (ihexByteCount(count) * 2) // hexadecimal digit pairs
       ;
}

int
ihexMakeRecord (char *buffer, size_t size, IhexType type, IhexAddress address, const IhexByte *data, IhexCount count) {
  IhexByte bytes[ihexByteCount(count)];
  IhexByte *end = bytes;

  *end++ = count;
  *end++ = (address >> IHEX_BYTE_WIDTH) & IHEX_BYTE_MASK;
  *end++ = address & IHEX_BYTE_MASK;
  *end++ = type;
  if (count > 0) end = mempcpy(end, data, count);

  {
    uint32_t checksum = 0;

    {
      const IhexByte *byte = bytes;
      while (byte < end) checksum += *byte++;
    }

    checksum ^= IHEX_BYTE_MASK;
    checksum += 1;
    *end++ = checksum & IHEX_BYTE_MASK;
  }

  if ((1 + (end - bytes) + 1) > size) return 0;
  STR_BEGIN(buffer, size);
  STR_PRINTF("%c", IHEX_RECORD_PREFIX);

  {
    const IhexByte *byte = bytes;
    while (byte < end) STR_PRINTF("%02X", *byte++);
  }

  STR_END;
  return 1;
}

int
ihexMakeDataRecord (char *buffer, size_t size, IhexAddress address, const IhexByte *data, IhexCount count) {
  return ihexMakeRecord(buffer, size, IHEX_TYPE_DATA, address, data, count);
}

int
ihexMakeEndRecord (char *buffer, size_t size) {
  return ihexMakeRecord(buffer, size, IHEX_TYPE_END, 0, NULL, 0);
}

typedef struct {
  const char *record;
  const char *source;
  unsigned int line;

  unsigned char error:1;
} IhexRecordProcessingData;

typedef struct {
  IhexRecordProcessingData rpd;
  IhexRecordHandler *handler;
  void *data;
} IhexFileProcessingData;

static void
ihexReportProblem (IhexRecordProcessingData *rpd, const char *message) {
  rpd->error = 1;

  logMessage(LOG_ERR,
    "ihex error: %s: %s[%u]: %s",
    message, rpd->source, rpd->line, rpd->record
  );
}

static int
ihexCheckDigit (IhexRecordProcessingData *rpd, unsigned char *value, char digit) {
  typedef struct {
    char first;
    char last;
    char offset;
  } Range;

  static const Range ranges[] = {
    { .first='0', .last='9', .offset= 0 },
    { .first='A', .last='F', .offset=10 },
    { .first='a', .last='f', .offset=10 },
  };

  const Range *range = ranges;
  const Range *end = range + ARRAY_COUNT(ranges);

  while (range < end) {
    if ((digit >= range->first) && (digit <= range->last)) {
      *value = (digit - range->first) + range->offset;
      return 1;
    }

    range += 1;
  }

  ihexReportProblem(rpd, "invalid hexadecimal digit");
  return 0;
}

static IhexParsedRecord *
ihexParseRecord (IhexRecordProcessingData *rpd) {
  const char *character = rpd->record;

  if (!*character || (*character != IHEX_RECORD_PREFIX)) {
    ihexReportProblem(rpd, "not an ihex record");
    return NULL;
  }

  size_t length = strlen(++character);
  IhexByte bytes[length + 1]; // +1 in case length is 0
  IhexByte *end = bytes;
  int first = 1;

  while (*character) {
    unsigned char value;
    if (!ihexCheckDigit(rpd, &value, *character)) return NULL;

    if (first) {
      *end = value << 4;
    } else {
      *end++ |= value;
    }

    first = !first;
    character += 1;
  }

  if (!first) {
    ihexReportProblem(rpd, "missing hexadecimal digit");
    return NULL;
  }

  {
    uint32_t checksum = 0;
    const IhexByte *byte = bytes;
    while (byte < end) checksum += *byte++;
    checksum &= IHEX_BYTE_MASK;

    if (checksum) {
      ihexReportProblem(rpd, "checksum mismatch");
      return NULL;
    }
  }

  const IhexByte *byte = bytes;
  size_t actualCount = end - byte;

  {
    static const char *const messages[] = {
      [0] = "missing data byte count",
      [1] = "missing address",
      [2] = "incomplete address",
      [3] = "missing record type",
    };

    if (actualCount < ARRAY_COUNT(messages)) {
      const char *message = messages[actualCount];
      if (!message) message = "unknown error";

      ihexReportProblem(rpd, message);
      return NULL;
    }
  }

  IhexCount count = *byte++;
  size_t expectCount = ihexByteCount(count);

  if (actualCount < expectCount) {
    ihexReportProblem(rpd, "truncated data");
    return NULL;
  }

  if (actualCount > expectCount) {
    ihexReportProblem(rpd, "excessive data");
    return NULL;
  }

  IhexParsedRecord *record;
  size_t size = sizeof(*record) + count;
  record = malloc(size);

  if (!record) {
    logMallocError();
    return NULL;
  }

  memset(record, 0, size);
  record->count = count;
  record->address = *byte++ << IHEX_BYTE_WIDTH;
  record->address |= *byte++;
  record->type = *byte++;
  memcpy(record->data, byte, count);

  if (IHEX_PARSE_VERIFY) {
    const char *expect = rpd->record;
    char actual[ihexRecordLength(record->count) + 1];

    ihexMakeRecord(
      actual, sizeof(actual),
      record->type, record->address,
      record->data, record->count
    );

    if (strcmp(actual, expect) != 0) {
      ihexReportProblem(rpd, "ihex parse mismatch");
      logMessage(LOG_DEBUG, "expect: %s", expect);
      logMessage(LOG_DEBUG, "actual: %s", actual);

      free(record);
      return NULL;
    }
  }

  return record;
}

static int
ihexCallHandler (IhexFileProcessingData *fpd, const IhexParsedRecord *record) {
  IhexRecordProcessingData *rpd = &fpd->rpd;

  switch (record->type) {
    case IHEX_TYPE_DATA:
      if (!record->count) return 0;
      break;

    case IHEX_TYPE_END:
      return 0;

    default:
      ihexReportProblem(rpd, "unsupported record type");
      return 0;
  }

  if (!fpd->handler(record, fpd->data)) {
    ihexReportProblem(rpd, "record handler failed");
    return 0;
  }

  return 1;
}

static int
ihexProcessLine (const LineHandlerParameters *parameters) {
  IhexFileProcessingData *fpd = parameters->data;
  IhexRecordProcessingData *rpd = &fpd->rpd;
  rpd->line += 1;

  const char *line = parameters->line.text;
  while (*line == ' ') line += 1;
  if (!*line) return 1;
  if (*line == IHEX_COMMENT_PREFIX) return 1;

  rpd->record = line;
  IhexParsedRecord *record = ihexParseRecord(rpd);
  int ok = 0;

  if (record) {
    if (ihexCallHandler(fpd, record)) {
      ok = 1;
    }

    free(record);
  }

  return ok;
}

int
ihexProcessFile (const char *path, IhexRecordHandler *handler, void *data) {
  IhexFileProcessingData fpd = {
    .rpd = {
      .source = path,
      .line = 0
    },

    .handler = handler,
    .data = data
  };

  int ok = 0;
  FILE *file = openDataFile(path, "r", 0);

  if (file) {
    if (processLines(file, ihexProcessLine, &fpd)) {
      if (!fpd.rpd.error) {
        ok = 1;
      }
    }

    fclose(file);
  } else if (errno == ENOENT) {
    char *url = makePath(PACKAGE_URL, IHEX_FILES_SUBDIRECTORY);

    if (url) {
      logMessage(LOG_WARNING, "missing firmware blobs can be downloaded from %s", url);
      free(url);
    }
  }

  return ok;
}

char *
ihexEnsureExtension (const char *path) {
  return ensureFileExtension(path, IHEX_FILE_EXTENSION);
}

char *
ihexMakePath (const char *directory, const char *name) {
  char *subdirectory = makePath(directory, IHEX_FILES_SUBDIRECTORY);

  if (subdirectory) {
    char *file = makeFilePath(subdirectory, name, IHEX_FILE_EXTENSION);

    free(subdirectory);
    if (file) return file;
  }

  return NULL;
}
