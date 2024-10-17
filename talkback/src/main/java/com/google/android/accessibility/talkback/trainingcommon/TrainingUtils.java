/*
 * Copyright (C) 2023 Google Inc.
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

package com.google.android.accessibility.talkback.trainingcommon;

import android.content.Intent;
import android.provider.Settings;
import android.view.View;
import android.view.ViewGroup;
import com.google.android.accessibility.talkback.R;
import com.google.android.accessibility.utils.AccessibilityServiceCompatUtils;
import com.google.android.accessibility.utils.PreferenceSettingsUtils;

/** Utils class provides training module related methods. */
public class TrainingUtils {
  private TrainingUtils() {}

  public static final String GUP_SUPPORT_PORTAL_URL =
      "https://support.google.com/accessibility/answer/7641084";

  public static final String VERBOSITY_OPTION_HELP_CENTER_URL =
      "https://support.google.com/accessibility/android/answer/6283655?ref_topic=10601571&sjid=10534216736552497237-NA#zippy=%2Cchange-verbosity";

  public static final String IMAGE_DESCRIPTION_SUPPORTED_LANGUAGES_URL =
      "https://support.google.com/accessibility/android/answer/11101402";

  /** Gets the height of the training navigation bar's height if existed. */
  public static int getTrainingNavBarHeight(ViewGroup root) {
    View navBar = root.findViewById(R.id.nav_container);
    if (navBar == null) {
      // No navigation bar found in root, return 0 presents it does not have height.
      return 0;
    }
    return navBar.getHeight();
  }

  /** Gets the Accessibility Settings intent and highlight on TalkBack. */
  public static Intent getAccessibilitySettingsAndHighLightTalkBackIntent() {
    Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
    intent.addFlags(
        Intent.FLAG_ACTIVITY_NEW_TASK
            | Intent.FLAG_ACTIVITY_CLEAR_TASK
            | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
    PreferenceSettingsUtils.attachSettingsHighlightBundle(
        intent, AccessibilityServiceCompatUtils.Constants.TALKBACK_SERVICE);
    return intent;
  }
}
