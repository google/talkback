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

package com.google.android.accessibility.talkback.focusmanagement;

import android.os.Message;
import android.support.v4.view.accessibility.AccessibilityEventCompat;
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import android.support.v4.view.accessibility.AccessibilityRecordCompat;
import android.util.Pair;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import com.google.android.accessibility.compositor.GlobalVariables;
import com.google.android.accessibility.talkback.TalkBackService;
import com.google.android.accessibility.talkback.eventprocessor.EventState;
import com.google.android.accessibility.utils.AccessibilityEventUtils;
import com.google.android.accessibility.utils.AccessibilityNodeInfoUtils;
import com.google.android.accessibility.utils.FormFactorUtils;
import com.google.android.accessibility.utils.PerformActionUtils;
import com.google.android.accessibility.utils.Performance.EventId;
import com.google.android.accessibility.utils.WeakReferenceHandler;
import com.google.android.accessibility.utils.WindowManager;
import com.google.android.accessibility.utils.compat.accessibilityservice.AccessibilityServiceCompatUtils;
import com.google.android.accessibility.utils.input.CursorController;
import java.util.ArrayDeque;
import java.util.Iterator;

/** The {@link FocusProcessor} to synchronize accessibility focus onto input focus. */
public class FocusProcessorForSynchronization extends FocusProcessor {
  private static final int MAX_CACHED_FOCUSED_RECORD_QUEUE = 10;

  private static final int SYNC_FOCUS_DELAY_MS = 100;
  private static final int SYNC_FOCUS_DELAY_WITH_IME_MS = 500;

  /** Event types that are handled by FocusProcessorForSynchronization. */
  private static final int MASK_EVENTS_HANDLED_BY_PROCESSOR_FOR_SYNCRONIZATION =
      AccessibilityEvent.TYPE_VIEW_FOCUSED
          | AccessibilityEvent.TYPE_VIEW_SELECTED
          | AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
          | AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
          | AccessibilityEvent.TYPE_VIEW_SCROLLED
          | AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED
          | AccessibilityEvent.TYPE_WINDOWS_CHANGED;

  private AccessibilityFocusManager mA11yFocusManager;

  private TalkBackService mService;
  private FirstWindowFocusManager mFirstWindowFocusManager;
  private final GlobalVariables mGlobalVariables;

  // The previous AccessibilityRecordCompat that failed to focus, but it is potentially
  // focusable when view scrolls, or window state changes.
  private final ArrayDeque<Pair<AccessibilityRecordCompat, Integer>>
      mCachedPotentiallyFocusableRecordQueue = new ArrayDeque<>(MAX_CACHED_FOCUSED_RECORD_QUEUE);

  private SyncFocusHandler mSyncFocusHandler;

  /** Whether the IME was open the last time the window state was changed. */
  private boolean mWasImeOpen;

  public FocusProcessorForSynchronization(
      AccessibilityFocusManager accessibilityFocusManager,
      TalkBackService service,
      GlobalVariables globalVariables) {
    mA11yFocusManager = accessibilityFocusManager;
    mService = service;
    mGlobalVariables = globalVariables;
    mFirstWindowFocusManager = new FirstWindowFocusManager(service);
    mSyncFocusHandler = new SyncFocusHandler(this);
  }

  @Override
  public int getEventTypes() {
    return MASK_EVENTS_HANDLED_BY_PROCESSOR_FOR_SYNCRONIZATION;
  }

  @Override
  public void onAccessibilityEvent(AccessibilityEvent event, EventId eventId) {
    switch (event.getEventType()) {
      case AccessibilityEvent.TYPE_VIEW_FOCUSED:
      case AccessibilityEvent.TYPE_VIEW_SELECTED:
        if (!mFirstWindowFocusManager.shouldProcessFocusEvent(event)) {
          return;
        }
        boolean isViewFocusedEvent = (AccessibilityEvent.TYPE_VIEW_FOCUSED == event.getEventType());
        final AccessibilityRecordCompat record = AccessibilityEventCompat.asRecord(event);
        if (!setFocusOnView(record, isViewFocusedEvent, eventId)) {
          // It is possible that the only speakable child of source node is invisible
          // at the moment, but could be made visible when view scrolls, or window state
          // changes. Cache it now. And try to focus on the cached record on:
          // VIEW_SCROLLED, WINDOW_CONTENT_CHANGED, WINDOW_STATE_CHANGED.
          // The above 3 are the events that could affect view visibility.
          cachePotentiallyFocusableRecord(record, event.getEventType());
        } else {
          emptyCachedPotentialFocusQueue();
        }
        break;
      case AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED:
        if (!EventState.getInstance()
            .checkAndClearRecentFlag(EventState.EVENT_SKIP_FOCUS_SYNC_FROM_WINDOW_STATE_CHANGED)) {
          scheduleSyncFocus(eventId);
        }
        mFirstWindowFocusManager.registerWindowChange(event);
        tryFocusCachedRecord(eventId);
        break;
      case AccessibilityEvent.TYPE_WINDOWS_CHANGED:
        if (!EventState.getInstance()
            .checkAndClearRecentFlag(EventState.EVENT_SKIP_FOCUS_SYNC_FROM_WINDOWS_CHANGED)) {
          scheduleSyncFocus(eventId);
        }
        break;
      case AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED:
        // TODO: Move this into a handler to avoid latency.
        tryFocusCachedRecord(eventId);
        break;
      case AccessibilityEvent.TYPE_VIEW_SCROLLED:
        // TODO: Verify if it's the best place to call this. Probably when there is a valid
        // a11y focus after handleViewManualScrolled or handleViewAutoScrolled, we don't need to
        // tryFocusCachedRecord(EventId).
        tryFocusCachedRecord(eventId);
        break;
      case AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED:
        // TODO: Move this to a better location.
        emptyCachedPotentialFocusQueue();
        break;
      default:
        break;
    }
  }

  private void scheduleSyncFocus(EventId eventId) {
    mSyncFocusHandler.removeMessages(SyncFocusHandler.MESSAGE_WHAT_SYNC);
    mSyncFocusHandler.sendMessageDelayed(
        mSyncFocusHandler.obtainMessage(SyncFocusHandler.MESSAGE_WHAT_SYNC, eventId),
        mWasImeOpen ? SYNC_FOCUS_DELAY_WITH_IME_MS : SYNC_FOCUS_DELAY_MS);
  }

  private void attemptSyncA11yAndInputFocus(EventId eventId) {
    // TODO: Break dependency on TalkBackService.
    AccessibilityNodeInfoCompat root =
        AccessibilityServiceCompatUtils.getRootInAccessibilityFocusedWindow(mService);
    if (root == null) {
      return;
    }

    WindowManager windowManager = new WindowManager(false /* ignore RTL state */);
    windowManager.setWindows(mService.getWindows());
    boolean isImeOpen = windowManager.isInputWindowOnScreen();

    // TODO: We can replace it with mAccessibilityFocusManager.getAccessibilityFocus().
    AccessibilityNodeInfoCompat a11yFocus =
        root.findFocus(AccessibilityNodeInfoCompat.FOCUS_ACCESSIBILITY);
    AccessibilityNodeInfoCompat inputFocus =
        root.findFocus(AccessibilityNodeInfoCompat.FOCUS_INPUT);
    try {
      if (a11yFocus == null
          && inputFocus != null
          && AccessibilityNodeInfoUtils.isEditable(inputFocus)) {
        // If the IME was recently closed, don't re-announce the node when focusing it.
        if (mWasImeOpen && !isImeOpen) {
          mGlobalVariables.setFlag(GlobalVariables.EVENT_SKIP_FOCUS_PROCESSING_AFTER_IME_CLOSED);
        }

        mGlobalVariables.setFlag(GlobalVariables.EVENT_SYNCED_ACCESSIBILITY_FOCUS);
        EventState.getInstance().setFlag(EventState.EVENT_HINT_FOR_SYNCED_ACCESSIBILITY_FOCUS);
        PerformActionUtils.performAction(
            inputFocus, AccessibilityNodeInfoCompat.ACTION_ACCESSIBILITY_FOCUS, eventId);
      }
    } finally {
      AccessibilityNodeInfoUtils.recycleNodes(root, a11yFocus, inputFocus);
    }

    mWasImeOpen = isImeOpen;
  }

  private void tryFocusCachedRecord(EventId eventId) {
    if (mCachedPotentiallyFocusableRecordQueue.isEmpty()) {
      return;
    }

    Iterator<Pair<AccessibilityRecordCompat, Integer>> iterator =
        mCachedPotentiallyFocusableRecordQueue.descendingIterator();

    while (iterator.hasNext()) {
      Pair<AccessibilityRecordCompat, Integer> focusableRecord = iterator.next();
      AccessibilityRecordCompat record = focusableRecord.first;
      int eventType = focusableRecord.second;
      if (setFocusOnView(record, eventType == AccessibilityEvent.TYPE_VIEW_FOCUSED, eventId)) {
        emptyCachedPotentialFocusQueue();
        return;
      }
    }
  }

  private void emptyCachedPotentialFocusQueue() {
    if (mCachedPotentiallyFocusableRecordQueue.isEmpty()) {
      return;
    }

    for (Pair<AccessibilityRecordCompat, Integer> focusableRecord :
        mCachedPotentiallyFocusableRecordQueue) {
      focusableRecord.first.recycle();
    }
    mCachedPotentiallyFocusableRecordQueue.clear();
  }

  private void cachePotentiallyFocusableRecord(AccessibilityRecordCompat record, int eventType) {
    if (mCachedPotentiallyFocusableRecordQueue.size() == MAX_CACHED_FOCUSED_RECORD_QUEUE) {
      mCachedPotentiallyFocusableRecordQueue.remove().first.recycle();
    }

    mCachedPotentiallyFocusableRecordQueue.add(
        new Pair<>(AccessibilityRecordCompat.obtain(record), eventType));
  }

  /**
   * @param record the AccessbilityRecord for the event.
   * @param isViewFocusedEvent true if the event is TYPE_VIEW_FOCUSED, otherwise it is
   *     TYPE_VIEW_SELECTED.
   */
  private boolean setFocusOnView(
      AccessibilityRecordCompat record, boolean isViewFocusedEvent, EventId eventId) {
    AccessibilityNodeInfoCompat source = null;
    AccessibilityNodeInfoCompat existing = null;
    AccessibilityNodeInfoCompat child = null;

    try {
      source = record.getSource();
      if (source == null || !source.refresh()) {
        return false;
      }

      if (record.getItemCount() > 0) {
        final int index = (record.getCurrentItemIndex() - record.getFromIndex());
        if (index >= 0 && index < source.getChildCount()) {
          child = source.getChild(index);
          if (child != null) {
            if (AccessibilityNodeInfoUtils.isTopLevelScrollItem(child)
                && mA11yFocusManager.tryFocusing(child, false, eventId)) {
              return true;
            }
          }
        }
      }

      if (!isViewFocusedEvent) {
        return false;
      }

      // Logic below is only specific to TYPE_VIEW_FOCUSED event.
      // Try focusing the source node.
      if (mA11yFocusManager.tryFocusing(source, false, eventId)) {
        return true;
      }

      // If we fail and the source node already contains focus, abort.
      existing = source.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY);
      if (existing != null) {
        return false;
      }

      // If we fail to focus a node, perhaps because it is a focusable
      // but non-speaking container, we should still attempt to place
      // focus on a speaking child within the container.
      child =
          AccessibilityNodeInfoUtils.searchFromBfs(
              source, AccessibilityNodeInfoUtils.FILTER_SHOULD_FOCUS);
      return child != null && mA11yFocusManager.tryFocusing(child, false, eventId);

    } finally {
      AccessibilityNodeInfoUtils.recycleNodes(source, existing, child);
    }
  }

  private static class SyncFocusHandler
      extends WeakReferenceHandler<FocusProcessorForSynchronization> {
    private static final int MESSAGE_WHAT_SYNC = 1;

    private SyncFocusHandler(FocusProcessorForSynchronization parent) {
      super(parent);
    }

    @Override
    protected void handleMessage(Message message, FocusProcessorForSynchronization parent) {
      if (parent == null || message.what != MESSAGE_WHAT_SYNC) {
        return;
      }
      EventId eventId = (EventId) message.obj;
      parent.attemptSyncA11yAndInputFocus(eventId);
    }
  }

  private static class FirstWindowFocusManager implements CursorController.CursorListener {
    private static final int MISS_FOCUS_DELAY_NORMAL_MS = 300;
    // TODO: Revisit the delay due to TV transitions if changes.
    private static final int MISS_FOCUS_DELAY_TV_MS = 1200; // Longer transitions on TV.

    private static final String SOFT_INPUT_WINDOW = "android.inputmethodservice.SoftInputWindow";

    private long mLastWindowStateChangeEventTime;
    private int mLastWindowId;
    private boolean mIsFirstFocusInWindow;
    private final TalkBackService mService;

    private FirstWindowFocusManager(TalkBackService service) {
      mService = service;
      mService.getCursorController().addCursorListener(this);
    }

    private void registerWindowChange(AccessibilityEvent event) {
      mLastWindowStateChangeEventTime = event.getEventTime();
      if (mLastWindowId != event.getWindowId() && !shouldIgnoreWindowChangeEvent(event)) {
        mLastWindowId = event.getWindowId();
        mIsFirstFocusInWindow = true;
      }
    }

    /**
     * Decides whether to ignore an event for purposes of registering the first-focus window change;
     * returns true events that come from non-main windows such as IMEs.
     */
    private boolean shouldIgnoreWindowChangeEvent(AccessibilityEvent event) {
      if (event.getWindowId() == -1) {
        return true;
      }

      // The specific SoftInputWindow check seems to be necessary for Android TV.
      if (SOFT_INPUT_WINDOW.equals(event.getClassName())) {
        return true;
      }

      return AccessibilityEventUtils.isNonMainWindowEvent(event);
    }

    @Override
    public void beforeSetCursor(AccessibilityNodeInfoCompat newCursor, int action) {
      // Manual focus actions should go through, even if mLastWindowId doesn't match.
      if (action == AccessibilityNodeInfoCompat.ACTION_FOCUS) {
        mLastWindowId = newCursor.getWindowId();
      }
    }

    @Override
    public void onSetCursor(AccessibilityNodeInfoCompat newCursor, int action) {}

    private boolean shouldProcessFocusEvent(AccessibilityEvent event) {
      boolean isFirstFocus = mIsFirstFocusInWindow;
      mIsFirstFocusInWindow = false;

      if (mLastWindowId != event.getWindowId()) {
        mLastWindowId = event.getWindowId();
        return false;
      }

      int focusDelay =
          FormFactorUtils.getInstance(mService).isTv()
              ? MISS_FOCUS_DELAY_TV_MS
              : MISS_FOCUS_DELAY_NORMAL_MS;

      return !isFirstFocus || event.getEventTime() - mLastWindowStateChangeEventTime > focusDelay;
    }
  }
}
