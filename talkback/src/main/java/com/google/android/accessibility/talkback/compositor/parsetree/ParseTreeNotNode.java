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

package com.google.android.accessibility.talkback.compositor.parsetree;

class ParseTreeNotNode extends ParseTreeNode {
  private final ParseTreeNode mChild;

  ParseTreeNotNode(ParseTreeNode child) {
    if (!child.canCoerceTo(ParseTree.VARIABLE_BOOL)) {
      throw new IllegalStateException("Cannot coerce child node to boolean");
    }

    mChild = child;
  }

  @Override
  public int getType() {
    return ParseTree.VARIABLE_BOOL;
  }

  @Override
  public boolean resolveToBoolean(ParseTree.VariableDelegate delegate, String logIndent) {
    return !mChild.resolveToBoolean(delegate, logIndent);
  }
}
