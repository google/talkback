/*
 * Copyright (C) 2016 Google Inc.
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

package com.google.android.accessibility.switchaccess.ui;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.google.android.accessibility.switchaccess.R;
import org.checkerframework.checker.nullness.qual.Nullable;

/** Menu button used in menu overlays. Consists of an icon and text underneath. */
public class MenuButton extends LinearLayout {

  final ImageView imageView;
  final TextView textView;

  public MenuButton(Context context) {
    this(context, null);
  }

  public MenuButton(Context context, @Nullable AttributeSet attrs) {
    this(context, attrs, 0);
  }

  public MenuButton(Context context, @Nullable AttributeSet attrs, int defStyleRes) {
    this(context, attrs, defStyleRes, 0);
  }

  public MenuButton(
      Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
    this(context, attrs, defStyleAttr, defStyleRes, R.layout.switch_access_menu_button);
  }

  // #inflate expects a fully initialized ViewGroup but we need to inflate it here in the
  // constructor.
  @SuppressWarnings("nullness:argument.type.incompatible")
  public MenuButton(
      Context context,
      @Nullable AttributeSet attrs,
      int defStyleAttr,
      int defStyleRes,
      int layoutResource) {
    super(context, attrs, defStyleAttr, defStyleRes);

    LayoutInflater.from(context).inflate(layoutResource, this);

    imageView = findViewById(R.id.icon);
    textView = findViewById(R.id.text);

    clearButton();
  }

  /**
   * Set icon resource id, text resource id, and click listener for menu button and enable button.
   *
   * @param icon Resource id of icon displayed on this menu button
   * @param text Resource id of human readable label text displayed under the icon
   * @param listener Click listener called when this button is selected
   */
  public void setIconTextAndOnClickListener(int icon, int text, OnClickListener listener) {
    imageView.setImageResource(icon);
    textView.setText(text);
    setOnClickListener(listener);

    setEnabled(true);
  }

  /**
   * Set icon resource id, text, and click listener for menu button and enable button.
   *
   * @param icon Resource id of icon displayed on this menu button
   * @param text Human readable label text displayed under the icon
   * @param listener Click listener called when this button is selected
   */
  public void setIconTextAndOnClickListener(int icon, CharSequence text, OnClickListener listener) {
    imageView.setImageResource(icon);
    textView.setText(text);
    setOnClickListener(listener);

    setEnabled(true);
  }

  /** Clear all data associated with this menu button and disable button. */
  public void clearButton() {
    imageView.setImageResource(0);
    textView.setText(null);
    setOnClickListener(null);

    setEnabled(false);
  }

  @Override
  public void setEnabled(boolean enabled) {
    super.setEnabled(enabled);
    setFocusable(enabled);
    setClickable(enabled);

    // Set the background tint. Unfortunately, using a color state list that sets a background
    // depending on whether the View is enabled doesn't work...
    if (enabled) {
      setVisibility(View.VISIBLE);
    } else {
      setVisibility(View.INVISIBLE);
    }
    invalidate();
  }

  @Override
  public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
    super.onInitializeAccessibilityNodeInfo(info);
    info.setClassName(MenuButton.class.getName());
  }
}
