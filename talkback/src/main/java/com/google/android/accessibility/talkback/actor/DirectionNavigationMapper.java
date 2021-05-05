/*
 * Copyright (C) 2020 Google Inc.
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

package com.google.android.accessibility.talkback.actor;

import static com.google.android.accessibility.talkback.Feedback.Focus.Action.CLICK_CURRENT;
import static com.google.android.accessibility.talkback.Feedback.Focus.Action.LONG_CLICK_CURRENT;
import static com.google.android.accessibility.utils.input.CursorGranularity.CHARACTER;
import static com.google.android.accessibility.utils.input.CursorGranularity.DEFAULT;
import static com.google.android.accessibility.utils.input.CursorGranularity.WORD;
import static com.google.android.accessibility.utils.input.InputModeManager.INPUT_MODE_KEYBOARD;
import static com.google.android.accessibility.utils.traversal.TraversalStrategy.SEARCH_FOCUS_BACKWARD;
import static com.google.android.accessibility.utils.traversal.TraversalStrategy.SEARCH_FOCUS_DOWN;
import static com.google.android.accessibility.utils.traversal.TraversalStrategy.SEARCH_FOCUS_FORWARD;
import static com.google.android.accessibility.utils.traversal.TraversalStrategy.SEARCH_FOCUS_UP;

import com.google.android.accessibility.talkback.Feedback;
import com.google.android.accessibility.talkback.Mappers;
import com.google.android.accessibility.talkback.focusmanagement.NavigationTarget;
import com.google.android.accessibility.talkback.focusmanagement.NavigationTarget.TargetType;
import com.google.android.accessibility.utils.Performance.EventId;
import com.google.android.accessibility.utils.keyboard.KeyComboManager;
import com.google.android.accessibility.utils.traversal.TraversalStrategy;
import com.google.android.accessibility.utils.traversal.TraversalStrategy.SearchDirection;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Feedback-mapper for directional navigation. This class reacts to navigation actions, and either
 * traverses with intra-node granularity or changes accessibility focus.
 */
public class DirectionNavigationMapper {

  private DirectionNavigationMapper() {} // Not instantiatable.

  //////////////////////////////////////////////////////////////////////////////////////////////////
  // Methods handling KeyCombo shortcuts.

  public static @Nullable Feedback onComboPerformed(
      EventId eventId, Mappers.Variables variables, int depth) {

    switch (variables.keyCombo(depth)) {
      case KeyComboManager.ACTION_NAVIGATE_NEXT:
        return toFeedback(
            eventId,
            Feedback.focusDirection(SEARCH_FOCUS_FORWARD)
                .setInputMode(INPUT_MODE_KEYBOARD)
                .setWrap(true)
                .setScroll(true)
                .setDefaultToInputFocus(true));
      case KeyComboManager.ACTION_NAVIGATE_PREVIOUS:
        return toFeedback(
            eventId,
            Feedback.focusDirection(SEARCH_FOCUS_BACKWARD)
                .setInputMode(INPUT_MODE_KEYBOARD)
                .setWrap(true)
                .setScroll(true)
                .setDefaultToInputFocus(true));
      case KeyComboManager.ACTION_NAVIGATE_NEXT_DEFAULT:
        return toFeedback(
            eventId,
            Feedback.focusDirection(SEARCH_FOCUS_FORWARD)
                .setInputMode(INPUT_MODE_KEYBOARD)
                .setGranularity(DEFAULT));
      case KeyComboManager.ACTION_NAVIGATE_PREVIOUS_DEFAULT:
        return toFeedback(
            eventId,
            Feedback.focusDirection(SEARCH_FOCUS_BACKWARD)
                .setInputMode(INPUT_MODE_KEYBOARD)
                .setGranularity(DEFAULT));
      case KeyComboManager.ACTION_NAVIGATE_UP:
        return toFeedback(
            eventId,
            Feedback.focusDirection(SEARCH_FOCUS_UP)
                .setInputMode(INPUT_MODE_KEYBOARD)
                .setWrap(true)
                .setScroll(true)
                .setDefaultToInputFocus(true));
      case KeyComboManager.ACTION_NAVIGATE_DOWN:
        return toFeedback(
            eventId,
            Feedback.focusDirection(SEARCH_FOCUS_DOWN)
                .setInputMode(INPUT_MODE_KEYBOARD)
                .setWrap(true)
                .setScroll(true)
                .setDefaultToInputFocus(true));
      case KeyComboManager.ACTION_NAVIGATE_FIRST:
        return Feedback.create(eventId, Feedback.focusTop(INPUT_MODE_KEYBOARD).build());
      case KeyComboManager.ACTION_NAVIGATE_LAST:
        return Feedback.create(eventId, Feedback.focusBottom(INPUT_MODE_KEYBOARD).build());
      case KeyComboManager.ACTION_PERFORM_CLICK:
        return toFeedback(eventId, Feedback.focus(CLICK_CURRENT));
      case KeyComboManager.ACTION_NAVIGATE_NEXT_WORD:
        return toFeedback(
            eventId,
            Feedback.focusDirection(SEARCH_FOCUS_FORWARD)
                .setInputMode(INPUT_MODE_KEYBOARD)
                .setGranularity(WORD));
      case KeyComboManager.ACTION_NAVIGATE_PREVIOUS_WORD:
        return toFeedback(
            eventId,
            Feedback.focusDirection(SEARCH_FOCUS_BACKWARD)
                .setInputMode(INPUT_MODE_KEYBOARD)
                .setGranularity(WORD));
      case KeyComboManager.ACTION_NAVIGATE_NEXT_CHARACTER:
        return toFeedback(
            eventId,
            Feedback.focusDirection(SEARCH_FOCUS_FORWARD)
                .setInputMode(INPUT_MODE_KEYBOARD)
                .setGranularity(CHARACTER));
      case KeyComboManager.ACTION_NAVIGATE_PREVIOUS_CHARACTER:
        return toFeedback(
            eventId,
            Feedback.focusDirection(SEARCH_FOCUS_BACKWARD)
                .setInputMode(INPUT_MODE_KEYBOARD)
                .setGranularity(CHARACTER));
      case KeyComboManager.ACTION_PERFORM_LONG_CLICK:
        return toFeedback(eventId, Feedback.focus(LONG_CLICK_CURRENT));
      case KeyComboManager.ACTION_NAVIGATE_NEXT_HEADING:
        return performWebNavigationKeyCombo(
            NavigationTarget.TARGET_HTML_ELEMENT_HEADING, true /* forward */, eventId);
      case KeyComboManager.ACTION_NAVIGATE_PREVIOUS_HEADING:
        return performWebNavigationKeyCombo(
            NavigationTarget.TARGET_HTML_ELEMENT_HEADING, false /* backward */, eventId);
      case KeyComboManager.ACTION_NAVIGATE_NEXT_BUTTON:
        return performWebNavigationKeyCombo(
            NavigationTarget.TARGET_HTML_ELEMENT_BUTTON, true /* forward */, eventId);
      case KeyComboManager.ACTION_NAVIGATE_PREVIOUS_BUTTON:
        return performWebNavigationKeyCombo(
            NavigationTarget.TARGET_HTML_ELEMENT_BUTTON, false /* backward */, eventId);
      case KeyComboManager.ACTION_NAVIGATE_NEXT_CHECKBOX:
        return performWebNavigationKeyCombo(
            NavigationTarget.TARGET_HTML_ELEMENT_CHECKBOX, true /* forward */, eventId);
      case KeyComboManager.ACTION_NAVIGATE_PREVIOUS_CHECKBOX:
        return performWebNavigationKeyCombo(
            NavigationTarget.TARGET_HTML_ELEMENT_CHECKBOX, false /* backward */, eventId);
      case KeyComboManager.ACTION_NAVIGATE_NEXT_ARIA_LANDMARK:
        return performWebNavigationKeyCombo(
            NavigationTarget.TARGET_HTML_ELEMENT_ARIA_LANDMARK, true /* forward */, eventId);
      case KeyComboManager.ACTION_NAVIGATE_PREVIOUS_ARIA_LANDMARK:
        return performWebNavigationKeyCombo(
            NavigationTarget.TARGET_HTML_ELEMENT_ARIA_LANDMARK, false /* backward */, eventId);
      case KeyComboManager.ACTION_NAVIGATE_NEXT_EDIT_FIELD:
        return performWebNavigationKeyCombo(
            NavigationTarget.TARGET_HTML_ELEMENT_EDIT_FIELD, true /* forward */, eventId);
      case KeyComboManager.ACTION_NAVIGATE_PREVIOUS_EDIT_FIELD:
        return performWebNavigationKeyCombo(
            NavigationTarget.TARGET_HTML_ELEMENT_EDIT_FIELD, false /* backward */, eventId);
      case KeyComboManager.ACTION_NAVIGATE_NEXT_FOCUSABLE_ITEM:
        return performWebNavigationKeyCombo(
            NavigationTarget.TARGET_HTML_ELEMENT_FOCUSABLE_ITEM, true /* forward */, eventId);
      case KeyComboManager.ACTION_NAVIGATE_PREVIOUS_FOCUSABLE_ITEM:
        return performWebNavigationKeyCombo(
            NavigationTarget.TARGET_HTML_ELEMENT_FOCUSABLE_ITEM, false /* backward */, eventId);
      case KeyComboManager.ACTION_NAVIGATE_NEXT_HEADING_1:
        return performWebNavigationKeyCombo(
            NavigationTarget.TARGET_HTML_ELEMENT_HEADING_1, true /* forward */, eventId);
      case KeyComboManager.ACTION_NAVIGATE_PREVIOUS_HEADING_1:
        return performWebNavigationKeyCombo(
            NavigationTarget.TARGET_HTML_ELEMENT_HEADING_1, false /* backward */, eventId);
      case KeyComboManager.ACTION_NAVIGATE_NEXT_HEADING_2:
        return performWebNavigationKeyCombo(
            NavigationTarget.TARGET_HTML_ELEMENT_HEADING_2, true /* forward */, eventId);
      case KeyComboManager.ACTION_NAVIGATE_PREVIOUS_HEADING_2:
        return performWebNavigationKeyCombo(
            NavigationTarget.TARGET_HTML_ELEMENT_HEADING_2, false /* backward */, eventId);
      case KeyComboManager.ACTION_NAVIGATE_NEXT_HEADING_3:
        return performWebNavigationKeyCombo(
            NavigationTarget.TARGET_HTML_ELEMENT_HEADING_3, true /* forward */, eventId);
      case KeyComboManager.ACTION_NAVIGATE_PREVIOUS_HEADING_3:
        return performWebNavigationKeyCombo(
            NavigationTarget.TARGET_HTML_ELEMENT_HEADING_3, false /* backward */, eventId);
      case KeyComboManager.ACTION_NAVIGATE_NEXT_HEADING_4:
        return performWebNavigationKeyCombo(
            NavigationTarget.TARGET_HTML_ELEMENT_HEADING_4, true /* forward */, eventId);
      case KeyComboManager.ACTION_NAVIGATE_PREVIOUS_HEADING_4:
        return performWebNavigationKeyCombo(
            NavigationTarget.TARGET_HTML_ELEMENT_HEADING_4, false /* backward */, eventId);
      case KeyComboManager.ACTION_NAVIGATE_NEXT_HEADING_5:
        return performWebNavigationKeyCombo(
            NavigationTarget.TARGET_HTML_ELEMENT_HEADING_5, true /* forward */, eventId);
      case KeyComboManager.ACTION_NAVIGATE_PREVIOUS_HEADING_5:
        return performWebNavigationKeyCombo(
            NavigationTarget.TARGET_HTML_ELEMENT_HEADING_5, false /* backward */, eventId);
      case KeyComboManager.ACTION_NAVIGATE_NEXT_HEADING_6:
        return performWebNavigationKeyCombo(
            NavigationTarget.TARGET_HTML_ELEMENT_HEADING_6, true /* forward */, eventId);
      case KeyComboManager.ACTION_NAVIGATE_PREVIOUS_HEADING_6:
        return performWebNavigationKeyCombo(
            NavigationTarget.TARGET_HTML_ELEMENT_HEADING_6, false /* backward */, eventId);
      case KeyComboManager.ACTION_NAVIGATE_NEXT_LINK:
        return performWebNavigationKeyCombo(
            NavigationTarget.TARGET_HTML_ELEMENT_LINK, true /* forward */, eventId);
      case KeyComboManager.ACTION_NAVIGATE_PREVIOUS_LINK:
        return performWebNavigationKeyCombo(
            NavigationTarget.TARGET_HTML_ELEMENT_LINK, false /* backward */, eventId);
      case KeyComboManager.ACTION_NAVIGATE_NEXT_CONTROL:
        return performWebNavigationKeyCombo(
            NavigationTarget.TARGET_HTML_ELEMENT_CONTROL, true /* forward */, eventId);
      case KeyComboManager.ACTION_NAVIGATE_PREVIOUS_CONTROL:
        return performWebNavigationKeyCombo(
            NavigationTarget.TARGET_HTML_ELEMENT_CONTROL, false /* backward */, eventId);
      case KeyComboManager.ACTION_NAVIGATE_NEXT_GRAPHIC:
        return performWebNavigationKeyCombo(
            NavigationTarget.TARGET_HTML_ELEMENT_GRAPHIC, true /* forward */, eventId);
      case KeyComboManager.ACTION_NAVIGATE_PREVIOUS_GRAPHIC:
        return performWebNavigationKeyCombo(
            NavigationTarget.TARGET_HTML_ELEMENT_GRAPHIC, false /* backward */, eventId);
      case KeyComboManager.ACTION_NAVIGATE_NEXT_LIST_ITEM:
        return performWebNavigationKeyCombo(
            NavigationTarget.TARGET_HTML_ELEMENT_LIST_ITEM, true /* forward */, eventId);
      case KeyComboManager.ACTION_NAVIGATE_PREVIOUS_LIST_ITEM:
        return performWebNavigationKeyCombo(
            NavigationTarget.TARGET_HTML_ELEMENT_LIST_ITEM, false /* backward */, eventId);
      case KeyComboManager.ACTION_NAVIGATE_NEXT_LIST:
        return performWebNavigationKeyCombo(
            NavigationTarget.TARGET_HTML_ELEMENT_LIST, true /* forward */, eventId);
      case KeyComboManager.ACTION_NAVIGATE_PREVIOUS_LIST:
        return performWebNavigationKeyCombo(
            NavigationTarget.TARGET_HTML_ELEMENT_LIST, false /* backward */, eventId);
      case KeyComboManager.ACTION_NAVIGATE_NEXT_TABLE:
        return performWebNavigationKeyCombo(
            NavigationTarget.TARGET_HTML_ELEMENT_TABLE, true /* forward */, eventId);
      case KeyComboManager.ACTION_NAVIGATE_PREVIOUS_TABLE:
        return performWebNavigationKeyCombo(
            NavigationTarget.TARGET_HTML_ELEMENT_TABLE, false /* backward */, eventId);
      case KeyComboManager.ACTION_NAVIGATE_NEXT_COMBOBOX:
        return performWebNavigationKeyCombo(
            NavigationTarget.TARGET_HTML_ELEMENT_COMBOBOX, true /* forward */, eventId);
      case KeyComboManager.ACTION_NAVIGATE_PREVIOUS_COMBOBOX:
        return performWebNavigationKeyCombo(
            NavigationTarget.TARGET_HTML_ELEMENT_COMBOBOX, false /* backward */, eventId);
      case KeyComboManager.ACTION_NAVIGATE_NEXT_WINDOW:
        return toFeedback(
            eventId, Feedback.nextWindow(INPUT_MODE_KEYBOARD).setDefaultToInputFocus(true));
      case KeyComboManager.ACTION_NAVIGATE_PREVIOUS_WINDOW:
        return toFeedback(
            eventId, Feedback.previousWindow(INPUT_MODE_KEYBOARD).setDefaultToInputFocus(true));
      default:
        return null;
    }
  }

  private static Feedback performWebNavigationKeyCombo(
      @TargetType int targetType, boolean forward, EventId eventId) {
    @SearchDirection
    int direction =
        forward ? TraversalStrategy.SEARCH_FOCUS_FORWARD : TraversalStrategy.SEARCH_FOCUS_BACKWARD;
    return toFeedback(
        eventId,
        Feedback.focusDirection(direction)
            .setInputMode(INPUT_MODE_KEYBOARD)
            .setHtmlTargetType(targetType));
  }

  private static Feedback toFeedback(
      @Nullable EventId eventId, Feedback.FocusDirection.Builder focusDirection) {
    return Feedback.create(
        eventId, Feedback.part().setFocusDirection(focusDirection.build()).build());
  }

  private static Feedback toFeedback(@Nullable EventId eventId, Feedback.Focus.Builder focus) {
    return Feedback.create(eventId, Feedback.part().setFocus(focus.build()).build());
  }
}
