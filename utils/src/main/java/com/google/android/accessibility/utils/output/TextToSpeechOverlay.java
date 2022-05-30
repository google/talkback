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

package com.google.android.accessibility.utils.output;

import android.content.Context;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.Message;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.WindowManager.BadTokenException;
import android.widget.FrameLayout;
import android.widget.TextView;
import com.google.android.accessibility.utils.R;
import com.google.android.accessibility.utils.WeakReferenceHandler;
import com.google.android.accessibility.utils.widget.DialogUtils;
import com.google.android.libraries.accessibility.utils.log.LogUtils;
import com.google.android.libraries.accessibility.widgets.simple.SimpleOverlay;

/**
 * Displays text on the screen. The class currently is used by {@link SpeechControllerImpl} and
 * AccessibilityMenuService.
 */
public class TextToSpeechOverlay extends SimpleOverlay {

  private static final String LOG_TAG = "TextToSpeechOverlay";
  private static final int DISPLAY_MS = 2000;
  private static final int MSG_SET_TEXT = 1;
  private static final int MSG_CLEAR_TEXT = 2;
  private final OverlayHandler handler = new OverlayHandler(this);
  private final TextView text;

  public TextToSpeechOverlay(Context context) {
    this(context, /* id= */ 0, /* sendsAccessibilityEvents= */ false);
  }

  public TextToSpeechOverlay(Context context, int id, final boolean sendsAccessibilityEvents) {
    super(context, id, sendsAccessibilityEvents);

    final WindowManager.LayoutParams params = getParams();
    params.type = DialogUtils.getDialogType();
    params.format = PixelFormat.TRANSPARENT;
    params.flags |= WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
    params.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
    params.width = WindowManager.LayoutParams.WRAP_CONTENT;
    params.height = WindowManager.LayoutParams.WRAP_CONTENT;
    params.gravity = Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM;
    setParams(params);

    int padding = context.getResources().getDimensionPixelSize(R.dimen.tts_overlay_text_padding);
    int bottomMargin =
        context.getResources().getDimensionPixelSize(R.dimen.tts_overlay_text_bottom_margin);

    text = new TextView(context);
    text.setBackgroundColor(0xAA000000);
    text.setTextColor(Color.WHITE);
    text.setPadding(padding, padding, padding, padding);
    text.setGravity(Gravity.CENTER);

    FrameLayout layout = new FrameLayout(context);
    FrameLayout.LayoutParams layoutParams =
        new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    layoutParams.setMargins(0, 0, 0, bottomMargin);
    layout.addView(text, layoutParams);

    setContentView(layout);
  }

  public void displayText(CharSequence text) {
    if (TextUtils.isEmpty(text)) {
      handler.sendEmptyMessage(MSG_CLEAR_TEXT);
      return;
    }
    final long displayTimeMs = Math.max(DISPLAY_MS, text.length() * 100);
    handler.removeMessages(MSG_CLEAR_TEXT);
    handler.sendMessage(Message.obtain(handler, MSG_SET_TEXT, text.toString().trim()));
    handler.sendEmptyMessageDelayed(MSG_CLEAR_TEXT, displayTimeMs);
  }

  private static class OverlayHandler extends WeakReferenceHandler<TextToSpeechOverlay> {
    public OverlayHandler(TextToSpeechOverlay parent) {
      super(parent);
    }

    @Override
    protected void handleMessage(Message msg, TextToSpeechOverlay parent) {
      switch (msg.what) {
        case MSG_SET_TEXT:
          try {
            parent.show();
          } catch (BadTokenException e) {
            LogUtils.e(LOG_TAG, e, "Caught WindowManager.BadTokenException while displaying text.");
          }
          parent.text.setText((CharSequence) msg.obj);
          break;
        case MSG_CLEAR_TEXT:
          parent.text.setText("");
          parent.hide();
          break;
        default: // fall out
      }
    }
  }
}
