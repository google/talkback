/*
 * Copyright (C) 2022 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

/*
 * Stubs for various system specific functionality that we don't need or
 * have on Android.
 */

#include "prologue.h"  // NOLINT Must include first
#include "alert.h"
#include "ktb_types.h"
#include "program.h"
#include "system.h"

/* program. c */
void registerProgramMemory(const char *name, void *pointer) {}

void onProgramExit(const char *name, ProgramExitHandler *handler, void *data) {}

/* scr.c */
KeyTableCommandContext getScreenCommandContext(void) { return KTB_CTX_DEFAULT; }

/* alert.c */
void alert(AlertIdentifier identifier) {}
