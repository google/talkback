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

package com.google.android.accessibility.talkback.trainingcommon.content;

import static androidx.core.content.res.ResourcesCompat.ID_NULL;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import androidx.annotation.DrawableRes;
import androidx.annotation.StringRes;
import com.google.android.accessibility.talkback.R;
import com.google.android.accessibility.talkback.trainingcommon.TrainingIpcClient.ServiceData;

/** An image and its content description. */
public class Image extends PageContentConfig {

  @DrawableRes private final int imageDrawableId;
  @StringRes private final int contentDescriptionResId;

  public Image(@DrawableRes int imageDrawableId, @StringRes int contentDescriptionResId) {
    this.imageDrawableId = imageDrawableId;
    this.contentDescriptionResId = contentDescriptionResId;
  }

  @Override
  public View createView(
      LayoutInflater inflater, ViewGroup container, Context context, ServiceData data) {
    final View view = inflater.inflate(R.layout.training_image, container, false);
    final ImageView image = view.findViewById(R.id.image);
    image.setImageResource(imageDrawableId);
    if (contentDescriptionResId != ID_NULL) {
      image.setContentDescription(context.getString(contentDescriptionResId));
    }
    return view;
  }
}
