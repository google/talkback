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
import com.google.android.libraries.accessibility.utils.log.LogUtils;
import java.util.ArrayList;
import java.util.List;

/** Outputs a comment to verbose logging, and optionally increases the indent level. */
class ParseTreeCommentNode extends ParseTreeNode {

  private static final String TAG = "ParseTreeCommentNode";

  private final @Nullable ParseTreeNode mChild;
  private final String mCommentFormat;
  private final Object[] mArgs;
  private final boolean mIndent;

  ParseTreeCommentNode(@Nullable ParseTreeNode child, String commentFormat, Object[] args) {
    mChild = child;
    mCommentFormat = commentFormat;
    mArgs = args;
    mIndent = true;
  }

  ParseTreeCommentNode(@Nullable ParseTreeNode child, String commentFormat, boolean indent) {
    mChild = child;
    mCommentFormat = commentFormat;
    mArgs = new Object[0];
    mIndent = indent;
  }

  ParseTreeCommentNode(
      @Nullable ParseTreeNode child, String commentFormat, Object[] args, boolean indent) {
    mChild = child;
    mCommentFormat = commentFormat;
    mArgs = args;
    mIndent = indent;
  }

  @Override
  public int getType() {
    return mChild != null ? mChild.getType() : ParseTree.VARIABLE_STRING;
  }

  @Override
  public boolean canCoerceTo(@ParseTree.VariableType int type) {
    return mChild != null && mChild.canCoerceTo(type);
  }

  @Override
  public boolean resolveToBoolean(ParseTree.VariableDelegate delegate, String logIndent) {
    logIndent = updateIndent(logIndent);
    LogUtils.v(TAG, "%s%s", logIndent, String.format(mCommentFormat, mArgs));
    return mChild != null && mChild.resolveToBoolean(delegate, logIndent);
  }

  @Override
  public int resolveToInteger(ParseTree.VariableDelegate delegate, String logIndent) {
    logIndent = updateIndent(logIndent);
    LogUtils.v(TAG, "%s%s", logIndent, String.format(mCommentFormat, mArgs));
    if (mChild != null) {
      return mChild.resolveToInteger(delegate, logIndent);
    }
    return 0;
  }

  @Override
  public double resolveToNumber(ParseTree.VariableDelegate delegate, String logIndent) {
    logIndent = updateIndent(logIndent);
    LogUtils.v(TAG, "%s%s", logIndent, String.format(mCommentFormat, mArgs));
    if (mChild != null) {
      return mChild.resolveToNumber(delegate, logIndent);
    }
    return 0;
  }

  @Override
  public CharSequence resolveToString(ParseTree.VariableDelegate delegate, String logIndent) {
    logIndent = updateIndent(logIndent);
    LogUtils.v(TAG, "%s%s", logIndent, String.format(mCommentFormat, mArgs));
    if (mChild != null) {
      return mChild.resolveToString(delegate, logIndent);
    }
    return "";
  }

  @Override
  public @Nullable ParseTree.VariableDelegate resolveToReference(
      ParseTree.VariableDelegate delegate, String logIndent) {
    logIndent = updateIndent(logIndent);
    LogUtils.v(TAG, "%s%s", logIndent, String.format(mCommentFormat, mArgs));
    if (mChild != null) {
      return mChild.resolveToReference(delegate, logIndent);
    }
    return null;
  }

  @Override
  public List<CharSequence> resolveToArray(ParseTree.VariableDelegate delegate, String logIndent) {
    logIndent = updateIndent(logIndent);
    LogUtils.v(TAG, "%s%s", logIndent, String.format(mCommentFormat, mArgs));
    if (mChild != null) {
      return mChild.resolveToArray(delegate, logIndent);
    }
    return new ArrayList<>();
  }

  @Override
  public List<ParseTree.VariableDelegate> resolveToChildArray(
      ParseTree.VariableDelegate delegate, String logIndent) {
    logIndent = updateIndent(logIndent);
    LogUtils.v(TAG, "%s%s", logIndent, String.format(mCommentFormat, mArgs));
    if (mChild != null) {
      return mChild.resolveToChildArray(delegate, logIndent);
    }
    return new ArrayList<>();
  }

  private String updateIndent(String logIndent) {
    return mIndent ? logIndent += "  " : logIndent;
  }
}
