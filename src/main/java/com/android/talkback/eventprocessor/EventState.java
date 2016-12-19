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

package com.android.talkback.eventprocessor;

import android.os.SystemClock;
import android.util.SparseArray;

/**
 * Single instance that keeps info about events and their time
 */
public class EventState {

    // When moving with granularity focus could be moved to next node automatically. In that case
    // TalkBack will also move with granularity inside newly focused node and pronounce part of
    // the content. There is no need to pronounce the whole content of the node in that case
    public static final int EVENT_SKIP_FOCUS_PROCESSING_AFTER_GRANULARITY_MOVE = 1;
    public static final int EVENT_SKIP_HINT_AFTER_GRANULARITY_MOVE = 2;

    public static final int EVENT_SKIP_FOCUS_PROCESSING_AFTER_CURSOR_CONTROL = 3;
    public static final int EVENT_SKIP_WINDOWS_CHANGED_PROCESSING_AFTER_CURSOR_CONTROL = 4;
    public static final int EVENT_SKIP_WINDOW_STATE_CHANGED_PROCESSING_AFTER_CURSOR_CONTROL = 5;
    public static final int EVENT_SKIP_HINT_AFTER_CURSOR_CONTROL = 6;

    /** Indicates that we want to switch TTS silently, i.e. don't say "Using XYZ engine". */
    public static final int EVENT_SKIP_FEEDBACK_AFTER_QUIET_TTS_CHANGE = 7;

    /** Indicates that TalkBack recently forced a refocus of a node. */
    public static final int EVENT_NODE_REFOCUSED = 8;

    private static final int EVENT_TIMEOUT = 1000;

    private static EventState sInstance = new EventState();

    public static EventState getInstance() {
        return sInstance;
    }

    private SparseArray<Long> mEvents = new SparseArray<>();

    public void addEvent(int event) {
        mEvents.put(event, SystemClock.uptimeMillis());
    }

    public void clearEvent(int event) {
        mEvents.remove(event);
    }

    public boolean checkAndClearRecentEvent(int event) {
        if (hasEvent(event, EVENT_TIMEOUT)) {
            mEvents.remove(event);
            return true;
        }

        return false;
    }

    private boolean hasEvent(int event, long timeout) {
        Long lastEventTime = mEvents.get(event);
        if (lastEventTime != null) {
            return SystemClock.uptimeMillis() - lastEventTime < timeout;
        }

        return false;
    }

    public void clear() {
        mEvents.clear();
    }
}
