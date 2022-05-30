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
package com.google.android.accessibility.talkback.utils;

import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;
import android.view.View;
import androidx.wear.widget.SwipeDismissFrameLayout;

/** The class handles custom swipe action of view for wear. */
public final class TalkbackCustomViewSwipeAction {

  /**
   * Customizes swipe action.
   *
   * @param activity FragmentActivity contains fragment
   * @param view View of Fragment
   */
  public static View wrapWithSwipeHandler(FragmentActivity activity, View view) {
    if ((activity == null) || (view == null)) {
      return view;
    }
    // This callback is used for wear only and handles swipe event.
    SwipeDismissFrameLayout.Callback swipeCallback =
        new SwipeDismissFrameLayout.Callback() {
          @Override
          public void onDismissed(SwipeDismissFrameLayout layout) {
            FragmentManager fragmentManager = activity.getSupportFragmentManager();

            if (fragmentManager.getBackStackEntryCount() > 1) {
              fragmentManager.popBackStackImmediate();
            } else {
              activity.finish();
            }
          }
        };

    SwipeDismissFrameLayout swipeLayout = new SwipeDismissFrameLayout(activity);
    swipeLayout.addView(view);
    swipeLayout.addCallback(swipeCallback);

    return swipeLayout;
  }

  private TalkbackCustomViewSwipeAction() {}
}
