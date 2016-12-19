/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.talkback.eventprocessor;

import android.os.Build;
import android.os.Handler;
import android.os.Message;
import android.support.v4.os.BuildCompat;
import android.support.v4.view.accessibility.AccessibilityEventCompat;
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import android.support.v4.view.accessibility.AccessibilityRecordCompat;
import android.support.v4.view.accessibility.AccessibilityWindowInfoCompat;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;

import com.android.talkback.controller.FeedbackController;
import com.android.talkback.keyboard.KeyComboModel;
import com.android.talkback.KeyComboManager;
import com.android.talkback.R;
import com.android.talkback.SpeechController;
import com.android.utils.AccessibilityEventListener;
import com.android.utils.AccessibilityEventUtils;
import com.android.utils.StringBuilderUtils;
import com.android.utils.WindowManager;
import com.google.android.marvin.talkback.TalkBackService;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

public class ProcessorScreen implements AccessibilityEventListener {

    private static final int WINDOW_ID_NONE = -1;
    private static final int WINDOW_TYPE_NONE = -1;
    private static final int SCREEN_FEEDBACK_DELAY = 500; // ms
    private static final boolean IS_IN_ARC = TalkBackService.isInArc();

    private class SpeechHandler extends Handler {

        public static final int MESSAGE_WHAT_SPEAK = 1;

        @Override
        public void handleMessage(Message message) {
            if (message.what != MESSAGE_WHAT_SPEAK || mUtterance == null) {
                return;
            }

            speak(mUtterance);
        }

    }

    private final Handler mDelayedSpeechHandler = new SpeechHandler();
    private final TalkBackService mService;
    private final boolean mIsSplitScreenModeAvailable;

    private CharSequence mUtterance;

    // TODO: Extract this to another class, and merge with the same logic in
    // TouchExplorationFormatter.
    private HashMap<Integer, CharSequence> mWindowTitlesMap = new HashMap<>();
    private HashMap<Integer, CharSequence> mWindowToClassName = new HashMap<>();
    private HashMap<Integer, CharSequence> mWindowToPackageName = new HashMap<>();

    private HashSet<Integer> mSystemWindowIdsSet = new HashSet<>();

    // Window A: In split screen mode, left (right in RTL) or top window. In full screen mode, the
    // current window.
    private int mWindowIdA = WINDOW_ID_NONE;

    // Window B: In split screen mode, right (left in RTL) or bottom window. This must be
    // WINDOW_ID_NONE in full screen mode.
    private int mWindowIdB = WINDOW_ID_NONE;

    // Accessibility overlay window
    private int mAccessibilityOverlayWindowId = WINDOW_ID_NONE;

    public ProcessorScreen(TalkBackService service) {
        mService = service;
        mIsSplitScreenModeAvailable = BuildCompat.isAtLeastN() && !service.isDeviceTelevision();
    }

    public void clearScreenState() {
        mWindowIdA = WINDOW_ID_NONE;
        mWindowIdB = WINDOW_ID_NONE;
        mAccessibilityOverlayWindowId = WINDOW_ID_NONE;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        int eventType = event.getEventType();
        if (eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
                eventType != AccessibilityEvent.TYPE_WINDOWS_CHANGED) {
            return;
        }

        int windowIdABefore = mWindowIdA;
        CharSequence windowTitleABefore = getWindowTitle(mWindowIdA);
        int windowIdBBefore = mWindowIdB;
        CharSequence windowTitleBBefore = getWindowTitle(mWindowIdB);
        int accessibilityOverlayWindowIdBefore = mAccessibilityOverlayWindowId;
        CharSequence accessibilityOverlayWindowTitleBefore =
                getWindowTitle(mAccessibilityOverlayWindowId);

        updateWindowTitlesMap(event);
        updateScreenState(event);

        // If there is no screen update, do not provide spoken feedback.
        if (windowIdABefore == mWindowIdA &&
                TextUtils.equals(windowTitleABefore, getWindowTitle(mWindowIdA)) &&
                windowIdBBefore == mWindowIdB &&
                TextUtils.equals(windowTitleBBefore, getWindowTitle(mWindowIdB)) &&
                accessibilityOverlayWindowIdBefore == mAccessibilityOverlayWindowId &&
                TextUtils.equals(accessibilityOverlayWindowTitleBefore,
                    getWindowTitle(mAccessibilityOverlayWindowId))) {
            return;
        }

        // If the user performs a cursor control(copy, paste, start selection mode, etc) in the
        // local context menu and lands back to the edit text, a TYPE_WINDOWS_CHANGED and a
        // TYPE_WINDOW_STATE_CHANGED events will be fired. We should skip these two events to
        // avoid announcing the window title.
        if (event.getEventType() == AccessibilityEvent.TYPE_WINDOWS_CHANGED &&
                EventState.getInstance().checkAndClearRecentEvent(EventState
                        .EVENT_SKIP_WINDOWS_CHANGED_PROCESSING_AFTER_CURSOR_CONTROL)) {
            return;
        }
        if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
                EventState.getInstance().checkAndClearRecentEvent(EventState
                        .EVENT_SKIP_WINDOW_STATE_CHANGED_PROCESSING_AFTER_CURSOR_CONTROL)) {
            return;
        }

        // Generate spoken feedback.
        CharSequence utterance;
        boolean isUiStabilized;
        if (mAccessibilityOverlayWindowId != WINDOW_ID_NONE) {
            // Case where accessibility overlay is shown. Use separated logic for accessibility
            // overlay not to say out of split screen mode, e.g. accessibility overlay is shown when
            // user is in split screen mode.
            utterance = getWindowTitleForFeedback(mAccessibilityOverlayWindowId);
            isUiStabilized = true;
        } else if (mWindowIdB == WINDOW_ID_NONE) {
            // Single window mode.
            CharSequence windowTitleA = getWindowTitle(mWindowIdA);
            if (windowTitleA == null) {
                // In single window mode, do not provide feedback if window title is not set.
                return;
            }

            utterance = getWindowTitleForFeedback(mWindowIdA);

            if (IS_IN_ARC) {
                // If windowIdABefore was WINDOW_ID_NONE, we consider it as the focus comes into Arc
                // window.
                utterance = formatAnnouncementForArc(utterance,
                        windowIdABefore == WINDOW_ID_NONE /* focusIntoArc */);
            }

            // Consider UI is stabilized if it's alert dialog to provide faster feedback.
            isUiStabilized = !mIsSplitScreenModeAvailable || isAlertDialog(mWindowIdA);
        } else {
            // Split screen mode.
            int feedbackTemplate;
            if (mService.isScreenOrientationLandscape()) {
                if (mService.isScreenLayoutRTL()) {
                    feedbackTemplate = R.string.template_split_screen_mode_landscape_rtl;
                } else {
                    feedbackTemplate = R.string.template_split_screen_mode_landscape_ltr;
                }
            } else {
                feedbackTemplate = R.string.template_split_screen_mode_portrait;
            }

            utterance = mService.getString(feedbackTemplate, getWindowTitleForFeedback(mWindowIdA),
                    getWindowTitleForFeedback(mWindowIdB));
            isUiStabilized = !mIsSplitScreenModeAvailable || isAlertDialog(mWindowIdA) ||
                    isAlertDialog(mWindowIdB);
        }

        // Speak.
        if (!isUiStabilized) {
            // If UI is not stabilized, wait SCREEN_FEEDBACK_DELAY for next accessibility event.
            speakLater(utterance, SCREEN_FEEDBACK_DELAY);
        } else {
            speak(utterance);
        }
    }

    private CharSequence formatAnnouncementForArc(CharSequence title, boolean focusIntoArc) {
        SpannableStringBuilder builder = new SpannableStringBuilder(title);

        StringBuilderUtils.appendWithSeparator(builder,
                mService.getString(R.string.arc_android_window));

        if (focusIntoArc) {
            // Append short navigation hint.
            StringBuilderUtils.appendWithSeparator(builder,
                    mService.getString(R.string.arc_navigation_hint));

            // Append hint to see the list of keyboard shortcuts.
            appendKeyboardShortcutHint(builder, R.string.arc_open_manage_keyboard_shortcuts_hint,
                    R.string.keycombo_shortcut_open_manage_keyboard_shortcuts);

            // Append hint to open TalkBack settings.
            appendKeyboardShortcutHint(builder, R.string.arc_open_talkback_settings_hint,
                    R.string.keycombo_shortcut_open_talkback_settings);
        }

        return builder;
    }

    private void appendKeyboardShortcutHint(SpannableStringBuilder builder, int templateId,
            int keyComboId) {
        KeyComboManager keyComboManager = mService.getKeyComboManager();
        KeyComboModel keyComboModel = keyComboManager.getKeyComboModel();
        long keyComboCode = keyComboModel.getKeyComboCodeForKey(mService.getString(keyComboId));
        if (keyComboCode != KeyComboModel.KEY_COMBO_CODE_UNASSIGNED) {
            long keyComboCodeWithModifier = KeyComboManager.getKeyComboCode(
                    KeyComboManager.getModifier(keyComboCode) |
                    keyComboModel.getTriggerModifier(),
                    keyComboManager.getKeyCode(keyComboCode));
            String keyCombo = keyComboManager.getKeyComboStringRepresentation(
                    keyComboCodeWithModifier);
            StringBuilderUtils.appendWithSeparator(builder,
                    mService.getString(templateId, keyCombo));
        }
    }

    private boolean isAlertDialog(int windowId) {
        CharSequence className = mWindowToClassName.get(windowId);
        return className != null && className.equals("android.app.AlertDialog");
    }

    private void updateWindowTitlesMap(AccessibilityEvent event) {
        switch (event.getEventType()) {
            case AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED: {
                // If split screen mode is NOT available, we only need to care single window.
                if (!mIsSplitScreenModeAvailable) {
                    mWindowTitlesMap.clear();
                }

                int windowId = getWindowId(event);
                boolean shouldAnnounceEvent = shouldAnnounceEvent(event, windowId);
                CharSequence title = getWindowTitleFromEvent(event,
                        shouldAnnounceEvent /* useContentDescription */);
                if (title != null) {
                    if (shouldAnnounceEvent) {
                        // When software keyboard is shown or hidden, TYPE_WINDOW_STATE_CHANGED
                        // is dispatched with text describing the visibility of the keyboard.
                        speakWithFeedback(title);
                    } else {
                        mWindowTitlesMap.put(windowId, title);

                        if (getWindowType(event) == AccessibilityWindowInfo.TYPE_SYSTEM) {
                            mSystemWindowIdsSet.add(windowId);
                        }

                        CharSequence eventWindowClassName = event.getClassName();
                        mWindowToClassName.put(windowId, eventWindowClassName);
                        mWindowToPackageName.put(windowId, event.getPackageName());
                    }
                }
            } break;
            case AccessibilityEvent.TYPE_WINDOWS_CHANGED: {
                HashSet<Integer> windowIdsToBeRemoved =
                        new HashSet<Integer>(mWindowTitlesMap.keySet());
                List<AccessibilityWindowInfo> windows = mService.getWindows();
                for (AccessibilityWindowInfo window : windows) {
                    windowIdsToBeRemoved.remove(window.getId());
                }
                for (Integer windowId : windowIdsToBeRemoved) {
                    mWindowTitlesMap.remove(windowId);
                    mSystemWindowIdsSet.remove(windowId);
                    mWindowToClassName.remove(windowId);
                    mWindowToPackageName.remove(windowId);
                }
            } break;
        }
    }

    private CharSequence getWindowTitleFromEvent(AccessibilityEvent event,
            boolean useContentDescription) {
        if (useContentDescription && !TextUtils.isEmpty(event.getContentDescription())) {
            return event.getContentDescription();
        }

        List<CharSequence> titles = event.getText();
        if (titles.size() > 0) {
            return titles.get(0);
        }

        return null;
    }

    /**
     * Uses a heuristic to guess whether an event should be announced.
     * Any event that comes from an IME, or an invisible window is considered
     * an announcement.
     */
    private boolean shouldAnnounceEvent(AccessibilityEvent event, int windowId) {
        // Assume window ID of 0 is the keyboard.
        if (windowId == WINDOW_ID_NONE) {
            return true;
        }

        // If there's an actual window ID, we need to check the window type (if window available).
        boolean shouldAnnounceWindow = false;
        AccessibilityRecordCompat record = AccessibilityEventCompat.asRecord(event);
        AccessibilityNodeInfoCompat source = record.getSource();
        if (source != null) {
            AccessibilityWindowInfoCompat window = source.getWindow();
            if (window != null) {
                shouldAnnounceWindow =
                    window.getType() == AccessibilityWindowInfoCompat.TYPE_INPUT_METHOD;
                window.recycle();
            } else {
                // If window is not visible, we cannot know whether the window type is input method
                // or not. Let's announce it for the case. If window is visible but window info is
                // not available, it can be non-focusable visible window. Don't announce it for the
                // case. It can be a toast.
                shouldAnnounceWindow = !source.isVisibleToUser();
            }
            source.recycle();
        }
        return shouldAnnounceWindow;
    }

    private void updateScreenState(AccessibilityEvent event) {
        switch (event.getEventType()) {
            case AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED:
                // Do nothing if split screen mode is available since it can be covered by
                // TYPE_WINDOWS_CHANGED events.
                if (mIsSplitScreenModeAvailable) {
                    return;
                }

                mWindowIdA = getWindowId(event);
                break;
            case AccessibilityEvent.TYPE_WINDOWS_CHANGED:
                // Do nothing if split screen mode is NOT available since it can be covered by
                // TYPE_WINDOW_STATE_CHANGED events.
                if (!mIsSplitScreenModeAvailable) {
                    return;
                }

                ArrayList<AccessibilityWindowInfo> applicationWindows = new ArrayList<>();
                ArrayList<AccessibilityWindowInfo> systemWindows = new ArrayList<>();
                ArrayList<AccessibilityWindowInfo> accessibilityOverlayWindows = new ArrayList<>();
                List<AccessibilityWindowInfo> windows = mService.getWindows();

                // If there are no windows available, clear the cached IDs.
                if (windows.isEmpty()) {
                    mAccessibilityOverlayWindowId = WINDOW_ID_NONE;
                    mWindowIdA = WINDOW_ID_NONE;
                    mWindowIdB = WINDOW_ID_NONE;
                    return;
                }

                for (int i = 0; i < windows.size(); i++) {
                    AccessibilityWindowInfo window = windows.get(i);
                    switch (window.getType()) {
                        case AccessibilityWindowInfo.TYPE_APPLICATION:
                            if (window.getParent() == null) {
                                applicationWindows.add(window);
                            }
                            break;
                        case AccessibilityWindowInfo.TYPE_SYSTEM:
                            systemWindows.add(window);
                            break;
                        case AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY:
                            accessibilityOverlayWindows.add(window);
                            break;
                    }
                }

                if (accessibilityOverlayWindows.size() == windows.size()) {
                    // TODO: investigate whether there is a case where we have more than one
                    // accessibility overlay, and add a logic for it if there is.
                    mAccessibilityOverlayWindowId = accessibilityOverlayWindows.get(0).getId();
                    return;
                }

                mAccessibilityOverlayWindowId = WINDOW_ID_NONE;

                if (applicationWindows.size() == 0) {
                    mWindowIdA = WINDOW_ID_NONE;
                    mWindowIdB = WINDOW_ID_NONE;

                    // If there is no application window but a system window, consider it as a
                    // current window. This logic handles notification shade and lock screen.
                    if (systemWindows.size() > 0) {
                        Collections.sort(systemWindows,
                                new WindowManager.WindowPositionComparator(
                                        mService.isScreenLayoutRTL()));

                        mWindowIdA = systemWindows.get(0).getId();
                    }
                } else if (applicationWindows.size() == 1) {
                    mWindowIdA = applicationWindows.get(0).getId();
                    mWindowIdB = WINDOW_ID_NONE;
                } else if (applicationWindows.size() == 2) {
                    Collections.sort(applicationWindows,
                            new WindowManager.WindowPositionComparator(
                                    mService.isScreenLayoutRTL()));

                    mWindowIdA = applicationWindows.get(0).getId();
                    mWindowIdB = applicationWindows.get(1).getId();
                } else {
                    // If there are more than 2 windows, report the active window as the current
                    // window.
                    for (AccessibilityWindowInfo applicationWindow : applicationWindows) {
                        if (applicationWindow.isActive()) {
                            mWindowIdA = applicationWindow.getId();
                            mWindowIdB = WINDOW_ID_NONE;
                            return;
                        }
                    }
                }
                break;
        }
    }

    private void speak(CharSequence utterance) {
        mDelayedSpeechHandler.removeMessages(SpeechHandler.MESSAGE_WHAT_SPEAK);
        mUtterance = null;

        speakWithFeedback(utterance);
    }

    private void speakLater(CharSequence utterance, int delay) {
        mDelayedSpeechHandler.removeMessages(SpeechHandler.MESSAGE_WHAT_SPEAK);
        mUtterance = utterance;

        mDelayedSpeechHandler.sendEmptyMessageDelayed(SpeechHandler.MESSAGE_WHAT_SPEAK, delay);
    }

    private void speakWithFeedback(CharSequence utterance) {
        FeedbackController feedbackController = mService.getFeedbackController();
        feedbackController.playHaptic(R.array.window_state_pattern);
        feedbackController.playAuditory(R.raw.window_state);
        mService.getSpeechController().speak(utterance,
                SpeechController.QUEUE_MODE_UNINTERRUPTIBLE, 0 /* no flag */, null);
    }

    private CharSequence getWindowTitle(int windowId) {
        // Try to get window title from the map.
        CharSequence windowTitle = mWindowTitlesMap.get(windowId);
        if (windowTitle != null) {
            return windowTitle;
        }

        if (!BuildCompat.isAtLeastN()) {
            return null;
        }

        // Do not try to get system window title from AccessibilityWindowInfo.getTitle, it can
        // return non-translated value.
        if (isSystemWindow(windowId)) {
            return null;
        }

        // Try to get window title from AccessibilityWindowInfo.
        for (AccessibilityWindowInfo window : mService.getWindows()) {
            if (window.getId() == windowId) {
                return window.getTitle();
            }
        }

        return null;
    }

    private boolean isSystemWindow(int windowId) {
        if (mSystemWindowIdsSet.contains(windowId)) {
            return true;
        }

        if (!mIsSplitScreenModeAvailable) {
            return false;
        }

        for (AccessibilityWindowInfo window : mService.getWindows()) {
            if (window.getId() == windowId &&
                    window.getType() == AccessibilityWindowInfo.TYPE_SYSTEM) {
                return true;
            }
        }

        return false;
    }

    private CharSequence getWindowTitleForFeedback(int windowId) {
        CharSequence title = getWindowTitle(windowId);

        // Try to fall back to application label if window title is not available.
        if (title == null) {
            CharSequence packageName = mWindowToPackageName.get(windowId);

            // Try to get package name from accessibility window info if it's not in the map.
            if (packageName == null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                for (AccessibilityWindowInfo window : mService.getWindows()) {
                    if (window.getId() == windowId) {
                        AccessibilityNodeInfo rootNode = window.getRoot();
                        if (rootNode != null) {
                            packageName = rootNode.getPackageName();
                            rootNode.recycle();
                        }
                    }
                }
            }

            if (packageName != null) {
                title = mService.getApplicationLabel(packageName);
            }
        }

        title = WindowManager.formatWindowTitleForFeedback(title, mService);

        if (isAlertDialog(windowId)) {
            title = mService.getString(R.string.template_alert_dialog_template, title);
        }

        return title;
    }

    private int getWindowId(AccessibilityEvent event) {
        AccessibilityNodeInfo node = event.getSource();
        if (node == null) {
            return WINDOW_ID_NONE;
        }

        int windowId = node.getWindowId();
        node.recycle();
        return windowId;
    }

    private int getWindowType(AccessibilityEvent event) {
        if (event == null) {
            return WINDOW_TYPE_NONE;
        }

        AccessibilityNodeInfo nodeInfo = event.getSource();
        if (nodeInfo == null) {
            return WINDOW_TYPE_NONE;
        }

        AccessibilityNodeInfoCompat nodeInfoCompat = new AccessibilityNodeInfoCompat(nodeInfo);
        AccessibilityWindowInfoCompat windowInfoCompat = nodeInfoCompat.getWindow();
        if (windowInfoCompat == null) {
            nodeInfoCompat.recycle();
            return WINDOW_TYPE_NONE;
        }

        int windowType = windowInfoCompat.getType();
        windowInfoCompat.recycle();
        nodeInfoCompat.recycle();

        return windowType;
    }

}
