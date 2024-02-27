/*
 * Copyright (C) 2023 Google Inc.
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

import static com.google.android.accessibility.talkback.trainingcommon.NavigationButtonBar.BUTTON_TYPE_EXIT;
import static com.google.android.accessibility.talkback.trainingcommon.NavigationButtonBar.BUTTON_TYPE_NEXT;

import androidx.wear.widget.ConfirmationOverlay;
import com.google.android.accessibility.talkback.R;
import com.google.android.accessibility.talkback.trainingcommon.PageConfig;
import com.google.android.accessibility.talkback.trainingcommon.PageConfig.PageId;
import com.google.android.accessibility.talkback.trainingcommon.TrainingActivity;
import com.google.android.accessibility.talkback.trainingcommon.TrainingConfig;
import com.google.android.accessibility.talkback.trainingcommon.TrainingFragment;
import com.google.android.accessibility.talkback.trainingcommon.TrainingSwipeDismissListener;
import com.google.common.collect.ImmutableList;

/** Sets up the TalkBack tutorial content on Wear. */
public final class WearTutorialInitiator {

  // TODO: After we separate the tutorial as a singleton library, we should move it to
  //  the corresponding resource files.
  private static final int EXTRA_MARGIN_TOP_TITLE_FOR_SHORT_TEXT_DP = 20;
  private static final int EXTRA_MARGIN_TOP_NAVIGATION_BUTTON_FOR_SHORT_TEXT_DP = 7;
  private static final int EXTRA_MARGIN_TOP_TITLE_FOR_LAST_PAGE_DP = 25;
  private static final int EXTRA_MARGIN_TOP_NAVIGATION_BUTTON_FOR_LAST_PAGE_DP = 21;

  /** A class encapsulating interaction logic with a user for the go back tutorial page. */
  static class GoBackTutorialSwipeDismissListener implements TrainingSwipeDismissListener {

    /** Sets to true if the {@link #onDismissed(TrainingActivity)} has been invoked. */
    boolean invoked = false;

    @Override
    public boolean onDismissed(TrainingActivity activity) {
      if (!invoked) {
        new ConfirmationOverlay()
            .setType(ConfirmationOverlay.SUCCESS_ANIMATION)
            .setMessage(
                (CharSequence)
                    activity.getString(
                        R.string.wear_training_go_back_success_confirmation_overlay_message))
            // We go to the next button after the overlay's animation is finished.
            .setOnAnimationFinishedListener(
                () -> {
                  TrainingFragment trainingFragment = activity.getCurrentTrainingFragment();
                  if (trainingFragment != null) {
                    trainingFragment.moveInputFocusToNextButton();
                  }
                })
            .showOn(activity);
        invoked = true;
        return true;
      }

      return false;
    }
  }

  public static final PageConfig.Builder WELCOME_TO_TALKBACK_WATCH_PAGE =
      PageConfig.builder(
              PageId.PAGE_ID_WELCOME_TO_TALKBACK_WATCH, R.string.welcome_to_talkback_title)
          .hidePageNumber()
          .addText(R.string.wear_training_welcome_paragraph)
          .setNavigationButtons(ImmutableList.of(BUTTON_TYPE_NEXT, BUTTON_TYPE_EXIT));

  public static final PageConfig.Builder SCROLLING_WATCH_PAGE =
      PageConfig.builder(PageId.PAGE_ID_WATCH_SCROLLING, R.string.wear_training_scroll_title)
          .hidePageNumber()
          .addText(R.string.wear_training_scroll_text)
          .addList(R.array.tutorial_scrolling_items);

  public static final PageConfig.Builder GO_BACK_WATCH_PAGE =
      PageConfig.builder(PageId.PAGE_ID_WATCH_GO_BACK, R.string.wear_training_go_back_title)
          .hidePageNumber()
          .addText(R.string.wear_training_go_back_text);

  public static final PageConfig.Builder VOLUME_UP_WATCH_PAGE =
      PageConfig.builder(PageId.PAGE_ID_WATCH_VOLUME_UP, R.string.wear_training_volume_up_title)
          .hidePageNumber()
          .setTitleExtraMarginTop(EXTRA_MARGIN_TOP_TITLE_FOR_SHORT_TEXT_DP)
          .setNavigationButtonExtraMarginTop(EXTRA_MARGIN_TOP_NAVIGATION_BUTTON_FOR_SHORT_TEXT_DP)
          .addText(R.string.wear_training_volume_up_text);

  public static final PageConfig.Builder VOLUME_DOWN_WATCH_PAGE =
      PageConfig.builder(PageId.PAGE_ID_WATCH_VOLUME_DOWN, R.string.wear_training_volume_down_title)
          .hidePageNumber()
          .setTitleExtraMarginTop(EXTRA_MARGIN_TOP_TITLE_FOR_SHORT_TEXT_DP)
          .setNavigationButtonExtraMarginTop(EXTRA_MARGIN_TOP_NAVIGATION_BUTTON_FOR_SHORT_TEXT_DP)
          .addText(R.string.wear_training_volume_down_text);

  public static final PageConfig.Builder OPEN_TALKBACK_MENU_WATCH_PAGE =
      PageConfig.builder(
              PageId.PAGE_ID_WATCH_OPEN_TALKBACK_MENU,
              R.string.wear_training_open_talkback_menu_title)
          .hidePageNumber()
          .addText(R.string.wear_training_open_talkback_menu_text);

  public static final PageConfig.Builder END_TUTORIAL_WATCH_PAGE =
      PageConfig.builder(
              PageId.PAGE_ID_WATCH_END_TUTORIAL, R.string.wear_training_end_tutorial_title)
          .hidePageNumber()
          .setTitleExtraMarginTop(EXTRA_MARGIN_TOP_TITLE_FOR_LAST_PAGE_DP)
          .clearTitleHorizontalMargin()
          .setNavigationButtonExtraMarginTop(EXTRA_MARGIN_TOP_NAVIGATION_BUTTON_FOR_LAST_PAGE_DP)
          .setNavigationButtons(ImmutableList.of(BUTTON_TYPE_EXIT));

  private WearTutorialInitiator() {}

  private static ImmutableList<PageConfig.Builder> createWearPageConfigList() {
    return ImmutableList.of(
        WELCOME_TO_TALKBACK_WATCH_PAGE,
        SCROLLING_WATCH_PAGE,
        GO_BACK_WATCH_PAGE.setTrainingSwipeDismissListener(
            new GoBackTutorialSwipeDismissListener()),
        VOLUME_UP_WATCH_PAGE,
        VOLUME_DOWN_WATCH_PAGE,
        OPEN_TALKBACK_MENU_WATCH_PAGE,
        END_TUTORIAL_WATCH_PAGE);
  }

  public static TrainingConfig createTutorialForWatch() {
    return TrainingConfig.builder(R.string.welcome_to_talkback_title)
        .setPages(createWearPageConfigList())
        .setButtons(ImmutableList.of(BUTTON_TYPE_NEXT))
        .build();
  }
}
