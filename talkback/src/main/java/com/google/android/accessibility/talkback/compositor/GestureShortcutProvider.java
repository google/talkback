/*
 * Copyright (C) 2019 Google Inc.
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

package com.google.android.accessibility.talkback.compositor;

import androidx.annotation.Nullable;

/** This is an interface to get shortcut name for actions. */
public interface GestureShortcutProvider {

  /** Returns shortcut name for local context menu. */
  @Nullable
  CharSequence nodeMenuShortcut();

  /** Returns shortcut name to select the next setting in reading control. */
  @Nullable
  CharSequence readingMenuNextSettingShortcut();

  /**
   * Returns shortcut name to perform the next action of the selected setting in reading control
   * setting.
   */
  @Nullable
  CharSequence readingMenuUpShortcut();

  /**
   * Returns shortcut name to perform the previous action of the selected setting in reading control
   * setting.
   */
  @Nullable
  CharSequence readingMenuDownShortcut();

  /** Returns shortcut name to select the supported custom actions. */
  @Nullable
  CharSequence actionsShortcut();
}
