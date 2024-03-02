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

package com.google.android.accessibility.utils.output;

import android.view.accessibility.AccessibilityEvent;
import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import com.google.android.accessibility.utils.AccessibilityNode;
import com.google.android.accessibility.utils.StringBuilderUtils;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Record about a scroll-action. Used to match {@link AccessibilityEvent#TYPE_VIEW_SCROLLED} to
 * action.
 */
public class ScrollActionRecord {

  //////////////////////////////////////////////////////////////////////////////////////
  // Constants

  // Calling features that caused scroll
  public static final String FOCUS = "FOCUS";

  // User actions that caused scroll
  public static final int ACTION_UNKNOWN = 0;
  public static final int ACTION_AUTO_SCROLL = 1;
  public static final int ACTION_SCROLL_COMMAND = 2;
  public static final int ACTION_MANUAL_SCROLL = 3;

  /** Source action types that result in scroll events. */
  @IntDef({ACTION_UNKNOWN, ACTION_AUTO_SCROLL, ACTION_SCROLL_COMMAND, ACTION_MANUAL_SCROLL})
  @Retention(RetentionPolicy.SOURCE)
  public @interface UserAction {}

  public static String userActionToString(@UserAction int action) {
    switch (action) {
      case ACTION_AUTO_SCROLL:
        return "ACTION_AUTO_SCROLL";
      case ACTION_SCROLL_COMMAND:
        return "ACTION_SCROLL_SHORTCUT";
      case ACTION_MANUAL_SCROLL:
        return "ACTION_MANUAL_SCROLL";
      case ACTION_UNKNOWN:
      default:
        return "ACTION_UNKNOWN";
    }
  }

  //////////////////////////////////////////////////////////////////////////////////////
  // Member data
  public final int scrollInstanceId;
  @UserAction public final int userAction;

  /**
   * During transition from AccessibilityNodeInfoCompat to AccessibilityNode, some callers provide
   * AccessibilityNode, others provide compat -- either works. AutoScrollRecord recyles node.
   */
  public final @Nullable AccessibilityNode scrolledNode;

  // TODO: Switch focus-management to use AccessibilityNode, and remove this
  // redundant field.
  public final @Nullable AccessibilityNodeInfoCompat scrolledNodeCompat;

  // SystemClock.uptimeMillis(), used to compare with AccessibilityEvent.getEventTime().
  public final long autoScrolledTime;
  public final @Nullable String scrollSource;

  //////////////////////////////////////////////////////////////////////////////////////
  // Construction

  /** Creates scroll-record */
  public ScrollActionRecord(
      int scrollInstanceId,
      @Nullable AccessibilityNode scrolledNode,
      @Nullable AccessibilityNodeInfoCompat scrolledNodeCompat,
      @UserAction int userAction,
      long autoScrolledTime,
      @Nullable String scrollSource) {
    this.scrollInstanceId = scrollInstanceId;
    this.userAction = userAction;
    this.scrolledNode = scrolledNode;
    this.scrolledNodeCompat = scrolledNodeCompat;
    this.autoScrolledTime = autoScrolledTime;
    this.scrollSource = scrollSource;
  }

  //////////////////////////////////////////////////////////////////////////////////////
  // Methods

  public boolean scrolledNodeMatches(@Nullable AccessibilityNodeInfoCompat node) {
    if (node == null) {
      return false;
    }
    if (scrolledNodeCompat != null) {
      return scrolledNodeCompat.equals(node);
    } else if (scrolledNode != null) {
      return scrolledNode.equalTo(node);
    } else {
      return false;
    }
  }

  public void refresh() {
    if (scrolledNode != null) {
      scrolledNode.refresh();
    }
    if (scrolledNodeCompat != null) {
      scrolledNodeCompat.refresh();
    }
  }

  @NonNull
  @Override
  public String toString() {
    return StringBuilderUtils.joinFields(
        StringBuilderUtils.optionalSubObj("scrolledNode", scrolledNode),
        StringBuilderUtils.optionalSubObj("scrolledNodeCompat", scrolledNodeCompat),
        StringBuilderUtils.optionalInt("scrollInstanceId", scrollInstanceId, 0),
        StringBuilderUtils.optionalInt("userAction", userAction, 0),
        StringBuilderUtils.optionalInt("autoScrolledTime", autoScrolledTime, 0),
        StringBuilderUtils.optionalText("scrollSource", scrollSource));
  }
}
