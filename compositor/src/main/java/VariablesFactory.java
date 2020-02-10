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

package com.google.android.accessibility.compositor;

import android.content.Context;
import androidx.annotation.Nullable;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import android.view.accessibility.AccessibilityEvent;
import com.google.android.accessibility.utils.labeling.LabelManager;
import com.google.android.accessibility.utils.parsetree.ParseTree;
import com.google.android.accessibility.utils.parsetree.ParseTree.VariableDelegate;
import java.util.Locale;

/** Provides an interface for creating VariableDelegates for the Compositor. */
class VariablesFactory {
  private final Context mContext;
  private final GlobalVariables mGlobalVariables;
  @Nullable private final LabelManager mLabelManager;
  @Nullable private NodeMenuProvider nodeMenuProvider;
  // Stores the user preferred locale changed using language switcher.
  private @Nullable Locale mUserPreferredLocale;

  VariablesFactory(
      Context context, GlobalVariables globalVariables, @Nullable LabelManager labelManager) {
    mContext = context;
    mGlobalVariables = globalVariables;
    mLabelManager = labelManager;
    mUserPreferredLocale = null;
  }

  ParseTree.VariableDelegate getDefaultDelegate() {
    return mGlobalVariables;
  }

  // Gets the user preferred locale changed using language switcher.
  @Nullable
  Locale getUserPreferredLocale() {
    return mUserPreferredLocale;
  }

  // Sets the user preferred locale changed using language switcher.
  void setUserPreferredLocale(Locale locale) {
    mUserPreferredLocale = locale;
  }

  public void setNodeMenuProvider(@Nullable NodeMenuProvider nodeMenuProvider) {
    this.nodeMenuProvider = nodeMenuProvider;
  }

  // Copies node, does not recycle original node.
  VariableDelegate createLocalVariableDelegate(
      @Nullable AccessibilityEvent event,
      @Nullable AccessibilityNodeInfoCompat node,
      @Nullable EventInterpretation interpretation) {
    VariableDelegate delegate = mGlobalVariables;
    if (event != null) {
      delegate =
          new EventVariables(mContext, delegate, event, event.getSource(), mUserPreferredLocale);
    }

    if (interpretation != null) {
      delegate =
          new InterpretationVariables(mContext, delegate, interpretation, mUserPreferredLocale);
    }

    // Node variables is constructed last. This ensures that child nodes it creates have access to
    // top level global variables.
    if (node != null) {
      delegate =
          new NodeVariables(
              mContext,
              mLabelManager,
              nodeMenuProvider,
              delegate,
              AccessibilityNodeInfoCompat.obtain(node),
              mUserPreferredLocale);
    }
    return delegate;
  }

  void declareVariables(ParseTree parseTree) {
    if (mGlobalVariables != null) {
      // Allow mGlobalVariables to be null for tests.
      mGlobalVariables.declareVariables(parseTree);
    }
    InterpretationVariables.declareVariables(parseTree);
    EventVariables.declareVariables(parseTree);
    NodeVariables.declareVariables(parseTree);
    ActionVariables.declareVariables(parseTree);
  }
}
