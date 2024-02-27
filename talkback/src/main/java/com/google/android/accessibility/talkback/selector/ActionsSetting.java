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

import static com.google.android.accessibility.talkback.selector.SelectorController.Setting.ACTIONS;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import com.google.android.accessibility.talkback.focusmanagement.AccessibilityFocusMonitor;
import com.google.android.accessibility.talkback.menurules.NodeMenuRuleCreator;
import com.google.android.accessibility.talkback.menurules.NodeMenuRuleCreator.MenuRules;
import com.google.android.accessibility.utils.Role;
import com.google.android.accessibility.utils.SharedPreferencesUtils;

/**
 * Contextual setting for node with custom actions.
 *
 * <p><b>Note:</b> Custom actions are reported and handled by custom widgets. For example, an
 * application may define a custom action for clearing the user history.
 */
public class ActionsSetting implements SelectorController.ContextualSetting {
  private final NodeMenuRuleCreator nodeMenuCreator;
  private final AccessibilityFocusMonitor accessibilityFocusMonitor;

  public ActionsSetting(
      NodeMenuRuleCreator nodeMenuCreator, AccessibilityFocusMonitor accessibilityFocusMonitor) {
    this.nodeMenuCreator = nodeMenuCreator;
    this.accessibilityFocusMonitor = accessibilityFocusMonitor;
  }

  @Override
  public SelectorController.Setting getSetting() {
    return ACTIONS;
  }

  @Override
  public boolean isNodeSupportSetting(Context context, AccessibilityNodeInfoCompat node) {
    if (node == null) {
      return false;
    }
    SharedPreferences prefs = SharedPreferencesUtils.getSharedPreferences(context);
    boolean settingEnabled =
        prefs.getBoolean(
            context.getString(ACTIONS.prefKeyResId),
            context.getResources().getBoolean(ACTIONS.defaultValueResId));

    return settingEnabled
        && !nodeMenuCreator
            .getNodeMenuByRule(MenuRules.RULE_CUSTOM_ACTION, context, node, true)
            .isEmpty();
  }

  @Override
  public boolean shouldActivateSetting(Context context, AccessibilityNodeInfoCompat node) {
    // For better editing experience, keeps the setting of Reading Controls when navigating to an
    // edit text.
    if (Role.getRole(node) == Role.ROLE_EDIT_TEXT) {
      return false;
    }

    if (accessibilityFocusMonitor.getEditingNodeFromFocusedKeyboard(node) != null) {
      return false;
    }

    return isNodeSupportSetting(context, node);
  }
}
