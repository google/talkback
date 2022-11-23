/*
 * Copyright (C) 2012 Google Inc.
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

package com.google.android.accessibility.braille.brailledisplay;

import static com.google.android.accessibility.utils.AccessibilityServiceCompatUtils.Constants.BRAILLE_KEYBOARD;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.database.ContentObserver;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import androidx.appcompat.app.AlertDialog;
import android.view.inputmethod.InputMethodManager;
import com.google.android.accessibility.braille.brailledisplay.controller.ImeHelper;
import com.google.android.accessibility.utils.MaterialComponentUtils;

/**
 * An Activity that alerts the user that a braille display is connected but that the IME associated
 * with the braille display is not currently enabled or not the current IME.
 */
public class BrailleDisplayImeUnavailableActivity extends Activity {
  private AlertDialog alertDialog;
  private InputMethodManager inputMethodManager;
  private final Handler handler = new Handler();
  private boolean ignoreNextFocusChanged = false;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    inputMethodManager = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
    getContentResolver()
        .registerContentObserver(
            Settings.Secure.getUriFor(Settings.Secure.DEFAULT_INPUT_METHOD),
            false,
            contentObserver);
  }

  @Override
  protected void onResume() {
    super.onResume();
    checkStatus();
  }

  @Override
  protected void onPause() {
    super.onPause();
    dismissDialog();
  }

  @Override
  protected void onDestroy() {
    super.onDestroy();
    getContentResolver().unregisterContentObserver(contentObserver);
  }

  @Override
  public void onWindowFocusChanged(boolean hasFocus) {
    super.onWindowFocusChanged(hasFocus);
    if (!hasFocus) {
      return;
    }
    if (ignoreNextFocusChanged) {
      ignoreNextFocusChanged = false;
      return;
    }
    checkStatus();
  }

  private void checkStatus() {
    if (!ImeHelper.isInputMethodEnabled(this, BRAILLE_KEYBOARD)) {
      showNotifyEnabledKeyboardDialog(this);
    } else if (!ImeHelper.isInputMethodDefault(this, BRAILLE_KEYBOARD)) {
      showNotifySwitchToKeyboardDialog(this);
    } else {
      finish();
    }
  }

  private void dismissDialog() {
    if (alertDialog != null && alertDialog.isShowing()) {
      alertDialog.dismiss();
    }
  }

  private void showNotifyEnabledKeyboardDialog(Context context) {
    alertDialog =
        MaterialComponentUtils.alertDialogBuilder(context)
            .setTitle(R.string.bd_ime_disabled_dialog_title)
            .setMessage(R.string.bd_ime_disabled_dialog_message)
            .setNegativeButton(android.R.string.cancel, (dialog, which) -> finish())
            .setPositiveButton(
                R.string.bd_ime_disabled_dialog_title_positive,
                (dialog, which) -> {
                  final Intent intent = new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS);
                  startActivity(intent);
                  finish();
                })
            .create();
    alertDialog.setCancelable(false);
    alertDialog.show();
  }

  private void showNotifySwitchToKeyboardDialog(Context context) {
    alertDialog =
        MaterialComponentUtils.alertDialogBuilder(context)
            .setTitle(R.string.bd_ime_not_default_dialog_title)
            .setMessage(R.string.bd_ime_not_default_dialog_message)
            .setNegativeButton(android.R.string.cancel, (dialog, which) -> finish())
            .setPositiveButton(
                R.string.bd_ime_not_default_dialog_positive,
                (dialog, which) -> {
                  // The next focus changed due to ime picker appear, do not show the dialog
                  // when it is showing.
                  ignoreNextFocusChanged = true;
                  handler.postDelayed(() -> inputMethodManager.showInputMethodPicker(), 300);
                })
            .create();
    alertDialog.setCancelable(false);
    alertDialog.show();
  }

  private final ContentObserver contentObserver =
      new ContentObserver(handler) {
        @Override
        public void onChange(boolean selfChange) {
          if (ImeHelper.isInputMethodDefault(
              BrailleDisplayImeUnavailableActivity.this, BRAILLE_KEYBOARD)) {
            finish();
          }
        }
      };
}
