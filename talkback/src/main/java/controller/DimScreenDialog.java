/*
 * Copyright 2018 Google Inc.
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

import android.content.DialogInterface;
import com.google.android.accessibility.talkback.R;
import com.google.android.accessibility.talkback.TalkBackService;
import com.google.android.accessibility.talkback.dialog.FirstTimeUseDialog;

/** Shows a dialog warning to the user before dimming the screen. {@link DimScreenControllerApp} */
public class DimScreenDialog extends FirstTimeUseDialog {

  private final DimScreenController dimScreenController;

  public DimScreenDialog(TalkBackService service, DimScreenController dimScreenController) {
    super(
        service,
        /* showDialogPreference= */ R.string.pref_show_dim_screen_confirmation_dialog,
        /* dialogTitleResId= */ R.string.dialog_title_dim_screen,
        /* dialogMainMessageResId= */ R.string.dialog_message_dim_screen,
        /* checkboxTextResId= */ R.string.show_suspend_warning_label);

    this.dimScreenController = dimScreenController;
  }

  @Override
  public void handleDialogClick(int buttonClicked) {
    super.handleDialogClick(buttonClicked);

    // TalkBack should be active here, but let's check just in case.
    if (buttonClicked == DialogInterface.BUTTON_POSITIVE && TalkBackService.isServiceActive()) {
      dimScreenController.makeScreenDim();
      setSharedPreferencesByKey(R.string.pref_dim_when_talkback_enabled_key, true);
    }
  }

  /**
   * By default, shows a dialog warning the user before dimming the screen. If the user has elected
   * to not show the dialog, or the user selects "OK" from the warning dialog, this method will turn
   * dimming on and set the shared preference on as well.
   *
   * @return {@code true} if dialog is shown.
   */
  public boolean showDialogThenDimScreen() {
    if (!getShouldShowDialogPref()) {
      dimScreenController.makeScreenDim();
      setSharedPreferencesByKey(R.string.pref_dim_when_talkback_enabled_key, true);
      return false;
    }

    showDialog();
    return true;
  }
}
