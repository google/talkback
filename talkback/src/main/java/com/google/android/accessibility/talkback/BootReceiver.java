/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.android.accessibility.talkback;

import static com.google.android.accessibility.utils.Performance.EVENT_ID_UNTRACKED;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import com.google.android.accessibility.utils.BuildVersionUtils;
import com.google.android.accessibility.utils.Performance.EventId;

public class BootReceiver extends BroadcastReceiver {

  @Override
  public void onReceive(Context context, Intent intent) {
    TalkBackService service = TalkBackService.getInstance();
    if (service == null) {
      return;
    }

    EventId eventId = EVENT_ID_UNTRACKED; // Not a user-initiated event.

    // We need to ensure that onLockedBootCompleted() and onUnlockedBootCompleted() are called
    // *in that order* to properly set up TalkBack.
    switch (intent.getAction()) {
      case Intent.ACTION_LOCKED_BOOT_COMPLETED:
        // Only N+ devices will get this intent (even if they don't have FBE enabled).
        service.onLockedBootCompleted(eventId);
        break;
      case Intent.ACTION_BOOT_COMPLETED:
        if (!BuildVersionUtils.isAtLeastN()) {
          // Pre-N devices will never get LOCKED_BOOT, so we need to do the locked-boot
          // initialization here right before we do the unlocked-boot initialization.
          service.onLockedBootCompleted(eventId);
        }
        service.onUnlockedBootCompleted();
        break;
      default: // fall out
    }
  }
}
