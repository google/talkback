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
package com.google.android.accessibility.talkback.compositor;

import android.content.Context;
import android.text.TextUtils;
import android.view.accessibility.AccessibilityEvent;
import com.google.android.accessibility.utils.AccessibilityEventUtils;
import com.google.android.accessibility.utils.AccessibilityNodeInfoUtils;
import com.google.android.accessibility.utils.LocaleUtils;
import com.google.android.accessibility.utils.PackageManagerUtils;
import java.util.List;
import java.util.Locale;

/**
 * Utils class that provides common methods that provide accessibility information by {@link
 * AccessibilityEvent} for compositor event feedback output.
 */
public final class AccessibilityEventFeedbackUtils {

  private static final String TAG = "AccessibilityEventFeedbackUtils";

  private AccessibilityEventFeedbackUtils() {}

  /** Returns the content description by the a11y event. */
  public static CharSequence getEventContentDescription(AccessibilityEvent event, Locale locale) {
    CharSequence eventContentDescription = event.getContentDescription();
    /**
     * Wrap the text with user preferred locale changed using language switcher, with an exception
     * for all talkback created events. As talkback text is always in the system language.
     */
    if (PackageManagerUtils.isTalkBackPackage(event.getPackageName())) {
      return (eventContentDescription == null) ? "" : eventContentDescription;
    }
    // Note: mUserPreferredLocale will not override any LocaleSpan that is already attached
    // to the description. The content description will have just one LocaleSpan.
    return (eventContentDescription == null)
        ? ""
        : LocaleUtils.wrapWithLocaleSpan(eventContentDescription, locale);
  }

  /** Returns event aggregate text by the a11y event. */
  public static CharSequence getEventAggregateText(AccessibilityEvent event, Locale locale) {
    CharSequence eventText = AccessibilityEventUtils.getEventAggregateText(event);
    /**
     * Wrap the text with user preferred locale changed using language switcher, with an exception
     * for all talkback created events. As talkback text is always in the system language.
     */
    if (PackageManagerUtils.isTalkBackPackage(event.getPackageName())) {
      return (eventText == null) ? "" : eventText;
    }
    return (eventText == null) ? "" : LocaleUtils.wrapWithLocaleSpan(eventText, locale);
  }

  /**
   * Returns the content description or event aggregate text by the a11y event. If the content
   * description is empty, it fallbacks to return the event aggregate text.
   */
  public static CharSequence getEventContentDescriptionOrEventAggregateText(
      AccessibilityEvent event, Locale locale) {
    CharSequence contentDescription = getEventContentDescription(event, locale);
    return TextUtils.isEmpty(contentDescription)
        ? getEventAggregateText(event, locale)
        : contentDescription;
  }

  /**
   * Returns the event text by the a11y event. The index in the event text list represents the
   * priority of the text.
   */
  static CharSequence getEventTextFromArrayString(
      AccessibilityEvent event, int index, Locale locale) {
    List<CharSequence> texts = event.getText();
    CharSequence eventText = (texts == null || texts.isEmpty()) ? null : texts.get(index);
    /**
     * Wrap the text with user preferred locale changed using language switcher, with an exception
     * for all talkback created events. As talkback text is always in the system language.
     */
    if (PackageManagerUtils.isTalkBackPackage(event.getPackageName())) {
      return (eventText == null) ? "" : eventText;
    }
    return (eventText == null) ? "" : LocaleUtils.wrapWithLocaleSpan(eventText, locale);
  }

  /** Returns the page index count. */
  public static CharSequence getPagerIndexCount(
      AccessibilityEvent event, Context context, GlobalVariables globalVariables) {
    int fromIndex = event.getFromIndex();
    int itemCount = event.getItemCount();
    if (fromIndex >= 0 && itemCount > 0) {
      // Omit title if page is focused, because page-title will be announced for focus-event.
      CharSequence pageTitle =
          AccessibilityNodeInfoUtils.getSelectedPageTitle(
              AccessibilityNodeInfoUtils.toCompat(event.getSource()));
      if (!TextUtils.isEmpty(pageTitle) && !globalVariables.focusIsPage()) {
        return CompositorUtils.joinCharSequences(
            pageTitle,
            context.getString(
                com.google.android.accessibility.utils.R.string.template_viewpager_index_count_short, fromIndex + 1, itemCount));
      } else {
        return context.getString(com.google.android.accessibility.utils.R.string.template_viewpager_index_count, fromIndex + 1, itemCount);
      }
    }
    return "";
  }
}
