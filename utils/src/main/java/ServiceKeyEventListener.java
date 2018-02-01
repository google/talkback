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
import com.google.android.accessibility.utils.Performance.EventId;

/**
 * Receive events from a service, with the option to continue receiving them if the service is in a
 * suspended state.
 */
public interface ServiceKeyEventListener {
  /** Called when a key event is received. */
  boolean onKeyEvent(KeyEvent event, EventId eventId);

  /** Determines whether events are received when the service isn't running. */
  boolean processWhenServiceSuspended();
}
