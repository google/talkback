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

import android.content.Context;
import android.content.SharedPreferences;
import com.google.android.accessibility.talkback.analytics.TalkBackAnalytics;

/** Analytics that tracks TalkBack usage. */
public class TalkBackAnalyticsImpl extends TalkBackAnalytics {
  public TalkBackAnalyticsImpl(TalkBackService service) {}

  /** Collect image caption event. This is reserved for report from Settings. */
  public static void onImageCaptionEventFromSettings(
      SharedPreferences prefs, Context context, @ImageCaptionEventId int event) {}
}
