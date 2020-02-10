/*
 * Copyright (C) 2016 Google Inc.
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

package com.google.android.accessibility.switchaccess.menuitems;

import static android.view.accessibility.AccessibilityNodeInfo.ACTION_ARGUMENT_MOVEMENT_GRANULARITY_INT;

import android.accessibilityservice.AccessibilityService;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import android.util.SparseArray;
import com.google.android.accessibility.switchaccess.R;
import com.google.android.accessibility.switchaccess.SwitchAccessAction;
import com.google.android.accessibility.switchaccess.proto.SwitchAccessMenuItemEnum;
import com.google.android.accessibility.switchaccess.ui.MenuButton;
import com.google.android.accessibility.switchaccess.utils.TextEditingUtils;
import com.google.android.libraries.accessibility.utils.undo.ActionTimeline;
import com.google.android.libraries.accessibility.utils.undo.UndoRedoManager;
import com.google.android.libraries.accessibility.utils.undo.UndoRedoManager.RecycleBehavior;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Holds data required to create a menu item that corresponds to a specific action on a specific
 * {@link AccessibilityNodeInfoCompat}.
 */
public class NodeActionMenuItem extends MenuItem {

  // Map of granularities and the human readable labels associated with them. Used when
  // text for ACTION_NEXT_AT_MOVEMENT_GRANULARITY or ACTION_PREVIOUS_AT_MOVEMENT_GRANULARITY
  private static final SparseArray<Integer> MAP_FORWARD_GRANULARITY_IDS = new SparseArray<>();
  private static final SparseArray<Integer> MAP_BACKWARD_GRANULARITY_IDS = new SparseArray<>();

  static {
    MAP_FORWARD_GRANULARITY_IDS.put(
        AccessibilityNodeInfoCompat.MOVEMENT_GRANULARITY_CHARACTER,
        R.string.switch_access_move_next_character);
    MAP_FORWARD_GRANULARITY_IDS.put(
        AccessibilityNodeInfoCompat.MOVEMENT_GRANULARITY_LINE,
        R.string.switch_access_move_next_line);
    MAP_FORWARD_GRANULARITY_IDS.put(
        AccessibilityNodeInfoCompat.MOVEMENT_GRANULARITY_PAGE,
        R.string.switch_access_move_next_page);
    MAP_FORWARD_GRANULARITY_IDS.put(
        AccessibilityNodeInfoCompat.MOVEMENT_GRANULARITY_PARAGRAPH,
        R.string.switch_access_move_next_paragraph);
    MAP_FORWARD_GRANULARITY_IDS.put(
        AccessibilityNodeInfoCompat.MOVEMENT_GRANULARITY_WORD,
        R.string.switch_access_move_next_word);
    MAP_FORWARD_GRANULARITY_IDS.put(
        TextEditingUtils.ACTION_GRANULARITY_SENTENCE, R.string.switch_access_move_next_sentence);

    MAP_BACKWARD_GRANULARITY_IDS.put(
        AccessibilityNodeInfoCompat.MOVEMENT_GRANULARITY_CHARACTER,
        R.string.switch_access_move_prev_character);
    MAP_BACKWARD_GRANULARITY_IDS.put(
        AccessibilityNodeInfoCompat.MOVEMENT_GRANULARITY_LINE,
        R.string.switch_access_move_prev_line);
    MAP_BACKWARD_GRANULARITY_IDS.put(
        AccessibilityNodeInfoCompat.MOVEMENT_GRANULARITY_PAGE,
        R.string.switch_access_move_prev_page);
    MAP_BACKWARD_GRANULARITY_IDS.put(
        AccessibilityNodeInfoCompat.MOVEMENT_GRANULARITY_PARAGRAPH,
        R.string.switch_access_move_prev_paragraph);
    MAP_BACKWARD_GRANULARITY_IDS.put(
        AccessibilityNodeInfoCompat.MOVEMENT_GRANULARITY_WORD,
        R.string.switch_access_move_prev_word);
    MAP_BACKWARD_GRANULARITY_IDS.put(
        TextEditingUtils.ACTION_GRANULARITY_SENTENCE, R.string.switch_access_move_prev_sentence);
  }

  // String returned if we can't find the correct description. (Note: This should never happen.)
  private static final String UNKNOWN_STRING = "";

  // AccessibilityService is necessary for executing actions and getting strings from resources.
  // Resource ids don't work nicely as we sometimes need to append numbers to actions.
  private final AccessibilityService service;
  private SwitchAccessAction action;

  private final MenuItemOnClickListener onClickListener;

  /**
   * @param service AccessibilityService used to retrieve strings from resources and to execute
   *     actions
   * @param nodeCompat Node on which the provided {@link SwitchAccessAction} is to be performed
   * @param action Action to be performed on the provided {@link AccessibilityNodeInfoCompat}
   */
  public NodeActionMenuItem(
      AccessibilityService service,
      final AccessibilityNodeInfoCompat nodeCompat,
      final SwitchAccessAction action,
      final ActionTimeline actionTimeline,
      @Nullable final SelectMenuItemListener selectMenuItemListener) {

    onClickListener =
        new MenuItemOnClickListener() {
          @Override
          public void onClick() {
            action.execute(service);

            // Add action to the timeline if it's undoable.
            if (action.isUndoable()) {
              ActionTimeline timeline =
                  UndoRedoManager.getInstance(RecycleBehavior.DO_RECYCLE_NODES)
                      .getTimelineForNodeCompat(nodeCompat, actionTimeline);
              if (timeline != null) {
                timeline.newActionPerformed(action);
              }
            }

            CharSequence nodeClassName = nodeCompat.getClassName();
            if ((selectMenuItemListener != null)
                && (nodeClassName != null)
                && !MenuButton.class.getName().contentEquals(nodeClassName)) {
              // Do not call SelectMenuItemListener if the associated AccessibilityNodeInfoCompat
              // corresponds to a Switch Access menu item. This method will be called whenever a
              // SwitchAccessAction is performed. When a menu item, for example "Scroll Forward" is
              // selected, two SwitchAccessActions will be performed. The first action is
              // ACTION_MENU_CLICK, which corresponds to clicking the "Scroll Forward" menu item.
              // The second action is ACTION_MENU_SCROLL_FORWARD, which corresponds to scrolling the
              // item that triggers the Switch Access menu. We should only log the second action, as
              // this is the action that is triggered by clicking the menu item.
              selectMenuItemListener.onMenuItemSelected(getMenuItemForAction(action));
            }
          }
        };
    this.service = service;
    this.action = action;
  }

  /** Returns an icon resource id associated with the current action. */
  @Override
  public int getIconResource() {
    int actionId = action.getId();
    switch (actionId) {
      case AccessibilityNodeInfoCompat.ACTION_CLICK:
        return R.drawable.ic_select;
      case AccessibilityNodeInfoCompat.ACTION_LONG_CLICK:
        return R.drawable.ic_long_press;
      case AccessibilityNodeInfoCompat.ACTION_SCROLL_FORWARD:
        return R.drawable.ic_scroll_down;
      case AccessibilityNodeInfoCompat.ACTION_SCROLL_BACKWARD:
        return R.drawable.ic_scroll_up;
      case AccessibilityNodeInfoCompat.ACTION_DISMISS:
        return R.drawable.ic_dismiss;
      case AccessibilityNodeInfoCompat.ACTION_NEXT_AT_MOVEMENT_GRANULARITY:
      case AccessibilityNodeInfoCompat.ACTION_PREVIOUS_AT_MOVEMENT_GRANULARITY:
        return R.drawable.ic_text;
      case AccessibilityNodeInfoCompat.ACTION_COLLAPSE:
        return R.drawable.ic_collapse;
      case AccessibilityNodeInfoCompat.ACTION_EXPAND:
        return R.drawable.ic_expand;
      case AccessibilityNodeInfoCompat.ACTION_SET_SELECTION:
        return R.drawable.ic_highlight_text;
      case AccessibilityNodeInfoCompat.ACTION_CUT:
        return R.drawable.ic_cut;
      case AccessibilityNodeInfoCompat.ACTION_COPY:
        return R.drawable.ic_copy;
      case AccessibilityNodeInfoCompat.ACTION_PASTE:
        return R.drawable.ic_paste;
      case TextEditingUtils.ACTION_DELETE_TEXT:
        return R.drawable.ic_delete_text;
      case TextEditingUtils.ACTION_UNDO:
        return R.drawable.ic_undo;
      case TextEditingUtils.ACTION_REDO:
        return R.drawable.ic_redo;
      default:
        // We don't know what the action is. Don't show an icon.
        return 0;
    }
  }

  @Override
  public String getText() {
    String label = getTextInternal();
    int numberToAppendToDuplicateAction = action.getNumberToAppendToDuplicateAction();
    if (numberToAppendToDuplicateAction >= 0) {
      String formatString =
          service.getResources().getString(R.string.switch_access_dup_bounds_format);
      return String.format(formatString, label, numberToAppendToDuplicateAction);
    }
    return label;
  }

  @Override
  public MenuItemOnClickListener getOnClickListener() {
    return onClickListener;
  }

  private String getTextInternal() {
    CharSequence label = action.getLabel();
    if (label != null) {
      return label.toString();
    }

    SwitchAccessMenuItemEnum.MenuItem menuItem = getMenuItemForAction(action);
    switch (menuItem) {
      case ACTION_MENU_CLICK:
        return service.getString(R.string.action_name_click);
      case ACTION_MENU_LONG_CLICK:
        return service.getString(R.string.action_name_long_click);
      case ACTION_MENU_SCROLL_FORWARD:
        return service.getString(R.string.action_name_scroll_forward);
      case ACTION_MENU_SCROLL_BACKWARD:
        return service.getString(R.string.action_name_scroll_backward);
      case ACTION_MENU_DISMISS:
        return service.getString(R.string.action_name_dismiss);
      case ACTION_MENU_COLLAPSE:
        return service.getString(R.string.action_name_collapse);
      case ACTION_MENU_EXPAND:
        return service.getString(R.string.action_name_expand);
      case ACTION_MENU_HIGHLIGHT_ALL_TEXT:
        return service.getString(R.string.action_name_highlight_all_text);
      case ACTION_MENU_HIGHLIGHT_PREVIOUS_CHARACTER:
      case ACTION_MENU_HIGHLIGHT_PREVIOUS_LINE:
      case ACTION_MENU_HIGHLIGHT_PREVIOUS_PAGE:
      case ACTION_MENU_HIGHLIGHT_PREVIOUS_PARAGRAPH:
      case ACTION_MENU_HIGHLIGHT_PREVIOUS_WORD:
      case ACTION_MENU_HIGHLIGHT_PREVIOUS_SENTENCE:
        int selectionGranularity =
            action.getArgs().getInt(ACTION_ARGUMENT_MOVEMENT_GRANULARITY_INT);
        return service.getString(
            R.string.action_name_highlight_granular_text,
            service
                .getString(MAP_BACKWARD_GRANULARITY_IDS.get(selectionGranularity))
                .toLowerCase());
      case ACTION_MENU_HIGHLIGHT_TEXT:
        return service.getString(R.string.action_name_highlight_text);
      case ACTION_MENU_CUT_ALL_TEXT:
        return service.getString(R.string.action_name_cut_all_text);
      case ACTION_MENU_CUT:
        return service.getString(R.string.action_name_cut);
      case ACTION_MENU_COPY_ALL_TEXT:
        return service.getString(R.string.action_name_copy_all_text);
      case ACTION_MENU_COPY:
        return service.getString(R.string.action_name_copy);
      case ACTION_MENU_PASTE:
        return service.getString(R.string.action_name_paste);
      case ACTION_MENU_DELETE_PREVIOUS_CHARACTER:
      case ACTION_MENU_DELETE_PREVIOUS_LINE:
      case ACTION_MENU_DELETE_PREVIOUS_PAGE:
      case ACTION_MENU_DELETE_PREVIOUS_PARAGRAPH:
      case ACTION_MENU_DELETE_PREVIOUS_WORD:
      case ACTION_MENU_DELETE_PREVIOUS_SENTENCE:
        int deleteGranularity = action.getArgs().getInt(ACTION_ARGUMENT_MOVEMENT_GRANULARITY_INT);
        return service.getString(
            R.string.action_name_delete,
            service.getString(MAP_BACKWARD_GRANULARITY_IDS.get(deleteGranularity)).toLowerCase());
      case ACTION_MENU_DELETE_HIGHLIGHTED_TEXT:
        return service.getString(R.string.action_name_delete_highlighted_text);
      case ACTION_MENU_MOVE_TO_NEXT_CHARACTER:
      case ACTION_MENU_MOVE_TO_NEXT_LINE:
      case ACTION_MENU_MOVE_TO_NEXT_PAGE:
      case ACTION_MENU_MOVE_TO_NEXT_PARAGRAPH:
      case ACTION_MENU_MOVE_TO_NEXT_WORD:
      case ACTION_MENU_MOVE_TO_NEXT_SENTENCE:
        int nextGranularity = action.getArgs().getInt(ACTION_ARGUMENT_MOVEMENT_GRANULARITY_INT);
        return service.getString(MAP_FORWARD_GRANULARITY_IDS.get(nextGranularity));
      case ACTION_MENU_MOVE_TO_PREVIOUS_CHARACTER:
      case ACTION_MENU_MOVE_TO_PREVIOUS_LINE:
      case ACTION_MENU_MOVE_TO_PREVIOUS_PAGE:
      case ACTION_MENU_MOVE_TO_PREVIOUS_PARAGRAPH:
      case ACTION_MENU_MOVE_TO_PREVIOUS_WORD:
      case ACTION_MENU_MOVE_TO_PREVIOUS_SENTENCE:
        int previousGranularity = action.getArgs().getInt(ACTION_ARGUMENT_MOVEMENT_GRANULARITY_INT);
        return service.getString(MAP_BACKWARD_GRANULARITY_IDS.get(previousGranularity));
      case ACTION_MENU_UNDO:
        return service.getString(R.string.action_name_undo);
      case ACTION_MENU_REDO:
        return service.getString(R.string.action_name_redo);
      default:
        return UNKNOWN_STRING;
    }
  }

  private static SwitchAccessMenuItemEnum.MenuItem getMenuItemForAction(SwitchAccessAction action) {
    switch (action.getId()) {
      case AccessibilityNodeInfoCompat.ACTION_CLICK:
        return SwitchAccessMenuItemEnum.MenuItem.ACTION_MENU_CLICK;
      case AccessibilityNodeInfoCompat.ACTION_LONG_CLICK:
        return SwitchAccessMenuItemEnum.MenuItem.ACTION_MENU_LONG_CLICK;
      case AccessibilityNodeInfoCompat.ACTION_SCROLL_FORWARD:
        return SwitchAccessMenuItemEnum.MenuItem.ACTION_MENU_SCROLL_FORWARD;
      case AccessibilityNodeInfoCompat.ACTION_SCROLL_BACKWARD:
        return SwitchAccessMenuItemEnum.MenuItem.ACTION_MENU_SCROLL_BACKWARD;
      case AccessibilityNodeInfoCompat.ACTION_DISMISS:
        return SwitchAccessMenuItemEnum.MenuItem.ACTION_MENU_DISMISS;
      case AccessibilityNodeInfoCompat.ACTION_COLLAPSE:
        return SwitchAccessMenuItemEnum.MenuItem.ACTION_MENU_COLLAPSE;
      case AccessibilityNodeInfoCompat.ACTION_EXPAND:
        return SwitchAccessMenuItemEnum.MenuItem.ACTION_MENU_EXPAND;
      case AccessibilityNodeInfoCompat.ACTION_SET_SELECTION:
        return getMenuItemForSetSelectionAction(action);
      case AccessibilityNodeInfoCompat.ACTION_CUT:
        return getMenuItemForCutAction(action);
      case AccessibilityNodeInfoCompat.ACTION_COPY:
        return getMenuItemForCopyAction(action);
      case AccessibilityNodeInfoCompat.ACTION_PASTE:
        return SwitchAccessMenuItemEnum.MenuItem.ACTION_MENU_PASTE;
      case TextEditingUtils.ACTION_DELETE_TEXT:
        return getMenuItemForDeleteTextAction(action);
      case AccessibilityNodeInfoCompat.ACTION_NEXT_AT_MOVEMENT_GRANULARITY:
        return getMenuItemForNextAtMovementGranularityAction(action);
      case AccessibilityNodeInfoCompat.ACTION_PREVIOUS_AT_MOVEMENT_GRANULARITY:
        return getMenuItemForPreviousAtMovementGranularityAction(action);
      case TextEditingUtils.ACTION_UNDO:
        return SwitchAccessMenuItemEnum.MenuItem.ACTION_MENU_UNDO;
      case TextEditingUtils.ACTION_REDO:
        return SwitchAccessMenuItemEnum.MenuItem.ACTION_MENU_REDO;
      default:
        // Other actions such as "App info", "Add to home screen" etc.
        return SwitchAccessMenuItemEnum.MenuItem.ACTION_MENU_CUSTOM_ACTION;
    }
  }

  private static SwitchAccessMenuItemEnum.MenuItem getMenuItemForSetSelectionAction(
      SwitchAccessAction action) {
    // Selecting only a portion of the text requires a granularity argument.
    int selectionGranularity = action.getArgs().getInt(ACTION_ARGUMENT_MOVEMENT_GRANULARITY_INT);
    if (selectionGranularity == TextEditingUtils.ACTION_GRANULARITY_ALL) {
      // Highlighting all text does not follow the same format as other granularities, so it
      // has its own string ID.
      return SwitchAccessMenuItemEnum.MenuItem.ACTION_MENU_HIGHLIGHT_ALL_TEXT;
    } else {
      Integer stringId = MAP_BACKWARD_GRANULARITY_IDS.get(selectionGranularity);
      // This highlight action is based on existing ACTION_PREVIOUS_AT_MOVEMENT_GRANULARITY
      // granularities, and so the string is formatted to use granular previous movement
      // strings as arguments.
      if (stringId == R.string.switch_access_move_prev_character) {
        return SwitchAccessMenuItemEnum.MenuItem.ACTION_MENU_HIGHLIGHT_PREVIOUS_CHARACTER;
      } else if (stringId == R.string.switch_access_move_prev_line) {
        return SwitchAccessMenuItemEnum.MenuItem.ACTION_MENU_HIGHLIGHT_PREVIOUS_LINE;
      } else if (stringId == R.string.switch_access_move_prev_page) {
        return SwitchAccessMenuItemEnum.MenuItem.ACTION_MENU_HIGHLIGHT_PREVIOUS_PAGE;
      } else if (stringId == R.string.switch_access_move_prev_paragraph) {
        return SwitchAccessMenuItemEnum.MenuItem.ACTION_MENU_HIGHLIGHT_PREVIOUS_PARAGRAPH;
      } else if (stringId == R.string.switch_access_move_prev_word) {
        return SwitchAccessMenuItemEnum.MenuItem.ACTION_MENU_HIGHLIGHT_PREVIOUS_WORD;
      } else if (stringId == R.string.switch_access_move_prev_sentence) {
        return SwitchAccessMenuItemEnum.MenuItem.ACTION_MENU_HIGHLIGHT_PREVIOUS_SENTENCE;
      } else {
        // This should never happen.
        return SwitchAccessMenuItemEnum.MenuItem.ACTION_MENU_UNKNOWN_STRING;
      }
    }
  }

  private static SwitchAccessMenuItemEnum.MenuItem getMenuItemForCutAction(
      SwitchAccessAction action) {
    if (action.getArgs().getInt(ACTION_ARGUMENT_MOVEMENT_GRANULARITY_INT)
        == TextEditingUtils.ACTION_GRANULARITY_ALL) {
      return SwitchAccessMenuItemEnum.MenuItem.ACTION_MENU_CUT_ALL_TEXT;
    } else {
      return SwitchAccessMenuItemEnum.MenuItem.ACTION_MENU_CUT;
    }
  }

  private static SwitchAccessMenuItemEnum.MenuItem getMenuItemForCopyAction(
      SwitchAccessAction action) {
    if (action.getArgs().getInt(ACTION_ARGUMENT_MOVEMENT_GRANULARITY_INT)
        == TextEditingUtils.ACTION_GRANULARITY_ALL) {
      return SwitchAccessMenuItemEnum.MenuItem.ACTION_MENU_COPY_ALL_TEXT;
    } else {
      return SwitchAccessMenuItemEnum.MenuItem.ACTION_MENU_COPY;
    }
  }

  private static SwitchAccessMenuItemEnum.MenuItem getMenuItemForDeleteTextAction(
      SwitchAccessAction action) {
    // Setting only a portion of the text requires a granularity argument.
    int deleteGranularity = action.getArgs().getInt(ACTION_ARGUMENT_MOVEMENT_GRANULARITY_INT);
    if (deleteGranularity == TextEditingUtils.ACTION_GRANULARITY_HIGHLIGHT) {
      return SwitchAccessMenuItemEnum.MenuItem.ACTION_MENU_DELETE_HIGHLIGHTED_TEXT;
    }

    Integer stringId = MAP_BACKWARD_GRANULARITY_IDS.get(deleteGranularity);

    if (stringId == R.string.switch_access_move_prev_character) {
      return SwitchAccessMenuItemEnum.MenuItem.ACTION_MENU_DELETE_PREVIOUS_CHARACTER;
    } else if (stringId == R.string.switch_access_move_prev_line) {
      return SwitchAccessMenuItemEnum.MenuItem.ACTION_MENU_DELETE_PREVIOUS_LINE;
    } else if (stringId == R.string.switch_access_move_prev_page) {
      return SwitchAccessMenuItemEnum.MenuItem.ACTION_MENU_DELETE_PREVIOUS_PAGE;
    } else if (stringId == R.string.switch_access_move_prev_paragraph) {
      return SwitchAccessMenuItemEnum.MenuItem.ACTION_MENU_DELETE_PREVIOUS_PARAGRAPH;
    } else if (stringId == R.string.switch_access_move_prev_word) {
      return SwitchAccessMenuItemEnum.MenuItem.ACTION_MENU_DELETE_PREVIOUS_WORD;
    } else if (stringId == R.string.switch_access_move_prev_sentence) {
      return SwitchAccessMenuItemEnum.MenuItem.ACTION_MENU_DELETE_PREVIOUS_SENTENCE;
    } else {
      // This should never happen.
      return SwitchAccessMenuItemEnum.MenuItem.ACTION_MENU_UNKNOWN_STRING;
    }
  }

  private static SwitchAccessMenuItemEnum.MenuItem getMenuItemForNextAtMovementGranularityAction(
      SwitchAccessAction action) {
    // Movement requires a granularity argument.
    int nextGranularity = action.getArgs().getInt(ACTION_ARGUMENT_MOVEMENT_GRANULARITY_INT);
    Integer nextStringId = MAP_FORWARD_GRANULARITY_IDS.get(nextGranularity);

    if (nextStringId == R.string.switch_access_move_next_character) {
      return SwitchAccessMenuItemEnum.MenuItem.ACTION_MENU_MOVE_TO_NEXT_CHARACTER;
    } else if (nextStringId == R.string.switch_access_move_next_line) {
      return SwitchAccessMenuItemEnum.MenuItem.ACTION_MENU_MOVE_TO_NEXT_LINE;
    } else if (nextStringId == R.string.switch_access_move_next_page) {
      return SwitchAccessMenuItemEnum.MenuItem.ACTION_MENU_MOVE_TO_NEXT_PAGE;
    } else if (nextStringId == R.string.switch_access_move_next_paragraph) {
      return SwitchAccessMenuItemEnum.MenuItem.ACTION_MENU_MOVE_TO_NEXT_PARAGRAPH;
    } else if (nextStringId == R.string.switch_access_move_next_word) {
      return SwitchAccessMenuItemEnum.MenuItem.ACTION_MENU_MOVE_TO_NEXT_WORD;
    } else if (nextStringId == R.string.switch_access_move_next_sentence) {
      return SwitchAccessMenuItemEnum.MenuItem.ACTION_MENU_MOVE_TO_NEXT_SENTENCE;
    } else {
      // This should never happen.
      return SwitchAccessMenuItemEnum.MenuItem.ACTION_MENU_UNKNOWN_STRING;
    }
  }

  private static SwitchAccessMenuItemEnum.MenuItem
      getMenuItemForPreviousAtMovementGranularityAction(SwitchAccessAction action) {
    // Movement requires a granularity argument.
    int previousGranularity = action.getArgs().getInt(ACTION_ARGUMENT_MOVEMENT_GRANULARITY_INT);
    Integer previousStringId = MAP_BACKWARD_GRANULARITY_IDS.get(previousGranularity);

    if (previousStringId == R.string.switch_access_move_prev_character) {
      return SwitchAccessMenuItemEnum.MenuItem.ACTION_MENU_MOVE_TO_PREVIOUS_CHARACTER;
    } else if (previousStringId == R.string.switch_access_move_prev_line) {
      return SwitchAccessMenuItemEnum.MenuItem.ACTION_MENU_MOVE_TO_PREVIOUS_LINE;
    } else if (previousStringId == R.string.switch_access_move_prev_page) {
      return SwitchAccessMenuItemEnum.MenuItem.ACTION_MENU_MOVE_TO_PREVIOUS_PAGE;
    } else if (previousStringId == R.string.switch_access_move_prev_paragraph) {
      return SwitchAccessMenuItemEnum.MenuItem.ACTION_MENU_MOVE_TO_PREVIOUS_PARAGRAPH;
    } else if (previousStringId == R.string.switch_access_move_prev_word) {
      return SwitchAccessMenuItemEnum.MenuItem.ACTION_MENU_MOVE_TO_PREVIOUS_WORD;
    } else if (previousStringId == R.string.switch_access_move_prev_sentence) {
      return SwitchAccessMenuItemEnum.MenuItem.ACTION_MENU_MOVE_TO_PREVIOUS_SENTENCE;
    } else {
      // This should never happen.
      return SwitchAccessMenuItemEnum.MenuItem.ACTION_MENU_UNKNOWN_STRING;
    }
  }
}
