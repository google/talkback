/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.google.android.accessibility.talkback.preference;


import android.content.Context;
import android.content.pm.PackageManager;
import androidx.annotation.IntDef;
import android.text.TextUtils;
import androidx.preference.Preference;
import androidx.preference.PreferenceGroup;
import com.google.android.accessibility.brailleime.Constants;
import com.google.android.accessibility.talkback.R;
import com.google.android.accessibility.utils.FeatureSupport;
import com.google.android.accessibility.utils.SettingsUtils;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Filters TalkBack preferences by feature flag. It will hide the preference if device doesn't
 * support the related feature.
 */
public class TalkBackPreferenceFilter {
  /** Flags to filter TalkBack preference setting. */
  @IntDef({
    HIDDEN_ON_TV,
    HIDDEN_ON_ARC,
    HIDDEN_ON_WATCH,
    HIDDEN_NO_VIBRATION,
    HIDDEN_SETUP,
    HIDDEN_HAS_ACCESSIBILITY_SHORTCUT,
    SHOW_IF_VOLUME_KEY_SHORTCUT,
    HIDE_NO_PROXIMITY_SENSOR,
    HIDE_HAS_VOLUME_KEY,
    HIDE_NO_BRAILLE_KEYBOARD,
    SHOW_IF_MULTI_FINGER,
    SHOW_IF_FINGER_PRINT
  })
  @Retention(RetentionPolicy.SOURCE)
  private @interface FilterFlag {}

  /** Flag to hide preference on TV. */
  private static final int HIDDEN_ON_TV = 0x02;
  /** Flag to hide preference on ARC device. */
  private static final int HIDDEN_ON_ARC = 0x04;
  /** Flag to hide preference on watch. */
  private static final int HIDDEN_ON_WATCH = 0x08;
  /** Flag to hide preference if the device doesn't support vibration. */
  private static final int HIDDEN_NO_VIBRATION = 0x10;
  /** Flag to hide preference during device setup. */
  private static final int HIDDEN_SETUP = 0x20;
  /** Flag to hide preference if the device support accessibility shortcut. */
  private static final int HIDDEN_HAS_ACCESSIBILITY_SHORTCUT = 0x40;
  /** Flag to show preference if the device has volume key shortcut . */
  private static final int SHOW_IF_VOLUME_KEY_SHORTCUT = 0x80;
  /** Flag to hide preference if no proximity sensor. */
  private static final int HIDE_NO_PROXIMITY_SENSOR = 0x100;
  /** Flag to hide volume settings if system supports volume keys. */
  private static final int HIDE_HAS_VOLUME_KEY = 0x200;
  /** Flag to hide Braille keyboard if system doesn't support it. */
  private static final int HIDE_NO_BRAILLE_KEYBOARD = 0x400;
  /** Flag to hide if system doesn't support multi-finger gesture. */
  private static final int SHOW_IF_MULTI_FINGER = 0x800;
  /** Flag to hide finger print gesture when finger print is not supported . */
  private static final int SHOW_IF_FINGER_PRINT = 0x1000;

  /** List TalkBack preferences. */
  enum TalkBackPreference {
    // Sound and vibration.
    SOUND_FEEDBACK(R.string.pref_soundback_key, HIDDEN_ON_TV),
    SOUND_FEEDBACK_VOLUME(R.string.pref_soundback_volume_key, HIDDEN_ON_TV),
    AUDIO_DUCKING(R.string.pref_use_audio_focus_key, HIDDEN_ON_TV | HIDDEN_ON_ARC),
    VIBRATION_FEEDBACK(R.string.pref_vibration_key, HIDDEN_ON_ARC | HIDDEN_NO_VIBRATION),
    // Advanced settings.
    CUSTOM_LABELS(R.string.pref_manage_labels_key, HIDDEN_ON_TV | HIDDEN_ON_WATCH | HIDDEN_SETUP),
    SINGLE_TAP_ACTIVATION(
        R.string.pref_single_tap_key, HIDDEN_ON_TV | HIDDEN_ON_WATCH | HIDDEN_ON_ARC),
    KEYBOARD_SHORTCUTS(
        R.string.pref_category_manage_keyboard_shortcut_key, HIDDEN_ON_TV | HIDDEN_ON_WATCH),
    SUSPEND_AND_RESUME(
        R.string.pref_two_volume_long_press_key,
        HIDDEN_ON_TV | HIDDEN_ON_ARC | HIDDEN_HAS_ACCESSIBILITY_SHORTCUT),
    HIDE_SCREEN(
        R.string.pref_dim_volume_three_clicks_key,
        HIDDEN_ON_TV | HIDDEN_ON_ARC | SHOW_IF_VOLUME_KEY_SHORTCUT),
    RESUME_FROM_SUSPEND(
        R.string.pref_resume_talkback_key,
        HIDDEN_ON_TV | HIDDEN_ON_ARC | HIDDEN_HAS_ACCESSIBILITY_SHORTCUT),
    PRIVACY_POLICY(R.string.pref_policy_key, HIDDEN_SETUP),
    TERMS_OF_SERVICE(R.string.pref_show_tos_key, HIDDEN_SETUP),
    // Help & Tutorial.
    HELP_AND_FEEDBACK(R.string.pref_help_and_feedback_key, HIDDEN_SETUP),
    PRACTICE_GESTURES(R.string.pref_practice_gestures_entry_point_key, HIDDEN_ON_TV),
    TUTORIAL(R.string.pref_tutorial_entry_point_key, HIDDEN_ON_TV),
    // Basic settings.
    NEW_FEATURE(
        R.string.pref_new_feature_talkback91_entry_point_key, HIDDEN_ON_TV | HIDDEN_ON_WATCH),
    PROXIMITY(R.string.pref_proximity_key, HIDDEN_ON_ARC | HIDDEN_ON_TV | HIDE_NO_PROXIMITY_SENSOR),
    TTS_SETTINGS(R.string.pref_tts_settings_key, HIDDEN_ON_WATCH),
    SPEECH_VOLUME(R.string.pref_speech_volume_key, HIDE_HAS_VOLUME_KEY),
    SOUND_AND_VIBRATION(R.string.pref_sound_and_vibration_key, HIDDEN_ON_TV),
    BRAILLE_KEYBOARD(
        R.string.pref_brailleime_key, HIDDEN_ON_TV | HIDDEN_ON_WATCH | HIDE_NO_BRAILLE_KEYBOARD),
    CUSTOMIZE_MENU(R.string.pref_manage_customize_menus_key, HIDDEN_ON_TV),
    // Developer settings/
    EXPLORE_BY_TOUCH(R.string.pref_explore_by_touch_reflect_key, HIDDEN_ON_ARC),
    // Gesture/Verbosity Settings.
    CUSTOMIZE_GESTURE(R.string.pref_category_manage_gestures_key, HIDDEN_ON_TV),
    CUSTOMIZE_GESTURE_GROUP_2FINGER(
        R.string.pref_category_2finger_shortcuts_key, SHOW_IF_MULTI_FINGER),
    CUSTOMIZE_GESTURE_GROUP_3FINGER(
        R.string.pref_category_3finger_shortcuts_key, HIDDEN_ON_WATCH | SHOW_IF_MULTI_FINGER),
    CUSTOMIZE_GESTURE_GROUP_4FINGER(
        R.string.pref_category_4finger_shortcuts_key, HIDDEN_ON_WATCH | SHOW_IF_MULTI_FINGER),
    CUSTOMIZE_GESTURE_FINGERPRINT(
        R.string.pref_category_fingerprint_touch_shortcuts_key, SHOW_IF_FINGER_PRINT);

    TalkBackPreference(int resId, int hideFlags) {
      this.resId = resId;
      this.hideFlags = hideFlags;
    }

    int resId;
    int hideFlags;
  }

  private final Context context;

  public TalkBackPreferenceFilter(Context context) {
    this.context = context;
  }

  /**
   * Filters preferences from given {@link PreferenceGroup}. For each preference, we'll hide it if
   * the device met flags defines in {@link TalkBackPreference#hideFlags}.
   */
  public void filterPreferences(PreferenceGroup preferenceGroup) {

    for (int i = 0; i < preferenceGroup.getPreferenceCount(); ++i) {
      Preference preference = preferenceGroup.getPreference(i);
      if (hide(preference)) {
        preferenceGroup.removePreference(preference);
        --i; // Preference i removed.
      } else {
        if (preference instanceof PreferenceGroup) {
          filterPreferences((PreferenceGroup) preference);
          if (((PreferenceGroup) preference).getPreferenceCount() == 0) {
            // When all of the preferences under the group removed, the group should be removed too.
            preferenceGroup.removePreference(preference);
            --i;
          }
        }
      }
    }
  }

  private boolean hide(Preference preference) {
    Optional<TalkBackPreference> pref =
        Stream.of(TalkBackPreference.values())
            .filter(p -> TextUtils.equals(preference.getKey(), context.getString(p.resId)))
            .findFirst();

    if (!pref.isPresent()) {
      // Doesn't hide the preference if it's not in TalkBackPreference. That means no situation
      // needs to hide the preference.
      return false;
    }

    if (hasFlag(pref.get(), HIDDEN_ON_TV) && FeatureSupport.isTv(context)) {
      return true;
    }

    if (hasFlag(pref.get(), HIDDEN_ON_ARC) && FeatureSupport.isArc()) {
      return true;
    }

    if (hasFlag(pref.get(), HIDDEN_ON_WATCH) && FeatureSupport.isWatch(context)) {
      return true;
    }

    if (hasFlag(pref.get(), HIDDEN_NO_VIBRATION) && !FeatureSupport.isVibratorSupported(context)) {
      return true;
    }

    if (hasFlag(pref.get(), HIDDEN_SETUP) && !SettingsUtils.allowLinksOutOfSettings(context)) {
      return true;
    }

    if (hasFlag(pref.get(), HIDDEN_HAS_ACCESSIBILITY_SHORTCUT)
        && FeatureSupport.hasAccessibilityShortcut(context)) {
      return true;
    }

    if (hasFlag(pref.get(), SHOW_IF_VOLUME_KEY_SHORTCUT)
        && !FeatureSupport.supportsVolumeKeyShortcuts()) {
      return true;
    }

    if (hasFlag(pref.get(), HIDE_NO_PROXIMITY_SENSOR)
        && !FeatureSupport.supportProximitySensor(context)) {
      return true;
    }

    if (hasFlag(pref.get(), HIDE_HAS_VOLUME_KEY)
        && FeatureSupport.hasAccessibilityAudioStream(context)) {
      return true;
    }

    if (hasFlag(pref.get(), HIDE_NO_BRAILLE_KEYBOARD)) {
      PackageManager packageManager = context.getPackageManager();
      if (packageManager.getComponentEnabledSetting(Constants.BRAILLE_KEYBOARD)
          != PackageManager.COMPONENT_ENABLED_STATE_ENABLED) {
        return true;
      }
    }

    if (hasFlag(pref.get(), SHOW_IF_MULTI_FINGER)
        && !FeatureSupport.isMultiFingerGestureSupported()) {
      return true;
    }

    if (hasFlag(pref.get(), SHOW_IF_FINGER_PRINT)
        && !FeatureSupport.isFingerprintSupported(context)) {
      return true;
    }

    return false;
  }

  private boolean hasFlag(TalkBackPreference preference, @FilterFlag int flag) {
    return (preference.hideFlags & flag) == flag;
  }
}
