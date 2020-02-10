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

import android.app.Activity;
import android.app.AlertDialog;
import android.app.NotificationManager;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import com.google.android.accessibility.utils.SharedPreferencesUtils;

public class GestureChangeNotificationActivity extends Activity {

  private SharedPreferences prefs;

  /**
   * The available shortcut gestures and their default actions. The default action for the gesture
   * specified by gestureNameRes[i] is the action specified by actionNameRes[i].
   */
  private final int[] gestureNameRes = {
    R.string.title_pref_shortcut_down_and_left,
    R.string.title_pref_shortcut_up_and_left,
    R.string.title_pref_shortcut_down_and_right,
    R.string.title_pref_shortcut_up_and_right,
    R.string.title_pref_shortcut_right_and_down,
    R.string.title_pref_shortcut_left_and_down,
    R.string.title_pref_shortcut_right_and_up,
    R.string.title_pref_shortcut_left_and_up
  };

  private final int[] actionNameRes = {
    R.string.shortcut_back,
    R.string.shortcut_home,
    R.string.shortcut_talkback_breakout,
    R.string.shortcut_local_breakout,
    R.string.shortcut_notifications,
    R.string.shortcut_unassigned,
    R.string.shortcut_unassigned,
    R.string.shortcut_overview
  };

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    prefs = SharedPreferencesUtils.getSharedPreferences(this);

    final CharSequence dialogTitle =
        getString(R.string.notification_title_talkback_gestures_changed);
    final CharSequence dialogMessage =
        getString(
            R.string.talkback_gesture_change_details,
            getMappingDescription(gestureNameRes, actionNameRes));
    final CharSequence acceptButtonText =
        getString(R.string.button_accept_changed_gesture_mappings);
    final OnClickListener acceptButtonListener =
        new OnClickListener() {
          @Override
          public void onClick(DialogInterface dialog, int which) {
            clearPreviouslyConfiguredMappings();
            dismissNotification();
            dialog.dismiss();
            finish();
          }
        };
    final CharSequence customizeButtonText = getString(R.string.button_customize_gesture_mappings);
    final OnClickListener customizeButtonListener =
        new OnClickListener() {
          @Override
          public void onClick(DialogInterface dialog, int which) {
            clearPreviouslyConfiguredMappings();
            dismissNotification();
            dialog.dismiss();
            final Intent shortcutsIntent =
                new Intent(getApplicationContext(), TalkBackShortcutPreferencesActivity.class);
            startActivity(shortcutsIntent);
            finish();
          }
        };

    new AlertDialog.Builder(this)
        .setTitle(dialogTitle)
        .setMessage(dialogMessage)
        .setCancelable(false)
        .setPositiveButton(acceptButtonText, acceptButtonListener)
        .setNeutralButton(customizeButtonText, customizeButtonListener)
        .create()
        .show();
  }

  private void clearPreviouslyConfiguredMappings() {
    final SharedPreferences.Editor editor = prefs.edit();

    editor.remove(getString(R.string.pref_shortcut_down_and_left_key));
    editor.remove(getString(R.string.pref_shortcut_down_and_right_key));
    editor.remove(getString(R.string.pref_shortcut_up_and_left_key));
    editor.remove(getString(R.string.pref_shortcut_up_and_right_key));
    editor.remove(getString(R.string.pref_shortcut_right_and_down_key));
    editor.remove(getString(R.string.pref_shortcut_right_and_up_key));
    editor.remove(getString(R.string.pref_shortcut_left_and_down_key));
    editor.remove(getString(R.string.pref_shortcut_left_and_up_key));

    editor.apply();
  }

  private CharSequence getMappingDescription(int[] directions, int[] mappings) {
    SpannableStringBuilder sb = new SpannableStringBuilder();

    for (int i = 0; i < directions.length; ++i) {
      sb.append(getString(directions[i])).append(": ").append(getString(mappings[i])).append("\n");
    }

    return sb;
  }

  private void dismissNotification() {
    final int notificationId = TalkBackUpdateHelper.GESTURE_CHANGE_NOTIFICATION_ID;
    final NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
    nm.cancel(notificationId);

    // Clear the flag signaling TalkBack to display the notification again
    final SharedPreferences.Editor editor = prefs.edit();
    editor.remove(getString(R.string.pref_must_accept_gesture_change_notification));
    editor.apply();
  }
}
