package com.google.android.accessibility.braille.common.translate;

import static com.google.android.accessibility.braille.common.translate.BrailleTranslateUtils.DOTS46;

import android.content.Context;
import com.google.android.accessibility.braille.common.TalkBackSpeaker;
import com.google.android.accessibility.braille.interfaces.BrailleCharacter;
import com.google.android.accessibility.braille.translate.BrailleTranslator;

/** An EditBuffer for Latvian Braille. */
public class EditBufferLatvian extends EditBufferUnContracted {
  public EditBufferLatvian(
      Context context, BrailleTranslator brailleTranslator, TalkBackSpeaker talkBack) {
    super(context, brailleTranslator, talkBack);
  }

  @Override
  protected BrailleCharacter getCapitalize() {
    return DOTS46;
  }
}
