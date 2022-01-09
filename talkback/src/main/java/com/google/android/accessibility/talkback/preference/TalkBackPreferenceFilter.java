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
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.text.TextUtils;
import androidx.annotation.IntDef;
import androidx.preference.Preference;
import androidx.preference.PreferenceGroup;
import androidx.preference.PreferenceScreen;
import com.google.android.accessibility.talkback.R;
import com.google.android.accessibility.utils.AccessibilityServiceCompatUtils.Constants;
import com.google.android.accessibility.utils.BuildConfig;
import com.google.android.accessibility.utils.FeatureSupport;
import com.google.android.accessibility.utils.SettingsUtils;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.List;
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
    SHOW_IF_FINGER_PRINT,
    SHOW_SYSTEM_ACTION,
    SHOW_IF_MULTI_FINGER_TAP_AND_HOLD,
    SHOW_FOCUS_INDICATOR,
    HIDDEN_ON_RELEASE_BUILD,
  })
  @Retention(RetentionPolicy.SOURCE)
  private @interface FilterFlag {}

  /** Flag to hide preference in all cases. */
  private static final int HIDDEN = Integer.MAX_VALUE;
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
  /** Flag to hide finger print gesture when finger print is not supported. */
  private static final int SHOW_IF_FINGER_PRINT = 0x1000;
  /** Flag to show system action. */
  private static final int SHOW_SYSTEM_ACTION = 0x2000;
  /** Flag to show multi-finger gesture extended. */
  private static final int SHOW_IF_MULTI_FINGER_TAP_AND_HOLD = 0x4000;
  /** Flag to show focus indicator. */
  private static final int SHOW_FOCUS_INDICATOR = 0x8000;
  /** Flag to hide if the runtime is a release build. */
  private static final int HIDDEN_ON_RELEASE_BUILD = 0x10000;

  /** List TalkBack preferences. */
  enum TalkBackPreference {
    // Sound and vibration.
    SOUND_FEEDBACK(R.string.pref_soundback_key, HIDDEN_ON_TV),
    SOUND_FEEDBACK_VOLUME(R.string.pref_soundback_volume_key, HIDDEN_ON_TV),
    AUDIO_DUCKING(
        R.string.pref_use_audio_focus_key, HIDDEN_ON_TV | HIDDEN_ON_ARC | HIDDEN_ON_WATCH),
    VIBRATION_FEEDBACK(R.string.pref_vibration_key, HIDDEN_ON_ARC | HIDDEN_NO_VIBRATION),
    // Advanced settings.
    CUSTOM_LABELS(R.string.pref_manage_labels_key, HIDDEN_ON_TV | HIDDEN_ON_WATCH | HIDDEN_SETUP),
    SINGLE_TAP_ACTIVATION(
        R.string.pref_single_tap_key, HIDDEN_ON_TV | HIDDEN_ON_WATCH | HIDDEN_ON_ARC),
    KEYBOARD_SHORTCUTS(
        R.string.pref_category_manage_keyboard_shortcut_key, HIDDEN_ON_TV | HIDDEN_ON_WATCH),
    SUSPEND_AND_RESUME(
        R.string.pref_two_volume_long_press_key,
        HIDDEN_ON_TV | HIDDEN_ON_ARC | HIDDEN_ON_WATCH | HIDDEN_HAS_ACCESSIBILITY_SHORTCUT),
    HIDE_SCREEN(
        R.string.pref_dim_volume_three_clicks_key,
        HIDDEN_ON_TV | HIDDEN_ON_ARC | SHOW_IF_VOLUME_KEY_SHORTCUT),
    RESUME_FROM_SUSPEND(
        R.string.pref_resume_talkback_key,
        HIDDEN_ON_TV | HIDDEN_ON_ARC | HIDDEN_ON_WATCH | HIDDEN_HAS_ACCESSIBILITY_SHORTCUT),
    PRIVACY_POLICY(R.string.pref_policy_key, HIDDEN_SETUP),
    TERMS_OF_SERVICE(R.string.pref_show_tos_key, HIDDEN_SETUP),
    // Help & Tutorial.
    HELP_AND_FEEDBACK(R.string.pref_help_and_feedback_key, HIDDEN_SETUP),
    PRACTICE_GESTURES(
        R.string.pref_practice_gestures_entry_point_key, HIDDEN_ON_TV | HIDDEN_ON_WATCH),
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
    BRAILLE_DISPLAY(R.string.pref_brailledisplay_key, HIDDEN | HIDDEN_ON_TV | HIDDEN_ON_WATCH),
    CUSTOMIZE_MENU(R.string.pref_manage_customize_menus_key, HIDDEN_ON_TV),
    // TalkBack/Reading Menu
    CUSTOMIZE_TALKBACK_MENU_EDIT_OPTIONS(
        R.string.pref_show_context_menu_editing_setting_key, HIDDEN_ON_WATCH),
    CUSTOMIZE_TALKBACK_MENU_EDIT_NAVIGATION_CONTROLS(
        R.string.pref_show_navigation_menu_controls_setting_key, HIDDEN_ON_WATCH),
    CUSTOMIZE_TALKBACK_MENU_EDIT_NAVIGATION_LINKS(
        R.string.pref_show_navigation_menu_links_setting_key, HIDDEN_ON_WATCH),
    CUSTOMIZE_TALKBACK_MENU_EDIT_NAVIGATION_LANDMARKS(
        R.string.pref_show_navigation_menu_landmarks_setting_key, HIDDEN_ON_WATCH),
    CUSTOMIZE_TALKBACK_MENU_EDIT_NAVIGATION_SPECIAL_CONTENTS(
        R.string.pref_show_navigation_menu_special_content_setting_key, HIDDEN_ON_WATCH),
    CUSTOMIZE_TALKBACK_MENU_EDIT_NAVIGATION_OTHER_WEBS(
        R.string.pref_show_navigation_menu_other_web_navigation_setting_key, HIDDEN_ON_WATCH),
    CUSTOMIZE_TALKBACK_MENU_EDIT_NAVIGATION_WINDOWS(
        R.string.pref_show_navigation_menu_window_setting_key, HIDDEN_ON_WATCH),
    CUSTOMIZE_TALKBACK_MENU_SCREEN_SEARCH(
        R.string.pref_show_context_menu_find_on_screen_setting_key, HIDDEN_ON_WATCH),
    SUPPORT_SYSTEM_ACTION(
        R.string.pref_show_context_menu_system_action_setting_key, SHOW_SYSTEM_ACTION),
    CUSTOMIZE_TALKBACK_MENU_VIBRATION_FEEDBACK(
        R.string.pref_show_context_menu_vibration_feedback_setting_key, HIDDEN_NO_VIBRATION),
    CUSTOMIZE_TALKBACK_MENU_PAUSE_TALKBACK(
        R.string.pref_show_context_menu_pause_feedback_setting_key,
        HIDDEN_ON_WATCH | HIDDEN_HAS_ACCESSIBILITY_SHORTCUT),

    CUSTOMIZE_READING_MENU_NAVIGATION_LINKS(
        R.string.pref_selector_granularity_links_key, HIDDEN_ON_WATCH),
    CUSTOMIZE_READING_MENU_NAVIGATION_LANDMARKS(
        R.string.pref_selector_granularity_landmarks_key, HIDDEN_ON_WATCH),
    CUSTOMIZE_READING_MENU_NAVIGATION_WINDOWS(
        R.string.pref_selector_granularity_windows_key, HIDDEN_ON_WATCH),
    // Developer settings/
    EXPLORE_BY_TOUCH(R.string.pref_explore_by_touch_reflect_key, HIDDEN_ON_ARC),
    // Gesture/Verbosity Settings.
    CONFIG_ON_SCREEN_KEYBOARD_ECHO(R.string.pref_keyboard_echo_on_screen_key, HIDDEN_ON_WATCH),
    CONFIG_PHYSICAL_KEYBOARD_ECHO(R.string.pref_keyboard_echo_physical_key, HIDDEN_ON_WATCH),
    SPEAK_LIST_AND_GRID(R.string.pref_speak_container_element_positions_key, HIDDEN_ON_WATCH),
    SPEAK_NUMBER_OF_LIST_(R.string.pref_verbose_scroll_announcement_key, HIDDEN_ON_WATCH),
    SPEAK_ELEMENT_TYPE(R.string.pref_speak_roles_key, HIDDEN_ON_WATCH),
    SPEAK_PHONETIC_LETTERS(R.string.pref_phonetic_letters_key, HIDDEN_ON_WATCH),
    USE_PITCH_CHANGE(R.string.pref_intonation_key, HIDDEN_ON_WATCH),
    SPEAK_WHEN_SCREEN_OFF(R.string.pref_screenoff_key, HIDDEN_ON_WATCH),
    VERBOSITY_MISC(R.string.pref_verbosity_category_misc_settings_key, HIDDEN_ON_WATCH),
    CUSTOMIZE_GESTURE(R.string.pref_category_manage_gestures_key, HIDDEN_ON_TV),
    CUSTOMIZE_GESTURE_GROUP_2FINGER(
        R.string.pref_category_2finger_shortcuts_key, SHOW_IF_MULTI_FINGER),
    CUSTOMIZE_GESTURE_GROUP_3FINGER(
        R.string.pref_category_3finger_shortcuts_key, HIDDEN_ON_WATCH | SHOW_IF_MULTI_FINGER),
    CUSTOMIZE_GESTURE_GROUP_4FINGER(
        R.string.pref_category_4finger_shortcuts_key, HIDDEN_ON_WATCH | SHOW_IF_MULTI_FINGER),
    CUSTOMIZE_GESTURE_FINGERPRINT(
        R.string.pref_category_fingerprint_touch_shortcuts_key, SHOW_IF_FINGER_PRINT),
    CUSTOMIZE_GESTURE_2FINGER_3TAP_HOLD(
        R.string.pref_shortcut_2finger_3tap_hold_key, SHOW_IF_MULTI_FINGER_TAP_AND_HOLD),
    CUSTOMIZE_FOCUS_INDICATOR(
        R.string.pref_category_manage_focus_indicator_key, SHOW_FOCUS_INDICATOR),
    ;

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
          // Skip PreferenceScreen since PreferenceScreen is subclass of PreferenceGroup and may be
          // used as the element of xml file.
          if ((preference instanceof PreferenceScreen)
              && (((PreferenceGroup) preference).getPreferenceCount() == 0)) {
            continue;
          }
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
      Intent serviceIntent = new Intent().setComponent(Constants.BRAILLE_KEYBOARD);
      List<ResolveInfo> resolveInfo = packageManager.queryIntentServices(serviceIntent, 0);
      if (resolveInfo == null
          || resolveInfo.isEmpty()
          || packageManager.getComponentEnabledSetting(Constants.BRAILLE_KEYBOARD)
              != PackageManager.COMPONENT_ENABLED_STATE_ENABLED) {
        return true;
      }
    }

    if (hasFlag(pref.get(), SHOW_IF_MULTI_FINGER)
        && !FeatureSupport.isMultiFingerGestureSupported()) {
      return true;
    }

    if (hasFlag(pref.get(), SHOW_IF_MULTI_FINGER_TAP_AND_HOLD)
        && !FeatureSupport.multiFingerTapAndHold()) {
      return true;
    }

    if (hasFlag(pref.get(), SHOW_IF_FINGER_PRINT)
        && !FeatureSupport.isFingerprintSupported(context)) {
      return true;
    }

    if (hasFlag(pref.get(), SHOW_SYSTEM_ACTION) && !FeatureSupport.supportSystemActions(context)) {
      return true;
    }

    if (hasFlag(pref.get(), SHOW_FOCUS_INDICATOR)
        && !FeatureSupport.supportCustomizingFocusIndicator()) {
      return true;
    }

    if (hasFlag(pref.get(), HIDDEN)) {
      return true;
    }

    if (!BuildConfig.DEBUG && hasFlag(pref.get(), HIDDEN_ON_RELEASE_BUILD)) {
      return true;
    }

    return false;
  }

  private boolean hasFlag(TalkBackPreference preference, @FilterFlag int flag) {
    return (preference.hideFlags & flag) == flag;
  }
}
