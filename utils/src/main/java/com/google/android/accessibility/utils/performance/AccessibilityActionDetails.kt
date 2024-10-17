package com.google.android.accessibility.utils.performance

/**
 * Stores information about the performed action, data when performing
 * [AccessibilityAction][android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction]
 * including execution time, action ID, timestamp and the result.
 */
data class AccessibilityActionDetails(
  val actionId: Int,
  // The time it takes to perform the action in million seconds.
  val processingTime: Long,
  // The time when the action is finished in milliseconds.
  val finishedUpTime: Long,
  val success: Boolean
)
