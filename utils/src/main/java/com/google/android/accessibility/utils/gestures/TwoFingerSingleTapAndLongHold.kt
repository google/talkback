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
import android.os.Build
import android.view.MotionEvent
import androidx.annotation.RequiresApi
import com.google.android.accessibility.utils.Performance.EventId

/**
 * This class matches gesture of the form 2-finger 1-tap and long hold. It is only for TalkBack
 * mis-triggering recovery feature. Its hold complete timeout is longer than long press timeout.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
internal class TwoFingerSingleTapAndLongHold(
  context: Context,
  gestureId: Int,
  listener: GestureMatcher.StateChangeListener,
  logger: GestureMatcher.AnalyticsEventLogger,
) : MultiFingerMultiTap(context, 2, 1, gestureId, listener, logger) {

  override fun onPointerDown(eventId: EventId?, event: MotionEvent) {
    super.onPointerDown(eventId, event)
    if (isTargetFingerCountReached && completedTapCount + 1 == mTargetTapCount) {
      completeAfter(LONG_HOLD_TIMEOUT_MS.toLong(), eventId, event)
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

  override fun getGestureName(): String = "2-Finger Tap and long hold"

  private companion object {
    const val LONG_HOLD_TIMEOUT_MS = 5000
  }
}
