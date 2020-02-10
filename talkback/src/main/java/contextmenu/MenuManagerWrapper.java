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

  private MenuManager menuManager;

  public void setMenuManager(MenuManager menuManager) {
    this.menuManager = menuManager;
  }

  @Override
  public boolean showMenu(int menuId, EventId eventId) {
    return menuManager != null && menuManager.showMenu(menuId, eventId);
  }

  @Override
  public boolean isMenuShowing() {
    return menuManager != null && menuManager.isMenuShowing();
  }

  @Override
  public void dismissAll() {
    if (menuManager != null) {
      menuManager.dismissAll();
    }
  }

  @Override
  public void clearCache() {
    if (menuManager != null) {
      menuManager.clearCache();
    }
  }

  @Override
  public void onGesture(int gesture) {
    if (menuManager != null) {
      menuManager.onGesture(gesture);
    }
  }

  @Override
  public void setMenuTransformer(MenuTransformer transformer) {
    if (menuManager != null) {
      menuManager.setMenuTransformer(transformer);
    }
  }

  @Override
  public void setMenuActionInterceptor(MenuActionInterceptor actionInterceptor) {
    if (menuManager != null) {
      menuManager.setMenuActionInterceptor(actionInterceptor);
    }
  }
}
