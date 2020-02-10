/*
 * Copyright (C) 2015 Google Inc.
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

package com.android.switchaccess;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.os.Trace;
import android.os.UserManager;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.accessibility.AccessibilityEvent;
import com.google.android.accessibility.compositor.Compositor;
import com.google.android.accessibility.compositor.GlobalVariables;
import com.google.android.accessibility.switchaccess.AutoScanController;
import com.google.android.accessibility.switchaccess.FeatureFlags;
import com.google.android.accessibility.switchaccess.OptionManager;
import com.google.android.accessibility.switchaccess.PerformanceMonitor;
import com.google.android.accessibility.switchaccess.PerformanceMonitor.KeyPressEvent;
import com.google.android.accessibility.switchaccess.PointScanManager;
import com.google.android.accessibility.switchaccess.SwitchAccessLogger;
import com.google.android.accessibility.switchaccess.SwitchAccessPreferenceCache;
import com.google.android.accessibility.switchaccess.SwitchAccessPreferenceCache.SwitchAccessPreferenceChangedListener;
import com.google.android.accessibility.switchaccess.SwitchAccessPreferenceUtils;
import com.google.android.accessibility.switchaccess.UiChangeDetector;
import com.google.android.accessibility.switchaccess.UiChangeHandler;
import com.google.android.accessibility.switchaccess.UiChangeStabilizer;
import com.google.android.accessibility.switchaccess.feedback.SwitchAccessAccessibilityEventFeedbackController;
import com.google.android.accessibility.switchaccess.feedback.SwitchAccessActionFeedbackController;
import com.google.android.accessibility.switchaccess.feedback.SwitchAccessFeedbackController;
import com.google.android.accessibility.switchaccess.feedback.SwitchAccessHighlightFeedbackController;
import com.google.android.accessibility.switchaccess.keyassignment.KeyAssignmentUtils;
import com.google.android.accessibility.switchaccess.keyboardactions.KeyboardEventManager;
import com.google.android.accessibility.switchaccess.setupwizard.SetupWizardActivity;
import com.google.android.accessibility.switchaccess.treebuilding.MainTreeBuilder;
import com.google.android.accessibility.switchaccess.ui.OverlayController;
import com.google.android.accessibility.utils.ScreenMonitor;
import com.google.android.accessibility.utils.SharedKeyEvent;
import com.google.android.accessibility.utils.feedback.AccessibilityHintsManager;
import com.google.android.accessibility.utils.input.InputModeManager;
import com.google.android.accessibility.utils.output.FeedbackController;
import com.google.android.accessibility.utils.output.SpeechController;
import com.google.android.accessibility.utils.output.SpeechControllerImpl;
import com.google.android.accessibility.utils.widget.SimpleOverlay;
import com.google.android.libraries.accessibility.utils.concurrent.ThreadUtils;
import com.google.android.libraries.accessibility.utils.eventfilter.AccessibilityEventFilter;
import com.google.android.libraries.accessibility.utils.log.LogUtils;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.dataflow.qual.SideEffectFree;

/**
 * Enable a user to perform touch gestures using keyboards with only a small number of keys. These
 * keyboards may be adapted to match a user's capabilities, for example with very large buttons,
 * proximity sensors around the head, or sip/puff switches.
 */
public class SwitchAccessService extends AccessibilityService
    implements SwitchAccessPreferenceChangedListener,
        SharedKeyEvent.Listener,
        SpeechController.Delegate {

  private static final String TAG = "SAService";

  private UiChangeStabilizer uiChangeStabilizer;
  private UiChangeDetector eventProcessor;
  private UiChangeHandler uiChangeHandler;
  private AccessibilityEventFilter accessibilityEventFilter;

  private SwitchAccessLogger analytics;

  private WakeLock wakeLock;

  @Nullable private static SwitchAccessService instance;

  private OverlayController overlayController;
  private OptionManager optionManager;
  private AutoScanController autoScanController;
  private PointScanManager pointScanManager;
  private KeyboardEventManager keyboardEventManager;
  private SwitchAccessFeedbackController switchAccessFeedbackController;
  /** {@link BroadcastReceiver} for tracking the screen status. */
  private ScreenMonitor screenMonitor;

  private boolean spokenFeedbackEnabledInServiceInfo;
  private boolean hapticFeedbackEnabledInServiceInfo;
  private boolean auditoryFeedbackEnabledInServiceInfo;

  private HandlerThread handlerThread =
      new HandlerThread("BackgroundThread", android.os.Process.THREAD_PRIORITY_BACKGROUND);

  private final BroadcastReceiver userUnlockedBroadcastReceiver =
      new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
          analytics = SwitchAccessLogger.getOrCreateInstance(SwitchAccessService.this);
          unregisterReceiver(userUnlockedBroadcastReceiver);
        }
      };

  private final BroadcastReceiver screenOffBroadcastReceiver =
      new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
          String action = intent.getAction();
          if ((action != null) && (action.equals(Intent.ACTION_SCREEN_OFF))) {
            overlayController.clearMenuOverlay();
          }
        }
      };

  @Override
  public void onCreate() {
    super.onCreate();

    if (KeyAssignmentUtils.areKeysAssigned(this)) {
      PerformanceMonitor.getOrCreateInstance()
          .initializePerformanceMonitoringIfNotInitialized(this, getApplication());
    }

    handlerThread.start();
  }

  @Override
  public boolean onUnbind(Intent intent) {
    ThreadUtils.removeCallbacksAndMessages(null);
    if (instance != null) {
      unregisterReceiver(screenOffBroadcastReceiver);
    }

    instance = null;
    try {
      autoScanController.stopScan();
      uiChangeStabilizer.shutdown();
      uiChangeHandler.shutdown();
    } catch (NullPointerException e) {
      // Ignore.
    }
    return super.onUnbind(intent);
  }

  @Override
  public void onDestroy() {
    SharedKeyEvent.unregister(this);
    if (analytics != null) {
      analytics.stop(this);
    }
    SwitchAccessPreferenceUtils.unregisterSwitchAccessPreferenceChangedListener(this);
    PerformanceMonitor.getOrCreateInstance().shutdown();

    // Check for nullness before shutting down as these can be null during Robolectric testing.
    if (optionManager != null) {
      optionManager.shutdown();
    }

    if (overlayController != null) {
      overlayController.shutdown();
    }

    if (pointScanManager != null) {
      pointScanManager.shutdown();
    }

    if (switchAccessFeedbackController != null) {
      switchAccessFeedbackController.shutdown();
    }

    if (screenMonitor != null) {
      unregisterReceiver(screenMonitor);
    }

    SwitchAccessPreferenceCache.shutdownIfInitialized(this);
    instance = null;
    super.onDestroy();
  }

  @Override
  public void onAccessibilityEvent(AccessibilityEvent event) {
    Trace.beginSection("SwitchAccessService#onAccessibilityEvent");
    // Only process the AccessibilityEvents when the screen is on.
    if (screenMonitor != null && screenMonitor.isScreenOn()) {
      if (eventProcessor != null) {
        eventProcessor.onAccessibilityEvent(event);
      }
      if (accessibilityEventFilter != null) {
        accessibilityEventFilter.onAccessibilityEvent(event);
      }
    }
    Trace.endSection();
  }

  @Override
  public void onInterrupt() {
    /* TODO: Will this ever be called? */
  }

  /** @return The active SwitchingControl instance or {@code null} if not available. */
  @Nullable
  @SideEffectFree
  public static SwitchAccessService getInstance() {
    return instance;
  }

  /**
   * Intended to mimic the behavior of onKeyEvent if this were the only service running. It will be
   * called from onKeyEvent, both from this service and from others in this apk (TalkBack). This
   * method must not block, since it will block onKeyEvent as well.
   *
   * @param keyEvent A key event
   * @return {@code true} if the event is handled, {@code false} otherwise.
   */
  @Override
  public boolean onKeyEventShared(KeyEvent keyEvent) {
    Trace.beginSection("SwitchAccessService#onKeyEventShared");
    if (keyEvent.getAction() == KeyEvent.ACTION_DOWN) {
      PerformanceMonitor.getOrCreateInstance()
          .startNewTimerEvent(KeyPressEvent.UNKNOWN_KEY_ASSIGNMENT);
    }

    if (keyboardEventManager.onKeyEvent(keyEvent, analytics, this)) {
      wakeLock.acquire();
      wakeLock.release();

      Trace.endSection();
      return true;
    }
    Trace.endSection();
    return false;
  }

  /**
   * Alert the event processor that the user has initiated a screen change. This method should only
   * be called after a key press that results in a screen change (e.g. global action, starting a
   * scan, clicking an item, etc.)
   */
  public void onUserInitiatedScreenChange() {
    /*
     * This is needed when we pull down the notification shade. It also only works because the key
     * event handling is delayed to see if the UI needs to stabilize.
     */
    if (!SwitchAccessPreferenceUtils.isPointScanEnabled(this) && (eventProcessor != null)) {
      eventProcessor.onUserClick();
    }
  }

  /*
   * FULL_WAKE_LOCK has been deprecated, but the replacement only works for applications. The docs
   * actually say that using wake locks from a service is a legitimate use case.
   */
  @SuppressWarnings("deprecation")
  @Override
  protected void onServiceConnected() {
    // Enable verbose logging in dev builds.
    if (FeatureFlags.devBuildLogging()) {
      LogUtils.setLogLevel(Log.VERBOSE);
    }

    AccessibilityServiceInfo accessibilityServiceInfo = getServiceInfo();
    if (accessibilityServiceInfo != null) {
      spokenFeedbackEnabledInServiceInfo =
          ((accessibilityServiceInfo.feedbackType & AccessibilityServiceInfo.FEEDBACK_SPOKEN) != 0);
      hapticFeedbackEnabledInServiceInfo =
          ((accessibilityServiceInfo.feedbackType & AccessibilityServiceInfo.FEEDBACK_HAPTIC) != 0);
      auditoryFeedbackEnabledInServiceInfo =
          ((accessibilityServiceInfo.feedbackType & AccessibilityServiceInfo.FEEDBACK_AUDIBLE)
              != 0);
      updateServiceInfoIfFeedbackTypeChanged();
    } else {
      spokenFeedbackEnabledInServiceInfo = false;
      hapticFeedbackEnabledInServiceInfo = false;
      auditoryFeedbackEnabledInServiceInfo = false;
    }

    // Make sure that the soft keyboard can be shown when a hardware key is connected. Only
    // supported on Q+.
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      getSoftKeyboardController().setShowMode(AccessibilityService.SHOW_MODE_IGNORE_HARD_KEYBOARD);
    }

    SimpleOverlay highlightOverlay = new SimpleOverlay(this);
    SimpleOverlay globalMenuButtonOverlay = new SimpleOverlay(this, 0, true);
    SimpleOverlay menuOverlay = new SimpleOverlay(this, 0, true);
    SimpleOverlay screenSwitchOverlay = new SimpleOverlay(this, 0, true);
    overlayController =
        new OverlayController(
            highlightOverlay, globalMenuButtonOverlay, menuOverlay, screenSwitchOverlay);
    overlayController.configureOverlays();
    configureScreenSwitch();

    FeedbackController feedbackController = new FeedbackController(this);
    SpeechControllerImpl speechController =
        new SpeechControllerImpl(this, this, feedbackController);
    GlobalVariables globalVariables = new GlobalVariables(this, new InputModeManager(), null);
    Compositor compositor =
        new Compositor(
            this, speechController, null, globalVariables, Compositor.FLAVOR_SWITCH_ACCESS);
    AccessibilityHintsManager hintsManager = new AccessibilityHintsManager(speechController);
    SwitchAccessHighlightFeedbackController highlightFeedbackController =
        new SwitchAccessHighlightFeedbackController(
            this, compositor, speechController, hintsManager);
    SwitchAccessActionFeedbackController actionFeedbackController =
        new SwitchAccessActionFeedbackController(this, speechController, feedbackController);
    SwitchAccessAccessibilityEventFeedbackController accessibilityEventFeedbackController =
        new SwitchAccessAccessibilityEventFeedbackController(
            this, compositor, globalVariables, speechController, feedbackController, hintsManager);
    switchAccessFeedbackController =
        new SwitchAccessFeedbackController(
            this,
            compositor,
            speechController,
            feedbackController,
            globalVariables,
            hintsManager,
            highlightFeedbackController,
            actionFeedbackController,
            accessibilityEventFeedbackController,
            new Handler());

    optionManager = new OptionManager(overlayController, switchAccessFeedbackController);
    pointScanManager = new PointScanManager(overlayController, this);
    autoScanController =
        new AutoScanController(optionManager, switchAccessFeedbackController, new Handler(), this);
    keyboardEventManager =
        new KeyboardEventManager(this, optionManager, autoScanController, pointScanManager);
    PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
    if (powerManager != null) {
      wakeLock =
          powerManager.newWakeLock(
              PowerManager.FULL_WAKE_LOCK | PowerManager.ON_AFTER_RELEASE, "SwitchAccess:");
    }
    screenMonitor = new ScreenMonitor(powerManager);
    registerReceiver(screenMonitor, screenMonitor.getFilter());
    screenMonitor.updateScreenState();

    SwitchAccessPreferenceUtils.registerSwitchAccessPreferenceChangedListener(this, this);
    uiChangeHandler =
        new UiChangeHandler(
            this,
            new MainTreeBuilder(this),
            optionManager,
            overlayController,
            pointScanManager,
            new Handler(handlerThread.getLooper()));
    uiChangeStabilizer = new UiChangeStabilizer(uiChangeHandler, switchAccessFeedbackController);
    eventProcessor = new UiChangeDetector(uiChangeStabilizer);
    accessibilityEventFilter = new AccessibilityEventFilter(switchAccessFeedbackController);
    SharedKeyEvent.register(this);

    overlayController.setGlobalMenuButtonListener(uiChangeStabilizer);

    // Register a broadcast receiver so that we can clear the menu overlay when the screen turns
    // off.
    IntentFilter intentFilter = new IntentFilter(Intent.ACTION_SCREEN_OFF);
    registerReceiver(screenOffBroadcastReceiver, intentFilter);

    UserManager userManager = (UserManager) getSystemService(Context.USER_SERVICE);
    if ((Build.VERSION.SDK_INT < Build.VERSION_CODES.N)
        || ((userManager != null) && userManager.isUserUnlocked())) {
      analytics = SwitchAccessLogger.getOrCreateInstance(this);
      overlayController.addMenuListener(analytics);
      if (!KeyAssignmentUtils.areKeysAssigned(this)) {
        Intent intent = new Intent(this, SetupWizardActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        // Although the Activity is inside this apk and will resolve with PackageManager, it
        // cannot be started until after the device has completed booting.
        try {
          SwitchAccessService.this.startActivity(intent);
        } catch (ActivityNotFoundException e) {
          // Ignore.
        }
      }
    } else {
      // Register a broadcast receiver, so we know when the user unlocks the device
      IntentFilter filter = new IntentFilter();
      filter.addAction(Intent.ACTION_USER_UNLOCKED);
      this.registerReceiver(userUnlockedBroadcastReceiver, filter);
    }

    // Don't set the instance until the service has finished connecting. Otherwise,
    // BadTokenExceptions can occur before the service is fully set up.
    instance = this;
  }

  @Override
  protected boolean onKeyEvent(KeyEvent keyEvent) {
    Trace.beginSection("SwitchAccessService#onKeyEvent");
    boolean wasKeyEventProcessed = SharedKeyEvent.onKeyEvent(this, keyEvent);
    Trace.endSection();
    return wasKeyEventProcessed;
  }

  @Override
  public void onPreferenceChanged(SharedPreferences prefs, String key) {
    Trace.beginSection("SwitchAccessService#onPreferenceChanged");
    updateServiceInfoIfFeedbackTypeChanged();
    LogUtils.d(TAG, "A shared preference changed: %s", key);
    keyboardEventManager.reloadPreferences(this);

    // TODO: Refactor this out of SwitchAccessService.
    if (SwitchAccessPreferenceUtils.isScreenSwitchEnabled(this)) {
      overlayController.showScreenSwitch();
    } else {
      overlayController.hideScreenSwitch();
    }
    Trace.endSection();
  }

  /** Get Option Manager. Used by Analytics. */
  public OptionManager getOptionManager() {
    return optionManager;
  }

  /** Get Point Scan Manager. Used by Analytics. */
  public PointScanManager getPointScanManager() {
    return pointScanManager;
  }

  /** Get OverlayController. Used by Analytics. */
  public OverlayController getOverlayController() {
    return overlayController;
  }

  @Override
  public boolean isAudioPlaybackActive() {
    return false;
  }

  @Override
  public boolean isMicrophoneActiveAndHeadphoneOff() {
    return false;
  }

  @Override
  public boolean isSsbActiveAndHeadphoneOff() {
    return false;
  }

  @Override
  public boolean isPhoneCallActive() {
    return false;
  }

  @Override
  public void onSpeakingForcedFeedback() {}

  private void updateServiceInfoIfFeedbackTypeChanged() {
    boolean spokenFeedbackEnabled = SwitchAccessPreferenceUtils.isSpokenFeedbackEnabled(this);
    boolean hapticFeedbackEnabled = SwitchAccessPreferenceUtils.shouldPlayVibrationFeedback(this);
    boolean auditoryFeedbackEnabled = SwitchAccessPreferenceUtils.shouldPlaySoundFeedback(this);

    if ((spokenFeedbackEnabled == spokenFeedbackEnabledInServiceInfo)
        && (hapticFeedbackEnabled == hapticFeedbackEnabledInServiceInfo)
        && (auditoryFeedbackEnabled == auditoryFeedbackEnabledInServiceInfo)) {
      return;
    }

    AccessibilityServiceInfo serviceInfo = getServiceInfo();
    if (serviceInfo == null) {
      LogUtils.e(TAG, "Failed to update feedback type, service info was null");
      return;
    }

    if (spokenFeedbackEnabled) {
      serviceInfo.feedbackType |= AccessibilityServiceInfo.FEEDBACK_SPOKEN;
    } else {
      serviceInfo.feedbackType &= ~AccessibilityServiceInfo.FEEDBACK_SPOKEN;
    }
    if (hapticFeedbackEnabled) {
      serviceInfo.feedbackType |= AccessibilityServiceInfo.FEEDBACK_HAPTIC;
    } else {
      serviceInfo.feedbackType &= ~AccessibilityServiceInfo.FEEDBACK_HAPTIC;
    }
    if (auditoryFeedbackEnabled) {
      serviceInfo.feedbackType |= AccessibilityServiceInfo.FEEDBACK_AUDIBLE;
    } else {
      serviceInfo.feedbackType &= ~AccessibilityServiceInfo.FEEDBACK_AUDIBLE;
    }
    setServiceInfo(serviceInfo);

    spokenFeedbackEnabledInServiceInfo = spokenFeedbackEnabled;
    hapticFeedbackEnabledInServiceInfo = hapticFeedbackEnabled;
    auditoryFeedbackEnabledInServiceInfo = auditoryFeedbackEnabled;
  }

  private void configureScreenSwitch() {
    overlayController.setScreenSwitchOnTouchListener(
        (view, event) -> {
          if (event.getAction() == MotionEvent.ACTION_DOWN) {
            return onKeyEventShared(KeyAssignmentUtils.SCREEN_SWITCH_EVENT_DOWN);
          } else {
            return onKeyEventShared(KeyAssignmentUtils.SCREEN_SWITCH_EVENT_UP);
          }
        });

    if (SwitchAccessPreferenceUtils.isScreenSwitchEnabled(this)) {
      overlayController.showScreenSwitch();
    }
  }

  // TODO: Investigate making this non-static.
  /**
   * Returns {@code true} if the service is active. This will be true when {@link
   * SwitchAccessService#getInstance} does not return null.
   */
  public static boolean isActive() {
    return getInstance() != null;
  }
}

