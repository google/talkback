/*
 * Copyright (C) 2022 The Android Open Source Project
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
import static com.google.android.accessibility.utils.gestures.Swipe.NONE;
import static com.google.android.accessibility.utils.gestures.Swipe.RIGHT;
import static com.google.android.accessibility.utils.gestures.Swipe.UP;

import android.content.Context;
import android.os.Build;
import androidx.annotation.RequiresApi;
import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.List;

/**
 * This class generates the list of the {@link GestureMatcher} with the given support gesture list.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
class GestureMatcherFactory {

  /** List of the gestures. */
  private enum GestureMatchConfig {
    // Start with double tap.
    MAPPER_GESTURE_DOUBLE_TAP(GESTURE_DOUBLE_TAP, 1, 2, NONE, NONE),
    MAPPER_GESTURE_DOUBLE_TAP_AND_HOLD(GESTURE_DOUBLE_TAP_AND_HOLD, 1, 2, true, NONE, NONE),
    // Second-finger tap.
    MAPPER_GESTURE_FAKED_SPLIT_TYPING(
        GestureManifold.GESTURE_FAKED_SPLIT_TYPING, 1, 1, true, NONE, NONE),
    // One-direction swipes.
    MAPPER_GESTURE_SWIPE_RIGHT(GESTURE_SWIPE_RIGHT, 1, 0, RIGHT, NONE),
    MAPPER_GESTURE_SWIPE_LEFT(GESTURE_SWIPE_LEFT, 1, 0, LEFT, NONE),
    MAPPER_GESTURE_SWIPE_UP(GESTURE_SWIPE_UP, 1, 0, UP, NONE),
    MAPPER_GESTURE_SWIPE_DOWN(GESTURE_SWIPE_DOWN, 1, 0, DOWN, NONE),
    // Two-direction swipes.
    MAPPER_GESTURE_SWIPE_LEFT_AND_RIGHT(GESTURE_SWIPE_LEFT_AND_RIGHT, 1, 0, LEFT, RIGHT),
    MAPPER_GESTURE_SWIPE_LEFT_AND_UP(GESTURE_SWIPE_LEFT_AND_UP, 1, 0, LEFT, UP),
    MAPPER_GESTURE_SWIPE_LEFT_AND_DOWN(GESTURE_SWIPE_LEFT_AND_DOWN, 1, 0, LEFT, DOWN),
    MAPPER_GESTURE_SWIPE_RIGHT_AND_UP(GESTURE_SWIPE_RIGHT_AND_UP, 1, 0, RIGHT, UP),
    MAPPER_GESTURE_SWIPE_RIGHT_AND_DOWN(GESTURE_SWIPE_RIGHT_AND_DOWN, 1, 0, RIGHT, DOWN),
    MAPPER_GESTURE_SWIPE_RIGHT_AND_LEFT(GESTURE_SWIPE_RIGHT_AND_LEFT, 1, 0, RIGHT, LEFT),
    MAPPER_GESTURE_SWIPE_DOWN_AND_UP(GESTURE_SWIPE_DOWN_AND_UP, 1, 0, DOWN, UP),
    MAPPER_GESTURE_SWIPE_DOWN_AND_LEFT(GESTURE_SWIPE_DOWN_AND_LEFT, 1, 0, DOWN, LEFT),
    MAPPER_GESTURE_SWIPE_DOWN_AND_RIGHT(GESTURE_SWIPE_DOWN_AND_RIGHT, 1, 0, DOWN, RIGHT),
    MAPPER_GESTURE_SWIPE_UP_AND_DOWN(GESTURE_SWIPE_UP_AND_DOWN, 1, 0, UP, DOWN),
    MAPPER_GESTURE_SWIPE_UP_AND_LEFT(GESTURE_SWIPE_UP_AND_LEFT, 1, 0, UP, LEFT),
    MAPPER_GESTURE_SWIPE_UP_AND_RIGHT(GESTURE_SWIPE_UP_AND_RIGHT, 1, 0, UP, RIGHT),
    // Set up multi-finger gestures to be enabled later.
    // Two-finger taps.
    MAPPER_GESTURE_2_FINGER_SINGLE_TAP(GESTURE_2_FINGER_SINGLE_TAP, 2, 1, NONE, NONE),
    MAPPER_GESTURE_2_FINGER_DOUBLE_TAP(GESTURE_2_FINGER_DOUBLE_TAP, 2, 2, NONE, NONE),
    MAPPER_GESTURE_2_FINGER_DOUBLE_TAP_AND_HOLD(
        GESTURE_2_FINGER_DOUBLE_TAP_AND_HOLD, 2, 2, true, NONE, NONE),
    MAPPER_GESTURE_2_FINGER_TRIPLE_TAP(GESTURE_2_FINGER_TRIPLE_TAP, 2, 3, NONE, NONE),
    MAPPER_GESTURE_2_FINGER_TRIPLE_TAP_AND_HOLD(
        GESTURE_2_FINGER_TRIPLE_TAP_AND_HOLD, 2, 3, true, NONE, NONE),
    // Three-finger taps.
    MAPPER_GESTURE_3_FINGER_SINGLE_TAP(GESTURE_3_FINGER_SINGLE_TAP, 3, 1, NONE, NONE),
    MAPPER_GESTURE_3_FINGER_DOUBLE_TAP(GESTURE_3_FINGER_DOUBLE_TAP, 3, 2, NONE, NONE),
    MAPPER_GESTURE_3_FINGER_SINGLE_TAP_AND_HOLD(
        GESTURE_3_FINGER_SINGLE_TAP_AND_HOLD, 3, 1, true, NONE, NONE),
    MAPPER_GESTURE_3_FINGER_DOUBLE_TAP_AND_HOLD(
        GESTURE_3_FINGER_DOUBLE_TAP_AND_HOLD, 3, 2, true, NONE, NONE),
    MAPPER_GESTURE_3_FINGER_TRIPLE_TAP(GESTURE_3_FINGER_TRIPLE_TAP, 3, 3, NONE, NONE),
    MAPPER_GESTURE_3_FINGER_TRIPLE_TAP_AND_HOLD(
        GESTURE_3_FINGER_TRIPLE_TAP_AND_HOLD, 3, 3, true, NONE, NONE),
    // Four-finger taps.
    MAPPER_GESTURE_4_FINGER_SINGLE_TAP(GESTURE_4_FINGER_SINGLE_TAP, 4, 1, NONE, NONE),
    MAPPER_GESTURE_4_FINGER_DOUBLE_TAP(GESTURE_4_FINGER_DOUBLE_TAP, 4, 2, NONE, NONE),
    MAPPER_GESTURE_4_FINGER_DOUBLE_TAP_AND_HOLD(
        GESTURE_4_FINGER_DOUBLE_TAP_AND_HOLD, 4, 2, true, NONE, NONE),
    MAPPER_GESTURE_4_FINGER_TRIPLE_TAP(GESTURE_4_FINGER_TRIPLE_TAP, 4, 3, NONE, NONE),
    // Two-finger swipes.
    MAPPER_GESTURE_2_FINGER_SWIPE_DOWN(GESTURE_2_FINGER_SWIPE_DOWN, 2, 0, DOWN, NONE),
    MAPPER_GESTURE_2_FINGER_SWIPE_LEFT(GESTURE_2_FINGER_SWIPE_LEFT, 2, 0, LEFT, NONE),
    MAPPER_GESTURE_2_FINGER_SWIPE_RIGHT(GESTURE_2_FINGER_SWIPE_RIGHT, 2, 0, RIGHT, NONE),
    MAPPER_GESTURE_2_FINGER_SWIPE_UP(GESTURE_2_FINGER_SWIPE_UP, 2, 0, UP, NONE),
    // Three-finger swipes.
    MAPPER_GESTURE_3_FINGER_SWIPE_DOWN(GESTURE_3_FINGER_SWIPE_DOWN, 3, 0, DOWN, NONE),
    MAPPER_GESTURE_3_FINGER_SWIPE_LEFT(GESTURE_3_FINGER_SWIPE_LEFT, 3, 0, LEFT, NONE),
    MAPPER_GESTURE_3_FINGER_SWIPE_RIGHT(GESTURE_3_FINGER_SWIPE_RIGHT, 3, 0, RIGHT, NONE),
    MAPPER_GESTURE_3_FINGER_SWIPE_UP(GESTURE_3_FINGER_SWIPE_UP, 3, 0, UP, NONE),
    // Four-finger swipes.
    MAPPER_GESTURE_4_FINGER_SWIPE_DOWN(GESTURE_4_FINGER_SWIPE_DOWN, 4, 0, DOWN, NONE),
    MAPPER_GESTURE_4_FINGER_SWIPE_LEFT(GESTURE_4_FINGER_SWIPE_LEFT, 4, 0, LEFT, NONE),
    MAPPER_GESTURE_4_FINGER_SWIPE_RIGHT(GESTURE_4_FINGER_SWIPE_RIGHT, 4, 0, RIGHT, NONE),
    MAPPER_GESTURE_4_FINGER_SWIPE_UP(GESTURE_4_FINGER_SWIPE_UP, 4, 0, UP, NONE);

    GestureMatchConfig(int gestureId, int finger, int tap, int direction1, int direction2) {
      this(gestureId, finger, tap, /* isHold= */ false, direction1, direction2);
    }

    GestureMatchConfig(
        int gestureId, int finger, int tap, boolean isHold, int direction1, int direction2) {
      this.gestureId = gestureId;
      this.finger = finger;
      this.tap = tap;
      this.isHold = isHold;
      this.direction1 = direction1;
      this.direction2 = direction2;
    }

    final int gestureId;
    final int finger;
    final int tap;
    final boolean isHold;
    final int direction1;
    final int direction2;

    @Override
    public String toString() {
      return "GestureMatchConfig{"
          + "gestureId="
          + gestureId
          + ", finger="
          + finger
          + ", tap="
          + tap
          + ", isHold="
          + isHold
          + ", direction1="
          + direction1
          + ", direction2="
          + direction2
          + '}';
    }
  }

  private GestureMatcherFactory() {}

  /**
   * Gets the list of {@link GestureMatcher} by the support gesture list.
   *
   * @param supportGestureList the support gesture list
   * @param listener the listener to set to the GestureMatcher
   * @return the list of GestureMatcher
   */
  static List<GestureMatcher> getGestureMatcherList(
      Context context,
      ImmutableList<String> supportGestureList,
      GestureMatcher.StateChangeListener listener) {
    List<GestureMatcher> gestureMatchers = new ArrayList<>();
    for (GestureMatchConfig iterator : GestureMatchConfig.values()) {
      if (supportGestureList.contains(iterator.name())) {
        GestureMatcher gestureMatcher = createGestureMatcher(context, iterator, listener);
        gestureMatchers.add(gestureMatcher);
      }
    }
    return gestureMatchers;
  }

  private static GestureMatcher createGestureMatcher(
      Context context,
      GestureMatchConfig gestureMatchConfig,
      GestureMatcher.StateChangeListener listener) {

    // 1-finger
    if (gestureMatchConfig.finger == 1) {
      // swipe
      if (gestureMatchConfig.tap == 0) {
        if (gestureMatchConfig.direction2 == NONE) {
          return new Swipe(
              context, gestureMatchConfig.direction1, gestureMatchConfig.gestureId, listener);
        } else {
          return new Swipe(
              context,
              gestureMatchConfig.direction1,
              gestureMatchConfig.direction2,
              gestureMatchConfig.gestureId,
              listener);
        }
      }
      // single-tap and hold
      if ((gestureMatchConfig.tap == 1) && gestureMatchConfig.isHold) {
        return new SecondFingerTap(
            context, gestureMatchConfig.tap, gestureMatchConfig.gestureId, listener);
      }
      // double-taps
      if (gestureMatchConfig.tap == 2) {
        if (gestureMatchConfig.isHold) {
          return new MultiTapAndHold(
              context, gestureMatchConfig.tap, gestureMatchConfig.gestureId, listener);
        } else {
          return new MultiTap(
              context, gestureMatchConfig.tap, gestureMatchConfig.gestureId, listener);
        }
      }
    } else {
      // multi-finger
      // swipe
      if (gestureMatchConfig.tap == 0) {
        return new MultiFingerSwipe(
            context,
            gestureMatchConfig.finger,
            gestureMatchConfig.direction1,
            gestureMatchConfig.gestureId,
            listener);
      }
      // multi-taps and hold
      if (gestureMatchConfig.isHold) {
        return new MultiFingerMultiTapAndHold(
            context,
            gestureMatchConfig.finger,
            gestureMatchConfig.tap,
            gestureMatchConfig.gestureId,
            listener);
      } else {
        // multi-taps without hold
        return new MultiFingerMultiTap(
            context,
            gestureMatchConfig.finger,
            gestureMatchConfig.tap,
            gestureMatchConfig.gestureId,
            listener);
      }
    }
    throw new IllegalArgumentException(
        String.format(
            "IllegalArgumentException: GestureMatchConfig %s defines the wrong argument. %s",
            gestureMatchConfig.name(), gestureMatchConfig));
  }
}
