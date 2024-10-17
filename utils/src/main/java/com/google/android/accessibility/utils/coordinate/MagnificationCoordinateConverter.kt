package com.google.android.accessibility.utils.coordinate

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import androidx.annotation.VisibleForTesting

/**
 * Convert between magnified coordinates and unmagnified coordinates. Magnified coordinates will be
 * noted Local, and unmagnified will be noted global.
 *
 * Problem it's trying to solve: When magnified, [AccessibilityNodeInfo] returns magnified (local)
 * coordinates, while user interaction (user selected rect), and drawing works in unmagnified
 * (global) coordinate. This class helps converting between two.
 */
class MagnificationCoordinateConverter() {

  /** Metadata for calculating coordinates. Null if magnification is not active. */
  private var config: MagnificationCoordinateConfig? = null

  /** Local display window (after magnification). */
  private val displayLocal: Rect?
    get() = config?.displayLocal

  /** Global display size (before magnification). */
  private val displayGlobal: Rect?
    get() = config?.displayGlobal

  /** Initialize all metadata required for coordinate conversion. */
  fun updateCoordinates(service: AccessibilityService) {
    updateCoordinates(MagnificationCoordinateConfig.createConfig(service))
  }

  @VisibleForTesting
  fun updateCoordinates(config: MagnificationCoordinateConfig?) {
    this.config = config
  }

  /** Magnify given global rect to local coordinate. */
  fun localize(rectGlobal: Rect) {
    val from = displayGlobal ?: return
    val to = displayLocal ?: return

    CoordinateConverter.convert(from, to, rectGlobal)
  }

  /** Unmagnify given local rect to global coordinate. */
  fun globalize(rectLocal: Rect) {
    val from = displayLocal ?: return
    val to = displayGlobal ?: return

    CoordinateConverter.convert(from, to, rectLocal)
  }
}
