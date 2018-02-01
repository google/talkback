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
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.ArcShape;
import android.widget.ImageView;
import android.widget.RelativeLayout;

/**
 * A single clock displayed on the screen. Each Nomon clock is associated to a view on the screen,
 * informing the user when that particular view is selectable.
 */
public class NomonClock {
  private static final int NOON_POSITION_DEG = 270;
  private static final int CLOCK_DIAMETER_DP = 75;

  private static final int NOMON_LIGHT_GREY = 0xFABBBBBB;
  private static final int NOMON_YELLOW = 0xFAFFD54F;

  private final ImageView mClockFaceView;
  private final ImageView mHandView;
  private final ImageView mWedgeView;

  private boolean mIsActiveGroup = false;

  /**
   * @param boundingRect The bounding rectangle of the view represented by this clock
   * @param context The current context
   * @param startAngle The angle at which this clock's hand will be initially set
   * @param liveAngleRangeDegree The angle range of this clock's colored wedge
   * @param isActive Whether or not the clock is currently active
   */
  public NomonClock(
      Rect boundingRect,
      Context context,
      float startAngle,
      float liveAngleRangeDegree,
      boolean isActive) {
    mClockFaceView = new ImageView(context);
    mWedgeView = new ImageView(context);
    mHandView = new ImageView(context);

    createClockFace(liveAngleRangeDegree, boundingRect);

    mIsActiveGroup = isActive;
    if (mIsActiveGroup) {
      setActiveState();
    } else {
      setInactiveOpaqueState();
    }
  }

  /**
   * Show the clock. Note: This method should only be called once.
   *
   * @param overlayController The {@link OverlayController} to which views corresponding to this
   *     clock should be added
   */
  public void show(OverlayController overlayController) {
    overlayController.addViewAndShow(mClockFaceView);
    overlayController.addViewAndShow(mWedgeView);
    overlayController.addViewAndShow(mHandView);
  }

  /**
   * Update the animation.
   *
   * @param rotationalValue The new rotationalValue of the clock
   * @param isActive {@code true} if this clock is currently active
   */
  public void updateAnimation(float rotationalValue, boolean isActive) {
    mHandView.setRotation(rotationalValue);
    if (!mIsActiveGroup && isActive) {
      mIsActiveGroup = true;
      setActiveState();
    } else if (mIsActiveGroup && !isActive) {
      mIsActiveGroup = false;
      setInactiveOpaqueState();
    }
  }

  private void createClockFace(float liveAngleRangeDegree, Rect boundingRect) {
    final ShapeDrawable wedge =
        new ShapeDrawable(
            new ArcShape(
                (float) (NOON_POSITION_DEG - liveAngleRangeDegree / 2.0), liveAngleRangeDegree));
    wedge.setIntrinsicHeight(CLOCK_DIAMETER_DP);
    wedge.setIntrinsicWidth(CLOCK_DIAMETER_DP);
    mWedgeView.setImageDrawable(wedge);
    mClockFaceView.setImageResource(R.drawable.nomon_clock_clockface);
    mHandView.setImageResource(R.drawable.nomon_clock_hand);

    // Set the drawables that change state to be mutable.
    mWedgeView.getDrawable().mutate();
    mClockFaceView.getDrawable().mutate();

    // Align image with node we're highlighting
    final RelativeLayout.LayoutParams layoutParams =
        new RelativeLayout.LayoutParams(CLOCK_DIAMETER_DP, CLOCK_DIAMETER_DP);
    layoutParams.leftMargin = boundingRect.left;
    layoutParams.topMargin = boundingRect.top;
    layoutParams.height = CLOCK_DIAMETER_DP;
    layoutParams.width = CLOCK_DIAMETER_DP;

    mClockFaceView.setLayoutParams(layoutParams);
    mWedgeView.setLayoutParams(layoutParams);
    mHandView.setLayoutParams(layoutParams);
  }

  private void setActiveState() {
    mClockFaceView.getDrawable().setColorFilter(Color.WHITE, PorterDuff.Mode.MULTIPLY);
    mWedgeView.getDrawable().setColorFilter(NOMON_YELLOW, PorterDuff.Mode.SRC_ATOP);
  }

  private void setInactiveOpaqueState() {
    mClockFaceView.getDrawable().setColorFilter(NOMON_LIGHT_GREY, PorterDuff.Mode.MULTIPLY);
    mWedgeView.getDrawable().setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_ATOP);
  }
}
