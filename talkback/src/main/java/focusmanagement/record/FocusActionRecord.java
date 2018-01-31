/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.google.android.accessibility.talkback.focusmanagement.record;

import android.os.SystemClock;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import com.google.android.accessibility.utils.AccessibilityNodeInfoUtils;
import java.util.Collection;

/**
 * A record of TalkBack performing {@link AccessibilityNodeInfoCompat#ACTION_ACCESSIBILITY_FOCUS}
 * action.
 */
public class FocusActionRecord {
  /**
   * Time when the accessibility focus action is performed. Initialized with
   * SystemClock.uptimeMillis().
   */
  private final long mActionTime;
  /** Node being accessibility focused. */
  private final AccessibilityNodeInfoCompat mFocusedNode;
  /** Extra information about source focus action. */
  private final FocusActionInfo mExtraInfo;

  /**
   * Constructs a FocusActionRecord.
   *
   * <p><strong>Note:</strong>Caller is responsible for recycling the {@code focusedNode}.
   *
   * @param focusedNode Node being accessibility focused.
   * @param extraInfo Extra information defined by action performer.
   * @param actionTime Time when focus action happens. Got by {@link SystemClock#uptimeMillis()}.
   */
  public FocusActionRecord(
      @NonNull AccessibilityNodeInfoCompat focusedNode,
      @NonNull FocusActionInfo extraInfo,
      long actionTime) {
    mFocusedNode = AccessibilityNodeInfoUtils.obtain(focusedNode);
    mExtraInfo = extraInfo;
    mActionTime = actionTime;
  }

  /**
   * Returns an instance of the focused node.
   *
   * <p><strong>Caller is responsible for recycling the node after use.</strong>
   */
  @Nullable
  public AccessibilityNodeInfoCompat getFocusedNode() {
    return AccessibilityNodeInfoUtils.obtain(mFocusedNode);
  }

  /** Returns extra information of the focus action. */
  @NonNull
  public FocusActionInfo getExtraInfo() {
    return mExtraInfo;
  }

  /**
   * Returns the time when the accessibility focus action happens, which is initialized with {@link
   * SystemClock#uptimeMillis()}.
   */
  public long getActionTime() {
    return mActionTime;
  }

  /** Returns the instance of focused node back into reuse. */
  public void recycle() {
    AccessibilityNodeInfoUtils.recycleNodes(mFocusedNode);
  }

  /** Returns a copied instance of another FocusActionRecord. */
  public static FocusActionRecord copy(FocusActionRecord record) {
    if (record == null) {
      return null;
    }
    return new FocusActionRecord(
        AccessibilityNodeInfoUtils.obtain(record.mFocusedNode),
        record.mExtraInfo,
        record.mActionTime);
  }

  /** Recycles a collection of record instances. */
  public static void recycle(Collection<FocusActionRecord> records) {
    if (records == null) {
      return;
    }
    for (FocusActionRecord record : records) {
      if (record != null) {
        record.recycle();
      }
    }
  }

  @Override
  public int hashCode() {
    int result = (int) (mActionTime ^ (mActionTime >>> 32));
    result = 31 * result + mFocusedNode.hashCode();
    result = 31 * result + mExtraInfo.hashCode();
    return result;
  }

  @Override
  public boolean equals(Object other) {
    if (other == null) {
      return false;
    }
    if (other == this) {
      return true;
    }
    if (getClass() != other.getClass()) {
      return false;
    }
    FocusActionRecord otherRecord = (FocusActionRecord) other;
    return (mFocusedNode.equals(otherRecord.mFocusedNode))
        && (mExtraInfo.equals(otherRecord.mExtraInfo))
        && (mActionTime == otherRecord.mActionTime);
  }

  @Override
  public String toString() {
    return "FocusActionRecord: \n"
        + "node="
        + mFocusedNode.toString()
        + "\n"
        + "time="
        + mActionTime
        + "\n"
        + "extraInfo="
        + mExtraInfo.toString();
  }
}
