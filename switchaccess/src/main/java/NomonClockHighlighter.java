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

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.Handler;
import android.os.SystemClock;
import android.support.annotation.VisibleForTesting;
import android.view.animation.LinearInterpolator;
import com.google.android.accessibility.utils.SharedPreferencesUtils;
import java.util.ArrayList;
import java.util.List;

/** HighlightStrategy used to display Nomon Clocks. */
public class NomonClockHighlighter
    implements HighlightStrategy, SharedPreferences.OnSharedPreferenceChangeListener {

  private static final int MILLISECONDS_PER_SECOND = 1000;
  private static final int DEGREES_IN_CIRCLE = 360;
  private static final int NOON_POSITION_DEG = 270;
  private static final int FIRST_ACTIVE_CLOCK_GROUP_INDEX = 0;

  private final OverlayController mOverlayController;
  private final Handler mHandler;

  private List<NomonClock> mAllClocks = null;
  private List<ValueAnimator> mAnimators = null;
  private long mStartTimeMs;
  private long mGroupActiveTimeMs;
  private int mMaxNumClockGroups;
  private int mNumClockGroups;

  public NomonClockHighlighter(OverlayController overlayController) {
    mOverlayController = overlayController;
    Context context = mOverlayController.getContext();
    mHandler = new Handler();
    mAllClocks = new ArrayList<>();
    mAnimators = new ArrayList<>();
    SharedPreferences prefs = SharedPreferencesUtils.getSharedPreferences(context);
    prefs.registerOnSharedPreferenceChangeListener(this);
    onSharedPreferenceChanged(prefs, null);
  }

  @Override
  public void highlight(
      final Iterable<TreeScanLeafNode> nodes,
      final Paint highlightPaint,
      int groupIndex,
      int totalChildren) {
    mNumClockGroups = Math.min(totalChildren, mMaxNumClockGroups);
    float liveAngleRangeDegree = DEGREES_IN_CIRCLE / (float) mNumClockGroups;
    float handStartAngle = (liveAngleRangeDegree * groupIndex);
    boolean isActive = (groupIndex == FIRST_ACTIVE_CLOCK_GROUP_INDEX);
    Context context = mOverlayController.getContext();
    List<NomonClock> groupClocks = new ArrayList<>();
    for (TreeScanLeafNode node : nodes) {
      Rect rect = node.getRectForNodeHighlight();
      if (rect == null) {
        continue;
      }
      groupClocks.add(
          new NomonClock(rect, context, handStartAngle, liveAngleRangeDegree, isActive));
    }
    mAnimators.add(displayClocksAndCreateAnimation(groupClocks, groupIndex, liveAngleRangeDegree));
    mAllClocks.addAll(groupClocks);
  }

  @Override
  public void shutdown() {
    SharedPreferences prefs =
        SharedPreferencesUtils.getSharedPreferences(mOverlayController.getContext());
    prefs.unregisterOnSharedPreferenceChangeListener(this);
    cancelHighlight();
  }

  @Override
  public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
    Context context = mOverlayController.getContext();
    mMaxNumClockGroups = SwitchAccessPreferenceActivity.getNumNomonClockGroups(context);
    mGroupActiveTimeMs =
        (long)
            (SwitchAccessPreferenceActivity.getNomonClockTimeDelayMs(context)
                * MILLISECONDS_PER_SECOND);
  }

  /**
   * Get the current number of Nomon clocks.
   *
   * @return the number of visible nomon clocks.
   */
  @VisibleForTesting
  public int getNumAllClocks() {
    if (mAllClocks == null) {
      return 0;
    }
    return mAllClocks.size();
  }

  /**
   * Get the index of the active clock group at the specified time.
   *
   * @param eventTime The time for which the active clock group is requested
   * @return The index of the active clock group
   */
  public int getActiveClockGroup(long eventTime) {
    // If initialization hasn't happened yet, scanning also couldn't have started.
    if (mGroupActiveTimeMs == 0 || mNumClockGroups == 0) {
      return 0;
    }
    return (int) (((eventTime - mStartTimeMs) / mGroupActiveTimeMs) % mNumClockGroups);
  }

  /**
   * @param clocks The clocks to display and animate
   * @param clocksGroupIndex The index of this group of Nomon clocks
   * @return ValueAnimator for given clocks
   */
  private ValueAnimator displayClocksAndCreateAnimation(
      final List<NomonClock> clocks, final int clocksGroupIndex, final float liveAngleRangeDegree) {
    final ValueAnimator animator = ValueAnimator.ofFloat(0, DEGREES_IN_CIRCLE);

    mHandler.post(
        new Runnable() {
          @Override
          public void run() {
            if (!mAnimators.isEmpty()) {
              final float clockRotationalValueOffset =
                  (float)
                      (NOON_POSITION_DEG
                          - (liveAngleRangeDegree / 2.0)
                          - (liveAngleRangeDegree * clocksGroupIndex));
              animator.addUpdateListener(
                  new ValueAnimator.AnimatorUpdateListener() {
                    @Override
                    public void onAnimationUpdate(ValueAnimator valueAnimator) {
                      float animatedValue = (float) valueAnimator.getAnimatedValue();
                      float handRotationalValue = clockRotationalValueOffset + animatedValue;
                      int activeGroupIndex = (int) Math.floor(animatedValue / liveAngleRangeDegree);
                      boolean isActive = (clocksGroupIndex == activeGroupIndex);
                      for (NomonClock clock : clocks) {
                        clock.updateAnimation(handRotationalValue, isActive);
                      }
                    }
                  });
              animator.addListener(
                  new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                      mHandler.post(
                          new Runnable() {
                            @Override
                            public void run() {
                              cancelHighlight();
                            }
                          });
                    }
                  });
              animator.setRepeatCount(ValueAnimator.INFINITE);
              animator.setDuration(mNumClockGroups * mGroupActiveTimeMs);
              animator.setInterpolator(new LinearInterpolator());

              for (final NomonClock clock : clocks) {
                clock.show(mOverlayController);
              }
              animator.start();
              mStartTimeMs = SystemClock.uptimeMillis();
            }
          }
        });

    return animator;
  }

  /** Stops animation when UI changes */
  public void cancelHighlight() {
    mOverlayController.clearHighlightOverlay();
    for (ValueAnimator animator : mAnimators) {
      animator.cancel();
    }
    mAnimators.clear();
    mAllClocks.clear();
  }
}
