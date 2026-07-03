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

import android.graphics.PointF
import android.view.MotionEvent
import kotlin.math.hypot

/** Some helper functions for gesture detection. */
object GestureUtils {

  const val MM_PER_CM = 10
  const val CM_PER_INCH = 2.54f

  @JvmStatic
  fun isMultiTap(
    firstUp: MotionEvent?,
    secondUp: MotionEvent?,
    multiTapTimeSlop: Int,
    multiTapDistanceSlop: Int,
  ): Boolean {
    if (firstUp == null || secondUp == null) {
      return false
    }
    return eventsWithinTimeAndDistanceSlop(firstUp, secondUp, multiTapTimeSlop, multiTapDistanceSlop)
  }

  private fun eventsWithinTimeAndDistanceSlop(
    first: MotionEvent,
    second: MotionEvent,
    timeout: Int,
    distance: Int,
  ): Boolean {
    if (isTimedOut(first, second, timeout)) {
      return false
    }
    val deltaMove = distance(first, second)
    if (deltaMove >= distance) {
      return false
    }
    return true
  }

  @JvmStatic
  fun distance(first: MotionEvent, second: MotionEvent): Double =
    dist(first.x, first.y, second.x, second.y).toDouble()

  /**
   * Returns the minimum distance between {@code pointerDown} and each pointer of {@link
   * MotionEvent}.
   *
   * @param pointerDown The action pointer location of the {@link MotionEvent} with {@link
   *     MotionEvent#ACTION_DOWN} or {@link MotionEvent#ACTION_POINTER_DOWN}
   * @param moveEvent The {@link MotionEvent} with {@link MotionEvent#ACTION_MOVE}
   * @return the movement of the pointer.
   */
  @JvmStatic
  fun distanceClosestPointerToPoint(pointerDown: PointF, moveEvent: MotionEvent): Double {
    var movement = Float.MAX_VALUE
    for (i in 0 until moveEvent.pointerCount) {
      val moveDelta = dist(pointerDown.x, pointerDown.y, moveEvent.getX(i), moveEvent.getY(i))
      if (movement > moveDelta) {
        movement = moveDelta
      }
    }
    return movement.toDouble()
  }

  @JvmStatic
  fun isTimedOut(firstUp: MotionEvent, secondUp: MotionEvent, timeout: Int): Boolean {
    val deltaTime = secondUp.eventTime - firstUp.eventTime
    return deltaTime >= timeout
  }

  /**
   * Determines whether a two pointer gesture is a dragging one.
   *
   * @return True if the gesture is a dragging one.
   */
  @JvmStatic
  fun isDraggingGesture(
    firstPtrDownX: Float,
    firstPtrDownY: Float,
    secondPtrDownX: Float,
    secondPtrDownY: Float,
    firstPtrX: Float,
    firstPtrY: Float,
    secondPtrX: Float,
    secondPtrY: Float,
    maxDraggingAngleCos: Float,
  ): Boolean {
    // Check if the pointers are moving in the same direction.
    val firstDeltaX = firstPtrX - firstPtrDownX
    val firstDeltaY = firstPtrY - firstPtrDownY

    if (firstDeltaX == 0f && firstDeltaY == 0f) {
      return true
    }

    val firstMagnitude = hypot(firstDeltaX, firstDeltaY)
    val firstXNormalized = if (firstMagnitude > 0) firstDeltaX / firstMagnitude else firstDeltaX
    val firstYNormalized = if (firstMagnitude > 0) firstDeltaY / firstMagnitude else firstDeltaY

    val secondDeltaX = secondPtrX - secondPtrDownX
    val secondDeltaY = secondPtrY - secondPtrDownY

    if (secondDeltaX == 0f && secondDeltaY == 0f) {
      return true
    }

    val secondMagnitude = hypot(secondDeltaX, secondDeltaY)
    val secondXNormalized = if (secondMagnitude > 0) secondDeltaX / secondMagnitude else secondDeltaX
    val secondYNormalized = if (secondMagnitude > 0) secondDeltaY / secondMagnitude else secondDeltaY

    val angleCos = firstXNormalized * secondXNormalized + firstYNormalized * secondYNormalized

    if (angleCos < maxDraggingAngleCos) {
      return false
    }

    return true
  }

  /** Gets the index of the pointer that went up or down from a motion event. */
  @JvmStatic
  fun getActionIndex(event: MotionEvent): Int =
    (event.action and MotionEvent.ACTION_POINTER_INDEX_MASK) shr
      MotionEvent.ACTION_POINTER_INDEX_SHIFT

  @JvmStatic
  fun dist(x1: Float, y1: Float, x2: Float, y2: Float): Float {
    val x = x2 - x1
    val y = y2 - y1
    return hypot(x, y)
  }
}
