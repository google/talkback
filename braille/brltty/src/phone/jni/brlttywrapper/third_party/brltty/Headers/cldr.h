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

#ifndef BRLTTY_INCLUDED_CLDR
#define BRLTTY_INCLUDED_CLDR

#ifdef __cplusplus
extern "C" {
#endif /* __cplusplus */

typedef struct {
  const char *sequence;
  const char *name;
  void *data;
} CLDR_AnnotationHandlerParameters;

#define CLDR_ANNOTATION_HANDLER(name) int name (const CLDR_AnnotationHandlerParameters *parameters)
typedef CLDR_ANNOTATION_HANDLER(CLDR_AnnotationHandler);

typedef struct CLDR_DocumentParserObjectStruct CLDR_DocumentParserObject;

extern CLDR_DocumentParserObject *cldrNewDocumentParser (
  CLDR_AnnotationHandler *handler, void *data
);

extern void cldrDestroyDocumentParser (
  CLDR_DocumentParserObject *dpo
);

extern int cldrParseText (
  CLDR_DocumentParserObject *dpo,
  const char *text, size_t size, int final
);

extern int cldrParseDocument (
  const char *document, size_t size,
  CLDR_AnnotationHandler *handler, void *data
);

extern const char cldrAnnotationsDirectory[];
extern const char cldrAnnotationsExtension[];

extern int cldrParseFile (
  const char *name,
  CLDR_AnnotationHandler *handler, void *data
);

#ifdef __cplusplus
}
#endif /* __cplusplus */

#endif /* BRLTTY_INCLUDED_CLDR */
