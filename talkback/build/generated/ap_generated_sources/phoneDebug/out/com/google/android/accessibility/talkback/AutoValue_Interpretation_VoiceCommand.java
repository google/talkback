package com.google.android.accessibility.talkback;

import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import com.google.android.accessibility.utils.input.CursorGranularity;
import javax.annotation.Generated;
import org.checkerframework.checker.nullness.qual.Nullable;

@Generated("com.google.auto.value.processor.AutoValueProcessor")
final class AutoValue_Interpretation_VoiceCommand extends Interpretation.VoiceCommand {

  private final Interpretation.VoiceCommand.Action command;

  private final @Nullable AccessibilityNodeInfoCompat targetNode;

  private final @Nullable CursorGranularity granularity;

  private final @Nullable CharSequence text;

  AutoValue_Interpretation_VoiceCommand(
      Interpretation.VoiceCommand.Action command,
      @Nullable AccessibilityNodeInfoCompat targetNode,
      @Nullable CursorGranularity granularity,
      @Nullable CharSequence text) {
    if (command == null) {
      throw new NullPointerException("Null command");
    }
    this.command = command;
    this.targetNode = targetNode;
    this.granularity = granularity;
    this.text = text;
  }

  @Override
  public Interpretation.VoiceCommand.Action command() {
    return command;
  }

  @Override
  public @Nullable AccessibilityNodeInfoCompat targetNode() {
    return targetNode;
  }

  @Override
  public @Nullable CursorGranularity granularity() {
    return granularity;
  }

  @Override
  public @Nullable CharSequence text() {
    return text;
  }

  @Override
  public boolean equals(@Nullable Object o) {
    if (o == this) {
      return true;
    }
    if (o instanceof Interpretation.VoiceCommand) {
      Interpretation.VoiceCommand that = (Interpretation.VoiceCommand) o;
      return this.command.equals(that.command())
          && (this.targetNode == null ? that.targetNode() == null : this.targetNode.equals(that.targetNode()))
          && (this.granularity == null ? that.granularity() == null : this.granularity.equals(that.granularity()))
          && (this.text == null ? that.text() == null : this.text.equals(that.text()));
    }
    return false;
  }

  @Override
  public int hashCode() {
    int h$ = 1;
    h$ *= 1000003;
    h$ ^= command.hashCode();
    h$ *= 1000003;
    h$ ^= (targetNode == null) ? 0 : targetNode.hashCode();
    h$ *= 1000003;
    h$ ^= (granularity == null) ? 0 : granularity.hashCode();
    h$ *= 1000003;
    h$ ^= (text == null) ? 0 : text.hashCode();
    return h$;
  }

}
