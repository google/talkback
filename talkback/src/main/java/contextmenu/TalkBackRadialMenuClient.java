/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.google.android.accessibility.talkback.contextmenu;

import static com.google.android.accessibility.utils.Performance.EVENT_ID_UNTRACKED;
import static com.google.android.accessibility.utils.input.InputModeManager.INPUT_MODE_TOUCH;
import static com.google.android.accessibility.utils.output.SpeechController.QUEUE_MODE_FLUSH_ALL;
import static com.google.android.accessibility.utils.output.SpeechController.QUEUE_MODE_QUEUE;
import static com.google.android.accessibility.utils.traversal.TraversalStrategy.SEARCH_FOCUS_BACKWARD;
import static com.google.android.accessibility.utils.traversal.TraversalStrategy.SEARCH_FOCUS_FORWARD;

import android.content.Context;
import android.os.Handler;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import android.view.MenuInflater;
import android.view.MenuItem;
import com.google.android.accessibility.talkback.Feedback;
import com.google.android.accessibility.talkback.Pipeline;
import com.google.android.accessibility.talkback.R;
import com.google.android.accessibility.talkback.TalkBackService;
import com.google.android.accessibility.talkback.focusmanagement.AccessibilityFocusMonitor;
import com.google.android.accessibility.talkback.menurules.NodeMenuRuleProcessor;
import com.google.android.accessibility.utils.Performance.EventId;
import com.google.android.accessibility.utils.output.FeedbackItem;
import com.google.android.accessibility.utils.output.SpeechController.SpeakOptions;

/**
 * TalkBack-specific implementation of {@link
 * com.google.android.accessibility.talkback.contextmenu.RadialMenuManager.RadialMenuClient}.
 *
 * <p>Used by {@link RadialMenuManager} to configure each list-style context menu before display.
 * Uses {@link GlobalMenuProcessor} and {@link NodeMenuRuleProcessor} to help configure menus.
 */
public class TalkBackRadialMenuClient implements RadialMenuManager.RadialMenuClient {
  /** The parent service. */
  private final TalkBackService service;

  /** Menu inflater, used for constructing menus on-demand. */
  private final MenuInflater menuInflater;

  /** Menu rule processor, used to generate local context menus. */
  private final NodeMenuRuleProcessor nodeMenuRuleProcessor;

  /** Global menu processor, used to generate items depending on current TalkBack state. */
  private final GlobalMenuProcessor globalMenuProcessor;

  private ContextMenuItemClickProcessor menuClickProcessor;

  private final Pipeline.FeedbackReturner pipeline;
  private final AccessibilityFocusMonitor accessibilityFocusMonitor;

  public TalkBackRadialMenuClient(
      TalkBackService service,
      Pipeline.FeedbackReturner pipeline,
      AccessibilityFocusMonitor accessibilityFocusMonitor,
      NodeMenuRuleProcessor nodeMenuRuleProcessor) {
    this.service = service;
    this.pipeline = pipeline;
    this.accessibilityFocusMonitor = accessibilityFocusMonitor;
    menuInflater = new MenuInflater(this.service);
    this.nodeMenuRuleProcessor = nodeMenuRuleProcessor;
    globalMenuProcessor = new GlobalMenuProcessor(this.service);
    menuClickProcessor = new ContextMenuItemClickProcessor(this.service, pipeline);
  }

  @Override
  public void onCreateRadialMenu(int menuId, RadialMenu menu) {
    if (menuId == R.menu.global_context_menu) {
      onCreateGlobalContextMenu(menu);
    }
  }

  @Override
  public boolean onPrepareRadialMenu(int menuId, RadialMenu menu) {
    if (menuId == R.menu.global_context_menu) {
      return onPrepareGlobalContextMenu(menu);
    } else if (menuId == R.menu.local_context_menu) {
      return onPrepareLocalContextMenu(menu);
    } else if (menuId == R.id.custom_action_menu) {
      return onPrepareCustomActionMenu(menu);
    } else if (menuId == R.id.editing_menu) {
      return onPrepareEditingMenu(menu);
    } else if (menuId == R.menu.language_menu) {
      return onPrepareLanguageMenu(menu);
    } else {
      return false;
    }
  }

  /**
   * Handles clicking on a radial menu item.
   *
   * @param menuItem The radial menu item that was clicked.
   */
  @Override
  public boolean onMenuItemClicked(MenuItem menuItem) {
    return menuClickProcessor.onMenuItemClicked(menuItem);
  }

  @Override
  public boolean onMenuItemHovered() {
    // Let the manager handle spoken feedback from hovering.
    return false;
  }

  private void onCreateGlobalContextMenu(RadialMenu menu) {
    menuInflater.inflate(R.menu.global_context_menu, menu);
    onCreateQuickNavigationMenuItem(menu.findItem(R.id.quick_navigation));
  }

  private void onCreateQuickNavigationMenuItem(RadialMenuItem quickNavigationItem) {
    final RadialSubMenu quickNavigationSubMenu = quickNavigationItem.getSubMenu();
    final QuickNavigationJogDial quickNav = new QuickNavigationJogDial(service, pipeline);

    // TODO: This doesn't seem like a very clean OOP implementation.
    quickNav.populateMenu(quickNavigationSubMenu);

    quickNavigationSubMenu.setDefaultSelectionListener(quickNav);
    quickNavigationSubMenu.setOnMenuVisibilityChangedListener(quickNav);
  }

  private boolean onPrepareGlobalContextMenu(RadialMenu menu) {
    return globalMenuProcessor.prepareMenu(menu);
  }

  private boolean onPrepareLocalContextMenu(RadialMenu menu) {
    final AccessibilityNodeInfoCompat currentNode =
        accessibilityFocusMonitor.getAccessibilityFocus(/* useInputFocusIfEmpty= */ true);
    if (nodeMenuRuleProcessor == null || currentNode == null) {
      return false;
    }

    final boolean result = nodeMenuRuleProcessor.prepareMenuForNode(menu, currentNode);
    if (!result && menu.size() == 0) {
      EventId eventId = EVENT_ID_UNTRACKED; // Not tracking performance of menu events.
      pipeline.returnFeedback(
          eventId,
          Feedback.speech(
              service.getString(R.string.title_local_breakout_no_items),
              SpeakOptions.create()
                  .setQueueMode(QUEUE_MODE_FLUSH_ALL)
                  .setFlags(
                      FeedbackItem.FLAG_NO_HISTORY
                          | FeedbackItem.FLAG_FORCED_FEEDBACK_AUDIO_PLAYBACK_ACTIVE
                          | FeedbackItem.FLAG_FORCED_FEEDBACK_MICROPHONE_ACTIVE
                          | FeedbackItem.FLAG_FORCED_FEEDBACK_SSB_ACTIVE)));
    }
    currentNode.recycle();
    return result;
  }

  private boolean onPrepareLanguageMenu(RadialMenu menu) {
    return LanguageMenuProcessor.prepareLanguageMenu(service, menu);
  }

  private boolean onPrepareCustomActionMenu(RadialMenu menu) {
    final AccessibilityNodeInfoCompat currentNode =
        accessibilityFocusMonitor.getAccessibilityFocus(/* useInputFocusIfEmpty= */ true);
    if (nodeMenuRuleProcessor == null || currentNode == null) {
      return false;
    }

    final boolean result = nodeMenuRuleProcessor.prepareCustomActionMenuForNode(menu, currentNode);
    if (!result && menu.size() == 0) {
      EventId eventId = EVENT_ID_UNTRACKED; // Not tracking performance of menu events.
      pipeline.returnFeedback(
          eventId,
          Feedback.speech(
              service.getString(R.string.title_local_breakout_no_items),
              SpeakOptions.create()
                  .setQueueMode(QUEUE_MODE_FLUSH_ALL)
                  .setFlags(
                      FeedbackItem.FLAG_NO_HISTORY
                          | FeedbackItem.FLAG_FORCED_FEEDBACK_AUDIO_PLAYBACK_ACTIVE
                          | FeedbackItem.FLAG_FORCED_FEEDBACK_MICROPHONE_ACTIVE
                          | FeedbackItem.FLAG_FORCED_FEEDBACK_SSB_ACTIVE)));
    }
    currentNode.recycle();
    return result;
  }

  private boolean onPrepareEditingMenu(RadialMenu menu) {
    final AccessibilityNodeInfoCompat currentNode =
        accessibilityFocusMonitor.getAccessibilityFocus(/* useInputFocusIfEmpty= */ true);
    if (nodeMenuRuleProcessor == null || currentNode == null) {
      return false;
    }

    final boolean result = nodeMenuRuleProcessor.prepareEditingMenuForNode(menu, currentNode);
    if (!result && menu.size() == 0) {
      EventId eventId = EVENT_ID_UNTRACKED; // Not tracking performance of menu events.
      pipeline.returnFeedback(
          eventId,
          Feedback.speech(
              service.getString(R.string.title_local_breakout_no_items),
              SpeakOptions.create()
                  .setQueueMode(QUEUE_MODE_FLUSH_ALL)
                  .setFlags(
                      FeedbackItem.FLAG_NO_HISTORY
                          | FeedbackItem.FLAG_FORCED_FEEDBACK_AUDIO_PLAYBACK_ACTIVE
                          | FeedbackItem.FLAG_FORCED_FEEDBACK_MICROPHONE_ACTIVE
                          | FeedbackItem.FLAG_FORCED_FEEDBACK_SSB_ACTIVE)));
    }
    currentNode.recycle();
    return result;
  }

  private static class QuickNavigationJogDial extends BreakoutMenuUtils.JogDial
      implements RadialMenuItem.OnMenuItemSelectionListener,
          RadialMenu.OnMenuVisibilityChangedListener {

    private static final int SEGMENT_COUNT = 16;

    private final Handler handler = new Handler();

    private final Context context;
    private final Pipeline.FeedbackReturner pipeline;

    public QuickNavigationJogDial(TalkBackService service, Pipeline.FeedbackReturner pipeline) {
      super(SEGMENT_COUNT);

      context = service;
      this.pipeline = pipeline;
    }

    @Override
    public void onFirstTouch() {
      EventId eventId = EVENT_ID_UNTRACKED; // Not tracking performance for menu events.
      pipeline.returnFeedback(
          eventId,
          Feedback.focusDirection(SEARCH_FOCUS_FORWARD)
              .setInputMode(INPUT_MODE_TOUCH)
              .setScroll(true));
    }

    @Override
    public void onPrevious() {
      EventId eventId = EVENT_ID_UNTRACKED; // Not tracking performance for menu events.
      boolean navSuccess =
          pipeline.returnFeedback(
              eventId,
              Feedback.focusDirection(SEARCH_FOCUS_BACKWARD)
                  .setInputMode(INPUT_MODE_TOUCH)
                  .setScroll(true));
      if (!navSuccess) {
        pipeline.returnFeedback(eventId, Feedback.sound(R.raw.complete));
      }
    }

    @Override
    public void onNext() {
      EventId eventId = EVENT_ID_UNTRACKED; // Not tracking performance for menu events.
      boolean navSuccess =
          pipeline.returnFeedback(
              eventId,
              Feedback.focusDirection(SEARCH_FOCUS_FORWARD)
                  .setInputMode(INPUT_MODE_TOUCH)
                  .setScroll(true));
      if (!navSuccess) {
        pipeline.returnFeedback(eventId, Feedback.sound(R.raw.complete));
      }
    }

    @Override
    public boolean onMenuItemSelection(RadialMenuItem item) {
      handler.removeCallbacks(hintRunnable);

      if (item == null) {
        // Let the manager handle cancellations.
        return false;
      }

      // Don't provide feedback for individual segments.
      return true;
    }

    @Override
    public void onMenuShown() {
      handler.postDelayed(hintRunnable, RadialMenuManager.DELAY_RADIAL_MENU_HINT);
    }

    @Override
    public void onMenuDismissed() {
      handler.removeCallbacks(hintRunnable);
    }

    private final Runnable hintRunnable =
        new Runnable() {
          @Override
          public void run() {
            final String hintText = context.getString(R.string.hint_summary_jog_dial);
            EventId eventId = EVENT_ID_UNTRACKED; // Hints occur after other feedback.
            pipeline.returnFeedback(
                eventId,
                Feedback.speech(
                    hintText,
                    SpeakOptions.create()
                        .setQueueMode(QUEUE_MODE_QUEUE)
                        .setFlags(FeedbackItem.FLAG_NO_HISTORY)));
          }
        };
  }
}
