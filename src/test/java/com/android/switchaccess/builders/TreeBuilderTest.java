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

package com.android.switchaccess.builders;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Rect;
import android.os.Build;
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import android.text.TextUtils;
import android.view.accessibility.AccessibilityNodeInfo;

import com.android.switchaccess.AccessibilityNodeActionNode;
import com.android.switchaccess.ContextMenuItem;
import com.android.switchaccess.OptionScanActionNode;
import com.android.switchaccess.OptionScanNode;
import com.android.switchaccess.SwitchAccessNodeCompat;
import com.android.switchaccess.SwitchAccessWindowInfo;
import com.android.switchaccess.test.ShadowAccessibilityNodeInfo;
import com.android.switchaccess.test.ShadowAccessibilityService;
import com.android.switchaccess.treebuilding.TreeBuilder;
import com.android.talkback.BuildConfig;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricGradleTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.fakes.RoboSharedPreferences;
import org.robolectric.internal.ShadowExtractor;
import org.robolectric.shadows.ShadowPreferenceManager;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@Config(
        constants = BuildConfig.class,
        sdk = 21,
        shadows = {
                ShadowAccessibilityNodeInfo.class,
                ShadowAccessibilityNodeInfo.ShadowAccessibilityAction.class,
                ShadowAccessibilityService.class})
@TargetApi(Build.VERSION_CODES.LOLLIPOP)
@RunWith(RobolectricGradleTestRunner.class)
public class TreeBuilderTest {
    private static final Rect DUPLICATE_BOUNDS = new Rect(100, 200, 300, 400);
    private final Context mContext = RuntimeEnvironment.application.getApplicationContext();
    private final RoboSharedPreferences mSharedPreferences =
            (RoboSharedPreferences) ShadowPreferenceManager.getDefaultSharedPreferences(mContext);
    private MyTreeBuilder mTreeBuilder;
    private SwitchAccessNodeCompat mDuplicateBoundsParent, mDuplicateBoundsChild;

    @Before
    public void setUp() {
        mTreeBuilder = new MyTreeBuilder(mContext);
        AccessibilityNodeInfo duplicateBoundsParentInfo = AccessibilityNodeInfo.obtain();
        duplicateBoundsParentInfo.setVisibleToUser(true);
        duplicateBoundsParentInfo.setBoundsInScreen(DUPLICATE_BOUNDS);
        duplicateBoundsParentInfo.setContentDescription("Parent");
        AccessibilityNodeInfo duplicateBoundsChildInfo = AccessibilityNodeInfo.obtain();
        duplicateBoundsChildInfo.setVisibleToUser(true);
        duplicateBoundsChildInfo.setBoundsInScreen(DUPLICATE_BOUNDS);
        duplicateBoundsChildInfo.setContentDescription("Child");
        ((ShadowAccessibilityNodeInfo) ShadowExtractor.extract(duplicateBoundsParentInfo))
                .addChild(duplicateBoundsChildInfo);
        mDuplicateBoundsParent = new SwitchAccessNodeCompat(duplicateBoundsParentInfo);
        mDuplicateBoundsChild = new SwitchAccessNodeCompat(duplicateBoundsChildInfo);
    }

    @Test
    public void testListeningForSharedPreferenceChange() {
        assertTrue(mSharedPreferences.hasListener(mTreeBuilder));
    }

    @Test
    public void testUnregisterSharedPreferenceChangeListener() {
        mTreeBuilder.shutdown();
        assertFalse(mSharedPreferences.hasListener(mTreeBuilder));
    }

    @Test
    public void testGetCompatActionNodes_actionDismiss_includedInList() {
        AccessibilityNodeInfo info = AccessibilityNodeInfo.obtain();
        info.addAction(
                AccessibilityNodeInfo.AccessibilityAction.ACTION_DISMISS);
        info.setVisibleToUser(true);
        SwitchAccessNodeCompat compat = new SwitchAccessNodeCompat(info);

        List<AccessibilityNodeActionNode> actionNodes = mTreeBuilder.publicGetCompatActionNodes(compat);
        assertEquals(1, actionNodes.size());
    }

    @Test
    public void testGetCompatActionNodes_actionNextG9yOneLine_hasCharAndWord() {
        AccessibilityNodeInfo info = AccessibilityNodeInfo.obtain();
        info.setEditable(true);
        info.setMultiLine(false);
        info.setText("Howdy");
        info.setTextSelection(1, 1);
        info.addAction(
                AccessibilityNodeInfo.AccessibilityAction.ACTION_NEXT_AT_MOVEMENT_GRANULARITY);
        info.setMovementGranularities(AccessibilityNodeInfo.MOVEMENT_GRANULARITY_CHARACTER
                | AccessibilityNodeInfo.MOVEMENT_GRANULARITY_WORD
                | AccessibilityNodeInfo.MOVEMENT_GRANULARITY_LINE
                | AccessibilityNodeInfo.MOVEMENT_GRANULARITY_PARAGRAPH
                | AccessibilityNodeInfo.MOVEMENT_GRANULARITY_PAGE);

        info.setVisibleToUser(true);
        SwitchAccessNodeCompat compat = new SwitchAccessNodeCompat(info);
        List<AccessibilityNodeActionNode> actionNodes =
                mTreeBuilder.publicGetCompatActionNodes(compat);
        assertEquals(2, actionNodes.size());
    }

    @Test
    public void testGetCompatActionNodes_actionNextG9yMultiLine_hasAll() {
        AccessibilityNodeInfo info = AccessibilityNodeInfo.obtain();
        info.setEditable(true);
        info.setMultiLine(true);
        info.setText("Howdy");
        info.setTextSelection(1, 1);
        info.addAction(
                AccessibilityNodeInfo.AccessibilityAction.ACTION_NEXT_AT_MOVEMENT_GRANULARITY);
        info.setMovementGranularities(AccessibilityNodeInfo.MOVEMENT_GRANULARITY_CHARACTER
                | AccessibilityNodeInfo.MOVEMENT_GRANULARITY_WORD
                | AccessibilityNodeInfo.MOVEMENT_GRANULARITY_LINE
                | AccessibilityNodeInfo.MOVEMENT_GRANULARITY_PARAGRAPH
                | AccessibilityNodeInfo.MOVEMENT_GRANULARITY_PAGE);
        info.setVisibleToUser(true);
        SwitchAccessNodeCompat compat = new SwitchAccessNodeCompat(info);
        List<AccessibilityNodeActionNode> actionNodes =
                mTreeBuilder.publicGetCompatActionNodes(compat);
        assertEquals(5, actionNodes.size());
    }

    @Test
    public void testGetCompatActionNodes_moveForwardInTextButNotEditable_ignoreMovement() {
        AccessibilityNodeInfo info = AccessibilityNodeInfo.obtain();
        info.setEditable(false);
        info.setMultiLine(false);
        info.setText("Howdy");
        info.setTextSelection(1, 1);
        info.addAction(
                AccessibilityNodeInfo.AccessibilityAction.ACTION_NEXT_AT_MOVEMENT_GRANULARITY);
        info.setMovementGranularities(AccessibilityNodeInfo.MOVEMENT_GRANULARITY_CHARACTER
                | AccessibilityNodeInfo.MOVEMENT_GRANULARITY_PAGE);
        info.setVisibleToUser(true);
        SwitchAccessNodeCompat compat = new SwitchAccessNodeCompat(info);
        List<AccessibilityNodeActionNode> actionNodes =
                mTreeBuilder.publicGetCompatActionNodes(compat);
        assertEquals(0, actionNodes.size());
    }

    @Test
    public void testGetCompatActionNodes_moveForwardInTextButAtEnd_ignoreMovement() {
        AccessibilityNodeInfo info = AccessibilityNodeInfo.obtain();
        info.setEditable(true);
        info.setText("Howdy");
        info.setTextSelection(info.getText().length(), info.getText().length());
        info.addAction(
                AccessibilityNodeInfo.AccessibilityAction.ACTION_NEXT_AT_MOVEMENT_GRANULARITY);
        info.setMovementGranularities(AccessibilityNodeInfo.MOVEMENT_GRANULARITY_WORD
                | AccessibilityNodeInfo.MOVEMENT_GRANULARITY_LINE
                | AccessibilityNodeInfo.MOVEMENT_GRANULARITY_PARAGRAPH);
        info.setVisibleToUser(true);
        SwitchAccessNodeCompat compat = new SwitchAccessNodeCompat(info);
        List<AccessibilityNodeActionNode> actionNodes =
                mTreeBuilder.publicGetCompatActionNodes(compat);
        assertEquals(0, actionNodes.size());
    }

    @Test
    public void testGetCompatActionNodes_actionPreviousWithGranularity_allGranularities() {
        AccessibilityNodeInfo info = AccessibilityNodeInfo.obtain();
        info.setEditable(true);
        info.setMultiLine(true);
        info.setText("Howdy");
        info.setTextSelection(1, 1);
        info.addAction(
                AccessibilityNodeInfo.AccessibilityAction.ACTION_PREVIOUS_AT_MOVEMENT_GRANULARITY);
        info.setMovementGranularities(AccessibilityNodeInfo.MOVEMENT_GRANULARITY_WORD
                | AccessibilityNodeInfo.MOVEMENT_GRANULARITY_LINE
                | AccessibilityNodeInfo.MOVEMENT_GRANULARITY_PARAGRAPH);
        info.setVisibleToUser(true);
        SwitchAccessNodeCompat compat = new SwitchAccessNodeCompat(info);
        List<AccessibilityNodeActionNode> actionNodes =
                mTreeBuilder.publicGetCompatActionNodes(compat);
        assertEquals(3, actionNodes.size());
    }

    @Test
    public void testGetCompatActionNodes_moveBackwardInTextButNoEditableText_ignoreMovement() {
        AccessibilityNodeInfo info = AccessibilityNodeInfo.obtain();
        info.setEditable(false);
        info.setText("Howdy");
        info.setTextSelection(1, 1);
        info.addAction(
                AccessibilityNodeInfo.AccessibilityAction.ACTION_PREVIOUS_AT_MOVEMENT_GRANULARITY);
        info.setMovementGranularities(AccessibilityNodeInfo.MOVEMENT_GRANULARITY_WORD
                | AccessibilityNodeInfo.MOVEMENT_GRANULARITY_LINE
                | AccessibilityNodeInfo.MOVEMENT_GRANULARITY_PARAGRAPH);
        info.setVisibleToUser(true);
        SwitchAccessNodeCompat compat = new SwitchAccessNodeCompat(info);
        List<AccessibilityNodeActionNode> actionNodes =
                mTreeBuilder.publicGetCompatActionNodes(compat);
        assertEquals(0, actionNodes.size());
    }

    @Test
    public void testGetCompatActionNodes_moveBackwardInTextButAtStart_ignoreMovement() {
        AccessibilityNodeInfo info = AccessibilityNodeInfo.obtain();
        info.setEditable(true);
        info.setText("Howdy");
        info.setTextSelection(0, 0);
        info.addAction(
                AccessibilityNodeInfo.AccessibilityAction.ACTION_PREVIOUS_AT_MOVEMENT_GRANULARITY);
        info.setMovementGranularities(AccessibilityNodeInfo.MOVEMENT_GRANULARITY_WORD
                | AccessibilityNodeInfo.MOVEMENT_GRANULARITY_LINE
                | AccessibilityNodeInfo.MOVEMENT_GRANULARITY_PARAGRAPH);
        info.setVisibleToUser(true);
        SwitchAccessNodeCompat compat = new SwitchAccessNodeCompat(info);
        List<AccessibilityNodeActionNode> actionNodes =
                mTreeBuilder.publicGetCompatActionNodes(compat);
        assertEquals(0, actionNodes.size());
    }

    @Test
    public void testGetCompatActionNodes_parentAndChildWithSameBounds_parentHasAllActions() {
        mDuplicateBoundsParent
                .addAction(AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_CLICK);
        mDuplicateBoundsChild
                .addAction(AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_LONG_CLICK);
        List<AccessibilityNodeActionNode> actionNodes =
                mTreeBuilder.publicGetCompatActionNodes(mDuplicateBoundsParent);
        assertEquals(2, actionNodes.size());
        SwitchAccessNodeCompat parentActionCompat = actionNodes.get(0).getNodeInfoCompat();
        SwitchAccessNodeCompat childActionCompat = actionNodes.get(1).getNodeInfoCompat();
        assertEquals(mDuplicateBoundsParent, parentActionCompat);
        assertEquals(mDuplicateBoundsChild, childActionCompat);
        assertFalse(TextUtils.equals(actionNodes.get(0).getActionLabel(mContext),
                actionNodes.get(1).getActionLabel(mContext)));
    }

    @Test
    public void testGetCompatActionNodes_parentAndChildWithSameBounds_childActionsSuppressed() {
        mDuplicateBoundsParent
                .addAction(AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_CLICK);
        mDuplicateBoundsChild
                .addAction(AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_LONG_CLICK);
        assertEquals(0, mTreeBuilder.publicGetCompatActionNodes(mDuplicateBoundsChild).size());
    }

    static class MyTreeBuilder extends TreeBuilder {
        public MyTreeBuilder(Context context) {
            super(context);
        }

        @Override
        public OptionScanNode addViewHierarchyToTree(SwitchAccessNodeCompat node,
                OptionScanNode treeToBuildOn) {
            return null;
        }

        @Override
        public OptionScanNode addWindowListToTree(List<SwitchAccessWindowInfo> windowList,
                OptionScanNode treeToBuildOn) {
            return null;
        }

        @Override
        public OptionScanNode buildContextMenu(List<? extends ContextMenuItem> actionList) {
            return null;
        }

        public List<AccessibilityNodeActionNode> publicGetCompatActionNodes(
                SwitchAccessNodeCompat compat) {
            return getCompatActionNodes(compat);
        }
    }
}
