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

import static android.text.style.SuggestionSpan.FLAG_GRAMMAR_ERROR;
import static com.google.android.accessibility.talkback.Feedback.EditText.Action.TYPO_CORRECTION;
import static com.google.android.accessibility.utils.Performance.EVENT_ID_UNTRACKED;

import android.content.Context;
import android.text.BidiFormatter;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.TextDirectionHeuristics;
import android.text.style.SuggestionSpan;
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
    return !AccessibilityNodeInfoUtils.getSpellingSuggestions(context, editingNode).isEmpty();
  }

  @Override
  public List<ContextMenuItem> getMenuItemsForNode(
      Context context, AccessibilityNodeInfoCompat node, boolean includeAncestors) {
    List<ContextMenuItem> suggestionItems = new ArrayList<>();

    AccessibilityNodeInfoCompat editingNode = getEditingNode(node);
    SpellingSuggestion targetSpellingSuggestion = getTargetSpellingSuggestion(context, editingNode);
    if (targetSpellingSuggestion == null) {
      return suggestionItems;
    }

    SuggestionSpan span = targetSpellingSuggestion.suggestionSpan();
    boolean isTypo = (span.getFlags() & FLAG_GRAMMAR_ERROR) == 0;
    String[] suggestions = span.getSuggestions();
    int suggestionCount = suggestions.length;
    for (int i = 0; i < suggestionCount; i++) {
      String suggestion = suggestions[i];
      // The spelling and grammar suggestion will be read out as a word, then characters will be
      // read out verbatim.
      SpannableStringBuilder title =
          new SpannableStringBuilder(
              context.getString(
                  isTypo
                      ? R.string.title_edittext_typo_suggestion
                      : R.string.title_edittext_grammar_suggestion,
                  suggestion));
      title.append(
          " ", new TtsSpan.VerbatimBuilder(suggestion).build(), Spannable.SPAN_INCLUSIVE_EXCLUSIVE);
      title.append(
          " ",
          new TtsSpan.TextBuilder(
                  context.getString(
                      R.string.title_edittext_typo_suggestion_number, i + 1, suggestionCount))
              .build(),
          Spannable.SPAN_INCLUSIVE_EXCLUSIVE);
      ContextMenuItem suggestionItem =
          ContextMenu.createMenuItem(
              context,
              /* groupId= */ Menu.NONE,
              /* itemId= */ R.id.typo_suggestions_menu,
              /* order= */ Menu.NONE,
              // Forces the text direction to the default locale direction.
              BidiFormatter.getInstance().unicodeWrap(title, TextDirectionHeuristics.LOCALE));
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

  @Nullable
  private static SpellingSuggestion getTargetSpellingSuggestion(
      Context context, @Nullable AccessibilityNodeInfoCompat node) {
    if (node == null) {
      return null;
    }

    // Refreshes the node to update suggestion spans.
    if (!node.refresh()) {
      LogUtils.v(TAG, "Fail to refresh node.");
      return null;
    }

    List<SpellingSuggestion> spellingSuggestions =
        AccessibilityNodeInfoUtils.getSpellingSuggestions(context, node);

    return spellingSuggestions.isEmpty() ? null : spellingSuggestions.get(0);
  }
}
