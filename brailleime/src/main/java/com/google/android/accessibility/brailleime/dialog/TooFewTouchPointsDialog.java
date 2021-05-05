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
import com.google.android.accessibility.brailleime.Dialogs;
import com.google.android.accessibility.brailleime.R;
import com.google.android.accessibility.utils.keyboard.KeyboardUtils;

/** An error dialog which shows when the device supports too few touch points. */
public class TooFewTouchPointsDialog extends ViewAttachedDialog {

  /** A callback to notify {@link BrailleIme}. */
  public interface Callback {
    void onSwitchToNextIme();
  }

  private Dialog dialog;
  private final Context context;
  private final Callback callback;

  public TooFewTouchPointsDialog(Context context, Callback callback) {
    this.context = context;
    this.callback = callback;
  }

  @Override
  protected Dialog makeDialog() {
    AlertDialog.Builder dialogBuilder = Dialogs.getAlertDialogBuilder(context);
    dialogBuilder
        .setTitle(context.getString(R.string.not_enough_touch_points_dialog_title))
        .setMessage(context.getString(R.string.not_enough_touch_points_dialog_message));
    if (KeyboardUtils.areMultipleImesEnabled(context)) {
      dialogBuilder.setPositiveButton(
          context.getString(R.string.next_keyboard),
          (dialog, which) -> callback.onSwitchToNextIme());
    } else {
      dialogBuilder.setPositiveButton(
          context.getString(R.string.done), (dialog, which) -> callback.onSwitchToNextIme());
    }
    dialogBuilder.setOnCancelListener(dialogInterface -> callback.onSwitchToNextIme());
    dialog = dialogBuilder.create();
    dialog.setCanceledOnTouchOutside(false);
    return dialog;
  }
}
