/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.google.android.accessibility.utils.input;

import androidx.annotation.NonNull;
import android.view.InputDevice;
import android.view.KeyEvent;
import androidx.annotation.IntDef;
import com.google.android.accessibility.utils.Performance.EventId;
import com.google.android.accessibility.utils.ServiceKeyEventListener;

/**
 * InputModeManager manages input mode which user is currently using to interact with the service.
 */
public class InputModeManager implements ServiceKeyEventListener {

  /** Different input modes used to move accessibility focus or perform actions. */
  @IntDef({
    INPUT_MODE_UNKNOWN,
    INPUT_MODE_TOUCH,
    INPUT_MODE_KEYBOARD,
    INPUT_MODE_TV_REMOTE,
    // INPUT_MODE_NON_ALPHABETIC_KEYBOARD will be used for numeric keypads built into phones.
    INPUT_MODE_NON_ALPHABETIC_KEYBOARD
  })
  public @interface InputMode {}

  public static final int INPUT_MODE_UNKNOWN = -1;
  public static final int INPUT_MODE_TOUCH = 0;
  public static final int INPUT_MODE_KEYBOARD = 1;
  public static final int INPUT_MODE_TV_REMOTE = 2;
  public static final int INPUT_MODE_NON_ALPHABETIC_KEYBOARD = 3;

  private @InputMode int mInputMode = INPUT_MODE_UNKNOWN;

  public void clear() {
    mInputMode = INPUT_MODE_UNKNOWN;
  }

  // TODO: Refactor all places where setInputMode is called such that the input mode
  // changes are done only in InputModeManager and setInputMode becomes a private method.
  public void setInputMode(@InputMode int inputMode) {
    if (inputMode == INPUT_MODE_UNKNOWN) {
      return;
    }

    mInputMode = inputMode;
  }

  public @InputMode int getInputMode() {
    return mInputMode;
  }

  @Override
  public boolean onKeyEvent(KeyEvent event, EventId eventId) {
    // Talkback needs to differentiate between a separate physical Keyboard and numeric keypads
    // built into phones. Keyboard attached with the phones should not be treated as physical
    // keyboards.
    setInputMode(
        isEventFromNonAlphabeticKeyboard(event)
            ? INPUT_MODE_NON_ALPHABETIC_KEYBOARD
            : INPUT_MODE_KEYBOARD);
    return false;
  }

  @Override
  public boolean processWhenServiceSuspended() {
    return false;
  }

  public static String inputModeToString(@InputMode int inputMode) {
    switch (inputMode) {
      case INPUT_MODE_TOUCH:
        return "INPUT_MODE_TOUCH";
      case INPUT_MODE_KEYBOARD:
        return "INPUT_MODE_KEYBOARD";
      case INPUT_MODE_TV_REMOTE:
        return "INPUT_MODE_TV_REMOTE";
      case INPUT_MODE_NON_ALPHABETIC_KEYBOARD:
        return "INPUT_MODE_NON_ALPHABETIC_KEYBOARD";
      default:
        return "INPUT_MODE_UNKNOWN";
    }
  }

  // Checks if the event is from a non alphabetic keyboard, like the ones built into a phone.
  private static boolean isEventFromNonAlphabeticKeyboard(@NonNull KeyEvent event) {
    InputDevice inputDevice = InputDevice.getDevice(event.getDeviceId());
    return (inputDevice != null)
        && (InputDevice.KEYBOARD_TYPE_NON_ALPHABETIC == inputDevice.getKeyboardType());
  }
}
