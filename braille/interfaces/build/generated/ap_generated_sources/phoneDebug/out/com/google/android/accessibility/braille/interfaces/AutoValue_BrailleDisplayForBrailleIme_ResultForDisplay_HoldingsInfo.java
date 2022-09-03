package com.google.android.accessibility.braille.interfaces;

import java.nio.ByteBuffer;
import javax.annotation.Generated;

@Generated("com.google.auto.value.processor.AutoValueProcessor")
final class AutoValue_BrailleDisplayForBrailleIme_ResultForDisplay_HoldingsInfo extends BrailleDisplayForBrailleIme.ResultForDisplay.HoldingsInfo {

  private final ByteBuffer holdings;

  private final int position;

  AutoValue_BrailleDisplayForBrailleIme_ResultForDisplay_HoldingsInfo(
      ByteBuffer holdings,
      int position) {
    if (holdings == null) {
      throw new NullPointerException("Null holdings");
    }
    this.holdings = holdings;
    this.position = position;
  }

  @Override
  public ByteBuffer holdings() {
    return holdings;
  }

  @Override
  public int position() {
    return position;
  }

  @Override
  public String toString() {
    return "HoldingsInfo{"
        + "holdings=" + holdings + ", "
        + "position=" + position
        + "}";
  }

  @Override
  public boolean equals(Object o) {
    if (o == this) {
      return true;
    }
    if (o instanceof BrailleDisplayForBrailleIme.ResultForDisplay.HoldingsInfo) {
      BrailleDisplayForBrailleIme.ResultForDisplay.HoldingsInfo that = (BrailleDisplayForBrailleIme.ResultForDisplay.HoldingsInfo) o;
      return this.holdings.equals(that.holdings())
          && this.position == that.position();
    }
    return false;
  }

  @Override
  public int hashCode() {
    int h$ = 1;
    h$ *= 1000003;
    h$ ^= holdings.hashCode();
    h$ *= 1000003;
    h$ ^= position;
    return h$;
  }

}
