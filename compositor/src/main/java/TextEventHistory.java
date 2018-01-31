/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.google.android.accessibility.compositor;

import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import com.google.android.accessibility.utils.AccessibilityEventUtils;
import com.google.android.accessibility.utils.AccessibilityNodeInfoUtils;
import com.google.android.accessibility.utils.EditTextActionHistory;
import com.google.android.accessibility.utils.LogUtils;

/**
 * Wrapper around text event history data. This wrapper helps share data between EventFilter and
 * TextEventInterpreter.
 */
public class TextEventHistory {

  ////////////////////////////////////////////////////////////////////////////////////
  // Constants

  public static final int NO_INDEX = -1;

  ////////////////////////////////////////////////////////////////////////////////////
  // Member data

  public boolean trace = false;

  private EditTextActionHistory mEditTextHistory;

  // Event history used by text change events
  private int mTextChangesAwaitingSelection = 0;
  private long mLastTextChangeTime = -1;
  private CharSequence mLastTextChangePackageName = null;
  private AccessibilityEvent mLastKeptTextSelection = null;

  // Event history used by selection change events
  private AccessibilityEvent mLastProcessedEvent;
  private int mLastFromIndex = NO_INDEX;
  private int mLastToIndex = NO_INDEX;
  private AccessibilityNodeInfo mLastNode;

  // //////////////////////////////////////////////////////////////////////////////////
  // Construction

  public TextEventHistory(EditTextActionHistory editTextActionHistory) {
    mEditTextHistory = editTextActionHistory;
  }

  ////////////////////////////////////////////////////////////////////////////////////
  // Methods to get/set member data

  public void setTextChangesAwaitingSelection(int changes) {
    mTextChangesAwaitingSelection = changes;
    traceSet("TextChangesAwaitingSelection", changes);
  }

  public void incrementTextChangesAwaitingSelection(int increment) {
    mTextChangesAwaitingSelection += increment;
    traceSet("TextChangesAwaitingSelection", mTextChangesAwaitingSelection);
  }

  public int getTextChangesAwaitingSelection() {
    return mTextChangesAwaitingSelection;
  }

  public void setLastTextChangeTime(long time) {
    mLastTextChangeTime = time;
    traceSet("LastTextChangeTime", time);
  }

  public long getLastTextChangeTime() {
    return mLastTextChangeTime;
  }

  public void setLastTextChangePackageName(CharSequence name) {
    mLastTextChangePackageName = name;
    traceSet("LastTextChangePackageName", name);
  }

  public CharSequence getLastTextChangePackageName() {
    return mLastTextChangePackageName;
  }

  /** Caller must recycle event. */
  public void setLastKeptTextSelection(AccessibilityEvent event) {
    mLastKeptTextSelection = AccessibilityEventUtils.replace(mLastKeptTextSelection, event);
    traceSet("LastKeptTextSelection", "(object)");
  }

  public AccessibilityEvent getLastKeptTextSelection() {
    return mLastKeptTextSelection;
  }

  public boolean hasPasteActionAtTime(long eventTime) {
    return mEditTextHistory.hasPasteActionAtTime(eventTime);
  }

  public boolean hasCutActionAtTime(long eventTime) {
    return mEditTextHistory.hasCutActionAtTime(eventTime);
  }

  public boolean hasSelectAllActionAtTime(long eventTime) {
    return mEditTextHistory.hasSelectAllActionAtTime(eventTime);
  }

  public void setLastFromIndex(int index) {
    mLastFromIndex = index;
    traceSet("LastFromIndex", index);
  }

  public int getLastFromIndex() {
    return mLastFromIndex;
  }

  public void setLastToIndex(int index) {
    mLastToIndex = index;
    traceSet("LastToIndex", index);
  }

  public int getLastToIndex() {
    return mLastToIndex;
  }

  /** TextEventHistory will recycle newNode. */
  public void setLastNode(AccessibilityNodeInfo newNode) {
    try {
      AccessibilityNodeInfoUtils.recycleNodes(mLastNode);
      mLastNode = newNode;
      newNode = null;
      traceSet("LastNode", "(object)");
    } finally {
      AccessibilityNodeInfoUtils.recycleNodes(newNode);
    }
  }

  public AccessibilityNodeInfo getLastNode() {
    return mLastNode;
  }

  /** Caller must recycle newEvent. */
  public void setLastProcessedEvent(AccessibilityEvent newEvent) {
    mLastProcessedEvent = AccessibilityEventUtils.replace(mLastProcessedEvent, newEvent);
    traceSet("LastProcessedEvent", "(object)");
  }

  ////////////////////////////////////////////////////////////////////////////////////
  // Methods to log set operations

  private <T> void traceSet(String member, T value) {
    if (!trace) {
      return;
    }
    LogUtils.log(this, Log.VERBOSE, "set %s = %s", member, value);
  }
}
