/*
 * Copyright (C) 2017 The Android Open Source Project
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

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import com.google.android.libraries.accessibility.utils.log.LogUtils;

/** Utility class containing operations for networking. */
public final class NetworkUtils {
  private static final String TAG = "ResourceUtils";

  private NetworkUtils() {}

  /**
   * Method to determine whether the network capabilities is available.
   *
   * @return true if the device hav network functioning.
   */
  public static boolean isNetworkConnected(Context context) {
    ConnectivityManager connectivityManager =
        (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
    Network activeNetwork = connectivityManager.getActiveNetwork();
    if (activeNetwork == null) {
      LogUtils.v(TAG, "No active network.");
      return false;
    }

    NetworkCapabilities networkCapabilities =
        connectivityManager.getNetworkCapabilities(activeNetwork);
    if (networkCapabilities == null) {
      LogUtils.v(TAG, "Can't get the capability of the active network.");
      return false;
    }

    if (!networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        || !networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) {
      LogUtils.v(TAG, "No or unverified network capabilities");
      return false;
    }
    return true;
  }
}
