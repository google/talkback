/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.google.android.accessibility.talkback.contextmenu;

import com.google.android.accessibility.utils.Performance.EventId;

public class MenuManagerWrapper implements MenuManager {

  private MenuManager mMenuManager;

  public void setMenuManager(MenuManager menuManager) {
    mMenuManager = menuManager;
  }

  @Override
  public boolean showMenu(int menuId, EventId eventId) {
    return mMenuManager != null && mMenuManager.showMenu(menuId, eventId);
  }

  @Override
  public boolean isMenuShowing() {
    return mMenuManager != null && mMenuManager.isMenuShowing();
  }

  @Override
  public void dismissAll() {
    if (mMenuManager != null) {
      mMenuManager.dismissAll();
    }
  }

  @Override
  public void clearCache() {
    if (mMenuManager != null) {
      mMenuManager.clearCache();
    }
  }

  @Override
  public void onGesture(int gesture) {
    if (mMenuManager != null) {
      mMenuManager.onGesture(gesture);
    }
  }

  @Override
  public void setMenuTransformer(MenuTransformer transformer) {
    if (mMenuManager != null) {
      mMenuManager.setMenuTransformer(transformer);
    }
  }

  @Override
  public void setMenuActionInterceptor(MenuActionInterceptor actionInterceptor) {
    if (mMenuManager != null) {
      mMenuManager.setMenuActionInterceptor(actionInterceptor);
    }
  }
}
