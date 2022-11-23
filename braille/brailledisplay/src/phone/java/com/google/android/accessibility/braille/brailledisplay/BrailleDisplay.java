package com.google.android.accessibility.braille.brailledisplay;

import android.content.Context;
import android.view.accessibility.AccessibilityEvent;
import com.google.android.accessibility.braille.brailledisplay.controller.BdController;
import com.google.android.accessibility.braille.brailledisplay.platform.BrailleDisplayManager;
import com.google.android.accessibility.braille.brltty.BrlttyEncoder;
import com.google.android.accessibility.braille.brltty.Encoder;
import com.google.android.accessibility.braille.interfaces.BrailleDisplayForBrailleIme;
import com.google.android.accessibility.braille.interfaces.BrailleDisplayForTalkBack;
import com.google.android.accessibility.braille.interfaces.TalkBackForBrailleDisplay;

/** Entry point between TalkBack and the braille display feature. */
public class BrailleDisplay implements BrailleDisplayForTalkBack {
  private static final String TAG = "BrailleDisplay";
  public static final Encoder.Factory ENCODER_FACTORY = new BrlttyEncoder.BrlttyFactory();

  private boolean isRunning;
  private BrailleDisplayManager brailleDisplayManager;
  private final BdController controller;

  public BrailleDisplay(Context context, TalkBackForBrailleDisplay talkBackForBrailleDisplay) {
    this.controller = new BdController(context, talkBackForBrailleDisplay);
    this.brailleDisplayManager = new BrailleDisplayManager(context, controller, ENCODER_FACTORY);
    BrailleDisplayTalkBackSpeaker.getInstance().initialize(talkBackForBrailleDisplay);
  }

  /** Starts braille display. */
  @Override
  public void start() {
    BrailleDisplayLog.d(TAG, "start");
    brailleDisplayManager.onServiceStarted();
    isRunning = true;
  }

  /** Stops braille display. */
  @Override
  public void stop() {
    BrailleDisplayLog.d(TAG, "stop");
    brailleDisplayManager.onServiceStopped();
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
  public BrailleDisplayForBrailleIme getBrailleDisplayForBrailleIme() {
    return controller.getBrailleDisplayForBrailleIme();
  }
}
