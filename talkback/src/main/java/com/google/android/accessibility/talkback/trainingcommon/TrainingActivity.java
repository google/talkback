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

import static com.google.android.accessibility.talkback.analytics.TalkBackAnalytics.TRAINING_BUTTON_CLOSE;
import static com.google.android.accessibility.talkback.analytics.TalkBackAnalytics.TRAINING_SECTION_ONBOARDING;
import static com.google.android.accessibility.talkback.analytics.TalkBackAnalytics.TRAINING_SECTION_TUTORIAL;
import static com.google.android.accessibility.talkback.analytics.TalkBackAnalytics.TRAINING_SECTION_TUTORIAL_BASIC_NAVIGATION;
import static com.google.android.accessibility.talkback.analytics.TalkBackAnalytics.TRAINING_SECTION_TUTORIAL_EVERYDAY_TASKS;
import static com.google.android.accessibility.talkback.analytics.TalkBackAnalytics.TRAINING_SECTION_TUTORIAL_PRACTICE_GESTURES;
import static com.google.android.accessibility.talkback.analytics.TalkBackAnalytics.TRAINING_SECTION_TUTORIAL_READING_NAVIGATION;
import static com.google.android.accessibility.talkback.analytics.TalkBackAnalytics.TRAINING_SECTION_TUTORIAL_TEXT_EDITING;
import static com.google.android.accessibility.talkback.analytics.TalkBackAnalytics.TRAINING_SECTION_TUTORIAL_VOICE_COMMAND;
import static com.google.android.accessibility.talkback.ipc.IpcService.EXTRA_TRAINING_PAGE_ID;
import static com.google.android.accessibility.talkback.ipc.IpcService.MSG_DOWNLOAD_ICON_DETECTION;
import static com.google.android.accessibility.talkback.ipc.IpcService.MSG_DOWNLOAD_IMAGE_DESCRIPTION;
import static com.google.android.accessibility.talkback.ipc.IpcService.MSG_REQUEST_AVAILABLE_FEATURES;
import static com.google.android.accessibility.talkback.ipc.IpcService.MSG_REQUEST_DISABLE_TALKBACK;
import static com.google.android.accessibility.talkback.ipc.IpcService.MSG_REQUEST_GESTURES;
import static com.google.android.accessibility.talkback.ipc.IpcService.MSG_TRAINING_FINISH;
import static com.google.android.accessibility.talkback.ipc.IpcService.MSG_TRAINING_PAGE_SWITCHED;
import static com.google.android.accessibility.talkback.trainingcommon.PageConfig.PageId.PAGE_ID_FINISHED;
import static com.google.android.accessibility.utils.AccessibilityServiceCompatUtils.Constants.TALKBACK_SERVICE;
import static com.google.android.accessibility.utils.PackageManagerUtils.TALKBACK_PACKAGE;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager.NameNotFoundException;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentTransaction;
import android.util.Pair;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toolbar;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.core.content.res.ResourcesCompat;
import com.google.android.accessibility.talkback.BuildConfig;
import com.google.android.accessibility.talkback.R;
import com.google.android.accessibility.talkback.TalkBackMetricStore;
import com.google.android.accessibility.talkback.analytics.TalkBackAnalytics.TrainingSectionId;
import com.google.android.accessibility.talkback.ipc.IpcService;
import com.google.android.accessibility.talkback.trainingcommon.NavigationButtonBar.NavigationListener;
import com.google.android.accessibility.talkback.trainingcommon.PageConfig.PageId;
import com.google.android.accessibility.talkback.trainingcommon.PageController.OnPageChangeCallback;
import com.google.android.accessibility.talkback.trainingcommon.TrainingConfig.TrainingId;
import com.google.android.accessibility.talkback.trainingcommon.TrainingIpcClient.IpcServerStateListener;
import com.google.android.accessibility.utils.AccessibilityServiceCompatUtils;
import com.google.android.accessibility.utils.FormFactorUtils;
import com.google.android.accessibility.utils.material.A11yAlertDialogWrapper;
import com.google.android.accessibility.utils.material.SwipeDismissListener;
import com.google.android.accessibility.utils.material.WrapSwipeDismissLayoutHelper;
import com.google.android.libraries.accessibility.utils.log.LogUtils;
import com.google.common.collect.ImmutableMap;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.lang.ref.WeakReference;
import java.util.Objects;
import java.util.function.Function;

/**
 * An activity is for showing a TalkBack training that can be parsed from {@link #EXTRA_TRAINING}
 * argument.
 */
public class TrainingActivity extends FragmentActivity
    implements OnPageChangeCallback, IpcServerStateListener {

  private static final String TAG = "TrainingActivity";
  private static final String ACTION_START =
      "com.google.android.accessibility.talkback.training.START";
  public static final String EXTRA_USER_INITIATED = "user_initiated";
  public static final String EXTRA_TRAINING = "training";
  public static final String EXTRA_TRAINING_SHOW_EXIT_BANNER = "training_show_exit_banner";

  // the container layout ID of TrainingFragment
  public static final int ROOT_RES_ID = R.id.training_root;

  @Nullable private TrainingConfig training;
  @Nullable private NavigationButtonBar navigationButtonBar;
  private PageController pageController;
  private NavigationController navigationController;
  @Nullable private TrainingIpcClient ipcClient;
  private final FormFactorUtils formFactorUtils = FormFactorUtils.getInstance();

  private TalkBackMetricStore metricStore;

  // For some form factors, they don't have navigation bar container in activity's layout.
  @Nullable private ViewGroup navigationBarContainer;

  private final TrainingActivityInterfaceInjector trainingActivityInterfaceInjector =
      TrainingActivityInterfaceInjector.getInstance();

  private static class NavigationController implements NavigationListener, SwipeDismissListener {
    final PageController pageController;
    final WeakReference<TrainingActivity> trainingActivityWeakReference;

    public NavigationController(TrainingActivity trainingActivity, PageController pageController) {
      this.pageController = pageController;
      pageController.setNavigationListener(this);
      trainingActivityWeakReference = new WeakReference<>(trainingActivity);
    }

    @Override
    public boolean onDismissed(FragmentActivity activity) {
      boolean consumed = false;

      PageConfig pageConfig = pageController.getCurrentPageConfig();
      if (pageConfig == null) {
        return false;
      }

      TrainingSwipeDismissListener swipeDismissListener = pageConfig.getSwipeDismissListener();
      if (swipeDismissListener != null) {
        consumed = swipeDismissListener.onDismissed((TrainingActivity) activity);
      }

      if (!consumed) {
        // We fallback to back-pressed action.
        consumed = onBackPressed();
      }

      return consumed;
    }

    @Override
    public void onBack() {
      TrainingActivity trainingActivity = trainingActivityWeakReference.get();
      if (trainingActivity == null) {
        return;
      }

      // Goes to the previous page.
      if (trainingActivity.goBackPreviousPage()) {
        return;
      }
      // No previous page.
      trainingActivity.finishOnAbort(/* userInitiated= */ true);
    }

    @Override
    public void onNext() {
      pageController.nextPage();
    }

    @Override
    public void onExit() {
      TrainingActivity trainingActivity = trainingActivityWeakReference.get();
      if (trainingActivity == null) {
        return;
      }

      // Goes back to the index page.
      if (pageController.backToLinkIndexPage()) {
        return;
      }

      // Don't show the pop-up exit dialog when exiting training from the last page.
      if (pageController.isLastPage()) {
        trainingActivity.finishOnComplete();
        return;
      }

      // Exits training.
      trainingActivity.showExitDialog();
    }

    public void onNavigationClick(View navigationView) {
      TrainingActivity trainingActivity = trainingActivityWeakReference.get();
      if (trainingActivity == null) {
        return;
      }

      if (pageController.backToLinkIndexPage()) {
        return;
      }
      trainingActivity.finishOnAbort(/* userInitiated= */ true);
    }

    @CanIgnoreReturnValue
    public boolean onBackPressed() {
      TrainingActivity trainingActivity = trainingActivityWeakReference.get();
      if (trainingActivity == null) {
        return false;
      }

      if (pageController.handleBackPressed()) {
        return true;
      }
      trainingActivity.finishOnAbort(/* userInitiated= */ true);
      return true;
    }
  }

  public TrainingFragment getCurrentTrainingFragment() {
    return (TrainingFragment) getSupportFragmentManager().findFragmentById(ROOT_RES_ID);
  }

  private void finishOnComplete() {
    notifyTrainingFinishByUser();
    setResult(RESULT_OK);
    finish();
  }

  private void finishOnAbort(boolean userInitiated) {
    if (userInitiated) {
      notifyTrainingFinishByUser();
    }

    Intent finishData = new Intent();
    finishData.putExtra(EXTRA_USER_INITIATED, userInitiated);
    setResult(RESULT_CANCELED, finishData);
    finish();
  }

  // Each time when user enters a tutorial section, the first page of the section will be presented.
  // We record in a map the entry page title of each tutorial section, which can help to identify
  // how many times user enter this section.
  private final ImmutableMap<Integer, Integer> docPageToMetric =
      ImmutableMap.<Integer, Integer>builder()
          .put(R.string.talkback_tutorial_title, TRAINING_SECTION_TUTORIAL)
          .put(R.string.new_feature_in_talkback_title, TRAINING_SECTION_ONBOARDING)
          .put(R.string.welcome_to_talkback_title, TRAINING_SECTION_TUTORIAL_BASIC_NAVIGATION)
          .put(R.string.using_text_boxes_title, TRAINING_SECTION_TUTORIAL_TEXT_EDITING)
          .put(R.string.read_by_character_title, TRAINING_SECTION_TUTORIAL_READING_NAVIGATION)
          .put(R.string.voice_commands_title, TRAINING_SECTION_TUTORIAL_VOICE_COMMAND)
          .put(R.string.making_calls_title, TRAINING_SECTION_TUTORIAL_EVERYDAY_TASKS)
          .put(R.string.practice_gestures_title, TRAINING_SECTION_TUTORIAL_PRACTICE_GESTURES)
          .buildOrThrow();
  private boolean trainingLogged;
  private boolean trainingInSessionLogged;

  private void prepareAnalytics() {
    metricStore = new TalkBackMetricStore(this);
    trainingLogged = false;
  }

  private void logEnterPages(TrainingConfig trainingConfig, int pageNumber) {
    FormFactorUtils formFactorUtils = FormFactorUtils.getInstance();
    if (formFactorUtils.isAndroidAuto()
        || formFactorUtils.isAndroidTv()
        || formFactorUtils.isAndroidWear()) {
      // Currently count only mobile phone device's metric.
      return;
    }
    if (!docPageToMetric.containsKey(trainingConfig.getName())) {
      return;
    }
    @TrainingSectionId int logEvent;
    logEvent = docPageToMetric.get(trainingConfig.getName());
    switch (logEvent) {
      case TRAINING_SECTION_ONBOARDING:
        if (trainingLogged) {
          return;
        }
        trainingLogged = true;
        break;
      case TRAINING_SECTION_TUTORIAL:
        PageConfig pageConfig = training.getPages().get(pageNumber);
        if (!docPageToMetric.containsKey(pageConfig.getPageNameResId())) {
          return;
        }
        logEvent = docPageToMetric.get(pageConfig.getPageNameResId());
        switch (logEvent) {
          case TRAINING_SECTION_TUTORIAL:
            trainingInSessionLogged = false;
            if (trainingLogged) {
              return;
            }
            break;
          case TRAINING_SECTION_TUTORIAL_BASIC_NAVIGATION:
          case TRAINING_SECTION_TUTORIAL_TEXT_EDITING:
          case TRAINING_SECTION_TUTORIAL_READING_NAVIGATION:
          case TRAINING_SECTION_TUTORIAL_VOICE_COMMAND:
          case TRAINING_SECTION_TUTORIAL_EVERYDAY_TASKS:
          case TRAINING_SECTION_TUTORIAL_PRACTICE_GESTURES:
            if (trainingInSessionLogged) {
              return;
            }
            trainingInSessionLogged = true;
            break;
          default:
            return;
        }
        trainingLogged = true;
        break;
      default:
        return;
    }
    metricStore.onTutorialEntered(logEvent);
  }

  @Override
  public void onPageSwitched(
      int pageNumber,
      @Nullable Pair<Integer, Integer> shownPageNumber,
      Function<Context, NavigationButtonBar> navbarSupplier) {
    if (training != null) {
      logEnterPages(training, pageNumber);
    }

    PageConfig targetPage = training.getPages().get(pageNumber);
    boolean hasNavigationButtonBar = targetPage.hasNavigationButtonBar();

    TrainingFragment fragment = createFragment(targetPage, shownPageNumber);
    if (fragment == null) {
      LogUtils.e(TAG, "Cannot create a fragment in training page switch phrase.");
      return;
    }

    if (navigationBarContainer == null && hasNavigationButtonBar) {
      // We delegate rendering navigation bar to fragment if this layout doesn't have navigation bar
      // container.
      fragment.setNavigationButtonBarSupplier(navbarSupplier);
    }
    FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
    transaction.replace(ROOT_RES_ID, fragment);
    transaction.commitNow();

    if (hasNavigationButtonBar) {
      createNavigationBar(navbarSupplier);
    } else {
      // There is no button on the index page.
      removeNavigationBar();
    }

    // Announces page title to notify the page is changed.
    if (!formFactorUtils.isAndroidTv()) {
      String pageTitle = targetPage.getPageNameFromStringOrRes(this);
      setWindowTitle(pageTitle);
    }

    // On TV, display an image if one is defined for current page.
    if (formFactorUtils.isAndroidTv()) {
      ExternalDrawableResource image = targetPage.getImage();
      ImageView imageView = findViewById(R.id.tv_training_image);
      if (image != null) {
        try {
          Drawable drawable =
              ResourcesCompat.getDrawable(
                  getPackageManager().getResourcesForApplication(image.packageName()),
                  image.resourceId(),
                  null);
          imageView.setImageDrawable(drawable);
        } catch (NameNotFoundException e) {
          LogUtils.e(TAG, "Image not found.", e);
        }
        imageView.setVisibility(View.VISIBLE);
      } else {
        imageView.setVisibility(View.GONE);
      }
    }

    // Passes a page ID to TalkBackService.
    passPageIdToService(targetPage.getPageId());
  }

  /**
   * Goes back to the previous page.
   *
   * @return false if this is the first page. The other page will return true.
   */
  @CanIgnoreReturnValue
  public boolean goBackPreviousPage() {
    return pageController.previousPage();
  }

  @Nullable
  private TrainingFragment createFragment(
      PageConfig targetPage, @Nullable Pair<Integer, Integer> shownPageNumber) {

    if (ipcClient == null) {
      LogUtils.e(
          TAG,
          "Null IpcClient implies that TalkBack is off and we don't launch tutorial when the"
              + " TalkBack is off.");
      finishOnAbort(/* userInitiated= */ false);
      return null;
    }

    Bundle args = new Bundle();

    // Passes a PageId which is an enum instead of a PageConfig to avoid the serialization problem.
    args.putSerializable(TrainingFragment.EXTRA_PAGE, targetPage.getPageId());
    if (shownPageNumber != null) {
      args.putInt(TrainingFragment.EXTRA_PAGE_NUMBER, shownPageNumber.first);
      args.putInt(TrainingFragment.EXTRA_TOTAL_NUMBER, shownPageNumber.second);
    }
    args.putInt(TrainingFragment.EXTRA_VENDOR_PAGE_INDEX, targetPage.getVendorPageIndex());

    TrainingFragment fragment = new TrainingFragment();
    fragment.setArguments(args);
    fragment.setLinkHandler((first) -> pageController.handleLink(first));
    fragment.setData(ipcClient.getServiceData());
    fragment.setMetricStore(metricStore);
    return fragment;
  }

  @Override
  protected void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    prepareAnalytics();
    initialize(getIntent());
    getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
  }

  @Override
  protected void onNewIntent(Intent intent) {
    super.onNewIntent(intent);
    initialize(intent);
  }

  @Override
  protected void onStart() {
    super.onStart();
  }

  @Override
  protected void onResume() {
    super.onResume();
    if (ipcClient != null) {
      ipcClient.bindService();
    }
  }

  @Override
  protected void onPause() {
    super.onPause();
    passPageIdToService(PAGE_ID_FINISHED);
    if (ipcClient != null) {
      ipcClient.unbindService();
    }
  }

  View wrapWithSwipeHandler(View root) {
    if (formFactorUtils.isAndroidWear()) {
      return WrapSwipeDismissLayoutHelper.wrapSwipeDismissLayout(this, root, navigationController);
    } else {
      return root;
    }
  }

  @Override
  @SuppressWarnings("MissingSuperCall")
  public void onBackPressed() {
    navigationController.onBackPressed();
  }

  /** Returns an intent to show the given training on {@link TrainingActivity}. */
  public static Intent createTrainingIntent(Context context, TrainingId training) {
    return createTrainingIntent(context, training, false);
  }

  /** Returns an intent to show the given training on {@link TrainingActivity}. */
  public static Intent createTrainingIntent(
      Context context, TrainingId training, boolean showExitBanner) {
    Intent intent = new Intent(context, TrainingActivity.class);
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
    if (FormFactorUtils.getInstance().isAndroidWear()) {
      // Wear platform prefers to not push AAS to the recent.
      intent.addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
    }
    // Passes a TrainingId which is an enum instead of a TrainingConfig to avoid the serialization
    // problem.
    intent.putExtra(TrainingActivity.EXTRA_TRAINING, training);
    intent.putExtra(EXTRA_TRAINING_SHOW_EXIT_BANNER, showExitBanner);
    intent.setPackage(TALKBACK_PACKAGE);
    return intent;
  }

  @VisibleForTesting
  @Nullable
  PageConfig getCurrentPage() {
    return training == null ? null : pageController.getCurrentPageConfig();
  }

  @Nullable
  private PageId getCurrentPageId() {
    @Nullable PageConfig currentPage = getCurrentPage();
    return currentPage == null ? null : currentPage.getPageId();
  }

  private static boolean isTalkBackEnabled(Context context) {
    return AccessibilityServiceCompatUtils.isAccessibilityServiceEnabled(
        context, TALKBACK_SERVICE.flattenToShortString());
  }

  /** Passes the current page ID to TalkBack. */
  private void passPageIdToService(@Nullable PageId pageId) {
    if (pageId == null) {
      return;
    }

    Message message = Message.obtain(null, MSG_TRAINING_PAGE_SWITCHED);
    Bundle data = new Bundle();
    data.putSerializable(EXTRA_TRAINING_PAGE_ID, pageId);
    message.setData(data);
    sendMessageToService(message);
  }

  /** Asks TalkBack about gesture information. */
  private void requestGesturesFromService() {
    sendMessageToService(Message.obtain(null, MSG_REQUEST_GESTURES));
  }

  /** Notifies that the user requests to finish the training. */
  private void notifyTrainingFinishByUser() {
    sendMessageToService(Message.obtain(null, MSG_TRAINING_FINISH));
  }

  /**
   * Requests TalkBack disable services from tutorial. And it will also request disabling tutorial
   * UI at the same time.
   */
  public void onRequestDisableTalkBack() throws InterruptedException {
    sendMessageToService(Message.obtain(null, MSG_REQUEST_DISABLE_TALKBACK));
    new Handler().postDelayed(() -> finishOnAbort(false), 1000);
  }

  /** Asks TalkBack about available features. */
  private void requestAvailableFeatures() {
    sendMessageToService(Message.obtain(null, MSG_REQUEST_AVAILABLE_FEATURES));
  }

  /** Sends a message to {@link IpcService} or throws exception. */
  public void checkAndSendMessageToService(Message message) {
    switch (message.what) {
      case MSG_DOWNLOAD_ICON_DETECTION: // fall-through
      case MSG_DOWNLOAD_IMAGE_DESCRIPTION:
        {
          sendMessageToService(message);
          return;
        }
      default:
        throw new IllegalArgumentException(String.format("Unknown message. what=%s", message.what));
    }
  }

  /** Initializes activity. */
  private void initialize(Intent intent) {
    // Shows a warning dialog if TalkBack is off.
    if (!isTalkBackEnabled(this)) {
      // We don't use TalkBackService.getInstance here because 1. It's bad to expose TalkBack
      // service itself through a global method. 2. When the TrainingActivity is running on a
      // separate process, it's not applicable to access TalkBack's identifiers which is running on
      // another process.
      A11yAlertDialogWrapper.alertDialogBuilder(this, getSupportFragmentManager())
          .setTitle(R.string.talkback_inactive_title)
          .setMessage(R.string.talkback_inactive_message)
          .setCancelable(true)
          .setOnCancelListener(dialog -> finishOnAbort(/* userInitiated= */ true))
          .setPositiveButton(R.string.training_close_button, (dialog, which) -> finish())
          .create()
          .show();
      return;
    }

    @Nullable TrainingConfig training = getTrainingFromIntent(intent);
    if (training == null) {
      finishOnAbort(/* userInitiated= */ false);
      return;
    }

    boolean showExitBanner = intent.getBooleanExtra(EXTRA_TRAINING_SHOW_EXIT_BANNER, false);

    if (ipcClient == null) {
      ipcClient =
          new TrainingIpcClient(
              getApplicationContext(),
              this,
              showExitBanner,
              () -> {
                passPageIdToService(getCurrentPageId());
                requestGesturesFromService();
                requestAvailableFeatures();
              });
    }
    ipcClient.bindService();

    setupTrainingView(training);
  }

  @Override
  public void onIpcServerDestroyed() {
    finishOnAbort(/* userInitiated= */ false);
  }

  @Nullable
  protected TrainingConfig getTrainingFromIntent(Intent intent) {
    // The TV tutorial may be invoked by an intent from the outside, which does not contain an
    // extra with the TrainingId. Since there is only one training on TV, there is no doubt on
    // which training should be started. For Non-TV, no training must be started from the outside.
    if (Objects.equals(intent.getAction(), ACTION_START)) {
      if (formFactorUtils.isAndroidTv()) {
        return trainingActivityInterfaceInjector.getTraining(
            TrainingId.TRAINING_ID_TUTORIAL_FOR_TV, this);
      } else {
        return null;
      }
    }

    @Nullable TrainingId trainingId = (TrainingId) intent.getSerializableExtra(EXTRA_TRAINING);
    if (trainingId == null) {
      return null;
    }

    return trainingActivityInterfaceInjector.getTraining(trainingId, this);
  }

  /** Sets up the action bar and the page controller. */
  @VisibleForTesting
  void setupTrainingView(@NonNull TrainingConfig training) {
    setContentView(R.layout.training_activity);

    this.training = training;
    pageController = new PageController(training, /* onPageChangeCallback= */ this, metricStore);
    navigationController = new NavigationController(this, pageController);
    navigationBarContainer = findViewById(R.id.nav_container);

    setupToolbarIfNecessary();

    // Shows the first page.
    pageController.initialize();
  }

  private void setupToolbarIfNecessary() {
    Toolbar toolbar = findViewById(R.id.training_toolbar);

    if (toolbar == null) {
      return;
    }

    if (training.isSupportNavigateUpArrow()) {
      try {
        // Shows navigate up arrow.
        setActionBar(toolbar);

        toolbar.setNavigationIcon(R.drawable.ic_arrow_back_gm_grey_24dp);
        toolbar.setNavigationContentDescription(R.string.training_navigate_up);
        toolbar.setNavigationOnClickListener(navigationController::onNavigationClick);
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
      // Removes action bar (if it exists).
      LinearLayout layout = findViewById(R.id.training_toolbar_layout);
      if (layout != null) {
        layout.removeView(toolbar);
      }
    }
  }

  /** Sets and announces window title. */
  private void setWindowTitle(String pageTitle) {
    // Set the same window title as the displayed title, which is the first element of a page, to
    // avoid announcing the title twice. Then, the focus will land on the next element after the
    // title.
    setTitle(pageTitle);
    // TODO: Until TalkBack supports managing the focus position by pane-changed
    // events, not use accessibility-pane on the tutorial.
    //  ViewCompat.setAccessibilityPaneTitle(findViewById(R.id.training_root), pageTitle);
  }

  private void showExitDialog() {
    // Statistic showing training-exit dialog due to Close action.
    if (metricStore != null) {
      metricStore.onTutorialEvent(TRAINING_BUTTON_CLOSE);
    }

    A11yAlertDialogWrapper.alertDialogBuilder(this, getSupportFragmentManager())
        .setTitle(R.string.exit_tutorial_title)
        .setMessage(R.string.exit_tutorial_content)
        .setCancelable(true)
        .setPositiveButton(
            R.string.training_close_button,
            (dialog, which) -> finishOnAbort(/* userInitiated= */ true))
        .setNegativeButton(R.string.stay_in_tutorial_button, (dialog, which) -> dialog.dismiss())
        .create()
        .show();
  }

  /** Creates new navigation buttons. */
  private void createNavigationBar(Function<Context, NavigationButtonBar> supplier) {
    if (training == null) {
      return;
    }

    if (navigationBarContainer != null) {
      navigationBarContainer.removeView(navigationButtonBar);
      navigationButtonBar = supplier.apply(this);
      navigationBarContainer.addView(navigationButtonBar);
    }
  }

  /** Removes navigation buttons. */
  private void removeNavigationBar() {
    if (navigationBarContainer != null) {
      navigationBarContainer.removeView(navigationButtonBar);
      navigationButtonBar = null;
    }
  }

  /**
   * Sends the message to {@link IpcService}.
   *
   * <p>This method has responsibility to check the service state.
   */
  private void sendMessageToService(Message message) {
    // Do not send the data to the IpcService if TalkBack is not enabled.
    if (!isTalkBackEnabled(getApplicationContext()) || ipcClient == null) {
      return;
    }
    ipcClient.sendMessage(message);
  }

  @VisibleForTesting
  @Nullable
  NavigationButtonBar getNavigationButtonBar() {
    return navigationButtonBar;
  }

  void moveInputFocusToNavButtons() {
    if (navigationButtonBar != null) {
      navigationButtonBar.requestFocus();
    }
  }

  @VisibleForTesting
  void setIpcClient(TrainingIpcClient ipcClient) {
    this.ipcClient = ipcClient;
  }
}
