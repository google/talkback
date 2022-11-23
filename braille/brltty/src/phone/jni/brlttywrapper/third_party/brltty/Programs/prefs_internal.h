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

#ifndef BRLTTY_INCLUDED_PREFS_INTERNAL
#define BRLTTY_INCLUDED_PREFS_INTERNAL

#ifdef __cplusplus
extern "C" {
#endif /* __cplusplus */

typedef struct {
  const char *const *table;
  unsigned char count;
} PreferenceStringTable;

struct PreferenceEntryStruct {
  const char *name;
  unsigned char *encountered;
  const PreferenceStringTable *settingNames;
  unsigned char defaultValue;
  unsigned char settingCount;
  unsigned char *setting;
};

extern unsigned char statusFieldsSet;
extern const PreferenceEntry preferenceTable[];
extern const unsigned char preferenceCount;

typedef struct {
  const char *oldName;
  const char *newName;
} PreferenceAliasEntry;

extern const PreferenceAliasEntry preferenceAliasTable[];
extern const unsigned char preferenceAliasCount;

#ifdef __cplusplus
}
#endif /* __cplusplus */

#endif /* BRLTTY_INCLUDED_PREFS_INTERNAL */
