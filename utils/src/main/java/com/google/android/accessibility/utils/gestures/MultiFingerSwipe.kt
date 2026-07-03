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
import android.os.Build
import android.os.Handler
import android.util.Log
import android.util.Log.VERBOSE
import android.view.MotionEvent
import android.view.MotionEvent.INVALID_POINTER_ID
import android.view.ViewConfiguration
import androidx.annotation.RequiresApi
import com.google.android.accessibility.utils.Performance.EventId
import com.google.android.accessibility.utils.R
import java.util.Arrays
import kotlin.math.abs
import kotlin.math.hypot

/**
 * This class is responsible for matching one-finger swipe gestures. Each instance matches one swipe
 * gesture. A swipe is specified as a series of one or more directions e.g. left, left and up, etc.
 * At this time swipes with more than two directions are not supported.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
internal class MultiFingerSwipe(
  context: Context,
  fingerCount: Int,
  direction: Int,
  gesture: Int,
  listener: GestureMatcher.StateChangeListener,
  logger: GestureMatcher.AnalyticsEventLogger,
) : GestureMatcher(gesture, Handler(context.mainLooper), listener, logger) {

  // Buffer for storing points for gesture detection.
  private val strokeBuffers: MutableList<MutableList<PointF>>

  // The swipe direction for this matcher.
  private val targetDirection: Int = direction
  private val pointerIds: IntArray
  // The starting point of each finger's path in the gesture.
  private val base: Array<PointF?>
  // The most recent entry in each finger's gesture path.
  private val previousGesturePoint: Array<PointF?>
  private val targetFingerCount: Int = fingerCount
  private var currentFingerCount: Int = 0
  // Whether the appropriate number of fingers have gone down at some point. This is reset only on
  // clear.
  private var targetFingerCountReached: Boolean = false
  private var minPixelsBetweenSamplesX: Float = 0f
  private var minPixelsBetweenSamplesY: Float = 0f
  // The minimum distance the finger must travel before we evaluate the initial direction of the
  // swipe.
  // Anything less is still considered a touch.
  private var touchSlop: Int = 0
  private var tapTimeout: Int = 0
  // This is used to record the time of onDown/onPointerDown, so that we can check whether the all
  // fingersʼ tap comply the TapTimeout spec.
  private var lastDownTime: Long = Long.MAX_VALUE

  init {
    pointerIds = IntArray(targetFingerCount)
    base = arrayOfNulls(targetFingerCount)
    previousGesturePoint = arrayOfNulls(targetFingerCount)
    strokeBuffers = ArrayList()
    for (i in 0 until targetFingerCount) {
      strokeBuffers.add(ArrayList())
    }
    initializeViewConfigurationParameters(context)
    clear()
  }

  override fun onConfigurationChanged(context: Context) {
    initializeViewConfigurationParameters(context)
  }

  private fun initializeViewConfigurationParameters(context: Context) {
    val displayMetrics = context.resources.displayMetrics
    // Calculate gesture sampling interval.
    val pixelsPerCmX = displayMetrics.xdpi / GestureUtils.CM_PER_INCH
    val pixelsPerCmY = displayMetrics.ydpi / GestureUtils.CM_PER_INCH
    minPixelsBetweenSamplesX = MIN_CM_BETWEEN_SAMPLES * pixelsPerCmX
    minPixelsBetweenSamplesY = MIN_CM_BETWEEN_SAMPLES * pixelsPerCmY
    tapTimeout = targetFingerCount * ViewConfiguration.getTapTimeout()
    touchSlop =
      ViewConfiguration.get(context).scaledTouchSlop *
        context.resources.getInteger(R.integer.config_slop_default_multiplier)
  }

  override fun clear() {
    targetFingerCountReached = false
    currentFingerCount = 0
    for (i in 0 until targetFingerCount) {
      pointerIds[i] = INVALID_POINTER_ID
      if (base[i] == null) {
        base[i] = PointF()
      }
      base[i]!!.x = Float.NaN
      base[i]!!.y = Float.NaN
      if (previousGesturePoint[i] == null) {
        previousGesturePoint[i] = PointF()
      }
      previousGesturePoint[i]!!.x = Float.NaN
      previousGesturePoint[i]!!.y = Float.NaN
      strokeBuffers[i].clear()
    }
    lastDownTime = Long.MAX_VALUE
    super.clear()
  }

  override fun onDown(eventId: EventId?, event: MotionEvent) {
    if (currentFingerCount > 0) {
      cancelGesture(event)
      return
    }
    currentFingerCount = 1
    val actionIndex = event.actionIndex
    val pointerId = event.getPointerId(actionIndex)
    val pointerIndex = event.pointerCount - 1
    if (pointerId < 0) {
      // Nonsensical pointer id.
      cancelGesture(event)
      return
    }
    if (pointerIds[pointerIndex] != INVALID_POINTER_ID) {
      // Inconsistent event stream.
      cancelGesture(event)
      return
    }
    lastDownTime = event.eventTime
    pointerIds[pointerIndex] = pointerId
    if (base[pointerIndex]!!.x.isNaN() && base[pointerIndex]!!.y.isNaN()) {
      val x = event.getX(actionIndex)
      val y = event.getY(actionIndex)
      if (x < 0f || y < 0f) {
        cancelGesture(event)
        return
      }
      base[pointerIndex]!!.x = x
      base[pointerIndex]!!.y = y
      previousGesturePoint[pointerIndex]!!.x = x
      previousGesturePoint[pointerIndex]!!.y = y
    } else {
      // This event doesn't make sense in the middle of a gesture.
      cancelGesture(event)
      return
    }
  }

  override fun onPointerDown(eventId: EventId?, event: MotionEvent) {
    if (event.pointerCount > targetFingerCount) {
      cancelGesture(event)
      return
    }
    val timeDelta = event.eventTime - lastDownTime
    if (timeDelta > tapTimeout) {
      cancelGesture(event)
      return
    }
    lastDownTime = event.eventTime
    currentFingerCount += 1
    if (currentFingerCount != event.pointerCount) {
      cancelGesture(event)
      return
    }
    if (currentFingerCount == targetFingerCount) {
      targetFingerCountReached = true
    }
    val actionIndex = event.actionIndex
    val pointerId = event.getPointerId(actionIndex)
    if (pointerId < 0) {
      // Nonsensical pointer id.
      cancelGesture(event)
      return
    }
    val pointerIndex = currentFingerCount - 1
    if (pointerIds[pointerIndex] != INVALID_POINTER_ID) {
      // Inconsistent event stream.
      cancelGesture(event)
      return
    }
    pointerIds[pointerIndex] = pointerId
    if (base[pointerIndex]!!.x.isNaN() && base[pointerIndex]!!.y.isNaN()) {
      val x = event.getX(actionIndex)
      val y = event.getY(actionIndex)
      if (x < 0f || y < 0f) {
        cancelGesture(event)
        return
      }
      base[pointerIndex]!!.x = x
      base[pointerIndex]!!.y = y
      previousGesturePoint[pointerIndex]!!.x = x
      previousGesturePoint[pointerIndex]!!.y = y
    } else {
      cancelGesture(event)
      return
    }
  }

  override fun onPointerUp(eventId: EventId?, event: MotionEvent) {
    if (!targetFingerCountReached) {
      cancelGesture(event)
      return
    }
    currentFingerCount -= 1
    val actionIndex = event.actionIndex
    val pointerId = event.getPointerId(actionIndex)
    if (pointerId < 0) {
      // Nonsensical pointer id.
      cancelGesture(event)
      return
    }
    val pointerIndex = Arrays.binarySearch(pointerIds, pointerId)
    if (pointerIndex < 0) {
      cancelGesture(event)
      return
    }
    val x = event.getX(actionIndex)
    val y = event.getY(actionIndex)
    if (x < 0f || y < 0f) {
      cancelGesture(event)
      return
    }
    val dX = abs(x - previousGesturePoint[pointerIndex]!!.x)
    val dY = abs(y - previousGesturePoint[pointerIndex]!!.y)
    if (dX >= minPixelsBetweenSamplesX || dY >= minPixelsBetweenSamplesY) {
      strokeBuffers[pointerIndex].add(PointF(x, y))
    }
    // We will evaluate all the paths on ACTION_UP.
  }

  override fun onMove(eventId: EventId?, event: MotionEvent) {
    for (pointerIndex in 0 until targetFingerCount) {
      if (pointerIds[pointerIndex] == INVALID_POINTER_ID) {
        // Fingers have started to move before the required number of fingers are down.
        // However, they can still move less than the touch slop and still be considered
        // touching, not moving.
        // So we just ignore fingers that haven't been assigned a pointer id and process
        // those who have.
        continue
      }
      gestureMotionEventLog(VERBOSE, getGestureName(), "Processing move on finger %d", pointerIndex)
      val index = event.findPointerIndex(pointerIds[pointerIndex])
      if (index < 0) {
        // This finger is not present in this event. It could have gone up just before this
        // movement.
        gestureMotionEventLog(VERBOSE, "Finger %d not found in this event. skipping.", pointerIndex)
        continue
      }
      val x = event.getX(index)
      val y = event.getY(index)
      if (x < 0f || y < 0f) {
        cancelGesture(event)
        return
      }
      val dX = abs(x - previousGesturePoint[pointerIndex]!!.x)
      val dY = abs(y - previousGesturePoint[pointerIndex]!!.y)
      val moveDelta =
        hypot(
          abs(x - base[pointerIndex]!!.x).toDouble(),
          abs(y - base[pointerIndex]!!.y).toDouble(),
        )
      gestureMotionEventLog(VERBOSE, "moveDelta%g", moveDelta)
      if (state == STATE_CLEAR) {
        if (moveDelta < targetFingerCount * touchSlop) {
          // This still counts as a touch not a swipe.
          continue
        }
        // First, make sure we have the right number of fingers down.
        if (currentFingerCount != targetFingerCount) {
          cancelGesture(event)
          return
        }
        // Then, make sure the pointer is going in the right direction.
        val direction = toDirection(x - base[pointerIndex]!!.x, y - base[pointerIndex]!!.y)
        if (direction != targetDirection) {
          cancelGesture(event)
          return
        }
        // This is confirmed to be some kind of swipe so start tracking points.
        startGesture(event)
        for (i in 0 until targetFingerCount) {
          strokeBuffers[i].add(PointF(base[i]!!.x, base[i]!!.y))
        }
      } else if (state == STATE_GESTURE_STARTED) {
        // Cancel if the finger starts to go the wrong way.
        // Note that this only works because this matcher assumes one direction.
        val direction = toDirection(x - base[pointerIndex]!!.x, y - base[pointerIndex]!!.y)
        if (direction != UNCERTAIN && direction != targetDirection) {
          cancelGesture(event)
          return
        }
        if (dX >= minPixelsBetweenSamplesX || dY >= minPixelsBetweenSamplesY) {
          // Sample every 2.5 MM in order to guard against minor variations in path.
          previousGesturePoint[pointerIndex]!!.x = x
          previousGesturePoint[pointerIndex]!!.y = y
          strokeBuffers[pointerIndex].add(PointF(x, y))
        }
      }
    }
  }

  override fun onUp(eventId: EventId?, event: MotionEvent) {
    when (state) {
      STATE_GESTURE_STARTED -> {}
      STATE_CLEAR -> {
        // For Swipe gestures, this is the very last motion event. When any of the swipe gesture
        // detectors matches, the others will enter the clear state. We should not Cancel the
        // detector again for the Up event, or it cannot detect new gesture immediately.
        return
      }
      else -> {
        cancelGesture(event)
        return
      }
    }
    currentFingerCount = 0
    val actionIndex = event.actionIndex
    val pointerId = event.getPointerId(actionIndex)
    val pointerIndex = Arrays.binarySearch(pointerIds, pointerId)
    if (pointerIndex < 0) {
      cancelGesture(event)
      return
    }
    val x = event.getX(actionIndex)
    val y = event.getY(actionIndex)
    if (x < 0f || y < 0f) {
      cancelGesture(event)
      return
    }
    val dX = abs(x - previousGesturePoint[pointerIndex]!!.x)
    val dY = abs(y - previousGesturePoint[pointerIndex]!!.y)
    if (dX >= minPixelsBetweenSamplesX || dY >= minPixelsBetweenSamplesY) {
      strokeBuffers[pointerIndex].add(PointF(x, y))
    }
    recognizeGesture(eventId, event)
  }

  /**
   * Looks at the sequence of motions in mStrokeBuffer, classifies the gesture, then transitions to
   * the complete or cancel state depending on the result.
   */
  private fun recognizeGesture(eventId: EventId?, event: MotionEvent) {
    // Check the path of each finger against the specified direction.
    // Note that we sample every 2.5 MMm, and the direction matching is extremely tolerant (each
    // direction has a 90-degree arch of tolerance) meaning that minor perpendicular movements
    // should not create false negatives.
    for (i in 0 until targetFingerCount) {
      gestureMotionEventLog(VERBOSE, "Recognizing finger: %d", i)
      if (strokeBuffers[i].size < 2) {
        Log.d(getGestureName(), "Too few points.")
        cancelGesture(event)
        return
      }
      val path: List<PointF> = strokeBuffers[i]

      gestureMotionEventLog(VERBOSE, "path= %s", path.toString())
      // Classify line segments, and call Listener callbacks.
      if (!recognizeGesturePath(path)) {
        cancelGesture(event)
        return
      }
    }
    // If we reach this point then all paths match.
    completeGesture(eventId, event)
  }

  /**
   * Tests the path of a given finger against the direction specified in this matcher.
   *
   * @return True if the path matches the specified direction for this matcher, otherwise false.
   */
  private fun recognizeGesturePath(path: List<PointF>): Boolean {
    for (i in 0 until path.size - 1) {
      val start = path[i]
      val end = path[i + 1]

      val dX = end.x - start.x
      val dY = end.y - start.y
      val direction = toDirection(dX, dY)
      if (direction != targetDirection) {
        gestureMotionEventLog(
          VERBOSE,
          "Found direction %s when expecting %s",
          directionToString(direction),
          directionToString(this.targetDirection),
        )
        return false
      }
    }
    gestureMotionEventLog(VERBOSE, "Completed.")
    return true
  }

  override fun getGestureName(): String {
    val builder = StringBuilder()
    builder.append(targetFingerCount).append("-finger ")
    builder.append("Swipe ").append(directionToString(targetDirection))
    return builder.toString()
  }

  override fun toString(): String {
    val builder = StringBuilder(super.toString())
    if (state != STATE_GESTURE_CANCELED) {
      builder
        .append(", mBase: ")
        .append(Arrays.toString(base))
        .append(", mMinPixelsBetweenSamplesX:")
        .append(minPixelsBetweenSamplesX)
        .append(", mMinPixelsBetweenSamplesY:")
        .append(minPixelsBetweenSamplesY)
    }
    return builder.toString()
  }

  companion object {
    // Direction constants.
    const val LEFT = 0
    const val RIGHT = 1
    const val UP = 2
    const val DOWN = 3
    const val UNCERTAIN = 4

    // Constants for sampling motion event points.
    // We sample based on a minimum distance between points, primarily to improve accuracy by
    // reducing noisy minor changes in direction.
    private const val MIN_CM_BETWEEN_SAMPLES = 0.25f

    private fun toDirection(dX: Float, dY: Float): Int {
      if (dX == 0f && dY == 0f) {
        return UNCERTAIN
      }
      return if (abs(dX) > abs(dY)) {
        // Horizontal
        if (dX < 0) LEFT else RIGHT
      } else {
        // Vertical
        if (dY < 0) UP else DOWN
      }
    }

    @JvmStatic
    fun directionToString(direction: Int): String =
      when (direction) {
        LEFT -> "left"
        RIGHT -> "right"
        UP -> "up"
        DOWN -> "down"
        UNCERTAIN -> "still"
        else -> "Unknown Direction"
      }
  }
}
