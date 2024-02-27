package com.google.android.accessibility.braille.common.translate;

import static com.google.android.accessibility.braille.common.translate.BrailleTranslateUtils.DOTS46;

import android.content.Context;
import com.google.android.accessibility.braille.common.TalkBackSpeaker;
import com.google.android.accessibility.braille.interfaces.BrailleCharacter;
import com.google.android.accessibility.braille.translate.BrailleTranslator;

/** An EditBuffer for German Braille. */
public class EditBufferGerman extends EditBufferUnContracted {
  public EditBufferGerman(
      Context context, BrailleTranslator ueb1Translator, TalkBackSpeaker talkBack) {
    super(context, ueb1Translator, talkBack);
  }

  @Override
  protected BrailleCharacter getCapitalize() {
    return DOTS46;
  }
}
