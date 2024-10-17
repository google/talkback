/*
 * Copyright (C) 2023 Google Inc.
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
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;
import com.google.android.accessibility.braille.brailledisplay.controller.BdController;
import com.google.android.accessibility.braille.brailledisplay.platform.BrailleDisplayManager;
import com.google.android.accessibility.braille.brltty.BrlttyEncoder;
import com.google.android.accessibility.braille.brltty.Encoder;
import com.google.android.accessibility.braille.common.BrailleCommonTalkBackSpeaker;
import com.google.android.accessibility.braille.interfaces.BrailleDisplayForBrailleIme;
import com.google.android.accessibility.braille.interfaces.BrailleDisplayForTalkBack;
import com.google.android.accessibility.braille.interfaces.BrailleImeForBrailleDisplay;
import com.google.android.accessibility.braille.interfaces.TalkBackForBrailleCommon;
import com.google.android.accessibility.braille.interfaces.TalkBackForBrailleDisplay;

/** Entry point between TalkBack and the braille display feature. */
public class BrailleDisplay implements BrailleDisplayForTalkBack, BrailleDisplayForBrailleIme {
  private static final String TAG = "BrailleDisplay";
  public static final Encoder.Factory ENCODER_FACTORY = new BrlttyEncoder.BrlttyFactory();

  private boolean isRunning;
  private BrailleDisplayManager brailleDisplayManager;
  private final BdController controller;
  private final AccessibilityService accessibilityService;

  /** Provides BrailleIme callbacks for BrailleDisplay. */
  public interface BrailleImeProvider {
    BrailleImeForBrailleDisplay getBrailleImeForBrailleDisplay();
  }

  public BrailleDisplay(
      AccessibilityService accessibilityService,
      TalkBackForBrailleDisplay talkBackForBrailleDisplay,
      TalkBackForBrailleCommon talkBackForBrailleCommon,
      BrailleImeProvider brailleImeProvider) {
    this.controller =
        new BdController(
            accessibilityService,
            talkBackForBrailleDisplay,
            talkBackForBrailleCommon,
            brailleImeProvider);
    this.accessibilityService = accessibilityService;
    this.brailleDisplayManager =
        new BrailleDisplayManager(accessibilityService, controller, ENCODER_FACTORY);
    BrailleCommonTalkBackSpeaker.getInstance().initialize(talkBackForBrailleCommon);
  }

  /** Starts braille display. */
  @Override
  public void start() {
    BrailleDisplayLog.d(TAG, "start");
    brailleDisplayManager.setAccessibilityServiceContextProvider(() -> accessibilityService);
    brailleDisplayManager.onServiceStarted();
    isRunning = true;
  }

  /** Stops braille display. */
  @Override
  public void stop() {
    BrailleDisplayLog.d(TAG, "stop");
    brailleDisplayManager.onServiceStopped();
    brailleDisplayManager.setAccessibilityServiceContextProvider(() -> null);
    brailleDisplayManager = null;
    isRunning = false;
  }

  /** Notifies receiving accessibility event. */
  @Override
  public void onAccessibilityEvent(AccessibilityEvent accessibilityEvent) {
    if (isRunning) {
      brailleDisplayManager.onAccessibilityEvent(accessibilityEvent);
    }
  }

  @Override
  public boolean onKeyEvent(KeyEvent keyEvent) {
    return brailleDisplayManager.onKeyEvent(keyEvent);
  }

  @Override
  public void onReadingControlChanged(CharSequence readingControlDescription) {
    controller.onReadingControlChanged(readingControlDescription);
  }

  @Override
  public void switchBrailleDisplayOnOrOff() {
    controller.switchBrailleDisplayOnOrOff();
  }

  @Override
  public void onImeVisibilityChanged(boolean visible) {
    controller.getBrailleDisplayForBrailleIme().onImeVisibilityChanged(visible);
  }

  @Override
  public void showOnDisplay(ResultForDisplay result) {
    controller.getBrailleDisplayForBrailleIme().showOnDisplay(result);
  }

  @Override
  public boolean isBrailleDisplayConnectedAndNotSuspended() {
    return controller.getBrailleDisplayForBrailleIme().isBrailleDisplayConnectedAndNotSuspended();
  }

  @Override
  public void suspendInFavorOfBrailleKeyboard() {
    controller.getBrailleDisplayForBrailleIme().suspendInFavorOfBrailleKeyboard();
  }
}
