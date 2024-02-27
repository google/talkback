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

import com.google.android.accessibility.utils.LocaleUtils;
import com.google.android.accessibility.utils.PackageManagerUtils;
import com.google.android.accessibility.utils.input.TextEventInterpretation;
import com.google.android.libraries.accessibility.utils.log.LogUtils;
import java.util.Locale;

/**
 * Utils class that provides common methods that provide accessibility information by {@link
 * EventInterpretation} for compositor event feedback output.
 */
public final class AccessibilityInterpretationFeedbackUtils {

  private static final String TAG = "AccessibilityInterpretationUtils";

  private AccessibilityInterpretationFeedbackUtils() {}

  /**
   * Returns {@link TextEventInterpretation}.
   *
   * <p>Note: Fallbacks to return an empty interpretation if the input is null.
   */
  public static TextEventInterpretation safeTextInterpretation(EventInterpretation interpretation) {
    if (interpretation != null) {
      TextEventInterpretation textInterpretation = interpretation.getText();
      if (textInterpretation != null) {
        return textInterpretation;
      }
    }
    LogUtils.w(TAG, "Falling back to safe TextEventInterpretation");
    return new TextEventInterpretation(Compositor.EVENT_UNKNOWN);
  }

  /**
   * Returns {@link AccessibilityFocusEventInterpretation}.
   *
   * <p>Note: Fallbacks to return an empty interpretation if the input is null.
   */
  public static AccessibilityFocusEventInterpretation safeAccessibilityFocusInterpretation(
      EventInterpretation interpretation) {
    if (interpretation != null) {
      AccessibilityFocusEventInterpretation a11yFocusInterpretation =
          interpretation.getAccessibilityFocusInterpretation();
      if (a11yFocusInterpretation != null) {
        return a11yFocusInterpretation;
      }
    }
    LogUtils.w(TAG, "Falling back to safe AccessibilityFocusEventInterpretation");
    return new AccessibilityFocusEventInterpretation(Compositor.EVENT_UNKNOWN);
  }

  /** Returns the event traversed text for TextEventInterpretation. */
  public static CharSequence getEventTraversedText(
      EventInterpretation interpretation, Locale locale) {
    CharSequence traversedText = safeTextInterpretation(interpretation).getTraversedText();
    /**
     * Wrap the text with user preferred locale changed using language switcher, with an exception
     * for all talkback created events. As talkback text is always in the system language.
     */
    if (PackageManagerUtils.isTalkBackPackage(interpretation.getPackageName())) {
      return traversedText;
    }
    return (traversedText == null) ? "" : LocaleUtils.wrapWithLocaleSpan(traversedText, locale);
  }
}
