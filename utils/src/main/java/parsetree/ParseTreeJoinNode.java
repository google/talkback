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

import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import com.google.android.accessibility.utils.SpannableUtils;
import java.util.List;

/** This class implements a ParseTreeNode that joins a child array into a single string. */
class ParseTreeJoinNode extends ParseTreeNode {
  private final ParseTreeNode mChild;
  private final CharSequence mSeparator;
  private final boolean mPruneEmpty;

  ParseTreeJoinNode(ParseTreeNode child, CharSequence separator, boolean pruneEmpty) {
    if (!child.canCoerceTo(ParseTree.VARIABLE_ARRAY)) {
      throw new IllegalStateException("Only arrays can be children of joins.");
    }

    mChild = child;
    mSeparator = separator;
    mPruneEmpty = pruneEmpty;
  }

  @Override
  public int getType() {
    return ParseTree.VARIABLE_STRING;
  }

  @Override
  public CharSequence resolveToString(ParseTree.VariableDelegate delegate, String logIndent) {
    List<CharSequence> values = mChild.resolveToArray(delegate, logIndent);
    SpannableStringBuilder builder = new SpannableStringBuilder();
    boolean first = true;
    for (CharSequence value : values) {
      if (!mPruneEmpty || !TextUtils.isEmpty(value)) {
        if (mSeparator != null) {
          if (first) {
            first = false;
          } else {
            // We have to wrap each separator with a different span, because a single span object
            // can only be used once in a CharSequence.
            builder.append(SpannableUtils.wrapWithIdentifierSpan(mSeparator));
          }
        }
        builder.append(value);
      }
    }
    return builder;
  }
}
