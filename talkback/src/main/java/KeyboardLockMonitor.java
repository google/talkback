/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.google.android.accessibility.talkback;

import android.view.KeyEvent;
import com.google.android.accessibility.compositor.Compositor;
import com.google.android.accessibility.utils.Performance.EventId;
import com.google.android.accessibility.utils.ServiceKeyEventListener;

/** Watches for changes in the keyboard lock state, such as Caps Lock or Num Lock. */
public class KeyboardLockMonitor implements ServiceKeyEventListener {

  private final Compositor compositor;

  public KeyboardLockMonitor(Compositor compositor) {
    this.compositor = compositor;
  }

  @Override
  public boolean onKeyEvent(KeyEvent event, EventId eventId) {
    // Lock state changes should only occur on key up. If we don't check for key up, two events
    // will fire. This is especially noticeable if the user holds down the Caps Lock key for
    // a while before releasing.
    if (event.getAction() == KeyEvent.ACTION_UP) {
      if (event.getKeyCode() == KeyEvent.KEYCODE_CAPS_LOCK) {
        if (event.isCapsLockOn()) {
          compositor.handleEvent(Compositor.EVENT_CAPS_LOCK_ON, eventId);
        } else {
          compositor.handleEvent(Compositor.EVENT_CAPS_LOCK_OFF, eventId);
        }
      } else if (event.getKeyCode() == KeyEvent.KEYCODE_NUM_LOCK) {
        if (event.isNumLockOn()) {
          compositor.handleEvent(Compositor.EVENT_NUM_LOCK_ON, eventId);
        } else {
          compositor.handleEvent(Compositor.EVENT_NUM_LOCK_OFF, eventId);
        }
      } else if (event.getKeyCode() == KeyEvent.KEYCODE_SCROLL_LOCK) {
        if (event.isScrollLockOn()) {
          compositor.handleEvent(Compositor.EVENT_SCROLL_LOCK_ON, eventId);
        } else {
          compositor.handleEvent(Compositor.EVENT_SCROLL_LOCK_OFF, eventId);
        }
      }
    }

    return false; // Never intercept keys; only report on their state.
  }

  @Override
  public boolean processWhenServiceSuspended() {
    return false;
  }
}
