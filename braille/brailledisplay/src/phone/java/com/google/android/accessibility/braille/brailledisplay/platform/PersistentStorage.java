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

import static com.google.common.collect.ImmutableSet.toImmutableSet;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;
import android.util.Pair;
import com.google.android.accessibility.braille.brailledisplay.platform.connect.device.ConnectableDevice;
import com.google.android.accessibility.braille.brailledisplay.platform.lib.SharedPreferencesStringList;
import com.google.android.accessibility.utils.SharedPreferencesUtils;
import com.google.common.base.Objects;
import com.google.common.collect.ImmutableSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Handles the persistence of Braille Display related data, often user preferences. */
public final class PersistentStorage {

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
    ImmutableSet<Pair<String, String>> nameAddressSet =
        getRememberedDevices(context).stream()
            .filter(pair -> pair.second.equals(deviceInfo.second))
            .collect(toImmutableSet());

    if (!nameAddressSet.isEmpty()) {
      // Remove duplicate devices.
      if (nameAddressSet.size() > 1) {
        int index = 0;
        for (Pair<String, String> pair : nameAddressSet) {
          if (index == 0) {
            // Keep only one.
            index++;
            continue;
          }
          deleteRememberedDevice(context, pair);
        }
      }

      // Don't save repeated device.
      return;
    }
    String deviceInfoString =
        concatenateWithDelimiter(DELIMITER, deviceInfo.first, deviceInfo.second);
    SharedPreferencesStringList.insertAtFront(
        getSharedPrefs(context), REMEMBERED_DEVICES_WITH_ADDRESSES, deviceInfoString);
  }

  /** Deletes remembered devices that match the address. */
  public static void deleteRememberedDevice(Context context, String deviceAddress) {
    ImmutableSet<Pair<String, String>> nameAddressSet =
        getRememberedDevices(context).stream()
            .filter(pair -> pair.second.equals(deviceAddress))
            .collect(toImmutableSet());
    for (Pair<String, String> pair : nameAddressSet) {
      deleteRememberedDevice(context, pair);
    }
  }

  private static void deleteRememberedDevice(Context context, Pair<String, String> deviceInfo) {
    String deviceInfoString =
        concatenateWithDelimiter(DELIMITER, deviceInfo.first, deviceInfo.second);
    SharedPreferencesStringList.remove(
        getSharedPrefs(context), REMEMBERED_DEVICES_WITH_ADDRESSES, deviceInfoString);
  }

  /** Syncs remembered device with saved device in Bluetooth settings */
  public static void syncRememberedDevice(Context context, Set<ConnectableDevice> bondedDevices) {
    List<Pair<String, String>> rememberedDeviceInfos = getRememberedDevices(context);
    for (Pair<String, String> pair : rememberedDeviceInfos) {
      ImmutableSet<ConnectableDevice> filteredBondedDevice =
          bondedDevices.stream()
              .filter(device -> device.address().equals(pair.second))
              .collect(toImmutableSet());
      if (filteredBondedDevice.isEmpty()) {
        deleteRememberedDevice(context, pair);
      } else {
        ConnectableDevice device = filteredBondedDevice.stream().findFirst().get();
        if (!Objects.equal(device.name(), pair.first)) {
          deleteRememberedDevice(context, pair);
          addRememberedDevice(context, new Pair<>(device.name(), device.address()));
        }
      }
    }
  }

  public static List<Pair<String, String>> getRememberedDevices(Context context) {
    List<String> deviceInfoStrings =
        SharedPreferencesStringList.getList(
            getSharedPrefs(context), REMEMBERED_DEVICES_WITH_ADDRESSES);
    List<Pair<String, String>> deviceInfos = new ArrayList<>();
    for (String deviceInfoString : deviceInfoStrings) {
      int indexOfDelimiter = deviceInfoString.lastIndexOf(DELIMITER);
      String name = deviceInfoString.substring(0, indexOfDelimiter);
      String address = deviceInfoString.substring(indexOfDelimiter + DELIMITER.length());
      // Ignore and remove old saved incomplete device info.
      if (TextUtils.isEmpty(name) || TextUtils.isEmpty(address)) {
        deleteRememberedDevice(context, address);
      } else {
        deviceInfos.add(new Pair<>(name, address));
      }
    }
    return deviceInfos;
  }

  public static void setAutoConnect(Context context, boolean autoConnect) {
    getSharedPrefs(context).edit().putBoolean(PREF_AUTO_CONNECT, autoConnect).apply();
  }

  public static boolean isAutoConnect(Context context) {
    return getSharedPrefs(context).getBoolean(PREF_AUTO_CONNECT, true);
  }

  /** Sets the connection setting enable or disabled. */
  public static void setConnectionEnabled(Context context, boolean enabled) {
    getSharedPrefs(context).edit().putBoolean(PREF_CONNECTION_ENABLED, enabled).apply();
  }

  /** The default setting is off. */
  public static boolean isConnectionEnabled(Context context) {
    return getSharedPrefs(context).getBoolean(PREF_CONNECTION_ENABLED, false);
  }

  private static SharedPreferences getSharedPrefs(Context context) {
    return SharedPreferencesUtils.getSharedPreferences(context, FILENAME);
  }

  private static String concatenateWithDelimiter(String delimiter, String first, String second) {
    return first + delimiter + second;
  }

  private PersistentStorage() {}
}
