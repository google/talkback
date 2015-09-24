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

import android.view.KeyEvent;
import android.view.ViewConfiguration;

public class SingleVolumeButtonShortPressPatternMatcher extends VolumeButtonPatternMatcher {

    private static final int LONG_PRESS_TIMEOUT = ViewConfiguration.getLongPressTimeout();

    private VolumeButtonAction mAction;

    public SingleVolumeButtonShortPressPatternMatcher(int keyCode) {
        super(VolumeButtonPatternDetector.SHORT_PRESS_PATTERN, keyCode);
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
        return mAction != null && !mAction.pressed &&
                mAction.endTimestamp - mAction.startTimestamp < LONG_PRESS_TIMEOUT;
    }

    @Override
    public void clear() {
        mAction = null;
    }
}
