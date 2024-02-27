/*
 * Copyright (C) 2015 Google Inc.
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

package com.google.android.accessibility.braille.brailledisplay.controller.wrapping;

/**
 * A wrapping strategy where lines can break wherever there is an empty braille cell. Empty braille
 * cells are marked as "removable" break points, so they may be elided if they occur at the end of
 * the display line.
 */
public class WordWrapStrategy extends WrapStrategy {

  public WordWrapStrategy(int displayWidth) {
    super(displayWidth);
  }

  @Override
  protected void calculateBreakPoints() {
    WrapStrategyUtils.addWordWrapBreakPoints(
        breakPoints, translation.cells(), 0, translation.cells().size());
  }

  @Override
  void recalculateLineBreaks(int pivot) {
    if (lineBreaks.indexOfKey(pivot) < 0) {
      lineBreaks.append(calculateWordWrapPivot(pivot), LINE_BREAK);
    }
  }
}
