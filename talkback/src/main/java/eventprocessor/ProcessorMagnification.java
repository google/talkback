/*
 * Copyright (C) 2017 Google Inc.
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

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityService.MagnificationController;
import android.annotation.TargetApi;
import android.graphics.Rect;
import android.graphics.Region;
import android.os.Build;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import android.text.TextUtils;
import android.view.accessibility.AccessibilityEvent;
import com.google.android.accessibility.utils.AccessibilityEventListener;
import com.google.android.accessibility.utils.AccessibilityNodeInfoUtils;
import com.google.android.accessibility.utils.BuildVersionUtils;
import com.google.android.accessibility.utils.Performance.EventId;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Re-centers magnifier to accessibility focused or input focused node on {@link
 * AccessibilityEvent#TYPE_VIEW_ACCESSIBILITY_FOCUSED} and {@link
 * AccessibilityEvent#TYPE_VIEW_FOCUSED} events.
 */
public class ProcessorMagnification implements AccessibilityEventListener {

  private static final int EVENT_MASK =
      AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED | AccessibilityEvent.TYPE_VIEW_FOCUSED;
  @Nullable private final MagnificationController magnificationController;

  public ProcessorMagnification(AccessibilityService service) {
    if (BuildVersionUtils.isAtLeastN()) {
      magnificationController = service.getMagnificationController();
    } else {
      magnificationController = null;
    }
  }

  @Override
  public int getEventTypes() {
    return EVENT_MASK;
  }

  @Override
  public void onAccessibilityEvent(AccessibilityEvent event, EventId eventId) {
    if (!BuildVersionUtils.isAtLeastN()) {
      return;
    }
    AccessibilityNodeInfoCompat sourceNode = AccessibilityNodeInfoUtils.toCompat(event.getSource());
    if (sourceNode != null) {
      recenterMagnifier(sourceNode);
    }
    AccessibilityNodeInfoUtils.recycleNodes(sourceNode);
  }

  @TargetApi(Build.VERSION_CODES.N)
  private void recenterMagnifier(AccessibilityNodeInfoCompat node) {
    Rect sourceNodeBounds = new Rect();
    node.getBoundsInScreen(sourceNodeBounds);
    Region magRegion = magnificationController.getMagnificationRegion();
    Rect magBounds = magRegion.getBounds();
    float magScale = magnificationController.getScale();
    float halfMagWidth = (float) magBounds.width() / (2f * magScale); // use screen coordinates
    float halfMagHeight = (float) magBounds.height() / (2f * magScale);
    float margin = 5.0f;
    float newMagCenterY = sourceNodeBounds.top + halfMagHeight - margin;
    float newMagCenterX = -1;
    if (isLeftToRight(node)) {
      // Align upper left corner of sourceNode and magnifier
      newMagCenterX = sourceNodeBounds.left + halfMagWidth - margin;
    } else {
      // Align upper right corner of sourceNode and magnifier
      newMagCenterX = sourceNodeBounds.right - halfMagWidth + margin;
    }
    // Require that magnifier center is within magnifiable region
    float tolerance = 1.0f;
    newMagCenterX = Math.max(newMagCenterX, magBounds.left + tolerance);
    newMagCenterX = Math.min(newMagCenterX, magBounds.right - tolerance);
    newMagCenterY = Math.max(newMagCenterY, magBounds.top + tolerance);
    newMagCenterY = Math.min(newMagCenterY, magBounds.bottom - tolerance);
    magnificationController.setCenter(newMagCenterX, newMagCenterY, /* animate= */ true);
  }

  /**
   * Returns whether a node's content starts at left or right side.
   *
   * @param node The node whose direction we want.
   * @return {@code true} if node direction is left-to-right or unknown.
   */
  private static boolean isLeftToRight(AccessibilityNodeInfoCompat node) {
    if (node == null) {
      return true;
    }
    CharSequence text = node.getText();
    if (text == null || TextUtils.isEmpty(text)) {
      return true;
    }
    int direction = Character.getDirectionality(text.charAt(0));
    return (direction != Character.DIRECTIONALITY_RIGHT_TO_LEFT
        && direction != Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC);
  }
}
