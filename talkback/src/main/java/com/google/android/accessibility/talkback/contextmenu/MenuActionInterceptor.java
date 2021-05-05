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

import android.view.MenuItem;

/**
 * MenuActionInterceptor is injected to MenuManager to intercept or augment menu item or menu dialog
 * button clicks
 */
public interface MenuActionInterceptor {
  /**
   * @param item menu item that was clicked
   * @return whether event should be intercepted
   */
  public boolean onInterceptMenuClick(MenuItem item);
  /** Callback that is called before cancel menu button was clicked */
  public void onCancelButtonClicked();
}
