/*
 * Copyright (C) 2024 Google Open Source Project
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

/**
 * A base class to define the common part of Gesture Analytics event. The realization of each
 * gesture' analytics, which can extend this base with additional data.
 *
 * @param event It's used to identify which extended event to report; which is listed above.
 * @param gestureId The gesture state machine generating this event.
 */
open class GestureAnalyticsEvent internal constructor(
  @JvmField val event: Int,
  @JvmField val gestureId: Int,
) {
  companion object {
    // The events which the detector will report.
    const val EVENT_DOUBLE_TAP_SLOP_OVER_RANGE = 0 // Double-tap slop's over range.
    const val EVENT_TAP_TO_TOUCH_EXPLORE = 1 // Tap's sped up entering Touch Explore.

    // Extra debug data for event EVENT_DOUBLE_TAP_SLOP_OVER_RANGE.
    const val EXTRA_DEBUG_DOUBLE_TAP_SLOP_OVER_IN_10_PERCENT = 0
    const val EXTRA_DEBUG_DOUBLE_TAP_SLOP_OVER_IN_20_PERCENT = 1
    const val EXTRA_DEBUG_DOUBLE_TAP_SLOP_OVER_IN_50_PERCENT = 2
    const val EXTRA_DEBUG_DOUBLE_TAP_SLOP_OVER_IN_100_PERCENT = 3
    const val EXTRA_DEBUG_DOUBLE_TAP_SLOP_OVER_MORE_THAN_100_PERCENT = 4

    // Extra debug data for event EVENT_TAP_TO_TOUCH_EXPLORE.
    const val EXTRA_DEBUG_TAP_TO_TOUCH_EXPLORE_TOTAL_SAVED_TIME = 0
    const val EXTRA_DEBUG_TAP_TO_TOUCH_EXPLORE_HIT_COUNT = 1
  }
}
