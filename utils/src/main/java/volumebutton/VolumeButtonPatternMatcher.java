/*
 * Copyright 2015 Google Inc.
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

package com.google.android.accessibility.utils.volumebutton;

import android.view.KeyEvent;
import com.google.android.accessibility.utils.volumebutton.VolumeButtonPatternDetector.ButtonSequence;
import com.google.android.accessibility.utils.volumebutton.VolumeButtonPatternDetector.ButtonsUsed;

/** Matches patterned action on volume buttons. */
// TODO:  Simplify VolumeButtonPatternMatcher subclasses.
public abstract class VolumeButtonPatternMatcher {

  @ButtonSequence private final int mPatternCode;
  @ButtonsUsed private final int mButtonCombination;

  public VolumeButtonPatternMatcher(
      @ButtonSequence int patternCode, @ButtonsUsed int buttonCombination) {
    mPatternCode = patternCode;
    mButtonCombination = buttonCombination;
  }

  public @ButtonSequence int getPatternCode() {
    return mPatternCode;
  }

  public @ButtonsUsed int getButtonCombination() {
    return mButtonCombination;
  }

  protected VolumeButtonAction createAction(KeyEvent downEvent) {
    if (downEvent == null || downEvent.getAction() != KeyEvent.ACTION_DOWN) {
      throw new IllegalArgumentException();
    }

    VolumeButtonAction action = new VolumeButtonAction();
    action.button = downEvent.getKeyCode();
    action.startTimestamp = downEvent.getEventTime();
    action.endTimestamp = downEvent.getEventTime();
    action.pressed = true;

    return action;
  }

  public abstract void onKeyEvent(KeyEvent keyEvent);

  public abstract boolean checkMatch();

  public abstract void clear();
}
