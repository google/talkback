/*
 * Copyright 2019 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.android.accessibility.brailleime;

import static com.google.android.accessibility.braille.common.BrailleUserPreferences.BRAILLE_SHARED_PREFS_FILENAME;
import static com.google.android.accessibility.braille.common.BrailleUserPreferences.getCurrentTypingLanguageType;
import static com.google.android.accessibility.braille.common.ImeConnection.AnnounceType.HIDE_PASSWORD;
import static com.google.android.accessibility.braille.common.ImeConnection.AnnounceType.NORMAL;
import static com.google.android.accessibility.braille.common.ImeConnection.AnnounceType.SILENCE;
import static com.google.android.accessibility.brailleime.tutorial.TutorialView.TutorialState.State.INTRO;
import static com.google.android.accessibility.brailleime.tutorial.TutorialView.TutorialState.State.NONE;
import static com.google.android.accessibility.utils.AccessibilityServiceCompatUtils.isAccessibilityServiceEnabled;
import static com.google.android.accessibility.utils.input.CursorGranularity.CHARACTER;
import static com.google.android.accessibility.utils.input.CursorGranularity.LINE;
import static com.google.android.accessibility.utils.input.CursorGranularity.PARAGRAPH;
import static com.google.android.accessibility.utils.input.CursorGranularity.WORD;

import android.app.KeyguardManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.database.ContentObserver;
import android.graphics.Rect;
import android.graphics.Region;
import android.inputmethodservice.InputMethodService;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.provider.Settings;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Size;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import com.google.android.accessibility.braille.common.BrailleCommonTalkBackSpeaker;
import com.google.android.accessibility.braille.common.BrailleCommonUtils;
import com.google.android.accessibility.braille.common.BrailleUserPreferences;
import com.google.android.accessibility.braille.common.BrailleUtils;
import com.google.android.accessibility.braille.common.FeedbackManager;
import com.google.android.accessibility.braille.common.ImeConnection;
import com.google.android.accessibility.braille.common.ImeConnection.AnnounceType;
import com.google.android.accessibility.braille.common.TalkBackSpeaker;
import com.google.android.accessibility.braille.common.TouchDots;
import com.google.android.accessibility.braille.common.translate.BrailleLanguages;
import com.google.android.accessibility.braille.common.translate.BrailleLanguages.Code;
import com.google.android.accessibility.braille.common.translate.EditBuffer;
import com.google.android.accessibility.braille.common.translate.EditBufferUtils;
import com.google.android.accessibility.braille.interfaces.BrailleCharacter;
import com.google.android.accessibility.braille.interfaces.BrailleDisplayForBrailleIme;
import com.google.android.accessibility.braille.interfaces.BrailleDisplayForBrailleIme.ResultForDisplay;
import com.google.android.accessibility.braille.interfaces.BrailleImeForBrailleDisplay;
import com.google.android.accessibility.braille.interfaces.BrailleImeForTalkBack;
import com.google.android.accessibility.braille.interfaces.BrailleWord;
import com.google.android.accessibility.braille.interfaces.ScreenReaderActionPerformer.ScreenReaderAction;
import com.google.android.accessibility.braille.interfaces.TalkBackForBrailleCommon;
import com.google.android.accessibility.braille.interfaces.TalkBackForBrailleIme;
import com.google.android.accessibility.braille.interfaces.TalkBackForBrailleIme.ServiceStatus;
import com.google.android.accessibility.braille.translate.BrailleTranslator;
import com.google.android.accessibility.braille.translate.TranslatorFactory;
import com.google.android.accessibility.brailleime.BrailleImeVibrator.VibrationType;
import com.google.android.accessibility.brailleime.LayoutOrientator.LayoutOrientatorCallback;
import com.google.android.accessibility.brailleime.analytics.BrailleImeAnalytics;
import com.google.android.accessibility.brailleime.dialog.ContextMenuDialog;
import com.google.android.accessibility.brailleime.dialog.TalkBackOffDialog;
import com.google.android.accessibility.brailleime.dialog.TalkBackSuspendDialog;
import com.google.android.accessibility.brailleime.dialog.TooFewTouchPointsDialog;
import com.google.android.accessibility.brailleime.dialog.ViewAttachedDialog;
import com.google.android.accessibility.brailleime.input.BrailleDisplayImeStripView;
import com.google.android.accessibility.brailleime.input.BrailleInputView;
import com.google.android.accessibility.brailleime.input.BrailleInputView.CalibrationTriggeredType;
import com.google.android.accessibility.brailleime.input.BrailleInputView.FingersPattern;
import com.google.android.accessibility.brailleime.input.Swipe;
import com.google.android.accessibility.brailleime.keyboardview.AccessibilityOverlayKeyboardView;
import com.google.android.accessibility.brailleime.keyboardview.KeyboardView;
import com.google.android.accessibility.brailleime.keyboardview.KeyboardView.KeyboardViewCallback;
import com.google.android.accessibility.brailleime.keyboardview.StandardKeyboardView;
import com.google.android.accessibility.brailleime.settings.BrailleImePreferencesActivity;
import com.google.android.accessibility.brailleime.tutorial.TutorialView.TutorialCallback;
import com.google.android.accessibility.brailleime.tutorial.TutorialView.TutorialState.State;
import com.google.android.accessibility.utils.AccessibilityServiceCompatUtils.Constants;
import com.google.android.accessibility.utils.BuildVersionUtils;
import com.google.android.accessibility.utils.KeyboardUtils;
import com.google.android.accessibility.utils.PreferenceSettingsUtils;
import com.google.android.accessibility.utils.input.CursorGranularity;
import com.google.android.accessibility.utils.output.SpeechController;
import com.google.android.accessibility.utils.output.SpeechController.UtteranceCompleteRunnable;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableSet;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.lang.Thread.UncaughtExceptionHandler;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * An input method intended for blind/low-vision users that displays braille dot touch targets and
 * converts taps on those braille dots into print characters.
 *
 * <p>Since it is the root of the object graph and the class that has access to the {@link
 * InputConnection}, this class coordinates the flow of user input. Chronologically, that input
 * begins with the {@link BrailleInputView}, which is instantiated and owned by this class, and
 * which converts touch events into {@link BrailleInputView.Callback} callbacks, which are
 * implemented inside this class. In the case of {@link
 * BrailleInputView.Callback#onBrailleProduced(BrailleCharacter)} this class passes the {@link
 * BrailleCharacter} object to the {@link EditBuffer}, which holds a list of accumulated {@link
 * BrailleCharacter} until the time comes to translate braille to print and send it to the IME
 * Editor via the {@link InputConnection}.
 *
 * <p>Difficulty arises because the prototypical IME envisioned by the super class has an input area
 * with a candidates bar atop it, and possibly an extracted Editor view at the very top in case the
 * IME needs to be fullscreen. This IME differs from that protoype in two major ways:
 *
 * <ol>
 *   <li>It wants to be immersive - as fullscreen as possible
 *   <li>It has no need to display the underlying Editor nor an extracted Editor
 * </ol>
 *
 * <p>Therefore this class avoids the default View structure of the super class-provided Window in a
 * somewhat complex way, with the strategy depending on the version of the operating system. For
 * more information, see {@link KeyboardView}. One of these strategies involves adding a whole new
 * Window object to the WindowManager, and the other involves defining touch exploration passthrough
 * regions; both of these abilities rely on the BrailleIme being colocated with an
 * AccessibilityService, namely TalkBack. Indeed BrailleIme has a close dependency upon TalkBack,
 * which means that moving BrailleIme out of the TalkBack application would require much work.
 */
public class BrailleIme extends InputMethodService {

  private static final String TAG = "BrailleIme";

  // Follow the lifecycle of keyboard, onDestroy() when switching to other keyboard. onCreate() when
  // switching from other keyboard.
  @SuppressWarnings("NonFinalStaticField")
  private static BrailleIme instance;

  // A note on how the desired hiding of the default IME views is achieved:
  // - Hiding the candidatesArea is simple - simply do not override onCreateCandidatesView.
  // - Hiding the extractArea can be accomplished in either of two ways - either override
  // onEvaluateFullscreenMode() to always return false (which is counterintuitive since this IME is
  // to be fullscreen), or expand the bounds of the inputArea by overriding setInputView(View)
  // and making an ill-advised modification to the LayoutParams of the parent of the
  // BrailleInputView. This code uses the first of these two options; this allows our inputArea,
  // which we furnish in the override of onCreateInputView, to take up the entire view region.
  @SuppressWarnings("NonFinalStaticField")
  @Nullable
  private static TalkBackForBrailleIme talkBackForBrailleIme;

  @SuppressWarnings("NonFinalStaticField")
  @Nullable
  private static TalkBackForBrailleCommon talkBackForBrailleCommon;

  @SuppressWarnings("NonFinalStaticField")
  @Nullable
  private static BrailleDisplayForBrailleIme brailleDisplayForBrailleIme;

  private static final String BARD_PACKAGE_NAME = "gov.loc.nls.dtb";
  private static final int ANNOUNCE_DELAY_MS =
      800; // Delay, so that it follows previous-IME-is-hidden announcement.
  private static final int ANNOUNCE_CALIBRATION_DELAY_MS = 1500;
  private static final int CALIBRATION_EARCON_DELAY_MS = 500;
  private static final int CALIBRATION_EARCON_REPEAT_COUNT = 3;
  private static final int CALIBRATION_ANNOUNCEMENT_REPEAT_MS = 8000;

  // An Immutable set includes the granularities which are related to editing.
  private static final ImmutableSet<CursorGranularity> VALID_GRANULARITIES =
      ImmutableSet.of(CHARACTER, WORD, LINE, PARAGRAPH);

  private final AtomicInteger instructionSpeechId = new AtomicInteger();
  private boolean deviceSupportsAtLeast5Pointers;
  private State tutorialState;
  private EditBuffer editBuffer;
  private Thread.UncaughtExceptionHandler originalDefaultUncaughtExceptionHandler;
  private OrientationMonitor.Callback orientationCallbackDelegate;
  private ViewAttachedDialog talkbackOffDialog;
  private ViewAttachedDialog contextMenuDialog;
  private ViewAttachedDialog tooFewTouchPointsDialog;
  private ViewAttachedDialog talkBackSuspendDialog;
  private LayoutOrientator layoutOrientator;
  private EscapeReminder escapeReminder;
  private BrailleImeAnalytics brailleImeAnalytics;
  private KeyboardView keyboardView;
  private BrailleImeGestureController brailleImeGestureController;
  private TypoHandler typoHandler;
  private Handler mainHandler;
  private Handler calibrationAnnouncementHandler;
  private boolean brailleDisplayConnectedAndNotSuspended;
  private int orientation;
  private boolean isVisible;
  private FeedbackManager feedbackManager;

  /** An interface to notify orientation change. */
  public interface OrientationSensitive {
    void onOrientationChanged(int orientation, Size screenSize);
  }

  /** TalkBack invokes this to provide us with the TalkBackForBrailleIme instance. */
  public static void initialize(
      Context context,
      TalkBackForBrailleIme talkBackForBrailleIme,
      TalkBackForBrailleCommon talkBackForBrailleCommon,
      BrailleDisplayForBrailleIme brailleDisplayForBrailleIme) {
    BrailleIme.talkBackForBrailleIme = talkBackForBrailleIme;
    BrailleIme.talkBackForBrailleCommon = talkBackForBrailleCommon;
    BrailleIme.brailleDisplayForBrailleIme = brailleDisplayForBrailleIme;
    if (talkBackForBrailleIme != null) {
      talkBackForBrailleIme.setBrailleImeForTalkBack(
          instance == null ? null : instance.brailleImeForTalkBack);
    }
    if (instance != null && talkBackForBrailleCommon != null) {
      instance.feedbackManager =
          new FeedbackManager(talkBackForBrailleCommon.getFeedBackController());
    }
    BrailleCommonTalkBackSpeaker.getInstance().initialize(talkBackForBrailleCommon);
    BrailleImePreferencesActivity.initialize(talkBackForBrailleIme);
    Utils.setComponentEnabled(context, Constants.BRAILLE_KEYBOARD, true);
  }

  @Override
  public void onCreate() {
    super.onCreate();
    instance = this;
    BrailleImeLog.d(TAG, "onCreate");
    if (talkBackForBrailleCommon != null) {
      feedbackManager = new FeedbackManager(talkBackForBrailleCommon.getFeedBackController());
    }
    orientation = getResources().getConfiguration().orientation;
    readDeviceFeatures();
    mainHandler = new Handler();
    calibrationAnnouncementHandler = new Handler();
    if (brailleDisplayForBrailleIme != null) {
      brailleDisplayConnectedAndNotSuspended =
          brailleDisplayForBrailleIme.isBrailleDisplayConnectedAndNotSuspended();
    }
    keyboardView = createKeyboardView();
    escapeReminder = new EscapeReminder(this, escapeReminderCallback);
    talkbackOffDialog = new TalkBackOffDialog(this, talkBackOffDialogCallback);
    contextMenuDialog = new ContextMenuDialog(this, contextMenuDialogCallback);
    tooFewTouchPointsDialog = new TooFewTouchPointsDialog(this, tooFewTouchPointsDialogCallback);
    talkBackSuspendDialog = new TalkBackSuspendDialog(this, talkBackSuspendDialogCallback);
    tutorialState = NONE;
    originalDefaultUncaughtExceptionHandler = Thread.getDefaultUncaughtExceptionHandler();
    Thread.setDefaultUncaughtExceptionHandler(localUncaughtExceptionHandler);

    BrailleUserPreferences.getSharedPreferences(this, BRAILLE_SHARED_PREFS_FILENAME)
        .registerOnSharedPreferenceChangeListener(onSharedPreferenceChangeListener);

    IntentFilter intentFilter = new IntentFilter();
    intentFilter.addAction(Intent.ACTION_SCREEN_OFF);
    intentFilter.addAction(Intent.ACTION_SCREEN_ON);
    ContextCompat.registerReceiver(
        this, screenOffReceiver, intentFilter, ContextCompat.RECEIVER_NOT_EXPORTED);
    ContextCompat.registerReceiver(
        this,
        closeSystemDialogsReceiver,
        new IntentFilter(Intent.ACTION_CLOSE_SYSTEM_DIALOGS),
        ContextCompat.RECEIVER_NOT_EXPORTED);
    ContextCompat.registerReceiver(
        this,
        imeChangeListener,
        new IntentFilter(Intent.ACTION_INPUT_METHOD_CHANGED),
        ContextCompat.RECEIVER_NOT_EXPORTED);
    Uri uri = Settings.Secure.getUriFor(Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
    getContentResolver()
        .registerContentObserver(uri, false, accessibilityServiceStatusChangeObserver);

    brailleImeAnalytics = BrailleImeAnalytics.getInstance(this);
    OrientationMonitor.init(this);
    layoutOrientator = new LayoutOrientator(this, layoutOrientatorCallback);

    if (talkBackForBrailleIme != null) {
      talkBackForBrailleIme.setBrailleImeForTalkBack(brailleImeForTalkBack);
    }
  }

  @Override
  public void onBindInput() {
    BrailleImeLog.d(TAG, "onBindInput");
    super.onBindInput();
  }

  @Override
  public View onCreateInputView() {
    View viewForImeFrameworks = keyboardView.createImeInputView();
    if (viewForImeFrameworks.getParent() != null) {
      // Remove any old one, to prevent a leak.
      ((ViewGroup) viewForImeFrameworks.getParent()).removeView(viewForImeFrameworks);
    }
    return viewForImeFrameworks;
  }

  private KeyboardView createKeyboardView() {
    return Utils.useImeSuppliedInputWindow() || brailleDisplayConnectedAndNotSuspended
        ? new StandardKeyboardView(
            this, keyboardViewCallback, /* fullScreen= */ !brailleDisplayConnectedAndNotSuspended)
        : new AccessibilityOverlayKeyboardView(this, keyboardViewCallback);
  }

  @Override
  public boolean onShowInputRequested(int flags, boolean configChange) {
    if (talkBackForBrailleIme != null) {
      if (talkBackForBrailleIme.isContextMenuExist()) {
        BrailleImeLog.d(TAG, "TalkBack context menu is running.");
        // Reject the request since TalkBack context menu is showing.
        return false;
      }
    }

    return super.onShowInputRequested(flags, configChange);
  }

  @Override
  public void onStartInputView(EditorInfo info, boolean restarting) {
    BrailleImeLog.d(TAG, "onStartInputView");
    getWindow().setTitle(Utils.getBrailleKeyboardDisplayName(this));
    if (Utils.isPhonePermissionGranted(this)) {
      TelephonyManager telephonyManager =
          (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
      telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_CALL_STATE);
    }

    // Surprisingly, framework sometimes invokes onStartInputView just after the screen turns off;
    // therefore we first confirm that the screen is indeed on before invoking activateIfNeeded.
    PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
    if (pm.isInteractive()) {
      if (activateIfNeeded() && !restarting) {
        talkBackForBrailleIme.resetGranularity();
      }
    } else {
      hideSelf();
    }
    startAnalyticsPossibly();

    InputConnection inputConnection = getCurrentInputConnection();
    if (inputConnection != null) {
      // Invoking requestCursorUpdates causes onUpdateCursorAnchorInfo() to be invoked.
      getCurrentInputConnection()
          .requestCursorUpdates(
              InputConnection.CURSOR_UPDATE_IMMEDIATE | InputConnection.CURSOR_UPDATE_MONITOR);
    }
  }

  @Override
  public void onConfigurationChanged(Configuration newConfig) {
    super.onConfigurationChanged(newConfig);
    if (orientation != newConfig.orientation) {
      orientation = newConfig.orientation;
      keyboardView.onOrientationChanged(newConfig.orientation);
    }
  }

  @Override
  public void onFinishInputView(boolean finishingInput) {
    if (Utils.isPhonePermissionGranted(this)) {
      TelephonyManager telephonyManager =
          (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
      telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_NONE);
    }
    // Of the teardown methods, this is the most reliable, so we use it to deactivate.
    BrailleImeLog.d(TAG, "onFinishInputView");
    super.onFinishInputView(finishingInput);
    deactivateIfNeeded();
    brailleImeAnalytics.collectSessionEvents();
  }

  @Override
  public boolean onEvaluateFullscreenMode() {
    // Why return false here? - see the note atop the class regarding how we suppress Views.
    return false;
  }

  @Override
  public void onDestroy() {
    BrailleImeLog.d(TAG, "onDestroy");
    instance = null;
    if (talkBackForBrailleIme != null) {
      talkBackForBrailleIme.setBrailleImeForTalkBack(null);
    }
    BrailleUserPreferences.getSharedPreferences(this, BRAILLE_SHARED_PREFS_FILENAME)
        .unregisterOnSharedPreferenceChangeListener(onSharedPreferenceChangeListener);
    unregisterReceiver(screenOffReceiver);
    unregisterReceiver(closeSystemDialogsReceiver);
    unregisterReceiver(imeChangeListener);
    getContentResolver().unregisterContentObserver(accessibilityServiceStatusChangeObserver);
    super.onDestroy();
    keyboardView.tearDown();
    keyboardView = null;
    brailleImeAnalytics.sendAllLogs();
  }

  @CanIgnoreReturnValue
  private boolean activateIfNeeded() {
    BrailleImeLog.d(TAG, "activateIfNeeded");
    if (keyboardView == null) {
      BrailleImeLog.e(TAG, "keyboardView is null. Activate should not invoke before onCreate()");
      return false;
    }
    if (!isInputViewShown()) {
      // Defer to superclass, if it knows that our input view is not showing (this is not an error).
      return false;
    }
    if (talkBackForBrailleIme == null
        || talkBackForBrailleIme.getServiceStatus() == ServiceStatus.OFF) {
      BrailleImeLog.e(TAG, "talkBackForBrailleIme is null or Talkback is off.");
      showTalkBackOffDialog();
      return false;
    } else if (talkBackForBrailleIme.getServiceStatus() == ServiceStatus.SUSPEND) {
      BrailleImeLog.e(TAG, "Talkback is suspend.");
      showTalkBackSuspendDialog();
      return false;
    }

    if (!deviceSupportsAtLeast5Pointers) {
      showTooFewTouchPointsDialog();
      return false;
    }

    BrailleImeLog.d(TAG, "activate");
    if (talkBackForBrailleIme.isVibrationFeedbackEnabled()) {
      BrailleImeVibrator.getInstance(this).enable();
    }
    boolean brailleDisplayConnectedAndNotIgnored =
        brailleDisplayForBrailleIme != null
            && brailleDisplayForBrailleIme.isBrailleDisplayConnectedAndNotSuspended();
    if (this.brailleDisplayConnectedAndNotSuspended != brailleDisplayConnectedAndNotIgnored) {
      this.brailleDisplayConnectedAndNotSuspended = brailleDisplayConnectedAndNotIgnored;
      updateInputView();
    }
    createViewContainerAndAddView();
    createEditBuffer();
    OrientationMonitor.getInstance().enable();
    OrientationMonitor.getInstance().registerCallback(orientationMonitorCallback);
    updateNavigationBarColor();
    if (typoHandler == null) {
      // Do not recreate the TypoHandler is because TalkBack performs typo correction makes IME
      // restart views but user won't aware. If we recreate, the data will all lost. So making the
      // TypoHandler keep as-it but only renew its InputConnection.
      typoHandler =
          new TypoHandler(
              BrailleIme.this,
              talkBackForBrailleIme.createFocusFinder(),
              talkBackForBrailleIme,
              BrailleCommonTalkBackSpeaker.getInstance());
    }
    typoHandler.updateInputConnection(getCurrentInputConnection());
    brailleImeGestureController =
        new BrailleImeGestureController(
            BrailleIme.this,
            typoHandler,
            editBuffer,
            brailleImeGestureCallback,
            talkBackForBrailleIme,
            feedbackManager);
    return true;
  }

  private void createViewContainerAndAddView() {
    keyboardView.setWindowManager(talkBackForBrailleIme.getWindowManager());
    keyboardView.createViewContainer();
    if (brailleDisplayConnectedAndNotSuspended) {
      keyboardView.createAndAddStripView(brailleDisplayKeyboardCallback);
    } else if (tutorialState != NONE
        || BrailleUserPreferences.shouldLaunchTutorial(getApplicationContext())) {
      if (tutorialState == NONE) {
        // Launch tutorial for the first usage.
        tutorialState = INTRO;
      }
      // Restore to previous tutorial state.
      createAndAddTutorialView();
    } else if (!keyboardView.isInputViewCreated()) {
      keyboardView.createAndAddInputView(inputPlaneCallback);
      escapeReminder.startTimer();
    }
  }

  private void createAndAddTutorialView() {
    // Correct tutorial state according to phone size.
    if (BrailleUtils.isPhoneSizedDevice(getResources())) {
      if (tutorialState == State.HOLD_6_FINGERS) {
        tutorialState = State.ROTATE_ORIENTATION;
      }
    } else {
      if (tutorialState == State.ROTATE_ORIENTATION
          || tutorialState == State.ROTATE_ORIENTATION_CONTINUE) {
        tutorialState = State.HOLD_6_FINGERS;
      }
    }
    keyboardView.createAndAddTutorialView(tutorialState, tutorialCallback);
    talkBackForBrailleIme.disableSilenceOnProximity();
  }

  private void activateBrailleIme() {
    if (talkBackForBrailleIme != null && isInputViewShown()) {
      Region region = null;
      if (keyboardView.obtainImeViewRegion().isPresent()) {
        region = new Region(keyboardView.obtainImeViewRegion().get());
      }
      talkBackForBrailleIme.onBrailleImeActivated(
          !brailleDisplayConnectedAndNotSuspended,
          Utils.useImeSuppliedInputWindow(),
          // Region might be null for short time before onTalkBackResumed() is called.
          region);
      if (brailleDisplayForBrailleIme != null
          && brailleDisplayConnectedAndNotSuspended
          && !isVisible) {
        isVisible = true;
        brailleDisplayForBrailleIme.onImeVisibilityChanged(true);
      }
    }
  }

  private void deactivateBrailleIme() {
    if (talkBackForBrailleIme != null) {
      talkBackForBrailleIme.onBrailleImeInactivated(
          Utils.useImeSuppliedInputWindow(), (tutorialState.equals(INTRO) && keyboardView != null));
    }
    if (brailleDisplayForBrailleIme != null
        && brailleDisplayConnectedAndNotSuspended
        && isVisible) {
      isVisible = false;
      brailleDisplayForBrailleIme.onImeVisibilityChanged(false);
    }
  }

  private void showTalkBackOffDialog() {
    // When screen rotates, onStartInputView is called and if there is a dialog showing, keep it
    // showing instead of adding a new one.
    if (!talkbackOffDialog.isShowing()) {
      brailleImeAnalytics.logTalkBackOffDialogDisplay();
      keyboardView.showViewAttachedDialog(talkbackOffDialog);
    }
  }

  private void showTalkBackSuspendDialog() {
    // When screen rotates, onStartInputView is called and if there is a dialog showing, keep it
    // showing instead of adding a new one.
    if (!talkBackSuspendDialog.isShowing()) {
      brailleImeAnalytics.logTalkBackOffDialogDisplay();
      keyboardView.showViewAttachedDialog(talkBackSuspendDialog);
    }
  }

  private void showTooFewTouchPointsDialog() {
    // When screen rotates, onStartInputView is called and if there is a dialog showing, keep it
    // showing instead of adding a new one.
    if (!tooFewTouchPointsDialog.isShowing()) {
      brailleImeAnalytics.logFewTouchPointsDialogDisplay();
      keyboardView.showViewAttachedDialog(tooFewTouchPointsDialog);
    }
  }

  private void createEditBuffer() {
    Code code = BrailleUserPreferences.readCurrentActiveInputCodeAndCorrect(this);
    boolean contractedMode =
        BrailleUserPreferences.readContractedMode(this) && code.isSupportsContracted(this);
    BrailleImeLog.d(
        TAG, "Code: " + code.getUserFacingName(BrailleIme.this) + " contracted: " + contractedMode);

    TranslatorFactory translatorFactory = BrailleUserPreferences.readTranslatorFactory(this);
    editBuffer =
        BrailleLanguages.createEditBuffer(
            this,
            BrailleCommonTalkBackSpeaker.getInstance(),
            code,
            translatorFactory,
            contractedMode);
  }

  @Override
  public void onComputeInsets(Insets outInsets) {
    if (Utils.useImeSuppliedInputWindow()) {
      // Set the contentTopInsets, which is measured from the top edge of the display, positively
      // downward, to be as tall as possible allowing the underlying framework to provide plenty of
      // vertical space to layout the underlying Activity.  In the absence of setting this value to
      // be large, the underlying Activity, in case it uses windowSoftInputMode adjustResize or
      // adjustUnspecified, will have very little (or zero) vertical room to perform a valid layout
      // - and that causes many problems, such as the IME getting closed or the Editor not receiving
      // our input.
      outInsets.contentTopInsets = Utils.getDisplaySizeInPixels(this).getHeight();
    }
    if (keyboardView.obtainImeViewRegion().isPresent()) {
      Rect rect = keyboardView.obtainImeViewRegion().get();
      if (Build.VERSION.SDK_INT == Build.VERSION_CODES.P) {
        if (brailleDisplayConnectedAndNotSuspended) {
          outInsets.visibleTopInsets = rect.top;
        } else {
          // In Android P, we need to manually set the size of the outInsets which represent the
          // area north of the IME window, otherwise any dialog attached to the unused IME window
          // will not show any foreground contents. But we also need to take care not to set this
          // insets area to be the entire screen, because doing that causes the inputView to be
          // ignored by an accessibility framework class responsible for sending info to Talkback,
          // and this prevents the proper announcement of the IME by TalkBack.
          outInsets.visibleTopInsets = rect.bottom - 1;
        }
        outInsets.contentTopInsets = outInsets.visibleTopInsets;
      }
    }
  }

  @CanIgnoreReturnValue
  private boolean deactivateIfNeeded() {
    BrailleImeLog.d(TAG, "deactivateIfNeeded");
    dismissDialogs();
    escapeReminder.cancelTimer();
    if (!keyboardView.isViewContainerCreated()) {
      // Deactivation is not needed because we're already deactivated (this is not an error).
      return false;
    }
    if (talkBackForBrailleIme == null) {
      BrailleImeLog.e(TAG, "talkBackForBrailleIme is null");
      return false;
    }
    BrailleImeLog.d(TAG, "deactivate");
    BrailleImeVibrator.getInstance(this).disable();
    if (isConnectionValid()) {
      editBuffer.commit(getImeConnection());
    }

    deactivateBrailleIme();
    tutorialState = keyboardView.getTutorialStatus();
    keyboardView.tearDown();
    calibrationAnnouncementHandler.removeCallbacksAndMessages(null);
    OrientationMonitor.getInstance().unregisterCallback();
    OrientationMonitor.getInstance().disable();
    return true;
  }

  private void reactivate() {
    deactivateIfNeeded();
    activateIfNeeded();
  }

  private void hideSelf() {
    requestHideSelf(0);
  }

  /**
   * Performs the 'actions' specified, via imeOptions, by the application such as 'Send'. A typical
   * IME usually surfaces the trigger for such an action with an (often blue-colored) action button.
   *
   * <p>By far the most common case is for an application to specify a single action (as opposed to
   * multiple). We don't currently support the distinguishment of multiple actions in that case.
   *
   * <p>Return {@code true} if the keyboard should remain showing.
   */
  private void performEditorAction(InputConnection inputConnection) {
    EditorInfo editorInfo = getCurrentInputEditorInfo();
    int editorAction = editorInfo.imeOptions & EditorInfo.IME_MASK_ACTION;
    BrailleImeLog.d(TAG, "performEnterAction editorAction = " + editorAction);
    if (editorAction != EditorInfo.IME_ACTION_UNSPECIFIED
        && editorAction != EditorInfo.IME_ACTION_NONE) {
      if (TextUtils.equals(editorInfo.packageName, Constants.ANDROID_MESSAGES_PACKAGE_NAME)) {
        // Messages uses async thread to check conditions when performing submit. We pend the task
        // with 50 millis seconds to avoid perform action failed.
        new Handler().postDelayed(() -> inputConnection.performEditorAction(editorAction), 50);
      } else {
        inputConnection.performEditorAction(editorAction);
      }
      if (editorAction == EditorInfo.IME_ACTION_NEXT) {
        BrailleCommonTalkBackSpeaker.getInstance().speak(getString(R.string.perform_action_next));
      } else {
        BrailleCommonTalkBackSpeaker.getInstance()
            .speak(getString(R.string.perform_action_submitting));
      }
    }
  }

  private void updateInputView() {
    if (keyboardView != null) {
      keyboardView.tearDown();
    }
    keyboardView = createKeyboardView();
    setInputView(keyboardView.createImeInputView());
    createViewContainerAndAddView();
  }

  /**
   * Attempt to exit this IME and switch to another.
   *
   * <p>First, try switching to Gboard if it exists. Otherwise, switch to the next IME if one
   * exists.
   *
   * <p>If switching to the next IME fails (which can happen because there are no other IMEs
   * installed and enabled OR for an unknown reason (which DOES occur on some phones), show the
   * system IME picker if there is another IME installed and enabled.
   *
   * <p>Finally, if there are not other IMEs installed and enabled, launch IME Settings.
   */
  @VisibleForTesting
  boolean switchToNextInputMethod() {
    if (talkBackForBrailleIme != null) {
      talkBackForBrailleIme.interruptSpeak();
    }
    if (isConnectionValid() && editBuffer != null) {
      // Commit holdings here, otherwise InputConnect will become invalid after switch keyboard.
      editBuffer.commit(getImeConnection());
    }
    if (!KeyboardUtils.areMultipleImesEnabled(this)) {
      // Show a toast and bring up Ime settings to user.
      Toast.makeText(this, getString(R.string.bring_ime_settings_page), Toast.LENGTH_SHORT).show();
      Intent intent = new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS);
      intent.addFlags(
          Intent.FLAG_ACTIVITY_NEW_TASK
              | Intent.FLAG_ACTIVITY_CLEAR_TASK
              | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
      startActivity(intent);
      return false;
    }
    boolean succeeded;
    // Default switch to gboard.
    String inputMethodInfoId = KeyboardUtils.getEnabledImeId(this, Constants.GBOARD_PACKAGE_NAME);
    if (!TextUtils.isEmpty(inputMethodInfoId)) {
      // This api doesn't tell us switch succeed or not. Assume it switch successfully.
      switchInputMethod(inputMethodInfoId);
      succeeded = true;
    } else if (BuildVersionUtils.isAtLeastP()) {
      succeeded = switchToNextInputMethod(false);
    } else {
      IBinder token = getWindow().getWindow().getAttributes().token;
      InputMethodManager inputMethodManager =
          (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
      succeeded = inputMethodManager.switchToNextInputMethod(token, false);
    }
    // REFERTO: Switch to next keyboard manually by giving ime id.
    if (!succeeded) {
      // This api doesn't tell us switch succeed or not. Assume it switch successfully.
      switchInputMethod(KeyboardUtils.getNextEnabledImeId(this));
    }
    return true;
  }

  private void readDeviceFeatures() {
    PackageManager pm = getPackageManager();
    deviceSupportsAtLeast5Pointers =
        pm.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN_MULTITOUCH_JAZZHAND);
  }

  private void dismissDialogs() {
    talkbackOffDialog.dismiss();
    contextMenuDialog.dismiss();
    tooFewTouchPointsDialog.dismiss();
    talkBackSuspendDialog.dismiss();
  }

  // Starts to log keyboard session only in non tutorial mode.
  private void startAnalyticsPossibly() {
    if (tutorialState == NONE) {
      brailleImeAnalytics.startSession();
    }
  }

  private void updateNavigationBarColor() {
    getWindow()
        .getWindow()
        .setNavigationBarColor(
            ContextCompat.getColor(
                this,
                brailleDisplayConnectedAndNotSuspended
                    ? R.color.braille_keyboard_background
                    : com.google.android.accessibility.utils.R.color.google_transparent));
  }

  private boolean isEightDotsBraille() {
    return BrailleUserPreferences.isCurrentActiveInputCodeEightDot(getApplicationContext());
  }

  private String getTwoStepsCalibrationAnnounceString(FingersPattern fingersPattern) {
    boolean reverseDot = BrailleUserPreferences.readReverseDotsMode(BrailleIme.this);
    StringBuilder sb = new StringBuilder();
    switch (fingersPattern) {
      case NO_FINGERS:
      case FIVE_FINGERS:
      case SIX_FINGERS:
      case SEVEN_FINGERS:
        sb.append(
                getString(
                    R.string.calibration_step1_hold_left_or_right_finger_announcement,
                    getString(reverseDot ? R.string.right_hand : R.string.left_hand)))
            .append(" ")
            .append(
                getString(
                    isEightDotsBraille()
                        ? R.string.calibration_hold_left_or_right_four_finger_announcement
                        : R.string.calibration_hold_left_or_right_three_finger_announcement,
                    getString(reverseDot ? R.string.right_hand : R.string.left_hand)));
        return sb.toString();
      case FIRST_THREE_FINGERS:
        sb.append(
                getString(
                    R.string.calibration_step2_hold_left_or_right_finger_announcement,
                    getString(reverseDot ? R.string.left_hand : R.string.right_hand)))
            .append(" ")
            .append(
                getString(
                    isEightDotsBraille()
                        ? R.string.calibration_hold_left_or_right_four_finger_announcement
                        : R.string.calibration_hold_left_or_right_three_finger_announcement,
                    getString(reverseDot ? R.string.left_hand : R.string.right_hand)));
        return sb.toString();
      case FIRST_FOUR_FINGERS:
        sb.append(
                getString(
                    R.string
                        .eightDot_braille_calibration_step2_hold_left_or_right_finger_announcement,
                    getString(reverseDot ? R.string.left_hand : R.string.right_hand)))
            .append(" ")
            .append(
                getString(
                    isEightDotsBraille()
                        ? R.string.calibration_hold_left_or_right_four_finger_announcement
                        : R.string.calibration_hold_left_or_right_three_finger_announcement,
                    getString(reverseDot ? R.string.left_hand : R.string.right_hand)));
        return sb.toString();
      default:
        return "";
    }
  }

  private String getRepeatedTwoStepCalibrationAnnounceString(FingersPattern fingersPattern) {
    boolean reverseDot = BrailleUserPreferences.readReverseDotsMode(BrailleIme.this);
    switch (fingersPattern) {
      case NO_FINGERS:
      case FIVE_FINGERS:
      case SIX_FINGERS:
      case SEVEN_FINGERS:
        return getString(
            isEightDotsBraille()
                ? R.string.calibration_hold_left_or_right_four_finger_announcement
                : R.string.calibration_hold_left_or_right_three_finger_announcement,
            getString(reverseDot ? R.string.right_hand : R.string.left_hand));
      case FIRST_THREE_FINGERS:
      case FIRST_FOUR_FINGERS:
        return getString(
            isEightDotsBraille()
                ? R.string.calibration_hold_left_or_right_four_finger_announcement
                : R.string.calibration_hold_left_or_right_three_finger_announcement,
            getString(reverseDot ? R.string.left_hand : R.string.right_hand));
      default:
        return "";
    }
  }

  private void speakAnnouncementRepeatedly(CharSequence announcement, int delay) {
    calibrationAnnouncementHandler.removeCallbacksAndMessages(null);
    // Do not use the delay in Talkback because we want to be able to cancel it.
    calibrationAnnouncementHandler.postDelayed(
        () ->
            BrailleCommonTalkBackSpeaker.getInstance()
                .speak(announcement, getRepeatAnnouncementRunnable(announcement)),
        delay);
  }

  private UtteranceCompleteRunnable getRepeatAnnouncementRunnable(
      CharSequence repeatedAnnouncement) {
    int speechId = instructionSpeechId.incrementAndGet();
    return status -> {
      if (speechId == instructionSpeechId.get() && keyboardView.inTwoStepCalibration()) {
        speakAnnouncementRepeatedly(repeatedAnnouncement, CALIBRATION_ANNOUNCEMENT_REPEAT_MS);
      }
    };
  }

  private final LayoutOrientatorCallback layoutOrientatorCallback =
      new LayoutOrientatorCallback() {
        @Override
        public boolean useSensorsToDetectLayout() {
          return BrailleUserPreferences.readLayoutMode(BrailleIme.this) == TouchDots.AUTO_DETECT
              && !keyboardView.isTutorialShown()
              && !brailleDisplayConnectedAndNotSuspended;
        }

        @Override
        public void onDetectionChanged(boolean isTabletop, boolean isFirstChangedEvent) {
          String layout =
              getString(
                  isTabletop
                      ? R.string.switch_to_tabletop_announcement
                      : R.string.switch_to_screen_away_announcement);
          String calibrationTips = "";
          if (keyboardView.inTwoStepCalibration()) {
            if (!isFirstChangedEvent) {
              calibrationTips = getTwoStepsCalibrationAnnounceString(FingersPattern.NO_FINGERS);
            }
          } else if (isTabletop) {
            calibrationTips =
                getString(
                    R.string.calibration_tip_announcement,
                    getCurrentTypingLanguageType(getApplicationContext()).getDotCount());
          }
          if (isFirstChangedEvent) {
            String finalCalibrationTips = calibrationTips;
            BrailleCommonTalkBackSpeaker.getInstance().speak(layout, ANNOUNCE_DELAY_MS);
            calibrationAnnouncementHandler.postDelayed(
                () -> BrailleCommonTalkBackSpeaker.getInstance().speak(finalCalibrationTips),
                ANNOUNCE_DELAY_MS);
          } else {
            BrailleCommonTalkBackSpeaker.getInstance()
                .speak(layout, TalkBackSpeaker.AnnounceType.INTERRUPT);
            calibrationAnnouncementHandler.removeCallbacksAndMessages(null);
            BrailleCommonTalkBackSpeaker.getInstance()
                .speak(
                    calibrationTips,
                    keyboardView.inTwoStepCalibration()
                        ? getRepeatAnnouncementRunnable(
                            getRepeatedTwoStepCalibrationAnnounceString(FingersPattern.NO_FINGERS))
                        : null);
          }
          keyboardView.setTableMode(isTabletop);
        }
      };

  private final Thread.UncaughtExceptionHandler localUncaughtExceptionHandler =
      new UncaughtExceptionHandler() {
        @Override
        public void uncaughtException(Thread thread, Throwable throwable) {
          BrailleImeLog.e(TAG, "Uncaught exception", throwable);
          try {
            deactivateIfNeeded();
            if (isInputViewShown()) {
              switchToNextInputMethod();
            }
          } catch (RuntimeException e) {
            BrailleImeLog.e(TAG, "Uncaught exception in handler", throwable);
          } finally {
            if (originalDefaultUncaughtExceptionHandler != null) {
              originalDefaultUncaughtExceptionHandler.uncaughtException(thread, throwable);
            }
          }
        }
      };

  private final BroadcastReceiver screenOffReceiver =
      new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
          if (intent.getAction().equals(Intent.ACTION_SCREEN_OFF)) {
            BrailleImeLog.d(TAG, "screen off");
            deactivateIfNeeded();
            dismissDialogs();
            // Finish session while screen off because no called onFinishInputView() in this case.
            brailleImeAnalytics.collectSessionEvents();
          } else if (intent.getAction().equals(Intent.ACTION_SCREEN_ON)) {
            // Activate upon SCREEN_ON to resolve the following scenario occurs:
            // 1. Screen turns off, and then abruptly turns on (before SCREEN_OFF receiver is
            // triggered).
            // 2. onStartInputView() gets invoked before SCREEN_OFF receiver gets triggered.
            // 3. SCREEN_OFF receiver gets triggered, thus deactivating, causing bad state - IME is
            // up but Window is absent.
            BrailleImeLog.d(TAG, "screen on");
            KeyguardManager keyguardManager =
                (KeyguardManager) context.getSystemService(Context.KEYGUARD_SERVICE);
            BrailleImeLog.d(TAG, "screen is locked: " + keyguardManager.isKeyguardLocked());
            // Do not activate if keyguard is showing (because our Window would show atop keyguard).
            if (!keyguardManager.isKeyguardLocked()) {
              activateIfNeeded();
            }
          }
        }
      };

  private final BroadcastReceiver closeSystemDialogsReceiver =
      new BroadcastReceiver() {
        private static final String SYSTEM_DIALOG_REASON_KEY = "reason";
        private static final String SYSTEM_DIALOG_REASON_RECENT_APPS = "recentapps";
        private static final String SYSTEM_DIALOG_REASON_HOME_KEY = "homekey";
        private static final String SYSTEM_DIALOG_REASON_VOICE_INTERACTION = "voiceinteraction";

        @Override
        public void onReceive(Context context, Intent intent) {
          if (intent.getAction().equals(Intent.ACTION_CLOSE_SYSTEM_DIALOGS)) {
            String reason = intent.getStringExtra(SYSTEM_DIALOG_REASON_KEY);
            if (reason != null) {
              BrailleImeLog.d(TAG, "action:" + intent.getAction() + ",reason:" + reason);
              if (reason.equals(SYSTEM_DIALOG_REASON_HOME_KEY)
                  || reason.equals(SYSTEM_DIALOG_REASON_RECENT_APPS)
                  || reason.equals(SYSTEM_DIALOG_REASON_VOICE_INTERACTION)) {
                // Home key, recent key or google assistant comes up.
                dismissDialogs();
              }
            }
          }
        }
      };

  private final BroadcastReceiver imeChangeListener =
      new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
          if (intent.getAction().equals(Intent.ACTION_INPUT_METHOD_CHANGED)) {
            dismissDialogs();
          }
        }
      };

  private final BrailleImeForTalkBack brailleImeForTalkBack =
      new BrailleImeForTalkBack() {
        @Override
        public void onTalkBackSuspended() {
          BrailleImeLog.d(TAG, "onTalkBackSuspended");
          // We might get service state off when TalkBack turns off, but we'll handle it in
          // accessibilityServiceStatusChangeObserver.
          if (isInputViewShown()
              && talkBackForBrailleIme.getServiceStatus() == ServiceStatus.SUSPEND) {
            if (keyboardView.isTutorialShown()) {
              brailleImeAnalytics.logTutorialFinishedByTalkbackStop();
            }
            if (KeyboardUtils.areMultipleImesEnabled(BrailleIme.this)) {
              switchToNextInputMethod();
            } else {
              deactivateIfNeeded();
              showTalkBackSuspendDialog();
            }
          }
        }

        @Override
        public void onTalkBackResumed() {
          BrailleImeLog.d(TAG, "onTalkBackResumed");
          // This callback won't be triggered when service state changes from off to on because it's
          // set to null when off so we register it back in
          // accessibilityServiceStatusChangeObserver.
          if (isInputViewShown()) {
            dismissDialogs();
            activateIfNeeded();
          }
        }

        @Override
        public boolean isTouchInteracting() {
          return !brailleDisplayConnectedAndNotSuspended && keyboardView.isTouchInteracting();
        }

        @Override
        public BrailleImeForBrailleDisplay getBrailleImeForBrailleDisplay() {
          return brailleImeForBrailleDisplay;
        }

        @Override
        public void onScreenDim() {
          keyboardView.setKeyboardViewTransparent(true);
        }

        @Override
        public void onScreenBright() {
          keyboardView.setKeyboardViewTransparent(false);
        }

        @Override
        public boolean isGranularityValid(CursorGranularity cursorGranularity) {
          return VALID_GRANULARITIES.contains(cursorGranularity);
        }

        @Override
        public boolean isBrailleKeyboardActivated() {
          return isInputViewShown();
        }
      };

  // We need this because in some situations BrailleImeForTalkBack is set to null. There is no
  // callback (set when onBrailleImeActivated()) in TB for us to know it turns off or on. For
  // example, when TalkBackOff dialog shows up, first page of tutorial or context menu shows up.
  // Note: TalkBack turns from active to suspended and suspended to resumed will not come in through
  // this callback.
  private final ContentObserver accessibilityServiceStatusChangeObserver =
      new ContentObserver(new Handler()) {
        @Override
        public void onChange(boolean selfChange) {
          super.onChange(selfChange);
          if (!isInputViewShown()) {
            return;
          }
          if (isAccessibilityServiceEnabled(
              BrailleIme.this, Constants.TALKBACK_SERVICE.flattenToShortString())) {
            BrailleImeLog.d(TAG, "TalkBack becomes active.");
            // This listener is triggered before TB service is ready. Call activateIfNeeded() will
            // get service state is off so we need to set BrailleImeForTalkBack in TB to get
            // onTalkBackResumed() to make sure the state has been set to active.
            activateBrailleIme();
          } else {
            BrailleImeLog.d(TAG, "TalkBack becomes inactive.");
            if (KeyboardUtils.areMultipleImesEnabled(BrailleIme.this)) {
              switchToNextInputMethod();
            } else {
              deactivateIfNeeded();
              showTalkBackOffDialog();
            }
          }
        }
      };

  private final BrailleImeGestureController.Callback brailleImeGestureCallback =
      new BrailleImeGestureController.Callback() {
        @Override
        public void hideBrailleKeyboard() {
          hideSelf();
          escapeReminder.increaseExitKeyboardCounter();
        }

        @Override
        public void switchToNextInputMethod() {
          BrailleIme.this.switchToNextInputMethod();
          escapeReminder.increaseExitKeyboardCounter();
        }

        @Override
        public void showContextMenu() {
          keyboardView.showViewAttachedDialog(contextMenuDialog);
          brailleImeAnalytics.logGestureActionOpenOptionsMenu();
          brailleImeAnalytics.collectSessionEvents();
          escapeReminder.increaseOptionDialogCounter();
        }

        @Override
        public void performEditorAction() {
          BrailleIme.this.performEditorAction(getImeConnection().inputConnection);
        }

        @Override
        public boolean isConnectionValid() {
          return BrailleIme.this.isConnectionValid();
        }

        @Override
        public ImeConnection getImeConnection() {
          return BrailleIme.this.getImeConnection();
        }
      };

  private final OrientationMonitor.Callback orientationMonitorCallback =
      new OrientationMonitor.Callback() {
        @Override
        public void onOrientationChanged(OrientationMonitor.Orientation orientation) {
          if (orientationCallbackDelegate != null) {
            orientationCallbackDelegate.onOrientationChanged(orientation);
          }
        }
      };

  private final BrailleInputView.Callback inputPlaneCallback =
      new BrailleInputView.Callback() {
        @Override
        public boolean onSwipeProduced(Swipe swipe) {
          if (brailleImeGestureController.performSwipeAction(swipe)) {
            showOnBrailleDisplay();
            if (!brailleDisplayConnectedAndNotSuspended) {
              escapeReminder.restartTimer();
            }
            return true;
          }
          return false;
        }

        @Override
        public boolean onDotHoldAndDotSwipe(Swipe swipe, BrailleCharacter heldBrailleCharacter) {
          if (brailleImeGestureController.performDotHoldAndSwipeAction(
              swipe, heldBrailleCharacter)) {
            showOnBrailleDisplay();
            return true;
          }
          return false;
        }

        @Override
        public boolean isCalibrationHoldRecognized(
            boolean inTwoStepCalibration, int pointersHeldCount) {
          return isSixDotCalibration(pointersHeldCount)
              || isEightDotCalibration(pointersHeldCount)
              || (inTwoStepCalibration && isConfirmedTwoStepCalibration(pointersHeldCount));
        }

        @Override
        public boolean onHoldProduced(int pointersHeldCount) {
          return brailleImeGestureController.performDotHoldAction(pointersHeldCount);
        }

        @Nullable
        @Override
        public String onBrailleProduced(BrailleCharacter brailleChar) {
          if (!isConnectionValid()) {
            return null;
          }
          talkBackForBrailleIme.interruptSpeak();
          if (talkBackForBrailleIme.isCurrentGranularityTypoCorrection()) {
            talkBackForBrailleIme.resetGranularity();
          }
          brailleImeAnalytics.logTotalBrailleCharCount(1);
          String result = editBuffer.appendBraille(getImeConnection(), brailleChar);
          if (!TextUtils.isEmpty(result)) {
            escapeReminder.restartTimer();
            showOnBrailleDisplay();
          }
          BrailleImeVibrator.getInstance(BrailleIme.this).vibrate(VibrationType.BRAILLE_COMMISSION);
          return result;
        }

        @Override
        public boolean onCalibration(
            CalibrationTriggeredType calibration, FingersPattern fingersPattern) {
          calibrationAnnouncementHandler.removeCallbacksAndMessages(null);
          boolean processed = false;
          if (isCalibrationSucceeded(fingersPattern)) {
            playCalibrationDoneSoundAndAnnouncement(
                getString(
                    fingersPattern == FingersPattern.REMAINING_THREE_FINGERS
                            || fingersPattern == FingersPattern.REMAINING_FOUR_FINGERS
                        ? R.string.remaining_calibration_finish_announcement
                        : R.string.calibration_finish_announcement));
            keyboardView.saveInputViewPoints();
            escapeReminder.startTimer();
            processed = true;
            brailleImeAnalytics.logCalibrationFinish(
                mapCalibrationToType(calibration), isCurrentTableTopMode(), isEightDotsBraille());
          } else if ((calibration == CalibrationTriggeredType.MANUAL
                  && fingersPattern == FingersPattern.NO_FINGERS)
              || fingersPattern == FingersPattern.FIVE_FINGERS
              || fingersPattern == FingersPattern.SIX_FINGERS
              || fingersPattern == FingersPattern.SEVEN_FINGERS) {
            escapeReminder.cancelTimer();
            processed = true;
            brailleImeAnalytics.logCalibrationStarted(
                mapCalibrationToType(calibration), isCurrentTableTopMode(), isEightDotsBraille());
            // Add 6/7 dots calibration for 8-dot braille. Wait for showing braille keyboard and
            // layout mode announcement finish.
            calibrationAnnouncementHandler.postDelayed(
                () ->
                    BrailleCommonTalkBackSpeaker.getInstance()
                        .speak(
                            getTwoStepsCalibrationAnnounceString(fingersPattern),
                            getRepeatAnnouncementRunnable(
                                getRepeatedTwoStepCalibrationAnnounceString(fingersPattern))),
                ANNOUNCE_CALIBRATION_DELAY_MS);
          } else if (fingersPattern == FingersPattern.FIRST_THREE_FINGERS
              || fingersPattern == FingersPattern.FIRST_FOUR_FINGERS) {
            processed = true;
            playCalibrationDoneSoundAndAnnouncement(
                getTwoStepsCalibrationAnnounceString(fingersPattern));
          }
          return processed;
        }

        @Override
        public void onCalibrationFailed(CalibrationTriggeredType calibration) {
          brailleImeAnalytics.logCalibrationFailed(
              mapCalibrationToType(calibration), isCurrentTableTopMode(), isEightDotsBraille());
          calibrationAnnouncementHandler.removeCallbacksAndMessages(null);
          BrailleCommonTalkBackSpeaker.getInstance()
              .speak(
                  getString(R.string.calibration_fail_announcement),
                  TalkBackSpeaker.AnnounceType.INTERRUPT);
          if (calibration != CalibrationTriggeredType.MANUAL) {
            BrailleCommonTalkBackSpeaker.getInstance()
                .speak(
                    getString(
                        R.string.calibration_fail_try_again_announcement,
                        getCurrentTypingLanguageType(getApplicationContext()).getDotCount()));
          }
        }

        @Override
        public void onTwoStepCalibrationRetry(boolean isFirstStep) {
          calibrationAnnouncementHandler.removeCallbacksAndMessages(null);
          boolean reverseDot = BrailleUserPreferences.readReverseDotsMode(BrailleIme.this);
          String announcement =
              getString(
                  isEightDotsBraille()
                      ? R.string.calibration_hold_left_or_right_four_finger_announcement
                      : R.string.calibration_hold_left_or_right_three_finger_announcement,
                  isFirstStep
                      ? getString(reverseDot ? R.string.right_hand : R.string.left_hand)
                      : getString(reverseDot ? R.string.left_hand : R.string.right_hand));
          BrailleCommonTalkBackSpeaker.getInstance()
              .speak(
                  announcement,
                  TalkBackSpeaker.AnnounceType.INTERRUPT,
                  getRepeatAnnouncementRunnable(announcement));
        }

        private boolean isCalibrationSucceeded(FingersPattern fingersPattern) {
          boolean currentInputCodeEightDot = isEightDotsBraille();
          return (fingersPattern == FingersPattern.REMAINING_THREE_FINGERS
                  && !currentInputCodeEightDot)
              || (fingersPattern == FingersPattern.REMAINING_FOUR_FINGERS
                  && currentInputCodeEightDot)
              || (fingersPattern == FingersPattern.SIX_FINGERS && !currentInputCodeEightDot)
              || (fingersPattern == FingersPattern.EIGHT_FINGERS && currentInputCodeEightDot);
        }

        private BrailleImeAnalytics.CalibrationTriggeredType mapCalibrationToType(
            CalibrationTriggeredType calibration) {
          switch (calibration) {
            case FIVE_FINGERS:
              return BrailleImeAnalytics.CalibrationTriggeredType.FIVE_FINGER;
            case SIX_FINGERS:
              return BrailleImeAnalytics.CalibrationTriggeredType.SIX_FINGER;
            case SEVEN_FINGERS:
              return BrailleImeAnalytics.CalibrationTriggeredType.SEVEN_FINGER;
            case EIGHT_FINGERS:
              return BrailleImeAnalytics.CalibrationTriggeredType.EIGHT_FINGER;
            case MANUAL:
              return BrailleImeAnalytics.CalibrationTriggeredType.MANUAL;
          }
          return BrailleImeAnalytics.CalibrationTriggeredType.UNSPECIFIED_FINGER;
        }

        private void playCalibrationDoneSoundAndAnnouncement(String announcement) {
          for (int i = 0; i < CALIBRATION_EARCON_REPEAT_COUNT; i++) {
            feedbackManager.emitFeedback(
                FeedbackManager.Type.CALIBRATION, CALIBRATION_EARCON_DELAY_MS * i);
          }
          // Wait a second for playing sound and then speak the post-action announcement.
          BrailleCommonTalkBackSpeaker.getInstance()
              .speak(
                  announcement,
                  ANNOUNCE_CALIBRATION_DELAY_MS,
                  TalkBackSpeaker.AnnounceType.INTERRUPT);
        }

        private boolean isEightDotCalibration(int pointersHeldCount) {
          // Do 2-step calibration for 5/6/7 dots.
          return (5 <= pointersHeldCount && pointersHeldCount <= 8) && isEightDotsBraille();
        }

        private boolean isSixDotCalibration(int pointersHeldCount) {
          return (pointersHeldCount == 5 || pointersHeldCount == 6) && !isEightDotsBraille();
        }

        private boolean isConfirmedTwoStepCalibration(int pointersHeldCount) {
          return isEightDotsBraille() ? pointersHeldCount == 4 : pointersHeldCount == 3;
        }
      };

  private final KeyboardViewCallback keyboardViewCallback =
      new KeyboardViewCallback() {
        @Override
        public void onViewReady() {
          BrailleImeLog.d(TAG, "onViewReady");
          activateBrailleIme();
          layoutOrientator.startIfNeeded();
          if (!keyboardView.isTutorialShown()) {
            showOnBrailleDisplay();
          }
        }

        @Override
        public void onViewUpdated() {
          if (!contextMenuDialog.isShowing() && tutorialState != INTRO) {
            activateBrailleIme();
          }
        }

        @Override
        public void onViewCleared() {
          BrailleImeLog.d(TAG, "onViewCleared");
          layoutOrientator.stop();
        }

        @Override
        public void onAnnounce(String announcement, int delayMs) {
          if (delayMs <= 0) {
            BrailleCommonTalkBackSpeaker.getInstance().speak(announcement);
          } else {
            BrailleCommonTalkBackSpeaker.getInstance().speak(announcement, delayMs);
          }
        }

        @Override
        public boolean isHideScreenMode() {
          return talkBackForBrailleIme.isHideScreenMode();
        }
      };

  private final BrailleDisplayImeStripView.CallBack brailleDisplayKeyboardCallback =
      new BrailleDisplayImeStripView.CallBack() {
        @Override
        public void onSwitchToOnscreenKeyboard() {
          BrailleImeLog.d(TAG, "onStripClicked");
          brailleDisplayConnectedAndNotSuspended = false;
          updateInputView();
          BrailleCommonTalkBackSpeaker.getInstance()
              .speak(
                  getString(R.string.switch_on_screen_keyboard_announcement),
                  TalkBackSpeaker.AnnounceType.INTERRUPT);
          keyboardView.setTableMode(isCurrentTableTopMode());
          brailleDisplayForBrailleIme.suspendInFavorOfBrailleKeyboard();
          updateNavigationBarColor();
        }

        @Override
        public void onSwitchToNextKeyboard() {
          switchToNextInputMethod();
        }
      };

  private void showOnBrailleDisplay() {
    if (!isInputViewShown()) {
      return;
    }
    mainHandler.post(
        () -> {
          if (brailleDisplayForBrailleIme == null || editBuffer == null || !isConnectionValid()) {
            return;
          }
          ResultForDisplay result =
              ResultForDisplay.builder()
                  .setHoldingsInfo(editBuffer.getHoldingsInfo(getImeConnection()))
                  .setOnScreenText(EditBufferUtils.getTextFieldText(getCurrentInputConnection()))
                  .setTextSelection(
                      BrailleCommonUtils.getTextSelection(getCurrentInputConnection()))
                  .setIsMultiLine(
                      EditBufferUtils.isMultiLineField(getCurrentInputEditorInfo().inputType))
                  .setAction(Utils.getActionLabel(this, getCurrentInputEditorInfo()).toString())
                  .setHint(Utils.getHint(getImeConnection()).toString())
                  .setShowPassword(
                      BrailleCommonUtils.isVisiblePasswordField(getCurrentInputEditorInfo()))
                  .build();
          brailleDisplayForBrailleIme.showOnDisplay(result);
        });
  }

  private boolean isConnectionValid() {
    if (getCurrentInputConnection() == null) {
      BrailleImeLog.e(TAG, "lack of InputConnection");
      return false;
    }
    if (getCurrentInputEditorInfo() == null) {
      BrailleImeLog.e(TAG, "lack of InputEditorInfo");
      return false;
    }
    return true;
  }

  @VisibleForTesting
  boolean isCurrentTableTopMode() {
    Optional<TouchDots> layoutOptional = layoutOrientator.getDetectedLayout();
    TouchDots mode = BrailleUserPreferences.readLayoutMode(this);
    return mode == TouchDots.TABLETOP
        || (mode == TouchDots.AUTO_DETECT
            && layoutOptional.isPresent()
            && layoutOptional.get() == TouchDots.TABLETOP);
  }

  private ImeConnection getImeConnection() {
    AnnounceType announceType = SILENCE;
    boolean shouldAnnounceCharacter =
        brailleDisplayForBrailleIme.isBrailleDisplayConnectedAndNotSuspended()
            ? talkBackForBrailleIme.shouldAnnounceCharacterForPhysicalKeyboard()
            : talkBackForBrailleIme.shouldAnnounceCharacterForOnScreenKeyboard();
    if (talkBackForBrailleIme != null && shouldAnnounceCharacter) {
      announceType = talkBackForBrailleIme.shouldSpeakPassword() ? NORMAL : HIDE_PASSWORD;
    }
    return new ImeConnection(
        getCurrentInputConnection(), getCurrentInputEditorInfo(), announceType);
  }

  private final OnSharedPreferenceChangeListener onSharedPreferenceChangeListener =
      new OnSharedPreferenceChangeListener() {
        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
          if (key.equals(getString(com.google.android.accessibility.braille.common.R.string.pref_brailleime_translator_code))) {
            Code newCode =
                BrailleUserPreferences.readCurrentActiveInputCodeAndCorrect(BrailleIme.this);
            if (!brailleDisplayConnectedAndNotSuspended) {
              BrailleCommonTalkBackSpeaker.getInstance()
                  .speak(
                      getString(
                          R.string.switch_to_language_announcement,
                          newCode.getUserFacingName(BrailleIme.this)),
                      TalkBackSpeaker.AnnounceType.INTERRUPT);
            }
            if (keyboardView.getBrailleInputViewDotCount()
                != BrailleUserPreferences.getCurrentTypingLanguageType(BrailleIme.this)
                    .getDotCount()) {
              keyboardView.refreshInputView();
            }
            refreshEditBufferAndBrailleDisplay();
          } else if (key.equals(getString(com.google.android.accessibility.braille.common.R.string.pref_braille_contracted_mode))) {
            boolean contractedMode = BrailleUserPreferences.readContractedMode(BrailleIme.this);
            if (BrailleUserPreferences.readCurrentActiveInputCodeAndCorrect(BrailleIme.this)
                .isSupportsContracted(BrailleIme.this)) {
              BrailleImeAnalytics.getInstance(BrailleIme.this).logContractedToggle(contractedMode);
            }
            if (!brailleDisplayConnectedAndNotSuspended) {
              BrailleCommonTalkBackSpeaker.getInstance()
                  .speak(
                      getString(
                          contractedMode
                              ? R.string.switched_to_contracted_announcement
                              : R.string.switched_to_uncontracted_announcement),
                      TalkBackSpeaker.AnnounceType.INTERRUPT);
            }
            refreshEditBufferAndBrailleDisplay();
          }
        }

        private void refreshEditBufferAndBrailleDisplay() {
          if (editBuffer != null) {
            editBuffer.commit(getImeConnection());
          }
          createEditBuffer();
          if (brailleImeGestureController != null) {
            brailleImeGestureController.updateEditBuffer(editBuffer);
          }
          getWindow().setTitle(Utils.getBrailleKeyboardDisplayName(BrailleIme.this));
          showOnBrailleDisplay();
        }
      };

  private final ContextMenuDialog.Callback contextMenuDialogCallback =
      new ContextMenuDialog.Callback() {
        @Override
        public void onDialogHidden() {
          activateBrailleIme();
          startAnalyticsPossibly();
          layoutOrientator.startIfNeeded();
          showOnBrailleDisplay();
        }

        @Override
        public void onDialogShown() {
          deactivateBrailleIme();
          layoutOrientator.stop();
        }

        @Override
        public void onLaunchSettings() {
          Intent intent = new Intent();
          ComponentName name =
              new ComponentName(getPackageName(), BrailleImePreferencesActivity.class.getName());
          intent.setComponent(name);
          intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
          startActivity(intent);
        }

        @Override
        public void onTutorialOpen() {
          escapeReminder.cancelTimer();
          layoutOrientator.stop();
          tutorialState = INTRO;
          createAndAddTutorialView();
        }

        @Override
        public void onTutorialClosed() {
          escapeReminder.startTimer();
        }

        @Override
        public void onCalibration() {
          activateBrailleIme();
          layoutOrientator.startIfNeeded();
          keyboardView.calibrateBrailleInputView();
        }
      };

  private final TutorialCallback tutorialCallback =
      new TutorialCallback() {
        @Override
        public void onBrailleImeActivated() {
          activateBrailleIme();
        }

        @Override
        public void onBrailleImeInactivated() {
          deactivateBrailleIme();
        }

        @Override
        public void onAudialAnnounce(
            String announcement,
            int delayMs,
            TalkBackSpeaker.AnnounceType announceType,
            UtteranceCompleteRunnable utteranceCompleteRunnable) {
          BrailleCommonTalkBackSpeaker.getInstance()
              .speak(announcement, delayMs, announceType, utteranceCompleteRunnable);
        }

        @Override
        public void onPlaySound(FeedbackManager.Type type) {
          feedbackManager.emitFeedback(type);
        }

        @Override
        public void onSwitchToNextInputMethod() {
          switchToNextInputMethod();
          brailleImeAnalytics.logTutorialFinishedBySwitchToNextInputMethod();
        }

        @Override
        public void onLaunchSettings() {
          contextMenuDialogCallback.onLaunchSettings();
          brailleImeAnalytics.logTutorialFinishedByLaunchSettings();
        }

        @Override
        public void onTutorialFinished() {
          BrailleUserPreferences.setTutorialFinished(getApplicationContext());
          tutorialState = NONE;
          talkBackForBrailleIme.restoreSilenceOnProximity();
          activateBrailleIme();
          keyboardView.createAndAddInputView(inputPlaneCallback);
          // Braille keyboard sometimes will restart. startTimer() might be called twice (here and
          // activateIfNeeded).
          escapeReminder.startTimer();
          brailleImeAnalytics.logTutorialFinishedByTutorialCompleted();
          layoutOrientator.startIfNeeded();
        }

        @Override
        public void onRestartTutorial() {
          reactivate();
        }

        @Override
        public void registerOrientationChange(OrientationMonitor.Callback callBack) {
          orientationCallbackDelegate = callBack;
        }

        @Override
        public void unregisterOrientationChange() {
          orientationCallbackDelegate = null;
        }
      };

  private final EscapeReminder.Callback escapeReminderCallback =
      new EscapeReminder.Callback() {
        @Override
        public void onRemind(SpeechController.UtteranceCompleteRunnable utteranceCompleteRunnable) {
          BrailleCommonTalkBackSpeaker.getInstance()
              .speak(
                  getString(R.string.reminder_announcement),
                  ANNOUNCE_DELAY_MS,
                  utteranceCompleteRunnable);
        }

        @Override
        public boolean shouldAnnounce() {
          return keyboardView.isViewContainerCreated() && tutorialState == NONE;
        }
      };

  private final TalkBackOffDialog.Callback talkBackOffDialogCallback =
      new TalkBackOffDialog.Callback() {
        @Override
        public void onSwitchToNextIme() {
          switchToNextInputMethod();
        }

        @Override
        public void onLaunchSettings() {
          Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
          intent.addFlags(
              Intent.FLAG_ACTIVITY_NEW_TASK
                  | Intent.FLAG_ACTIVITY_CLEAR_TASK
                  | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
          // Highlight TalkBack item in Accessibility Settings upon arriving there (Pixel only).
          PreferenceSettingsUtils.attachSettingsHighlightBundle(intent, Constants.TALKBACK_SERVICE);
          startActivity(intent);
          // The ACTION_CLOSE_SYSTEM_DIALOGS intent action is deprecated from S. The platform will
          // automatically collapse the proper system dialogs in the proper use-cases.
          if (!BuildVersionUtils.isAtLeastS()) {
            // Collapse notification panel (quick settings).
            sendBroadcast(new Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS));
          }
        }
      };

  private final TalkBackSuspendDialog.Callback talkBackSuspendDialogCallback =
      new TalkBackSuspendDialog.Callback() {
        @Override
        public void onSwitchToNextIme() {
          switchToNextInputMethod();
        }
      };

  private final TooFewTouchPointsDialog.Callback tooFewTouchPointsDialogCallback =
      new TooFewTouchPointsDialog.Callback() {
        @Override
        public void onSwitchToNextIme() {
          switchToNextInputMethod();
        }
      };

  private final PhoneStateListener phoneStateListener =
      new PhoneStateListener() {
        @Override
        public void onCallStateChanged(int state, String incomingNumber) {
          if (state == TelephonyManager.CALL_STATE_RINGING) {
            // Close keyboard when phone call coming.
            if (keyboardView.isViewContainerCreated()) {
              hideSelf();
            }
          }
        }
      };

  private final BrailleImeForBrailleDisplay brailleImeForBrailleDisplay =
      new BrailleImeForBrailleDisplay() {
        @Override
        public void onBrailleDisplayConnected() {
          BrailleImeLog.d(TAG, "onBrailleDisplayConnected");
          brailleDisplayConnectedAndNotSuspended = true;
          updateInputView();
          activateBrailleIme();
          updateNavigationBarColor();
        }

        @Override
        public void onBrailleDisplayDisconnected() {
          BrailleImeLog.d(TAG, "onBrailleDisplayDisconnected");
          brailleDisplayConnectedAndNotSuspended = false;
          updateInputView();
          activateBrailleIme();
        }

        @Override
        public boolean sendBrailleDots(BrailleCharacter brailleCharacter) {
          keyboardView.getStripView().animateInput(brailleCharacter.toDotNumbers());
          boolean result;
          if (brailleCharacter.isEmpty()) {
            editBuffer.appendSpace(getImeConnection());
            result = true;
          } else if (brailleCharacter.equals(BrailleCharacter.DOT7)) {
            result = deleteBackward();
          } else if (brailleCharacter.equals(BrailleCharacter.DOT8)) {
            result = commitHoldingsAndPerformEnterKeyAction();
          } else {
            editBuffer.appendBraille(getImeConnection(), brailleCharacter);
            result = true;
          }
          showOnBrailleDisplay();
          return result;
        }

        @Override
        public boolean moveCursorForward() {
          boolean result = editBuffer.moveCursorForward(getImeConnection());
          if (!result) {
            result = talkBackForBrailleIme.performAction(ScreenReaderAction.FOCUS_NEXT_CHARACTER);
          }
          if (result) {
            showOnBrailleDisplay();
          }
          return result;
        }

        @Override
        public boolean moveCursorBackward() {
          boolean result = editBuffer.moveCursorBackward(getImeConnection());
          if (!result) {
            result =
                talkBackForBrailleIme.performAction(ScreenReaderAction.FOCUS_PREVIOUS_CHARACTER);
          }
          if (result) {
            showOnBrailleDisplay();
          }
          return result;
        }

        @Override
        public boolean moveCursorForwardByWord() {
          editBuffer.commit(getImeConnection());
          boolean result = talkBackForBrailleIme.performAction(ScreenReaderAction.FOCUS_NEXT_WORD);
          if (result) {
            showOnBrailleDisplay();
          }
          return result;
        }

        @Override
        public boolean moveCursorBackwardByWord() {
          editBuffer.commit(getImeConnection());
          // Commit takes time to get into the editor, post the backward movement to prevent
          // cursor movement ignoring the committed content.
          mainHandler.post(
              () -> talkBackForBrailleIme.performAction(ScreenReaderAction.FOCUS_PREVIOUS_WORD));
          showOnBrailleDisplay();
          return true;
        }

        @Override
        public boolean moveCursorForwardByLine() {
          if (!EditBufferUtils.isMultiLineField(getImeConnection().editorInfo.inputType)) {
            return false;
          }
          editBuffer.commit(getImeConnection());
          boolean result = talkBackForBrailleIme.performAction(ScreenReaderAction.FOCUS_NEXT_LINE);
          if (result) {
            showOnBrailleDisplay();
          }
          return result;
        }

        @Override
        public boolean moveCursorBackwardByLine() {
          if (!EditBufferUtils.isMultiLineField(getImeConnection().editorInfo.inputType)) {
            return false;
          }
          editBuffer.commit(getImeConnection());
          // Commit takes time to get into the editor, post the backward movement to prevent
          // cursor movement ignoring the committed content.
          mainHandler.post(
              () -> talkBackForBrailleIme.performAction(ScreenReaderAction.FOCUS_PREVIOUS_LINE));
          showOnBrailleDisplay();
          return true;
        }

        @Override
        public boolean moveTextFieldCursor(int toIndex) {
          boolean result = editBuffer.moveTextFieldCursor(getImeConnection(), toIndex);
          if (result) {
            showOnBrailleDisplay();
          }
          return result;
        }

        @Override
        public boolean moveHoldingsCursor(int toIndex) {
          boolean result = editBuffer.moveHoldingsCursor(getImeConnection(), toIndex);
          if (result) {
            showOnBrailleDisplay();
          }
          return result;
        }

        @Override
        public boolean moveCursorToBeginning() {
          boolean result = editBuffer.moveCursorToBeginning(getImeConnection());
          showOnBrailleDisplay();
          return result;
        }

        @Override
        public boolean moveCursorToEnd() {
          boolean result = editBuffer.moveCursorToEnd(getImeConnection());
          showOnBrailleDisplay();
          return result;
        }

        @Override
        public boolean deleteBackward() {
          editBuffer.deleteCharacterBackward(getImeConnection());
          showOnBrailleDisplay();
          return true;
        }

        @Override
        public boolean deleteWordBackward() {
          editBuffer.deleteWord(getImeConnection());
          showOnBrailleDisplay();
          return true;
        }

        @Override
        public boolean cutSelectedText() {
          boolean result = talkBackForBrailleIme.performAction(ScreenReaderAction.CUT);
          if (result) {
            showOnBrailleDisplay();
          }
          return result;
        }

        @Override
        public boolean copySelectedText() {
          boolean result = talkBackForBrailleIme.performAction(ScreenReaderAction.COPY);
          if (result) {
            showOnBrailleDisplay();
          }
          return result;
        }

        @Override
        public boolean pasteSelectedText() {
          boolean result = talkBackForBrailleIme.performAction(ScreenReaderAction.PASTE);
          if (result) {
            showOnBrailleDisplay();
          }
          return result;
        }

        @Override
        public boolean selectAllText() {
          boolean result = editBuffer.selectAllText(getImeConnection());
          if (result) {
            showOnBrailleDisplay();
          }
          return result;
        }

        @Override
        public boolean selectCurrentToStart() {
          commitHoldings();
          boolean result =
              talkBackForBrailleIme.performAction(ScreenReaderAction.SELECT_CURRENT_TO_START);
          if (result) {
            showOnBrailleDisplay();
          }
          return result;
        }

        @Override
        public boolean selectCurrentToEnd() {
          commitHoldings();
          boolean result =
              talkBackForBrailleIme.performAction(ScreenReaderAction.SELECT_CURRENT_TO_END);
          if (result) {
            showOnBrailleDisplay();
          }
          return result;
        }

        @Override
        public boolean selectPreviousCharacter() {
          commitHoldings();
          boolean result =
              talkBackForBrailleIme.performAction(ScreenReaderAction.SELECT_PREVIOUS_CHARACTER);
          if (result) {
            showOnBrailleDisplay();
          }
          return result;
        }

        @Override
        public boolean selectNextCharacter() {
          commitHoldings();
          boolean result =
              talkBackForBrailleIme.performAction(ScreenReaderAction.SELECT_NEXT_CHARACTER);
          if (result) {
            showOnBrailleDisplay();
          }
          return result;
        }

        @Override
        public boolean selectPreviousWord() {
          commitHoldings();
          boolean result =
              talkBackForBrailleIme.performAction(ScreenReaderAction.SELECT_PREVIOUS_WORD);
          if (result) {
            showOnBrailleDisplay();
          }
          return result;
        }

        @Override
        public boolean selectNextWord() {
          commitHoldings();
          boolean result = talkBackForBrailleIme.performAction(ScreenReaderAction.SELECT_NEXT_WORD);
          if (result) {
            showOnBrailleDisplay();
          }
          return result;
        }

        @Override
        public boolean selectPreviousLine() {
          commitHoldings();
          boolean result =
              talkBackForBrailleIme.performAction(ScreenReaderAction.SELECT_PREVIOUS_LINE);
          if (result) {
            showOnBrailleDisplay();
          }
          return result;
        }

        @Override
        public boolean selectNextLine() {
          commitHoldings();
          boolean result = talkBackForBrailleIme.performAction(ScreenReaderAction.SELECT_NEXT_LINE);
          if (result) {
            showOnBrailleDisplay();
          }
          return result;
        }

        @Override
        public void commitHoldings() {
          if (editBuffer != null) {
            editBuffer.commit(getImeConnection());
          }
        }

        @Override
        public boolean commitHoldingsAndPerformEditorAction() {
          commitHoldings();
          performEditorAction(getImeConnection().inputConnection);
          return true;
        }

        @Override
        public boolean commitHoldingsAndPerformEnterKeyAction() {
          commitHoldings();
          if (getCurrentInputConnection()
              .sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))) {
            return getCurrentInputConnection()
                .sendKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER));
          }
          return false;
        }

        @Override
        public boolean switchToNextInputMethod() {
          return BrailleIme.this.switchToNextInputMethod();
        }

        @Override
        public void hideKeyboard() {
          hideSelf();
        }

        @Override
        public void updateResultForDisplay() {
          showOnBrailleDisplay();
        }

        @Override
        public boolean isBrailleKeyboardActivated() {
          return isInputViewShown();
        }

        @Override
        public boolean handleBrailleKeyForBARDMobile(int keyCode) {
          // Only handle Bard application.
          if (getCurrentInputEditorInfo() == null
              || !Objects.equals(getCurrentInputEditorInfo().packageName, BARD_PACKAGE_NAME)) {
            return false;
          }
          // To allow BARD Mobile to receive keyboard shortcuts, must use English computer braille.
          // See BARD Mobile keyboard shortcuts:
          // https://nlsbard.loc.gov/apidocs/BARDMobile.userguide.iOS.1.0.html#BrailleShortcutKeys7.3
          BrailleTranslator translator =
              BrailleUserPreferences.readTranslatorFactory(BrailleIme.this)
                  .create(BrailleIme.this, Code.EN_NABCC.name(), /* contractedMode= */ false);
          String key = translator.translateToPrint(new BrailleWord(new byte[] {(byte) keyCode}));
          return getCurrentInputConnection().commitText(key, /* newCursorPosition= */ 1);
        }
      };

  @VisibleForTesting
  public ContextMenuDialog.Callback testing_getContextMenuDialogCallback() {
    return contextMenuDialogCallback;
  }

  @VisibleForTesting
  public BrailleImeForTalkBack testing_getBrailleImeForTalkBack() {
    return brailleImeForTalkBack;
  }

  @VisibleForTesting
  public BrailleInputView.Callback testing_getInputPlaneCallback() {
    return inputPlaneCallback;
  }

  @VisibleForTesting
  public BrailleImeGestureController.Callback testing_getGestureCallback() {
    return brailleImeGestureCallback;
  }

  @VisibleForTesting
  public void testing_setGestureController(BrailleImeGestureController gestureController) {
    this.brailleImeGestureController = gestureController;
  }

  @VisibleForTesting
  public BrailleImeGestureController testing_getGestureController() {
    return brailleImeGestureController;
  }

  @VisibleForTesting
  public void testing_setEditBuffer(EditBuffer editBuffer) {
    this.editBuffer = editBuffer;
  }

  @VisibleForTesting
  public KeyboardView testing_getKeyboardView() {
    return keyboardView;
  }

  @VisibleForTesting
  public void testing_setBrailleImeAnalytics(BrailleImeAnalytics brailleImeAnalytics) {
    this.brailleImeAnalytics = brailleImeAnalytics;
  }

  @VisibleForTesting
  public void testing_setTalkBackOffDialog(TalkBackOffDialog dialog) {
    talkbackOffDialog = dialog;
  }

  @VisibleForTesting
  public BrailleDisplayImeStripView.CallBack testing_getStripViewCallback() {
    return brailleDisplayKeyboardCallback;
  }

  @VisibleForTesting
  public BrailleDisplayForBrailleIme testing_getBrailleDisplayForBrailleIme() {
    return brailleDisplayForBrailleIme;
  }

  @VisibleForTesting
  public void testing_setTutorialState(State state) {
    tutorialState = state;
  }
}
