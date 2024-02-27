/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.google.android.accessibility.talkback;

import static com.google.android.accessibility.talkback.Feedback.EditText.Action.MOVE_CURSOR;

import android.text.TextUtils;
import android.view.accessibility.AccessibilityNodeInfo;
import androidx.annotation.Nullable;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import com.google.android.accessibility.talkback.GranularityIterator.TextSegmentIterator;
import com.google.android.accessibility.talkback.Pipeline.SyntheticEvent;
import com.google.android.accessibility.talkback.eventprocessor.ProcessorPhoneticLetters;
import com.google.android.accessibility.utils.AccessibilityNodeInfoUtils;
import com.google.android.accessibility.utils.FeatureSupport;
import com.google.android.accessibility.utils.PackageManagerUtils;
import com.google.android.accessibility.utils.Performance.EventId;
import com.google.android.accessibility.utils.Role;
import com.google.android.accessibility.utils.WebInterfaceUtils;
import com.google.android.accessibility.utils.input.CursorGranularity;
import com.google.android.accessibility.utils.input.TextEventInterpreter;
import com.google.android.libraries.accessibility.utils.log.LogUtils;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Performs granularity traversal within Talkback. Supports only character, word and paragraph
 * granularity.
 */
public final class GranularityTraversal {
  /**
   * Granularities supported within talkback if the conditions in {@link
   * GranularityTraversal#shouldHandleGranularityTraversalInTalkback(AccessibilityNodeInfoCompat)}
   * are satisfied.
   */
  static final int TALKBACK_SUPPORTED_GRANULARITIES =
      AccessibilityNodeInfo.MOVEMENT_GRANULARITY_CHARACTER
          | AccessibilityNodeInfo.MOVEMENT_GRANULARITY_WORD
          | AccessibilityNodeInfo.MOVEMENT_GRANULARITY_PARAGRAPH;

  private static final int ACCESSIBILITY_CURSOR_POSITION_UNDEFINED = -1;
  private static final String TAG = "GranularityTraversal";

  private final ProcessorPhoneticLetters processorPhoneticLetters;
  private Optional<Pipeline.FeedbackReturner> pipelineReturner = Optional.empty();
  private Optional<Pipeline.EventReceiver> pipelineReceiver = Optional.empty();

  /**
   * Manages the cursor position for each node while traversing with granularity within Talkback.
   */
  private final Map<AccessibilityNodeInfoCompat, Integer> cache = new ConcurrentHashMap<>();

  GranularityTraversal(ProcessorPhoneticLetters processorPhoneticLetters) {
    this.processorPhoneticLetters = processorPhoneticLetters;
  }

  public void setPipelineFeedbackReturner(Pipeline.FeedbackReturner pipeline) {
    processorPhoneticLetters.setPipeline(pipeline);
    pipelineReturner = Optional.of(pipeline);
  }

  public void setPipelineEventReceiver(Pipeline.EventReceiver pipeline) {
    pipelineReceiver = Optional.of(pipeline);
  }

  /**
   * Regulates the storage in the cache. Call it regularly to avoid overpopulating the cache.
   * GranularityManager#clear() calls this to make sure the cache gets cleared regularly.
   */
  void clearAllCursors() {
    cache.clear();
  }

  /**
   * Returns {@code true} if talkback should handle granularity traversal.
   *
   * <p>Following cases are handled within Talkback:
   *
   * <ul>
   *   <li>Views with content description.
   *   <li>Edit texts with no input focus.
   *   <li>Edit texts with input focus and a non-active keyboard.
   * </ul>
   *
   * <p>Cases not handled by Talkback and rely on framework:
   *
   * <ul>
   *   <li>Views with no content description.
   *   <li>Edit texts which have input focus with an active keyboard.
   *   <li>Webviews.
   *   <li>View with selectable text that aren't edit texts.
   * </ul>
   */
  static boolean shouldHandleGranularityTraversalInTalkback(AccessibilityNodeInfoCompat node) {

    // Webviews are handled by the framework.
    if (WebInterfaceUtils.isWebContainer(node)) {
      LogUtils.v(TAG, "Granularity traversal not handled by Talkback since its a webview");
      return false;
    }
    // Edit texts that have input focus or non-editable selectable texts are handled by the
    // framework.
    if ((Role.getRole(node) == Role.ROLE_EDIT_TEXT) && node.isFocused()
        || AccessibilityNodeInfoUtils.isNonEditableSelectableText(node)) {
      LogUtils.v(TAG, "Granularity traversal not handled by Talkback as node is focused");
      return false;
    }

    if (TextUtils.isEmpty(getIterableTextForAccessibility(node))) {
      LogUtils.v(
          TAG,
          "Granularity traversal not handled by Talkback as iterable text is null or empty string");
      return false;
    }
    LogUtils.d(TAG, "Granularity traversal handled by Talkback");
    return true;
  }

  /**
   * Handles granularity traversal by Talkback.
   *
   * @param node at which granularity traversal is to be performed.
   * @param granularity the user preferred granularity.
   * @param forward {@code true} if move has to be in the forward direction or else {@code false}.
   * @param eventId ID of the event used for performance monitoring.
   * @return {@code true} if traversal was successful or else {@code false}.
   */
  public boolean traverseAtGranularity(
      AccessibilityNodeInfoCompat node, int granularity, boolean forward, EventId eventId) {

    CharSequence text = getIterableTextForAccessibility(node);
    LogUtils.d(TAG, "Text to be traversed: " + text);
    if (text == null || text.length() == 0) {
      return false;
    }
    TextSegmentIterator iterator = getIteratorForGranularity(node, granularity);
    if (iterator == null) {
      LogUtils.v(TAG, "Iterator for granularity traversal is null.");
      return false;
    }
    int current = getCursorPosition(node);
    if (current == ACCESSIBILITY_CURSOR_POSITION_UNDEFINED) {
      current = forward ? 0 : text.length();
    }
    final int[] range = forward ? iterator.following(current) : iterator.preceding(current);
    if (range == null) {
      return false;
    }

    int segmentStart = range[0];
    int segmentEnd = range[1];
    LogUtils.v(TAG, "Text traversal segmentStart: " + segmentStart);
    LogUtils.v(TAG, "Text traversal segmentEnd: " + segmentEnd);

    setAccessibilityCursor(node, segmentEnd, segmentStart, text.length(), forward);

    sendViewTextTraversedAtGranularityEvent(
        segmentStart, segmentEnd, text, granularity, node, eventId);
    return true;
  }

  /**
   * Returns {@code true} if TalkBack should handle line granularity traversal.
   *
   * <p>Only line granularity traversal at {@link android.widget.EditText} is handled by TalkBack.
   *
   * @param forward {@code true} if move has to be in the forward direction.
   */
  public static boolean shouldHandleLineGranularityTraversalInTalkback(
      AccessibilityNodeInfoCompat node, boolean forward) {
    // WebViews and non-EditText are handled by the framework.
    if (WebInterfaceUtils.isWebContainer(node) || Role.getRole(node) != Role.ROLE_EDIT_TEXT) {
      LogUtils.v(
          TAG,
          "Line granularity traversal not handled by Talkback since it is a WebView or not an"
              + " EditText");
      return false;
    }

    if (TextUtils.isEmpty(getIterableTextForAccessibility(node))) {
      LogUtils.v(
          TAG,
          "Line granularity traversal not handled by Talkback as iterable text is null or empty"
              + " string");
      return false;
    }

    int current = forward ? node.getTextSelectionEnd() : node.getTextSelectionStart();
    if (current == ACCESSIBILITY_CURSOR_POSITION_UNDEFINED) {
      LogUtils.v(TAG, "Line granularity traversal not handled by Talkback since no cursor");
      return false;
    }

    LogUtils.d(TAG, "Line granularity traversal handled by Talkback");
    return true;
  }

  /**
   * Handles line granularity traversal by Talkback.
   *
   * @param node at which line granularity traversal is to be performed.
   * @param forward {@code true} if move has to be in the forward direction.
   * @param eventId ID of the event used for performance monitoring.
   * @return {@code true} if traversal was successful.
   */
  public boolean traverseAtLineGranularity(
      AccessibilityNodeInfoCompat node, boolean forward, EventId eventId) {
    if (!node.refresh()) {
      return false;
    }

    CharSequence text = getIterableTextForAccessibility(node);
    LogUtils.d(TAG, "Text to be traversed: " + text);
    if (TextUtils.isEmpty(text)) {
      return false;
    }
    int current;
    if (FeatureSupport.supportInputConnectionByA11yService()) {
      current = node.getTextSelectionEnd();
    } else {
      current = forward ? node.getTextSelectionEnd() : node.getTextSelectionStart();
    }

    TextSegmentIterator iterator = GranularityIterator.getLineIterator(node, text);
    int[] range = forward ? iterator.following(current) : iterator.preceding(current);
    if (range == null) {
      return false;
    }

    int segmentStart = range[0];
    int segmentEnd = range[1];
    LogUtils.v(TAG, "Text traversal segmentStart: " + segmentStart);
    LogUtils.v(TAG, "Text traversal segmentEnd: " + segmentEnd);

    pipelineReturner.ifPresent(
        feedbackReturner ->
            feedbackReturner.returnFeedback(
                Feedback.create(
                    eventId,
                    Feedback.part()
                        .setEdit(
                            Feedback.edit(node, MOVE_CURSOR)
                                .setCursorIndex(forward ? segmentEnd : segmentStart)
                                .build())
                        .build())));
    sendViewTextTraversedAtGranularityEvent(
        segmentStart, segmentEnd, text, CursorGranularity.LINE.value, node, eventId);
    return true;
  }

  /**
   * Gets the iterator for granularity traversal. Talkback can handle only character, word and
   * paragraph granularity movements. If the granularity is not supported by Talkback or the text is
   * null, it returns {@code null}.
   *
   * @param node on which granularity traversal has to be performed.
   * @param granularity that has been requested by the user.
   * @return the iterator for text traversal or {@code null}.
   */
  @Nullable
  private static TextSegmentIterator getIteratorForGranularity(
      AccessibilityNodeInfoCompat node, int granularity) {

    CharSequence text = getIterableTextForAccessibility(node);
    if (TextUtils.isEmpty(text)) {
      LogUtils.v(TAG, "Iterator is null as the text is an empty string or null.");
      return null;
    }

    return GranularityIterator.getIteratorForGranularity(text, granularity);
  }

  /**
   * Gets the text for granularity traversal. For edit texts, it first checks for text and then the
   * hint text and then the content description.
   *
   * <p><strong>Note:</strong> For edit texts with no text and just hint text, getText() returns the
   * hint text.
   *
   * @param node on which granularity traversal has to be performed.
   * @return the content description or the text/hint text in case of edit fields, since navigation
   *     on these is not handled by the framework.
   */
  public static CharSequence getIterableTextForAccessibility(AccessibilityNodeInfoCompat node) {
    @Nullable CharSequence nodeText = AccessibilityNodeInfoUtils.getText(node);
    if (AccessibilityNodeInfoUtils.isTextSelectable(node) && !TextUtils.isEmpty(nodeText)) {
      return nodeText;
    }

    return node.getContentDescription();
  }

  private int getCursorPosition(AccessibilityNodeInfoCompat node) {
    if (cache.containsKey(node)) {
      return cache.get(node);
    }
    return ACCESSIBILITY_CURSOR_POSITION_UNDEFINED;
  }

  /** Stores the cursor position for each node in the map. */
  private void setCursorPosition(AccessibilityNodeInfoCompat node, int index) {
    cache.put(node, index);
  }

  /** Sets the cursor position for granularity traversal */
  private void setAccessibilityCursor(
      AccessibilityNodeInfoCompat node, int end, int start, int length, boolean forward) {
    if (forward && end <= length) {
      LogUtils.v(TAG, "Setting accessibility cursor at end position: " + end);
      setCursorPosition(node, end);
    } else if (!forward && start >= 0) {
      LogUtils.v(TAG, "Setting accessibility cursor at start position: " + start);
      setCursorPosition(node, start);
    }
  }

  /** Creates and sends the traversal event to the compositor */
  private void sendViewTextTraversedAtGranularityEvent(
      int fromIndex,
      int toIndex,
      CharSequence text,
      int granularity,
      AccessibilityNodeInfoCompat node,
      EventId eventId) {

    CharSequence traversedText =
        TextEventInterpreter.getSubsequenceWithSpans(
            text, Math.min(fromIndex, toIndex), Math.max(fromIndex, toIndex));
    if (pipelineReceiver.isPresent()) {
      LogUtils.d(TAG, "sendViewTextTraversedAtGranularityEvent: " + traversedText);
      pipelineReceiver.get().input(SyntheticEvent.Type.TEXT_TRAVERSAL, traversedText);
    }

    // No current case where isTalkbackPackage would be true, but just kept it for safety.
    boolean isTalkbackPackage = PackageManagerUtils.isTalkBackPackage(node.getPackageName());
    boolean isCharacterGranularity = (granularity == CursorGranularity.CHARACTER.value);
    if (isCharacterGranularity) {
      processorPhoneticLetters.speakPhoneticLetterForTraversedText(
          isTalkbackPackage, traversedText.toString(), eventId);
    }
  }
}
