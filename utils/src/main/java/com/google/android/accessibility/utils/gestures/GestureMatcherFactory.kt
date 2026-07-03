/*
 * Copyright (C) The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Ported from Java to Kotlin.
 */

package com.google.android.accessibility.utils.gestures

import android.accessibilityservice.AccessibilityService.GESTURE_2_FINGER_DOUBLE_TAP
import android.accessibilityservice.AccessibilityService.GESTURE_2_FINGER_DOUBLE_TAP_AND_HOLD
import android.accessibilityservice.AccessibilityService.GESTURE_2_FINGER_SINGLE_TAP
import android.accessibilityservice.AccessibilityService.GESTURE_2_FINGER_SWIPE_DOWN
import android.accessibilityservice.AccessibilityService.GESTURE_2_FINGER_SWIPE_LEFT
import android.accessibilityservice.AccessibilityService.GESTURE_2_FINGER_SWIPE_RIGHT
import android.accessibilityservice.AccessibilityService.GESTURE_2_FINGER_SWIPE_UP
import android.accessibilityservice.AccessibilityService.GESTURE_2_FINGER_TRIPLE_TAP
import android.accessibilityservice.AccessibilityService.GESTURE_2_FINGER_TRIPLE_TAP_AND_HOLD
import android.accessibilityservice.AccessibilityService.GESTURE_3_FINGER_DOUBLE_TAP
import android.accessibilityservice.AccessibilityService.GESTURE_3_FINGER_DOUBLE_TAP_AND_HOLD
import android.accessibilityservice.AccessibilityService.GESTURE_3_FINGER_SINGLE_TAP
import android.accessibilityservice.AccessibilityService.GESTURE_3_FINGER_SINGLE_TAP_AND_HOLD
import android.accessibilityservice.AccessibilityService.GESTURE_3_FINGER_SWIPE_DOWN
import android.accessibilityservice.AccessibilityService.GESTURE_3_FINGER_SWIPE_LEFT
import android.accessibilityservice.AccessibilityService.GESTURE_3_FINGER_SWIPE_RIGHT
import android.accessibilityservice.AccessibilityService.GESTURE_3_FINGER_SWIPE_UP
import android.accessibilityservice.AccessibilityService.GESTURE_3_FINGER_TRIPLE_TAP
import android.accessibilityservice.AccessibilityService.GESTURE_3_FINGER_TRIPLE_TAP_AND_HOLD
import android.accessibilityservice.AccessibilityService.GESTURE_4_FINGER_DOUBLE_TAP
import android.accessibilityservice.AccessibilityService.GESTURE_4_FINGER_DOUBLE_TAP_AND_HOLD
import android.accessibilityservice.AccessibilityService.GESTURE_4_FINGER_SINGLE_TAP
import android.accessibilityservice.AccessibilityService.GESTURE_4_FINGER_SWIPE_DOWN
import android.accessibilityservice.AccessibilityService.GESTURE_4_FINGER_SWIPE_LEFT
import android.accessibilityservice.AccessibilityService.GESTURE_4_FINGER_SWIPE_RIGHT
import android.accessibilityservice.AccessibilityService.GESTURE_4_FINGER_SWIPE_UP
import android.accessibilityservice.AccessibilityService.GESTURE_4_FINGER_TRIPLE_TAP
import android.accessibilityservice.AccessibilityService.GESTURE_DOUBLE_TAP
import android.accessibilityservice.AccessibilityService.GESTURE_DOUBLE_TAP_AND_HOLD
import android.accessibilityservice.AccessibilityService.GESTURE_SWIPE_DOWN
import android.accessibilityservice.AccessibilityService.GESTURE_SWIPE_DOWN_AND_LEFT
import android.accessibilityservice.AccessibilityService.GESTURE_SWIPE_DOWN_AND_RIGHT
import android.accessibilityservice.AccessibilityService.GESTURE_SWIPE_DOWN_AND_UP
import android.accessibilityservice.AccessibilityService.GESTURE_SWIPE_LEFT
import android.accessibilityservice.AccessibilityService.GESTURE_SWIPE_LEFT_AND_DOWN
import android.accessibilityservice.AccessibilityService.GESTURE_SWIPE_LEFT_AND_RIGHT
import android.accessibilityservice.AccessibilityService.GESTURE_SWIPE_LEFT_AND_UP
import android.accessibilityservice.AccessibilityService.GESTURE_SWIPE_RIGHT
import android.accessibilityservice.AccessibilityService.GESTURE_SWIPE_RIGHT_AND_DOWN
import android.accessibilityservice.AccessibilityService.GESTURE_SWIPE_RIGHT_AND_LEFT
import android.accessibilityservice.AccessibilityService.GESTURE_SWIPE_RIGHT_AND_UP
import android.accessibilityservice.AccessibilityService.GESTURE_SWIPE_UP
import android.accessibilityservice.AccessibilityService.GESTURE_SWIPE_UP_AND_DOWN
import android.accessibilityservice.AccessibilityService.GESTURE_SWIPE_UP_AND_LEFT
import android.accessibilityservice.AccessibilityService.GESTURE_SWIPE_UP_AND_RIGHT
import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import com.google.android.accessibility.utils.gestures.GestureManifold.GestureConfigProvider
import com.google.android.accessibility.utils.gestures.Swipe.Companion.DOWN
import com.google.android.accessibility.utils.gestures.Swipe.Companion.LEFT
import com.google.android.accessibility.utils.gestures.Swipe.Companion.NONE
import com.google.android.accessibility.utils.gestures.Swipe.Companion.RIGHT
import com.google.android.accessibility.utils.gestures.Swipe.Companion.UP
import com.google.android.libraries.accessibility.utils.log.LogUtils

/**
 * This class generates the list of the {@link GestureMatcher} with the given support gesture list.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
internal object GestureMatcherFactory {

  /** List of the gestures. */
  private enum class GestureMatchConfig(
    @JvmField val gestureId: Int,
    @JvmField val finger: Int,
    @JvmField val tap: Int,
    @JvmField val isHold: Boolean,
    @JvmField val direction1: Int,
    @JvmField val direction2: Int,
  ) {
    // Start with double tap.
    MAPPER_GESTURE_DOUBLE_TAP(GESTURE_DOUBLE_TAP, 1, 2, NONE, NONE),
    MAPPER_GESTURE_DOUBLE_TAP_AND_HOLD(GESTURE_DOUBLE_TAP_AND_HOLD, 1, 2, true, NONE, NONE),
    // Second-finger tap.
    MAPPER_GESTURE_FAKED_SPLIT_TYPING(
      GestureManifold.GESTURE_FAKED_SPLIT_TYPING, 1, 1, true, NONE, NONE),
    MAPPER_GESTURE_FAKED_SPLIT_TYPING_AND_HOLD(
      GestureManifold.GESTURE_FAKED_SPLIT_TYPING_AND_HOLD, 1, 1, true, NONE, NONE),
    // One-direction swipes.
    MAPPER_GESTURE_SWIPE_RIGHT(GESTURE_SWIPE_RIGHT, 1, 0, RIGHT, NONE),
    MAPPER_GESTURE_SWIPE_LEFT(GESTURE_SWIPE_LEFT, 1, 0, LEFT, NONE),
    MAPPER_GESTURE_SWIPE_UP(GESTURE_SWIPE_UP, 1, 0, UP, NONE),
    MAPPER_GESTURE_SWIPE_DOWN(GESTURE_SWIPE_DOWN, 1, 0, DOWN, NONE),
    // Two-direction swipes.
    MAPPER_GESTURE_SWIPE_LEFT_AND_RIGHT(GESTURE_SWIPE_LEFT_AND_RIGHT, 1, 0, LEFT, RIGHT),
    MAPPER_GESTURE_SWIPE_LEFT_AND_UP(GESTURE_SWIPE_LEFT_AND_UP, 1, 0, LEFT, UP),
    MAPPER_GESTURE_SWIPE_LEFT_AND_DOWN(GESTURE_SWIPE_LEFT_AND_DOWN, 1, 0, LEFT, DOWN),
    MAPPER_GESTURE_SWIPE_RIGHT_AND_UP(GESTURE_SWIPE_RIGHT_AND_UP, 1, 0, RIGHT, UP),
    MAPPER_GESTURE_SWIPE_RIGHT_AND_DOWN(GESTURE_SWIPE_RIGHT_AND_DOWN, 1, 0, RIGHT, DOWN),
    MAPPER_GESTURE_SWIPE_RIGHT_AND_LEFT(GESTURE_SWIPE_RIGHT_AND_LEFT, 1, 0, RIGHT, LEFT),
    MAPPER_GESTURE_SWIPE_DOWN_AND_UP(GESTURE_SWIPE_DOWN_AND_UP, 1, 0, DOWN, UP),
    MAPPER_GESTURE_SWIPE_DOWN_AND_LEFT(GESTURE_SWIPE_DOWN_AND_LEFT, 1, 0, DOWN, LEFT),
    MAPPER_GESTURE_SWIPE_DOWN_AND_RIGHT(GESTURE_SWIPE_DOWN_AND_RIGHT, 1, 0, DOWN, RIGHT),
    MAPPER_GESTURE_SWIPE_UP_AND_DOWN(GESTURE_SWIPE_UP_AND_DOWN, 1, 0, UP, DOWN),
    MAPPER_GESTURE_SWIPE_UP_AND_LEFT(GESTURE_SWIPE_UP_AND_LEFT, 1, 0, UP, LEFT),
    MAPPER_GESTURE_SWIPE_UP_AND_RIGHT(GESTURE_SWIPE_UP_AND_RIGHT, 1, 0, UP, RIGHT),
    // Set up multi-finger gestures to be enabled later.
    // Two-finger taps.
    MAPPER_GESTURE_2_FINGER_SINGLE_TAP(GESTURE_2_FINGER_SINGLE_TAP, 2, 1, NONE, NONE),
    MAPPER_GESTURE_2_FINGER_SINGLE_TAP_AND_HOLD(
      GestureManifold.GESTURE_2_FINGER_SINGLE_TAP_AND_HOLD, 2, 1, true, NONE, NONE),
    MAPPER_GESTURE_2_FINGER_DOUBLE_TAP(GESTURE_2_FINGER_DOUBLE_TAP, 2, 2, NONE, NONE),
    MAPPER_GESTURE_2_FINGER_DOUBLE_TAP_AND_HOLD(
      GESTURE_2_FINGER_DOUBLE_TAP_AND_HOLD, 2, 2, true, NONE, NONE),
    MAPPER_GESTURE_2_FINGER_TRIPLE_TAP(GESTURE_2_FINGER_TRIPLE_TAP, 2, 3, NONE, NONE),
    MAPPER_GESTURE_2_FINGER_TRIPLE_TAP_AND_HOLD(
      GESTURE_2_FINGER_TRIPLE_TAP_AND_HOLD, 2, 3, true, NONE, NONE),
    // Three-finger taps.
    MAPPER_GESTURE_3_FINGER_SINGLE_TAP(GESTURE_3_FINGER_SINGLE_TAP, 3, 1, NONE, NONE),
    MAPPER_GESTURE_3_FINGER_DOUBLE_TAP(GESTURE_3_FINGER_DOUBLE_TAP, 3, 2, NONE, NONE),
    MAPPER_GESTURE_3_FINGER_SINGLE_TAP_AND_HOLD(
      GESTURE_3_FINGER_SINGLE_TAP_AND_HOLD, 3, 1, true, NONE, NONE),
    MAPPER_GESTURE_3_FINGER_DOUBLE_TAP_AND_HOLD(
      GESTURE_3_FINGER_DOUBLE_TAP_AND_HOLD, 3, 2, true, NONE, NONE),
    MAPPER_GESTURE_3_FINGER_TRIPLE_TAP(GESTURE_3_FINGER_TRIPLE_TAP, 3, 3, NONE, NONE),
    MAPPER_GESTURE_3_FINGER_TRIPLE_TAP_AND_HOLD(
      GESTURE_3_FINGER_TRIPLE_TAP_AND_HOLD, 3, 3, true, NONE, NONE),
    // Four-finger taps.
    MAPPER_GESTURE_4_FINGER_SINGLE_TAP(GESTURE_4_FINGER_SINGLE_TAP, 4, 1, NONE, NONE),
    MAPPER_GESTURE_4_FINGER_DOUBLE_TAP(GESTURE_4_FINGER_DOUBLE_TAP, 4, 2, NONE, NONE),
    MAPPER_GESTURE_4_FINGER_DOUBLE_TAP_AND_HOLD(
      GESTURE_4_FINGER_DOUBLE_TAP_AND_HOLD, 4, 2, true, NONE, NONE),
    MAPPER_GESTURE_4_FINGER_TRIPLE_TAP(GESTURE_4_FINGER_TRIPLE_TAP, 4, 3, NONE, NONE),
    // Two-finger swipes.
    MAPPER_GESTURE_2_FINGER_SWIPE_DOWN(GESTURE_2_FINGER_SWIPE_DOWN, 2, 0, DOWN, NONE),
    MAPPER_GESTURE_2_FINGER_SWIPE_LEFT(GESTURE_2_FINGER_SWIPE_LEFT, 2, 0, LEFT, NONE),
    MAPPER_GESTURE_2_FINGER_SWIPE_RIGHT(GESTURE_2_FINGER_SWIPE_RIGHT, 2, 0, RIGHT, NONE),
    MAPPER_GESTURE_2_FINGER_SWIPE_UP(GESTURE_2_FINGER_SWIPE_UP, 2, 0, UP, NONE),
    // Three-finger swipes.
    MAPPER_GESTURE_3_FINGER_SWIPE_DOWN(GESTURE_3_FINGER_SWIPE_DOWN, 3, 0, DOWN, NONE),
    MAPPER_GESTURE_3_FINGER_SWIPE_LEFT(GESTURE_3_FINGER_SWIPE_LEFT, 3, 0, LEFT, NONE),
    MAPPER_GESTURE_3_FINGER_SWIPE_RIGHT(GESTURE_3_FINGER_SWIPE_RIGHT, 3, 0, RIGHT, NONE),
    MAPPER_GESTURE_3_FINGER_SWIPE_UP(GESTURE_3_FINGER_SWIPE_UP, 3, 0, UP, NONE),
    // Four-finger swipes.
    MAPPER_GESTURE_4_FINGER_SWIPE_DOWN(GESTURE_4_FINGER_SWIPE_DOWN, 4, 0, DOWN, NONE),
    MAPPER_GESTURE_4_FINGER_SWIPE_LEFT(GESTURE_4_FINGER_SWIPE_LEFT, 4, 0, LEFT, NONE),
    MAPPER_GESTURE_4_FINGER_SWIPE_RIGHT(GESTURE_4_FINGER_SWIPE_RIGHT, 4, 0, RIGHT, NONE),
    MAPPER_GESTURE_4_FINGER_SWIPE_UP(GESTURE_4_FINGER_SWIPE_UP, 4, 0, UP, NONE);

    constructor(
      gestureId: Int,
      finger: Int,
      tap: Int,
      direction1: Int,
      direction2: Int,
    ) : this(gestureId, finger, tap, /* isHold= */ false, direction1, direction2)

    override fun toString(): String =
      "GestureMatchConfig{" +
        "gestureId=" +
        gestureId +
        ", finger=" +
        finger +
        ", tap=" +
        tap +
        ", isHold=" +
        isHold +
        ", direction1=" +
        direction1 +
        ", direction2=" +
        direction2 +
        '}'
  }

  /**
   * Gets the list of {@link GestureMatcher} by the support gesture list.
   *
   * @param supportGestureList the support gesture list
   * @param listener the listener to set to the GestureMatcher
   * @param logger the event logger to report the specific events
   * @return the list of GestureMatcher
   */
  @JvmStatic
  fun getGestureMatcherList(
    context: Context,
    supportGestureList: List<String>,
    listener: GestureMatcher.StateChangeListener,
    configResolver: GestureConfigProvider,
    logger: GestureMatcher.AnalyticsEventLogger,
  ): List<GestureMatcher> {
    val gestureMatchers = ArrayList<GestureMatcher>()
    LogUtils.v(
      "GestureMatcherFactory",
      "Speed up TouchExplore state: %b",
      configResolver.getSpeedUpTouchExploreState(),
    )
    if (configResolver.getSpeedUpTouchExploreState()) {
      gestureMatchers.add(
        TapToTouchExplore(
          context, GestureManifold.GESTURE_TOUCH_EXPLORE, listener, configResolver, logger),
      )
      gestureMatchers.add(
        TapUpToTouchExplore(context, GestureManifold.GESTURE_TAP_UP_TOUCH_EXPLORE, listener, logger),
      )
    }
    for (iterator in GestureMatchConfig.entries) {
      if (supportGestureList.contains(iterator.name)) {
        val gestureMatcher = createGestureMatcher(context, iterator, listener, configResolver, logger)
        if (gestureMatcher != null) {
          gestureMatchers.add(gestureMatcher)
        }
      }
    }
    return gestureMatchers
  }

  private fun createGestureMatcher(
    context: Context,
    gestureMatchConfig: GestureMatchConfig,
    listener: GestureMatcher.StateChangeListener,
    configResolver: GestureConfigProvider,
    logger: GestureMatcher.AnalyticsEventLogger,
  ): GestureMatcher? {
    // 1-finger
    if (gestureMatchConfig.finger == 1) {
      // swipe
      if (gestureMatchConfig.tap == 0) {
        return if (gestureMatchConfig.direction2 == NONE) {
          Swipe(
            context,
            gestureMatchConfig.direction1,
            gestureMatchConfig.gestureId,
            listener,
            configResolver,
            logger,
          )
        } else {
          Swipe(
            context,
            gestureMatchConfig.direction1,
            gestureMatchConfig.direction2,
            gestureMatchConfig.gestureId,
            listener,
            configResolver,
            logger,
          )
        }
      }
      // single-tap and hold
      if (gestureMatchConfig.tap == 1 && gestureMatchConfig.isHold) {
        return if (gestureMatchConfig.gestureId == GestureManifold.GESTURE_FAKED_SPLIT_TYPING) {
          SecondFingerTap(
            context, gestureMatchConfig.tap, gestureMatchConfig.gestureId, listener, logger)
        } else if (configResolver.enableSplitTapAndHold() &&
          gestureMatchConfig.gestureId == GestureManifold.GESTURE_FAKED_SPLIT_TYPING_AND_HOLD
        ) {
          SecondFingerTapAndHold(
            context, gestureMatchConfig.tap, gestureMatchConfig.gestureId, listener, logger)
        } else {
          null
        }
      }
      // double-taps
      if (gestureMatchConfig.tap == 2) {
        return if (gestureMatchConfig.isHold) {
          MultiTapAndHold(
            context,
            gestureMatchConfig.tap,
            gestureMatchConfig.gestureId,
            listener,
            configResolver,
            logger,
          )
        } else {
          MultiTap(
            context,
            gestureMatchConfig.tap,
            gestureMatchConfig.gestureId,
            listener,
            configResolver,
            logger,
          )
        }
      }
    } else {
      // multi-finger
      // swipe
      if (gestureMatchConfig.tap == 0) {
        return MultiFingerSwipe(
          context,
          gestureMatchConfig.finger,
          gestureMatchConfig.direction1,
          gestureMatchConfig.gestureId,
          listener,
          logger,
        )
      }
      // multi-taps and hold
      return if (gestureMatchConfig.isHold) {
        // Two-finger single tap and hold is special gesture for TalkBack mis-triggering recovery
        // feature.
        if (gestureMatchConfig.finger == 2 && gestureMatchConfig.tap == 1) {
          TwoFingerSingleTapAndLongHold(context, gestureMatchConfig.gestureId, listener, logger)
        } else {
          MultiFingerMultiTapAndHold(
            context,
            gestureMatchConfig.finger,
            gestureMatchConfig.tap,
            gestureMatchConfig.gestureId,
            listener,
            logger,
          )
        }
      } else {
        // multi-taps without hold
        MultiFingerMultiTap(
          context,
          gestureMatchConfig.finger,
          gestureMatchConfig.tap,
          gestureMatchConfig.gestureId,
          listener,
          logger,
        )
      }
    }
    throw IllegalArgumentException(
      String.format(
        "IllegalArgumentException: GestureMatchConfig %s defines the wrong argument. %s",
        gestureMatchConfig.name,
        gestureMatchConfig,
      ),
    )
  }
}
