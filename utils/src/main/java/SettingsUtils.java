/*
 * Copyright (C) 2012 Google Inc.
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

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.provider.Settings;

/** Utility class to access {@link android.provider.Settings}. */
public class SettingsUtils {

  /** Value of hidden constant {@code android.provider.Settings.Secure.USER_SETUP_COMPLETE} */
  public static final String USER_SETUP_COMPLETE = "user_setup_complete";

  public static boolean allowLinksOutOfSettings(Context context) {
    // Do not allow access to web during setup.  affects android M-O.
    return 1 == Settings.Secure.getInt(context.getContentResolver(), USER_SETUP_COMPLETE, 0);
  }

  /**
   * Returns whether a specific accessibility service is enabled.
   *
   * @param context The parent context.
   * @param packageName The package name of the accessibility service.
   * @return {@code true} of the service is enabled.
   */
  public static boolean isAccessibilityServiceEnabled(Context context, String packageName) {
    final ContentResolver resolver = context.getContentResolver();
    final String enabledServices =
        Settings.Secure.getString(resolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);

    return enabledServices.contains(packageName);
  }

  /**
   * Returns flag whether animation setting is definitely disabled. If no setting found, returns
   * false.
   */
  public static boolean isAnimationDisabled(Context context) {
    return FeatureSupport.disableAnimation()
        && (0 == getGlobalInt(context, Settings.Global.WINDOW_ANIMATION_SCALE))
        && (0 == getGlobalInt(context, Settings.Global.TRANSITION_ANIMATION_SCALE))
        && (0 == getGlobalInt(context, Settings.Global.ANIMATOR_DURATION_SCALE));
  }

  /**
   * Requests system settings writing permission if the parent context needs.
   *
   * @param context The parent context
   * @return {@code true} has the permission; {@code false} need to request the permission
   */
  public static boolean requestWriteSettingsPermission(Context context) {
    boolean hasWritePermission = Settings.System.canWrite(context);
    if (hasWritePermission) {
      return true;
    }
    // Starting in M, we need the user to manually allow the app to modify system settings.
    Intent intent = new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS);
    intent.setData(Uri.parse("package:" + context.getPackageName()));
    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    context.startActivity(intent);
    return false;
  }

  /** Returns value of constants in Settings.Global. */
  private static int getGlobalInt(Context context, String constantName) {
    int value = Settings.Global.getInt(context.getContentResolver(), constantName, -1);
    return value;
  }
}
