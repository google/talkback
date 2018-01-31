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
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import android.view.accessibility.AccessibilityEvent;
import com.google.android.accessibility.utils.labeling.LabelManager;
import com.google.android.accessibility.utils.parsetree.ParseTree;
import com.google.android.accessibility.utils.parsetree.ParseTree.VariableDelegate;
import java.util.Locale;

/** Provides an interface for creating VariableDelegates for the Compositor. */
class VariablesFactory {
  private final Context mContext;
  private final GlobalVariables mGlobalVariables;
  private final LabelManager mLabelManager;
  // Stores the user preferred locale changed using language switcher.
  private Locale mUserPreferredLocale;

  VariablesFactory(Context context, GlobalVariables globalVariables, LabelManager labelManager) {
    mContext = context;
    mGlobalVariables = globalVariables;
    mLabelManager = labelManager;
    mUserPreferredLocale = null;
  }

  ParseTree.VariableDelegate getDefaultDelegate() {
    return mGlobalVariables;
  }

  // Gets the user preferred locale changed using language switcher.
  Locale getUserPreferredLocale() {
    return mUserPreferredLocale;
  }

  // Sets the user preferred locale changed using language switcher.
  void setUserPreferredLocale(Locale locale) {
    mUserPreferredLocale = locale;
  }

  VariableDelegate createLocalVariableDelegate(
      AccessibilityEvent event,
      AccessibilityNodeInfoCompat node,
      EventInterpretation eventInterpreted) {
    VariableDelegate delegate = mGlobalVariables;
    // Node variables is constructed first, so that its parent is mGlobalVariables.  This ensures
    // that child nodes it creates don't have access to top level local variables.
    if (node != null) {
      delegate =
          new NodeVariables(
              mContext,
              mLabelManager,
              delegate,
              AccessibilityNodeInfoCompat.obtain(node),
              mUserPreferredLocale);
    }

    if (event != null) {
      delegate =
          new EventVariables(
              mContext, delegate, event, event.getSource(), eventInterpreted, mUserPreferredLocale);
    }
    return delegate;
  }

  void declareVariables(ParseTree parseTree) {
    if (mGlobalVariables != null) {
      // Allow mGlobalVariables to be null for tests.
      mGlobalVariables.declareVariables(parseTree);
    }
    EventVariables.declareVariables(parseTree);
    NodeVariables.declareVariables(parseTree);
    ActionVariables.declareVariables(parseTree);
  }
}
