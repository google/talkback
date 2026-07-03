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

import android.content.Context
import android.graphics.PointF
import android.os.Handler
import android.util.Log.VERBOSE
import android.view.MotionEvent
import android.view.MotionEvent.INVALID_POINTER_ID
import android.view.ViewConfiguration
import androidx.annotation.IntDef
import com.google.android.accessibility.utils.Performance.EventId
import java.util.Arrays
import kotlin.math.hypot

/**
 * This class matches second-finger multi-tap gestures. The difference between this class and
 * SecondFingerMultiTap is the two finger tap at the same time for this gesture.
 *
 * This class matches gestures of the form 2-finger tap. Then one of them keeps hold while the
 * other finger multi-taps. The number of taps for each instance is specified in the constructor.
 */
class TwoFingerSecondFingerMultiTap internal constructor(
  context: Context,
  taps: Int,
  @RotateDirection private val rotateDirection: Int,
  gestureId: Int,
  listener: GestureMatcher.StateChangeListener,
  logger: GestureMatcher.AnalyticsEventLogger,
) : GestureMatcher(gestureId, Handler(context.mainLooper), listener, logger) {

  @Retention(AnnotationRetention.SOURCE)
  @IntDef(ROTATE_DIRECTION_DONT_CARE, ROTATE_DIRECTION_FORWARD, ROTATE_DIRECTION_BACKWARD)
  internal annotation class RotateDirection

  // The target number of taps.
  private val targetTapCount: Int = taps

  // The target number of fingers.
  private val targetFingerCount: Int = 2
  private val tapTimeout: Int = targetFingerCount * ViewConfiguration.getTapTimeout()

  // The acceptable distance the pointer can move and still count as a tap.
  private val touchSlop: Int =
    ViewConfiguration.get(context).scaledTouchSlop * targetFingerCount

  // A tap counts when target number of fingers are down and up once.
  private var completedTapCount: Int = 0

  // A flag set to true when target number of fingers have touched down at once before.
  // Used to indicate what next finger action should be. Down when false and lift when true.
  private var isTargetFingerCountReached: Boolean = false

  // Store initial down points for slop checking and update when next down if is inside slop.
  private val bases: Array<PointF> = Array(targetFingerCount) { PointF() }
  private val pointerIds: IntArray = IntArray(targetFingerCount)
  private var lastDownTime: Long = Long.MAX_VALUE
  private var lastUpTime: Long = Long.MAX_VALUE
  private var tappingIndex: Int = 0

  init {
    clear()
  }

  override fun clear() {
    completedTapCount = 0
    isTargetFingerCountReached = false
    for (i in 0 until targetFingerCount) {
      pointerIds[i] = INVALID_POINTER_ID
      bases[i].x = Float.NaN
      bases[i].y = Float.NaN
    }
    lastDownTime = Long.MAX_VALUE
    lastUpTime = Long.MAX_VALUE
    super.clear()
  }

  override fun onDown(eventId: EventId?, event: MotionEvent) {
    lastDownTime = event.eventTime
    if (pointerIds[0] != INVALID_POINTER_ID) {
      // Inconsistent event stream.
      cancelGesture(event)
      return
    }
    pointerIds[0] = 0
    if (bases[0].x.isNaN() && bases[0].y.isNaN()) {
      val x = event.getX(0)
      val y = event.getY(0)
      if (x < 0f || y < 0f) {
        gestureMotionEventLog(VERBOSE, "MotionEvent position's incorrect.")
        cancelGesture(event)
        return
      }
      bases[0].x = x
      bases[0].y = y
    } else {
      gestureMotionEventLog(VERBOSE, "MotionEvent comes out of sync.")
      // This event doesn't make sense in the middle of a gesture.
      cancelGesture(event)
      return
    }
  }

  override fun onUp(eventId: EventId?, event: MotionEvent) {
    // Because this is a multi-finger gesture, we must have received ACTION_POINTER_UP before this
    // so we calculate timeDelta relative to lastUpTime.
    if (completedTapCount != targetTapCount) {
      gestureMotionEventLog(VERBOSE, "The expected tap count does not reach.")
      cancelGesture(event)
      return
    }
    completeGesture(eventId, event)
  }

  override fun onMove(eventId: EventId?, event: MotionEvent) {
    if (bases[0].x.isNaN() && bases[0].y.isNaN()) {
      return
    }

    val currentFingerCount = event.pointerCount
    for (i in 0 until currentFingerCount) {
      val delta =
        hypot(
          event.getX(i) - bases[event.getPointerId(i)].x,
          event.getY(i) - bases[event.getPointerId(i)].y,
        )
      if (delta > (completedTapCount + 1) * touchSlop) {
        // Outside the touch slop
        gestureMotionEventLog(VERBOSE, "MotionEvent positions move Excessively.")
        cancelGesture(event)
        return
      }
    }
    if (currentFingerCount > targetFingerCount) {
      gestureMotionEventLog(VERBOSE, "Too many fingers involved.")
      cancelGesture(event)
      return
    }
  }

  override fun onPointerDown(eventId: EventId?, event: MotionEvent) {
    if (bases[0].x.isNaN() && bases[0].y.isNaN()) {
      return
    }
    val timeDelta = event.eventTime - lastUpTime
    if (timeDelta > tapTimeout) {
      gestureMotionEventLog(VERBOSE, "The 2nd finger taps occur too slow.")
      cancelGesture(event)
      return
    }
    lastDownTime = event.eventTime
    val currentFingerCount = event.pointerCount
    // Accept down only before target number of fingers are down
    // or the finger count is not more than target.
    if (currentFingerCount > targetFingerCount) {
      gestureMotionEventLog(VERBOSE, "Too many fingers involved.")
      cancelGesture(event)
      return
    }
    completedTapCount++
    if (completedTapCount > 1 && event.actionIndex != tappingIndex) {
      gestureMotionEventLog(VERBOSE, "The tapping finger is not persistent.")
      cancelGesture(event)
      return
    }
    if (state == STATE_GESTURE_STARTED || state == STATE_CLEAR) {
      // The user have all fingers down within the tap timeout since first finger down,
      // setting the timeout for fingers to be lifted.
      if (currentFingerCount == targetFingerCount) {
        isTargetFingerCountReached = true
      }
    } else {
      gestureMotionEventLog(VERBOSE, "MotionEvent state's out of sync.")
      cancelGesture(event)
      return
    }
    if (completedTapCount == 1) {
      // Update pointer location .
      bases[1].x = event.getX(1)
      bases[1].y = event.getY(1)
    }
  }

  override fun onPointerUp(eventId: EventId?, event: MotionEvent) {
    // Accept up only after target number of fingers are down.
    if (bases[0].x.isNaN() && bases[0].y.isNaN()) {
      return
    }
    if (!isTargetFingerCountReached) {
      cancelGesture(event)
      return
    }
    if (completedTapCount == 1) {
      tappingIndex = event.actionIndex
      val deltaX = event.getX(tappingIndex) - bases[1 - tappingIndex].x
      when (rotateDirection) {
        ROTATE_DIRECTION_FORWARD -> {
          if (deltaX <= 0) {
            gestureMotionEventLog(VERBOSE, "Rotating direction mismatches.")
            cancelGesture(event)
            return
          }
        }
        ROTATE_DIRECTION_BACKWARD -> {
          if (deltaX >= 0) {
            gestureMotionEventLog(VERBOSE, "Rotating direction mismatches.")
            cancelGesture(event)
            return
          }
        }
        ROTATE_DIRECTION_DONT_CARE -> {}
        else -> {}
      }
      if (completedTapCount == targetTapCount - 1) {
        startGesture(event)
      }
    } else if (tappingIndex != event.actionIndex) {
      gestureMotionEventLog(VERBOSE, "The tapping finger is not persistent.")
      cancelGesture(event)
      return
    }

    if (state == STATE_GESTURE_STARTED || state == STATE_CLEAR) {
      // Needs more fingers lifted within the tap timeout
      // after reaching the target number of fingers are down.
      // Calculate timeDelta relative to whichever baseline is most recent, lastUpTime or
      // lastDownTime.
      val timeDelta = event.eventTime - lastDownTime
      if (timeDelta > tapTimeout) {
        gestureMotionEventLog(VERBOSE, "The tapping finger holds too long time.")
        cancelGesture(event)
        return
      }
    } else {
      gestureMotionEventLog(VERBOSE, "MotionEvent state's out of sync.")
      cancelGesture(event)
      return
    }
    lastUpTime = event.eventTime
    if (completedTapCount == targetTapCount) {
      completeAfterDoubleTapTimeout(eventId, event)
    }
  }

  override fun getGestureName(): String {
    val builder = StringBuilder()
    builder.append("One").append("-Finger Tap-and-hold with 2nd finger")
    if (targetTapCount == 2) {
      builder.append("Double")
    } else if (targetTapCount == 3) {
      builder.append("Triple")
    } else if (targetTapCount > 3) {
      builder.append(targetTapCount)
    }
    return builder.append(" Tap").toString()
  }

  override fun toString(): String {
    val builder = StringBuilder(super.toString())
    if (state != STATE_GESTURE_CANCELED) {
      builder.append(", CompletedTapCount: ")
      builder.append(completedTapCount)
      builder.append(", IsTargetFingerCountReached: ")
      builder.append(isTargetFingerCountReached)
      builder.append(", Bases: ")
      builder.append(Arrays.toString(bases))
    }
    return builder.toString()
  }

  companion object {
    const val ROTATE_DIRECTION_DONT_CARE = 0
    const val ROTATE_DIRECTION_FORWARD = 1
    const val ROTATE_DIRECTION_BACKWARD = 2
  }
}
