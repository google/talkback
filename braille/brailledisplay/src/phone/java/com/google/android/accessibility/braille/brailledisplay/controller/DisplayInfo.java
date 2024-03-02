package com.google.android.accessibility.braille.brailledisplay.controller;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import java.nio.ByteBuffer;
import java.util.List;

/** The information displayer and overlay needs. */
@AutoValue
public abstract class DisplayInfo {

  /** Indicates where the display info is from. */
  enum Source {
    DEFAULT,
    IME
  }

  // Displayed content, already trimmed based on the display position.
  // Updated in updateDisplayedContent() and used in refresh().
  public abstract ByteBuffer displayedBraille();

  public abstract ByteBuffer displayedOverlaidBraille();

  public abstract CharSequence displayedText();

  public abstract ImmutableList<Integer> displayedBrailleToTextPositions();

  public abstract boolean blink();

  public abstract Source source();

  public static Builder builder() {
    return new AutoValue_DisplayInfo.Builder();
  }

  /** Builder for display info */
  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder setDisplayedBraille(ByteBuffer displayedBraille);

    public abstract Builder setDisplayedOverlaidBraille(ByteBuffer displayedOverlaidBraille);

    public abstract Builder setDisplayedText(CharSequence text);

    public abstract Builder setDisplayedBrailleToTextPositions(
        List<Integer> brailleToTextPositions);

    public abstract Builder setBlink(boolean isBlink);

    public abstract Builder setSource(Source source);

    public abstract DisplayInfo build();
  }
}
