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

import com.android.talkback.BuildConfig;
import com.android.talkback.R;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.Build;
import android.os.Handler;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import com.android.switchaccess.ClearFocusNode;
import com.android.switchaccess.ContextMenuItem;
import com.android.switchaccess.ContextMenuNode;
import com.android.switchaccess.OverlayController;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.MockitoAnnotations;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricGradleTestRunner;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.annotation.RealObject;
import org.robolectric.internal.ShadowExtractor;
import org.robolectric.shadows.ShadowHandler;
import org.robolectric.shadows.ShadowLinearLayout;
import org.robolectric.shadows.ShadowPreferenceManager;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for ContextMenuNode
 */
@Config(
        constants = BuildConfig.class,
        sdk = 21,
        shadows = {
                ContextMenuNodeTest.HandlerThatDelaysPostedRunnables.class,
                ContextMenuNodeTest.ShadowLayoutInflater.class})
@TargetApi(Build.VERSION_CODES.LOLLIPOP)
@RunWith(RobolectricGradleTestRunner.class)
public class ContextMenuNodeTest {

    private static final CharSequence ITEM_0_LABEL = "Label0";
    private static final CharSequence ITEM_1_LABEL_A = "Label1A";
    private static final CharSequence ITEM_1_LABEL_B = "Label1B";

    private static final Rect BOUNDS_1 = new Rect(0, 0, 90, 20);
    private static final Rect BOUNDS_2 = new Rect(0, 0, 191, 124);
    private static final Rect BOUNDS_3 = new Rect(0, 0, 292, 225);

    ContextMenuItem mItem0 = mock(ContextMenuItem.class);
    ContextMenuItem mItem1 = mock(ContextMenuItem.class);
    ContextMenuNode mNode = new ContextMenuNode(mItem0, mItem1, new ClearFocusNode());

    Paint[] mPaints = {new Paint(), new Paint(), new Paint()};

    OverlayController mMockOverlayController;
    private final Context mContext = RuntimeEnvironment.application.getApplicationContext();
    private final SharedPreferences mSharedPreferences =
            ShadowPreferenceManager.getDefaultSharedPreferences(mContext);

    @Captor
    ArgumentCaptor<Iterable<Rect>> mRectSetCaptor;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        when(mItem0.getActionLabels(any(Context.class))).thenReturn(Arrays.asList(ITEM_0_LABEL));
        when(mItem1.getActionLabels(any(Context.class)))
                .thenReturn(Arrays.asList(ITEM_1_LABEL_A, ITEM_1_LABEL_B));
        mPaints[0].setColor(Color.RED);
        mPaints[1].setColor(Color.BLUE);
        mPaints[2].setColor(Color.GREEN);
        mMockOverlayController = mock(OverlayController.class);
    }

    @Test
    public void testShowSelections_addsViewWithExpectedProperties() {
        LinearLayout addedLayout = getLinearLayoutAddedWithShowSelections();

        assertEquals(LinearLayout.VERTICAL, addedLayout.getOrientation());
        ShadowLinearLayout shadowLinearLayout =
                (ShadowLinearLayout) ShadowExtractor.extract(addedLayout);
        assertEquals(Gravity.CENTER, shadowLinearLayout.getGravity());
        ViewGroup.LayoutParams params = addedLayout.getLayoutParams();
        assertEquals(ViewGroup.LayoutParams.MATCH_PARENT, params.width);
        assertEquals(ViewGroup.LayoutParams.MATCH_PARENT, params.height);
    }

    @Test
    public void testShowSelections_hasButtonsForAllLabels() {
        LinearLayout addedLayout = getLinearLayoutAddedWithShowSelections();

        assertEquals(3, addedLayout.getChildCount());
        for (int i = 0; i < 3; ++i) {
            assertTrue(addedLayout.getChildAt(i) instanceof Button);
        }
        assertEquals(ITEM_0_LABEL, ((Button) addedLayout.getChildAt(0)).getText());
        assertEquals(ITEM_1_LABEL_A, ((Button) addedLayout.getChildAt(1)).getText());
        assertEquals(ITEM_1_LABEL_B, ((Button) addedLayout.getChildAt(2)).getText());
    }

    @Test
    public void testShowSelections_highlightsButtons() {
        LinearLayout addedLayout = getLinearLayoutAddedWithShowSelections();

        /* Set up bounds since nothing is actually doing a layout here. */
        setBoundsForView(addedLayout.getChildAt(0), BOUNDS_1);
        setBoundsForView(addedLayout.getChildAt(1), BOUNDS_2);
        setBoundsForView(addedLayout.getChildAt(2), BOUNDS_3);

        /* Allow handler to run */
        ShadowHandler.runMainLooperToEndOfTasks();

        /* Verify first highlight */
        verify(mMockOverlayController).highlightPerimeterOfRects(
                mRectSetCaptor.capture(), eq(mPaints[0]));
        Iterator<Rect> rectIterator1 = mRectSetCaptor.getValue().iterator();
        assertEquals(BOUNDS_1, rectIterator1.next());
        assertFalse(rectIterator1.hasNext());
        /* Verify second highlight */
        verify(mMockOverlayController).highlightPerimeterOfRects(
                mRectSetCaptor.capture(), eq(mPaints[1]));
        Iterator<Rect> rectIterator2 = mRectSetCaptor.getValue().iterator();
        Rect rect1 = rectIterator2.next();
        Rect rect2 = rectIterator2.next();
        assertFalse(rectIterator2.hasNext());
        assertTrue((rect1.equals(BOUNDS_2)) || (rect2.equals(BOUNDS_2)));
        assertTrue((rect1.equals(BOUNDS_3)) || (rect2.equals(BOUNDS_3)));
        /* Verify that Clear Focus was not added in the context menu */
        verify(mMockOverlayController).highlightPerimeterOfRects(
                mRectSetCaptor.capture(), eq(mPaints[2]));
        Iterator<Rect> rectIterator3 = mRectSetCaptor.getValue().iterator();
        assertFalse(rectIterator3.hasNext());
    }

    @Test
    public void getActionLabels_returnListOfAllLabels() {
        List<CharSequence> labels = mNode.getActionLabels(null);

        assertEquals(3, labels.size());
        assertEquals(ITEM_0_LABEL, labels.get(0));
        assertEquals(ITEM_1_LABEL_A, labels.get(1));
        assertEquals(ITEM_1_LABEL_B, labels.get(2));
    }

    @Test
    public void optionScanningEnabled_contextMenuHasClearFocusButton() {
        mSharedPreferences.edit().clear().putString(mContext.getString(
                R.string.pref_scanning_methods_key), mContext.getString(
                R.string.option_scanning_key)).commit();
        LinearLayout addedLayout = getLinearLayoutAddedWithShowSelections();
        String clearFocusLabel = mContext.getResources()
                .getString(android.R.string.cancel);
        assertEquals(4, addedLayout.getChildCount());
        assertEquals(clearFocusLabel, ((Button) addedLayout.getChildAt(3)).getText());
    }

    private LinearLayout getLinearLayoutAddedWithShowSelections() {
        when(mMockOverlayController.getContext())
                .thenReturn(RuntimeEnvironment.application.getApplicationContext());
        mNode.showSelections(mMockOverlayController, mPaints);
        ArgumentCaptor<View> viewCaptor = ArgumentCaptor.forClass(View.class);
        verify(mMockOverlayController).addViewAndShow(viewCaptor.capture());
        View addedView = viewCaptor.getValue();
        assertTrue(addedView instanceof LinearLayout);
        return (LinearLayout) addedView;
    }

    private void setBoundsForView(View view, Rect bounds) {
        view.layout(bounds.left, bounds.top, bounds.right, bounds.bottom);
    }

    private Rect getBoundsForView(View view) {
        int[] locationOnScreen = new int[2];
        view.getLocationOnScreen(locationOnScreen);
        return new Rect(locationOnScreen[0], locationOnScreen[1],
                locationOnScreen[0] + view.getWidth(),
                locationOnScreen[1] + view.getHeight());
    }

    @Implements(Handler.class)
    public static class HandlerThatDelaysPostedRunnables {
        @RealObject Handler realHandler;
        @Implementation
        public boolean post(Runnable runnable) {
            return realHandler.postDelayed(runnable, 1);
        }
    }

    /* Can't find a way to make the existing LayoutInflater work */
    @Implements(LayoutInflater.class)
    public static class ShadowLayoutInflater {
        @Implementation
        public View inflate(int id, ViewGroup viewGroup) {
            /* Create return a button to match the layout file */
            return new Button(RuntimeEnvironment.application.getApplicationContext());
        }
    }
}
