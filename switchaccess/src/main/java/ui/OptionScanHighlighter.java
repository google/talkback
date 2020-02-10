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

package com.google.android.accessibility.switchaccess.ui;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat.AccessibilityActionCompat;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import com.android.switchaccess.SwitchAccessService;
import com.google.android.accessibility.switchaccess.FeatureFlags;
import com.google.android.accessibility.switchaccess.R;
import com.google.android.accessibility.switchaccess.treenodes.TreeScanLeafNode;
import com.google.android.libraries.accessibility.utils.concurrent.ThreadUtils;
import com.google.android.libraries.accessibility.utils.log.LogUtils;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.checkerframework.checker.nullness.qual.KeyFor;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * {@code HighlightStrategy} used for linear scanning, row scanning, and group selection. This class
 * draws rectangular bounds on the screen.
 */
public class OptionScanHighlighter implements HighlightStrategy {

  private static final String TAG = "OptionScanHighlighter";

  // TODO Replace ugly map with a better solution. The better solution will likely
  // change the preferences, which this approach avoids touching.
  private static final Map<Integer, Integer> MAIN_TO_OUTER_HIGHLIGHT_COLOR_MAP;

  static {
    MAIN_TO_OUTER_HIGHLIGHT_COLOR_MAP = new HashMap<>();
    MAIN_TO_OUTER_HIGHLIGHT_COLOR_MAP.put(0xff4caf50, 0xff1b5e20);
    MAIN_TO_OUTER_HIGHLIGHT_COLOR_MAP.put(0xffff9800, 0xffe65100);
    MAIN_TO_OUTER_HIGHLIGHT_COLOR_MAP.put(0xfff44336, 0xffb71c1c);
    MAIN_TO_OUTER_HIGHLIGHT_COLOR_MAP.put(0xff2196f3, 0xff0d47a1);
    MAIN_TO_OUTER_HIGHLIGHT_COLOR_MAP.put(0xffffffff, 0xff000000);
  }

  private final OverlayController overlayController;

  public OptionScanHighlighter(OverlayController overlayController) {
    this.overlayController = overlayController;
  }

  @Override
  public void highlight(
      final Iterable<TreeScanLeafNode> nodes,
      final Paint highlightPaint,
      int groupIndex,
      int totalChildren) {

    // Do not draw invisible Views.
    if (highlightPaint.getAlpha() == 0) {
      return;
    }

    /*
     * Run the rest of the function in a handler to give the thread a chance to draw the
     * overlay.
     */
    ThreadUtils.runOnMainThread(
        SwitchAccessService::isActive, new HighlightRunnable(nodes, highlightPaint));
  }

  @Override
  public void shutdown() {
    // Remove the callbacks from the handler on shutdown.
    ThreadUtils.removeCallbacksAndMessages(null);
  }

  private class HighlightRunnable implements Runnable {
    private final Iterable<TreeScanLeafNode> nodes;
    private final Paint highlightPaint;

    HighlightRunnable(final Iterable<TreeScanLeafNode> nodes, final Paint highlightPaint) {
      this.nodes = nodes;
      this.highlightPaint = highlightPaint;
    }

    @Override
    public void run() {
      int[] layoutCoordinates = new int[2];
      for (TreeScanLeafNode node : nodes) {
        Rect rect = node.getRectForNodeHighlight();
        if (rect == null) {
          continue;
        }
        int halfStrokeWidth = (int) highlightPaint.getStrokeWidth() / 2;
        GradientDrawable mainHighlightDrawable = getHighlightDrawable(halfStrokeWidth, rect);
        @KeyFor("MAIN_TO_OUTER_HIGHLIGHT_COLOR_MAP")
        int mainPaintColor = highlightPaint.getColor();
        mainHighlightDrawable.setStroke(halfStrokeWidth, mainPaintColor);

        GradientDrawable outerHighlightDrawable = null;
        if (MAIN_TO_OUTER_HIGHLIGHT_COLOR_MAP.containsKey(mainPaintColor)) {
          outerHighlightDrawable = getHighlightDrawable(halfStrokeWidth, rect);
          outerHighlightDrawable.setStroke(
              halfStrokeWidth / 2, MAIN_TO_OUTER_HIGHLIGHT_COLOR_MAP.get(mainPaintColor));
        }

        // Determine which scroll arrows we should show.
        boolean shouldShowUpArrow = false;
        boolean shouldShowDownArrow = false;
        boolean shouldShowRightArrow = false;
        boolean shouldShowLeftArrow = false;
        boolean supportsScrollForward = false;
        boolean supportsScrollBackward = false;
        if (FeatureFlags.scrollArrows()
            && (VERSION.SDK_INT >= VERSION_CODES.N)
            && node.isScrollable()) {
          for (AccessibilityActionCompat action : node.getActionList()) {
            if (action.getId() == AccessibilityNodeInfoCompat.ACTION_SCROLL_BACKWARD) {
              supportsScrollBackward = true;
            } else if (action.getId() == AccessibilityNodeInfoCompat.ACTION_SCROLL_FORWARD) {
              supportsScrollForward = true;
            } else if (VERSION.SDK_INT >= VERSION_CODES.N_MR1) {
              if (action.getId() == AccessibilityActionCompat.ACTION_SCROLL_UP.getId()) {
                shouldShowUpArrow = true;
              } else if (action.getId() == AccessibilityActionCompat.ACTION_SCROLL_DOWN.getId()) {
                shouldShowDownArrow = true;
              } else if (action.getId() == AccessibilityActionCompat.ACTION_SCROLL_RIGHT.getId()) {
                shouldShowRightArrow = true;
              } else if (action.getId() == AccessibilityActionCompat.ACTION_SCROLL_LEFT.getId()) {
                shouldShowLeftArrow = true;
              }
            }
          }

          // If only the less granular version of a scroll action is supported, show the possible
          // scroll directions for that scroll action.
          if (supportsScrollForward && !shouldShowDownArrow && !shouldShowRightArrow) {
            shouldShowDownArrow = true;
            shouldShowRightArrow = true;
          }
          if (supportsScrollBackward && !shouldShowUpArrow && !shouldShowLeftArrow) {
            shouldShowUpArrow = true;
            shouldShowLeftArrow = true;
          }
        }

        ImageView imageView = new ImageView(overlayController.getContext());
        imageView.setBackground(
            getFinalHighlightDrawable(
                shouldShowUpArrow,
                shouldShowDownArrow,
                shouldShowRightArrow,
                shouldShowLeftArrow,
                mainHighlightDrawable,
                outerHighlightDrawable));

        // Align image with node we're highlighting
        final RelativeLayout.LayoutParams layoutParams =
            new RelativeLayout.LayoutParams(rect.width(), rect.height());
        layoutParams.leftMargin = rect.left - layoutCoordinates[0];
        layoutParams.topMargin = rect.top - layoutCoordinates[1];
        imageView.setLayoutParams(layoutParams);
        overlayController.addViewAndShow(imageView);
      }
    }

    // Sets the shape, corner radius, and size for the other and main highlight drawables.
    private GradientDrawable getHighlightDrawable(int halfStrokeWidth, Rect rectForNodeHighlight) {
      GradientDrawable highlightDrawable = new GradientDrawable();
      highlightDrawable.setShape(GradientDrawable.RECTANGLE);
      highlightDrawable.setCornerRadius(halfStrokeWidth);
      highlightDrawable.setSize(rectForNodeHighlight.width(), rectForNodeHighlight.height());
      return highlightDrawable;
    }

    // Create the final drawable, adding arrows to scrollable elements if the
    // Scroll Arrows feature is enabled
    @SuppressWarnings("argument.type.incompatible")
    private LayerDrawable getFinalHighlightDrawable(
        boolean shouldShowUpArrow,
        boolean shouldShowDownArrow,
        boolean shouldShowRightArrow,
        boolean shouldShowLeftArrow,
        GradientDrawable mainHighlightDrawable,
        @Nullable GradientDrawable outerHighlightDrawable) {
      Context context = overlayController.getContext();
      List<Drawable> layers = new ArrayList<>(6);
      try {
        if (shouldShowRightArrow) {
          layers.add(context.getDrawable(R.drawable.scroll_indicator_right));
        }
        if (shouldShowLeftArrow) {
          layers.add(context.getDrawable(R.drawable.scroll_indicator_left));
        }
        if (shouldShowUpArrow) {
          layers.add(context.getDrawable(R.drawable.scroll_indicator_up));
        }
        if (shouldShowDownArrow) {
          layers.add(context.getDrawable(R.drawable.scroll_indicator_down));
        }
      } catch (Resources.NotFoundException e) {
        // TODO: Investigate why this ResourcesNotFoundException is happening.
        LogUtils.e(TAG, "Scroll arrow resource not found.");
      }
      // Draw the borders on top of the arrow indicators.
      layers.add(mainHighlightDrawable);
      if (outerHighlightDrawable != null) {
        // The outer drawable can be null if we didn't find the color corresponding to the main
        // highlight drawable in our map.
        layers.add(outerHighlightDrawable);
      }
      Drawable[] layersList = new Drawable[layers.size()];
      layers.toArray(layersList);
      return new LayerDrawable(layersList);
    }
  }
}
