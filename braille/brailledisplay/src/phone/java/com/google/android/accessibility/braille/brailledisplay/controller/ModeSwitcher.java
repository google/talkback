/*
 * Copyright (C) 2012 Google Inc.
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

package com.google.android.accessibility.braille.brailledisplay.controller;

import android.view.accessibility.AccessibilityEvent;
import com.google.android.accessibility.braille.brltty.BrailleInputEvent;

/**
 * Keeps track of the current navigation mode and dispatches events to the navigation modes
 * depending on which one is active.
 */
public class ModeSwitcher implements NavigationMode {
  private final NavigationMode[] modes;
  private int modeIndex = 0;
  private NavigationMode overrideMode;
  private boolean active = false;

  public ModeSwitcher(NavigationMode... modes) {
    this.modes = modes;
  }

  public NavigationMode getCurrentMode() {
    return overrideMode != null ? overrideMode : modes[modeIndex];
  }

  public void switchMode() {
    if (active) {
      getCurrentMode().onDeactivate();
    }
    modeIndex = (modeIndex + 1) % modes.length;
    overrideMode = null;
    if (active) {
      getCurrentMode().onActivate();
    }
  }

  /**
   * Sets a mode, typically not one of the modes that were supplied during constructions, that
   * overrides the current mode. {@code newOverrideMode} is the overriding mode, or {@code null} to
   * go back to the mode that was active before overriding it.
   */
  public void overrideMode(NavigationMode newOverrideMode) {
    NavigationMode oldMode = getCurrentMode();
    NavigationMode newMode = newOverrideMode != null ? newOverrideMode : modes[modeIndex];
    if (newMode == oldMode) {
      return;
    }
    if (active) {
      oldMode.onDeactivate();
    }
    overrideMode = newOverrideMode;
    if (active) {
      newMode.onActivate();
    }
  }

  @Override
  public void onActivate() {
    active = true;
    getCurrentMode().onActivate();
  }

  @Override
  public void onDeactivate() {
    getCurrentMode().onDeactivate();
    active = false;
  }

  @Override
  public boolean onPanLeftOverflow() {
    boolean ret = getCurrentMode().onPanLeftOverflow();
    if (!ret && overrideMode != null) {
      ret = modes[modeIndex].onPanLeftOverflow();
    }
    return ret;
  }

  @Override
  public boolean onPanRightOverflow() {
    boolean ret = getCurrentMode().onPanRightOverflow();
    if (!ret && overrideMode != null) {
      ret = modes[modeIndex].onPanRightOverflow();
    }
    return ret;
  }

  @Override
  public boolean onMappedInputEvent(BrailleInputEvent event) {
    boolean ret = getCurrentMode().onMappedInputEvent(event);
    if (!ret && overrideMode != null) {
      ret = modes[modeIndex].onMappedInputEvent(event);
    }
    return ret;
  }

  @Override
  public boolean onAccessibilityEvent(AccessibilityEvent event) {
    if (overrideMode == null || !overrideMode.onAccessibilityEvent(event)) {
      return modes[modeIndex].onAccessibilityEvent(event);
    }
    return true;
  }
}
