/*
 * Copyright 2020 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.android.accessibility.brailleime.dialog;

import android.app.Dialog;
import android.content.Context;
import androidx.appcompat.app.AlertDialog;
import com.google.android.accessibility.brailleime.BrailleIme;
import com.google.android.accessibility.brailleime.Dialogs;
import com.google.android.accessibility.brailleime.R;
import com.google.android.accessibility.utils.keyboard.KeyboardUtils;

/** An error dialog which shows when the keyboard is raised while TalkBack is off. */
public class TalkBackOffDialog extends ViewAttachedDialog {

  /** A callback to notify {@link BrailleIme}. */
  public interface Callback {
    void onSwitchToNextIme();

    void onLaunchSettings();
  }

  private Dialog dialog;
  private final Context context;
  private final Callback callback;

  public TalkBackOffDialog(Context context, Callback callback) {
    this.context = context;
    this.callback = callback;
  }

  @Override
  protected Dialog makeDialog() {
    AlertDialog.Builder dialogBuilder =
        Dialogs.getAlertDialogBuilder(context)
            .setTitle(R.string.talkback_off_dialog_title)
            .setMessage(R.string.talkback_off_or_suspend_dialog_message)
            .setPositiveButton(
                R.string.talkback_off_dialog_positive_button,
                (dialogInterface, i) -> callback.onLaunchSettings())
            .setOnCancelListener(dialogInterface -> callback.onSwitchToNextIme());
    if (KeyboardUtils.areMultipleImesEnabled(context)) {
      dialogBuilder.setNegativeButton(
          R.string.next_keyboard, (dialogInterface, i) -> callback.onSwitchToNextIme());
    }
    dialog = dialogBuilder.create();
    dialog.setCanceledOnTouchOutside(false);
    return dialog;
  }
}
