/*
 * Copyright (C) 2015 Google Inc.
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

package com.google.android.accessibility.switchaccess;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.Trace;
import androidx.annotation.VisibleForTesting;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import com.android.switchaccess.SwitchAccessService;
import com.google.android.accessibility.switchaccess.SwitchAccessPreferenceCache.SwitchAccessPreferenceChangedListener;
import com.google.android.accessibility.switchaccess.feedback.SwitchAccessFeedbackController;
import com.google.android.accessibility.switchaccess.menuitems.MenuItem;
import com.google.android.accessibility.switchaccess.menuitems.MenuItem.SelectMenuItemListener;
import com.google.android.accessibility.switchaccess.proto.SwitchAccessMenuTypeEnum.MenuType;
import com.google.android.accessibility.switchaccess.treenodes.ClearFocusNode;
import com.google.android.accessibility.switchaccess.treenodes.NonActionableItemNode;
import com.google.android.accessibility.switchaccess.treenodes.OverlayActionNode;
import com.google.android.accessibility.switchaccess.treenodes.ShowActionsMenuNode;
import com.google.android.accessibility.switchaccess.treenodes.TreeScanLeafNode;
import com.google.android.accessibility.switchaccess.treenodes.TreeScanNode;
import com.google.android.accessibility.switchaccess.treenodes.TreeScanSelectionNode;
import com.google.android.accessibility.switchaccess.treenodes.TreeScanSystemProvidedNode;
import com.google.android.accessibility.switchaccess.ui.HighlightStrategy;
import com.google.android.accessibility.switchaccess.ui.OptionScanHighlighter;
import com.google.android.accessibility.switchaccess.ui.OverlayController;
import com.google.android.accessibility.switchaccess.ui.OverlayController.MenuListener;
import com.google.android.libraries.accessibility.utils.concurrent.ThreadUtils;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Manages options in a tree of {@code TreeScanNode}s and traverses them as options are selected.
 */
public class OptionManager implements SwitchAccessPreferenceChangedListener, MenuListener {

  /**
   * Represents the type of scan event that an action triggered. i.e. if an action resulted in a
   * started scan or a continued scan.
   */
  public enum ScanEvent {
    /* An action resulted in a started scan. */
    SCAN_STARTED,
    /* An action resulted in an already started scan moving forwards or backwards. */
    SCAN_CONTINUED,
    /* An action didn't result in a started scan or a continued scan. */
    IGNORED_EVENT
  }

  public static final int OPTION_INDEX_CLICK = 0;
  public static final int OPTION_INDEX_NEXT = 1;

  // Add a slight delay for showing the highlight overlay to ensure that the global menu button
  // has been drawn.
  private static final int TIME_BETWEEN_DRAWING_MENU_BUTTON_AND_OVERLAY_MS = 100;

  // When a scan completes without a selection, the Switch Access menu button is hidden. This causes
  // a UI change that triggers #clearFocusIfNewTree. If auto-start scan is enabled, this will cause
  // the scan to immediately restart after the scan completes. To prevent this, we need to add a
  // slight delay between when focus is cleared after a completed scan with no selection and
  // attempting to begin scanning with auto-start scanning. This delay needs to be calculated after
  // focus is cleared, rather than after the menu button is hidden, so OptionManager should manage
  // the delay, instead of UiChangeStabilizer.
  private static final int TIME_BETWEEN_FOCUS_CLEAR_WITHOUT_SELECTION_AND_START_AUTO_SCAN_MS = 750;

  private final OverlayController overlayController;
  private final SwitchAccessFeedbackController switchAccessFeedbackController;

  private final List<OptionManagerListener> optionManagerListeners = new ArrayList<>();

  private Paint[] optionPaintArray;

  // The root node of the node tree corresponding to the last screen displayed before a Switch
  // Access menu was shown. If no menu is being shown, this will be null.
  @Nullable private TreeScanNode baseTreeRootNode;
  // The root node of the node tree corresponding to the current screen.
  @Nullable private TreeScanNode currentTreeRootNode;

  // The node representing the row being scanned during row-column scanning. If no row is being
  // scanned, this will be null.
  @Nullable private TreeScanNode currentRowNode;

  // The number of elements in the row currently being scanned. This is needed to honor the number
  // of scan loops with reverse auto-scan with row-column scanning. When currentRowNode is null,
  // this should be 0.
  private int currentRowLength = 0;

  // When a row is being scanned, the number of elements currently left in that row. This is needed
  // when using row-column scanning with reverse auto-scan or a switch assigned to previous. This
  // should always be less than currentRowLength.
  private int numberOfElementsInRowScanned = 0;

  // The node representing the previous row node scanned during row-column scanning. This is used
  // so that a row may be re-scanned after currentRowNode is set to null when the end of a row
  // is reached. This should be null if not in use.
  @Nullable private TreeScanNode previousRowNode;

  @Nullable private TreeScanNode currentNode;

  // Indicates if the Switch Access global menu button was just clicked. Used for the new menu
  // redesign to keep the menu button on screen when it's selected. The new menu is placed next to
  // the selected item, with everything darkened in the background except the selected item. So,
  // when the menu button is selected, we want to keep the menu button visible. Otherwise, the
  // global menu would show up and there would be a non-darkened patch in the background with
  // nothing inside it.
  private boolean globalMenuButtonJustClicked = false;

  private boolean groupSelectionEnabled;

  // If we should attempt to restart auto-scan in #clearFocusIfNewTree. This should be false only
  // immediately after focus has been cleared with no selection.
  private boolean shouldRestartAutoScan = true;

  @Nullable private ScanListener scanListener;
  @Nullable private SelectMenuItemListener selectMenuItemListener;
  private final HighlightStrategy scanHighlighter;

  @Nullable private ShowActionsMenuNode previouslySelectedLeafNode;

  @Nullable private ScanStateChangeTrigger previousScanStateChangeTrigger = null;

  /**
   * Possible triggers for the changes of scan state. These triggers include keys assigned for
   * scanning (e.g., Next, Select, etc.), the auto-scan features, the automatically start scanning
   * feature, and the window changes.
   */
  public enum ScanStateChangeTrigger {
    KEY_AUTO_SCAN,
    KEY_REVERSE_AUTO_SCAN,
    KEY_SELECT,
    KEY_NEXT,
    KEY_PREVIOUS,
    KEY_GROUP_1,
    KEY_GROUP_2,
    KEY_GROUP_3,
    KEY_GROUP_4,
    KEY_GROUP_5,
    KEY_POINT_SCAN,
    KEY_LONG_CLICK,
    KEY_SCROLL_FORWARD,
    KEY_SCROLL_BACKWARD,
    FEATURE_AUTO_START_SCAN,
    FEATURE_AUTO_SCAN,
    FEATURE_REVERSE_AUTO_SCAN,
    FEATURE_POINT_SCAN,
    FEATURE_POINT_SCAN_MENU,
    FEATURE_POINT_SCAN_CUSTOM_SWIPE,
    WINDOW_CHANGE,
  }

  /** @param overlayController The controller for the overlay on which to present options */
  public OptionManager(
      OverlayController overlayController, SwitchAccessFeedbackController feedbackController) {
    this.overlayController = overlayController;
    switchAccessFeedbackController = feedbackController;

    SwitchAccessPreferenceUtils.registerSwitchAccessPreferenceChangedListener(
        overlayController.getContext(), this);
    overlayController.addMenuListener(this);

    scanHighlighter = new OptionScanHighlighter(overlayController);
  }

  /** Clean up when this object is no longer needed */
  public void shutdown() {
    scanHighlighter.shutdown();
    SwitchAccessPreferenceUtils.unregisterSwitchAccessPreferenceChangedListener(this);
    if (currentTreeRootNode != null) {
      currentTreeRootNode.recycle();
    }
    currentTreeRootNode = null;

    if (baseTreeRootNode != null) {
      baseTreeRootNode.recycle();
    }
    baseTreeRootNode = null;
  }

  /**
   * Clear any traversal in progress and use the new tree for future traversals
   *
   * @param newTreeRoot The root of the tree to traverse next
   */
  public synchronized void clearFocusIfNewTree(TreeScanNode newTreeRoot) {
    Trace.beginSection("OptionManager#clearFocusIfNewTree");
    // If the tree is the same, do nothing.
    if ((currentTreeRootNode == newTreeRoot)) {
      focusNode(previouslySelectedLeafNode);
      Trace.endSection();
      return;
    }

    // We cannot quickly and reliably determine whether two trees are the same if they do not have
    // exactly the same nodes, so don't try (and instead treat the tree as if it is a new tree). If
    // use too many heuristics, we run the risk of leaving the user with a stale tree where the
    // nodes match  on-screen elements at a superficial level, but cannot be acted upon.

    // TODO: Determine correct behavior for highlighting the previously selected or
    // highlighted nodes when group selection is enabled.
    // Put focus back on the last action item that was selected if it's still on screen. To prevent
    // losing the previously selected leaf node, we shouldn't attempt to re-focus the previously
    // selected node if a non-static menu has been opened (for example, a text-editing menu).
    // However, since a static menu may use the previously selected node (i.e. re-focusing the
    // volume control adjustment buttons after an adjustment is made), attempt to re-focus the
    // previously selected node only if the opened menu is a static menu. This needs to be stored
    // in a boolean outside the if-statement in order to make the null-checker happy.
    boolean isMenuOpenThatShouldNotUsePreviouslySelectedNode =
        overlayController.isMenuVisible() && !overlayController.isStaticMenuVisible();
    if (currentNode != null) {
      // Put focus back on the last non-menu action item that was highlighted if it's still on
      // screen.
      TreeScanLeafNode nonNullPreviouslyHighlightedNode = currentNode.getFirstLeafNode();
      // Look for the node in the new tree that has the same bounds and view ids as the currentNode.
      TreeScanLeafNode previouslyHighlightLeafNodeInTree = null;
      if (newTreeRoot != null) {
        for (TreeScanLeafNode node : newTreeRoot.getNodesList()) {
          if (nonNullPreviouslyHighlightedNode.isProbablyTheSameAs(node)) {
            previouslyHighlightLeafNodeInTree = node;
            break;
          }
        }
      }

      // Current node cannot feasibly be null here, but we need an extra check to make the nullness
      // checker happy.
      if ((previouslyHighlightLeafNodeInTree != null) && (currentNode != null)) {
        // Update the current node to correspond to a parent of previouslyHighlightLeafNodeInTree.
        // This may not be the most direct parent if group selection or row-column scanning are
        // being used, so use the depth of currentNode and previouslyHighlightedNode to get the
        // correct ancestor. This will allow us to begin scanning a new tree without clearing focus
        // when the screen is changed when the highlighted item is the same on both screens. For
        // example, the menu button.
        int currentNodeDepth = currentNode.getDepth();
        TreeScanNode newHighlightedNode = previouslyHighlightLeafNodeInTree;
        int goalDepth = previouslyHighlightLeafNodeInTree.getDepth() - currentNodeDepth;
        while ((goalDepth > 0) && (newHighlightedNode.getParent() != null)) {
          goalDepth--;
          newHighlightedNode = newHighlightedNode.getParent();
        }

        // TODO: Determine if #onNodeFocused should be called after the current node
        // is updated. This provides visual feedback to users that the tree is being updated, but
        // creates a constant flickering that could be frustrating to users. This will be necessary
        // if we wish to maintain the highlight on an item with changed bounds.
        currentNode = newHighlightedNode;
      } else {
        signalFocusClearedIfScanning();
        clearFocus();
      }
    } else if ((previouslySelectedLeafNode != null)
        && !isMenuOpenThatShouldNotUsePreviouslySelectedNode) {
      // Look for the node in the new tree that has the same bounds and view ids as
      // previouslySelectedLeafNode
      ShowActionsMenuNode previouslySelectedLeafNodeInNewTree =
          findNodeInTree(newTreeRoot, previouslySelectedLeafNode);
      signalFocusClearedIfScanning();
      previouslySelectedLeafNode = null;
      if (previouslySelectedLeafNodeInNewTree != null) {
        currentNode = previouslySelectedLeafNodeInNewTree.getParent();
        onNodeFocused(true /* isFocusingFirstNodeInTree */, ScanStateChangeTrigger.WINDOW_CHANGE);
      } else {
        // New tree is different from the current tree and the previously selected item is not
        // present on the screen.
        clearFocus();
      }
    } else {
      // New tree is different from the current tree and either a menu is visible or there was no
      // relevant previously selected item.
      signalFocusClearedIfScanning();
      clearFocus();
    }

    if (currentTreeRootNode != null) {
      if (overlayController.isMenuVisible()) {
        // If we just showed a menu, hold on to the base tree. We need it to be able to
        // perform actions on it.
        if (baseTreeRootNode == null) {
          baseTreeRootNode = currentTreeRootNode;
        }
      } else {
        // If no menu is being shown, currentTreeRootNode will hold the current base tree,
        // so baseTreeRootNode can be set to null.
        currentTreeRootNode.recycle();
        if (baseTreeRootNode != null) {
          baseTreeRootNode.recycle();
          baseTreeRootNode = null;
        }
      }
    }

    currentTreeRootNode = newTreeRoot;

    // If auto-scan is already in progress when a view is refreshed, attempting to start
    // auto-scan will do nothing in AutoScanController#onOptionManagerStartedAutoScan, as
    // AutoScanController#onOptionManagerClearedFocus is not called. The next item with
    // auto-scan will be highlighted as expected. Therefore, no additional handling is needed
    // to ensure that the highlight doesn't spend too long on an item if the view is refreshing
    // more frequently than the auto-scan delay.
    startAutoScanIfNeeded();
    Trace.endSection();
  }

  /**
   * Traverse to the child node of the current node that has the specified index and take whatever
   * action is appropriate for that node. If nothing currently has focus, any option moves to the
   * root of the tree.
   *
   * @param optionIndex The index of the child to traverse to. Out-of-bounds indices, such as
   *     negative values or those above the index of the last child, cause focus to be reset
   * @param trigger The trigger that led to the selection of an option
   * @return The type of {@link ScanEvent} that this call resulted in
   */
  public ScanEvent selectOption(int optionIndex, ScanStateChangeTrigger trigger) {
    Trace.beginSection("OptionManager#selectOption");
    boolean triggeredByPreviousAction =
        (trigger == ScanStateChangeTrigger.KEY_REVERSE_AUTO_SCAN)
            || (trigger == ScanStateChangeTrigger.FEATURE_REVERSE_AUTO_SCAN)
            || (previousScanStateChangeTrigger == ScanStateChangeTrigger.KEY_PREVIOUS);
    boolean triggeredByUserAction =
        (trigger != ScanStateChangeTrigger.FEATURE_AUTO_START_SCAN)
            && (trigger != ScanStateChangeTrigger.FEATURE_AUTO_SCAN)
            && (trigger != ScanStateChangeTrigger.FEATURE_REVERSE_AUTO_SCAN)
            && (trigger != ScanStateChangeTrigger.FEATURE_POINT_SCAN)
            && (trigger != ScanStateChangeTrigger.FEATURE_POINT_SCAN_MENU)
            && (trigger != ScanStateChangeTrigger.FEATURE_POINT_SCAN_CUSTOM_SWIPE)
            && (trigger != ScanStateChangeTrigger.WINDOW_CHANGE);
    previousScanStateChangeTrigger = trigger;

    // If select option was triggered by a user action, cancel all speech regardless of their
    // queue mode.
    switchAccessFeedbackController.stopAllFeedback(
        false /* stopTtsCompletely */,
        triggeredByUserAction /* interruptItemsThatCanIgnoreInterrupts */,
        true /* cancelHints */);

    if (optionIndex < 0) {
      clearFocus();
      return ScanEvent.SCAN_CONTINUED;
    }

    for (OptionManagerListener listener : optionManagerListeners) {
      listener.onHighlightMoved();
    }

    boolean isScanJustStarted = (currentNode == null) && triggeredByUserAction;
    boolean isFocusingFirstNodeInTree = false;

    /* Move to desired node */
    if (currentNode == null) {
      isFocusingFirstNodeInTree = true;
      currentNode = currentTreeRootNode;
      if (currentNode == null) {
        return ScanEvent.SCAN_CONTINUED;
      }
    } else {
      if (!(currentNode instanceof TreeScanSelectionNode)) {
        /* This should never happen */
        clearFocus();
        return ScanEvent.SCAN_CONTINUED;
      }
      TreeScanSelectionNode selectionNode = (TreeScanSelectionNode) currentNode;
      if (optionIndex >= selectionNode.getChildCount()) {
        // User pressed a switch for an index greater than this node's order
        signalFocusClearedWithoutSelection(trigger);
        clearFocus();
        return ScanEvent.SCAN_CONTINUED;
      }
      currentNode = selectionNode.getChild(optionIndex);

      if (groupSelectionEnabled) {
        switchAccessFeedbackController.onGroupSelected(optionIndex);
      } else if (currentNode instanceof TreeScanSelectionNode
          && (optionIndex == OPTION_INDEX_CLICK)) {
        // A row was selected in the row-column scanning mode.
        currentRowNode = currentNode;
        if (triggeredByUserAction) {
          for (OptionManagerListener listener : optionManagerListeners) {
            listener.onRowScanStarted();
          }
        }

        if (triggeredByPreviousAction) {
          // If a row was selected via a reverse scan, highlight the last element in the row
          // first. This maintains consistency with scan behavior outside the row and allows all
          // elements in the row to be focusable.
          setCurrentNodeToLastSelectionNode(currentNode, true /* isRow */);
          numberOfElementsInRowScanned = 0;
        }

        if (currentNode != null) {
          switchAccessFeedbackController.onNodeSelected(currentNode);
        }
      } else if (currentNode instanceof ShowActionsMenuNode
          || currentNode instanceof OverlayActionNode) {
        // An item was selected in the linear scanning or row-column scanning mode.
        switchAccessFeedbackController.onNodeSelected(currentNode);
      }
    }

    if ((currentRowNode != null)
        && (optionIndex == OPTION_INDEX_NEXT)
        && (currentNode instanceof ClearFocusNode)) {
      // A row has finished being scanned forwards, so update focus.
      updateStateAndNotifyAfterRowScanCompleted(trigger);
      return ScanEvent.SCAN_CONTINUED;
    }

    onNodeFocused(isFocusingFirstNodeInTree, trigger);
    Trace.endSection();
    return isScanJustStarted ? ScanEvent.SCAN_STARTED : ScanEvent.SCAN_CONTINUED;
  }

  /**
   * Move up the tree to the parent of the current node.
   *
   * @param wrap Controls wrapping when the parent is null. If {@code false}, the current node will
   *     not change if the parent is null. If {@code true}, a node from the bottom of the tree will
   *     be used instead of a null parent. The bottom node is chosen as the last
   *     TreeScanSelectionNode found by repeatedly selecting {@code OPTION_INDEX_NEXT}. Note that
   *     this result makes sense for most traditional scanning methods, but may not make perfect
   *     sense for all trees.
   * @return The type of scan event that this call resulted in
   */
  public ScanEvent moveToParent(boolean wrap, ScanStateChangeTrigger trigger) {
    previousScanStateChangeTrigger = trigger;
    if (currentNode != null) {
      currentNode = currentNode.getParent();
      if (currentNode == null) {
        clearFocus();
      } else if ((currentRowNode != null) && (numberOfElementsInRowScanned >= currentRowLength)) {
        // TODO: Investigate comparing currentNode to the current row node's parent
        // instead of using the row length. This was previously unreliable, as the currentRow's
        // data could appear to change mid-scanning, making it difficult to verify similarity. We
        // should investigate why the data sometimes changes. A row has finished being scanned
        // backwards, so update focus.
        updateStateAndNotifyAfterRowScanCompleted(trigger);
        numberOfElementsInRowScanned = 0;
      } else {
        for (OptionManagerListener listener : optionManagerListeners) {
          listener.onHighlightMoved();
        }
        onNodeFocused(false /* isFocusingFirstNodeInTree */, trigger);
        if (currentRowNode != null) {
          numberOfElementsInRowScanned++;
        }
      }
      return ScanEvent.SCAN_CONTINUED;
    } else if (!wrap) {
      return ScanEvent.SCAN_CONTINUED;
    }

    setCurrentNodeToLastSelectionNode(currentTreeRootNode, false /* isRow */);
    if (currentNode == null) {
      clearFocus();
      return ScanEvent.SCAN_CONTINUED;
    } else {
      onNodeFocused(true /* isFocusingFirstNodeInTree */, trigger);
      return ScanEvent.SCAN_STARTED;
    }
  }

  /**
   * Allows a row to be re-scanned. If there's no previous row node, this method does nothing. This
   * should be called when the number of scan loops is greater than one. This happens after a row
   * scan has finished when using auto-scan with row-column scanning.
   */
  public void rescanRow() {
    if (previousRowNode != null) {
      currentRowNode = currentNode;
      currentNode = previousRowNode.getParent();
      previousRowNode = null;
    }
  }

  /**
   * Register a listener to be notified when focus is cleared
   *
   * @param optionManagerListener A listener that should be called when focus is cleared
   */
  public void addOptionManagerListener(OptionManagerListener optionManagerListener) {
    optionManagerListeners.add(optionManagerListener);
  }

  /**
   * Support legacy long click key action. Perform a long click on the currently selected item, if
   * that is possible. Long click is possible only if a ShowActionsMenuNode is the only thing
   * highlighted, and if the corresponding AccessibilityNodeInfoCompat accepts the long click
   * action. If the long click goes through, reset the focus.
   */
  public void performLongClick() {
    SwitchAccessNodeCompat compat = findCurrentlyActiveNode();
    if (compat != null) {
      if (compat.performAction(AccessibilityNodeInfoCompat.ACTION_LONG_CLICK)) {
        clearFocus();
        if (scanListener != null) {
          scanListener.onScanSelection(ScanStateChangeTrigger.KEY_LONG_CLICK);
        }
      }
      compat.recycle();
    }
  }

  /**
   * Support legacy scroll key actions. Perform a scroll on the currently selected item, if it is
   * scrollable, or a scrollable parent if one can be found. If the scroll action is accepted, focus
   * is cleared.
   *
   * @param scrollAction Either {@code AccessibilityNodeInfoCompat.ACTION_SCROLL_FORWARD} or {@code
   *     AccessibilityNodeInfoCompat.ACTION_SCROLL_BACKWARD}.
   */
  public void performScrollAction(int scrollAction) {
    SwitchAccessNodeCompat compat = findCurrentlyActiveNode();
    while (compat != null) {
      if (compat.isScrollable()) {
        if (compat.performAction(scrollAction)) {
          clearFocus();
          if (scanListener != null) {
            ScanStateChangeTrigger trigger =
                (scrollAction == AccessibilityNodeInfoCompat.ACTION_SCROLL_FORWARD)
                    ? ScanStateChangeTrigger.KEY_SCROLL_FORWARD
                    : ScanStateChangeTrigger.KEY_SCROLL_BACKWARD;
            scanListener.onScanSelection(trigger);
          }
        }
        compat.recycle();
        return;
      }
      SwitchAccessNodeCompat parent = compat.getParent();
      compat.recycle();
      compat = parent;
    }
  }

  /** Notify that preferences have changed. */
  @Override
  public void onPreferenceChanged(SharedPreferences sharedPreferences, String preferenceKey) {
    Trace.beginSection("OptionManager#onPreferenceChanged");
    Context context = overlayController.getContext();
    groupSelectionEnabled = SwitchAccessPreferenceUtils.isGroupSelectionEnabled(context);
    optionPaintArray = SwitchAccessPreferenceUtils.getHighlightPaints(context);
    /*
     * Always configure element 0 based on preferences. Only configure the others if we're
     * doing group selection.
     */
    if (!groupSelectionEnabled) {
      for (int i = 1; i < optionPaintArray.length; ++i) {
        optionPaintArray[i].setColor(Color.TRANSPARENT);
      }
    }
    Trace.endSection();
  }

  /**
   * Register a listener to notify of auto-scan activity
   *
   * @param listener the listener to be set
   */
  public void setScanListener(@Nullable ScanListener listener) {
    scanListener = listener;
  }

  /**
   * Register a listener to notify of the selection of a Switch Access menu item.
   *
   * @param listener the listener to be set
   */
  public void setSelectMenuItemListener(@Nullable SelectMenuItemListener listener) {
    selectMenuItemListener = listener;
  }

  @Override
  public void onMenuShown(MenuType menuType, int menuId) {
    globalMenuButtonJustClicked = (menuType == MenuType.TYPE_GLOBAL);
  }

  @Override
  public void onMenuClosed(int menuId) {
    globalMenuButtonJustClicked = false;
  }

  private @Nullable ShowActionsMenuNode findNodeInTree(
      TreeScanNode root, ShowActionsMenuNode similarNode) {
    if ((root == null) || (similarNode == null)) {
      return null;
    }

    for (TreeScanLeafNode currentNode : root.getNodesList()) {
      if (similarNode.isProbablyTheSameAs(currentNode)) {
        return (ShowActionsMenuNode) currentNode;
      }
    }

    return null;
  }

  private void clearFocus() {
    switchAccessFeedbackController.onFocusCleared();
    switchAccessFeedbackController.stopAllFeedback(
        false /* stopTtsCompletely */,
        false /* interruptItemsThatCanIgnoreInterrupts */,
        true /* cancelHints */);
    currentNode = null;
    currentRowLength = 0;
    overlayController.clearHighlightOverlay();
    if (!globalMenuButtonJustClicked) {
      overlayController.clearMenuButtonOverlay();
    }
    for (OptionManagerListener listener : optionManagerListeners) {
      listener.onOptionManagerClearedFocus();
    }
  }

  private void onNodeFocused(boolean isFocusingFirstNodeInTree, ScanStateChangeTrigger trigger) {
    if (scanListener != null) {
      if (currentNode instanceof ClearFocusNode) {
        signalFocusClearedWithoutSelection(trigger);
      } else if (currentNode instanceof TreeScanLeafNode) {
        scanListener.onScanSelection(trigger);
      } else {
        if (isFocusingFirstNodeInTree) {
          scanListener.onScanStart(trigger);
        } else {
          scanListener.onScanFocusChanged(trigger);
        }
      }
    }

    if (currentNode instanceof TreeScanLeafNode) {
      TreeScanLeafNode currentActionNode = (TreeScanLeafNode) currentNode;

      // If the current node is a non-actionable node, then, when a user presses the "Select"
      // switch, the only action we could reasonably perform is clearing focus. However, if a user
      // pressed "Select", it is more likely that the user is trying to perform an action on a
      // nearby element (e.g. the next element) than trying to clear focus. Further, clearing focus
      // would force the user to start the selection process from the beginning. Therefore, when a
      // user presses "Select" on a non-actionable item, Switch Access does nothing.
      if (currentNode instanceof NonActionableItemNode) {
        currentNode = currentNode.getParent();
      } else {
        // If the current node corresponds to a key on the on-screen keyboard, speak the key.
        if (currentNode instanceof ShowActionsMenuNode) {
          ShowActionsMenuNode node = (ShowActionsMenuNode) currentNode;
          if (node.isImeWindowType()) {
            switchAccessFeedbackController.onKeyTyped(node);
          }
        }

        // If the current node is an actionable node, perform actions or present the menu items.
        List<MenuItem> menuItems =
            currentActionNode.performActionOrGetMenuItems(selectMenuItemListener);
        if (!menuItems.isEmpty()) {
          // Store the last selected action node if it is neither an IME nor part of an overlay menu
          // so focus can be restored to this item if it's still on screen. Allow focus to be
          // restored on certain overlay menus that do not utilize a dynamic layout (i.e. the
          // volume adjustment page).
          if (currentActionNode instanceof ShowActionsMenuNode
              && !((ShowActionsMenuNode) currentActionNode).isImeWindowType()
              && (!overlayController.isMenuVisible() || overlayController.isStaticMenuVisible())) {
            previouslySelectedLeafNode = (ShowActionsMenuNode) currentActionNode;
          }
          if (menuItems.size() == 1) {
            clickMenuItem(menuItems.get(0));
          } else {
            Rect highlightRect = currentActionNode.getRectForNodeHighlight();
            Rect boundsToDraw = (highlightRect == null) ? new Rect() : highlightRect;
            overlayController.drawMenu(menuItems, boundsToDraw);
          }
        }

        clearFocus();
      }
    } else if (currentNode instanceof TreeScanSelectionNode) {
      final TreeScanSelectionNode selectionNode = (TreeScanSelectionNode) currentNode;

      // Draw the Global Menu button
      ThreadUtils.runOnMainThread(
          SwitchAccessService::isActive, overlayController::drawMenuButtonIfMenuNotVisible);

      switchAccessFeedbackController.speakFeedback(
          selectionNode, isFocusingFirstNodeInTree, overlayController.isMenuVisible());

      // showSelections() needs to know the location of the button in the screen to highlight
      // it. Hence run it a handler to give the thread a chance to draw the overlay.
      ThreadUtils.runOnMainThreadDelayed(
          SwitchAccessService::isActive,
          () -> {
            // The user may start scanning before the first highlight is shown on screen. We want
            // only the latest highlight to show.
            overlayController.clearHighlightOverlay();
            selectionNode.showSelections(scanHighlighter, optionPaintArray);
          },
          TIME_BETWEEN_DRAWING_MENU_BUTTON_AND_OVERLAY_MS);
    } else {
      clearFocus();
    }
  }

  // Automatically focus the next item if auto-start scanning is enabled or a menu has just been
  // opened.
  private void startAutoScanIfNeeded() {
    if ((SwitchAccessPreferenceUtils.isAutostartScanEnabled(overlayController.getContext())
            || overlayController.isMenuVisible())
        && shouldRestartAutoScan) {
      // We should automatically start scanning when a menu is opened if the previous action was
      // triggered by auto-scan. This should be calculated before #selectOption is is called to
      // highlight the first item in the menu, as this will reset the value of
      // previousScanStateTrigger. We should also automatically start scanning if the previous scan
      // state trigger is null, as this means that Switch Access was just turned on.
      boolean shouldAutomaticallyStartScanning =
          (previousScanStateChangeTrigger == ScanStateChangeTrigger.KEY_AUTO_SCAN)
              || (previousScanStateChangeTrigger == ScanStateChangeTrigger.KEY_REVERSE_AUTO_SCAN)
              || (previousScanStateChangeTrigger == ScanStateChangeTrigger.FEATURE_AUTO_SCAN)
              || (previousScanStateChangeTrigger == ScanStateChangeTrigger.FEATURE_AUTO_START_SCAN)
              || (previousScanStateChangeTrigger
                  == ScanStateChangeTrigger.FEATURE_REVERSE_AUTO_SCAN)
              || (previousScanStateChangeTrigger == null);
      if (currentNode == null) {
        selectOption(OPTION_INDEX_CLICK, ScanStateChangeTrigger.FEATURE_AUTO_START_SCAN);
      }

      // When the a menu is opened but it wasn't opened by a key assigned to auto-scan, only the
      // first menu item should be highlighted.
      if (shouldAutomaticallyStartScanning) {
        // Automatically start scanning if enabled
        for (int i = 0; i < optionManagerListeners.size(); i++) {
          OptionManagerListener listener = optionManagerListeners.get(i);
          listener.onOptionManagerStartedAutoScan();
        }
      }
    }
  }

  // None of the MenuItem onClickListeners use the input View, so it doesn't matter what it is.
  @SuppressWarnings("nullness:argument.type.incompatible")
  private void clickMenuItem(MenuItem menuItem) {
    menuItem.getOnClickListener().onClick(null);
  }

  /*
   * Find exactly one {@code SwitchAccessNodeCompat} in the current tree
   * @return an {@code obtain}ed SwitchAccessNodeCompat if there is exactly one in the
   * current tree. Returns {@code null} otherwise.
   */
  @Nullable
  private SwitchAccessNodeCompat findCurrentlyActiveNode() {
    if (!(currentNode instanceof TreeScanSelectionNode)) {
      return null;
    }
    TreeScanNode startNode = ((TreeScanSelectionNode) currentNode).getChild(OPTION_INDEX_CLICK);
    Set<TreeScanSystemProvidedNode> nodeSet = new HashSet<>();
    addItemNodesToSet(startNode, nodeSet);
    SwitchAccessNodeCompat compat = null;
    for (TreeScanSystemProvidedNode treeScanSystemProvidedNode : nodeSet) {
      SwitchAccessNodeCompat nodeCompat = treeScanSystemProvidedNode.getNodeInfoCompat();
      if (nodeCompat == null) {
        continue; // Should never happen
      }
      if (compat == null) {
        compat = nodeCompat;
      } else if (compat.equals(nodeCompat)) {
        nodeCompat.recycle();
      } else {
        compat.recycle();
        nodeCompat.recycle();
        return null;
      }
    }
    return compat;
  }

  /*
   * Find all ItemNodes in the tree rooted at the current selection
   */
  private void addItemNodesToSet(TreeScanNode startNode, Set<TreeScanSystemProvidedNode> nodeSet) {
    if (startNode instanceof TreeScanSystemProvidedNode) {
      nodeSet.add((TreeScanSystemProvidedNode) startNode);
    }
    if (startNode instanceof TreeScanSelectionNode) {
      TreeScanSelectionNode selectionNode = (TreeScanSelectionNode) startNode;
      for (int i = 0; i < selectionNode.getChildCount(); ++i) {
        addItemNodesToSet(selectionNode.getChild(i), nodeSet);
      }
    }
  }

  /*
   * Sets the current node to be the last selection node that can be reached from currentRoot.
   *
   * If currentRoot represents a row, we should update the current row length.
   */
  private void setCurrentNodeToLastSelectionNode(
      @Nullable TreeScanNode currentRoot, boolean isRow) {
    TreeScanNode newNode = currentRoot;
    if (!(newNode instanceof TreeScanSelectionNode)) {
      currentNode = null;
      return;
    }
    TreeScanNode possibleNewNode = ((TreeScanSelectionNode) newNode).getChild(OPTION_INDEX_NEXT);
    if (isRow) {
      currentRowLength = 0;
    }
    while (possibleNewNode instanceof TreeScanSelectionNode) {
      newNode = possibleNewNode;
      possibleNewNode = ((TreeScanSelectionNode) newNode).getChild(OPTION_INDEX_NEXT);
      currentRowLength++;
    }
    currentNode = newNode;
  }

  /*
   * Updates state after a row has finished being scanned and notifies any listeners that a row
   * scan has been completed. Updating state is necessary to either re-scan the row or clear focus.
   */
  private void updateStateAndNotifyAfterRowScanCompleted(ScanStateChangeTrigger trigger) {
    previousRowNode = currentRowNode;
    currentRowNode = null;
    currentNode = null;

    boolean shouldRescanRow = false;
    for (OptionManagerListener listener : optionManagerListeners) {
      shouldRescanRow |= listener.onRowScanCompleted(trigger);
    }

    if (!shouldRescanRow) {
      // The row isn't being rescanned, so focus should be cleared.
      clearFocus();
    }
  }

  private void focusNode(@Nullable ShowActionsMenuNode node) {
    if (node != null) {
      currentNode = node.getParent();
      onNodeFocused(true /* isFocusingFirstNodeInTree */, ScanStateChangeTrigger.WINDOW_CHANGE);
    }
  }

  private void signalFocusClearedIfScanning() {
    // If current node is not null, then we are about to rebuild the tree while the user is in the
    // middle of scanning and has not selected anything yet. We should only send this if focus is
    // actually cleared. Otherwise, we can get a constant stream of "screen changed" notifications
    // on constantly refreshing views.
    if (currentNode != null) {
      switchAccessFeedbackController.onTreeRebuiltDuringScanning();
      if (scanListener != null) {
        scanListener.onScanFocusClearedOnWindowChange(ScanStateChangeTrigger.WINDOW_CHANGE);
      }
    }
  }

  @VisibleForTesting
  void signalFocusClearedWithoutSelection(ScanStateChangeTrigger trigger) {
    if (scanListener != null) {
      scanListener.onScanFocusClearedAtEndWithNoSelection(trigger);
    }

    shouldRestartAutoScan = false;
    ThreadUtils.runOnMainThreadDelayed(
        SwitchAccessService::isActive,
        () -> shouldRestartAutoScan = true,
        TIME_BETWEEN_FOCUS_CLEAR_WITHOUT_SELECTION_AND_START_AUTO_SCAN_MS);
  }

  /** Interface to monitor when focus is cleared */
  public interface OptionManagerListener {
    /** Called when scanning is automatically started */
    void onOptionManagerStartedAutoScan();

    /** Called when focus clears */
    void onOptionManagerClearedFocus();

    /** Called when the highlight moves */
    void onHighlightMoved();

    /** Called when a row starts being scanned. */
    void onRowScanStarted();

    /**
     * Called when a row finishes being scanned.
     *
     * @param trigger The {@link ScanStateChangeTrigger} that caused the row scan to be completed
     * @return If the completed row should be rescanned
     */
    boolean onRowScanCompleted(ScanStateChangeTrigger trigger);
  }

  /** Interface to monitor the user's progress of scanning to desired items */
  public interface ScanListener {
    /** Called when scanning starts and the first highlighting is drawn */
    void onScanStart(ScanStateChangeTrigger trigger);
    /** Called when scanning reaches a new selection node and highlighting changes */
    void onScanFocusChanged(ScanStateChangeTrigger trigger);
    /** Called when scanning reaches an action node and an action is taken */
    void onScanSelection(ScanStateChangeTrigger trigger);
    /**
     * Called when focus is cleared after scanning reaches to the end without any action being taken
     */
    void onScanFocusClearedAtEndWithNoSelection(ScanStateChangeTrigger trigger);
    /**
     * Called when focus is cleared without any action being taken due to unexpected window changes
     */
    void onScanFocusClearedOnWindowChange(ScanStateChangeTrigger trigger);
  }
}
