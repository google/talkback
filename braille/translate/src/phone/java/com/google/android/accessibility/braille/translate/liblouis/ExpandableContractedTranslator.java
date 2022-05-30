package com.google.android.accessibility.braille.translate.liblouis;

import android.text.TextUtils;
import androidx.annotation.Nullable;
import com.google.android.accessibility.braille.interfaces.BrailleWord;
import com.google.android.accessibility.braille.translate.BrailleTranslator;
import com.google.android.accessibility.braille.translate.TranslationResult;
import com.google.common.base.Splitter;
import java.nio.ByteBuffer;
import java.util.Objects;

/**
 * A contracted translator translates the word the cursor point at in uncontracted.
 *
 * <ul>
 *   <li>If a text field has "a child|", it will be translated into 1-0-16-78.
 *   <li>If a text field has "a chil|d", it will be translated into 1-0-14-125-24-123-14578. The
 *       text will be cut into 3 parts: <br>
 *       1. the string before child "a " <br>
 *       2. the word "child" <br>
 *       3. string after child "" <br>
 *       The translator will translate 1 and 3 in grade 2 and translate 2 in grade 1 and combine all
 *       the result.
 *   <li>If a text field has "a |child", it will be translated into 1-0-1478-125-24-123-145.
 */
public class ExpandableContractedTranslator implements BrailleTranslator {
  private final BrailleTranslator g1Translator;
  private final BrailleTranslator g2Translator;

  public ExpandableContractedTranslator(
      BrailleTranslator g1Translator, BrailleTranslator g2Translator) {
    this.g1Translator = g1Translator;
    this.g2Translator = g2Translator;
  }

  @Override
  public String translateToPrint(BrailleWord brailleWord) {
    return g2Translator.translateToPrint(brailleWord);
  }

  @Override
  public String translateToPrintPartial(BrailleWord brailleWord) {
    return g2Translator.translateToPrintPartial(brailleWord);
  }

  @Override
  public TranslationResult translate(
      String wholeText, int cursorPosition, boolean computerBrailleAtCursor) {
    Iterable<String> words = Splitter.on(' ').split(wholeText);
    int lastSearchIndex = -1;
    for (String wordString : words) {
      if (TextUtils.isEmpty(wordString)) {
        continue;
      }
      int startIndex = wholeText.indexOf(wordString, /* fromIndex= */ lastSearchIndex + 1);
      int endIndex = startIndex + wordString.length();
      lastSearchIndex = endIndex;
      // Check if the cursor is at this word.
      if (startIndex <= cursorPosition && cursorPosition <= endIndex - 1) {
        return getTranslationResult(wholeText, startIndex, endIndex, cursorPosition);
      }
    }
    return g2Translator.translate(wholeText, cursorPosition, /* computerBrailleAtCursor= */ false);
  }

  private TranslationResult getTranslationResult(
      String wholeText, int startIndex, int endIndex, int cursorPosition) {
    String beforeWord = wholeText.substring(0, startIndex);
    String expandableString = wholeText.substring(startIndex, endIndex);
    String afterWord = wholeText.substring(endIndex);
    TranslationResult beforeWordResult =
        g2Translator.translate(
            beforeWord, /* cursorPosition= */ -1, /* computerBrailleAtCursor= */ false);
    TranslationResult expandableWordResult =
        g1Translator.translate(
            expandableString, /* cursorPosition= */ -1, /* computerBrailleAtCursor= */ false);
    TranslationResult afterWordResult =
        g2Translator.translate(
            afterWord, /* cursorPosition= */ -1, /* computerBrailleAtCursor= */ false);
    byte[] beforeWordByteArray = beforeWordResult.getCells();
    byte[] expandableWordArray = expandableWordResult.getCells();
    byte[] afterWordByteArray = afterWordResult.getCells();
    int[] beforeWordTextToBraillePositions = beforeWordResult.getTextToBraillePositions();
    int[] beforeWordBrailleToTextPositions = beforeWordResult.getBrailleToTextPositions();
    int[] expandableWordTextToBraillePositions = expandableWordResult.getTextToBraillePositions();
    int[] expandableWordBrailleToTextPositions = expandableWordResult.getBrailleToTextPositions();
    int[] afterWordTextToBraillePositions = afterWordResult.getTextToBraillePositions();
    int[] afterWordBrailleToTextPositions = afterWordResult.getBrailleToTextPositions();
    byte[] all =
        ByteBuffer.allocate(
                beforeWordByteArray.length + expandableWordArray.length + afterWordByteArray.length)
            .put(beforeWordByteArray)
            .put(expandableWordArray)
            .put(afterWordByteArray)
            .array();
    int[] textToBraille = new int[wholeText.length()];
    int[] brailleToText = new int[all.length];
    // Assign the position of braille byte array to each character in text.
    for (int i = 0; i < textToBraille.length; i++) {
      if (i < beforeWord.length()) {
        textToBraille[i] = beforeWordTextToBraillePositions[i];
      } else if (i < beforeWord.length() + expandableString.length()) {
        textToBraille[i] =
            beforeWordByteArray.length
                + expandableWordTextToBraillePositions[i - beforeWord.length()];
      } else if (i < wholeText.length()) {
        textToBraille[i] =
            beforeWordByteArray.length
                + expandableWordArray.length
                + afterWordTextToBraillePositions[
                    i - beforeWord.length() - expandableString.length()];
      }
    }
    // Assign the position of character in text to each byte in braille byte array.
    for (int i = 0; i < all.length; i++) {
      if (i < beforeWordByteArray.length) {
        brailleToText[i] = beforeWordBrailleToTextPositions[i];
      } else if (i < beforeWordByteArray.length + expandableWordArray.length) {
        brailleToText[i] =
            beforeWord.length()
                + expandableWordBrailleToTextPositions[i - beforeWordByteArray.length];
      } else if (i < all.length) {
        brailleToText[i] =
            beforeWord.length()
                + expandableString.length()
                + afterWordBrailleToTextPositions[
                    i - beforeWordByteArray.length - expandableWordArray.length];
      }
    }
    return new TranslationResult(all, textToBraille, brailleToText, cursorPosition);
  }

  @Override
  public boolean equals(@Nullable Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof ExpandableContractedTranslator)) {
      return false;
    }
    return g1Translator.equals(((ExpandableContractedTranslator) o).g1Translator)
        && g2Translator.equals(((ExpandableContractedTranslator) o).g2Translator);
  }

  @Override
  public int hashCode() {
    return Objects.hash(g1Translator, g2Translator);
  }
}
