/*
 * Copyright (C) 2019 Google Inc.
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
package com.google.android.accessibility.brailleime.analytics;

import android.content.Context;

/** Stub implementation of analytics used by the open source variant. */
public class BrailleAnalytics {

  private static BrailleAnalytics instance;

  public static BrailleAnalytics getInstance(Context context) {
    if (instance == null) {
      instance = new BrailleAnalytics(context.getApplicationContext());
    }
    return instance;
  }

  private BrailleAnalytics(Context context) {}

  /** Stub implementation does nothing. */
  public void sendAllLogs() {}

  /** Stub implementation does nothing. */
  public void startSession() {}

  /** Stub implementation does nothing. */
  public void collectSessionEvents() {}

  /** Stub implementation does nothing. */
  public void logGestureActionKeySpace() {}

  /** Stub implementation does nothing. */
  public void logGestureActionKeyDeleteCharacter() {}

  /** Stub implementation does nothing. */
  public void logGestureActionKeyDeleteWord() {}

  /** Stub implementation does nothing. */
  public void logGestureActionKeyNewline() {}

  /** Stub implementation does nothing. */
  public void logGestureActionSubmitText() {}

  /** Stub implementation does nothing. */
  public void logGestureActionCloseKeyboard() {}

  /** Stub implementation does nothing. */
  public void logGestureActionSwitchKeyboard() {}

  /** Stub implementation does nothing. */
  public void logGestureActionOpenOptionsMenu() {}

  /** Stub implementation does nothing. */
  public void logContractedToggle(boolean contractedModeOn) {}

  /** Stub implementation does nothing. */
  public void logTalkBackOffDialogDisplay() {}

  /** Stub implementation does nothing. */
  public void logFewTouchPointsDialogDisplay() {}

  /** Stub implementation does nothing. */
  public void logTotalBrailleCharCount(int numOfChar) {}

  /** Stub implementation does nothing. */
  public void logTutorialFinishedByTutorialCompleted() {}

  /** Stub implementation does nothing. */
  public void logTutorialFinishedByTalkbackStop() {}

  /** Stub implementation does nothing. */
  public void logTutorialFinishedByLaunchSettings() {}

  /** Stub implementation does nothing. */
  public void logTutorialFinishedBySwitchToNextInputMethod() {}

  /** Stub implementation does nothing. */
  public void logContextMenuOptionCount(int optionPosition) {}
}
