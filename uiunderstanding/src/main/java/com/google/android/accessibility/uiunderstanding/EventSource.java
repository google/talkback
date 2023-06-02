/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.google.android.accessibility.uiunderstanding;

import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import com.google.android.accessibility.utils.Consumer;
import com.google.android.accessibility.utils.FocusFinder;
import com.google.android.libraries.accessibility.utils.log.LogUtils;
import com.google.common.collect.ImmutableList;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.Executor;

/**
 * AccessibilityServices should instantiate EventSource, feed it raw events, and receive cleaned
 * events. AccessibilityServices should also pass actions & policy & state to EventSource, to
 * influence the cleanup of events. Also read
 * go/ui-understanding-architecture#heading=h.to9mv8ialy51
 */
public class EventSource {
  //////////////////////////////////////////////////////////////////////////////////
  // Constants

  private static final String LOG_TAG = "EventSource";

  //////////////////////////////////////////////////////////////////////////////////
  // Member data

  @NonNull private final Consumer<Event> eventConsumer;
  @NonNull private final FocusFinder focusFinder;
  @NonNull private final Deque<ActionRecord> actionHistory = new ArrayDeque<>();
  @Nullable private Snapshot currentSnapshot;

  //////////////////////////////////////////////////////////////////////////////////
  // Construction

  public EventSource(@NonNull FocusFinder focusFinder, @NonNull Consumer<Event> eventConsumer) {
    this.eventConsumer = eventConsumer;
    this.focusFinder = focusFinder;
  }

  //////////////////////////////////////////////////////////////////////////////////
  // Methods

  @NonNull
  public Snapshot requestNodeTree() {
    return getCurrentSnapshot();
  }

  public void requestNodeTreeAsync(Executor executor) {
    executor.execute(() -> eventConsumer.accept(new Event.SnapshotDone(getCurrentSnapshot())));
  }

  /** Returns the cached-snapshot, maybe blocking to create the snapshot. */
  @NonNull
  private Snapshot getCurrentSnapshot() {
    if (currentSnapshot == null) {
      currentSnapshot = generateSnapshot();
    }
    return currentSnapshot;
  }

  /** Returns target-view refreshed from app, in new snapshot refreshed from AccessibilityCache. */
  @Nullable
  SnapshotView refresh(@Nullable SnapshotViewInternal view) {
    refresh();
    boolean viewRefreshed = (view != null) && view.refreshRawNode();
    if (!viewRefreshed) {
      LogUtils.d(LOG_TAG, "refresh() failed to refresh view=%s", view);
    }
    return currentSnapshot.find(view);
  }

  /** Returns new snapshot refreshed from AccessibilityCache. */
  @Nullable
  Snapshot refresh() {
    currentSnapshot = generateSnapshot();
    return currentSnapshot;
  }

  @NonNull
  private synchronized Snapshot generateSnapshot() {
    // TODO: Replace with specific snapshot implementation.
    return new Snapshot() {
      @Override
      @Nullable
      public EventSource getEventSource() {
        return null;
      }

      @Override
      @NonNull
      public ImmutableList<SnapshotWindow> getWindows() {
        return ImmutableList.of();
      }

      @Override
      @Nullable
      public Snapshot refresh() {
        return null;
      }

      @Override
      public boolean contains(@Nullable SnapshotView target) {
        return false;
      }

      @Override
      @NonNull
      public SnapshotMetric getMetric() {
        return new SnapshotMetric(0, false, false);
      }
    };
  }

  /** Returns accessibility-focus, or null if nothing is focused. May update snapshot. */
  @Nullable
  public SnapshotView getAccessibilityFocus() {
    return getAccessibilityFocus(null);
  }

  @Nullable
  public SnapshotView getAccessibilityFocus(@Nullable Snapshot snapshotOld) {
    // Try to find focused raw-node.
    @Nullable AccessibilityNodeInfoCompat focus = focusFinder.findAccessibilityFocus();
    if (focus == null) {
      return null;
    }

    // Try to find focus in the old snapshot.
    if (snapshotOld != null) {
      @Nullable SnapshotView focusInSnapshot = snapshotOld.find(focus);
      if (focusInSnapshot != null) {
        return focusInSnapshot;
      }
    }

    // Try to find focus in the current snapshot.
    if (currentSnapshot != null) {
      @Nullable SnapshotView focusInSnapshot = currentSnapshot.find(focus);
      if (focusInSnapshot != null) {
        return focusInSnapshot;
      }
    }

    // Try to find focus in a refreshed snapshot.
    refresh();
    return (currentSnapshot == null) ? null : currentSnapshot.find(focus);
  }

  // Accepts event, to track event from creation to action, for performance and debugging.
  boolean performAction(
      @NonNull SnapshotView view, int actionId, @Nullable Bundle bundle, @Nullable Event event) {

    LogUtils.i(
        LOG_TAG, "performAction() actionId=%d arg bundle=%s for event=%s", actionId, bundle, event);

    // Execute action.
    boolean result = false;
    if (view instanceof SnapshotViewInternal) {
      ((SnapshotViewInternal) view).performActionOnRawNode(actionId, bundle);
    }

    // Record action history, and enforce maximum-history-size limit.
    @NonNull ActionRecord action = new ActionRecord(actionId, bundle);
    actionHistory.addLast(action);
    while (100 < actionHistory.size()) {
      actionHistory.removeFirst();
    }

    return result;
  }
}
