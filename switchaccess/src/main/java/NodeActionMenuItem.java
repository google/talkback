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

package com.google.android.accessibility.switchaccess;

import android.content.Context;
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import android.util.SparseArray;
import android.view.View;
import com.google.android.accessibility.utils.PerformActionUtils;

/**
 * Holds data required to create a menu item that corresponds to a specific action on a specific
 * {@link AccessibilityNodeInfoCompat}.
 */
public class NodeActionMenuItem extends MenuItem {

  // Map of granularities and the human readable labels associtated with them. Used when
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
  }

  // String returned if we can't find the correct description. (Note: This should never happen.)
  private static final String UNKNOWN_STRING = "";

  // Context is necessary to get strings from resources. Resource ids don't work nicely as we
  // sometimes need to append numbers to actions.
  protected Context mContext;
  protected AccessibilityNodeInfoCompat mNodeCompat;
  protected SwitchAccessActionCompat mAction;

  /**
   * @param context Context used to retrieve strings from resources
   * @param nodeCompat Node on which the provided {@link SwitchAccessActionCompat} is to be
   *     performed
   * @param action Action to be performed on the provided {@link AccessibilityNodeInfoCompat}
   */
  public NodeActionMenuItem(
      Context context,
      final AccessibilityNodeInfoCompat nodeCompat,
      final SwitchAccessActionCompat action) {
    super(
        -1,
        null,
        new View.OnClickListener() {
          @Override
          public void onClick(View view) {
            PerformActionUtils.performAction(
                nodeCompat, action.getId(), action.getArgs(), null /* EventId */);
          }
        });

    mContext = context;
    mNodeCompat = nodeCompat;
    mAction = action;
  }

  /** Returns an icon resource id associated with the current action. */
  @Override
  public int getIconResource() {
    int actionId = mAction.getId();
    switch (actionId) {
      case AccessibilityNodeInfoCompat.ACTION_CLICK:
        return R.drawable.ic_select;
      case AccessibilityNodeInfoCompat.ACTION_LONG_CLICK:
        return R.drawable.ic_long_press;
      case AccessibilityNodeInfoCompat.ACTION_SCROLL_FORWARD:
        return R.drawable.quantum_ic_keyboard_arrow_down_white_24;
      case AccessibilityNodeInfoCompat.ACTION_SCROLL_BACKWARD:
        return R.drawable.quantum_ic_keyboard_arrow_up_white_24;
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
      default:
        // We don't know what the action is. Don't show an icon.
        return 0;
    }
  }

  @Override
  public CharSequence getText() {
    CharSequence label = getTextInternal();
    int numberToAppendToDuplicateAction = mAction.getNumberToAppendToDuplicateAction();
    if (numberToAppendToDuplicateAction >= 0) {
      String formatString =
          mContext.getResources().getString(R.string.switch_access_dup_bounds_format);
      return String.format(formatString, label.toString(), numberToAppendToDuplicateAction);
    }
    return label;
  }

  protected CharSequence getTextInternal() {
    CharSequence label = mAction.getLabel();
    if (label != null) {
      return label;
    }

    int actionId = mAction.getId();
    switch (actionId) {
      case AccessibilityNodeInfoCompat.ACTION_CLICK:
        return mContext.getString(R.string.action_name_click);
      case AccessibilityNodeInfoCompat.ACTION_LONG_CLICK:
        return mContext.getString(R.string.action_name_long_click);
      case AccessibilityNodeInfoCompat.ACTION_SCROLL_FORWARD:
        return mContext.getString(R.string.action_name_scroll_forward);
      case AccessibilityNodeInfoCompat.ACTION_SCROLL_BACKWARD:
        return mContext.getString(R.string.action_name_scroll_backward);
      case AccessibilityNodeInfoCompat.ACTION_DISMISS:
        return mContext.getString(R.string.action_name_dismiss);
      case AccessibilityNodeInfoCompat.ACTION_COLLAPSE:
        return mContext.getString(R.string.action_name_collapse);
      case AccessibilityNodeInfoCompat.ACTION_EXPAND:
        return mContext.getString(R.string.action_name_expand);
      case AccessibilityNodeInfoCompat.ACTION_SET_SELECTION:
        return mContext.getString(R.string.action_name_highlight_text);
      case AccessibilityNodeInfoCompat.ACTION_CUT:
        return mContext.getString(R.string.action_name_cut);
      case AccessibilityNodeInfoCompat.ACTION_COPY:
        return mContext.getString(R.string.action_name_copy);
      case AccessibilityNodeInfoCompat.ACTION_PASTE:
        return mContext.getString(R.string.action_name_paste);
      case AccessibilityNodeInfoCompat.ACTION_NEXT_AT_MOVEMENT_GRANULARITY:
        {
          // Movement requires a granularity argument.
          int granularity =
              mAction
                  .getArgs()
                  .getInt(AccessibilityNodeInfoCompat.ACTION_ARGUMENT_MOVEMENT_GRANULARITY_INT);
          Integer stringId = MAP_FORWARD_GRANULARITY_IDS.get(granularity);
          return (stringId == null) ? UNKNOWN_STRING : mContext.getString(stringId);
        }
      case AccessibilityNodeInfoCompat.ACTION_PREVIOUS_AT_MOVEMENT_GRANULARITY:
        {
          // Movement requires a granularity argument.
          int granularity =
              mAction
                  .getArgs()
                  .getInt(AccessibilityNodeInfoCompat.ACTION_ARGUMENT_MOVEMENT_GRANULARITY_INT);
          Integer stringId = MAP_BACKWARD_GRANULARITY_IDS.get(granularity);
          return (stringId == null) ? UNKNOWN_STRING : mContext.getString(stringId);
        }
      default:
        // This should never happen.
        return UNKNOWN_STRING;
    }
  }
}
