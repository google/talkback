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

import android.accessibilityservice.AccessibilityService;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import com.google.common.collect.ImmutableList;

/**
 * Interface for snapshot of UI. Snapshot contains a forrest of {@link SnapshotWindow} that each
 * contain a tree of {@link SnapshotView}. The interface is written in java to make integration
 * easier.
 */
public interface Snapshot {

  /**
   * Returns a {@link EventSource} from which snapshot was built/triggered if available. Returns
   * null otherwise.
   */
  @Nullable
  default EventSource getEventSource() {
    return null;
  }

  /**
   * Returns list of windows that represent what is on UI screen, as returned by {@link
   * AccessibilityService#getWindows()}.
   */
  @NonNull
  default ImmutableList<SnapshotWindow> getWindows() {
    return ImmutableList.of();
  }

  /** Get metric specific to the entire snapshot. */
  @NonNull
  default SnapshotMetric getMetric() {
    // TODO: (b/229649593) remove metrics as it does not support all implementation.
    return new Snapshot.SnapshotMetric(-1, false, false);
  }

  /** Returns true if the view is found in the snapshot. False otherwise. */
  default boolean contains(@NonNull SnapshotView view) {
    return false;
  }

  /** Finds the SnapshotView from this Snapshot, which represents the same View as target-node. */
  @Nullable
  default SnapshotView find(@Nullable AccessibilityNodeInfoCompat target) {
    return null;
  }

  /** Finds the SnapshotView from this Snapshot, which represents the same View as target-node. */
  @Nullable
  default SnapshotView find(@Nullable SnapshotView target) {
    return null;
  }

  /**
   * Returns a new snapshot updated from the AccessibilityCache, or null if the snapshot cannot be
   * updated.
   */
  @Nullable
  default Snapshot refresh() {
    @Nullable EventSource eventSource = getEventSource();
    return (eventSource == null) ? null : eventSource.refresh();
  }

  /** Metric regarding the entire tree. */
  class SnapshotMetric {
    /** Total creation time of snapshot. */
    public final long creationTime;
    /** True if snapshot view tree contains loop. */
    public final boolean viewsHasLoop;
    /** True if snapshot window tree contains loop. */
    public final boolean windowsHasLoop;

    public SnapshotMetric(long creationTime, boolean hasLoop, boolean windowsHasLoop) {
      this.creationTime = creationTime;
      this.viewsHasLoop = hasLoop;
      this.windowsHasLoop = windowsHasLoop;
    }
  }
}
