/*
 * Copyright (C) 2015 The Android Open Source Project
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

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.provider.Settings.Secure;
import android.speech.tts.TextToSpeech;
import android.text.TextUtils;
import com.google.android.accessibility.utils.compat.provider.SettingsCompatUtils;
import java.util.List;
import org.checkerframework.checker.nullness.qual.Nullable;

class TextToSpeechUtils {

  /**
   * Reloads the list of installed TTS engines.
   *
   * @param pm The package manager.
   * @param results The list to populate with installed TTS engines.
   * @return The package for the system default TTS.
   */
  public static @Nullable String reloadInstalledTtsEngines(
      PackageManager pm, List<String> results) {
    final Intent intent = new Intent(TextToSpeech.Engine.INTENT_ACTION_TTS_SERVICE);
    final List<ResolveInfo> resolveInfos =
        pm.queryIntentServices(intent, PackageManager.GET_SERVICES);

    String systemTtsEngine = null;

    for (ResolveInfo resolveInfo : resolveInfos) {
      final ServiceInfo serviceInfo = resolveInfo.serviceInfo;
      final ApplicationInfo appInfo = serviceInfo.applicationInfo;
      final String packageName = serviceInfo.packageName;
      final boolean isSystemApp = ((appInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0);

      results.add(serviceInfo.packageName);

      if (isSystemApp) {
        systemTtsEngine = packageName;
      }
    }

    return systemTtsEngine;
  }

  /**
   * Attempts to shutdown the specified TTS engine, ignoring any errors.
   *
   * @param tts The TTS engine to shutdown.
   */
  static void attemptTtsShutdown(TextToSpeech tts) {
    try {
      tts.shutdown();
    } catch (Exception e) {
      // Don't care, we're shutting down.
    }
  }

  /**
   * Returns the localized name of the TTS engine with the specified package name.
   *
   * @param context The parent context.
   * @param enginePackage The package name of the TTS engine.
   * @return The localized name of the TTS engine.
   */
  static @Nullable CharSequence getLabelForEngine(Context context, String enginePackage) {
    if (enginePackage == null) {
      return null;
    }

    final PackageManager pm = context.getPackageManager();
    final Intent intent = new Intent(TextToSpeech.Engine.INTENT_ACTION_TTS_SERVICE);
    intent.setPackage(enginePackage);

    final List<ResolveInfo> resolveInfos =
        pm.queryIntentServices(intent, PackageManager.MATCH_DEFAULT_ONLY);

    if ((resolveInfos == null) || resolveInfos.isEmpty()) {
      return null;
    }

    final ResolveInfo resolveInfo = resolveInfos.get(0);
    final ServiceInfo serviceInfo = resolveInfo.serviceInfo;

    if (serviceInfo == null) {
      return null;
    }

    return serviceInfo.loadLabel(pm);
  }

  static @Nullable String getDefaultLocaleForEngine(ContentResolver cr, String engineName) {
    final String defaultLocales =
        Secure.getString(cr, SettingsCompatUtils.SecureCompatUtils.TTS_DEFAULT_LOCALE);
    return parseEnginePrefFromList(defaultLocales, engineName);
  }

  /**
   * Parses a comma separated list of engine locale preferences. The list is of the form {@code
   * "engine_name_1:locale_1,engine_name_2:locale2"} and so on and so forth. Returns null if the
   * list is empty, malformed or if there is no engine specific preference in the list.
   */
  private static @Nullable String parseEnginePrefFromList(String prefValue, String engineName) {
    if (TextUtils.isEmpty(prefValue)) {
      return null;
    }

    String[] prefValues = prefValue.split(",");

    for (String value : prefValues) {
      final int delimiter = value.indexOf(':');
      if (delimiter > 0) {
        if (engineName.equals(value.substring(0, delimiter))) {
          return value.substring(delimiter + 1);
        }
      }
    }

    return null;
  }
}
