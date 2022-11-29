/*
 * Copyright (C) 2022 Google Inc.
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

package com.google.android.accessibility.talkback.selector;

import static android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction.ACTION_DRAG_CANCEL;
import static android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction.ACTION_DRAG_DROP;
import static android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction.ACTION_DRAG_START;
import static com.google.android.accessibility.talkback.selector.SelectorController.Setting.ACTIONS;

import android.content.Context;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import com.google.android.accessibility.utils.AccessibilityNodeInfoUtils;
import com.google.android.accessibility.utils.FeatureSupport;
import com.google.android.accessibility.utils.Filter;

/**
 * Contextual setting for node with custom actions.
 *
 * <p><b>Note:</b> Custom actions are reported and handled by custom widgets. For example, an
 * application may define a custom action for clearing the user history.
 */
public class ActionsSetting implements SelectorController.ContextualSetting {

  // TODO: Use RuleCustomAction to collect actions.
  static final Filter<AccessibilityNodeInfoCompat> HAS_ACTION_FOR_MENU =
      new Filter.NodeCompat(
          (node) -> {
            for (AccessibilityNodeInfoCompat.AccessibilityActionCompat action :
                node.getActionList()) {
              if (shouldIncludeAction(action)) {
                return true;
              }
            }
            return false;
          });

  @Override
  public SelectorController.Setting getSetting() {
    return ACTIONS;
  }

  @Override
  public boolean shouldActivateSetting(Context context, AccessibilityNodeInfoCompat node) {
    if (node == null) {
      return false;
    }

    return AccessibilityNodeInfoUtils.isOrHasMatchingAncestor(node, HAS_ACTION_FOR_MENU);
  }

  /** Checks the action should be included in Actions menu or not. */
  static boolean shouldIncludeAction(AccessibilityNodeInfoCompat.AccessibilityActionCompat action) {
    int id = action.getId();
    // TODO: Replace with AndroidX constants
    boolean actionIsDragAndDrop =
        FeatureSupport.supportDragAndDrop()
            && (id == ACTION_DRAG_START.getId()
                || id == ACTION_DRAG_DROP.getId()
                || id == ACTION_DRAG_CANCEL.getId());

    return ((AccessibilityNodeInfoUtils.isCustomAction(action) && (action.getLabel() != null))
        || (id == AccessibilityNodeInfoCompat.ACTION_DISMISS)
        || (id == AccessibilityNodeInfoCompat.ACTION_EXPAND)
        || (id == AccessibilityNodeInfoCompat.ACTION_COLLAPSE)
        || actionIsDragAndDrop);
  }
}
