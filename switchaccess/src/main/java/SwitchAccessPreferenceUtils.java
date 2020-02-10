/*
 * Copyright (C) 2018 Google Inc.
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

package com.google.android.accessibility.switchaccess;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Paint;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import com.google.android.accessibility.compositor.Compositor;
import com.google.android.accessibility.compositor.Compositor.DescriptionOrder;
import com.google.android.accessibility.switchaccess.SwitchAccessPreferenceCache.SwitchAccessPreferenceChangedListener;
import com.google.android.accessibility.switchaccess.keyassignment.KeyAssignmentUtils;
import com.google.android.accessibility.utils.SharedPreferencesUtils;
import com.google.android.libraries.accessibility.utils.device.ScreenUtils;
import com.google.android.libraries.accessibility.utils.log.LogUtils;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import org.checkerframework.checker.initialization.qual.UnknownInitialization;
import org.checkerframework.checker.nullness.qual.Nullable;

/** Utils class for getting and setting Switch Access preferences. */
public class SwitchAccessPreferenceUtils {

  private static final String TAG = "SAPreferenceUtils";

  /** These are the IDs, in order, of the key assignment preferences for group selection */
  public static final int[] GROUP_SELECTION_SWITCH_CONFIG_IDS = {
    R.string.pref_key_mapped_to_click_key, R.string.pref_key_mapped_to_next_key,
    R.string.pref_key_mapped_to_switch_3_key, R.string.pref_key_mapped_to_switch_4_key,
    R.string.pref_key_mapped_to_switch_5_key
  };

  private static final int MILLISECONDS_PER_SECOND = 1000;

  // Conversion from centimeters to inches.
  private static final float CENTIMETERS_PER_INCH = 2.54f;

  // Used to convert from pixels to dp.
  private static final float PIXEL_TO_DP_MULTIPLIER = 160;

  /**
   * Registers a listener to the cache to be notified whenever a preference changes.
   *
   * @param context The context associated with the cache
   * @param listener The listener to notify whenever a preference changes
   */
  public static void registerSwitchAccessPreferenceChangedListener(
      Context context, @UnknownInitialization SwitchAccessPreferenceChangedListener listener) {
    SwitchAccessPreferenceCache cache = SwitchAccessPreferenceCache.getOrCreateInstance(context);
    if (cache != null) {
      cache.registerSwitchAccessCachedPreferenceChangeListener(listener);
    }
  }

  /**
   * Unregisters a listener from being notified whenever a preference changes.
   *
   * @param listener The listener to unregister
   */
  public static void unregisterSwitchAccessPreferenceChangedListener(
      SwitchAccessPreferenceChangedListener listener) {
    SwitchAccessPreferenceCache cache = SwitchAccessPreferenceCache.getInstanceIfExists();
    if (cache != null) {
      cache.unregisterSwitchAccessCachedPreferenceChangeListener(listener);
    }
  }

  /**
   * Check if group selection is enabled.
   *
   * @param context The current context
   * @return {@code true} if group selection is enabled in the preferences, {@code false} otherwise
   */
  public static boolean isGroupSelectionEnabled(Context context) {
    String groupSelectionKey = context.getString(R.string.group_selection_key);
    String scanPref = getCurrentScanningMethod(context);
    return TextUtils.equals(scanPref, groupSelectionKey);
  }

  /**
   * Check if row-column scanning is enabled.
   *
   * @param context The current context
   * @return {@code true} if row-column scanning is enabled in the preferences
   */
  public static boolean isRowColumnScanningEnabled(Context context) {
    String rowColumnScanningKey = context.getString(R.string.row_col_scanning_key);
    String scanPref = getCurrentScanningMethod(context);
    return TextUtils.equals(scanPref, rowColumnScanningKey);
  }

  /**
   * Check if linear scanning is enabled.
   *
   * @param context The current context
   * @return {@code true} if linear scanning is enabled in the preferences
   */
  public static boolean isLinearScanningEnabled(Context context) {
    String linearScanningKey = context.getString(R.string.linear_scanning_key);
    String scanPref = getCurrentScanningMethod(context);
    return TextUtils.equals(scanPref, linearScanningKey);
  }

  /**
   * Check if linear scanning without keyboard is enabled.
   *
   * @param context The current context
   * @return {@code true} if linear scanning without keyboard is enabled in the preferences
   */
  public static boolean isLinearScanningWithoutKeyboardEnabled(Context context) {
    String linearScanningWithoutKeyboardKey =
        context.getString(R.string.views_linear_ime_row_col_key);
    String scanPref = getCurrentScanningMethod(context);
    return TextUtils.equals(scanPref, linearScanningWithoutKeyboardKey);
  }

  /**
   * Check if auto-scanning is enabled.
   *
   * @param context The current context
   * @return {@code true} if auto scan is enabled in the preferences, {@code false} otherwise
   */
  public static boolean isAutoScanEnabled(Context context) {
    return getBooleanPreference(
        context,
        R.string.pref_key_auto_scan_enabled,
        Boolean.parseBoolean(context.getString(R.string.pref_auto_scan_default_value)));
  }

  /**
   * Check whether point scanning is currently enabled.
   *
   * @param context The current context
   * @return {@code true} if point scanning is currently enabled
   */
  public static boolean isPointScanEnabled(Context context) {
    // When animations are disabled, point scan won't animate correctly, so revert to another type
    // of scanning.
    // TODO: Investigate other ways of displaying point scan so that it isn't
    // represented as an animation.
    return ((VERSION.SDK_INT < VERSION_CODES.O) || ValueAnimator.areAnimatorsEnabled())
        && getBooleanPreference(
            context,
            R.string.pref_key_point_scan_enabled,
            R.bool.pref_point_scan_enabled_default_value);
  }

  /**
   * Sets whether auto scan is currently enabled.
   *
   * @param context The current context
   * @param enabled {@code true} if auto scan mode should be enabled
   */
  public static void setAutoScanEnabled(Context context, boolean enabled) {
    setBooleanPreference(context, R.string.pref_key_auto_scan_enabled, enabled);
  }

  /**
   * Set whether point scanning is currently enabled.
   *
   * @param context The current context
   * @param enabled {@code true} to enter point scan mode, {@code false} to return to box scanning
   */
  public static void setPointScanEnabled(Context context, boolean enabled) {
    setBooleanPreference(context, R.string.pref_key_point_scan_enabled, enabled);
  }

  /**
   * Check whether auto-select is currently enabled.
   *
   * @param context The current context
   * @return {@code true} auto-selecting is controlled from the auto-scan menu, {@code false}
   *     otherwise
   */
  public static boolean isAutoselectEnabled(Context context) {
    String autoselectGlobalMenuPrefValue =
        getStringPreference(
            context,
            R.string.switch_access_choose_action_global_menu_behavior_key,
            R.string.switch_access_pref_choose_action_behavior_default);
    return TextUtils.equals(
        autoselectGlobalMenuPrefValue,
        context.getString(R.string.switch_access_choose_action_auto_select_key));
  }

  /**
   * Set whether auto-select is enabled.
   *
   * @param context The current context
   * @param enabled {@code true} to enable auto-select when the global menu controls the preference,
   *     {@code false} to disable it.
   */
  public static void setAutoselectEnabled(Context context, boolean enabled) {
    int newStringValue =
        (enabled)
            ? R.string.switch_access_choose_action_auto_select_key
            : R.string.switch_access_choose_action_show_menu_key;
    setStringPreference(
        context, R.string.switch_access_choose_action_global_menu_behavior_key, newStringValue);
  }

  /**
   * Sets the current scanning method.
   *
   * @param context The current context
   * @param newScanningMethod The resource id for the new scanning method preference
   */
  public static void setScanningMethod(Context context, int newScanningMethod) {
    setStringPreference(context, R.string.pref_scanning_methods_key, newScanningMethod);
  }

  /**
   * Return whether spoken feedback is enabled.
   *
   * @param context The current context
   * @return {@code true} if spoken feedback is enabled
   */
  public static boolean isSpokenFeedbackEnabled(Context context) {
    return getBooleanPreference(context, R.string.pref_key_switch_access_spoken_feedback, false);
  }

  /**
   * Returns whether non-actionable items should be included when scanning items on the screen.
   * Users who can read text on the screen only need to use Switch Access to scan actionable items.
   * However, some users (e.g. blind or low vision users) rely on spoken feedback to get information
   * about the screen. In addition to hearing information about actionable items, they need to hear
   * information about non-actionable items in order to use the application.
   *
   * <p>Because users cannot perform actions on non-actionable items, this feature is automatically
   * disabled when spoken feedback is disabled.
   *
   * @param context The current context
   * @return {@code true} if non-actionable items should be scanned; {@code false} otherwise
   */
  public static boolean shouldScanNonActionableItems(Context context) {
    return (isSpokenFeedbackEnabled(context)
        && getBooleanPreference(
            context,
            R.string.pref_key_scan_non_actionable_items,
            R.bool.pref_scan_non_actionable_items_default_value));
  }

  /**
   * Returns whether hints should be included when providing spoken feedback.
   *
   * @param context The current context
   * @return {@code true} if hints should be included; {@code false} otherwise
   */
  public static boolean shouldSpeakHints(Context context) {
    return isSpokenFeedbackEnabled(context)
        && getBooleanPreference(
            context, R.string.pref_key_enable_hints, R.bool.pref_enable_hints_default_value);
  }

  /**
   * Return whether the first and last highlighted item should be spoke when providing spoken
   * feedback. This setting is only relevant when more than one item is highlighted.
   *
   * @param context The current context
   * @return {@code true} if the first and last items should be spoken for groups of items
   */
  public static boolean shouldSpeakFirstAndLastItem(Context context) {
    return getBooleanPreference(
        context,
        R.string.pref_key_switch_access_speak_first_last_item,
        R.bool.pref_speak_first_last_item_default_value);
  }

  /**
   * Return whether the number of highlighted elements should be included when providing spoken
   * feedback. This setting is only relevant when more than one item is highlighted.
   *
   * @param context The current context
   * @return {@code true} if the number of highlighted elements should be spoken
   */
  public static boolean shouldSpeakNumberOfItems(Context context) {
    return getBooleanPreference(
        context, R.string.pref_key_switch_access_speak_number_of_items, true);
  }

  /**
   * Return whether the all highlighted items should be included when providing spoken feedback.
   * This setting is only relevant when more than one item is highlighted.
   *
   * @param context The current context
   * @return {@code true} if all highlighted elements should be spoken
   */
  public static boolean shouldSpeakAllItems(Context context) {
    return getBooleanPreference(context, R.string.pref_key_switch_access_speak_all_items, true);
  }

  /**
   * Return whether autoscan should wait until feedback is complete before moving highlight to the
   * next item or group of items.
   *
   * @param context The current context
   * @return {@code true} if autoscan should wait until feedback is complete before moving highlight
   *     to the next item or group of items
   */
  public static boolean shouldFinishSpeechBeforeContinuingScan(Context context) {
    return isSpokenFeedbackEnabled(context)
        && getBooleanPreference(
            context, R.string.pref_key_switch_access_spoken_feedback_finish_speech, true);
  }

  /**
   * Returns whether an echo should be provided after a key on the keyboard is typed.
   *
   * @param context The current context
   * @return {@code true} if an echo should be provided after a key on the keyboard is typed
   */
  public static boolean shouldSpeakTypedKey(Context context) {
    return isSpokenFeedbackEnabled(context)
        && getBooleanPreference(
            context, R.string.pref_key_keyboard_echo, R.bool.pref_keyboard_echo_default_value);
  }

  /**
   * Returns whether the pitch of spoken feedback for typed keys should be changed.
   *
   * @param context The current context
   * @return {@code true} if the typed keys should be spoken in a lower-pitched voice
   */
  public static boolean shouldChangePitchForIme(Context context) {
    return isSpokenFeedbackEnabled(context)
        && getBooleanPreference(
            context,
            R.string.pref_key_pitch_change_for_ime,
            R.bool.pref_pitch_change_for_ime_default_value);
  }

  /**
   * Returns whether an acknowledgement should be provided after an item or group is selected.
   *
   * @param context The current context
   * @return {@code true} if an acknowledgement should be provided after an item or group is
   *     selected
   */
  public static boolean shouldSpeakSelectedItemOrGroup(Context context) {
    return isSpokenFeedbackEnabled(context)
        && getBooleanPreference(
            context,
            R.string.pref_key_speak_selected_item_or_group,
            R.bool.pref_speak_selected_item_or_group_default_value);
  }

  /**
   * Returns whether the location of the highlighted element in a list or grid should be included in
   * the spoken feedback.
   *
   * @param context The current context
   * @return {@code true} if location of the highlighted element should be spoken
   */
  public static boolean shouldSpeakElementPosition(Context context) {
    return isSpokenFeedbackEnabled(context)
        && getBooleanPreference(
            context,
            R.string.pref_key_speak_container_element_position,
            R.bool.pref_speak_container_element_position_default_value);
  }

  /**
   * Returns whether the element type (e.g., button) should be included in the spoken feedback.
   *
   * @param context The current context
   * @return {@code true} if the element type should be spoken
   */
  public static boolean shouldSpeakElementType(Context context) {
    return isSpokenFeedbackEnabled(context)
        && getBooleanPreference(
            context,
            R.string.pref_key_speak_element_type,
            R.bool.pref_speak_element_type_default_value);
  }

  /**
   * Gets the maximum amount of time that speaking an item's description can take. Item descriptions
   * that exceed this time will be truncated.
   *
   * <p>Note: Hints and item type will always be fully spoken if enabled.
   *
   * @param context The current context
   * @return The maximum amount of time that speaking an item's description can take
   */
  public static int getMaximumTimePerItem(Context context) {
    float feedbackDurationSeconds;
    try {
      feedbackDurationSeconds =
          getFloatFromStringPreference(
              context,
              R.string.pref_key_switch_access_spoken_feedback_maximum_time_per_item,
              R.integer.pref_maximum_time_per_item_default_value_seconds);
    } catch (NumberFormatException e) {
      feedbackDurationSeconds =
          context
              .getResources()
              .getInteger(R.integer.pref_maximum_time_per_item_default_value_seconds);
    }
    return (int) (feedbackDurationSeconds * MILLISECONDS_PER_SECOND);
  }

  /**
   * Gets the element description order in the spoken feedback for a highlighted item.
   *
   * @param context The current context
   * @return The order in which element descriptions should be spoken
   */
  public static @DescriptionOrder int getElementDescriptionOrder(Context context) {
    String descriptionOrder =
        getStringPreference(
            context,
            R.string.pref_key_node_description_order,
            R.string.pref_node_description_order_default);
    return prefValueToDescriptionOrder(context.getResources(), descriptionOrder);
  }

  /**
   * Returns whether element IDs should be spoken for unlabeled element when providing spoken
   * feedback.
   *
   * @param context The current context
   * @return {@code true} if element IDs should be spoken for unlabeled buttons
   */
  public static boolean shouldSpeakElementIds(Context context) {
    return isSpokenFeedbackEnabled(context)
        && getBooleanPreference(
            context,
            R.string.pref_key_speak_element_ids,
            R.bool.pref_speak_element_ids_default_value);
  }

  /**
   * Returns whether vibration feedback should be provided along with the spoken feedback.
   *
   * @param context The current context
   * @return {@code true} if vibration feedback should be provided
   */
  public static boolean shouldPlayVibrationFeedback(Context context) {
    return isSpokenFeedbackEnabled(context)
        && getBooleanPreference(
            context,
            R.string.pref_key_vibration_feedback,
            R.bool.pref_vibration_feedback_default_value);
  }

  /**
   * Returns whether sound feedback should be provided along with the spoken feedback.
   *
   * @param context The current context
   * @return {@code true} if sound feedback should be provided
   */
  public static boolean shouldPlaySoundFeedback(Context context) {
    return isSpokenFeedbackEnabled(context)
        && getBooleanPreference(
            context, R.string.pref_key_sound_feedback, R.bool.pref_sound_feedback_default_value);
  }

  /**
   * Returns whether audio ducking should be enabled while speaking.
   *
   * @param context The current context
   * @return {@code true} if audio ducking should be enabled
   */
  public static boolean shouldDuckAudio(Context context) {
    return isSpokenFeedbackEnabled(context)
        && getBooleanPreference(
            context, R.string.pref_key_audio_ducking, R.bool.pref_audio_ducking_default_value);
  }

  /**
   * Gets the sound volume as a percentage of the media volume.
   *
   * @param context The current context
   * @return The sound volume as a percentage of the media volume
   */
  public static int getSoundVolumePercentage(Context context) {
    return getIntFromStringPreference(
        context, R.string.pref_key_sound_volume, R.string.pref_switch_access_sound_volume_default);
  }

  /**
   * Gets whether displaying speech output is enabled.
   *
   * @param context The current context
   * @return If speech output should be displayed visually
   */
  public static boolean isSpeechOutputVisible(Context context) {
    if (!isSpokenFeedbackEnabled(context)) {
      return false;
    }

    SharedPreferences prefs = SharedPreferencesUtils.getSharedPreferences(context);
    return SharedPreferencesUtils.getBooleanPref(
        prefs,
        context.getResources(),
        R.string.pref_key_display_speech_output,
        R.bool.pref_display_speech_output_default_value);
  }

  /**
   * Check whether auto-start scanning is enabled.
   *
   * @param context The current context
   * @return {@code true} if auto start scanning is currently enabled
   */
  public static boolean isAutostartScanEnabled(Context context) {
    return getBooleanPreference(
        context,
        R.string.switch_access_auto_start_scan_key,
        R.bool.switch_access_auto_start_scan_default);
  }

  /**
   * Check whether press on release is enabled. When enabled, presses occur when a key is released
   * (key up). When disabled, presses occur when a key is released (key down).
   *
   * @param context The current context
   * @return {@code true} if presses should occur on key up instead of key down
   */
  public static boolean isPressOnReleaseEnabled(Context context) {
    return getBooleanPreference(context, R.string.pref_key_switch_access_press_on_release, false);
  }

  /** Get the auto scan delay in seconds. */
  public static float getAutoScanDelaySeconds(Context context) {
    float delaySeconds;
    try {
      delaySeconds =
          getFloatFromStringPreference(
              context,
              R.string.pref_key_auto_scan_time_delay,
              R.string.pref_auto_scan_time_delay_default_value);
    } catch (NumberFormatException e) {
      delaySeconds =
          (float)
              Double.parseDouble(
                  context.getString(R.string.pref_auto_scan_time_delay_default_value));
    }
    return delaySeconds;
  }

  /** Get the auto scan delay in ms. */
  public static int getAutoScanDelayMs(Context context) {
    return (int) (getAutoScanDelaySeconds(context) * MILLISECONDS_PER_SECOND);
  }

  /** Get the extra time, in seconds, to spend on the first element at the start of scanning. */
  public static float getFirstItemScanDelaySeconds(Context context) {
    return getFloatFromStringPreference(
        context, R.string.pref_key_start_scan_delay, R.string.pref_start_scan_delay_default_value);
  }

  /**
   * Get the extra time, in milliseconds, to spend on the first element at the start of scanning.
   */
  public static int getFirstItemScanDelayMs(Context context) {
    return (int) (getFirstItemScanDelaySeconds(context) * MILLISECONDS_PER_SECOND);
  }

  /** Get debounce time. */
  public static int getDebounceTimeMs(Context context) {
    float delaySeconds =
        getFloatFromStringPreference(
            context, R.string.pref_key_debounce_time, R.string.pref_debounce_time_default);
    return (int) (delaySeconds * MILLISECONDS_PER_SECOND);
  }

  /** Get the current point scan line speed in dp/ms. */
  public static float getPointScanLineSpeed(Context context) {
    float lineSpeed;
    try {
      lineSpeed =
          getFloatFromStringPreference(
              context,
              R.string.pref_key_point_scan_line_speed,
              R.string.pref_point_scan_line_speed_default);
    } catch (NumberFormatException e) {
      lineSpeed = Integer.parseInt(context.getString(R.string.pref_point_scan_line_speed_default));
    }

    // The inputted line speed is given in cm/s. Before returning, convert to dp/ms, using the dpi
    // of the screen, to make it easier to work with internally.
    DisplayMetrics displayMetrics = ScreenUtils.getRealDisplayMetrics(context);
    float lineSpeedInDpPerSecond =
        lineSpeed * (displayMetrics.density * PIXEL_TO_DP_MULTIPLIER) / CENTIMETERS_PER_INCH;
    return lineSpeedInDpPerSecond / MILLISECONDS_PER_SECOND;
  }

  /** Get the current scanning animation repeat count. */
  public static int getNumberOfScanningLoops(Context context) {
    return getIntFromStringPreference(
        context,
        R.string.pref_key_point_scan_and_autoscan_loop_count,
        R.string.pref_point_scan_and_autoscan_loop_count_default);
  }

  /** Get the number of switches configured for group selection, Next, and Select. */
  public static int getNumSwitches(Context context) {
    int numSwitchesConfigured = 0;
    while ((GROUP_SELECTION_SWITCH_CONFIG_IDS.length > numSwitchesConfigured)
        && !getKeyCodesForPreference(
                context, GROUP_SELECTION_SWITCH_CONFIG_IDS[numSwitchesConfigured])
            .isEmpty()) {
      numSwitchesConfigured++;
    }
    return numSwitchesConfigured;
  }

  /** Get all the highlight paints. */
  public static Paint[] getHighlightPaints(Context context) {
    String[] highlightColorPrefKeys =
        context.getResources().getStringArray(R.array.switch_access_highlight_color_pref_keys);
    String[] highlightColorDefaults =
        context.getResources().getStringArray(R.array.switch_access_highlight_color_defaults);
    String[] highlightWeightPrefKeys =
        context.getResources().getStringArray(R.array.switch_access_highlight_weight_pref_keys);
    String defaultWeight = context.getString(R.string.pref_highlight_weight_default);

    Paint[] paints = new Paint[highlightColorPrefKeys.length];
    for (int i = 0; i < highlightColorPrefKeys.length; ++i) {
      String hexStringColor =
          getStringPreference(context, highlightColorPrefKeys[i], highlightColorDefaults[i]);
      int color = Integer.parseInt(hexStringColor, 16);
      Paint paint = new Paint();
      paint.setStyle(Paint.Style.STROKE);
      paint.setColor(color);
      paint.setAlpha(255);

      String stringWeight = getStringPreference(context, highlightWeightPrefKeys[i], defaultWeight);
      int weight = Integer.parseInt(stringWeight);
      DisplayMetrics dm = context.getResources().getDisplayMetrics();
      float strokeWidth = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, weight, dm);
      paint.setStrokeWidth(strokeWidth);
      paints[i] = paint;
    }
    return paints;
  }

  /**
   * Get the String key of the current scanning method.
   *
   * @param context The context associated with the scanning method
   * @return The String key of the current scanning method
   */
  public static String getCurrentScanningMethod(Context context) {
    return getStringPreference(
        context, R.string.pref_scanning_methods_key, R.string.pref_scanning_methods_default);
  }

  /**
   * Get the highlight colors that are used by the currently selected scanning method.
   *
   * @return The highlight colors configured for scanning
   */
  public static String[] getHighlightColors(Context context) {
    String[] highlightColorPrefKeys =
        context.getResources().getStringArray(R.array.switch_access_highlight_color_pref_keys);
    String[] highlightColorDefaults =
        context.getResources().getStringArray(R.array.switch_access_highlight_color_defaults);

    int numberOfColorsUsed = isGroupSelectionEnabled(context) ? getNumSwitches(context) : 1;
    String[] colors = new String[numberOfColorsUsed];
    for (int i = 0; i < numberOfColorsUsed; ++i) {
      colors[i] =
          getStringPreference(context, highlightColorPrefKeys[i], highlightColorDefaults[i]);
    }
    return colors;
  }

  /**
   * Get the highlight line styles that are used by the currently selected scanning method
   *
   * @return The highlight line styles configured for scanning
   */
  public static String[] getHighlightLineStyles(Context context) {
    String[] highlightWeightPrefKeys =
        context.getResources().getStringArray(R.array.switch_access_highlight_weight_pref_keys);
    String defaultWeight = context.getString(R.string.pref_highlight_weight_default);

    int numberOfHighlightStylesUsed =
        isGroupSelectionEnabled(context) ? getNumSwitches(context) : 1;
    String[] stringWeights = new String[numberOfHighlightStylesUsed];
    for (int i = 0; i < numberOfHighlightStylesUsed; ++i) {
      stringWeights[i] = getStringPreference(context, highlightWeightPrefKeys[i], defaultWeight);
    }
    return stringWeights;
  }

  /**
   * Gets the current highlight color used by the linear and row-column scanning methods.
   *
   * @return The highlight color used for scanning
   */
  public static String getHighlightColorForScanningMethodsWithOneHighlight(Context context) {
    return getStringPreference(
        context, R.string.pref_highlight_0_color_key, R.string.pref_highlight_0_color_default);
  }

  /**
   * Gets the current highlight weight used by the linear and row-column scanning methods.
   *
   * @return The highlight weight used for scanning
   */
  public static String getHighlightWeightForScanningMethodsWithOneHighlight(Context context) {
    return getStringPreference(
        context, R.string.pref_highlight_0_weight_key, R.string.pref_highlight_weight_default);
  }

  /**
   * Returns the set of long codes of the keys assigned to a preference.
   *
   * <p>This is a wrapper for {@link KeyAssignmentUtils#getKeyCodesForPreference} so that the set of
   * long codes can be cached.
   *
   * @param context The current context
   * @param resId The resource Id of the preference key
   * @return The {@code Set<Long>} of the keys assigned to the preference
   */
  // Casting an object to Set<Long> gives an unchecked cast warning, so suppress the
  // warning since this exception is guarded by a try / catch.
  @SuppressWarnings("unchecked")
  public static Set<Long> getKeyCodesForPreference(Context context, int resId) {
    return getKeyCodesForPreference(context, context.getString(resId));
  }

  /**
   * Returns the set of long codes of the keys assigned to a preference.
   *
   * <p>This is a wrapper for {@link KeyAssignmentUtils#getKeyCodesForPreference} so that the set of
   * long codes can be cached.
   *
   * @param context The current context
   * @param preferenceKey The resource Id of the preference key associated with a set of key codes
   * @return The {@code Set<Long>} of the keys assigned to the preference
   */
  // Casting an object to Set<Long> gives an unchecked cast warning, so suppress the
  // warning since this exception is guarded by a try / catch.
  @SuppressWarnings("unchecked")
  public static Set<Long> getKeyCodesForPreference(Context context, String preferenceKey) {

    // Note: The preference key associated with resId references a Set<String>, but we're
    // caching it as a Set<Long>.
    Object currentValue = retrieveCurrentValueFromCache(context, preferenceKey);
    if (currentValue instanceof Set) {
      try {
        return (Set<Long>) currentValue;
      } catch (ClassCastException exception) {
        // If we retrieve a preference value that isn't of type Set<Long>, we stored an
        // incorrectly formatted keycode preference. Get the preference value from
        // KeyAssignmentUtils instead. This shouldn't happen.
      }
    }

    Set<Long> newValue = KeyAssignmentUtils.getKeyCodesForPreference(context, preferenceKey);
    storeValueToCache(context, preferenceKey, newValue);
    return newValue;
  }

  /**
   * Checks if the screen is being used as a switch.
   *
   * @param context The current context
   * @return {@code true} if screen switch is enabled in the preferences. {@code false} otherwise
   */
  public static boolean isScreenSwitchEnabled(Context context) {
    if (!FeatureFlags.screenSwitch()) {
      // TODO: Cache if the screen switch is enabled.
      return false;
    }

    Set<String> prefKeys = KeyAssignmentUtils.getPrefKeys(context);
    SharedPreferences sharedPreferences = SharedPreferencesUtils.getSharedPreferences(context);
    long screenSwitchKeyCode = KeyAssignmentUtils.KEYCODE_SCREEN_SWITCH;
    for (String key : prefKeys) {
      if (getKeyCodesForPreference(context, key).contains(screenSwitchKeyCode)) {
        Set<String> stringSet = sharedPreferences.getStringSet(key, Collections.emptySet());
        String stringKeyCode = Long.toString(screenSwitchKeyCode);

        if (stringSet.contains(stringKeyCode)) {
          return true;
        }
      }
    }
    return false;
  }

  /**
   * Returns the user to default screen usage if screen-as-a-switch is enabled.
   *
   * @param context The current context.
   * @return {@code true} if the screen switch preference has been successfully removed, {@code
   *     false} otherwise
   */
  public static boolean disableScreenSwitch(Context context) {
    Set<String> prefKeys = KeyAssignmentUtils.getPrefKeys(context);
    SharedPreferences sharedPreferences = SharedPreferencesUtils.getSharedPreferences(context);
    long screenSwitchKeyCode = KeyAssignmentUtils.KEYCODE_SCREEN_SWITCH;
    for (String key : prefKeys) {
      if (getKeyCodesForPreference(context, key).contains(screenSwitchKeyCode)) {
        Set<String> stringSet =
            new HashSet<>(sharedPreferences.getStringSet(key, Collections.emptySet()));
        String stringKeyCode = Long.toString(screenSwitchKeyCode);

        if (stringSet.contains(stringKeyCode)) {
          stringSet.remove(stringKeyCode);
          sharedPreferences.edit().putStringSet(key, stringSet).apply();
          return true;
        }
      }
    }
    return false;
  }

  /**
   * Increases the number of times the setup guide has been opened by one and returns the new value.
   *
   * @param context The current context
   */
  public static int incrementAndGetSetupRequestCount(Context context) {
    int setupRequestCount =
        getIntFromStringPreference(
            context, R.string.key_setup_request_count, R.string.setup_request_count_default);

    setupRequestCount++;
    SharedPreferencesUtils.getSharedPreferences(context)
        .edit()
        .putString(
            context.getString(R.string.key_setup_request_count),
            Integer.toString(setupRequestCount))
        .apply();

    return setupRequestCount;
  }

  private static @DescriptionOrder int prefValueToDescriptionOrder(
      Resources resources, String value) {
    if (TextUtils.equals(value, resources.getString(R.string.type_name_state_key))) {
      return Compositor.DESC_ORDER_ROLE_NAME_STATE_POSITION;
    } else if (TextUtils.equals(value, resources.getString(R.string.state_name_type_key))) {
      return Compositor.DESC_ORDER_STATE_NAME_ROLE_POSITION;
    } else if (TextUtils.equals(value, resources.getString(R.string.name_type_state_key))) {
      return Compositor.DESC_ORDER_NAME_ROLE_STATE_POSITION;
    } else {
      LogUtils.e(TAG, "Unhandled description order preference value \"%s\"", value);
      return Compositor.DESC_ORDER_STATE_NAME_ROLE_POSITION;
    }
  }

  /* Helper methods for getting and setting preference values from shared preferences. */

  /** @throws NumberFormatException if the string does not contain a parsable {@code float} */
  private static float getFloatFromStringPreference(
      Context context, int preferenceKeyId, int defaultId) {
    String preferenceKey = context.getString(preferenceKeyId);
    Object currentValue = retrieveCurrentValueFromCache(context, preferenceKey);
    if (currentValue != null) {
      return (float) currentValue;
    }

    float newValue =
        SharedPreferencesUtils.getFloatFromStringPref(
            SharedPreferencesUtils.getSharedPreferences(context),
            context.getResources(),
            preferenceKeyId,
            defaultId);
    storeValueToCache(context, preferenceKey, newValue);
    return newValue;
  }

  /** @throws NumberFormatException if the string does not contain a parsable {@code integer} */
  private static int getIntFromStringPreference(
      Context context, int preferenceKeyId, int defaultId) {
    String preferenceKey = context.getString(preferenceKeyId);
    Object currentValue = retrieveCurrentValueFromCache(context, preferenceKey);

    if (currentValue != null) {
      return (int) currentValue;
    }

    int newValue =
        SharedPreferencesUtils.getIntFromStringPref(
            SharedPreferencesUtils.getSharedPreferences(context),
            context.getResources(),
            preferenceKeyId,
            defaultId);
    storeValueToCache(context, preferenceKey, newValue);
    return newValue;
  }

  private static String getStringPreference(
      Context context, int preferenceKeyId, int defaultValueResource) {
    return getStringPreference(
        context, context.getString(preferenceKeyId), context.getString(defaultValueResource));
  }

  private static String getStringPreference(
      Context context, String preferenceKey, String defaultValue) {
    Object currentValue = retrieveCurrentValueFromCache(context, preferenceKey);
    if (currentValue != null) {
      return (String) currentValue;
    }

    String newValue =
        SharedPreferencesUtils.getSharedPreferences(context).getString(preferenceKey, defaultValue);
    storeValueToCache(context, preferenceKey, newValue);
    return newValue;
  }

  private static void setStringPreference(
      Context context, int preferenceKeyId, int newValueResource) {
    SharedPreferencesUtils.getSharedPreferences(context)
        .edit()
        .putString(context.getString(preferenceKeyId), context.getString(newValueResource))
        .apply();
  }

  private static boolean getBooleanPreference(
      Context context, int preferenceKeyId, int defaultValueResource) {
    return getBooleanPreference(
        context, preferenceKeyId, context.getResources().getBoolean(defaultValueResource));
  }

  private static boolean getBooleanPreference(
      Context context, int preferenceKeyId, boolean defaultValue) {
    String preferenceKey = context.getString(preferenceKeyId);
    Object currentValue = retrieveCurrentValueFromCache(context, preferenceKey);

    if (currentValue != null) {
      return (boolean) currentValue;
    }

    boolean newValue =
        SharedPreferencesUtils.getSharedPreferences(context)
            .getBoolean(context.getString(preferenceKeyId), defaultValue);
    storeValueToCache(context, preferenceKey, newValue);
    return newValue;
  }

  private static void setBooleanPreference(Context context, int preferenceKeyId, boolean newValue) {
    SharedPreferencesUtils.getSharedPreferences(context)
        .edit()
        .putBoolean(context.getString(preferenceKeyId), newValue)
        .apply();
  }

  @Nullable
  private static Object retrieveCurrentValueFromCache(Context context, String preferenceKey) {
    Object currentValue = null;
    SwitchAccessPreferenceCache switchAccessPreferenceCache =
        SwitchAccessPreferenceCache.getOrCreateInstance(context);
    if (switchAccessPreferenceCache != null) {
      currentValue = switchAccessPreferenceCache.retrievePreferenceValue(preferenceKey);
    }

    return currentValue;
  }

  private static void storeValueToCache(Context context, String preferenceKey, Object newValue) {
    SwitchAccessPreferenceCache switchAccessPreferenceCache =
        SwitchAccessPreferenceCache.getOrCreateInstance(context);
    if (switchAccessPreferenceCache != null) {
      switchAccessPreferenceCache.storePreferenceValue(preferenceKey, newValue);
    }
  }
}
