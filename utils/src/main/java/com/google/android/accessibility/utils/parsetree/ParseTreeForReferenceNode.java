/*
 * Copyright (C) 2017 The Android Open Source Project
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
import java.util.ArrayList;
import java.util.List;

class ParseTreeForReferenceNode extends ParseTreeNode {
  private final ParseTreeNode mReference;
  private final ParseTreeNode mFunction;

  ParseTreeForReferenceNode(ParseTreeNode reference, ParseTreeNode function) {
    if (!reference.canCoerceTo(ParseTree.VARIABLE_REFERENCE)) {
      throw new IllegalStateException("Only references can be children of 'for_reference'");
    }

    mReference = reference;
    mFunction = function;
  }

  @Override
  public int getType() {
    return mFunction.getType();
  }

  @Override
  public boolean canCoerceTo(@ParseTree.VariableType int type) {
    return mFunction.canCoerceTo(type);
  }

  @Override
  public boolean resolveToBoolean(ParseTree.VariableDelegate delegate, String logIndent) {
    ParseTree.VariableDelegate referenceDelegate =
        mReference.resolveToReference(delegate, logIndent);
    if (referenceDelegate != null) {
      boolean result = mFunction.resolveToBoolean(referenceDelegate, logIndent);
      referenceDelegate.cleanup();
      return result;
    } else {
      return false;
    }
  }

  @Override
  public int resolveToInteger(ParseTree.VariableDelegate delegate, String logIndent) {
    ParseTree.VariableDelegate referenceDelegate =
        mReference.resolveToReference(delegate, logIndent);
    if (referenceDelegate != null) {
      int result = mFunction.resolveToInteger(referenceDelegate, logIndent);
      referenceDelegate.cleanup();
      return result;
    } else {
      return 0;
    }
  }

  @Override
  public double resolveToNumber(ParseTree.VariableDelegate delegate, String logIndent) {
    ParseTree.VariableDelegate referenceDelegate =
        mReference.resolveToReference(delegate, logIndent);
    if (referenceDelegate != null) {
      double result = mFunction.resolveToNumber(referenceDelegate, logIndent);
      referenceDelegate.cleanup();
      return result;
    } else {
      return 0;
    }
  }

  @Override
  public CharSequence resolveToString(ParseTree.VariableDelegate delegate, String logIndent) {
    ParseTree.VariableDelegate referenceDelegate =
        mReference.resolveToReference(delegate, logIndent);
    if (referenceDelegate != null) {
      CharSequence result = mFunction.resolveToString(referenceDelegate, logIndent);
      referenceDelegate.cleanup();
      return result;
    } else {
      return "";
    }
  }

  @Override
  public @Nullable ParseTree.VariableDelegate resolveToReference(
      ParseTree.VariableDelegate delegate, String logIndent) {
    ParseTree.VariableDelegate referenceDelegate =
        mReference.resolveToReference(delegate, logIndent);
    if (referenceDelegate != null) {
      ParseTree.VariableDelegate result =
          mFunction.resolveToReference(referenceDelegate, logIndent);
      referenceDelegate.cleanup();
      return result;
    } else {
      return null;
    }
  }

  @Override
  public List<CharSequence> resolveToArray(ParseTree.VariableDelegate delegate, String logIndent) {
    ParseTree.VariableDelegate referenceDelegate =
        mReference.resolveToReference(delegate, logIndent);
    if (referenceDelegate != null) {
      List<CharSequence> result = mFunction.resolveToArray(referenceDelegate, logIndent);
      referenceDelegate.cleanup();
      return result;
    } else {
      return new ArrayList<>();
    }
  }

  @Override
  public List<ParseTree.VariableDelegate> resolveToChildArray(
      ParseTree.VariableDelegate delegate, String logIndent) {
    ParseTree.VariableDelegate referenceDelegate =
        mReference.resolveToReference(delegate, logIndent);
    if (referenceDelegate != null) {
      List<ParseTree.VariableDelegate> result =
          mFunction.resolveToChildArray(referenceDelegate, logIndent);
      referenceDelegate.cleanup();
      return result;
    } else {
      return new ArrayList<>();
    }
  }
}
