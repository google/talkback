/*
 * Copyright (C) 2022 Google Inc.
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
package com.google.android.accessibility.talkback.keyboard;

import static com.google.android.accessibility.talkback.Feedback.ContinuousRead.Action.START_AT_NEXT;
import static com.google.android.accessibility.talkback.Feedback.ContinuousRead.Action.START_AT_TOP;
import static com.google.android.accessibility.talkback.Feedback.DimScreen.Action.BRIGHTEN;
import static com.google.android.accessibility.talkback.Feedback.DimScreen.Action.DIM;
import static com.google.android.accessibility.talkback.Feedback.Focus.Action.CLICK_CURRENT;
import static com.google.android.accessibility.talkback.Feedback.Focus.Action.LONG_CLICK_CURRENT;
import static com.google.android.accessibility.talkback.Feedback.FocusDirection.Action.NEXT_GRANULARITY;
import static com.google.android.accessibility.talkback.Feedback.FocusDirection.Action.PREVIOUS_GRANULARITY;
import static com.google.android.accessibility.talkback.Feedback.Speech.Action.COPY_LAST;
import static com.google.android.accessibility.talkback.Feedback.UniversalSearch.Action.TOGGLE_SEARCH;
import static com.google.android.accessibility.talkback.selector.SelectorController.Setting.ACTIONS;
import static com.google.android.accessibility.utils.input.CursorGranularity.CHARACTER;
import static com.google.android.accessibility.utils.input.CursorGranularity.DEFAULT;
import static com.google.android.accessibility.utils.input.CursorGranularity.WORD;
import static com.google.android.accessibility.utils.monitor.InputModeTracker.INPUT_MODE_KEYBOARD;
import static com.google.android.accessibility.utils.preference.PreferencesActivity.FRAGMENT_NAME;
import static com.google.android.accessibility.utils.traversal.TraversalStrategy.SEARCH_FOCUS_BACKWARD;
import static com.google.android.accessibility.utils.traversal.TraversalStrategy.SEARCH_FOCUS_DOWN;
import static com.google.android.accessibility.utils.traversal.TraversalStrategy.SEARCH_FOCUS_FORWARD;
import static com.google.android.accessibility.utils.traversal.TraversalStrategy.SEARCH_FOCUS_UP;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.content.Intent;
import com.android.talkback.TalkBackPreferencesActivity;
import com.google.android.accessibility.talkback.ActorState;
import com.google.android.accessibility.talkback.Feedback;
import com.google.android.accessibility.talkback.Feedback.FocusDirection;
import com.google.android.accessibility.talkback.Pipeline;
import com.google.android.accessibility.talkback.R;
import com.google.android.accessibility.talkback.actor.DirectionNavigationActor;
import com.google.android.accessibility.talkback.actor.FullScreenReadActor;
import com.google.android.accessibility.talkback.actor.SystemActionPerformer;
import com.google.android.accessibility.talkback.contextmenu.ListMenuManager;
import com.google.android.accessibility.talkback.focusmanagement.NavigationTarget;
import com.google.android.accessibility.talkback.focusmanagement.NavigationTarget.TargetType;
import com.google.android.accessibility.talkback.preference.base.TalkBackKeyboardShortcutPreferenceFragment;
import com.google.android.accessibility.talkback.selector.SelectorController;
import com.google.android.accessibility.talkback.selector.SelectorController.AnnounceType;
import com.google.android.accessibility.utils.Performance.EventId;
import com.google.android.accessibility.utils.SettingsUtils;
import com.google.android.accessibility.utils.input.CursorGranularity;
import com.google.android.accessibility.utils.traversal.TraversalStrategy;
import com.google.android.accessibility.utils.traversal.TraversalStrategy.SearchDirection;
import com.google.errorprone.annotations.CanIgnoreReturnValue;

/**
 * Feedback-mapper for keyboard shortcuts to actions. This class reacts to navigation actions,
 * global actions, and either traverses with intra-node granularity or changes accessibility focus.
 */
public class KeyComboMapper {
  private final Pipeline.FeedbackReturner pipeline;
  private final ActorState actorState;
  private final SelectorController selectorController;
  private final ListMenuManager menuManager;
  private final FullScreenReadActor fullScreenReadActor;
  private final Context context;
  private final DirectionNavigationActor.StateReader stateReader;

  public KeyComboMapper(
      Context context,
      Pipeline.FeedbackReturner pipeline,
      ActorState actorState,
      SelectorController selectorController,
      ListMenuManager menuManager,
      FullScreenReadActor fullScreenReadActor,
      DirectionNavigationActor.StateReader stateReader) {
    this.context = context;
    this.pipeline = pipeline;
    this.actorState = actorState;
    this.selectorController = selectorController;
    this.menuManager = menuManager;
    this.fullScreenReadActor = fullScreenReadActor;
    this.stateReader = stateReader;
  }

  /**
   * Performs the KeyCombo action by given {@code actionId}.
   *
   * @param action the keyboard shortcut generating from key combos.
   */
  @CanIgnoreReturnValue
  boolean performKeyComboAction(
      TalkBackPhysicalKeyboardShortcut action, String name, EventId eventId) {
    boolean result = true;
    switch (action) {
        // Direction navigation actions.
      case NAVIGATE_NEXT:
        result = pipeline.returnFeedback(eventId, createFocusDirectionBuilder(true));
        break;
      case NAVIGATE_PREVIOUS:
        result = pipeline.returnFeedback(eventId, createFocusDirectionBuilder(false));
        break;
      case NAVIGATE_NEXT_DEFAULT:
        result =
            pipeline.returnFeedback(
                eventId,
                Feedback.focusDirection(SEARCH_FOCUS_FORWARD)
                    .setGranularity(DEFAULT)
                    .setInputMode(INPUT_MODE_KEYBOARD)
                    .setDefaultToInputFocus(true)
                    .setScroll(true)
                    .setWrap(true));
        break;
      case NAVIGATE_PREVIOUS_DEFAULT:
        result =
            pipeline.returnFeedback(
                eventId,
                Feedback.focusDirection(SEARCH_FOCUS_BACKWARD)
                    .setGranularity(DEFAULT)
                    .setInputMode(INPUT_MODE_KEYBOARD)
                    .setDefaultToInputFocus(true)
                    .setScroll(true)
                    .setWrap(true));
        break;
      case NAVIGATE_ABOVE:
        result =
            pipeline.returnFeedback(
                eventId,
                Feedback.focusDirection(SEARCH_FOCUS_UP)
                    .setInputMode(INPUT_MODE_KEYBOARD)
                    .setWrap(true)
                    .setScroll(true)
                    .setDefaultToInputFocus(true));
        break;
      case NAVIGATE_BELOW:
        result =
            pipeline.returnFeedback(
                eventId,
                Feedback.focusDirection(SEARCH_FOCUS_DOWN)
                    .setInputMode(INPUT_MODE_KEYBOARD)
                    .setWrap(true)
                    .setScroll(true)
                    .setDefaultToInputFocus(true));
        break;
      case NAVIGATE_FIRST:
        result = pipeline.returnFeedback(eventId, Feedback.focusTop(INPUT_MODE_KEYBOARD));
        break;
      case NAVIGATE_LAST:
        result = pipeline.returnFeedback(eventId, Feedback.focusBottom(INPUT_MODE_KEYBOARD));
        break;
        // Tap
      case PERFORM_CLICK:
        if (SelectorController.getCurrentSetting(context).equals(ACTIONS)) {
          selectorController.activateCurrentAction(eventId);
          break;
        }
        result = pipeline.returnFeedback(eventId, Feedback.focus(CLICK_CURRENT));
        break;
      case PERFORM_LONG_CLICK:
        result = pipeline.returnFeedback(eventId, Feedback.focus(LONG_CLICK_CURRENT));
        break;
        // Micro Granularity
      case NAVIGATE_NEXT_WINDOW:
        result =
            pipeline.returnFeedback(
                eventId, Feedback.nextWindow(INPUT_MODE_KEYBOARD).setDefaultToInputFocus(true));
        break;
      case NAVIGATE_PREVIOUS_WINDOW:
        result =
            pipeline.returnFeedback(
                eventId, Feedback.previousWindow(INPUT_MODE_KEYBOARD).setDefaultToInputFocus(true));
        break;
      case NAVIGATE_NEXT_WORD:
        result =
            pipeline.returnFeedback(
                eventId,
                Feedback.focusDirection(SEARCH_FOCUS_FORWARD)
                    .setInputMode(INPUT_MODE_KEYBOARD)
                    .setGranularity(WORD));
        break;
      case NAVIGATE_PREVIOUS_WORD:
        result =
            pipeline.returnFeedback(
                eventId,
                Feedback.focusDirection(SEARCH_FOCUS_BACKWARD)
                    .setInputMode(INPUT_MODE_KEYBOARD)
                    .setGranularity(WORD));
        break;
      case NAVIGATE_NEXT_CHARACTER:
        result =
            pipeline.returnFeedback(
                eventId,
                Feedback.focusDirection(SEARCH_FOCUS_FORWARD)
                    .setInputMode(INPUT_MODE_KEYBOARD)
                    .setGranularity(CHARACTER));
        break;
      case NAVIGATE_PREVIOUS_CHARACTER:
        result =
            pipeline.returnFeedback(
                eventId,
                Feedback.focusDirection(SEARCH_FOCUS_BACKWARD)
                    .setInputMode(INPUT_MODE_KEYBOARD)
                    .setGranularity(CHARACTER));
        break;

        // Web navigation actions.
      case NAVIGATE_NEXT_HEADING:
        result =
            performWebNavigationKeyCombo(
                NavigationTarget.TARGET_HTML_ELEMENT_HEADING, true /* forward */, eventId);
        break;
      case NAVIGATE_PREVIOUS_HEADING:
        result =
            performWebNavigationKeyCombo(
                NavigationTarget.TARGET_HTML_ELEMENT_HEADING, false /* backward */, eventId);
        break;
      case NAVIGATE_NEXT_BUTTON:
        result =
            performWebNavigationKeyCombo(
                NavigationTarget.TARGET_HTML_ELEMENT_BUTTON, true /* forward */, eventId);
        break;
      case NAVIGATE_PREVIOUS_BUTTON:
        result =
            performWebNavigationKeyCombo(
                NavigationTarget.TARGET_HTML_ELEMENT_BUTTON, false /* backward */, eventId);
        break;
      case NAVIGATE_NEXT_CHECKBOX:
        result =
            performWebNavigationKeyCombo(
                NavigationTarget.TARGET_HTML_ELEMENT_CHECKBOX, true /* forward */, eventId);
        break;
      case NAVIGATE_PREVIOUS_CHECKBOX:
        result =
            performWebNavigationKeyCombo(
                NavigationTarget.TARGET_HTML_ELEMENT_CHECKBOX, false /* backward */, eventId);
        break;
      case NAVIGATE_NEXT_ARIA_LANDMARK:
        result =
            performWebNavigationKeyCombo(
                NavigationTarget.TARGET_HTML_ELEMENT_ARIA_LANDMARK, true /* forward */, eventId);
        break;
      case NAVIGATE_PREVIOUS_ARIA_LANDMARK:
        result =
            performWebNavigationKeyCombo(
                NavigationTarget.TARGET_HTML_ELEMENT_ARIA_LANDMARK, false /* backward */, eventId);
        break;
      case NAVIGATE_NEXT_EDIT_FIELD:
        result =
            performWebNavigationKeyCombo(
                NavigationTarget.TARGET_HTML_ELEMENT_EDIT_FIELD, true /* forward */, eventId);
        break;
      case NAVIGATE_PREVIOUS_EDIT_FIELD:
        result =
            performWebNavigationKeyCombo(
                NavigationTarget.TARGET_HTML_ELEMENT_EDIT_FIELD, false /* backward */, eventId);
        break;
      case NAVIGATE_NEXT_FOCUSABLE_ITEM:
        result =
            performWebNavigationKeyCombo(
                NavigationTarget.TARGET_HTML_ELEMENT_FOCUSABLE_ITEM, true /* forward */, eventId);
        break;
      case NAVIGATE_PREVIOUS_FOCUSABLE_ITEM:
        result =
            performWebNavigationKeyCombo(
                NavigationTarget.TARGET_HTML_ELEMENT_FOCUSABLE_ITEM, false /* backward */, eventId);
        break;
      case NAVIGATE_NEXT_HEADING_1:
        result =
            performWebNavigationKeyCombo(
                NavigationTarget.TARGET_HTML_ELEMENT_HEADING_1, true /* forward */, eventId);
        break;
      case NAVIGATE_PREVIOUS_HEADING_1:
        result =
            performWebNavigationKeyCombo(
                NavigationTarget.TARGET_HTML_ELEMENT_HEADING_1, false /* backward */, eventId);
        break;
      case NAVIGATE_NEXT_HEADING_2:
        result =
            performWebNavigationKeyCombo(
                NavigationTarget.TARGET_HTML_ELEMENT_HEADING_2, true /* forward */, eventId);
        break;
      case NAVIGATE_PREVIOUS_HEADING_2:
        result =
            performWebNavigationKeyCombo(
                NavigationTarget.TARGET_HTML_ELEMENT_HEADING_2, false /* backward */, eventId);
        break;
      case NAVIGATE_NEXT_HEADING_3:
        result =
            performWebNavigationKeyCombo(
                NavigationTarget.TARGET_HTML_ELEMENT_HEADING_3, true /* forward */, eventId);
        break;
      case NAVIGATE_PREVIOUS_HEADING_3:
        result =
            performWebNavigationKeyCombo(
                NavigationTarget.TARGET_HTML_ELEMENT_HEADING_3, false /* backward */, eventId);
        break;
      case NAVIGATE_NEXT_HEADING_4:
        result =
            performWebNavigationKeyCombo(
                NavigationTarget.TARGET_HTML_ELEMENT_HEADING_4, true /* forward */, eventId);
        break;
      case NAVIGATE_PREVIOUS_HEADING_4:
        result =
            performWebNavigationKeyCombo(
                NavigationTarget.TARGET_HTML_ELEMENT_HEADING_4, false /* backward */, eventId);
        break;
      case NAVIGATE_NEXT_HEADING_5:
        result =
            performWebNavigationKeyCombo(
                NavigationTarget.TARGET_HTML_ELEMENT_HEADING_5, true /* forward */, eventId);
        break;
      case NAVIGATE_PREVIOUS_HEADING_5:
        result =
            performWebNavigationKeyCombo(
                NavigationTarget.TARGET_HTML_ELEMENT_HEADING_5, false /* backward */, eventId);
        break;
      case NAVIGATE_NEXT_HEADING_6:
        result =
            performWebNavigationKeyCombo(
                NavigationTarget.TARGET_HTML_ELEMENT_HEADING_6, true /* forward */, eventId);
        break;
      case NAVIGATE_PREVIOUS_HEADING_6:
        result =
            performWebNavigationKeyCombo(
                NavigationTarget.TARGET_HTML_ELEMENT_HEADING_6, false /* backward */, eventId);
        break;
      case NAVIGATE_NEXT_LINK:
        result =
            performWebNavigationKeyCombo(
                NavigationTarget.TARGET_HTML_ELEMENT_LINK, true /* forward */, eventId);
        break;
      case NAVIGATE_PREVIOUS_LINK:
        result =
            performWebNavigationKeyCombo(
                NavigationTarget.TARGET_HTML_ELEMENT_LINK, false /* backward */, eventId);
        break;
      case NAVIGATE_NEXT_CONTROL:
        result =
            performWebNavigationKeyCombo(
                NavigationTarget.TARGET_HTML_ELEMENT_CONTROL, true /* forward */, eventId);
        break;
      case NAVIGATE_PREVIOUS_CONTROL:
        result =
            performWebNavigationKeyCombo(
                NavigationTarget.TARGET_HTML_ELEMENT_CONTROL, false /* backward */, eventId);
        break;
      case NAVIGATE_NEXT_GRAPHIC:
        result =
            performWebNavigationKeyCombo(
                NavigationTarget.TARGET_HTML_ELEMENT_GRAPHIC, true /* forward */, eventId);
        break;
      case NAVIGATE_PREVIOUS_GRAPHIC:
        result =
            performWebNavigationKeyCombo(
                NavigationTarget.TARGET_HTML_ELEMENT_GRAPHIC, false /* backward */, eventId);
        break;
      case NAVIGATE_NEXT_LIST_ITEM:
        result =
            performWebNavigationKeyCombo(
                NavigationTarget.TARGET_HTML_ELEMENT_LIST_ITEM, true /* forward */, eventId);
        break;
      case NAVIGATE_PREVIOUS_LIST_ITEM:
        result =
            performWebNavigationKeyCombo(
                NavigationTarget.TARGET_HTML_ELEMENT_LIST_ITEM, false /* backward */, eventId);
        break;
      case NAVIGATE_NEXT_LIST:
        result =
            performWebNavigationKeyCombo(
                NavigationTarget.TARGET_HTML_ELEMENT_LIST, true /* forward */, eventId);
        break;
      case NAVIGATE_PREVIOUS_LIST:
        result =
            performWebNavigationKeyCombo(
                NavigationTarget.TARGET_HTML_ELEMENT_LIST, false /* backward */, eventId);
        break;
      case NAVIGATE_NEXT_TABLE:
        result =
            performWebNavigationKeyCombo(
                NavigationTarget.TARGET_HTML_ELEMENT_TABLE, true /* forward */, eventId);
        break;
      case NAVIGATE_PREVIOUS_TABLE:
        result =
            performWebNavigationKeyCombo(
                NavigationTarget.TARGET_HTML_ELEMENT_TABLE, false /* backward */, eventId);
        break;
      case NAVIGATE_NEXT_COMBOBOX:
        result =
            performWebNavigationKeyCombo(
                NavigationTarget.TARGET_HTML_ELEMENT_COMBOBOX, true /* forward */, eventId);
        break;
      case NAVIGATE_PREVIOUS_COMBOBOX:
        result =
            performWebNavigationKeyCombo(
                NavigationTarget.TARGET_HTML_ELEMENT_COMBOBOX, false /* backward */, eventId);
        break;

        // Global actions
      case BACK:
        result =
            pipeline.returnFeedback(
                eventId, Feedback.systemAction(AccessibilityService.GLOBAL_ACTION_BACK));
        break;
      case HOME:
        result =
            pipeline.returnFeedback(
                eventId, Feedback.systemAction(AccessibilityService.GLOBAL_ACTION_HOME));
        break;
      case NOTIFICATIONS:
        result =
            pipeline.returnFeedback(
                eventId, Feedback.systemAction(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS));
        break;
      case RECENT_APPS:
        result =
            pipeline.returnFeedback(
                eventId, Feedback.systemAction(AccessibilityService.GLOBAL_ACTION_RECENTS));
        break;
      case PLAY_PAUSE_MEDIA:
        result =
            pipeline.returnFeedback(
                eventId,
                Feedback.systemAction(SystemActionPerformer.GLOBAL_ACTION_KEYCODE_HEADSETHOOK));
        break;

        // Other actions.
      case SEARCH_SCREEN_FOR_ITEM:
        result = pipeline.returnFeedback(eventId, Feedback.universalSearch(TOGGLE_SEARCH));
        break;
      case SELECT_NEXT_READING_MENU:
        selectorController.selectPreviousOrNextSetting(eventId, AnnounceType.DESCRIPTION, true);
        break;
      case SELECT_PREVIOUS_READING_MENU:
        selectorController.selectPreviousOrNextSetting(eventId, AnnounceType.DESCRIPTION, false);
        break;
      case ADJUST_READING_MENU_DOWN:
        selectorController.adjustSelectedSetting(eventId, true);
        break;
      case ADJUST_READING_MENU_UP:
        selectorController.adjustSelectedSetting(eventId, false);
        break;
      case NEXT_NAVIGATION_SETTING:
        result = pipeline.returnFeedback(eventId, Feedback.focusDirection(NEXT_GRANULARITY));
        break;
      case PREVIOUS_NAVIGATION_SETTING:
        result = pipeline.returnFeedback(eventId, Feedback.focusDirection(PREVIOUS_GRANULARITY));
        break;
      case READ_FROM_TOP:
        result = pipeline.returnFeedback(eventId, Feedback.continuousRead(START_AT_TOP));
        break;
      case READ_FROM_NEXT_ITEM:
        result = pipeline.returnFeedback(eventId, Feedback.continuousRead(START_AT_NEXT));
        break;
      case SHOW_GLOBAL_CONTEXT_MENU:
        result = menuManager.showMenu(R.menu.context_menu, eventId);
        break;
      case SHOW_ACTIONS:
        result = menuManager.showMenu(R.id.custom_action_menu, eventId);
        break;
      case SHOW_LANGUAGES_AVAILABLE:
        result = menuManager.showMenu(R.menu.language_menu, eventId);
        break;
      case OPEN_MANAGE_KEYBOARD_SHORTCUTS:
        if (SettingsUtils.allowLinksOutOfSettings(context.getApplicationContext())) {
          openManageKeyboardShortcuts();
        }
        break;
      case OPEN_TALKBACK_SETTINGS:
        if (SettingsUtils.allowLinksOutOfSettings(context.getApplicationContext())) {
          openTalkBackSettings();
        }
        break;
      case COPY_LAST_SPOKEN_PHRASE:
        result =
            pipeline.returnFeedback(
                eventId, Feedback.part().setSpeech(Feedback.Speech.create(COPY_LAST)));
        break;
      case HIDE_OR_SHOW_SCREEN:
        if (actorState.getDimScreen().isDimmingEnabled()) {
          result = pipeline.returnFeedback(eventId, Feedback.dimScreen(BRIGHTEN));
        } else {
          result = pipeline.returnFeedback(eventId, Feedback.dimScreen(DIM));
        }
        break;
      default: // fall out
    }
    if (!result) {
      pipeline.returnFeedback(eventId, Feedback.sound(R.raw.complete));
    }
    return result;
  }

  /**
   * Interrupts the actions if FullScreenReadActor is activated and the action is not a navigation.
   *
   * @param performedAction the action generating from key combos.
   */
  void interruptByKeyCombo(TalkBackPhysicalKeyboardShortcut performedAction) {
    if (performedAction
            == TalkBackPhysicalKeyboardShortcut.NAVIGATE_NEXT_DEFAULT /* next in default keymap */
        || performedAction == TalkBackPhysicalKeyboardShortcut.NAVIGATE_PREVIOUS_DEFAULT
        /* previous in default keymap */
        || performedAction
            == TalkBackPhysicalKeyboardShortcut.NAVIGATE_NEXT /* next in classic keymap */
        || performedAction
            == TalkBackPhysicalKeyboardShortcut
                .NAVIGATE_PREVIOUS /* previous in classic keymap */) {
      return;
    }
    if (fullScreenReadActor.isActive()) {
      fullScreenReadActor.interrupt();
    }
  }

  private boolean performWebNavigationKeyCombo(
      @TargetType int targetType, boolean forward, EventId eventId) {
    @SearchDirection
    int direction =
        forward ? TraversalStrategy.SEARCH_FOCUS_FORWARD : TraversalStrategy.SEARCH_FOCUS_BACKWARD;
    return pipeline.returnFeedback(
        eventId,
        Feedback.focusDirection(direction)
            .setInputMode(INPUT_MODE_KEYBOARD)
            .setHtmlTargetType(targetType));
  }

  private void openManageKeyboardShortcuts() {
    Intent intent = new Intent(context, TalkBackPreferencesActivity.class);
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    intent.putExtra(FRAGMENT_NAME, TalkBackKeyboardShortcutPreferenceFragment.class.getName());
    context.startActivity(intent);
  }

  private void openTalkBackSettings() {
    Intent intent = new Intent(context, TalkBackPreferencesActivity.class);
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    context.startActivity(intent);
  }

  /**
   * Creates focus direction builder for focus navigation in Container and Window granularity by
   * using classic keymap actions.
   */
  private FocusDirection.Builder createFocusDirectionBuilder(boolean navigateToNext) {
    int direction = navigateToNext ? SEARCH_FOCUS_FORWARD : SEARCH_FOCUS_BACKWARD;
    FocusDirection.Builder defaultBuilder =
        Feedback.focusDirection(direction)
            .setInputMode(INPUT_MODE_KEYBOARD)
            .setWrap(true)
            .setScroll(true)
            .setDefaultToInputFocus(true);
    CursorGranularity granularity = stateReader.getCurrentGranularity();
    if (granularity == null) {
      return defaultBuilder;
    }
    switch (granularity) {
      case CONTAINER:
        return defaultBuilder.setToContainer(true);
      case WINDOWS:
        return defaultBuilder.setToWindow(true);
      default:
        return defaultBuilder;
    }
  }
}
