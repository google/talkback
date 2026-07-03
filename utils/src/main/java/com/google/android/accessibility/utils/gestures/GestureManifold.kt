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

import android.accessibilityservice.AccessibilityGestureEvent
import android.accessibilityservice.AccessibilityService.GESTURE_2_FINGER_SWIPE_DOWN
import android.accessibilityservice.AccessibilityService.GESTURE_2_FINGER_SWIPE_LEFT
import android.accessibilityservice.AccessibilityService.GESTURE_2_FINGER_SWIPE_RIGHT
import android.accessibilityservice.AccessibilityService.GESTURE_2_FINGER_SWIPE_UP
import android.content.Context
import android.os.Build
import android.view.MotionEvent
import androidx.annotation.RequiresApi
import com.google.android.accessibility.utils.Performance.EventId
import com.google.android.accessibility.utils.gestures.GestureMatcher.AnalyticsEventLogger
import com.google.android.libraries.accessibility.utils.log.LogUtils
import com.google.common.collect.ImmutableList
import com.google.errorprone.annotations.CanIgnoreReturnValue

/**
 * This class coordinates a series of individual gesture matchers to serve as a unified gesture
 * detector. Gesture matchers are tied to a single gesture. It calls listener callback functions
 * when a gesture starts or completes.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
class GestureManifold(
  context: Context,
  // Listener to be notified of gesture start and end.
  private val listener: Listener,
  configResolver: GestureConfigProvider,
  logger: AnalyticsEventLogger,
  private val displayId: Int,
  supportGestureList: ImmutableList<String>,
) : GestureMatcher.StateChangeListener {

  private val gestures: MutableList<GestureMatcher> = ArrayList()

  // Whether multi-finger gestures are enabled.
  @JvmField internal var multiFingerGesturesEnabled: Boolean = false

  // Whether the two-finger passthrough is enabled when multi-finger gestures are enabled.
  private var twoFingerPassthroughEnabled: Boolean = false

  // A list of all the multi-finger gestures, for easy adding and removal.
  private val multiFingerGestures: MutableList<GestureMatcher> = ArrayList()

  // A list of two-finger swipes, for easy adding and removal when turning on or off two-finger
  // passthrough.
  private val twoFingerSwipes: MutableList<GestureMatcher> = ArrayList()
  private var logMotionEvent = false

  /** Define the interface to get project base feature settings. */
  interface GestureConfigProvider {
    fun getDoubleTapSlopMultiplier(): Float = 1.0f

    fun getSpeedUpTouchExploreState(): Boolean = false

    fun invalidSwipeGestureEarlyDetection(): Boolean = false

    fun useMultipleGestureSet(): Boolean = false

    fun enableSplitTapAndHold(): Boolean = false
  }

  init {
    // Set up gestures.
    val gestureMatcherList =
      GestureMatcherFactory.getGestureMatcherList(
        context, supportGestureList, this, configResolver, logger,
      )

    for (gestureMatcher in gestureMatcherList) {
      if (gestureMatcher is Swipe ||
        gestureMatcher is MultiTap ||
        gestureMatcher is MultiTapAndHold ||
        gestureMatcher is SecondFingerTap
      ) {
        gestures.add(gestureMatcher)
      } else {
        multiFingerGestures.add(gestureMatcher)
        val gestureId = gestureMatcher.gestureId
        if (gestureId == GESTURE_2_FINGER_SWIPE_DOWN ||
          gestureId == GESTURE_2_FINGER_SWIPE_LEFT ||
          gestureId == GESTURE_2_FINGER_SWIPE_RIGHT ||
          gestureId == GESTURE_2_FINGER_SWIPE_UP
        ) {
          twoFingerSwipes.add(gestureMatcher)
        }
      }
    }
    if (configResolver.useMultipleGestureSet()) {
      gestures.add(
        TwoFingerSecondFingerMultiTap(
          context,
          2,
          TwoFingerSecondFingerMultiTap.ROTATE_DIRECTION_FORWARD,
          GESTURE_TAP_HOLD_AND_2ND_FINGER_FORWARD_DOUBLE_TAP,
          this,
          logger,
        ),
      )
      gestures.add(
        TwoFingerSecondFingerMultiTap(
          context,
          2,
          TwoFingerSecondFingerMultiTap.ROTATE_DIRECTION_BACKWARD,
          GESTURE_TAP_HOLD_AND_2ND_FINGER_BACKWARD_DOUBLE_TAP,
          this,
          logger,
        ),
      )
    }
  }

  fun onConfigurationChanged(context: Context) {
    for (gestureDetector in gestures) {
      gestureDetector.onConfigurationChanged(context)
    }
  }

  fun enableLogMotionEvent() {
    logMotionEvent = true
    for (gestureDetector in gestures) {
      gestureDetector.enableLogMotionEvent()
    }
  }

  /**
   * Processes a motion event.
   *
   * @param event The event as received from the previous entry in the event stream.
   * @return True if the event has been appropriately handled by the gesture manifold and related
   *     callback functions, false if it should be handled further by the calling function.
   */
  @CanIgnoreReturnValue
  fun onMotionEvent(eventId: EventId?, event: MotionEvent): Boolean {
    for (matcher in gestures) {
      if (matcher.state != GestureMatcher.STATE_GESTURE_CANCELED) {
        if (logMotionEvent) {
          LogUtils.v(LOG_TAG, matcher.toString())
        }
        matcher.onMotionEvent(eventId, event)
        if (logMotionEvent) {
          LogUtils.v(LOG_TAG, matcher.toString())
        }

        if (matcher.state == GestureMatcher.STATE_GESTURE_COMPLETED) {
          // Here we just return. The actual gesture dispatch is done in
          // onStateChanged().
          // No need to process this event any further.
          return true
        }
      }
    }
    return false
  }

  fun clear() {
    for (matcher in gestures) {
      matcher.clear()
    }
  }

  /**
   * Listener that receives notifications of the state of the gesture detector. Listener functions
   * are called as a result of onMotionEvent(). The current MotionEvent in the context of these
   * functions is the event passed into onMotionEvent.
   */
  interface Listener {

    /**
     * Called when the system has decided the event stream is a potential gesture.
     *
     * @param gestureId the gesture which is start matching.
     */
    fun onGestureStarted(gestureId: Int)

    /**
     * Called when an event stream is recognized as a gesture.
     *
     * @param gestureEvent Information about the gesture.
     */
    fun onGestureCompleted(gestureEvent: AccessibilityGestureEvent)

    /**
     * Called when the system has decided an event stream doesn't match any known gesture.
     *
     * @param gestureId the gesture which is fail to match.
     */
    fun onGestureCancelled(gestureId: Int)

    /**
     * Called when the gesture is processing and should be avoided to be interrupted. It's mainly be
     * used to extend the multi-tap timeout even the user sets the touch focus delay with a shorter
     * time.
     *
     * @param gestureId the gesture which is fail to match.
     */
    fun onGestureProcessing(gestureId: Int) {}
  }

  override fun onStateChanged(gestureId: Int, state: Int, event: MotionEvent) {
    if (state == GestureMatcher.STATE_GESTURE_STARTED) {
      listener.onGestureStarted(gestureId)
    } else if (state == GestureMatcher.STATE_GESTURE_COMPLETED) {
      onGestureCompleted(gestureId, event)
    } else if (state == GestureMatcher.STATE_GESTURE_CANCELED) {
      listener.onGestureCancelled(gestureId)
    } else if (state == GestureMatcher.STATE_GESTURE_PROCESSING) {
      listener.onGestureProcessing(gestureId)
    }
  }

  /**
   * Called when the gesture detector has successfully identified the gesture by a series of
   * MotionEvent.
   *
   * @param gestureId the gesture which is fail to match.
   * @param event the last MotionEvent to match the identified gesture.
   */
  private fun onGestureCompleted(gestureId: Int, event: MotionEvent) {
    // Note that gestures that complete immediately call clear() from onMotionEvent.
    // Gestures that complete on a delay call clear() here.
    val eventList = ArrayList<MotionEvent>()
    eventList.add(event)
    val gestureEvent = AccessibilityGestureEvent(gestureId, displayId, eventList)
    for (matcher in gestures) {
      if (gestureId == GESTURE_TAP_UP_TOUCH_EXPLORE && matcher.bypassCancelByTapUpToTouchExplore()) {
        // Skip to cancel the gesture which claims itself to bypass cancel for this event.
        continue
      } else if (gestureId == GESTURE_FAKED_SPLIT_TYPING &&
        matcher.gestureId == GESTURE_FAKED_SPLIT_TYPING_AND_HOLD
      ) {
        matcher.restart(true)
        continue
      } else if (gestureId == GESTURE_FAKED_SPLIT_TYPING_AND_HOLD &&
        matcher.gestureId == GESTURE_FAKED_SPLIT_TYPING
      ) {
        matcher.restart(true)
        continue
      }
      if (matcher.gestureId != gestureId) {
        matcher.cancelGesture(event, false)
      }
    }
    listener.onGestureCompleted(gestureEvent)
  }

  fun isMultiFingerGesturesEnabled(): Boolean = multiFingerGesturesEnabled

  fun setMultiFingerGesturesEnabled(mode: Boolean) {
    if (multiFingerGesturesEnabled != mode) {
      multiFingerGesturesEnabled = mode
      if (mode) {
        gestures.addAll(multiFingerGestures)
      } else {
        gestures.removeAll(multiFingerGestures)
      }
    }
  }

  fun isTwoFingerPassthroughEnabled(): Boolean = twoFingerPassthroughEnabled

  fun setTwoFingerPassthroughEnabled(mode: Boolean) {
    if (twoFingerPassthroughEnabled != mode) {
      twoFingerPassthroughEnabled = mode
      if (!mode) {
        multiFingerGestures.addAll(twoFingerSwipes)
        if (multiFingerGesturesEnabled) {
          gestures.addAll(twoFingerSwipes)
        }
      } else {
        multiFingerGestures.removeAll(twoFingerSwipes)
        gestures.removeAll(twoFingerSwipes)
      }
    }
  }

  /**
   * This class helps to collect data (saved time to enter Touch Explore), in addition to the
   * fundamental Gesture analytic event.
   */
  class TapToTouchExploreAnalyticsEvent(event: Int, gestureId: Int, @JvmField var savedTimeMs: Int) :
    GestureAnalyticsEvent(event, gestureId)

  companion object {
    const val GESTURE_FAKED_SPLIT_TYPING = -3
    const val GESTURE_TAP_HOLD_AND_2ND_FINGER_FORWARD_DOUBLE_TAP = -4
    const val GESTURE_TAP_HOLD_AND_2ND_FINGER_BACKWARD_DOUBLE_TAP = -5
    const val GESTURE_TOUCH_EXPLORE = -6
    const val GESTURE_TAP_UP_TOUCH_EXPLORE = -7
    const val GESTURE_FAKED_SPLIT_TYPING_AND_HOLD = -8

    // Match the value of GESTURE_ID_2FINGER_1TAP_HOLD in TalkBack.
    const val GESTURE_2_FINGER_SINGLE_TAP_AND_HOLD = 63

    private const val LOG_TAG = "GestureManifold"
  }
}
