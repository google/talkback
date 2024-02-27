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

/**
 * AbstractOnContextMenuItemClickListener implements {@link OnContextMenuItemClickListener} and is a
 * listener of menu item to store AccessibilityNodeInfo, Pipeline and TalkBackAnalytics.
 */
public abstract class AbstractOnContextMenuItemClickListener
    implements OnContextMenuItemClickListener {
  protected AccessibilityNodeInfoCompat node;
  protected final Pipeline.FeedbackReturner pipeline;
  protected final TalkBackAnalytics analytics;

  protected AbstractOnContextMenuItemClickListener(
      AccessibilityNodeInfoCompat node,
      Pipeline.FeedbackReturner pipeline,
      TalkBackAnalytics analytics) {
    this.node = node;
    this.pipeline = pipeline;
    this.analytics = analytics;
  }
}
