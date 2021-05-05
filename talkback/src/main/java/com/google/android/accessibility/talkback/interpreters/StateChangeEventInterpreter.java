package com.google.android.accessibility.talkback.interpreters;

import static com.google.android.accessibility.talkback.Interpretation.ID.Value.STATE_CHANGE;

import androidx.core.view.accessibility.AccessibilityEventCompat;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import com.google.android.accessibility.talkback.ActorState;
import com.google.android.accessibility.talkback.Interpretation.ID;
import com.google.android.accessibility.talkback.Pipeline.InterpretationReceiver;
import com.google.android.accessibility.talkback.actor.NodeActionPerformer.NodeActionRecord;
import com.google.android.accessibility.utils.AccessibilityEventListener;
import com.google.android.accessibility.utils.AccessibilityNodeInfoUtils;
import com.google.android.accessibility.utils.Performance.EventId;

/**
 * It handles AccessibilityEvent.CONTENT_CHANGE_TYPE_STATE_DESCRIPTION event when the source node is
 * not within accessibility focus (when the node is within accessibility focus, it is handled by
 * "TYPE_WINDOW_CONTENT_CHANGED" in compositor.json) and the state change is caused by user action.
 */
public class StateChangeEventInterpreter implements AccessibilityEventListener {
  /**
   * Timeout to determine whether a state change event could be resulted from the last user action.
   */
  public static final int ACTION_TIMEOUT_MS = 500;

  private ActorState actorState;
  private InterpretationReceiver pipeline;

  public void setActorState(ActorState actorState) {
    this.actorState = actorState;
  }

  public void setPipeline(InterpretationReceiver pipeline) {
    this.pipeline = pipeline;
  }

  @Override
  public int getEventTypes() {
    return AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED;
  }

  @Override
  public void onAccessibilityEvent(AccessibilityEvent event, EventId eventId) {
    if (event.getEventType() != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
      return;
    }
    if ((event.getContentChangeTypes()
            & AccessibilityEventCompat.CONTENT_CHANGE_TYPE_STATE_DESCRIPTION)
        == 0) {
      return;
    }
    AccessibilityNodeInfo node = event.getSource();
    // If the node is gone, we don't announce anything.
    if (node == null) {
      return;
    }
    try {
      NodeActionRecord actionRecord =
          actorState.getNodeActionPerformerState().getNodeActionRecord();
      if (actionRecord == null) {
        return;
      }
      long timeDiff = event.getEventTime() - actionRecord.actionTime;
      if ((timeDiff < 0L) || (timeDiff > ACTION_TIMEOUT_MS)) {
        return;
      }
      // When the node is within accessibility focus, it is handled by
      // "TYPE_WINDOW_CONTENT_CHANGED" in compositor.json.
      if (!AccessibilityNodeInfoUtils.isSelfOrAncestorFocused(
              AccessibilityNodeInfoUtils.toCompat(node))
          && actionRecord.actionedNodeMatches(node)) {
        pipeline.input(eventId, event, new ID(STATE_CHANGE));
      }
    } finally {
      AccessibilityNodeInfoUtils.recycleNodes(node);
    }
  }
}
