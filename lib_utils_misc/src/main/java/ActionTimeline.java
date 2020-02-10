package com.google.android.libraries.accessibility.utils.undo;

import android.accessibilityservice.AccessibilityService;
import com.google.android.libraries.accessibility.utils.undo.TimelineAction.ActionResult;
import com.google.android.libraries.accessibility.utils.undo.TimelineAction.Undoable;
import com.google.android.libraries.accessibility.utils.undo.UndoRedoManager.RecycleBehavior;
import com.google.common.annotations.VisibleForTesting;
import java.util.ArrayDeque;
import java.util.Date;
import java.util.Deque;

/**
 * This class manages undo and redo operations for {@link Undoable} {@link TimelineAction} instances
 * of a particular view.
 */
public abstract class ActionTimeline implements Comparable<ActionTimeline> {

  // The maximum number of actions we keep track of.
  protected static final int SIZE_LIMIT = 128;

  /**
   * The undo and redo stack hold {@link TimelineAction} instances that are undoable (implement the
   * {@link Undoable} interface). The undo stack holds actions that were performed in the past but
   * have not been undone, while the redo stack holds actions that were also performed in the past,
   * but have been undone in this "timeline".
   *
   * <p>The notion of a timeline is used to describe changes in past decisions/actions. For example,
   * if the user undoes an action, then performs a different action after undoing, he/she has
   * diverged from the previous timeline; the action that was undone is no longer accessible after
   * the new action was performed.
   */
  public final Deque<TimelineAction> undoStack = new ArrayDeque<>();

  public final Deque<TimelineAction> redoStack = new ArrayDeque<>();
  private Date lastTimeUsed = new Date();

  /**
   * The method to execute immediately prior to performing an action. Should be called in the logic
   * that actually executes and action.
   */
  public abstract void willPerformNewAction();

  /**
   * The method to execute in between commands, upon reactivations of Voice Access. This method will
   * be invoked by the singleton {@link UndoRedoManager} instance.
   */
  public abstract void logicInBetweenActions();

  /**
   * Adds an {@link Undoable} to the undo-redo management system. Assumes that the action specified
   * was performed elsewhere right before calling this method.
   *
   * @param actionPerformed the action that was performed that will be added to the undo-redo
   *     management system
   * @return whether or not the action was successfully integrated into the undo-redo management
   *     system
   */
  public synchronized boolean newActionPerformed(TimelineAction actionPerformed) {
    updateTimeStamp();

    // If the action is not undoable, then we ignore it
    if (!(actionPerformed instanceof Undoable)) {
      /* TODO: If the action is not undoable, then it's possible that no other action
       * can be changed, because the state changed so drastically. In this case clear the stack.
       */
      return false;
    }

    /**
     * If we're undoing another action, we don't want to mess with the stack. If the logic that is
     * calling this method naively invokes it on actions that are undoing other actions, then we can
     * stop them from affecting our stack here.
     */
    if (actionPerformed.isUndoingAnotherAction()) {
      return false;
    }

    // Add the undoable action to the undo stack
    undoStack.addFirst(actionPerformed);

    if (undoStack.size() > SIZE_LIMIT) {
      undoStack.removeLast();
    }

    // Clear the redo stack, since any actions on that stack are now on a divergent timeline
    redoStack.clear();

    return true;
  }

  /**
   * Executes an undo operation by performing the next action. May modify the state of the screen.
   *
   * @return {@code true} if the execution of the undo operation was successful
   */
  public synchronized boolean performUndo(AccessibilityService service) {

    updateTimeStamp();
    if (!canPerformUndo()) {
      return false;
    }

    // Get the previous action
    final TimelineAction prevActionObj = getLastActionExecuted();

    if (!(prevActionObj instanceof Undoable)) {
      return false;
    }

    final Undoable prevAction = (Undoable) prevActionObj;

    // Get the inverse action and perform it
    final TimelineAction inverseAction = prevAction.generateInverseAction();
    if (inverseAction == null) {
      return false;
    }

    final ActionResult result = inverseAction.execute(service);
    // Change the action's stack assignment if we were successfully able to perform the action
    if (result.isSuccessful()) {
      undoActionPerformed();
      return true;
    } else {
      return false;
    }
  }

  /**
   * Executes a redo operation by performing the next action. May modify the state of the screen.
   *
   * @return {@code true} if the execution of the redo operation was successful
   */
  public synchronized boolean performRedo(AccessibilityService service) {

    updateTimeStamp();
    if (!canPerformRedo()) {
      return false;
    }

    // Get the next action
    final TimelineAction nextAction = getNextActionToRedo();

    // We don't want this action to be inadvertently clear the redo stack while executing
    nextAction.setUndoingAnotherAction(true);

    // Perform the next action
    final ActionResult result = nextAction.execute(service);

    nextAction.setUndoingAnotherAction(false);

    // Change the action's stack assignment if we were successfully able to perform the action
    if (result.isSuccessful()) {
      redoActionPerformed();
      return true;
    } else {
      return false;
    }
  }

  /**
   * Checks to see whether an undo action can be performed in this timeline.
   *
   * @return {@code true} if an undo action is possible in this timeline
   */
  public synchronized boolean canPerformUndo() {
    return !undoStack.isEmpty();
  }

  /**
   * Checks to see whether a redo action can be performed in this timeline.
   *
   * @return {@code true} if a redo action is possible in this timeline
   */
  public synchronized boolean canPerformRedo() {
    return !redoStack.isEmpty();
  }

  protected synchronized TimelineAction getLastActionExecuted() {
    if (undoStack.isEmpty()) {
      return null;
    }

    return undoStack.peekFirst();
  }

  protected synchronized TimelineAction getNextActionToRedo() {
    if (redoStack.isEmpty()) {
      return null;
    }

    return redoStack.peekFirst();
  }

  /**
   * Handles the moving of the previous action from the undo stack to the redo stack after it has
   * been undone. The method assumes that the action has already been undone; no {@link
   * TimelineAction} instances are performed in this method.
   */
  private synchronized void undoActionPerformed() {

    // Move the undo action to the redo stack
    redoStack.addFirst(undoStack.removeFirst());
  }

  /**
   * Handles the moving of the next action from the redo stack to the undo stack after it has been
   * redone. The method assumes that the action has already been redone; no {@link TimelineAction}
   * instances are performed in this method.
   */
  private synchronized void redoActionPerformed() {

    // Move the redo action to the undo stack
    undoStack.addFirst(redoStack.removeFirst());
  }

  /**
   * Updates the time stamp and informs the {@link UndoRedoManager} singleton that {@code this}
   * {@link ActionTimeline} has just been used. This method should only be invoked if {@code this}
   * has just been used in any manner.
   */
  final synchronized void updateTimeStamp() {
    lastTimeUsed = new Date();
    UndoRedoManager.getInstance(RecycleBehavior.DO_NOT_RECYCLE_NODES).updateHeapPosition(this);
  }

  /**
   * Returns the last time this ActionTimeline was used.
   *
   * @return the last time this ActionTimeline was used
   */
  Date getLastTimeUsed() {
    return lastTimeUsed;
  }

  /**
   * Allows explicit overriding of the lastTimeUsed variable. Should only be used in testing.
   *
   * @param date The time to override lastTimeUsed with
   * @return This ActionTimeline to reduce code needed to construct an ActionTimeline and set the
   *     time
   */
  @VisibleForTesting
  ActionTimeline setLastTimeUsed(Date date) {
    lastTimeUsed = date;
    return this;
  }

  @Override
  public int compareTo(ActionTimeline other) {
    return getLastTimeUsed().compareTo(other.getLastTimeUsed());
  }
}
