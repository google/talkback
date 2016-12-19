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

package com.android.utils.traversal;

/**
 * Tests for NodeCachedBoundsCalculator
 */

import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import android.view.accessibility.AccessibilityNodeInfo;
import com.android.switchaccess.test.ShadowAccessibilityNodeInfo;
import com.android.talkback.BuildConfig;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricGradleTestRunner;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.internal.ShadowExtractor;

import static org.junit.Assert.*;

@Config(
        constants = BuildConfig.class,
        manifest = Config.NONE,
        sdk = 21,
        shadows = {
                ShadowAccessibilityNodeInfo.class})
@RunWith(RobolectricGradleTestRunner.class)
public class NodeCachedBoundsCalculatorTest {

    @Before
    public void setUp() {
        ShadowAccessibilityNodeInfo.resetObtainedInstances();
    }

    @After
    public void tearDown() {
        assertFalse(ShadowAccessibilityNodeInfo.areThereUnrecycledNodes(true));
        ShadowAccessibilityNodeInfo.resetObtainedInstances();
    }

    @Test(timeout=1000)
    public void getBoundsWithLoop_shouldNotHang() {
        AccessibilityNodeInfo level1Node = AccessibilityNodeInfo.obtain();
        AccessibilityNodeInfo level2Node = AccessibilityNodeInfo.obtain();
        AccessibilityNodeInfo level3Node = AccessibilityNodeInfo.obtain();

        ((ShadowAccessibilityNodeInfo) ShadowExtractor.extract(level1Node)).addChild(level2Node);
        ((ShadowAccessibilityNodeInfo) ShadowExtractor.extract(level2Node)).addChild(level3Node);
        ((ShadowAccessibilityNodeInfo) ShadowExtractor.extract(level3Node)).addChild(level1Node);

        AccessibilityNodeInfoCompat startNode = new AccessibilityNodeInfoCompat(level1Node);

        NodeCachedBoundsCalculator calculator = new NodeCachedBoundsCalculator();
        calculator.getBounds(startNode);

        level1Node.recycle();
        level2Node.recycle();
        level3Node.recycle();
    }
}
