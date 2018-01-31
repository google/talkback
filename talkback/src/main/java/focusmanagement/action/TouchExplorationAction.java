/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.google.android.accessibility.talkback.focusmanagement.action;

import android.support.annotation.IntDef;
import android.support.annotation.Nullable;
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/** A class defining user actions during touch exploration. */
public class TouchExplorationAction {
  /** touch exploration action types. */
  @IntDef({TOUCH_INTERACTION_START, HOVER_ENTER, TOUCH_INTERACTION_END})
  @Retention(RetentionPolicy.SOURCE)
  public @interface ActionType {}

  public static final int TOUCH_INTERACTION_START = 0;
  public static final int HOVER_ENTER = 1;
  public static final int TOUCH_INTERACTION_END = 2;

  @ActionType public final int type;
  /**
   * Node being touched.
   *
   * <p><strong>Note: </strong> It must be null if {@link #type}=={@link #TOUCH_INTERACTION_START}
   * or {@link #TOUCH_INTERACTION_END}. It must be non-null if {@link #type} == {@link
   * #HOVER_ENTER}.
   */
  public final AccessibilityNodeInfoCompat touchedNode;

  public TouchExplorationAction(
      @ActionType int type, @Nullable AccessibilityNodeInfoCompat touchedNode) {
    this.type = type;
    this.touchedNode = touchedNode;
  }
}
