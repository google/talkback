/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.google.android.accessibility.utils.compat.view;

import android.os.SystemClock;
import android.view.MotionEvent;
import com.google.android.accessibility.utils.compat.CompatUtils;
import java.lang.reflect.Method;

/** Compat utility class for motion event. */
public class MotionEventCompatUtils {
    private static final Class<?> CLASS_MotionEvent = MotionEvent.class;
    private static final Method METHOD_getSource = CompatUtils.getMethod(CLASS_MotionEvent,
            "getSource");
    private static final Method METHOD_setSource = CompatUtils.getMethod(CLASS_MotionEvent,
            "setSource", int.class);
    private static final Method METHOD_setDownTime = CompatUtils.getMethod(CLASS_MotionEvent,
            "setDownTime", long.class);

    public static final int ACTION_HOVER_MOVE = 0x7;
    public static final int ACTION_HOVER_ENTER = 0x9;
    public static final int ACTION_HOVER_EXIT = 0xA;

    private static long sPreviousDownTime = 0;

    private MotionEventCompatUtils() {
        // This class is non-instantiable.
    }

  /**
   * Gets the source of the event.
   *
   * @return The event source or {@link InputDeviceCompatUtils#SOURCE_UNKNOWN} if unknown.
   */
  public static int getSource(MotionEvent event) {
    return (Integer)
        CompatUtils.invoke(event, InputDeviceCompatUtils.SOURCE_UNKNOWN, METHOD_getSource);
    }

    /**
     * Modifies the source of the event.
     * <p>
     * Introduced in API Level 12. This method has no effect when called using
     * earlier API levels.
     * </p>
     * 
     * @param event The event to modify.
     * @param source The new source.
     */
    public static void setSource(MotionEvent event, int source) {
        CompatUtils.invoke(event, null, METHOD_setSource, source);
    }

    /**
     * Sets the time (in ms) when the user originally pressed down to start a
     * stream of position events.
     * <p>
     * Not a public API. This method has no effect when called using unsupported
     * API levels.
     * </p>
     * 
     * @param event The event to modify.
     */
    public static void setDownTime(MotionEvent event, long downTime) {
        CompatUtils.invoke(event, null, METHOD_setDownTime, downTime);
    }

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

        long downTime = sPreviousDownTime;

        switch (touchEvent.getAction()) {
            case MotionEventCompatUtils.ACTION_HOVER_ENTER:
                touchEvent.setAction(MotionEvent.ACTION_DOWN);
                sPreviousDownTime = SystemClock.uptimeMillis();
                downTime = sPreviousDownTime;
                break;
            case MotionEventCompatUtils.ACTION_HOVER_MOVE:
                touchEvent.setAction(MotionEvent.ACTION_MOVE);
                break;
            case MotionEventCompatUtils.ACTION_HOVER_EXIT:
                touchEvent.setAction(MotionEvent.ACTION_UP);
                sPreviousDownTime = 0;
                break;
            default:
                downTime = touchEvent.getDownTime();
        }

        MotionEventCompatUtils.setSource(touchEvent, InputDeviceCompatUtils.SOURCE_MOUSE);
        MotionEventCompatUtils.setDownTime(touchEvent, downTime);

        return touchEvent;
    }
}
