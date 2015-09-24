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

package com.android.utils;

import android.annotation.TargetApi;
import android.os.Build;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;

import java.util.List;

@TargetApi(Build.VERSION_CODES.LOLLIPOP)
public class WindowManager {

    public static final int WRONG_WINDOW_TYPE = -1;
    private static final int WRONG_INDEX = -1;

    private static final int PREVIOUS = -1;
    private static final int CURRENT = 0;
    private static final int NEXT = 1;

    private  List<AccessibilityWindowInfo> mWindows;

    /**
     * Set windows that would be used by WindowManager
     * @param windows Set the windows on the screen.
     */
    public void setWindows(List<AccessibilityWindowInfo> windows) {
        mWindows = windows;
    }

    /**
     * returns wheather accessibility focused window has AccessibilityWindowInfo.TYPE_APPLICATION
     * type
     */
    public boolean isApplicationWindowFocused() {
        AccessibilityWindowInfo info = getCurrentWindow();
        return info != null && info.getType() == AccessibilityWindowInfo.TYPE_APPLICATION;
    }

    /**
     * returns true if there is no window with windowType after baseWindow
     */
    public boolean isLastWindow(AccessibilityWindowInfo baseWindow, int windowType) {
        int index = getWindowIndex(baseWindow);
        if (index == WRONG_INDEX) {
            return true;
        }

        int count = mWindows.size();
        for (int i = index + 1; i < count; i++) {
            AccessibilityWindowInfo window = mWindows.get(i);
            if (window != null && window.getType() == windowType) {
                return false;
            }
        }

        return true;
    }

    /**
     * returns true if there is no window with windowType before baseWindow
     */
    public boolean isFirstWindow(AccessibilityWindowInfo baseWindow, int windowType) {
        int index = getWindowIndex(baseWindow);
        if (index <= 0) {
            return true;
        }

        for (int i = index - 1; i > 0; i--) {
            AccessibilityWindowInfo window = mWindows.get(i);
            if (window != null && window.getType() == windowType) {
                return false;
            }
        }

        return true;
    }

    /**
     * @return window that currently accessibilityFocused window.
     * If there is no accessibility focused window it returns first window that has TYPE_APPLICATION
     * or null if there is no window with TYPE_APPLICATION type
     */
    public AccessibilityWindowInfo getCurrentWindow() {
        int currentWindowIndex = getAccessibilityFocusedWindowIndex(mWindows);
        if (currentWindowIndex != WRONG_INDEX) {
            return mWindows.get(currentWindowIndex);
        }

        return null;
    }

    /**
     * @return window that is previous relatively currently accessibilityFocused window.
     * If there is no accessibility focused window it returns first window that has TYPE_APPLICATION
     * or null if there is no window with TYPE_APPLICATION type
     */
    public AccessibilityWindowInfo getPreviousWindow(AccessibilityWindowInfo pivotWindow) {
        return getWindow(pivotWindow, PREVIOUS);
    }

    public boolean isInputWindowOnScreen() {
        if (mWindows == null) {
            return false;
        }

        for (AccessibilityWindowInfo window : mWindows) {
            if (window != null && window.getType() == AccessibilityWindowInfo.TYPE_INPUT_METHOD) {
                return true;
            }
        }

        return false;
    }

    public int getWindowType(int windowId) {
        if (mWindows != null) {
            for (AccessibilityWindowInfo window : mWindows) {
                if (window != null && window.getId() == windowId) {
                    return window.getType();
                }
            }
        }

        return WRONG_WINDOW_TYPE;
    }

    /**
     * @return window that is next relatively currently accessibilityFocused window.
     * If there is no accessibility focused window it returns first window that has TYPE_APPLICATION
     * or null if there is no window with TYPE_APPLICATION type
     */
    public AccessibilityWindowInfo getNextWindow(AccessibilityWindowInfo pivotWindow) {
        return getWindow(pivotWindow, NEXT);
    }

    private AccessibilityWindowInfo getWindow(AccessibilityWindowInfo pivotWindow, int direction) {
        if (mWindows == null || pivotWindow == null ||
                (direction != NEXT && direction != PREVIOUS)) {
            return null;
        }

        int currentWindowIndex = getWindowIndex(pivotWindow);
        int resultIndex;
        if (direction == NEXT) {
            resultIndex = getNextWindowIndex(currentWindowIndex);
        } else {
            resultIndex = getPreviousWindowIndex(currentWindowIndex);
        }

        if (resultIndex == WRONG_INDEX) {
            return null;
        }

        return mWindows.get(resultIndex);
    }

    private int getNextWindowIndex(int currentIndex) {
        int size = mWindows.size();
        if (size == 0 || currentIndex < 0 || currentIndex >= size) {
            return WRONG_INDEX;
        }

        currentIndex++;
        if (currentIndex > size - 1) {
            currentIndex = 0;
        }
        return currentIndex;
    }

    private int getPreviousWindowIndex(int currentIndex) {
        int size = mWindows.size();
        if (size == 0 || currentIndex < 0 || currentIndex >= size) {
            return WRONG_INDEX;
        }

        currentIndex--;
        if (currentIndex < 0) {
            currentIndex = size - 1;
        }
        return currentIndex;
    }

    private int getWindowIndex(AccessibilityWindowInfo windowInfo) {
        if (mWindows == null || windowInfo == null) {
            return WRONG_INDEX;
        }

        int windowSize = mWindows.size();
        for (int i = 0; i < windowSize; i++) {
            if (windowInfo.equals(mWindows.get(i))) {
                return i;
            }
        }

        return WRONG_INDEX;
    }

    private static int getAccessibilityFocusedWindowIndex(List<AccessibilityWindowInfo> windows) {
        if (windows == null) {
            return WRONG_INDEX;
        }

        for (int i = 0, size = windows.size(); i < size; i++) {
            AccessibilityWindowInfo window = windows.get(i);
            if (window != null && window.isAccessibilityFocused()) {
                return i;
            }
        }

        return WRONG_INDEX;
    }

    private static AccessibilityWindowInfo getDefaultWindow(List<AccessibilityWindowInfo> windows) {
        if (windows.size() == 0) {
            return null;
        }

        for (AccessibilityWindowInfo window : windows) {
            if (window != null && window.getType() == AccessibilityWindowInfo.TYPE_APPLICATION) {
                return window;
            }
        }

        return windows.get(0);
    }
}
