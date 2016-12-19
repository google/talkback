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
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.Build;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import com.android.switchaccess.OverlayController;
import com.android.talkback.BuildConfig;
import com.android.utils.widget.SimpleOverlay;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricGradleTestRunner;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.when;

/**
 * Tests for OverlayController
 */
/**
 * Tests for ContextMenuController
 */
@Config(
        constants = BuildConfig.class,
        sdk = 21)
@RunWith(RobolectricGradleTestRunner.class)
@TargetApi(Build.VERSION_CODES.LOLLIPOP)
public class OverlayControllerTest {
    private static final Rect BOUNDS0 = new Rect(130, 140, 200, 250);  // 70x110

    private static final Rect BOUNDS1 = new Rect(130, 260, 200, 300);  // 70x40

    /*
     * Choose offsets large enough that getting them wrong or transposing them would move
     * the highlight enough to include the wrong rectangles
     */
    private static final int RELATIVE_LAYOUT_X_OFFSET = 50;
    private static final int RELATIVE_LAYOUT_Y_OFFSET = 100;

    private final Context mContext = RuntimeEnvironment.application.getApplicationContext();
    private SimpleOverlay mMockSimpleOverlay = mock(SimpleOverlay.class);
    private RelativeLayout mMockRelativeLayout = mock(RelativeLayout.class);
    private OverlayController mOverlayController;

    @Before
    public void setUp() {
        WindowManager.LayoutParams overlayLayoutParams = new WindowManager.LayoutParams();
        when(mMockSimpleOverlay.findViewById(anyInt())).thenReturn(mMockRelativeLayout);
        when(mMockSimpleOverlay.getParams()).thenReturn(overlayLayoutParams);
        when(mMockSimpleOverlay.getContext()).thenReturn(mContext);
        mOverlayController = new OverlayController(mMockSimpleOverlay);
        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) throws Throwable {
                int[] locationOnScreen = (int[]) invocation.getArguments()[0];
                locationOnScreen[0] = RELATIVE_LAYOUT_X_OFFSET;
                locationOnScreen[1] = RELATIVE_LAYOUT_Y_OFFSET;
                return null;
            }
        }).when(mMockRelativeLayout).getLocationOnScreen((int[]) anyObject());
    }

    @Test
    public void testAtStartup_overlayNotShown() {
        verify(mMockSimpleOverlay, times(0)).show();
    }

    @Test
    public void testConfigure_systemOverlayIsNotTouchable() {
        mOverlayController.configureOverlay();
        ArgumentCaptor<WindowManager.LayoutParams> layoutParamsArgumentCaptor =
                ArgumentCaptor.forClass(WindowManager.LayoutParams.class);
        verify(mMockSimpleOverlay, atLeastOnce()).setParams(layoutParamsArgumentCaptor.capture());
        WindowManager.LayoutParams layoutParams = layoutParamsArgumentCaptor.getValue();

        assertEquals(WindowManager.LayoutParams.TYPE_SYSTEM_OVERLAY, layoutParams.type);
        assertTrue((layoutParams.flags & WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE) != 0);
    }

    @Test
    public void testHighlightPerimeterOfRects_singleRectHighlighted() {
        mOverlayController.highlightPerimeterOfRects(Arrays.asList(BOUNDS0), new Paint());
        ArgumentCaptor<ImageView> highlightViewCaptor = ArgumentCaptor.forClass(ImageView.class);
        verify(mMockRelativeLayout, times(1)).addView(highlightViewCaptor.capture());

        ImageView highlightView = highlightViewCaptor.getValue();
        RelativeLayout.LayoutParams layoutParams =
                (RelativeLayout.LayoutParams) highlightView.getLayoutParams();
        assertTrue(paramsBoundsRect(layoutParams, BOUNDS0));
    }

    @Test
    public void testHighlightPerimeterOfRects_twoRectHighlighted() {
        mOverlayController.highlightPerimeterOfRects(Arrays.asList(BOUNDS0, BOUNDS1), new Paint());
        ArgumentCaptor<ImageView> highlightViewCaptor = ArgumentCaptor.forClass(ImageView.class);
        verify(mMockRelativeLayout, times(2)).addView(highlightViewCaptor.capture());
        List<ImageView> highlightViews = highlightViewCaptor.getAllValues();

        ImageView highlightView0 = highlightViews.get(0);
        ImageView highlightView1 = highlightViews.get(1);
        RelativeLayout.LayoutParams layoutParams0 =
                (RelativeLayout.LayoutParams) highlightView0.getLayoutParams();
        RelativeLayout.LayoutParams layoutParams1 =
                (RelativeLayout.LayoutParams) highlightView1.getLayoutParams();

        if (paramsBoundsRect(layoutParams0, BOUNDS0)) {
            assertTrue(paramsBoundsRect(layoutParams1, BOUNDS1));
        } else {
            assertTrue(paramsBoundsRect(layoutParams0, BOUNDS1));
            assertTrue(paramsBoundsRect(layoutParams1, BOUNDS0));
        }
    }

    @Test
    public void testAddViewAndShow_doesAddViewAndShow() {
        View view = new View(mContext);
        mOverlayController.addViewAndShow(view);
        verify(mMockRelativeLayout, times(1)).addView(view);
        verify(mMockSimpleOverlay, times(1)).show();
    }

    @Test
    public void testClearOverlay_removesAllViewsAndHides() {
        mOverlayController.clearOverlay();
        verify(mMockRelativeLayout, times(1)).removeAllViews();
        verify(mMockSimpleOverlay, times(1)).hide();
    }

    @Test
    public void testShutdown_hides() {
        mOverlayController.shutdown();
        verify(mMockSimpleOverlay, times(1)).hide();
    }

    private boolean paramsBoundsRect(RelativeLayout.LayoutParams layoutParams, Rect rect) {
        boolean result = (layoutParams.leftMargin <= rect.left - RELATIVE_LAYOUT_X_OFFSET);
        result = result && (layoutParams.topMargin <= rect.top - RELATIVE_LAYOUT_Y_OFFSET);
        result = result && (layoutParams.leftMargin + layoutParams.width
                >= rect.right - RELATIVE_LAYOUT_X_OFFSET);
        result = result && (layoutParams.topMargin + layoutParams.height
                >= rect.bottom - RELATIVE_LAYOUT_Y_OFFSET);
        return result;
    }
}
