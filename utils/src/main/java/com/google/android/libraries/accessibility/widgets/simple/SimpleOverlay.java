/*
 * Copyright (C) 2011 Google Inc.
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

package com.google.android.libraries.accessibility.widgets.simple;

import android.content.Context;
import android.graphics.PixelFormat;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnKeyListener;
import android.view.View.OnTouchListener;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.WindowManager.LayoutParams;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.FrameLayout;
import org.checkerframework.checker.nullness.qual.Nullable;

/** Provides a simple full-screen overlay. Behaves like a {@link android.app.Dialog} but simpler. */
public class SimpleOverlay {
  private final Context context;
  private final WindowManager windowManager;
  private final ViewGroup contentView;
  private final LayoutParams params;
  private final int id;

  private SimpleOverlayListener listener;
  private OnTouchListener touchListener;
  private OnKeyListener keyListener;
  private boolean isVisible;
  @Nullable private CharSequence rootViewClassName = null;

  /**
   * Creates a new simple overlay that does not send {@link AccessibilityEvent}s.
   *
   * @param context The parent context.
   */
  public SimpleOverlay(Context context) {
    this(context, 0);
  }

  /**
   * Creates a new simple overlay that does not send {@link AccessibilityEvent}s.
   *
   * @param context The parent context.
   * @param id An optional identifier for the overlay.
   */
  public SimpleOverlay(Context context, int id) {
    this(context, id, false);
  }

  /**
   * Creates a new simple overlay.
   *
   * @param context The parent context.
   * @param id An optional identifier for the overlay.
   * @param sendsAccessibilityEvents Whether this window should dispatch {@link
   *     AccessibilityEvent}s.
   */
  // WindowManager is guaranteed to be not null but getSystemService() can return null in general.
  @SuppressWarnings("nullness:assignment")
  public SimpleOverlay(Context context, int id, final boolean sendsAccessibilityEvents) {
    this.context = context;
    windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
    contentView =
        new FrameLayout(context) {
          @Override
          public boolean dispatchKeyEvent(KeyEvent event) {
            if ((keyListener != null) && keyListener.onKey(this, event.getKeyCode(), event)) {
              return true;
            }

            return super.dispatchKeyEvent(event);
          }

          @Override
          public boolean dispatchTouchEvent(MotionEvent event) {
            // TODO: Check if we should adjust position after notifying touch listener.
            event.offsetLocation(-getTranslationX(), -getTranslationY());
            if ((touchListener != null) && touchListener.onTouch(this, event)) {
              return true;
            }

            return super.dispatchTouchEvent(event);
          }

          @Override
          public boolean requestSendAccessibilityEvent(View view, AccessibilityEvent event) {
            if (sendsAccessibilityEvents) {
              return super.requestSendAccessibilityEvent(view, event);
            } else {
              // Never send accessibility events if sendAccessibilityEvents == false.
              return false;
            }
          }

          @Override
          public void sendAccessibilityEventUnchecked(AccessibilityEvent event) {
            // Never send accessibility events if sendAccessibilityEvents == false.
            if (sendsAccessibilityEvents) {
              super.sendAccessibilityEventUnchecked(event);
            }
          }

          @Override
          public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
            super.onInitializeAccessibilityNodeInfo(info);
            if (rootViewClassName != null) {
              info.setClassName(rootViewClassName);
            }
          }
        };

    params = new LayoutParams();
    params.type = LayoutParams.TYPE_SYSTEM_ALERT;
    params.format = PixelFormat.TRANSLUCENT;
    params.flags |= LayoutParams.FLAG_NOT_FOCUSABLE;

    this.id = id;

    isVisible = false;
  }

  /** Returns the overlay context. */
  public Context getContext() {
    return context;
  }

  /** Returns the overlay identifier, or {@code 0} if no identifier was provided at construction. */
  public int getId() {
    return id;
  }

  /**
   * Sets class name in {@link AccessibilityNodeInfo} of the root view. This is a work around for
   * Android L where we cannot override the class name of FrameLayout by setting
   * AccessibilityDelegate.
   */
  public void setRootViewClassName(CharSequence className) {
    rootViewClassName = className;
  }

  /** Sets the key listener. */
  public void setOnKeyListener(OnKeyListener keyListener) {
    this.keyListener = keyListener;
  }

  /** Sets the touch listener. */
  public void setOnTouchListener(OnTouchListener touchListener) {
    this.touchListener = touchListener;
  }

  /** Sets the listener for overlay visibility callbacks. */
  public void setListener(SimpleOverlayListener listener) {
    this.listener = listener;
  }

  /**
   * Shows the overlay. Calls the listener's {@link SimpleOverlayListener#onShow(SimpleOverlay)} if
   * available.
   */
  public void show() {
    if (isVisible) {
      return;
    }

    windowManager.addView(contentView, params);
    isVisible = true;

    if (listener != null) {
      listener.onShow(this);
    }

    onShow();
  }

  /**
   * Hides the overlay. Calls the listener's {@link SimpleOverlayListener#onHide(SimpleOverlay)} if
   * available.
   */
  public void hide() {
    if (!isVisible) {
      return;
    }

    windowManager.removeViewImmediate(contentView);
    isVisible = false;

    if (listener != null) {
      listener.onHide(this);
    }

    onHide();
  }

  /** Called after {@link #show()}. */
  protected void onShow() {
    // Do nothing.
  }

  /** Called after {@link #hide()}. */
  protected void onHide() {
    // Do nothing.
  }

  /** Returns a copy of the current layout parameters. */
  public LayoutParams getParams() {
    LayoutParams copy = new LayoutParams();
    copy.copyFrom(params);
    return copy;
  }

  /**
   * Sets the current layout parameters and applies them immediately.
   *
   * @param params The layout parameters to use.
   */
  public void setParams(LayoutParams params) {
    this.params.copyFrom(params);
    updateViewLayout();
  }

  /** Updates the current layout if this overlay is visible. */
  public void updateViewLayout() {
    if (isVisible) {
      windowManager.updateViewLayout(contentView, this.params);
    }
  }

  /** Returns {@code true} if this overlay is visible. */
  public boolean isVisible() {
    return isVisible;
  }

  /**
   * Inflates the specified resource ID and sets it as the content view.
   *
   * @param layoutResId The layout ID of the view to set as the content view.
   */
  public void setContentView(int layoutResId) {
    contentView.removeAllViews();
    final LayoutInflater inflater = LayoutInflater.from(context);
    inflater.inflate(layoutResId, contentView);
  }

  /**
   * Sets the specified view as the content view.
   *
   * @param content The view to set as the content view.
   */
  public void setContentView(View content) {
    contentView.removeAllViews();
    contentView.addView(content);
  }

  /**
   * Returns the root {@link View} for this overlay. This is <strong>not</strong> the content view.
   */
  public View getRootView() {
    return contentView;
  }

  /**
   * Finds and returns the view within the overlay content.
   *
   * @param id The ID of the view to return.
   * @return The view with the specified ID, or {@code null} if not found.
   */
  public View findViewById(int id) {
    return contentView.findViewById(id);
  }

  /** Handles overlay visibility change callbacks. */
  public interface SimpleOverlayListener {
    /**
     * Called after the overlay is displayed.
     *
     * @param overlay The overlay that was displayed.
     */
    public void onShow(SimpleOverlay overlay);

    /**
     * Called after the overlay is hidden.
     *
     * @param overlay The overlay that was hidden.
     */
    public void onHide(SimpleOverlay overlay);
  }
}
