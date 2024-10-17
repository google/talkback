/*
 * Copyright (C) 2023 Google Inc.
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

package com.google.android.accessibility.braille.brailledisplay.analytics;

import android.content.Context;
import com.google.android.accessibility.braille.common.translate.BrailleLanguages.Code;

/** Stub braille display analytics. */
public class BrailleDisplayAnalytics {
  private static BrailleDisplayAnalytics instance;

  public static BrailleDisplayAnalytics getInstance(Context context) {
    if (instance == null) {
      instance = new BrailleDisplayAnalytics(context.getApplicationContext());
    }
    return instance;
  }

  private BrailleDisplayAnalytics(Context context) {}

  public void logStartedEvent(
      String driverCode,
      String deviceName,
      Code inputCode,
      Code outputCode,
      boolean inputContracted,
      boolean outputContracted,
      boolean supportHid,
      boolean bluetoothDevice) {}

  public void logTypingBrailleCharacter(int count) {}

  public void logReadingBrailleCharacter(int count) {}

  public void logBrailleInputCodeSetting(Code code, boolean contracted) {}

  public void logBrailleOutputCodeSetting(Code code, boolean contracted) {}

  public void logChangeTypingMode(boolean toPhysical) {}

  public void logAutoConnectSetting(boolean enabled) {}

  public void logEnablerSetting(boolean enabled) {}

  public void logBrailleCommand(int command) {}

  public void logStartToEstablishRfcommConnection() {}

  public void logStartToEstablishHidConnection() {}

  public void logConnectionReset() {}

  public void logStartToConnectToBrailleDisplay() {}

  public void logBlinkRate(int millisecond) {}

  public void logReversePanningKey(boolean enabled) {}

  public void logTimedMessageDurationMs(int millisecond) {}
}
