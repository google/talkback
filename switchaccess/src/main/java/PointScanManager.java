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
import android.content.SharedPreferences;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.RectShape;
import android.os.Build;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.View;
import android.view.animation.LinearInterpolator;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import com.android.switchaccess.SwitchAccessService;
import com.google.android.accessibility.switchaccess.OptionManager.ScanListener;
import com.google.android.accessibility.switchaccess.OptionManager.ScanStateChangeTrigger;
import com.google.android.accessibility.switchaccess.SwitchAccessPreferenceCache.SwitchAccessPreferenceChangedListener;
import com.google.android.accessibility.switchaccess.menuitems.MenuItem.SelectMenuItemListener;
import com.google.android.accessibility.switchaccess.menuitems.MenuItemOnClickListener;
import com.google.android.accessibility.switchaccess.menuitems.PointScanMenuItem;
import com.google.android.accessibility.switchaccess.proto.SwitchAccessMenuItemEnum.MenuItem;
import com.google.android.accessibility.switchaccess.proto.SwitchAccessMenuTypeEnum.MenuType;
import com.google.android.accessibility.switchaccess.ui.OverlayController;
import com.google.android.accessibility.switchaccess.ui.OverlayController.MenuListener;
import com.google.android.libraries.accessibility.utils.concurrent.ThreadUtils;
import com.google.android.libraries.accessibility.utils.device.ScreenUtils;
import com.google.android.libraries.accessibility.utils.input.GestureUtils;
import com.google.android.libraries.accessibility.utils.log.LogUtils;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Manager for point scanning, where the entire screen is scanned in each direction so the user can
 * select a point to start performing gestures.
 */
@TargetApi(Build.VERSION_CODES.N)
public class PointScanManager implements SwitchAccessPreferenceChangedListener, MenuListener {
  /** Possible actions that can be performed with point scanning. */
  public enum PointScanAction {
    ACTION_CLICK,
    ACTION_LONG_CLICK,
    ACTION_SWIPE_RIGHT,
    ACTION_SWIPE_LEFT,
    ACTION_SWIPE_UP,
    ACTION_SWIPE_DOWN,
    ACTION_SWIPE_CUSTOM,
    ACTION_ZOOM_OUT,
    ACTION_ZOOM_IN
  }

  /** Possible directions in which scanning can occur. */
  public enum ScanMode {
    NONE,
    X,
    Y
  }

  private static final String TAG = "PointScanManager";

  // Keep track of whether the scan just completed so that we don't automatically start rescanning
  // each time a scan finishes - this would cause point scan to continue scanning indefinitely.
  private boolean scanFinished = false;

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

  private final ShapeDrawable lineDrawable;

  private final AccessibilityService service;
  private final OverlayController overlayController;

  @Nullable private ScanListener scanListener;
  private PointScanListener pointScanListener;

  private final Runnable startAnimationRunnable =
      new Runnable() {
        @Override
        public void run() {
          if (!SwitchAccessPreferenceUtils.isPointScanEnabled(overlayController.getContext())) {
            currentAnimator = null;
            return;
          }

          if (currentAnimator != null) {
            currentAnimator.start();
          } else {
            resetScan();
          }
        }
      };

  private final Runnable resumeAnimationRunnable =
      new Runnable() {
        @Override
        public void run() {
          if (!SwitchAccessPreferenceUtils.isPointScanEnabled(overlayController.getContext())) {
            currentAnimator = null;
            return;
          }

          if (currentAnimator != null) {
            currentAnimator.resume();
          } else {
            resetScan();
          }
        }
      };

  @Nullable private ValueAnimator currentAnimator;

  // The x and y coordinates selected by the user. Note: these are set only after the animation
  // has been started and cancelled.
  private int x;
  private int y;
  // Only used when the point scan action disambiguation menu is shown. When this menu is shown,
  // the coordinates for where the user wishes the action are stored here.
  private int prevX;
  private int prevY;

  // The direction in which we are currently scanning
  private ScanMode scanMode = ScanMode.NONE;

  // Whether the user has selected to perform a custom swipe
  private boolean isPerformingCustomSwipe;

  @Nullable private SelectMenuItemListener selectMenuItemListener;

  // Indicates if the Switch Access global menu button was just clicked. Used for the new menu
  // redesign to keep the menu button on screen when it's selected. The new menu is placed next to
  // the selected item, with everything darkened in the background except the selected item. So,
  // when the menu button is selected, we want to keep the menu button visible. Otherwise, the
  // global menu would show up and there would be a non-darkened patch in the background with
  // nothing inside it.
  private boolean globalMenuButtonJustClicked;

  /**
   * @param overlayController The overlay on which to show the scan bars
   * @param service The service that will dispatch the gestures
   */
  public PointScanManager(OverlayController overlayController, AccessibilityService service) {
    this.overlayController = overlayController;
    this.service = service;
    lineDrawable = new ShapeDrawable(new RectShape());
    isPerformingCustomSwipe = false;
    SwitchAccessPreferenceUtils.registerSwitchAccessPreferenceChangedListener(service, this);
    overlayController.addMenuListener(this);
  }

  /** Shut down nicely. */
  public void shutdown() {
    SwitchAccessPreferenceUtils.unregisterSwitchAccessPreferenceChangedListener(this);
    ThreadUtils.removeCallbacks(startAnimationRunnable);
  }

  /**
   * Register a listener to notify of scanning activity.
   *
   * @param listener The listener to be set
   */
  public void setScanListener(@Nullable ScanListener listener) {
    scanListener = listener;
  }

  /**
   * Register a listener to notify of point scan actions.
   *
   * @param listener The listener to be set
   */
  public void setPointScanListener(PointScanListener listener) {
    pointScanListener = listener;
  }

  /**
   * Register a listener to notify of the selection of a Switch Access menu item.
   *
   * @param listener The listener to be set
   */
  public void setSelectMenuItemListener(@Nullable SelectMenuItemListener listener) {
    selectMenuItemListener = listener;
  }

  @Override
  public void onMenuShown(MenuType menuType, int menuId) {
    globalMenuButtonJustClicked = (menuType == MenuType.TYPE_GLOBAL);
  }

  @Override
  public void onMenuClosed(int menuId) {
    globalMenuButtonJustClicked = false;
  }

  /** Resets scanning, so that the next time onSelect() is called scanning restarts. */
  private void resetScan() {
    ThreadUtils.removeCallbacks(startAnimationRunnable);
    overlayController.clearHighlightOverlay();
    scanMode = ScanMode.NONE;

    if (SwitchAccessPreferenceUtils.isAutostartScanEnabled(overlayController.getContext())
        && !scanFinished) {
      onSelect(ScanStateChangeTrigger.FEATURE_AUTO_START_SCAN);
    } else if (!overlayController.isMenuVisible()) {
      // Put clearing the menu button overlay in a Handler as if the user just chose to click
      // on the menu button, we don't want to remove it until after the click has been
      // performed.
      ThreadUtils.runOnMainThreadDelayed(
          SwitchAccessService::isActive,
          () -> {
            // Ensure that scanning has not resumed before clearing the menu button overlay.
            if ((scanMode == ScanMode.NONE) && !globalMenuButtonJustClicked) {
              overlayController.clearMenuButtonOverlay();
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
    // #onUiChanged is called when point scan finishes and the Switch Access menu button disappears.
    // Because of this, we should prevent point scan from resuming immediately after it has ended.
    boolean shouldPreventAutoStartScan = scanFinished;
    scanFinished = false;
    if (isPerformingCustomSwipe) {
      // Do nothing.
      return;
    } else if (scanMode == ScanMode.NONE) {
      // If we just finished scanning, don't automatically restart the scan in order to honor
      // the selected number of scanning loops.
      if (SwitchAccessPreferenceUtils.isAutostartScanEnabled(overlayController.getContext())
          && !shouldPreventAutoStartScan) {
        onSelect(ScanStateChangeTrigger.FEATURE_AUTO_START_SCAN);
      } else if (overlayController.isMenuVisible()) {
        onSelect(ScanStateChangeTrigger.FEATURE_POINT_SCAN_MENU);
      }
    } else if (!overlayController.isHighlightOverlayVisible()) {
      // If another class cleared the highlight overlay (e.g. because a user tapped the
      // Switch Access menu), we should reset scanning.
      resetScan();
    }
  }

  /** Undo the previous action. */
  public void undo() {
    // If no coordinate has been selected and a menu is visible, move to the previous menu
    // screen if one exists or remove the menu if there is no previous menu screen.
    if ((scanMode != ScanMode.X) && overlayController.isMenuVisible()) {
      overlayController.moveToPreviousMenuItems();
    }
    isPerformingCustomSwipe = false;
    resetScan();
  }

  /**
   * Indicate that the user has made a selection. Will either transition from vertical to horizontal
   * scanning or perform the specified action.
   */
  public void onSelect(ScanStateChangeTrigger trigger) {
    if ((currentAnimator == null) && (scanMode != ScanMode.NONE)) {
      // Note: This should never happen
      scanMode = ScanMode.NONE;
      isPerformingCustomSwipe = false;
    }
    switch (scanMode) {
      case NONE:
        overlayController.clearHighlightOverlay();
        overlayController.drawMenuButtonIfMenuNotVisible();
        startScan(ScanMode.Y);
        scanMode = ScanMode.Y;
        if (scanListener != null) {
          scanListener.onScanStart(trigger);
        }
        break;
      case Y:
        // Ignore any switch presses changing from Y-scan to X-scan if the highlight is not yet
        // visible. Otherwise, it is possible for the Y-scan line to become invisible.
        if (!overlayController.isHighlightOverlayVisible()) {
          return;
        }

        cancelCurrentAnimator();
        startScan(ScanMode.X);
        scanMode = ScanMode.X;
        if (scanListener != null) {
          scanListener.onScanFocusChanged(trigger);
        }
        break;
      case X:
        cancelCurrentAnimator();
        currentAnimator = null;
        overlayController.clearHighlightOverlay();
        if (isPerformingCustomSwipe) {
          performAction(PointScanAction.ACTION_SWIPE_CUSTOM);
          isPerformingCustomSwipe = false;
          /* Dispatch the gesture to (x, y) */
        } else if (SwitchAccessPreferenceUtils.isAutoselectEnabled(overlayController.getContext())
            || overlayController.isMenuVisible()) {
          // Select the current point
          performAction(PointScanAction.ACTION_CLICK);
        } else {
          // Disambiguate between possible gestures
          overlayController.drawMenu(
              PointScanMenuItem.getPointScanMenuItemList(service, this), new Rect(x, y, x, y));
          prevX = x;
          prevY = y;
        }
        if (scanListener != null) {
          scanListener.onScanSelection(trigger);
        }
        resetScan();
        break;
    }
  }

  // This method is called only in #onSelect. The logic is too complicated for the null checker to
  // figure out that this is only called if currentAnimator is not null. This is the easiest way to
  // contain the suppression such that the entire #onSelect method isn't suppressed.
  @SuppressWarnings("nullness:dereference.of.nullable")
  private void cancelCurrentAnimator() {
    if (currentAnimator == null) {
      LogUtils.w(
          TAG,
          "currentAnimator was null! Please ensure currentAnimator is not null before calling "
              + "this method.");
    }
    currentAnimator.cancel();
  }

  /**
   * Start point scanning in the direction corresponding to {@code scanMode}.
   *
   * @param scanMode the current scan mode. Determines scan direction.
   */
  private void startScan(final ScanMode scanMode) {
    // Create the ValueAnimator in a separate function before assigning it to currentAnimator
    // as otherwise currentAnimator becomes null after the function terminates...
    currentAnimator = createAndDrawCurrentAnimator(scanMode);

    // Remove any previous callbacks so we don't restart after starting to scan, causing the
    // line to jump back to the start
    ThreadUtils.removeCallbacks(startAnimationRunnable);
    // Pause before starting to scan, so the user knows when to expect scanning
    ThreadUtils.runOnMainThreadDelayed(
        SwitchAccessService::isActive,
        startAnimationRunnable,
        SwitchAccessPreferenceUtils.getFirstItemScanDelayMs(overlayController.getContext()));
  }

  private ValueAnimator createAndDrawCurrentAnimator(final ScanMode scanMode) {
    // Create the view that will hold the new line
    final Point screenSize = ScreenUtils.getRealScreenSize(service);

    int duration = 0;
    switch (scanMode) {
      case Y:
        lineDrawable.setIntrinsicWidth(screenSize.x);
        lineDrawable.setIntrinsicHeight((int) lineDrawable.getPaint().getStrokeWidth());
        duration =
            (int)
                (screenSize.y
                    / SwitchAccessPreferenceUtils.getPointScanLineSpeed(
                        overlayController.getContext()));
        break;
      case X:
        lineDrawable.setIntrinsicWidth((int) lineDrawable.getPaint().getStrokeWidth());
        lineDrawable.setIntrinsicHeight(screenSize.y);
        duration =
            (int)
                (screenSize.x
                    / SwitchAccessPreferenceUtils.getPointScanLineSpeed(
                        overlayController.getContext()));
        break;
      case NONE:
        // Note: This should never happen
        if (FeatureFlags.crashOnError()) {
          throw new IllegalArgumentException(
              "scanMode should not be NONE during #createAndDrawCurrentAnimator");
        }
    }

    final View view = new View(service);
    view.setBackground(lineDrawable);
    final RelativeLayout.LayoutParams layoutParams =
        new RelativeLayout.LayoutParams(
            lineDrawable.getIntrinsicWidth(), lineDrawable.getIntrinsicHeight());
    view.setLayoutParams(layoutParams);
    // Add the view as soon as possible to minimize perceived latency for the user
    overlayController.addViewAndShow(view);

    // Configure the line animator
    final ValueAnimator valueAnimator = ValueAnimator.ofFloat(0.0f, 1.0f);
    if (ScanMode.Y == scanMode) {
      y = 0;
      valueAnimator.addUpdateListener(
          animatorUpdateListenerValueAnimator -> {
            final float animatedValue =
                (float) animatorUpdateListenerValueAnimator.getAnimatedValue();
            // Move the horizontal line down as the animation progresses.
            layoutParams.topMargin = (int) (animatedValue * screenSize.y);
            view.setLayoutParams(layoutParams);
          });
    } else { // ScanMode.X == scanMode
      x = 0;
      valueAnimator.addUpdateListener(
          animatorUpdateListenerValueAnimator -> {
            final float animatedValue =
                (float) animatorUpdateListenerValueAnimator.getAnimatedValue();
            // Move the vertical line to the right as the animation progresses.
            layoutParams.leftMargin = (int) (animatedValue * screenSize.x);
            view.setLayoutParams(layoutParams);
          });
    }
    valueAnimator.setDuration(duration);
    valueAnimator.setInterpolator(LINEAR_INTERPOLATOR);
    valueAnimator.setRepeatCount(
        SwitchAccessPreferenceUtils.getNumberOfScanningLoops(overlayController.getContext()) - 1);
    valueAnimator.addListener(
        new Animator.AnimatorListener() {
          @Override
          public void onAnimationCancel(Animator animation) {
            if (currentAnimator == null) {
              if (FeatureFlags.crashOnError()) {
                throw new NullPointerException(
                    "currentAnimator became null before it could be cancelled.");
              } else {
                resetScan();
                return;
              }
            }
            Point screenSize = ScreenUtils.getRealScreenSize(service);
            if (scanMode == ScanMode.Y) {
              y = (int) (screenSize.y * (float) currentAnimator.getAnimatedValue());
            } else {
              x = (int) (screenSize.x * (float) currentAnimator.getAnimatedValue());
            }
          }

          @Override
          public void onAnimationEnd(Animator animation) {
            if ((currentAnimator != null) && (currentAnimator.getAnimatedFraction() == 1.0)) {
              // The user didn't make a selection, so reset point scanning.
              scanFinished = true;
              resetScan();
              if (scanListener != null) {
                scanListener.onScanFocusClearedAtEndWithNoSelection(
                    ScanStateChangeTrigger.FEATURE_POINT_SCAN);
              }
            }
          }

          @Override
          public void onAnimationRepeat(Animator animation) {
            if (currentAnimator != null) {
              currentAnimator.pause();
              ThreadUtils.runOnMainThreadDelayed(
                  SwitchAccessService::isActive,
                  resumeAnimationRunnable,
                  SwitchAccessPreferenceUtils.getFirstItemScanDelayMs(
                      overlayController.getContext()));
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
  // Even though AccessibilityService#dispatchGesture is annotated as @Nullable for its second and
  // third parameters, the checker thinks it only accepts non-null.
  @SuppressWarnings("nullness:argument.type.incompatible")
  private void performAction(PointScanAction scanAction) {
    LogUtils.v(TAG, "Performing action: " + scanAction);
    GestureDescription gesture;
    switch (scanAction) {
      case ACTION_CLICK:
        gesture = GestureUtils.createTap(service, x, y);
        break;
      case ACTION_LONG_CLICK:
        gesture = GestureUtils.createLongPress(service, x, y);
        break;
      case ACTION_SWIPE_RIGHT:
        gesture = GestureUtils.createSwipe(service, x, y, x + SWIPE_DISTANCE, y);
        break;
      case ACTION_SWIPE_LEFT:
        gesture = GestureUtils.createSwipe(service, x, y, x - SWIPE_DISTANCE, y);
        break;
      case ACTION_SWIPE_UP:
        gesture = GestureUtils.createSwipe(service, x, y, x, y - SWIPE_DISTANCE);
        break;
      case ACTION_SWIPE_DOWN:
        gesture = GestureUtils.createSwipe(service, x, y, x, y + SWIPE_DISTANCE);
        break;
      case ACTION_SWIPE_CUSTOM:
        gesture = GestureUtils.createSwipe(service, prevX, prevY, x, y);
        break;
      case ACTION_ZOOM_OUT:
        gesture = GestureUtils.createPinch(service, x, y, PINCH_DISTANCE_FAR, PINCH_DISTANCE_CLOSE);
        break;
      case ACTION_ZOOM_IN:
        gesture = GestureUtils.createPinch(service, x, y, PINCH_DISTANCE_CLOSE, PINCH_DISTANCE_FAR);
        break;
      default:
        return;
    }
    service.dispatchGesture(gesture, null, null);
    if (pointScanListener != null) {
      pointScanListener.onActionPerformed();
    }
  }

  /** Return the {@link MenuItemOnClickListener} for the given scan action. */
  public MenuItemOnClickListener createOnClickListenerForAction(final PointScanAction scanAction) {
    final Runnable runnable;
    if (scanAction == PointScanAction.ACTION_SWIPE_CUSTOM) {
      runnable = createOnClickListenerRunnableForCustomSwipe();
    } else {
      runnable = createOnClickListenerRunnableForOneStepAction(scanAction);
    }

    return new MenuItemOnClickListener() {
      @Override
      public void onClick() {
        ThreadUtils.runOnMainThreadDelayed(
            SwitchAccessService::isActive, runnable, TIME_TO_HIDE_MENU_MS);

        if (selectMenuItemListener != null) {
          switch (scanAction) {
            case ACTION_CLICK:
              selectMenuItemListener.onMenuItemSelected(MenuItem.ACTION_MENU_POINT_SCAN_CLICK);
              break;
            case ACTION_LONG_CLICK:
              selectMenuItemListener.onMenuItemSelected(MenuItem.ACTION_MENU_POINT_SCAN_LONG_CLICK);
              break;
            case ACTION_SWIPE_LEFT:
              selectMenuItemListener.onMenuItemSelected(MenuItem.ACTION_MENU_POINT_SCAN_SWIPE_LEFT);
              break;
            case ACTION_SWIPE_RIGHT:
              selectMenuItemListener.onMenuItemSelected(
                  MenuItem.ACTION_MENU_POINT_SCAN_SWIPE_RIGHT);
              break;
            case ACTION_SWIPE_UP:
              selectMenuItemListener.onMenuItemSelected(MenuItem.ACTION_MENU_POINT_SCAN_SWIPE_UP);
              break;
            case ACTION_SWIPE_DOWN:
              selectMenuItemListener.onMenuItemSelected(MenuItem.ACTION_MENU_POINT_SCAN_SWIPE_DOWN);
              break;
            case ACTION_SWIPE_CUSTOM:
              selectMenuItemListener.onMenuItemSelected(
                  MenuItem.ACTION_MENU_POINT_SCAN_SWIPE_CUSTOM);
              break;
            case ACTION_ZOOM_IN:
              selectMenuItemListener.onMenuItemSelected(MenuItem.ACTION_MENU_POINT_SCAN_ZOOM_IN);
              break;
            case ACTION_ZOOM_OUT:
              selectMenuItemListener.onMenuItemSelected(MenuItem.ACTION_MENU_POINT_SCAN_ZOOM_OUT);
          }
        }
      }
    };
  }

  /*
   * Create a runnable for when the user selects a custom swipe from the point scan action menu.
   * The user has already specified the origin of the swipe, but we still need to ask the user for
   * the destination of the swipe.
   */
  private Runnable createOnClickListenerRunnableForCustomSwipe() {
    // We need the user to choose a second point before we can perform the action.
    return () -> {
      isPerformingCustomSwipe = true;
      scanMode = ScanMode.NONE;
      onSelect(ScanStateChangeTrigger.FEATURE_POINT_SCAN_CUSTOM_SWIPE);
      drawPoint(prevX, prevY);
    };
  }

  /*
   * Create a runnable for when the user selects a point scan action from the point scan action
   * menu, where this action can be performed without asking the user for further input.
   */
  private Runnable createOnClickListenerRunnableForOneStepAction(final PointScanAction scanAction) {
    return () -> {
      x = prevX;
      y = prevY;
      performAction(scanAction);
      resetScan();
    };
  }

  /*
   * Draw a point at (x, y).
   */
  private void drawPoint(int x, int y) {
    ImageView point = new ImageView(service);
    point.setImageResource(R.drawable.ic_circle);

    int strokeWidth = (int) (lineDrawable.getPaint().getStrokeWidth() * 4);
    point.setColorFilter(lineDrawable.getPaint().getColor());
    final RelativeLayout.LayoutParams layoutParams =
        new RelativeLayout.LayoutParams(strokeWidth, strokeWidth);
    layoutParams.leftMargin = x;
    layoutParams.topMargin = y;
    point.setLayoutParams(layoutParams);

    overlayController.addViewAndShow(point);
  }

  /** Notify that preferences have changed. */
  @Override
  public void onPreferenceChanged(SharedPreferences sharedPreferences, String preferenceKey) {
    // Configure highlighting.
    String hexStringColor =
        SwitchAccessPreferenceUtils.getHighlightColorForScanningMethodsWithOneHighlight(service);
    int color = Integer.parseInt(hexStringColor, 16);

    Paint linePaint = new Paint();
    linePaint.setColor(color);
    linePaint.setAlpha(255);

    int weight =
        Integer.parseInt(
            SwitchAccessPreferenceUtils.getHighlightWeightForScanningMethodsWithOneHighlight(
                service));
    DisplayMetrics dm = service.getResources().getDisplayMetrics();
    float strokeWidth = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, weight, dm);
    linePaint.setStrokeWidth(strokeWidth);
    lineDrawable.getPaint().set(linePaint);

    // TODO: Ensure that line speed changes are properly tested once PointScanManager
    // and its tests are refactored to improve testability.
    // If the line speed changes in the middle of scanning, continue the scan with the new line
    // speed.
    if (scanMode != ScanMode.NONE
        && preferenceKey.contentEquals(
            service.getString(R.string.pref_key_point_scan_line_speed))) {
      // Checking if currentAnimator is null needs to be moved to a separate if-statement in order
      // to make the null checker happy.
      if (currentAnimator != null) {
        float duration = currentAnimator.getDuration();
        Point screenSize = ScreenUtils.getRealScreenSize(service);
        if (scanMode == ScanMode.Y) {
          duration =
              (screenSize.y
                  / SwitchAccessPreferenceUtils.getPointScanLineSpeed(
                      overlayController.getContext()));
        } else if (scanMode == ScanMode.X) {
          duration =
              (screenSize.x
                  / SwitchAccessPreferenceUtils.getPointScanLineSpeed(
                      overlayController.getContext()));
        }

        // An extra check to appease the null-checker.
        if (currentAnimator != null) {
          currentAnimator.setDuration((int) duration);
        }
      }
    }
  }

  /** Interface to monitor point scan events. */
  public interface PointScanListener {

    /** Called when a point scan action is performed. */
    void onActionPerformed();
  }
}
