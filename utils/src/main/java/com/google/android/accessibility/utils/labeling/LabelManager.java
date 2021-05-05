/*
 * Copyright (C) 2017 Google Inc.
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

package com.google.android.accessibility.utils.labeling;

import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;

/** An interface for retreiving custom labels. */
public interface LabelManager {
  static final int SOURCE_TYPE_USER = 0; // labels that were inserted by user
  static final int SOURCE_TYPE_IMPORT = 1; // labels that were imported
  static final int SOURCE_TYPE_BACKUP = 2; // labels that were overridden by import

  Label getLabelForViewIdFromCache(String resourceName);
  /** Returns whether node needs a label. */
  boolean needsLabel(AccessibilityNodeInfoCompat node);
}
