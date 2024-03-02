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
import androidx.annotation.Nullable;
import com.google.android.accessibility.talkback.trainingcommon.PageConfig.PageContentPredicate;
import com.google.android.accessibility.talkback.trainingcommon.TrainingIpcClient.ServiceData;

// TODO Creates Contents and set text/condition by builder.
/** Pages are composed of contents. */
public abstract class PageContentConfig {

  public static final int UNKNOWN_RESOURCE_ID = -1;

  @Nullable private PageContentPredicate predicate;

  /** Shows this instead if the {@link #predicate} is not matched. */
  @Nullable private PageContentConfig substitute;

  public void setShowingPredicate(PageContentPredicate predicate) {
    setShowingPredicate(predicate, /* substitute= */ null);
  }

  public void setShowingPredicate(PageContentPredicate predicate, PageContentConfig substitute) {
    this.predicate = predicate;
    this.substitute = substitute;
  }

  @Nullable
  public PageContentConfig getSubstitute() {
    return substitute;
  }

  /** Returns true if the condition is right or no condition. */
  public boolean isNeedToShow(ServiceData data) {
    return predicate == null || predicate.test(data);
  }

  /** Creates a view for this component. */
  public abstract View createView(
      LayoutInflater inflater, ViewGroup container, Context context, ServiceData data);
}
