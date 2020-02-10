/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.google.android.accessibility.talkback.tutorial.exercise;

import android.view.LayoutInflater;
import android.view.View;
import android.view.View.AccessibilityDelegate;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityEvent;
import android.widget.TextView;
import com.google.android.accessibility.talkback.R;
import com.google.android.accessibility.talkback.tutorial.GridView;
import com.google.android.apps.common.proguard.UsedByReflection;

/** Provides common functionality for lessons that have an GridView to explore */
@UsedByReflection("tutorial.json")
public class GridItemExercise extends Exercise implements View.OnClickListener {

  protected View mView;
  private GridView gridView;

  @Override
  public View getContentView(final LayoutInflater inflater, ViewGroup parent) {
    gridView =
        new GridView(
            inflater.getContext(),
            new GridView.ItemProvider() {
              @Override
              public View getView(ViewGroup parent, final int index) {
                View view = inflater.inflate(R.layout.tutorial_content_grid_item, parent, false);
                TextView title = (TextView) view.findViewById(R.id.title);
                String text =
                    inflater.getContext().getString(R.string.tutorial_template_item, index + 1);
                title.setText(text);
                view.setContentDescription(text);
                view.setOnClickListener(GridItemExercise.this);
                view.setAccessibilityDelegate(new GridItemAccessibilityDelegate(index + 1));
                return view;
              }
            });
    int horizontalPadding =
        inflater
            .getContext()
            .getResources()
            .getDimensionPixelSize(R.dimen.tutorial_grid_horizontal_offset);
    gridView.setPadding(horizontalPadding, 0, horizontalPadding, 0);
    mView = gridView;
    return mView;
  }

  @Override
  public void onClick(View view) {
    clearSelection();
    view.findViewById(R.id.circle).setSelected(true);
  }

  private void clearSelection() {
    int childrenCount = gridView.getChildCount();
    for (int i = 0; i < childrenCount; i++) {
      View view = gridView.getChildAt(i);
      if (view != null) {
        view.findViewById(R.id.circle).setSelected(false);
      }
    }
  }

  protected View getView() {
    return mView;
  }

  protected void onAccessibilityFocused(int index) {}

  protected void onAccessibilityClicked(int index) {}

  @Override
  public int getEventTypes() {
    return 0;
  }

  private class GridItemAccessibilityDelegate extends AccessibilityDelegate {
    private final int index;

    /** @param index the index the user sees; use 1-based indexing, not 0-based indexing */
    GridItemAccessibilityDelegate(int index) {
      this.index = index;
    }

    @Override
    public void sendAccessibilityEvent(View host, int eventType) {
      super.sendAccessibilityEvent(host, eventType);

      if (eventType == AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED) {
        onAccessibilityFocused(index);
      } else if (eventType == AccessibilityEvent.TYPE_VIEW_CLICKED) {
        onAccessibilityClicked(index);
      }
    }
  }
}
