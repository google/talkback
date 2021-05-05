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

import android.graphics.Rect;
import androidx.annotation.NonNull;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Utility methods for Rects. */
public final class RectUtils {
  /* A {@link Comparator} used to compare position of rectangles. The position order is from top
   * to bottom, from left to right. */
  public static final Comparator<Rect> RECT_POSITION_COMPARATOR =
      new Comparator<Rect>() {
        @Override
        public int compare(Rect o1, Rect o2) {
          if (o1.top != o2.top) {
            return o1.top - o2.top;
          } else if (o1.bottom != o2.bottom) {
            return o1.bottom - o2.bottom;
          } else if (o1.left != o2.left) {
            return o1.left - o2.left;
          } else {
            return o1.right - o2.right;
          }
        }
      };

  private RectUtils() {}

  /**
   * Adjusts the {@code rect} such that each edge is at least {@code minimumEdge} pixels long. If
   * the rect needs to be expanded, it will expand around the center point.
   *
   * @param rect the rectangle to adjust bounds
   * @param minimumEdge the minimum edge length
   */
  public static void ensureMinimumEdge(Rect rect, int minimumEdge) {
    final boolean flipHorizontal = rect.left > rect.right;
    final int width = Math.abs(rect.left - rect.right);
    final int widthDelta = (minimumEdge - width) / 2;
    if (widthDelta > 0) {
      if (flipHorizontal) {
        rect.left += widthDelta;
        rect.right -= widthDelta;
      } else {
        rect.left -= widthDelta;
        rect.right += widthDelta;
      }
    }

    final boolean flipVertical = rect.top > rect.bottom;
    final int height = Math.abs(rect.bottom - rect.top);
    final int heightDelta = (minimumEdge - height) / 2;
    if (heightDelta > 0) {
      if (flipVertical) {
        rect.top += heightDelta;
        rect.bottom -= heightDelta;
      } else {
        rect.top -= heightDelta;
        rect.bottom += heightDelta;
      }
    }
  }

  /**
   * Checks whether top/bottom or left/right edges are flipped (i.e. left > right and/or top >
   * bottom).
   *
   * @return true if the edges are <strong>NOT</strong> flipped.
   */
  public static boolean isSorted(Rect rect) {
    return rect.left <= rect.right && rect.top <= rect.bottom;
  }

  /**
   * Returns true if the rectangle is empty (left == right or top == bottom) <strong>Note:</strong>
   * This method is different from {@link Rect#isEmpty()}, unsorted Rect is defined as non-empty
   * Rect here.
   */
  public static boolean isEmpty(Rect rect) {
    return rect.left == rect.right || rect.top == rect.bottom;
  }

  /**
   * Find the largest sub-rectangle that doesn't intersect a specified one. <strong>Note:</strong>
   * {@code rectToModify} and {@code otherRect} will be sorted after operation.
   *
   * @param rectToModify The rect that may be modified to avoid intersections
   * @param otherRect The rect that should be avoided
   */
  public static void adjustRectToAvoidIntersection(Rect rectToModify, Rect otherRect) {
    /*
     * Some rectangles are flipped around (left > right). Make sure we have two Rects free of
     * such pathologies.
     */
    rectToModify.sort();
    otherRect.sort();

    if (rectToModify.contains(otherRect) || !Rect.intersects(rectToModify, otherRect)) {
      return;
    }

    /*
     * Intersect rectToModify with four rects that represent cuts of the entire space along
     * lines defined by the otherRect's edges
     */
    Rect[] cuts = {
      new Rect(rectToModify.left, rectToModify.top, otherRect.left, rectToModify.bottom),
      new Rect(rectToModify.left, rectToModify.top, rectToModify.right, otherRect.top),
      new Rect(otherRect.right, rectToModify.top, rectToModify.right, rectToModify.bottom),
      new Rect(rectToModify.left, otherRect.bottom, rectToModify.right, rectToModify.bottom)
    };

    int maxIntersectingRectArea = 0;
    int indexOfLargestIntersection = -1;
    for (int i = 0; i < cuts.length; i++) {
      if (!isSorted(cuts[i])) {
        continue;
      }
      if (Rect.intersects(cuts[i], rectToModify)) {
        /* Reassign this cut to its intersection with rectToModify */
        int visibleRectArea = cuts[i].width() * cuts[i].height();
        if (visibleRectArea > maxIntersectingRectArea) {
          maxIntersectingRectArea = visibleRectArea;
          indexOfLargestIntersection = i;
        }
      }
    }
    if (maxIntersectingRectArea <= 0) {
      // The rectToModify isn't within any of our cuts, so it's entirely occuled by otherRect.
      rectToModify.setEmpty();
      return;
    }
    rectToModify.set(cuts[indexOfLargestIntersection]);
  }

  /**
   * Returns whether the two rectangles are at the same line. (Have the same top & bottom values.)
   */
  public static boolean isAligned(@NonNull Rect rect1, @NonNull Rect rect2) {
    return rect1.top == rect2.top && rect1.bottom == rect2.bottom;
  }

  /**
   * Finds the smallest Rect that contains {@code target} and {@code candidate}, and store the
   * result in {@code target}. <strong>Note:</strong> The input Rect must be sorted.
   */
  public static void join(Rect candidate, Rect target) {
    target.set(
        Math.min(target.left, candidate.left),
        Math.min(target.top, candidate.top),
        Math.max(target.right, candidate.right),
        Math.max(target.bottom, candidate.bottom));
  }

  /**
   * Collapses aligned rectangles into a single rectangle. Stores a list of rectangles representing
   * joint lines. The result list is sorted from top to bottom, left to right.
   *
   * @see {@link #isAligned(Rect, Rect)}
   * @see {@link #join(Rect, Rect)}
   */
  public static void collapseRects(List<Rect> rectList) {
    if (rectList == null || rectList.size() <= 1) {
      return;
    }
    List<Rect> copy = new ArrayList<>(rectList);
    Collections.sort(copy, RECT_POSITION_COMPARATOR);
    rectList.clear();
    Rect tmp = new Rect(copy.get(0));
    for (Rect rect : copy) {
      if (isAligned(tmp, rect)) {
        join(rect, tmp);
      } else {
        rectList.add(tmp);
        tmp = new Rect(rect);
      }
    }
    rectList.add(tmp);
  }
}
