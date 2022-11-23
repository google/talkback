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

import static com.google.android.accessibility.utils.AccessibilityServiceCompatUtils.Constants.BRAILLE_KEYBOARD;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;
import com.google.android.accessibility.braille.brailledisplay.BrailleDisplayImeUnavailableActivity;
import com.google.android.accessibility.braille.brltty.BrailleInputEvent;

/**
 * Helps coordinating between the accessibility service and input method. Among other things, this
 * class pops up the IMESetupWizardActivity if the user tries to use the braille keyboard when the
 * braille IME is not the current one.
 */
public class ImeHelper {
  private final Context context;

  /** Initializes an object, registering a broadcast receiver to open the IME picker. */
  public ImeHelper(Context contextArg) {
    context = contextArg;
  }

  /**
   * Listens for text input keystrokes. If the user tries to enter text and the Braille IME is not
   * the default on the system, takes the user through the {@link
   * BrailleDisplayImeUnavailableActivity}.
   */
  public boolean onInputEvent(BrailleInputEvent event) {
    int cmd = event.getCommand();
    if (cmd == BrailleInputEvent.CMD_BRAILLE_KEY || cmd == BrailleInputEvent.CMD_KEY_DEL) {
      if (!isInputMethodDefault(context, BRAILLE_KEYBOARD)) {
        tryIMESwitch();
        return true;
      }
    }
    return false;
  }

  /** Determines from system settings if {@code imeComponentName} is an enabled input method. */
  @SuppressWarnings("StringSplitter") // Guava not used in project, so Splitter not available.
  public static boolean isInputMethodEnabled(Context contextArg, ComponentName imeComponentName) {
    final String enabledIMEIds =
        Settings.Secure.getString(
            contextArg.getContentResolver(), Settings.Secure.ENABLED_INPUT_METHODS);
    if (enabledIMEIds == null) {
      return false;
    }

    for (String enabledIMEId : enabledIMEIds.split(":")) {
      if (imeComponentName.equals(ComponentName.unflattenFromString(enabledIMEId))) {
        return true;
      }
    }
    return false;
  }

  /** Determines, from system settings, if {@code imeComponentName} is the default input method. */
  public static boolean isInputMethodDefault(Context contextArg, ComponentName imeComponentName) {
    final String defaultIMEId =
        Settings.Secure.getString(
            contextArg.getContentResolver(), Settings.Secure.DEFAULT_INPUT_METHOD);

    return defaultIMEId != null
        && imeComponentName.equals(ComponentName.unflattenFromString(defaultIMEId));
  }

  private void tryIMESwitch() {
    Intent intent = new Intent(context, BrailleDisplayImeUnavailableActivity.class);
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    context.startActivity(intent);
  }
}
