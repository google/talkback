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

import static com.google.android.accessibility.switchaccess.utils.TextEditingUtils.ACTION_DELETE_TEXT;
import static com.google.android.accessibility.switchaccess.utils.TextEditingUtils.ACTION_GRANULARITY_ALL;

import android.accessibilityservice.AccessibilityService;
import android.os.Bundle;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat.AccessibilityActionCompat;
import android.text.TextUtils;
import android.view.accessibility.AccessibilityNodeInfo;
import com.google.android.accessibility.switchaccess.SwitchAccessAction;
import com.google.android.accessibility.switchaccess.SwitchAccessActionBase;
import com.google.android.accessibility.switchaccess.SwitchAccessActionGroup;
import com.google.android.accessibility.switchaccess.SwitchAccessActionTimeline;
import com.google.android.accessibility.switchaccess.SwitchAccessNodeCompat;
import com.google.android.accessibility.switchaccess.SwitchAccessPreferenceUtils;
import com.google.android.accessibility.utils.Role;
import com.google.android.libraries.accessibility.utils.undo.UndoRedoManager;
import com.google.android.libraries.accessibility.utils.undo.UndoRedoManager.RecycleBehavior;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Utility class for handling action creation for {@link SwitchAccessNodeCompat}s */
public class ActionBuildingUtils {

  // TODO Support all actions, conditioned on user preferences.
  private static final Set<Integer> FRAMEWORK_ACTIONS =
      new HashSet<>(
          Arrays.asList(
              AccessibilityNodeInfoCompat.ACTION_CLICK,
              AccessibilityNodeInfoCompat.ACTION_COLLAPSE,
              AccessibilityNodeInfoCompat.ACTION_COPY,
              AccessibilityNodeInfoCompat.ACTION_CUT,
              AccessibilityNodeInfoCompat.ACTION_DISMISS,
              AccessibilityNodeInfoCompat.ACTION_EXPAND,
              AccessibilityNodeInfoCompat.ACTION_LONG_CLICK,
              AccessibilityNodeInfoCompat.ACTION_NEXT_AT_MOVEMENT_GRANULARITY,
              AccessibilityNodeInfoCompat.ACTION_PASTE,
              AccessibilityNodeInfoCompat.ACTION_PREVIOUS_AT_MOVEMENT_GRANULARITY,
              AccessibilityNodeInfoCompat.ACTION_SCROLL_BACKWARD,
              AccessibilityNodeInfoCompat.ACTION_SCROLL_FORWARD,
              AccessibilityNodeInfoCompat.ACTION_SET_SELECTION));

  private static final int SYSTEM_ACTION_MAX = 0x01FFFFFF;

  /**
   * Returns {@code true} if the given list of {@link SwitchAccessNodeCompat}s supports multiple
   * Switch Access actions.
   *
   * <p>Note: methods #isActionSupportedByNode and #getActionsByNode should be updated if the logic
   * of this one changes.
   *
   * @param service The {@link AccessibilityService} used to check whether auto-select is enabled
   * @param nodeCompats A list of {@link SwitchAccessNodeCompat}s which contains a base
   *     SwitchAccessNodeCompat and all its children that have duplicate bounds and support actions
   */
  public static boolean hasMultipleActions(
      AccessibilityService service, List<SwitchAccessNodeCompat> nodeCompats) {
    int actionCount = 0;
    boolean autoSelectEnabled = SwitchAccessPreferenceUtils.isAutoselectEnabled(service);

    for (int i = 0; i < nodeCompats.size(); i++) {
      SwitchAccessNodeCompat nodeCompat = nodeCompats.get(i);
      List<AccessibilityActionCompat> originalActions = nodeCompat.getActionList();

      for (AccessibilityActionCompat action : originalActions) {
        if ((action.getId() == AccessibilityNodeInfoCompat.ACTION_CLICK) && autoSelectEnabled) {
          return false;
        }

        if (isActionSupportedBySwitchAccess(action)) {
          if (isMovementAction(action.getId())) {
            List<SwitchAccessActionBase> actions =
                getSwitchAccessMovementActionsForNode(nodeCompat, action);
            actionCount += actions.size();

            if ((actionCount > 1) && !autoSelectEnabled) {
              return true;
            }
          } else if (action.getId() == AccessibilityNodeInfoCompat.ACTION_SET_SELECTION) {
            List<SwitchAccessActionBase> actions =
                getSwitchAccessActionsForSetSelectionAction(nodeCompat, action);
            actionCount += actions.size();

            if ((actionCount > 1) && !autoSelectEnabled) {
              return true;
            }
          } else {
            actionCount++;
            if ((actionCount > 1) && !autoSelectEnabled) {
              return true;
            }
          }
        }
      }
    }

    return (actionCount > 1);
  }

  /**
   * Returns whether the given {@link AccessibilityActionCompat} is supported by the given {@link
   * SwitchAccessNodeCompat}. If the action is a movement action, return {@code true} if the given
   * node is editable and has nonempty text. If the action is a selection action, return {@code
   * true} if the given node is an EditText and has nonempty text. Otherwise, all other supported
   * actions are possible.
   *
   * <p>Note: methods #hasMultipleActions and #getActionsForNode should be updated if the logic of
   * this one changes.
   *
   * @param action The action to check
   * @param nodeCompat The node for which we will check whether it is possible to perform the
   *     provided action
   * @return {@code true} if the given {@link AccessibilityActionCompat} is supported by the given
   *     {@link SwitchAccessNodeCompat}
   */
  public static boolean isActionSupportedByNode(
      AccessibilityActionCompat action, SwitchAccessNodeCompat nodeCompat) {
    if (isActionSupportedBySwitchAccess(action)) {
      boolean isMovementOption = isMovementAction(action.getId());
      boolean isSelection = (action.getId() == AccessibilityNodeInfoCompat.ACTION_SET_SELECTION);
      if (!isMovementOption && !isSelection) {
        return true;
      }

      boolean textIsEmpty = TextUtils.isEmpty(nodeCompat.getText());
      boolean canMoveInNode = isMovementOption && nodeCompat.isEditable() && !textIsEmpty;
      if (canMoveInNode) {
        return true;
      }

      boolean isSelectingNonEmptyEditTextNode =
          isSelection && (Role.getRole(nodeCompat) == Role.ROLE_EDIT_TEXT) && !textIsEmpty;
      if (isSelectingNonEmptyEditTextNode) {
        return true;
      }
    }

    return false;
  }

  /**
   * Get the {@link SwitchAccessActionBase}s that the given {@link SwitchAccessNodeCompat} can
   * perform.
   *
   * <p>Note: methods #hasMultipleActions and #isActionSupportedByNode should be updated if the
   * logic of this one changes.
   *
   * @param service The {@link AccessibilityService} used to check whether auto-select is enabled
   * @param nodeCompat The {@link SwitchAccessNodeCompat} to get actions for
   * @param actionTimeline The {@link SwitchAccessActionTimeline} used for the given {@code
   *     nodeCompat) if it does not already have one. The timeline is used to check if the {@code
   *     nodeCompat} has undo or redo actions
   * @return A list of {@link SwitchAccessActionBase}s that the given {@link SwitchAccessNodeCompat}
   *     can perform
   */
  public static List<SwitchAccessActionBase> getActionsForNode(
      AccessibilityService service,
      SwitchAccessNodeCompat nodeCompat,
      SwitchAccessActionTimeline actionTimeline) {
    // Get the latest text in this node. If the user types something without using the switches, the
    // node doesn't get updated automatically.
    nodeCompat.refresh();
    List<SwitchAccessActionBase> actions = new ArrayList<>();
    List<AccessibilityActionCompat> originalActions = nodeCompat.getActionList();
    boolean autoselectEnabled = SwitchAccessPreferenceUtils.isAutoselectEnabled(service);
    for (AccessibilityActionCompat action : originalActions) {
      if (autoselectEnabled && (action.getId() == AccessibilityNodeInfoCompat.ACTION_CLICK)) {
        actions.clear();
        actions.add(new SwitchAccessAction(nodeCompat, action));
        return actions;
      }
      if (isActionSupportedBySwitchAccess(action)) {
        if (isMovementAction(action.getId())) {
          actions.addAll(getSwitchAccessMovementActionsForNode(nodeCompat, action));
        } else if (action.getId() == AccessibilityNodeInfoCompat.ACTION_SET_SELECTION) {
          actions.addAll(getSwitchAccessActionsForSetSelectionAction(nodeCompat, action));
        } else {
          actions.add(new SwitchAccessAction(nodeCompat, action));
        }
      }
    }

    // Get a copy of nodeCompat so that if it is recycled before the corresponding timeline is
    // removed, the timeline doesn't act on a recycled node.
    SwitchAccessNodeCompat node = nodeCompat.obtainCopy();
    SwitchAccessActionTimeline switchAccessActionTimeline =
        (SwitchAccessActionTimeline)
            UndoRedoManager.getInstance(RecycleBehavior.DO_RECYCLE_NODES)
                .getTimelineForNodeCompat(node, actionTimeline);
    if (switchAccessActionTimeline == null) {
      node.recycle();
    } else if (node.isEditable()) {
      if (switchAccessActionTimeline.canPerformUndo()) {
        actions.add(new SwitchAccessAction(node, TextEditingUtils.ACTION_UNDO));
      }
      if (switchAccessActionTimeline.canPerformRedo()) {
        actions.add(new SwitchAccessAction(node, TextEditingUtils.ACTION_REDO));
      }
    }

    return actions;
  }

  /** Return if the given action id represents a movement action. */
  private static boolean isMovementAction(int actionId) {
    return (actionId == AccessibilityNodeInfoCompat.ACTION_PREVIOUS_AT_MOVEMENT_GRANULARITY)
        || (actionId == AccessibilityNodeInfoCompat.ACTION_NEXT_AT_MOVEMENT_GRANULARITY);
  }

  /* Return if the given action is supported by Switch Access. */
  private static boolean isActionSupportedBySwitchAccess(AccessibilityActionCompat action) {
    // White-listed framework actions
    if (action.getId() <= SYSTEM_ACTION_MAX) {
      return FRAMEWORK_ACTIONS.contains(action.getId());
    }
    // Support custom actions with proper labels.
    return !TextUtils.isEmpty(action.getLabel());
  }

  /**
   * Gets the {@link SwitchAccessActionBase}s that the given {@link AccessibilityActionCompat} can
   * perform on the given editable {@link SwitchAccessNodeCompat}.
   *
   * @param nodeCompat The {@link SwitchAccessNodeCompat} for which to get actions. This node must
   *     be editable.
   * @param action The {@link AccessibilityActionCompat} that is performed on the given {@link
   *     SwitchAccessNodeCompat}
   * @return A list of {@link SwitchAccessActionBase}s that the given {@link
   *     AccessibilityActionCompat} can perform on the given {@link SwitchAccessNodeCompat}
   */
  private static List<SwitchAccessActionBase> getSwitchAccessMovementActionsForNode(
      SwitchAccessNodeCompat nodeCompat, AccessibilityActionCompat action) {
    List<SwitchAccessActionBase> actions = new ArrayList<>();

    if (!nodeCompat.isEditable()) {
      return actions;
    }
    /*
     * These actions can populate a long context menu, and all Views with
     * content descriptions support them. We therefore try to filter out what
     * we should surface to provide the user with exactly the set of actions that
     * are relevant to the view.
     */
    if (canMoveInDirection(nodeCompat, action)) {
      if ((nodeCompat.getText() != null) && (nodeCompat.getText().length() == 1)) {
        // We only need the character granularity for text that has only one character.
        Bundle args = new Bundle();
        args.putInt(
            AccessibilityNodeInfo.ACTION_ARGUMENT_MOVEMENT_GRANULARITY_INT,
            AccessibilityNodeInfo.MOVEMENT_GRANULARITY_CHARACTER);
        actions.add(new SwitchAccessAction(nodeCompat, action, args));
      } else {
        // The granular actions for this action group are created in the
        // SwitchAccessActionGroup#onClick method (i.e. when the user selects this group
        // action in the overlay menu).
        actions.add(new SwitchAccessActionGroup(action));
      }

      // Allow deletion of text if there is room to move backwards in the text.
      if (action.getId() == AccessibilityNodeInfoCompat.ACTION_PREVIOUS_AT_MOVEMENT_GRANULARITY) {
        if (TextEditingUtils.isTextSelected(nodeCompat)) {
          // Only allow the highlighted text to be deleted.
          Bundle args = new Bundle();
          args.putInt(
              AccessibilityNodeInfo.ACTION_ARGUMENT_MOVEMENT_GRANULARITY_INT,
              TextEditingUtils.ACTION_GRANULARITY_HIGHLIGHT);
          actions.add(
              new SwitchAccessAction(nodeCompat, ACTION_DELETE_TEXT, null /* label */, args));
        } else if ((nodeCompat.getText() != null) && (nodeCompat.getText().length() == 1)) {
          // We only need the character granularity for text that has only one character.
          Bundle args = new Bundle();
          args.putInt(
              AccessibilityNodeInfo.ACTION_ARGUMENT_MOVEMENT_GRANULARITY_INT,
              AccessibilityNodeInfo.MOVEMENT_GRANULARITY_CHARACTER);
          actions.add(
              new SwitchAccessAction(nodeCompat, ACTION_DELETE_TEXT, null /* label */, args));
        } else {
          actions.add(new SwitchAccessActionGroup(ACTION_DELETE_TEXT));
        }
      }
    }

    return actions;
  }

  /**
   * Gets the {@link SwitchAccessActionBase}s that the given {@link
   * AccessibilityNodeInfoCompat#ACTION_SET_SELECTION} action can perform on the given {@link
   * SwitchAccessNodeCompat}.
   *
   * @param nodeCompat The {@link SwitchAccessNodeCompat} for which to get actions
   * @param action The {@link AccessibilityActionCompat} that is performed on the given {@link
   *     SwitchAccessNodeCompat}. This should be an {@link
   *     AccessibilityNodeInfoCompat#ACTION_SET_SELECTION} action
   * @return A list of {@link SwitchAccessActionBase}s that the given {@link
   *     AccessibilityActionCompat} can perform on the given {@link SwitchAccessNodeCompat}
   */
  private static List<SwitchAccessActionBase> getSwitchAccessActionsForSetSelectionAction(
      SwitchAccessNodeCompat nodeCompat, AccessibilityActionCompat action) {
    List<SwitchAccessActionBase> actions = new ArrayList<>();

    // Ignore nodes that support ACTION_SET_SELECTION but are not EditTexts because many nodes with
    // visible text claim to support the action without doing so, causing a lot of clutter without
    // this restriction. The expected primary use case for this action is copying or cutting paste
    // from an editable text view, which should not be affected by this pruning.
    if ((Role.getRole(nodeCompat) == Role.ROLE_EDIT_TEXT)
        && !TextUtils.isEmpty(nodeCompat.getText())) {
      boolean textIsSelected = TextEditingUtils.isTextSelected(nodeCompat);
      if (!textIsSelected
          && (nodeCompat.getTextSelectionStart() > 0)
          && (nodeCompat.getText().length() == 1)) {
        // We only need the character granularity for text that has only one character.
        Bundle args = new Bundle();
        args.putInt(
            AccessibilityNodeInfo.ACTION_ARGUMENT_MOVEMENT_GRANULARITY_INT,
            AccessibilityNodeInfo.MOVEMENT_GRANULARITY_CHARACTER);
        actions.add(new SwitchAccessAction(nodeCompat, action, args));
      } else if (nodeCompat.getTextSelectionStart() > 0) {
        // Allow selection as long as there are characters before the current selection that
        // are not selected because granular selection is possible.
        actions.add(new SwitchAccessActionGroup(AccessibilityActionCompat.ACTION_SET_SELECTION));
      } else if (textIsSelected
          && (nodeCompat.getTextSelectionEnd() < nodeCompat.getText().length())) {
        // Allow user to select all text as long as some portion of the text is not
        // highlighted.
        Bundle args = new Bundle();
        args.putInt(
            AccessibilityNodeInfoCompat.ACTION_ARGUMENT_MOVEMENT_GRANULARITY_INT,
            ACTION_GRANULARITY_ALL);
        actions.add(
            new SwitchAccessAction(
                nodeCompat, AccessibilityActionCompat.ACTION_SET_SELECTION, args));
      }

      // Allow copying and cutting all text only if no text is currently selected. If any
      // text is selected, copy and cut selection actions are automatically added.
      if (!textIsSelected) {
        Bundle copyArgs = new Bundle();
        copyArgs.putInt(
            AccessibilityNodeInfoCompat.ACTION_ARGUMENT_MOVEMENT_GRANULARITY_INT,
            ACTION_GRANULARITY_ALL);
        actions.add(
            new SwitchAccessAction(nodeCompat, AccessibilityActionCompat.ACTION_COPY, copyArgs));
        actions.add(
            new SwitchAccessAction(nodeCompat, AccessibilityActionCompat.ACTION_CUT, copyArgs));
      }
    }

    return actions;
  }

  /**
   * Returns if the given action can move the focus to the next entity in the given node's text.
   *
   * @param nodeCompat the {@link SwitchAccessNodeCompat} that corresponds to a view on the screen
   * @param action the action to check. This should be a movement action that is supported by Switch
   *     Access
   * @return {@code true} if the given {@link AccessibilityActionCompat} can move the focus to the
   *     next entity in the given {@link SwitchAccessNodeCompat}'s text
   */
  private static boolean canMoveInDirection(
      SwitchAccessNodeCompat nodeCompat, AccessibilityActionCompat action) {
    boolean canMoveInDirection = !TextUtils.isEmpty(nodeCompat.getText());
    if (canMoveInDirection
        && (nodeCompat.getTextSelectionStart() == nodeCompat.getTextSelectionEnd())) {
      // Nothing is selected.
      int cursorPosition = nodeCompat.getTextSelectionStart();
      boolean forward =
          (action.getId() == AccessibilityNodeInfoCompat.ACTION_NEXT_AT_MOVEMENT_GRANULARITY);
      canMoveInDirection &= !(forward && cursorPosition == nodeCompat.getText().length());
      canMoveInDirection &= !(!forward && cursorPosition == 0);
      canMoveInDirection &= cursorPosition >= 0;
    }
    return canMoveInDirection;
  }
}
