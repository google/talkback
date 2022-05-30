/*
 * Copyright 2021 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.android.accessibility.utils.screenunderstanding;

import android.graphics.Bitmap;
import android.graphics.Rect;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import java.util.Locale;

/** An interface for detecting screen annotations (including icons) from a specific screenshot. */
public interface ScreenAnnotationsDetector {

  /** Callback interface to be invoked when detecting screen annotations has finished. */
  interface ProcessScreenshotResultListener {
    /**
     * Invoked when detecting screen annotations from a given screenshot has finished.
     *
     * <p>When detecting screen annotations has successfully finished, invoke {@link
     * IconAnnotationsDetector#getIconLabel(Locale, AccessibilityNodeInfoCompat)} to get the label
     * of the detected icons for a given node.
     */
    void onDetectionFinished(boolean success);
  }

  /** Starts the screen annotations detector. */
  void start();

  /** Shuts down the screen annotations detector and releases resources. */
  void shutdown();

  /**
   * Asynchronously processes the provided {@code screenshot} to detect annotations.
   *
   * @param screenshot The screenshot of the entire screen for which annotations should be detected
   * @param listener The {@link ProcessScreenshotResultListener#onDetectionFinished(boolean)} will
   *     be invoked when detecting annotations from the given {@code screenshot} has finished
   */
  void processScreenshotAsync(Bitmap screenshot, ProcessScreenshotResultListener listener);

  /** Invoked when a UI change happens which changes the whole screen content. */
  void clearWholeScreenCache();

  /**
   * Invoked when a UI change happens which only changes the content inside the specific {@code
   * rect}.
   */
  void clearPartialScreenCache(Rect rect);
}
