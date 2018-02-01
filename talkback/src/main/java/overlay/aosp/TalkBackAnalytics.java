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

import android.content.SharedPreferences;
import com.google.android.accessibility.utils.input.CursorGranularity;

public class TalkBackAnalytics extends Analytics {

  public TalkBackAnalytics(TalkBackService service) {
    super(service);
  }

  @Override
  public void onTalkBackServiceStarted() {}

  @Override
  public void onTalkBackServiceStopped() {}

  @Override
  public void onGesture(int gestureId) {}

  @Override
  public void onTextEdited() {}

  @Override
  public void onGranularityChanged(
      CursorGranularity newGranularity, @UserActionType int userActionType, boolean isPending) {}

  @Override
  public void logPendingChanges() {}

  @Override
  public void clearPendingGranularityChange() {}

  @Override
  public void onManuallyChangeSetting(
      String prefKey, @UserActionType int userActionType, boolean isPending) {}

  @Override
  public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {}
}
