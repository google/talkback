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

import static android.accessibilityservice.AccessibilityServiceInfo.FLAG_SERVICE_HANDLES_DOUBLE_TAP;
import static com.google.android.accessibility.talkback.Feedback.Focus.Action.RENEW_ENSURE_FOCUS;
import static com.google.android.accessibility.talkback.Feedback.PassThroughMode.Action.DISABLE_PASSTHROUGH;
import static com.google.android.accessibility.talkback.Feedback.Speech.Action.INVALIDATE_FREQUENT_CONTENT_CHANGE_CACHE;
import static com.google.android.accessibility.talkback.TalkBackExitController.TalkBackMistriggeringRecoveryType.TYPE_TALKBACK_EXIT_BANNER;
import static com.google.android.accessibility.talkback.analytics.TalkBackAnalytics.GESTURE_SPLIT_TAP;
import static com.google.android.accessibility.talkback.compositor.roledescription.RoleDescriptionExtractor.DESC_ORDER_NAME_ROLE_STATE_POSITION;
import static com.google.android.accessibility.talkback.compositor.roledescription.RoleDescriptionExtractor.DESC_ORDER_ROLE_NAME_STATE_POSITION;
import static com.google.android.accessibility.talkback.compositor.roledescription.RoleDescriptionExtractor.DESC_ORDER_STATE_NAME_ROLE_POSITION;
import static com.google.android.accessibility.talkback.dynamicfeature.ModuleDownloadPrompter.Requester.ONBOARDING;
import static com.google.android.accessibility.talkback.focusmanagement.FocusProcessorForTapAndTouchExploration.FORCE_LIFT_TO_TYPE_ON_IME;
import static com.google.android.accessibility.talkback.ipc.IpcService.EXTRA_IS_ANY_GESTURE_CHANGED;
import static com.google.android.accessibility.talkback.ipc.IpcService.EXTRA_IS_ICON_DETECTION_UNAVAILABLE;
import static com.google.android.accessibility.talkback.ipc.IpcService.EXTRA_IS_IMAGE_DESCRIPTION_UNAVAILABLE;
import static com.google.android.accessibility.talkback.permission.PermissionRequestActivity.PERMISSIONS;
import static com.google.android.accessibility.talkback.trainingcommon.PageConfig.PageId.PAGE_ID_FINISHED;
import static com.google.android.accessibility.talkback.trainingcommon.PageConfig.PageId.PAGE_ID_UNKNOWN;
import static com.google.android.accessibility.talkback.trainingcommon.PageConfig.PageId.PAGE_ID_UPDATE_WELCOME;
import static com.google.android.accessibility.talkback.trainingcommon.PageConfig.PageId.PAGE_ID_WELCOME_TO_TALKBACK;
import static com.google.android.accessibility.talkback.trainingcommon.PageConfig.UNKNOWN_PAGE_INDEX;
import static com.google.android.accessibility.utils.Performance.EVENT_ID_UNTRACKED;
import static com.google.android.accessibility.utils.caption.ImageCaptionUtils.CaptionType.ICON_LABEL;
import static com.google.android.accessibility.utils.caption.ImageCaptionUtils.CaptionType.IMAGE_DESCRIPTION;
import static com.google.android.accessibility.utils.gestures.GestureManifold.GESTURE_FAKED_SPLIT_TYPING;
import static com.google.android.accessibility.utils.output.SpeechControllerImpl.CAPITAL_LETTERS_TYPE_SPEAK_CAP;
import static java.util.Arrays.stream;

import android.Manifest.permission;
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
import android.media.AudioManager;
import android.os.Build;
import android.os.Build.VERSION;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.provider.Settings;
import android.speech.tts.TextToSpeech;
import android.speech.tts.Voice;
import android.text.TextUtils;
import android.util.Log;
import android.view.Display;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import androidx.annotation.VisibleForTesting;
import androidx.core.content.ContextCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import com.google.android.accessibility.braille.brailledisplay.BrailleDisplay;
import com.google.android.accessibility.braille.interfaces.BrailleImeForTalkBack;
import com.google.android.accessibility.braille.interfaces.ScreenReaderActionPerformer;
import com.google.android.accessibility.braille.interfaces.TalkBackForBrailleIme;
import com.google.android.accessibility.braille.interfaces.TalkBackForBrailleIme.BrailleImeForTalkBackProvider;
import com.google.android.accessibility.brailleime.BrailleIme;
import com.google.android.accessibility.talkback.Feedback.DeviceInfo.Action;
import com.google.android.accessibility.talkback.PrimesController.TimerAction;
import com.google.android.accessibility.talkback.TalkBackExitController.TrainingState;
import com.google.android.accessibility.talkback.actor.AutoScrollActor;
import com.google.android.accessibility.talkback.actor.DimScreenActor;
import com.google.android.accessibility.talkback.actor.DimScreenActor.DimScreenNotifier;
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
import com.google.android.accessibility.talkback.actor.TypoNavigator;
import com.google.android.accessibility.talkback.actor.VolumeAdjustor;
import com.google.android.accessibility.talkback.actor.search.SearchScreenNodeStrategy;
import com.google.android.accessibility.talkback.actor.search.UniversalSearchActor;
import com.google.android.accessibility.talkback.actor.search.UniversalSearchManager;
import com.google.android.accessibility.talkback.actor.voicecommands.SpeechRecognizerActor;
import com.google.android.accessibility.talkback.actor.voicecommands.VoiceCommandProcessor;
import com.google.android.accessibility.talkback.adb.AdbReceiver;
import com.google.android.accessibility.talkback.braille.BrailleHelper;
import com.google.android.accessibility.talkback.braille.TalkBackForBrailleDisplayImpl;
import com.google.android.accessibility.talkback.braille.TalkBackForBrailleImeImpl;
import com.google.android.accessibility.talkback.braille.TalkBackForBrailleImeImpl.TalkBackPrivateMethodProvider;
import com.google.android.accessibility.talkback.compositor.Compositor;
import com.google.android.accessibility.talkback.compositor.CompositorUtils;
import com.google.android.accessibility.talkback.compositor.EventFilter;
import com.google.android.accessibility.talkback.compositor.GlobalVariables;
import com.google.android.accessibility.talkback.compositor.roledescription.RoleDescriptionExtractor.DescriptionOrder;
import com.google.android.accessibility.talkback.contextmenu.ListMenuManager;
import com.google.android.accessibility.talkback.controller.TelevisionNavigationController;
import com.google.android.accessibility.talkback.eventprocessor.AccessibilityEventProcessor;
import com.google.android.accessibility.talkback.eventprocessor.AccessibilityEventProcessor.TalkBackListener;
import com.google.android.accessibility.talkback.eventprocessor.ProcessLivingEvent;
import com.google.android.accessibility.talkback.eventprocessor.ProcessorCursorState;
import com.google.android.accessibility.talkback.eventprocessor.ProcessorEventQueue;
import com.google.android.accessibility.talkback.eventprocessor.ProcessorGestureVibrator;
import com.google.android.accessibility.talkback.eventprocessor.ProcessorMagnification;
import com.google.android.accessibility.talkback.eventprocessor.ProcessorPhoneticLetters;
import com.google.android.accessibility.talkback.eventprocessor.ProcessorVolumeStream;
import com.google.android.accessibility.talkback.eventprocessor.ProcessorVolumeStream.TouchInteractingIndicator;
import com.google.android.accessibility.talkback.feedbackpolicy.ScreenFeedbackManager;
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
import com.google.android.accessibility.talkback.interpreters.HintEventInterpreter;
import com.google.android.accessibility.talkback.interpreters.InputFocusInterpreter;
import com.google.android.accessibility.talkback.interpreters.ManualScrollInterpreter;
import com.google.android.accessibility.talkback.interpreters.PassThroughModeInterpreter;
import com.google.android.accessibility.talkback.interpreters.ScrollPositionInterpreter;
import com.google.android.accessibility.talkback.interpreters.StateChangeEventInterpreter;
import com.google.android.accessibility.talkback.interpreters.SubtreeChangeEventInterpreter;
import com.google.android.accessibility.talkback.interpreters.UiChangeEventInterpreter;
import com.google.android.accessibility.talkback.ipc.IpcService;
import com.google.android.accessibility.talkback.ipc.IpcService.IpcClientCallback;
import com.google.android.accessibility.talkback.ipc.IpcService.ServerOnDestroyListener;
import com.google.android.accessibility.talkback.keyboard.KeyComboManager;
import com.google.android.accessibility.talkback.labeling.CustomLabelManager;
import com.google.android.accessibility.talkback.labeling.StoragelessLabelManager;
import com.google.android.accessibility.talkback.labeling.TalkBackLabelManager;
import com.google.android.accessibility.talkback.logging.EventLatencyLogger;
import com.google.android.accessibility.talkback.menurules.NodeMenuRuleCreator;
import com.google.android.accessibility.talkback.menurules.NodeMenuRuleProcessor;
import com.google.android.accessibility.talkback.overlay.DevInfoOverlayController;
import com.google.android.accessibility.talkback.monitor.BatteryMonitor;
import com.google.android.accessibility.talkback.monitor.CallStateMonitor;
import com.google.android.accessibility.talkback.monitor.InputMethodMonitor;
import com.google.android.accessibility.talkback.preference.PreferencesActivityUtils;
import com.google.android.accessibility.talkback.selector.SelectorController;
import com.google.android.accessibility.talkback.selector.SelectorController.SelectorEventNotifier;
import com.google.android.accessibility.talkback.speech.SpeakPasswordsManager;
import com.google.android.accessibility.talkback.training.OnboardingInitiator;
import com.google.android.accessibility.talkback.training.TutorialInitiator;
import com.google.android.accessibility.talkback.trainingcommon.PageConfig;
import com.google.android.accessibility.talkback.trainingcommon.PageConfig.PageId;
import com.google.android.accessibility.talkback.utils.DiagnosticOverlayControllerImpl;
import com.google.android.accessibility.talkback.utils.ExperimentalUtils;
import com.google.android.accessibility.talkback.utils.FocusIndicatorUtils;
import com.google.android.accessibility.talkback.utils.NotificationUtils;
import com.google.android.accessibility.talkback.utils.SplitCompatUtils;
import com.google.android.accessibility.talkback.utils.VerbosityPreferences;
import com.google.android.accessibility.utils.AccessibilityEventListener;
import com.google.android.accessibility.utils.AccessibilityEventUtils;
import com.google.android.accessibility.utils.AccessibilityNodeInfoUtils;
import com.google.android.accessibility.utils.BuildConfig;
import com.google.android.accessibility.utils.BuildVersionUtils;
import com.google.android.accessibility.utils.FeatureSupport;
import com.google.android.accessibility.utils.FocusFinder;
import com.google.android.accessibility.utils.FormFactorUtils;
import com.google.android.accessibility.utils.ImageContents;
import com.google.android.accessibility.utils.Logger;
import com.google.android.accessibility.utils.PackageManagerUtils;
import com.google.android.accessibility.utils.Performance;
import com.google.android.accessibility.utils.Performance.EventId;
import com.google.android.accessibility.utils.Performance.StageId;
import com.google.android.accessibility.utils.Performance.Statistics;
import com.google.android.accessibility.utils.ProximitySensor;
import com.google.android.accessibility.utils.ServiceKeyEventListener;
import com.google.android.accessibility.utils.ServiceStateListener;
import com.google.android.accessibility.utils.SettingsUtils;
import com.google.android.accessibility.utils.SharedPreferencesUtils;
import com.google.android.accessibility.utils.TreeDebug;
import com.google.android.accessibility.utils.WindowUtils;
import com.google.android.accessibility.utils.caption.ImageCaptionStorage;
import com.google.android.accessibility.utils.caption.ImageCaptionUtils.CaptionType;
import com.google.android.accessibility.utils.input.PreferenceProvider;
import com.google.android.accessibility.utils.input.ScrollEventInterpreter;
import com.google.android.accessibility.utils.input.SelectionEventInterpreter;
import com.google.android.accessibility.utils.input.TextCursorTracker;
import com.google.android.accessibility.utils.input.TextEventFilter;
import com.google.android.accessibility.utils.input.TextEventHistory;
import com.google.android.accessibility.utils.input.TextEventInterpreter;
import com.google.android.accessibility.utils.input.WindowEventInterpreter;
import com.google.android.accessibility.utils.material.A11yAlertDialogWrapper;
import com.google.android.accessibility.utils.monitor.AudioPlaybackMonitor;
import com.google.android.accessibility.utils.monitor.CollectionState;
import com.google.android.accessibility.utils.monitor.DisplayMonitor;
import com.google.android.accessibility.utils.monitor.HeadphoneStateMonitor;
import com.google.android.accessibility.utils.monitor.InputModeTracker;
import com.google.android.accessibility.utils.monitor.SpeechStateMonitor;
import com.google.android.accessibility.utils.monitor.TouchMonitor;
import com.google.android.accessibility.utils.output.ActorStateProvider;
import com.google.android.accessibility.utils.output.EditTextActionHistory;
import com.google.android.accessibility.utils.output.FeedbackController;
import com.google.android.accessibility.utils.output.FeedbackProcessingUtils;
import com.google.android.accessibility.utils.output.ScrollActionRecord;
import com.google.android.accessibility.utils.output.SelectionStateReader;
import com.google.android.accessibility.utils.output.SpeechController;
import com.google.android.accessibility.utils.output.SpeechController.UtteranceCompleteRunnable;
import com.google.android.accessibility.utils.output.SpeechControllerImpl;
import com.google.android.accessibility.utils.output.SpeechControllerImpl.CapitalLetterHandlingMethod;
import com.google.android.libraries.accessibility.utils.concurrent.HandlerExecutor;
import com.google.android.libraries.accessibility.utils.log.LogUtils;
import com.google.common.collect.ImmutableMap;
import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.lang.Thread.UncaughtExceptionHandler;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

/** An {@link AccessibilityService} that provides spoken, haptic, and audible feedback. */
public class TalkBackService extends AccessibilityService
    implements Thread.UncaughtExceptionHandler, SpeechController.Delegate {

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
  // ----------------- TB4D -------------------

  private static class IpcClientCallbackImpl
      implements IpcService.IpcClientCallback, TrainingState {

    public boolean hasTrainingPageSwitched;
    private final TalkBackService talkBackService;
    private ServerOnDestroyListener serverOnDestroyListener;

    private PageId currentPageId = PAGE_ID_UNKNOWN;
    private long clientDisconnectedTimeStamp = -1;

    /** Training is recent active within the specified IPC disconnected timeout. */
    private static final int TRAINING_ACTIVE_DISCONNECTED_TIMEOUT_MS = 1000;

    IpcClientCallbackImpl(TalkBackService talkBackService) {
      this.talkBackService = talkBackService;
    }

    public void notifyServerOnDestroyIfNecessary() {
      if (serverOnDestroyListener != null) {
        serverOnDestroyListener.onServerDestroy();
      }
    }

    public void clearServerOnDestroyListener() {
      serverOnDestroyListener = null;
    }

    @Override
    public PageId getCurrentPageId() {
      return currentPageId;
    }

    private void setCurrentPageId(PageId pageId) {
      currentPageId = pageId;
    }

    @Override
    public void onClientConnected(ServerOnDestroyListener serverOnDestroyListener) {
      this.serverOnDestroyListener = serverOnDestroyListener;
      clientDisconnectedTimeStamp = 0;
    }

    @Override
    public void onClientDisconnected() {
      clearServerOnDestroyListener();
      clientDisconnectedTimeStamp = System.currentTimeMillis();
    }

    @Override
    public boolean isTrainingRecentActive() {
      return serverOnDestroyListener != null
          || (clientDisconnectedTimeStamp > 0
              && (System.currentTimeMillis() - clientDisconnectedTimeStamp)
                  < TRAINING_ACTIVE_DISCONNECTED_TIMEOUT_MS);
    }

    @Override
    public Bundle onRequestGesture(Context context) {
      GestureShortcutMapping mapping = new GestureShortcutMapping(context);
      HashMap<String, String> actionKeyToGestureText = mapping.getAllGestureTexts();

      Bundle data = new Bundle();
      actionKeyToGestureText.forEach(data::putString);
      data.putBoolean(EXTRA_IS_ANY_GESTURE_CHANGED, GestureController.isAnyGestureChanged(context));
      return data;
    }

    @Override
    public void onPageSwitched(PageId pageId) {
      @Nullable GestureController gestureController = talkBackService.gestureController;
      if (gestureController != null) {
        @Nullable PageConfig pageConfig =
            PageConfig.getPage(
                pageId, /* context= */ talkBackService, /* vendorPageIndex= */ UNKNOWN_PAGE_INDEX);
        if (pageConfig == null) {
          gestureController.setCaptureGestureIdToAnnouncements(
              /* captureGestureIdToAnnouncements= */ ImmutableMap.of(),
              /* captureFingerprintGestureIdToAnnouncements= */ ImmutableMap.of());
        } else {
          gestureController.setCaptureGestureIdToAnnouncements(
              pageConfig.getCaptureGestureIdToAnnouncements(),
              pageConfig.getCaptureFingerprintGestureIdToAnnouncements());
        }
      }

      // Store the current non-finished page ID.
      if (pageId != PAGE_ID_FINISHED) {
        // If training page has been switched by user, it means the user may be TalkBack user.
        if (currentPageId != PAGE_ID_UNKNOWN && pageId != currentPageId) {
          hasTrainingPageSwitched = true;
        }
        setCurrentPageId(pageId);
      }

      if (pageId == PAGE_ID_WELCOME_TO_TALKBACK || pageId == PAGE_ID_UPDATE_WELCOME) {
        talkBackService.registerTalkBackExitEventListener();
      } else {
        talkBackService.unregisterTalkBackExitEventListener();
      }
    }

    @Override
    public void onTrainingFinish() {
      talkBackService.setTrainingFinished(true);

      // Request permissions after TalkBack tutorial is finished.
      if (NotificationUtils.hasPostNotificationPermission(talkBackService)) {
        talkBackService.helper.flushPendingNotification();
      } else {
        // Post notification permission.
        NotificationUtils.requestPostNotificationPermissionIfNeeded(
            talkBackService,
            new BroadcastReceiver() {
              @Override
              public void onReceive(Context context, Intent intent) {
                String[] permissions = intent.getStringArrayExtra(PERMISSIONS);
                boolean requestPostNotificationPermission =
                    stream(permissions)
                        .anyMatch(p -> TextUtils.equals(p, permission.POST_NOTIFICATIONS));
                if (requestPostNotificationPermission) {
                  context.unregisterReceiver(this);
                  // Even if a user declines the notification permission and we still need to make
                  // notification for some change in talkback upgrade, we will ask permission again.
                  talkBackService.helper.flushPendingNotification();
                }
              }
            });
      }
      // Phone permission.
      @Nullable CallStateMonitor callStateMonitor = talkBackService.callStateMonitor;
      @Nullable SharedPreferences prefs = talkBackService.prefs;
      if (callStateMonitor != null && prefs != null) {
        callStateMonitor.requestPhonePermissionIfNeeded(prefs);
      }
    }

    @Override
    public void onRequestDisableTalkBack() {
      talkBackService.requestDisableTalkBack(TYPE_TALKBACK_EXIT_BANNER.ordinal());
    }

    @Override
    public Bundle onRequestDynamicFeatureState(Context context) {
      Bundle data = new Bundle();
      data.putBoolean(
          EXTRA_IS_ICON_DETECTION_UNAVAILABLE,
          talkBackService.getImageCaptioner().needDownloadDialog(ICON_LABEL, ONBOARDING));
      data.putBoolean(
          EXTRA_IS_IMAGE_DESCRIPTION_UNAVAILABLE,
          talkBackService.getImageCaptioner().needDownloadDialog(IMAGE_DESCRIPTION, ONBOARDING));
      return data;
    }

    @Override
    public void onRequestDownloadLibrary(CaptionType type) {
      talkBackService.imageCaptioner.showDownloadDialogOrAnnounceState(type, ONBOARDING);
    }
  }

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

  /** Interface for asking service flags to an {@link AccessibilityService}. */
  public interface ServiceFlagRequester {
    /**
     * Attempts to change the service info flag.
     *
     * @param flag to specify the service flag to change.
     * @param requestedState {@code true} to request the service flag, or {@code false} to disable
     *     the flag from the service.
     */
    void requestFlag(int flag, boolean requestedState);
  }

  /**
   * Check whether gesture detection is enabled in service side.
   *
   * @return true for Android T and 'handle gesture detection' is on.
   */
  public interface GestureDetectionState {
    boolean gestureDetector();
  }

  /** Whether the user has seen the TalkBack tutorial. */
  public static final String PREF_FIRST_TIME_USER = "first_time_user";

  /** Whether TalkBack training has been exited by user's request. */
  public static final String PREF_HAS_TRAINING_FINISHED = "has_training_exit";

  /** Intent to open text-to-speech settings. */
  public static final String INTENT_TTS_SETTINGS = "com.android.settings.TTS_SETTINGS";

  /** Intent to open text-to-speech settings. */
  public static final String INTENT_TTS_TV_SETTINGS = "android.settings.TTS_SETTINGS";

  /** Default interactive UI timeout in milliseconds. */
  public static final int DEFAULT_INTERACTIVE_UI_TIMEOUT_MILLIS = 10000;

  /** Timeout to turn off TalkBack without waiting for callback from TTS. */
  private static final long TURN_OFF_TIMEOUT_MS = 5000;

  private static final long TURN_OFF_WAIT_PERIOD_MS = 1000;

  /** An active instance of TalkBack. */
  private static volatile @Nullable TalkBackService instance = null;

  /* Call setAnimationScale with this value will disable animation. */
  private static final float ANIMATION_OFF = 0;

  private static final String TAG = "TalkBackService";

  private static final boolean IS_DEBUG_BUILD =
      "eng".equals(Build.TYPE) || "userdebug".equals(Build.TYPE);

  /**
   * List of key event processors. Processors in the list are sent the event in the order they were
   * added until a processor consumes the event.
   */
  private final List<ServiceKeyEventListener> keyEventListeners = new ArrayList<>();

  /** The current state of the service. */
  private volatile int serviceState;

  /** Components to receive callbacks on changes in the service's state. */
  private List<ServiceStateListener> serviceStateListeners = new ArrayList<>();

  /** Controller for speech feedback. */
  private SpeechControllerImpl speechController;

  /** Controller for diagnostic overlay (developer mode). */
  private DiagnosticOverlayControllerImpl diagnosticOverlayController;

  /** TB4D Overlay **/
    private DevInfoOverlayController devInfoOverlayController;
  /** Staged pipeline for separating interpreters, feedback-mappers, and actors. */
  private Pipeline pipeline;

  /** Controller for audio and haptic feedback. */
  private FeedbackController feedbackController;

  /** Watches the proximity sensor, and silences feedback when triggered. */
  private ProximitySensorListener proximitySensorListener;

  private PassThroughModeActor passThroughModeActor;
  private CollectionState collectionState;
  private GlobalVariables globalVariables;
  private EventFilter eventFilter;
  private TextEventInterpreter textEventInterpreter;
  private Compositor compositor;
  private DirectionNavigationActor.StateReader directionNavigationActorStateReader;
  private FullScreenReadActor fullScreenReadActor;
  private EditTextActionHistory editTextActionHistory;

  /** Interface for monitoring current and previous cursor position in editable node */
  private TextCursorTracker textCursorTracker;

  /** Monitors the call state for the phone device. */
  private CallStateMonitor callStateMonitor;

  /** Monitors voice actions from other applications */
  private VoiceActionMonitor voiceActionMonitor;

  /** Monitors speech actions from other applications */
  private SpeechStateMonitor speechStateMonitor;

  /** Maintains cursor state during explore-by-touch by working around EBT problems. */
  private ProcessorCursorState processorCursorState;

  /** Controller for manage keyboard commands */
  private KeyComboManager keyComboManager;

  /** Manager for showing radial menus. */
  private ListMenuManager menuManager;

  /** Manager for detecting missing labels and handling custom labels. */
  private TalkBackLabelManager labelManager;

  /** Manager for the screen search feature. */
  private UniversalSearchManager universalSearchManager;

  /** Orientation monitor for watching orientation changes. */
  private DeviceConfigurationMonitor deviceConfigurationMonitor;

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

  /** Shared preferences used within TalkBack. */
  private SharedPreferences prefs;

  /** The system's uncaught exception handler */
  private UncaughtExceptionHandler systemUeh;

  /** The system feature if the device supports touch screen */
  private boolean supportsTouchScreen = true;

  /** Feature flag from P/H experimentation framework, for using service gesture detection. */
  private boolean gestureDetectionFeatureFlag = true;

  /** Whether the current root node is dirty or not. */
  private boolean isRootNodeDirty = true;

  /** Keep Track of current root node. */
  private AccessibilityNodeInfo rootNode;

  private AccessibilityEventProcessor accessibilityEventProcessor;

  /** Interprets subtree-change event, and sends interpretations to the pipeline. */
  private SubtreeChangeEventInterpreter subtreeChangeEventInterpreter;

  /** Keeps track of whether we need to run the locked-boot-completed callback when connected. */
  private boolean lockedBootCompletedPending;

  private final InputModeTracker inputModeTracker = new InputModeTracker();
  private WindowEventInterpreter windowEventInterpreter;
  private ScreenFeedbackManager processorScreen;
  private @Nullable ProcessorMagnification processorMagnification;
  private final DisableTalkBackCompleteAction disableTalkBackCompleteAction =
      new DisableTalkBackCompleteAction();
  private SpeakPasswordsManager speakPasswordsManager;
  private final FormFactorUtils formFactorUtils = FormFactorUtils.getInstance();

  // Focus logic
  private AccessibilityFocusMonitor accessibilityFocusMonitor;
  private AccessibilityFocusInterpreter accessibilityFocusInterpreter;
  private FocusActor focuser;
  private FocusFinder focusFinder;
  private InputFocusInterpreter inputFocusInterpreter;
  private ScrollPositionInterpreter scrollPositionInterpreter;
  private ScreenStateMonitor screenStateMonitor;
  private InputMethodMonitor inputMethodMonitor;
  private DisplayMonitor displayMonitor;
  private ProcessorEventQueue processorEventQueue;
  private ProcessorPhoneticLetters processorPhoneticLetters;

  private BrailleDisplay brailleDisplay;
  private BrailleImeForTalkBackProvider brailleImeForTalkBackProvider;

  private GestureShortcutMapping gestureShortcutMapping;
  private NodeMenuRuleProcessor nodeMenuRuleProcessor;
  private PrimesController primesController;
  private SpeechLanguage speechLanguage;
  private ImageCaptioner imageCaptioner;
  private TalkBackExitController talkBackExitController;

  private @Nullable Boolean useServiceGestureDetection;
  private LanguageActor languageActor;
  // In general, volume key should work as pass through mode, unless the touch interaction is
  // ongoing. isTouchInteracting denotes the occurrence of key event is in the time window between
  // TYPE_TOUCH_INTERACTION_START and TYPE_TOUCH_INTERACTION_END will be considered as passthrough
  // window.
  private boolean isTouchInteracting = false;
  // In order to handle key action down/up in pair for the same functions.
  // Records whether the last keystroke of VolumeUp key occurred in the passthrough window.
  private boolean volumeUpKeyPressedInPassThroughWindow = false;
  // Records whether the last keystroke of VolumeDown key occurred in the passthrough window.
  private boolean volumeDownKeyPressedInPassThroughWindow = false;
  private TouchInteractionMonitor mainTouchInteractionMonitor;

  private final @NonNull Map<Integer, TouchInteractionMonitor> displayIdToTouchInteractionMonitor =
      new HashMap<>();

  private IpcClientCallbackImpl ipcClientCallback;
  private BootReceiver bootReceiver;

  /** A helper to smoothly migrate from old version to the latest version for TalkBack. */
  private TalkBackUpdateHelper helper;

  private EventLatencyLogger eventLatencyLogger;

  @Override
  public void onCreate() {
    bootReceiver = new BootReceiver();
    ContextCompat.registerReceiver(this, bootReceiver, BootReceiver.getFilter(), RECEIVER_EXPORTED);
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
    interruptAllFeedback(/* stopTtsSpeechCompletely= */ false);
    storeTalkBackUserUsage();
    if (pipeline != null) {
      pipeline.onUnbind(calculateFinalAnnouncementVolume(), disableTalkBackCompleteAction);
        interruptAllFeedback(false /* stopTtsSpeechCompletely */); // TB4D
    }
    if (gestureShortcutMapping != null) {
      gestureShortcutMapping.onUnbind();
    }
    if (ringerModeAndScreenMonitor != null) {
      ringerModeAndScreenMonitor.stopMonitoring(this);
      ringerModeAndScreenMonitor.clearListeners();
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
    if (prefs != null) { // Protect from early unbind case which the preference is not yet created.
      enableAnimation(/* enable= */ true);
    }
    return false;
  }

  @Override
  public void onDestroy() {
      // INFO: TalkBack For Developers modification
      AdbReceiver.unregisterAdbReceiver(this);
      // ------------------------------------------
    if (eventLatencyLogger != null) {
      eventLatencyLogger.destroy();
    }

    if (shouldUseTalkbackGestureDetection()) {
      unregisterGestureDetection();
    }

    if (bootReceiver != null) {
      unregisterReceiver(bootReceiver);
      bootReceiver = null;
    }

    if (passThroughModeActor != null) {
      passThroughModeActor.onDestroy();
    }

    if (displayMonitor != null) {
      displayMonitor.clearListeners();
    }

    if (ipcClientCallback != null) {
      ipcClientCallback.notifyServerOnDestroyIfNecessary();
      ipcClientCallback.clearServerOnDestroyListener();
    }

    super.onDestroy();

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

    // When TalkBack off, also disable the option of TalkBack gesture detection if the flag from the
    // P/H experimental framework is off. So next time when the user turns on TalkBack, it will not
    // use the gesture detection.
    if (!gestureDetectionFeatureFlag) {
      prefs
          .edit()
          .putBoolean(getString(R.string.pref_talkback_gesture_detection_key), false)
          .apply();
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

    if (isServiceActive() && (deviceConfigurationMonitor != null)) {
      deviceConfigurationMonitor.onConfigurationChanged(newConfig);
    }

    if (gestureShortcutMapping != null) {
      gestureShortcutMapping.onConfigurationChanged(newConfig);
    }

    if (keyComboManager != null) {
      keyComboManager.onConfigurationChanged(newConfig);
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
    int eventType = event.getEventType();
    if (eventType == AccessibilityEvent.TYPE_TOUCH_INTERACTION_START) {
      // TODO: Could move the logic of TOUCH_INTERACTION related event handling out of
      // TalkBackService, and concentrated in a dedicated module such as ?
      isTouchInteracting = true;
      pipeline
          .getFeedbackReturner()
          .returnFeedback(EVENT_ID_UNTRACKED, Feedback.focus(RENEW_ENSURE_FOCUS));
      pipeline
          .getFeedbackReturner()
          .returnFeedback(
              Performance.EVENT_ID_UNTRACKED,
              Feedback.speech(INVALIDATE_FREQUENT_CONTENT_CHANGE_CACHE));
    } else if (eventType == AccessibilityEvent.TYPE_TOUCH_INTERACTION_END) {
      isTouchInteracting = false;
    }

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
    // TODO: Quintin: Evaluate
//        if (devOverlayController != null) {
//            devOverlayController.displayFeedback(event);
//        }
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

  /** Stops all delayed events in the service. */
  public void clearQueues() {
    interruptAllFeedback(/* stopTtsSpeechCompletely= */ false);
    processorEventQueue.clearQueue();
    if (windowEventInterpreter != null) {
      windowEventInterpreter.clearQueue();
    }
    // TODO: Clear queues wherever there are message handlers that delay event processing.
  }

  private boolean shouldInterruptByAnyKeyEvent() {
    return !fullScreenReadActor.isActive();
  }

  /**
   * Wrapper around {@link #onKeyEventInternal} that measures the latency.
   *
   * <p>Subclasses can override {@link #onKeyEventInternal} instead of this.
   */
  @Override
  protected final boolean onKeyEvent(KeyEvent keyEvent) {
    boolean result = onKeyEventInternal(keyEvent);

    if (primesController != null) {
      // We use keyEvent.getEventTime() as starting point because we don't know how long the
      // message was enqueued before onKeyEvent() has started.
      primesController.recordDuration(
          TimerAction.KEY_EVENT, keyEvent.getEventTime(), SystemClock.uptimeMillis());
    }

    return result;
  }

  /** Handles a key event and returns whether it should be considered consumed. */
  protected boolean onKeyEventInternal(KeyEvent keyEvent) {
    int keyCode = keyEvent.getKeyCode();
    int keyAction = keyEvent.getAction();

    if (keyCode == KeyEvent.KEYCODE_UNKNOWN) {
      // Tapping on fingerprint sensor somehow files KeyEvent with KEYCODE_UNKNOWN, which will
      // change input mode to keyboard, and cancel pending accessibility hints. It is OK to just
      // ignore these KeyEvents since they're unused in TalkBack.
      return false;
    }
    boolean passThroughThisKey = false;
    if (keyAction == KeyEvent.ACTION_DOWN) {
      switch (keyCode) {
        case KeyEvent.KEYCODE_VOLUME_DOWN:
          passThroughThisKey = !isTouchInteracting && !isBrailleImeTouchInteracting();
          volumeDownKeyPressedInPassThroughWindow = passThroughThisKey;
          break;
        case KeyEvent.KEYCODE_VOLUME_UP:
          passThroughThisKey = !isTouchInteracting && !isBrailleImeTouchInteracting();
          volumeUpKeyPressedInPassThroughWindow = passThroughThisKey;
          break;
        default:
          break;
      }
    } else { // KeyEvent.ACTION_UP
      switch (keyCode) {
        case KeyEvent.KEYCODE_VOLUME_DOWN:
          passThroughThisKey = volumeDownKeyPressedInPassThroughWindow;
          volumeDownKeyPressedInPassThroughWindow = false;
          break;
        case KeyEvent.KEYCODE_VOLUME_UP:
          passThroughThisKey = volumeUpKeyPressedInPassThroughWindow;
          volumeUpKeyPressedInPassThroughWindow = false;
          break;
        default:
          break;
      }
    }
    if (passThroughThisKey) {
      return false;
    }

    if (keyAction == KeyEvent.ACTION_DOWN) {
      textEventInterpreter.setLastKeyEventTime(keyEvent.getEventTime());
    }
    Performance perf = Performance.getInstance();
    EventId eventId = perf.onEventReceived(keyEvent);

    if (isServiceActive()) {
      // Stop the TTS engine when any key (except for volume up/down key) is pressed on physical
      // keyboard.
      if (shouldInterruptByAnyKeyEvent()
          && keyEvent.getDeviceId() != 0
          && keyAction == KeyEvent.ACTION_DOWN
          && keyCode != KeyEvent.KEYCODE_VOLUME_DOWN
          && keyCode != KeyEvent.KEYCODE_VOLUME_UP) {
        interruptAllFeedback(/* stopTtsSpeechCompletely= */ false);
      }
    }

    // Pass KeyEvents to Mappers, un-consumed.
    if (pipeline != null) {
      pipeline.getInterpretationReceiver().input(eventId, new Interpretation.Key(keyEvent));
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

  private boolean isBrailleImeTouchInteracting() {
    return getBrailleImeForTalkBack() != null && getBrailleImeForTalkBack().isTouchInteracting();
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

  /** Called by {@link TouchInteractionMonitor} when gesture detection started. */
  public void onGestureDetectionStarted() {
    if (processorPhoneticLetters != null) {
      processorPhoneticLetters.cancelPhoneticLetter(EVENT_ID_UNTRACKED);
    }
  }

  private boolean handleOnGestureById(int gestureId) {
    if (!isServiceActive()) {
      return false;
    }
    Performance perf = Performance.getInstance();
    EventId eventId = perf.onGestureEventReceived(gestureId);
    primesController.startTimer(TimerAction.GESTURE_EVENT);

    switch (gestureId) {
      case GESTURE_FAKED_SPLIT_TYPING:
        analytics.onGesture(GESTURE_SPLIT_TAP);
        break;
      case GESTURE_DOUBLE_TAP:
      case GESTURE_DOUBLE_TAP_AND_HOLD:
        // Double-tap/Double-tap-and-hold are not necessary to count here.
        break;
      default:
        analytics.onGesture(gestureId);
    }

    if (gestureShortcutMapping.isSupportedGesture(gestureId)) {
      getFeedbackController().playAuditory(R.raw.gesture_end, eventId);
    }

    gestureController.onGesture(gestureId, eventId);
    if (gestureId == GESTURE_FAKED_SPLIT_TYPING && mainTouchInteractionMonitor != null) {
      mainTouchInteractionMonitor.requestTouchExploration();
    }

    // Measure latency.
    // Preceding event handling frequently initiates a framework action, which in turn
    // cascades a focus event, which in turn generates feedback.
    perf.onHandlerDone(eventId);
    primesController.stopTimer(TimerAction.GESTURE_EVENT);
    return true;
  }

  public GestureController getGestureController() {
    if (gestureController == null) {
      throw new IllegalStateException("mGestureController has not been initialized");
    }

    return gestureController;
  }

  // TODO: As controller logic moves to pipeline, delete this function.
  public SpeechControllerImpl getSpeechController() {
    if (speechController == null) {
      throw new IllegalStateException("mSpeechController has not been initialized");
    }

    return speechController;
  }

  public FeedbackController getFeedbackController() {
    if (feedbackController == null) {
      throw new IllegalStateException("mFeedbackController has not been initialized");
    }

    return feedbackController;
  }

  public VoiceActionMonitor getVoiceActionMonitor() {
    if (voiceActionMonitor == null) {
      throw new IllegalStateException("mVoiceActionMonitor has not been initialized");
    }

    return voiceActionMonitor;
  }

  public KeyComboManager getKeyComboManager() {
    return keyComboManager;
  }

  public TalkBackLabelManager getLabelManager() {
    if (labelManager == null) {
      throw new IllegalStateException("mLabelManager has not been initialized");
    }

    return labelManager;
  }

  public TalkBackAnalyticsImpl getAnalytics() {
    if (analytics == null) {
      throw new IllegalStateException("mAnalytics has not been initialized");
    }

    return analytics;
  }

  @VisibleForTesting
  public ImageCaptioner getImageCaptioner() {
    if (imageCaptioner == null) {
      throw new IllegalArgumentException("imageCaptioner has not been initialized");
    }
    return imageCaptioner;
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
  IpcClientCallback getIpcClientCallback() {
    return ipcClientCallback;
  }

  /**
   * Registers the dialog to {@link RingerModeAndScreenMonitor} for screen monitor and {@link
   * DeviceConfigurationMonitor} for device orientation..
   */
  public void registerDialog(DialogInterface dialog, boolean hasEditText) {
    if (ringerModeAndScreenMonitor != null) {
      ringerModeAndScreenMonitor.registerDialog(dialog);
    }
    if (deviceConfigurationMonitor != null
        && hasEditText
        && dialog instanceof A11yAlertDialogWrapper) {
      deviceConfigurationMonitor.setDialogWithEditText((A11yAlertDialogWrapper) dialog);
    }
  }

  /**
   * Unregisters the dialog from {@link RingerModeAndScreenMonitor} for screen monitor {@link
   * DeviceConfigurationMonitor} for device orientation.
   */
  public void unregisterDialog(DialogInterface dialog) {
    if (ringerModeAndScreenMonitor != null) {
      ringerModeAndScreenMonitor.unregisterDialog(dialog);
    }
    if (deviceConfigurationMonitor != null) {
      deviceConfigurationMonitor.setDialogWithEditText(null);
    }
  }

  @Override
  public void onInterrupt() {
    interruptAllFeedback(/* stopTtsSpeechCompletely= */ false);
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

  private @Nullable Locale localeByName(String localeName) {
    @Nullable Set<Voice> voices = speechController.getVoices();
    if (localeName == null || voices == null) {
      return null;
    }
    Optional<Voice> result =
        voices.stream()
            .filter(
                voice -> {
                  Set<String> features = voice.getFeatures();
                  return ((features != null)
                      && !features.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED)
                      && !voice.isNetworkConnectionRequired()
                      && localeName.equals(voice.getLocale().getDisplayName()));
                })
            .findFirst();

    return result.map(Voice::getLocale).orElse(null);
  }

  @Override
  public void onTtsReady() {
    @Nullable String localeName =
        SharedPreferencesUtils.getStringPref(
            prefs, getResources(), R.string.pref_talkback_prefer_locale_key, 0);
    compositor.setUserPreferredLanguage(localeByName(localeName));
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
    primesController.startTimer(TimerAction.START_UP);

    SharedPreferencesUtils.migrateSharedPreferences(this);
    prefs = SharedPreferencesUtils.getSharedPreferences(this);

    if (FeatureFlagReader.logEventBasedLatency(getBaseContext())) {
      eventLatencyLogger = new EventLatencyLogger(primesController, getApplicationContext(), prefs);
      eventLatencyLogger.init();
    }

    if (FeatureFlagReader.usePeriodAsSeparator(getBaseContext())) {
      CompositorUtils.usePeriodAsSeparator();
    }

    initializeInfrastructure();

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
    helper = new TalkBackUpdateHelper(this);
    helper.checkUpdate();

    compositor.handleEvent(Compositor.EVENT_SPOKEN_FEEDBACK_ON, EVENT_ID_UNTRACKED);

    // If the locked-boot-completed intent was fired before onServiceConnected, we queued it,
    // so now we need to run it.
    if (lockedBootCompletedPending) {
      onLockedBootCompletedInternal();
      lockedBootCompletedPending = false;
    }

    boolean shouldShowTutorial = shouldShowTutorial();

    if (shouldShowTutorial) {
      // Ignore Onboarding for the first-time user.
      if (isFirstTimeUser()) {
        OnboardingInitiator.markAllOnboardingAsShown(this);
      }
      if (skipShowingTutorialInLaunching()) {
        // Jump to training finished state if RRO overlays the skip tutorial in launching TalkBack
        // stage.
        ipcClientCallback.onTrainingFinish();
      } else {
        // The method of requestPhonePermissionIfNeeded, which is triggered after the tutorial is
        // finished, is dependent on hasOnboardingForNewFeaturesBeenShown, so we move it at the rear
        // of markAllOnboardingAsShown.
        showTutorial();
      }
    } else {
      // We don't need to show the tutorial so we can directly notify the changes.
      helper.flushPendingNotification();
      OnboardingInitiator.showOnboardingIfNecessary(this);
    }

    if (shouldShowTutorial || formFactorUtils.isAndroidTv()) {
      setFirstTimeUser(false);
    }

    updateTalkBackEnabledCount();

    // Service gesture detection.
    if (shouldUseTalkbackGestureDetection()) {
      registerGestureDetection();
    }

    primesController.stopTimer(TimerAction.START_UP);
  }

  /**
   * ReturnsThe current state of the TalkBack service, or {@code INACTIVE} if the service is not
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

  /** Returns{@code true} if TalkBack is running and initialized, {@code false} otherwise. */
  public static boolean isServiceActive() {
    return (getServiceState() == ServiceStateListener.SERVICE_STATE_ACTIVE);
  }

  /** Returns the active TalkBack instance, or {@code null} if not available. */
  public static @Nullable TalkBackService getInstance() {
    return instance;
  }

  /** Initialize {@link FingerprintGestureCallback} for detecting fingerprint gestures. */
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
    displayMonitor = new DisplayMonitor(this);
    accessibilityEventProcessor = new AccessibilityEventProcessor(this, displayMonitor);
    feedbackController = new FeedbackController(this);
    speechController =
        new SpeechControllerImpl(
            this, this, feedbackController, FeatureFlagReader.removeUnnecessarySpans(this));
    if (FeatureFlagReader.enableAggressiveChunking(this)) {
      FeedbackProcessingUtils.enableAggressiveChunking();
    }
    speechStateMonitor = new SpeechStateMonitor();
    diagnosticOverlayController = new DiagnosticOverlayControllerImpl(this);
      devInfoOverlayController = new DevInfoOverlayController(this);

    gestureShortcutMapping = new GestureShortcutMapping(this);

    collectionState = new CollectionState();
    globalVariables =
        new GlobalVariables(this, inputModeTracker, collectionState, gestureShortcutMapping);

    labelManager =
        formFactorUtils.isAndroidTv()
            ? new StoragelessLabelManager()
            : new CustomLabelManager(this);
    addEventListener(labelManager);

    ImageCaptionStorage imageCaptionStorage = new ImageCaptionStorage();
    ImageContents imageContents =
        ImageCaptioner.supportsImageCaption(this)
            ? new ImageContents(labelManager, imageCaptionStorage)
            : new ImageContents(labelManager, /* imageCaptionStorage= */ null);

    processorPhoneticLetters = new ProcessorPhoneticLetters(this, globalVariables);

    compositor =
        new Compositor(
            this,
            /* speechController= */ null,
            imageContents,
            globalVariables,
            processorPhoneticLetters,
            getCompositorFlavor());
    // TODO: Make pipeline run Compositor, which returns speech feedback, no callback.

    analytics = new TalkBackAnalyticsImpl(this);

    focusFinder = new FocusFinder(this);

    // Construct system-monitors.
    batteryMonitor = new BatteryMonitor();
    callStateMonitor = new CallStateMonitor(this);
    inputMethodMonitor = new InputMethodMonitor(this);
    audioPlaybackMonitor = new AudioPlaybackMonitor(this);
    @NonNull TouchMonitor touchMonitor = new TouchMonitor();

    // Construct event-interpreters.
    screenStateMonitor = new ScreenStateMonitor(/* service= */ this, inputMethodMonitor);
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
    AutoScrollInterpreter autoScrollInterpreter = new AutoScrollInterpreter();

    imageCaptioner =
        new ImageCaptioner(
            this, imageCaptionStorage, accessibilityFocusMonitor, analytics, primesController);

    // TODO: ScreenState should be passed through pipeline.
    focuser =
        new FocusActor(
            this,
            focusFinder,
            screenStateMonitor.state,
            focusHistory,
            accessibilityFocusMonitor,
            this::shouldUseTalkbackGestureDetection);

    UniversalSearchActor universalSearchActor =
        new UniversalSearchActor(this, screenStateMonitor.state, focusFinder, labelManager);

    autoScrollInterpreter.setUniversalSearchActor(universalSearchActor);

    DirectionNavigationActor directionNavigationActor =
        new DirectionNavigationActor(
            inputModeTracker,
            globalVariables,
            analytics,
            this,
            focusFinder,
            processorPhoneticLetters,
            accessibilityFocusMonitor,
            screenStateMonitor.state,
            universalSearchActor.state);
    directionNavigationActorStateReader = directionNavigationActor.state;
    TextEditActor editor =
        new TextEditActor(
            this,
            editTextActionHistory,
            textCursorTracker,
            directionNavigationActorStateReader,
            getSystemService(ClipboardManager.class));
    fullScreenReadActor =
        new FullScreenReadActor(
            accessibilityFocusMonitor, this, speechController, screenStateMonitor.state);
    dimScreenController = new DimScreenActor(this, gestureShortcutMapping, dimScreenNotifier);

    accessibilityFocusInterpreter =
        new AccessibilityFocusInterpreter(
            this, accessibilityFocusMonitor, screenStateMonitor.state, analytics);

    inputFocusInterpreter =
        new InputFocusInterpreter(accessibilityFocusInterpreter, focusFinder, globalVariables);

    proximitySensorListener = new ProximitySensorListener(/* service= */ this);
    speechLanguage = new SpeechLanguage();

    DirectionNavigationInterpreter directionNavigationInterpreter =
        new DirectionNavigationInterpreter(this);
    HintEventInterpreter hintEventInterpreter = new HintEventInterpreter();

    passThroughModeActor = new PassThroughModeActor(this);

    voiceCommandProcessor =
        new VoiceCommandProcessor(this, accessibilityFocusMonitor, selectorController, analytics);
    speechRecognizer = new SpeechRecognizerActor(this, voiceCommandProcessor, analytics);
    UiChangeEventInterpreter uiChangeEventInterpreter = new UiChangeEventInterpreter();
    addEventListener(uiChangeEventInterpreter);

    UserInterface userInterface = new UserInterface();
    subtreeChangeEventInterpreter =
        new SubtreeChangeEventInterpreter(screenStateMonitor.state, displayMonitor);

    languageActor = new LanguageActor(this, speechLanguage);
    // Construct pipeline.
    pipeline =
        new Pipeline(
            this,
            new Monitors(
                batteryMonitor,
                callStateMonitor,
                touchMonitor,
                speechStateMonitor,
                collectionState),
            new Interpreters(
                inputFocusInterpreter,
                scrollEventInterpreter,
                manualScrollInterpreter,
                autoScrollInterpreter,
                scrollPositionInterpreter,
                new SelectionEventInterpreter(),
                accessibilityFocusInterpreter,
                fullScreenReadInterpreter,
                new StateChangeEventInterpreter(),
                directionNavigationInterpreter,
                hintEventInterpreter,
                voiceCommandProcessor,
                new PassThroughModeInterpreter(),
                subtreeChangeEventInterpreter,
                new AccessibilityEventIdleInterpreter(),
                uiChangeEventInterpreter),
            new Mappers(this, compositor, focusFinder),
            new Actors(
                this,
                analytics,
                accessibilityFocusMonitor,
                dimScreenController,
                speechController,
                fullScreenReadActor,
                feedbackController,
                scroller,
                focuser,
                new FocusActorForScreenStateChange(
                    this, inputMethodMonitor, focusFinder, primesController),
                new FocusActorForTapAndTouchExploration(),
                directionNavigationActor,
                new SearchScreenNodeStrategy(/* observer= */ null, labelManager),
                editor,
                labelManager,
                new NodeActionPerformer(),
                new SystemActionPerformer(this),
                languageActor,
                passThroughModeActor,
                new TalkBackUIActor(this),
                new SpeechRateActor(this),
                new NumberAdjustor(this, accessibilityFocusMonitor),
                new TypoNavigator(this, editor, accessibilityFocusMonitor),
                new VolumeAdjustor(this),
                speechRecognizer,
                new GestureReporter(this, new GestureHistory()),
                imageCaptioner,
                universalSearchActor,
                this::requestServiceFlag,
                () -> brailleDisplay.switchBrailleDisplayOnOrOff()),
            proximitySensorListener,
            speechController,
            diagnosticOverlayController,
            devInfoOverlayController,
            compositor,
            userInterface);

    voiceCommandProcessor.setActorState(pipeline.getActorState());
    voiceCommandProcessor.setPipeline(pipeline.getFeedbackReturner());

    accessibilityEventProcessor.setActorState(pipeline.getActorState());
    accessibilityEventProcessor.setAccessibilityEventIdleListener(pipeline);

    autoScrollInterpreter.setDirectionNavigationActor(directionNavigationActor);

    // TalkBack menu and Reading Controls.
    NodeMenuRuleCreator nodeMenuCreator =
        new NodeMenuRuleCreator(
            pipeline.getFeedbackReturner(),
            pipeline.getActorState(),
            accessibilityFocusMonitor,
            analytics);
    nodeMenuRuleProcessor = new NodeMenuRuleProcessor(this, nodeMenuCreator);
    globalVariables.setNodeMenuProvider(nodeMenuRuleProcessor);

    menuManager =
        new ListMenuManager(
            this,
            pipeline.getFeedbackReturner(),
            pipeline.getActorState(),
            accessibilityFocusMonitor,
            nodeMenuRuleProcessor,
            analytics);
    voiceCommandProcessor.setListMenuManager(menuManager);

    selectorController =
        new SelectorController(
            this,
            pipeline.getFeedbackReturner(),
            pipeline.getActorState(),
            accessibilityFocusMonitor,
            nodeMenuCreator,
            analytics,
            gestureShortcutMapping,
            compositor.getTextComposer(),
            selectorEventNotifier);
    userInterface.registerListener(selectorController);
    voiceCommandProcessor.setSelectorController(selectorController);
    globalVariables.setSelectorController(selectorController);

    compositor.setSpeaker(pipeline.getSpeaker());

    TouchExplorationInterpreter touchExplorationInterpreter =
        new TouchExplorationInterpreter(inputModeTracker);

    if (FeatureSupport.supportMagnificationController()) {
      processorMagnification =
          new ProcessorMagnification(
              getMagnificationController(),
              globalVariables,
              compositor,
              analytics,
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

    keyEventListeners.add(inputModeTracker);

    keyComboManager =
        new KeyComboManager(
            this,
            pipeline.getFeedbackReturner(),
            pipeline.getActorState(),
            selectorController,
            menuManager,
            fullScreenReadActor,
            analytics,
            directionNavigationActorStateReader);

    globalVariables.setKeyComboManager(keyComboManager);

    ringerModeAndScreenMonitor =
        new RingerModeAndScreenMonitor(
            menuManager,
            pipeline.getFeedbackReturner(),
            proximitySensorListener,
            callStateMonitor,
            displayMonitor,
            this);
    accessibilityEventProcessor.setRingerModeAndScreenMonitor(ringerModeAndScreenMonitor);

    headphoneStateMonitor = new HeadphoneStateMonitor(this);
    speakPasswordsManager = new SpeakPasswordsManager(this, headphoneStateMonitor, globalVariables);

    ProcessorVolumeStream processorVolumeStream =
        new ProcessorVolumeStream(pipeline.getActorState(), this, touchInteractingIndicator);
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
            accessibilityFocusInterpreter,
            gestureShortcutMapping,
            analytics);

    audioPlaybackMonitor = new AudioPlaybackMonitor(this);

    // Add event processors. These will process incoming AccessibilityEvents
    // in the order they are added.
    eventFilter = new EventFilter(compositor, touchMonitor, globalVariables);
    eventFilter.setVoiceActionDelegate(voiceActionMonitor);
    eventFilter.setAccessibilityFocusEventInterpreter(accessibilityFocusInterpreter);
    ActorStateProvider actorStateProvider =
        new ActorStateProvider() {
          @Override
          public boolean resettingNodeCursor() {
            return globalVariables.resettingNodeCursor();
          }

          @Override
          public @Nullable ScrollActionRecord scrollState() {
            return pipeline.getActorState().getScrollerState().get();
          }

          @Override
          public @NonNull SelectionStateReader selectionState() {
            return directionNavigationActor.state;
          }

          @Override
          public EditTextActionHistory.@NonNull Provider editHistory() {
            return editTextActionHistory.provider;
          }
        };
    PreferenceProvider preferenceProvider =
        new PreferenceProvider() {
          @Override
          public boolean shouldSpeakPasswords() {
            return globalVariables.shouldSpeakPasswords();
          }
        };
    final TextEventHistory textEventHistory = new TextEventHistory();
    final TextEventFilter textEventFilter =
        new TextEventFilter(this, textCursorTracker, textEventHistory);
    textEventInterpreter =
        new TextEventInterpreter(
            this,
            textCursorTracker,
            inputModeTracker,
            textEventHistory,
            actorStateProvider,
            preferenceProvider,
            voiceActionMonitor,
            textEventFilter);
    // Event-interpreters are chained: textEventInterpreter -> hintEventInterpreter
    textEventInterpreter.addListener(hintEventInterpreter);
    processorEventQueue = new ProcessorEventQueue(eventFilter, textEventInterpreter);

    addEventListener(processorEventQueue);
    addEventListener(processorPhoneticLetters);

    // Create window event interpreter and announcer.
    windowEventInterpreter = new WindowEventInterpreter(this, displayMonitor);
    processorScreen =
        new ScreenFeedbackManager(
            this,
            windowEventInterpreter,
            compositor.getTextComposer(),
            keyComboManager,
            focusFinder,
            gestureShortcutMapping,
            pipeline.getFeedbackReturner(),
            isScreenOrientationLandscape());
    globalVariables.setWindowsDelegate(windowEventInterpreter);
    screenStateMonitor.setWindowsDelegate(windowEventInterpreter);
    addEventListener(processorScreen);

    // Monitor window transition status by registering listeners.
    if (windowEventInterpreter != null) {
      windowEventInterpreter.addListener(menuManager);
      windowEventInterpreter.addListener(screenStateMonitor);
      windowEventInterpreter.addListener(uiChangeEventInterpreter);
      windowEventInterpreter.addListener(imageCaptioner);
    }

    processorCursorState = new ProcessorCursorState(this, pipeline.getFeedbackReturner());

    volumeMonitor = new VolumeMonitor(pipeline.getFeedbackReturner(), this, callStateMonitor);

    addEventListener(new ProcessorGestureVibrator(pipeline.getFeedbackReturner()));

    addEventListener(new ProcessLivingEvent(analytics));

    universalSearchManager =
        new UniversalSearchManager(
            pipeline.getFeedbackReturner(), ringerModeAndScreenMonitor, windowEventInterpreter);

    keyEventListeners.add(keyComboManager);
    serviceStateListeners.add(keyComboManager);

    deviceConfigurationMonitor = new DeviceConfigurationMonitor(compositor, this);
    deviceConfigurationMonitor.addConfigurationChangedListener(dimScreenController);

    KeyboardLockMonitor keyboardLockMonitor = new KeyboardLockMonitor(compositor);
    keyEventListeners.add(keyboardLockMonitor);

    ipcClientCallback = new IpcClientCallbackImpl(this);

    if (!hasTrainingFinishedByUser()) {
      talkBackExitController = new TalkBackExitController(TalkBackService.getInstance());
      if (FeatureFlagReader.allowAutomaticTurnOff(this)) {
        talkBackExitController.setActorState(pipeline.getActorState());
        talkBackExitController.setTrainingState(ipcClientCallback);
        ringerModeAndScreenMonitor.addScreenChangedListener(talkBackExitController);
      }
    }

    if (Build.VERSION.SDK_INT >= TelevisionNavigationController.MIN_API_LEVEL
        && formFactorUtils.isAndroidTv()) {
      televisionNavigationController =
          new TelevisionNavigationController(
              /* service= */ this,
              accessibilityFocusMonitor,
              inputMethodMonitor,
              primesController,
              menuManager,
              pipeline.getFeedbackReturner(),
              TvNavigation.useHandlerThread(/* context= */ this));
      keyEventListeners.add(televisionNavigationController);
      televisionDPadManager = new TelevisionDPadManager(televisionNavigationController, this);
      addEventListener(televisionDPadManager);
      onTelevisionNavigationControllerInitialized(televisionNavigationController);
    }

    ScreenReaderActionPerformer screenReaderActionPerformer =
        new BrailleHelper(
            this,
            pipeline.getFeedbackReturner(),
            pipeline.getActorState(),
            menuManager,
            selectorController,
            focusFinder);
    brailleDisplay =
        new BrailleDisplay(
            this,
            new TalkBackForBrailleDisplayImpl(
                this, pipeline.getFeedbackReturner(), screenReaderActionPerformer),
            () ->
                getBrailleImeForTalkBack() == null
                    ? null
                    : getBrailleImeForTalkBack().getBrailleImeForBrailleDisplay());

    TalkBackForBrailleIme talkBackForBrailleIme =
        new TalkBackForBrailleImeImpl(
            this,
            pipeline.getFeedbackReturner(),
            focusFinder,
            dimScreenController,
            directionNavigationActorStateReader,
            proximitySensorListener,
            new TalkBackPrivateMethodProvider() {
              @Override
              public void requestTouchExploration(boolean enabled) {
                getInstance().requestTouchExploration(enabled);
              }

              @Override
              public GlobalVariables getGlobalVariables() {
                return globalVariables;
              }
            },
            screenReaderActionPerformer,
            selectorController);
    brailleImeForTalkBackProvider = talkBackForBrailleIme.getBrailleImeForTalkBackProvider();

    BrailleIme.initialize(this, talkBackForBrailleIme, brailleDisplay);
    analytics.onTalkBackServiceStarted();

    TalkbackServiceStateNotifier.getInstance().notifyTalkBackServiceStateChanged(true);
  }

  /** Callback that is invoked after a {@link TelevisionNavigationController} has been set up. */
  protected void onTelevisionNavigationControllerInitialized(
      TelevisionNavigationController televisionNavigationController) {}

  @VisibleForTesting
  public WindowEventInterpreter getWindowEventInterpreter() {
    return windowEventInterpreter;
  }

  private final TouchInteractingIndicator touchInteractingIndicator =
      new TouchInteractingIndicator() {
        @Override
        public boolean isTouchInteracting() {
          return isBrailleImeTouchInteracting();
        }
      };

  private final SelectorController.SelectorEventNotifier selectorEventNotifier =
      new SelectorEventNotifier() {
        @Override
        public void onSelectorOverlayShown(CharSequence message) {
          if (brailleDisplay != null) {
            brailleDisplay.onReadingControlChanged(message);
          }
        }
      };

  private final DimScreenNotifier dimScreenNotifier =
      new DimScreenNotifier() {
        @Override
        public void onScreenDim() {
          if (getBrailleImeForTalkBack() != null) {
            getBrailleImeForTalkBack().onScreenDim();
          }
        }

        @Override
        public void onScreenBright() {
          if (getBrailleImeForTalkBack() != null) {
            getBrailleImeForTalkBack().onScreenBright();
          }
        }
      };

  private BrailleImeForTalkBack getBrailleImeForTalkBack() {
    return brailleImeForTalkBackProvider.getBrailleImeForTalkBack();
  }

  private boolean isBrailleKeyboardActivated() {
    return getBrailleImeForTalkBack() == null
        ? false
        : getBrailleImeForTalkBack().isBrailleKeyboardActivated();
  }

  @Compositor.Flavor
  public int getCompositorFlavor() {
    if (formFactorUtils.isAndroidTv()) {
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
    if (locale == null) {
      prefs.edit().remove(getString(R.string.pref_talkback_prefer_locale_key)).apply();
    } else {
      prefs
          .edit()
          .putString(getString(R.string.pref_talkback_prefer_locale_key), locale.getDisplayName())
          .apply();
    }
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
      // If we need to show the tutorial, we will ask permission after completing it.
      if (!shouldShowTutorial()) {
        callStateMonitor.requestPhonePermissionIfNeeded(prefs);
      }
      callStateMonitor.startMonitoring();
    }

    // If we need to show the tutorial, we will ask permission after completing it.
    if (!shouldShowTutorial()) {
      NotificationUtils.requestPostNotificationPermissionIfNeeded(this);
    }

    if (voiceActionMonitor != null) {
      voiceActionMonitor.onResumeInfrastructure();
    }

    if (inputMethodMonitor != null) {
      inputMethodMonitor.onResumeInfrastructure();
    }

    if (audioPlaybackMonitor != null) {
      audioPlaybackMonitor.onResumeInfrastructure();
    }

    if (displayMonitor != null) {
      displayMonitor.startMonitoring();
    }

    if (accessibilityEventProcessor != null) {
      accessibilityEventProcessor.onResumeInfrastructure();
    }

    if (subtreeChangeEventInterpreter != null) {
      subtreeChangeEventInterpreter.onResumeInfrastructure();
    }

    if (windowEventInterpreter != null) {
      windowEventInterpreter.onResumeInfrastructure();
    }

    if (ringerModeAndScreenMonitor != null) {
      ringerModeAndScreenMonitor.startMonitoring(this);
    }

    if (headphoneStateMonitor != null) {
      headphoneStateMonitor.startMonitoring();
    }

    if (volumeMonitor != null) {
      ContextCompat.registerReceiver(
          this, volumeMonitor, volumeMonitor.getFilter(), RECEIVER_EXPORTED);
      if (FeatureSupport.hasAccessibilityAudioStream(this)) {
        // Cache the initial volume in case that the volume is never changed during runtime.
        volumeMonitor.cacheAccessibilityStreamVolume();
      }
    }

    if (batteryMonitor != null) {
      ContextCompat.registerReceiver(
          this, batteryMonitor, batteryMonitor.getFilter(), RECEIVER_EXPORTED);
    }

    if (labelManager != null) {
      labelManager.onResume(/* context= */ this);
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

    gestureDetectionFeatureFlag = FeatureFlagReader.useTalkbackGestureDetection(this);

    if (getBrailleImeForTalkBack() != null) {
      getBrailleImeForTalkBack().onTalkBackResumed();
    }
    brailleDisplay.start();

    if (eventLatencyLogger != null) {
      Performance.getInstance()
          .addLatencyTracker(eventLatencyLogger, new HandlerExecutor(new Handler(getMainLooper())));
      speechController.getFailoverTts().addListener(eventLatencyLogger);
    }
    IpcService.setClientCallback(ipcClientCallback);
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

    if (displayMonitor != null) {
      displayMonitor.stopMonitoring();
    }

    if (accessibilityEventProcessor != null) {
      accessibilityEventProcessor.onSuspendInfrastructure();
    }

    if (subtreeChangeEventInterpreter != null) {
      subtreeChangeEventInterpreter.onSuspendInfrastructure();
    }

    if (windowEventInterpreter != null) {
      windowEventInterpreter.onSuspendInfrastructure();
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

    if (inputMethodMonitor != null) {
      inputMethodMonitor.onSuspendInfrastructure();
    }

    dimScreenController.suspend();

    interruptAllFeedback(/* stopTtsSpeechCompletely */ false);

    // Some apps depend on these being set to false when TalkBack is disabled.
    if (supportsTouchScreen) {
      requestTouchExploration(false);
    }

    prefs.unregisterOnSharedPreferenceChangeListener(sharedPreferenceChangeListener);
    prefs.unregisterOnSharedPreferenceChangeListener(analytics);

    unregisterReceivers(batteryMonitor, volumeMonitor);

    if (labelManager != null) {
      labelManager.onSuspend(/* context= */ this);
    }

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

    TalkbackServiceStateNotifier.getInstance().notifyTalkBackServiceStateChanged(false);

    if (getBrailleImeForTalkBack() != null) {
      getBrailleImeForTalkBack().onTalkBackSuspended();
    }
    brailleDisplay.stop();
    if (eventLatencyLogger != null) {
      Performance.getInstance().removeLatencyTracker(eventLatencyLogger);
      speechController.getFailoverTts().removeListener(eventLatencyLogger);
    }

    IpcService.setClientCallback(null);
  }

  /** Shuts down the infrastructure in case it has been initialized. */
  private void shutdownInfrastructure() {
    setServiceState(ServiceStateListener.SERVICE_STATE_SHUTTING_DOWN);
    // we put it first to be sure that screen dimming would be removed even if code below
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
    if (keyComboManager != null) {
      keyComboManager.shutdown();
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

  /** Returns a boolean preference by resource id. */
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
   *     animation.
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

  /** Reloads service preferences. */
  protected void reloadPreferences() {
    final Resources res = getResources();

    LogUtils.v(
        TAG,
        "TalkBackService.reloadPreferences() diagnostic mode=%s",
        PreferencesActivityUtils.isDiagnosisModeOn(prefs, res));

    // Preferece to reduce window announcement delay.
    boolean reduceDelayPref =
        getBooleanPref(
            R.string.pref_reduce_window_delay_key, R.bool.pref_reduce_window_delay_default);
    if (windowEventInterpreter != null) {
      windowEventInterpreter.setReduceDelayPref(reduceDelayPref);
      enableAnimation(!reduceDelayPref);
    }

    // If performance statistics changing enabled setting... clear collected stats.
    boolean performanceEnabled =
        getBooleanPref(R.string.pref_performance_stats_key, R.bool.pref_performance_stats_default);
    Performance performance = Performance.getInstance();
    if (performance.getComputeStatsEnabled() != performanceEnabled) {
      performance.clearRecentEvents();
      performance.clearAllStats();
      performance.setComputeStatsEnabled(performanceEnabled);
    }

    boolean logOverlayEnabled =
        PreferencesActivityUtils.getDiagnosticPref(
            prefs, res, R.string.pref_log_overlay_key, R.bool.pref_log_overlay_default);
    diagnosticOverlayController.setLogOverlayEnabled(logOverlayEnabled);

    // INFO: TalkBack For Developers modification
    boolean blockOutEnabled =
            getBooleanPref(R.string.pref_tb4d_block_overlay_key, R.bool.pref_tb4d_overlay_block_default);
    devInfoOverlayController.setOverlayEnabled(blockOutEnabled);
    if (blockOutEnabled && logOverlayEnabled) {
      diagnosticOverlayController.setLogOverlayEnabled(false);
    }
    // ----------------- TB4D -------------------

    accessibilityEventProcessor.setSpeakWhenScreenOff(
        VerbosityPreferences.getPreferenceValueBool(
            prefs,
            res,
            res.getString(R.string.pref_screenoff_key),
            res.getBoolean(R.bool.pref_screenoff_default)));

    accessibilityEventProcessor.setDumpEventMask(
        prefs.getInt(res.getString(R.string.pref_dump_event_mask_key), 0));

    proximitySensorListener.reloadSilenceOnProximity();
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

    if (supportsTouchScreen && !isBrailleKeyboardActivated()) {
      // Touch exploration *must* be enabled on TVs for TalkBack to function.
      final boolean touchExploration =
          (formFactorUtils.isAndroidTv()
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
    int onScreenKeyboardPref = VerbosityPreferences.readOnScreenKeyboardEcho(prefs, getResources());
    textEventInterpreter.setOnScreenKeyboardEcho(onScreenKeyboardPref);

    int physicalKeyboardPref = VerbosityPreferences.readPhysicalKeyboardEcho(prefs, getResources());
    textEventInterpreter.setPhysicalKeyboardEcho(physicalKeyboardPref);

    boolean useAudioFocus =
        getBooleanPref(R.string.pref_use_audio_focus_key, R.bool.pref_use_audio_focus_default);
    pipeline.setUseAudioFocus(useAudioFocus);

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

    // Update preference: time feedback format.
    String timeFeedbackFormat =
        SharedPreferencesUtils.getStringPref(
            prefs,
            res,
            R.string.pref_time_feedback_format_key,
            R.string.pref_time_feedback_format_default);
    ringerModeAndScreenMonitor.setTimeFeedbackFormat(
        RingerModeAndScreenMonitor.prefValueToTimeFeedbackFormat(res, timeFeedbackFormat));

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

      // Update preference: limit frequent content change announcement.
      boolean rateLimitTextChange =
          SharedPreferencesUtils.getBooleanPref(
              prefs,
              res,
              R.string.pref_allow_frequent_content_change_announcement_key,
              R.bool.pref_allow_frequent_content_change_announcement_default);
      globalVariables.setTextChangeRateUnlimited(rateLimitTextChange);

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

  @DescriptionOrder
  private static int prefValueToDescriptionOrder(Resources resources, String value) {
    if (TextUtils.equals(
        value, resources.getString(R.string.pref_node_desc_order_value_role_name_state_pos))) {
      return DESC_ORDER_ROLE_NAME_STATE_POSITION;
    } else if (TextUtils.equals(
        value, resources.getString(R.string.pref_node_desc_order_value_state_name_role_pos))) {
      return DESC_ORDER_STATE_NAME_ROLE_POSITION;
    } else if (TextUtils.equals(
        value, resources.getString(R.string.pref_node_desc_order_value_name_role_state_pos))) {
      return DESC_ORDER_NAME_ROLE_STATE_POSITION;
    } else {
      LogUtils.e(TAG, "Unhandled description order preference value \"%s\"", value);
      return DESC_ORDER_STATE_NAME_ROLE_POSITION;
    }
  }

  /**
   * Attempts to return the state of touch exploration.
   *
   * <p>Should only be called if {@link #supportsTouchScreen} is true.
   *
   * <p>Returns{@code true} if touch exploration is enabled, {@code false} if touch exploration is
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
   * @param flags to specify the service flags to change.
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

    if (((flags & FLAG_SERVICE_HANDLES_DOUBLE_TAP) != 0) && mainTouchInteractionMonitor != null) {
      // Mask off double-tap service flag. When gesture detection's activated, in Android T, change
      // this flag causes the touch interaction controller reset the state.
      flags &= ~FLAG_SERVICE_HANDLES_DOUBLE_TAP;
    }
    if (newValue) {
      info.flags |= flags;
    } else {
      info.flags &= ~flags;
    }

    LogUtils.v(TAG, "Accessibility Service flag changed: 0x%X", info.flags);
    setServiceInfo(info);
    if ((flags & AccessibilityServiceInfo.FLAG_REQUEST_TOUCH_EXPLORATION_MODE)
            == AccessibilityServiceInfo.FLAG_REQUEST_TOUCH_EXPLORATION_MODE
        && newValue
        && shouldUseTalkbackGestureDetection()) {
      // Modifies the explore-by-touch flag will invalidate the gesture detection from the service
      // side. Here is the workaround to re-configure the gesture detection.
      unregisterGestureDetection();
      registerGestureDetection();
    }
  }

  /**
   * Checks the condition for showing tutorial.
   *
   * @return {@code true} if the tutorial should be shown
   */
  public boolean shouldShowTutorial() {
    if (formFactorUtils.isAndroidTv()) {
      return false;
    }

    boolean isDeviceProvisioned =
        Settings.Secure.getInt(getContentResolver(), Settings.Global.DEVICE_PROVISIONED, 1) != 0;

    // Training should show again if the user didn't exit training UI by clicking the exit button.
    if (isDeviceProvisioned && (!isFirstTimeUser() && hasTrainingFinishedByUser())) {
      return false;
    }

    final int touchscreenState = getResources().getConfiguration().touchscreen;

    return touchscreenState != Configuration.TOUCHSCREEN_NOTOUCH && supportsTouchScreen;
  }

  public void showTutorial() {
    startActivity(TutorialInitiator.createFirstRunTutorialIntent(getApplicationContext()));
  }

  private void setFirstTimeUser(boolean newValue) {
    prefs.edit().putBoolean(PREF_FIRST_TIME_USER, newValue).apply();
  }

  private boolean isFirstTimeUser() {
    return prefs.getBoolean(PREF_FIRST_TIME_USER, true);
  }

  void setTrainingFinished(boolean newValue) {
    prefs.edit().putBoolean(PREF_HAS_TRAINING_FINISHED, newValue).apply();
  }

  boolean hasTrainingFinishedByUser() {
    return prefs.getBoolean(PREF_HAS_TRAINING_FINISHED, false);
  }

  private void updateTalkBackEnabledCount() {
    String enabledCountKey = getString(R.string.talkback_enabled_count);
    int enabledCount = prefs.getInt(enabledCountKey, 0) + 1;
    prefs.edit().putInt(enabledCountKey, enabledCount).apply();
  }

  /** Stores TalkBack user usage when service is on unbind. */
  private void storeTalkBackUserUsage() {
    if (ipcClientCallback == null) {
      return;
    }
    if (ipcClientCallback.hasTrainingPageSwitched) {
      prefs.edit().putBoolean(getString(R.string.has_training_page_switched), true);
    }
    prefs
        .edit()
        .putLong(getString(R.string.talkback_off_timestamp), System.currentTimeMillis())
        .apply();
  }

  /**
   * User requests to disable TalkBack. And it will enter tutorial when TalkBack restarts.
   *
   * @param type that turns off TalkBack
   */
  void requestDisableTalkBack(int type) {
    LogUtils.d(TAG, "mis-triggering: requestDisableTalkBack  type=%d", type);
    analytics.sendLogImmediately(type);
    setTrainingFinished(false);
    disableSelf();
  }

  private boolean skipShowingTutorialInLaunching() {
    return getResources().getBoolean(R.bool.skip_tutorial_in_launching);
  }

  /** Reloads preferences whenever their values change. */
  private final OnSharedPreferenceChangeListener sharedPreferenceChangeListener =
      (prefs, key) -> {
        LogUtils.d(TAG, "A shared preference changed: %s", key);
        if (getString(R.string.pref_previous_global_window_animation_scale_key).equals(key)) {
          // The stored animation factor is no related to TalkBack Settings at all. We skip to
          // reloadPreferences to avoid the additional of Talkback re-configuration.
          return;
        }
        reloadPreferences();
      };

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
      labelManager.onUnlockedBoot();
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

  public void onShutDown() {
    if (talkBackExitController != null) {
      talkBackExitController.onShutDown();
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

  public InputModeTracker getInputModeTracker() {
    return inputModeTracker;
  }

  public void registerTalkBackExitEventListener() {
    if (talkBackExitController != null) {
      addEventListener(talkBackExitController);
    }
  }

  public void unregisterTalkBackExitEventListener() {
    if (talkBackExitController != null) {
      postRemoveEventListener(talkBackExitController);
    }
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

  /** Notifier notifies when TalkBack state changed. */
  public static class TalkbackServiceStateNotifier {
    private final Set<TalkBackServiceStateChangeListener> serviceStateChangeListeners;
    private static TalkbackServiceStateNotifier serviceStateChangeNotifier;

    public static TalkbackServiceStateNotifier getInstance() {
      if (serviceStateChangeNotifier == null) {
        serviceStateChangeNotifier = new TalkbackServiceStateNotifier();
      }
      return serviceStateChangeNotifier;
    }

    private TalkbackServiceStateNotifier() {
      serviceStateChangeListeners = ConcurrentHashMap.newKeySet();
    }

    private void notifyTalkBackServiceStateChanged(boolean enabled) {
      for (TalkBackServiceStateChangeListener serviceStateChangeListener :
          serviceStateChangeListeners) {
        serviceStateChangeListener.onServiceStateChange(enabled);
      }
    }

    public void registerTalkBackServiceStateChangeListener(
        TalkBackServiceStateChangeListener listener) {
      serviceStateChangeListeners.add(listener);
    }

    public void unregisterTalkBackServiceStateChangeListener(
        TalkBackServiceStateChangeListener listener) {
      serviceStateChangeListeners.remove(listener);
    }

    /** Notifies TalkBackService state changed events. */
    public interface TalkBackServiceStateChangeListener {
      /** Callbacks when TalkBackService state changed. */
      void onServiceStateChange(boolean isServiceActive);
    }
  }

  /** Watches the proximity sensor, and silences speech when it's triggered. */
  public class ProximitySensorListener {
    /** Proximity sensor for implementing "shut up" functionality. */
    private @Nullable ProximitySensor proximitySensor;

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

    public void reloadSilenceOnProximity() {
      final boolean silenceOnProximity =
          getBooleanPref(R.string.pref_proximity_key, R.bool.pref_proximity_default);
      setSilenceOnProximity(silenceOnProximity);
    }

    public void setProximitySensorStateByScreen() {
      setProximitySensorState(screenIsOn);
    }
  }

  private void resetTouchExplorePassThrough() {
    if (FeatureSupport.supportPassthrough()) {
      if (isBrailleKeyboardActivated()) {
        return;
      }
      pipeline
          .getFeedbackReturner()
          .returnFeedback(
              Performance.EVENT_ID_UNTRACKED, Feedback.passThroughMode(DISABLE_PASSTHROUGH));
    }
  }

  protected boolean shouldUseTalkbackGestureDetection() {
    if (useServiceGestureDetection == null) {
      SharedPreferences sharedPreferences = SharedPreferencesUtils.getSharedPreferences(this);
      useServiceGestureDetection =
          sharedPreferences.getBoolean(
              getString(R.string.pref_talkback_gesture_detection_key),
              getResources().getBoolean(R.bool.pref_talkback_gesture_detection_default));
    }
    return useServiceGestureDetection;
  }

  private void registerGestureDetection() {
    if (FeatureSupport.supportGestureDetection()) {
      AccessibilityServiceInfo info = getServiceInfo();
      if (info != null) {
        // When gesture detection's enabled in the service side, FLAG_SERVICE_HANDLES_DOUBLE_TAP
        // will be set. And it won't be changed during the life time of service. Otherwise the touch
        // interaction controller will be affected.
        info.flags |= FLAG_SERVICE_HANDLES_DOUBLE_TAP;
        setServiceInfo(info);
      }

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
            new TouchInteractionMonitor(context, touchInteractionController, this);
        if (display.getDisplayId() == Display.DEFAULT_DISPLAY) {
          mainTouchInteractionMonitor = touchInteractionMonitor;
        }
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
    if (FeatureSupport.supportGestureDetection()) {
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
      mainTouchInteractionMonitor = null;
    }
  }

  public @Nullable Statistics getPerformanceStatisticsByLabelAndStageId(
      String label, @StageId int stageId) {
    return Performance.getInstance().getStatisticsByLabelAndStageId(label, stageId);
  }

  static final String COMPONENT_BASIC_INFO = "basic_info";
  static final String COMPONENT_GESTURE_MAPPING = "gesture_mapping";
  static final String COMPONENT_NODE_HIERARCHY = "node_hierarchy";
  static final String COMPONENT_PERF_METRICS = "perf_metrics";
  static final String COMPONENT_PERF_METRICS_CLEAR = "clear_perf_metrics";

  @Override
  protected void dump(FileDescriptor fd, PrintWriter writer, String[] args) {
    super.dump(fd, writer, args);
    writer.println(
        "============ Talkback Service Dump: args=" + TextUtils.join(",", args) + " ============");

    Set<String> argsSet = new HashSet<>(Arrays.asList(args));
    Logger dumpLogger = (format, formatArgs) -> writer.println(String.format(format, formatArgs));

    dumpComponentsIfNeeded(dumpLogger, argsSet);
    dumpComponentsWithGivenArgs(dumpLogger, argsSet);
  }

  /**
   * Dumps the components conditionally with given {@code argSet}.
   *
   * @param dumpLogger the logger to print the information
   * @param argsSet additional arguments to the dump request
   */
  private void dumpComponentsIfNeeded(Logger dumpLogger, Set<String> argsSet) {
    if (debugDumpComponentByDefault(dumpLogger, argsSet, COMPONENT_NODE_HIERARCHY)
        && LogUtils.shouldLog(Log.VERBOSE)) {
      dumpLogger.log("Current Node Hierarchy:");
      TreeDebug.logNodeTreesOnAllDisplays(this, dumpLogger);
    }
    if (dumpComponent(argsSet, COMPONENT_BASIC_INFO)) {
      dumpBasicInfo(dumpLogger);
    }
    if (dumpComponent(argsSet, COMPONENT_GESTURE_MAPPING)) {
      dumpGestureMapping(dumpLogger);
    }
  }

  private void dumpGestureMapping(Logger dumpLogger) {
    if (gestureShortcutMapping != null) {
      gestureShortcutMapping.dump(dumpLogger);
    }
  }

  private void dumpBasicInfo(Logger dumpLogger) {
    dumpLogger.log("TalkBackService basic information: ");
    dumpLogger.log("  versionName=" + PackageManagerUtils.getVersionName(this));
    dumpLogger.log("  versionCode=" + PackageManagerUtils.getVersionCode(this));
    dumpLogger.log("  LogUtils.getLogLevel=" + LogUtils.getLogLevel());
    dumpLogger.log("  Build.VERSION.SDK_INT=" + VERSION.SDK_INT);
    dumpLogger.log("  BuildConfig.DEBUG=" + BuildConfig.DEBUG);
    dumpLogger.log("");
  }

  private boolean debugDumpComponentByDefault(
      Logger dumpLogger, Set<String> argsSet, String componentName) {
    final boolean enabled = dumpComponent(argsSet, componentName);
    if (!IS_DEBUG_BUILD && enabled) {
      dumpLogger.log("Can not dump information for <" + componentName + "> in a non-debug type.");
    }
    return IS_DEBUG_BUILD && enabled;
  }

  private boolean dumpComponent(Set<String> argsSet, String componentName) {
    return argsSet == null || argsSet.isEmpty() || argsSet.contains(componentName);
  }

  /**
   * Dumps the components with given {@code argSet} which includes the corresponding name of the
   * component.
   *
   * @param dumpLogger the logger to print the information
   * @param argsSet additional arguments to the dump request
   */
  private void dumpComponentsWithGivenArgs(Logger dumpLogger, Set<String> argsSet) {
    if (argsSet.contains(COMPONENT_PERF_METRICS)) {
      Performance.getInstance().dump(dumpLogger);
    }
    if (argsSet.contains(COMPONENT_PERF_METRICS_CLEAR)) {
      Performance.getInstance().clearAllStatsAndRecords(dumpLogger);
    }
  }
}
