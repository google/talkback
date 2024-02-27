package com.google.android.accessibility.talkback.compositor;

import static android.accessibilityservice.MagnificationConfig.MAGNIFICATION_MODE_FULLSCREEN;

import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import com.google.auto.value.AutoValue;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/** Data-structure that holds current magnification state for compositor used. */
@AutoValue
public abstract class MagnificationState {

  /** Magnification is disabled. */
  public static final int STATE_OFF = 0;
  /** Magnification is enabled. */
  public static final int STATE_ON = 1;
  /** Magnification scale is changed. */
  public static final int STATE_SCALE_CHANGED = 2;
  /** Magnification state for compositor speech feedback. */
  @IntDef({STATE_OFF, STATE_ON, STATE_SCALE_CHANGED})
  @Retention(RetentionPolicy.SOURCE)
  public @interface State {}

  /**
   * The current magnification mode. It is nullable if the old platform doesn't support multiple
   * magnification mode.
   */
  public abstract @Nullable Integer mode();

  /** The current magnification scale. */
  public abstract float currentScale();

  /** The state for TalkBack to provide user feedback. */
  public abstract @State int state();

  public static MagnificationState.Builder builder() {
    return new AutoValue_MagnificationState.Builder()
        .setMode(MAGNIFICATION_MODE_FULLSCREEN)
        .setCurrentScale(1.0f)
        .setState(STATE_OFF);
  }

  /** Builder for magnification state data. */
  @AutoValue.Builder
  public abstract static class Builder {

    public abstract MagnificationState.Builder setMode(Integer value);

    public abstract MagnificationState.Builder setCurrentScale(float value);

    public abstract MagnificationState.Builder setState(@State int value);

    public abstract MagnificationState build();
  }
}
