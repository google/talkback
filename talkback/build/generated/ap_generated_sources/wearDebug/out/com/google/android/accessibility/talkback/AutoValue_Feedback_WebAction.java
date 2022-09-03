package com.google.android.accessibility.talkback;

import android.os.Bundle;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import com.google.android.accessibility.talkback.focusmanagement.action.NavigationAction;
import javax.annotation.Generated;
import org.checkerframework.checker.nullness.qual.Nullable;

@Generated("com.google.auto.value.processor.AutoValueProcessor")
final class AutoValue_Feedback_WebAction extends Feedback.WebAction {

  private final Feedback.WebAction.Action action;

  private final AccessibilityNodeInfoCompat target;

  private final int nodeAction;

  private final @Nullable Bundle nodeActionArgs;

  private final boolean updateFocusHistory;

  private final @Nullable NavigationAction navigationAction;

  private AutoValue_Feedback_WebAction(
      Feedback.WebAction.Action action,
      AccessibilityNodeInfoCompat target,
      int nodeAction,
      @Nullable Bundle nodeActionArgs,
      boolean updateFocusHistory,
      @Nullable NavigationAction navigationAction) {
    this.action = action;
    this.target = target;
    this.nodeAction = nodeAction;
    this.nodeActionArgs = nodeActionArgs;
    this.updateFocusHistory = updateFocusHistory;
    this.navigationAction = navigationAction;
  }

  @Override
  public Feedback.WebAction.Action action() {
    return action;
  }

  @Override
  public AccessibilityNodeInfoCompat target() {
    return target;
  }

  @Override
  public int nodeAction() {
    return nodeAction;
  }

  @Override
  public @Nullable Bundle nodeActionArgs() {
    return nodeActionArgs;
  }

  @Override
  public boolean updateFocusHistory() {
    return updateFocusHistory;
  }

  @Override
  public @Nullable NavigationAction navigationAction() {
    return navigationAction;
  }

  @Override
  public boolean equals(@Nullable Object o) {
    if (o == this) {
      return true;
    }
    if (o instanceof Feedback.WebAction) {
      Feedback.WebAction that = (Feedback.WebAction) o;
      return this.action.equals(that.action())
          && this.target.equals(that.target())
          && this.nodeAction == that.nodeAction()
          && (this.nodeActionArgs == null ? that.nodeActionArgs() == null : this.nodeActionArgs.equals(that.nodeActionArgs()))
          && this.updateFocusHistory == that.updateFocusHistory()
          && (this.navigationAction == null ? that.navigationAction() == null : this.navigationAction.equals(that.navigationAction()));
    }
    return false;
  }

  @Override
  public int hashCode() {
    int h$ = 1;
    h$ *= 1000003;
    h$ ^= action.hashCode();
    h$ *= 1000003;
    h$ ^= target.hashCode();
    h$ *= 1000003;
    h$ ^= nodeAction;
    h$ *= 1000003;
    h$ ^= (nodeActionArgs == null) ? 0 : nodeActionArgs.hashCode();
    h$ *= 1000003;
    h$ ^= updateFocusHistory ? 1231 : 1237;
    h$ *= 1000003;
    h$ ^= (navigationAction == null) ? 0 : navigationAction.hashCode();
    return h$;
  }

  static final class Builder extends Feedback.WebAction.Builder {
    private Feedback.WebAction.Action action;
    private AccessibilityNodeInfoCompat target;
    private Integer nodeAction;
    private @Nullable Bundle nodeActionArgs;
    private Boolean updateFocusHistory;
    private @Nullable NavigationAction navigationAction;
    Builder() {
    }
    @Override
    public Feedback.WebAction.Builder setAction(Feedback.WebAction.Action action) {
      if (action == null) {
        throw new NullPointerException("Null action");
      }
      this.action = action;
      return this;
    }
    @Override
    public Feedback.WebAction.Builder setTarget(AccessibilityNodeInfoCompat target) {
      if (target == null) {
        throw new NullPointerException("Null target");
      }
      this.target = target;
      return this;
    }
    @Override
    AccessibilityNodeInfoCompat target() {
      if (target == null) {
        throw new IllegalStateException("Property \"target\" has not been set");
      }
      return target;
    }
    @Override
    public Feedback.WebAction.Builder setNodeAction(int nodeAction) {
      this.nodeAction = nodeAction;
      return this;
    }
    @Override
    public Feedback.WebAction.Builder setNodeActionArgs(@Nullable Bundle nodeActionArgs) {
      this.nodeActionArgs = nodeActionArgs;
      return this;
    }
    @Override
    public Feedback.WebAction.Builder setUpdateFocusHistory(boolean updateFocusHistory) {
      this.updateFocusHistory = updateFocusHistory;
      return this;
    }
    @Override
    public Feedback.WebAction.Builder setNavigationAction(NavigationAction navigationAction) {
      this.navigationAction = navigationAction;
      return this;
    }
    @Override
    Feedback.WebAction autoBuild() {
      if (this.action == null
          || this.target == null
          || this.nodeAction == null
          || this.updateFocusHistory == null) {
        StringBuilder missing = new StringBuilder();
        if (this.action == null) {
          missing.append(" action");
        }
        if (this.target == null) {
          missing.append(" target");
        }
        if (this.nodeAction == null) {
          missing.append(" nodeAction");
        }
        if (this.updateFocusHistory == null) {
          missing.append(" updateFocusHistory");
        }
        throw new IllegalStateException("Missing required properties:" + missing);
      }
      return new AutoValue_Feedback_WebAction(
          this.action,
          this.target,
          this.nodeAction,
          this.nodeActionArgs,
          this.updateFocusHistory,
          this.navigationAction);
    }
  }

}
