package com.google.android.accessibility.braille.common.translate;

import static com.google.android.accessibility.braille.common.ImeConnection.AnnounceType.NORMAL;
import static com.google.android.accessibility.braille.common.translate.BrailleTranslateUtils.PASSWORD_BULLET;
import static com.google.android.accessibility.braille.common.translate.EditBufferUtils.NO_CURSOR;
import static com.google.common.base.Strings.nullToEmpty;

import android.content.Context;
import android.content.res.Resources;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.inputmethod.ExtractedText;
import android.view.inputmethod.ExtractedTextRequest;
import android.view.inputmethod.InputConnection;
import com.google.android.accessibility.braille.common.BrailleCommonUtils;
import com.google.android.accessibility.braille.common.ImeConnection;
import com.google.android.accessibility.braille.common.R;
import com.google.android.accessibility.braille.common.TalkBackSpeaker;
import com.google.android.accessibility.braille.interfaces.BrailleCharacter;
import com.google.android.accessibility.braille.interfaces.BrailleDisplayForBrailleIme.ResultForDisplay.HoldingsInfo;
import com.google.android.accessibility.braille.interfaces.BrailleWord;
import com.google.android.accessibility.braille.translate.BrailleTranslator;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

/** Basement abstraction class for contracted braille languages. */
public abstract class EditBufferContracted implements EditBuffer {
  private static final String TAG = "EditBufferCommonContracted";
  private static final String DELIMITER = ",";
  private static final String NEW_LINE = "\n";
  private static final int DELETE_WORD_MAX = 50;
  private final Context context;
  private final BrailleTranslator translator;
  private final BrailleWord holdings = new BrailleWord();
  private final TalkBackSpeaker talkBack;
  /**
   * Mapping for initial braille characters. Defines the single braille character translation
   * without any context or relationship with other braille characters.
   */
  private final Map<String, String> initialCharacterTranslationMap = new HashMap<>();
  /**
   * Mapping for non-initial braille characters. Defines the single braille character translation
   * that place not at the word's beginning.
   */
  private final Map<String, String> nonInitialCharacterTranslationMap = new HashMap<>();

  private int holdingPosition = NO_CURSOR;

  public EditBufferContracted(
      Context context, BrailleTranslator translator, TalkBackSpeaker talkBack) {
    this.context = context;
    this.translator = translator;
    this.talkBack = talkBack;
    fillTranslatorMaps(initialCharacterTranslationMap, nonInitialCharacterTranslationMap);
  }

  @Override
  public String appendBraille(ImeConnection imeConnection, BrailleCharacter brailleCharacter) {
    CharSequence selectedText = imeConnection.inputConnection.getSelectedText(0);
    if (!TextUtils.isEmpty(selectedText)) {
      // Delete selection first.
      imeConnection.inputConnection.commitText("", 1);
    }
    updateHoldingsPosition(brailleCharacter);
    int previousTranslationIndex = holdingPosition - 1;
    String result =
        getAnnouncement(context.getResources(), translator, holdings, previousTranslationIndex);
    if (EditBufferUtils.shouldEmitPerCharacterFeedback(imeConnection)) {
      result =
          hideTextForPasswordIfNecessary(imeConnection, result, /* brailleCharacterLength= */ 1);
      EditBufferUtils.speak(context, talkBack, result);
    }
    return result;
  }

  @Override
  public void appendSpace(ImeConnection imeConnection) {
    updateHoldingsPosition(new BrailleCharacter());
    clearHoldingsAndSendToEditor(imeConnection, /* ignoreHoldingsPosition= */ false);
  }

  @Override
  public void appendNewline(ImeConnection imeConnection) {
    if (EditBufferUtils.isMultiLineField(imeConnection.editorInfo.inputType)) {
      clearHoldingsAndSendToEditor(imeConnection, /* ignoreHoldingsPosition= */ false, NEW_LINE);
    } else {
      EditBufferUtils.speak(context, talkBack, context.getString(R.string.new_line_not_supported));
    }
  }

  @Override
  public void deleteCharacterBackward(ImeConnection imeConnection) {
    if (holdings.isEmpty()) {
      BrailleCommonUtils.performKeyAction(imeConnection.inputConnection, KeyEvent.KEYCODE_DEL);
      return;
    }
    if (holdingPosition <= 0) {
      return;
    }
    holdingPosition--;
    String result = getAnnouncement(context.getResources(), translator, holdings, holdingPosition);
    holdings.remove(holdingPosition);
    if (holdings.isEmpty()) {
      holdingPosition = NO_CURSOR;
    }
    result = hideTextForPasswordIfNecessary(imeConnection, result, /* brailleCharacterLength= */ 1);
    EditBufferUtils.speakDelete(context, talkBack, result);
  }

  @Override
  public void deleteCharacterForward(ImeConnection imeConnection) {
    if (holdings.isEmpty()) {
      BrailleCommonUtils.performKeyAction(
          imeConnection.inputConnection, KeyEvent.KEYCODE_FORWARD_DEL);
      return;
    }
    if (holdingPosition >= holdings.size()) {
      return;
    }
    String result = getAnnouncement(context.getResources(), translator, holdings, holdingPosition);
    holdings.remove(holdingPosition);
    if (holdings.isEmpty()) {
      holdingPosition = NO_CURSOR;
    }
    result = hideTextForPasswordIfNecessary(imeConnection, result, /* brailleCharacterLength= */ 1);
    EditBufferUtils.speakDelete(context, talkBack, result);
  }

  @Override
  public void deleteWord(ImeConnection imeConnection) {
    // If there is any holdings left, clear it out; otherwise delete at the Editor level.
    if (!holdings.isEmpty()) {
      ImmutableList.Builder<String> holdingsStringBuilder = ImmutableList.builder();
      for (int i = 0; i < holdings.size(); i++) {
        holdingsStringBuilder.add(getAnnouncement(context.getResources(), translator, holdings, i));
      }
      String deletedWord = TextUtils.join(DELIMITER, holdingsStringBuilder.build());
      deletedWord = hideTextForPasswordIfNecessary(imeConnection, deletedWord, holdings.size());
      EditBufferUtils.speakDelete(context, talkBack, deletedWord);
      holdingPosition = NO_CURSOR;
      holdings.clear();
      imeConnection.inputConnection.setComposingText("", 0);
    } else {
      CharSequence hunkBeforeCursor =
          imeConnection.inputConnection.getTextBeforeCursor(DELETE_WORD_MAX, 0);
      int charactersToDeleteCount =
          BrailleCommonUtils.getLastWordLengthForDeletion(hunkBeforeCursor);
      if (charactersToDeleteCount > 0) {
        imeConnection.inputConnection.deleteSurroundingText(charactersToDeleteCount, 0);
      }
    }
  }

  @Override
  public void commit(ImeConnection imeConnection) {
    clearHoldingsAndSendToEditor(imeConnection, /* ignoreHoldingsPosition= */ true);
  }

  @Override
  public boolean moveCursorForward(ImeConnection imeConnection) {
    if (holdings.isEmpty()) {
      return false;
    }
    return moveHoldingsCursor(imeConnection, holdingPosition + 1);
  }

  @Override
  public boolean moveCursorBackward(ImeConnection imeConnection) {
    if (holdings.isEmpty()) {
      return false;
    }
    return moveHoldingsCursor(imeConnection, holdingPosition - 1);
  }

  @Override
  public boolean moveHoldingsCursor(ImeConnection imeConnection, int index) {
    if (0 <= index && index <= holdings.size()) {
      int start = holdingPosition;
      int end = index;
      if (holdingPosition > index) {
        start = index;
        end = holdingPosition;
      }
      holdingPosition = index;
      String announcement =
          getAnnouncement(context.getResources(), translator, holdings, start, end);
      announcement =
          hideTextForPasswordIfNecessary(
              imeConnection, announcement, /* brailleCharacterLength= */ end - start);
      EditBufferUtils.speak(context, talkBack, announcement);
    } else {
      int oldPosition = EditBufferUtils.getCursorPosition(imeConnection.inputConnection);
      commit(imeConnection);
      if (index < 0) {
        return imeConnection.inputConnection.setSelection(oldPosition, oldPosition);
      }
    }
    return true;
  }

  @Override
  public boolean moveTextFieldCursor(ImeConnection imeConnection, int index) {
    if (0 <= index
        && index <= EditBufferUtils.getTextFieldText(imeConnection.inputConnection).length()) {
      return imeConnection.inputConnection.setSelection(index, index);
    }
    return false;
  }

  @Override
  public boolean moveCursorToBeginning(ImeConnection imeConnection) {
    commit(imeConnection);
    return imeConnection.inputConnection.setSelection(0, 0);
  }

  @Override
  public boolean moveCursorToEnd(ImeConnection imeConnection) {
    commit(imeConnection);
    int end = EditBufferUtils.getTextFieldText(imeConnection.inputConnection).length();
    return imeConnection.inputConnection.setSelection(end, end);
  }

  @Override
  public boolean selectAllText(ImeConnection imeConnection) {
    if (!holdings.isEmpty()) {
      commit(imeConnection);
    }
    String textFieldText = EditBufferUtils.getTextFieldText(imeConnection.inputConnection);
    boolean result = imeConnection.inputConnection.setSelection(0, textFieldText.length());
    if (result) {
      EditBufferUtils.speakSelectAll(context, talkBack, textFieldText);
      return true;
    }
    return false;
  }

  @Override
  public HoldingsInfo getHoldingsInfo(ImeConnection imeConnection) {
    return HoldingsInfo.create(ByteBuffer.wrap(holdings.toByteArray()), holdingPosition);
  }

  // TODO: Improve the design.
  /**
   * Fills {@link EditBufferContracted#initialCharacterTranslationMap} and {@link
   * EditBufferContracted#nonInitialCharacterTranslationMap}.
   */
  protected abstract void fillTranslatorMaps(
      Map<String, String> initialCharacterTranslationMap,
      Map<String, String> nonInitialCharacterTranslationMap);

  /** Whether the character is a letter. */
  protected abstract boolean isLetter(char character);

  /** Gets capitalize indicator braille character. */
  protected abstract BrailleCharacter getCapitalize();

  /** Gets numeric indicator braille character. */
  protected abstract BrailleCharacter getNumeric();

  /** Whether the initial braille character should be forced to announce the dots. */
  protected abstract boolean forceInitialDefaultTranslation(String dotsNumber);

  /** Whether the non-initial braille character should be forced to announce the dots. */
  protected abstract boolean forceNonInitialDefaultTranslation(String dotsNumber);

  private void updateHoldingsPosition(BrailleCharacter brailleCharacter) {
    if (holdingPosition == NO_CURSOR || holdingPosition == holdings.size()) {
      holdings.append(brailleCharacter);
      holdingPosition = holdings.size();
    } else {
      holdings.insert(holdingPosition, brailleCharacter);
      holdingPosition++;
    }
  }

  private void clearHoldingsAndSendToEditor(
      ImeConnection imeConnection, boolean ignoreHoldingsPosition) {
    clearHoldingsAndSendToEditor(imeConnection, ignoreHoldingsPosition, "");
  }

  private void clearHoldingsAndSendToEditor(
      ImeConnection imeConnection, boolean ignoreHoldingsPosition, String appendix) {
    if (holdings.isEmpty()) {
      if (!TextUtils.isEmpty(appendix)) {
        imeConnection.inputConnection.commitText(appendix, /* newCursorPosition= */ 1);
      }
      return;
    }
    if (ignoreHoldingsPosition) {
      holdingPosition = holdings.size();
    }
    String holdingsBeforeCursor = translator.translateToPrint(holdings.subword(0, holdingPosition));
    String holdingsAfterCursor =
        translator.translateToPrint(holdings.subword(holdingPosition, holdings.size()));

    int textCursorStartIndex = getTextCursorStartIndex(imeConnection.inputConnection);

    imeConnection.inputConnection.commitText(
        holdingsBeforeCursor + appendix + holdingsAfterCursor, /* newCursorPosition= */ 1);

    boolean unused =
        moveTextFieldCursor(
            imeConnection,
            holdingsBeforeCursor.length() + textCursorStartIndex + appendix.length());

    holdings.clear();
    holdingPosition = NO_CURSOR;
  }

  private int getTextCursorStartIndex(InputConnection inputConnection) {
    ExtractedText extractedText = inputConnection.getExtractedText(new ExtractedTextRequest(), 0);
    CharSequence textLengthBeforeCursor = "";
    if (extractedText != null && extractedText.text != null && extractedText.selectionStart >= 0) {
      textLengthBeforeCursor = extractedText.text.subSequence(0, extractedText.selectionStart);
    }
    return TextUtils.isEmpty(textLengthBeforeCursor) ? 0 : textLengthBeforeCursor.length();
  }

  private String getAnnouncement(
      Resources resources,
      BrailleTranslator translator,
      BrailleWord brailleWord,
      int startIndex,
      int endIndex) {
    StringBuilder sb = new StringBuilder();
    for (int i = startIndex; i < endIndex; i++) {
      sb.append(getAnnouncement(resources, translator, brailleWord, i));
    }
    return sb.toString();
  }

  private String getAnnouncement(
      Resources resources, BrailleTranslator translator, BrailleWord brailleWord, int index) {
    BrailleCharacter brailleCharacter = brailleWord.get(index);
    String result = getNonInitialCharacterTranslation(resources, brailleCharacter);
    if (index == 0) {
      result = getInitialCharacterTranslation(resources, brailleCharacter);
    }
    if (result.isEmpty()) {
      result = getTranslateDifference(translator, brailleWord, index, index + 1);
      if (result.isEmpty() || isLetter(result.charAt(0))) {
        result = getCharacterTranslation(resources, brailleCharacter, brailleWord.size() > 1);
      }
    }
    return result;
  }

  private String getInitialCharacterTranslation(
      Resources resources, BrailleCharacter brailleCharacter) {
    String translation = getTextToSpeak(resources, brailleCharacter);
    if (forceInitialDefaultTranslation(brailleCharacter.toString())) {
      translation = BrailleTranslateUtils.getDotsText(resources, brailleCharacter);
    }
    if (TextUtils.isEmpty(translation)) {
      translation = nullToEmpty(initialCharacterTranslationMap.get(brailleCharacter.toString()));
      if (TextUtils.isEmpty(translation)) {
        translation = BrailleTranslateUtils.getDotsText(resources, brailleCharacter);
      }
    }
    return translation;
  }

  private String getNonInitialCharacterTranslation(
      Resources resources, BrailleCharacter brailleCharacter) {
    String translation = getTextToSpeak(resources, brailleCharacter);
    if (forceNonInitialDefaultTranslation(brailleCharacter.toString())) {
      translation = BrailleTranslateUtils.getDotsText(resources, brailleCharacter);
    }
    if (TextUtils.isEmpty(translation)) {
      translation = nullToEmpty(nonInitialCharacterTranslationMap.get(brailleCharacter.toString()));
    }
    return translation;
  }

  private String getTextToSpeak(Resources resources, BrailleCharacter brailleCharacter) {
    if (brailleCharacter.equals(getCapitalize())) {
      return resources.getString(R.string.capitalize_announcement);
    } else if (brailleCharacter.equals(getNumeric())) {
      return resources.getString(R.string.number_announcement);
    }
    return "";
  }

  private String hideTextForPasswordIfNecessary(
      ImeConnection imeConnection, String text, int brailleCharacterLength) {
    if (imeConnection.announceType == NORMAL
        || !BrailleCommonUtils.isPasswordField(imeConnection.editorInfo)) {
      return text;
    }
    return Strings.repeat(PASSWORD_BULLET, brailleCharacterLength);
  }

  /**
   * Gets the proper translation for single braille character. For example, dot 16 can be "child" or
   * "ch", it's better to get "ch" in most use experience. Returns {@link
   * EditBufferContracted#initialCharacterTranslationMap} of there has other holdings; otherwise
   * {@link EditBufferContracted#nonInitialCharacterTranslationMap}.
   */
  private String getCharacterTranslation(
      Resources resources, BrailleCharacter brailleCharacter, boolean hasOtherHoldings) {
    return hasOtherHoldings
        ? getInitialCharacterTranslation(resources, brailleCharacter)
        : getNonInitialCharacterTranslation(resources, brailleCharacter);
  }

  private static String getTranslateDifference(
      BrailleTranslator translator, BrailleWord brailleWord, int firstIndex, int secondIndex) {
    String longerString = translator.translateToPrint(brailleWord.subword(0, secondIndex));
    String shorterString = translator.translateToPrint(brailleWord.subword(0, firstIndex));
    if (longerString.startsWith(shorterString)) {
      return longerString.substring(shorterString.length());
    }
    return "";
  }
}
