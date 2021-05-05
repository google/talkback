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

package com.google.android.accessibility.brailleime.translate;

import static java.lang.Character.PRIVATE_USE;

import android.content.res.Resources;
import com.google.android.accessibility.brailleime.BrailleCharacter;
import com.google.android.accessibility.brailleime.R;
import com.google.android.accessibility.brailleime.Utils;

/** Utils for translation of Braille. */
public class BrailleTranslateUtils {

  public static final BrailleCharacter NUMERIC = new BrailleCharacter(3, 4, 5, 6);

  /**
   * Returns a String representation of {@link BrailleCharacter} with human understanding text.
   * Example: "⠏" -> "dots 1 2 3 4".
   */
  public static String getDotsText(Resources resources, BrailleCharacter brailleCharacter) {
    String dotsString = Utils.insertSpacesInto(brailleCharacter.toString());
    return resources.getQuantityString(
        R.plurals.braille_dots, brailleCharacter.getOnCount(), dotsString);
  }

  /**
   * A translator will sometimes return a String result that is unpronounceable (instead of
   * returning an empty string). An example is the translation of 23456, which the LibLouis UEB
   * table translates as \uF501.
   */
  public static boolean isPronounceable(String text) {
    // We only attempt to recognize length-1 cases of this sort for now.
    if (text.length() == 1) {
      if (Character.getType(text.charAt(0)) == PRIVATE_USE) {
        return false;
      }
    }
    return true;
  }

  private BrailleTranslateUtils() {}
}
