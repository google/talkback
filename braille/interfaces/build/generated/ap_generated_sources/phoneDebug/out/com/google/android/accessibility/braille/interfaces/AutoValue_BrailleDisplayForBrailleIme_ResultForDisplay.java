package com.google.android.accessibility.braille.interfaces;

import android.util.Range;
import javax.annotation.Generated;

@Generated("com.google.auto.value.processor.AutoValueProcessor")
final class AutoValue_BrailleDisplayForBrailleIme_ResultForDisplay extends BrailleDisplayForBrailleIme.ResultForDisplay {

  private final CharSequence onScreenText;

  private final Range<Integer> textSelectionRange;

  private final BrailleDisplayForBrailleIme.ResultForDisplay.HoldingsInfo holdingsInfo;

  private final boolean isMultiLine;

  private final String hint;

  private final String action;

  private AutoValue_BrailleDisplayForBrailleIme_ResultForDisplay(
      CharSequence onScreenText,
      Range<Integer> textSelectionRange,
      BrailleDisplayForBrailleIme.ResultForDisplay.HoldingsInfo holdingsInfo,
      boolean isMultiLine,
      String hint,
      String action) {
    this.onScreenText = onScreenText;
    this.textSelectionRange = textSelectionRange;
    this.holdingsInfo = holdingsInfo;
    this.isMultiLine = isMultiLine;
    this.hint = hint;
    this.action = action;
  }

  @Override
  public CharSequence onScreenText() {
    return onScreenText;
  }

  @Override
  public Range<Integer> textSelectionRange() {
    return textSelectionRange;
  }

  @Override
  public BrailleDisplayForBrailleIme.ResultForDisplay.HoldingsInfo holdingsInfo() {
    return holdingsInfo;
  }

  @Override
  public boolean isMultiLine() {
    return isMultiLine;
  }

  @Override
  public String hint() {
    return hint;
  }

  @Override
  public String action() {
    return action;
  }

  @Override
  public String toString() {
    return "ResultForDisplay{"
        + "onScreenText=" + onScreenText + ", "
        + "textSelectionRange=" + textSelectionRange + ", "
        + "holdingsInfo=" + holdingsInfo + ", "
        + "isMultiLine=" + isMultiLine + ", "
        + "hint=" + hint + ", "
        + "action=" + action
        + "}";
  }

  @Override
  public boolean equals(Object o) {
    if (o == this) {
      return true;
    }
    if (o instanceof BrailleDisplayForBrailleIme.ResultForDisplay) {
      BrailleDisplayForBrailleIme.ResultForDisplay that = (BrailleDisplayForBrailleIme.ResultForDisplay) o;
      return this.onScreenText.equals(that.onScreenText())
          && this.textSelectionRange.equals(that.textSelectionRange())
          && this.holdingsInfo.equals(that.holdingsInfo())
          && this.isMultiLine == that.isMultiLine()
          && this.hint.equals(that.hint())
          && this.action.equals(that.action());
    }
    return false;
  }

  @Override
  public int hashCode() {
    int h$ = 1;
    h$ *= 1000003;
    h$ ^= onScreenText.hashCode();
    h$ *= 1000003;
    h$ ^= textSelectionRange.hashCode();
    h$ *= 1000003;
    h$ ^= holdingsInfo.hashCode();
    h$ *= 1000003;
    h$ ^= isMultiLine ? 1231 : 1237;
    h$ *= 1000003;
    h$ ^= hint.hashCode();
    h$ *= 1000003;
    h$ ^= action.hashCode();
    return h$;
  }

  static final class Builder extends BrailleDisplayForBrailleIme.ResultForDisplay.Builder {
    private CharSequence onScreenText;
    private Range<Integer> textSelectionRange;
    private BrailleDisplayForBrailleIme.ResultForDisplay.HoldingsInfo holdingsInfo;
    private Boolean isMultiLine;
    private String hint;
    private String action;
    Builder() {
    }
    @Override
    public BrailleDisplayForBrailleIme.ResultForDisplay.Builder setOnScreenText(CharSequence onScreenText) {
      if (onScreenText == null) {
        throw new NullPointerException("Null onScreenText");
      }
      this.onScreenText = onScreenText;
      return this;
    }
    @Override
    public BrailleDisplayForBrailleIme.ResultForDisplay.Builder setTextSelectionRange(Range<Integer> textSelectionRange) {
      if (textSelectionRange == null) {
        throw new NullPointerException("Null textSelectionRange");
      }
      this.textSelectionRange = textSelectionRange;
      return this;
    }
    @Override
    public BrailleDisplayForBrailleIme.ResultForDisplay.Builder setHoldingsInfo(BrailleDisplayForBrailleIme.ResultForDisplay.HoldingsInfo holdingsInfo) {
      if (holdingsInfo == null) {
        throw new NullPointerException("Null holdingsInfo");
      }
      this.holdingsInfo = holdingsInfo;
      return this;
    }
    @Override
    public BrailleDisplayForBrailleIme.ResultForDisplay.Builder setIsMultiLine(boolean isMultiLine) {
      this.isMultiLine = isMultiLine;
      return this;
    }
    @Override
    public BrailleDisplayForBrailleIme.ResultForDisplay.Builder setHint(String hint) {
      if (hint == null) {
        throw new NullPointerException("Null hint");
      }
      this.hint = hint;
      return this;
    }
    @Override
    public BrailleDisplayForBrailleIme.ResultForDisplay.Builder setAction(String action) {
      if (action == null) {
        throw new NullPointerException("Null action");
      }
      this.action = action;
      return this;
    }
    @Override
    public BrailleDisplayForBrailleIme.ResultForDisplay build() {
      if (this.onScreenText == null
          || this.textSelectionRange == null
          || this.holdingsInfo == null
          || this.isMultiLine == null
          || this.hint == null
          || this.action == null) {
        StringBuilder missing = new StringBuilder();
        if (this.onScreenText == null) {
          missing.append(" onScreenText");
        }
        if (this.textSelectionRange == null) {
          missing.append(" textSelectionRange");
        }
        if (this.holdingsInfo == null) {
          missing.append(" holdingsInfo");
        }
        if (this.isMultiLine == null) {
          missing.append(" isMultiLine");
        }
        if (this.hint == null) {
          missing.append(" hint");
        }
        if (this.action == null) {
          missing.append(" action");
        }
        throw new IllegalStateException("Missing required properties:" + missing);
      }
      return new AutoValue_BrailleDisplayForBrailleIme_ResultForDisplay(
          this.onScreenText,
          this.textSelectionRange,
          this.holdingsInfo,
          this.isMultiLine,
          this.hint,
          this.action);
    }
  }

}
