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

package com.google.android.accessibility.braille.brailledisplay.controller.rule;

import static com.google.android.accessibility.utils.AccessibilityNodeInfoUtils.FILTER_SHOULD_FOCUS;

import android.content.Context;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.widget.AbsSeekBar;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.QuickContactBadge;
import androidx.annotation.Nullable;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import com.google.android.accessibility.braille.brailledisplay.R;
import com.google.android.accessibility.braille.brailledisplay.controller.BdController.BehaviorNodeText;
import com.google.android.accessibility.braille.brailledisplay.controller.utils.StringUtils;
import com.google.android.accessibility.braille.common.BrailleCommonUtils;
import com.google.android.accessibility.utils.AccessibilityNodeInfoUtils;

/** Default rule that adds the text of the node and a check mark if the node is checkable. */
public class DefaultBrailleRule implements BrailleRule {

  /** Interface for converting {@link AccessibilityNodeInfoCompat} into {@link CharSequence}. */
  public interface NodeTextSupplier {
    CharSequence getNodeText(AccessibilityNodeInfoCompat node);

    boolean needsLabel(AccessibilityNodeInfoCompat node);
  }

  private final BehaviorNodeText nodeTextSupplier;

  public DefaultBrailleRule(BehaviorNodeText nodeTextSupplier) {
    this.nodeTextSupplier = nodeTextSupplier;
  }

  @Override
  public boolean accept(AccessibilityNodeInfoCompat node) {
    return true;
  }

  @Override
  public CharSequence format(Context context, AccessibilityNodeInfoCompat node) {
    SpannableStringBuilder result = new SpannableStringBuilder();
    CharSequence text = getNodeText(node);
    if (TextUtils.isEmpty(text)) {
      AccessibilityNodeInfoCompat labeledBy = node.getLabeledBy();
      if (labeledBy != null) {
        text = getNodeText(labeledBy);
      }
    }
    if (TextUtils.isEmpty(text) && nodeTextSupplier.needsLabel(node)) {
      text = getFallbackText(context, node);
    }
    if (text == null) {
      text = "";
    }

    StringUtils.appendWithSpaces(result, BrailleCommonUtils.filterNonPrintCharacter(text));

    if (node.isCheckable() || node.isChecked()) {
      CharSequence mark;
      if (node.isChecked()) {
        mark = context.getString(R.string.checkmark_checked);
      } else {
        mark = context.getString(R.string.checkmark_not_checked);
      }
      StringUtils.appendWithSpaces(result, mark);
    }
    return result;
  }

  @Override
  public boolean includeChildren(AccessibilityNodeInfoCompat node) {
    // This is intended to deal with containers that have content
    // descriptions (such as in the launcher).  This might need to be
    // refined if it takes away text that won't get accessibility focus by
    // itself.
    if (!TextUtils.isEmpty(node.getContentDescription())) {
      AccessibilityNodeInfoCompat firstDescendant =
          AccessibilityNodeInfoUtils.getMatchingDescendant(node, FILTER_SHOULD_FOCUS);
      return firstDescendant == null;
    }
    return true;
  }

  private CharSequence getFallbackText(Context context, AccessibilityNodeInfoCompat node) {
    // Order is important below because of class inheritance.
    if (matchesAny(node, Button.class, ImageButton.class)) {
      return context.getString(R.string.type_unlabeled_button);
    }
    if (matchesAny(node, QuickContactBadge.class)) {
      return context.getString(R.string.type_unlabeled_quickcontact);
    }
    if (matchesAny(node, ImageView.class)) {
      return context.getString(R.string.type_unlabeled_image);
    }
    if (matchesAny(node, EditText.class)) {
      return context.getString(R.string.type_unlabeled_edittext);
    }
    if (matchesAny(node, AbsSeekBar.class)) {
      return context.getString(R.string.type_unlabeled_seekbar);
    }
    return context.getString(R.string.type_unlabeled);
  }

  private boolean matchesAny(AccessibilityNodeInfoCompat node, Class<?>... classes) {
    for (Class<?> clazz : classes) {
      if (AccessibilityNodeInfoUtils.nodeMatchesClassByType(node, clazz)) {
        return true;
      }
    }
    return false;
  }

  @Nullable
  private CharSequence getNodeText(AccessibilityNodeInfoCompat node) {
    if (node == null) {
      return null;
    }
    CharSequence nodeText = AccessibilityNodeInfoUtils.getNodeText(node);
    if (!TextUtils.isEmpty(nodeText)) {
      return nodeText;
    }
    CharSequence hint = AccessibilityNodeInfoUtils.getHintText(node);
    if (!TextUtils.isEmpty(hint)) {
      return hint;
    }
    return nodeTextSupplier.getCustomLabelText(node);
  }
}
