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

object NodeFocusFinder {
  const val SEARCH_FORWARD = 1
  const val SEARCH_BACKWARD = -1

  /**
   * Perform in-order navigation from a given node in a particular direction.
   *
   * @param node The starting node.
   * @param direction The direction to travel.
   * @return The next node in the specified direction, or {@code null} if there are no more nodes.
   */
  @JvmStatic
  fun focusSearch(
    node: AccessibilityNodeInfoCompat?,
    direction: Int,
  ): AccessibilityNodeInfoCompat? {
    val ref = AccessibilityNodeInfoRef.unOwned(node) ?: return null

    when (direction) {
      SEARCH_FORWARD -> {
        if (!ref.nextInOrder()) {
          return null
        }
        return ref.release()
      }
      SEARCH_BACKWARD -> {
        if (!ref.previousInOrder()) {
          return null
        }
        return ref.release()
      }
      else -> {}
    }

    return null
  }
}
