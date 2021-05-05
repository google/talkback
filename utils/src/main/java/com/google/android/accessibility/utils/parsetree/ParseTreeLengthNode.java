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

class ParseTreeLengthNode extends ParseTreeNode {
  private final ParseTreeNode mParam;

  ParseTreeLengthNode(ParseTreeNode param) {
    @ParseTree.VariableType int paramType = param.getType();
    if (paramType != ParseTree.VARIABLE_STRING
        && paramType != ParseTree.VARIABLE_ARRAY
        && paramType != ParseTree.VARIABLE_CHILD_ARRAY) {
      throw new IllegalStateException(
          "length() only takes strings, arrays, and child arrays as a parameter.");
    }
    mParam = param;
  }

  @Override
  public int getType() {
    return ParseTree.VARIABLE_INTEGER;
  }

  @Override
  public boolean canCoerceTo(@ParseTree.VariableType int type) {
    return type == ParseTree.VARIABLE_BOOL
        || type == ParseTree.VARIABLE_INTEGER
        || type == ParseTree.VARIABLE_NUMBER;
  }

  @Override
  public boolean resolveToBoolean(ParseTree.VariableDelegate delegate, String logIndent) {
    return getLength(delegate, logIndent) != 0;
  }

  @Override
  public int resolveToInteger(ParseTree.VariableDelegate delegate, String logIndent) {
    return getLength(delegate, logIndent);
  }

  @Override
  public double resolveToNumber(ParseTree.VariableDelegate delegate, String logIndent) {
    return getLength(delegate, logIndent);
  }

  private int getLength(ParseTree.VariableDelegate delegate, String logIndent) {
    if (mParam.getType() == ParseTree.VARIABLE_STRING) {
      CharSequence value = mParam.resolveToString(delegate, logIndent);
      return value == null ? 0 : value.length();
    } else {
      return mParam.getArrayLength(delegate, logIndent);
    }
  }
}
