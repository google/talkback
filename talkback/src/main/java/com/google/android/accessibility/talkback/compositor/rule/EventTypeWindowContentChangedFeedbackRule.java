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

import static android.view.View.ACCESSIBILITY_LIVE_REGION_ASSERTIVE;
import static android.view.View.ACCESSIBILITY_LIVE_REGION_NONE;
import static android.view.accessibility.AccessibilityEvent.CONTENT_CHANGE_TYPE_CONTENT_DESCRIPTION;
import static android.view.accessibility.AccessibilityEvent.CONTENT_CHANGE_TYPE_DRAG_CANCELLED;
import static android.view.accessibility.AccessibilityEvent.CONTENT_CHANGE_TYPE_DRAG_DROPPED;
import static android.view.accessibility.AccessibilityEvent.CONTENT_CHANGE_TYPE_DRAG_STARTED;
import static android.view.accessibility.AccessibilityEvent.CONTENT_CHANGE_TYPE_ENABLED;
import static android.view.accessibility.AccessibilityEvent.CONTENT_CHANGE_TYPE_ERROR;
import static android.view.accessibility.AccessibilityEvent.CONTENT_CHANGE_TYPE_STATE_DESCRIPTION;
import static android.view.accessibility.AccessibilityEvent.CONTENT_CHANGE_TYPE_TEXT;
import static android.view.accessibility.AccessibilityEvent.CONTENT_CHANGE_TYPE_UNDEFINED;
import static com.google.android.accessibility.talkback.compositor.Compositor.EVENT_TYPE_WINDOW_CONTENT_CHANGED;
import static com.google.android.accessibility.talkback.compositor.TalkBackFeedbackProvider.EMPTY_FEEDBACK;
import static com.google.android.accessibility.utils.output.SpeechController.QUEUE_MODE_INTERRUPT;
import static com.google.android.accessibility.utils.output.SpeechController.QUEUE_MODE_QUEUE;
import static com.google.android.accessibility.utils.output.SpeechController.QUEUE_MODE_UNINTERRUPTIBLE_BY_NEW_SPEECH;
import static com.google.android.accessibility.utils.output.SpeechController.UTTERANCE_GROUP_CONTENT_CHANGE;
import static com.google.android.accessibility.utils.output.SpeechController.UTTERANCE_GROUP_DEFAULT;
import static com.google.android.accessibility.utils.output.SpeechController.UTTERANCE_GROUP_PROGRESS_BAR_PROGRESS;
import static com.google.android.accessibility.utils.output.SpeechController.UTTERANCE_GROUP_SEEK_PROGRESS;

import android.content.Context;
import android.text.TextUtils;
import android.view.accessibility.AccessibilityEvent;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import com.google.android.accessibility.talkback.R;
import com.google.android.accessibility.talkback.compositor.AccessibilityEventFeedbackUtils;
import com.google.android.accessibility.talkback.compositor.AccessibilityNodeFeedbackUtils;
import com.google.android.accessibility.talkback.compositor.Compositor.HandleEventOptions;
import com.google.android.accessibility.talkback.compositor.EarconFeedbackUtils;
import com.google.android.accessibility.talkback.compositor.EventFeedback;
import com.google.android.accessibility.talkback.compositor.GlobalVariables;
import com.google.android.accessibility.talkback.compositor.TalkBackFeedbackProvider;
import com.google.android.accessibility.talkback.compositor.WindowContentChangeAnnouncementFilter;
import com.google.android.accessibility.talkback.compositor.roledescription.RoleDescriptionExtractor;
import com.google.android.accessibility.talkback.compositor.roledescription.TreeNodesDescription;
import com.google.android.accessibility.utils.AccessibilityNodeInfoUtils;
import com.google.android.accessibility.utils.Role;
import com.google.android.libraries.accessibility.utils.log.LogUtils;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * Event feedback rules for {@link EVENT_TYPE_WINDOW_CONTENT_CHANGED} event. These rules will
 * provide the event feedback output function by inputting the {@link HandleEventOptions} and
 * outputting {@link EventFeedback}.
 */
public final class EventTypeWindowContentChangedFeedbackRule {

  private static final String TAG = "EventTypeWindowContentChangedFeedbackRule";

  /**
   * Adds the feedback rules to the provided event feedback rules map, {@code eventFeedbackRules}.
   * So {@link TalkBackFeedbackProvider} can populate the event feedback with the given event.
   *
   * @param eventFeedbackRules the event feedback rules
   * @param context the parent context
   * @param globalVariables the global compositor variables
   * @param roleDescriptionExtractor the node role description extractor
   * @param treeNodesDescription the node tree description extractor
   */
  public static void addFeedbackRule(
      Map<Integer, Function<HandleEventOptions, EventFeedback>> eventFeedbackRules,
      Context context,
      GlobalVariables globalVariables,
      RoleDescriptionExtractor roleDescriptionExtractor,
      TreeNodesDescription treeNodesDescription) {
    eventFeedbackRules.put(
        EVENT_TYPE_WINDOW_CONTENT_CHANGED,
        eventOptions ->
            windowContentChanged(
                eventOptions.eventObject,
                eventOptions.sourceNode,
                context,
                globalVariables,
                roleDescriptionExtractor,
                treeNodesDescription));
  }

  private static EventFeedback windowContentChanged(
      AccessibilityEvent event,
      AccessibilityNodeInfoCompat node,
      Context context,
      GlobalVariables globalVariables,
      RoleDescriptionExtractor roleDescriptionExtractor,
      TreeNodesDescription treeNodesDescription) {
    if (node == null) {
      LogUtils.e(TAG, "    windowContentChanged: error, node is null");
      return EMPTY_FEEDBACK;
    }

    Locale preferredLocale = globalVariables.getPreferredLocaleByNode(node);
    int eventSrcRole = Role.getSourceRole(event);
    int nodeId = node.hashCode();
    int nodeRole = Role.getRole(node);
    int nodeLiveRegion = node.getLiveRegion();
    boolean nodeNotFrequentAnnounced =
        WindowContentChangeAnnouncementFilter.shouldAnnounce(
            node, globalVariables.getTextChangeRateUnlimited());

    CharSequence ttsOutput;
    if (nodeLiveRegion != ACCESSIBILITY_LIVE_REGION_NONE) {
      ttsOutput =
          nodeNotFrequentAnnounced
              ? treeNodesDescription.aggregateNodeTreeDescription(node, event)
              : "";
    } else {
      ttsOutput =
          computeWindowContentChangedStateText(
              node,
              event,
              context,
              roleDescriptionExtractor,
              nodeNotFrequentAnnounced,
              preferredLocale);
    }

    boolean forcedFeedback = true;
    // List the event type that shouldn't be announced when microphone or SSB is active.
    if (TextUtils.isEmpty(ttsOutput)
        || event.getContentChangeTypes() == CONTENT_CHANGE_TYPE_CONTENT_DESCRIPTION
        || event.getContentChangeTypes() == CONTENT_CHANGE_TYPE_TEXT
        || (event.getContentChangeTypes() == CONTENT_CHANGE_TYPE_STATE_DESCRIPTION
            && Role.getRole(node) == Role.ROLE_PROGRESS_BAR)) {
      forcedFeedback = false;
    }

    LogUtils.v(
        TAG,
        "windowContentChanged: %s",
        new StringBuilder()
            .append(String.format("(%s) ", nodeId))
            .append(String.format(", ttsOutput= {%s}", ttsOutput))
            .append(String.format(", eventSrcRole=%s", Role.roleToString(eventSrcRole)))
            .append(String.format(", nodeRole=%s", Role.roleToString(nodeRole)))
            .append(String.format(", nodeLiveRegion=%s", nodeLiveRegion))
            .append(String.format(", nodeNotFrequentAnnounced=%s", nodeNotFrequentAnnounced))
            .toString());

    return EventFeedback.builder()
        .setTtsOutput(Optional.of(ttsOutput))
        .setQueueMode(queueMode(eventSrcRole, nodeLiveRegion))
        .setTtsClearQueueGroup(ttsClearQueueGroup(eventSrcRole, event.getContentChangeTypes()))
        .setTtsAddToHistory(true)
        .setTtsSkipDuplicate(true)
        .setForceFeedbackEvenIfAudioPlaybackActive(true)
        .setForceFeedbackEvenIfMicrophoneActive(forcedFeedback)
        .setForceFeedbackEvenIfSsbActive(forcedFeedback)
        .setEarcon(EarconFeedbackUtils.getProgressBarChangeEarcon(event, node, preferredLocale))
        .setEarconRate(EarconFeedbackUtils.getProgressBarChangeEarconRate(event, node))
        .setEarconVolume(EarconFeedbackUtils.getProgressBarChangeEarconVolume(event, node))
        .build();
  }

  private static CharSequence computeWindowContentChangedStateText(
      AccessibilityNodeInfoCompat node,
      AccessibilityEvent event,
      Context context,
      RoleDescriptionExtractor roleDescriptionExtractor,
      boolean nodeNotFrequentAnnounced,
      Locale preferredLocale) {
    int nodeRole = Role.getRole(node);
    int contentChangeType = event.getContentChangeTypes();
    boolean isSelfOrAncestorFocused = AccessibilityNodeInfoUtils.isSelfOrAncestorFocused(node);

    LogUtils.v(
        TAG,
        "  computeWindowContentChangedStateText: %s",
        new StringBuilder()
            .append(String.format("  contentChangeType=%s", contentChangeType))
            .append(String.format(", isSelfOrAncestorFocused=%s", isSelfOrAncestorFocused))
            .append(String.format(", nodeNotFrequentAnnounced=%s", nodeNotFrequentAnnounced))
            .toString());

    switch (contentChangeType) {
      case CONTENT_CHANGE_TYPE_CONTENT_DESCRIPTION:
        return (isSelfOrAncestorFocused && nodeNotFrequentAnnounced)
            ? AccessibilityEventFeedbackUtils.getEventContentDescription(event, preferredLocale)
            : "";
      case CONTENT_CHANGE_TYPE_TEXT:
        return isSelfOrAncestorFocused
                && nodeNotFrequentAnnounced
                && nodeRole != Role.ROLE_EDIT_TEXT
            ? AccessibilityNodeFeedbackUtils.getNodeText(node, context, preferredLocale)
            : "";
      case CONTENT_CHANGE_TYPE_STATE_DESCRIPTION:
        if (!isSelfOrAncestorFocused || !nodeNotFrequentAnnounced) {
          return "";
        }
        if (nodeRole == Role.ROLE_SEEK_CONTROL) {
          return roleDescriptionExtractor.getSeekBarStateDescription(node, event);
        }
        // Fallback to aggregate text.
        CharSequence aggregateText =
            AccessibilityEventFeedbackUtils.getEventAggregateText(event, preferredLocale);
        if (!TextUtils.isEmpty(aggregateText)) {
          return aggregateText;
        }
        // Fallback to seekBar description for progressBar role.
        // Android widget Progressbar used to send TYPE_VIEW_SELECTED event when
        // progress changes. It then adopted StateDescription. However, if a new
        // widget wants to provide the RangeInfo data only without state
        // description, we can use state change event to announce state changes
        // TODO: moves seekBarStateDescription together for seekBar and progressBar
        if (nodeRole == Role.ROLE_PROGRESS_BAR) {
          return roleDescriptionExtractor.getSeekBarStateDescription(node, event);
        }
        // Fallback to state description.
        return AccessibilityNodeFeedbackUtils.getNodeStateDescription(
            node, context, preferredLocale);
      case CONTENT_CHANGE_TYPE_DRAG_STARTED:
        return context.getString(R.string.drag_started);
      case CONTENT_CHANGE_TYPE_DRAG_DROPPED:
        return context.getString(R.string.drag_dropped);
      case CONTENT_CHANGE_TYPE_DRAG_CANCELLED:
        return context.getString(R.string.drag_cancelled);
      case CONTENT_CHANGE_TYPE_UNDEFINED:
      case CONTENT_CHANGE_TYPE_ERROR:
        // When an error should be displayed, send
        // AccessibilityEvent#CONTENT_CHANGE_TYPE_INVALID. At this point talkback should
        // indicate the presence of an error to the user. REFERTO.
        return AccessibilityNodeFeedbackUtils.getAccessibilityNodeErrorText(node, context);
      case CONTENT_CHANGE_TYPE_ENABLED:
        return AccessibilityNodeFeedbackUtils.getAccessibilityEnabledState(node, context);
      default:
        return "";
    }
  }

  private static int queueMode(int role, int nodeLiveRegion) {
    switch (role) {
      case Role.ROLE_PROGRESS_BAR:
        return nodeLiveRegion == ACCESSIBILITY_LIVE_REGION_ASSERTIVE
            ? QUEUE_MODE_INTERRUPT
            : QUEUE_MODE_UNINTERRUPTIBLE_BY_NEW_SPEECH;
      case Role.ROLE_SEEK_CONTROL:
        return QUEUE_MODE_UNINTERRUPTIBLE_BY_NEW_SPEECH;
      default:
        return nodeLiveRegion == ACCESSIBILITY_LIVE_REGION_ASSERTIVE
            ? QUEUE_MODE_INTERRUPT
            : QUEUE_MODE_QUEUE;
    }
  }

  private static int ttsClearQueueGroup(int role, int contentChangeType) {
    switch (role) {
      case Role.ROLE_PROGRESS_BAR:
        return UTTERANCE_GROUP_PROGRESS_BAR_PROGRESS;
      case Role.ROLE_SEEK_CONTROL:
        return UTTERANCE_GROUP_SEEK_PROGRESS;
      default:
        if (contentChangeType == CONTENT_CHANGE_TYPE_CONTENT_DESCRIPTION
            || contentChangeType == CONTENT_CHANGE_TYPE_TEXT
            || contentChangeType == CONTENT_CHANGE_TYPE_STATE_DESCRIPTION) {
          return UTTERANCE_GROUP_CONTENT_CHANGE;
        } else {
          return UTTERANCE_GROUP_DEFAULT;
        }
    }
  }

  private EventTypeWindowContentChangedFeedbackRule() {}
}
