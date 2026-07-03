/*
 * Copyright (C) 2024 Google Inc.
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
 *
 * Ported from Java to Kotlin.
 */

package com.google.android.accessibility.utils.gestures

import android.content.Context
import android.os.Build
import android.os.Handler
import android.util.Log.VERBOSE
import android.util.TypedValue
import android.view.MotionEvent
import androidx.annotation.RequiresApi
import com.google.android.accessibility.utils.Performance.EventId
import com.google.android.accessibility.utils.R
import com.google.android.accessibility.utils.gestures.GestureUtils.MM_PER_CM
import kotlin.math.abs
import kotlin.math.hypot

/**
 * This class is a pseudo gesture matcher which can early determine to enter the Touch Explore
 * state. To help entering Touch Explore state earlier, it has to predict that all gesture detector
 * would no longer possible to match with additional MotionEvent. For 1-finger case, when the 1st
 * action_down is held for more than the time the swipe gestures expect the finger should move, itʼs
 * no conflict to directly enter the touch explore state.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
internal class TapUpToTouchExplore(
  context: Context,
  gesture: Int,
  listener: GestureMatcher.StateChangeListener,
  logger: GestureMatcher.AnalyticsEventLogger,
) : GestureMatcher(gesture, Handler(context.mainLooper), listener, logger) {

  private var baseX: Float = Float.NaN
  private var baseY: Float = Float.NaN
  private var firstDownTime: Long = Long.MAX_VALUE

  // This is the calculated movement threshold used track if the user is still
  // moving their finger.
  private var gestureDetectionThresholdPixels: Float = 0f

  // Time threshold in millisecond to determine if an interaction is a gesture or not.
  private var maxStartThreshold: Int = 0

  init {
    initializeViewConfigurationParameters(context)
    clear()
  }

  override fun onConfigurationChanged(context: Context) {
    initializeViewConfigurationParameters(context)
  }

  private fun initializeViewConfigurationParameters(context: Context) {
    val gestureConfirmDistanceCm =
      context.resources.getFloat(R.dimen.config_gesture_confirm_distance_cm)
    val displayMetrics = context.resources.displayMetrics
    gestureDetectionThresholdPixels =
      TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_MM, MM_PER_CM.toFloat(), displayMetrics) *
        gestureConfirmDistanceCm
    val maxTimeToStartSwipeMsPerCm =
      context.resources.getInteger(R.integer.config_max_time_to_start_swipe_ms_per_cm)
    maxStartThreshold = (maxTimeToStartSwipeMsPerCm * gestureConfirmDistanceCm).toInt()
  }

  override fun clear() {
    baseX = Float.NaN
    baseY = Float.NaN
    firstDownTime = Long.MAX_VALUE
    super.clear()
  }

  override fun onDown(eventId: EventId?, event: MotionEvent) {
    baseX = event.x
    baseY = event.y
    firstDownTime = event.eventTime
  }

  override fun onMove(eventId: EventId?, event: MotionEvent) {
    if (event.pointerCount > 1) {
      return
    }
    val x = event.x
    val y = event.y
    val moveDelta = hypot(abs(x - baseX).toDouble(), abs(y - baseY).toDouble())
    if (moveDelta > gestureDetectionThresholdPixels) {
      // No need to monitor to Touch Explore
      cancelGesture(event)
      return
    }
    val timeDelta = event.eventTime - firstDownTime
    if (timeDelta > maxStartThreshold) {
      debugMotionEvent(TAG, "onMove/timeDelta is over. Gesture:%d", gestureId)
      completeGesture(eventId, event)
      return
    }
  }

  override fun onPointerDown(eventId: EventId?, event: MotionEvent) {
    cancelGesture(event)
  }

  override fun onPointerUp(eventId: EventId?, event: MotionEvent) {
    cancelGesture(event)
  }

  override fun onUp(eventId: EventId?, event: MotionEvent) {
    gestureMotionEventLog(VERBOSE, "onUp")
    cancelGesture(event)
  }

  override fun getGestureName(): String = "TapUpToTouchExplore"

  override fun toString(): String = super.toString() + "BaseX: " + baseX + ", BaseY: " + baseY

  private companion object {
    const val TAG = "TapUpToTouchExplore"
  }
}
