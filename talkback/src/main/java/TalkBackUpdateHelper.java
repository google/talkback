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

package com.google.android.accessibility.talkback;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Resources;
import android.os.Handler;
import android.support.v4.app.NotificationCompat;
import com.google.android.accessibility.talkback.utils.VerbosityPreferences;
import com.google.android.accessibility.utils.SharedPreferencesUtils;
import com.google.android.accessibility.utils.keyboard.KeyComboManager;

public class TalkBackUpdateHelper {
  public static final String PREF_APP_VERSION = "app_version";

  /** Time in milliseconds after initialization to delay the posting of TalkBack notifications. */
  private static final int NOTIFICATION_DELAY = 5000;

  /**
   * Notification ID for the Gesture Change notification. This is also used in the
   * GestureChangeNotificationActivity to dismiss the notification.
   */
  /* package */ static final int GESTURE_CHANGE_NOTIFICATION_ID = 2;

  /** Notification ID for the built-in gesture change notification. */
  /* package */ private static final int BUILT_IN_GESTURE_CHANGE_NOTIFICATION_ID = 3;

  private final Handler mHandler = new Handler();

  private final TalkBackService mService;
  private final NotificationManager mNotificationManager;
  private final SharedPreferences mSharedPreferences;

  public TalkBackUpdateHelper(TalkBackService service) {
    mService = service;
    mNotificationManager =
        (NotificationManager) mService.getSystemService(Context.NOTIFICATION_SERVICE);
    mSharedPreferences = SharedPreferencesUtils.getSharedPreferences(mService);
  }

  public void showPendingNotifications() {
    // Revision 74 changes the gesture model for Jelly Bean and above.
    // This flag is used to ensure they accept the notification of this
    // change.
    final boolean userMustAcceptGestureChange =
        mSharedPreferences.getBoolean(
            mService.getString(R.string.pref_must_accept_gesture_change_notification), false);

    if (userMustAcceptGestureChange) {
      // Build the intent for when the notification is clicked.
      final Intent notificationIntent =
          new Intent(mService, GestureChangeNotificationActivity.class);
      notificationIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
      notificationIntent.addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);

      NotificationPosterRunnable runnable =
          new NotificationPosterRunnable(
              buildGestureChangeNotification(notificationIntent), GESTURE_CHANGE_NOTIFICATION_ID);
      mHandler.postDelayed(runnable, NOTIFICATION_DELAY);
    }
  }

  public void checkUpdate() {
    final int previousVersion = mSharedPreferences.getInt(PREF_APP_VERSION, -1);

    final PackageManager pm = mService.getPackageManager();
    final int currentVersion;

    try {
      final PackageInfo packageInfo = pm.getPackageInfo(mService.getPackageName(), 0);
      currentVersion = packageInfo.versionCode;
    } catch (NameNotFoundException e) {
      e.printStackTrace();
      return;
    }

    if (previousVersion == currentVersion) {
      return;
    }

    final Editor editor = mSharedPreferences.edit();
    editor.putInt(PREF_APP_VERSION, currentVersion);

    // Revision 74 changes the gesture model added in revision 68.
    if ((previousVersion >= 68) && (previousVersion < 74)) {
      notifyUserOfGestureChanges();
    }

    // Revision 84 combines the TalkBack and continuous reading breakout
    // menus. References to the continuous reading menu are silently
    // remapped to the local breakout menu.
    if (previousVersion < 84) {
      remapContinuousReadingMenu();
    }

    // Revision 90 removes the "shake to read" checkbox preference and
    // replaces it with a list preference of shake velocity thresholds that
    // includes a default "Off" option.
    if (previousVersion < 90) {
      remapShakeToReadPref();
    }

    // Revision 97 moved granularity selection into a local context menu, so
    // the up-then-down and down-then-up gestures were remapped to help
    // users navigate past groups of things, like web content and lists.
    if ((previousVersion != -1) && (previousVersion < 97)) {
      notifyUserOfBuiltInGestureChanges();
    }

    // TalkBack 4.5 changes the default up and down gestures to prev/next navigation setting.
    if ((previousVersion != -1) && previousVersion < 40500000) {
      notifyUserOfBuiltInGestureChanges45();
    }

    // TalkBack 5.2 adds verbosity presets.
    if ((previousVersion != -1) && previousVersion < 50200000) {
      copyVerbosityActivePrefsToPresetCustom(editor);
    }

    // Update key combo model.
    KeyComboManager keyComboManager = mService.getKeyComboManager();
    if (keyComboManager != null) {
      keyComboManager.getKeyComboModel().updateVersion(previousVersion);
    }

    editor.apply();
  }

  /** Copies preferences from the old preference keys, to preference keys for preset "custom". */
  private void copyVerbosityActivePrefsToPresetCustom(SharedPreferences.Editor editor) {
    Resources resources = mService.getResources();
    String presetName = resources.getString(R.string.pref_verbosity_preset_value_custom);

    // Copy boolean preferences.
    copyPreferenceBoolean(
        editor, presetName, R.string.pref_a11y_hints_key, R.bool.pref_a11y_hints_default);
    copyPreferenceBoolean(
        editor,
        presetName,
        R.string.pref_speak_container_element_positions_key,
        R.bool.pref_speak_container_element_positions_default);
    copyPreferenceBoolean(
        editor, presetName, R.string.pref_speak_roles_key, R.bool.pref_speak_roles_default);
    copyPreferenceBoolean(
        editor,
        presetName,
        R.string.pref_phonetic_letters_key,
        R.bool.pref_phonetic_letters_default);
    copyPreferenceBoolean(
        editor, presetName, R.string.pref_intonation_key, R.bool.pref_intonation_default);
    copyPreferenceBoolean(
        editor, presetName, R.string.pref_screenoff_key, R.bool.pref_screenoff_default);

    // Copy string preferences.
    copyPreferenceString(
        editor, presetName, R.string.pref_keyboard_echo_key, R.string.pref_keyboard_echo_default);

    // Set preset to custom.
    editor.putString(
        resources.getString(R.string.pref_verbosity_preset_key),
        resources.getString(R.string.pref_verbosity_preset_value_custom));
  }

  private void copyPreferenceBoolean(
      SharedPreferences.Editor editor, String presetName, int prefKeyId, int prefDefaultId) {
    Resources resources = mService.getResources();
    String key = resources.getString(prefKeyId);
    boolean valueDefault = resources.getBoolean(prefDefaultId);
    boolean value = mSharedPreferences.getBoolean(key, valueDefault);
    String keyForPreset = VerbosityPreferences.toPresetPrefKey(presetName, key);
    editor.putBoolean(keyForPreset, value);
  }

  private void copyPreferenceString(
      SharedPreferences.Editor editor, String presetName, int prefKeyId, int prefDefaultId) {
    Resources resources = mService.getResources();
    String key = resources.getString(prefKeyId);
    String valueDefault = resources.getString(prefDefaultId);
    String value = mSharedPreferences.getString(key, valueDefault);
    String keyForPreset = VerbosityPreferences.toPresetPrefKey(presetName, key);
    editor.putString(keyForPreset, value);
  }

  /**
   * Persists old default gestures (or preserves explicit user-defined gestures) and posts the
   * gesture change notification.
   */
  private void notifyUserOfGestureChanges() {
    final Editor editor = mSharedPreferences.edit();

    // Manually persist old defaults until the user acknowledges the change.
    deprecateStringPreference(
        editor,
        R.string.pref_shortcut_down_and_left_key,
        R.string.pref_deprecated_shortcut_down_and_left_default);
    deprecateStringPreference(
        editor,
        R.string.pref_shortcut_down_and_right_key,
        R.string.pref_deprecated_shortcut_down_and_right_default);
    deprecateStringPreference(
        editor,
        R.string.pref_shortcut_up_and_left_key,
        R.string.pref_deprecated_shortcut_up_and_left_default);
    deprecateStringPreference(
        editor,
        R.string.pref_shortcut_up_and_right_key,
        R.string.pref_deprecated_shortcut_up_and_right_default);

    // Flag that this user needs to get through the notification flow.
    editor.putBoolean(
        mService.getString(R.string.pref_must_accept_gesture_change_notification), true);

    editor.apply();

    // Build the intent for when the notification is clicked.
    final Intent notificationIntent = new Intent(mService, GestureChangeNotificationActivity.class);
    notificationIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    notificationIntent.addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);

    NotificationPosterRunnable runnable =
        new NotificationPosterRunnable(
            buildGestureChangeNotification(notificationIntent), GESTURE_CHANGE_NOTIFICATION_ID);
    mHandler.postDelayed(runnable, NOTIFICATION_DELAY);
  }

  private void notifyUserOfBuiltInGestureChanges() {
    // Build the intent for when the notification is clicked.
    final Intent notificationIntent = new Intent(mService, NotificationActivity.class);
    notificationIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    notificationIntent.addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
    notificationIntent.putExtra(
        NotificationActivity.EXTRA_INT_DIALOG_TITLE,
        R.string.notification_title_talkback_gestures_changed);
    notificationIntent.putExtra(
        NotificationActivity.EXTRA_INT_DIALOG_MESSAGE,
        R.string.talkback_built_in_gesture_change_details);
    notificationIntent.putExtra(
        NotificationActivity.EXTRA_INT_NOTIFICATION_ID, BUILT_IN_GESTURE_CHANGE_NOTIFICATION_ID);

    NotificationPosterRunnable runnable =
        new NotificationPosterRunnable(
            buildGestureChangeNotification(notificationIntent),
            BUILT_IN_GESTURE_CHANGE_NOTIFICATION_ID);
    mHandler.postDelayed(runnable, NOTIFICATION_DELAY);
  }

  private void notifyUserOfBuiltInGestureChanges45() {
    // Expected behavior:
    // 1. If user has changed neither up nor down: clear both prefs (reset to default).
    //    User will see a change only in this case, so present a notification.
    // 2. If user has changed either setting, make sure that the gesture behavior stays the
    //    same (even if the user has changed one gesture and not the other).

    // Set the up and down gestures to the old defaults if the user hasn't modified them yet.
    final Editor editor = mSharedPreferences.edit();
    deprecateStringPreference(
        editor, R.string.pref_shortcut_up_key, R.string.pref_deprecated_shortcut_up);
    deprecateStringPreference(
        editor, R.string.pref_shortcut_down_key, R.string.pref_deprecated_shortcut_down);
    editor.apply();

    // Are the preferences both equal to the old default?
    String upPrefKey = mService.getString(R.string.pref_shortcut_up_key);
    String downPrefKey = mService.getString(R.string.pref_shortcut_down_key);
    String upPrefDeprecated = mService.getString(R.string.pref_deprecated_shortcut_up);
    String downPrefDeprecated = mService.getString(R.string.pref_deprecated_shortcut_down);

    // Only reset prefs if the user has changed at least one of them to a non-default value.
    boolean prefsMatchDeprecated =
        upPrefDeprecated.equals(mSharedPreferences.getString(upPrefKey, null))
            && downPrefDeprecated.equals(mSharedPreferences.getString(downPrefKey, null));
    if (prefsMatchDeprecated) {
      editor.remove(upPrefKey);
      editor.remove(downPrefKey);
      editor.apply();

      // Build the intent for when the notification is clicked.
      final Intent notificationIntent = new Intent(mService, NotificationActivity.class);
      notificationIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
      notificationIntent.addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
      notificationIntent.putExtra(
          NotificationActivity.EXTRA_INT_DIALOG_TITLE,
          R.string.notification_title_talkback_gestures_changed);
      notificationIntent.putExtra(
          NotificationActivity.EXTRA_INT_DIALOG_MESSAGE,
          R.string.talkback_built_in_gesture_change_details_45);
      notificationIntent.putExtra(
          NotificationActivity.EXTRA_INT_NOTIFICATION_ID, BUILT_IN_GESTURE_CHANGE_NOTIFICATION_ID);

      NotificationPosterRunnable runnable =
          new NotificationPosterRunnable(
              buildGestureChangeNotification(notificationIntent),
              BUILT_IN_GESTURE_CHANGE_NOTIFICATION_ID);
      mHandler.postDelayed(runnable, NOTIFICATION_DELAY);
    }
  }

  private Notification buildGestureChangeNotification(Intent clickIntent) {
    final PendingIntent pendingIntent =
        PendingIntent.getActivity(mService, 0, clickIntent, PendingIntent.FLAG_UPDATE_CURRENT);

    final String ticker = mService.getString(R.string.notification_title_talkback_gestures_changed);
    final String contentTitle =
        mService.getString(R.string.notification_title_talkback_gestures_changed);
    final String contentText =
        mService.getString(R.string.notification_message_talkback_gestures_changed);
    final Notification notification =
        new NotificationCompat.Builder(mService)
            .setSmallIcon(R.drawable.ic_stat_info)
            .setTicker(ticker)
            .setContentTitle(contentTitle)
            .setContentText(contentText)
            .setContentIntent(pendingIntent)
            .setAutoCancel(false)
            .setWhen(0)
            .build();

    notification.defaults |= Notification.DEFAULT_SOUND;
    notification.flags |= Notification.FLAG_ONGOING_EVENT;
    return notification;
  }

  /**
   * Persist an old default String preference that the user has set or, if none, the old default
   * value. Note, this method does not commit the Editor.
   *
   * @param editor The Editor for the given SharedPreferences
   * @param resIdPrefKey The resource ID of the preference key to update
   * @param deprecatedDefaultResId The old default value for the preference
   */
  private void deprecateStringPreference(
      Editor editor, int resIdPrefKey, int deprecatedDefaultResId) {
    final String key = mService.getString(resIdPrefKey);
    final String oldDefault = mService.getString(deprecatedDefaultResId);
    final String userSetOrOldDefault = mSharedPreferences.getString(key, oldDefault);

    editor.putString(key, userSetOrOldDefault);
  }

  /**
   * Replaces any user-defined gesture mappings that reference the continuous reading menu with the
   * local breakout menu.
   */
  private void remapContinuousReadingMenu() {
    final Editor editor = mSharedPreferences.edit();
    final String targetValue = "READ_ALL_BREAKOUT";
    final String replaceValue = "LOCAL_BREAKOUT";
    final int[] gestureKeys = {
      R.string.pref_shortcut_down_and_left_key,
      R.string.pref_shortcut_down_and_right_key,
      R.string.pref_shortcut_left_and_down_key,
      R.string.pref_shortcut_left_and_up_key,
      R.string.pref_shortcut_right_and_down_key,
      R.string.pref_shortcut_right_and_up_key,
      R.string.pref_shortcut_up_and_left_key,
      R.string.pref_shortcut_up_and_right_key
    };

    for (int key : gestureKeys) {
      final String prefKey = mService.getString(key);
      if (mSharedPreferences.getString(prefKey, "").equals(targetValue)) {
        editor.putString(prefKey, replaceValue);
      }
    }

    editor.apply();
  }

  /**
   * Handles the conversion from the check box preference to the list preference for the shake to
   * read feature. Users who have previously enabled the shake to read feature will be switched to
   * the medium velocity threshold.
   */
  private void remapShakeToReadPref() {
    final boolean oldPrefOn =
        SharedPreferencesUtils.getBooleanPref(
            mSharedPreferences,
            mService.getResources(),
            R.string.pref_shake_to_read_key,
            R.bool.pref_shake_to_read_default);

    if (oldPrefOn) {
      final Editor editor = mSharedPreferences.edit();
      editor.putString(
          mService.getString(R.string.pref_shake_to_read_threshold_key),
          mService.getString(R.string.pref_shake_to_read_threshold_conversion_default));
      editor.putBoolean(mService.getString(R.string.pref_shake_to_read_key), false);
      editor.apply();
    }
  }

  /**
   * Runnable used for posting notifications to the {@link NotificationManager} after a short delay.
   */
  private class NotificationPosterRunnable implements Runnable {
    private Notification mNotification;
    private int mId;

    NotificationPosterRunnable(Notification n, int id) {
      mNotification = n;
      mId = id;
    }

    @Override
    public void run() {
      mNotificationManager.notify(mId, mNotification);
    }
  }
}
