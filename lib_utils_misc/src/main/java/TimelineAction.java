package com.google.android.libraries.accessibility.utils.undo;

import android.accessibilityservice.AccessibilityService;
import org.checkerframework.checker.nullness.qual.Nullable;

/** A general action that can be used with {@link ActionTimeline}. */
public abstract class TimelineAction {

  protected boolean undoingAnotherAction = false;

  /**
   * Public method responsible for enforcing gating (API, Keyguard, or other) and then executing the
   * action.
   */
  public abstract ActionResult execute(AccessibilityService service);

  public boolean isUndoingAnotherAction() {
    return undoingAnotherAction;
  }

  public void setUndoingAnotherAction(boolean undoing) {
    undoingAnotherAction = undoing;
  }

  /**
   * The result object from calling execute() on {@link TimelineAction} or any class that derives
   * from it. Stores the success of the action.
   */
  public static class ActionResult {
    private final boolean isSuccessful;

    public ActionResult(boolean isSuccessful) {
      this.isSuccessful = isSuccessful;
    }

    public boolean isSuccessful() {
      return isSuccessful;
    }
  }

  /**
   * This interface is to be implemented by any TimelineAction that can be undone. It defines a
   * common protocol for acquiring information regarding the inverse actions of an undoable action.
   *
   * <p>Note: If a {@link TimelineAction} is undoable, it will also be redoable. This is true
   * because all {@link TimelineAction} instances are doable and all actions that are both doable
   * and undoable are, logically, redoable.
   */
  public interface Undoable {

    /**
     * Generates an inverse action which can undo the results of this action.
     *
     * <p>The following sequence of code should result in no net change in the screen: {@code
     * someUndoable.performAction(someService);} {@code
     * someUndoable.generateInverseAction().performAction(someService);}
     *
     * @return a {@link TimelineAction} that, when executed, results in the cancelation of this
     *     action, or {@code null} if this action has no inverse.
     */
    @Nullable
    TimelineAction generateInverseAction();
  }
}
