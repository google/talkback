/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.talkback.controller;

import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

public class TextCursorControllerApp implements TextCursorController {

    private AccessibilityNodeInfoCompat mNode;
    private int mCurrentCursorPosition = NO_POSITION;
    private int mPreviousCursorPosition = NO_POSITION;

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        switch (event.getEventType()) {
            case AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED:
                processTextSelectionChange(event);
                break;
            case AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED:
                clear();
                break;
        }
    }

    private void processTextSelectionChange(AccessibilityEvent event) {
        AccessibilityNodeInfo node = event.getSource();
        if (node == null) {
            clear();
            return;
        }

        AccessibilityNodeInfoCompat compat = new AccessibilityNodeInfoCompat(node);
        if (compat.equals(mNode)) {
            mPreviousCursorPosition = mCurrentCursorPosition;
            mCurrentCursorPosition = event.getToIndex();
        } else {
            clear();
            mNode = compat;
            mCurrentCursorPosition = event.getToIndex();
        }
    }

    private void clear() {
        if (mNode != null) {
            mNode.recycle();
            mNode = null;
        }

        mCurrentCursorPosition = NO_POSITION;
        mPreviousCursorPosition = NO_POSITION;
    }

    @Override
    public AccessibilityNodeInfoCompat getCurrentNode() {
        return mNode;
    }

    @Override
    public int getCurrentCursorPosition() {
        return mCurrentCursorPosition;
    }

    @Override
    public int getPreviousCursorPosition() {
        return mPreviousCursorPosition;
    }
}
