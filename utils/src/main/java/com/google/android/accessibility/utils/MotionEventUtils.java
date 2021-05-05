/*
 * Copyright (C) 2011 Google Inc.
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

package com.google.android.accessibility.utils;

import android.view.MotionEvent;
import com.google.android.accessibility.utils.compat.view.InputDeviceCompatUtils;
import com.google.android.accessibility.utils.compat.view.MotionEventCompatUtils;

/** Utility class for motion event. */
public class MotionEventUtils {
    /**
     * Converts a hover {@link MotionEvent} to touch event by changing its
     * action and source. Returns an modified clone of the original event.
     * <p>
     * The following types are affected:
     * <ul>
     * <li>{@link MotionEventCompatUtils#ACTION_HOVER_ENTER}
     * <li>{@link MotionEventCompatUtils#ACTION_HOVER_MOVE}
     * <li>{@link MotionEventCompatUtils#ACTION_HOVER_EXIT}
     * </ul>
     * 
     * @param hoverEvent The hover event to convert.
     * @return a touch event
     */
    public static MotionEvent convertHoverToTouch(MotionEvent hoverEvent) {
        final MotionEvent touchEvent = MotionEvent.obtain(hoverEvent);
        MotionEventCompatUtils.setSource(hoverEvent, InputDeviceCompatUtils.SOURCE_TOUCHSCREEN);

        switch (hoverEvent.getAction()) {
            case MotionEventCompatUtils.ACTION_HOVER_ENTER:
                touchEvent.setAction(MotionEvent.ACTION_DOWN);
                break;
            case MotionEventCompatUtils.ACTION_HOVER_MOVE:
                touchEvent.setAction(MotionEvent.ACTION_MOVE);
                break;
            case MotionEventCompatUtils.ACTION_HOVER_EXIT:
                touchEvent.setAction(MotionEvent.ACTION_UP);
                break;
        }

        return touchEvent;
    }
}
