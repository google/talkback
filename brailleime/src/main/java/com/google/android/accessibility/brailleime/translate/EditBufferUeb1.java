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

package com.google.android.accessibility.brailleime.translate;

import android.content.Context;
import android.content.res.Resources;
import com.google.android.accessibility.brailleime.BrailleCharacter;
import com.google.android.accessibility.brailleime.TalkBackForBrailleImeInternal;
import java.util.Optional;

/** Unified English Braille (UEB) Grade 1 EditBuffer. */
public class EditBufferUeb1 extends EditBufferCommon {

  public EditBufferUeb1(
      Context context, Translator ueb1Translator, TalkBackForBrailleImeInternal talkBack) {
    super(context, ueb1Translator, talkBack);
  }

  @Override
  protected Optional<String> getAppendBrailleTextToSpeak(
      Resources resources, BrailleCharacter brailleCharacter) {
    return Optional.of(BrailleTranslateUtilsUeb.getTextToSpeak(resources, brailleCharacter));
  }
}
