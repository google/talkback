/*
 * Copyright (C) 2019 The Android Open Source Project
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

import static androidx.core.view.accessibility.AccessibilityNodeInfoCompat.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE;
import static androidx.core.view.accessibility.AccessibilityNodeInfoCompat.ACTION_SET_TEXT;
import static com.google.android.accessibility.talkback.Feedback.FocusDirection.Action.SELECTION_MODE_OFF;
import static com.google.android.accessibility.utils.output.FeedbackItem.FLAG_FORCE_FEEDBACK_EVEN_IF_AUDIO_PLAYBACK_ACTIVE;
import static com.google.android.accessibility.utils.output.FeedbackItem.FLAG_FORCE_FEEDBACK_EVEN_IF_MICROPHONE_ACTIVE;
import static com.google.android.accessibility.utils.output.FeedbackItem.FLAG_FORCE_FEEDBACK_EVEN_IF_SSB_ACTIVE;
import static com.google.android.accessibility.utils.output.FeedbackItem.FLAG_NO_HISTORY;
import static com.google.android.accessibility.utils.output.SpeechController.QUEUE_MODE_INTERRUPT_AND_UNINTERRUPTIBLE_BY_NEW_SPEECH;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.InputMethod;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.os.Bundle;
import android.text.Spanned;
import android.text.TextUtils;
import android.util.Pair;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import com.google.android.accessibility.talkback.Feedback;
import com.google.android.accessibility.talkback.Pipeline.FeedbackReturner;
import com.google.android.accessibility.talkback.R;
import com.google.android.accessibility.talkback.actor.DirectionNavigationActor.StateReader;
import com.google.android.accessibility.utils.AccessibilityNodeInfoUtils;
import com.google.android.accessibility.utils.AccessibilityNodeInfoUtils.SpellingSuggestion;
import com.google.android.accessibility.utils.FeatureSupport;
import com.google.android.accessibility.utils.PerformActionUtils;
import com.google.android.accessibility.utils.Performance.EventId;
import com.google.android.accessibility.utils.Role;
import com.google.android.accessibility.utils.input.TextCursorTracker;
import com.google.android.accessibility.utils.output.EditTextActionHistory;
import com.google.android.accessibility.utils.output.SpeechCleanupUtils;
import com.google.android.accessibility.utils.output.SpeechController.SpeakOptions;
import com.google.android.libraries.accessibility.utils.log.LogUtils;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import org.checkerframework.checker.nullness.qual.Nullable;

/** Executes text-manipulation actions on editable or non-editable selectable text views. */
public class TextEditActor {

  /////////////////////////////////////////////////////////////////////////////////////////////
  // Constants

  private static final String TAG = "TextEditActor";

  /**
   * It makes sense to interrupt all the previous utterances generated in the talkback context menu.
   * After the cursor action is performed, it's the most important to notify the user what happens
   * to the edit text.
   */
  private static final SpeakOptions SPEAK_OPTIONS =
      SpeakOptions.create()
          .setQueueMode(QUEUE_MODE_INTERRUPT_AND_UNINTERRUPTIBLE_BY_NEW_SPEECH)
          .setFlags(
              FLAG_NO_HISTORY
                  | FLAG_FORCE_FEEDBACK_EVEN_IF_AUDIO_PLAYBACK_ACTIVE
                  | FLAG_FORCE_FEEDBACK_EVEN_IF_MICROPHONE_ACTIVE
                  | FLAG_FORCE_FEEDBACK_EVEN_IF_SSB_ACTIVE);

  /////////////////////////////////////////////////////////////////////////////////////////////
  // Member variables

  private final AccessibilityService service;
  private final EditTextActionHistory editTextActionHistory;
  private final TextCursorTracker textCursorTracker;
  private final ClipboardManager clipboard;
  private final DirectionNavigationActor.StateReader stateReader;
  private FeedbackReturner pipeline;

  /////////////////////////////////////////////////////////////////////////////////////////////
  // Construction

  public TextEditActor(
      AccessibilityService service,
      EditTextActionHistory editTextActionHistory,
      TextCursorTracker textCursorTracker,
      StateReader stateReader,
      ClipboardManager clipboard) {
    this.service = service;
    this.editTextActionHistory = editTextActionHistory;
    this.textCursorTracker = textCursorTracker;
    this.stateReader = stateReader;
    this.clipboard = clipboard;
  }

  public void setPipeline(FeedbackReturner pipeline) {
    this.pipeline = pipeline;
  }

  /////////////////////////////////////////////////////////////////////////////////////////////
  // Methods

  /** Executes and announces move cursor to end of edit-text. */
  public boolean cursorToEnd(
      AccessibilityNodeInfoCompat node, boolean stopSelecting, EventId eventId) {

    if (node == null || Role.getRole(node) != Role.ROLE_EDIT_TEXT) {
      return false;
    }

    // Stop selecting.
    if (stopSelecting) {
      pipeline.returnFeedback(eventId, Feedback.focusDirection(SELECTION_MODE_OFF));
    }

    // Move cursor.
    @Nullable CharSequence nodeText = AccessibilityNodeInfoUtils.getText(node);
    boolean result = false;
    if (nodeText != null) {
      result = moveCursor(node, nodeText.length(), eventId);
    }

    // Announce cursor movement.
    pipeline.returnFeedback(
        eventId,
        Feedback.speech(service.getString(R.string.notification_type_end_of_field), SPEAK_OPTIONS));

    return result;
  }

  /** Executes and announces move cursor to start of edit-text. */
  public boolean cursorToBeginning(
      AccessibilityNodeInfoCompat node, boolean stopSelecting, EventId eventId) {

    if (node == null || Role.getRole(node) != Role.ROLE_EDIT_TEXT) {
      return false;
    }

    // Stop selecting.
    if (stopSelecting) {
      pipeline.returnFeedback(eventId, Feedback.focusDirection(SELECTION_MODE_OFF));
    }

    // Move cursor.
    boolean result = moveCursor(node, 0, eventId);

    // Announce cursor movement.
    pipeline.returnFeedback(
        eventId,
        Feedback.speech(
            service.getString(R.string.notification_type_beginning_of_field), SPEAK_OPTIONS));

    return result;
  }

  /** Executes and announces start selecting text in edit-text. */
  @CanIgnoreReturnValue
  public boolean startSelect(AccessibilityNodeInfoCompat node, EventId eventId) {

    if (node == null || !AccessibilityNodeInfoUtils.isTextSelectable(node)) {
      return false;
    }

    // Start selecting.
    pipeline.returnFeedback(eventId, Feedback.selectionModeOn(node));

    // Announce selecting started.
    pipeline.returnFeedback(
        eventId,
        Feedback.speech(
            service.getString(R.string.notification_type_selection_mode_on), SPEAK_OPTIONS));

    return true;
  }

  /** Executes and announces end select text in selectable text. Modifies edit history. */
  public boolean endSelect(AccessibilityNodeInfoCompat node, EventId eventId) {

    if (node == null || !AccessibilityNodeInfoUtils.isTextSelectable(node)) {
      return false;
    }

    // Stop selecting.
    pipeline.returnFeedback(eventId, Feedback.focusDirection(SELECTION_MODE_OFF));

    // Announce selecting stopped.
    pipeline.returnFeedback(
        eventId,
        Feedback.speech(
            service.getString(R.string.notification_type_selection_mode_off), SPEAK_OPTIONS));

    @Nullable CharSequence textSelected =
        AccessibilityNodeInfoUtils.subsequenceSafe(
            AccessibilityNodeInfoUtils.getText(node),
            node.getTextSelectionStart(),
            node.getTextSelectionEnd());
    CharSequence textToSpeak =
        TextUtils.isEmpty(textSelected)
            ? service.getString(R.string.template_no_text_selected)
            : service.getString(R.string.template_announce_selected_text, textSelected);
    // Uses another speak options not to interrupt previous speech.
    SpeakOptions speakOptions =
        SpeakOptions.create()
            .setFlags(
                FLAG_FORCE_FEEDBACK_EVEN_IF_AUDIO_PLAYBACK_ACTIVE
                    | FLAG_FORCE_FEEDBACK_EVEN_IF_MICROPHONE_ACTIVE
                    | FLAG_FORCE_FEEDBACK_EVEN_IF_SSB_ACTIVE);
    pipeline.returnFeedback(eventId, Feedback.speech(textToSpeak, speakOptions));

    return true;
  }

  /** Executes and announces select-all text in selectable. Modifies edit history. */
  public boolean selectAll(AccessibilityNodeInfoCompat node, EventId eventId) {

    if (node == null || !AccessibilityNodeInfoUtils.isTextSelectable(node)) {
      return false;
    }

    @Nullable CharSequence nodeText = AccessibilityNodeInfoUtils.getText(node);
    if (TextUtils.isEmpty(nodeText)) {
      return false;
    }

    editTextActionHistory.beforeSelectAll();
    boolean result = false;
    if (FeatureSupport.supportInputConnectionByA11yService()) {
      result = performSelectText(0, nodeText.length());
      if (result && !stateReader.isSelectionModeActive()) {
        pipeline.returnFeedback(eventId, Feedback.selectionModeOn(node));
      }
    }
    if (!result) {
      // Execute select-all on target node.
      final Bundle args = new Bundle();
      args.putInt(AccessibilityNodeInfoCompat.ACTION_ARGUMENT_SELECTION_START_INT, 0);
      args.putInt(AccessibilityNodeInfoCompat.ACTION_ARGUMENT_SELECTION_END_INT, nodeText.length());
      result =
          PerformActionUtils.performAction(
              node, AccessibilityNodeInfoCompat.ACTION_SET_SELECTION, args, eventId);
      if (!result) {
        return false;
      }
    }
    editTextActionHistory.afterSelectAll();

    // Announce selected.
    pipeline.returnFeedback(
        eventId,
        Feedback.speech(
            SpeechCleanupUtils.cleanUp(
                service,
                service.getString(
                    R.string.template_announce_selected_text,
                    AccessibilityNodeInfoUtils.getText(node))),
            SPEAK_OPTIONS));
    return true;
  }

  /**
   * Executes and announces copy text. If the node is edit-text or has selectable text, it would
   * copy the selected text or it would copy the first non-empty node text within the root node.
   */
  public boolean copy(@Nullable AccessibilityNodeInfoCompat node, EventId eventId) {

    if (node == null) {
      return false;
    }

    boolean result = false;
    @Nullable CharSequence copyData = null;
    // TODO: use the existing tree-traversal to collect the node text within the root
    // node
    @Nullable CharSequence nodeText = AccessibilityNodeInfoUtils.getNodeText(node);
    @Nullable CharSequence selectedNodeText = AccessibilityNodeInfoUtils.getSelectedNodeText(node);
    // Perform copy action on target node. If the selection is not set, the primary clip will be
    // the node text.
    if (AccessibilityNodeInfoUtils.isTextSelectable(node) && !TextUtils.isEmpty(selectedNodeText)) {
      copyData = selectedNodeText;
      result =
          PerformActionUtils.performAction(node, AccessibilityNodeInfoCompat.ACTION_COPY, eventId);
    } else if (!TextUtils.isEmpty(nodeText)) {
      copyData = nodeText;
      ClipData clip = ClipData.newPlainText(/* label= */ null, copyData);
      clipboard.setPrimaryClip(clip);
      result = true;
    }

    pipeline.returnFeedback(
        eventId,
        Feedback.speech(
            copyData == null
                ? service.getString(R.string.cannot_copy_feedback)
                : (service.getString(R.string.template_text_copied, copyData.toString())),
            SPEAK_OPTIONS));
    return result;
  }

  /** Executes and announces cut text in edit-text. Modifies edit history. */
  public boolean cut(AccessibilityNodeInfoCompat node, EventId eventId) {

    if (node == null || Role.getRole(node) != Role.ROLE_EDIT_TEXT) {
      return false;
    }

    // Saved the selected text before performing the CUT action.
    CharSequence cutData = AccessibilityNodeInfoUtils.getSelectedNodeText(node);

    editTextActionHistory.beforeCut();
    boolean result =
        PerformActionUtils.performAction(node, AccessibilityNodeInfoCompat.ACTION_CUT, eventId);
    editTextActionHistory.afterCut();

    pipeline.returnFeedback(
        eventId,
        Feedback.speech(
            cutData != null
                ? (service.getString(R.string.template_text_cut, cutData.toString()))
                : service.getString(R.string.cannot_cut_feedback),
            SPEAK_OPTIONS));

    return result;
  }

  /** Executes and announces delete text in edit-text. Modifies edit history. */
  public boolean delete(AccessibilityNodeInfoCompat node, EventId eventId) {
    if (node == null || Role.getRole(node) != Role.ROLE_EDIT_TEXT) {
      return false;
    }

    @Nullable CharSequence nodeText = AccessibilityNodeInfoUtils.getNodeText(node);
    @Nullable CharSequence deletedText = AccessibilityNodeInfoUtils.getSelectedNodeText(node);
    // Set updated text.
    Pair<Integer, Integer> selectionIndexes =
        AccessibilityNodeInfoUtils.getSelectionIndexesSafe(node);
    int selectionStart = selectionIndexes.first;
    int selectionEnd = selectionIndexes.second;
    CharSequence textUpdated =
        TextUtils.concat(
            nodeText.subSequence(0, selectionStart),
            nodeText.subSequence(selectionEnd, nodeText.length()));
    Bundle arguments = new Bundle();
    arguments.putCharSequence(ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, textUpdated);
    boolean result = PerformActionUtils.performAction(node, ACTION_SET_TEXT, arguments, eventId);
    if (!result) {
      return false;
    }

    pipeline.returnFeedback(
        eventId,
        Feedback.speech(
            deletedText == null
                ? service.getString(R.string.cannot_delete_feedback)
                : (service.getString(R.string.template_text_removed, deletedText.toString())),
            SPEAK_OPTIONS));

    // Move cursor to start of deleted text.
    return moveCursor(node, selectionStart, eventId);
  }

  /** Executes and announces paste text in edit-text. Modifies edit history. */
  public boolean paste(AccessibilityNodeInfoCompat node, EventId eventId) {

    if (node == null || Role.getRole(node) != Role.ROLE_EDIT_TEXT) {
      return false;
    }

    editTextActionHistory.beforePaste();
    boolean result =
        PerformActionUtils.performAction(node, AccessibilityNodeInfoCompat.ACTION_PASTE, eventId);
    editTextActionHistory.afterPaste();

    if (!result) {
      pipeline.returnFeedback(
          eventId,
          Feedback.speech(service.getString(R.string.cannot_paste_feedback), SPEAK_OPTIONS));
    }

    return result;
  }

  /** Inserts text in edit-text. Modifies edit history. */
  public boolean insert(
      AccessibilityNodeInfoCompat node, CharSequence textToInsert, EventId eventId) {

    if (node == null || Role.getRole(node) != Role.ROLE_EDIT_TEXT) {
      return false;
    }

    // Find current selected text or cursor position.
    Pair<Integer, Integer> selectionIndexes =
        AccessibilityNodeInfoUtils.getSelectionIndexesSafe(node);
    int selectionStart = selectionIndexes.first;
    int selectionEnd = selectionIndexes.second;
    @Nullable CharSequence currentText = AccessibilityNodeInfoUtils.getText(node);
    CharSequence hintText = node.getHintText();
    // There is a low probability that the content of the input field is totally the same with the
    // hint text. In that case the current input field will be overridden by the inserted text.
    if (TextUtils.equals(currentText, hintText)) {
      currentText = null;
    }

    LogUtils.v(TAG, "insert() currentText=\"%s\"", (currentText == null ? "null" : currentText));
    if (currentText == null) {
      currentText = "";
    }

    // Set updated text.
    CharSequence textUpdated =
        TextUtils.concat(
            currentText.subSequence(0, selectionStart),
            textToInsert,
            currentText.subSequence(selectionEnd, currentText.length()));
    Bundle arguments = new Bundle();
    arguments.putCharSequence(ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, textUpdated);
    boolean result = PerformActionUtils.performAction(node, ACTION_SET_TEXT, arguments, eventId);
    if (!result) {
      return false;
    }

    // Move cursor to end of inserted text.
    return moveCursor(node, selectionStart + textToInsert.length(), eventId);
  }

  /**
   * Corrects the misspelling word by the suggestion.
   *
   * @param node Typo correction for the node.
   * @param suggestion The misspelled word will be replaced with the suggestion.
   * @param spellingSuggestion The wrap of {@link android.text.style.SuggestionSpan} for the
   *     misspelled word.
   * @param eventId Event ID for performance monitoring.
   * @return If the misspelled is replaced with the suggestion.
   */
  public boolean correctTypo(
      AccessibilityNodeInfoCompat node,
      CharSequence suggestion,
      SpellingSuggestion spellingSuggestion,
      EventId eventId) {
    if (node == null
        || Role.getRole(node) != Role.ROLE_EDIT_TEXT
        || TextUtils.isEmpty(suggestion)
        || spellingSuggestion == null) {
      return false;
    }

    @Nullable CharSequence currentText = AccessibilityNodeInfoUtils.getText(node);
    if (TextUtils.isEmpty(currentText) || !(currentText instanceof Spanned)) {
      return false;
    }

    // Finds the position of the misspelling word.
    int start = spellingSuggestion.start();
    int end = spellingSuggestion.end();
    if (start == -1 || end == -1) {
      LogUtils.d(TAG, "The suggestionSpan is not in the currentText.");
      return false;
    }

    // Replaces text.
    CharSequence textReplaced =
        start == 0
            ? TextUtils.concat(suggestion, currentText.subSequence(end, currentText.length()))
            : TextUtils.concat(
                currentText.subSequence(0, start),
                suggestion,
                currentText.subSequence(end, currentText.length()));
    Bundle arguments = new Bundle();
    arguments.putCharSequence(ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, textReplaced);
    editTextActionHistory.beforeSetText();
    boolean result = PerformActionUtils.performAction(node, ACTION_SET_TEXT, arguments, eventId);
    editTextActionHistory.afterSetText();
    if (result) {
      pipeline.returnFeedback(
          eventId,
          Feedback.speech(service.getString(R.string.text_replaced_feedback), SPEAK_OPTIONS));
    }

    // Moves the cursor to the end of the replaced text.
    return moveCursor(node, start + suggestion.length(), eventId);
  }

  /**
   * Moves the cursor in {@link android.widget.EditText}.
   *
   * @param node where the cursor is moved
   * @param cursorIndex the new position of the cursor
   * @param eventId event ID to perform editing actions
   * @return {@code true} if the cursor move is successful.
   */
  public boolean moveCursor(AccessibilityNodeInfoCompat node, int cursorIndex, EventId eventId) {

    if (node == null || Role.getRole(node) != Role.ROLE_EDIT_TEXT) {
      return false;
    }

    final Bundle args = new Bundle();
    boolean result = false;
    textCursorTracker.forceSetCursorPosition(cursorIndex, cursorIndex);
    @Nullable CharSequence nodeText = AccessibilityNodeInfoUtils.getText(node);
    if (AccessibilityNodeInfoUtils.supportsAction(
        node, AccessibilityNodeInfoCompat.ACTION_SET_SELECTION)) {
      int cursor = node.getTextSelectionStart();
      if (FeatureSupport.supportInputConnectionByA11yService()
          && stateReader.isSelectionModeActive()
          && cursorIndex != cursor) {
        result = performSelectText(cursor, cursorIndex);
      }
      if (!result) {
        // Perform node-action to move cursor.
        args.putInt(AccessibilityNodeInfoCompat.ACTION_ARGUMENT_SELECTION_START_INT, cursorIndex);
        args.putInt(AccessibilityNodeInfoCompat.ACTION_ARGUMENT_SELECTION_END_INT, cursorIndex);
        result =
            PerformActionUtils.performAction(
                node, AccessibilityNodeInfoCompat.ACTION_SET_SELECTION, args, eventId);
      }
    } else if (cursorIndex == 0) {
      // Fall-back to move cursor to start of text.
      args.putInt(
          AccessibilityNodeInfoCompat.ACTION_ARGUMENT_MOVEMENT_GRANULARITY_INT,
          AccessibilityNodeInfoCompat.MOVEMENT_GRANULARITY_PAGE);
      result =
          PerformActionUtils.performAction(
              node,
              AccessibilityNodeInfoCompat.ACTION_PREVIOUS_AT_MOVEMENT_GRANULARITY,
              args,
              eventId);
    } else if ((nodeText != null) && (cursorIndex == nodeText.length())) {
      // Fall-back to move cursor to end of text.
      args.putInt(
          AccessibilityNodeInfoCompat.ACTION_ARGUMENT_MOVEMENT_GRANULARITY_INT,
          AccessibilityNodeInfoCompat.MOVEMENT_GRANULARITY_PAGE);
      result =
          PerformActionUtils.performAction(
              node, AccessibilityNodeInfoCompat.ACTION_NEXT_AT_MOVEMENT_GRANULARITY, args, eventId);
    }
    return result;
  }

  @CanIgnoreReturnValue
  private boolean performSelectText(int start, int end) {
    if (!FeatureSupport.supportInputConnectionByA11yService()) {
      return false;
    }
    InputMethod inputMethod = service.getInputMethod();
    if (inputMethod == null) {
      return false;
    }
    InputMethod.AccessibilityInputConnection inputConnection =
        inputMethod.getCurrentInputConnection();
    if (inputConnection == null) {
      return false;
    }
    inputConnection.setSelection(start, end);
    return true;
  }
}
