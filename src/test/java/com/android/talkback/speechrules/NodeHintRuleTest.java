/*
 * Copyright (C) 2016 Google Inc.
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

package com.android.talkback.speechrules;

import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build;
import android.support.v4.view.ViewCompat;
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import android.view.View;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityWindowInfo;
import android.widget.Button;
import android.widget.EditText;

import com.android.switchaccess.test.ShadowAccessibilityNodeInfo;
import com.android.switchaccess.test.ShadowAccessibilityWindowInfo;
import com.android.talkback.BuildConfig;
import com.android.talkback.speechrules.NodeHintRule;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricGradleTestRunner;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.internal.ShadowExtractor;

import java.lang.CharSequence;
import java.lang.Override;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;


/**
 * Tests for NodeHintRule
 */
@Config(constants = BuildConfig.class,
        sdk = 21,
        shadows = {ShadowAccessibilityNodeInfo.class,
                ShadowAccessibilityWindowInfo.class,
                ShadowAccessibilityNodeInfo.ShadowAccessibilityAction.class})
@TargetApi(Build.VERSION_CODES.LOLLIPOP)
@RunWith(RobolectricGradleTestRunner.class)
public class NodeHintRuleTest {

    private Context mContext = RuntimeEnvironment.application.getApplicationContext();
    private AccessibilityNodeInfoCompat mNodeInfo;

    @Before
    public void setUp() {
        ShadowAccessibilityNodeInfo.resetObtainedInstances();
        mNodeInfo = AccessibilityNodeInfoCompat.obtain();
    }

    @After
    public void tearDown() {
        try {
            mNodeInfo.recycle();
            assertFalse(ShadowAccessibilityNodeInfo.areThereUnrecycledNodes(true));
        } finally {
            ShadowAccessibilityNodeInfo.resetObtainedInstances();
        }
    }

    @Test
    public void testLongClickableEditTextNodes_shouldNotHaveDuplicateHintText() {
        NodeHintRule.NodeHintHelper.updateHints(false, false);
        mNodeInfo.setFocused(false);
        mNodeInfo.setEnabled(true);
        mNodeInfo.setClickable(true);
        mNodeInfo.setLongClickable(true);
        mNodeInfo.setClassName(Button.class.getName());
        mNodeInfo.addAction(new AccessibilityNodeInfoCompat.AccessibilityActionCompat(
                AccessibilityNodeInfoCompat.ACTION_CLICK, "expand"));
        mNodeInfo.addAction(new AccessibilityNodeInfoCompat.AccessibilityActionCompat(
                AccessibilityNodeInfoCompat.ACTION_LONG_CLICK, "collapse"));

        assertTrue("Double-tap to expand. Double-tap and hold to collapse.".equalsIgnoreCase(
                NodeHintRule.NodeHintHelper.getDefaultHintString(mContext, mNodeInfo).toString()));
    }

    // TODO: add test case of keyboard navigation.
}

