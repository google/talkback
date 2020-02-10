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

import android.os.Build;
import android.os.LocaleList;
import android.os.PersistableBundle;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import android.text.ParcelableSpan;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.SpannedString;
import android.text.TextUtils;
import android.text.style.LocaleSpan;
import android.text.style.TtsSpan;
import android.text.style.URLSpan;
import android.util.Log;
import com.google.android.libraries.accessibility.utils.log.LogUtils;
import java.util.Locale;
import java.util.Set;
import org.checkerframework.checker.nullness.qual.Nullable;

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

  public static <T> boolean isWrappedWithTargetSpan(
      CharSequence text, Class<T> spanClass, boolean shouldTrim) {
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
    T[] spans = spannable.getSpans(0, text.length(), spanClass);
    if ((spans == null) || (spans.length != 1)) {
      return false;
    }

    T span = spans[0];
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
  public static <T> @Nullable SpannableString getStringWithTargetSpan(
      AccessibilityNodeInfoCompat node, Class<T> spanClass) {

    CharSequence text = node.getContentDescription();
    if (isEmptyOrNotSpannableStringType(text)) {
      text = AccessibilityNodeInfoUtils.getText(node);
      if (isEmptyOrNotSpannableStringType(text)) {
        return null;
      }
    }

    SpannableString spannable = SpannableString.valueOf(text);
    T[] spans = spannable.getSpans(0, spannable.length(), spanClass);
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
  public static <T> void stripTargetSpanFromText(CharSequence text, Class<T> spanClass) {
    if (TextUtils.isEmpty(text) || !(text instanceof SpannableString)) {
      return;
    }
    SpannableString spannable = (SpannableString) text;
    T[] spans = spannable.getSpans(0, spannable.length(), spanClass);
    if (spans != null) {
      for (T span : spans) {
        if (span != null) {
          spannable.removeSpan(span);
        }
      }
    }
  }

  /**
   * Logs the type, position and args of spans which attach to given text, but only if log priority
   * is equal to Log.VERBOSE. Format is {type 'spanned text' extra-data} {type 'other text'
   * extra-data} ..."
   *
   * @param text Text to be logged
   */
  public static String spansToStringForLogging(CharSequence text) {
    if (!LogUtils.shouldLog(Log.VERBOSE)) {
      return null;
    }

    if (isEmptyOrNotSpannableStringType(text)) {
      return null;
    }

    Spanned spanned = (Spanned) text;
    ParcelableSpan[] spans = spanned.getSpans(0, text.length(), ParcelableSpan.class);
    if (spans.length == 0) {
      return null;
    }

    StringBuilder stringBuilder = new StringBuilder();
    for (ParcelableSpan span : spans) {
      stringBuilder.append("{");
      // Span type.
      stringBuilder.append(span.getClass().getSimpleName());

      // Span text.
      int start = spanned.getSpanStart(span);
      int end = spanned.getSpanEnd(span);
      if (start < 0 || end < 0 || start == end) {
        stringBuilder.append(" invalid index:[");
        stringBuilder.append(start);
        stringBuilder.append(",");
        stringBuilder.append(end);
        stringBuilder.append("]}");
        continue;
      } else {
        stringBuilder.append(" '");
        stringBuilder.append(spanned, start, end);
        stringBuilder.append("'");
      }

      // Extra data.
      if (span instanceof LocaleSpan) {
        LocaleSpan localeSpan = (LocaleSpan) span;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
          Locale locale = localeSpan.getLocale();
          if (locale != null) {
            stringBuilder.append(" locale=");
            stringBuilder.append(locale);
          }
        } else {
          LocaleList localeList = localeSpan.getLocales();
          int size = localeList.size();
          if (size > 0) {
            stringBuilder.append(" locale=[");
            for (int i = 0; i < size - 1; i++) {
              stringBuilder.append(localeList.get(i));
              stringBuilder.append(",");
            }
            stringBuilder.append(localeList.get(size - 1));
            stringBuilder.append("]");
          }
        }

      } else if (span instanceof TtsSpan) {
        TtsSpan ttsSpan = (TtsSpan) span;
        stringBuilder.append(" ttsType=");
        stringBuilder.append(ttsSpan.getType());
        PersistableBundle bundle = ttsSpan.getArgs();
        Set<String> keys = bundle.keySet();
        if (!keys.isEmpty()) {
          for (String key : keys) {
            stringBuilder.append(" ");
            stringBuilder.append(key);
            stringBuilder.append("=");
            stringBuilder.append(bundle.get(key));
          }
        }
      } else if (span instanceof URLSpan) {
        URLSpan urlSpan = (URLSpan) span;
        stringBuilder.append(" url=");
        stringBuilder.append(urlSpan.getURL());
      }
      stringBuilder.append("}");
    }
    return stringBuilder.toString();
  }

  private static boolean isEmptyOrNotSpannableStringType(CharSequence text) {
    return TextUtils.isEmpty(text)
        || !(text instanceof SpannedString
            || text instanceof SpannableString
            || text instanceof SpannableStringBuilder);
  }
}
