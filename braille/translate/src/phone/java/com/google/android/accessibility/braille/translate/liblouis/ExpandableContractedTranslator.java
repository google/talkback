package com.google.android.accessibility.braille.translate.liblouis;

import android.text.TextUtils;
import androidx.annotation.Nullable;
import com.google.android.accessibility.braille.interfaces.BrailleWord;
import com.google.android.accessibility.braille.translate.BrailleTranslator;
import com.google.android.accessibility.braille.translate.TranslationResult;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.List;
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
  public TranslationResult translate(CharSequence wholeText, int cursorPosition) {
    Iterable<String> words = Splitter.on(' ').split(wholeText);
    int lastSearchIndex = -1;
    for (String wordString : words) {
      if (TextUtils.isEmpty(wordString)) {
        continue;
      }
      int startIndex =
          wholeText.toString().indexOf(wordString, /* fromIndex= */ lastSearchIndex + 1);
      int endIndex = startIndex + wordString.length();
      lastSearchIndex = endIndex;
      // Check if the cursor is at this word.
      if (startIndex <= cursorPosition && cursorPosition <= endIndex - 1) {
        return getTranslationResult(wholeText, startIndex, endIndex, cursorPosition);
      }
    }
    return g2Translator.translate(wholeText, cursorPosition);
  }

  private TranslationResult getTranslationResult(
      CharSequence wholeText, int startIndex, int endIndex, int cursorPosition) {
    CharSequence beforeWord = wholeText.subSequence(0, startIndex);
    CharSequence expandableString = wholeText.subSequence(startIndex, endIndex);
    CharSequence afterWord = wholeText.subSequence(endIndex, wholeText.length());
    TranslationResult beforeWordResult =
        g2Translator.translate(beforeWord, /* cursorPosition= */ -1);
    TranslationResult expandableWordResult =
        g1Translator.translate(expandableString, /* cursorPosition= */ -1);
    TranslationResult afterWordResult = g2Translator.translate(afterWord, /* cursorPosition= */ -1);
    BrailleWord beforeWordWord = beforeWordResult.cells();
    BrailleWord expandableWord = expandableWordResult.cells();
    BrailleWord afterWordWord = afterWordResult.cells();
    ImmutableList<Integer> beforeWordTextToBraillePositions =
        beforeWordResult.textToBraillePositions();
    ImmutableList<Integer> beforeWordBrailleToTextPositions =
        beforeWordResult.brailleToTextPositions();
    ImmutableList<Integer> expandableWordTextToBraillePositions =
        expandableWordResult.textToBraillePositions();
    ImmutableList<Integer> expandableWordBrailleToTextPositions =
        expandableWordResult.brailleToTextPositions();
    ImmutableList<Integer> afterWordTextToBraillePositions =
        afterWordResult.textToBraillePositions();
    ImmutableList<Integer> afterWordBrailleToTextPositions =
        afterWordResult.brailleToTextPositions();
    BrailleWord all = new BrailleWord();
    all.append(beforeWordWord);
    all.append(expandableWord);
    all.append(afterWordWord);
    List<Integer> textToBraille = new ArrayList<>(wholeText.length());
    List<Integer> brailleToText = new ArrayList<>(all.size());
    // Assign the position of braille byte array to each character in text.
    for (int i = 0; i < wholeText.length(); i++) {
      if (i < beforeWord.length()) {
        textToBraille.add(beforeWordTextToBraillePositions.get(i));
      } else if (i < beforeWord.length() + expandableString.length()) {
        textToBraille.add(
            beforeWordWord.size()
                + expandableWordTextToBraillePositions.get(i - beforeWord.length()));
      } else if (i < wholeText.length()) {
        textToBraille.add(
            beforeWordWord.size()
                + expandableWord.size()
                + afterWordTextToBraillePositions.get(
                    i - beforeWord.length() - expandableString.length()));
      }
    }
    // Assign the position of character in text to each byte in braille byte array.
    for (int i = 0; i < all.size(); i++) {
      if (i < beforeWordWord.size()) {
        brailleToText.add(beforeWordBrailleToTextPositions.get(i));
      } else if (i < beforeWordWord.size() + expandableWord.size()) {
        brailleToText.add(
            beforeWord.length()
                + expandableWordBrailleToTextPositions.get(i - beforeWordWord.size()));
      } else if (i < all.size()) {
        brailleToText.add(
            beforeWord.length()
                + expandableString.length()
                + afterWordBrailleToTextPositions.get(
                    i - beforeWordWord.size() - expandableWord.size()));
      }
    }
    return TranslationResult.builder()
        .setText(wholeText)
        .setCells(all)
        .setTextToBraillePositions(textToBraille)
        .setBrailleToTextPositions(brailleToText)
        .setCursorBytePosition(cursorPosition)
        .build();
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
