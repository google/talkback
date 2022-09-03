package com.google.android.accessibility.utils.input;

import javax.annotation.Generated;
import org.checkerframework.checker.nullness.qual.Nullable;

@Generated("com.google.auto.value.processor.AutoValueProcessor")
final class AutoValue_WindowEventInterpreter_Announcement extends WindowEventInterpreter.Announcement {

  private final CharSequence text;

  private final @Nullable CharSequence packageName;

  private final boolean isFromVolumeControlPanel;

  private final boolean isFromInputMethodEditor;

  AutoValue_WindowEventInterpreter_Announcement(
      CharSequence text,
      @Nullable CharSequence packageName,
      boolean isFromVolumeControlPanel,
      boolean isFromInputMethodEditor) {
    if (text == null) {
      throw new NullPointerException("Null text");
    }
    this.text = text;
    this.packageName = packageName;
    this.isFromVolumeControlPanel = isFromVolumeControlPanel;
    this.isFromInputMethodEditor = isFromInputMethodEditor;
  }

  @Override
  public CharSequence text() {
    return text;
  }

  @Override
  public @Nullable CharSequence packageName() {
    return packageName;
  }

  @Override
  public boolean isFromVolumeControlPanel() {
    return isFromVolumeControlPanel;
  }

  @Override
  public boolean isFromInputMethodEditor() {
    return isFromInputMethodEditor;
  }

  @Override
  public String toString() {
    return "Announcement{"
        + "text=" + text + ", "
        + "packageName=" + packageName + ", "
        + "isFromVolumeControlPanel=" + isFromVolumeControlPanel + ", "
        + "isFromInputMethodEditor=" + isFromInputMethodEditor
        + "}";
  }

  @Override
  public boolean equals(@Nullable Object o) {
    if (o == this) {
      return true;
    }
    if (o instanceof WindowEventInterpreter.Announcement) {
      WindowEventInterpreter.Announcement that = (WindowEventInterpreter.Announcement) o;
      return this.text.equals(that.text())
          && (this.packageName == null ? that.packageName() == null : this.packageName.equals(that.packageName()))
          && this.isFromVolumeControlPanel == that.isFromVolumeControlPanel()
          && this.isFromInputMethodEditor == that.isFromInputMethodEditor();
    }
    return false;
  }

  @Override
  public int hashCode() {
    int h$ = 1;
    h$ *= 1000003;
    h$ ^= text.hashCode();
    h$ *= 1000003;
    h$ ^= (packageName == null) ? 0 : packageName.hashCode();
    h$ *= 1000003;
    h$ ^= isFromVolumeControlPanel ? 1231 : 1237;
    h$ *= 1000003;
    h$ ^= isFromInputMethodEditor ? 1231 : 1237;
    return h$;
  }

}
