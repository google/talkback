/*
 * Copyright (C) 2013 Google Inc.
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

package com.google.android.accessibility.braille.brailledisplay;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.Message;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowManager;
import com.google.android.accessibility.utils.MotionEventUtils;
import com.google.android.libraries.accessibility.widgets.simple.SimpleOverlay;

/**
 * Overlay that can be long-pressed, and then dragged to the top or bottom of the screen.
 * Intermediate positions are not supported due to the complexity that arises from screen
 * orientation changes.
 */
public class DraggableOverlay extends SimpleOverlay {
  private static final int DEFAULT_GRAVITY = Gravity.BOTTOM;
  private static final int LONG_PRESS_TIMEOUT = ViewConfiguration.getLongPressTimeout();
  private static final int TAP_TIMEOUT = ViewConfiguration.getTapTimeout();
  private final int touchSlopSquare;
  private final WindowManager windowManager;
  private final WindowManager.LayoutParams windowParams;
  private final View touchStealingView;
  private final WindowManager.LayoutParams touchStealingLayoutParams;
  private final InternalListener internalListener;
  private boolean dragging = false;
  private float dragOrigin;

  public DraggableOverlay(Context context) {
    super(context);
    windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);

    // Compute touch slop.
    ViewConfiguration viewConfiguration = ViewConfiguration.get(context);
    int touchSlop = viewConfiguration.getScaledTouchSlop();
    touchSlopSquare = touchSlop * touchSlop;

    // Configure the overlay window.
    windowParams = createOverlayParams();
    windowParams.width = WindowManager.LayoutParams.MATCH_PARENT;
    windowParams.height = WindowManager.LayoutParams.WRAP_CONTENT;
    windowParams.gravity = DEFAULT_GRAVITY;
    windowParams.windowAnimations = android.R.style.Animation_Translucent;
    setParams(windowParams);

    // Listen to touch events.
    internalListener = new InternalListener();
    getRootView().setOnHoverListener(internalListener);
    getRootView().setOnTouchListener(internalListener);

    // Prepare another view which can grab touch events for the entire
    // screen during dragging.
    touchStealingView = new View(context);
    touchStealingView.setOnHoverListener(internalListener);
    touchStealingView.setOnTouchListener(internalListener);
    WindowManager.LayoutParams lp = createOverlayParams();
    lp.width = WindowManager.LayoutParams.MATCH_PARENT;
    lp.height = WindowManager.LayoutParams.MATCH_PARENT;
    lp.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL;
    touchStealingLayoutParams = lp;
  }

  private static WindowManager.LayoutParams createOverlayParams() {
    WindowManager.LayoutParams lp = new WindowManager.LayoutParams();
    lp.type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY;
    lp.format = PixelFormat.TRANSPARENT;
    lp.flags |= WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;
    lp.flags |= WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
    return lp;
  }

  private void startDragging(MotionEvent event) {
    if (dragging) {
      return;
    }

    dragging = true;
    dragOrigin = event.getRawY();
    onStartDragging();
    windowParams.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
    setParams(windowParams);
    windowManager.addView(touchStealingView, touchStealingLayoutParams);
    getRootView().performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
  }

  @SuppressLint("WrongConstant")
  private void stopDragging() {
    if (!dragging) {
      return;
    }

    dragging = false;
    windowParams.flags &= ~WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
    if (windowParams.y > touchStealingView.getHeight() / 2) {
      switch (windowParams.gravity & Gravity.VERTICAL_GRAVITY_MASK) {
        case Gravity.BOTTOM:
          windowParams.gravity &= ~Gravity.VERTICAL_GRAVITY_MASK;
          windowParams.gravity |= Gravity.TOP;
          break;
        case Gravity.TOP:
          windowParams.gravity &= ~Gravity.VERTICAL_GRAVITY_MASK;
          windowParams.gravity |= Gravity.BOTTOM;
          break;
      }
    }
    windowParams.y = 0;
    setParams(windowParams);
    windowManager.removeViewImmediate(touchStealingView);
  }

  private void cancelDragging() {
    if (!dragging) {
      return;
    }

    dragging = false;
    windowParams.flags &= ~WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
    windowParams.y = 0;
    setParams(windowParams);
    windowManager.removeViewImmediate(touchStealingView);
  }

  private void drag(MotionEvent event) {
    if (!dragging) {
      return;
    }

    switch (windowParams.gravity & Gravity.VERTICAL_GRAVITY_MASK) {
      case Gravity.BOTTOM:
        windowParams.y = (int) (dragOrigin - event.getRawY());
        break;
      case Gravity.TOP:
        windowParams.y = (int) (event.getRawY() - dragOrigin);
        break;
    }
    setParams(windowParams);
  }

  protected void onStartDragging() {
    // Intentionally left blank.
  }

  private final class InternalListener
      implements View.OnTouchListener, View.OnHoverListener, Handler.Callback {

    private static final int MSG_LONG_PRESS = 1;
    private final Handler handler = new Handler(this);
    private float touchStartX;
    private float touchStartY;

    @Override
    public boolean onHover(View view, MotionEvent event) {
      MotionEvent touchEvent = MotionEventUtils.convertHoverToTouch(event);
      return onTouch(view, touchEvent);
    }

    @Override
    public boolean onTouch(View view, MotionEvent event) {
      switch (event.getActionMasked()) {
        case MotionEvent.ACTION_DOWN:
          if (view != touchStealingView) {
            touchStartX = event.getRawX();
            touchStartY = event.getRawY();
            long timeout = (long) TAP_TIMEOUT + LONG_PRESS_TIMEOUT;
            handler.sendMessageAtTime(
                handler.obtainMessage(MSG_LONG_PRESS, event), event.getEventTime() + timeout);
          }
          break;

        case MotionEvent.ACTION_UP:
          handler.removeMessages(MSG_LONG_PRESS);
          if (view == touchStealingView) {
            stopDragging();
          }
          break;

        case MotionEvent.ACTION_CANCEL:
          handler.removeMessages(MSG_LONG_PRESS);
          cancelDragging();
          break;

        case MotionEvent.ACTION_MOVE:
          float distanceX = event.getRawX() - touchStartX;
          float distanceY = event.getRawY() - touchStartY;
          float distanceSquare = distanceX * distanceX + distanceY * distanceY;
          if (distanceSquare > touchSlopSquare) {
            handler.removeMessages(MSG_LONG_PRESS);
          }
          drag(event);
          break;
      }

      return false;
    }

    @Override
    public boolean handleMessage(Message msg) {
      switch (msg.what) {
        case MSG_LONG_PRESS:
          startDragging((MotionEvent) msg.obj);
          break;
      }
      return true;
    }
  }
}
