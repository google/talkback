/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.google.android.accessibility.talkback.actor;

import static androidx.core.view.accessibility.AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_CLICK;
import static androidx.core.view.accessibility.AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_FOCUS;
import static androidx.core.view.accessibility.AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_LONG_CLICK;

import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import com.google.android.accessibility.talkback.ActorState;
import com.google.android.accessibility.talkback.Feedback;
import com.google.android.accessibility.talkback.Pipeline;
import com.google.android.accessibility.talkback.focusmanagement.record.FocusActionInfo;
import com.google.android.accessibility.utils.Performance.EventId;
import com.google.android.accessibility.utils.Role;
import com.google.android.accessibility.utils.WebInterfaceUtils;

/** The feedback-actor to handle accessibility focus during touch interaction. */
public class FocusActorForTapAndTouchExploration {

  @VisibleForTesting
  protected static final FocusActionInfo REFOCUS_ACTION_INFO =
      new FocusActionInfo.Builder()
          .setSourceAction(FocusActionInfo.TOUCH_EXPLORATION)
          .setIsFromRefocusAction(true)
          .build();

  @VisibleForTesting
  protected static final FocusActionInfo NON_REFOCUS_ACTION_INFO =
      new FocusActionInfo.Builder()
          .setSourceAction(FocusActionInfo.TOUCH_EXPLORATION)
          .setIsFromRefocusAction(false)
          .build();

  //////////////////////////////////////////////////////////////////////////////////////////////////
  // Member variables

  private Pipeline.FeedbackReturner pipeline;
  private ActorState actorState;

  //////////////////////////////////////////////////////////////////////////////////////////////////
  // Contstructor methods

  public FocusActorForTapAndTouchExploration() {}

  public void setPipeline(Pipeline.FeedbackReturner pipeline) {
    this.pipeline = pipeline;
  }

  public void setActorState(ActorState actorState) {
    this.actorState = actorState;
  }

  //////////////////////////////////////////////////////////////////////////////////////////////////
  // Methods for executing focus actions

  public boolean attemptLongPress(@Nullable AccessibilityNodeInfoCompat node, EventId eventId) {
    if (node == null) {
      return false;
    }
    return pipeline.returnFeedback(eventId, Feedback.nodeAction(node, ACTION_LONG_CLICK.getId()));
  }

  public boolean setAccessibilityFocus(
      @Nullable AccessibilityNodeInfoCompat node,
      boolean forceRefocusIfAlreadyFocused,
      EventId eventId) {

    if (node == null) {
      return false;
    }

    // REFERTO. Force focus the node if it is not the last focused node. The
    // accessibility focus may not update immediately after {@link
    // AccessibilityNodeInfoCompat#performAction(int)}, this may easily happened when receiving
    // multiple hover events in a short time. so we check last focused node is equal to current
    // touched node by calling {@link
    // FocusManagerInternal#lastAccessibilityFocusedNodeEquals(AccessibilityNodeInfoCompat)}.
    if (!actorState.getFocusHistory().lastAccessibilityFocusedNodeEquals(node)) {
      forceRefocusIfAlreadyFocused = true;
    }

    final FocusActionInfo info =
        (forceRefocusIfAlreadyFocused) ? REFOCUS_ACTION_INFO : NON_REFOCUS_ACTION_INFO;
    return pipeline.returnFeedback(
        eventId, Feedback.focus(node, info).setForceRefocus(forceRefocusIfAlreadyFocused));
  }

  public boolean performClick(@Nullable AccessibilityNodeInfoCompat node, EventId eventId) {

    if (node == null) {
      return false;
    }

    // Performing a click on an EditText does not show the IME, so we need
    // to place input focus on it. If the IME was already connected and is
    // hidden, there is nothing we can do.
    if (Role.getRole(node) == Role.ROLE_EDIT_TEXT) {
      return pipeline.returnFeedback(eventId, Feedback.nodeAction(node, ACTION_FOCUS.getId()));
    }

    // If a user quickly touch explores in web content (event stream <
    // TAP_TIMEOUT_MS), we'll send an unintentional ACTION_CLICK. Switch
    // off clicking on web content for now.
    // TODO: Verify if it's a legacy feature.
    if (WebInterfaceUtils.supportsWebActions(node)) {
      return false;
    }

    return pipeline.returnFeedback(eventId, Feedback.nodeAction(node, ACTION_CLICK.getId()));
  }

}
