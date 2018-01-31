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

import android.annotation.TargetApi;
import android.os.Build;
import android.os.Handler;
import android.os.SystemClock;
import android.view.WindowManager;

/**
 * Delays notifying the rest of Switch Access of changes to the UI. As we aren't synchronized with
 * the UI, this gives the UI time to settle.
 */
@TargetApi(Build.VERSION_CODES.LOLLIPOP)
public class UiChangeStabilizer implements UiChangeDetector.PossibleUiChangeListener {
  /* Empirically determined magic time that we delay after possible changes to the UI */
  private static final long FRAMEWORK_SETTLING_TIME_MILLIS = 500;

  private final Handler mHandler = new Handler();
  private final UiChangedListener mUiChangedListener;

  private final Runnable mRunnableToInformOfUiChange =
      new Runnable() {
        @Override
        public void run() {
          long timeToWait = getMillisUntilSafeToInformOfUiChange();

          if (timeToWait > 0) {
            /* Not safe to process now; reschedule for later */
            mHandler.removeCallbacks(mRunnableToInformOfUiChange);
            mHandler.postDelayed(mRunnableToInformOfUiChange, timeToWait);
            return;
          }

          try {
            mUiChangedListener.onUiChangedAndIsNowStable();
          } catch (WindowManager.BadTokenException exception) {
            // This could happen if Switch Access's permissions to draw on the screen are
            // revoked before the onUnbind() callback completes (as that is where shutdown is
            // called).
          }
        }
      };

  private long mLastWindowChangeTime = 0;

  /**
   * @param uiChangedListener A listener to be notified when the UI updates (typically an
   *     OptionManager)
   */
  public UiChangeStabilizer(UiChangedListener uiChangedListener) {
    mUiChangedListener = uiChangedListener;
  }

  /**
   * If the UI may have changed, this method should be called so we know to wait for it to settle.
   */
  @Override
  public void onPossibleChangeToUi() {
    mLastWindowChangeTime = SystemClock.elapsedRealtime();
    mHandler.removeCallbacks(mRunnableToInformOfUiChange);
    mHandler.postDelayed(mRunnableToInformOfUiChange, getMillisUntilSafeToInformOfUiChange());
  }

  private long getMillisUntilSafeToInformOfUiChange() {
    long timeToWait =
        mLastWindowChangeTime + FRAMEWORK_SETTLING_TIME_MILLIS - SystemClock.elapsedRealtime();
    timeToWait = Math.max(timeToWait, 0);
    return timeToWait;
  }

  /** Listener that is notified of UI changes once the UI stabilizes. */
  public interface UiChangedListener {
    void onUiChangedAndIsNowStable();
  }

  public void shutdown() {
    mHandler.removeCallbacks(mRunnableToInformOfUiChange);
  }
}
