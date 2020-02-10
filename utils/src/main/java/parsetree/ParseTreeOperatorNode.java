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

class ParseTreeOperatorNode extends ParseTreeNode {

  private static final String TAG = "ParseTreeOperatorNode";

  private final @ParseTree.Operator int mOperator;
  private final ParseTreeNode mLvalue;
  private final ParseTreeNode mRvalue;

  ParseTreeOperatorNode(
      @ParseTree.Operator int operator, ParseTreeNode lvalue, ParseTreeNode rvalue) {
    mOperator = operator;
    mLvalue = lvalue;
    mRvalue = rvalue;
  }

  @Override
  public int getType() {
    switch (mOperator) {
      case ParseTree.OPERATOR_PLUS:
      case ParseTree.OPERATOR_MINUS:
      case ParseTree.OPERATOR_MULTIPLY:
      case ParseTree.OPERATOR_DIVIDE:
      case ParseTree.OPERATOR_POW:
        if (mLvalue.getType() == ParseTree.VARIABLE_NUMBER
            || mRvalue.getType() == ParseTree.VARIABLE_NUMBER) {
          return ParseTree.VARIABLE_NUMBER;
        } else {
          return ParseTree.VARIABLE_INTEGER;
        }

      case ParseTree.OPERATOR_EQUALS:
      case ParseTree.OPERATOR_NEQUALS:
      case ParseTree.OPERATOR_GT:
      case ParseTree.OPERATOR_LT:
      case ParseTree.OPERATOR_GE:
      case ParseTree.OPERATOR_LE:
      case ParseTree.OPERATOR_AND:
      case ParseTree.OPERATOR_OR:
        return ParseTree.VARIABLE_BOOL;
      default:
        return ParseTree.VARIABLE_BOOL;
    }
  }

  @Override
  public boolean canCoerceTo(@ParseTree.VariableType int type) {
    switch (mOperator) {
      case ParseTree.OPERATOR_PLUS:
      case ParseTree.OPERATOR_MINUS:
      case ParseTree.OPERATOR_MULTIPLY:
      case ParseTree.OPERATOR_DIVIDE:
      case ParseTree.OPERATOR_POW:
        return type == ParseTree.VARIABLE_NUMBER
            || type == ParseTree.VARIABLE_INTEGER
            || type == ParseTree.VARIABLE_STRING;

      case ParseTree.OPERATOR_EQUALS:
      case ParseTree.OPERATOR_NEQUALS:
      case ParseTree.OPERATOR_GT:
      case ParseTree.OPERATOR_LT:
      case ParseTree.OPERATOR_GE:
      case ParseTree.OPERATOR_LE:
      case ParseTree.OPERATOR_AND:
      case ParseTree.OPERATOR_OR:
        return type == ParseTree.VARIABLE_BOOL;
      default:
        return false;
    }
  }

  @Override
  public boolean resolveToBoolean(ParseTree.VariableDelegate delegate, String logIndent) {
    switch (mOperator) {
      case ParseTree.OPERATOR_PLUS:
      case ParseTree.OPERATOR_MINUS:
      case ParseTree.OPERATOR_MULTIPLY:
      case ParseTree.OPERATOR_DIVIDE:
      case ParseTree.OPERATOR_POW:
        LogUtils.e(TAG, "Cannot coerce Number to Boolean");
        return false;

      case ParseTree.OPERATOR_EQUALS:
        return checkEquals(delegate, logIndent);
      case ParseTree.OPERATOR_NEQUALS:
        return !checkEquals(delegate, logIndent);
      case ParseTree.OPERATOR_GT:
        return mLvalue.resolveToNumber(delegate, logIndent)
            > mRvalue.resolveToNumber(delegate, logIndent);
      case ParseTree.OPERATOR_LT:
        return mLvalue.resolveToNumber(delegate, logIndent)
            < mRvalue.resolveToNumber(delegate, logIndent);
      case ParseTree.OPERATOR_GE:
        return mLvalue.resolveToNumber(delegate, logIndent)
            >= mRvalue.resolveToNumber(delegate, logIndent);
      case ParseTree.OPERATOR_LE:
        return mLvalue.resolveToNumber(delegate, logIndent)
            <= mRvalue.resolveToNumber(delegate, logIndent);
      case ParseTree.OPERATOR_AND:
        return mLvalue.resolveToBoolean(delegate, logIndent)
            && mRvalue.resolveToBoolean(delegate, logIndent);
      case ParseTree.OPERATOR_OR:
        return mLvalue.resolveToBoolean(delegate, logIndent)
            || mRvalue.resolveToBoolean(delegate, logIndent);

      default:
        return false;
    }
  }

  @Override
  public int resolveToInteger(ParseTree.VariableDelegate delegate, String logIndent) {
    switch (mOperator) {
      case ParseTree.OPERATOR_PLUS:
        return (int)
            (mLvalue.resolveToNumber(delegate, logIndent)
                + mRvalue.resolveToNumber(delegate, logIndent));
      case ParseTree.OPERATOR_MINUS:
        return (int)
            (mLvalue.resolveToNumber(delegate, logIndent)
                - mRvalue.resolveToNumber(delegate, logIndent));
      case ParseTree.OPERATOR_MULTIPLY:
        return (int)
            (mLvalue.resolveToNumber(delegate, logIndent)
                * mRvalue.resolveToNumber(delegate, logIndent));
      case ParseTree.OPERATOR_DIVIDE:
        return (int)
            (mLvalue.resolveToNumber(delegate, logIndent)
                / mRvalue.resolveToNumber(delegate, logIndent));
      case ParseTree.OPERATOR_POW:
        return (int)
            Math.pow(
                mLvalue.resolveToNumber(delegate, logIndent),
                mRvalue.resolveToNumber(delegate, logIndent));

      case ParseTree.OPERATOR_EQUALS:
      case ParseTree.OPERATOR_NEQUALS:
      case ParseTree.OPERATOR_GT:
      case ParseTree.OPERATOR_LT:
      case ParseTree.OPERATOR_GE:
      case ParseTree.OPERATOR_LE:
      case ParseTree.OPERATOR_AND:
      case ParseTree.OPERATOR_OR:
      default:
        LogUtils.e(TAG, "Cannot coerce Boolean to Integer");
        return 0;
    }
  }

  @Override
  public double resolveToNumber(ParseTree.VariableDelegate delegate, String logIndent) {
    switch (mOperator) {
      case ParseTree.OPERATOR_PLUS:
        return mLvalue.resolveToNumber(delegate, logIndent)
            + mRvalue.resolveToNumber(delegate, logIndent);
      case ParseTree.OPERATOR_MINUS:
        return mLvalue.resolveToNumber(delegate, logIndent)
            - mRvalue.resolveToNumber(delegate, logIndent);
      case ParseTree.OPERATOR_MULTIPLY:
        return mLvalue.resolveToNumber(delegate, logIndent)
            * mRvalue.resolveToNumber(delegate, logIndent);
      case ParseTree.OPERATOR_DIVIDE:
        return mLvalue.resolveToNumber(delegate, logIndent)
            / mRvalue.resolveToNumber(delegate, logIndent);
      case ParseTree.OPERATOR_POW:
        return Math.pow(
            mLvalue.resolveToNumber(delegate, logIndent),
            mRvalue.resolveToNumber(delegate, logIndent));

      case ParseTree.OPERATOR_EQUALS:
      case ParseTree.OPERATOR_NEQUALS:
      case ParseTree.OPERATOR_GT:
      case ParseTree.OPERATOR_LT:
      case ParseTree.OPERATOR_GE:
      case ParseTree.OPERATOR_LE:
      case ParseTree.OPERATOR_AND:
      case ParseTree.OPERATOR_OR:
      default:
        LogUtils.e(TAG, "Cannot coerce Boolean to Number");
        return 0;
    }
  }

  @Override
  public CharSequence resolveToString(ParseTree.VariableDelegate delegate, String logIndent) {
    return Double.toString(resolveToNumber(delegate, logIndent));
  }

  private boolean checkEquals(ParseTree.VariableDelegate delegate, String logIndent) {
    @ParseTree.VariableType int ltype = mLvalue.getType();
    @ParseTree.VariableType int rtype = mRvalue.getType();
    if (ltype == ParseTree.VARIABLE_BOOL && rtype == ParseTree.VARIABLE_BOOL) {
      return mLvalue.resolveToBoolean(delegate, logIndent)
          == mRvalue.resolveToBoolean(delegate, logIndent);
    } else if ((ltype == ParseTree.VARIABLE_INTEGER || ltype == ParseTree.VARIABLE_ENUM)
        && (rtype == ParseTree.VARIABLE_INTEGER || rtype == ParseTree.VARIABLE_ENUM)) {
      return mLvalue.resolveToInteger(delegate, logIndent)
          == mRvalue.resolveToInteger(delegate, logIndent);
    } else if (ltype == ParseTree.VARIABLE_INTEGER && rtype == ParseTree.VARIABLE_NUMBER) {
      return mLvalue.resolveToInteger(delegate, logIndent)
          == mRvalue.resolveToNumber(delegate, logIndent);
    } else if (ltype == ParseTree.VARIABLE_NUMBER && rtype == ParseTree.VARIABLE_INTEGER) {
      return mLvalue.resolveToNumber(delegate, logIndent)
          == mRvalue.resolveToInteger(delegate, logIndent);
    } else if (ltype == ParseTree.VARIABLE_NUMBER && rtype == ParseTree.VARIABLE_NUMBER) {
      return mLvalue.resolveToNumber(delegate, logIndent)
          == mRvalue.resolveToNumber(delegate, logIndent);
    }
    LogUtils.e(TAG, "Incompatible types in compare: %d, %d", ltype, rtype);
    return false;
  }
}
