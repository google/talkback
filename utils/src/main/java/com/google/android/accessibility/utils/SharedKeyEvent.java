/*
 * Copyright (C) 2017 Google Inc.
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

package com.google.android.accessibility.utils;

import android.view.KeyEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * This class forwards keyEvents to all services that listen for it.
 *
 * <p>On Android M and lower, keyEvents are only sent to a single service. On Android N and higher,
 * all services get key events. This class hides this detail from services, and ensures they all get
 * key events.
 */
public class SharedKeyEvent {
  /** Interface for receiving a shared key event */
  public interface Listener {
    boolean onKeyEventShared(KeyEvent keyEvent);
  }

  // List of keyEvent listeners.
  private static List<Listener> sListeners = new ArrayList<>();

  // Cannot be instantiated.
  private SharedKeyEvent() {}

  /**
   * Add a listener to shared key events.
   *
   * @param listener Object that will listen to shared key events.
   */
  public static void register(Listener listener) {
    sListeners.add(listener);
  }

  /**
   * Remove a listener to shared key events.
   *
   * @param listener Object that will listen to shared key events.
   */
  public static void unregister(Listener listener) {
    sListeners.remove(listener);
  }

  /**
   * Send a key event to all listeners.
   *
   * <p>On M and lower, the event will be sent to all listeners. On N and higher, it will only be
   * sent to the current listener.
   *
   * @param listener Service that received the keyEvent.
   * @param keyEvent Event received from the system.
   */
  public static boolean onKeyEvent(Listener listener, KeyEvent keyEvent) {
    if (BuildVersionUtils.isAtLeastN()) {
      return listener.onKeyEventShared(keyEvent);
    } else {
      boolean handled = false;
      for (Listener currentListener : sListeners) {
        handled = currentListener.onKeyEventShared(keyEvent) || handled;
      }
      return handled;
    }
  }
}
