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

import static com.google.android.accessibility.talkback.TalkBackService.PERMISSION_TALKBACK;
import static com.google.android.accessibility.talkback.training.PageConfig.PageId.PAGE_ID_FINISHED;
import static com.google.android.accessibility.utils.AccessibilityServiceCompatUtils.Constants.TALKBACK_SERVICE;
import static com.google.android.accessibility.utils.PackageManagerUtils.TALBACK_PACKAGE;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentTransaction;
import androidx.core.view.ViewCompat;
import android.util.Pair;
import android.widget.LinearLayout;
import android.widget.Toolbar;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import com.google.android.accessibility.talkback.BuildConfig;
import com.google.android.accessibility.talkback.R;
import com.google.android.accessibility.talkback.training.NavigationButtonBar.NavigationListener;
import com.google.android.accessibility.talkback.training.PageConfig.PageId;
import com.google.android.accessibility.talkback.training.PageController.OnPageChangeCallback;
import com.google.android.accessibility.talkback.training.TrainingConfig.TrainingId;
import com.google.android.accessibility.talkback.utils.AlertDialogUtils;
import com.google.android.accessibility.utils.AccessibilityServiceCompatUtils;
import com.google.android.libraries.accessibility.utils.log.LogUtils;

/**
 * An activity is for showing a TalkBack training that can be parsed from {@link #EXTRA_TRAINING}
 * argument.
 */
public class TrainingActivity extends FragmentActivity implements OnPageChangeCallback {

  private static final String TAG = "TrainingActivity";
  public static final String EXTRA_TRAINING = "training";
  public static final int ROOT_RES_ID = R.id.training_root;
  public static final String ACTION_TRAINING_PAGE_SWITCHED =
      "com.google.android.marvin.talkback.action.TRAINING_PAGE_SWITCHED";
  public static final String EXTRA_TRAINING_PAGE_ID = "training_page_id";

  @Nullable private TrainingConfig training;
  @Nullable private NavigationButtonBar navigationButtonBar;
  private PageController pageController;

  private final NavigationListener navigationListener =
      new NavigationListener() {
        @Override
        public void onBack() {
          // Goes to the previous page.
          if (pageController.previousPage()) {
            return;
          }
          // No previous page.
          finish();
        }

        @Override
        public void onNext() {
          pageController.nextPage();
        }

        @Override
        public void onExit() {
          // Goes back to the index page.
          if (pageController.backToLinkIndexPage()) {
            return;
          }

          // Don't show the pop-up exit dialog when exiting training from the last page.
          if (pageController.isLastPage()) {
            finish();
            return;
          }

          // Exits training.
          showExitDialog();
        }
      };

  @Override
  public void onPageSwitched(int pageNumber, @Nullable Pair<Integer, Integer> shownPageNumber) {
    PageConfig targetPage = training.getPages().get(pageNumber);
    TrainingFragment fragment = createFragment(targetPage, shownPageNumber);
    FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
    transaction.replace(R.id.training_root, fragment);
    transaction.commit();

    if (targetPage.hasNavigationButtonBar()) {
      createNavigationBar(pageNumber);
    } else {
      // There is no button on the index page.
      removeNavigationBar();
    }

    // Announces page title to notify the page is changed.
    setWindowTitle(getString(targetPage.getPageName()));

    // Passes a page ID to TalkBackService.
    passPageId(this, targetPage.getPageId());
  }

  private TrainingFragment createFragment(
      PageConfig targetPage, @Nullable Pair<Integer, Integer> shownPageNumber) {
    Bundle args = new Bundle();

    // Passes a PageId which is an enum instead of a PageConfig to avoid the serialization problem.
    args.putSerializable(TrainingFragment.EXTRA_PAGE, targetPage.getPageId());
    if (shownPageNumber != null) {
      args.putInt(TrainingFragment.EXTRA_PAGE_NUMBER, shownPageNumber.first);
      args.putInt(TrainingFragment.EXTRA_TOTAL_NUMBER, shownPageNumber.second);
    }

    TrainingFragment fragment = new TrainingFragment();
    fragment.setArguments(args);
    fragment.setLinkHandler((first) -> pageController.handleLink(first));
    return fragment;
  }

  @Override
  protected void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    initialize(getIntent());
  }

  @Override
  protected void onNewIntent(Intent intent) {
    super.onNewIntent(intent);
    initialize(intent);
  }

  @Override
  protected void onStart() {
    super.onStart();
    // Shows a warning dialog if TalkBack is off.
    if (!isTalkBackEnabled(this)) {
      // We don't use TalkBackService.getInstance here because 1. It's bad to expose TalkBack
      // service itself through a global method. 2. When the TrainingActivity is running on a
      // separate process,it's not applicable to access TalkBack's identifiers which is running on
      // another process.
      AlertDialogUtils.builder(this)
          .setTitle(R.string.talkback_inactive_title)
          .setMessage(R.string.talkback_inactive_message)
          .setCancelable(true)
          .setOnCancelListener(dialog -> finish())
          .setPositiveButton(R.string.training_close_button, (dialog, which) -> finish())
          .create()
          .show();
    }
  }

  @Override
  public void finish() {
    passPageId(this, PAGE_ID_FINISHED);
    super.finish();
  }

  @Override
  public void onBackPressed() {
    if (pageController.handleBackPressed()) {
      return;
    }
    finish();
  }

  /** Returns an intent to show the given training on {@link TrainingActivity}. */
  public static Intent createTrainingIntent(Context context, TrainingId training) {
    Intent intent = new Intent(context, TrainingActivity.class);
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
    // Passes a TrainingId which is an enum instead of a TrainingConfig to avoid the serialization
    // problem.
    intent.putExtra(TrainingActivity.EXTRA_TRAINING, training);
    intent.setPackage(TALBACK_PACKAGE);
    return intent;
  }

  @VisibleForTesting
  @Nullable
  PageConfig getCurrentPage() {
    return training == null ? null : training.getPages().get(pageController.getCurrentPageNumber());
  }

  private static boolean isTalkBackEnabled(Context context) {
    return AccessibilityServiceCompatUtils.isAccessibilityServiceEnabled(
        context, TALKBACK_SERVICE.flattenToShortString());
  }

  /**
   * Notifies TalkBackService that which page is shown on the screen. The data isn't sent to
   * TalkBackService if it is disabled.
   *
   * <p>This method has responsibility for checking the service state.
   */
  private static void passPageId(Context context, PageId pageId) {
    // Do not send the data to TalkBackService if it is not enabled.
    if (!isTalkBackEnabled(context)) {
      return;
    }

    Intent intent = new Intent(ACTION_TRAINING_PAGE_SWITCHED);
    intent.putExtra(EXTRA_TRAINING_PAGE_ID, pageId);
    context.sendBroadcast(intent, PERMISSION_TALKBACK);
  }

  /** Initializes activity. */
  private void initialize(Intent intent) {
    @Nullable TrainingConfig training = getTrainingFromIntent(intent);
    if (training == null) {
      finish();
      return;
    }
    setupTrainingView(training);
  }

  @Nullable
  private TrainingConfig getTrainingFromIntent(Intent intent) {

    @Nullable TrainingId trainingId = (TrainingId) intent.getSerializableExtra(EXTRA_TRAINING);
    if (trainingId == null) {
      return null;
    }

    return TrainingConfig.getTraining(trainingId);
  }

  /** Sets up the action bar and the page controller. */
  @VisibleForTesting
  void setupTrainingView(@NonNull TrainingConfig training) {
    setContentView(R.layout.training_activity);

    this.training = training;
    pageController = new PageController(training, /* onPageChangeCallback= */ this);

    Toolbar toolbar = findViewById(R.id.training_toolbar);
    if (training.isSupportNavigateUpArrow()) {
      try {
        // Shows navigate up arrow.
        setActionBar(toolbar);

        toolbar.setNavigationIcon(R.drawable.ic_arrow_back_gm_grey_24dp);
        toolbar.setNavigationContentDescription(R.string.training_navigate_up);
        toolbar.setNavigationOnClickListener(
            view -> {
              if (pageController.backToLinkIndexPage()) {
                return;
              }
              finish();
            });
        getActionBar().setDisplayShowTitleEnabled(false);
      } catch (NullPointerException e) {
        // The exception can be thrown on non-release builds.
        if (BuildConfig.DEBUG) {
          throw e;
        }
        // REFERTO: Skips the action bar to avoid tutorial process crashes when setting
        // REFERTO: the action bar on LG devices.
        LogUtils.e(TAG, "TrainingActivity crashed when setting action bar. %s", e.getMessage());
        LinearLayout layout = findViewById(R.id.training_toolbar_layout);
        layout.removeView(toolbar);
      }

    } else {
      // Removes action bar.
      LinearLayout layout = findViewById(R.id.training_toolbar_layout);
      layout.removeView(toolbar);
    }

    // Shows the first page.
    pageController.initialize();
  }

  /** Sets and announces window title. */
  private void setWindowTitle(String pageTitle) {
    // Set the same window title as the displayed title, which is the first element of a page, to
    // avoid announcing the title twice. Then, the focus will land on the next element after the
    // title.
    setTitle(pageTitle);
    ViewCompat.setAccessibilityPaneTitle(findViewById(R.id.training_root), pageTitle);
  }

  private void showExitDialog() {
    AlertDialogUtils.builder(this)
        .setTitle(R.string.exit_tutorial_title)
        .setMessage(R.string.exit_tutorial_content)
        .setCancelable(true)
        .setPositiveButton(R.string.training_close_button, (dialog, which) -> finish())
        .setNegativeButton(R.string.stay_in_tutorial_button, (dialog, which) -> {})
        .create()
        .show();
  }

  /** Creates new navigation buttons. */
  private void createNavigationBar(int position) {
    if (training == null) {
      return;
    }

    LinearLayout navigationBar = findViewById(R.id.training_bottom);
    navigationBar.removeView(navigationButtonBar);
    navigationButtonBar =
        new NavigationButtonBar(
            this,
            training.getButtons(),
            navigationListener,
            position,
            pageController.isFirstPage() || pageController.isOnePage(),
            pageController.isLastPage(),
            training.isExitButtonOnlyShowOnLastPage());
    navigationBar.addView(navigationButtonBar);
  }

  /** Removes navigation buttons. */
  private void removeNavigationBar() {
    LinearLayout navigationBar = findViewById(R.id.training_bottom);
    navigationBar.removeView(navigationButtonBar);
    navigationButtonBar = null;
  }

  @VisibleForTesting
  @Nullable
  NavigationButtonBar getNavigationButtonBar() {
    return navigationButtonBar;
  }
}
