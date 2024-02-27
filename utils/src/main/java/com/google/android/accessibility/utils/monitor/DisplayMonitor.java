/*
 * Copyright (C) 2022 The Android Open Source Project
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

import android.content.Context;
import android.hardware.display.DisplayManager;
import android.hardware.display.DisplayManager.DisplayListener;
import android.os.Handler;
import android.os.Looper;
import android.view.Display;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Listens to display state change. */
public class DisplayMonitor implements DisplayListener {

  private final DisplayManager displayManager;

  private boolean monitoring = false;

  // Default is true since we assume when this class is created, the default display should be on.
  private boolean defaultDisplayOn = true;

  private final List<DisplayStateChangedListener> displayStateChangedListeners =
      new CopyOnWriteArrayList<>();

  /** Listens to the default display's state changes. */
  public interface DisplayStateChangedListener {
    void onDisplayStateChanged(boolean displayOn);
  }

  // incompatible types in assignment.
  @SuppressWarnings("nullness:assignment")
  public DisplayMonitor(Context context) {
    displayManager = (DisplayManager) context.getSystemService(Context.DISPLAY_SERVICE);
  }

  /** Starts monitoring display listener by registering itself to {@link DisplayManager}. */
  // incompatible argument for parameter arg0 of Handler.
  @SuppressWarnings("nullness:argument")
  public void startMonitoring() {
    if (!monitoring) {
      displayManager.registerDisplayListener(this, new Handler(Looper.myLooper()));
      defaultDisplayOn =
          (displayManager.getDisplay(Display.DEFAULT_DISPLAY).getState() == Display.STATE_ON);
      monitoring = true;
    }
  }

  /** Stops monitoring display listener by unregistering itself to {@link DisplayManager}. */
  public void stopMonitoring() {
    if (monitoring) {
      displayManager.unregisterDisplayListener(this);
      monitoring = false;
    }
  }

  /**
   * Adds a {@link DisplayStateChangedListener} listener.
   *
   * @param listener Listener that will be notified when the display state is changed.
   */
  public void addDisplayStateChangedListener(DisplayStateChangedListener listener) {
    displayStateChangedListeners.add(listener);
  }

  /**
   * Removes a {@link DisplayStateChangedListener} listener.
   *
   * @param listener Listener that will be notified when the display state is changed.
   */
  public void removeDisplayStateChangedListener(DisplayStateChangedListener listener) {
    displayStateChangedListeners.remove(listener);
  }

  /** Clears all listeners. */
  public void clearListeners() {
    displayStateChangedListeners.clear();
  }

  @Override
  public void onDisplayAdded(int displayId) {
    if (displayId == Display.DEFAULT_DISPLAY) {
      final boolean displayOn =
          displayManager.getDisplay(Display.DEFAULT_DISPLAY).getState() == Display.STATE_ON;
      for (DisplayStateChangedListener listener : displayStateChangedListeners) {
        listener.onDisplayStateChanged(displayOn);
      }
    }
  }

  @Override
  public void onDisplayRemoved(int displayId) {
    if (displayId == Display.DEFAULT_DISPLAY) {
      for (DisplayStateChangedListener listener : displayStateChangedListeners) {
        listener.onDisplayStateChanged(false);
      }
    }
  }

  @Override
  public void onDisplayChanged(int displayId) {
    if (displayId == Display.DEFAULT_DISPLAY) {
      final boolean displayOn =
          displayManager.getDisplay(Display.DEFAULT_DISPLAY).getState() == Display.STATE_ON;
      if (defaultDisplayOn != displayOn) {
        // We only invoke the callback when the display state has been changed.
        defaultDisplayOn = displayOn;
        for (DisplayStateChangedListener listener : displayStateChangedListeners) {
          listener.onDisplayStateChanged(defaultDisplayOn);
        }
      }
    }
  }
}
