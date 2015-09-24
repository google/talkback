/*
 * Copyright (C) 2012 The Android Open Source Project
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
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Message;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.TextView;
import com.android.utils.WeakReferenceHandler;
import com.android.utils.widget.SimpleOverlay;

/**
 * Displays text-to-speech text on the screen.
 */
public class TextToSpeechOverlay extends SimpleOverlay {
    private static final int MSG_CLEAR_TEXT = 1;

    private TextView mText;

    public TextToSpeechOverlay(Context context) {
        super(context);

        final WindowManager.LayoutParams params = getParams();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            params.type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY;
        } else {
            params.type = WindowManager.LayoutParams.TYPE_SYSTEM_OVERLAY;
        }
        params.format = PixelFormat.TRANSPARENT;
        params.flags |= WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        params.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        params.width = WindowManager.LayoutParams.WRAP_CONTENT;
        params.height = WindowManager.LayoutParams.WRAP_CONTENT;
        params.gravity = Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM;
        setParams(params);

        int padding = context.getResources()
                .getDimensionPixelSize(R.dimen.tts_overlay_text_padding);
        int bottomMargin = context.getResources()
                .getDimensionPixelSize(R.dimen.tts_overlay_text_bottom_margin);

        mText = new TextView(context);
        mText.setBackgroundColor(0xAA000000);
        mText.setTextColor(Color.WHITE);
        mText.setPadding(padding, padding, padding, padding);
        mText.setGravity(Gravity.CENTER);

        FrameLayout layout = new FrameLayout(context);
        FrameLayout.LayoutParams layoutParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        layoutParams.setMargins(0, 0, 0, bottomMargin);
        layout.addView(mText, layoutParams);

        setContentView(layout);
    }

    public void speak(CharSequence text) {
        if (TextUtils.isEmpty(text)) {
            hide();
            return;
        }

        show();

        final long displayTime = Math.max(2000, text.length() * 100);

        mHandler.removeMessages(MSG_CLEAR_TEXT);
        mText.setText(text.toString().trim());
        mHandler.sendEmptyMessageDelayed(MSG_CLEAR_TEXT, displayTime);
    }

    private final OverlayHandler mHandler = new OverlayHandler(this);

    private static class OverlayHandler extends WeakReferenceHandler<TextToSpeechOverlay> {
        public OverlayHandler(TextToSpeechOverlay parent) {
            super(parent);
        }

        @Override
        protected void handleMessage(Message msg, TextToSpeechOverlay parent) {
            switch (msg.what) {
                case MSG_CLEAR_TEXT:
                    parent.mText.setText("");
                    parent.hide();
                    break;
            }
        }
    }
}
