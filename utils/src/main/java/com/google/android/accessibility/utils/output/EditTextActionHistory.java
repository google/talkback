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

package com.google.android.accessibility.utils.output;

import android.os.SystemClock;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;

/**
 * Maintains a history of when EditText actions or selection of {@link
 * AccessibilityNodeInfoUtils#isNonEditableSelectableText(AccessibilityNodeInfoCompat)} occurred.
 */
public class EditTextActionHistory {

  /** Read-only interface, for use by event-interpreters. */
  public interface Provider {
    public boolean hasPasteActionAtTime(long eventTime);

    public boolean hasCutActionAtTime(long eventTime);

    public boolean hasSelectAllActionAtTime(long eventTime);

    public boolean hasSetTextActionAtTime(long eventTime);
  }

  /** Instance of read-only interface. */
  public final Provider provider =
      new Provider() {
        @Override
        public boolean hasPasteActionAtTime(long eventTime) {
          return EditTextActionHistory.this.hasPasteActionAtTime(eventTime);
        }

        @Override
        public boolean hasCutActionAtTime(long eventTime) {
          return EditTextActionHistory.this.hasCutActionAtTime(eventTime);
        }

        @Override
        public boolean hasSelectAllActionAtTime(long eventTime) {
          return EditTextActionHistory.this.hasSelectAllActionAtTime(eventTime);
        }

        @Override
        public boolean hasSetTextActionAtTime(long eventTime) {
          return EditTextActionHistory.this.hasSetTextActionAtTime(eventTime);
        }
      };

  /** Map of action identifiers to start times. */
  private long mCutStartTime = -1;
  private long mPasteStartTime = -1;
  private long mSelectAllStartTime = -1;
  private long setTextStartTime = -1;

  /** Map of action identifiers to finish times. */
  private long mCutFinishTime = -1;
  private long mPasteFinishTime = -1;
  private long mSelectAllFinishTime = -1;
  private long setTextFinishTime = -1;

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
   * Stores the start time for a set text action. This should be called immediately before {@link
   * AccessibilityNodeInfoCompat#performAction}.
   */
  public void beforeSetText() {
    setTextStartTime = SystemClock.uptimeMillis();
  }

  /**
   * Stores the finish time for a set text action. This should be called immediately after {@link
   * AccessibilityNodeInfoCompat#performAction}.
   */
  public void afterSetText() {
    setTextFinishTime = SystemClock.uptimeMillis();
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

  public boolean hasSetTextActionAtTime(long eventTime) {
    return !((setTextFinishTime == -1) || (setTextStartTime > eventTime))
        && !((setTextFinishTime >= setTextStartTime) && (setTextFinishTime < eventTime));
  }
}
