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

import static com.google.android.accessibility.utils.Performance.EVENT_ID_UNTRACKED;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityService.MagnificationController.OnMagnificationChangedListener;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.accessibilityservice.FingerprintGestureController.FingerprintGestureCallback;
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.app.KeyguardManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnDismissListener;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Region;
import android.media.AudioManager;
import android.os.Build;
import android.os.Handler;
import android.provider.Settings;
import android.support.v4.app.NotificationCompat;
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import android.widget.CheckBox;
import android.widget.ScrollView;
import android.widget.TextView;
import com.android.talkback.TalkBackPreferencesActivity;
import com.google.android.accessibility.compositor.Compositor;
import com.google.android.accessibility.compositor.EventFilter;
import com.google.android.accessibility.compositor.GlobalVariables;
import com.google.android.accessibility.talkback.contextmenu.ListMenuManager;
import com.google.android.accessibility.talkback.contextmenu.MenuManager;
import com.google.android.accessibility.talkback.contextmenu.MenuManagerWrapper;
import com.google.android.accessibility.talkback.contextmenu.RadialMenuManager;
import com.google.android.accessibility.talkback.controller.CursorControllerApp;
import com.google.android.accessibility.talkback.controller.DimScreenController;
import com.google.android.accessibility.talkback.controller.DimScreenControllerApp;
import com.google.android.accessibility.talkback.controller.FullScreenReadController;
import com.google.android.accessibility.talkback.controller.FullScreenReadControllerApp;
import com.google.android.accessibility.talkback.controller.GestureController;
import com.google.android.accessibility.talkback.controller.GestureControllerApp;
import com.google.android.accessibility.talkback.controller.SelectorController;
import com.google.android.accessibility.talkback.controller.TelevisionNavigationController;
import com.google.android.accessibility.talkback.eventprocessor.AccessibilityEventProcessor;
import com.google.android.accessibility.talkback.eventprocessor.AccessibilityEventProcessor.TalkBackListener;
import com.google.android.accessibility.talkback.eventprocessor.ProcessorAccessibilityHints;
import com.google.android.accessibility.talkback.eventprocessor.ProcessorCursorState;
import com.google.android.accessibility.talkback.eventprocessor.ProcessorEventQueue;
import com.google.android.accessibility.talkback.eventprocessor.ProcessorFocusAndSingleTap;
import com.google.android.accessibility.talkback.eventprocessor.ProcessorGestureVibrator;
import com.google.android.accessibility.talkback.eventprocessor.ProcessorPermissionDialogs;
import com.google.android.accessibility.talkback.eventprocessor.ProcessorPhoneticLetters;
import com.google.android.accessibility.talkback.eventprocessor.ProcessorScreen;
import com.google.android.accessibility.talkback.eventprocessor.ProcessorScrollPosition;
import com.google.android.accessibility.talkback.eventprocessor.ProcessorVolumeStream;
import com.google.android.accessibility.talkback.features.ProximitySensorListener;
import com.google.android.accessibility.talkback.focusmanagement.AccessibilityFocusManager;
import com.google.android.accessibility.talkback.labeling.CustomLabelManager;
import com.google.android.accessibility.talkback.labeling.PackageRemovalReceiver;
import com.google.android.accessibility.talkback.speech.SpeakPasswordsManager;
import com.google.android.accessibility.talkback.tutorial.AccessibilityTutorialActivity;
import com.google.android.accessibility.talkback.utils.VerbosityPreferences;
import com.google.android.accessibility.utils.AccessibilityEventListener;
import com.google.android.accessibility.utils.AccessibilityNodeInfoUtils;
import com.google.android.accessibility.utils.BuildVersionUtils;
import com.google.android.accessibility.utils.EditTextActionHistory;
import com.google.android.accessibility.utils.FormFactorUtils;
import com.google.android.accessibility.utils.HardwareUtils;
import com.google.android.accessibility.utils.HeadphoneStateMonitor;
import com.google.android.accessibility.utils.LogUtils;
import com.google.android.accessibility.utils.PerformActionUtils;
import com.google.android.accessibility.utils.Performance;
import com.google.android.accessibility.utils.Performance.EventId;
import com.google.android.accessibility.utils.ServiceKeyEventListener;
import com.google.android.accessibility.utils.ServiceStateListener;
import com.google.android.accessibility.utils.SharedKeyEvent;
import com.google.android.accessibility.utils.SharedPreferencesUtils;
import com.google.android.accessibility.utils.input.CursorController;
import com.google.android.accessibility.utils.input.InputModeManager;
import com.google.android.accessibility.utils.input.TextCursorManager;
import com.google.android.accessibility.utils.keyboard.KeyComboManager;
import com.google.android.accessibility.utils.output.FeedbackController;
import com.google.android.accessibility.utils.output.SpeechController;
import com.google.android.accessibility.utils.output.SpeechController.UtteranceCompleteRunnable;
import com.google.android.accessibility.utils.output.SpeechControllerImpl;
import java.lang.Thread.UncaughtExceptionHandler;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;

/** An {@link AccessibilityService} that provides spoken, haptic, and audible feedback. */
public class TalkBackService extends AccessibilityService
    implements Thread.UncaughtExceptionHandler, SpeechController.Delegate, SharedKeyEvent.Listener {

  // Set to TRUE to use experimental focus management feature.
  public static final boolean USE_A11Y_FOCUS_MANAGER = false;

  /** Whether the user has seen the TalkBack tutorial. */
  public static final String PREF_FIRST_TIME_USER = "first_time_user";

  /** Permission required to perform gestures. */
  public static final String PERMISSION_TALKBACK =
      "com.google.android.marvin.feedback.permission.TALKBACK";

  /** The intent action used to perform a custom gesture action. */
  public static final String ACTION_PERFORM_GESTURE_ACTION = "performCustomGestureAction";

  /**
   * The gesture action to pass with {@link #ACTION_PERFORM_GESTURE_ACTION} as a string extra.
   * Expected to be the name of the shortcut pref value, like R.strings.shortcut_value_previous
   */
  public static final String EXTRA_GESTURE_ACTION = "gestureAction";

  /**
   * Key to send extra data to indicate presence of navigation up button with the intent to start
   * the tutorial.
   */
  public static final String EXTRA_TUTORIAL_INTENT_SOURCE =
      "com.google.android.marvin.talkback.tutorialSource";

  public static final String TUTORIAL_SRC = "service";

  /** The intent action used to suspend TalkBack's control over D-pad KeyEvents. */
  public static final String ACTION_SUSPEND_DPAD_CONTROL =
      "com.google.android.marvin.talkback.action.suspendDPadControl";

  /** The intent action used to resume TalkBack's control over D-pad KeyEvents. */
  public static final String ACTION_RESUME_DPAD_CONTROL =
      "com.google.android.marvin.talkback.action.resumeDPadControl";

  /** Intent to open text-to-speech settings. */
  public static final String INTENT_TTS_SETTINGS = "com.android.settings.TTS_SETTINGS";

  /** Action used to resume feedback. */
  private static final String ACTION_RESUME_FEEDBACK =
      "com.google.android.marvin.talkback.RESUME_FEEDBACK";

  /** An active instance of TalkBack. */
  private static TalkBackService sInstance = null;

  private static final String LOGTAG = "TalkBackService";

  /**
   * List of key event processors. Processors in the list are sent the event in the order they were
   * added until a processor consumes the event.
   */
  private final LinkedList<ServiceKeyEventListener> mKeyEventListeners = new LinkedList<>();

  /** The current state of the service. */
  private int mServiceState;

  /** Components to receive callbacks on changes in the service's state. */
  private List<ServiceStateListener> mServiceStateListeners = new LinkedList<>();

  /** Controller for speech feedback. */
  private SpeechControllerImpl mSpeechController;

  /** Controller for audio and haptic feedback. */
  private FeedbackController mFeedbackController;

  /** Watches the proximity sensor, and silences feedback when triggered. */
  private ProximitySensorListener mProximitySensorListener;

  private GlobalVariables mGlobalVariables;
  private EventFilter mEventFilter;
  private Compositor mCompositor;

  /** Controller for reading the entire hierarchy. */
  private FullScreenReadControllerApp mFullScreenReadController;

  private EditTextActionHistory mEditTextActionHistory;

  /** Interface for monitoring current and previous cursor position in editable node */
  private TextCursorManager mTextCursorManager;

  /** Monitors voice actions from other applications */
  private VoiceActionMonitor mVoiceActionMonitor;

  /** Maintains cursor state during explore-by-touch by working around EBT problems. */
  private ProcessorCursorState mProcessorCursorState;

  /** Processor for allowing clicking on buttons in permissions dialogs. */
  private ProcessorPermissionDialogs mProcessorPermissionsDialogs;

  /** Controller for manage keyboard commands */
  private KeyComboManager mKeyComboManager;

  /** Listener for device shake events. */
  private ShakeDetector mShakeDetector;

  /** Manager for side tap events */
  private SideTapManager mSideTapManager;

  /** Manager for showing radial menus. */
  private MenuManagerWrapper mMenuManager;

  /** Manager for handling custom labels. */
  private CustomLabelManager mLabelManager;

  /** Manager for keyboard search. */
  private KeyboardSearchManager mKeyboardSearchManager;

  /** Controller for cursor movement. */
  private CursorController mCursorController;

  /** Processor for moving access focus. */
  private ProcessorFocusAndSingleTap mProcessorFollowFocus;

  private AccessibilityFocusManager mAccessibilityFocusManager;

  /** Orientation monitor for watching orientation changes. */
  private OrientationMonitor mOrientationMonitor;

  /** {@link BroadcastReceiver} for tracking the ringer and screen states. */
  private RingerModeAndScreenMonitor mRingerModeAndScreenMonitor;

  /** {@link BroadcastReceiver} for tracking volume changes. */
  private VolumeMonitor mVolumeMonitor;

  /** {@link android.content.BroadcastReceiver} for tracking battery status changes. */
  private BatteryMonitor mBatteryMonitor;

  /** {@link BroadcastReceiver} for tracking headphone connected status changes. */
  private HeadphoneStateMonitor mHeadphoneStateMonitor;

  /** Manages screen dimming */
  private DimScreenController mDimScreenController;

  /** The television controller; non-null if the device is a television (Android TV). */
  private TelevisionNavigationController mTelevisionNavigationController;

  private TelevisionDPadManager mTelevisionDPadManager;

  /** {@link BroadcastReceiver} for tracking package removals for custom label data consistency. */
  private PackageRemovalReceiver mPackageReceiver;

  /** The analytics instance, used for sending data to Google Analytics. */
  private Analytics mAnalytics;

  /** Callback to be invoked when fingerprint gestures are being used for accessibility. */
  private FingerprintGestureCallback mFingerprintGestureCallback;

  /** Controller for the selector */
  private SelectorController mSelectorController;

  /** Controller for handling gestures */
  private GestureController mGestureController;

  /** Alert dialog shown when the user attempts to suspend feedback. */
  private AlertDialog mSuspendDialog;

  /** Shared preferences used within TalkBack. */
  private SharedPreferences mPrefs;

  /** The system's uncaught exception handler */
  private UncaughtExceptionHandler mSystemUeh;

  /** The node that was focused during the last call to {@link #saveFocusedNode()} */
  private SavedNode mSavedNode = new SavedNode();

  /** The system feature if the device supports touch screen */
  private boolean mSupportsTouchScreen = true;

  /** Preference specifying when TalkBack should automatically resume. */
  private String mAutomaticResume;

  /** Whether the current root node is dirty or not. */
  private boolean mIsRootNodeDirty = true;
  /** Keep Track of current root node. */
  private AccessibilityNodeInfo mRootNode;

  private AccessibilityEventProcessor mAccessibilityEventProcessor;

  /** Keeps track of whether we need to run the locked-boot-completed callback when connected. */
  private boolean mLockedBootCompletedPending;

  private final InputModeManager mInputModeManager = new InputModeManager();

  private ProcessorAccessibilityHints mProcessorHints;

  private ProcessorScreen mProcessorScreen;

  private OnMagnificationChangedListener mOnMagnificationChangedListener;

  private final DisableTalkBackCompleteAction mDisableTalkBackCompleteAction =
      new DisableTalkBackCompleteAction();

  private SpeakPasswordsManager mSpeakPasswordsManager;

  @Override
  public void onCreate() {
    super.onCreate();

    if (BuildVersionUtils.isAtLeastN()) {
      mOnMagnificationChangedListener =
          new OnMagnificationChangedListener() {
            private float mLastScale = 1.0f;

            @Override
            public void onMagnificationChanged(
                MagnificationController magnificationController,
                Region region,
                float scale,
                float centerX,
                float centerY) {
              // Do nothing if scale hasn't changed.
              if (scale == mLastScale) {
                return;
              }

              mGlobalVariables.setScreenMagnificationLastScale(mLastScale);
              mGlobalVariables.setScreenMagnificationCurrentScale(scale);

              mLastScale = scale;

              mCompositor.sendEvent(
                  Compositor.EVENT_SCREEN_MAGNIFICATION_CHANGED, Performance.EVENT_ID_UNTRACKED);
            }
          };
    }

    sInstance = this;
    setServiceState(ServiceStateListener.SERVICE_STATE_INACTIVE);

    SharedPreferencesUtils.migrateSharedPreferences(this);

    mPrefs = SharedPreferencesUtils.getSharedPreferences(this);

    mSystemUeh = Thread.getDefaultUncaughtExceptionHandler();
    Thread.setDefaultUncaughtExceptionHandler(this);

    mAccessibilityEventProcessor = new AccessibilityEventProcessor(this);
    initializeInfrastructure();

    SharedKeyEvent.register(this);
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
    if (!FormFactorUtils.hasAcessibilityAudioStream(this)) {
      return 1.0f;
    }
    AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);

    int musicStreamVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
    int musicStreamMaxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
    int accessibilityStreamVolume =
        (mVolumeMonitor == null) ? -1 : mVolumeMonitor.getCachedAccessibilityStreamVolume();
    int accessibilityStreamMaxVolume =
        (mVolumeMonitor == null) ? -1 : mVolumeMonitor.getCachedAccessibilityMaxVolume();
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
    interruptAllFeedback(false /* stopTtsSpeechCompletely */);
    // Main thread will be waiting during the TTS announcement, thus in this special case we should
    // not handle TTS callback in main thread.
    mSpeechController.setHandleTtsCallbackInMainThread(false);
    // TalkBack is not allowed to display overlay at this state.
    mSpeechController.setOverlayEnabled(false);
    mSpeechController.setSpeechVolume(calculateFinalAnnouncementVolume());
    mCompositor.sendEventWithCompletionHandler(
        Compositor.EVENT_SPOKEN_FEEDBACK_DISABLED,
        Performance.EVENT_ID_UNTRACKED,
        mDisableTalkBackCompleteAction);
    while (true) {
      synchronized (mDisableTalkBackCompleteAction) {
        if (mDisableTalkBackCompleteAction.isDone) {
          break;
        }
        try {
          mDisableTalkBackCompleteAction.wait();
        } catch (InterruptedException e) {
          // Do nothing
        }
      }
    }
    return false;
  }

  @Override
  public void onDestroy() {
    super.onDestroy();

    SharedKeyEvent.unregister(this);

    if (isServiceActive()) {
      suspendInfrastructure();
    }

    sInstance = null;

    // Shutdown and unregister all components.
    shutdownInfrastructure();
    setServiceState(ServiceStateListener.SERVICE_STATE_INACTIVE);
    mServiceStateListeners.clear();
  }

  @Override
  public void onConfigurationChanged(Configuration newConfig) {
    if (isServiceActive() && (mOrientationMonitor != null)) {
      mOrientationMonitor.onConfigurationChanged(newConfig);
    }

    // Clear the radial menu cache to reload localized strings.
    mMenuManager.clearCache();
  }

  @Override
  public void onAccessibilityEvent(AccessibilityEvent event) {
    Performance perf = Performance.getInstance();
    EventId eventId = perf.onEventReceived(event);

    mAccessibilityEventProcessor.onAccessibilityEvent(event, eventId);

    perf.onHandlerDone(eventId);
  }

  public boolean supportsTouchScreen() {
    return mSupportsTouchScreen;
  }

  @Override
  public AccessibilityNodeInfo getRootInActiveWindow() {
    if (mIsRootNodeDirty || mRootNode == null) {
      mRootNode = super.getRootInActiveWindow();
      mIsRootNodeDirty = false;
    }
    return mRootNode == null ? null : AccessibilityNodeInfo.obtain(mRootNode);
  }

  public void setRootDirty(boolean rootIsDirty) {
    mIsRootNodeDirty = rootIsDirty;
  }

  private void setServiceState(int newState) {
    if (mServiceState == newState) {
      return;
    }

    mServiceState = newState;
    for (ServiceStateListener listener : mServiceStateListeners) {
      listener.onServiceStateChanged(newState);
    }
  }

  public void addServiceStateListener(ServiceStateListener listener) {
    if (listener != null) {
      mServiceStateListeners.add(listener);
    }
  }

  public void removeServiceStateListener(ServiceStateListener listener) {
    if (listener != null) {
      mServiceStateListeners.remove(listener);
    }
  }

  /** Suspends TalkBack, showing a confirmation dialog if applicable. */
  public void requestSuspendTalkBack(EventId eventId) {
    final boolean showConfirmation =
        SharedPreferencesUtils.getBooleanPref(
            mPrefs,
            getResources(),
            R.string.pref_show_suspension_confirmation_dialog,
            R.bool.pref_show_suspension_confirmation_dialog_default);
    if (showConfirmation) {
      confirmSuspendTalkBack();
    } else {
      suspendTalkBack(eventId);
    }
  }

  /** Shows a dialog asking the user to confirm suspension of TalkBack. */
  private void confirmSuspendTalkBack() {
    // Ensure only one dialog is showing.
    if (mSuspendDialog != null) {
      if (mSuspendDialog.isShowing()) {
        return;
      } else {
        mSuspendDialog.dismiss();
        mSuspendDialog = null;
      }
    }

    final LayoutInflater inflater = LayoutInflater.from(this);
    @SuppressLint("InflateParams")
    final ScrollView root = (ScrollView) inflater.inflate(R.layout.suspend_talkback_dialog, null);
    final CheckBox confirmCheckBox = (CheckBox) root.findViewById(R.id.show_warning_checkbox);
    final TextView message = (TextView) root.findViewById(R.id.message_resume);

    final DialogInterface.OnClickListener okayClick =
        new DialogInterface.OnClickListener() {
          @Override
          public void onClick(DialogInterface dialog, int which) {
            if (which == DialogInterface.BUTTON_POSITIVE) {
              if (!confirmCheckBox.isChecked()) {
                SharedPreferencesUtils.putBooleanPref(
                    mPrefs,
                    getResources(),
                    R.string.pref_show_suspension_confirmation_dialog,
                    false);
              }

              EventId eventId = EVENT_ID_UNTRACKED; // Not tracking menu events performance.
              suspendTalkBack(eventId);
            }
          }
        };

    final OnDismissListener onDismissListener =
        new OnDismissListener() {
          @Override
          public void onDismiss(DialogInterface dialog) {
            mSuspendDialog = null;
          }
        };

    if (mAutomaticResume.equals(getString(R.string.resume_screen_keyguard))) {
      message.setText(getString(R.string.message_resume_keyguard));
    } else if (mAutomaticResume.equals(getString(R.string.resume_screen_manual))) {
      message.setText(getString(R.string.message_resume_manual));
    } else { // screen on is the default value
      message.setText(getString(R.string.message_resume_screen_on));
    }

    mSuspendDialog =
        new AlertDialog.Builder(this)
            .setTitle(R.string.dialog_title_suspend_talkback)
            .setView(root)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok, okayClick)
            .create();

    if (BuildVersionUtils.isAtLeastLMR1()) {
      mSuspendDialog.getWindow().setType(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY);
    } else {
      mSuspendDialog.getWindow().setType(WindowManager.LayoutParams.TYPE_SYSTEM_ERROR);
    }

    mSuspendDialog.setOnDismissListener(onDismissListener);
    mSuspendDialog.show();
  }

  /** Suspends TalkBack and Explore by Touch. */
  public void suspendTalkBack(EventId eventId) {

    // Ensure that talkback does not suspend on system with accessibility shortcut.
    if (FormFactorUtils.getInstance(this).hasAccessibilityShortcut()) {
      SharedPreferencesUtils.storeBooleanAsync(mPrefs, getString(R.string.pref_suspended), false);
      return;
    }

    if (!isServiceActive()) {
      LogUtils.log(this, Log.ERROR, "Attempted to suspend TalkBack while already suspended.");
      return;
    }

    SharedPreferencesUtils.storeBooleanAsync(mPrefs, getString(R.string.pref_suspended), true);
    mFeedbackController.playAuditory(R.raw.paused_feedback);

    if (mSupportsTouchScreen) {
      requestTouchExploration(false);
    }

    if (mCursorController != null) {
      try {
        mCursorController.clearCursor(eventId);
      } catch (SecurityException e) {
        LogUtils.log(this, Log.ERROR, "Unable to clear cursor");
      }
    }

    mInputModeManager.clear();

    AccessibilityTutorialActivity.stopActiveTutorial();

    final IntentFilter filter = new IntentFilter();
    filter.addAction(ACTION_RESUME_FEEDBACK);
    filter.addAction(Intent.ACTION_SCREEN_ON);
    registerReceiver(mSuspendedReceiver, filter, PERMISSION_TALKBACK, null);

    // Suspending infrastructure sets sIsTalkBackSuspended to true.
    suspendInfrastructure();

    final Intent resumeIntent = new Intent(ACTION_RESUME_FEEDBACK);
    final PendingIntent pendingIntent = PendingIntent.getBroadcast(this, 0, resumeIntent, 0);
    final Notification notification =
        new NotificationCompat.Builder(this)
            .setContentTitle(getString(R.string.notification_title_talkback_suspended))
            .setContentText(getString(R.string.notification_message_talkback_suspended))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setSmallIcon(R.drawable.ic_stat_info)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setWhen(0)
            .build();

    startForeground(R.id.notification_suspended, notification);

    mCompositor.sendEvent(Compositor.EVENT_SPOKEN_FEEDBACK_SUSPENDED, eventId);
  }

  /**
   * A method used to disable TalkBack from tutorial
   *
   * @param eventId
   */
  public void disableTalkBackFromTutorial(EventId eventId) {
    if (isServiceActive()) {
      if (mSupportsTouchScreen) {
        requestTouchExploration(false);
      }
      AccessibilityTutorialActivity.stopActiveTutorial();

      // OK to clear completely since TalkBack is about to go away.
      mServiceStateListeners.clear();
    }
    disableSelf();
  }

  /** Resumes TalkBack and Explore by Touch. */
  public void resumeTalkBack(EventId eventId) {
    if (isServiceActive()) {
      LogUtils.log(this, Log.ERROR, "Attempted to resume TalkBack when not suspended.");
      return;
    }

    SharedPreferencesUtils.storeBooleanAsync(mPrefs, getString(R.string.pref_suspended), false);

    unregisterReceiver(mSuspendedReceiver);
    resumeInfrastructure();

    mCompositor.sendEvent(Compositor.EVENT_SPOKEN_FEEDBACK_RESUMED, eventId);
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
    Performance perf = Performance.getInstance();
    EventId eventId = perf.onEventReceived(keyEvent);

    if (isServiceActive()) {
      // Stop the TTS engine when any key (except for volume up/down key) is pressed on physical
      // keyboard.
      if (keyEvent.getDeviceId() != 0
          && keyEvent.getAction() == KeyEvent.ACTION_DOWN
          && keyEvent.getKeyCode() != KeyEvent.KEYCODE_VOLUME_DOWN
          && keyEvent.getKeyCode() != KeyEvent.KEYCODE_VOLUME_UP) {
        interruptAllFeedback(false /* stopTtsSpeechCompletely */);
      }
    }

    for (ServiceKeyEventListener listener : mKeyEventListeners) {
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
    if (!isServiceActive()) {
      return false;
    }

    Performance perf = Performance.getInstance();
    EventId eventId = perf.onGestureEventReceived(gestureId);

    LogUtils.log(this, Log.VERBOSE, "Recognized gesture %s", gestureId);

    if (mKeyboardSearchManager != null && mKeyboardSearchManager.onGesture(eventId)) {
      perf.onHandlerDone(eventId);
      return true;
    }
    mAnalytics.onGesture(gestureId);
    mFeedbackController.playAuditory(R.raw.gesture_end);

    mMenuManager.onGesture(gestureId);
    mGestureController.onGesture(gestureId, eventId);

    // Measure latency.
    // Preceding event handling frequently initiates a framework action, which in turn
    // cascades a focus event, which in turn generates feedback.
    perf.onHandlerDone(eventId);

    return true;
  }

  public GestureController getGestureController() {
    if (mGestureController == null) {
      throw new RuntimeException("mGestureController has not been initialized");
    }

    return mGestureController;
  }

  public SpeechController getSpeechController() {
    if (mSpeechController == null) {
      throw new RuntimeException("mSpeechController has not been initialized");
    }

    return mSpeechController;
  }

  public FeedbackController getFeedbackController() {
    if (mFeedbackController == null) {
      throw new RuntimeException("mFeedbackController has not been initialized");
    }

    return mFeedbackController;
  }

  public VoiceActionMonitor getVoiceActionMonitor() {
    if (mVoiceActionMonitor == null) {
      throw new RuntimeException("mVoiceActionMonitor has not been initialized");
    }

    return mVoiceActionMonitor;
  }

  public CursorController getCursorController() {
    if (mCursorController == null) {
      throw new RuntimeException("mCursorController has not been initialized");
    }

    return mCursorController;
  }

  public KeyComboManager getKeyComboManager() {
    return mKeyComboManager;
  }

  public FullScreenReadController getFullScreenReadController() {
    if (mFullScreenReadController == null) {
      throw new RuntimeException("mFullScreenReadController has not been initialized");
    }

    return mFullScreenReadController;
  }

  public DimScreenController getDimScreenController() {
    if (mDimScreenController == null) {
      throw new RuntimeException("mDimScreenController has not been initialized");
    }

    return mDimScreenController;
  }

  public CustomLabelManager getLabelManager() {
    if (mLabelManager == null) {
      throw new RuntimeException("mLabelManager has not been initialized");
    }

    return mLabelManager;
  }

  public Analytics getAnalytics() {
    if (mAnalytics == null) {
      throw new RuntimeException("mAnalytics has not been initialized");
    }

    return mAnalytics;
  }

  public RingerModeAndScreenMonitor getRingerModeAndScreenMonitor() {
    if (mRingerModeAndScreenMonitor == null) {
      throw new RuntimeException("mRingerModeAndScreenMonitor has not been initialized");
    }

    return mRingerModeAndScreenMonitor;
  }

  /**
   * Obtains the shared instance of TalkBack's {@link ShakeDetector}
   *
   * @return the shared {@link ShakeDetector} instance, or null if not initialized.
   */
  public ShakeDetector getShakeDetector() {
    return mShakeDetector;
  }

  /**
   * Obtains the shared instance of TalkBack's {@link TelevisionNavigationController} if the current
   * device is a television. Otherwise returns {@code null}.
   */
  public TelevisionNavigationController getTelevisionNavigationController() {
    return mTelevisionNavigationController;
  }

  public AccessibilityFocusManager getAccessibilityFocusManager() {
    return mAccessibilityFocusManager;
  }

  public ProcessorFocusAndSingleTap getProcessorFocusAndSingleTap() {
    return mProcessorFollowFocus;
  }

  public ProcessorAccessibilityHints getProcessorAccessibilityHints() {
    return mProcessorHints;
  }

  /** Save the currently focused node so that focus can be returned to it later. */
  public void saveFocusedNode() {
    mSavedNode.recycle();

    AccessibilityNodeInfoCompat node = mCursorController.getCursorOrInputCursor();
    if (node != null) {
      mSavedNode.saveNodeState(node, mCursorController.getGranularityAt(node), this);
      node.recycle();
    }
  }

  public boolean hasSavedNode() {
    return mSavedNode.getNode() != null;
  }

  /**
   * Reset the accessibility focus to the node that was focused during the last call to {@link
   * #saveFocusedNode()}
   */
  public void resetFocusedNode(EventId eventId) {
    resetFocusedNode(0, eventId);
  }

  public void resetFocusedNode(long delay, final EventId eventId) {
    final Handler handler = new Handler();
    handler.postDelayed(
        new Runnable() {
          @SuppressLint("InlinedApi")
          @Override
          public void run() {
            AccessibilityNodeInfoCompat node = mSavedNode.getNode();
            if (node == null) {
              return;
            }

            AccessibilityNodeInfoCompat refreshed = AccessibilityNodeInfoUtils.refreshNode(node);

            if (refreshed != null) {
              if (!refreshed.isAccessibilityFocused()) {
                // Restore accessibility focus.
                mCursorController.setGranularity(
                    mSavedNode.getGranularity(), refreshed, false, eventId);
                mSavedNode.restoreTextAndSelection(eventId);

                PerformActionUtils.performAction(
                    refreshed, AccessibilityNodeInfoCompat.ACTION_ACCESSIBILITY_FOCUS, eventId);
              } else if (BuildVersionUtils.isAtLeastN()
                  && mSavedNode.getAnchor() != null
                  && refreshed.getWindow() == null) {
                // The node is anchored, but its window has disappeared.
                // We need to wait for the node's window to appear, and then do a fuzzy-find
                // to place accessibility focus on the node's replacement.
                mCursorController.setGranularity(
                    mSavedNode.getGranularity(), refreshed, false, eventId);
                mSavedNode.restoreTextAndSelection(eventId);

                resetFocusedNodeInAnchoredWindow(
                    mSavedNode.getNode(), mSavedNode.getAnchor(), 250 /* ms */, eventId);
              }

              refreshed.recycle();
            }

            mSavedNode.recycle();
          }
        },
        delay);
  }

  /**
   * Resets the accessibility focus to an item inside an anchored window. We must delay slightly in
   * order for the anchored window to reappear in the windows list before attempting to place the
   * accessibility focus. Furthermore, we cannot find the exact previously-focused item again; we
   * must do a fuzzy search for it instead.
   */
  private void resetFocusedNodeInAnchoredWindow(
      AccessibilityNodeInfoCompat node,
      AccessibilityNodeInfoCompat anchor,
      long delay,
      final EventId eventId) {
    if (node == null || anchor == null) {
      return;
    }

    final AccessibilityNodeInfoCompat obtainedNode = AccessibilityNodeInfoCompat.obtain(node);
    final AccessibilityNodeInfoCompat obtainedAnchor = AccessibilityNodeInfoCompat.obtain(anchor);

    final Handler handler = new Handler();
    handler.postDelayed(
        new Runnable() {
          @Override
          public void run() {
            com.google.android.accessibility.utils.WindowManager windowManager =
                new com.google.android.accessibility.utils.WindowManager(TalkBackService.this);

            AccessibilityWindowInfo anchoredWindow =
                windowManager.getAnchoredWindow(obtainedAnchor);
            AccessibilityNodeInfoCompat refreshed =
                AccessibilityNodeInfoUtils.refreshNodeFuzzy(obtainedNode, anchoredWindow);
            if (refreshed != null) {
              PerformActionUtils.performAction(
                  refreshed, AccessibilityNodeInfoCompat.ACTION_ACCESSIBILITY_FOCUS, eventId);
              refreshed.recycle();
            }
            obtainedNode.recycle();
            obtainedAnchor.recycle();
          }
        },
        delay);
  }

  private void showGlobalContextMenu(EventId eventId) {
    if (mSupportsTouchScreen) {
      mMenuManager.showMenu(R.menu.global_context_menu, eventId);
    }
  }

  private void showLocalContextMenu(EventId eventId) {
    if (mSupportsTouchScreen) {
      mMenuManager.showMenu(R.menu.local_context_menu, eventId);
    }
  }

  private void showCustomActions(EventId eventId) {
    if (mSupportsTouchScreen) {
      mMenuManager.showMenu(R.id.custom_action_menu, eventId);
    }
  }

  private void showLanguageOptions(EventId eventId) {
    if (mSupportsTouchScreen) {
      mMenuManager.showMenu(R.menu.language_menu, eventId);
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
    if (mProcessorScreen != null && FormFactorUtils.getInstance(this).isArc()) {
      // In Arc, we consider that focus goes out from Arc when onInterrupt is called.
      mProcessorScreen.clearScreenState();
    }
    interruptAllFeedback(false /* stopTtsSpeechCompletely */);
  }

  @Override
  public boolean shouldSuppressPassiveFeedback() {
    return mVoiceActionMonitor.shouldSuppressPassiveFeedback();
  }

  @Override
  public void onSpeakingForcedFeedback() {
    mVoiceActionMonitor.onSpeakingForcedFeedback();
  }

  // Interrupts all Talkback feedback. Stops speech from other apps if stopTtsSpeechCompletely
  // is true.
  @Override
  public void interruptAllFeedback(boolean stopTtsSpeechCompletely) {

    if (mFullScreenReadController != null) {
      mFullScreenReadController.interrupt();
    }

    if (mSpeechController != null) {
      mSpeechController.interrupt(stopTtsSpeechCompletely);
    }

    if (mFeedbackController != null) {
      mFeedbackController.interrupt();
    }
  }

  @Override
  protected void onServiceConnected() {
    LogUtils.log(this, Log.VERBOSE, "System bound to service.");

    // The service must be connected before getFingerprintGestureController() is called, thus we
    // cannot initialize fingerprint gesture detection in onCreate().
    initializeFingerprintGestureCallback();

    resumeInfrastructure();

    // Handle any update actions.
    final TalkBackUpdateHelper helper = new TalkBackUpdateHelper(this);
    helper.showPendingNotifications();
    helper.checkUpdate();

    EventId eventId = EVENT_ID_UNTRACKED; // Performance not tracked for service events.
    if (mPrefs.getBoolean(getString(R.string.pref_suspended), false)) {
      if (FormFactorUtils.getInstance(this).hasAccessibilityShortcut()) {
        // Announce that talkback is still on. Even though talkback is not suspendable on android O,
        // talkback might start suspended if user downgrades, suspends, then upgrades talkback, or
        // it could happen if user restored settings from older talkback that was suspended.
        SharedPreferencesUtils.storeBooleanAsync(mPrefs, getString(R.string.pref_suspended), false);
        mCompositor.sendEvent(Compositor.EVENT_SPOKEN_FEEDBACK_ON, eventId);
      } else {
        suspendTalkBack(eventId);
      }
    } else {
      mCompositor.sendEvent(Compositor.EVENT_SPOKEN_FEEDBACK_ON, eventId);
    }

    // If the locked-boot-completed intent was fired before onServiceConnected, we queued it,
    // so now we need to run it.
    if (mLockedBootCompletedPending) {
      onLockedBootCompletedInternal(eventId);
      mLockedBootCompletedPending = false;
    }
    showTutorialIfNecessary();
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

    return service.mServiceState;
  }

  /**
   * Whether the current TalkBackService instance is running and initialized. This method is useful
   * for testing because it can be overridden by mocks.
   */
  public boolean isInstanceActive() {
    return mServiceState == ServiceStateListener.SERVICE_STATE_ACTIVE;
  }

  /** @return {@code true} if TalkBack is running and initialized, {@code false} otherwise. */
  public static boolean isServiceActive() {
    return (getServiceState() == ServiceStateListener.SERVICE_STATE_ACTIVE);
  }

  /** Returns the active TalkBack instance, or {@code null} if not available. */
  public static TalkBackService getInstance() {
    return sInstance;
  }

  /** Initialize {@link FingerprintGestureCallback} for detecting fingerprint gestures. */
  @TargetApi(Build.VERSION_CODES.O)
  private void initializeFingerprintGestureCallback() {
    if (mFingerprintGestureCallback != null || !supportFingerprintGesture()) {
      return;
    }
    mFingerprintGestureCallback =
        new FingerprintGestureCallback() {
          @Override
          public void onGestureDetected(int gesture) {
            if (isServiceActive() && mGestureController != null) {
              Performance perf = Performance.getInstance();
              EventId eventId = perf.onFingerprintGestureEventReceived(gesture);

              LogUtils.log(this, Log.VERBOSE, "Recognized fingerprint gesture %s", gesture);

              // Interrupt keyboard search when the fingerprint gesture is assigned with action.
              if (mGestureController.isFingerprintGestureAssigned(gesture)
                  && mKeyboardSearchManager != null
                  && mKeyboardSearchManager.onGesture(eventId)) {
                perf.onHandlerDone(eventId);
                return;
              }

              // TODO: Update analytics data.
              // TODO: Check if we should dismiss radial menu.
              mFeedbackController.playAuditory(R.raw.gesture_end);

              mGestureController.onFingerprintGesture(gesture, eventId);

              // Measure latency.
              // Preceding event handling frequently initiates a framework action, which in turn
              // cascades a focus event, which in turn generates feedback.
              perf.onHandlerDone(eventId);
            }
          }

          @Override
          public void onGestureDetectionAvailabilityChanged(boolean available) {
            LogUtils.log(
                this,
                Log.VERBOSE,
                "Fingerprint gesture detection is now "
                    + (available ? "available" : "unavailable")
                    + ".");
          }
        };
  }

  /**
   * Initializes the controllers, managers, and processors. This should only be called once from
   * {@link #onCreate}.
   */
  private void initializeInfrastructure() {
    // TODO: we still need it keep true for TV until TouchExplore and Accessibility focus is not
    // unpaired
    // mSupportsTouchScreen = packageManager.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN);

    mFeedbackController = new FeedbackController(this);

    mKeyComboManager = KeyComboManager.create(this);
    mKeyComboManager.addListener(mKeyComboListener);

    mGlobalVariables = new GlobalVariables(this, mInputModeManager, mKeyComboManager);

    mSpeechController = new SpeechControllerImpl(this, this, mFeedbackController);

    mCursorController = new CursorControllerApp(this, mGlobalVariables);
    addEventListener(mCursorController);

    mAnalytics = new TalkBackAnalytics(this);
    mAnalytics.onTalkBackServiceStarted();

    mVoiceActionMonitor = new VoiceActionMonitor(this);
    mAccessibilityEventProcessor.setVoiceActionMonitor(mVoiceActionMonitor);

    mFullScreenReadController =
        new FullScreenReadControllerApp(mFeedbackController, mCursorController, this);
    addEventListener(mFullScreenReadController);

    mKeyEventListeners.add(mInputModeManager);

    mProximitySensorListener = new ProximitySensorListener(this, mSpeechController);

    mShakeDetector = new ShakeDetector(mFullScreenReadController, this);

    mMenuManager = new MenuManagerWrapper();
    updateMenuManagerFromPreferences(); // Sets mMenuManager

    mRingerModeAndScreenMonitor =
        new RingerModeAndScreenMonitor(
            mFeedbackController,
            mMenuManager,
            mShakeDetector,
            mSpeechController,
            mProximitySensorListener,
            this);
    mAccessibilityEventProcessor.setRingerModeAndScreenMonitor(mRingerModeAndScreenMonitor);

    mTextCursorManager = new TextCursorManager();
    addEventListener(mTextCursorManager);

    mLabelManager = new CustomLabelManager(this);

    @Compositor.Flavor int compositorFlavor;
    if (FormFactorUtils.getInstance(this).isArc()) {
      compositorFlavor = Compositor.FLAVOR_ARC;
    } else if (FormFactorUtils.getInstance(this).isTv()) {
      compositorFlavor = Compositor.FLAVOR_TV;
    } else {
      compositorFlavor = Compositor.FLAVOR_NONE;
    }
    mCompositor =
        new Compositor(this, mSpeechController, mLabelManager, mGlobalVariables, compositorFlavor);

    // Only use speak-pass talkback-preference on android O+.
    if (FormFactorUtils.useSpeakPasswordsServicePref()) {
      mHeadphoneStateMonitor = new HeadphoneStateMonitor(this);
      mSpeakPasswordsManager =
          new SpeakPasswordsManager(this, mHeadphoneStateMonitor, mGlobalVariables);
    }

    mEditTextActionHistory = new EditTextActionHistory();

    mSelectorController = new SelectorController(this, mCompositor, mCursorController, mAnalytics);

    mGestureController =
        new GestureControllerApp(
            this,
            mCursorController,
            mFeedbackController,
            mFullScreenReadController,
            mMenuManager,
            mSelectorController);

    mSideTapManager = new SideTapManager(this, mGestureController);
    addEventListener(mSideTapManager);
    mFeedbackController.addHapticFeedbackListener(mSideTapManager);

    // Add event processors. These will process incoming AccessibilityEvents
    // in the order they are added.
    mEventFilter =
        new EventFilter(
            mCompositor,
            this,
            mTextCursorManager,
            mCursorController,
            mInputModeManager,
            mEditTextActionHistory,
            mGlobalVariables);
    mEventFilter.setVoiceActionDelegate(mVoiceActionMonitor);

    ProcessorEventQueue processorEventQueue = new ProcessorEventQueue(mEventFilter);

    addEventListener(processorEventQueue);
    addEventListener(
        new ProcessorScrollPosition(
            mFullScreenReadController, mSpeechController, mCursorController, this));
    addEventListener(new ProcessorPhoneticLetters(this, mSpeechController));

    mProcessorScreen = new ProcessorScreen(this);
    mGlobalVariables.setWindowsDelegate(mProcessorScreen);
    addEventListener(mProcessorScreen);

    mProcessorHints = new ProcessorAccessibilityHints(this, mSpeechController, mCompositor);
    addEventListener(mProcessorHints);
    mKeyEventListeners.add(0, mProcessorHints); // Needs to be first; will not catch any events.

    if (USE_A11Y_FOCUS_MANAGER) {
      mAccessibilityFocusManager =
          new AccessibilityFocusManager(
              /* service= */ this,
              mSpeechController,
              mFeedbackController,
              /* speechControllerDelegate= */ this);
      addEventListener(mAccessibilityFocusManager);
    } else {
      mProcessorFollowFocus =
          new ProcessorFocusAndSingleTap(
              mCursorController, mFeedbackController, mSpeechController, this, mGlobalVariables);
      addEventListener(mProcessorFollowFocus);
    }

    // ProcessorPermissionDialogs gets the instance of DimScreenController
    // So DimScreenController need to be initialized before ProcessorPermissionDialogs
    DimScreenControllerApp dimScreenController = new DimScreenControllerApp(this);
    mDimScreenController = dimScreenController;

    mProcessorCursorState = new ProcessorCursorState(this, mGlobalVariables);
    mProcessorPermissionsDialogs = new ProcessorPermissionDialogs(this);

    mVolumeMonitor = new VolumeMonitor(mSpeechController, this);
    mBatteryMonitor =
        new BatteryMonitor(
            this,
            mSpeechController,
            (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE));

    // TODO: Move this into the custom label manager code
    mPackageReceiver = new PackageRemovalReceiver();

    addEventListener(new ProcessorGestureVibrator(mFeedbackController));

    ProcessorVolumeStream processorVolumeStream =
        new ProcessorVolumeStream(
            mFeedbackController,
            mCursorController,
            mDimScreenController,
            mSpeechController,
            mFullScreenReadController,
            this,
            mGlobalVariables);
    addEventListener(processorVolumeStream);
    mKeyEventListeners.add(processorVolumeStream);

    // Search mode should receive key combos immediately after the TalkBackService.
    mKeyboardSearchManager = new KeyboardSearchManager(this, mLabelManager);
    mKeyEventListeners.add(mKeyboardSearchManager);
    addEventListener(mKeyboardSearchManager);
    mKeyComboManager.addListener(mKeyboardSearchManager);
    mKeyComboManager.addListener(mCursorController);
    mKeyEventListeners.add(mKeyComboManager);
    mServiceStateListeners.add(mKeyComboManager);

    addEventListener(mSavedNode);

    mOrientationMonitor = new OrientationMonitor(mCompositor, this);
    mOrientationMonitor.addOnOrientationChangedListener(dimScreenController);

    KeyboardLockMonitor keyboardLockMonitor = new KeyboardLockMonitor(mCompositor);
    mKeyEventListeners.add(keyboardLockMonitor);

    if (Build.VERSION.SDK_INT >= TelevisionNavigationController.MIN_API_LEVEL
        && FormFactorUtils.getInstance(this).isTv()) {
      mTelevisionNavigationController = new TelevisionNavigationController(this);
      mKeyEventListeners.add(mTelevisionNavigationController);
      mTelevisionDPadManager = new TelevisionDPadManager(mTelevisionNavigationController);
      addEventListener(mTelevisionDPadManager);
    }
  }

  public void updateMenuManagerFromPreferences() {
    mMenuManager.dismissAll();

    if (SharedPreferencesUtils.getBooleanPref(
        mPrefs,
        getResources(),
        R.string.pref_show_context_menu_as_list_key,
        R.bool.pref_show_menu_as_list)) {
      setMenuManagerToList();
    } else {
      setMenuManagerToRadial();
    }
  }

  // Gets the user preferred locale changed using language switcher.
  public Locale getUserPreferredLocale() {
    return mCompositor.getUserPreferredLanguage();
  }

  // Sets the user preferred locale changed using language switcher.
  public void setUserPreferredLocale(Locale locale) {
    mCompositor.setUserPreferredLanguage(locale);
  }

  public void setMenuManagerToList() {
    mMenuManager.setMenuManager(
        new ListMenuManager(this, mEditTextActionHistory, mTextCursorManager, mGlobalVariables));
  }

  public void setMenuManagerToRadial() {
    mMenuManager.setMenuManager(
        new RadialMenuManager(
            mSupportsTouchScreen, this, mEditTextActionHistory, mTextCursorManager));
  }

  public MenuManager getMenuManager() {
    return mMenuManager;
  }

  /**
   * Check whether fingerprint gesture is supported on the device.
   *
   * @return whether fingerprint gesture is supported on the device.
   */
  private boolean supportFingerprintGesture() {
    return BuildVersionUtils.isAtLeastO() && HardwareUtils.isFingerprintSupported(this);
  }

  /**
   * Registers listeners, sets service info, loads preferences. This should be called from {@link
   * #onServiceConnected} and when TalkBack resumes from a suspended state.
   */
  private void resumeInfrastructure() {
    if (isServiceActive()) {
      LogUtils.log(this, Log.ERROR, "Attempted to resume while not suspended");
      return;
    }

    setServiceState(ServiceStateListener.SERVICE_STATE_ACTIVE);
    stopForeground(true);

    final AccessibilityServiceInfo info = new AccessibilityServiceInfo();
    info.eventTypes = AccessibilityEvent.TYPES_ALL_MASK;
    info.feedbackType |= AccessibilityServiceInfo.FEEDBACK_SPOKEN;
    info.feedbackType |= AccessibilityServiceInfo.FEEDBACK_AUDIBLE;
    info.feedbackType |= AccessibilityServiceInfo.FEEDBACK_HAPTIC;
    info.flags |= AccessibilityServiceInfo.DEFAULT;
    info.flags |= AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS;
    info.flags |= AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS;
    if (BuildVersionUtils.isAtLeastLMR1()) {
      info.flags |= AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
    }
    if (BuildVersionUtils.isAtLeastO()) {
      info.flags |= AccessibilityServiceInfo.FLAG_ENABLE_ACCESSIBILITY_VOLUME;
      info.flags |= AccessibilityServiceInfo.FLAG_REQUEST_FINGERPRINT_GESTURES;
    }
    info.notificationTimeout = 0;

    // Ensure the initial touch exploration request mode is correct.
    if (mSupportsTouchScreen
        && SharedPreferencesUtils.getBooleanPref(
            mPrefs,
            getResources(),
            R.string.pref_explore_by_touch_key,
            R.bool.pref_explore_by_touch_default)) {
      info.flags |= AccessibilityServiceInfo.FLAG_REQUEST_TOUCH_EXPLORATION_MODE;
    }

    setServiceInfo(info);

    if (mVoiceActionMonitor != null) {
      mVoiceActionMonitor.onResumeInfrastructure();
    }

    if (mRingerModeAndScreenMonitor != null) {
      registerReceiver(mRingerModeAndScreenMonitor, mRingerModeAndScreenMonitor.getFilter());
      // It could now be confused with the current screen state
      mRingerModeAndScreenMonitor.updateScreenState();
    }

    if (mHeadphoneStateMonitor != null) {
      mHeadphoneStateMonitor.startMonitoring();
    }

    if (mVolumeMonitor != null) {
      registerReceiver(mVolumeMonitor, mVolumeMonitor.getFilter());
      if (FormFactorUtils.hasAcessibilityAudioStream(this)) {
        // Cache the initial volume in case that the volume is never changed during runtime.
        mVolumeMonitor.cacheAccessibilityStreamVolume();
      }
    }

    if (mBatteryMonitor != null) {
      registerReceiver(mBatteryMonitor, mBatteryMonitor.getFilter());
    }

    if (mPackageReceiver != null) {
      registerReceiver(mPackageReceiver, mPackageReceiver.getFilter());
      if (mLabelManager != null) {
        mLabelManager.ensureDataConsistency();
      }
    }

    if (mSideTapManager != null) {
      registerReceiver(mSideTapManager, SideTapManager.getFilter());
    }

    mPrefs.registerOnSharedPreferenceChangeListener(mSharedPreferenceChangeListener);
    mPrefs.registerOnSharedPreferenceChangeListener(mAnalytics);

    // Add the broadcast listener for gestures.
    final IntentFilter filter = new IntentFilter();
    filter.addAction(ACTION_PERFORM_GESTURE_ACTION);
    registerReceiver(mActiveReceiver, filter, PERMISSION_TALKBACK, null);

    if (mTelevisionDPadManager != null) {
      registerReceiver(mTelevisionDPadManager, TelevisionDPadManager.getFilter());
    }

    if (BuildVersionUtils.isAtLeastN()) {
      MagnificationController magnificationController = getMagnificationController();
      if (magnificationController != null && mOnMagnificationChangedListener != null) {
        magnificationController.addListener(mOnMagnificationChangedListener);
      }
    }

    if (mFingerprintGestureCallback != null) {
      getFingerprintGestureController()
          .registerFingerprintGestureCallback(mFingerprintGestureCallback, null);
    }

    reloadPreferences();

    mDimScreenController.resume();

    mCursorController.initLastEditable();
  }

  @Override
  public void unregisterReceiver(BroadcastReceiver receiver) {
    try {
      if (receiver != null) {
        super.unregisterReceiver(receiver);
      }
    } catch (IllegalArgumentException e) {
      Log.e(
          LOGTAG,
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
      LogUtils.log(this, Log.ERROR, "Attempted to suspend while already suspended");
      return;
    }

    if (mVoiceActionMonitor != null) {
      mVoiceActionMonitor.onSuspendInfrastructure();
    }

    mDimScreenController.suspend();

    interruptAllFeedback(false /* stopTtsSpeechCompletely */);
    setServiceState(ServiceStateListener.SERVICE_STATE_SUSPENDED);

    // Some apps depend on these being set to false when TalkBack is disabled.
    if (mSupportsTouchScreen) {
      requestTouchExploration(false);
    }

    mPrefs.unregisterOnSharedPreferenceChangeListener(mSharedPreferenceChangeListener);
    mPrefs.unregisterOnSharedPreferenceChangeListener(mSharedPreferenceChangeListener);

    if (mMenuManager != null) {
      mMenuManager.clearCache();
    }

    unregisterReceivers(
        mActiveReceiver,
        mRingerModeAndScreenMonitor,
        mBatteryMonitor,
        mPackageReceiver,
        mVolumeMonitor,
        mSideTapManager,
        mTelevisionDPadManager);

    if (mVolumeMonitor != null) {
      mVolumeMonitor.releaseControl();
    }

    if (mShakeDetector != null) {
      mShakeDetector.setEnabled(false);
    }

    // The tap detector is enabled through reloadPreferences
    if (mSideTapManager != null) {
      mSideTapManager.onSuspendInfrastructure();
    }

    if (mHeadphoneStateMonitor != null) {
      mHeadphoneStateMonitor.stopMonitoring();
    }

    // Remove any pending notifications that shouldn't persist.
    final NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
    nm.cancelAll();

    if (BuildVersionUtils.isAtLeastN()) {
      MagnificationController magnificationController = getMagnificationController();
      if (magnificationController != null && mOnMagnificationChangedListener != null) {
        magnificationController.removeListener(mOnMagnificationChangedListener);
      }
    }

    if (mFingerprintGestureCallback != null) {
      getFingerprintGestureController()
          .unregisterFingerprintGestureCallback(mFingerprintGestureCallback);
    }
  }

  /** Shuts down the infrastructure in case it has been initialized. */
  private void shutdownInfrastructure() {
    // we put it first to be sure that screen dimming would be removed even if code bellow
    // will crash by any reason. Because leaving user with dimmed screen is super bad
    mDimScreenController.shutdown();

    if (mCursorController != null) {
      mCursorController.shutdown();
    }

    if (mFullScreenReadController != null) {
      mFullScreenReadController.shutdown();
    }

    if (mLabelManager != null) {
      mLabelManager.shutdown();
    }

    mProximitySensorListener.shutdown();
    mFeedbackController.shutdown();
    mSpeechController.shutdown();
    mAnalytics.onTalkBackServiceStopped();
  }

  /**
   * Adds an event listener.
   *
   * @param listener The listener to add.
   */
  public void addEventListener(AccessibilityEventListener listener) {
    mAccessibilityEventProcessor.addAccessibilityEventListener(listener);
  }

  /**
   * Posts a {@link Runnable} to removes an event listener. This is safe to call from inside {@link
   * AccessibilityEventListener#onAccessibilityEvent(AccessibilityEvent, EventId)}.
   *
   * @param listener The listener to remove.
   */
  public void postRemoveEventListener(final AccessibilityEventListener listener) {
    mAccessibilityEventProcessor.postRemoveAccessibilityEventListener(listener);
  }

  /** Reloads service preferences. */
  private void reloadPreferences() {
    final Resources res = getResources();

    // If performance statistics changing enabled setting... clear collected stats.
    boolean performanceEnabled =
        SharedPreferencesUtils.getBooleanPref(
            mPrefs,
            res,
            R.string.pref_performance_stats_key,
            R.bool.pref_performance_stats_default);
    Performance performance = Performance.getInstance();
    if (performance.getEnabled() != performanceEnabled) {
      performance.clearRecentEvents();
      performance.clearAllStats();
      performance.setEnabled(performanceEnabled);
    }

    mAccessibilityEventProcessor.setSpeakWhenScreenOff(
        VerbosityPreferences.getPreferenceValueBool(
            mPrefs,
            res,
            res.getString(R.string.pref_screenoff_key),
            res.getBoolean(R.bool.pref_screenoff_default)));

    mAutomaticResume =
        mPrefs.getString(
            res.getString(R.string.pref_resume_talkback_key), getString(R.string.resume_screen_on));

    final boolean silenceOnProximity =
        SharedPreferencesUtils.getBooleanPref(
            mPrefs, res, R.string.pref_proximity_key, R.bool.pref_proximity_default);
    mProximitySensorListener.setSilenceOnProximity(silenceOnProximity);

    LogUtils.setLogLevel(
        SharedPreferencesUtils.getIntFromStringPref(
            mPrefs, res, R.string.pref_log_level_key, R.string.pref_log_level_default));

    final boolean useSingleTap =
        SharedPreferencesUtils.getBooleanPref(
            mPrefs, res, R.string.pref_single_tap_key, R.bool.pref_single_tap_default);
    mGlobalVariables.setUseSingleTap(useSingleTap);
    if (USE_A11Y_FOCUS_MANAGER) {
      mAccessibilityFocusManager.setSingleTapEnabled(useSingleTap);
    } else {
      mProcessorFollowFocus.setSingleTapEnabled(useSingleTap);
    }
    if (mShakeDetector != null) {
      final int shakeThreshold =
          SharedPreferencesUtils.getIntFromStringPref(
              mPrefs,
              res,
              R.string.pref_shake_to_read_threshold_key,
              R.string.pref_shake_to_read_threshold_default);
      final boolean useShake =
          (shakeThreshold > 0)
              && (mVoiceActionMonitor.getCurrentCallState() == TelephonyManager.CALL_STATE_IDLE);

      mShakeDetector.setEnabled(useShake);
    }

    if (mSideTapManager != null) {
      mSideTapManager.onReloadPreferences();
    }

    if (mSupportsTouchScreen) {
      // Touch exploration *must* be enabled on TVs for TalkBack to function.
      final boolean touchExploration =
          FormFactorUtils.getInstance(this).isTv()
              || SharedPreferencesUtils.getBooleanPref(
                  mPrefs,
                  res,
                  R.string.pref_explore_by_touch_key,
                  R.bool.pref_explore_by_touch_default);
      requestTouchExploration(touchExploration);
    }

    mProcessorCursorState.onReloadPreferences(this);
    mProcessorPermissionsDialogs.onReloadPreferences(this);

    // Reload speech preferences.
    mSpeechController.setOverlayEnabled(
        SharedPreferencesUtils.getBooleanPref(
            mPrefs, res, R.string.pref_tts_overlay_key, R.bool.pref_tts_overlay_default));
    mSpeechController.setUseIntonation(
        VerbosityPreferences.getPreferenceValueBool(
            mPrefs,
            res,
            res.getString(R.string.pref_intonation_key),
            res.getBoolean(R.bool.pref_intonation_default)));
    mSpeechController.setSpeechPitch(
        SharedPreferencesUtils.getFloatFromStringPref(
            mPrefs, res, R.string.pref_speech_pitch_key, R.string.pref_speech_pitch_default));
    float speechRate =
        SharedPreferencesUtils.getFloatFromStringPref(
            mPrefs, res, R.string.pref_speech_rate_key, R.string.pref_speech_rate_default);
    mSpeechController.setSpeechRate(speechRate);
    mGlobalVariables.setSpeechRate(speechRate);
    int keyboardPref =
        Integer.parseInt(
            VerbosityPreferences.getPreferenceValueString(
                mPrefs,
                res,
                res.getString(R.string.pref_keyboard_echo_key),
                res.getString(R.string.pref_keyboard_echo_default)));
    mEventFilter.setKeyboardEcho(keyboardPref);

    boolean useAudioFocus =
        SharedPreferencesUtils.getBooleanPref(
            mPrefs, res, R.string.pref_use_audio_focus_key, R.bool.pref_use_audio_focus_default);
    mSpeechController.setUseAudioFocus(useAudioFocus);
    mGlobalVariables.setUseAudioFocus(useAudioFocus);

    // Speech volume is stored as int [0,100] and scaled to float [0,1].
    if (!FormFactorUtils.hasAcessibilityAudioStream(this)) {
      mSpeechController.setSpeechVolume(
          SharedPreferencesUtils.getIntFromStringPref(
                  mPrefs, res, R.string.pref_speech_volume_key, R.string.pref_speech_volume_default)
              / 100.0f);
    }

    updateMenuManagerFromPreferences();

    if (mSpeakPasswordsManager != null) {
      mSpeakPasswordsManager.onPreferencesChanged();
    }

    // Reload feedback preferences.
    int adjustment =
        SharedPreferencesUtils.getIntFromStringPref(
            mPrefs,
            res,
            R.string.pref_soundback_volume_key,
            R.string.pref_soundback_volume_default);
    mFeedbackController.setVolumeAdjustment(adjustment / 100.0f);

    boolean hapticEnabled =
        SharedPreferencesUtils.getBooleanPref(
            mPrefs, res, R.string.pref_vibration_key, R.bool.pref_vibration_default);
    mFeedbackController.setHapticEnabled(hapticEnabled);

    boolean auditoryEnabled =
        SharedPreferencesUtils.getBooleanPref(
            mPrefs, res, R.string.pref_soundback_key, R.bool.pref_soundback_default);
    mFeedbackController.setAuditoryEnabled(auditoryEnabled);

    // Update compositor preferences.
    if (mCompositor != null) {
      // Update preference: speak collection info.
      boolean speakCollectionInfo =
          VerbosityPreferences.getPreferenceValueBool(
              mPrefs,
              res,
              res.getString(R.string.pref_speak_container_element_positions_key),
              res.getBoolean(R.bool.pref_speak_container_element_positions_default));
      mCompositor.setSpeakCollectionInfo(speakCollectionInfo);

      // Update preference: speak roles.
      boolean speakRoles =
          VerbosityPreferences.getPreferenceValueBool(
              mPrefs,
              res,
              res.getString(R.string.pref_speak_roles_key),
              res.getBoolean(R.bool.pref_speak_roles_default));
      mCompositor.setSpeakRoles(speakRoles);

      // Update preference: description order.
      String descriptionOrder =
          SharedPreferencesUtils.getStringPref(
              mPrefs,
              res,
              R.string.pref_node_desc_order_key,
              R.string.pref_node_desc_order_default);
      mCompositor.setDescriptionOrder(prefValueToDescriptionOrder(res, descriptionOrder));

      // Update preference: speak element IDs.
      boolean speakElementIds =
          SharedPreferencesUtils.getBooleanPref(
              mPrefs,
              res,
              R.string.pref_speak_element_ids_key,
              R.bool.pref_speak_element_ids_default);
      mCompositor.setSpeakElementIds(speakElementIds);

      // Reload compositor configuration.
      mCompositor.refreshParseTreeIfNeeded();
    }
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
      LogUtils.log(
          Compositor.class,
          Log.ERROR,
          "Unhandled description order preference value \"%s\"",
          value);
      return Compositor.DESC_ORDER_STATE_NAME_ROLE_POSITION;
    }
  }

  @Override
  public List<AccessibilityWindowInfo> getWindows() {
    List<AccessibilityWindowInfo> windows;
    // If build version is not isAtLeastN(), there is a chance of ClassCastException or
    // NullPointerException.
    // TODO: Create a wrapper for AccessibilityService#getWindows() in
    // AccessibilityServiceCompatUtils and route all calls to AccessibilityService#getWindows() via
    // AccessibilityServiceCompatUtils.
    if (!BuildVersionUtils.isAtLeastN()) {
      try {
        windows = super.getWindows();
      } catch (Exception e) {
        LogUtils.log(
            this, Log.ERROR, "Exception occurred at AccessibilityService#getWindows(): %s", e);
        return new ArrayList<AccessibilityWindowInfo>();
      }
    } else {
      windows = super.getWindows();
    }
    return windows;
  }

  /**
   * Attempts to change the state of touch exploration.
   *
   * <p>Should only be called if {@link #mSupportsTouchScreen} is true.
   *
   * @param requestedState {@code true} to request exploration.
   */
  private void requestTouchExploration(boolean requestedState) {
    final AccessibilityServiceInfo info = getServiceInfo();
    if (info == null) {
      LogUtils.log(
          this,
          Log.ERROR,
          "Failed to change touch exploration request state, service info was null");
      return;
    }

    final boolean currentState =
        ((info.flags & AccessibilityServiceInfo.FLAG_REQUEST_TOUCH_EXPLORATION_MODE) != 0);
    if (currentState == requestedState) {
      return;
    }

    if (requestedState) {
      info.flags |= AccessibilityServiceInfo.FLAG_REQUEST_TOUCH_EXPLORATION_MODE;
    } else {
      info.flags &= ~AccessibilityServiceInfo.FLAG_REQUEST_TOUCH_EXPLORATION_MODE;
    }

    setServiceInfo(info);
  }

  /**
   * Launches the touch exploration tutorial if necessary.
   *
   * @return {@code true} if the tutorial is launched successfully.
   */
  public boolean showTutorialIfNecessary() {
    if (FormFactorUtils.getInstance(this).isArc()) {
      return false;
    }

    boolean isDeviceProvisioned =
        Settings.Secure.getInt(getContentResolver(), Settings.Global.DEVICE_PROVISIONED, 1) != 0;

    if (isDeviceProvisioned && !isFirstTimeUser()) {
      return false;
    }

    final int touchscreenState = getResources().getConfiguration().touchscreen;

    if (touchscreenState != Configuration.TOUCHSCREEN_NOTOUCH && mSupportsTouchScreen) {
      final Intent tutorial = new Intent(this, AccessibilityTutorialActivity.class);
      tutorial.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
      tutorial.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
      tutorial.putExtra(EXTRA_TUTORIAL_INTENT_SOURCE, TUTORIAL_SRC);
      startActivity(tutorial);
      return true;
    }

    return false;
  }

  private boolean isFirstTimeUser() {
    return mPrefs.getBoolean(PREF_FIRST_TIME_USER, true);
  }

  private final KeyComboManager.KeyComboListener mKeyComboListener =
      new KeyComboManager.KeyComboListener() {
        @Override
        public boolean onComboPerformed(int id, EventId eventId) {
          switch (id) {
            case KeyComboManager.ACTION_SUSPEND_OR_RESUME:
              if (mServiceState == ServiceStateListener.SERVICE_STATE_SUSPENDED) {
                resumeTalkBack(eventId);
              } else if (mServiceState == ServiceStateListener.SERVICE_STATE_ACTIVE) {
                requestSuspendTalkBack(eventId);
              }
              return true;
            case KeyComboManager.ACTION_BACK:
              TalkBackService.this.performGlobalAction(GLOBAL_ACTION_BACK);
              return true;
            case KeyComboManager.ACTION_HOME:
              TalkBackService.this.performGlobalAction(GLOBAL_ACTION_HOME);
              return true;
            case KeyComboManager.ACTION_NOTIFICATION:
              TalkBackService.this.performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS);
              return true;
            case KeyComboManager.ACTION_RECENTS:
              TalkBackService.this.performGlobalAction(GLOBAL_ACTION_RECENTS);
              return true;
            case KeyComboManager.ACTION_GRANULARITY_INCREASE:
              mCursorController.nextGranularity(eventId);
              return true;
            case KeyComboManager.ACTION_GRANULARITY_DECREASE:
              mCursorController.previousGranularity(eventId);
              return true;
            case KeyComboManager.ACTION_READ_FROM_TOP:
              mFullScreenReadController.startReadingFromBeginning(eventId);
              return true;
            case KeyComboManager.ACTION_READ_FROM_NEXT_ITEM:
              mFullScreenReadController.startReadingFromNextNode(eventId);
              return true;
            case KeyComboManager.ACTION_GLOBAL_CONTEXT_MENU:
              showGlobalContextMenu(eventId);
              return true;
            case KeyComboManager.ACTION_LOCAL_CONTEXT_MENU:
              showLocalContextMenu(eventId);
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
              openTalkBackSettings();
              return true;
            default: // fall out
          }

          return false;
        }
      };

  /** Reloads preferences whenever their values change. */
  private final OnSharedPreferenceChangeListener mSharedPreferenceChangeListener =
      new OnSharedPreferenceChangeListener() {
        @Override
        public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
          LogUtils.log(this, Log.DEBUG, "A shared preference changed: %s", key);
          reloadPreferences();
        }
      };

  /** Broadcast receiver for actions that happen while the service is active. */
  private final BroadcastReceiver mActiveReceiver =
      new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
          final String action = intent.getAction();

          if (ACTION_PERFORM_GESTURE_ACTION.equals(action)) {
            int gestureId =
                intent.getIntExtra(EXTRA_GESTURE_ACTION, R.string.shortcut_value_unassigned);
            EventId eventId = Performance.getInstance().onGestureEventReceived(gestureId);
            mGestureController.onGesture(gestureId, eventId);
            Performance.getInstance().onHandlerDone(eventId);
          }
        }
      };

  /** Broadcast receiver for actions that happen while the service is inactive. */
  private final BroadcastReceiver mSuspendedReceiver =
      new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
          final String action = intent.getAction();

          EventId eventId = EVENT_ID_UNTRACKED; // Performance not tracked for broadcasts.
          if (ACTION_RESUME_FEEDBACK.equals(action)) {
            resumeTalkBack(eventId);
          } else if (Intent.ACTION_SCREEN_ON.equals(action)) {
            if (mAutomaticResume.equals(getString(R.string.resume_screen_keyguard))) {
              final KeyguardManager keyguard =
                  (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
              if (keyguard.inKeyguardRestrictedInputMode()) {
                resumeTalkBack(eventId);
              }
            } else if (mAutomaticResume.equals(getString(R.string.resume_screen_on))) {
              resumeTalkBack(eventId);
            }
          }
        }
      };

  public void onLockedBootCompleted(EventId eventId) {
    if (mServiceState == ServiceStateListener.SERVICE_STATE_INACTIVE) {
      // onServiceConnected has not completed yet. We need to defer the boot completion
      // callback until after onServiceConnected has run.
      mLockedBootCompletedPending = true;
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
    mSpeechController.updateTtsEngine(true /* quiet */);

    if (!isServiceActive()
        && mAutomaticResume != null
        && !mAutomaticResume.equals(getString(R.string.resume_screen_manual))) {
      resumeTalkBack(eventId);
    }
  }

  public void onUnlockedBootCompleted() {
    // Update TTS and allow announcement.
    // If the TTS changes here, it is probably a non-FBE TTS that is available after the user
    // unlocks their phone. In this case, the user heard Google TTS at the lock screen, so we
    // should let them know that we're using their preferred TTS now.
    mSpeechController.updateTtsEngine(false /* quiet */);

    if (mLabelManager != null) {
      mLabelManager.ensureLabelsLoaded();
    }
  }

  @Override
  public void uncaughtException(Thread thread, Throwable ex) {
    try {
      if (mDimScreenController != null) {
        mDimScreenController.shutdown();
      }

      if (mMenuManager != null && mMenuManager.isMenuShowing()) {
        mMenuManager.dismissAll();
      }

      if (mSuspendDialog != null) {
        mSuspendDialog.dismiss();
      }
    } catch (Exception e) {
      // Do nothing.
    } finally {
      if (mSystemUeh != null) {
        mSystemUeh.uncaughtException(thread, ex);
      }
    }
  }

  public void setTestingListener(TalkBackListener testingListener) {
    mAccessibilityEventProcessor.setTestingListener(testingListener);
  }

  public boolean isScreenOrientationLandscape() {
    Configuration config = getResources().getConfiguration();
    if (config == null) {
      return false;
    }
    return config.orientation == Configuration.ORIENTATION_LANDSCAPE;
  }

  public InputModeManager getInputModeManager() {
    return mInputModeManager;
  }

  public void notifyDumpEventPreferenceChanged(int eventType, boolean shouldDump) {
    mAccessibilityEventProcessor.onDumpEventPreferenceChanged(eventType, shouldDump);
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
}
