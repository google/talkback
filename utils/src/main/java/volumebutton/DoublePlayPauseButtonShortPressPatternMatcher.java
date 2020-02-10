/*
 * Copyright 2018 Google Inc.
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

import androidx.annotation.Nullable;
import android.view.KeyEvent;
import android.view.ViewConfiguration;
import java.util.concurrent.TimeUnit;

/** Matches the double short press of the play/pause button on wired headset * */
public class DoublePlayPauseButtonShortPressPatternMatcher extends VolumeButtonPatternMatcher {

  private static final long MULTIPLE_TAP_TIMEOUT = TimeUnit.MINUTES.toMillis(1);
  private static final long LONG_PRESS_TIMEOUT = ViewConfiguration.getLongPressTimeout();

  private int buttonPresses;
  @Nullable private VolumeButtonAction lastAction;
  private long lastButtonPressEndTimestamp;

  public DoublePlayPauseButtonShortPressPatternMatcher() {
    super(
        VolumeButtonPatternDetector.SHORT_DOUBLE_PRESS_PARTTERN,
        VolumeButtonPatternDetector.PLAY_PAUSE);
  }

  @Override
  public void onKeyEvent(KeyEvent keyEvent) {
    if (keyEvent.getKeyCode() != getButtonCombination()) {
      return;
    }

    if (interruptActionSequence(keyEvent)) {
      clear();
    }

    if (keyEvent.getAction() == KeyEvent.ACTION_DOWN) {
      lastAction = createAction(keyEvent);
    } else {
      handleActionUpEvent(keyEvent);
    }

    updateCounters();
  }

  private void handleActionUpEvent(KeyEvent event) {
    if (lastAction != null) {
      lastAction.pressed = false;
      lastAction.endTimestamp = event.getEventTime();
    }
  }

  private boolean interruptActionSequence(KeyEvent keyEvent) {
    if (keyEvent.getAction() == KeyEvent.ACTION_DOWN) {
      return isDownEventInterruptsActionSequence(keyEvent);
    } else {
      return isUpEventInterruptsActionSequence(keyEvent);
    }
  }

  private boolean isDownEventInterruptsActionSequence(KeyEvent keyEvent) {
    // Too long timeout between previous buttons pushes.
    return buttonPresses != 0
        && keyEvent.getEventTime() - lastButtonPressEndTimestamp > MULTIPLE_TAP_TIMEOUT;
  }

  private boolean isUpEventInterruptsActionSequence(KeyEvent keyEvent) {
    VolumeButtonAction lastAction = this.lastAction;
    if (lastAction == null) {
      // This is key up on event on non-pressed button.
      return true;
    }

    // Check whether button was pressed too long.
    return keyEvent.getEventTime() - lastAction.startTimestamp > LONG_PRESS_TIMEOUT;
  }

  private void updateCounters() {
    if (lastAction != null && !lastAction.pressed) {
      buttonPresses++;
      lastButtonPressEndTimestamp = lastAction.endTimestamp;
      lastAction = null;
    }
  }

  @Override
  public boolean checkMatch() {
    return buttonPresses >= 2;
  }

  @Override
  public void clear() {
    buttonPresses = 0;
    lastAction = null;
    lastButtonPressEndTimestamp = 0;
  }
}
