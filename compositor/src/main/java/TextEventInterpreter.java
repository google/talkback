/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.google.android.accessibility.compositor;

import static com.google.android.accessibility.compositor.TextEventHistory.NO_INDEX;

import android.content.Context;
import androidx.annotation.Nullable;
import androidx.core.view.accessibility.AccessibilityEventCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.style.TtsSpan;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import com.google.android.accessibility.utils.AccessibilityNodeInfoUtils;
import com.google.android.accessibility.utils.BuildVersionUtils;
import com.google.android.accessibility.utils.SpeechCleanupUtils;
import com.google.android.accessibility.utils.input.InputModeManager;
import com.google.android.accessibility.utils.input.TextCursorManager;
import com.google.android.libraries.accessibility.utils.log.LogUtils;
import java.util.List;

/**
 * Looks at current event & event history, to more specifically determine current event type, and to
 * extract important pieces of event data.
 */
public class TextEventInterpreter {

  private static final String TAG = "TextEventInterpreter";

  /** A minimal interface to read text-selection state. */
  public interface SelectionStateReader {
    boolean isSelectionModeActive();
  }

  // /////////////////////////////////////////////////////////////////////////////////
  // Member variables

  private final Context mContext;
  @Nullable private final TextCursorManager mTextCursorManager;
  @Nullable private final SelectionStateReader selectionStateReader;
  private final InputModeManager mInputModeManager;
  private final GlobalVariables mGlobalVariables;

  // Event history
  private TextEventHistory mHistory;

  // /////////////////////////////////////////////////////////////////////////////////
  // Construction

  public TextEventInterpreter(
      Context context,
      @Nullable TextCursorManager textCursorManager,
      @Nullable SelectionStateReader selectionStateReader,
      InputModeManager inputModeManager,
      TextEventHistory history,
      GlobalVariables globalVariables) {
    mContext = context;
    mTextCursorManager = textCursorManager;
    this.selectionStateReader = selectionStateReader;
    mInputModeManager = inputModeManager;
    mHistory = history;
    mGlobalVariables = globalVariables;
  }

  ///////////////////////////////////////////////////////////////////////////////////
  // Methods to interpret event based on event content and event history

  /** Extract a text event interpretation data from event. May return null. */
  public @Nullable TextEventInterpretation interpret(AccessibilityEvent event) {
    // Interpret more specific event type.
    @Compositor.Event int eventType = event.getEventType();
    TextEventInterpretation interpretation;
    boolean isTextEvent = false;
    switch (eventType) {
      case AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED:
        interpretation = interpretTextChange(event);
        isTextEvent = true;
        break;

      case AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED:
      case AccessibilityEvent.TYPE_VIEW_TEXT_TRAVERSED_AT_MOVEMENT_GRANULARITY:
        interpretation = interpretSelectionChange(event);
        isTextEvent = true;
        break;

      default:
        return null;
    }

    // Display interpretation, seal interpretation.
    interpretation.setReadOnly();
    if (isTextEvent) {
      LogUtils.i(TAG, "interpretation: %s", interpretation);
    }
    return interpretation;
  }

  private TextEventInterpretation interpretTextChange(AccessibilityEvent event) {
    // Default to original event type.
    @Compositor.Event int eventType = event.getEventType();
    TextEventInterpretation interpretation = new TextEventInterpretation(eventType);

    // Case for handling password.
    if (event.isPassword() && !mGlobalVariables.shouldSpeakPasswords()) {
      int removed = event.getRemovedCount();
      int added = event.getAddedCount();
      if ((added <= 0) && (removed <= 0)) {
        interpretation.setEvent(Compositor.EVENT_TYPE_INPUT_CHANGE_INVALID);
      } else if ((added == 1) && (removed <= 0)) {
        interpretation.setEvent(Compositor.EVENT_TYPE_INPUT_TEXT_PASSWORD_ADD);
      } else if ((added <= 0) && (removed == 1)) {
        interpretation.setEvent(Compositor.EVENT_TYPE_INPUT_TEXT_PASSWORD_REMOVE);
      } else if (isJunkyCharacterReplacedByBulletInUnlockPinEntry(event)) {
        return interpretation.setInvalid(
            "Junky text change event when the number typed in pin entry is replaced by bullet.");
      } else {
        interpretation.setEvent(Compositor.EVENT_TYPE_INPUT_TEXT_PASSWORD_REPLACE);
      }
      interpretation.setReason("Event is password and not speaking passwords.");
      return interpretation;
    }

    // Sanity check
    if (!passesSanityCheck(event)) {
      return interpretation.setInvalid("passesSanityCheck() is false.");
    }

    // Check for ongoing cut/paste.
    if (mHistory.hasCutActionAtTime(event.getEventTime())) {
      interpretation.setIsCutAction(true);
    } else if (mHistory.hasPasteActionAtTime(event.getEventTime())) {
      interpretation.setIsPasteAction(true);
    }

    // If no text was added but all the previous text was removed, text was cleared.
    if (event.getRemovedCount() > 1
        && event.getAddedCount() == 0
        && event.getBeforeText().length() == event.getRemovedCount()) {
      interpretation.setEvent(Compositor.EVENT_TYPE_INPUT_TEXT_CLEAR);
      interpretation.setReason("Cleared number of characters equal to field content length.");
      return interpretation;
    }

    // Extract added/removed text from event.
    CharSequence removedText = getRemovedText(event);
    CharSequence addedText = getAddedText(event);
    if (removedText == null) {
      return interpretation.setInvalid("removedText is null.");
    }
    if (addedText == null) {
      return interpretation.setInvalid("addedText is null.");
    }
    if (TextUtils.equals(addedText, removedText)) {
      return interpretation.setInvalid("addedText is the same as removedText.");
    }

    // Translate partial replacement into net addition / deletion.
    final int removedLength = removedText.length();
    final int addedLength = addedText.length();
    if (removedLength > addedLength) {
      if (TextUtils.regionMatches(removedText, 0, addedText, 0, addedLength)) {
        removedText = getSubsequenceWithSpans(removedText, addedLength, removedLength);
        // Prevent TapPresubmit alert for Nullable annotation conflict
        removedText = (removedText == null) ? "" : removedText;
        addedText = "";
      }
    } else if (addedLength > removedLength) {
      if (TextUtils.regionMatches(removedText, 0, addedText, 0, removedLength)) {
        removedText = "";
        addedText = getSubsequenceWithSpans(addedText, removedLength, addedLength);
        // Prevent TapPresubmit alert for Nullable annotation conflict
        addedText = (addedText == null) ? "" : addedText;
      }
    }
    interpretation.setRemovedText(removedText);
    interpretation.setAddedText(addedText);

    // Apply speech clean up rules. Example: changing "A" to "capital A".
    final CharSequence cleanRemovedText = SpeechCleanupUtils.cleanUp(mContext, removedText);
    final CharSequence cleanAddedText = SpeechCleanupUtils.cleanUp(mContext, addedText);
    if (isJunkyCharacterReplacedByBulletInUnlockPinEntry(event)) {
      return interpretation.setInvalid(
          "Junky text change event when the number typed in pin entry is replaced by bullet.");
    }

    // Text added
    if (!TextUtils.isEmpty(cleanAddedText)) {
      boolean replacementSupported =
          mContext.getResources().getBoolean(R.bool.supports_text_replacement);
      if (appendLastWordIfNeeded(event, interpretation)
          || TextUtils.isEmpty(cleanRemovedText)
          || TextUtils.equals(cleanAddedText, cleanRemovedText)
          || (!replacementSupported)) {
        interpretation.setEvent(Compositor.EVENT_TYPE_INPUT_TEXT_ADD);
      } else {
        interpretation.setEvent(Compositor.EVENT_TYPE_INPUT_TEXT_REPLACE);
      }
      interpretation.setReason("cleanAddedText is not empty.");
      return interpretation;
    }

    // Text removed
    if (!TextUtils.isEmpty(cleanRemovedText)) {
      interpretation.setEvent(Compositor.EVENT_TYPE_INPUT_TEXT_REMOVE);
      interpretation.setReason("cleanRemovedText is not empty.");
      return interpretation;
    }

    return interpretation.setInvalid("addedText and removedText are both empty.");
  }

  /**
   * Returns {@code true} if it's a junky {@link AccessibilityEvent#TYPE_VIEW_TEXT_CHANGED} when
   * typing pin code in unlock screen.
   *
   * <p>Starting from P, pin entry at lock screen files text changed event when the digit typed in
   * is visually replaced by bullet. We don't want to announce this change.
   *
   * <p>Fortunately, password field at lock screen doesn't have this issue.
   */
  private static boolean isJunkyCharacterReplacedByBulletInUnlockPinEntry(
      AccessibilityEvent event) {
    if (!BuildVersionUtils.isAtLeastP()
        || (event.getAddedCount() != 1)
        || (event.getRemovedCount() != 1)) {
      return false;
    }
    AccessibilityNodeInfo source = event.getSource();
    try {
      return AccessibilityNodeInfoUtils.isPinEntry(source);
    } finally {
      AccessibilityNodeInfoUtils.recycleNodes(source);
    }
  }

  private TextEventInterpretation interpretSelectionChange(AccessibilityEvent event) {
    // Default to original event type.
    @Compositor.Event int eventType = event.getEventType();
    TextEventInterpretation interpretation = new TextEventInterpretation(eventType);

    // Extract text from input field.
    final boolean isGranularTraversal =
        (event.getEventType()
            == AccessibilityEventCompat.TYPE_VIEW_TEXT_TRAVERSED_AT_MOVEMENT_GRANULARITY);
    final @Nullable CharSequence text;
    if (isGranularTraversal) {
      // Gets text from node instead of event to prevent missing locale spans.
      AccessibilityNodeInfoCompat nodeToAnnounce =
          AccessibilityNodeInfoUtils.toCompat(event.getSource());
      text = AccessibilityNodeInfoUtils.getNodeText(nodeToAnnounce);
      AccessibilityNodeInfoUtils.recycleNodes(nodeToAnnounce);
    } else {
      // Only use the first item from getText().
      text = getEventText(event);
    }
    interpretation.setTextOrDescription(text);

    // Don't provide selection feedback when there's no text. We have to
    // check the item count separately to avoid speaking hint text,
    // which always has an item count of zero even though the event text
    // is not empty. Note that, on <= M, password text is empty but the count is nonzero.
    final int count = event.getItemCount();
    boolean isPassword = event.isPassword();
    if ((TextUtils.isEmpty(text) && !isPassword) || (count == 0)) {
      // In Android O, we rely on TEXT_SELECTION_CHANGED events to announce text changes in password
      // field. Thus even though we don't announce anything in this case, we need to carefully
      // update the index.
      AccessibilityNodeInfoCompat source = AccessibilityNodeInfoUtils.toCompat(event.getSource());
      if (AccessibilityNodeInfoUtils.isEmptyEditTextRegardlessOfHint(source)) {
        mHistory.setLastFromIndex(0);
        mHistory.setLastToIndex(0);
      }
      AccessibilityNodeInfoUtils.recycleNodes(source);
      return interpretation.setInvalid("Text is empty.");
    }

    // Check whether event state requires resetting selection.
    if (event.getEventType() == AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED
        && (mGlobalVariables.checkAndClearRecentFlag(
                GlobalVariables.EVENT_SKIP_SELECTION_CHANGED_AFTER_FOCUSED)
            || mGlobalVariables.checkAndClearRecentFlag(
                GlobalVariables.EVENT_SKIP_SELECTION_CHANGED_AFTER_CURSOR_RESET))) {
      interpretation.setEvent(Compositor.EVENT_TYPE_INPUT_SELECTION_RESET_SELECTION);
      interpretation.setReason("Event state is EVENT_SKIP_SELECTION_CHANGED...");
      return interpretation;
    }

    if (mTextCursorManager == null) {
      interpretation.setInvalid("TextCursorManager is null.");
      return interpretation;
    }

    int toIndex = event.getToIndex();
    int fromIndex = event.getFromIndex();
    int previousCursorPos = mTextCursorManager.getPreviousCursorPosition();
    int currentCursorPos = mTextCursorManager.getCurrentCursorPosition();
    int textLength = TextUtils.isEmpty(text) ? 0 : text.length();
    boolean isSelectionModeActive =
        (selectionStateReader != null) && selectionStateReader.isSelectionModeActive();

    int eventTypeInt = eventType;
    long eventTime = event.getEventTime();
    boolean hasKeyboardAction =
        (mInputModeManager.getInputMode() == InputModeManager.INPUT_MODE_KEYBOARD);
    if (eventTypeInt == AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED) {
      if (!sourceEqualsLastNode(event)) {
        interpretation.setEvent(Compositor.EVENT_TYPE_INPUT_SELECTION_FOCUS_EDIT_TEXT);
        interpretation.setReason("source != mLastNode");
        // Update history.
        mHistory.setLastFromIndex(NO_INDEX);
        mHistory.setLastToIndex(NO_INDEX);
        setHistoryLastNode(event);
        return interpretation;
      } else if (mHistory.hasCutActionAtTime(eventTime) && fromIndex == toIndex) {
        interpretation.setEvent(Compositor.EVENT_TYPE_INPUT_SELECTION_CUT);
        interpretation.setReason("Cut action ongoing and from==to");
        return interpretation;
      } else if (mHistory.hasPasteActionAtTime(eventTime)) {
        interpretation.setEvent(Compositor.EVENT_TYPE_INPUT_SELECTION_PASTE);
        interpretation.setReason("Paste action ongoing.");
        return interpretation;
      } else if (fromIndex == 0
          && toIndex == 0
          && previousCursorPos == 0
          && currentCursorPos == 0) {
        interpretation.setEvent(Compositor.EVENT_TYPE_INPUT_SELECTION_MOVE_CURSOR_TO_BEGINNING);
        interpretation.setReason("All cursor positions == 0");
        return interpretation;
      } else if (fromIndex == textLength
          && toIndex == textLength
          && previousCursorPos == textLength
          && currentCursorPos == textLength) {
        interpretation.setEvent(Compositor.EVENT_TYPE_INPUT_SELECTION_MOVE_CURSOR_TO_END);
        interpretation.setReason("All cursor positions == textLength");
        return interpretation;
      } else if (fromIndex == 0
          && toIndex == textLength
          && mHistory.hasSelectAllActionAtTime(eventTime)) {
        interpretation.setEvent(Compositor.EVENT_TYPE_INPUT_SELECTION_SELECT_ALL);
        interpretation.setReason("Select-all ongoing and from==0 and to==textLength");
        return interpretation;
      } else if (fromIndex == toIndex // Selection is empty.
          && mHistory.getLastFromIndex() == mHistory.getLastToIndex() // Prev select empty
          && toIndex == currentCursorPos // Cursor location is valid.
          && mHistory.getLastToIndex() == previousCursorPos) { // Prev cursor is valid.
        interpretation.setEvent(Compositor.EVENT_TYPE_INPUT_SELECTION_MOVE_CURSOR_NO_SELECTION);
        interpretation.setReason("Cursor moved to end of selection.");
        // Extract traversed text.
        int startIndex = Math.min(mHistory.getLastToIndex(), toIndex);
        int endIndex = Math.max(mHistory.getLastToIndex(), toIndex);
        if (0 <= startIndex && endIndex <= textLength) {
          CharSequence traversedText = getSubsequence(isPassword, text, startIndex, endIndex);
          interpretation.setTraversedText(traversedText);
        }
        // Update history.
        mHistory.setLastProcessedEvent(event);
        return interpretation;
        /**
         * TODO refactor the following three cases when we get more information for the text
         * selection action on physical keyboard. 
         *
         * <p>Sometimes TalkBack cannot distinguish between "select all" action and "move cursor
         * within selection mode" action. In this case, we currently classify the ambiguous action
         * with some preferences.
         *
         * <p>Suppose we use "|...|" to represent selection range. Example 1: "||hello" -->
         * "|hello|" It can be achieved by Ctrl+A or Shift+Ctrl+right. Since there is no selection
         * before the action, we prefer to classify it as a "select all" action, which lands on the
         * first case beneath. Example 2: "|hello| world" --> "|hello world|" It can be achieved by
         * Ctrl+A or Shift+Ctrl+right. Since there is already a selection before the action, we
         * prefer to classify it as a "move cursor within selection mode" action, which lands on the
         * second case. Example 3: "say |hello| to the world" --> "|say hello to the world|" It can
         * only be achieved by Ctrl+A. Thus it lands on the third case, which is for general select
         * all actions.
         *
         * <p>That's why we need to have the first case: "duplicated" SELECT_ALL_WITH_KEYBOARD.
         */
      } else if (mHistory.getLastFromIndex() == mHistory.getLastToIndex()
          && fromIndex == 0
          && toIndex == textLength
          && hasKeyboardAction) {
        interpretation.setEvent(Compositor.EVENT_TYPE_INPUT_SELECTION_SELECT_ALL_WITH_KEYBOARD);
        interpretation.setReason("from==0 to==textLength and hasKeyboardAction");
        return interpretation;
      } else if ((isSelectionModeActive || hasKeyboardAction)
          && mHistory.getLastFromIndex() == fromIndex
          && mHistory.getLastToIndex() == previousCursorPos
          && toIndex == currentCursorPos) {
        interpretation.setEvent(Compositor.EVENT_TYPE_INPUT_SELECTION_MOVE_CURSOR_WITH_SELECTION);
        interpretation.setReason("Selecting and toIndex == cursorPosition");
        // Extract de/selected text.
        CharSequence deselectedText =
            getUnselectedText(isPassword, text, fromIndex, toIndex, mHistory.getLastToIndex());
        CharSequence selectedText =
            getSelectedText(isPassword, text, fromIndex, toIndex, mHistory.getLastToIndex());
        interpretation.setDeselectedText(deselectedText);
        interpretation.setSelectedText(selectedText);
        // Update history.
        mHistory.setLastProcessedEvent(event);
        return interpretation;
      } else if (fromIndex == 0 && toIndex == textLength && hasKeyboardAction) {
        interpretation.setEvent(Compositor.EVENT_TYPE_INPUT_SELECTION_SELECT_ALL_WITH_KEYBOARD);
        interpretation.setReason("from==0 to==textLength and hasKeyboardAction");
        return interpretation;
      } else if (mHistory.getLastFromIndex() != mHistory.getLastToIndex() && fromIndex == toIndex) {
        interpretation.setEvent(
            Compositor.EVENT_TYPE_INPUT_SELECTION_MOVE_CURSOR_SELECTION_CLEARED);
        interpretation.setReason("mLastFromIndex != mLastToIndex && fromIndex == toIndex");
        return interpretation;
      }
    } else if (eventTypeInt
        == AccessibilityEvent.TYPE_VIEW_TEXT_TRAVERSED_AT_MOVEMENT_GRANULARITY) {

      if (fromIndex >= 0 && fromIndex <= textLength && toIndex >= 0 && toIndex <= textLength) {
        interpretation.setEvent(Compositor.EVENT_TYPE_INPUT_SELECTION_TEXT_TRAVERSAL);
        interpretation.setReason("fromIndex and toIndex both within text range");

        // Extract traversed text.
        CharSequence traversedText = null;
        if (event.getMovementGranularity()
            == AccessibilityNodeInfoCompat.MOVEMENT_GRANULARITY_CHARACTER) {
          int charIndex = Math.min(fromIndex, toIndex);
          if (0 <= charIndex && charIndex < textLength) {
            traversedText = getSubsequenceWithSpans(text, charIndex, charIndex + 1);
          }
        } else {
          traversedText =
              getSubsequenceWithSpans(
                  text, Math.min(fromIndex, toIndex), Math.max(fromIndex, toIndex));
        }
        interpretation.setTraversedText(traversedText);
        return interpretation;
      }
    }

    // Update history for events which could not be interpreted in the above mentioned categories.
    mHistory.setLastFromIndex(event.getFromIndex());
    mHistory.setLastToIndex(event.getToIndex());

    return interpretation.setInvalid("Unhandled selection event.");
  }

  // Visible for testing only.
  protected void setHistoryLastNode(AccessibilityEvent event) {
    mHistory.setLastNode(event.getSource()); // TextEventHistory will recycle source node.
  }

  ////////////////////////////////////////////////////////////////////////////////////////
  // Helper functions for text-change events.

  private static boolean passesSanityCheck(AccessibilityEvent event) {
    final List<CharSequence> afterTexts = event.getText();
    final CharSequence afterText =
        (afterTexts == null || afterTexts.isEmpty()) ? null : afterTexts.get(0);

    final CharSequence beforeText = event.getBeforeText();

    // Special case for deleting all the text in an EditText with a
    // hint, since the event text will contain the hint rather than an
    // empty string.
    int beforeTextLength = (beforeText == null) ? 0 : beforeText.length();
    if ((event.getAddedCount() == 0) && (event.getRemovedCount() == beforeTextLength)) {
      return true;
    }

    if (afterText == null || beforeText == null) {
      return false;
    }

    final int diff = (event.getAddedCount() - event.getRemovedCount());

    return ((beforeText.length() + diff) == afterText.length());
  }

  private static @Nullable CharSequence getRemovedText(AccessibilityEvent event) {
    final CharSequence beforeText = event.getBeforeText();
    if (beforeText == null) {
      return null;
    }

    final int beforeBegIndex = event.getFromIndex();
    final int beforeEndIndex = beforeBegIndex + event.getRemovedCount();
    if (areInvalidIndices(beforeText, beforeBegIndex, beforeEndIndex)) {
      return "";
    }

    return getSubsequenceWithSpans(beforeText, beforeBegIndex, beforeEndIndex);
  }

  /**
   * Returns {@code null}, empty string or the added text depending on the event.
   *
   * <p>For cases where event.getText() is null or bad size, text interpretation is expected to be
   * set invalid with "addedText is null" in interpretTextChange(). Hence where event.getText() is
   * null or bad size, we return null as returning an empty string here would bypass this condition
   * and the text interpretation would be incorrect.
   *
   * @param event
   * @return the added text.
   */
  private static @Nullable CharSequence getAddedText(AccessibilityEvent event) {
    final List<CharSequence> textList = event.getText();
    // noinspection ConstantConditions
    if (textList == null || textList.size() > 1) {
      LogUtils.w(TAG, "getAddedText: Text list was null or bad size");
      return null;
    }

    // If the text was empty, the list will be empty. See the
    // implementation for TextView.onPopulateAccessibilityEvent().
    if (textList.size() == 0) {
      return "";
    }

    final CharSequence text = textList.get(0);
    if (text == null) {
      LogUtils.w(TAG, "getAddedText: First text entry was null");
      return null;
    }

    final int addedBegIndex = event.getFromIndex();
    final int addedEndIndex = addedBegIndex + event.getAddedCount();
    if (areInvalidIndices(text, addedBegIndex, addedEndIndex)) {
      LogUtils.w(
          TAG,
          "getAddedText: Invalid indices (%d,%d) for \"%s\"",
          addedBegIndex,
          addedEndIndex,
          text);
      return "";
    }

    return getSubsequenceWithSpans(text, addedBegIndex, addedEndIndex);
  }

  private static boolean areInvalidIndices(CharSequence text, int begin, int end) {
    return (begin < 0) || (end > text.length()) || (begin >= end);
  }

  private boolean appendLastWordIfNeeded(
      AccessibilityEvent event, TextEventInterpretation interpretation) {
    final CharSequence text = getEventText(event);
    final CharSequence addedText = getAddedText(event);
    final int fromIndex = event.getFromIndex();

    if (fromIndex > text.length()) {
      LogUtils.w(TAG, "Received event with invalid fromIndex: %s", event);
      return false;
    }

    // Check if any visible text was added.
    if (addedText != null) {
      int trimmedLength = TextUtils.getTrimmedLength(addedText);
      if (trimmedLength > 0) {
        return false;
      }
    }

    final int breakIndex = getPrecedingWhitespace(text, fromIndex);
    final CharSequence word = text.subSequence(breakIndex, fromIndex);

    // Did the user just type a word?
    if (TextUtils.getTrimmedLength(word) == 0) {
      return false;
    }

    interpretation.setInitialWord(word);
    return true;
  }

  ////////////////////////////////////////////////////////////////////////////////////////
  // Helper functions for selection-change events.

  private static CharSequence getEventText(AccessibilityEvent event) {
    final List<CharSequence> eventText = event.getText();

    if (eventText.isEmpty()) {
      return "";
    }

    return eventText.get(0);
  }

  /** Returns index of first whitespace preceding fromIndex. */
  private static int getPrecedingWhitespace(CharSequence text, int fromIndex) {
    if (fromIndex > text.length()) {
      fromIndex = text.length();
    }
    for (int i = (fromIndex - 1); i > 0; i--) {
      if (Character.isWhitespace(text.charAt(i))) {
        return i;
      }
    }

    return 0;
  }

  // Visible for testing only.
  protected boolean sourceEqualsLastNode(AccessibilityEvent event) {
    AccessibilityNodeInfo source = null;
    try {
      source = event.getSource();
      AccessibilityNodeInfo lastNode = mHistory.getLastNode();
      return (source != null) && lastNode != null && source.equals(lastNode);
    } finally {
      AccessibilityNodeInfoUtils.recycleNodes(source);
    }
  }

  private @Nullable CharSequence getUnselectedText(
      boolean isPassword,
      @Nullable CharSequence text,
      int fromIndex,
      int toIndex,
      int lastToIndex) {
    if (fromIndex < lastToIndex && toIndex < lastToIndex) {
      return getSubsequence(isPassword, text, Math.max(fromIndex, toIndex), lastToIndex);
    } else if (fromIndex > lastToIndex && toIndex > lastToIndex) {
      return getSubsequence(isPassword, text, lastToIndex, Math.min(fromIndex, toIndex));
    } else {
      return null;
    }
  }

  private @Nullable CharSequence getSelectedText(
      boolean isPassword,
      @Nullable CharSequence text,
      int fromIndex,
      int toIndex,
      int lastToIndex) {
    if (fromIndex < toIndex && lastToIndex < toIndex) {
      return getSubsequence(isPassword, text, Math.max(fromIndex, lastToIndex), toIndex);
    } else if (fromIndex > toIndex && lastToIndex > toIndex) {
      return getSubsequence(isPassword, text, toIndex, Math.min(fromIndex, lastToIndex));
    } else {
      return null;
    }
  }

  /**
   * Gets the subsequence {@code [from, to)} of the given text. If the text is a password and the
   * password cannot be read aloud, then returns a suitable substitute description, such as
   * "Character 3" or "Characters 3 to 4".
   *
   * @param isPassword whether the text input is a password input
   * @param text the text from which we need to extract a subsequence (or for which the password
   *     substitution needs to be provided)
   * @param from the beginning index (inclusive)
   * @param to the ending index (exclusive)
   * @return the requested subsequence or an alternate description for passwords, or null if range
   *     is invalid.
   */
  private @Nullable CharSequence getSubsequence(
      boolean isPassword, @Nullable CharSequence text, int from, int to) {
    if (isPassword && !mGlobalVariables.shouldSpeakPasswords()) {
      if (to - from == 1) {
        return mContext.getString(R.string.template_password_traversed, from + 1);
      } else {
        return mContext.getString(R.string.template_password_selected, from + 1, to);
      }
    } else {
      return getSubsequenceWithSpans(text, from, to);
    }
  }

  // ,remove only TtsSpans which are marked up beyond the boundary of traversed text.
  @Nullable
  public static CharSequence getSubsequenceWithSpans(
      @Nullable CharSequence text, int from, int to) {
    if (text == null) {
      return null;
    }
    if (from < 0 || text.length() < to || to < from) {
      return null;
    }

    SpannableString textWithSpans = SpannableString.valueOf(text);
    CharSequence subsequence = text.subSequence(from, to);
    SpannableString subsequenceWithSpans = SpannableString.valueOf(subsequence);
    TtsSpan[] spans = subsequenceWithSpans.getSpans(0, subsequence.length(), TtsSpan.class);

    for (TtsSpan span : spans) {
      if (textWithSpans.getSpanStart(span) < from || to < textWithSpans.getSpanEnd(span)) {
        subsequenceWithSpans.removeSpan(span);
      }
    }
    return subsequence;
  }
}
