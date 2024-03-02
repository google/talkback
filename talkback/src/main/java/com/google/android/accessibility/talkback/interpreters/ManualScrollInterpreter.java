/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.google.android.accessibility.talkback.interpreters;

import android.view.accessibility.AccessibilityEvent;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import com.google.android.accessibility.talkback.ActorState;
import com.google.android.accessibility.talkback.focusmanagement.record.NodePathDescription;
import com.google.android.accessibility.utils.AccessibilityNodeInfoUtils;
import com.google.android.accessibility.utils.BuildVersionUtils;
import com.google.android.accessibility.utils.Performance.EventId;
import com.google.android.accessibility.utils.input.ScrollEventInterpreter.ScrollEventHandler;
import com.google.android.accessibility.utils.input.ScrollEventInterpreter.ScrollEventInterpretation;
import com.google.android.accessibility.utils.output.ScrollActionRecord;
import com.google.android.accessibility.utils.traversal.TraversalStrategy;
import com.google.auto.value.AutoValue;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Manages manual scroll feedback. If an interpreted {@link AccessibilityEvent#TYPE_VIEW_SCROLLED}
 * event passes through this processor and has a valid scroll-direction, it starts to search the
 * accessibility focus.
 */
public class ManualScrollInterpreter implements ScrollEventHandler {

  /** Listens to scrolled view changes. */
  public interface ScrolledViewChangeListener {

    /** Callback when the view is manually scrolled. */
    void onManualScroll(ManualScrollInterpretation interpretation);
  }

  /** Data structure for interpretation of manual scroll. */
  @AutoValue
  public abstract static class ManualScrollInterpretation {

    public abstract @Nullable EventId eventId();

    public abstract AccessibilityEvent event();

    @TraversalStrategy.SearchDirection
    public abstract int direction();

    public static ManualScrollInterpretation create(
        @Nullable EventId eventId, AccessibilityEvent event, int direction) {
      return new AutoValue_ManualScrollInterpreter_ManualScrollInterpretation(
          eventId, event, direction);
    }
  }

  ////////////////////////////////////////////////////////////////////////////////////////////////
  // Member variables

  private ActorState actorState;
  private ScrolledViewChangeListener listener;

  ////////////////////////////////////////////////////////////////////////////////////////////////
  // Construction

  public void setActorState(ActorState actorState) {
    this.actorState = actorState;
  }

  public void setListener(ScrolledViewChangeListener listener) {
    this.listener = listener;
  }

  ////////////////////////////////////////////////////////////////////////////////////////////////
  // Methods

  @Override
  public void onScrollEvent(
      AccessibilityEvent event, ScrollEventInterpretation interpretation, EventId eventId) {
    if ((interpretation.userAction != ScrollActionRecord.ACTION_MANUAL_SCROLL)
        || (interpretation.scrollDirection == TraversalStrategy.SEARCH_FOCUS_UNKNOWN)) {
      return;
    }

    AccessibilityNodeInfoCompat scrolledNode =
        AccessibilityNodeInfoUtils.toCompat(event.getSource());
    if (scrolledNode == null) {
      return;
    }

    NodePathDescription lastFocusNodePathDescription =
        actorState.getFocusHistory().getLastFocusNodePathDescription();
    if (lastFocusNodePathDescription == null) {
      return;
    }

    // Match ancestor node. Before android-OMR1, need refresh to get viewIdResourceName.
    if (!BuildVersionUtils.isAtLeastOMR1()) {
      scrolledNode.refresh();
    }
    if (!lastFocusNodePathDescription.containsNodeByHashAndIdentity(scrolledNode)) {
      return;
    }

    listener.onManualScroll(
        ManualScrollInterpretation.create(eventId, event, interpretation.scrollDirection));
  }
}
