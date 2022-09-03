package com.google.android.accessibility.talkback;

import android.os.Bundle;
import com.google.android.accessibility.utils.AccessibilityNode;
import javax.annotation.Generated;
import org.checkerframework.checker.nullness.qual.Nullable;

@Generated("com.google.auto.value.processor.AutoValueProcessor")
final class AutoValue_Feedback_NodeAction extends Feedback.NodeAction {

  private final AccessibilityNode target;

  private final int actionId;

  private final @Nullable Bundle args;

  private AutoValue_Feedback_NodeAction(
      AccessibilityNode target,
      int actionId,
      @Nullable Bundle args) {
    this.target = target;
    this.actionId = actionId;
    this.args = args;
  }

  @Override
  public AccessibilityNode target() {
    return target;
  }

  @Override
  public int actionId() {
    return actionId;
  }

  @Override
  public @Nullable Bundle args() {
    return args;
  }

  @Override
  public boolean equals(@Nullable Object o) {
    if (o == this) {
      return true;
    }
    if (o instanceof Feedback.NodeAction) {
      Feedback.NodeAction that = (Feedback.NodeAction) o;
      return this.target.equals(that.target())
          && this.actionId == that.actionId()
          && (this.args == null ? that.args() == null : this.args.equals(that.args()));
    }
    return false;
  }

  @Override
  public int hashCode() {
    int h$ = 1;
    h$ *= 1000003;
    h$ ^= target.hashCode();
    h$ *= 1000003;
    h$ ^= actionId;
    h$ *= 1000003;
    h$ ^= (args == null) ? 0 : args.hashCode();
    return h$;
  }

  static final class Builder extends Feedback.NodeAction.Builder {
    private AccessibilityNode target;
    private Integer actionId;
    private @Nullable Bundle args;
    Builder() {
    }
    @Override
    public Feedback.NodeAction.Builder setTarget(AccessibilityNode target) {
      if (target == null) {
        throw new NullPointerException("Null target");
      }
      this.target = target;
      return this;
    }
    @Override
    AccessibilityNode target() {
      if (target == null) {
        throw new IllegalStateException("Property \"target\" has not been set");
      }
      return target;
    }
    @Override
    public Feedback.NodeAction.Builder setActionId(int actionId) {
      this.actionId = actionId;
      return this;
    }
    @Override
    public Feedback.NodeAction.Builder setArgs(@Nullable Bundle args) {
      this.args = args;
      return this;
    }
    @Override
    Feedback.NodeAction autoBuild() {
      if (this.target == null
          || this.actionId == null) {
        StringBuilder missing = new StringBuilder();
        if (this.target == null) {
          missing.append(" target");
        }
        if (this.actionId == null) {
          missing.append(" actionId");
        }
        throw new IllegalStateException("Missing required properties:" + missing);
      }
      return new AutoValue_Feedback_NodeAction(
          this.target,
          this.actionId,
          this.args);
    }
  }

}
