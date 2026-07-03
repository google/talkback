/*
 * Copyright (C) 2019 The Android Open Source Project
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

import android.content.Context
import android.os.Build
import android.os.Handler
import android.view.MotionEvent
import android.view.ViewConfiguration
import androidx.annotation.RequiresApi
import com.google.android.accessibility.utils.Performance.EventId
import com.google.android.accessibility.utils.R
import com.google.android.accessibility.utils.gestures.GestureManifold.GestureConfigProvider
import com.google.android.libraries.accessibility.utils.log.LogUtils
import com.google.errorprone.annotations.CanIgnoreReturnValue
import kotlin.math.hypot

/**
 * This class matches multi-tap gestures. The number of taps for each instance is specified in the
 * constructor.
 *
 * @hide
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
open class MultiTap(
  context: Context,
  taps: Int,
  gesture: Int,
  listener: GestureMatcher.StateChangeListener,
  private val configProvider: GestureConfigProvider,
  logger: GestureMatcher.AnalyticsEventLogger,
) : GestureMatcher(gesture, Handler(context.mainLooper), listener, logger) {

  @JvmField internal val targetTaps: Int = taps

  // The acceptable distance between two taps
  @JvmField internal var doubleTapSlop: Int = 0

  // The acceptable distance the pointer can move and still count as a tap.
  @JvmField internal var touchSlop: Int = 0
  @JvmField internal var tapTimeout: Int = 0
  @JvmField internal var doubleTapTimeout: Int = 0
  @JvmField internal var currentTaps: Int = 0
  @JvmField internal var baseX: Float = Float.NaN
  @JvmField internal var baseY: Float = Float.NaN
  @JvmField internal var lastDownTime: Long = Long.MAX_VALUE
  @JvmField internal var lastUpTime: Long = Long.MAX_VALUE

  init {
    initializeViewConfigurationParameters(context)
    clear()
  }

  override fun onConfigurationChanged(context: Context) {
    initializeViewConfigurationParameters(context)
  }

  private fun initializeViewConfigurationParameters(context: Context) {
    doubleTapSlop =
      (ViewConfiguration.get(context).scaledDoubleTapSlop *
          configProvider.getDoubleTapSlopMultiplier())
        .toInt()
    LogUtils.v(TAG, "Double-Tap slop is: %d", doubleTapSlop)
    touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    val deltaTapTimeout = context.resources.getInteger(R.integer.config_tap_timeout_delta)
    tapTimeout = ViewConfiguration.getTapTimeout() + deltaTapTimeout
    doubleTapTimeout = GestureConfiguration.DOUBLE_TAP_TIMEOUT_MS
  }

  override fun clear() {
    currentTaps = 0
    baseX = Float.NaN
    baseY = Float.NaN
    lastDownTime = Long.MAX_VALUE
    lastUpTime = Long.MAX_VALUE
    super.clear()
  }

  override fun onDown(eventId: EventId?, event: MotionEvent) {
    val time = event.eventTime
    val timeDelta = time - lastUpTime
    if (timeDelta > doubleTapTimeout) {
      debugMotionEvent(TAG, "onDown/doubleTapTimeout's over. Gesture:%d", gestureId)
      cancelGesture(event)
      return
    }
    lastDownTime = time
    if (baseX.isNaN() && baseY.isNaN()) {
      baseX = event.x
      baseY = event.y
    }
    if (!isInsideSlop(event, doubleTapSlop, /* isTouchSlop= */ false)) {
      debugMotionEvent(TAG, "onDown/doubleTapSlop's over. Gesture:%d", gestureId)
      cancelGesture(event)
      return
    }
    baseX = event.x
    baseY = event.y
    if (currentTaps + 1 == targetTaps) {
      // Start gesture detecting on down of final tap.
      // Note that if this instance is matching double tap,
      // and the service is not requesting to handle double tap, GestureManifold will
      // ignore this.
      startGesture(event)
    }
  }

  override fun onUp(eventId: EventId?, event: MotionEvent) {
    if (!isValidUpEvent(event)) {
      debugMotionEvent(TAG, "onUp/!isValidUpEvent. Gesture:%d", gestureId)
      cancelGesture(event)
      return
    }
    if (state == STATE_GESTURE_STARTED || state == STATE_CLEAR) {
      currentTaps++
      if (currentTaps == targetTaps) {
        // Done.
        completeGesture(eventId, event)
      } else {
        processGesture(eventId, event)
      }
      // Needs more taps.
    } else {
      // Either too many taps or nonsensical event stream.
      cancelGesture(event)
      debugMotionEvent(TAG, "onUp/Too many taps. Gesture:%d", gestureId)
    }
  }

  override fun onMove(eventId: EventId?, event: MotionEvent) {
    if (!isInsideSlop(event, touchSlop, /* isTouchSlop= */ true)) {
      cancelGesture(event)
      debugMotionEvent(TAG, "onMove/!isInsideSlop. Gesture:%d", gestureId)
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

  override fun getGestureName(): String =
    when (targetTaps) {
      2 -> "Double Tap"
      3 -> "Triple Tap"
      else -> "$targetTaps Taps"
    }

  /**
   * This class helps to collect data (double-tap slop over), in addition to the fundamental Gesture
   * analytic event.
   */
  class MultiTapAnalyticsEvent internal constructor(event: Int, gestureId: Int) :
    GestureAnalyticsEvent(event, gestureId) {
    @JvmField var doubleTapSlopOverRange: Int = 0

    @CanIgnoreReturnValue
    internal fun setDoubleTapSlopOverRange(doubleTapSlopOverRange: Int): MultiTapAnalyticsEvent {
      this.doubleTapSlopOverRange = doubleTapSlopOverRange
      return this
    }
  }

  private fun logExceededSlop(deviation: Double) {
    val extraData: Int =
      if (deviation < 0.1) {
        GestureAnalyticsEvent.EXTRA_DEBUG_DOUBLE_TAP_SLOP_OVER_IN_10_PERCENT
      } else if (deviation < 0.2) {
        GestureAnalyticsEvent.EXTRA_DEBUG_DOUBLE_TAP_SLOP_OVER_IN_20_PERCENT
      } else if (deviation < 0.5) {
        GestureAnalyticsEvent.EXTRA_DEBUG_DOUBLE_TAP_SLOP_OVER_IN_50_PERCENT
      } else if (deviation < 1.0) {
        GestureAnalyticsEvent.EXTRA_DEBUG_DOUBLE_TAP_SLOP_OVER_IN_100_PERCENT
      } else {
        GestureAnalyticsEvent.EXTRA_DEBUG_DOUBLE_TAP_SLOP_OVER_MORE_THAN_100_PERCENT
      }
    debugMotionEvent(TAG, "logExceededSlop. Gesture:%d, range:%d", gestureId, extraData)
    val event =
      MultiTapAnalyticsEvent(GestureAnalyticsEvent.EVENT_DOUBLE_TAP_SLOP_OVER_RANGE, gestureId)
        .setDoubleTapSlopOverRange(extraData)
    analyticsEvent(event)
  }

  private fun isInsideSlop(event: MotionEvent, slop: Int, isTouchSlop: Boolean): Boolean {
    val deltaX = baseX - event.x
    val deltaY = baseY - event.y
    if (deltaX == 0f && deltaY == 0f) {
      return true
    }
    val moveDelta = hypot(deltaX.toDouble(), deltaY.toDouble())
    if (!isTouchSlop && moveDelta > slop) {
      logExceededSlop((moveDelta - slop) / slop)
    }
    return moveDelta <= slop
  }

  protected fun isValidUpEvent(upEvent: MotionEvent): Boolean {
    val time = upEvent.eventTime
    val timeDelta = time - lastDownTime
    if (timeDelta > tapTimeout) {
      debugMotionEvent(TAG, "isValidUpEvent/tapTimeout's over. Gesture:%d", gestureId)
      return false
    }
    lastUpTime = time
    if (!isInsideSlop(upEvent, touchSlop, /* isTouchSlop= */ true)) {
      debugMotionEvent(TAG, "isValidUpEvent/!isInsideSlop. Gesture:%d", gestureId)
      return false
    }
    return true
  }

  override fun toString(): String =
    super.toString() + ", Taps:" + currentTaps + ", mBaseX: " + baseX + ", mBaseY: " + baseY

  private companion object {
    const val TAG = "MultiTap"
  }
}
