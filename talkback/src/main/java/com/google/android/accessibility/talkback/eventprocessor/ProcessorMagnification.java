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

import static android.accessibilityservice.MagnificationConfig.MAGNIFICATION_MODE_FULLSCREEN;
import static android.view.accessibility.AccessibilityWindowInfo.TYPE_MAGNIFICATION_OVERLAY;
import static com.google.android.accessibility.talkback.compositor.MagnificationState.STATE_OFF;
import static com.google.android.accessibility.talkback.compositor.MagnificationState.STATE_ON;
import static com.google.android.accessibility.talkback.compositor.MagnificationState.STATE_SCALE_CHANGED;

import android.accessibilityservice.AccessibilityService.MagnificationController;
import android.accessibilityservice.AccessibilityService.MagnificationController.OnMagnificationChangedListener;
import android.accessibilityservice.MagnificationConfig;
import android.graphics.Rect;
import android.graphics.Region;
import android.text.TextUtils;
import android.view.View;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityWindowInfo;
import androidx.annotation.VisibleForTesting;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import androidx.core.view.accessibility.AccessibilityWindowInfoCompat;
import com.google.android.accessibility.talkback.analytics.TalkBackAnalytics;
import com.google.android.accessibility.talkback.compositor.Compositor;
import com.google.android.accessibility.talkback.compositor.GlobalVariables;
import com.google.android.accessibility.talkback.compositor.MagnificationState;
import com.google.android.accessibility.talkback.compositor.MagnificationState.State;
import com.google.android.accessibility.utils.AccessibilityEventListener;
import com.google.android.accessibility.utils.AccessibilityNodeInfoUtils;
import com.google.android.accessibility.utils.AccessibilityWindowInfoUtils;
import com.google.android.accessibility.utils.FeatureSupport;
import com.google.android.accessibility.utils.Performance;
import com.google.android.accessibility.utils.Performance.EventId;
import java.util.Locale;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Re-centers magnifier to accessibility focused or input focused node on {@link
 * AccessibilityEvent#TYPE_VIEW_ACCESSIBILITY_FOCUSED} and {@link
 * AccessibilityEvent#TYPE_VIEW_FOCUSED} events. And Handles the feedback when the magnification
 * state is changed.
 */
public class ProcessorMagnification implements AccessibilityEventListener {

  private static final int EVENT_MASK =
      AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED | AccessibilityEvent.TYPE_VIEW_FOCUSED;

  /** The fraction for getting window magnification center offset. */
  private static final float WINDOW_MAGNIFICATION_CENTER_OFFSET_FRACTION = 0.2f;

  private final boolean supportWindowMagnification;

  private final @Nullable MagnificationController magnificationController;
  private final GlobalVariables globalVariables;
  private final Compositor compositor;
  private final TalkBackAnalytics analytics;

  /** Magnification bounds for testing. */
  private Rect testMagBounds = null;

  @Nullable @VisibleForTesting
  public final OnMagnificationChangedListener onMagnificationChangedListener;

  private float lastScale = 1.0f;
  private int lastMode = MagnificationConfig.MAGNIFICATION_MODE_DEFAULT;

  public ProcessorMagnification(
      MagnificationController magnificationController,
      GlobalVariables globalVariables,
      Compositor compositor,
      TalkBackAnalytics analytics,
      boolean supportWindowMagnification) {
    this.magnificationController = magnificationController;
    this.globalVariables = globalVariables;
    this.compositor = compositor;
    this.analytics = analytics;
    this.supportWindowMagnification = supportWindowMagnification;

    onMagnificationChangedListener = createMagnificationChangeListener();
  }

  /** Creates the magnification change listener by platform support. */
  private @Nullable OnMagnificationChangedListener createMagnificationChangeListener() {
    if (!FeatureSupport.supportMagnificationController()) {
      return null;
    }

    if (supportWindowMagnification) {
      // Magnification change listener for SDK 33+ that can listen to the multiple modes.
      return new OnMagnificationChangedListener() {
        // Suppress the warning since this is for target SDK 33.
        @SuppressWarnings("Override")
        @Override
        public void onMagnificationChanged(
            MagnificationController magnificationController,
            Region region,
            MagnificationConfig config) {

          final int mode = config.getMode();
          final float scale = config.getScale();
          final boolean modeIsChanged = mode != lastMode;
          try {
            // Do nothing if scale and mode haven't changed.
            if (!modeIsChanged && (scale == lastScale)) {
              return;
            }

            handleMagnificationChanged(mode, scale, lastScale, modeIsChanged);
          } finally {
            lastMode = mode;
            lastScale = scale;
          }
        }

        @Override
        public void onMagnificationChanged(
            MagnificationController magnificationController,
            Region region,
            float scale,
            float centerX,
            float centerY) {
          // Legacy listener has no action.
        }
      };
    } else {
      // Legacy magnification change listener for old platform that can only listen to full-screen
      // magnification.
      return (magnificationController, region, scale, centerX, centerY) -> {

        // Do nothing if scale hasn't changed.
        if (scale == lastScale) {
          return;
        }

        try {
          handleMagnificationChanged(null, scale, lastScale, false);
        } finally {
          lastScale = scale;
        }
      };
    }
  }

  private void handleMagnificationChanged(
      Integer mode, float scale, float lastScale, boolean modeIsChanged) {
    @State int state;
    if (scale > 1 && (modeIsChanged || lastScale == 1)) {
      state = STATE_ON;

      // Log magnification mode.
      analytics.onMagnificationUsed((mode == null) ? MAGNIFICATION_MODE_FULLSCREEN : mode);
    } else if (scale == 1 && lastScale > 1) {
      state = STATE_OFF;
    } else if (scale > 1) {
      state = STATE_SCALE_CHANGED;
    } else {
      return;
    }

    globalVariables.setMagnificationState(
        MagnificationState.builder().setMode(mode).setCurrentScale(scale).setState(state).build());

    if (FeatureSupport.supportAnnounceMagnificationChanged()) {
      // TODO use pipeline to handle compositor for magnification
      compositor.handleEvent(
          Compositor.EVENT_MAGNIFICATION_CHANGED, Performance.EVENT_ID_UNTRACKED);
    }
  }

  @Override
  public int getEventTypes() {
    return EVENT_MASK;
  }

  @Override
  public void onAccessibilityEvent(AccessibilityEvent event, EventId eventId) {
    if (!FeatureSupport.supportMagnificationController()) {
      return;
    }
    AccessibilityNodeInfoCompat sourceNode = AccessibilityNodeInfoUtils.toCompat(event.getSource());
    AccessibilityWindowInfoCompat window = AccessibilityNodeInfoUtils.getWindow(sourceNode);
    // It’s unnecessary to recenter the magnifier if the focus is on the keyboard because Keyboard
    // isn't in the magnifier and when the magnifier is not magnifying.
    // TODO: recenter magnifier for window magnification
    if (sourceNode == null
        || window == null
        || AccessibilityNodeInfoUtils.isKeyboard(sourceNode)
        || (AccessibilityWindowInfoUtils.getType(window) == TYPE_MAGNIFICATION_OVERLAY)
        || getMagnificationScale() <= 1) {
      return;
    }

    recenterMagnifier(sourceNode, AccessibilityWindowInfoUtils.getType(window));
  }

  /** Registers the magnification change listener when TalkBack resumes. */
  public void onResumeInfrastructure() {
    if (FeatureSupport.supportMagnificationController() && magnificationController != null) {
      magnificationController.addListener(onMagnificationChangedListener);
    }
  }

  /** Unregisters the magnification change listener when TalkBack is suspended. */
  public void onSuspendInfrastructure() {
    if (FeatureSupport.supportMagnificationController() && magnificationController != null) {
      magnificationController.removeListener(onMagnificationChangedListener);

      lastScale = 1.0f;
      lastMode = MagnificationConfig.MAGNIFICATION_MODE_DEFAULT;
    }
  }

  @VisibleForTesting
  public void setMagnificationBounds(Rect rect) {
    testMagBounds = rect;
  }

  private Rect getMagnificationBounds() {
    if (testMagBounds != null) {
      return testMagBounds;
    }
    // New API supports getting magnification bounds of multiple magnification modes.
    if (supportWindowMagnification) {
      return magnificationController.getCurrentMagnificationRegion().getBounds();
    }
    return magnificationController.getMagnificationRegion().getBounds();
  }

  private void recenterMagnifier(AccessibilityNodeInfoCompat node, int windowType) {
    if (!supportWindowMagnification) {
      recenterFullScreenMagnifier(node);
      return;
    }

    int mode = magnificationController.getMagnificationConfig().getMode();
    if (mode == MAGNIFICATION_MODE_FULLSCREEN) {
      recenterFullScreenMagnifier(node);
    } else if (mode == MagnificationConfig.MAGNIFICATION_MODE_WINDOW
        && windowType != AccessibilityWindowInfo.TYPE_SYSTEM) {
      // Support magnification re-centering except system window because the nodes on status bar
      // window would send accessibility view focused event repeatedly.
      recenterWindowMagnifier(node);
    }
  }

  private void recenterFullScreenMagnifier(AccessibilityNodeInfoCompat node) {
    Rect sourceNodeBounds = new Rect();
    node.getBoundsInScreen(sourceNodeBounds);
    Rect magBounds = getMagnificationBounds();
    float magScale = magnificationController.getScale();
    float halfMagWidth = (float) magBounds.width() / (2f * magScale); // use screen coordinates
    float halfMagHeight = (float) magBounds.height() / (2f * magScale);
    float margin = 5.0f;
    float newMagCenterY;
    float newMagCenterX;
    // From Android 8.1, the bounds are scaled up by magnification scale and the node position are
    // the offset of the magnifier. REFERTO
    if (FeatureSupport.isBoundsScaledUpByMagnifier()) {
      newMagCenterY =
          magnificationController.getCenterY() + (sourceNodeBounds.top / magScale) - margin;
      if (isLeftToRight(node)) {
        // Aligns upper left corner of sourceNode and magnifier.
        newMagCenterX =
            magnificationController.getCenterX() + (sourceNodeBounds.left / magScale) - margin;
      } else {
        // Aligns upper right corner of sourceNode and magnifier.
        newMagCenterX =
            magnificationController.getCenterX() + (sourceNodeBounds.right / magScale) + margin;
      }
    } else {
      newMagCenterY = sourceNodeBounds.top + halfMagHeight - margin;
      if (isLeftToRight(node)) {
        // Aligns upper left corner of sourceNode and magnifier.
        newMagCenterX = sourceNodeBounds.left + halfMagWidth - margin;
      } else {
        // Aligns upper right corner of sourceNode and magnifier.
        newMagCenterX = sourceNodeBounds.right - halfMagWidth + margin;
      }
    }
    // Require that magnifier center is within magnifiable region
    float tolerance = 1.0f;
    newMagCenterX = Math.max(newMagCenterX, magBounds.left + tolerance);
    newMagCenterX = Math.min(newMagCenterX, magBounds.right - tolerance);
    newMagCenterY = Math.max(newMagCenterY, magBounds.top + tolerance);
    newMagCenterY = Math.min(newMagCenterY, magBounds.bottom - tolerance);
    magnificationController.setCenter(newMagCenterX, newMagCenterY, /* animate= */ true);
  }

  private void recenterWindowMagnifier(AccessibilityNodeInfoCompat node) {
    Rect sourceNodeBounds = new Rect();
    node.getBoundsInScreen(sourceNodeBounds);
    Rect magBounds = getMagnificationBounds();
    float newMagCenterY = sourceNodeBounds.exactCenterY();
    float newMagCenterX;
    // If the source node width is smaller than the width of window magnification bounds.
    // The new magnification center can be moved to the center of the source node.
    if (sourceNodeBounds.width() <= magBounds.width()) {
      newMagCenterX = sourceNodeBounds.exactCenterX();
    } else {
      // The new magnification center would be 20% or 80% position in the source node width based
      // on the node text direction for reading convenience.
      float offset = (sourceNodeBounds.width() * WINDOW_MAGNIFICATION_CENTER_OFFSET_FRACTION);
      newMagCenterX =
          isLeftToRight(node) ? sourceNodeBounds.left + offset : sourceNodeBounds.right - offset;
    }
    MagnificationConfig newConfig =
        new MagnificationConfig.Builder()
            .setCenterX(newMagCenterX)
            .setCenterY(newMagCenterY)
            .build();
    magnificationController.setMagnificationConfig(newConfig, /* animate= */ false);
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
    @Nullable CharSequence text = AccessibilityNodeInfoUtils.getText(node);
    if (TextUtils.isEmpty(text)) {
      return TextUtils.getLayoutDirectionFromLocale(Locale.getDefault())
          == View.LAYOUT_DIRECTION_LTR;
    }
    int direction = Character.getDirectionality(text.charAt(0));
    return (direction != Character.DIRECTIONALITY_RIGHT_TO_LEFT
        && direction != Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC);
  }

  private float getMagnificationScale() {
    if (supportWindowMagnification) {
      return magnificationController.getMagnificationConfig().getScale();
    }
    return magnificationController.getScale();
  }
}
