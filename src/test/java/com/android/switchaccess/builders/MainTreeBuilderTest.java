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

import static org.junit.Assert.*;
import static org.junit.Assert.assertFalse;
import static org.mockito.Matchers.anyObject;
import static org.mockito.Mockito.*;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Point;
import android.graphics.Rect;
import android.os.Build;
import android.preference.PreferenceManager;
import android.view.Display;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;

import com.android.switchaccess.*;
import com.android.switchaccess.test.ShadowAccessibilityNodeInfo;
import com.android.switchaccess.test.ShadowAccessibilityService;
import com.android.switchaccess.treebuilding.LinearScanTreeBuilder;
import com.android.switchaccess.treebuilding.MainTreeBuilder;
import com.android.switchaccess.treebuilding.RowColumnTreeBuilder;
import com.android.switchaccess.treebuilding.TalkBackOrderNDegreeTreeBuilder;
import com.android.talkback.BuildConfig;
import com.android.talkback.R;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import org.robolectric.RobolectricGradleTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.fakes.RoboSharedPreferences;
import org.robolectric.internal.ShadowExtractor;
import org.robolectric.shadows.ShadowPreferenceManager;
import org.robolectric.util.ReflectionHelpers;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Robolectric tests for MainTreeBuilder
 */
@Config(
        constants = BuildConfig.class,
        sdk = 21,
        shadows = {
                ShadowAccessibilityNodeInfo.class,
                ShadowAccessibilityNodeInfo.ShadowAccessibilityAction.class,
                ShadowAccessibilityService.class})
@TargetApi(Build.VERSION_CODES.LOLLIPOP)
@RunWith(RobolectricGradleTestRunner.class)
public class MainTreeBuilderTest {
    private static final CharSequence WINDOW_0_ROOT_CONTENT_DESCRIPTION = "Description 0";
    private static final CharSequence WINDOW_1_ROOT_CONTENT_DESCRIPTION = "Description 1";
    private static final Rect NO_OVERLAP_WINDOW_0_BOUNDS = new Rect(10, 10, 90, 20);
    private static final Rect NO_OVERLAP_WINDOW_1_BOUNDS = new Rect(10, 30, 90, 80);
    private final Context mContext = RuntimeEnvironment.application.getApplicationContext();
    private final LinearScanTreeBuilder mMockLinearScanTreeBuilder =
            mock(LinearScanTreeBuilder.class);
    private final RowColumnTreeBuilder mMockRowColumnTreeBuilder = mock(RowColumnTreeBuilder.class);
    private final TalkBackOrderNDegreeTreeBuilder mMockTalkBackOrderNDegreeTreeBuilder =
            mock(TalkBackOrderNDegreeTreeBuilder.class);

    private MainTreeBuilder mMainTreeBuilderWithRealBuilders;
    private MainTreeBuilder mMainTreeBuilderWithMockBuilders;
    private AccessibilityNodeInfo mWindow0Root, mWindow0Node;
    private AccessibilityNodeInfo mWindow1Root, mWindow1Node;
    private List<SwitchAccessWindowInfo> mWindows = new ArrayList<>();

    @Before
    public void setUp() {
        /* For some reason this value becomes 22 when I allow the manifest to load */
        ReflectionHelpers.setStaticField(Build.VERSION.class, "SDK_INT", 21);
        ShadowAccessibilityNodeInfo.resetObtainedInstances();

        /* Build accessibility node tree */
        mWindow0Root = AccessibilityNodeInfo.obtain();
        mWindow0Root.setClickable(false);
        mWindow0Root.setFocusable(false);
        mWindow0Node = AccessibilityNodeInfo.obtain();
        mWindow0Node.setClickable(true);
        mWindow0Node.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_CLICK);
        mWindow0Node.setFocusable(true);
        mWindow0Node.setVisibleToUser(true);
        ((ShadowAccessibilityNodeInfo) ShadowExtractor.extract(mWindow0Root))
                .addChild(mWindow0Node);
        mWindow0Root.setContentDescription(WINDOW_0_ROOT_CONTENT_DESCRIPTION);
        mWindow0Node.setBoundsInScreen(NO_OVERLAP_WINDOW_0_BOUNDS);

        mWindow1Root = AccessibilityNodeInfo.obtain();
        mWindow1Root.setClickable(false);
        mWindow1Root.setFocusable(false);
        mWindow1Node = AccessibilityNodeInfo.obtain();
        mWindow1Node.setClickable(true);
        mWindow1Node.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_CLICK);
        mWindow1Node.setFocusable(true);
        mWindow1Node.setVisibleToUser(true);
        ((ShadowAccessibilityNodeInfo) ShadowExtractor.extract(mWindow1Root))
                .addChild(mWindow1Node);
        mWindow1Root.setContentDescription(WINDOW_1_ROOT_CONTENT_DESCRIPTION);
        mWindow1Node.setBoundsInScreen(NO_OVERLAP_WINDOW_1_BOUNDS);

        mMainTreeBuilderWithRealBuilders = new MainTreeBuilder(mContext);
        mMainTreeBuilderWithMockBuilders = new MainTreeBuilder(mContext,
                mMockLinearScanTreeBuilder, mMockRowColumnTreeBuilder,
                mMockTalkBackOrderNDegreeTreeBuilder);
        mWindows.add(mock(SwitchAccessWindowInfo.class));
        mWindows.add(mock(SwitchAccessWindowInfo.class));
        when(mWindows.get(0).getRoot())
                .thenAnswer(new Answer<SwitchAccessNodeCompat>() {
                    @Override
                    public SwitchAccessNodeCompat answer(InvocationOnMock invocation) {
                        return new SwitchAccessNodeCompat(AccessibilityNodeInfo
                                .obtain(mWindow0Root));
                    }
                });
        when(mWindows.get(1).getRoot())
                .thenAnswer(new Answer<SwitchAccessNodeCompat>() {
                    @Override
                    public SwitchAccessNodeCompat answer(InvocationOnMock invocation) {
                        return new SwitchAccessNodeCompat(AccessibilityNodeInfo
                                .obtain(mWindow1Root));
                    }
                });
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
    public void testSystemWindowFirst_appWindowShouldGetFocusFirst() {
        configureWindowsWithNoOverlap();
        when(mWindows.get(0).getType()).thenReturn(AccessibilityWindowInfo.TYPE_SYSTEM);
        when(mWindows.get(1).getType()).thenReturn(AccessibilityWindowInfo.TYPE_APPLICATION);
        OptionScanSelectionNode treeRoot = (OptionScanSelectionNode)
                mMainTreeBuilderWithRealBuilders.addWindowListToTree(mWindows, null);
        AccessibilityNodeActionNode firstNode = (AccessibilityNodeActionNode) treeRoot.getChild(0);
        assertTrue(firstNode.getRectsForNodeHighlight().contains(NO_OVERLAP_WINDOW_1_BOUNDS));
        treeRoot.recycle();
    }

    @Test
    public void testAppWindowFirst_appWindowShouldGetFocusFirst() {
        configureWindowsWithNoOverlap();
        when(mWindows.get(0).getType()).thenReturn(AccessibilityWindowInfo.TYPE_APPLICATION);
        when(mWindows.get(1).getType()).thenReturn(AccessibilityWindowInfo.TYPE_SYSTEM);
        OptionScanSelectionNode treeRoot = (OptionScanSelectionNode)
                mMainTreeBuilderWithRealBuilders.addWindowListToTree(mWindows, null);
        AccessibilityNodeActionNode firstNode = (AccessibilityNodeActionNode) treeRoot.getChild(0);
        assertTrue(firstNode.getRectsForNodeHighlight().contains(NO_OVERLAP_WINDOW_0_BOUNDS));
        treeRoot.recycle();
    }

    @Test
    public void testAppWindowFirst_systemWindowShouldGetFocusSecond() {
        configureWindowsWithNoOverlap();
        when(mWindows.get(0).getType()).thenReturn(AccessibilityWindowInfo.TYPE_APPLICATION);
        when(mWindows.get(1).getType()).thenReturn(AccessibilityWindowInfo.TYPE_SYSTEM);
        OptionScanSelectionNode treeRoot = (OptionScanSelectionNode)
                mMainTreeBuilderWithRealBuilders.addWindowListToTree(mWindows, null);
        OptionScanSelectionNode secondWindowSelectionNode =
                (OptionScanSelectionNode) treeRoot.getChild(1);
        AccessibilityNodeActionNode secondNode =
                (AccessibilityNodeActionNode) secondWindowSelectionNode.getChild(0);
        assertTrue(secondNode.getRectsForNodeHighlight().contains(NO_OVERLAP_WINDOW_1_BOUNDS));
        treeRoot.recycle();
    }

    public void testImeWindowFirst_imeWindowShouldGetFocusFirst() {
        configureWindowsWithNoOverlap();
        when(mWindows.get(0).getType()).thenReturn(AccessibilityWindowInfo.TYPE_INPUT_METHOD);
        when(mWindows.get(1).getType()).thenReturn(AccessibilityWindowInfo.TYPE_APPLICATION);
        OptionScanSelectionNode treeRoot = (OptionScanSelectionNode)
                mMainTreeBuilderWithRealBuilders.addWindowListToTree(mWindows, null);
        AccessibilityNodeActionNode firstNode = (AccessibilityNodeActionNode) treeRoot.getChild(0);
        assertTrue(firstNode.getRectsForNodeHighlight().contains(NO_OVERLAP_WINDOW_1_BOUNDS));
        treeRoot.recycle();
    }

    public void testImeWindowSecond_imeWindowShouldGetFocusFirst() {
        configureWindowsWithNoOverlap();
        when(mWindows.get(0).getType()).thenReturn(AccessibilityWindowInfo.TYPE_APPLICATION);
        when(mWindows.get(1).getType()).thenReturn(AccessibilityWindowInfo.TYPE_INPUT_METHOD);
        OptionScanSelectionNode treeRoot = (OptionScanSelectionNode)
                mMainTreeBuilderWithRealBuilders.addWindowListToTree(mWindows, null);
        AccessibilityNodeActionNode firstNode = (AccessibilityNodeActionNode) treeRoot.getChild(0);
        assertTrue(firstNode.getRectsForNodeHighlight().contains(NO_OVERLAP_WINDOW_1_BOUNDS));
        treeRoot.recycle();
    }

    @Test
    public void removeSystemButtons_onBottom() {
        configureWindowsWithNoOverlap();
        Point screenSize = getScreenSize();
        setupOneAppAndOneSystemWindowWithBounds(new Rect(0, screenSize.y - 100, screenSize.x, 100));
        OptionScanSelectionNode treeRoot = (OptionScanSelectionNode)
                mMainTreeBuilderWithRealBuilders.addWindowListToTree(mWindows, null);

        // System window should be missing
        assertFalse(treeRoot.getRectsForNodeHighlight().contains(NO_OVERLAP_WINDOW_0_BOUNDS));
        treeRoot.recycle();
    }

    @Test
    public void removeSystemButtons_onRight() {
        configureWindowsWithNoOverlap();
        Point screenSize = getScreenSize();
        setupOneAppAndOneSystemWindowWithBounds(new Rect(screenSize.x - 100, 0, 100, screenSize.y));
        OptionScanSelectionNode treeRoot = (OptionScanSelectionNode)
                mMainTreeBuilderWithRealBuilders.addWindowListToTree(mWindows, null);

        // System window should be missing
        assertFalse(treeRoot.getRectsForNodeHighlight().contains(NO_OVERLAP_WINDOW_0_BOUNDS));
        treeRoot.recycle();
    }

    @Test
    public void removeSystemButtons_butKeepNotifications() {
        configureWindowsWithNoOverlap();
        Point screenSize = getScreenSize();
        setupOneAppAndOneSystemWindowWithBounds(new Rect(0, 0, screenSize.x, 100));
        OptionScanSelectionNode treeRoot = (OptionScanSelectionNode)
                mMainTreeBuilderWithRealBuilders.addWindowListToTree(mWindows, null);

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
        OptionScanSelectionNode treeRoot = (OptionScanSelectionNode)
                mMainTreeBuilderWithRealBuilders.addWindowListToTree(mWindows, null);

        // System window should be present
        assertTrue(treeRoot.getRectsForNodeHighlight().contains(NO_OVERLAP_WINDOW_0_BOUNDS));
        treeRoot.recycle();
    }

    @Test
    public void removeSystemButtons_butKeepFullScreenOverlay() {
        configureWindowsWithNoOverlap();
        Point screenSize = getScreenSize();
        setupOneAppAndOneSystemWindowWithBounds(new Rect(0, 50, screenSize.x, screenSize.y));
        OptionScanSelectionNode treeRoot = (OptionScanSelectionNode)
                mMainTreeBuilderWithRealBuilders.addWindowListToTree(mWindows, null);

        // System window should be present
        assertTrue(treeRoot.getRectsForNodeHighlight().contains(NO_OVERLAP_WINDOW_0_BOUNDS));
        treeRoot.recycle();
    }

    @Test
    public void ifPrefIsRowCol_viewsUseRowColumn() {
        setStringPreference(mContext.getString(R.string.pref_scanning_methods_key),
                mContext.getString(R.string.row_col_scanning_key));
        when(mWindows.get(0).getType()).thenReturn(AccessibilityWindowInfo.TYPE_APPLICATION);
        mMainTreeBuilderWithMockBuilders
                .addWindowListToTree(Arrays.asList(mWindows.get(0)), null);
        verify(mMockRowColumnTreeBuilder, times(1)).addViewHierarchyToTree(
                (SwitchAccessNodeCompat) anyObject(), (OptionScanNode) anyObject());
        verify(mMockLinearScanTreeBuilder, never()).addViewHierarchyToTree(
                (SwitchAccessNodeCompat) anyObject(), (OptionScanNode) anyObject());
        verifyNoMoreInteractions(mMockTalkBackOrderNDegreeTreeBuilder);
    }

    @Test
    public void ifPrefIsRowColForImeOnly_viewsUseLinear() {
        setStringPreference(mContext.getString(R.string.pref_scanning_methods_key),
                mContext.getString(R.string.views_linear_ime_row_col_key));
        when(mWindows.get(0).getType()).thenReturn(AccessibilityWindowInfo.TYPE_APPLICATION);
        mMainTreeBuilderWithMockBuilders
                .addWindowListToTree(Arrays.asList(mWindows.get(0)), null);
        verify(mMockLinearScanTreeBuilder, times(1)).addViewHierarchyToTree(
                (SwitchAccessNodeCompat) anyObject(), (OptionScanNode) anyObject());
        verify(mMockRowColumnTreeBuilder, never()).addViewHierarchyToTree(
                (SwitchAccessNodeCompat) anyObject(), (OptionScanNode) anyObject());
        verifyNoMoreInteractions(mMockTalkBackOrderNDegreeTreeBuilder);
    }

    @Test
    public void ifPrefIsRowColForImeOnly_keyboardUsesRowColumn() {
        setStringPreference(mContext.getString(R.string.pref_scanning_methods_key),
                mContext.getString(R.string.views_linear_ime_row_col_key));
        when(mWindows.get(0).getType()).thenReturn(AccessibilityWindowInfo.TYPE_INPUT_METHOD);
        mMainTreeBuilderWithMockBuilders
                .addWindowListToTree(Arrays.asList(mWindows.get(0)), null);
        verify(mMockRowColumnTreeBuilder, times(1)).addViewHierarchyToTree(
                (SwitchAccessNodeCompat) anyObject(), (OptionScanNode) anyObject());
        verify(mMockLinearScanTreeBuilder, never()).addViewHierarchyToTree(
                (SwitchAccessNodeCompat) anyObject(), (OptionScanNode) anyObject());
        verifyNoMoreInteractions(mMockTalkBackOrderNDegreeTreeBuilder);
    }

    @Test
    public void ifPrefIsOption_usesOptionScanning() {
        setStringPreference(mContext.getString(R.string.pref_scanning_methods_key),
                mContext.getString(R.string.option_scanning_key));
        when(mWindows.get(0).getType()).thenReturn(AccessibilityWindowInfo.TYPE_INPUT_METHOD);
        mMainTreeBuilderWithMockBuilders
                .addWindowListToTree(Arrays.asList(mWindows.get(0)), null);

        verify(mMockTalkBackOrderNDegreeTreeBuilder, times(1)).addWindowListToTree(
                (List<SwitchAccessWindowInfo>) anyObject(), (OptionScanNode) anyObject());
        verify(mMockLinearScanTreeBuilder, never()).addViewHierarchyToTree(
                (SwitchAccessNodeCompat) anyObject(), (OptionScanNode) anyObject());
        verify(mMockRowColumnTreeBuilder, never()).addViewHierarchyToTree(
                (SwitchAccessNodeCompat) anyObject(), (OptionScanNode) anyObject());
    }

    @Test
    public void ifPrefIsRowCol_contextMenuUsesLinear() {
        setStringPreference(mContext.getString(R.string.pref_scanning_methods_key),
                mContext.getString(R.string.views_linear_ime_row_col_key));
        List<ContextMenuItem> actionList = new ArrayList<>();
        mMainTreeBuilderWithMockBuilders.buildContextMenu(actionList);
        verify(mMockLinearScanTreeBuilder, times(1)).buildContextMenu(actionList);
        verify(mMockTalkBackOrderNDegreeTreeBuilder, never()).buildContextMenu(
                (List<ContextMenuItem>) anyObject());
        verify(mMockRowColumnTreeBuilder, never()).buildContextMenu(
                (List<ContextMenuItem>) anyObject());
    }

    @Test
    public void ifPrefIsOption_contextMenuUsesOption() {
        setStringPreference(mContext.getString(R.string.pref_scanning_methods_key),
                mContext.getString(R.string.option_scanning_key));
        List<ContextMenuItem> actionList = new ArrayList<>();
        mMainTreeBuilderWithMockBuilders.buildContextMenu(actionList);
        verify(mMockTalkBackOrderNDegreeTreeBuilder, times(1)).buildContextMenu(actionList);
        verify(mMockLinearScanTreeBuilder, never()).buildContextMenu(
                (List<ContextMenuItem>) anyObject());
        verify(mMockRowColumnTreeBuilder, never()).buildContextMenu(
                (List<ContextMenuItem>) anyObject());
    }

    private void setStringPreference(String preferenceKey, String value) {
        PreferenceManager.getDefaultSharedPreferences(mContext).edit()
                .putString(preferenceKey, value).commit();
    }

    /* Set up an app window with default bounds behind a system window with specified bounds */
    private void setupOneAppAndOneSystemWindowWithBounds(Rect rect) {
        setBoundsInScreen(mWindows.get(0), rect);
        when(mWindows.get(0).getType()).thenReturn(AccessibilityWindowInfo.TYPE_SYSTEM);
        when(mWindows.get(1).getType()).thenReturn(AccessibilityWindowInfo.TYPE_APPLICATION);
    }

    private Point getScreenSize() {
        final WindowManager wm = (WindowManager) mContext.getSystemService(Context.WINDOW_SERVICE);
        final Display display = wm.getDefaultDisplay();
        final Point screenSize = new Point();
        display.getSize(screenSize);
        return screenSize;
    }

    private void configureWindowsWithNoOverlap() {
        setBoundsInScreen(mWindows.get(0), NO_OVERLAP_WINDOW_0_BOUNDS);
        setBoundsInScreen(mWindows.get(1), NO_OVERLAP_WINDOW_1_BOUNDS);
    }

    private void setBoundsInScreen(SwitchAccessWindowInfo window, Rect bounds) {
        final Rect finalBounds = new Rect();
        finalBounds.set(bounds);
        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocationOnMock) {
                Rect bounds = (Rect) invocationOnMock.getArguments()[0];
                bounds.set(finalBounds);
                return null;
            }
        }).when(window).getBoundsInScreen((Rect) anyObject());
    }
}