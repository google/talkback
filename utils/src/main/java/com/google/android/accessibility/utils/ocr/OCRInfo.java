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

import android.graphics.Rect;
import androidx.annotation.Nullable;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import com.google.android.gms.vision.text.TextBlock;
import java.util.List;

/**
 * Stores some node information for performing OCR on {@link AccessibilityNodeInfoCompat}, and OCR
 * result.
 */
public class OCRInfo {

  public final AccessibilityNodeInfoCompat node;
  @Nullable public List<TextBlock> textBlocks;

  /** Caller retains responsibility to recycle the node. */
  public OCRInfo(AccessibilityNodeInfoCompat node) {
    this.node = node;
  }

  public AccessibilityNodeInfoCompat getNode() {
    return node;
  }

  public void getBoundsInScreenForOCR(Rect bounds) {
    node.getBoundsInScreen(bounds);
  }

  public void setTextBlocks(@Nullable List<TextBlock> textBlocks) {
    this.textBlocks = textBlocks;
  }

  @Nullable
  public List<TextBlock> getTextBlocks() {
    return textBlocks;
  }
}
