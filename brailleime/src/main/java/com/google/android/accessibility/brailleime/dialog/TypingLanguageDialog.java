/*
 * Copyright 2019 Google Inc.
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
import com.google.android.accessibility.brailleime.BrailleLanguages;
import com.google.android.accessibility.brailleime.BrailleLanguages.Code;
import com.google.android.accessibility.brailleime.Dialogs;
import com.google.android.accessibility.brailleime.R;
import com.google.android.accessibility.brailleime.UserPreferences;
import java.util.List;

/** A dialog for user to select typing language. */
public class TypingLanguageDialog extends ViewAttachedDialog {
  /** Callback for typing language dialog events. */
  interface Callback {
    void showContextMenu();

    void onTypingLanguageChanged();
  }

  private Dialog dialog;
  private final Context context;
  private final Callback callback;

  public TypingLanguageDialog(Context context, Callback callback) {
    this.context = context;
    this.callback = callback;
  }

  @Override
  protected Dialog makeDialog() {
    List<Code> selectedCodes = BrailleLanguages.getSelectedCodes(context);
    Code currentCode = BrailleLanguages.getCurrentCodeAndCorrect(context);
    int currentCodePosition = selectedCodes.indexOf(currentCode);
    AlertDialog.Builder dialogBuilder =
        Dialogs.getAlertDialogBuilder(context)
            .setTitle(R.string.change_typing_language_dialog_title)
            .setSingleChoiceItems(
                selectedCodes.stream()
                    .map(code -> code.getUserFacingName(context.getResources()))
                    .toArray(CharSequence[]::new),
                currentCodePosition,
                (dialog, which) -> {
                  if (which != currentCodePosition) {
                    UserPreferences.writeTranslateCode(context, selectedCodes.get(which));
                    callback.onTypingLanguageChanged();
                  }
                  if (callback != null) {
                    callback.showContextMenu();
                  }
                  dialog.dismiss();
                })
            .setPositiveButton(
                android.R.string.ok, (dialogInterface, index) -> callback.showContextMenu());
    dialog = dialogBuilder.create();
    dialog.setCanceledOnTouchOutside(false);
    return dialog;
  }
}
