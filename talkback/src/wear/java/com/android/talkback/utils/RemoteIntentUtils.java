/*
 * Copyright (C) 2019 The Android Open Source Project
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
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.ResultReceiver;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.wear.widget.ConfirmationOverlay;
import com.google.android.accessibility.talkback.R;
import com.google.android.accessibility.utils.SettingsUtils;
import com.google.android.wearable.intent.RemoteIntent;

/** Utility class to access private API that allows system app to open URL on the wear. */
public class RemoteIntentUtils {

  /**
   * Assigns an URL intent to the preference. When clicking the preference, it would jump to URL.
   *
   * @param fragment PreferenceFragmentCompat to get context.
   * @param preference Preference to send Intent
   * @param url URL which launches web page
   */
  public static void assignWebIntentToPreference(
      PreferenceFragmentCompat fragment, Preference preference, String url) {
    if (!SettingsUtils.allowLinksOutOfSettings(fragment.getContext())) {
      return;
    }

    Uri uri = Uri.parse(url);
    Activity activity = fragment.getActivity();
    if (activity != null) {
      preference.setOnPreferenceClickListener(
          pref -> {
            RemoteIntentUtils.startRemoteActivityToOpenUriOnPhone(uri, activity, pref.getContext());
            return true;
          });
    }
  }

  /**
   * Convenience function to start an activity for the given {@code uri} on another device and
   * animates a success or failure message depending on the result.
   *
   * @param uri the address to be opened
   * @param activity the activity to display the confirmation over
   * @param context the context for sending the intent
   */
  public static void startRemoteActivityToOpenUriOnPhone(
      Uri uri, Activity activity, Context context) {
    RemoteIntent.startRemoteActivity(
        context,
        new Intent(Intent.ACTION_VIEW).setData(uri).addCategory(Intent.CATEGORY_BROWSABLE),
        new ConfirmationOverlayReceiver(activity));
  }

  /** A {@link ResultReceiver} that shows a {@link ConfirmationOverlay} to indicate the result. */
  private static final class ConfirmationOverlayReceiver extends ResultReceiver {
    private final Activity activity;

    public ConfirmationOverlayReceiver(Activity activity) {
      super(new Handler());
      this.activity = activity;
    }

    @Override
    protected void onReceiveResult(int resultCode, Bundle resultData) {
      ConfirmationOverlay confirmationOverlay =
          new ConfirmationOverlay()
              .setType(
                  resultCode == RemoteIntent.RESULT_OK
                      ? ConfirmationOverlay.OPEN_ON_PHONE_ANIMATION
                      : ConfirmationOverlay.FAILURE_ANIMATION);
      if (resultCode != RemoteIntent.RESULT_OK) {
        confirmationOverlay.setMessage(
            (CharSequence)
                activity.getString(R.string.watch_remote_intent_error_confirmation_text));
      }
      confirmationOverlay.showOn(activity);
    }
  }

  private RemoteIntentUtils() {}
}
