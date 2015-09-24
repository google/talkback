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

package com.android.talkback.volumebutton;

import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.ViewConfiguration;

public class DoubleVolumeButtonLongPressPatternMatcher extends VolumeButtonPatternMatcher {

    private static final int LONG_PRESS_TIMEOUT = ViewConfiguration.getLongPressTimeout();

    private VolumeButtonAction mVolumeUpAction;
    private VolumeButtonAction mVolumeDownAction;

    public DoubleVolumeButtonLongPressPatternMatcher() {
        super(VolumeButtonPatternDetector.TWO_BUTTONS_LONG_PRESS_PATTERN,
                VolumeButtonPatternDetector.TWO_BUTTONS);
    }

    @Override
    public void onKeyEvent(KeyEvent keyEvent) {
        if (keyEvent.getKeyCode() != KeyEvent.KEYCODE_VOLUME_DOWN &&
                keyEvent.getKeyCode() != KeyEvent.KEYCODE_VOLUME_UP) {
            return;
        }

        if (keyEvent.getAction() == KeyEvent.ACTION_DOWN) {
            handleActionDownEvent(keyEvent);
        } else {
            handleActionUpEvent(keyEvent);
        }
    }

    private void handleActionDownEvent(KeyEvent event) {
        VolumeButtonAction action = createAction(event);
        if (event.getKeyCode() == KeyEvent.KEYCODE_VOLUME_UP) {
            mVolumeUpAction = action;
        } else {
            mVolumeDownAction = action;
        }
    }

    private void handleActionUpEvent(KeyEvent event) {
        VolumeButtonAction action = null;
        if (event.getKeyCode() == KeyEvent.KEYCODE_VOLUME_UP) {
            action = mVolumeUpAction;
        } else {
            action = mVolumeDownAction;
        }

        if (action != null) {
            action.pressed = false;
            action.endTimestamp = event.getEventTime();
        }
    }

    @Override
    public boolean checkMatch() {
        if (mVolumeUpAction == null || mVolumeDownAction == null) {
            return false;
        }

        long doubleButtonStartTimestamp = Math.max(mVolumeUpAction.startTimestamp,
                mVolumeDownAction.startTimestamp);
        long upButtonEndTimestamp = mVolumeUpAction.pressed ? SystemClock.uptimeMillis() :
                mVolumeUpAction.endTimestamp;
        long downButtonEndTimestamp = mVolumeDownAction.pressed ? SystemClock.uptimeMillis() :
                mVolumeDownAction.endTimestamp;
        long doubleButtonEndTimestamp = Math.min(upButtonEndTimestamp, downButtonEndTimestamp);
        return doubleButtonEndTimestamp - doubleButtonStartTimestamp > LONG_PRESS_TIMEOUT;
    }

    @Override
    public void clear() {
        mVolumeUpAction = null;
        mVolumeDownAction = null;
    }
}
