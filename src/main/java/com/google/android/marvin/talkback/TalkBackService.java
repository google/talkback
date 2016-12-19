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

package com.google.android.marvin.talkback;

import android.annotation.SuppressLint;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.accessibility.AccessibilityNodeInfo;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.app.KeyguardManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnDismissListener;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Build;
import android.os.Handler;
import android.support.v4.app.NotificationCompat;
import android.support.v4.os.BuildCompat;
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityWindowInfo;
import android.widget.CheckBox;
import android.widget.ScrollView;
import android.widget.TextView;
import com.android.switchaccess.SwitchAccessService;
import com.android.talkback.Analytics;
import com.android.talkback.BatteryMonitor;
import com.android.talkback.CallStateMonitor;
import com.android.talkback.controller.TelevisionNavigationController;
import com.android.talkback.InputModeManager;
import com.android.talkback.KeyComboManager;
import com.android.talkback.KeyboardLockMonitor;
import com.android.talkback.KeyboardSearchManager;
import com.android.talkback.OrientationMonitor;
import com.android.talkback.R;
import com.android.talkback.RingerModeAndScreenMonitor;
import com.android.talkback.SavedNode;
import com.android.talkback.ShakeDetector;
import com.android.talkback.ShortcutProxyActivity;
import com.android.talkback.SideTapManager;
import com.android.talkback.SpeechController;
import com.android.talkback.TalkBackAnalytics;
import com.android.talkback.TalkBackKeyboardShortcutPreferencesActivity;
import com.android.talkback.TalkBackPreferencesActivity;
import com.android.talkback.TalkBackUpdateHelper;
import com.android.talkback.VolumeMonitor;
import com.android.talkback.contextmenu.ListMenuManager;
import com.android.talkback.contextmenu.MenuManager;
import com.android.talkback.contextmenu.MenuManagerWrapper;
import com.android.talkback.contextmenu.RadialMenuManager;
import com.android.talkback.controller.CursorController;
import com.android.talkback.controller.CursorControllerApp;
import com.android.talkback.controller.DimScreenController;
import com.android.talkback.controller.DimScreenControllerApp;
import com.android.talkback.controller.FeedbackController;
import com.android.talkback.controller.FeedbackControllerApp;
import com.android.talkback.controller.FullScreenReadController;
import com.android.talkback.controller.FullScreenReadControllerApp;
import com.android.talkback.controller.GestureController;
import com.android.talkback.controller.GestureControllerApp;
import com.android.talkback.eventprocessor.AccessibilityEventProcessor;
import com.android.talkback.eventprocessor.AccessibilityEventProcessor.TalkBackListener;
import com.android.talkback.eventprocessor.ProcessorEventQueue;
import com.android.talkback.eventprocessor.ProcessorFocusAndSingleTap;
import com.android.talkback.eventprocessor.ProcessorGestureVibrator;
import com.android.talkback.eventprocessor.ProcessorAccessibilityHints;
import com.android.talkback.eventprocessor.ProcessorPhoneticLetters;
import com.android.talkback.eventprocessor.ProcessorScreen;
import com.android.talkback.eventprocessor.ProcessorScrollPosition;
import com.android.talkback.eventprocessor.ProcessorVolumeStream;
import com.android.talkback.eventprocessor.ProcessorWebContent;
import com.android.talkback.controller.TextCursorController;
import com.android.talkback.controller.TextCursorControllerApp;
import com.android.talkback.speechrules.NodeHintRule;
import com.android.talkback.speechrules.NodeSpeechRuleProcessor;
import com.android.talkback.tutorial.AccessibilityTutorialActivity;
import com.android.utils.AccessibilityEventListener;
import com.android.utils.AccessibilityNodeInfoUtils;
import com.android.utils.LogUtils;
import com.android.utils.PerformActionUtils;
import com.android.utils.SharedPreferencesUtils;
import com.android.utils.WebInterfaceUtils;
import com.android.utils.labeling.CustomLabelManager;
import com.android.utils.labeling.PackageRemovalReceiver;

import java.lang.Thread.UncaughtExceptionHandler;
import java.util.LinkedList;
import java.util.List;

/**
 * An {@link AccessibilityService} that provides spoken, haptic, and audible
 * feedback.
 */
public class TalkBackService extends AccessibilityService
        implements Thread.UncaughtExceptionHandler {
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
     * Intent to open text-to-speech settings.
     */
    public static final String INTENT_TTS_SETTINGS = "com.android.settings.TTS_SETTINGS";

    /** Whether the current SDK supports service-managed web scripts. */
    private static final boolean SUPPORTS_WEB_SCRIPT_TOGGLE =
        (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2);

    /** Action used to resume feedback. */
    private static final String ACTION_RESUME_FEEDBACK =
            "com.google.android.marvin.talkback.RESUME_FEEDBACK";

    /** An active instance of TalkBack. */
    private static TalkBackService sInstance = null;

    /** The possible states of the service. */
    /** The state of the service before the system has bound to it or after it is destroyed. */
    public static final int SERVICE_STATE_INACTIVE = 0;
    /** The state of the service when it initialized and active. */
    public static final int SERVICE_STATE_ACTIVE = 1;
    /** The state of the service when it has been suspended by the user. */
    public static final int SERVICE_STATE_SUSPENDED = 2;

    private final static String LOGTAG = "TalkBackService";

    private final static String ARC_DEVICE_PATTERN = ".+_cheets";

    /**
     * List of key event processors. Processors in the list are sent the event
     * in the order they were added until a processor consumes the event.
     */
    private final LinkedList<KeyEventListener> mKeyEventListeners = new LinkedList<>();

    /** The current state of the service. */
    private int mServiceState;

    /** Components to receive callbacks on changes in the service's state. */
    private List<ServiceStateListener> mServiceStateListeners = new LinkedList<>();

    /** Controller for cursor movement. */
    private CursorControllerApp mCursorController;

    /** Controller for speech feedback. */
    private SpeechController mSpeechController;

    /** Controller for audio and haptic feedback. */
    private FeedbackController mFeedbackController;

    /** Controller for reading the entire hierarchy. */
    private FullScreenReadControllerApp mFullScreenReadController;

    /** Controller for monitoring current and previous cursor position in editable node */
    private TextCursorController mTextCursorController;

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

    /** Processor for moving access focus. Used in Jelly Bean and above. */
    private ProcessorFocusAndSingleTap mProcessorFollowFocus;

    /** Orientation monitor for watching orientation changes. */
    private OrientationMonitor mOrientationMonitor;

    /** {@link BroadcastReceiver} for tracking the ringer and screen states. */
    private RingerModeAndScreenMonitor mRingerModeAndScreenMonitor;

    /** {@link BroadcastReceiver} for tracking the call state. */
    private CallStateMonitor mCallStateMonitor;

    /** {@link BroadcastReceiver} for tracking volume changes. */
    private VolumeMonitor mVolumeMonitor;

    /** {@link android.content.BroadcastReceiver} for tracking battery status changes. */
    private BatteryMonitor mBatteryMonitor;

    /** Manages screen dimming */
    private DimScreenController mDimScreenController;

    /** Whether the current device is a television (Android TV). */
    private boolean mIsDeviceTelevision;

    /** The television controller; non-null if the device is a television (Android TV). */
    private TelevisionNavigationController mTelevisionNavigationController;

    /**
     * {@link BroadcastReceiver} for tracking package removals for custom label
     * data consistency.
     */
    private PackageRemovalReceiver mPackageReceiver;

    /** The analytics instance, used for sending data to Google Analytics. */
    private Analytics mAnalytics;

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

    /**
     * Whether the current root node is dirty or not.
     **/
    private boolean mIsRootNodeDirty = true;
    /**
     * Keep Track of current root node.
     */
    private AccessibilityNodeInfo mRootNode;

    private AccessibilityEventProcessor mAccessibilityEventProcessor;

    /** Keeps track of whether we need to run the locked-boot-completed callback when connected. */
    private boolean mLockedBootCompletedPending;

    private final InputModeManager mInputModeManager = new InputModeManager();

    private ProcessorScreen mProcessorScreen;

    @Override
    public void onCreate() {
        super.onCreate();

        sInstance = this;
        setServiceState(SERVICE_STATE_INACTIVE);

        SharedPreferencesUtils.migrateSharedPreferences(this);

        mPrefs = SharedPreferencesUtils.getSharedPreferences(this);

        mSystemUeh = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(this);

        mAccessibilityEventProcessor = new AccessibilityEventProcessor(this);
        initializeInfrastructure();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if (isServiceActive()) {
            suspendInfrastructure();
        }

        sInstance = null;

        // Shutdown and unregister all components.
        shutdownInfrastructure();
        setServiceState(SERVICE_STATE_INACTIVE);
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
        mAccessibilityEventProcessor.onAccessibilityEvent(event);
    }

    public boolean supportsTouchScreen() {
        return mSupportsTouchScreen;
    }

    @Override
    public AccessibilityNodeInfo getRootInActiveWindow() {
        if(mIsRootNodeDirty || mRootNode == null) {
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

    @Override
    public AccessibilityNodeInfo findFocus(int focus) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            return super.findFocus(focus);
        } else {
            AccessibilityNodeInfo root = getRootInActiveWindow();
            return root == null ? null : root.findFocus(focus);
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

    /**
     * Suspends TalkBack, showing a confirmation dialog if applicable.
     */
    public void requestSuspendTalkBack() {
        final boolean showConfirmation = SharedPreferencesUtils.getBooleanPref(mPrefs,
                getResources(), R.string.pref_show_suspension_confirmation_dialog,
                R.bool.pref_show_suspension_confirmation_dialog_default);
        if (showConfirmation) {
            confirmSuspendTalkBack();
        } else {
            suspendTalkBack();
        }
    }

    /**
     * Shows a dialog asking the user to confirm suspension of TalkBack.
     */
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
        @SuppressLint("InflateParams") final ScrollView root = (ScrollView) inflater.inflate(
                R.layout.suspend_talkback_dialog, null);
        final CheckBox confirmCheckBox = (CheckBox) root.findViewById(R.id.show_warning_checkbox);
        final TextView message = (TextView) root.findViewById(R.id.message_resume);

        final DialogInterface.OnClickListener okayClick = new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                if (which == DialogInterface.BUTTON_POSITIVE) {
                    if (!confirmCheckBox.isChecked()) {
                        SharedPreferencesUtils.putBooleanPref(mPrefs, getResources(),
                                R.string.pref_show_suspension_confirmation_dialog, false);
                    }

                    suspendTalkBack();
                }
            }
        };

        final OnDismissListener onDismissListener = new OnDismissListener() {
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

        mSuspendDialog = new AlertDialog.Builder(this)
                .setTitle(R.string.dialog_title_suspend_talkback)
                .setView(root)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok, okayClick)
                .create();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            mSuspendDialog.getWindow().setType(
                    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY);
        } else {
            mSuspendDialog.getWindow().setType(WindowManager.LayoutParams.TYPE_SYSTEM_ERROR);
        }

        mSuspendDialog.setOnDismissListener(onDismissListener);
        mSuspendDialog.show();
    }

    /**
     * Suspends TalkBack and Explore by Touch.
     */
    public void suspendTalkBack() {
        if (!isServiceActive()) {
            if (LogUtils.LOG_LEVEL <= Log.ERROR) {
                Log.e(LOGTAG, "Attempted to suspend TalkBack while already suspended.");
            }
            return;
        }

        SharedPreferencesUtils.storeBooleanAsync(mPrefs, getString(R.string.pref_suspended), true);
        mFeedbackController.playAuditory(R.raw.paused_feedback);

        if (mSupportsTouchScreen) {
            requestTouchExploration(false);
        }

        if (mCursorController != null) {
            try {
                mCursorController.clearCursor();
            } catch (SecurityException e) {
                if (LogUtils.LOG_LEVEL >= Log.ERROR) {
                    Log.e(LOGTAG, "Unable to clear cursor");
                }
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
        final Notification notification = new NotificationCompat.Builder(this)
                .setContentTitle(getString(R.string.notification_title_talkback_suspended))
                .setContentText(getString(R.string.notification_message_talkback_suspended))
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setSmallIcon(R.drawable.ic_stat_info)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setWhen(0)
                .build();

        startForeground(R.id.notification_suspended, notification);

        mSpeechController.speak(getString(R.string.talkback_suspended),
                SpeechController.QUEUE_MODE_UNINTERRUPTIBLE, 0, null);
    }

    public void disableTalkBack() {
        if (isServiceActive()) {
            if (mSupportsTouchScreen) {
                requestTouchExploration(false);
            }
            AccessibilityTutorialActivity.stopActiveTutorial();
            suspendInfrastructure();

            // OK to clear completely since TalkBack is about to go away.
            mServiceStateListeners.clear();
        }

        TalkBackService.getInstance().getSpeechController().speak(
                getString(R.string.talkback_disabled), null, null,
                SpeechController.QUEUE_MODE_UNINTERRUPTIBLE, 0,
                SpeechController.UTTERANCE_GROUP_DEFAULT, null, null,
                mDisableTalkBackHandler);
    }

    /**
     * Resumes TalkBack and Explore by Touch.
     */
    public void resumeTalkBack() {
        if (isServiceActive()) {
            if (LogUtils.LOG_LEVEL <= Log.ERROR) {
                Log.e(LOGTAG, "Attempted to resume TalkBack when not suspended.");
            }
            return;
        }

        SharedPreferencesUtils.storeBooleanAsync(mPrefs, getString(R.string.pref_suspended), false);

        unregisterReceiver(mSuspendedReceiver);
        resumeInfrastructure();

        mSpeechController.speak(getString(R.string.talkback_resumed),
                SpeechController.QUEUE_MODE_UNINTERRUPTIBLE, 0, null);
    }

    /**
     * Intended to mimic the behavior of onKeyEvent if this were the only service running.
     * It will be called from onKeyEvent, both from this service and from others in this apk
     * (TalkBack). This method must not block, since it will block onKeyEvent as well.
     * @param keyEvent A key event
     * @return {@code true} if the event is handled, {@code false} otherwise.
     */
    public boolean onKeyEventShared(KeyEvent keyEvent) {
        for (KeyEventListener listener : mKeyEventListeners) {
            if (!isServiceActive() && !listener.processWhenServiceSuspended()) {
                continue;
            }

            if (listener.onKeyEvent(keyEvent)) {
                return true;
            }
        }

        return false;
    }

    @Override
    protected boolean onKeyEvent(KeyEvent keyEvent) {
        boolean keyHandled = onKeyEventShared(keyEvent);
        if (!BuildCompat.isAtLeastN()) {
            SwitchAccessService switchAccessService = SwitchAccessService.getInstance();
            if (switchAccessService != null) {
                keyHandled = switchAccessService.onKeyEventShared(keyEvent) || keyHandled;
            }
        }
        return keyHandled;
    }

    @Override
    protected boolean onGesture(int gestureId) {
        if (!isServiceActive()) return false;

        if (LogUtils.LOG_LEVEL <= Log.VERBOSE) {
            Log.v(LOGTAG, String.format("Recognized gesture %s", gestureId));
        }

        if (mKeyboardSearchManager != null && mKeyboardSearchManager.onGesture()) return true;
        mAnalytics.onGesture(gestureId);
        mFeedbackController.playAuditory(R.raw.gesture_end);

        // Gestures always stop global speech on API 16. On API 17+ we silence
        // on TOUCH_INTERACTION_START.
        // TODO: Will this negatively affect something like Books?
        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.JELLY_BEAN) {
            interruptAllFeedback();
        }

        mMenuManager.onGesture(gestureId);
        mGestureController.onGesture(gestureId);
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

    public CursorController getCursorController() {
        if (mCursorController == null) {
            throw new RuntimeException("mCursorController has not been initialized");
        }

        return mCursorController;
    }

    public TextCursorController getTextCursorController() {
        if (mTextCursorController == null) {
            throw new RuntimeException("mTextCursorController has not been initialized");
        }

        return mTextCursorController;
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
        if (mLabelManager == null && Build.VERSION.SDK_INT >= CustomLabelManager.MIN_API_LEVEL) {
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

    public CallStateMonitor getCallStateMonitor() {
        if (mCallStateMonitor == null) {
            throw new RuntimeException("mCallStateMonitor has not been initialized");
        }

        return mCallStateMonitor;
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
     * Obtains the shared instance of TalkBack's {@link TelevisionNavigationController} if the
     * current device is a television. Otherwise returns {@code null}.
     */
    public TelevisionNavigationController getTelevisionNavigationController() {
        return mTelevisionNavigationController;
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
     * Reset the accessibility focus to the node that was focused during the last call to
     * {@link #saveFocusedNode()}
     */
    public void resetFocusedNode() {
        resetFocusedNode(0);
    }

    public void resetFocusedNode(long delay) {
        final Handler handler = new Handler();
        handler.postDelayed(new Runnable() {
            @SuppressLint("InlinedApi")
            @Override
            public void run() {
                AccessibilityNodeInfoCompat node = mSavedNode.getNode();
                if (node == null) {
                    return;
                }

                AccessibilityNodeInfoCompat refreshed =
                        AccessibilityNodeInfoUtils.refreshNode(node);

                if (refreshed != null) {
                    if (!refreshed.isAccessibilityFocused()) {
                        // Restore accessibility focus.
                        mCursorController.setGranularity(mSavedNode.getGranularity(),
                                refreshed, false);
                        mSavedNode.restoreTextAndSelection();

                        PerformActionUtils.performAction(refreshed,
                                AccessibilityNodeInfoCompat.ACTION_ACCESSIBILITY_FOCUS);
                    } else if (BuildCompat.isAtLeastN() && mSavedNode.getAnchor() != null &&
                            refreshed.getWindow() == null) {
                        // The node is anchored, but its window has disappeared.
                        // We need to wait for the node's window to appear, and then do a fuzzy-find
                        // to place accessibility focus on the node's replacement.
                        mCursorController.setGranularity(mSavedNode.getGranularity(),
                                refreshed, false);
                        mSavedNode.restoreTextAndSelection();

                        resetFocusedNodeInAnchoredWindow(
                                mSavedNode.getNode(),
                                mSavedNode.getAnchor(),
                                250 /* ms */);
                    }

                    refreshed.recycle();
                }

                mSavedNode.recycle();
            }
        }, delay);
    }

    /**
     * Resets the accessibility focus to an item inside an anchored window.
     * We must delay slightly in order for the anchored window to reappear in the windows list
     * before attempting to place the accessibility focus. Furthermore, we cannot find the exact
     * previously-focused item again; we must do a fuzzy search for it instead.
     */
    private void resetFocusedNodeInAnchoredWindow(AccessibilityNodeInfoCompat node,
            AccessibilityNodeInfoCompat anchor,
            long delay) {
        if (node == null || anchor == null) {
            return;
        }

        final AccessibilityNodeInfoCompat obtainedNode = AccessibilityNodeInfoCompat.obtain(node);
        final AccessibilityNodeInfoCompat obtainedAnchor =
                AccessibilityNodeInfoCompat.obtain(anchor);

        final Handler handler = new Handler();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                com.android.utils.WindowManager windowManager =
                        new com.android.utils.WindowManager(isScreenLayoutRTL());
                windowManager.setWindows(getWindows());

                AccessibilityWindowInfo anchoredWindow =
                        windowManager.getAnchoredWindow(obtainedAnchor);
                AccessibilityNodeInfoCompat refreshed =
                        AccessibilityNodeInfoUtils.refreshNodeFuzzy(obtainedNode, anchoredWindow);
                if (refreshed != null) {
                    PerformActionUtils.performAction(refreshed,
                            AccessibilityNodeInfoCompat.ACTION_ACCESSIBILITY_FOCUS);
                    refreshed.recycle();
                }
                obtainedNode.recycle();
                obtainedAnchor.recycle();
            }
        }, delay);
    }

    private void showGlobalContextMenu() {
        if (mSupportsTouchScreen) {
            mMenuManager.showMenu(R.menu.global_context_menu);
        }
    }

    private void showLocalContextMenu() {
        if (mSupportsTouchScreen) {
            mMenuManager.showMenu(R.menu.local_context_menu);
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
        if (mProcessorScreen != null && isInArc()) {
            // In Arc, we consider that focus goes out from Arc when onInterrupt is called.
            mProcessorScreen.clearScreenState();
        }

        interruptAllFeedback();
    }

    public void interruptAllFeedback() {
        interruptAllFeedback(false);
    }

    public void interruptAllFeedback(boolean isSuspending) {
        // Don't interrupt feedback if the tutorial is active unless it's triggered by
        // suspendInfrastructure()
        if (!isSuspending && AccessibilityTutorialActivity.isTutorialActive()) {
            return;
        }

        // If this method is not called from suspendInfrastructure(), instruct ChromeVox to stop
        // speech and halt any automatic actions.
        if (!isSuspending && mCursorController != null) {
            final AccessibilityNodeInfoCompat currentNode = mCursorController.getCursor();
            if (currentNode != null && WebInterfaceUtils.hasLegacyWebContent(currentNode)) {
                if (WebInterfaceUtils.isScriptInjectionEnabled(this)) {
                    WebInterfaceUtils.performSpecialAction(
                            currentNode, WebInterfaceUtils.ACTION_STOP_SPEECH);
                }
            }
        }

        if (mFullScreenReadController != null) {
            mFullScreenReadController.interrupt();
        }

        if (mSpeechController != null) {
            mSpeechController.interrupt();
        }

        if (mFeedbackController != null) {
            mFeedbackController.interrupt();
        }
    }

    @Override
    protected void onServiceConnected() {
        if (LogUtils.LOG_LEVEL <= Log.VERBOSE) {
            Log.v(LOGTAG, "System bound to service.");
        }
        resumeInfrastructure();

        // Handle any update actions.
        final TalkBackUpdateHelper helper = new TalkBackUpdateHelper(this);
        helper.showPendingNotifications();
        helper.checkUpdate();

        final ContentResolver resolver = getContentResolver();
        if (!TalkBackPreferencesActivity.TalkBackPreferenceFragment.isTouchExplorationEnabled(
                resolver) || !showTutorial()) {
                startCallStateMonitor();
        }

        if (mPrefs.getBoolean(getString(R.string.pref_suspended), false)) {
            suspendTalkBack();
        } else {
            if (!isInArc()) {
                mSpeechController.speak(getString(R.string.talkback_on),
                        SpeechController.QUEUE_MODE_UNINTERRUPTIBLE, 0, null);
            }
        }

        // If the locked-boot-completed intent was fired before onServiceConnected, we queued it,
        // so now we need to run it.
        if (mLockedBootCompletedPending) {
            onLockedBootCompletedInternal();
            mLockedBootCompletedPending = false;
        }
    }

    /**
     * @return The current state of the TalkBack service, or
     *         {@code INACTIVE} if the service is not initialized.
     */
    public static int getServiceState() {
        final TalkBackService service = getInstance();
        if (service == null) {
            return SERVICE_STATE_INACTIVE;
        }

        return service.mServiceState;
    }

    /**
     * Whether the current TalkBackService instance is running and initialized.
     * This method is useful for testing because it can be overridden by mocks.
     */
    public boolean isInstanceActive() {
        return mServiceState == SERVICE_STATE_ACTIVE;
    }

    /**
     * @return {@code true} if TalkBack is running and initialized,
     *         {@code false} otherwise.
     */
    public static boolean isServiceActive() {
        return (getServiceState() == SERVICE_STATE_ACTIVE);
    }

    /**
     * Returns the active TalkBack instance, or {@code null} if not available.
     */
    public static TalkBackService getInstance() {
        return sInstance;
    }


    /**
     * Initializes the controllers, managers, and processors. This should only
     * be called once from {@link #onCreate}.
     */
    private void initializeInfrastructure() {
        // Initialize static instances that do not have dependencies.
        NodeSpeechRuleProcessor.initialize(this);

        // Assume that a device cannot become a TV or stop being a TV while running.
        mIsDeviceTelevision = TelevisionNavigationController.isContextTelevision(this);

        final PackageManager packageManager = getPackageManager();
        final boolean deviceIsPhone = packageManager.hasSystemFeature(
                PackageManager.FEATURE_TELEPHONY);
        //TODO we still need it keep true for TV until TouchExplore and Accessibility focus is not
        //unpaired
        //mSupportsTouchScreen = packageManager.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN);

        // Only initialize telephony and call state for phones.
        if (deviceIsPhone) {
            mCallStateMonitor = new CallStateMonitor(this);
            mAccessibilityEventProcessor.setCallStateMonitor(mCallStateMonitor);
        }

        mCursorController = new CursorControllerApp(this);
        addEventListener(mCursorController);

        mFeedbackController = new FeedbackControllerApp(this);

        mFullScreenReadController = new FullScreenReadControllerApp(mFeedbackController,
                mCursorController, this);
        addEventListener(mFullScreenReadController);

        mSpeechController = new SpeechController(this, mFeedbackController);
        mKeyEventListeners.add(mSpeechController);

        mShakeDetector = new ShakeDetector(mFullScreenReadController, this);

        mMenuManager = new MenuManagerWrapper();
        updateMenuManagerFromPreferences(); // Sets mMenuManager

        mRingerModeAndScreenMonitor = new RingerModeAndScreenMonitor(mFeedbackController,
                mMenuManager, mShakeDetector, mSpeechController, this);
        mAccessibilityEventProcessor.setRingerModeAndScreenMonitor(mRingerModeAndScreenMonitor);

        mGestureController = new GestureControllerApp(this,
                mCursorController, mFeedbackController, mFullScreenReadController, mMenuManager);

        mSideTapManager = new SideTapManager(this, mGestureController);
        addEventListener(mSideTapManager);
        mFeedbackController.addHapticFeedbackListener(mSideTapManager);

        mTextCursorController = new TextCursorControllerApp();
        addEventListener(mTextCursorController);

        // Add event processors. These will process incoming AccessibilityEvents
        // in the order they are added.
        ProcessorEventQueue processorEventQueue = new ProcessorEventQueue(mSpeechController, this);
        processorEventQueue.setTestingListener(mAccessibilityEventProcessor.getTestingListener());
        mAccessibilityEventProcessor.setProcessorEventQueue(processorEventQueue);

        addEventListener(processorEventQueue);
        addEventListener(new ProcessorScrollPosition(mFullScreenReadController,
                mSpeechController, mCursorController, this));
        addEventListener(new ProcessorAccessibilityHints(this, mSpeechController));
        addEventListener(new ProcessorPhoneticLetters(this, mSpeechController));
        mProcessorScreen = new ProcessorScreen(this);
        addEventListener(mProcessorScreen);

        mProcessorFollowFocus = new ProcessorFocusAndSingleTap(
                mCursorController, mFeedbackController, mSpeechController, this);
        mAccessibilityEventProcessor.setProcessorFocusAndSingleTap(mProcessorFollowFocus);
        addEventListener(mProcessorFollowFocus);
        if (mCursorController != null) {
            mCursorController.addScrollListener(mProcessorFollowFocus);
        }

        mVolumeMonitor = new VolumeMonitor(mSpeechController, this);
        mBatteryMonitor = new BatteryMonitor(this, mSpeechController,
                (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE));

        if (Build.VERSION.SDK_INT >= PackageRemovalReceiver.MIN_API_LEVEL) {
            // TODO: Move this into the custom label manager code
            mPackageReceiver = new PackageRemovalReceiver();
        }

        if (Build.VERSION.SDK_INT >= ProcessorGestureVibrator.MIN_API_LEVEL) {
            addEventListener(new ProcessorGestureVibrator(mFeedbackController));
        }

        addEventListener(new ProcessorWebContent(this));

        DimScreenControllerApp dimScreenController = new DimScreenControllerApp(this);
        mDimScreenController = dimScreenController;

        if (Build.VERSION.SDK_INT >= ProcessorVolumeStream.MIN_API_LEVEL) {
            ProcessorVolumeStream processorVolumeStream =
                    new ProcessorVolumeStream(mFeedbackController, mCursorController,
                            mDimScreenController, this);
            addEventListener(processorVolumeStream);
            mKeyEventListeners.add(processorVolumeStream);
        }

        if (Build.VERSION.SDK_INT >= CustomLabelManager.MIN_API_LEVEL) {
            mLabelManager = new CustomLabelManager(this);
        }

        mKeyComboManager =  KeyComboManager.create(this);
        if (mKeyComboManager != null) {
            mKeyComboManager.addListener(mKeyComboListener);
            // Search mode should receive key combos immediately after the TalkBackService.
            if (Build.VERSION.SDK_INT >= KeyboardSearchManager.MIN_API_LEVEL) {
                mKeyboardSearchManager = new KeyboardSearchManager(this, mLabelManager);
                mKeyEventListeners.add(mKeyboardSearchManager);
                addEventListener(mKeyboardSearchManager);
                mKeyComboManager.addListener(mKeyboardSearchManager);
            }
            mKeyComboManager.addListener(mCursorController);
            mKeyEventListeners.add(mKeyComboManager);
            mServiceStateListeners.add(mKeyComboManager);
        }

        addEventListener(mSavedNode);

        mOrientationMonitor = new OrientationMonitor(mSpeechController, this);
        mOrientationMonitor.addOnOrientationChangedListener(dimScreenController);

        KeyboardLockMonitor keyboardLockMonitor = new KeyboardLockMonitor(mSpeechController, this);
        mKeyEventListeners.add(keyboardLockMonitor);

        if (Build.VERSION.SDK_INT >= TelevisionNavigationController.MIN_API_LEVEL &&
                isDeviceTelevision()) {
            mTelevisionNavigationController = new TelevisionNavigationController(this);
            mKeyEventListeners.add(mTelevisionNavigationController);
        }

        mAnalytics = new TalkBackAnalytics(this);
    }

    public void updateMenuManagerFromPreferences() {
        mMenuManager.dismissAll();
        MenuManager menuManager;

        if (SharedPreferencesUtils.getBooleanPref(mPrefs, getResources(),
                R.string.pref_show_context_menu_as_list_key, R.bool.pref_show_menu_as_list)) {
            menuManager = new ListMenuManager(this);
        } else {
            menuManager = new RadialMenuManager(mSupportsTouchScreen, this);
        }

        setMenuManager(menuManager);
    }

    public void setMenuManager(MenuManager menuManager) {
        mMenuManager.setMenuManager(menuManager);
    }

    public MenuManager getMenuManager() {
        return mMenuManager;
    }

    /**
     * Registers listeners, sets service info, loads preferences. This should be
     * called from {@link #onServiceConnected} and when TalkBack resumes from a
     * suspended state.
     */
    private void resumeInfrastructure() {
        if (isServiceActive()) {
            if (LogUtils.LOG_LEVEL <= Log.ERROR) {
                Log.e(LOGTAG, "Attempted to resume while not suspended");
            }
            return;
        }

        setServiceState(SERVICE_STATE_ACTIVE);
        stopForeground(true);

        final AccessibilityServiceInfo info = new AccessibilityServiceInfo();
        info.eventTypes = AccessibilityEvent.TYPES_ALL_MASK;
        info.feedbackType |= AccessibilityServiceInfo.FEEDBACK_SPOKEN;
        info.feedbackType |= AccessibilityServiceInfo.FEEDBACK_AUDIBLE;
        info.feedbackType |= AccessibilityServiceInfo.FEEDBACK_HAPTIC;
        info.flags |= AccessibilityServiceInfo.DEFAULT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            info.flags |= AccessibilityServiceInfo.FLAG_REQUEST_ENHANCED_WEB_ACCESSIBILITY;
            info.flags |= AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS;
            info.flags |= AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            info.flags |= AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
        }
        info.notificationTimeout = 0;

        // Ensure the initial touch exploration request mode is correct.
        if (mSupportsTouchScreen && SharedPreferencesUtils.getBooleanPref(
                mPrefs, getResources(), R.string.pref_explore_by_touch_key,
                R.bool.pref_explore_by_touch_default)) {
            info.flags |= AccessibilityServiceInfo.FLAG_REQUEST_TOUCH_EXPLORATION_MODE;
        }

        setServiceInfo(info);

        if (mRingerModeAndScreenMonitor != null) {
            registerReceiver(mRingerModeAndScreenMonitor, mRingerModeAndScreenMonitor.getFilter());
            // It could now be confused with the current screen state
            mRingerModeAndScreenMonitor.updateScreenState();
        }

        if (mVolumeMonitor != null) {
            registerReceiver(mVolumeMonitor, mVolumeMonitor.getFilter());
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

        // Add the broadcast listener for gestures.
        final IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_PERFORM_GESTURE_ACTION);
        registerReceiver(mActiveReceiver, filter, PERMISSION_TALKBACK, null);

        // Enable the proxy activity for long-press search.
        final PackageManager packageManager = getPackageManager();
        final ComponentName shortcutProxy = new ComponentName(this, ShortcutProxyActivity.class);
        packageManager.setComponentEnabledSetting(shortcutProxy,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP);

        reloadPreferences();

        mDimScreenController.resume();
    }

    /**
     * Registers listeners, sets service info, loads preferences. This should be called from
     * {@link #onServiceConnected} and when TalkBack resumes from a suspended state.
     */
    private void suspendInfrastructure() {
        if (!isServiceActive()) {
            if (LogUtils.LOG_LEVEL <= Log.ERROR) {
                Log.e(LOGTAG, "Attempted to suspend while already suspended");
            }
            return;
        }

        mDimScreenController.suspend();

        interruptAllFeedback(true);
        setServiceState(SERVICE_STATE_SUSPENDED);

        // Some apps depend on these being set to false when TalkBack is disabled.
        if (mSupportsTouchScreen) {
            requestTouchExploration(false);
        }

        if (SUPPORTS_WEB_SCRIPT_TOGGLE) {
            requestWebScripts(false);
        }

        mPrefs.unregisterOnSharedPreferenceChangeListener(mSharedPreferenceChangeListener);

        unregisterReceiver(mActiveReceiver);

        if (mCallStateMonitor != null) {
            mCallStateMonitor.stopMonitor();
        }

        if (mRingerModeAndScreenMonitor != null) {
            unregisterReceiver(mRingerModeAndScreenMonitor);
        }

        if (mMenuManager != null) {
            mMenuManager.clearCache();
        }

        if (mVolumeMonitor != null) {
            unregisterReceiver(mVolumeMonitor);
            mVolumeMonitor.releaseControl();
        }

        if (mBatteryMonitor != null) {
            unregisterReceiver(mBatteryMonitor);
        }

        if (mPackageReceiver != null) {
            unregisterReceiver(mPackageReceiver);
        }

        if (mShakeDetector != null) {
            mShakeDetector.setEnabled(false);
        }

        // The tap detector is enabled through reloadPreferences
        if (mSideTapManager != null) {
            unregisterReceiver(mSideTapManager);
            mSideTapManager.onSuspendInfrastructure();
        }

        // Disable the proxy activity for long-press search.
        final PackageManager packageManager = getPackageManager();
        final ComponentName shortcutProxy = new ComponentName(this, ShortcutProxyActivity.class);
        packageManager.setComponentEnabledSetting(shortcutProxy,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP);

        // Remove any pending notifications that shouldn't persist.
        final NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        nm.cancelAll();
    }

    /**
     * Shuts down the infrastructure in case it has been initialized.
     */
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

        mFeedbackController.shutdown();
        mSpeechController.shutdown();
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
     * Posts a {@link Runnable} to removes an event listener. This is safe to
     * call from inside {@link AccessibilityEventListener#onAccessibilityEvent(AccessibilityEvent)}.
     *
     * @param listener The listener to remove.
     */
    public void postRemoveEventListener(final AccessibilityEventListener listener) {
        mAccessibilityEventProcessor.postRemoveAccessibilityEventListener(listener);
    }

    /**
     * Reloads service preferences.
     */
    private void reloadPreferences() {
        final Resources res = getResources();

        mAccessibilityEventProcessor.setSpeakWhenScreenOff(SharedPreferencesUtils.getBooleanPref(
                mPrefs, res, R.string.pref_screenoff_key, R.bool.pref_screenoff_default));

        mAutomaticResume = mPrefs.getString(res.getString(R.string.pref_resume_talkback_key),
                getString(R.string.resume_screen_on));

        final boolean silenceOnProximity = SharedPreferencesUtils.getBooleanPref(
                mPrefs, res, R.string.pref_proximity_key, R.bool.pref_proximity_default);
        mSpeechController.setSilenceOnProximity(silenceOnProximity);

        LogUtils.setLogLevel(
                SharedPreferencesUtils.getIntFromStringPref(mPrefs, res, R.string.pref_log_level_key, R.string.pref_log_level_default));

        if (mProcessorFollowFocus != null) {
            final boolean useSingleTap = SharedPreferencesUtils.getBooleanPref(
                    mPrefs, res, R.string.pref_single_tap_key, R.bool.pref_single_tap_default);

            mProcessorFollowFocus.setSingleTapEnabled(useSingleTap);

            // Update the "X to activate" long-hover hint.
            NodeHintRule.NodeHintHelper.updateHints(useSingleTap, isDeviceTelevision());
        }

        if (mShakeDetector != null) {
            final int shakeThreshold = SharedPreferencesUtils.getIntFromStringPref(
                    mPrefs, res, R.string.pref_shake_to_read_threshold_key,
                    R.string.pref_shake_to_read_threshold_default);
            final boolean useShake = (shakeThreshold > 0) && ((mCallStateMonitor == null) || (
                    mCallStateMonitor.getCurrentCallState() == TelephonyManager.CALL_STATE_IDLE));

            mShakeDetector.setEnabled(useShake);
        }

        if (mSideTapManager != null) {
            mSideTapManager.onReloadPreferences();
        }

        if (mSupportsTouchScreen) {
            // Touch exploration *must* be enabled on TVs for TalkBack to function.
            final boolean touchExploration = isDeviceTelevision() ||
                    SharedPreferencesUtils.getBooleanPref(mPrefs, res,
                            R.string.pref_explore_by_touch_key,
                            R.bool.pref_explore_by_touch_default);
            requestTouchExploration(touchExploration);
        }

        if (SUPPORTS_WEB_SCRIPT_TOGGLE) {
            final boolean requestWebScripts = SharedPreferencesUtils.getBooleanPref(mPrefs, res,
                    R.string.pref_web_scripts_key, R.bool.pref_web_scripts_default);
            requestWebScripts(requestWebScripts);
        }

        updateMenuManagerFromPreferences();
    }

    /**
     * Attempts to change the state of touch exploration.
     * <p>
     * Should only be called if {@link #mSupportsTouchScreen} is true.
     *
     * @param requestedState {@code true} to request exploration.
     */
    private void requestTouchExploration(boolean requestedState) {
        final AccessibilityServiceInfo info = getServiceInfo();
        if (info == null) {
            if (LogUtils.LOG_LEVEL <= Log.ERROR) {
                Log.e(LOGTAG,
                        "Failed to change touch exploration request state, service info was null");
            }
            return;
        }

        final boolean currentState = (
                (info.flags & AccessibilityServiceInfo.FLAG_REQUEST_TOUCH_EXPLORATION_MODE) != 0);
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
     */
    public boolean showTutorial() {
        if (isInArc()) {
            return false;
        }

        boolean isDeviceProvisioned = true;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            isDeviceProvisioned = Settings.Secure
                    .getInt(getContentResolver(), Settings.Global.DEVICE_PROVISIONED, 1) != 0;
        }

        if (isDeviceProvisioned && !isFirstTimeUser()) {
            return false;
        }

        final int touchscreenState = getResources().getConfiguration().touchscreen;

        if (touchscreenState != Configuration.TOUCHSCREEN_NOTOUCH
                && mSupportsTouchScreen) {
            final Intent tutorial = new Intent(this, AccessibilityTutorialActivity.class);
            tutorial.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            tutorial.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(tutorial);
            return true;
        }

        return false;
    }

    private boolean isFirstTimeUser() {
        return mPrefs.getBoolean(PREF_FIRST_TIME_USER, true);
    }

    public void startCallStateMonitor() {
        if (mCallStateMonitor == null || mCallStateMonitor.isStarted()) {
            return;
        }

        mCallStateMonitor.startMonitor();
    }

    /**
     * Attempts to change the state of web script injection.
     * <p>
     * Should only be called if {@link #SUPPORTS_WEB_SCRIPT_TOGGLE} is true.
     *
     * @param requestedState {@code true} to request script injection,
     *            {@code false} otherwise.
     */
    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
    private void requestWebScripts(boolean requestedState) {
        final AccessibilityServiceInfo info = getServiceInfo();
        if (info == null) {
            if (LogUtils.LOG_LEVEL <= Log.ERROR) {
                Log.e(LOGTAG, "Failed to change web script injection request state, service info "
                        + "was null");
            }
            return;
        }

        final boolean currentState = (
                (info.flags & AccessibilityServiceInfo.FLAG_REQUEST_ENHANCED_WEB_ACCESSIBILITY)
                != 0);
        if (currentState == requestedState) {
            return;
        }

        if (requestedState) {
            info.flags |= AccessibilityServiceInfo.FLAG_REQUEST_ENHANCED_WEB_ACCESSIBILITY;
        } else {
            info.flags &= ~AccessibilityServiceInfo.FLAG_REQUEST_ENHANCED_WEB_ACCESSIBILITY;
        }

        setServiceInfo(info);
    }

    private final KeyComboManager.KeyComboListener mKeyComboListener =
            new KeyComboManager.KeyComboListener() {
        @Override
        public boolean onComboPerformed(int id) {
            switch (id) {
                case KeyComboManager.ACTION_SUSPEND_OR_RESUME:
                    if (mServiceState == SERVICE_STATE_SUSPENDED) {
                        resumeTalkBack();
                    } else if (mServiceState == SERVICE_STATE_ACTIVE) {
                        requestSuspendTalkBack();
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
                    mCursorController.nextGranularity();
                    return true;
                case KeyComboManager.ACTION_GRANULARITY_DECREASE:
                    mCursorController.previousGranularity();
                    return true;
                case KeyComboManager.ACTION_READ_FROM_TOP:
                    mFullScreenReadController.startReadingFromBeginning();
                    return true;
                case KeyComboManager.ACTION_READ_FROM_NEXT_ITEM:
                    mFullScreenReadController.startReadingFromNextNode();
                    return true;
                case KeyComboManager.ACTION_GLOBAL_CONTEXT_MENU:
                    showGlobalContextMenu();
                    return true;
                case KeyComboManager.ACTION_LOCAL_CONTEXT_MENU:
                    showLocalContextMenu();
                    return true;
                case KeyComboManager.ACTION_OPEN_MANAGE_KEYBOARD_SHORTCUTS:
                    openManageKeyboardShortcuts();
                    return true;
                case KeyComboManager.ACTION_OPEN_TALKBACK_SETTINGS:
                    openTalkBackSettings();
                    return true;
            }

            return false;
        }
    };

    /**
     * Reloads preferences whenever their values change.
     */
    private final OnSharedPreferenceChangeListener
            mSharedPreferenceChangeListener = new OnSharedPreferenceChangeListener() {
        @Override
        public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
            if (LogUtils.LOG_LEVEL <= Log.DEBUG) {
                Log.d(LOGTAG, "A shared preference changed: " + key);
            }
            reloadPreferences();
        }
    };

    /**
     * Broadcast receiver for actions that happen while the service is active.
     */
    private final BroadcastReceiver mActiveReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();

            if (ACTION_PERFORM_GESTURE_ACTION.equals(action)) {
                mGestureController.onGesture(
                        intent.getIntExtra(EXTRA_GESTURE_ACTION,
                                R.string.shortcut_value_unassigned));
            }
        }
    };

    /**
     * Broadcast receiver for actions that happen while the service is inactive.
     */
    private final BroadcastReceiver mSuspendedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();

            if (ACTION_RESUME_FEEDBACK.equals(action)) {
                resumeTalkBack();
            } else if (Intent.ACTION_SCREEN_ON.equals(action)) {
                if (mAutomaticResume.equals(getString(R.string.resume_screen_keyguard))) {
                    final KeyguardManager keyguard = (KeyguardManager) getSystemService(
                            Context.KEYGUARD_SERVICE);
                    if (keyguard.inKeyguardRestrictedInputMode()) {
                        resumeTalkBack();
                    }
                } else if (mAutomaticResume.equals(getString(R.string.resume_screen_on))) {
                    resumeTalkBack();
                }
            }
        }
    };

    private final SpeechController.UtteranceCompleteRunnable mDisableTalkBackHandler =
            new SpeechController.UtteranceCompleteRunnable() {
                @Override
                public void run(int status) {
                    // Double-check that TalkBack is still available before we try to disable it.
                    TalkBackService service = TalkBackService.getInstance();
                    if (service != null) {
                        service.disableSelf();
                    }
                }
            };

    public void onLockedBootCompleted() {
        if (mServiceState == SERVICE_STATE_INACTIVE) {
            // onServiceConnected has not completed yet. We need to defer the boot completion
            // callback until after onServiceConnected has run.
            mLockedBootCompletedPending = true;
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
        mSpeechController.updateTtsEngine(true /* quiet */);

        if (!isServiceActive() &&
                mAutomaticResume != null &&
                !mAutomaticResume.equals(getString(R.string.resume_screen_manual))) {
            resumeTalkBack();
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

    /**
     * Interface for receiving callbacks when the state of the TalkBack service
     * changes.
     * <p>
     * Implementing controllers should note that this may be invoked even after
     * the controller was explicitly shut down by TalkBack.
     * <p>
     * {@link TalkBackService#addServiceStateListener(ServiceStateListener)}
     * {@link TalkBackService#removeServiceStateListener(ServiceStateListener)}
     * {@link TalkBackService#SERVICE_STATE_INACTIVE}
     * {@link TalkBackService#SERVICE_STATE_ACTIVE}
     * {@link TalkBackService#SERVICE_STATE_SUSPENDED}
     */
    public interface ServiceStateListener {
        void onServiceStateChanged(int newState);
    }

    /**
     * Interface for key event listeners.
     */
    public interface KeyEventListener {
        boolean onKeyEvent(KeyEvent event);
        boolean processWhenServiceSuspended();
    }

    public boolean isScreenLayoutRTL() {
        Configuration config = getResources().getConfiguration();
        if (config == null) {
            return false;
        }
        return (config.screenLayout & Configuration.SCREENLAYOUT_LAYOUTDIR_MASK) ==
                Configuration.SCREENLAYOUT_LAYOUTDIR_RTL;
    }

    public boolean isScreenOrientationLandscape() {
        Configuration config = getResources().getConfiguration();
        if (config == null) {
            return false;
        }
        return config.orientation == Configuration.ORIENTATION_LANDSCAPE;
    }

    public boolean isDeviceTelevision() {
        return mIsDeviceTelevision;
    }

    public InputModeManager getInputModeManager() {
        return mInputModeManager;
    }

    public CharSequence getApplicationLabel(CharSequence packageName) {
        PackageManager packageManager = (PackageManager) getPackageManager();
        if (packageManager == null) {
            return null;
        }

        ApplicationInfo applicationInfo;
        try {
            applicationInfo = packageManager.getApplicationInfo(packageName.toString(),
                    0 /* no flag */);
        } catch (PackageManager.NameNotFoundException exception) {
            return null;
        }

        return packageManager.getApplicationLabel(applicationInfo);
    }

    public static boolean isInArc() {
        return Build.DEVICE != null && Build.DEVICE.matches(ARC_DEVICE_PATTERN);
    }

    public void notifyDumpEventPreferenceChanged(int eventType, boolean shouldDump) {
        mAccessibilityEventProcessor.onDumpEventPreferenceChanged(eventType, shouldDump);
    }
}
