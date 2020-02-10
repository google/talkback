/*
 * Copyright (C) 2016 The Android Open Source Project
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

import android.os.Build;
import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat.CollectionInfoCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat.CollectionItemInfoCompat;
import android.util.SparseArray;
import android.view.accessibility.AccessibilityEvent;
import com.google.android.accessibility.utils.Role.RoleName;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.HashSet;
import java.util.Set;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Manages the contextual collection state when the user is navigating between elements or touch
 * exploring. This class implements a state machine for determining what transition feedback should
 * be provided for collections.
 */
public class CollectionState {

  /** The possible collection transitions that can occur when moving from node to node. */
  @Retention(RetentionPolicy.SOURCE)
  @IntDef({NAVIGATE_NONE, NAVIGATE_ENTER, NAVIGATE_EXIT, NAVIGATE_INTERIOR})
  public @interface CollectionTransition {}

  /** Bitmask used when we need to identify a row transition, column transition, or both. */
  @Retention(RetentionPolicy.SOURCE)
  @IntDef(
    flag = true,
    value = {TYPE_NONE, TYPE_ROW, TYPE_COLUMN}
  )
  public @interface RowColumnTransition {}

  /** The possible heading types for a table heading. */
  @Retention(RetentionPolicy.SOURCE)
  @IntDef({TYPE_NONE, TYPE_ROW, TYPE_COLUMN, TYPE_INDETERMINATE})
  public @interface TableHeadingType {}

  /** Whether the collection is horizontal or vertical. A square collection is vertical. */
  @Retention(RetentionPolicy.SOURCE)
  @IntDef({ALIGNMENT_VERTICAL, ALIGNMENT_HORIZONTAL})
  public @interface CollectionAlignment {}

  /** Transition to a node outside any collection from a node also outside any collection. */
  public static final int NAVIGATE_NONE = 0;
  /** Transition to a node inside a collection from a node that is not in that collection. */
  public static final int NAVIGATE_ENTER = 1;
  /** Transition to a node outside any collection from a node that is within a collection. */
  public static final int NAVIGATE_EXIT = 2;
  /** Transition between two nodes in the same collection. */
  public static final int NAVIGATE_INTERIOR = 3;

  public static final int TYPE_NONE = 0;
  public static final int TYPE_ROW = 1 << 0;
  public static final int TYPE_COLUMN = 1 << 1;
  public static final int TYPE_INDETERMINATE = 1 << 2;
  public static final int ALIGNMENT_VERTICAL = 0;
  public static final int ALIGNMENT_HORIZONTAL = 1;

  static final String EVENT_ROW = "AccessibilityNodeInfo.CollectionItemInfo.rowIndex";
  static final String EVENT_COLUMN = "AccessibilityNodeInfo.CollectionItemInfo.columnIndex";
  static final String EVENT_HEADING = "AccessibilityNodeInfo.CollectionItemInfo.heading";

  private @CollectionTransition int mCollectionTransition = NAVIGATE_NONE;
  private @RowColumnTransition int mRowColumnTransition = TYPE_NONE;
  @Nullable private AccessibilityNodeInfoCompat mCollectionRoot;
  @Nullable private AccessibilityNodeInfoCompat mLastAnnouncedNode;
  @Nullable private ItemState mItemState;
  private SparseArray<CharSequence> mRowHeaders = new SparseArray<>();
  private SparseArray<CharSequence> mColumnHeaders = new SparseArray<>();
  private int mCollectionLevel = -1;
  private boolean mShouldComputeHeaders = false;
  private boolean mShouldComputeNumbering = false;

  private static final Filter<AccessibilityNodeInfoCompat> FILTER_COLLECTION =
      new Filter<AccessibilityNodeInfoCompat>() {
        @Override
        public boolean accept(AccessibilityNodeInfoCompat node) {
          int role = Role.getRole(node);
          return (role == Role.ROLE_LIST) || (role == Role.ROLE_GRID) || (role == Role.ROLE_PAGER);
        }
      };

  private static final Filter<AccessibilityNodeInfoCompat> FILTER_HIERARCHICAL_COLLECTION =
      new Filter<AccessibilityNodeInfoCompat>() {
        @Override
        public boolean accept(AccessibilityNodeInfoCompat node) {
          return FILTER_COLLECTION.accept(node)
              && node.getCollectionInfo() != null
              && node.getCollectionInfo().isHierarchical();
        }
      };

  private static final Filter<AccessibilityNodeInfoCompat> FILTER_FLAT_COLLECTION =
      new Filter<AccessibilityNodeInfoCompat>() {
        @Override
        public boolean accept(AccessibilityNodeInfoCompat node) {
          return FILTER_COLLECTION.accept(node)
              && (node.getCollectionInfo() == null || !node.getCollectionInfo().isHierarchical());
        }
      };

  private static final Filter<AccessibilityNodeInfoCompat> FILTER_COLLECTION_ITEM =
      new Filter<AccessibilityNodeInfoCompat>() {
        @Override
        public boolean accept(AccessibilityNodeInfoCompat node) {
          return node != null && node.getCollectionItemInfo() != null;
        }
      };

  private static final Filter<AccessibilityNodeInfoCompat> FILTER_WEBVIEW =
      new Filter<AccessibilityNodeInfoCompat>() {
        @Override
        public boolean accept(AccessibilityNodeInfoCompat node) {
          return node != null && Role.getRole(node) == Role.ROLE_WEB_VIEW;
        }
      };

  /**
   * Base interface for internal collection item state. It should be kept private because clients of
   * the CollectionState should not need polymorphism for ListItemState/TableItemState. On the other
   * hand, polymorphic behavior is quite useful for simplifying some of our internal logic.
   */
  private interface ItemState {
    /**
     * @return The row-column transition from the {@code from} state to the current state. If the
     *     {@code from} state and the current state are of incompatible types, should return {@code
     *     TYPE_ROW | TYPE_COLUMN}.
     */
    public @RowColumnTransition int getTransition(@NonNull ItemState from);
  }

  public static class ListItemState implements ItemState {
    /** Whether the list item is a heading. */
    private final boolean mHeading;
    /** The index of the list item. */
    private final int mIndex;
    /** Whether the index should be displayed; used to work around a framework bug pre-N. */
    private final boolean mDisplayIndex;

    public ListItemState(boolean heading, int index, boolean displayIndex) {
      mHeading = heading;
      mIndex = index;
      mDisplayIndex = displayIndex;
    }

    @Override
    public @RowColumnTransition int getTransition(@NonNull ItemState from) {
      if (!(from instanceof ListItemState)) {
        return TYPE_ROW | TYPE_COLUMN;
      }

      ListItemState otherListItemState = (ListItemState) from;
      if (mIndex != otherListItemState.mIndex) {
        return TYPE_ROW | TYPE_COLUMN;
      }

      return TYPE_NONE;
    }

    public boolean isHeading() {
      return mHeading;
    }

    public int getIndex() {
      if (mDisplayIndex) {
        return mIndex;
      } else {
        return -1;
      }
    }
  }

  /** A holder for current page info for when the user transitions between collection states */
  public static class PagerItemState implements ItemState {
    private final boolean heading;
    private final int rowIndex;
    private final int columnIndex;

    /**
     * Constructs a PagerItemState, which holds the current page info when transitioning between
     * collection states.
     */
    public PagerItemState(boolean heading, int rowIndex, int columnIndex) {
      this.heading = heading;
      this.rowIndex = rowIndex;
      this.columnIndex = columnIndex;
    }

    @Override
    @RowColumnTransition
    public int getTransition(@NonNull ItemState other) {
      if (!(other instanceof PagerItemState)) {
        return TYPE_ROW | TYPE_COLUMN;
      }

      PagerItemState otherPagerItemState = (PagerItemState) other;
      int transition = TYPE_NONE;
      if (rowIndex != otherPagerItemState.rowIndex) {
        transition |= TYPE_ROW;
      }
      if (columnIndex != otherPagerItemState.columnIndex) {
        transition |= TYPE_COLUMN;
      }

      return transition;
    }

    /** Returns {@code true} if this item represents a heading page. */
    public boolean isHeading() {
      return heading;
    }

    /** Returns the row index of the item in a grid or vertical list pager. */
    public int getRowIndex() {
      return rowIndex;
    }

    /** Returns the column index of the item in a grid or horizontal list pager. */
    public int getColumnIndex() {
      return columnIndex;
    }
  }

  public static class TableItemState implements ItemState {
    /** Indicates whether the table cell is a row, column, or indeterminate heading. */
    private final @TableHeadingType int mHeading;
    /** The row name, or {@code null} if the row is unnamed. */
    @Nullable private final CharSequence mRowName;
    /** The column name, or {@code null} if the column is unnamed. */
    @Nullable private final CharSequence mColumnName;
    /** The row index. */
    private final int mRowIndex;
    /** The column index. */
    private final int mColumnIndex;
    /** Whether the indices should be displayed; used to work around a framework bug pre-N. */
    private final boolean mDisplayIndices;

    public TableItemState(
        @TableHeadingType int heading,
        @Nullable CharSequence rowName,
        @Nullable CharSequence columnName,
        int rowIndex,
        int columnIndex,
        boolean displayIndices) {
      mHeading = heading;
      mRowName = rowName;
      mColumnName = columnName;
      mRowIndex = rowIndex;
      mColumnIndex = columnIndex;
      mDisplayIndices = displayIndices;
    }

    @Override
    public @RowColumnTransition int getTransition(@NonNull ItemState other) {
      if (!(other instanceof TableItemState)) {
        return TYPE_ROW | TYPE_COLUMN;
      }

      TableItemState otherTableItemState = (TableItemState) other;
      int transition = TYPE_NONE;
      if (mRowIndex != otherTableItemState.mRowIndex) {
        transition |= TYPE_ROW;
      }
      if (mColumnIndex != otherTableItemState.mColumnIndex) {
        transition |= TYPE_COLUMN;
      }

      return transition;
    }

    public @TableHeadingType int getHeadingType() {
      return mHeading;
    }

    public @Nullable CharSequence getRowName() {
      return mRowName;
    }

    public @Nullable CharSequence getColumnName() {
      return mColumnName;
    }

    public int getRowIndex() {
      if (mDisplayIndices) {
        return mRowIndex;
      } else {
        return -1;
      }
    }

    public int getColumnIndex() {
      if (mDisplayIndices) {
        return mColumnIndex;
      } else {
        return -1;
      }
    }
  }

  public CollectionState() {}

  public @CollectionTransition int getCollectionTransition() {
    return mCollectionTransition;
  }

  public @RowColumnTransition int getRowColumnTransition() {
    return mRowColumnTransition;
  }

  public @Nullable CharSequence getCollectionName() {
    return AccessibilityNodeInfoUtils.getNodeText(mCollectionRoot);
  }

  /**
   * @return Either {@link com.google.android.accessibility.utils.Role#ROLE_LIST} or {@link
   *     com.google.android.accessibility.utils.Role#ROLE_GRID} if there is a collection, or {@link
   *     com.google.android.accessibility.utils.Role#ROLE_NONE} if there isn't one.
   */
  public @Role.RoleName int getCollectionRole() {
    return Role.getRole(mCollectionRoot);
  }

  public int getCollectionRowCount() {
    if (mCollectionRoot == null
        || mCollectionRoot.getCollectionInfo() == null
        || !mShouldComputeNumbering) {
      return -1;
    }

    return mCollectionRoot.getCollectionInfo().getRowCount();
  }

  public int getCollectionColumnCount() {
    if (mCollectionRoot == null
        || mCollectionRoot.getCollectionInfo() == null
        || !mShouldComputeNumbering) {
      return -1;
    }

    return mCollectionRoot.getCollectionInfo().getColumnCount();
  }

  public @CollectionAlignment int getCollectionAlignment() {
    if (mCollectionRoot == null || !mShouldComputeNumbering) {
      return ALIGNMENT_VERTICAL;
    } else {
      return getCollectionAlignmentInternal(mCollectionRoot.getCollectionInfo());
    }
  }

  public static @CollectionAlignment int getCollectionAlignmentInternal(
      @Nullable CollectionInfoCompat collection) {
    if (collection == null || collection.getRowCount() >= collection.getColumnCount()) {
      return ALIGNMENT_VERTICAL;
    } else {
      return ALIGNMENT_HORIZONTAL;
    }
  }

  public boolean doesCollectionExist() {
    if (mCollectionRoot == null) {
      return false;
    }

    // If collection can be refresh successfully, it still exists.
    return mCollectionRoot.refresh();
  }

  /**
   * Guaranteed to return a non-{@code null} ListItemState if {@link #getRowColumnTransition()} is
   * not {@link #TYPE_NONE} and {@link #getCollectionRole()} is {@link
   * com.google.android.accessibility.utils.Role#ROLE_LIST}.
   */
  @Nullable
  public ListItemState getListItemState() {
    if (mItemState != null && mItemState instanceof ListItemState) {
      return (ListItemState) mItemState;
    }

    return null;
  }

  @Nullable
  private static ListItemState getListItemState(
      AccessibilityNodeInfoCompat collectionRoot,
      AccessibilityNodeInfoCompat announcedNode,
      boolean computeNumbering) {
    if (collectionRoot == null || collectionRoot.getCollectionInfo() == null) {
      return null;
    }

    // Checking the ancestors should incur zero performance penalty in the typical case
    // where list items are direct descendants. Assuming list items are not deeply
    // nested, any performance penalty would be minimal.
    AccessibilityNodeInfoCompat collectionItem =
        AccessibilityNodeInfoUtils.getSelfOrMatchingAncestor(
            announcedNode, collectionRoot, FILTER_COLLECTION_ITEM);

    if (collectionItem == null) {
      return null;
    }

    CollectionInfoCompat collection = collectionRoot.getCollectionInfo();
    CollectionItemInfoCompat item = collectionItem.getCollectionItemInfo();

    boolean heading = AccessibilityNodeInfoUtils.isHeading(collectionItem);
    int index;
    if (getCollectionAlignmentInternal(collection) == ALIGNMENT_VERTICAL) {
      index = getRowIndex(item, collection);
    } else {
      index = getColumnIndex(item, collection);
    }

    collectionItem.recycle();
    return new ListItemState(heading, index, computeNumbering);
  }

  /**
   * Returns a non-{@code null} PagerItemState if {@link #getRowColumnTransition()} is not {@link
   * #TYPE_NONE} and {@link #getCollectionRole()} is {@link
   * com.google.android.accessibility.utils.Role#ROLE_PAGER}.
   */
  @Nullable
  public PagerItemState getPagerItemState() {
    if (mItemState instanceof PagerItemState) {
      return (PagerItemState) mItemState;
    }
    return null;
  }

  /**
   * Returns a non-{@code null} PagerItemState if {@code collectionRoot} and {@code announcedNode}
   * are not null.
   *
   * @param collectionRoot the node with role {@link
   *     com.google.android.accessibility.utils.Role#ROLE_PAGER}, representing a collection of
   *     pages. Its descendants include {@code announcedNode}
   * @param announcedNode the node that was given accessibility focus. It is or is a child of a page
   *     item that belongs to the pager defined by {@code collectionRoot}
   * @param computeHeaders is {@code true} if {@link
   *     #shouldComputeHeaders(AccessibilityNodeInfoCompat)} returns {@code true}
   * @return
   */
  @Nullable
  private static PagerItemState extractPagerItemState(
      AccessibilityNodeInfoCompat collectionRoot,
      AccessibilityNodeInfoCompat announcedNode,
      boolean computeHeaders) {
    if ((collectionRoot == null) || (collectionRoot.getCollectionInfo() == null)) {
      return null;
    }

    // Checking the ancestors should incur zero performance penalty in the typical case
    // where list items are direct descendants. Assuming list items are not deeply
    // nested, any performance penalty would be minimal.

    AccessibilityNode collectionItem =
        AccessibilityNode.takeOwnership(
            AccessibilityNodeInfoUtils.getSelfOrMatchingAncestor(
                announcedNode, collectionRoot, FILTER_COLLECTION_ITEM));

    if (collectionItem == null) {
      return null;
    }

    try {
      CollectionInfoCompat collection = collectionRoot.getCollectionInfo();
      CollectionItemInfoCompat item = collectionItem.getCollectionItemInfo();

      boolean heading = computeHeaders && collectionItem.isHeading();
      int rowIndex = getRowIndex(item, collection);
      int columnIndex = getColumnIndex(item, collection);

      return new PagerItemState(heading, rowIndex, columnIndex);
    } finally {
      collectionItem.recycle("CollectionState.extractPagerItemState");
    }
  }

  /**
   * Guaranteed to return a non-{@code null} TableItemState if {@link #getRowColumnTransition()} is
   * not {@link #TYPE_NONE} and {@link #getCollectionRole()} is {@link
   * com.google.android.accessibility.utils.Role#ROLE_GRID}.
   */
  @Nullable
  public TableItemState getTableItemState() {
    if (mItemState != null && mItemState instanceof TableItemState) {
      return (TableItemState) mItemState;
    }

    return null;
  }

  @Nullable
  private static TableItemState getTableItemState(
      AccessibilityNodeInfoCompat collectionRoot,
      AccessibilityNodeInfoCompat announcedNode,
      SparseArray<CharSequence> rowHeaders,
      SparseArray<CharSequence> columnHeaders,
      boolean computeHeaders,
      boolean computeNumbering) {
    if (collectionRoot == null || collectionRoot.getCollectionInfo() == null) {
      return null;
    }

    // Checking the ancestors should incur zero performance penalty in the typical case
    // where list items are direct descendants. Assuming list items are not deeply
    // nested, any performance penalty would be minimal.
    AccessibilityNodeInfoCompat collectionItem =
        AccessibilityNodeInfoUtils.getSelfOrMatchingAncestor(
            announcedNode, collectionRoot, FILTER_COLLECTION_ITEM);

    if (collectionItem == null) {
      return null;
    }

    CollectionInfoCompat collection = collectionRoot.getCollectionInfo();
    CollectionItemInfoCompat item = collectionItem.getCollectionItemInfo();

    int heading = computeHeaders ? getTableHeading(collectionItem, item, collection) : TYPE_NONE;
    int rowIndex = getRowIndex(item, collection);
    int columnIndex = getColumnIndex(item, collection);
    CharSequence rowName = rowIndex != -1 ? rowHeaders.get(rowIndex) : null;
    CharSequence columnName = columnIndex != -1 ? columnHeaders.get(columnIndex) : null;

    collectionItem.recycle();
    return new TableItemState(
        heading, rowName, columnName, rowIndex, columnIndex, computeNumbering);
  }

  /**
   * If the collection is part of a hierarchy of collections (e.g. a tree or outlined list), returns
   * the nesting level of the collection, with 0 being the outermost list, 1 being the list nested
   * within the outermost list, and so forth. If the collection is not part of a hierarchy, returns
   * -1.
   */
  public int getCollectionLevel() {
    return mCollectionLevel;
  }

  private static int getCollectionLevelInternal(@Nullable AccessibilityNodeInfoCompat node) {
    if (node == null) {
      return -1;
    }

    if (!FILTER_HIERARCHICAL_COLLECTION.accept(node)) {
      return -1;
    }

    return AccessibilityNodeInfoUtils.countMatchingAncestors(node, FILTER_HIERARCHICAL_COLLECTION);
  }

  /**
   * This method updates the collection state based on the new item focused by the user. Internally,
   * it advances the state machine and then acts on the new state,
   */
  public void updateCollectionInformation(
      AccessibilityNodeInfoCompat announcedNode, AccessibilityEvent event) {
    if (announcedNode == null) {
      return;
    }

    AccessibilityNodeInfoCompat announcedNodeParent = announcedNode.getParent();
    AccessibilityNodeInfoCompat newCollectionRoot =
        AccessibilityNodeInfoUtils.getCollectionRoot(announcedNodeParent);
    if (announcedNodeParent != null) {
      announcedNodeParent.recycle();
    }

    // STATE DIAGRAM:
    // (None*)--------->(Enter*)--------->(Interior*)--------->(Exit)
    //   ^               ^  |                                   ^ | |
    //   |               |  +---------------------------------> + | |
    //   |               + <--------------------------------------+ |
    //   + <--------------------------------------------------------+
    // * = can self loop.

    // Perform the state transition.
    switch (mCollectionTransition) {
      case NAVIGATE_ENTER:
      case NAVIGATE_INTERIOR:
        if (newCollectionRoot != null && newCollectionRoot.equals(mCollectionRoot)) {
          mCollectionTransition = NAVIGATE_INTERIOR;
        } else if (newCollectionRoot != null && shouldEnter(newCollectionRoot)) {
          mCollectionTransition = NAVIGATE_ENTER;
        } else {
          mCollectionTransition = NAVIGATE_EXIT;
        }
        break;
      case NAVIGATE_EXIT:
      case NAVIGATE_NONE:
      default:
        if (newCollectionRoot != null && shouldEnter(newCollectionRoot)) {
          mCollectionTransition = NAVIGATE_ENTER;
        } else {
          mCollectionTransition = NAVIGATE_NONE;
        }
        break;
    }

    // Act on the new state.
    switch (mCollectionTransition) {
      case NAVIGATE_ENTER:
        {
          // Only recompute workarounds once per collection.
          mShouldComputeHeaders = shouldComputeHeaders(newCollectionRoot);
          mShouldComputeNumbering = shouldComputeNumbering(newCollectionRoot);
          mCollectionLevel = getCollectionLevelInternal(newCollectionRoot);

          ItemState newItemState = null;
          if (Role.getRole(newCollectionRoot) == Role.ROLE_GRID) {
            // Cache the row and column headers.
            updateTableHeaderInfo(
                newCollectionRoot, mRowHeaders, mColumnHeaders, mShouldComputeHeaders);

            newItemState =
                getTableItemState(
                    newCollectionRoot,
                    announcedNode,
                    mRowHeaders,
                    mColumnHeaders,
                    mShouldComputeHeaders,
                    mShouldComputeNumbering);
          } else if (Role.getRole(newCollectionRoot) == Role.ROLE_LIST) {
            newItemState =
                getListItemState(newCollectionRoot, announcedNode, mShouldComputeNumbering);
          } else if (Role.getRole(newCollectionRoot) == Role.ROLE_PAGER) {
            newItemState =
                extractPagerItemState(newCollectionRoot, announcedNode, mShouldComputeHeaders);
          }

          // Row and column change only if we enter and there is collection item information.
          if (newItemState == null) {
            mRowColumnTransition = TYPE_NONE;
          } else {
            mRowColumnTransition = TYPE_ROW | TYPE_COLUMN;
          }

          AccessibilityNodeInfoUtils.recycleNodes(mCollectionRoot, mLastAnnouncedNode);
          mCollectionRoot = newCollectionRoot;
          mLastAnnouncedNode = AccessibilityNodeInfoCompat.obtain(announcedNode);
          mItemState = newItemState;
          break;
        }
      case NAVIGATE_INTERIOR:
        {
          ItemState newItemState = null;
          if (Role.getRole(newCollectionRoot) == Role.ROLE_GRID) {
            newItemState =
                getTableItemState(
                    newCollectionRoot,
                    announcedNode,
                    mRowHeaders,
                    mColumnHeaders,
                    mShouldComputeHeaders,
                    mShouldComputeNumbering);
          } else if (Role.getRole(newCollectionRoot) == Role.ROLE_LIST) {
            newItemState =
                getListItemState(newCollectionRoot, announcedNode, mShouldComputeNumbering);
          } else if (Role.getRole(newCollectionRoot) == Role.ROLE_PAGER) {
            newItemState =
                extractPagerItemState(newCollectionRoot, announcedNode, mShouldComputeHeaders);
          }

          // Determine if the row and/or column has changed.
          if (newItemState == null) {
            mRowColumnTransition = TYPE_NONE;
          } else if (mItemState == null
              || mLastAnnouncedNode == null
              || mLastAnnouncedNode.equals(announcedNode)) {
            // We want to repeat row/column feedback on refocus *of the exact same node*.
            mRowColumnTransition = TYPE_ROW | TYPE_COLUMN;
          } else {
            mRowColumnTransition = newItemState.getTransition(mItemState);
          }

          AccessibilityNodeInfoUtils.recycleNodes(mCollectionRoot, mLastAnnouncedNode);
          mCollectionRoot = newCollectionRoot;
          mLastAnnouncedNode = AccessibilityNodeInfoCompat.obtain(announcedNode);
          mItemState = newItemState;
          break;
        }
      case NAVIGATE_EXIT:
        {
          // We can clear the item state, but we need to keep the collection root.
          AccessibilityNodeInfoUtils.recycleNodes(mLastAnnouncedNode, newCollectionRoot);
          mRowColumnTransition = 0;
          mLastAnnouncedNode = null;
          mItemState = null;
          break;
        }
      case NAVIGATE_NONE:
      default:
        {
          // Safe to clear everything.
          AccessibilityNodeInfoUtils.recycleNodes(
              mCollectionRoot, mLastAnnouncedNode, newCollectionRoot);
          mRowColumnTransition = 0;
          mCollectionRoot = null;
          mLastAnnouncedNode = null;
          mItemState = null;
          break;
        }
    }
  }

  private static void updateTableHeaderInfo(
      AccessibilityNodeInfoCompat collectionRoot,
      SparseArray<CharSequence> rowHeaders,
      SparseArray<CharSequence> columnHeaders,
      boolean computeHeaders) {
    rowHeaders.clear();
    columnHeaders.clear();

    if (!computeHeaders) {
      return;
    }

    if (collectionRoot == null || collectionRoot.getCollectionInfo() == null) {
      return;
    }

    // Limit search to children and grandchildren of the root node for performance reasons.
    // We want to search grandchildren because web pages put table headers <th> inside table
    // rows <tr> so they are nested two levels down.
    CollectionInfoCompat collectionInfo = collectionRoot.getCollectionInfo();
    int numChildren = collectionRoot.getChildCount();
    for (int i = 0; i < numChildren; ++i) {
      AccessibilityNodeInfoCompat child = collectionRoot.getChild(i);
      if (child == null) {
        continue;
      }
      if (!updateSingleTableHeader(child, collectionInfo, rowHeaders, columnHeaders)) {
        int numGrandchildren = child.getChildCount();
        for (int j = 0; j < numGrandchildren; ++j) {
          AccessibilityNodeInfoCompat grandchild = child.getChild(j);
          if (grandchild == null) {
            continue;
          }
          updateSingleTableHeader(grandchild, collectionInfo, rowHeaders, columnHeaders);
          grandchild.recycle();
        }
      }

      child.recycle();
    }
  }

  private static boolean updateSingleTableHeader(
      @Nullable AccessibilityNodeInfoCompat node,
      CollectionInfoCompat collectionInfo,
      SparseArray<CharSequence> rowHeaders,
      SparseArray<CharSequence> columnHeaders) {
    if (node == null) {
      return false;
    }

    CharSequence headingName = getHeaderText(node);
    CollectionItemInfoCompat itemInfo = node.getCollectionItemInfo();
    if (itemInfo != null && headingName != null) {
      @RowColumnTransition int headingType = getTableHeading(node, itemInfo, collectionInfo);
      if ((headingType & TYPE_ROW) != 0) {
        rowHeaders.put(itemInfo.getRowIndex(), headingName);
      }
      if ((headingType & TYPE_COLUMN) != 0) {
        columnHeaders.put(itemInfo.getColumnIndex(), headingName);
      }

      return headingType != TYPE_NONE;
    }

    return false;
  }

  /**
   * For finding the name of the header, we want to use a simpler strategy than the
   * NodeSpeechRuleProcessor. We don't want to include the role description of items within the
   * header, because it will add confusion when the header name is appended to collection items. But
   * we do want to search down the tree in case the immediate root element doesn't have text.
   *
   * <p>We traverse single children of single children until we find a node with text. If we hit any
   * node that has multiple children, we simply stop the search and return {@code null}.
   */
  public static @Nullable CharSequence getHeaderText(AccessibilityNodeInfoCompat node) {
    if (node == null) {
      return null;
    }

    Set<AccessibilityNodeInfoCompat> visitedNodes = new HashSet<>();
    try {
      AccessibilityNodeInfoCompat currentNode = AccessibilityNodeInfoCompat.obtain(node);
      while (currentNode != null) {
        if (!visitedNodes.add(currentNode)) {
          // Cycle in traversal.
          currentNode.recycle();
          return null;
        }

        CharSequence nodeText = AccessibilityNodeInfoUtils.getNodeText(currentNode);
        if (nodeText != null) {
          return nodeText;
        }

        if (currentNode.getChildCount() != 1) {
          return null;
        }

        currentNode = currentNode.getChild(0);
      }
    } finally {
      AccessibilityNodeInfoUtils.recycleNodes(visitedNodes);
    }

    return null;
  }

  /**
   * In this method, only one cell per row and per column can be the row or column header.
   * Additionally, a cell can be a row or column header but not both.
   *
   * @return {@code TYPE_ROW} or {@ocde TYPE_COLUMN} for row or column headers; {@code
   *     TYPE_INDETERMINATE} for cells marked as headers that are neither row nor column headers;
   *     {@code TYPE_NONE} for all other cells.
   */
  private static @TableHeadingType int getTableHeading(
      @NonNull AccessibilityNodeInfoCompat node,
      @NonNull CollectionItemInfoCompat item,
      @NonNull CollectionInfoCompat collection) {
    if (AccessibilityNodeInfoUtils.isHeading(node)) {
      if (item.getRowSpan() == 1 && item.getColumnSpan() == 1) {
        if (getRowIndex(item, collection) == 0) {
          return TYPE_COLUMN;
        }
        if (getColumnIndex(item, collection) == 0) {
          return TYPE_ROW;
        }
      }
      return TYPE_INDETERMINATE;
    }

    return TYPE_NONE;
  }

  /** @return -1 if there is no valid row index for the item; otherwise the item's row index */
  private static int getRowIndex(
      @NonNull CollectionItemInfoCompat item, @NonNull CollectionInfoCompat collection) {
    if (item.getRowSpan() == collection.getRowCount()) {
      return -1;
    } else if (item.getRowIndex() < 0) {
      return -1;
    } else {
      return item.getRowIndex();
    }
  }

  /**
   * @return -1 if there is no valid column index for the item; otherwise the item's column index
   */
  private static int getColumnIndex(
      @NonNull CollectionItemInfoCompat item, @NonNull CollectionInfoCompat collection) {
    if (item.getColumnSpan() == collection.getColumnCount()) {
      return -1;
    } else if (item.getColumnIndex() < 0) {
      return -1;
    } else {
      return item.getColumnIndex();
    }
  }

  private static boolean shouldEnter(@NonNull AccessibilityNodeInfoCompat collectionRoot) {
    if (collectionRoot.getCollectionInfo() != null) {
      CollectionInfoCompat collectionInfo = collectionRoot.getCollectionInfo();
      if (!hasMultipleItems(
          collectionInfo.getRowCount(),
          collectionInfo.getColumnCount(),
          Role.getRole(collectionRoot))) {
        return false;
      }
    } else if (collectionRoot.getChildCount() <= 1) {
      // If we don't have collection info, use the child count as an approximation.
      return false;
    }

    // If the collection is flat and contains other flat collections, then we discard it.
    // We only announce hierarchies of collections if they are explicitly marked hierarchical.
    // Otherwise we announce only the innermost collection.
    if (FILTER_FLAT_COLLECTION.accept(collectionRoot)
        && AccessibilityNodeInfoUtils.hasMatchingDescendant(
            collectionRoot, FILTER_FLAT_COLLECTION)) {
      return false;
    }

    return true;
  }

  private static boolean hasMultipleItems(int rows, int columns, @RoleName int role) {
    // Collection size is unknown, this is a valid collection.
    if (rows == -1 && columns == -1) {
      return true;
    }
    int numberOfItems = rows * columns;
    // Collection size is zero or 1, not a valid a collection
    if (numberOfItems == 0 || numberOfItems == 1) {
      // ViewPager2 will have 0 items following cl/266013601 and won't be entered. Its collection
      // info will populate info.rowCount/info.colCount with 0 if it's horizontal/vertical
      // A corresponding ViewPager2 change fixes this but won't be submitted until after 8.1. Add a
      // special case for pagers here. If it has a size of 0 (row or col == 0 ) but a col or row
      // size > 0, then enter it.
      // TODO Remove this code/revert this change when aosp/1093531 gets in.
      if (role == Role.ROLE_PAGER) {
        if (numberOfItems == 0 && (rows > 1 || columns > 1)) {
          return true;
        }
      }
      return false;
    }
    return true;
  }

  /**
   * Don't compute headers if: (1) API level is pre-N, and (2) the collection root is not a
   * descendant of a WebView, and (3) the collection root is itself a ListView or GridView.
   *
   * <p>Under these circumstances, the framework ListView/GridView will mark headers as non-headers
   * and vice-versa.
   */
  private static boolean shouldComputeHeaders(@NonNull AccessibilityNodeInfoCompat collectionRoot) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
      if (!AccessibilityNodeInfoUtils.hasMatchingAncestor(collectionRoot, FILTER_WEBVIEW)) {
        // TODO: Convert to use Role.
        // Bugs exist in specific classes, so check class names and not roles.
        if (AccessibilityNodeInfoUtils.nodeIsListOrGrid(collectionRoot)) {
          return false;
        }
      }
    }

    return true;
  }

  /**
   * Don't compute indices or row/column counts if: (1) API level is pre-N, (2) the collection root
   * is not a descendant of a WebView, and (3) the collection root is not a pager.
   *
   * <p>Item indices are broken in some major first-party apps that use "spacer" items in
   * collections; this check makes sure no apps in the wild are affected. TODO: Re-evaluate
   * this check before N release to see if it needs to be extended to N.
   *
   * <p>Always compute for pagers. The ability to have pagers with collections was introduced after
   * these bugs, and visually pagers should not have "spacer" items.
   */
  private static boolean shouldComputeNumbering(
      @NonNull AccessibilityNodeInfoCompat collectionRoot) {
    if (Role.getRole(collectionRoot) == Role.ROLE_PAGER) {
      return true;
    }
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
      if (!AccessibilityNodeInfoUtils.hasMatchingAncestor(collectionRoot, FILTER_WEBVIEW)) {
        return false;
      }
    }

    return true;
  }
}
