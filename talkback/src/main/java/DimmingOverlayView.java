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
import android.graphics.Color;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.accessibility.AccessibilityEvent;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

public class DimmingOverlayView extends LinearLayout {

  private View content;
  private TextView timerView;
  private ProgressBar progress;
  private int timerLimit;

  public DimmingOverlayView(Context context) {
    super(context);
    init(context);
  }

  private void init(Context context) {
    setOrientation(VERTICAL);
    setGravity(Gravity.CENTER);
    setBackgroundColor(Color.BLACK);

    LayoutInflater inflater = LayoutInflater.from(getContext());
    inflater.inflate(R.layout.dimming_overlay_exit_instruction, this, true);
    content = findViewById(R.id.content);

    timerView = (TextView) findViewById(R.id.timer);
    progress = (ProgressBar) findViewById(R.id.progress);

    // Set dim-screen instructions to use context-menu to exit, because some users do not have
    // dim-screen volume-key shortcut.
    // TODO: Add gesture-finding code to a function in GestureController.
    CharSequence instructionText =
        context.getString(
            R.string.screen_dimming_exit_instruction_line2,
            context.getString(R.string.value_direction_down_and_right),
            context.getString(R.string.shortcut_disable_dimming));
    TextView instruction2 = (TextView) findViewById(R.id.message_line_1);
    instruction2.setText(instructionText);

    setAccessibilityDelegate(
        new View.AccessibilityDelegate() {
          @Override
          public void sendAccessibilityEvent(View host, int eventType) {
            // Do not dispatch TYPE_WINDOW_STATE_CHANGED event about this view.
            if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
              return;
            }

            super.sendAccessibilityEvent(host, eventType);
          }
        });
  }

  public void setTimerLimit(int seconds) {
    timerLimit = seconds;
    progress.setMax(seconds);
  }

  public void updateSecondsText(int seconds) {
    String text =
        getContext().getString(R.string.dim_screen_timer, getMinutes(seconds), getSeconds(seconds));
    timerView.setText(text);
    progress.setProgress(timerLimit - seconds);
  }

  private int getMinutes(int seconds) {
    return seconds / 60;
  }

  private String getSeconds(int seconds) {
    int res = seconds % 60;
    if (res < 10) {
      return "0" + res;
    } else {
      return String.valueOf(res);
    }
  }

  public void hideText() {
    setContentVisibility(View.GONE);
  }

  public void showText() {
    setContentVisibility(View.VISIBLE);
  }

  private void setContentVisibility(int visibility) {
    content.setVisibility(visibility);
  }
}
