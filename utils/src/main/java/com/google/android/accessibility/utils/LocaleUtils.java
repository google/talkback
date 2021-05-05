/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.google.android.accessibility.utils;

import android.text.SpannableString;
import android.text.TextUtils;
import android.text.style.LocaleSpan;
import java.util.Locale;
import org.checkerframework.checker.nullness.qual.Nullable;

/** Manages parsing for the locale. */
public class LocaleUtils {

  public static final String LANGUAGE_EN = "en";

  // Extracts the locale. Keeps language and country but drops the variant.
  // Returns null on failure.
  public static @Nullable Locale parseLocaleString(String localeString) {
    if (TextUtils.isEmpty(localeString)) {
      return null;
    }
    String[] localeParts = localeString.split("_", 3);

    if (localeParts.length >= 2) {
      return new Locale(localeParts[0], localeParts[1]);
    } else if (localeParts.length >= 1) {
      return new Locale(localeParts[0]);
    } else {
      return null;
    }
  }

  /**
   * Wraps the {@link text} with {@link preferredLocale}. If a LocaleSpan is already attached to the
   * {@link text}, {@link SpannableString#setSpan} will add a second LocaleSpan.
   */
  public static @Nullable CharSequence wrapWithLocaleSpan(
      @Nullable CharSequence text, @Nullable Locale preferredLocale) {
    if (text != null && preferredLocale != null) {
      SpannableString textToBeWrapped = new SpannableString(text);
      textToBeWrapped.setSpan(new LocaleSpan(preferredLocale), 0, textToBeWrapped.length(), 0);
      return textToBeWrapped;
    }
    return text;
  }

  public static String getDefaultLocale() {
    String locale = Locale.getDefault().toString();
    return getLanguageLocale(locale);
  }

  public static String getLanguageLocale(String locale) {
    if (locale != null) {
      int localeDivider = locale.indexOf('_');
      if (localeDivider > 0) {
        return locale.substring(0, localeDivider);
      }
    }

    return locale;
  }

  public static boolean isDefaultLocale(String targetLocale) {
    return TextUtils.equals(getDefaultLocale(), targetLocale);
  }
}
