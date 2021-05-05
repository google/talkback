/*
 * Copyright (C) 2020 Google Inc.
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

package com.google.android.accessibility.talkback.actor;

import android.accessibilityservice.AccessibilityGestureEvent;
import android.content.Context;
import android.content.Intent;
import com.google.android.accessibility.talkback.R;
import com.google.android.accessibility.talkback.gesture.GestureHistory;
import com.google.android.accessibility.utils.FeatureSupport;
import com.google.android.libraries.accessibility.utils.log.LogUtils;

/** A class to cache gesture and report. */
public class GestureReporter {
  public static final String TAG = "GestureReporter";
  public static final boolean ENABLED = false && FeatureSupport.supportGestureMotionEvents();

  private Context context;
  private GestureHistory gestureHistory;

  public GestureReporter(Context context) {
    this.context = context;
    gestureHistory = new GestureHistory();
  }

  /** Records gestures to history queue. */
  public boolean record(AccessibilityGestureEvent accessibilityGestureEvent) {
    if (!ENABLED) {
      return false;
    }
    return gestureHistory.save(accessibilityGestureEvent);
  }

  /** Reports gesture data by Share intent. */
  public boolean report() {
    if (!ENABLED) {
      LogUtils.w(GestureReporter.TAG, "Not support ReportingGesture");
      return false;
    }
    if (gestureHistory.isEmpty()) {
      LogUtils.w(GestureReporter.TAG, "Fail to report because no gesture data!");
      return false;
    }
    reportBySendIntent();
    return true;
  }

  private void reportBySendIntent() {
    Intent intent = new Intent(Intent.ACTION_SEND);
    intent.putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.shortcut_report_gesture));
    intent.putExtra(
        Intent.EXTRA_TEXT,
        context.getString(R.string.report_gesture_description)
            + gestureHistory.getGestureListString(context));
    intent.setType("text/plain");
    intent.putExtra(Intent.EXTRA_STREAM, gestureHistory.getFileUri(context));
    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    context.startActivity(intent);
  }
}
