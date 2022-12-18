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

import static com.google.android.accessibility.talkback.Feedback.PassThroughMode.Action.DISABLE_PASSTHROUGH;
import static com.google.android.accessibility.talkback.Feedback.PassThroughMode.Action.LOCK_PASS_THROUGH;
import static com.google.android.accessibility.talkback.focusmanagement.FocusProcessorForTapAndTouchExploration.FORCE_LIFT_TO_TYPE_ON_IME;
import static com.google.android.accessibility.talkback.training.PageConfig.PageId.PAGE_ID_FINISHED;
import static com.google.android.accessibility.utils.Performance.EVENT_ID_UNTRACKED;
import static com.google.android.accessibility.utils.input.TextEventFilter.PREF_ECHO_CHARACTERS;
import static com.google.android.accessibility.utils.input.TextEventFilter.PREF_ECHO_CHARACTERS_AND_WORDS;
import static com.google.android.accessibility.utils.output.SpeechControllerImpl.CAPITAL_LETTERS_TYPE_SPEAK_CAP;

import android.accessibilityservice.AccessibilityGestureEvent;
import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.accessibilityservice.FingerprintGestureController;
import android.accessibilityservice.FingerprintGestureController.FingerprintGestureCallback;
import android.accessibilityservice.TouchInteractionController;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Region;
import android.media.AudioManager;
import android.os.Build;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.view.Display;
import android.view.KeyEvent;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import androidx.annotation.VisibleForTesting;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;

import com.google.android.accessibility.braille.brailledisplay.BrailleDisplay;
import com.google.android.accessibility.braille.interfaces.BrailleDisplayForTalkBack;
import com.google.android.accessibility.braille.interfaces.BrailleImeForBrailleDisplay;
import com.google.android.accessibility.braille.interfaces.BrailleImeForTalkBack;
import com.google.android.accessibility.braille.interfaces.TalkBackForBrailleDisplay;
import com.google.android.accessibility.braille.interfaces.TalkBackForBrailleIme;
import com.google.android.accessibility.brailleime.BrailleIme;
import com.google.android.accessibility.talkback.Feedback.DeviceInfo.Action;
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
import com.google.android.accessibility.talkback.actor.search.UniversalSearchActor;
import com.google.android.accessibility.talkback.actor.search.UniversalSearchManager;
import com.google.android.accessibility.talkback.actor.voicecommands.SpeechRecognizerActor;
import com.google.android.accessibility.talkback.actor.voicecommands.VoiceCommandProcessor;
import com.google.android.accessibility.talkback.adb.AdbReceiver;
import com.google.android.accessibility.talkback.brailledisplay.BrailleDisplayHelper;
import com.google.android.accessibility.talkback.compositor.Compositor;
import com.google.android.accessibility.talkback.compositor.EventFilter;
import com.google.android.accessibility.talkback.compositor.GlobalVariables;
import com.google.android.accessibility.talkback.contextmenu.ListMenuManager;
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
import com.google.android.accessibility.talkback.gesture.GestureHistory;
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
import com.google.android.accessibility.talkback.interpreters.UiChangeEventInterpreter;
import com.google.android.accessibility.talkback.ipc.IpcService;
import com.google.android.accessibility.talkback.keyboard.KeyComboManager;
import com.google.android.accessibility.talkback.labeling.CustomLabelManager;
import com.google.android.accessibility.talkback.labeling.LabelDialogManager;
import com.google.android.accessibility.talkback.labeling.PackageRemovalReceiver;
import com.google.android.accessibility.talkback.menurules.NodeMenuRuleProcessor;
import com.google.android.accessibility.talkback.preference.PreferencesActivityUtils;
import com.google.android.accessibility.talkback.selector.SelectorController;
import com.google.android.accessibility.talkback.speech.SpeakPasswordsManager;
import com.google.android.accessibility.talkback.training.OnboardingInitiator;
import com.google.android.accessibility.talkback.training.PageConfig;
import com.google.android.accessibility.talkback.training.PageConfig.PageId;
import com.google.android.accessibility.talkback.training.TutorialInitiator;
import com.google.android.accessibility.talkback.utils.DiagnosticOverlayControllerImpl;
import com.google.android.accessibility.talkback.utils.ExperimentalUtils;
import com.google.android.accessibility.talkback.utils.FocusIndicatorUtils;
import com.google.android.accessibility.talkback.utils.SplitCompatUtils;
import com.google.android.accessibility.talkback.utils.VerbosityPreferences;
import com.google.android.accessibility.utils.AccessibilityEventListener;
import com.google.android.accessibility.utils.AccessibilityEventUtils;
import com.google.android.accessibility.utils.AccessibilityNodeInfoUtils;
import com.google.android.accessibility.utils.BuildVersionUtils;
import com.google.android.accessibility.utils.EditTextActionHistory;
import com.google.android.accessibility.utils.FeatureSupport;
import com.google.android.accessibility.utils.FocusFinder;
import com.google.android.accessibility.utils.ImageContents;
import com.google.android.accessibility.utils.KeyboardUtils;
import com.google.android.accessibility.utils.PackageManagerUtils;
import com.google.android.accessibility.utils.Performance;
import com.google.android.accessibility.utils.Performance.EventId;
import com.google.android.accessibility.utils.ProximitySensor;
import com.google.android.accessibility.utils.ServiceKeyEventListener;
import com.google.android.accessibility.utils.ServiceStateListener;
import com.google.android.accessibility.utils.SettingsUtils;
import com.google.android.accessibility.utils.SharedKeyEvent;
import com.google.android.accessibility.utils.SharedPreferencesUtils;
import com.google.android.accessibility.utils.WindowUtils;
import com.google.android.accessibility.utils.caption.ImageCaptionStorage;
import com.google.android.accessibility.utils.input.CursorGranularity;
import com.google.android.accessibility.utils.input.InputModeManager;
import com.google.android.accessibility.utils.input.PreferenceProvider;
import com.google.android.accessibility.utils.input.ScrollEventInterpreter;
import com.google.android.accessibility.utils.input.SpeechStateMonitor;
import com.google.android.accessibility.utils.input.TextCursorTracker;
import com.google.android.accessibility.utils.input.TextEventFilter.KeyboardEchoType;
import com.google.android.accessibility.utils.input.TextEventHistory;
import com.google.android.accessibility.utils.input.TextEventInterpreter;
import com.google.android.accessibility.utils.labeling.Label;
import com.google.android.accessibility.utils.monitor.AudioPlaybackMonitor;
import com.google.android.accessibility.utils.monitor.HeadphoneStateMonitor;
import com.google.android.accessibility.utils.monitor.TouchMonitor;
import com.google.android.accessibility.utils.output.ActorStateProvider;
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
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * An {@link AccessibilityService} that provides spoken, haptic, and audible feedback.
 */
public class TalkBackService extends AccessibilityService
        implements Thread.UncaughtExceptionHandler, SpeechController.Delegate, SharedKeyEvent.Listener {
    /**
     * Accesses the current speech language.
     */
    public class SpeechLanguage {
        /**
         * Gets the current speech language.
         */
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

    /**
     * Interface for asking service flags to an {@link AccessibilityService}.
     */
    public interface ServiceFlagRequester {
        /**
         * Attempts to change the service info flag.
         *
         * @param flag           to specify the service flag to change.
         * @param requestedState {@code true} to request the service flag, or {@code false} to disable
         *                       the flag from the service.
         */
        void requestFlag(int flag, boolean requestedState);
    }

    /**
     * Whether the user has seen the TalkBack tutorial.
     */
    public static final String PREF_FIRST_TIME_USER = "first_time_user";

    /**
     * Intent to open text-to-speech settings.
     */
    public static final String INTENT_TTS_SETTINGS = "com.android.settings.TTS_SETTINGS";

    /**
     * Intent to open text-to-speech settings.
     */
    public static final String INTENT_TTS_TV_SETTINGS = "android.settings.TTS_SETTINGS";

    /**
     * Default interactive UI timeout in milliseconds.
     */
    public static final int DEFAULT_INTERACTIVE_UI_TIMEOUT_MILLIS = 10000;

    /**
     * Timeout to turn off TalkBack without waiting for callback from TTS.
     */
    private static final long TURN_OFF_TIMEOUT_MS = 5000;

    private static final long TURN_OFF_WAIT_PERIOD_MS = 1000;

    /**
     * An active instance of TalkBack.
     */
    private static @Nullable TalkBackService instance = null;

    /* Call setAnimationScale with this value will disable animation. */
    private static float ANIMATION_OFF = 0;

    private static final String TAG = "TalkBackService";

    /**
     * List of key event processors. Processors in the list are sent the event in the order they were
     * added until a processor consumes the event.
     */
    private final List<ServiceKeyEventListener> keyEventListeners = new ArrayList<>();

    /**
     * The current state of the service.
     */
    private int serviceState;

    /**
     * Components to receive callbacks on changes in the service's state.
     */
    private List<ServiceStateListener> serviceStateListeners = new ArrayList<>();

    /**
     * Controller for speech feedback.
     */
    private SpeechControllerImpl speechController;

    /**
     * Controller for diagnostic overlay (developer mode).
     */
    private DiagnosticOverlayControllerImpl diagnosticOverlayController;

    /**
     * Staged pipeline for separating interpreters, feedback-mappers, and actors.
     */
    private Pipeline pipeline;

    /**
     * Controller for audio and haptic feedback.
     */
    private FeedbackController feedbackController;

    /**
     * Watches the proximity sensor, and silences feedback when triggered.
     */
    private ProximitySensorListener proximitySensorListener;

    private PassThroughModeActor passThroughModeActor;
    private GlobalVariables globalVariables;
    private EventFilter eventFilter;
    private TextEventInterpreter textEventInterpreter;
    private Compositor compositor;
    private DirectionNavigationActor.StateReader directionNavigationActorStateReader;
    private FullScreenReadActor fullScreenReadActor;
    private EditTextActionHistory editTextActionHistory;

    /**
     * Interface for monitoring current and previous cursor position in editable node
     */
    private TextCursorTracker textCursorTracker;

    /**
     * Monitors the call state for the phone device.
     */
    private CallStateMonitor callStateMonitor;

    /**
     * Monitors voice actions from other applications
     */
    private VoiceActionMonitor voiceActionMonitor;

    /**
     * Monitors speech actions from other applications
     */
    private SpeechStateMonitor speechStateMonitor;

    /**
     * Maintains cursor state during explore-by-touch by working around EBT problems.
     */
    private ProcessorCursorState processorCursorState;

    /**
     * Processor for allowing clicking on buttons in permissions dialogs.
     */
    private ProcessorPermissionDialogs processorPermissionsDialogs;

    /**
     * Controller for manage keyboard commands
     */
    private KeyComboManager keyComboManager;

    /**
     * Manager for showing radial menus.
     */
    private ListMenuManager menuManager;

    /**
     * Manager for handling custom labels.
     */
    private CustomLabelManager labelManager;

    /**
     * Manager for the screen search feature.
     */
    private UniversalSearchManager universalSearchManager;

    /**
     * Orientation monitor for watching orientation changes.
     */
    private OrientationMonitor orientationMonitor;

    /**
     * {@link BroadcastReceiver} for tracking the ringer and screen states.
     */
    private RingerModeAndScreenMonitor ringerModeAndScreenMonitor;

    /**
     * {@link BroadcastReceiver} for tracking volume changes.
     */
    private VolumeMonitor volumeMonitor;

    /**
     * {@link android.content.BroadcastReceiver} for tracking battery status changes.
     */
    private BatteryMonitor batteryMonitor;

    /**
     * {@link BroadcastReceiver} for tracking headphone connected status changes.
     */
    private HeadphoneStateMonitor headphoneStateMonitor;

    /**
     * Tracks changes to audio output and provides information on what types of audio are playing.
     */
    private AudioPlaybackMonitor audioPlaybackMonitor;

    /**
     * Manages screen dimming
     */
    private DimScreenActor dimScreenController;

    /**
     * The television controller; non-null if the device is a television (Android TV).
     */
    private TelevisionNavigationController televisionNavigationController;

    private TelevisionDPadManager televisionDPadManager;

    /**
     * {@link BroadcastReceiver} for tracking package removals for custom label data consistency.
     */
    private PackageRemovalReceiver packageReceiver;

    /**
     * The analytics instance, used for sending data to Google Analytics.
     */
    private TalkBackAnalyticsImpl analytics;

    /**
     * Callback to be invoked when fingerprint gestures are being used for accessibility.
     */
    private FingerprintGestureCallback fingerprintGestureCallback;

    /**
     * Controller for the selector
     */
    private SelectorController selectorController;

    /**
     * Controller for handling gestures
     */
    private GestureController gestureController;

    /**
     * Speech recognition wrapper for voice commands
     */
    private SpeechRecognizerActor speechRecognizer;

    /**
     * Processor for voice commands
     */
    private VoiceCommandProcessor voiceCommandProcessor;

    /**
     * Shared preferences used within TalkBack.
     */
    private SharedPreferences prefs;

    /**
     * The system's uncaught exception handler
     */
    private UncaughtExceptionHandler systemUeh;

    /**
     * The system feature if the device supports touch screen
     */
    private boolean supportsTouchScreen = true;

    /**
     * Whether the current root node is dirty or not.
     */
    private boolean isRootNodeDirty = true;
    /**
     * Keep Track of current root node.
     */
    private AccessibilityNodeInfo rootNode;

    private AccessibilityEventProcessor accessibilityEventProcessor;

    /**
     * Keeps track of whether we need to run the locked-boot-completed callback when connected.
     */
    private boolean lockedBootCompletedPending;

    private final InputModeManager inputModeManager = new InputModeManager();
    private ProcessorAccessibilityHints processorHints;
    private ProcessorScreen processorScreen;
    @Nullable
    private ProcessorMagnification processorMagnification;
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

    /**
     * A reference to the active Braille IME if any.
     */
    private @Nullable BrailleImeForTalkBack brailleImeForTalkBack;

    private BrailleDisplayForTalkBack brailleDisplay;

    private GestureShortcutMapping gestureShortcutMapping;
    private NodeMenuRuleProcessor nodeMenuRuleProcessor;
    private PrimesController primesController;
    private SpeechLanguage speechLanguage;
    private boolean isBrailleKeyboardActivated;
    private ImageCaptioner imageCaptioner;
    private ImageContents imageContents;
    private @Nullable Boolean useServiceGestureDetection;

    private final @NonNull Map<Integer, TouchInteractionMonitor> displayIdToTouchInteractionMonitor =
            new HashMap<>();

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
            pipeline.onUnbind(calculateFinalAnnouncementVolume(), disableTalkBackCompleteAction);
        }
        if (gestureShortcutMapping != null) {
            gestureShortcutMapping.onUnbind();
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
        // Resume animation if necessary.
        enableAnimation(/* enable= */ true);
        return false;
    }

    @Override
    public void onDestroy() {
        // INFO: TalkBack For Developers modification
        AdbReceiver.unregisterAdbReceiver(this);
        // ------------------------------------------
        if (shouldUseTalkbackGestureDetection()) {
            unregisterGestureDetection();
        }

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
            pipeline
                    .getFeedbackReturner()
                    .returnFeedback(EVENT_ID_UNTRACKED, Feedback.renewOverlay(newConfig));
        }

        if (isServiceActive() && (orientationMonitor != null)) {
            orientationMonitor.onConfigurationChanged(newConfig);
        }

        if (gestureShortcutMapping != null) {
            gestureShortcutMapping.onConfigurationChanged(newConfig);
        }

        if (pipeline != null) {
            resetTouchExplorePassThrough();
            pipeline
                    .getFeedbackReturner()
                    .returnFeedback(
                            EVENT_ID_UNTRACKED, Feedback.deviceInfo(Action.CONFIG_CHANGED, newConfig));
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

        // Re-apply diagnosis-mode logging, in case other accessibility-services changed the shared
        // log-level preference.
        enforceDiagnosisModeLogging();

        if (diagnosticOverlayController != null) {
            diagnosticOverlayController.displayEvent(event);
        }
    }

    public boolean supportsTouchScreen() {
        return supportsTouchScreen;
    }

    @Override
    public @Nullable AccessibilityNodeInfo getRootInActiveWindow() {
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

    /**
     * Stops all delayed events in the service.
     */
    public void clearQueues() {
        interruptAllFeedback(/* stopTtsSpeechCompletely= */ false);
        processorEventQueue.clearQueue();
        if (processorScreen != null && processorScreen.getWindowEventInterpreter() != null) {
            processorScreen.getWindowEventInterpreter().clearQueue();
        }
        // TODO: Clear queues wherever there are message handlers that delay event processing.
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
            textEventInterpreter.setLastKeyEventTime(keyEvent.getEventTime());
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
    public TextCursorTracker getTextCursorTracker() {
        return textCursorTracker;
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

    /**
     * Registers the dialog to {@link RingerModeAndScreenMonitor} for screen monitor.
     */
    public void registerDialog(DialogInterface dialog) {
        if (ringerModeAndScreenMonitor != null) {
            ringerModeAndScreenMonitor.registerDialog(dialog);
        }
    }

    /**
     * Unregisters the dialog from {@link RingerModeAndScreenMonitor} for screen monitor.
     */
    public void unregisterDialog(DialogInterface dialog) {
        if (ringerModeAndScreenMonitor != null) {
            ringerModeAndScreenMonitor.unregisterDialog(dialog);
        }
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

        // INFO: TalkBack For Developers modification
        AdbReceiver.registerAdbReceiver(this);
        // ------------------------------------------

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

        compositor.handleEvent(Compositor.EVENT_SPOKEN_FEEDBACK_ON, EVENT_ID_UNTRACKED);

        // If the locked-boot-completed intent was fired before onServiceConnected, we queued it,
        // so now we need to run it.
        if (lockedBootCompletedPending) {
            onLockedBootCompletedInternal();
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
            OnboardingInitiator.showOnboardingIfNecessary(this);
        }

        // Service gesture detection.
        if (shouldUseTalkbackGestureDetection()) {
            registerGestureDetection();
        }

        primesController.stopTimer(Timer.START_UP);
    }

    /**
     * @return The current state of the TalkBack service, or {@code INACTIVE} if the service is not
     * initialized.
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

    /**
     * @return {@code true} if TalkBack is running and initialized, {@code false} otherwise.
     */
    public static boolean isServiceActive() {
        return (getServiceState() == ServiceStateListener.SERVICE_STATE_ACTIVE);
    }

    /**
     * Returns the active TalkBack instance, or {@code null} if not available.
     */
    public static @Nullable TalkBackService getInstance() {
        return instance;
    }

    /**
     * Initialize {@link FingerprintGestureCallback} for detecting fingerprint gestures.
     */
    private void initializeFingerprintGestureCallback() {
        if (fingerprintGestureCallback != null || !FeatureSupport.isFingerprintGestureSupported(this)) {
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
        speechStateMonitor = new SpeechStateMonitor();
        diagnosticOverlayController = new DiagnosticOverlayControllerImpl(this);

        gestureShortcutMapping = new GestureShortcutMapping(this);

        globalVariables =
                new GlobalVariables(this, inputModeManager, keyComboManager, gestureShortcutMapping);

        labelManager = new CustomLabelManager(this);
        addEventListener(labelManager);

        ImageCaptionStorage imageCaptionStorage = new ImageCaptionStorage();
        imageContents =
                ImageCaptioner.supportsImageCaption(this)
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

        analytics = new TalkBackAnalyticsImpl(this);

        processorPhoneticLetters = new ProcessorPhoneticLetters(this);

        FocusFinder focusFinder = new FocusFinder(this);

        // Construct system-monitors.
        batteryMonitor = new BatteryMonitor(this);
        callStateMonitor = new CallStateMonitor(this);
        audioPlaybackMonitor = new AudioPlaybackMonitor(this);
        @NonNull TouchMonitor touchMonitor = new TouchMonitor();

        // Construct event-interpreters.
        AutoScrollInterpreter autoScrollInterpreter = new AutoScrollInterpreter();
        screenStateMonitor = new ScreenStateMonitor(/* service= */ this);
        FullScreenReadInterpreter fullScreenReadInterpreter = new FullScreenReadInterpreter();
        scrollPositionInterpreter = new ScrollPositionInterpreter();
        ScrollEventInterpreter scrollEventInterpreter =
                new ScrollEventInterpreter(audioPlaybackMonitor, touchMonitor);
        ManualScrollInterpreter manualScrollInterpreter = new ManualScrollInterpreter();

        // Constructor output-actor-state.
        textCursorTracker = new TextCursorTracker();
        editTextActionHistory = new EditTextActionHistory();
        AccessibilityFocusActionHistory focusHistory = new AccessibilityFocusActionHistory(this);

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
        directionNavigationActorStateReader = directionNavigationActor.state;
        TextEditActor editor =
                new TextEditActor(
                        this,
                        editTextActionHistory,
                        textCursorTracker,
                        getSystemService(ClipboardManager.class));
        fullScreenReadActor =
                new FullScreenReadActor(accessibilityFocusMonitor, this, speechController);
        dimScreenController = new DimScreenActor(this, gestureShortcutMapping);

        accessibilityFocusInterpreter =
                new AccessibilityFocusInterpreter(
                        this, accessibilityFocusMonitor, screenStateMonitor.state);

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

        UniversalSearchActor universalSearchActor =
                new UniversalSearchActor(this, screenStateMonitor.state, focusFinder, labelManager);

        autoScrollInterpreter.setUniversalSearchActor(universalSearchActor);

        voiceCommandProcessor =
                new VoiceCommandProcessor(this, accessibilityFocusMonitor, selectorController, analytics);
        speechRecognizer = new SpeechRecognizerActor(this, voiceCommandProcessor, analytics);
        UiChangeEventInterpreter uiChangeEventInterpreter = new UiChangeEventInterpreter();
        addEventListener(uiChangeEventInterpreter);

        UserInterface userInterface = new UserInterface(selectorController);

        // Construct pipeline.
        pipeline =
                new Pipeline(
                        this,
                        new Monitors(batteryMonitor, callStateMonitor, touchMonitor, speechStateMonitor),
                        new Interpreters(
                                inputFocusInterpreter,
                                scrollEventInterpreter,
                                manualScrollInterpreter,
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
                                new AccessibilityEventIdleInterpreter(),
                                uiChangeEventInterpreter),
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
                                new GestureReporter(this, new GestureHistory()),
                                imageCaptioner,
                                universalSearchActor,
                                this::requestServiceFlag),
                        proximitySensorListener,
                        speechController,
                        diagnosticOverlayController,
                        compositor,
                        userInterface);

        processorHints.setActorState(pipeline.getActorState());
        processorHints.setPipeline(pipeline.getFeedbackReturner());

        voiceCommandProcessor.setActorState(pipeline.getActorState());
        voiceCommandProcessor.setPipeline(pipeline.getFeedbackReturner());

        accessibilityEventProcessor.setActorState(pipeline.getActorState());
        accessibilityEventProcessor.setAccessibilityEventIdleListener(pipeline);

        autoScrollInterpreter.setDirectionNavigationActor(directionNavigationActor);

        nodeMenuRuleProcessor =
                new NodeMenuRuleProcessor(
                        this, pipeline.getFeedbackReturner(), pipeline.getActorState(), analytics);
        compositor.setNodeMenuProvider(nodeMenuRuleProcessor);

        compositor.setSpeaker(pipeline.getSpeaker());

        TouchExplorationInterpreter touchExplorationInterpreter =
                new TouchExplorationInterpreter(inputModeManager);

        if (FeatureSupport.supportMagnificationController()) {
            processorMagnification =
                    new ProcessorMagnification(
                            getMagnificationController(),
                            globalVariables,
                            compositor,
                            FeatureSupport.supportWindowMagnification(this));
        }

        // Register AccessibilityEventListeners
        addEventListener(touchExplorationInterpreter);
        addEventListener(directionNavigationInterpreter);
        if (processorMagnification != null) {
            addEventListener(processorMagnification);
        }
        addEventListener(pipeline);

        touchExplorationInterpreter.addTouchExplorationActionListener(accessibilityFocusInterpreter);
        screenStateMonitor.addScreenStateChangeListener(accessibilityFocusInterpreter);

        screenStateMonitor.addScreenStateChangeListener(inputFocusInterpreter);

        voiceActionMonitor = new VoiceActionMonitor(this, callStateMonitor, speechStateMonitor);
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

        keyComboManager =
                new KeyComboManager(
                        this,
                        pipeline.getFeedbackReturner(),
                        selectorController,
                        menuManager,
                        fullScreenReadActor);

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
                new ProcessorVolumeStream(pipeline.getActorState(), this);
        addEventListener(processorVolumeStream);
        keyEventListeners.add(processorVolumeStream);

        gestureController =
                new GestureController(
                        this,
                        pipeline.getFeedbackReturner(),
                        pipeline.getActorState(),
                        menuManager,
                        selectorController,
                        accessibilityFocusMonitor,
                        gestureShortcutMapping);

        audioPlaybackMonitor = new AudioPlaybackMonitor(this);

        // Add event processors. These will process incoming AccessibilityEvents
        // in the order they are added.
        eventFilter = new EventFilter(compositor, this, touchMonitor, globalVariables);
        eventFilter.setVoiceActionDelegate(voiceActionMonitor);
        eventFilter.setAccessibilityFocusEventInterpreter(accessibilityFocusInterpreter);
        ActorStateProvider actorStateProvider =
                new ActorStateProvider() {
                    @Override
                    public boolean resettingNodeCursor() {
                        return globalVariables.resettingNodeCursor();
                    }
                };
        PreferenceProvider preferenceProvider =
                new PreferenceProvider() {
                    @Override
                    public boolean shouldSpeakPasswords() {
                        return globalVariables.shouldSpeakPasswords();
                    }
                };
        textEventInterpreter =
                new TextEventInterpreter(
                        this,
                        textCursorTracker,
                        directionNavigationActor.state,
                        inputModeManager,
                        new TextEventHistory(),
                        editTextActionHistory.provider,
                        actorStateProvider,
                        preferenceProvider,
                        voiceActionMonitor);
        processorEventQueue = new ProcessorEventQueue(eventFilter, textEventInterpreter);

        addEventListener(processorEventQueue);
        addEventListener(processorPhoneticLetters);

        // Create window event interpreter and announcer.
        processorScreen =
                new ProcessorScreen(
                        this,
                        processorHints,
                        keyComboManager,
                        focusFinder,
                        gestureShortcutMapping,
                        pipeline.getFeedbackReturner());
        globalVariables.setWindowsDelegate(processorScreen.getWindowEventInterpreter());
        screenStateMonitor.setWindowsDelegate(processorScreen.getWindowEventInterpreter());
        addEventListener(processorScreen);

        // Monitor window transition status by registering listeners.
        if (processorScreen != null && processorScreen.getWindowEventInterpreter() != null) {
            processorScreen.getWindowEventInterpreter().addListener(menuManager);
            processorScreen.getWindowEventInterpreter().addListener(screenStateMonitor);
            processorScreen.getWindowEventInterpreter().addListener(uiChangeEventInterpreter);
            processorScreen.getWindowEventInterpreter().addListener(imageCaptioner);
        }

        processorCursorState =
                new ProcessorCursorState(this, pipeline.getFeedbackReturner(), globalVariables);
        processorPermissionsDialogs =
                new ProcessorPermissionDialogs(
                        this, pipeline.getActorState(), pipeline.getFeedbackReturner());

        volumeMonitor = new VolumeMonitor(pipeline.getFeedbackReturner(), this, callStateMonitor);

        // TODO: Move this into the custom label manager code
        packageReceiver = new PackageRemovalReceiver();

        addEventListener(new ProcessorGestureVibrator(pipeline.getFeedbackReturner()));

        universalSearchManager =
                new UniversalSearchManager(
                        pipeline.getFeedbackReturner(),
                        ringerModeAndScreenMonitor,
                        processorScreen.getWindowEventInterpreter());

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

        brailleDisplay = new BrailleDisplay(this, talkBackForBrailleDisplay);

        BrailleIme.initialize(
                this, talkBackForBrailleIme, brailleDisplay.getBrailleDisplayForBrailleIme());
        analytics.onTalkBackServiceStarted();
    }

    private final TalkBackForBrailleDisplay talkBackForBrailleDisplay =
            new TalkBackForBrailleDisplay() {
                @Override
                public boolean performAction(ScreenReaderAction action, Object... args) {
                    // TODO: implement the screen reader actions.
                    if (action == ScreenReaderAction.OPEN_TALKBACK_MENU) {
                        return menuManager.showMenu(R.menu.context_menu, EVENT_ID_UNTRACKED);
                    }
                    return BrailleDisplayHelper.performAction(
                            getInstance(), pipeline.getFeedbackReturner(), action, args);
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
                public CharSequence getCustomLabelText(AccessibilityNodeInfoCompat node) {
                    Label label = labelManager.getLabelForViewIdFromCache(node.getViewIdResourceName());
                    if (label != null) {
                        return label.getText();
                    }
                    return null;
                }

                @Override
                public boolean needsLabel(AccessibilityNodeInfoCompat node) {
                    return labelManager.needsLabel(node);
                }

                @Override
                public @Nullable BrailleImeForBrailleDisplay getBrailleImeForBrailleDisplay() {
                    return brailleImeForTalkBack == null
                            ? null
                            : brailleImeForTalkBack.getBrailleImeForBrailleDisplay();
                }

                @Override
                public void speak(CharSequence textToSpeak, int delayMs, SpeakOptions speakOptions) {
                    pipeline
                            .getFeedbackReturner()
                            .returnFeedback(
                                    Performance.EVENT_ID_UNTRACKED,
                                    Feedback.speech(textToSpeak, speakOptions).setDelayMs(delayMs));
                }
            };

    private final TalkBackForBrailleIme talkBackForBrailleIme =
            new TalkBackForBrailleIme() {
                @Override
                public void onBrailleImeActivated(
                        BrailleImeForTalkBack brailleImeForTalkBack,
                        boolean disableEbt,
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
                        requestTouchExploration(!disableEbt);
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
                public boolean setInputMethodEnabled() {
                    if (FeatureSupport.supportEnableDisableIme() && getInstance() != null) {
                        return getSoftKeyboardController()
                                .setInputMethodEnabled(
                                        KeyboardUtils.getImeId(getInstance(), getPackageName()),
                                        /* enabled= */ true)
                                == SoftKeyboardController.ENABLE_IME_SUCCESS;
                    }
                    return false;
                }

                @Override
                public WindowManager getWindowManager() {
                    return (WindowManager) getSystemService(Context.WINDOW_SERVICE);
                }

                @Override
                public ServiceStatus getServiceStatus() {
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
                    @KeyboardEchoType
                    int echoType =
                            brailleDisplay
                                    .getBrailleDisplayForBrailleIme()
                                    .isBrailleDisplayConnectedAndNotSuspended()
                                    ? readPhysicalKeyboardEcho()
                                    : readOnScreenKeyboardEcho();
                    return echoType == PREF_ECHO_CHARACTERS || echoType == PREF_ECHO_CHARACTERS_AND_WORDS;
                }

                @Override
                public boolean shouldSpeakPassword() {
                    return globalVariables.shouldSpeakPasswords();
                }

                @Override
                public boolean shouldUseCharacterGranularity() {
                    CursorGranularity granularity =
                            directionNavigationActorStateReader.getCurrentGranularity();
                    return granularity == CursorGranularity.CHARACTER || !granularity.isMicroGranularity();
                }

                @Override
                public void moveCursorForward() {
                    if (directionNavigationActorStateReader.getCurrentGranularity().isMicroGranularity()) {
                        selectorController.adjustSelectedSetting(EVENT_ID_UNTRACKED, /* isNext= */ true);
                    }
                }

                @Override
                public void moveCursorBackward() {
                    if (directionNavigationActorStateReader.getCurrentGranularity().isMicroGranularity()) {
                        selectorController.adjustSelectedSetting(EVENT_ID_UNTRACKED, /* isNext= */ false);
                    }
                }
            };

    @Compositor.Flavor
    public int getCompositorFlavor() {
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
            LogUtils.e(TAG, "Fail to get service flag!");
        } else {
            info.flags |= ExperimentalUtils.getAdditionalTalkBackServiceFlags();
            if (FeatureSupport.isMultiFingerGestureSupported()) {
                info.flags |=
                        AccessibilityServiceInfo.FLAG_REQUEST_MULTI_FINGER_GESTURES
                                | AccessibilityServiceInfo.FLAG_REQUEST_2_FINGER_PASSTHROUGH;
                resetTouchExplorePassThrough();
            } else {
                info.flags &=
                        ~(AccessibilityServiceInfo.FLAG_REQUEST_MULTI_FINGER_GESTURES
                                | AccessibilityServiceInfo.FLAG_REQUEST_2_FINGER_PASSTHROUGH);
            }
            if (GestureReporter.ENABLED) {
                info.flags |= AccessibilityServiceInfo.FLAG_SEND_MOTION_EVENTS;
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
        }

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

        if (processorMagnification != null) {
            processorMagnification.onResumeInfrastructure();
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

        setServiceState(ServiceStateListener.SERVICE_STATE_SHUTTING_DOWN);

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

        interruptAllFeedback(/* stopTtsSpeechCompletely */ false);

        // Some apps depend on these being set to false when TalkBack is disabled.
        if (supportsTouchScreen) {
            requestTouchExploration(false);
        }

        prefs.unregisterOnSharedPreferenceChangeListener(sharedPreferenceChangeListener);
        prefs.unregisterOnSharedPreferenceChangeListener(analytics);

        unregisterReceivers(ringerModeAndScreenMonitor, batteryMonitor, packageReceiver, volumeMonitor);

        if (volumeMonitor != null) {
            volumeMonitor.releaseControl();
        }

        if (headphoneStateMonitor != null) {
            headphoneStateMonitor.stopMonitoring();
        }

        // Remove any pending notifications that shouldn't persist.
        final NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        nm.cancelAll();

        if (processorMagnification != null) {
            processorMagnification.onSuspendInfrastructure();
        }

        if ((fingerprintGestureCallback != null) && (getFingerprintGestureController() != null)) {
            getFingerprintGestureController()
                    .unregisterFingerprintGestureCallback(fingerprintGestureCallback);
        }

        if (FeatureSupport.isFingerprintGestureSupported(this)) {
            requestServiceFlag(AccessibilityServiceInfo.FLAG_REQUEST_FINGERPRINT_GESTURES, false);
        }

        if (brailleImeForTalkBack != null) {
            brailleImeForTalkBack.onTalkBackSuspended();
        }
        brailleDisplay.stop();
    }

    /**
     * Shuts down the infrastructure in case it has been initialized.
     */
    private void shutdownInfrastructure() {
        setServiceState(ServiceStateListener.SERVICE_STATE_SHUTTING_DOWN);
        // we put it first to be sure that screen dimming would be removed even if code bellow
        // will crash by any reason. Because leaving user with dimmed screen is super bad
        // We check the instance against null to prevent the premature service destroy (aka destroy
        // before connected).
        if (dimScreenController != null) {
            dimScreenController.shutdown();
        }

        if (fullScreenReadActor != null) {
            fullScreenReadActor.shutdown();
        }

        if (labelManager != null) {
            labelManager.shutdown();
        }

        if (imageCaptioner != null) {
            imageCaptioner.shutdown();
        }

        if (proximitySensorListener != null) {
            proximitySensorListener.shutdown();
        }
        if (feedbackController != null) {
            feedbackController.shutdown();
        }
        if (pipeline != null) {
            pipeline.shutdown();
        }
        if (analytics != null) {
            analytics.onTalkBackServiceStopped();
        }
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

    /**
     * Returns a boolean preference by resource id.
     */
    private boolean getBooleanPref(int prefKeyResId, int prefDefaultResId) {
        return SharedPreferencesUtils.getBooleanPref(
                prefs, getResources(), prefKeyResId, prefDefaultResId);
    }

    /**
     * When the device supports {@link AccessibilityService#setAnimationScale(float)}, system will
     * determine to disable animation feature when TalkBack is on, and resume it after TalkBack is
     * off.
     *
     * @param enable {@code false} to request the disable of animation, and {@code true} to resume the
     *               animation.
     */
    private void enableAnimation(boolean enable) {
        if (!FeatureSupport.supportsServiceControlOfGlobalAnimations()) {
            return;
        }
        if (enable) {
            if (prefs.contains(getString(R.string.pref_previous_global_window_animation_scale_key))) {
                float scale =
                        SharedPreferencesUtils.getFloatFromStringPref(
                                prefs,
                                getResources(),
                                R.string.pref_previous_global_window_animation_scale_key,
                                R.string.pref_window_animation_scale_default);
                if (scale > ANIMATION_OFF && SettingsUtils.isAnimationDisabled(this)) {
                    // Resume animation when the record value is meaningful (greater than zero);
                    setAnimationScale(scale);
                }
                prefs
                        .edit()
                        .remove(getString(R.string.pref_previous_global_window_animation_scale_key))
                        .apply();
            }
        } else {
            if (!SettingsUtils.isAnimationDisabled(this)) {
                prefs
                        .edit()
                        .putString(
                                getString(R.string.pref_previous_global_window_animation_scale_key),
                                Float.toString(
                                        Settings.Global.getFloat(
                                                getContentResolver(), Settings.Global.WINDOW_ANIMATION_SCALE, 1)))
                        .apply();
            }
            // Disable animation;
            setAnimationScale(ANIMATION_OFF);
        }
    }

    /**
     * Reloads service preferences.
     */
    private void reloadPreferences() {
        final Resources res = getResources();

        LogUtils.v(
                TAG,
                "TalkBackService.reloadPreferences() diagnostic mode=%s",
                PreferencesActivityUtils.isDiagnosisModeOn(prefs, res));

        // Preferece to reduce window announcement delay.
        boolean reduceDelayPref =
                getBooleanPref(
                        R.string.pref_reduce_window_delay_key, R.bool.pref_reduce_window_delay_default);
        if (processorScreen != null && processorScreen.getWindowEventInterpreter() != null) {
            processorScreen.getWindowEventInterpreter().setReduceDelayPref(reduceDelayPref);
            enableAnimation(!reduceDelayPref);
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
                PreferencesActivityUtils.getDiagnosticPref(
                        prefs, res, R.string.pref_log_overlay_key, R.bool.pref_log_overlay_default);
        diagnosticOverlayController.setLogOverlayEnabled(logOverlayEnabled);

        accessibilityEventProcessor.setSpeakWhenScreenOff(
                VerbosityPreferences.getPreferenceValueBool(
                        prefs,
                        res,
                        res.getString(R.string.pref_screenoff_key),
                        res.getBoolean(R.bool.pref_screenoff_default)));

        accessibilityEventProcessor.setDumpEventMask(
                prefs.getInt(res.getString(R.string.pref_dump_event_mask_key), 0));

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
        accessibilityFocusInterpreter.setTypingLongPressDurationMs(
                SharedPreferencesUtils.getIntFromStringPref(
                        prefs,
                        res,
                        R.string.pref_typing_long_press_duration_key,
                        R.string.pref_typing_long_press_duration_default));
        globalVariables.setInterpretAsEntryKey(
                accessibilityFocusInterpreter.getTypingMethod() == FORCE_LIFT_TO_TYPE_ON_IME);

        if (supportsTouchScreen && !isBrailleKeyboardActivated) {
            // Touch exploration *must* be enabled on TVs for TalkBack to function.
            final boolean touchExploration =
                    (FeatureSupport.isTv(this)
                            || getBooleanPref(
                            R.string.pref_explore_by_touch_key, R.bool.pref_explore_by_touch_default));
            requestTouchExploration(touchExploration);
        }

        if (FeatureSupport.isMultiFingerGestureSupported()) {
            requestServiceFlag(
                    AccessibilityServiceInfo.FLAG_REQUEST_MULTI_FINGER_GESTURES
                            | AccessibilityServiceInfo.FLAG_REQUEST_2_FINGER_PASSTHROUGH,
                    /* newValue= */ true);
            resetTouchExplorePassThrough();
        }

        processorCursorState.onReloadPreferences(this);
        processorPermissionsDialogs.onReloadPreferences(this);

        voiceCommandProcessor.setEchoRecognizedTextEnabled(
                PreferencesActivityUtils.getDiagnosticPref(
                        this,
                        R.string.pref_echo_recognized_text_speech_key,
                        R.bool.pref_echo_recognized_text_default));

        // Reload speech preferences.
        pipeline.setOverlayEnabled(
                PreferencesActivityUtils.getDiagnosticPref(
                        this, R.string.pref_tts_overlay_key, R.bool.pref_tts_overlay_default));
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
        textEventInterpreter.setOnScreenKeyboardEcho(onScreenKeyboardPref);

        int physicalKeyboardPref = readPhysicalKeyboardEcho();
        textEventInterpreter.setPhysicalKeyboardEcho(physicalKeyboardPref);

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
                FeatureSupport.isFingerprintGestureSupported(this)
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
            globalVariables.setSpeakCollectionInfo(speakCollectionInfo);

            // Update preference: speak roles.
            boolean speakRoles =
                    VerbosityPreferences.getPreferenceValueBool(
                            prefs,
                            res,
                            res.getString(R.string.pref_speak_roles_key),
                            res.getBoolean(R.bool.pref_speak_roles_default));
            globalVariables.setSpeakRoles(speakRoles);

            // Update preference: speak system window titles.
            boolean speakWindowTitle =
                    VerbosityPreferences.getPreferenceValueBool(
                            prefs,
                            res,
                            res.getString(R.string.pref_speak_system_window_titles_key),
                            res.getBoolean(R.bool.pref_speak_system_window_titles_default));
            globalVariables.setSpeakSystemWindowTitles(speakWindowTitle);

            // Update preference: description order.
            String descriptionOrder =
                    SharedPreferencesUtils.getStringPref(
                            prefs, res, R.string.pref_node_desc_order_key, R.string.pref_node_desc_order_default);
            globalVariables.setDescriptionOrder(prefValueToDescriptionOrder(res, descriptionOrder));

            // Update preference: speak element IDs.
            boolean speakElementIds =
                    getBooleanPref(
                            R.string.pref_speak_element_ids_key, R.bool.pref_speak_element_ids_default);
            globalVariables.setSpeakElementIds(speakElementIds);

            // Update preference: speak usage hints.
            boolean speakUsageHints =
                    VerbosityPreferences.getPreferenceValueBool(
                            prefs,
                            res,
                            res.getString(R.string.pref_a11y_hints_key),
                            res.getBoolean(R.bool.pref_a11y_hints_default));
            globalVariables.setUsageHintEnabled(speakUsageHints);
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

    private int readPhysicalKeyboardEcho() {
        return Integer.parseInt(
                VerbosityPreferences.getPreferenceValueString(
                        prefs,
                        getResources(),
                        getResources().getString(R.string.pref_keyboard_echo_physical_key),
                        getResources().getString(R.string.pref_keyboard_echo_default)));
    }

    private void reloadPreferenceLogLevel() {
        LogUtils.setLogLevel(
                SharedPreferencesUtils.getIntFromStringPref(
                        prefs, getResources(), R.string.pref_log_level_key, R.string.pref_log_level_default));
        enforceDiagnosisModeLogging();
    }

    private void enforceDiagnosisModeLogging() {
        if ((LogUtils.getLogLevel() != Log.VERBOSE)
                && PreferencesActivityUtils.isDiagnosisModeOn(prefs, getResources())) {
            LogUtils.setLogLevel(Log.VERBOSE);
        }
    }

    private void reloadSilenceOnProximity() {
        final boolean silenceOnProximity =
                getBooleanPref(R.string.pref_proximity_key, R.bool.pref_proximity_default);
        proximitySensorListener.setSilenceOnProximity(silenceOnProximity);
    }

    @Compositor.DescriptionOrder
    private static int prefValueToDescriptionOrder(Resources resources, String value) {
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
     * disabled or {@code null} if we couldn't get the state of touch exploration.
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
     * now disabled or {@code null} if we couldn't get the state of touch exploration which means
     * no change to touch exploration state occurred.
     */
    private @Nullable Boolean requestTouchExploration(boolean requestedState) {
        requestServiceFlag(
                AccessibilityServiceInfo.FLAG_REQUEST_TOUCH_EXPLORATION_MODE, requestedState);
        return isTouchExplorationEnabled();
    }

    /**
     * Attempts to change the service info flag.
     *
     * @param flags    to specify the service flags to change.
     * @param newValue {@code true} to request service flag change.
     */
    private void requestServiceFlag(int flags, boolean newValue) {
        final AccessibilityServiceInfo info = getServiceInfo();
        if (info == null) {
            return;
        }

        // No need to make changes if
        // 1. newValue is true and current value of the requested flags are all set, or
        // 2. newValue is false and current value of the requested flags are all clear.
        boolean noChange = newValue ? ((info.flags & flags) == flags) : ((info.flags & flags) == 0);
        if (noChange) {
            return;
        }

        if (newValue) {
            info.flags |= flags;
        } else {
            info.flags &= ~flags;
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

    /**
     * Reloads preferences whenever their values change.
     */
    private final OnSharedPreferenceChangeListener sharedPreferenceChangeListener =
            new OnSharedPreferenceChangeListener() {
                @Override
                public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
                    LogUtils.d(TAG, "A shared preference changed: %s", key);
                    if (getResources()
                            .getString(R.string.pref_previous_global_window_animation_scale_key)
                            .equals(key)) {
                        // The stored animation factor is no related to TalkBack Settings at all. We skip to
                        // reloadPreferences to avoid the additional of Talkback re-configuration.
                        return;
                    }
                    reloadPreferences();
                }
            };

    /**
     * Called when the training page is switched.
     *
     * <p>This method should only be called by {@link IpcService}, which is the only class that can
     * provide the ipcService argument.
     */
    public static void handleTrainingPageSwitched(IpcService ipcService, @NonNull PageId pageId) {
        if (ipcService == null) {
            return;
        }

        @Nullable TalkBackService talkBackService = TalkBackService.getInstance();
        if (talkBackService == null) {
            return;
        }

        @Nullable GestureController gestureController = talkBackService.gestureController;
        if (gestureController != null) {
            @Nullable PageConfig pageConfig = PageConfig.getPage(pageId);
            gestureController.setCaptureGestureIdToAnnouncements(
                    pageConfig == null ? ImmutableMap.of() : pageConfig.getCaptureGestureIdToAnnouncements());
        }

        // Request phone permission after TalkBack tutorial is finished.
        @Nullable CallStateMonitor callStateMonitor = talkBackService.callStateMonitor;
        @Nullable SharedPreferences prefs = talkBackService.prefs;
        if (callStateMonitor != null && prefs != null && pageId == PAGE_ID_FINISHED) {
            callStateMonitor.requestPhonePermissionIfNeeded(prefs);
        }
    }

    public void onLockedBootCompleted(EventId eventId) {
        if (serviceState == ServiceStateListener.SERVICE_STATE_INACTIVE) {
            // onServiceConnected has not completed yet. We need to defer the boot completion
            // callback until after onServiceConnected has run.
            lockedBootCompletedPending = true;
        } else {
            // onServiceConnected has already completed, so we should run the callback now.
            onLockedBootCompletedInternal();
        }
    }

    private void onLockedBootCompletedInternal() {
        // Update TTS quietly.
        // If the TTS changes here, it is probably a non-FBE TTS that didn't appear in the TTS
        // engine list when TalkBack initialized during system boot, so we want the change to be
        // invisible to the user.
        pipeline.onBoot(/* quiet= */ true);
    }

    public void onUnlockedBootCompleted() {
        // Update TTS and allow announcement.
        // If the TTS changes here, it is probably a non-FBE TTS that is available after the user
        // unlocks their phone. In this case, the user heard Google TTS at the lock screen, so we
        // should let them know that we're using their preferred TTS now.
        if (pipeline != null) {
            // pipeline can be null if a Boot Complete event arrives in between invocations of onCreated
            // and onServiceConnected.
            pipeline.onBoot(/* quiet= */ false);
        }

        if (labelManager != null) {
            labelManager.ensureLabelsLoaded();
        }

        // The invocation of installActivity() enables immediate access to code and resources of split
        // APKs. It can be invoked even though we are a Service and not an Activity.
        // Call SplitCompat.install after local filesystem accessible in boot process.
        boolean splitCompatInstallSuccess = SplitCompatUtils.installActivity(this);

        // In theory, the boolean returned by installActivity will be false only for API 20 or lower.
        if (!splitCompatInstallSuccess) {
            Log.e(TAG, "SplitCompatUtils.installActivity() failed");
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
        } catch (Exception e) {
            // Do nothing.
        } finally {
            if (systemUeh != null
                    && getServiceState() != ServiceStateListener.SERVICE_STATE_SHUTTING_DOWN) {
                systemUeh.uncaughtException(thread, ex);
            } else {
                // There is either no default exception handler available, or the service is in the middle
                // of shutting down.
                // If the service is in the middle of shutting down exceptions would cause the service to
                // crash.
                LogUtils.e(TAG, "Received exception while shutting down.", ex);
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

    /**
     * Runnable to run after announcing "TalkBack off".
     */
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

    /**
     * Watches the proximity sensor, and silences speech when it's triggered.
     */
    public class ProximitySensorListener {
        /**
         * Proximity sensor for implementing "shut up" functionality.
         */
        private @Nullable ProximitySensor proximitySensor;

        private TalkBackService service;

        /**
         * Whether to use the proximity sensor to silence speech.
         */
        private boolean silenceOnProximity;

        /**
         * Whether or not the screen is on. This is set by RingerModeAndScreenMonitor and used by
         * SpeechControllerImpl to determine if the ProximitySensor should be on or off.
         */
        private boolean screenIsOn;

        public ProximitySensorListener(TalkBackService service) {
            this.service = service;
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
         *                otherwise.
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

    protected boolean shouldUseTalkbackGestureDetection() {
        // TODO: Control the feature by p/h flag.
        if (useServiceGestureDetection == null) {
            SharedPreferences sharedPreferences = SharedPreferencesUtils.getSharedPreferences(this);
            useServiceGestureDetection =
                    sharedPreferences.getBoolean(
                            getString(R.string.pref_talkback_gesture_detection_key), true);
        }
        return useServiceGestureDetection;
    }

    // INFO: TalkBack For Developers modification
    public void performGesture(String gestureString) {
        Performance perf = Performance.getInstance();
        EventId eventId = perf.onEventReceived(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_UNKNOWN));
        gestureController.performAction(gestureString, eventId);
    }

    public void moveAtGranularity(SelectorController.Granularity granularity, boolean isNext) {
        Performance perf = Performance.getInstance();
        EventId eventId = perf.onEventReceived(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_UNKNOWN));
        selectorController.moveAtGranularity(eventId, granularity, isNext);
    }

    /**
     * The build needs to be on 33 to work. However the registration of the
     * [TouchInteractionController] instance will prevent single touch to focus from working.
     * This is not the touch exploration, that will work fine.
     *
     * I am not sure what benefit this listener brings, and I am reluctant to remove it, but since
     * it's only Tiramisu builds and most of what I need works fine - I will just do this in the
     * meantime. This little "bug" has cost me at least three weeks of work, including weekends
     *
     * It is odd to me that this is not registered if the tutorial is shown
     *
     * @return if the [TouchInteractionController] registration breaks TalkBack
     */
    private boolean hasOneDisplay() {
        List<Display> displays = WindowUtils.getAllDisplays(getApplicationContext());
        return displays.size() == 1;
    }
    // ------------------------------------------

    private void registerGestureDetection() {
        if (hasOneDisplay()) return;
        if (BuildVersionUtils.isAtLeastT()) {
            List<Display> displays = WindowUtils.getAllDisplays(getApplicationContext());
            Executor gestureExecutor = Executors.newSingleThreadExecutor();
            for (Display display : displays) {
                Context context = createDisplayContext(display);
                @Nullable TouchInteractionController touchInteractionController =
                        getTouchInteractionController(display.getDisplayId());
                if (touchInteractionController == null) {
                    continue;
                }
                TouchInteractionMonitor touchInteractionMonitor =
                        null;
                touchInteractionMonitor = new TouchInteractionMonitor(context, touchInteractionController, this);
                touchInteractionMonitor.setMultiFingerGesturesEnabled(true);
                touchInteractionMonitor.setTwoFingerPassthroughEnabled(true);
                touchInteractionMonitor.setServiceHandlesDoubleTap(true);
                touchInteractionController.registerCallback(gestureExecutor, touchInteractionMonitor);
                displayIdToTouchInteractionMonitor.put(display.getDisplayId(), touchInteractionMonitor);
                LogUtils.i(TAG, "Enabling service gesture detection on display %d", display.getDisplayId());
            }
        }
    }

    private void unregisterGestureDetection() {
        if (hasOneDisplay()) return;
        if (BuildVersionUtils.isAtLeastT()) {
            List<Display> displays = WindowUtils.getAllDisplays(getApplicationContext());
            for (Display display : displays) {
                @Nullable TouchInteractionController touchInteractionController =
                        getTouchInteractionController(display.getDisplayId());
                TouchInteractionMonitor touchInteractionMonitor =
                        displayIdToTouchInteractionMonitor.get(display.getDisplayId());
                if (touchInteractionController == null || touchInteractionMonitor == null) {
                    continue;
                }
                touchInteractionController.unregisterCallback(touchInteractionMonitor);
            }
            displayIdToTouchInteractionMonitor.clear();
        }
    }
}
