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
import android.view.ViewGroup;
import android.view.ViewGroup.MarginLayoutParams;
import android.widget.TextView;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import com.google.android.accessibility.talkback.R;
import com.google.android.accessibility.talkback.trainingcommon.PageConfig;
import com.google.android.accessibility.talkback.trainingcommon.TrainingIpcClient.ServiceData;
import com.google.android.accessibility.utils.DisplayUtils;

/** A title shows at the beginning in general. */
public class Title extends PageContentConfig {

  /** Can be overridden by {@link #titleString}. */
  @StringRes private final int titleResId;

  /** Has precedence over {@link #titleResId}. */
  @Nullable private final String titleString;

  /** The dp value for extra margin top. */
  private final int extraMarginTopDp;

  /** The flag to determine whether the horizontal margin would be reset. */
  private final boolean clearTitleHorizontalMargin;

  public Title(PageConfig pageConfig) {
    titleResId = pageConfig.getPageNameResId();
    titleString = pageConfig.getPageNameString();
    extraMarginTopDp = pageConfig.getExtraTitleMarginTop();
    clearTitleHorizontalMargin = pageConfig.clearTitleHorizontalMargin();
  }

  @Override
  public View createView(
      LayoutInflater inflater, ViewGroup container, Context context, ServiceData data) {
    final View view = inflater.inflate(R.layout.training_title, container, false);
    final TextView title = view.findViewById(R.id.training_title);
    if (titleString != null) {
      title.setText(titleString);
    } else {
      title.setText(titleResId);
    }

    ViewGroup.MarginLayoutParams layoutParams = (MarginLayoutParams) title.getLayoutParams();
    if (extraMarginTopDp > 0) {
      layoutParams.topMargin += DisplayUtils.dpToPx(context, extraMarginTopDp);
    }
    if (clearTitleHorizontalMargin) {
      layoutParams.leftMargin = 0;
      layoutParams.rightMargin = 0;
    }
    title.setLayoutParams(layoutParams);

    return view;
  }
}
