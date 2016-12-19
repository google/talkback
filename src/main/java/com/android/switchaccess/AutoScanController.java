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

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.util.Log;
import android.view.WindowManager.BadTokenException;

import com.android.talkback.R;
import com.android.utils.LogUtils;
import com.android.utils.SharedPreferencesUtils;

/**
 * Auto-scanning allows the user to control the phone with one button. The user presses the button
 * to start moving focus around, and presses it again to select the currently focused item.
 */
public class AutoScanController implements OptionManager.OptionManagerListener {

    private static final int MILLISECONDS_PER_SECOND = 1000;

    private final Context mContext;

    private final Handler mHandler;

    private final OptionManager mOptionManager;

    private final Runnable mAutoScanRunnable = new Runnable() {

        /**
         * Advances the focus to the next node in the view. If there are no more nodes that can be
         * clicked or if Auto Scan was disabled, then the scan is stopped
         */
        @Override
        public void run() {
            if (!SwitchAccessPreferenceActivity.isAutoScanEnabled(mContext)) {
                stopScan();
                return;
            }

            /*
             * TODO: Option selection should be configurable. This choice mimics
             * linear scanning
             */
            if (mIsScanInProgress) {
                try {
                    if (mReverseScan) {
                        mOptionManager.moveToParent(true);
                    } else {
                        mOptionManager.selectOption(OptionManager.OPTION_INDEX_NEXT);
                    }
                    if (mIsScanInProgress) {
                        mHandler.postDelayed(mAutoScanRunnable, getAutoScanDelay());
                    }
                } catch (BadTokenException exception) {
                    stopScan();
                    LogUtils.log(this, Log.DEBUG, "Unable to start scan: %s", exception);
                }
            }
        }
    };

    private boolean mIsScanInProgress;
    private boolean mReverseScan;

    public AutoScanController(OptionManager optionManager, Handler handler, Context context) {
        mOptionManager = optionManager;
        mOptionManager.addOptionManagerListener(this);
        mHandler = handler;
        mContext = context;
    }

    /**
     * Called when auto scan key is pressed
     */
    public void autoScanActivated(boolean reverseScan) {
        if (!mIsScanInProgress) {
            startScan(reverseScan);
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
        if (mIsScanInProgress) {
            mHandler.postDelayed(mAutoScanRunnable, getAutoScanDelay());
        }
    }

    /**
     * Scanning stops when focus is cleared
     */
    public void onOptionManagerClearedFocus() {
        stopScan();
    }

    /**
     * If auto-scan starts, schedule runnable to continue scanning
     */
    public void onOptionManagerStartedAutoScan() {
        if (SwitchAccessPreferenceActivity.isAutoScanEnabled(mContext) && !mIsScanInProgress) {
            mIsScanInProgress = true;
            mReverseScan = false;
            mHandler.postDelayed(mAutoScanRunnable, getAutoScanDelay());
        }
    }

    /**
     * Starts the auto scan if it is not already running
     * @param reverseScan if true - scanning starts at the last item and moves backward.
     */
    private void startScan(boolean reverseScan) {
        mIsScanInProgress = true;
        mReverseScan = reverseScan;
        mHandler.post(mAutoScanRunnable);
    }

    /**
     * Stops the auto scan if it is currently running
     */
    public void stopScan() {
        mIsScanInProgress = false;
        mHandler.removeCallbacks(mAutoScanRunnable);
    }

    /**
     * @return the current auto scan time delay that is selected in the preferences
     */
    private int getAutoScanDelay() {
        final SharedPreferences prefs = SharedPreferencesUtils.getSharedPreferences(mContext);
        return (int) (MILLISECONDS_PER_SECOND
                * SharedPreferencesUtils.getFloatFromStringPref(prefs, mContext.getResources(),
                R.string.pref_key_auto_scan_time_delay,
                R.string.pref_auto_scan_time_delay_default_value));
    }
}
