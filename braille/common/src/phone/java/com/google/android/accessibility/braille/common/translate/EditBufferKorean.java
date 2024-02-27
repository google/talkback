package com.google.android.accessibility.braille.common.translate;

import android.content.Context;
import android.text.TextUtils;
import android.view.KeyEvent;
import com.google.android.accessibility.braille.common.BrailleCommonUtils;
import com.google.android.accessibility.braille.common.ImeConnection;
import com.google.android.accessibility.braille.common.TalkBackSpeaker;
import com.google.android.accessibility.braille.interfaces.BrailleCharacter;
import com.google.android.accessibility.braille.interfaces.BrailleWord;
import com.google.android.accessibility.braille.translate.BrailleTranslator;
import com.google.common.collect.ImmutableMap;
import com.google.errorprone.annotations.CanIgnoreReturnValue;

/** An EditBuffer for Korean Braille. */
public class EditBufferKorean extends EditBufferUnContracted {
  private static final String TAG = "EditBufferKorean";
  private final ImmutableMap<String, String> initialConsonantsTranslationMap =
      ImmutableMap.<String, String>builder()
          .put("4", "ᄀ")
          .put("14", "ㄴ")
          .put("24", "ㄷ")
          .put("5", "ㄹ")
          .put("15", "ㅁ")
          .put("45", "ㅂ")
          .put("6", "ㅅ")
          .put("46", "ㅈ")
          .put("56", "ㅊ")
          .put("124", "ㅋ")
          .put("125", "ㅌ")
          .put("145", "ㅍ")
          .put("245", "ㅎ")
          .put("1245", "ㅇ")
          .buildOrThrow();
  private final ImmutableMap<String, String> vowelTranslationMap =
      ImmutableMap.<String, String>builder()
          .put("126", "ᅡ")
          .put("345", "ᅣ")
          .put("234", "ᅥ")
          .put("156", "ᅧ")
          .put("136", "ᅩ")
          .put("346", "ᅭ")
          .put("134", "ᅮ")
          .put("146", "ᅲ")
          .put("246", "ᅳ")
          .put("135", "ᅵ")
          .put("1345", "ᅦ")
          .put("1235", "ᅢ")
          .put("34", "ᅨ")
          .put("2456", "ᅴ")
          .put("1236", "ᅪ")
          .put("1234", "ᅯ")
          .put("13456", "ᅬ")
          .put("345-1235", "ᅤ")
          .put("1236-1235", "ᅫ")
          .put("1234-1235", "ᅰ")
          .put("134-1235", "ᅱ")
          .buildOrThrow();

  private final ImmutableMap<String, String> finalConsonantsTranslationMap =
      ImmutableMap.<String, String>builder()
          .put("1", "ᆨ")
          .put("25", "ᆫ")
          .put("35", "ᆮ")
          .put("2", "ᆯ")
          .put("26", "ᆷ")
          .put("12", "ᆸ")
          .put("3", "ᆺ")
          .put("13", "ᆽ")
          .put("23", "ᆾ")
          .put("235", "ᆿ")
          .put("236", "ᇀ")
          .put("256", "ᇁ")
          .put("356", "ᇂ")
          .put("2356", "ᆼ")
          .buildOrThrow();
  private final ImmutableMap<String, String> speakInitialConsonantsTranslationMap =
      ImmutableMap.<String, String>builder()
          .put("4", "ㄱ")
          .put("14", "ㄴ")
          .put("24", "ㄷ")
          .put("5", "ㄹ")
          .put("15", "ㅁ")
          .put("45", "ㅂ")
          .put("6", "ㅅ")
          .put("46", "ㅈ")
          .put("56", "ㅊ")
          .put("124", "ㅋ")
          .put("125", "ㅌ")
          .put("145", "ㅍ")
          .put("245", "ㅎ")
          .put("1245", "ㅇ")
          .buildOrThrow();
  private final ImmutableMap<String, String> speakVowelTranslationMap =
      ImmutableMap.<String, String>builder()
          .put("126", "ㅏ")
          .put("345", "ㅑ")
          .put("234", "ㅓ")
          .put("156", "ㅕ")
          .put("136", "ㅗ")
          .put("346", "ㅛ")
          .put("134", "ㅜ")
          .put("146", "ㅠ")
          .put("246", "ㅡ")
          .put("135", "ㅣ")
          .put("1345", "ㅔ")
          .put("1235", "ㅐ")
          .put("34", "ㅖ")
          .put("2456", "ㅢ")
          .put("1236", "ㅘ")
          .put("1234", "ㅝ")
          .put("13456", "ㅚ")
          .put("345-1235", "ㅒ")
          .put("1236-1235", "ㅙ")
          .put("1234-1235", "ㅞ")
          .put("134-1235", "ㅟ")
          .buildOrThrow();
  private final ImmutableMap<String, String> speakFinalConsonantsTranslationMap =
      ImmutableMap.<String, String>builder()
          .put("1", "ㄱ")
          .put("25", "ㄴ")
          .put("35", "ㄷ")
          .put("2", "ㄹ")
          .put("26", "ㅁ")
          .put("12", "ㅂ")
          .put("3", "ㅅ")
          .put("13", "ㅈ")
          .put("23", "ㅊ")
          .put("235", "ㅋ")
          .put("236", "ㅌ")
          .put("256", "ㅍ")
          .put("356", "ㅎ")
          .put("2356", "ㅇ")
          .buildOrThrow();
  private final ImmutableMap<String, String> specialOTranslationMap =
      ImmutableMap.<String, String>builder()
          .put("126", "아")
          .put("345", "야")
          .put("234", "어")
          .put("156", "여")
          .put("136", "오")
          .put("346", "요")
          .put("134", "우")
          .put("146", "유")
          .put("246", "으")
          .put("135", "이")
          .put("1235", "애")
          .put("1345", "에")
          .put("345-1235", "얘")
          .put("34", "예")
          .put("1236", "와")
          .put("1236-1235", "왜")
          .put("13456", "외")
          .put("2456", "의")
          .put("1234", "워")
          .put("1234-1235", "웨")
          .put("134-1235", "위")
          .buildOrThrow();
  private final Context context;
  private final TalkBackSpeaker talkBack;
  private final BrailleTranslator translator;

  public EditBufferKorean(
      Context context, BrailleTranslator brailleTranslator, TalkBackSpeaker talkBack) {
    super(context, brailleTranslator, talkBack);
    this.translator = brailleTranslator;
    this.context = context;
    this.talkBack = talkBack;
  }

  @CanIgnoreReturnValue
  @Override
  public String appendBraille(ImeConnection imeConnection, BrailleCharacter brailleCharacter) {
    String previousTranslation = translator.translateToPrint(holdings);
    holdings.append(brailleCharacter);
    String currentTranslation = translator.translateToPrint(holdings);

    String speak = getSpeakableAnnouncement(brailleCharacter);
    talkBack.speak(speak, TalkBackSpeaker.AnnounceType.INTERRUPT_AND_UNINTERRUPTIBLE_BY_NEW_SPEECH);
    String result = getAppendResult(previousTranslation, currentTranslation, brailleCharacter);
    if (!TextUtils.isEmpty(result)) {
      imeConnection.inputConnection.setComposingText(result, result.length());
    }
    lastCommitIndexOfHoldings = holdings.size();
    return speak;
  }

  @Override
  public void deleteCharacterBackward(ImeConnection imeConnection) {
    if (holdings.isEmpty()) {
      BrailleCommonUtils.performKeyAction(imeConnection.inputConnection, KeyEvent.KEYCODE_DEL);
      return;
    }
    BrailleCharacter deleted = holdings.get(holdings.size() - 1);
    holdings.remove(holdings.size() - 1);
    String speak = getSpeakableAnnouncement(deleted);
    talkBack.speak(speak, TalkBackSpeaker.AnnounceType.INTERRUPT_AND_UNINTERRUPTIBLE_BY_NEW_SPEECH);

    if (!holdings.isEmpty()) {
      String previousTranslation =
          translator.translateToPrint(holdings.subword(0, holdings.size() - 1));
      String currentTranslation = translator.translateToPrint(holdings);
      BrailleCharacter b = holdings.get(holdings.size() - 1);
      String result = getAppendResult(previousTranslation, currentTranslation, b);
      if (result != null) {
        imeConnection.inputConnection.setComposingText(result, result.length());
      }
    } else {
      BrailleCommonUtils.performKeyAction(imeConnection.inputConnection, KeyEvent.KEYCODE_DEL);
    }
    lastCommitIndexOfHoldings = holdings.size();
  }

  private String getAppendResult(
      String previousTranslation, String currentTranslation, BrailleCharacter brailleCharacter) {
    if (TextUtils.isEmpty(previousTranslation) && TextUtils.isEmpty(currentTranslation)) {
      return "";
    }
    String result = currentTranslation;
    if (TextUtils.isEmpty(previousTranslation)) {
      if (isAllHangul(currentTranslation)) {
        result = currentTranslation;
      } else if (specialOTranslationMap.containsKey(brailleCharacter.toString())) {
        result = specialOTranslationMap.get(brailleCharacter.toString());
      } else {
        result = getSyllable(brailleCharacter);
      }
    } else if (currentTranslation.startsWith(previousTranslation)) {
      String difference = currentTranslation.substring(previousTranslation.length());
      if (!TextUtils.isEmpty(difference) && !isAllHangul(difference)) {
        // 사 -> 사`
        String ini =
            currentTranslation.substring(0, currentTranslation.indexOf(previousTranslation) + 1);
        if (!isAllHangul(ini) && !TextUtils.isEmpty(ini)) {
          // < -> <`
          // : -> :`
          // <니i -> <니다
          ini = correctIfPossible(ini);
        } else {
          BrailleWord iniBrailleWord = getHoldings(ini);
          ini +=
              getHangulIfPossible(
                  holdings.subword(
                      holdings.indexOf(iniBrailleWord) + iniBrailleWord.size(), holdings.size()));
        }
        result = ini;
      }
    } else if (!isAllHangul(currentTranslation)) {
      // :` -> :ò
      String common = getCommonString(previousTranslation, currentTranslation);
      if (!TextUtils.isEmpty(common)) {
        if (!isAllHangul(common)) {
          common = correctIfPossible(common);
        }
        result = common;
      } else {
        result = getComposedCharacter(holdings);
      }
    }
    return result;
  }

  private String correctIfPossible(String initString) {
    BrailleWord brailleWord = getHoldings(initString);
    if (brailleWord.size() == 1 && specialOTranslationMap.containsKey(brailleWord.toString())) {
      StringBuilder sb = new StringBuilder(specialOTranslationMap.get(brailleWord.toString()));
      int restWord = holdings.indexOf(brailleWord) + brailleWord.size();
      BrailleWord rest = holdings.subword(restWord, holdings.size());
      String translation = translator.translateToPrint(rest);
      if (!isAllHangul(translation)) {
        sb.append(getHangulIfPossible(rest));
      } else {
        sb.append(translation);
      }
      return sb.toString();
    }
    return initString;
  }

  private String getHangulIfPossible(BrailleWord brailleWord) {
    if (brailleWord.size() == 1) {
      return getSyllable(brailleWord.get(0));
    } else {
      String translation = translator.translateToPrint(brailleWord);
      if (isAllHangul(translation)) {
        return translation;
      } else {
        return getComposedCharacter(brailleWord);
      }
    }
  }

  private String getCommonString(String oldStr, String newStr) {
    int index = -1;
    for (int i = 1; i < oldStr.length(); i++) {
      String s = oldStr.substring(0, i);
      if (newStr.startsWith(s)) {
        index = i;
      }
    }
    if (index != -1) {
      return oldStr.substring(0, index);
    }
    return "";
  }

  private BrailleWord getHoldings(String initString) {
    for (int i = 1; i < holdings.size(); i++) {
      BrailleWord brailleWord = holdings.subword(0, i);
      String t = translator.translateToPrint(brailleWord);
      if (t.equals(initString)) {
        return brailleWord;
      }
    }
    return new BrailleWord();
  }

  private String getComposedCharacter(final BrailleWord brailleWord) {
    char[] result = new char[brailleWord.size()];
    for (int i = 0; i < brailleWord.size(); i++) {
      BrailleCharacter brailleCharacter = brailleWord.get(i);
      String syllable = getSyllable(brailleCharacter);
      if (!TextUtils.isEmpty(syllable)) {
        result[i] = getSyllable(brailleCharacter).charAt(0); // Unicode value;
      }
    }
    return new String(result);
  }

  private String getSpeakableAnnouncement(BrailleCharacter brailleCharacter) {
    String result = speakInitialConsonantsTranslationMap.get(brailleCharacter.toString());
    if (TextUtils.isEmpty(result)) {
      result = speakVowelTranslationMap.get(brailleCharacter.toString());
    }
    if (TextUtils.isEmpty(result)) {
      result = speakFinalConsonantsTranslationMap.get(brailleCharacter.toString());
    }
    if (TextUtils.isEmpty(result)) {
      result = BrailleTranslateUtils.getDotsText(context.getResources(), brailleCharacter);
    }
    return result;
  }

  private String getSyllable(BrailleCharacter brailleCharacter) {
    String result = initialConsonantsTranslationMap.get(brailleCharacter.toString());
    if (TextUtils.isEmpty(result)) {
      result = vowelTranslationMap.get(brailleCharacter.toString());
    }
    if (TextUtils.isEmpty(result)) {
      result = finalConsonantsTranslationMap.get(brailleCharacter.toString());
    }
    return TextUtils.isEmpty(result) ? "" : result;
  }

  private boolean isAllHangul(String all) {
    for (int i = 0; i < all.length(); i++) {
      if (!isHangul(all.charAt(i))) {
        return false;
      }
    }
    return true;
  }

  private boolean isHangul(int codepoint) {
    return (Character.UnicodeScript.of(codepoint) == Character.UnicodeScript.HANGUL);
  }
}
