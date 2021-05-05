/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.google.android.accessibility.talkback.contextmenu;

import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import com.google.android.accessibility.talkback.Pipeline;
import com.google.android.accessibility.talkback.analytics.TalkBackAnalytics;
import com.google.android.accessibility.utils.AccessibilityNodeInfoUtils;

/**
 * AbstractOnContextMenuItemClickListener implements {@link OnContextMenuItemClickListener} and is a
 * listener of menu item to store AccessibilityNodeInfo, Pipeline and TalkBackAnalytics. Menu item
 * needs to call {@link #clear()} to recycle node when it is clicked or dismisses. One listener may
 * be shared by multi-contextItems.
 */
public abstract class AbstractOnContextMenuItemClickListener
    implements OnContextMenuItemClickListener {
  // It can't be final when this node is recycled by the first item and is assigned to null. The
  // second item doesn't recycle again and cause crash.
  protected AccessibilityNodeInfoCompat node;

  protected final Pipeline.FeedbackReturner pipeline;
  protected final TalkBackAnalytics analytics;

  /** Copies node, caller retains ownership. */
  protected AbstractOnContextMenuItemClickListener(
      AccessibilityNodeInfoCompat node,
      Pipeline.FeedbackReturner pipeline,
      TalkBackAnalytics analytics) {
    this.node = AccessibilityNodeInfoUtils.obtain(node);
    this.pipeline = pipeline;
    this.analytics = analytics;
  }

  /** Cleans OnContextMenuItemClickListener of context menu item */
  @Override
  public void clear() {
    AccessibilityNodeInfoUtils.recycleNodes(node);
    node = null;
  }
}
