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

package com.google.android.accessibility.talkback.preference;

import android.app.Dialog;
import android.os.Bundle;
import androidx.appcompat.app.AlertDialog;
import android.view.View;
import android.widget.ListView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.preference.ListPreferenceDialogFragmentCompat;
import com.google.android.accessibility.talkback.R;

/**
 * For {@link WearListPreference} to shows a dialog with the custom title when the preference is
 * clicked.
 */
public class WearListPreferenceDialogFragmentCompat extends ListPreferenceDialogFragmentCompat {

  public static WearListPreferenceDialogFragmentCompat create(WearListPreference preference) {
    final WearListPreferenceDialogFragmentCompat fragment =
        new WearListPreferenceDialogFragmentCompat();
    final Bundle args = new Bundle(1);
    args.putString(ARG_KEY, preference.getKey());
    fragment.setArguments(args);
    return fragment;
  }

  @Override
  protected void onPrepareDialogBuilder(AlertDialog.Builder builder) {
    super.onPrepareDialogBuilder(builder);

    // Sets custom title.
    View titlePanel =
        getActivity()
            .getLayoutInflater()
            .inflate(R.layout.list_preference_dialog_title, /* root= */ null);
    TextView title = titlePanel.findViewById(R.id.dialog_title_text);
    title.setText(getPreference().getTitle());
    builder.setCustomTitle(titlePanel);
  }

  @Override
  @NonNull
  public Dialog onCreateDialog(Bundle savedInstanceState) {
    Dialog dialog = super.onCreateDialog(savedInstanceState);
    if (dialog instanceof AlertDialog) {
      dialog.create();
      ListView listView = ((AlertDialog) dialog).getListView();
      if (listView != null) {
        // Supports watch rotary input
        listView.requestFocus();
      }
    }
    return dialog;
  }
}
