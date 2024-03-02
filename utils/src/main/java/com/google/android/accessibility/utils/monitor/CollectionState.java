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

package com.google.android.accessibility.utils.monitor;

import android.text.TextUtils;
import android.util.SparseArray;
import android.view.accessibility.AccessibilityEvent;
import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat.CollectionInfoCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat.CollectionItemInfoCompat;
import com.google.android.accessibility.utils.AccessibilityEventUtils;
import com.google.android.accessibility.utils.AccessibilityNode;
import com.google.android.accessibility.utils.AccessibilityNodeInfoUtils;
import com.google.android.accessibility.utils.Filter;
import com.google.android.accessibility.utils.Role;
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
      value = {TYPE_NONE, TYPE_ROW, TYPE_COLUMN})
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

  /** Possible modes of selection for a collection like radio-group or multi-select list. */
  @Retention(RetentionPolicy.SOURCE)
  @IntDef({SELECTION_NONE, SELECTION_SINGLE, SELECTION_MULTIPLE})
  public @interface CollectionSelectionMode {}

  public static final int SELECTION_NONE =
      AccessibilityNodeInfoCompat.CollectionInfoCompat.SELECTION_MODE_NONE;
  public static final int SELECTION_SINGLE =
      AccessibilityNodeInfoCompat.CollectionInfoCompat.SELECTION_MODE_SINGLE;
  public static final int SELECTION_MULTIPLE =
      AccessibilityNodeInfoCompat.CollectionInfoCompat.SELECTION_MODE_MULTIPLE;

  @CollectionTransition private int mCollectionTransition = NAVIGATE_NONE;
  @RowColumnTransition private int mRowColumnTransition = TYPE_NONE;
  private @Nullable AccessibilityNodeInfoCompat mCollectionRoot;
  private @Nullable AccessibilityNodeInfoCompat mLastAnnouncedNode;
  private @Nullable ItemState mItemState;
  private SparseArray<CharSequence> mRowHeaders = new SparseArray<>();
  private SparseArray<CharSequence> mColumnHeaders = new SparseArray<>();
  private int mCollectionLevel = -1;

  // Automotive sets these strings into the content description to indicate the view is
  // a rotary container, a scrollable container and can be scrolled horizontally by the
  // rotary controller or a scrollable container and can be scrolled vertically by the
  // rotary controller.
  private static final String ROTARY_CONTAINER = "com.android.car.ui.utils.ROTARY_CONTAINER";
  private static final String ROTARY_HORIZONTALLY_SCROLLABLE =
      "com.android.car.ui.utils.HORIZONTALLY_SCROLLABLE";
  private static final String ROTARY_VERTICALLY_SCROLLABLE =
      "com.android.car.ui.utils.VERTICALLY_SCROLLABLE";

  private static final Filter<AccessibilityNodeInfoCompat> FILTER_HIERARCHICAL_COLLECTION =
      new Filter<AccessibilityNodeInfoCompat>() {
        @Override
        public boolean accept(AccessibilityNodeInfoCompat node) {
          return AccessibilityNodeInfoUtils.FILTER_COLLECTION.accept(node)
              && node.getCollectionInfo() != null
              && node.getCollectionInfo().isHierarchical();
        }
      };

  private static final Filter<AccessibilityNodeInfoCompat> FILTER_FLAT_COLLECTION =
      new Filter<AccessibilityNodeInfoCompat>() {
        @Override
        public boolean accept(AccessibilityNodeInfoCompat node) {
          return AccessibilityNodeInfoUtils.FILTER_COLLECTION.accept(node)
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
    @RowColumnTransition
    public int getTransition(@NonNull ItemState from);
  }

  public static class ListItemState implements ItemState {
    private final boolean isHeading;
    /** The role description, or {@code null} if it has none. */
    private final @Nullable CharSequence roleDescription;

    private final int index;

    public ListItemState(boolean isHeading, @Nullable CharSequence roleDescription, int index) {
      this.isHeading = isHeading;
      this.index = index;
      this.roleDescription = roleDescription;
    }

    @Override
    @RowColumnTransition
    public int getTransition(@NonNull ItemState from) {
      if (!(from instanceof ListItemState)) {
        return TYPE_ROW | TYPE_COLUMN;
      }

      ListItemState otherListItemState = (ListItemState) from;
      if (index != otherListItemState.index) {
        return TYPE_ROW | TYPE_COLUMN;
      }

      return TYPE_NONE;
    }

    public boolean isHeading() {
      return isHeading;
    }

    public int getIndex() {
      return index;
    }

    public @Nullable CharSequence getRoleDescription() {
      return roleDescription;
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
    @TableHeadingType private final int headingType;
    /** The row name, or {@code null} if the row is unnamed. */
    private final @Nullable CharSequence rowName;
    /** The column name, or {@code null} if the column is unnamed. */
    private final @Nullable CharSequence columnName;
    /** The role description, or {@code null} if it has none. */
    private final @Nullable CharSequence roleDescription;

    private final int rowIndex;
    private final int columnIndex;

    public TableItemState(
        @TableHeadingType int headingType,
        @Nullable CharSequence rowName,
        @Nullable CharSequence columnName,
        @Nullable CharSequence roleDescription,
        int rowIndex,
        int columnIndex) {
      this.headingType = headingType;
      this.rowName = rowName;
      this.columnName = columnName;
      this.roleDescription = roleDescription;
      this.rowIndex = rowIndex;
      this.columnIndex = columnIndex;
    }

    @Override
    @RowColumnTransition
    public int getTransition(@NonNull ItemState other) {
      if (!(other instanceof TableItemState)) {
        return TYPE_ROW | TYPE_COLUMN;
      }

      TableItemState otherTableItemState = (TableItemState) other;
      int transition = TYPE_NONE;
      if (rowIndex != otherTableItemState.rowIndex) {
        transition |= TYPE_ROW;
      }
      if (columnIndex != otherTableItemState.columnIndex) {
        transition |= TYPE_COLUMN;
      }

      return transition;
    }

    @TableHeadingType
    public int getHeadingType() {
      return headingType;
    }

    public @Nullable CharSequence getRowName() {
      return rowName;
    }

    public @Nullable CharSequence getColumnName() {
      return columnName;
    }

    public @Nullable CharSequence getRoleDescription() {
      return roleDescription;
    }

    public int getRowIndex() {
      return rowIndex;
    }

    public int getColumnIndex() {
      return columnIndex;
    }
  }

  public CollectionState() {}

  @CollectionTransition
  public int getCollectionTransition() {
    return mCollectionTransition;
  }

  @RowColumnTransition
  public int getRowColumnTransition() {
    return mRowColumnTransition;
  }

  public @Nullable CharSequence getCollectionName() {
    final CharSequence collectionName = AccessibilityNodeInfoUtils.getNodeText(mCollectionRoot);
    // If the collection name is the one of the ROTARY_CONTAINER, ROTARY_HORIZONTALLY_SCROLLABLE,
    // or ROTARY_VERTICALLY_SCROLLABLE, then ignoring it and return null because these strings
    // in the content description aren't the descriptive content.
    // TODO : b/245629969 for the long term solution.
    if (collectionName == null
        || TextUtils.equals(collectionName, ROTARY_CONTAINER)
        || TextUtils.equals(collectionName, ROTARY_HORIZONTALLY_SCROLLABLE)
        || TextUtils.equals(collectionName, ROTARY_VERTICALLY_SCROLLABLE)) {
      return null;
    }

    return collectionName;
  }

  /**
   * @return Either {@link com.google.android.accessibility.utils.Role#ROLE_LIST} or {@link
   *     com.google.android.accessibility.utils.Role#ROLE_GRID} if there is a collection, or {@link
   *     com.google.android.accessibility.utils.Role#ROLE_NONE} if there isn't one.
   */
  @Role.RoleName
  public int getCollectionRole() {
    return Role.getRole(mCollectionRoot);
  }

  public @Nullable CharSequence getCollectionRoleDescription() {
    return (mCollectionRoot == null) ? null : mCollectionRoot.getRoleDescription();
  }

  public int getCollectionRowCount() {
    if (mCollectionRoot == null || mCollectionRoot.getCollectionInfo() == null) {
      return -1;
    }

    return mCollectionRoot.getCollectionInfo().getRowCount();
  }

  public int getCollectionColumnCount() {
    if (mCollectionRoot == null || mCollectionRoot.getCollectionInfo() == null) {
      return -1;
    }

    return mCollectionRoot.getCollectionInfo().getColumnCount();
  }

  @CollectionAlignment
  public int getCollectionAlignment() {
    if (mCollectionRoot == null) {
      return ALIGNMENT_VERTICAL;
    } else {
      return getCollectionAlignmentInternal(mCollectionRoot.getCollectionInfo());
    }
  }

  @CollectionAlignment
  public static int getCollectionAlignmentInternal(@Nullable CollectionInfoCompat collection) {
    if (collection == null || collection.getRowCount() >= collection.getColumnCount()) {
      return ALIGNMENT_VERTICAL;
    } else {
      return ALIGNMENT_HORIZONTAL;
    }
  }

  public int getSelectionMode() {
    if (mCollectionRoot == null) {
      return SELECTION_NONE;
    } else {
      CollectionInfoCompat collection = mCollectionRoot.getCollectionInfo();
      return (collection == null) ? SELECTION_NONE : collection.getSelectionMode();
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
  public @Nullable ListItemState getListItemState() {
    if (mItemState != null && mItemState instanceof ListItemState) {
      return (ListItemState) mItemState;
    }

    return null;
  }

  private static @Nullable ListItemState getListItemState(
      @Nullable AccessibilityNodeInfoCompat collectionRoot,
      AccessibilityNodeInfoCompat announcedNode) {
    if (collectionRoot == null || collectionRoot.getCollectionInfo() == null) {
      return null;
    }

    // Checking the ancestors should incur zero performance penalty in the typical case
    // where list items are direct descendants. Assuming list items are not deeply
    // nested, any performance penalty would be minimal.
    AccessibilityNodeInfoCompat collectionItemNode =
        AccessibilityNodeInfoUtils.getSelfOrMatchingAncestor(
            announcedNode, collectionRoot, FILTER_COLLECTION_ITEM);

    if (collectionItemNode == null) {
      return null;
    }

    CollectionInfoCompat collection = collectionRoot.getCollectionInfo();
    CollectionItemInfoCompat item = collectionItemNode.getCollectionItemInfo();

    boolean isHeading = AccessibilityNodeInfoUtils.isHeading(collectionItemNode);
    CharSequence roleDescription = collectionItemNode.getRoleDescription();
    int index;
    if (getCollectionAlignmentInternal(collection) == ALIGNMENT_VERTICAL) {
      index = getRowIndex(item, collection);
    } else {
      index = getColumnIndex(item, collection);
    }

    return new ListItemState(isHeading, roleDescription, index);
  }

  /**
   * Returns a non-{@code null} PagerItemState if {@link #getRowColumnTransition()} is not {@link
   * #TYPE_NONE} and {@link #getCollectionRole()} is {@link
   * com.google.android.accessibility.utils.Role#ROLE_PAGER}.
   */
  public @Nullable PagerItemState getPagerItemState() {
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
   * @return
   */
  private static @Nullable PagerItemState extractPagerItemState(
      @Nullable AccessibilityNodeInfoCompat collectionRoot,
      AccessibilityNodeInfoCompat announcedNode) {
    if ((collectionRoot == null) || (collectionRoot.getCollectionInfo() == null)) {
      return null;
    }

    // Checking the ancestors should incur zero performance penalty in the typical case
    // where list items are direct descendants. Assuming list items are not deeply
    // nested, any performance penalty would be minimal.

    AccessibilityNode collectionItemNode =
        AccessibilityNode.takeOwnership(
            AccessibilityNodeInfoUtils.getSelfOrMatchingAncestor(
                announcedNode, collectionRoot, FILTER_COLLECTION_ITEM));

    if (collectionItemNode == null) {
      return null;
    }

    CollectionInfoCompat collection = collectionRoot.getCollectionInfo();
    CollectionItemInfoCompat item = collectionItemNode.getCollectionItemInfo();

    boolean heading = collectionItemNode.isHeading();
    int rowIndex = getRowIndex(item, collection);
    int columnIndex = getColumnIndex(item, collection);

    return new PagerItemState(heading, rowIndex, columnIndex);
  }

  /**
   * Guaranteed to return a non-{@code null} TableItemState if {@link #getRowColumnTransition()} is
   * not {@link #TYPE_NONE} and {@link #getCollectionRole()} is {@link
   * com.google.android.accessibility.utils.Role#ROLE_GRID}.
   */
  public @Nullable TableItemState getTableItemState() {
    if (mItemState != null && mItemState instanceof TableItemState) {
      return (TableItemState) mItemState;
    }

    return null;
  }

  private static @Nullable TableItemState getTableItemState(
      @Nullable AccessibilityNodeInfoCompat collectionRoot,
      AccessibilityNodeInfoCompat announcedNode,
      SparseArray<CharSequence> rowHeaders,
      SparseArray<CharSequence> columnHeaders) {
    if (collectionRoot == null || collectionRoot.getCollectionInfo() == null) {
      return null;
    }

    // Checking the ancestors should incur zero performance penalty in the typical case
    // where list items are direct descendants. Assuming list items are not deeply
    // nested, any performance penalty would be minimal.
    AccessibilityNodeInfoCompat collectionItemNode =
        AccessibilityNodeInfoUtils.getSelfOrMatchingAncestor(
            announcedNode, collectionRoot, FILTER_COLLECTION_ITEM);

    if (collectionItemNode == null) {
      return null;
    }

    CollectionInfoCompat collection = collectionRoot.getCollectionInfo();
    CollectionItemInfoCompat item = collectionItemNode.getCollectionItemInfo();

    @TableHeadingType int heading = getTableHeadingType(collectionItemNode, item, collection);
    int rowIndex = getRowIndex(item, collection);
    int columnIndex = getColumnIndex(item, collection);
    CharSequence rowName = rowIndex != -1 ? rowHeaders.get(rowIndex) : null;
    CharSequence columnName = columnIndex != -1 ? columnHeaders.get(columnIndex) : null;
    CharSequence roleDescription = collectionItemNode.getRoleDescription();
    if (rowName == null) {
      rowName = AccessibilityNodeInfoUtils.geGridRowTitle(collectionItemNode);
    }
    if (columnName == null) {
      columnName = AccessibilityNodeInfoUtils.geGridColumnTitle(collectionItemNode);
    }

    return new TableItemState(heading, rowName, columnName, roleDescription, rowIndex, columnIndex);
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

  public int getEventTypes() {
    return AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED;
  }

  /** Upon a TYPE_VIEW_FOCUSED event, collection information will be updated. */
  public void onAccessibilityEvent(AccessibilityEvent event) {
    if (event.getEventType() == AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED) {
      updateCollectionInformation(AccessibilityEventUtils.sourceCompat(event), event);
    }
  }

  /**
   * This method updates the collection state based on the new item focused by the user. Internally,
   * it advances the state machine and then acts on the new state,
   */
  public void updateCollectionInformation(
      @Nullable AccessibilityNodeInfoCompat announcedNode, AccessibilityEvent event) {
    if (announcedNode == null) {
      return;
    }

    AccessibilityNodeInfoCompat newCollectionRoot;
    if ((mCollectionRoot != null) && announcedNode.equals(mCollectionRoot)) {
      newCollectionRoot = mCollectionRoot;
    } else {
      newCollectionRoot = AccessibilityNodeInfoUtils.getCollectionRoot(announcedNode.getParent());
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
        if ((newCollectionRoot != null)
            && (mCollectionRoot != null)
            && newCollectionRoot.equals(mCollectionRoot)) {
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
          mCollectionLevel = getCollectionLevelInternal(newCollectionRoot);

          ItemState newItemState = null;
          if (Role.getRole(newCollectionRoot) == Role.ROLE_GRID) {
            // Cache the row and column headers.
            updateTableHeaderInfo(newCollectionRoot, mRowHeaders, mColumnHeaders);

            newItemState =
                getTableItemState(newCollectionRoot, announcedNode, mRowHeaders, mColumnHeaders);
          } else if (Role.getRole(newCollectionRoot) == Role.ROLE_LIST) {
            newItemState = getListItemState(newCollectionRoot, announcedNode);
          } else if (Role.getRole(newCollectionRoot) == Role.ROLE_PAGER) {
            newItemState = extractPagerItemState(newCollectionRoot, announcedNode);
          }

          // Row and column change only if we enter and there is collection item information.
          if (newItemState == null) {
            mRowColumnTransition = TYPE_NONE;
          } else {
            mRowColumnTransition = TYPE_ROW | TYPE_COLUMN;
          }

          mCollectionRoot = newCollectionRoot;
          mLastAnnouncedNode = announcedNode;
          mItemState = newItemState;
          break;
        }
      case NAVIGATE_INTERIOR:
        {
          ItemState newItemState = null;
          if (Role.getRole(newCollectionRoot) == Role.ROLE_GRID) {
            newItemState =
                getTableItemState(newCollectionRoot, announcedNode, mRowHeaders, mColumnHeaders);
          } else if (Role.getRole(newCollectionRoot) == Role.ROLE_LIST) {
            newItemState = getListItemState(newCollectionRoot, announcedNode);
          } else if (Role.getRole(newCollectionRoot) == Role.ROLE_PAGER) {
            newItemState = extractPagerItemState(newCollectionRoot, announcedNode);
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

          mCollectionRoot = newCollectionRoot;
          mLastAnnouncedNode = announcedNode;
          mItemState = newItemState;
          break;
        }
      case NAVIGATE_EXIT:
        {
          // We can clear the item state, but we need to keep the collection root.
          mRowColumnTransition = 0;
          mLastAnnouncedNode = null;
          mItemState = null;
          break;
        }
      case NAVIGATE_NONE:
      default:
        {
          // Safe to clear everything.
          mRowColumnTransition = 0;
          mCollectionRoot = null;
          mLastAnnouncedNode = null;
          mItemState = null;
          break;
        }
    }
  }

  private static void updateTableHeaderInfo(
      @Nullable AccessibilityNodeInfoCompat collectionRoot,
      SparseArray<CharSequence> rowHeaders,
      SparseArray<CharSequence> columnHeaders) {
    rowHeaders.clear();
    columnHeaders.clear();

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
        }
      }
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
      @TableHeadingType int headingType = getTableHeadingType(node, itemInfo, collectionInfo);
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
    AccessibilityNodeInfoCompat currentNode = node;
    while (currentNode != null) {
      if (!visitedNodes.add(currentNode)) {
        // Cycle in traversal.
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
  @TableHeadingType
  private static int getTableHeadingType(
      @NonNull AccessibilityNodeInfoCompat node,
      @NonNull CollectionItemInfoCompat item,
      @NonNull CollectionInfoCompat collection) {
    if (AccessibilityNodeInfoUtils.isHeading(node)) {
      if (item.getRowSpan() == 1 && item.getColumnSpan() == 1) {
        if (getRowIndex(item, collection) == 0 && collection.getColumnCount() > 1) {
          return TYPE_COLUMN;
        }
        if (getColumnIndex(item, collection) == 0 && collection.getRowCount() > 1) {
          return TYPE_ROW;
        }
      }
      return TYPE_INDETERMINATE;
    }

    return TYPE_NONE;
  }

  /**
   * @return -1 if there is no valid row index for the item; otherwise the item's row index
   */
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
      if (!hasMultipleItems(collectionInfo.getRowCount(), collectionInfo.getColumnCount())) {
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

  private static boolean hasMultipleItems(int rows, int columns) {
    // Collection size is unknown, this is a valid collection.
    if (rows == -1 && columns == -1) {
      return true;
    }
    int numberOfItems = rows * columns;
    // Collection size is zero or 1, not a valid a collection
    if (numberOfItems == 0 || numberOfItems == 1) {
      return false;
    }
    return true;
  }
}
