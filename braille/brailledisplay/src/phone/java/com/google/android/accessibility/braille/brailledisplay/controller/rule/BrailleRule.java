/*
 * Copyright (C) 2012 Google Inc.
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

package com.google.android.accessibility.braille.brailledisplay.controller.rule;

import android.content.Context;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;

/** Decides how to format a single node for output on a braille display. */
public interface BrailleRule {

  /** Returns whether this rule should be used for this node. */
  boolean accept(AccessibilityNodeInfoCompat node);

  /** Returns formatted {@code node} braille output. */
  CharSequence format(Context context, AccessibilityNodeInfoCompat node);

  /**
   * Returns {@code true} if the children of this node should be appended after formatting with this
   * rule.
   */
  boolean includeChildren(AccessibilityNodeInfoCompat node);
}
