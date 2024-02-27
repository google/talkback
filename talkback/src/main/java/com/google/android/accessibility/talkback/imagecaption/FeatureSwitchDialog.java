/*
 * Copyright (C) 2023 Google Inc.
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

package com.google.android.accessibility.talkback.imagecaption;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import com.google.android.accessibility.talkback.R;
import com.google.android.accessibility.talkback.dialog.BaseDialog;
import com.google.android.accessibility.talkback.imagecaption.ImageCaptionConstants.FeatureSwitchDialogResources;
import com.google.android.accessibility.utils.SharedPreferencesUtils;

/** A dialog to enable or disable an image captioning feature. */
public class FeatureSwitchDialog extends BaseDialog {
  private final SharedPreferences prefs;
  private final FeatureSwitchDialogResources resources;
  private final boolean isDeletable;
  private RadioGroup switches;

  public FeatureSwitchDialog(
      Context context, FeatureSwitchDialogResources resources, boolean isDeletable) {
    super(context, resources.titleRes, /* pipeline= */ null);
    prefs = SharedPreferencesUtils.getSharedPreferences(context);
    this.resources = resources;
    this.isDeletable = isDeletable;
    setPositiveButtonStringRes(R.string.switch_auto_image_caption_dialog_positive_button_text);
    if (isDeletable) {
      setNegativeButtonStringRes(R.string.switch_auto_image_caption_dialog_negative_button_text);
    }
  }

  @Override
  public String getMessageString() {
    return null;
  }

  @SuppressLint("InflateParams")
  @Override
  public View getCustomizedView() {
    LayoutInflater inflater = LayoutInflater.from(context);
    final ScrollView root =
        (ScrollView)
            inflater.inflate(R.layout.automatic_image_caption_switch_dialog, /* root= */ null);

    // Shows "Downloaded" if the library is downloadable and deletable.
    TextView subtitle = root.findViewById(R.id.automatic_image_caption_switch_dialog_subtitle);
    if (!isDeletable) {
      subtitle.setVisibility(View.GONE);
    }

    TextView message = root.findViewById(R.id.automatic_image_caption_switch_dialog_message);
    message.setText(resources.messageRes);

    switches = root.findViewById(R.id.automatic_image_caption_switch_dialog_radiogroup);
    switches.setOnCheckedChangeListener(
        (group, checkedId) -> {
          TextView disabledSubText =
              group.findViewById(
                  R.id.automatic_image_caption_switch_dialog_radiobutton_disabled_subtext);
          if (checkedId == R.id.automatic_image_caption_switch_dialog_radiobutton_disabled) {
            disabledSubText.setVisibility(View.VISIBLE);
          } else {
            disabledSubText.setVisibility(View.GONE);
          }
        });

    boolean isFeatureEnabled =
        SharedPreferencesUtils.getBooleanPref(
            prefs, context.getResources(), resources.switchKey, resources.switchDefaultValue);
    ((RadioButton)
            switches.findViewById(
                isFeatureEnabled
                    ? R.id.automatic_image_caption_switch_dialog_radiobutton_enabled
                    : R.id.automatic_image_caption_switch_dialog_radiobutton_disabled))
        .setChecked(true);
    return root;
  }

  @Override
  public void handleDialogClick(int buttonClicked) {
    if (switches == null) {
      return;
    }

    if (buttonClicked == DialogInterface.BUTTON_POSITIVE) {
      SharedPreferencesUtils.putBooleanPref(
          prefs,
          context.getResources(),
          resources.switchKey,
          switches.getCheckedRadioButtonId()
              == R.id.automatic_image_caption_switch_dialog_radiobutton_enabled);
    }
  }

  @Override
  public void handleDialogDismiss() {}
}
