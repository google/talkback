package com.google.android.accessibility.utils.coordinate

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.MagnificationConfig
import android.graphics.Point
import android.graphics.Rect
import android.os.Build
import androidx.annotation.VisibleForTesting
import com.google.android.accessibility.utils.coordinate.Util.getDisplaySizeRect

/**
 * Metadata for magnification coordinate calculations. All coordinates are in pixel.
 *
 * Global coordinate: coordinate system before magnified. Local coordinate: coordinate system after
 * magnified. [displayLocal.width] == [scale] * [displayGlobal.width]
 *
 * @param scale how much it was magnified by
 * @param centerGlobal center x,y point in global coordinate
 * @param displayGlobal display rect size (width/height) in global coordinate
 * @param displayLocal display rect in global coordinate. Magnified display will often have negative
 *   left,top based on where the visible window is.
 */
data class MagnificationCoordinateConfig(
  val scale: Float,
  val centerGlobal: Point,
  val displayGlobal: Rect,
  val displayLocal: Rect = calculateDisplayLocal(displayGlobal, centerGlobal, scale),
) {

  companion object {

    /**
     * Creates [MagnificationCoordinateConfig] if magnification is enabled. Return null otherwise.
     */
    @JvmStatic
    fun createConfig(service: AccessibilityService): MagnificationCoordinateConfig? {
      val controller = service.magnificationController
      return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        val configuration = controller.magnificationConfig ?: return null
        if (
          !configuration.isActivated ||
            configuration.mode != MagnificationConfig.MAGNIFICATION_MODE_FULLSCREEN
        ) {
          null
        } else {
          val display = service.getDisplaySizeRect()
          // Only full screen magnification requires this special treatment.
          MagnificationCoordinateConfig(
            scale = configuration.scale,
            centerGlobal = Point(configuration.centerX.toInt(), configuration.centerY.toInt()),
            displayGlobal = display,
          )
        }
      } else {
        val scale: Float = controller.scale
        if (scale <= 1f) {
          null
        } else {
          val display = service.getDisplaySizeRect()
          MagnificationCoordinateConfig(
            scale = scale,
            centerGlobal = Point(controller.centerX.toInt(), controller.centerY.toInt()),
            displayGlobal = display,
          )
        }
      }
    }

    /**
     * Calculate display rect in local coordinate. Display's left, top are determined by
     * [centerGlobal].
     */
    @VisibleForTesting
    fun calculateDisplayLocal(displayGlobal: Rect, centerGlobal: Point, scale: Float): Rect {
      val displayLocalWidth: Int = (displayGlobal.width() * scale).toInt()
      val displayLocalHeight: Int = (displayGlobal.height() * scale).toInt()

      val viewPortGlobal = viewPortGlobal(displayGlobal, centerGlobal, scale)
      val offscreenXLocal = viewPortGlobal.left * scale
      val offscreenYLocal = viewPortGlobal.top * scale

      return Rect(
        -offscreenXLocal.toInt(),
        -offscreenYLocal.toInt(),
        -offscreenXLocal.toInt() + displayLocalWidth,
        -offscreenYLocal.toInt() + displayLocalHeight,
      )
    }

    /**
     * Calculate the view port (visible area to user) from magnification in global coordinate. view
     * port is a user-visible area. View port's width in local coordinate would match
     * [displayGlobal.width].
     */
    @VisibleForTesting
    fun viewPortGlobal(displayGlobal: Rect, centerGlobal: Point, scale: Float): Rect {
      val scaledWidthHalf = (displayGlobal.width() / scale / 2f).toInt()
      val scaledHeightHalf = (displayGlobal.height() / scale / 2f).toInt()

      return Rect(
        centerGlobal.x - scaledWidthHalf,
        centerGlobal.y - scaledHeightHalf,
        centerGlobal.x + scaledWidthHalf,
        centerGlobal.y + scaledHeightHalf,
      )
    }
  }
}
