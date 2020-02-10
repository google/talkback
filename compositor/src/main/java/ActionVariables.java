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

package com.google.android.accessibility.compositor;

import android.content.Context;
import androidx.annotation.Nullable;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat.AccessibilityActionCompat;
import com.google.android.accessibility.utils.AccessibilityNodeInfoUtils;
import com.google.android.accessibility.utils.SpeechCleanupUtils;
import com.google.android.accessibility.utils.parsetree.ParseTree;
import java.util.HashMap;

/** A VariableDelegate that maps AccessbilityAction from AccessibilityNodeInfoCompat */
class ActionVariables implements ParseTree.VariableDelegate {
  // IDs of variables.
  private static final int ACTION_ID = 9000;
  private static final int ACTION_LABEL = 9001;
  private static final int ACTION_IS_CUSTOM_ACTION = 9002;
  private static final int ACTION_IS_CLICK = 9003;
  private static final int ACTION_IS_LONG_CLICK = 9004;

  // IDs of enums.
  private static final int ENUM_ACTION_ID = 9100;

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
  public void cleanup() {
    // Nothing to recycle.
  }

  @Override
  public boolean getBoolean(int variableId) {
    switch (variableId) {
      case ACTION_IS_CUSTOM_ACTION:
        return AccessibilityNodeInfoUtils.isCustomAction(mAction);
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
  public @Nullable CharSequence getString(int variableId) {
    return SpeechCleanupUtils.collapseRepeatedCharactersAndCleanUp(
        mContext, getStringInternal(variableId));
  }

  private @Nullable CharSequence getStringInternal(int variableId) {
    switch (variableId) {
      case ACTION_LABEL:
        return mAction.getLabel() == null ? "" : mAction.getLabel();
      default:
        return mParentVariables.getString(variableId);
    }
  }

  @Override
  public int getEnum(int variableId) {
    switch (variableId) {
      case ACTION_ID:
        return mAction.getId();
      default:
        return mParentVariables.getEnum(variableId);
    }
  }

  @Override
  public @Nullable ParseTree.VariableDelegate getReference(int variableId) {
    return mParentVariables.getReference(variableId);
  }

  @Override
  public int getArrayLength(int variableId) {
    return mParentVariables.getArrayLength(variableId);
  }

  @Override
  public @Nullable CharSequence getArrayStringElement(int variableId, int index) {
    return mParentVariables.getArrayStringElement(variableId, index);
  }

  @Override
  public @Nullable ParseTree.VariableDelegate getArrayChildElement(int variableId, int index) {
    return mParentVariables.getArrayChildElement(variableId, index);
  }

  static void declareVariables(ParseTree parseTree) {
    // Enumerations
    // For action IDs, only the values in AccessibilityNodeInfo/Compat are valid at test time, not
    // the values in AccessibilityAction/Compat.
    HashMap<Integer, String> actions = new HashMap<>();
    actions.put(AccessibilityNodeInfoCompat.ACTION_DISMISS, "dismiss");
    actions.put(AccessibilityNodeInfoCompat.ACTION_EXPAND, "expand");
    actions.put(AccessibilityNodeInfoCompat.ACTION_COLLAPSE, "collapse");
    parseTree.addEnum(ENUM_ACTION_ID, actions);

    // Variables.
    parseTree.addEnumVariable("action.id", ACTION_ID, ENUM_ACTION_ID);
    parseTree.addStringVariable("action.label", ACTION_LABEL);
    parseTree.addBooleanVariable("action.isCustomAction", ACTION_IS_CUSTOM_ACTION);
    parseTree.addBooleanVariable("action.isClick", ACTION_IS_CLICK);
    parseTree.addBooleanVariable("action.isLongClick", ACTION_IS_LONG_CLICK);
  }
}
