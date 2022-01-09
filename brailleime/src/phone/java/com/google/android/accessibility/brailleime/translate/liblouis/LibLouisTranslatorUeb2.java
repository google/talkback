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

package com.google.android.accessibility.brailleime.translate.liblouis;

import android.content.Context;
import com.google.android.accessibility.brailleime.BrailleWord;
import com.google.android.accessibility.brailleime.Utils;
import com.google.android.accessibility.brailleime.translate.BrailleTranslateUtilsUeb;
import com.google.common.base.Ascii;

class LibLouisTranslatorUeb2 extends LibLouisTranslator {

  LibLouisTranslatorUeb2(Context context) {
    super(context, "en-ueb-g2.ctb");
    fillBypassMap();
  }

  private void fillBypassMap() {
    addToBypassMap("5-2346-3-234", "there's");
    addToBypassMap("26", "enough");
    addToBypassMap("136-256-234-256", "u.s.");
    addToBypassMap("24-256-15-256", "i.e.");
    addToBypassMap("15-256-1245-256", "e.g.");
    addToBypassMap("1234-256-134-256", "p.m.");
    addToBypassMap("1-256-134-256", "a.m.");
    addToBypassMap("5-2346-3-123-123", "there'll");
    addToBypassMap("5-2346-3-145", "there'd");
    addToBypassMap("1234-256-1234-256", "p.p.");
    addToBypassMap("136-1345-234-145", "unsaid");
    addToBypassMap("6-1234-125-256-6-145-256", "Ph.D.");
    addToBypassMap("1-256-136-256", "a.u.");
    addToBypassMap("12-1246-123456-145-146-24-1235-15", "bedfordshire");
    addToBypassMap("123-24-3-123", "li'l");
    addToBypassMap("14-1-1345-3-34", "can'st");
    addToBypassMap("2346-1235-15-5-136", "thereunder");
    addToBypassMap("145-256-134-256", "d.m.");
    addToBypassMap("124-1235-123-24-123-13456", "friendlily");
  }

  private void addToBypassMap(String dashEncoded, String print) {
    // Handle lower-case (example: somewhere).
    getBypassMap().put(BrailleWord.create(dashEncoded), print);
    // Handle initial-letter-upper-case (example: Somewhere).
    getBypassMap()
        .put(
            BrailleWord.create(BrailleTranslateUtilsUeb.CAPITALIZE + "-" + dashEncoded),
            Utils.capitalizeFirstLetter(print));
    // Handle all-word-upper-case (example: SOMEWHERE).
    getBypassMap()
        .put(
            BrailleWord.create(
                BrailleTranslateUtilsUeb.CAPITALIZE
                    + "-"
                    + BrailleTranslateUtilsUeb.CAPITALIZE
                    + "-"
                    + dashEncoded),
            Ascii.toUpperCase(print));
  }
}
