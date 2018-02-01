/*
 * Copyright (C) 2017 Google Inc.
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

package com.google.android.accessibility.switchaccess;

import android.content.Context;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Handler;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import java.util.HashMap;
import java.util.Map;

/**
 * {@code HighlightStrategy} used for linear scanning, row scanning, and group selection. This class
 * draws rectangular bounds on the screen.
 */
public class OptionScanHighlighter implements HighlightStrategy {
  // TODO replace ugly map with a better solution. The better solution will likely change
  // the preferences, which this approach avoids touching.
  private static final Map<Integer, Integer> MAIN_TO_OUTER_HIGHLIGHT_COLOR_MAP;

  static {
    MAIN_TO_OUTER_HIGHLIGHT_COLOR_MAP = new HashMap<>();
    MAIN_TO_OUTER_HIGHLIGHT_COLOR_MAP.put(Integer.valueOf(0xff4caf50), Integer.valueOf(0xff1b5e20));
    MAIN_TO_OUTER_HIGHLIGHT_COLOR_MAP.put(Integer.valueOf(0xffff9800), Integer.valueOf(0xffe65100));
    MAIN_TO_OUTER_HIGHLIGHT_COLOR_MAP.put(Integer.valueOf(0xfff44336), Integer.valueOf(0xffb71c1c));
    MAIN_TO_OUTER_HIGHLIGHT_COLOR_MAP.put(Integer.valueOf(0xff2196f3), Integer.valueOf(0xff0d47a1));
    MAIN_TO_OUTER_HIGHLIGHT_COLOR_MAP.put(Integer.valueOf(0xffffffff), Integer.valueOf(0xff000000));
  }

  private final OverlayController mOverlayController;

  public OptionScanHighlighter(OverlayController overlayController) {
    mOverlayController = overlayController;
  }

  @Override
  public void highlight(
      final Iterable<TreeScanLeafNode> nodes,
      final Paint highlightPaint,
      int groupIndex,
      int totalChildren) {

    /*
     * Run the rest of the function in a handler to give the thread a chance to draw the
     * overlay.
     */
    new Handler()
        .post(
            new Runnable() {
              @Override
              public void run() {
                int[] layoutCoordinates = new int[2];
                for (TreeScanLeafNode node : nodes) {
                  Rect rect = node.getRectForNodeHighlight();
                  if (rect == null) {
                    continue;
                  }
                  int halfStrokeWidth = (int) highlightPaint.getStrokeWidth() / 2;
                  GradientDrawable mainHighlightDrawable = new GradientDrawable();
                  mainHighlightDrawable.setShape(GradientDrawable.RECTANGLE);
                  mainHighlightDrawable.setCornerRadius(halfStrokeWidth);
                  mainHighlightDrawable.setSize(rect.width(), rect.height());
                  mainHighlightDrawable.setStroke(halfStrokeWidth, highlightPaint.getColor());
                  Drawable highlightDrawable = mainHighlightDrawable;
                  if (MAIN_TO_OUTER_HIGHLIGHT_COLOR_MAP.containsKey(highlightPaint.getColor())) {
                    GradientDrawable outerHighlightDrawable = new GradientDrawable();
                    outerHighlightDrawable.setShape(GradientDrawable.RECTANGLE);
                    outerHighlightDrawable.setCornerRadius(halfStrokeWidth);
                    outerHighlightDrawable.setSize(rect.width(), rect.height());
                    outerHighlightDrawable.setStroke(
                        halfStrokeWidth / 2,
                        MAIN_TO_OUTER_HIGHLIGHT_COLOR_MAP.get(highlightPaint.getColor()));

                    // Create the final drawable, adding arrows to scrollable elements if the
                    // Scroll Arrows feature is enabled
                    Context context = mOverlayController.getContext();
                    if (FeatureFlags.scrollArrows() && node.isScrollable()) {
                      int scrollArrowBackgroundColor =
                          MAIN_TO_OUTER_HIGHLIGHT_COLOR_MAP.get(highlightPaint.getColor());

                      LayerDrawable leftScrollDrawable =
                          createScrollIndicatorDrawable(
                              context,
                              R.drawable.scroll_indicator_left,
                              scrollArrowBackgroundColor);
                      LayerDrawable upScrollDrawable =
                          createScrollIndicatorDrawable(
                              context, R.drawable.scroll_indicator_up, scrollArrowBackgroundColor);
                      LayerDrawable rightScrollDrawable =
                          createScrollIndicatorDrawable(
                              context,
                              R.drawable.scroll_indicator_right,
                              scrollArrowBackgroundColor);
                      LayerDrawable downScrollDrawable =
                          createScrollIndicatorDrawable(
                              context,
                              R.drawable.scroll_indicator_down,
                              scrollArrowBackgroundColor);

                      Drawable[] layers = {
                        mainHighlightDrawable,
                        outerHighlightDrawable,
                        leftScrollDrawable,
                        upScrollDrawable,
                        rightScrollDrawable,
                        downScrollDrawable
                      };
                      highlightDrawable = new LayerDrawable(layers);
                    } else {
                      Drawable[] layers = {mainHighlightDrawable, outerHighlightDrawable};
                      highlightDrawable = new LayerDrawable(layers);
                    }
                  }

                  ImageView imageView = new ImageView(mOverlayController.getContext());
                  imageView.setBackground(highlightDrawable);

                  // Align image with node we're highlighting
                  final RelativeLayout.LayoutParams layoutParams =
                      new RelativeLayout.LayoutParams(rect.width(), rect.height());
                  layoutParams.leftMargin = rect.left - layoutCoordinates[0];
                  layoutParams.topMargin = rect.top - layoutCoordinates[1];
                  imageView.setLayoutParams(layoutParams);
                  mOverlayController.addViewAndShow(imageView);
                }
              }
            });
  }

  private LayerDrawable createScrollIndicatorDrawable(
      Context context, int resourceId, int backgroundColor) {
    LayerDrawable scrollDrawable = (LayerDrawable) context.getDrawable(resourceId);
    GradientDrawable scrollBackground =
        (GradientDrawable) scrollDrawable.findDrawableByLayerId(R.id.background);
    scrollBackground.setColor(backgroundColor);
    return scrollDrawable;
  }

  @Override
  public void shutdown() {
    // Do nothing
  }
}
