/*
 * Copyright (C) 2023 Google Inc.
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
package com.google.android.accessibility.talkback.compositor.roledescription;

import static androidx.core.view.ViewCompat.ACCESSIBILITY_LIVE_REGION_NONE;

import android.content.Context;
import android.text.TextUtils;
import android.view.accessibility.AccessibilityEvent;
import androidx.annotation.Nullable;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat.RangeInfoCompat;
import com.google.android.accessibility.talkback.R;
import com.google.android.accessibility.talkback.compositor.AccessibilityNodeFeedbackUtils;
import com.google.android.accessibility.talkback.compositor.GlobalVariables;
import com.google.android.accessibility.utils.AccessibilityNodeInfoUtils;
import com.google.android.accessibility.utils.ImageContents;
import com.google.android.accessibility.utils.Role;
import com.google.android.libraries.accessibility.utils.log.LogUtils;

/** Role description for {@link Role.ROLE_SEEK_CONTROL} and {@link Role.ROLE_PROGRESS_BAR}. */
public final class SeekBarDescription implements RoleDescription {

  private static final String TAG = "SeekBarDescription";

  private final ImageContents imageContents;

  SeekBarDescription(ImageContents imageContents) {
    this.imageContents = imageContents;
  }

  @Override
  public boolean shouldIgnoreDescription(AccessibilityNodeInfoCompat node) {
    StringBuilder logString = new StringBuilder();
    boolean isAccessibilityFocused = node.isAccessibilityFocused();
    int nodeLiveRegion = node.getLiveRegion();
    boolean result;
    if (node.isAccessibilityFocused() || nodeLiveRegion != ACCESSIBILITY_LIVE_REGION_NONE) {
      result = false;
    } else {
      logString.append("ignore description");
      result = true;
    }
    LogUtils.v(
        TAG,
        String.format(
            "    shouldIgnoreDescription: (%s)  %s",
            node.hashCode(),
            logString
                .append(String.format(", isAccessibilityFocused=%s", isAccessibilityFocused))
                .append(String.format(", nodeLiveRegion=%s", nodeLiveRegion))));
    return result;
  }

  @Override
  public CharSequence nodeName(
      AccessibilityNodeInfoCompat node, Context context, GlobalVariables globalVariables) {
    return AccessibilityNodeFeedbackUtils.getNodeTextOrLabelOrIdDescription(
        node, context, imageContents, globalVariables);
  }

  @Override
  public CharSequence nodeRole(
      AccessibilityNodeInfoCompat node, Context context, GlobalVariables globalVariables) {
    return AccessibilityNodeFeedbackUtils.defaultRoleDescription(node, context, globalVariables);
  }

  @Override
  public CharSequence nodeState(
      AccessibilityEvent event,
      AccessibilityNodeInfoCompat node,
      Context context,
      GlobalVariables globalVariables) {
    return stateDescription(event, node, context, globalVariables);
  }

  /** Returns seekbar node state description text. */
  private static CharSequence stateDescription(
      AccessibilityEvent event,
      AccessibilityNodeInfoCompat node,
      Context context,
      GlobalVariables globalVariables) {
    CharSequence stateDescription =
        AccessibilityNodeFeedbackUtils.getNodeStateDescription(
            node, context, globalVariables.getPreferredLocaleByNode(node));
    if (!TextUtils.isEmpty(stateDescription)) {
      return stateDescription;
    }
    return seekBarPercentText(event, node, context);
  }

  /** Returns the seekbar percent description text. */
  private static CharSequence seekBarPercentText(
      AccessibilityEvent event, AccessibilityNodeInfoCompat node, Context context) {
    @Nullable RangeInfoCompat rangeInfo = node.getRangeInfo();
    float current = rangeInfo == null ? 0 : rangeInfo.getCurrent();
    int type = rangeInfo == null ? -1 : rangeInfo.getType();
    switch (type) {
      case RangeInfoCompat.RANGE_TYPE_PERCENT:
        return context.getString(R.string.template_percent, String.valueOf(current));
      case RangeInfoCompat.RANGE_TYPE_INT:
        return context.getString(R.string.template_value, String.valueOf((int) current));
      case RangeInfoCompat.RANGE_TYPE_FLOAT:
        return context.getString(R.string.template_value, String.valueOf(current));
      default:
        return event.getItemCount() > 0
            ? context.getString(
                R.string.template_percent,
                String.valueOf(
                    AccessibilityNodeInfoUtils.roundForProgressPercent(
                        AccessibilityNodeInfoUtils.getProgressPercent(node))))
            : "";
    }
  }
}
