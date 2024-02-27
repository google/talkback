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

package com.google.android.accessibility.talkback.compositor;

import android.content.Context;
import android.view.accessibility.AccessibilityEvent;
import androidx.annotation.Nullable;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import com.google.android.accessibility.talkback.compositor.parsetree.ParseTree;
import com.google.android.accessibility.talkback.compositor.parsetree.ParseTree.VariableDelegate;
import com.google.android.accessibility.utils.ImageContents;

/** Provides an interface for creating VariableDelegates for the Compositor. */
class VariablesFactory {
  private final Context mContext;
  private final GlobalVariables globalVariables;
  @Nullable private final ImageContents imageContents;

  VariablesFactory(
      Context context, GlobalVariables globalVariables, @Nullable ImageContents imageContents) {
    mContext = context;
    this.globalVariables = globalVariables;
    this.imageContents = imageContents;
  }

  // Copies node.
  VariableDelegate createLocalVariableDelegate(
      @Nullable AccessibilityEvent event,
      @Nullable AccessibilityNodeInfoCompat node,
      @Nullable EventInterpretation interpretation) {
    VariableDelegate delegate = globalVariables;
    if (event != null) {
      delegate = new EventVariables(mContext, delegate, event, event.getSource(), globalVariables);
    }

    if (interpretation != null) {
      delegate = new InterpretationVariables(mContext, delegate, interpretation, globalVariables);
    }

    // Node variables is constructed last. This ensures that child nodes it creates have access to
    // top level global variables.
    if (node != null) {
      delegate = new NodeVariables(mContext, imageContents, delegate, node, globalVariables);
    }
    return delegate;
  }

  void declareVariables(ParseTree parseTree) {
    if (globalVariables != null) {
      // Allow mGlobalVariables to be null for tests.
      globalVariables.declareVariables(parseTree);
    }
    InterpretationVariables.declareVariables(parseTree);
    EventVariables.declareVariables(parseTree);
    NodeVariables.declareVariables(parseTree);
    ActionVariables.declareVariables(parseTree);
  }
}
