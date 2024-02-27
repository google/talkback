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

#ifndef BRLTTY_INCLUDED_PREF_TABLES
#define BRLTTY_INCLUDED_PREF_TABLES

#ifdef __cplusplus
extern "C" {
#endif /* __cplusplus */

typedef struct {
  const char *const *table;
  unsigned char count;
} PreferenceStringTable;

struct PreferenceDefinitionStruct {
  const char *name;
  unsigned char *setting;
  const PreferenceStringTable *settingNames;
  unsigned char *encountered;
  unsigned char settingCount;
  unsigned char defaultValue;
  unsigned char dontSave:1;
};

extern unsigned char statusFieldsSet;
extern const PreferenceDefinition preferenceDefinitionTable[];
extern const unsigned char preferenceDefinitionCount;

typedef struct {
  const char *oldName;
  const char *newName;
} PreferenceAlias;

extern const PreferenceAlias preferenceAliasTable[];
extern const unsigned char preferenceAliasCount;

#ifdef __cplusplus
}
#endif /* __cplusplus */

#endif /* BRLTTY_INCLUDED_PREF_TABLES */
