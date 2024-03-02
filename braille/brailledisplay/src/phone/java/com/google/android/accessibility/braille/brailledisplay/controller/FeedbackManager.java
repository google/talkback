/*
 * Copyright (C) 2012 Google Inc.
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

package com.google.android.accessibility.braille.brailledisplay.controller;

import com.google.android.accessibility.braille.brailledisplay.R;
import com.google.android.accessibility.utils.output.FeedbackController;

/**
 * Provides 'out of band feedback', that is feedback that is typically not output on the braille
 * display and that doesn't come from the UI itself. Examples include error beeps and other earcons.
 * Note that feedback that is not specific to braille navigation is handled by other accessibility
 * services.
 */
// TODO: Add haptic feedback.
public class FeedbackManager {
  public static final int TYPE_NONE = -1;
  public static final int TYPE_NAVIGATE_OUT_OF_BOUNDS = 0;
  public static final int TYPE_NAVIGATE_INTO_HIERARCHY = 1;
  public static final int TYPE_NAVIGATE_OUT_OF_HIERARCHY = 2;
  public static final int TYPE_DISPLAY_CONNECTED = 3;
  public static final int TYPE_DISPLAY_DISCONNECTED = 4;
  public static final int TYPE_COMMAND_FAILED = 5;
  public static final int TYPE_UNKNOWN_COMMAND = 6;
  public static final int TYPE_AUTO_SCROLL_START = 7;
  public static final int TYPE_AUTO_SCROLL_STOP = 8;
  private static final int[] TYPES_TO_RESOURCE_IDS = {
    R.raw.complete,
    R.raw.chime_down,
    R.raw.chime_up,
    R.raw.display_connected,
    R.raw.display_disconnected,
    R.raw.double_beep,
    R.raw.double_beep,
    R.raw.turn_on,
    R.raw.turn_off,
  };

  private FeedbackController feedbackController;

  public FeedbackManager(FeedbackController controller) {
    feedbackController = controller;
  }

  public void emitFeedback(int type) {
    if (type < 0 || TYPES_TO_RESOURCE_IDS.length <= type) {
      return;
    }
    feedbackController.playAuditory(TYPES_TO_RESOURCE_IDS[type], null);
  }

  /**
   * Emits feedback if an operation fails ({@code result} is {@code false}), and returns {@code
   * true} always. This is intended to signal that an event was handled even if it fails, but
   * instead emit the appropriate feedback {@code type} to the user.
   */
  public boolean emitOnFailure(boolean result, int type) {
    if (!result) {
      emitFeedback(type);
    }
    return true;
  }
}
