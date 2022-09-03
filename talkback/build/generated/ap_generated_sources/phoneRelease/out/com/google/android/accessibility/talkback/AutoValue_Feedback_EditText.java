package com.google.android.accessibility.talkback;

import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import javax.annotation.Generated;
import org.checkerframework.checker.nullness.qual.Nullable;

@Generated("com.google.auto.value.processor.AutoValueProcessor")
final class AutoValue_Feedback_EditText extends Feedback.EditText {

  private final AccessibilityNodeInfoCompat node;

  private final Feedback.EditText.Action action;

  private final boolean stopSelecting;

  private final @Nullable CharSequence text;

  private AutoValue_Feedback_EditText(
      AccessibilityNodeInfoCompat node,
      Feedback.EditText.Action action,
      boolean stopSelecting,
      @Nullable CharSequence text) {
    this.node = node;
    this.action = action;
    this.stopSelecting = stopSelecting;
    this.text = text;
  }

  @Override
  public AccessibilityNodeInfoCompat node() {
    return node;
  }

  @Override
  public Feedback.EditText.Action action() {
    return action;
  }

  @Override
  public boolean stopSelecting() {
    return stopSelecting;
  }

  @Override
  public @Nullable CharSequence text() {
    return text;
  }

  @Override
  public String toString() {
    return "EditText{"
        + "node=" + node + ", "
        + "action=" + action + ", "
        + "stopSelecting=" + stopSelecting + ", "
        + "text=" + text
        + "}";
  }

  @Override
  public boolean equals(@Nullable Object o) {
    if (o == this) {
      return true;
    }
    if (o instanceof Feedback.EditText) {
      Feedback.EditText that = (Feedback.EditText) o;
      return this.node.equals(that.node())
          && this.action.equals(that.action())
          && this.stopSelecting == that.stopSelecting()
          && (this.text == null ? that.text() == null : this.text.equals(that.text()));
    }
    return false;
  }

  @Override
  public int hashCode() {
    int h$ = 1;
    h$ *= 1000003;
    h$ ^= node.hashCode();
    h$ *= 1000003;
    h$ ^= action.hashCode();
    h$ *= 1000003;
    h$ ^= stopSelecting ? 1231 : 1237;
    h$ *= 1000003;
    h$ ^= (text == null) ? 0 : text.hashCode();
    return h$;
  }

  static final class Builder extends Feedback.EditText.Builder {
    private AccessibilityNodeInfoCompat node;
    private Feedback.EditText.Action action;
    private Boolean stopSelecting;
    private @Nullable CharSequence text;
    Builder() {
    }
    @Override
    public Feedback.EditText.Builder setNode(AccessibilityNodeInfoCompat node) {
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
    public Feedback.EditText.Builder setAction(Feedback.EditText.Action action) {
      if (action == null) {
        throw new NullPointerException("Null action");
      }
      this.action = action;
      return this;
    }
    @Override
    public Feedback.EditText.Builder setStopSelecting(boolean stopSelecting) {
      this.stopSelecting = stopSelecting;
      return this;
    }
    @Override
    public Feedback.EditText.Builder setText(@Nullable CharSequence text) {
      this.text = text;
      return this;
    }
    @Override
    Feedback.EditText autoBuild() {
      if (this.node == null
          || this.action == null
          || this.stopSelecting == null) {
        StringBuilder missing = new StringBuilder();
        if (this.node == null) {
          missing.append(" node");
        }
        if (this.action == null) {
          missing.append(" action");
        }
        if (this.stopSelecting == null) {
          missing.append(" stopSelecting");
        }
        throw new IllegalStateException("Missing required properties:" + missing);
      }
      return new AutoValue_Feedback_EditText(
          this.node,
          this.action,
          this.stopSelecting,
          this.text);
    }
  }

}
