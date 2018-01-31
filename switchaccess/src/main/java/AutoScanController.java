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

package com.google.android.accessibility.switchaccess;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.util.Log;
import android.view.WindowManager.BadTokenException;
import com.google.android.accessibility.utils.LogUtils;
import com.google.android.accessibility.utils.SharedPreferencesUtils;

/**
 * Auto-scanning allows the user to control the phone with one button. The user presses the button
 * to start moving focus around, and presses it again to select the currently focused item.
 */
public class AutoScanController
    implements OptionManager.OptionManagerListener,
        FeedbackController.OnUtteranceCompleteListener,
        SharedPreferences.OnSharedPreferenceChangeListener {

  private final Context mContext;

  private final Handler mHandler;

  private final OptionManager mOptionManager;

  private int mCompletedScanningLoops;

  private int mMaxScanningLoops;

  private long mLastScanEventTimeMs;
  private int mAutoScanDelayMs;
  private int mExtraDelayOnFirstItemMs;

  private boolean mShouldFinishSpeechBeforeContinuingScan;

  private final Runnable mAutoScanRunnable =
      new Runnable() {
        /**
         * Advances the focus to the next node in the view. If there are no more nodes that can be
         * clicked or if Auto Scan was disabled, then the scan is stopped
         */
        @Override
        public void run() {
          if (!mIsAutoScanEnabled) {
            stopScan();
            return;
          }

          /*
           * TODO: Option selection should be configurable. This choice mimics
           * linear scanning
           */
          if (mIsScanInProgress) {
            try {
              selectNextItem();
              if (mIsScanInProgress) {
                // We only know the exact time when we should move the highlight if we don't
                // need to wait for spoken feedback to complete. The callback to
                // AutoScanController#onUtteranceComplete will schedule this runnable if we
                // are waiting for spoken feedback.
                if (!mShouldFinishSpeechBeforeContinuingScan) {
                  mHandler.postDelayed(mAutoScanRunnable, getAutoScanDelay(false));
                }
              } else {
                mCompletedScanningLoops++;
                if (mCompletedScanningLoops < mMaxScanningLoops) {
                  selectNextItem();
                  startScan();
                } else {
                  mCompletedScanningLoops = 0;
                }
              }
            } catch (BadTokenException exception) {
              stopScan();
              LogUtils.log(this, Log.DEBUG, "Unable to start scan: %s", exception);
            }
            mLastScanEventTimeMs = System.currentTimeMillis();
          }
        }
      };

  private boolean mIsAutoScanEnabled;

  private boolean mIsScanInProgress;

  private boolean mReverseScan;

  public AutoScanController(
      OptionManager optionManager,
      FeedbackController feedbackController,
      Handler handler,
      Context context) {
    mOptionManager = optionManager;
    mOptionManager.addOptionManagerListener(this);
    feedbackController.setOnUtteranceCompleteListener(this);
    mHandler = handler;
    mContext = context;

    SharedPreferences prefs = SharedPreferencesUtils.getSharedPreferences(mContext);
    prefs.registerOnSharedPreferenceChangeListener(this);
    onSharedPreferenceChanged(prefs, null);

    mCompletedScanningLoops = 0;
  }

  /** Called when auto scan key is pressed */
  public void autoScanActivated(boolean reverseScan) {
    if (!mIsScanInProgress) {
      mReverseScan = reverseScan;
      selectNextItem();
      startScan();
      return;
    }
    if (mReverseScan != reverseScan) {
      mReverseScan = reverseScan;
      return;
    }
    /* The user made a selection. Stop moving focus. */
    mHandler.removeCallbacks(mAutoScanRunnable);
    mOptionManager.selectOption(OptionManager.OPTION_INDEX_CLICK);
    /* Re-start scanning on the updated tree if focus wasn't cleared */
    if (mIsScanInProgress && !mShouldFinishSpeechBeforeContinuingScan) {
      mHandler.postDelayed(mAutoScanRunnable, getAutoScanDelay(true));
    }
  }

  /** Scanning stops when focus is cleared */
  @Override
  public void onOptionManagerClearedFocus() {
    stopScan();
  }

  /** If auto-scan starts, schedule runnable to continue scanning */
  @Override
  public void onOptionManagerStartedAutoScan() {
    if (mIsAutoScanEnabled && !mIsScanInProgress) {
      mReverseScan = false;
      startScan();
    }
  }

  /** Starts the auto scan if it is not already running */
  private void startScan() {
    mIsScanInProgress = true;
    if (!mShouldFinishSpeechBeforeContinuingScan) {
      mHandler.postDelayed(mAutoScanRunnable, getAutoScanDelay(true));
    }
  }

  /**
   * Select the next item in either the forward or backward direction, depending on whether we're
   * moving forward or backward.
   */
  private void selectNextItem() {
    if (mReverseScan) {
      mOptionManager.moveToParent(true);
    } else {
      mOptionManager.selectOption(OptionManager.OPTION_INDEX_NEXT);
    }
  }

  /** Stops the auto scan if it is currently running */
  public void stopScan() {
    mIsScanInProgress = false;
    mHandler.removeCallbacks(mAutoScanRunnable);
  }

  /** @return the current auto scan time delay that is selected in the preferences */
  private int getAutoScanDelay(boolean isFirstElement) {
    return isFirstElement ? (mAutoScanDelayMs + mExtraDelayOnFirstItemMs) : mAutoScanDelayMs;
  }

  @Override
  public void onUtteranceComplete() {
    // We only care about speech finishing if we need to wait for it before moving highlight.
    if (mShouldFinishSpeechBeforeContinuingScan) {
      long timeSinceLastScanMs = (System.currentTimeMillis() - mLastScanEventTimeMs);
      long timeToNextScanMs = mAutoScanDelayMs - timeSinceLastScanMs;
      if (timeToNextScanMs < 0) {
        timeToNextScanMs = 0;
      }
      mHandler.postDelayed(mAutoScanRunnable, timeToNextScanMs);
    }
  }

  @Override
  public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
    mMaxScanningLoops = SwitchAccessPreferenceActivity.getNumberOfScanningLoops(mContext);
    mAutoScanDelayMs = SwitchAccessPreferenceActivity.getAutoScanDelayMs(mContext);
    mExtraDelayOnFirstItemMs = SwitchAccessPreferenceActivity.getFirstItemScanDelayMs(mContext);
    mIsAutoScanEnabled = SwitchAccessPreferenceActivity.isAutoScanEnabled(mContext);
    mShouldFinishSpeechBeforeContinuingScan =
        SwitchAccessPreferenceActivity.isSpokenFeedbackEnabled(mContext)
            && SwitchAccessPreferenceActivity.shouldFinishSpeechBeforeContinuingScan(mContext);
  }
}
