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
import android.os.Handler
import android.util.Log.ERROR
import android.util.Log.VERBOSE
import android.view.MotionEvent
import android.view.MotionEvent.INVALID_POINTER_ID
import android.view.ViewConfiguration
import com.google.android.accessibility.utils.Performance.EventId
import kotlin.math.hypot

/**
 * This class matches second-finger multi-tap gestures. A second-finger multi-tap gesture is where
 * one finger is held down and a second finger executes the taps. The number of taps for each
 * instance is specified in the constructor.
 */
internal class SecondFingerMultiTap(
  context: Context,
  taps: Int,
  gesture: Int,
  listener: GestureMatcher.StateChangeListener,
  logger: GestureMatcher.AnalyticsEventLogger,
) : GestureMatcher(gesture, Handler(context.mainLooper), listener, logger) {
  private val targetTaps: Int = taps
  private val doubleTapSlop: Int = ViewConfiguration.get(context).scaledDoubleTapSlop
  private val touchSlop: Int = ViewConfiguration.get(context).scaledTouchSlop
  private val tapTimeout: Int = ViewConfiguration.getTapTimeout()
  private val doubleTapTimeout: Int = GestureConfiguration.DOUBLE_TAP_TIMEOUT_MS
  private var currentTaps: Int = 0
  private var secondFingerPointerId: Int = INVALID_POINTER_ID
  @JvmField internal var baseX: Float = Float.NaN
  @JvmField internal var baseY: Float = Float.NaN
  @JvmField internal var lastDownTime: Long = Long.MAX_VALUE
  @JvmField internal var lastUpTime: Long = Long.MAX_VALUE

  init {
    clear()
  }

  override fun clear() {
    currentTaps = 0
    baseX = Float.NaN
    baseY = Float.NaN
    secondFingerPointerId = INVALID_POINTER_ID
    lastDownTime = Long.MAX_VALUE
    lastUpTime = Long.MAX_VALUE
    super.clear()
  }

  override fun onPointerDown(eventId: EventId?, event: MotionEvent) {
    if (event.pointerCount > 2) {
      cancelGesture(event)
      return
    }
    // Second finger has gone down.
    val index = event.actionIndex
    secondFingerPointerId = event.getPointerId(index)
    val time = event.eventTime
    val timeDelta = time - lastUpTime
    if (timeDelta > doubleTapTimeout) {
      cancelGesture(event)
      return
    }
    lastDownTime = time
    if (baseX.isNaN() && baseY.isNaN()) {
      baseX = event.getX(index)
      baseY = event.getY(index)
    }
    if (!isSecondFingerInsideSlop(event, doubleTapSlop)) {
      cancelGesture(event)
    }
    baseX = event.getX(index)
    baseY = event.getY(index)
  }

  override fun onPointerUp(eventId: EventId?, event: MotionEvent) {
    if (event.pointerCount > 2) {
      cancelGesture(event)
      return
    }
    val time = event.eventTime
    val timeDelta = time - lastDownTime
    if (timeDelta > tapTimeout) {
      cancelGesture(event)
      return
    }
    lastUpTime = time
    if (!isSecondFingerInsideSlop(event, touchSlop)) {
      cancelGesture(event)
    }
    if (state == STATE_GESTURE_STARTED || state == STATE_CLEAR) {
      currentTaps++
      if (currentTaps == targetTaps) {
        // Done.
        completeGesture(eventId, event)
        return
      }
    } else {
      // Nonsensical event stream.
      cancelGesture(event)
    }
  }

  override fun onMove(eventId: EventId?, event: MotionEvent) {
    when (event.pointerCount) {
      1 -> {
        // We don't need to track anything about one-finger movements.
      }
      2 -> {
        if (!isSecondFingerInsideSlop(event, touchSlop)) {
          cancelGesture(event)
        }
      }
      else ->
        // More than two fingers means we stop tracking.
        cancelGesture(event)
    }
  }

  override fun onUp(eventId: EventId?, event: MotionEvent) {
    // Cancel early when possible, or it will take precedence over two-finger double tap.
    cancelGesture(event)
  }

  override fun getGestureName(): String =
    when (targetTaps) {
      2 -> "Second Finger Double Tap"
      3 -> "Second Finger Triple Tap"
      else -> "Second Finger $targetTaps Taps"
    }

  private fun isSecondFingerInsideSlop(event: MotionEvent, slop: Int): Boolean {
    val pointerIndex = event.findPointerIndex(secondFingerPointerId)
    if (pointerIndex == -1) {
      gestureMotionEventLog(ERROR, "Unable to find pointer.")
      return false
    }
    val deltaX = baseX - event.getX(pointerIndex)
    val deltaY = baseY - event.getY(pointerIndex)
    if (deltaX == 0f && deltaY == 0f) {
      return true
    }
    val moveDelta = hypot(deltaX.toDouble(), deltaY.toDouble())
    gestureMotionEventLog(VERBOSE, "moveDelta: %g", moveDelta)
    return moveDelta <= slop
  }

  override fun toString(): String =
    super.toString() + ", Taps:" + currentTaps + ", mBaseX: " + baseX + ", mBaseY: " + baseY
}
