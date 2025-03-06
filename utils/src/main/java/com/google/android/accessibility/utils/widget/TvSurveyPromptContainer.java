/*
 * Copyright (C) 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.android.accessibility.utils.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewParent;
import android.view.ViewTreeObserver;
import android.view.ViewTreeObserver.OnGlobalLayoutListener;
import android.widget.FrameLayout;
import com.google.android.accessibility.utils.R;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import org.checkerframework.checker.nullness.qual.Nullable;

/** Container for the HaTS survey on TV which does some custom focus handling. */
public class TvSurveyPromptContainer extends FrameLayout {
  private boolean hasSetFocus = false;

  public TvSurveyPromptContainer(Context context, AttributeSet attrs) {
    super(context, attrs);

    // The assumption in the following is, that when the survey is opened, one or more child views
    // are added and when it is closed, all children are removed again. If no survey is opened, no
    // child will be added.
    setOnHierarchyChangeListener(
        new OnHierarchyChangeListener() {
          @Override
          public void onChildViewAdded(View parent, View child) {
            if (hasSetFocus) {
              return;
            }
            if (trySettingFocus(child)) {
              return;
            }
            ViewTreeObserver viewTreeObserver = child.getViewTreeObserver();
            if (viewTreeObserver.isAlive()) {
              viewTreeObserver.addOnGlobalLayoutListener(
                  new OnGlobalLayoutListener() {
                    @Override
                    public void onGlobalLayout() {
                      trySettingFocus(child);
                      if (hasSetFocus && viewTreeObserver.isAlive()) {
                        viewTreeObserver.removeOnGlobalLayoutListener(this);
                      }
                    }
                  });
            }
          }

          @Override
          public void onChildViewRemoved(View parent, View child) {
            if (hasSetFocus && TvSurveyPromptContainer.this.getChildCount() == 0) {
              getRootView()
                  .findViewById(R.id.preference_root)
                  .setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_AUTO);
              getRootView()
                  .findViewById(androidx.appcompat.R.id.action_bar_container)
                  .setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_AUTO);
            }
          }
        });
  }

  /**
   * Sets input focus and accessibility focus to the first focusable child of the survey and
   * prevents accessibility focus from going to background.
   */
  @CanIgnoreReturnValue
  private boolean trySettingFocus(View view) {
    if (hasSetFocus || view.getWidth() == 0) {
      // If this view has a width of zero it probably means that it has not been drawn yet.
      return false;
    }
    if (view.requestFocus()) {
      hasSetFocus = true;
      getRootView()
          .findViewById(R.id.preference_root)
          .setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
      getRootView()
          .findViewById(androidx.appcompat.R.id.action_bar_container)
          .setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
    }
    return hasSetFocus;
  }

  /** Prevents input focus from going to background. */
  @Override
  public @Nullable View focusSearch(View focused, int direction) {
    View result = super.focusSearch(focused, direction);
    if (result == null) {
      return null;
    }

    ViewParent parent = result.getParent();
    while (parent != null) {
      if (parent == this) {
        return result;
      }
      parent = parent.getParent();
    }

    return null;
  }
}
