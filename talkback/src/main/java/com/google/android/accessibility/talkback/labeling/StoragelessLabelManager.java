/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.google.android.accessibility.talkback.labeling;

import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import com.google.android.accessibility.talkback.labeling.LabelsFetchRequest.OnLabelsFetchedListener;
import com.google.android.accessibility.utils.labeling.Label;
import com.google.android.accessibility.utils.labeling.LabelManager;
import java.util.Collections;
import org.checkerframework.checker.nullness.qual.Nullable;

/** LabelManager that does not support storing custom labels. */
public class StoragelessLabelManager extends TalkBackLabelManager {
  private class State implements LabelManager.State {
    @Override
    public long getLabelIdForNode(AccessibilityNodeInfoCompat node) {
      return Label.NO_ID;
    }

    @Override
    public boolean supportsLabel(AccessibilityNodeInfoCompat node) {
      return false;
    }

    @Override
    public boolean needsLabel(AccessibilityNodeInfoCompat node) {
      return StoragelessLabelManager.this.needsLabel(node);
    }
  }

  private final LabelManager.State stateReader = new StoragelessLabelManager.State();

  @Override
  public LabelManager.State stateReader() {
    return stateReader;
  }

  @Override
  public boolean setLabel(@Nullable AccessibilityNodeInfoCompat node, @Nullable String userLabel) {
    return false;
  }

  @Override
  public void getLabelsFromDatabase(@Nullable OnLabelsFetchedListener callback) {
    if (callback != null) {
      callback.onLabelsFetched(Collections.emptyList());
    }
  }

  @Override
  public @Nullable Label getLabelForViewIdFromCache(String resourceName) {
    return null;
  }

  @Override
  public boolean canAddLabel(@Nullable AccessibilityNodeInfoCompat node) {
    return false;
  }
}
