/*
 * Copyright (C) 2022 The Android Open Source Project
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
import android.graphics.PointF
import android.os.Build
import android.os.Handler
import android.util.Log.VERBOSE
import android.view.MotionEvent
import android.view.ViewConfiguration
import androidx.annotation.RequiresApi
import com.google.android.accessibility.utils.Performance.EventId
import kotlin.math.hypot

/**
 * This class matches second-finger multi-tap gestures. A second-finger multi-tap gesture is where
 * one finger is held down and a second finger executes the taps. The number of taps for each
 * instance is specified in the constructor.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
internal open class SecondFingerTap(
  context: Context,
  taps: Int,
  gesture: Int,
  listener: GestureMatcher.StateChangeListener,
  logger: GestureMatcher.AnalyticsEventLogger,
) : GestureMatcher(gesture, Handler(context.mainLooper), listener, logger) {

  @JvmField protected val targetTaps: Int = taps
  private val doubleTapTimeout: Int = ViewConfiguration.getDoubleTapTimeout()
  @JvmField protected var currentTaps: Int = 0
  private val touchSlop: Int =
    ViewConfiguration.get(context).scaledTouchSlop * TARGET_FINGER_COUNT
  private var firstDownTime: Long = Long.MAX_VALUE

  // Store initial down points for slop checking and update when next down if is inside slop.
  private val bases: Array<PointF> = Array(TARGET_FINGER_COUNT) { PointF() }
  @JvmField protected var pendingRestart: Boolean = false

  init {
    clear()
  }

  override fun clear() {
    currentTaps = 0
    firstDownTime = Long.MAX_VALUE
    pendingRestart = false
    super.clear()
  }

  // Instead of clear the detector, this method restore the state variables to detect the next tap
  // event.
  override fun restart(pending: Boolean) {
    super.restart(pending)
    pendingRestart = pending
    currentTaps = 0
  }

  override fun bypassCancelByTapUpToTouchExplore(): Boolean = true

  override fun onDown(eventId: EventId?, event: MotionEvent) {
    firstDownTime = event.eventTime
  }

  override fun onPointerDown(eventId: EventId?, event: MotionEvent) {
    val timeDelta = event.eventTime - firstDownTime
    if (timeDelta < doubleTapTimeout) {
      cancelGesture(event)
      return
    }

    if (event.pointerCount > TARGET_FINGER_COUNT) {
      gestureMotionEventLog(VERBOSE, "onPointerDown/getPointerCount=%d", event.pointerCount)
      cancelGesture(event)
      return
    }
    bases[0].x = event.getX(0)
    bases[0].y = event.getY(0)
    bases[1].x = event.getX(1)
    bases[1].y = event.getY(1)
  }

  override fun onPointerUp(eventId: EventId?, event: MotionEvent) {
    gestureMotionEventLog(VERBOSE, "onPointerUp")
    if (event.getPointerId(event.actionIndex) != 1) {
      // Invalid finger of split-tap
      gestureMotionEventLog(VERBOSE, "Invalid finger of split-tap")
      cancelGesture(event)
      return
    }
    if (event.pointerCount > TARGET_FINGER_COUNT) {
      gestureMotionEventLog(VERBOSE, "onPointerUp/getPointerCount=%d", event.pointerCount)
      cancelGesture(event)
      return
    }
    if (!validatePositions(event)) {
      gestureMotionEventLog(VERBOSE, "onPointerUp/validatePositions=false")
      cancelGesture(event)
      return
    }
    if (pendingRestart) {
      pendingRestart = false
      return
    }
    if (state == STATE_GESTURE_STARTED || state == STATE_CLEAR) {
      currentTaps++
      gestureMotionEventLog(VERBOSE, "onPointerUp/getState=%d", state)
      if (currentTaps == targetTaps) {
        gestureMotionEventLog(VERBOSE, "onPointerUp/currentTaps=%d", currentTaps)
        // Done.
        completeGesture(eventId, event)
        restart(false)
        startGesture(event)
      }
    } else {
      gestureMotionEventLog(VERBOSE, "onPointerUp/currentTaps=%d", currentTaps)
      // Nonsensical event stream.
      cancelGesture(event)
    }
  }

  override fun onUp(eventId: EventId?, event: MotionEvent) {
    gestureMotionEventLog(VERBOSE, "onUp")
    // Cancel early when possible, or it will take precedence over two-finger double tap.
    cancelGesture(event)
  }

  /**
   * Ensures the touched points when performing split-tap are not moving too far (within the
   * touch-slop).
   *
   * @param event the latest MotionEvent received.
   * @return {@code true} if successful, {@code false} otherwise.
   */
  private fun validatePositions(event: MotionEvent): Boolean {
    val eventCount = event.pointerCount
    if (eventCount != TARGET_FINGER_COUNT) {
      return true
    }
    for (index in 0 until eventCount) {
      val base = bases[index]
      val dX = base.x - event.getX(index)
      val dY = base.y - event.getY(index)
      val delta = hypot(dX, dY)
      if (delta > touchSlop) {
        return false
      }
    }
    return true
  }

  override fun getGestureName(): String =
    when (targetTaps) {
      1 -> "Second Finger Tap"
      else -> "Second Finger $targetTaps Taps"
    }

  override fun toString(): String =
    super.toString() + ", Taps:" + currentTaps + ", Bases:" + bases[0] + "," + bases[1]

  private companion object {
    const val TARGET_FINGER_COUNT = 2
  }
}
