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

import android.graphics.PointF;
import androidx.annotation.IntDef;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.List;
import javax.annotation.Nullable;

/**
 * The result produced by a {@link MultitouchHandler} commission.
 *
 * <p>Data fields are non-null depending on the type, as follows:
 *
 * <ul>
 *   <li>For type {@link MultitouchResult#TYPE_TAP}, field {@code releasePoints} is non-null.
 *   <li>For type {@link MultitouchResult#TYPE_SWIPE}, field {@code swipe} is non-null.
 *   <li>For type {@link MultitouchResult#TYPE_HOLD}, field {@code hold} is non-null.
 * </ul>
 */
class MultitouchResult {

  @IntDef({TYPE_TAP, TYPE_SWIPE, TYPE_HOLD, TYPE_CALIBRATION_HOLD, TYPE_HOLD_AND_SWIPE})
  @Retention(RetentionPolicy.SOURCE)
  public @interface Type {}

  static final int TYPE_TAP = 0;
  static final int TYPE_SWIPE = 1;
  static final int TYPE_HOLD = 2;
  static final int TYPE_CALIBRATION_HOLD = 3;
  static final int TYPE_HOLD_AND_SWIPE = 4;

  @Type int type;
  @Nullable Swipe swipe;
  @Nullable List<PointF> heldPoints;
  @Nullable List<PointF> releasedPoints;

  private MultitouchResult() {}

  static MultitouchResult createTap(List<PointF> releaseContributors) {
    MultitouchResult result = new MultitouchResult();
    result.type = TYPE_TAP;
    result.releasedPoints = releaseContributors;
    return result;
  }

  static MultitouchResult createSwipe(Swipe swipe, List<PointF> releaseContributors) {
    MultitouchResult result = new MultitouchResult();
    result.type = TYPE_SWIPE;
    result.swipe = swipe;
    result.releasedPoints = releaseContributors;
    return result;
  }

  static MultitouchResult createCalibrationHold(List<PointF> holdContributors) {
    MultitouchResult result = new MultitouchResult();
    result.type = TYPE_CALIBRATION_HOLD;
    result.heldPoints = holdContributors;
    return result;
  }

  static MultitouchResult createHold(List<PointF> holdContributors) {
    MultitouchResult result = new MultitouchResult();
    result.type = TYPE_HOLD;
    result.heldPoints = holdContributors;
    return result;
  }

  static MultitouchResult createHoldAndDotSwipe(
      List<PointF> holdContributors, Swipe swipe, List<PointF> releaseContributors) {
    MultitouchResult result = new MultitouchResult();
    result.type = TYPE_HOLD_AND_SWIPE;
    result.swipe = swipe;
    result.heldPoints = holdContributors;
    result.releasedPoints = releaseContributors;
    return result;
  }

  @Override
  public String toString() {
    return "MultitouchResult{"
        + "type="
        + type
        + ", releasePoints="
        + releasedPoints
        + ", swipe="
        + swipe
        + ", heldPoints="
        + heldPoints
        + '}';
  }
}
