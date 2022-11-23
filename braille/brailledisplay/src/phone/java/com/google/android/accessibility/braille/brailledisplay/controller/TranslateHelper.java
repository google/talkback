package com.google.android.accessibility.braille.brailledisplay.controller;

import static java.util.Arrays.stream;

import android.text.Spanned;
import androidx.annotation.NonNull;
import com.google.android.accessibility.braille.brailledisplay.BrailleDisplayLog;
import com.google.android.accessibility.braille.brailledisplay.controller.DisplaySpans.BrailleSpan;
import com.google.android.accessibility.braille.interfaces.BrailleCharacter;
import com.google.android.accessibility.braille.interfaces.BrailleWord;
import com.google.android.accessibility.braille.translate.BrailleTranslator;
import com.google.android.accessibility.braille.translate.TranslationResult;
import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/** Helps to translate content in CellsContentManager. */
public final class TranslateHelper {
  private static final String TAG = "TranslateHelper";
  private static final char NEW_LINE = '\n';

  public static TranslationResult translateCurrentContent(
      BrailleTranslator translator, int cursorPositionToTranslate, CharSequence text) {
    TranslationResult currentTranslationResult =
        translateWithVerbatimBraille(text, translator, cursorPositionToTranslate);
    // Make very sure we do not call cells() on a null translation.
    // translateWithVerbatimBraille() currently should never return null.
    if (currentTranslationResult == null) {
      BrailleDisplayLog.e(TAG, "currentTranslationResult is null");
      currentTranslationResult = createDummyTranslation(text);
    }
    return currentTranslationResult;
  }

  private static TranslationResult createDummyTranslation(CharSequence text) {
    int textLength = (text == null) ? 0 : text.length();
    return TranslationResult.builder()
        .setText(text)
        .setCells(new BrailleWord())
        .setTextToBraillePositions(stream(new int[textLength]).boxed().collect(Collectors.toList()))
        .setBrailleToTextPositions(stream(new int[0]).boxed().collect(Collectors.toList()))
        .setCursorBytePosition(0)
        .build();
  }

  /**
   * Translates the text content, preserving any verbatim braille that is embedded in a BrailleSpan.
   * The current implementation of this method only handles the first BrailleSpan; all subsequent
   * BrailleSpans are ignored.
   *
   * @param translator The translator used for translating the subparts of the text without embedded
   *     BrailleSpans.
   * @param cursorPosition The position of the cursor; if it occurs in a section of the text without
   *     BrailleSpans, then the final cursor position in the output braille by the translator.
   *     Otherwise, if the cursor occurs within a BrailleSpan section, the final cursor position in
   *     the output braille is set to the first braille cell of the BrailleSpan.
   * @return The result of translation, possibly empty, not null.
   */
  public static TranslationResult translateWithVerbatimBraille(
      CharSequence text, BrailleTranslator translator, int cursorPosition) {
    if (translator == null) {
      return createDummyTranslation(text);
    }

    // Assume that we have at most one BrailleSpan since we currently
    // never add more than one BrailleSpan.
    // Also ignore BrailleSpans with zero-length span or no braille for
    // now because we don't currently add such BrailleSpans.
    BrailleSpan brailleSpan = null;
    int start = -1;
    int end = -1;
    if (text instanceof Spanned) {
      Spanned spanned = (Spanned) text;
      BrailleSpan[] spans = spanned.getSpans(0, spanned.length(), BrailleSpan.class);
      if (spans.length > 1) {
        BrailleDisplayLog.v(TAG, "More than one BrailleSpan, handling first only");
      }
      if (spans.length != 0) {
        int spanStart = spanned.getSpanStart(spans[0]);
        int spanEnd = spanned.getSpanEnd(spans[0]);
        if (spans[0].braille != null && spans[0].braille.length != 0 && spanStart < spanEnd) {
          brailleSpan = spans[0];
          start = spanStart;
          end = spanEnd;
        }
      }
    }

    if (brailleSpan != null) {
      // Chunk the text into three sections:
      // left: [0, start) - needs translation
      // mid: [start, end) - use the literal braille provided
      // right: [end, length) - needs translation
      CharSequence left = text.subSequence(0, start);
      TranslationResult leftTrans =
          translator.translate(left, cursorPosition < start ? cursorPosition : -1, false);

      CharSequence right = text.subSequence(end, text.length());
      TranslationResult rightTrans =
          translator.translate(right, cursorPosition >= end ? cursorPosition - end : -1, false);

      // If one of the left or right translations is not valid, then
      // we will fall back by ignoring the BrailleSpan and
      // translating everything normally. (Chances are that
      // translating the whole text will fail also, but it wouldn't
      // hurt to try.)
      if (leftTrans == null || rightTrans == null) {
        BrailleDisplayLog.e(
            TAG,
            "Could not translate left or right subtranslation, "
                + "falling back on default translation");
        return translateOrDefault(text, translator, cursorPosition, false);
      }

      int startBraille = leftTrans.cells().size();
      int endBraille = startBraille + brailleSpan.braille.length;
      int totalBraille = endBraille + rightTrans.cells().size();

      // Copy braille cells.
      BrailleWord cells = new BrailleWord(leftTrans.cells());
      cells.append(new BrailleWord(brailleSpan.braille));
      cells.append(new BrailleWord(rightTrans.cells()));

      // Copy text-to-braille indices.
      ImmutableList<Integer> leftTtb = leftTrans.textToBraillePositions();
      ImmutableList<Integer> rightTtb = rightTrans.textToBraillePositions();
      List<Integer> textToBraille = new ArrayList<>(text.length());

      textToBraille.addAll(leftTtb.subList(0, start));
      for (int i = start; i < end; ++i) {
        textToBraille.add(startBraille);
      }
      for (int i = end; i < text.length(); ++i) {
        textToBraille.add(endBraille + rightTtb.get(i - end));
      }

      // Copy braille-to-text indices.
      ImmutableList<Integer> leftBtt = leftTrans.brailleToTextPositions();
      ImmutableList<Integer> rightBtt = rightTrans.brailleToTextPositions();
      List<Integer> brailleToText = new ArrayList<>(totalBraille);

      brailleToText.addAll(leftBtt.subList(0, startBraille));
      for (int i = startBraille; i < endBraille; ++i) {
        brailleToText.add(start);
      }
      for (int i = endBraille; i < totalBraille; ++i) {
        brailleToText.add(end + rightBtt.get(i - endBraille));
      }

      // Get cursor.
      int cursor;
      if (cursorPosition < 0) {
        cursor = -1;
      } else if (cursorPosition < start) {
        cursor = leftTrans.cursorBytePosition();
      } else if (cursorPosition < end) {
        cursor = startBraille;
      } else {
        cursor = endBraille + rightTrans.cursorBytePosition();
      }
      return TranslationResult.builder()
          .setText(text)
          .setCells(cells)
          .setTextToBraillePositions(textToBraille)
          .setBrailleToTextPositions(brailleToText)
          .setCursorBytePosition(cursor)
          .build();
    }
    return translateOrDefault(text, translator, cursorPosition, false);
  }

  private static TranslationResult translateOrDefault(
      CharSequence text,
      @NonNull BrailleTranslator translator,
      int cursorPosition,
      boolean computerBrailleAtCursor) {
    TranslationResult translation =
        translator.translate(text, cursorPosition, computerBrailleAtCursor);
    if (isNewLineBlank(translator)) {
      translation = replaceNewLine(translation);
    }
    return translation != null ? translation : createDummyTranslation(text);
  }

  /** Whether new line is translated into a Space. */
  public static boolean isNewLineBlank(BrailleTranslator translator) {
    TranslationResult result =
        translator.translate(Character.toString(NEW_LINE), 0, /* computerBrailleAtCursor= */ false);
    if (result != null) {
      return result.cells().equals(new BrailleWord(new BrailleCharacter()));
    }
    return false;
  }

  /** Replaces new line with 2346-123. */
  public static TranslationResult replaceNewLine(TranslationResult result) {
    List<Integer> textToBraille = new ArrayList<>(result.textToBraillePositions());
    List<Integer> brailleToText = new ArrayList<>(result.brailleToTextPositions());
    BrailleWord cells = new BrailleWord(result.cells());
    for (int index = 0; index < result.text().length(); index++) {
      if (result.text().charAt(index) != NEW_LINE) {
        continue;
      }
      int newLineByte = textToBraille.get(index);
      cells.set(newLineByte, BrailleWord.NEW_LINE);
      for (int i = index + 1; i < textToBraille.size(); i++) {
        textToBraille.set(i, textToBraille.get(i) + BrailleWord.NEW_LINE.size() - 1);
      }
      for (int i = 1; i < BrailleWord.NEW_LINE.size(); i++) {
        brailleToText.add(newLineByte + i, brailleToText.get(newLineByte));
      }
    }
    return TranslationResult.builder()
        .setCells(cells)
        .setBrailleToTextPositions(brailleToText)
        .setTextToBraillePositions(textToBraille)
        .setText(result.text())
        .setCursorBytePosition(result.cursorBytePosition())
        .build();
  }

  private TranslateHelper() {}
}
