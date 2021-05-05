/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.google.android.accessibility.utils.parsetree;

import androidx.annotation.Nullable;
import java.util.List;

class ParseTreeIfNode extends ParseTreeNode {
  private final ParseTreeNode mCondition;
  private final ParseTreeNode mOnTrue;
  private final ParseTreeNode mOnFalse;
  private final ParseTreeNode mTypeDelegate;

  ParseTreeIfNode(
      ParseTreeNode condition, @Nullable ParseTreeNode onTrue, @Nullable ParseTreeNode onFalse) {
    if (onTrue != null) {
      mTypeDelegate = onTrue;
    } else if (onFalse != null) {
      mTypeDelegate = onFalse;
    } else {
      throw new IllegalStateException("\"if\" requires at least one output condition");
    }
    mCondition = condition;
    mOnTrue = new ParseTreeCommentNode(onTrue, "if (true)", false);
    mOnFalse = new ParseTreeCommentNode(onFalse, "if (false)", false);
  }

  @Override
  public int getType() {
    return mTypeDelegate.getType();
  }

  @Override
  public int getEnumType() {
    return mTypeDelegate.getEnumType();
  }

  @Override
  public boolean canCoerceTo(@ParseTree.VariableType int type) {
    return mTypeDelegate.canCoerceTo(type);
  }

  @Override
  public boolean resolveToBoolean(ParseTree.VariableDelegate delegate, String logIndent) {
    if (mCondition.resolveToBoolean(delegate, logIndent)) {
      return mOnTrue.resolveToBoolean(delegate, logIndent);
    } else {
      return mOnFalse.resolveToBoolean(delegate, logIndent);
    }
  }

  @Override
  public int resolveToInteger(ParseTree.VariableDelegate delegate, String logIndent) {
    if (mCondition.resolveToBoolean(delegate, logIndent)) {
      return mOnTrue.resolveToInteger(delegate, logIndent);
    } else {
      return mOnFalse.resolveToInteger(delegate, logIndent);
    }
  }

  @Override
  public double resolveToNumber(ParseTree.VariableDelegate delegate, String logIndent) {
    if (mCondition.resolveToBoolean(delegate, logIndent)) {
      return mOnTrue.resolveToNumber(delegate, logIndent);
    } else {
      return mOnFalse.resolveToNumber(delegate, logIndent);
    }
  }

  @Override
  public CharSequence resolveToString(ParseTree.VariableDelegate delegate, String logIndent) {
    if (mCondition.resolveToBoolean(delegate, logIndent)) {
      return mOnTrue.resolveToString(delegate, logIndent);
    } else {
      return mOnFalse.resolveToString(delegate, logIndent);
    }
  }

  @Override
  public @Nullable ParseTree.VariableDelegate resolveToReference(
      ParseTree.VariableDelegate delegate, String logIndent) {
    if (mCondition.resolveToBoolean(delegate, logIndent)) {
      return mOnTrue.resolveToReference(delegate, logIndent);
    } else {
      return mOnFalse.resolveToReference(delegate, logIndent);
    }
  }

  @Override
  public List<CharSequence> resolveToArray(ParseTree.VariableDelegate delegate, String logIndent) {
    if (mCondition.resolveToBoolean(delegate, logIndent)) {
      return mOnTrue.resolveToArray(delegate, logIndent);
    } else {
      return mOnFalse.resolveToArray(delegate, logIndent);
    }
  }

  @Override
  public List<ParseTree.VariableDelegate> resolveToChildArray(
      ParseTree.VariableDelegate delegate, String logIndent) {
    if (mCondition.resolveToBoolean(delegate, logIndent)) {
      return mOnTrue.resolveToChildArray(delegate, logIndent);
    } else {
      return mOnFalse.resolveToChildArray(delegate, logIndent);
    }
  }
}
