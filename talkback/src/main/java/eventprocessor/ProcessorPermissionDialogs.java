/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.google.android.accessibility.talkback.eventprocessor;

import android.content.Context;
import android.graphics.Rect;
import androidx.core.view.accessibility.AccessibilityEventCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import androidx.core.view.accessibility.AccessibilityRecordCompat;
import android.view.accessibility.AccessibilityEvent;
import com.google.android.accessibility.talkback.Feedback;
import com.google.android.accessibility.talkback.NodeBlockingOverlay;
import com.google.android.accessibility.talkback.NodeBlockingOverlay.OnDoubleTapListener;
import com.google.android.accessibility.talkback.Pipeline;
import com.google.android.accessibility.talkback.TalkBackService;
import com.google.android.accessibility.talkback.controller.DimScreenController;
import com.google.android.accessibility.utils.AccessibilityEventListener;
import com.google.android.accessibility.utils.BuildVersionUtils;
import com.google.android.accessibility.utils.Performance.EventId;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Enables the user to click "allow" on permissions dialogs by intercepting tap events that occur
 * when the user is focused on a permissions dialog. Without this interceptor, taps on a permissions
 * dialog will not work when an overlay (such as the screen dimming overlay) is present.
 */
public class ProcessorPermissionDialogs implements AccessibilityEventListener, OnDoubleTapListener {

  /**
   * The permissions dialogs start to appear from M to N. For N_MR1, the overlay does not restrict
   * the touch action on dim screen and starting from O, we don't need this workaround because
   * framework performs ACTION_CLICK instead of mocking touch down/up actions when double tap on
   * screen. Thus the target API level is M <= api level <= N.
   */
  private static final boolean IS_API_LEVEL_SUPPORTED = !BuildVersionUtils.isAtLeastNMR1();

  public static final String ALLOW_BUTTON =
      "com.android.packageinstaller:id/permission_allow_button";

  /** Event types that are handled by ProcessorPermissionDialogs. */
  private static final int MASK_EVENTS_HANDLED_BY_PROCESSOR_PERMISSION_DIALOGS =
      AccessibilityEvent.TYPE_TOUCH_INTERACTION_END
          | AccessibilityEvent.TYPE_TOUCH_INTERACTION_START
          | AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
          | AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUS_CLEARED
          | AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED;

  private final NodeBlockingOverlay overlay;
  @Nullable private AccessibilityNodeInfoCompat allowNode = null;
  private boolean registered = false;
  private DimScreenController dimScreenController;
  private final Pipeline.FeedbackReturner pipeline;

  public ProcessorPermissionDialogs(
      Context context,
      DimScreenController dimScreenController,
      Pipeline.FeedbackReturner pipeline) {
    this.dimScreenController = dimScreenController;
    this.pipeline = pipeline;
    overlay = new NodeBlockingOverlay(context, this);
  }

  public void onReloadPreferences(TalkBackService service) {
    boolean supported = IS_API_LEVEL_SUPPORTED && NodeBlockingOverlay.isSupported(service);
    if (registered && !supported) {
      service.postRemoveEventListener(this);
      clearNode();
      registered = false;
    } else if (!registered && supported) {
      service.addEventListener(this);
      registered = true;
    }
  }

  @Override
  public int getEventTypes() {
    return MASK_EVENTS_HANDLED_BY_PROCESSOR_PERMISSION_DIALOGS;
  }

  @Override
  public void onAccessibilityEvent(AccessibilityEvent event, EventId eventId) {
    switch (event.getEventType()) {
      case AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED:
        clearNode();
        AccessibilityRecordCompat record = AccessibilityEventCompat.asRecord(event);
        AccessibilityNodeInfoCompat source = record.getSource();
        if (source != null) {
          if (ALLOW_BUTTON.equals(source.getViewIdResourceName())
              && dimScreenController.isDimmingEnabled()) {
            Rect sourceRect = new Rect();
            source.getBoundsInScreen(sourceRect);
            overlay.show(sourceRect);
            allowNode = source;
          } else {
            source.recycle();
          }
        }
        break;
      case AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUS_CLEARED:
      case AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED:
        clearNode();
        break;
      default: // fall out
    }

    if (allowNode != null) {
      overlay.onAccessibilityEvent(event, eventId);
    }
  }

  @Override
  public void onDoubleTap(EventId eventId) {
    if (allowNode != null) {
      pipeline.returnFeedback(
          eventId, Feedback.nodeAction(allowNode, AccessibilityNodeInfoCompat.ACTION_CLICK));
      allowNode.recycle();
      allowNode = null;
    }
  }

  private void clearNode() {
    overlay.hide();
    if (allowNode != null) {
      allowNode.recycle();
      allowNode = null;
    }
  }
}
