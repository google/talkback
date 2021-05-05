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

import static android.view.accessibility.AccessibilityNodeInfo.FOCUS_ACCESSIBILITY;
import static android.view.accessibility.AccessibilityNodeInfo.FOCUS_INPUT;

import android.graphics.Rect;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import com.google.android.accessibility.utils.AccessibilityNodeInfoUtils;
import com.google.android.accessibility.utils.BuildVersionUtils;
import com.google.android.accessibility.utils.Filter;
import com.google.android.accessibility.utils.FocusFinder;
import com.google.android.accessibility.utils.WebInterfaceUtils;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.checkerframework.checker.nullness.qual.Nullable;

public class DirectionalTraversalStrategy implements TraversalStrategy {

  /** The root node within which to traverse. */
  private AccessibilityNodeInfoCompat mRoot;

  /** Instance for finding Accessibility/Input focus. */
  private final FocusFinder focusFinder;

  /** The cached on-screen bounds of the root node. */
  private final Rect mRootRect;

  /** The bounds of the root node, padded slightly for intersection checks. */
  private final Rect mRootRectPadded;

  /** A set of all visited nodes in mRoot's hierarchy. */
  private final Set<AccessibilityNodeInfoCompat> visitedNodes = new HashSet<>();

  /** A list of only focusable nodes. */
  private final List<AccessibilityNodeInfoCompat> mFocusables = new ArrayList<>();

  /** The set of focusable nodes that have focusable descendants. */
  private final Set<AccessibilityNodeInfoCompat> mContainers = new HashSet<>();

  /** Cache of nodes that have speech for use by AccessibilityNodeInfoUtils. */
  private final Map<AccessibilityNodeInfoCompat, Boolean> mSpeakingNodesCache = new HashMap<>();

  public DirectionalTraversalStrategy(AccessibilityNodeInfoCompat root, FocusFinder focusFinder) {
    mRoot = AccessibilityNodeInfoCompat.obtain(root);
    this.focusFinder = focusFinder;

    mRootRect = new Rect();
    mRoot.getBoundsInScreen(mRootRect);

    int fudge = -(mRootRect.width() / 20); // 5% fudge factor to catch objects near edge.
    mRootRectPadded = new Rect(mRootRect);
    mRootRectPadded.inset(fudge, fudge);

    processNodes(mRoot, false /* forceRefresh */);

    // Before N, sometimes AccessibilityNodeInfo is not properly updated after transitions
    // occur. This was fixed in a system framework change for N. REFERTO for context.
    // To work-around, manually refresh AccessibilityNodeInfo if it initially
    // looks like there's nothing to focus on.
    if (mFocusables.isEmpty() && !BuildVersionUtils.isAtLeastN()) {
      recycle(false /* recycleRoot */);
      processNodes(mRoot, true /* forceRefresh */);
    }
  }

  /**
   * Goes through root and its descendant nodes, sorting out the focusable nodes and the container
   * nodes for use in finding focus. Does not re-process visitedNodes.
   *
   * @return whether the root is focusable or has focusable children in its hierarchy
   */
  private boolean processNodes(AccessibilityNodeInfoCompat root, boolean forceRefresh) {
    if (root == null || visitedNodes.contains(root)) {
      return false;
    }

    if (forceRefresh) {
      root.refresh();
    }

    Rect currentRect = new Rect();
    root.getBoundsInScreen(currentRect);

    // Determine if the node is inside mRootRect (within a fudge factor). If it is outside, we
    // will optimize by skipping its entire hierarchy.
    if (!Rect.intersects(currentRect, mRootRectPadded)) {
      return false;
    }

    AccessibilityNodeInfoCompat rootNode = AccessibilityNodeInfoCompat.obtain(root);
    visitedNodes.add(rootNode);

    // When we reach a node that supports web navigation, we traverse using the web navigation
    // actions, so we should not add any of its descendants to the list of focusable nodes.
    if (WebInterfaceUtils.hasNativeWebContent(rootNode)) {
      mFocusables.add(rootNode);
      return true;
    } else {
      boolean isFocusable =
          AccessibilityNodeInfoUtils.shouldFocusNode(rootNode, mSpeakingNodesCache);
      if (isFocusable) {
        mFocusables.add(rootNode);
      }

      boolean hasFocusableDescendants = false;
      int childCount = rootNode.getChildCount();
      for (int i = 0; i < childCount; ++i) {
        AccessibilityNodeInfoCompat child = rootNode.getChild(i);
        if (child != null) {
          hasFocusableDescendants |= processNodes(child, forceRefresh);
          child.recycle();
        }
      }

      if (hasFocusableDescendants) {
        mContainers.add(rootNode);
      }

      return isFocusable || hasFocusableDescendants;
    }
  }

  @Override
  public @Nullable AccessibilityNodeInfoCompat findFocus(
      AccessibilityNodeInfoCompat startNode, int direction) {
    if (startNode == null) {
      return null;
    } else if (startNode.equals(mRoot)) {
      return getFirstOrderedFocus();
    }

    Rect focusedRect = new Rect();
    getAssumedRectInScreen(startNode, focusedRect);

    return findFocus(startNode, focusedRect, direction);
  }

  /** Caller must recycle returned node. */
  public @Nullable AccessibilityNodeInfoCompat findFocus(
      AccessibilityNodeInfoCompat focused, Rect focusedRect, int direction) {
    // Using roughly the same algorithm as
    // frameworks/base/core/java/android/view/FocusFinder.java#findNextFocusInAbsoluteDirection

    Rect bestCandidateRect = new Rect(focusedRect);
    switch (direction) {
      case TraversalStrategy.SEARCH_FOCUS_LEFT:
        bestCandidateRect.offset(focusedRect.width() + 1, 0);
        break;
      case TraversalStrategy.SEARCH_FOCUS_RIGHT:
        bestCandidateRect.offset(-(focusedRect.width() + 1), 0);
        break;
      case TraversalStrategy.SEARCH_FOCUS_UP:
        bestCandidateRect.offset(0, focusedRect.height() + 1);
        break;
      case TraversalStrategy.SEARCH_FOCUS_DOWN:
        bestCandidateRect.offset(0, -(focusedRect.height() + 1));
        break;
      default: // fall out
    }

    AccessibilityNodeInfoCompat closest = null;
    for (AccessibilityNodeInfoCompat focusable : mFocusables) {
      // Skip the currently-focused view.
      if (focusable.equals(focused) || focusable.equals(mRoot)) {
        continue;
      }

      Rect otherRect = new Rect();
      getAssumedRectInScreen(focusable, otherRect);

      if (isBetterCandidate(direction, focusedRect, otherRect, bestCandidateRect)) {
        bestCandidateRect.set(otherRect);
        closest = focusable;
      }
    }

    if (closest != null) {
      return AccessibilityNodeInfoCompat.obtain(closest);
    }

    return null;
  }

  /**
   * Selects an item to focus when there is no current accessibility focus.
   *
   * <p>Uses a two-pronged strategy. First tries to see if there is an input-focused node, and if
   * so, returns that node. Otherwise, returns the item that an OrderedTraversalStrategy would first
   * focus; this has the advantage of working nicely for both LTR and RTL users.
   */
  private @Nullable AccessibilityNodeInfoCompat getFirstOrderedFocus() {
    Filter<AccessibilityNodeInfoCompat> filter =
        new Filter<AccessibilityNodeInfoCompat>() {
          @Override
          public boolean accept(AccessibilityNodeInfoCompat node) {
            return node != null && mFocusables.contains(node);
          }
        };

    // 1. Attempt to find input-focused node.
    AccessibilityNodeInfoCompat inputFocused = focusFinder.findFocusCompat(FOCUS_INPUT);

    try {
      AccessibilityNodeInfoCompat target =
          AccessibilityNodeInfoUtils.getSelfOrMatchingAncestor(inputFocused, filter);
      if (target != null) {
        return target;
      }
    } finally {
      if (inputFocused != null) {
        inputFocused.recycle();
      }
    }

    // 2. Just use the OrderedTraversalStrategy.
    final OrderedTraversalStrategy orderedStrategy = new OrderedTraversalStrategy(mRoot);
    try {
      // Should not need to obtain() here; the inner code should do this for us.
      return TraversalStrategyUtils.searchFocus(
          orderedStrategy, mRoot, TraversalStrategy.SEARCH_FOCUS_FORWARD, filter);
    } finally {
      orderedStrategy.recycle();
    }
  }

  /** Caller must recycle returned node. */
  @Override
  public @Nullable AccessibilityNodeInfoCompat focusInitial(
      AccessibilityNodeInfoCompat root, int direction) {
    if (root == null) {
      return null;
    }

    Rect rootRect = new Rect();
    root.getBoundsInScreen(rootRect);

    AccessibilityNodeInfoCompat focusedNode = focusFinder.findFocusCompat(FOCUS_ACCESSIBILITY);

    Rect searchRect = new Rect();
    if (focusedNode != null) {
      getSearchStartRect(focusedNode, direction, searchRect);
    } else if (direction == TraversalStrategy.SEARCH_FOCUS_LEFT) {
      searchRect.set(rootRect.right, rootRect.top, rootRect.right + 1, rootRect.bottom);
    } else if (direction == TraversalStrategy.SEARCH_FOCUS_RIGHT) {
      searchRect.set(rootRect.left - 1, rootRect.top, rootRect.left, rootRect.bottom);
    } else if (direction == TraversalStrategy.SEARCH_FOCUS_UP) {
      searchRect.set(rootRect.left, rootRect.bottom, rootRect.right, rootRect.bottom + 1);
    } else {
      searchRect.set(rootRect.left, rootRect.top - 1, rootRect.right, rootRect.top);
    }

    return findFocus(focusedNode, searchRect, direction);
  }

  @Override
  public Map<AccessibilityNodeInfoCompat, Boolean> getSpeakingNodesCache() {
    return null;
  }

  private void recycle(boolean recycleRoot) {
    for (AccessibilityNodeInfoCompat node : visitedNodes) {
      node.recycle();
    }
    visitedNodes.clear();
    mFocusables.clear(); // No recycle needed for mFocusables or mContainers because their
    mContainers.clear(); // nodes were already recycled from visitedNodes.
    mSpeakingNodesCache.clear();

    if (recycleRoot) {
      mRoot.recycle();
      mRoot = null;
    }
  }

  @Override
  public void recycle() {
    recycle(true);
  }

  /**
   * Returns the bounding rect of the given node for directional navigation purposes. Any node that
   * is a container of a focusable node will be reduced to a strip at its very top edge.
   */
  private void getAssumedRectInScreen(AccessibilityNodeInfoCompat node, Rect assumedRect) {
    node.getBoundsInScreen(assumedRect);
    if (mContainers.contains(node)) {
      assumedRect.set(assumedRect.left, assumedRect.top, assumedRect.right, assumedRect.top + 1);
    }
  }

  /**
   * Given a focus rectangle, returns another rectangle that is placed at the beginning of the row
   * or column of the focused object, depending on the direction in which we are navigating.
   *
   * <p>Example:
   *
   * <pre>
   *  +---------+
   *  |         | node=#
   * A|      #  | When direction=TraversalStrategy.SEARCH_FOCUS_RIGHT, then a rectangle A with
   *  |         |   same width and height as node gets returned.
   *  |         | When direction=TraversalStrategy.SEARCH_FOCUS_UP, then a rectangle B with same
   *  +---------+   width and height as node gets returned.
   *         B
   * </pre>
   */
  private void getSearchStartRect(AccessibilityNodeInfoCompat node, int direction, Rect rect) {
    Rect focusedRect = new Rect();
    node.getBoundsInScreen(focusedRect);

    Rect rootBounds = new Rect();
    mRoot.getBoundsInScreen(rootBounds);

    switch (direction) {
      case TraversalStrategy.SEARCH_FOCUS_LEFT: // Start from right and move leftwards.
        rect.set(
            rootBounds.right,
            focusedRect.top,
            rootBounds.right + focusedRect.width(),
            focusedRect.bottom);
        break;
      case TraversalStrategy.SEARCH_FOCUS_RIGHT: // Start from left and move rightwards.
        rect.set(
            rootBounds.left - focusedRect.width(),
            focusedRect.top,
            rootBounds.left,
            focusedRect.bottom);
        break;
      case TraversalStrategy.SEARCH_FOCUS_UP: // Start from bottom and move upwards.
        rect.set(
            focusedRect.left,
            rootBounds.bottom,
            focusedRect.right,
            rootBounds.bottom + focusedRect.height());
        break;
      case TraversalStrategy.SEARCH_FOCUS_DOWN: // Start from top and move downwards.
        rect.set(
            focusedRect.left,
            rootBounds.top - focusedRect.height(),
            focusedRect.right,
            rootBounds.top);
        break;
      default:
        throw new IllegalArgumentException("direction must be a SearchDirection");
    }
  }

  /*
   * BEGIN CODE COPIED FROM frameworks/base/core/java/android/view/FocusFinder.java
   * These lines were last revised 2009-03-03 in revision 9066cfe9.
   * Modifications from original:
   *   - Uses TraversalStrategy.SEARCH_FOCUS_* constants instead of View.FOCUS_* constants
   *   - getWeightedDistanceFor() returns MAX_VALUE for very large values to prevent overflow
   */

  /**
   * Is rect1 a better candidate than rect2 for a focus search in a particular direction from a
   * source rect? This is the core routine that determines the order of focus searching.
   *
   * @param direction the direction (up, down, left, right)
   * @param source The source we are searching from
   * @param rect1 The candidate rectangle
   * @param rect2 The current best candidate.
   * @return Whether the candidate is the new best.
   */
  boolean isBetterCandidate(int direction, Rect source, Rect rect1, Rect rect2) {

    // to be a better candidate, need to at least be a candidate in the first
    // place :)
    if (!isCandidate(source, rect1, direction)) {
      return false;
    }

    // we know that rect1 is a candidate.. if rect2 is not a candidate,
    // rect1 is better
    if (!isCandidate(source, rect2, direction)) {
      return true;
    }

    // if rect1 is better by beam, it wins
    if (beamBeats(direction, source, rect1, rect2)) {
      return true;
    }

    // if rect2 is better, then rect1 cant' be :)
    if (beamBeats(direction, source, rect2, rect1)) {
      return false;
    }

    // otherwise, do fudge-tastic comparison of the major and minor axis
    return (getWeightedDistanceFor(
            majorAxisDistance(direction, source, rect1),
            minorAxisDistance(direction, source, rect1))
        < getWeightedDistanceFor(
            majorAxisDistance(direction, source, rect2),
            minorAxisDistance(direction, source, rect2)));
  }

  /**
   * One rectangle may be another candidate than another by virtue of being exclusively in the beam
   * of the source rect.
   *
   * @return Whether rect1 is a better candidate than rect2 by virtue of it being in src's beam
   */
  boolean beamBeats(int direction, Rect source, Rect rect1, Rect rect2) {
    final boolean rect1InSrcBeam = beamsOverlap(direction, source, rect1);
    final boolean rect2InSrcBeam = beamsOverlap(direction, source, rect2);

    // if rect1 isn't exclusively in the src beam, it doesn't win
    if (rect2InSrcBeam || !rect1InSrcBeam) {
      return false;
    }

    // we know rect1 is in the beam, and rect2 is not

    // if rect1 is to the direction of, and rect2 is not, rect1 wins.
    // for example, for direction left, if rect1 is to the left of the source
    // and rect2 is below, then we always prefer the in beam rect1, since rect2
    // could be reached by going down.
    if (!isToDirectionOf(direction, source, rect2)) {
      return true;
    }

    // for horizontal directions, being exclusively in beam always wins
    if ((direction == TraversalStrategy.SEARCH_FOCUS_LEFT
        || direction == TraversalStrategy.SEARCH_FOCUS_RIGHT)) {
      return true;
    }

    // for vertical directions, beams only beat up to a point:
    // now, as long as rect2 isn't completely closer, rect1 wins
    // e.g for direction down, completely closer means for rect2's top
    // edge to be closer to the source's top edge than rect1's bottom edge.
    return (majorAxisDistance(direction, source, rect1)
        < majorAxisDistanceToFarEdge(direction, source, rect2));
  }

  /**
   * Fudge-factor opportunity: how to calculate distance given major and minor axis distances.
   * Warning: this fudge factor is finely tuned, be sure to run all focus tests if you dare tweak
   * it.
   */
  int getWeightedDistanceFor(int majorAxisDistance, int minorAxisDistance) {
    if (majorAxisDistance > 10000 || minorAxisDistance > 10000) {
      return Integer.MAX_VALUE;
    } else {
      // Won't overflow; max possible value = 1400000000 < Integer.MAX_VALUE.
      return 13 * majorAxisDistance * majorAxisDistance + minorAxisDistance * minorAxisDistance;
    }
  }

  /**
   * Is destRect a candidate for the next focus given the direction? This checks whether the dest is
   * at least partially to the direction of (e.g left of) from source.
   *
   * <p>Includes an edge case for an empty rect (which is used in some cases when searching from a
   * point on the screen).
   */
  boolean isCandidate(Rect srcRect, Rect destRect, int direction) {
    switch (direction) {
      case TraversalStrategy.SEARCH_FOCUS_LEFT:
        return (srcRect.right > destRect.right || srcRect.left >= destRect.right)
            && srcRect.left > destRect.left;
      case TraversalStrategy.SEARCH_FOCUS_RIGHT:
        return (srcRect.left < destRect.left || srcRect.right <= destRect.left)
            && srcRect.right < destRect.right;
      case TraversalStrategy.SEARCH_FOCUS_UP:
        return (srcRect.bottom > destRect.bottom || srcRect.top >= destRect.bottom)
            && srcRect.top > destRect.top;
      case TraversalStrategy.SEARCH_FOCUS_DOWN:
        return (srcRect.top < destRect.top || srcRect.bottom <= destRect.top)
            && srcRect.bottom < destRect.bottom;
      default: // fall out
    }
    throw new IllegalArgumentException("direction must be a SearchDirection");
  }

  /**
   * Do the "beams" w.r.t the given direction's axis of rect1 and rect2 overlap?
   *
   * @param direction the direction (up, down, left, right)
   * @param rect1 The first rectangle
   * @param rect2 The second rectangle
   * @return whether the beams overlap
   */
  boolean beamsOverlap(int direction, Rect rect1, Rect rect2) {
    switch (direction) {
      case TraversalStrategy.SEARCH_FOCUS_LEFT:
      case TraversalStrategy.SEARCH_FOCUS_RIGHT:
        return (rect2.bottom >= rect1.top) && (rect2.top <= rect1.bottom);
      case TraversalStrategy.SEARCH_FOCUS_UP:
      case TraversalStrategy.SEARCH_FOCUS_DOWN:
        return (rect2.right >= rect1.left) && (rect2.left <= rect1.right);
      default: // fall out
    }
    throw new IllegalArgumentException("direction must be a SearchDirection");
  }

  /** e.g for left, is 'to left of' */
  boolean isToDirectionOf(int direction, Rect src, Rect dest) {
    switch (direction) {
      case TraversalStrategy.SEARCH_FOCUS_LEFT:
        return src.left >= dest.right;
      case TraversalStrategy.SEARCH_FOCUS_RIGHT:
        return src.right <= dest.left;
      case TraversalStrategy.SEARCH_FOCUS_UP:
        return src.top >= dest.bottom;
      case TraversalStrategy.SEARCH_FOCUS_DOWN:
        return src.bottom <= dest.top;
      default: // fall out
    }
    throw new IllegalArgumentException("direction must be a SearchDirection");
  }

  /**
   * @return The distance from the edge furthest in the given direction of source to the edge
   *     nearest in the given direction of dest. If the dest is not in the direction from source,
   *     return 0.
   */
  static int majorAxisDistance(int direction, Rect source, Rect dest) {
    return Math.max(0, majorAxisDistanceRaw(direction, source, dest));
  }

  static int majorAxisDistanceRaw(int direction, Rect source, Rect dest) {
    switch (direction) {
      case TraversalStrategy.SEARCH_FOCUS_LEFT:
        return source.left - dest.right;
      case TraversalStrategy.SEARCH_FOCUS_RIGHT:
        return dest.left - source.right;
      case TraversalStrategy.SEARCH_FOCUS_UP:
        return source.top - dest.bottom;
      case TraversalStrategy.SEARCH_FOCUS_DOWN:
        return dest.top - source.bottom;
      default: // fall out
    }
    throw new IllegalArgumentException("direction must be a SearchDirection");
  }

  /**
   * @return The distance along the major axis w.r.t the direction from the edge of source to the
   *     far edge of dest. If the dest is not in the direction from source, return 1 (to break ties
   *     with {@link #majorAxisDistance}).
   */
  static int majorAxisDistanceToFarEdge(int direction, Rect source, Rect dest) {
    return Math.max(1, majorAxisDistanceToFarEdgeRaw(direction, source, dest));
  }

  static int majorAxisDistanceToFarEdgeRaw(int direction, Rect source, Rect dest) {
    switch (direction) {
      case TraversalStrategy.SEARCH_FOCUS_LEFT:
        return source.left - dest.left;
      case TraversalStrategy.SEARCH_FOCUS_RIGHT:
        return dest.right - source.right;
      case TraversalStrategy.SEARCH_FOCUS_UP:
        return source.top - dest.top;
      case TraversalStrategy.SEARCH_FOCUS_DOWN:
        return dest.bottom - source.bottom;
      default: // fall out
    }
    throw new IllegalArgumentException("direction must be a SearchDirection");
  }

  /**
   * Find the distance on the minor axis w.r.t the direction to the nearest edge of the destination
   * rectangle.
   *
   * @param direction the direction (up, down, left, right)
   * @param source The source rect.
   * @param dest The destination rect.
   * @return The distance.
   */
  static int minorAxisDistance(int direction, Rect source, Rect dest) {
    switch (direction) {
      case TraversalStrategy.SEARCH_FOCUS_LEFT:
      case TraversalStrategy.SEARCH_FOCUS_RIGHT:
        // the distance between the center verticals
        return Math.abs(((source.top + source.height() / 2) - ((dest.top + dest.height() / 2))));
      case TraversalStrategy.SEARCH_FOCUS_UP:
      case TraversalStrategy.SEARCH_FOCUS_DOWN:
        // the distance between the center horizontals
        return Math.abs(((source.left + source.width() / 2) - ((dest.left + dest.width() / 2))));
      default: // fall out
    }
    throw new IllegalArgumentException("direction must be a SearchDirection");
  }

  /* END CODE COPIED FROM frameworks/base/core/java/android/view/FocusFinder.java */

}
