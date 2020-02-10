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

package com.google.android.accessibility.talkback.eventprocessor;

import com.google.android.accessibility.utils.TimedFlags;

/** Single instance that keeps info about events and their time */
public class EventState extends TimedFlags {

  // When moving with granularity focus could be moved to next node automatically. In that case
  // TalkBack will also move with granularity inside newly focused node and pronounce part of
  // the content. There is no need to pronounce the whole content of the node in that case
  public static final int EVENT_SKIP_HINT_AFTER_GRANULARITY_MOVE = 2;

  public static final int EVENT_SKIP_WINDOWS_CHANGED_PROCESSING_AFTER_CURSOR_CONTROL = 4;
  public static final int EVENT_SKIP_WINDOW_STATE_CHANGED_PROCESSING_AFTER_CURSOR_CONTROL = 5;
  public static final int EVENT_SKIP_HINT_AFTER_CURSOR_CONTROL = 6;

  /** Indicates that TalkBack recently forced a refocus of a node. */
  public static final int EVENT_NODE_REFOCUSED = 8;

  public static final int EVENT_SKIP_FOCUS_SYNC_FROM_WINDOWS_CHANGED = 11;
  public static final int EVENT_SKIP_FOCUS_SYNC_FROM_WINDOW_STATE_CHANGED = 12;

  public static final int EVENT_HINT_FOR_SYNCED_ACCESSIBILITY_FOCUS = 15;

  private static EventState instance = new EventState();

  public static EventState getInstance() {
    return instance;
  }
}
