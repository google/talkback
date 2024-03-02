/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.google.android.accessibility.talkback.focusmanagement.record;

import static com.google.android.accessibility.talkback.focusmanagement.record.NodeDescription.OUT_OF_RANGE;
import static com.google.android.accessibility.talkback.focusmanagement.record.NodeDescription.UNDEFINED_INDEX;
import static com.google.android.accessibility.utils.traversal.TraversalStrategy.SEARCH_FOCUS_FORWARD;
import static java.util.Comparator.comparingInt;

import android.graphics.Rect;
import android.text.TextUtils;
import android.view.accessibility.AccessibilityNodeInfo;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat.CollectionItemInfoCompat;
import com.google.android.accessibility.utils.AccessibilityNode;
import com.google.android.accessibility.utils.AccessibilityNodeInfoUtils;
import com.google.android.accessibility.utils.DiagnosticOverlayUtils;
import com.google.android.accessibility.utils.Filter;
import com.google.android.accessibility.utils.FocusFinder;
import com.google.android.accessibility.utils.LogDepth;
import com.google.android.accessibility.utils.Role;
import com.google.android.accessibility.utils.StringBuilderUtils;
import com.google.android.accessibility.utils.compat.CompatUtils;
import com.google.android.accessibility.utils.traversal.TraversalStrategy;
import com.google.android.accessibility.utils.traversal.TraversalStrategyUtils;
import com.google.auto.value.AutoValue;
import com.google.errorprone.annotations.FormatMethod;
import com.google.errorprone.annotations.FormatString;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Map;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

/** Describes the path from root node to a given node. */
public final class NodePathDescription {

  private static final String LOG_TAG = "NodePath";
  private static final boolean DO_LOG = false;

  private static final double MAX_DIST = Float.MAX_VALUE;

  // Node and ancestors, ordered from leaf to root
  private final ArrayList<NodeDescription> nodeDescriptions;

  ////////////////////////////////////////////////////////////////////////////////////////////////
  // Construction methods

  private NodePathDescription() {
    nodeDescriptions = new ArrayList<>();
  }

  public NodePathDescription(@NonNull NodePathDescription original) {
    this.nodeDescriptions = new ArrayList<>();
    for (@NonNull NodeDescription n : original.nodeDescriptions) {
      this.nodeDescriptions.add(new NodeDescription(n));
    }
  }

  public static @NonNull NodePathDescription obtain(AccessibilityNodeInfoCompat node) {
    final NodePathDescription nodePath = new NodePathDescription();

    AccessibilityNodeInfoUtils.getSelfOrMatchingAncestor(
        node,
        new Filter<AccessibilityNodeInfoCompat>() {
          @Override
          public boolean accept(AccessibilityNodeInfoCompat node) {
            boolean isPathEnd = (nodePath.nodeDescriptions.isEmpty());
            nodePath.nodeDescriptions.add(new NodeDescription(node, isPathEnd));
            // Always return false to iterate until the root node.
            return false;
          }
        });
    return nodePath;
  }

  ////////////////////////////////////////////////////////////////////////////////////////////////
  // Methods for finding a node matching an old tree-path

  /** Finds node matching identity-path, or next node after, in traversal order. */
  public @Nullable AccessibilityNodeInfoCompat findNodeToRefocus(
      @Nullable AccessibilityNodeInfoCompat rootCompat, @NonNull FocusFinder focusFinder) {

    @Nullable AccessibilityNodeInfoCompat lastFocusedNode = null;
    if (!nodeDescriptions.isEmpty() && nodeDescriptions.get(0).savedNode != null) {
      lastFocusedNode = nodeDescriptions.get(0).savedNode.obtainCopyCompat();
    }

    @Nullable AccessibilityNode root = AccessibilityNode.takeOwnership(rootCompat);
    log("findNodeToRefocus() this=%s", this);
    log("findNodeToRefocus() root=%s", root);
    if (root == null) {
      return null;
    }

    @Nullable AccessibilityNode targetUp = matchAncestorUniqueId();
    if (targetUp != null && lastFocusedNode != null) {
      if (lastFocusedNode.refresh()
          && AccessibilityNodeInfoUtils.shouldFocusNode(lastFocusedNode)) {
        return lastFocusedNode;
      }
    }

    // If old node-tree still partially exists, find an existing path-ancestor, searching upward
    // from the last-focused-node.  This limits the scope of the downward tree-search needed to find
    // a node that matches the full node-path.
    if (targetUp == null) {
      targetUp = findUpward();
    }

    log("findNodeToRefocus() targetUp=%s", targetUp);
    // Ensure targetUp is not null.
    if (targetUp == null) {
      targetUp = root;
    }
    // Search down from root or old-ancestor-node, finding lowest new-node that matches the
    // old-focused-node.
    @Nullable AccessibilityNode targetDown = findDownward(rootCompat, focusFinder, targetUp);
    log("findNodeToRefocus() targetDown=%s", targetDown);

    return (targetDown == null) ? targetUp.obtainCopyCompat() : targetDown.obtainCopyCompat();
  }

  private @Nullable AccessibilityNode matchAncestorUniqueId() {
    for (int p = 1; p < nodeDescriptions.size() - 1; ++p) {
      NodeDescription pathParent = nodeDescriptions.get(p); // Parent of pathNode
      @Nullable AccessibilityNode lastParentNode =
          pathParent.savedNode == null
              ? null
              : AccessibilityNode.obtainCopy(pathParent.savedNode.getCompat());
      @Nullable AccessibilityNode parentNode = refreshOrNull(pathParent.savedNode);
      if (parentNode != null && lastParentNode != null) {
        @Nullable String lastUniqueId = lastParentNode.getUniqueId();
        if (lastUniqueId != null && lastUniqueId.equals(parentNode.getUniqueId())) {
          return parentNode;
        }
      }
    }
    return null;
  }

  /** Searches ancestors for a path-node that still exists, mostly unchanged. */
  private @Nullable AccessibilityNode findUpward() {
    // For each path-node, from leaf to root...
    for (int p = 0; p < nodeDescriptions.size() - 1; ++p) {
      NodeDescription pathNode = nodeDescriptions.get(p);
      NodeDescription pathParent = nodeDescriptions.get(p + 1); // Parent of pathNode
      boolean isPathEnd = (p == 0);
      log("findUpward() p=%s pathNode=%s", p, pathNode);

      // If node exists, with same content & identity... matched.
      @Nullable AccessibilityNode pathNodeUpdated = refreshOrNull(pathNode.savedNode);
      log("findUpward() p=%s pathNodeUpdated=%s", p, pathNodeUpdated);
      @Nullable AccessibilityNode parentUpdated = refreshOrNull(pathParent.savedNode);
      log("findUpward() p=%s parentUpdated=%s", p, parentUpdated);

      // Match if only 1 changed among {identity, index, content, adjacent-content}
      boolean identityMatch = identityMatches(pathNode, pathNodeUpdated);
      boolean contentMatch = contentMatches(pathNode, pathNodeUpdated, isPathEnd);
      boolean indexMatch = indexMatches(pathNode, pathNodeUpdated, parentUpdated);

      // Only run adjacentContentMatches() if necessary, for efficiency.
      @Nullable AccessibilityNode adjacentMatch = null;
      int numMatches = (identityMatch ? 1 : 0) + (indexMatch ? 1 : 0) + (contentMatch ? 1 : 0);
      if (numMatches < 3) {
        adjacentMatch = adjacentContentMatches(pathNode, pathParent, parentUpdated, isPathEnd);
        numMatches += (adjacentMatch == null ? 0 : 1);
      }
      log(
          "findUpward() p=%s identityMatch=%s contentMatch=%s indexMatch=%s adjacentMatch=%s",
          p, identityMatch, contentMatch, indexMatch, adjacentMatch);

      // If more than 1 changed match-type... keep looking upward for lowest matching ancestor.
      if (numMatches < 3) {
        continue;
      }

      // If ancestor matched... return ancestor.
      if (identityMatch && contentMatch && indexMatch) {
        return pathNodeUpdated;
      } else if (adjacentMatch != null) {
        return adjacentMatch;
      }
    }
    return null;
  }

  /**
   * The comparator of {@link Match}.
   *
   * <p>Priority:
   *
   * <ul>
   *   <li>Node-match data that closes to the end of the node-path.
   *   <li>Node-match data with a higher score that matches path-nodes.
   *   <li>Node-match data with a lower physical distance with path-nodes.
   * </ul>
   */
  private static final Comparator<Match> NODE_MATCH_COMPARATOR =
      comparingInt(Match::depth)
          .thenComparingDouble(Match::score)
          .thenComparing(Match::distance, Comparator.reverseOrder());

  /** Node-match data for downward pruned-tree-search for current-node that matches path-nodes. */
  @AutoValue
  abstract static class Match {
    /** Whether the node-match data should be pruned. Default is false. */
    abstract boolean prune();

    /** Whether the node-match data is at the end of path-nodes. Default is false. */
    abstract boolean isPathEnd();

    /**
     * The score of the node-match data matches path-nodes. It is the sum of adjacent-match,
     * index-match and content-match. Desfault is 0.
     */
    abstract double score();

    /** The depth that the node-match data matches the node path. Default is 0. */
    abstract int depth();

    /**
     * The distance between current-node and the path-node, using their bounds in screen
     * coordinates. Default is {@link #MAX_DIST}.
     */
    abstract double distance();

    @Nullable
    abstract AccessibilityNode node();

    static Builder builder() {
      return new AutoValue_NodePathDescription_Match.Builder()
          .setPrune(false)
          .setIsPathEnd(false)
          .setScore(0)
          .setDepth(0)
          .setDistance(MAX_DIST);
    }

    public boolean hasNode() {
      return (node() != null);
    }

    @Override
    public String toString() {
      return StringBuilderUtils.joinFields(
          StringBuilderUtils.optionalTag("prune", prune()),
          StringBuilderUtils.optionalTag("isPathEnd", isPathEnd()),
          StringBuilderUtils.optionalDouble("score", score(), 0),
          StringBuilderUtils.optionalInt("depth", depth(), 0),
          StringBuilderUtils.optionalDouble("distance", distance(), MAX_DIST),
          StringBuilderUtils.optionalSubObj("node", node()));
    }

    @AutoValue.Builder
    abstract static class Builder {
      abstract Builder setPrune(boolean value);

      abstract Builder setIsPathEnd(boolean value);

      abstract Builder setScore(double value);

      abstract Builder setDepth(int value);

      abstract Builder setDistance(double value);

      abstract Builder setNode(AccessibilityNode value);

      abstract Match build();
    }
  }

  /** Searches tree for current-node that matches path-nodes. */
  private @Nullable AccessibilityNode findDownward(
      @NonNull AccessibilityNodeInfoCompat root,
      @NonNull FocusFinder focusFinder,
      @NonNull AccessibilityNode startNode) {

    // No identity-match is likely in downward search, because matching identity would have matched
    // upward.
    // That leaves only content/adjacent/index-matches for downward match.
    // Inner-nodes like FrameLayout may trivially match the node-path, with null-values.
    // So search down multiple tree-branches, pruning any branches that do not match at all.

    if (startNode == null) {
      return null;
    }
    int startNodeDepth = startNode.findDepth();
    int startNodeIndexInParent = getRawIndexInParent(startNode);
    log(
        "findDownward() startNodeDepth=%s startNodeIndexInParent=%s",
        startNodeDepth, startNodeIndexInParent);
    log("findDownward() startNode=%s", startNode);

    HashSet<AccessibilityNode> visited = new HashSet<>();
    @Nullable Match bestMatch =
        findDownwardMatch(
            startNode,
            startNodeIndexInParent,
            startNodeDepth,
            /* previousSiblingText= */ null,
            /* nextSiblingText= */ null,
            visited);
    log("findDownward() visited.size()=%d bestMatch=%s", visited.size(), bestMatch);
    // TODO: Refactor focus scoring system for easier maintainability.
    // If no complete-path match exists, traverse forward from matching internal-node.
    if ((bestMatch == null) || !bestMatch.hasNode() || bestMatch.node().equalTo(root)) {
      return null;
    }
    return (bestMatch.isPathEnd())
        ? bestMatch.node()
        : nextInTraversalOrder(root, focusFinder, bestMatch.node());
  }

  /**
   * Returns matching node or null. Does recursive pruned depth-first-search on node-tree. Adds
   * nodes to visited.
   */
  private @Nullable Match findDownwardMatch(
      @NonNull AccessibilityNode node,
      int childIndex,
      int depth,
      @Nullable CharSequence previousSiblingText,
      @Nullable CharSequence nextSiblingText,
      HashSet<AccessibilityNode> visited) {

    log(depth, "");
    log(depth, "findDownward() depth=%d childIndex=%d node=%s", depth, childIndex, node);
    if (node == null) {
      return null;
    }
    DiagnosticOverlayUtils.appendLog(DiagnosticOverlayUtils.REFOCUS_PATH, node);

    // If node already checked... quit.
    if (visited.contains(node)) {
      return null;
    } else {
      visited.add(node);
    }

    // If node does not match... prune tree-branch.
    @NonNull
    Match match = scoreMatch(node, childIndex, depth, previousSiblingText, nextSiblingText);
    log(depth, "findDownward() match=%s", match);
    if (!match.hasNode() || match.prune()) {
      return null;
    }
    // If current node is path-end... do not recurse on children.
    boolean isPathEnd = (nodeDescriptions.size() - 1 <= depth);
    if (isPathEnd) {
      return match;
    }

    // Recurse on each child.
    @Nullable CharSequence previousChildText = null;
    @Nullable CharSequence childText = OUT_OF_RANGE;
    @Nullable AccessibilityNode child = null;
    boolean isChildPathEnd = (nodeDescriptions.size() - 1 <= depth + 1);
    boolean nodeIsViewPager = false;
    if (node.getRole() == Role.ROLE_PAGER) {
      log(depth, "findDownward() node is a view pager");
      nodeIsViewPager = true;
    }
    // For each child...
    for (int nextChildIndex = 0; nextChildIndex <= node.getChildCount(); ++nextChildIndex) {
      @Nullable AccessibilityNode nextChild =
          (nextChildIndex < node.getChildCount()) ? node.getChild(nextChildIndex) : null;
      @Nullable CharSequence nextChildText =
          (nextChild == null) ? OUT_OF_RANGE : NodeDescription.getText(nextChild, isChildPathEnd);

      CollectionItemInfoCompat itemInfoCompat =
          (nextChild == null) ? null : nextChild.getCollectionItemInfo();
      // When the parent node is a view pager, the child index should be replaced with item info's
      // column index.
      int realIndex =
          nodeIsViewPager
              ? (itemInfoCompat != null) ? itemInfoCompat.getColumnIndex() : nextChildIndex
              : nextChildIndex;
      if (child != null) {
        // Recursively search within child-subtree.
        @Nullable Match descendantMatch =
            findDownwardMatch(
                child, realIndex - 1, depth + 1, previousChildText, nextChildText, visited);
        // Any lower/descendant-match is better than current higher inner-node-match.
        if ((descendantMatch != null)
            && descendantMatch.hasNode()
            && (NODE_MATCH_COMPARATOR.compare(descendantMatch, match) > 0)
            && (descendantMatch.node().isVisibleToUser())) {
          match = descendantMatch;
        }
      }

      // Shift nextChild -> child -> previousChild.
      previousChildText = childText;
      childText = nextChildText;
      child = nextChild;
    }

    // Return best match found among current node and descendants.
    return match;
  }

  // Returns match-score, with node-field only set for non-pruned nodes.
  private @NonNull Match scoreMatch(
      @NonNull AccessibilityNode node,
      int index,
      int depth,
      @Nullable CharSequence previousSiblingText,
      @Nullable CharSequence nextSiblingText) {
    Match.Builder match = Match.builder().setDepth(depth);
    boolean isRoot = (depth == 0);

    // Always allow root-node, do not prune.
    if (isRoot) {
      log(depth, "scoreMatch() isRoot=%s", isRoot);
      return match.setNode(node).build();
    }

    // Prune if out of path.
    int pathIndex = nodeDescriptions.size() - depth - 1;
    if (pathIndex < 0) {
      log(depth, "scoreMatch() pathIndex=%d", pathIndex);
      return match.setPrune(true).build();
    }

    boolean isPathEnd = (pathIndex == 0);
    match.setIsPathEnd(isPathEnd);
    NodeDescription pathNode = nodeDescriptions.get(pathIndex);
    log(depth, "scoreMatch() isPathEnd=%s pathNode=%s", isPathEnd, pathNode);

    log(depth, "scoreMatch() previousSiblingText=%s", previousSiblingText);
    log(depth, "scoreMatch() nextSiblingText=%s", nextSiblingText);
    boolean adjacentMatch =
        isTextMatchingNonEmptyText(previousSiblingText, pathNode.previousSiblingText)
            || isTextMatchingNonEmptyText(nextSiblingText, pathNode.nextSiblingText);

    boolean contentMatch = contentMatches(pathNode, node, isPathEnd);
    boolean indexMatch = indexesMatch(pathNode, node, index);
    log(
        depth,
        "scoreMatch() adjacentMatch=%s contentMatch=%s indexMatch=%s",
        adjacentMatch,
        contentMatch,
        indexMatch);
    // Match-type priority:  adjacent < index < identity < content-match
    // Identity-match is not used findDownward(), because findUpward() would already have found an
    // identity-matching node, if it existed.
    double score = (contentMatch ? 1.2 : 0) + (indexMatch ? 1.1 : 0) + (adjacentMatch ? 1.0 : 0);
    log(depth, "scoreMatch() score=%s", score);
    match.setScore(score);
    // Prune ancestors without even weak match. (Match for index(1.1) or adjacent(1.0))
    if (score <= 1.1) {
      return match.setPrune(true).build();
    }

    match.setNode(node);
    if (pathNode.savedNode != null) {
      double distance = getDistanceBetweenNodes(pathNode.savedNode, node);
      match.setDistance(distance);
      log(depth, "scoreMatch() distance=%s", distance);
    }

    return match.build();
  }

  private static boolean isTextMatchingNonEmptyText(CharSequence text, CharSequence targetText) {
    if (TextUtils.isEmpty(text) || TextUtils.equals(text, OUT_OF_RANGE)) {
      return false;
    }

    return TextUtils.equals(text, targetText);
  }

  /**
   * Returns the distance between two {@link AccessibilityNode}, using their bounds in screen
   * coordinates.
   */
  private static double getDistanceBetweenNodes(AccessibilityNode node1, AccessibilityNode node2) {
    Rect rect1 = new Rect();
    node1.getBoundsInScreen(rect1);
    Rect rect2 = new Rect();
    node2.getBoundsInScreen(rect2);
    int xd = (int) Math.pow(rect2.centerX() - rect1.centerX(), 2);
    int yd = (int) Math.pow(rect2.centerY() - rect1.centerY(), 2);
    return Math.sqrt(xd + yd);
  }

  private @Nullable AccessibilityNode nextInTraversalOrder(
      @NonNull AccessibilityNodeInfoCompat root,
      @NonNull FocusFinder focusFinder,
      @NonNull AccessibilityNode node) {
    TraversalStrategy traversal =
        TraversalStrategyUtils.getTraversalStrategy(root, focusFinder, SEARCH_FOCUS_FORWARD);
    @Nullable AccessibilityNodeInfoCompat nodeCompat = node.obtainCopyCompat();
    final @Nullable Map<AccessibilityNodeInfoCompat, Boolean> speakingNodes =
        traversal.getSpeakingNodesCache();

    return AccessibilityNode.takeOwnership(
        TraversalStrategyUtils.searchFocus(
            traversal,
            nodeCompat,
            SEARCH_FOCUS_FORWARD,
            Filter.node(n -> AccessibilityNodeInfoUtils.shouldFocusNode(n, speakingNodes))));
  }

  ////////////////////////////////////////////////////////////////////////////////////////////////
  // Methods for searching children of a parent-node

  private static @Nullable AccessibilityNode findChildBetweenText(
      @Nullable CharSequence targetPrevChildText,
      @Nullable CharSequence targetNextChildText,
      @Nullable AccessibilityNode parent,
      boolean isPathEnd) {
    if ((parent == null)
        || (TextUtils.isEmpty(targetPrevChildText) && TextUtils.isEmpty(targetNextChildText))) {
      return null;
    }
    return findChild(
        parent,
        isPathEnd,
        (child, index, previousChildText, nextChildText) ->
            TextUtils.equals(previousChildText, targetPrevChildText)
                && TextUtils.equals(nextChildText, targetNextChildText));
  }

  private interface ChildCheck {
    boolean accept(
        @NonNull AccessibilityNode child,
        int indexInParent,
        @Nullable CharSequence previousSiblingText,
        @Nullable CharSequence nextSiblingText);
  }

  private static @Nullable AccessibilityNode findChild(
      @Nullable AccessibilityNode parent, boolean isPathEnd, ChildCheck target) {
    if (parent == null) {
      return null;
    }

    @Nullable CharSequence previousChildText = null;
    @Nullable CharSequence childText = OUT_OF_RANGE;
    @Nullable AccessibilityNode child = null;
    for (int c = 0; c <= parent.getChildCount(); ++c) {
      @Nullable AccessibilityNode nextChild =
          (c < parent.getChildCount()) ? parent.getChild(c) : null;
      @Nullable CharSequence nextChildText =
          (nextChild == null) ? OUT_OF_RANGE : NodeDescription.getText(nextChild, isPathEnd);
      // If previous/next-child-texts match... return child.
      if ((child != null) && target.accept(child, c - 1, previousChildText, nextChildText)) {
        // Return found-child.
        return child;
      }
      // Shift nextChild -> child -> previousChild
      previousChildText = childText;
      childText = nextChildText;
      child = nextChild;
      nextChild = null;
    }
    return null;
  }

  private static int getRawIndexInParent(@NonNull AccessibilityNode node) {
    return NodeDescription.getRawIndexInParent(node, node.getParent());
  }

  ////////////////////////////////////////////////////////////////////////////////////////////////
  // Methods for matching fields

  private static boolean identityMatches(
      NodeDescription node, @Nullable AccessibilityNode nodeUpdated) {
    return (nodeUpdated != null) && (node.nodeInfoHashCode == nodeUpdated.hashCode());
  }

  private static boolean contentMatches(
      NodeDescription node, @Nullable AccessibilityNode nodeUpdated, boolean isPathEnd) {
    if (nodeUpdated == null) {
      return false;
    }
    @Nullable CharSequence nodeText = NodeDescription.getText(nodeUpdated, isPathEnd);

    if (isPathEnd && TextUtils.isEmpty(node.text)) {
      return false;
    }

    if (!TextUtils.equals(node.className, nodeUpdated.getClassName())) {
      return false;
    }

    return TextUtils.equals(node.text, nodeText);
  }

  private static @Nullable AccessibilityNode adjacentContentMatches(
      NodeDescription node,
      NodeDescription parent,
      @Nullable AccessibilityNode parentUpdated,
      boolean isPathEnd) {
    if (parentUpdated == null) {
      return null;
    }
    // Parent-text does not include descendant-text, because only leaves include descendant-text.
    if (!TextUtils.equals(parent.text, NodeDescription.getText(parentUpdated, isPathEnd))) {
      return null;
    }
    return findChildBetweenText(
        node.previousSiblingText, node.nextSiblingText, parentUpdated, isPathEnd);
  }

  /** Checks whether refreshed NodeDescription.node indices match updated node. */
  private static boolean indexMatches(
      @NonNull NodeDescription node,
      @Nullable AccessibilityNode nodeUpdated,
      @Nullable AccessibilityNode parentUpdated) {

    // Match row/column-index.
    if (node.hasCollectionIndex()) {
      return node.matchesCollectionIndices(nodeUpdated);
    }

    // Match raw-index.
    // If updated child-index is unknown... saved-path must match unknown-index.
    if ((parentUpdated == null) || (parentUpdated.getChildCount() <= node.rawIndexInParent)) {
      return (node.rawIndexInParent == UNDEFINED_INDEX);
    }
    if ((node.rawIndexInParent == UNDEFINED_INDEX) || (node.savedNode == null)) {
      return false;
    }
    @Nullable AccessibilityNode childAtIndexUpdated = parentUpdated.getChild(node.rawIndexInParent);
    return node.savedNode.equals(childAtIndexUpdated);
  }

  private static boolean indexesMatch(
      @NonNull NodeDescription pathNode,
      @NonNull AccessibilityNode searchNode,
      int searchNodeIndex) {
    boolean rawIndexMatches = (pathNode.rawIndexInParent == searchNodeIndex);
    return pathNode.hasCollectionIndex()
        ? pathNode.matchesCollectionIndices(searchNode)
        : rawIndexMatches;
  }

  /**
   * Returns true if we can find a node in path with the same hash code and identity information of
   * {@code target}.
   */
  public boolean containsNodeByHashAndIdentity(AccessibilityNodeInfoCompat target) {
    if (target == null) {
      return false;
    }
    int hashCode = target.hashCode();
    String targetUniqueId =
        (String)
            CompatUtils.invoke(
                target.unwrap(),
                /* defaultValue= */ null,
                CompatUtils.getMethod(AccessibilityNodeInfo.class, "getUniqueId"));

    // Compare from root to leaf, because root node is more immutable.
    for (NodeDescription description : nodeDescriptions) {
      if (description.savedNode != null && targetUniqueId != null) {
        AccessibilityNodeInfoCompat accessibilityNodeInfoCompat =
            description.savedNode.obtainCopyCompat();
        if (accessibilityNodeInfoCompat != null) {
          String uniQueueId =
              (String)
                  CompatUtils.invoke(
                      accessibilityNodeInfoCompat.unwrap(),
                      /* defaultValue= */ null,
                      CompatUtils.getMethod(AccessibilityNodeInfo.class, "getUniqueId"));
          if (uniQueueId != null && uniQueueId.equals(targetUniqueId)) {
            return true;
          }
        }
      }
      if ((description.nodeInfoHashCode == hashCode) && description.identityMatches(target)) {
        return true;
      }
    }

    return false;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    NodePathDescription that = (NodePathDescription) o;
    return nodeDescriptions.equals(that.nodeDescriptions);
  }

  @Override
  public int hashCode() {
    return nodeDescriptions.hashCode();
  }

  // Returns refreshed node, or null.
  private static @Nullable AccessibilityNode refreshOrNull(@Nullable AccessibilityNode node) {
    return (node != null) && node.refresh() ? node : null;
  }

  ////////////////////////////////////////////////////////////////////////////////////////////////
  // Methods for logging

  @FormatMethod
  private static void log(@FormatString String format, Object... arguments) {
    log(/* depth= */ 0, format, arguments);
  }

  @FormatMethod
  private static void log(int depth, @FormatString String format, Object... arguments) {
    if (DO_LOG) {
      LogDepth.log(LOG_TAG, depth, format, arguments);
    }
  }

  @Override
  public String toString() {
    StringBuilder text = new StringBuilder();
    text.append("NodePathDescription:[");
    for (int n = nodeDescriptions.size() - 1; 0 <= n; --n) {
      text.append("\n\t");
      text.append(n);
      text.append(": ");
      text.append(nodeDescriptions.get(n));
    }
    text.append("]");
    return text.toString();
  }
}
