/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.android.accessibility.utils;

import android.os.SystemClock;

/** Maintains a history of when EditText actions occurred. */
public class EditTextActionHistory {
  /** Map of action identifiers to start times. */
  private long mCutStartTime = -1;

  private long mPasteStartTime = -1;
  private long mSelectAllStartTime = -1;

  /** Map of action identifiers to finish times. */
  private long mCutFinishTime = -1;

  private long mPasteFinishTime = -1;
  private long mSelectAllFinishTime = -1;

  /**
   * Stores the start time for a cut action. This should be called immediately before {@link
   * AccessibilityNodeInfoCompat#performAction}.
   */
  public void beforeCut() {
    mCutStartTime = SystemClock.uptimeMillis();
  }

  /**
   * Stores the finish time for a cut action. This should be called immediately after {@link
   * AccessibilityNodeInfoCompat#performAction}.
   */
  public void afterCut() {
    mCutFinishTime = SystemClock.uptimeMillis();
  }

  /**
   * Stores the start time for a paste action. This should be called immediately before {@link
   * AccessibilityNodeInfoCompat#performAction}.
   */
  public void beforePaste() {
    mPasteStartTime = SystemClock.uptimeMillis();
  }

  /**
   * Stores the finish time for a paste action. This should be called immediately after {@link
   * AccessibilityNodeInfoCompat#performAction}.
   */
  public void afterPaste() {
    mPasteFinishTime = SystemClock.uptimeMillis();
  }

  public void beforeSelectAll() {
    mSelectAllStartTime = SystemClock.uptimeMillis();
  }

  public void afterSelectAll() {
    mSelectAllFinishTime = SystemClock.uptimeMillis();
  }

  /**
   * Returns whether the specified event time falls between the start and finish times of the last
   * cut action.
   *
   * @param eventTime The event time to check.
   * @return {@code true} if the event time falls between the start and finish times of the
   *     specified action.
   */
  public boolean hasCutActionAtTime(long eventTime) {
    return !((mCutStartTime == -1) || (mCutStartTime > eventTime))
        && !((mCutFinishTime >= mCutStartTime) && (mCutFinishTime < eventTime));
  }

  /**
   * Returns whether the specified event time falls between the start and finish times of the last
   * paste action.
   *
   * @param eventTime The event time to check.
   * @return {@code true} if the event time falls between the start and finish times of the
   *     specified action.
   */
  public boolean hasPasteActionAtTime(long eventTime) {
    return !((mPasteStartTime == -1) || (mPasteStartTime > eventTime))
        && !((mPasteFinishTime >= mPasteStartTime) && (mPasteFinishTime < eventTime));
  }

  public boolean hasSelectAllActionAtTime(long eventTime) {
    return !((mSelectAllFinishTime == -1) || (mSelectAllStartTime > eventTime))
        && !((mSelectAllFinishTime >= mSelectAllStartTime) && (mSelectAllFinishTime < eventTime));
  }
}
