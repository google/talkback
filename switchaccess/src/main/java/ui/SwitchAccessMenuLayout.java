/*
 * Copyright (C) 2018 Google Inc.
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
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.Path;
import android.graphics.Path.Direction;
import android.graphics.Point;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.View;
import android.widget.FrameLayout;
import com.google.android.accessibility.switchaccess.R;
import com.google.android.accessibility.switchaccess.utils.OverlayUtils;
import com.google.common.annotations.VisibleForTesting;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Layout for the Switch Access menu. Allows a FrameLayout with a transparent hole in it and draws a
 * border around the edge of the menu.
 */
public class SwitchAccessMenuLayout extends FrameLayout {

  @VisibleForTesting static final int STROKE_WIDTH = 2;

  private final Paint cutoutPaint;
  private final Paint menuBorderPaint;
  private final float cornerRadius;
  private Rect layoutCutout;
  private View toolTipView;
  private final Rect toolTipRect;
  private View menuContentView;
  private final Rect menuRect;
  private final Path borderPath;

  public SwitchAccessMenuLayout(Context context) {
    this(context, null);
  }

  public SwitchAccessMenuLayout(Context context, @Nullable AttributeSet attrs) {
    this(context, attrs, 0);
  }

  public SwitchAccessMenuLayout(Context context, @Nullable AttributeSet attrs, int defStyleRes) {
    this(context, attrs, defStyleRes, 0);
  }

  // This constructor needs to be public because it is for an inflated view.
  @SuppressWarnings("WeakerAccess")
  public SwitchAccessMenuLayout(
      Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
    super(context, attrs, defStyleAttr, defStyleRes);
    toolTipRect = new Rect();
    menuRect = new Rect();
    borderPath = new Path();
    cornerRadius = getResources().getDimension(R.dimen.switch_access_menu_border_radius);

    menuBorderPaint = new Paint();
    menuBorderPaint.setStrokeWidth(STROKE_WIDTH);
    menuBorderPaint.setColor(getResources().getColor(R.color.switch_access_menu_border));
    menuBorderPaint.setStyle(Style.STROKE);

    cutoutPaint = new Paint();
    cutoutPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
  }

  @Override
  public void onDraw(Canvas canvas) {
    super.onDraw(canvas);
    if (layoutCutout != null) {
      if (OverlayUtils.areBoundsAPoint(layoutCutout)) {
        // The bounding box is a point because we are in point scanning mode, so draw a circle
        // around the point.
        canvas.drawCircle(
            layoutCutout.left,
            layoutCutout.top,
            OverlayController.POINT_SCAN_CUTOUT_RADIUS,
            cutoutPaint);
      } else {
        canvas.drawRect(layoutCutout, cutoutPaint);
      }
    }
    if ((menuContentView != null) && (toolTipView != null)) {
      drawMenuBorder(canvas);
    }
  }

  @VisibleForTesting
  Rect getLayoutCutout() {
    return layoutCutout;
  }

  public void setLayoutCutout(Rect layoutCutout) {
    this.layoutCutout = layoutCutout;
  }

  public void setToolTipView(View toolTipView) {
    this.toolTipView = toolTipView;
  }

  public void setMenuContentView(View menuContentView) {
    this.menuContentView = menuContentView;
  }

  // The documentation for #getGlobalVisibleRect indicates that the globalOffset parameter can be
  // null, but it doesn't annotate the parameter.
  @SuppressWarnings("nullness:argument.type.incompatible")
  private void drawMenuBorder(Canvas canvas) {
    toolTipView.getGlobalVisibleRect(toolTipRect, null);
    menuContentView.getGlobalVisibleRect(menuRect, null);

    // Draw the border around the main menu content. Since the actual menu will be drawn after this,
    // the section of the border between the menu and the tool tip will be hidden.
    borderPath.reset();
    borderPath.addRoundRect(
        menuRect.left - STROKE_WIDTH,
        menuRect.top - STROKE_WIDTH,
        menuRect.right + STROKE_WIDTH,
        menuRect.bottom + STROKE_WIDTH,
        cornerRadius,
        cornerRadius,
        Direction.CW);

    // Get the location of the tooltip's bottom left and right corners and its pointed tip.
    int toolTipTop = toolTipRect.top - STROKE_WIDTH;
    int toolTipBottom = toolTipRect.bottom + STROKE_WIDTH;
    int toolTipLeft = toolTipRect.left - STROKE_WIDTH;
    int toolTipRight = toolTipRect.right + STROKE_WIDTH;
    Point toolTipPoint = new Point();
    Point toolTipFlatEdgeLeftCorner = new Point();
    Point toolTipFlatEdgeRightCorner = new Point();
    if (toolTipLeft < menuRect.left) {
      // Tooltip points left.
      toolTipPoint.set(toolTipLeft, toolTipRect.centerY());
      toolTipFlatEdgeLeftCorner.set(toolTipRight, toolTipBottom);
      toolTipFlatEdgeRightCorner.set(toolTipRight, toolTipTop);
    } else if (toolTipRight > menuRect.right) {
      // Tooltip points right.
      toolTipPoint.set(toolTipRight, toolTipRect.centerY());
      toolTipFlatEdgeLeftCorner.set(toolTipLeft, toolTipTop);
      toolTipFlatEdgeRightCorner.set(toolTipLeft, toolTipBottom);
    } else if (toolTipTop < menuRect.top) {
      // Tooltip points up.
      toolTipPoint.set(toolTipRect.centerX(), toolTipTop);
      toolTipFlatEdgeLeftCorner.set(toolTipLeft, toolTipBottom);
      toolTipFlatEdgeRightCorner.set(toolTipRight, toolTipBottom);
    } else {
      // Tooltip points down
      toolTipPoint.set(toolTipRect.centerX(), toolTipBottom);
      toolTipFlatEdgeLeftCorner.set(toolTipLeft, toolTipTop);
      toolTipFlatEdgeRightCorner.set(toolTipRight, toolTipTop);
    }

    // Draw the border around the menu tool tip. In a triangle pointing upwards, this draws from the
    // bottom left corner to the pointed tip of the triangle, back down to the bottom right corner.
    borderPath.moveTo(toolTipFlatEdgeLeftCorner.x, toolTipFlatEdgeLeftCorner.y);
    borderPath.lineTo(toolTipPoint.x, toolTipPoint.y);
    borderPath.lineTo(toolTipFlatEdgeRightCorner.x, toolTipFlatEdgeRightCorner.y);
    canvas.drawPath(borderPath, menuBorderPaint);
  }
}
