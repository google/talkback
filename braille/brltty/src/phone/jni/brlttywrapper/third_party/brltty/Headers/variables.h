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

#ifndef BRLTTY_INCLUDED_VARIABLES
#define BRLTTY_INCLUDED_VARIABLES

#ifdef __cplusplus
extern "C" {
#endif /* __cplusplus */

typedef struct VariableNestingLevelStruct VariableNestingLevel;
typedef struct VariableStruct Variable;

typedef struct {
  const char *name;
  const char *value;
} VariableInitializer;

extern VariableNestingLevel *newVariableNestingLevel (VariableNestingLevel *previous, const char *name);
extern VariableNestingLevel *removeVariableNestingLevel (VariableNestingLevel *vnl);

extern VariableNestingLevel *claimVariableNestingLevel (VariableNestingLevel *vnl);
extern void releaseVariableNestingLevel (VariableNestingLevel *vnl);

extern void listVariables (VariableNestingLevel *from);
extern const Variable *findReadableVariable (VariableNestingLevel *vnl, const wchar_t *name, int length);
extern Variable *findWritableVariable (VariableNestingLevel *vnl, const wchar_t *name, int length);

extern void deleteVariables (VariableNestingLevel *vnl);
extern int setVariable (Variable *variable, const wchar_t *value, int length);

extern void getVariableName (const Variable *variable, const wchar_t **characters, int *length);
extern void getVariableValue (const Variable *variable, const wchar_t **characters, int *length);

extern int setStringVariable (VariableNestingLevel *vnl, const char *name, const char *value);
extern int setStringVariables (VariableNestingLevel *vnl, const VariableInitializer *initializers);

extern VariableNestingLevel *getGlobalVariables (int create);
extern int setGlobalVariable (const char *name, const char *value);

#ifdef __cplusplus
}
#endif /* __cplusplus */

#endif /* BRLTTY_INCLUDED_VARIABLES */
