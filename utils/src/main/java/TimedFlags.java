/*
 * Copyright (C) 2017 The Android Open Source Project
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

import android.os.SystemClock;
import android.util.SparseArray;

/** Encapsulates a set of flags that persist for a limited time frame. */
public class TimedFlags {
  private static final int FLAG_TIMEOUT = 1000;

  private final SparseArray<Long> mFlags = new SparseArray<>();

  public void setFlag(int flag) {
    mFlags.put(flag, SystemClock.uptimeMillis());
  }

  public void clearFlag(int flag) {
    mFlags.remove(flag);
  }

  public boolean checkAndClearRecentFlag(int flag) {
    if (hasFlag(flag, FLAG_TIMEOUT)) {
      mFlags.remove(flag);
      return true;
    }

    return false;
  }

  private boolean hasFlag(int flag, long timeout) {
    Long lastFlagTime = mFlags.get(flag);
    if (lastFlagTime != null) {
      return SystemClock.uptimeMillis() - lastFlagTime < timeout;
    }

    return false;
  }

  public void clearAllFlags() {
    mFlags.clear();
  }
}
