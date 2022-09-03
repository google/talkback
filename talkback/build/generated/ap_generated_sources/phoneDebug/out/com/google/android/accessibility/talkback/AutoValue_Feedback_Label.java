package com.google.android.accessibility.talkback;

import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import javax.annotation.Generated;
import org.checkerframework.checker.nullness.qual.Nullable;

@Generated("com.google.auto.value.processor.AutoValueProcessor")
final class AutoValue_Feedback_Label extends Feedback.Label {

  private final Feedback.Label.Action action;

  private final @Nullable String text;

  private final AccessibilityNodeInfoCompat node;

  private AutoValue_Feedback_Label(
      Feedback.Label.Action action,
      @Nullable String text,
      AccessibilityNodeInfoCompat node) {
    this.action = action;
    this.text = text;
    this.node = node;
  }

  @Override
  public Feedback.Label.Action action() {
    return action;
  }

  @Override
  public @Nullable String text() {
    return text;
  }

  @Override
  public AccessibilityNodeInfoCompat node() {
    return node;
  }

  @Override
  public String toString() {
    return "Label{"
        + "action=" + action + ", "
        + "text=" + text + ", "
        + "node=" + node
        + "}";
  }

  @Override
  public boolean equals(@Nullable Object o) {
    if (o == this) {
      return true;
    }
    if (o instanceof Feedback.Label) {
      Feedback.Label that = (Feedback.Label) o;
      return this.action.equals(that.action())
          && (this.text == null ? that.text() == null : this.text.equals(that.text()))
          && this.node.equals(that.node());
    }
    return false;
  }

  @Override
  public int hashCode() {
    int h$ = 1;
    h$ *= 1000003;
    h$ ^= action.hashCode();
    h$ *= 1000003;
    h$ ^= (text == null) ? 0 : text.hashCode();
    h$ *= 1000003;
    h$ ^= node.hashCode();
    return h$;
  }

  static final class Builder extends Feedback.Label.Builder {
    private Feedback.Label.Action action;
    private @Nullable String text;
    private AccessibilityNodeInfoCompat node;
    Builder() {
    }
    @Override
    public Feedback.Label.Builder setAction(Feedback.Label.Action action) {
      if (action == null) {
        throw new NullPointerException("Null action");
      }
      this.action = action;
      return this;
    }
    @Override
    public Feedback.Label.Builder setText(@Nullable String text) {
      this.text = text;
      return this;
    }
    @Override
    public Feedback.Label.Builder setNode(AccessibilityNodeInfoCompat node) {
      if (node == null) {
        throw new NullPointerException("Null node");
      }
      this.node = node;
      return this;
    }
    @Override
    AccessibilityNodeInfoCompat node() {
      if (node == null) {
        throw new IllegalStateException("Property \"node\" has not been set");
      }
      return node;
    }
    @Override
    Feedback.Label autoBuild() {
      if (this.action == null
          || this.node == null) {
        StringBuilder missing = new StringBuilder();
        if (this.action == null) {
          missing.append(" action");
        }
        if (this.node == null) {
          missing.append(" node");
        }
        throw new IllegalStateException("Missing required properties:" + missing);
      }
      return new AutoValue_Feedback_Label(
          this.action,
          this.text,
          this.node);
    }
  }

}
