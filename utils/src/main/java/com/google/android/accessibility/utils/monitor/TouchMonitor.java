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

package com.google.android.accessibility.utils.monitor;

import static android.view.accessibility.AccessibilityEvent.TYPE_TOUCH_INTERACTION_END;
import static android.view.accessibility.AccessibilityEvent.TYPE_TOUCH_INTERACTION_START;

import android.view.accessibility.AccessibilityEvent;
import com.google.android.accessibility.utils.Consumer;
import com.google.errorprone.annotations.CheckReturnValue;
import java.util.ArrayList;
import org.checkerframework.checker.nullness.qual.NonNull;

/** Monitors touch-events, and can report changes or current touch state. */
@CheckReturnValue // see go/why-crv
public class TouchMonitor {

  ///////////////////////////////////////////////////////////////////////////////////
  // Member data

  private boolean isUserTouchingScreen = false;
  private final @NonNull ArrayList<@NonNull Consumer<Boolean>> listeners = new ArrayList<>();

  ///////////////////////////////////////////////////////////////////////////////////
  // Construction

  public void addListener(@NonNull Consumer<Boolean> listener) {
    listeners.add(listener);
  }

  ///////////////////////////////////////////////////////////////////////////////////
  // Methods
  public int getEventTypes() {
    return TYPE_TOUCH_INTERACTION_START | TYPE_TOUCH_INTERACTION_END;
  }

  public void onAccessibilityEvent(@NonNull AccessibilityEvent event) {

    boolean wasUserTouchingScreen = isUserTouchingScreen;

    int eventType = event.getEventType();
    if (eventType == TYPE_TOUCH_INTERACTION_START) {
      // TODO: When double tapping on screen, touch interaction start/end events might
      // be sent in reversed order. We might need a more reliable way to detect touch start/end
      // actions.
      isUserTouchingScreen = true;
    } else if (eventType == TYPE_TOUCH_INTERACTION_END) {
      isUserTouchingScreen = false;
    }

    // Notify listeners that touch-state changed.
    if (wasUserTouchingScreen != isUserTouchingScreen) {
      for (@NonNull Consumer<Boolean> listener : listeners) {
        listener.accept(isUserTouchingScreen);
      }
    }
  }

  public boolean isUserTouchingScreen() {
    return isUserTouchingScreen;
  }
}
