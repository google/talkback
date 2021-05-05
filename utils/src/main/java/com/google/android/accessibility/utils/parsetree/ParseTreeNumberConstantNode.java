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

class ParseTreeNumberConstantNode extends ParseTreeNode {
  private final double mValue;

  ParseTreeNumberConstantNode(double value) {
    mValue = value;
  }

  @Override
  public int getType() {
    return ParseTree.VARIABLE_NUMBER;
  }

  @Override
  public boolean canCoerceTo(@ParseTree.VariableType int type) {
    return type == ParseTree.VARIABLE_BOOL
        || type == ParseTree.VARIABLE_NUMBER
        || type == ParseTree.VARIABLE_STRING;
  }

  @Override
  public boolean resolveToBoolean(ParseTree.VariableDelegate delegate, String logIndent) {
    return mValue != 0;
  }

  @Override
  public double resolveToNumber(ParseTree.VariableDelegate delegate, String logIndent) {
    return mValue;
  }

  @Override
  public CharSequence resolveToString(ParseTree.VariableDelegate delegate, String logIndent) {
    return Double.toString(mValue);
  }
}
