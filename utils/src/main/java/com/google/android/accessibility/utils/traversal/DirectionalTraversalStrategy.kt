/*
 * Copyright (C) The Android Open Source Project
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
 *
 * Ported from Java to Kotlin.
 */

package com.google.android.accessibility.utils.traversal

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo.FOCUS_ACCESSIBILITY
import android.view.accessibility.AccessibilityNodeInfo.FOCUS_INPUT
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import com.google.android.accessibility.utils.AccessibilityNodeInfoUtils
import com.google.android.accessibility.utils.Filter
import com.google.android.accessibility.utils.FocusFinder
import com.google.android.accessibility.utils.WebInterfaceUtils
import kotlin.math.abs
import kotlin.math.max

class DirectionalTraversalStrategy(
  /** The root node within which to traverse. */
  private val root: AccessibilityNodeInfoCompat?,
  /** Instance for finding Accessibility/Input focus. */
  private val focusFinder: FocusFinder?,
) : TraversalStrategy {

  /** The bounds of the root node, padded slightly for intersection checks. */
  private val rootRectPadded: Rect

  /** A set of all visited nodes in root's hierarchy. */
  private val visitedNodes: MutableSet<AccessibilityNodeInfoCompat> = HashSet()

  /** A list of only focusable nodes. */
  private val focusableNodes: MutableList<AccessibilityNodeInfoCompat> = ArrayList()

  /** The set of focusable nodes that have focusable descendants. */
  private val containerNodes: MutableSet<AccessibilityNodeInfoCompat> = HashSet()

  /** Cache of nodes that have speech for use by AccessibilityNodeInfoUtils. */
  private val speakingNodesCache: MutableMap<AccessibilityNodeInfoCompat, Boolean> = HashMap()

  init {
    // The cached on-screen bounds of the root node.
    val rootRect = Rect()
    this.root?.getBoundsInScreen(rootRect)

    val fudge = -(rootRect.width() / 20) // 5% fudge factor to catch objects near edge.
    rootRectPadded = Rect(rootRect)
    rootRectPadded.inset(fudge, fudge)

    processNodes(this.root, /* forceRefresh= */ false)
  }

  /**
   * Goes through root and its descendant nodes, sorting out the focusable nodes and the container
   * nodes for use in finding focus. Does not re-process visitedNodes.
   *
   * @return whether the root is focusable or has focusable children in its hierarchy
   */
  private fun processNodes(root: AccessibilityNodeInfoCompat?, forceRefresh: Boolean): Boolean {
    if (root == null || visitedNodes.contains(root)) {
      return false
    }

    if (forceRefresh) {
      root.refresh()
    }

    val currentRect = Rect()
    root.getBoundsInScreen(currentRect)

    // Determine if the node is inside rootRect (within a fudge factor). If it is outside, we
    // will optimize by skipping its entire hierarchy.
    if (!Rect.intersects(currentRect, rootRectPadded)) {
      return false
    }

    visitedNodes.add(root)

    // When we reach a node that supports web navigation, we traverse using the web navigation
    // actions, so we should not add any of its descendants to the list of focusable nodes.
    if (WebInterfaceUtils.hasNativeWebContent(root)) {
      focusableNodes.add(root)
      return true
    } else {
      val isFocusable = AccessibilityNodeInfoUtils.shouldFocusNode(root, speakingNodesCache)
      if (isFocusable) {
        focusableNodes.add(root)
      }

      var hasFocusableDescendants = false
      val childCount = root.childCount
      for (i in 0 until childCount) {
        val child = root.getChild(i)
        if (child != null) {
          hasFocusableDescendants = hasFocusableDescendants or processNodes(child, forceRefresh)
        }
      }

      if (hasFocusableDescendants) {
        containerNodes.add(root)
      }

      return isFocusable || hasFocusableDescendants
    }
  }

  override fun findFocus(
    startNode: AccessibilityNodeInfoCompat?,
    direction: Int,
  ): AccessibilityNodeInfoCompat? {
    if (startNode == null) {
      return null
    } else if (startNode == root) {
      return getFirstOrderedFocus()
    }

    val focusedRect = Rect()
    getAssumedRectInScreen(startNode, focusedRect)

    return findFocusFromRect(startNode, focusedRect, direction)
  }

  /**
   * Searches the best candidate to focus in the given direction.
   *
   * @param focused The node which is currently accessibility-focused.
   * @param focusedRect The coordinates from which to start the search from. This may be different
   *     from the actual coordinates of {@code focused}.
   * @param direction The direction in which to search.
   * @return Returns the best candidate to focus in the given direction or {@code null} if there is
   *     no such candidate.
   */
  private fun findFocusFromRect(
    focused: AccessibilityNodeInfoCompat?,
    focusedRect: Rect,
    direction: Int,
  ): AccessibilityNodeInfoCompat? {
    // Using roughly the same algorithm as
    // frameworks/base/core/java/android/view/FocusFinder.java#findNextFocusInAbsoluteDirection

    val bestCandidateRect = Rect(focusedRect)
    when (direction) {
      TraversalStrategy.SEARCH_FOCUS_LEFT -> bestCandidateRect.offset(focusedRect.width() + 1, 0)
      TraversalStrategy.SEARCH_FOCUS_RIGHT ->
        bestCandidateRect.offset(-(focusedRect.width() + 1), 0)
      TraversalStrategy.SEARCH_FOCUS_UP -> bestCandidateRect.offset(0, focusedRect.height() + 1)
      TraversalStrategy.SEARCH_FOCUS_DOWN ->
        bestCandidateRect.offset(0, -(focusedRect.height() + 1))
      else -> {}
    }

    var closest: AccessibilityNodeInfoCompat? = null
    for (focusable in focusableNodes) {
      // Skip the currently-focused view.
      if (focusable == focused || focusable == root) {
        continue
      }

      val otherRect = Rect()
      getAssumedRectInScreen(focusable, otherRect)

      if (isBetterCandidate(direction, focusedRect, otherRect, bestCandidateRect)) {
        bestCandidateRect.set(otherRect)
        closest = focusable
      }
    }

    return closest
  }

  /**
   * Selects an item to focus when there is no current accessibility focus.
   *
   * <p>Uses a two-pronged strategy. First tries to see if there is an input-focused node, and if
   * so, returns that node. Otherwise, returns the item that an OrderedTraversalStrategy would first
   * focus; this has the advantage of working nicely for both LTR and RTL users.
   */
  private fun getFirstOrderedFocus(): AccessibilityNodeInfoCompat? {
    val filter =
      object : Filter<AccessibilityNodeInfoCompat>() {
        override fun accept(node: AccessibilityNodeInfoCompat?): Boolean {
          return node != null && focusableNodes.contains(node)
        }
      }

    // 1. Attempt to find input-focused node.
    val inputFocused = focusFinder!!.findFocusCompat(FOCUS_INPUT)

    val target = AccessibilityNodeInfoUtils.getSelfOrMatchingAncestor(inputFocused, filter)
    if (target != null) {
      return target
    }

    // 2. Just use the OrderedTraversalStrategy.
    val orderedStrategy = OrderedTraversalStrategy(root)

    return TraversalStrategyUtils.searchFocus(
      orderedStrategy, root, TraversalStrategy.SEARCH_FOCUS_FORWARD, filter)
  }

  override fun focusFirst(
    root: AccessibilityNodeInfoCompat?,
    direction: Int,
  ): AccessibilityNodeInfoCompat? {
    if (root == null) {
      return null
    }

    val rootRect = Rect()
    root.getBoundsInScreen(rootRect)

    val focusedNode = focusFinder!!.findFocusCompat(FOCUS_ACCESSIBILITY)

    val searchRect = Rect()
    if (focusedNode != null) {
      getSearchStartRect(focusedNode, direction, searchRect)
    } else if (direction == TraversalStrategy.SEARCH_FOCUS_LEFT) {
      searchRect.set(rootRect.right, rootRect.top, rootRect.right + 1, rootRect.bottom)
    } else if (direction == TraversalStrategy.SEARCH_FOCUS_RIGHT) {
      searchRect.set(rootRect.left - 1, rootRect.top, rootRect.left, rootRect.bottom)
    } else if (direction == TraversalStrategy.SEARCH_FOCUS_UP) {
      searchRect.set(rootRect.left, rootRect.bottom, rootRect.right, rootRect.bottom + 1)
    } else {
      searchRect.set(rootRect.left, rootRect.top - 1, rootRect.right, rootRect.top)
    }

    return findFocusFromRect(focusedNode, searchRect, direction)
  }

  override fun getSpeakingNodesCache(): Map<AccessibilityNodeInfoCompat, Boolean>? = null

  /**
   * Returns the bounding rect of the given node for directional navigation purposes. Any node that
   * is a container of a focusable node will be reduced to a strip at its very top edge.
   */
  private fun getAssumedRectInScreen(node: AccessibilityNodeInfoCompat, assumedRect: Rect) {
    node.getBoundsInScreen(assumedRect)
    if (containerNodes.contains(node)) {
      assumedRect.set(assumedRect.left, assumedRect.top, assumedRect.right, assumedRect.top + 1)
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
  private fun getSearchStartRect(node: AccessibilityNodeInfoCompat, direction: Int, rect: Rect) {
    val focusedRect = Rect()
    node.getBoundsInScreen(focusedRect)

    val rootBounds = Rect()
    root!!.getBoundsInScreen(rootBounds)

    when (direction) {
      TraversalStrategy.SEARCH_FOCUS_LEFT ->
        // Start from right and move leftwards.
        rect.set(
          rootBounds.right,
          focusedRect.top,
          rootBounds.right + focusedRect.width(),
          focusedRect.bottom,
        )
      TraversalStrategy.SEARCH_FOCUS_RIGHT ->
        // Start from left and move rightwards.
        rect.set(
          rootBounds.left - focusedRect.width(),
          focusedRect.top,
          rootBounds.left,
          focusedRect.bottom,
        )
      TraversalStrategy.SEARCH_FOCUS_UP ->
        // Start from bottom and move upwards.
        rect.set(
          focusedRect.left,
          rootBounds.bottom,
          focusedRect.right,
          rootBounds.bottom + focusedRect.height(),
        )
      TraversalStrategy.SEARCH_FOCUS_DOWN ->
        // Start from top and move downwards.
        rect.set(
          focusedRect.left,
          rootBounds.top - focusedRect.height(),
          focusedRect.right,
          rootBounds.top,
        )
      else -> throw IllegalArgumentException("direction must be a SearchDirection")
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
  private fun isBetterCandidate(direction: Int, source: Rect, rect1: Rect, rect2: Rect): Boolean {
    // to be a better candidate, need to at least be a candidate in the first
    // place :)
    if (!isCandidate(source, rect1, direction)) {
      return false
    }

    // we know that rect1 is a candidate.. if rect2 is not a candidate,
    // rect1 is better
    if (!isCandidate(source, rect2, direction)) {
      return true
    }

    // if rect1 is better by beam, it wins
    if (beamBeats(direction, source, rect1, rect2)) {
      return true
    }

    // if rect2 is better, then rect1 can't be :)
    if (beamBeats(direction, source, rect2, rect1)) {
      return false
    }

    // otherwise, do fudge-tastic comparison of the major and minor axis
    return (getWeightedDistanceFor(
      majorAxisDistance(direction, source, rect1),
      minorAxisDistance(direction, source, rect1),
    ) <
      getWeightedDistanceFor(
        majorAxisDistance(direction, source, rect2),
        minorAxisDistance(direction, source, rect2),
      ))
  }

  /**
   * One rectangle may be another candidate than another by virtue of being exclusively in the beam
   * of the source rect.
   *
   * @return Whether rect1 is a better candidate than rect2 by virtue of it being in src's beam
   */
  private fun beamBeats(direction: Int, source: Rect, rect1: Rect, rect2: Rect): Boolean {
    val rect1InSrcBeam = beamsOverlap(direction, source, rect1)
    val rect2InSrcBeam = beamsOverlap(direction, source, rect2)

    // if rect1 isn't exclusively in the src beam, it doesn't win
    if (rect2InSrcBeam || !rect1InSrcBeam) {
      return false
    }

    // we know rect1 is in the beam, and rect2 is not

    // if rect1 is to the direction of, and rect2 is not, rect1 wins.
    // for example, for direction left, if rect1 is to the left of the source
    // and rect2 is below, then we always prefer the in beam rect1, since rect2
    // could be reached by going down.
    if (!isToDirectionOf(direction, source, rect2)) {
      return true
    }

    // for horizontal directions, being exclusively in beam always wins
    if (direction == TraversalStrategy.SEARCH_FOCUS_LEFT ||
      direction == TraversalStrategy.SEARCH_FOCUS_RIGHT
    ) {
      return true
    }

    // for vertical directions, beams only beat up to a point:
    // now, as long as rect2 isn't completely closer, rect1 wins
    // e.g. for direction down, completely closer means for rect2's top
    // edge to be closer to the source's top edge than rect1's bottom edge.
    return (majorAxisDistance(direction, source, rect1) <
      majorAxisDistanceToFarEdge(direction, source, rect2))
  }

  /**
   * Fudge-factor opportunity: how to calculate distance given major and minor axis distances.
   * Warning: this fudge factor is finely tuned, be sure to run all focus tests if you dare tweak
   * it.
   */
  private fun getWeightedDistanceFor(majorAxisDistance: Int, minorAxisDistance: Int): Int {
    return if (majorAxisDistance > 10000 || minorAxisDistance > 10000) {
      Int.MAX_VALUE
    } else {
      // Won't overflow; max possible value = 1400000000 < Integer.MAX_VALUE.
      13 * majorAxisDistance * majorAxisDistance + minorAxisDistance * minorAxisDistance
    }
  }

  /**
   * Is destRect a candidate for the next focus given the direction? This checks whether the dest is
   * at least partially to the direction of (e.g left of) from source.
   *
   * <p>Includes an edge case for an empty rect (which is used in some cases when searching from a
   * point on the screen).
   */
  private fun isCandidate(srcRect: Rect, destRect: Rect, direction: Int): Boolean {
    when (direction) {
      TraversalStrategy.SEARCH_FOCUS_LEFT -> {
        return (srcRect.right > destRect.right || srcRect.left >= destRect.right) &&
          srcRect.left > destRect.left
      }
      TraversalStrategy.SEARCH_FOCUS_RIGHT -> {
        return (srcRect.left < destRect.left || srcRect.right <= destRect.left) &&
          srcRect.right < destRect.right
      }
      TraversalStrategy.SEARCH_FOCUS_UP -> {
        return (srcRect.bottom > destRect.bottom || srcRect.top >= destRect.bottom) &&
          srcRect.top > destRect.top
      }
      TraversalStrategy.SEARCH_FOCUS_DOWN -> {
        return (srcRect.top < destRect.top || srcRect.bottom <= destRect.top) &&
          srcRect.bottom < destRect.bottom
      }
      else -> {}
    }
    throw IllegalArgumentException("direction must be a SearchDirection")
  }

  /**
   * Do the "beams" w.r.t the given direction's axis of rect1 and rect2 overlap?
   *
   * @param direction the direction (up, down, left, right)
   * @param rect1 The first rectangle
   * @param rect2 The second rectangle
   * @return whether the beams overlap
   */
  private fun beamsOverlap(direction: Int, rect1: Rect, rect2: Rect): Boolean {
    when (direction) {
      TraversalStrategy.SEARCH_FOCUS_LEFT, TraversalStrategy.SEARCH_FOCUS_RIGHT -> {
        return (rect2.bottom >= rect1.top) && (rect2.top <= rect1.bottom)
      }
      TraversalStrategy.SEARCH_FOCUS_UP, TraversalStrategy.SEARCH_FOCUS_DOWN -> {
        return (rect2.right >= rect1.left) && (rect2.left <= rect1.right)
      }
      else -> {}
    }
    throw IllegalArgumentException("direction must be a SearchDirection")
  }

  /** e.g. for left, is 'to left of' */
  private fun isToDirectionOf(direction: Int, src: Rect, dest: Rect): Boolean {
    when (direction) {
      TraversalStrategy.SEARCH_FOCUS_LEFT -> {
        return src.left >= dest.right
      }
      TraversalStrategy.SEARCH_FOCUS_RIGHT -> {
        return src.right <= dest.left
      }
      TraversalStrategy.SEARCH_FOCUS_UP -> {
        return src.top >= dest.bottom
      }
      TraversalStrategy.SEARCH_FOCUS_DOWN -> {
        return src.bottom <= dest.top
      }
      else -> {}
    }
    throw IllegalArgumentException("direction must be a SearchDirection")
  }

  companion object {
    /**
     * @return The distance from the edge furthest in the given direction of source to the edge
     *     nearest in the given direction of dest. If the dest is not in the direction from source,
     *     return 0.
     */
    @JvmStatic
    internal fun majorAxisDistance(direction: Int, source: Rect, dest: Rect): Int =
      max(0, majorAxisDistanceRaw(direction, source, dest))

    @JvmStatic
    internal fun majorAxisDistanceRaw(direction: Int, source: Rect, dest: Rect): Int {
      when (direction) {
        TraversalStrategy.SEARCH_FOCUS_LEFT -> {
          return source.left - dest.right
        }
        TraversalStrategy.SEARCH_FOCUS_RIGHT -> {
          return dest.left - source.right
        }
        TraversalStrategy.SEARCH_FOCUS_UP -> {
          return source.top - dest.bottom
        }
        TraversalStrategy.SEARCH_FOCUS_DOWN -> {
          return dest.top - source.bottom
        }
        else -> {}
      }
      throw IllegalArgumentException("direction must be a SearchDirection")
    }

    /**
     * @return The distance along the major axis w.r.t the direction from the edge of source to the
     *     far edge of dest. If the dest is not in the direction from source, return 1 (to break
     *     ties with {@link #majorAxisDistance}).
     */
    @JvmStatic
    internal fun majorAxisDistanceToFarEdge(direction: Int, source: Rect, dest: Rect): Int =
      max(1, majorAxisDistanceToFarEdgeRaw(direction, source, dest))

    @JvmStatic
    internal fun majorAxisDistanceToFarEdgeRaw(direction: Int, source: Rect, dest: Rect): Int {
      when (direction) {
        TraversalStrategy.SEARCH_FOCUS_LEFT -> {
          return source.left - dest.left
        }
        TraversalStrategy.SEARCH_FOCUS_RIGHT -> {
          return dest.right - source.right
        }
        TraversalStrategy.SEARCH_FOCUS_UP -> {
          return source.top - dest.top
        }
        TraversalStrategy.SEARCH_FOCUS_DOWN -> {
          return dest.bottom - source.bottom
        }
        else -> {}
      }
      throw IllegalArgumentException("direction must be a SearchDirection")
    }

    /**
     * Find the distance on the minor axis w.r.t the direction to the nearest edge of the
     * destination rectangle.
     *
     * @param direction the direction (up, down, left, right)
     * @param source The source rect.
     * @param dest The destination rect.
     * @return The distance.
     */
    @JvmStatic
    internal fun minorAxisDistance(direction: Int, source: Rect, dest: Rect): Int {
      when (direction) {
        TraversalStrategy.SEARCH_FOCUS_LEFT, TraversalStrategy.SEARCH_FOCUS_RIGHT -> {
          // the distance between the center verticals
          return abs((source.top + source.height() / 2) - (dest.top + dest.height() / 2))
        }
        TraversalStrategy.SEARCH_FOCUS_UP, TraversalStrategy.SEARCH_FOCUS_DOWN -> {
          // the distance between the center horizontals
          return abs((source.left + source.width() / 2) - (dest.left + dest.width() / 2))
        }
        else -> {}
      }
      throw IllegalArgumentException("direction must be a SearchDirection")
    }
  }

  /* END CODE COPIED FROM frameworks/base/core/java/android/view/FocusFinder.java */
}
