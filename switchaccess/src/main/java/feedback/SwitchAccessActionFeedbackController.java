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

package com.google.android.accessibility.switchaccess.feedback;

import android.content.Context;
import android.os.Bundle;
import android.text.TextUtils;
import com.google.android.accessibility.switchaccess.R;
import com.google.android.accessibility.switchaccess.proto.SwitchAccessMenuTypeEnum.MenuType;
import com.google.android.accessibility.switchaccess.treenodes.TreeScanLeafNode;
import com.google.android.accessibility.switchaccess.treenodes.TreeScanNode;
import com.google.android.accessibility.switchaccess.treenodes.TreeScanSelectionNode;
import com.google.android.accessibility.switchaccess.treenodes.TreeScanSystemProvidedNode;
import com.google.android.accessibility.utils.output.FeedbackController;
import com.google.android.accessibility.utils.output.SpeechController;
import com.google.android.accessibility.utils.output.SpeechControllerImpl;

/**
 * Controller used to provide spoken, vibration, and sound feedback to acknowledge the completion of
 * an action.
 */
public class SwitchAccessActionFeedbackController {

  private final Context context;
  private final SpeechControllerImpl speechController;
  private final FeedbackController feedbackController;

  public SwitchAccessActionFeedbackController(
      Context context,
      SpeechControllerImpl speechController,
      FeedbackController feedbackController) {
    this.context = context;
    this.speechController = speechController;
    this.feedbackController = feedbackController;
  }

  /**
   * Speaks the text of a key typed on the on-screen keyboard. This method should be called whenever
   * a key from the on-screen keyboard is typed.
   *
   * @param node The {@link TreeScanSystemProvidedNode} that corresponds to the key that was typed
   *     from the on-screen keyboard
   */
  void onKeyTyped(TreeScanSystemProvidedNode node) {
    speechController.speak(
        node.getNodeInfoCompat().getNodeText(),
        SpeechController.QUEUE_MODE_CAN_IGNORE_INTERRUPTS,
        0, /* flags */
        null, /* speechParams */
        null); /* eventId */
  }

  /**
   * Called whenever we are about to rebuild the tree while the user is in the middle of scanning.
   * If this method is called with spoken feedback enabled, spoken feedback indicates that the
   * screen has changed.
   */
  void onTreeRebuiltDuringScanning() {
    // Provide spoken feedback to indicate that the screen has changed if the tree was rebuilt
    // while the user was in the middle of scanning.
    onActionCompleted();
    speechController.speak(
        context.getString(R.string.switch_access_focus_cleared_no_selection),
        SpeechController.QUEUE_MODE_UNINTERRUPTIBLE_BY_NEW_SPEECH_CAN_IGNORE_INTERRUPTS,
        0, /* flags */
        null, /* speechParams */
        null); /* eventId */
  }

  /**
   * Speaks a message to acknowledge the selection of a group. This method should be called whenever
   * a group is selected.
   *
   * @param optionIndex The index of the selected group
   */
  void onGroupSelected(int optionIndex) {
    onActionCompleted();
    speechController.speak(
        context.getString(
            R.string.switch_access_spoken_feedback_group_selected,
            Integer.toString(optionIndex + 1)),
        SpeechController.QUEUE_MODE_CAN_IGNORE_INTERRUPTS,
        0, /* flags */
        null, /* speechParams */
        null); /* eventId */
  }

  /**
   * Speaks a message to acknowledge the selection of an actionable item or a row. This method
   * should be called whenever the user selects an actionable item or row.
   *
   * @param currentNode The {@link TreeScanNode} which corresponds to the actionable item or the row
   *     that was selected
   */
  void onNodeSelected(TreeScanNode currentNode) {
    CharSequence msg =
        (currentNode instanceof TreeScanSelectionNode)
            ? context.getString(R.string.switch_access_spoken_feedback_row_selected)
            : context.getString(
                R.string.switch_access_spoken_feedback_item_selected,
                TextUtils.join(" ", ((TreeScanLeafNode) currentNode).getSpeakableText()));

    int queueMode =
        (currentNode instanceof TreeScanSelectionNode)
            ? SpeechController.QUEUE_MODE_UNINTERRUPTIBLE_BY_NEW_SPEECH
            : SpeechController.QUEUE_MODE_UNINTERRUPTIBLE_BY_NEW_SPEECH_CAN_IGNORE_INTERRUPTS;

    onActionCompleted();
    speechController.speak(
        msg, queueMode, 0, /* flags */ null, /* speechParams */ null /* eventId */);
  }

  /**
   * Speaks a message to indicate that a Switch Access menu showed up on the screen.
   *
   * @param menuType Type of the Switch Access menu that showed up
   */
  void onSwitchAccessMenuShown(MenuType menuType) {
    int screenHintResource =
        (menuType == MenuType.TYPE_GLOBAL)
            ? R.string.switch_access_global_menu
            : R.string.switch_access_actions_menu;
    onActionCompleted();
    // Speech mode should be QUEUE_MODE_CAN_IGNORE_INTERRUPTS because when the Switch Access menu
    // shows up, the scan tree will be rebuilt, which will force the highlight on the screen to be
    // cleared and speechController#interrupt to be called.
    speechController.speak(
        context.getResources().getString(screenHintResource),
        SpeechController.QUEUE_MODE_UNINTERRUPTIBLE_BY_NEW_SPEECH_CAN_IGNORE_INTERRUPTS,
        0, /* flags */
        new Bundle(), /* speechParams */
        null); /* eventId */
  }

  /** Called after a selection is made by Switch Access. */
  private void onActionCompleted() {
    feedbackController.playActionCompletionFeedback();
  }
}
