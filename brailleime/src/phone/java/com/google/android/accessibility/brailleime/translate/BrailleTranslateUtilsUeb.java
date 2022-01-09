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

import static com.google.android.accessibility.brailleime.translate.BrailleTranslateUtils.NUMERIC;

import android.content.res.Resources;
import com.google.android.accessibility.brailleime.BrailleCharacter;
import com.google.android.accessibility.brailleime.R;

/** Utils for translation of UEB Braille. */
public class BrailleTranslateUtilsUeb {

  public static final BrailleCharacter CAPITALIZE = new BrailleCharacter(6);
  public static final BrailleCharacter LETTER_A = new BrailleCharacter(1);
  public static final BrailleCharacter LETTER_B = new BrailleCharacter(1, 2);
  public static final BrailleCharacter LETTER_C = new BrailleCharacter(1, 4);
  public static final BrailleCharacter LETTER_D = new BrailleCharacter(1, 4, 5);

  /**
   * Return a brief announcement text that represents the given {@link BrailleCharacter}, in case
   * such an announcement is appropriate. This is useful for certain prefixes that do not have a
   * stand-alone translation, but are important enough that the user would appreciate an audial
   * description of it.
   */
  public static String getTextToSpeak(Resources resources, BrailleCharacter brailleCharacter) {
    String textToSpeak = "";
    if (brailleCharacter.equals(CAPITALIZE)) {
      textToSpeak = resources.getString(R.string.capitalize_announcement);
    } else if (brailleCharacter.equals(NUMERIC)) {
      textToSpeak = resources.getString(R.string.number_announcement);
    }
    return textToSpeak;
  }

  private BrailleTranslateUtilsUeb() {}
}
