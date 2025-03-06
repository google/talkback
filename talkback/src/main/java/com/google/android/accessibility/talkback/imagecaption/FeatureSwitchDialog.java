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

import static android.view.View.GONE;
import static com.google.android.accessibility.talkback.imagecaption.ImageCaptionConstants.FeatureSwitchDialogResources.IMAGE_DESCRIPTION_AICORE_SCOPE;
import static com.google.android.accessibility.talkback.imagecaption.ImageCaptionConstants.TYPE_DETAILED_DESCRIPTION;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.text.SpannableString;
import android.text.method.LinkMovementMethod;
import android.text.style.TextAppearanceSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import com.google.android.accessibility.talkback.FeatureFlagReader;
import com.google.android.accessibility.talkback.R;
import com.google.android.accessibility.talkback.actor.gemini.GeminiFunctionUtils;
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
    this(context, resources, isDeletable, -1);
  }

  public FeatureSwitchDialog(
      Context context,
      FeatureSwitchDialogResources resources,
      boolean isDeletable,
      int positiveButtonRes) {
    super(context, resources.titleRes, /* pipeline= */ null);
    prefs = SharedPreferencesUtils.getSharedPreferences(context);
    this.resources = resources;
    this.isDeletable = isDeletable;
    setPositiveButtonStringRes(
        positiveButtonRes == -1
            ? R.string.switch_auto_image_caption_dialog_positive_button_text
            : positiveButtonRes);
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
    if (resources.descriptionType == TYPE_DETAILED_DESCRIPTION) {
      final View root =
          inflater.inflate(R.layout.detailed_image_description_dialog, /* root= */ null);
      TextView textView = root.findViewById(R.id.ai_descriptiog_switch_dialog_message);
      if (textView != null) {
        String tos = context.getString(R.string.dialog_message_gen_ai_tos_link);
        String rawText = context.getString(resources.messageRes, tos);
        SpannableString text = new SpannableString(rawText);
        int spanIndexStart = rawText.indexOf(tos);
        if (spanIndexStart >= 0) {
          text.setSpan(
              GeminiFunctionUtils.createClickableSpanForGeminiTOS(context, this),
              spanIndexStart,
              rawText.length(),
              0);
        }
        textView.setText(text);
        textView.setMovementMethod(LinkMovementMethod.getInstance());
      }
      return root;
    }
    final ScrollView root =
        (ScrollView)
            inflater.inflate(R.layout.automatic_image_caption_switch_dialog, /* root= */ null);

    // Shows "Downloaded" if the library is downloadable and deletable.
    TextView subtitle = root.findViewById(R.id.automatic_image_caption_switch_dialog_subtitle);
    if (!isDeletable) {
      subtitle.setVisibility(GONE);
    }

    TextView message = root.findViewById(R.id.automatic_image_caption_switch_dialog_message);
    message.setText(resources.messageRes);

    switches = root.findViewById(R.id.automatic_image_caption_switch_dialog_radiogroup);

    // Hides "On anytime" button if the feature flag is off.
    if (!FeatureFlagReader.enableAutomaticCaptioningForAllImages(context)) {
      switches
          .findViewById(R.id.automatic_image_caption_switch_dialog_radiobutton_enabled)
          .setVisibility(GONE);
    }

    RadioButton enableAlways =
        switches.findViewById(R.id.automatic_image_caption_switch_dialog_radiobutton_enabled);
    RadioButton enableUnlabelledOnly =
        switches.findViewById(
            R.id.automatic_image_caption_switch_dialog_radiobutton_enabled_unlabelled_only);
    RadioButton disable =
        switches.findViewById(R.id.automatic_image_caption_switch_dialog_radiobutton_disabled);

    // Customize option texts for icon detection.
    if (resources == FeatureSwitchDialogResources.ICON_DETECTION) {
      enableAlways.setText(R.string.title_pref_enable_auto_icon_detection);
      enableUnlabelledOnly.setText(R.string.title_pref_enable_auto_icon_detection_unlabelled_only);
      disable.setText(R.string.title_pref_disable_auto_icon_detection);
    } else if (resources == IMAGE_DESCRIPTION_AICORE_SCOPE) {
      String subText =
          context.getString(R.string.subtitle_pref_enable_auto_image_caption_with_aicore);
      String rawText =
          context.getString(R.string.title_pref_enable_auto_image_caption_arg, subText);
      SpannableString text = new SpannableString(rawText);
      int spanIndexStart = rawText.indexOf(subText);
      if (spanIndexStart >= 0) {
        text.setSpan(
            new TextAppearanceSpan(context, com.google.android.accessibility.utils.R.style.A11yAlertDialogSubtitleStyle),
            spanIndexStart,
            rawText.length(),
            0);
      }

      enableAlways.setText(text);
    }

    switch (ImageCaptionUtils.getAutomaticImageCaptioningState(context, prefs, resources)) {
      case ON_ALL_IMAGES:
        enableAlways.setChecked(true);
        break;
      case ON_UNLABELLED_ONLY:
        enableUnlabelledOnly.setChecked(true);
        break;
      case OFF:
        disable.setChecked(true);
        break;
    }
    return root;
  }

  @Override
  public void handleDialogClick(int buttonClicked) {
    if (switches == null) {
      return;
    }

    if (buttonClicked == DialogInterface.BUTTON_POSITIVE) {
      int radioBtnId = switches.getCheckedRadioButtonId();
      if (radioBtnId == R.id.automatic_image_caption_switch_dialog_radiobutton_disabled) {
        prefs
            .edit()
            .putBoolean(context.getString(resources.switchKey), false)
            .remove(context.getString(resources.switchOnUnlabelledOnlyKey))
            .apply();
      } else {
        prefs
            .edit()
            .putBoolean(context.getString(resources.switchKey), true)
            .putBoolean(
                context.getString(resources.switchOnUnlabelledOnlyKey),
                radioBtnId
                    == R.id
                        .automatic_image_caption_switch_dialog_radiobutton_enabled_unlabelled_only)
            .apply();
      }
    }
  }

  @Override
  public void handleDialogDismiss() {}
}
