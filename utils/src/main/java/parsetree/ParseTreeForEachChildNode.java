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

import com.google.android.libraries.accessibility.utils.log.LogUtils;
import java.util.ArrayList;
import java.util.List;

/** This class implements a ParseTreeNode that evaluates a node for each entry in a child array. */
class ParseTreeForEachChildNode extends ParseTreeNode {

  private static final String TAG = "ParseTreeForEachChildNode";

  private final ParseTreeNode mChild;
  private ParseTreeNode mFunction;

  ParseTreeForEachChildNode(ParseTreeNode child) {
    if (!child.canCoerceTo(ParseTree.VARIABLE_CHILD_ARRAY)) {
      throw new IllegalStateException("Only child arrays can be children of 'for_each_child'");
    }

    mChild = child;
  }

  public void setFunction(ParseTreeNode function) {
    mFunction = function;
  }

  @Override
  public int getType() {
    return ParseTree.VARIABLE_ARRAY;
  }

  @Override
  public List<CharSequence> resolveToArray(ParseTree.VariableDelegate delegate, String logIndent) {
    if (mFunction == null) {
      LogUtils.e(TAG, "Missing function node");
      return new ArrayList<>();
    }

    List<CharSequence> result = new ArrayList<>();

    List<ParseTree.VariableDelegate> children = mChild.resolveToChildArray(delegate, logIndent);
    for (ParseTree.VariableDelegate child : children) {
      result.add(mFunction.resolveToString(child, logIndent));
      child.cleanup();
    }
    return result;
  }
}
