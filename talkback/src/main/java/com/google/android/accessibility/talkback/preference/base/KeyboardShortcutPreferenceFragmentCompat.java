/*
 * Copyright (C) 2021 Google Inc.
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
package com.google.android.accessibility.talkback.preference.base;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Color;
import android.os.Bundle;
import androidx.appcompat.app.AlertDialog;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import androidx.preference.PreferenceDialogFragmentCompat;
import com.google.android.accessibility.talkback.R;
import com.google.android.accessibility.talkback.keyboard.KeyComboManager;
import com.google.android.accessibility.talkback.keyboard.KeyComboModel;
import com.google.android.accessibility.talkback.preference.PreferencesActivityUtils;

/** A dialog fragment contains a customized list view for TalkBack supported actions. */
public class KeyboardShortcutPreferenceFragmentCompat extends PreferenceDialogFragmentCompat {

  private TextView keyAssignmentView;
  private TextView instructionText;
  private KeyComboManager keyComboManager;
  private KeyboardShortcutDialogPreference preference;

  /** Creates the fragment from given {@link KeyboardShortcutDialogPreference}. */
  public static KeyboardShortcutPreferenceFragmentCompat create(
      KeyboardShortcutDialogPreference preference) {
    KeyboardShortcutPreferenceFragmentCompat fragment =
        new KeyboardShortcutPreferenceFragmentCompat();
    Bundle args = new Bundle();
    args.putString(PreferenceDialogFragmentCompat.ARG_KEY, preference.getKey());
    fragment.setArguments(args);
    return fragment;
  }

  public KeyboardShortcutPreferenceFragmentCompat() {}

  @Override
  public Dialog onCreateDialog(Bundle savedInstanceState) {
    Dialog dialog = super.onCreateDialog(savedInstanceState);

    // androidx.preference.DialogPreference hides button from S, but this button exists. This button
    // shows below S.
    dialog.setOnShowListener(
        (dialogInterface) -> {
          Button okButton = ((AlertDialog) dialog).getButton(DialogInterface.BUTTON_POSITIVE);
          if (okButton != null) {
            okButton.setOnClickListener(
                (View v) -> {
                  processOKButtonClickListener();
                });
            okButton.setFocusableInTouchMode(true);
            okButton.requestFocus();
          }
          preference.registerDialogKeyEvent(dialog);
        });

    return dialog;
  }

  @Override
  protected View onCreateDialogView(Context context) {
    super.onCreateDialogView(context);
    final LayoutInflater li = LayoutInflater.from(context);
    final View dialogView = li.inflate(R.layout.keyboard_shortcut_dialog, /* root= */ null);
    Bundle args = getArguments();
    String key = args.getString(PreferenceDialogFragmentCompat.ARG_KEY);
    preference = (KeyboardShortcutDialogPreference) getPreference();

    keyComboManager = KeyboardShortcutDialogPreference.getKeyComboManager(getContext());

    preference.setTemporaryKeyComboCodeWithoutTriggerModifier(
        keyComboManager.getKeyComboModel().getKeyComboCodeForKey(key));

    keyAssignmentView = (TextView) dialogView.findViewById(R.id.assigned_combination);
    instructionText = (TextView) dialogView.findViewById(R.id.instruction);
    instructionText.setText(keyComboManager.getKeyComboModel().getDescriptionOfEligibleKeyCombo());

    keyAssignmentView.setText(
        keyComboManager.getKeyComboStringRepresentation(
            preference.getTemporaryKeyComboCodeWithTriggerModifier()));

    View clear = dialogView.findViewById(R.id.clear);
    clear.setOnClickListener(
        (View v) -> {
          instructionText.setTextColor(Color.BLACK);
          preference.clearTemporaryKeyComboCode();
          updateKeyAssignmentText();
        });

    keyComboManager.setMatchKeyCombo(false);

    return dialogView;
  }

  @Override
  public void onDialogClosed(boolean positiveResult) {
    preference.onDialogClosed();
  }

  /** Updates key assignment by getting the summary of th preference. */
  void updateKeyAssignmentText() {
    keyAssignmentView.setText(preference.getSummary());
  }

  /** Processes when OK buttons is clicked. */
  void processOKButtonClickListener() {
    long temporaryKeyComboCode = preference.getTemporaryKeyComboCodeWithoutTriggerModifier();
    if (temporaryKeyComboCode == KeyComboModel.KEY_COMBO_CODE_INVALID
        || !keyComboManager.getKeyComboModel().isEligibleKeyComboCode(temporaryKeyComboCode)) {
      instructionText.setTextColor(Color.RED);
      PreferencesActivityUtils.announceText(instructionText.getText().toString(), getContext());
      return;
    }

    String key =
        keyComboManager
            .getKeyComboModel()
            .getKeyForKeyComboCode(preference.getTemporaryKeyComboCodeWithoutTriggerModifier());
    if (key == null) {
      preference.saveKeyCode();
      preference.notifyChanged();
    } else if (!key.equals(preference.getKey())) {
      preference.showOverrideKeyComboDialog(key);
      return;
    }
    Dialog dialog = getDialog();
    if (dialog != null) {
      dialog.dismiss();
    }
  }
}
