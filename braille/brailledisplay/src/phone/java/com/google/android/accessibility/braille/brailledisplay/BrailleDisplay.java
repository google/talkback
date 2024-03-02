package com.google.android.accessibility.braille.brailledisplay;

import android.accessibilityservice.AccessibilityService;
import android.view.accessibility.AccessibilityEvent;
import com.google.android.accessibility.braille.brailledisplay.controller.BdController;
import com.google.android.accessibility.braille.brailledisplay.platform.BrailleDisplayManager;
import com.google.android.accessibility.braille.brltty.BrlttyEncoder;
import com.google.android.accessibility.braille.brltty.Encoder;
import com.google.android.accessibility.braille.interfaces.BrailleDisplayForBrailleIme;
import com.google.android.accessibility.braille.interfaces.BrailleDisplayForTalkBack;
import com.google.android.accessibility.braille.interfaces.BrailleImeForBrailleDisplay;
import com.google.android.accessibility.braille.interfaces.TalkBackForBrailleDisplay;

/** Entry point between TalkBack and the braille display feature. */
public class BrailleDisplay implements BrailleDisplayForTalkBack, BrailleDisplayForBrailleIme {
  private static final String TAG = "BrailleDisplay";
  public static final Encoder.Factory ENCODER_FACTORY = new BrlttyEncoder.BrlttyFactory();

  private boolean isRunning;
  private BrailleDisplayManager brailleDisplayManager;
  private final BdController controller;
  private final AccessibilityService accessibilityService;

  /** Provides BrailleIme callbacks for BrailleDisplay. */
  public interface BrailleImeProvider {
    BrailleImeForBrailleDisplay getBrailleImeForBrailleDisplay();
  }

  public BrailleDisplay(
      AccessibilityService accessibilityService,
      TalkBackForBrailleDisplay talkBackForBrailleDisplay,
      BrailleImeProvider brailleImeProvider) {
    this.controller =
        new BdController(accessibilityService, talkBackForBrailleDisplay, brailleImeProvider);
    this.accessibilityService = accessibilityService;
    this.brailleDisplayManager =
        new BrailleDisplayManager(accessibilityService, controller, ENCODER_FACTORY);
    BrailleDisplayTalkBackSpeaker.getInstance().initialize(talkBackForBrailleDisplay);
  }

  /** Starts braille display. */
  @Override
  public void start() {
    BrailleDisplayLog.d(TAG, "start");
    brailleDisplayManager.setAccessibilityServiceContextProvider(() -> accessibilityService);
    brailleDisplayManager.onServiceStarted();
    isRunning = true;
  }

  /** Stops braille display. */
  @Override
  public void stop() {
    BrailleDisplayLog.d(TAG, "stop");
    brailleDisplayManager.onServiceStopped();
    brailleDisplayManager.setAccessibilityServiceContextProvider(() -> null);
    brailleDisplayManager = null;
    isRunning = false;
  }

  /** Notifies receiving accessibility event. */
  @Override
  public void onAccessibilityEvent(AccessibilityEvent accessibilityEvent) {
    if (isRunning) {
      brailleDisplayManager.onAccessibilityEvent(accessibilityEvent);
    }
  }

  @Override
  public void onReadingControlChanged(CharSequence readingControlDescription) {
    controller.onReadingControlChanged(readingControlDescription);
  }

  @Override
  public void switchBrailleDisplayOnOrOff() {
    controller.switchBrailleDisplayOnOrOff();
  }

  @Override
  public void onImeVisibilityChanged(boolean visible) {
    controller.getBrailleDisplayForBrailleIme().onImeVisibilityChanged(visible);
  }

  @Override
  public void showOnDisplay(ResultForDisplay result) {
    controller.getBrailleDisplayForBrailleIme().showOnDisplay(result);
  }

  @Override
  public boolean isBrailleDisplayConnectedAndNotSuspended() {
    return controller.getBrailleDisplayForBrailleIme().isBrailleDisplayConnectedAndNotSuspended();
  }

  @Override
  public void suspendInFavorOfBrailleKeyboard() {
    controller.getBrailleDisplayForBrailleIme().suspendInFavorOfBrailleKeyboard();
  }
}
