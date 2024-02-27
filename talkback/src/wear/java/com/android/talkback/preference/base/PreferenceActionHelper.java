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

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import android.view.View;
import androidx.annotation.StringRes;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import com.google.android.accessibility.talkback.R;
import com.google.android.accessibility.talkback.utils.RemoteIntentUtils;
import com.google.android.accessibility.talkback.utils.RemoteIntentUtils.OnRemoteIntentAnimationFinishedListener;

// TODO
/** A form factor helper to address preference events in Wear devices . */
public final class PreferenceActionHelper {

  /** Enum for information of a web page. */
  public enum WebPage {
    WEB_PAGE_PRIVACY_POLICY(
        R.string.privacy_policy_url,
        R.string.title_pref_show_privacy_policy,
        R.string.scan_the_qr_code,
        R.string.alternate_privacy_policy),
    WEB_PAGE_TERMS_OF_SERVICE(
        R.string.tos_url,
        R.string.title_pref_show_tos,
        R.string.scan_the_qr_code,
        R.string.alternate_tos);

    @StringRes final int urlRes;
    // Compared with other form factors, the following fields are specific to Wear.
    @StringRes final int titleRes;
    @StringRes final int callToActionRes;
    @StringRes final int alternateRes;

    WebPage(int urlRes, int titleRes, int callToActionRes, int alternateRes) {
      this.urlRes = urlRes;
      this.titleRes = titleRes;
      this.callToActionRes = callToActionRes;
      this.alternateRes = alternateRes;
    }
  }

  /**
   * Assigns an URL intent to the preference. When clicking the preference, it would open a browser
   * for the URL in the remote device. However, if we cannot reach out to the remote device, we will
   * show a QR code for this URL in the local device.
   *
   * @param fragment PreferenceFragmentCompat to get context
   * @param preference Preference to send Intent
   * @param webPage WebPage which is shown for the preference
   */
  public static void assignWebIntentToPreference(
      PreferenceFragmentCompat fragment, Preference preference, WebPage webPage) {
    String url = fragment.getString(webPage.urlRes);
    RemoteIntentUtils.assignWebIntentToPreference(
        fragment,
        preference,
        url,
        (OnRemoteIntentAnimationFinishedListener)
            success -> {
              if (!success) {

                String title = fragment.getString(webPage.titleRes);

                FragmentManager fragmentManager = fragment.getParentFragmentManager();
                Fragment oldFragment = fragmentManager.findFragmentByTag(title);
                if (oldFragment != null) {
                  // The fragment is existed so we skip this time. This issue could be derived from
                  // quickly double tapping on the preference.
                  return;
                }

                WearQRCodeFragment wearQrCodeFragment =
                    WearQRCodeFragment.createWearQrCodeFragment(
                        url,
                        title,
                        fragment.getString(webPage.callToActionRes),
                        fragment.getString(webPage.alternateRes));
                wearQrCodeFragment.setTargetFragment(fragment, 0);
                fragmentManager
                    .beginTransaction()
                    .setReorderingAllowed(true)
                    .add(
                        ((View) fragment.requireView().getParent()).getId(),
                        wearQrCodeFragment,
                        title)
                    .addToBackStack(null)
                    .commit();
              }
            });
  }

  private PreferenceActionHelper() {}
}
