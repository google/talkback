/*
 * Copyright (C) 2020 Google Inc.
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

package com.google.android.accessibility.talkback;

import android.accessibilityservice.AccessibilityService;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.view.accessibility.AccessibilityEvent;
import com.google.android.accessibility.talkback.controller.TelevisionNavigationController;
import com.google.android.accessibility.utils.AccessibilityEventListener;
import com.google.android.accessibility.utils.Performance;

/**
 * Manages the state whether TalkBack should consume DPad KeyEvents. This class only works on TV
 * devices, when {@link TelevisionNavigationController} is enabled, but will be deprecated.
 */
public class TelevisionDPadManager extends BroadcastReceiver implements AccessibilityEventListener {

  public TelevisionDPadManager(
      TelevisionNavigationController tvNavigationController, AccessibilityService service) {
    if (tvNavigationController == null) {
      throw new IllegalArgumentException();
    }
  }

  @Override
  public void onReceive(Context context, Intent intent) {}

  @Override
  public int getEventTypes() {
    return 0;
  }

  @Override
  public void onAccessibilityEvent(AccessibilityEvent event, Performance.EventId eventId) {}

  public static IntentFilter getFilter() {
    return new IntentFilter();
  }
}