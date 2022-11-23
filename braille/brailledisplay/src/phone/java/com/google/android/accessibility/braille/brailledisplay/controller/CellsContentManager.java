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

import static com.google.android.accessibility.braille.common.translate.EditBufferUtils.NO_CURSOR;
import static java.lang.Math.max;
import static java.lang.Math.min;

import android.content.Context;
import android.text.style.ClickableSpan;
import android.util.Range;
import androidx.annotation.VisibleForTesting;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import com.google.android.accessibility.braille.brailledisplay.BrailleDisplayLog;
import com.google.android.accessibility.braille.brailledisplay.analytics.BrailleDisplayAnalytics;
import com.google.android.accessibility.braille.brailledisplay.controller.DisplayInfo.Source;
import com.google.android.accessibility.braille.brailledisplay.controller.TranslatorManager.OutputCodeChangedListener;
import com.google.android.accessibility.braille.brailledisplay.controller.wrapping.EditorWordWrapStrategy;
import com.google.android.accessibility.braille.brailledisplay.controller.wrapping.WordWrapStrategy;
import com.google.android.accessibility.braille.brailledisplay.controller.wrapping.WrapStrategy;
import com.google.android.accessibility.braille.brltty.BrailleInputEvent;
import com.google.android.accessibility.braille.interfaces.SelectionRange;
import com.google.android.accessibility.braille.translate.TranslationResult;
import com.google.auto.value.AutoValue;
import com.google.common.base.Preconditions;
import com.google.common.collect.Iterables;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

/** Keeps track of the current display content and handles panning. */
public class CellsContentManager implements CellsContentConsumer {
  private static final String TAG = "CellsContentManager";

  // Duration for display-side cursor blink
  private static final int BLINK_MILLIS = 700;

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

  /**
   * Callback interface for notifying interested callers when the display is panned out of the
   * available content. A typical reaction to such an event would be to move focus to a different
   * area of the screen and display it.
   */
  public interface PanOverflowListener {
    void onPanLeftOverflow();

    void onPanRightOverflow();
  }

  /**
   * Listener for input events that also get information about the current display content and
   * position mapping for commands with a positional argument.
   */
  public interface MappedInputListener {
    /**
     * Handles an input {@code event} that was received when {@code content} was present on the
     * display.
     *
     * <p>If the input event has a positional argument, it is mapped according to the display pan
     * position in the content so that it corresponds to the character that the user touched.
     *
     * <p>{@code event} and {@code content} are owned by the caller and may not be referenced after
     * this method returns.
     *
     * <p>NOTE: Since the display is updated asynchronously, there is a chance that the actual
     * content on the display when the user invoked the command is different from {@code content}.
     */
    void onMappedInputEvent(BrailleInputEvent event);
  }

  /** Provides the active status of the IME. */
  public interface ImeStatusProvider {
    boolean isImeOpen();
  }

  /** A callback used to send braille display dots toward the hardware. */
  public interface DotDisplayer {
    void displayDots(byte[] patterns, CharSequence text, int[] brailleToTextPositions);
  }

  private final ImeStatusProvider imeStatusProvider;
  private final TranslatorManager translatorManager;
  private final PanOverflowListener panOverflowListener;
  private final MappedInputListener mappedInputEventListener;
  private final DotDisplayer inputEventListener;
  private final Pulser pulseHandler;
  private final ContentHelper contentHelper;
  private WrapStrategy editingWrapStrategy;
  private WrapStrategy preferredWrapStrategy;
  private boolean overlaysOn;
  private DisplayInfo displayInfo;
  private Range<Integer> holdingsRange;
  private List<Range<Integer>> onScreenRange;
  private Range<Integer> actionRange;
  private Context context;

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
      PanOverflowListener panOverflowListenerArg,
      MappedInputListener mappedInputEventListenerArg,
      DotDisplayer inputEventListenerArg) {
    this.context = context;
    this.imeStatusProvider = imeStatusProvider;
    translatorManager = translatorManagerArg;
    panOverflowListener = panOverflowListenerArg;
    mappedInputEventListener = mappedInputEventListenerArg;
    inputEventListener = inputEventListenerArg;
    pulseHandler = new Pulser(this::pulse, BLINK_MILLIS);
    contentHelper = new ContentHelper(translatorManagerArg, wrapStrategyRetriever);
  }

  public void start(int numTextCells) {
    preferredWrapStrategy = new WordWrapStrategy(numTextCells);
    editingWrapStrategy = new EditorWordWrapStrategy(numTextCells);
    contentHelper.setTextCells(numTextCells);
    translatorManager.addOnOutputTablesChangedListener(outputCodeChangedListener);
  }

  public void shutdown() {
    pulseHandler.cancelPulse();
    translatorManager.removeOnOutputTablesChangedListener(outputCodeChangedListener);
  }

  /**
   * Updates the display to reflect {@code content}. The {@code content} must not be modified after
   * this function is called.
   */
  @Override
  public void setContent(CellsContent content) {
    Preconditions.checkNotNull(content, "content can't be null");
    Preconditions.checkNotNull(content.getText(), "content text is null");
    displayInfo =
        contentHelper.generateDisplayInfo(
            content.getText(), content.getPanStrategy(), content.isSplitParagraphs());
    BrailleDisplayAnalytics.getInstance(context)
        .logReadingBrailleCharacter(displayInfo.displayedBraille().array().length);
    refresh(displayInfo);
  }

  @Override
  public AccessibilityNodeInfoCompat getAccessibilityNode(int byteIndex) {
    return contentHelper.getAccessibilityNodeInfo(byteIndex);
  }

  @Override
  public int getTextIndexInWhole(int routingKeyIndex) {
    return contentHelper.getTextCursorPosition(routingKeyIndex);
  }

  @Override
  public Optional<ClickableSpan[]> getClickableSpans(int routingKeyIndex) {
    return contentHelper.getClickableSpans(routingKeyIndex);
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
    displayInfo =
        contentHelper.generateDisplayInfo(
            CellsContent.PAN_CURSOR,
            selection,
            beginningOfInput,
            max(holdingsPosition, textCursorPosition),
            isMultiLine,
            overlayTranslationResult,
            Source.IME);
    refresh(displayInfo);
  }

  /** Maps the click index on the braille display to its index in the whole content. */
  public Cursor map(int positionOnBrailleDisplay) throws ExecutionException {
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

  public void onBrailleInputEvent(BrailleInputEvent event) {
    if (BrailleDisplayLog.DEBUG) {
      BrailleDisplayLog.v(TAG, "BrailleInputEvent: " + event);
    }
    int command = event.getCommand();
    if (command == BrailleInputEvent.CMD_NAV_PAN_UP) {
      panLeft();
    } else if (command == BrailleInputEvent.CMD_NAV_PAN_DOWN) {
      panRight();
    } else {
      mappedInputEventListener.onMappedInputEvent(event);
    }
  }

  private void panLeft() {
    DisplayInfo displayInfo = contentHelper.panLeft(this.displayInfo.source());
    if (displayInfo == null) {
      panOverflowListener.onPanLeftOverflow();
    } else {
      this.displayInfo = displayInfo;
      refresh(displayInfo);
    }
  }

  private void panRight() {
    DisplayInfo displayInfo = contentHelper.panRight(this.displayInfo.source());
    if (displayInfo == null) {
      panOverflowListener.onPanRightOverflow();
    } else {
      this.displayInfo = displayInfo;
      refresh(displayInfo);
    }
  }

  private void pulse() {
    overlaysOn = !overlaysOn;
    refresh(displayInfo);
  }

  private void refresh(DisplayInfo displayInfo) {
    if (displayInfo == null) {
      return;
    }
    byte[] toDisplay =
        overlaysOn
            ? displayInfo.displayedOverlaidBraille().array()
            : displayInfo.displayedBraille().array();
    inputEventListener.displayDots(
        toDisplay,
        displayInfo.displayedText(),
        displayInfo.displayedBrailleToTextPositions().stream()
            .mapToInt(Integer::intValue)
            .toArray());
    if (displayInfo.blink()) {
      pulseHandler.schedulePulse();
    } else {
      pulseHandler.cancelPulse();
      overlaysOn = true;
    }
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
          if (displayInfo.source() == Source.DEFAULT) {
            displayInfo = contentHelper.retranslate();
            refresh(displayInfo);
          }
        }
      };

  @VisibleForTesting
  ContentHelper.WrapStrategyRetriever testing_getWrapStrategyRetriever() {
    return wrapStrategyRetriever;
  }
}
