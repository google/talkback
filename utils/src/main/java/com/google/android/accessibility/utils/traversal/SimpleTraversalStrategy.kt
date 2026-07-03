/*
 * Copyright (C) The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Ported from Java to Kotlin.
 */

package com.google.android.accessibility.utils.traversal

import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import com.google.android.accessibility.utils.AccessibilityNodeInfoRef

class SimpleTraversalStrategy : TraversalStrategy {

  override fun findFocus(
    startNode: AccessibilityNodeInfoCompat?,
    @TraversalStrategy.SearchDirection direction: Int,
  ): AccessibilityNodeInfoCompat? {
    if (startNode == null) {
      return null
    }

    val ref = AccessibilityNodeInfoRef.obtain(startNode)
    val focusFound =
      if (direction == TraversalStrategy.SEARCH_FOCUS_FORWARD) {
        ref.nextInOrder()
      } else {
        ref.previousInOrder()
      }
    if (focusFound) {
      return ref.get()
    }

    return null
  }

  override fun focusFirst(
    root: AccessibilityNodeInfoCompat?,
    @TraversalStrategy.SearchDirection direction: Int,
  ): AccessibilityNodeInfoCompat? {
    if (root == null) {
      return null
    }

    if (direction == TraversalStrategy.SEARCH_FOCUS_FORWARD) {
      return root
    } else if (direction == TraversalStrategy.SEARCH_FOCUS_BACKWARD) {
      val ref = AccessibilityNodeInfoRef.obtain(root)
      return if (ref.lastDescendant()) {
        ref.get()
      } else {
        null
      }
    }

    return null
  }

  override fun getSpeakingNodesCache(): Map<AccessibilityNodeInfoCompat, Boolean>? = null
}
