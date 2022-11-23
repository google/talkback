package com.google.android.accessibility.braille.brailledisplay.controller.wrapping;

import static com.google.android.accessibility.braille.brailledisplay.controller.wrapping.WrapStrategy.REMOVABLE_BREAK_POINT;
import static com.google.android.accessibility.braille.brailledisplay.controller.wrapping.WrapStrategy.UNREMOVABLE_BREAK_POINT;

import android.util.SparseIntArray;
import com.google.android.accessibility.braille.interfaces.BrailleWord;
import com.google.common.base.Preconditions;

/** Utility methods for wrap strategy. */
public final class WrapStrategyUtils {

  /**
   * Find the best cut point. The indicated cut point might cut off a word. If the cut point is
   * between a word, find the beginning of word. If the word is too long that can't find the
   * beginning, cut it in the original point anyway. For example: a 12-cell braille display<br>
   * <br>
   * line1: "Hi Gooooo" -> 3 cells move to next line when continuing typing<br>
   * line2: "oooooooog" -> Can't find the beginning of word so cut it at the original point.<br>
   * line3: "le|"<br>
   * <br>
   * If the cut point is a space, find the beginning of next word. If there are too many spaces that
   * can't find the next word, cut it in the original point anyway. For example: a 12-cell braille
   * display<br>
   * <br>
   * line1: "Hi Gooooo" -> 3 cells move to next line when continuing typing<br>
   * line2: "oooogle " -> Can't find the beginning of word so cut it at the original point.<br>
   * line3: " Ok|"
   */
  public static int findWordWrapCutPoint(
      BrailleWord brailleWord, int cutPoint, int begin, int end) {
    if (brailleWord.get(cutPoint).isEmpty()) {
      for (int i = cutPoint + 1; i < end; i++) {
        // Find the next non-empty character position.
        if (!brailleWord.get(i).isEmpty()) {
          return i;
        }
      }
    } else {
      for (int i = cutPoint - 1; i >= begin; i--) {
        // Find the most front non-character position.
        if (brailleWord.get(i).isEmpty()) {
          return i + 1;
        }
      }
    }
    return cutPoint;
  }

  /**
   * Adds break points marked as removable and unremovable from start to end. <br>
   * For example, "Hi&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;Google", the index after "i" is marked as
   * removable and the index before "G" is marked as unremovable. When this display limit falls
   * between removable and unremovable, the characters after display limit to unremovable will be
   * omitted.
   */
  public static void addWordWrapBreakPoints(
      SparseIntArray breakPoints, BrailleWord cells, int start, int end) {
    Preconditions.checkArgument(!cells.isEmpty() && start >= 0 && end <= cells.size());
    boolean previousCellEmpty = false;
    for (int i = start; i < end; ++i) {
      boolean currentCellEmpty = cells.get(i).isEmpty();
      if (currentCellEmpty) {
        breakPoints.append(i, REMOVABLE_BREAK_POINT);
      } else if (previousCellEmpty) {
        breakPoints.append(i, UNREMOVABLE_BREAK_POINT);
      }
      previousCellEmpty = currentCellEmpty;
    }
  }

  private WrapStrategyUtils() {}
}
