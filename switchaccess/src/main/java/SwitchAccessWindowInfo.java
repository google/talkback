/*
 * Copyright (C) 2015 The Android Open Source Project
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
 */

package com.google.android.accessibility.switchaccess;

import android.graphics.Rect;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import java.util.ArrayList;
import java.util.List;
import org.checkerframework.checker.nullness.qual.Nullable;

/** Extension of AccessibilityWindowInfo that returns {@code ExtendedNodeCompat} for its root */
public class SwitchAccessWindowInfo {
  private final AccessibilityWindowInfo accessibilityWindowInfo;
  private final List<AccessibilityWindowInfo> listOfWindowsAbove;

  /**
   * Convert a list of standard {@code AccessibilityWindowInfo} objects into a list of {@code
   * ExtendedWindowInfo} objects.
   *
   * @param originalList The original list in Z order (as the framework returns it)
   * @return The new list in the same order as the original
   */
  public static List<SwitchAccessWindowInfo> convertZOrderWindowList(
      List<AccessibilityWindowInfo> originalList) {
    List<SwitchAccessWindowInfo> newList = new ArrayList<>(originalList.size());
    for (int i = 0; i < originalList.size(); i++) {
      newList.add(new SwitchAccessWindowInfo(originalList.get(i), originalList.subList(0, i)));
    }
    return newList;
  }

  /**
   * @param accessibilityWindowInfo The windowInfo to wrap
   * @param listOfWindowsAbove A list of all windows above this one
   */
  public SwitchAccessWindowInfo(
      AccessibilityWindowInfo accessibilityWindowInfo,
      List<AccessibilityWindowInfo> listOfWindowsAbove) {
    if (accessibilityWindowInfo == null) {
      throw new NullPointerException();
    }
    this.accessibilityWindowInfo = accessibilityWindowInfo;
    this.listOfWindowsAbove = listOfWindowsAbove;
  }

  /** @return The root of the window */
  @Nullable
  public SwitchAccessNodeCompat getRoot() {
    AccessibilityNodeInfo root = null;
    try {
      root = accessibilityWindowInfo.getRoot();
    } catch (NullPointerException | SecurityException | StackOverflowError e) {
      // If the framework throws an exception, ignore.
    }
    return (root == null) ? null : new SwitchAccessNodeCompat(root, listOfWindowsAbove);
  }

  /** @return The type of the window. See {@link AccessibilityWindowInfo} */
  public int getType() {
    return accessibilityWindowInfo.getType();
  }

  public void getBoundsInScreen(Rect outBounds) {
    accessibilityWindowInfo.getBoundsInScreen(outBounds);
  }
}
