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

package com.google.android.accessibility.braille.brailledisplay.controller;

import androidx.annotation.VisibleForTesting;
import com.google.android.accessibility.braille.interfaces.SelectionRange;
import com.google.android.accessibility.braille.translate.TranslationResult;

/** Wrapper wraps {@link DisplayInfo} with it necessary component {@link ContentHelper}. */
public class DisplayInfoWrapper {
  private ContentHelper contentHelper;
  private DisplayInfo displayInfo;
  private boolean reachToEnd;
  private boolean reachToBeginning;

  public DisplayInfoWrapper(ContentHelper contentHelper) {
    this.contentHelper = contentHelper;
  }

  /** Renews the {@link DisplayInfo} data with new parameters. */
  public void renewDisplayInfo(CharSequence text, int panStrategy, boolean isSplitParagraphs) {
    displayInfo = contentHelper.generateDisplayInfo(text, panStrategy, isSplitParagraphs);
    reset();
  }

  /** Renews the {@link DisplayInfo} data with new parameters. */
  public void renewDisplayInfo(
      int panStrategy,
      SelectionRange selection,
      int beginningOfInput,
      int endOfInput,
      boolean isSplitParagraphs,
      TranslationResult translationResult,
      DisplayInfo.Source source) {
    displayInfo =
        contentHelper.generateDisplayInfo(
            panStrategy,
            selection,
            beginningOfInput,
            endOfInput,
            isSplitParagraphs,
            translationResult,
            source);
    reset();
  }

  /**
   * Pans the content to the previous window and updates the {@link DisplayInfo}. Returns true if
   * pan executed.
   */
  public boolean panUp() {
    reachToEnd = false;
    if (hasReachedToBeginning()) {
      return false;
    }
    DisplayInfo newDisplayInfo = contentHelper.panUp(displayInfo.source());
    if (newDisplayInfo == null) {
      reachToBeginning = true;
      return false;
    } else {
      displayInfo = newDisplayInfo;
      reachToBeginning = false;
      return true;
    }
  }

  /**
   * Pans the content to the next window and updates the {@link DisplayInfo}. Returns true if pan
   * executed.
   */
  public boolean panDown() {
    reachToBeginning = false;
    if (hasReachedToEnd()) {
      return false;
    }
    DisplayInfo newDisplayInfo = contentHelper.panDown(displayInfo.source());
    if (newDisplayInfo == null) {
      reachToEnd = true;
      return false;
    } else {
      displayInfo = newDisplayInfo;
      reachToEnd = false;
      return true;
    }
  }

  /** Retranslates the content. */
  public void retranslate() {
    if (hasDisplayInfo() && displayInfo.source() == DisplayInfo.Source.DEFAULT) {
      displayInfo = contentHelper.retranslate();
      reset();
    }
  }

  /** Clears the content. */
  public void clear() {
    displayInfo = null;
  }

  /** Checks whether {@link DisplayInfo} exists. */
  public boolean hasDisplayInfo() {
    return displayInfo != null;
  }

  /** Whether {@link DisplayInfo} has pan overflow the head. */
  public boolean hasReachedToBeginning() {
    return hasDisplayInfo() && reachToBeginning;
  }

  /** Whether {@link DisplayInfo} has pan overflow the tail. */
  public boolean hasReachedToEnd() {
    return hasDisplayInfo() && reachToEnd;
  }

  /** Gets wrapped {@link DisplayInfo}. */
  public DisplayInfo getDisplayInfo() {
    return displayInfo;
  }

  /** Gets wrapped {@link ContentHelper}. */
  public ContentHelper getContentHelper() {
    return contentHelper;
  }

  private void reset() {
    reachToBeginning = false;
    reachToEnd = false;
  }

  @VisibleForTesting
  void testing_setContentHelper(ContentHelper contentHelper) {
    this.contentHelper = contentHelper;
  }
}
