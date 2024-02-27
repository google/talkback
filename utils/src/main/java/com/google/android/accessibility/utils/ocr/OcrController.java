/*
 * Copyright (C) 2021 Google Inc.
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

package com.google.android.accessibility.utils.ocr;

import static java.util.Comparator.comparing;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import com.google.android.accessibility.utils.Filter;
import com.google.android.accessibility.utils.RectUtils;
import com.google.android.libraries.accessibility.utils.bitmap.BitmapUtils;
import com.google.android.libraries.accessibility.utils.log.LogUtils;
import com.google.common.collect.ImmutableList;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text.Element;
import com.google.mlkit.vision.text.Text.Line;
import com.google.mlkit.vision.text.Text.TextBlock;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * OcrController holds onto an OCR TextRecognizer, and allows consumers to perform OCR based on a
 * list of {@link AccessibilityNodeInfoCompat}, and a screencapture {@link Bitmap}.
 */
public class OcrController {

  // TODO: Consider localizing wordSeparator and paragraphSeparator as a string resource
  // (or, retrieve separators based on user's default language from system).
  public static final String WORD_SEPARATOR = " ";
  public static final String PARAGRAPH_SEPARATOR = "\n";
  private static final long DELAY_PARSER_OCR_RESULT_MS = 50;
  /** Maximal waiting time of OCR results. */
  private static final long OCR_RESULT_MAX_WAITING_TIME_MS = 5000;

  public static final Comparator<TextBlock> TEXT_BLOCK_POSITION_COMPARATOR =
      comparing(
          textBlock -> {
            @Nullable Rect boundingBox = textBlock.getBoundingBox();
            return boundingBox == null ? new Rect() : boundingBox;
          },
          RectUtils.RECT_POSITION_COMPARATOR);
  private static final String TAG = "OcrController";

  private final OcrListener ocrListener;
  private final Handler handler;
  // TextRecognizer (MlKitContext) may not be ready when the device just boots completely, so this
  // recognizer can't be initialized in the constructor.
  @Nullable private TextRecognizer recognizer;

  public OcrController(Context context, OcrListener ocrListener) {
    this(new Handler(Looper.getMainLooper()), ocrListener, /* recognizer= */ null);
  }

  public OcrController(
      Handler handler, OcrListener ocrListener, @Nullable TextRecognizer recognizer) {
    this.ocrListener = ocrListener;
    this.handler = handler;
    this.recognizer = recognizer;
  }

  /**
   * Closes the detector and releases its resources. Invokes this method when you no longer want to
   * use OcrController.
   */
  public void shutdown() {
    if (recognizer != null) {
      recognizer.close();
      recognizer = null;
    }
  }

  /**
   * Recognize text in all the nodes by performing OCR on the cropped screenshots of all the nodes,
   * and save the resulting TextBlocks in each node.
   *
   * @param image The {@link Bitmap} containing the screenshot.
   * @param ocrInfos Provides some information of {@link AccessibilityNodeInfoCompat} nodes
   *     representing the on-screen {@link android.view.View}s whose screenshots we want to extract
   *     text from using OCR.
   * @param filter Only the nodes which are accepted by the filter will be recognized.
   */
  public void recognizeTextForNodes(
      Bitmap image,
      List<OcrInfo> ocrInfos,
      Rect selectionBounds,
      Filter<AccessibilityNodeInfoCompat> filter) {
    if (recognizer == null) {
      try {
        recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
      } catch (IllegalStateException e) {
        LogUtils.w(TAG, "Fail to get TextRecognizer.");
        // MlKitContext is not ready.
        ocrListener.onOcrFinished(ocrInfos);
        return;
      }
    }

    new Thread(
            new OcrRunnable(
                handler, ocrListener, recognizer, image, ocrInfos, selectionBounds, filter))
        .start();
  }

  private static void filterTextBlocks(OcrInfo ocrInfo, Rect selectionBounds) {
    Rect nodeBounds = new Rect();
    ocrInfo.getBoundsInScreenForOcr(nodeBounds);

    List<TextBlock> textBlocks = ocrInfo.getTextBlocks();
    if (textBlocks == null) {
      return;
    }
    Set<TextBlock> toBeRemoved = new HashSet<>();
    for (int i = textBlocks.size() - 1; i >= 0; i--) {
      TextBlock block = textBlocks.get(i);
      @Nullable Rect textBlockBox = block.getBoundingBox();
      if (textBlockBox == null) {
        toBeRemoved.add(block);
        continue;
      }

      textBlockBox.offset(nodeBounds.left, nodeBounds.top);

      if (!Rect.intersects(textBlockBox, selectionBounds)) {
        toBeRemoved.add(block);
      }
    }
    textBlocks.removeAll(toBeRemoved);
  }

  // TODO: Consolidate logic from getTextFromBlocks and createWordBoundsMapping. Consider using
  // SpannableStringBuilder#setSpan to attach TextLocationSpans (containing Rects) to each element
  // from OCR TextBlocks.

  /**
   * Combine all the text from the textBlocks into one string, concatenating words and lines with
   * wordSeparators (likely spaces), and TextBlocks with paragraphSeparators (likely newlines).
   *
   * @param textBlocks The TextBlocks that contain the resulting text from OCR.
   * @return A string containing the combined text from all the TextBlocks.
   */
  @Nullable
  public static String getTextFromBlocks(@Nullable List<TextBlock> textBlocks) {
    if (textBlocks == null || textBlocks.isEmpty()) {
      return null;
    }

    StringBuilder text = new StringBuilder();

    for (int i = 0; i < textBlocks.size(); i++) {
      TextBlock textBlock = textBlocks.get(i);

      for (Line line : textBlock.getLines()) {
        for (Element word : line.getElements()) {
          text.append(word.getText().trim()).append(WORD_SEPARATOR);
        }
      }

      if (textBlock.getLines().isEmpty() || TextUtils.isEmpty(text)) {
        continue;
      }

      // If this TextBlock isn't empty (i.e. contains Lines), replace the just-added wordSeparator
      // with a paragraphSeparator (if this isn't the last textblock) or remove the just-added
      // wordSeparator (if this is the last textblock).
      if (i < textBlocks.size() - 1) {
        text.replace(text.length() - WORD_SEPARATOR.length(), text.length(), PARAGRAPH_SEPARATOR);
      } else {
        text.replace(text.length() - WORD_SEPARATOR.length(), text.length(), "");
      }
    }

    return text.toString();
  }

  /**
   * Interprets OCR result into a list of {@link TextBlock} sorted by {@link
   * #TEXT_BLOCK_POSITION_COMPARATOR}.
   */
  @Nullable
  @VisibleForTesting
  public static List<TextBlock> ocrResultToSortedList(@Nullable List<TextBlock> textBlocks) {
    if (textBlocks == null) {
      return null;
    }
    List<TextBlock> result = new ArrayList<>();
    for (TextBlock item : textBlocks) {
      if (item == null || item.getBoundingBox() == null) {
        continue;
      }
      result.add(item);
    }

    if (result.isEmpty()) {
      return null;
    }

    result.sort(TEXT_BLOCK_POSITION_COMPARATOR);
    return result;
  }

  /**
   * Listener callback interface for performing OCR.
   *
   * @see OcrController#recognizeTextForNodes(Bitmap, List, Rect, Filter)
   */
  public interface OcrListener {

    /** Invoked when OCR started. */
    void onOcrStarted();

    /**
     * Invoked when OCR completes.
     *
     * @param ocrResults The {@link List} of {@link AccessibilityNodeInfoCompat} and their
     *     ocrTextBlocks which sets with {@link List}s of {@link TextBlock}s that contain the lines,
     *     words, and bounding boxes detected by OCR. For reference, see:
     *     https://developers.google.com/android/reference/com/google/mlkit/vision/text/Text.TextBlock
     */
    void onOcrFinished(List<OcrInfo> ocrResults);
  }

  /** Performs OCR and gets text blocks from OCR results. */
  @VisibleForTesting
  static class OcrRunnable implements Runnable {

    private final Handler handler;
    private final OcrListener ocrListener;
    private final TextRecognizer recognizer;
    private final Bitmap screenshot;
    private final List<OcrInfo> ocrInfos;
    @Nullable private final Rect selectionBounds;
    private final Filter<AccessibilityNodeInfoCompat> filter;

    public OcrRunnable(
        Handler handler,
        OcrListener ocrListener,
        TextRecognizer recognizer,
        Bitmap screenshot,
        List<OcrInfo> ocrInfos,
        @Nullable Rect selectionBounds,
        Filter<AccessibilityNodeInfoCompat> filter) {
      this.handler = handler;
      this.ocrListener = ocrListener;
      this.recognizer = recognizer;
      this.screenshot = screenshot;
      this.ocrInfos = ocrInfos;
      this.selectionBounds = selectionBounds;
      this.filter = filter;
    }

    @Override
    public void run() {
      // Add recognized text to HashMap instead of mutating the nodes inside the thread.
      ConcurrentHashMap<OcrInfo, List<TextBlock>> textBlocksMap = new ConcurrentHashMap<>();
      ParserResultRunnable runnable =
          new ParserResultRunnable(handler, ocrInfos, textBlocksMap, selectionBounds, ocrListener);

      for (OcrInfo ocrInfo : ocrInfos) {
        AccessibilityNodeInfoCompat node = ocrInfo.getNode();
        if (filter.accept(node)) {
          Rect nodeBounds = new Rect();
          ocrInfo.getBoundsInScreenForOcr(nodeBounds);

          if (screenshot.isRecycled()) {
            LogUtils.w(TAG, "Screenshot has been recycled.");
            break;
          }

          Bitmap croppedBitmap;
          try {
            croppedBitmap = BitmapUtils.cropBitmap(screenshot, nodeBounds);
          } catch (IllegalArgumentException e) {
            LogUtils.w(TAG, e.getMessage() == null ? "Fail to crop screenshot." : e.getMessage());
            continue;
          }

          if (croppedBitmap == null) {
            continue;
          }

          runnable.addRecognitionCount();
          recognizer
              .process(InputImage.fromBitmap(croppedBitmap, /* rotationDegrees= */ 0))
              .addOnSuccessListener(text -> textBlocksMap.put(ocrInfo, text.getTextBlocks()))
              .addOnFailureListener(
                  exception -> {
                    LogUtils.w(TAG, "Fail to recognize text. errMsg=" + exception.getMessage());
                    textBlocksMap.put(ocrInfo, ImmutableList.of());
                  });
        }
      }
      handler.postDelayed(runnable, DELAY_PARSER_OCR_RESULT_MS);
    }
  }

  /** Parsers OCR results and notifies caller that OCR is finished. */
  @VisibleForTesting
  static class ParserResultRunnable implements Runnable {

    private final Handler handler;
    private final List<OcrInfo> ocrInfos;
    @Nullable private final Rect selectionBounds;
    private final ConcurrentHashMap<OcrInfo, List<TextBlock>> textBlocksMap;
    private final OcrListener ocrListener;
    private long waitingTimeMs;
    private int recognitionNumber = 0;

    public ParserResultRunnable(
        Handler handler,
        List<OcrInfo> ocrInfos,
        ConcurrentHashMap<OcrInfo, List<TextBlock>> textBlocksMap,
        @Nullable Rect selectionBounds,
        OcrListener ocrListener) {
      this.handler = handler;
      this.ocrInfos = ocrInfos;
      this.textBlocksMap = textBlocksMap;
      this.selectionBounds = selectionBounds;
      this.ocrListener = ocrListener;
      this.waitingTimeMs = 0;
    }

    /** Checks if all OCR tasks for the nodes in ocrInfos are finished. */
    public boolean isOcrFinished() {
      return textBlocksMap.size() == recognitionNumber;
    }

    public synchronized void addRecognitionCount() {
      recognitionNumber++;
    }

    @Override
    public void run() {
      // If there are unfinished recognition tasks, waiting DELAY_PARSER_OCR_RESULT_MS then checking
      // again.
      if (!isOcrFinished() && waitingTimeMs < OCR_RESULT_MAX_WAITING_TIME_MS) {
        waitingTimeMs = waitingTimeMs + DELAY_PARSER_OCR_RESULT_MS;
        LogUtils.v(TAG, "waiting for OCR result... timeout=" + waitingTimeMs);
        handler.postDelayed(this, DELAY_PARSER_OCR_RESULT_MS);
        return;
      }

      boolean isOcrInAction = false;

      // Copies OCR results which are represented by a list of text blocks (a.k.a. paragraphs) into
      // nodes that requested OCR.
      for (OcrInfo ocrInfo : ocrInfos) {
        List<TextBlock> textBlocks = ocrResultToSortedList(textBlocksMap.get(ocrInfo));
        ocrInfo.setTextBlocks(textBlocks);

        // It's unnecessary to check all OCR results. isOCRInAction will be true as long as
        // there is a non-empty OCR result.
        if (!isOcrInAction && !TextUtils.isEmpty(getTextFromBlocks(textBlocks))) {
          isOcrInAction = true;
        }
      }

      if (isOcrInAction) {
        ocrListener.onOcrStarted();
      }

      // If the user selected only one node, and the node has non-null ocrTextBlocks, then
      // filter the textblocks down to only textBlocks that intersect with selectionBounds
      if (selectionBounds != null
          && ocrInfos.size() == 1
          && ocrInfos.get(0).getTextBlocks() != null) {
        filterTextBlocks(ocrInfos.get(0), selectionBounds);
      }

      ocrListener.onOcrFinished(ocrInfos);
    }
  }
}
