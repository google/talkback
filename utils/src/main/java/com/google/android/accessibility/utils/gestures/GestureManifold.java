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

import static android.accessibilityservice.AccessibilityService.GESTURE_2_FINGER_SWIPE_DOWN;
import static android.accessibilityservice.AccessibilityService.GESTURE_2_FINGER_SWIPE_LEFT;
import static android.accessibilityservice.AccessibilityService.GESTURE_2_FINGER_SWIPE_RIGHT;
import static android.accessibilityservice.AccessibilityService.GESTURE_2_FINGER_SWIPE_UP;
import static com.google.android.accessibility.utils.gestures.TwoFingerSecondFingerMultiTap.ROTATE_DIRECTION_BACKWARD;
import static com.google.android.accessibility.utils.gestures.TwoFingerSecondFingerMultiTap.ROTATE_DIRECTION_FORWARD;

import android.accessibilityservice.AccessibilityGestureEvent;
import android.content.Context;
import android.os.Build;
import android.view.MotionEvent;
import androidx.annotation.RequiresApi;
import com.google.android.libraries.accessibility.utils.log.LogUtils;
import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.List;

/**
 * This class coordinates a series of individual gesture matchers to serve as a unified gesture
 * detector. Gesture matchers are tied to a single gesture. It calls listener callback functions
 * when a gesture starts or completes.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
public class GestureManifold implements GestureMatcher.StateChangeListener {
  public static final int GESTURE_FAKED_SPLIT_TYPING = -3;
  public static final boolean ENABLE_MULTIPLE_GESTURE_SETS = false;
  public static final int GESTURE_TAP_HOLD_AND_2ND_FINGER_FORWARD_DOUBLE_TAP = -4;
  public static final int GESTURE_TAP_HOLD_AND_2ND_FINGER_BACKWARD_DOUBLE_TAP = -5;

  private static final String LOG_TAG = "GestureManifold";

  private final List<GestureMatcher> gestures = new ArrayList<>();
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

  public GestureManifold(
      Context context, Listener listener, int displayId, ImmutableList<String> supportGestureList) {
    this.listener = listener;
    this.displayId = displayId;
    multiFingerGesturesEnabled = false;
    twoFingerPassthroughEnabled = false;

    // Set up gestures.
    List<GestureMatcher> gestureMatcherList =
        GestureMatcherFactory.getGestureMatcherList(context, supportGestureList, this);

    for (GestureMatcher gestureMatcher : gestureMatcherList) {
      if (gestureMatcher != null) {
        if ((gestureMatcher instanceof Swipe)
            || (gestureMatcher instanceof MultiTap)
            || (gestureMatcher instanceof MultiTapAndHold)
            || (gestureMatcher instanceof SecondFingerTap)) {
          gestures.add(gestureMatcher);
        } else {
          multiFingerGestures.add(gestureMatcher);
          int gestureId = gestureMatcher.getGestureId();
          if ((gestureId == GESTURE_2_FINGER_SWIPE_DOWN)
              || (gestureId == GESTURE_2_FINGER_SWIPE_LEFT)
              || (gestureId == GESTURE_2_FINGER_SWIPE_RIGHT)
              || (gestureId == GESTURE_2_FINGER_SWIPE_UP)) {
            twoFingerSwipes.add(gestureMatcher);
          }
        }
      }
    }
    if (ENABLE_MULTIPLE_GESTURE_SETS) {
      gestures.add(
          new TwoFingerSecondFingerMultiTap(
              context,
              2,
              ROTATE_DIRECTION_FORWARD,
              GESTURE_TAP_HOLD_AND_2ND_FINGER_FORWARD_DOUBLE_TAP,
              this));
      gestures.add(
          new TwoFingerSecondFingerMultiTap(
              context,
              2,
              ROTATE_DIRECTION_BACKWARD,
              GESTURE_TAP_HOLD_AND_2ND_FINGER_BACKWARD_DOUBLE_TAP,
              this));
    }
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

    /**
     * Called when the system has decided the event stream is a potential gesture.
     *
     * @param gestureId the gesture which is start matching.
     */
    void onGestureStarted(int gestureId);

    /**
     * Called when an event stream is recognized as a gesture.
     *
     * @param gestureEvent Information about the gesture.
     */
    void onGestureCompleted(AccessibilityGestureEvent gestureEvent);

    /**
     * Called when the system has decided an event stream doesn't match any known gesture.
     *
     * @param gestureId the gesture which is fail to match.
     */
    void onGestureCancelled(int gestureId);
  }

  @Override
  public void onStateChanged(int gestureId, int state, MotionEvent event) {
    if (state == GestureMatcher.STATE_GESTURE_STARTED) {
      listener.onGestureStarted(gestureId);
    } else if (state == GestureMatcher.STATE_GESTURE_COMPLETED) {
      onGestureCompleted(gestureId, event);
    } else if (state == GestureMatcher.STATE_GESTURE_CANCELED) {
      listener.onGestureCancelled(gestureId);
    }
  }

  private void onGestureCompleted(int gestureId, MotionEvent event) {
    // Note that gestures that complete immediately call clear() from onMotionEvent.
    // Gestures that complete on a delay call clear() here.
    AccessibilityGestureEvent gestureEvent =
        new AccessibilityGestureEvent(gestureId, displayId, new ArrayList<MotionEvent>());
    for (GestureMatcher matcher : gestures) {
      if (matcher.getGestureId() != gestureId) {
        matcher.cancelGesture(event, false);
      }
    }
    listener.onGestureCompleted(gestureEvent);
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
