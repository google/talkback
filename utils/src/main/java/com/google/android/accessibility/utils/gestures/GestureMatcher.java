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
 */

package com.google.android.accessibility.utils.gestures;

import static android.accessibilityservice.AccessibilityService.GESTURE_2_FINGER_DOUBLE_TAP;
import static android.accessibilityservice.AccessibilityService.GESTURE_2_FINGER_DOUBLE_TAP_AND_HOLD;
import static android.accessibilityservice.AccessibilityService.GESTURE_2_FINGER_SINGLE_TAP;
import static android.accessibilityservice.AccessibilityService.GESTURE_2_FINGER_SWIPE_DOWN;
import static android.accessibilityservice.AccessibilityService.GESTURE_2_FINGER_SWIPE_LEFT;
import static android.accessibilityservice.AccessibilityService.GESTURE_2_FINGER_SWIPE_RIGHT;
import static android.accessibilityservice.AccessibilityService.GESTURE_2_FINGER_SWIPE_UP;
import static android.accessibilityservice.AccessibilityService.GESTURE_2_FINGER_TRIPLE_TAP;
import static android.accessibilityservice.AccessibilityService.GESTURE_2_FINGER_TRIPLE_TAP_AND_HOLD;
import static android.accessibilityservice.AccessibilityService.GESTURE_3_FINGER_DOUBLE_TAP;
import static android.accessibilityservice.AccessibilityService.GESTURE_3_FINGER_DOUBLE_TAP_AND_HOLD;
import static android.accessibilityservice.AccessibilityService.GESTURE_3_FINGER_SINGLE_TAP;
import static android.accessibilityservice.AccessibilityService.GESTURE_3_FINGER_SINGLE_TAP_AND_HOLD;
import static android.accessibilityservice.AccessibilityService.GESTURE_3_FINGER_SWIPE_DOWN;
import static android.accessibilityservice.AccessibilityService.GESTURE_3_FINGER_SWIPE_LEFT;
import static android.accessibilityservice.AccessibilityService.GESTURE_3_FINGER_SWIPE_RIGHT;
import static android.accessibilityservice.AccessibilityService.GESTURE_3_FINGER_SWIPE_UP;
import static android.accessibilityservice.AccessibilityService.GESTURE_3_FINGER_TRIPLE_TAP;
import static android.accessibilityservice.AccessibilityService.GESTURE_3_FINGER_TRIPLE_TAP_AND_HOLD;
import static android.accessibilityservice.AccessibilityService.GESTURE_4_FINGER_DOUBLE_TAP;
import static android.accessibilityservice.AccessibilityService.GESTURE_4_FINGER_DOUBLE_TAP_AND_HOLD;
import static android.accessibilityservice.AccessibilityService.GESTURE_4_FINGER_SINGLE_TAP;
import static android.accessibilityservice.AccessibilityService.GESTURE_4_FINGER_SWIPE_DOWN;
import static android.accessibilityservice.AccessibilityService.GESTURE_4_FINGER_SWIPE_LEFT;
import static android.accessibilityservice.AccessibilityService.GESTURE_4_FINGER_SWIPE_RIGHT;
import static android.accessibilityservice.AccessibilityService.GESTURE_4_FINGER_SWIPE_UP;
import static android.accessibilityservice.AccessibilityService.GESTURE_4_FINGER_TRIPLE_TAP;
import static android.accessibilityservice.AccessibilityService.GESTURE_DOUBLE_TAP;
import static android.accessibilityservice.AccessibilityService.GESTURE_DOUBLE_TAP_AND_HOLD;
import static android.accessibilityservice.AccessibilityService.GESTURE_SWIPE_DOWN;
import static android.accessibilityservice.AccessibilityService.GESTURE_SWIPE_DOWN_AND_LEFT;
import static android.accessibilityservice.AccessibilityService.GESTURE_SWIPE_DOWN_AND_RIGHT;
import static android.accessibilityservice.AccessibilityService.GESTURE_SWIPE_DOWN_AND_UP;
import static android.accessibilityservice.AccessibilityService.GESTURE_SWIPE_LEFT;
import static android.accessibilityservice.AccessibilityService.GESTURE_SWIPE_LEFT_AND_DOWN;
import static android.accessibilityservice.AccessibilityService.GESTURE_SWIPE_LEFT_AND_RIGHT;
import static android.accessibilityservice.AccessibilityService.GESTURE_SWIPE_LEFT_AND_UP;
import static android.accessibilityservice.AccessibilityService.GESTURE_SWIPE_RIGHT;
import static android.accessibilityservice.AccessibilityService.GESTURE_SWIPE_RIGHT_AND_DOWN;
import static android.accessibilityservice.AccessibilityService.GESTURE_SWIPE_RIGHT_AND_LEFT;
import static android.accessibilityservice.AccessibilityService.GESTURE_SWIPE_RIGHT_AND_UP;
import static android.accessibilityservice.AccessibilityService.GESTURE_SWIPE_UP;
import static android.accessibilityservice.AccessibilityService.GESTURE_SWIPE_UP_AND_DOWN;
import static android.accessibilityservice.AccessibilityService.GESTURE_SWIPE_UP_AND_LEFT;
import static android.accessibilityservice.AccessibilityService.GESTURE_SWIPE_UP_AND_RIGHT;

import android.os.Build;
import android.os.Handler;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import com.google.android.libraries.accessibility.utils.log.LogUtils;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

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
@RequiresApi(Build.VERSION_CODES.S)
public abstract class GestureMatcher {
  // Potential states for this individual gesture matcher.
  /**
   * In STATE_CLEAR, this matcher is accepting new motion events but has not formally signaled that
   * there is enough data to judge that a gesture has started.
   */
  public static final int STATE_CLEAR = 0;
  /**
   * In STATE_GESTURE_STARTED, this matcher continues to accept motion events and it has signaled to
   * the listener that what looks like the specified gesture has started.
   */
  public static final int STATE_GESTURE_STARTED = 1;
  /**
   * In STATE_GESTURE_COMPLETED, this matcher has successfully matched the specified gesture. and
   * will not accept motion events until it is cleared.
   */
  public static final int STATE_GESTURE_COMPLETED = 2;
  /**
   * In STATE_GESTURE_CANCELED, this matcher will not accept new motion events because it is
   * impossible that this set of motion events will match the specified gesture.
   */
  public static final int STATE_GESTURE_CANCELED = 3;

  @Retention(RetentionPolicy.SOURCE)
  @IntDef({STATE_CLEAR, STATE_GESTURE_STARTED, STATE_GESTURE_COMPLETED, STATE_GESTURE_CANCELED})
  @interface State {}

  @State private int state = STATE_CLEAR;

  @IntDef({
    GESTURE_2_FINGER_SINGLE_TAP,
    GESTURE_2_FINGER_DOUBLE_TAP,
    GESTURE_2_FINGER_DOUBLE_TAP_AND_HOLD,
    GESTURE_2_FINGER_TRIPLE_TAP,
    GESTURE_2_FINGER_TRIPLE_TAP_AND_HOLD,
    GESTURE_3_FINGER_SINGLE_TAP,
    GESTURE_3_FINGER_SINGLE_TAP_AND_HOLD,
    GESTURE_3_FINGER_DOUBLE_TAP,
    GESTURE_3_FINGER_DOUBLE_TAP_AND_HOLD,
    GESTURE_3_FINGER_TRIPLE_TAP,
    GESTURE_3_FINGER_TRIPLE_TAP_AND_HOLD,
    GESTURE_DOUBLE_TAP,
    GESTURE_DOUBLE_TAP_AND_HOLD,
    GESTURE_SWIPE_UP,
    GESTURE_SWIPE_UP_AND_LEFT,
    GESTURE_SWIPE_UP_AND_DOWN,
    GESTURE_SWIPE_UP_AND_RIGHT,
    GESTURE_SWIPE_DOWN,
    GESTURE_SWIPE_DOWN_AND_LEFT,
    GESTURE_SWIPE_DOWN_AND_UP,
    GESTURE_SWIPE_DOWN_AND_RIGHT,
    GESTURE_SWIPE_LEFT,
    GESTURE_SWIPE_LEFT_AND_UP,
    GESTURE_SWIPE_LEFT_AND_RIGHT,
    GESTURE_SWIPE_LEFT_AND_DOWN,
    GESTURE_SWIPE_RIGHT,
    GESTURE_SWIPE_RIGHT_AND_UP,
    GESTURE_SWIPE_RIGHT_AND_LEFT,
    GESTURE_SWIPE_RIGHT_AND_DOWN,
    GESTURE_2_FINGER_SWIPE_DOWN,
    GESTURE_2_FINGER_SWIPE_LEFT,
    GESTURE_2_FINGER_SWIPE_RIGHT,
    GESTURE_2_FINGER_SWIPE_UP,
    GESTURE_3_FINGER_SWIPE_DOWN,
    GESTURE_3_FINGER_SWIPE_LEFT,
    GESTURE_3_FINGER_SWIPE_RIGHT,
    GESTURE_3_FINGER_SWIPE_UP,
    GESTURE_4_FINGER_DOUBLE_TAP,
    GESTURE_4_FINGER_DOUBLE_TAP_AND_HOLD,
    GESTURE_4_FINGER_SINGLE_TAP,
    GESTURE_4_FINGER_SWIPE_DOWN,
    GESTURE_4_FINGER_SWIPE_LEFT,
    GESTURE_4_FINGER_SWIPE_RIGHT,
    GESTURE_4_FINGER_SWIPE_UP,
    GESTURE_4_FINGER_TRIPLE_TAP
  })
  @Retention(RetentionPolicy.SOURCE)
  @interface GestureId {}

  // The id number of the gesture that gets passed to accessibility services.
  @GestureId private final int gestureId;
  // handler for asynchronous operations like timeouts
  private final Handler handler;

  private StateChangeListener listener = null;

  // Use this to transition to new states after a delay.
  // e.g. cancel or complete after some timeout.
  // Convenience functions for tapTimeout and doubleTapTimeout are already defined here.
  protected final DelayedTransition delayedTransition;

  protected GestureMatcher(int gestureId, Handler handler, StateChangeListener listener) {
    this.gestureId = gestureId;
    this.handler = handler;
    delayedTransition = new DelayedTransition();
    this.listener = listener;
  }

  /**
   * Resets all state information for this matcher. Subclasses that include their own state
   * information should override this method to reset their own state information and call
   * super.clear().
   */
  public void clear() {
    state = STATE_CLEAR;
    cancelPendingTransitions();
  }

  public final int getState() {
    return state;
  }

  /**
   * Transitions to a new state and notifies any listeners. Note that any pending transitions are
   * canceled.
   */
  private void setState(@State int state, MotionEvent event) {
    setState(state, event, true);
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
  private void setState(@State int state, MotionEvent event, boolean notify) {
    this.state = state;
    cancelPendingTransitions();
    if (notify && listener != null) {
      listener.onStateChanged(gestureId, state, event);
    }
  }

  /** Indicates that there is evidence to suggest that this gesture has started. */
  protected final void startGesture(MotionEvent event) {
    setState(STATE_GESTURE_STARTED, event);
  }

  /** Indicates this stream of motion events can no longer match this gesture. */
  public final void cancelGesture(MotionEvent event, boolean notify) {
    setState(STATE_GESTURE_CANCELED, event, notify);
  }

  public final void cancelGesture(MotionEvent event) {
    setState(STATE_GESTURE_CANCELED, event);
  }

  /** Indicates this gesture is completed. */
  protected final void completeGesture(MotionEvent event) {
    setState(STATE_GESTURE_COMPLETED, event);
  }

  public final void setListener(@NonNull StateChangeListener listener) {
    this.listener = listener;
  }

  public int getGestureId() {
    return gestureId;
  }

  /**
   * Process a motion event and attempt to match it to this gesture.
   *
   * @param event the event as passed in from the event stream.
   * @return the state of this matcher.
   */
  public final int onMotionEvent(MotionEvent event) {
    if (state == STATE_GESTURE_CANCELED || state == STATE_GESTURE_COMPLETED) {
      return state;
    }
    switch (event.getActionMasked()) {
      case MotionEvent.ACTION_DOWN:
        onDown(event);
        break;
      case MotionEvent.ACTION_POINTER_DOWN:
        onPointerDown(event);
        break;
      case MotionEvent.ACTION_MOVE:
        onMove(event);
        break;
      case MotionEvent.ACTION_POINTER_UP:
        onPointerUp(event);
        break;
      case MotionEvent.ACTION_UP:
        onUp(event);
        break;
      default:
        // Cancel because of invalid event.
        setState(STATE_GESTURE_CANCELED, event);
        break;
    }
    return state;
  }

  /**
   * Matchers override this method to respond to ACTION_DOWN events. ACTION_DOWN events indicate the
   * first finger has touched the screen. If not overridden the default response is to do nothing.
   */
  protected void onDown(MotionEvent event) {}

  /**
   * Matchers override this method to respond to ACTION_POINTER_DOWN events. ACTION_POINTER_DOWN
   * indicates that more than one finger has touched the screen. If not overridden the default
   * response is to do nothing.
   *
   * @param event the event as passed in from the event stream.
   */
  protected void onPointerDown(MotionEvent event) {}

  /**
   * Matchers override this method to respond to ACTION_MOVE events. ACTION_MOVE indicates that one
   * or fingers has moved. If not overridden the default response is to do nothing.
   *
   * @param event the event as passed in from the event stream.
   */
  protected void onMove(MotionEvent event) {}

  /**
   * Matchers override this method to respond to ACTION_POINTER_UP events. ACTION_POINTER_UP
   * indicates that a finger has lifted from the screen but at least one finger continues to touch
   * the screen. If not overridden the default response is to do nothing.
   *
   * @param event the event as passed in from the event stream.
   */
  protected void onPointerUp(MotionEvent event) {}

  /**
   * Matchers override this method to respond to ACTION_UP events. ACTION_UP indicates that there
   * are no more fingers touching the screen. If not overridden the default response is to do
   * nothing.
   *
   * @param event the event as passed in from the event stream.
   */
  protected void onUp(MotionEvent event) {}

  /** Cancels this matcher after the tap timeout. Any pending state transitions are removed. */
  protected void cancelAfterTapTimeout(MotionEvent event) {
    cancelAfter(ViewConfiguration.getTapTimeout(), event);
  }

  /** Cancels this matcher after the double tap timeout. Any pending cancelations are removed. */
  protected final void cancelAfterDoubleTapTimeout(MotionEvent event) {
    cancelAfter(ViewConfiguration.getDoubleTapTimeout(), event);
  }

  /**
   * Cancels this matcher after the specified timeout. Any pending cancelations are removed. Used to
   * prevent this matcher from accepting motion events until it is cleared.
   */
  protected final void cancelAfter(long timeout, MotionEvent event) {
    delayedTransition.cancel();
    delayedTransition.post(STATE_GESTURE_CANCELED, timeout, event);
  }

  /** Cancels any delayed transitions between states scheduled for this matcher. */
  protected final void cancelPendingTransitions() {
    delayedTransition.cancel();
  }

  /**
   * Signals that this gesture has been completed after the tap timeout has expired. Used to ensure
   * that there is no conflict with another gesture or for gestures that explicitly require a hold.
   */
  protected final void completeAfterLongPressTimeout(MotionEvent event) {
    completeAfter(ViewConfiguration.getLongPressTimeout(), event);
  }

  /**
   * Signals that this gesture has been completed after the tap timeout has expired. Used to ensure
   * that there is no conflict with another gesture or for gestures that explicitly require a hold.
   */
  protected final void completeAfterTapTimeout(MotionEvent event) {
    completeAfter(ViewConfiguration.getTapTimeout(), event);
  }

  /**
   * Signals that this gesture has been completed after the specified timeout has expired. Used to
   * ensure that there is no conflict with another gesture or for gestures that explicitly require a
   * hold.
   */
  protected final void completeAfter(long timeout, MotionEvent event) {
    delayedTransition.cancel();
    delayedTransition.post(STATE_GESTURE_COMPLETED, timeout, event);
  }

  /**
   * Signals that this gesture has been completed after the double-tap timeout has expired. Used to
   * ensure that there is no conflict with another gesture or for gestures that explicitly require a
   * hold.
   */
  protected final void completeAfterDoubleTapTimeout(MotionEvent event) {
    completeAfter(ViewConfiguration.getDoubleTapTimeout(), event);
  }

  static String getStateSymbolicName(@State int state) {
    switch (state) {
      case STATE_CLEAR:
        return "STATE_CLEAR";
      case STATE_GESTURE_STARTED:
        return "STATE_GESTURE_STARTED";
      case STATE_GESTURE_COMPLETED:
        return "STATE_GESTURE_COMPLETED";
      case STATE_GESTURE_CANCELED:
        return "STATE_GESTURE_CANCELED";
      default:
        return "Unknown state: " + state;
    }
  }

  /**
   * Returns a readable name for this matcher that can be displayed to the user and in system logs.
   */
  protected abstract String getGestureName();

  /**
   * Returns a String representation of this matcher. Each matcher can override this method to add
   * extra state information to the string representation.
   */
  @Override
  public String toString() {
    return getGestureName() + ":" + getStateSymbolicName(state);
  }

  /** This class allows matchers to transition between states on a delay. */
  protected final class DelayedTransition implements Runnable {

    private static final String LOG_TAG = "GestureMatcher.DelayedTransition";
    int targetState;
    MotionEvent event;

    public void cancel() {
      // Avoid meaningless debug messages.
      synchronized (GestureMatcher.this) {
        if (isPending()) {
          LogUtils.v(
              LOG_TAG,
              "%s: canceling delayed transition to %s",
              getGestureName(),
              getStateSymbolicName(targetState));
        }
        handler.removeCallbacks(this);
        recycleEvent();
      }
    }

    public void post(int state, long delay, MotionEvent event) {
      synchronized (GestureMatcher.this) {
        this.targetState = state;
        // Just in case the cancel is not performed immediately before post.
        recycleEvent();
        this.event = MotionEvent.obtain(event);
        handler.postDelayed(this, delay);
        LogUtils.v(
            LOG_TAG,
            "%s: posting delayed transition to %s",
            getGestureName(),
            getStateSymbolicName(targetState));
      }
    }

    public boolean isPending() {
      return handler.hasCallbacks(this);
    }

    public void forceSendAndRemove() {
      if (isPending()) {
        run();
        cancel();
      }
    }

    @Override
    public void run() {
      synchronized (GestureMatcher.this) {
        if (event == null) {
          return;
        }
        LogUtils.v(
            LOG_TAG,
            "%s: executing delayed transition to %s",
            getGestureName(),
            getStateSymbolicName(targetState));
        setState(targetState, event);
        recycleEvent();
      }
    }

    private void recycleEvent() {
      if (event == null) {
        return;
      }
      event.recycle();
      event = null;
    }
  }

  /** Interface to allow a class to listen for state changes in a specific gesture matcher */
  public interface StateChangeListener {

    void onStateChanged(int gestureId, int state, MotionEvent event);
  }
}
