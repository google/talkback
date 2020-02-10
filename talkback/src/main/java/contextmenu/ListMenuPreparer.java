/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.android.accessibility.talkback.contextmenu;

import android.content.Context;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import android.view.MenuInflater;
import com.google.android.accessibility.talkback.R;
import com.google.android.accessibility.talkback.TalkBackService;
import com.google.android.accessibility.talkback.focusmanagement.AccessibilityFocusMonitor;
import com.google.android.accessibility.talkback.menurules.NodeMenuRuleProcessor;

/**
 * Used by {@link ListMenuManager} to configure each list-style context menu before display.
 *
 * <p>Uses {@link GlobalMenuProcessor} and {@link NodeMenuRuleProcessor} to help configure menu
 * contents.
 */
public class ListMenuPreparer {

  private final Context context;
  private final TalkBackService service;
  private final AccessibilityFocusMonitor accessibilityFocusMonitor;
  private final NodeMenuRuleProcessor nodeMenuRuleProcessor;

  public ListMenuPreparer(
      TalkBackService service,
      AccessibilityFocusMonitor accessibilityFocusMonitor,
      NodeMenuRuleProcessor nodeMenuRuleProcessor) {
    context = service;
    this.service = service;
    this.accessibilityFocusMonitor = accessibilityFocusMonitor;
    this.nodeMenuRuleProcessor = nodeMenuRuleProcessor;
  }

  public void prepareMenu(ListMenu menu, int menuId) {
    if (menuId == R.menu.global_context_menu) {
      new MenuInflater(context).inflate(R.menu.global_context_menu, menu);
      menu.removeItem(R.id.quick_navigation);

      GlobalMenuProcessor globalMenuProcessor = new GlobalMenuProcessor(service);

      globalMenuProcessor.prepareMenu(menu);
      menu.setTitle(context.getString(R.string.global_context_menu_title));
    } else if (menuId == R.menu.local_context_menu) {
      final AccessibilityNodeInfoCompat currentNode =
          accessibilityFocusMonitor.getAccessibilityFocus(/* useInputFocusIfEmpty= */ true);
      if (currentNode == null) {
        return;
      }

      nodeMenuRuleProcessor.prepareMenuForNode(menu, currentNode);
      currentNode.recycle();
      menu.setTitle(context.getString(R.string.local_context_menu_title));
    } else if (menuId == R.id.custom_action_menu) {
      final AccessibilityNodeInfoCompat currentNode =
          accessibilityFocusMonitor.getAccessibilityFocus(/* useInputFocusIfEmpty= */ true);
      if (currentNode == null) {
        return;
      }

      nodeMenuRuleProcessor.prepareCustomActionMenuForNode(menu, currentNode);
      currentNode.recycle();
      menu.setTitle(context.getString(R.string.title_custom_action));
    } else if (menuId == R.id.editing_menu) {
      final AccessibilityNodeInfoCompat currentNode =
          accessibilityFocusMonitor.getAccessibilityFocus(/* useInputFocusIfEmpty= */ true);
      if (currentNode == null) {
        return;
      }

      nodeMenuRuleProcessor.prepareEditingMenuForNode(menu, currentNode);
      currentNode.recycle();
      menu.setTitle(context.getString(R.string.title_edittext_controls));
    } else if (menuId == R.menu.language_menu) {
      // Menu for language switcher
      LanguageMenuProcessor.prepareLanguageMenu(service, menu);
      menu.setTitle(service.getString(R.string.language_options));
    }
  }
}
