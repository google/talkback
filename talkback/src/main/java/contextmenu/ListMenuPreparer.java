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
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import android.view.MenuInflater;
import com.google.android.accessibility.talkback.R;
import com.google.android.accessibility.talkback.TalkBackService;
import com.google.android.accessibility.talkback.menurules.NodeMenuRuleProcessor;
import com.google.android.accessibility.utils.EditTextActionHistory;
import com.google.android.accessibility.utils.FormFactorUtils;
import com.google.android.accessibility.utils.input.TextCursorManager;

/**
 * Used by {@link ListMenuManager} to configure each list-style context menu before display.
 *
 * <p>Uses {@link GlobalMenuProcessor} and {@link NodeMenuRuleProcessor} to help configure menu
 * contents.
 */
public class ListMenuPreparer {

  private final Context mContext;
  private final TalkBackService mService;
  private final EditTextActionHistory mEditTextActionHistory;
  private final TextCursorManager mTextCursorManager;

  public ListMenuPreparer(
      TalkBackService service,
      EditTextActionHistory editTextActionHistory,
      TextCursorManager textCursorManager) {
    mContext = service;
    mService = service;
    mEditTextActionHistory = editTextActionHistory;
    mTextCursorManager = textCursorManager;
  }

  public void prepareMenu(ListMenu menu, int menuId) {
    if (menuId == R.menu.global_context_menu) {
      new MenuInflater(mContext).inflate(R.menu.global_context_menu, menu);
      menu.removeItem(R.id.quick_navigation);
      if (FormFactorUtils.getInstance(mContext).hasAccessibilityShortcut()) {
        menu.removeItem(R.id.pause_feedback);
      }

      GlobalMenuProcessor globalMenuProcessor = new GlobalMenuProcessor(mService);

      globalMenuProcessor.prepareMenu(menu);
      menu.setTitle(mContext.getString(R.string.global_context_menu_title));
    } else if (menuId == R.menu.local_context_menu) {
      final AccessibilityNodeInfoCompat currentNode =
          mService.getCursorController().getCursorOrInputCursor();
      if (currentNode == null) {
        return;
      }

      NodeMenuRuleProcessor menuRuleProcessor =
          new NodeMenuRuleProcessor(mService, mEditTextActionHistory, mTextCursorManager);
      menuRuleProcessor.prepareMenuForNode(menu, currentNode);
      currentNode.recycle();
      menu.setTitle(mContext.getString(R.string.local_context_menu_title));
    } else if (menuId == R.id.custom_action_menu) {
      final AccessibilityNodeInfoCompat currentNode =
          mService.getCursorController().getCursorOrInputCursor();
      if (currentNode == null) {
        return;
      }

      NodeMenuRuleProcessor menuRuleProcessor =
          new NodeMenuRuleProcessor(mService, mEditTextActionHistory, mTextCursorManager);
      menuRuleProcessor.prepareCustomActionMenuForNode(menu, currentNode);
      currentNode.recycle();
      menu.setTitle(mContext.getString(R.string.title_custom_action));
    } else if (menuId == R.id.editing_menu) {
      final AccessibilityNodeInfoCompat currentNode =
          mService.getCursorController().getCursorOrInputCursor();
      if (currentNode == null) {
        return;
      }

      NodeMenuRuleProcessor menuRuleProcessor =
          new NodeMenuRuleProcessor(mService, mEditTextActionHistory, mTextCursorManager);
      menuRuleProcessor.prepareEditingMenuForNode(menu, currentNode);
      currentNode.recycle();
      menu.setTitle(mContext.getString(R.string.title_edittext_controls));
    } else if (menuId == R.menu.language_menu) {
      // Menu for language switcher
      LanguageMenuProcessor.prepareLanguageMenu(mService, menu);
      menu.setTitle(mService.getString(R.string.language_options));
    }
  }
}
