/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.talkback;

import android.os.SystemClock;
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;

/**
 * Maintains a history of when actions occurred.
 */
// TODO(KM): make this non-global
public class PasteHistory {
    /** Lazily-initialized shared instance. */
    private static PasteHistory sInstance;

    /**
     * @return A shared instance of the action history.
     */
    public static PasteHistory getInstance() {
        if (sInstance == null) {
            sInstance = new PasteHistory();
        }
        return sInstance;
    }

    /** Map of action identifiers to start times. */
    private long mStartTime = -1;

    /** Map of action identifiers to finish times. */
    private long mFinishTime = -1;

    private PasteHistory() {
        // This class is not publicly instantiable.
    }

    /**
     * Stores the start time for a paste action. This should be called immediately before
     * {@link AccessibilityNodeInfoCompat#performAction}.
     */
    public void before() {
        mStartTime = SystemClock.uptimeMillis();
    }

    /**
     * Stores the finish time for a paste action. This should be called immediately after
     * {@link AccessibilityNodeInfoCompat#performAction}.
     */
    public void after() {
        mFinishTime = SystemClock.uptimeMillis();
    }

    /**
     * Returns whether the specified event time falls between the start and finish times of the last
     * paste action.
     *
     * @param eventTime The event time to check.
     * @return {@code true} if the event time falls between the start and finish
     *         times of the specified action.
     */
    public boolean hasActionAtTime(long eventTime) {
        return !((mStartTime == -1) || (mStartTime > eventTime))
                && !((mFinishTime >= mStartTime) && (mFinishTime < eventTime));
    }
}
