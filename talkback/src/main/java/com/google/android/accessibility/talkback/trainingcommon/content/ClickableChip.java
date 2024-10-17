/*
 * Copyright (C) 2020 Google Inc.
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

package com.google.android.accessibility.talkback.trainingcommon.content;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.DrawableRes;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import com.google.android.accessibility.talkback.R;
import com.google.android.accessibility.talkback.trainingcommon.TrainingIpcClient.ServiceData;

/** A clickable {@link PageContentConfig} with chip style contains text, subtext, and an icon. */
public abstract class ClickableChip extends PageContentConfig {

  @StringRes private final int textResId;
  @StringRes private final int subtextResId;
  @DrawableRes private final int srcResId;

  @Nullable protected OnClickListener onClickListener;

  public ClickableChip(
      @StringRes int textResId,
      @StringRes int subtextResId,
      @DrawableRes int srcResId,
      @Nullable OnClickListener onClickListener) {
    this.textResId = textResId;
    this.subtextResId = subtextResId;
    this.srcResId = srcResId;
    this.onClickListener = onClickListener;
  }

  @Override
  public View createView(
      LayoutInflater inflater, ViewGroup container, Context context, ServiceData data) {
    final View view =
        inflater.inflate(R.layout.training_link, container, /* attachToRoot= */ false);
    final ImageView icon = view.findViewById(R.id.training_chip_icon);
    final TextView text = view.findViewById(R.id.training_chip_text);
    final TextView subtext = view.findViewById(R.id.training_chip_subtext);
    if (srcResId != UNKNOWN_RESOURCE_ID) {
      icon.setImageResource(srcResId);
    }
    text.setText(textResId);
    if (subtextResId != UNKNOWN_RESOURCE_ID) {
      subtext.setText(subtextResId);
    } else {
      subtext.setVisibility(View.GONE);
    }

    final LinearLayout chipView = view.findViewById(R.id.training_chip);
    chipView.setOnClickListener(createOnClickListener(context, data));
    return view;
  }

  protected abstract OnClickListener createOnClickListener(Context context, ServiceData data);
}
