/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.talkback.tutorial;

import android.accessibilityservice.AccessibilityService;
import android.annotation.TargetApi;
import android.content.Context;
import android.support.annotation.NonNull;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.widget.FrameLayout;

/**
 * Prevents the user from touching the screen and interrupting the tutorial.
 * <p>
 * <b>Note:</b> Because this view doesn't obtain accessibility focus, events
 * sent from an {@link AccessibilityService} are not prevented from reaching the
 * currently focused node.
 * </p>
 */
@TargetApi(16)
public class TouchGuard extends FrameLayout {
    public TouchGuard(Context context) {
        super(context);
    }

    public TouchGuard(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public TouchGuard(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    public boolean dispatchGenericMotionEvent(@NonNull MotionEvent event) {
        // Swallow all motion events.
        return true;
    }

    @Override
    public boolean dispatchTouchEvent(@NonNull MotionEvent event) {
        // Swallow all touch events.
        return true;
    }

    @Override
    public void sendAccessibilityEvent(int eventType) {
        // Never send accessibility events.
    }
}
