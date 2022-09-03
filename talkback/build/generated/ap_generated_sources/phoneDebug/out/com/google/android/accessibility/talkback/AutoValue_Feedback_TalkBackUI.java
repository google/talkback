package com.google.android.accessibility.talkback;

import com.google.android.accessibility.talkback.actor.TalkBackUIActor;
import javax.annotation.Generated;
import org.checkerframework.checker.nullness.qual.Nullable;

@Generated("com.google.auto.value.processor.AutoValueProcessor")
final class AutoValue_Feedback_TalkBackUI extends Feedback.TalkBackUI {

  private final Feedback.TalkBackUI.Action action;

  private final TalkBackUIActor.Type type;

  private final @Nullable CharSequence message;

  private final boolean showIcon;

  AutoValue_Feedback_TalkBackUI(
      Feedback.TalkBackUI.Action action,
      TalkBackUIActor.Type type,
      @Nullable CharSequence message,
      boolean showIcon) {
    if (action == null) {
      throw new NullPointerException("Null action");
    }
    this.action = action;
    if (type == null) {
      throw new NullPointerException("Null type");
    }
    this.type = type;
    this.message = message;
    this.showIcon = showIcon;
  }

  @Override
  public Feedback.TalkBackUI.Action action() {
    return action;
  }

  @Override
  public TalkBackUIActor.Type type() {
    return type;
  }

  @Override
  public @Nullable CharSequence message() {
    return message;
  }

  @Override
  public boolean showIcon() {
    return showIcon;
  }

  @Override
  public String toString() {
    return "TalkBackUI{"
        + "action=" + action + ", "
        + "type=" + type + ", "
        + "message=" + message + ", "
        + "showIcon=" + showIcon
        + "}";
  }

  @Override
  public boolean equals(@Nullable Object o) {
    if (o == this) {
      return true;
    }
    if (o instanceof Feedback.TalkBackUI) {
      Feedback.TalkBackUI that = (Feedback.TalkBackUI) o;
      return this.action.equals(that.action())
          && this.type.equals(that.type())
          && (this.message == null ? that.message() == null : this.message.equals(that.message()))
          && this.showIcon == that.showIcon();
    }
    return false;
  }

  @Override
  public int hashCode() {
    int h$ = 1;
    h$ *= 1000003;
    h$ ^= action.hashCode();
    h$ *= 1000003;
    h$ ^= type.hashCode();
    h$ *= 1000003;
    h$ ^= (message == null) ? 0 : message.hashCode();
    h$ *= 1000003;
    h$ ^= showIcon ? 1231 : 1237;
    return h$;
  }

}
