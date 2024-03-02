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

package com.google.android.accessibility.talkback.actor.search;

import static com.google.android.accessibility.talkback.Feedback.Focus.Action.CACHE;
import static com.google.android.accessibility.talkback.Feedback.Focus.Action.RESTORE_ON_NEXT_WINDOW;
import static com.google.android.accessibility.talkback.actor.TalkBackUIActor.Type.GESTURE_ACTION_OVERLAY;
import static com.google.android.accessibility.talkback.actor.TalkBackUIActor.Type.SELECTOR_ITEM_ACTION_OVERLAY;
import static com.google.android.accessibility.talkback.actor.TalkBackUIActor.Type.SELECTOR_MENU_ITEM_OVERLAY_MULTI_FINGER;
import static com.google.android.accessibility.talkback.actor.TalkBackUIActor.Type.SELECTOR_MENU_ITEM_OVERLAY_SINGLE_FINGER;
import static com.google.android.accessibility.utils.AccessibilityWindowInfoUtils.WINDOW_ID_NONE;
import static com.google.android.accessibility.utils.Performance.EVENT_ID_UNTRACKED;

import android.content.Context;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.ResultReceiver;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.text.Editable;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.style.LocaleSpan;
import android.text.style.StyleSpan;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup.LayoutParams;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageButton;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import com.google.android.accessibility.talkback.Feedback;
import com.google.android.accessibility.talkback.Feedback.Speech;
import com.google.android.accessibility.talkback.Feedback.TalkBackUI;
import com.google.android.accessibility.talkback.Pipeline;
import com.google.android.accessibility.talkback.R;
import com.google.android.accessibility.talkback.TalkBackService;
import com.google.android.accessibility.talkback.actor.search.SearchState.MatchedNodeInfo;
import com.google.android.accessibility.talkback.actor.search.StringMatcher.MatchResult;
import com.google.android.accessibility.talkback.focusmanagement.NavigationTarget;
import com.google.android.accessibility.talkback.labeling.TalkBackLabelManager;
import com.google.android.accessibility.utils.AccessibilityNode;
import com.google.android.accessibility.utils.AccessibilityNodeInfoUtils;
import com.google.android.accessibility.utils.AccessibilityServiceCompatUtils;
import com.google.android.accessibility.utils.AccessibilityWindow;
import com.google.android.accessibility.utils.Filter;
import com.google.android.accessibility.utils.FocusFinder;
import com.google.android.accessibility.utils.Performance.EventId;
import com.google.android.accessibility.utils.Role;
import com.google.android.accessibility.utils.SharedPreferencesUtils;
import com.google.android.accessibility.utils.output.FeedbackItem;
import com.google.android.accessibility.utils.output.ScrollActionRecord;
import com.google.android.accessibility.utils.output.SpeechController;
import com.google.android.accessibility.utils.traversal.OrderedTraversalStrategy;
import com.google.android.accessibility.utils.traversal.TraversalStrategy;
import com.google.android.accessibility.utils.traversal.TraversalStrategyUtils;
import java.lang.Character.UnicodeBlock;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import org.checkerframework.checker.nullness.qual.NonNull;

/**
 * This class provide a overlay window to show screen search UI. It also implement SearchObserver
 * interface for SearchScreenNodeStrategy to callback. Screen mockup REFERTO.
 */
public class SearchScreenOverlay implements SearchObserver {

  public static final String SEARCH = "SEARCH"; // Scroll source id

  /** The delay in milliseconds for focusable node finder. */
  static final int DELAY_FIND_NODE_MILLISEC = 50;

  /** The delay in milliseconds for scrolling action. */
  private static final int DELAY_SCROLL_MILLISEC = 400;

  /** The delay in milliseconds for speaking hint. */
  private static final int DELAY_SPEAK_HINT = 1000;

  /**
   * The delay time for accessibility service to generate the accessibility node info after
   * softInput hidden window change.
   */
  private static final int IME_DELAY_MILLISEC = 500;

  /* Filter for skipping SeekView. */
  private static final Filter<AccessibilityNodeInfoCompat> FILTER_NO_SEEK_BAR =
      new Filter<AccessibilityNodeInfoCompat>() {
        @Override
        public boolean accept(AccessibilityNodeInfoCompat node) {
          return node != null && Role.getRole(node) != Role.ROLE_SEEK_CONTROL;
        }
      };

  /** The parent context. */
  private final TalkBackService service;

  // UI elements.
  private SearchScreenOverlayLayout overlayPanel;
  private EditText keywordEditText;
  private ImageButton clearInputButton;
  private ImageButton cancelButton;
  private ImageButton prevScreenButton;
  private ImageButton nextScreenButton;
  private RecyclerView searchResultList;
  private Handler hintHandler;

  /** Adapter of search result. */
  private SearchAdapter searchStateAdapter;

  /** Search strategy for keyword searching. */
  private final SearchScreenNodeStrategy searchStrategy;

  /** Search state obtained from SearchStrategy. */
  SearchState searchState;

  /** Instance for finding accessibility/input focus. */
  private final FocusFinder focusFinder;

  /** The AccessibilityWindow that was focused before entering search mode. */
  @Nullable private AccessibilityWindow initialFocusedWindow = null;

  /** The {@code AccessibilityNode} containing accessibility focus in the base window. */
  private AccessibilityNode initialFocusedNode;

  /** The scroll actors used for previous/next screen. */
  private Pipeline.FeedbackReturner pipeline;

  /** Caches the ttsOverlay config before showing screen search overlay. */
  private boolean ttsOverlayWasOn = false;

  /** Callback to run when scroll succeeds or fails. */
  private interface AutoScrollCallback {
    void onAutoScrolled(AccessibilityNode scrolledNode, EventId eventId);

    void onAutoScrollFailed(AccessibilityNode nodeToScroll);
  }

  @Nullable private AutoScrollCallback scrollCallback;

  /** Defines functional interface. An action called by hideImeAndPerformAction() */
  private interface Action {
    void act();
  }

  /**
   * Constructs a new screen search overlay.
   *
   * @param service the parent service
   * @param focusFinder find input/accessibility focus
   * @param labelManager the custom label manager
   */
  public SearchScreenOverlay(
      TalkBackService service, FocusFinder focusFinder, TalkBackLabelManager labelManager) {
    this.service = service;
    this.focusFinder = focusFinder;
    this.hintHandler = new Handler();

    // Create search strategy object.
    searchStrategy = new SearchScreenNodeStrategy(this, labelManager);
  }
  /** pipeline the actors which need to perform scroll event */
  public void setPipeline(Pipeline.FeedbackReturner pipeline) {
    this.pipeline = pipeline;
  }
  /** Creates search overlay window and necessary widgets. */
  private void createUIElements() {
    WindowManager wm = (WindowManager) service.getSystemService(Context.WINDOW_SERVICE);

    LayoutInflater layoutInflater =
        (LayoutInflater) service.getSystemService(Context.LAYOUT_INFLATER_SERVICE);

    // Create window parameters.
    final WindowManager.LayoutParams parameters = new WindowManager.LayoutParams();
    parameters.setTitle(service.getString(R.string.title_screen_search));
    parameters.type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY;
    parameters.flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL;
    parameters.format = PixelFormat.TRANSLUCENT;
    parameters.width = LayoutParams.MATCH_PARENT;
    parameters.height = LayoutParams.MATCH_PARENT;
    parameters.softInputMode =
        WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
            | WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE;

    // Find widgets.
    overlayPanel =
        (SearchScreenOverlayLayout) layoutInflater.inflate(R.layout.screen_search_dialog, null);
    keywordEditText = overlayPanel.findViewById(R.id.keyword_edit);
    clearInputButton = overlayPanel.findViewById(R.id.clear_keyword);
    cancelButton = overlayPanel.findViewById(R.id.cancel_search);
    prevScreenButton = overlayPanel.findViewById(R.id.previous_screen);
    nextScreenButton = overlayPanel.findViewById(R.id.next_screen);
    searchResultList = overlayPanel.findViewById(R.id.search_result);

    searchResultList.setLayoutManager(new LinearLayoutManager(service));
    searchStateAdapter = new SearchAdapter();
    searchStateAdapter.setOnViewHolderClickListener(
        clickedView -> {
          searchResultList.setClickable(false);
          hideImeAndPerformAction(
              () -> postPerformFocusNode(searchResultList.getChildLayoutPosition(clickedView)));
        });
    searchResultList.setAdapter(searchStateAdapter);

    keywordEditText.addTextChangedListener(
        new TextWatcher() {
          String previousKeyword;

          @Override
          public void afterTextChanged(Editable s) {
            String keyword = keywordEditText.getText().toString().trim();
            if (TextUtils.equals(keyword, previousKeyword)) {
              return;
            }

            if (!keyword.isEmpty()) {
              // Do keyword search.
              searchStrategy.searchKeyword(keyword);
            } else {
              clearSearchResult();
            }

            previousKeyword = keyword;
          }

          @Override
          public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            // Do nothing.
          }

          @Override
          public void onTextChanged(CharSequence s, int start, int before, int count) {
            // Do nothing.
          }
        });

    // Need to manually hide the soft input otherwise if user uses enter key to move focus to other
    // UI elements, the soft input won't be closed and will cause issues.
    keywordEditText.setOnFocusChangeListener(
        (view, hasInputFocus) -> {
          InputMethodManager inputMgr =
              (InputMethodManager) service.getSystemService(Context.INPUT_METHOD_SERVICE);
          if (hasInputFocus) {
            inputMgr.showSoftInput(keywordEditText, InputMethodManager.SHOW_IMPLICIT);
          } else {
            inputMgr.hideSoftInputFromWindow(
                keywordEditText.getWindowToken(), InputMethodManager.HIDE_IMPLICIT_ONLY);
          }
        });

    keywordEditText.setOnEditorActionListener(
        (view, actionId, keyEvent) -> {
          if (actionId == EditorInfo.IME_ACTION_SEARCH) {
            performSearch();
            return true;
          }
          return false;
        });

    clearInputButton.setOnClickListener((view) -> keywordEditText.getText().clear());

    cancelButton.setOnClickListener((view) -> hide());

    overlayPanel.setOnKeyListener(this::onKey);

    prevScreenButton.setOnClickListener(
        (view) ->
            hideImeAndPerformAction(
                () -> scrollScreen(AccessibilityNodeInfoCompat.ACTION_SCROLL_BACKWARD)));

    nextScreenButton.setOnClickListener(
        (view) ->
            hideImeAndPerformAction(
                () -> scrollScreen(AccessibilityNodeInfoCompat.ACTION_SCROLL_FORWARD)));

    // Add to window manager.
    wm.addView(overlayPanel, parameters);
  }

  /** Returns the window id contains initial focus before searching. */
  public int getInitialFocusedWindowId() {
    return initialFocusedWindow == null ? WINDOW_ID_NONE : initialFocusedWindow.getId();
  }

  private void hideImeAndPerformAction(Action action) {
    InputMethodManager inputMgr =
        (InputMethodManager) service.getSystemService(Context.INPUT_METHOD_SERVICE);

    ResultReceiver imeResultReceiver =
        new ResultReceiver(new Handler()) {
          @Override
          protected void onReceiveResult(int resultCode, Bundle resultData) {
            action.act();
          }
        };

    if (!inputMgr.hideSoftInputFromWindow(
        keywordEditText.getWindowToken(), InputMethodManager.HIDE_NOT_ALWAYS, imeResultReceiver)) {
      // ResultReceiver is not executed, perform the desired action manually.
      action.act();
    }
  }

  /**
   * Performs search action which will hide the IME to refresh the node cache and then searches for
   * current keyword again.
   */
  private void performSearch() {
    hideImeAndPerformAction(
        () ->
            // Delay the cache action for a period of time to ensure that the nodes info is ready to
            // use after the window change triggered by softInput hidden action.
            new Handler()
                .postDelayed(
                    () -> {
                      searchStrategy.cacheNodeTree(initialFocusedWindow);
                      searchStrategy.searchKeyword(keywordEditText.getText());
                    },
                    IME_DELAY_MILLISEC));
  }

  /**
   * Gets the node from the matching list by position and visit all its ancestors, including itself,
   * to find the first visible and focusable target node. And perform focus on the target node. If
   * not found, post-delay some time to try again until reaching the retry limit.
   *
   * <p>REFERTO: [Screen Search] The nodes, which are covered by software IME, are
   * invisible at the moment of closing IME.
   *
   * @param position the index value in the matched list.
   */
  private void postPerformFocusNode(int position) {
    final AccessibilityNode clickedNode = searchState.getResult(position).node();

    if (clickedNode.isWebContainer()) {
      // Since we are able to find nodes outside of current screen for webView, we need to
      // ensure the selected node is in current screen so we could focus on it.
      clickedNode.showOnScreen(EVENT_ID_UNTRACKED);
    }

    // Override the restore focus state for the target node.
    pipeline.returnFeedback(EVENT_ID_UNTRACKED, Feedback.focus(RESTORE_ON_NEXT_WINDOW));

    // Close search UI.
    hide();

    final Handler handler = new Handler();
    handler.postDelayed(
        new Runnable() {
          int counter = 0;
          int counterLimit = 20;
          // The clickedNode is one of the nodes in the node cache.
          @Override
          public void run() {
            // Finding clickNodes's matching ancestor or self.
            AccessibilityNode focusableVisibleNode =
                clickedNode.getSelfOrMatchingAncestor(
                    AccessibilityNodeInfoUtils.FILTER_SHOULD_FOCUS);

            if (focusableVisibleNode != null) {
              // Cache the matching node as the focus target then restore focus when the window
              // state is stable.
              pipeline.returnFeedback(
                  EVENT_ID_UNTRACKED,
                  Feedback.focus(CACHE).setTarget(focusableVisibleNode.getCompat()));
            } else {
              // Can not found a focusable visible node, post-delay to try again later or set as
              // the clicked node itself if exceed maximum retry.
              if (counter < counterLimit) {
                counter++;
                handler.postDelayed(this, DELAY_FIND_NODE_MILLISEC);
              } else {
                // Cache the clicked node as the focus target then restore focus when the window
                // state is stable.
                pipeline.returnFeedback(
                    EVENT_ID_UNTRACKED, Feedback.focus(CACHE).setTarget(clickedNode.getCompat()));
              }
            }
          }
        },
        DELAY_FIND_NODE_MILLISEC);
  }

  /**
   * Performs scroll action on the scrollable node of {@link #initialFocusedWindow} except SeekBar
   * because there is no text that can be searched.
   *
   * @param action The accessibility scroll action. Should be {@link
   *     AccessibilityNodeInfoCompat#ACTION_SCROLL_BACKWARD} or {@link
   *     AccessibilityNodeInfoCompat#ACTION_SCROLL_FORWARD}.
   */
  private boolean scrollScreen(int action) {
    if (action != AccessibilityNodeInfoCompat.ACTION_SCROLL_BACKWARD
        && action != AccessibilityNodeInfoCompat.ACTION_SCROLL_FORWARD) {
      return false;
    }

    AccessibilityNode scrollableNode = getScrollableNode(action);
    if (scrollableNode == null) {
      refreshUiState();
      return false;
    }

    // Check if we need to update focused node after scrolling, the condition is copied from
    // FocusProcessorForLogicalNavigation.performScrollActionInternal()
    boolean needToUpdateFocus =
        (initialFocusedNode != null) && initialFocusedNode.hasAncestor(scrollableNode);

    scrollCallback =
        new AutoScrollCallback() {
          @Override
          public void onAutoScrolled(AccessibilityNode scrolledNode, EventId eventId) {
            if (needToUpdateFocus) {
              // Need to update focus node to get next correct scrollable node.
              // REFERTO.
              onScrolledWithFocusUpdate(scrolledNode, action);
            } else {
              onScrolled();
            }
          }

          @Override
          public void onAutoScrollFailed(AccessibilityNode nodeToScroll) {
            searchStrategy.cacheNodeTree(initialFocusedWindow);
            // Search again since the result was already cleared before scrolling start.
            searchStrategy.searchKeyword(keywordEditText.getText().toString());
            // Updates the scroll button state.
            refreshUiState();
          }
        };

    boolean result = false;

    if (pipeline != null) {
      pipeline.returnFeedback(
          EVENT_ID_UNTRACKED,
          Feedback.scroll(scrollableNode, ScrollActionRecord.ACTION_AUTO_SCROLL, action, SEARCH));
    }
    if (result) {
      // Screen is scrolled, clears previous search results.
      clearSearchResult();
    } else {
      // User clicks on the scroll button but the scrolling doesn't happen. Refresh button's state
      // to see if they should be disabled.
      refreshUiState();
    }

    return result;
  }

  /** Handles scroll success. */
  public void onAutoScrolled(@NonNull AccessibilityNode scrolledNode, EventId eventId) {
    if (scrollCallback != null) {
      scrollCallback.onAutoScrolled(scrolledNode, eventId);
      scrollCallback = null;
    }
  }

  /** Handles scroll failure. */
  public void onAutoScrollFailed(@NonNull AccessibilityNode scrolledNode) {
    if (scrollCallback != null) {
      scrollCallback.onAutoScrollFailed(scrolledNode);
      scrollCallback = null;
    }
  }

  /** Returns a scrollable node from input window except SeekBar. */
  @Nullable
  private AccessibilityNode getScrollableNode(int action) {
    AccessibilityNode scrollableNode = getScrollableNodeByFocusedNode(action);
    if (scrollableNode == null) {
      scrollableNode = getScrollableNodeByWindow(action);
    }

    return scrollableNode;
  }

  /** Returns a scrollable node from focused node except SeekBar. */
  @Nullable
  private AccessibilityNode getScrollableNodeByFocusedNode(int action) {
    if (initialFocusedNode == null) {
      return null;
    }

    return initialFocusedNode.getSelfOrMatchingAncestor(getScrollFilter(action));
  }

  /** Returns a scrollable node from focused window except SeekBar. */
  @Nullable
  private AccessibilityNode getScrollableNodeByWindow(int action) {
    if (initialFocusedWindow == null) {
      return null;
    }
    AccessibilityNode rootNode = initialFocusedWindow.getRoot();

    if (rootNode == null) {
      return null;
    }
    return rootNode.searchFromBfs(getScrollFilter(action));
  }

  // Clear search result adapter and notify UI element.
  private void clearSearchResult() {
    searchResultList.setClickable(false);
    searchStateAdapter.clearAndNotify();
  }

  private boolean onKey(View view, int keyCode, KeyEvent keyEvent) {
    switch (keyCode) {
      case KeyEvent.KEYCODE_BACK:
        hide();
        return true;
      case KeyEvent.KEYCODE_ENTER:
        // TODO: Add test cases for this.
        if (!keywordEditText.isFocused()) {
          return false;
        }

        if (keyEvent.getAction() == KeyEvent.ACTION_DOWN) {
          performSearch();
        }
        return true;
      default:
        return false;
    }
  }

  private void onScrolled() {
    // Delay a small amount of time to make sure all the nodes have been updated before we access
    // them.
    new Handler()
        .postDelayed(
            () -> {
              searchStrategy.cacheNodeTree(initialFocusedWindow);
              searchStrategy.searchKeyword(keywordEditText.getText().toString());
              refreshUiState();
            },
            DELAY_SCROLL_MILLISEC);
  }

  private void onScrolledWithFocusUpdate(AccessibilityNode scrolledNode, int scrollAction) {
    // Delay a small amount of time to make sure all the nodes have been updated before we access
    // them.
    new Handler()
        .postDelayed(
            () -> {
              searchStrategy.cacheNodeTree(initialFocusedWindow);
              searchStrategy.searchKeyword(keywordEditText.getText().toString());
              updateFocusedNodeAfterScrolled(scrolledNode, scrollAction);
              refreshUiState();
            },
            DELAY_SCROLL_MILLISEC);
  }

  private void refreshUiState() {
    updateButtonState(prevScreenButton, AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD);
    updateButtonState(nextScreenButton, AccessibilityNodeInfo.ACTION_SCROLL_FORWARD);
  }

  /**
   * Updates the button's state according to the specified scroll action currently available.
   *
   * @param button button to be updated.
   * @param scrollAction scrollAction that will be used to determine if the button should be
   *     enabled.
   */
  private void updateButtonState(@Nullable ImageButton button, int scrollAction) {
    if (button == null) {
      return;
    }

    AccessibilityNode node = getScrollableNode(scrollAction);
    if (node == null) {
      // No node available to perform the action.
      if (button.isEnabled()) {
        disableImageButton(button);
      }
    } else {
      // The action is currently available.
      if (!button.isEnabled()) {
        enableImageButton(button);
      }
    }
  }

  /**
   * Extracts node texts from {@code searchState} and add them to searchStateAdapter. Nodes without
   * text will be removed and won't be added to adapter.
   *
   * @param searchState The searchState to process.
   */
  private void extractNodeTextToAdapter(SearchState searchState) {
    // TODO: Changes the data type to ImmutableList after confirming that AOSP TalkBack
    // could support guava lib
    List<CharSequence> result = new ArrayList<>();
    for (Iterator<MatchedNodeInfo> iterator = searchState.getResults().iterator();
        iterator.hasNext(); ) {
      MatchedNodeInfo matchedInfo = iterator.next();
      if (!matchedInfo.hasMatchedResult()) {
        iterator.remove();
        continue;
      }

      // Make the keyword bold before truncate, so even if the keyword is truncated the remaining
      // parts could still keep bold.
      CharSequence nodeText = makeAllKeywordBold(matchedInfo);
      // Get text surrounding the keyword.
      CharSequence surroundingText = truncateSurroundingWords(nodeText, matchedInfo);
      // Add valid node text to adapter to show in UI.
      result.add(surroundingText);
    }

    searchStateAdapter.setResultAndNotify(result);
  }

  /** Updates search result list by searchState. Call its dispose() before return. */
  @Override
  public void updateSearchState(SearchState searchState) {
    // Clear previous search result in adapter.
    clearSearchResult();

    // Extract descriptions from searchState and add to searchStateAdapter.
    extractNodeTextToAdapter(searchState);

    // Announce 'X' matches.
    int matches = searchState.getResults().size();
    String text =
        (matches > 0)
            ? service
                .getResources()
                .getQuantityString(R.plurals.msg_matches_found, matches, matches)
            : service.getResources().getString(R.string.msg_no_matches);

    speakHint(text);

    // The searchResultList will be set as unClickable when clicked, so we need to set it as
    // clickable when UI shows and the search result is not empty.
    searchResultList.setClickable(matches > 0);

    // Clear previous searchState.
    if (this.searchState != null) {
      this.searchState.clear();
    }

    // Keep searchState for setting focus when clicked, will dispose it when UI hide.
    this.searchState = searchState;
  }

  public void show() {
    // REFERTO. Because accessibility framework sent window changed events from
    // TextToSpeechOverlay and cause Screen search close, this is a work around not to show
    // TextToSpeechOverlay to lower the chance.
    if (Build.VERSION.SDK_INT == Build.VERSION_CODES.O_MR1
        || Build.VERSION.SDK_INT == Build.VERSION_CODES.O) {
      ttsOverlayWasOn =
          SharedPreferencesUtils.getBooleanPref(
              SharedPreferencesUtils.getSharedPreferences(service),
              service.getResources(),
              R.string.pref_tts_overlay_key,
              R.bool.pref_tts_overlay_default);
      if (ttsOverlayWasOn) {
        // Disable it if ttsOverlay was on before showing.
        SharedPreferencesUtils.putBooleanPref(
            SharedPreferencesUtils.getSharedPreferences(service),
            service.getResources(),
            R.string.pref_tts_overlay_key,
            false);
      }
      // Turns off quick menu overlays when search screen is showing on Android O.
      if (pipeline != null) {
        pipeline.returnFeedback(
            EVENT_ID_UNTRACKED,
            Feedback.talkBackUI(
                TalkBackUI.Action.NOT_SUPPORT, SELECTOR_MENU_ITEM_OVERLAY_SINGLE_FINGER));
        pipeline.returnFeedback(
            EVENT_ID_UNTRACKED,
            Feedback.talkBackUI(
                TalkBackUI.Action.NOT_SUPPORT, SELECTOR_MENU_ITEM_OVERLAY_MULTI_FINGER));
        pipeline.returnFeedback(
            EVENT_ID_UNTRACKED,
            Feedback.talkBackUI(TalkBackUI.Action.NOT_SUPPORT, SELECTOR_ITEM_ACTION_OVERLAY));
        pipeline.returnFeedback(
            EVENT_ID_UNTRACKED,
            Feedback.talkBackUI(TalkBackUI.Action.NOT_SUPPORT, GESTURE_ACTION_OVERLAY));
      }
    }

    // Updates initial focused window before overlay UI show up, for caching nodes info to search.
    findTargetWindow();

    if (overlayPanel == null) {
      createUIElements();
    }
    overlayPanel.setVisibility(View.VISIBLE);

    if (searchState != null) {
      searchState.clear();
    }

    // Cache the nodes on current window when user is about to search.
    searchStrategy.cacheNodeTree(initialFocusedWindow);

    // Searches using existing keyword if there is any in the EditText.
    String searchKeyword = keywordEditText.getText().toString();
    if (!TextUtils.isEmpty(searchKeyword)) {
      // Make text(s) as all selected.
      keywordEditText.selectAll();
      searchStrategy.searchKeyword(searchKeyword);
    }

    // Move focus to keyword edit field.
    keywordEditText.requestFocus();

    refreshUiState();
  }

  /**
   * When the configuration change has impact the UI elements, we force the original overlay panel
   * to null, so that it will be recreated when shows UI
   */
  public void invalidateUIElements() {
    overlayPanel = null;
  }

  public void hide() {
    if (ttsOverlayWasOn) {
      // Enable it if ttsOverlay was on before showing.
      SharedPreferencesUtils.putBooleanPref(
          SharedPreferencesUtils.getSharedPreferences(service),
          service.getResources(),
          R.string.pref_tts_overlay_key,
          true);
    }
    if (pipeline != null) {
      pipeline.returnFeedback(
          EVENT_ID_UNTRACKED,
          Feedback.talkBackUI(TalkBackUI.Action.SUPPORT, SELECTOR_MENU_ITEM_OVERLAY_SINGLE_FINGER));
      pipeline.returnFeedback(
          EVENT_ID_UNTRACKED,
          Feedback.talkBackUI(TalkBackUI.Action.SUPPORT, SELECTOR_MENU_ITEM_OVERLAY_MULTI_FINGER));
      pipeline.returnFeedback(
          EVENT_ID_UNTRACKED,
          Feedback.talkBackUI(TalkBackUI.Action.SUPPORT, SELECTOR_ITEM_ACTION_OVERLAY));
      pipeline.returnFeedback(
          EVENT_ID_UNTRACKED,
          Feedback.talkBackUI(TalkBackUI.Action.SUPPORT, GESTURE_ACTION_OVERLAY));
    }
    if (overlayPanel == null) {
      return;
    }

    overlayPanel.setVisibility(View.GONE);

    // Clear the cached nodes since user is done with current search.
    searchStrategy.clearCachedNodes();

    if (searchState != null) {
      searchState.clear();
    }

    setInitialFocusedNode(null);
  }

  ///////////////////////////////////////////////////////////////////////////////////////
  // Helper Methods

  /** Clears all formatting spans in {@code spannedString}. */
  static void clearFormattingSpans(SpannableStringBuilder spannedString) {
    Object[] allSpans = spannedString.getSpans(0, spannedString.length(), Object.class);
    for (Object span : allSpans) {
      if (isFormattingSpan(span)) {
        spannedString.removeSpan(span);
      }
    }
  }

  /** Disables the {@code button} and applies the grey-out effect. */
  private static void disableImageButton(@Nullable ImageButton button) {
    if (button == null) {
      return;
    }

    button.setEnabled(false);
    // Set focusable to false to prevent receiving focus.
    button.setFocusable(false);
    // Apply grey out effect.
    button.setImageAlpha(0x50);
  }

  /** Enables the {@code button} and removes the grey-out effect. */
  private static void enableImageButton(@Nullable ImageButton button) {
    if (button == null) {
      return;
    }

    button.setEnabled(true);
    // Set focusable to true to receive focus.
    button.setFocusable(true);
    // Remove grey out effect.
    button.setImageAlpha(0xFF);
  }

  int getOverlayId() {
    return overlayPanel.getOverlayId();
  }

  /**
   * Gets the filter to find the scrollable node according to the specified {@code action} except
   * SeekBar because there is no text that can be searched.
   *
   * @param action filter action to find the nodes, could be {@code
   *     AccessibilityNodeInfoUtils.FILTER_COULD_SCROLL_FORWARD} or {@code
   *     AccessibilityNodeInfoUtils.FILTER_COULD_SCROLL_BACKWARD}
   * @return the node filter or null if the specified action is not valid
   */
  @Nullable
  static Filter<AccessibilityNodeInfoCompat> getScrollFilter(int action) {
    if (action == AccessibilityNodeInfoCompat.ACTION_SCROLL_FORWARD) {
      return AccessibilityNodeInfoUtils.FILTER_COULD_SCROLL_FORWARD.and(FILTER_NO_SEEK_BAR);
    } else if (action == AccessibilityNodeInfoCompat.ACTION_SCROLL_BACKWARD) {
      return AccessibilityNodeInfoUtils.FILTER_COULD_SCROLL_BACKWARD.and(FILTER_NO_SEEK_BAR);
    } else {
      return null;
    }
  }

  /**
   * Makes all instances of {@code keyword} within {@code fullString} bold.
   *
   * @param matchedInfo the matchedNodeInfo containing the matched {@code AccessibilityNode} and
   *     {@code MatchResult}
   * @return the fullString with all contained keyword set as bold or null if node is not available
   */
  @Nullable
  static SpannableStringBuilder makeAllKeywordBold(@Nullable MatchedNodeInfo matchedInfo) {
    if (matchedInfo == null || TextUtils.isEmpty(matchedInfo.getNodeText())) {
      return null;
    }

    SpannableStringBuilder updatedString = new SpannableStringBuilder(matchedInfo.getNodeText());
    if (!matchedInfo.hasMatchedResult()) {
      return updatedString;
    }

    // Clear existing formatting spans since we only want the highlight span shown in the result
    // list.
    clearFormattingSpans(updatedString);

    for (MatchResult match : matchedInfo.matchResults()) {
      updatedString.setSpan(
          new StyleSpan(Typeface.BOLD),
          match.start(),
          match.end(),
          Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    return updatedString;
  }

  /** Checks if {@code span} is a formatting span or not. */
  private static boolean isFormattingSpan(Object span) {
    // We only keep LocaleSpan for now, could add more types if needed.
    if (span instanceof LocaleSpan) {
      return false;
    } else {
      return true;
    }
  }

  /** Determines if the specified character is a Japanese syllabary. */
  static boolean isJapaneseSyllabary(char c) {
    UnicodeBlock block = UnicodeBlock.of(c);
    return block != null
        && (block.equals(UnicodeBlock.HIRAGANA) || block.equals(UnicodeBlock.KATAKANA));
  }

  /**
   * Scans backward/forward until the beginning/end of text to find a CJKV
   * (Chinese,Japanese,Korean,Vietnamese) ideograph or Japanese syllabary.
   */
  static int scanForNonAlphabetic(CharSequence text, int fromIndex, boolean scanBackward) {
    int pos = fromIndex;
    while (scanBackward ? pos > 0 : pos < text.length()) {
      char c = text.charAt(pos);
      if (Character.isIdeographic(c) || isJapaneseSyllabary(c)) {
        // Found CJKV ideograph or Japanese syllabary, stop scanning. CJKV ideograph and
        // Japanese syllabary doesn't have word boundaries like English or other alphabetic
        // writing, they don't separate words with spaces. Since no space char found, we can
        // only truncate text in a specific length.
        pos = scanBackward ? pos + 1 : pos;
        break;
      }
      // This character is alphabetic writing, continue to scan backward/forward.
      pos = scanBackward ? pos - 1 : pos + 1;
    }
    return pos;
  }

  /**
   * Checks if this instance is actively handling a search.
   *
   * @return {@code true} if there is an active search, or {@code false} otherwise.
   */
  public boolean isVisible() {
    if (overlayPanel == null) {
      return false;
    }

    return (overlayPanel.getVisibility() == View.VISIBLE);
  }

  /** Checks if the hint announcement will be silenced or not. */
  private boolean shouldDelayHint() {
    return service.isSsbActiveAndHeadphoneOff();
  }

  private void speakHint(String text) {
    hintHandler.removeCallbacksAndMessages(null);
    if (pipeline != null) {
      // Use hint for confirming that the matching results will be announced after the editing
      // announcement and also not be interrupted by it.
      Feedback.Part.Builder builder =
          Feedback.Part.builder()
              .setSpeech(
                  Speech.builder()
                      .setAction(Speech.Action.SPEAK)
                      .setHintSpeakOptions(
                          SpeechController.SpeakOptions.create()
                              .setFlags(FeedbackItem.FLAG_NO_HISTORY))
                      .setHint(text)
                      .build());
      if (shouldDelayHint()) {
        hintHandler.postDelayed(() -> speakHint(text), DELAY_SPEAK_HINT);
      } else {
        pipeline.returnFeedback(Feedback.create(EVENT_ID_UNTRACKED, builder.build()));
      }
    }
  }

  private void updateFocusedNodeAfterScrolled(AccessibilityNode scrolledNode, int scrollAction) {
    // The focused node searching strategy is copied from
    // FocusProcessorForLogicalNavigation.handleViewScrolledForScrollNavigationAction to align
    // behavior with scrolling by gesture.
    // TODO: Try to refactor the logic into a shared method that could be used by both
    // FocusProcessorForLogicalNavigation and SearchScreenOverlay
    if (scrolledNode == null) {
      return;
    }

    int searchDirection = TraversalStrategyUtils.convertScrollActionToSearchDirection(scrollAction);

    TraversalStrategy traversalStrategy =
        scrolledNode.getTraversalStrategy(focusFinder, searchDirection);

    Filter<AccessibilityNodeInfoCompat> nodeFilter =
        NavigationTarget.createNodeFilter(
            NavigationTarget.TARGET_DEFAULT, traversalStrategy.getSpeakingNodesCache());

    AccessibilityNode nodeToFocus =
        scrolledNode.findFirstFocusInNodeTree(traversalStrategy, searchDirection, nodeFilter);
    setInitialFocusedNode(nodeToFocus);
  }

  /**
   * Finds the target window has a11y focus and update {@code initialFocusedWindow} and {@code
   * initialFocusedNode}. Otherwise, finds the active window.
   */
  private void findTargetWindow() {
    AccessibilityNodeInfoCompat rootNode = null;
    AccessibilityNodeInfoCompat currentNode =
        FocusFinder.getAccessibilityFocusNode(service, /* fallbackOnRoot= */ false);
    TraversalStrategy traversal = null;

    if (currentNode == null) {
      rootNode = AccessibilityServiceCompatUtils.getRootInActiveWindow(service);
      if (rootNode == null) {
        return;
      }
      traversal = new OrderedTraversalStrategy(rootNode);
      currentNode =
          TraversalStrategyUtils.searchFocus(
              traversal,
              rootNode,
              TraversalStrategy.SEARCH_FOCUS_FORWARD,
              AccessibilityNodeInfoUtils.FILTER_SHOULD_FOCUS);
    }

    if (currentNode == null) {
      return;
    }

    AccessibilityNode focusedNode = AccessibilityNode.takeOwnership(currentNode);
    setInitialFocusedNode(focusedNode);
    AccessibilityWindow currentFocusedWindow =
        AccessibilityWindow.takeOwnership(null, currentNode.getWindow());
    setInitialFocusedWindow(currentFocusedWindow);
  }

  ///////////////////////////////////////////////////////////////////////////////////////
  // VisibleForTesting methods

  @VisibleForTesting
  View getContentView() {
    return overlayPanel;
  }

  @VisibleForTesting
  void setInitialFocusedNode(AccessibilityNode node) {
    initialFocusedNode = node;
  }

  @VisibleForTesting
  void setInitialFocusedWindow(AccessibilityWindow window) {
    initialFocusedWindow = window;
  }

  /**
   * Returns a string that is a substring of text which surrounding the keyword without truncating
   * word in middle. If no such keyword occurs, then text is returned.
   *
   * <pre>
   * String text = "Google LLC is an American multinational technology company"
   * truncateSurroundingWords(text,"Google")  = "Google LLC is an American"
   * truncateSurroundingWords(text,"google")  = "Google LLC is an American"
   * truncateSurroundingWords(text,"company") = "multinational technology company"
   * truncateSurroundingWords(text,"tech")    = "American multinational technology company"
   * </pre>
   */
  @VisibleForTesting
  static CharSequence truncateSurroundingWords(
      CharSequence styledNodeText, MatchedNodeInfo matchedInfo) {
    // TODO: Refine this method when TPM / UX designer spec out the final UI.
    if (styledNodeText.length() == 0 || matchedInfo == null || !matchedInfo.hasMatchedResult()) {
      return styledNodeText;
    }

    // Truncate 20 characters after/before the keyword.
    int truncateLength = 20;
    if (styledNodeText.length() < truncateLength) {
      return styledNodeText;
    }

    MatchResult firstMatch = matchedInfo.matchResults().get(0);
    int startPos = firstMatch.start();
    int endPos = firstMatch.end() - 1;

    if (startPos > truncateLength) {
      // Find the index of space char before the keyword at least 'truncateLength' chars before the
      // keyword.
      startPos = startPos - truncateLength;
      int spaceIdx = styledNodeText.toString().lastIndexOf(' ', startPos);
      if (spaceIdx == -1) {
        // Found no space character.
        startPos = scanForNonAlphabetic(styledNodeText, startPos, true);
      } else {
        startPos = spaceIdx + 1;
      }
    } else {
      startPos = 0;
    }

    if (styledNodeText.length() - endPos < truncateLength) {
      endPos = styledNodeText.length();
    } else {
      // Find the index of space char after the keyword at least 'truncateLength' chars after the
      // keyword.
      endPos = endPos + truncateLength - 1;
      int spaceIdx = styledNodeText.toString().indexOf(' ', endPos);
      if (spaceIdx == -1) {
        // Found no space character.
        endPos = scanForNonAlphabetic(styledNodeText.toString(), endPos, false);
      } else {
        endPos = spaceIdx;
      }
    }

    return styledNodeText.subSequence(startPos, endPos);
  }
}
