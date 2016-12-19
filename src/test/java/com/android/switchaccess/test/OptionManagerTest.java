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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.android.switchaccess.*;
import com.android.talkback.BuildConfig;
import com.android.talkback.R;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import android.view.View;
import android.os.Build;
import android.view.accessibility.AccessibilityNodeInfo;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.widget.LinearLayout;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.MockitoAnnotations;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricGradleTestRunner;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import org.robolectric.annotation.Config;
import org.robolectric.fakes.RoboSharedPreferences;
import org.robolectric.internal.ShadowExtractor;
import org.robolectric.shadows.ShadowHandler;
import org.robolectric.shadows.ShadowPreferenceManager;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;

/**
 * Tests for OptionManager
 */
@Config(
        constants = BuildConfig.class,
        sdk = 21,
        shadows = {
            ShadowAccessibilityNodeInfo.class,
            ShadowAccessibilityNodeInfo.ShadowAccessibilityAction.class})
@TargetApi(Build.VERSION_CODES.LOLLIPOP)
@RunWith(RobolectricGradleTestRunner.class)
public class OptionManagerTest {
    private static final Rect NODE_BOUNDS_1 = new Rect(10, 10, 90, 20);
    private static final Rect NODE_BOUNDS_2 = new Rect(110, 110, 190, 120);
    private static final Rect MENU_BUTTON_BOUNDS = new Rect(150, 10, 250, 60);
    private static final int DEFAULT_HIGHLIGHT_COLOR = 0x4caf50;
    private static final int ORANGE_500_COLOR = 0xff9800;
    private static final int BLUE_500_COLOR = 0x2196f3;

    private final OverlayController mOverlayController = mock(OverlayController.class);

    private final OptionManager.OptionManagerListener mMockListener =
            mock(OptionManager.OptionManagerListener.class);

    private final Context mContext = RuntimeEnvironment.application.getApplicationContext();
    private final RoboSharedPreferences mSharedPreferences =
            (RoboSharedPreferences) ShadowPreferenceManager.getDefaultSharedPreferences(mContext);
    private OptionManager mOptionManager;
    private SwitchAccessNodeCompat mCompat1, mCompat2;
    private AccessibilityNodeActionNode mActionNode1, mActionNode2;
    private OptionScanSelectionNode mSelectionNode;
    ShadowAccessibilityNodeInfo mShadowInfo1, mShadowInfo2;

    @Captor
    ArgumentCaptor<Collection<Rect>> mHighlightCaptor;
    @Captor
    ArgumentCaptor<Paint> mPaintCaptor;

    @Before
    public void setUp() {
        ShadowAccessibilityNodeInfo.resetObtainedInstances();
        mCompat1 = new SwitchAccessNodeCompat(AccessibilityNodeInfo.obtain());
        mShadowInfo1 = (ShadowAccessibilityNodeInfo)
                ShadowExtractor.extract((AccessibilityNodeInfo) mCompat1.getInfo());
        mCompat2 = new SwitchAccessNodeCompat(AccessibilityNodeInfo.obtain());
        mShadowInfo2 = (ShadowAccessibilityNodeInfo)
                ShadowExtractor.extract((AccessibilityNodeInfo) mCompat2.getInfo());
        mSharedPreferences.edit().clear().commit();
        MockitoAnnotations.initMocks(this);
        mCompat1.setBoundsInScreen(NODE_BOUNDS_1);
        mCompat2.setBoundsInScreen(NODE_BOUNDS_2);
        mActionNode1 = new AccessibilityNodeActionNode(mCompat1,
                new AccessibilityNodeInfoCompat.AccessibilityActionCompat(
                        AccessibilityNodeInfoCompat.ACTION_CLICK, "label1"));
        mActionNode2 = new AccessibilityNodeActionNode(mCompat2,
                new AccessibilityNodeInfoCompat.AccessibilityActionCompat(
                        AccessibilityNodeInfoCompat.ACTION_CLICK, "label2"));
        mSelectionNode = new OptionScanSelectionNode(mActionNode1, mActionNode2);

        when(mOverlayController.getContext()).thenReturn(mContext);
        mOptionManager = new OptionManager(mOverlayController);
    }

    @After
    public void tearDown() {
        if (mSelectionNode != null) {
            mSelectionNode.recycle();
        }
        mCompat1.recycle();
        mCompat2.recycle();
        try {
            assertFalse(ShadowAccessibilityNodeInfo.areThereUnrecycledNodes(true));
        } finally {
            ShadowAccessibilityNodeInfo.resetObtainedInstances();
        }
    }

    @Test
    public void testListeningForSharedPreferenceChange() {
        assertTrue(mSharedPreferences.hasListener(mOptionManager));
    }

    @Test
    public void testUnregisterSharedPreferenceChangeListener() {
        mOptionManager.shutdown();
        assertFalse(mSharedPreferences.hasListener(mOptionManager));
    }

    @Test
    public void testNewTreeAfterShutdown_shouldNotCrash() {
        mOptionManager.clearFocusIfNewTree(mSelectionNode);
        mOptionManager.shutdown();
        mOptionManager.clearFocusIfNewTree(null);
        /* Everything should be recycled at this point */
        mSelectionNode = null;
    }

    @Test
    public void testClearFocusIfNewTree_shouldIgnoreIdenticalTrees() {
        mOptionManager.clearFocusIfNewTree(null);
        verify(mOverlayController, times(0)).clearOverlay();
        mOptionManager.clearFocusIfNewTree(mActionNode1);
        verify(mOverlayController, times(1)).clearOverlay();
        mOptionManager.clearFocusIfNewTree(mActionNode1);
        verify(mOverlayController, times(1)).clearOverlay();
        mOptionManager.clearFocusIfNewTree(mActionNode2);
        verify(mOverlayController, times(2)).clearOverlay();
        mOptionManager.clearFocusIfNewTree(null);
        verify(mOverlayController, times(3)).clearOverlay();
        mSelectionNode = null;
    }

    @Test
    public void testClearFocusIfNewTree_shouldClearFocusOnNewTree() {
        mOptionManager.addOptionManagerListener(mMockListener);
        mOptionManager.clearFocusIfNewTree(mSelectionNode);
        verify(mMockListener, times(1)).onOptionManagerClearedFocus();
    }

    @Test
    public void testOnlyOneActionNode_shouldDoNothingBeforeSelection() {
        mOptionManager.clearFocusIfNewTree(mActionNode1);
        assertEquals(0, mShadowInfo1.getPerformedActions().size());
    }

    @Test
    public void testOnlyOneActionNode_shouldPerformActionOnFirstSelect() {
        mOptionManager.clearFocusIfNewTree(mActionNode1);
        mOptionManager.selectOption(0);
        assertEquals(1, mShadowInfo1.getPerformedActions().size());
    }

    @Test
    public void testOnlyOneActionNode_shouldClearFocusAndOverlayWhenActionPerformed() {
        mOptionManager.addOptionManagerListener(mMockListener);
        mOptionManager.clearFocusIfNewTree(mActionNode1);
        mOptionManager.selectOption(0);
        verify(mMockListener, times(2)).onOptionManagerClearedFocus();
        verify(mOverlayController, times(2)).clearOverlay();
    }

    @Test
    public void testNegativeSelection_shouldBeIgnored() {
        mOptionManager.clearFocusIfNewTree(mActionNode1);
        mOptionManager.selectOption(-1);
        assertEquals(0, mShadowInfo1.getPerformedActions().size());
    }

    @Test
    public void testSelectionNodeWithSettingsCleared_shouldHighlightWithDefault() {
        mSharedPreferences.edit().clear().commit();
        mOptionManager = new OptionManager(mOverlayController);
        mOptionManager.clearFocusIfNewTree(mSelectionNode);
        mOptionManager.selectOption(0);
        verify(mOverlayController, times(2))
                .highlightPerimeterOfRects(mHighlightCaptor.capture(), mPaintCaptor.capture());
        /* One paint should be transparent; the other should be the default green */
        List<Collection<Rect>> capturedHighlights = mHighlightCaptor.getAllValues();
        List<Paint> capturedPaints = mPaintCaptor.getAllValues();
        int defaultIndex = 0;
        int transparentIndex = 1;
        if (capturedPaints.get(0).getColor() == Color.TRANSPARENT) {
            transparentIndex = 0;
            defaultIndex = 1;
        }
        assertEquals(Color.TRANSPARENT, capturedPaints.get(transparentIndex).getColor());
        assertEquals(DEFAULT_HIGHLIGHT_COLOR, capturedPaints.get(defaultIndex).getColor());
        assertEquals(0xff, capturedPaints.get(defaultIndex).getAlpha());
        DisplayMetrics dm = mContext.getResources().getDisplayMetrics();
        float expectedStrokeWidth = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 8, dm);
        assertEquals(expectedStrokeWidth, capturedPaints.get(defaultIndex).getStrokeWidth(), 0.01);
        assertTrue(capturedHighlights.get(defaultIndex).contains(NODE_BOUNDS_1));
        assertTrue(capturedHighlights.get(transparentIndex).contains(NODE_BOUNDS_2));
    }

    @Test
    public void testSelectionNodeWithoutOptionScanning_shouldHighlightOneOption() {
        mSharedPreferences.edit()
                .putString(mContext.getString(R.string.pref_highlight_0_color_key),
                        mContext.getString(R.string.material_orange_500))
                .putString(mContext.getString(R.string.pref_highlight_0_weight_key),
                        mContext.getString(R.string.thickness_4_dp))
                .putString(mContext.getString(R.string.pref_highlight_1_color_key),
                        mContext.getString(R.string.material_blue_500))
                .putString(mContext.getString(R.string.pref_highlight_1_weight_key),
                        mContext.getString(R.string.thickness_4_dp))
                .commit();
        mOptionManager.onSharedPreferenceChanged(mSharedPreferences, null);
        mOptionManager.clearFocusIfNewTree(mSelectionNode);
        mOptionManager.selectOption(0);
        verify(mOverlayController, times(2))
                .highlightPerimeterOfRects(mHighlightCaptor.capture(), mPaintCaptor.capture());
        /* One paint should be transparent; the other should be orange */
        List<Collection<Rect>> capturedHighlights = mHighlightCaptor.getAllValues();
        List<Paint> capturedPaints = mPaintCaptor.getAllValues();
        int highlightIndex = 0;
        int transparentIndex = 1;
        if (capturedPaints.get(0).getAlpha() == 0) {
            transparentIndex = 0;
            highlightIndex = 1;
        }
        assertEquals(Color.TRANSPARENT, capturedPaints.get(transparentIndex).getColor());
        assertEquals(ORANGE_500_COLOR, capturedPaints.get(highlightIndex).getColor());
        assertEquals(0xff, capturedPaints.get(highlightIndex).getAlpha());
        DisplayMetrics dm = mContext.getResources().getDisplayMetrics();
        float expectedStrokeWidth = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 4, dm);
        assertEquals(expectedStrokeWidth, capturedPaints.get(highlightIndex).getStrokeWidth(),
                0.01);
        assertTrue(capturedHighlights.get(highlightIndex).contains(NODE_BOUNDS_1));
        assertTrue(capturedHighlights.get(transparentIndex).contains(NODE_BOUNDS_2));
    }

    @Test
    public void testSelectionNodeWithOptionScanning_shouldHighlightTwoOptions() {
        mSharedPreferences.edit()
                .putString(mContext.getString(R.string.pref_highlight_0_color_key),
                        mContext.getString(R.string.material_orange_500))
                .putString(mContext.getString(R.string.pref_highlight_0_weight_key),
                        mContext.getString(R.string.thickness_4_dp))
                .putString(mContext.getString(R.string.pref_highlight_1_color_key),
                        mContext.getString(R.string.material_blue_500))
                .putString(mContext.getString(R.string.pref_highlight_1_weight_key),
                        mContext.getString(R.string.thickness_4_dp))
                .putString(mContext.getString(R.string.pref_scanning_methods_key),
                        mContext.getString(R.string.option_scanning_key))
                .commit();
        mOptionManager.onSharedPreferenceChanged(mSharedPreferences, null);
        mOptionManager.clearFocusIfNewTree(mSelectionNode);
        mOptionManager.selectOption(0);
        verify(mOverlayController, times(2))
                .highlightPerimeterOfRects(mHighlightCaptor.capture(), mPaintCaptor.capture());
        /* One paint should be blue; the other should be orange */
        List<Collection<Rect>> capturedHighlights = mHighlightCaptor.getAllValues();
        List<Paint> capturedPaints = mPaintCaptor.getAllValues();
        int orangeIndex = 0;
        int blueIndex = 1;
        if (capturedPaints.get(0).getColor() == BLUE_500_COLOR) {
            blueIndex = 0;
            orangeIndex = 1;
        }
        assertEquals(BLUE_500_COLOR, capturedPaints.get(blueIndex).getColor());
        assertEquals(ORANGE_500_COLOR, capturedPaints.get(orangeIndex).getColor());
        assertEquals(0xff, capturedPaints.get(orangeIndex).getAlpha());
        DisplayMetrics dm = mContext.getResources().getDisplayMetrics();
        float expectedStrokeWidth = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 4, dm);
        assertEquals(expectedStrokeWidth, capturedPaints.get(orangeIndex).getStrokeWidth(),
                0.01);
        assertTrue(capturedHighlights.get(orangeIndex).contains(NODE_BOUNDS_1));
        assertTrue(capturedHighlights.get(blueIndex).contains(NODE_BOUNDS_2));
    }

    @Test
    public void testMoveToParent_nullTree_shouldNotCrash() {
        mOptionManager.clearFocusIfNewTree(null);
        mOptionManager.moveToParent(false);
        mOptionManager.moveToParent(true);
    }

    @Test
    public void testOptionScanningEnabled_highlightsMenuButton() {
        mSharedPreferences.edit()
                .putString(mContext.getString(R.string.pref_scanning_methods_key),
                        mContext.getString(R.string.option_scanning_key))
                .commit();
        mOptionManager.onSharedPreferenceChanged(mSharedPreferences, null);
        when(mOverlayController.getMenuButtonLocation()).thenReturn(MENU_BUTTON_BOUNDS);

        /* add a context menu with two items to the tree */
        CharSequence globalActionLabel0 = "global action label 0";
        CharSequence globalActionLabel1 = "global action label 1";
        GlobalActionNode globalNode0 = new GlobalActionNode(0, null, globalActionLabel0);
        GlobalActionNode globalNode1 = new GlobalActionNode(1, null, globalActionLabel1);
        ContextMenuNode contextMenu = new ContextMenuNode(globalNode0, globalNode1);
        mSelectionNode = new OptionScanSelectionNode(mSelectionNode, contextMenu);

        mOptionManager.clearFocusIfNewTree(mSelectionNode);
        mOptionManager.selectOption(0);
        verify(mOverlayController, times(1)).drawMenuButton();

        ShadowHandler.runMainLooperToEndOfTasks();

        verify(mOverlayController, times(2)).highlightPerimeterOfRects(
                mHighlightCaptor.capture(), mPaintCaptor.capture());

        List<Collection<Rect>> capturedHighlights = mHighlightCaptor.getAllValues();
        assertTrue(capturedHighlights.get(0).contains(NODE_BOUNDS_1));
        assertTrue(capturedHighlights.get(0).contains(NODE_BOUNDS_2));
        assertTrue(capturedHighlights.get(1).contains(MENU_BUTTON_BOUNDS));
    }

    @Test
    public void testOptionScanningDisabled_NoMenuButtonDrawn() {
        /* add a context menu with two items to the tree */
        CharSequence globalActionLabel0 = "global action label 0";
        CharSequence globalActionLabel1 = "global action label 1";
        GlobalActionNode globalNode0 = new GlobalActionNode(0, null, globalActionLabel0);
        GlobalActionNode globalNode1 = new GlobalActionNode(1, null, globalActionLabel1);
        ContextMenuNode contextMenu = new ContextMenuNode(globalNode0, globalNode1);
        mSelectionNode = new OptionScanSelectionNode(mSelectionNode, contextMenu);

        mOptionManager.clearFocusIfNewTree(mSelectionNode);
        mOptionManager.selectOption(0);
        verify(mOverlayController, times(0)).drawMenuButton();
    }

    @Test
    public void testMoveToParent_hasParent_shouldRefocusOnParent() {
        OptionScanSelectionNode mockSelectionNode = mock(OptionScanSelectionNode.class);
        mSelectionNode.setParent(mockSelectionNode);
        mOptionManager.clearFocusIfNewTree(mSelectionNode);
        mOptionManager.selectOption(0);
        mOptionManager.moveToParent(false);
        verify(mockSelectionNode, times(1)).performAction();
    }

    @Test
    public void testMoveToParent_nowhereToGoNoWrap_shouldClearFocus() {
        mOptionManager.clearFocusIfNewTree(mSelectionNode);
        mOptionManager.selectOption(0);
        mOptionManager.moveToParent(false);
        /* Confirm that we're on null selection by moving to a child */
        mOptionManager.selectOption(0);
        assertEquals(0, mShadowInfo1.getPerformedActions().size());
        mOptionManager.selectOption(0);
        assertEquals(1, mShadowInfo1.getPerformedActions().size());
    }

    @Test
    public void testMoveToParent_nowhereToGoWithWrap_shouldClearFocus() {
        mOptionManager.clearFocusIfNewTree(mSelectionNode);
        mOptionManager.selectOption(0);
        mOptionManager.moveToParent(true);
        /* Confirm that we're on null selection by moving to a child */
        mOptionManager.selectOption(0);
        assertEquals(0, mShadowInfo1.getPerformedActions().size());
        mOptionManager.selectOption(0);
        assertEquals(1, mShadowInfo1.getPerformedActions().size());
    }

    @Test
    public void testMoveToParent_withWrap_shouldCleaFocus() {
        OptionScanSelectionNode topSelectionNode =
                new OptionScanSelectionNode(mActionNode1, mSelectionNode);
        mOptionManager.clearFocusIfNewTree(topSelectionNode);
        mOptionManager.selectOption(0);
        mOptionManager.moveToParent(true);
        /* Confirm that we're on null selection by moving to a child */
        mOptionManager.selectOption(0);
        assertEquals(0, mShadowInfo1.getPerformedActions().size());
        mOptionManager.selectOption(0);
        assertEquals(1, mShadowInfo1.getPerformedActions().size());
    }

    @Test
    public void testMoveToParent_fromNull_shouldWrap() {
        OptionScanSelectionNode topSelectionNode =
                new OptionScanSelectionNode(mActionNode1, mSelectionNode);
        mOptionManager.clearFocusIfNewTree(topSelectionNode);
        mOptionManager.moveToParent(true);
        /* Confirm that we're on mSelectionNode by moving to a child */
        mOptionManager.selectOption(0);
        assertEquals(1, mShadowInfo1.getPerformedActions().size());
    }

    @Test
    public void testLongClickWithOneOptionThatSucceeds_shouldLongClickAndClearFocus() {
        mOptionManager.clearFocusIfNewTree(mSelectionNode);
        mOptionManager.selectOption(0);
        mOptionManager.performLongClick();
        List<Integer> info1Actions = mShadowInfo1.getPerformedActions();
        assertEquals(1, info1Actions.size());
        assertEquals(new Integer(AccessibilityNodeInfoCompat.ACTION_LONG_CLICK),
                info1Actions.get(0));
        verify(mOverlayController, times(3)).clearOverlay();
    }

    @Test
    public void testLongClickWithTwoOptions_shouldDoNothing() {
        OptionScanSelectionNode superParentNode = new OptionScanSelectionNode(mSelectionNode,
                mSelectionNode);
        mOptionManager.clearFocusIfNewTree(superParentNode);
        mOptionManager.selectOption(0);
        mOptionManager.performLongClick();
        assertEquals(0, mShadowInfo1.getPerformedActions().size());
        assertEquals(0, mShadowInfo2.getPerformedActions().size());
    }

    @Test
    public void testScrollWithOneOptionThatSucceeds_shouldScrollAndClearFocus() {
        mCompat1.setScrollable(true);
        mActionNode1.recycle();
        mActionNode1 = new AccessibilityNodeActionNode(mCompat1,
                new AccessibilityNodeInfoCompat.AccessibilityActionCompat(
                        AccessibilityNodeInfoCompat.ACTION_CLICK, "label1"));
        mSelectionNode = new OptionScanSelectionNode(mActionNode1, mActionNode2);
        mOptionManager.clearFocusIfNewTree(mSelectionNode);
        mOptionManager.selectOption(0);
        mOptionManager.performScrollAction(AccessibilityNodeInfoCompat.ACTION_SCROLL_FORWARD);
        List<Integer> info1Actions = mShadowInfo1.getPerformedActions();
        assertEquals(1, info1Actions.size());
        assertEquals(new Integer(AccessibilityNodeInfoCompat.ACTION_SCROLL_FORWARD),
                info1Actions.get(0));
        verify(mOverlayController, times(3)).clearOverlay();
    }

    @Test
    public void testScrollWithParent_shouldScrollAndClearFocus() {
        AccessibilityNodeInfoCompat parent = AccessibilityNodeInfoCompat.obtain();
        ShadowAccessibilityNodeInfo shadowParent =
                (ShadowAccessibilityNodeInfo) ShadowExtractor.extract(parent.getInfo());
        shadowParent.addChild((AccessibilityNodeInfo)  mCompat1.getInfo());
        parent.setScrollable(true);
        mActionNode1.recycle();
        mActionNode1 = new AccessibilityNodeActionNode(mCompat1,
                new AccessibilityNodeInfoCompat.AccessibilityActionCompat(
                        AccessibilityNodeInfoCompat.ACTION_CLICK, "label1"));
        mSelectionNode = new OptionScanSelectionNode(mActionNode1, mActionNode2);
        mOptionManager.clearFocusIfNewTree(mSelectionNode);
        mOptionManager.selectOption(0);
        mOptionManager.performScrollAction(AccessibilityNodeInfoCompat.ACTION_SCROLL_FORWARD);
        assertEquals(0, mShadowInfo1.getPerformedActions().size());
        List<Integer> parentActions = shadowParent.getPerformedActions();
        assertEquals(1, parentActions.size());
        assertEquals(new Integer(AccessibilityNodeInfoCompat.ACTION_SCROLL_FORWARD),
                parentActions.get(0));
        verify(mOverlayController, times(3)).clearOverlay();
        parent.recycle();
    }

    /**
     * This test isn't, strictly speaking, a test of OptionManager. Instead it verifies that the
     * xml arrays for highligh color are all the same length. The option manager assumes they
     * are, so verifying this condition reduces the chance of it crashing later on if we add
     * a new preference without adding a default.
     */
    @Test
    public void testHighlightArraysHaveConsistentLength() {
        String[] colorPrefs = mContext.getResources()
                .getStringArray(R.array.switch_access_highlight_color_pref_keys);
        String[] colorDefaults = mContext.getResources()
                .getStringArray(R.array.switch_access_highlight_color_defaults);
        assertEquals(colorPrefs.length, colorDefaults.length);
    }

    @Test
    public void testHighlightStylesForOptionScanning_defaultsAreDifferent() {
        mOptionManager.onSharedPreferenceChanged(mSharedPreferences, null);
        mOptionManager.clearFocusIfNewTree(mSelectionNode);
        mOptionManager.selectOption(0);
        verify(mOverlayController, times(2))
                .highlightPerimeterOfRects(mHighlightCaptor.capture(), mPaintCaptor.capture());
        /* The two paint colors should be different */
        List<Paint> capturedPaints = mPaintCaptor.getAllValues();
        assertFalse(capturedPaints.get(0).getColor() == capturedPaints.get(1).getColor());
    }

    @Test
    public void testStartScanningWithListener_shouldCallScanStart() {
        OptionManager.ScanListener mockListener = mock(OptionManager.ScanListener.class);
        mOptionManager.setScanListener(mockListener);
        mOptionManager.clearFocusIfNewTree(mSelectionNode);
        mOptionManager.selectOption(0);
        verify(mockListener, times(1)).onScanStart();
        verify(mockListener, times(1)).onScanFocusChanged();
        verifyNoMoreInteractions(mockListener);
    }

    @Test
    public void scanningReachesClearFocusNode_shouldCallNoSelection() {
        OptionManager.ScanListener mockListener = mock(OptionManager.ScanListener.class);
        mOptionManager.setScanListener(mockListener);
        mActionNode2.recycle();
        mSelectionNode = new OptionScanSelectionNode(mActionNode1, new ClearFocusNode());
        mOptionManager.clearFocusIfNewTree(mSelectionNode);
        mOptionManager.selectOption(0);
        mOptionManager.selectOption(1);
        verify(mockListener, times(1)).onScanStart();
        verify(mockListener, times(1)).onScanFocusChanged();
        verify(mockListener, times(1)).onScanCompletedWithNoSelection();
        verifyNoMoreInteractions(mockListener);
    }

    @Test
    public void userAbortsScanWithExtraSwitch_shouldCallNoSelection() {
        OptionManager.ScanListener mockListener = mock(OptionManager.ScanListener.class);
        mOptionManager.setScanListener(mockListener);
        mOptionManager.clearFocusIfNewTree(mSelectionNode);
        mOptionManager.selectOption(0);
        mOptionManager.selectOption(2);
        verify(mockListener, times(1)).onScanStart();
        verify(mockListener, times(1)).onScanFocusChanged();
        verify(mockListener, times(1)).onScanCompletedWithNoSelection();
        verifyNoMoreInteractions(mockListener);
    }

    @Test
    public void testSelectingWithListener_shouldCallSelected() {
        OptionManager.ScanListener mockListener = mock(OptionManager.ScanListener.class);
        mOptionManager.setScanListener(mockListener);
        mOptionManager.clearFocusIfNewTree(mSelectionNode);
        mOptionManager.selectOption(0);
        mOptionManager.selectOption(0);
        verify(mockListener, times(1)).onScanStart();
        verify(mockListener, times(1)).onScanFocusChanged();
        verify(mockListener, times(1)).onScanSelection();
        verifyNoMoreInteractions(mockListener);
    }

    @Test
    public void testAutoStartScan_shouldAutomaticallySelectFirstItem() {
        mSharedPreferences.edit()
                .putBoolean(mContext.getString(R.string.switch_access_auto_start_scan_key), true)
                .commit();
        mOptionManager.onSharedPreferenceChanged(mSharedPreferences, null);
        mOptionManager.clearFocusIfNewTree(mActionNode1);
        assertEquals(1, mShadowInfo1.getPerformedActions().size());
    }

    @Test
    public void testAutoStartScan_shouldCallListener() {
        mOptionManager.addOptionManagerListener(mMockListener);
        mSharedPreferences.edit()
                .putBoolean(mContext.getString(R.string.switch_access_auto_start_scan_key), true)
                .commit();
        mOptionManager.onSharedPreferenceChanged(mSharedPreferences, null);
        mOptionManager.clearFocusIfNewTree(mActionNode1);
        verify(mMockListener, times(1)).onOptionManagerStartedAutoScan();
    }
}
