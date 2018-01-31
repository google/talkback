/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.google.android.accessibility.talkback.focusmanagement.record;

import android.support.annotation.IntDef;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/** The class contains extra information about accessibility focus action. */
public class FocusActionInfo {
  /** Type of the source focus action. */
  @IntDef({UNKNOWN, MANUAL_SCROLL, TOUCH_EXPLORATION})
  @Retention(RetentionPolicy.SOURCE)
  public @interface SourceAction {}

  public static final int UNKNOWN = 0;
  public static final int MANUAL_SCROLL = 1;
  public static final int TOUCH_EXPLORATION = 2;

  @SourceAction public final int sourceAction;
  public final boolean isFromRefocusAction;

  /** Builds {@link FocusActionInfo}. */
  public static class Builder {
    @SourceAction private int mSourceAction = UNKNOWN;
    private boolean mIsFromRefocusAction = false;

    public FocusActionInfo build() {
      return new FocusActionInfo(this);
    }

    public Builder setSourceAction(@SourceAction int sourceAction) {
      mSourceAction = sourceAction;
      return this;
    }

    public Builder setIsFromRefocusAction(boolean isFromRefocusAction) {
      mIsFromRefocusAction = isFromRefocusAction;
      return this;
    }
  }

  private FocusActionInfo(Builder builder) {
    sourceAction = builder.mSourceAction;
    isFromRefocusAction = builder.mIsFromRefocusAction;
  }
}
