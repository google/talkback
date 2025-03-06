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

import static androidx.core.view.accessibility.AccessibilityWindowInfoCompat.TYPE_INPUT_METHOD;
import static androidx.core.view.accessibility.AccessibilityWindowInfoCompat.TYPE_SYSTEM;
import static com.google.android.accessibility.talkback.compositor.Compositor.EVENT_TYPE_VIEW_ACCESSIBILITY_FOCUSED;
import static com.google.android.accessibility.talkback.compositor.CompositorUtils.PRUNE_EMPTY;
import static com.google.android.accessibility.talkback.compositor.TalkBackFeedbackProvider.EMPTY_FEEDBACK;
import static com.google.android.accessibility.utils.AccessibilityNodeInfoUtils.WINDOW_TYPE_PICTURE_IN_PICTURE;
import static com.google.android.accessibility.utils.output.SpeechController.QUEUE_MODE_FLUSH_ALL;
import static com.google.android.accessibility.utils.output.SpeechController.QUEUE_MODE_QUEUE;

import android.content.Context;
import android.text.TextUtils;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityWindowInfo;
import androidx.annotation.VisibleForTesting;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import com.google.android.accessibility.talkback.R;
import com.google.android.accessibility.talkback.compositor.AccessibilityEventFeedbackUtils;
import com.google.android.accessibility.talkback.compositor.AccessibilityFocusEventInterpretation;
import com.google.android.accessibility.talkback.compositor.AccessibilityInterpretationFeedbackUtils;
import com.google.android.accessibility.talkback.compositor.AccessibilityNodeFeedbackUtils;
import com.google.android.accessibility.talkback.compositor.Compositor.HandleEventOptions;
import com.google.android.accessibility.talkback.compositor.CompositorUtils;
import com.google.android.accessibility.talkback.compositor.EventFeedback;
import com.google.android.accessibility.talkback.compositor.GlobalVariables;
import com.google.android.accessibility.talkback.compositor.roledescription.TreeNodesDescription;
import com.google.android.accessibility.talkback.eventprocessor.ProcessorPhoneticLetters;
import com.google.android.accessibility.utils.AccessibilityNodeInfoUtils;
import com.google.android.accessibility.utils.Filter;
import com.google.android.accessibility.utils.ImageContents;
import com.google.android.accessibility.utils.Role;
import com.google.android.accessibility.utils.WebInterfaceUtils;
import com.google.android.libraries.accessibility.utils.log.LogUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * Event feedback rules for {@link EVENT_TYPE_VIEW_ACCESSIBILITY_FOCUSED} event. These rules will
 * provide the event feedback output function by inputting the {@link HandleEventOptions} and
 * outputting {@link EventFeedback}.
 */
public final class EventTypeViewAccessibilityFocusedFeedbackRule {

  private static final String TAG = "EventTypeViewAccessibilityFocusedFeedbackRule";

  @VisibleForTesting public static CharSequence currentContainerTitle = "";

  /**
   * Adds the feedback rules to the provided event feedback rules map, {@code eventFeedbackRules}.
   * So {@link TalkBackFeedbackProvider} can populate the event feedback with the given event.
   *
   * @param eventFeedbackRules the event feedback rules
   * @param context the parent context
   * @param imageContents the wrapper to get label or image caption result
   * @param globalVariables the global compositor variables
   * @param treeNodesDescription the node tree description extractor
   */
  public static void addFeedbackRule(
      Map<Integer, Function<HandleEventOptions, EventFeedback>> eventFeedbackRules,
      Context context,
      ImageContents imageContents,
      GlobalVariables globalVariables,
      ProcessorPhoneticLetters processorPhoneticLetters,
      TreeNodesDescription treeNodesDescription) {
    eventFeedbackRules.put(
        EVENT_TYPE_VIEW_ACCESSIBILITY_FOCUSED,
        eventOptions ->
            viewAccessibilityFocused(
                eventOptions,
                context,
                imageContents,
                globalVariables,
                processorPhoneticLetters,
                treeNodesDescription));
  }

  private static EventFeedback viewAccessibilityFocused(
      HandleEventOptions eventOptions,
      Context context,
      ImageContents imageContents,
      GlobalVariables globalVariables,
      ProcessorPhoneticLetters processorPhoneticLetters,
      TreeNodesDescription treeNodesDescription) {

    AccessibilityNodeInfoCompat srcNode = eventOptions.sourceNode;
    if (srcNode == null) {
      LogUtils.w(TAG, " viewAccessibilityFocused: has null source node.");
      return EMPTY_FEEDBACK;
    }

    AccessibilityFocusEventInterpretation accessibilityFocusEventInterpretation =
        AccessibilityInterpretationFeedbackUtils.safeAccessibilityFocusInterpretation(
            eventOptions.eventInterpretation);
    boolean isInitialFocus =
        accessibilityFocusEventInterpretation.getIsInitialFocusAfterScreenStateChange();
    boolean isEventNavigateByUser = accessibilityFocusEventInterpretation.getIsNavigateByUser();
    CharSequence ttsOutput =
        viewAccessibilityFocusedDescription(
            eventOptions.eventObject,
            srcNode,
            isEventNavigateByUser,
            context,
            imageContents,
            globalVariables,
            processorPhoneticLetters,
            treeNodesDescription);

    LogUtils.v(
        TAG,
        " viewAccessibilityFocused: %s,",
        new StringBuilder()
            .append(String.format("(%s) ", srcNode.hashCode()))
            .append(String.format(", ttsOutput={%s}", ttsOutput))
            .append(String.format(", isInitialFocus=%s", isInitialFocus))
            .append(String.format(", isEventNavigateByUser=%s", isEventNavigateByUser))
            .toString());

    return EventFeedback.builder()
        .setTtsOutput(Optional.of(ttsOutput))
        .setQueueMode(queueMode(isInitialFocus))
        .setTtsAddToHistory(true)
        .setAdvanceContinuousReading(true)
        .setForceFeedbackEvenIfAudioPlaybackActive(
            accessibilityFocusEventInterpretation.getForceFeedbackEvenIfAudioPlaybackActive())
        .setForceFeedbackEvenIfMicrophoneActive(
            forceFeedbackEvenIfMicrophoneActive(
                accessibilityFocusEventInterpretation, isInitialFocus))
        .setForceFeedbackEvenIfSsbActive(
            forceFeedbackEvenIfSsbActive(accessibilityFocusEventInterpretation, isInitialFocus))
        .setPreventDeviceSleep(true)
        .setEarcon(earcon(srcNode, globalVariables))
        .setHaptic(haptic(srcNode))
        .build();
  }

  /**
   * Returns TTS feedback text for {@link EVENT_TYPE_VIEW_ACCESSIBILITY_FOCUSED} event.
   *
   * <ul>
   *   The feedback text is composed of below elements:
   *   <li>1. Unlabelled description or Node tree description or Event description,
   *   <li>2. Collection item transition or Node role/heading description,
   *   <li>3. Collection transition, or Container transition,
   *   <li>4. Window transition,
   * </ul>
   */
  private static CharSequence viewAccessibilityFocusedDescription(
      AccessibilityEvent event,
      AccessibilityNodeInfoCompat node,
      boolean isEventNavigateByUser,
      Context context,
      ImageContents imageContents,
      GlobalVariables globalVariables,
      ProcessorPhoneticLetters processorPhoneticLetters,
      TreeNodesDescription treeNodesDescription) {
    StringBuilder logString = new StringBuilder();
    List<CharSequence> outputJoinList = new ArrayList<>();
    Locale preferredLocale = globalVariables.getPreferredLocaleByNode(node);

    // Prepare Unlabelled description or Node tree description or Event description for feedback.
    CharSequence nodeUnlabelledState =
        AccessibilityNodeFeedbackUtils.getUnlabelledNodeDescription(
            Role.getRole(node), node, context, imageContents, globalVariables);
    CharSequence eventDescription =
        AccessibilityEventFeedbackUtils.getEventContentDescriptionOrEventAggregateText(
            event, preferredLocale);
    if (!TextUtils.isEmpty(nodeUnlabelledState)) {
      CharSequence unlabelledDescription =
          TextUtils.isEmpty(eventDescription) ? nodeUnlabelledState : eventDescription;
      outputJoinList.add(unlabelledDescription);
      logString
          .append(String.format("\n    unlabelledDescription={%s}", unlabelledDescription))
          .append(String.format(", eventDescription={%s}", eventDescription));
    } else {
      CharSequence nodeTreeDescription =
          treeNodesDescription.aggregateNodeTreeDescription(node, event);
      if (!TextUtils.isEmpty(nodeTreeDescription)) {
        outputJoinList.add(nodeTreeDescription);
        logString.append(String.format("\n    nodeTreeDescription={%s}", nodeTreeDescription));
      } else {
        outputJoinList.add(eventDescription);
        logString.append(String.format("\n    eventDescription={%s}", eventDescription));
      }
    }

    // Add phonetic spelling if necessary.
    Optional<CharSequence> phoneticExample =
        processorPhoneticLetters.getPhoneticLetterForKeyboardFocusEvent(event);
    phoneticExample.ifPresent(outputJoinList::add);
    logString.append(String.format("\n    phoneticExample={%s}", phoneticExample));

    // Prepare Collection item transition state or Node role/heading description for feedback.
    boolean speakCollectionInfo = globalVariables.getSpeakCollectionInfo();
    boolean speakRoles = globalVariables.getSpeakRoles();
    logString
        .append(String.format("\n Verbosity speakCollectionInfo=%s", speakCollectionInfo))
        .append(String.format(", speakRoles=%s", speakRoles));
    CharSequence collectionItemTransition =
        speakCollectionInfo ? globalVariables.getCollectionItemTransitionDescription(node) : "";
    if (!TextUtils.isEmpty(collectionItemTransition)) {
      outputJoinList.add(collectionItemTransition);
      logString.append(
          String.format("\n    collectionItemTransition={%s}", collectionItemTransition));
    } else if (speakRoles
        && !WebInterfaceUtils.isWebContainer(node)
        && AccessibilityNodeInfoUtils.isHeading(node)) {
      // If the source node has collection item transition, collectionItemTransition text would
      // not be empty. And TalkBack should announce the collection item transition information or it
      // should fallback to announce the role/heading description.
      CharSequence nodeRoleDescription =
          AccessibilityNodeFeedbackUtils.getNodeRoleDescription(node, context, globalVariables);
      if (!TextUtils.isEmpty(nodeRoleDescription)) {
        outputJoinList.add(nodeRoleDescription);
        logString.append(String.format("\n    nodeRoleDescription={%s}", nodeRoleDescription));
      } else {
        outputJoinList.add(context.getString(R.string.heading_template));
        logString.append("\n    heading");
      }
    }

    // Prepare collection transition state if the collection transition happened for feedback.
    if (speakCollectionInfo) {
      CharSequence collectionTransition = globalVariables.getCollectionTransitionDescription();
      if (!TextUtils.isEmpty(collectionTransition)) {
        outputJoinList.add(collectionTransition);
        logString.append(String.format("\n    collectionTransition={%s}", collectionTransition));
      } else {
        // Prepare container transition state for feedback.
        CharSequence containerTransition = containerTransitionState(node, context);
        if (!TextUtils.isEmpty(containerTransition)) {
          outputJoinList.add(containerTransition);
          logString.append(String.format("\n    containerTransition={%s}", containerTransition));
        }
      }
    }

    // Prepare window transition state if the window transition happened for feedback.
    CharSequence windowTransition =
        windowTransitionState(isEventNavigateByUser, node, context, globalVariables);
    if (!TextUtils.isEmpty(windowTransition)) {
      outputJoinList.add(windowTransition);
      logString.append(String.format("\n    windowTransition={%s}", windowTransition));
    }

    LogUtils.v(TAG, "viewAccessibilityFocusedDescription: %s", logString.toString());

    return CompositorUtils.joinCharSequences(
        outputJoinList, CompositorUtils.getSeparator(), PRUNE_EMPTY);
  }

  /**
   * Returns the window transition state text if the window transition happened.
   *
   * <p>Note: When the accessibility focus is moved to another window, Talkback reads the window
   * title. However, if the accessibility focus is automatically moved by the Enhanced focus
   * function by changing to split screen mode, the window title is not read. So user can not know
   * the window which the accessibility focus is located. Even, if it is not navigation by user, the
   * title should be read in split screen mode.
   */
  private static CharSequence windowTransitionState(
      boolean isEventNavigateByUser,
      AccessibilityNodeInfoCompat node,
      Context context,
      GlobalVariables globalVariables) {
    boolean isSplitScreenMode = globalVariables.isSplitScreenMode();
    boolean isWindowIdChanged = globalVariables.getLastWindowId() != node.getWindowId();

    StringBuilder logString = new StringBuilder();
    logString
        .append(String.format("isWindowIdChanged=%s", isWindowIdChanged))
        .append(String.format(", isSplitScreenMode=%s", isSplitScreenMode))
        .append(String.format(", isEventNavigateByUser=%s", isEventNavigateByUser));

    // Provide window transition state feedback if the event is triggered by user interaction or it
    // is in split screen mode.
    CharSequence windowTransitionState;
    if (isWindowIdChanged && (isEventNavigateByUser || isSplitScreenMode)) {
      int windowType = AccessibilityNodeInfoUtils.getWindowType(node);
      CharSequence currentWindowTitle =
          globalVariables.getWindowTitle(globalVariables.getCurrentWindowId());
      boolean speakSystemWindowTitles = globalVariables.getSpeakSystemWindowTitles();

      logString
          .append(String.format(", windowType=%s", windowType))
          .append(String.format(", currentWindowTitle=%s", currentWindowTitle))
          .append(String.format(", speakSystemWindowTitles=%s", speakSystemWindowTitles));

      if (windowType == WINDOW_TYPE_PICTURE_IN_PICTURE) {
        logString.append(", hasWindowTransition for PIP window");
        windowTransitionState =
            context.getString(com.google.android.accessibility.utils.R.string.template_overlay_window, currentWindowTitle);
      } else if (speakSystemWindowTitles
          || (windowType != TYPE_SYSTEM && windowType != TYPE_INPUT_METHOD)) {
        logString.append(", hasWindowTransition for speakWindowTitles");
        windowTransitionState = context.getString(R.string.in_window_with_name, currentWindowTitle);
      } else {
        logString.append(String.format(", feedback not granted"));
        windowTransitionState = "";
      }
    } else {
      windowTransitionState = "";
    }

    LogUtils.v(TAG, "windowTransitionState: %s", logString.toString());
    return windowTransitionState;
  }

  /** Returns the container transition state text if the container transition happened. */
  private static CharSequence containerTransitionState(
      AccessibilityNodeInfoCompat node, Context context) {
    CharSequence lastContainerTitle = currentContainerTitle;
    // Get container title from self or ancestor node.
    AccessibilityNodeInfoCompat containerNode =
        AccessibilityNodeInfoUtils.getSelfOrMatchingAncestor(
            node, Filter.node((n) -> (n != null) && !TextUtils.isEmpty(n.getContainerTitle())));
    currentContainerTitle = containerNode == null ? "" : containerNode.getContainerTitle();
    if (TextUtils.equals(lastContainerTitle, currentContainerTitle)) {
      return "";
    }
    if (!TextUtils.isEmpty(currentContainerTitle)) {
      return context.getString(R.string.in_collection_role_description, currentContainerTitle);
    } else if (!TextUtils.isEmpty(lastContainerTitle)) {
      return context.getString(R.string.out_of_role_description, lastContainerTitle);
    }
    return "";
  }

  private static int queueMode(boolean isInitialFocus) {
    return isInitialFocus ? QUEUE_MODE_QUEUE : QUEUE_MODE_FLUSH_ALL;
  }

  private static boolean forceFeedbackEvenIfMicrophoneActive(
      AccessibilityFocusEventInterpretation accessibilityFocusEventInterpretation,
      boolean isInitialFocus) {
    return !isInitialFocus
        && accessibilityFocusEventInterpretation.getForceFeedbackEvenIfMicrophoneActive();
  }

  private static boolean forceFeedbackEvenIfSsbActive(
      AccessibilityFocusEventInterpretation accessibilityFocusEventInterpretation,
      boolean isInitialFocus) {
    return !isInitialFocus
        && accessibilityFocusEventInterpretation.getForceFeedbackEvenIfSsbActive();
  }

  private static int earcon(AccessibilityNodeInfoCompat node, GlobalVariables globalVariables) {
    if (AccessibilityNodeInfoUtils.getWindowType(node)
            == AccessibilityWindowInfo.TYPE_SPLIT_SCREEN_DIVIDER
        && globalVariables.getLastWindowId() != node.getWindowId()) {
      return R.raw.complete;
    }

    if (globalVariables.lastFocusInScrollableNode()
        != globalVariables.currentFocusInScrollableNode()) {
      return globalVariables.currentFocusInScrollableNode() ? R.raw.chime_up : R.raw.chime_down;
    } else {
      return AccessibilityNodeInfoUtils.isActionableForAccessibility(node)
          ? R.raw.focus_actionable
          : R.raw.focus;
    }
  }

  private static int haptic(AccessibilityNodeInfoCompat node) {
    return AccessibilityNodeInfoUtils.isActionableForAccessibility(node)
        ? R.array.view_actionable_pattern
        : R.array.view_hovered_pattern;
  }

  private EventTypeViewAccessibilityFocusedFeedbackRule() {}
}
