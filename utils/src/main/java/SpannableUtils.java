/*
 * Copyright (C) 2017 Google Inc.
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

package com.google.android.accessibility.utils;

import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.TextUtils;

/** Utility methods for working with spannable objects. */
public final class SpannableUtils {

  /** Identifies separators attached in spoken feedback. */
  public static class IdentifierSpan {}

  public static CharSequence wrapWithIdentifierSpan(CharSequence text) {
    if (TextUtils.isEmpty(text)) {
      return text;
    }
    SpannableString spannedText = new SpannableString(text);
    spannedText.setSpan(
        new SpannableUtils.IdentifierSpan(),
        /* start= */ 0,
        /* end= */ text.length(),
        /* flags= */ 0);
    return spannedText;
  }

  public static boolean isWrappedWithTargetSpan(
      CharSequence text, Class<?> spanClass, boolean shouldTrim) {
    if (TextUtils.isEmpty(text) || !(text instanceof Spannable)) {
      return false;
    }
    if (shouldTrim) {
      text = SpeechCleanupUtils.trimText(text);
    }
    if (TextUtils.isEmpty(text)) {
      return false;
    }
    Spannable spannable = (Spannable) text;
    Object[] spans = spannable.getSpans(0, text.length(), spanClass);
    if ((spans == null) || (spans.length != 1)) {
      return false;
    }

    Object span = spans[0];
    return (spannable.getSpanStart(span) == 0)
        && (spannable.getSpanEnd(span) == spannable.length());
  }

  /**
   * Retrieves SpannableString containing the target span in the accessibility node. The content
   * description and text of the node is checked in order.
   *
   * @param node The AccessibilityNodeInfoCompat where the text comes from.
   * @param spanClass Class of target span.
   * @return SpannableString with at least 1 target span. null if no target span found in the node.
   */
  public static SpannableString getStringWithTargetSpan(
      AccessibilityNodeInfoCompat node, Class<?> spanClass) {

    CharSequence text = node.getContentDescription();
    if (!TextUtils.isEmpty(text)) {
      if (!(text instanceof SpannableString)) {
        return null;
      }
    } else {
      text = node.getText();
      if (TextUtils.isEmpty(text) || !(text instanceof SpannableString)) {
        return null;
      }
    }

    SpannableString spannable = (SpannableString) text;
    final Object[] spans = spannable.getSpans(0, spannable.length(), spanClass);
    if (spans == null || spans.length == 0) {
      return null;
    }

    return spannable;
  }

  /**
   * Strip out all the spans of target span class from the given text.
   *
   * @param text Text to remove span.
   * @param spanClass class of span to be removed.
   */
  public static void stripTargetSpanFromText(CharSequence text, Class<?> spanClass) {
    if (TextUtils.isEmpty(text) || !(text instanceof SpannableString)) {
      return;
    }
    SpannableString spannable = (SpannableString) text;
    final Object[] spans = spannable.getSpans(0, spannable.length(), spanClass);
    if (spans != null) {
      for (Object span : spans) {
        spannable.removeSpan(span);
      }
    }
  }
}
