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

package com.google.android.accessibility.braille.brailledisplay.controller;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import java.nio.ByteBuffer;
import java.util.List;

/** The information displayer and overlay needs. */
@AutoValue
public abstract class DisplayInfo {

  /** Indicates where the display info is from. */
  enum Source {
    DEFAULT,
    IME
  }

  // Displayed content, already trimmed based on the display position.
  // Updated in updateDisplayedContent() and used in refresh().
  public abstract ByteBuffer displayedBraille();

  public abstract ByteBuffer displayedOverlaidBraille();

  public abstract CharSequence displayedText();

  public abstract ImmutableList<Integer> displayedBrailleToTextPositions();

  public abstract boolean blink();

  public abstract Source source();

  public static Builder builder() {
    return new AutoValue_DisplayInfo.Builder();
  }

  /** Builder for display info */
  @AutoValue.Builder
  public abstract static class Builder {
    public abstract Builder setDisplayedBraille(ByteBuffer displayedBraille);

    public abstract Builder setDisplayedOverlaidBraille(ByteBuffer displayedOverlaidBraille);

    public abstract Builder setDisplayedText(CharSequence text);

    public abstract Builder setDisplayedBrailleToTextPositions(
        List<Integer> brailleToTextPositions);

    public abstract Builder setBlink(boolean isBlink);

    public abstract Builder setSource(Source source);

    public abstract DisplayInfo build();
  }
}
