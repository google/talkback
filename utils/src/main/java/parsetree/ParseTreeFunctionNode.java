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

import android.util.Log;
import com.google.android.accessibility.utils.LogUtils;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

class ParseTreeFunctionNode extends ParseTreeNode {
  private final @ParseTree.VariableType int mType;
  private final Object mDelegate;
  private final Method mFunction;
  private final List<ParseTreeNode> mParams = new ArrayList<>();
  private final int[] mParamTypes;

  ParseTreeFunctionNode(Object delegate, Method function, List<ParseTreeNode> params) {
    Class<?>[] paramTypes = function.getParameterTypes();
    if (params.size() != paramTypes.length) {
      throw new IllegalStateException("Incorrect number of params for: " + function);
    }

    // Store the parameter types.
    mParamTypes = new int[paramTypes.length];
    for (int i = 0; i < paramTypes.length; i++) {
      mParamTypes[i] = getVariableType(paramTypes[i]);
      if (!params.get(i).canCoerceTo(mParamTypes[i])) {
        throw new IllegalStateException("Cannot coerce parameter " + i + " to " + paramTypes[i]);
      }
    }

    mType = getVariableType(function.getReturnType());
    mDelegate = delegate;
    mFunction = function;
    // Make sure we can access the function, even if the visibility isn't public.
    mFunction.setAccessible(true);
    mParams.addAll(params);
  }

  @Override
  public int getType() {
    return mType;
  }

  @Override
  public boolean canCoerceTo(@ParseTree.VariableType int type) {
    if (type == mType) {
      return true;
    }

    switch (mType) {
      case ParseTree.VARIABLE_INTEGER:
        return type == ParseTree.VARIABLE_NUMBER;

      case ParseTree.VARIABLE_BOOL:
      case ParseTree.VARIABLE_NUMBER:
      case ParseTree.VARIABLE_STRING:
      case ParseTree.VARIABLE_ENUM:
      case ParseTree.VARIABLE_ARRAY:
      case ParseTree.VARIABLE_CHILD_ARRAY:
      default:
        return false;
    }
  }

  @Override
  public boolean resolveToBoolean(ParseTree.VariableDelegate delegate, String logIndent) {
    if (mType != ParseTree.VARIABLE_BOOL) {
      LogUtils.log(this, Log.ERROR, "Cannot coerce to Boolean");
      return false;
    }
    try {
      return (Boolean) mFunction.invoke(mDelegate, getParams(delegate, logIndent));
    } catch (Exception e) {
      LogUtils.log(this, Log.ERROR, e.toString());
      return false;
    }
  }

  @Override
  public int resolveToInteger(ParseTree.VariableDelegate delegate, String logIndent) {
    if (mType != ParseTree.VARIABLE_INTEGER) {
      LogUtils.log(this, Log.ERROR, "Cannot coerce to Integer");
      return 0;
    }
    try {
      return (Integer) mFunction.invoke(mDelegate, getParams(delegate, logIndent));
    } catch (Exception e) {
      LogUtils.log(this, Log.ERROR, e.toString());
      return 0;
    }
  }

  @Override
  public double resolveToNumber(ParseTree.VariableDelegate delegate, String logIndent) {
    Object result;
    try {
      result = mFunction.invoke(mDelegate, getParams(delegate, logIndent));
    } catch (Exception e) {
      LogUtils.log(this, Log.ERROR, e.toString());
      return 0;
    }
    if (mType == ParseTree.VARIABLE_INTEGER) {
      return (Integer) result;
    } else if (mType == ParseTree.VARIABLE_NUMBER) {
      return (Double) result;
    } else {
      LogUtils.log(this, Log.ERROR, "Cannot coerce to a Number");
      return 0;
    }
  }

  @Override
  public CharSequence resolveToString(ParseTree.VariableDelegate delegate, String logIndent) {
    Object result;
    try {
      result = mFunction.invoke(mDelegate, getParams(delegate, logIndent));
    } catch (Exception e) {
      LogUtils.log(this, Log.ERROR, e.toString());
      return "";
    }
    if (mType == ParseTree.VARIABLE_STRING) {
      return (CharSequence) result;
    } else {
      return result.toString();
    }
  }

  @SuppressWarnings("unchecked")
  @Override
  public List<CharSequence> resolveToArray(ParseTree.VariableDelegate delegate, String logIndent) {
    if (mType == ParseTree.VARIABLE_ARRAY) {
      try {
        return (List<CharSequence>) mFunction.invoke(mDelegate, getParams(delegate, logIndent));
      } catch (Exception e) {
        LogUtils.log(this, Log.ERROR, e.toString());
      }
    } else {
      LogUtils.log(this, Log.ERROR, "Cannot coerce to an Array");
    }
    return new ArrayList<>();
  }

  private Object[] getParams(ParseTree.VariableDelegate delegate, String logIndent) {
    Object[] result = new Object[mParamTypes.length];
    for (int i = 0; i < mParamTypes.length; i++) {
      switch (mParamTypes[i]) {
        case ParseTree.VARIABLE_BOOL:
          result[i] = mParams.get(i).resolveToBoolean(delegate, logIndent);
          break;
        case ParseTree.VARIABLE_INTEGER:
          result[i] = mParams.get(i).resolveToInteger(delegate, logIndent);
          break;
        case ParseTree.VARIABLE_NUMBER:
          result[i] = mParams.get(i).resolveToNumber(delegate, logIndent);
          break;
        case ParseTree.VARIABLE_STRING:
          result[i] = mParams.get(i).resolveToString(delegate, logIndent);
          break;
        case ParseTree.VARIABLE_ARRAY:
          result[i] = mParams.get(i).resolveToArray(delegate, logIndent);
          break;
        case ParseTree.VARIABLE_ENUM:
        case ParseTree.VARIABLE_CHILD_ARRAY:
        default:
          // This should never happen.
          LogUtils.log(this, Log.ERROR, "Cannot resolve param " + i);
          break;
      }
    }
    return result;
  }

  private static @ParseTree.VariableType int getVariableType(Class clazz) {
    if (clazz == boolean.class) {
      return ParseTree.VARIABLE_BOOL;
    } else if (clazz == int.class) {
      return ParseTree.VARIABLE_INTEGER;
    } else if (clazz == double.class) {
      return ParseTree.VARIABLE_NUMBER;
    } else if (clazz == CharSequence.class) {
      return ParseTree.VARIABLE_STRING;
    } else if (clazz == List.class) {
      return ParseTree.VARIABLE_ARRAY;
    }
    throw new IllegalStateException("Unsupported variable type: " + clazz);
  }
}
