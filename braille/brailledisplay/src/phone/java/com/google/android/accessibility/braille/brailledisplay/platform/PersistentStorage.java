/*
 * Copyright 2021 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.android.accessibility.braille.brailledisplay.platform;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Pair;
import com.google.android.accessibility.braille.brailledisplay.platform.lib.SharedPreferencesStringList;
import com.google.android.accessibility.utils.SharedPreferencesUtils;
import java.util.ArrayList;
import java.util.List;

/** Handles the persistence of Braille Display related data, often user preferences. */
public class PersistentStorage {

  private static final String FILENAME = "braille_display";

  public static final String REMEMBERED_DEVICES_WITH_ADDRESSES =
      "remembered_devices_with_addresses";
  public static final String PREF_AUTO_CONNECT = "auto_connect";
  public static final String PREF_CONNECTION_ENABLED = "connection_enabled_by_user";
  private static final String DELIMITER = "$";

  public static void registerListener(
      Context context, SharedPreferences.OnSharedPreferenceChangeListener changeListener) {
    getSharedPrefs(context).registerOnSharedPreferenceChangeListener(changeListener);
  }

  public static void unregisterListener(
      Context context, SharedPreferences.OnSharedPreferenceChangeListener changeListener) {
    getSharedPrefs(context).unregisterOnSharedPreferenceChangeListener(changeListener);
  }

  public static void addRememberedDevice(Context context, Pair<String, String> deviceInfo) {
    String deviceInfoString =
        concatenateWithDelimiter(DELIMITER, deviceInfo.first, deviceInfo.second);
    SharedPreferencesStringList.insertAtFront(
        getSharedPrefs(context), REMEMBERED_DEVICES_WITH_ADDRESSES, deviceInfoString);
  }

  public static void deleteRememberedDevice(Context context, Pair<String, String> deviceInfo) {
    String deviceInfoString =
        concatenateWithDelimiter(DELIMITER, deviceInfo.first, deviceInfo.second);
    SharedPreferencesStringList.remove(
        getSharedPrefs(context), REMEMBERED_DEVICES_WITH_ADDRESSES, deviceInfoString);
  }

  public static List<Pair<String, String>> getRememberedDevices(Context context) {
    List<String> deviceInfoStrings =
        SharedPreferencesStringList.getList(
            getSharedPrefs(context), REMEMBERED_DEVICES_WITH_ADDRESSES);
    List<Pair<String, String>> deviceInfos = new ArrayList<>();
    for (String deviceInfoString : deviceInfoStrings) {
      int indexOfDelimiter = deviceInfoString.lastIndexOf(DELIMITER);
      deviceInfos.add(
          new Pair<>(
              deviceInfoString.substring(0, indexOfDelimiter),
              deviceInfoString.substring(indexOfDelimiter + DELIMITER.length())));
    }
    return deviceInfos;
  }

  public static void setAutoConnect(Context context, boolean autoConnect) {
    getSharedPrefs(context).edit().putBoolean(PREF_AUTO_CONNECT, autoConnect).apply();
  }

  public static boolean isAutoConnect(Context context) {
    return getSharedPrefs(context).getBoolean(PREF_AUTO_CONNECT, true);
  }

  public static void setConnectionEnabledByUser(Context context, boolean enabled) {
    getSharedPrefs(context).edit().putBoolean(PREF_CONNECTION_ENABLED, enabled).apply();
  }

  public static boolean isConnectionEnabledByUser(Context context) {
    return getSharedPrefs(context).getBoolean(PREF_CONNECTION_ENABLED, false);
  }

  private static SharedPreferences getSharedPrefs(Context context) {
    return SharedPreferencesUtils.getSharedPreferences(context, FILENAME);
  }

  private static String concatenateWithDelimiter(String delimiter, String first, String second) {
    return first + delimiter + second;
  }
}
