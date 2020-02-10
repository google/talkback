package com.google.android.accessibility.switchaccess;

import android.accessibilityservice.AccessibilityService;
import com.google.android.libraries.accessibility.utils.undo.ActionTimeline;
import com.google.android.libraries.accessibility.utils.undo.ActionTimelineForNodeCompat;
import com.google.common.annotations.VisibleForTesting;

/** An {@link ActionTimeline} to use with {@link SwitchAccessAction}s. */
// TODO: Use this in ShowActionsMenuNode and others to track undo/redo actions.
public class SwitchAccessActionTimeline extends ActionTimelineForNodeCompat {

  public SwitchAccessActionTimeline(SwitchAccessNodeCompat node) {
    super(node);
  }

  @Override
  public void willPerformNewAction() {
    /* Do nothing */
  }

  @Override
  public void logicInBetweenActions() {
    /* Do nothing */
  }

  @VisibleForTesting
  SwitchAccessNodeCompat getNode() {
    return (SwitchAccessNodeCompat) node;
  }

  public void setNode(SwitchAccessNodeCompat node) {
    this.node = node;
  }

  @Override
  public synchronized boolean performUndo(AccessibilityService service) {
    // Update the nodeCompat of the last action so that it doesn't execute on a recycled node.
    // Since the node tree is constantly rebuilding, the SwitchAccessNodeCompat used to create the
    // previous action may not exist anymore.
    SwitchAccessAction action = (SwitchAccessAction) getLastActionExecuted();
    action.setNodeCompat(getNode());
    return super.performUndo(service);
  }

  @Override
  public synchronized boolean performRedo(AccessibilityService service) {
    // Update the nodeCompat of the next action so that it doesn't execute on a recycled node.
    // Since the node tree is constantly rebuilding, the SwitchAccessNodeCompat used to create the
    // next action may not exist anymore.
    SwitchAccessAction action = (SwitchAccessAction) getNextActionToRedo();
    action.setNodeCompat(getNode());
    return super.performRedo(service);
  }
}
