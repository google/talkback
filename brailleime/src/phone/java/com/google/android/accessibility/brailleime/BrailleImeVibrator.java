/*
 * Copyright 2020 Google Inc.
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

package com.google.android.accessibility.brailleime;

import android.app.Service;
import android.content.Context;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;

/** Singleton class which presents vibrations in braille keyboard. */
public class BrailleImeVibrator {

  /** Vibration type in braille keyboard. */
  public enum VibrationType {
    BRAILLE_COMMISSION(25, 120),
    SPACE_OR_DELETE(70, 150),
    NEWLINE_OR_DELETE_WORD(120, 180),
    OTHER_GESTURES(190, 210),
    ;

    private final int duration;
    private final int amplitude;

    VibrationType(int duration, int amplitude) {
      this.duration = duration;
      this.amplitude = amplitude;
    }
  }

  private static BrailleImeVibrator instance;
  private final Vibrator vibrator;
  private boolean enabled = false;

  public static BrailleImeVibrator getInstance(Context context) {
    if (instance == null) {
      instance = new BrailleImeVibrator(context.getApplicationContext());
    }
    return instance;
  }

  private BrailleImeVibrator(Context context) {
    vibrator = (Vibrator) context.getSystemService(Service.VIBRATOR_SERVICE);
  }

  public void enable() {
    enabled = true;
  }

  public void disable() {
    enabled = false;
  }

  /**
   * Vibrates with {@link Vibrator}.
   *
   * @param vibrationType specific vibration type.
   */
  public void vibrate(VibrationType vibrationType) {
    if (!enabled) {
      return;
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      vibrator.vibrate(
          VibrationEffect.createOneShot(vibrationType.duration, vibrationType.amplitude));
    } else {
      vibrator.vibrate(vibrationType.duration);
    }
  }
}
