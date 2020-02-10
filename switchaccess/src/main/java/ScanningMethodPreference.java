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

package com.google.android.accessibility.switchaccess;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.preference.DialogPreference;
import androidx.annotation.NonNull;
import android.util.AttributeSet;
import android.view.View;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import com.google.common.annotations.VisibleForTesting;

/**
 * Dialog to let the user choose a scanning method in a preference. This class assumes that all
 * preferences are in the default shared preferences. It uses this assumption to store and retrieve
 * the preference value.
 */
public class ScanningMethodPreference extends DialogPreference {

  // The context for retrieving preference values and saving preference changes.
  private final Context context;

  // The resource ids for the radio buttons. The order should be kept in sync with that of the other
  // arrays.
  private final int[] radioButtonIds = {
    R.id.linear_scanning_radio_button,
    R.id.linear_scanning_except_keyboard_radio_button,
    R.id.row_column_scanning_radio_button,
    R.id.group_selection_radio_button,
  };

  // The resource ids for the scanning method description view ids. The order should be kept in sync
  // with that of the other arrays.
  private final int[] scanningMethodSummaryViewIds = {
    R.id.linear_scanning_description_view,
    R.id.linear_scanning_except_keyboard_description_view,
    R.id.row_column_scanning_description_view,
    R.id.group_selection_description_view,
  };

  // The preference keys for scanning methods. The order should be kept in sync with that of the
  // other arrays.
  private final int[] scanningMethodKeys = {
    R.string.linear_scanning_key,
    R.string.views_linear_ime_row_col_key,
    R.string.row_col_scanning_key,
    R.string.group_selection_key,
  };

  // The resource ids for the scanning method titles. The order should be kept in sync with that of
  // the other arrays.
  private final int[] scanningMethodTitleIds = {
    R.string.linear_scanning_title,
    R.string.linear_scanning_except_keyboard_title,
    R.string.row_column_scanning_title,
    R.string.option_scanning_title,
  };

  // Whether each of the available scanning methods is enabled (that is, whether the corresponding
  // radio button should be available to the user). The order should be kept in sync with that of
  // the other arrays.
  private final boolean[] isScanningMethodEnabled = {
    false, /* linear scanning */
    true, /* linear scanning except keyboard */
    true, /* row column scanning */
    true, /* group selection */
  };

  /**
   * @param context Context used for retrieving preference values and saving preference changes
   * @param attributes Attribute set passed to DialogInterface
   */
  public ScanningMethodPreference(Context context, AttributeSet attributes) {
    super(context, attributes);
    this.context = context;
    setDialogLayoutResource(R.layout.switch_access_setup_scanning_method_dialog);
    updateSummaryBasedOnCurrentValue();
  }

  @Override
  public void notifyChanged() {
    super.notifyChanged();
    updateSummaryBasedOnCurrentValue();
  }

  /**
   * Enable or disable a scanning method.
   *
   * @param scanningMethodKey The key of the scanning method to enable or disable
   * @param shouldEnable Whether the scanning method should be enabled or disabled
   */
  public void enableScanningMethod(int scanningMethodKey, boolean shouldEnable) {
    for (int i = 0; i < scanningMethodKeys.length; i++) {
      if (scanningMethodKey == scanningMethodKeys[i]) {
        isScanningMethodEnabled[i] = shouldEnable;
      }
    }
  }

  /** Returns {@code true} if the scanning method corresponding to the provided key is enabled. */
  @VisibleForTesting
  public boolean isScanningMethodEnabled(int scanningMethodKey) {
    for (int i = 0; i < scanningMethodKeys.length; i++) {
      if (scanningMethodKey == scanningMethodKeys[i]) {
        return isScanningMethodEnabled[i];
      }
    }
    return false;
  }

  @Override
  protected void onBindDialogView(@NonNull View dialogView) {
    for (int i = 0; i < radioButtonIds.length; i++) {
      RadioButton radioButton = dialogView.findViewById(radioButtonIds[i]);
      View summaryView = dialogView.findViewById(scanningMethodSummaryViewIds[i]);
      if (isScanningMethodEnabled[i]) {
        radioButton.setVisibility(View.VISIBLE);
        summaryView.setVisibility(View.VISIBLE);
        // Use an OnClickListener instead of on OnCheckChangedListener so we are informed when the
        // currently checked item is tapped. (The OnCheckChangedListener is only called when a
        // different radio button is selected.)
        final int keyIndex = i;
        View.OnClickListener scanningMethodOnClickListener =
            v -> {
              SwitchAccessPreferenceUtils.setScanningMethod(context, scanningMethodKeys[keyIndex]);

              Dialog dialog = getDialog();
              if (dialog != null) {
                dialog.dismiss();
              }
            };
        radioButton.setOnClickListener(scanningMethodOnClickListener);
        summaryView.setOnClickListener(scanningMethodOnClickListener);

      } else {
        radioButton.setVisibility(View.GONE);
        summaryView.setVisibility(View.GONE);
      }
    }
    RadioGroup scanningMethodRadioGroup =
        dialogView.findViewById(R.id.scanning_options_radio_group);
    updateCheckedBasedOnCurrentValue(scanningMethodRadioGroup);
  }

  @Override
  protected void showDialog(Bundle state) {
    super.showDialog(state);
    AlertDialog alertDialog = (AlertDialog) getDialog();
    if (alertDialog == null) {
      return;
    }

    alertDialog.getButton(DialogInterface.BUTTON_POSITIVE).setVisibility(View.GONE);
    alertDialog.getButton(DialogInterface.BUTTON_NEGATIVE).setVisibility(View.GONE);
  }

  private void updateSummaryBasedOnCurrentValue() {
    String currentValue = getCurrentPreferenceValue();
    for (int i = 0; i < scanningMethodKeys.length; i++) {
      if (getString(scanningMethodKeys[i]).equals(currentValue)) {
        setSummary(scanningMethodTitleIds[i]);
      }
    }
  }

  private void updateCheckedBasedOnCurrentValue(RadioGroup group) {
    String currentValue = getCurrentPreferenceValue();
    for (int i = 0; i < scanningMethodKeys.length; i++) {
      if (getString(scanningMethodKeys[i]).equals(currentValue)) {
        ((RadioButton) group.findViewById(radioButtonIds[i])).toggle();
        return;
      }
    }
  }

  private String getCurrentPreferenceValue() {
    return SwitchAccessPreferenceUtils.getCurrentScanningMethod(context);
  }

  private String getString(int resourceId) {
    return context.getString(resourceId);
  }
}
