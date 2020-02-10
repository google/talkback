/*
 * Copyright (C) 2018 Google Inc.
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
package com.google.android.accessibility.switchaccess.utils;

import android.accessibilityservice.AccessibilityService;
import android.os.Bundle;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import android.view.accessibility.AccessibilityNodeInfo;
import com.google.android.accessibility.switchaccess.SwitchAccessActionTimeline;
import com.google.android.accessibility.switchaccess.SwitchAccessNodeCompat;
import com.google.android.accessibility.utils.PerformActionUtils;
import com.google.android.libraries.accessibility.utils.undo.UndoRedoManager;
import com.google.android.libraries.accessibility.utils.undo.UndoRedoManager.RecycleBehavior;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Utility class for handling text editing actions. */
public class TextEditingUtils {

  /**
   * The built-in movement actions, used to ensure only one of these two is passed into certain
   * methods.
   */
  public enum MovementDirection {
    DIRECTION_NEXT,
    DIRECTION_PREVIOUS
  }

  // Arbitrary numbers for custom granularities to distinguish them from the others.
  public static final int ACTION_GRANULARITY_SENTENCE = Integer.MAX_VALUE;
  public static final int ACTION_GRANULARITY_ALL = Integer.MAX_VALUE - 1;
  public static final int ACTION_GRANULARITY_HIGHLIGHT = Integer.MAX_VALUE - 2;

  // Begin custom actions at max value to avoid conflicts with built-in actions which start from the
  // beginning.
  public static final int ACTION_DELETE_TEXT = Integer.MAX_VALUE;
  public static final int ACTION_UNDO = Integer.MAX_VALUE - 1;
  public static final int ACTION_REDO = Integer.MAX_VALUE - 2;

  // All supported action granularities.
  public static final int[] MOVEMENT_GRANULARITIES_ONE_LINE = {
    AccessibilityNodeInfoCompat.MOVEMENT_GRANULARITY_CHARACTER,
    AccessibilityNodeInfoCompat.MOVEMENT_GRANULARITY_WORD
  };
  public static final int[] MOVEMENT_GRANULARITIES_MULTILINE = {
    AccessibilityNodeInfoCompat.MOVEMENT_GRANULARITY_CHARACTER,
    AccessibilityNodeInfoCompat.MOVEMENT_GRANULARITY_WORD,
    AccessibilityNodeInfoCompat.MOVEMENT_GRANULARITY_LINE,
    AccessibilityNodeInfoCompat.MOVEMENT_GRANULARITY_PAGE,
    AccessibilityNodeInfoCompat.MOVEMENT_GRANULARITY_PARAGRAPH
  };

  // The padding to use on the left and right sides of the highlight around an EditText so that the
  // cursor isn't obscured by the highlight when it's at the far left or right side of the box. This
  // is used only for text boxes and not others because it would look bad in most other places. For
  // example, the keyboard rects would all overlap, and full screen rects would disappear past the
  // edges of the screen.
  public static final int EDIT_TEXT_HORIZONTAL_PADDING_PX = 25;

  // A string with a sentence has at least one punctuation mark followed by some whitespace or the
  // end of the string.
  private static final Pattern SENTENCE_PATTERN = Pattern.compile("[.?!]+(\\p{Space}+|$)");

  /**
   * Checks the input {@link CharSequence} to see if it contains a sentence.
   *
   * @param text The {@link CharSequence} to check
   * @return {@code true} if the input is not null and contains a sentence
   */
  public static boolean containsSentence(CharSequence text) {
    if (text == null) {
      return false;
    }

    Matcher sentenceMatcher = SENTENCE_PATTERN.matcher(text);
    return sentenceMatcher.find();
  }

  /**
   * Checks the input {@link AccessibilityNodeInfoCompat} to see if it contains selected text.
   *
   * @param nodeCompat The {@link AccessibilityNodeInfoCompat} to check
   * @return {@code true} if the input contains text and some part of the text is selected
   */
  public static boolean isTextSelected(AccessibilityNodeInfoCompat nodeCompat) {
    return nodeCompat.getText() != null
        && nodeCompat.getTextSelectionEnd() != nodeCompat.getTextSelectionStart();
  }

  /*
   * Returns the index of the end of the last sentence before the given index.
   *
   * @param text The {@link CharSequence} to check
   * @param index The index of the input {@link CharSequence} to check against
   * @return The index of the end of the last sentence before the given index
   */
  private static int getEndOfLastSentenceBeforeIndex(CharSequence text, int index) {
    Matcher matcher = SENTENCE_PATTERN.matcher(text);
    int match = 0;
    while (matcher.find() && matcher.end() < index) {
      match = matcher.end();
    }
    return match;
  }

  /*
   * Returns the index of the end of the next sentence after the given index.
   *
   * @param text The {@link CharSequence} to check
   * @param index The index of the input {@link CharSequence} to check against
   * @return The index of the end of the next sentence after the given index
   */
  private static int getEndOfNextSentenceAfterIndex(CharSequence text, int index) {
    Matcher matcher = SENTENCE_PATTERN.matcher(text);
    return matcher.find(index) ? matcher.end() : text.length();
  }

  /**
   * Moves the cursor forward or backward in the text by one sentence in the given {@link
   * AccessibilityNodeInfoCompat}. The movement direction depends on the given actionId.
   *
   * @param nodeCompat The {@link AccessibilityNodeInfoCompat} in which to move the cursor
   * @param direction A {@link MovementDirection} indicating the movement direction
   * @return {@code true} if this movement is successful
   */
  public static boolean moveCursorBySentenceGranularity(
      AccessibilityNodeInfoCompat nodeCompat, MovementDirection direction) {
    CharSequence text = nodeCompat.getText();
    int currentCursorPosition = nodeCompat.getTextSelectionEnd();
    int newCursorPosition = 0;
    switch (direction) {
      case DIRECTION_PREVIOUS:
        newCursorPosition = getEndOfLastSentenceBeforeIndex(text, currentCursorPosition);
        break;
      case DIRECTION_NEXT:
        newCursorPosition = getEndOfNextSentenceAfterIndex(text, currentCursorPosition);
        break;
    }
    return selectText(nodeCompat, newCursorPosition, newCursorPosition);
  }

  /**
   * Deletes text in the given {@link AccessibilityNodeInfoCompat} with the granularity indicated in
   * the given {@link Bundle}.
   *
   * @param nodeCompat The {@link AccessibilityNodeInfo} containing the text to delete
   * @param arguments The {@link Bundle} containing the granularity arguments for deletion
   * @return {@code true} if the deletion is successful
   */
  public static boolean deleteTextWithGranularity(
      AccessibilityNodeInfoCompat nodeCompat, Bundle arguments) {
    if (arguments == Bundle.EMPTY) {
      return false;
    }

    nodeCompat.refresh();
    CharSequence text = nodeCompat.getText();

    // Find the bounds of the section of text to delete.
    int deleteSectionEnd = nodeCompat.getTextSelectionEnd();
    int deleteSectionStart;
    int deleteGranularity =
        arguments.getInt(AccessibilityNodeInfo.ACTION_ARGUMENT_MOVEMENT_GRANULARITY_INT);
    if (deleteGranularity == ACTION_GRANULARITY_SENTENCE) {
      deleteSectionStart = getEndOfLastSentenceBeforeIndex(text, deleteSectionEnd);
    } else if (deleteGranularity == ACTION_GRANULARITY_HIGHLIGHT) {
      deleteSectionStart = nodeCompat.getTextSelectionStart();
    } else {
      if (!PerformActionUtils.performAction(
          nodeCompat,
          AccessibilityNodeInfo.ACTION_PREVIOUS_AT_MOVEMENT_GRANULARITY,
          arguments,
          null)) {
        return false;
      }
      nodeCompat.refresh();
      deleteSectionStart = nodeCompat.getTextSelectionEnd();
    }
    int deleteSectionLowerIndex = Math.min(deleteSectionStart, deleteSectionEnd);
    int deleteSectionUpperIndex = Math.max(deleteSectionStart, deleteSectionEnd);

    // Set text to be the entire existing text minus the section to delete.
    String oldText = (text == null) ? "" : text.toString();
    String newText =
        oldText.substring(0, deleteSectionLowerIndex) + oldText.substring(deleteSectionUpperIndex);
    arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, newText);
    if (!PerformActionUtils.performAction(
        nodeCompat, AccessibilityNodeInfo.ACTION_SET_TEXT, arguments, null)) {
      return false;
    }
    nodeCompat.refresh();

    // Place the cursor back where it was before the deletion.
    int endOfText = (text == null) ? 0 : text.length();
    int newCursorPosition = Math.min(deleteSectionLowerIndex, endOfText);
    return selectText(nodeCompat, newCursorPosition, newCursorPosition);
  }

  /**
   * Select the text in the given {@link AccessibilityNodeInfoCompat} with the granularity indicated
   * in the given {@link Bundle}.
   *
   * @param nodeCompat The {@link AccessibilityNodeInfoCompat} to select text in
   * @param arguments The {@link Bundle} containing either (a) the granularity argument for
   *     selection or (b) the explicit start and end selection indices
   * @return {@code true} if the selection is successful
   */
  public static boolean selectTextWithGranularity(
      AccessibilityNodeInfoCompat nodeCompat, Bundle arguments) {
    if (arguments == Bundle.EMPTY) {
      return false;
    }
    nodeCompat.refresh();
    CharSequence text = nodeCompat.getText();
    final int noGranularityPresent = -1;
    switch (arguments.getInt(
        AccessibilityNodeInfo.ACTION_ARGUMENT_MOVEMENT_GRANULARITY_INT, noGranularityPresent)) {
      case ACTION_GRANULARITY_ALL:
        return selectText(nodeCompat, 0, text.length());
      case ACTION_GRANULARITY_SENTENCE:
        return extendSelectionBackOneSentence(nodeCompat);
      case noGranularityPresent:
        // Select text based on the explicit selection start and end boundaries.
        int noSelectionIndexPresent = -1;
        int selectionStart =
            arguments.getInt(
                AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, noSelectionIndexPresent);
        int selectionEnd =
            arguments.getInt(
                AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, noSelectionIndexPresent);
        return (selectionStart != noSelectionIndexPresent)
            && (selectionEnd != noSelectionIndexPresent)
            && selectText(nodeCompat, selectionStart, selectionEnd);
      default:
        int currentSelectionEnd =
            Math.max(nodeCompat.getTextSelectionEnd(), nodeCompat.getTextSelectionStart());
        // If text is already selected, extend the selection. To do this, first move the cursor to
        // the beginning of the current selection. (When text is selected, and a previous movement
        // action is performed, the cursor moves by granularity from the end of the current
        // selection.)
        if (isTextSelected(nodeCompat)
            && !selectText(
                nodeCompat,
                nodeCompat.getTextSelectionStart(),
                nodeCompat.getTextSelectionStart())) {
          return false;
        }
        nodeCompat.refresh();
        if (!PerformActionUtils.performAction(
            nodeCompat,
            AccessibilityNodeInfo.ACTION_PREVIOUS_AT_MOVEMENT_GRANULARITY,
            arguments,
            null)) {
          return false;
        }
        nodeCompat.refresh();
        return selectText(nodeCompat, nodeCompat.getTextSelectionEnd(), currentSelectionEnd);
    }
  }

  /**
   * Set the text and the cursor position of the given {@link AccessibilityNodeInfoCompat}.
   *
   * @param nodeCompat The {@link AccessibilityNodeInfoCompat} for which to set text
   * @param arguments The {@link Bundle} containing the text to set and the selection start and end
   *     indices to set
   * @return {@code true} if setting the text and the cursor position is successful
   */
  public static boolean setText(AccessibilityNodeInfoCompat nodeCompat, Bundle arguments) {
    if (!PerformActionUtils.performAction(
        nodeCompat, AccessibilityNodeInfo.ACTION_SET_TEXT, arguments, null /* EventId */)) {
      return false;
    }

    // Restore the cursor position. If arguments has no ACTION_ARGUMENT_SELECTION_START_INT, this
    // moves the cursor to the beginning of the text.
    nodeCompat.refresh();
    return selectText(
        nodeCompat,
        arguments.getInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT),
        arguments.getInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT));
  }

  /**
   * Undo the previous action on the action timeline corresponding to the given node.
   *
   * @param service The accessibility service used to get the timeline and perform the undo
   * @param nodeCompat The node on which to perform the undo
   * @return {@code true} if the undo action is successful
   */
  public static boolean performUndo(
      AccessibilityService service, SwitchAccessNodeCompat nodeCompat) {
    SwitchAccessActionTimeline timeline = updateAndGetTimelineForNode(nodeCompat);
    return timeline.performUndo(service);
  }

  /**
   * Redo a previously undone action on the action timeline corresponding to the given node.
   *
   * @param service The accessibility service used to get the timeline and perform the redo
   * @param nodeCompat The node on which to perform the redo
   * @return {@code true} if the redo action is successful
   */
  public static boolean performRedo(
      AccessibilityService service, SwitchAccessNodeCompat nodeCompat) {
    SwitchAccessActionTimeline timeline = updateAndGetTimelineForNode(nodeCompat);
    return timeline.performRedo(service);
  }

  /**
   * Get the {@link AccessibilityNodeInfoCompat}'s text if it is not default text (e.g. "Search..."
   * in a search box).
   *
   * <p>Note: (AccessibilityNodeInfoCompat#getTextSelectionStart == -1) indicates that there is no
   * current selection or cursor position because the text is empty (excluding default text).
   *
   * @param nodeCompat The node for which to get text
   * @return The node's text, or an empty string if the given node's text is default text
   */
  public static CharSequence getNonDefaultTextForNode(AccessibilityNodeInfoCompat nodeCompat) {
    return (nodeCompat.getTextSelectionStart() == -1) ? "" : nodeCompat.getText();
  }

  /*
   * Update the node of the {@link SwitchAccessActionTimeline} corresponding to the given
   * nodeCompat to be the given nodeCompat and return it.
   *
   * @param service The accessibility service used to get the timeline and perform the redo
   * @param nodeCompat The node on which to perform the redo
   * @return The timeline corresponding to the given nodeCompat
   */
  private static SwitchAccessActionTimeline updateAndGetTimelineForNode(
      SwitchAccessNodeCompat nodeCompat) {
    // This should always return an existing timeline (not the new one) because ActionBuildingUtils
    // should only add an undo/redo action for nodeCompat if nodeCompat has actions to undo/redo.
    // nodeCompat can only have actions to undo/redo if it already has a corresponding timeline.
    SwitchAccessActionTimeline timeline =
        (SwitchAccessActionTimeline)
            UndoRedoManager.getInstance(RecycleBehavior.DO_RECYCLE_NODES)
                .getTimelineForNodeCompat(nodeCompat, new SwitchAccessActionTimeline(nodeCompat));

    // Update the nodeCompat of this timeline so it does not try to access a recycled node.
    // Since the node tree is constantly rebuilding, the SwitchAccessNodeCompat used to create
    // the previous/next action may not exist anymore.
    timeline.setNode(nodeCompat);
    return timeline;
  }

  /*
   * Select the text in the given {@link AccessibilityNodeInfoCompat} between the given starting and
   * ending indices.
   *
   * @param nodeCompat The {@link AccessibilityNodeInfoCompat} to select text in
   * @param startIndex The beginning index of the selection
   * @param endIndex The ending index of the selection
   * @return {@code true} if the selection is successful
   */
  private static boolean selectText(
      AccessibilityNodeInfoCompat nodeCompat, int startIndex, int endIndex) {
    Bundle args = new Bundle();
    args.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, startIndex);
    args.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, endIndex);
    boolean selected =
        PerformActionUtils.performAction(
            nodeCompat, AccessibilityNodeInfo.ACTION_SET_SELECTION, args, null);
    // Return true if we're setting the cursor to the end of the text. This is a workaround for
    //  where setting selection returns false when setting the index at the end of the
    // text. The correct action is still performed, but the method call sometimes returns false.
    // TODO: Update this for newer API versions when  is fixed.
    CharSequence text = getNonDefaultTextForNode(nodeCompat);
    int textEnd = (text == null) ? 0 : text.length();
    return ((startIndex == textEnd) && (endIndex == textEnd)) || selected;
  }

  /*
   * Extend the current selection in the given {@link AccessibilityNodeInfoCompat} backwards by one
   * sentence. The new selection should start at the beginning of the sentence before the
   * start of the current selection, and it should end at the end of the current selection.
   *
   * @param nodeCompat The {@link AccessibilityNodeInfoCompat} to select text in
   * @return {@code true} if the selection is successful
   */
  private static boolean extendSelectionBackOneSentence(AccessibilityNodeInfoCompat nodeCompat) {
    int newSelectionStart =
        getEndOfLastSentenceBeforeIndex(nodeCompat.getText(), nodeCompat.getTextSelectionStart());
    return selectText(nodeCompat, newSelectionStart, nodeCompat.getTextSelectionEnd());
  }
}
