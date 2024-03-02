package com.google.android.accessibility.braille.brailledisplay.controller;

import static com.google.android.accessibility.braille.common.translate.BrailleTranslateUtils.PASSWORD_BULLET;
import static com.google.android.accessibility.braille.common.translate.EditBufferUtils.NO_CURSOR;
import static com.google.android.accessibility.braille.interfaces.BrailleCharacter.EMPTY_CELL;

import android.text.TextUtils;
import android.util.Range;
import com.google.android.accessibility.braille.brailledisplay.BrailleDisplayLog;
import com.google.android.accessibility.braille.interfaces.BrailleDisplayForBrailleIme.ResultForDisplay;
import com.google.android.accessibility.braille.interfaces.BrailleWord;
import com.google.android.accessibility.braille.interfaces.SelectionRange;
import com.google.android.accessibility.braille.translate.BrailleTranslator;
import com.google.android.accessibility.braille.translate.TranslationResult;
import com.google.auto.value.AutoValue;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Assembles info of text field into the format:
 *
 * <p>{$hint}: {$text in the text field and holdings} [{$action}]
 *
 * <p>For example, if hint is "Name", action is "Done" and user types 1-12-14 (holdings). We will
 * show: 6-5-1345(Name)-25(:)-0(space)-1-12-14(abc)-78(cursor)-0(space)-46-126(left square
 * bracket)-6-145(D)-5-135(one)-46-345(right square bracket).
 *
 * <ul>
 *   The formatted result will stored in {@link AssembledResult} with following information:
 *   <li>textFieldTextClickableByteRange is empty.
 *   <li>blinkingCursorByteRange is 8-8
 *   <li>holdingsClickableByteRange is 5-8
 *   <li>overlayText is "Name: [Done]"
 *   <li>overlayTranslationResult is
 *       <ul>
 *         <li>byte array: 6-5-1345-25-0-1-12-14-78-0-46-126-6-145-5-135-46-345.
 *         <li>textToBraillePositions: [0, 0, 0, 0, 3, 4, 5, 6, 7, 8, 9, 10, 12, 14, 14, 14, 16]
 *         <li>brailleToTextPositions: [0, 0, 0, 4, 5, 6, 7, 8, 9, 10, 11, 11, 12, 12, 13, 13, 16,
 *             16]
 *         <li>cursorPosition: 6
 *       </ul>
 * </ul>
 *
 * If 1-12-14 (holdings) is committed, the formatted result will be as following:
 *
 * <ul>
 *   <li>textFieldTextClickableByteRange is 5-8.
 *   <li>blinkingCursorByteRange is 8-8
 *   <li>holdingsClickableByteRange is empty
 *   <li>overlayText is "Name: abc&nbsp;&nbsp;[Done]"
 *   <li>overlayTranslationResult is
 *       <ul>
 *         <li>byte array: 6-5-1345-25-0-1-12-14-78-0-46-126-6-145-5-135-46-345.
 *         <li>textToBraillePositions: [0, 0, 0, 0, 3, 4, 5, 6, 7, 8, 9, 10, 12, 14, 14, 14, 16]
 *         <li>brailleToTextPositions: [0, 0, 0, 4, 5, 6, 7, 8, 9, 10, 11, 11, 12, 12, 13, 13, 16,
 *             16]
 *         <li>cursorPosition: 9
 *       </ul>
 * </ul>
 *
 * If user enter dot 14 between a and b, the formatted result will be as following:
 *
 * <ul>
 *   <li>textFieldTextClickableByteRange is 5-5, 7-9
 *   <li>blinkingCursorByteRange is 7-7
 *   <li>holdingsClickableByteRange is 6-7
 *   <li>overlayText is "Name: a bc [Done]"
 *   <li>overlayTranslationResult is
 *       <ul>
 *         <li>byte array: 6-5-1345-25-0-1-14-1278-14-0-46-126-6-145-5-135-46-345.
 *         <li>textToBraillePositions: [0, 0, 0, 0, 3, 4, 5, 6, 7, 8, 9, 10, 12, 14, 14, 14, 16]
 *         <li>brailleToTextPositions: [0, 0, 0, 4, 5, 6, 7, 8, 9, 10, 11, 11, 12, 12, 13, 13, 16,
 *             16]
 *         <li>cursorPosition: 7
 *       </ul>
 * </ul>
 */
@AutoValue
abstract class AssembledResult {
  /** The index of the selected text in the byte array. */
  abstract SelectionRange textByteSelection();
  /** The range of clickable index of text in the text field in the byte array. */
  abstract ImmutableList<Range<Integer>> textFieldTextClickableByteRange();
  /** The range of clickable index of holdings in the byte array. */
  abstract Range<Integer> holdingsClickableByteRange();
  /** The range of clickable index of action in the byte array. */
  abstract Range<Integer> actionClickableByteRange();
  /** TranslationResult of overlayText. */
  abstract TranslationResult overlayTranslationResult();
  /** Builder class for {@link AssembledResult}. */
  static class Builder {
    private static final String TAG = "AssembledResultBuilder";
    private static final String SPACE = " ";
    private static final String LEFT_SQUARE_BRACKET = "[";
    private static final String RIGHT_SQUARE_BRACKET = "]";
    private static final String COLON = ":";
    private final StringBuilder textOnOverlay = new StringBuilder();
    private final BrailleWord brailleWord = new BrailleWord();
    private final List<Range<Integer>> textFieldTextClickableByteRange = new ArrayList<>();
    private final List<Integer> textToBraillePositions = new ArrayList<>();
    private final List<Integer> brailleToTextPositions = new ArrayList<>();
    private final ResultForDisplay resultForDisplay;
    private final BrailleTranslator translator;
    private final String textFieldText;
    private final Range<Integer> textSelectionRange;
    private final int holdingsPosition;
    private final BrailleWord holdingsWord;
    private final BrailleWord textFieldWord;
    private final ImmutableList<Integer> textFieldTextToBraillePositions;
    private final ImmutableList<Integer> textFieldTextBrailleToTextPositions;
    private final boolean showPassword;
    private Range<Integer> holdingsClickableByteRange;
    private Range<Integer> actionClickableByteRange;
    private int textDisplacement;
    private boolean appendHint;
    private boolean appendAction;

    public Builder(BrailleTranslator translator, ResultForDisplay resultForDisplay) {
      this.translator = translator;
      this.resultForDisplay = resultForDisplay;
      this.showPassword = resultForDisplay.showPassword();
      BrailleWord holdingsWord =
          new BrailleWord(resultForDisplay.holdingsInfo().holdings().array());
      int holdingsPosition = resultForDisplay.holdingsInfo().position();
      String textFieldText = String.valueOf(resultForDisplay.onScreenText());
      textSelectionRange =
          new Range<>(
              resultForDisplay.textSelection().getLower(),
              resultForDisplay.textSelection().getUpper());
      if (showPassword) {
        BrailleWord bullet = translateIfPossible(PASSWORD_BULLET, NO_CURSOR).cells();
        this.holdingsWord = new BrailleWord(bullet, holdingsWord.size());
        this.holdingsPosition = bullet.size() * holdingsPosition;
        this.textFieldText = Strings.repeat(PASSWORD_BULLET, textFieldText.length());
      } else {
        this.holdingsWord = holdingsWord;
        this.holdingsPosition = holdingsPosition;
        this.textFieldText = textFieldText;
      }
      TranslationResult translationResult =
          translateIfPossible(this.textFieldText, this.textSelectionRange.getLower());
      textFieldWord = new BrailleWord(translationResult.cells());
      textFieldTextToBraillePositions = translationResult.textToBraillePositions();
      textFieldTextBrailleToTextPositions = translationResult.brailleToTextPositions();
      holdingsClickableByteRange = new Range<>(NO_CURSOR, NO_CURSOR);
      actionClickableByteRange = new Range<>(NO_CURSOR, NO_CURSOR);
    }

    /** Whether to append hint. */
    @CanIgnoreReturnValue
    public Builder appendHint(boolean append) {
      appendHint = append;
      return this;
    }

    private void doAppendHint() throws IOException {
      if (TextUtils.isEmpty(resultForDisplay.hint())) {
        return;
      }
      StringBuilder hint =
          new StringBuilder().append(resultForDisplay.hint()).append(COLON).append(SPACE);
      TranslationResult result = translateIfPossible(hint, NO_CURSOR);
      textOnOverlay.append(hint);
      brailleWord.append(new BrailleWord(result.cells()));
      appendPositionsToList(
          textToBraillePositions,
          /* base= */ 0,
          /* startIndexToAppend= */ 0,
          /* endIndexToAppend= */ result.textToBraillePositions().size(),
          /* positionsToCopy= */ result.textToBraillePositions());
      appendPositionsToList(
          brailleToTextPositions,
          /* base= */ 0,
          /* startIndexToAppend= */ 0,
          /* endIndexToAppend= */ result.brailleToTextPositions().size(),
          /* positionsToCopy= */ result.brailleToTextPositions());
      // user input is put after hint.
      textDisplacement += hint.length();
    }
    /** Whether to append action. */
    @CanIgnoreReturnValue
    public Builder appendAction(boolean append) {
      appendAction = append;
      return this;
    }

    private void doAppendAction() throws IOException {
      if (TextUtils.isEmpty(resultForDisplay.action())) {
        return;
      }
      // Add a space between input and action.
      appendSpace();
      // Add action.
      StringBuilder action =
          new StringBuilder()
              .append(LEFT_SQUARE_BRACKET)
              .append(resultForDisplay.action())
              .append(RIGHT_SQUARE_BRACKET);
      TranslationResult result = translateIfPossible(action, NO_CURSOR);
      appendPositionsToList(
          textToBraillePositions,
          /* base= */ brailleWord.size(),
          /* startIndexToAppend= */ 0,
          /* endIndexToAppend= */ result.textToBraillePositions().size(),
          /* positionsToCopy= */ result.textToBraillePositions());
      appendPositionsToList(
          brailleToTextPositions,
          /* base= */ textOnOverlay.length(),
          /* startIndexToAppend= */ 0,
          /* endIndexToAppend= */ result.brailleToTextPositions().size(),
          /* positionsToCopy= */ result.brailleToTextPositions());
      textOnOverlay.append(action);
      actionClickableByteRange =
          new Range<>(brailleWord.size(), brailleWord.size() + result.cells().size());
      brailleWord.append(result.cells());
    }

    private TranslationResult translateIfPossible(CharSequence text, int cursorPosition) {
      TranslationResult result = translator.translate(text, cursorPosition);
      if (result == null) {
        result = TranslationResult.createUnknown(text, cursorPosition);
      }
      return result;
    }

    public AssembledResult build() {
      try {
        if (appendHint) {
          doAppendHint();
        }
        appendTextFieldTextBeforeSelection();
        appendTextFieldTextBetweenSelection();
        appendHoldings();
        appendTextFieldTextAfterSelection();
        extendTextFieldTextForCursor();
        if (appendAction) {
          doAppendAction();
        }
      } catch (IOException e) {
        BrailleDisplayLog.w(TAG, "Build result failed: ", e);
      }
      int lowerIndex = textSelectionRange.getLower() + textDisplacement;
      int lowerByteIndex =
          lowerIndex == textOnOverlay.length()
              ? brailleWord.size()
              : textToBraillePositions.get(lowerIndex);
      int upperIndex = textSelectionRange.getUpper() + textDisplacement;
      int upperByteIndex =
          upperIndex == textOnOverlay.length()
              ? brailleWord.size()
              : textToBraillePositions.get(upperIndex);
      TranslationResult allTranslationResult =
          TranslationResult.builder()
              .setText(textOnOverlay.toString())
              .setCells(brailleWord)
              .setTextToBraillePositions(textToBraillePositions)
              .setBrailleToTextPositions(brailleToTextPositions)
              .setCursorBytePosition(lowerByteIndex)
              .build();
      return new AutoValue_AssembledResult(
          new SelectionRange(lowerByteIndex, upperByteIndex),
          ImmutableList.copyOf(textFieldTextClickableByteRange),
          holdingsClickableByteRange,
          actionClickableByteRange,
          allTranslationResult);
    }

    private void appendTextFieldTextBeforeSelection() {
      if (TextUtils.isEmpty(textFieldText) || textSelectionRange.getLower() == 0) {
        return;
      }
      int textFieldByteBeforeCursorLength = textFieldWord.size();
      boolean cursorAtEnd = textSelectionRange.getLower() == NO_CURSOR || cursorInTheEnd();
      int textIndexBeforeCursor =
          textSelectionRange.getLower() == NO_CURSOR
              ? textFieldText.length() - 1
              : textSelectionRange.getLower();
      if (!cursorAtEnd) {
        textFieldByteBeforeCursorLength =
            textFieldTextToBraillePositions.get(textIndexBeforeCursor);
      }
      String textFieldTextBeforeCursor = textFieldText.substring(0, textIndexBeforeCursor);
      BrailleWord textFieldSubwordBeforeCursor =
          textFieldWord.subword(0, textFieldByteBeforeCursorLength);
      textFieldTextClickableByteRange.add(
          new Range<>(
              brailleWord.size(),
              brailleWord.size()
                  + (cursorAtEnd
                      ? textFieldSubwordBeforeCursor.size()
                      : textFieldSubwordBeforeCursor.size() - 1)));
      appendPositionsToList(
          textToBraillePositions,
          /* base= */ brailleWord.size(),
          /* startIndexToAppend= */ 0,
          /* endIndexToAppend= */ textSelectionRange.getLower(),
          /* positionsToCopy= */ textFieldTextToBraillePositions);
      appendPositionsToList(
          brailleToTextPositions,
          /* base= */ textOnOverlay.length(),
          /* startIndexToAppend= */ 0,
          /* endIndexToAppend= */ textFieldByteBeforeCursorLength,
          /* positionsToCopy= */ textFieldTextBrailleToTextPositions);
      brailleWord.append(textFieldSubwordBeforeCursor);
      textOnOverlay.append(textFieldTextBeforeCursor);
    }

    private void appendTextFieldTextBetweenSelection() {
      if (TextUtils.isEmpty(textFieldText) || noSelection()) {
        return;
      }
      int textIndexBeforeCursor =
          textSelectionRange.getUpper() == NO_CURSOR
              ? textFieldText.length() - 1
              : textSelectionRange.getLower();
      int textIndexAfterCursor = textSelectionRange.getUpper();
      String textFieldTextBetweenCursor =
          textFieldText.substring(textIndexBeforeCursor, textIndexAfterCursor);
      int textFieldWordEnd =
          textIndexAfterCursor != textFieldText.length()
              ? textFieldTextToBraillePositions.get(textIndexAfterCursor)
              : textFieldWord.size();
      BrailleWord textFieldSubwordBetweenCursor =
          textFieldWord.subword(
              textFieldTextToBraillePositions.get(textIndexBeforeCursor), textFieldWordEnd);
      appendPositionsToList(
          textToBraillePositions,
          /* base= */ brailleWord.size()
              - textFieldTextToBraillePositions.get(textIndexBeforeCursor),
          /* startIndexToAppend= */ textIndexBeforeCursor,
          /* endIndexToAppend= */ textIndexAfterCursor,
          /* positionsToCopy= */ textFieldTextToBraillePositions);
      appendPositionsToList(
          brailleToTextPositions,
          /* base= */ textOnOverlay.length() - textIndexBeforeCursor,
          /* startIndexToAppend= */ textFieldTextToBraillePositions.get(textIndexBeforeCursor),
          /* endIndexToAppend= */ textFieldWordEnd,
          /* positionsToCopy= */ textFieldTextBrailleToTextPositions);
      textFieldTextClickableByteRange.add(
          new Range<>(
              brailleWord.size(), brailleWord.size() + textFieldSubwordBetweenCursor.size()));
      textOnOverlay.append(textFieldTextBetweenCursor);
      brailleWord.append(textFieldSubwordBetweenCursor);
    }

    private void appendHoldings() {
      if (holdingsWord.isEmpty()) {
        return;
      }
      boolean extendForCursor =
          holdingsWord.size() == holdingsPosition
              && textFieldText.length() == textSelectionRange.getLower();
      holdingsClickableByteRange =
          new Range<>(brailleWord.size(), brailleWord.size() + holdingsWord.size());
      for (int i = 0; i < holdingsWord.size(); i++) {
        textToBraillePositions.add(brailleWord.size());
        brailleToTextPositions.add(textOnOverlay.length());
        // Translate holdings to space.
        textOnOverlay.append(SPACE);
        brailleWord.append(holdingsWord.get(i));
      }
      if (extendForCursor) {
        appendSpace();
      }
      textDisplacement += holdingsPosition;
    }

    private void appendTextFieldTextAfterSelection() {
      if (TextUtils.isEmpty(textFieldText)
          || textSelectionRange.getUpper().intValue() == textFieldText.length()
          || textSelectionRange.getUpper() == NO_CURSOR) {
        return;
      }
      int textAfterCursor = textSelectionRange.getUpper();
      String textFieldTextAfterCursor = textFieldText.substring(textAfterCursor);
      int textFieldWordStart = textFieldTextToBraillePositions.get(textAfterCursor);
      BrailleWord textFieldSubwordAfterCursor =
          textFieldWord.subword(textFieldWordStart, textFieldWord.size());
      appendPositionsToList(
          textToBraillePositions,
          /* base= */ brailleWord.size() - textFieldWordStart,
          /* startIndexToAppend= */ textAfterCursor,
          /* endIndexToAppend= */ textFieldText.length(),
          /* positionsToCopy= */ textFieldTextToBraillePositions);
      appendPositionsToList(
          brailleToTextPositions,
          /* base= */ textOnOverlay.length() - textAfterCursor,
          /* startIndexToAppend= */ textFieldWordStart,
          /* endIndexToAppend= */ textFieldWord.size(),
          /* positionsToCopy= */ textFieldTextBrailleToTextPositions);
      textFieldTextClickableByteRange.add(
          new Range<>(brailleWord.size(), brailleWord.size() + textFieldSubwordAfterCursor.size()));
      textOnOverlay.append(textFieldTextAfterCursor);
      brailleWord.append(textFieldSubwordAfterCursor);
    }

    private void extendTextFieldTextForCursor() {
      boolean extendForCursor =
          TextUtils.isEmpty(resultForDisplay.action())
              || (cursorInTheEnd() && noSelection() && holdingsWord.isEmpty());
      if (extendForCursor) {
        appendSpace();
      }
    }

    private boolean cursorInTheEnd() {
      return textSelectionRange.getLower() == textFieldText.length();
    }

    private boolean noSelection() {
      return textSelectionRange.getLower().intValue() == textSelectionRange.getUpper().intValue();
    }

    private void appendSpace() {
      textToBraillePositions.add(brailleWord.size());
      brailleToTextPositions.add(textOnOverlay.length());
      brailleWord.append(EMPTY_CELL);
      textOnOverlay.append(SPACE);
    }

    private static void appendPositionsToList(
        List<Integer> list,
        int base,
        int startIndexToAppend,
        int endIndexToAppend,
        List<Integer> positionsToCopy) {
      for (int i = startIndexToAppend; i < endIndexToAppend; i++) {
        list.add(base + positionsToCopy.get(i));
      }
    }
  }
}
