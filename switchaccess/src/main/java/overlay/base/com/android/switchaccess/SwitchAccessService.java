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
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Handler;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.os.UserManager;
import android.util.Log;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;
import com.google.android.accessibility.switchaccess.Analytics;
import com.google.android.accessibility.switchaccess.AutoScanController;
import com.google.android.accessibility.switchaccess.ClearOverlayNode;
import com.google.android.accessibility.switchaccess.FeedbackController;
import com.google.android.accessibility.switchaccess.KeyAssignmentUtils;
import com.google.android.accessibility.switchaccess.KeyboardEventManager;
import com.google.android.accessibility.switchaccess.OptionManager;
import com.google.android.accessibility.switchaccess.OverlayController;
import com.google.android.accessibility.switchaccess.PointScanManager;
import com.google.android.accessibility.switchaccess.ShowGlobalMenuNode;
import com.google.android.accessibility.switchaccess.SwitchAccessPreferenceActivity;
import com.google.android.accessibility.switchaccess.SwitchAccessWindowInfo;
import com.google.android.accessibility.switchaccess.TreeScanNode;
import com.google.android.accessibility.switchaccess.UiChangeDetector;
import com.google.android.accessibility.switchaccess.UiChangeStabilizer;
import com.google.android.accessibility.switchaccess.setupwizard.SetupWizardActivity;
import com.google.android.accessibility.switchaccess.treebuilding.MainTreeBuilder;
import com.google.android.accessibility.utils.LogUtils;
import com.google.android.accessibility.utils.SharedKeyEvent;
import com.google.android.accessibility.utils.SharedPreferencesUtils;
import com.google.android.accessibility.utils.widget.SimpleOverlay;

/**
 * Enable a user to perform touch gestures using keyboards with only a small number of keys. These
 * keyboards may be adapted to match a user's capabilities, for example with very large buttons,
 * proximity sensors around the head, or sip/puff switches.
 */
@TargetApi(Build.VERSION_CODES.LOLLIPOP)
public class SwitchAccessService extends AccessibilityService
    implements SharedPreferences.OnSharedPreferenceChangeListener,
        UiChangeStabilizer.UiChangedListener,
        SharedKeyEvent.Listener {

  private UiChangeStabilizer mUiChangeStabilizer;
  private UiChangeDetector mEventProcessor;

  private Analytics mAnalytics;

  private WakeLock mWakeLock;

  private static SwitchAccessService sInstance = null;

  private OverlayController mOverlayController;
  private OptionManager mOptionManager;
  private AutoScanController mAutoScanController;
  private PointScanManager mPointScanManager;
  private KeyboardEventManager mKeyboardEventManager;
  private MainTreeBuilder mMainTreeBuilder;
  private FeedbackController mFeedbackController;

  private final BroadcastReceiver mUserUnlockedBroadcastReceiver =
      new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
          mAnalytics = Analytics.getOrCreateInstance(SwitchAccessService.this);
          unregisterReceiver(mUserUnlockedBroadcastReceiver);
        }
      };

  @Override
  public boolean onUnbind(Intent intent) {
    try {
      mAutoScanController.stopScan();
    } catch (NullPointerException e) {
      // Ignore.
    }
    mUiChangeStabilizer.shutdown();
    return super.onUnbind(intent);
  }

  @Override
  public void onDestroy() {
    SharedKeyEvent.unregister(this);
    if (mAnalytics != null) {
      mAnalytics.stop();
    }
    mOptionManager.shutdown();
    mOverlayController.shutdown();
    mMainTreeBuilder.shutdown();
    mPointScanManager.shutdown();
    mFeedbackController.shutdown();
    sInstance = null;
    super.onDestroy();
  }

  @Override
  public void onAccessibilityEvent(AccessibilityEvent event) {
    mEventProcessor.onAccessibilityEvent(event, null /* EventId */);
  }

  @Override
  public void onInterrupt() {
    /* TODO: Will this ever be called? */
  }

  /** @return The active SwitchingControl instance or {@code null} if not available. */
  public static SwitchAccessService getInstance() {
    return sInstance;
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
    if (mKeyboardEventManager.onKeyEvent(keyEvent, mAnalytics)) {
      mWakeLock.acquire();
      mWakeLock.release();
      /*
       * This is inefficient but is needed when we pull down the notification shade.
       * It also only works because the key event handling is delayed to see if the
       * UI needs to stabilize.
       * TODO: Refactor so we only re-index immediately after events if we're scanning
       */
      if (!SwitchAccessPreferenceActivity.isPointScanEnabled(this)) {
        rebuildScanTree();
      }
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
    SimpleOverlay highlightOverlay = new SimpleOverlay(this);
    SimpleOverlay globalMenuButtonOverlay = new SimpleOverlay(this, 0, true);
    SimpleOverlay menuOverlay = new SimpleOverlay(this, 0, true);
    mOverlayController =
        new OverlayController(highlightOverlay, globalMenuButtonOverlay, menuOverlay);
    mOverlayController.configureOverlays();
    mFeedbackController = new FeedbackController(this);
    mOptionManager = new OptionManager(mOverlayController, mFeedbackController);
    mPointScanManager = new PointScanManager(mOverlayController, this);
    mMainTreeBuilder = new MainTreeBuilder(this);
    mAutoScanController =
        new AutoScanController(mOptionManager, mFeedbackController, new Handler(), this);
    mKeyboardEventManager =
        new KeyboardEventManager(this, mOptionManager, mAutoScanController, mPointScanManager);
    PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
    mWakeLock =
        powerManager.newWakeLock(
            PowerManager.FULL_WAKE_LOCK | PowerManager.ON_AFTER_RELEASE, "SwitchAccess");
    SharedPreferencesUtils.getSharedPreferences(this)
        .registerOnSharedPreferenceChangeListener(this);
    mUiChangeStabilizer = new UiChangeStabilizer(this);
    mEventProcessor = new UiChangeDetector(mUiChangeStabilizer);
    SharedKeyEvent.register(this);

    UserManager userManager = (UserManager) getSystemService(Context.USER_SERVICE);
    if ((Build.VERSION.SDK_INT < Build.VERSION_CODES.N) || userManager.isUserUnlocked()) {
      mAnalytics = Analytics.getOrCreateInstance(this);
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
      this.registerReceiver(mUserUnlockedBroadcastReceiver, filter);
    }
  }

  @Override
  protected boolean onKeyEvent(KeyEvent keyEvent) {
    return SharedKeyEvent.onKeyEvent(this, keyEvent);
  }

  @Override
  public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
    LogUtils.log(this, Log.DEBUG, "A shared preference changed: %s", key);
    mKeyboardEventManager.reloadPreferences(this);
  }

  @Override
  public void onUiChangedAndIsNowStable() {
    if (SwitchAccessPreferenceActivity.isPointScanEnabled(this)) {
      // Only reset scan if we're not already scanning so as not to throw users off with the
      // cursor randomly jumping to the beginning.
      mPointScanManager.onUiChanged();
    } else {
      rebuildScanTree();
    }
  }

  /** Get Option Manager. Used by Analytics. */
  public OptionManager getOptionManager() {
    return mOptionManager;
  }

  /** Get Point Scan Manager. Used by Analytics. */
  public PointScanManager getPointScanManager() {
    return mPointScanManager;
  }

  /** Get OverlayController. Used by Analytics. */
  public OverlayController getOverlayController() {
    return mOverlayController;
  }

  private void rebuildScanTree() {
    TreeScanNode firstOrLastNode;
    boolean shouldPlaceNodeFirst;
    if (mOverlayController.isMenuVisible()) {
      firstOrLastNode = new ClearOverlayNode(mOverlayController);
      shouldPlaceNodeFirst = false;
    } else {
      firstOrLastNode = new ShowGlobalMenuNode(mOverlayController);
      shouldPlaceNodeFirst = true;
    }
    mOptionManager.clearFocusIfNewTree(
        mMainTreeBuilder.addWindowListToTree(
            SwitchAccessWindowInfo.convertZOrderWindowList(getWindows()),
            firstOrLastNode,
            shouldPlaceNodeFirst));
  }
}
