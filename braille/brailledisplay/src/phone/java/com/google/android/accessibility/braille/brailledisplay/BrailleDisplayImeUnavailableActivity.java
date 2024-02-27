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
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import com.google.android.accessibility.braille.common.BrailleCommonUtils;
import com.google.android.accessibility.utils.material.MaterialComponentUtils;

/**
 * An Activity that alerts the user that a braille display is connected but that the IME associated
 * with the braille display is not currently enabled or not the current IME. Besides, the
 * InputMethodPicker can only launch with foreground instance, so the Activity, this class, is
 * necessary.
 */
public class BrailleDisplayImeUnavailableActivity extends Activity {
  private AlertDialog alertDialog;
  private InputMethodManager inputMethodManager;
  private final Handler handler = new Handler();
  private boolean ignoreNextFocusChanged = false;
  private static AccessibilityDialogBuilderProvider dialogBuilderProvider;

  /** Provides the necessary instance for building the accessibility overlay type dialog. */
  public interface AccessibilityDialogBuilderProvider {
    AlertDialog.Builder getAccessibilityDialogBuilder();
  }

  /** Client invokes this to provide us with the accessibility dialog builder provider instance. */
  public static void initialize(AccessibilityDialogBuilderProvider dialogBuilderProvider) {
    BrailleDisplayImeUnavailableActivity.dialogBuilderProvider = dialogBuilderProvider;
  }

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

  /** Whether this Activity is necessary to start. */
  public static boolean necessaryToStart(Context context) {
    return !BrailleCommonUtils.isInputMethodEnabled(context, BRAILLE_KEYBOARD)
        || !BrailleCommonUtils.isInputMethodDefault(context, BRAILLE_KEYBOARD);
  }

  private void checkStatus() {
    if (!BrailleCommonUtils.isInputMethodEnabled(this, BRAILLE_KEYBOARD)) {
      showNotifyEnabledKeyboardDialog();
    } else if (!BrailleCommonUtils.isInputMethodDefault(this, BRAILLE_KEYBOARD)) {
      showNotifySwitchToKeyboardDialog();
    } else {
      finish();
    }
  }

  private void dismissDialog() {
    if (alertDialog != null && alertDialog.isShowing()) {
      alertDialog.dismiss();
    }
  }

  private void showNotifyEnabledKeyboardDialog() {
    dismissDialog();
    alertDialog =
        getAlertDialogBuilder()
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
    alertDialog.getWindow().setType(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY);
    alertDialog.show();
  }

  private void showNotifySwitchToKeyboardDialog() {
    dismissDialog();
    alertDialog =
        getAlertDialogBuilder()
            .setTitle(R.string.bd_ime_not_default_dialog_title)
            .setMessage(R.string.bd_ime_not_default_dialog_message)
            .setNegativeButton(android.R.string.cancel, (dialog, which) -> finish())
            .setPositiveButton(
                R.string.bd_ime_not_default_dialog_positive,
                (dialog, which) -> {
                  // The next focus changed due to ime picker appear, do not show the dialog
                  // when it is showing.
                  ignoreNextFocusChanged = true;
                  // Need delay to ensure showInputMethodPicker() receives correct UID, else it
                  // fails.
                  handler.postDelayed(() -> inputMethodManager.showInputMethodPicker(), 300);
                })
            .create();
    alertDialog.setCancelable(false);
    alertDialog.getWindow().setType(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY);
    alertDialog.show();
  }

  private AlertDialog.Builder getAlertDialogBuilder() {
    if (dialogBuilderProvider == null) {
      return MaterialComponentUtils.alertDialogBuilder(this);
    }
    return dialogBuilderProvider.getAccessibilityDialogBuilder();
  }

  private final ContentObserver contentObserver =
      new ContentObserver(handler) {
        @Override
        public void onChange(boolean selfChange) {
          if (BrailleCommonUtils.isInputMethodDefault(
              BrailleDisplayImeUnavailableActivity.this, BRAILLE_KEYBOARD)) {
            finish();
          }
        }
      };
}
