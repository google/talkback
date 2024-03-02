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
import android.view.accessibility.AccessibilityEvent;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.Spinner;
import androidx.annotation.Nullable;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import com.google.android.accessibility.braille.brailledisplay.R;
import com.google.android.accessibility.braille.brailledisplay.controller.BdController.BehaviorNodeText;
import com.google.android.accessibility.braille.brailledisplay.controller.rule.BrailleRule;
import com.google.android.accessibility.braille.brailledisplay.controller.rule.DefaultBrailleRule;
import com.google.android.accessibility.braille.brailledisplay.controller.utils.StringUtils;
import com.google.android.accessibility.utils.AccessibilityEventUtils;
import com.google.android.accessibility.utils.AccessibilityNodeInfoUtils;
import com.google.android.accessibility.utils.Role;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** Turns a subset of the node tree into braille. */
public class NodeBrailler {
  private final Context context;
  private final List<BrailleRule> rules = new ArrayList<>();
  private static final String BRAILLE_UNICODE_CLICKABLE = "⠿⠄";
  private static final String BRAILLE_UNICODE_LONG_CLICKABLE = "⠿⠤";
  private static final char NEW_LINE = '\n';
  private static final int MAX_HEADING_LEVEL = 7;

  public NodeBrailler(Context context, BehaviorNodeText behaviorNodeText) {
    this.context = context;
    rules.add(new DefaultBrailleRule(behaviorNodeText));
  }

  /**
   * Converts {@code AccessibilityNodeInfoCompat} to annotated text to put on the braille display.
   * Returns the new content, or {@code null} if the event doesn't have a source node.
   */
  public CellsContent brailleNode(AccessibilityNodeInfoCompat node) {
    CellsContent content = new CellsContent(formatSubtree(node, /* event= */ null));
    return content;
  }

  /**
   * Converts the source of {@code event} and its surroundings to annotated text to put on the
   * braille display. Returns the new content, or {@code null} if the event doesn't have a source
   * node.
   */
  public CellsContent brailleEvent(AccessibilityEvent event) {
    AccessibilityNodeInfoCompat node = AccessibilityEventUtils.sourceCompat(event);
    CellsContent content = new CellsContent(formatSubtree(node, event));
    return content;
  }

  /** Formats {@code node} and its descendants or extract text and description of {@code event}. */
  private CharSequence formatSubtree(AccessibilityNodeInfoCompat node, AccessibilityEvent event) {
    if (!node.isVisibleToUser()) {
      return "";
    }
    SpannableStringBuilder result;
    if (shouldAppendChildrenText(node)) {
      List<AccessibilityNodeInfoCompat> nodeTree = obtainNodeTreePreorder(node);
      result = new SpannableStringBuilder(formatAndRemoveContinuousSameTextNode(nodeTree));
    } else {
      result = new SpannableStringBuilder();
      result.append(find(node).format(context, node));
    }
    if (TextUtils.isEmpty(result) && event != null) {
      result = new SpannableStringBuilder(AccessibilityEventUtils.getEventTextOrDescription(event));
    }
    if (node.isAccessibilityFocused() && !TextUtils.isEmpty(result)) {
      DisplaySpans.addSelection(result, /* start= */ 0, /* end= */ 0);
    }
    addInlineClickableLabel(result);
    StringUtils.appendWithSpaces(result, getSuffixLabelForNode(context, node));
    addSpaceAfterNewLine(result);
    addAccessibilityNodeSpanForUncovered(node, result);
    return result;
  }

  private boolean shouldAppendChildrenText(AccessibilityNodeInfoCompat node) {
    // Follow the same adding children rule with TalkBack compositor.
    int role = Role.getRole(node);
    return (role == Role.ROLE_GRID
            || role == Role.ROLE_LIST
            || role == Role.ROLE_PAGER
            || TextUtils.isEmpty(node.getContentDescription()))
        && role != Role.ROLE_WEB_VIEW;
  }

  private void addSpaceAfterNewLine(SpannableStringBuilder text) {
    for (int i = text.length() - 1; i >= 0; i--) {
      if (text.charAt(i) == NEW_LINE) {
        text.insert(i + 1, " ");
      }
    }
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

  private List<AccessibilityNodeInfoCompat> obtainNodeTreePreorder(
      AccessibilityNodeInfoCompat root) {
    List<AccessibilityNodeInfoCompat> result = new ArrayList<>();
    result.add(root);
    for (int i = 0; i < root.getChildCount(); i++) {
      AccessibilityNodeInfoCompat child = root.getChild(i);
      if (AccessibilityNodeInfoUtils.FILTER_NON_FOCUSABLE_VISIBLE_NODE.accept(child)
          || AccessibilityNodeInfoUtils.FILTER_NON_FOCUSABLE_NON_VISIBLE_HAS_TEXT_NODE.accept(
              child)) {
        result.addAll(obtainNodeTreePreorder(child));
      }
    }
    return result;
  }

  /**
   * Traverses and compares {@link AccessibilityNodeInfoCompat} in the list in order and remove the
   * continuous {@link AccessibilityNodeInfoCompat} with absolutely same text.
   */
  private CharSequence formatAndRemoveContinuousSameTextNode(
      List<AccessibilityNodeInfoCompat> list) {
    if (list.isEmpty()) {
      return "";
    }
    SpannableStringBuilder result = new SpannableStringBuilder();
    CharSequence previous = find(list.get(0)).format(context, list.get(0));
    CharSequence current;
    StringUtils.appendWithSpaces(result, previous);
    for (int i = 1; i < list.size(); i++) {
      current = find(list.get(i)).format(context, list.get(i));
      if (!previous.toString().contentEquals(current)) {
        StringUtils.appendWithSpaces(result, current);
      }
      previous = current;
    }
    return result;
  }

  /** Returns a user-facing, possibly-empty suffix label for the node, such as "btn" for button. */
  private CharSequence getSuffixLabelForNode(Context context, AccessibilityNodeInfoCompat node) {
    boolean shouldCheckClickable = false;
    Role.getRole(node);
    StringBuilder result = new StringBuilder();

    if (node.isSelected()) {
      StringUtils.appendWithSpaces(result, context.getString(R.string.bd_affix_label_selected));
    }

    String heading = getHeadingString(node.getRoleDescription());
    if (TextUtils.isEmpty(heading)) {
      if (AccessibilityNodeInfoUtils.isHeading(node)) {
        StringUtils.appendWithSpaces(
            result, context.getString(R.string.bd_affix_label_heading_no_level));
      }
    } else {
      StringUtils.appendWithSpaces(result, heading);
    }

    if (AccessibilityNodeInfoUtils.isExpandable(node)) {
      StringUtils.appendWithSpaces(result, context.getString(R.string.bd_affix_label_collapsed));
    } else if (AccessibilityNodeInfoUtils.isCollapsible(node)) {
      StringUtils.appendWithSpaces(result, context.getString(R.string.bd_affix_label_expanded));
    }

    if (!node.isCheckable()
        && (AccessibilityNodeInfoUtils.nodeMatchesClassByType(node, Button.class)
            || AccessibilityNodeInfoUtils.nodeMatchesClassByType(node, ImageButton.class)
            || (AccessibilityNodeInfoUtils.nodeMatchesClassByType(node, ImageView.class)
                && AccessibilityNodeInfoUtils.isClickable(node)))) {
      StringUtils.appendWithSpaces(result, context.getString(R.string.bd_affix_label_button));
    } else if (AccessibilityNodeInfoUtils.nodeMatchesClassByType(node, EditText.class)) {
      if (node.isMultiLine()) {
        StringUtils.appendWithSpaces(
            result, context.getString(R.string.bd_affix_label_multiple_line));
      }
      StringUtils.appendWithSpaces(
          result, context.getString(R.string.bd_affix_label_editable_text));
    } else if (AccessibilityNodeInfoUtils.nodeMatchesClassByType(node, Spinner.class)) {
      StringUtils.appendWithSpaces(
          result, context.getString(R.string.bd_affix_label_drop_down_list));
    } else {
      shouldCheckClickable = true;
    }

    if (!node.isEnabled()) {
      StringUtils.appendWithSpaces(result, context.getString(R.string.bd_affix_label_disabled));
    }

    if (AccessibilityNodeInfoUtils.nodeMatchesClassByType(node, ImageView.class)) {
      StringUtils.appendWithSpaces(result, context.getString(R.string.bd_affix_label_graphic));
    }

    if (shouldCheckClickable) {
      if (AccessibilityNodeInfoUtils.isLongClickable(node) && node.isEnabled()) {
        StringUtils.appendWithSpaces(result, BRAILLE_UNICODE_LONG_CLICKABLE);
      } else if (AccessibilityNodeInfoUtils.isClickable(node) && node.isEnabled()) {
        StringUtils.appendWithSpaces(result, BRAILLE_UNICODE_CLICKABLE);
      }
    }

    return result;
  }

  private String getHeadingString(CharSequence roleDescription) {
    if (!TextUtils.isEmpty(roleDescription)) {
      for (int i = 1; i < MAX_HEADING_LEVEL; i++) {
        if (Pattern.matches(
            ".*" + context.getString(R.string.bd_role_description_heading, i) + ".*",
            roleDescription)) {
          return context.getString(R.string.bd_affix_label_heading_with_level, i);
        }
      }
    }
    return "";
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
