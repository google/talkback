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

import android.view.accessibility.AccessibilityNodeInfo;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;

/** An interface for retrieving custom labels. */
public interface LabelManager {
  static final int SOURCE_TYPE_USER = 0; // labels that were inserted by user
  static final int SOURCE_TYPE_IMPORT = 1; // labels that were imported
  static final int SOURCE_TYPE_BACKUP = 2; // labels that were overridden by import

  /**
   * Retrieves a {@link Label} from the label cache given a fully-qualified resource identifier
   * name.
   *
   * @param resourceName The fully-qualified resource identifier, such as
   *     "com.android.deskclock:id/analog_appwidget", as provided by {@link
   *     AccessibilityNodeInfo#getViewIdResourceName()}
   * @return The {@link Label} matching the provided identifier, or {@code null} if no such label
   *     exists or has not yet been fetched from storage
   */
  Label getLabelForViewIdFromCache(String resourceName);

  /** Read-only interface to pull state information. */
  interface State {
    public long getLabelIdForNode(AccessibilityNodeInfoCompat node);

    public boolean supportsLabel(AccessibilityNodeInfoCompat node);

    public boolean needsLabel(AccessibilityNodeInfoCompat node);
  }

  /** Returns an instance of the read-only {@link LabelManager.State}. */
  State stateReader();
}
