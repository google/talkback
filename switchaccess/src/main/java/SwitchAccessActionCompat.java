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

import android.os.Bundle;
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat.AccessibilityActionCompat;

/**
 * Holds all action data that Switch Access needs to identify an action. This data is used to query
 * which action the user wishes to perform if an {@link ShowActionsMenuNode} supports multiple
 * actions and to perform an action on the corresponding {@link SwitchAccessNodeCompat}.
 */
public class SwitchAccessActionCompat {

  private final int mId;
  private final CharSequence mLabel;
  private int mNumberToAppendToDuplicateAction = -1;
  private final Bundle mArgs;

  /**
   * @param id The id used to identify this action. Should correspond to a valid {@link
   *     AccessibilityActionCompat} id.
   * @param label The human readable label used to identify this action
   */
  public SwitchAccessActionCompat(int id, CharSequence label) {
    this(id, label, null);
  }

  /**
   * @param id The id used to identify this action. Should correspond to a valid {@link
   *     AccessibilityActionCompat} id.
   * @param label The human readable label used to identify this action
   * @param args Additional arguments to use when performing this action
   */
  public SwitchAccessActionCompat(int id, CharSequence label, Bundle args) {
    mId = id;
    mLabel = label;
    mArgs = args;
  }

  /**
   * @param actionCompat The {@link AccessibilityActionCompat} whose id and label to use to identify
   *     this action
   */
  public SwitchAccessActionCompat(AccessibilityActionCompat actionCompat) {
    this(actionCompat, null);
  }

  /**
   * @param actionCompat The {@link AccessibilityActionCompat} whose id and label to use to identify
   *     this action
   * @param args Additional arguments to use when performing this action
   */
  public SwitchAccessActionCompat(AccessibilityActionCompat actionCompat, Bundle args) {
    this(actionCompat.getId(), actionCompat.getLabel(), args);
  }

  /**
   * Returns the id used to identify thos action. Corresponds to an {@link
   * AccessibilityActionCompat}'s id.
   */
  public int getId() {
    return mId;
  }

  /** Returns the human readable label of this action if one was assigned. */
  public CharSequence getLabel() {
    return mLabel;
  }

  /**
   * Set a number to append to the action's description. Negative values indicate that no value will
   * be appended. The default number is -1.
   *
   * @param numberToAppendToDuplicateAction If 0 or greater, number to be appended to action's
   *     descriptions.
   */
  public void setNumberToAppendToDuplicateAction(int numberToAppendToDuplicateAction) {
    mNumberToAppendToDuplicateAction = numberToAppendToDuplicateAction;
  }

  /**
   * Returns the number to append to the action's description. Negative numbers should be ignored.
   */
  public int getNumberToAppendToDuplicateAction() {
    return mNumberToAppendToDuplicateAction;
  }

  /** Returns any additional arguments to be used when performing this action. */
  public Bundle getArgs() {
    return mArgs;
  }
}
