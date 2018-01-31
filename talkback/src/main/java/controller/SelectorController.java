/*
 * Copyright (C) 2017 Google Inc.
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

package com.google.android.accessibility.talkback.controller;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.support.annotation.NonNull;
import android.text.TextUtils;
import com.google.android.accessibility.compositor.Compositor;
import com.google.android.accessibility.talkback.Analytics;
import com.google.android.accessibility.talkback.R;
import com.google.android.accessibility.talkback.TalkBackVerbosityPreferencesActivity;
import com.google.android.accessibility.utils.FormFactorUtils;
import com.google.android.accessibility.utils.Performance.EventId;
import com.google.android.accessibility.utils.SharedPreferencesUtils;
import com.google.android.accessibility.utils.input.CursorController;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Class to handle changes to selector and calls from {@link GestureControllerApp}. */
public class SelectorController {
  private final Context mContext;
  private final Compositor mCompositor;
  private final CursorController mCursorController;
  private final Analytics mAnalytics;
  private final SharedPreferences mPrefs;

  // Values for speech rate
  public static final float MIN_RATE = 0.0f;
  public static final float MAX_RATE = 2.0f; // Max rate is double the 1.0 default rate
  public static final float RATE_INCREMENT =
      0.05f * (MAX_RATE - MIN_RATE); // change in 5% increments

  public SelectorController(
      @NonNull Context context,
      @NonNull Compositor compositor,
      @NonNull CursorController cursorController,
      @NonNull Analytics analytics) {

    mContext = context;
    mCompositor = compositor;
    mCursorController = cursorController;
    mAnalytics = analytics;

    mPrefs = SharedPreferencesUtils.getSharedPreferences(mContext);
  }

  /** Selects the previous or next setting. */
  public void selectPreviousOrNextSetting(EventId eventId, boolean isNext) {
    List<String> settings = getFilteredSettings();
    int settingsSize = settings.size();

    if (settingsSize == 0) {
      return;
    }

    // Get the index of the selected setting.
    String currentSettingKey = mContext.getString(R.string.pref_current_selector_setting_key);
    String currentSetting =
        mPrefs.getString(
            currentSettingKey, mContext.getString(R.string.pref_selector_setting_default));
    int index = settings.indexOf(currentSetting);

    // Change the selected setting.
    if (isNext) {
      index++;
      if (index >= settingsSize || index < 0) {
        // User has reached end of list so wrap to start of list.
        index = 0;
      }
    } else { // Choose previous setting.
      index--;
      if (index >= settingsSize || index < 0) {
        // User has reached start of list so wrap to end of list.
        index = settingsSize - 1;
      }
    }

    String newCurrentSetting = settings.get(index);
    mPrefs.edit().putString(currentSettingKey, newCurrentSetting).apply();

    announceSetting(eventId, newCurrentSetting);
  }

  /**
   * Filter settings based on device. Filter out the settings turned off by users in selector
   * preferences.
   */
  private List<String> getFilteredSettings() {
    List<String> settings = Arrays.asList(mContext.getResources().getStringArray(R.array.selector));
    ArrayList<String> filteredSettings = new ArrayList<>();

    String preferenceKey;

    for (String setting : settings) {
      preferenceKey = getPreferenceKeyFromString(setting);

      // Check if the SwitchPreference is on for this setting.
      if (preferenceKey != null && mPrefs.getBoolean(preferenceKey, true)) {
        if (!setting.equals(mContext.getString(R.string.selector_audio_focus))) {
          filteredSettings.add(setting);
        } else if (!FormFactorUtils.getInstance(mContext).isArc()) {
          // Add audio focus setting if not ARC, as for ARC this preference is not supported.
          filteredSettings.add(setting);
        }
      }
    }
    return filteredSettings;
  }

  /** Returns the preference key corresponding to {@param setting} or otherwise null */
  private String getPreferenceKeyFromString(String setting) {
    if (TextUtils.equals(mContext.getString(R.string.selector_speech_rate), setting)) {
      return mContext.getString(R.string.pref_selector_speech_rate_key);
    } else if (TextUtils.equals(mContext.getString(R.string.selector_verbosity), setting)) {
      return mContext.getString(R.string.pref_selector_verbosity_key);
    } else if (TextUtils.equals(mContext.getString(R.string.selector_granularity), setting)) {
      return mContext.getString(R.string.pref_selector_granularity_key);
    } else if (TextUtils.equals(mContext.getString(R.string.selector_audio_focus), setting)) {
      return mContext.getString(R.string.pref_selector_audio_focus_key);
    } else {
      return null;
    }
  }

  /**
   * Announces the new selected setting.
   *
   * @param eventId
   * @param newSetting The new current selected setting.
   */
  private void announceSetting(EventId eventId, String newSetting) {
    if (TextUtils.equals(mContext.getString(R.string.selector_speech_rate), newSetting)) {
      mCompositor.sendEvent(Compositor.EVENT_SELECT_SPEECH_RATE, eventId);
    } else if (TextUtils.equals(mContext.getString(R.string.selector_verbosity), newSetting)) {
      mCompositor.sendEvent(Compositor.EVENT_SELECT_VERBOSITY, eventId);
    } else if (TextUtils.equals(mContext.getString(R.string.selector_granularity), newSetting)) {
      mCompositor.sendEvent(Compositor.EVENT_SELECT_GRANULARITY, eventId);
    } else if (TextUtils.equals(mContext.getString(R.string.selector_audio_focus), newSetting)) {
      mCompositor.sendEvent(Compositor.EVENT_SELECT_AUDIO_FOCUS, eventId);
    }
  }

  /** Change the value of the selected setting. */
  public void performSelectedSettingAction(EventId eventId, boolean isNext) {
    String currentSetting =
        mPrefs.getString(
            mContext.getString(R.string.pref_current_selector_setting_key),
            mContext.getString(R.string.pref_selector_setting_default));

    if (TextUtils.equals(mContext.getString(R.string.selector_speech_rate), currentSetting)) {
      // TODO: Increase speech rate when swipe-up is assigned to the selected setting's
      // previous action, which will be the default assignment.
      changeSpeechRate(eventId, !isNext);
    } else if (TextUtils.equals(mContext.getString(R.string.selector_verbosity), currentSetting)) {
      changeVerbosity(isNext);
    } else if (TextUtils.equals(
        mContext.getString(R.string.selector_granularity), currentSetting)) {
      changeGranularity(eventId, isNext);
    } else if (TextUtils.equals(
        mContext.getString(R.string.selector_audio_focus), currentSetting)) {
      switchOnOrOffAudioDucking(eventId);
    }
  }
  /** Change the value of the granularity. */
  private void changeGranularity(EventId eventId, boolean isNext) {
    boolean result;
    if (isNext) {
      result = mCursorController.nextGranularity(eventId);
    } else {
      result = mCursorController.previousGranularity(eventId);
    }
    if (result) {
      mAnalytics.onGranularityChanged(
          mCursorController.getCurrentGranularity(),
          Analytics.TYPE_SELECTOR,
          /* isPending= */ true);
    }
  }

  /** Decrease or increase the speech rate. */
  private void changeSpeechRate(EventId eventId, boolean isIncrease) {
    float currentRate =
        SharedPreferencesUtils.getFloatFromStringPref(
            mPrefs,
            mContext.getResources(),
            R.string.pref_speech_rate_key,
            R.string.pref_speech_rate_default);
    float newRate;

    // Increase or decrease rate.
    if (isIncrease) {
      newRate = currentRate + RATE_INCREMENT;
    } else {
      newRate = currentRate - RATE_INCREMENT;
    }

    // Check if new rate is within range.
    if (newRate < MIN_RATE) {
      newRate = MIN_RATE;
    } else if (newRate > MAX_RATE) {
      newRate = MAX_RATE;
    }

    mPrefs
        .edit()
        .putString(mContext.getString(R.string.pref_speech_rate_key), Float.toString(newRate))
        .apply();
    mCompositor.sendEvent(Compositor.EVENT_SPEECH_RATE_CHANGE, eventId);
  }

  /** Set the current verbosity preset to the previous or next verbosity preset. */
  private void changeVerbosity(boolean isNext) {
    List<String> verbosities =
        Arrays.asList(mContext.getResources().getStringArray(R.array.pref_verbosity_preset_values));
    int verbositiesSize = verbosities.size();
    if (verbositiesSize == 0) {
      return;
    }

    // Get the index of the current verbosity preset.
    String verbosityPresetKey = mContext.getString(R.string.pref_verbosity_preset_key);
    String currentVerbosity =
        mPrefs.getString(
            verbosityPresetKey, mContext.getString(R.string.pref_verbosity_preset_value_default));
    int index = verbosities.indexOf(currentVerbosity);

    // Change the verbosity preset.
    if (isNext) {
      index++;
      if (index >= verbositiesSize || index < 0) {
        // User has reached end of list so wrap to start of list.
        index = 0;
      }
    } else { // Choose previous preset.
      index--;
      if (index >= verbositiesSize || index < 0) {
        // User has reached start of list so wrap to end of list.
        index = verbositiesSize - 1;
      }
    }

    String newVerbosity = verbosities.get(index);
    mAnalytics.onManuallyChangeSetting(
        verbosityPresetKey, Analytics.TYPE_SELECTOR, /* isPending= */ true);
    mPrefs.edit().putString(verbosityPresetKey, newVerbosity).apply();
    // Announce new preset. If the TalkBackVerbosityPreferencesActivity fragment is visible,
    // the fragment's OnSharedPreferenceChangeListener.onSharedPreferenceChanged will also call this
    // method. SpeechController will then deduplicate the announcement event so only one is spoken.
    TalkBackVerbosityPreferencesActivity.announcePresetChange(newVerbosity, mContext);
  }

  /** Switch on or off audio ducking. */
  private void switchOnOrOffAudioDucking(EventId eventId) {
    Resources res = mContext.getResources();
    boolean audioFocusOn =
        SharedPreferencesUtils.getBooleanPref(
            mPrefs, res, R.string.pref_use_audio_focus_key, R.bool.pref_use_audio_focus_default);
    mAnalytics.onManuallyChangeSetting(
        res.getString(R.string.pref_use_audio_focus_key),
        Analytics.TYPE_SELECTOR, /* isPending */
        true);
    SharedPreferencesUtils.putBooleanPref(
        mPrefs, res, R.string.pref_use_audio_focus_key, !audioFocusOn);
    mCompositor.sendEvent(Compositor.EVENT_AUDIO_FOCUS_SWITCH, eventId);
  }
}
