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
import com.google.android.accessibility.utils.BuildVersionUtils;
import com.google.android.accessibility.utils.FormFactorUtils;
import com.google.android.accessibility.utils.Performance.EventId;
import com.google.android.accessibility.utils.SharedPreferencesUtils;
import com.google.android.accessibility.utils.WeakReferenceHandler;
import com.google.android.accessibility.utils.widget.SimpleOverlay;

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

  private final AnimHandler mAnimHandler = new AnimHandler(this);
  private final FrameLayout mRootLayout;
  private final GestureDetector mGestureDetector;
  private final GestureListener mGestureListener = new GestureListener();
  private final OnDoubleTapListener mDoubleTapListener;
  private long mLastHideTime = 0;
  private int mLastTouchAction = MotionEvent.ACTION_CANCEL;
  private long mLastTouchDownTime = 0;
  private Rect mDesiredRect = null;

  public NodeBlockingOverlay(Context context, OnDoubleTapListener doubleTapListener) {
    super(context);

    final WindowManager.LayoutParams params = getParams();
    if (BuildVersionUtils.isAtLeastLMR1()) {
      params.type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY;
    } else {
      params.type = WindowManager.LayoutParams.TYPE_SYSTEM_ERROR;
    }
    params.format = PixelFormat.TRANSPARENT;
    params.flags |= WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
    params.flags |= WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;
    params.width = 0;
    params.height = 0;
    params.gravity = Gravity.LEFT | Gravity.TOP; // Tested with RTL and seems OK.
    setParams(params);

    mRootLayout = new FrameLayout(context);
    mRootLayout.setBackgroundColor(0x00000000); // Transparent black.

    mGestureDetector = new GestureDetector(context, mGestureListener);
    mDoubleTapListener = doubleTapListener;

    setContentView(mRootLayout);
  }

  /** The NodeBlockingOverlay is only supported on non-TV platforms with explore-by-touch. */
  public static boolean isSupported(TalkBackService service) {
    if (FormFactorUtils.getInstance(service).isTv()) {
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
    mAnimHandler.removeMessages(AnimHandler.WHAT_HIDE);
    mAnimHandler.removeMessages(AnimHandler.WHAT_RELAYOUT);

    mDesiredRect = r;
    if (!mAnimHandler.hasMessages(AnimHandler.WHAT_SHOW)) {
      if (SystemClock.uptimeMillis() - mLastHideTime > DELAY_HIDE_MS) {
        mAnimHandler.sendEmptyMessageDelayed(AnimHandler.WHAT_SHOW, DELAY_SHOW_MS);
      } else {
        mAnimHandler.sendEmptyMessage(AnimHandler.WHAT_SHOW);
      }
    }
  }

  /**
   * Immediately shows the overlay in the given bounds.
   *
   * @param r The bounds for the overlay.
   */
  public void show(Rect r) {
    mAnimHandler.removeCallbacksAndMessages(null);
    mDesiredRect = r;
    mAnimHandler.sendEmptyMessage(AnimHandler.WHAT_SHOW);
  }

  /**
   * Hide the overlay with a short delay in order to block a double-tap event. Use this if you need
   * to hide the overlay when the user is not touching the screen. If you don't need to block
   * double-taps, or you aren't hiding the overlay in response to the end of a touch interaction,
   * use {@link #hide()} instead.
   */
  public void hideDelayed() {
    mAnimHandler.removeMessages(AnimHandler.WHAT_SHOW);
    mAnimHandler.removeMessages(AnimHandler.WHAT_RELAYOUT);

    if (!mAnimHandler.hasMessages(AnimHandler.WHAT_HIDE)) {
      mAnimHandler.sendEmptyMessageDelayed(AnimHandler.WHAT_HIDE, DELAY_HIDE_MS);
    }
  }

  @Override
  public void hide() {
    super.hide();
    mAnimHandler.removeCallbacksAndMessages(null);
    mLastHideTime = SystemClock.uptimeMillis();
    mLastTouchAction = MotionEvent.ACTION_CANCEL;
  }

  public boolean isVisibleOrShowPending() {
    return isVisible() || mAnimHandler.hasMessages(AnimHandler.WHAT_SHOW);
  }

  public void onAccessibilityEvent(AccessibilityEvent event, EventId eventId) {
    if (event.getEventType() == AccessibilityEvent.TYPE_TOUCH_INTERACTION_START) {
      mGestureListener.clearDoubleTapOccurred();

      // EBT doesn't send an intermediate down action during a double-tap
      // (checked behavior against Android L, M, N).
      if (mLastTouchAction == MotionEvent.ACTION_DOWN) {
        MotionEvent upEvent =
            MotionEvent.obtain(
                mLastTouchDownTime,
                event.getEventTime() - DOUBLE_TAP_MIN_MS,
                MotionEvent.ACTION_UP,
                0 /* x*/,
                0 /* y */,
                0 /* metaState */);
        mGestureDetector.onTouchEvent(upEvent);
        upEvent.recycle();
      }

      // Update down time and send actual down event.
      mLastTouchDownTime = event.getEventTime();
      MotionEvent downEvent =
          MotionEvent.obtain(
              mLastTouchDownTime,
              event.getEventTime(),
              MotionEvent.ACTION_DOWN,
              0 /* x*/,
              0 /* y */,
              0 /* metaState */);
      mGestureDetector.onTouchEvent(downEvent);
      downEvent.recycle();

      mLastTouchAction = MotionEvent.ACTION_DOWN;
    } else if (event.getEventType() == AccessibilityEvent.TYPE_TOUCH_INTERACTION_END) {
      // Send actual up event using cached down time.
      MotionEvent upEvent =
          MotionEvent.obtain(
              mLastTouchDownTime,
              event.getEventTime(),
              MotionEvent.ACTION_UP,
              0 /* x*/,
              0 /* y */,
              0 /* metaState */);
      mGestureDetector.onTouchEvent(upEvent);
      upEvent.recycle();

      // Check if a double tap occurred during the last interaction.
      if (mGestureListener.getDoubleTapOccurred()) {
        mDoubleTapListener.onDoubleTap(eventId);
        mGestureListener.clearDoubleTapOccurred();
      }

      mLastTouchAction = MotionEvent.ACTION_UP;
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
     * Specifies the delta between y=0 in AccessibilityNodeInfo (mDesiredRect) and y=0 in
     * WindowManager.LayoutParams. The y-position specified in WindowManager.LayoutParams
     * corresponds to the vertical distance from the status bar (if it is visible), but the
     * y-position in AccessibilityNodeInfo always refers to the vertical distance from the top of
     * the screen. We must compensate for the error in order to accurately draw the overlay in the
     * right position; in addition, we must be cognizant that the status bar may appear and reappear
     * when switching activities.
     */
    private int mLastVerticalError = 0;

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
            lp.x = parent.mDesiredRect.left;
            lp.y = parent.mDesiredRect.top - mLastVerticalError;
            lp.width = parent.mDesiredRect.width();
            lp.height = parent.mDesiredRect.height();
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
            parent.mRootLayout.getLocationOnScreen(actual);

            WindowManager.LayoutParams lp = parent.getParams();
            mLastVerticalError += actual[1] - parent.mDesiredRect.top;
            lp.y = parent.mDesiredRect.top - mLastVerticalError;
            parent.setParams(lp);
          }
          break;
        default: // fall out
      }
    }
  }

  /** Used to detect double-taps in the NodeBlockingOverlay. */
  private static class GestureListener extends SimpleOnGestureListener {
    private boolean mDoubleTapOccurred = false;

    @Override
    public boolean onDoubleTap(MotionEvent e) {
      mDoubleTapOccurred = true;
      return super.onDoubleTap(e);
    }

    public boolean getDoubleTapOccurred() {
      return mDoubleTapOccurred;
    }

    public void clearDoubleTapOccurred() {
      mDoubleTapOccurred = false;
    }
  }
}
