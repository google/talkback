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

import androidx.core.view.accessibility.AccessibilityNodeInfoCompat.AccessibilityActionCompat;
import com.google.android.libraries.accessibility.utils.undo.TimelineAction;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Holds all action data that Switch Access needs to let the user choose which action they wish to
 * perform.
 */
public abstract class SwitchAccessActionBase extends TimelineAction {
  private final int id;
  @Nullable private final CharSequence label;
  private int numberToAppendToDuplicateAction = -1;

  /**
   * @param id The id used to identify this action. Should correspond to a valid {@link
   *     AccessibilityActionCompat} id.
   * @param label The human readable label used to identify this action
   */
  public SwitchAccessActionBase(int id, @Nullable CharSequence label) {
    this.id = id;
    this.label = label;
  }

  /**
   * Returns the id used to identify this action. Corresponds to an {@link
   * AccessibilityActionCompat}'s id.
   */
  public int getId() {
    return id;
  }

  /** Returns the human readable label of this action if one was assigned. */
  @Nullable
  public CharSequence getLabel() {
    return label;
  }

  /**
   * Set a number to append to the action's description. Negative values indicate that no value will
   * be appended. The default number is -1.
   *
   * @param numberToAppendToDuplicateAction If 0 or greater, number to be appended to action's
   *     descriptions.
   */
  public void setNumberToAppendToDuplicateAction(int numberToAppendToDuplicateAction) {
    this.numberToAppendToDuplicateAction = numberToAppendToDuplicateAction;
  }

  /**
   * Returns the number to append to the action's description. Negative numbers should be ignored.
   */
  public int getNumberToAppendToDuplicateAction() {
    return numberToAppendToDuplicateAction;
  }
}
