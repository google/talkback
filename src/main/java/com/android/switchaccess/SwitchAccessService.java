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

import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import android.annotation.TargetApi;
import android.os.Build;

import android.accessibilityservice.AccessibilityService;
import android.os.Handler;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.util.Log;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;
import com.android.utils.LogUtils;
import com.android.utils.widget.SimpleOverlay;
import com.google.android.marvin.talkback.TalkBackService;


/**
 * Enable a user to perform touch gestures using keyboards with only a small number of keys. These
 * keyboards may be adapted to match a user's capabilities, for example with very large buttons,
 * proximity sensors around the head, or sip/puff switches.
 */
@TargetApi(Build.VERSION_CODES.LOLLIPOP)
public class SwitchAccessService extends AccessibilityService
        implements SharedPreferences.OnSharedPreferenceChangeListener,
        ActionProcessor.UiChangedListener {

    private ActionProcessor mActionProcessor;
    private UiChangeDetector mEventProcessor;

    private Analytics mAnalytics;

    private WakeLock mWakeLock;

    private static SwitchAccessService sInstance = null;

    private OverlayController mOverlayController;
    private OptionManager mOptionManager;
    private KeyboardEventManager mKeyboardEventManager;
    private MultiWindowTreeBuilder mMultiWindowTreeBuilder;
    private TalkBackOrderNDegreeTreeBuilder mTalkBackOrderNDegreeTreeBuilder;

    @Override
    public void onCreate() {
        super.onCreate();
        sInstance = this;
        mOverlayController = new OverlayController(new SimpleOverlay(this));
        mOverlayController.configureOverlay();
        mOptionManager = new OptionManager(mOverlayController);
        mTalkBackOrderNDegreeTreeBuilder = new TalkBackOrderNDegreeTreeBuilder(this);
        mMultiWindowTreeBuilder = new MultiWindowTreeBuilder(this, new LinearScanTreeBuilder(),
                new RowColumnTreeBuilder(), mTalkBackOrderNDegreeTreeBuilder);
        AutoScanController autoScanController =
                new AutoScanController(mOptionManager, new Handler(), this);
        mKeyboardEventManager = new KeyboardEventManager(this, mOptionManager, autoScanController);
        mAnalytics = new Analytics();
        mAnalytics.start();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mAnalytics.stop();
        mOptionManager.shutdown();
        mOverlayController.shutdown();
        mMultiWindowTreeBuilder.shutdown();
        sInstance = null;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        mEventProcessor.onAccessibilityEvent(event);
    }

    @Override
    public void onInterrupt() {
        /* TODO(PW) Will this ever be called? */
    }

    /**
     * @return The active SwitchingControl instance or {@code null} if not available.
     */
    public static SwitchAccessService getInstance() {
        return sInstance;
    }

    /**
     * Intended to mimic the behavior of onKeyEvent if this were the only service running.
     * It will be called from onKeyEvent, both from this service and from others in this apk
     * (TalkBack). This method must not block, since it will block onKeyEvent as well.
     * @param keyEvent A key event
     * @return {@code true} if the event is handled, {@code false} otherwise.
     */
    public boolean onKeyEventShared(KeyEvent keyEvent) {
        if (mKeyboardEventManager.onKeyEvent(keyEvent, mActionProcessor, mAnalytics)) {
            mWakeLock.acquire();
            mWakeLock.release();
            return true;
        }
        return false;
    }

    /*
     * FULL_WAKE_LOCK has been deprecated, but the replacement only works for applications. The docs
     * actually say that using wake locks from a service is a legitimate use case.
     */
    @SuppressWarnings("deprecation")
    @Override
    protected void onServiceConnected() {
        PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        mWakeLock = powerManager.newWakeLock(
                PowerManager.FULL_WAKE_LOCK | PowerManager.ON_AFTER_RELEASE, "SwitchAccess");
        PreferenceManager.getDefaultSharedPreferences(this)
                .registerOnSharedPreferenceChangeListener(this);
        mKeyboardEventManager.reloadPreferences(this);
        mActionProcessor = new ActionProcessor(this);
        mEventProcessor = new UiChangeDetector(mActionProcessor);
    }

    @Override
    protected boolean onKeyEvent(KeyEvent keyEvent) {
        boolean keyHandled = onKeyEventShared(keyEvent);
        if (keyHandled) {
            /*
             * This is inefficient but is needed when we pull down the notification shade.
             * It also only works because the key event handling is delayed to see if the
             * UI needs to stabilize.
             * TODO(pweaver) Refactor so we only re-index immediately after events if we're scanning
             */
            mOptionManager.clearFocusIfNewTree(
                    mMultiWindowTreeBuilder.buildTreeFromWindowList(getWindows(), this));
        }
        TalkBackService talkBackService = TalkBackService.getInstance();
        if (talkBackService != null) {
            keyHandled = talkBackService.onKeyEventShared(keyEvent) || keyHandled;
        }
        return keyHandled;
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
        LogUtils.log(this, Log.DEBUG, "A shared preference changed: %s", key);
        mKeyboardEventManager.reloadPreferences(this);
        mTalkBackOrderNDegreeTreeBuilder.reloadPreferences(this);
    }

    @Override
    public void onUiChangedAndIsNowStable() {
        mOptionManager.clearFocusIfNewTree(
                mMultiWindowTreeBuilder.buildTreeFromWindowList(getWindows(), this));
    }

    public OptionManager getOptionManager() {
        return mOptionManager;
    }
}
