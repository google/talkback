/*
 * Copyright (C) 2023 Google Inc.
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

package com.google.android.accessibility.brailleime;

import com.google.android.accessibility.braille.common.Constants.BrailleType;
import com.google.auto.value.AutoValue;

/** The options of braille input. */
@AutoValue
public abstract class BrailleInputOptions {
  /** Whether braille input is for tutorial mode. */
  public abstract boolean tutorialMode();

  /** Whether dots should reverse. */
  public abstract boolean reverseDots();

  /** The amount of braille dots. Classic braille is 6 while computer braille is 8. */
  public abstract BrailleType brailleType();

  public static Builder builder() {
    return new AutoValue_BrailleInputOptions.Builder()
        .setTutorialMode(false)
        .setBrailleType(BrailleType.SIX_DOT)
        .setReverseDots(false);
  }

  /** Builder for {@link BrailleInputOptions}. */
  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder setTutorialMode(boolean tutorialMode);

    public abstract Builder setReverseDots(boolean reverseDots);

    public abstract Builder setBrailleType(BrailleType brailleType);

    public abstract BrailleInputOptions build();
  }
}
