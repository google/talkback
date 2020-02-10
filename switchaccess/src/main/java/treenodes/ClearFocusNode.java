/*
 * Copyright (C) 2015 Google Inc.
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

package com.google.android.accessibility.switchaccess.treenodes;

import android.graphics.Rect;
import androidx.annotation.Nullable;

/** Leaf node of scanning tree that does nothing and just allows focus to be cleared. */
public class ClearFocusNode extends TreeScanLeafNode {

  @Nullable
  @Override
  public Rect getRectForNodeHighlight() {
    return null;
  }

  @Override
  public boolean isProbablyTheSameAs(Object other) {
    // We don't want to compare a ClearFocusNode and another type of TreeScanNode when determining
    // whether or not to clear focus, as the highlight should never fall on this node during
    // scanning.
    return other instanceof ClearFocusNode;
  }

  @Override
  public boolean equals(@Nullable Object other) {
    return other instanceof ClearFocusNode;
  }

  @Override
  public int hashCode() {
    /*
     * Hashing function taken from an example in "Effective Java" page 38/39. The number 13 is
     * arbitrary, but choosing non-zero number to start decreases the number of collisions. 37
     * is used as it's an odd prime. If multiplication overflowed and the 37 was an even number,
     * it would be equivalent to bit shifting. The fact that 37 is prime is standard practice.
     */
    int hashCode = 13;
    hashCode = 37 * hashCode + getClass().hashCode();
    return hashCode;
  }
}
