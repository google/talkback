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

#ifndef BRLTTY_INCLUDED_ATB
#define BRLTTY_INCLUDED_ATB

#ifdef __cplusplus
extern "C" {
#endif /* __cplusplus */

typedef struct AttributesTableStruct AttributesTable;
extern AttributesTable *attributesTable;

extern void lockAttributesTable (void);
extern void unlockAttributesTable (void);

extern AttributesTable *compileAttributesTable (const char *name);
extern void destroyAttributesTable (AttributesTable *table);

extern char *ensureAttributesTableExtension (const char *path);
extern char *makeAttributesTablePath (const char *directory, const char *name);

extern int replaceAttributesTable (const char *directory, const char *name);

extern unsigned char convertAttributesToDots (AttributesTable *table, unsigned char attributes);

#ifdef __cplusplus
}
#endif /* __cplusplus */

#endif /* BRLTTY_INCLUDED_ATB */
