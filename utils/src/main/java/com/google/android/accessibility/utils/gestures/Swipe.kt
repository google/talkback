/*
 * Copyright (C) 2021 The Android Open Source Project
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
import android.util.TypedValue
import android.view.MotionEvent
import android.view.ViewConfiguration
import androidx.annotation.RequiresApi
import com.google.android.accessibility.utils.Performance.EventId
import com.google.android.accessibility.utils.R
import com.google.android.accessibility.utils.gestures.GestureManifold.GestureConfigProvider
import com.google.android.accessibility.utils.gestures.GestureUtils.MM_PER_CM
import kotlin.math.abs
import kotlin.math.hypot

/**
 * This class is responsible for matching one-finger swipe gestures. Each instance matches one swipe
 * gesture. A swipe is specified as a series of one or more directions e.g. left, left and up, etc.
 * At this time swipes with more than two directions are not supported.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
internal class Swipe private constructor(
  context: Context,
  private val directions: IntArray,
  gesture: Int,
  listener: GestureMatcher.StateChangeListener,
  configProvider: GestureConfigProvider,
  logger: GestureMatcher.AnalyticsEventLogger,
) : GestureMatcher(gesture, Handler(context.mainLooper), listener, logger) {

  // This is the calculated movement threshold used track if the user is still
  // moving their finger.
  private val gestureDetectionThresholdPixels: Float

  // Buffer for storing points for gesture detection.
  private val strokeBuffer = ArrayList<PointF>(100)

  private var baseX = 0f
  private var baseY = 0f
  private var baseTime = 0L
  private var previousGestureX = 0f
  private var previousGestureY = 0f
  private val minPixelsBetweenSamplesX: Float
  private val minPixelsBetweenSamplesY: Float

  // Time threshold in millisecond to determine if an interaction is a gesture or not.
  private val maxStartThreshold: Int

  // Time threshold in millisecond to determine if a gesture should be cancelled.
  private val maxContinueThreshold: Int

  // The minimum distance the finger must travel before we evaluate the initial direction of the
  // swipe.
  // Anything less is still considered a touch.
  private val touchSlop: Int

  private val invalidSwipeGestureEarlyDetection: Boolean

  constructor(
    context: Context,
    direction: Int,
    gesture: Int,
    listener: GestureMatcher.StateChangeListener,
    configProvider: GestureConfigProvider,
    logger: GestureMatcher.AnalyticsEventLogger,
  ) : this(context, intArrayOf(direction), gesture, listener, configProvider, logger)

  constructor(
    context: Context,
    direction1: Int,
    direction2: Int,
    gesture: Int,
    listener: GestureMatcher.StateChangeListener,
    configProvider: GestureConfigProvider,
    logger: GestureMatcher.AnalyticsEventLogger,
  ) : this(context, intArrayOf(direction1, direction2), gesture, listener, configProvider, logger)

  init {
    val gestureConfirmDistanceCm =
      context.resources.getFloat(R.dimen.config_gesture_confirm_distance_cm)
    val maxTimeToStartSwipeMsPerCm =
      context.resources.getInteger(R.integer.config_max_time_to_start_swipe_ms_per_cm)
    val maxTimeToContinueSwipeMsPerCm =
      context.resources.getInteger(R.integer.config_max_time_to_continue_swipe_ms_per_cm)
    invalidSwipeGestureEarlyDetection = configProvider.invalidSwipeGestureEarlyDetection()
    maxStartThreshold = (maxTimeToStartSwipeMsPerCm * gestureConfirmDistanceCm).toInt()
    maxContinueThreshold =
      // The tolerance of gesture time was actually greater than this value. Considering the
      // swiping
      // distance in large screen devices (such as Tablet) could be quite long, and the value
      // doesn't affect the detection time, we increase the value.
      if (invalidSwipeGestureEarlyDetection) {
        ((maxTimeToContinueSwipeMsPerCm * gestureConfirmDistanceCm) * 3).toInt()
      } else {
        (maxTimeToContinueSwipeMsPerCm * gestureConfirmDistanceCm).toInt()
      }

    val displayMetrics = context.resources.displayMetrics
    gestureDetectionThresholdPixels =
      TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_MM, MM_PER_CM.toFloat(), displayMetrics) *
        gestureConfirmDistanceCm
    // Calculate minimum gesture velocity
    val pixelsPerCmX = displayMetrics.xdpi / 2.54f
    val pixelsPerCmY = displayMetrics.ydpi / 2.54f
    minPixelsBetweenSamplesX = MIN_CM_BETWEEN_SAMPLES * pixelsPerCmX
    minPixelsBetweenSamplesY = MIN_CM_BETWEEN_SAMPLES * pixelsPerCmY
    touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    clear()
  }

  override fun clear() {
    baseX = Float.NaN
    baseY = Float.NaN
    baseTime = 0
    previousGestureX = Float.NaN
    previousGestureY = Float.NaN
    strokeBuffer.clear()
    super.clear()
  }

  override fun onDown(eventId: EventId?, event: MotionEvent) {
    if (baseX.isNaN() && baseY.isNaN()) {
      baseX = event.x
      baseY = event.y
      baseTime = event.eventTime
      previousGestureX = baseX
      previousGestureY = baseY
    }
    // Otherwise do nothing because this event doesn't make sense in the middle of a gesture.
  }

  override fun onMove(eventId: EventId?, event: MotionEvent) {
    val x = event.x
    val y = event.y
    val time = event.eventTime
    val dX = abs(x - previousGestureX)
    val dY = abs(y - previousGestureY)
    val moveDelta = hypot(abs(x - baseX).toDouble(), abs(y - baseY).toDouble())
    val timeDelta = time - baseTime
    gestureMotionEventLog(
      VERBOSE,
      "moveDelta: %g,  mGestureDetectionThreshold: %g",
      moveDelta,
      gestureDetectionThresholdPixels,
    )
    if (state == STATE_CLEAR) {
      if (moveDelta < touchSlop) {
        // This still counts as a touch not a swipe.
        return
      } else if (strokeBuffer.isEmpty()) {
        // First, make sure the pointer is going in the right direction.
        val direction = toDirection(x - baseX, y - baseY)
        if (direction != directions[0]) {
          cancelGesture(event)
          return
        }
        // This is confirmed to be some kind of swipe so start tracking points.
        strokeBuffer.add(PointF(baseX, baseY))
      }
    }
    if (moveDelta > gestureDetectionThresholdPixels) {
      if (invalidSwipeGestureEarlyDetection && timeDelta > maxContinueThreshold) {
        cancelGesture(event)
        return
      }
      if (!invalidSwipeGestureEarlyDetection || state == STATE_CLEAR) {
        // This is a gesture, not touch exploration.
        baseX = x
        baseY = y
        baseTime = time
        startGesture(event)
      }
    } else if (state == STATE_CLEAR && timeDelta > maxStartThreshold) {
      cancelGesture(event)
      return
    } else if (state == STATE_GESTURE_STARTED && timeDelta > maxContinueThreshold) {
      cancelGesture(event)
      return
    }

    if (dX >= minPixelsBetweenSamplesX || dY >= minPixelsBetweenSamplesY) {
      // At this point gesture detection has started and we are sampling points.
      previousGestureX = x
      previousGestureY = y
      strokeBuffer.add(PointF(x, y))
    }
  }

  override fun onUp(eventId: EventId?, event: MotionEvent) {
    when (state) {
      STATE_GESTURE_STARTED -> {}
      STATE_CLEAR -> {
        // For Swipe gestures, this is the very last motion event. When any of the swipe gesture
        // detectors matches, the others will enter the clear state. We should not Cancel the
        // detector again for the Up event, or it cannot detect new gesture immediately.
        // On the other hand, if we don't do clear(), the followed onDown event will credit the last
        // stroke data, which caused miss-identified gesture.
        clear()
        return
      }
      else -> {
        cancelGesture(event)
        return
      }
    }

    val x = event.x
    val y = event.y
    val dX = abs(x - previousGestureX)
    val dY = abs(y - previousGestureY)
    if (dX >= minPixelsBetweenSamplesX || dY >= minPixelsBetweenSamplesY) {
      strokeBuffer.add(PointF(x, y))
    }
    recognizeGesture(eventId, event)
  }

  override fun onPointerDown(eventId: EventId?, event: MotionEvent) {
    cancelGesture(event)
  }

  override fun onPointerUp(eventId: EventId?, event: MotionEvent) {
    cancelGesture(event)
  }

  /**
   * Looks at the sequence of motions in mStrokeBuffer, classifies the gesture, then calls Listener
   * callbacks for success or failure.
   *
   * @param event The raw motion event to pass to the listener callbacks.
   */
  private fun recognizeGesture(eventId: EventId?, event: MotionEvent) {
    if (strokeBuffer.size < 2) {
      cancelGesture(event)
      return
    }

    // Look at mStrokeBuffer and extract 2 line segments, delimited by near-perpendicular
    // direction change.
    // Method: for each sampled motion event, check the angle of the most recent motion vector
    // versus the preceding motion vector, and segment the line if the angle is about
    // 90 degrees.

    val path = ArrayList<PointF>()
    var lastDelimiter = strokeBuffer[0]
    path.add(lastDelimiter)

    var dX = 0f // Sum of unit vectors from last delimiter to each following point
    var dY = 0f
    var count = 0 // Number of points since last delimiter
    var length = 0f // Vector length from delimiter to most recent point

    var next: PointF? = null
    for (i in 1 until strokeBuffer.size) {
      next = strokeBuffer[i]
      if (count > 0) {
        // Average of unit vectors from delimiter to following points
        val currentDX = dX / count
        val currentDY = dY / count

        // newDelimiter is a possible new delimiter, based on a vector with length from
        // the last delimiter to the previous point, but in the direction of the average
        // unit vector from delimiter to previous points.
        // Using the averaged vector has the effect of "squaring off the curve",
        // creating a sharper angle between the last motion and the preceding motion from
        // the delimiter. In turn, this sharper angle achieves the splitting threshold
        // even in a gentle curve.
        val newDelimiter =
          PointF(length * currentDX + lastDelimiter.x, length * currentDY + lastDelimiter.y)

        // Unit vector from newDelimiter to the most recent point
        var nextDX = next.x - newDelimiter.x
        var nextDY = next.y - newDelimiter.y
        val nextLength = hypot(nextDX, nextDY)
        nextDX /= nextLength
        nextDY /= nextLength

        // Compare the initial motion direction to the most recent motion direction,
        // and segment the line if direction has changed by about 90 degrees.
        val dot = currentDX * nextDX + currentDY * nextDY
        if (dot < ANGLE_THRESHOLD) {
          path.add(newDelimiter)
          lastDelimiter = newDelimiter
          dX = 0f
          dY = 0f
          count = 0
        }
      }

      // Vector from last delimiter to most recent point
      val currentDX = next.x - lastDelimiter.x
      val currentDY = next.y - lastDelimiter.y
      length = hypot(currentDX, currentDY)

      // Increment sum of unit vectors from delimiter to each following point
      count += 1
      dX += currentDX / length
      dY += currentDY / length
    }

    path.add(next!!)
    gestureMotionEventLog(VERBOSE, "path = %s", path.toString())
    // Classify line segments, and call Listener callbacks.
    recognizeGesturePath(eventId, event, path)
  }

  /**
   * Classifies a pair of line segments, by direction. Calls Listener callbacks for success or
   * failure.
   *
   * @param event The raw motion event to pass to the listener's onGestureCanceled method.
   * @param path A sequence of motion line segments derived from motion points in mStrokeBuffer.
   */
  private fun recognizeGesturePath(eventId: EventId?, event: MotionEvent, path: ArrayList<PointF>) {
    if (path.size != directions.size + 1) {
      cancelGesture(event)
      return
    }
    for (i in 0 until path.size - 1) {
      val start = path[i]
      val end = path[i + 1]

      val dX = end.x - start.x
      val dY = end.y - start.y
      val direction = toDirection(dX, dY)
      if (direction != directions[i]) {
        gestureMotionEventLog(
          VERBOSE,
          "Found direction %s  when expecting %s",
          directionToString(direction),
          directionToString(directions[i]),
        )
        cancelGesture(event)
        return
      }
    }
    gestureMotionEventLog(VERBOSE, "Completed.")
    completeGesture(eventId, event)
  }

  override fun getGestureName(): String {
    val builder = StringBuilder()
    builder.append("Swipe ").append(directionToString(directions[0]))
    for (i in 1 until directions.size) {
      builder.append(" and ").append(directionToString(directions[i]))
    }
    return builder.toString()
  }

  override fun toString(): String {
    val builder = StringBuilder(super.toString())
    if (state != STATE_GESTURE_CANCELED) {
      builder
        .append(", mBaseX: ")
        .append(baseX)
        .append(", mBaseY: ")
        .append(baseY)
        .append(", mGestureDetectionThreshold:")
        .append(gestureDetectionThresholdPixels)
        .append(", mMinPixelsBetweenSamplesX:")
        .append(minPixelsBetweenSamplesX)
        .append(", mMinPixelsBetweenSamplesY:")
        .append(minPixelsBetweenSamplesY)
    }
    return builder.toString()
  }

  companion object {
    // Direction constants.
    const val NONE = -1
    const val LEFT = 0
    const val RIGHT = 1
    const val UP = 2
    const val DOWN = 3

    // Constants for sampling motion event points.
    // We sample based on a minimum distance between points, primarily to improve accuracy by
    // reducing noisy minor changes in direction.
    private const val MIN_CM_BETWEEN_SAMPLES = 0.25f

    // Constants for separating gesture segments
    private const val ANGLE_THRESHOLD = 0.0f

    private fun toDirection(dX: Float, dY: Float): Int =
      if (abs(dX) > abs(dY)) {
        // Horizontal
        if (dX < 0) LEFT else RIGHT
      } else {
        // Vertical
        if (dY < 0) UP else DOWN
      }

    @JvmStatic
    fun directionToString(direction: Int): String =
      when (direction) {
        LEFT -> "left"
        RIGHT -> "right"
        UP -> "up"
        DOWN -> "down"
        else -> "Unknown Direction"
      }
  }
}
