/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.google.android.accessibility.talkback;

import static com.google.android.accessibility.compositor.EventFilter.PREF_ECHO_CHARACTERS;
import static com.google.android.accessibility.compositor.EventFilter.PREF_ECHO_CHARACTERS_AND_WORDS;
import static com.google.android.accessibility.talkback.Feedback.ContinuousRead.Action.START_AT_NEXT;
import static com.google.android.accessibility.talkback.Feedback.ContinuousRead.Action.START_AT_TOP;
import static com.google.android.accessibility.talkback.Feedback.FocusDirection.Action.NEXT_GRANULARITY;
import static com.google.android.accessibility.talkback.Feedback.FocusDirection.Action.PREVIOUS_GRANULARITY;
import static com.google.android.accessibility.talkback.Feedback.PassThroughMode.Action.DISABLE_PASSTHROUGH;
import static com.google.android.accessibility.talkback.Feedback.PassThroughMode.Action.LOCK_PASS_THROUGH;
import static com.google.android.accessibility.talkback.actor.TalkBackUIActor.Type.SELECTOR_MENU_CONFIG_CHANGED;
import static com.google.android.accessibility.talkback.training.PageConfig.PageId.PAGE_ID_FINISHED;
import static com.google.android.accessibility.talkback.training.TrainingActivity.ACTION_TRAINING_PAGE_SWITCHED;
import static com.google.android.accessibility.talkback.training.TrainingActivity.EXTRA_TRAINING_PAGE_ID;
import static com.google.android.accessibility.utils.FeatureSupport.FLAG_REQUEST_2_FINGER_PASSTHROUGH;
import static com.google.android.accessibility.utils.PackageManagerUtils.TALBACK_PACKAGE;
import static com.google.android.accessibility.utils.Performance.EVENT_ID_UNTRACKED;
import static com.google.android.accessibility.utils.keyboard.KeyComboManager.ACTION_NAVIGATE_NEXT;
import static com.google.android.accessibility.utils.keyboard.KeyComboManager.ACTION_NAVIGATE_NEXT_DEFAULT;
import static com.google.android.accessibility.utils.keyboard.KeyComboManager.ACTION_NAVIGATE_PREVIOUS;
import static com.google.android.accessibility.utils.keyboard.KeyComboManager.ACTION_NAVIGATE_PREVIOUS_DEFAULT;
import static com.google.android.accessibility.utils.output.SpeechControllerImpl.CAPITAL_LETTERS_TYPE_SPEAK_CAP;

import android.accessibilityservice.AccessibilityGestureEvent;
import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityService.MagnificationController.OnMagnificationChangedListener;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.accessibilityservice.FingerprintGestureController;
import android.accessibilityservice.FingerprintGestureController.FingerprintGestureCallback;
import android.annotation.TargetApi;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Region;
import android.media.AudioManager;
import android.os.Build;
import android.provider.Settings;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import androidx.annotation.VisibleForTesting;
import com.android.talkback.TalkBackPreferencesActivity;
import com.google.android.accessibility.brailledisplay.BrailleDisplay;
import com.google.android.accessibility.brailleime.BrailleIme;
import com.google.android.accessibility.compositor.Compositor;
import com.google.android.accessibility.compositor.EventFilter;
import com.google.android.accessibility.compositor.EventFilter.KeyboardEchoType;
import com.google.android.accessibility.compositor.GlobalVariables;
import com.google.android.accessibility.talkback.Feedback.TalkBackUI.Action;
import com.google.android.accessibility.talkback.PrimesController.Timer;
import com.google.android.accessibility.talkback.actor.AutoScrollActor;
import com.google.android.accessibility.talkback.actor.DimScreenActor;
import com.google.android.accessibility.talkback.actor.DirectionNavigationActor;
import com.google.android.accessibility.talkback.actor.FocusActor;
import com.google.android.accessibility.talkback.actor.FocusActorForScreenStateChange;
import com.google.android.accessibility.talkback.actor.FocusActorForTapAndTouchExploration;
import com.google.android.accessibility.talkback.actor.FullScreenReadActor;
import com.google.android.accessibility.talkback.actor.GestureReporter;
import com.google.android.accessibility.talkback.actor.ImageCaptioner;
import com.google.android.accessibility.talkback.actor.LanguageActor;
import com.google.android.accessibility.talkback.actor.NodeActionPerformer;
import com.google.android.accessibility.talkback.actor.NumberAdjustor;
import com.google.android.accessibility.talkback.actor.PassThroughModeActor;
import com.google.android.accessibility.talkback.actor.SpeechRateActor;
import com.google.android.accessibility.talkback.actor.SystemActionPerformer;
import com.google.android.accessibility.talkback.actor.TalkBackUIActor;
import com.google.android.accessibility.talkback.actor.TextEditActor;
import com.google.android.accessibility.talkback.actor.VolumeAdjustor;
import com.google.android.accessibility.talkback.actor.search.SearchScreenNodeStrategy;
import com.google.android.accessibility.talkback.actor.search.SearchScreenOverlay;
import com.google.android.accessibility.talkback.actor.search.UniversalSearchManager;
import com.google.android.accessibility.talkback.actor.voicecommands.SpeechRecognizerActor;
import com.google.android.accessibility.talkback.actor.voicecommands.VoiceCommandProcessor;
import com.google.android.accessibility.talkback.brailledisplay.BrailleDisplayHelper;
import com.google.android.accessibility.talkback.contextmenu.ListMenuManager;
import com.google.android.accessibility.talkback.controller.SelectorController;
import com.google.android.accessibility.talkback.controller.TelevisionNavigationController;
import com.google.android.accessibility.talkback.eventprocessor.AccessibilityEventProcessor;
import com.google.android.accessibility.talkback.eventprocessor.AccessibilityEventProcessor.TalkBackListener;
import com.google.android.accessibility.talkback.eventprocessor.ProcessorAccessibilityHints;
import com.google.android.accessibility.talkback.eventprocessor.ProcessorCursorState;
import com.google.android.accessibility.talkback.eventprocessor.ProcessorEventQueue;
import com.google.android.accessibility.talkback.eventprocessor.ProcessorGestureVibrator;
import com.google.android.accessibility.talkback.eventprocessor.ProcessorMagnification;
import com.google.android.accessibility.talkback.eventprocessor.ProcessorPermissionDialogs;
import com.google.android.accessibility.talkback.eventprocessor.ProcessorPhoneticLetters;
import com.google.android.accessibility.talkback.eventprocessor.ProcessorScreen;
import com.google.android.accessibility.talkback.eventprocessor.ProcessorVolumeStream;
import com.google.android.accessibility.talkback.focusmanagement.AccessibilityFocusMonitor;
import com.google.android.accessibility.talkback.focusmanagement.interpreter.ScreenStateMonitor;
import com.google.android.accessibility.talkback.focusmanagement.interpreter.TouchExplorationInterpreter;
import com.google.android.accessibility.talkback.focusmanagement.record.AccessibilityFocusActionHistory;
import com.google.android.accessibility.talkback.gesture.GestureController;
import com.google.android.accessibility.talkback.gesture.GestureShortcutMapping;
import com.google.android.accessibility.talkback.interpreters.AccessibilityEventIdleInterpreter;
import com.google.android.accessibility.talkback.interpreters.AccessibilityFocusInterpreter;
import com.google.android.accessibility.talkback.interpreters.AutoScrollInterpreter;
import com.google.android.accessibility.talkback.interpreters.DirectionNavigationInterpreter;
import com.google.android.accessibility.talkback.interpreters.FullScreenReadInterpreter;
import com.google.android.accessibility.talkback.interpreters.InputFocusInterpreter;
import com.google.android.accessibility.talkback.interpreters.ManualScrollInterpreter;
import com.google.android.accessibility.talkback.interpreters.PassThroughModeInterpreter;
import com.google.android.accessibility.talkback.interpreters.ScrollPositionInterpreter;
import com.google.android.accessibility.talkback.interpreters.StateChangeEventInterpreter;
import com.google.android.accessibility.talkback.interpreters.SubtreeChangeEventInterpreter;
import com.google.android.accessibility.talkback.labeling.CustomLabelManager;
import com.google.android.accessibility.talkback.labeling.LabelDialogManager;
import com.google.android.accessibility.talkback.labeling.PackageRemovalReceiver;
import com.google.android.accessibility.talkback.menurules.NodeMenuRuleProcessor;
import com.google.android.accessibility.talkback.preference.TalkBackKeyboardShortcutPreferencesActivity;
import com.google.android.accessibility.talkback.speech.SpeakPasswordsManager;
import com.google.android.accessibility.talkback.training.OnboardingInitiator;
import com.google.android.accessibility.talkback.training.PageConfig;
import com.google.android.accessibility.talkback.training.PageConfig.PageId;
import com.google.android.accessibility.talkback.training.TutorialInitiator;
import com.google.android.accessibility.talkback.utils.DiagnosticOverlayControllerImpl;
import com.google.android.accessibility.talkback.utils.ExperimentalUtils;
import com.google.android.accessibility.talkback.utils.FocusIndicatorUtils;
import com.google.android.accessibility.talkback.utils.NotificationUtils;
import com.google.android.accessibility.talkback.utils.VerbosityPreferences;
import com.google.android.accessibility.utils.AccessibilityEventListener;
import com.google.android.accessibility.utils.AccessibilityEventUtils;
import com.google.android.accessibility.utils.AccessibilityNodeInfoUtils;
import com.google.android.accessibility.utils.AudioPlaybackMonitor;
import com.google.android.accessibility.utils.BuildVersionUtils;
import com.google.android.accessibility.utils.EditTextActionHistory;
import com.google.android.accessibility.utils.FeatureSupport;
import com.google.android.accessibility.utils.FocusFinder;
import com.google.android.accessibility.utils.HeadphoneStateMonitor;
import com.google.android.accessibility.utils.ImageContents;
import com.google.android.accessibility.utils.PackageManagerUtils;
import com.google.android.accessibility.utils.Performance;
import com.google.android.accessibility.utils.Performance.EventId;
import com.google.android.accessibility.utils.ProximitySensor;
import com.google.android.accessibility.utils.ScreenMonitor;
import com.google.android.accessibility.utils.ServiceKeyEventListener;
import com.google.android.accessibility.utils.ServiceStateListener;
import com.google.android.accessibility.utils.SettingsUtils;
import com.google.android.accessibility.utils.SharedKeyEvent;
import com.google.android.accessibility.utils.SharedPreferencesUtils;
import com.google.android.accessibility.utils.brailledisplay.BrailleDisplayForTalkBack;
import com.google.android.accessibility.utils.brailledisplay.TalkBackForBrailleDisplay;
import com.google.android.accessibility.utils.brailleime.BrailleImeForTalkBack;
import com.google.android.accessibility.utils.brailleime.TalkBackForBrailleIme;
import com.google.android.accessibility.utils.caption.ImageCaptionStorage;
import com.google.android.accessibility.utils.input.InputModeManager;
import com.google.android.accessibility.utils.input.TextCursorManager;
import com.google.android.accessibility.utils.keyboard.KeyComboManager;
import com.google.android.accessibility.utils.output.FeedbackController;
import com.google.android.accessibility.utils.output.SpeechController;
import com.google.android.accessibility.utils.output.SpeechController.SpeakOptions;
import com.google.android.accessibility.utils.output.SpeechController.UtteranceCompleteRunnable;
import com.google.android.accessibility.utils.output.SpeechControllerImpl;
import com.google.android.accessibility.utils.output.SpeechControllerImpl.CapitalLetterHandlingMethod;
import com.google.android.libraries.accessibility.utils.log.LogUtils;
import com.google.common.collect.ImmutableMap;
import java.lang.Thread.UncaughtExceptionHandler;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.checkerframework.checker.nullness.qual.Nullable;

/** An {@link AccessibilityService} that provides spoken, haptic, and audible feedback. */
public class TalkBackService extends AccessibilityService
    implements Thread.UncaughtExceptionHandler, SpeechController.Delegate, SharedKeyEvent.Listener {
  /** Accesses the current speech language. */
  public class SpeechLanguage {
    /** Gets the current speech language. */
    public @Nullable Locale getCurrentLanguage() {
      return TalkBackService.this.getUserPreferredLocale();
    }

    /**
     * Sets the current speech language.
     *
     * @param speechLanguage null is using the system language.
     */
    public void setCurrentLanguage(@Nullable Locale speechLanguage) {
      TalkBackService.this.setUserPreferredLocale(speechLanguage);
    }
  }

  /** Whether the user has seen the TalkBack tutorial. */
  public static final String PREF_FIRST_TIME_USER = "first_time_user";

  /** Permission required to perform gestures. */
  public static final String PERMISSION_TALKBACK = BuildConfig.TALKBACK_MAIN_PERMISSION;

  /** The intent action used to perform a custom gesture action. */
  public static final String ACTION_PERFORM_GESTURE_ACTION = "performCustomGestureAction";

  /**
   * The gesture action to pass with {@link #ACTION_PERFORM_GESTURE_ACTION} as a string extra.
   * Expected to be the name of the shortcut pref value, like R.strings.shortcut_value_previous
   */
  public static final String EXTRA_GESTURE_ACTION = "gestureAction";

  /** The intent action used to suspend TalkBack's control over D-pad KeyEvents. */
  public static final String ACTION_SUSPEND_DPAD_CONTROL =
      "com.google.android.marvin.talkback.action.suspendDPadControl";

  /** The intent action used to resume TalkBack's control over D-pad KeyEvents. */
  public static final String ACTION_RESUME_DPAD_CONTROL =
      "com.google.android.marvin.talkback.action.resumeDPadControl";

  /** Intent to open text-to-speech settings. */
  public static final String INTENT_TTS_SETTINGS = "com.android.settings.TTS_SETTINGS";

  /** Default interactive UI timeout in milliseconds. */
  public static final int DEFAULT_INTERACTIVE_UI_TIMEOUT_MILLIS = 10000;

  /** Action used to resume feedback. */
  private static final String ACTION_RESUME_FEEDBACK =
      "com.google.android.marvin.talkback.RESUME_FEEDBACK";

  /** Timeout to turn off TalkBack without waiting for callback from TTS. */
  private static final long TURN_OFF_TIMEOUT_MS = 5000;

  private static final long TURN_OFF_WAIT_PERIOD_MS = 1000;

  /** An active instance of TalkBack. */
  @Nullable private static TalkBackService instance = null;

  private static final String TAG = "TalkBackService";

  /**
   * List of key event processors. Processors in the list are sent the event in the order they were
   * added until a processor consumes the event.
   */
  private final List<ServiceKeyEventListener> keyEventListeners = new ArrayList<>();

  /** The current state of the service. */
  private int serviceState;

  /** Components to receive callbacks on changes in the service's state. */
  private List<ServiceStateListener> serviceStateListeners = new ArrayList<>();

  /** Controller for speech feedback. */
  private SpeechControllerImpl speechController;

  /** Controller for diagnostic overlay (developer mode). */
  private DiagnosticOverlayControllerImpl diagnosticOverlayController;

  /** Staged pipeline for separating interpreters, feedback-mappers, and actors. */
  private Pipeline pipeline;

  /** Controller for audio and haptic feedback. */
  private FeedbackController feedbackController;

  /** Watches the proximity sensor, and silences feedback when triggered. */
  private ProximitySensorListener proximitySensorListener;

  private PassThroughModeActor passThroughModeActor;
  private GlobalVariables globalVariables;
  private EventFilter eventFilter;
  private Compositor compositor;
  private FullScreenReadActor fullScreenReadActor;
  private EditTextActionHistory editTextActionHistory;

  /** Interface for monitoring current and previous cursor position in editable node */
  private TextCursorManager textCursorManager;

  /** Monitors the call state for the phone device. */
  private CallStateMonitor callStateMonitor;

  /** Monitors voice actions from other applications */
  private VoiceActionMonitor voiceActionMonitor;

  /** Maintains cursor state during explore-by-touch by working around EBT problems. */
  private ProcessorCursorState processorCursorState;

  /** Processor for allowing clicking on buttons in permissions dialogs. */
  private ProcessorPermissionDialogs processorPermissionsDialogs;

  /** Controller for manage keyboard commands */
  private KeyComboManager keyComboManager;

  /** Manager for showing radial menus. */
  private ListMenuManager menuManager;

  /** Manager for handling custom labels. */
  private CustomLabelManager labelManager;

  /** Manager for the screen search feature. */
  private UniversalSearchManager universalSearchManager;

  /** Orientation monitor for watching orientation changes. */
  private OrientationMonitor orientationMonitor;

  /** {@link BroadcastReceiver} for tracking the ringer and screen states. */
  private RingerModeAndScreenMonitor ringerModeAndScreenMonitor;

  /** {@link BroadcastReceiver} for tracking volume changes. */
  private VolumeMonitor volumeMonitor;

  /** {@link android.content.BroadcastReceiver} for tracking battery status changes. */
  private BatteryMonitor batteryMonitor;

  /** {@link BroadcastReceiver} for tracking headphone connected status changes. */
  private HeadphoneStateMonitor headphoneStateMonitor;

  /** Tracks changes to audio output and provides information on what types of audio are playing. */
  private AudioPlaybackMonitor audioPlaybackMonitor;

  /** Manages screen dimming */
  private DimScreenActor dimScreenController;

  /** The television controller; non-null if the device is a television (Android TV). */
  private TelevisionNavigationController televisionNavigationController;

  private TelevisionDPadManager televisionDPadManager;

  /** {@link BroadcastReceiver} for tracking package removals for custom label data consistency. */
  private PackageRemovalReceiver packageReceiver;

  /** The analytics instance, used for sending data to Google Analytics. */
  private TalkBackAnalyticsImpl analytics;

  /** Callback to be invoked when fingerprint gestures are being used for accessibility. */
  private FingerprintGestureCallback fingerprintGestureCallback;

  /** Controller for the selector */
  private SelectorController selectorController;

  /** Controller for handling gestures */
  private GestureController gestureController;

  /** Speech recognition wrapper for voice commands */
  private SpeechRecognizerActor speechRecognizer;

  /** Processor for voice commands */
  private VoiceCommandProcessor voiceCommandProcessor;

  /** Alert dialog shown when the user attempts to suspend feedback. */
  private TalkBackSuspendDialog talkBackSuspendDialog;

  /** Shared preferences used within TalkBack. */
  private SharedPreferences prefs;

  /** The system's uncaught exception handler */
  private UncaughtExceptionHandler systemUeh;

  /** The system feature if the device supports touch screen */
  private boolean supportsTouchScreen = true;

  /** Preference specifying when TalkBack should automatically resume. */
  private String automaticResume;

  /** Whether the current root node is dirty or not. */
  private boolean isRootNodeDirty = true;
  /** Keep Track of current root node. */
  private AccessibilityNodeInfo rootNode;

  private AccessibilityEventProcessor accessibilityEventProcessor;

  /** Keeps track of whether we need to run the locked-boot-completed callback when connected. */
  private boolean lockedBootCompletedPending;

  private final InputModeManager inputModeManager = new InputModeManager();
  private ProcessorAccessibilityHints processorHints;
  private ProcessorScreen processorScreen;
  private OnMagnificationChangedListener onMagnificationChangedListener;
  private final DisableTalkBackCompleteAction disableTalkBackCompleteAction =
      new DisableTalkBackCompleteAction();
  private SpeakPasswordsManager speakPasswordsManager;

  // Focus logic
  private AccessibilityFocusMonitor accessibilityFocusMonitor;
  private AccessibilityFocusInterpreter accessibilityFocusInterpreter;
  private FocusActor focuser;
  private InputFocusInterpreter inputFocusInterpreter;
  private ScrollPositionInterpreter scrollPositionInterpreter;
  private ScreenStateMonitor screenStateMonitor;
  private ProcessorEventQueue processorEventQueue;
  private ProcessorPhoneticLetters processorPhoneticLetters;

  /** A reference to the active Braille IME if any. */
  @Nullable private BrailleImeForTalkBack brailleImeForTalkBack;

  private BrailleDisplayForTalkBack brailleDisplay;

  private GestureShortcutMapping gestureShortcutMapping;
  private NodeMenuRuleProcessor nodeMenuRuleProcessor;
  private PrimesController primesController;
  private SpeechLanguage speechLanguage;
  private boolean isBrailleKeyboardActivated;
  private ImageCaptioner imageCaptioner;
  private ImageContents imageContents;

  @Override
  public void onCreate() {
    super.onCreate();

    this.setTheme(R.style.TalkbackBaseTheme);

    instance = this;
    setServiceState(ServiceStateListener.SERVICE_STATE_INACTIVE);

    systemUeh = Thread.getDefaultUncaughtExceptionHandler();
    Thread.setDefaultUncaughtExceptionHandler(this);
  }

  /**
   * Calculates the volume for {@link SpeechControllerImpl#setSpeechVolume(float)} when announcing
   * "TalkBack off".
   *
   * <p>TalkBack switches to use {@link AudioManager#STREAM_ACCESSIBILITY} from Android O. However,
   * when announcing "TalkBack off" before turning TalkBack off, the audio goes through {@link
   * AudioManager#STREAM_MUSIC}. It's because accessibility stream has already been shut down before
   * {@link #onUnbind(Intent)} is called.
   *
   * <p>To work around this issue, it's not recommended to directly override media stream volume.
   * Instead, we can adjust the relative TTS volume to match the original accessibility stream
   * volume.
   *
   * @return TTS volume in [0.0f, 1.0f].
   */
  private float calculateFinalAnnouncementVolume() {
    if (!FeatureSupport.hasAccessibilityAudioStream(this)) {
      return 1.0f;
    }
    AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);

    int musicStreamVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
    int musicStreamMaxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
    int accessibilityStreamVolume =
        (volumeMonitor == null) ? -1 : volumeMonitor.getCachedAccessibilityStreamVolume();
    int accessibilityStreamMaxVolume =
        (volumeMonitor == null) ? -1 : volumeMonitor.getCachedAccessibilityMaxVolume();
    if (musicStreamVolume <= 0
        || musicStreamMaxVolume <= 0
        || accessibilityStreamVolume < 0
        || accessibilityStreamMaxVolume <= 0) {
      // Do not adjust volume if music stream is muted, or when any volume is invalid.
      return 1.0f;
    }
    if (accessibilityStreamVolume == 0) {
      return 0.0f;
    }

    // Depending on devices/API level, a stream might have 7 steps or 15 steps adjustment.
    // We need to normalize the values to eliminate this difference.
    float musicVolumeFraction = (float) musicStreamVolume / musicStreamMaxVolume;
    float accessibilityVolumeFraction =
        (float) accessibilityStreamVolume / accessibilityStreamMaxVolume;
    if (musicVolumeFraction <= accessibilityVolumeFraction) {
      // Do not adjust volume when a11y stream volume is louder than music stream volume.
      return 1.0f;
    }

    // AudioManager measures the volume in dB scale, while TTS measures it in linear scale. We need
    // to apply exponential operation to map dB/logarithmic-scaled diff value into linear-scaled
    // multiplier value.
    // The dB scaling could be different based on devices/OEMs/streams, which is not under our
    // control.
    // What we can do is to try our best to adjust the volume and avoid sudden volume increase.
    // TODO: The parameters in Math.pow() are results from experiments. Feel free to change
    // them.
    return (float) Math.pow(10.0f, (accessibilityVolumeFraction - musicVolumeFraction) / 0.4f);
  }

  @Override
  public boolean onUnbind(Intent intent) {
    final long turningOffTime = System.currentTimeMillis();
    interruptAllFeedback(false /* stopTtsSpeechCompletely */);
    if (pipeline != null) {
      pipeline.onUnbind(calculateFinalAnnouncementVolume());
    }
    if (gestureShortcutMapping != null) {
      gestureShortcutMapping.onUnbind();
    }
    if (compositor != null) {
      compositor.handleEventWithCompletionHandler(
          Compositor.EVENT_SPOKEN_FEEDBACK_DISABLED,
          Performance.EVENT_ID_UNTRACKED,
          disableTalkBackCompleteAction);
    }
    while (true) {
      synchronized (disableTalkBackCompleteAction) {
        try {
          disableTalkBackCompleteAction.wait(TURN_OFF_WAIT_PERIOD_MS);
        } catch (InterruptedException e) {
          // Do nothing
        }
        if (System.currentTimeMillis() - turningOffTime > TURN_OFF_TIMEOUT_MS
            || disableTalkBackCompleteAction.isDone) {
          break;
        }
      }
    }
    return false;
  }

  @Override
  public void onDestroy() {
    if (passThroughModeActor != null) {
      passThroughModeActor.onDestroy();
    }
    super.onDestroy();

    SharedKeyEvent.unregister(this);

    if (isServiceActive()) {
      suspendInfrastructure();
    }

    instance = null;

    // Shutdown and unregister all components.
    shutdownInfrastructure();
    setServiceState(ServiceStateListener.SERVICE_STATE_INACTIVE);
    serviceStateListeners.clear();
    if (televisionNavigationController != null) {
      televisionNavigationController.onDestroy();
    }
  }

  @Override
  public void onConfigurationChanged(Configuration newConfig) {
    this.getTheme().applyStyle(R.style.TalkbackBaseTheme, /* force= */ true);

    // onConfigurationChanged may be called before TalkBack initialization. To avoid crash, each
    // listener should checks the instance is null or not.
    if (universalSearchManager != null) {
      universalSearchManager.renewOverlay(newConfig);
    }

    if (isServiceActive() && (orientationMonitor != null)) {
      orientationMonitor.onConfigurationChanged(newConfig);
    }

    if (gestureShortcutMapping != null) {
      gestureShortcutMapping.onConfigurationChanged(newConfig);
    }

    if (pipeline != null) {
      resetTouchExplorePassThrough();
      // TODO: Uses the new TalkBackUI action, CONFIG_CHANGED, when b/170600530 is
      // fixed.
      pipeline
          .getFeedbackReturner()
          .returnFeedback(
              EVENT_ID_UNTRACKED,
              Feedback.talkBackUI(Action.SHOW_SELECTOR_UI, SELECTOR_MENU_CONFIG_CHANGED));
    }

    if (brailleDisplay != null) {
      brailleDisplay.onConfigurationChanged(newConfig);
    }
  }

  @Override
  public void onAccessibilityEvent(AccessibilityEvent event) {
    Performance perf = Performance.getInstance();
    EventId eventId = perf.onEventReceived(event);
    accessibilityEventProcessor.onAccessibilityEvent(event, eventId);
    perf.onHandlerDone(eventId);

    if (brailleDisplay != null) {
      brailleDisplay.onAccessibilityEvent(event);
    }
  }

  public boolean supportsTouchScreen() {
    return supportsTouchScreen;
  }

  @Override
  public AccessibilityNodeInfo getRootInActiveWindow() {
    if (isRootNodeDirty || rootNode == null) {
      rootNode = super.getRootInActiveWindow();
      isRootNodeDirty = false;
    }
    return rootNode == null ? null : AccessibilityNodeInfo.obtain(rootNode);
  }

  public void setRootDirty(boolean rootIsDirty) {
    isRootNodeDirty = rootIsDirty;
  }

  private void setServiceState(int newState) {
    if (serviceState == newState) {
      return;
    }

    serviceState = newState;
    for (ServiceStateListener listener : serviceStateListeners) {
      listener.onServiceStateChanged(newState);
    }
  }

  public void addServiceStateListener(ServiceStateListener listener) {
    if (listener != null) {
      serviceStateListeners.add(listener);
    }
  }

  public void removeServiceStateListener(ServiceStateListener listener) {
    if (listener != null) {
      serviceStateListeners.remove(listener);
    }
  }

  /** Stops all delayed events in the service. */
  public void clearQueues() {
    interruptAllFeedback(/* stopTtsSpeechCompletely= */ false);
    processorEventQueue.clearQueue();
    if (processorScreen != null && processorScreen.getWindowEventInterpreter() != null) {
      processorScreen.getWindowEventInterpreter().clearQueue();
    }
    // TODO: Clear queues wherever there are message handlers that delay event processing.
  }

  /** Suspends TalkBack, showing a confirmation dialog if applicable. */
  public void requestSuspendTalkBack(EventId eventId) {
    if (talkBackSuspendDialog == null) {
      talkBackSuspendDialog = new TalkBackSuspendDialog(this);
    }

    final boolean showConfirmation = talkBackSuspendDialog.getShouldShowDialogPref();
    if (showConfirmation) {
      // Shows a dialog asking the user to confirm suspension of TalkBack.
      talkBackSuspendDialog.confirmSuspendTalkBack(automaticResume);
    } else {
      suspendTalkBack(eventId);
    }
  }

  /** Suspends TalkBack and Explore by Touch. */
  public void suspendTalkBack(EventId eventId) {

    // Ensure that talkback does not suspend on system with accessibility shortcut.
    if (FeatureSupport.hasAccessibilityShortcut(this)) {
      SharedPreferencesUtils.storeBooleanAsync(prefs, getString(R.string.pref_suspended), false);
      return;
    }

    if (!isServiceActive()) {
      LogUtils.e(TAG, "Attempted to suspend TalkBack while already suspended.");
      return;
    }

    SharedPreferencesUtils.storeBooleanAsync(prefs, getString(R.string.pref_suspended), true);
    feedbackController.playAuditory(R.raw.paused_feedback, eventId);

    if (supportsTouchScreen) {
      requestTouchExploration(false);
    }

    inputModeManager.clear();

    final IntentFilter filter = new IntentFilter();
    filter.addAction(ACTION_RESUME_FEEDBACK);
    filter.addAction(Intent.ACTION_SCREEN_ON);
    registerReceiver(suspendedReceiver, filter, PERMISSION_TALKBACK, null);

    // Suspending infrastructure sets sIsTalkBackSuspended to true.
    suspendInfrastructure();

    final Intent resumeIntent = new Intent(ACTION_RESUME_FEEDBACK);
    resumeIntent.setPackage(TALBACK_PACKAGE);
    final PendingIntent pendingIntent =
        PendingIntent.getBroadcast(
            this,
            0,
            resumeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    final Notification notification =
        NotificationUtils.createNotification(
            this,
            null,
            getString(R.string.notification_title_talkback_suspended),
            getString(R.string.notification_message_talkback_suspended),
            pendingIntent);
    startForeground(R.id.notification_suspended, notification);

    compositor.handleEvent(Compositor.EVENT_SPOKEN_FEEDBACK_SUSPENDED, eventId);
  }

  /** Resumes TalkBack and Explore by Touch. */
  public void resumeTalkBack(EventId eventId) {
    if (isServiceActive()) {
      LogUtils.e(TAG, "Attempted to resume TalkBack when not suspended.");
      return;
    }

    SharedPreferencesUtils.storeBooleanAsync(prefs, getString(R.string.pref_suspended), false);

    unregisterReceiver(suspendedReceiver);
    resumeInfrastructure();

    compositor.handleEvent(Compositor.EVENT_SPOKEN_FEEDBACK_RESUMED, eventId);
  }

  private boolean shouldInterruptByAnyKeyEvent() {
    return !fullScreenReadActor.isActive();
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
    if (keyEvent.getKeyCode() == KeyEvent.KEYCODE_UNKNOWN) {
      // Tapping on fingerprint sensor somehow files KeyEvent with KEYCODE_UNKNOWN, which will
      // change input mode to keyboard, and cancel pending accessibility hints. It is OK to just
      // ignore these KeyEvents since they're unused in TalkBack.
      return false;
    }
    if (keyEvent.getAction() == KeyEvent.ACTION_DOWN) {
      eventFilter.setLastKeyEventTime(keyEvent.getEventTime());
    }
    Performance perf = Performance.getInstance();
    EventId eventId = perf.onEventReceived(keyEvent);

    if (isServiceActive()) {
      // Stop the TTS engine when any key (except for volume up/down key) is pressed on physical
      // keyboard.
      if (shouldInterruptByAnyKeyEvent()
          && keyEvent.getDeviceId() != 0
          && keyEvent.getAction() == KeyEvent.ACTION_DOWN
          && keyEvent.getKeyCode() != KeyEvent.KEYCODE_VOLUME_DOWN
          && keyEvent.getKeyCode() != KeyEvent.KEYCODE_VOLUME_UP) {
        interruptAllFeedback(false /* stopTtsSpeechCompletely */);
      }
    }

    for (ServiceKeyEventListener listener : keyEventListeners) {
      if (!isServiceActive() && !listener.processWhenServiceSuspended()) {
        continue;
      }

      if (listener.onKeyEvent(keyEvent, eventId)) {
        perf.onHandlerDone(eventId);
        return true;
      }
    }

    return false;
  }

  @Override
  protected boolean onKeyEvent(KeyEvent keyEvent) {
    return SharedKeyEvent.onKeyEvent(this, keyEvent);
  }

  @Override
  protected boolean onGesture(int gestureId) {
    return handleOnGestureById(gestureId);
  }

  @Override
  public boolean onGesture(AccessibilityGestureEvent accessibilityGestureEvent) {
    if (handleOnGestureById(accessibilityGestureEvent.getGestureId())) {
      pipeline
          .getFeedbackReturner()
          .returnFeedback(
              Performance.EVENT_ID_UNTRACKED, Feedback.saveGesture(accessibilityGestureEvent));
      return true;
    }
    return false;
  }

  private boolean handleOnGestureById(int gestureId) {
    if (!isServiceActive()) {
      return false;
    }
    Performance perf = Performance.getInstance();
    EventId eventId = perf.onGestureEventReceived(gestureId);
    primesController.startTimer(Timer.GESTURE_EVENT);

    analytics.onGesture(gestureId);
    feedbackController.playAuditory(R.raw.gesture_end, eventId);

    gestureController.onGesture(gestureId, eventId);

    // Measure latency.
    // Preceding event handling frequently initiates a framework action, which in turn
    // cascades a focus event, which in turn generates feedback.
    perf.onHandlerDone(eventId);
    primesController.stopTimer(Timer.GESTURE_EVENT);
    return true;
  }

  public GestureController getGestureController() {
    if (gestureController == null) {
      throw new RuntimeException("mGestureController has not been initialized");
    }

    return gestureController;
  }

  // TODO: As controller logic moves to pipeline, delete this function.
  public SpeechControllerImpl getSpeechController() {
    if (speechController == null) {
      throw new RuntimeException("mSpeechController has not been initialized");
    }

    return speechController;
  }

  public FeedbackController getFeedbackController() {
    if (feedbackController == null) {
      throw new RuntimeException("mFeedbackController has not been initialized");
    }

    return feedbackController;
  }

  public VoiceActionMonitor getVoiceActionMonitor() {
    if (voiceActionMonitor == null) {
      throw new RuntimeException("mVoiceActionMonitor has not been initialized");
    }

    return voiceActionMonitor;
  }

  public KeyComboManager getKeyComboManager() {
    return keyComboManager;
  }

  public CustomLabelManager getLabelManager() {
    if (labelManager == null) {
      throw new RuntimeException("mLabelManager has not been initialized");
    }

    return labelManager;
  }

  public TalkBackAnalyticsImpl getAnalytics() {
    if (analytics == null) {
      throw new RuntimeException("mAnalytics has not been initialized");
    }

    return analytics;
  }

  /**
   * Obtains the shared instance of TalkBack's {@link TelevisionNavigationController} if the current
   * device is a television. Otherwise returns {@code null}.
   */
  public TelevisionNavigationController getTelevisionNavigationController() {
    return televisionNavigationController;
  }

  @VisibleForTesting
  public TextCursorManager getTextCursorManager() {
    return textCursorManager;
  }

  @VisibleForTesting
  public RingerModeAndScreenMonitor getRingerModeAndScreenMonitor() {
    return ringerModeAndScreenMonitor;
  }

  @VisibleForTesting
  public GlobalVariables getGlobalVariables() {
    return globalVariables;
  }

  @VisibleForTesting
  public ProcessorScreen getProcessorScreen() {
    return processorScreen;
  }

  /** Registers the dialog to {@link RingerModeAndScreenMonitor} for screen monitor. */
  public void registerDialog(DialogInterface dialog) {
    if (ringerModeAndScreenMonitor != null) {
      ringerModeAndScreenMonitor.registerDialog(dialog);
    }
  }

  /** Unregisters the dialog from {@link RingerModeAndScreenMonitor} for screen monitor. */
  public void unregisterDialog(DialogInterface dialog) {
    if (ringerModeAndScreenMonitor != null) {
      ringerModeAndScreenMonitor.unregisterDialog(dialog);
    }
  }

  private void showTalkbackContextMenu(EventId eventId) {
    if (supportsTouchScreen) {
      menuManager.showMenu(R.menu.context_menu, eventId);
    }
  }

  private void showCustomActions(EventId eventId) {
    if (supportsTouchScreen) {
      menuManager.showMenu(R.id.custom_action_menu, eventId);
    }
  }

  private void showLanguageOptions(EventId eventId) {
    if (supportsTouchScreen) {
      menuManager.showMenu(R.menu.language_menu, eventId);
    }
  }

  private void openManageKeyboardShortcuts() {
    Intent intent = new Intent(this, TalkBackKeyboardShortcutPreferencesActivity.class);
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    startActivity(intent);
  }

  private void openTalkBackSettings() {
    Intent intent = new Intent(this, TalkBackPreferencesActivity.class);
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    startActivity(intent);
  }

  @Override
  public void onInterrupt() {
    if (processorScreen != null && FeatureSupport.isArc()) {
      // In Arc, we consider that focus goes out from Arc when onInterrupt is called.
      processorScreen.clearScreenState();
    }
    interruptAllFeedback(false /* stopTtsSpeechCompletely */);
  }

  @Override
  public boolean isAudioPlaybackActive() {
    return voiceActionMonitor.isAudioPlaybackActive();
  }

  @Override
  public boolean isMicrophoneActiveAndHeadphoneOff() {
    return voiceActionMonitor.isMicrophoneActiveAndHeadphoneOff();
  }

  @Override
  public boolean isSsbActiveAndHeadphoneOff() {
    return voiceActionMonitor.isSsbActiveAndHeadphoneOff();
  }

  @Override
  public boolean isPhoneCallActive() {
    return voiceActionMonitor.isPhoneCallActive();
  }

  @Override
  public void onSpeakingForcedFeedback() {
    voiceActionMonitor.onSpeakingForcedFeedback();
  }

  // Interrupts all Talkback feedback. Stops speech from other apps if stopTtsSpeechCompletely
  // is true.
  public void interruptAllFeedback(boolean stopTtsSpeechCompletely) {

    if (fullScreenReadActor != null) {
      fullScreenReadActor.interrupt();
    }

    if (pipeline != null) {
      pipeline.interruptAllFeedback(stopTtsSpeechCompletely);
    }
  }

  @Override
  protected void onServiceConnected() {
    LogUtils.v(TAG, "System bound to service.");

    primesController = new PrimesController();
    primesController.initialize(getApplication());
    primesController.startTimer(Timer.START_UP);

    SharedPreferencesUtils.migrateSharedPreferences(this);
    prefs = SharedPreferencesUtils.getSharedPreferences(this);
    initializeInfrastructure();
    SharedKeyEvent.register(this);

    // Configure logs.
    LogUtils.setTagPrefix("talkback: ");
    LogUtils.setParameterCustomizer(
        (object) -> {
          if (object instanceof AccessibilityNodeInfoCompat) {
            return AccessibilityNodeInfoUtils.toStringShort((AccessibilityNodeInfoCompat) object)
                + "    ";
          } else if (object instanceof AccessibilityNodeInfo) {
            return AccessibilityNodeInfoUtils.toStringShort((AccessibilityNodeInfo) object)
                + "    ";
          } else if (object instanceof AccessibilityEvent) {
            return AccessibilityEventUtils.toStringShort((AccessibilityEvent) object) + "    ";
          } else {
            return object;
          }
        });

    // The service must be connected before getFingerprintGestureController() is called, thus we
    // cannot initialize fingerprint gesture detection in onCreate().
    initializeFingerprintGestureCallback();

    resumeInfrastructure();

    // Handle any update actions.
    final TalkBackUpdateHelper helper = new TalkBackUpdateHelper(this);
    helper.checkUpdate();

    EventId eventId = EVENT_ID_UNTRACKED; // Performance not tracked for service events.
    if (prefs.getBoolean(getString(R.string.pref_suspended), false)) {
      if (FeatureSupport.hasAccessibilityShortcut(this)) {
        // Announce that talkback is still on. Even though talkback is not suspendable on android O,
        // talkback might start suspended if user downgrades, suspends, then upgrades talkback, or
        // it could happen if user restored settings from older talkback that was suspended.
        SharedPreferencesUtils.storeBooleanAsync(prefs, getString(R.string.pref_suspended), false);
        compositor.handleEvent(Compositor.EVENT_SPOKEN_FEEDBACK_ON, eventId);
      } else {
        suspendTalkBack(eventId);
      }
    } else {
      compositor.handleEvent(Compositor.EVENT_SPOKEN_FEEDBACK_ON, eventId);
    }

    // If the locked-boot-completed intent was fired before onServiceConnected, we queued it,
    // so now we need to run it.
    if (lockedBootCompletedPending) {
      onLockedBootCompletedInternal(eventId);
      lockedBootCompletedPending = false;
    }

    // Shows tutorial or onboarding.
    if (showTutorialIfNecessary()) {
      // Avoids showing onboarding when user turns on TalkBack for the second time.
      OnboardingInitiator.ignoreOnboarding(this);
      return;
    }
    if (!FeatureSupport.isTv(getApplicationContext())
        && !FeatureSupport.isWatch(getApplicationContext())) {
      OnboardingInitiator.showOnboarding91IfNecessary(this);
    }

    primesController.stopTimer(Timer.START_UP);
  }

  /**
   * @return The current state of the TalkBack service, or {@code INACTIVE} if the service is not
   *     initialized.
   */
  public static int getServiceState() {
    final TalkBackService service = getInstance();
    if (service == null) {
      return ServiceStateListener.SERVICE_STATE_INACTIVE;
    }

    return service.serviceState;
  }

  /**
   * Whether the current TalkBackService instance is running and initialized. This method is useful
   * for testing because it can be overridden by mocks.
   */
  public boolean isInstanceActive() {
    return serviceState == ServiceStateListener.SERVICE_STATE_ACTIVE;
  }

  /** @return {@code true} if TalkBack is running and initialized, {@code false} otherwise. */
  public static boolean isServiceActive() {
    return (getServiceState() == ServiceStateListener.SERVICE_STATE_ACTIVE);
  }

  /** Returns the active TalkBack instance, or {@code null} if not available. */
  public static @Nullable TalkBackService getInstance() {
    return instance;
  }

  /** Initialize {@link FingerprintGestureCallback} for detecting fingerprint gestures. */
  @TargetApi(Build.VERSION_CODES.O)
  private void initializeFingerprintGestureCallback() {
    if (fingerprintGestureCallback != null || !FeatureSupport.isFingerprintSupported(this)) {
      return;
    }
    fingerprintGestureCallback =
        new FingerprintGestureCallback() {
          @Override
          public void onGestureDetected(int gesture) {
            if (isServiceActive() && gestureController != null) {
              Performance perf = Performance.getInstance();
              EventId eventId = perf.onFingerprintGestureEventReceived(gesture);

              LogUtils.v(TAG, "Recognized fingerprint gesture %s", gesture);

              // TODO: Update analytics data.
              // TODO: Check if we should dismiss radial menu.
              feedbackController.playAuditory(R.raw.gesture_end, eventId);

              gestureController.onFingerprintGesture(gesture, eventId);

              // Measure latency.
              // Preceding event handling frequently initiates a framework action, which in turn
              // cascades a focus event, which in turn generates feedback.
              perf.onHandlerDone(eventId);
            }
          }

          @Override
          public void onGestureDetectionAvailabilityChanged(boolean available) {
            LogUtils.v(
                TAG,
                "Fingerprint gesture detection is now "
                    + (available ? "available" : "unavailable")
                    + ".");
          }
        };
  }

  /**
   * Initializes the controllers, managers, and processors. This should only be called once from
   * {@link #onServiceConnected()}.
   */
  private void initializeInfrastructure() {
    // TODO: we still need it keep true for TV until TouchExplore and Accessibility focus is
    // not unpaired
    // supportsTouchScreen = packageManager.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN);

    accessibilityEventProcessor = new AccessibilityEventProcessor(this);
    feedbackController = new FeedbackController(this);
    speechController = new SpeechControllerImpl(this, this, feedbackController);
    diagnosticOverlayController = new DiagnosticOverlayControllerImpl(this);

    keyComboManager = KeyComboManager.create(this);
    keyComboManager.addListener(keyComboListener);
    keyComboManager.setKeyUpListener(keyUpListener);

    gestureShortcutMapping = new GestureShortcutMapping(this);

    globalVariables =
        new GlobalVariables(this, inputModeManager, keyComboManager, gestureShortcutMapping);

    labelManager = new CustomLabelManager(this);
    addEventListener(labelManager);

    ImageCaptionStorage imageCaptionStorage = new ImageCaptionStorage();
    imageContents =
        ImageCaptioner.supportsImageCaption()
            ? new ImageContents(labelManager, imageCaptionStorage)
            : new ImageContents(labelManager, /* imageCaptionStorage= */ null);

    compositor =
        new Compositor(
            this,
            /* speechController= */ null,
            imageContents,
            globalVariables,
            getCompositorFlavor());
    // TODO: Make pipeline run Compositor, which returns speech feedback, no callback.

    onMagnificationChangedListener =
        FeatureSupport.supportMagnificationController()
            ? new OnMagnificationChangedListener() {
              private float lastScale = 1.0f;

              @Override
              public void onMagnificationChanged(
                  MagnificationController magnificationController,
                  Region region,
                  float scale,
                  float centerX,
                  float centerY) {
                // Do nothing if scale hasn't changed.
                if (scale == lastScale) {
                  return;
                }

                globalVariables.setScreenMagnificationLastScale(lastScale);
                globalVariables.setScreenMagnificationCurrentScale(scale);

                lastScale = scale;
                if (FeatureSupport.supportAnnounceMagnificationChanged()) {
                  compositor.handleEvent(
                      Compositor.EVENT_SCREEN_MAGNIFICATION_CHANGED,
                      Performance.EVENT_ID_UNTRACKED);
                }
              }
            }
            : null;

    analytics = new TalkBackAnalyticsImpl(this);

    processorPhoneticLetters = new ProcessorPhoneticLetters(this);

    FocusFinder focusFinder = new FocusFinder(this);

    // Construct event-interpreters.
    AutoScrollInterpreter autoScrollInterpreter = new AutoScrollInterpreter();
    screenStateMonitor = new ScreenStateMonitor(/* service= */ this);
    FullScreenReadInterpreter fullScreenReadInterpreter = new FullScreenReadInterpreter();
    scrollPositionInterpreter = new ScrollPositionInterpreter();

    // Constructor output-actor-state.
    textCursorManager = new TextCursorManager();
    editTextActionHistory = new EditTextActionHistory();
    AccessibilityFocusActionHistory focusHistory = new AccessibilityFocusActionHistory();

    // Construct output-actors.
    AutoScrollActor scroller = new AutoScrollActor();
    accessibilityFocusMonitor =
        new AccessibilityFocusMonitor(this, focusFinder, focusHistory.reader);

    imageCaptioner = new ImageCaptioner(this, imageCaptionStorage, accessibilityFocusMonitor);

    // TODO: ScreenState should be passed through pipeline.
    focuser =
        new FocusActor(
            this, focusFinder, screenStateMonitor.state, focusHistory, accessibilityFocusMonitor);
    DirectionNavigationActor directionNavigationActor =
        new DirectionNavigationActor(
            inputModeManager,
            globalVariables,
            analytics,
            compositor,
            this,
            focusFinder,
            processorPhoneticLetters,
            accessibilityFocusMonitor,
            screenStateMonitor.state);
    TextEditActor editor =
        new TextEditActor(
            this,
            editTextActionHistory,
            textCursorManager,
            getSystemService(ClipboardManager.class));
    fullScreenReadActor =
        new FullScreenReadActor(accessibilityFocusMonitor, this, speechController);
    dimScreenController = new DimScreenActor(this, gestureShortcutMapping);

    accessibilityFocusInterpreter =
        new AccessibilityFocusInterpreter(accessibilityFocusMonitor, screenStateMonitor.state);

    inputFocusInterpreter =
        new InputFocusInterpreter(accessibilityFocusInterpreter, focusFinder, globalVariables);

    proximitySensorListener = new ProximitySensorListener(/* service= */ this);
    speechLanguage = new SpeechLanguage();

    DirectionNavigationInterpreter directionNavigationInterpreter =
        new DirectionNavigationInterpreter(this);

    processorHints = new ProcessorAccessibilityHints();
    addEventListener(processorHints);
    keyEventListeners.add(0, processorHints); // Needs to be first; will not catch any events.

    passThroughModeActor = new PassThroughModeActor(this);

    selectorController =
        new SelectorController(
            this, accessibilityFocusMonitor, analytics, gestureShortcutMapping, processorHints);

    voiceCommandProcessor =
        new VoiceCommandProcessor(this, accessibilityFocusMonitor, selectorController, analytics);
    speechRecognizer = new SpeechRecognizerActor(this, voiceCommandProcessor, analytics);

    UserInterface userInterface = new UserInterface(selectorController);
    // Construct pipeline.
    pipeline =
        new Pipeline(
            this,
            new Interpreters(
                inputFocusInterpreter,
                autoScrollInterpreter,
                scrollPositionInterpreter,
                accessibilityFocusInterpreter,
                fullScreenReadInterpreter,
                new StateChangeEventInterpreter(),
                directionNavigationInterpreter,
                processorHints,
                voiceCommandProcessor,
                new PassThroughModeInterpreter(),
                new SubtreeChangeEventInterpreter(screenStateMonitor.state),
                new AccessibilityEventIdleInterpreter()),
            new Mappers(this, compositor, focusFinder),
            new Actors(
                this,
                accessibilityFocusMonitor,
                dimScreenController,
                speechController,
                fullScreenReadActor,
                feedbackController,
                scroller,
                focuser,
                new FocusActorForScreenStateChange(focusFinder, primesController),
                new FocusActorForTapAndTouchExploration(),
                directionNavigationActor,
                new SearchScreenNodeStrategy(/* observer= */ null, labelManager),
                editor,
                labelManager,
                new NodeActionPerformer(),
                new SystemActionPerformer(this),
                new LanguageActor(this, speechLanguage),
                passThroughModeActor,
                new TalkBackUIActor(this),
                new SpeechRateActor(this),
                new NumberAdjustor(this, accessibilityFocusMonitor),
                new VolumeAdjustor(this),
                speechRecognizer,
                new GestureReporter(this),
                imageCaptioner),
            proximitySensorListener,
            speechController,
            diagnosticOverlayController,
            userInterface);

    processorHints.setActorState(pipeline.getActorState());
    processorHints.setPipeline(pipeline.getFeedbackReturner());

    voiceCommandProcessor.setPipeline(pipeline.getFeedbackReturner());

    accessibilityEventProcessor.setActorState(pipeline.getActorState());
    accessibilityEventProcessor.setAccessibilityEventIdleListener(pipeline);

    autoScrollInterpreter.setDirectionNavigationActor(directionNavigationActor);

    nodeMenuRuleProcessor =
        new NodeMenuRuleProcessor(
            this, pipeline.getFeedbackReturner(), pipeline.getActorState(), analytics);
    compositor.setNodeMenuProvider(nodeMenuRuleProcessor);

    compositor.setSpeaker(pipeline.getSpeaker());

    // Event-interpreters are chained:
    // scrollEventInterpreter -> manualScrollInterpreter -> accessibilityFocusInterpreter
    ManualScrollInterpreter manualScrollInterpreter =
        new ManualScrollInterpreter(accessibilityFocusInterpreter, pipeline.getActorState());

    ScrollEventInterpreter scrollEventInterpreter =
        new ScrollEventInterpreter(pipeline.getActorState());
    addEventListener(scrollEventInterpreter);
    scrollEventInterpreter.addListener(scrollPositionInterpreter);
    scrollEventInterpreter.setAutoScrollInterpreter(autoScrollInterpreter);
    scrollEventInterpreter.addListener(manualScrollInterpreter);

    TouchExplorationInterpreter touchExplorationInterpreter =
        new TouchExplorationInterpreter(inputModeManager);

    // Register AccessibilityEventListeners
    addEventListener(touchExplorationInterpreter);
    addEventListener(directionNavigationInterpreter);
    addEventListener(new ProcessorMagnification(/* service= */ this));
    addEventListener(pipeline);

    touchExplorationInterpreter.addTouchExplorationActionListener(accessibilityFocusInterpreter);
    screenStateMonitor.addScreenStateChangeListener(accessibilityFocusInterpreter);

    screenStateMonitor.addScreenStateChangeListener(inputFocusInterpreter);

    callStateMonitor = new CallStateMonitor(this);
    voiceActionMonitor = new VoiceActionMonitor(this, callStateMonitor);
    accessibilityEventProcessor.setVoiceActionMonitor(voiceActionMonitor);

    keyEventListeners.add(inputModeManager);

    menuManager =
        new ListMenuManager(
            this,
            pipeline.getFeedbackReturner(),
            pipeline.getActorState(),
            accessibilityFocusMonitor,
            nodeMenuRuleProcessor,
            analytics);
    voiceCommandProcessor.setListMenuManager(menuManager);

    ringerModeAndScreenMonitor =
        new RingerModeAndScreenMonitor(
            menuManager,
            pipeline.getFeedbackReturner(),
            proximitySensorListener,
            callStateMonitor,
            this);
    accessibilityEventProcessor.setRingerModeAndScreenMonitor(ringerModeAndScreenMonitor);

    // Only use speak-pass talkback-preference on android O+.
    if (FeatureSupport.useSpeakPasswordsServicePref()) {
      headphoneStateMonitor = new HeadphoneStateMonitor(this);
      speakPasswordsManager =
          new SpeakPasswordsManager(this, headphoneStateMonitor, globalVariables);
    }

    ProcessorVolumeStream processorVolumeStream =
        new ProcessorVolumeStream(
            pipeline.getFeedbackReturner(),
            accessibilityFocusMonitor,
            pipeline.getActorState(),
            this,
            globalVariables,
            menuManager);
    addEventListener(processorVolumeStream);
    keyEventListeners.add(processorVolumeStream);

    gestureController =
        new GestureController(
            this,
            pipeline.getFeedbackReturner(),
            pipeline.getActorState(),
            menuManager,
            selectorController,
            processorVolumeStream,
            accessibilityFocusMonitor,
            gestureShortcutMapping,
            analytics);

    audioPlaybackMonitor = new AudioPlaybackMonitor(this);

    // Add event processors. These will process incoming AccessibilityEvents
    // in the order they are added.
    eventFilter =
        new EventFilter(
            compositor,
            this,
            textCursorManager,
            directionNavigationActor.state,
            inputModeManager,
            editTextActionHistory,
            audioPlaybackMonitor,
            globalVariables);
    eventFilter.setVoiceActionDelegate(voiceActionMonitor);
    eventFilter.setAccessibilityFocusEventInterpreter(accessibilityFocusInterpreter);
    processorEventQueue = new ProcessorEventQueue(eventFilter);

    addEventListener(processorEventQueue);
    addEventListener(processorPhoneticLetters);

    // Create window event interpreter and announcer.
    processorScreen =
        new ProcessorScreen(this, processorHints, keyComboManager, pipeline.getFeedbackReturner());
    globalVariables.setWindowsDelegate(processorScreen);
    screenStateMonitor.setWindowsDelegate(processorScreen);
    addEventListener(processorScreen);

    // Monitor window transition status by registering listeners.
    if (processorScreen != null && processorScreen.getWindowEventInterpreter() != null) {
      processorScreen.getWindowEventInterpreter().addListener(menuManager);
      processorScreen.getWindowEventInterpreter().addListener(screenStateMonitor);
    }

    processorCursorState =
        new ProcessorCursorState(this, pipeline.getFeedbackReturner(), globalVariables);
    processorPermissionsDialogs =
        new ProcessorPermissionDialogs(
            this, pipeline.getActorState(), pipeline.getFeedbackReturner());

    volumeMonitor = new VolumeMonitor(pipeline.getFeedbackReturner(), this, callStateMonitor);
    batteryMonitor = new BatteryMonitor(this, pipeline.getFeedbackReturner(), callStateMonitor);

    // TODO: Move this into the custom label manager code
    packageReceiver = new PackageRemovalReceiver();

    addEventListener(new ProcessorGestureVibrator(pipeline.getFeedbackReturner()));

    // Search mode should receive key combos immediately after the TalkBackService.
    SearchScreenOverlay searchScreenOverlay =
        new SearchScreenOverlay(this, focusFinder, labelManager, pipeline.getFeedbackReturner());
    universalSearchManager =
        universalSearchManager =
            new UniversalSearchManager(
                this,
                searchScreenOverlay,
                pipeline.getFeedbackReturner(),
                ringerModeAndScreenMonitor,
                processorScreen.getWindowEventInterpreter());
    keyComboManager.addListener(universalSearchManager);
    autoScrollInterpreter.setSearchManager(universalSearchManager);

    keyComboManager.addListener(directionNavigationInterpreter);
    keyEventListeners.add(keyComboManager);
    serviceStateListeners.add(keyComboManager);

    orientationMonitor = new OrientationMonitor(compositor, this);
    orientationMonitor.addOnOrientationChangedListener(dimScreenController);

    KeyboardLockMonitor keyboardLockMonitor = new KeyboardLockMonitor(compositor);
    keyEventListeners.add(keyboardLockMonitor);

    if (Build.VERSION.SDK_INT >= TelevisionNavigationController.MIN_API_LEVEL
        && FeatureSupport.isTv(this)) {
      televisionNavigationController =
          new TelevisionNavigationController(
              this, accessibilityFocusMonitor, pipeline.getFeedbackReturner());
      keyEventListeners.add(televisionNavigationController);
      televisionDPadManager = new TelevisionDPadManager(televisionNavigationController, this);
      addEventListener(televisionDPadManager);
    }

    BrailleIme.initialize(this, talkBackForBrailleIme);
    analytics.onTalkBackServiceStarted();

    brailleDisplay = new BrailleDisplay(this, talkBackForBrailleDisplay);
  }

  private final TalkBackForBrailleDisplay talkBackForBrailleDisplay =
      new TalkBackForBrailleDisplay() {
        @Override
        public boolean performAction(ScreenReaderAction action) {
          // TODO: implement the screen reader actions.
          return BrailleDisplayHelper.performAction(pipeline.getFeedbackReturner(), action);
        }

        @Override
        public AccessibilityNodeInfoCompat getAccessibilityFocusNode(boolean fallbackOnRoot) {
          return FocusFinder.getAccessibilityFocusNode(getInstance(), fallbackOnRoot);
        }

        @Override
        public FocusFinder createFocusFinder() {
          return new FocusFinder(getInstance());
        }

        @Override
        public boolean showLabelDialog(CustomLabelAction action, AccessibilityNodeInfoCompat node) {
          if (action == CustomLabelAction.ADD_LABEL) {
            return LabelDialogManager.addLabel(
                getInstance(),
                node.getViewIdResourceName(),
                /* needToRestoreFocus= */ true,
                pipeline.getFeedbackReturner());
          } else if (action == CustomLabelAction.EDIT_LABEL) {
            return LabelDialogManager.editLabel(
                getInstance(),
                labelManager.getLabelForViewIdFromCache(node.getViewIdResourceName()).getId(),
                /* needToRestoreFocus= */ true,
                pipeline.getFeedbackReturner());
          }
          return false;
        }

        @Override
        public CharSequence getNodeText(AccessibilityNodeInfoCompat node) {
          return CustomLabelManager.getNodeText(node, labelManager);
        }

        @Override
        public boolean needsLabel(AccessibilityNodeInfoCompat node) {
          return labelManager.needsLabel(node);
        }
      };

  private final TalkBackForBrailleIme talkBackForBrailleIme =
      new TalkBackForBrailleIme() {
        @Override
        public void onBrailleImeActivated(
            BrailleImeForTalkBack brailleImeForTalkBack,
            boolean usePassThrough,
            Region passThroughRegion) {
          isBrailleKeyboardActivated = true;
          TalkBackService.this.brailleImeForTalkBack = brailleImeForTalkBack;
          if (usePassThrough) {
            pipeline
                .getFeedbackReturner()
                .returnFeedback(
                    Performance.EVENT_ID_UNTRACKED,
                    Feedback.passThroughMode(LOCK_PASS_THROUGH, passThroughRegion));
          } else {
            // Keep EBT always disabled when braille keyboard activated.
            requestTouchExploration(false);
          }
        }

        @Override
        public void onBrailleImeInactivated(boolean usePassThrough) {
          if (getServiceStatus() != ServiceStatus.ON) {
            return;
          }
          isBrailleKeyboardActivated = false;
          TalkBackService.this.brailleImeForTalkBack = null;
          if (usePassThrough) {
            pipeline
                .getFeedbackReturner()
                .returnFeedback(
                    Performance.EVENT_ID_UNTRACKED,
                    Feedback.passThroughMode(LOCK_PASS_THROUGH, null));
          } else {
            boolean ebtEnabled =
                getBooleanPref(
                    R.string.pref_explore_by_touch_key, R.bool.pref_explore_by_touch_default);
            if (ebtEnabled) {
              requestTouchExploration(true);
            }
          }
        }

        @Override
        public WindowManager getWindowManager() {
          return (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        }

        @Override
        public ServiceStatus getServiceStatus() {
          if (TalkBackService.getServiceState() == ServiceStateListener.SERVICE_STATE_SUSPENDED
              && prefs.getBoolean(getString(R.string.pref_suspended), false)) {
            return ServiceStatus.SUSPEND;
          }
          return isServiceActive() ? ServiceStatus.ON : ServiceStatus.OFF;
        }

        @Override
        public void speak(CharSequence textToSpeak, int delayMs, SpeakOptions speakOptions) {
          // TODO: For uses cases where the timer is meant to re-schedule text, we
          // should create a centralized repeat-feedback feature, and have BrailleIme use that.
          pipeline
              .getFeedbackReturner()
              .returnFeedback(
                  Performance.EVENT_ID_UNTRACKED,
                  Feedback.speech(textToSpeak, speakOptions).setDelayMs(delayMs));
        }

        @Override
        public void interruptSpeak() {
          interruptAllFeedback(false);
        }

        @Override
        public void playSound(int resId, int delayMs) {
          pipeline
              .getFeedbackReturner()
              .returnFeedback(
                  Performance.EVENT_ID_UNTRACKED, Feedback.sound(resId).setDelayMs(delayMs));
        }

        @Override
        public void disableSilenceOnProximity() {
          proximitySensorListener.setSilenceOnProximity(false);
        }

        @Override
        public void restoreSilenceOnProximity() {
          reloadSilenceOnProximity();
        }

        @Override
        public boolean isContextMenuExist() {
          return menuManager.isMenuExist();
        }

        @Override
        public boolean isVibrationFeedbackEnabled() {
          return FeatureSupport.isVibratorSupported(getApplicationContext())
              && getBooleanPref(R.string.pref_vibration_key, R.bool.pref_vibration_default);
        }

        @Override
        public boolean shouldAnnounceCharacter() {
          @KeyboardEchoType int echoType = readOnScreenKeyboardEcho();
          return echoType == PREF_ECHO_CHARACTERS || echoType == PREF_ECHO_CHARACTERS_AND_WORDS;
        }
      };

  public @Compositor.Flavor int getCompositorFlavor() {
    if (FeatureSupport.isArc()) {
      return Compositor.FLAVOR_ARC;
    } else if (FeatureSupport.isTv(this)) {
      return Compositor.FLAVOR_TV;
    } else {
      return Compositor.FLAVOR_NONE;
    }
  }

  public UniversalSearchManager getUniversalSearchManager() {
    return universalSearchManager;
  }

  @VisibleForTesting
  public SpeechLanguage getSpeechLanguage() {
    return speechLanguage;
  }

  // Gets the user preferred locale changed using language switcher.
  public Locale getUserPreferredLocale() {
    return compositor.getUserPreferredLanguage();
  }

  // Sets the user preferred locale changed using language switcher.
  private void setUserPreferredLocale(Locale locale) {
    compositor.setUserPreferredLanguage(locale);
  }

  public ListMenuManager getMenuManager() {
    return menuManager;
  }

  /**
   * Registers listeners, sets service info, loads preferences. This should be called from {@link
   * #onServiceConnected} and when TalkBack resumes from a suspended state.
   */
  private void resumeInfrastructure() {

    // Load log-level preference early, so that we can log it and use it during startup.
    reloadPreferenceLogLevel();
    // Log meta-data about service, disregarding log-level pref, in 1 line for easy log filtering.
    if (Log.isLoggable(TAG, Log.INFO)) {
      Log.i(
          TAG,
          "resumeInfrastructure() android Build.VERSION.SDK_INT="
              + Build.VERSION.SDK_INT
              + " talkback getVersionName="
              + PackageManagerUtils.getVersionName(this)
              + " LogUtils.getLogLevel="
              + LogUtils.getLogLevel()
              + " utils.BuildConfig.DEBUG="
              + com.google.android.accessibility.utils.BuildConfig.DEBUG);
    }

    if (isServiceActive()) {
      LogUtils.e(TAG, "Attempted to resume while not suspended");
      return;
    }

    setServiceState(ServiceStateListener.SERVICE_STATE_ACTIVE);
    stopForeground(true);

    AccessibilityServiceInfo info = getServiceInfo();
    if (info == null) {
      info = new AccessibilityServiceInfo();
    }
    info.eventTypes = AccessibilityEvent.TYPES_ALL_MASK;
    info.feedbackType |= AccessibilityServiceInfo.FEEDBACK_SPOKEN;
    info.feedbackType |= AccessibilityServiceInfo.FEEDBACK_AUDIBLE;
    info.feedbackType |= AccessibilityServiceInfo.FEEDBACK_HAPTIC;
    info.flags |= AccessibilityServiceInfo.DEFAULT;
    info.flags |= AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS;
    info.flags |= AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS;
    info.flags |= AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
    if (BuildVersionUtils.isAtLeastO()) {
      info.flags |= AccessibilityServiceInfo.FLAG_ENABLE_ACCESSIBILITY_VOLUME;
    }
    info.flags |= ExperimentalUtils.getAddtionalTalkBackServiceFlags();
    if (FeatureSupport.isMultiFingerGestureSupported()) {
      info.flags |=
          AccessibilityServiceInfo.FLAG_REQUEST_MULTI_FINGER_GESTURES
              | FLAG_REQUEST_2_FINGER_PASSTHROUGH;
      resetTouchExplorePassThrough();
    } else {
      info.flags &=
          ~(AccessibilityServiceInfo.FLAG_REQUEST_MULTI_FINGER_GESTURES
              | FLAG_REQUEST_2_FINGER_PASSTHROUGH);
    }
    if (GestureReporter.ENABLED) {
      info.flags |= FeatureSupport.FLAG_SEND_MOTION_EVENTS;
    }
    info.notificationTimeout = 0;
    if (BuildVersionUtils.isAtLeastQ()) {
      info.setInteractiveUiTimeoutMillis(DEFAULT_INTERACTIVE_UI_TIMEOUT_MILLIS);
    }

    // Ensure the initial touch exploration request mode is correct.
    if (supportsTouchScreen
        && getBooleanPref(
            R.string.pref_explore_by_touch_key, R.bool.pref_explore_by_touch_default)) {
      info.flags |= AccessibilityServiceInfo.FLAG_REQUEST_TOUCH_EXPLORATION_MODE;
    }

    LogUtils.v(TAG, "Accessibility Service flag set: 0x%X", info.flags);
    setServiceInfo(info);

    if (callStateMonitor != null) {
      if (!isFirstTimeUser()) {
        callStateMonitor.requestPhonePermissionIfNeeded(prefs);
      }
      callStateMonitor.startMonitoring();
    }

    if (voiceActionMonitor != null) {
      voiceActionMonitor.onResumeInfrastructure();
    }

    if (audioPlaybackMonitor != null) {
      audioPlaybackMonitor.onResumeInfrastructure();
    }

    if (ringerModeAndScreenMonitor != null) {
      registerReceiver(ringerModeAndScreenMonitor, ringerModeAndScreenMonitor.getFilter());
      // It could now be confused with the current screen state
      ringerModeAndScreenMonitor.updateScreenState();
    }

    if (headphoneStateMonitor != null) {
      headphoneStateMonitor.startMonitoring();
    }

    if (volumeMonitor != null) {
      registerReceiver(volumeMonitor, volumeMonitor.getFilter());
      if (FeatureSupport.hasAccessibilityAudioStream(this)) {
        // Cache the initial volume in case that the volume is never changed during runtime.
        volumeMonitor.cacheAccessibilityStreamVolume();
      }
    }

    if (batteryMonitor != null) {
      registerReceiver(batteryMonitor, batteryMonitor.getFilter());
    }

    if (packageReceiver != null) {
      registerReceiver(packageReceiver, packageReceiver.getFilter());
      if (labelManager != null) {
        labelManager.ensureDataConsistency();
      }
    }

    prefs.registerOnSharedPreferenceChangeListener(sharedPreferenceChangeListener);
    prefs.registerOnSharedPreferenceChangeListener(analytics);

    // Add the broadcast listener for gestures.
    final IntentFilter filter = new IntentFilter();
    filter.addAction(ACTION_PERFORM_GESTURE_ACTION);
    registerReceiver(activeReceiver, filter, PERMISSION_TALKBACK, null);

    // Add the broadcast listener for training pages.
    final IntentFilter trainingPageFilter = new IntentFilter(ACTION_TRAINING_PAGE_SWITCHED);
    registerReceiver(trainingPageReceiver, trainingPageFilter, PERMISSION_TALKBACK, null);

    if (televisionDPadManager != null) {
      registerReceiver(televisionDPadManager, TelevisionDPadManager.getFilter());
    }

    if (FeatureSupport.supportMagnificationController()) {
      MagnificationController magnificationController = getMagnificationController();
      if (magnificationController != null && onMagnificationChangedListener != null) {
        magnificationController.addListener(onMagnificationChangedListener);
      }
    }

    if ((fingerprintGestureCallback != null) && (getFingerprintGestureController() != null)) {
      getFingerprintGestureController()
          .registerFingerprintGestureCallback(fingerprintGestureCallback, null);
    }

    reloadPreferences();

    dimScreenController.resume();

    inputFocusInterpreter.initLastEditableFocusForGlobalVariables();

    if (brailleImeForTalkBack != null) {
      brailleImeForTalkBack.onTalkBackResumed();
    }
    brailleDisplay.start();
  }

  @Override
  public void unregisterReceiver(BroadcastReceiver receiver) {
    try {
      if (receiver != null) {
        super.unregisterReceiver(receiver);
      }
    } catch (IllegalArgumentException e) {
      LogUtils.e(
          TAG,
          "Do not unregister receiver as it was never registered: "
              + receiver.getClass().getSimpleName());
    }
  }

  private void unregisterReceivers(BroadcastReceiver... receivers) {
    if (receivers == null) {
      return;
    }
    for (BroadcastReceiver receiver : receivers) {
      unregisterReceiver(receiver);
    }
  }

  /**
   * Registers listeners, sets service info, loads preferences. This should be called from {@link
   * #onServiceConnected} and when TalkBack resumes from a suspended state.
   */
  private void suspendInfrastructure() {
    if (!isServiceActive()) {
      LogUtils.e(TAG, "Attempted to suspend while already suspended");
      return;
    }

    if (callStateMonitor != null) {
      callStateMonitor.stopMonitoring();
    }

    if (voiceActionMonitor != null) {
      voiceActionMonitor.onSuspendInfrastructure();
    }

    if (audioPlaybackMonitor != null) {
      audioPlaybackMonitor.onSuspendInfrastructure();
    }

    dimScreenController.suspend();

    interruptAllFeedback(false /* stopTtsSpeechCompletely */);
    setServiceState(ServiceStateListener.SERVICE_STATE_SUSPENDED);

    // Some apps depend on these being set to false when TalkBack is disabled.
    if (supportsTouchScreen) {
      requestTouchExploration(false);
    }

    prefs.unregisterOnSharedPreferenceChangeListener(sharedPreferenceChangeListener);
    prefs.unregisterOnSharedPreferenceChangeListener(analytics);

    unregisterReceivers(
        activeReceiver,
        ringerModeAndScreenMonitor,
        batteryMonitor,
        packageReceiver,
        volumeMonitor,
        televisionDPadManager,
        trainingPageReceiver);

    if (volumeMonitor != null) {
      volumeMonitor.releaseControl();
    }

    if (headphoneStateMonitor != null) {
      headphoneStateMonitor.stopMonitoring();
    }

    // Remove any pending notifications that shouldn't persist.
    final NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
    nm.cancelAll();

    if (FeatureSupport.supportMagnificationController()) {
      MagnificationController magnificationController = getMagnificationController();
      if (magnificationController != null && onMagnificationChangedListener != null) {
        magnificationController.removeListener(onMagnificationChangedListener);
      }
    }

    if ((fingerprintGestureCallback != null) && (getFingerprintGestureController() != null)) {
      getFingerprintGestureController()
          .unregisterFingerprintGestureCallback(fingerprintGestureCallback);
    }

    if (FeatureSupport.isFingerprintSupported(this)) {
      requestServiceFlag(AccessibilityServiceInfo.FLAG_REQUEST_FINGERPRINT_GESTURES, false);
    }

    if (brailleImeForTalkBack != null) {
      brailleImeForTalkBack.onTalkBackSuspended();
    }
    brailleDisplay.stop();
  }

  /** Shuts down the infrastructure in case it has been initialized. */
  private void shutdownInfrastructure() {
    // we put it first to be sure that screen dimming would be removed even if code bellow
    // will crash by any reason. Because leaving user with dimmed screen is super bad
    dimScreenController.shutdown();

    if (fullScreenReadActor != null) {
      fullScreenReadActor.shutdown();
    }

    if (labelManager != null) {
      labelManager.shutdown();
    }

    proximitySensorListener.shutdown();
    feedbackController.shutdown();
    pipeline.shutdown();
    analytics.onTalkBackServiceStopped();
  }

  /**
   * Adds an event listener.
   *
   * @param listener The listener to add.
   */
  public void addEventListener(AccessibilityEventListener listener) {
    accessibilityEventProcessor.addAccessibilityEventListener(listener);
  }

  /**
   * Posts a {@link Runnable} to removes an event listener. This is safe to call from inside {@link
   * AccessibilityEventListener#onAccessibilityEvent(AccessibilityEvent, EventId)}.
   *
   * @param listener The listener to remove.
   */
  public void postRemoveEventListener(final AccessibilityEventListener listener) {
    accessibilityEventProcessor.postRemoveAccessibilityEventListener(listener);
  }

  /** Returns a boolean preference by resource id. */
  private boolean getBooleanPref(int prefKeyResId, int prefDefaultResId) {
    return SharedPreferencesUtils.getBooleanPref(
        prefs, getResources(), prefKeyResId, prefDefaultResId);
  }

  /** Reloads service preferences. */
  private void reloadPreferences() {
    final Resources res = getResources();

    // Preferece to reduce window announcement delay.
    boolean reduceDelayPref =
        getBooleanPref(
            R.string.pref_reduce_window_delay_key, R.bool.pref_reduce_window_delay_default);
    if (processorScreen != null && processorScreen.getWindowEventInterpreter() != null) {
      processorScreen.getWindowEventInterpreter().setReduceDelayPref(reduceDelayPref);
    }

    // If performance statistics changing enabled setting... clear collected stats.
    boolean performanceEnabled =
        getBooleanPref(R.string.pref_performance_stats_key, R.bool.pref_performance_stats_default);
    Performance performance = Performance.getInstance();
    if (performance.getEnabled() != performanceEnabled) {
      performance.clearRecentEvents();
      performance.clearAllStats();
      performance.setEnabled(performanceEnabled);
    }

    boolean logOverlayEnabled =
        getBooleanPref(R.string.pref_log_overlay_key, R.bool.pref_log_overlay_default);
    diagnosticOverlayController.setLogOverlayEnabled(logOverlayEnabled);

    accessibilityEventProcessor.setSpeakWhenScreenOff(
        VerbosityPreferences.getPreferenceValueBool(
            prefs,
            res,
            res.getString(R.string.pref_screenoff_key),
            res.getBoolean(R.bool.pref_screenoff_default)));

    accessibilityEventProcessor.setDumpEventMask(
        prefs.getInt(res.getString(R.string.pref_dump_event_mask_key), 0));

    automaticResume =
        prefs.getString(
            res.getString(R.string.pref_resume_talkback_key), getString(R.string.resume_screen_on));

    reloadSilenceOnProximity();
    reloadPreferenceLogLevel();

    final boolean useSingleTap =
        getBooleanPref(R.string.pref_single_tap_key, R.bool.pref_single_tap_default);
    globalVariables.setUseSingleTap(useSingleTap);
    accessibilityFocusInterpreter.setSingleTapEnabled(useSingleTap);
    accessibilityFocusInterpreter.setTypingMethod(
        SharedPreferencesUtils.getIntFromStringPref(
            prefs,
            res,
            R.string.pref_typing_confirmation_key,
            R.string.pref_typing_confirmation_default));

    if (supportsTouchScreen) {
      // Touch exploration *must* be enabled on TVs for TalkBack to function.
      final boolean touchExploration =
          FeatureSupport.isTv(this)
              || getBooleanPref(
                  R.string.pref_explore_by_touch_key, R.bool.pref_explore_by_touch_default);
      requestTouchExploration(touchExploration);
    }

    if (FeatureSupport.isMultiFingerGestureSupported()) {
      requestServiceFlag(
          AccessibilityServiceInfo.FLAG_REQUEST_MULTI_FINGER_GESTURES
              | FLAG_REQUEST_2_FINGER_PASSTHROUGH,
          /* requestedState= */ true);
      resetTouchExplorePassThrough();
    }

    processorCursorState.onReloadPreferences(this);
    processorPermissionsDialogs.onReloadPreferences(this);

    voiceCommandProcessor.setEchoRecognizedTextEnabled(
        getBooleanPref(
            R.string.pref_echo_recognized_text_speech_key,
            R.bool.pref_echo_recognized_text_default));

    // Reload speech preferences.
    pipeline.setOverlayEnabled(
        getBooleanPref(R.string.pref_tts_overlay_key, R.bool.pref_tts_overlay_default));
    pipeline.setUseIntonation(
        VerbosityPreferences.getPreferenceValueBool(
            prefs,
            res,
            res.getString(R.string.pref_intonation_key),
            res.getBoolean(R.bool.pref_intonation_default)));
    pipeline.setUsePunctuation(
        getBooleanPref(R.string.pref_punctuation_key, R.bool.pref_punctuation_default));
    @CapitalLetterHandlingMethod
    int capLetterFeedback =
        Integer.parseInt(
            VerbosityPreferences.getPreferenceValueString(
                prefs,
                res,
                res.getString(R.string.pref_capital_letters_key),
                res.getString(R.string.pref_capital_letters_default)));
    speechController.setCapLetterFeedback(capLetterFeedback);
    globalVariables.setGlobalSayCapital(capLetterFeedback == CAPITAL_LETTERS_TYPE_SPEAK_CAP);
    pipeline.setSpeechPitch(
        SharedPreferencesUtils.getFloatFromStringPref(
            prefs, res, R.string.pref_speech_pitch_key, R.string.pref_speech_pitch_default));
    float speechRate =
        SharedPreferencesUtils.getFloatFromStringPref(
            prefs, res, R.string.pref_speech_rate_key, R.string.pref_speech_rate_default);
    pipeline.setSpeechRate(speechRate);
    globalVariables.setSpeechRate(speechRate);
    int onScreenKeyboardPref = readOnScreenKeyboardEcho();
    eventFilter.setOnScreenKeyboardEcho(onScreenKeyboardPref);

    int physicalKeyboardPref =
        Integer.parseInt(
            VerbosityPreferences.getPreferenceValueString(
                prefs,
                res,
                res.getString(R.string.pref_keyboard_echo_physical_key),
                res.getString(R.string.pref_keyboard_echo_default)));
    eventFilter.setPhysicalKeyboardEcho(physicalKeyboardPref);

    boolean useAudioFocus =
        getBooleanPref(R.string.pref_use_audio_focus_key, R.bool.pref_use_audio_focus_default);
    pipeline.setUseAudioFocus(useAudioFocus);
    globalVariables.setUseAudioFocus(useAudioFocus);

    // Speech volume is stored as int [0,100] and scaled to float [0,1].
    if (!FeatureSupport.hasAccessibilityAudioStream(this)) {
      pipeline.setSpeechVolume(
          SharedPreferencesUtils.getIntFromStringPref(
                  prefs, res, R.string.pref_speech_volume_key, R.string.pref_speech_volume_default)
              / 100.0f);
    }

    if (speakPasswordsManager != null) {
      speakPasswordsManager.onPreferencesChanged();
    }

    // Reload feedback preferences.
    int adjustment =
        SharedPreferencesUtils.getIntFromStringPref(
            prefs, res, R.string.pref_soundback_volume_key, R.string.pref_soundback_volume_default);
    feedbackController.setVolumeAdjustment(adjustment / 100.0f);

    boolean hapticEnabled =
        FeatureSupport.isVibratorSupported(getApplicationContext())
            && getBooleanPref(R.string.pref_vibration_key, R.bool.pref_vibration_default);
    feedbackController.setHapticEnabled(hapticEnabled);

    boolean auditoryEnabled =
        getBooleanPref(R.string.pref_soundback_key, R.bool.pref_soundback_default);
    feedbackController.setAuditoryEnabled(auditoryEnabled);

    if (scrollPositionInterpreter != null) {
      scrollPositionInterpreter.setVerboseAnnouncement(
          VerbosityPreferences.getPreferenceValueBool(
              prefs,
              res,
              res.getString(R.string.pref_verbose_scroll_announcement_key),
              res.getBoolean(R.bool.pref_verbose_scroll_announcement_default)));
    }

    boolean isFingerprintGestureAssigned =
        FeatureSupport.isFingerprintSupported(this)
            && (gestureController.isFingerprintGestureAssigned(
                    FingerprintGestureController.FINGERPRINT_GESTURE_SWIPE_UP)
                || gestureController.isFingerprintGestureAssigned(
                    FingerprintGestureController.FINGERPRINT_GESTURE_SWIPE_DOWN)
                || gestureController.isFingerprintGestureAssigned(
                    FingerprintGestureController.FINGERPRINT_GESTURE_SWIPE_LEFT)
                || gestureController.isFingerprintGestureAssigned(
                    FingerprintGestureController.FINGERPRINT_GESTURE_SWIPE_RIGHT));
    requestServiceFlag(
        AccessibilityServiceInfo.FLAG_REQUEST_FINGERPRINT_GESTURES, isFingerprintGestureAssigned);

    // Update compositor preferences.
    if (compositor != null) {
      // Update preference: speak collection info.
      boolean speakCollectionInfo =
          VerbosityPreferences.getPreferenceValueBool(
              prefs,
              res,
              res.getString(R.string.pref_speak_container_element_positions_key),
              res.getBoolean(R.bool.pref_speak_container_element_positions_default));
      compositor.setSpeakCollectionInfo(speakCollectionInfo);

      // Update preference: speak roles.
      boolean speakRoles =
          VerbosityPreferences.getPreferenceValueBool(
              prefs,
              res,
              res.getString(R.string.pref_speak_roles_key),
              res.getBoolean(R.bool.pref_speak_roles_default));
      compositor.setSpeakRoles(speakRoles);

      // Update preference: description order.
      String descriptionOrder =
          SharedPreferencesUtils.getStringPref(
              prefs, res, R.string.pref_node_desc_order_key, R.string.pref_node_desc_order_default);
      compositor.setDescriptionOrder(prefValueToDescriptionOrder(res, descriptionOrder));

      // Update preference: speak element IDs.
      boolean speakElementIds =
          getBooleanPref(
              R.string.pref_speak_element_ids_key, R.bool.pref_speak_element_ids_default);
      compositor.setSpeakElementIds(speakElementIds);

      // Update preference: speak usage hints.
      boolean speakUsageHints =
          VerbosityPreferences.getPreferenceValueBool(
              prefs,
              res,
              res.getString(R.string.pref_a11y_hints_key),
              res.getBoolean(R.bool.pref_a11y_hints_default));
      globalVariables.setUsageHintEnabled(speakUsageHints);

      // Reload compositor configuration.
      compositor.refreshParseTreeIfNeeded();
    }

    FocusIndicatorUtils.applyFocusAppearancePreference(this, prefs, res);
  }

  private int readOnScreenKeyboardEcho() {
    return Integer.parseInt(
        VerbosityPreferences.getPreferenceValueString(
            prefs,
            getResources(),
            getResources().getString(R.string.pref_keyboard_echo_on_screen_key),
            getResources().getString(R.string.pref_keyboard_echo_default)));
  }

  private void reloadPreferenceLogLevel() {
    LogUtils.setLogLevel(
        SharedPreferencesUtils.getIntFromStringPref(
            prefs, getResources(), R.string.pref_log_level_key, R.string.pref_log_level_default));
  }

  private void reloadSilenceOnProximity() {
    final boolean silenceOnProximity =
        getBooleanPref(R.string.pref_proximity_key, R.bool.pref_proximity_default);
    proximitySensorListener.setSilenceOnProximity(silenceOnProximity);
  }

  private static @Compositor.DescriptionOrder int prefValueToDescriptionOrder(
      Resources resources, String value) {
    if (TextUtils.equals(
        value, resources.getString(R.string.pref_node_desc_order_value_role_name_state_pos))) {
      return Compositor.DESC_ORDER_ROLE_NAME_STATE_POSITION;
    } else if (TextUtils.equals(
        value, resources.getString(R.string.pref_node_desc_order_value_state_name_role_pos))) {
      return Compositor.DESC_ORDER_STATE_NAME_ROLE_POSITION;
    } else if (TextUtils.equals(
        value, resources.getString(R.string.pref_node_desc_order_value_name_role_state_pos))) {
      return Compositor.DESC_ORDER_NAME_ROLE_STATE_POSITION;
    } else {
      LogUtils.e(TAG, "Unhandled description order preference value \"%s\"", value);
      return Compositor.DESC_ORDER_STATE_NAME_ROLE_POSITION;
    }
  }

  /**
   * Attempts to return the state of touch exploration.
   *
   * <p>Should only be called if {@link #supportsTouchScreen} is true.
   *
   * @return {@code true} if touch exploration is enabled, {@code false} if touch exploration is
   *     disabled or {@code null} if we couldn't get the state of touch exploration.
   */
  private @Nullable Boolean isTouchExplorationEnabled() {
    final AccessibilityServiceInfo info = getServiceInfo();
    if (info == null) {
      LogUtils.e(TAG, "Failed to read touch exploration request state, service info was null");
      return null;
    }

    return ((info.flags & AccessibilityServiceInfo.FLAG_REQUEST_TOUCH_EXPLORATION_MODE) != 0);
  }

  /**
   * Attempts to change the state of touch exploration.
   *
   * <p>Should only be called if {@link #supportsTouchScreen} is true.
   *
   * @param requestedState {@code true} to request exploration.
   * @return {@code true} if touch exploration is now enabled, {@code false} if touch exploration is
   *     now disabled or {@code null} if we couldn't get the state of touch exploration which means
   *     no change to touch exploration state occurred.
   */
  private @Nullable Boolean requestTouchExploration(boolean requestedState) {
    requestServiceFlag(
        AccessibilityServiceInfo.FLAG_REQUEST_TOUCH_EXPLORATION_MODE, requestedState);
    return isTouchExplorationEnabled();
  }

  /**
   * Attempts to change the service info flag.
   *
   * @param flag to specify the service flag to change.
   * @param requestedState {@code true} to request service flag change.
   */
  private void requestServiceFlag(int flag, boolean requestedState) {
    final AccessibilityServiceInfo info = getServiceInfo();
    if (info == null) {
      return;
    }

    boolean currentState = (info.flags & flag) == flag;
    if (currentState == requestedState) {
      return;
    }

    if (requestedState) {
      info.flags |= flag;
    } else {
      info.flags &= ~flag;
    }

    LogUtils.v(TAG, "Accessibility Service flag changed: 0x%X", info.flags);
    setServiceInfo(info);
  }

  /**
   * Launches the touch exploration tutorial if necessary.
   *
   * @return {@code true} if the tutorial is launched successfully.
   */
  public boolean showTutorialIfNecessary() {
    if (FeatureSupport.isArc() || FeatureSupport.isTv(getApplicationContext())) {
      return false;
    }

    boolean isDeviceProvisioned =
        Settings.Secure.getInt(getContentResolver(), Settings.Global.DEVICE_PROVISIONED, 1) != 0;

    if (isDeviceProvisioned && !isFirstTimeUser()) {
      return false;
    }

    final int touchscreenState = getResources().getConfiguration().touchscreen;

    if (touchscreenState != Configuration.TOUCHSCREEN_NOTOUCH && supportsTouchScreen) {
      startActivity(TutorialInitiator.createFirstRunTutorialIntent(getApplicationContext()));
      prefs.edit().putBoolean(PREF_FIRST_TIME_USER, false).apply();
      return true;
    }

    return false;
  }

  private boolean isFirstTimeUser() {
    return prefs.getBoolean(PREF_FIRST_TIME_USER, true);
  }

  private final KeyComboManager.KeyComboListener keyComboListener =
      new KeyComboManager.KeyComboListener() {
        // TODO: Move event-mapping's feedback logics to Mappers
        @Override
        public boolean onComboPerformed(int id, String name, EventId eventId) {
          switch (id) {
            case KeyComboManager.ACTION_SUSPEND_OR_RESUME:
              if (serviceState == ServiceStateListener.SERVICE_STATE_SUSPENDED) {
                resumeTalkBack(eventId);
              } else if (serviceState == ServiceStateListener.SERVICE_STATE_ACTIVE) {
                requestSuspendTalkBack(eventId);
              }
              return true;
            case KeyComboManager.ACTION_BACK:
              pipeline
                  .getFeedbackReturner()
                  .returnFeedback(eventId, Feedback.systemAction(GLOBAL_ACTION_BACK));
              return true;
            case KeyComboManager.ACTION_HOME:
              pipeline
                  .getFeedbackReturner()
                  .returnFeedback(eventId, Feedback.systemAction(GLOBAL_ACTION_HOME));
              return true;
            case KeyComboManager.ACTION_NOTIFICATION:
              pipeline
                  .getFeedbackReturner()
                  .returnFeedback(eventId, Feedback.systemAction(GLOBAL_ACTION_NOTIFICATIONS));
              return true;
            case KeyComboManager.ACTION_RECENTS:
              pipeline
                  .getFeedbackReturner()
                  .returnFeedback(eventId, Feedback.systemAction(GLOBAL_ACTION_RECENTS));
              return true;
            case KeyComboManager.ACTION_PLAY_PAUSE_MEDIA:
              pipeline
                  .getFeedbackReturner()
                  .returnFeedback(
                      eventId,
                      Feedback.systemAction(
                          SystemActionPerformer.GLOBAL_ACTION_KEYCODE_HEADSETHOOK));
              return true;
            case KeyComboManager.ACTION_SCROLL_FORWARD_READING_MENU:
              selectorController.selectPreviousOrNextSetting(eventId, true);
              return true;
            case KeyComboManager.ACTION_SCROLL_BACKWARD_READING_MENU:
              selectorController.selectPreviousOrNextSetting(eventId, false);
              return true;
            case KeyComboManager.ACTION_ADJUST_READING_SETTING_PREVIOUS:
              selectorController.adjustSelectedSetting(eventId, false);
              return true;
            case KeyComboManager.ACTION_ADJUST_READING_SETTING_NEXT:
              selectorController.adjustSelectedSetting(eventId, true);
              return true;
            case KeyComboManager.ACTION_GRANULARITY_INCREASE:
              pipeline
                  .getFeedbackReturner()
                  .returnFeedback(eventId, Feedback.focusDirection(NEXT_GRANULARITY));
              return true;
            case KeyComboManager.ACTION_GRANULARITY_DECREASE:
              pipeline
                  .getFeedbackReturner()
                  .returnFeedback(eventId, Feedback.focusDirection(PREVIOUS_GRANULARITY));
              return true;
            case KeyComboManager.ACTION_READ_FROM_TOP:
              pipeline.execute(
                  Feedback.create(eventId, Feedback.continuousRead(START_AT_TOP).build()));
              return true;
            case KeyComboManager.ACTION_READ_FROM_NEXT_ITEM:
              pipeline.execute(
                  Feedback.create(eventId, Feedback.continuousRead(START_AT_NEXT).build()));
              return true;
            case KeyComboManager.ACTION_TALKBACK_CONTEXT_MENU:
              showTalkbackContextMenu(eventId);
              return true;
            case KeyComboManager.ACTION_CUSTOM_ACTIONS:
              showCustomActions(eventId);
              return true;
            case KeyComboManager.ACTION_LANGUAGE_OPTIONS:
              showLanguageOptions(eventId);
              return true;
            case KeyComboManager.ACTION_OPEN_MANAGE_KEYBOARD_SHORTCUTS:
              openManageKeyboardShortcuts();
              return true;
            case KeyComboManager.ACTION_OPEN_TALKBACK_SETTINGS:
              if (SettingsUtils.allowLinksOutOfSettings(getApplicationContext())) {
                openTalkBackSettings();
              }
              return true;
            default: // fall out
          }

          return false;
        }
      };

  private final KeyComboManager.KeyUpListener keyUpListener =
      new KeyComboManager.KeyUpListener() {
        @Override
        public void onKeyUpShouldInterrupt(int performedActionId) {
          if (performedActionId == ACTION_NAVIGATE_NEXT_DEFAULT /* next in default keymap */
              || performedActionId
                  == ACTION_NAVIGATE_PREVIOUS_DEFAULT /* previous in default keymap */
              || performedActionId == ACTION_NAVIGATE_NEXT /* next in classic keymap */
              || performedActionId == ACTION_NAVIGATE_PREVIOUS /* previous in classic keymap */) {
            return;
          }
          if (fullScreenReadActor.isActive()) {
            fullScreenReadActor.interrupt();
          }
        }
      };

  /** Reloads preferences whenever their values change. */
  private final OnSharedPreferenceChangeListener sharedPreferenceChangeListener =
      new OnSharedPreferenceChangeListener() {
        @Override
        public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
          LogUtils.d(TAG, "A shared preference changed: %s", key);
          reloadPreferences();
        }
      };

  /** Broadcast receiver for actions that happen while the service is active. */
  private final BroadcastReceiver activeReceiver =
      new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
          final String action = intent.getAction();

          if (ACTION_PERFORM_GESTURE_ACTION.equals(action)) {
            int gestureId =
                intent.getIntExtra(EXTRA_GESTURE_ACTION, R.string.shortcut_value_unassigned);
            EventId eventId = Performance.getInstance().onGestureEventReceived(gestureId);
            gestureController.onGesture(gestureId, eventId);
            Performance.getInstance().onHandlerDone(eventId);
          }
        }
      };

  /** Broadcast receiver for actions that happen while the service is inactive. */
  private final BroadcastReceiver suspendedReceiver =
      new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
          final String action = intent.getAction();

          EventId eventId = EVENT_ID_UNTRACKED; // Performance not tracked for broadcasts.
          if (ACTION_RESUME_FEEDBACK.equals(action)) {
            resumeTalkBack(eventId);
          } else if (Intent.ACTION_SCREEN_ON.equals(action)) {
            if (automaticResume.equals(getString(R.string.resume_screen_keyguard))) {
              if (ScreenMonitor.isDeviceLocked(instance)) {
                resumeTalkBack(eventId);
              }
            } else if (automaticResume.equals(getString(R.string.resume_screen_on))) {
              resumeTalkBack(eventId);
            }
          }
        }
      };

  /** Broadcast receiver for actions that happen while training page is switched. */
  private final BroadcastReceiver trainingPageReceiver =
      new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
          if (ACTION_TRAINING_PAGE_SWITCHED.equals(intent.getAction())
              && gestureController != null) {
            @Nullable PageId pageId = (PageId) intent.getSerializableExtra(EXTRA_TRAINING_PAGE_ID);
            if (pageId == null) {
              return;
            }

            @Nullable PageConfig pageConfig = PageConfig.getPage(pageId);
            gestureController.setCaptureGestureIdToAnnouncements(
                pageConfig == null
                    ? ImmutableMap.of()
                    : pageConfig.getCaptureGestureIdToAnnouncements());

            // Request phone permission after TalkBack tutorial is finished.
            if (callStateMonitor != null && pageId == PAGE_ID_FINISHED) {
              callStateMonitor.requestPhonePermissionIfNeeded(prefs);
            }
          }
        }
      };

  public void onLockedBootCompleted(EventId eventId) {
    if (serviceState == ServiceStateListener.SERVICE_STATE_INACTIVE) {
      // onServiceConnected has not completed yet. We need to defer the boot completion
      // callback until after onServiceConnected has run.
      lockedBootCompletedPending = true;
    } else {
      // onServiceConnected has already completed, so we should run the callback now.
      onLockedBootCompletedInternal(eventId);
    }
  }

  private void onLockedBootCompletedInternal(EventId eventId) {
    // Update TTS quietly.
    // If the TTS changes here, it is probably a non-FBE TTS that didn't appear in the TTS
    // engine list when TalkBack initialized during system boot, so we want the change to be
    // invisible to the user.
    pipeline.onBoot(/* quiet= */ true);

    if (!isServiceActive()
        && automaticResume != null
        && !automaticResume.equals(getString(R.string.resume_screen_manual))) {
      resumeTalkBack(eventId);
    }
  }

  public void onUnlockedBootCompleted() {
    // Update TTS and allow announcement.
    // If the TTS changes here, it is probably a non-FBE TTS that is available after the user
    // unlocks their phone. In this case, the user heard Google TTS at the lock screen, so we
    // should let them know that we're using their preferred TTS now.
    pipeline.onBoot(/* quiet= */ false);

    if (labelManager != null) {
      labelManager.ensureLabelsLoaded();
    }
  }

  @Override
  public void uncaughtException(Thread thread, Throwable ex) {
    try {
      if (dimScreenController != null) {
        dimScreenController.shutdown();
      }

      if (menuManager != null && menuManager.isMenuShowing()) {
        menuManager.dismissAll();
      }

      if (talkBackSuspendDialog != null) {
        talkBackSuspendDialog.dismissDialog();
      }
    } catch (Exception e) {
      // Do nothing.
    } finally {
      if (systemUeh != null) {
        systemUeh.uncaughtException(thread, ex);
      }
    }
  }

  public void setTestingListener(TalkBackListener testingListener) {
    accessibilityEventProcessor.setTestingListener(testingListener);
  }

  public boolean isScreenOrientationLandscape() {
    Configuration config = getResources().getConfiguration();
    if (config == null) {
      return false;
    }
    return config.orientation == Configuration.ORIENTATION_LANDSCAPE;
  }

  public InputModeManager getInputModeManager() {
    return inputModeManager;
  }

  /** Runnable to run after announcing "TalkBack off". */
  private static final class DisableTalkBackCompleteAction implements UtteranceCompleteRunnable {
    boolean isDone = false;

    @Override
    public void run(int status) {
      synchronized (DisableTalkBackCompleteAction.this) {
        isDone = true;
        DisableTalkBackCompleteAction.this.notifyAll();
      }
    }
  }

  /** Watches the proximity sensor, and silences speech when it's triggered. */
  public class ProximitySensorListener {
    /** Proximity sensor for implementing "shut up" functionality. */
    @Nullable private ProximitySensor proximitySensor;

    private TalkBackService service;

    /** Whether to use the proximity sensor to silence speech. */
    private boolean silenceOnProximity;

    /**
     * Whether or not the screen is on. This is set by RingerModeAndScreenMonitor and used by
     * SpeechControllerImpl to determine if the ProximitySensor should be on or off.
     */
    private boolean screenIsOn;

    public ProximitySensorListener(TalkBackService service) {

      this.service = service;
      service.addServiceStateListener(
          new ServiceStateListener() {
            @Override
            public void onServiceStateChanged(int newState) {
              if (newState == ServiceStateListener.SERVICE_STATE_ACTIVE) {
                setProximitySensorState(true);
              } else if (newState == ServiceStateListener.SERVICE_STATE_SUSPENDED) {
                setProximitySensorState(false);
              }
            }
          });

      screenIsOn = true;
    }

    public void shutdown() {
      setProximitySensorState(false);
    }

    public void setScreenIsOn(boolean screenIsOn) {
      this.screenIsOn = screenIsOn;

      // The proximity sensor should always be on when the screen is on so
      // that the proximity gesture can be used to silence all apps.
      if (this.screenIsOn) {
        setProximitySensorState(true);
      }
    }

    /**
     * Sets whether or not the proximity sensor should be used to silence speech.
     *
     * <p>This should be called when the user changes the state of the "silence on proximity"
     * preference.
     */
    public void setSilenceOnProximity(boolean silenceOnProximity) {
      this.silenceOnProximity = silenceOnProximity;

      // Propagate the proximity sensor change.
      setProximitySensorState(silenceOnProximity);
    }

    /**
     * Enables/disables the proximity sensor. The proximity sensor should be disabled when not in
     * use to save battery.
     *
     * <p>This is a no-op if the user has turned off the "silence on proximity" preference.
     *
     * @param enabled {@code true} if the proximity sensor should be enabled, {@code false}
     *     otherwise.
     */
    // TODO: Rewrite for readability.
    public void setProximitySensorState(boolean enabled) {
      if (proximitySensor != null) {
        // Should we be using the proximity sensor at all?
        if (!silenceOnProximity) {
          proximitySensor.stop();
          proximitySensor = null;
          return;
        }

        if (!service.isInstanceActive()) {
          proximitySensor.stop();
          return;
        }
      } else {
        // Do we need to initialize the proximity sensor?
        if (enabled && silenceOnProximity) {
          proximitySensor = new ProximitySensor(service);
          proximitySensor.setProximityChangeListener(pipeline.getProximityChangeListener());
        } else {
          // Since proximitySensor is null, we can return here.
          return;
        }
      }

      // Manage the proximity sensor state.
      if (enabled) {
        proximitySensor.start();
      } else {
        proximitySensor.stop();
      }
    }

    public void setProximitySensorStateByScreen() {
      setProximitySensorState(screenIsOn);
    }
  }

  private void resetTouchExplorePassThrough() {
    if (FeatureSupport.supportPassthrough()) {
      if (isBrailleKeyboardActivated) {
        return;
      }
      pipeline
          .getFeedbackReturner()
          .returnFeedback(
              Performance.EVENT_ID_UNTRACKED, Feedback.passThroughMode(DISABLE_PASSTHROUGH));
    }
  }
}
