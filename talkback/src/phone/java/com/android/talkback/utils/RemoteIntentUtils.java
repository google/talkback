/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.google.android.accessibility.talkback.utils;

import android.app.Activity;
import android.content.Context;
import android.net.Uri;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

/**
 * Utility class to access private API that allows system app to open URL on the phone. This is fake
 * class to solve copybara issue.
 */
public class RemoteIntentUtils {
  /**
   * Assigns an URL intent to the preference. When clicking the preference, it would jump to URL. Do
   * nothing since this function is used by wear only.
   *
   * @param fragment PreferenceFragmentCompat to get context.
   * @param preference Preference to send Intent
   * @param url URL which launches web page
   */
  public static void assignWebIntentToPreference(
      PreferenceFragmentCompat fragment, Preference preference, String url) {}

  /**
   * Convenience function to start an activity for the given {@code uri} on another device and
   * animates a success or failure message depending on the result. Do nothing since this function
   * is used by wear only.
   *
   * @param uri the address to be opened
   * @param activity the activity to display the confirmation over
   * @param context the context for sending the intent
   */
  public static void startRemoteActivityToOpenUriOnPhone(
      Uri uri, Activity activity, Context context) {}

  private RemoteIntentUtils() {}
}
