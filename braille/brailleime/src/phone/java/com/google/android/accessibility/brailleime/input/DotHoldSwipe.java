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

package com.google.android.accessibility.brailleime.input;

import androidx.annotation.Nullable;
import com.google.android.accessibility.braille.interfaces.BrailleCharacter;
import com.google.android.accessibility.brailleime.input.Swipe.Direction;

/**
 * Describes a user-initiated swipe and dots hold on the touch screen.
 *
 * <p>Dot hold and swipe includes the braille dots hold, a swipe direction{@link Direction}, a touch
 * count which is the number of fingers that were touching the screen.
 */
public class DotHoldSwipe implements Gesture {
  private final Direction direction;
  private final int touchCount;
  private final BrailleCharacter heldBrailleCharacter;

  public DotHoldSwipe(Direction direction, int touchCount, BrailleCharacter heldBrailleCharacter) {
    this.direction = direction;
    this.touchCount = touchCount;
    this.heldBrailleCharacter = heldBrailleCharacter;
  }

  @Override
  public Swipe getSwipe() {
    return new Swipe(direction, touchCount);
  }

  @Override
  public BrailleCharacter getHeldDots() {
    return new BrailleCharacter(heldBrailleCharacter.toString());
  }

  @Override
  public Gesture mirrorDots() {
    return new DotHoldSwipe(direction, touchCount, heldBrailleCharacter.toMirror());
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
    result = 37 * result + (heldBrailleCharacter == null ? 0 : heldBrailleCharacter.hashCode());
    result = 37 * result + touchCount;
    return result;
  }

  @Override
  public boolean equals(@Nullable Object obj) {
    if (!(obj instanceof DotHoldSwipe)) {
      return false;
    }
    DotHoldSwipe that = (DotHoldSwipe) obj;
    return direction.equals(that.direction)
        && touchCount == that.touchCount
        && heldBrailleCharacter.equals(that.heldBrailleCharacter);
  }
}
