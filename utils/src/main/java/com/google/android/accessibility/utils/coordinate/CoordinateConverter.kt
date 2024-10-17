package com.google.android.accessibility.utils.coordinate

import android.graphics.Rect
import android.graphics.RectF

/** Convert between two coordinates. */
object CoordinateConverter {

  /**
   * Convert and update [rectInFrom] from [from] bound to [to] bound.
   *
   * @param from outer bounds of coordinate where [rectInFrom] lives.
   * @param rectInFrom old rect in [from] that will be updated later.
   * @param to newer bounds of coordinate where [rectInFrom] will be updated to.
   */
  fun convert(from: Rect, to: Rect, rectInFrom: Rect) {
    // TODO: b/301161252 Improve the process by saving ratio between from and to.
    val fractional = fractional(rectInFrom, from)
    rectInFrom.set(
      (fractional.left * to.width()).toInt() + to.left,
      (fractional.top * to.height()).toInt() + to.top,
      (fractional.right * to.width()).toInt() + to.left,
      (fractional.bottom * to.height()).toInt() + to.top,
    )
  }

  private fun fractional(rect: Rect, outer: Rect): RectF {
    val w = outer.width().toFloat()
    val h = outer.height().toFloat()
    return RectF(
      (rect.left - outer.left) / w,
      (rect.top - outer.top) / h,
      (rect.right - outer.left) / w,
      (rect.bottom - outer.top) / h,
    )
  }
}
