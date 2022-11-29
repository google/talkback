/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.google.android.accessibility.uiunderstanding;

import android.os.Bundle;
import android.os.SystemClock;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.util.concurrent.atomic.AtomicInteger;

/** A record of an action sent to EventSource.performAction(). */
class ActionRecord {

  //////////////////////////////////////////////////////////////////////////////////
  // Class data

  @NonNull private static final AtomicInteger actionCount = new AtomicInteger(0);

  //////////////////////////////////////////////////////////////////////////////////
  // Member data

  final int actionId; // From AccessibilityEvent.ACTION_* or AccessibilityEvent.Action
  @Nullable final Bundle bundle;
  final long createdUptimeMillisec;
  final int instanceId; // For matching the source of new events

  //////////////////////////////////////////////////////////////////////////////////
  // Construction

  ActionRecord(int actionId, @Nullable Bundle bundle) {
    this.actionId = actionId;
    this.bundle = bundle;
    this.instanceId = actionCount.incrementAndGet();
    this.createdUptimeMillisec = SystemClock.uptimeMillis();
  }
}
