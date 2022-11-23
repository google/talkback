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

package com.google.android.accessibility.talkback.imagecaption;

import android.accessibilityservice.AccessibilityService;
import android.graphics.Bitmap;
import android.text.TextUtils;
import androidx.annotation.NonNull;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import com.google.android.accessibility.utils.Filter;
import com.google.android.accessibility.utils.StringBuilderUtils;
import com.google.android.accessibility.utils.ocr.OcrController;
import com.google.android.accessibility.utils.ocr.OcrController.OcrListener;
import com.google.android.accessibility.utils.ocr.OcrInfo;
import java.util.ArrayList;
import java.util.List;

/**
 * A {@link CaptionRequest} for performing OCR (optical character recognition) to recognize text
 * from the screenshot.
 */
public class CharacterCaptionRequest extends CaptionRequest implements OcrListener {

  private final OcrController ocrController;
  private final Bitmap screenCapture;

  /** This object takes ownership of node, caller should not recycle. */
  public CharacterCaptionRequest(
      int requestId,
      AccessibilityService service,
      AccessibilityNodeInfoCompat node,
      Bitmap screenCapture,
      @NonNull OnFinishListener onFinishListener,
      @NonNull OnErrorListener onErrorListener,
      boolean isUserRequested) {
    super(requestId, node, onFinishListener, onErrorListener, isUserRequested);
    ocrController = new OcrController(service, this);
    this.screenCapture = screenCapture;
  }

  /**
   * Captures screen and performs ocr(optical character recognition) to recognize text for the given
   * node.
   */
  @Override
  public void perform() {
    final List<OcrInfo> ocrInfos = new ArrayList<>();
    ocrInfos.add(new OcrInfo(AccessibilityNodeInfoCompat.obtain(node)));
    ocrController.recognizeTextForNodes(
        screenCapture, ocrInfos, /* selectionBounds= */ null, new Filter.NodeCompat(node -> true));

    runTimeoutRunnable();
  }

  @Override
  public void onOcrStarted() {}

  @Override
  public void onOcrFinished(List<OcrInfo> ocrResults) {
    stopTimeoutRunnable();
    if (ocrResults == null) {
      onError(ERROR_IMAGE_CAPTION_NO_RESULT);
      return;
    }

    List<CharSequence> texts = new ArrayList<>();
    for (OcrInfo ocrResult : ocrResults) {
      String text = OcrController.getTextFromBlocks(ocrResult.getTextBlocks());
      if (TextUtils.isEmpty(text)) {
        continue;
      }
      texts.add(text);
    }

    onCaptionFinish(StringBuilderUtils.getAggregateText(texts));
  }
}
