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
import android.view.MotionEvent
import android.view.ViewConfiguration
import androidx.annotation.RequiresApi
import com.google.android.accessibility.utils.Performance.EventId
import com.google.android.accessibility.utils.R
import com.google.android.accessibility.utils.gestures.GestureManifold.GestureConfigProvider
import kotlin.math.hypot

/**
 * This class is a pseudo gesture matcher which can early determine to enter the Touch Explore
 * state. To help entering Touch Explore state earlier, it has to predict that all gesture detector
 * would no longer possible to match with additional MotionEvent. For 1-finger case, when the delta
 * time of action_down & action_up is over the TapTimeout value, we can determine all gesture
 * detectors are fail. To report the faked gesture complete event would cancel the all detectors and
 * inform TouchInteractionMonitor to directly transit to Touch Explore state.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
class TapToTouchExplore(
  context: Context,
  gesture: Int,
  listener: GestureMatcher.StateChangeListener,
  configProvider: GestureConfigProvider,
  logger: GestureMatcher.AnalyticsEventLogger,
) : GestureMatcher(gesture, Handler(context.mainLooper), listener, logger) {

  // The acceptable distance the pointer can move and still count as a tap.
  @JvmField internal var touchSlop: Int = 0
  @JvmField internal var tapTimeout: Int = 0
  @JvmField internal var baseX: Float = Float.NaN
  @JvmField internal var baseY: Float = Float.NaN
  @JvmField internal var lastDownTime: Long = Long.MAX_VALUE

  init {
    initializeViewConfigurationParameters(context)
    clear()
  }

  override fun onConfigurationChanged(context: Context) {
    initializeViewConfigurationParameters(context)
  }

  private fun initializeViewConfigurationParameters(context: Context) {
    touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    val deltaTapTimeout = context.resources.getInteger(R.integer.config_tap_timeout_delta)
    tapTimeout = ViewConfiguration.getTapTimeout() + deltaTapTimeout
  }

  override fun clear() {
    baseX = Float.NaN
    baseY = Float.NaN
    lastDownTime = Long.MAX_VALUE
    super.clear()
  }

  override fun onDown(eventId: EventId?, event: MotionEvent) {
    baseX = event.x
    baseY = event.y
    lastDownTime = event.eventTime
  }

  override fun onUp(eventId: EventId?, event: MotionEvent) {
    if (isOutsideSlop(event, touchSlop, /* isTouchSlop= */ true)) {
      debugMotionEvent(TAG, "onUp/isOutsideSlop. Gesture:%d", gestureId)
      cancelGesture(event)
      return
    }
    if (isInvalidUpEvent(event)) {
      debugMotionEvent(TAG, "onUp/isInvalidUpEvent. Gesture:%d", gestureId)
      completeGesture(eventId, event)
      return
    }
    cancelGesture(event)
  }

  override fun onMove(eventId: EventId?, event: MotionEvent) {
    if (isOutsideSlop(event, touchSlop, /* isTouchSlop= */ true)) {
      cancelGesture(event)
      debugMotionEvent(TAG, "onMove/isOutsideSlop. Gesture:%d", gestureId)
    }
  }

  override fun onPointerDown(eventId: EventId?, event: MotionEvent) {
    cancelGesture(event)
    debugMotionEvent(TAG, "onPointerDown. Gesture:%d", gestureId)
  }

  override fun onPointerUp(eventId: EventId?, event: MotionEvent) {
    cancelGesture(event)
    debugMotionEvent(TAG, "onPointerUp. Gesture:%d", gestureId)
  }

  override fun getGestureName(): String = "Touch Explore"

  protected fun isOutsideSlop(event: MotionEvent, slop: Int, isTouchSlop: Boolean): Boolean {
    val deltaX = baseX - event.x
    val deltaY = baseY - event.y
    if (deltaX == 0f && deltaY == 0f) {
      return false
    }
    val moveDelta = hypot(deltaX.toDouble(), deltaY.toDouble())
    return moveDelta > slop
  }

  protected fun isInvalidUpEvent(upEvent: MotionEvent): Boolean {
    val time = upEvent.eventTime
    val timeDelta = time - lastDownTime
    if (timeDelta > tapTimeout) {
      debugMotionEvent(TAG, "isInvalidUpEvent/tapTimeout's over. Gesture:%d", gestureId)
      return true
    }
    return false
  }

  override fun toString(): String = super.toString() + "mBaseX: " + baseX + ", mBaseY: " + baseY

  private companion object {
    const val TAG = "TapToTouchExplore"
  }
}
