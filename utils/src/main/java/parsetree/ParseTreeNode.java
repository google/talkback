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

abstract class ParseTreeNode {

  private static final String TAG = "ParseTreeNode";

  // Returns the type of value this node represents.
  public abstract @ParseTree.VariableType int getType();

  // Returns the enum type of this node.
  public int getEnumType() {
    LogUtils.e(TAG, "Cannot coerce " + getClass() + " to Enum");
    return -1;
  }

  // Returns true if this node can be resolved to the specified type.
  public boolean canCoerceTo(@ParseTree.VariableType int type) {
    return type == getType();
  }

  // Resolve the value of this node to a boolean value.
  public boolean resolveToBoolean(ParseTree.VariableDelegate delegate, String logIndent) {
    LogUtils.e(TAG, "Cannot coerce " + getClass() + " to Boolean");
    return false;
  }

  // Resolve the value of this node to an integer value.
  public int resolveToInteger(ParseTree.VariableDelegate delegate, String logIndent) {
    LogUtils.e(TAG, "Cannot coerce " + getClass() + " to Integer");
    return 0;
  }

  // Resolve the value of this node to a number value.
  public double resolveToNumber(ParseTree.VariableDelegate delegate, String logIndent) {
    LogUtils.e(TAG, "Cannot coerce " + getClass() + " to Number");
    return 0;
  }

  // Resolve the value of this node to a string.
  public CharSequence resolveToString(ParseTree.VariableDelegate delegate, String logIndent) {
    LogUtils.e(TAG, "Cannot coerce " + getClass() + " to String");
    return "";
  }

  // Resolve the value of this node to a reference.
  public @Nullable ParseTree.VariableDelegate resolveToReference(
      ParseTree.VariableDelegate delegate, String logIndent) {
    LogUtils.e(TAG, "Cannot coerce " + getClass() + " to Reference");
    return null;
  }

  // Resolve the value of this node to an array.
  public List<CharSequence> resolveToArray(ParseTree.VariableDelegate delegate, String logIndent) {
    LogUtils.e(TAG, "Cannot coerce " + getClass() + " to Array");
    return new ArrayList<>();
  }

  // Resolve the value of this node to a child array.
  public List<ParseTree.VariableDelegate> resolveToChildArray(
      ParseTree.VariableDelegate delegate, String logIndent) {
    LogUtils.e(TAG, "Cannot coerce " + getClass() + " to Child Array");
    return new ArrayList<>();
  }

  // Query the length as an array.
  int getArrayLength(ParseTree.VariableDelegate delegate, String logIndent) {
    LogUtils.e(TAG, "Cannot query array length of " + getClass());
    return 0;
  }
}
