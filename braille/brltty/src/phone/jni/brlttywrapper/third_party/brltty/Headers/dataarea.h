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

#ifndef BRLTTY_INCLUDED_DATAAREA
#define BRLTTY_INCLUDED_DATAAREA

#ifdef __cplusplus
extern "C" {
#endif /* __cplusplus */

typedef struct DataAreaStruct DataArea;
extern DataArea *newDataArea (void);
extern void destroyDataArea (DataArea *area);
extern void resetDataArea (DataArea *area);

typedef unsigned long int DataOffset;
extern int allocateDataItem (DataArea *area, DataOffset *offset, size_t size, size_t alignment);
extern void *getDataItem (DataArea *area, DataOffset offset);
extern size_t getDataSize (DataArea *area);
extern int saveDataItem (DataArea *area, DataOffset *offset, const void *item, size_t size, size_t alignment);

#ifdef __cplusplus
}
#endif /* __cplusplus */

#endif /* BRLTTY_INCLUDED_DATAAREA */
