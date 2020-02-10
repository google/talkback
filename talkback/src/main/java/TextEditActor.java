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

package com.google.android.accessibility.talkback;

import static androidx.core.view.accessibility.AccessibilityNodeInfoCompat.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE;
import static androidx.core.view.accessibility.AccessibilityNodeInfoCompat.ACTION_SET_TEXT;
import static com.google.android.accessibility.talkback.Feedback.FocusDirection.Action.SELECTION_MODE_OFF;
import static com.google.android.accessibility.talkback.Feedback.FocusDirection.Action.SELECTION_MODE_ON;
import static com.google.android.accessibility.utils.output.FeedbackItem.FLAG_FORCED_FEEDBACK_AUDIO_PLAYBACK_ACTIVE;
import static com.google.android.accessibility.utils.output.FeedbackItem.FLAG_FORCED_FEEDBACK_MICROPHONE_ACTIVE;
import static com.google.android.accessibility.utils.output.FeedbackItem.FLAG_FORCED_FEEDBACK_SSB_ACTIVE;
import static com.google.android.accessibility.utils.output.SpeechController.QUEUE_MODE_INTERRUPT;

import android.content.Context;
import android.os.Bundle;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import android.text.TextUtils;
import com.google.android.accessibility.utils.AccessibilityNodeInfoUtils;
import com.google.android.accessibility.utils.EditTextActionHistory;
import com.google.android.accessibility.utils.PerformActionUtils;
import com.google.android.accessibility.utils.Performance.EventId;
import com.google.android.accessibility.utils.Role;
import com.google.android.accessibility.utils.SpeechCleanupUtils;
import com.google.android.accessibility.utils.input.TextCursorManager;
import com.google.android.accessibility.utils.output.SpeechController.SpeakOptions;
import com.google.android.libraries.accessibility.utils.log.LogUtils;
import org.checkerframework.checker.nullness.qual.Nullable;

/** Executes text-editing actions on EditText views. */
public class TextEditActor {

  /////////////////////////////////////////////////////////////////////////////////////////////
  // Constants

  /**
   * It makes sense to interrupt all the previous utterances generated in the local context menu.
   * After the cursor action is performed, it's the most important to notify the user what happens
   * to the edit text.
   */
  private static final SpeakOptions SPEAK_OPTIONS =
      SpeakOptions.create()
          .setQueueMode(QUEUE_MODE_INTERRUPT)
          .setFlags(
              FLAG_FORCED_FEEDBACK_AUDIO_PLAYBACK_ACTIVE
                  | FLAG_FORCED_FEEDBACK_MICROPHONE_ACTIVE
                  | FLAG_FORCED_FEEDBACK_SSB_ACTIVE);

  /////////////////////////////////////////////////////////////////////////////////////////////
  // Member variables

  private final Context context;
  private final EditTextActionHistory editTextActionHistory;
  private final TextCursorManager textCursorManager;
  private Pipeline.FeedbackReturner pipeline;

  /////////////////////////////////////////////////////////////////////////////////////////////
  // Construction

  public TextEditActor(
      Context context,
      EditTextActionHistory editTextActionHistory,
      TextCursorManager textCursorManager) {
    this.context = context;
    this.editTextActionHistory = editTextActionHistory;
    this.textCursorManager = textCursorManager;
  }

  public void setPipeline(Pipeline.FeedbackReturner pipeline) {
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
    CharSequence nodeText = node.getText();
    boolean result = false;
    if (nodeText != null) {
      result = moveCursor(node, nodeText.length(), eventId);
    }

    // Announce cursor movement.
    pipeline.returnFeedback(
        eventId,
        Feedback.speech(context.getString(R.string.notification_type_end_of_field), SPEAK_OPTIONS));

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
            context.getString(R.string.notification_type_beginning_of_field), SPEAK_OPTIONS));

    return result;
  }

  /** Executes and announces start selecting text in edit-text. */
  public boolean startSelect(AccessibilityNodeInfoCompat node, EventId eventId) {

    if (node == null || Role.getRole(node) != Role.ROLE_EDIT_TEXT) {
      return false;
    }

    // Start selecting.
    pipeline.returnFeedback(eventId, Feedback.focusDirection(SELECTION_MODE_ON));

    // Announce selecting started.
    pipeline.returnFeedback(
        eventId,
        Feedback.speech(
            context.getString(R.string.notification_type_selection_mode_on), SPEAK_OPTIONS));

    return true;
  }

  /** Executes and announces end select text in edit-text. Modifies edit history. */
  public boolean endSelect(AccessibilityNodeInfoCompat node, EventId eventId) {

    if (node == null || Role.getRole(node) != Role.ROLE_EDIT_TEXT) {
      return false;
    }

    // Stop selecting.
    pipeline.returnFeedback(eventId, Feedback.focusDirection(SELECTION_MODE_OFF));

    // Announce selecting stopped.
    pipeline.returnFeedback(
        eventId,
        Feedback.speech(
            context.getString(R.string.notification_type_selection_mode_off), SPEAK_OPTIONS));

    @Nullable
    CharSequence textSelected =
        AccessibilityNodeInfoUtils.subsequenceSafe(
            node.getText(), node.getTextSelectionStart(), node.getTextSelectionEnd());
    CharSequence textToSpeak =
        TextUtils.isEmpty(textSelected)
            ? context.getString(R.string.template_no_text_selected)
            : context.getString(R.string.template_announce_selected_text, textSelected);
    // Uses another speak options not to interrupt previous speech.
    SpeakOptions speakOptions =
        SpeakOptions.create()
            .setFlags(
                FLAG_FORCED_FEEDBACK_AUDIO_PLAYBACK_ACTIVE
                    | FLAG_FORCED_FEEDBACK_MICROPHONE_ACTIVE
                    | FLAG_FORCED_FEEDBACK_SSB_ACTIVE);
    pipeline.returnFeedback(eventId, Feedback.speech(textToSpeak, speakOptions));

    return true;
  }

  /** Executes and announces select-all text in edit-text. Modifies edit history. */
  public boolean selectAll(AccessibilityNodeInfoCompat node, EventId eventId) {

    if (node == null || Role.getRole(node) != Role.ROLE_EDIT_TEXT) {
      return false;
    }

    editTextActionHistory.beforeSelectAll();

    // Execute select-all on target node.
    final Bundle args = new Bundle();
    args.putInt(AccessibilityNodeInfoCompat.ACTION_ARGUMENT_SELECTION_START_INT, 0);
    args.putInt(
        AccessibilityNodeInfoCompat.ACTION_ARGUMENT_SELECTION_END_INT, node.getText().length());
    boolean result =
        PerformActionUtils.performAction(
            node, AccessibilityNodeInfoCompat.ACTION_SET_SELECTION, args, eventId);
    if (!result) {
      return result;
    }
    editTextActionHistory.afterSelectAll();

    // Announce selected.
    pipeline.returnFeedback(
        eventId,
        Feedback.speech(
            SpeechCleanupUtils.cleanUp(
                context,
                context.getString(R.string.template_announce_selected_text, node.getText())),
            SPEAK_OPTIONS));
    return result;
  }

  /** Executes and announces copy text in edit-text. */
  public boolean copy(AccessibilityNodeInfoCompat node, EventId eventId) {

    if (node == null || Role.getRole(node) != Role.ROLE_EDIT_TEXT) {
      return false;
    }

    // Perform copy action on target node.
    boolean result =
        PerformActionUtils.performAction(node, AccessibilityNodeInfoCompat.ACTION_COPY, eventId);

    CharSequence copyData = AccessibilityNodeInfoUtils.getSelectedNodeText(node);
    if (copyData != null) {
      // Announce copied.
      pipeline.returnFeedback(
          eventId,
          Feedback.speech(
              context.getString(R.string.template_text_copied, copyData.toString()),
              SPEAK_OPTIONS));
    }
    return result;
  }

  /** Executes and announces cut text in edit-text. Modifies edit history. */
  public boolean cut(AccessibilityNodeInfoCompat node, EventId eventId) {

    if (node == null || Role.getRole(node) != Role.ROLE_EDIT_TEXT) {
      return false;
    }

    editTextActionHistory.beforeCut();
    boolean result =
        PerformActionUtils.performAction(node, AccessibilityNodeInfoCompat.ACTION_CUT, eventId);
    editTextActionHistory.afterCut();
    return result;
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
    return result;
  }

  /** Inserts text in edit-text. Modifies edit history. */
  public boolean insert(
      AccessibilityNodeInfoCompat node, CharSequence textToInsert, EventId eventId) {

    if (node == null || Role.getRole(node) != Role.ROLE_EDIT_TEXT) {
      return false;
    }

    // Find current selected text or cursor position.
    int selectionStart = node.getTextSelectionStart();
    if (selectionStart < 0) {
      selectionStart = 0;
    }
    int selectionEnd = node.getTextSelectionEnd();
    if (selectionEnd < 0) {
      selectionEnd = selectionStart;
    }
    if (selectionEnd < selectionStart) {
      // Swap start and end to make sure they are in order.
      int newStart = selectionEnd;
      selectionEnd = selectionStart;
      selectionStart = newStart;
    }
    CharSequence currentText = node.getText();
    LogUtils.v(
        "RuleEditText",
        "insert() currentText=\"%s\"",
        (currentText == null ? "null" : currentText));
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

  /** Moves cursor in edit-text. */
  private boolean moveCursor(AccessibilityNodeInfoCompat node, int cursorIndex, EventId eventId) {

    if (node == null || Role.getRole(node) != Role.ROLE_EDIT_TEXT) {
      return false;
    }

    final Bundle args = new Bundle();
    boolean result = false;
    textCursorManager.forceSetCursorPosition(cursorIndex, cursorIndex);
    CharSequence nodeText = node.getText();
    if (AccessibilityNodeInfoUtils.supportsAction(
        node, AccessibilityNodeInfoCompat.ACTION_SET_SELECTION)) {
      // Perform node-action to move cursor.
      args.putInt(AccessibilityNodeInfoCompat.ACTION_ARGUMENT_SELECTION_START_INT, cursorIndex);
      args.putInt(AccessibilityNodeInfoCompat.ACTION_ARGUMENT_SELECTION_END_INT, cursorIndex);
      result =
          PerformActionUtils.performAction(
              node, AccessibilityNodeInfoCompat.ACTION_SET_SELECTION, args, eventId);
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
    } else if (nodeText != null && cursorIndex == node.getText().length()) {
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
}
