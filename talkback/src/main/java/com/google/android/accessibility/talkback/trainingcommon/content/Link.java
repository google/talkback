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
import android.view.View.OnClickListener;
import androidx.annotation.DrawableRes;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import com.google.android.accessibility.talkback.trainingcommon.TrainingIpcClient.ServiceData;
import com.google.android.libraries.accessibility.utils.log.LogUtils;

/** Includes two multiline texts and an icon. Links to some pages when view is clicked. */
public class Link extends ClickableChip {

  private static final String TAG = "Link";

  /** Handles the clickable link action. */
  public interface LinkHandler {
    /**
     * Links to the first page in a section and goes back to the current page after finishing
     * reading the last page in the section.
     */
    void handle(@StringRes int... firstPageCandidatesInSectionNameResIds);
  }

  @Nullable private transient LinkHandler linkHandler;
  @StringRes private final int[] firstPageCandidatesInSectionNameResIds;

  public Link(
      @StringRes int textResId,
      @StringRes int subtextResId,
      @DrawableRes int srcResId,
      int[] firstPageCandidatesInSectionNameResIds) {
    super(textResId, subtextResId, srcResId, null);
    this.firstPageCandidatesInSectionNameResIds = firstPageCandidatesInSectionNameResIds;
  }

  @Override
  protected OnClickListener createOnClickListener(Context context, ServiceData data) {
    return clickedView -> {
      if (linkHandler == null) {
        LogUtils.e(TAG, "No linkHandler. Invoking setLinkHandler() before using it.");
      } else {
        linkHandler.handle(firstPageCandidatesInSectionNameResIds);
      }
    };
  }

  public void setLinkHandler(LinkHandler linkHandler) {
    this.linkHandler = linkHandler;
  }
}
