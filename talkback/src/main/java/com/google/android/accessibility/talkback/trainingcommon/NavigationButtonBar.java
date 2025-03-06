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

import static com.google.android.accessibility.utils.material.ButtonIconType.ICON_TYPE_BACK;
import static com.google.android.accessibility.utils.material.ButtonIconType.ICON_TYPE_CANCEL;
import static com.google.android.accessibility.utils.material.ButtonIconType.ICON_TYPE_CHECK;
import static com.google.android.accessibility.utils.material.ButtonIconType.ICON_TYPE_NEXT;
import static com.google.android.accessibility.utils.material.MaterialComponentUtils.ButtonStyle.DEFAULT_BUTTON;
import static com.google.android.accessibility.utils.material.MaterialComponentUtils.ButtonStyle.FILLED_BUTON;
import static com.google.android.accessibility.utils.material.MaterialComponentUtils.ButtonStyle.OUTLINED_BUTTON;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.text.TextUtils.TruncateAt;
import android.util.TypedValue;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import androidx.annotation.Dimension;
import androidx.annotation.IdRes;
import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.annotation.VisibleForTesting;
import com.google.android.accessibility.talkback.R;
import com.google.android.accessibility.talkback.trainingcommon.tv.TvNavigationButton;
import com.google.android.accessibility.utils.DisplayUtils;
import com.google.android.accessibility.utils.FormFactorUtils;
import com.google.android.accessibility.utils.material.AccessibilitySuiteButtonHelper;
import com.google.android.accessibility.utils.material.MaterialComponentUtils;
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
  @IntDef({BUTTON_TYPE_BACK, BUTTON_TYPE_NEXT, BUTTON_TYPE_EXIT, BUTTON_TYPE_FINISH})
  public @interface ButtonType {}

  public static final int BUTTON_TYPE_BACK = 0;
  public static final int BUTTON_TYPE_NEXT = 1;
  public static final int BUTTON_TYPE_EXIT = 2;
  public static final int BUTTON_TYPE_FINISH = 3;

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
  private final boolean isPrevButtonShownOnFirstPage;

  public NavigationButtonBar(
      Context context,
      List<Integer> navigationButtons,
      NavigationListener navigationListener,
      int currentPageNumber,
      boolean isFirstPage,
      boolean isLastPage,
      boolean isExitButtonOnlyShowOnLastPage,
      boolean isPrevButtonShownOnFirstPage,
      @Dimension(unit = Dimension.DP) int extraNavigationButtonMarginTop) {
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
    this.isPrevButtonShownOnFirstPage = isPrevButtonShownOnFirstPage;

    if (FormFactorUtils.getInstance().isAndroidTv()) {
      setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
      setClipChildren(false);
      setClipToPadding(false);
    }

    if (FormFactorUtils.getInstance().isAndroidWear()) {
      createButtonsForWear();
    } else {
      createButtons();
    }

    if (extraNavigationButtonMarginTop > 0) {
      ViewGroup.MarginLayoutParams layoutParams =
          (MarginLayoutParams) this.navigationBarLayout.getLayoutParams();
      layoutParams.topMargin += DisplayUtils.dpToPx(context, extraNavigationButtonMarginTop);
      this.navigationBarLayout.setLayoutParams(layoutParams);
    }
  }

  void requestFocusOnButton(@ButtonType int type) {
    Button button = getButton(type);
    if (button != null) {
      button.setFocusableInTouchMode(true);
      button.requestFocus();
    }
  }

  /**
   * Creates navigation buttons for the current page. The order of buttons is Next, Back and Close,
   * but the order of buttons on the last page is Finish and Back.
   */
  private void createButtons() {
    int buttonCount = getButtonsCount();
    if (isLastPage && hasButton(BUTTON_TYPE_EXIT)) {
      addButton(BUTTON_TYPE_FINISH, buttonCount);
    }

    // Add Next button if the current page is not the last page.
    if (!isLastPage && hasButton(BUTTON_TYPE_NEXT)) {
      addButton(BUTTON_TYPE_NEXT, buttonCount);
    }

    // Add back button if the current page is not the first page.
    if ((!isFirstPage || isPrevButtonShownOnFirstPage) && hasButton(BUTTON_TYPE_BACK)) {
      addButton(BUTTON_TYPE_BACK, buttonCount);
    }

    // If isExitButtonOnlyShowOnLastPage flag is true, exit button only shows on the last page.
    if (!isLastPage && !isExitButtonOnlyShowOnLastPage && hasButton(BUTTON_TYPE_EXIT)) {
      addButton(BUTTON_TYPE_EXIT, buttonCount);
    }
  }

  /**
   * Creates navigation buttons for the current page on wear. The order of buttons is negative and
   * then positive buttons.
   */
  private void createButtonsForWear() {
    int buttonCount = getButtonsCount();
    if (isLastPage && hasButton(BUTTON_TYPE_EXIT)) {
      addButton(BUTTON_TYPE_FINISH, buttonCount);
    }

    // If isExitButtonOnlyShowOnLastPage flag is true, exit button only shows on the last page.
    if (!isLastPage && !isExitButtonOnlyShowOnLastPage && hasButton(BUTTON_TYPE_EXIT)) {
      addButton(BUTTON_TYPE_EXIT, buttonCount);
    }

    // Add Next button if the current page is not the last page.
    if (!isLastPage && hasButton(BUTTON_TYPE_NEXT)) {
      addButton(BUTTON_TYPE_NEXT, buttonCount);
    }
  }

  private int getButtonsCount() {
    int count = 0;
    if (isLastPage && hasButton(BUTTON_TYPE_EXIT)) {
      count++;
    }

    // Add Next button if the current page is not the last page.
    if (!isLastPage && hasButton(BUTTON_TYPE_NEXT)) {
      count++;
    }

    // Add back button if the current page is not the first page.
    if (!isFirstPage && hasButton(BUTTON_TYPE_BACK)) {
      count++;
    }

    // If isExitButtonOnlyShowOnLastPage flag is true, exit button only shows on the last page.
    if (!isLastPage && !isExitButtonOnlyShowOnLastPage && hasButton(BUTTON_TYPE_EXIT)) {
      count++;
    }

    return count;
  }

  /** Creates a navigation button, the ID of which depends on page number. */
  private void addButton(@ButtonType int buttonType, int buttonCount) {
    switch (buttonType) {
      case BUTTON_TYPE_BACK:
        Button backButton =
            createButton(
                getContext(),
                buttonType,
                R.string.training_back_button,
                view -> navigationListener.onBack(),
                buttonCount);
        navigationBarLayout.addView(backButton);
        return;
      case BUTTON_TYPE_NEXT:
        Button nextButton =
            createButton(
                getContext(),
                buttonType,
                R.string.training_next_button,
                view -> navigationListener.onNext(),
                buttonCount);
        navigationBarLayout.addView(nextButton);
        return;
      case BUTTON_TYPE_EXIT:
        Button exitButton =
            createButton(
                getContext(),
                buttonType,
                R.string.training_finish_button,
                view -> navigationListener.onExit(),
                buttonCount);
        exitButton.setContentDescription(getContext().getString(R.string.training_finish_tutorial));
        navigationBarLayout.addView(exitButton);
        return;
      case BUTTON_TYPE_FINISH:
        Button finishButton =
            createButton(
                getContext(),
                buttonType,
                R.string.training_finish_button,
                view -> navigationListener.onExit(),
                buttonCount);
        navigationBarLayout.addView(finishButton);
        return;
      default:
        throw new IllegalArgumentException("Unsupported button type.");
    }
  }

  private Button createButton(
      Context context,
      @ButtonType int buttonType,
      @StringRes int text,
      OnClickListener clickListener,
      int buttonCount) {
    if (FormFactorUtils.getInstance().isAndroidTv()) {
      return createButtonTv(context, text, clickListener);
    }

    Drawable drawable = createButtonIconDrawable(context, buttonType);
    Button button;

    if (drawable == null) {
      // Creates the text button.
      if (MaterialComponentUtils.supportMaterialComponent(context)) {
        if ((buttonType == BUTTON_TYPE_NEXT) || (buttonType == BUTTON_TYPE_FINISH)) {
          button = MaterialComponentUtils.createButton(context, FILLED_BUTON);
        } else if ((buttonType == BUTTON_TYPE_EXIT) || (buttonType == BUTTON_TYPE_BACK)) {
          button = MaterialComponentUtils.createButton(context, OUTLINED_BUTTON);
        } else {
          button = MaterialComponentUtils.createButton(context, DEFAULT_BUTTON);
        }
      } else {
        button = new Button(context);
        button.setBackgroundColor(
            context
                .getResources()
                .getColor(
                    R.color.training_navigation_button_bar_background_color, /* theme= */ null));
        button.setTextColor(
            context.getResources().getColor(R.color.training_button_text_color, /* theme= */ null));
      }
      button.setText(text);
      button.setTypeface(
          Typeface.create(context.getString(com.google.android.accessibility.utils.R.string.accessibility_font), Typeface.NORMAL));
      button.setTextSize(
          TypedValue.COMPLEX_UNIT_PX,
          context.getResources().getDimensionPixelSize(R.dimen.training_button_text_size));
      button.setPaddingRelative(
          0,
          0,
          0,
          context
              .getResources()
              .getDimensionPixelSize(R.dimen.training_button_text_padding_bottom));
      button.setAllCaps(false);
      button.setEllipsize(TruncateAt.END);
      button.setLines(1);
    } else {
      // Creates the icon button
      button = new Button(context);
      button.setBackground(drawable);
      button.setContentDescription(context.getString(text));
    }

    button.setId(getButtonId(buttonType, currentPageNumber));
    button.setLayoutParams(
        drawable == null
            ? createLayoutParams(context, buttonType, buttonCount)
            : createLayoutParamsForIconButton(context, drawable));
    button.setOnClickListener(clickListener);
    return button;
  }

  private static LayoutParams createLayoutParams(
      Context context, @ButtonType int buttonType, int buttonCount) {
    LayoutParams layoutParams =
        new LayoutParams(
            /* width= */ 0,
            (int) context.getResources().getDimension(R.dimen.training_button_height),
            /* weight= */ 1);

    if (MaterialComponentUtils.supportMaterialComponent(context)) {
      // Default 3-button layout
      int leftMarginDimRes = R.dimen.training_button_margin_2dp;
      int rightMarginDimRes = R.dimen.training_button_margin_2dp;

      if (buttonCount == 2) {
        if ((buttonType == BUTTON_TYPE_NEXT) || (buttonType == BUTTON_TYPE_FINISH)) {
          // Sets left button layout
          leftMarginDimRes = R.dimen.training_button_margin_24dp;
          rightMarginDimRes = R.dimen.training_button_margin_8dp;
        } else if ((buttonType == BUTTON_TYPE_EXIT) || (buttonType == BUTTON_TYPE_BACK)) {
          // Sets right button layout
          leftMarginDimRes = R.dimen.training_button_margin_8dp;
          rightMarginDimRes = R.dimen.training_button_margin_24dp;
        }
      } else if (buttonCount == 1) {
        // Sets 1 button layout
        leftMarginDimRes = R.dimen.training_button_margin_24dp;
        rightMarginDimRes = R.dimen.training_button_margin_24dp;
      }
      layoutParams.leftMargin = (int) context.getResources().getDimension(leftMarginDimRes);
      layoutParams.rightMargin = (int) context.getResources().getDimension(rightMarginDimRes);
    }

    return layoutParams;
  }

  private static LayoutParams createLayoutParamsForIconButton(Context context, Drawable drawable) {
    LayoutParams layoutParams =
        new LinearLayout.LayoutParams(drawable.getMinimumWidth(), drawable.getMinimumHeight());
    layoutParams.leftMargin =
        layoutParams.rightMargin =
            (int)
                context.getResources().getDimension(R.dimen.training_icon_button_horizontal_margin);
    return layoutParams;
  }

  private static Button createButtonTv(
      Context context, @StringRes int text, OnClickListener clickListener) {
    TvNavigationButton button = new TvNavigationButton(context);
    button.setText(context.getString(text));
    button.setOnClickListener(clickListener);
    return button;
  }

  /**
   * Returns an alternate resource ID for the specified button to avoid the focus still keeping on
   * the last focused node when the page is changed.
   */
  @IdRes
  private static int getButtonId(@ButtonType int type, int currentPageNumber) {
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
      case BUTTON_TYPE_FINISH:
        return (currentPageNumber % 2 == 0)
            ? R.id.training_finish_button_0
            : R.id.training_finish_button_1;
      default:
        throw new IllegalArgumentException("Unsupported button type.");
    }
  }

  private boolean hasButton(@ButtonType int buttonType) {
    return navigationButtons.contains(buttonType);
  }

  /**
   * The function is used to generate the Drawable by the type of button.
   *
   * @param context the context
   * @param buttonType The type of the button to generate the drawable.
   */
  protected Drawable createButtonIconDrawable(Context context, int buttonType) {
    int iconType;
    switch (buttonType) {
      case BUTTON_TYPE_BACK:
        iconType = ICON_TYPE_BACK;
        break;
      case BUTTON_TYPE_NEXT:
        iconType = ICON_TYPE_NEXT;
        break;
      case BUTTON_TYPE_EXIT:
        iconType = ICON_TYPE_CANCEL;
        break;
      case BUTTON_TYPE_FINISH:
        iconType = ICON_TYPE_CHECK;
        break;
      default:
        throw new IllegalArgumentException("Unsupported button type.");
    }
    return AccessibilitySuiteButtonHelper.createButtonIconDrawable(context, iconType);
  }

  @VisibleForTesting
  @Nullable
  Button getButton(@ButtonType int type) {
    return navigationBarLayout.findViewById(getButtonId(type, currentPageNumber));
  }
}
