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
import android.support.annotation.Nullable;
import android.support.annotation.VisibleForTesting;
import android.support.v4.util.LruCache;
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import com.google.android.accessibility.utils.AccessibilityEventUtils;
import com.google.android.accessibility.utils.AccessibilityNodeInfoUtils;
import com.google.android.accessibility.utils.LogUtils;
import com.google.android.accessibility.utils.Role;
import java.util.Iterator;
import java.util.LinkedList;

/**
 * A data structure to cache and query history of accessibility focus action.
 *
 * <p>Accessibility service performs {@link AccessibilityNodeInfoCompat#ACTION_ACCESSIBILITY_FOCUS}
 * on nodes via framework, and receives the result {@link
 * AccessibilityEvent#TYPE_VIEW_ACCESSIBILITY_FOCUSED} event from framework. These two processes are
 * asynchronous. Sometimes we need the information of source action when generating feedback for the
 * result event. This class is used to fill in the gap between actions and events.
 *
 * <p><strong>Usage:</strong>The service should call {@link
 * #onAccessibilityFocusAction(AccessibilityNodeInfoCompat, FocusActionInfo, long)} every time when
 * successfully performing focus action, and query action information with {@link
 * #matchFocusActionRecordFromEvent(AccessibilityEvent)}, {@link
 * #getLastFocusActionRecordInWindow(int)} and {@link #getLastEditableFocusActionRecord()}.
 */
public final class AccessibilityFocusActionHistory {
  /** Maximum size of {@link #mWindowIdToFocusActionRecordMap} */
  @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
  static final int MAXIMUM_WINDOW_MAP_SIZE = 10;

  /** Maximum size of {@link #mFocusActionRecordList} */
  @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
  static final int MAXIMUM_RECORD_QUEUE_SIZE = 5;

  /**
   * Maximum difference between action time and event time within which we think the event might
   * result from the action.
   */
  @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
  static final int TIMEOUT_TOLERANCE_MS = 300;

  /**
   * A queue of {@link FocusActionRecord} with maximum size of {@link #MAXIMUM_RECORD_QUEUE_SIZE}.
   * The earliest record will be dropped and recycled when the queue grows up to its maximum size.
   */
  private final LinkedList<FocusActionRecord> mFocusActionRecordList;

  /**
   * Keeps the last focus action record in each window. Map from window ID to {@link
   * FocusActionRecord} in each window with maximum size of {@link #MAXIMUM_WINDOW_MAP_SIZE}. The
   * eldest-accessed record will be removed and recycled when the map grows up to its maximum size.
   */
  // TODO: Check if we should use <windowId, windowTitle> pair as the map key.
  // Window change implemented with fragment transition does not change window ID, we might need to
  // use window title as well to identify a window state.
  private final LruCache<Integer, FocusActionRecord> mWindowIdToFocusActionRecordMap;

  /** The last {@link FocusActionRecord} on an editable node. */
  private FocusActionRecord mLastEditableFocusActionRecord;

  private static AccessibilityFocusActionHistory sInstance;

  public static AccessibilityFocusActionHistory getInstance() {
    if (sInstance == null) {
      sInstance = new AccessibilityFocusActionHistory();
    }
    return sInstance;
  }

  private AccessibilityFocusActionHistory() {
    mFocusActionRecordList = new LinkedList<>();
    mWindowIdToFocusActionRecordMap =
        new LruCache<Integer, FocusActionRecord>(MAXIMUM_WINDOW_MAP_SIZE) {
          /**
           * Recycles the source node in the record when the focus action record is removed from the
           * cache.
           */
          @Override
          protected void entryRemoved(
              boolean evicted,
              Integer key,
              FocusActionRecord oldValue,
              FocusActionRecord newValue) {
            if (oldValue != null) {
              oldValue.recycle();
            }
          }
        };
  }

  /**
   * Registers the action information. Called immediately after an accessibility focus action is
   * performed.
   *
   * <p><strong>Note:</strong> Caller is responsible for recycling the {@code node}.
   *
   * @param node Node being accessibility focused.
   * @param extraData Extra information of the action.
   * @param actionTime {@link SystemClock#uptimeMillis()} right before performing focus action.
   */
  public void onAccessibilityFocusAction(
      AccessibilityNodeInfoCompat node, FocusActionInfo extraData, long actionTime) {
    // FocusActionRecord handles making a copy of 'node'. We don't nee to call obtain() here.
    FocusActionRecord record = new FocusActionRecord(node, extraData, actionTime);

    // Add to the record queue.
    mFocusActionRecordList.offer(record);
    if (mFocusActionRecordList.size() > MAXIMUM_RECORD_QUEUE_SIZE) {
      // Poll and recycle the eldest order if the queue grows up its maximum size.
      mFocusActionRecordList.pollFirst().recycle();
    }

    // Add to the window record map.
    mWindowIdToFocusActionRecordMap.put(node.getWindowId(), FocusActionRecord.copy(record));

    // Update the last editable node focus action.
    if (node.isEditable() || (Role.getRole(node) == Role.ROLE_EDIT_TEXT)) {
      if (mLastEditableFocusActionRecord != null) {
        mLastEditableFocusActionRecord.recycle();
      }
      mLastEditableFocusActionRecord = FocusActionRecord.copy(record);
    }
  }

  /** Returns the last focus action successfully performed in the window with given windowId. */
  @Nullable
  public FocusActionRecord getLastFocusActionRecordInWindow(int windowId) {
    return mWindowIdToFocusActionRecordMap.get(windowId);
  }

  /** Returns the last focus action. */
  @Nullable
  public FocusActionRecord getLastFocusActionRecord() {
    return mFocusActionRecordList.peekLast();
  }

  /** Returns the last focus action on editable node. */
  @Nullable
  public FocusActionRecord getLastEditableFocusActionRecord() {
    return mLastEditableFocusActionRecord;
  }

  /**
   * Matches {@link AccessibilityEvent#TYPE_VIEW_ACCESSIBILITY_FOCUSED} event to the source action.
   *
   * @param event The {@link AccessibilityEvent} queried.
   * @return The matched FocusActionRecord or null.
   */
  @Nullable
  public FocusActionRecord matchFocusActionRecordFromEvent(AccessibilityEvent event) {
    if (!AccessibilityEventUtils.eventMatchesAnyType(
        event, AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED)) {
      return null;
    }
    AccessibilityNodeInfoCompat eventNode = AccessibilityNodeInfoUtils.toCompat(event.getSource());
    if (eventNode == null) {
      return null;
    }
    long eventTime = event.getEventTime();
    FocusActionRecord result = null;
    // Iterate from the newest(last) record to the eldest(first) record.
    Iterator<FocusActionRecord> iterator = mFocusActionRecordList.descendingIterator();
    while (iterator.hasNext()) {
      FocusActionRecord record = iterator.next();
      long timeDiff = eventTime - record.getActionTime();
      AccessibilityNodeInfoCompat recordNode = record.getFocusedNode();

      boolean timeMatches = timeDiff >= 0 && timeDiff < TIMEOUT_TOLERANCE_MS;
      boolean nodeMatches = eventNode.equals(recordNode);
      AccessibilityNodeInfoUtils.recycleNodes(recordNode);
      if (timeMatches && nodeMatches) {
        result = FocusActionRecord.copy(record);
        break;
      }
    }
    AccessibilityNodeInfoUtils.recycleNodes(eventNode);
    LogUtils.log(
        this, Log.DEBUG, "Match event to focus action.\nEvent:%s\nRecord: %s", event, result);
    return result;
  }

  /** Clears all the action records. */
  public void clear() {
    // Recycle and clear the record list.
    FocusActionRecord.recycle(mFocusActionRecordList);
    mFocusActionRecordList.clear();

    // Clear the LruCache.
    mWindowIdToFocusActionRecordMap.evictAll();

    // Recycle and clear editable node focus record.
    if (mLastEditableFocusActionRecord != null) {
      mLastEditableFocusActionRecord.recycle();
      mLastEditableFocusActionRecord = null;
    }
  }
}
