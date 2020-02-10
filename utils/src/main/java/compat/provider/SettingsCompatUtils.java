/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.google.android.accessibility.utils.compat.provider;

import android.content.Context;
import android.provider.Settings;

/** TODO: Figure out why this is separate from SecureSettingsUtils, or merge them. */
public class SettingsCompatUtils {
  private SettingsCompatUtils() {
    // This class is non-instantiable.
  }

  /** TODO: Figure out why this inner class is needed, or merge it with outer class. */
  public static class SecureCompatUtils {
    private SecureCompatUtils() {
      // This class is non-instantiable.
    }

    /** Whether to speak passwords while in accessibility mode. */
    public static final String ACCESSIBILITY_SPEAK_PASSWORD = "speak_password";

    /**
     * Stores the default TTS locales on a per engine basis. Stored as a comma separated list of
     * values, each value being of the form {@code engine_name:locale} for example, {@code
     * com.foo.ttsengine:eng-USA,com.bar.ttsengine:esp-ESP}. Apps should never need to read this
     * setting directly, and can query the TextToSpeech framework classes for the locale that is in
     * use.
     */
    public static final String TTS_DEFAULT_LOCALE = "tts_default_locale";

    /**
     * Returns whether to speak passwords while in accessibility mode.
     *
     * @param context The parent context.
     * @return {@code true} if passwords should always be spoken aloud.
     */
    public static boolean shouldSpeakPasswords(Context context) {
      return (Settings.Secure.getInt(context.getContentResolver(), ACCESSIBILITY_SPEAK_PASSWORD, 0)
          == 1);
    }
  }
}
