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

package com.google.android.accessibility.braille.brailledisplay.platform;

import android.view.accessibility.AccessibilityEvent;
import com.google.android.accessibility.braille.brltty.BrailleInputEvent;

/**
 * Handles the business logic of transforming {@link AccessibilityEvent} into un-encoded braille
 * dots meant for the display, and making use of input commands arriving from the display.
 */
public interface Controller {

  /** Informs that starting a connection to a braille display. */
  void onConnectStarted();

  /** Informs that a connection to the display was just established. */
  void onConnected();

  /** Informs that the displayer is ready to be used. */
  void onDisplayerReady(Displayer displayer);

  /** Informs that the connection has been dropped. */
  void onDisconnected();

  /** Passes an accessibility event for consumption. */
  void onAccessibilityEvent(AccessibilityEvent accessibilityEvent);

  /** Informs that a read command has arrived. */
  void onBrailleInputEvent(BrailleInputEvent brailleInputEvent);

  /** Destroys this object. */
  void onDestroy();

  /** Informs that reading control changed. */
  void onReadingControlChanged(CharSequence readingControlDescription);
}
