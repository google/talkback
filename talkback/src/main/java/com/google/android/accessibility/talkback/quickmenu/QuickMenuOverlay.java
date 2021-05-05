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

package com.google.android.accessibility.talkback.quickmenu;

import android.content.Context;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup.LayoutParams;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.google.android.accessibility.talkback.R;
import com.google.android.accessibility.utils.FeatureSupport;
import com.google.android.accessibility.utils.widget.SimpleOverlay;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * An non-focusable overlay window to show what quick menu item or action is changed. The focus
 * shouldn't be changed while the overlay is showing or hiding.
 */
public class QuickMenuOverlay extends SimpleOverlay {

  // The same timeout as Snackbar by default (SnackbarManager.LONG_DURATION_MS).
  private static final int SHOWING_TIME_MS = 2750;
  private final Context context;
  private final Handler handler = new Handler();
  private final int layoutResId;
  private LinearLayout overlay;
  private TextView settingText;
  private ImageView leftIcon;
  private ImageView rightIcon;
  private final Runnable hideOverlay = this::hide;
  private @Nullable CharSequence message;
  private boolean supported = true;

  public QuickMenuOverlay(Context context, int layoutResId) {
    super(context);
    this.context = context;
    this.layoutResId = layoutResId;
  }

  public void show(boolean showIcon) {
    if (!supported || TextUtils.isEmpty(message)) {
      return;
    }

    if (overlay == null) {
      createOverlay();
    }
    settingText.setText(message);
    if (showIcon) {
      leftIcon.setVisibility(View.VISIBLE);
      rightIcon.setVisibility(View.VISIBLE);
    } else {
      leftIcon.setVisibility(View.GONE);
      rightIcon.setVisibility(View.GONE);
    }

    if (isShowing()) {
      // Updates the view if the overlay is showing to avoid adding the view to WindowManager again.
      updateViewLayout();
    } else {
      super.show();
    }

    handler.removeCallbacks(hideOverlay);

    // Users can choose their preferred timeout in accessibility settings on Android Q and above
    // devices.
    int timeout = SHOWING_TIME_MS;
    if (FeatureSupport.supportRecommendedTimeout()) {
      @Nullable
      AccessibilityManager accessibilityManager =
          (AccessibilityManager) context.getSystemService(Context.ACCESSIBILITY_SERVICE);
      if (accessibilityManager != null) {
        timeout =
            accessibilityManager.getRecommendedTimeoutMillis(
                SHOWING_TIME_MS, AccessibilityManager.FLAG_CONTENT_TEXT);
      }
    }
    handler.postDelayed(hideOverlay, timeout);
  }

  @Override
  public void hide() {
    if (overlay == null) {
      return;
    }
    handler.removeCallbacks(hideOverlay);
    super.hide();
  }

  public boolean isShowing() {
    return (overlay != null) && isVisible();
  }

  public void setMessage(@Nullable CharSequence message) {
    this.message = message;
  }

  /**
   * Supports this overlay or not. If {@code supported} is false, to hide this overlay while it's
   * showing.
   */
  public void setSupported(boolean supported) {
    if (!supported) {
      hide();
    }
    this.supported = supported;
  }

  private void createOverlay() {
    WindowManager.LayoutParams parameters = new WindowManager.LayoutParams();
    parameters.type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY;
    parameters.flags =
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
    parameters.format = PixelFormat.TRANSLUCENT;
    parameters.width = LayoutParams.MATCH_PARENT;
    parameters.height = LayoutParams.WRAP_CONTENT;
    parameters.gravity = Gravity.CENTER;
    setParams(parameters);

    LayoutInflater layoutInflater =
        (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    overlay = (LinearLayout) layoutInflater.inflate(layoutResId, null);
    settingText = overlay.findViewById(R.id.quick_menu_text);
    leftIcon = overlay.findViewById(R.id.quick_menu_left_icon);
    rightIcon = overlay.findViewById(R.id.quick_menu_right_icon);
    setContentView(overlay);
  }
}
