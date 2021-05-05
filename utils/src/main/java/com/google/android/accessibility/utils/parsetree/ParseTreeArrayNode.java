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

import java.util.ArrayList;
import java.util.List;

class ParseTreeArrayNode extends ParseTreeNode {
  private final List<ParseTreeNode> mChildren = new ArrayList<>();

  ParseTreeArrayNode(List<ParseTreeNode> children) {
    for (ParseTreeNode child : children) {
      if (!child.canCoerceTo(ParseTree.VARIABLE_STRING)
          && !child.canCoerceTo(ParseTree.VARIABLE_ARRAY)) {
        throw new IllegalStateException("Only strings and arrays can be children of arrays.");
      }
    }
    mChildren.addAll(children);
  }

  @Override
  public int getType() {
    return ParseTree.VARIABLE_ARRAY;
  }

  @Override
  public List<CharSequence> resolveToArray(ParseTree.VariableDelegate delegate, String logIndent) {
    List<CharSequence> result = new ArrayList<>();
    for (ParseTreeNode child : mChildren) {
      if (child.canCoerceTo(ParseTree.VARIABLE_STRING)) {
        result.add(child.resolveToString(delegate, logIndent));
      } else if (child.canCoerceTo(ParseTree.VARIABLE_ARRAY)) {
        result.addAll(child.resolveToArray(delegate, logIndent));
      }
    }
    return result;
  }
}
