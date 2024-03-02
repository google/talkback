package com.google.android.accessibility.braille.common.translate;

import android.content.Context;
import androidx.annotation.Nullable;
import com.google.android.accessibility.braille.common.EmojiUtils;
import com.google.android.accessibility.braille.common.FeatureFlagReader;
import com.google.android.accessibility.braille.interfaces.BrailleCharacter;
import com.google.android.accessibility.braille.interfaces.BrailleWord;
import com.google.android.accessibility.braille.translate.BrailleTranslator;
import com.google.android.accessibility.braille.translate.TranslationResult;
import com.google.android.accessibility.braille.translate.TranslationResultCustomizer;
import java.util.regex.Matcher;

/**
 * TranslationResultCustomizer customizes the result for some indicator replacement such as newline.
 */
public class GoogleTranslationResultCustomizer implements TranslationResultCustomizer {
  private static final char NEW_LINE = '\n';
  private final Context context;

  public GoogleTranslationResultCustomizer(Context context) {
    this.context = context;
  }

  @Nullable
  @Override
  public TranslationResult customize(TranslationResult result, BrailleTranslator translator) {
    if (result == null) {
      return result;
    }
    // Force translate '\n' to space-1246-123.
    for (int i = 0; i < result.text().length(); i++) {
      if (result.text().charAt(i) != NEW_LINE) {
        continue;
      }
      BrailleWord correctBrailleWord = new BrailleWord();
      correctBrailleWord.append(BrailleCharacter.EMPTY_CELL);
      correctBrailleWord.append(BrailleWord.NEW_LINE);
      result = TranslationResult.correctTranslation(result, correctBrailleWord, i, i + 1);
    }

    if (FeatureFlagReader.useReplaceEmoji(context)) {
      // Replace emoji with emoji's short code.
      Matcher emojis = EmojiUtils.findEmoji(result.text());
      while (emojis.find()) {
        String replacement = EmojiUtils.getEmojiShortCode(emojis.group());
        BrailleWord correctBrailleWord = new BrailleWord();
        correctBrailleWord.append(BrailleCharacter.EMPTY_CELL);
        correctBrailleWord.append(
            translator.translate(replacement, /* cursorPosition= */ -1).cells());
        result =
            TranslationResult.correctTranslation(
                result, correctBrailleWord, emojis.start(), emojis.end());
      }
    }
    return result;
  }
}
