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
import androidx.annotation.Nullable;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import android.text.TextUtils;
import android.util.SparseArray;
import androidx.annotation.VisibleForTesting;
import com.google.android.accessibility.utils.Filter;
import com.google.android.accessibility.utils.RectUtils;
import com.google.android.gms.vision.Detector;
import com.google.android.gms.vision.Frame;
import com.google.android.gms.vision.text.Text;
import com.google.android.gms.vision.text.TextBlock;
import com.google.android.gms.vision.text.TextRecognizer;
import com.google.android.libraries.accessibility.utils.bitmap.BitmapUtils;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * OCRController holds onto an OCR TextRecognizer, and allows consumers to perform OCR based on a
 * list of {@link AccessibilityNodeInfoCompat}, and a screencapture {@link Bitmap}.
 */
public class OCRController {

  // TODO: Consider localizing wordSeparator and paragraphSeparator as a string resource
  // (or, retrieve separators based on user's default language from system).
  public static final String WORD_SEPARATOR = " ";
  public static final String PARAGRAPH_SEPARATOR = "\n";

  private static final Comparator<TextBlock> TEXT_BLOCK_POSITION_COMPARATOR =
      comparing(TextBlock::getBoundingBox, RectUtils.RECT_POSITION_COMPARATOR);

  private final OCRListener ocrListener;
  private final Handler handler;
  private final Detector<TextBlock> detector;

  public OCRController(Context context, OCRListener ocrListener) {
    this(
        new Handler(Looper.getMainLooper()),
        ocrListener,
        new TextRecognizer.Builder(context).build());
  }

  public OCRController(Handler handler, OCRListener ocrListener, Detector<TextBlock> detector) {
    this.ocrListener = ocrListener;
    this.handler = handler;
    this.detector = detector;
  }

  /** Release the detector. Invoke this method when you no longer want to use OCRController. */
  public void shutdown() {
    detector.release();
  }

  /**
   * Recognize text in all the nodes by performing OCR on the cropped screenshots of all the nodes,
   * and save the resulting TextBlocks in each node.
   *
   * @param image The {@link Bitmap} containing the screenshot.
   * @param ocrInfos Provides some information of {@link AccessibilityNodeInfoCompat} nodes
   *     representing the on-screen {@link android.view.View}s whose screenshots we want to extract
   *     text from using OCR. Caller retains responsibility to recycle them.
   */
  public void recognizeTextForNodes(
      Bitmap image,
      List<OCRInfo> ocrInfos,
      Rect selectionBounds,
      Filter<AccessibilityNodeInfoCompat> isImageFilter) {
    new Thread(
            () -> {
              // Add recognized text to HashMap instead of mutating the nodes inside the thread.
              HashMap<OCRInfo, SparseArray<TextBlock>> textBlocksMap = new HashMap<>();
              for (OCRInfo orcInfo : ocrInfos) {
                AccessibilityNodeInfoCompat node = orcInfo.getNode();
                if (isImageFilter.accept(node)) {
                  Rect nodeBounds = new Rect();
                  orcInfo.getBoundsInScreenForOCR(nodeBounds);

                  // TODO: Makes cropped bitmap an input-argument to
                  // recognizeTextForNodes(), because icon-recognition-library will need to analyze
                  // the same screenshots.
                  Bitmap croppedBitmap = BitmapUtils.cropBitmap(image, nodeBounds);
                  if (croppedBitmap == null) {
                    continue;
                  }
                  // TODO: If a bit faster, optimize this using the other setBitmap() that takes a
                  // ByteBuffer
                  Frame nodeFrame = new Frame.Builder().setBitmap(croppedBitmap).build();
                  SparseArray<TextBlock> textBlocks = detector.detect(nodeFrame);

                  textBlocksMap.put(orcInfo, textBlocks);
                }
              }

              handler.post(
                  () -> {
                    // Now that we're back in the main thread, mutate the ocrInfos using the
                    // HashMap.
                    boolean isOCRInAction = false;
                    for (OCRInfo orcInfo : ocrInfos) {
                      List<TextBlock> textBlocks =
                          ocrResultToSortedList(textBlocksMap.get(orcInfo));
                      orcInfo.setTextBlocks(textBlocks);

                      if (!TextUtils.isEmpty(getTextFromBlocks(textBlocks))) {
                        isOCRInAction = true;
                      }
                    }
                    if (isOCRInAction) {
                      ocrListener.onOCRStarted();
                    }

                    // If the user selected only one node, and the node has non-null ocrTextBlocks,
                    // then filter the textblocks down to only textBlocks that instersect with
                    // selectionBounds
                    if (selectionBounds != null
                        && ocrInfos.size() == 1
                        && ocrInfos.get(0).getTextBlocks() != null) {
                      filterTextBlocks(ocrInfos.get(0), selectionBounds);
                    }

                    ocrListener.onOCRFinished(ocrInfos);
                  });
            })
        .start();
  }

  private static void filterTextBlocks(OCRInfo ocrInfo, Rect selectionBounds) {
    Rect nodeBounds = new Rect();
    ocrInfo.getBoundsInScreenForOCR(nodeBounds);

    List<TextBlock> textBlocks = ocrInfo.getTextBlocks();
    if (textBlocks == null) {
      return;
    }
    Set<TextBlock> toBeRemoved = new HashSet<>();
    for (int i = textBlocks.size() - 1; i >= 0; i--) {
      TextBlock block = textBlocks.get(i);
      Rect textBlockBox = block.getBoundingBox();
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
      Text textBlock = textBlocks.get(i);

      for (Text line : textBlock.getComponents()) {
        for (Text word : line.getComponents()) {
          text.append(word.getValue().trim()).append(WORD_SEPARATOR);
        }
      }

      // TODO: Can we just assume all TextBlocks aren't empty (i.e. always contain Lines)?
      // If this TextBlock isn't empty (i.e. contains Lines), replace the just-added wordSeparator
      // with a paragraphSeparator (if this isn't the last textblock) or remove the just-added
      // wordSeparator (if this is the last textblock).
      if (!textBlock.getComponents().isEmpty()) {
        if (i < textBlocks.size() - 1) {
          text.replace(text.length() - WORD_SEPARATOR.length(), text.length(), PARAGRAPH_SEPARATOR);
        } else {
          text.replace(text.length() - WORD_SEPARATOR.length(), text.length(), "");
        }
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
  public static List<TextBlock> ocrResultToSortedList(@Nullable SparseArray<TextBlock> textBlocks) {
    if (textBlocks == null) {
      return null;
    }
    List<TextBlock> result = new ArrayList<>();
    for (int i = 0; i < textBlocks.size(); i++) {
      // Use valueAt() for iteration with index.
      TextBlock item = textBlocks.valueAt(i);
      if (item != null) {
        result.add(item);
      }
    }

    if (result.isEmpty()) {
      return null;
    }

    Collections.sort(result, TEXT_BLOCK_POSITION_COMPARATOR);
    return result;
  }

  /**
   * Listener callback interface for performing OCR.
   *
   * @see OCRController#recognizeTextForNodes(Bitmap, List, Rect, Filter)
   */
  public interface OCRListener {

    /** Invoked when OCR started. */
    void onOCRStarted();

    /**
     * Invoked when OCR completes.
     *
     * @param ocrResults The {@link List} of {@link AccessibilityNodeInfoCompat} and their
     *     ocrTextBlocks which sets with {@link SparseArray}s of {@link TextBlock}s that contain the
     *     lines, words, and bounding boxes detected by OCR. Note that a SparseArray may be empty if
     *     OCR did not detect any text. For reference, see:
     *     https://developers.google.com/android/reference/com/google/android/gms/vision/text/TextBlock
     */
    void onOCRFinished(List<OCRInfo> ocrResults);
  }
}
