/*
 * Copyright (C) 2020 Google Inc.
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

package com.google.android.accessibility.talkback.actor;

import static android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD;
import static android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD;
import static com.google.android.accessibility.utils.Performance.EVENT_ID_UNTRACKED;

import android.content.Context;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import com.google.android.accessibility.talkback.Feedback;
import com.google.android.accessibility.talkback.Pipeline;
import com.google.android.accessibility.talkback.R;
import com.google.android.accessibility.talkback.focusmanagement.AccessibilityFocusMonitor;
import com.google.android.accessibility.utils.AccessibilityNodeInfoUtils;
import com.google.android.accessibility.utils.Role;
import com.google.android.accessibility.utils.output.SpeechController.SpeakOptions;
import com.google.android.libraries.accessibility.utils.log.LogUtils;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * This class supports to notify user while manipulating slider(SeekBar). When the current value is
 * already maximum and user tries to increase it, or the value is minimum and user tries to decrease
 * it, user will get notify about the status.
 */
public class NumberAdjustor {
  private static final String TAG = "NumberAdjustor";
  private final Context context;
  private Pipeline.FeedbackReturner pipeline;
  private final AccessibilityFocusMonitor accessibilityFocusMonitor;

  public NumberAdjustor(Context context, AccessibilityFocusMonitor accessibilityFocusMonitor) {
    this.context = context;
    this.accessibilityFocusMonitor = accessibilityFocusMonitor;
  }

  public void setPipeline(Pipeline.FeedbackReturner pipeline) {
    this.pipeline = pipeline;
  }

  /**
   * Check whether the focused node is Role.ROLE_SEEK_CONTROL, and the current value has reached its
   * upper/lower bound.
   *
   * <p><strong>Note:</strong> It is a client responsibility to recycle the received info by calling
   * {@link AccessibilityNodeInfoCompat#recycle()} .
   *
   * @return true when the value adjusting succeeds. Return false otherwise.
   */
  public boolean adjustValue(boolean decrease) {
    @Nullable AccessibilityNodeInfoCompat node = null;
    try {
      node = accessibilityFocusMonitor.getSupportedAdjustableNode();
      if (node == null) {
        return false;
      }
      if (Role.getRole(node) == Role.ROLE_SEEK_CONTROL) {
        final AccessibilityNodeInfoCompat.RangeInfoCompat rangeInfo = node.getRangeInfo();
        if (rangeInfo != null) {
          if (decrease && (rangeInfo.getCurrent() <= rangeInfo.getMin())) {
            // Notify user it reaches lower bound: 0%
            pipeline.returnFeedback(
                EVENT_ID_UNTRACKED,
                Feedback.speech(
                    context.getString(R.string.template_seekbar_range, 0), SpeakOptions.create()));
            return false;
          } else if (!decrease && (rangeInfo.getCurrent() >= rangeInfo.getMax())) {
            // Notify user it reaches upper bound: 100%
            pipeline.returnFeedback(
                EVENT_ID_UNTRACKED,
                Feedback.speech(
                    context.getString(R.string.template_seekbar_range, 100),
                    SpeakOptions.create()));
            return false;
          }
        }
      }
      if (decrease) {
        if (AccessibilityNodeInfoUtils.supportsAction(node, ACTION_SCROLL_BACKWARD.getId())) {
          pipeline.returnFeedback(
              EVENT_ID_UNTRACKED, Feedback.nodeAction(node, ACTION_SCROLL_BACKWARD.getId()));
          return true;
        }
      } else {
        if (AccessibilityNodeInfoUtils.supportsAction(node, ACTION_SCROLL_FORWARD.getId())) {
          pipeline.returnFeedback(
              EVENT_ID_UNTRACKED, Feedback.nodeAction(node, ACTION_SCROLL_FORWARD.getId()));
          return true;
        }
      }
      LogUtils.d(TAG, "adjustValue does not happen");
      return false;
    } finally {
      AccessibilityNodeInfoUtils.recycleNodes(node);
    }
  }
}
