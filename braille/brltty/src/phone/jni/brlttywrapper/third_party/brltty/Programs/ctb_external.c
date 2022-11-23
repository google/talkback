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

#include <string.h>
#include <errno.h>

#include "log.h"
#include "ctb_translate.h"
#include "brl_dots.h"
#include "file.h"
#include "parse.h"
#include "charset.h"

static int
putExternalRequests (BrailleContractionData *bcd) {
  typedef enum {
    REQ_TEXT,
    REQ_NUMBER
  } ExternalRequestType;

  typedef struct {
    const char *name;
    ExternalRequestType type;

    union {
      struct {
        const wchar_t *start;
        size_t count;
      } text;

      unsigned int number;
    } value;
  } ExternalRequestEntry;

  const ExternalRequestEntry externalRequestTable[] = {
    { .name = "cursor-position",
      .type = REQ_NUMBER,
      .value.number = bcd->input.cursor? bcd->input.cursor-bcd->input.begin+1: 0
    },

    { .name = "expand-current-word",
      .type = REQ_NUMBER,
      .value.number = prefs.expandCurrentWord
    },

    { .name = "capitalization-mode",
      .type = REQ_NUMBER,
      .value.number = prefs.capitalizationMode
    },

    { .name = "maximum-length",
      .type = REQ_NUMBER,
      .value.number = getOutputCount(bcd)
    },

    { .name = "text",
      .type = REQ_TEXT,
      .value.text = {
        .start = bcd->input.begin,
        .count = getInputCount(bcd)
      }
    },

    { .name = NULL }
  };

  FILE *stream = bcd->table->data.external.standardInput;
  const ExternalRequestEntry *req = externalRequestTable;

  while (req->name) {
    if (fputs(req->name, stream) == EOF) goto outputError;
    if (fputc('=', stream) == EOF) goto outputError;

    switch (req->type) {
      case REQ_TEXT: {
        const wchar_t *character = req->value.text.start;
        const wchar_t *end = character + req->value.text.count;

        while (character < end) {
          Utf8Buffer utf8;
          size_t utfs = convertWcharToUtf8(*character++, utf8);

          if (!utfs) return 0;
          if (fputs(utf8, stream) == EOF) goto outputError;
        }

        break;
      }

      case REQ_NUMBER:
        if (fprintf(stream, "%u", req->value.number) == EOF) goto outputError;
        break;

      default:
        logMessage(LOG_WARNING, "unimplemented external contraction request property type: %s: %u (%s)", bcd->table->data.external.command, req->type, req->name);
        return 0;
    }

    if (fputc('\n', stream) == EOF) goto outputError;
    req += 1;
  }

  if (fflush(stream) == EOF) goto outputError;
  return 1;

outputError:
  logMessage(LOG_WARNING, "external contraction output error: %s: %s", bcd->table->data.external.command, strerror(errno));
  return 0;
}

static const unsigned char brfTable[0X40] = {
  /* 0X20   */ 0,
  /* 0X21 ! */ BRL_DOT_2 | BRL_DOT_3 | BRL_DOT_4 | BRL_DOT_6,
  /* 0X22 " */ BRL_DOT_5,
  /* 0X23 # */ BRL_DOT_3 | BRL_DOT_4 | BRL_DOT_5 | BRL_DOT_6,
  /* 0X24 $ */ BRL_DOT_1 | BRL_DOT_2 | BRL_DOT_4 | BRL_DOT_6,
  /* 0X25 % */ BRL_DOT_1 | BRL_DOT_4 | BRL_DOT_6,
  /* 0X26 & */ BRL_DOT_1 | BRL_DOT_2 | BRL_DOT_3 | BRL_DOT_4 | BRL_DOT_6,
  /* 0X27 ' */ BRL_DOT_3,
  /* 0X28 ( */ BRL_DOT_1 | BRL_DOT_2 | BRL_DOT_3 | BRL_DOT_5 | BRL_DOT_6,
  /* 0X29 ) */ BRL_DOT_2 | BRL_DOT_3 | BRL_DOT_4 | BRL_DOT_5 | BRL_DOT_6,
  /* 0X2A * */ BRL_DOT_1 | BRL_DOT_6,
  /* 0X2B + */ BRL_DOT_3 | BRL_DOT_4 | BRL_DOT_6,
  /* 0X2C , */ BRL_DOT_6,
  /* 0X2D - */ BRL_DOT_3 | BRL_DOT_6,
  /* 0X2E . */ BRL_DOT_4 | BRL_DOT_6,
  /* 0X2F / */ BRL_DOT_3 | BRL_DOT_4,
  /* 0X30 0 */ BRL_DOT_3 | BRL_DOT_5 | BRL_DOT_6,
  /* 0X31 1 */ BRL_DOT_2,
  /* 0X32 2 */ BRL_DOT_2 | BRL_DOT_3,
  /* 0X33 3 */ BRL_DOT_2 | BRL_DOT_5,
  /* 0X34 4 */ BRL_DOT_2 | BRL_DOT_5 | BRL_DOT_6,
  /* 0X35 5 */ BRL_DOT_2 | BRL_DOT_6,
  /* 0X36 6 */ BRL_DOT_2 | BRL_DOT_3 | BRL_DOT_5,
  /* 0X37 7 */ BRL_DOT_2 | BRL_DOT_3 | BRL_DOT_5 | BRL_DOT_6,
  /* 0X38 8 */ BRL_DOT_2 | BRL_DOT_3 | BRL_DOT_6,
  /* 0X39 9 */ BRL_DOT_3 | BRL_DOT_5,
  /* 0X3A : */ BRL_DOT_1 | BRL_DOT_5 | BRL_DOT_6,
  /* 0X3B ; */ BRL_DOT_5 | BRL_DOT_6,
  /* 0X3C < */ BRL_DOT_1 | BRL_DOT_2 | BRL_DOT_6,
  /* 0X3D = */ BRL_DOT_1 | BRL_DOT_2 | BRL_DOT_3 | BRL_DOT_4 | BRL_DOT_5 | BRL_DOT_6,
  /* 0X3E > */ BRL_DOT_3 | BRL_DOT_4 | BRL_DOT_5,
  /* 0X3F ? */ BRL_DOT_1 | BRL_DOT_4 | BRL_DOT_5 | BRL_DOT_6,
  /* 0X40 @ */ BRL_DOT_4,
  /* 0X41 A */ BRL_DOT_1,
  /* 0X42 B */ BRL_DOT_1 | BRL_DOT_2,
  /* 0X43 C */ BRL_DOT_1 | BRL_DOT_4,
  /* 0X44 D */ BRL_DOT_1 | BRL_DOT_4 | BRL_DOT_5,
  /* 0X45 E */ BRL_DOT_1 | BRL_DOT_5,
  /* 0X46 F */ BRL_DOT_1 | BRL_DOT_2 | BRL_DOT_4,
  /* 0X47 G */ BRL_DOT_1 | BRL_DOT_2 | BRL_DOT_4 | BRL_DOT_5,
  /* 0X48 H */ BRL_DOT_1 | BRL_DOT_2 | BRL_DOT_5,
  /* 0X49 I */ BRL_DOT_2 | BRL_DOT_4,
  /* 0X4A J */ BRL_DOT_2 | BRL_DOT_4 | BRL_DOT_5,
  /* 0X4B K */  BRL_DOT_1 | BRL_DOT_3,
  /* 0X4C L */ BRL_DOT_1 | BRL_DOT_2 | BRL_DOT_3,
  /* 0X4D M */ BRL_DOT_1 | BRL_DOT_3 | BRL_DOT_4,
  /* 0X4E N */ BRL_DOT_1 | BRL_DOT_3 | BRL_DOT_4 | BRL_DOT_5,
  /* 0X4F O */ BRL_DOT_1 | BRL_DOT_3 | BRL_DOT_5,
  /* 0X50 P */ BRL_DOT_1 | BRL_DOT_2 | BRL_DOT_3 | BRL_DOT_4,
  /* 0X51 Q */ BRL_DOT_1 | BRL_DOT_2 | BRL_DOT_3 | BRL_DOT_4 | BRL_DOT_5,
  /* 0X52 R */ BRL_DOT_1 | BRL_DOT_2 | BRL_DOT_3 | BRL_DOT_5,
  /* 0X53 S */ BRL_DOT_2 | BRL_DOT_3 | BRL_DOT_4,
  /* 0X54 T */ BRL_DOT_2 | BRL_DOT_3 | BRL_DOT_4 | BRL_DOT_5,
  /* 0X55 U */ BRL_DOT_1 | BRL_DOT_3 | BRL_DOT_6,
  /* 0X56 V */ BRL_DOT_1 | BRL_DOT_2 | BRL_DOT_3 | BRL_DOT_6,
  /* 0X57 W */ BRL_DOT_2 | BRL_DOT_4 | BRL_DOT_5 | BRL_DOT_6,
  /* 0X58 X */ BRL_DOT_1 | BRL_DOT_3 | BRL_DOT_4 | BRL_DOT_6,
  /* 0X59 Y */ BRL_DOT_1 | BRL_DOT_3 | BRL_DOT_4 | BRL_DOT_5 | BRL_DOT_6,
  /* 0X5A Z */ BRL_DOT_1 | BRL_DOT_3 | BRL_DOT_5 | BRL_DOT_6,
  /* 0X5B [ */ BRL_DOT_2 | BRL_DOT_4 | BRL_DOT_6,
  /* 0X5C \ */ BRL_DOT_1 | BRL_DOT_2 | BRL_DOT_5 | BRL_DOT_6,
  /* 0X5D ] */ BRL_DOT_1 | BRL_DOT_2 | BRL_DOT_4 | BRL_DOT_5 | BRL_DOT_6,
  /* 0X5E ^ */ BRL_DOT_4 | BRL_DOT_5,
  /* 0X5F _ */ BRL_DOT_4 | BRL_DOT_5 | BRL_DOT_6
};

static int
handleExternalResponse_brf (BrailleContractionData *bcd, const char *value) {
  int useDot7 = prefs.capitalizationMode == CTB_CAP_DOT7;

  while (*value && (bcd->output.current < bcd->output.end)) {
    unsigned char brf = *value++ & 0XFF;
    unsigned char dots = 0;
    unsigned char superimpose = 0;

    if ((brf >= 0X60) && (brf <= 0X7F)) {
      brf -= 0X20;
    } else if ((brf >= 0X41) && (brf <= 0X5A)) {
      if (useDot7) superimpose |= BRL_DOT_7;
    }

    if ((brf >= 0X20) && (brf <= 0X5F)) dots = brfTable[brf - 0X20] | superimpose;
    *bcd->output.current++ = dots;
  }

  return 1;
}

static int
handleExternalResponse_consumedLength (BrailleContractionData *bcd, const char *value) {
  int length;

  if (!isInteger(&length, value)) return 0;
  if (length < 1) return 0;
  if (length > getInputCount(bcd)) return 0;

  bcd->input.current = bcd->input.begin + length;
  return 1;
}

static int
handleExternalResponse_outputOffsets (BrailleContractionData *bcd, const char *value) {
  if (bcd->input.offsets) {
    int previous = CTB_NO_OFFSET;
    unsigned int count = getInputCount(bcd);
    unsigned int index = 0;

    while (*value && (index < count)) {
      int offset;

      {
        char *delimiter = strchr(value, ',');

        if (delimiter) {
          int ok;

          {
            char oldDelimiter = *delimiter;
            *delimiter = 0;
            ok = isInteger(&offset, value);
            *delimiter = oldDelimiter;
          }

          if (!ok) return 0;
          value = delimiter + 1;
        } else if (isInteger(&offset, value)) {
          value += strlen(value);
        } else {
          return 0;
        }
      }

      if (offset < ((index == 0)? 0: previous)) return 0;
      if (offset >= getOutputCount(bcd)) return 0;

      bcd->input.offsets[index++] = (offset == previous)? CTB_NO_OFFSET: offset;
      previous = offset;
    }
  }

  return 1;
}

typedef struct {
  const char *name;
  int (*handler) (BrailleContractionData *bcd, const char *value);
  unsigned stop:1;
} ExternalResponseEntry;

static const ExternalResponseEntry externalResponseTable[] = {
  { .name = "brf",
    .stop = 1,
    .handler = handleExternalResponse_brf
  },

  { .name = "consumed-length",
    .handler = handleExternalResponse_consumedLength
  },

  { .name = "output-offsets",
    .handler = handleExternalResponse_outputOffsets
  },

  { .name = NULL }
};

static int
getExternalResponses (BrailleContractionData *bcd) {
  FILE *stream = bcd->table->data.external.standardOutput;

  while (readLine(stream, &bcd->table->data.external.input.buffer, &bcd->table->data.external.input.size)) {
    int ok = 0;
    int stop = 0;
    char *delimiter = strchr(bcd->table->data.external.input.buffer, '=');

    if (delimiter) {
      const char *value = delimiter + 1;
      const ExternalResponseEntry *rsp = externalResponseTable;

      char oldDelimiter = *delimiter;
      *delimiter = 0;

      while (rsp->name) {
        if (strcmp(bcd->table->data.external.input.buffer, rsp->name) == 0) {
          if (rsp->handler(bcd, value)) ok = 1;
          if (rsp->stop) stop = 1;
          break;
        }

        rsp += 1;
      }

      *delimiter = oldDelimiter;
    }

    if (!ok) logMessage(LOG_WARNING, "unexpected external contraction response: %s: %s", bcd->table->data.external.command, bcd->table->data.external.input.buffer);
    if (stop) return 1;
  }

  logMessage(LOG_WARNING, "incomplete external contraction response: %s", bcd->table->data.external.command);
  return 0;
}

static int
contractText_external (BrailleContractionData *bcd) {
  setOffset(bcd);
  while (++bcd->input.current < bcd->input.end) clearOffset(bcd);

  if (startContractionCommand(bcd->table)) {
    if (putExternalRequests(bcd)) {
      if (getExternalResponses(bcd)) {
        return 1;
      }
    }
  }

  stopContractionCommand(bcd->table);
  return 0;
}

static void
finishCharacterEntry_external (BrailleContractionData *bcd, CharacterEntry *entry) {
}

static const ContractionTableTranslationMethods externalTranslationMethods = {
  .contractText = contractText_external,
  .finishCharacterEntry = finishCharacterEntry_external
};

const ContractionTableTranslationMethods *
getContractionTableTranslationMethods_external (void) {
  return &externalTranslationMethods;
}
