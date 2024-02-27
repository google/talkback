package com.google.android.accessibility.braille.common.translate;

import static com.google.android.accessibility.braille.common.translate.BrailleTranslateUtils.DOTS456;

import android.content.Context;
import com.google.android.accessibility.braille.common.TalkBackSpeaker;
import com.google.android.accessibility.braille.interfaces.BrailleCharacter;
import com.google.android.accessibility.braille.translate.BrailleTranslator;

/** An EditBuffer for English 6-dot computer Braille. */
public class EditBufferEnComp6 extends EditBufferUnContracted {
  public EditBufferEnComp6(
      Context context, BrailleTranslator ueb1Translator, TalkBackSpeaker talkBack) {
    super(context, ueb1Translator, talkBack);
  }

  @Override
  protected BrailleCharacter getCapitalize() {
    return DOTS456;
  }

  @Override
  protected BrailleCharacter getNumeric() {
    return null;
  }
}
