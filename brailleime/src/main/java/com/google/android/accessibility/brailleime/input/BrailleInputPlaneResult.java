/*
 * Copyright 2019 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.android.accessibility.brailleime.input;

import android.content.res.Configuration;
import androidx.annotation.IntDef;
import com.google.android.accessibility.brailleime.BrailleCharacter;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import javax.annotation.Nullable;

/**
 * The result produced by a {@link BrailleInputPlane} commission.
 *
 * <p>Data fields are non-null depending on the type, as follows:
 *
 * <ul>
 *   <li>For type {@link MultitouchResult#TYPE_TAP}, field {@code brailleCharacter} is non-null.
 *   <li>For type {@link MultitouchResult#TYPE_SWIPE}, field {@code swipe} is non-null.
 *   <li>For type {@link MultitouchResult#TYPE_HOLD}, field {@code pointersHeldCount} is non-null.
 * </ul>
 */
class BrailleInputPlaneResult {
  @IntDef({TYPE_TAP, TYPE_SWIPE, TYPE_CALIBRATION})
  @Retention(RetentionPolicy.SOURCE)
  public @interface Type {}

  static final int TYPE_TAP = 0;
  static final int TYPE_SWIPE = 1;
  static final int TYPE_CALIBRATION = 2;

  @Type int type;
  @Nullable BrailleCharacter brailleCharacter;
  @Nullable Swipe swipe;
  int pointersHeldCount;
  boolean isLeft;

  private BrailleInputPlaneResult() {}

  static BrailleInputPlaneResult createTapAndRelease(BrailleCharacter brailleCharacter) {
    BrailleInputPlaneResult result = new BrailleInputPlaneResult();
    result.type = TYPE_TAP;
    result.brailleCharacter = brailleCharacter;
    return result;
  }

  static BrailleInputPlaneResult createCalibration(boolean isLeft, int pointersHeldCount) {
    BrailleInputPlaneResult result = new BrailleInputPlaneResult();
    result.type = TYPE_CALIBRATION;
    result.pointersHeldCount = pointersHeldCount;
    result.isLeft = isLeft;
    return result;
  }

  static BrailleInputPlaneResult createSwipeForPhone(
      Swipe swipe, int orientation, boolean isTableTopMode) {
    Swipe reorientedSwipe =
        (orientation == Configuration.ORIENTATION_PORTRAIT)
            ? Swipe.createFromRotation90(swipe)
            : new Swipe(swipe);
    if (isTableTopMode) {
      reorientedSwipe = Swipe.createFromMirror(reorientedSwipe);
    }

    BrailleInputPlaneResult result = new BrailleInputPlaneResult();
    result.type = MultitouchResult.TYPE_SWIPE;
    result.swipe = reorientedSwipe;
    return result;
  }

  static BrailleInputPlaneResult createSwipeForTablet(Swipe swipe) {
    Swipe reorientedSwipe = Swipe.createFromMirror(swipe);
    BrailleInputPlaneResult result = new BrailleInputPlaneResult();
    result.type = MultitouchResult.TYPE_SWIPE;
    result.swipe = reorientedSwipe;
    return result;
  }

  @Override
  public String toString() {
    return "BrailleInputPlaneResult{"
        + "type="
        + type
        + ", brailleCharacter="
        + brailleCharacter
        + ", swipe="
        + swipe
        + ", pointersHeldCount="
        + pointersHeldCount
        + '}';
  }
}
