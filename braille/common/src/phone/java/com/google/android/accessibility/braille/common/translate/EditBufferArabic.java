package com.google.android.accessibility.braille.common.translate;

import android.content.Context;
import android.content.res.Resources;
import com.google.android.accessibility.braille.common.R;
import com.google.android.accessibility.braille.common.TalkBackSpeaker;
import com.google.android.accessibility.braille.interfaces.BrailleCharacter;
import com.google.android.accessibility.braille.translate.BrailleTranslator;
import java.util.Optional;

/** An EditBuffer for Arabic Braille. */
public class EditBufferArabic extends EditBufferCommon {

  public EditBufferArabic(
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
