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
import android.os.Handler;
import android.os.SystemClock;
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import com.google.android.accessibility.switchaccess.utils.FeedbackUtils;
import com.google.android.accessibility.utils.SharedPreferencesUtils;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Manages options in a tree of {@code TreeScanNode}s and traverses them as options are selected.
 */
public class OptionManager implements SharedPreferences.OnSharedPreferenceChangeListener {
  public static final int OPTION_INDEX_CLICK = 0;
  public static final int OPTION_INDEX_NEXT = 1;

  private final OverlayController mOverlayController;
  private final FeedbackController mFeedbackController;

  private final List<OptionManagerListener> mOptionManagerListeners = new ArrayList<>();

  private Paint[] mOptionPaintArray;

  // The root node of the node tree corresponding to the last screen displayed before a Switch
  // Access menu was shown. If no menu is being shown, this will be null.
  private TreeScanNode mBaseTreeRootNode = null;
  // The root node of the node tree corresponding to the current screen.
  private TreeScanNode mCurrentTreeRootNode = null;

  private TreeScanNode mCurrentNode = null;

  private boolean mStartScanAutomatically = false;

  private boolean mIsSpokenFeedbackEnabled;
  private boolean mSpeakFirstAndLastItem;
  private boolean mSpeakNumberOfItems;
  private boolean mSpeakAllItems;

  private boolean mOptionScanningEnabled;
  private boolean mNomonClocksEnabled;

  private ScanListener mScanListener;
  private HighlightStrategy mScanHighlighter;

  /** @param overlayController The controller for the overlay on which to present options */
  public OptionManager(OverlayController overlayController, FeedbackController feedbackController) {
    mOverlayController = overlayController;
    mFeedbackController = feedbackController;
    Context context = mOverlayController.getContext();
    SharedPreferences prefs = SharedPreferencesUtils.getSharedPreferences(context);
    onSharedPreferenceChanged(prefs, null);
    prefs.registerOnSharedPreferenceChangeListener(this);
  }

  /** Clean up when this object is no longer needed */
  public void shutdown() {
    if (mScanHighlighter != null) {
      mScanHighlighter.shutdown();
    }
    SharedPreferences prefs =
        SharedPreferencesUtils.getSharedPreferences(mOverlayController.getContext());
    prefs.unregisterOnSharedPreferenceChangeListener(this);
    if (mCurrentTreeRootNode != null) {
      mCurrentTreeRootNode.recycle();
    }
    mCurrentTreeRootNode = null;

    if (mBaseTreeRootNode != null) {
      mBaseTreeRootNode.recycle();
    }
    mBaseTreeRootNode = null;
  }

  /**
   * Clear any traversal in progress and use the new tree for future traversals
   *
   * @param newTreeRoot The root of the tree to traverse next
   */
  public synchronized void clearFocusIfNewTree(TreeScanNode newTreeRoot) {
    // If nothing changed, do nothing.
    if (mCurrentTreeRootNode == newTreeRoot) {
      return;
    }
    if (newTreeRoot != null && newTreeRoot.equals(mCurrentTreeRootNode)) {
      newTreeRoot.recycle();
      return;
    }

    // New tree is different from the current tree.
    clearFocus();

    if (mCurrentTreeRootNode != null) {
      if (mOverlayController.isMenuVisible()) {
        // If we just showed a menu, hold on to the base tree. We need it to be able to
        // perform actions on it.
        if (mBaseTreeRootNode == null) {
          mBaseTreeRootNode = mCurrentTreeRootNode;
        }
      } else {
        // If no menu is being shown, mCurrentTreeRootNode will hold the current base tree,
        // so mBaseTreeRootNode can be set to null.
        mCurrentTreeRootNode.recycle();
        if (mBaseTreeRootNode != null) {
          mBaseTreeRootNode.recycle();
          mBaseTreeRootNode = null;
        }
      }
    }

    mCurrentTreeRootNode = newTreeRoot;

    // Automatically focus the next item if autoscanning is enabled
    if (mStartScanAutomatically) {
      selectOption(0);
      // Automatically start scanning if enabled
      for (int i = 0; i < mOptionManagerListeners.size(); i++) {
        OptionManagerListener listener = mOptionManagerListeners.get(i);
        listener.onOptionManagerStartedAutoScan();
      }
    }
  }

  /**
   * Traverse to the child node of the current node that has the specified index and take whatever
   * action is appropriate for that node. If nothing currently has focus, any option moves to the
   * root of the tree.
   *
   * @param optionIndex The index of the child to traverse to. Out-of-bounds indices, such as
   *     negative values or those above the index of the last child, cause focus to be reset.
   */
  public void selectOption(int optionIndex) {
    selectOption(optionIndex, SystemClock.uptimeMillis());
  }

  /**
   * Traverse to the child node of the current node that has the specified index and take whatever
   * action is appropriate for that node. If nothing currently has focus, any option moves to the
   * root of the tree.
   *
   * @param optionIndex The index of the child to traverse to. Out-of-bounds indices, such as
   *     negative values or those above the index of the last child, cause focus to be reset.
   * @param eventTime The time at which the selection occurred, relative to {@link
   *     SystemClock#uptimeMillis}
   */
  public void selectOption(int optionIndex, long eventTime) {
    if (optionIndex < 0) {
      clearFocus();
      return;
    }

    if (mNomonClocksEnabled) {
      optionIndex = ((NomonClockHighlighter) getHighlighter()).getActiveClockGroup(eventTime);
    }
    /* Move to desired node */
    if (mCurrentNode == null) {
      if (mScanListener != null) {
        mScanListener.onScanStart();
      }
      mCurrentNode = mCurrentTreeRootNode;
      if (mCurrentNode == null) {
        return;
      }
    } else {
      if (!(mCurrentNode instanceof TreeScanSelectionNode)) {
        /* This should never happen */
        clearFocus();
        return;
      }
      TreeScanSelectionNode selectionNode = (TreeScanSelectionNode) mCurrentNode;
      if (optionIndex >= selectionNode.getChildCount()) {
        // User pressed an option-scan switch for an index greater than this node's order
        if (mScanListener != null) {
          mScanListener.onScanCompletedWithNoSelection();
        }
        clearFocus();
        return;
      }
      mCurrentNode = selectionNode.getChild(optionIndex);
    }

    onNodeFocused();
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
   */
  public void moveToParent(boolean wrap) {
    if (mCurrentNode != null) {
      mCurrentNode = mCurrentNode.getParent();
      if (mCurrentNode == null) {
        clearFocus();
      } else {
        onNodeFocused();
      }
      return;
    } else if (!wrap) {
      return;
    }

    mCurrentNode = findLastSelectionNode();
    if (mCurrentNode == null) {
      clearFocus();
    } else {
      onNodeFocused();
    }
  }

  /**
   * Register a listener to be notified when focus is cleared
   *
   * @param optionManagerListener A listener that should be called when focus is cleared
   */
  public void addOptionManagerListener(OptionManagerListener optionManagerListener) {
    mOptionManagerListeners.add(optionManagerListener);
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
        }
        compat.recycle();
        return;
      }
      SwitchAccessNodeCompat parent = compat.getParent();
      compat.recycle();
      compat = parent;
    }
  }

  @Override
  public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
    Context context = mOverlayController.getContext();

    boolean optionScanningEnabledNewValue =
        SwitchAccessPreferenceActivity.isOptionScanningEnabled(context);
    boolean nomonClocksEnabledNewValue =
        SwitchAccessPreferenceActivity.areNomonClocksEnabled(context);
    if (optionScanningEnabledNewValue || nomonClocksEnabledNewValue) {
      if ((mOptionScanningEnabled != optionScanningEnabledNewValue)
          || (mNomonClocksEnabled != nomonClocksEnabledNewValue)) {
        mScanHighlighter = null;
      }
    }
    mOptionScanningEnabled = optionScanningEnabledNewValue;
    mNomonClocksEnabled = nomonClocksEnabledNewValue;

    mOptionPaintArray = SwitchAccessPreferenceActivity.getHighlightPaints(context);
    /*
     * Always configure element 0 based on preferences. Only configure the others if we're
     * option scanning.
     */
    if (!shouldShowMultipleGroups()) {
      for (int i = 1; i < mOptionPaintArray.length; ++i) {
        mOptionPaintArray[i].setColor(Color.TRANSPARENT);
      }
    }
    mStartScanAutomatically =
        prefs.getBoolean(context.getString(R.string.switch_access_auto_start_scan_key), false);

    mIsSpokenFeedbackEnabled = SwitchAccessPreferenceActivity.isSpokenFeedbackEnabled(context);
    mSpeakFirstAndLastItem = SwitchAccessPreferenceActivity.shouldSpeakFirstAndLastItem(context);
    mSpeakNumberOfItems = SwitchAccessPreferenceActivity.shouldSpeakNumberOfItems(context);
    mSpeakAllItems = SwitchAccessPreferenceActivity.shouldSpeakAllItems(context);
  }

  /**
   * Register a listener to notify of auto-scan activity
   *
   * @param listener the listener to be set
   */
  public void setScanListener(ScanListener listener) {
    mScanListener = listener;
  }

  /**
   * Gets the HighlightStrategy used for the current scanning method, or creates one if none exists.
   */
  private HighlightStrategy getHighlighter() {
    if (mScanHighlighter == null) {
      if (mNomonClocksEnabled) {
        mScanHighlighter = new NomonClockHighlighter(mOverlayController);
      } else {
        mScanHighlighter = new OptionScanHighlighter(mOverlayController);
      }
    }
    return mScanHighlighter;
  }

  /**
   * Determines whether the user has selected a scanning method that requires highlighting multiple
   * groups at once.
   *
   * @return {@code true} if Option scanning or Nomon clocks are enabled.
   */
  private boolean shouldShowMultipleGroups() {
    return (mOptionScanningEnabled || mNomonClocksEnabled);
  }

  private void clearFocus() {
    mFeedbackController.stop();
    mCurrentNode = null;
    mOverlayController.clearHighlightOverlay();
    mOverlayController.clearMenuButtonOverlay();
    for (OptionManagerListener listener : mOptionManagerListeners) {
      listener.onOptionManagerClearedFocus();
    }
  }

  private void onNodeFocused() {
    if (mScanListener != null) {
      if (mCurrentNode instanceof ClearFocusNode) {
        mScanListener.onScanCompletedWithNoSelection();
      } else if (mCurrentNode instanceof TreeScanLeafNode) {
        mScanListener.onScanSelection();
      } else {
        mScanListener.onScanFocusChanged();
      }
    }

    if (mCurrentNode instanceof TreeScanLeafNode) {
      TreeScanLeafNode currentActionNode = (TreeScanLeafNode) mCurrentNode;

      // If the current node is a non-actionable node, then, when a user presses the "Select"
      // switch, the only action we could reasonably perform is clearing focus. However, if a user
      // pressed "Select", it is more likely that the user is trying to perform an action on a
      // nearby element (e.g. the next element) than trying to clear focus. Further, clearing focus
      // would force the user to start the selection process from the beginning. Therefore, when a
      // user presses "Select" on a non-actionable item, Switch Access does nothing.
      if (mCurrentNode instanceof NonActionableItemNode) {
        mCurrentNode = mCurrentNode.getParent();
      } else {
        // If the current node is an actionable node, perform actions or present the menu items.
        List<MenuItem> menuItems = currentActionNode.performActionOrGetMenuItems();
        if (!menuItems.isEmpty()) {
          if (menuItems.size() == 1) {
            menuItems.get(0).getOnClickListener().onClick(null);
          } else {
            mOverlayController.drawMenu(menuItems);
          }
        }

        clearFocus();
      }
    } else if (mCurrentNode instanceof TreeScanSelectionNode) {
      mOverlayController.clearHighlightOverlay();
      // Draw the Global Menu button
      mOverlayController.drawMenuButtonIfMenuNotVisible();
      // Speak relevant text, if enabled
      if (mIsSpokenFeedbackEnabled) {
        mFeedbackController.speak(
            FeedbackUtils.getSpeakableTextForTree(
                mOverlayController.getContext(),
                (TreeScanSelectionNode) mCurrentNode,
                shouldShowMultipleGroups(),
                mSpeakFirstAndLastItem,
                mSpeakNumberOfItems,
                mSpeakAllItems));
      }
      // showSelections() needs to know the location of the button in the screen to highlight
      // it. Hence run it a handler to give the thread a chance to draw the overlay.
      final TreeScanSelectionNode selectionNode = (TreeScanSelectionNode) mCurrentNode;
      new Handler()
          .post(
              new Runnable() {
                @Override
                public void run() {
                  selectionNode.showSelections(getHighlighter(), mOptionPaintArray);
                }
              });
    } else {
      clearFocus();
    }
  }

  /*
   * Find exactly one {@code SwitchAccessNodeCompat} in the current tree
   * @return an {@code obtain}ed SwitchAccessNodeCompat if there is exactly one in the
   * current tree. Returns {@code null} otherwise.
   */
  private SwitchAccessNodeCompat findCurrentlyActiveNode() {
    if (!(mCurrentNode instanceof TreeScanSelectionNode)) {
      return null;
    }
    TreeScanNode startNode = ((TreeScanSelectionNode) mCurrentNode).getChild(OPTION_INDEX_CLICK);
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

  private TreeScanNode findLastSelectionNode() {
    TreeScanNode newNode = mCurrentTreeRootNode;
    if (!(newNode instanceof TreeScanSelectionNode)) {
      return null;
    }
    TreeScanNode possibleNewNode = ((TreeScanSelectionNode) newNode).getChild(OPTION_INDEX_NEXT);
    while (possibleNewNode instanceof TreeScanSelectionNode) {
      newNode = possibleNewNode;
      possibleNewNode = ((TreeScanSelectionNode) newNode).getChild(OPTION_INDEX_NEXT);
    }
    return newNode;
  }

  /** Interface to monitor when focus is cleared */
  public interface OptionManagerListener {
    /** Called when scanning is automatically started */
    void onOptionManagerStartedAutoScan();

    /** Called when focus clears */
    void onOptionManagerClearedFocus();
  }

  /** Interface to monitor the user's progress of scanning to desired items */
  public interface ScanListener {
    /** Called when scanning starts and the first highlighting is drawn */
    void onScanStart();
    /** Called when scanning reaches a new selection node and highlighting changes */
    void onScanFocusChanged();
    /** Called when scanning reaches an action node and an action is taken */
    void onScanSelection();
    /** Called when scanning completes without any action being taken */
    void onScanCompletedWithNoSelection();
  }
}
