/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.google.android.accessibility.talkback.compositor;

import android.content.Context;
import androidx.annotation.Nullable;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat.AccessibilityActionCompat;
import com.google.android.accessibility.talkback.compositor.parsetree.ParseTree;
import com.google.android.accessibility.utils.output.SpeechCleanupUtils;

/** A VariableDelegate that maps AccessbilityAction from AccessibilityNodeInfoCompat */
class ActionVariables implements ParseTree.VariableDelegate {
  // IDs of variables.
  private static final int ACTION_LABEL = 9000;
  private static final int ACTION_IS_CLICK = 9001;
  private static final int ACTION_IS_LONG_CLICK = 9002;

  private final Context mContext;
  private final ParseTree.VariableDelegate mParentVariables;
  private final AccessibilityActionCompat mAction;

  /**
   * Constructs a ActionVariables, which contains context variables to help generate feedback for an
   * accessibility action.
   *
   * @param action The accessibility action for which we are generating feedback.
   */
  public ActionVariables(
      Context context, ParseTree.VariableDelegate parent, AccessibilityActionCompat action) {
    if (action == null) {
      throw new IllegalArgumentException("action cannot be null");
    }
    mContext = context;
    mParentVariables = parent;
    mAction = action;
  }

  @Override
  public boolean getBoolean(int variableId) {
    switch (variableId) {
      case ACTION_IS_CLICK:
        return mAction.getId() == AccessibilityNodeInfoCompat.ACTION_CLICK;
      case ACTION_IS_LONG_CLICK:
        return mAction.getId() == AccessibilityNodeInfoCompat.ACTION_LONG_CLICK;
      default:
        return mParentVariables.getBoolean(variableId);
    }
  }

  @Override
  public int getInteger(int variableId) {
    return mParentVariables.getInteger(variableId);
  }

  @Override
  public double getNumber(int variableId) {
    return mParentVariables.getNumber(variableId);
  }

  @Override
  @Nullable
  public CharSequence getString(int variableId) {
    return SpeechCleanupUtils.collapseRepeatedCharactersAndCleanUp(
        mContext, getStringInternal(variableId));
  }

  @Nullable
  private CharSequence getStringInternal(int variableId) {
    switch (variableId) {
      case ACTION_LABEL:
        return mAction.getLabel() == null ? "" : mAction.getLabel();
      default:
        return mParentVariables.getString(variableId);
    }
  }

  @Override
  public int getEnum(int variableId) {
    return mParentVariables.getEnum(variableId);
  }

  @Override
  @Nullable
  public ParseTree.VariableDelegate getReference(int variableId) {
    return mParentVariables.getReference(variableId);
  }

  @Override
  public int getArrayLength(int variableId) {
    return mParentVariables.getArrayLength(variableId);
  }

  @Override
  @Nullable
  public CharSequence getArrayStringElement(int variableId, int index) {
    return mParentVariables.getArrayStringElement(variableId, index);
  }

  @Override
  @Nullable
  public ParseTree.VariableDelegate getArrayChildElement(int variableId, int index) {
    return mParentVariables.getArrayChildElement(variableId, index);
  }

  static void declareVariables(ParseTree parseTree) {

    // Variables.
    parseTree.addStringVariable("action.label", ACTION_LABEL);
    parseTree.addBooleanVariable("action.isClick", ACTION_IS_CLICK);
    parseTree.addBooleanVariable("action.isLongClick", ACTION_IS_LONG_CLICK);
  }
}
