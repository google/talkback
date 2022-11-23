/*
 * Copyright (C) 2012 Google Inc.
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

package com.google.android.accessibility.braille.brailledisplay.controller;

import android.content.Context;
import android.text.Editable;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ClickableSpan;
import android.text.style.URLSpan;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import androidx.annotation.Nullable;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import com.google.android.accessibility.braille.brailledisplay.R;
import com.google.android.accessibility.braille.brailledisplay.controller.BdController.BehaviorNodeText;
import com.google.android.accessibility.braille.brailledisplay.controller.rule.BrailleRule;
import com.google.android.accessibility.braille.brailledisplay.controller.rule.DefaultBrailleRule;
import com.google.android.accessibility.braille.brailledisplay.controller.rule.VerticalContainerBrailleRule;
import com.google.android.accessibility.braille.brailledisplay.controller.utils.StringUtils;
import com.google.android.accessibility.utils.AccessibilityNodeInfoUtils;
import com.google.android.accessibility.utils.Role;
import java.util.ArrayList;
import java.util.List;

/** Turns a subset of the node tree into braille. */
public class NodeBrailler {
  private final Context context;
  private final List<BrailleRule> rules = new ArrayList<>();
  private static final String BRAILLE_UNICODE_CLICKABLE = "⠿⠄";
  private static final String BRAILLE_UNICODE_LONG_CLICKABLE = "⠿⠤";

  public NodeBrailler(Context context, BehaviorNodeText behaviorNodeText) {
    this.context = context;
    rules.add(new VerticalContainerBrailleRule());
    rules.add(new DefaultBrailleRule(behaviorNodeText));
  }

  /**
   * Converts the source of {@code event} and its surroundings to annotated text to put on the
   * braille display. Returns the new content, or {@code null} if the event doesn't have a source
   * node.
   */
  public CellsContent brailleNode(AccessibilityNodeInfoCompat node) {
    AccessibilityNodeInfoCompat toFormat = AccessibilityNodeInfoCompat.obtain(node);
    CellsContent content = new CellsContent(formatSubtree(toFormat));
    content.setFirstNode(toFormat).setLastNode(toFormat);
    return content;
  }

  /** Formats {@code node} and its descendants. */
  private CharSequence formatSubtree(AccessibilityNodeInfoCompat node) {
    if (!node.isVisibleToUser()) {
      return "";
    }
    CharSequence subtreeResult = appendNonFocusableChildren(node);
    SpannableStringBuilder result = new SpannableStringBuilder();
    // Only append the node when it has text. Otherwise append the node when subtreeResult is still
    // empty.
    if (AccessibilityNodeInfoUtils.FILTER_HAS_TEXT.accept(node)
        || TextUtils.isEmpty(subtreeResult)) {
      find(node).format(result, context, node);
    }
    StringUtils.appendWithSpaces(result, subtreeResult);

    if (node.isAccessibilityFocused() && !TextUtils.isEmpty(result)) {
      DisplaySpans.addSelection(result, /* start= */ 0, /* end= */ 0);
    }
    addInlineClickableLabel(result);
    StringUtils.appendWithSpaces(result, getSuffixLabelForNode(context, node));
    addAccessibilityNodeSpanForUncovered(node, result);
    return result;
  }

  private void addInlineClickableLabel(Editable editable) {
    final ClickableSpan[] spans = editable.getSpans(0, editable.length(), ClickableSpan.class);
    for (int i = spans.length - 1; i >= 0; i--) {
      int start = editable.getSpanStart(spans[i]);
      int end = editable.getSpanEnd(spans[i]);
      SpannableString label =
          spans[i] instanceof URLSpan
              ? new SpannableString(context.getString(R.string.bd_affix_label_link))
              : new SpannableString(BRAILLE_UNICODE_CLICKABLE);
      StringUtils.insertWithSpaces(editable, end, label);
      editable.setSpan(spans[i], start, end + label.length() + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }
  }

  private CharSequence appendNonFocusableChildren(AccessibilityNodeInfoCompat node) {
    SpannableStringBuilder subtreeResult = new SpannableStringBuilder();
    for (int i = 0; i < node.getChildCount(); i++) {
      AccessibilityNodeInfoCompat child = node.getChild(i);
      if (!AccessibilityNodeInfoUtils.FILTER_NON_FOCUSABLE_VISIBLE_NODE.accept(child)) {
        continue;
      }
      find(child).format(subtreeResult, context, child);
      StringUtils.appendWithSpaces(subtreeResult, appendNonFocusableChildren(child));
    }
    return subtreeResult;
  }

  /** Returns a user-facing, possibly-empty suffix label for the node, such as "btn" for button. */
  private CharSequence getSuffixLabelForNode(Context context, AccessibilityNodeInfoCompat node) {
    Role.getRole(node);
    StringBuilder result = new StringBuilder();
    if (node.isSelected()) {
      StringUtils.appendWithSpaces(result, context.getString(R.string.bd_affix_label_selected));
    }
    if (AccessibilityNodeInfoUtils.isHeading(node)) {
      StringUtils.appendWithSpaces(
          result, context.getString(R.string.bd_affix_label_heading_no_level));
    }
    if (AccessibilityNodeInfoUtils.isLongClickable(node) && node.isEnabled()) {
      StringUtils.appendWithSpaces(result, BRAILLE_UNICODE_LONG_CLICKABLE);
    } else if (AccessibilityNodeInfoUtils.isClickable(node) && node.isEnabled()) {
      StringUtils.appendWithSpaces(result, BRAILLE_UNICODE_CLICKABLE);
    }
    if (!node.isCheckable()
        && (AccessibilityNodeInfoUtils.nodeMatchesClassByType(node, Button.class)
            || AccessibilityNodeInfoUtils.nodeMatchesClassByType(node, ImageButton.class)
            || (AccessibilityNodeInfoUtils.nodeMatchesClassByType(node, ImageView.class)
                && AccessibilityNodeInfoUtils.isClickable(node)))) {
      StringUtils.appendWithSpaces(result, context.getString(R.string.bd_affix_label_button));
    }
    if (AccessibilityNodeInfoUtils.nodeMatchesClassByType(node, EditText.class)) {
      StringUtils.appendWithSpaces(
          result, context.getString(R.string.bd_affix_label_editable_text));
    }
    if (AccessibilityNodeInfoUtils.nodeMatchesClassByType(node, ImageView.class)) {
      StringUtils.appendWithSpaces(result, context.getString(R.string.bd_affix_label_graphic));
    }
    return result;
  }

  @Nullable
  private BrailleRule find(AccessibilityNodeInfoCompat node) {
    return rules.stream().filter(r -> r.accept(node)).findFirst().orElse(null);
  }

  /**
   * Adds {@code node} as a span on {@code content} if not already fully covered by an accessibility
   * node info span.
   */
  private void addAccessibilityNodeSpanForUncovered(
      AccessibilityNodeInfoCompat node, Spannable spannable) {
    AccessibilityNodeInfoCompat[] spans =
        spannable.getSpans(0, spannable.length(), AccessibilityNodeInfoCompat.class);
    for (AccessibilityNodeInfoCompat span : spans) {
      if (spannable.getSpanStart(span) == 0 && spannable.getSpanEnd(span) == spannable.length()) {
        return;
      }
    }
    DisplaySpans.setAccessibilityNode(spannable, node);
  }
}
