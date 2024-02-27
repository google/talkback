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
