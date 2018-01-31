/*
 * Copyright (C) 2016 Google Inc.
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

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.animation.Animator;
import android.animation.ValueAnimator;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.RectShape;
import android.os.Build;
import android.os.Handler;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.LinearInterpolator;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import com.google.android.accessibility.switchaccess.OptionManager.ScanListener;
import com.google.android.accessibility.switchaccess.utils.GestureUtils;
import com.google.android.accessibility.utils.SharedPreferencesUtils;

/**
 * Manager for point scanning, where the entire screen is scanned in each direction so the user can
 * select a point to start performing gestures.
 */
@TargetApi(Build.VERSION_CODES.N)
public class PointScanManager implements SharedPreferences.OnSharedPreferenceChangeListener {
  public static final int ACTION_CLICK = 0;
  public static final int ACTION_LONG_CLICK = 1;
  public static final int ACTION_SWIPE_RIGHT = 2;
  public static final int ACTION_SWIPE_LEFT = 3;
  public static final int ACTION_SWIPE_UP = 4;
  public static final int ACTION_SWIPE_DOWN = 5;
  public static final int ACTION_SWIPE_CUSTOM = 6;
  public static final int ACTION_ZOOM_OUT = 7;
  public static final int ACTION_ZOOM_IN = 8;

  /** Possible directions in which scanning can occur. */
  public static enum ScanMode {
    NONE,
    X,
    Y
  };

  private static final String TAG = PointScanManager.class.getSimpleName();

  // The distance between the starting point and the ending point of a swipe gesture. Use a large
  // number, so that it will later be adjusted to move exactly to the edge of the screen. Do not
  // use MAX_VALUE so we don't get an overflow.
  private static final int SWIPE_DISTANCE = 100000000;
  // The shortest distance between two points during a pinch gesture
  private static final int PINCH_DISTANCE_CLOSE = 200;
  // The longest distance between two points during a pinch gesture
  private static final int PINCH_DISTANCE_FAR = 800;

  // The time necessary for the menu overlay to disappear
  private static final int TIME_TO_HIDE_MENU_MS = 500;

  // Time to wait before removing the menu button
  private static final int DELAY_BEFORE_REMOVING_MENU_BUTTON_MS = 600;

  // Interpolator used to move the point scan animations
  private static final LinearInterpolator LINEAR_INTERPOLATOR = new LinearInterpolator();

  // Time to wait before starting scanning. We wait this long before perfoming a gesture selected
  // from the point scan gestures menu.
  private int mDelayBeforeScanningStartsMs;

  private boolean mPointScanEnabled;
  private boolean mAutoStartScanEnabled;
  private boolean mAutoSelectEnabled;

  private final ShapeDrawable mLineDrawable;

  private final AccessibilityService mService;
  private final OverlayController mOverlayController;

  private final Handler mHandler;
  private ScanListener mScanListener = null;

  private final Runnable mStartAnimationRunnable =
      new Runnable() {
        @Override
        public void run() {
          if (!mPointScanEnabled) {
            mCurrentAnimator = null;
            return;
          }

          if (mCurrentAnimator != null) {
            mCurrentAnimator.start();
          } else {
            resetScan();
          }
        }
      };

  private final Runnable mResumeAnimationRunnable =
      new Runnable() {
        @Override
        public void run() {
          if (!mPointScanEnabled) {
            mCurrentAnimator = null;
            return;
          }

          if (mCurrentAnimator != null) {
            mCurrentAnimator.resume();
          } else {
            resetScan();
          }
        }
      };

  // The speed at which the point scanning lines move in dp/ms
  private float mLineSpeed;

  // The number of times that scanning repeats before stopping
  private int mRepeatCount;

  private ValueAnimator mCurrentAnimator;

  // The x and y coordinates selected by the user. Note: these are set only after the animation
  // has been started and cancelled.
  private int mX;
  private int mY;
  // Only used when the point scan action disambiguation menu is shown. When this menu is shown,
  // the coordinates for where the user wishes the action are stored here.
  private int mPrevX;
  private int mPrevY;

  // The direction in which we are currently scanning
  private ScanMode mScanMode = ScanMode.NONE;

  // Whether the user has selected to perform a custom swipe
  private boolean mIsPerformingCustomSwipe;

  /**
   * @param overlayController The overlay on which to show the scan bars
   * @param service The service that will dispatch the gestures
   */
  public PointScanManager(OverlayController overlayController, AccessibilityService service) {
    mOverlayController = overlayController;
    mService = service;
    mHandler = new Handler();
    mLineDrawable = new ShapeDrawable(new RectShape());
    SharedPreferences prefs = SharedPreferencesUtils.getSharedPreferences(mService);
    onSharedPreferenceChanged(prefs, null);
    prefs.registerOnSharedPreferenceChangeListener(this);
    mIsPerformingCustomSwipe = false;
  }

  /** Shut down nicely. */
  public void shutdown() {
    SharedPreferences prefs = SharedPreferencesUtils.getSharedPreferences(mService);
    prefs.unregisterOnSharedPreferenceChangeListener(this);
    mHandler.removeCallbacks(mStartAnimationRunnable);
  }

  /**
   * Register a listener to notify of scanning activity.
   *
   * @param listener The listener to be set
   */
  public void setScanListener(ScanListener listener) {
    mScanListener = listener;
  }

  /** Resets scanning, so that the next time onSelect() is called scanning restarts. */
  private void resetScan() {
    mHandler.removeCallbacks(mStartAnimationRunnable);
    mOverlayController.clearHighlightOverlay();
    mScanMode = ScanMode.NONE;

    if (mAutoStartScanEnabled || mOverlayController.isMenuVisible()) {
      onSelect();
    } else {
      // Put clearing the menu button overlay in a Handler as if the user just chose to click
      // on the menu button, we don't want to remove it until after the click has been
      // performed.
      mHandler.postDelayed(
          new Runnable() {
            @Override
            public void run() {
              if (mScanMode == ScanMode.NONE) {
                mOverlayController.clearMenuButtonOverlay();
              }
            }
          },
          DELAY_BEFORE_REMOVING_MENU_BUTTON_MS);
    }
  }

  /**
   * Handles changes to the UI. If we're not currently scanning and either auto-starting scanning is
   * enabled or a menu is visible, scanning is started.
   */
  public void onUiChanged() {
    if (mIsPerformingCustomSwipe) {
      // Do nothing.
      return;
    } else if (mScanMode == ScanMode.NONE) {
      if (mAutoStartScanEnabled || mOverlayController.isMenuVisible()) {
        onSelect();
      }
    } else if (!mOverlayController.isHighlightOverlayVisible()) {
      // If another class cleared the highlight overlay (e.g. because a user tapped the
      // Switch Access menu), we should reset scanning.
      resetScan();
    }
  }

  /** Undo the previous action. */
  public void undo() {
    // If no coordinate has been selected and a menu is visible, move to the previous menu
    // screen if one exists or remove the menu if there is no previous menu screen.
    if ((mScanMode != ScanMode.X) && mOverlayController.isMenuVisible()) {
      mOverlayController.moveToPreviousMenuItemsOrClearOverlays();
    }
    mIsPerformingCustomSwipe = false;
    resetScan();
  }

  /**
   * Indicate that the user has made a selection. Will either transition from vertical to horizontal
   * scanning or perform the specified action.
   */
  public void onSelect() {
    if ((mCurrentAnimator == null) && (mScanMode != ScanMode.NONE)) {
      // Note: This should never happen
      mScanMode = ScanMode.NONE;
      mIsPerformingCustomSwipe = false;
    }
    switch (mScanMode) {
      case NONE:
        mOverlayController.clearHighlightOverlay();
        mOverlayController.drawMenuButtonIfMenuNotVisible();
        startScan(ScanMode.Y);
        mScanMode = ScanMode.Y;
        if (mScanListener != null) {
          mScanListener.onScanStart();
        }
        break;
      case Y:
        mCurrentAnimator.cancel();
        startScan(ScanMode.X);
        mScanMode = ScanMode.X;
        if (mScanListener != null) {
          mScanListener.onScanFocusChanged();
        }
        break;
      case X:
        mCurrentAnimator.cancel();
        mCurrentAnimator = null;
        mOverlayController.clearHighlightOverlay();
        if (mIsPerformingCustomSwipe) {
          performAction(ACTION_SWIPE_CUSTOM);
          mIsPerformingCustomSwipe = false;
          /* Dispatch the gesture to (x, y) */
        } else if (mAutoSelectEnabled || mOverlayController.isMenuVisible()) {
          // Select the current point
          performAction(ACTION_CLICK);
        } else {
          // Disambiguate between possible gestures
          mOverlayController.drawMenu(PointScanMenuItem.getPointScanMenuItemList(mService, this));
          mPrevX = mX;
          mPrevY = mY;
        }
        if (mScanListener != null) {
          mScanListener.onScanSelection();
        }
        resetScan();
        break;
      default:
        // Do nothing
    }
  }

  /**
   * Start point scanning in the direction corresponding to {@code scanMode}.
   *
   * @param scanMode the current scan mode. Determines scan direction.
   */
  private void startScan(final ScanMode scanMode) {
    // Create the ValueAnimator in a separate function before assigning it to mCurrentAnimator
    // as otheriwise mCurrentAnimator becomes null after the function terminates...
    mCurrentAnimator = createAndDrawCurrentAnimator(scanMode);

    // Remove any previous callbacks so we don't restart after starting to scan, causing the
    // line to jump back to the start
    mHandler.removeCallbacks(mStartAnimationRunnable);
    // Pause before starting to scan, so the user knows when to expect scanning
    mHandler.postDelayed(mStartAnimationRunnable, mDelayBeforeScanningStartsMs);
  }

  private ValueAnimator createAndDrawCurrentAnimator(final ScanMode scanMode) {
    // Create the view that will hold the new line
    final WindowManager windowManager =
        (WindowManager) mService.getSystemService(Context.WINDOW_SERVICE);
    final Point screenSize = new Point();
    windowManager.getDefaultDisplay().getRealSize(screenSize);

    int duration;
    if (PointScanManager.ScanMode.Y == scanMode) {
      mLineDrawable.setIntrinsicWidth(screenSize.x);
      mLineDrawable.setIntrinsicHeight((int) mLineDrawable.getPaint().getStrokeWidth());
      duration = (int) (screenSize.y / mLineSpeed);
    } else if (ScanMode.X == scanMode) {
      mLineDrawable.setIntrinsicWidth((int) mLineDrawable.getPaint().getStrokeWidth());
      mLineDrawable.setIntrinsicHeight(screenSize.y);
      duration = (int) (screenSize.x / mLineSpeed);
    } else {
      // Note: This should never happen
      return null;
    }

    final View view = new View(mService);
    view.setBackground(mLineDrawable);
    final RelativeLayout.LayoutParams layoutParams =
        new RelativeLayout.LayoutParams(
            mLineDrawable.getIntrinsicWidth(), mLineDrawable.getIntrinsicHeight());
    view.setLayoutParams(layoutParams);
    // Add the view as soon as possible to minimize perceived latency for the user
    mOverlayController.addViewAndShow(view);

    // Configure the line animator
    final ValueAnimator valueAnimator = ValueAnimator.ofFloat(0.0f, 1.0f);
    if (ScanMode.Y == scanMode) {
      mY = 0;
      valueAnimator.addUpdateListener(
          new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator valueAnimator) {
              final float animatedValue = (float) valueAnimator.getAnimatedValue();
              // Move the horizontal line down as the animation progresses.
              layoutParams.topMargin = (int) (animatedValue * screenSize.y);
              view.setLayoutParams(layoutParams);
            }
          });
    } else { // ScanMode.X == scanMode
      mX = 0;
      valueAnimator.addUpdateListener(
          new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator valueAnimator) {
              final float animatedValue = (float) valueAnimator.getAnimatedValue();
              // Move the vertical line to the right as the animation progresses.
              layoutParams.leftMargin = (int) (animatedValue * screenSize.x);
              view.setLayoutParams(layoutParams);
            }
          });
    }
    valueAnimator.setDuration(duration);
    valueAnimator.setInterpolator(LINEAR_INTERPOLATOR);
    valueAnimator.setRepeatCount(mRepeatCount);
    valueAnimator.addListener(
        new Animator.AnimatorListener() {
          @Override
          public void onAnimationCancel(Animator animation) {
            if (mCurrentAnimator == null) {
              resetScan();
            }
            final WindowManager windowManager =
                (WindowManager) mService.getSystemService(Context.WINDOW_SERVICE);
            final Point screenSize = new Point();
            windowManager.getDefaultDisplay().getRealSize(screenSize);
            if (scanMode == ScanMode.Y) {
              mY = (int) (screenSize.y * (float) mCurrentAnimator.getAnimatedValue());
            } else {
              mX = (int) (screenSize.x * (float) mCurrentAnimator.getAnimatedValue());
            }
          }

          @Override
          public void onAnimationEnd(Animator animation) {
            if ((mCurrentAnimator != null) && (mCurrentAnimator.getAnimatedFraction() == 1.0)) {
              // The user didn't make a selection, so reset point scanning.
              resetScan();
              if (mScanListener != null) {
                mScanListener.onScanCompletedWithNoSelection();
              }
            }
          }

          @Override
          public void onAnimationRepeat(Animator animation) {
            if (mCurrentAnimator != null) {
              mCurrentAnimator.pause();
              mHandler.postDelayed(mResumeAnimationRunnable, mDelayBeforeScanningStartsMs);
            }
          }

          @Override
          public void onAnimationStart(Animator animation) {
            // Do nothing
          }
        });
    return valueAnimator;
  }

  /**
   * Perform a gesture at the current chosen point.
   *
   * @param scanAction the action to perform
   */
  private void performAction(int scanAction) {
    Log.v(TAG, "Performing action: " + scanAction);
    GestureDescription gesture;
    switch (scanAction) {
      case ACTION_CLICK:
        gesture = GestureUtils.createTap(mService, mX, mY);
        break;
      case ACTION_LONG_CLICK:
        gesture = GestureUtils.createLongPress(mService, mX, mY);
        break;
      case ACTION_SWIPE_RIGHT:
        gesture = GestureUtils.createSwipe(mService, mX, mY, mX + SWIPE_DISTANCE, mY);
        break;
      case ACTION_SWIPE_LEFT:
        gesture = GestureUtils.createSwipe(mService, mX, mY, mX - SWIPE_DISTANCE, mY);
        break;
      case ACTION_SWIPE_UP:
        gesture = GestureUtils.createSwipe(mService, mX, mY, mX, mY - SWIPE_DISTANCE);
        break;
      case ACTION_SWIPE_DOWN:
        gesture = GestureUtils.createSwipe(mService, mX, mY, mX, mY + SWIPE_DISTANCE);
        break;
      case ACTION_SWIPE_CUSTOM:
        gesture = GestureUtils.createSwipe(mService, mPrevX, mPrevY, mX, mY);
        break;
      case ACTION_ZOOM_OUT:
        gesture =
            GestureUtils.createPinch(mService, mX, mY, PINCH_DISTANCE_FAR, PINCH_DISTANCE_CLOSE);
        break;
      case ACTION_ZOOM_IN:
        gesture =
            GestureUtils.createPinch(mService, mX, mY, PINCH_DISTANCE_CLOSE, PINCH_DISTANCE_FAR);
        break;
      default:
        return;
    }
    mService.dispatchGesture(gesture, null, null);
  }

  /** Return the {@link View.OnClickListener} for the given scan action. */
  public View.OnClickListener createOnClickListenerForAction(final int scanAction) {
    final Runnable runnable;
    if (scanAction == ACTION_SWIPE_CUSTOM) {
      runnable = createOnClickListenerRunnableForCustomSwipe();
    } else {
      runnable = createOnClickListenerRunnableForOneStepAction(scanAction);
    }

    return new View.OnClickListener() {
      @Override
      public void onClick(View view) {
        mOverlayController.clearMenuOverlay();
        mHandler.postDelayed(runnable, TIME_TO_HIDE_MENU_MS);
      }
    };
  }

  /*
   * Create a runnable for when the user selects a cutom swipe from the point scan action menu.
   * The user has already specified the origin of the swipe, but we still need to ask the user for
   * the destination of the swipe.
   */
  private Runnable createOnClickListenerRunnableForCustomSwipe() {
    // We need the user to choose a second point before we can perform the action.
    return new Runnable() {
      @Override
      public void run() {
        mIsPerformingCustomSwipe = true;
        mScanMode = ScanMode.NONE;
        onSelect();
        drawPoint(mPrevX, mPrevY);
      }
    };
  }

  /*
   * Create a runnable for when the user selects a point scan action from the point scan action
   * menu, where this action can be perfored without asking the user for further input.
   */
  private Runnable createOnClickListenerRunnableForOneStepAction(final int scanAction) {
    return new Runnable() {
      @Override
      public void run() {
        mX = mPrevX;
        mY = mPrevY;
        performAction(scanAction);
        resetScan();
      }
    };
  }

  /*
   * Draw a point at (x, y).
   */
  private void drawPoint(int x, int y) {
    ImageView point = new ImageView(mService);
    point.setImageResource(R.drawable.ic_circle);

    int strokeWidth = (int) mLineDrawable.getPaint().getStrokeWidth() * 4;
    point.setColorFilter(mLineDrawable.getPaint().getColor());
    final RelativeLayout.LayoutParams layoutParams =
        new RelativeLayout.LayoutParams(strokeWidth, strokeWidth);
    layoutParams.leftMargin = x;
    layoutParams.topMargin = y;
    point.setLayoutParams(layoutParams);

    mOverlayController.addViewAndShow(point);
  }

  @Override
  public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
    Resources resources = mService.getResources();
    // Configure highlighting.
    String hexStringColor =
        prefs.getString(
            resources.getString(R.string.pref_highlight_0_color_key),
            resources.getString(R.string.pref_highlight_0_color_default));
    int color = Integer.parseInt(hexStringColor, 16);

    Paint linePaint = new Paint();
    linePaint.setColor(color);
    linePaint.setAlpha(255);

    String weightString =
        prefs.getString(
            resources.getString(R.string.pref_highlight_0_weight_key),
            resources.getString(R.string.pref_highlight_weight_default));
    int weight = Integer.parseInt(weightString);
    DisplayMetrics dm = mService.getResources().getDisplayMetrics();
    float strokeWidth = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, weight, dm);
    linePaint.setStrokeWidth(strokeWidth);
    mLineDrawable.getPaint().set(linePaint);

    // Read new line speed.
    mLineSpeed = SwitchAccessPreferenceActivity.getPointScanLineSpeed(mService);

    // Read the repeat count for the animation.
    mRepeatCount = SwitchAccessPreferenceActivity.getNumberOfScanningLoops(mService) - 1;

    // Read the time delay for the first item.
    mDelayBeforeScanningStartsMs = SwitchAccessPreferenceActivity.getFirstItemScanDelayMs(mService);

    // Is point scan enabled?
    mPointScanEnabled = SwitchAccessPreferenceActivity.isPointScanEnabled(mService);

    // Should we auto-start scanning?
    mAutoStartScanEnabled = SwitchAccessPreferenceActivity.isAutostartScanEnabled(mService);

    // Is auto-select enabled?
    mAutoSelectEnabled = SwitchAccessPreferenceActivity.isAutoselectEnabled(mService);
  }
}
