package com.google.android.accessibility.braille.common;

/** Braille constants. */
public class Constants {
  /** Braille types. */
  public enum BrailleType {
    SIX_DOT(/* dotCount= */ 6),
    EIGHT_DOT(/* dotCount = */ 8),
    ;

    private final int dotCount;

    BrailleType(int dotCount) {
      this.dotCount = dotCount;
    }

    public int getDotCount() {
      return dotCount;
    }
  }

  private Constants() {}
}
