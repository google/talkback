/*
 * Copyright (C) 2012 Google Inc.
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
package com.google.android.accessibility.braille.brailledisplay.controller;

import static com.google.android.accessibility.braille.common.BrailleUserPreferences.BRAILLE_SHARED_PREFS_FILENAME;
import static com.google.android.accessibility.braille.common.translate.EditBufferUtils.NO_CURSOR;
import static java.lang.Math.max;
import static java.lang.Math.min;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.text.style.ClickableSpan;
import android.util.Range;
import androidx.annotation.VisibleForTesting;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import com.google.android.accessibility.braille.brailledisplay.analytics.BrailleDisplayAnalytics;
import com.google.android.accessibility.braille.brailledisplay.controller.DisplayInfo.Source;
import com.google.android.accessibility.braille.brailledisplay.controller.TranslatorManager.OutputCodeChangedListener;
import com.google.android.accessibility.braille.brailledisplay.controller.wrapping.EditorWordWrapStrategy;
import com.google.android.accessibility.braille.brailledisplay.controller.wrapping.WordWrapStrategy;
import com.google.android.accessibility.braille.brailledisplay.controller.wrapping.WrapStrategy;
import com.google.android.accessibility.braille.common.BrailleUserPreferences;
import com.google.android.accessibility.braille.common.R;
import com.google.android.accessibility.braille.interfaces.SelectionRange;
import com.google.android.accessibility.braille.translate.TranslationResult;
import com.google.auto.value.AutoValue;
import com.google.common.base.Preconditions;
import com.google.common.collect.Iterables;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

/** Keeps track of the current display content and handles panning. */
public class CellsContentManager implements CellsContentConsumer {
  private static final String TAG = "CellsContentManager";

  /** Indicates the cursor is at which part of the text with a position. */
  @AutoValue
  abstract static class Cursor {
    enum Type {
      TEXT_FIELD,
      HOLDINGS,
      ACTION
    }

    static Cursor create(int position, Type cursorType) {
      return new AutoValue_CellsContentManager_Cursor(position, cursorType);
    }

    public abstract int position();

    public abstract Type type();
  }

  /** Provides the active status of the IME. */
  public interface ImeStatusProvider {
    boolean isImeOpen();
  }

  /** A callback used to send braille display dots toward the hardware. */
  public interface DotDisplayer {
    void displayDots(byte[] patterns, CharSequence text, int[] brailleToTextPositions);
  }

  private final Context context;
  private final ImeStatusProvider imeStatusProvider;
  private final TranslatorManager translatorManager;
  private final Pulser pulseHandler;
  private final TimedMessager timedMessager;
  private final DotDisplayer inputEventListener;
  private WrapStrategy editingWrapStrategy;
  private WrapStrategy preferredWrapStrategy;
  private boolean overlaysOn;
  private boolean panUpOverflow;
  private Range<Integer> holdingsRange;
  private List<Range<Integer>> onScreenRange;
  private Range<Integer> actionRange;
  private DisplayInfoWrapper commonDisplayInfoWrapper;
  private DisplayInfoWrapper timedMessageDisplayInfoWrapper;
  private final List<OnDisplayContentChangeListener> onDisplayContentChangeListeners =
      new ArrayList<>();

  /**
   * Creates an instance of this class and starts the internal thread to connect to the braille
   * display service. {@code contextArg} is used to connect to the display service. {@code
   * translator} is used for braille translation. The various listeners will be called as
   * appropriate and on the same thread that was used to create this object. The current thread must
   * have a prepared looper.
   */
  public CellsContentManager(
      Context context,
      ImeStatusProvider imeStatusProvider,
      TranslatorManager translatorManagerArg,
      DotDisplayer inputEventListenerArg) {
    this.context = context;
    this.imeStatusProvider = imeStatusProvider;
    translatorManager = translatorManagerArg;
    inputEventListener = inputEventListenerArg;
    pulseHandler = new Pulser(this::pulse, BrailleUserPreferences.readBlinkingIntervalMs(context));
    timedMessager = new TimedMessager(timedMessagerCallback);
  }

  public void start(int numTextCells) {
    preferredWrapStrategy = new WordWrapStrategy(numTextCells);
    editingWrapStrategy = new EditorWordWrapStrategy(numTextCells);
    commonDisplayInfoWrapper =
        new DisplayInfoWrapper(new ContentHelper(translatorManager, wrapStrategyRetriever));
    timedMessageDisplayInfoWrapper =
        new DisplayInfoWrapper(
            new ContentHelper(translatorManager, new WordWrapStrategy(numTextCells)));
    translatorManager.addOnOutputTablesChangedListener(outputCodeChangedListener);
    BrailleUserPreferences.getSharedPreferences(context, BRAILLE_SHARED_PREFS_FILENAME)
        .registerOnSharedPreferenceChangeListener(onSharedPreferenceChangeListener);
  }

  public void shutdown() {
    pulseHandler.cancelPulse();
    translatorManager.removeOnOutputTablesChangedListener(outputCodeChangedListener);
    BrailleUserPreferences.getSharedPreferences(context, BRAILLE_SHARED_PREFS_FILENAME)
        .unregisterOnSharedPreferenceChangeListener(onSharedPreferenceChangeListener);
    onDisplayContentChangeListeners.clear();
  }

  /**
   * Updates the display to reflect {@code content}. The {@code content} must not be modified after
   * this function is called.
   */
  @Override
  public void setContent(CellsContent content, Reason reason) {
    Preconditions.checkNotNull(content, "content can't be null");
    Preconditions.checkNotNull(content.getText(), "content text is null");
    commonDisplayInfoWrapper.renewDisplayInfo(
        content.getText(), content.getPanStrategy(), content.isSplitParagraphs());
    BrailleDisplayAnalytics.getInstance(context)
        .logReadingBrailleCharacter(
            commonDisplayInfoWrapper.getDisplayInfo().displayedBraille().array().length);
    if (reason == Reason.NAVIGATE_TO_NEW_NODE && panUpOverflow) {
      refreshToTail();
    } else {
      refresh();
    }
    cellOnDisplayContentChanged();
    panUpOverflow = false;
  }

  /** This is for BrailleIme to display the content. */
  public void setContent(
      List<Range<Integer>> onScreenRange,
      Range<Integer> holdingsRange,
      Range<Integer> actionRange,
      SelectionRange selection,
      TranslationResult overlayTranslationResult,
      boolean isMultiLine) {
    this.onScreenRange = onScreenRange;
    this.holdingsRange = holdingsRange;
    this.actionRange = actionRange;
    int beginningOfInput =
        onScreenRange.isEmpty()
            ? (holdingsRange.getLower() == NO_CURSOR
                ? min(selection.start, selection.end)
                : holdingsRange.getLower())
            : onScreenRange.get(0).getLower();
    int holdingsPosition =
        holdingsRange.getLower() == NO_CURSOR
            ? max(selection.start, selection.end)
            : holdingsRange.getUpper();
    int textCursorPosition =
        onScreenRange.isEmpty()
            ? max(selection.start, selection.end)
            : Iterables.getLast(onScreenRange).getUpper();
    commonDisplayInfoWrapper.renewDisplayInfo(
        CellsContent.PAN_CURSOR,
        selection,
        beginningOfInput,
        max(holdingsPosition, textCursorPosition),
        isMultiLine,
        overlayTranslationResult,
        Source.IME);
    panUpOverflow = false;
    refresh();
    cellOnDisplayContentChanged();
  }

  @Override
  public void setTimedContent(CellsContent content, int durationInMilliseconds) {
    Preconditions.checkNotNull(content, "content can't be null");
    Preconditions.checkNotNull(content.getText(), "content text is null");
    timedMessager.setTimedMessage(content, durationInMilliseconds);
  }

  @Override
  public AccessibilityNodeInfoCompat getAccessibilityNode(int byteIndex) {
    return getCurrentDisplayInfoWrapper().getContentHelper().getAccessibilityNodeInfo(byteIndex);
  }

  @Override
  public int getTextIndexInWhole(int routingKeyIndex) {
    return getCurrentDisplayInfoWrapper().getContentHelper().getTextCursorPosition(routingKeyIndex);
  }

  @Override
  public Optional<ClickableSpan[]> getClickableSpans(int routingKeyIndex) {
    return getCurrentDisplayInfoWrapper().getContentHelper().getClickableSpans(routingKeyIndex);
  }

  /** Checks whether split point exists. */
  public boolean hasSplitPoints() {
    return editingWrapStrategy.hasSplitPoints();
  }

  /** Returns the length of current show content. */
  public int getCurrentShowContentLength() {
    return getCurrentDisplayInfoWrapper().getDisplayInfo().displayedBraille().array().length;
  }

  /** Checks whether the timed message is showing. */
  @Override
  public boolean isTimedMessageDisplaying() {
    return timedMessager.isTimedMessageDisplaying();
  }

  @Override
  public void clearTimedMessage() {
    timedMessager.clearTimedMessage();
  }

  /** Maps the click index on the braille display to its index in the whole content. */
  public Cursor map(int positionOnBrailleDisplay) throws ExecutionException {
    ContentHelper contentHelper = getCurrentDisplayInfoWrapper().getContentHelper();
    int bytePositionInWhole = contentHelper.toWholeContentIndex(positionOnBrailleDisplay);
    if (bytePositionInWhole == NO_CURSOR) {
      throw new ExecutionException("Can't move cursor to " + positionOnBrailleDisplay, null);
    } else if (actionRange.contains(bytePositionInWhole)) {
      return Cursor.create(NO_CURSOR, Cursor.Type.ACTION);
    } else if (holdingsRange.contains(bytePositionInWhole)) {
      return Cursor.create(bytePositionInWhole - holdingsRange.getLower(), Cursor.Type.HOLDINGS);
    } else if (holdingsRange.getLower() != NO_CURSOR) {
      return Cursor.create(
          bytePositionInWhole > holdingsRange.getLower()
              ? holdingsRange.getUpper() - holdingsRange.getLower() + 1
              : NO_CURSOR,
          Cursor.Type.HOLDINGS);
    }
    int indexOnTextFieldText = NO_CURSOR;
    for (Range<Integer> range : onScreenRange) {
      if (range.contains(bytePositionInWhole)) {
        indexOnTextFieldText +=
            contentHelper.transferByteIndexToTextIndex(bytePositionInWhole)
                - contentHelper.transferByteIndexToTextIndex(range.getLower())
                + 1;
        return Cursor.create(indexOnTextFieldText, Cursor.Type.TEXT_FIELD);
      }
      if (bytePositionInWhole > range.getUpper()) {
        int upperTextIndex = contentHelper.transferByteIndexToTextIndex(range.getUpper());
        int lowerTextIndex = contentHelper.transferByteIndexToTextIndex(range.getLower());
        indexOnTextFieldText += upperTextIndex - lowerTextIndex + 1;
      }
    }
    // The routing button user clicks on cannot move any cursor.
    throw new ExecutionException("Can't move cursor to " + positionOnBrailleDisplay, null);
  }

  /**
   * Returns {@code true} if panning the displayed info up successfully, {@code false} if reach to
   * the end.
   */
  public boolean panUp() {
    DisplayInfoWrapper displayInfoWrapper = getCurrentDisplayInfoWrapper();
    if (displayInfoWrapper.panUp()) {
      refresh();
      cellOnDisplayContentChanged();
    } else if (timedMessager.isTimedMessageDisplaying()) {
      clearTimedMessage();
    } else {
      panUpOverflow = true;
      return false;
    }
    return true;
  }

  /**
   * Returns {@code true} if panning the displayed info down successfully, {@code false} if reach to
   * the end.
   */
  public boolean panDown() {
    DisplayInfoWrapper displayInfoWrapper = getCurrentDisplayInfoWrapper();
    if (displayInfoWrapper.panDown()) {
      refresh();
      cellOnDisplayContentChanged();
    } else if (timedMessager.isTimedMessageDisplaying()) {
      clearTimedMessage();
    } else {
      return false;
    }
    return true;
  }

  private void pulse() {
    overlaysOn = !overlaysOn;
    refresh();
  }

  private void refreshToTail() {
    DisplayInfoWrapper displayInfoWrapper = getCurrentDisplayInfoWrapper();
    while (displayInfoWrapper.panDown()) {
      // Empty.
    }
    refresh();
  }

  private void refresh() {
    DisplayInfo displayInfoTarget = getCurrentDisplayInfoWrapper().getDisplayInfo();
    if (displayInfoTarget == null) {
      return;
    }
    byte[] toDisplay =
        overlaysOn
            ? displayInfoTarget.displayedOverlaidBraille().array()
            : displayInfoTarget.displayedBraille().array();
    inputEventListener.displayDots(
        toDisplay,
        displayInfoTarget.displayedText(),
        displayInfoTarget.displayedBrailleToTextPositions().stream()
            .mapToInt(Integer::intValue)
            .toArray());
    if (displayInfoTarget.blink()) {
      pulseHandler.schedulePulse();
    } else {
      pulseHandler.cancelPulse();
      overlaysOn = true;
    }
  }

  private DisplayInfoWrapper getCurrentDisplayInfoWrapper() {
    // Timed message has higher priority than common message.
    return timedMessageDisplayInfoWrapper.hasDisplayInfo()
        ? timedMessageDisplayInfoWrapper
        : commonDisplayInfoWrapper;
  }

  private final ContentHelper.WrapStrategyRetriever wrapStrategyRetriever =
      new ContentHelper.WrapStrategyRetriever() {
        @Override
        public WrapStrategy getWrapStrategy() {
          return imeStatusProvider.isImeOpen() ? editingWrapStrategy : preferredWrapStrategy;
        }
      };

  private final OutputCodeChangedListener outputCodeChangedListener =
      new OutputCodeChangedListener() {
        @Override
        public void onOutputCodeChanged() {
          timedMessageDisplayInfoWrapper.retranslate();
          commonDisplayInfoWrapper.retranslate();
          refresh();
          cellOnDisplayContentChanged();
        }
      };

  private final OnSharedPreferenceChangeListener onSharedPreferenceChangeListener =
      new OnSharedPreferenceChangeListener() {
        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
          if (context.getString(R.string.pref_bd_blinking_interval_key).equals(key)) {
            pulseHandler.setFrequencyMillis(BrailleUserPreferences.readBlinkingIntervalMs(context));
          }
        }
      };

  private final TimedMessager.Callback timedMessagerCallback =
      new TimedMessager.Callback() {
        @Override
        public void onTimedMessageDisplayed(CellsContent content) {
          timedMessageDisplayInfoWrapper.renewDisplayInfo(
              content.getText(), content.getPanStrategy(), content.isSplitParagraphs());
          refresh();
          cellOnDisplayContentChanged();
        }

        @Override
        public void onTimedMessageCleared() {
          if (timedMessageDisplayInfoWrapper.hasDisplayInfo()) {
            timedMessageDisplayInfoWrapper.clear();
            refresh();
            cellOnDisplayContentChanged();
          }
        }
      };

  /** Interface definition for a callback to be invoked when the display content is changed. */
  interface OnDisplayContentChangeListener {
    void onDisplayContentChanged();
  }

  /** Adds a listener to be called when display content have changed. */
  public void addOnDisplayContentChangeListener(OnDisplayContentChangeListener listener) {
    onDisplayContentChangeListeners.add(listener);
  }

  /** Removes a display content change listener. */
  public void removeOnDisplayContentChangeListener(OnDisplayContentChangeListener listener) {
    onDisplayContentChangeListeners.remove(listener);
  }

  private void cellOnDisplayContentChanged() {
    for (OnDisplayContentChangeListener listener : onDisplayContentChangeListeners) {
      listener.onDisplayContentChanged();
    }
  }

  @VisibleForTesting
  ContentHelper.WrapStrategyRetriever testing_getWrapStrategyRetriever() {
    return wrapStrategyRetriever;
  }

  @SuppressWarnings("VisibleForTestingUsed")
  @VisibleForTesting
  void testing_setContentHelper(ContentHelper contentHelper) {
    commonDisplayInfoWrapper.testing_setContentHelper(contentHelper);
  }
}
