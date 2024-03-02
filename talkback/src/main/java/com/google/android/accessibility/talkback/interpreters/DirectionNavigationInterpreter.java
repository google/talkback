/*
 * Copyright (C) 2017 Google Inc.
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


import android.content.Context;
import android.view.accessibility.AccessibilityEvent;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import com.google.android.accessibility.talkback.ActorState;
import com.google.android.accessibility.talkback.Interpretation;
import com.google.android.accessibility.talkback.Pipeline;
import com.google.android.accessibility.talkback.focusmanagement.action.NavigationAction;
import com.google.android.accessibility.talkback.focusmanagement.record.FocusActionInfo;
import com.google.android.accessibility.utils.AccessibilityEventListener;
import com.google.android.accessibility.utils.AccessibilityNodeInfoUtils;
import com.google.android.accessibility.utils.Performance.EventId;
import com.google.android.accessibility.utils.WindowUtils;
import com.google.android.accessibility.utils.traversal.TraversalStrategy.SearchDirection;
import com.google.android.accessibility.utils.traversal.TraversalStrategyUtils;
import com.google.android.libraries.accessibility.utils.log.LogUtils;

/** Event-interpreter for directional navigation. */
public class DirectionNavigationInterpreter implements AccessibilityEventListener {

  private static final String TAG = "DirectionNavigationInterpreter";

  private final Context context;
  private ActorState actorState;
  private Pipeline.InterpretationReceiver pipeline;

  ////////////////////////////////////////////////////////////////////////////////////////////////
  // Construction methods

  public DirectionNavigationInterpreter(Context context) {
    this.context = context;
  }

  public void setActorState(ActorState actorState) {
    this.actorState = actorState;
  }

  public void setPipeline(Pipeline.InterpretationReceiver pipeline) {
    this.pipeline = pipeline;
  }

  ////////////////////////////////////////////////////////////////////////////////////////////////

  @Override
  public int getEventTypes() {
    return AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED;
  }

  @Override
  public void onAccessibilityEvent(AccessibilityEvent event, EventId eventId) {
    if (actorState == null) {
      return;
    }
    FocusActionInfo actionInfo = actorState.getFocusHistory().getFocusActionInfoFromEvent(event);
    if (actionInfo == null) {
      LogUtils.w(
          TAG,
          "Accessibility focus is not assigned by TalkBack. Unable to find source action info for"
              + " event: %s",
          event);
      return;
    }
    AccessibilityNodeInfoCompat sourceNode = AccessibilityNodeInfoUtils.toCompat(event.getSource());

    if (actionInfo.sourceAction == FocusActionInfo.LOGICAL_NAVIGATION) {
      NavigationAction navigationAction = actionInfo.navigationAction;
      if ((navigationAction != null)
          && (navigationAction.originalNavigationGranularity != null)
          && navigationAction.originalNavigationGranularity.isMicroGranularity()) {
        // Node reference only, do not recycle.
        AccessibilityNodeInfoCompat moveToNode =
            AccessibilityNodeInfoUtils.isKeyboard(sourceNode) ? null : sourceNode;
        // Try to automatically perform micro-granularity movement.
        @SearchDirection
        int linearDirection =
            TraversalStrategyUtils.getLogicalDirection(
                navigationAction.searchDirection, WindowUtils.isScreenLayoutRTL(context));
        pipeline.input(
            eventId, Interpretation.DirectionNavigation.create(linearDirection, moveToNode));
      }
    }
  }

}
