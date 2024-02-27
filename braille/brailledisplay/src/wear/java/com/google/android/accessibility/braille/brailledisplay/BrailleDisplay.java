/*
 * Copyright (C) 2021 Google Inc.
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
package com.google.android.accessibility.braille.brailledisplay;

import android.accessibilityservice.AccessibilityService;
import android.view.accessibility.AccessibilityEvent;
import com.google.android.accessibility.braille.interfaces.BrailleDisplayForBrailleIme;
import com.google.android.accessibility.braille.interfaces.BrailleDisplayForTalkBack;
import com.google.android.accessibility.braille.interfaces.BrailleImeForBrailleDisplay;
import com.google.android.accessibility.braille.interfaces.TalkBackForBrailleDisplay;

/** Stub class for the build that doesn't include Braille display. */
public class BrailleDisplay implements BrailleDisplayForTalkBack, BrailleDisplayForBrailleIme {

  /** Provides BrailleIme callbacks for BrailleDisplay. */
  public interface BrailleImeProvider {
    BrailleImeForBrailleDisplay getBrailleImeForBrailleDisplay();
  }

  public BrailleDisplay(
      AccessibilityService accessibilityService,
      TalkBackForBrailleDisplay talkBackForBrailleDisplay,
      BrailleImeProvider brailleImeProvider) {}

  @Override
  public void start() {}

  @Override
  public void stop() {}

  @Override
  public void onAccessibilityEvent(AccessibilityEvent accessibilityEvent) {}

  @Override
  public void onReadingControlChanged(CharSequence readingControlDescription) {}

  @Override
  public void onImeVisibilityChanged(boolean visible) {}

  @Override
  public void switchBrailleDisplayOnOrOff() {}

  @Override
  public void showOnDisplay(ResultForDisplay result) {}

  @Override
  public boolean isBrailleDisplayConnectedAndNotSuspended() {
    return false;
  }

  @Override
  public void suspendInFavorOfBrailleKeyboard() {}
}
