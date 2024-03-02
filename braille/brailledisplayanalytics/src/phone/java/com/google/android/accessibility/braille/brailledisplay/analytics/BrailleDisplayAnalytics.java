package com.google.android.accessibility.braille.brailledisplay.analytics;

import android.content.Context;
import com.google.android.accessibility.braille.common.DeviceProvider;
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
      String device,
      Code inputCode,
      Code outputCode,
      boolean inputContracted,
      boolean outputContracted,
      DeviceProvider<?> deviceProvider) {}

  public void logTypingBrailleCharacter(int count) {}

  public void logReadingBrailleCharacter(int count) {}

  public void logBrailleInputCodeSetting(Code code, boolean contracted) {}

  public void logBrailleOutputCodeSetting(Code code, boolean contracted) {}

  public void logWordWrappingSetting(boolean enabled) {}

  public void logStartToEstablishBluetoothConnection() {}

  public void logStartToConnectToBrailleDisplay() {}

  public void logChangeTypingMode(boolean toPhysical) {}

  public void logBrailleCommand(int command) {}

  public void logBlinkRate(int millisecond) {}

  public void logReversePanningKey(boolean enabled) {}

  public void logTimedMessageDurationMs(int millisecond) {}

  public void logAutoConnectSetting(boolean enabled) {}

  public void logEnablerSetting(boolean enabled) {}
}
