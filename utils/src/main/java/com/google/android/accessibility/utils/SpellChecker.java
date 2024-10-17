/*
 * Copyright (C) 2023 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.google.android.accessibility.utils;

import static android.text.style.SuggestionSpan.FLAG_AUTO_CORRECTION;
import static android.text.style.SuggestionSpan.FLAG_MISSPELLED;
import static android.view.textservice.SuggestionsInfo.RESULT_ATTR_HAS_RECOMMENDED_SUGGESTIONS;
import static android.view.textservice.SuggestionsInfo.RESULT_ATTR_IN_THE_DICTIONARY;
import static android.view.textservice.SuggestionsInfo.RESULT_ATTR_LOOKS_LIKE_TYPO;

import android.content.Context;
import android.os.Looper;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.SuggestionSpan;
import android.util.Log;
import android.view.textservice.SentenceSuggestionsInfo;
import android.view.textservice.SpellCheckerSession;
import android.view.textservice.SpellCheckerSession.SpellCheckerSessionListener;
import android.view.textservice.SuggestionsInfo;
import android.view.textservice.TextInfo;
import android.view.textservice.TextServicesManager;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import com.google.android.libraries.accessibility.utils.log.LogUtils;
import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import org.checkerframework.checker.nullness.qual.NonNull;

/**
 * This class supports spell checking, by using {@link
 * android.service.textservice.SpellCheckerService}.
 *
 * <p>Synchronous use: call {@link #getTextWithSuggestionSpans(Context,
 * AccessibilityNodeInfoCompat)} or {@link #getTextWithSuggestionSpans(Context, CharSequence)} to
 * start a spell checker session and obtain a text whose misspelled words are wrapped by
 * SuggestionSpans.
 *
 * <p>Asynchronous use: create a {@link SpellChecker} with a {@link SpellCheckingListener}, then
 * invoke {@link #perform()} to start a spell checking session. The {@link #listener} will be
 * invoked when the spell checking session is finished.
 */
// TODO: b/313824604 - Designs a cache mechanism in SpellChecker
public class SpellChecker {

  private static final String TAG = "SpellChecker";
  private static final int FLAG_UNKNOWN_RESULT = 0;
  private static final long SPELL_CHECKING_MAX_WAITING_TIME_MS = 500;
  private static final int MISSPELLED_WORD_SUGGESTIONS_MAX_NUM = 5;
  private static final int RESULT_ATTR_LOOKS_LIKE_GRAMMAR_ERROR =
      FeatureSupport.supportGrammarError()
          ? SuggestionsInfo.RESULT_ATTR_LOOKS_LIKE_GRAMMAR_ERROR
          : 0x0008;

  private static final int FLAG_GRAMMAR_ERROR =
      FeatureSupport.supportGrammarError() ? SuggestionSpan.FLAG_GRAMMAR_ERROR : 0x0008;

  @SuppressWarnings("NonFinalStaticField")
  private static boolean enabled = true;

  private final CharSequence text;
  private final TextServicesManager textServicesManager;
  private final SpellCheckingListener listener;

  private static final Cache resultCache = new Cache();

  /**
   * Creates a spell checker to check spelling and grammar in the given text with a {@link
   * SpellCheckerSessionListener}.
   */
  public SpellChecker(Context context, CharSequence text, SpellCheckingListener listener) {
    this.text = text instanceof Spannable ? text : new SpannableString(text);
    this.listener = listener;
    this.textServicesManager =
        (TextServicesManager) context.getSystemService(Context.TEXT_SERVICES_MANAGER_SERVICE);
  }

  public static void setEnabled(boolean enabled) {
    SpellChecker.enabled = enabled;
  }

  /**
   * Returns {@link AccessibilityNodeInfoCompat}'s text whose misspelled words are wrapped around
   * {@link SuggestionSpan}s. Misspelled words and suggestions found out by the spell checker.
   */
  public static CharSequence getTextWithSuggestionSpans(
      Context context, AccessibilityNodeInfoCompat node) {
    return getTextWithSuggestionSpans(context, node.getText());
  }

  /**
   * Returns a {@link CharSequence} whose misspelled words are wrapped around {@link
   * SuggestionSpan}s. Misspelled words and suggestions found out by the spell checker.
   */
  public static CharSequence getTextWithSuggestionSpans(Context context, CharSequence text) {
    return getTextWithSuggestionSpans(context, text, new SpellCheckingListener());
  }

  private static CharSequence getTextWithSuggestionSpans(
      Context context, CharSequence text, final SpellCheckingListener listener) {
    if (!enabled) {
      LogUtils.v(TAG, "Active spell check is disabled.");
      return text;
    }

    if (TextUtils.isEmpty(text)) {
      LogUtils.v(TAG, "Text is empty.");
      return new SpannableString("");
    }

    if (hasCachedResult(text)) {
      LogUtils.v(TAG, "Get result from the cache.");
      return getResultFromCache();
    }

    final SpellChecker spellChecker = new SpellChecker(context, text, listener);
    final Thread thread =
        new Thread(
            () -> {
              // Creates a looper to perform spell checker and wait for the result.
              Looper.prepare();
              if (!spellChecker.perform()) {
                LogUtils.e(TAG, "Fail to perform spell checker.");
                return;
              }
              Looper.loop();
            });
    thread.start();
    try {
      // Waits for the result.
      thread.join(SPELL_CHECKING_MAX_WAITING_TIME_MS);
    } catch (InterruptedException e) {
      LogUtils.e(TAG, "Fail to wait for the thread of spell checking.");
    }

    if (!spellChecker.listener.isFinished()) {
      LogUtils.e(TAG, "Spell checking timeout.");
      return text;
    }

    CharSequence result =
        wrapSuggestionSpansForAllMisspelledWords(context, text, spellChecker.listener.getResult());
    updateCachedResult(result);
    return result;
  }

  /**
   * Creates a spell checker session to get spelling suggestions. The session is asynchronous. The
   * result is able to obtain from the {@link SpellCheckingListener}.
   */
  @CanIgnoreReturnValue
  public boolean perform() {
    LogUtils.v(TAG, "Start a session for [%s]", text);
    if (TextUtils.isEmpty(text)) {
      LogUtils.e(TAG, "Spell checking in an empty text.");
      return false;
    }

    if (textServicesManager == null) {
      LogUtils.e(TAG, "TextServicesManager is null.");
      return false;
    }

    SpellCheckerSession session =
        textServicesManager.newSpellCheckerSession(
            /* bundle= */ null,
            /* locale= */ null, // Refers to the spell checker language settings page.
            listener,
            /* referToSpellCheckerLanguageSettings= */ true);
    if (session == null) {
      LogUtils.e(TAG, "Fail to create a SpellingCheckerSession.");
      return false;
    }

    // Results are returned via SpellCheckingListener.onGetSentenceSuggestions() asynchronously.
    session.getSentenceSuggestions(
        new TextInfo[] {new TextInfo(text.toString())}, MISSPELLED_WORD_SUGGESTIONS_MAX_NUM);
    return true;
  }

  private static List<SpellingSuggestion> mergeSuggestions(
      Context context, CharSequence text, ImmutableList<SentenceSuggestionsInfo> results) {
    // Merges two suggestions list in order of misspelled words index.
    if (TextUtils.isEmpty(text)) {
      return ImmutableList.of();
    }

    Spanned spanned = (text instanceof Spannable) ? (Spanned) text : new SpannableString(text);

    // Converts the spelling checker results to a SpellingSuggestion list.
    List<SpellingSuggestion> suggestionsForSpellCheck = new ArrayList<>();
    if (!results.isEmpty()) {
      SentenceSuggestionsInfo sentenceSuggestionsInfo = null;
      // Since the spell checking session is created with only one sentence, so there is only one
      // SentenceSuggestionsInfo in results list.
      for (SentenceSuggestionsInfo info : results) {
        if (info != null) {
          sentenceSuggestionsInfo = info;
          break;
        }
      }

      if (sentenceSuggestionsInfo == null) {
        return ImmutableList.of();
      }

      int count = sentenceSuggestionsInfo.getSuggestionsCount();
      for (int i = 0; i < count; i++) {
        SuggestionsInfo suggestionsInfo = sentenceSuggestionsInfo.getSuggestionsInfoAt(i);
        int start = sentenceSuggestionsInfo.getOffsetAt(i);
        int end = start + sentenceSuggestionsInfo.getLengthAt(i);
        @Nullable
        SpellingSuggestion spellingSuggestion =
            createSpellingSuggestion(
                context, suggestionsInfo, start, end, spanned.subSequence(start, end));
        if (spellingSuggestion == null) {
          continue;
        }
        suggestionsForSpellCheck.add(spellingSuggestion);
      }
    }

    // Converts the SuggestionSpan list which is from the node text to a SpellingSuggestion list.
    SuggestionSpan[] originalSuggestionSpans =
        spanned.getSpans(0, spanned.length(), SuggestionSpan.class);
    List<SpellingSuggestion> suggestionsForOriginalSuggestionSpan = new ArrayList<>();
    for (SuggestionSpan suggestionSpan : originalSuggestionSpans) {
      suggestionsForOriginalSuggestionSpan.add(createSpellingSuggestion(spanned, suggestionSpan));
    }

    return mergeSuggestions(suggestionsForOriginalSuggestionSpan, suggestionsForSpellCheck);
  }

  /** Merges two SpellingSuggestion lists and sorts by the start index. */
  private static List<SpellingSuggestion> mergeSuggestions(
      List<SpellingSuggestion> list1, List<SpellingSuggestion> list2) {
    List<SpellingSuggestion> spellingSuggestions = new ArrayList<>();
    int index1 = 0;
    int index2 = 0;
    int count1 = list1.size();
    int count2 = list2.size();
    while (index1 < count1 && index2 < count2) {
      SpellingSuggestion spellingSuggestion1 = list1.get(index1);
      SpellingSuggestion spellingSuggestion2 = list2.get(index2);
      if (spellingSuggestion1.ahead(spellingSuggestion2)) {
        spellingSuggestions.add(spellingSuggestion1);
        index1++;
      } else if (spellingSuggestion1.contain(spellingSuggestion2)
          || spellingSuggestion1.isMisspelledWordEqual(spellingSuggestion2)) {
        spellingSuggestions.add(spellingSuggestion1);
        index1++;
        // spellingSuggestion2 is inside of or equal spellingSuggestion1, so it can be skipped.
        index2++;
      } else if (spellingSuggestion2.contain(spellingSuggestion1)) {
        spellingSuggestions.add(spellingSuggestion2);
        index2++;
        // spellingSuggestion1 is inside of spellingSuggestion2, so it can be skipped.
        index1++;
      } else {
        // spellingSuggestion2 is ahead of spellingSuggestion1.
        spellingSuggestions.add(spellingSuggestion2);
        index2++;
      }
    }

    // Adds remaining items to the new list.
    while (index1 < count1) {
      spellingSuggestions.add(list1.get(index1));
      index1++;
    }
    while (index2 < count2) {
      spellingSuggestions.add(list2.get(index2));
      index2++;
    }
    return spellingSuggestions;
  }

  private static CharSequence wrapSuggestionSpans(
      CharSequence text, List<SpellingSuggestion> spellingSuggestions) {
    if (TextUtils.isEmpty(text)) {
      return text;
    }

    SpannableString spannableString = new SpannableString(text.toString());
    for (SpellingSuggestion suggestion : spellingSuggestions) {
      spannableString.setSpan(
          suggestion.suggestionSpan(),
          suggestion.start(),
          suggestion.end(),
          Spannable.SPAN_INCLUSIVE_EXCLUSIVE);
      LogUtils.v(TAG, suggestion.toString());
    }
    return spannableString;
  }

  @VisibleForTesting
  static CharSequence wrapSuggestionSpansForAllMisspelledWords(
      Context context,
      CharSequence text,
      ImmutableList<SentenceSuggestionsInfo> sentenceSuggestionsInfos) {
    return wrapSuggestionSpans(text, mergeSuggestions(context, text, sentenceSuggestionsInfos));
  }

  @Nullable
  private static SpellingSuggestion createSpellingSuggestion(
      Context context,
      SuggestionsInfo suggestionsInfo,
      int suggestionSpanStartIndex,
      int suggestionEndIndex,
      CharSequence misspelledWord) {
    int suggestionType = getSuggestionType(suggestionsInfo);
    if (suggestionType == FLAG_UNKNOWN_RESULT) {
      return null;
    }
    return SpellingSuggestion.create(
        suggestionSpanStartIndex,
        suggestionEndIndex,
        misspelledWord,
        suggestionsInfoToSuggestionSpan(context, suggestionsInfo, suggestionType));
  }

  private static SpellingSuggestion createSpellingSuggestion(
      Spanned text, SuggestionSpan suggestionSpan) {
    int start = text.getSpanStart(suggestionSpan);
    int end = text.getSpanEnd(suggestionSpan);
    return SpellingSuggestion.create(start, end, text.subSequence(start, end), suggestionSpan);
  }

  private static SuggestionSpan suggestionsInfoToSuggestionSpan(
      Context context, SuggestionsInfo suggestionsInfo, int flag) {
    int count = suggestionsInfo.getSuggestionsCount();
    if (count <= 0) {
      return new SuggestionSpan(context, new String[0], flag);
    }
    String[] suggestions = new String[count];
    for (int i = 0; i < count; i++) {
      suggestions[i] = suggestionsInfo.getSuggestionAt(i);
    }
    return new SuggestionSpan(context, suggestions, flag);
  }

  private static int getSuggestionType(SuggestionsInfo suggestionsInfo) {
    int attribute = suggestionsInfo.getSuggestionsAttributes();
    if ((attribute & RESULT_ATTR_LOOKS_LIKE_TYPO) != 0) {
      return FLAG_MISSPELLED;
    } else if ((attribute & RESULT_ATTR_LOOKS_LIKE_GRAMMAR_ERROR) != 0) {
      return FLAG_GRAMMAR_ERROR;
    } else if ((attribute & RESULT_ATTR_HAS_RECOMMENDED_SUGGESTIONS) != 0) {
      return FLAG_MISSPELLED;
    }
    return FLAG_UNKNOWN_RESULT;
  }

  @VisibleForTesting
  static void updateCachedResult(CharSequence textWithSuggestionSpans) {
    resultCache.textWithSuggestionSpans = textWithSuggestionSpans;
  }

  private static boolean hasCachedResult(CharSequence text) {
    return resultCache.isSameText(text);
  }

  private static CharSequence getResultFromCache() {
    return resultCache.textWithSuggestionSpans;
  }

  /**
   * A callback to obtain spelling suggestions from the {@link
   * android.service.textservice.SpellCheckerService}
   */
  @VisibleForTesting
  static class SpellCheckingListener implements SpellCheckerSessionListener {

    // Do not set the text directly. Sets text text via setText() to ensure it's spannable.
    private ImmutableList<SentenceSuggestionsInfo> results = ImmutableList.of();
    private boolean isFinished;

    public SpellCheckingListener() {}

    /**
     * Invoked when the spell checking is finished.
     *
     * @param results misspelled errors and suggestions..
     */
    public void onFinished(ImmutableList<SentenceSuggestionsInfo> results) {
      setResult(results);
      isFinished = true;

      Looper looper = Looper.myLooper();
      // Quits the looper to finish waiting for the result.
      if (looper != null && looper != Looper.getMainLooper()) {
        looper.quitSafely();
      }
    }

    @Override
    public void onGetSuggestions(SuggestionsInfo[] results) {}

    @Override
    public void onGetSentenceSuggestions(SentenceSuggestionsInfo[] results) {
      LogUtils.v(TAG, "onGetSentenceSuggestions");
      if (results == null) {
        onFinished(ImmutableList.of());
        return;
      }
      dumpSuggestionsInfos(results);
      onFinished(ImmutableList.copyOf(results));
    }

    private void setResult(ImmutableList<SentenceSuggestionsInfo> results) {
      this.results = results;
    }

    @VisibleForTesting
    ImmutableList<SentenceSuggestionsInfo> getResult() {
      return results;
    }

    @VisibleForTesting
    boolean isFinished() {
      return isFinished;
    }

    private static void dumpSuggestionsInfos(SentenceSuggestionsInfo[] results) {
      if (!LogUtils.shouldLog(Log.VERBOSE)) {
        return;
      }
      for (SentenceSuggestionsInfo sentenceSuggestionsInfo : results) {
        if (sentenceSuggestionsInfo == null) {
          continue;
        }
        for (int i = 0; i < sentenceSuggestionsInfo.getSuggestionsCount(); i++) {
          SuggestionsInfo suggestionsInfo = sentenceSuggestionsInfo.getSuggestionsInfoAt(i);
          if ((suggestionsInfo.getSuggestionsAttributes() & RESULT_ATTR_IN_THE_DICTIONARY) != 0) {
            continue;
          }
          StringBuilder suggestions = new StringBuilder("suggestions=");
          for (int j = 0; j < suggestionsInfo.getSuggestionsCount(); j++) {
            suggestions.append(suggestionsInfo.getSuggestionAt(j));
            suggestions.append("/");
          }
          LogUtils.v(
              TAG,
              "suggestionInfo[%d] start=%d length=%d flag=%s suggestions=%s",
              i,
              sentenceSuggestionsInfo.getOffsetAt(i),
              sentenceSuggestionsInfo.getLengthAt(i),
              suggestionsAttributeToString(suggestionsInfo.getSuggestionsAttributes()),
              suggestions);
        }
      }
    }

    private static String suggestionsAttributeToString(int attribute) {
      StringBuilder builder = new StringBuilder();
      if ((attribute & RESULT_ATTR_LOOKS_LIKE_TYPO) != 0) {
        builder.append("RESULT_ATTR_LOOKS_LIKE_TYPO/");
      }
      if ((attribute & RESULT_ATTR_HAS_RECOMMENDED_SUGGESTIONS) != 0) {
        builder.append("RESULT_ATTR_HAS_RECOMMENDED_SUGGESTIONS/");
      }
      if ((attribute & RESULT_ATTR_LOOKS_LIKE_GRAMMAR_ERROR) != 0) {
        builder.append("RESULT_ATTR_LOOKS_LIKE_GRAMMAR_ERROR/");
      }
      return builder.toString();
    }
  }

  /** A wrapper of {@link SuggestionSpan}. */
  @AutoValue
  public abstract static class SpellingSuggestion {
    public abstract int start();

    public abstract int end();

    public abstract CharSequence misspelledWord();

    public abstract SuggestionSpan suggestionSpan();

    public static SpellingSuggestion create(
        int start, int end, CharSequence misspelledWord, SuggestionSpan suggestionSpan) {
      return new AutoValue_SpellChecker_SpellingSuggestion(
          start, end, misspelledWord, suggestionSpan);
    }

    /**
     * Returns true if index of the given {@link SpellingSuggestion} is in front of this
     * suggestions.
     */
    private boolean ahead(SpellingSuggestion spellingSuggestion) {
      return (start() < spellingSuggestion.start()) && (end() < spellingSuggestion.end());
    }

    /**
     * Returns true if the misspelled words of the given {@link SpellingSuggestion} is part of the
     * misspelled words.
     */
    private boolean contain(SpellingSuggestion spellingSuggestion) {
      return ((start() < spellingSuggestion.start()) && (end() > spellingSuggestion.end()))
          || ((start() == spellingSuggestion.start()) && (end() > spellingSuggestion.end()))
          || ((start() < spellingSuggestion.start()) && (end() == spellingSuggestion.end()));
    }

    private boolean isMisspelledWordEqual(SpellingSuggestion spellingSuggestion) {
      return (start() == spellingSuggestion.start()) && (end() == spellingSuggestion.end());
    }

    @Override
    public final @NonNull String toString() {
      return String.format(
          Locale.getDefault(),
          "[%d-%d][%s][flag=%s][suggestion=%s]",
          start(),
          end(),
          misspelledWord(),
          getSuggestionFlag(suggestionSpan().getFlags()),
          Arrays.toString(suggestionSpan().getSuggestions()));
    }

    private static String getSuggestionFlag(int attribute) {
      StringBuilder flags = new StringBuilder();
      if ((attribute & FLAG_MISSPELLED) != 0) {
        flags.append("FLAG_MISSPELLED/");
      }
      if ((attribute & FLAG_AUTO_CORRECTION) != 0) {
        flags.append("FLAG_AUTO_CORRECTION/");
      }
      if ((attribute & FLAG_GRAMMAR_ERROR) != 0) {
        flags.append("FLAG_GRAMMAR_ERROR/");
      }
      return flags.toString();
    }
  }

  /** A class to store the result of the spell checker. */
  private static class Cache {

    /**
     * Typo or grammar errors in {@code text} found by the spell checker are wrapped around {@link
     * SuggestionSpan}s.
     */
    @Nullable private CharSequence textWithSuggestionSpans;

    private Cache() {}

    /** Returns {@code true}, if the result in the cache is for the given text. */
    private boolean isSameText(CharSequence newText) {
      return !TextUtils.isEmpty(textWithSuggestionSpans)
          && TextUtils.equals(textWithSuggestionSpans, newText);
    }
  }
}
