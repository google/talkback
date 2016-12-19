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
import android.annotation.TargetApi;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Handler;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.support.v4.os.BuildCompat;
import android.util.Log;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

import com.android.switchaccess.treebuilding.MainTreeBuilder;
import com.android.utils.LogUtils;
import com.android.utils.SharedPreferencesUtils;
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
    private AutoScanController mAutoScanController;
    private KeyboardEventManager mKeyboardEventManager;
    private MainTreeBuilder mMainTreeBuilder;

    @Override
    public boolean onUnbind(Intent intent) {
        if (mAutoScanController != null) {
            mAutoScanController.stopScan();
        }
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        mAnalytics.stop();
        mOptionManager.shutdown();
        mOverlayController.shutdown();
        mMainTreeBuilder.shutdown();
        sInstance = null;
        super.onDestroy();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        mEventProcessor.onAccessibilityEvent(event);
    }

    @Override
    public void onInterrupt() {
        /* TODO Will this ever be called? */
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
        sInstance = this;
        mOverlayController = new OverlayController(new SimpleOverlay(this));
        mOverlayController.configureOverlay();
        mOptionManager = new OptionManager(mOverlayController);
        mMainTreeBuilder = new MainTreeBuilder(this);
        mAutoScanController = new AutoScanController(mOptionManager, new Handler(), this);
        mKeyboardEventManager = new KeyboardEventManager(this, mOptionManager, mAutoScanController);
        mAnalytics = new Analytics();
        mAnalytics.start();
        PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        mWakeLock = powerManager.newWakeLock(
                PowerManager.FULL_WAKE_LOCK | PowerManager.ON_AFTER_RELEASE, "SwitchAccess");
        SharedPreferencesUtils.getSharedPreferences(this)
                .registerOnSharedPreferenceChangeListener(this);
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
             * TODO Refactor so we only re-index immediately after events if we're scanning
             */
            rebuildOptionScanTree();
        }
        if (!BuildCompat.isAtLeastN()) {
            TalkBackService talkBackService = TalkBackService.getInstance();
            if (talkBackService != null) {
                keyHandled = talkBackService.onKeyEventShared(keyEvent) || keyHandled;
            }
        }
        return keyHandled;
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
        LogUtils.log(this, Log.DEBUG, "A shared preference changed: %s", key);
        mKeyboardEventManager.reloadPreferences(this);
    }

    @Override
    public void onUiChangedAndIsNowStable() {
        rebuildOptionScanTree();
    }

    public OptionManager getOptionManager() {
        return mOptionManager;
    }

    private void rebuildOptionScanTree() {
        OptionScanNode globalContextMenuTree =
                mMainTreeBuilder.buildContextMenu(GlobalActionNode.getGlobalActionList(this));
        mOptionManager.clearFocusIfNewTree(mMainTreeBuilder.addWindowListToTree(
                SwitchAccessWindowInfo.convertZOrderWindowList(getWindows()),
                globalContextMenuTree));
    }
}
