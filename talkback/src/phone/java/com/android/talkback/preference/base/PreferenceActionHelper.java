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

package com.google.android.accessibility.talkback.preference.base;

import androidx.annotation.StringRes;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import com.google.android.accessibility.talkback.R;
import com.google.android.accessibility.utils.PreferenceSettingsUtils;

// TODO
/** A form factor helper to address preference events in Handset devices . */
public final class PreferenceActionHelper {

  /** Enum for information of a web page. */
  public enum WebPage {
    WEB_PAGE_PRIVACY_POLICY(R.string.privacy_policy_url),
    WEB_PAGE_TERMS_OF_SERVICE(R.string.tos_url);

    @StringRes final int urlRes;

    WebPage(int urlRes) {
      this.urlRes = urlRes;
    }
  }

  /**
   * Assigns an URL intent to the preference. When clicking the preference, it would jump to URL.
   *
   * @param fragment PreferenceFragmentCompat to get context
   * @param preference Preference to send Intent
   * @param webPage WebPage which is shown for the preference
   */
  public static void assignWebIntentToPreference(
      PreferenceFragmentCompat fragment, Preference preference, WebPage webPage) {
    PreferenceSettingsUtils.assignWebIntentToPreference(
        fragment, preference, fragment.getString(webPage.urlRes));
  }

  private PreferenceActionHelper() {}
}
