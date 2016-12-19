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

package com.android.switchaccess;

import android.annotation.TargetApi;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.RectShape;
import android.os.Build;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import com.android.talkback.R;
import com.android.utils.widget.SimpleOverlay;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Controller for the Switch Access overlay. The controller handles two operations: it outlines
 * groups of Views, and it presents context menus (with Views that are outlined).
 */
@TargetApi(Build.VERSION_CODES.LOLLIPOP_MR1)
public class OverlayController {

    private final SimpleOverlay mOverlay;

    private final RelativeLayout mRelativeLayout;

    private final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(Intent.ACTION_CONFIGURATION_CHANGED)) {
                configureOverlay();
            }
        }
    };

    /*
     * TODO replace ugly map with a better solution. The better solution will likely change
     * the preferences, which this approach avoids touching.
     */
    private static final Map<Integer, Integer> MAIN_TO_OUTER_HIGHLIGHT_COLOR_MAP;
    static {
        MAIN_TO_OUTER_HIGHLIGHT_COLOR_MAP = new HashMap<>();
        MAIN_TO_OUTER_HIGHLIGHT_COLOR_MAP.put(new Integer(0xff4caf50), new Integer(0xff1b5e20));
        MAIN_TO_OUTER_HIGHLIGHT_COLOR_MAP.put(new Integer(0xffff9800), new Integer(0xffe65100));
        MAIN_TO_OUTER_HIGHLIGHT_COLOR_MAP.put(new Integer(0xfff44336), new Integer(0xffb71c1c));
        MAIN_TO_OUTER_HIGHLIGHT_COLOR_MAP.put(new Integer(0xff2196f3), new Integer(0xff0d47a1));
        MAIN_TO_OUTER_HIGHLIGHT_COLOR_MAP.put(new Integer(0xffffffff), new Integer(0xff000000));
    }

    /**
     * @param overlay The overlay on which to draw focus indications
     */
    public OverlayController(SimpleOverlay overlay) {
        mOverlay = overlay;
        mOverlay.setContentView(R.layout.switch_access_overlay_layout);
        mRelativeLayout = (RelativeLayout) mOverlay.findViewById(R.id.overlayRelativeLayout);
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_CONFIGURATION_CHANGED);
        mOverlay.getContext().registerReceiver(mBroadcastReceiver, filter);
    }

    /**
     * Get the overlay ready to show. This method displays an empty overlay and tweaks it to
     * make sure it's in the right location.
     */
    public void configureOverlay() {
        configureOverlayBeforeShow();
        mOverlay.show();
        new Handler().post(new Runnable() {
            @Override
            public void run() {
                configureOverlayAfterShow();
            }
        });
    }

    public void highlightPerimeterOfRects(Iterable<Rect> rects, Paint highlightPaint) {
        final Set<Rect> rectsToHighlight = new HashSet<>();
        final Paint finalHighlightPaint = new Paint(highlightPaint);
        for (Rect rect : rects) {
            rectsToHighlight.add(rect);
        }
        mOverlay.show();

        /*
         * Run the rest of the function in a handler to give the thread a chance to draw the
         * overlay.
         */
        new Handler().post(new Runnable() {
            @Override
            public void run() {
                int[] layoutCoordinates = new int[2];
                mRelativeLayout.getLocationOnScreen(layoutCoordinates);
                for (Rect rect : rectsToHighlight) {
                    ShapeDrawable mainHighlightDrawable = new ShapeDrawable(new RectShape());
                    mainHighlightDrawable.setIntrinsicWidth(rect.width());
                    mainHighlightDrawable.setIntrinsicHeight(rect.height());
                    mainHighlightDrawable.getPaint().set(finalHighlightPaint);
                    Drawable highlightDrawable = mainHighlightDrawable;
                    if (MAIN_TO_OUTER_HIGHLIGHT_COLOR_MAP
                            .containsKey(finalHighlightPaint.getColor())) {
                        ShapeDrawable outerHighlightDrawable = new ShapeDrawable(new RectShape());
                        outerHighlightDrawable.setIntrinsicWidth(rect.width());
                        outerHighlightDrawable.setIntrinsicHeight(rect.height());
                        Paint outerHighlightPaint = new Paint(finalHighlightPaint);
                        outerHighlightPaint.setColor(MAIN_TO_OUTER_HIGHLIGHT_COLOR_MAP
                                .get(finalHighlightPaint.getColor()));
                        outerHighlightPaint
                                .setStrokeWidth(finalHighlightPaint.getStrokeWidth() / 2);
                        outerHighlightDrawable.getPaint().set(outerHighlightPaint);
                        Drawable[] layers = {mainHighlightDrawable, outerHighlightDrawable};
                        highlightDrawable = new LayerDrawable(layers);
                    }

                    ImageView imageView = new ImageView(mOverlay.getContext());
                    imageView.setBackground(highlightDrawable);

                    // Align image with node we're highlighting
                    final RelativeLayout.LayoutParams layoutParams =
                            new RelativeLayout.LayoutParams(rect.width(), rect.height());
                    layoutParams.leftMargin = rect.left - layoutCoordinates[0];
                    layoutParams.topMargin = rect.top - layoutCoordinates[1];
                    imageView.setLayoutParams(layoutParams);
                    mRelativeLayout.addView(imageView);
                }
            }
        });
    }

    /**
     * Override focus highlighting with a custom overlay
     */
    public void addViewAndShow(View view) {
        mRelativeLayout.addView(view);
        mOverlay.show();
    }

    /**
     * Clear focus highlighting
     */
    public void clearOverlay() {
        mRelativeLayout.removeAllViews();
        mOverlay.hide();
    }

    /**
     * Shut down nicely
     */
    public void shutdown() {
        mOverlay.getContext().unregisterReceiver(mBroadcastReceiver);
        mOverlay.hide();
    }

    /**
     * When option scanning is enabled, a menu button is drawn at the top of the screen. This
     * button offers the user the possibility of clearing the focus or choosing global actions
     * (i.e Home, Back, Notifications, etc).
     */
    public void drawMenuButton() {
        Context context = mOverlay.getContext();
        LayoutInflater layoutInflater =
                (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        LinearLayout menuButtonLayout = (LinearLayout) layoutInflater
                .inflate(R.layout.switch_access_global_menu_button, mRelativeLayout, false);
        addViewAndShow(menuButtonLayout);
    }

    /**
     * @return If option scanning is enabled, gets the location of the menu at the top of the
     *         screen. Otherwise null is returned.
     */
    public Rect getMenuButtonLocation() {
        Button menuButton = (Button) mOverlay.findViewById(R.id.top_screen_menu_button);
        if (menuButton != null) {
            Rect locationOnScreen = new Rect();
            menuButton.getGlobalVisibleRect(locationOnScreen);
            return locationOnScreen;
        }
        return null;
    }

    /**
     * Obtain the context for drawing
     */
    public Context getContext() {
        return mOverlay.getContext();
    }

    private void configureOverlayBeforeShow() {
        // The overlay shouldn't capture touch events
        final WindowManager.LayoutParams params = mOverlay.getParams();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            params.type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY;
        } else {
            params.type = WindowManager.LayoutParams.TYPE_SYSTEM_OVERLAY;
        }
        params.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        params.flags |= WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;

        /* The overlay covers the entire screen. However, there is a left, top, right, and
         * bottom margin.  */
        final WindowManager wm = (WindowManager) mOverlay.getContext()
                .getSystemService(Context.WINDOW_SERVICE);
        final Point size = new Point();
        wm.getDefaultDisplay().getRealSize(size);
        params.height = size.y;
        params.width = size.x;
        params.x = 0;
        params.y = 0;

        mOverlay.setParams(params);
    }

    /*
     * For some reason, it's very difficult to create a layout that covers exactly the entire screen
     * and doesn't move when an unhandled key is pressed. The configuration we're using seems to
     * result in a layout that starts above the screen. So we split initialization into two
     * pieces, and here we find out where the overlay ended up and move it to be at the top
     * of the screen.
     * TODO Separating the menu and highlighting should be a cleaner way to solve this
     * issue
     */
    private void configureOverlayAfterShow() {
        int[] location = new int[2];
        mRelativeLayout.getLocationOnScreen(location);
        WindowManager.LayoutParams layoutParams = mOverlay.getParams();
        layoutParams.y -= location[1];
        mOverlay.setParams(layoutParams);
    }
}
