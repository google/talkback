/*
 * Copyright 2021 Google Inc.
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

package com.google.android.accessibility.braille.common.translate;

import android.content.Context;
import android.content.res.Resources;
import com.google.android.accessibility.braille.common.R;
import com.google.android.accessibility.braille.common.TalkBackSpeaker;
import com.google.android.accessibility.braille.interfaces.BrailleCharacter;
import com.google.android.accessibility.braille.translate.BrailleTranslator;
import java.util.Optional;

/** An EditBuffer for Serbian Braille Grade 1. */
public class EditBufferSerbian extends EditBufferCommon {

  public EditBufferSerbian(
      Context context, BrailleTranslator ueb1Translator, TalkBackSpeaker talkBack) {
    super(context, ueb1Translator, talkBack);
  }

  @Override
  protected Optional<String> getAppendBrailleTextToSpeak(
      Resources resources, BrailleCharacter brailleCharacter) {
    if (brailleCharacter.equals(BrailleTranslateUtils.NUMERIC)) {
      return Optional.of(resources.getString(R.string.number_announcement));
    }
    return Optional.empty();
  }
}
