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
import android.view.View;
import androidx.annotation.Nullable;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

/** A dummy class for interaction with the remote device. */
public class RemoteIntentUtils {

  /** An interface invoked when the animation is finished. */
  public interface OnRemoteIntentAnimationFinishedListener {
    void onAnimationFinished(boolean success);
  }

  public static void assignWebIntentToPreference(
      PreferenceFragmentCompat fragment, Preference preference, String url) {}

  public static void assignWebIntentToPreference(
      PreferenceFragmentCompat fragment,
      Preference preference,
      String url,
      OnRemoteIntentAnimationFinishedListener listener) {}

  public static void startRemoteActivityToOpenUriOnPhone(
      Uri uri,
      Context context,
      Activity activity,
      @Nullable OnRemoteIntentAnimationFinishedListener listener) {}

  public static void startRemoteActivityToOpenUriOnPhone(
      Uri uri,
      Context context,
      View rootView,
      @Nullable OnRemoteIntentAnimationFinishedListener listener) {}

  private RemoteIntentUtils() {}
}
