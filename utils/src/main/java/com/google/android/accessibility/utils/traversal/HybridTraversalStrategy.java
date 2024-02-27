/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.google.android.accessibility.utils.traversal;

import static com.google.android.accessibility.utils.traversal.AccessibilityFocusHistory.NOT_FOUND;

import android.graphics.Rect;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import com.google.android.accessibility.utils.FocusFinder;
import com.google.android.libraries.accessibility.utils.log.LogUtils;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

/** Traversal strategy for directional navigation that takes the view hierarchy into account. */
public class HybridTraversalStrategy extends DirectionalTraversalStrategy {
  private static final String TAG = "HybridTraversalStrategy";

  /** Instance for retrieving previously focused nodes. */
  private final AccessibilityFocusHistory focusHistory;

  /** Whether the screen has an RTL layout. */
  private final boolean isRtl;

  public HybridTraversalStrategy(
      AccessibilityNodeInfoCompat root,
      FocusFinder focusFinder,
      AccessibilityFocusHistory focusHistory,
      boolean isRtl) {
    super(root, focusFinder);
    this.focusHistory = focusHistory;
    this.isRtl = isRtl;
  }

  @Override
  protected @Nullable AccessibilityNodeInfoCompat findFocusFromRect(
      AccessibilityNodeInfoCompat focused,
      Rect focusedRect,
      @SpatialSearchDirection int direction) {

    AccessibilityNodeInfoCompat node = focused;
    AccessibilityNodeInfoCompat excluded = null;
    while (node != null) {
      AccessibilityNodeInfoCompat bestChildCandidate =
          findBestChild(node, focusedRect, direction, excluded);
      if (bestChildCandidate != null) {
        return bestChildCandidate;
      }
      excluded = node;
      node = node.getParent();
    }

    return null;
  }

  private @Nullable AccessibilityNodeInfoCompat findBestChild(
      @NonNull AccessibilityNodeInfoCompat parent,
      @NonNull Rect focusedRect,
      @SpatialSearchDirection int direction,
      @Nullable AccessibilityNodeInfoCompat excludedChild) {
    int childCount = parent.getChildCount();
    AccessibilityNodeInfoCompat bestCandidate = null;
    for (int i = 0; i < childCount; ++i) {
      AccessibilityNodeInfoCompat child = parent.getChild(i);
      if (child == null || child.equals(excludedChild) || child.equals(root)) {
        continue;
      }
      if (focusableNodes.contains(child)) {
        Rect childRect = new Rect();
        getAssumedRectInScreen(child, childRect);
        if (isBetterCandidate(direction, focusedRect, child, bestCandidate)) {
          bestCandidate = child;
        }
      }
      if (child.getChildCount() > 0) {
        AccessibilityNodeInfoCompat bestChildCandidate =
            findBestChild(child, focusedRect, direction, null);
        if (bestChildCandidate != null
            && isBetterCandidate(direction, focusedRect, bestChildCandidate, bestCandidate)) {
          bestCandidate = bestChildCandidate;
        }
      }
    }
    return bestCandidate;
  }

  /**
   * Is rect1 a better candidate than rect2 for a focus search in a particular direction from a
   * source rect? This is the core routine that determines the order of focus searching.
   *
   * @param direction the direction (up, down, left, right)
   * @param source the source we are searching from
   * @param candidate the candidate rectangle.
   * @param currentBest the current best candidate; has to be an actual candidate (see {@link
   *     #isCandidate}) or {@code null}.
   * @return Whether the candidate is the new best.
   */
  private boolean isBetterCandidate(
      @SpatialSearchDirection int direction,
      Rect source,
      @NonNull AccessibilityNodeInfoCompat candidate,
      @Nullable AccessibilityNodeInfoCompat currentBest) {
    Rect candidateRect = new Rect();
    getAssumedRectInScreen(candidate, candidateRect);
    LogUtils.d(TAG, "Check candidate: %s", candidate);
    // To be a better candidate, needs to be a candidate, that is, be in the given direction.
    if (!isCandidate(source, candidateRect, direction)) {
      LogUtils.d(TAG, "Candidate is not in the desired direction.", candidate);
      return false;
    }

    if (currentBest == null) {
      LogUtils.d(TAG, "Candidate is better than nothing.", candidate);
      return true;
    }

    Rect currentBestRect = new Rect();
    getAssumedRectInScreen(currentBest, currentBestRect);
    int candidateDistance =
        DirectionalTraversalStrategy.majorAxisDistance(direction, source, candidateRect);
    int currentBestDistance =
        DirectionalTraversalStrategy.majorAxisDistance(direction, source, currentBestRect);
    if (candidateDistance < currentBestDistance) {
      LogUtils.d(TAG, "Candidate is closer than current best.", candidate);
      return true;
    }
    if (candidateDistance > currentBestDistance) {
      LogUtils.d(TAG, "Candidate is further than current best.", candidate);
      return false;
    }

    // Among same-distance nodes, prefer one with input focus.
    if (candidate.isFocused()) {
      LogUtils.d(TAG, "Candidate has input focus.", candidate);
      return true;
    }

    // Among same-distance nodes, prefer one that was recently focused.
    long candidateLastFocused = focusHistory.getTimeOfLastFocusForNode(candidate);
    long currentBestLastFocused = focusHistory.getTimeOfLastFocusForNode(currentBest);
    if (candidateLastFocused != NOT_FOUND && currentBestLastFocused != NOT_FOUND) {
      if (candidateLastFocused > currentBestLastFocused) {
        LogUtils.d(TAG, "Candidate has more recently been focused than current best.", candidate);
        return true;
      }
      if (candidateLastFocused < currentBestLastFocused) {
        LogUtils.d(TAG, "Candidate has less recently been focused than current best.", candidate);
        return false;
      }
    }
    if (candidateLastFocused != NOT_FOUND) {
      LogUtils.d(TAG, "Candidate has recently been focused and current best has not.", candidate);
      return true;
    }
    if (currentBestLastFocused != NOT_FOUND) {
      LogUtils.d(TAG, "Candidate has not recently been focused and current best has.", candidate);
      return false;
    }

    // Among same-distance nodes, prefer one that is earlier in reading direction.
    Axis minorAxis = getMinorAxis(direction);
    if (isCandidatePrecedingInReadingDirection(minorAxis, candidateRect, currentBestRect)) {
      LogUtils.d(TAG, "Candidate is earlier in read direction than current best.", candidate);
      return true;
    }

    LogUtils.d(TAG, "Candidate is not better than current best.", candidate);
    return false;
  }

  private boolean isCandidatePrecedingInReadingDirection(
      Axis axis, Rect candidateRect, Rect currentBestRect) {
    switch (axis) {
      case VERTICAL:
        return candidateRect.top < currentBestRect.top;
      case HORIZONTAL:
        if (isRtl) {
          return candidateRect.right > currentBestRect.right;
        } else {
          return candidateRect.left < currentBestRect.left;
        }
    }
    throw new IllegalArgumentException("Invalid axis.");
  }

  /**
   * Is destRect a candidate for the next focus given the direction? This checks whether the dest is
   * at least partially to the direction of (e.g left of) from source.
   *
   * <p>Includes an edge case for an empty rect (which is used in some cases when searching from a
   * point on the screen).
   */
  private boolean isCandidate(Rect srcRect, Rect dstRect, @SpatialSearchDirection int direction) {
    switch (direction) {
      case TraversalStrategy.SEARCH_FOCUS_LEFT:
        return srcRect.left > dstRect.left
            && (srcRect.left >= dstRect.right || srcRect.right > dstRect.right)
            && (srcRect.left >= dstRect.right || Rect.intersects(srcRect, dstRect));
      case TraversalStrategy.SEARCH_FOCUS_RIGHT:
        return srcRect.right < dstRect.right
            && (srcRect.right <= dstRect.left || srcRect.left < dstRect.left)
            && (srcRect.right <= dstRect.left || Rect.intersects(srcRect, dstRect));
      case TraversalStrategy.SEARCH_FOCUS_UP:
        return srcRect.top > dstRect.top
            && (srcRect.top >= dstRect.bottom || srcRect.bottom > dstRect.bottom)
            && (srcRect.top >= dstRect.bottom || Rect.intersects(srcRect, dstRect));
      case TraversalStrategy.SEARCH_FOCUS_DOWN:
        return srcRect.bottom < dstRect.bottom
            && (srcRect.bottom <= dstRect.top || srcRect.top < dstRect.top)
            && (srcRect.bottom <= dstRect.top || Rect.intersects(srcRect, dstRect));
    }
    throw new IllegalArgumentException("Invalid direction.");
  }

  /** Returns the axis that is perpendicular to the search direction */
  private static Axis getMinorAxis(@SpatialSearchDirection int searchDirection) {
    switch (searchDirection) {
      case SEARCH_FOCUS_UP:
      case SEARCH_FOCUS_DOWN:
        return Axis.HORIZONTAL;
      case SEARCH_FOCUS_LEFT:
      case SEARCH_FOCUS_RIGHT:
        return Axis.VERTICAL;
      default: // fall out
    }
    throw new IllegalArgumentException("Invalid search direction.");
  }

  private enum Axis {
    VERTICAL,
    HORIZONTAL
  }
}
