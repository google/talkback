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

package com.google.android.accessibility.switchaccess;

import static android.view.accessibility.AccessibilityNodeInfo.ACTION_ARGUMENT_MOVEMENT_GRANULARITY_INT;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat.AccessibilityActionCompat;
import android.view.accessibility.AccessibilityNodeInfo;
import com.google.android.accessibility.switchaccess.treenodes.ShowActionsMenuNode;
import com.google.android.accessibility.switchaccess.utils.TextEditingUtils;
import com.google.android.accessibility.switchaccess.utils.TextEditingUtils.MovementDirection;
import com.google.android.accessibility.utils.AccessibilityNodeInfoUtils;
import com.google.android.accessibility.utils.PerformActionUtils;
import com.google.android.accessibility.utils.SpannableUrl;
import com.google.android.accessibility.utils.UrlUtils;
import com.google.android.libraries.accessibility.utils.undo.TimelineAction;
import com.google.android.libraries.accessibility.utils.undo.TimelineAction.Undoable;
import com.google.common.collect.ImmutableSet;
import java.util.ArrayList;
import java.util.List;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Holds all action data that Switch Access needs to identify an action. This data is used to query
 * which action the user wishes to perform if an {@link ShowActionsMenuNode} supports multiple
 * actions and to perform an action on the corresponding {@link SwitchAccessNodeCompat}.
 */
public class SwitchAccessAction extends SwitchAccessActionBase implements Undoable {

  // All possible actions that have an inverse action (i.e. they are undoable). When adding a value
  // to this array, a matching case should be added to #generateInverseAction method.
  // LINT.IfChange
  private static final ImmutableSet<Integer> UNDOABLE_ACTIONS =
      ImmutableSet.of(
          AccessibilityNodeInfoCompat.ACTION_NEXT_AT_MOVEMENT_GRANULARITY,
          AccessibilityNodeInfoCompat.ACTION_PREVIOUS_AT_MOVEMENT_GRANULARITY,
          AccessibilityNodeInfoCompat.ACTION_SET_SELECTION,
          AccessibilityNodeInfoCompat.ACTION_CUT,
          AccessibilityNodeInfoCompat.ACTION_PASTE,
          TextEditingUtils.ACTION_DELETE_TEXT);
  // LINT.ThenChange(//depot/google3/java/com/google/android/accessibility/switchaccess/SwitchAccessAction.java:generateInverseAction)

  // When there is no text selection, the cursor position is set to -1. Performing selection with
  // this argument does not perform the selection.
  private static final int CURSOR_POSITION_UNKNOWN = -1;

  private SwitchAccessNodeCompat nodeCompat;
  private final Bundle args;

  // The start and end selection indices in this action's nodeCompat before this was executed.
  private int previousSelectionStart = CURSOR_POSITION_UNKNOWN;
  private int previousSelectionEnd = CURSOR_POSITION_UNKNOWN;

  // The text content of this action's nodeCompat before this action was executed.
  private CharSequence previousTextContent = "";

  /**
   * @param nodeCompat The {@link SwitchAccessNodeCompat} to perform this action on
   * @param id The id used to identify this action. Should correspond to a valid {@link
   *     AccessibilityActionCompat} id.
   */
  public SwitchAccessAction(SwitchAccessNodeCompat nodeCompat, int id) {
    this(nodeCompat, id, null);
  }

  /**
   * @param nodeCompat The {@link SwitchAccessNodeCompat} to perform this action on
   * @param id The id used to identify this action. Should correspond to a valid {@link
   *     AccessibilityActionCompat} id.
   * @param label The human readable label used to identify this action
   */
  public SwitchAccessAction(
      SwitchAccessNodeCompat nodeCompat, int id, @Nullable CharSequence label) {
    this(nodeCompat, id, label, Bundle.EMPTY);
  }

  /**
   * @param nodeCompat The {@link SwitchAccessNodeCompat} to perform this action on
   * @param id The id used to identify this action. Should correspond to a valid {@link
   *     AccessibilityActionCompat} id.
   * @param label The human readable label used to identify this action
   * @param args Additional arguments to use when performing this action
   */
  public SwitchAccessAction(
      SwitchAccessNodeCompat nodeCompat, int id, @Nullable CharSequence label, Bundle args) {
    super(id, label);
    this.nodeCompat = nodeCompat;
    this.args = args;
  }

  /**
   * @param nodeCompat The {@link SwitchAccessNodeCompat} to perform this action on
   * @param actionCompat The {@link AccessibilityActionCompat} whose id and label to use to identify
   *     this action
   */
  public SwitchAccessAction(
      SwitchAccessNodeCompat nodeCompat, AccessibilityActionCompat actionCompat) {
    this(nodeCompat, actionCompat, Bundle.EMPTY);
  }

  /**
   * @param nodeCompat The {@link SwitchAccessNodeCompat} to perform this action on
   * @param actionCompat The {@link AccessibilityActionCompat} whose id and label to use to identify
   *     this action
   * @param args Additional arguments to use when performing this action
   */
  public SwitchAccessAction(
      SwitchAccessNodeCompat nodeCompat, AccessibilityActionCompat actionCompat, Bundle args) {
    this(nodeCompat, actionCompat.getId(), actionCompat.getLabel(), args);
  }

  /** Returns any additional arguments to be used when performing this action. */
  public Bundle getArgs() {
    return args;
  }

  /** Update the {@link SwitchAccessNodeCompat} stored for this action. */
  public void setNodeCompat(SwitchAccessNodeCompat nodeCompat) {
    this.nodeCompat = nodeCompat;
  }

  @Override
  public ActionResult execute(AccessibilityService service) {
    previousSelectionStart = Math.max(0, nodeCompat.getTextSelectionStart());
    previousSelectionEnd = Math.max(0, nodeCompat.getTextSelectionEnd());
    previousTextContent = TextEditingUtils.getNonDefaultTextForNode(nodeCompat);

    switch (getId()) {
      case TextEditingUtils.ACTION_DELETE_TEXT:
        // Delete text with granularity according to args.
        return new ActionResult(TextEditingUtils.deleteTextWithGranularity(nodeCompat, args));
      case AccessibilityNodeInfo.ACTION_SET_SELECTION:
        // Select text with granularity according to args.
        return new ActionResult(TextEditingUtils.selectTextWithGranularity(nodeCompat, args));
      case AccessibilityNodeInfo.ACTION_NEXT_AT_MOVEMENT_GRANULARITY:
      case AccessibilityNodeInfo.ACTION_PREVIOUS_AT_MOVEMENT_GRANULARITY:
        if ((args.getInt(ACTION_ARGUMENT_MOVEMENT_GRANULARITY_INT)
            == TextEditingUtils.ACTION_GRANULARITY_SENTENCE)) {
          MovementDirection movementDirection =
              (getId() == AccessibilityNodeInfo.ACTION_NEXT_AT_MOVEMENT_GRANULARITY)
                  ? TextEditingUtils.MovementDirection.DIRECTION_NEXT
                  : TextEditingUtils.MovementDirection.DIRECTION_PREVIOUS;
          // Sentence granularity doesn't have built-in support, so move cursor by sentence
          // granularity.
          return new ActionResult(
              TextEditingUtils.moveCursorBySentenceGranularity(nodeCompat, movementDirection));
        } else {
          // Perform the original granular movement
          return new ActionResult(
              PerformActionUtils.performAction(nodeCompat, getId(), args, null /* EventId */));
        }
      case TextEditingUtils.ACTION_UNDO:
        // Undo the previous action on this node's timeline.
        return new ActionResult(TextEditingUtils.performUndo(service, nodeCompat));
      case TextEditingUtils.ACTION_REDO:
        // Redo the next action on this node's timeline.
        return new ActionResult(TextEditingUtils.performRedo(service, nodeCompat));
      case AccessibilityNodeInfo.ACTION_SET_TEXT:
        // Set the text and restore the cursor position.
        return new ActionResult(TextEditingUtils.setText(nodeCompat, args));
      case AccessibilityNodeInfo.ACTION_COPY:
      case AccessibilityNodeInfo.ACTION_CUT:
        // For copy and cut actions, select all text as these actions only act on the currently
        // selected text.
        if ((args.getInt(ACTION_ARGUMENT_MOVEMENT_GRANULARITY_INT)
                == TextEditingUtils.ACTION_GRANULARITY_ALL)
            && !TextEditingUtils.selectTextWithGranularity(nodeCompat, args)) {
          return new ActionResult(false);
        } else {
          return new ActionResult(
              PerformActionUtils.performAction(nodeCompat, getId(), args, null /* EventId */));
        }
      case AccessibilityNodeInfo.ACTION_CLICK:
        if (nodeCompat.isClickable() && attemptToClickUrl(service)) {
          return new ActionResult(true /* isSuccessful */);
        }
        // Fall-through if there were no clickable urls.
      default:
        // Perform the original user-visible action.
        return new ActionResult(
            PerformActionUtils.performAction(nodeCompat, getId(), args, null /* EventId */));
    }
  }

  @Override
  @Nullable
  // LINT.IfChange(generateInverseAction)
  public TimelineAction generateInverseAction() {
    Bundle args = new Bundle();
    switch (getId()) {
      case AccessibilityNodeInfoCompat.ACTION_NEXT_AT_MOVEMENT_GRANULARITY:
      case AccessibilityNodeInfoCompat.ACTION_PREVIOUS_AT_MOVEMENT_GRANULARITY:
      case AccessibilityNodeInfoCompat.ACTION_SET_SELECTION:
        // If previousSelectionStart == -1 or previousSelectionEnd == -1, performing this action
        // does nothing.
        args.putInt(
            AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, previousSelectionStart);
        args.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, previousSelectionEnd);
        return new SwitchAccessAction(
            nodeCompat, AccessibilityActionCompat.ACTION_SET_SELECTION, args);
      case AccessibilityNodeInfoCompat.ACTION_CUT:
      case AccessibilityNodeInfoCompat.ACTION_PASTE:
      case TextEditingUtils.ACTION_DELETE_TEXT:
        args.putInt(
            AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, previousSelectionStart);
        args.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, previousSelectionEnd);
        args.putCharSequence(
            AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, previousTextContent);
        return new SwitchAccessAction(nodeCompat, AccessibilityActionCompat.ACTION_SET_TEXT, args);
      default:
        // Action is not undoable.
        return null;
    }
  }
  // LINT.ThenChange()

  /**
   * Returns {@code true} if this action is undoable. This corresponds to actions whose
   * #generateInverseAction returns a non-null TimelineAction.
   */
  public boolean isUndoable() {
    return UNDOABLE_ACTIONS.contains(getId());
  }

  /* Returns {@code true} if the node contained a URL that was clicked. */
  private boolean attemptToClickUrl(AccessibilityService service) {
    List<SpannableUrl> urls = AccessibilityNodeInfoUtils.getNodeUrls(nodeCompat);
    if (urls.isEmpty()) {
      return false;
    }

    if (urls.size() == 1) {
      UrlUtils.openUrlWithIntent(service, urls.get(0).path());
    } else {
      ArrayList<SpannableUrl> links = new ArrayList<>(urls);
      new Handler()
          .post(
              () -> {
                Intent intent = new Intent(service, DialogActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                intent.putParcelableArrayListExtra(DialogActivity.EXTRA_URL_LIST, links);
                service.startActivity(intent);
              });
    }
    return true;
  }
}
