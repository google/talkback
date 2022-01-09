package com.google.android.accessibility.utils.brailledisplay;

import android.content.res.Configuration;
import android.view.accessibility.AccessibilityEvent;

/** Exposes some BrailleDisplay behavior to TalkBack. */
public interface BrailleDisplayForTalkBack {
  /** Starts braille display. */
  void start();
  /** Stops braille display. */
  void stop();
  /** Notifies configuration changed. */
  void onConfigurationChanged(Configuration configuration);
  /** Notifies receiving accessibility event. */
  void onAccessibilityEvent(AccessibilityEvent accessibilityEvent);
}
