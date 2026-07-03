/*
 * Copyright (C) 2020 The Android Open Source Project
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
import android.view.MotionEvent
import android.view.ViewConfiguration
import com.google.android.accessibility.utils.Performance.EventId
import java.util.Arrays
import kotlin.math.hypot

/**
 * This class matches multi-finger multi-tap gestures. The number of fingers and the number of taps
 * for each instance is specified in the constructor.
 *
 * @throws IllegalArgumentException if `fingers` is less than 2 or `taps` is not positive.
 */
internal open class MultiFingerMultiTap(
  context: Context,
  fingers: Int,
  taps: Int,
  gestureId: Int,
  listener: GestureMatcher.StateChangeListener,
  logger: GestureMatcher.AnalyticsEventLogger,
) : GestureMatcher(gestureId, Handler(context.mainLooper), listener, logger) {

  // The target number of taps.
  @JvmField internal val mTargetTapCount: Int = taps

  // The target number of fingers.
  @JvmField internal val targetFingerCount: Int = fingers

  // The acceptable distance between two taps of a finger.
  private val doubleTapSlop: Int = ViewConfiguration.get(context).scaledDoubleTapSlop * fingers
  private val doubleTapTimeout: Int = GestureConfiguration.DOUBLE_TAP_TIMEOUT_MS
  private val tapTimeout: Int = targetFingerCount * ViewConfiguration.getTapTimeout()

  // The acceptable distance the pointer can move and still count as a tap.
  private val touchSlop: Int = ViewConfiguration.get(context).scaledTouchSlop * fingers

  // A tap counts when target number of fingers are down and up once.
  @JvmField protected var completedTapCount: Int = 0

  // A flag set to true when target number of fingers have touched down at once before.
  // Used to indicate what next finger action should be. Down when false and lift when true.
  @JvmField protected var isTargetFingerCountReached: Boolean = false

  // Store initial down points for slop checking and update when next down if is inside slop.
  private val bases: Array<PointF> = Array(targetFingerCount) { PointF() }

  // The points in bases that already have slop checked when onDown or onPointerDown.
  // It prevents excluded points matched multiple times by other pointers from next check.
  private val excludedPointsForDownSlopChecked: ArrayList<PointF> = ArrayList(targetFingerCount)
  private var lastDownTime: Long = Long.MAX_VALUE
  private var lastUpTime: Long = Long.MAX_VALUE

  init {
    clear()
  }

  override fun clear() {
    completedTapCount = 0
    isTargetFingerCountReached = false
    for (i in bases.indices) {
      bases[i].set(Float.NaN, Float.NaN)
    }
    excludedPointsForDownSlopChecked.clear()
    lastDownTime = Long.MAX_VALUE
    lastUpTime = Long.MAX_VALUE
    super.clear()
  }

  override fun onDown(eventId: EventId?, event: MotionEvent) {
    // Before the matcher state transit to completed,
    // Cancel when an additional down arrived after reaching the target number of taps.
    if (completedTapCount == mTargetTapCount) {
      cancelGesture(event)
      return
    }
    val timeDelta = event.eventTime - lastUpTime
    if (timeDelta > doubleTapTimeout) {
      cancelGesture(event)
      return
    }
    lastDownTime = event.eventTime
    if (completedTapCount == 0) {
      initBaseLocation(event)
      return
    }
    // As fingers go up and down, their pointer ids will not be the same.
    // Therefore we require that a given finger be in slop range of any one
    // of the fingers from the previous tap.
    val nearest = findNearestPoint(event, doubleTapSlop.toFloat(), true)
    if (nearest != null) {
      // Update pointer location to nearest one as a new base for next slop check.
      val index = event.actionIndex
      nearest.set(event.getX(index), event.getY(index))
    } else {
      cancelGesture(event)
    }
  }

  override fun onUp(eventId: EventId?, event: MotionEvent) {
    // Because this is a multi-finger gesture, we must have received ACTION_POINTER_UP before this
    // so we calculate timeDelta relative to lastUpTime.
    val timeDelta = event.eventTime - lastUpTime
    if (timeDelta > tapTimeout) {
      cancelGesture(event)
      return
    }
    lastUpTime = event.eventTime
    val nearest = findNearestPoint(event, touchSlop.toFloat(), false)
    if ((state == STATE_GESTURE_STARTED || state == STATE_CLEAR) && nearest != null) {
      // Increase current tap count when the user have all fingers lifted
      // within the tap timeout since the target number of fingers are down.
      if (isTargetFingerCountReached) {
        completedTapCount++
        isTargetFingerCountReached = false
        excludedPointsForDownSlopChecked.clear()
      }

      // Start gesture detection here to avoid the conflict to 2nd finger double tap
      // that never actually started gesture detection.
      if (completedTapCount == 1) {
        startGesture(event)
      }
      if (completedTapCount == mTargetTapCount) {
        // Done.
        completeAfterDoubleTapTimeout(eventId, event)
      }
    } else {
      // Either too many taps or nonsensical event stream.
      cancelGesture(event)
    }
  }

  override fun onMove(eventId: EventId?, event: MotionEvent) {
    // Outside the touch slop
    if (findNearestPoint(event, touchSlop.toFloat(), false) == null) {
      cancelGesture(event)
    }
  }

  override fun onPointerDown(eventId: EventId?, event: MotionEvent) {
    // Reset timeout to ease the use for some people
    // with certain impairments to get all their fingers down.
    val timeDelta = event.eventTime - lastDownTime
    if (timeDelta > tapTimeout) {
      cancelGesture(event)
      return
    }
    lastDownTime = event.eventTime
    val currentFingerCount = event.pointerCount
    // Accept down only before target number of fingers are down
    // or the finger count is not more than target.
    if (currentFingerCount > targetFingerCount || isTargetFingerCountReached) {
      isTargetFingerCountReached = false
      cancelGesture(event)
      return
    }

    val nearest: PointF? =
      if (completedTapCount == 0) {
        initBaseLocation(event)
      } else {
        findNearestPoint(event, doubleTapSlop.toFloat(), true)
      }
    if ((state == STATE_GESTURE_STARTED || state == STATE_CLEAR) && nearest != null) {
      // The user have all fingers down within the tap timeout since first finger down,
      // setting the timeout for fingers to be lifted.
      if (currentFingerCount == targetFingerCount) {
        isTargetFingerCountReached = true
      }
      // Update pointer location to nearest one as a new base for next slop check.
      val index = event.actionIndex
      nearest.set(event.getX(index), event.getY(index))
    } else {
      cancelGesture(event)
    }
  }

  override fun onPointerUp(eventId: EventId?, event: MotionEvent) {
    // Accept up only after target number of fingers are down.
    if (!isTargetFingerCountReached) {
      cancelGesture(event)
      return
    }

    if (state == STATE_GESTURE_STARTED || state == STATE_CLEAR) {
      // Needs more fingers lifted within the tap timeout
      // after reaching the target number of fingers are down.
      // Calculate timeDelta relative to whichever baseline is most recent, lastUpTime or
      // lastDownTime.
      val timeDelta = event.eventTime - maxOf(lastDownTime, lastUpTime)
      if (timeDelta > tapTimeout) {
        cancelGesture(event)
        return
      }
      lastUpTime = event.eventTime
    } else {
      cancelGesture(event)
    }
  }

  override fun getGestureName(): String {
    val builder = StringBuilder()
    builder.append(targetFingerCount).append("-Finger ")
    if (mTargetTapCount == 1) {
      builder.append("Single")
    } else if (mTargetTapCount == 2) {
      builder.append("Double")
    } else if (mTargetTapCount == 3) {
      builder.append("Triple")
    } else if (mTargetTapCount > 3) {
      builder.append(mTargetTapCount)
    }
    return builder.append(" Tap").toString()
  }

  private fun initBaseLocation(event: MotionEvent): PointF {
    val index = event.actionIndex
    val baseIndex = event.pointerCount - 1
    val p = bases[baseIndex]
    if (p.x.isNaN() && p.y.isNaN()) {
      p.set(event.getX(index), event.getY(index))
    }
    return p
  }

  /**
   * Find the nearest location to the given event in the bases. If no one found, it could be not
   * inside {@code slop}, filtered or empty bases. When {@code filterMatched} is true, if the
   * location of given event matches one of the points in {@link #mExcludedPointsForDownSlopChecked}
   * it would be ignored. Otherwise, the location will be added to {@link
   * #mExcludedPointsForDownSlopChecked}.
   *
   * @param event to find nearest point in bases.
   * @param slop to check to the given location of the event.
   * @param filterMatched true to exclude points already matched other pointers.
   * @return the point in bases closed to the location of the given event.
   */
  private fun findNearestPoint(event: MotionEvent, slop: Float, filterMatched: Boolean): PointF? {
    var moveDelta = Float.MAX_VALUE
    var nearest: PointF? = null
    for (i in bases.indices) {
      val p = bases[i]
      if (p.x.isNaN() && p.y.isNaN()) {
        continue
      }
      if (filterMatched && excludedPointsForDownSlopChecked.contains(p)) {
        continue
      }
      val index = event.actionIndex
      val dX = p.x - event.getX(index)
      val dY = p.y - event.getY(index)
      if (dX == 0f && dY == 0f) {
        if (filterMatched) {
          excludedPointsForDownSlopChecked.add(p)
        }
        return p
      }
      val delta = hypot(dX, dY)
      if (moveDelta > delta) {
        moveDelta = delta
        nearest = p
      }
    }
    if (moveDelta < slop) {
      if (filterMatched) {
        excludedPointsForDownSlopChecked.add(nearest!!)
      }
      return nearest
    }
    return null
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
      builder.append(", ExcludedPointsForDownSlopChecked: ")
      builder.append(excludedPointsForDownSlopChecked.toString())
    }
    return builder.toString()
  }
}
