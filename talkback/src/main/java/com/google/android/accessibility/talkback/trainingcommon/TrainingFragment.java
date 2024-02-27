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

package com.google.android.accessibility.talkback.trainingcommon;

import static android.view.View.IMPORTANT_FOR_ACCESSIBILITY_AUTO;
import static android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS;
import static com.google.android.accessibility.talkback.trainingcommon.NavigationButtonBar.BUTTON_TYPE_NEXT;
import static com.google.android.accessibility.talkback.trainingcommon.PageConfig.PageId.PAGE_ID_UPDATE_WELCOME;

import android.content.Context;
import android.os.Bundle;
import android.os.Message;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import androidx.annotation.Nullable;
import androidx.core.view.AccessibilityDelegateCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import com.google.android.accessibility.talkback.R;
import com.google.android.accessibility.talkback.TalkBackMetricStore;
import com.google.android.accessibility.talkback.trainingcommon.PageConfig.PageId;
import com.google.android.accessibility.talkback.trainingcommon.TrainingIpcClient.ServiceData;
import com.google.android.accessibility.talkback.trainingcommon.content.ClickableContent;
import com.google.android.accessibility.talkback.trainingcommon.content.ClickableContent.LinkHandler;
import com.google.android.accessibility.talkback.trainingcommon.content.ExitBanner;
import com.google.android.accessibility.talkback.trainingcommon.content.PageButton;
import com.google.android.accessibility.talkback.trainingcommon.content.PageContentConfig;
import com.google.android.accessibility.talkback.trainingcommon.content.PageNumber;
import com.google.android.accessibility.talkback.trainingcommon.content.Title;
import com.google.android.accessibility.utils.FormFactorUtils;
import com.google.android.libraries.accessibility.utils.log.LogUtils;
import com.google.common.base.VerifyException;
import com.google.common.collect.ImmutableList;
import java.util.function.Function;

/** A fragment to show one of the training page that parsers from {@link #EXTRA_PAGE} argument. */
public class TrainingFragment extends Fragment {

  private static final String TAG = "TrainingFragment";
  public static final String EXTRA_PAGE = "page";
  public static final String EXTRA_PAGE_NUMBER = "page_number";
  public static final String EXTRA_TOTAL_NUMBER = "total_number";
  public static final String EXTRA_VENDOR_PAGE_INDEX = "vendor_page_index";

  @Nullable private PageConfig page;
  private LinearLayout pageLayout;
  private LinearLayout pageBannerLayout;
  private LinkHandler linkHandler;
  private ServiceData data;

  @Nullable NavigationButtonBar navigationButtonBar;

  // We only have supplier if this page has navigation bar and it is belong to some form factors.
  @Nullable private Function<Context, NavigationButtonBar> navigationButtonBarSupplier;

  private TalkBackMetricStore metricStore;

  private final FormFactorUtils formFactorUtils = FormFactorUtils.getInstance();

  void setNavigationButtonBarSupplier(
      Function<Context, NavigationButtonBar> navigationButtonBarSupplier) {
    this.navigationButtonBarSupplier = navigationButtonBarSupplier;
  }

  void setMetricStore(TalkBackMetricStore metricStore) {
    this.metricStore = metricStore;
  }

  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedState) {
    View view = inflater.inflate(R.layout.training_fragment_name, container, false);
    pageLayout = view.findViewById(R.id.training_page);
    pageBannerLayout = view.findViewById(R.id.training_page_banner);

    @Nullable Bundle arguments = getArguments();
    if (arguments == null) {
      LogUtils.e(TAG, "Cannot create view because fragment was created without arguments.");
      return view;
    }

    @Nullable PageId pageId = (PageId) arguments.get(EXTRA_PAGE);

    if (pageId == null) {
      LogUtils.e(TAG, "Cannot create view because no page ID.");
      return view;
    }

    FragmentActivity activity = getActivity();
    if (activity == null) {
      LogUtils.e(TAG, "Cannot create view because fragment is not attached to activity.");
      return view;
    }

    // Remind there is a potential issue: The supplier may be null when the fragment is restored
    // while the activity is recreated. Ideally we should reassign it when the window is recreated.
    // However we don't need to worried about it now because we don't recreate the trainingActivity
    // in configuration changes.
    ViewGroup navBarContainer = view.findViewById(R.id.nav_container);
    if (navBarContainer != null && navigationButtonBarSupplier != null) {
      navigationButtonBar = navigationButtonBarSupplier.apply(view.getContext());
      navBarContainer.addView(navigationButtonBar);
    }

    int vendorPageIndex = arguments.getInt(EXTRA_VENDOR_PAGE_INDEX, PageConfig.UNKNOWN_PAGE_INDEX);
    page = PageConfig.getPage(pageId, activity, vendorPageIndex);

    if (page == null) {
      LogUtils.e(TAG, "Cannot create view because unknown PageId. [%s]", pageId.name());
      return view;
    }

    addView(inflater, container);
    pageLayout.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_AUTO);

    // Make initial focus on training page layout but not on training banner.
    if (page.isOnlyOneFocus() && pageBannerLayout != null && pageBannerLayout.getChildCount() > 0) {
      setTrainingPageInitialFocus(view.findViewById(R.id.training_page_scroll));
    }

    if (formFactorUtils.isAndroidWear()) {
      // Setting a pane title for fragment will trigger TYPE_WINDOW_STATE_CHANGED. It will drop an
      // immediate TYPE_VIEW_FOCUS event sent after the last window state changed event. As a
      // result, the dropped TYPE_VIEW_FOCUS event will not set a11y focus on the fragment.
      ViewCompat.setAccessibilityPaneTitle(view, getString(page.getPageNameResId()));

      // Support rotary input.
      view.requestFocus();

      // The page supports to swipe right with 2-fingers to go back to the previous page.
      if (getActivity() instanceof TrainingActivity) {
        view = ((TrainingActivity) getActivity()).wrapWithSwipeHandler(view);
      }
    }

    // On TV, we apply the TalkBack focus always first on the text, then after clicking the center
    // button we move the input focus to the navigation buttons. Note that on TV, the TalkBack focus
    // automatically follows the input focus.
    if (formFactorUtils.isAndroidTv()) {
      view.findViewById(R.id.training_page_wrapper)
          .setOnClickListener(
              clickedView -> {
                ((TrainingActivity) getActivity()).moveInputFocusToNavButtons();
              });

      pageLayout.requestFocus();
    }
    return view;
  }

  public void setLinkHandler(LinkHandler linkHandler) {
    this.linkHandler = linkHandler;
  }

  public void setData(ServiceData data) {
    this.data = data;
  }

  public void moveInputFocusToNextButton() {
    if (navigationButtonBar != null) {
      navigationButtonBar.requestFocusOnButton(BUTTON_TYPE_NEXT);
    }
  }

  /** Creates and adds all contents to the fragment. */
  private void addView(LayoutInflater inflater, ViewGroup container) {
    if (page == null) {
      LogUtils.e(TAG, "Cannot add view to fragment because no page.");
      return;
    }

    // Sets title.
    Title title = new Title(page);
    View titleView = title.createView(inflater, container, getContext(), data);
    addView(titleView);
    // Update welcome page title has the initial focus.
    if (page.getPageId() == PAGE_ID_UPDATE_WELCOME) {
      setTrainingPageInitialFocus(titleView);
    }

    // Sets page number.
    int pageNumber = getArguments().getInt(EXTRA_PAGE_NUMBER);
    int totalNumber = getArguments().getInt(EXTRA_TOTAL_NUMBER);
    if (pageNumber > 0 && totalNumber > 0) {
      addView(
          new PageNumber(pageNumber, totalNumber)
              .createView(inflater, container, getContext(), data));
    }

    ImmutableList<PageContentConfig> contents = page.getContents();
    for (PageContentConfig content : contents) {
      addView(content, inflater, container);
    }
  }

  private void addView(PageContentConfig content, LayoutInflater inflater, ViewGroup container) {
    if (data != null && content.isNeedToShow(data)) {
      // Add Page banner if needed.
      if (content instanceof ExitBanner && pageBannerLayout != null) {
        View view = content.createView(inflater, container, getContext(), data);
        ((ExitBanner) content)
            .setRequestDisableTalkBack(
                () -> {
                  try {
                    ((TrainingActivity) getActivity()).onRequestDisableTalkBack();
                  } catch (InterruptedException e) {
                    throw new VerifyException(e);
                  }
                });
        ((ExitBanner) content).setMetricStore(metricStore);
        pageBannerLayout.addView(view);
        return;
      }

      // Sets a click listener to send message to TalkBack for a PageButton.
      if (content instanceof PageButton) {
        View view = content.createView(inflater, container, getContext(), data);
        @Nullable Button button = ((PageButton) content).getButton();
        @Nullable Message message = ((PageButton) content).getMessage();
        if (button != null && message != null) {
          button.setOnClickListener(
              v -> {
                LogUtils.v(TAG, "Sends a message to service. what=%d", message.what);
                ((TrainingActivity) getActivity()).checkAndSendMessageToService(message);
              });
        }
        addView(view);
        return;
      }

      // For the navigation contents, like Link and button.
      if (content instanceof ClickableContent) {
        ((ClickableContent) content).setLinkHandler(linkHandler);
      }
      addView(content.createView(inflater, container, getContext(), data));
    } else {
      PageContentConfig substitute = content.getSubstitute();
      if (substitute == null) {
        return;
      }
      addView(substitute, inflater, container);
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

  private void setTrainingPageInitialFocus(View view) {
    if (view != null) {
      ViewCompat.setAccessibilityDelegate(
          view,
          new AccessibilityDelegateCompat() {
            @Override
            public void onInitializeAccessibilityNodeInfo(
                View host, AccessibilityNodeInfoCompat info) {
              super.onInitializeAccessibilityNodeInfo(host, info);
              info.setRequestInitialAccessibilityFocus(true);
            }
          });
    }
  }
}
