package com.google.android.accessibility.talkback.utils;

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
import androidx.core.content.ContextCompat;
import com.google.android.accessibility.talkback.R;
import com.google.android.accessibility.utils.WeakReferenceHandler;
import com.google.android.accessibility.utils.widget.SimpleOverlay;
import com.google.android.libraries.accessibility.utils.log.LogUtils;

/** Overlay for displaying logs in developer mode */
public class DiagnosticOverlay extends SimpleOverlay {

  private static final String LOG_TAG = "DiagnosticOverlay";

  private static final int MSG_CLEAR_TEXT = 1;

  private TextView mText;

  public DiagnosticOverlay(Context context) {
    super(context, 0, false);

    final WindowManager.LayoutParams params = getParams();
    params.format = PixelFormat.TRANSPARENT;
    params.flags |= WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
    params.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
    params.width = WindowManager.LayoutParams.WRAP_CONTENT;
    params.height = WindowManager.LayoutParams.WRAP_CONTENT;
    // TODO: (b/191974605) integrate tts + log overlay into one view
    params.gravity = Gravity.CENTER | Gravity.TOP;
    setParams(params);

    int padding =
        context.getResources().getDimensionPixelSize(R.dimen.diagnostic_overlay_text_padding);
    int bottomMargin =
        context.getResources().getDimensionPixelSize(R.dimen.diagnostic_overlay_text_bottom_margin);

    mText = new TextView(context);
    /** color isn't same as tts overlay - might be using different color format */
    mText.setBackgroundColor(
        ContextCompat.getColor(context, R.color.diagnostic_overlay_background));
    mText.setTextColor(Color.WHITE);
    mText.setPadding(padding, padding, padding, padding);
    mText.setGravity(Gravity.LEFT);

    FrameLayout layout = new FrameLayout(context);
    FrameLayout.LayoutParams layoutParams =
        new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    layoutParams.setMargins(0, 0, 0, bottomMargin);
    layout.addView(mText, layoutParams);
    setContentView(layout);
  }

  public void displayText(CharSequence text) {
    if (TextUtils.isEmpty(text)) {
      hide();
      return;
    }
    mHandler.removeMessages(MSG_CLEAR_TEXT);

    try {
      show();
    } catch (BadTokenException e) {
      LogUtils.e(LOG_TAG, e, "Caught WindowManager.BadTokenException while displaying text.");
    }

    final long displayTime = Math.max(4000, text.length() * 2000);
    mText.setText(text);
    mHandler.sendEmptyMessageDelayed(MSG_CLEAR_TEXT, displayTime);
  }

  private final OverlayHandler mHandler = new OverlayHandler(this);

  private static class OverlayHandler extends WeakReferenceHandler<DiagnosticOverlay> {
    public OverlayHandler(DiagnosticOverlay parent) {
      super(parent);
    }

    @Override
    protected void handleMessage(Message msg, DiagnosticOverlay parent) {
      switch (msg.what) {
        case MSG_CLEAR_TEXT:
          parent.mText.setText("");
          parent.hide();
          break;
        default: // fall out
      }
    }
  }
}
