/*
 * Copyright 2024 Google Inc.
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
package com.google.android.accessibility.braille.brltty;

import androidx.annotation.Nullable;
import android.util.Log;
import com.google.android.accessibility.braille.common.FakeBrailleDisplayController;
import com.google.android.apps.common.proguard.UsedByNative;
import com.google.common.base.Preconditions;
import java.io.IOException;
import java.util.ArrayDeque;

/** A Helper class helps hid_android.c in brltty access BrailleDisplayController APIs. */
public class BrlttyHidNativeHelper {
  private static final String TAG = "HidAndroidHelper";
  private static final Object lock = new Object();
  private static final ArrayDeque<byte[]> inputReports = new ArrayDeque<>();

  @SuppressWarnings("NonFinalStaticField")
  @Nullable
  private static byte[] reportDescriptor;

  @SuppressWarnings("NonFinalStaticField")
  @Nullable
  private static FakeBrailleDisplayController brailleDisplayController;

  // Methods used by HidConnection.java

  /** Prepares a report descriptor for BRLTTY to interpret the format of incoming reports. */
  public static void setup(FakeBrailleDisplayController controller, byte[] descriptor) {
    brailleDisplayController = controller;
    reportDescriptor = descriptor;
  }

  /**
   * Saves the Braille display's input to the input cache. The cache will be read very soon after
   * when hid_android.c is notified that some input is available and it calls readBrailleDisplay.
   */
  public static void onInput(byte[] input) {
    synchronized (lock) {
      inputReports.add(input);
    }
  }

  // Methods used by hid_android.c with JNI

  @UsedByNative("hid_android.c")
  @Nullable
  public static byte[] getReportDescriptor() {
    return reportDescriptor;
  }

  /** Outputs text to the braille display. */
  @UsedByNative("hid_android.c")
  public static void writeBrailleDisplay(byte[] buffer) {
    Preconditions.checkNotNull(brailleDisplayController);
    try {
      brailleDisplayController.write(buffer);
    } catch (IOException e) {
      Log.e(TAG, "Error writing to BrailleDisplayController: " + e);
      brailleDisplayController.disconnect();
    }
  }

  @UsedByNative("hid_android.c")
  @Nullable
  /** Reads braille display's input from cache saved in {@link #onInput}. */
  public static byte[] readBrailleDisplay() {
    synchronized (lock) {
      if (inputReports.isEmpty()) {
        return null;
      }
      return inputReports.pop();
    }
  }
}
