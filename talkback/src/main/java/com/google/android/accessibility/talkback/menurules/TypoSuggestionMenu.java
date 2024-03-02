/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.google.android.accessibility.talkback.menurules;

import static com.google.android.accessibility.talkback.Feedback.EditText.Action.TYPO_CORRECTION;
import static com.google.android.accessibility.utils.Performance.EVENT_ID_UNTRACKED;

import android.content.Context;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.TtsSpan;
import android.view.Menu;
import androidx.annotation.Nullable;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import com.google.android.accessibility.talkback.Feedback;
import com.google.android.accessibility.talkback.Pipeline;
import com.google.android.accessibility.talkback.R;
import com.google.android.accessibility.talkback.contextmenu.ContextMenu;
import com.google.android.accessibility.talkback.contextmenu.ContextMenuItem;
import com.google.android.accessibility.talkback.contextmenu.ContextMenuItem.DeferredType;
import com.google.android.accessibility.talkback.focusmanagement.AccessibilityFocusMonitor;
import com.google.android.accessibility.utils.AccessibilityNodeInfoUtils;
import com.google.android.accessibility.utils.AccessibilityNodeInfoUtils.SpellingSuggestion;
import com.google.android.accessibility.utils.Role;
import com.google.android.accessibility.utils.SharedPreferencesUtils;
import com.google.android.libraries.accessibility.utils.log.LogUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.IntStream;

/** Rule for generating menu item related to typo suggestions. */
public class TypoSuggestionMenu implements NodeMenu {

  private static final String TAG = "RuleTypoSuggestions";
  private final Pipeline.FeedbackReturner pipeline;
  private final AccessibilityFocusMonitor accessibilityFocusMonitor;

  /**
   * Constructs an instance of {@link NodeMenuRule} for typo correction.
   *
   * @param pipeline Handles {@link com.google.android.accessibility.talkback.Feedback.Speech}
   *     feedbacks.
   * @param accessibilityFocusMonitor Getting the current accessibility focus or editing node if the
   *     focus is on an IME window.
   */
  public TypoSuggestionMenu(
      Pipeline.FeedbackReturner pipeline, AccessibilityFocusMonitor accessibilityFocusMonitor) {
    this.pipeline = pipeline;
    this.accessibilityFocusMonitor = accessibilityFocusMonitor;
  }

  @Override
  public boolean accept(Context context, AccessibilityNodeInfoCompat node) {
    boolean isEnabled =
        SharedPreferencesUtils.getSharedPreferences(context)
            .getBoolean(
                context.getString(R.string.pref_show_context_menu_typo_suggestions_setting_key),
                context
                    .getResources()
                    .getBoolean(R.bool.pref_show_context_menu_typo_suggestions_default));

    if (!isEnabled) {
      return false;
    }

    AccessibilityNodeInfoCompat editingNode = getEditingNode(node);
    if (Role.getRole(editingNode) != Role.ROLE_EDIT_TEXT) {
      return false;
    }
    return !AccessibilityNodeInfoUtils.getSpellingSuggestions(editingNode).isEmpty();
  }

  @Override
  public List<ContextMenuItem> getMenuItemsForNode(
      Context context, AccessibilityNodeInfoCompat node, boolean includeAncestors) {
    List<ContextMenuItem> suggestionItems = new ArrayList<>();

    AccessibilityNodeInfoCompat editingNode = getEditingNode(node);
    SpellingSuggestion targetSpellingSuggestion = getTargetSpellingSuggestion(editingNode);
    if (targetSpellingSuggestion == null) {
      return suggestionItems;
    }

    for (String suggestion : targetSpellingSuggestion.suggestionSpan().getSuggestions()) {
      // The suggestion will be read out as a word, then characters will be read out verbatim.
      Spannable readVerbatim =
          new SpannableStringBuilder()
              .append(suggestion, new TtsSpan.TextBuilder(suggestion + ",").build(), 0)
              .append(" ", new TtsSpan.VerbatimBuilder(suggestion).build(), 0);
      Spannable title =
          formatTitle(context.getText(R.string.title_edittext_typo_suggestion), readVerbatim);
      ContextMenuItem suggestionItem =
          ContextMenu.createMenuItem(
              context,
              /* groupId= */ Menu.NONE,
              /* itemId= */ R.id.typo_suggestions_menu,
              /* order= */ Menu.NONE,
              title);
      suggestionItem.setOnMenuItemClickListener(
          menuItem -> {
            LogUtils.v(
                TAG,
                String.format(
                    Locale.ENGLISH,
                    "[%d-%d]%s replaced with %s",
                    targetSpellingSuggestion.start(),
                    targetSpellingSuggestion.end(),
                    targetSpellingSuggestion.misspelledWord(),
                    suggestion));
            return pipeline.returnFeedback(
                Feedback.create(
                    EVENT_ID_UNTRACKED,
                    Feedback.part()
                        .setEdit(
                            Feedback.edit(editingNode, TYPO_CORRECTION)
                                .setText(suggestion)
                                .setSpellingSuggestion(targetSpellingSuggestion)
                                .build())
                        .build()));
          });
      // Skip window and focued event for edit options, REFERTO.
      suggestionItem.setSkipRefocusEvents(true);
      suggestionItem.setSkipWindowEvents(true);
      suggestionItem.setDeferredType(DeferredType.ACCESSIBILITY_FOCUS_RECEIVED);
      suggestionItems.add(suggestionItem);
    }
    return suggestionItems;
  }

  private AccessibilityNodeInfoCompat getEditingNode(AccessibilityNodeInfoCompat node) {
    if (Role.getRole(node) == Role.ROLE_EDIT_TEXT) {
      return node;
    }

    AccessibilityNodeInfoCompat editingNode =
        accessibilityFocusMonitor.getEditingNodeFromFocusedKeyboard(node);
    return editingNode == null ? node : editingNode;
  }

  private static Spannable formatTitle(CharSequence format, CharSequence suggestion) {
    // We can't use String.format() because the title contains TtsSpan. So manually format the text
    // by replacing "%1$s" to the given spelling suggestion.
    int start =
        IntStream.range(0, format.length())
            .filter(i -> format.charAt(i) == '%')
            .findFirst()
            .orElse(-1);
    int end = -1;
    if ((start >= 0) && (start + 3 < format.length())) {
      if ((format.charAt(start + 1) == '1')
          && (format.charAt(start + 2) == '$')
          && (format.charAt(start + 3) == 's')) {
        end = start + 3;
      }
    }

    SpannableStringBuilder result = new SpannableStringBuilder();
    if (start >= 0) {
      // Prefix of the format.
      result.append(format.subSequence(0, start));
    }

    result.append(suggestion);

    if (end >= 0 && end < format.length()) {
      // Suffix of the format.
      result.append(format.subSequence(end + 1, format.length()));
    }

    return result;
  }

  @Nullable
  private static SpellingSuggestion getTargetSpellingSuggestion(
      @Nullable AccessibilityNodeInfoCompat node) {
    if (node == null) {
      return null;
    }

    // Refreshes the node to update suggestion spans.
    if (!node.refresh()) {
      LogUtils.v(TAG, "Fail to refresh node.");
      return null;
    }

    List<SpellingSuggestion> spellingSuggestions =
        AccessibilityNodeInfoUtils.getSpellingSuggestions(node);

    return spellingSuggestions.isEmpty() ? null : spellingSuggestions.get(0);
  }
}
