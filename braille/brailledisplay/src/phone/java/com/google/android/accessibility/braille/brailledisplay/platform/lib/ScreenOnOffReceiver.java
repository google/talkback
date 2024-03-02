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

package com.google.android.accessibility.braille.brailledisplay.platform.lib;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

/** A BroadcastReceiver for listening to screen off and screen on broadcasts. */
public class ScreenOnOffReceiver
    extends ActionReceiver<ScreenOnOffReceiver, ScreenOnOffReceiver.Callback> {

  /** The callback associated with the actions of this receiver. */
  public interface Callback {
    void onScreenOn();

    void onScreenOff();
  }

  public ScreenOnOffReceiver(Context context, Callback callback) {
    super(context, callback);
  }

  @Override
  protected void onReceive(Callback callback, String action, Bundle extras) {
    if (Intent.ACTION_SCREEN_ON.equals(action)) {
      callback.onScreenOn();
    } else if (Intent.ACTION_SCREEN_OFF.equals(action)) {
      callback.onScreenOff();
    }
  }

  @Override
  protected String[] getActionsList() {
    return new String[] {
      Intent.ACTION_SCREEN_ON, Intent.ACTION_SCREEN_OFF,
    };
  }
}
