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

#ifndef BRLTTY_INCLUDED_KTB_LIST
#define BRLTTY_INCLUDED_KTB_LIST

#include "ktb_internal.h"
#include "ktb_inspect.h"

#ifdef __cplusplus
extern "C" {
#endif /* __cplusplus */

typedef struct {
  const BoundCommand *command;
  const KeyCombination *keyCombination;
  size_t keysOffset;
  size_t length;
  wchar_t text[0];
} BindingLine;

typedef struct {
  KeyTable *const keyTable;

  const wchar_t *topicHeader;
  const char *listHeader;

  struct {
    wchar_t *characters;
    size_t size;
    size_t length;
  } line;

  struct {
    const KeyTableListMethods *const methods;
    KeyTableWriteLineMethod *const writeLine;
    void *const data;
    const unsigned internal:1;

    unsigned int elementLevel;
    wchar_t elementBullet;
  } list;

  struct {
    BindingLine **lines;
    size_t size;
    size_t count;
  } binding;
} ListGenerationData;

#ifdef __cplusplus
}
#endif /* __cplusplus */

#endif /* BRLTTY_INCLUDED_KTB_LIST */
