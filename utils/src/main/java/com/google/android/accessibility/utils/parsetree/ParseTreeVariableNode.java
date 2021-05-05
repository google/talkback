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
import android.text.TextUtils;
import com.google.android.libraries.accessibility.utils.log.LogUtils;
import java.util.ArrayList;
import java.util.List;

class ParseTreeVariableNode extends ParseTreeNode {

  private static final String TAG = "ParseTreeVariableNode";

  private final String mName;
  private final @ParseTree.VariableType int mType;
  private final int mId;
  private final int mEnumType;

  ParseTreeVariableNode(String name, @ParseTree.VariableType int type, int id) {
    if (type == ParseTree.VARIABLE_ENUM) {
      throw new IllegalStateException("Enum type required for enums");
    }
    mName = name;
    mType = type;
    mId = id;
    mEnumType = -1;
  }

  ParseTreeVariableNode(String name, @ParseTree.VariableType int type, int id, int enumType) {
    if (type != ParseTree.VARIABLE_ENUM) {
      throw new IllegalStateException("Enum type only applicable to enums");
    }
    mName = name;
    mType = type;
    mId = id;
    mEnumType = enumType;
  }

  @Override
  public int getType() {
    return mType;
  }

  @Override
  public int getEnumType() {
    return mEnumType;
  }

  @Override
  public boolean canCoerceTo(@ParseTree.VariableType int type) {
    if (type == mType) {
      return true;
    }

    switch (mType) {
      case ParseTree.VARIABLE_STRING:
      case ParseTree.VARIABLE_NUMBER:
        return type == ParseTree.VARIABLE_BOOL;

      case ParseTree.VARIABLE_INTEGER:
        return type == ParseTree.VARIABLE_NUMBER || type == ParseTree.VARIABLE_BOOL;

      case ParseTree.VARIABLE_ENUM:
        return type == ParseTree.VARIABLE_INTEGER;

      case ParseTree.VARIABLE_BOOL:
      case ParseTree.VARIABLE_REFERENCE:
      case ParseTree.VARIABLE_ARRAY:
      case ParseTree.VARIABLE_CHILD_ARRAY:
      default:
        return false;
    }
  }

  @Override
  public boolean resolveToBoolean(ParseTree.VariableDelegate delegate, String logIndent) {
    boolean value = false;
    switch (mType) {
      case ParseTree.VARIABLE_BOOL:
        value = delegate.getBoolean(mId);
        break;
      case ParseTree.VARIABLE_INTEGER:
        value = delegate.getInteger(mId) != 0;
        break;
      case ParseTree.VARIABLE_NUMBER:
        value = delegate.getNumber(mId) != 0;
        break;
      case ParseTree.VARIABLE_STRING:
        value = !TextUtils.isEmpty(delegate.getString(mId));
        break;
      case ParseTree.VARIABLE_ENUM:
      case ParseTree.VARIABLE_REFERENCE:
      case ParseTree.VARIABLE_ARRAY:
      case ParseTree.VARIABLE_CHILD_ARRAY:
        LogUtils.e(
            TAG,
            "Cannot coerce variable to boolean: %s %s",
            ParseTree.variableTypeToString(mType),
            mName);
        value = false;
        break;
      default:
        // This should never happen.
        LogUtils.e(TAG, "Unknown variable type: %d", mType);
        return false;
    }

    LogUtils.v(
        TAG,
        "%sParseTreeVariableNode.resolveToBoolean() name=%s value=%s",
        logIndent,
        mName,
        value);
    return value;
  }

  @Override
  public int resolveToInteger(ParseTree.VariableDelegate delegate, String logIndent) {
    switch (mType) {
      case ParseTree.VARIABLE_INTEGER:
        {
          int value = delegate.getInteger(mId);
          LogUtils.v(
              TAG,
              "%sParseTreeVariableNode.resolveToInteger() name=%s value=%s",
              logIndent,
              mName,
              value);
          return value;
        }
      case ParseTree.VARIABLE_ENUM:
        {
          int value = delegate.getEnum(mId);
          LogUtils.v(
              TAG,
              "%sParseTreeVariableNode.resolveToInteger() name=%s value=%s",
              logIndent,
              mName,
              value);
          return value;
        }
      case ParseTree.VARIABLE_NUMBER:
      case ParseTree.VARIABLE_BOOL:
      case ParseTree.VARIABLE_STRING:
      case ParseTree.VARIABLE_REFERENCE:
      case ParseTree.VARIABLE_ARRAY:
      case ParseTree.VARIABLE_CHILD_ARRAY:
        LogUtils.e(
            TAG,
            "Cannot coerce variable to integer: %s %s",
            ParseTree.variableTypeToString(mType),
            mName);
        return 0;
      default: // fall out
    }

    // This should never happen.
    LogUtils.e(TAG, "Unknown variable type: %d", mType);
    return 0;
  }

  @Override
  public double resolveToNumber(ParseTree.VariableDelegate delegate, String logIndent) {
    switch (mType) {
      case ParseTree.VARIABLE_INTEGER:
        {
          int value = delegate.getInteger(mId);
          LogUtils.v(
              TAG,
              "%sParseTreeVariableNode.resolveToNumber() name=%s value=%s",
              logIndent,
              mName,
              value);
          return value;
        }
      case ParseTree.VARIABLE_NUMBER:
        {
          double value = delegate.getNumber(mId);
          LogUtils.v(
              TAG,
              "%sParseTreeVariableNode.resolveToNumber() name=%s value=%s",
              logIndent,
              mName,
              value);
          return value;
        }
      case ParseTree.VARIABLE_BOOL:
      case ParseTree.VARIABLE_STRING:
      case ParseTree.VARIABLE_ENUM:
      case ParseTree.VARIABLE_REFERENCE:
      case ParseTree.VARIABLE_ARRAY:
      case ParseTree.VARIABLE_CHILD_ARRAY:
        LogUtils.e(
            TAG,
            "Cannot coerce variable to number: %s %s",
            ParseTree.variableTypeToString(mType),
            mName);
        return 0;
      default: // fall out
    }

    // This should never happen.
    LogUtils.e(TAG, "Unknown variable type: %d", mType);
    return 0;
  }

  @Override
  public CharSequence resolveToString(ParseTree.VariableDelegate delegate, String logIndent) {
    switch (mType) {
      case ParseTree.VARIABLE_STRING:
        {
          CharSequence value = delegate.getString(mId);
          if (value == null) {
            value = "";
          }
          LogUtils.v(
              TAG,
              "%sParseTreeVariableNode.resolveToString() name=%s value=%s",
              logIndent,
              mName,
              value);
          return value;
        }
      case ParseTree.VARIABLE_BOOL:
      case ParseTree.VARIABLE_INTEGER:
      case ParseTree.VARIABLE_NUMBER:
      case ParseTree.VARIABLE_ENUM:
      case ParseTree.VARIABLE_REFERENCE:
      case ParseTree.VARIABLE_ARRAY:
      case ParseTree.VARIABLE_CHILD_ARRAY:
        LogUtils.e(
            TAG,
            "Cannot coerce variable to string: %s %s",
            ParseTree.variableTypeToString(mType),
            mName);
        return "";
      default: // fall out
    }

    // This should never happen.
    LogUtils.e(TAG, "Unknown variable type: %d", mType);
    return "";
  }

  @Override
  public @Nullable ParseTree.VariableDelegate resolveToReference(
      ParseTree.VariableDelegate delegate, String logIndent) {
    if (mType == ParseTree.VARIABLE_REFERENCE) {
      return delegate.getReference(mId);
    }
    return null;
  }

  @Override
  public List<CharSequence> resolveToArray(ParseTree.VariableDelegate delegate, String logIndent) {
    List<CharSequence> result = new ArrayList<>();
    if (mType == ParseTree.VARIABLE_ARRAY) {
      int length = delegate.getArrayLength(mId);
      for (int i = 0; i < length; i++) {
        CharSequence value = delegate.getArrayStringElement(mId, i);
        if (value == null) {
          value = "";
        }
        LogUtils.v(
            TAG,
            "%sParseTreeVariableNode.resolveToArray() name=%s value=%s",
            logIndent,
            mName,
            value);
        result.add(value);
      }
    } else {
      LogUtils.e(
          TAG,
          "Cannot coerce variable to array: %s %s",
          ParseTree.variableTypeToString(mType),
          mName);
    }
    return result;
  }

  @Override
  public List<ParseTree.VariableDelegate> resolveToChildArray(
      ParseTree.VariableDelegate delegate, String logIndent) {
    List<ParseTree.VariableDelegate> result = new ArrayList<>();
    if (mType == ParseTree.VARIABLE_CHILD_ARRAY) {
      int length = delegate.getArrayLength(mId);
      for (int i = 0; i < length; i++) {
        ParseTree.VariableDelegate value = delegate.getArrayChildElement(mId, i);
        if (value != null) {
          result.add(value);
        }
      }
    } else {
      LogUtils.e(
          TAG,
          "Cannot coerce variable to child array: %s %s",
          ParseTree.variableTypeToString(mType),
          mName);
    }
    return result;
  }

  @Override
  int getArrayLength(ParseTree.VariableDelegate delegate, String logIndent) {
    if (mType == ParseTree.VARIABLE_ARRAY || mType == ParseTree.VARIABLE_CHILD_ARRAY) {
      return delegate.getArrayLength(mId);
    } else {
      return super.getArrayLength(delegate, logIndent);
    }
  }
}
