package com.google.android.accessibility.brailleime.translate;

import android.content.Context;
import android.content.res.Resources;
import com.google.android.accessibility.brailleime.BrailleCharacter;
import com.google.android.accessibility.brailleime.R;
import com.google.android.accessibility.brailleime.TalkBackForBrailleImeInternal;
import java.util.Optional;

/** An EditBuffer for Arabic Braille. */
public class EditBufferArabic extends EditBufferCommon {

  public EditBufferArabic(
      Context context, Translator ueb1Translator, TalkBackForBrailleImeInternal talkBack) {
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
