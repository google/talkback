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

package com.android.switchaccess.test;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Point;
import android.graphics.Rect;
import android.os.Build;
import android.preference.PreferenceManager;
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import android.view.Display;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import com.android.switchaccess.*;
import com.android.talkback.R;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.fakes.RoboSharedPreferences;
import org.robolectric.internal.ShadowExtractor;
import org.robolectric.shadows.ShadowPreferenceManager;
import org.robolectric.util.ReflectionHelpers;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;
import static org.junit.Assert.assertFalse;
import static org.mockito.Matchers.anyObject;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

/**
 * Robolectric tests for MultiWindowTreeBuilder
 */
@Config(
        emulateSdk = 18,
        shadows = {
                ShadowAccessibilityNodeInfo.class,
                ShadowAccessibilityNodeInfo.ShadowAccessibilityAction.class,
                ShadowAccessibilityNodeInfoCompat.class,
                ShadowAccessibilityNodeInfoCompat.ShadowAccessibilityActionCompat.class,
                ShadowAccessibilityService.class,
                ShadowAccessibilityWindowInfo.class})
@TargetApi(Build.VERSION_CODES.LOLLIPOP)
@RunWith(RobolectricTestRunner.class)
public class MultiWindowTreeBuilderTest {
    private static final CharSequence WINDOW_0_ROOT_CONTENT_DESCRIPTION = "Description 0";
    private static final CharSequence WINDOW_1_ROOT_CONTENT_DESCRIPTION = "Description 1";
    private static final Rect NO_OVERLAP_WINDOW_0_BOUNDS = new Rect(10, 10, 90, 20);
    private static final Rect NO_OVERLAP_WINDOW_1_BOUNDS = new Rect(10, 30, 90, 80);
    private static final Rect OVERLAP_LARGER_WINDOW_BOUNDS = new Rect(0, 0, 500, 500);
    private static final Rect OVERLAP_SMALLER_WINDOW_BOUNDS = new Rect(100, 100, 200, 200);
    private final List<AccessibilityWindowInfo> mWindows = new ArrayList<>();
    private final Context mContext = RuntimeEnvironment.application.getApplicationContext();
    private final SwitchAccessService mSwitchControlService = new SwitchAccessService();
    private final RoboSharedPreferences mSharedPreferences =
            (RoboSharedPreferences) ShadowPreferenceManager.getDefaultSharedPreferences(mContext);
    private final LinearScanTreeBuilder mMockLinearScanTreeBuilder =
            mock(LinearScanTreeBuilder.class);
    private final RowColumnTreeBuilder mMockRowColumnTreeBuilder = mock(RowColumnTreeBuilder.class);
    private final TalkBackOrderNDegreeTreeBuilder mMockTalkBackOrderNDegreeTreeBuilder =
            mock(TalkBackOrderNDegreeTreeBuilder.class);

    private MultiWindowTreeBuilder mMultiWindowTreeBuilderWithRealBuilders;
    private MultiWindowTreeBuilder mMultiWindowTreeBuilderWithMockBuilders;
    private AccessibilityNodeInfo mWindow0Root, mWindow0Node;
    private AccessibilityNodeInfo mWindow1Root, mWindow1Node;
    private ShadowAccessibilityWindowInfo mShadowWindow0, mShadowWindow1;

    @Before
    public void setUp() {
        /* For some reason this value becomes 22 when I allow the manifest to load */
        ReflectionHelpers.setStaticField(Build.VERSION.class, "SDK_INT", 21);
        ShadowAccessibilityNodeInfo.resetObtainedInstances();
        /* Build accessibility node tree */
        mWindows.add(AccessibilityWindowInfo.obtain());
        mWindows.add(AccessibilityWindowInfo.obtain());
        mShadowWindow0 = (ShadowAccessibilityWindowInfo) ShadowExtractor.extract(mWindows.get(0));
        mShadowWindow1 = (ShadowAccessibilityWindowInfo) ShadowExtractor.extract(mWindows.get(1));

        mWindow0Root = AccessibilityNodeInfo.obtain();
        mWindow0Root.setClickable(false);
        mWindow0Root.setFocusable(false);
        mWindow0Node = AccessibilityNodeInfo.obtain();
        mWindow0Node.setClickable(true);
        mWindow0Node.setFocusable(true);
        mWindow0Node.setVisibleToUser(true);
        ((ShadowAccessibilityNodeInfo) ShadowExtractor.extract(mWindow0Root))
                .addChild(mWindow0Node);
        mWindow0Node.setContentDescription(WINDOW_0_ROOT_CONTENT_DESCRIPTION);
        mShadowWindow0.setRoot(mWindow0Root);

        mWindow1Root = AccessibilityNodeInfo.obtain();
        mWindow1Root.setClickable(false);
        mWindow1Root.setFocusable(false);
        mWindow1Node = AccessibilityNodeInfo.obtain();
        mWindow1Node.setClickable(true);
        mWindow1Node.setFocusable(true);
        mWindow1Node.setVisibleToUser(true);
        ((ShadowAccessibilityNodeInfo) ShadowExtractor.extract(mWindow1Root))
                .addChild(mWindow1Node);
        mWindow1Root.setContentDescription(WINDOW_1_ROOT_CONTENT_DESCRIPTION);
        mShadowWindow1.setRoot(mWindow1Root);

        mMultiWindowTreeBuilderWithRealBuilders = new MultiWindowTreeBuilder(mContext,
                new LinearScanTreeBuilder(), new RowColumnTreeBuilder(),
                new TalkBackOrderNDegreeTreeBuilder(mContext));
        mMultiWindowTreeBuilderWithMockBuilders = new MultiWindowTreeBuilder(mContext,
                mMockLinearScanTreeBuilder, mMockRowColumnTreeBuilder,
                mMockTalkBackOrderNDegreeTreeBuilder);
    }

    @After
    public void tearDown() {
        mWindow0Root.recycle();
        mWindow1Root.recycle();
        mWindow0Node.recycle();
        mWindow1Node.recycle();
        assertFalse(ShadowAccessibilityNodeInfo.areThereUnrecycledNodes(true));
    }

    @Test
    public void testListeningForSharedPreferenceChange() {
        assertTrue(mSharedPreferences.hasListener(mMultiWindowTreeBuilderWithRealBuilders));
    }

    @Test
    public void testUnregisterSharedPreferenceChangeListener() {
        mMultiWindowTreeBuilderWithRealBuilders.shutdown();
        assertFalse(mSharedPreferences.hasListener(mMultiWindowTreeBuilderWithRealBuilders));
    }

    @Test
    public void testNullList_shouldReturnGlobalContextMenu() {
        OptionScanNode node = mMultiWindowTreeBuilderWithRealBuilders.buildTreeFromWindowList(null,
                mSwitchControlService);
        assertTrue(node instanceof ContextMenuNode);
    }

    @Test
    public void testSystemWindowFirst_appWindowShouldGetFocusFirst() {
        configureWindowsWithNoOverlap();
        ((ShadowAccessibilityWindowInfo) ShadowExtractor.extract(mWindows.get(0)))
                .setType(AccessibilityWindowInfo.TYPE_SYSTEM);
        ((ShadowAccessibilityWindowInfo) ShadowExtractor.extract(mWindows.get(1)))
                .setType(AccessibilityWindowInfo.TYPE_APPLICATION);
        OptionScanSelectionNode treeRoot =
                (OptionScanSelectionNode) mMultiWindowTreeBuilderWithRealBuilders
                .buildTreeFromWindowList(mWindows, mSwitchControlService);
        AccessibilityNodeActionNode firstNode = (AccessibilityNodeActionNode) treeRoot.getChild(0);
        assertTrue(firstNode.getRectsForNodeHighlight().contains(NO_OVERLAP_WINDOW_1_BOUNDS));
        treeRoot.recycle();
    }

    @Test
    public void testAppWindowFirst_appWindowShouldGetFocusFirst() {
        configureWindowsWithNoOverlap();
        ((ShadowAccessibilityWindowInfo) ShadowExtractor.extract(mWindows.get(0)))
                .setType(AccessibilityWindowInfo.TYPE_APPLICATION);
        ((ShadowAccessibilityWindowInfo) ShadowExtractor.extract(mWindows.get(1)))
                .setType(AccessibilityWindowInfo.TYPE_SYSTEM);
        OptionScanSelectionNode treeRoot =
                (OptionScanSelectionNode) mMultiWindowTreeBuilderWithRealBuilders
                .buildTreeFromWindowList(mWindows, mSwitchControlService);
        AccessibilityNodeActionNode firstNode = (AccessibilityNodeActionNode) treeRoot.getChild(0);
        assertTrue(firstNode.getRectsForNodeHighlight().contains(NO_OVERLAP_WINDOW_0_BOUNDS));
        treeRoot.recycle();
    }

    @Test
    public void testAppWindowFirst_systemWindowShouldGetFocusSecond() {
        configureWindowsWithNoOverlap();
        ((ShadowAccessibilityWindowInfo) ShadowExtractor.extract(mWindows.get(0)))
                .setType(AccessibilityWindowInfo.TYPE_APPLICATION);
        ((ShadowAccessibilityWindowInfo) ShadowExtractor.extract(mWindows.get(1)))
                .setType(AccessibilityWindowInfo.TYPE_SYSTEM);
        OptionScanSelectionNode treeRoot =
                (OptionScanSelectionNode) mMultiWindowTreeBuilderWithRealBuilders
                .buildTreeFromWindowList(mWindows, mSwitchControlService);
        OptionScanSelectionNode secondWindowSelectionNode =
                (OptionScanSelectionNode) treeRoot.getChild(1);
        AccessibilityNodeActionNode secondNode =
                (AccessibilityNodeActionNode) secondWindowSelectionNode.getChild(0);
        assertTrue(secondNode.getRectsForNodeHighlight().contains(NO_OVERLAP_WINDOW_1_BOUNDS));
        treeRoot.recycle();
    }

    @Test
    public void testAppWindowFirst_shouldHaveContextMenuAtEnd() {
        configureWindowsWithNoOverlap();
        ((ShadowAccessibilityWindowInfo) ShadowExtractor.extract(mWindows.get(0)))
                .setType(AccessibilityWindowInfo.TYPE_APPLICATION);
        ((ShadowAccessibilityWindowInfo) ShadowExtractor.extract(mWindows.get(1)))
                .setType(AccessibilityWindowInfo.TYPE_SYSTEM);
        OptionScanSelectionNode treeRoot =
                (OptionScanSelectionNode) mMultiWindowTreeBuilderWithRealBuilders
                .buildTreeFromWindowList(mWindows, mSwitchControlService);
        OptionScanSelectionNode secondWindowSelectionNode =
                (OptionScanSelectionNode) treeRoot.getChild(1);
        assertTrue(secondWindowSelectionNode.getChild(1) instanceof ContextMenuNode);
        treeRoot.recycle();
    }

    public void testImeWindowFirst_imeWindowShouldGetFocusFirst() {
        configureWindowsWithNoOverlap();
        ((ShadowAccessibilityWindowInfo) ShadowExtractor.extract(mWindows.get(0)))
                .setType(AccessibilityWindowInfo.TYPE_INPUT_METHOD);
        ((ShadowAccessibilityWindowInfo) ShadowExtractor.extract(mWindows.get(1)))
                .setType(AccessibilityWindowInfo.TYPE_APPLICATION);
        OptionScanSelectionNode treeRoot =
                (OptionScanSelectionNode) mMultiWindowTreeBuilderWithRealBuilders
                .buildTreeFromWindowList(mWindows, mSwitchControlService);
        AccessibilityNodeActionNode firstNode = (AccessibilityNodeActionNode) treeRoot.getChild(0);
        assertTrue(firstNode.getRectsForNodeHighlight().contains(NO_OVERLAP_WINDOW_1_BOUNDS));
        treeRoot.recycle();
    }

    public void testImeWindowSecond_imeWindowShouldGetFocusFirst() {
        configureWindowsWithNoOverlap();
        ((ShadowAccessibilityWindowInfo) ShadowExtractor.extract(mWindows.get(1)))
                .setType(AccessibilityWindowInfo.TYPE_INPUT_METHOD);
        ((ShadowAccessibilityWindowInfo) ShadowExtractor.extract(mWindows.get(0)))
                .setType(AccessibilityWindowInfo.TYPE_APPLICATION);
        OptionScanSelectionNode treeRoot =
                (OptionScanSelectionNode) mMultiWindowTreeBuilderWithRealBuilders
                .buildTreeFromWindowList(mWindows, mSwitchControlService);
        AccessibilityNodeActionNode firstNode = (AccessibilityNodeActionNode) treeRoot.getChild(0);
        assertTrue(firstNode.getRectsForNodeHighlight().contains(NO_OVERLAP_WINDOW_1_BOUNDS));
        treeRoot.recycle();
    }

    @Test
    public void removeSystemButtons_onBottom() {
        configureWindowsWithNoOverlap();
        Point screenSize = getScreenSize();
        setupOneAppAndOneSystemWindowWithBounds(new Rect(0, screenSize.y - 100, screenSize.x, 100));
        OptionScanSelectionNode treeRoot =
                (OptionScanSelectionNode) mMultiWindowTreeBuilderWithRealBuilders
                .buildTreeFromWindowList(mWindows, mSwitchControlService);

        // System window should be missing
        assertFalse(treeRoot.getRectsForNodeHighlight().contains(NO_OVERLAP_WINDOW_0_BOUNDS));
        treeRoot.recycle();
    }

    @Test
    public void removeSystemButtons_onRight() {
        configureWindowsWithNoOverlap();
        Point screenSize = getScreenSize();
        setupOneAppAndOneSystemWindowWithBounds(new Rect(screenSize.x - 100, 0, 100, screenSize.y));
        OptionScanSelectionNode treeRoot =
                (OptionScanSelectionNode) mMultiWindowTreeBuilderWithRealBuilders
                .buildTreeFromWindowList(mWindows, mSwitchControlService);

        // System window should be missing
        assertFalse(treeRoot.getRectsForNodeHighlight().contains(NO_OVERLAP_WINDOW_0_BOUNDS));
        treeRoot.recycle();
    }

    @Test
    public void removeSystemButtons_butKeepNotifications() {
        configureWindowsWithNoOverlap();
        Point screenSize = getScreenSize();
        setupOneAppAndOneSystemWindowWithBounds(new Rect(0, 0, screenSize.x, 100));
        OptionScanSelectionNode treeRoot =
                (OptionScanSelectionNode) mMultiWindowTreeBuilderWithRealBuilders
                        .buildTreeFromWindowList(mWindows, mSwitchControlService);

        // System window should be present
        assertTrue(treeRoot.getRectsForNodeHighlight().contains(NO_OVERLAP_WINDOW_0_BOUNDS));
        treeRoot.recycle();
    }

    @Test
    public void removeSystemButtons_butKeepDialogsInMiddleOfScreen() {
        configureWindowsWithNoOverlap();
        Point screenSize = getScreenSize();
        setupOneAppAndOneSystemWindowWithBounds(
                new Rect(10, 10, screenSize.x - 20, screenSize.y - 20));
        OptionScanSelectionNode treeRoot =
                (OptionScanSelectionNode) mMultiWindowTreeBuilderWithRealBuilders
                        .buildTreeFromWindowList(mWindows, mSwitchControlService);

        // System window should be present
        assertTrue(treeRoot.getRectsForNodeHighlight().contains(NO_OVERLAP_WINDOW_0_BOUNDS));
        treeRoot.recycle();
    }

    @Test
    public void removeSystemButtons_butKeepFullScreenOverlay() {
        configureWindowsWithNoOverlap();
        Point screenSize = getScreenSize();
        setupOneAppAndOneSystemWindowWithBounds(new Rect(0, 50, screenSize.x, screenSize.y));
        OptionScanSelectionNode treeRoot =
                (OptionScanSelectionNode) mMultiWindowTreeBuilderWithRealBuilders
                        .buildTreeFromWindowList(mWindows, mSwitchControlService);

        // System window should be present
        assertTrue(treeRoot.getRectsForNodeHighlight().contains(NO_OVERLAP_WINDOW_0_BOUNDS));
        treeRoot.recycle();
    }

    @Test
    public void ifPrefIsRowCol_viewsUseRowColumn() {
        setStringPreference(mContext.getString(R.string.pref_scanning_methods_key),
                mContext.getString(R.string.row_col_scanning_key));
        ((ShadowAccessibilityWindowInfo) ShadowExtractor.extract(mWindows.get(0)))
                .setType(AccessibilityWindowInfo.TYPE_APPLICATION);
        mMultiWindowTreeBuilderWithMockBuilders
                .buildTreeFromWindowList(Arrays.asList(mWindows.get(0)), mSwitchControlService);
        verify(mMockRowColumnTreeBuilder, times(1)).buildTreeFromNodeTree(
                (SwitchAccessNodeCompat) anyObject(), (OptionScanNode) anyObject());
        verify(mMockLinearScanTreeBuilder, never()).buildTreeFromNodeTree(
                (SwitchAccessNodeCompat) anyObject(), (OptionScanNode) anyObject());
        verifyNoMoreInteractions(mMockTalkBackOrderNDegreeTreeBuilder);
    }

    @Test
    public void ifPrefIsRowColForImeOnly_viewsUseLinear() {
        setStringPreference(mContext.getString(R.string.pref_scanning_methods_key),
                mContext.getString(R.string.views_linear_ime_row_col_key));
        ((ShadowAccessibilityWindowInfo) ShadowExtractor.extract(mWindows.get(0)))
                .setType(AccessibilityWindowInfo.TYPE_APPLICATION);
        mMultiWindowTreeBuilderWithMockBuilders
                .buildTreeFromWindowList(Arrays.asList(mWindows.get(0)), mSwitchControlService);
        verify(mMockLinearScanTreeBuilder, times(1)).buildTreeFromNodeTree(
                (SwitchAccessNodeCompat) anyObject(), (OptionScanNode) anyObject());
        verify(mMockRowColumnTreeBuilder, never()).buildTreeFromNodeTree(
                (SwitchAccessNodeCompat) anyObject(), (OptionScanNode) anyObject());
        verifyNoMoreInteractions(mMockTalkBackOrderNDegreeTreeBuilder);
    }

    @Test
    public void ifPrefIsRowColForImeOnly_keyboardUsesRowColumn() {
        setStringPreference(mContext.getString(R.string.pref_scanning_methods_key),
                mContext.getString(R.string.views_linear_ime_row_col_key));
        ((ShadowAccessibilityWindowInfo) ShadowExtractor.extract(mWindows.get(0)))
                .setType(AccessibilityWindowInfo.TYPE_INPUT_METHOD);
        mMultiWindowTreeBuilderWithMockBuilders
                .buildTreeFromWindowList(Arrays.asList(mWindows.get(0)), mSwitchControlService);
        verify(mMockRowColumnTreeBuilder, times(1)).buildTreeFromNodeTree(
                (SwitchAccessNodeCompat) anyObject(), (OptionScanNode) anyObject());
        verify(mMockLinearScanTreeBuilder, never()).buildTreeFromNodeTree(
                (SwitchAccessNodeCompat) anyObject(), (OptionScanNode) anyObject());
        verifyNoMoreInteractions(mMockTalkBackOrderNDegreeTreeBuilder);
    }

    @Test
    public void ifPrefIsOption_usesOptionScanning() {
        setStringPreference(mContext.getString(R.string.pref_scanning_methods_key),
                mContext.getString(R.string.option_scanning_key));
        ((ShadowAccessibilityWindowInfo) ShadowExtractor.extract(mWindows.get(0)))
                .setType(AccessibilityWindowInfo.TYPE_INPUT_METHOD);
        mMultiWindowTreeBuilderWithMockBuilders
                .buildTreeFromWindowList(Arrays.asList(mWindows.get(0)), mSwitchControlService);

        verify(mMockTalkBackOrderNDegreeTreeBuilder, times(1))
                .buildContextMenuTree((List<OptionScanActionNode>) anyObject());
        verify(mMockTalkBackOrderNDegreeTreeBuilder, times(1)).buildTreeFromWindowList(
                (List<SwitchAccessWindowInfo>) anyObject(), (OptionScanNode) anyObject());
        verify(mMockLinearScanTreeBuilder, never()).buildTreeFromNodeTree(
                (SwitchAccessNodeCompat) anyObject(), (OptionScanNode) anyObject());
        verify(mMockRowColumnTreeBuilder, never()).buildTreeFromNodeTree(
                (SwitchAccessNodeCompat) anyObject(), (OptionScanNode) anyObject());
    }

    private void setStringPreference(String preferenceKey, String value) {
        PreferenceManager.getDefaultSharedPreferences(mContext).edit()
                .putString(preferenceKey, value).commit();
    }

    /* Set up an app window with default bounds behind a system window with specified bounds */
    private void setupOneAppAndOneSystemWindowWithBounds(Rect rect) {
        mShadowWindow0.setType(AccessibilityWindowInfo.TYPE_SYSTEM);
        mShadowWindow0.setBoundsInScreen(rect);
        mShadowWindow1.setType(AccessibilityWindowInfo.TYPE_APPLICATION);
    }

    private Point getScreenSize() {
        final WindowManager wm = (WindowManager) mContext.getSystemService(Context.WINDOW_SERVICE);
        final Display display = wm.getDefaultDisplay();
        final Point screenSize = new Point();
        display.getSize(screenSize);
        return screenSize;
    }

    private void configureWindowsWithNoOverlap() {
        mWindow0Node.setBoundsInScreen(NO_OVERLAP_WINDOW_0_BOUNDS);
        mShadowWindow0.setBoundsInScreen(NO_OVERLAP_WINDOW_0_BOUNDS);
        mWindow1Node.setBoundsInScreen(NO_OVERLAP_WINDOW_1_BOUNDS);
        mShadowWindow1.setBoundsInScreen(NO_OVERLAP_WINDOW_1_BOUNDS);
    }
}