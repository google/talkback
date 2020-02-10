/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.google.android.accessibility.talkback;

/**
 * An overlay window that blocks nodes from receiving explore-by-touch interaction, allowing
 * TalkBack to intercept and act upon the touch interaction.
 */

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.os.Message;
import android.os.SystemClock;
import android.view.GestureDetector;
import android.view.GestureDetector.SimpleOnGestureListener;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.widget.FrameLayout;
import com.google.android.accessibility.utils.FeatureSupport;
import com.google.android.accessibility.utils.Performance.EventId;
import com.google.android.accessibility.utils.SharedPreferencesUtils;
import com.google.android.accessibility.utils.WeakReferenceHandler;
import com.google.android.accessibility.utils.widget.DialogUtils;
import com.google.android.accessibility.utils.widget.SimpleOverlay;
import org.checkerframework.checker.nullness.qual.Nullable;

/** Overlay to block double-taps on an EditText in explore-by-touch mode. */
public class NodeBlockingOverlay extends SimpleOverlay {

  private static final int DOUBLE_TAP_MAX_MS = ViewConfiguration.getDoubleTapTimeout();

  private static final int DOUBLE_TAP_MIN_MS = 40;

  /**
   * The overlay must be visible at least this long following the end of any touch interaction in
   * order to catch double taps.
   */
  private static final int DELAY_HIDE_MS = DOUBLE_TAP_MAX_MS;

  /**
   * The overlay must appear after this time following the start of any touch interaction. Shorter
   * times will cause more scroll interactions to be accidentally blocked; longer times will cause
   * more double-taps to go through unblocked. Empirical testing shows that this must be greater
   * than {@link #DOUBLE_TAP_MIN_MS} to accomplish both goals.
   */
  private static final int DELAY_SHOW_MS = (int) (DOUBLE_TAP_MIN_MS * 1.5);

  private final AnimHandler animHandler = new AnimHandler(this);
  private final FrameLayout rootLayout;
  private final GestureDetector gestureDetector;
  private final GestureListener gestureListener = new GestureListener();
  private final OnDoubleTapListener doubleTapListener;
  private long lastHideTime = 0;
  private int lastTouchAction = MotionEvent.ACTION_CANCEL;
  private long lastTouchDownTime = 0;
  @Nullable private Rect desiredRect = null;

  public NodeBlockingOverlay(Context context, OnDoubleTapListener doubleTapListener) {
    super(context);

    final WindowManager.LayoutParams params = getParams();
    params.type = DialogUtils.getDialogType();
    params.format = PixelFormat.TRANSPARENT;
    params.flags |= WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
    params.flags |= WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;
    params.width = 0;
    params.height = 0;
    params.gravity = Gravity.LEFT | Gravity.TOP; // Tested with RTL and seems OK.
    setParams(params);

    rootLayout = new FrameLayout(context);
    rootLayout.setBackgroundColor(0x00000000); // Transparent black.

    gestureDetector = new GestureDetector(context, gestureListener);
    this.doubleTapListener = doubleTapListener;

    setContentView(rootLayout);
  }

  /** The NodeBlockingOverlay is only supported on non-TV platforms with explore-by-touch. */
  public static boolean isSupported(TalkBackService service) {
    if (FeatureSupport.isTv(service)) {
      return false;
    }

    SharedPreferences prefs = SharedPreferencesUtils.getSharedPreferences(service);
    if (!SharedPreferencesUtils.getBooleanPref(
        prefs,
        service.getResources(),
        R.string.pref_explore_by_touch_key,
        R.bool.pref_explore_by_touch_default)) {
      return false;
    }

    return true;
  }

  /**
   * Shows the overlay with a short delay in order to prevent capturing scroll interactions. If you
   * are not worried about scroll interactions, you can simply use the {@link #show(Rect)} method.
   *
   * @param r The bounds for the overlay.
   */
  public void showDelayed(Rect r) {
    animHandler.removeMessages(AnimHandler.WHAT_HIDE);
    animHandler.removeMessages(AnimHandler.WHAT_RELAYOUT);

    desiredRect = r;
    if (!animHandler.hasMessages(AnimHandler.WHAT_SHOW)) {
      if (SystemClock.uptimeMillis() - lastHideTime > DELAY_HIDE_MS) {
        animHandler.sendEmptyMessageDelayed(AnimHandler.WHAT_SHOW, DELAY_SHOW_MS);
      } else {
        animHandler.sendEmptyMessage(AnimHandler.WHAT_SHOW);
      }
    }
  }

  /**
   * Immediately shows the overlay in the given bounds.
   *
   * @param r The bounds for the overlay.
   */
  public void show(Rect r) {
    animHandler.removeCallbacksAndMessages(null);
    desiredRect = r;
    animHandler.sendEmptyMessage(AnimHandler.WHAT_SHOW);
  }

  /**
   * Hide the overlay with a short delay in order to block a double-tap event. Use this if you need
   * to hide the overlay when the user is not touching the screen. If you don't need to block
   * double-taps, or you aren't hiding the overlay in response to the end of a touch interaction,
   * use {@link #hide()} instead.
   */
  public void hideDelayed() {
    animHandler.removeMessages(AnimHandler.WHAT_SHOW);
    animHandler.removeMessages(AnimHandler.WHAT_RELAYOUT);

    if (!animHandler.hasMessages(AnimHandler.WHAT_HIDE)) {
      animHandler.sendEmptyMessageDelayed(AnimHandler.WHAT_HIDE, DELAY_HIDE_MS);
    }
  }

  @Override
  public void hide() {
    super.hide();
    animHandler.removeCallbacksAndMessages(null);
    lastHideTime = SystemClock.uptimeMillis();
    lastTouchAction = MotionEvent.ACTION_CANCEL;
  }

  public boolean isVisibleOrShowPending() {
    return isVisible() || animHandler.hasMessages(AnimHandler.WHAT_SHOW);
  }

  public void onAccessibilityEvent(AccessibilityEvent event, EventId eventId) {
    if (event.getEventType() == AccessibilityEvent.TYPE_TOUCH_INTERACTION_START) {
      gestureListener.clearDoubleTapOccurred();

      // EBT doesn't send an intermediate down action during a double-tap
      // (checked behavior against Android L, M, N).
      if (lastTouchAction == MotionEvent.ACTION_DOWN) {
        MotionEvent upEvent =
            MotionEvent.obtain(
                lastTouchDownTime,
                event.getEventTime() - DOUBLE_TAP_MIN_MS,
                MotionEvent.ACTION_UP,
                0 /* x*/,
                0 /* y */,
                0 /* metaState */);
        gestureDetector.onTouchEvent(upEvent);
        upEvent.recycle();
      }

      // Update down time and send actual down event.
      lastTouchDownTime = event.getEventTime();
      MotionEvent downEvent =
          MotionEvent.obtain(
              lastTouchDownTime,
              event.getEventTime(),
              MotionEvent.ACTION_DOWN,
              0 /* x*/,
              0 /* y */,
              0 /* metaState */);
      gestureDetector.onTouchEvent(downEvent);
      downEvent.recycle();

      lastTouchAction = MotionEvent.ACTION_DOWN;
    } else if (event.getEventType() == AccessibilityEvent.TYPE_TOUCH_INTERACTION_END) {
      // Send actual up event using cached down time.
      MotionEvent upEvent =
          MotionEvent.obtain(
              lastTouchDownTime,
              event.getEventTime(),
              MotionEvent.ACTION_UP,
              0 /* x*/,
              0 /* y */,
              0 /* metaState */);
      gestureDetector.onTouchEvent(upEvent);
      upEvent.recycle();

      // Check if a double tap occurred during the last interaction.
      if (gestureListener.getDoubleTapOccurred()) {
        doubleTapListener.onDoubleTap(eventId);
        gestureListener.clearDoubleTapOccurred();
      }

      lastTouchAction = MotionEvent.ACTION_UP;
    }
  }

  public interface OnDoubleTapListener {
    void onDoubleTap(EventId eventId);
  }

  /** Used to handle delayed show/hide in the NodeBlockingOverlay. */
  private static class AnimHandler extends WeakReferenceHandler<NodeBlockingOverlay> {
    public static final int WHAT_HIDE = 1;
    public static final int WHAT_SHOW = 2;
    public static final int WHAT_RELAYOUT = 3;

    /**
     * Specifies the delta between y=0 in AccessibilityNodeInfo (desiredRect) and y=0 in
     * WindowManager.LayoutParams. The y-position specified in WindowManager.LayoutParams
     * corresponds to the vertical distance from the status bar (if it is visible), but the
     * y-position in AccessibilityNodeInfo always refers to the vertical distance from the top of
     * the screen. We must compensate for the error in order to accurately draw the overlay in the
     * right position; in addition, we must be cognizant that the status bar may appear and reappear
     * when switching activities.
     */
    private int lastVerticalError = 0;

    public AnimHandler(NodeBlockingOverlay parent) {
      super(parent);
    }

    @Override
    protected void handleMessage(Message msg, final NodeBlockingOverlay parent) {
      switch (msg.what) {
        case WHAT_HIDE:
          {
            parent.hide();
          }
          break;
        case WHAT_SHOW:
          {
            // Shows the overlay. However, we need to wait until the overlay is
            // shown before doing the final layout (we may need to compensate for the
            // status bar height).
            WindowManager.LayoutParams lp = parent.getParams();
            lp.x = parent.desiredRect.left;
            lp.y = parent.desiredRect.top - lastVerticalError;
            lp.width = parent.desiredRect.width();
            lp.height = parent.desiredRect.height();
            parent.setParams(lp);
            parent.show();
            sendEmptyMessage(WHAT_RELAYOUT);
          }
          break;
        case WHAT_RELAYOUT:
          {
            // Does final relayout. Our LayoutParams position on the screen needs to be
            // adjusted to compensate for the status bar height.
            int[] actual = new int[2];
            parent.rootLayout.getLocationOnScreen(actual);

            WindowManager.LayoutParams lp = parent.getParams();
            lastVerticalError += actual[1] - parent.desiredRect.top;
            lp.y = parent.desiredRect.top - lastVerticalError;
            parent.setParams(lp);
          }
          break;
        default: // fall out
      }
    }
  }

  /** Used to detect double-taps in the NodeBlockingOverlay. */
  private static class GestureListener extends SimpleOnGestureListener {
    private boolean doubleTapOccurred = false;

    @Override
    public boolean onDoubleTap(MotionEvent e) {
      doubleTapOccurred = true;
      return super.onDoubleTap(e);
    }

    public boolean getDoubleTapOccurred() {
      return doubleTapOccurred;
    }

    public void clearDoubleTapOccurred() {
      doubleTapOccurred = false;
    }
  }
}
