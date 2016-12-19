/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.talkback.menurules;

import com.google.android.marvin.talkback.TalkBackService;

import android.content.Context;
import android.support.v4.os.BuildCompat;
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MenuItem.OnMenuItemClickListener;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;

import com.android.talkback.R;
import com.android.talkback.contextmenu.ContextMenuItem;
import com.android.talkback.contextmenu.ContextMenuItemBuilder;
import com.android.talkback.controller.CursorController;
import com.android.utils.AccessibilityNodeInfoUtils;
import com.android.utils.Role;
import com.android.utils.WindowManager;
import com.android.utils.traversal.TraversalStrategy;
import com.android.utils.traversal.TraversalStrategyUtils;

import java.util.LinkedList;
import java.util.List;

/**
 * Provides menu items for moving to the suggestions window attached to an autocomplete text view,
 * or for moving back to the original autocomplete text view that anchors the suggestions.
 */
public class RuleSuggestions implements NodeMenuRule {

    @Override
    public boolean accept(TalkBackService service, AccessibilityNodeInfoCompat node) {
        if (!BuildCompat.isAtLeastN()) {
            return false;
        }

        // Only accept for "Jump to suggestions" if we're in an edit field.
        if (Role.getRole(node) == Role.ROLE_EDIT_TEXT) {
            WindowManager windowManager = new WindowManager(service.isScreenLayoutRTL());
            windowManager.setWindows(service.getWindows());
            AccessibilityWindowInfo anchoredWindow = windowManager.getAnchoredWindow(node);
            if (anchoredWindow != null) {
                return true;
            }
        }

        // Only accept for "Return to edit field" if the anchor is an edit field.
        AccessibilityNodeInfoCompat anchor = AccessibilityNodeInfoUtils.getAnchor(node);
        if (anchor != null) {
            try {
                if (Role.getRole(anchor) == Role.ROLE_EDIT_TEXT) {
                    return true;
                }
            } finally {
                anchor.recycle();
            }
        }

        return false;
    }

    @Override
    public List<ContextMenuItem> getMenuItemsForNode(TalkBackService service,
            ContextMenuItemBuilder menuItemBuilder, AccessibilityNodeInfoCompat node) {
        final LinkedList<ContextMenuItem> items = new LinkedList<>();

        if (Role.getRole(node) == Role.ROLE_EDIT_TEXT) {
            WindowManager windowManager = new WindowManager(service.isScreenLayoutRTL());
            windowManager.setWindows(service.getWindows());
            AccessibilityWindowInfo anchoredWindow = windowManager.getAnchoredWindow(node);
            if (anchoredWindow != null) {
                final ContextMenuItem viewSuggestions = menuItemBuilder.createMenuItem(service,
                        Menu.NONE, R.id.suggestions_breakout_suggestions, Menu.NONE,
                        service.getString(R.string.title_suggestions_breakout_suggestions));
                viewSuggestions.setOnMenuItemClickListener(new ViewSuggestionsItemClickListener(
                        AccessibilityNodeInfoCompat.obtain(node), service));
                viewSuggestions.setSkipRefocusEvents(true);
                items.add(viewSuggestions);
            }
        }

        AccessibilityNodeInfoCompat anchor = AccessibilityNodeInfoUtils.getAnchor(node);
        if (anchor != null) {
            if (Role.getRole(anchor) == Role.ROLE_EDIT_TEXT) {
                final ContextMenuItem returnToAnchor = menuItemBuilder.createMenuItem(service,
                        Menu.NONE, R.id.suggestions_breakout_anchor, Menu.NONE,
                        service.getString(R.string.title_suggestions_breakout_anchor));
                returnToAnchor.setOnMenuItemClickListener(new ReturnToAnchorItemClickListener(
                        anchor, service));
                returnToAnchor.setSkipRefocusEvents(true);
                items.add(returnToAnchor);
                // Don't recycle anchor here because we gave it to the listener.
            } else {
                anchor.recycle();
            }
        }

        return items;
    }

    @Override
    public CharSequence getUserFriendlyMenuName(Context context) {
        return context.getString(R.string.title_suggestions_controls);
    }

    @Override
    public boolean canCollapseMenu() {
        return true;
    }

    private class ViewSuggestionsItemClickListener implements OnMenuItemClickListener {
        private AccessibilityNodeInfoCompat mAnchor;
        private final TalkBackService mService;

        public ViewSuggestionsItemClickListener(AccessibilityNodeInfoCompat anchor,
                TalkBackService service) {
            mAnchor = anchor;
            mService = service;
        }

        @Override
        public boolean onMenuItemClick(MenuItem item) {
            if (mAnchor != null) {
                WindowManager windowManager = new WindowManager(mService.isScreenLayoutRTL());
                windowManager.setWindows(mService.getWindows());
                AccessibilityWindowInfo anchoredWindow = windowManager.getAnchoredWindow(mAnchor);
                if (anchoredWindow != null) {
                    AccessibilityNodeInfoCompat firstNode = getFirstNode(anchoredWindow);
                    if (firstNode != null) {
                        CursorController cursorController = mService.getCursorController();
                        cursorController.setCursor(firstNode);
                        firstNode.recycle();
                    }
                }

                mAnchor.recycle();
                mAnchor = null;
            }
            return true;
        }

        private AccessibilityNodeInfoCompat getFirstNode(AccessibilityWindowInfo window) {
            AccessibilityNodeInfo root = window.getRoot();
            if (root != null) {
                AccessibilityNodeInfoCompat compatRoot = new AccessibilityNodeInfoCompat(root);
                TraversalStrategy traversalStrategy = TraversalStrategyUtils.getTraversalStrategy(
                        compatRoot, TraversalStrategy.SEARCH_FOCUS_FORWARD);

                AccessibilityNodeInfoCompat firstNode = AccessibilityNodeInfoUtils.searchFocus(
                        traversalStrategy,
                        compatRoot,
                        TraversalStrategy.SEARCH_FOCUS_FORWARD,
                        AccessibilityNodeInfoUtils.FILTER_SHOULD_FOCUS);

                compatRoot.recycle(); // This will also recycle the underlying node (root).
                return firstNode;
            }

            return null;
        }
    }

    private static class ReturnToAnchorItemClickListener implements OnMenuItemClickListener {
        private AccessibilityNodeInfoCompat mAnchor;
        private final TalkBackService mService;

        public ReturnToAnchorItemClickListener(AccessibilityNodeInfoCompat anchor,
                TalkBackService service) {
            mAnchor = anchor;
            mService = service;
        }

        @Override
        public boolean onMenuItemClick(MenuItem item) {
            if (mAnchor != null) {
                CursorController cursorController = mService.getCursorController();
                cursorController.setCursor(mAnchor);
                mAnchor.recycle();
                mAnchor = null;
            }

            return true;
        }
    }
}
