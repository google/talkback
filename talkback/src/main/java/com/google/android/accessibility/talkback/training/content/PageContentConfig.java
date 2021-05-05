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
import androidx.annotation.Nullable;
import com.google.android.accessibility.talkback.training.PageConfig.PageContentPredicate;
import java.io.Serializable;

// TODO Creates Contents and set text/condition by builder.
/** Pages are composed of contents. */
public abstract class PageContentConfig implements Serializable {

  public static final int UNKNOWN_RESOURCE_ID = -1;

  @Nullable private PageContentPredicate predicate;

  public void setShowingPredicate(PageContentPredicate predicate) {
    this.predicate = predicate;
  }

  /** Returns true if the condition is right or no condition. */
  public boolean isNeedToShow(Context context) {
    return predicate == null || predicate.test(context);
  }

  /** Creates a view for this component. */
  public abstract View createView(LayoutInflater inflater, ViewGroup container, Context context);
}
