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
#include <fcntl.h>
#include <sys/stat.h>

#include "log.h"
#include "cldr.h"
#include "file.h"

#undef HAVE_XML_PROCESSOR

#if defined(HAVE_EXPAT)
#define HAVE_XML_PROCESSOR
#include <expat.h>

#else /* XML processor */
#endif /* XML processor */

#ifdef HAVE_XML_PROCESSOR
struct CLDR_DocumentParserObjectStruct {
  struct {
    CLDR_AnnotationHandler *handler;
    void *data;
  } caller;

  struct {
    XML_Parser parser;
    unsigned int depth;
  } document;

  struct {
    char *sequence;
    char *name;
    unsigned int depth;
  } annotation;
};

static void
abortParser (CLDR_DocumentParserObject *dpo) {
  XML_StopParser(dpo->document.parser, 0);
}

static void
appendAnnotationText (void *userData, const char *characters, int count) {
  CLDR_DocumentParserObject *dpo = userData;

  if (dpo->document.depth == dpo->annotation.depth) {
    if (count > 0) {
      char *name = dpo->annotation.name;

      if (name) {
        size_t oldLength = strlen(name);
        size_t newLength = oldLength + count;
        char *newName = realloc(name, newLength+1);

        if (!newName) {
          logMallocError();
          abortParser(dpo);
          return;
        }

        memcpy(&newName[oldLength], characters, count);
        newName[newLength] = 0;
        name = newName;
      } else {
        if (!(name = malloc(count+1))) {
          logMallocError();
          abortParser(dpo);
          return;
        }

        memcpy(name, characters, count);
        name[count] = 0;
      }

      dpo->annotation.name = name;
    }
  }
}

static void
handleElementStart (void *userData, const char *element, const char **attributes) {
  CLDR_DocumentParserObject *dpo = userData;
  dpo->document.depth += 1;

  if (dpo->annotation.depth) {
    logMessage(LOG_WARNING, "nested annotation");
    abortParser(dpo);
    return;
  }

  if (strcmp(element, "annotation") == 0) {
    const char *sequence = NULL;
    int tts = 0;

    while (*attributes) {
      const char *name = *attributes++;
      const char *value = *attributes++;

      if (strcmp(name, "type") == 0) {
        if (strcmp(value, "tts") == 0) tts = 1;
      } else if (strcmp(name, "cp") == 0) {
        sequence = value;
      }
    }

    if (tts) {
      if (sequence) {
        if ((dpo->annotation.sequence = strdup(sequence))) {
          dpo->annotation.depth = dpo->document.depth;
        } else {
          logMallocError();
          abortParser(dpo);
        }
      }
    }
  }
}

static void
handleElementEnd (void *userData, const char *name) {
  CLDR_DocumentParserObject *dpo = userData;

  if (dpo->document.depth == dpo->annotation.depth) {
    if (dpo->annotation.name) {
      CLDR_AnnotationHandlerParameters parameters = {
        .sequence = dpo->annotation.sequence,
        .name = dpo->annotation.name,
        .data = dpo->caller.data
      };

      if (!dpo->caller.handler(&parameters)) {
        abortParser(dpo);
        return;
      }

      free(dpo->annotation.name);
      dpo->annotation.name = NULL;
    }

    free(dpo->annotation.sequence);
    dpo->annotation.sequence = NULL;

    dpo->annotation.depth = 0;
  }

  dpo->document.depth -= 1;
}

CLDR_DocumentParserObject *
cldrNewDocumentParser (CLDR_AnnotationHandler *handler, void *data) {
  CLDR_DocumentParserObject *dpo;

  if ((dpo = malloc(sizeof(*dpo)))) {
    memset(dpo, 0, sizeof(*dpo));

    dpo->caller.handler = handler;
    dpo->caller.data = data;

    dpo->document.depth = 0;

    dpo->annotation.sequence = NULL;
    dpo->annotation.name = NULL;
    dpo->annotation.depth = 0;

    if ((dpo->document.parser = XML_ParserCreate(NULL))) {
      XML_SetUserData(dpo->document.parser, dpo);
      XML_SetElementHandler(dpo->document.parser, handleElementStart, handleElementEnd);
      XML_SetCharacterDataHandler(dpo->document.parser, appendAnnotationText);
      return dpo;
    } else {
      logMallocError();
    }

    free(dpo);
  } else {
    logMallocError();
  }

  return NULL;
}

void
cldrDestroyDocumentParser (CLDR_DocumentParserObject *dpo) {
  if (dpo->annotation.sequence) {
    free(dpo->annotation.sequence);
    dpo->annotation.sequence = NULL;
  }

  if (dpo->annotation.name) {
    free(dpo->annotation.name);
    dpo->annotation.name = NULL;
  }

  XML_ParserFree(dpo->document.parser);
  free(dpo);
}

int
cldrParseText (CLDR_DocumentParserObject *dpo, const char *text, size_t size, int final) {
  enum XML_Status status = XML_Parse(dpo->document.parser, text, size, final);

  switch (status) {
    case XML_STATUS_OK:
      return 1;

    case XML_STATUS_ERROR:
      logMessage(LOG_WARNING, "CLDR parse error: %s", XML_ErrorString(XML_GetErrorCode(dpo->document.parser)));
      break;

    default:
      logMessage(LOG_WARNING, "unrecognized CLDR parse status: %d", status);
      break;
  }

  return 0;
}

int
cldrParseDocument (
  const char *document, size_t size,
  CLDR_AnnotationHandler *handler, void *data
) {
  int ok = 0;
  CLDR_DocumentParserObject *dpo = cldrNewDocumentParser(handler, data);

  if (dpo) {
    if (cldrParseText(dpo, document, size, 1)) ok = 1;
    cldrDestroyDocumentParser(dpo);
  }

  return ok;
}
#endif /* HAVE_XML_PROCESSOR */

const char cldrAnnotationsDirectory[] = "/usr/share/unicode/cldr/common/annotations";
const char cldrAnnotationsExtension[] = ".xml";

int
cldrParseFile (
  const char *name,
  CLDR_AnnotationHandler *handler, void *data
) {
  int ok = 0;

#ifdef HAVE_XML_PROCESSOR
  char *path = makeFilePath(cldrAnnotationsDirectory, name, cldrAnnotationsExtension);

  if (path) {
    logMessage(LOG_DEBUG, "processing CLDR annotations file: %s", path);
    int fd = open(path, O_RDONLY);

    if (fd != -1) {
      CLDR_DocumentParserObject *dpo = cldrNewDocumentParser(handler, data);

      if (dpo) {
        while (1) {
          char buffer[0X2000];
          size_t size = sizeof(buffer);
          ssize_t count = read(fd, buffer, size);

          if (count == -1) {
            if (errno == EINTR) continue;
            logMessage(LOG_WARNING, "CLDR read error: %s: %s", strerror(errno), path);
            break;
          }

          int final = count == 0;
          if (!cldrParseText(dpo, buffer, count, final)) break;

          if (final) {
            ok = 1;
            break;
          }
        }

        cldrDestroyDocumentParser(dpo);
      }

      close(fd);
      fd = -1;
    } else {
      logMessage(LOG_WARNING, "CLDR open error: %s: %s", strerror(errno), path);

      if (errno == ENOENT) {
        if (!isAbsolutePath(name)) {
          if (!testDirectoryPath(cldrAnnotationsDirectory)) {
            logPossibleCause("the package that defines the CLDR annotations directory is not installed");
          }
        }
      }
    }

    free(path);
  }
#else /* HAVE_XML_PROCESSOR */
  logMessage(LOG_WARNING, "CLDR data can't be loaded - no supported XML parser");
#endif /* HAVE_XML_PROCESSOR */

  return ok;
}
