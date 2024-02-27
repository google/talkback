package com.google.android.accessibility.braille.brailledisplay.controller;

import android.text.Spannable;
import android.text.Spanned;
import androidx.annotation.Nullable;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import com.google.errorprone.annotations.CanIgnoreReturnValue;

/**
 * Builder-like class used to construct the content to put on the display.
 *
 * <p>This object contains a {@link CharSequence} that represents what characters to put on the
 * display. This sequence can be a {@link Spannable} so that the characters can be annotated with
 * information about cursors and focus which will affect how the content is presented on the
 * display. Arbitrary java objects may also be included in the {@link Spannable} which can be used
 * to determine what action to take when the user invokes key commands related to a particular
 * position on the display (i.e. involving a cursor routing key). In particular, {@link
 * AccessibilityNodeInfoCompat}s may be included. To facilitate movement outside the bounds of the
 * current {@link CellsContent}, {@link AccessibilityNodeInfoCompat}s that represent the extent of
 * the content can also be added, but in that case, they are not included in the {@link Spannable}.
 */
public class CellsContent {

  private static final String TAG = "BrailleContent";

  /**
   * Pan strategy that moves the display to the leftmost position. This is the default panning
   * strategy.
   */
  public static final int PAN_RESET = 0;

  /**
   * Pan strategy that positions the display so that it overlaps the start of a selection or focus
   * mark. Falls back on {@code PAN_RESET} if there is no selection or focus.
   */
  public static final int PAN_CURSOR = 1;

  /**
   * Pan strategy that tries to position the display close to the position that corresponds to the
   * panning position in the previously displayed content. Spans of type {@link
   * AccessibilityNodeInfoCompat} are used to identify the corresponding content in the old and new
   * display content. Falls back on {@code SPAN_CURSOR} if a corresponding position can't be found.
   */
  public static final int PAN_KEEP = 2;

  /** Allow contraction, regardless of the presence of a selection span. */
  public static final int CONTRACT_ALWAYS_ALLOW = 1;

  private CharSequence text;
  private int panStrategy;
  private boolean splitParagraphs;

  /** Shortcut to just set text for a one-off use. */
  public CellsContent(CharSequence textArg) {
    text = textArg;
  }

  @CanIgnoreReturnValue
  public CellsContent setText(CharSequence textArg) {
    text = textArg;
    return this;
  }

  public CharSequence getText() {
    return text;
  }

  @Nullable
  public Spanned getSpanned() {
    if (text instanceof Spanned) {
      return (Spanned) text;
    }
    return null;
  }

  @CanIgnoreReturnValue
  public CellsContent setPanStrategy(int strategy) {
    panStrategy = strategy;
    return this;
  }

  public int getPanStrategy() {
    return panStrategy;
  }

  @CanIgnoreReturnValue
  public CellsContent setSplitParagraphs(boolean value) {
    splitParagraphs = value;
    return this;
  }

  public boolean isSplitParagraphs() {
    return splitParagraphs;
  }

  @Override
  public String toString() {
    return String.format("BrailleContent {text=%s}", getText());
  }
}
