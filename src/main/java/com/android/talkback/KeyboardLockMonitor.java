/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.talkback;

import android.content.Context;
import android.view.KeyEvent;
import com.google.android.marvin.talkback.TalkBackService;

/**
 * Watches for changes in the keyboard lock state, such as Caps Lock or Num Lock.
 */
public class KeyboardLockMonitor implements TalkBackService.KeyEventListener {

    private final SpeechController mSpeechController;
    private final Context mContext;

    public KeyboardLockMonitor(SpeechController speechController, TalkBackService context) {
        mSpeechController = speechController;
        mContext = context;
    }

    @Override
    public boolean onKeyEvent(KeyEvent event) {
        // Lock state changes should only occur on key up. If we don't check for key up, two events
        // will fire. This is especially noticeable if the user holds down the Caps Lock key for
        // a while before releasing.
        if (event.getAction() == KeyEvent.ACTION_UP) {
            CharSequence announcement = null;

            if (event.getKeyCode() == KeyEvent.KEYCODE_CAPS_LOCK) {
                if (event.isCapsLockOn()) {
                    announcement = mContext.getString(R.string.value_caps_lock_on);
                } else {
                    announcement = mContext.getString(R.string.value_caps_lock_off);
                }
            } else if (event.getKeyCode() == KeyEvent.KEYCODE_NUM_LOCK) {
                if (event.isNumLockOn()) {
                    announcement = mContext.getString(R.string.value_num_lock_on);
                } else {
                    announcement = mContext.getString(R.string.value_num_lock_off);
                }
            } else if (event.getKeyCode() == KeyEvent.KEYCODE_SCROLL_LOCK) {
                if (event.isScrollLockOn()) {
                    announcement = mContext.getString(R.string.value_scroll_lock_on);
                } else {
                    announcement = mContext.getString(R.string.value_scroll_lock_off);
                }
            }

            if (announcement != null) {
                mSpeechController.speak(announcement, SpeechController.QUEUE_MODE_INTERRUPT,
                        FeedbackItem.FLAG_NO_HISTORY, null);
            }
        }

        return false; // Never intercept keys; only report on their state.
    }

    @Override
    public boolean processWhenServiceSuspended() {
        return false;
    }
}
