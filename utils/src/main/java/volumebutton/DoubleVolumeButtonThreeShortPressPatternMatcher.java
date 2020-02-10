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

import androidx.annotation.Nullable;
import android.view.KeyEvent;
import android.view.ViewConfiguration;
import java.util.concurrent.TimeUnit;

/** Matches 3 short presses on both volume buttons. */
public class DoubleVolumeButtonThreeShortPressPatternMatcher extends VolumeButtonPatternMatcher {

  private static final long MULTIPLE_TAP_TIMEOUT = TimeUnit.MINUTES.toMillis(1);
  private static final long LONG_PRESS_TIMEOUT = ViewConfiguration.getLongPressTimeout();

  private int mDoubleButtonPresses;
  private long mLastDoubleButtonPressEndTimestamp;
  private @Nullable VolumeButtonAction mLastVolumeUpAction;
  private @Nullable VolumeButtonAction mLastVolumeDownAction;

  public DoubleVolumeButtonThreeShortPressPatternMatcher() {
    super(
        VolumeButtonPatternDetector.TWO_BUTTONS_THREE_PRESS_PATTERN,
        VolumeButtonPatternDetector.TWO_BUTTONS);
  }

  @Override
  public void onKeyEvent(KeyEvent keyEvent) {
    if (keyEvent.getKeyCode() != KeyEvent.KEYCODE_VOLUME_DOWN
        && keyEvent.getKeyCode() != KeyEvent.KEYCODE_VOLUME_UP) {
      return;
    }

    if (interruptActionSequence(keyEvent)) {
      clear();
    }

    if (keyEvent.getAction() == KeyEvent.ACTION_DOWN) {
      handleActionDownEvent(keyEvent);
    } else {
      handleActionUpEvent(keyEvent);
    }

    updateCounters();
  }

  private void handleActionDownEvent(KeyEvent event) {
    VolumeButtonAction action = createAction(event);
    if (event.getKeyCode() == KeyEvent.KEYCODE_VOLUME_UP) {
      mLastVolumeUpAction = action;
    } else {
      mLastVolumeDownAction = action;
    }
  }

  private void handleActionUpEvent(KeyEvent event) {
    VolumeButtonAction action;
    if (event.getKeyCode() == KeyEvent.KEYCODE_VOLUME_UP) {
      action = mLastVolumeUpAction;
    } else {
      action = mLastVolumeDownAction;
    }

    if (action != null) {
      action.pressed = false;
      action.endTimestamp = event.getEventTime();
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
    if (mDoubleButtonPresses != 0
        && keyEvent.getEventTime() - mLastDoubleButtonPressEndTimestamp > MULTIPLE_TAP_TIMEOUT) {
      // too long timeout between previous double volume buttons pushes
      return true;
    }

    VolumeButtonAction otherAction = getOtherAction(keyEvent);
    //noinspection RedundantIfStatement
    if (otherAction != null && !otherAction.pressed) {
      // other button was released before current action pressed
      return true;
    }

    return false;
  }

  private boolean isUpEventInterruptsActionSequence(KeyEvent keyEvent) {
    VolumeButtonAction currentAction = getCurrentAction(keyEvent);
    VolumeButtonAction otherAction = getOtherAction(keyEvent);
    if (currentAction == null) {
      // up on non-pressed button
      return true;
    }

    if (otherAction == null) {
      // click detected while other button was not pressed
      return true;
    }

    //noinspection RedundantIfStatement
    if (keyEvent.getEventTime() - currentAction.startTimestamp > LONG_PRESS_TIMEOUT) {
      // button was pressed too long
      return true;
    }

    return false;
  }

  private @Nullable VolumeButtonAction getCurrentAction(KeyEvent keyEvent) {
    if (keyEvent.getKeyCode() == KeyEvent.KEYCODE_VOLUME_UP) {
      return mLastVolumeUpAction;
    } else {
      return mLastVolumeDownAction;
    }
  }

  private @Nullable VolumeButtonAction getOtherAction(KeyEvent keyEvent) {
    if (keyEvent.getKeyCode() == KeyEvent.KEYCODE_VOLUME_UP) {
      return mLastVolumeDownAction;
    } else {
      return mLastVolumeUpAction;
    }
  }

  private void updateCounters() {
    if (mLastVolumeUpAction != null
        && !mLastVolumeUpAction.pressed
        && mLastVolumeDownAction != null
        && !mLastVolumeDownAction.pressed) {
      mDoubleButtonPresses++;
      mLastDoubleButtonPressEndTimestamp =
          Math.max(mLastVolumeUpAction.endTimestamp, mLastVolumeDownAction.endTimestamp);
      mLastVolumeUpAction = null;
      mLastVolumeDownAction = null;
    }
  }

  @Override
  public boolean checkMatch() {
    return mDoubleButtonPresses >= 3;
  }

  @Override
  public void clear() {
    mDoubleButtonPresses = 0;
    mLastDoubleButtonPressEndTimestamp = 0;
    mLastVolumeUpAction = null;
    mLastVolumeDownAction = null;
  }
}
