/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.google.android.accessibility.utils;

import static com.google.common.base.Preconditions.checkArgument;

/** Utility class containing operations on primitive data. */
public final class PrimitiveUtils {
  private PrimitiveUtils() {}

  /**
   * Returns whether the target integer number is inside a given interval.
   *
   * @param target The target number
   * @param intervalFrom The start of interval
   * @param intervalTo Then end of interval
   * @param isClosure Whether it's a closure interval.
   */
  public static boolean isInInterval(
      int target, int intervalFrom, int intervalTo, boolean isClosure) {
    return (intervalFrom < target && target < intervalTo)
        || (isClosure && (intervalFrom == target || intervalTo == target));
  }

  /**
   * Given a value and a previous min/max, returns a corresponding scaled value with the same
   * relative position to the new min/max.
   */
  public static int scaleValue(int prevValue, int prevMin, int prevMax, int min, int max) {
    if (prevMin == prevMax) {
      return min;
    }
    final float fraction = ((float) (prevValue - prevMin)) / (prevMax - prevMin);
    return (int) (min + fraction * (max - min));
  }

  /**
   * Given a value and a previous min/max, returns a corresponding scaled value with the same
   * relative position to the new min/max.
   */
  public static float scaleValue(
      float prevValue, float prevMin, float prevMax, float min, float max) {
    if (prevMin == prevMax) {
      return min;
    }
    final float fraction = (prevValue - prevMin) / (prevMax - prevMin);
    return (min + fraction * (max - min));
  }

  /**
   * Given an integer value and min/max, clamp the value in the range of [min, max] and return the
   * new value.
   */
  public static int clampValue(int value, int min, int max) {
    if (value < min) {
      return min;
    }
    if (value > max) {
      return max;
    }
    return value;
  }

  /**
   * Given a float value and min/max, clamp the value in the range of [min, max] and return the new
   * value.
   */
  public static float clampValue(float value, float min, float max) {
    if (value < min) {
      return min;
    }
    if (value > max) {
      return max;
    }
    return value;
  }

  /**
   * Returns {@code true} if {@code a} and {@code b} are within {@code tolerance} of each other.
   *
   * <p>This is a copy of Guava's {@code DoubleMath#fuzzyEquals} method but for floats.
   */
  @SuppressWarnings("RestrictTo")
  public static boolean fuzzyEquals(float a, float b, float tolerance) {
    checkArgument(tolerance >= 0);
    return Math.copySign(a - b, 1.0f) <= tolerance
        // copySign(x, 1.0) is a branch-free version of abs(x), but with different NaN semantics
        || (a == b) // needed to ensure that infinities equal themselves
        || (Float.isNaN(a) && Float.isNaN(b));
  }
}
