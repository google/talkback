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
import android.os.Handler;
import android.view.WindowManager.BadTokenException;
import com.google.android.accessibility.switchaccess.OptionManager.ScanEvent;
import com.google.android.accessibility.switchaccess.OptionManager.ScanStateChangeTrigger;
import com.google.android.accessibility.switchaccess.feedback.SwitchAccessFeedbackController;
import com.google.android.libraries.accessibility.utils.log.LogUtils;

/*
 * TODO: Fix issues related with the timeout in auto-scanning.
 */
/**
 * Auto-scanning allows the user to control the phone with one button. The user presses the button
 * to start moving focus around, and presses it again to select the currently focused item.
 */
public class AutoScanController
    implements OptionManager.OptionManagerListener,
        SwitchAccessFeedbackController.OnUtteranceCompleteListener {

  private static final String TAG = "AutoScanController";

  private final Context context;

  private final Handler handler;

  private final OptionManager optionManager;

  /**
   * The number of completed scanning loops that applies to both the main loop and a row. If a row
   * is selected, this resets. If the row is scanned the max number of times with nothing selected,
   * the main scan resumes and this number resets.
   */
  private int completedScanningLoops;

  private long lastScanEventTimeMs;

  private final Runnable autoScanRunnable =
      new Runnable() {
        /**
         * Advances the focus to the next node in the view. If there are no more nodes that can be
         * clicked or if Auto Scan was disabled, then the scan is stopped
         */
        @Override
        public void run() {
          if (!SwitchAccessPreferenceUtils.isAutoScanEnabled(context)) {
            stopScan();
            return;
          }

          if (isScanInProgress) {
            try {
              selectNextItem(false /* firstItem */);
              if (isScanInProgress) {
                // We only know the exact time when we should move the highlight if we don't
                // need to wait for spoken feedback to complete. The callback to
                // AutoScanController#onUtteranceComplete will schedule this runnable if we
                // are waiting for spoken feedback.
                if (!SwitchAccessPreferenceUtils.shouldFinishSpeechBeforeContinuingScan(context)) {
                  handler.postDelayed(autoScanRunnable, getAutoScanDelay(false));
                }
              } else if (!isRowScanInProgress) {
                completedScanningLoops++;
                if (completedScanningLoops
                    < SwitchAccessPreferenceUtils.getNumberOfScanningLoops(context)) {
                  selectNextItem(false /* firstItem */);
                  startScan();
                } else {
                  completedScanningLoops = 0;
                }
              }
            } catch (BadTokenException exception) {
              stopScan();
              LogUtils.d(TAG, "Unable to start scan: %s", exception);
            }
            lastScanEventTimeMs = System.currentTimeMillis();
          }
        }
      };

  private boolean isScanInProgress;

  /** If a row is currently being scanned when row-column scanning is enabled. */
  private boolean isRowScanInProgress = false;

  private ScanDirection scanDirection;

  /** The direction in which auto-scan will advance. */
  public enum ScanDirection {
    FORWARD,
    REVERSE,
  }

  // #setOnUtteranceCompleteListener and # addOptionManagerListener expect a fully initialized
  // listener, but we need to be able to set/add the listener during construction.
  @SuppressWarnings("nullness:argument.type.incompatible")
  public AutoScanController(
      OptionManager optionManager,
      SwitchAccessFeedbackController switchAccessFeedbackController,
      Handler handler,
      Context context) {
    this.optionManager = optionManager;
    this.optionManager.addOptionManagerListener(this);
    switchAccessFeedbackController.setOnUtteranceCompleteListener(this);
    this.handler = handler;
    this.context = context;

    completedScanningLoops = 0;
  }

  /**
   * Called when auto scan key is pressed.
   *
   * @return The type of scan event that this call resulted in
   */
  public ScanEvent startAutoScan(ScanDirection scanDirection) {
    if (!isScanInProgress) {
      this.scanDirection = scanDirection;
      ScanEvent scanEvent = selectNextItem(true /* firstItem */);
      startScan();
      return scanEvent;
    }
    if (this.scanDirection != scanDirection) {
      this.scanDirection = scanDirection;
      return ScanEvent.IGNORED_EVENT;
    }
    /* The user made a selection. Stop moving focus. */
    handler.removeCallbacks(autoScanRunnable);
    if (scanDirection == ScanDirection.FORWARD) {
      optionManager.selectOption(
          OptionManager.OPTION_INDEX_CLICK, ScanStateChangeTrigger.KEY_AUTO_SCAN);
    } else {
      optionManager.selectOption(
          OptionManager.OPTION_INDEX_CLICK, ScanStateChangeTrigger.KEY_REVERSE_AUTO_SCAN);
    }

    /* Re-start scanning on the updated tree if focus wasn't cleared */
    if (isScanInProgress
        && !SwitchAccessPreferenceUtils.shouldFinishSpeechBeforeContinuingScan(context)) {
      handler.postDelayed(autoScanRunnable, getAutoScanDelay(true));
    }
    return ScanEvent.SCAN_CONTINUED;
  }

  /** Scanning stops when focus is cleared */
  @Override
  public void onOptionManagerClearedFocus() {
    stopScan();
  }

  /** If auto-scan starts, schedule runnable to continue scanning */
  @Override
  public void onOptionManagerStartedAutoScan() {
    if (SwitchAccessPreferenceUtils.isAutoScanEnabled(context) && !isScanInProgress) {
      scanDirection = ScanDirection.FORWARD;
      completedScanningLoops = 0;
      isRowScanInProgress = false;
      startScan();
    }
  }

  /**
   * Restarts auto-scan time if the user presses the "next" or "previous" keys while auto-scan is in
   * progress.
   */
  @Override
  public void onHighlightMoved() {
    if (isScanInProgress) {
      lastScanEventTimeMs = System.currentTimeMillis();
      handler.removeCallbacks(autoScanRunnable);
      if (!SwitchAccessPreferenceUtils.shouldFinishSpeechBeforeContinuingScan(context)) {
        handler.postDelayed(autoScanRunnable, getAutoScanDelay(false));
      }
    }
  }

  @Override
  public void onRowScanStarted() {
    isRowScanInProgress = true;
    completedScanningLoops = 0;
  }

  @Override
  public boolean onRowScanCompleted(ScanStateChangeTrigger trigger) {
    if (!isTriggerFromAutoScan(trigger)) {
      return false;
    }

    completedScanningLoops++;
    if (isRowScanInProgress
        && (completedScanningLoops
            < SwitchAccessPreferenceUtils.getNumberOfScanningLoops(context))) {
      // The number of completed row scanning loops is less than the max, so we should rescan the
      // row.
      optionManager.rescanRow();
      handler.removeCallbacks(autoScanRunnable);
      if (scanDirection == ScanDirection.FORWARD) {
        optionManager.selectOption(
            OptionManager.OPTION_INDEX_CLICK, ScanStateChangeTrigger.FEATURE_AUTO_SCAN);
      } else {
        optionManager.selectOption(
            OptionManager.OPTION_INDEX_CLICK, ScanStateChangeTrigger.FEATURE_REVERSE_AUTO_SCAN);
      }

      /* Re-start scanning on the updated tree if focus wasn't cleared */
      if (isScanInProgress
          && !SwitchAccessPreferenceUtils.shouldFinishSpeechBeforeContinuingScan(context)) {
        handler.postDelayed(autoScanRunnable, getAutoScanDelay(true));
      }

      // The row should be rescanned.
      return true;
    } else {
      // All of the scan on the row have completed, so we should resume scanning the rest of the
      // tree, if there are scans left.
      completedScanningLoops = 0;
      isRowScanInProgress = false;
      startScan();

      // The row should not be rescanned.
      return false;
    }
  }

  /** Starts the auto scan if it is not already running */
  private void startScan() {
    isScanInProgress = true;
    if (!SwitchAccessPreferenceUtils.shouldFinishSpeechBeforeContinuingScan(context)) {
      handler.postDelayed(autoScanRunnable, getAutoScanDelay(true));
    }
  }

  /**
   * Select the next item in either the forward or backward direction, depending on whether we're
   * moving forward or backward.
   *
   * @return ScanEvent The scan event representing the type of action that this selection triggered
   */
  private ScanEvent selectNextItem(boolean firstItem) {
    if (scanDirection == ScanDirection.REVERSE) {
      return optionManager.moveToParent(
          true,
          firstItem
              ? ScanStateChangeTrigger.KEY_REVERSE_AUTO_SCAN
              : ScanStateChangeTrigger.FEATURE_REVERSE_AUTO_SCAN);
    } else {
      return optionManager.selectOption(
          OptionManager.OPTION_INDEX_NEXT,
          firstItem
              ? ScanStateChangeTrigger.KEY_AUTO_SCAN
              : ScanStateChangeTrigger.FEATURE_AUTO_SCAN);
    }
  }

  /** Stops the auto scan if it is currently running */
  public void stopScan() {
    isScanInProgress = false;
    isRowScanInProgress = false;
    handler.removeCallbacks(autoScanRunnable);
  }

  /** @return the current auto scan time delay that is selected in the preferences */
  private int getAutoScanDelay(boolean isFirstElement) {
    int autoScanDelayMs = SwitchAccessPreferenceUtils.getAutoScanDelayMs(context);
    return isFirstElement
        ? (autoScanDelayMs + SwitchAccessPreferenceUtils.getFirstItemScanDelayMs(context))
        : autoScanDelayMs;
  }

  private boolean isTriggerFromAutoScan(ScanStateChangeTrigger trigger) {
    return (trigger == ScanStateChangeTrigger.KEY_AUTO_SCAN)
        || (trigger == ScanStateChangeTrigger.KEY_REVERSE_AUTO_SCAN)
        || (trigger == ScanStateChangeTrigger.FEATURE_AUTO_SCAN)
        || (trigger == ScanStateChangeTrigger.FEATURE_REVERSE_AUTO_SCAN);
  }

  @Override
  public void onUtteranceComplete() {
    // We only care about speech finishing if we need to wait for it before moving highlight.
    if (SwitchAccessPreferenceUtils.shouldFinishSpeechBeforeContinuingScan(context)) {
      long timeSinceLastScanMs = (System.currentTimeMillis() - lastScanEventTimeMs);
      long timeToNextScanMs =
          SwitchAccessPreferenceUtils.getAutoScanDelayMs(context) - timeSinceLastScanMs;
      if (timeToNextScanMs < 0) {
        timeToNextScanMs = 0;
      }
      handler.removeCallbacks(autoScanRunnable);
      handler.postDelayed(autoScanRunnable, timeToNextScanMs);
    }
  }
}
