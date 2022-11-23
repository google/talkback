package com.google.android.accessibility.braille.brailledisplay.controller.wrapping;

/*
 * Copyright (C) 2022 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

import static java.lang.Math.max;
import static java.lang.Math.min;

/**
 * A wrapping strategy for an editor.
 *
 * <p>The displayed text has 3 parts: hint, user input and action. For the hint and action parts,
 * WordWrapStrategy is used.
 *
 * <p>For the user input part, if the input string stretches across two lines, then move 1/4 of the
 * text to the beginning of the next line.
 */
public class EditorWordWrapStrategy extends WrapStrategy {

  private static final double FRACTION = 0.25;

  public EditorWordWrapStrategy(int displayWidth) {
    super(displayWidth);
  }

  @Override
  void calculateBreakPoints() {
    final int preserve = (int) (displayWidth * FRACTION);
    int breakPoint =
        startIndexOfInput < displayWidth
            ? min(endIndexOfInput, displayWidth)
            : WrapStrategyUtils.findWordWrapCutPoint(
                translation.cells(), displayWidth, 0, startIndexOfInput);

    // Add break points before user input.
    WrapStrategyUtils.addWordWrapBreakPoints(
        breakPoints, translation.cells(), 0, startIndexOfInput - 1);
    for (int i = 0; i < startIndexOfInput; i++) {
      if (i < breakPoint) {
        continue;
      }
      // Find next break point.
      int newBreakPoint =
          WrapStrategyUtils.findWordWrapCutPoint(
              translation.cells(),
              min(breakPoint + displayWidth, endIndexOfInput),
              Math.max(0, breakPoint),
              startIndexOfInput);
      if (newBreakPoint >= startIndexOfInput) {
        // Break point goes into user input.
        int diff = startIndexOfInput - breakPoint;
        if (0 < diff && diff < preserve) {
          // Need to cut some next line to user input.
          breakPoint =
              WrapStrategyUtils.findWordWrapCutPoint(
                  translation.cells(),
                  breakPoint - (preserve - diff),
                  max(0, breakPoint - displayWidth),
                  startIndexOfInput);
          addBreakPoint(breakPoint);
        } else {
          breakPoint += displayWidth;
        }
        break;
      } else {
        breakPoint = newBreakPoint;
      }
    }

    // Keep adding break points in user input.
    for (int i = startIndexOfInput; i <= endIndexOfInput; i++) {
      if (i < breakPoint) {
        continue;
      }
      if (breakPoint < displayWidth && endIndexOfInput < displayWidth) {
        // one line
        addBreakPoint(breakPoint);
        break;
      } else {
        int cut =
            WrapStrategyUtils.findWordWrapCutPoint(
                translation.cells(),
                min(breakPoint - preserve, endIndexOfInput),
                max(0, breakPoint - displayWidth),
                breakPoint);
        addBreakPoint(cut);
        breakPoint = cut + displayWidth;
      }
    }

    // Add break points after user input.
    if (endIndexOfInput < translation.cells().size()
        && translation.cells().get(endIndexOfInput).isEmpty()) {
      addBreakPoint(endIndexOfInput + 1);
    }
    WrapStrategyUtils.addWordWrapBreakPoints(
        breakPoints, translation.cells(), endIndexOfInput + 1, translation.cells().size());
  }

  @Override
  void recalculateLineBreaks(int pivot) {
    if (lineBreaks.indexOfKey(pivot) < 0) {
      lineBreaks.append(calculateWordWrapPivot(pivot), LINE_BREAK);
    }
  }

  private void addBreakPoint(int breakPoint) {
    breakPoints.append(breakPoint, REMOVABLE_BREAK_POINT);
    breakPoints.append(breakPoint, UNREMOVABLE_BREAK_POINT);
  }
}
