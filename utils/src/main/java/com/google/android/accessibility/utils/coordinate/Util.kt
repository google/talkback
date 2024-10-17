package com.google.android.accessibility.utils.coordinate

import android.content.Context
import android.graphics.Rect
import android.os.Build
import android.util.DisplayMetrics
import android.view.WindowManager

object Util {

  /** Returns the display size in Rect, with 0,0 as top left. */
  @JvmStatic
  fun Context.getDisplaySizeRect(): Rect {
    val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
      val windowMetrics = windowManager.currentWindowMetrics
      return windowMetrics.bounds
    } else {
      val display = windowManager.defaultDisplay
      val displayMetrics = DisplayMetrics()
      display.getMetrics(displayMetrics)
      // TODO: add position offset of the navigation bar in reverse-landscape orientation.
      return Rect(0, 0, displayMetrics.widthPixels, displayMetrics.heightPixels)
    }
  }
}
