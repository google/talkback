package com.google.android.accessibility.talkback.actor;

import android.os.SystemClock;
import android.view.accessibility.AccessibilityNodeInfo;
import com.google.android.accessibility.talkback.Feedback.NodeAction;
import com.google.android.accessibility.utils.AccessibilityNode;
import com.google.android.accessibility.utils.Performance.EventId;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

/** Node action performer. */
public class NodeActionPerformer {

  private static final String TAG = "NodeActionPerformer";

  private @Nullable NodeActionRecord actionRecord = null;

  /** Creates node-action-record. */
  public static class NodeActionRecord {
    public final int actionId;
    private final AccessibilityNode targetNode;
    // SystemClock.uptimeMillis(), used to compare with AccessibilityEvent.getEventTime().
    public final long actionTime;

    public NodeActionRecord(int actionId, @NonNull AccessibilityNode targetNode, long actionTime) {
      this.actionId = actionId;
      this.targetNode = targetNode;
      this.actionTime = actionTime;
    }

    public boolean actionedNodeMatches(AccessibilityNodeInfo node) {
      return targetNode.equalTo(node);
    }
  }

  ///////////////////////////////////////////////////////////////////////////////////////
  // Read-only interface

  /** Limited read-only interface to pull state data. */
  public class StateReader {
    public NodeActionRecord getNodeActionRecord() {
      return NodeActionPerformer.this.actionRecord;
    }
  }

  /** Read-only interface for pulling state data. */
  public final StateReader stateReader = new StateReader();

  ///////////////////////////////////////////////////////////////////////////////////////
  // Methods

  public boolean performAction(@NonNull NodeAction nodeAction, @Nullable EventId eventId) {
    AccessibilityNode nodeActionTarget = nodeAction.target();
    boolean success = true;
    if (nodeActionTarget != null) {
      long time = SystemClock.uptimeMillis();
      success = nodeActionTarget.performAction(nodeAction.actionId(), nodeAction.args(), eventId);
      if (success) {
        setNodeActionRecord(new NodeActionRecord(nodeAction.actionId(), nodeActionTarget, time));
      }
    }
    return success;
  }

  private void setNodeActionRecord(NodeActionRecord record) {
    actionRecord = record;
  }
}
