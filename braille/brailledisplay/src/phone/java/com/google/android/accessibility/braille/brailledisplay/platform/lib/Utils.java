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

package com.google.android.accessibility.braille.brailledisplay.platform.lib;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.view.accessibility.AccessibilityManager;
import androidx.annotation.Nullable;
import java.util.List;

/** Some utilities for Braille Display. */
public class Utils {

  private static final String TAG = "BrailleDisplay";

  /** Throws IllegalStateException if we are not running on the main thread. */
  public static void assertMainThread() {
    if (Looper.getMainLooper() != Looper.myLooper()) {
      throw new IllegalStateException("Must be on main thread");
    }
  }

  /** Returns true if the current thread is the application's main thread. */
  public static boolean isMainThread() {
    return Looper.getMainLooper() == Looper.myLooper();
  }

  /** Throws IllegalStateException if we are running on the main thread. */
  public static void assertNotMainThread() {
    if (Looper.getMainLooper() == Looper.myLooper()) {
      throw new IllegalStateException("Must be off main thread");
    }
  }

  /**
   * Convert byte[] to space-separated String of hex-encoded bytes. Useful for debugging.
   *
   * @param bytes the bytes, must be non-null
   * @param count the number of bytes to convert
   * @return bytes as String
   */
  public static String bytesToHexString(byte[] bytes, int count) {
    StringBuilder sb = new StringBuilder();
    if (count > bytes.length) {
      throw new IllegalArgumentException(
          String.format("byte array has length %d; cannot process %d bytes", bytes.length, count));
    }
    for (int i = 0; i < count; i++) {
      sb.append(String.format("%02X", bytes[i]));
      if (i < count - 1) {
        sb.append(" ");
      }
    }
    return sb.toString();
  }

  public static String bytesToHexString(byte[] bytes) {
    return bytesToHexString(bytes, bytes.length);
  }

  /* s must be an even-length string. */
  public static byte[] hexStringToByteArray(String s) {
    s = s.replace(" ", "");
    int len = s.length();
    byte[] data = new byte[len / 2];
    for (int i = 0; i < len; i += 2) {
      data[i / 2] =
          (byte) ((Character.digit(s.charAt(i), 16) << 4) + Character.digit(s.charAt(i + 1), 16));
    }
    return data;
  }

  /**
   * Log the message using the PairedSetup tag. The message will be prepended with the subTag.
   *
   * @param subTag the sub tag with which to prepend the message
   * @param message the message to log
   */
  public static void logE(String subTag, String message) {
    Log.e(TAG, subTag + ": " + message);
  }

  public static byte[] convertBytesCollectionToByteArray(List<Byte> list) {
    byte[] array = new byte[list.size()];
    for (int i = 0; i < list.size(); i++) {
      array[i] = list.get(i);
    }
    return array;
  }

  public static void addBytesToCollection(List<Byte> collection, byte[] bytes) {
    for (int i = 0; i < bytes.length; i++) {
      collection.add(bytes[i]);
    }
  }

  public static byte[] extractBytes(List<Byte> list, int count) {
    if (list.size() < count) {
      throw new IllegalArgumentException(
          "Cannot extract " + count + "bytes from list of size is " + list.size());
    }
    byte[] result = new byte[count];
    for (int i = 0; i < count; i++) {
      result[i] = list.remove(0);
    }
    return result;
  }

  /** Returns if accessibility service is enabled. */
  // TODO: use existing Utils class for the his method
  public static boolean isAccessibilityServiceEnabled(Context context, String packageName) {
    @Nullable
    AccessibilityManager manager =
        (AccessibilityManager) context.getSystemService(Context.ACCESSIBILITY_SERVICE);
    if (manager == null) {
      return false;
    }
    List<AccessibilityServiceInfo> list =
        manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK);
    if (list != null) {
      for (AccessibilityServiceInfo serviceInfo : list) {
        if (serviceInfo.getId().contains(packageName)) {
          return true;
        }
      }
    }
    return false;
  }

  // Requires SDK_INT >= KITKAT
  // Returns false if an exception occurred in reading from Secure Settings.
  public static boolean isGlobalLocationSettingEnabled(Context context) {
    try {
      int locationMode =
          Settings.Secure.getInt(context.getContentResolver(), Settings.Secure.LOCATION_MODE);
      return locationMode != Settings.Secure.LOCATION_MODE_OFF;
    } catch (Settings.SettingNotFoundException e) {
      return false;
    }
  }

  public static boolean launchAppDetailsActivity(Context context, String packageName) {
    Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
    intent.addFlags(
        Intent.FLAG_ACTIVITY_NEW_TASK
            | Intent.FLAG_ACTIVITY_CLEAR_TASK
            | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
    intent.setData(Uri.fromParts("package", packageName, null));
    try {
      context.startActivity(intent);
      return true;
    } catch (Exception e) {
      return false;
    }
  }

  public static boolean launchAccessibilitySettings(
      Context context, @Nullable ComponentName component) {
    Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
    intent.addFlags(
        Intent.FLAG_ACTIVITY_NEW_TASK
            | Intent.FLAG_ACTIVITY_CLEAR_TASK
            | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
    // Highlight TalkBack item in Accessibility Settings upon arriving there (Pixel only).
    if (component != null) {
      Utils.attachSettingsHighlightBundle(intent, component);
    }
    try {
      context.startActivity(intent);
      return true;
    } catch (Exception e) {
      return false;
    }
  }

  public static void attachSettingsHighlightBundle(Intent intent, ComponentName componentName) {
    Bundle bundle = new Bundle();
    bundle.putString(":settings:fragment_args_key", componentName.flattenToString());
    intent.putExtra(":settings:show_fragment_args", bundle);
  }

  public static boolean launchLocationSettingsActivity(Context context) {
    Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
    try {
      context.startActivity(intent);
      return true;
    } catch (Exception e) {
      return false;
    }
  }
}
