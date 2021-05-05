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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class ParseTreeSwitchNode extends ParseTreeNode {
  private final ParseTreeNode mCondition;
  private final Map<Integer, ParseTreeNode> mCases = new HashMap<>();
  private final ParseTreeNode mDefault;

  ParseTreeSwitchNode(
      ParseTreeNode condition,
      Map<Integer, ParseTreeNode> cases,
      @Nullable ParseTreeNode defaultCase) {
    if (cases.isEmpty()) {
      throw new IllegalStateException("'switch' requires at least one output condition");
    }
    mCondition = condition;
    mCases.putAll(cases);
    mDefault =
        new ParseTreeCommentNode(defaultCase, "switch: Falling back to default value", false);
  }

  @Override
  public int getType() {
    return mCases.entrySet().iterator().next().getValue().getType();
  }

  @Override
  public int getEnumType() {
    return mCases.entrySet().iterator().next().getValue().getEnumType();
  }

  @Override
  public boolean resolveToBoolean(ParseTree.VariableDelegate delegate, String logIndent) {
    int value = mCondition.resolveToInteger(delegate, logIndent);
    ParseTreeNode node = mCases.get(value);
    if (node != null) {
      return node.resolveToBoolean(delegate, logIndent);
    } else {
      return mDefault.resolveToBoolean(delegate, logIndent);
    }
  }

  @Override
  public int resolveToInteger(ParseTree.VariableDelegate delegate, String logIndent) {
    int value = mCondition.resolveToInteger(delegate, logIndent);
    ParseTreeNode node = mCases.get(value);
    if (node != null) {
      return node.resolveToInteger(delegate, logIndent);
    } else {
      return mDefault.resolveToInteger(delegate, logIndent);
    }
  }

  @Override
  public double resolveToNumber(ParseTree.VariableDelegate delegate, String logIndent) {
    int value = mCondition.resolveToInteger(delegate, logIndent);
    ParseTreeNode node = mCases.get(value);
    if (node != null) {
      return node.resolveToNumber(delegate, logIndent);
    } else {
      return mDefault.resolveToNumber(delegate, logIndent);
    }
  }

  @Override
  public CharSequence resolveToString(ParseTree.VariableDelegate delegate, String logIndent) {
    int value = mCondition.resolveToInteger(delegate, logIndent);
    ParseTreeNode node = mCases.get(value);
    if (node != null) {
      return node.resolveToString(delegate, logIndent);
    } else {
      return mDefault.resolveToString(delegate, logIndent);
    }
  }

  @Override
  public @Nullable ParseTree.VariableDelegate resolveToReference(
      ParseTree.VariableDelegate delegate, String logIndent) {
    int value = mCondition.resolveToInteger(delegate, logIndent);
    ParseTreeNode node = mCases.get(value);
    if (node != null) {
      return node.resolveToReference(delegate, logIndent);
    } else {
      return mDefault.resolveToReference(delegate, logIndent);
    }
  }

  @Override
  public List<CharSequence> resolveToArray(ParseTree.VariableDelegate delegate, String logIndent) {
    int value = mCondition.resolveToInteger(delegate, logIndent);
    ParseTreeNode node = mCases.get(value);
    if (node != null) {
      return node.resolveToArray(delegate, logIndent);
    } else {
      return mDefault.resolveToArray(delegate, logIndent);
    }
  }
}
