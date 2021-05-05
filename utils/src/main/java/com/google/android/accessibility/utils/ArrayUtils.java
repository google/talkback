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

import java.util.Arrays;
import org.checkerframework.checker.nullness.qual.Nullable;

/** Utility class containing operations on arrays. */
public final class ArrayUtils {
  private ArrayUtils() {}

  /**
   * Concatenates a set of objects to the end of array.
   *
   * @param <T> the class of the objects in the array
   * @param array the array of object.
   * @param rest objects to be concatenated a the end of array.
   * @return an array containing all the elements.
   */
  public static <T> T[] concat(T[] array, T... rest) {
    @Nullable T[] result = Arrays.copyOf(array, array.length + rest.length);
    int offset = array.length;
    for (T item : rest) {
      result[offset++] = item;
    }
    return (T[]) result;
  }

  /**
   * Searches the specified array of floats for the specified value using the binary search
   * algorithm. The array must be sorted in ascending order prior to making this call.
   *
   * @return The index of the search value if it's contained in the array. Otherwise returns the
   *     index of the closest value in the array.
   */
  public static int binarySearchClosestIndex(float[] array, float target) {
    if (target < array[0]) {
      return 0;
    }
    if (target > array[array.length - 1]) {
      return array.length - 1;
    }

    int lo = 0;
    int hi = array.length - 1;
    while (lo + 1 < hi) {
      // Avoid integer overflow on midpoint calculation. hi + lo can result in overflow if
      // array.length > INT_MAX / 2
      int mid = lo + ((hi - lo) / 2);
      if (target < array[mid]) {
        hi = mid;
      } else if (target > array[mid]) {
        lo = mid;
      } else {
        return mid;
      }
    }

    // lo==hi or lo+1==hi
    return ((target - array[lo]) < (array[hi] - target)) ? lo : hi;
  }
}
