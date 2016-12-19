/*
 * Copyright (C) 2015 Google Inc.
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

package com.googlecode.eyesfree.testing;

import android.accessibilityservice.AccessibilityService;
import android.annotation.TargetApi;
import android.os.Build;
import android.view.accessibility.AccessibilityEvent;

import com.android.utils.AccessibilityEventListener;
import com.android.utils.compat.CompatUtils;

import java.lang.reflect.Field;

public class TestAccessibilityService extends AccessibilityService {
    private static Field FIELD_mConnectionId = CompatUtils.getField(
            AccessibilityService.class, "mConnectionId");
    private static TestAccessibilityService sInstance = null;

    private AccessibilityEventListener mEventListener;

    static {
        FIELD_mConnectionId.setAccessible(true);
    }

    /**
     * Returns the active instance of {@link com.googlecode.eyesfree.testing.TestAccessibilityService} if
     * available, or {@code null} otherwise.
     */
    public static TestAccessibilityService getInstance() {
        return sInstance;
    }

    public void setTestingListener(AccessibilityEventListener eventListener) {
        mEventListener = eventListener;
    }

    @Override
    public void onServiceConnected() {
        sInstance = this;
    }

    @Override
    public void onDestroy() {
        sInstance = null;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (mEventListener != null) {
            mEventListener.onAccessibilityEvent(event);
        }
    }

    @Override
    public void onInterrupt() {
        // Do nothing.
    }
}
