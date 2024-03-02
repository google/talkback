/*
 * Copyright 2019 Google Inc.
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

package com.google.android.accessibility.brailleime.input;

import androidx.annotation.Nullable;
import com.google.android.accessibility.braille.interfaces.BrailleCharacter;

/**
 * Describes a user-initiated swipe on the touch screen.
 *
 * <p>A swipe has both a {@link Direction} and a touch count, which is the number of fingers that
 * were touching the screen while the swipe was occurring.
 */
public class Swipe implements Gesture {

  /** The direction in which the swipe occurred. */
  public enum Direction {
    UP,
    DOWN,
    LEFT,
    RIGHT,
  }

  private final Direction direction;
  private final int touchCount;

  public Swipe(Direction direction, int touchCount) {
    this.direction = direction;
    this.touchCount = touchCount;
  }

  public Swipe(Swipe swipe) {
    this.direction = swipe.direction;
    this.touchCount = swipe.touchCount;
  }

  static Swipe createFromRotation90(Swipe swipe) {
    return new Swipe(rotate90Degrees(swipe.direction), swipe.touchCount);
  }

  static Swipe createFromUpSideDown(Swipe swipe) {
    return new Swipe(turnDirectionUpsideDown(swipe.direction), swipe.touchCount);
  }

  static Swipe createFromMirror(Swipe swipe) {
    return new Swipe(turnDirectionMirror(swipe.direction), swipe.touchCount);
  }

  public Direction getDirection() {
    return direction;
  }

  public int getTouchCount() {
    return touchCount;
  }

  @Override
  public Swipe getSwipe() {
    return new Swipe(this);
  }

  @Override
  public BrailleCharacter getHeldDots() {
    return new BrailleCharacter();
  }

  @Override
  public Gesture mirrorDots() {
    return new Swipe(this);
  }

  private static Direction rotate90Degrees(Direction oldDirection) {
    if (oldDirection == Direction.UP) {
      return Direction.RIGHT;
    } else if (oldDirection == Direction.DOWN) {
      return Direction.LEFT;
    } else if (oldDirection == Direction.LEFT) {
      return Direction.UP;
    } else if (oldDirection == Direction.RIGHT) {
      return Direction.DOWN;
    }
    throw new IllegalArgumentException("unknown direction " + oldDirection);
  }

  private static Direction turnDirectionUpsideDown(Direction oldDirection) {
    if (oldDirection == Direction.UP) {
      return Direction.DOWN;
    } else if (oldDirection == Direction.DOWN) {
      return Direction.UP;
    }
    return oldDirection;
  }

  private static Direction turnDirectionMirror(Direction oldDirection) {
    if (oldDirection == Direction.LEFT) {
      return Direction.RIGHT;
    } else if (oldDirection == Direction.RIGHT) {
      return Direction.LEFT;
    }
    return oldDirection;
  }

  @Override
  public String toString() {
    return "Swipe{" + "direction=" + direction + ", touchCount=" + touchCount + '}';
  }

  @Override
  public boolean equals(@Nullable Object obj) {
    if (!(obj instanceof Swipe)) {
      return false;
    }
    Swipe that = (Swipe) obj;
    return direction.equals(that.direction) && touchCount == that.touchCount;
  }

  @Override
  public int hashCode() {
    /*
     * Hashing function taken from an example in "Effective Java" page 38/39. The number 13 is
     * arbitrary, but choosing non-zero number to start decreases the number of collisions. 37
     * is used as it's an odd prime. If multiplication overflowed and the 37 was an even number,
     * it would be equivalent to bit shifting. The fact that 37 is prime is standard practice.
     */
    int result = 13;
    result = 37 * result + (direction == null ? 0 : direction.hashCode());
    result = 37 * result + touchCount;
    return result;
  }
}
