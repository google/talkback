/*
 * Copyright (C) 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Ported from Java to Kotlin.
 */

package com.google.android.accessibility.utils.gestures

import android.content.Context
import android.view.MotionEvent
import com.google.android.accessibility.utils.Performance.EventId

/**
 * This class matches gestures of the form multi-finger multi-tap and hold. The number of fingers
 * and taps for each instance is specified in the constructor.
 */
internal class MultiFingerMultiTapAndHold(
  context: Context,
  fingers: Int,
  taps: Int,
  gestureId: Int,
  listener: GestureMatcher.StateChangeListener,
  logger: GestureMatcher.AnalyticsEventLogger,
) : MultiFingerMultiTap(context, fingers, taps, gestureId, listener, logger) {

  override fun onPointerDown(eventId: EventId?, event: MotionEvent) {
    super.onPointerDown(eventId, event)
    if (isTargetFingerCountReached && completedTapCount + 1 == mTargetTapCount) {
      completeAfterLongPressTimeout(eventId, event)
    }
  }

  override fun onUp(eventId: EventId?, event: MotionEvent) {
    if (completedTapCount + 1 == mTargetTapCount) {
      // Calling super.onUp  would complete the multi-tap version of this.
      cancelGesture(event)
    } else {
      super.onUp(eventId, event)
      cancelAfterDoubleTapTimeout(event)
    }
  }

  override fun getGestureName(): String {
    val builder = StringBuilder()
    builder.append(targetFingerCount).append("-Finger ")
    if (mTargetTapCount == 1) {
      builder.append("Single")
    } else if (mTargetTapCount == 2) {
      builder.append("Double")
    } else if (mTargetTapCount == 3) {
      builder.append("Triple")
    } else if (mTargetTapCount > 3) {
      builder.append(mTargetTapCount)
    }
    return builder.append(" Tap and hold").toString()
  }
}
