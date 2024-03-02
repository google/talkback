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
package com.google.android.accessibility.talkback.compositor.rule;

import static com.google.android.accessibility.talkback.compositor.Compositor.EVENT_TYPE_VIEW_CLICKED;
import static com.google.android.accessibility.utils.output.SpeechController.QUEUE_MODE_QUEUE;

import android.content.Context;
import android.text.TextUtils;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import com.google.android.accessibility.talkback.R;
import com.google.android.accessibility.talkback.compositor.AccessibilityNodeFeedbackUtils;
import com.google.android.accessibility.talkback.compositor.Compositor.HandleEventOptions;
import com.google.android.accessibility.talkback.compositor.EventFeedback;
import com.google.android.accessibility.talkback.compositor.GlobalVariables;
import com.google.android.accessibility.talkback.compositor.TalkBackFeedbackProvider;
import com.google.android.accessibility.utils.Role;
import com.google.android.accessibility.utils.StringBuilderUtils;
import com.google.android.libraries.accessibility.utils.log.LogUtils;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * Event feedback rules for {@link EVENT_TYPE_VIEW_CLICKED} event.
 *
 * <p>These rules will provide the event feedback output function by inputting the {@link
 * HandleEventOptions} and outputting {@link EventFeedback}.
 */
public final class EventTypeViewClickedFeedbackRule {

  private static final String TAG = "EventTypeViewClickedFeedbackRule";

  /**
   * Adds the feedback rules to the provided event feedback rules map. So {@link
   * TalkBackFeedbackProvider} can provide the event feedback by the rules.
   *
   * @param eventFeedbackRules the event feedback rules
   * @param context the parent context
   * @param globalVariables the global compositor variables
   */
  public static void addFeedbackRule(
      Map<Integer, Function<HandleEventOptions, EventFeedback>> eventFeedbackRules,
      Context context,
      GlobalVariables globalVariables) {
    eventFeedbackRules.put(
        EVENT_TYPE_VIEW_CLICKED,
        (eventOptions) ->
            EventFeedback.builder()
                .setTtsOutput(
                    computeTtsOutput(
                        eventOptions.sourceNode, context, globalVariables.getUserPreferredLocale()))
                .setQueueMode(QUEUE_MODE_QUEUE)
                .setTtsAddToHistory(true)
                .setForceFeedbackEvenIfAudioPlaybackActive(true)
                .setForceFeedbackEvenIfMicrophoneActive(true)
                .setForceFeedbackEvenIfSsbActive(true)
                .setTtsSkipDuplicate(true)
                .setEarcon(R.raw.tick)
                .setHaptic(R.array.view_clicked_pattern)
                .build());
  }

  private static Optional<CharSequence> computeTtsOutput(
      AccessibilityNodeInfoCompat node, Context context, Locale locale) {
    if (node == null) {
      LogUtils.v(TAG, "source node is null");
      return Optional.empty();
    } else {
      // TtsOutput fallbacks [checkedState, selectedState, collapsedOrExpandedState]
      CharSequence checkedState = getViewCheckStateText(node, context, locale);
      if (!TextUtils.isEmpty(checkedState)) {
        LogUtils.v(TAG, " ttsOutputRule= checkedState");
        return Optional.of(checkedState);
      } else {
        LogUtils.v(
            TAG,
            StringBuilderUtils.joinFields(
                StringBuilderUtils.optionalTag("srcIsCheckable", node.isCheckable()),
                StringBuilderUtils.optionalTag("srcIsChecked", node.isChecked()),
                StringBuilderUtils.optionalTag("srcIsSelected", node.isSelected())));

        CharSequence selectedState =
            AccessibilityNodeFeedbackUtils.getSelectedStateText(node, context);
        if (!TextUtils.isEmpty(selectedState)) {
          LogUtils.v(TAG, " ttsOutputRule= selectedState");
          return Optional.of(selectedState);
        } else {
          LogUtils.v(TAG, " ttsOutputRule= collapsedOrExpandedStateText");
          return Optional.of(
              AccessibilityNodeFeedbackUtils.getCollapsedOrExpandedStateText(node, context));
        }
      }
    }
  }

  /**
   * Returns the view check state text of the source node or the descendant node. If the source node
   * doesn't have valid node checked state, it checks the descendant node.
   */
  private static CharSequence getViewCheckStateText(
      AccessibilityNodeInfoCompat node, Context context, Locale locale) {
    CharSequence nodeChecked = getNodeCheckedStateText(node, context, locale);
    if (!TextUtils.isEmpty(nodeChecked)) {
      return nodeChecked;
    }

    // Traverse the child node checked state if the source node doesn't have node checked state
    // text.
    int childCount = node.getChildCount();
    for (int i = 0; i < childCount; i++) {
      AccessibilityNodeInfoCompat childNode = node.getChild(i);
      if (childNode != null) {
        nodeChecked = getNodeCheckedStateText(childNode, context, locale);
        if (!TextUtils.isEmpty(nodeChecked)) {
          LogUtils.v(
              TAG,
              " getViewCheckStateText: child node index=%d  isChecked=%b",
              i,
              node.isChecked());
          return nodeChecked;
        }
      }
    }
    return "";
  }

  /** Returns the node checked state text of the input node. */
  private static CharSequence getNodeCheckedStateText(
      AccessibilityNodeInfoCompat node, Context context, Locale locale) {
    if (node == null) {
      return "";
    }
    // If node is checkable, speak checked state depending on node type.
    // if stateDescription is set, the CONTENT_CHANGE_TYPE_STATE_DESCRIPTION event will
    // speak out the state description.
    CharSequence stateDescription =
        AccessibilityNodeFeedbackUtils.getNodeStateDescription(node, context, locale);
    if (!node.isCheckable() || !TextUtils.isEmpty(stateDescription)) {
      return "";
    }

    int role = Role.getRole(node);
    boolean isChecked = node.isChecked();
    if (role == Role.ROLE_SWITCH || role == Role.ROLE_TOGGLE_BUTTON) {
      return isChecked
          ? context.getString(R.string.value_on)
          : context.getString(R.string.value_off);
    } else {
      return isChecked
          ? context.getString(R.string.value_checked)
          : context.getString(R.string.value_not_checked);
    }
  }

  private EventTypeViewClickedFeedbackRule() {}
}
