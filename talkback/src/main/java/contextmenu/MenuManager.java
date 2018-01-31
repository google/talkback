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

import com.google.android.accessibility.utils.Performance.EventId;

/* Configures and controls context menus, whether list or radial menu. */
public interface MenuManager {

  boolean showMenu(int menuId, EventId eventId);

  boolean isMenuShowing();

  void dismissAll();

  void clearCache();

  void onGesture(int gestureId);

  void setMenuTransformer(MenuTransformer transformer);

  void setMenuActionInterceptor(MenuActionInterceptor actionInterceptor);
}
