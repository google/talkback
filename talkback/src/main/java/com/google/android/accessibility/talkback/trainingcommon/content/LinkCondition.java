/*
 * Copyright (C) 2024 Google Inc.
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
import androidx.annotation.Nullable;
import com.google.android.accessibility.talkback.trainingcommon.PageConfig.PageAndContentPredicate;
import com.google.android.accessibility.talkback.trainingcommon.TrainingIpcClient.ServiceData;
import com.google.android.accessibility.utils.Consumer;

/**
 * Includes two multiline texts and an icon. Links to some pages when matched the {@link
 * LinkCondition#condition}, otherwise execute the {@link LinkCondition#conditionFailedConsumer}.
 */
public class LinkCondition extends Link {
  private final PageAndContentPredicate condition;
  @Nullable private final Consumer<Context> conditionFailedConsumer;

  public LinkCondition(
      int textResId,
      int subtextResId,
      int srcResId,
      PageAndContentPredicate condition,
      @Nullable Consumer<Context> conditionFailedConsumer,
      int[] firstPageCandidatesInSectionNameResIds) {
    super(textResId, subtextResId, srcResId, firstPageCandidatesInSectionNameResIds);
    this.condition = condition;
    this.conditionFailedConsumer = conditionFailedConsumer;
  }

  @Override
  protected OnClickListener createOnClickListener(Context context, ServiceData data) {
    OnClickListener parentOnClickListener = super.createOnClickListener(context, data);
    return v -> {
      if (!condition.test(data)) {
        if (conditionFailedConsumer != null) {
          conditionFailedConsumer.accept(context);
        }
      } else {
        // Acts as parent if match the condition.
        parentOnClickListener.onClick(v);
      }
    };
  }
}
