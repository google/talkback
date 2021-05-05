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

package com.google.android.accessibility.talkback.training;

import static android.view.View.IMPORTANT_FOR_ACCESSIBILITY_AUTO;
import static android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS;

import android.os.Bundle;
import androidx.fragment.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import androidx.annotation.Nullable;
import com.google.android.accessibility.talkback.R;
import com.google.android.accessibility.talkback.training.content.ClickableContent;
import com.google.android.accessibility.talkback.training.content.ClickableContent.LinkHandler;
import com.google.android.accessibility.talkback.training.content.PageContentConfig;
import com.google.android.accessibility.talkback.training.content.PageNumber;
import com.google.android.accessibility.talkback.training.content.Title;
import com.google.android.libraries.accessibility.utils.log.LogUtils;
import java.util.List;

/** A fragment to show one of the training page that parsers from {@link #EXTRA_PAGE} argument. */
public class TrainingFragment extends Fragment {

  private static final String TAG = "TrainingFragment";
  public static final String EXTRA_PAGE = "page";
  public static final String EXTRA_PAGE_NUMBER = "page_number";
  public static final String EXTRA_TOTAL_NUMBER = "total_number";

  @Nullable private PageConfig page;
  private LinearLayout pageLayout;
  private LinkHandler linkHandler;

  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedState) {
    final View view = inflater.inflate(R.layout.training_fragment_name, container, false);
    pageLayout = view.findViewById(R.id.training_page);
    page = (PageConfig) getArguments().get(EXTRA_PAGE);

    if (page == null) {
      LogUtils.e(TAG, "Cannot create view because no page.");
      return view;
    }
    addView(inflater, container);
    pageLayout.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_AUTO);
    return view;
  }

  public void setLinkHandler(LinkHandler linkHandler) {
    this.linkHandler = linkHandler;
  }

  /** Creates and adds all contents to the fragment. */
  private void addView(LayoutInflater inflater, ViewGroup container) {
    if (page == null) {
      LogUtils.e(TAG, "Cannot add view to fragment because no page.");
    }

    // Sets title.
    addView(new Title(page.getPageName()).createView(inflater, container, getContext()));

    // Sets page number.
    int pageNumber = getArguments().getInt(EXTRA_PAGE_NUMBER);
    int totalNumber = getArguments().getInt(EXTRA_TOTAL_NUMBER);
    if (pageNumber > 0 && totalNumber > 0) {
      addView(
          new PageNumber(pageNumber, totalNumber).createView(inflater, container, getContext()));
    }

    List<PageContentConfig> contents = page.getContents();
    for (PageContentConfig content : contents) {
      if (content.isNeedToShow(getContext())) {
        // For the navigation contents, like Link and button.
        if (content instanceof ClickableContent) {
          ((ClickableContent) content).setLinkHandler(linkHandler);
        }
        addView(content.createView(inflater, container, getContext()));
      }
    }
  }

  private void addView(View view) {
    if (page == null) {
      LogUtils.e(TAG, "Cannot add view to fragment because no page.");
    }

    if (page.isOnlyOneFocus()) {
      // Entire page is spoken continuously. The focus is on the first child (pageLayout) of
      // ViewPager, so the content view and its descendant views are not important for
      // accessibility.
      view.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
    }
    pageLayout.addView(view);
  }
}
