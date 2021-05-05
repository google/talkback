/*
 * Copyright (C) 2011 Google Inc.
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

package com.google.android.accessibility.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.preference.PreferenceManager;
import androidx.core.content.ContextCompat;

/** Utility methods for interacting with {@link SharedPreferences} objects. */
public class SharedPreferencesUtils {
  /**
   * Returns the value of an integer preference stored as a string. This is necessary when using a
   * {@link android.preference.ListPreference} to manage an integer preference, since the entries
   * must be String values.
   *
   * @param prefs Shared preferences from which to obtain the value
   * @param res Resources from which to obtain the key and default value
   * @param keyResId Resource identifier for the key
   * @param defaultResId Resource identifier for the default value
   * @return The preference value, or the default value if not set
   */
  public static int getIntFromStringPref(
      SharedPreferences prefs, Resources res, int keyResId, int defaultResId) {
    final String strPref = prefs.getString(res.getString(keyResId), res.getString(defaultResId));
    return Integer.parseInt(strPref);
  }

  /**
   * Returns the value of a floating point preference stored as a string. This is necessary when
   * using a {@link android.preference.ListPreference} to manage a floating point preference, since
   * the entries must be String values.
   *
   * @param prefs Shared preferences from which to obtain the value
   * @param res Resources from which to obtain the key and default value
   * @param keyResId Resource identifier for the key
   * @param defaultResId Resource identifier for the default value
   * @return The preference value, or the default value if not set
   */
  public static float getFloatFromStringPref(
      SharedPreferences prefs, Resources res, int keyResId, int defaultResId) {
    final String strPref = prefs.getString(res.getString(keyResId), res.getString(defaultResId));
    return Float.parseFloat(strPref);
  }

  /**
   * Returns the value of a string preference.
   *
   * @param prefs Shared preferences from which to obtain the value
   * @param res Resources from which to obtain the key and default value
   * @param keyResId Resource identifier for the key
   * @param defaultResId Resource identifier for the default value
   * @return The preference value, or the default value if not set
   */
  public static String getStringPref(
      SharedPreferences prefs, Resources res, int keyResId, int defaultResId) {
    return prefs.getString(
        res.getString(keyResId), ((defaultResId == 0) ? null : res.getString(defaultResId)));
  }

  /**
   * Stores the value of a String preference.
   *
   * @param prefs Shared preferences from which to obtain the value
   * @param res Resources from which to obtain the key and default value
   * @param keyResId Resource identifier for the key
   * @param value The value to store
   */
  public static void putStringPref(
      SharedPreferences prefs, Resources res, int keyResId, String value) {
    final SharedPreferences.Editor editor = prefs.edit();
    editor.putString(res.getString(keyResId), value);
    editor.apply();
  }

  /**
   * Returns the value of an integer preference.
   *
   * @param prefs Shared preferences from which to obtain the value
   * @param res Resources from which to obtain the key and default value
   * @param keyResId Resource identifier for the key
   * @param defaultResId Resource identifier for the default value
   * @return The preference value, or the default value if not set
   */
  public static int getIntPref(
      SharedPreferences prefs, Resources res, int keyResId, int defaultResId) {
    if (defaultResId == 0) {
      throw new IllegalArgumentException("defaultResId should not be 0");
    }
    return prefs.getInt(res.getString(keyResId), res.getInteger(defaultResId));
  }

  /**
   * Stores the value of an integer preference.
   *
   * @param prefs Shared preferences from which to obtain the value
   * @param res Resources from which to obtain the key and default value
   * @param keyResId Resource identifier for the key
   * @param value The value to store
   */
  public static void putIntPref(SharedPreferences prefs, Resources res, int keyResId, int value) {
    final SharedPreferences.Editor editor = prefs.edit();
    editor.putInt(res.getString(keyResId), value);
    editor.apply();
  }

  /**
   * Returns the value of a boolean preference.
   *
   * @param prefs Shared preferences from which to obtain the value
   * @param res Resources from which to obtain the key and default value
   * @param keyResId Resource identifier for the key
   * @param defaultResId Resource identifier for the default value
   * @return The preference value, or the default value if not set
   */
  public static boolean getBooleanPref(
      SharedPreferences prefs, Resources res, int keyResId, int defaultResId) {
    return prefs.getBoolean(res.getString(keyResId), res.getBoolean(defaultResId));
  }

  /**
   * Returns the value of a boolean preference.
   *
   * @param prefs Shared preferences from which to obtain the value
   * @param res Resources from which to obtain the key and default value
   * @param keyResId Resource identifier for the key
   * @param defaultValue The default value
   * @return The preference value, or the default value if not set
   */
  public static boolean getBooleanPref(
      SharedPreferences prefs, Resources res, int keyResId, boolean defaultValue) {
    return prefs.getBoolean(res.getString(keyResId), defaultValue);
  }

  /**
   * Stores the value of a boolean preference.
   *
   * @param prefs Shared preferences from which to obtain the value
   * @param res Resources from which to obtain the key and default value
   * @param keyResId Resource identifier for the key
   * @param value The value to store
   */
  public static void putBooleanPref(
      SharedPreferences prefs, Resources res, int keyResId, boolean value) {
    storeBooleanAsync(prefs, res.getString(keyResId), value);
  }

  /**
   * Stores the value of a boolean preference async.
   *
   * @param prefs Shared preferences from which to obtain the value
   * @param key The pref key
   * @param value The value to store
   */
  public static void storeBooleanAsync(SharedPreferences prefs, String key, boolean value) {
    SharedPreferences.Editor editor = prefs.edit();
    editor.putBoolean(key, value);
    editor.apply();
  }

  /**
   * Gets the appropriate SharedPreferences depending on the device capabilities. On systems that
   * support device-protected storage (Android N or later with compatible device), returns
   * SharedPreferences backed by device-protected storage. Otherwise, returns SharedPreferences
   * backed by a standard credential-protected storage context.
   */
  public static SharedPreferences getSharedPreferences(Context context) {
    Context deContext = ContextCompat.createDeviceProtectedStorageContext(context);
    if (deContext != null) {
      return PreferenceManager.getDefaultSharedPreferences(deContext);
    } else {
      return PreferenceManager.getDefaultSharedPreferences(context);
    }
  }

  /**
   * Gets the appropriate SharedPreferences depending on the device capabilities by {@code name}. On
   * systems that support device-protected storage (Android N or later with compatible device),
   * returns SharedPreferences backed by device-protected storage. Otherwise, returns
   * SharedPreferences backed by a standard credential-protected storage context.
   */
  public static SharedPreferences getSharedPreferences(Context context, String name) {
    Context deContext = ContextCompat.createDeviceProtectedStorageContext(context);
    if (deContext != null) {
      return deContext.getSharedPreferences(name, Context.MODE_PRIVATE);
    }
    return context.getSharedPreferences(name, Context.MODE_PRIVATE);
  }

  /**
   * Move existing preferences file from credential protected storage to device protected storage.
   * This is used to migrate data between storage locations after an Android upgrade from
   * Build.VERSION < N to Build.VERSION >= N.
   */
  public static void migrateSharedPreferences(Context context) {
    if (BuildVersionUtils.isAtLeastN()) {
      Context deContext = ContextCompat.createDeviceProtectedStorageContext(context);
      deContext.moveSharedPreferencesFrom(
          context, PreferenceManager.getDefaultSharedPreferencesName(context));
    }
  }

  /** Removes the preferences. */
  public static void remove(SharedPreferences pref, String... keys) {
    for (String key : keys) {
      if (pref.contains(key)) {
        pref.edit().remove(key).apply();
      }
    }
  }
}
