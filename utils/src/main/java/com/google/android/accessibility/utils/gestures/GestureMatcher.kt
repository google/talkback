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

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.os.Build
import android.os.Handler
import android.util.Log.ERROR
import android.util.Log.VERBOSE
import android.view.MotionEvent
import android.view.ViewConfiguration
import androidx.annotation.IntDef
import androidx.annotation.RequiresApi
import com.google.android.accessibility.utils.Performance
import com.google.android.accessibility.utils.Performance.EventId
import com.google.android.libraries.accessibility.utils.log.LogUtils
import com.google.errorprone.annotations.CanIgnoreReturnValue

/**
 * This class describes a common base for gesture matchers. A gesture matcher checks a series of
 * motion events against a single gesture. Coordinating the individual gesture matchers is done by
 * the GestureManifold. To create a new Gesture, extend this class and override the onDown, onMove,
 * onUp, etc methods as necessary. If you don't override a method your matcher will do nothing in
 * response to that type of event. Finally, be sure to give your gesture a name by overriding
 * getGestureName().
 *
 * @hide
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
abstract class GestureMatcher protected constructor(
  // The id number of the gesture that gets passed to accessibility services.
  @get:JvmName("getGestureId") val gestureId: Int,
  // handler for asynchronous operations like timeouts
  private val handler: Handler,
  private var listener: StateChangeListener?,
  private val logger: AnalyticsEventLogger,
) {

  @Retention(AnnotationRetention.SOURCE)
  @IntDef(
    STATE_CLEAR,
    STATE_GESTURE_STARTED,
    STATE_GESTURE_COMPLETED,
    STATE_GESTURE_CANCELED,
    STATE_GESTURE_PROCESSING,
  )
  internal annotation class State

  @Retention(AnnotationRetention.SOURCE)
  @IntDef(
    AccessibilityService.GESTURE_2_FINGER_SINGLE_TAP,
    AccessibilityService.GESTURE_2_FINGER_DOUBLE_TAP,
    AccessibilityService.GESTURE_2_FINGER_DOUBLE_TAP_AND_HOLD,
    AccessibilityService.GESTURE_2_FINGER_TRIPLE_TAP,
    AccessibilityService.GESTURE_2_FINGER_TRIPLE_TAP_AND_HOLD,
    AccessibilityService.GESTURE_3_FINGER_SINGLE_TAP,
    AccessibilityService.GESTURE_3_FINGER_SINGLE_TAP_AND_HOLD,
    AccessibilityService.GESTURE_3_FINGER_DOUBLE_TAP,
    AccessibilityService.GESTURE_3_FINGER_DOUBLE_TAP_AND_HOLD,
    AccessibilityService.GESTURE_3_FINGER_TRIPLE_TAP,
    AccessibilityService.GESTURE_3_FINGER_TRIPLE_TAP_AND_HOLD,
    AccessibilityService.GESTURE_DOUBLE_TAP,
    AccessibilityService.GESTURE_DOUBLE_TAP_AND_HOLD,
    AccessibilityService.GESTURE_SWIPE_UP,
    AccessibilityService.GESTURE_SWIPE_UP_AND_LEFT,
    AccessibilityService.GESTURE_SWIPE_UP_AND_DOWN,
    AccessibilityService.GESTURE_SWIPE_UP_AND_RIGHT,
    AccessibilityService.GESTURE_SWIPE_DOWN,
    AccessibilityService.GESTURE_SWIPE_DOWN_AND_LEFT,
    AccessibilityService.GESTURE_SWIPE_DOWN_AND_UP,
    AccessibilityService.GESTURE_SWIPE_DOWN_AND_RIGHT,
    AccessibilityService.GESTURE_SWIPE_LEFT,
    AccessibilityService.GESTURE_SWIPE_LEFT_AND_UP,
    AccessibilityService.GESTURE_SWIPE_LEFT_AND_RIGHT,
    AccessibilityService.GESTURE_SWIPE_LEFT_AND_DOWN,
    AccessibilityService.GESTURE_SWIPE_RIGHT,
    AccessibilityService.GESTURE_SWIPE_RIGHT_AND_UP,
    AccessibilityService.GESTURE_SWIPE_RIGHT_AND_LEFT,
    AccessibilityService.GESTURE_SWIPE_RIGHT_AND_DOWN,
    AccessibilityService.GESTURE_2_FINGER_SWIPE_DOWN,
    AccessibilityService.GESTURE_2_FINGER_SWIPE_LEFT,
    AccessibilityService.GESTURE_2_FINGER_SWIPE_RIGHT,
    AccessibilityService.GESTURE_2_FINGER_SWIPE_UP,
    AccessibilityService.GESTURE_3_FINGER_SWIPE_DOWN,
    AccessibilityService.GESTURE_3_FINGER_SWIPE_LEFT,
    AccessibilityService.GESTURE_3_FINGER_SWIPE_RIGHT,
    AccessibilityService.GESTURE_3_FINGER_SWIPE_UP,
    AccessibilityService.GESTURE_4_FINGER_DOUBLE_TAP,
    AccessibilityService.GESTURE_4_FINGER_DOUBLE_TAP_AND_HOLD,
    AccessibilityService.GESTURE_4_FINGER_SINGLE_TAP,
    AccessibilityService.GESTURE_4_FINGER_SWIPE_DOWN,
    AccessibilityService.GESTURE_4_FINGER_SWIPE_LEFT,
    AccessibilityService.GESTURE_4_FINGER_SWIPE_RIGHT,
    AccessibilityService.GESTURE_4_FINGER_SWIPE_UP,
    AccessibilityService.GESTURE_4_FINGER_TRIPLE_TAP,
  )
  internal annotation class GestureId

  @State
  var state: Int = STATE_CLEAR
    private set

  // Use this to transition to new states after a delay.
  // e.g. cancel or complete after some timeout.
  // Convenience functions for tapTimeout and doubleTapTimeout are already defined here.
  protected val delayedTransition: DelayedTransition = DelayedTransition()

  protected var logMotionEvent: Boolean = false

  open fun onConfigurationChanged(context: Context) {}

  fun enableLogMotionEvent() {
    logMotionEvent = true
  }

  /**
   * Resets all state information for this matcher. Subclasses that include their own state
   * information should override this method to reset their own state information and call
   * super.clear().
   */
  open fun clear() {
    state = STATE_CLEAR
    cancelPendingTransitions()
  }

  /**
   * TalkBack maintains the touch-interaction & touch-explore state by itself. When the system
   * detects a valid split-tap, user can keep his finger touched on screen and request the state to
   * touch-explore so that the gesture detector can resume again.
   *
   * @param pending tells the Split-tap detector should wait extra events then back to its detecting
   *     state.
   */
  open fun restart(pending: Boolean) {
    state = STATE_CLEAR
    cancelPendingTransitions()
  }

  open fun debugMotionEvent(tag: String, format: String, vararg args: Any?) {
    if (logMotionEvent) {
      LogUtils.v(tag, format, *args)
    }
  }

  /**
   * Transitions to a new state and notifies any listeners. Note that any pending transitions are
   * canceled.
   *
   * @param state the new state for the gesture detector.
   * @param event the MotionEvent caused the state transition.
   * @param notify should notify the upper listener or not about the state change. This can avoid
   *     the upper listeners receive call back more than once (especially for cancel event).
   */
  private fun setState(@State state: Int, event: MotionEvent, notify: Boolean = true) {
    if (state != STATE_GESTURE_PROCESSING) {
      this.state = state
      cancelPendingTransitions()
    }
    if (notify) {
      listener?.onStateChanged(gestureId, state, event)
    }
  }

  /** Indicates that there is evidence to suggest that this gesture has started. */
  protected fun startGesture(event: MotionEvent) {
    setState(STATE_GESTURE_STARTED, event)
  }

  /** Indicates this stream of motion events can no longer match this gesture. */
  fun cancelGesture(event: MotionEvent, notify: Boolean) {
    setState(STATE_GESTURE_CANCELED, event, notify)
  }

  fun cancelGesture(event: MotionEvent) {
    setState(STATE_GESTURE_CANCELED, event)
  }

  /** Indicates this gesture is completed. */
  protected fun completeGesture(eventId: EventId?, event: MotionEvent) {
    Performance.getInstance().onGestureLastMotionEventTime(eventId, event.eventTime)
    setState(STATE_GESTURE_COMPLETED, event)
  }

  /** Extend the touch explore timer window. */
  protected fun processGesture(eventId: EventId?, event: MotionEvent) {
    setState(STATE_GESTURE_PROCESSING, event)
  }

  protected fun analyticsEvent(analyticsEvent: GestureAnalyticsEvent) {
    logger.logAnalyticsEvent(analyticsEvent)
  }

  fun setListener(listener: StateChangeListener) {
    this.listener = listener
  }

  /**
   * Returns true to indicate that the gesture would not be cancelled when the touch-exploring mode
   * is still ongoing event gesture completed. For example, split-typing could keep alive when user
   * is touch-exploring the screen.
   */
  open fun bypassCancelByTapUpToTouchExplore(): Boolean = false

  /**
   * Process a motion event and attempt to match it to this gesture.
   *
   * @param event the event as passed in from the event stream.
   * @return the state of this matcher.
   */
  @CanIgnoreReturnValue
  fun onMotionEvent(eventId: EventId?, event: MotionEvent): Int {
    if (state == STATE_GESTURE_CANCELED || state == STATE_GESTURE_COMPLETED) {
      return state
    }
    when (event.actionMasked) {
      MotionEvent.ACTION_DOWN -> onDown(eventId, event)
      MotionEvent.ACTION_POINTER_DOWN -> onPointerDown(eventId, event)
      MotionEvent.ACTION_MOVE -> onMove(eventId, event)
      MotionEvent.ACTION_POINTER_UP -> onPointerUp(eventId, event)
      MotionEvent.ACTION_UP -> onUp(eventId, event)
      else ->
        // Cancel because of invalid event.
        setState(STATE_GESTURE_CANCELED, event)
    }
    return state
  }

  /**
   * Matchers override this method to respond to ACTION_DOWN events. ACTION_DOWN events indicate the
   * first finger has touched the screen. If not overridden the default response is to do nothing.
   */
  protected open fun onDown(eventId: EventId?, event: MotionEvent) {}

  /**
   * Matchers override this method to respond to ACTION_POINTER_DOWN events. ACTION_POINTER_DOWN
   * indicates that more than one finger has touched the screen. If not overridden the default
   * response is to do nothing.
   *
   * @param event the event as passed in from the event stream.
   */
  protected open fun onPointerDown(eventId: EventId?, event: MotionEvent) {}

  /**
   * Matchers override this method to respond to ACTION_MOVE events. ACTION_MOVE indicates that one
   * or fingers has moved. If not overridden the default response is to do nothing.
   *
   * @param event the event as passed in from the event stream.
   */
  protected open fun onMove(eventId: EventId?, event: MotionEvent) {}

  /**
   * Matchers override this method to respond to ACTION_POINTER_UP events. ACTION_POINTER_UP
   * indicates that a finger has lifted from the screen but at least one finger continues to touch
   * the screen. If not overridden the default response is to do nothing.
   *
   * @param event the event as passed in from the event stream.
   */
  protected open fun onPointerUp(eventId: EventId?, event: MotionEvent) {}

  /**
   * Matchers override this method to respond to ACTION_UP events. ACTION_UP indicates that there
   * are no more fingers touching the screen. If not overridden the default response is to do
   * nothing.
   *
   * @param event the event as passed in from the event stream.
   */
  protected open fun onUp(eventId: EventId?, event: MotionEvent) {}

  /** Cancels this matcher after the tap timeout. Any pending state transitions are removed. */
  protected open fun cancelAfterTapTimeout(eventId: EventId?, event: MotionEvent) {
    cancelAfter(ViewConfiguration.getTapTimeout().toLong(), event)
  }

  /** Cancels this matcher after the double tap timeout. Any pending cancelations are removed. */
  protected fun cancelAfterDoubleTapTimeout(event: MotionEvent) {
    cancelAfter(ViewConfiguration.getDoubleTapTimeout().toLong(), event)
  }

  /**
   * Cancels this matcher after the specified timeout. Any pending cancelations are removed. Used to
   * prevent this matcher from accepting motion events until it is cleared.
   */
  protected fun cancelAfter(timeout: Long, event: MotionEvent) {
    delayedTransition.cancel()
    delayedTransition.post(STATE_GESTURE_CANCELED, timeout, event)
  }

  /** Cancels any delayed transitions between states scheduled for this matcher. */
  protected fun cancelPendingTransitions() {
    delayedTransition.cancel()
  }

  /**
   * Signals that this gesture has been completed after the tap timeout has expired. Used to ensure
   * that there is no conflict with another gesture or for gestures that explicitly require a hold.
   */
  protected fun completeAfterLongPressTimeout(eventId: EventId?, event: MotionEvent) {
    completeAfter(ViewConfiguration.getLongPressTimeout().toLong(), eventId, event)
  }

  /**
   * Signals that this gesture has been completed after the tap timeout has expired. Used to ensure
   * that there is no conflict with another gesture or for gestures that explicitly require a hold.
   */
  protected fun completeAfterTapTimeout(eventId: EventId?, event: MotionEvent) {
    completeAfter(ViewConfiguration.getTapTimeout().toLong(), eventId, event)
  }

  /**
   * Signals that this gesture has been completed after the specified timeout has expired. Used to
   * ensure that there is no conflict with another gesture or for gestures that explicitly require a
   * hold.
   */
  protected fun completeAfter(timeout: Long, eventId: EventId?, event: MotionEvent) {
    delayedTransition.cancel()
    Performance.getInstance().onGestureLastMotionEventTime(eventId, event.eventTime)
    delayedTransition.post(STATE_GESTURE_COMPLETED, timeout, event)
  }

  /**
   * Signals that this gesture has been completed after the double-tap timeout has expired. Used to
   * ensure that there is no conflict with another gesture or for gestures that explicitly require a
   * hold.
   */
  protected fun completeAfterDoubleTapTimeout(eventId: EventId?, event: MotionEvent) {
    completeAfter(ViewConfiguration.getDoubleTapTimeout().toLong(), eventId, event)
  }

  internal fun gestureMotionEventLog(logLevel: Int, format: String, vararg args: Any?) {
    if (logMotionEvent) {
      when (logLevel) {
        ERROR -> LogUtils.e(getGestureName(), format, *args)
        VERBOSE -> LogUtils.v(getGestureName(), format, *args)
        else -> LogUtils.v(getGestureName(), format, *args)
      }
    }
  }

  /**
   * Returns a readable name for this matcher that can be displayed to the user and in system logs.
   */
  protected abstract fun getGestureName(): String

  /**
   * Returns a String representation of this matcher. Each matcher can override this method to add
   * extra state information to the string representation.
   */
  override fun toString(): String = getGestureName() + ":" + getStateSymbolicName(state)

  /** This class allows matchers to transition between states on a delay. */
  protected inner class DelayedTransition : Runnable {
    private var targetState = 0
    private var event: MotionEvent? = null

    fun cancel() {
      // Avoid meaningless debug messages.
      synchronized(this@GestureMatcher) {
        if (isPending) {
          LogUtils.v(
            DELAYED_TRANSITION_LOG_TAG,
            "%s: canceling delayed transition to %s",
            getGestureName(),
            getStateSymbolicName(targetState),
          )
        }
        handler.removeCallbacks(this)
        recycleEvent()
      }
    }

    fun post(state: Int, delay: Long, event: MotionEvent) {
      synchronized(this@GestureMatcher) {
        this.targetState = state
        // Just in case the cancel is not performed immediately before post.
        recycleEvent()
        this.event = MotionEvent.obtain(event)
        handler.postDelayed(this, delay)
        LogUtils.v(
          DELAYED_TRANSITION_LOG_TAG,
          "%s: posting delayed transition to %s",
          getGestureName(),
          getStateSymbolicName(targetState),
        )
      }
    }

    val isPending: Boolean
      get() = handler.hasCallbacks(this)

    fun forceSendAndRemove() {
      if (isPending) {
        run()
        cancel()
      }
    }

    override fun run() {
      synchronized(this@GestureMatcher) {
        val event = this.event ?: return
        LogUtils.v(
          DELAYED_TRANSITION_LOG_TAG,
          "%s: executing delayed transition to %s",
          getGestureName(),
          getStateSymbolicName(targetState),
        )
        setState(targetState, event)
        recycleEvent()
      }
    }

    private fun recycleEvent() {
      val event = this.event ?: return
      event.recycle()
      this.event = null
    }
  }

  /** Interface to allow a class to listen for state changes in a specific gesture matcher */
  fun interface StateChangeListener {
    fun onStateChanged(gestureId: Int, state: Int, event: MotionEvent)
  }

  /** Interface to handle the analytics event for a gesture. */
  fun interface AnalyticsEventLogger {
    fun logAnalyticsEvent(analyticsEvent: GestureAnalyticsEvent)
  }

  companion object {
    // Potential states for this individual gesture matcher.
    /**
     * In STATE_CLEAR, this matcher is accepting new motion events but has not formally signaled
     * that there is enough data to judge that a gesture has started.
     */
    const val STATE_CLEAR = 0

    /**
     * In STATE_GESTURE_STARTED, this matcher continues to accept motion events and it has signaled
     * to the listener that what looks like the specified gesture has started.
     */
    const val STATE_GESTURE_STARTED = 1

    /**
     * In STATE_GESTURE_COMPLETED, this matcher has successfully matched the specified gesture. and
     * will not accept motion events until it is cleared.
     */
    const val STATE_GESTURE_COMPLETED = 2

    /**
     * In STATE_GESTURE_CANCELED, this matcher will not accept new motion events because it is
     * impossible that this set of motion events will match the specified gesture.
     */
    const val STATE_GESTURE_CANCELED = 3

    /**
     * In STATE_GESTURE_PROCESSING, this matcher does nothing but informing the listener which can
     * handle trivial thing such as extend the touch explore timer.
     */
    const val STATE_GESTURE_PROCESSING = 4

    private const val DELAYED_TRANSITION_LOG_TAG = "GestureMatcher.DelayedTransition"

    @JvmStatic
    internal fun getStateSymbolicName(@State state: Int): String =
      when (state) {
        STATE_CLEAR -> "STATE_CLEAR"
        STATE_GESTURE_STARTED -> "STATE_GESTURE_STARTED"
        STATE_GESTURE_COMPLETED -> "STATE_GESTURE_COMPLETED"
        STATE_GESTURE_CANCELED -> "STATE_GESTURE_CANCELED"
        STATE_GESTURE_PROCESSING -> "STATE_GESTURE_PROCESSING"
        else -> "Unknown state: $state"
      }
  }
}
