/*
 * Copyright 2023 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.android.accessibility.brailleime;

import com.google.android.accessibility.braille.interfaces.BrailleCharacter;
import com.google.android.accessibility.brailleime.input.DotHoldSwipe;
import com.google.android.accessibility.brailleime.input.Gesture;
import com.google.android.accessibility.brailleime.input.Swipe;
import com.google.android.accessibility.brailleime.input.Swipe.Direction;
import java.util.Optional;

/** Braille keyboard gestures. */
public enum BrailleImeGesture {
  UNASSIGNED_GESTURE(GestureType.NONE, null, -1),

  SWIPE_UP_ONE_FINGER(GestureType.SWIPE, Direction.UP, 1),
  SWIPE_DOWN_ONE_FINGER(GestureType.SWIPE, Direction.DOWN, 1),
  SWIPE_LEFT_ONE_FINGER(GestureType.SWIPE, Direction.LEFT, 1),
  SWIPE_RIGHT_ONE_FINGER(GestureType.SWIPE, Direction.RIGHT, 1),
  SWIPE_UP_TWO_FINGERS(GestureType.SWIPE, Direction.UP, 2),
  SWIPE_DOWN_TWO_FINGERS(GestureType.SWIPE, Direction.DOWN, 2),
  SWIPE_LEFT_TWO_FINGERS(GestureType.SWIPE, Direction.LEFT, 2),
  SWIPE_RIGHT_TWO_FINGERS(GestureType.SWIPE, Direction.RIGHT, 2),
  SWIPE_UP_THREE_FINGERS(GestureType.SWIPE, Direction.UP, 3),
  SWIPE_DOWN_THREE_FINGERS(GestureType.SWIPE, Direction.DOWN, 3),
  SWIPE_LEFT_THREE_FINGERS(GestureType.SWIPE, Direction.LEFT, 3),
  SWIPE_RIGHT_THREE_FINGERS(GestureType.SWIPE, Direction.RIGHT, 3),

  HOLD_DOT1_SWIPE_UP_ONE_FINGER(GestureType.DOT_HOLD_AND_SWIPE, Direction.UP, 1, "1"),
  HOLD_DOT1_SWIPE_DOWN_ONE_FINGER(GestureType.DOT_HOLD_AND_SWIPE, Direction.DOWN, 1, "1"),
  HOLD_DOT1_SWIPE_LEFT_ONE_FINGER(GestureType.DOT_HOLD_AND_SWIPE, Direction.LEFT, 1, "1"),
  HOLD_DOT1_SWIPE_RIGHT_ONE_FINGER(GestureType.DOT_HOLD_AND_SWIPE, Direction.RIGHT, 1, "1"),
  HOLD_DOT1_SWIPE_UP_TWO_FINGERS(GestureType.DOT_HOLD_AND_SWIPE, Direction.UP, 2, "1"),
  HOLD_DOT1_SWIPE_DOWN_TWO_FINGERS(GestureType.DOT_HOLD_AND_SWIPE, Direction.DOWN, 2, "1"),
  HOLD_DOT1_SWIPE_LEFT_TWO_FINGERS(GestureType.DOT_HOLD_AND_SWIPE, Direction.LEFT, 2, "1"),
  HOLD_DOT1_SWIPE_RIGHT_TWO_FINGERS(GestureType.DOT_HOLD_AND_SWIPE, Direction.RIGHT, 2, "1"),
  HOLD_DOT1_SWIPE_UP_THREE_FINGERS(GestureType.DOT_HOLD_AND_SWIPE, Direction.UP, 3, "1"),
  HOLD_DOT1_SWIPE_DOWN_THREE_FINGERS(GestureType.DOT_HOLD_AND_SWIPE, Direction.DOWN, 3, "1"),
  HOLD_DOT1_SWIPE_LEFT_THREE_FINGERS(GestureType.DOT_HOLD_AND_SWIPE, Direction.LEFT, 3, "1"),
  HOLD_DOT1_SWIPE_RIGHT_THREE_FINGERS(GestureType.DOT_HOLD_AND_SWIPE, Direction.RIGHT, 3, "1"),

  HOLD_DOT2_SWIPE_UP_ONE_FINGER(GestureType.DOT_HOLD_AND_SWIPE, Direction.UP, 1, "2"),
  HOLD_DOT2_SWIPE_DOWN_ONE_FINGER(GestureType.DOT_HOLD_AND_SWIPE, Direction.DOWN, 1, "2"),
  HOLD_DOT2_SWIPE_LEFT_ONE_FINGER(GestureType.DOT_HOLD_AND_SWIPE, Direction.LEFT, 1, "2"),
  HOLD_DOT2_SWIPE_RIGHT_ONE_FINGER(GestureType.DOT_HOLD_AND_SWIPE, Direction.RIGHT, 1, "2"),
  HOLD_DOT2_SWIPE_UP_TWO_FINGERS(GestureType.DOT_HOLD_AND_SWIPE, Direction.UP, 2, "2"),
  HOLD_DOT2_SWIPE_DOWN_TWO_FINGERS(GestureType.DOT_HOLD_AND_SWIPE, Direction.DOWN, 2, "2"),
  HOLD_DOT2_SWIPE_LEFT_TWO_FINGERS(GestureType.DOT_HOLD_AND_SWIPE, Direction.LEFT, 2, "2"),
  HOLD_DOT2_SWIPE_RIGHT_TWO_FINGERS(GestureType.DOT_HOLD_AND_SWIPE, Direction.RIGHT, 2, "2"),
  HOLD_DOT2_SWIPE_UP_THREE_FINGERS(GestureType.DOT_HOLD_AND_SWIPE, Direction.UP, 3, "2"),
  HOLD_DOT2_SWIPE_DOWN_THREE_FINGERS(GestureType.DOT_HOLD_AND_SWIPE, Direction.DOWN, 3, "2"),
  HOLD_DOT2_SWIPE_LEFT_THREE_FINGERS(GestureType.DOT_HOLD_AND_SWIPE, Direction.LEFT, 3, "2"),
  HOLD_DOT2_SWIPE_RIGHT_THREE_FINGERS(GestureType.DOT_HOLD_AND_SWIPE, Direction.RIGHT, 3, "2"),

  HOLD_DOT3_SWIPE_UP_ONE_FINGER(GestureType.DOT_HOLD_AND_SWIPE, Direction.UP, 1, "3"),
  HOLD_DOT3_SWIPE_DOWN_ONE_FINGER(GestureType.DOT_HOLD_AND_SWIPE, Direction.DOWN, 1, "3"),
  HOLD_DOT3_SWIPE_LEFT_ONE_FINGER(GestureType.DOT_HOLD_AND_SWIPE, Direction.LEFT, 1, "3"),
  HOLD_DOT3_SWIPE_RIGHT_ONE_FINGER(GestureType.DOT_HOLD_AND_SWIPE, Direction.RIGHT, 1, "3"),
  HOLD_DOT3_SWIPE_UP_TWO_FINGERS(GestureType.DOT_HOLD_AND_SWIPE, Direction.UP, 2, "3"),
  HOLD_DOT3_SWIPE_DOWN_TWO_FINGERS(GestureType.DOT_HOLD_AND_SWIPE, Direction.DOWN, 2, "3"),
  HOLD_DOT3_SWIPE_LEFT_TWO_FINGERS(GestureType.DOT_HOLD_AND_SWIPE, Direction.LEFT, 2, "3"),
  HOLD_DOT3_SWIPE_RIGHT_TWO_FINGERS(GestureType.DOT_HOLD_AND_SWIPE, Direction.RIGHT, 2, "3"),
  HOLD_DOT3_SWIPE_UP_THREE_FINGERS(GestureType.DOT_HOLD_AND_SWIPE, Direction.UP, 3, "3"),
  HOLD_DOT3_SWIPE_DOWN_THREE_FINGERS(GestureType.DOT_HOLD_AND_SWIPE, Direction.DOWN, 3, "3"),
  HOLD_DOT3_SWIPE_LEFT_THREE_FINGERS(GestureType.DOT_HOLD_AND_SWIPE, Direction.LEFT, 3, "3"),
  HOLD_DOT3_SWIPE_RIGHT_THREE_FINGERS(GestureType.DOT_HOLD_AND_SWIPE, Direction.RIGHT, 3, "3"),

  HOLD_DOTS12_SWIPE_UP_ONE_FINGER(GestureType.DOT_HOLD_AND_SWIPE, Direction.UP, 1, "12"),
  HOLD_DOTS12_SWIPE_DOWN_ONE_FINGER(GestureType.DOT_HOLD_AND_SWIPE, Direction.DOWN, 1, "12"),
  HOLD_DOTS12_SWIPE_LEFT_ONE_FINGER(GestureType.DOT_HOLD_AND_SWIPE, Direction.LEFT, 1, "12"),
  HOLD_DOTS12_SWIPE_RIGHT_ONE_FINGER(GestureType.DOT_HOLD_AND_SWIPE, Direction.RIGHT, 1, "12"),
  HOLD_DOTS12_SWIPE_UP_TWO_FINGERS(GestureType.DOT_HOLD_AND_SWIPE, Direction.UP, 2, "12"),
  HOLD_DOTS12_SWIPE_DOWN_TWO_FINGERS(GestureType.DOT_HOLD_AND_SWIPE, Direction.DOWN, 2, "12"),
  HOLD_DOTS12_SWIPE_LEFT_TWO_FINGERS(GestureType.DOT_HOLD_AND_SWIPE, Direction.LEFT, 2, "12"),
  HOLD_DOTS12_SWIPE_RIGHT_TWO_FINGERS(GestureType.DOT_HOLD_AND_SWIPE, Direction.RIGHT, 2, "12"),
  HOLD_DOTS12_SWIPE_UP_THREE_FINGERS(GestureType.DOT_HOLD_AND_SWIPE, Direction.UP, 3, "12"),
  HOLD_DOTS12_SWIPE_DOWN_THREE_FINGERS(GestureType.DOT_HOLD_AND_SWIPE, Direction.DOWN, 3, "12"),
  HOLD_DOTS12_SWIPE_LEFT_THREE_FINGERS(GestureType.DOT_HOLD_AND_SWIPE, Direction.LEFT, 3, "12"),
  HOLD_DOTS12_SWIPE_RIGHT_THREE_FINGERS(GestureType.DOT_HOLD_AND_SWIPE, Direction.RIGHT, 3, "12"),
  ;

  /** {@link BrailleImeGesture} gesture type. */
  public enum GestureType {
    NONE,
    SWIPE,
    DOT_HOLD_AND_SWIPE,
  }

  private static final String NO_DOTS = "";

  private final GestureType gestureType;
  private final Direction direction;
  private final int touchCount;
  private final String heldDotNumberString;

  BrailleImeGesture(GestureType gestureType, Direction direction, int touchCount) {
    this(gestureType, direction, touchCount, NO_DOTS);
  }

  BrailleImeGesture(
      GestureType gestureType, Direction direction, int touchCount, String heldDotNumberString) {
    this.gestureType = gestureType;
    this.direction = direction;
    this.touchCount = touchCount;
    this.heldDotNumberString = heldDotNumberString;
  }

  /** Returns {@link Gesture}. */
  public Optional<Gesture> getGesture() {
    if (gestureType == GestureType.SWIPE) {
      return Optional.of(new Swipe(direction, touchCount));
    } else if (gestureType == GestureType.DOT_HOLD_AND_SWIPE) {
      return Optional.of(
          new DotHoldSwipe(direction, touchCount, new BrailleCharacter(heldDotNumberString)));
    }
    return Optional.empty();
  }
}
