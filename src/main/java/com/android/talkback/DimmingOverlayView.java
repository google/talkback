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

package com.android.talkback;

import android.content.Context;
import android.graphics.Color;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

public class DimmingOverlayView extends LinearLayout {

    private View mContent;
    private TextView mTimerView;
    private ProgressBar mProgress;
    private int mTimerLimit;

    public DimmingOverlayView(Context context) {
        super(context);
        init();
    }

    private void init() {
        setOrientation(VERTICAL);
        setGravity(Gravity.CENTER);
        setBackgroundColor(Color.BLACK);

        LayoutInflater inflater = LayoutInflater.from(getContext());
        inflater.inflate(R.layout.dimming_overlay_exit_instruction, this, true);
        mContent = findViewById(R.id.content);

        mTimerView = (TextView) findViewById(R.id.timer);
        mProgress = (ProgressBar) findViewById(R.id.progress);
    }

    public void setTimerLimit(int seconds) {
        mTimerLimit = seconds;
        mProgress.setMax(seconds);
    }

    public void updateSecondsText(int seconds) {
        String text = getContext().getString(R.string.dim_screen_timer,
                getMinutes(seconds), getSeconds(seconds));
        mTimerView.setText(text);
        mProgress.setProgress(mTimerLimit - seconds);
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
        mContent.setVisibility(visibility);
    }
}
