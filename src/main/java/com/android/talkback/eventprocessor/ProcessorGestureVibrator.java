/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.talkback.eventprocessor;

import android.annotation.TargetApi;
import android.os.Build;
import android.os.Handler;
import android.support.v4.view.accessibility.AccessibilityEventCompat;
import android.view.accessibility.AccessibilityEvent;
import com.android.talkback.R;
import com.android.talkback.controller.FeedbackController;
import com.android.utils.AccessibilityEventListener;

/**
 * Produces continuous vibration feedback during framework gesture recognition.
 * <p>
 * Requires API 17+ (JB MR1).
 * </p>
 */
@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
public class ProcessorGestureVibrator implements AccessibilityEventListener {
    /** The minimum API level required to use this class. */
    public static final int MIN_API_LEVEL = Build.VERSION_CODES.JELLY_BEAN_MR1;

    /** Delay after a gesture starts before feedback begins. */
    private static final int FEEDBACK_DELAY = 70;

    /** Feedback controller, used to vibrate and play sounds. */
    private final FeedbackController mFeedbackController;

    public ProcessorGestureVibrator(FeedbackController feedbackController) {
        if (feedbackController == null) throw new IllegalStateException();
        mFeedbackController = feedbackController;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        switch (event.getEventType()) {
            case AccessibilityEventCompat.TYPE_GESTURE_DETECTION_START:
                mHandler.postDelayed(mFeedbackRunnable, FEEDBACK_DELAY);
                break;
            case AccessibilityEventCompat.TYPE_GESTURE_DETECTION_END:
                mHandler.removeCallbacks(mFeedbackRunnable);
                mFeedbackController.interrupt();
                break;
        }
    }

    private final Handler mHandler = new Handler();

    private final Runnable mFeedbackRunnable = new Runnable() {
        @Override
        public void run() {
            mFeedbackController.playHaptic(R.array.gesture_detection_repeated_pattern);
            mFeedbackController.playAuditory(R.raw.gesture_begin, 1.0f, 0.5f);
        }
    };
}
