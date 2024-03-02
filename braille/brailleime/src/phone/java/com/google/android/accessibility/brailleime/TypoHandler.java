package com.google.android.accessibility.brailleime;

import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.TtsSpan;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.inputmethod.InputConnection;
import androidx.annotation.VisibleForTesting;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import com.google.android.accessibility.braille.common.BrailleTypoFinder;
import com.google.android.accessibility.braille.common.BrailleTypoFinder.NoTypoFocusFoundException;
import com.google.android.accessibility.braille.common.TalkBackSpeaker;
import com.google.android.accessibility.braille.interfaces.ScreenReaderActionPerformer.ScreenReaderAction;
import com.google.android.accessibility.braille.interfaces.TalkBackForBrailleIme;
import com.google.android.accessibility.utils.AccessibilityNodeInfoUtils.SpellingSuggestion;
import com.google.android.accessibility.utils.FocusFinder;
import com.google.errorprone.annotations.CanIgnoreReturnValue;

/** Class handles all the typo correction functionalities in Braille keyboard. */
public class TypoHandler {
  private static final int NON_INITIALIZED = -1;
  private final TalkBackForBrailleIme talkBackForBrailleIme;
  private final TalkBackSpeaker talkBackSpeaker;
  private BrailleTypoFinder typoFinder;
  private InputConnection inputConnection;
  private CharSequence lastCorrectedTypo;
  private int lastCorrectedTypoHeadIndex;
  private int lastCorrectedSuggestionTailIndex;

  public TypoHandler(
      FocusFinder focusFinder,
      TalkBackForBrailleIme talkBackForBrailleIme,
      TalkBackSpeaker talkBackSpeaker) {
    this.typoFinder = new BrailleTypoFinder(focusFinder);
    this.talkBackForBrailleIme = talkBackForBrailleIme;
    this.talkBackSpeaker = talkBackSpeaker;
    clearCorrectionCache();
  }

  /** Updates the {@link InputConnection} for current editor. */
  public void updateInputConnection(InputConnection inputConnection) {
    this.inputConnection = inputConnection;
  }

  /** Refreshes focused typo. */
  @CanIgnoreReturnValue
  public boolean updateTypoTarget() {
    clearCorrectionCache();
    return typoFinder.updateTypoCorrectionFrom(AccessibilityNodeInfo.FOCUS_INPUT);
  }

  /** Navigates to next suggestion for current typo. */
  @CanIgnoreReturnValue
  public boolean nextSuggestion() {
    String suggestion;
    try {
      suggestion = typoFinder.nextSuggestion();
    } catch (NoTypoFocusFoundException e) {
      if (updateTypoTarget()) {
        return nextSuggestion();
      }
      return false;
    }
    talkBackSpeaker.speak(grantVerbatimSpan(suggestion));
    return true;
  }

  /** Navigates to previous suggestion for current typo. */
  @CanIgnoreReturnValue
  public boolean previousSuggestion() {
    String suggestion;
    try {
      suggestion = typoFinder.previousSuggestion();
    } catch (NoTypoFocusFoundException e) {
      if (updateTypoTarget()) {
        return previousSuggestion();
      }
      return false;
    }
    talkBackSpeaker.speak(grantVerbatimSpan(suggestion));
    return true;
  }

  /** Confirms current suggestion as final. */
  public boolean confirmSuggestion() {
    String suggestionCandidate;
    try {
      suggestionCandidate = typoFinder.getCurrentSuggestionCandidate();
      if (TextUtils.isEmpty(suggestionCandidate)) {
        // There are 2 possible causes: 1. This typo doesn't have suggestions. 2. User hasn't chosen
        // a suggestion.
        return false;
      }
    } catch (NoTypoFocusFoundException e) {
      return false;
    }
    SpellingSuggestion spellingSuggestion = typoFinder.getSpellingSuggestion();
    AccessibilityNodeInfoCompat node = typoFinder.getTargetNode();
    lastCorrectedTypoHeadIndex = spellingSuggestion.start();
    lastCorrectedTypo =
        spellingSuggestion
            .misspelledWord()
            .subSequence(0, spellingSuggestion.misspelledWord().length());
    lastCorrectedSuggestionTailIndex =
        lastCorrectedTypoHeadIndex + suggestionCandidate.length() - 1;
    typoFinder.clear();
    return talkBackForBrailleIme.performAction(
        ScreenReaderAction.TYPO_CORRECT, node, suggestionCandidate, spellingSuggestion);
  }

  /** Undoes last confirmed final to original typo. */
  public boolean undoConfirmSuggestion() {
    if (lastCorrectedTypo == null) {
      return false;
    }
    // Replace replaced suggestion with original typo.
    inputConnection.beginBatchEdit();
    inputConnection.setComposingRegion(
        lastCorrectedTypoHeadIndex, lastCorrectedSuggestionTailIndex + 1);
    inputConnection.setComposingText(lastCorrectedTypo, 0);
    inputConnection.finishComposingText();
    inputConnection.endBatchEdit();
    clearCorrectionCache();
    return true;
  }

  private void clearCorrectionCache() {
    lastCorrectedTypoHeadIndex = NON_INITIALIZED;
    lastCorrectedTypo = null;
    lastCorrectedSuggestionTailIndex = NON_INITIALIZED;
  }

  private Spanned grantVerbatimSpan(String string) {
    return new SpannableStringBuilder()
        .append(string, new TtsSpan.TextBuilder(string + ",").build(), 0)
        .append(" ", new TtsSpan.VerbatimBuilder(string).build(), 0);
  }

  @VisibleForTesting
  void testing_setTypoFinder(BrailleTypoFinder typoFinder) {
    this.typoFinder = typoFinder;
  }
}
