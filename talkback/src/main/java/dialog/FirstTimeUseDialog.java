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

package com.google.android.accessibility.talkback.dialog;

import android.content.DialogInterface;
import android.content.SharedPreferences;
import androidx.annotation.Nullable;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CheckBox;
import android.widget.ScrollView;
import android.widget.TextView;
import com.google.android.accessibility.talkback.R;
import com.google.android.accessibility.talkback.TalkBackService;
import com.google.android.accessibility.utils.SharedPreferencesUtils;

/**
 * Provides interfaces and utils for first-time-use dialogs in
 * <li>DimScreen {@link com.google.android.accessibility.talkback.controller.DimScreenDialog}
 * <li>FullScreenRead {@link
 *     com.google.android.accessibility.talkback.controller.FullScreenReadDialog}
 * <li>Voice commands {@link
 *     com.google.android.accessibility.talkback.voicecommands.SpeechRecognitionDialog}
 * <li>SuspendTalkBack {@link com.google.android.accessibility.talkback.TalkBackSuspendDialog}
 */
public abstract class FirstTimeUseDialog extends BaseDialog {

  /** Invalid resource ID for unused resource. */
  protected static final int INVALID_RES_ID = -1;

  /** Dialog preference key */
  private final int showDialogPreference;
  /** Dialog preference */
  private final SharedPreferences prefs;

  @Nullable private CheckBox checkBox;
  // Dialog resource IDs
  private final int checkboxTextResId;
  private final int dialogMainMessageResId;
  // The dialog message can be customized by setting string resource.
  private int dialogSecondMessageResId;
  private int dialogThirdMessageResId;

  public FirstTimeUseDialog(
      @Nullable TalkBackService service,
      int showDialogPreference,
      int dialogTitleResId,
      int dialogMainMessageResId,
      int checkboxTextResId) {
    super(service, dialogTitleResId, /* pipeline= */ null);
    this.showDialogPreference = showDialogPreference;
    this.dialogMainMessageResId = dialogMainMessageResId;
    // Set invalid resource ID for unused dialog message.
    dialogSecondMessageResId = INVALID_RES_ID;
    dialogThirdMessageResId = INVALID_RES_ID;
    this.checkboxTextResId = checkboxTextResId;
    prefs = SharedPreferencesUtils.getSharedPreferences(service);
  }

  /** Handles Ok and Cancel button click event in dialog. */
  @Override
  public void handleDialogClick(int buttonClicked) {
    // Not to show dialog again if user confirmed and unchecked the checkbox.
    if (buttonClicked == DialogInterface.BUTTON_POSITIVE) {
      if (checkBox != null && !checkBox.isChecked()) {
        setShouldShowDialogPref(
            /** state= */
            false);
      }
    }
  }

  /** Handles dialog dismissed event. */
  @Override
  public void handleDialogDismiss() {
    checkBox = null;
  }

  @Override
  public String getMessageString() {
    return null;
  }

  @Override
  public View getCustomizedView() {
    LayoutInflater inflater = LayoutInflater.from(context);
    final ScrollView root = (ScrollView) inflater.inflate(R.layout.first_time_use_dialog, null);
    checkBox = root.findViewById(R.id.show_message_checkbox);
    final TextView mainContent = root.findViewById(R.id.dialog_content);
    checkBox.setText(checkboxTextResId);
    mainContent.setText(dialogMainMessageResId);

    // Customize dialog content and set text view visible.
    final TextView secondContent = root.findViewById(R.id.dialog_second_content);
    final TextView thirdContent = root.findViewById(R.id.dialog_third_content);
    if (dialogSecondMessageResId != INVALID_RES_ID) {
      secondContent.setVisibility(View.VISIBLE);
      secondContent.setText(dialogSecondMessageResId);
    }
    if (dialogThirdMessageResId != INVALID_RES_ID) {
      thirdContent.setVisibility(View.VISIBLE);
      thirdContent.setText(dialogThirdMessageResId);
    }
    return root;
  }

  /**
   * Sets dialog message text resource ID if customization is needed. And it's only for
   * TalkBackSuspendDialog used now.
   *
   * @param dialogSecondMessageResId text resource ID for the second dialog message
   * @param dialogThirdMessageResId text resource ID for the third dialog message
   */
  protected void setCustomizedDialogMessageResId(
      int dialogSecondMessageResId, int dialogThirdMessageResId) {
    this.dialogSecondMessageResId = dialogSecondMessageResId;
    this.dialogThirdMessageResId = dialogThirdMessageResId;
  }

  /**
   * Sets dialog preference setting.
   *
   * @param state dialog preference setting
   */
  protected void setShouldShowDialogPref(boolean state) {
    SharedPreferencesUtils.putBooleanPref(
        prefs, context.getResources(), showDialogPreference, state);
  }

  /**
   * Sets preference setting by key.
   *
   * @param keyResId preference key
   * @param state preference setting
   */
  protected void setSharedPreferencesByKey(int keyResId, boolean state) {
    SharedPreferencesUtils.putBooleanPref(prefs, context.getResources(), keyResId, state);
  }

  /**
   * Checks preference if dialog should be shown.
   *
   * @return true shows dialog; false hides dialog
   */
  public boolean getShouldShowDialogPref() {
    return prefs.getBoolean(context.getString(showDialogPreference), true);
  }
}
