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

import android.text.TextUtils;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import com.google.android.accessibility.utils.AccessibilityNode;
import com.google.android.accessibility.utils.AccessibilityNodeInfoUtils;
import com.google.android.accessibility.utils.DiagnosticOverlayUtils;
import com.google.android.accessibility.utils.Filter;
import com.google.android.accessibility.utils.FocusFinder;
import com.google.android.accessibility.utils.LogDepth;
import com.google.android.accessibility.utils.StringBuilderUtils;
import com.google.android.accessibility.utils.traversal.TraversalStrategy;
import com.google.android.accessibility.utils.traversal.TraversalStrategyUtils;
import com.google.errorprone.annotations.FormatMethod;
import com.google.errorprone.annotations.FormatString;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Map;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

/** Describes the path from root node to a given node. */
public final class NodePathDescription {

  private static final String LOG_TAG = "NodePath";
  private static final boolean DO_LOG = false;

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
            boolean isPathEnd = (nodePath.nodeDescriptions.size() == 0);
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

    @Nullable AccessibilityNode root = AccessibilityNode.obtainCopy(rootCompat);
    log("findNodeToRefocus() this=%s", this);
    log("findNodeToRefocus() root=%s", root);
    if (root == null) {
      return null;
    }

    // If old node-tree still partially exists, find an existing path-ancestor, searching upward
    // from the last-focused-node.  This limits the scope of the downward tree-search needed to find
    // a node that matches the full node-path.
    @Nullable AccessibilityNode targetUp = findUpward();
    log("findNodeToRefocus() targetUp=%s", targetUp);
    // Ensure targetUp is not null.
    if (targetUp == null) {
      targetUp = root.obtainCopy();
    }
    // Search down from root or old-ancestor-node, finding lowest new-node that matches the
    // old-focused-node.
    @Nullable AccessibilityNode targetDown = findDownward(rootCompat, focusFinder, targetUp);
    log("findNodeToRefocus() targetDown=%s", targetDown);

    return (targetDown == null) ? targetUp.obtainCopyCompat() : targetDown.obtainCopyCompat();
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
      @Nullable AccessibilityNode pathNodeUpdated = copyAndRefresh(pathNode.savedNode);
      log("findUpward() p=%s pathNodeUpdated=%s", p, pathNodeUpdated);
      @Nullable AccessibilityNode parentUpdated = copyAndRefresh(pathParent.savedNode);
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
        return pathNodeUpdated.obtainCopy();
      } else if (adjacentMatch != null) {
        return adjacentMatch.obtainCopy();
      }
    }
    return null;
  }

  /** Node-match data for downward pruned-tree-search for current-node that matches path-nodes. */
  private static class Match {
    // Member data
    public boolean prune = false;
    public boolean isPathEnd = false;
    public float score = 0;
    public int depth = 0;
    private @Nullable AccessibilityNode node;

    // Methods for node
    public boolean isNull() {
      return (this.node == null);
    }

    public Match node(@Nullable AccessibilityNode newNode) {
      this.node = newNode;
      return this;
    }

    public @Nullable AccessibilityNode giveUpNode() {
      @Nullable AccessibilityNode result = this.node;
      this.node = null;
      return result;
    }

    // Builder methods, for convenient set & return
    public Match prune(boolean prune) {
      this.prune = prune;
      return this;
    }

    public Match score(float score) {
      this.score = score;
      return this;
    }

    // Logging methods
    public String toString() {
      return StringBuilderUtils.joinFields(
          StringBuilderUtils.optionalTag("prune", prune),
          StringBuilderUtils.optionalTag("isPathEnd", isPathEnd),
          StringBuilderUtils.optionalNum("score", score, 0),
          StringBuilderUtils.optionalInt("depth", depth, 0),
          StringBuilderUtils.optionalSubObj("node", node));
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
        findDownward(
            startNode,
            startNodeIndexInParent,
            startNodeDepth,
            /* previousSiblingText= */ null,
            /* nextSiblingText= */ null,
            visited);
    log("findDownward() visited.size()=%d", visited.size());
    // TODO: Refactor focus scoring system for easier maintainability.
    // If no complete-path match exists, or only match for index(1.1f) or adjacent(1.0f), traverse
    // forward from matching internal-node.
    if ((bestMatch == null) || bestMatch.isNull() || bestMatch.score <= 1.1f) {
      return null;
    }
    return (bestMatch.isPathEnd)
        ? bestMatch.giveUpNode()
        : nextInTraversalOrder(root, focusFinder, bestMatch.node);
  }

  /**
   * Returns matching node or null. Does recursive pruned depth-first-search on node-tree. Adds
   * nodes to visited.
   */
  private @Nullable Match findDownward(
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
      visited.add(node.obtainCopy());
    }

    // If node does not match... prune tree-branch.
    @NonNull
    Match match = scoreMatch(node, childIndex, depth, previousSiblingText, nextSiblingText);
    log(depth, "findDownward() match=%s", match);
    if (match.isNull() || match.prune) {
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
    // For each child...
    for (int nextChildIndex = 0; nextChildIndex <= node.getChildCount(); ++nextChildIndex) {
      @Nullable AccessibilityNode nextChild =
          (nextChildIndex < node.getChildCount()) ? node.getChild(nextChildIndex) : null;
      @Nullable CharSequence nextChildText =
          (nextChild == null) ? OUT_OF_RANGE : NodeDescription.getText(nextChild, isChildPathEnd);

      if (child != null) {
        // Recursively search within child-subtree.
        @Nullable Match descendantMatch = null;
        descendantMatch =
            findDownward(
                child, nextChildIndex - 1, depth + 1, previousChildText, nextChildText, visited);
        // Any lower/descendant-match is better than current higher inner-node-match.
        if ((descendantMatch != null)
            && !descendantMatch.isNull()
            && ((match.score < descendantMatch.score) || (match.depth < descendantMatch.depth))) {
          match = descendantMatch;
          descendantMatch = null;
        }
      }

      // Shift nextChild -> child -> previousChild.
      previousChildText = childText;
      childText = nextChildText;
      child = nextChild;
      nextChild = null;
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
    Match match = new Match();
    match.depth = depth;
    boolean isRoot = (depth == 0);

    // Always allow root-node, do not prune.
    if (isRoot) {
      log(depth, "scoreMatch() isRoot=%s", isRoot);
      return match.node(node.obtainCopy());
    }

    // Prune if out of path.
    int pathIndex = nodeDescriptions.size() - depth - 1;
    if (pathIndex < 0) {
      log(depth, "scoreMatch() pathIndex=%d", pathIndex);
      return match.prune(true);
    }

    boolean isPathEnd = (pathIndex == 0);
    match.isPathEnd = isPathEnd;
    @Nullable NodeDescription pathNode = nodeDescriptions.get(pathIndex);
    log(depth, "scoreMatch() isPathEnd=%s pathNode=%s", isPathEnd, pathNode);

    // Prune if out of path.
    if (pathNode == null) {
      return match.prune(true);
    }

    log(depth, "scoreMatch() previousSiblingText=%s", previousSiblingText);
    log(depth, "scoreMatch() nextSiblingText=%s", nextSiblingText);
    boolean adjacentMatch =
        TextUtils.equals(previousSiblingText, pathNode.previousSiblingText)
            || TextUtils.equals(nextSiblingText, pathNode.nextSiblingText);

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
    float score = (contentMatch ? 1.2f : 0) + (indexMatch ? 1.1f : 0) + (adjacentMatch ? 1.0f : 0);
    log(depth, "scoreMatch() score=%s", score);
    // Prune ancestors without even weak match.
    match.score(score);
    match.prune(score < 1.0f);
    return match.prune ? match : match.node(node.obtainCopy()); // Copy non-pruned node.
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
            new Filter.NodeCompat(
                n -> AccessibilityNodeInfoUtils.shouldFocusNode(n, speakingNodes))));
  }

  ////////////////////////////////////////////////////////////////////////////////////////////////
  // Methods for searching children of a parent-node

  private static @Nullable AccessibilityNode findChildBetweenText(
      @Nullable CharSequence targetPrevChildText,
      @Nullable CharSequence targetNextChildText,
      @Nullable AccessibilityNode parent,
      boolean isPathEnd) {
    if (parent == null) {
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

    if (isPathEnd && (node.text == null)) {
      return false;
    }
    return TextUtils.equals(node.text, nodeText)
        && TextUtils.equals(node.className, nodeUpdated.getClassName());
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
    // Compare from root to leaf, because root node is more immutable.
    for (NodeDescription description : nodeDescriptions) {
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

  // Returns copied & refreshed node.
  private static @Nullable AccessibilityNode copyAndRefresh(@Nullable AccessibilityNode node) {
    if (node == null) {
      return null;
    }
    @Nullable AccessibilityNode copy = node.obtainCopy();
    if (copy == null) {
      return null;
    }
    if (!copy.refresh()) {
      return null;
    }
    return copy;
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
