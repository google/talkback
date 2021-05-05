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

package com.google.android.accessibility.talkback.training.content;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.DrawableRes;
import androidx.annotation.StringRes;
import com.google.android.accessibility.talkback.R;
import com.google.android.libraries.accessibility.utils.log.LogUtils;

/** Includes two multiline texts and an icon. Links to some pages when view is clicked. */
public class Link extends ClickableContent {

  private static final String TAG = "Link";
  @StringRes private final int textResId;
  @StringRes private final int subtextResId;
  @DrawableRes private final int srcResId;
  @StringRes private final int firstPageInSectionNameResId;

  public Link(
      @StringRes int textResId,
      @StringRes int subtextResId,
      @DrawableRes int srcResId,
      int firstPageInSectionNameResId) {
    this.textResId = textResId;
    this.subtextResId = subtextResId;
    this.srcResId = srcResId;
    this.firstPageInSectionNameResId = firstPageInSectionNameResId;
  }

  @Override
  public View createView(LayoutInflater inflater, ViewGroup container, Context context) {
    final View view =
        inflater.inflate(R.layout.training_link, container, /* attachToRoot= */ false);
    final ImageView icon = view.findViewById(R.id.training_link_icon);
    final TextView text = view.findViewById(R.id.training_link_text);
    final TextView subtext = view.findViewById(R.id.training_link_subtext);
    if (srcResId != UNKNOWN_RESOURCE_ID) {
      icon.setImageResource(srcResId);
    }
    text.setText(textResId);
    if (subtextResId != UNKNOWN_RESOURCE_ID) {
      subtext.setText(subtextResId);
    } else {
      subtext.setVisibility(View.GONE);
    }

    // Jumps to the start page when the view is clicked.
    final LinearLayout linkedView = view.findViewById(R.id.training_link);
    linkedView.setOnClickListener(
        clickedView -> {
          if (linkHandler == null) {
            LogUtils.e(TAG, "No linkHandler. Invoking setLinkHandler() before using it.");
          }
          linkHandler.handle(firstPageInSectionNameResId);
        });
    return view;
  }
}
