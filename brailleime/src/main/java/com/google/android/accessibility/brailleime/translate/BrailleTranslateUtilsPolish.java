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

/** Utils for translation of Polish Braille. */
public class BrailleTranslateUtilsPolish {

  public static final BrailleCharacter CAPITALIZE = new BrailleCharacter(4, 6);
  public static final BrailleCharacter NUMERIC = new BrailleCharacter(3,4,5,6);

  private BrailleTranslateUtilsPolish() {}
}
