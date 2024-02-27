package com.google.android.accessibility.braille.brailledisplay.controller;

import static com.google.android.accessibility.braille.common.translate.EditBufferUtils.NO_CURSOR;
import static java.lang.Math.max;
import static java.lang.Math.min;
import static java.util.Arrays.stream;

import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.SpannedString;
import android.text.style.ClickableSpan;
import android.util.Range;
import androidx.annotation.Nullable;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import com.google.android.accessibility.braille.brailledisplay.BrailleDisplayLog;
import com.google.android.accessibility.braille.brailledisplay.controller.DisplayInfo.Source;
import com.google.android.accessibility.braille.brailledisplay.controller.wrapping.WrapStrategy;
import com.google.android.accessibility.braille.interfaces.BrailleCharacter;
import com.google.android.accessibility.braille.interfaces.BrailleWord;
import com.google.android.accessibility.braille.interfaces.SelectionRange;
import com.google.android.accessibility.braille.translate.TranslationResult;
import com.google.common.collect.ImmutableList;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

// TODO: Make this class more readable.
/** Helps to generate what to show on a braille display. */
public class ContentHelper {
  private static final String TAG = "ContentHelper";
  // Dot pattern used to overlay characters under a selection.
  private static final BrailleCharacter SELECTION_DOTS = new BrailleCharacter("78");
  private int numTextCells;

  /**
   * Cursor position last passed to the translate method of the translator. We use this because it
   * is more reliable than the position maps inside contracted words. In the common case where there
   * is just one selection/focus on the display at the same time, this gives better results.
   * Otherwise, we fall back on the position map, whic is also used for keeping the pan position.
   */
  private TranslationResult currentTranslationResult;
  /** Display content without overlays for cursors, focus etc. */
  private BrailleWord brailleContent = new BrailleWord();
  /** Braille content, potentially with dots overlaid for cursors and focus. */
  private BrailleWord overlaidBrailleContent = brailleContent;

  private CharSequence originalText;
  private boolean isSplitParagraphs;
  private final TranslatorManager translatorManager;
  private final WrapStrategyRetriever wrapStrategyRetriever;

  /** Callback for ContentHelper. */
  interface WrapStrategyRetriever {
    WrapStrategy getWrapStrategy();
  }

  public ContentHelper(
      TranslatorManager translatorManager, WrapStrategyRetriever wrapStrategyRetriever) {
    this.translatorManager = translatorManager;
    this.wrapStrategyRetriever = wrapStrategyRetriever;
  }

  public ContentHelper(TranslatorManager translatorManager, WrapStrategy wrapStrategy) {
    this(translatorManager, () -> wrapStrategy);
  }

  /** Sets the number of text cells. */
  public void setTextCells(int numTextCell) {
    this.numTextCells = numTextCell;
  }

  /** Generates display info needed by a displayer. */
  public DisplayInfo generateDisplayInfo(
      CharSequence text, int panStrategy, boolean isSplitParagraphs) {
    originalText = text;
    TranslationResult translationResult =
        translatorManager.getOutputTranslator().translate(text, NO_CURSOR);
    SpannableStringBuilder spannableStringBuilder = new SpannableStringBuilder(text);
    Range<Integer> selectionRangeToTranslate = findSelectionRange(spannableStringBuilder);
    int cursorByteStart =
        textToDisplayPosition(translationResult, selectionRangeToTranslate.getLower());
    int cursorByteEnd =
        textToDisplayPosition(translationResult, selectionRangeToTranslate.getUpper());
    return generateDisplayInfo(
        panStrategy,
        new SelectionRange(cursorByteStart, cursorByteEnd),
        /* beginningOfInput= */ 0,
        /* endOfInput= */ 0,
        isSplitParagraphs,
        translationResult,
        Source.DEFAULT);
  }

  /** Generates display info needed by a displayer. */
  public DisplayInfo generateDisplayInfo(
      int panStrategy,
      SelectionRange selection,
      int beginningOfInput,
      int endOfInput,
      boolean isSplitParagraphs,
      TranslationResult translationResult,
      Source source) {
    WrapStrategy wrapStrategy = wrapStrategyRetriever.getWrapStrategy();
    int newPanPosition =
        getPanPosition(
            panStrategy,
            translationResult.text(),
            currentTranslationResult,
            translationResult,
            wrapStrategy.getDisplayStart());
    this.isSplitParagraphs = isSplitParagraphs;
    if (max(selection.start, selection.end) >= translationResult.cells().size()) {
      translationResult = TranslationResult.appendOneEmptyCell(translationResult);
    }
    currentTranslationResult = translationResult;
    brailleContent = translationResult.cells();
    overlaidBrailleContent = new BrailleWord(brailleContent);
    markSelection(selection.start, selection.end);
    wrapStrategy.setContent(isSplitParagraphs, translationResult, beginningOfInput, endOfInput);
    if (newPanPosition >= 0) {
      wrapStrategy.panTo(newPanPosition, /* fix= */ false);
    } else {
      wrapStrategy.panTo(selection.end, /* fix= */ true);
    }
    return getDisplayInfo(
        translationResult.text(),
        wrapStrategy.getDisplayStart(),
        wrapStrategy.getDisplayEnd(),
        currentTranslationResult.brailleToTextPositions(),
        source);
  }

  /**
   * Moves the display starting and ending positions to the left of the current content and returns
   * the display info.
   */
  public DisplayInfo panUp(Source source) {
    WrapStrategy wrapStrategy = wrapStrategyRetriever.getWrapStrategy();
    return wrapStrategy.panUp()
        ? getDisplayInfo(
            currentTranslationResult.text(),
            wrapStrategy.getDisplayStart(),
            wrapStrategy.getDisplayEnd(),
            currentTranslationResult.brailleToTextPositions(),
            source)
        : null;
  }

  /**
   * Moves the display starting and ending positions to the left of the current content and returns
   * the display info.
   */
  public DisplayInfo panDown(Source source) {
    WrapStrategy wrapStrategy = wrapStrategyRetriever.getWrapStrategy();
    return wrapStrategy.panDown()
        ? getDisplayInfo(
            currentTranslationResult.text(),
            wrapStrategy.getDisplayStart(),
            wrapStrategy.getDisplayEnd(),
            currentTranslationResult.brailleToTextPositions(),
            source)
        : null;
  }

  /** Retranslates the display info. This should only called when output language changes */
  public DisplayInfo retranslate() {
    if (originalText == null) {
      return null;
    }
    return generateDisplayInfo(originalText, CellsContent.PAN_KEEP, isSplitParagraphs);
  }

  /** Gets the text position of given byte position. */
  public int transferByteIndexToTextIndex(int bytePosition) {
    return displayToTextPosition(currentTranslationResult, bytePosition);
  }

  /** Converts to the byte index of whole content. */
  public int toWholeContentIndex(int routingPosition) {
    // Offset argument by pan position and make sure it is less than the next split position.
    // For example it's 4-cell braille display. "hello" will be displayed in two pages.
    // First page: "hell"
    // Second page: "o"
    // If user is at the second page and clicks on the second routing button, it's out of range.
    WrapStrategy wrapStrategy = wrapStrategyRetriever.getWrapStrategy();
    int offsetArgument = routingPosition + wrapStrategy.getDisplayStart();
    if (offsetArgument >= wrapStrategy.getDisplayEnd()) {
      // The event is outside the currently displayed content, drop the event.
      BrailleDisplayLog.i(
          "User clicked on outside of the currently displayed content: " + offsetArgument, null);
      return NO_CURSOR;
    }
    return offsetArgument;
  }

  private DisplayInfo getDisplayInfo(
      CharSequence text,
      int displayStart,
      int displayEnd,
      List<Integer> brailleToTextPositions,
      Source source) {
    if (text == null) {
      return null;
    }
    if (displayEnd < displayStart) {
      return null;
    }

    // Compute equivalent text and mapping.
    int textLeft =
        displayStart >= brailleToTextPositions.size()
            ? 0
            : brailleToTextPositions.get(displayStart);
    int textRight =
        displayEnd >= brailleToTextPositions.size()
            ? text.length()
            : brailleToTextPositions.get(displayEnd);
    // TODO: Prevent out of order brailleToTextPositions.
    if (textRight < textLeft) {
      textRight = textLeft;
    }
    StringBuilder newText = new StringBuilder(text.subSequence(textLeft, textRight));
    int[] trimmedBrailleToTextPositions = new int[displayEnd - displayStart];
    for (int i = 0; i < trimmedBrailleToTextPositions.length; i++) {
      if (displayStart + i < brailleToTextPositions.size()) {
        trimmedBrailleToTextPositions[i] = brailleToTextPositions.get(displayStart + i) - textLeft;
      } else {
        trimmedBrailleToTextPositions[i] = newText.length();
        newText.append(' ');
      }
    }

    // Store all data needed by refresh().
    BrailleWord displayedBraille = brailleContent.subword(displayStart, displayEnd);
    BrailleWord displayedOverlaidBraille;
    if (brailleContent != overlaidBrailleContent) {
      displayedOverlaidBraille = overlaidBrailleContent.subword(displayStart, displayEnd);
    } else {
      displayedOverlaidBraille = displayedBraille;
    }
    return DisplayInfo.builder()
        .setDisplayedBraille(ByteBuffer.wrap(displayedBraille.toByteArray()))
        .setDisplayedOverlaidBraille(ByteBuffer.wrap(displayedOverlaidBraille.toByteArray()))
        .setDisplayedText(newText.toString())
        .setDisplayedBrailleToTextPositions(
            stream(trimmedBrailleToTextPositions).boxed().collect(Collectors.toList()))
        .setBlink(!displayedBraille.equals(displayedOverlaidBraille))
        .setSource(source)
        .build();
  }

  /**
   * Gets the AccessibilityNodeInfoCompat of given byte position. When byte index is out of content
   * range or no matching accessibility node found will return null.
   */
  @Nullable
  public AccessibilityNodeInfoCompat getAccessibilityNodeInfo(int routingKeyIndex) {
    int byteIndexInWhole = toWholeContentIndex(routingKeyIndex);
    if (byteIndexInWhole == NO_CURSOR) {
      return null;
    }
    return DisplaySpans.getAccessibilityNodeFromPosition(
        displayToTextPosition(currentTranslationResult, byteIndexInWhole),
        currentTranslationResult.text());
  }

  /** Activates the clickable span in the index if possible. */
  public Optional<ClickableSpan[]> getClickableSpans(int byteIndex) {
    int textIndexInWhole = getTextCursorPosition(byteIndex);
    if (textIndexInWhole == NO_CURSOR) {
      return Optional.empty();
    }
    SpannableString spannableString = new SpannableString(currentTranslationResult.text());
    ClickableSpan[] spans =
        spannableString.getSpans(textIndexInWhole, textIndexInWhole, ClickableSpan.class);
    return Optional.of(spans);
  }

  /** Maps routing key to corresponding text cursor position. */
  public int getTextCursorPosition(int routingKeyIndex) {
    return transferByteIndexToTextIndex(toWholeContentIndex(routingKeyIndex));
  }

  private int findMatchingPanPosition(
      Spanned oldSpanned,
      Spanned newSpanned,
      TranslationResult oldTranslationResult,
      TranslationResult newTranslationResult,
      int oldDisplayPosition) {
    if (oldSpanned == null || newSpanned == null) {
      return NO_CURSOR;
    }
    // Map the current display start and past-the-end positions
    // to the corresponding input positions.
    int oldTextStart = displayToTextPosition(oldTranslationResult, oldDisplayPosition);
    int oldTextEnd = displayToTextPosition(oldTranslationResult, oldDisplayPosition + numTextCells);
    // Find the nodes that overlap with the display.
    AccessibilityNodeInfoCompat[] displayedNodes =
        oldSpanned.getSpans(oldTextStart, oldTextEnd, AccessibilityNodeInfoCompat.class);
    Arrays.sort(displayedNodes, new ByDistanceComparator(oldSpanned, oldTextStart));
    // Find corresponding node in new content.
    for (AccessibilityNodeInfoCompat oldNode : displayedNodes) {
      AccessibilityNodeInfoCompat newNode =
          (AccessibilityNodeInfoCompat) DisplaySpans.getEqualSpan(newSpanned, oldNode);
      if (newNode == null) {
        continue;
      }
      int oldDisplayStart =
          textToDisplayPosition(oldTranslationResult, oldSpanned.getSpanStart(oldNode));
      int newDisplayStart =
          textToDisplayPosition(newTranslationResult, newSpanned.getSpanStart(newNode));
      // TODO: If crashes happen here, return -1 when *DisplayStart == -1.
      // Offset position according to diff in node position.
      return oldDisplayPosition + (newDisplayStart - oldDisplayStart);
    }
    return NO_CURSOR;
  }

  private int getPanPosition(
      int panStrategy,
      CharSequence newText,
      TranslationResult oldTranslationResult,
      TranslationResult newTranslationResult,
      int oldDisplayPosition) {
    // Adjust the pan position according to the panning strategy.
    // Setting the position to -1 below means that the cursor position
    // returned by markCursor() will be used instead; if the pan
    // position is >= 0, then the cursor position will be ignored.
    // If the pan position is -1 and the cursor position is also -1
    // (no cursor), then the wrap strategy will reset the display to the
    // beginning of the line.
    int panPosition = NO_CURSOR;
    switch (panStrategy) {
      case CellsContent.PAN_RESET:
        panPosition = 0;
        break;
      case CellsContent.PAN_KEEP:
        if (oldTranslationResult != null && oldTranslationResult.text() != null) {
          // We don't align the display position to the size of the display in this case so that
          // content doesn't jump around on the dipslay if content before the current display
          // position changes size.
          panPosition =
              findMatchingPanPosition(
                  new SpannedString(oldTranslationResult.text()),
                  new SpannedString(newText),
                  oldTranslationResult,
                  newTranslationResult,
                  oldDisplayPosition);
        }
        break;
      case CellsContent.PAN_CURSOR:
        break;
      default:
        BrailleDisplayLog.e(TAG, "Unknown pan strategy: " + panStrategy);
    }
    return panPosition;
  }

  /** Marks selection spans. If start is equal to end, only mark in overlaid braille. */
  private void markSelection(int start, int end) {
    if (start == NO_CURSOR || end == NO_CURSOR) {
      return;
    }
    if (start == end) {
      overlaidBrailleContent.set(start, overlaidBrailleContent.get(start).union(SELECTION_DOTS));
    } else {
      for (int i = min(start, end); i < max(start, end) && i < overlaidBrailleContent.size(); ++i) {
        brailleContent.set(i, brailleContent.get(i).union(SELECTION_DOTS));
        overlaidBrailleContent.set(i, overlaidBrailleContent.get(i).union(SELECTION_DOTS));
      }
    }
  }

  private static Range<Integer> findSelectionRange(Spanned spanned) {
    if (spanned != null) {
      DisplaySpans.SelectionSpan[] selectionSpans =
          spanned.getSpans(0, spanned.length(), DisplaySpans.SelectionSpan.class);
      if (selectionSpans.length > 0) {
        return new Range<>(
            spanned.getSpanStart(selectionSpans[0]), spanned.getSpanEnd(selectionSpans[0]));
      }
    }
    return new Range<>(NO_CURSOR, NO_CURSOR);
  }

  private static int displayToTextPosition(
      TranslationResult translationResult, int displayPosition) {
    ImmutableList<Integer> posMap = translationResult.brailleToTextPositions();
    // Any position past-the-end of the position map maps to the
    // corresponding past-the-end position in the braille.
    if (displayPosition < 0) {
      return NO_CURSOR;
    } else if (displayPosition >= posMap.size()) {
      return translationResult.textToBraillePositions().size();
    }
    return posMap.get(displayPosition);
  }

  /** Returns braille character index of a text character index. May return {@link #NO_CURSOR}. */
  private static int textToDisplayPosition(TranslationResult translationResult, int textPosition) {
    ImmutableList<Integer> posMap = translationResult.textToBraillePositions();
    // Any position past-the-end of the position map maps to the
    // corresponding past-the-end position in the braille.
    if (textPosition < 0) {
      return NO_CURSOR;
    } else if (textPosition >= posMap.size()) {
      return translationResult.brailleToTextPositions().size();
    }
    return posMap.get(textPosition);
  }

  private static class ByDistanceComparator implements Comparator<AccessibilityNodeInfoCompat> {
    private final Spanned spanned;
    private final int start;

    public ByDistanceComparator(Spanned spannedArg, int startArg) {
      spanned = spannedArg;
      start = startArg;
    }

    @Override
    public int compare(AccessibilityNodeInfoCompat a, AccessibilityNodeInfoCompat b) {
      int aStart = spanned.getSpanStart(a);
      int bStart = spanned.getSpanStart(b);
      int aDist = Math.abs(start - aStart);
      int bDist = Math.abs(start - bStart);
      if (aDist != bDist) {
        return aDist - bDist;
      }
      // They are on the same distance, compare by length.
      int aLength = aStart + spanned.getSpanEnd(a);
      int bLength = bStart + spanned.getSpanEnd(b);
      return aLength - bLength;
    }
  }
}
