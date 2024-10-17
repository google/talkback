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

package com.google.android.accessibility.brailleime.input;

import com.google.android.accessibility.braille.interfaces.BrailleCharacter;

/** Integrates the gesture of braille keyboard. */
public interface Gesture {
  /** Returns a {@link Swipe} if the gesture includes swipe, otherwise null. */
  Swipe getSwipe();

  /**
   * Returns a {@link BrailleCharacter} if the gesture includes dot hold, otherwise empty
   * BrailleCharacter.
   */
  BrailleCharacter getHeldDots();

  /** Returns a {@link Gesture} with mirroring hold dot. */
  Gesture mirrorDots();
}
