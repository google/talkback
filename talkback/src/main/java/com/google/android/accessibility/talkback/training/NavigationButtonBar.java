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

import android.content.Context;
import android.util.TypedValue;
import android.widget.Button;
import android.widget.LinearLayout;
import androidx.annotation.IdRes;
import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.annotation.VisibleForTesting;
import com.google.android.accessibility.talkback.R;
import com.google.common.collect.ImmutableList;
import java.util.List;

/** A navigation bar with four buttons for Back, Next, Exit and Turn off TalkBack. */
public class NavigationButtonBar extends LinearLayout {

  private static final String TAG = "NavigationButtonBar";

  /** A callback to be invoked when training navigation button has been clicked. */
  public interface NavigationListener {
    /** Called when Back button has been clicked. */
    void onBack();

    /** Called when Next button has been clicked. */
    void onNext();

    /** Called when Exit button has been clicked. */
    void onExit();
  }

  /** The function of buttons. */
  @IntDef({BUTTON_TYPE_BACK, BUTTON_TYPE_NEXT, BUTTON_TYPE_EXIT})
  public @interface ButtonType {}

  public static final int BUTTON_TYPE_BACK = 0;
  public static final int BUTTON_TYPE_NEXT = 1;
  public static final int BUTTON_TYPE_EXIT = 2;

  public static final ImmutableList<Integer> DEFAULT_BUTTONS =
      ImmutableList.of(BUTTON_TYPE_BACK, BUTTON_TYPE_NEXT, BUTTON_TYPE_EXIT);

  private final LinearLayout navigationBarLayout;
  /**
   * A list of buttons which will be shown on the navigation button bar.
   *
   * <p>The type of the element in the list should be {@link ButtonType}.
   */
  private final List<Integer> navigationButtons;

  private final NavigationListener navigationListener;
  private final int currentPageNumber;
  private final boolean isFirstPage;
  private final boolean isLastPage;
  private final boolean isExitButtonOnlyShowOnLastPage;

  public NavigationButtonBar(
      Context context,
      List<Integer> navigationButtons,
      NavigationListener navigationListener,
      int currentPageNumber,
      boolean isFirstPage,
      boolean isLastPage,
      boolean isExitButtonOnlyShowOnLastPage) {
    super(context);
    this.navigationBarLayout =
        inflate(context, R.layout.training_navigation_button_bar, this)
            .findViewById(R.id.training_navigation);
    this.navigationButtons = navigationButtons;
    this.navigationListener = navigationListener;
    this.currentPageNumber = currentPageNumber;
    this.isFirstPage = isFirstPage;
    this.isLastPage = isLastPage;
    this.isExitButtonOnlyShowOnLastPage = isExitButtonOnlyShowOnLastPage;
    createButtons();
  }

  /**
   * Creates navigation buttons for the current page. The order of buttons is Next, Back and Close,
   * but the order of buttons on the last page is Finish and Back.
   */
  private void createButtons() {
    if (isLastPage) {
      if (hasButton(BUTTON_TYPE_EXIT)) {
        addButton(BUTTON_TYPE_EXIT);
      }
    }

    // Add Next button if the current page is not the last page.
    if (!isLastPage) {
      if (hasButton(BUTTON_TYPE_NEXT)) {
        addButton(BUTTON_TYPE_NEXT);
      }
    }

    // Add back button if the current page is not the first page.
    if (!isFirstPage) {
      if (hasButton(BUTTON_TYPE_BACK)) {
        addButton(BUTTON_TYPE_BACK);
      }
    }

    // If isExitButtonOnlyShowOnLastPage flag is true, exit button only shows on the last page.
    if (!isLastPage && !isExitButtonOnlyShowOnLastPage) {
      if (hasButton(BUTTON_TYPE_EXIT)) {
        addButton(BUTTON_TYPE_EXIT);
      }
    }
  }

  /** Creates a navigation button, the ID of which depends on page number. */
  private void addButton(@ButtonType int buttonType) {
    switch (buttonType) {
      case BUTTON_TYPE_BACK:
        Button backButton =
            createButton(
                getContext(),
                getButtonId(buttonType),
                R.string.training_back_button,
                view -> navigationListener.onBack());
        navigationBarLayout.addView(backButton);
        return;
      case BUTTON_TYPE_NEXT:
        Button nextButton =
            createButton(
                getContext(),
                getButtonId(buttonType),
                R.string.training_next_button,
                view -> navigationListener.onNext());
        navigationBarLayout.addView(nextButton);
        return;
      case BUTTON_TYPE_EXIT:
        Button exitButton =
            createButton(
                getContext(),
                getButtonId(buttonType),
                isLastPage ? R.string.training_finish_button : R.string.training_close_button,
                view -> navigationListener.onExit());
        navigationBarLayout.addView(exitButton);
        return;
      default:
        throw new IllegalArgumentException("Unsupported button type.");
    }
  }

  private static Button createButton(
      Context context, int id, @StringRes int text, OnClickListener clickListener) {
    Button button = new Button(context);
    button.setId(id);
    LayoutParams layoutParams =
        new LayoutParams(
            0, (int) (context.getResources().getDimension(R.dimen.training_button_height)), 1);
    button.setLayoutParams(layoutParams);
    button.setBackgroundColor(
        context
            .getResources()
            .getColor(R.color.training_navigation_button_bar_background_color, null));
    button.setText(text);
    button.setTextColor(context.getResources().getColor(R.color.training_button_text_color, null));
    button.setTextSize(
        TypedValue.COMPLEX_UNIT_PX,
        context.getResources().getDimensionPixelSize(R.dimen.training_button_text_size));
    button.setAllCaps(false);
    button.setOnClickListener(clickListener);
    return button;
  }

  /**
   * Returns an alternate resource ID for the specified button to avoid the focus still keeping on
   * the last focused node when the page is changed.
   */
  private @IdRes int getButtonId(@ButtonType int type) {
    switch (type) {
      case BUTTON_TYPE_BACK:
        return (currentPageNumber % 2 == 0)
            ? R.id.training_back_button_0
            : R.id.training_back_button_1;
      case BUTTON_TYPE_NEXT:
        return (currentPageNumber % 2 == 0)
            ? R.id.training_next_button_0
            : R.id.training_next_button_1;
      case BUTTON_TYPE_EXIT:
        return (currentPageNumber % 2 == 0)
            ? R.id.training_exit_button_0
            : R.id.training_exit_button_1;
      default:
        throw new IllegalArgumentException("Unsupported button type.");
    }
  }

  private boolean hasButton(@ButtonType int buttonType) {
    return navigationButtons.contains(buttonType);
  }

  @VisibleForTesting
  @Nullable
  Button getButton(@ButtonType int type) {
    return navigationBarLayout.findViewById(getButtonId(type));
  }
}
