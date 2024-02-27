/*
 * Copyright 2021 Google Inc.
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
package com.google.android.accessibility.utils.material;

import androidx.fragment.app.FragmentActivity;
import android.view.View;
import androidx.annotation.Nullable;

/**
 * Wraps the view in {@link SwipeDismissFrameLayout} with given {@link SwipeDismissListener}. It has
 * no effect on phone.
 */
public final class WrapSwipeDismissLayoutHelper {

  /**
   * Wraps the view in {@link SwipeDismissFrameLayout} with given {@link SwipeDismissListener}. It
   * has no effect on the phone.
   *
   * @param activity the activity containing fragment
   * @param view the root view of fragment
   * @param swipeDismissListener the callback invoked when the swipe gesture is completed
   */
  public static View wrapSwipeDismissLayout(
      FragmentActivity activity, View view, @Nullable SwipeDismissListener swipeDismissListener) {
    // Does nothing for phone case.
    return view;
  }

  private WrapSwipeDismissLayoutHelper() {}
}
