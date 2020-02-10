/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.google.android.accessibility.talkback.screensearch;

import androidx.annotation.NonNull;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import android.text.TextUtils;
import com.google.android.accessibility.talkback.controller.DirectionNavigationActor;
import com.google.android.accessibility.talkback.labeling.CustomLabelManager;
import com.google.android.accessibility.talkback.screensearch.SearchState.MatchedNodeInfo;
import com.google.android.accessibility.talkback.screensearch.StringMatcher.MatchResult;
import com.google.android.accessibility.utils.AccessibilityNode;
import com.google.android.accessibility.utils.AccessibilityNodeInfoUtils;
import com.google.android.accessibility.utils.AccessibilityWindow;
import com.google.android.accessibility.utils.Filter;
import java.util.List;
import org.checkerframework.checker.nullness.qual.Nullable;

/** Searches keyword in screen nodes. */
public final class SearchScreenNodeStrategy {
  /** Observer instance which need to notify when search done. */
  @Nullable private SearchObserver observer;

  /** The custom label manager that is used for search screen node. */
  @Nullable private final CustomLabelManager labelManager;

  /** Stores last-searched keyword. */
  private @Nullable CharSequence lastKeyword;

  /** The cache for all searchable nodes on current screen. */
  private final ScreenNodesCache nodesCache;

  /**
   * Creates a new SearchScreenNodeStrategy instance.
   *
   * @param observer The Observer which need to be notified when search done.
   * @param labelManager The custom label manager, or {@code null} if the API version does not
   */
  public SearchScreenNodeStrategy(
      @Nullable SearchObserver observer, @Nullable CustomLabelManager labelManager) {
    this.observer = observer;
    this.labelManager = labelManager;
    this.nodesCache = new ScreenNodesCache();
  }

  /** Gets last-searched keyword. */
  public @Nullable CharSequence getLastKeyword() {
    return lastKeyword;
  }

  /** Runs search, notifies observer of results. */
  public void searchKeyword(CharSequence keyword) {
    SearchState searchState = search(keyword);
    if (observer != null) {
      observer.updateSearchState(searchState);
    }
  }

  /**
   * Searches {@code userInput} from the nodes cached in {@link
   * #cacheNodeTree(AccessibilityWindow)}. The search is case-insensitive and the leading/tailing
   * whitespaces in {@code userInput} will be trimmed before searching.
   *
   * @param userInput the input to be used for searching
   * @return SearchState containing the nodes in {@code SearchState.result}, the {@code
   *     SearchState.result} will be null if {@code userInput} is empty or contains only spaces.
   *     Caller must not recycle the nodes in {@code SearchState.result} after using
   */
  @NonNull
  protected SearchState search(@Nullable CharSequence userInput) {
    // Return a empty SearchState if keyword is not valid for search.
    if (TextUtils.isEmpty(userInput)) {
      return new SearchState();
    }

    // Return a empty SearchState if userInput contains spaces only.
    String trimmedUserInput = userInput.toString().trim();
    if (trimmedUserInput.isEmpty()) {
      return new SearchState();
    }

    lastKeyword = trimmedUserInput;

    // Get all matched nodes per window into a list.
    SearchState state = new SearchState();

    for (AccessibilityNode node : nodesCache.getCachedNodes()) {
      List<MatchResult> matchResults =
          StringMatcher.findMatches(node.getNodeText().toString(), trimmedUserInput);

      if (matchResults.size() > 0) {
        state.addResult(new MatchedNodeInfo(node, matchResults));
      }
    }

    return state;
  }

  /** Caches all the searchable nodes in currentWindow. Caller should recycle currentWindow. */
  void cacheNodeTree(@Nullable AccessibilityWindow currentWindow) {
    clearCachedNodes();

    nodesCache.cacheCurrentWindow(
        currentWindow,
        new Filter<AccessibilityNodeInfoCompat>() {
          @Override
          public boolean accept(AccessibilityNodeInfoCompat node) {
            // Only keep the visible nodes.
            if (!AccessibilityNodeInfoUtils.isVisible(node)) {
              return false;
            }

            // Keep the nodes with texts.
            CharSequence nodeText = CustomLabelManager.getNodeText(node, labelManager);
            return !TextUtils.isEmpty(nodeText);
          }
        });
  }

  void clearCachedNodes() {
    String caller = "SearchScreenNodeStrategy.clearCachedNodes()";
    nodesCache.clearCachedNodes(caller);
  }

  /**
   * Moves focus to next node after current focused-node, which matches target-keyword. Returns
   * success flag.
   */
  public boolean searchAndFocus(
      boolean startAtRoot,
      final @Nullable CharSequence target,
      DirectionNavigationActor directionNavigator) {

    // Clean and check target keyword.
    if (TextUtils.isEmpty(target)) {
      return false;
    }
    final String trimmedUserInput = target.toString().trim();
    if (trimmedUserInput.isEmpty()) {
      return false;
    }

    lastKeyword = trimmedUserInput;

    // Find node matching target keyword, and focus that node.
    return directionNavigator.searchAndFocus(
        startAtRoot,
        new Filter<AccessibilityNodeInfoCompat>() {
          @Override
          public boolean accept(AccessibilityNodeInfoCompat node) {
            if (node == null) {
              return false;
            }

            // Only keep the visible nodes.
            if (!AccessibilityNodeInfoUtils.isVisible(node)) {
              return false;
            }
            // Keep the nodes with texts.
            @Nullable CharSequence nodeText = CustomLabelManager.getNodeText(node, labelManager);
            if (TextUtils.isEmpty(nodeText)) {
              return false;
            }

            // Check for target-text match.
            List<MatchResult> matches =
                StringMatcher.findMatches(nodeText.toString(), trimmedUserInput);
            return (matches != null) && (matches.size() > 0);
          }
        });
  }
}
