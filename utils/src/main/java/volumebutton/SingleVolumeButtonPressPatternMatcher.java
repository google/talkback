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

import android.os.SystemClock;
import androidx.annotation.Nullable;
import android.view.KeyEvent;
import android.view.ViewConfiguration;
import com.google.android.accessibility.utils.volumebutton.VolumeButtonPatternDetector.ButtonSequence;

/** A {@link VolumeButtonPatternMatcher} to detect short pressing or long pressing on a key. */
public class SingleVolumeButtonPressPatternMatcher extends VolumeButtonPatternMatcher {

  private static final int LONG_PRESS_TIMEOUT = ViewConfiguration.getLongPressTimeout();

  private @Nullable VolumeButtonAction mAction;
  @ButtonSequence private final int patternCode;

  public SingleVolumeButtonPressPatternMatcher(@ButtonSequence int patternCode, int keyCode) {
    super(patternCode, keyCode);
    if (patternCode != VolumeButtonPatternDetector.SHORT_PRESS_PATTERN
        && patternCode != VolumeButtonPatternDetector.LONG_PRESS_PATTERN) {
      throw new IllegalArgumentException(
          "patternCode must be either SHORT_PRESS_PATTERN or LONG_PRESS_PATTERN");
    }
    this.patternCode = patternCode;
  }

  @Override
  public void onKeyEvent(KeyEvent keyEvent) {
    if (keyEvent.getKeyCode() != getButtonCombination()) {
      return;
    }

    if (keyEvent.getAction() == KeyEvent.ACTION_DOWN) {
      handleActionDownEvent(keyEvent);
    } else {
      handleActionUpEvent(keyEvent);
    }
  }

  private void handleActionDownEvent(KeyEvent event) {
    mAction = createAction(event);
  }

  private void handleActionUpEvent(KeyEvent event) {
    if (mAction != null) {
      mAction.pressed = false;
      mAction.endTimestamp = event.getEventTime();
    }
  }

  @Override
  public boolean checkMatch() {
    VolumeButtonAction action = mAction;
    if (action == null) {
      return false;
    }

    if (patternCode == VolumeButtonPatternDetector.SHORT_PRESS_PATTERN) {
      return !action.pressed && action.endTimestamp - action.startTimestamp < LONG_PRESS_TIMEOUT;
    } else if (patternCode == VolumeButtonPatternDetector.LONG_PRESS_PATTERN) {
      long buttonEndTimestamp = action.pressed ? SystemClock.uptimeMillis() : action.endTimestamp;
      return buttonEndTimestamp - action.startTimestamp >= LONG_PRESS_TIMEOUT;
    }

    return false;
  }

  @Override
  public void clear() {
    mAction = null;
  }
}
