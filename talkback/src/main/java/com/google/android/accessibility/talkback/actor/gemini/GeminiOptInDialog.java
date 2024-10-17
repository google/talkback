/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.google.android.accessibility.talkback.actor.gemini;

import android.annotation.SuppressLint;
import android.content.Context;
import android.text.SpannableString;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.annotation.StringRes;
import com.google.android.accessibility.talkback.R;
import com.google.android.accessibility.talkback.dialog.BaseDialog;

/** A dialog to accept or reject to opt-in detailed image description. */
public abstract class GeminiOptInDialog extends BaseDialog {

  private static final String TAG = "GeminiOptInDialog";
  private final int dialogMessageResId;
  private final boolean showSummary;

  /** Gemini is requested in what situation. The field is used by {@link #getCustomizedView()}. */
  public GeminiOptInDialog(
      Context context,
      @StringRes int titleResId,
      boolean showSummary,
      @StringRes int dialogMessageResId,
      @StringRes int positiveButtonResId,
      @StringRes int negativeButtonResId) {
    super(context, titleResId, /* pipeline= */ null);
    setPositiveButtonStringRes(positiveButtonResId);
    if (negativeButtonResId != -1) {
      setNegativeButtonStringRes(negativeButtonResId);
    }
    this.dialogMessageResId = dialogMessageResId;
    this.showSummary = showSummary;
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
        (ScrollView) inflater.inflate(R.layout.detail_image_description_promotion_dialog, null);
    if (!showSummary) {
      TextView summary = root.findViewById(R.id.ai_descriptiog_promotion_dialog_summary);
      summary.setVisibility(View.GONE);
    }

    TextView textView = root.findViewById(R.id.ai_descriptiog_promotion_dialog_message);
    if (dialogMessageResId != -1 && textView != null) {
      String tos = context.getString(R.string.dialog_message_gen_ai_tos_link);
      String rawText = context.getString(dialogMessageResId, tos);
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
    }
    return root;
  }
}
