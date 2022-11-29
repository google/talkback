/*
 * Copyright (C) 2021s The Android Open Source Project
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
import static com.google.android.accessibility.utils.gestures.Swipe.DOWN;
import static com.google.android.accessibility.utils.gestures.Swipe.LEFT;
import static com.google.android.accessibility.utils.gestures.Swipe.RIGHT;
import static com.google.android.accessibility.utils.gestures.Swipe.UP;

import android.accessibilityservice.AccessibilityGestureEvent;
import android.content.Context;
import android.os.Build;
import android.view.MotionEvent;
import androidx.annotation.RequiresApi;
import com.google.android.libraries.accessibility.utils.log.LogUtils;
import java.util.ArrayList;
import java.util.List;

/**
 * This class coordinates a series of individual gesture matchers to serve as a unified gesture
 * detector. Gesture matchers are tied to a single gesture. It calls listener callback functions
 * when a gesture starts or completes.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
public class GestureManifold implements GestureMatcher.StateChangeListener {
  private static final String LOG_TAG = "GestureManifold";

  private final List<GestureMatcher> gestures = new ArrayList<>();
  private final Context context;
  private final int displayId;
  // Listener to be notified of gesture start and end.
  private Listener listener;
  // Whether multi-finger gestures are enabled.
  boolean multiFingerGesturesEnabled;
  // Whether the two-finger passthrough is enabled when multi-finger gestures are enabled.
  private boolean twoFingerPassthroughEnabled;
  // A list of all the multi-finger gestures, for easy adding and removal.
  private final List<GestureMatcher> multiFingerGestures = new ArrayList<>();
  // A list of two-finger swipes, for easy adding and removal when turning on or off two-finger
  // passthrough.
  private final List<GestureMatcher> twoFingerSwipes = new ArrayList<>();

  public GestureManifold(Context context, Listener listener, int displayId) {
    this.context = context;
    this.listener = listener;
    this.displayId = displayId;
    multiFingerGesturesEnabled = false;
    twoFingerPassthroughEnabled = false;
    // Set up gestures.
    // Start with double tap.
    gestures.add(new MultiTap(context, 2, GESTURE_DOUBLE_TAP, this));
    gestures.add(new MultiTapAndHold(context, 2, GESTURE_DOUBLE_TAP_AND_HOLD, this));
    // Second-finger double tap.
    gestures.add(new SecondFingerMultiTap(context, 2, GESTURE_DOUBLE_TAP, this));
    // One-direction swipes.
    gestures.add(new Swipe(context, RIGHT, GESTURE_SWIPE_RIGHT, this));
    gestures.add(new Swipe(context, LEFT, GESTURE_SWIPE_LEFT, this));
    gestures.add(new Swipe(context, UP, GESTURE_SWIPE_UP, this));
    gestures.add(new Swipe(context, DOWN, GESTURE_SWIPE_DOWN, this));
    // Two-direction swipes.
    gestures.add(new Swipe(context, LEFT, RIGHT, GESTURE_SWIPE_LEFT_AND_RIGHT, this));
    gestures.add(new Swipe(context, LEFT, UP, GESTURE_SWIPE_LEFT_AND_UP, this));
    gestures.add(new Swipe(context, LEFT, DOWN, GESTURE_SWIPE_LEFT_AND_DOWN, this));
    gestures.add(new Swipe(context, RIGHT, UP, GESTURE_SWIPE_RIGHT_AND_UP, this));
    gestures.add(new Swipe(context, RIGHT, DOWN, GESTURE_SWIPE_RIGHT_AND_DOWN, this));
    gestures.add(new Swipe(context, RIGHT, LEFT, GESTURE_SWIPE_RIGHT_AND_LEFT, this));
    gestures.add(new Swipe(context, DOWN, UP, GESTURE_SWIPE_DOWN_AND_UP, this));
    gestures.add(new Swipe(context, DOWN, LEFT, GESTURE_SWIPE_DOWN_AND_LEFT, this));
    gestures.add(new Swipe(context, DOWN, RIGHT, GESTURE_SWIPE_DOWN_AND_RIGHT, this));
    gestures.add(new Swipe(context, UP, DOWN, GESTURE_SWIPE_UP_AND_DOWN, this));
    gestures.add(new Swipe(context, UP, LEFT, GESTURE_SWIPE_UP_AND_LEFT, this));
    gestures.add(new Swipe(context, UP, RIGHT, GESTURE_SWIPE_UP_AND_RIGHT, this));
    // Set up multi-finger gestures to be enabled later.
    // Two-finger taps.
    multiFingerGestures.add(
        new MultiFingerMultiTap(this.context, 2, 1, GESTURE_2_FINGER_SINGLE_TAP, this));
    multiFingerGestures.add(
        new MultiFingerMultiTap(this.context, 2, 2, GESTURE_2_FINGER_DOUBLE_TAP, this));
    multiFingerGestures.add(
        new MultiFingerMultiTapAndHold(
            this.context, 2, 2, GESTURE_2_FINGER_DOUBLE_TAP_AND_HOLD, this));
    multiFingerGestures.add(
        new MultiFingerMultiTap(this.context, 2, 3, GESTURE_2_FINGER_TRIPLE_TAP, this));
    multiFingerGestures.add(
        new MultiFingerMultiTapAndHold(
            this.context, 2, 3, GESTURE_2_FINGER_TRIPLE_TAP_AND_HOLD, this));
    // Three-finger taps.
    multiFingerGestures.add(
        new MultiFingerMultiTap(this.context, 3, 1, GESTURE_3_FINGER_SINGLE_TAP, this));
    multiFingerGestures.add(
        new MultiFingerMultiTap(this.context, 3, 2, GESTURE_3_FINGER_DOUBLE_TAP, this));
    multiFingerGestures.add(
        new MultiFingerMultiTapAndHold(
            this.context, 3, 1, GESTURE_3_FINGER_SINGLE_TAP_AND_HOLD, this));
    multiFingerGestures.add(
        new MultiFingerMultiTapAndHold(
            this.context, 3, 2, GESTURE_3_FINGER_DOUBLE_TAP_AND_HOLD, this));
    multiFingerGestures.add(
        new MultiFingerMultiTap(this.context, 3, 3, GESTURE_3_FINGER_TRIPLE_TAP, this));
    multiFingerGestures.add(
        new MultiFingerMultiTapAndHold(
            this.context, 3, 3, GESTURE_3_FINGER_TRIPLE_TAP_AND_HOLD, this));
    multiFingerGestures.add(
        new MultiFingerMultiTap(this.context, 3, 3, GESTURE_3_FINGER_TRIPLE_TAP, this));
    // Four-finger taps.
    multiFingerGestures.add(
        new MultiFingerMultiTap(this.context, 4, 1, GESTURE_4_FINGER_SINGLE_TAP, this));
    multiFingerGestures.add(
        new MultiFingerMultiTap(this.context, 4, 2, GESTURE_4_FINGER_DOUBLE_TAP, this));
    multiFingerGestures.add(
        new MultiFingerMultiTapAndHold(
            this.context, 4, 2, GESTURE_4_FINGER_DOUBLE_TAP_AND_HOLD, this));
    multiFingerGestures.add(
        new MultiFingerMultiTap(this.context, 4, 3, GESTURE_4_FINGER_TRIPLE_TAP, this));
    // Two-finger swipes.
    twoFingerSwipes.add(new MultiFingerSwipe(context, 2, DOWN, GESTURE_2_FINGER_SWIPE_DOWN, this));
    twoFingerSwipes.add(new MultiFingerSwipe(context, 2, LEFT, GESTURE_2_FINGER_SWIPE_LEFT, this));
    twoFingerSwipes.add(
        new MultiFingerSwipe(context, 2, RIGHT, GESTURE_2_FINGER_SWIPE_RIGHT, this));
    twoFingerSwipes.add(new MultiFingerSwipe(context, 2, UP, GESTURE_2_FINGER_SWIPE_UP, this));
    multiFingerGestures.addAll(twoFingerSwipes);
    // Three-finger swipes.
    multiFingerGestures.add(
        new MultiFingerSwipe(context, 3, DOWN, GESTURE_3_FINGER_SWIPE_DOWN, this));
    multiFingerGestures.add(
        new MultiFingerSwipe(context, 3, LEFT, GESTURE_3_FINGER_SWIPE_LEFT, this));
    multiFingerGestures.add(
        new MultiFingerSwipe(context, 3, RIGHT, GESTURE_3_FINGER_SWIPE_RIGHT, this));
    multiFingerGestures.add(new MultiFingerSwipe(context, 3, UP, GESTURE_3_FINGER_SWIPE_UP, this));
    // Four-finger swipes.
    multiFingerGestures.add(
        new MultiFingerSwipe(context, 4, DOWN, GESTURE_4_FINGER_SWIPE_DOWN, this));
    multiFingerGestures.add(
        new MultiFingerSwipe(context, 4, LEFT, GESTURE_4_FINGER_SWIPE_LEFT, this));
    multiFingerGestures.add(
        new MultiFingerSwipe(context, 4, RIGHT, GESTURE_4_FINGER_SWIPE_RIGHT, this));
    multiFingerGestures.add(new MultiFingerSwipe(context, 4, UP, GESTURE_4_FINGER_SWIPE_UP, this));
  }

  /**
   * Processes a motion event.
   *
   * @param event The event as received from the previous entry in the event stream.
   * @return True if the event has been appropriately handled by the gesture manifold and related
   *     callback functions, false if it should be handled further by the calling function.
   */
  public boolean onMotionEvent(MotionEvent event) {
    for (GestureMatcher matcher : gestures) {
      if (matcher.getState() != GestureMatcher.STATE_GESTURE_CANCELED) {
        LogUtils.v(LOG_TAG, matcher.toString());
        matcher.onMotionEvent(event);
        LogUtils.v(LOG_TAG, matcher.toString());
        if (matcher.getState() == GestureMatcher.STATE_GESTURE_COMPLETED) {
          // Here we just return. The actual gesture dispatch is done in
          // onStateChanged().
          // No need to process this event any further.
          return true;
        }
      }
    }
    return false;
  }

  public void clear() {
    for (GestureMatcher matcher : gestures) {
      matcher.clear();
    }
  }

  /**
   * Listener that receives notifications of the state of the gesture detector. Listener functions
   * are called as a result of onMotionEvent(). The current MotionEvent in the context of these
   * functions is the event passed into onMotionEvent.
   */
  public interface Listener {

    /** Called when the system has decided the event stream is a potential gesture. */
    void onGestureStarted();

    /**
     * Called when an event stream is recognized as a gesture.
     *
     * @param gestureEvent Information about the gesture.
     */
    void onGestureCompleted(AccessibilityGestureEvent gestureEvent);

    /** Called when the system has decided an event stream doesn't match any known gesture. */
    void onGestureCancelled();
  }

  @Override
  public void onStateChanged(int gestureId, int state, MotionEvent event) {
    if (state == GestureMatcher.STATE_GESTURE_STARTED) {
      listener.onGestureStarted();
    } else if (state == GestureMatcher.STATE_GESTURE_COMPLETED) {
      onGestureCompleted(gestureId, event);
    } else if (state == GestureMatcher.STATE_GESTURE_CANCELED) {
      // We only want to call the cancelation callback if there are no other pending
      // detectors.
      for (GestureMatcher matcher : gestures) {
        if (matcher.getState() == GestureMatcher.STATE_GESTURE_STARTED) {
          return;
        }
      }
      listener.onGestureCancelled();
    }
  }

  private void onGestureCompleted(int gestureId, MotionEvent event) {
    // Note that gestures that complete immediately call clear() from onMotionEvent.
    // Gestures that complete on a delay call clear() here.
    AccessibilityGestureEvent gestureEvent =
        new AccessibilityGestureEvent(gestureId, displayId, new ArrayList<MotionEvent>());
    listener.onGestureCompleted(gestureEvent);
    for (GestureMatcher matcher : gestures) {
      if (matcher.getGestureId() != gestureId) {
        matcher.cancelGesture(event);
      }
    }
  }

  public boolean isMultiFingerGesturesEnabled() {
    return multiFingerGesturesEnabled;
  }

  public void setMultiFingerGesturesEnabled(boolean mode) {
    if (multiFingerGesturesEnabled != mode) {
      multiFingerGesturesEnabled = mode;
      if (mode) {
        gestures.addAll(multiFingerGestures);
      } else {
        gestures.removeAll(multiFingerGestures);
      }
    }
  }

  public boolean isTwoFingerPassthroughEnabled() {
    return twoFingerPassthroughEnabled;
  }

  public void setTwoFingerPassthroughEnabled(boolean mode) {
    if (twoFingerPassthroughEnabled != mode) {
      twoFingerPassthroughEnabled = mode;
      if (!mode) {
        multiFingerGestures.addAll(twoFingerSwipes);
        if (multiFingerGesturesEnabled) {
          gestures.addAll(twoFingerSwipes);
        }
      } else {
        multiFingerGestures.removeAll(twoFingerSwipes);
        gestures.removeAll(twoFingerSwipes);
      }
    }
  }

  /**
   * Returns the current list of motion events. It is the caller's responsibility to copy the list
   * if they want it to persist after a call to clear().
   */
}
